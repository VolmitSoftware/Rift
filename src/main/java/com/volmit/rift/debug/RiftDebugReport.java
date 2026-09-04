package com.volmit.rift.debug;

import com.sun.management.OperatingSystemMXBean;
import com.sun.management.UnixOperatingSystemMXBean;
import com.volmit.rift.config.RiftConfig;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.LockInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

final class RiftDebugReport {
    private static final int BUFFER_SIZE = 16 * 1024;

    private RiftDebugReport() {
    }

    static String create(RiftDebugSnapshot snapshot) {
        StringBuilder report = new StringBuilder(16_384);
        section(report, "Rift diagnostic report");
        value(report, "Generated (UTC)", snapshot.generatedAt());
        value(report, "Format", 3);
        value(report, "Command sender type", snapshot.senderType());

        section(report, "Rift");
        value(report, "Version", snapshot.riftVersion());
        value(report, "Java bytecode target", 17);
        value(report, "Platform mode", snapshot.platform());
        value(report, "Dynamic world lifecycle", snapshot.dynamicWorldLifecycle());
        value(report, "Configuration loaded", snapshot.config() != null);
        value(report, "Localization loaded", !snapshot.activeLocale().isBlank());
        value(report, "Hot reload running", snapshot.hotReload().running());
        value(report, "Hot reload generation", snapshot.hotReload().generation());
        value(report, "Hot reload polls", snapshot.hotReload().polls());
        value(report, "Hot reload successful polls", snapshot.hotReload().successfulPolls());
        value(report, "Hot reload failures", snapshot.hotReload().failures());
        value(report, "Hot reload last poll", instant(snapshot.hotReload().lastPollEpochMillis()));
        value(report, "Hot reload last successful poll", instant(snapshot.hotReload().lastSuccessfulPollEpochMillis()));
        value(report, "Hot reload last failure", snapshot.hotReload().lastFailure().isBlank() ? "none" : snapshot.hotReload().lastFailure());
        value(report, "bStats runtime active", snapshot.metricsRunning());
        value(report, "Debug upload enabled", snapshot.config().isDebugUploadEnabled());
        value(report, "World container writable", snapshot.worldContainerWritable());
        value(report, "Active locale", snapshot.activeLocale());
        value(report, "Active language file", snapshot.activeLanguageFile());
        value(report, "Available locales", String.join(", ", snapshot.availableLocales()));
        value(report, "Available locale count", snapshot.availableLocales().size());
        value(report, "Remote language catalog revision", snapshot.remoteLanguageCatalogReference());
        value(report, "Remote language catalog failure", snapshot.remoteLanguageCatalogFailure());
        value(report, "Latest remote language download failure", snapshot.remoteLanguageDownloadFailure());
        value(report, "Managed profiles", snapshot.managedProfiles());
        value(report, "Discovered world storage", snapshot.discoveredWorldStorage());
        value(report, "Active world operations", snapshot.worlds().stream().filter(RiftDebugSnapshot.WorldState::operationActive).count());
        value(report, "Quarantine entries", snapshot.quarantine().size());

        section(report, "Server");
        value(report, "Implementation", snapshot.serverName());
        value(report, "Server version", snapshot.serverVersion());
        value(report, "Bukkit API", snapshot.bukkitVersion());
        value(report, "Minecraft version", snapshot.minecraftVersion());
        value(report, "Online mode", snapshot.onlineMode());
        value(report, "Players online", snapshot.onlinePlayers());
        value(report, "Maximum players", snapshot.maximumPlayers());
        value(report, "View distance", snapshot.viewDistance());
        value(report, "Simulation distance", snapshot.simulationDistance());
        value(report, "Default game mode", snapshot.defaultGameMode());
        value(report, "Hardcore", snapshot.hardcore());
        value(report, "Flight allowed", snapshot.allowFlight());
        value(report, "Whitelist enabled", snapshot.whitelist());
        value(report, "Spawn radius", snapshot.spawnRadius());
        value(report, "Idle timeout", snapshot.idleTimeout());
        value(report, "Pending scheduler tasks", snapshot.pendingSchedulerTasks());
        value(report, "Loaded worlds", snapshot.loadedWorlds());
        for (Map.Entry<String, Integer> environment : snapshot.worldsByEnvironment().entrySet()) {
            value(report, "Worlds in " + environment.getKey(), environment.getValue());
        }
        value(report, "TPS (1m, 5m, 15m)", snapshot.tickRates());
        value(report, "Average tick time", snapshot.averageTickMillis());

        appendConfig(report, snapshot.config());
        appendWorlds(report, snapshot.worlds(), snapshot.worldContainer());
        appendQuarantine(report, snapshot.quarantine());
        appendPlugins(report, snapshot.plugins());
        appendRuntime(report);
        appendMemory(report);
        appendCpu(report);
        appendThreads(report);
        appendGarbageCollectors(report);
        appendBufferPools(report);
        appendStorage(report, "Plugin data filesystem", snapshot.dataDirectory());
        appendStorage(report, "World filesystem", snapshot.worldContainer());
        appendManagedFiles(report, snapshot.dataDirectory());
        appendArtifact(report, snapshot.codeSource());
        return report.toString();
    }

