package com.volmit.rift.command;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import com.volmit.rift.Rift;
import com.volmit.rift.localization.RiftMessages;
import com.volmit.rift.world.WorldPolicyService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Director(name = "policy", description = "Inspect and edit per-world policies", descriptionKey = "rift.command.policy")
public final class RiftPolicyCommands {
    private final Rift plugin;
    private final WorldPolicyService policies;

    public RiftPolicyCommands(Rift plugin) {
        this.plugin = plugin;
        this.policies = plugin.worldPolicies();
    }

    @Director(name = "show", description = "Show the active policy profile for a managed world", descriptionKey = "rift.command.policy_show")
    public void show(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (allowed(sender)) {
            policies.show(sender, name);
        }
    }

    @Director(name = "difficulty", description = "Set PEACEFUL, EASY, NORMAL, HARD, or INHERIT", descriptionKey = "rift.command.policy_difficulty")
    public void difficulty(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "value", description = "Difficulty or INHERIT", descriptionKey = "rift.parameter.policy_difficulty") String value,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        run(sender, name, RiftMessages.LABEL_POLICY_DIFFICULTY, () -> policies.setDifficulty(sender, name, value));
    }

    @Director(name = "pvp", description = "Set ALLOW, DENY, or INHERIT", descriptionKey = "rift.command.policy_pvp")
    public void pvp(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "value", description = "ALLOW, DENY, or INHERIT", descriptionKey = "rift.parameter.policy_pvp") String value,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        run(sender, name, RiftMessages.LABEL_POLICY_PVP, () -> policies.setPvp(sender, name, value));
    }

    @Director(name = "gamerule", description = "Set a game rule value or stop managing it with INHERIT", descriptionKey = "rift.command.policy_gamerule")
    public void gameRule(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "rule", description = "Minecraft game-rule name", descriptionKey = "rift.parameter.game_rule") String rule,
            @Param(name = "value", description = "Rule value or INHERIT", descriptionKey = "rift.parameter.game_rule_value") String value,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        run(sender, name, RiftMessages.LABEL_POLICY_GAME_RULES, () -> policies.setGameRule(sender, name, rule, value));
    }

    @Director(name = "spawn", description = "Use your current safe location as this world's spawn", descriptionKey = "rift.command.policy_spawn")
    public void spawn(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        if (!(sender instanceof Player player)) {
            plugin.language().send(sender, RiftMessages.PLAYER_ONLY);
            return;
        }
        run(sender, name, RiftMessages.LABEL_POLICY_CUSTOM_SPAWN, () -> policies.setSpawn(sender, name, player));
    }

    @Director(name = "spawn-clear", description = "Stop managing this world's spawn", descriptionKey = "rift.command.policy_spawn_clear")
    public void clearSpawn(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        run(sender, name, RiftMessages.LABEL_POLICY_CUSTOM_SPAWN, () -> policies.clearSpawn(sender, name));
    }

    @Director(name = "border", description = "Configure this world's managed border", descriptionKey = "rift.command.policy_border")
    public void border(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "size", description = "Border diameter", descriptionKey = "rift.parameter.border_size") double size,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        run(sender, name, RiftMessages.LABEL_POLICY_MANAGED_BORDER,
                () -> policies.setBorder(sender, name, size));
    }

    @Director(name = "border-center", description = "Configure this world's border center", descriptionKey = "rift.command.policy_border")
    public void borderCenter(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "x", description = "Border center X", descriptionKey = "rift.parameter.border_center_x") double centerX,
            @Param(name = "z", description = "Border center Z", descriptionKey = "rift.parameter.border_center_z") double centerZ,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        run(sender, name, RiftMessages.LABEL_POLICY_MANAGED_BORDER,
                () -> policies.setBorderCenter(sender, name, centerX, centerZ));
    }

    @Director(name = "border-warning", description = "Configure this world's border warning", descriptionKey = "rift.command.policy_border")
    public void borderWarning(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "distance", description = "Warning distance", descriptionKey = "rift.parameter.border_warning_distance") int warningDistance,
            @Param(name = "seconds", description = "Warning seconds", descriptionKey = "rift.parameter.border_warning_time") int warningTime,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        run(sender, name, RiftMessages.LABEL_POLICY_MANAGED_BORDER,
                () -> policies.setBorderWarning(sender, name, warningDistance, warningTime));
    }

    @Director(name = "border-damage", description = "Configure this world's border damage", descriptionKey = "rift.command.policy_border")
    public void borderDamage(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "amount", description = "Damage per outside block", descriptionKey = "rift.parameter.border_damage") double damageAmount,
            @Param(name = "buffer", description = "Safe damage buffer", descriptionKey = "rift.parameter.border_buffer") double damageBuffer,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        run(sender, name, RiftMessages.LABEL_POLICY_MANAGED_BORDER,
                () -> policies.setBorderDamage(sender, name, damageAmount, damageBuffer));
    }

    @Director(name = "border-clear", description = "Reset this world's border and stop managing it", descriptionKey = "rift.command.policy_border_clear")
    public void clearBorder(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        run(sender, name, RiftMessages.LABEL_POLICY_MANAGED_BORDER, () -> policies.clearBorder(sender, name));
    }

    @Director(name = "access", description = "Require a permission to enter this world, or use clear", descriptionKey = "rift.command.policy_access")
    public void access(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "permission", description = "Permission node or clear", descriptionKey = "rift.parameter.access_permission") String permission,
            @Param(name = "denied-message", description = "Optional denial message", descriptionKey = "rift.parameter.access_denied_message", defaultValue = "default") String deniedMessage,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        run(sender, name, RiftMessages.LABEL_POLICY_ACCESS_PERMISSION,
                () -> policies.setAccess(sender, name, permission, deniedMessage));
    }

    @Director(name = "respawn", description = "Route deaths from this world to another managed world, or use default", descriptionKey = "rift.command.policy_respawn")
    public void respawn(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "destination", description = "Managed world or default", descriptionKey = "rift.parameter.respawn_destination") String destination,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        run(sender, name, RiftMessages.LABEL_POLICY_RESPAWN_WORLD,
                () -> policies.setRespawn(sender, name, destination));
    }

    @Director(name = "tag", description = "Add or remove an operator tag", descriptionKey = "rift.command.policy_tag")
    public void tag(
            @Param(name = "name", description = "Managed world", descriptionKey = "rift.parameter.managed_world", customHandler = RiftCommandHandlers.ManagedWorld.class) String name,
            @Param(name = "tag", description = "Tag name", descriptionKey = "rift.parameter.policy_tag") String tag,
            @Param(name = "enabled", description = "true adds and false removes", descriptionKey = "rift.parameter.tag_enabled") boolean enabled,
            @Param(name = "sender", description = "Command sender", descriptionKey = "rift.parameter.sender", contextual = true) CommandSender sender
    ) {
        run(sender, name, RiftMessages.LABEL_POLICY_TAGS, () -> policies.setTag(sender, name, tag, enabled));
    }

    private void run(CommandSender sender, String world, TextKey operation, Runnable action) {
        if (!allowed(sender)) {
            return;
        }
        try {
            action.run();
        } catch (RuntimeException exception) {
            plugin.language().send(sender, RiftMessages.OPERATION_FAILED, MessageArgs.builder()
                    .untrusted("operation", plugin.language().text(sender, operation).plain())
                    .untrusted("world", world)
                    .untrusted("reason", exception.getMessage())
                    .build());
        }
    }

    private boolean allowed(CommandSender sender) {
        if (sender.hasPermission("rift.policy") || sender.hasPermission("rift.admin")) {
            return true;
        }
        plugin.language().send(sender, RiftMessages.PERMISSION_DENIED, MessageArgs.builder()
                .untrusted("permission", "rift.policy")
                .build());
        return false;
    }
}
