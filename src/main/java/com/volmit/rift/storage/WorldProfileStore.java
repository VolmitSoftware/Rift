package com.volmit.rift.storage;

import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.io.AtomicFileIO;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

public final class WorldProfileStore {
    private static final String SOURCE_TAG = "Rift world";
    private static final DateTimeFormatter RETIRED_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final Plugin plugin;
    private final File directory;
    private final WorldNamePolicy names;
    private final ConcurrentMap<String, WorldProfile> profiles = new ConcurrentHashMap<>();

    public WorldProfileStore(Plugin plugin, File directory, WorldNamePolicy names) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.names = Objects.requireNonNull(names, "names");
    }

    public synchronized boolean loadAll() {
        try {
            prepareDirectory();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unable to access Rift world profile storage " + directory, exception);
            return false;
        }
        File[] files = directory.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".toml"));
        if (files == null) {
            plugin.getLogger().severe("Unable to enumerate Rift world profile storage " + directory);
            return false;
        }
        ConcurrentMap<String, WorldProfile> loaded = new ConcurrentHashMap<>();
        boolean valid = true;
        List<File> ordered = new ArrayList<>(List.of(files));
        ordered.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : ordered) {
            WorldProfile profile = read(file);
            if (profile == null) {
                valid = false;
                continue;
            }
            WorldProfile previous = loaded.putIfAbsent(profile.key(), profile);
            if (previous != null) {
                plugin.getLogger().severe("Rejected case-colliding world profile " + file + " for " + profile.getName());
                valid = false;
            }
        }
        if (!valid) {
            return false;
        }
        profiles.clear();
        profiles.putAll(loaded);
        return valid;
    }

    public synchronized boolean reload(File file) {
        if (file == null || !isProfileFile(file)) {
            return false;
        }
        String fileName = file.getName();
        String expectedName = fileName.substring(0, fileName.length() - ".toml".length());
        if (!file.exists()) {
            profiles.remove(expectedName.toLowerCase(Locale.ROOT));
            return true;
        }
        WorldProfile profile = read(file);
        if (profile == null) {
            return false;
        }
        return replace(profile, file);
    }

    public synchronized boolean reloadSnapshot(File file, String raw) {
        if (file == null || !isProfileFile(file)) {
            return false;
        }
        if (raw == null) {
            String fileName = file.getName();
            String expectedName = fileName.substring(0, fileName.length() - ".toml".length());
            profiles.remove(expectedName.toLowerCase(Locale.ROOT));
            return true;
        }
        WorldProfile profile = readSnapshot(file, raw);
        if (profile == null) {
            return false;
        }
        return replace(profile, file);
    }

    public synchronized void save(WorldProfile profile) throws IOException {
        WorldProfile normalized = Objects.requireNonNull(profile, "profile").normalize(names);
        File file = file(normalized.getName());
        AtomicFileIO.writeString(file.toPath(), TomlCodec.toToml(normalized, SOURCE_TAG));
        profiles.put(normalized.key(), normalized);
    }

    public synchronized void delete(String name) throws IOException {
        String validName = names.requireValid(name);
        Files.deleteIfExists(file(validName).toPath());
        profiles.remove(validName.toLowerCase(Locale.ROOT));
    }

    public synchronized Optional<File> retire(String name) throws IOException {
        String validName = names.requireValid(name);
        Path source = file(validName).toPath();
        if (profileFileIsMissing(source)) {
            profiles.remove(validName.toLowerCase(Locale.ROOT));
            return Optional.empty();
        }
        Path retiredDirectory = directory.toPath().resolve("retired");
        Files.createDirectories(retiredDirectory);
        BasicFileAttributes retiredAttributes = Files.readAttributes(
                retiredDirectory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!retiredAttributes.isDirectory() || retiredAttributes.isSymbolicLink()) {
            throw new IOException("Retired world profile path is not a regular directory: " + retiredDirectory);
        }
        Path target = uniqueRetiredPath(retiredDirectory, validName);
        move(source, target);
        profiles.remove(validName.toLowerCase(Locale.ROOT));
        return Optional.of(target.toFile());
    }

    public Optional<WorldProfile> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(profiles.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    public List<WorldProfile> all() {
        List<WorldProfile> result = new ArrayList<>(profiles.values());
        result.sort(Comparator.comparing(WorldProfile::getName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public Collection<File> files() {
        List<File> files = new ArrayList<>();
        for (WorldProfile profile : all()) {
            files.add(file(profile.getName()));
        }
        return files;
    }

    public File directory() {
        return directory;
    }

    public boolean isProfileFile(File file) {
        if (file == null || file.getParentFile() == null) {
            return false;
        }
        return directory.toPath().toAbsolutePath().normalize().equals(file.getParentFile().toPath().toAbsolutePath().normalize())
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".toml");
    }

    private WorldProfile read(File file) {
        try {
            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return readValidated(file, raw);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Rejected invalid world profile " + file + "; the last valid state remains active", exception);
            return null;
        }
    }

    private WorldProfile readSnapshot(File file, String raw) {
        try {
            return readValidated(file, raw);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Rejected invalid world profile snapshot " + file + "; the last valid state remains active", exception);
            return null;
        }
    }

    private WorldProfile readValidated(File file, String raw) throws IOException {
        WorldProfile profile = TomlCodec.fromToml(raw, WorldProfile.class);
        if (profile == null) {
            throw new IOException("Profile parser returned null");
        }
        profile.normalize(names);
        String expectedName = file.getName().substring(0, file.getName().length() - ".toml".length());
        if (!expectedName.equals(profile.getName())) {
            throw new IOException("Profile name must match its filename exactly: " + file.getName());
        }
        return profile;
    }

    private File file(String name) {
        return new File(directory, names.requireValid(name) + ".toml");
    }

    private void prepareDirectory() throws IOException {
        Path root = directory.toPath();
        Files.createDirectories(root);
        BasicFileAttributes attributes = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("World profile storage is not a regular directory: " + root);
        }
    }

    private boolean profileFileIsMissing(Path source) throws IOException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                throw new IOException("World profile is not a regular file: " + source);
            }
            return false;
        } catch (NoSuchFileException exception) {
            prepareDirectory();
            return true;
        }
    }

    private Path uniqueRetiredPath(Path retiredDirectory, String worldName) {
        String prefix = RETIRED_TIME.format(Instant.now()) + "-" + worldName;
        Path candidate = retiredDirectory.resolve(prefix + ".toml");
        int suffix = 2;
        while (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            candidate = retiredDirectory.resolve(prefix + "-" + suffix + ".toml");
            suffix++;
        }
        return candidate;
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private boolean replace(WorldProfile profile, File source) {
        WorldProfile existing = profiles.get(profile.key());
        if (existing != null && !existing.getName().equals(profile.getName())) {
            plugin.getLogger().severe("Rejected case-colliding world profile " + source + " for " + profile.getName());
            return false;
        }
        profiles.put(profile.key(), profile);
        return true;
    }
}
