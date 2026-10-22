package com.volmit.rift;

import net.md_5.bungee.api.ChatColor;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class RiftSplashScreenTest {
    @Test
    void presentsTheCanonicalIdentityAndRuntimeFields() {
        String[] details = RiftSplashScreen.details(
                "2.0.1-26.1-26.3",
                "Paper 26.2",
                "25",
                "2026-09-03"
        );
        List<String> visible = Arrays.stream(details)
                .map(line -> line.replaceAll("§[0-9a-fk-or]", "").trim())
                .toList();

        assertThat(visible).containsExactly(
                "",
                "Rift, Extremely Simple & Reliable World Manager",
                "Version: 2.0.1-26.1-26.3",
                "By: VolmitSoftware (Arcane Arts) | VolmitSoftware.com",
                "Server: Paper 26.2 | MC Support: 26.1 - 26.3",
                "Java: 25 | Date: 2026-09-03"
        );
    }

    @Test
    void rendersFilledAndEdgeGlyphsWithSeparateBrandColors() {
        ChatColor fill = ChatColor.of("#6f2dbd");
        ChatColor edge = ChatColor.of("#d16ba5");

        assertThat(RiftSplashScreen.colorize(" ██╗ █", fill, edge))
                .isEqualTo(" " + fill + "██" + edge + "╗ " + fill + "█");
    }
}
