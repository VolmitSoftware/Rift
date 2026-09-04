package com.volmit.rift.gui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiftConfigMenuLayoutTest {
    @Test
    void usesAFullChestInventory() {
        assertThat(RiftConfigMenu.inventorySize()).isEqualTo(54);
        assertThat(RiftConfigMenu.inventorySize() % 9).isZero();
    }

    @Test
    void routesFiveRootCategoriesWithoutNavigationOverlap() {
        assertThat(RiftConfigMenu.rootCategorySlots()).containsExactlyInAnyOrderEntriesOf(Map.of(
                20, "GENERAL",
                21, "FEEDBACK",
                22, "PRESENTATION",
                23, "DIAGNOSTICS",
                24, "LANGUAGES"
        ));
        assertThat(RiftConfigMenu.navigationSlots())
                .doesNotContainAnyElementsOf(RiftConfigMenu.rootCategorySlots().keySet());
        assertThat(RiftConfigMenu.navigationSlots()).containsExactlyInAnyOrder(45, 53);
        RiftConfigMenu.rootCategorySlots().forEach((slot, category) ->
                assertThat(RiftConfigMenu.rootCategoryNameAt(slot)).isEqualTo(category));
        assertThat(RiftConfigMenu.rootCategoryNameAt(9)).isNull();
    }

    @Test
    void routesEverySupportedCategorySizeAboveNavigation() {
        for (int count = 1; count <= 45; count++) {
            int[] slots = RiftConfigMenu.settingSlots(count);
            Set<Integer> unique = Arrays.stream(slots).boxed().collect(Collectors.toSet());
            assertThat(slots).hasSize(count);
            assertThat(unique).hasSize(count);
            assertThat(unique).allMatch(slot -> slot >= 0 && slot < 45);
            assertThat(unique).doesNotContainAnyElementsOf(RiftConfigMenu.navigationSlots());
        }
    }

    @Test
    void centersEveryCurrentCategoryLayout() {
        assertThat(RiftConfigMenu.settingSlots(5)).containsExactly(20, 21, 22, 23, 24);
        assertThat(RiftConfigMenu.settingSlots(6)).containsExactly(19, 20, 21, 22, 23, 24);
        assertThat(RiftConfigMenu.settingSlots(9)).containsExactly(18, 19, 20, 21, 22, 23, 24, 25, 26);
    }

    @Test
    void rejectsUnsupportedCategorySizes() {
        assertThatThrownBy(() -> RiftConfigMenu.settingSlots(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RiftConfigMenu.settingSlots(46)).isInstanceOf(IllegalArgumentException.class);
    }

}
