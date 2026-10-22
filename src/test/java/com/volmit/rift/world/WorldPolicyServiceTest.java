package com.volmit.rift.world;

import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import com.volmit.rift.storage.WorldNamePolicy;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Animals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Slime;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Squid;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class WorldPolicyServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void weatherPoliciesApplyTheirStormAndThunderStates() {
        for (String weather : List.of("CLEAR", "RAIN", "THUNDER")) {
            try (Context context = context()) {
                WorldProfile profile = profile("INHERIT");
                profile.setWeather(weather);
                when(context.profiles.find("private")).thenReturn(Optional.of(profile));
                AtomicBoolean storm = new AtomicBoolean(weather.equals("CLEAR"));
                AtomicBoolean thunder = new AtomicBoolean(!weather.equals("THUNDER"));
                when(context.world.hasStorm()).thenAnswer(invocation -> storm.get());
                when(context.world.isThundering()).thenAnswer(invocation -> thunder.get());
                doAnswer(invocation -> {
                    storm.set(invocation.getArgument(0));
                    return null;
                }).when(context.world).setStorm(anyBoolean());
                doAnswer(invocation -> {
                    thunder.set(invocation.getArgument(0));
                    return null;
                }).when(context.world).setThundering(anyBoolean());

                assertThat(context.service.apply(context.world)).isTrue();

                verify(context.world).setStorm(!weather.equals("CLEAR"));
                verify(context.world).setThundering(weather.equals("THUNDER"));
            }
        }
    }

    @Test
    void inheritedWeatherIsNotChangedOrLocked() {
        try (Context context = context()) {
            assertThat(context.service.apply(context.world)).isTrue();
            WeatherChangeEvent weather = new WeatherChangeEvent(context.world, true, WeatherChangeEvent.Cause.NATURAL);
            ThunderChangeEvent thunder = new ThunderChangeEvent(context.world, true, ThunderChangeEvent.Cause.NATURAL);
            context.service.onWeather(weather);
            context.service.onThunder(thunder);
            assertThat(weather.isCancelled()).isFalse();
            assertThat(thunder.isCancelled()).isFalse();
            verify(context.world, never()).setStorm(anyBoolean());
            verify(context.world, never()).setThundering(anyBoolean());
        }
    }

    @Test
    void cancelledWeatherChangeReportsRuntimeActivationFailure() {
        try (Context context = context()) {
            WorldProfile profile = profile("INHERIT");
            profile.setWeather("THUNDER");
            when(context.profiles.find("private")).thenReturn(Optional.of(profile));
            assertThat(context.service.apply(context.world)).isFalse();
            verify(context.world).setStorm(true);
            verify(context.world).setThundering(true);
        }
    }

    @Test
    void weatherEventsRejectOnlyChangesOpposingTheirPolicy() {
        for (String policy : List.of("CLEAR", "RAIN", "THUNDER")) {
            try (Context context = context()) {
                WorldProfile profile = profile("INHERIT");
                profile.setWeather(policy);
                when(context.profiles.find("private")).thenReturn(Optional.of(profile));
                for (boolean value : new boolean[]{false, true}) {
                    WeatherChangeEvent weather = new WeatherChangeEvent(context.world, value, WeatherChangeEvent.Cause.NATURAL);
                    ThunderChangeEvent thunder = new ThunderChangeEvent(context.world, value, ThunderChangeEvent.Cause.NATURAL);
                    context.service.onWeather(weather);
                    context.service.onThunder(thunder);
                    assertThat(weather.isCancelled()).isEqualTo(value != !policy.equals("CLEAR"));
                    assertThat(thunder.isCancelled()).isEqualTo(value != policy.equals("THUNDER"));
                }
            }
        }
    }

    @Test
    void deniesOnlyNaturalSpawnsForTheirCategory() {
        try (Context context = context()) {
            WorldProfile profile = profile("INHERIT");
            profile.setNaturalAnimals("DENY");
            profile.setNaturalMonsters("DENY");
            when(context.profiles.find("private")).thenReturn(Optional.of(profile));
            for (Class<? extends LivingEntity> type : List.of(Animals.class, Monster.class, Ghast.class,
                    Slime.class, MagmaCube.class, Phantom.class, Squid.class, Player.class)) {
                LivingEntity entity = mock(type);
                boolean monster = type != Animals.class && type != Squid.class && type != Player.class;
                when(entity.getSpawnCategory()).thenReturn(monster ? SpawnCategory.MONSTER : SpawnCategory.MISC);
                for (CreatureSpawnEvent.SpawnReason reason : CreatureSpawnEvent.SpawnReason.values()) {
                    CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
                    when(event.getEntity()).thenReturn(entity);
                    when(event.getLocation()).thenReturn(new Location(context.world, 0, 64, 0));
                    when(event.getSpawnReason()).thenReturn(reason);
                    context.service.onNaturalSpawn(event);
                    boolean natural = reason == CreatureSpawnEvent.SpawnReason.NATURAL;
                    if (natural && (monster || type == Animals.class)) {
                        verify(event).setCancelled(true);
                    } else {
                        verify(event, never()).setCancelled(anyBoolean());
                    }
                }
            }
        }
    }

    @Test
    void allowedNaturalSpawnsDoNotOverrideExistingCancellation() {
        try (Context context = context()) {
            WorldProfile profile = profile("INHERIT");
            profile.setNaturalAnimals("ALLOW");
            profile.setNaturalMonsters("ALLOW");
            when(context.profiles.find("private")).thenReturn(Optional.of(profile));
            CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);
            Monster monster = mock(Monster.class);
            when(monster.getSpawnCategory()).thenReturn(SpawnCategory.MONSTER);
            when(event.getEntity()).thenReturn(monster);
            when(event.getLocation()).thenReturn(new Location(context.world, 0, 64, 0));
            when(event.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.NATURAL);
            when(event.isCancelled()).thenReturn(true);
            context.service.onNaturalSpawn(event);
            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Test
    void ruleSnapshotRetainsOnlyExplicitSupportedValuesAndCopiesItsInput() {
        Map<String, String> available = new LinkedHashMap<>();
        available.put("minecraft:advance_time", "true");
        WorldPolicyService.GameRuleSnapshot snapshot = new WorldPolicyService.GameRuleSnapshot(true, available);
        available.put("minecraft:max_minecart_speed", "8");
        assertThat(snapshot.loaded()).isTrue();
        assertThat(snapshot.values()).containsExactlyEntriesOf(Map.of("minecraft:advance_time", "true"));
    }

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
    void joinAcceptsGrantedPermissionBeforeApplyingPlayerPolicies() {
        try (Context context = context()) {
            when(context.player.hasPermission("rift.private")).thenReturn(true);
            context.service.onJoin(join(context.player));
            assertThat(context.entity).hasSize(1);

            context.entity.removeFirst().run();

            verify(context.player, never()).teleportAsync(any());
            verify(context.player, never()).kickPlayer(anyString());
            verify(context.player, never()).sendPlainMessage(anyString());
        }
    }

    @Test
    void restrictedJoinWithoutFallbackDisconnectsWithExpandedDenial() {
        try (Context context = context()) {
            context.service.onJoin(join(context.player));

            verify(context.player).kickPlayer("Denied private: rift.private");
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
            verify(context.player).kickPlayer("Denied private: rift.private");
        }
    }

    @Test
    void failedEvacuationDoesNotDisconnectAfterPermissionWasGranted() {
        try (Context context = context()) {
            context.addFallback();
            CompletableFuture<Boolean> teleport = new CompletableFuture<>();
            when(context.player.teleportAsync(any())).thenReturn(teleport);
            context.service.onJoin(join(context.player));
            when(context.player.hasPermission("rift.private")).thenReturn(true);

            teleport.complete(false);
            context.drainEntityTasks();

            verify(context.player, never()).kickPlayer(anyString());
        }
    }

    @Test
    void sameWorldRespawnRechecksAccessAfterRespawn() {
        try (Context context = context()) {
            PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
            when(event.getPlayer()).thenReturn(context.player);

            context.service.afterRespawn(event);
            context.drainEntityTasks();

            verify(context.player).kickPlayer("Denied private: rift.private");
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
            verify(context.player).kickPlayer("Denied private: rift.private");
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
            if (invocation.getMethod().getName().equals("isOwnedByCurrentRegion")) {
                return true;
            }
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
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));
            when(world.getWorldBorder()).thenReturn(mock(WorldBorder.class));
            when(world.getPlayers()).thenReturn(List.of());
            when(player.getWorld()).thenReturn(world);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(profiles.find("private")).thenReturn(Optional.of(profile("INHERIT")));
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            bukkit.when(() -> Bukkit.getWorld(RiftWorldIdentity.key("private"))).thenReturn(world);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            Server server = mock(Server.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
            service = new WorldPolicyService(plugin, language, new WorldNamePolicy(directory), profiles);
            service.start();
        }

        private void addFallback() {
            World fallback = mock(World.class);
            when(fallback.getKey()).thenReturn(RiftWorldIdentity.key("lobby"));
            when(fallback.getUID()).thenReturn(UUID.randomUUID());
            when(fallback.getSpawnLocation()).thenReturn(new Location(fallback, 0.0D, 64.0D, 0.0D));
            when(profiles.find("lobby")).thenReturn(Optional.empty());
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world, fallback));
            service.start();
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
