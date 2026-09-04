package com.volmit.rift.gui;

import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.config.RiftConfig;
import com.volmit.rift.config.RiftConfigManager;
import com.volmit.rift.hotload.RiftHotloadService;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class RiftConfigMenu implements Listener {
    private static final int SIZE = 54;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;
    private static final int[] CATEGORY_SLOTS = {20, 21, 22, 23, 24};
    private static final long PROMPT_TICKS = 20L * 60L;
    private static final int MAXIMUM_INPUT_LENGTH = 128;

    private final Rift plugin;
    private final RiftConfigManager config;
    private final RiftLocalization language;
    private final RiftHotloadService hotload;
    private final List<Setting> settings;
    private final Map<UUID, PromptSession> prompts = new ConcurrentHashMap<>();
    private final AtomicLong promptIds = new AtomicLong();
    private final ExecutorService writer;

    public RiftConfigMenu(Rift plugin, RiftConfigManager config, RiftLocalization language, RiftHotloadService hotload) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.language = Objects.requireNonNull(language, "language");
        this.hotload = Objects.requireNonNull(hotload, "hotload");
        settings = createSettings();
        writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Rift-Config-Editor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void open(Player player) {
        if (!FoliaScheduler.runEntity(plugin, player,
                () -> LanguageAudience.run(player.getUniqueId(), () -> openRootOwned(player)))) {
            language.send(player, RiftMessages.CONFIG_SAVE_FAILED);
        }
    }

    public void shutdown() {
        prompts.clear();
        writer.shutdownNow();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EditorHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0
                || event.getRawSlot() >= SIZE) {
            return;
        }
        if (!player.hasPermission("rift.config") && !player.hasPermission("rift.admin")) {
            clearEditorState(player.getUniqueId());
            player.closeInventory();
            language.send(player, RiftMessages.PERMISSION_DENIED, MessageArgs.builder()
                    .untrusted("permission", "rift.config")
                    .build());
            return;
        }
        LanguageAudience.run(player.getUniqueId(), () -> handleClick(player, holder, event));
    }

    private void handleClick(Player player, EditorHolder holder, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            clearEditorState(player.getUniqueId());
            player.closeInventory();
            return;
        }
        if (holder.category() == null) {
            Category category = categoryAt(slot);
            if (category != null) {
                if (category == Category.LANGUAGES) {
                    plugin.languageSwitcher().openEditor(player, returning -> LanguageAudience.run(
                            returning.getUniqueId(), () -> openRootOwned(returning)));
                } else {
                    openCategoryOwned(player, category);
                }
            }
            return;
        }
        if (slot == BACK_SLOT) {
            openRootOwned(player);
            return;
        }
        List<Setting> categorySettings = settings(holder.category());
        int settingIndex = settingIndex(slot, categorySettings.size());
        if (settingIndex < 0 || settingIndex >= categorySettings.size()) {
            return;
        }
        handleSettingClick(player, holder.category(), categorySettings.get(settingIndex), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EditorHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PromptSession prompt = prompts.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String input = event.getMessage();
        FoliaScheduler.runEntity(
                plugin,
                player,
                () -> processPrompt(player, prompt, input),
                0L,
                () -> prompts.remove(player.getUniqueId(), prompt)
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        prompts.remove(playerId);
    }

    private void openRootOwned(Player player) {
        LanguageAudience.run(player.getUniqueId(), () -> renderRootOwned(player));
    }

    private void renderRootOwned(Player player) {
        clearEditorState(player.getUniqueId());
        EditorHolder holder = EditorHolder.root();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, language.text(RiftMessages.GUI_ROOT_TITLE).legacy());
        holder.setInventory(inventory);
        fill(inventory);
        Category[] categories = Category.values();
        for (int index = 0; index < categories.length; index++) {
            inventory.setItem(CATEGORY_SLOTS[index], categoryItem(categories[index]));
        }
        navigation(inventory, false);
        player.openInventory(inventory);
    }

    private void openCategoryOwned(Player player, Category category) {
        LanguageAudience.run(player.getUniqueId(), () -> renderCategoryOwned(player, category));
    }

    private void renderCategoryOwned(Player player, Category category) {
        EditorHolder holder = EditorHolder.category(category);
        MessageArgs titleArguments = MessageArgs.builder()
                .trusted("category", language.text(category.displayName()).legacy())
                .build();
        Inventory inventory = Bukkit.createInventory(
                holder,
                SIZE,
                language.text(RiftMessages.GUI_CATEGORY_TITLE, titleArguments).legacy()
        );
        holder.setInventory(inventory);
        fill(inventory);
        RiftConfig current = config.get();
        List<Setting> categorySettings = settings(category);
        int[] settingSlots = settingSlots(categorySettings.size());
        for (int index = 0; index < categorySettings.size(); index++) {
            inventory.setItem(settingSlots[index], settingItem(categorySettings.get(index), current));
        }
        navigation(inventory, true);
        player.openInventory(inventory);
    }

    private void navigation(Inventory inventory, boolean back) {
        if (back) {
            inventory.setItem(BACK_SLOT, item(Material.ARROW, language.text(RiftMessages.GUI_BACK).legacy(), List.of()));
        }
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, language.text(RiftMessages.GUI_CLOSE).legacy(), List.of()));
    }

    private void handleSettingClick(Player player, Category category, Setting setting, InventoryClickEvent event) {
        RiftConfig current = config.get();
        if (setting.kind() == SettingKind.LOCALE) {
            player.closeInventory();
            plugin.languageSwitcher().command(player, new String[]{"server"});
            return;
        }
        if (setting.kind() == SettingKind.BOOLEAN) {
            save(player, category, setting, Boolean.toString(!Boolean.parseBoolean(setting.reader().apply(current))));
            return;
        }
        if (setting.kind().numeric() && !isPromptClick(event.getClick())) {
            double direction = event.isRightClick() ? -1D : 1D;
            double multiplier = event.isShiftClick() ? 10D : 1D;
            save(player, category, setting, adjust(setting, setting.reader().apply(current), direction * multiplier));
            return;
        }
        beginPrompt(player, category, setting);
    }

    private void beginPrompt(Player player, Category category, Setting setting) {
        PromptSession prompt = new PromptSession(promptIds.incrementAndGet(), category, setting);
        prompts.put(player.getUniqueId(), prompt);
        player.closeInventory();
        MessageArgs arguments = MessageArgs.builder()
                .untrusted("setting", language.text(setting.name()).plain())
                .build();
        language.send(player, RiftMessages.GUI_PROMPT, arguments);
        language.send(player, RiftMessages.GUI_PROMPT_CANCEL);
        boolean scheduled = FoliaScheduler.runEntity(
                plugin,
                player,
                () -> expirePrompt(player, prompt),
                PROMPT_TICKS,
                () -> prompts.remove(player.getUniqueId(), prompt)
        );
        if (!scheduled) {
            prompts.remove(player.getUniqueId(), prompt);
            language.send(player, RiftMessages.GUI_PROMPT_CANCELLED);
        }
    }

    private void processPrompt(Player player, PromptSession prompt, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            language.send(player, RiftMessages.GUI_PROMPT_CANCELLED);
            openCategoryOwned(player, prompt.category());
            return;
        }
        if (input.length() > MAXIMUM_INPUT_LENGTH) {
            saveFailedOwned(player, prompt.category(), prompt.setting(), "value is longer than 128 characters");
            return;
        }
        save(player, prompt.category(), prompt.setting(), input);
    }

    private void expirePrompt(Player player, PromptSession prompt) {
        if (!prompts.remove(player.getUniqueId(), prompt)) {
            return;
        }
        language.send(player, RiftMessages.GUI_PROMPT_TIMEOUT);
        openCategoryOwned(player, prompt.category());
    }

    private void save(Player player, Category category, Setting setting, String input) {
        try {
            writer.execute(() -> saveOffThread(player, category, setting, input));
        } catch (RejectedExecutionException exception) {
            saveFailedOwned(player, category, setting, "configuration editor is shutting down");
        }
    }

    private void saveOffThread(Player player, Category category, Setting setting, String input) {
        try {
            String before = setting.reader().apply(config.get());
            boolean success = config.update(candidate -> {
                setting.writer().write(candidate, input);
                return candidate;
            });
            if (!success) {
                throw new IOException("configuration file could not be written");
            }
            hotload.noteConfigWrite();
            String after = setting.reader().apply(config.get());
            scheduleResult(player, () -> {
                language.send(player, RiftMessages.GUI_SETTING_SAVED, MessageArgs.builder()
                        .untrusted("setting", language.text(player, setting.name()).plain())
                        .untrusted("after", displayValue(after))
                        .untrusted("before", displayValue(before))
                        .build());
                openCategoryOwned(player, category);
            });
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save Rift setting " + setting.path(), exception);
            scheduleResult(player, () -> saveFailedOwned(player, category, setting, reason(exception)));
        }
    }
    private void scheduleResult(Player player, Runnable result) {
        if (!FoliaScheduler.runEntity(plugin, player, result, 0L, () -> clearEditorState(player.getUniqueId()))) {
            clearEditorState(player.getUniqueId());
        }
    }

    private void saveFailedOwned(Player player, Category category, Setting setting, String reason) {
        language.send(player, RiftMessages.GUI_SETTING_FAILED, MessageArgs.builder()
                .untrusted("setting", language.text(player, setting.name()).plain())
                .untrusted("reason", reason)
                .build());
        openCategoryOwned(player, category);
    }

    private ItemStack categoryItem(Category category) {
        return item(category.material(), language.text(category.displayName()).legacy(), List.of(
                language.text(RiftMessages.GUI_CATEGORY_OPEN).legacy()
        ));
    }

    private ItemStack settingItem(Setting setting, RiftConfig current) {
        String value = displayValue(setting.reader().apply(current));
        MessageArgs arguments = setting.kind() == SettingKind.BOOLEAN
                ? MessageArgs.builder().trusted("value", Boolean.parseBoolean(value) ? "&aenabled" : "&cdisabled").build()
                : MessageArgs.builder().untrusted("value", value).build();
        TextKey instruction = switch (setting.kind()) {
            case BOOLEAN -> RiftMessages.GUI_TOGGLE;
            case INTEGER, LONG, FLOAT -> RiftMessages.GUI_EXACT_NUMBER;
            case TEXT -> RiftMessages.GUI_TEXT;
            case LOCALE -> RiftMessages.GUI_LANGUAGE_SELECT;
        };
        return item(setting.material(), language.text(setting.name()).legacy(), List.of(
                language.text(RiftMessages.GUI_CURRENT, arguments).legacy(),
                language.text(instruction).legacy()
        ));
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private List<Setting> settings(Category category) {
        return settings.stream().filter(setting -> setting.category() == category).toList();
    }

    private static Category categoryAt(int slot) {
        Category[] categories = Category.values();
        for (int index = 0; index < CATEGORY_SLOTS.length; index++) {
            if (CATEGORY_SLOTS[index] == slot) {
                return categories[index];
            }
        }
        return null;
    }

    private static int settingIndex(int slot, int count) {
        int[] settingSlots = settingSlots(count);
        for (int index = 0; index < settingSlots.length; index++) {
            if (settingSlots[index] == slot) {
                return index;
            }
        }
        return -1;
    }

    static int inventorySize() {
        return SIZE;
    }

    static Map<Integer, String> rootCategorySlots() {
        Category[] categories = Category.values();
        Map<Integer, String> slots = new LinkedHashMap<>();
        for (int index = 0; index < categories.length; index++) {
            slots.put(CATEGORY_SLOTS[index], categories[index].name());
        }
        return Map.copyOf(slots);
    }

    static String rootCategoryNameAt(int slot) {
        Category category = categoryAt(slot);
        return category == null ? null : category.name();
    }

    static int[] settingSlots(int count) {
        if (count < 1 || count > BACK_SLOT) {
            throw new IllegalArgumentException("Rift config category size must be between 1 and 45: " + count);
        }
        int[] slots = new int[count];
        int rowCount = (count + 8) / 9;
        int startingRow = (5 - rowCount) / 2;
        int baseRowSize = count / rowCount;
        int largerRows = count % rowCount;
        int settingIndex = 0;
        for (int row = 0; row < rowCount; row++) {
            int rowSize = baseRowSize + (row < largerRows ? 1 : 0);
            int startingColumn = (9 - rowSize) / 2;
            for (int column = 0; column < rowSize; column++) {
                slots[settingIndex++] = (startingRow + row) * 9 + startingColumn + column;
            }
        }
        return slots;
    }

    static Set<Integer> navigationSlots() {
        return Set.of(BACK_SLOT, CLOSE_SLOT);
    }

    private static boolean isPromptClick(ClickType click) {
        return click == ClickType.DROP || click == ClickType.CONTROL_DROP;
    }

    private static String adjust(Setting setting, String current, double multiplier) {
        double adjusted = Double.parseDouble(current) + setting.step() * multiplier;
        return switch (setting.kind()) {
            case INTEGER -> Integer.toString((int) Math.round(adjusted));
            case LONG -> Long.toString(Math.round(adjusted));
            case FLOAT -> Float.toString((float) adjusted);
            default -> current;
        };
    }

    private static String displayValue(String value) {
        if (value.isBlank()) {
            return "primary";
        }
        return value.length() <= 64 ? value : value.substring(0, 61) + "…";
    }

    private static String optionalWorld(String value) {
        String normalized = value.trim();
        return normalized.equalsIgnoreCase("primary") || normalized.equalsIgnoreCase("none")
                ? ""
                : normalized;
    }

    private void clearEditorState(UUID playerId) {
        prompts.remove(playerId);
    }

    private static String reason(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "validation or disk write failed; see the console" : message;
    }

    static Set<String> editablePaths() {
        return createSettings().stream().map(Setting::path).collect(Collectors.toUnmodifiableSet());
    }

    static boolean languageUsesPicker() {
        return createSettings().stream()
                .anyMatch(setting -> setting.path().equals("language") && setting.kind() == SettingKind.LOCALE);
    }

    private static List<Setting> createSettings() {
        return List.of(
                setting(Category.GENERAL, "language", RiftMessages.GUI_LANGUAGE, Material.WRITABLE_BOOK,
                        SettingKind.LOCALE, 1D, RiftConfig::getLanguage,
                        (config, value) -> config.setLanguage(value.trim())),
                setting(Category.GENERAL, "autoLoadManagedWorlds", RiftMessages.GUI_AUTOLOAD, Material.ENDER_EYE,
                        SettingKind.BOOLEAN, 1D, config -> Boolean.toString(config.isAutoLoadManagedWorlds()),
                        (config, value) -> config.setAutoLoadManagedWorlds(Boolean.parseBoolean(value))),
                setting(Category.GENERAL, "evacuationWorld", RiftMessages.GUI_EVACUATION, Material.COMPASS,
                        SettingKind.TEXT, 1D, RiftConfig::getEvacuationWorld,
                        (config, value) -> config.setEvacuationWorld(optionalWorld(value))),
                setting(Category.GENERAL, "allowWorldDeletion", RiftMessages.GUI_DELETE, Material.HOPPER,
                        SettingKind.BOOLEAN, 1D, config -> Boolean.toString(config.isAllowWorldDeletion()),
                        (config, value) -> config.setAllowWorldDeletion(Boolean.parseBoolean(value))),
                setting(Category.GENERAL, "deleteConfirmationSeconds", RiftMessages.GUI_CONFIRM_SECONDS, Material.CLOCK,
                        SettingKind.INTEGER, 5D, config -> Integer.toString(config.getDeleteConfirmationSeconds()),
                        (config, value) -> config.setDeleteConfirmationSeconds(Integer.parseInt(value.trim()))),
                setting(Category.DIAGNOSTICS, "hotReloadPollMillis", RiftMessages.GUI_POLL, Material.REPEATER,
                        SettingKind.LONG, 250D, config -> Long.toString(config.getHotReloadPollMillis()),
                        (config, value) -> config.setHotReloadPollMillis(Long.parseLong(value.trim()))),
                setting(Category.DIAGNOSTICS, "hotReloadCooldownMillis", RiftMessages.GUI_COOLDOWN, Material.REDSTONE_TORCH,
                        SettingKind.LONG, 250D, config -> Long.toString(config.getHotReloadCooldownMillis()),
                        (config, value) -> config.setHotReloadCooldownMillis(Long.parseLong(value.trim()))),

                setting(Category.FEEDBACK, "worldLifecycleFeedback", RiftMessages.GUI_LIFECYCLE_FEEDBACK,
                        Material.RESPAWN_ANCHOR, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.isWorldLifecycleFeedback()),
                        (config, value) -> config.setWorldLifecycleFeedback(Boolean.parseBoolean(value))),
                setting(Category.FEEDBACK, "teleportFeedback", RiftMessages.GUI_TELEPORT_FEEDBACK,
                        Material.ENDER_PEARL, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.isTeleportFeedback()),
                        (config, value) -> config.setTeleportFeedback(Boolean.parseBoolean(value))),
                setting(Category.FEEDBACK, "failureFeedback", RiftMessages.GUI_FAILURE_FEEDBACK,
                        Material.BARRIER, SettingKind.BOOLEAN, 1D,
                        config -> Boolean.toString(config.isFailureFeedback()),
                        (config, value) -> config.setFailureFeedback(Boolean.parseBoolean(value))),
                setting(Category.FEEDBACK, "sounds", RiftMessages.GUI_SOUNDS, Material.NOTE_BLOCK,
                        SettingKind.BOOLEAN, 1D, config -> Boolean.toString(config.isSounds()),
                        (config, value) -> config.setSounds(Boolean.parseBoolean(value))),
                setting(Category.FEEDBACK, "worldLifecycleSound", RiftMessages.GUI_LIFECYCLE_SOUND, Material.JUKEBOX,
                        SettingKind.TEXT, 1D, RiftConfig::worldLifecycleSound,
                        (config, value) -> config.setWorldLifecycleSound(value.trim())),
                setting(Category.FEEDBACK, "teleportSound", RiftMessages.GUI_TELEPORT_SOUND, Material.MUSIC_DISC_CAT,
                        SettingKind.TEXT, 1D, RiftConfig::teleportSound,
                        (config, value) -> config.setTeleportSound(value.trim())),
                setting(Category.FEEDBACK, "failureSound", RiftMessages.GUI_FAILURE_SOUND, Material.GOAT_HORN,
                        SettingKind.TEXT, 1D, RiftConfig::failureSound,
                        (config, value) -> config.setFailureSound(value.trim())),
                setting(Category.FEEDBACK, "soundVolume", RiftMessages.GUI_SOUND_VOLUME, Material.ECHO_SHARD,
                        SettingKind.FLOAT, 0.1D, config -> Float.toString(config.getSoundVolume()),
                        (config, value) -> config.setSoundVolume(Float.parseFloat(value.trim()))),
                setting(Category.FEEDBACK, "soundPitch", RiftMessages.GUI_SOUND_PITCH, Material.AMETHYST_SHARD,
                        SettingKind.FLOAT, 0.1D, config -> Float.toString(config.getSoundPitch()),
                        (config, value) -> config.setSoundPitch(Float.parseFloat(value.trim()))),

                setting(Category.PRESENTATION, "splashScreen", RiftMessages.GUI_SPLASH, Material.FIREWORK_STAR,
                        SettingKind.BOOLEAN, 1D, config -> Boolean.toString(config.isSplashScreen()),
                        (config, value) -> config.setSplashScreen(Boolean.parseBoolean(value))),
                setting(Category.PRESENTATION, "titlePopups", RiftMessages.GUI_TITLE_POPUPS, Material.OAK_SIGN,
                        SettingKind.BOOLEAN, 1D, config -> Boolean.toString(config.isTitlePopups()),
                        (config, value) -> config.setTitlePopups(Boolean.parseBoolean(value))),
                setting(Category.PRESENTATION, "actionBarPopups", RiftMessages.GUI_ACTION_BAR_POPUPS, Material.NAME_TAG,
                        SettingKind.BOOLEAN, 1D, config -> Boolean.toString(config.isActionBarPopups()),
                        (config, value) -> config.setActionBarPopups(Boolean.parseBoolean(value))),
                setting(Category.PRESENTATION, "titleFadeInTicks", RiftMessages.GUI_TITLE_FADE_IN, Material.LIGHT_GRAY_DYE,
                        SettingKind.INTEGER, 5D, config -> Integer.toString(config.getTitleFadeInTicks()),
                        (config, value) -> config.setTitleFadeInTicks(Integer.parseInt(value.trim()))),
                setting(Category.PRESENTATION, "titleStayTicks", RiftMessages.GUI_TITLE_STAY, Material.PURPLE_DYE,
                        SettingKind.INTEGER, 5D, config -> Integer.toString(config.getTitleStayTicks()),
                        (config, value) -> config.setTitleStayTicks(Integer.parseInt(value.trim()))),
                setting(Category.PRESENTATION, "titleFadeOutTicks", RiftMessages.GUI_TITLE_FADE_OUT, Material.BLACK_DYE,
                        SettingKind.INTEGER, 5D, config -> Integer.toString(config.getTitleFadeOutTicks()),
                        (config, value) -> config.setTitleFadeOutTicks(Integer.parseInt(value.trim()))),

                setting(Category.DIAGNOSTICS, "verbose", RiftMessages.GUI_VERBOSE, Material.SPYGLASS,
                        SettingKind.BOOLEAN, 1D, config -> Boolean.toString(config.isVerbose()),
                        (config, value) -> config.setVerbose(Boolean.parseBoolean(value))),
                setting(Category.DIAGNOSTICS, "debugUploadEnabled", RiftMessages.GUI_DEBUG_UPLOAD, Material.MAP,
                        SettingKind.BOOLEAN, 1D, config -> Boolean.toString(config.isDebugUploadEnabled()),
                        (config, value) -> config.setDebugUploadEnabled(Boolean.parseBoolean(value))),
                setting(Category.DIAGNOSTICS, "bstatsEnabled", RiftMessages.GUI_BSTATS, Material.COMPARATOR,
                        SettingKind.BOOLEAN, 1D, config -> Boolean.toString(config.isBstatsEnabled()),
                        (config, value) -> config.setBstatsEnabled(Boolean.parseBoolean(value)))
        );
    }

    private static Setting setting(
            Category category,
            String path,
            TextKey name,
            Material material,
            SettingKind kind,
            double step,
            Function<RiftConfig, String> reader,
            SettingWriter writer
    ) {
        return new Setting(category, path, name, material, kind, step, reader, writer);
    }

    private enum Category {
        GENERAL(RiftMessages.GUI_CATEGORY_GENERAL, Material.COMMAND_BLOCK),
        FEEDBACK(RiftMessages.GUI_CATEGORY_FEEDBACK, Material.NOTE_BLOCK),
        PRESENTATION(RiftMessages.GUI_CATEGORY_PRESENTATION, Material.PAINTING),
        DIAGNOSTICS(RiftMessages.GUI_CATEGORY_DIAGNOSTICS, Material.SPYGLASS),
        LANGUAGES(RiftMessages.GUI_CATEGORY_LANGUAGES, Material.BOOKSHELF);

        private final TextKey displayName;
        private final Material material;

        Category(TextKey displayName, Material material) {
            this.displayName = displayName;
            this.material = material;
        }

        private TextKey displayName() {
            return displayName;
        }

        private Material material() {
            return material;
        }
    }

    private enum SettingKind {
        BOOLEAN,
        INTEGER,
        LONG,
        FLOAT,
        TEXT,
        LOCALE;

        private boolean numeric() {
            return this == INTEGER || this == LONG || this == FLOAT;
        }
    }

    @FunctionalInterface
    private interface SettingWriter {
        void write(RiftConfig config, String value);
    }

    private record Setting(
            Category category,
            String path,
            TextKey name,
            Material material,
            SettingKind kind,
            double step,
            Function<RiftConfig, String> reader,
            SettingWriter writer
    ) {
    }

    private record PromptSession(long id, Category category, Setting setting) {
    }


    private static final class EditorHolder implements InventoryHolder {
        private final Category category;
        private Inventory inventory;

        private EditorHolder(Category category) {
            this.category = category;
        }

        private static EditorHolder root() {
            return new EditorHolder(null);
        }

        private static EditorHolder category(Category category) {
            return new EditorHolder(category);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private Category category() {
            return category;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }
}