    private static void appendConfig(StringBuilder report, RiftConfig config) {
        section(report, "Rift configuration");
        value(report, "language", config.getLanguage());
        value(report, "hotReloadPollMillis", config.getHotReloadPollMillis());
        value(report, "hotReloadCooldownMillis", config.getHotReloadCooldownMillis());
        value(report, "autoLoadManagedWorlds", config.isAutoLoadManagedWorlds());
        value(report, "evacuationWorld", config.getEvacuationWorld().isBlank() ? "primary" : config.getEvacuationWorld());
        value(report, "allowWorldDeletion", config.isAllowWorldDeletion());
        value(report, "deleteConfirmationSeconds", config.getDeleteConfirmationSeconds());
        value(report, "splashScreen", config.isSplashScreen());
        value(report, "titlePopups", config.isTitlePopups());
        value(report, "actionBarPopups", config.isActionBarPopups());
        value(report, "sounds", config.isSounds());
        value(report, "worldLifecycleFeedback", config.isWorldLifecycleFeedback());
        value(report, "teleportFeedback", config.isTeleportFeedback());
        value(report, "failureFeedback", config.isFailureFeedback());
        value(report, "worldLifecycleSound", config.worldLifecycleSound());
        value(report, "teleportSound", config.teleportSound());
        value(report, "failureSound", config.failureSound());
        value(report, "soundVolume", config.getSoundVolume());
        value(report, "soundPitch", config.getSoundPitch());
        value(report, "titleFadeInTicks", config.getTitleFadeInTicks());
        value(report, "titleStayTicks", config.getTitleStayTicks());
        value(report, "titleFadeOutTicks", config.getTitleFadeOutTicks());
        value(report, "verbose", config.isVerbose());
        value(report, "debugUploadEnabled", config.isDebugUploadEnabled());
        value(report, "bstatsEnabled", config.isBstatsEnabled());
    }

    private static void appendWorlds(StringBuilder report, List<RiftDebugSnapshot.WorldState> worlds, Path worldContainer) {
        section(report, "World lifecycle inventory");
        value(report, "Entries", worlds.size());
        value(report, "Loaded", worlds.stream().filter(RiftDebugSnapshot.WorldState::loaded).count());
        value(report, "Managed", worlds.stream().filter(RiftDebugSnapshot.WorldState::managed).count());
        value(report, "Discovered only", worlds.stream().filter(world -> !world.managed() && world.presentOnDisk()).count());
        value(report, "Missing", worlds.stream().filter(world -> world.managed() && !world.presentOnDisk()).count());
        value(report, "Auto-load", worlds.stream().filter(RiftDebugSnapshot.WorldState::autoLoad).count());
        value(report, "Protected", worlds.stream().filter(RiftDebugSnapshot.WorldState::protectedWorld).count());
        value(report, "Active operations", worlds.stream().filter(RiftDebugSnapshot.WorldState::operationActive).count());
        for (RiftDebugSnapshot.WorldState world : worlds) {
            report.append("- ").append(sanitize(world.name())).append(" | loaded=").append(world.loaded())
                    .append(" | managed=").append(world.managed())
                    .append(" | onDisk=").append(world.presentOnDisk())
                    .append(" | autoLoad=").append(world.autoLoad())
                    .append(" | protected=").append(world.protectedWorld())
                    .append(" | environment=").append(sanitize(world.environment()))
                    .append(" | worldType=").append(sanitize(world.worldType()))
                    .append(" | generator=").append(sanitize(world.generator().isBlank() ? "vanilla" : world.generator()))
                    .append(" | seed=").append(world.seed())
                    .append(" | configuredDirectory=").append(sanitize(world.configuredDirectory().isBlank() ? "automatic" : world.configuredDirectory()))
                    .append(" | detectedDirectory=").append(sanitize(world.detectedDirectory().isBlank() ? "not detected" : world.detectedDirectory()))
                    .append(" | storageLayout=").append(sanitize(world.storageLayout()))
                    .append(" | operationActive=").append(world.operationActive())
                    .append('\n');
            appendWorldFiles(report, world, worldContainer);
        }
    }

