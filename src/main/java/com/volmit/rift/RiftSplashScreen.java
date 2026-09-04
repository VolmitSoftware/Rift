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
        ChatColor fill = ChatColor.of("#35135f");
        ChatColor edge = ChatColor.of("#6f35c5");
        ChatColor meta = ChatColor.of("#bba8cf");
        ChatColor accent = ChatColor.of("#c778ff");
        String[] column = details(
                plugin.getDescription().getVersion(),
                SplashScreenSupport.serverVersionWithoutMcSuffix(),
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
                "Server: " + server + " | MC Support: 1.20.1 - 26.x",
                "Java: " + javaVersion + " | Date: " + date
        };
    }

    static String colorize(String row, ChatColor fill, ChatColor edge) {
        StringBuilder output = new StringBuilder(row.length() * 2);
        boolean inFill = false;
        boolean inEdge = false;
        for (int index = 0; index < row.length(); index++) {
            char glyph = row.charAt(index);
            if (glyph == '█') {
                if (!inFill) {
                    output.append(fill);
                    inFill = true;
                    inEdge = false;
                }
            } else if (glyph != ' ' && !inEdge) {
                output.append(edge);
                inEdge = true;
                inFill = false;
            }
            output.append(glyph);
        }
        return output.toString();
    }
}
