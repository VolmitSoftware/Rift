package com.volmit.rift;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

final class PluginMetadataTest {
    @Test
    void exposesIndependentLanguageAndDebugPermissionsWithoutARootCommandGate() throws Exception {
        String pluginYaml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String commandBlock = pluginYaml.substring(
                pluginYaml.indexOf("commands:"),
                pluginYaml.indexOf("permissions:")
        );

        assertThat(commandBlock).doesNotContain("permission:");
        assertThat(pluginYaml)
                .contains("rift.language.self:")
                .contains("rift.status:")
                .contains("rift.debug:")
                .contains("default: true");
        assertThat(pluginYaml).doesNotContain("rift.doctor:");
    }
}
