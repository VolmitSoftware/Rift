package com.volmit.rift.localization;

import art.arcane.volmlib.util.director.DirectorMessages;
import art.arcane.volmlib.util.diagnostics.BukkitDebugMessages;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.VolmitLocales;

import java.util.List;

public final class RiftMessages {
    public static final String CHAT_PREFIX = "{prefix}&r &7› &7";
    public static final TextKey PREFIX = key("runtime.prefix", "<bold><gradient:#6f2dbd:#d16ba5>Rift</gradient></bold>");

    public static final TextKey VERSION = key("rift.message.version", "<gradient:#6f2dbd:#d16ba5>{prefix} v{version}</gradient>");

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
    public static final TextKey COMMAND_CONFIG = key("rift.command.config", "Open the in-game {prefix} configuration editor");
    public static final TextKey COMMAND_LANGUAGE = key("rift.command.language", "Select an available {prefix} language");
    public static final TextKey COMMAND_STATUS = key("rift.command.status", "Inspect platform capabilities and {prefix} state");
    public static final TextKey COMMAND_DEBUG = key("rift.command.debug", "{prefix} diagnostic tools");
    public static final TextKey COMMAND_VERSION = key("rift.command.version", "Show the {prefix} version");
    public static final TextKey COMMAND_DEBUG_DUMP = key("rift.command.debug_dump", "Create a comprehensive {prefix} diagnostic report");
    public static final TextKey COMMAND_AUTOLOAD = key("rift.command.autoload", "Change whether a managed world loads during startup");
    public static final TextKey COMMAND_PROTECT = key("rift.command.protect", "Protect or unprotect a managed world");
    public static final TextKey COMMAND_CHECK = key("rift.command.check", "Run a read-only world preflight check");
    public static final TextKey COMMAND_CHECK_ALL = key("rift.command.check_all", "Check every matching world without changing it");
    public static final TextKey COMMAND_UNMANAGE = key("rift.command.unmanage", "Stop managing a world without touching its files");
    public static final TextKey COMMAND_POLICY = key("rift.command.policy", "Inspect and edit policies for each managed world");
    public static final TextKey COMMAND_POLICY_SHOW = key("rift.command.policy_show", "Show the active policies for a managed world");
    public static final TextKey COMMAND_POLICY_DIFFICULTY = key("rift.command.policy_difficulty", "Set the world difficulty or inherit the server value");
    public static final TextKey COMMAND_POLICY_PVP = key("rift.command.policy_pvp", "Allow or deny PvP, or inherit the server value");
    public static final TextKey COMMAND_POLICY_GAMERULE = key("rift.command.policy_gamerule", "Set a game rule or stop managing it");
    public static final TextKey COMMAND_POLICY_SPAWN = key("rift.command.policy_spawn", "Set this world's spawn to your current safe location");
    public static final TextKey COMMAND_POLICY_SPAWN_CLEAR = key("rift.command.policy_spawn_clear", "Stop managing this world's spawn");
    public static final TextKey COMMAND_POLICY_BORDER = key("rift.command.policy_border", "Configure this world's managed border");
    public static final TextKey COMMAND_POLICY_BORDER_CLEAR = key("rift.command.policy_border_clear", "Reset this world's border and stop managing it");
    public static final TextKey COMMAND_POLICY_ACCESS = key("rift.command.policy_access", "Require a permission to enter this world, or clear the requirement");
    public static final TextKey COMMAND_POLICY_RESPAWN = key("rift.command.policy_respawn", "Route deaths in this world to another managed world or the server default");
    public static final TextKey COMMAND_POLICY_TAG = key("rift.command.policy_tag", "Add or remove an operator tag");

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
    public static final TextKey PARAM_MANAGED_WORLD = key("rift.parameter.managed_world", "Managed world name");
    public static final TextKey PARAM_POLICY_DIFFICULTY = key("rift.parameter.policy_difficulty", "PEACEFUL, EASY, NORMAL, HARD, or INHERIT");
    public static final TextKey PARAM_POLICY_PVP = key("rift.parameter.policy_pvp", "ALLOW, DENY, or INHERIT");
    public static final TextKey PARAM_GAME_RULE = key("rift.parameter.game_rule", "Minecraft game rule name");
    public static final TextKey PARAM_GAME_RULE_VALUE = key("rift.parameter.game_rule_value", "Game rule value or INHERIT");
    public static final TextKey PARAM_BORDER_SIZE = key("rift.parameter.border_size", "Border diameter in blocks");
    public static final TextKey PARAM_BORDER_CENTER_X = key("rift.parameter.border_center_x", "Border center X coordinate");
    public static final TextKey PARAM_BORDER_CENTER_Z = key("rift.parameter.border_center_z", "Border center Z coordinate");
    public static final TextKey PARAM_BORDER_WARNING_DISTANCE = key("rift.parameter.border_warning_distance", "Warning distance in blocks");
    public static final TextKey PARAM_BORDER_WARNING_TIME = key("rift.parameter.border_warning_time", "Warning time in seconds");
    public static final TextKey PARAM_BORDER_DAMAGE = key("rift.parameter.border_damage", "Damage per block outside the border");
    public static final TextKey PARAM_BORDER_BUFFER = key("rift.parameter.border_buffer", "Safe distance outside the border");
    public static final TextKey PARAM_ACCESS_PERMISSION = key("rift.parameter.access_permission", "Permission node, or clear");
    public static final TextKey PARAM_ACCESS_DENIED_MESSAGE = key("rift.parameter.access_denied_message", "Optional message shown when access is denied");
    public static final TextKey PARAM_RESPAWN_DESTINATION = key("rift.parameter.respawn_destination", "Managed world name, or default");
    public static final TextKey PARAM_POLICY_TAG = key("rift.parameter.policy_tag", "Operator tag name");
    public static final TextKey PARAM_TAG_ENABLED = key("rift.parameter.tag_enabled", "true adds the tag; false removes it");
    public static final TextKey PARAM_TAG_FILTER = key("rift.parameter.tag_filter", "Filter by an operator tag, or all");
    public static final TextKey PARAM_TAG_GROUP = key("rift.parameter.tag_group", "Group matching worlds by operator tag");

