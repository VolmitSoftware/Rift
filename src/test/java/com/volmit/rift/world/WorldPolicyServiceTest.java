package com.volmit.rift.world;

import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import com.volmit.rift.storage.WorldNamePolicy;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class WorldPolicyServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void queuedActivationUsesTheLatestProfile() throws Exception {
        try (Context context = context()) {
            CommandSender sender = mock(CommandSender.class);
            WorldProfile saved = profile("HARD");
            when(context.profiles.update(eq("private"), any())).thenReturn(saved);
            when(context.language.text(sender, RiftMessages.LABEL_POLICY_DIFFICULTY))
                    .thenReturn(ComponentText.literal("Difficulty"));

            context.service.setDifficulty(sender, "private", "HARD");
            context.async.removeFirst().run();
            when(context.profiles.find("private")).thenReturn(Optional.of(profile("PEACEFUL")));
            context.global.removeFirst().run();

            verify(context.world).setDifficulty(Difficulty.PEACEFUL);
            verify(context.world, never()).setDifficulty(Difficulty.HARD);
        }
    }

    @Test
    void queuedActivationDoesNotApplyAnUnmanagedProfile() throws Exception {
        try (Context context = context()) {
            CommandSender sender = mock(CommandSender.class);
            when(context.profiles.update(eq("private"), any())).thenReturn(profile("HARD"));
            when(context.language.text(sender, RiftMessages.LABEL_POLICY_DIFFICULTY))
                    .thenReturn(ComponentText.literal("Difficulty"));
            context.service.setDifficulty(sender, "private", "HARD");
            context.async.removeFirst().run();
            when(context.profiles.find("private")).thenReturn(Optional.empty());

            context.global.removeFirst().run();

            verify(context.world, never()).setDifficulty(any());
        }
    }

    @Test
    void delayedJoinCheckHonorsPermissionGrantedBeforeExecution() {
        try (Context context = context()) {
            context.service.onJoin(join(context.player));
            assertThat(context.entity).hasSize(1);
            when(context.player.hasPermission("rift.private")).thenReturn(true);

            context.entity.removeFirst().run();

            verify(context.player, never()).teleportAsync(any());
            verify(context.player, never()).kick(any(Component.class));
            verify(context.player, never()).sendMessage(any(Component.class));
        }
    }

    @Test
    void restrictedJoinWithoutFallbackDisconnectsWithExpandedDenial() {
        try (Context context = context()) {
            context.service.onJoin(join(context.player));

            context.entity.removeFirst().run();

            verify(context.player).kick(Component.text("Denied private: rift.private"));
        }
    }

    @Test
    void failedEvacuationDisconnectsPlayerStillInRestrictedWorld() {
        try (Context context = context()) {
            context.addFallback();
            when(context.player.teleportAsync(any())).thenReturn(CompletableFuture.completedFuture(false));
            context.service.onJoin(join(context.player));

            context.drainEntityTasks();

            verify(context.player).teleportAsync(any());
            verify(context.player).kick(Component.text("Denied private: rift.private"));
        }
    }

    @Test
    void failedEvacuationDoesNotDisconnectAfterPermissionWasGranted() {
        try (Context context = context()) {
            context.addFallback();
            CompletableFuture<Boolean> teleport = new CompletableFuture<>();
            when(context.player.teleportAsync(any())).thenReturn(teleport);
            context.service.onJoin(join(context.player));
            context.entity.removeFirst().run();
            when(context.player.hasPermission("rift.private")).thenReturn(true);

            teleport.complete(false);
            context.drainEntityTasks();

            verify(context.player, never()).kick(any(Component.class));
        }
    }

    @Test
    void sameWorldRespawnRechecksAccessAfterRespawn() {
        try (Context context = context()) {
            PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
            when(event.getPlayer()).thenReturn(context.player);

            context.service.afterRespawn(event);
            context.drainEntityTasks();

            verify(context.player).kick(Component.text("Denied private: rift.private"));
        }
    }

    @Test
    void applyingPolicyDefersPlayerPermissionChecksToEntityTasks() {
        try (Context context = context()) {
            when(context.world.getPlayers()).thenReturn(List.of(context.player));

            assertThat(context.service.apply(context.world)).isTrue();

            verify(context.player, never()).hasPermission(anyString());
            assertThat(context.entity).hasSize(1);
            context.drainEntityTasks();
            verify(context.player).kick(Component.text("Denied private: rift.private"));
        }
    }

    private Context context() {
        return new Context(temporaryDirectory);
    }

    private static PlayerJoinEvent join(Player player) {
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private static WorldProfile profile(String difficulty) {
        WorldProfile profile = new WorldProfile();
        profile.setName("private");
        profile.setDifficulty(difficulty);
        profile.setAccessPermission("rift.private");
        profile.setAccessDeniedMessage("Denied {world}: {permission}");
        return profile;
    }

    private static final class Context implements AutoCloseable {
        private final Rift plugin = mock(Rift.class);
        private final RiftLocalization language = mock(RiftLocalization.class);
        private final WorldProfileStore profiles = mock(WorldProfileStore.class);
        private final World world = mock(World.class);
        private final Player player = mock(Player.class);
        private final Deque<Runnable> async = new ArrayDeque<>();
        private final Deque<Runnable> global = new ArrayDeque<>();
        private final Deque<Runnable> entity = new ArrayDeque<>();
        private final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        private final MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class, invocation -> {
            Deque<Runnable> tasks = switch (invocation.getMethod().getName()) {
                case "runAsync" -> async;
                case "runGlobal" -> global;
                case "runEntity" -> entity;
                default -> throw new IllegalStateException("Unexpected scheduler call: " + invocation.getMethod());
            };
            for (Object argument : invocation.getArguments()) {
                if (argument instanceof Runnable task) {
                    tasks.addLast(task);
                    return true;
                }
            }
            throw new IllegalStateException("Scheduled call had no task");
        });
        private final WorldPolicyService service;

        private Context(Path directory) {
            when(plugin.getLogger()).thenReturn(Logger.getLogger(WorldPolicyServiceTest.class.getName()));
            when(world.getKey()).thenReturn(RiftWorldIdentity.key("private"));
            when(world.getWorldBorder()).thenReturn(mock(WorldBorder.class));
            when(world.getPlayers()).thenReturn(List.of());
            when(player.getWorld()).thenReturn(world);
            when(profiles.find("private")).thenReturn(Optional.of(profile("INHERIT")));
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            bukkit.when(() -> Bukkit.getWorld(RiftWorldIdentity.key("private"))).thenReturn(world);
            service = new WorldPolicyService(plugin, language, new WorldNamePolicy(directory), profiles);
        }

        private void addFallback() {
            World fallback = mock(World.class);
            when(fallback.getKey()).thenReturn(RiftWorldIdentity.key("lobby"));
            when(fallback.getSpawnLocation()).thenReturn(new Location(fallback, 0.0D, 64.0D, 0.0D));
            when(profiles.find("lobby")).thenReturn(Optional.empty());
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world, fallback));
        }

        private void drainEntityTasks() {
            int remaining = 10;
            while (!entity.isEmpty() && remaining-- > 0) {
                entity.removeFirst().run();
            }
            assertThat(entity).isEmpty();
        }

        @Override
        public void close() {
            scheduler.close();
            bukkit.close();
        }
    }
}
