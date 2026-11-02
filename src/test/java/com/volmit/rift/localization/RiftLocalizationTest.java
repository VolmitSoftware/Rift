package com.volmit.rift.localization;

import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.BukkitLanguageMessages;
import art.arcane.volmlib.util.localization.LanguageMeta;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.VolmitLocales;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.volmit.rift.command.RiftCommandService;
import com.volmit.rift.config.RiftConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
        assertThat(localization.sourceFailure()).isEmpty();
        assertThat(localization.sourceDescription()).startsWith("VolmitSoftware/Rift@");
        assertThat(localization.isRepositoryLocale("fr_FR")).isTrue();
        assertThat(localization.isRepositoryLocale("ja-JP")).isTrue();
        assertThat(localization.isRepositoryLocale("en_US")).isFalse();
        assertThat(languages.resolve(".upstream").resolve("en_US.toml")).isRegularFile();
        assertThat(localization.availableLocales()).containsExactlyElementsOf(
                VolmitLocales.all().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        assertThat(languageFiles(languages)).containsExactly("en_US.toml");
        assertThat(Files.readString(languages.resolve("en_US.toml")))
                .contains("[runtime]", "prefix = \"<bold><gradient:#6f2dbd:#d16ba5>Rift</gradient></bold>\"")
                .contains("{prefix}  the styled name from runtime.prefix")
                .contains("[director.runtime.error]");
    }

    @Test
    void preparingRemoteLocaleAlsoCreatesEditableEnglish() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        RiftLocalization.PreparedLanguage prepared = localization.prepare("fr_FR");

        assertThat(prepared.selectionReady()).isFalse();
        assertThat(prepared.file()).doesNotExist();
        assertThat(languageFiles(temporaryDirectory.resolve("languages"))).containsExactly("en_US.toml");
    }

    @Test
    void preservesSparseOperatorFilesAndFallsBackToEnglish() throws Exception {
        Path languages = temporaryDirectory.resolve("languages");
        Files.createDirectories(languages);
        Path french = languages.resolve("fr_FR.toml");
        String content = "[runtime]\nprefix = \"&6PORTAILS\"\n";
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
                .replace("prefix = \"<bold><gradient:#6f2dbd:#d16ba5>Rift</gradient></bold>\"", "prefix = \"&b&lPORTAL\"");

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
                .replace("created = \"{prefix}&r &7› &7Created and managed &a{world}&7.\"",
                        "created = \"&aCreated and managed &f{world}&a.\"");

        localization.install(localization.prepareSnapshot("en_US", raw));

        assertThat(localization.text(RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build()).plain())
                .isEqualTo("Created and managed test.");
        assertThat(localization.text(RiftMessages.LOADED,
                MessageArgs.builder().untrusted("world", "test").build()).plain())
                .startsWith("Rift › ");
    }

    @Test
    void blankPrefixLeavesTheTemplateSeparatorAndCallersCannotOverrideIt() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        Path english = temporaryDirectory.resolve("languages").resolve("en_US.toml");
        String raw = Files.readString(english)
                .replace("prefix = \"<bold><gradient:#6f2dbd:#d16ba5>Rift</gradient></bold>\"", "prefix = \"\"");
        localization.install(localization.prepareSnapshot("en_US", raw));

        assertThat(localization.text(RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build()).plain())
                .isEqualTo(" › Created and managed test.");
        assertThatThrownBy(() -> localization.text(RiftMessages.CREATED, MessageArgs.builder()
                .trusted("prefix", "override")
                .untrusted("world", "test")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("managed by Rift");
    }

    @Test
    void invalidEntriesUseEnglishWhileKeepingTheSelectedLocale() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        Path french = temporaryDirectory.resolve("languages").resolve("fr_FR.toml");
        Files.writeString(french,
                "[rift.message]\ncreated = \"{prefix}&cMissing the required world placeholder\"\n",
                StandardCharsets.UTF_8);

        localization.install(localization.prepare("fr_FR"));
        assertThat(localization.activeLocale()).isEqualTo("fr_FR");
        assertThat(localization.text(RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build()).plain())
                .contains("Created and managed test.");
        assertThat(Files.readString(french)).contains("Missing the required world placeholder");
    }

    @Test
    void partialDownloadsAcceptMissingAndInvalidTranslations() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        localization.validateContent("fr_FR", "");
        localization.validateContent("fr_FR", "[runtime]\nprefix = 42\n");
        localization.validateContent("fr_FR", "[rift.message]\ncreated = \"{wrong}\"\n");
    }

    @Test
    void malformedLanguageSnapshotRetainsTheLastValidMessages() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        localization.install(localization.prepareSnapshot("en_US", "[runtime]\nprefix = \"PORTAL\"\n"));
        assertThat(localization.reloadSnapshot(localization.file(), "[rift.message]\ncreated = \"unterminated")).isFalse();
        assertThat(localization.text(RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build()).plain())
                .isEqualTo("PORTAL › Created and managed test.");
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
                "{prefix}&r &7› &7&cEdited &f{permission}&c."
        );
        localization.install(prepared);
        String edited = Files.readString(english);

        assertThat(edited)
                .startsWith("# Rift — en_US")
                .contains("permission_denied = \"{prefix}&r &7› &7&cEdited &f{permission}&c.\"")
                .contains("[future]\nenabled = true");
        assertThat(localization.text(RiftMessages.PERMISSION_DENIED,
                MessageArgs.builder().untrusted("permission", "rift.test").build()).plain())
                .contains("Rift › Edited rift.test.");
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
                "{prefix}&r &7› &7&cEdited &f{permission}&c."
        );

        assertThat(notifications).hasValue(1);
        assertThat(notifiedFile.get()).isEqualTo(localization.file("en_US"));
        assertThat(notifiedContent.get()).isEqualTo(Files.readString(localization.file("en_US").toPath()));
    }

    @Test
    void acceptsUnknownFutureKeysInCompleteRemoteContent() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        String latest = RiftLocalization.referenceToml() + "\n[future]\nmessage = \"new value\"\n";

        localization.validateContent("fr_FR", latest);
    }

    @Test
    void acceptsSparseRemoteContentAndCompletesItInMemory() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());

        localization.validateContent("fr_FR", "[runtime]\nprefix = \"&6PORTAIL\"\n");
    }

    @Test
    void keepsPersistentPlayerLanguageSeparateFromServerDefault() throws Exception {
        RiftConfig config = new RiftConfig().normalize();
        RiftLocalization localization = localization(config);
        assertThat(localization.loadInitial()).isTrue();
        Path french = temporaryDirectory.resolve("languages").resolve("fr_FR.toml");
        Files.writeString(french, "[runtime]\nprefix = \"&6PORTAIL\"\n", StandardCharsets.UTF_8);
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
                .isEqualTo("Rift › Created and managed test.");
        assertThat(temporaryDirectory.resolve("languages").resolve("language-preferences.properties"))
                .isRegularFile();

        localization.selections().clearPlayer(playerId).join();
        assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                .isEqualTo("Rift › Created and managed test.");
        localization.close();
    }

    @Test
    void personalLanguageReloadRefreshesOnlyItsSnapshotAndRetainsValidMessagesOnFailure() throws Exception {
        RiftConfig config = new RiftConfig().normalize();
        try (RiftLocalization localization = localization(config)) {
            assertThat(localization.loadInitial()).isTrue();
            Path french = temporaryDirectory.resolve("languages").resolve("fr_FR.toml");
            String initial = "[runtime]\nprefix = \"PORTAIL\"\n"
                    + "[rift.message]\ncreated = \"{prefix} Initial {world}\"\n";
            Files.writeString(french, initial);
            localization.refreshAvailableLocales();
            localization.initializeSelections(config::getLanguage, (locale, snapshot) -> {
                throw new AssertionError("A personal language edit cannot change the server default");
            });
            UUID playerId = UUID.randomUUID();
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(playerId);
            localization.selections().selectPlayer(playerId, "fr_FR").get(5, TimeUnit.SECONDS);
            MessageArgs arguments = MessageArgs.builder().untrusted("world", "test").build();
            assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                    .isEqualTo("PORTAIL Initial test");

            String edited = initial.replace("Initial", "Modifié");
            Files.writeString(french, edited);
            assertThat(localization.reloadSnapshot(french.toFile(), edited)).isTrue();
            assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                    .isEqualTo("PORTAIL Modifié test");

            String partial = "[runtime]\nprefix = \"PORTAIL\"\n";
            Files.writeString(french, partial);
            assertThat(localization.reloadSnapshot(french.toFile(), partial)).isTrue();
            assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                    .isEqualTo("PORTAIL › Created and managed test.");

            String malformed = partial + "[rift.message]\ncreated = \"unterminated";
            Files.writeString(french, malformed);
            assertThat(localization.reloadSnapshot(french.toFile(), malformed)).isFalse();
            assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                    .isEqualTo("PORTAIL › Created and managed test.");
            assertThat(Files.readString(french)).isEqualTo(malformed);
            assertThat(localization.text(RiftMessages.CREATED, arguments).plain())
                    .isEqualTo("Rift › Created and managed test.");
            assertThat(config.getLanguage()).isEqualTo("en_US");
            assertThat(localization.activeLocale()).isEqualTo("en_US");
            assertThat(localization.selections().playerLocale(playerId)).contains("fr_FR");

            Path english = temporaryDirectory.resolve("languages").resolve("en_US.toml");
            String serverEdit = "[runtime]\nprefix = \"SERVER\"\n";
            Files.writeString(english, serverEdit);
            assertThat(localization.reloadSnapshot(english.toFile(), serverEdit)).isTrue();
            assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                    .isEqualTo("PORTAIL › Created and managed test.");

            Files.delete(french);
            assertThat(localization.isLanguageFile(french.toFile())).isTrue();
            assertThat(localization.reloadSnapshot(french.toFile(), null)).isTrue();
            assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                    .isEqualTo("Rift › Created and managed test.");
            assertThat(localization.text(RiftMessages.CREATED, arguments).plain())
                    .isEqualTo("SERVER › Created and managed test.");
            assertThat(localization.reloadSnapshot(english.toFile(), serverEdit)).isTrue();
            assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                    .isEqualTo("Rift › Created and managed test.");
            assertThat(localization.selections().playerLocale(playerId)).contains("fr_FR");
            assertThat(french).doesNotExist();

            Files.writeString(french, edited);
            assertThat(localization.reloadSnapshot(french.toFile(), edited)).isTrue();
            assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                    .isEqualTo("PORTAIL Modifié test");
        }
    }

    @Test
    void editablePrefixDoesNotLeakItsFormattingIntoMessagesOrInlineText() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        localization.install(localization.prepareSnapshot("en_US", "[runtime]\nprefix = \"&c&l&oPORTAL\"\n"));

        ComponentText created = localization.text(RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build());
        List<Style> styles = characterStyles(created);
        assertThat(created.plain()).isEqualTo("PORTAL › Created and managed test.");
        for (Style style : styles.subList(0, 6)) {
            assertThat(style.decoration(TextDecoration.BOLD)).isEqualTo(TextDecoration.State.TRUE);
            assertThat(style.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.TRUE);
        }
        for (Style style : styles.subList(6, styles.size())) {
            assertThat(style.decoration(TextDecoration.BOLD)).isNotEqualTo(TextDecoration.State.TRUE);
            assertThat(style.decoration(TextDecoration.ITALIC)).isNotEqualTo(TextDecoration.State.TRUE);
        }
        assertThat(styles.get(7).color()).isEqualTo(NamedTextColor.GRAY);
        assertThat(styles.get(9).color()).isEqualTo(NamedTextColor.GRAY);
        assertThat(styles.get(created.plain().indexOf("test")).color()).isEqualTo(NamedTextColor.GREEN);
        assertThat(localization.textWithoutPrefix(RiftMessages.GUI_ROOT_TITLE, MessageArgs.empty()).plain())
                .isEqualTo("PORTAL Configuration");
        assertThat(localization.textWithoutPrefix(RiftMessages.STATUS_TITLE,
                MessageArgs.builder().untrusted("version", "1.2.3").build()).plain())
                .isEqualTo("PORTAL 1.2.3 Status");

        localization.install(localization.prepareSnapshot("en_US", "[runtime]\nprefix = \"&c&lPORTAL\"\n"
                + "[rift.message]\ncreated = \"<yellow>{prefix} stays local {world}</yellow>\"\n"));
        ComponentText inline = localization.text(RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", "test").build());
        Style body = characterStyles(inline).get(7);
        assertThat(body.color()).isEqualTo(NamedTextColor.YELLOW);
        assertThat(body.decoration(TextDecoration.BOLD)).isNotEqualTo(TextDecoration.State.TRUE);
    }

    @Test
    void versionUsesOneHelpGradientAcrossTheEditableNameAndVersion() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        RiftLocalization.PreparedLanguage prepared = localization.prepareSnapshot("en_US",
                "[runtime]\nprefix = \"&b&l&oPORTAL\"\n");
        localization.install(prepared);
        MessageArgs arguments = MessageArgs.builder().untrusted("version", "1.2.3").build();
        ComponentText version = localization.text(RiftMessages.VERSION, arguments);
        ComponentText expected = ComponentText.markup(DirectorMiniMenu.version("PORTAL", "1.2.3", RiftCommandService.theme()));

        assertThat(RiftCommandService.theme().primaryLeft()).isEqualTo("#6f2dbd");
        assertThat(RiftCommandService.theme().primaryRight()).isEqualTo("#d16ba5");
        assertThat(version.plain()).isEqualTo("PORTAL v1.2.3");
        assertThat(characterStyles(version)).extracting(Style::color)
                .containsExactlyElementsOf(characterStyles(expected).stream().map(Style::color).toList());
        for (Style style : characterStyles(version)) {
            assertThat(style.decoration(TextDecoration.BOLD)).isNotEqualTo(TextDecoration.State.TRUE);
            assertThat(style.decoration(TextDecoration.ITALIC)).isNotEqualTo(TextDecoration.State.TRUE);
        }
        assertThat(localization.textWithoutPrefix(RiftMessages.VERSION, arguments).miniMessage())
                .isEqualTo(version.miniMessage());
        assertThat(localization.directorResolver().resolve(RiftMessages.VERSION, arguments))
                .isEqualTo(version.miniMessage());

        RiftLocalization.EditableMessage preview = localization.editableMessages(prepared).stream()
                .filter(message -> message.id().equals(RiftMessages.VERSION.id())).findFirst().orElseThrow();
        ComponentText previewText = ComponentText.component(MiniMessage.miniMessage().deserialize(preview.previewValue()));
        ComponentText expectedPreview = ComponentText.markup(DirectorMiniMenu.version("PORTAL", "[version]", RiftCommandService.theme()));
        assertThat(previewText.plain()).isEqualTo("PORTAL v[version]");
        assertThat(characterStyles(previewText)).extracting(Style::color)
                .containsExactlyElementsOf(characterStyles(expectedPreview).stream().map(Style::color).toList());

        localization.install(localization.prepareSnapshot("en_US", "[runtime]\nprefix = \"&b&lPORTAL\"\n"
                + "[rift.message]\nversion = \"<green>Build {version} for {prefix}</green>\"\n"));
        ComponentText customized = localization.text(RiftMessages.VERSION, arguments);
        assertThat(customized.plain()).isEqualTo("Build 1.2.3 for PORTAL");
        for (Style style : characterStyles(customized)) {
            assertThat(style.color()).isEqualTo(NamedTextColor.GREEN);
            assertThat(style.decoration(TextDecoration.BOLD)).isNotEqualTo(TextDecoration.State.TRUE);
        }
    }

    @Test
    void sharedLanguageFeedbackResolvesTheEditableNameInsteadOfThePluginArgument() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        localization.install(localization.prepareSnapshot("en_US", "[runtime]\nprefix = \"&b&lPORTAL\"\n"));
        String resolved = localization.directorResolver().resolve(BukkitLanguageMessages.PERSONAL_SELECTED,
                MessageArgs.builder().untrusted("plugin", "Rift").untrusted("locale", "de_DE").build());

        assertThat(ComponentText.markup(resolved).plain()).isEqualTo("PORTAL › your language is now de_DE.");
        assertThat(resolved).doesNotContain("{prefix}", "{plugin}");
        assertThat(characterStyles(ComponentText.markup(resolved)).get(0).decoration(TextDecoration.BOLD))
                .isEqualTo(TextDecoration.State.TRUE);

        String other = localization.directorResolver().resolve(BukkitLanguageMessages.PERSONAL_SELECTED,
                MessageArgs.builder().untrusted("plugin", "OtherProvider").untrusted("locale", "de_DE").build());
        assertThat(ComponentText.markup(other).plain()).isEqualTo("OtherProvider › your language is now de_DE.");
    }

    @Test
    void messageArgumentsKeepFormattingSyntaxLiteral() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        String value = "&c<bold>test</bold> [ff0000] \u00a7lvalue <rift_prefix>";
        String displayed = "&c<bold>test</bold> [ff0000] value <rift_prefix>";
        ComponentText message = localization.text(RiftMessages.CREATED,
                MessageArgs.builder().untrusted("world", value).build());

        assertThat(message.plain()).isEqualTo("Rift › Created and managed " + displayed + ".");
        List<Style> styles = characterStyles(message);
        for (Style style : styles.subList(message.plain().indexOf(displayed), styles.size() - 1)) {
            assertThat(style.color()).isEqualTo(NamedTextColor.GREEN);
            assertThat(style.decoration(TextDecoration.BOLD)).isNotEqualTo(TextDecoration.State.TRUE);
        }
    }

    @Test
    void languagePreparationUsesTheEditableOwnNameAndPreservesOtherTargets() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        localization.install(localization.prepareSnapshot("en_US", "[runtime]\nprefix = \"&b&lPORTAL\"\n"));
        ComponentText own = localization.text(BukkitLanguageMessages.PREPARING,
                MessageArgs.builder().untrusted("locale", "en_US").untrusted("target", "Rift").build());

        assertThat(own.plain()).isEqualTo("PORTAL › Preparing language en_US for PORTAL...");
        assertThat(characterStyles(own).get(own.plain().lastIndexOf("PORTAL")).decoration(TextDecoration.BOLD))
                .isEqualTo(TextDecoration.State.TRUE);
        for (String target : List.of("OtherProvider", "all Volmit plugins", "/rift")) {
            ComponentText other = localization.text(BukkitLanguageMessages.PREPARING,
                    MessageArgs.builder().untrusted("locale", "en_US").untrusted("target", target).build());
            assertThat(other.plain()).isEqualTo("PORTAL › Preparing language en_US for " + target + "...");
            assertThat(characterStyles(other).get(other.plain().lastIndexOf(target)).decoration(TextDecoration.BOLD))
                    .isNotEqualTo(TextDecoration.State.TRUE);
        }
    }

    @Test
    void everyLocaleRendersEveryCatalogEntryWithAnEditedName() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        for (String locale : VolmitLocales.all()) {
            String content = locale.equals("en_US")
                    ? RiftLocalization.referenceToml()
                    : Files.readString(Path.of("src/main/resources/languages", locale + ".toml"));
            content = content.replace(RiftMessages.PREFIX.english(), "&b&lPORTAL");
            Files.writeString(localization.file(locale).toPath(), content);
            localization.install(localization.prepareSnapshot(locale, content));
            for (MessageKey key : RiftMessages.catalog().keys()) {
                MessageArgs.Builder arguments = MessageArgs.builder();
                for (String name : key.placeholders()) {
                    if (!name.equals("prefix")) {
                        arguments.untrusted(name, "value");
                    }
                }
                String rendered = localization.text((TextKey) key, arguments.build()).plain();
                assertThat(rendered).describedAs("%s in %s", key.id(), locale)
                        .doesNotContain("{prefix}", "<rift_prefix>");
                if (key.placeholders().contains("prefix")) {
                    assertThat(rendered).describedAs("edited name in %s for %s", locale, key.id()).contains("PORTAL");
                }
            }
        }
    }

    @Test
    void everyRepositoryLocaleTranslatesPolicyEditorControls() throws Exception {
        RiftLocalization localization = localization(new RiftConfig().normalize());
        assertThat(localization.loadInitial()).isTrue();
        List<TextKey> controls = List.of(RiftMessages.COMMAND_POLICY_EDIT, RiftMessages.GUI_POLICY_TITLE,
                RiftMessages.GUI_POLICY_RESET, RiftMessages.GUI_POLICY_SPAWN,
                RiftMessages.GUI_POLICY_DENIAL,
                RiftMessages.GUI_POLICY_TAGS, RiftMessages.GUI_POLICY_CLEAR, RiftMessages.GUI_POLICY_PREVIOUS,
                RiftMessages.GUI_POLICY_NEXT, RiftMessages.COMMAND_POLICY_GAME_MODE, RiftMessages.COMMAND_POLICY_FLIGHT,
                RiftMessages.COMMAND_POLICY_WEATHER, RiftMessages.COMMAND_POLICY_NATURAL_ANIMALS,
                RiftMessages.COMMAND_POLICY_NATURAL_MONSTERS, RiftMessages.PARAM_POLICY_GAME_MODE,
                RiftMessages.PARAM_POLICY_WEATHER, RiftMessages.LABEL_POLICY_GAME_MODE,
                RiftMessages.LABEL_POLICY_FLIGHT, RiftMessages.LABEL_POLICY_WEATHER,
                RiftMessages.LABEL_POLICY_NATURAL_ANIMALS, RiftMessages.LABEL_POLICY_NATURAL_MONSTERS);
        for (String locale : VolmitLocales.all()) {
            if (locale.equals("en_US")) {
                continue;
            }
            String content = Files.readString(Path.of("src/main/resources/languages", locale + ".toml"));
            Files.writeString(localization.file(locale).toPath(), content);
            localization.install(localization.prepareSnapshot(locale, content));
            for (TextKey control : controls) {
                assertThat(localization.text(control).plain())
                        .describedAs("%s in %s", control.id(), locale)
                        .isNotBlank()
                        .isNotEqualTo(control.english().replaceAll("&[0-9a-fk-or]", ""));
            }
        }
    }

    @Test
    void upstreamSyncInstallsMergesAndKeepsEditsAndCustomFiles() throws Exception {
        LoopbackRepository repository = new LoopbackRepository();
        repository.files.put(LoopbackRepository.REVISION_ONE + "/fr_FR",
                "# fr v1\n\n[runtime]\nprefix = \"&6Faille\"\n");
        RiftConfig config = new RiftConfig().normalize();
        RiftLocalization localization = repository.localization(config, temporaryDirectory.resolve("languages").toFile());
        List<String> selfWrites = new ArrayList<>();
        localization.setSelfWriteListener((written, content) -> selfWrites.add(written.getName()));
        try {
            assertThat(localization.loadInitial()).isTrue();
            localization.initializeSelections(config::getLanguage, (locale, snapshot) -> localization.install(
                    new RiftLocalization.PreparedLanguage(locale, localization.file(locale), snapshot, true)));
            Path languages = temporaryDirectory.resolve("languages");
            String pirate = "[meta]\nname = \"Pirate Speak\"\nbase = \"fr_FR\"\n\n[runtime]\nprefix = \"&6Arr\"\n";
            Files.writeString(languages.resolve("pirate.toml"), pirate);

            assertThat(localization.requestInstall("fr_FR").get(10L, TimeUnit.SECONDS)).contains("Faille");
            assertThat(localization.file("fr_FR")).isFile();
            assertThat(languages.resolve(".upstream").resolve("fr_FR.toml")).isRegularFile();
            assertThat(languages.resolve(".upstream").resolve("upstream.properties")).isRegularFile();
            assertThat(selfWrites).contains("fr_FR.toml");
            UUID playerId = UUID.randomUUID();
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(playerId);
            localization.selections().selectPlayer(playerId, "fr_FR").get(5L, TimeUnit.SECONDS);
            MessageArgs arguments = MessageArgs.builder().untrusted("world", "test").build();
            assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                    .isEqualTo("Faille › Created and managed test.");

            localization.updateMessage("fr_FR", RiftMessages.PREFIX.id(), "&6Brèche");
            repository.revision.set(LoopbackRepository.REVISION_TWO);
            repository.files.put(LoopbackRepository.REVISION_TWO + "/fr_FR",
                    "# fr v2\n\n[runtime]\nprefix = \"&6Faille v2\"\n\n[rift.message]\n"
                            + "created = \"{prefix}&r &7› &7Monde &a{world}&7 créé.\"\n");

            LanguageUpstreamSync.SyncReport report = localization.updateAction().update().get(10L, TimeUnit.SECONDS);

            assertThat(report.failure()).isEmpty();
            assertThat(report.changedLocales()).containsExactly("fr_FR");
            assertThat(report.merged().get("fr_FR").kept()).containsExactly(RiftMessages.PREFIX.id());
            assertThat(report.merged().get("fr_FR").added()).containsExactly(RiftMessages.CREATED.id());
            String merged = Files.readString(localization.file("fr_FR").toPath());
            assertThat(merged).startsWith("# fr v2\n").contains("Brèche", "Monde").doesNotContain("Faille v2");
            assertThat(Files.readString(languages.resolve(".upstream").resolve("fr_FR.toml"))).contains("Faille v2");
            assertThat(Files.readString(languages.resolve("pirate.toml"))).isEqualTo(pirate);
            assertThat(localization.text(player, RiftMessages.CREATED, arguments).plain())
                    .isEqualTo("Brèche › Monde test créé.");
            assertThat(localization.upstreamStatus()).isPresent();
            assertThat(localization.upstreamStatus().get().revision()).contains(LoopbackRepository.REVISION_TWO);

            LanguageUpstreamSync.SyncReport unchanged = localization.updateAction().update().get(10L, TimeUnit.SECONDS);

            assertThat(unchanged.unchanged()).isTrue();
            assertThat(unchanged.changedLocales()).isEmpty();
            assertThat(Files.readString(localization.file("fr_FR").toPath())).isEqualTo(merged);
        } finally {
            localization.close();
            repository.close();
        }
    }

    @Test
    void customLocaleMetaProvidesDisplayNameAndBaseChainBeforeEnglish() throws Exception {
        RiftConfig config = new RiftConfig().normalize();
        try (RiftLocalization localization = localization(config)) {
            assertThat(localization.loadInitial()).isTrue();
            Path languages = temporaryDirectory.resolve("languages");
            Files.writeString(languages.resolve("de_DE.toml"), "[runtime]\nprefix = \"Spalt\"\n\n[rift.message]\n"
                    + "created = \"{prefix} Welt {world} erstellt\"\nloaded = \"{prefix} Welt {world} geladen\"\n");
            Files.writeString(languages.resolve("de_AT.toml"), "[meta]\nname = \"Österreichisch\"\nbase = \"de_DE\"\n\n"
                    + "[rift.message]\ncreated = \"{prefix} Welt {world} gebaut, Oida\"\n");
            Files.writeString(languages.resolve("pirate.toml"), "[meta]\nname = \"Pirate Speak\"\nbase = \"de_AT\"\n\n"
                    + "[runtime]\nprefix = \"Arr\"\n");

            RiftLocalization.PreparedLanguage pirate = localization.prepare("pirate");

            assertThat(pirate.selectionReady()).isTrue();
            assertThat(localization.availableLocales()).contains("pirate", "de_AT", "de_DE");
            assertThat(localization.localeDisplayName("pirate")).isEqualTo("Pirate Speak");
            assertThat(localization.localeDisplayName("de_DE")).isEqualTo(VolmitLocales.displayName("de_DE").orElseThrow());
            assertThat(localization.localeDisplayName("klingon")).isEqualTo("klingon");
            assertThat(localization.displayNames().displayName("pirate")).isEqualTo("Pirate Speak");
            assertThat(localization.displayNames().displayName("de_DE")).isNull();
            assertThat(localization.meta("pirate").base()).contains("de_AT");

            localization.install(pirate);
            MessageArgs arguments = MessageArgs.builder().untrusted("world", "test").build();
            assertThat(localization.text(RiftMessages.CREATED, arguments).plain()).isEqualTo("Arr Welt test gebaut, Oida");
            assertThat(localization.text(RiftMessages.LOADED, arguments).plain()).isEqualTo("Arr Welt test geladen");
            assertThat(localization.text(RiftMessages.UNLOADED, arguments).plain()).contains("test");

            localization.prepare("klingon");
            LanguageMeta generated = LanguageMeta.parse(Files.readString(languages.resolve("klingon.toml")));

            assertThat(generated.name()).contains("klingon");
            assertThat(generated.base()).contains("en_US");
            assertThat(localization.availableLocales()).contains("klingon");
        }
    }

    @Test
    void englishReferenceGainsNewCatalogKeysWhileKeepingEdits() throws Exception {
        RiftLocalization first = localization(new RiftConfig().normalize());
        assertThat(first.loadInitial()).isTrue();
        first.close();
        Path english = temporaryDirectory.resolve("languages").resolve("en_US.toml");
        Path sidecar = temporaryDirectory.resolve("languages").resolve(".upstream").resolve("en_US.toml");
        String olderReference = TomlLanguageEditor.remove(Files.readString(sidecar), RiftMessages.LOADED.id()).content();
        Files.writeString(sidecar, olderReference);
        Files.writeString(english, TomlLanguageEditor.upsertText(olderReference, RiftMessages.PREFIX.id(), "&6PORTAL").content());

        try (RiftLocalization localization = localization(new RiftConfig().normalize())) {
            assertThat(localization.loadInitial()).isTrue();
            Map<String, String> values = TomlLanguageParser.parseText(Files.readString(english));

            assertThat(values).containsKey(RiftMessages.LOADED.id());
            assertThat(values.get(RiftMessages.PREFIX.id())).isEqualTo("&6PORTAL");
            assertThat(localization.text(RiftMessages.LOADED,
                    MessageArgs.builder().untrusted("world", "test").build()).plain())
                    .isEqualTo("PORTAL › Loaded test.");
            assertThat(Files.readString(sidecar)).isEqualTo(RiftLocalization.referenceToml());
        }
    }

    private List<Style> characterStyles(ComponentText text) {
        ArrayList<Style> styles = new ArrayList<>();
        collectStyles(MiniMessage.miniMessage().deserialize(text.miniMessage()), Style.empty(), styles);
        return styles;
    }

    private void collectStyles(Component component, Style parent, List<Style> styles) {
        Style style = component.style().merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (component instanceof TextComponent text) {
            for (int index = 0; index < text.content().length(); index++) {
                styles.add(style);
            }
        }
        for (Component child : component.children()) {
            collectStyles(child, style, styles);
        }
    }

    private RiftLocalization localization(RiftConfig config) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(RiftLocalizationTest.class.getName()));
        return new RiftLocalization(plugin, () -> config, temporaryDirectory.resolve("languages").toFile(),
                URI.create("http://127.0.0.1:9/api/"), URI.create("http://127.0.0.1:9/raw/"), Duration.ZERO);
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

    private static final class LoopbackRepository {
        private static final String REVISION_ONE = "1111111111111111111111111111111111111111";
        private static final String REVISION_TWO = "2222222222222222222222222222222222222222";

        private final HttpServer server;
        private final AtomicReference<String> revision = new AtomicReference<>(REVISION_ONE);
        private final Map<String, String> files = new ConcurrentHashMap<>();

        private LoopbackRepository() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/", this::api);
            server.createContext("/raw/", this::raw);
            server.start();
        }

        private RiftLocalization localization(RiftConfig config, File directory) {
            Plugin plugin = mock(Plugin.class);
            when(plugin.getLogger()).thenReturn(Logger.getLogger(RiftLocalizationTest.class.getName()));
            URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            return new RiftLocalization(plugin, () -> config, directory, root.resolve("api/"), root.resolve("raw/"),
                    Duration.ZERO);
        }

        private void close() {
            server.stop(0);
        }

        private void api(HttpExchange exchange) throws IOException {
            String etag = "\"" + revision.get() + "\"";
            if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("ETag", etag);
            byte[] response = ("[{\"sha\":\"" + revision.get() + "\"}]").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }

        private void raw(HttpExchange exchange) throws IOException {
            String[] parts = exchange.getRequestURI().getPath().split("/");
            String key = parts[4] + "/" + parts[parts.length - 1].replace(".toml", "");
            String content = files.get(key);
            if (content == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] response = content.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }
}
