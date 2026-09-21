package com.volmit.rift;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

final class RuntimeJarTest {
    private static final String PLUGIN_PACKAGE = "com.volmit.rift.";
    private static final String LIBRARY_PACKAGE = PLUGIN_PACKAGE + "libs.";
    private static RuntimeLoader loader;

    @BeforeAll
    static void openRuntimeLoader() throws Exception {
        Path artifact = Path.of(Objects.requireNonNull(System.getProperty("rift.runtimeJar")));
        Path libraries = Path.of(Objects.requireNonNull(System.getProperty("rift.runtimeTestLibraries")));
        loader = new RuntimeLoader(new URL[]{artifact.toUri().toURL(), libraries.toUri().toURL()},
                RuntimeJarTest.class.getClassLoader());
    }

    @AfterAll
    static void closeRuntimeLoader() throws Exception {
        if (loader != null) {
            loader.close();
        }
    }

    @Test
    void pluginClassLoadsBeforeDownloadedLibraries() throws Exception {
        Path artifact = Path.of(Objects.requireNonNull(System.getProperty("rift.runtimeJar")));
        try (RuntimeLoader bootstrapLoader = new RuntimeLoader(new URL[]{artifact.toUri().toURL()},
                RuntimeJarTest.class.getClassLoader())) {
            assertThat(Class.forName(PLUGIN_PACKAGE + "Rift", true, bootstrapLoader)).isNotNull();
        }
    }

    @Test
    void packagedAdventureLoadsSerializers() throws Exception {
        Class<?> textType = loader.loadClass(LIBRARY_PACKAGE + "volmlib.util.plugin.ComponentText");
        Object text = textType.getMethod("markup", String.class).invoke(null, "<red>Rift</red>");
        assertThat(textType.getMethod("plain").invoke(text)).isEqualTo("Rift");
        assertThat((String) textType.getMethod("legacy").invoke(text)).endsWith("Rift");
        assertThat((String) textType.getMethod("miniMessage").invoke(text)).contains("<red>Rift");
    }

    @Test
    void packagedTomlPreservesConfigurationAndPersistenceFields() throws Exception {
        assertTomlField("config.RiftConfig", "language", "fr_FR");
        assertTomlField("storage.WorldProfile", "name", "packaging-world");
        assertTomlField("storage.TrashEntry", "worldName", "packaging-world");
    }

    @Test
    void packagedCommandsRetainReflectionMetadata() throws Exception {
        Class<?> commands = loader.loadClass(PLUGIN_PACKAGE + "command.RiftCommands");
        assertThat(Arrays.stream(commands.getDeclaredMethods()).map(method -> method.getName()))
                .contains("status", "create", "load", "unload");
        assertThat(Arrays.stream(commands.getDeclaredAnnotations())
                .map(annotation -> annotation.annotationType().getName()))
                .contains(LIBRARY_PACKAGE + "volmlib.util.director.annotations.Director");
        assertThat(loader.loadClass(PLUGIN_PACKAGE + "command.RiftCommandHandlers$KnownWorld")
                .getDeclaredConstructors()).isNotEmpty();
    }

    private void assertTomlField(String typeName, String fieldName, String value) throws Exception {
        Class<?> type = loader.loadClass(PLUGIN_PACKAGE + typeName);
        Class<?> codec = loader.loadClass(LIBRARY_PACKAGE + "volmlib.util.config.TomlCodec");
        Object decoded = codec.getMethod("fromToml", String.class, Class.class)
                .invoke(null, fieldName + " = \"" + value + "\"\n", type);
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        assertThat(field.get(decoded)).isEqualTo(value);
        String encoded = (String) codec.getMethod("toToml", Object.class, String.class)
                .invoke(null, decoded, "Rift");
        assertThat(encoded).contains(fieldName + " = \"" + value + "\"");
    }

    private static final class RuntimeLoader extends URLClassLoader {
        private RuntimeLoader(URL[] artifacts, ClassLoader parent) {
            super(artifacts, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(PLUGIN_PACKAGE)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
