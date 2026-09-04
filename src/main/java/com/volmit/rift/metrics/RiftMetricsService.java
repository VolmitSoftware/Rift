package com.volmit.rift.metrics;

import com.volmit.rift.Rift;
import com.volmit.rift.metrics.bstats.Metrics;

import java.util.Objects;
import java.util.logging.Level;

public final class RiftMetricsService implements AutoCloseable {
    private static final int PLUGIN_ID = 33701;

    private final Rift plugin;
    private Metrics metrics;
    private boolean closed;

    public RiftMetricsService(Rift plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public synchronized void install(boolean enabled) {
        if (closed) {
            return;
        }
        if (enabled == (metrics != null)) {
            return;
        }
        shutdownActive();
        if (!enabled) {
            return;
        }
        try {
            metrics = new Metrics(plugin, PLUGIN_ID);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to start Rift bStats metrics", exception);
        }
    }

    public synchronized boolean isRunning() {
        return metrics != null;
    }

    @Override
    public synchronized void close() {
        closed = true;
        shutdownActive();
    }

    private void shutdownActive() {
        Metrics active = metrics;
        metrics = null;
        if (active == null) {
            return;
        }
        try {
            active.shutdown();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to stop Rift bStats metrics", exception);
        }
    }
}
