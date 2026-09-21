package com.volmit.rift.gui;

import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.localization.RiftMessages;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.world.WorldPolicyService;
import com.volmit.rift.world.WorldPolicyService.GameRuleSnapshot;
import com.volmit.rift.world.WorldBorderSetting;
import com.volmit.rift.world.RiftWorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class RiftWorldPolicyMenu implements Listener {
    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 28;
    private final Rift plugin;
    private final WorldPolicyService policies;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, SpawnDraft> drafts = new ConcurrentHashMap<>();
    private final Map<UUID, SpawnDraft> spawnBaselines = new ConcurrentHashMap<>();

    public RiftWorldPolicyMenu(Rift plugin) {
        this.plugin = plugin;
        policies = plugin.worldPolicies();
    }

    public void open(Player player, String name) {
        FoliaScheduler.runEntity(plugin, player, () -> {
            plugin.configMenu().cancelPrompt(player.getUniqueId());
            drafts.remove(player.getUniqueId());
            spawnBaselines.remove(player.getUniqueId());
            show(player, new Page(name.equalsIgnoreCase("all") ? "" : name, View.ROOT, 0));
        });
    }

    public void cancelPrompt(UUID playerId) {
        prompts.remove(playerId);
        sessions.remove(playerId);
    }

    public void shutdown() {
        prompts.clear();
        sessions.clear();
        drafts.clear();
        spawnBaselines.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= SIZE) {
            return;
        }
        ClickType click = event.getClick();
        if (!supported(click)) {
            return;
        }
        BiConsumer<Player, ClickType> action = holder.actions.get(event.getRawSlot());
        if (action != null) {
            FoliaScheduler.runEntity(plugin, player, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() == holder && allowed(player)) {
                    LanguageAudience.run(player.getUniqueId(), () -> action.accept(player, click));
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String value = event.getMessage();
        FoliaScheduler.runEntity(plugin, player, () -> {
            if (!prompt.id().equals(sessions.get(player.getUniqueId())) || !allowed(player)) {
                return;
            }
            if (value.equalsIgnoreCase("cancel")) {
                plugin.language().send(player, RiftMessages.GUI_PROMPT_CANCELLED);
                show(player, prompt.page());
            } else {
                save(player, prompt.page(), () -> {
                    if (value.length() > 512) {
                        throw new IllegalArgumentException("Value must contain at most 512 characters");
                    }
                    return prompt.save().apply(value.trim());
                });
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        cancelPrompt(event.getPlayer().getUniqueId());
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            drafts.remove(event.getPlayer().getUniqueId());
            spawnBaselines.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPrompt(event.getPlayer().getUniqueId());
        drafts.remove(event.getPlayer().getUniqueId());
        spawnBaselines.remove(event.getPlayer().getUniqueId());
    }

    private boolean allowed(Player player) {
        if (player.hasPermission("rift.policy") || player.hasPermission("rift.admin")) {
            return true;
        }
        cancelPrompt(player.getUniqueId());
        player.closeInventory();
        plugin.language().send(player, RiftMessages.PERMISSION_DENIED,
                MessageArgs.builder().untrusted("permission", "rift.policy").build());
        return false;
    }

    private void show(Player player, Page page) {
        if (!allowed(player)) {
            return;
        }
        cancelPrompt(player.getUniqueId());
        if (page.view() == View.SPAWN && !drafts.containsKey(player.getUniqueId())) {
            inspectSpawn(player, page);
            return;
        }
        if (page.view() != View.RULES) {
            LanguageAudience.run(player.getUniqueId(), () -> render(player, page, new GameRuleSnapshot(false, Map.of())));
            return;
        }
        UUID id = UUID.randomUUID();
        Inventory expected = player.getOpenInventory().getTopInventory();
        sessions.put(player.getUniqueId(), id);
        policies.gameRuleValues(page.world()).whenComplete((values, failure) -> FoliaScheduler.runEntity(plugin, player, () -> {
            if (!sessions.remove(player.getUniqueId(), id)
                    || player.getOpenInventory().getTopInventory() != expected || !allowed(player)) {
                return;
            }
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Unable to inspect game rules for " + page.world(), failure);
                plugin.language().send(player, RiftMessages.CONFIG_SAVE_FAILED);
                return;
            }
            LanguageAudience.run(player.getUniqueId(), () -> render(player, page, values));
        }));
    }

    private void inspectSpawn(Player player, Page page) {
        UUID id = UUID.randomUUID();
        Inventory expected = player.getOpenInventory().getTopInventory();
        sessions.put(player.getUniqueId(), id);
        policies.spawnLocation(page.world()).whenComplete((location, failure) -> FoliaScheduler.runEntity(plugin, player, () -> {
            if (!sessions.remove(player.getUniqueId(), id)
                    || player.getOpenInventory().getTopInventory() != expected || !allowed(player)) {
                return;
            }
            if (failure != null) {
                plugin.getLogger().log(Level.WARNING, "Unable to inspect spawn for " + page.world(), failure);
                plugin.language().send(player, RiftMessages.CONFIG_SAVE_FAILED);
                return;
            }
            WorldProfile profile = plugin.profiles().find(page.world()).orElse(null);
            if (profile == null) {
                show(player, new Page("", View.ROOT, 0));
                return;
            }
            SpawnDraft baseline = location == null ? SpawnDraft.from(profile)
                    : new SpawnDraft(page.world(), location.getX(), location.getY(), location.getZ(), location.getYaw());
            spawnBaselines.put(player.getUniqueId(), baseline);
            drafts.put(player.getUniqueId(), profile.isCustomSpawn() ? SpawnDraft.from(profile) : baseline);
            LanguageAudience.run(player.getUniqueId(), () -> render(player, page, new GameRuleSnapshot(false, Map.of())));
        }));
    }

    private void render(Player player, Page page, GameRuleSnapshot effectiveRules) {
        WorldProfile profile = page.world().isEmpty() ? null : plugin.profiles().find(page.world()).orElse(null);
        if (!page.world().isEmpty() && profile == null) {
            plugin.language().send(player, RiftMessages.UNKNOWN_WORLD,
                    MessageArgs.builder().untrusted("world", page.world()).build());
            show(player, new Page("", View.ROOT, 0));
            return;
        }
        Holder holder = new Holder();
        String title = text(RiftMessages.GUI_POLICY_TITLE) + (profile == null ? "" : " : " + profile.getName());
        holder.inventory = Bukkit.createInventory(holder, SIZE, title);
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            holder.inventory.setItem(slot, filler);
        }
        button(holder, 45, Material.ARROW, text(RiftMessages.GUI_BACK), List.of(), (target, right) -> {
            if (page.world().isEmpty()) {
                plugin.configMenu().open(target);
            } else {
                show(target, new Page(page.view() == View.ROOT ? "" : page.world(), View.ROOT, 0));
            }
        });
        button(holder, 53, Material.BARRIER, text(RiftMessages.GUI_CLOSE), List.of(), (target, right) -> target.closeInventory());
        if (profile == null) {
            worlds(holder, page, null);
        } else {
            switch (page.view()) {
                case ROOT -> fields(holder, page, profile);
                case RULES -> rules(holder, page, profile, effectiveRules);
                case DIFFICULTY -> choices(holder, page, profile.getDifficulty(), List.of("INHERIT", "PEACEFUL", "EASY", "NORMAL", "HARD"));
                case PVP -> choices(holder, page, profile.getPvp(), List.of("INHERIT", "ALLOW", "DENY"));
                case RESPAWN -> worlds(holder, page, profile);
                case BORDER -> border(holder, page, profile);
                case SPAWN -> spawn(holder, page, profile, player);
            }
        }
        player.openInventory(holder.inventory);
    }

    private void worlds(Holder holder, Page page, WorldProfile profile) {
        List<WorldProfile> worlds = new ArrayList<>(plugin.profiles().all());
        worlds.sort(Comparator.comparing(WorldProfile::getName, String.CASE_INSENSITIVE_ORDER));
        if (profile != null) {
            button(holder, 4, Material.RED_BED, text(RiftMessages.LABEL_SERVER_DEFAULT),
                    values(emptyDefault(profile.getRespawnWorld()), text(RiftMessages.LABEL_SERVER_DEFAULT)),
                    (target, click) -> save(target, page, () -> policies.setRespawn(target, page.world(), "default")));
        }
        if (worlds.isEmpty()) {
            holder.inventory.setItem(22, item(Material.PAPER, text(RiftMessages.EMPTY_LIST), List.of()));
            return;
        }
        int offset = pageOffset(page.index(), worlds.size());
        for (int i = offset; i < Math.min(offset + PAGE_SIZE, worlds.size()); i++) {
            String name = worlds.get(i).getName();
            List<String> lore = profile == null ? List.of(text(RiftMessages.GUI_CATEGORY_OPEN))
                    : values(emptyDefault(profile.getRespawnWorld()), text(RiftMessages.LABEL_SERVER_DEFAULT));
            if (profile != null && name.equalsIgnoreCase(profile.getRespawnWorld())) {
                lore.add(text(RiftMessages.GUI_POLICY_SELECTED));
            }
            button(holder, contentSlot(i - offset), Material.GRASS_BLOCK, "§d" + name,
                    lore, (target, click) -> {
                        if (profile == null) {
                            show(target, new Page(name, View.ROOT, 0));
                        } else {
                            save(target, page, () -> policies.setRespawn(target, page.world(), name));
                        }
                    });
        }
        pagination(holder, page, offset, worlds.size());
    }

    private void fields(Holder holder, Page page, WorldProfile profile) {
        Field[] fields = Field.values();
        int[] slots = RiftConfigMenu.settingSlots(fields.length + 1);
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            List<String> lore = values(field.value(profile, this), field.defaultValue(this));
            lore.add(text(field.textInput() ? RiftMessages.GUI_TEXT : RiftMessages.GUI_POLICY_SELECT));
            if (field.textInput()) {
                lore.add(text(field == Field.TAGS ? RiftMessages.GUI_POLICY_TAGS : RiftMessages.GUI_POLICY_CLEAR));
            }
            lore.add(text(RiftMessages.GUI_POLICY_RESET));
            button(holder, slots[i], field.material(), "§d" + text(field.label()), lore,
                    (target, click) -> fieldClick(target, page, field, click));
        }
        button(holder, slots[fields.length], Material.BOOK, "§d" + text(RiftMessages.LABEL_POLICY_GAME_RULES),
                values(profile.getGameRules().isEmpty() ? "INHERIT" : profile.getGameRules().toString(), "INHERIT"),
                (target, click) -> show(target, new Page(page.world(), View.RULES, 0)));
    }

    private void fieldClick(Player player, Page page, Field field, ClickType click) {
        if (reset(click)) {
            save(player, page, () -> resetField(player, page.world(), field));
        } else if (field.textInput()) {
            prompt(player, page, text(field.label()), input -> changeText(player, page.world(), field, input));
        } else {
            if (field == Field.SPAWN) {
                drafts.remove(player.getUniqueId());
            }
            show(player, new Page(page.world(), View.valueOf(field.name()), 0));
        }
    }

    private void choices(Holder holder, Page page, String current, List<String> choices) {
        int[] slots = RiftConfigMenu.settingSlots(choices.size());
        for (int i = 0; i < choices.size(); i++) {
            String choice = choices.get(i);
            List<String> lore = values(current, "INHERIT");
            if (choice.equals(current)) {
                lore.add(text(RiftMessages.GUI_POLICY_SELECTED));
            }
            button(holder, slots[i], choice.equals(current) ? Material.LIME_DYE : Material.GRAY_DYE, "§d" + choice, lore,
                    (target, click) -> save(target, page, () -> page.view() == View.PVP
                            ? policies.setPvp(target, page.world(), choice) : policies.setDifficulty(target, page.world(), choice)));
        }
    }

    private void border(Holder holder, Page page, WorldProfile profile) {
        WorldBorderSetting[] settings = WorldBorderSetting.values();
        int[] slots = RiftConfigMenu.settingSlots(settings.length + 1);
        List<String> managed = values(Boolean.toString(profile.isManagedBorder()), "false");
        managed.add(text(RiftMessages.GUI_TOGGLE));
        managed.add(text(RiftMessages.GUI_POLICY_RESET));
        button(holder, slots[0], Material.LEVER, text(RiftMessages.GUI_POLICY_ENABLED), managed,
                (target, click) -> save(target, page, () -> reset(click) ? policies.clearBorder(target, page.world())
                        : policies.toggleBorder(target, page.world())));
        for (int i = 0; i < settings.length; i++) {
            WorldBorderSetting setting = settings[i];
            List<String> lore = values(number(setting.read(profile)), number(setting.defaultValue()));
            lore.add(numberHint(setting.step()));
            lore.add(text(RiftMessages.GUI_POLICY_RESET));
            button(holder, slots[i + 1], Material.IRON_BARS, text(borderLabel(setting)), lore,
                    (target, click) -> save(target, page,
                            () -> policies.adjustBorder(target, page.world(), setting, steps(click), reset(click))));
        }
    }

    private void spawn(Holder holder, Page page, WorldProfile profile, Player player) {
        SpawnDraft draft = drafts.compute(player.getUniqueId(), (id, existing) -> existing != null && existing.world().equals(page.world())
                ? existing : SpawnDraft.from(profile));
        List<String> managed = values(Boolean.toString(profile.isCustomSpawn()), "false");
        managed.add(text(RiftMessages.GUI_TOGGLE));
        managed.add(text(RiftMessages.GUI_POLICY_RESET));
        button(holder, 13, Material.LEVER, text(RiftMessages.GUI_POLICY_ENABLED), managed,
                (target, click) -> save(target, page, () -> profile.isCustomSpawn() || reset(click)
                        ? policies.clearSpawn(target, page.world()) : applySpawn(target, draft)));
        SpawnAxis[] axes = SpawnAxis.values();
        int[] slots = {20, 21, 23, 24};
        SpawnDraft defaults = SpawnDraft.from(new WorldProfile());
        SpawnDraft stored = profile.isCustomSpawn() ? SpawnDraft.from(profile)
                : spawnBaselines.getOrDefault(player.getUniqueId(), SpawnDraft.from(profile));
        for (int i = 0; i < axes.length; i++) {
            SpawnAxis axis = axes[i];
            List<String> lore = values(number(stored.value(axis)), number(defaults.value(axis)));
            lore.add(valueLine(RiftMessages.GUI_POLICY_DRAFT, number(draft.value(axis))));
            lore.add(numberHint(axis == SpawnAxis.YAW ? 5 : 1));
            lore.add(text(RiftMessages.GUI_POLICY_RESET));
            button(holder, slots[i], Material.COMPASS, text(axis.label()), lore, (target, click) -> {
                drafts.put(target.getUniqueId(), draft.adjust(axis, steps(click), reset(click)));
                show(target, page);
            });
        }
        button(holder, 30, Material.LIME_DYE, text(RiftMessages.GUI_POLICY_APPLY), List.of(text(RiftMessages.GUI_POLICY_SPAWN)),
                (target, click) -> save(target, page, () -> applySpawn(target, draft)));
        button(holder, 32, Material.COMPASS, text(RiftMessages.GUI_POLICY_HERE), List.of(text(RiftMessages.GUI_POLICY_SPAWN)),
                (target, click) -> {
                    String logical = RiftWorldIdentity.logicalName(target.getWorld(), plugin.profiles());
                    if (!logical.equalsIgnoreCase(page.world())) {
                        save(target, page, () -> policies.setSpawn(target, page.world(), target));
                        return;
                    }
                    Location location = target.getLocation();
                    drafts.put(target.getUniqueId(), new SpawnDraft(page.world(), location.getX(), location.getY(), location.getZ(), location.getYaw()));
                    show(target, page);
                });
    }

    private CompletableFuture<Boolean> applySpawn(Player player, SpawnDraft draft) {
        return policies.setSpawnCoordinates(player, draft.world(), draft.x(), draft.y(), draft.z(), (float) draft.yaw())
                .thenApply(success -> {
                    if (success) {
                        drafts.remove(player.getUniqueId(), draft);
                    }
                    return success;
                });
    }

    private void rules(Holder holder, Page page, WorldProfile profile, GameRuleSnapshot snapshot) {
        Map<String, String> effective = snapshot.values();
        List<GameRule<?>> rules = new ArrayList<>();
        for (GameRule<?> rule : Registry.GAME_RULE) {
            if (!rule.equals(GameRules.PVP) && effective.containsKey(Registry.GAME_RULE.getKeyOrThrow(rule).toString())) {
                rules.add(rule);
            }
        }
        rules.sort(Comparator.comparing(rule -> Registry.GAME_RULE.getKeyOrThrow(rule).toString()));
        Map<String, String> configured = profile.getGameRules();
        int offset = pageOffset(page.index(), rules.size());
        for (int i = offset; i < Math.min(offset + PAGE_SIZE, rules.size()); i++) {
            GameRule<?> rule = rules.get(i);
            String key = Registry.GAME_RULE.getKeyOrThrow(rule).toString();
            boolean toggle = rule.getType().equals(Boolean.class);
            String value = configured.getOrDefault(key, effective.getOrDefault(key, String.valueOf(rule.getDefaultValue())));
            List<String> lore = values(value, "INHERIT");
            lore.add(valueLine(RiftMessages.GUI_POLICY_NATIVE_DEFAULT, String.valueOf(rule.getDefaultValue())));
            if (!configured.containsKey(key)) {
                lore.add(text(snapshot.loaded() ? RiftMessages.GUI_POLICY_INHERITED : RiftMessages.GUI_POLICY_UNLOADED));
            }
            lore.add(toggle ? text(RiftMessages.GUI_TOGGLE) : numberHint(1));
            lore.add(text(RiftMessages.GUI_POLICY_RESET));
            button(holder, contentSlot(i - offset), toggle ? Material.LEVER : Material.PAPER, "§d" + key, lore,
                    (target, click) -> save(target, page, () -> reset(click)
                            ? policies.setGameRule(target, page.world(), key, "INHERIT")
                            : policies.adjustGameRule(target, page.world(), rule, steps(click))));
        }
        pagination(holder, page, offset, rules.size());
    }

    private void pagination(Holder holder, Page page, int offset, int total) {
        int current = offset / PAGE_SIZE;
        if (offset > 0) {
            button(holder, 48, Material.ARROW, text(RiftMessages.GUI_POLICY_PREVIOUS), List.of(),
                    (target, right) -> show(target, new Page(page.world(), page.view(), current - 1)));
        }
        if (offset + PAGE_SIZE < total) {
            button(holder, 50, Material.ARROW, text(RiftMessages.GUI_POLICY_NEXT), List.of(),
                    (target, right) -> show(target, new Page(page.world(), page.view(), current + 1)));
        }
    }

    private void prompt(Player player, Page page, String label, Function<String, CompletableFuture<Boolean>> action) {
        UUID id = UUID.randomUUID();
        Prompt prompt = new Prompt(id, page, action);
        sessions.put(player.getUniqueId(), id);
        prompts.put(player.getUniqueId(), prompt);
        player.closeInventory();
        plugin.language().send(player, RiftMessages.GUI_PROMPT, MessageArgs.builder().untrusted("setting", label).build());
        plugin.language().send(player, RiftMessages.GUI_PROMPT_CANCEL);
        if (!FoliaScheduler.runEntity(plugin, player, () -> {
            if (prompts.remove(player.getUniqueId(), prompt)) {
                plugin.language().send(player, RiftMessages.GUI_PROMPT_TIMEOUT);
                show(player, page);
            }
        }, 1200L, () -> cancelPrompt(player.getUniqueId()))) {
            cancelPrompt(player.getUniqueId());
            plugin.language().send(player, RiftMessages.GUI_PROMPT_CANCELLED);
        }
    }

    private void save(Player player, Page page, Supplier<CompletableFuture<Boolean>> action) {
        if (!allowed(player)) {
            return;
        }
        UUID id = UUID.randomUUID();
        sessions.put(player.getUniqueId(), id);
        player.closeInventory();
        try {
            action.get().thenAccept(success -> FoliaScheduler.runEntity(plugin, player, () -> {
                if (sessions.remove(player.getUniqueId(), id)) {
                    show(player, page);
                }
            }));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Invalid policy input for " + page.world(), exception);
            plugin.language().send(player, RiftMessages.GUI_SETTING_FAILED, MessageArgs.builder()
                    .untrusted("setting", page.world()).untrusted("reason", exception.getMessage()).build());
            show(player, page);
        }
    }

    private CompletableFuture<Boolean> resetField(Player player, String world, Field field) {
        return switch (field) {
            case DIFFICULTY -> policies.setDifficulty(player, world, "INHERIT");
            case PVP -> policies.setPvp(player, world, "INHERIT");
            case SPAWN -> policies.clearSpawn(player, world);
            case BORDER -> policies.clearBorder(player, world);
            case RESPAWN -> policies.setRespawn(player, world, "default");
            case ACCESS, DENIAL, TAGS -> changeText(player, world, field, "clear");
        };
    }

    private CompletableFuture<Boolean> changeText(Player player, String world, Field field, String input) {
        return switch (field) {
            case ACCESS -> policies.setAccessPermission(player, world, input);
            case DENIAL -> policies.setAccessDenial(player, world, input);
            case TAGS -> policies.setTags(player, world, input.equalsIgnoreCase("clear") ? List.of() : Arrays.asList(input.split(",")));
            default -> throw new IllegalArgumentException("This policy uses inventory controls");
        };
    }

    static long steps(ClickType click) {
        return (click.isRightClick() ? -1L : 1L) * (click.isShiftClick() ? 10L : 1L);
    }

    static boolean reset(ClickType click) {
        return click == ClickType.DROP || click == ClickType.CONTROL_DROP;
    }

    static boolean supported(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT || click == ClickType.SHIFT_LEFT
                || click == ClickType.SHIFT_RIGHT || reset(click);
    }

    static int pageOffset(int page, int size) {
        return Math.max(0, Math.min(page, Math.max(0, (size - 1) / PAGE_SIZE))) * PAGE_SIZE;
    }

    static int contentSlot(int index) {
        if (index < 0 || index >= PAGE_SIZE) {
            throw new IllegalArgumentException("Page index must be between 0 and 27");
        }
        return 10 + (index / 7) * 9 + index % 7;
    }

    private List<String> values(String current, String defaults) {
        return new ArrayList<>(List.of(valueLine(RiftMessages.GUI_CURRENT, current), valueLine(RiftMessages.GUI_POLICY_DEFAULT, defaults)));
    }

    private String valueLine(TextKey key, String value) {
        return plugin.language().text(key, MessageArgs.builder().untrusted("value", value).build()).legacy();
    }

    private String numberHint(double step) {
        return plugin.language().text(RiftMessages.GUI_POLICY_NUMBER, MessageArgs.builder().untrusted("step", number(step)).build()).legacy();
    }

    private String emptyDefault(String value) {
        return value.isEmpty() ? text(RiftMessages.LABEL_SERVER_DEFAULT) : value;
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String text(TextKey key) {
        return plugin.language().text(key).legacy();
    }

    private void button(Holder holder, int slot, Material material, String title, List<String> lore,
                        BiConsumer<Player, ClickType> action) {
        holder.inventory.setItem(slot, item(material, title, lore));
        holder.actions.put(slot, action);
    }

    private ItemStack item(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static TextKey borderLabel(WorldBorderSetting setting) {
        return switch (setting) {
            case SIZE -> RiftMessages.GUI_POLICY_BORDER_SIZE;
            case CENTER_X -> RiftMessages.GUI_POLICY_X;
            case CENTER_Z -> RiftMessages.GUI_POLICY_Z;
            case WARNING_DISTANCE -> RiftMessages.GUI_POLICY_WARNING_DISTANCE;
            case WARNING_TIME -> RiftMessages.GUI_POLICY_WARNING_TIME;
            case DAMAGE_AMOUNT -> RiftMessages.GUI_POLICY_DAMAGE_AMOUNT;
            case DAMAGE_BUFFER -> RiftMessages.GUI_POLICY_DAMAGE_BUFFER;
        };
    }

    private enum Field {
        DIFFICULTY, PVP, SPAWN, BORDER, ACCESS, DENIAL, RESPAWN, TAGS;

        private boolean textInput() {
            return this == ACCESS || this == DENIAL || this == TAGS;
        }

        private TextKey label() {
            return switch (this) {
                case DIFFICULTY -> RiftMessages.LABEL_POLICY_DIFFICULTY;
                case PVP -> RiftMessages.LABEL_POLICY_PVP;
                case SPAWN -> RiftMessages.LABEL_POLICY_CUSTOM_SPAWN;
                case BORDER -> RiftMessages.LABEL_POLICY_MANAGED_BORDER;
                case ACCESS -> RiftMessages.LABEL_POLICY_ACCESS_PERMISSION;
                case DENIAL -> RiftMessages.GUI_POLICY_DENIAL;
                case RESPAWN -> RiftMessages.LABEL_POLICY_RESPAWN_WORLD;
                case TAGS -> RiftMessages.LABEL_POLICY_TAGS;
            };
        }

        private Material material() {
            return switch (this) {
                case DIFFICULTY -> Material.IRON_SWORD;
                case PVP -> Material.SHIELD;
                case SPAWN -> Material.COMPASS;
                case BORDER -> Material.IRON_BARS;
                case ACCESS -> Material.TRIPWIRE_HOOK;
                case DENIAL -> Material.PAPER;
                case RESPAWN -> Material.RED_BED;
                case TAGS -> Material.NAME_TAG;
            };
        }

        private String value(WorldProfile profile, RiftWorldPolicyMenu menu) {
            return switch (this) {
                case DIFFICULTY -> profile.getDifficulty();
                case PVP -> profile.getPvp();
                case SPAWN -> Boolean.toString(profile.isCustomSpawn());
                case BORDER -> Boolean.toString(profile.isManagedBorder());
                case ACCESS -> profile.getAccessPermission().isEmpty() ? menu.text(RiftMessages.LABEL_NONE) : profile.getAccessPermission();
                case DENIAL -> menu.emptyDefault(profile.getAccessDeniedMessage());
                case RESPAWN -> menu.emptyDefault(profile.getRespawnWorld());
                case TAGS -> profile.getTags().isEmpty() ? menu.text(RiftMessages.LABEL_NONE) : String.join(", ", profile.getTags());
            };
        }

        private String defaultValue(RiftWorldPolicyMenu menu) {
            return value(new WorldProfile(), menu);
        }
    }

    private enum View { ROOT, RULES, DIFFICULTY, PVP, RESPAWN, BORDER, SPAWN }
    private enum SpawnAxis {
        X, Y, Z, YAW;

        private TextKey label() {
            return switch (this) {
                case X -> RiftMessages.GUI_POLICY_X;
                case Y -> RiftMessages.GUI_POLICY_Y;
                case Z -> RiftMessages.GUI_POLICY_Z;
                case YAW -> RiftMessages.GUI_POLICY_YAW;
            };
        }
    }

    private record SpawnDraft(String world, double x, double y, double z, double yaw) {
        private static SpawnDraft from(WorldProfile profile) {
            return new SpawnDraft(profile.getName(), profile.getSpawnX(), profile.getSpawnY(), profile.getSpawnZ(), profile.getSpawnYaw());
        }

        private double value(SpawnAxis axis) {
            return switch (axis) { case X -> x; case Y -> y; case Z -> z; case YAW -> yaw; };
        }

        private SpawnDraft adjust(SpawnAxis axis, long steps, boolean reset) {
            double value = reset ? from(new WorldProfile()).value(axis) : value(axis) + steps * (axis == SpawnAxis.YAW ? 5.0D : 1.0D);
            value = axis == SpawnAxis.YAW ? ((value % 360) + 360) % 360 : Math.max(-29_999_984, Math.min(29_999_984, value));
            return switch (axis) {
                case X -> new SpawnDraft(world, value, y, z, yaw);
                case Y -> new SpawnDraft(world, x, value, z, yaw);
                case Z -> new SpawnDraft(world, x, y, value, yaw);
                case YAW -> new SpawnDraft(world, x, y, z, value);
            };
        }
    }

    private record Page(String world, View view, int index) { }
    private record Prompt(UUID id, Page page, Function<String, CompletableFuture<Boolean>> save) { }

    private static final class Holder implements InventoryHolder {
        private final Map<Integer, BiConsumer<Player, ClickType>> actions = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
