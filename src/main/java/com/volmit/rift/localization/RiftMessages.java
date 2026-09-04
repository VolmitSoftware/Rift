package com.volmit.rift.localization;

import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.VolmitLocales;

import java.util.List;

public final class RiftMessages {
    public static final TextKey PREFIX = key("runtime.prefix", "&5&lRIFT&r &8›&r ");

    public static final TextKey COMMAND_ROOT = key("rift.command.root", "Manage server worlds safely");
    public static final TextKey COMMAND_CREATE = key("rift.command.create", "Create and manage a new world");
    public static final TextKey COMMAND_IMPORT = key("rift.command.import", "Import an existing world directory");
    public static final TextKey COMMAND_LOAD = key("rift.command.load", "Load a managed or discovered world");
    public static final TextKey COMMAND_UNLOAD = key("rift.command.unload", "Evacuate players and unload a world");
    public static final TextKey COMMAND_DELETE = key("rift.command.delete", "Move a managed world into quarantine");
    public static final TextKey COMMAND_RESTORE = key("rift.command.restore", "Restore a quarantined world");
    public static final TextKey COMMAND_TELEPORT = key("rift.command.teleport", "Teleport to a loaded world");
    public static final TextKey COMMAND_SEND = key("rift.command.send", "Teleport another player to a loaded world");
    public static final TextKey COMMAND_LIST = key("rift.command.list", "List loaded, managed, discovered, and quarantined worlds");
    public static final TextKey COMMAND_INFO = key("rift.command.info", "Show detailed state for one world");
    public static final TextKey COMMAND_GENERATORS = key("rift.command.generators", "Show generator identifiers already used by managed worlds");
    public static final TextKey COMMAND_CONFIG = key("rift.command.config", "Open the in-game Rift configuration editor");
    public static final TextKey COMMAND_LANGUAGE = key("rift.command.language", "Select an available Rift language");
    public static final TextKey COMMAND_STATUS = key("rift.command.status", "Inspect platform capabilities and Rift state");
    public static final TextKey COMMAND_DEBUG = key("rift.command.debug", "Rift diagnostic tools");
    public static final TextKey COMMAND_DEBUG_DUMP = key("rift.command.debug_dump", "Create a comprehensive Rift diagnostic report");
    public static final TextKey COMMAND_AUTOLOAD = key("rift.command.autoload", "Change whether a managed world loads during startup");
    public static final TextKey COMMAND_PROTECT = key("rift.command.protect", "Protect or unprotect a managed world");

    public static final TextKey PARAM_NAME = key("rift.parameter.name", "World name");
    public static final TextKey PARAM_ENVIRONMENT = key("rift.parameter.environment", "NORMAL, NETHER, THE_END, or CUSTOM");
    public static final TextKey PARAM_GENERATOR = key("rift.parameter.generator", "vanilla, void, or PluginName[:generator-id]");
    public static final TextKey PARAM_SEED = key("rift.parameter.seed", "random or a signed 64-bit seed");
    public static final TextKey PARAM_TYPE = key("rift.parameter.type", "Bukkit world type");
    public static final TextKey PARAM_SAVE = key("rift.parameter.save", "Save before unloading");
    public static final TextKey PARAM_ID = key("rift.parameter.id", "Quarantine entry id");
    public static final TextKey PARAM_PLAYER = key("rift.parameter.player", "Online player name");
    public static final TextKey PARAM_ENABLED = key("rift.parameter.enabled", "true or false");
    public static final TextKey PARAM_PAGE = key("rift.parameter.page", "Result page number");
    public static final TextKey PARAM_UPLOAD = key("rift.parameter.upload", "Upload the report when public uploads are enabled");
    public static final TextKey PARAM_SENDER = key("rift.parameter.sender", "Command sender");

