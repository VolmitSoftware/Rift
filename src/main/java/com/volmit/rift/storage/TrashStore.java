package com.volmit.rift.storage;

import art.arcane.volmlib.util.config.TomlCodec;
import art.arcane.volmlib.util.io.AtomicFileIO;
import com.volmit.rift.Rift;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

public final class TrashStore {
    private static final String SOURCE_TAG = "Rift trash";

    private final Rift plugin;
    private final File directory;
    private final ConcurrentMap<String, TrashEntry> entries = new ConcurrentHashMap<>();

    public TrashStore(Rift plugin, File directory) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public synchronized boolean loadAll() {
        directory.mkdirs();
        File[] files = directory.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".toml"));
        ConcurrentMap<String, TrashEntry> loaded = new ConcurrentHashMap<>();
        boolean valid = true;
        if (files != null) {
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - ".toml".length());
                TrashEntry entry = read(file, id);
                if (entry == null) {
                    valid = false;
                    continue;
                }
                loaded.put(id, entry);
            }
        }
        if (!valid) {
            return false;
        }
        entries.clear();
        entries.putAll(loaded);
        return true;
    }

    public synchronized void save(TrashEntry entry) throws IOException {
        directory.mkdirs();
        AtomicFileIO.writeString(file(entry.getId()).toPath(), TomlCodec.toToml(entry, SOURCE_TAG));
        entries.put(entry.getId(), entry);
    }

    public Optional<TrashEntry> find(String id) {
        if (!validId(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(id));
    }

    public List<TrashEntry> all() {
        List<TrashEntry> result = new ArrayList<>(entries.values());
        result.sort(Comparator.comparingLong(TrashEntry::getDeletedAtEpochMillis).reversed());
        return List.copyOf(result);
    }

    public synchronized void delete(String id) throws IOException {
        if (!validId(id)) {
            throw new IOException("Invalid trash id: " + id);
        }
        Files.deleteIfExists(file(id).toPath());
        entries.remove(id);
    }

    public synchronized boolean reloadSnapshot(File file, String raw) {
        if (!isTrashFile(file)) {
            return false;
        }
        String id = file.getName().substring(0, file.getName().length() - ".toml".length());
        if (raw == null) {
            entries.remove(id);
            return true;
        }
        TrashEntry entry = readSnapshot(file, id, raw);
        if (entry == null) {
            return false;
        }
        entries.put(id, entry);
        return true;
    }

    public Collection<File> files() {
        List<File> files = new ArrayList<>();
        for (TrashEntry entry : all()) {
            files.add(file(entry.getId()));
        }
        return files;
    }

    public File directory() {
        return directory;
    }

    public boolean isTrashFile(File file) {
        return file != null
                && file.getParentFile() != null
                && directory.toPath().toAbsolutePath().normalize()
                .equals(file.getParentFile().toPath().toAbsolutePath().normalize())
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".toml");
    }

    private File file(String id) {
        return new File(directory, id + ".toml");
    }

    private TrashEntry read(File file, String id) {
        if (!validId(id)) {
            plugin.getLogger().severe("Rejected invalid trash manifest filename " + file);
            return null;
        }
        try {
            TrashEntry entry = TomlCodec.fromToml(Files.readString(file.toPath(), StandardCharsets.UTF_8), TrashEntry.class);
            if (entry == null || !id.equals(entry.getId())) {
                throw new IOException("Trash manifest id does not match its filename");
            }
            return entry;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Rejected invalid trash manifest " + file, exception);
            return null;
        }
    }

    private TrashEntry readSnapshot(File file, String id, String raw) {
        if (!validId(id)) {
            plugin.getLogger().severe("Rejected invalid trash manifest filename " + file);
            return null;
        }
        try {
            TrashEntry entry = TomlCodec.fromToml(raw, TrashEntry.class);
            if (entry == null || !id.equals(entry.getId())) {
                throw new IOException("Trash manifest id does not match its filename");
            }
            return entry;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Rejected invalid trash manifest snapshot " + file
                    + "; the last valid state remains active", exception);
            return null;
        }
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[0-9]{8}T[0-9]{9}Z-[A-Za-z0-9._-]{1,64}");
    }
}