    private static void appendWorldFiles(StringBuilder report, RiftDebugSnapshot.WorldState world, Path worldContainer) {
        if (world.detectedDirectory().isBlank()) {
            return;
        }
        Path directory;
        try {
            directory = Path.of(world.detectedDirectory());
        } catch (RuntimeException exception) {
            return;
        }
        directory = directory.isAbsolute()
                ? directory.toAbsolutePath().normalize()
                : worldContainer.toAbsolutePath().normalize().resolve(directory).normalize();
        Path normalizedContainer = worldContainer.toAbsolutePath().normalize();
        if (!directory.startsWith(normalizedContainer) || directory.equals(normalizedContainer) || containsSymbolicLink(normalizedContainer, directory)) {
            report.append("  storageState=invalid or unsafe path\n");
            return;
        }
        report.append("  level.dat=").append(Files.isRegularFile(directory.resolve("level.dat")))
                .append(" | level.dat_old=").append(Files.isRegularFile(directory.resolve("level.dat_old")))
                .append(" | session.lock=").append(Files.isRegularFile(directory.resolve("session.lock")))
                .append(" | region=").append(Files.isDirectory(directory.resolve("region")))
                .append('\n');
    }

    private static void appendQuarantine(StringBuilder report, List<RiftDebugSnapshot.TrashState> quarantine) {
        section(report, "Quarantine");
        value(report, "Entries", quarantine.size());
        for (RiftDebugSnapshot.TrashState entry : quarantine) {
            report.append("- ").append(sanitize(entry.id()))
                    .append(" | world=").append(sanitize(entry.worldName()))
                    .append(" | deletedAt=").append(entry.deletedAt())
                    .append('\n');
        }
    }

    private static void appendPlugins(StringBuilder report, List<RiftDebugSnapshot.PluginState> plugins) {
        section(report, "Plugins");
        value(report, "Loaded", plugins.size());
        for (RiftDebugSnapshot.PluginState plugin : plugins) {
            report.append("- ").append(sanitize(plugin.name()))
                    .append(' ').append(sanitize(plugin.version()))
                    .append(" | enabled=").append(plugin.enabled())
                    .append(" | main=").append(sanitize(plugin.mainClass()))
                    .append(" | authors=").append(sanitize(String.join(", ", plugin.authors())))
                    .append(" | load=").append(sanitize(plugin.loadOrder()))
                    .append(" | api=").append(sanitize(plugin.apiVersion()))
                    .append(" | depend=").append(sanitize(String.join(", ", plugin.hardDependencies())))
                    .append(" | softDepend=").append(sanitize(String.join(", ", plugin.softDependencies())))
                    .append('\n');
        }
    }

    private static void appendRuntime(StringBuilder report) {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
        CompilationMXBean compilation = ManagementFactory.getCompilationMXBean();
        section(report, "Java runtime");
        value(report, "Process ID", ProcessHandle.current().pid());
        value(report, "Java version", System.getProperty("java.version"));
        value(report, "Java vendor", System.getProperty("java.vendor"));
        value(report, "Java runtime name", System.getProperty("java.runtime.name"));
        value(report, "Java runtime version", System.getProperty("java.runtime.version"));
        value(report, "Java specification", System.getProperty("java.specification.name"));
        value(report, "Java specification version", System.getProperty("java.specification.version"));
        value(report, "Java specification vendor", System.getProperty("java.specification.vendor"));
        value(report, "VM name", System.getProperty("java.vm.name"));
        value(report, "VM vendor", System.getProperty("java.vm.vendor"));
        value(report, "VM version", System.getProperty("java.vm.version"));
        value(report, "Management specification version", runtime.getManagementSpecVersion());
        value(report, "Default locale", Locale.getDefault().toLanguageTag());
        value(report, "Default time zone", ZoneId.systemDefault());
        value(report, "Default charset", Charset.defaultCharset());
        value(report, "Native encoding", System.getProperty("native.encoding"));
        value(report, "Classes currently loaded", classes.getLoadedClassCount());
        value(report, "Classes loaded total", classes.getTotalLoadedClassCount());
        value(report, "Classes unloaded", classes.getUnloadedClassCount());
        value(report, "JIT compiler", compilation == null ? "unavailable" : compilation.getName());
        value(report, "JIT compilation time", compilation == null || !compilation.isCompilationTimeMonitoringSupported()
                ? "unavailable"
                : duration(compilation.getTotalCompilationTime()));
        value(report, "Uptime", duration(runtime.getUptime()));
        value(report, "Start time (epoch ms)", runtime.getStartTime());
    }

