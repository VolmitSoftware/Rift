package com.volmit.rift.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class RiftConfigTest {
    @Test
    void enablesAnonymousMetricsByDefault() {
        assertThat(new RiftConfig().normalize().isBstatsEnabled()).isTrue();
    }

    @Test
    void enablesPublicDebugUploadsByDefault() {
        assertThat(new RiftConfig().normalize().isDebugUploadEnabled()).isTrue();
    }

    @Test
    void normalizesUnsafeOrExpensiveSettings() {
        RiftConfig config = new RiftConfig();
        config.setLanguage("  en-GB  ");
        config.setEvacuationWorld("  lobby  ");
        config.setHotReloadPollMillis(1L);
        config.setHotReloadCooldownMillis(Long.MAX_VALUE);
        config.setDeleteConfirmationSeconds(2);

        config.normalize();

        assertThat(config.getLanguage()).isEqualTo("en-GB");
        assertThat(config.getEvacuationWorld()).isEqualTo("lobby");
        assertThat(config.getHotReloadPollMillis()).isEqualTo(250L);
        assertThat(config.getHotReloadCooldownMillis()).isEqualTo(30_000L);
        assertThat(config.getDeleteConfirmationSeconds()).isEqualTo(10);
    }

    @Test
    void rejectsNonFiniteSoundValues() {
        RiftConfig config = new RiftConfig();
        config.setSoundVolume(Float.NaN);

        assertThatThrownBy(config::normalize)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("soundVolume");
    }

    @Test
    void rejectsUnsafeLanguageFileNames() {
        RiftConfig config = new RiftConfig();

        for (String invalid : new String[]{"../fr_FR", "folder/fr_FR", "fr_FR.yml", "fr FR"}) {
            config.setLanguage(invalid);
            assertThatThrownBy(config::normalize)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("language");
        }
    }
}
