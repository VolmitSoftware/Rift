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
    private static final Map<String, String> LOCALIZED_USAGE_WARNINGS = Map.ofEntries(
            Map.entry("de_DE", "Verwende einen Platzhalter nur in Nachrichten"),
            Map.entry("es_ES", "Usa cada marcador solo en mensajes"),
            Map.entry("fi_FI", "Käytä paikkamerkkiä vain viesteissä"),
            Map.entry("fr_FR", "Utilisez une variable uniquement dans les messages"),
            Map.entry("he_IL", "יש להשתמש במציין מקום רק בהודעות"),
            Map.entry("it_IT", "Usa un segnaposto solo nei messaggi"),
            Map.entry("ja-JP", "プレースホルダーは、既定テンプレートで宣言されているメッセージでのみ使用してください"),
            Map.entry("ko_KR", "자리표시자는 기본 템플릿에 선언된 메시지에서만 사용하세요"),
            Map.entry("lt_LT", "Vietos žymeklį naudokite tik tuose pranešimuose"),
            Map.entry("nl_NL", "Gebruik een tijdelijke aanduiding alleen in berichten"),
            Map.entry("pl_PL", "Używaj znacznika tylko w wiadomościach"),
            Map.entry("pt_PT", "Utilize um marcador apenas nas mensagens"),
            Map.entry("ru_RU", "Используйте заполнитель только в сообщениях"),
            Map.entry("tr_TR", "Bir yer tutucuyu yalnızca varsayılan şablonunda tanımlandığı mesajlarda kullanın"),
            Map.entry("vi_VI", "Chỉ dùng phần giữ chỗ trong thông báo"),
            Map.entry("zh_CN", "占位符只能用于默认模板已声明该占位符的消息"),
            Map.entry("zh_TW", "預留位置只能用於預設範本已宣告該預留位置的訊息")
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
        assertThat(catalog.keys()).hasSize(198);
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
                    .contains("[runtime]", "[rift.command]", "[rift.message]", "[director.runtime]")
                    .doesNotContain("messages:", "prefix:");
            assertThat(messages.keySet())
                    .describedAs("catalog coverage in %s", resource)
                    .containsExactlyInAnyOrderElementsOf(catalog.ids());
            assertThat(messages.get("runtime.prefix"))
                    .describedAs("global prefix in %s", resource)
                    .isEqualTo("&5&lRIFT&r &8›&r ");
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
        assertThat(LOCALIZED_USAGE_WARNINGS.keySet())
                .containsExactlyInAnyOrderElementsOf(VolmitLocales.nonEnglish());
        for (String locale : VolmitLocales.nonEnglish()) {
            Path resource = LANGUAGE_ROOT.resolve(locale + ".toml");
            String content = Files.readString(resource);
            String header = content.substring(0, content.indexOf("\n[runtime]"));

            assertThat(header)
                    .describedAs("localized placeholder warning in %s", resource)
                    .contains(LOCALIZED_USAGE_WARNINGS.get(locale), "{prefix}", "runtime.prefix");
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
