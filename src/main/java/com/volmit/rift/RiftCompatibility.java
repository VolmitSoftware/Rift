package com.volmit.rift;

import java.util.regex.Pattern;

final class RiftCompatibility {
    static final String MINECRAFT_RANGE = "26.1 - 26.3";
    static final int MINIMUM_JAVA_VERSION = 25;

    private static final Pattern MINECRAFT_RELEASE = Pattern.compile("26\\.[1-3](?:\\.(?:0|[1-9][0-9]*))?");

    private RiftCompatibility() {
    }

    static boolean supports(String minecraftVersion, int javaVersion) {
        return javaVersion >= MINIMUM_JAVA_VERSION
                && minecraftVersion != null
                && MINECRAFT_RELEASE.matcher(minecraftVersion).matches();
    }

    static String rejectionMessage(String minecraftVersion, int javaVersion) {
        return "Rift requires Minecraft " + MINECRAFT_RANGE + " release versions and Java " + MINIMUM_JAVA_VERSION
                + " or newer; detected Minecraft " + minecraftVersion + " and Java " + javaVersion
                + ". Rift will remain disabled.";
    }
}