    public static final TextKey PERMISSION_DENIED = prefixed("rift.message.permission_denied", "&cYou need &f{permission}&c.");
    public static final TextKey UNKNOWN_COMMAND = prefixed("rift.message.unknown_command", "&cUnknown command. Use &f/rift help&c.");
    public static final TextKey PLAYER_ONLY = prefixed("rift.message.player_only", "&cThis command requires a player.");
    public static final TextKey OPERATION_QUEUED = prefixed("rift.message.operation_queued", "&7Queued &f{operation}&7 for &d{world}&7.");
    public static final TextKey OPERATION_FAILED = prefixed("rift.message.operation_failed", "&c{operation} failed for &f{world}&c: {reason}");
    public static final TextKey CREATED = prefixed("rift.message.created", "&aCreated and managed &f{world}&a.");
    public static final TextKey IMPORTED = prefixed("rift.message.imported", "&aImported and managed &f{world}&a.");
    public static final TextKey LOADED = prefixed("rift.message.loaded", "&aLoaded &f{world}&a.");
    public static final TextKey UNLOADED = prefixed("rift.message.unloaded", "&aUnloaded &f{world}&a.");
    public static final TextKey TELEPORTED = prefixed("rift.message.teleported", "&aTeleported &f{player}&a to &f{world}&a.");
    public static final TextKey DELETE_CONFIRM = prefixed("rift.message.delete_confirm", "&eRun &f/rift delete {world}&e again within &f{seconds}s&e to move it into quarantine.");
    public static final TextKey QUARANTINED = prefixed("rift.message.quarantined", "&aMoved &f{world}&a into quarantine as &f{id}&a.");
    public static final TextKey RESTORED = prefixed("rift.message.restored", "&aRestored &f{world}&a from &f{id}&a.");
    public static final TextKey PROFILE_UPDATED = prefixed("rift.message.profile_updated", "&aUpdated &f{setting}&a for &f{world}&a to &f{value}&a.");
    public static final TextKey FOLIA_LIMIT = prefixed("rift.message.folia_limit", "&e{operation} is unavailable on Folia because its world load/unload API is not implemented.");
    public static final TextKey CONFIG_SAVED = prefixed("rift.message.config_saved", "&aConfiguration saved.");
    public static final TextKey CONFIG_OPENED = prefixed("rift.message.config_opened", "&aOpened the complete in-game configuration editor.&r");
    public static final TextKey CONFIG_SAVE_FAILED = prefixed("rift.message.config_save_failed", "&cConfiguration could not be saved. Check the console.");
    public static final TextKey EMPTY_LIST = prefixed("rift.message.empty_list", "&7No matching worlds were found.");
    public static final TextKey PLAYER_OFFLINE = prefixed("rift.message.player_offline", "&cPlayer is not online: &f{player}");
    public static final TextKey UNKNOWN_WORLD = prefixed("rift.message.unknown_world", "&cUnknown world: &f{world}");
    public static final TextKey SECTION = prefixed("rift.message.section", "&d{title} &8(&f{count}&8)");
    public static final TextKey ENTRY = prefixed("rift.message.entry", "&8- &f{name} &8| &7{detail}");
    public static final TextKey DETAIL = prefixed("rift.message.detail", "&8- &7{label}: &f{value}");
    public static final TextKey INFO_TITLE = prefixed("rift.message.info_title", "&d{world}");
    public static final TextKey STATUS_TITLE = prefixed("rift.message.status_title", "&dRift {version} Status");
    public static final TextKey GENERATOR_FORMAT = prefixed("rift.message.generator_format", "&7Custom format: &fPluginName[:generator-id]");
    public static final TextKey FOLIA_STATUS_NOTE = prefixed("rift.message.folia_status_note", "&eFolia supports Rift's read, editor, profile, and teleport features; dynamic world lifecycle is gated by the platform API.");
    public static final TextKey LABEL_WORLDS = key("rift.label.worlds", "Worlds");
    public static final TextKey LABEL_QUARANTINE = key("rift.label.quarantine", "Quarantine");
    public static final TextKey LABEL_LOADED = key("rift.label.loaded", "Loaded");
    public static final TextKey LABEL_MANAGED = key("rift.label.managed", "Managed");
    public static final TextKey LABEL_ON_DISK = key("rift.label.on_disk", "On disk");
    public static final TextKey LABEL_MISSING = key("rift.label.missing", "missing managed worlds");
    public static final TextKey LABEL_ENVIRONMENT = key("rift.label.environment", "Environment");
    public static final TextKey LABEL_GENERATOR = key("rift.label.generator", "Generator");
    public static final TextKey LABEL_SEED = key("rift.label.seed", "Seed");
    public static final TextKey LABEL_AUTOLOAD = key("rift.label.autoload", "Auto-load");
    public static final TextKey LABEL_PROTECTED = key("rift.label.protected", "Protected");
    public static final TextKey LABEL_OPERATION_ACTIVE = key("rift.label.operation_active", "Operation active");
    public static final TextKey LABEL_WORLD_TYPES = key("rift.label.world_types", "World types");
    public static final TextKey LABEL_CONFIGURED_GENERATORS = key("rift.label.configured_generators", "Configured generators");
    public static final TextKey LABEL_AVAILABLE = key("rift.label.available", "available");
    public static final TextKey LABEL_UNAVAILABLE = key("rift.label.unavailable", "unavailable");
    public static final TextKey LABEL_SERVER = key("rift.label.server", "Server");
    public static final TextKey LABEL_JAVA_RUNTIME = key("rift.label.java_runtime", "Java runtime");
    public static final TextKey LABEL_PLUGIN_BYTECODE = key("rift.label.plugin_bytecode", "Plugin bytecode");
    public static final TextKey LABEL_PLATFORM = key("rift.label.platform", "Platform");
    public static final TextKey LABEL_DYNAMIC_LIFECYCLE = key("rift.label.dynamic_lifecycle", "Dynamic world lifecycle");
    public static final TextKey LABEL_WORLD_CONTAINER = key("rift.label.world_container", "World container");
    public static final TextKey LABEL_WRITABLE = key("rift.label.writable", "World container writable");
    public static final TextKey LABEL_LOCALE = key("rift.label.locale", "Locale");
    public static final TextKey LABEL_WORLD_COUNTS = key("rift.label.world_counts", "Worlds");
    public static final TextKey LABEL_QUARANTINE_ENTRIES = key("rift.label.quarantine_entries", "Quarantine entries");

