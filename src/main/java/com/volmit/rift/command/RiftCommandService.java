package com.volmit.rift.command;

import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionResult;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.volmit.rift.Rift;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.UUID;

public final class RiftCommandService implements CommandExecutor, TabCompleter {
    private static final DirectorMiniMenu.Theme THEME = new DirectorMiniMenu.Theme(
            "#6f2dbd", "#d16ba5", "#31104f", "#7d3cc8", "#dec8f5", "#ff6666", "#d9a7ff", "#9370aa"
    );
    private static final Map<String, String> PERMISSIONS = permissions();

    private final Rift plugin;
    private final DirectorRuntimeEngine director;

    public RiftCommandService(Rift plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.director = DirectorEngineFactory.create(
                new RiftCommands(plugin),
                DirectorEngineOptions.builder()
                        .contexts(contexts())
                        .textResolver(plugin.language().directorResolver())
                        .build()
        );
    }

    public static DirectorMiniMenu.Theme theme() {
        return THEME;
    }

    public void register() {
        PluginCommand command = plugin.getCommand("rift");
        if (command == null) {
            throw new IllegalStateException("plugin.yml did not register /rift");
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        UUID audience = sender instanceof Player player ? player.getUniqueId() : null;
        return LanguageAudience.call(audience, () -> executeCommand(sender, label, args));
    }

    private boolean executeCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("rift.command") && !sender.hasPermission("rift.admin")
                && requiresRootPermission(args)) {
            plugin.language().send(sender, RiftMessages.PERMISSION_DENIED, MessageArgs.builder()
                    .untrusted("permission", "rift.command")
                    .build());
            return true;
        }
        if (!canAccessPath(sender, args)) {
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("language")) {
            return plugin.languageSwitcher().command(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        Optional<DirectorMiniMenu.DirectorHelpPage> help = DirectorMiniMenu.resolveHelp(director, Arrays.asList(args));
        if (help.isPresent()) {
            DirectorMiniMenu.deliver(sender, help.get(), THEME, plugin.language().directorResolver());
            return true;
        }
        try {
            DirectorExecutionResult result = director.execute(invocation(sender, label, args));
            if (result.isHandled()) {
                return true;
            }
        } catch (Throwable exception) {
            plugin.getLogger().log(Level.SEVERE, "Rift command execution failed", exception);
        }
        plugin.language().send(sender, RiftMessages.UNKNOWN_COMMAND);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!mayTabComplete(sender, args)) {
            return List.of();
        }
        UUID audience = sender instanceof Player player ? player.getUniqueId() : null;
        return LanguageAudience.call(audience, () -> complete(sender, alias, args));
    }

    private List<String> complete(CommandSender sender, String alias, String[] args) {
        try {
            if (args.length > 0 && args[0].equalsIgnoreCase("language")) {
                return plugin.languageSwitcher().complete(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            List<String> suggestions = director.tabComplete(invocation(sender, alias, args));
            if (args.length != 1) {
                return suggestions;
            }
            return suggestions.stream()
                    .filter(suggestion -> hasPermission(sender, PERMISSIONS.get(suggestion.toLowerCase(Locale.ROOT))))
                    .toList();
        } catch (Throwable exception) {
            plugin.getLogger().log(Level.WARNING, "Rift tab completion failed", exception);
            return List.of();
        }
    }

    DirectorRuntimeEngine director() {
        return director;
    }

    private boolean canAccessPath(CommandSender sender, String[] args) {
        String permission = permissionFor(args);
        if (hasPermission(sender, permission)) {
            return true;
        }
        plugin.language().send(sender, RiftMessages.PERMISSION_DENIED, MessageArgs.builder()
                .untrusted("permission", permission)
                .build());
        return false;
    }

    private boolean canAccessPathSilently(CommandSender sender, String[] args) {
        return hasPermission(sender, permissionFor(args));
    }

    private boolean mayTabComplete(CommandSender sender, String[] args) {
        if (args.length == 0 || args.length == 1 && args[0].isBlank()) {
            return sender.hasPermission("rift.command") || sender.hasPermission("rift.admin");
        }
        return !requiresRootPermission(args) && canAccessPathSilently(sender, args)
                || sender.hasPermission("rift.command")
                || sender.hasPermission("rift.admin");
    }

    private static boolean requiresRootPermission(String[] args) {
        if (args == null || args.length == 0) {
            return true;
        }
        String first = args[0] == null ? "" : args[0].toLowerCase(Locale.ROOT);
        if (first.isBlank() || first.equals("help") || first.equals("?")) {
            return true;
        }
        return !PERMISSIONS.containsKey(first) && !first.equals("language") && !first.equals("debug");
    }

    private static boolean hasPermission(CommandSender sender, String permission) {
        return permission == null || sender.hasPermission(permission) || sender.hasPermission("rift.admin");
    }

    private static String permissionFor(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        String first = args[0] == null ? "" : args[0].toLowerCase(Locale.ROOT);
        if (first.isBlank() || first.equals("help") || first.equals("?")) {
            return null;
        }
        return PERMISSIONS.get(first);
    }

    private DirectorInvocation invocation(CommandSender sender, String label, String[] args) {
        return new DirectorInvocation(new BukkitDirectorSender(sender, plugin.language()), label, Arrays.asList(args));
    }

    private static DirectorContextRegistry contexts() {
        DirectorContextRegistry contexts = new DirectorContextRegistry();
        contexts.register(CommandSender.class, (invocation, map) -> {
            if (invocation.getSender() instanceof BukkitDirectorSender sender) {
                return sender.sender();
            }
            return null;
        });
        contexts.register(Player.class, (invocation, map) -> {
            if (invocation.getSender() instanceof BukkitDirectorSender sender && sender.sender() instanceof Player player) {
                return player;
            }
            return null;
        });
        return contexts;
    }

    private static Map<String, String> permissions() {
        Map<String, String> permissions = new LinkedHashMap<>();
        permissions.put("create", "rift.create");
        permissions.put("import", "rift.import");
        permissions.put("load", "rift.load");
        permissions.put("unload", "rift.unload");
        permissions.put("delete", "rift.delete");
        permissions.put("restore", "rift.restore");
        permissions.put("tp", "rift.teleport");
        permissions.put("teleport", "rift.teleport");
        permissions.put("send", "rift.teleport.others");
        permissions.put("list", "rift.list");
        permissions.put("info", "rift.info");
        permissions.put("generators", "rift.generators");
        permissions.put("config", "rift.config");
        permissions.put("editor", "rift.config");
        permissions.put("status", "rift.status");
        permissions.put("autoload", "rift.config");
        permissions.put("protect", "rift.config");
        return Map.copyOf(permissions);
    }

    private record BukkitDirectorSender(CommandSender sender, RiftLocalization language) implements DirectorSender {
        @Override
        public String getName() {
            return sender.getName();
        }

        @Override
        public boolean isPlayer() {
            return sender instanceof Player;
        }

        @Override
        public void sendMessage(String message) {
            if (message != null && !message.isBlank()) {
                language.send(sender, ComponentText.literal(message));
            }
        }
    }
}
