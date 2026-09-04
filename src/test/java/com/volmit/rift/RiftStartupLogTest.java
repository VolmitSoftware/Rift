package com.volmit.rift;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class RiftStartupLogTest {
    @Test
    void reportsStartupDurationSchedulerAndManagedWorldCount() {
        assertThat(Rift.readyMessage(143L, "Bukkit main-thread", 4))
                .isEqualTo("Rift ready in 143 ms with Bukkit main-thread scheduling and 4 managed worlds.");
        assertThat(Rift.readyMessage(7L, "Folia region", 1))
                .isEqualTo("Rift ready in 7 ms with Folia region scheduling and 1 managed world.");
    }
}
