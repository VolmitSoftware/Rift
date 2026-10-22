package com.volmit.rift.world;

import com.volmit.rift.storage.WorldProfile;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class WorldPlayerPolicies {
    private final Map<UUID, AppliedPolicy> applied = new ConcurrentHashMap<>();
    private final Map<UUID, FlightLease> flight = new ConcurrentHashMap<>();

    void apply(Player player, WorldProfile profile, boolean entering) {
        UUID id = player.getUniqueId();
        if (profile == null) {
            release(player);
            return;
        }
        AppliedPolicy next = new AppliedPolicy(player.getWorld().getUID(), profile.getGameMode(), profile.getFlight());
        AppliedPolicy previous = applied.put(id, next);
        boolean changedWorld = previous == null || !previous.world().equals(next.world());
        if (changedWorld) {
            releaseFlight(player);
        }
        if (!next.gameMode().equals("INHERIT")
                && (entering || changedWorld || !next.gameMode().equals(previous.gameMode()))) {
            GameMode desired = GameMode.valueOf(next.gameMode());
            if (player.getGameMode() != desired) {
                player.setGameMode(desired);
            }
        }
        if (entering || changedWorld || !next.flight().equals(previous.flight()) || !next.gameMode().equals(previous.gameMode())) {
            applyFlight(player, next.flight());
        }
    }

    void gameModeChanged(Player player, WorldProfile profile) {
        releaseFlight(player);
        if (profile != null) {
            applyFlight(player, profile.getFlight());
        }
    }

    void release(Player player) {
        applied.remove(player.getUniqueId());
        releaseFlight(player);
    }

    boolean hasFlight(Player player) {
        return flight.containsKey(player.getUniqueId());
    }

    void clear() {
        applied.clear();
        flight.clear();
    }

    private void applyFlight(Player player, String policy) {
        releaseFlight(player);
        GameMode mode = player.getGameMode();
        if (policy.equals("INHERIT") || nativeFlight(mode)) {
            return;
        }
        boolean desired = policy.equals("ALLOW");
        boolean previous = player.getAllowFlight();
        if (previous == desired) {
            return;
        }
        if (!desired && player.isFlying()) {
            player.setFlying(false);
        }
        player.setAllowFlight(desired);
        flight.put(player.getUniqueId(), new FlightLease(previous, desired, mode));
    }

    private void releaseFlight(Player player) {
        FlightLease lease = flight.remove(player.getUniqueId());
        if (lease == null || player.getAllowFlight() != lease.applied() || player.getGameMode() != lease.mode()
                || nativeFlight(player.getGameMode())) {
            return;
        }
        if (!lease.previous() && player.isFlying()) {
            player.setFlying(false);
        }
        player.setAllowFlight(lease.previous());
    }

    private static boolean nativeFlight(GameMode mode) {
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    private record AppliedPolicy(UUID world, String gameMode, String flight) {
    }

    private record FlightLease(boolean previous, boolean applied, GameMode mode) {
    }
}
