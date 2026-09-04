package com.volmit.rift.feedback;

import art.arcane.volmlib.util.hud.HudActionBar;
import art.arcane.volmlib.util.hud.HudSegment;
import art.arcane.volmlib.util.hud.HudSlot;
import art.arcane.volmlib.util.hud.HudTitleClaim;
import art.arcane.volmlib.util.hud.HudTitleService;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.config.RiftConfig;
import com.volmit.rift.config.RiftConfigManager;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class RiftFeedbackService implements AutoCloseable {
    private static final String PURPOSE = "rift-feedback";
    private static final int PRIORITY = 20;

    private final Rift plugin;
    private final RiftConfigManager config;
    private final RiftLocalization language;
    private final HudActionBar actionBar;
    private final HudTitleService titles;

    public RiftFeedbackService(Rift plugin, RiftConfigManager config, RiftLocalization language) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.language = Objects.requireNonNull(language, "language");
        actionBar = new HudActionBar(plugin);
        titles = new HudTitleService(plugin);
    }

    public void world(CommandSender sender, String operation, String world) {
        if (!(sender instanceof Player player) || !config.get().isWorldLifecycleFeedback()) {
            return;
        }
        present(player, Feedback.world(operation, world));
    }

    public void teleport(Player player, String world) {
        if (!config.get().isTeleportFeedback()) {
            return;
        }
        present(player, Feedback.teleport(world));
    }

    public void failure(CommandSender sender, String operation, String world) {
        if (!(sender instanceof Player player) || !config.get().isFailureFeedback()) {
            return;
        }
        present(player, Feedback.failure(operation, world));
    }

    @Override
    public void close() {
        actionBar.shutdown();
        titles.shutdown();
    }

    private void present(Player player, Feedback feedback) {
        if (FoliaScheduler.isOwnedByCurrentRegion(player)) {
            presentOwned(player, feedback);
            return;
        }
        if (!FoliaScheduler.runEntity(plugin, player, () -> presentOwned(player, feedback))) {
            plugin.getLogger().warning("Unable to schedule Rift feedback for " + player.getName());
        }
    }

    private void presentOwned(Player player, Feedback feedback) {
        RiftConfig current = config.get();
        MessageArgs arguments = MessageArgs.builder()
                .untrusted("operation", feedback.operation())
                .untrusted("world", feedback.world())
                .build();
        long ttlMillis = Math.max(1_000L, (current.getTitleFadeInTicks()
                + current.getTitleStayTicks() + current.getTitleFadeOutTicks()) * 50L);
        if (current.isTitlePopups()) {
            HudTitleClaim claim = titles.open(player, PURPOSE, PRIORITY, ttlMillis);
            if (claim.resolve()) {
                ComponentText title = language.text(player, feedback.failure()
                        ? RiftMessages.FEEDBACK_FAILURE_TITLE : RiftMessages.FEEDBACK_TITLE);
                ComponentText subtitle = language.text(player, feedback.subtitle(), arguments);
                ComponentMessenger.showTitle(
                        player,
                        title,
                        subtitle,
                        Duration.ofMillis(current.getTitleFadeInTicks() * 50L),
                        Duration.ofMillis(current.getTitleStayTicks() * 50L),
                        Duration.ofMillis(current.getTitleFadeOutTicks() * 50L)
                );
            }
        }
        if (current.isActionBarPopups()) {
            String text = language.text(player, feedback.actionBar(), arguments).legacy();
            actionBar.publish(player, new HudSegment(PURPOSE, PRIORITY, ttlMillis, List.of(HudSlot.CENTER), text));
        }
        if (current.isSounds()) {
            player.playSound(
                    player.getLocation(),
                    sound(current, feedback),
                    SoundCategory.PLAYERS,
                    current.getSoundVolume(),
                    current.getSoundPitch()
            );
        }
    }

    private static String sound(RiftConfig config, Feedback feedback) {
        if (feedback.failure()) {
            return config.failureSound();
        }
        if (feedback.teleport()) {
            return config.teleportSound();
        }
        return config.worldLifecycleSound();
    }

    private record Feedback(
            String operation,
            String world,
            art.arcane.volmlib.util.localization.TextKey subtitle,
            art.arcane.volmlib.util.localization.TextKey actionBar,
            boolean teleport,
            boolean failure
    ) {
        private static Feedback world(String operation, String world) {
            return new Feedback(operation, world, RiftMessages.FEEDBACK_WORLD_SUBTITLE,
                    RiftMessages.FEEDBACK_ACTION_WORLD, false, false);
        }

        private static Feedback teleport(String world) {
            return new Feedback("teleport", world, RiftMessages.FEEDBACK_TELEPORT_SUBTITLE,
                    RiftMessages.FEEDBACK_ACTION_TELEPORT, true, false);
        }

        private static Feedback failure(String operation, String world) {
            return new Feedback(operation, world, RiftMessages.FEEDBACK_FAILURE_SUBTITLE,
                    RiftMessages.FEEDBACK_ACTION_FAILURE, false, true);
        }
    }
}
