package com.volmit.rift.world;

import com.volmit.rift.storage.WorldDirectoryResolver;
import com.volmit.rift.storage.WorldNamePolicy;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ManagedWorldStartupReconcilerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void retiresConclusiveMissingProfile() throws Exception {
        TestContext context = context(false);
        context.profiles().save(profile("testing100"));

        assertThat(context.reconciler().reconcile()).isEmpty();

        assertThat(context.profiles().find("testing100")).isEmpty();
        try (Stream<Path> retired = Files.list(context.profileDirectory().resolve("retired"))) {
            assertThat(retired).anySatisfy(path -> assertThat(path.getFileName().toString())
                    .endsWith("-testing100.toml"));
        }
        assertThat(context.logs()).anySatisfy(record -> assertThat(record.getMessage())
                .contains("Stopped managing testing100"));
    }

    @Test
    void preservesProfileWhenStorageCannotBeSafelyChecked() throws Exception {
        TestContext context = context(false);
        WorldProfile profile = profile("testing100");
        context.profiles().save(profile);
        profile.setDirectory("../testing100");

        assertThat(context.reconciler().reconcile()).isEmpty();

        assertThat(context.profiles().find("testing100")).isPresent();
        assertThat(context.logs()).anySatisfy(record -> {
            assertThat(record.getLevel()).isEqualTo(Level.SEVERE);
            assertThat(record.getMessage()).contains("its profile remains managed");
            assertThat(record.getThrown()).isNotNull();
        });
    }

    @Test
    void preservesProfileWhenAStorageEntryExistsButIsNotAValidWorld() throws Exception {
        TestContext context = context(false);
        context.profiles().save(profile("testing100"));
        Files.createDirectories(temporaryDirectory.resolve("server/testing100"));

        assertThat(context.reconciler().reconcile()).isEmpty();

        assertThat(context.profiles().find("testing100")).isPresent();
        assertThat(context.logs()).anySatisfy(record -> {
            assertThat(record.getLevel()).isEqualTo(Level.SEVERE);
            assertThat(record.getMessage()).contains("its profile remains managed");
        });
    }

    @Test
    void preservesProtectedProfileWhenItsStorageIsMissing() throws Exception {
        TestContext context = context(false);
        WorldProfile profile = profile("testing100");
        profile.setProtectedWorld(true);
        context.profiles().save(profile);

        assertThat(context.reconciler().reconcile()).isEmpty();

        assertThat(context.profiles().find("testing100")).isPresent();
        assertThat(context.logs()).anySatisfy(record -> assertThat(record.getMessage())
                .contains("Protected managed world testing100"));
    }

    @Test
    void preservesLoadedManagedWorldWithoutInspectingItsDirectory() throws Exception {
        TestContext context = context(true);
        WorldProfile profile = profile("testing100");
        context.profiles().save(profile);

        assertThat(context.reconciler().reconcile()).containsExactly(profile);
        assertThat(context.profiles().find("testing100")).isPresent();
    }

    @Test
    void keepsPresentProfileAvailableForConfiguredAutomaticLoading() throws Exception {
        TestContext context = context(false);
        WorldProfile profile = profile("testing100");
        context.profiles().save(profile);
        Path world = temporaryDirectory.resolve("server/testing100");
        Files.createDirectories(world);
        Files.createFile(world.resolve("level.dat"));

        assertThat(context.reconciler().reconcile()).containsExactly(profile);
        assertThat(context.profiles().find("testing100")).isPresent();
    }

    private TestContext context(boolean loaded) throws Exception {
        Path worldContainer = temporaryDirectory.resolve("server");
        Path primary = worldContainer.resolve("world");
        Path profileDirectory = temporaryDirectory.resolve("plugins/Rift/worlds");
        Files.createDirectories(primary);
        Files.createFile(primary.resolve("level.dat"));
        WorldNamePolicy names = new WorldNamePolicy(worldContainer);
        WorldDirectoryResolver directories = new WorldDirectoryResolver(worldContainer, primary, names);
        Plugin plugin = mock(Plugin.class);
        Logger logger = Logger.getAnonymousLogger();
        ArrayList<LogRecord> logs = new ArrayList<>();
        logger.setUseParentHandlers(false);
        logger.addHandler(new CapturingHandler(logs));
        when(plugin.getLogger()).thenReturn(logger);
        WorldProfileStore profiles = new WorldProfileStore(plugin, profileDirectory.toFile(), names);
        assertThat(profiles.loadAll()).isTrue();
        WorldInventory inventory = new WorldInventory(plugin, directories, profiles);
        ManagedWorldStartupReconciler reconciler = new ManagedWorldStartupReconciler(
                new ManagedWorldStartupReconciler.Options(
                        logger,
                        directories,
                        profiles,
                        inventory,
                        ignored -> loaded
                )
        );
        return new TestContext(reconciler, profiles, profileDirectory, logs);
    }

    private static WorldProfile profile(String name) {
        WorldProfile profile = new WorldProfile();
        profile.setName(name);
        return profile;
    }

    private record TestContext(
            ManagedWorldStartupReconciler reconciler,
            WorldProfileStore profiles,
            Path profileDirectory,
            List<LogRecord> logs
    ) {
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records;

        private CapturingHandler(List<LogRecord> records) {
            this.records = records;
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