    public static final TextKey PERMISSION_DENIED = prefixed("rift.message.permission_denied", "&cYou need &f{permission}&7.");
    public static final TextKey UNKNOWN_COMMAND = prefixed("rift.message.unknown_command", "&cUnknown command. Use &f/rift help&7.");
    public static final TextKey PLAYER_ONLY = prefixed("rift.message.player_only", "&cThis command requires a player.");
    public static final TextKey OPERATION_QUEUED = prefixed("rift.message.operation_queued", "Queued &f{operation}&7 for &d{world}&7.");
    public static final TextKey OPERATION_FAILED = prefixed("rift.message.operation_failed", "&c{operation} failed for &f{world}&7: {reason}");
    public static final TextKey CREATED = prefixed("rift.message.created", "Created and managed &a{world}&7.");
    public static final TextKey IMPORTED = prefixed("rift.message.imported", "Imported and managed &a{world}&7.");
    public static final TextKey LOADED = prefixed("rift.message.loaded", "Loaded &a{world}&7.");
    public static final TextKey UNLOADED = prefixed("rift.message.unloaded", "Unloaded &a{world}&7.");
    public static final TextKey TELEPORTED = prefixed("rift.message.teleported", "Teleported &a{player}&7 to &a{world}&7.");
    public static final TextKey DELETE_CONFIRM = prefixed("rift.message.delete_confirm", "Run &e/rift delete {world}&7 again within &e{seconds}s&7 to move it into quarantine.");
    public static final TextKey QUARANTINED = prefixed("rift.message.quarantined", "Moved &a{world}&7 into quarantine as &a{id}&7.");
    public static final TextKey RESTORED = prefixed("rift.message.restored", "Restored &a{world}&7 from &a{id}&7.");
    public static final TextKey PROFILE_UPDATED = prefixed("rift.message.profile_updated", "Updated &a{setting}&7 for &a{world}&7 to &a{value}&7.");
    public static final TextKey UNMANAGED_LOADED = key("rift.message.unmanaged_loaded", CHAT_PREFIX + "Stopped managing &a{world}&7 without changing its files. The world remains loaded until another plugin or the server unloads it.");
    public static final TextKey UNMANAGED_UNLOADED = key("rift.message.unmanaged_unloaded", CHAT_PREFIX + "Stopped managing &a{world}&7 without changing its files. {prefix} will no longer load it during startup.");
    public static final TextKey FOLIA_LIMIT = prefixed("rift.message.folia_limit", "{operation} is unavailable on Folia because its world load/unload API is not implemented.");
    public static final TextKey CONFIG_SAVED = prefixed("rift.message.config_saved", "&aConfiguration saved.");
    public static final TextKey CONFIG_OPENED = prefixed("rift.message.config_opened", "Opened the complete in-game configuration editor.&r");
    public static final TextKey CONFIG_SAVE_FAILED = prefixed("rift.message.config_save_failed", "&cConfiguration could not be saved. Check the console.");
    public static final TextKey EMPTY_LIST = prefixed("rift.message.empty_list", "No matching worlds were found.");
    public static final TextKey PLAYER_OFFLINE = prefixed("rift.message.player_offline", "&cPlayer is not online: &f{player}");
    public static final TextKey UNKNOWN_WORLD = prefixed("rift.message.unknown_world", "&cUnknown world: &f{world}");
    public static final TextKey SECTION = prefixed("rift.message.section", "&d{title} &8(&f{count}&8)");
    public static final TextKey ENTRY = prefixed("rift.message.entry", "&8- &f{name} &8| &7{detail}");
    public static final TextKey DETAIL = prefixed("rift.message.detail", "&8- &7{label}: &f{value}");
    public static final TextKey INFO_TITLE = prefixed("rift.message.info_title", "&d{world}");
    public static final TextKey STATUS_TITLE = prefixed("rift.message.status_title", "{prefix} {version} Status");
    public static final TextKey GENERATOR_FORMAT = prefixed("rift.message.generator_format", "Custom format: &fPluginName[:generator-id]");
    public static final TextKey FOLIA_STATUS_NOTE = prefixed("rift.message.folia_status_note", "Folia supports {prefix}'s read, editor, profile, and teleport features; dynamic world lifecycle is gated by the platform API.");
    public static final TextKey POLICY_ACCESS_DENIED = prefixed("rift.message.policy_access_denied", "&cYou cannot enter &f{world}&7. You need &f{permission}&7.");
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
    public static final TextKey LABEL_WORLD_KEY = key("rift.label.world_key", "World key");
    public static final TextKey LABEL_STORAGE_PATH = key("rift.label.storage_path", "Storage path");
    public static final TextKey LABEL_STORAGE_LAYOUT = key("rift.label.storage_layout", "Storage layout");
    public static final TextKey LABEL_PLAYERS = key("rift.label.players", "Players");
    public static final TextKey LABEL_GENERATOR_STATUS = key("rift.label.generator_status", "Generator status");
    public static final TextKey LABEL_PRIMARY_WORLD = key("rift.label.primary_world", "Primary world");
    public static final TextKey LABEL_CHECK_RESULT = key("rift.label.check_result", "Check result");
    public static final TextKey LABEL_READY = key("rift.label.ready", "ready");
    public static final TextKey LABEL_REVIEW_REQUIRED = key("rift.label.review_required", "review required");
    public static final TextKey LABEL_POLICY_DIFFICULTY = key("rift.label.policy_difficulty", "Difficulty");
    public static final TextKey LABEL_POLICY_PVP = key("rift.label.policy_pvp", "PvP");
    public static final TextKey LABEL_POLICY_GAME_RULES = key("rift.label.policy_game_rules", "Game rules");
    public static final TextKey LABEL_POLICY_CUSTOM_SPAWN = key("rift.label.policy_custom_spawn", "Custom spawn");
    public static final TextKey LABEL_POLICY_MANAGED_BORDER = key("rift.label.policy_managed_border", "Managed border");
    public static final TextKey LABEL_POLICY_ACCESS_PERMISSION = key("rift.label.policy_access_permission", "Access permission");
    public static final TextKey LABEL_POLICY_RESPAWN_WORLD = key("rift.label.policy_respawn_world", "Respawn world");
    public static final TextKey LABEL_POLICY_TAGS = key("rift.label.policy_tags", "Tags");
    public static final TextKey LABEL_NONE = key("rift.label.none", "none");
    public static final TextKey LABEL_SERVER_DEFAULT = key("rift.label.server_default", "server default");

