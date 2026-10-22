package com.volmit.rift;

import art.arcane.volmlib.util.plugin.ComponentLog;
import art.arcane.volmlib.util.plugin.SplashScreenSupport;
import net.md_5.bungee.api.ChatColor;

import java.util.logging.Level;

public final class RiftSplashScreen {
    private static final String[] ART = {
            "██████╗ ██╗███████╗████████╗",
            "██╔══██╗██║██╔════╝╚══██╔══╝",
            "██████╔╝██║█████╗     ██║   ",
            "██╔══██╗██║██╔══╝     ██║   ",
            "██║  ██║██║██║        ██║   ",
            "╚═╝  ╚═╝╚═╝╚═╝        ╚═╝   "
    };

    private RiftSplashScreen() {
    }

    public static void print(Rift plugin) {
        try {
            printSplash(plugin);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Rift splash screen failed to render", exception);
        }
    }

    private static void printSplash(Rift plugin) {
        ChatColor fill = ChatColor.of("#6f2dbd");
        ChatColor edge = ChatColor.of("#d16ba5");
        ChatColor meta = ChatColor.of("#d1ade8");
        ChatColor accent = edge;
        String[] column = details(
                plugin.getDescription().getVersion(),
                SplashScreenSupport.serverVersionWithoutMcSuffix(plugin.getServer().getVersion()),
                Integer.toString(SplashScreenSupport.javaMajorVersion()),
                SplashScreenSupport.startupDate()
        );
        for (int index = 1; index < column.length; index++) {
            column[index] = meta + "   " + column[index]
                    .replace("Rift", accent + "Rift" + meta)
                    .replace(": ", ": " + accent)
                    .replace(" | ", meta + " | ");
        }

        StringBuilder output = new StringBuilder("\n");
        for (int row = 0; row < ART.length; row++) {
            output.append(colorize(ART[row], fill, edge)).append(column[row]).append('\n');
        }
        ComponentLog.logLegacy(plugin, plugin.getLogger(), "", Level.INFO, output.toString(), null);
    }

    static String[] details(String version, String server, String javaVersion, String date) {
        return new String[]{
                "",
                "Rift, Extremely Simple & Reliable World Manager",
                "Version: " + version,
                "By: VolmitSoftware (Arcane Arts) | VolmitSoftware.com",
                "Server: " + server + " | MC Support: " + RiftCompatibility.MINECRAFT_RANGE,
                "Java: " + javaVersion + " | Date: " + date
        };
    }

    static String colorize(String row, ChatColor fill, ChatColor edge) {
        StringBuilder output = new StringBuilder(row.length() * 2);
        ChatColor activeColor = null;
        for (int index = 0; index < row.length(); index++) {
            char glyph = row.charAt(index);
            ChatColor glyphColor = glyph == ' ' ? activeColor : glyph == '█' ? fill : edge;
            if (glyphColor != null && !glyphColor.equals(activeColor)) {
                output.append(glyphColor);
                activeColor = glyphColor;
            }
            output.append(glyph);
        }
        return output.toString();
    }
}
