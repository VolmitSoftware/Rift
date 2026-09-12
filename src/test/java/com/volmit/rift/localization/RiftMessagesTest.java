package com.volmit.rift.localization;

import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class RiftMessagesTest {
    @Test
    void catalogContainsTypedOperationalAndDirectorMessages() {
        MessageCatalog catalog = RiftMessages.catalog();

        assertThat(catalog.englishLocale()).isEqualTo("en_US");
        assertThat(catalog.byId()).containsKeys(
                "rift.command.create",
                "runtime.prefix",
                "rift.command.language",
                "rift.command.status",
                "rift.command.debug_dump",
                "rift.parameter.page",
                "rift.parameter.upload",
                "language.menu.your_language",
                "language.menu.current_with_personal",
                "rift.message.operation_failed",
                "rift.message.quarantined",
                "rift.gui.setting_saved",
                "rift.gui.root_title",
                "rift.gui.category.languages",
                "rift.gui.category.diagnostics",
                "language.menu.edit_messages",
                "language.menu.use_server_default",
                "director.runtime.error.missing_argument"
        );
        assertThat(catalog.byId()).doesNotContainKeys(
                "rift.command.doctor",
                "rift.message.doctor_title",
                "rift.gui.automatic",
                "rift.gui.automatic_title",
                "rift.gui.category.hot_reload"
        );
        assertThat(RiftMessages.OPERATION_FAILED.placeholders())
                .containsExactlyInAnyOrder("prefix", "operation", "world", "reason");
        assertThat(RiftMessages.OPERATION_FAILED.optionalPlaceholders()).containsExactly("prefix");
        assertThat(RiftMessages.DELETE_CONFIRM.placeholders())
                .containsExactlyInAnyOrder("prefix", "world", "seconds");
        assertThat(RiftMessages.CONFIG_OPENED.optionalPlaceholders()).containsExactly("prefix");
        assertThat(RiftMessages.GUI_SETTING_SAVED.placeholders())
                .containsExactlyInAnyOrder("prefix", "setting", "after", "before");
        assertThat(RiftMessages.GUI_SETTING_SAVED.optionalPlaceholders()).containsExactly("prefix");
    }

    @Test
    void editableReferenceContainsEveryTextMessage() throws Exception {
        String toml = RiftLocalization.referenceToml();

        assertThat(TomlLanguageParser.parseText(toml).keySet())
                .containsExactlyInAnyOrderElementsOf(RiftMessages.catalog().ids());
        assertThat(toml)
                .contains("[runtime]", "prefix = \"<bold><gradient:#6f2dbd:#d16ba5>Rift</gradient></bold>\"", "{world}", "{permission}",
                        "{before}  previous value", "{after}  new value")
                .doesNotContain("messages:");
    }
}