    public static final TextKey EXPLAIN_LOADED = key("rift.explain.loaded", "Whether Bukkit currently has this world active in memory.");
    public static final TextKey EXPLAIN_MANAGED = key("rift.explain.managed", "Whether {prefix} owns a profile for this world and can restore its generator and startup settings.");
    public static final TextKey EXPLAIN_ON_DISK = key("rift.explain.on_disk", "Whether the world is loaded or has a valid world directory containing level.dat.");
    public static final TextKey EXPLAIN_ENVIRONMENT = key("rift.explain.environment", "The Bukkit world environment recorded by the live world or managed profile.");
    public static final TextKey EXPLAIN_GENERATOR = key("rift.explain.generator", "The generator {prefix} reapplies when this managed world loads. void is {prefix}'s built-in empty-world generator.");
    public static final TextKey EXPLAIN_SEED = key("rift.explain.seed", "The seed recorded for this world. Existing terrain is never regenerated by changing this display value.");
    public static final TextKey EXPLAIN_AUTOLOAD = key("rift.explain.autoload", "Whether {prefix} loads this managed world automatically during server startup.");
    public static final TextKey EXPLAIN_PROTECTED = key("rift.explain.protected", "Protected managed worlds cannot be moved into {prefix} quarantine.");
    public static final TextKey EXPLAIN_OPERATION_ACTIVE = key("rift.explain.operation_active", "Whether a lifecycle operation currently holds {prefix}'s per-world lock.");
    public static final TextKey EXPLAIN_SERVER = key("rift.explain.server", "The active server implementation and version reported by Bukkit.");
    public static final TextKey EXPLAIN_JAVA_RUNTIME = key("rift.explain.java_runtime", "The Java runtime currently hosting the server. {prefix} requires Java 25.");
    public static final TextKey EXPLAIN_PLUGIN_BYTECODE = key("rift.explain.plugin_bytecode", "The oldest Java runtime capable of loading {prefix}'s compiled classes.");
    public static final TextKey EXPLAIN_PLATFORM = key("rift.explain.platform", "The scheduler and server API family {prefix} detected at runtime.");
    public static final TextKey EXPLAIN_DYNAMIC_LIFECYCLE = key("rift.explain.dynamic_lifecycle", "Whether this platform safely exposes runtime world create, load, unload, quarantine, and restore operations.");
    public static final TextKey EXPLAIN_WORLD_CONTAINER = key("rift.explain.world_container", "The server directory where Bukkit world folders are stored.");
    public static final TextKey EXPLAIN_WRITABLE = key("rift.explain.writable", "Whether the operating system currently permits {prefix} to write inside the world container.");
    public static final TextKey EXPLAIN_LOCALE = key("rift.explain.locale", "The active language file under plugins/Rift/languages. Missing entries use built-in English.");
    public static final TextKey EXPLAIN_WORLD_COUNTS = key("rift.explain.world_counts", "Loaded counts active Bukkit worlds; managed counts {prefix} profiles; missing counts managed profiles that are neither loaded nor present on disk.");
    public static final TextKey EXPLAIN_QUARANTINE = key("rift.explain.quarantine", "Worlds moved aside by {prefix} delete and available to restore by quarantine id.");
    public static final TextKey EXPLAIN_WORLD_ENTRY = key("rift.explain.world_entry", "Loaded means active now; managed means {prefix} has a profile; on disk means a valid unloaded world folder exists.");
    public static final TextKey EXPLAIN_WORLD_KEY = key("rift.explain.world_key", "The canonical Paper identifier for this world.");
    public static final TextKey EXPLAIN_STORAGE_PATH = key("rift.explain.storage_path", "The exact resolved directory storing this world's files.");
    public static final TextKey EXPLAIN_STORAGE_LAYOUT = key("rift.explain.storage_layout", "The namespace that owns this world's dimension directory.");
    public static final TextKey EXPLAIN_PLAYERS = key("rift.explain.players", "The number of players currently inside this loaded world.");
    public static final TextKey EXPLAIN_GENERATOR_STATUS = key("rift.explain.generator_status", "Whether the configured generator is built in or its provider is available.");
    public static final TextKey EXPLAIN_PRIMARY_WORLD = key("rift.explain.primary_world", "Whether this is a primary server world that cannot be unmanaged or unloaded.");
    public static final TextKey EXPLAIN_CHECK_RESULT = key("rift.explain.check_result", "A summary of detected lifecycle blockers and warnings.");