    public static final TextKey EXPLAIN_LOADED = key("rift.explain.loaded", "Whether Bukkit currently has this world active in memory.");
    public static final TextKey EXPLAIN_MANAGED = key("rift.explain.managed", "Whether Rift owns a profile for this world and can restore its generator and startup settings.");
    public static final TextKey EXPLAIN_ON_DISK = key("rift.explain.on_disk", "Whether the world is loaded or has a valid world directory containing level.dat.");
    public static final TextKey EXPLAIN_ENVIRONMENT = key("rift.explain.environment", "The Bukkit world environment recorded by the live world or managed profile.");
    public static final TextKey EXPLAIN_GENERATOR = key("rift.explain.generator", "The generator Rift reapplies when this managed world loads. void is Rift's built-in empty-world generator.");
    public static final TextKey EXPLAIN_SEED = key("rift.explain.seed", "The seed recorded for this world. Existing terrain is never regenerated by changing this display value.");
    public static final TextKey EXPLAIN_AUTOLOAD = key("rift.explain.autoload", "Whether Rift loads this managed world automatically during server startup.");
    public static final TextKey EXPLAIN_PROTECTED = key("rift.explain.protected", "Protected managed worlds cannot be moved into Rift quarantine.");
    public static final TextKey EXPLAIN_OPERATION_ACTIVE = key("rift.explain.operation_active", "Whether a lifecycle operation currently holds Rift's per-world lock.");
    public static final TextKey EXPLAIN_SERVER = key("rift.explain.server", "The active server implementation and version reported by Bukkit.");
    public static final TextKey EXPLAIN_JAVA_RUNTIME = key("rift.explain.java_runtime", "The Java runtime currently hosting the server. Rift's bytecode remains compatible with Java 17.");
    public static final TextKey EXPLAIN_PLUGIN_BYTECODE = key("rift.explain.plugin_bytecode", "The oldest Java runtime capable of loading Rift's compiled classes.");
    public static final TextKey EXPLAIN_PLATFORM = key("rift.explain.platform", "The scheduler and server API family Rift detected at runtime.");
    public static final TextKey EXPLAIN_DYNAMIC_LIFECYCLE = key("rift.explain.dynamic_lifecycle", "Whether this platform safely exposes runtime world create, load, unload, quarantine, and restore operations.");
    public static final TextKey EXPLAIN_WORLD_CONTAINER = key("rift.explain.world_container", "The server directory where Bukkit world folders are stored.");
    public static final TextKey EXPLAIN_WRITABLE = key("rift.explain.writable", "Whether the operating system currently permits Rift to write inside the world container.");
    public static final TextKey EXPLAIN_LOCALE = key("rift.explain.locale", "The active language file under plugins/Rift/languages. Missing entries use the editable English defaults.");
    public static final TextKey EXPLAIN_WORLD_COUNTS = key("rift.explain.world_counts", "Loaded counts active Bukkit worlds; managed counts Rift profiles; missing counts managed profiles that are neither loaded nor present on disk.");
    public static final TextKey EXPLAIN_QUARANTINE = key("rift.explain.quarantine", "Worlds moved aside by Rift delete and available to restore by quarantine id.");
    public static final TextKey EXPLAIN_WORLD_ENTRY = key("rift.explain.world_entry", "Loaded means active now; managed means Rift has a profile; on disk means a valid unloaded world folder exists.");

