package com.volmit.rift.world;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

final class DeleteConfirmationServiceTest {
    @Test
    void requiresMatchingSenderAndWorldBeforeExpiry() {
        MutableClock clock = new MutableClock();
        DeleteConfirmationService confirmations = new DeleteConfirmationService(clock);

        assertThat(confirmations.confirm("Alice", "Sky", 30)).isFalse();
        assertThat(confirmations.confirm("Bob", "Sky", 30)).isFalse();
        assertThat(confirmations.confirm("Alice", "Other", 30)).isFalse();
        assertThat(confirmations.confirm("alice", "sky", 30)).isTrue();
        assertThat(confirmations.confirm("alice", "sky", 30)).isFalse();

        clock.advanceMillis(30_001L);
        assertThat(confirmations.confirm("alice", "sky", 30)).isFalse();
    }

    private static final class MutableClock extends Clock {
        private long millis;

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }

        private void advanceMillis(long duration) {
            millis += duration;
        }
    }
}
