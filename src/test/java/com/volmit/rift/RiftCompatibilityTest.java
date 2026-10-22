package com.volmit.rift;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

final class RiftCompatibilityTest {
    @ParameterizedTest
    @ValueSource(strings = {"26.1", "26.1.1", "26.1.2", "26.2", "26.2.1", "26.3", "26.3.0", "26.3.99"})
    void acceptsSupportedReleaseLinesAndTheirPatches(String minecraftVersion) {
        assertThat(RiftCompatibility.supports(minecraftVersion, 25)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"1.20.1", "1.21.11", "25.4", "26", "26.0", "26.4", "26.4.1", "27.1",
            "26.3-pre1", "26.3-rc1", "26.3-snapshot", "26w01a", "26.03", "26.3.01", "26.3.1.1",
            "26.3.1-extra", " 26.3", "26.3 ", "", "unknown"})
    void rejectsUnsupportedOrNonReleaseVersions(String minecraftVersion) {
        assertThat(RiftCompatibility.supports(minecraftVersion, 25)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {25, 26, 27})
    void acceptsJava25AndNewer(int javaVersion) {
        assertThat(RiftCompatibility.supports("26.3", javaVersion)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {17, 21, 24})
    void rejectsJavaOlderThan25(int javaVersion) {
        assertThat(RiftCompatibility.supports("26.3", javaVersion)).isFalse();
    }

    @Test
    void refusalNamesTheSupportedAndDetectedVersions() {
        assertThat(RiftCompatibility.rejectionMessage("26.4", 26))
                .isEqualTo("Rift requires Minecraft 26.1 - 26.3 release versions and Java 25 or newer; detected Minecraft 26.4 and Java 26. Rift will remain disabled.");
    }
}
