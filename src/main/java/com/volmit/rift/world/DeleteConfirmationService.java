package com.volmit.rift.world;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class DeleteConfirmationService {
    private final Clock clock;
    private final Map<String, Confirmation> confirmations = new ConcurrentHashMap<>();

    public DeleteConfirmationService() {
        this(Clock.systemUTC());
    }

    DeleteConfirmationService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean confirm(String senderIdentity, String worldName, int validSeconds) {
        String key = key(senderIdentity, worldName);
        long now = clock.millis();
        Confirmation existing = confirmations.remove(key);
        if (existing != null && existing.expiresAtMillis() >= now) {
            return true;
        }
        long durationMillis = Math.max(1L, validSeconds) * 1_000L;
        confirmations.put(key, new Confirmation(now + durationMillis));
        confirmations.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
        return false;
    }

    public void clear() {
        confirmations.clear();
    }

    private static String key(String senderIdentity, String worldName) {
        String sender = Objects.requireNonNullElse(senderIdentity, "unknown").toLowerCase(Locale.ROOT);
        String world = Objects.requireNonNullElse(worldName, "").toLowerCase(Locale.ROOT);
        return sender + '\u0000' + world;
    }

    private record Confirmation(long expiresAtMillis) {
    }
}
