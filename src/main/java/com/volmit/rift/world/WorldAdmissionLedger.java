package com.volmit.rift.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class WorldAdmissionLedger {
    private final Map<UUID, UUID> occupants = new HashMap<>();
    private final Map<UUID, Reservation> pending = new HashMap<>();
    private final Map<UUID, Integer> occupiedCounts = new HashMap<>();
    private final Map<UUID, Integer> pendingCounts = new HashMap<>();

    synchronized boolean reserve(UUID player, UUID world, int limit, boolean bypass) {
        if (world.equals(occupants.get(player))) {
            return true;
        }
        Reservation previous = pending.get(player);
        if (previous != null && world.equals(previous.world())) {
            pending.put(player, new Reservation(world));
            return true;
        }
        if (!bypass && limit > 0 && occupiedCounts.getOrDefault(world, 0) + pendingCounts.getOrDefault(world, 0) >= limit) {
            return false;
        }
        cancel(player);
        pending.put(player, new Reservation(world));
        pendingCounts.merge(world, 1, Integer::sum);
        return true;
    }

    synchronized boolean available(UUID player, UUID world, int limit, boolean bypass) {
        Reservation reservation = pending.get(player);
        return bypass || limit == 0 || world.equals(occupants.get(player))
                || (reservation != null && world.equals(reservation.world()))
                || occupiedCounts.getOrDefault(world, 0) + pendingCounts.getOrDefault(world, 0) < limit;
    }

    synchronized void arrive(UUID player, UUID world) {
        Reservation reservation = pending.get(player);
        if (reservation != null && world.equals(reservation.world())) {
            cancel(player);
        }
        UUID previous = occupants.put(player, world);
        if (world.equals(previous)) {
            return;
        }
        decrement(occupiedCounts, previous);
        occupiedCounts.merge(world, 1, Integer::sum);
    }

    synchronized void cancel(UUID player) {
        Reservation reservation = pending.remove(player);
        if (reservation != null) {
            decrement(pendingCounts, reservation.world());
        }
    }

    synchronized void cancel(UUID player, Reservation reservation) {
        if (reservation != null && pending.get(player) == reservation) {
            cancel(player);
        }
    }

    synchronized Reservation reservation(UUID player) {
        return pending.get(player);
    }

    synchronized void reconcile(UUID player, UUID actualWorld, Reservation reservation) {
        if (pending.get(player) == reservation && actualWorld.equals(reservation.world())) {
            arrive(player, actualWorld);
        }
    }

    synchronized void leave(UUID player) {
        cancel(player);
        decrement(occupiedCounts, occupants.remove(player));
    }

    synchronized void clear() {
        occupants.clear();
        pending.clear();
        occupiedCounts.clear();
        pendingCounts.clear();
    }

    synchronized boolean contains(UUID player) {
        return occupants.containsKey(player);
    }

    private void decrement(Map<UUID, Integer> counts, UUID world) {
        if (world != null) {
            counts.computeIfPresent(world, (key, count) -> count <= 1 ? null : count - 1);
        }
    }

    static final class Reservation {
        private final UUID world;

        private Reservation(UUID world) {
            this.world = world;
        }

        UUID world() {
            return world;
        }
    }
}
