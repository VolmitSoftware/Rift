package com.volmit.rift.command;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

final class RiftCommandContractTest {
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
