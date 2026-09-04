package com.volmit.rift.command;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

final class RiftDirectorMenuTest {
    @Test
    void listUsesPagedDirectorContentWithRiftAsItsParent() {
        List<String> entries = IntStream.range(0, 16)
                .mapToObj(index -> "entry-" + index)
                .toList();
        DirectorMiniMenu.ContentMenu menu = RiftCommands.listMenu("Worlds", entries, "empty", 2);

        assertThat(menu.command()).isEqualTo("/rift list");
        assertThat(menu.parentCommand()).isEqualTo("/rift");
        assertThat(menu.page()).isEqualTo(new DirectorMiniMenu.ContentPage(2, 2, 15, 16, 16));
        assertThat(DirectorMiniMenu.renderContent(menu, RiftCommandService.theme(), DirectorTextResolver.ENGLISH))
                .anyMatch(line -> line.contains("/rift list page=1"));
    }

    @Test
    void statusUsesAnUnpagedDirectorContentMenu() {
        DirectorMiniMenu.ContentMenu menu = RiftCommands.statusMenu("Rift Status", List.of("one", "two"));

        assertThat(menu.command()).isEqualTo("/rift status");
        assertThat(menu.parentCommand()).isEqualTo("/rift");
        assertThat(menu.page()).isEqualTo(new DirectorMiniMenu.ContentPage(1, 1, 0, 2, 2));
        assertThat(DirectorMiniMenu.renderContent(menu, RiftCommandService.theme(), DirectorTextResolver.ENGLISH))
                .anyMatch(line -> line.contains("<click:run_command:/rift>"));
    }

    @Test
    void generatorsUsePagedDirectorContentWithRiftAsItsParent() {
        List<String> entries = IntStream.range(0, 16)
                .mapToObj(index -> "generator-" + index)
                .toList();
        DirectorMiniMenu.ContentMenu menu = RiftCommands.generatorMenu("Generators", entries, 2);

        assertThat(menu.command()).isEqualTo("/rift generators");
        assertThat(menu.parentCommand()).isEqualTo("/rift");
        assertThat(menu.page()).isEqualTo(new DirectorMiniMenu.ContentPage(2, 2, 15, 16, 16));
        assertThat(DirectorMiniMenu.renderContent(menu, RiftCommandService.theme(), DirectorTextResolver.ENGLISH))
                .anyMatch(line -> line.contains("/rift generators page=1"));
    }
}
