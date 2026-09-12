package com.volmit.rift.command;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.config.RiftConfig;
import com.volmit.rift.localization.RiftLocalization;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class RiftCommandContractTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void versionIsListedOnlyUnderDebugAndBothPathsSendTheInstalledVersion() throws Exception {
        Rift plugin = mock(Rift.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(RiftCommandContractTest.class.getName()));
        RiftLocalization language = new RiftLocalization(plugin, () -> new RiftConfig().normalize(),
                temporaryDirectory.resolve("languages").toFile());
        assertThat(language.loadInitial()).isTrue();
        language.install(language.prepareSnapshot("en_US", "[runtime]\nprefix = \"&b&lPORTAL\"\n"));
        when(plugin.language()).thenReturn(language);
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("Rift", "2.7.4", "example.Main"));
        RiftCommandService service = new RiftCommandService(plugin);
        DirectorMiniMenu.DirectorHelpPage root = DirectorMiniMenu.resolveHelp(service.director(), List.of()).orElseThrow();
        DirectorMiniMenu.DirectorHelpPage debug = DirectorMiniMenu.resolveHelp(service.director(), List.of("debug")).orElseThrow();
        assertThat(root.entries()).noneMatch(node -> node.getDescriptor().getName().equals("version"));
        assertThat(debug.entries()).anyMatch(node -> node.getDescriptor().getName().equals("version"));

        RemoteConsoleCommandSender sender = mock(RemoteConsoleCommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        Command command = mock(Command.class);
        try (MockedStatic<FoliaScheduler> scheduling = mockStatic(FoliaScheduler.class)) {
            scheduling.when(FoliaScheduler::isPrimaryThread).thenReturn(true);
            for (String[] arguments : List.of(new String[]{"version"}, new String[]{"debug", "version"})) {
                clearInvocations(sender);
                assertThat(service.onCommand(sender, command, "rift", arguments)).isTrue();
                ArgumentCaptor<String> output = ArgumentCaptor.forClass(String.class);
                verify(sender).sendMessage(output.capture());
                assertThat(output.getValue()).isEqualTo("PORTAL v2.7.4");
            }
        }
    }

    @Test
    void exposesCanonicalCommandsAndOmitsRetiredReloadAndDoctor() throws Exception {
        Method teleport = RiftCommands.class.getDeclaredMethod(
                "teleport",
                String.class,
                CommandSender.class
        );
        Director command = teleport.getAnnotation(Director.class);
        Method language = RiftCommands.class.getDeclaredMethod(
                "language",
                CommandSender.class
        );
        Method debugDump = RiftDebugCommands.class.getDeclaredMethod(
                "dump",
                boolean.class,
                CommandSender.class
        );
        Method list = RiftCommands.class.getDeclaredMethod(
                "list",
                int.class,
                CommandSender.class
        );
        Method status = RiftCommands.class.getDeclaredMethod(
                "status",
                CommandSender.class
        );
        Method generators = RiftCommands.class.getDeclaredMethod(
                "generators",
                int.class,
                CommandSender.class
        );

        assertThat(command.name()).isEqualTo("tp");
        assertThat(command.aliases()).containsExactly("teleport");
        assertThat(language.getAnnotation(Director.class).name()).isEqualTo("language");
        assertThat(RiftDebugCommands.class.getAnnotation(Director.class).name()).isEqualTo("debug");
        assertThat(debugDump.getAnnotation(Director.class).name()).isEqualTo("dump");
        assertThat(status.getAnnotation(Director.class).name()).isEqualTo("status");
        assertThat(status.getAnnotation(Director.class).aliases()).doesNotContain("doctor");
        Param page = list.getParameters()[0].getAnnotation(Param.class);
        assertThat(page.name()).isEqualTo("page");
        assertThat(page.defaultValue()).isEqualTo("1");
        assertThat(page.descriptionKey()).isEqualTo("rift.parameter.page");
        Param generatorPage = generators.getParameters()[0].getAnnotation(Param.class);
        assertThat(generatorPage.name()).isEqualTo("page");
        assertThat(generatorPage.defaultValue()).isEqualTo("1");
        assertThat(generatorPage.descriptionKey()).isEqualTo("rift.parameter.page");
        Param upload = debugDump.getParameters()[0].getAnnotation(Param.class);
        assertThat(upload.defaultValue()).isEqualTo("true");
        assertThat(upload.descriptionKey()).isEqualTo("rift.parameter.upload");
        assertThat(Arrays.stream(RiftCommands.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("reload", "doctor");
    }
}