    public static final TextKey FEEDBACK_TITLE = key("rift.feedback.title", "{prefix}");
    public static final TextKey FEEDBACK_WORLD_SUBTITLE = key("rift.feedback.world_subtitle", "&a{operation}&8: &f{world}");
    public static final TextKey FEEDBACK_TELEPORT_SUBTITLE = key("rift.feedback.teleport_subtitle", "&aArrived in &f{world}");
    public static final TextKey FEEDBACK_FAILURE_TITLE = key("rift.feedback.failure_title", "&cOperation failed");
    public static final TextKey FEEDBACK_FAILURE_SUBTITLE = key("rift.feedback.failure_subtitle", "&7{operation}&8: &f{world}");
    public static final TextKey FEEDBACK_ACTION_WORLD = key("rift.feedback.action_world", "{prefix}&r &7› &7&a{operation} &a{world}");
    public static final TextKey FEEDBACK_ACTION_TELEPORT = key("rift.feedback.action_teleport", "{prefix}&r &7› &7&aTeleported to &a{world}");
    public static final TextKey FEEDBACK_ACTION_FAILURE = key("rift.feedback.action_failure", "{prefix}&r &7› &7&c{operation} failed for &f{world}");

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
    public static final TextKey GUI_ROOT_TITLE = key("rift.gui.root_title", "{prefix} Configuration");
    public static final TextKey GUI_CATEGORY_TITLE = key("rift.gui.category_title", "{prefix}: {category}");
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
    public static final TextKey GUI_PROMPT = prefixed("rift.gui.prompt", "Type a new value for &f{setting}&7 in chat.");
    public static final TextKey GUI_PROMPT_CANCEL = prefixed("rift.gui.prompt_cancel", "Type &fcancel&7 to return without changing it.");
    public static final TextKey GUI_PROMPT_CANCELLED = prefixed("rift.gui.prompt_cancelled", "Configuration input cancelled.");
    public static final TextKey GUI_PROMPT_TIMEOUT = prefixed("rift.gui.prompt_timeout", "Configuration input timed out.");
    public static final TextKey GUI_SETTING_SAVED = prefixed("rift.gui.setting_saved",
            "&f{setting} &7› &7Changed to &a{after} &7from &f{before}&7.");
    public static final TextKey GUI_SETTING_FAILED = prefixed("rift.gui.setting_failed", "&cCould not update &f{setting}&7: {reason}");
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

