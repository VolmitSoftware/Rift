package com.volmit.rift.world;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class WorldOperationLocksTest {
    @Test
    void serializesWorldOperationsWithoutCaseSensitivity() {
        WorldOperationLocks locks = new WorldOperationLocks();

        assertThat(locks.acquire("Sky")).isTrue();
        assertThat(locks.acquire("sky")).isFalse();
        assertThat(locks.isActive("SKY")).isTrue();

        locks.release("sKy");

        assertThat(locks.acquire("sky")).isTrue();
    }
}
