package com.volmit.rift.placeholder;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import com.volmit.rift.Rift;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class RiftPlaceholderServiceTest {
    @Test
    void missingOptionalPluginDoesNotCreateExpansionOrCollectWorlds() {
        Rift plugin = plugin(false);
        RiftPlaceholderService service = new RiftPlaceholderService(plugin);

        try (MockedConstruction<RiftPlaceholderExpansion> expansions = mockConstruction(RiftPlaceholderExpansion.class)) {
            service.start();

            assertThat(service.isRegistered()).isFalse();
            assertThat(expansions.constructed()).isEmpty();
            verify(plugin, never()).worldInventory();
            verify(plugin, never()).lifecycle();
        }
    }

    @Test
    void dependencyDisableDetachesAndReenableRegistersAgain() {
        Rift plugin = plugin(true);
        when(plugin.worldInventory().snapshots()).thenReturn(List.of());
        when(plugin.getServer().getOnlinePlayers()).thenReturn(List.of());
        SchedulerUtils.TaskHandle task = mock(SchedulerUtils.TaskHandle.class);
        Plugin dependency = mock(Plugin.class);
        when(dependency.getName()).thenReturn("PlaceholderAPI");
        PluginDisableEvent disabled = mock(PluginDisableEvent.class);
        when(disabled.getPlugin()).thenReturn(dependency);
        PluginEnableEvent enabled = mock(PluginEnableEvent.class);
        when(enabled.getPlugin()).thenReturn(dependency);
        RiftPlaceholderService service = new RiftPlaceholderService(plugin);

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
             MockedConstruction<RiftPlaceholderExpansion> expansions = mockConstruction(
                     RiftPlaceholderExpansion.class, (expansion, context) -> when(expansion.register()).thenReturn(true))) {
            scheduler.when(() -> FoliaScheduler.scheduleGlobal(eq(plugin), any(FoliaScheduler.DelayedTask.class)))
                    .thenReturn(task);
            service.start();
            assertThat(service.isRegistered()).isTrue();
            assertThat(expansions.constructed()).hasSize(1);

            service.onPluginDisable(disabled);
            assertThat(service.isRegistered()).isFalse();
            verify(expansions.constructed().getFirst()).unregister();
            verify(task).cancel();

            service.onPluginEnable(enabled);
            assertThat(service.isRegistered()).isTrue();
            assertThat(expansions.constructed()).hasSize(2);
            service.close();
            assertThat(service.isRegistered()).isFalse();
            verify(expansions.constructed().getLast()).unregister();
        }
    }

    @Test
    void identifierConflictDoesNotStartCollection() {
        Rift plugin = plugin(true);
        RiftPlaceholderService service = new RiftPlaceholderService(plugin);

        try (MockedConstruction<RiftPlaceholderExpansion> expansions = mockConstruction(RiftPlaceholderExpansion.class)) {
            service.start();

            assertThat(service.isRegistered()).isFalse();
            assertThat(expansions.constructed()).hasSize(1);
            verify(plugin, never()).worldInventory();
        }
    }

    private static Rift plugin(boolean dependencyEnabled) {
        Rift plugin = mock(Rift.class, RETURNS_DEEP_STUBS);
        when(plugin.getPluginMeta().getVersion()).thenReturn("test-version");
        when(plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")).thenReturn(dependencyEnabled);
        return plugin;
    }
}
