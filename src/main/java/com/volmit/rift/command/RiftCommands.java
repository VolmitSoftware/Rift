package com.volmit.rift.command;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.volmit.rift.Rift;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import com.volmit.rift.storage.TrashEntry;
import com.volmit.rift.world.PlatformCapabilities;
import com.volmit.rift.world.WorldInventory;
import com.volmit.rift.world.WorldLifecycleService;
import com.volmit.rift.world.WorldSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Director(name = "rift", aliases = {"rft"}, description = "Manage server worlds safely", descriptionKey = "rift.command.root")
public final class RiftCommands {
    private static final int LIST_PAGE_SIZE = 15;

    private final Rift plugin;
    private final RiftLocalization language;
    private final WorldLifecycleService lifecycle;
    private final WorldInventory inventory;
    private final PlatformCapabilities capabilities;
    private RiftDebugCommands debug;

    public RiftCommands(Rift plugin) {
        this.plugin = plugin;
        this.language = plugin.language();
        this.lifecycle = plugin.lifecycle();
        this.inventory = plugin.worldInventory();
        this.capabilities = plugin.capabilities();
        debug = new RiftDebugCommands(plugin);
    }

    @Director(name = "version", hidden = true, description = "Show the Rift version", descriptionKey = "rift.command.version")
    public void version(@Param(name = "sender", contextual = true) CommandSender sender) {
        debug.version(sender);
    }

    @Director(name = "create", description = "Create and manage a new world", descriptionKey = "rift.command.create")
    public void create(
            @Param(name = "name", description = "World name", descriptionKey = "rift.parameter.name") String name,
            @Param(name = "environment", description = "World environment", descriptionKey = "rift.parameter.environment", defaultValue = "NORMAL") World.Environment environment,
            @Param(name = "generator", description = "Bukkit generator identifier", descriptionKey = "rift.parameter.generator", defaultValue = "vanilla", customHandler = RiftCommandHandlers.Generator.class) String generator,
            @Param(name = "seed", description = "World seed", descriptionKey = "rift.parameter.seed", defaultValue = "random") String seed,
            @Param(name = "type", description = "World type", descriptionKey = "rift.parameter.type", defaultValue = "NORMAL") WorldType type,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (allowed(sender, "rift.create")) {
            lifecycle.create(sender, name, environment, generator, seed, type);
        }
    }

    @Director(name = "import", description = "Import an existing world directory", descriptionKey = "rift.command.import")
    public void importWorld(
            @Param(name = "name", description = "World name", descriptionKey = "rift.parameter.name", customHandler = RiftCommandHandlers.KnownWorld.class) String name,
            @Param(name = "generator", description = "Bukkit generator identifier", descriptionKey = "rift.parameter.generator", defaultValue = "vanilla", customHandler = RiftCommandHandlers.Generator.class) String generator,
            @Param(name = "auto-load", description = "Load during startup", descriptionKey = "rift.parameter.enabled", defaultValue = "true") boolean autoLoad,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (allowed(sender, "rift.import")) {
            lifecycle.importWorld(sender, name, generator, autoLoad);
        }
    }

    @Director(name = "load", description = "Load a managed or discovered world", descriptionKey = "rift.command.load")
    public void load(
            @Param(name = "name", description = "World name", descriptionKey = "rift.parameter.name", customHandler = RiftCommandHandlers.KnownWorld.class) String name,
            @Param(name = "generator", description = "Generator for an unmanaged world", descriptionKey = "rift.parameter.generator", defaultValue = "vanilla", customHandler = RiftCommandHandlers.Generator.class) String generator,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (allowed(sender, "rift.load")) {
            lifecycle.load(sender, name, generator);
        }
    }

    @Director(name = "unload", description = "Evacuate players and unload a world", descriptionKey = "rift.command.unload")
    public void unload(
            @Param(name = "name", description = "Loaded world", descriptionKey = "rift.parameter.name", customHandler = RiftCommandHandlers.LoadedWorld.class) String name,
            @Param(name = "save", description = "Save before unloading", descriptionKey = "rift.parameter.save", defaultValue = "true") boolean save,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (allowed(sender, "rift.unload")) {
            lifecycle.unload(sender, name, save);
        }
    }

