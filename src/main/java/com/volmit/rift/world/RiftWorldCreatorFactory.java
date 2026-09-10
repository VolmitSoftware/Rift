package com.volmit.rift.world;

import com.volmit.rift.storage.WorldDirectoryResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.WorldCreator;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Objects;

public final class RiftWorldCreatorFactory {
    private final PlatformCapabilities capabilities;
    private final WorldDirectoryResolver directories;

    public RiftWorldCreatorFactory(PlatformCapabilities capabilities, WorldDirectoryResolver directories) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.directories = Objects.requireNonNull(directories, "directories");
    }

    public WorldCreator forNewWorld(String logicalName) throws IOException {
        if (!capabilities.supportsNamespacedWorldStorage()) {
            return new WorldCreator(logicalName);
        }
        return riftCreator(logicalName);
    }

    public WorldCreator forStoredWorld(String logicalName, Path directory) throws IOException {
        if (!directories.isRiftDimension(directory, logicalName)) {
            return new WorldCreator(logicalName);
        }
        if (!capabilities.supportsNamespacedWorldStorage()) {
            throw new IOException("Rift namespaced world storage requires Paper 26.1 or newer: " + logicalName);
        }
        return riftCreator(logicalName);
    }

    private WorldCreator riftCreator(String logicalName) throws IOException {
        try {
            Method factory = WorldCreator.class.getMethod("ofKey", NamespacedKey.class);
            Object creator = factory.invoke(null, RiftWorldIdentity.key(logicalName));
            if (creator instanceof WorldCreator worldCreator) {
                return worldCreator;
            }
            throw new IOException("Paper returned an invalid WorldCreator for " + RiftWorldIdentity.key(logicalName));
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IOException("Paper rejected Rift world key " + RiftWorldIdentity.key(logicalName), cause);
        } catch (ReflectiveOperationException | SecurityException exception) {
            throw new IOException("Paper does not expose namespaced world creation for " + logicalName, exception);
        }
    }
}