    public static final TextKey COMMAND_POLICY_EDIT = key("rift.command.policy_edit", "Open the world-policy editor");
    public static final TextKey GUI_POLICY_TITLE = key("rift.gui.policy_title", "&dWorld policies");
    public static final TextKey GUI_POLICY_RESET = key("rift.gui.policy_reset", "&7Press Q to restore the default policy");
    public static final TextKey GUI_POLICY_SPAWN = key("rift.gui.policy_spawn", "&7Adjust the coordinates, then apply the safe spawn");
    public static final TextKey GUI_POLICY_CENTER = key("rift.gui.policy_center", "&dBorder center (x z)");
    public static final TextKey GUI_POLICY_WARNING = key("rift.gui.policy_warning", "&dBorder warning (blocks seconds)");
    public static final TextKey GUI_POLICY_DAMAGE = key("rift.gui.policy_damage", "&dBorder damage (amount buffer)");
    public static final TextKey GUI_POLICY_DENIAL = key("rift.gui.policy_denial", "&dAccess denial message");
    public static final TextKey GUI_POLICY_TAGS = key("rift.gui.policy_tags", "&7Enter comma-separated tags, or clear");
    public static final TextKey GUI_POLICY_CLEAR = key("rift.gui.policy_clear", "&7Enter clear to restore the default");
    public static final TextKey GUI_POLICY_PREVIOUS = key("rift.gui.policy_previous", "&ePrevious page");
    public static final TextKey GUI_POLICY_NEXT = key("rift.gui.policy_next", "&eNext page");
    public static final TextKey GUI_POLICY_DEFAULT = key("rift.gui.policy_default", "&7Default: &f{value}");
    public static final TextKey GUI_POLICY_NATIVE_DEFAULT = key("rift.gui.policy_native_default", "&7Native default: &f{value}");
    public static final TextKey GUI_POLICY_INHERITED = key("rift.gui.policy_inherited", "&7Policy: INHERIT");
    public static final TextKey GUI_POLICY_UNLOADED = key("rift.gui.policy_unloaded", "&7World unloaded: inherited values show native defaults");
    public static final TextKey GUI_POLICY_NUMBER = key("rift.gui.policy_number", "&7Left: +{step}; right: -{step}; shift: x10");
    public static final TextKey GUI_POLICY_SELECT = key("rift.gui.policy_select", "&7Click to choose from the available values");
    public static final TextKey GUI_POLICY_SELECTED = key("rift.gui.policy_selected", "&aSelected");
    public static final TextKey GUI_POLICY_APPLY = key("rift.gui.policy_apply", "&aApply spawn coordinates");
    public static final TextKey GUI_POLICY_HERE = key("rift.gui.policy_here", "&dUse my current position");
    public static final TextKey GUI_POLICY_DRAFT = key("rift.gui.policy_draft", "&7Draft: &f{value}");
    public static final TextKey GUI_POLICY_ENABLED = key("rift.gui.policy_enabled", "&dManaged");
    public static final TextKey GUI_POLICY_X = key("rift.gui.policy_x", "&dX coordinate");
    public static final TextKey GUI_POLICY_Y = key("rift.gui.policy_y", "&dY coordinate");
    public static final TextKey GUI_POLICY_Z = key("rift.gui.policy_z", "&dZ coordinate");
    public static final TextKey GUI_POLICY_YAW = key("rift.gui.policy_yaw", "&dYaw");
    public static final TextKey GUI_POLICY_BORDER_SIZE = key("rift.gui.policy_border_size", "&dBorder diameter");
    public static final TextKey GUI_POLICY_WARNING_DISTANCE = key("rift.gui.policy_warning_distance", "&dWarning distance (blocks)");
    public static final TextKey GUI_POLICY_WARNING_TIME = key("rift.gui.policy_warning_time", "&dWarning time (seconds)");
    public static final TextKey GUI_POLICY_DAMAGE_AMOUNT = key("rift.gui.policy_damage_amount", "&dDamage per block");
    public static final TextKey GUI_POLICY_DAMAGE_BUFFER = key("rift.gui.policy_damage_buffer", "&dDamage buffer (blocks)");

