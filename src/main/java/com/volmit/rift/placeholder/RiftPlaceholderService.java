package com.volmit.rift.placeholder;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import com.volmit.rift.Rift;
import com.volmit.rift.world.RiftWorldIdentity;
import com.volmit.rift.world.WorldSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class RiftPlaceholderService implements Listener, AutoCloseable {
    private final Rift plugin;
    private final RiftPlaceholderValues values;
    private final AtomicBoolean active = new AtomicBoolean();
    private Runnable unregister;
    private SchedulerUtils.TaskHandle refreshTask;

    public RiftPlaceholderService(Rift plugin) {
        this.plugin = plugin;
        values = new RiftPlaceholderValues(plugin.getPluginMeta().getVersion());
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        attach();
    }

    @Override
    public void close() {
        detach();
        HandlerList.unregisterAll(this);
    }

    public boolean isRegistered() {
        return active.get();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("PlaceholderAPI")) {
            attach();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("PlaceholderAPI")) {
            detach();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        cachePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        cachePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        values.removePlayer(event.getPlayer().getUniqueId());
    }

    private void attach() {
        if (active.get() || !plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            RiftPlaceholderExpansion expansion = new RiftPlaceholderExpansion(values, plugin.getPluginMeta().getVersion());
            if (!expansion.register()) {
                plugin.getLogger().warning("Unable to register Rift placeholders; the rift identifier may already be registered.");
                return;
            }
            unregister = expansion::unregister;
            active.set(true);
            refresh();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                FoliaScheduler.scheduleEntity(plugin, player, new FoliaScheduler.DelayedTask(
                        1L, () -> cachePlayer(player), () -> values.removePlayer(player.getUniqueId())));
            }
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to enable Rift PlaceholderAPI support", exception);
            detach();
        }
    }

    private void detach() {
        active.set(false);
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (unregister != null) {
            try {
                unregister.run();
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().log(Level.WARNING, "Unable to unregister Rift placeholders", exception);
            } finally {
                unregister = null;
            }
        }
        values.clear();
    }

    private void refresh() {
        if (!active.get()) {
            return;
        }
        List<WorldSnapshot> worlds = plugin.worldInventory().snapshots();
        Set<String> busy = new HashSet<>();
        for (WorldSnapshot world : worlds) {
            if (plugin.lifecycle().isBusy(world.name())) {
                busy.add(world.name().toLowerCase(Locale.ROOT));
            }
        }
        values.install(worlds, busy, plugin.lifecycle().activeOperationCount());
        refreshTask = FoliaScheduler.scheduleGlobal(plugin, new FoliaScheduler.DelayedTask(
                20L, this::refresh, () -> { }));
    }

    private void cachePlayer(Player player) {
        if (active.get()) {
            values.playerWorld(player.getUniqueId(), RiftWorldIdentity.logicalName(player.getWorld(), plugin.profiles()));
        }
    }
}
