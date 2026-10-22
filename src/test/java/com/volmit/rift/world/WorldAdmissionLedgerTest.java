package com.volmit.rift.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

final class WorldAdmissionLedgerTest {
    @Test
    void pendingArrivalsConsumeCapacityAndCancellationReleasesIt() {
        WorldAdmissionLedger ledger = new WorldAdmissionLedger();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        assertThat(ledger.reserve(first, world, 1, false)).isTrue();
        assertThat(ledger.reserve(second, world, 1, false)).isFalse();
        ledger.cancel(first);
        assertThat(ledger.reserve(second, world, 1, false)).isTrue();
        ledger.arrive(second, world);
        assertThat(ledger.reserve(first, world, 1, false)).isFalse();
        ledger.leave(second);
        assertThat(ledger.reserve(first, world, 1, false)).isTrue();
    }

    @Test
    void changingDestinationReleasesPreviousReservation() {
        WorldAdmissionLedger ledger = new WorldAdmissionLedger();
        UUID player = UUID.randomUUID();
        UUID firstWorld = UUID.randomUUID();
        UUID secondWorld = UUID.randomUUID();
        assertThat(ledger.reserve(player, firstWorld, 1, false)).isTrue();
        assertThat(ledger.reserve(player, secondWorld, 1, false)).isTrue();
        assertThat(ledger.reserve(UUID.randomUUID(), firstWorld, 1, false)).isTrue();
        ledger.arrive(player, secondWorld);
        assertThat(ledger.reserve(UUID.randomUUID(), secondWorld, 1, false)).isFalse();
    }

    @Test
    void loweredCapDoesNotEvictResidentsAndExplicitBypassCountsAsOccupancy() {
        WorldAdmissionLedger ledger = new WorldAdmissionLedger();
        UUID world = UUID.randomUUID();
        UUID resident = UUID.randomUUID();
        ledger.arrive(resident, world);
        ledger.arrive(UUID.randomUUID(), world);
        assertThat(ledger.reserve(resident, world, 1, false)).isTrue();
        assertThat(ledger.reserve(UUID.randomUUID(), world, 1, false)).isFalse();
        assertThat(ledger.reserve(UUID.randomUUID(), world, 1, true)).isTrue();
        assertThat(ledger.reserve(UUID.randomUUID(), world, 0, false)).isTrue();
    }

    @Test
    void concurrentReservationsCannotOverbook() {
        WorldAdmissionLedger ledger = new WorldAdmissionLedger();
        UUID world = UUID.randomUUID();
        AtomicInteger admitted = new AtomicInteger();
        List<CompletableFuture<Void>> requests = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            requests.add(CompletableFuture.runAsync(() -> {
                if (ledger.reserve(UUID.randomUUID(), world, 7, false)) {
                    admitted.incrementAndGet();
                }
            }));
        }
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();
        assertThat(admitted.get()).isEqualTo(7);
    }

    @Test
    void earlierArrivalAndCancellationDoNotClearNewerReservation() {
        WorldAdmissionLedger ledger = new WorldAdmissionLedger();
        UUID player = UUID.randomUUID();
        UUID firstWorld = UUID.randomUUID();
        UUID secondWorld = UUID.randomUUID();
        ledger.reserve(player, firstWorld, 1, false);
        WorldAdmissionLedger.Reservation previous = ledger.reservation(player);
        ledger.reserve(player, secondWorld, 1, false);
        ledger.arrive(player, firstWorld);
        ledger.cancel(player, previous);
        assertThat(ledger.reserve(UUID.randomUUID(), secondWorld, 1, false)).isFalse();
        ledger.leave(player);
        assertThat(ledger.reserve(UUID.randomUUID(), secondWorld, 1, false)).isTrue();
    }

    @Test
    void reconciliationRequiresTheCurrentAttemptAndDestination() {
        WorldAdmissionLedger ledger = new WorldAdmissionLedger();
        UUID player = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ledger.reserve(player, target, 1, false);
        WorldAdmissionLedger.Reservation first = ledger.reservation(player);
        ledger.reconcile(player, UUID.randomUUID(), first);
        assertThat(ledger.reservation(player)).isSameAs(first);
        ledger.reserve(player, target, 1, false);
        WorldAdmissionLedger.Reservation second = ledger.reservation(player);
        ledger.reconcile(player, target, first);
        ledger.cancel(player, first);
        assertThat(ledger.reservation(player)).isSameAs(second);
        ledger.reconcile(player, target, second);
        assertThat(ledger.reservation(player)).isNull();
        assertThat(ledger.reserve(UUID.randomUUID(), target, 1, false)).isFalse();
    }
}
