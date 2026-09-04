package com.volmit.rift.localization;

import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.VolmitLocales;
import com.volmit.rift.config.RiftConfig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class RiftLocalizationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsOnlyEditableEnglishAndDiscoversRemoteLocales() throws Exception {
        RiftConfig config = new RiftConfig().normalize();
        RiftLocalization localization = localization(config);
        Path languages = temporaryDirectory.resolve("languages");

        assertThat(localization.loadInitial()).isTrue();
        assertThat(languages.resolve("en_US.toml")).isRegularFile();
        assertThat(localization.file().getName()).isEqualTo("en_US.toml");
        assertThat(localization.remoteCatalogReference()).contains("main");
        assertThat(localization.hasRemoteCatalogLocale("fr_FR")).isTrue();
        assertThat(localization.hasRemoteCatalogLocale("en_US")).isFalse();
        assertThat(localization.availableLocales()).containsExactlyElementsOf(
                VolmitLocales.all().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        assertThat(languageFiles(languages)).containsExactly("en_US.toml");
        assertThat(Files.readString(languages.resolve("en_US.toml")))
                .contains("[runtime]", "prefix = \"&5&lRIFT&r &8›&r \"")
                .contains("{prefix}=the global runtime.prefix value")
                .contains("[director.runtime]");
    }

    @Test
    void preparingRemoteLocaleMaterializesNoUnselectedLanguageFiles() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        RiftLocalization.PreparedLanguage prepared = localization.prepare("fr_FR");

        assertThat(prepared.selectionReady()).isFalse();
        assertThat(prepared.file()).doesNotExist();
        assertThat(languageFiles(temporaryDirectory.resolve("languages"))).isEmpty();
    }

    @Test
    void preservesSparseOperatorFilesAndFallsBackToEnglish() throws Exception {
        Path languages = temporaryDirectory.resolve("languages");
        Files.createDirectories(languages);
        Path french = languages.resolve("fr_FR.toml");
        String content = "[runtime]\nprefix = \"&6PORTAILS&r &8›&r \"\n";
        Files.writeString(french, content, StandardCharsets.UTF_8);
        RiftLocalization localization = localization(new RiftConfig().normalize());

        RiftLocalization.PreparedLanguage prepared = localization.prepare("fr_FR");
        localization.install(prepared);

        assertThat(prepared.selectionReady()).isTrue();
        assertThat(localization.activeLocale()).isEqualTo("fr_FR");
        assertThat(localization.text(RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build()).plain())
                .isEqualTo("PORTAILS › Created and managed test.");
        assertThat(Files.readString(french)).isEqualTo(content);
    }

    @Test
    void prefixIsHotReloadableAndMenuTextRemainsUnprefixed() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        Path english = temporaryDirectory.resolve("languages").resolve("en_US.toml");
        String raw = Files.readString(english)
                .replace("prefix = \"&5&lRIFT&r &8›&r \"", "prefix = \"&b&lPORTAL&r &8›&r \"");

        localization.install(localization.prepareSnapshot("en_US", raw));

        assertThat(localization.text(RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build()).plain())
                .isEqualTo("PORTAL › Created and managed test.");
        assertThat(localization.text(RiftMessages.COMMAND_CREATE).plain())
                .isEqualTo("Create and manage a new world");
        assertThat(localization.directorResolver().resolve(RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build()))
                .isEqualTo("Created and managed test.");
    }

    @Test
    void removingPrefixFromOneMessageDoesNotChangeOtherMessages() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        Path english = temporaryDirectory.resolve("languages").resolve("en_US.toml");
        String raw = Files.readString(english)
                .replace("created = \"{prefix}&aCreated and managed &f{world}&a.\"",
                        "created = \"&aCreated and managed &f{world}&a.\"");

        localization.install(localization.prepareSnapshot("en_US", raw));

        assertThat(localization.text(RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build()).plain())
                .isEqualTo("Created and managed test.");
        assertThat(localization.text(RiftMessages.LOADED,
                MessageArgs.builder().untrusted("world", "test").build()).plain())
                .startsWith("RIFT › ");
    }

    @Test
    void blankPrefixDisablesItAndCallersCannotOverrideIt() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        Path english = temporaryDirectory.resolve("languages").resolve("en_US.toml");
        String raw = Files.readString(english)
                .replace("prefix = \"&5&lRIFT&r &8›&r \"", "prefix = \"\"");
        localization.install(localization.prepareSnapshot("en_US", raw));

        assertThat(localization.text(RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build()).plain())
                .isEqualTo("Created and managed test.");
        assertThatThrownBy(() -> localization.text(RiftMessages.CREATED, MessageArgs.builder()
                .trusted("prefix", "override")
                .untrusted("world", "test")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("managed by Rift");
    }

    @Test
    void rejectsInvalidSelectedFilesWithoutReplacingActiveLanguage() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        Path french = temporaryDirectory.resolve("languages").resolve("fr_FR.toml");
        Files.writeString(french,
                "[rift.message]\ncreated = \"{prefix}&cMissing the required world placeholder\"\n",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> localization.prepare("fr_FR"))
                .isInstanceOf(IOException.class);
        assertThat(localization.activeLocale()).isEqualTo("en_US");
        assertThat(Files.readString(french)).contains("Missing the required world placeholder");
    }

    @Test
    void ignoresUnsafeAndNonTomlFiles() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        Path languages = temporaryDirectory.resolve("languages");
        Files.writeString(languages.resolve("notes.txt"), "ignored");
        Files.writeString(languages.resolve("bad.locale.toml"), "ignored");
        Files.writeString(languages.resolve("pirate.toml"), "");

        localization.refreshAvailableLocales();

        assertThat(localization.availableLocales()).contains("pirate");
        assertThat(localization.availableLocales()).doesNotContain("bad.locale");
        assertThat(localization.files()).extracting(File::getName)
                .containsExactly("en_US.toml", "pirate.toml");
        assertThatThrownBy(() -> localization.file("../outside"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("safe name");
    }

    @Test
    void editorPreservesHeaderAndUnknownValues() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        Path english = temporaryDirectory.resolve("languages").resolve("en_US.toml");
        String original = Files.readString(english) + "\n[future]\nenabled = true\n";
        Files.writeString(english, original, StandardCharsets.UTF_8);

        RiftLocalization.PreparedLanguage prepared = localization.updateMessage(
                "en_US",
                RiftMessages.PERMISSION_DENIED.id(),
                "{prefix}&cEdited &f{permission}&c."
        );
        localization.install(prepared);
        String edited = Files.readString(english);

        assertThat(edited)
                .startsWith("# Rift language: en_US")
                .contains("permission_denied = \"{prefix}&cEdited &f{permission}&c.\"")
                .contains("[future]\nenabled = true");
        assertThat(localization.text(RiftMessages.PERMISSION_DENIED,
                MessageArgs.builder().untrusted("permission", "rift.test").build()).plain())
                .contains("RIFT › Edited rift.test.");
    }

    @Test
    void editorExposesSortedMessagesAndAllAllowedPlaceholders() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        RiftLocalization.PreparedLanguage prepared = localization.updateMessage(
                "en_US",
                RiftMessages.CREATED.id(),
                "&aCreated and managed &f{world}&a."
        );

        List<RiftLocalization.EditableMessage> messages = localization.editableMessages(prepared);
        List<String> ids = messages.stream().map(RiftLocalization.EditableMessage::id).toList();
        RiftLocalization.EditableMessage created = messages.stream()
                .filter(message -> message.id().equals(RiftMessages.CREATED.id()))
                .findFirst()
                .orElseThrow();

        assertThat(ids).hasSize(RiftMessages.catalog().keys().size()).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        assertThat(created.effectiveValue()).doesNotContain("{prefix}");
        assertThat(created.placeholders()).containsExactlyInAnyOrder("prefix", "world");
    }

    @Test
    void invalidEditorUpdatePreservesFileAndActiveSnapshot() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        Path english = temporaryDirectory.resolve("languages").resolve("en_US.toml");
        byte[] before = Files.readAllBytes(english);
        String activeBefore = localization.text(
                RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build()
        ).plain();

        assertThatThrownBy(() -> localization.updateMessage(
                "en_US",
                RiftMessages.CREATED.id(),
                "{prefix}&cMissing the required world placeholder"
        )).isInstanceOf(IOException.class);

        assertThat(Files.readAllBytes(english)).isEqualTo(before);
        assertThat(localization.text(
                RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build()
        ).plain()).isEqualTo(activeBefore);
    }

    @Test
    void editorNotifiesHotReloadWithExactlyPersistedContent() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        AtomicInteger notifications = new AtomicInteger();
        AtomicReference<File> notifiedFile = new AtomicReference<>();
        AtomicReference<String> notifiedContent = new AtomicReference<>();
        localization.setSelfWriteListener((file, content) -> {
            notifications.incrementAndGet();
            notifiedFile.set(file);
            notifiedContent.set(content);
        });

        localization.updateMessage(
                "en_US",
                RiftMessages.PERMISSION_DENIED.id(),
                "{prefix}&cEdited &f{permission}&c."
        );

        assertThat(notifications).hasValue(1);
        assertThat(notifiedFile.get()).isEqualTo(localization.file("en_US"));
        assertThat(notifiedContent.get()).isEqualTo(Files.readString(localization.file("en_US").toPath()));
    }

    @Test
    void acceptsUnknownFutureKeysInCompleteRemoteContent() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        String latest = RiftLocalization.referenceToml() + "\n[future]\nmessage = \"new value\"\n";

        localization.validateDownloadedContent("fr_FR", latest);
    }

    @Test
    void acceptsSparseRemoteContentAndCompletesItInMemory() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());

        localization.validateDownloadedContent("fr_FR", "[runtime]\nprefix = \"&6PORTAIL&r &8›&r \"\n");
    }

    @Test
    void keepsPersistentPlayerLanguageSeparateFromServerDefault() throws Exception {
        RiftConfig config = new RiftConfig().normalize();
        RiftLocalization localization = localization(config);
        assertThat(localization.loadInitial()).isTrue();
        Path french = temporaryDirectory.resolve("languages").resolve("fr_FR.toml");
        Files.writeString(french, "[runtime]\nprefix = \"&6PORTAIL&r &8›&r \"\n", StandardCharsets.UTF_8);
        localization.refreshAvailableLocales();
        localization.initializeSelections(
                config::getLanguage,
                (locale, snapshot) -> {
                    config.setLanguage(locale);
                    localization.install(new RiftLocalization.PreparedLanguage(
                            locale, localization.file(locale), snapshot, true));
                }
        );
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        localization.selections().selectPlayer(playerId, "fr_FR").join();

        MessageArgs arguments = MessageArgs.builder().untrusted("world", "test").build();
        assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                .isEqualTo("PORTAIL › Created and managed test.");
        assertThat(localization.text(RiftMessages.CREATED, arguments).plain())
                .isEqualTo("RIFT › Created and managed test.");
        assertThat(temporaryDirectory.resolve("languages").resolve("language-preferences.properties"))
                .isRegularFile();

        localization.selections().clearPlayer(playerId).join();
        assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                .isEqualTo("RIFT › Created and managed test.");
        localization.close();
    }

    private RiftLocalization localization(RiftConfig config) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(RiftLocalizationTest.class.getName()));
        return new RiftLocalization(plugin, () -> config, temporaryDirectory.resolve("languages").toFile());
    }

    private static List<String> languageFiles(Path directory) throws Exception {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".toml"))
                    .sorted()
                    .toList();
        }
    }
}
