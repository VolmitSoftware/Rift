package com.volmit.rift.world;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class TeleportService {
    private final Rift plugin;

    public TeleportService(Rift plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void teleport(Player player, Location destination, Consumer<Boolean> completion) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(destination, "destination");
        Consumer<Boolean> callback = completion == null ? ignored -> { } : completion;
        Method asyncMethod = resolveAsyncTeleport(player);
        if (asyncMethod != null) {
            invokeAsync(player, destination, asyncMethod, callback);
            return;
        }
        boolean scheduled = FoliaScheduler.runEntity(plugin, player, () -> callback.accept(player.teleport(destination)));
        if (!scheduled) {
            callback.accept(false);
        }
    }

    private void invokeAsync(Player player, Location destination, Method method, Consumer<Boolean> completion) {
        try {
            Object result = method.invoke(player, destination);
            if (!(result instanceof CompletableFuture<?> future)) {
                completion.accept(false);
                return;
            }
            future.whenComplete((value, failure) -> {
                boolean success = failure == null && Boolean.TRUE.equals(value);
                if (failure != null) {
                    plugin.getLogger().log(Level.WARNING, "Asynchronous teleport failed for " + player.getName(), failure);
                }
                FoliaScheduler.runEntity(
                        plugin,
                        player,
                        () -> completion.accept(success),
                        0L,
                        () -> completion.accept(false)
                );
            });
        } catch (IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to invoke asynchronous teleport for " + player.getName(), exception);
            completion.accept(false);
        }
    }

    private static Method resolveAsyncTeleport(Player player) {
        try {
            return player.getClass().getMethod("teleportAsync", Location.class);
        } catch (NoSuchMethodException | SecurityException exception) {
            return null;
        }
    }
}
