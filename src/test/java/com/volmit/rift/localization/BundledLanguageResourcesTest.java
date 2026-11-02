package com.volmit.rift.localization;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TextValue;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

final class BundledLanguageResourcesTest {
    private static final Path LANGUAGE_ROOT = Path.of("src/main/resources/languages");
    private static final Pattern AMPERSAND_FORMATTING = Pattern.compile("&[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_LITERAL = Pattern.compile("/rift(?: [a-z]+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");
    private static final Pattern TOKENIZER_ARTIFACT = Pattern.compile("(?:\\u2581|<unk>|\\u2047|@@|<s>|</s>)");
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("<[^>]*>");
    private static final String LATIN_WORD_CHARACTER = "[\\p{L}\\p{N}_&&[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]]";
    private static final List<Pattern> LEFTOVER_ENGLISH = List.of(
            Pattern.compile("/(?:right|left)(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<!" + LATIN_WORD_CHARACTER + ")(?:toggle|click|the|and|with|your)(?!" + LATIN_WORD_CHARACTER + ")",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<![\\p{L}\\p{N}])(\\p{L}+)(?:\\s+\\1(?![\\p{L}\\p{N}])){2,}",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
    );
    // Chat prompts, command parsers and policy setters match these words exactly, so a translated word would be saved as a value.
    private static final Map<String, List<String>> LITERAL_INPUT_KEYWORDS = Map.ofEntries(
            Map.entry("rift.gui.prompt_cancel", List.of("&fcancel&7")),
            Map.entry("language.editor.prompt.search", List.of("cancel")),
            Map.entry("language.editor.prompt.guidance", List.of("cancel", "\\n", "\\\\")),
            Map.entry("rift.gui.policy_tags", List.of("clear")),
            Map.entry("rift.gui.policy_clear", List.of("clear")),
            Map.entry("rift.gui.policy_inherited", List.of("INHERIT")),
            Map.entry("rift.gui.policy_reset", List.of("Q")),
            Map.entry("rift.gui.exact_number", List.of("Q")),
            Map.entry("rift.parameter.environment", List.of("NORMAL", "NETHER", "THE_END", "CUSTOM")),
            Map.entry("rift.parameter.generator", List.of("vanilla", "void", "PluginName[:generator-id]")),
            Map.entry("rift.message.generator_format", List.of("PluginName[:generator-id]")),
            Map.entry("rift.explain.generator", List.of("void")),
            Map.entry("rift.parameter.seed", List.of("random")),
            Map.entry("rift.parameter.enabled", List.of("true", "false")),
            Map.entry("rift.parameter.tag_enabled", List.of("true", "false")),
            Map.entry("rift.parameter.tag_filter", List.of("all")),
            Map.entry("rift.parameter.access_permission", List.of("clear")),
            Map.entry("rift.parameter.respawn_destination", List.of("default")),
            Map.entry("rift.parameter.player_limit", List.of("0")),
            Map.entry("rift.parameter.game_rule_value", List.of("INHERIT")),
            Map.entry("rift.parameter.policy_difficulty", List.of("PEACEFUL", "EASY", "NORMAL", "HARD", "INHERIT")),
            Map.entry("rift.parameter.policy_pvp", List.of("ALLOW", "DENY", "INHERIT")),
            Map.entry("rift.parameter.policy_game_mode", List.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR", "INHERIT")),
            Map.entry("rift.parameter.policy_weather", List.of("CLEAR", "RAIN", "THUNDER", "INHERIT")),
            Map.entry("rift.command.policy_player_limit", List.of("0")),
            Map.entry("rift.command.policy_game_mode", List.of("INHERIT")),
            Map.entry("rift.command.policy_flight", List.of("INHERIT")),
            Map.entry("rift.command.policy_weather", List.of("INHERIT")),
            Map.entry("rift.command.policy_natural_animals", List.of("DENY", "ALLOW")),
            Map.entry("rift.command.policy_natural_monsters", List.of("DENY", "ALLOW")),
            Map.entry("rift.command.policy_travel_entry", List.of("DENY", "INHERIT")),
            Map.entry("rift.command.policy_travel_exit", List.of("DENY")),
            Map.entry("rift.command.policy_first_join", List.of("default")),
            Map.entry("rift.command.policy_login", List.of("default")),
            Map.entry("language.usage.editor", List.of("language server edit")),
            Map.entry("language.usage.selection", List.of("language self", "reset", "server")),
            Map.entry("language.usage.volmit-selection", List.of("/volmit plugins languages")),
            Map.entry("language.error.server-locale-required", List.of("en_US")),
            Map.entry("director.runtime.error.unexpected_argument", List.of("seed=123"))
    );

    @Test
    void providesExactlyTheCanonicalRemoteLanguageSources() throws Exception {
        List<String> expected = VolmitLocales.nonEnglish().stream()
                .map(locale -> locale + ".toml")
                .sorted()
                .toList();
        List<String> actual;
        try (Stream<Path> resources = Files.list(LANGUAGE_ROOT)) {
            actual = resources
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".toml"))
                    .sorted()
                    .toList();
        }

        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(actual).hasSize(17);
        assertThat(LANGUAGE_ROOT.resolve("en_US.toml")).doesNotExist();
    }

    @Test
    void everyRemoteLocaleIsCompleteValidAndMeaningfullyTranslated() throws Exception {
        MessageCatalog catalog = RiftMessages.catalog();
        assertThat(catalog.keys()).hasSize(402);
        Set<String> expectedPlaceholders = catalog.keys().stream()
                .flatMap(key -> key.placeholders().stream())
                .collect(Collectors.toSet());
        assertThat(expectedPlaceholders).contains("prefix");

        for (String locale : VolmitLocales.nonEnglish()) {
            Path resource = LANGUAGE_ROOT.resolve(locale + ".toml");
            String content = Files.readString(resource);
            String header = content.substring(0, content.indexOf("\n[runtime]"));
            Map<String, String> messages = TomlLanguageParser.parseText(content);

            assertThat(content)
                    .describedAs("sectioned TOML in %s", resource)
                    .contains("[runtime]", "[rift.command]", "[rift.message]", "[director.runtime.error]")
                    .doesNotContain("messages:", "prefix:");
            assertThat(messages.keySet())
                    .describedAs("catalog coverage in %s", resource)
                    .containsExactlyInAnyOrderElementsOf(catalog.ids());
            assertThat(messages.get("runtime.prefix"))
                    .describedAs("global prefix in %s", resource)
                    .isEqualTo("<bold><gradient:#6f2dbd:#d16ba5>Rift</gradient></bold>");
            assertThat(messages.get("rift.message.version"))
                    .describedAs("version line in %s", resource)
                    .isEqualTo("<gradient:#6f2dbd:#d16ba5>{prefix} v{version}</gradient>");
            assertThat(placeholders(header))
                    .describedAs("placeholder reference in %s", resource)
                    .containsExactlyInAnyOrderElementsOf(expectedPlaceholders);

            int changed = 0;
            List<String> invalid = new ArrayList<>();
            for (MessageKey key : catalog.keys()) {
                String template = messages.get(key.id());
                if (template == null || template.isBlank()) {
                    invalid.add(key.id() + " is blank or not text");
                    continue;
                }
                if (template.contains("\uFFFD") || TOKENIZER_ARTIFACT.matcher(template).find()) {
                    invalid.add(key.id() + " contains a translation artifact");
                }
                if (template.contains("[Rift]")) {
                    invalid.add(key.id() + " repeats the retired prefix");
                }
                if (!new TextValue(template).placeholders().equals(key.placeholders())) {
                    invalid.add(key.id() + " changed placeholders");
                }
                if (key instanceof TextKey textKey) {
                    if (!formatCodes(template).equals(formatCodes(textKey.english()))) {
                        invalid.add(key.id() + " changed classic formatting codes");
                    }
                    if (!commandLiterals(template).equals(commandLiterals(textKey.english()))) {
                        invalid.add(key.id() + " changed command literals");
                    }
                    if (!template.equals(textKey.english())) {
                        changed++;
                    }
                }
            }

            assertThat(invalid).describedAs("translation integrity in %s", resource).isEmpty();
            assertThat(changed)
                    .describedAs("meaningful non-English coverage in %s", resource)
                    .isGreaterThanOrEqualTo(catalog.keys().size() * 3 / 4);
        }
    }

    @Test
    void everyRemoteLocaleExplainsTheRemoteAndPrefixContractsInItsLanguage() throws Exception {
        for (String locale : VolmitLocales.nonEnglish()) {
            Path resource = LANGUAGE_ROOT.resolve(locale + ".toml");
            String content = Files.readString(resource);
            String header = content.substring(0, content.indexOf("\n[runtime]"));

            assertThat(header)
                    .describedAs("localized placeholder warning in %s", resource)
                    .contains("{prefix}", "runtime.prefix", "plugins/Rift/languages/" + locale + ".toml");
            assertThat(header.lines().filter(line -> line.startsWith("# === ")).count()).isEqualTo(4);
            assertThat(header.lines().findFirst().orElseThrow())
                    .describedAs("localized title in %s", resource)
                    .doesNotContain("Rift language file");
            assertThat(header)
                    .describedAs("localized remote language guidance in %s", resource)
                    .doesNotContain(
                            "This file is downloaded only when it is missing",
                            "Local changes are never automatically replaced",
                            "Use a placeholder only in messages",
                            "Available placeholders"
                    );
        }
    }

    @Test
    void everyRemoteLocaleKeepsLiteralInputKeywordsUntranslated() throws Exception {
        Map<String, String> english = englishTemplates();
        for (Map.Entry<String, List<String>> entry : LITERAL_INPUT_KEYWORDS.entrySet()) {
            assertThat(english).describedAs("keyword message %s", entry.getKey()).containsKey(entry.getKey());
            for (String keyword : entry.getValue()) {
                assertThat(containsLiteral(english.get(entry.getKey()), keyword))
                        .describedAs("English %s contains %s", entry.getKey(), keyword)
                        .isTrue();
            }
        }

        for (String locale : VolmitLocales.nonEnglish()) {
            Path resource = LANGUAGE_ROOT.resolve(locale + ".toml");
            Map<String, String> messages = TomlLanguageParser.parseText(Files.readString(resource));
            List<String> invalid = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : LITERAL_INPUT_KEYWORDS.entrySet()) {
                String template = messages.getOrDefault(entry.getKey(), "");
                for (String keyword : entry.getValue()) {
                    if (!containsLiteral(template, keyword)) {
                        invalid.add(entry.getKey() + " must keep " + keyword + ": " + template);
                    }
                }
            }
            assertThat(invalid).describedAs("literal input keywords in %s", resource).isEmpty();
        }
    }

