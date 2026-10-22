package com.volmit.rift.world;

import art.arcane.volmlib.util.plugin.ComponentText;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class WorldAdmissionPoliciesTest {
    @Test
    void crossWorldTravelChecksBothEndsAndOnlyExplicitBypass() {
        try (Context context = new Context()) {
            context.source.setTravelExit("DENY");
            when(context.player.hasPermission("rift.admin")).thenReturn(true);
            PlayerTeleportEvent denied = context.teleport(context.lobby);
            context.policies.teleport(denied);
            assertThat(denied.isCancelled()).isTrue();
            when(context.player.hasPermission("rift.travel.bypass")).thenReturn(true);
            context.target.setTravelEntry("DENY");
            PlayerTeleportEvent bypass = context.teleport(context.lobby);
            context.policies.teleport(bypass);
            assertThat(bypass.isCancelled()).isFalse();
            when(context.player.hasPermission("rift.travel.bypass")).thenReturn(false);
            context.source.setTravelExit("ALLOW");
            PlayerTeleportEvent entry = context.teleport(context.lobby);
            context.policies.teleport(entry);
            assertThat(entry.isCancelled()).isTrue();
        }
    }

    @Test
    void sameWorldTravelAndPreviouslyCancelledEventsAreNotChanged() {
        try (Context context = new Context()) {
            context.source.setTravelExit("DENY");
            context.source.setTravelEntry("DENY");
            PlayerTeleportEvent same = context.teleport(context.world);
            context.policies.teleport(same);
            assertThat(same.isCancelled()).isFalse();
            PlayerTeleportEvent cancelled = context.teleport(context.lobby);
            cancelled.setCancelled(true);
            context.policies.teleport(cancelled);
            assertThat(cancelled.isCancelled()).isTrue();
        }
    }

    @Test
    void pendingTeleportCountsAgainstLimitAndCancelledEventFreesSlot() {
        try (Context context = new Context()) {
            context.target.setPlayerLimit(1);
            PlayerTeleportEvent first = context.teleport(context.lobby);
            context.policies.teleport(first);
            assertThat(first.isCancelled()).isFalse();
            Player other = context.player(context.world);
            PlayerTeleportEvent second = new PlayerTeleportEvent(other, other.getLocation(), context.lobby.getSpawnLocation());
            context.policies.teleport(second);
            assertThat(second.isCancelled()).isTrue();
            first.setCancelled(true);
            context.policies.afterTeleport(first);
            second.setCancelled(false);
            context.policies.teleport(second);
            assertThat(second.isCancelled()).isFalse();
        }
    }

    @Test
    void portalSearchDoesNotHoldCapacityBeforeFinalTeleport() {
        try (Context context = new Context()) {
            context.target.setPlayerLimit(1);
            PlayerPortalEvent portal = new PlayerPortalEvent(context.player, context.player.getLocation(), context.lobby.getSpawnLocation());
            context.policies.onPortal(portal);
            assertThat(portal.isCancelled()).isFalse();
            Player other = context.player(context.world);
            PlayerTeleportEvent finalTeleport = new PlayerTeleportEvent(other, other.getLocation(), context.lobby.getSpawnLocation());
            context.policies.teleport(finalTeleport);
            assertThat(finalTeleport.isCancelled()).isFalse();
            PlayerTeleportEvent portalTeleport = context.teleport(context.lobby);
            context.policies.teleport(portalTeleport);
            assertThat(portalTeleport.isCancelled()).isTrue();
        }
    }

    @Test
    void delayedTransferStillProtectsCapacityAndOldCallbackCannotRemoveNewTicket() {
        try (Context context = new Context()) {
            context.target.setPlayerLimit(1);
            PlayerTeleportEvent first = context.teleport(context.lobby);
            context.policies.teleport(first);
            context.policies.afterTeleport(first);
            context.tasks.removeFirst().run();
            Player other = context.player(context.world);
            PlayerTeleportEvent blocked = new PlayerTeleportEvent(other, other.getLocation(), context.lobby.getSpawnLocation());
            context.policies.teleport(blocked);
            assertThat(blocked.isCancelled()).isTrue();
            PlayerTeleportEvent newer = context.teleport(context.lobby);
            context.policies.teleport(newer);
            first.setCancelled(true);
            context.policies.afterTeleport(first);
            blocked.setCancelled(false);
            context.policies.teleport(blocked);
            assertThat(blocked.isCancelled()).isTrue();
            newer.setCancelled(true);
            context.policies.afterTeleport(newer);
            blocked.setCancelled(false);
            context.policies.teleport(blocked);
            assertThat(blocked.isCancelled()).isFalse();
        }
    }

    @Test
    void travelAndLimitBypassesAreIndependentAndDoNotGrantAccess() {
        try (Context context = new Context()) {
            context.target.setPlayerLimit(1);
            Player resident = context.player(context.lobby);
            context.policies.changed(resident);
            when(context.player.hasPermission("rift.travel.bypass")).thenReturn(true);
            PlayerTeleportEvent full = context.teleport(context.lobby);
            context.policies.teleport(full);
            assertThat(full.isCancelled()).isTrue();
            when(context.player.hasPermission("rift.limit.bypass")).thenReturn(true);
            PlayerTeleportEvent limitBypass = context.teleport(context.lobby);
            context.policies.teleport(limitBypass);
            assertThat(limitBypass.isCancelled()).isFalse();
            context.target.setTravelEntry("DENY");
            when(context.player.hasPermission("rift.travel.bypass")).thenReturn(false);
            PlayerTeleportEvent travel = context.teleport(context.lobby);
            context.policies.teleport(travel);
            assertThat(travel.isCancelled()).isTrue();
            when(context.player.hasPermission("rift.travel.bypass")).thenReturn(true);
            context.target.setAccessPermission("private.world");
            PlayerTeleportEvent access = context.teleport(context.lobby);
            context.policies.teleport(access);
            assertThat(access.isCancelled()).isTrue();
        }
    }

    @Test
    void normalLoginDoesNotApplyCrossWorldTravelLocks() {
        try (Context context = new Context()) {
            context.source.setTravelEntry("DENY");
            context.source.setTravelExit("DENY");
            assertThat(context.policies.join(context.player)).isTrue();
            verify(context.player, never()).kickPlayer(anyString());
        }
    }

    @Test
    void firstJoinOverridesLoginAndReturningPlayersUseLogin() {
        try (Context context = new Context()) {
            context.source.setFirstJoinWorld("lobby");
            context.source.setLoginWorld("source");
            context.policies.refresh();
            AsyncPlayerSpawnLocationEvent first = context.spawn(true);
            context.policies.onSpawn(first);
            assertThat(first.getSpawnLocation().getWorld()).isSameAs(context.lobby);
            AsyncPlayerSpawnLocationEvent returning = context.spawn(false);
            context.policies.onSpawn(returning);
            assertThat(returning.getSpawnLocation().getWorld()).isSameAs(context.world);
        }
    }

    @Test
    void unavailableFirstJoinUsesLoadedLoginWithoutLoadingWorlds() {
        try (Context context = new Context()) {
            context.source.setFirstJoinWorld("missing");
            context.source.setLoginWorld("lobby");
            context.policies.refresh();
            AsyncPlayerSpawnLocationEvent event = context.spawn(true);
            context.policies.onSpawn(event);
            assertThat(event.getSpawnLocation().getWorld()).isSameAs(context.lobby);
            context.source.setLoginWorld("");
            context.policies.refresh();
            AsyncPlayerSpawnLocationEvent noTarget = context.spawn(true);
            context.policies.onSpawn(noTarget);
            assertThat(noTarget.getSpawnLocation().getWorld()).isSameAs(context.world);
        }
    }

    @Test
    void reconfigurationDoesNotApplyLoginPlacementAgain() {
        try (Context context = new Context()) {
            context.source.setLoginWorld("lobby");
            context.policies.refresh();
            assertThat(context.policies.join(context.player)).isTrue();
            AsyncPlayerSpawnLocationEvent event = context.spawn(false);
            context.policies.onSpawn(event);
            assertThat(event.getSpawnLocation().getWorld()).isSameAs(context.world);
        }
    }

    @Test
    void deniedConfiguredJoinFallsBackToOriginalPlacementIgnoringExitLock() {
        try (Context context = new Context()) {
            context.source.setFirstJoinWorld("lobby");
            context.target.setTravelEntry("DENY");
            context.target.setTravelExit("DENY");
            context.policies.refresh();
            AsyncPlayerSpawnLocationEvent event = context.spawn(true);
            context.policies.onSpawn(event);
            when(context.player.getWorld()).thenReturn(context.lobby);
            when(context.player.teleportAsync(any())).thenReturn(CompletableFuture.completedFuture(true));
            assertThat(context.policies.join(context.player)).isFalse();
            verify(context.player).teleportAsync(context.world.getSpawnLocation());
            PlayerTeleportEvent evacuation = new PlayerTeleportEvent(context.player, context.lobby.getSpawnLocation(),
                    context.world.getSpawnLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            context.policies.teleport(evacuation);
            assertThat(evacuation.isCancelled()).isFalse();
        }
    }

    @Test
    void failedEarlierEvacuationDoesNotCancelOrReconcileNewerTeleport() {
        try (Context context = new Context()) {
            context.source.setAccessPermission("private.world");
            context.source.setAccessDeniedMessage("Denied");
            context.target.setPlayerLimit(1);
            CompletableFuture<Boolean> evacuation = new CompletableFuture<>();
            when(context.player.teleportAsync(any())).thenReturn(evacuation);
            assertThat(context.policies.join(context.player)).isFalse();
            PlayerTeleportEvent initial = new PlayerTeleportEvent(context.player, context.player.getLocation(),
                    context.lobby.getSpawnLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            context.policies.teleport(initial);
            PlayerTeleportEvent newer = context.teleport(context.lobby);
            context.policies.teleport(newer);
            assertThat(newer.isCancelled()).isFalse();
            evacuation.complete(false);
            context.tasks.removeLast().run();
            verify(context.player, never()).kickPlayer(anyString());
            Player other = context.player(context.world);
            PlayerTeleportEvent blocked = new PlayerTeleportEvent(other, other.getLocation(), context.lobby.getSpawnLocation());
            context.policies.teleport(blocked);
            assertThat(blocked.isCancelled()).isTrue();
        }
    }

    @Test
    void joinChecksCapacityAndCannotStayWhenEveryWorldIsDenied() {
        try (Context context = new Context()) {
            context.source.setPlayerLimit(1);
            context.target.setTravelEntry("DENY");
            assertThat(context.policies.join(context.player)).isTrue();
            Player other = context.player(context.world);
            when(context.language.text(other, RiftMessages.POLICY_PLACEMENT_FAILED)).thenReturn(ComponentText.literal("No placement"));
            assertThat(context.policies.join(other)).isFalse();
            verify(other).kickPlayer("No placement");
            verify(other, never()).teleportAsync(any());
        }
    }

    @Test
    void respawnIgnoresSourceExitButRespectsTargetEntryAndCapacity() {
        try (Context context = new Context()) {
            context.source.setTravelExit("DENY");
            Location selected = context.policies.respawn(context.player, context.lobby.getSpawnLocation());
            assertThat(selected.getWorld()).isSameAs(context.lobby);
            context.target.setTravelEntry("DENY");
            selected = context.policies.respawn(context.player, context.lobby.getSpawnLocation());
            assertThat(selected.getWorld()).isSameAs(context.world);
        }
    }

    private static final class Context implements AutoCloseable {
        private final Rift plugin = mock(Rift.class);
        private final RiftLocalization language = mock(RiftLocalization.class);
        private final WorldProfileStore profiles = mock(WorldProfileStore.class);
        private final Map<String, WorldProfile> stored = new LinkedHashMap<>();
        private final World world = world("source");
        private final World lobby = world("lobby");
        private final WorldProfile source = profile("source");
        private final WorldProfile target = profile("lobby");
        private final Player player = player(world);
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        private final MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class, invocation -> {
            if (invocation.getMethod().getName().equals("isOwnedByCurrentRegion")) {
                return true;
            }
            for (Object argument : invocation.getArguments()) {
                if (argument instanceof Runnable task) {
                    tasks.addLast(task);
                    return true;
                }
            }
            return false;
        });
        private final WorldAdmissionPolicies policies;

        private Context() {
            when(plugin.getLogger()).thenReturn(Logger.getLogger(WorldAdmissionPoliciesTest.class.getName()));
            when(profiles.find(anyString())).thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world, lobby));
            policies = new WorldAdmissionPolicies(plugin, language, profiles, new TeleportService(plugin));
            policies.refresh();
        }

        private World world(String name) {
            World result = mock(World.class);
            when(result.getUID()).thenReturn(UUID.randomUUID());
            when(result.getKey()).thenReturn(RiftWorldIdentity.key(name));
            when(result.getSpawnLocation()).thenReturn(new Location(result, 0, 64, 0));
            return result;
        }

        private WorldProfile profile(String name) {
            WorldProfile result = new WorldProfile();
            result.setName(name);
            stored.put(name, result);
            return result;
        }

        private Player player(World current) {
            Player result = mock(Player.class);
            Location location = current.getSpawnLocation();
            when(result.getUniqueId()).thenReturn(UUID.randomUUID());
            when(result.getWorld()).thenReturn(current);
            when(result.getLocation()).thenReturn(location);
            return result;
        }

        private PlayerTeleportEvent teleport(World destination) {
            return new PlayerTeleportEvent(player, player.getLocation(), destination.getSpawnLocation());
        }

        private AsyncPlayerSpawnLocationEvent spawn(boolean first) {
            PlayerConfigurationConnection connection = mock(PlayerConfigurationConnection.class);
            PlayerProfile profile = mock(PlayerProfile.class);
            UUID id = player.getUniqueId();
            when(profile.getId()).thenReturn(id);
            when(connection.getProfile()).thenReturn(profile);
            return new AsyncPlayerSpawnLocationEvent(connection, world.getSpawnLocation(), first);
        }

        @Override
        public void close() {
            scheduler.close();
            bukkit.close();
        }
    }
}