    private static void appendMemory(StringBuilder report) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();
        section(report, "Memory");
        value(report, "Objects pending finalization", memory.getObjectPendingFinalizationCount());
        memoryUsage(report, "Heap", memory.getHeapMemoryUsage());
        memoryUsage(report, "Non-heap", memory.getNonHeapMemoryUsage());
        value(report, "Runtime used", bytes(runtime.totalMemory() - runtime.freeMemory()));
        value(report, "Runtime free", bytes(runtime.freeMemory()));
        value(report, "Runtime total", bytes(runtime.totalMemory()));
        value(report, "Runtime maximum", bytes(runtime.maxMemory()));
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            memoryUsage(report, "Pool " + pool.getName(), pool.getUsage());
            memoryUsage(report, "Pool " + pool.getName() + " peak", pool.getPeakUsage());
            memoryUsage(report, "Pool " + pool.getName() + " collection", pool.getCollectionUsage());
        }
    }

    private static void appendCpu(StringBuilder report) {
        java.lang.management.OperatingSystemMXBean base = ManagementFactory.getOperatingSystemMXBean();
        section(report, "CPU and operating system");
        value(report, "OS name", System.getProperty("os.name"));
        value(report, "OS version", System.getProperty("os.version"));
        value(report, "OS architecture", System.getProperty("os.arch"));
        value(report, "Logical processors", base.getAvailableProcessors());
        value(report, "System load average", decimal(base.getSystemLoadAverage()));
        if (base instanceof OperatingSystemMXBean operatingSystem) {
            value(report, "Process CPU load", percent(operatingSystem.getProcessCpuLoad()));
            value(report, "System CPU load", percent(operatingSystem.getCpuLoad()));
            value(report, "Process CPU time", duration(operatingSystem.getProcessCpuTime() / 1_000_000L));
            value(report, "Committed virtual memory", bytes(operatingSystem.getCommittedVirtualMemorySize()));
            value(report, "Physical memory total", bytes(operatingSystem.getTotalMemorySize()));
            value(report, "Physical memory free", bytes(operatingSystem.getFreeMemorySize()));
            value(report, "Swap total", bytes(operatingSystem.getTotalSwapSpaceSize()));
            value(report, "Swap free", bytes(operatingSystem.getFreeSwapSpaceSize()));
        }
        if (base instanceof UnixOperatingSystemMXBean unix) {
            value(report, "Open file descriptors", unix.getOpenFileDescriptorCount());
            value(report, "Maximum file descriptors", unix.getMaxFileDescriptorCount());
        }
    }

    private static void appendThreads(StringBuilder report) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        section(report, "Threads");
        value(report, "Live", threads.getThreadCount());
        value(report, "Daemon", threads.getDaemonThreadCount());
        value(report, "Peak", threads.getPeakThreadCount());
        value(report, "Total started", threads.getTotalStartedThreadCount());
        Map<Thread.State, Integer> states = new EnumMap<>(Thread.State.class);
        ThreadInfo[] information = threads.getThreadInfo(threads.getAllThreadIds(), true, true);
        for (ThreadInfo thread : information) {
            if (thread != null) {
                states.merge(thread.getThreadState(), 1, Integer::sum);
            }
        }
        for (Thread.State state : Thread.State.values()) {
            value(report, "State " + state.name().toLowerCase(Locale.ROOT), states.getOrDefault(state, 0));
        }
        long[] deadlocked = threads.findDeadlockedThreads();
        value(report, "Deadlocked", deadlocked == null ? 0 : deadlocked.length);
        value(report, "Thread CPU time supported", threads.isThreadCpuTimeSupported());
        value(report, "Thread CPU time enabled", threads.isThreadCpuTimeSupported() && threads.isThreadCpuTimeEnabled());
        for (ThreadInfo thread : information) {
            if (thread == null) {
                continue;
            }
            report.append("- id=").append(thread.getThreadId())
                    .append(" | name=").append(sanitize(thread.getThreadName()))
                    .append(" | state=").append(thread.getThreadState())
                    .append(" | blockedCount=").append(thread.getBlockedCount())
                    .append(" | blockedTime=").append(duration(thread.getBlockedTime()))
                    .append(" | waitedCount=").append(thread.getWaitedCount())
                    .append(" | waitedTime=").append(duration(thread.getWaitedTime()))
                    .append(" | suspended=").append(thread.isSuspended())
                    .append(" | native=").append(thread.isInNative())
                    .append(" | lock=").append(sanitize(Objects.toString(thread.getLockInfo(), "none")))
                    .append(" | lockOwner=").append(sanitize(Objects.toString(thread.getLockOwnerName(), "none")));
            if (threads.isThreadCpuTimeSupported() && threads.isThreadCpuTimeEnabled()) {
                report.append(" | cpuTime=").append(duration(threads.getThreadCpuTime(thread.getThreadId()) / 1_000_000L))
                        .append(" | userTime=").append(duration(threads.getThreadUserTime(thread.getThreadId()) / 1_000_000L));
            }
            report.append('\n');
            for (StackTraceElement frame : thread.getStackTrace()) {
                report.append("    at ").append(sanitize(frame.toString())).append('\n');
            }
            for (MonitorInfo monitor : thread.getLockedMonitors()) {
                report.append("    locked-monitor ").append(sanitize(monitor.toString()))
                        .append(" at depth ").append(monitor.getLockedStackDepth()).append('\n');
            }
            for (LockInfo synchronizer : thread.getLockedSynchronizers()) {
                report.append("    locked-synchronizer ").append(sanitize(synchronizer.toString())).append('\n');
            }
        }
    }

    private static void appendGarbageCollectors(StringBuilder report) {
        section(report, "Garbage collectors");
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            value(report, collector.getName() + " collections", collector.getCollectionCount());
            value(report, collector.getName() + " time", duration(collector.getCollectionTime()));
        }
    }

    private static void appendBufferPools(StringBuilder report) {
        section(report, "Buffer pools");
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            value(report, pool.getName() + " count", pool.getCount());
            value(report, pool.getName() + " used", bytes(pool.getMemoryUsed()));
            value(report, pool.getName() + " capacity", bytes(pool.getTotalCapacity()));
        }
    }

    private static void appendStorage(StringBuilder report, String label, Path path) {
        section(report, label);
        try {
            FileStore store = Files.getFileStore(path);
            value(report, "Type", store.type());
            value(report, "Total", bytes(store.getTotalSpace()));
            value(report, "Usable", bytes(store.getUsableSpace()));
            value(report, "Unallocated", bytes(store.getUnallocatedSpace()));
        } catch (IOException | RuntimeException exception) {
            value(report, "Status", "unavailable (" + exception.getClass().getSimpleName() + ")");
        }
    }

    private static void appendManagedFiles(StringBuilder report, Path dataDirectory) {
        section(report, "Rift managed files");
        Path root = dataDirectory.toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        Path config = root.resolve("config.toml");
        if (Files.exists(config, LinkOption.NOFOLLOW_LINKS)) {
            files.add(config);
        }
        collectFiles(report, root, root.resolve("languages"), ".toml", files);
        collectFiles(report, root, root.resolve("worlds"), ".toml", files);
        collectFiles(report, root, root.resolve("trash"), ".toml", files);
        files.sort(Comparator.comparing(path -> root.relativize(path).toString(), String.CASE_INSENSITIVE_ORDER));
        value(report, "Entries", files.size());
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            report.append("- ").append(sanitize(relative));
            if (!file.startsWith(root) || containsSymbolicLink(root, file)) {
                report.append(" | state=invalid or symbolic-link path\n");
                continue;
            }
            try {
                report.append(" | size=").append(bytes(Files.size(file)))
                        .append(" | modified=").append(Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant())
                        .append(" | sha256=").append(sha256(file))
                        .append('\n');
            } catch (IOException | NoSuchAlgorithmException | RuntimeException exception) {
                report.append(" | state=unavailable (").append(exception.getClass().getSimpleName()).append(")\n");
            }
        }
    }

    private static void collectFiles(StringBuilder report, Path root, Path directory, String extension, List<Path> files) {
        if (!directory.startsWith(root)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) {
            return;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            entries.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .forEach(files::add);
        } catch (IOException | RuntimeException exception) {
            report.append("File inventory error for ")
                    .append(sanitize(root.relativize(directory).toString().replace('\\', '/')))
                    .append(": ").append(exception.getClass().getSimpleName()).append('\n');
        }
    }

    private static boolean containsSymbolicLink(Path root, Path target) {
        if (!target.startsWith(root)) {
            return true;
        }
        Path cursor = root;
        for (Path segment : root.relativize(target)) {
            cursor = cursor.resolve(segment);
            if (Files.isSymbolicLink(cursor)) {
                return true;
            }
        }
        return false;
    }

    private static void appendArtifact(StringBuilder report, Path codeSource) {
        section(report, "Rift artifact");
        if (codeSource == null) {
            value(report, "Status", "code source unavailable");
            return;
        }
        value(report, "Type", Files.isRegularFile(codeSource) ? "jar" : Files.isDirectory(codeSource) ? "classes directory" : "unknown");
        if (!Files.isRegularFile(codeSource)) {
            return;
        }
        try {
            value(report, "Filename", codeSource.getFileName());
            value(report, "Size", bytes(Files.size(codeSource)));
            value(report, "Last modified", Files.getLastModifiedTime(codeSource, LinkOption.NOFOLLOW_LINKS).toInstant());
            value(report, "SHA-256", sha256(codeSource));
        } catch (IOException | NoSuchAlgorithmException exception) {
            value(report, "Status", "artifact inspection unavailable (" + exception.getClass().getSimpleName() + ")");
        }
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void memoryUsage(StringBuilder report, String label, MemoryUsage usage) {
        if (usage == null) {
            value(report, label, "unavailable");
            return;
        }
        value(report, label, "used=" + bytes(usage.getUsed()) + ", committed=" + bytes(usage.getCommitted())
                + ", maximum=" + bytes(usage.getMax()));
    }

    private static void section(StringBuilder report, String name) {
        if (!report.isEmpty()) {
            report.append('\n');
        }
        report.append("== ").append(sanitize(name)).append(" ==\n");
    }

    private static void value(StringBuilder report, String name, Object value) {
        report.append(sanitize(name)).append(": ").append(sanitize(Objects.toString(value, "unavailable"))).append('\n');
    }

    private static String bytes(long value) {
        if (value < 0L) {
            return "unavailable";
        }
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        double scaled = value;
        int unit = 0;
        while (scaled >= 1024D && unit < units.length - 1) {
            scaled /= 1024D;
            unit++;
        }
        return String.format(Locale.ROOT, "%.2f %s (%d bytes)", scaled, units[unit], value);
    }

    private static String percent(double value) {
        return value < 0D || !Double.isFinite(value)
                ? "unavailable"
                : String.format(Locale.ROOT, "%.2f%%", value * 100D);
    }

    private static String decimal(double value) {
        return value < 0D || !Double.isFinite(value)
                ? "unavailable"
                : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String duration(long millis) {
        if (millis < 0L) {
            return "unavailable";
        }
        Duration duration = Duration.ofMillis(millis);
        return duration.toDaysPart() + "d " + duration.toHoursPart() + "h " + duration.toMinutesPart()
                + "m " + duration.toSecondsPart() + "s " + duration.toMillisPart() + "ms";
    }

    private static String instant(long epochMillis) {
        return epochMillis <= 0L ? "never" : Instant.ofEpochMilli(epochMillis).toString();
    }

    private static String sanitize(String value) {
        String source = Objects.requireNonNullElse(value, "unavailable");
        StringBuilder sanitized = new StringBuilder(source.length());
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            sanitized.append(Character.isISOControl(character) ? ' ' : character);
        }
        return sanitized.toString().trim();
    }
}
