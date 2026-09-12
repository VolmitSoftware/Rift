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
        assertThat(catalog.keys()).hasSize(276);
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
