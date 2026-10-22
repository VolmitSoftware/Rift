package com.volmit.rift.command;

import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionResult;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRoute;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.volmit.rift.Rift;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.UUID;

public final class RiftCommandService implements CommandExecutor, TabCompleter {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final DirectorMiniMenu.Theme THEME = new DirectorMiniMenu.Theme(
            "#6f2dbd", "#d16ba5", "#31104f", "#7d3cc8", "#AAAAAA", "#FF5555", "#D1ADE8", "#AAAAAA"
    );
    private static final String LANGUAGE = "language";
    private static final String ADMIN_PERMISSION = "rift.admin";
    private static final List<String> ROOT_PERMISSION = List.of("rift.command");
    private static final List<String> DEBUG_PERMISSIONS = List.of("rift.command", "rift.debug");
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
        DirectorRoute route = route(sender, label, args);
        List<String> required = requiredPermissions(route.path());
        if (!holdsAny(sender, required)) {
            plugin.language().send(sender, RiftMessages.PERMISSION_DENIED, MessageArgs.builder()
                    .untrusted("permission", required.getFirst())
                    .build());
            return true;
        }
        if (language(route)) {
            return plugin.languageSwitcher().command(sender, route.remainingArgs().toArray(String[]::new));
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
        UUID audience = sender instanceof Player player ? player.getUniqueId() : null;
        return LanguageAudience.call(audience, () -> complete(sender, alias, args));
    }

    private List<String> complete(CommandSender sender, String alias, String[] args) {
        try {
            String[] prior = args.length == 0 ? args : Arrays.copyOf(args, args.length - 1);
            DirectorRoute route = route(sender, alias, prior);
            if (!holdsAny(sender, requiredPermissions(route.path()))) {
                return List.of();
            }
            if (language(route)) {
                List<String> remaining = new ArrayList<>(route.remainingArgs());
                remaining.add(args[args.length - 1]);
                return plugin.languageSwitcher().complete(sender, remaining.toArray(String[]::new));
            }
            List<String> suggestions = director.tabComplete(invocation(sender, alias, args));
            if (route.invocable()) {
                return suggestions;
            }
            List<String> visible = new ArrayList<>(suggestions.size());
            for (String suggestion : suggestions) {
                String[] candidate = Arrays.copyOf(prior, prior.length + 1);
                candidate[prior.length] = suggestion;
                if (holdsAny(sender, requiredPermissions(route(sender, alias, candidate).path()))) {
                    visible.add(suggestion);
                }
            }
            return visible;
        } catch (Throwable exception) {
            plugin.getLogger().log(Level.WARNING, "Rift tab completion failed", exception);
            return List.of();
        }
    }

    DirectorRuntimeEngine director() {
        return director;
    }

    DirectorRoute route(CommandSender sender, String label, String[] args) {
        if (args.length > 0 && helpToken(args[0])) {
            return new DirectorRoute(null, Arrays.asList(args));
        }
        return director.route(invocation(sender, label, args));
    }

    static List<String> requiredPermissions(List<String> path) {
        if (path.isEmpty()) {
            return ROOT_PERMISSION;
        }
        String first = path.getFirst();
        if (first.equals(LANGUAGE)) {
            return List.of();
        }
        if (first.equals("debug")) {
            if (path.size() == 1) {
                return DEBUG_PERMISSIONS;
            }
            return path.get(1).equals("version") ? ROOT_PERMISSION : List.of("rift.debug");
        }
        String permission = PERMISSIONS.get(first);
        return permission == null ? List.of(ADMIN_PERMISSION) : List.of(permission);
    }

    private static boolean holdsAny(CommandSender sender, List<String> permissions) {
        if (permissions.isEmpty() || sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        for (String permission : permissions) {
            if (sender.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    private static boolean language(DirectorRoute route) {
        List<String> path = route.path();
        return !path.isEmpty() && path.getFirst().equals(LANGUAGE);
    }

    private static boolean helpToken(String token) {
        return token == null || token.isBlank() || token.equals("?") || token.equalsIgnoreCase("help");
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
        permissions.put("unmanage", "rift.unmanage");
        permissions.put("tp", "rift.teleport");
        permissions.put("send", "rift.teleport.others");
        permissions.put("list", "rift.list");
        permissions.put("info", "rift.info");
        permissions.put("generators", "rift.generators");
        permissions.put("config", "rift.config");
        permissions.put("status", "rift.status");
        permissions.put("autoload", "rift.config");
        permissions.put("protect", "rift.config");
        permissions.put("policy", "rift.policy");
        permissions.put("check-all", "rift.check");
        permissions.put("check", "rift.check");
        permissions.put("version", "rift.command");
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
                language.send(sender, ComponentText.component(MINI_MESSAGE.deserialize(message)));
            }
        }
    }
}
