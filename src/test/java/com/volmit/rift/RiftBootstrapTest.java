package com.volmit.rift;

import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

final class RiftBootstrapTest {
    @Test
    void unsupportedServerIsDisabledBeforeLibrariesOrPluginStateAreInitialized() {
        Rift plugin = mock(Rift.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        Logger logger = mock(Logger.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(server.getMinecraftVersion()).thenReturn("26.4");
        when(server.getPluginManager()).thenReturn(manager);
        doCallRealMethod().when(plugin).onLoad();
        doCallRealMethod().when(plugin).onEnable();
        doCallRealMethod().when(plugin).onDisable();

        try (MockedConstruction<SpigotApplicationBuilder> builders = mockConstruction(SpigotApplicationBuilder.class)) {
            plugin.onLoad();
            plugin.onEnable();
            plugin.onDisable();

            assertThat(builders.constructed()).isEmpty();
            verify(logger).severe(RiftCompatibility.rejectionMessage("26.4", Runtime.version().feature()));
            verify(manager).disablePlugin(plugin);
            verify(server).getMinecraftVersion();
            verify(server).getPluginManager();
            verify(plugin, never()).getDataFolder();
            verifyNoMoreInteractions(server, manager, logger);
        }
    }

    @Test
    void enableWithoutSuccessfulBootstrapDoesNotInitializePluginState() {
        Rift plugin = mock(Rift.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        doCallRealMethod().when(plugin).onEnable();

        plugin.onEnable();

        verify(manager).disablePlugin(plugin);
        verify(server).getPluginManager();
        verify(plugin, never()).getDataFolder();
        verifyNoMoreInteractions(server, manager);
    }
}
