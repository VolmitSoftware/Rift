package com.volmit.rift.metrics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

final class MetricsSourceIntegrityTest {
    private static final String RIFT_PACKAGE = "package com.volmit.rift.metrics.bstats;";
    private static final String UPSTREAM_PACKAGE = "package org.bstats.bukkit;";
    private static final String UPSTREAM_SHA256 = "c19484d1de92ae52ea5f80e949a7f5885e4e6819d0e6a57e920ac864ecd5f57a";

    @Test
    void differsFromTheOfficialSingleFileOnlyByPackage() throws IOException, NoSuchAlgorithmException {
        Path project = Path.of(System.getProperty("rift.projectDir"));
        Path source = project.resolve("src/main/java/com/volmit/rift/metrics/bstats/Metrics.java");
        String current = Files.readString(source, StandardCharsets.UTF_8);

        assertThat(current).containsOnlyOnce(RIFT_PACKAGE).doesNotContain(UPSTREAM_PACKAGE);
        String normalized = current.replace(RIFT_PACKAGE, UPSTREAM_PACKAGE);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));

        assertThat(HexFormat.of().formatHex(digest)).isEqualTo(UPSTREAM_SHA256);
    }
}