    @Test
    void everyRemoteLocaleIsFreeOfLeftoverEnglishFragments() throws Exception {
        for (String locale : VolmitLocales.nonEnglish()) {
            Path resource = LANGUAGE_ROOT.resolve(locale + ".toml");
            Map<String, String> messages = TomlLanguageParser.parseText(Files.readString(resource));
            List<String> invalid = new ArrayList<>();
            for (Map.Entry<String, String> message : messages.entrySet()) {
                String text = message.getValue();
                for (String keyword : LITERAL_INPUT_KEYWORDS.getOrDefault(message.getKey(), List.of())) {
                    text = text.replace(keyword, " ");
                }
                text = PLACEHOLDER.matcher(text).replaceAll(" ");
                text = AMPERSAND_FORMATTING.matcher(text).replaceAll(" ");
                text = MINI_MESSAGE_TAG.matcher(text).replaceAll(" ");
                text = COMMAND_LITERAL.matcher(text).replaceAll(" ");
                for (Pattern pattern : LEFTOVER_ENGLISH) {
                    if (pattern.matcher(text).find()) {
                        invalid.add(message.getKey() + " matches " + pattern.pattern() + ": " + message.getValue());
                    }
                }
            }
            assertThat(invalid).describedAs("leftover English fragments in %s", resource).isEmpty();
        }
    }

    private Map<String, String> englishTemplates() {
        return RiftMessages.catalog().keys().stream()
                .filter(TextKey.class::isInstance)
                .map(TextKey.class::cast)
                .collect(Collectors.toMap(TextKey::id, TextKey::english));
    }

    private boolean containsLiteral(String template, String keyword) {
        String text = keyword.contains("&") ? template : AMPERSAND_FORMATTING.matcher(template).replaceAll(" ");
        return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(keyword) + "(?![A-Za-z0-9_])").matcher(text).find();
    }

    private List<String> formatCodes(String template) {
        return matches(AMPERSAND_FORMATTING, template);
    }

    private List<String> commandLiterals(String template) {
        return matches(COMMAND_LITERAL, template);
    }

    private List<String> matches(Pattern pattern, String template) {
        ArrayList<String> values = new ArrayList<>();
        Matcher matcher = pattern.matcher(template);
        while (matcher.find()) {
            values.add(matcher.group());
        }
        return values;
    }

    private Set<String> placeholders(String header) {
        HashSet<String> placeholders = new HashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(header);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return Set.copyOf(placeholders);
    }
}
