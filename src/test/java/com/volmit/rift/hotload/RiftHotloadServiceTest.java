package com.volmit.rift.hotload;

import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.config.RiftConfig;
import com.volmit.rift.config.RiftConfigManager;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import com.volmit.rift.storage.TrashStore;
import com.volmit.rift.storage.WorldProfileStore;
import com.volmit.rift.world.WorldInventory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

final class RiftHotloadServiceTest {
    @TempDir
    Path directory;

    @Test
    void watcherPublishesEnglishAfterPersonalFileDeletionWithoutRecreatingIt() throws Exception {
        Rift plugin = mock(Rift.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.isEnabled()).thenReturn(true);
        RiftConfig settings = new RiftConfig().normalize();
        settings.setHotReloadPollMillis(250L);
        settings.setHotReloadCooldownMillis(250L);
        RiftConfigManager config = mock(RiftConfigManager.class);
        when(config.get()).thenReturn(settings);
        when(config.file()).thenReturn(Files.writeString(directory.resolve("config.toml"), "language = 'en_US'\n").toFile());
        WorldProfileStore profiles = mock(WorldProfileStore.class);
        when(profiles.directory()).thenReturn(Files.createDirectory(directory.resolve("worlds")).toFile());
        when(profiles.files()).thenReturn(List.of());
        TrashStore trash = mock(TrashStore.class);
        when(trash.directory()).thenReturn(Files.createDirectory(directory.resolve("trash")).toFile());
        when(trash.files()).thenReturn(List.of());
        AtomicReference<Runnable> poll = new AtomicReference<>();
        try (RiftLocalization language = new RiftLocalization(plugin, () -> settings, directory.resolve("languages").toFile());
             MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.runAsync(any(Plugin.class), any(Runnable.class), anyLong()))
                    .thenAnswer(invocation -> {
                        poll.set(invocation.getArgument(1));
                        return true;
                    });
            assertThat(language.loadInitial()).isTrue();
            Path french = directory.resolve("languages/fr_FR.toml");
            Files.writeString(french, "[runtime]\nprefix = 'PERSONAL'\n");
            language.refreshAvailableLocales();
            language.initializeSelections(settings::getLanguage, (locale, snapshot) -> {
                throw new AssertionError("File deletion cannot change the server language");
            });
            UUID playerId = UUID.randomUUID();
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(playerId);
            language.selections().selectPlayer(playerId, "fr_FR").get(5, TimeUnit.SECONDS);
            MessageArgs arguments = MessageArgs.builder().untrusted("world", "test").build();
            assertThat(language.text(player, RiftMessages.CREATED, arguments).plain()).startsWith("PERSONAL");
            try (RiftHotloadService hotload = new RiftHotloadService(plugin, config, language,
                    profiles, trash, mock(WorldInventory.class))) {
                hotload.start();
                Files.delete(french);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline
                        && language.text(player, RiftMessages.CREATED, arguments).plain().startsWith("PERSONAL")) {
                    poll.get().run();
                    Thread.sleep(50L);
                }
                assertThat(language.text(player, RiftMessages.CREATED, arguments).plain())
                        .isEqualTo("Rift › Created and managed test.");
                assertThat(language.selections().playerLocale(playerId)).contains("fr_FR");
                assertThat(settings.getLanguage()).isEqualTo("en_US");
                assertThat(french).doesNotExist();
            }
        }
    }
}
