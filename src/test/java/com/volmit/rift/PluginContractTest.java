package com.volmit.rift;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

final class PluginContractTest {
    @Test
    void descriptorUsesOneSafeAliasAndGranularPermissions() throws IOException {
        Path project = Path.of(System.getProperty("rift.projectDir"));
        String descriptor = Files.readString(project.resolve("src/main/resources/plugin.yml"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertThat(descriptor).contains("aliases: [rft]");
        assertThat(descriptor).doesNotContain("aliases: [ri", "aliases: [rt");
        assertThat(descriptor).contains("rift.delete:", "rift.restore:", "rift.teleport.others:", "rift.debug:");
        assertThat(descriptor).contains("rift.debug: true");
        assertThat(descriptor).contains("children:\n      rift.command: true");
        assertThat(descriptor).contains("folia-supported: true");
        assertThat(descriptor).doesNotContain("rift.limit.bypass: true", "rift.travel.bypass: true");
        assertThat(descriptor).contains("rift.limit.bypass:\n    description: Bypasses world player limits; occupants still count toward the limit\n    default: false");
        assertThat(descriptor).contains("rift.travel.bypass:\n    description: Bypasses cross-world entry and departure restrictions\n    default: false");
    }

    @Test
    void shadedJarRequiresJava25AndRemainsNmsFree() throws IOException {
        Path jar = Path.of(System.getProperty("rift.runtimeJar"));
        assertThat(jar).isRegularFile();

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            List<? extends ZipEntry> riftClasses = zip.stream()
                    .filter(entry -> entry.getName().startsWith("com/volmit/rift/"))
                    .filter(entry -> !entry.getName().startsWith("com/volmit/rift/libs/"))
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .toList();
            assertThat(riftClasses).isNotEmpty();
            List<? extends ZipEntry> allClasses = zip.stream()
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .toList();
            assertThat(zip.getEntry("com/volmit/rift/metrics/bstats/Metrics.class")).isNotNull();
            assertThat(zip.getEntry("org/bstats/bukkit/Metrics.class")).isNull();
            assertThat(zip.getEntry("META-INF/LICENSE-bStats-Metrics.txt")).isNotNull();
            for (ZipEntry entry : allClasses) {
                byte[] bytes = zip.getInputStream(entry).readAllBytes();
                int majorVersion = ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
                assertThat(majorVersion).as(entry.getName()).isLessThanOrEqualTo(69);
            }
            for (ZipEntry entry : riftClasses) {
                byte[] bytes = zip.getInputStream(entry).readAllBytes();
                int majorVersion = ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
                String constantPoolText = new String(bytes, StandardCharsets.ISO_8859_1);
                assertThat(majorVersion).as(entry.getName()).isEqualTo(69);
                assertThat(constantPoolText).as(entry.getName())
                        .doesNotContain("net/minecraft", "org/bukkit/craftbukkit");
            }

            String descriptor = new String(
                    zip.getInputStream(zip.getEntry("plugin.yml")).readAllBytes(),
                    StandardCharsets.UTF_8
            );
            assertThat(descriptor).contains("version: '2.0.1-26.1-26.3'", "api-version: '26.1'");
        }
    }
}