    @Director(name = "delete", description = "Move a managed world into quarantine", descriptionKey = "rift.command.delete")
    public void delete(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.name", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (allowed(sender, "rift.delete")) {
            lifecycle.delete(sender, name);
        }
    }

    @Director(name = "restore", description = "Restore a quarantined world", descriptionKey = "rift.command.restore")
    public void restore(
            @Param(name = "id", description = "Quarantine entry", descriptionKey = "rift.parameter.id", customHandler = RiftCommandHandlers.TrashId.class) String id,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (allowed(sender, "rift.restore")) {
            lifecycle.restore(sender, id);
        }
    }

    @Director(name = "tp", aliases = {"teleport"}, description = "Teleport to a loaded world", descriptionKey = "rift.command.teleport")
    public void teleport(
            @Param(name = "world", description = "Loaded world", descriptionKey = "rift.parameter.name", customHandler = RiftCommandHandlers.LoadedWorld.class) String world,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (!allowed(sender, "rift.teleport")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            language.send(sender, RiftMessages.PLAYER_ONLY);
            return;
        }
        lifecycle.teleport(sender, player, world);
    }

    @Director(name = "send", description = "Teleport another player to a loaded world", descriptionKey = "rift.command.send")
    public void send(
            @Param(name = "player", description = "Online player", descriptionKey = "rift.parameter.player", customHandler = RiftCommandHandlers.OnlinePlayer.class) String playerName,
            @Param(name = "world", description = "Loaded world", descriptionKey = "rift.parameter.name", customHandler = RiftCommandHandlers.LoadedWorld.class) String world,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (!allowed(sender, "rift.teleport.others")) {
            return;
        }
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            language.send(sender, RiftMessages.PLAYER_OFFLINE, MessageArgs.builder()
                    .untrusted("player", playerName)
                    .build());
            return;
        }
        lifecycle.teleport(sender, player, world);
    }

    @Director(name = "list", description = "List every world state", descriptionKey = "rift.command.list")
    public void list(
            @Param(name = "page", description = "Result page number", descriptionKey = "rift.parameter.page", defaultValue = "1") int page,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (!allowed(sender, "rift.list")) {
            return;
        }
        List<WorldSnapshot> snapshots = inventory.snapshots();
        List<TrashEntry> trashEntries = plugin.trash().all();
        List<String> entries = new ArrayList<>(snapshots.size() + trashEntries.size() + 2);
        if (!snapshots.isEmpty()) {
            entries.add(menuSection(sender, RiftMessages.LABEL_WORLDS, snapshots.size()));
        }
        for (WorldSnapshot snapshot : snapshots) {
            String state = label(sender, snapshot.loaded()
                    ? RiftMessages.LABEL_LOADED
                    : snapshot.presentOnDisk() ? RiftMessages.LABEL_ON_DISK : RiftMessages.LABEL_MISSING);
            String detail = snapshot.managed()
                    ? state + " | " + label(sender, RiftMessages.LABEL_MANAGED)
                    + " | " + label(sender, RiftMessages.LABEL_AUTOLOAD) + '=' + snapshot.autoLoad()
                    + " | " + label(sender, RiftMessages.LABEL_PROTECTED) + '=' + snapshot.protectedWorld()
                    : state;
            entries.add(menuEntry(sender, snapshot.name(), detail, RiftMessages.EXPLAIN_WORLD_ENTRY));
        }
        if (!trashEntries.isEmpty()) {
            entries.add(menuSection(sender, RiftMessages.LABEL_QUARANTINE, trashEntries.size()));
            for (TrashEntry entry : trashEntries) {
                entries.add(menuEntry(sender, entry.getId(), entry.getWorldName(), RiftMessages.EXPLAIN_QUARANTINE));
            }
        }
        deliverMenu(
                sender,
                listMenu(
                        language.textWithoutPrefix(sender, RiftMessages.LABEL_WORLDS, MessageArgs.empty()).plain(),
                        entries,
                        language.textWithoutPrefix(sender, RiftMessages.EMPTY_LIST, MessageArgs.empty()).miniMessage(),
                        page
                )
        );
    }

    @Director(name = "info", description = "Show detailed state for one world", descriptionKey = "rift.command.info")
    public void info(
            @Param(name = "name", description = "World name", descriptionKey = "rift.parameter.name", customHandler = RiftCommandHandlers.KnownWorld.class) String name,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (!allowed(sender, "rift.info")) {
            return;
        }
        WorldSnapshot snapshot = inventory.find(name).orElse(null);
        if (snapshot == null) {
            language.send(sender, RiftMessages.UNKNOWN_WORLD, MessageArgs.builder()
                    .untrusted("world", name)
                    .build());
            return;
        }
        language.send(sender, RiftMessages.INFO_TITLE, MessageArgs.builder().untrusted("world", snapshot.name()).build());
        detail(sender, RiftMessages.LABEL_LOADED, snapshot.loaded());
        detail(sender, RiftMessages.LABEL_MANAGED, snapshot.managed());
        detail(sender, RiftMessages.LABEL_ON_DISK, snapshot.presentOnDisk());
        detail(sender, RiftMessages.LABEL_ENVIRONMENT, snapshot.environment());
        detail(sender, RiftMessages.LABEL_GENERATOR, snapshot.generator().isBlank() ? "vanilla" : snapshot.generator());
        detail(sender, RiftMessages.LABEL_SEED, snapshot.seed());
        if (snapshot.managed()) {
            detail(sender, RiftMessages.LABEL_AUTOLOAD, snapshot.autoLoad());
            detail(sender, RiftMessages.LABEL_PROTECTED, snapshot.protectedWorld());
        }
        detail(sender, RiftMessages.LABEL_OPERATION_ACTIVE, lifecycle.isBusy(snapshot.name()));
    }

    @Director(name = "generators", description = "Show configured generator identifiers", descriptionKey = "rift.command.generators")
    public void generators(
            @Param(name = "page", description = "Result page number", descriptionKey = "rift.parameter.page", defaultValue = "1") int page,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (!allowed(sender, "rift.generators")) {
            return;
        }
        Set<String> generators = lifecycle.configuredGenerators();
        List<String> entries = new ArrayList<>(generators.size() + 3);
        entries.add(menuDetail(sender, RiftMessages.LABEL_WORLD_TYPES,
                String.join(", ", Arrays.stream(WorldType.values()).map(Enum::name).toList())));
        entries.add(menuSection(sender, RiftMessages.LABEL_CONFIGURED_GENERATORS, generators.size()));
        for (String generator : generators) {
            if (generator.equals("vanilla") || generator.equals("void")) {
                entries.add(menuEntry(sender, generator,
                        label(sender, RiftMessages.LABEL_AVAILABLE), RiftMessages.EXPLAIN_GENERATOR));
                continue;
            }
            String owner = generator.contains(":") ? generator.substring(0, generator.indexOf(':')) : generator;
            Plugin provider = Bukkit.getPluginManager().getPlugin(owner);
            TextKey state = provider != null && provider.isEnabled()
                    ? RiftMessages.LABEL_AVAILABLE
                    : RiftMessages.LABEL_UNAVAILABLE;
            entries.add(menuEntry(sender, generator, label(sender, state), RiftMessages.EXPLAIN_GENERATOR));
        }
        entries.add(language.textWithoutPrefix(sender, RiftMessages.GENERATOR_FORMAT, MessageArgs.empty()).miniMessage());
        deliverMenu(sender, generatorMenu(
                language.textWithoutPrefix(sender, RiftMessages.LABEL_CONFIGURED_GENERATORS, MessageArgs.empty()).plain(),
                entries,
                page
        ));
    }

    @Director(name = "config", aliases = {"editor"}, description = "Open the in-game configuration editor", descriptionKey = "rift.command.config")
    public void config(
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (!allowed(sender, "rift.config")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            language.send(sender, RiftMessages.PLAYER_ONLY);
            return;
        }
        plugin.configMenu().open(player);
        language.send(player, RiftMessages.CONFIG_OPENED);
    }

    @Director(name = "language", description = "Select an available Rift language", descriptionKey = "rift.command.language")
    public void language(
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        plugin.languageSwitcher().open(sender);
    }

    @Director(name = "status", description = "Inspect Rift and platform state", descriptionKey = "rift.command.status")
    public void status(
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (!allowed(sender, "rift.status")) {
            return;
        }
        List<WorldSnapshot> snapshots = inventory.snapshots();
        long loaded = snapshots.stream().filter(WorldSnapshot::loaded).count();
        long managed = snapshots.stream().filter(WorldSnapshot::managed).count();
        long missing = snapshots.stream().filter(snapshot -> snapshot.managed() && !snapshot.presentOnDisk()).count();
        List<String> entries = new ArrayList<>();
        entries.add(menuDetail(sender, RiftMessages.LABEL_SERVER, Bukkit.getName() + " " + Bukkit.getVersion()));
        entries.add(menuDetail(sender, RiftMessages.LABEL_JAVA_RUNTIME, System.getProperty("java.version")));
        entries.add(menuDetail(sender, RiftMessages.LABEL_PLUGIN_BYTECODE, "17"));
        entries.add(menuDetail(sender, RiftMessages.LABEL_PLATFORM,
                capabilities.isFolia() ? "Folia" : "Bukkit/Paper/Spigot"));
        entries.add(menuDetail(sender, RiftMessages.LABEL_DYNAMIC_LIFECYCLE,
                capabilities.supportsDynamicWorldLifecycle()));
        entries.add(menuDetail(sender, RiftMessages.LABEL_WORLD_CONTAINER, plugin.paths().worldContainer()));
        entries.add(menuDetail(sender, RiftMessages.LABEL_WRITABLE, Files.isWritable(plugin.paths().worldContainer())));
        entries.add(menuDetail(sender, RiftMessages.LABEL_LOCALE, language.activeLocale()));
        entries.add(menuDetail(sender, RiftMessages.LABEL_WORLD_COUNTS,
                loaded + " " + label(sender, RiftMessages.LABEL_LOADED)
                        + ", " + managed + " " + label(sender, RiftMessages.LABEL_MANAGED)
                        + ", " + missing + " " + label(sender, RiftMessages.LABEL_MISSING)));
        entries.add(menuDetail(sender, RiftMessages.LABEL_QUARANTINE_ENTRIES, plugin.trash().all().size()));
        if (capabilities.isFolia()) {
            entries.add(language.textWithoutPrefix(
                    sender, RiftMessages.FOLIA_STATUS_NOTE, MessageArgs.empty()).miniMessage());
        }
        deliverMenu(
                sender,
                statusMenu(
                        language.textWithoutPrefix(sender, RiftMessages.STATUS_TITLE, MessageArgs.builder()
                                .untrusted("version", plugin.getDescription().getVersion())
                                .build()).plain(),
                        entries
                )
        );
    }

    @Director(name = "autoload", description = "Change managed-world startup loading", descriptionKey = "rift.command.autoload")
    public void autoload(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.name", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "enabled", description = "true or false", descriptionKey = "rift.parameter.enabled") boolean enabled,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (allowed(sender, "rift.config")) {
            lifecycle.setAutoLoad(sender, name, enabled);
        }
    }

    @Director(name = "protect", description = "Protect or unprotect a managed world", descriptionKey = "rift.command.protect")
    public void protect(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.name", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "enabled", description = "true or false", descriptionKey = "rift.parameter.enabled") boolean enabled,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (allowed(sender, "rift.config")) {
            lifecycle.setProtected(sender, name, enabled);
        }
    }

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("rift.admin")) {
            return true;
        }
        language.send(sender, RiftMessages.PERMISSION_DENIED, MessageArgs.builder()
                .untrusted("permission", permission)
                .build());
        return false;
    }

    private void detail(CommandSender sender, TextKey label, Object value) {
        ComponentText message = language.text(sender, RiftMessages.DETAIL, MessageArgs.builder()
                .trusted("label", language.text(sender, label).legacy())
                .untrusted("value", value)
                .build());
        language.send(sender, message.hover(language.text(sender, explanation(label))));
    }

    private String menuSection(CommandSender sender, TextKey title, int count) {
        return language.textWithoutPrefix(sender, RiftMessages.SECTION, MessageArgs.builder()
                .trusted("title", language.text(sender, title).legacy())
                .untrusted("count", count)
                .build()).miniMessage();
    }

    private String menuDetail(CommandSender sender, TextKey label, Object value) {
        ComponentText message = language.textWithoutPrefix(sender, RiftMessages.DETAIL, MessageArgs.builder()
                .trusted("label", language.text(sender, label).legacy())
                .untrusted("value", value)
                .build());
        return message.hover(language.text(sender, explanation(label))).miniMessage();
    }

    private String menuEntry(CommandSender sender, String name, String detail, TextKey explanation) {
        ComponentText message = language.textWithoutPrefix(sender, RiftMessages.ENTRY, MessageArgs.builder()
                .untrusted("name", name)
                .untrusted("detail", detail)
                .build());
        return message.hover(language.text(sender, explanation)).miniMessage();
    }

    private void deliverMenu(CommandSender sender, DirectorMiniMenu.ContentMenu menu) {
        DirectorMiniMenu.deliverContent(sender, menu, RiftCommandService.theme(), language.directorResolver());
    }

    static DirectorMiniMenu.ContentMenu listMenu(String title, List<String> entries, String emptyLine, int page) {
        return new DirectorMiniMenu.ContentMenu(
                title, "/rift list", "/rift", entries, emptyLine, page, LIST_PAGE_SIZE);
    }

    static DirectorMiniMenu.ContentMenu statusMenu(String title, List<String> entries) {
        return new DirectorMiniMenu.ContentMenu(
                title, "/rift status", "/rift", entries, "", 1, DirectorMiniMenu.MAX_ENTRIES_PER_PAGE);
    }

    static DirectorMiniMenu.ContentMenu generatorMenu(String title, List<String> entries, int page) {
        return new DirectorMiniMenu.ContentMenu(
                title, "/rift generators", "/rift", entries, "", page, LIST_PAGE_SIZE);
    }

    private static TextKey explanation(TextKey label) {
        if (label.equals(RiftMessages.LABEL_LOADED)) return RiftMessages.EXPLAIN_LOADED;
        if (label.equals(RiftMessages.LABEL_MANAGED)) return RiftMessages.EXPLAIN_MANAGED;
        if (label.equals(RiftMessages.LABEL_ON_DISK)) return RiftMessages.EXPLAIN_ON_DISK;
        if (label.equals(RiftMessages.LABEL_ENVIRONMENT)) return RiftMessages.EXPLAIN_ENVIRONMENT;
        if (label.equals(RiftMessages.LABEL_GENERATOR)) return RiftMessages.EXPLAIN_GENERATOR;
        if (label.equals(RiftMessages.LABEL_SEED)) return RiftMessages.EXPLAIN_SEED;
        if (label.equals(RiftMessages.LABEL_AUTOLOAD)) return RiftMessages.EXPLAIN_AUTOLOAD;
        if (label.equals(RiftMessages.LABEL_PROTECTED)) return RiftMessages.EXPLAIN_PROTECTED;
        if (label.equals(RiftMessages.LABEL_OPERATION_ACTIVE)) return RiftMessages.EXPLAIN_OPERATION_ACTIVE;
        if (label.equals(RiftMessages.LABEL_SERVER)) return RiftMessages.EXPLAIN_SERVER;
        if (label.equals(RiftMessages.LABEL_JAVA_RUNTIME)) return RiftMessages.EXPLAIN_JAVA_RUNTIME;
        if (label.equals(RiftMessages.LABEL_PLUGIN_BYTECODE)) return RiftMessages.EXPLAIN_PLUGIN_BYTECODE;
        if (label.equals(RiftMessages.LABEL_PLATFORM)) return RiftMessages.EXPLAIN_PLATFORM;
        if (label.equals(RiftMessages.LABEL_DYNAMIC_LIFECYCLE)) return RiftMessages.EXPLAIN_DYNAMIC_LIFECYCLE;
        if (label.equals(RiftMessages.LABEL_WORLD_CONTAINER)) return RiftMessages.EXPLAIN_WORLD_CONTAINER;
        if (label.equals(RiftMessages.LABEL_WRITABLE)) return RiftMessages.EXPLAIN_WRITABLE;
        if (label.equals(RiftMessages.LABEL_LOCALE)) return RiftMessages.EXPLAIN_LOCALE;
        if (label.equals(RiftMessages.LABEL_WORLD_COUNTS)) return RiftMessages.EXPLAIN_WORLD_COUNTS;
        if (label.equals(RiftMessages.LABEL_QUARANTINE_ENTRIES)) return RiftMessages.EXPLAIN_QUARANTINE;
        return RiftMessages.EXPLAIN_WORLD_ENTRY;
    }

    private String label(CommandSender sender, TextKey key) {
        return language.text(sender, key).plain();
    }
}
