package com.volmit.rift.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class WorldDirectoryResolver {
    public static final String RIFT_NAMESPACE = "rift";
    private static final Set<String> PRIMARY_DIMENSION_KEYS = Set.of("overworld", "the_nether", "the_end");

    private final Path worldContainer;
    private final Path dimensionRoot;
    private final WorldNamePolicy names;

    public WorldDirectoryResolver(Path worldContainer, Path primaryWorldFolder, WorldNamePolicy names) {
        this.worldContainer = worldContainer.toAbsolutePath().normalize();
        Path primary = primaryWorldFolder.toAbsolutePath().normalize();
        if (!primary.startsWith(this.worldContainer)) {
            throw new IllegalArgumentException("Primary world folder is outside the Bukkit world container: " + primary);
        }
        dimensionRoot = resolveDimensionRoot(primary);
        this.names = names;
    }

    public Optional<Path> find(WorldProfile profile) throws IOException {
        if (profile.getDirectory().isBlank()) {
            return find(profile.getName());
        }
        Path configured = storedPath(profile.getName(), profile.getDirectory());
        return isWorldStorage(configured) ? Optional.of(configured) : Optional.empty();
    }

    public Optional<Path> find(String name) throws IOException {
        String validName = names.requireValid(name);
        Path legacy = names.resolve(validName);
        if (isLegacyWorld(legacy)) {
            return Optional.of(legacy);
        }
        Path riftDimension = riftDimension(validName);
        if (isWorldStorage(riftDimension)) {
            return Optional.of(riftDimension);
        }
        Path minecraftDimension = minecraftDimension(validName);
        if (!PRIMARY_DIMENSION_KEYS.contains(validName.toLowerCase(Locale.ROOT)) && isWorldStorage(minecraftDimension)) {
            return Optional.of(minecraftDimension);
        }
        return Optional.empty();
    }

    public Path require(WorldProfile profile) throws IOException {
        return find(profile).orElseThrow(() -> new IOException(
                "Managed world storage does not exist for " + profile.getName()
                        + "; checked standalone, Rift, and Minecraft dimension layouts"
        ));
    }

    public Path require(String name) throws IOException {
        return find(name).orElseThrow(() -> new IOException(
                "World storage does not exist for " + names.requireValid(name)
                        + "; checked standalone, Rift, and Minecraft dimension layouts"
        ));
    }

    public boolean exists(String name) throws IOException {
        return find(name).isPresent();
    }

    public boolean isDefinitelyMissing(WorldProfile profile) throws IOException {
        requireAccessibleWorldContainer();
        if (!profile.getDirectory().isBlank()) {
            Path configured = storedPath(profile.getName(), profile.getDirectory());
            if (!pathIsMissing(configured)) {
                return false;
            }
            requireNoAlternateStorage(profile.getName(), configured);
            return true;
        }
        String validName = names.requireValid(profile.getName());
        Path legacy = names.resolve(validName);
        Path riftDimension = riftDimension(validName);
        Path minecraftDimension = minecraftDimension(validName);
        return pathIsMissing(legacy) && pathIsMissing(riftDimension) && pathIsMissing(minecraftDimension);
    }

    public Path restoreTarget(WorldProfile profile) throws IOException {
        if (!profile.getDirectory().isBlank()) {
            return storedPath(profile.getName(), profile.getDirectory());
        }
        return names.resolve(profile.getName());
    }

    public String relative(Path directory, String worldName) throws IOException {
        String validName = names.requireValid(worldName);
        Path normalized = directory.toAbsolutePath().normalize();
        requireConfined(normalized);
        if (!isLegacyLocation(normalized) && !isDimensionLocation(normalized)) {
            throw new IOException("Bukkit returned an unsupported world storage layout: " + normalized);
        }
        if (!matchesWorldName(normalized, validName)) {
            throw new IOException("World storage folder does not match its Rift name: " + normalized);
        }
        return worldContainer.relativize(normalized).toString().replace('\\', '/');
    }

    public boolean isRiftDimension(Path directory, String worldName) throws IOException {
        String validName = names.requireValid(worldName);
        Path normalized = directory.toAbsolutePath().normalize();
        requireConfined(normalized);
        return normalized.equals(riftDimension(validName));
    }

    public List<DiscoveredWorld> discover() throws IOException {
        Map<String, DiscoveredWorld> discovered = new LinkedHashMap<>();
        discoverLegacy(discovered);
        discoverDimensions(discovered);
        List<DiscoveredWorld> result = new ArrayList<>(discovered.values());
        result.sort(Comparator.comparing(DiscoveredWorld::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private Path storedPath(String worldName, String relative) throws IOException {
        String validName = names.requireValid(worldName);
        Path relativePath;
        try {
            relativePath = Path.of(relative.replace('/', File.separatorChar)).normalize();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid managed world storage path: " + relative, exception);
        }
        if (relativePath.isAbsolute() || relativePath.startsWith("..")) {
            throw new IOException("Managed world storage path must stay relative to the world container: " + relative);
        }
        Path candidate = worldContainer.resolve(relativePath).toAbsolutePath().normalize();
        requireConfined(candidate);
        if (!isLegacyLocation(candidate) && !isDimensionLocation(candidate)) {
            throw new IOException("Managed world storage path is not a supported Bukkit layout: " + relative);
        }
        if (!matchesWorldName(candidate, validName)) {
            throw new IOException("Managed world storage path does not end with its Rift name: " + relative);
        }
        return candidate;
    }

    private void requireConfined(Path candidate) throws IOException {
        if (!candidate.startsWith(worldContainer) || candidate.equals(worldContainer)) {
            throw new IOException("World storage escapes the Bukkit world container: " + candidate);
        }
        Path cursor = worldContainer;
        Path relative = worldContainer.relativize(candidate);
        for (Path segment : relative) {
            cursor = cursor.resolve(segment);
            if (Files.isSymbolicLink(cursor)) {
                throw new IOException("Symbolic-link world storage is not supported: " + cursor);
            }
        }
    }

    private boolean isWorldStorage(Path candidate) throws IOException {
        requireConfined(candidate);
        return isLegacyWorld(candidate) || isDimensionWorld(candidate);
    }

    private boolean pathIsMissing(Path candidate) throws IOException {
        requireConfined(candidate);
        try {
            Files.readAttributes(candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return false;
        } catch (NoSuchFileException exception) {
            return true;
        }
    }

    private void requireAccessibleWorldContainer() throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                worldContainer, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("Bukkit world container is not a regular directory: " + worldContainer);
        }
    }

    private void requireNoAlternateStorage(String worldName, Path configured) throws IOException {
        String validName = names.requireValid(worldName);
        Path legacy = names.resolve(validName);
        Path riftDimension = riftDimension(validName);
        Path minecraftDimension = minecraftDimension(validName);
        if ((!configured.equals(legacy) && !pathIsMissing(legacy))
                || (!configured.equals(riftDimension) && !pathIsMissing(riftDimension))
                || (!configured.equals(minecraftDimension) && !pathIsMissing(minecraftDimension))) {
            throw new IOException("Recorded world storage is absent for " + validName
                    + " but another same-name storage entry exists; the managed profile was retained");
        }
    }

    private boolean isLegacyLocation(Path candidate) {
        return worldContainer.equals(candidate.getParent());
    }

    private boolean isDimensionLocation(Path candidate) {
        Path namespace = candidate.getParent();
        return namespace != null && dimensionRoot.equals(namespace.getParent());
    }

    private Path minecraftDimension(String worldName) {
        return dimensionRoot.resolve("minecraft").resolve(worldName.toLowerCase(Locale.ROOT)).normalize();
    }

    private Path riftDimension(String worldName) {
        return dimensionRoot.resolve(RIFT_NAMESPACE).resolve(worldName.toLowerCase(Locale.ROOT)).normalize();
    }

    private boolean matchesWorldName(Path directory, String worldName) {
        Path fileName = directory.getFileName();
        String expected = isLegacyLocation(directory) ? worldName : worldName.toLowerCase(Locale.ROOT);
        return fileName != null && fileName.toString().equals(expected);
    }

    private Path resolveDimensionRoot(Path primary) {
        Path namespace = primary.getParent();
        Path nestedRoot = namespace == null ? null : namespace.getParent();
        if (nestedRoot != null && nestedRoot.getFileName() != null
                && nestedRoot.getFileName().toString().equals("dimensions")
                && nestedRoot.startsWith(worldContainer)) {
            return nestedRoot;
        }
        return primary.resolve("dimensions").normalize();
    }

    private boolean isLegacyWorld(Path candidate) {
        return isLegacyLocation(candidate)
                && Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(candidate)
                && Files.isRegularFile(candidate.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(candidate.resolve("level.dat"));
    }

    private boolean isDimensionWorld(Path candidate) {
        if (!isDimensionLocation(candidate)
                || !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(candidate)) {
            return false;
        }
        return Files.isDirectory(candidate.resolve("region"), LinkOption.NOFOLLOW_LINKS)
                || Files.isRegularFile(candidate.resolve("paper-world.yml"), LinkOption.NOFOLLOW_LINKS);
    }

    private void discoverLegacy(Map<String, DiscoveredWorld> discovered) throws IOException {
        if (!Files.isDirectory(worldContainer, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> entries = Files.list(worldContainer)) {
            entries.filter(this::isLegacyWorld).forEach(path -> addDiscovered(discovered, path));
        }
    }

    private void discoverDimensions(Map<String, DiscoveredWorld> discovered) throws IOException {
        discoverDimensionNamespace(discovered, RIFT_NAMESPACE, false);
        discoverDimensionNamespace(discovered, "minecraft", true);
    }

    private void discoverDimensionNamespace(
            Map<String, DiscoveredWorld> discovered,
            String namespace,
            boolean filterPrimaryDimensions
    ) throws IOException {
        Path namespaceDirectory = dimensionRoot.resolve(namespace);
        if (!Files.isDirectory(namespaceDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(namespaceDirectory)) {
            return;
        }
        try (Stream<Path> entries = Files.list(namespaceDirectory)) {
            entries.filter(this::isDimensionWorld)
                    .filter(path -> !filterPrimaryDimensions
                            || !PRIMARY_DIMENSION_KEYS.contains(path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(path -> addDiscovered(discovered, path));
        }
    }

    private void addDiscovered(Map<String, DiscoveredWorld> discovered, Path path) {
        String name = path.getFileName().toString();
        try {
            String validName = names.requireValid(name);
            discovered.putIfAbsent(validName.toLowerCase(Locale.ROOT), new DiscoveredWorld(validName, path));
        } catch (IllegalArgumentException ignored) {
        }
    }

    public record DiscoveredWorld(String name, Path directory) {
    }
}
