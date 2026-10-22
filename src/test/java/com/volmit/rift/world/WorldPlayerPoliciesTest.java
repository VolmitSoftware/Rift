package com.volmit.rift.world;

import com.volmit.rift.storage.WorldProfile;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class WorldPlayerPoliciesTest {
    @Test
    void inheritDoesNotChangePlayerState() {
        Context context = new Context();
        context.policies.apply(context.player, context.profile, true);
        verify(context.player, never()).setGameMode(any());
        verify(context.player, never()).setAllowFlight(anyBoolean());
    }

    @Test
    void unrelatedProfileApplyDoesNotFightExternalGameModeOrFlightChanges() {
        Context context = new Context();
        context.profile.setGameMode("ADVENTURE");
        context.profile.setFlight("ALLOW");
        context.policies.apply(context.player, context.profile, true);
        context.mode.set(GameMode.SURVIVAL);
        context.allow.set(false);
        context.policies.apply(context.player, context.profile, false);
        assertThat(context.mode.get()).isEqualTo(GameMode.SURVIVAL);
        assertThat(context.allow.get()).isFalse();
    }

    @Test
    void reentryAndRespawnApplyConfiguredGameModeAgain() {
        Context context = new Context();
        context.profile.setGameMode("ADVENTURE");
        context.policies.apply(context.player, context.profile, true);
        context.mode.set(GameMode.SURVIVAL);
        context.policies.apply(context.player, context.profile, true);
        assertThat(context.mode.get()).isEqualTo(GameMode.ADVENTURE);
    }

    @Test
    void changedGameModePolicyAppliesWithoutReentry() {
        Context context = new Context();
        context.policies.apply(context.player, context.profile, true);
        context.profile.setGameMode("ADVENTURE");
        context.policies.apply(context.player, context.profile, false);
        assertThat(context.mode.get()).isEqualTo(GameMode.ADVENTURE);
        context.profile.setGameMode("INHERIT");
        context.policies.apply(context.player, context.profile, false);
        assertThat(context.mode.get()).isEqualTo(GameMode.ADVENTURE);
    }

    @Test
    void releasingFlightDoesNotRemoveNewCreativeFlight() {
        Context context = new Context();
        context.profile.setFlight("ALLOW");
        context.policies.apply(context.player, context.profile, true);
        context.mode.set(GameMode.CREATIVE);
        context.policies.release(context.player);
        assertThat(context.allow.get()).isTrue();
        assertThat(context.policies.hasFlight(context.player)).isFalse();
    }

    @Test
    void releasingGrantedFlightStopsFlightAndRestoresPermission() {
        Context context = new Context();
        context.profile.setFlight("ALLOW");
        context.policies.apply(context.player, context.profile, true);
        context.flying.set(true);
        context.policies.release(context.player);
        assertThat(context.allow.get()).isFalse();
        assertThat(context.flying.get()).isFalse();
        assertThat(context.policies.hasFlight(context.player)).isFalse();
    }

    @Test
    void denyReleaseRestoresPermissionWithoutStartingFlight() {
        Context context = new Context();
        context.allow.set(true);
        context.flying.set(true);
        context.profile.setFlight("DENY");
        context.policies.apply(context.player, context.profile, true);
        assertThat(context.allow.get()).isFalse();
        assertThat(context.flying.get()).isFalse();
        context.profile.setFlight("INHERIT");
        context.policies.apply(context.player, context.profile, false);
        assertThat(context.allow.get()).isTrue();
        assertThat(context.flying.get()).isFalse();
    }

    @Test
    void releasePreservesAnExternalPermissionChange() {
        Context context = new Context();
        context.profile.setFlight("ALLOW");
        context.policies.apply(context.player, context.profile, true);
        context.allow.set(false);
        context.policies.release(context.player);
        verify(context.player, never()).setAllowFlight(false);
    }

    @Test
    void denyPreservesCreativeAndSpectatorNativeFlight() {
        for (GameMode mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
            Context context = new Context();
            context.mode.set(mode);
            context.allow.set(true);
            context.flying.set(true);
            context.profile.setFlight("DENY");
            context.policies.apply(context.player, context.profile, true);
            assertThat(context.allow.get()).isTrue();
            assertThat(context.flying.get()).isTrue();
            verify(context.player, never()).setAllowFlight(anyBoolean());
        }
    }

    @Test
    void leavingToUnmanagedWorldReleasesFlightButDoesNotRestoreGameMode() {
        Context context = new Context();
        context.profile.setGameMode("ADVENTURE");
        context.profile.setFlight("ALLOW");
        context.policies.apply(context.player, context.profile, true);
        context.policies.apply(context.player, null, true);
        assertThat(context.mode.get()).isEqualTo(GameMode.ADVENTURE);
        assertThat(context.allow.get()).isFalse();
    }

    private static final class Context {
        private final Player player = mock(Player.class);
        private final WorldProfile profile = new WorldProfile();
        private final WorldPlayerPolicies policies = new WorldPlayerPolicies();
        private final AtomicBoolean allow = new AtomicBoolean();
        private final AtomicBoolean flying = new AtomicBoolean();
        private final AtomicReference<GameMode> mode = new AtomicReference<>(GameMode.SURVIVAL);

        private Context() {
            World world = mock(World.class);
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getWorld()).thenReturn(world);
            when(player.getAllowFlight()).thenAnswer(invocation -> allow.get());
            when(player.isFlying()).thenAnswer(invocation -> flying.get());
            when(player.getGameMode()).thenAnswer(invocation -> mode.get());
            doAnswer(invocation -> {
                allow.set(invocation.getArgument(0));
                return null;
            }).when(player).setAllowFlight(anyBoolean());
            doAnswer(invocation -> {
                flying.set(invocation.getArgument(0));
                return null;
            }).when(player).setFlying(anyBoolean());
            doAnswer(invocation -> {
                mode.set(invocation.getArgument(0));
                return null;
            }).when(player).setGameMode(any());
        }
    }
}