    public static final TextKey FEEDBACK_TITLE = key("rift.feedback.title", "&d&lRIFT");
    public static final TextKey FEEDBACK_WORLD_SUBTITLE = key("rift.feedback.world_subtitle", "&a{operation}&8: &f{world}");
    public static final TextKey FEEDBACK_TELEPORT_SUBTITLE = key("rift.feedback.teleport_subtitle", "&aArrived in &f{world}");
    public static final TextKey FEEDBACK_FAILURE_TITLE = key("rift.feedback.failure_title", "&cOperation failed");
    public static final TextKey FEEDBACK_FAILURE_SUBTITLE = key("rift.feedback.failure_subtitle", "&7{operation}&8: &f{world}");
    public static final TextKey FEEDBACK_ACTION_WORLD = key("rift.feedback.action_world", "&dRift &8• &a{operation} &f{world}");
    public static final TextKey FEEDBACK_ACTION_TELEPORT = key("rift.feedback.action_teleport", "&dRift &8• &aTeleported to &f{world}");
    public static final TextKey FEEDBACK_ACTION_FAILURE = key("rift.feedback.action_failure", "&dRift &8• &c{operation} failed for &f{world}");

    public static final TextKey GUI_CURRENT = key("rift.gui.current", "&7Current: &f{value}");
    public static final TextKey GUI_AUTOLOAD = key("rift.gui.autoload", "&dAuto-load managed worlds");
    public static final TextKey GUI_DELETE = key("rift.gui.delete", "&dAllow world quarantine");
    public static final TextKey GUI_VERBOSE = key("rift.gui.verbose", "&dVerbose logging");
    public static final TextKey GUI_DEBUG_UPLOAD = key("rift.gui.debug_upload", "&dPublic debug uploads");
    public static final TextKey GUI_BSTATS = key("rift.gui.bstats", "&dAnonymous bStats metrics");
    public static final TextKey GUI_CONFIRM_SECONDS = key("rift.gui.confirm_seconds", "&dDelete confirmation seconds");
    public static final TextKey GUI_POLL = key("rift.gui.poll", "&dHot-reload poll milliseconds");
    public static final TextKey GUI_COOLDOWN = key("rift.gui.cooldown", "&dHot-reload cooldown milliseconds");
    public static final TextKey GUI_EVACUATION = key("rift.gui.evacuation", "&dEvacuation world");
    public static final TextKey GUI_CLOSE = key("rift.gui.close", "&cClose");
    public static final TextKey GUI_SPLASH = key("rift.gui.splash", "&dStartup splash screen");
    public static final TextKey GUI_LIFECYCLE_FEEDBACK = key("rift.gui.lifecycle_feedback", "&dWorld lifecycle feedback");
    public static final TextKey GUI_TELEPORT_FEEDBACK = key("rift.gui.teleport_feedback", "&dTeleport feedback");
    public static final TextKey GUI_FAILURE_FEEDBACK = key("rift.gui.failure_feedback", "&dFailure feedback");
    public static final TextKey GUI_TITLE_POPUPS = key("rift.gui.title_popups", "&dTitle popups");
    public static final TextKey GUI_ACTION_BAR_POPUPS = key("rift.gui.action_bar_popups", "&dAction-bar popups");
    public static final TextKey GUI_SOUNDS = key("rift.gui.sounds", "&dFeedback sounds");
    public static final TextKey GUI_ROOT_TITLE = key("rift.gui.root_title", "Rift Configuration");
    public static final TextKey GUI_CATEGORY_TITLE = key("rift.gui.category_title", "Rift: {category}");
    public static final TextKey GUI_CATEGORY_GENERAL = key("rift.gui.category.general", "&dGeneral & Safety");
    public static final TextKey GUI_CATEGORY_FEEDBACK = key("rift.gui.category.feedback", "&dFeedback & Sounds");
    public static final TextKey GUI_CATEGORY_PRESENTATION = key("rift.gui.category.presentation", "&dPresentation");
    public static final TextKey GUI_CATEGORY_DIAGNOSTICS = key("rift.gui.category.diagnostics", "&dDiagnostics&r");
    public static final TextKey GUI_CATEGORY_LANGUAGES = key("rift.gui.category.languages", "&dLanguages & Messages");
    public static final TextKey GUI_CATEGORY_OPEN = key("rift.gui.category_open", "&8Click to edit every setting in this section");
    public static final TextKey GUI_BACK = key("rift.gui.back", "&eBack");
    public static final TextKey GUI_TOGGLE = key("rift.gui.toggle", "&8Click to toggle");
    public static final TextKey GUI_EXACT_NUMBER = key("rift.gui.exact_number", "&8Left/right adjusts; press Q to enter an exact value");
    public static final TextKey GUI_TEXT = key("rift.gui.text", "&8Click to enter a new value in chat");
    public static final TextKey GUI_PROMPT = prefixed("rift.gui.prompt", "&7Type a new value for &f{setting}&7 in chat.");
    public static final TextKey GUI_PROMPT_CANCEL = prefixed("rift.gui.prompt_cancel", "&7Type &fcancel&7 to return without changing it.");
    public static final TextKey GUI_PROMPT_CANCELLED = prefixed("rift.gui.prompt_cancelled", "&7Configuration input cancelled.");
    public static final TextKey GUI_PROMPT_TIMEOUT = prefixed("rift.gui.prompt_timeout", "&7Configuration input timed out.");
    public static final TextKey GUI_SETTING_SAVED = prefixed("rift.gui.setting_saved",
            "&d{setting} &8› &aChanged to &f{after} &7from &f{before}&a.");
    public static final TextKey GUI_SETTING_FAILED = prefixed("rift.gui.setting_failed", "&cCould not update &f{setting}&c: {reason}");
    public static final TextKey GUI_LANGUAGE = key("rift.gui.language", "&dLanguage locale");
    public static final TextKey GUI_LANGUAGE_SELECT = key("rift.gui.language_select", "&8Click to choose from available languages");
    public static final TextKey GUI_LIFECYCLE_SOUND = key("rift.gui.lifecycle_sound", "&dWorld lifecycle sound");
    public static final TextKey GUI_TELEPORT_SOUND = key("rift.gui.teleport_sound", "&dTeleport sound");
    public static final TextKey GUI_FAILURE_SOUND = key("rift.gui.failure_sound", "&dFailure sound");
    public static final TextKey GUI_SOUND_VOLUME = key("rift.gui.sound_volume", "&dSound volume");
    public static final TextKey GUI_SOUND_PITCH = key("rift.gui.sound_pitch", "&dSound pitch");
    public static final TextKey GUI_TITLE_FADE_IN = key("rift.gui.title_fade_in", "&dTitle fade-in ticks");
    public static final TextKey GUI_TITLE_STAY = key("rift.gui.title_stay", "&dTitle stay ticks");
    public static final TextKey GUI_TITLE_FADE_OUT = key("rift.gui.title_fade_out", "&dTitle fade-out ticks");

