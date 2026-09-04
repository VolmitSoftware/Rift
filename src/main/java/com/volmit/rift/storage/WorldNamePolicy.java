package com.volmit.rift.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class WorldNamePolicy {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
    );

    private final Path worldContainer;

    public WorldNamePolicy(Path worldContainer) {
        this.worldContainer = worldContainer.toAbsolutePath().normalize();
    }

    public String requireValid(String name) {
        String candidate = name == null ? "" : name.trim();
        if (!VALID_NAME.matcher(candidate).matches()) {
            throw new IllegalArgumentException("World names must be 1-64 characters using letters, numbers, dot, dash, or underscore");
        }
        if (candidate.equals(".") || candidate.equals("..") || candidate.endsWith(".")) {
            throw new IllegalArgumentException("World name is not safe for filesystem use: " + candidate);
        }
        String deviceName = candidate.toLowerCase(Locale.ROOT).split("\\.", 2)[0];
        if (WINDOWS_RESERVED.contains(deviceName)) {
            throw new IllegalArgumentException("World name is reserved by the operating system: " + candidate);
        }
        return candidate;
    }

    public Path resolve(String name) throws IOException {
        String validName = requireValid(name);
        Path candidate = worldContainer.resolve(validName).toAbsolutePath().normalize();
        if (!worldContainer.equals(candidate.getParent())) {
            throw new IOException("World path escapes the configured world container: " + candidate);
        }
        if (Files.isSymbolicLink(candidate)) {
            throw new IOException("Symbolic-link world directories are not supported: " + candidate);
        }
        return candidate;
    }

}