    public static final TextKey WORLD_ALREADY_EXISTS = key("rift.world.already_exists", CHAT_PREFIX + "&e{world} already exists. Use &f/rift info {world}&e to inspect it, or choose another name.");

    private static final List<TextKey> KEYS = List.of(
            WORLD_ALREADY_EXISTS,
            COMMAND_POLICY_EDIT, GUI_POLICY_TITLE, GUI_POLICY_RESET, GUI_POLICY_SPAWN,
            GUI_POLICY_CENTER, GUI_POLICY_WARNING, GUI_POLICY_DAMAGE, GUI_POLICY_DENIAL,
            GUI_POLICY_TAGS, GUI_POLICY_CLEAR, GUI_POLICY_PREVIOUS, GUI_POLICY_NEXT,
            GUI_POLICY_DEFAULT, GUI_POLICY_NUMBER, GUI_POLICY_SELECT, GUI_POLICY_SELECTED,
            GUI_POLICY_NATIVE_DEFAULT, GUI_POLICY_INHERITED, GUI_POLICY_UNLOADED,
            GUI_POLICY_APPLY, GUI_POLICY_HERE, GUI_POLICY_DRAFT, GUI_POLICY_ENABLED,
            GUI_POLICY_X, GUI_POLICY_Y, GUI_POLICY_Z, GUI_POLICY_YAW, GUI_POLICY_BORDER_SIZE,
            GUI_POLICY_WARNING_DISTANCE, GUI_POLICY_WARNING_TIME, GUI_POLICY_DAMAGE_AMOUNT, GUI_POLICY_DAMAGE_BUFFER,
            PREFIX, VERSION, COMMAND_ROOT, COMMAND_CREATE, COMMAND_IMPORT, COMMAND_LOAD, COMMAND_UNLOAD, COMMAND_DELETE,
            COMMAND_RESTORE, COMMAND_TELEPORT, COMMAND_SEND, COMMAND_LIST, COMMAND_INFO, COMMAND_GENERATORS,
            COMMAND_CONFIG, COMMAND_LANGUAGE, COMMAND_STATUS, COMMAND_DEBUG, COMMAND_DEBUG_DUMP, COMMAND_VERSION,
            COMMAND_AUTOLOAD, COMMAND_PROTECT, COMMAND_CHECK, COMMAND_CHECK_ALL, COMMAND_UNMANAGE,
            COMMAND_POLICY, COMMAND_POLICY_SHOW, COMMAND_POLICY_DIFFICULTY, COMMAND_POLICY_PVP,
            COMMAND_POLICY_GAMERULE, COMMAND_POLICY_SPAWN, COMMAND_POLICY_SPAWN_CLEAR, COMMAND_POLICY_BORDER,
            COMMAND_POLICY_BORDER_CLEAR, COMMAND_POLICY_ACCESS, COMMAND_POLICY_RESPAWN, COMMAND_POLICY_TAG,
            PARAM_NAME, PARAM_ENVIRONMENT, PARAM_GENERATOR, PARAM_SEED, PARAM_TYPE, PARAM_SAVE, PARAM_ID,
            PARAM_PLAYER, PARAM_ENABLED, PARAM_PAGE, PARAM_UPLOAD, PARAM_SENDER, PARAM_MANAGED_WORLD,
            PARAM_POLICY_DIFFICULTY, PARAM_POLICY_PVP, PARAM_GAME_RULE, PARAM_GAME_RULE_VALUE,
            PARAM_BORDER_SIZE, PARAM_BORDER_CENTER_X, PARAM_BORDER_CENTER_Z, PARAM_BORDER_WARNING_DISTANCE,
            PARAM_BORDER_WARNING_TIME, PARAM_BORDER_DAMAGE, PARAM_BORDER_BUFFER, PARAM_ACCESS_PERMISSION,
            PARAM_ACCESS_DENIED_MESSAGE, PARAM_RESPAWN_DESTINATION, PARAM_POLICY_TAG, PARAM_TAG_ENABLED, PARAM_TAG_FILTER, PARAM_TAG_GROUP,
            PERMISSION_DENIED, UNKNOWN_COMMAND, PLAYER_ONLY,
            OPERATION_QUEUED, OPERATION_FAILED, CREATED, IMPORTED, LOADED, UNLOADED, TELEPORTED,
            DELETE_CONFIRM, QUARANTINED, RESTORED, PROFILE_UPDATED, UNMANAGED_LOADED, UNMANAGED_UNLOADED,
            FOLIA_LIMIT, CONFIG_SAVED, CONFIG_OPENED, CONFIG_SAVE_FAILED, EMPTY_LIST, PLAYER_OFFLINE,
            UNKNOWN_WORLD, SECTION,
            ENTRY, DETAIL, INFO_TITLE, STATUS_TITLE, GENERATOR_FORMAT, FOLIA_STATUS_NOTE, POLICY_ACCESS_DENIED, LABEL_WORLDS,
            LABEL_QUARANTINE, LABEL_LOADED, LABEL_MANAGED, LABEL_ON_DISK, LABEL_MISSING, LABEL_ENVIRONMENT,
            LABEL_GENERATOR, LABEL_SEED, LABEL_AUTOLOAD, LABEL_PROTECTED, LABEL_OPERATION_ACTIVE,
            LABEL_WORLD_TYPES, LABEL_CONFIGURED_GENERATORS, LABEL_AVAILABLE, LABEL_UNAVAILABLE, LABEL_SERVER,
            LABEL_JAVA_RUNTIME, LABEL_PLUGIN_BYTECODE, LABEL_PLATFORM, LABEL_DYNAMIC_LIFECYCLE,
            LABEL_WORLD_CONTAINER, LABEL_WRITABLE, LABEL_LOCALE, LABEL_WORLD_COUNTS, LABEL_QUARANTINE_ENTRIES,
            LABEL_WORLD_KEY, LABEL_STORAGE_PATH, LABEL_STORAGE_LAYOUT, LABEL_PLAYERS, LABEL_GENERATOR_STATUS,
            LABEL_PRIMARY_WORLD, LABEL_CHECK_RESULT, LABEL_READY, LABEL_REVIEW_REQUIRED,
            LABEL_POLICY_DIFFICULTY, LABEL_POLICY_PVP, LABEL_POLICY_GAME_RULES, LABEL_POLICY_CUSTOM_SPAWN,
            LABEL_POLICY_MANAGED_BORDER, LABEL_POLICY_ACCESS_PERMISSION, LABEL_POLICY_RESPAWN_WORLD,
            LABEL_POLICY_TAGS, LABEL_NONE, LABEL_SERVER_DEFAULT,
            EXPLAIN_LOADED, EXPLAIN_MANAGED, EXPLAIN_ON_DISK, EXPLAIN_ENVIRONMENT, EXPLAIN_GENERATOR,
            EXPLAIN_SEED, EXPLAIN_AUTOLOAD, EXPLAIN_PROTECTED, EXPLAIN_OPERATION_ACTIVE, EXPLAIN_SERVER,
            EXPLAIN_JAVA_RUNTIME, EXPLAIN_PLUGIN_BYTECODE, EXPLAIN_PLATFORM, EXPLAIN_DYNAMIC_LIFECYCLE,
            EXPLAIN_WORLD_CONTAINER, EXPLAIN_WRITABLE, EXPLAIN_LOCALE, EXPLAIN_WORLD_COUNTS,
            EXPLAIN_QUARANTINE, EXPLAIN_WORLD_ENTRY, EXPLAIN_WORLD_KEY, EXPLAIN_STORAGE_PATH,
            EXPLAIN_STORAGE_LAYOUT, EXPLAIN_PLAYERS, EXPLAIN_GENERATOR_STATUS, EXPLAIN_PRIMARY_WORLD,
            EXPLAIN_CHECK_RESULT, FEEDBACK_TITLE, FEEDBACK_WORLD_SUBTITLE,
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

    public static boolean isSharedChat(String id) {
        return id.startsWith("director.runtime.")
                || id.startsWith("language.error.")
                || id.startsWith("language.usage.")
                || id.startsWith("language.selection.")
                || id.startsWith("language.editor.prompt.")
                || id.startsWith("language.editor.error.")
                || id.startsWith("language.editor.input.")
                || id.startsWith("language.editor.saved.")
                || id.equals("language.editor.loading")
                || id.startsWith("debug.") && !id.startsWith("debug.action.");
    }

    private static TextKey key(String id, String english) {
        return english.contains("{prefix}")
                ? TextKey.ofOptional(id, english, "prefix")
                : TextKey.of(id, english);
    }

    private static TextKey prefixed(String id, String english) {
        return TextKey.ofOptional(id, CHAT_PREFIX + english, "prefix");
    }

    private static MessageCatalog createCatalog() {
        MessageCatalog.Builder builder = MessageCatalog.builder(VolmitLocales.ENGLISH);
        addSharedMessages(builder, DirectorMessages.keys());
        addSharedMessages(builder, BukkitLanguageMessages.keys());
        addSharedMessages(builder, BukkitDebugMessages.keys());
        builder.addAll(KEYS);
        return builder.build();
    }

    private static void addSharedMessages(MessageCatalog.Builder builder, List<MessageKey> keys) {
        for (MessageKey definition : keys) {
            TextKey text = (TextKey) definition;
            String template = text.english().replace("{plugin}: ", "").replace("{plugin}", "{prefix}");
            if (isSharedChat(text.id())) {
                String color = text.id().contains(".error.") || text.id().endsWith("failed") ? "&c" : "&7";
                template = CHAT_PREFIX + color + template;
            }
            builder.add(key(text.id(), template));
        }
    }

}
