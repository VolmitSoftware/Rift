package com.volmit.rift.gui;

import com.volmit.rift.config.RiftConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

final class RiftConfigMenuCoverageTest {
    @Test
    void everyPersistedConfigFieldHasAnEditorSetting() {
        Set<String> persistedFields = Arrays.stream(RiftConfig.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toUnmodifiableSet());

        assertThat(RiftConfigMenu.editablePaths()).containsExactlyInAnyOrderElementsOf(persistedFields);
        assertThat(persistedFields).hasSize(25);
        assertThat(RiftConfigMenu.languageUsesPicker()).isTrue();
        assertThat(RiftConfigMenu.rootCategorySlots()).containsValue("LANGUAGES");
    }
}
