package com.volmit.rift.placeholder;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

final class RiftPlaceholderExpansionTest {
    @Test
    void expansionPersistsAndReadsOnlyPlayerIdentity() {
        RiftPlaceholderValues values = new RiftPlaceholderValues("version");
        RiftPlaceholderExpansion expansion = new RiftPlaceholderExpansion(values, "version");
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        assertThat(expansion.getIdentifier()).isEqualTo("rift");
        assertThat(expansion.getAuthor()).isEqualTo("VolmitSoftware");
        assertThat(expansion.getVersion()).isEqualTo("version");
        assertThat(expansion.persist()).isTrue();
        assertThat(expansion.onRequest(player, "world_name")).isEmpty();
        assertThat(expansion.onRequest(null, "worlds_loaded")).isEqualTo("0");
        verify(player).getUniqueId();
        verifyNoMoreInteractions(player);
    }
}
