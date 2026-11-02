package com.volmit.rift.world;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.config.RiftConfig;
import com.volmit.rift.config.RiftConfigManager;
import com.volmit.rift.feedback.RiftFeedbackService;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.storage.RiftPaths;
import com.volmit.rift.storage.TrashStore;
import com.volmit.rift.storage.WorldDirectoryResolver;
import com.volmit.rift.storage.WorldNamePolicy;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class WorldLifecycleProtectionTest {
    @Test
    void vanillaNetherCannotBeUnloaded() {
        try (Context context = new Context()) {
            World nether = context.loaded(NamespacedKey.minecraft("the_nether"));

            context.service.unload(context.sender, "the_nether", true);
            context.global.removeFirst().run();

            assertThat(context.failure("unload", "the_nether"))
                    .contains("overworld, nether, and end cannot be unloaded");
            context.bukkit.verify(() -> Bukkit.unloadWorld(eq(nether), anyBoolean()), never());
        }
    }

    @Test
    void managedVanillaEndCannotBeDeleted() {
        try (Context context = new Context()) {
            WorldProfile end = new WorldProfile();
            end.setName("the_end");
            end.setDirectory("world/dimensions/minecraft/the_end");
            when(context.profiles.find("the_end")).thenReturn(Optional.of(end));
            context.loaded(NamespacedKey.minecraft("the_end"));

            context.service.delete(context.sender, "the_end");
            context.service.delete(context.sender, "the_end");
            context.global.removeFirst().run();

            assertThat(context.failure("delete", "the_end"))
                    .contains("overworld, nether, and end are protected from delete");
        }
    }

    private static final class Context implements AutoCloseable {
        private final Rift plugin = mock(Rift.class);
        private final WorldProfileStore profiles = mock(WorldProfileStore.class);
        private final CommandSender sender = mock(CommandSender.class);
        private final Logger logger = mock(Logger.class);
        private final Deque<Runnable> global = new ArrayDeque<>();
        private final MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
        private final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        private final World overworld = mock(World.class);
        private final WorldLifecycleService service;

        private Context() {
            WorldNamePolicy names = mock(WorldNamePolicy.class);
            when(names.requireValid(any())).thenAnswer(invocation -> invocation.getArgument(0));
            PlatformCapabilities capabilities = mock(PlatformCapabilities.class);
            when(capabilities.supportsDynamicWorldLifecycle()).thenReturn(true);
            RiftConfigManager config = mock(RiftConfigManager.class);
            when(config.get()).thenReturn(new RiftConfig());
            when(plugin.getLogger()).thenReturn(logger);
            when(profiles.find(any())).thenReturn(Optional.empty());
            when(overworld.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(overworld));
            scheduler.when(() -> FoliaScheduler.runGlobal(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
                global.addLast(invocation.getArgument(1));
                return true;
            });
            scheduler.when(() -> FoliaScheduler.runAsync(eq(plugin), any(Runnable.class))).thenReturn(true);
            service = new WorldLifecycleService(plugin, config, mock(RiftLocalization.class),
                    mock(RiftPaths.class), names, mock(WorldDirectoryResolver.class), profiles, mock(TrashStore.class),
                    capabilities, mock(WorldInventory.class), mock(RiftFeedbackService.class),
                    mock(WorldPolicyService.class));
        }

        private World loaded(NamespacedKey key) {
            World world = mock(World.class);
            when(world.getKey()).thenReturn(key);
            bukkit.when(() -> Bukkit.getWorld(key)).thenReturn(world);
            return world;
        }

        private String failure(String operation, String world) {
            ArgumentCaptor<Throwable> failure = ArgumentCaptor.forClass(Throwable.class);
            verify(logger).log(eq(Level.SEVERE), eq("Rift " + operation + " failed for " + world), failure.capture());
            return failure.getValue().getMessage();
        }

        @Override
        public void close() {
            bukkit.close();
            scheduler.close();
        }
    }
}