    private static final List<TextKey> KEYS = List.of(
            PREFIX, COMMAND_ROOT, COMMAND_CREATE, COMMAND_IMPORT, COMMAND_LOAD, COMMAND_UNLOAD, COMMAND_DELETE,
            COMMAND_RESTORE, COMMAND_TELEPORT, COMMAND_SEND, COMMAND_LIST, COMMAND_INFO, COMMAND_GENERATORS,
            COMMAND_CONFIG, COMMAND_LANGUAGE, COMMAND_STATUS, COMMAND_DEBUG, COMMAND_DEBUG_DUMP,
            COMMAND_AUTOLOAD, COMMAND_PROTECT,
            PARAM_NAME, PARAM_ENVIRONMENT, PARAM_GENERATOR, PARAM_SEED, PARAM_TYPE, PARAM_SAVE, PARAM_ID,
            PARAM_PLAYER, PARAM_ENABLED, PARAM_PAGE, PARAM_UPLOAD, PARAM_SENDER,
            PERMISSION_DENIED, UNKNOWN_COMMAND, PLAYER_ONLY,
            OPERATION_QUEUED, OPERATION_FAILED, CREATED, IMPORTED, LOADED, UNLOADED, TELEPORTED,
            DELETE_CONFIRM, QUARANTINED, RESTORED, PROFILE_UPDATED,
            FOLIA_LIMIT, CONFIG_SAVED, CONFIG_OPENED, CONFIG_SAVE_FAILED, EMPTY_LIST, PLAYER_OFFLINE,
            UNKNOWN_WORLD, SECTION,
            ENTRY, DETAIL, INFO_TITLE, STATUS_TITLE, GENERATOR_FORMAT, FOLIA_STATUS_NOTE, LABEL_WORLDS,
            LABEL_QUARANTINE, LABEL_LOADED, LABEL_MANAGED, LABEL_ON_DISK, LABEL_MISSING, LABEL_ENVIRONMENT,
            LABEL_GENERATOR, LABEL_SEED, LABEL_AUTOLOAD, LABEL_PROTECTED, LABEL_OPERATION_ACTIVE,
            LABEL_WORLD_TYPES, LABEL_CONFIGURED_GENERATORS, LABEL_AVAILABLE, LABEL_UNAVAILABLE, LABEL_SERVER,
            LABEL_JAVA_RUNTIME, LABEL_PLUGIN_BYTECODE, LABEL_PLATFORM, LABEL_DYNAMIC_LIFECYCLE,
            LABEL_WORLD_CONTAINER, LABEL_WRITABLE, LABEL_LOCALE, LABEL_WORLD_COUNTS, LABEL_QUARANTINE_ENTRIES,
            EXPLAIN_LOADED, EXPLAIN_MANAGED, EXPLAIN_ON_DISK, EXPLAIN_ENVIRONMENT, EXPLAIN_GENERATOR,
            EXPLAIN_SEED, EXPLAIN_AUTOLOAD, EXPLAIN_PROTECTED, EXPLAIN_OPERATION_ACTIVE, EXPLAIN_SERVER,
            EXPLAIN_JAVA_RUNTIME, EXPLAIN_PLUGIN_BYTECODE, EXPLAIN_PLATFORM, EXPLAIN_DYNAMIC_LIFECYCLE,
            EXPLAIN_WORLD_CONTAINER, EXPLAIN_WRITABLE, EXPLAIN_LOCALE, EXPLAIN_WORLD_COUNTS,
            EXPLAIN_QUARANTINE, EXPLAIN_WORLD_ENTRY, FEEDBACK_TITLE, FEEDBACK_WORLD_SUBTITLE,
            FEEDBACK_TELEPORT_SUBTITLE, FEEDBACK_FAILURE_TITLE, FEEDBACK_FAILURE_SUBTITLE,
            FEEDBACK_ACTION_WORLD, FEEDBACK_ACTION_TELEPORT, FEEDBACK_ACTION_FAILURE,
            GUI_CURRENT,
            GUI_AUTOLOAD, GUI_DELETE, GUI_VERBOSE, GUI_DEBUG_UPLOAD, GUI_BSTATS, GUI_CONFIRM_SECONDS, GUI_POLL, GUI_COOLDOWN,
            GUI_EVACUATION, GUI_CLOSE, GUI_SPLASH,
            GUI_LIFECYCLE_FEEDBACK, GUI_TELEPORT_FEEDBACK, GUI_FAILURE_FEEDBACK, GUI_TITLE_POPUPS,
            GUI_ACTION_BAR_POPUPS, GUI_SOUNDS,
            GUI_ROOT_TITLE, GUI_CATEGORY_TITLE, GUI_CATEGORY_GENERAL,
            GUI_CATEGORY_FEEDBACK, GUI_CATEGORY_PRESENTATION, GUI_CATEGORY_DIAGNOSTICS,
            GUI_CATEGORY_LANGUAGES, GUI_CATEGORY_OPEN,
            GUI_BACK, GUI_TOGGLE,
            GUI_EXACT_NUMBER, GUI_TEXT, GUI_PROMPT, GUI_PROMPT_CANCEL, GUI_PROMPT_CANCELLED,
            GUI_PROMPT_TIMEOUT, GUI_SETTING_SAVED, GUI_SETTING_FAILED, GUI_LANGUAGE, GUI_LANGUAGE_SELECT,
            GUI_LIFECYCLE_SOUND,
            GUI_TELEPORT_SOUND, GUI_FAILURE_SOUND, GUI_SOUND_VOLUME, GUI_SOUND_PITCH, GUI_TITLE_FADE_IN,
            GUI_TITLE_STAY, GUI_TITLE_FADE_OUT
    );
    private static final MessageCatalog CATALOG = createCatalog();

    private RiftMessages() {
    }

    public static MessageCatalog catalog() {
        return CATALOG;
    }

    private static TextKey key(String id, String english) {
        return TextKey.of(id, english);
    }

    private static TextKey prefixed(String id, String english) {
        return TextKey.ofOptional(id, "{prefix}" + english, "prefix");
    }

    private static MessageCatalog createCatalog() {
        MessageCatalog.Builder builder = MessageCatalog.builder(VolmitLocales.ENGLISH);
        builder.addAll(DirectorMessages.keys());
        builder.addAll(BukkitLanguageMessages.keys());
        builder.addAll(KEYS);
        return builder.build();
    }
}
