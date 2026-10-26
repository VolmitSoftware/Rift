package com.volmit.rift.command;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorRoute;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.config.RiftConfig;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import com.volmit.rift.world.WorldPolicyService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class RiftCommandContractTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rootCompletionRequiresEachWorldCommandsPermission() {
        RiftCommandService service = commandService();
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("rift.command")).thenReturn(true);

        assertThat(service.onTabComplete(sender, mock(Command.class), "rift", new String[]{""}))
                .doesNotContain("check", "check-all", "unmanage", "create", "config", "version");

        when(sender.hasPermission("rift.check")).thenReturn(true);
        when(sender.hasPermission("rift.unmanage")).thenReturn(true);

        assertThat(service.onTabComplete(sender, mock(Command.class), "rift", new String[]{""}))
                .contains("check", "check-all", "unmanage")
                .doesNotContain("create", "config", "version");
    }

    @Test
    void everyDirectorPathIsGatedByTheCommandItRoutesTo() {
        RiftCommandService service = commandService();
        CommandSender sender = mock(CommandSender.class);
        List<DirectorRuntimeNode> nodes = new ArrayList<>();
        collect(service.director().getRoot(), nodes);
        assertThat(nodes).hasSizeGreaterThan(40);
        int routed = 0;
        for (DirectorRuntimeNode node : nodes) {
            List<String> canonical = new DirectorRoute(node, List.of()).path();
            List<String> required = RiftCommandService.requiredPermissions(canonical);
            assertThat(required).as(String.join(" ", canonical)).isNotEqualTo(List.of("rift.admin"));
            for (List<String> variant : variants(node)) {
                DirectorRoute route = service.route(sender, "rift", variant.toArray(String[]::new));
                if (route.node() == node) {
                    assertThat(RiftCommandService.requiredPermissions(route.path())).as(String.join(" ", variant)).isEqualTo(required);
                    routed++;
                }
            }
            assertThat(service.route(sender, "rift", canonical.toArray(String[]::new)).node()).as(String.join(" ", canonical)).isSameAs(node);
        }
        assertThat(routed).isGreaterThan(nodes.size() * 2);
    }

    @Test
    void misspelledAndAbbreviatedSubcommandsNeedTheRoutedCommandsPermission() {
        RiftCommandService service = commandService();
        CommandSender sender = mock(CommandSender.class);

        assertThat(gate(service, sender, "delet", "lobby")).containsExactly("rift.delete");
        assertThat(gate(service, sender, "DELETE", "lobby")).containsExactly("rift.delete");
        assertThat(gate(service, sender, "creat", "arena")).containsExactly("rift.create");
        assertThat(gate(service, sender, "unmanag", "lobby")).containsExactly("rift.unmanage");
        assertThat(gate(service, sender, "teleport", "lobby")).containsExactly("rift.teleport");
        assertThat(gate(service, sender, "editor")).containsExactly("rift.config");
        assertThat(gate(service, sender, "polic", "pvp", "lobby", "DENY")).containsExactly("rift.policy");
        assertThat(gate(service, sender, "debu", "dum")).containsExactly("rift.debug");
        assertThat(gate(service, sender, "debug", "versio")).containsExactly("rift.command");
        assertThat(gate(service, sender, "zzzzzzzz")).containsExactly("rift.command");
        assertThat(gate(service, sender, "help")).containsExactly("rift.command");
        assertThat(gate(service, sender)).containsExactly("rift.command");
        assertThat(RiftCommandService.requiredPermissions(List.of("unregistered"))).containsExactly("rift.admin");
    }

    @Test
    void abbreviatedLanguageCommandsReachTheLanguageSwitcherWithTheirArguments() {
        RiftCommandService service = commandService();
        DirectorRoute route = service.route(mock(CommandSender.class), "rift", new String[]{"lang", "server", "de_DE"});

        assertThat(route.path()).containsExactly("language");
        assertThat(route.remainingArgs()).containsExactly("server", "de_DE");
        assertThat(RiftCommandService.requiredPermissions(route.path())).isEmpty();
    }

    @Test
    void debugCompletionSeparatesPublicVersionFromDiagnosticDumpPermission() {
        RiftCommandService service = commandService();
        CommandSender reader = mock(CommandSender.class);
        when(reader.hasPermission("rift.command")).thenReturn(true);

        assertThat(service.onTabComplete(reader, mock(Command.class), "rift", new String[]{"debug", ""}))
                .contains("version").doesNotContain("dump");
        assertThat(service.onTabComplete(reader, mock(Command.class), "rift", new String[]{"debug", "dump", ""}))
                .isEmpty();

        CommandSender operator = mock(CommandSender.class);
        when(operator.hasPermission("rift.debug")).thenReturn(true);

        assertThat(service.onTabComplete(operator, mock(Command.class), "rift", new String[]{"debug", ""}))
                .contains("dump").doesNotContain("version");
    }

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
        DirectorMiniMenu.DirectorHelpPage policy = DirectorMiniMenu.resolveHelp(service.director(), List.of("policy")).orElseThrow();
        assertThat(root.entries()).noneMatch(node -> node.getDescriptor().getName().equals("version"));
        assertThat(debug.entries()).anyMatch(node -> node.getDescriptor().getName().equals("version"));
        List<String> policyCommands = new ArrayList<>();
        int policyPages = DirectorMiniMenu.helpWindow(policy, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH).pages();
        for (int page = 1; page <= policyPages; page++) {
            DirectorMiniMenu.DirectorHelpPage help = DirectorMiniMenu.resolveHelp(service.director(),
                    List.of("policy", "help=" + page)).orElseThrow();
            DirectorMiniMenu.ContentPage shown = DirectorMiniMenu.helpWindow(help, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);
            policyCommands.addAll(help.entries().subList(shown.startIndex(), shown.endIndex()).stream()
                    .map(node -> node.getDescriptor().getName()).toList());
        }
        assertThat(policyCommands)
                .contains("show", "difficulty", "pvp", "gamerule", "spawn", "border", "border-center", "border-warning",
                        "border-damage", "access", "respawn", "tag", "gamemode", "flight", "weather",
                        "natural-animals", "natural-monsters", "player-limit", "travel-entry", "travel-exit", "first-join", "login");

        RemoteConsoleCommandSender sender = mock(RemoteConsoleCommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        Command command = mock(Command.class);
        try (MockedStatic<FoliaScheduler> scheduling = mockStatic(FoliaScheduler.class)) {
            scheduling.when(FoliaScheduler::isPrimaryThread).thenReturn(true);
            for (String[] arguments : List.of(new String[]{"version"}, new String[]{"debug", "version"})) {
                clearInvocations(sender);
                assertThat(service.onCommand(sender, command, "rift", arguments)).isTrue();
                ArgumentCaptor<String> output = ArgumentCaptor.forClass(String.class);
                verify(sender).sendRichMessage(output.capture());
                assertThat(output.getValue()).isEqualTo("<gradient:#6F2DBD:#D16BA5>PORTAL v2.7.4");
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
                String.class,
                boolean.class,
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
        Method unmanage = RiftCommands.class.getDeclaredMethod(
                "unmanage",
                String.class,
                CommandSender.class
        );
        Method check = RiftCommands.class.getDeclaredMethod(
                "check",
                String.class,
                int.class,
                CommandSender.class
        );

        assertThat(command.name()).isEqualTo("tp");
        assertThat(command.aliases()).containsExactly("teleport");
        assertThat(language.getAnnotation(Director.class).name()).isEqualTo("language");
        assertThat(RiftDebugCommands.class.getAnnotation(Director.class).name()).isEqualTo("debug");
        assertThat(RiftPolicyCommands.class.getAnnotation(Director.class).name()).isEqualTo("policy");
        assertThat(debugDump.getAnnotation(Director.class).name()).isEqualTo("dump");
        assertThat(status.getAnnotation(Director.class).name()).isEqualTo("status");
        assertThat(status.getAnnotation(Director.class).aliases()).doesNotContain("doctor");
        assertThat(unmanage.getAnnotation(Director.class).name()).isEqualTo("unmanage");
        assertThat(check.getAnnotation(Director.class).name()).isEqualTo("check");
        Param page = list.getParameters()[0].getAnnotation(Param.class);
        assertThat(page.name()).isEqualTo("page");
        assertThat(page.defaultValue()).isEqualTo("1");
        assertThat(page.descriptionKey()).isEqualTo("rift.parameter.page");
        Param generatorPage = generators.getParameters()[0].getAnnotation(Param.class);
        assertThat(generatorPage.name()).isEqualTo("page");
        assertThat(generatorPage.defaultValue()).isEqualTo("1");
        assertThat(generatorPage.descriptionKey()).isEqualTo("rift.parameter.page");
        Param checkPage = check.getParameters()[1].getAnnotation(Param.class);
        assertThat(checkPage.name()).isEqualTo("page");
        assertThat(checkPage.defaultValue()).isEqualTo("1");
        Param upload = debugDump.getParameters()[0].getAnnotation(Param.class);
        assertThat(upload.defaultValue()).isEqualTo("true");
        assertThat(upload.descriptionKey()).isEqualTo("rift.parameter.upload");
        assertThat(Arrays.stream(RiftCommands.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("reload", "doctor");
    }

    @Test
    void additionalPoliciesOfferCompleteValueSuggestionsAndNormalizeCase() throws Exception {
        RiftPolicyCommands.GameModeChoice gameModes = new RiftPolicyCommands.GameModeChoice();
        RiftPolicyCommands.PermissionChoice permissions = new RiftPolicyCommands.PermissionChoice();
        RiftPolicyCommands.WeatherChoice weather = new RiftPolicyCommands.WeatherChoice();
        assertThat(gameModes.getPossibilities()).containsExactly("INHERIT", "SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR");
        assertThat(permissions.getPossibilities()).containsExactly("INHERIT", "ALLOW", "DENY");
        assertThat(weather.getPossibilities()).containsExactly("INHERIT", "CLEAR", "RAIN", "THUNDER");
        assertThat(gameModes.parse("creative", false)).isEqualTo("CREATIVE");
        assertThat(permissions.parse("deny", false)).isEqualTo("DENY");
        assertThat(weather.parse("rain", false)).isEqualTo("RAIN");
        RiftCommandService service = commandService();
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("rift.command")).thenReturn(true);
        when(sender.hasPermission("rift.policy")).thenReturn(true);
        assertThat(service.onTabComplete(sender, mock(Command.class), "rift", new String[]{"policy", "we"}))
                .contains("weather");
        for (String methodName : List.of("gameMode", "flight", "weather", "naturalAnimals", "naturalMonsters", "travelEntry", "travelExit")) {
            Method method = RiftPolicyCommands.class.getDeclaredMethod(methodName, String.class, String.class, CommandSender.class);
            Param value = method.getParameters()[1].getAnnotation(Param.class);
            assertThat(value.customHandler()).isIn(RiftPolicyCommands.GameModeChoice.class,
                    RiftPolicyCommands.PermissionChoice.class, RiftPolicyCommands.WeatherChoice.class);
            assertThat(method.getAnnotation(Director.class).descriptionKey()).startsWith("rift.command.policy_");
        }
    }

    @Test
    void placementSuggestionsIncludeManagedWorldsAndServerDefault() throws Exception {
        Rift plugin = mock(Rift.class);
        WorldProfileStore profiles = mock(WorldProfileStore.class);
        when(plugin.profiles()).thenReturn(profiles);
        WorldProfile lobby = new WorldProfile();
        lobby.setName("Lobby");
        when(profiles.all()).thenReturn(List.of(lobby));
        try (MockedStatic<Rift> singleton = mockStatic(Rift.class)) {
            singleton.when(Rift::get).thenReturn(plugin);
            RiftPolicyCommands.DestinationChoice choices = new RiftPolicyCommands.DestinationChoice();
            assertThat(choices.getPossibilities()).containsExactly("default", "Lobby");
            assertThat(choices.parse("lobby", false)).isEqualTo("Lobby");
            assertThat(choices.parse("DEFAULT", false)).isEqualTo("default");
        }
        for (String name : List.of("firstJoin", "login")) {
            Method method = RiftPolicyCommands.class.getDeclaredMethod(name, String.class, String.class, CommandSender.class);
            assertThat(method.getParameters()[1].getAnnotation(Param.class).customHandler())
                    .isEqualTo(RiftPolicyCommands.DestinationChoice.class);
        }
        Method limit = RiftPolicyCommands.class.getDeclaredMethod("playerLimit", String.class, int.class, CommandSender.class);
        assertThat(limit.getAnnotation(Director.class).name()).isEqualTo("player-limit");
        assertThat(limit.getParameters()[1].getAnnotation(Param.class).descriptionKey()).isEqualTo("rift.parameter.player_limit");
    }

    @Test
    void admissionCommandsRequirePolicyPermissionAndUseTheTypedSetters() {
        Rift plugin = mock(Rift.class);
        WorldPolicyService policies = mock(WorldPolicyService.class);
        when(plugin.worldPolicies()).thenReturn(policies);
        when(plugin.language()).thenReturn(mock(RiftLocalization.class));
        RiftPolicyCommands commands = new RiftPolicyCommands(plugin);
        CommandSender sender = mock(CommandSender.class);
        commands.playerLimit("example", 5, sender);
        commands.travelEntry("example", "DENY", sender);
        commands.travelExit("example", "ALLOW", sender);
        commands.firstJoin("example", "lobby", sender);
        commands.login("example", "default", sender);
        verifyNoInteractions(policies);
        when(sender.hasPermission("rift.policy")).thenReturn(true);
        commands.playerLimit("example", 5, sender);
        commands.travelEntry("example", "DENY", sender);
        commands.travelExit("example", "ALLOW", sender);
        commands.firstJoin("example", "lobby", sender);
        commands.login("example", "default", sender);
        verify(policies).setPlayerLimit(sender, "example", 5);
        verify(policies).setTravelEntry(sender, "example", "DENY");
        verify(policies).setTravelExit(sender, "example", "ALLOW");
        verify(policies).setFirstJoin(sender, "example", "lobby");
        verify(policies).setLogin(sender, "example", "default");
    }

    private static List<String> gate(RiftCommandService service, CommandSender sender, String... args) {
        return RiftCommandService.requiredPermissions(service.route(sender, "rift", args).path());
    }

    private static void collect(DirectorRuntimeNode node, List<DirectorRuntimeNode> nodes) {
        for (DirectorRuntimeNode child : node.getChildren()) {
            nodes.add(child);
            collect(child, nodes);
        }
    }

    private static List<List<String>> variants(DirectorRuntimeNode node) {
        List<DirectorRuntimeNode> chain = new ArrayList<>();
        for (DirectorRuntimeNode cursor = node; cursor.getParent() != null; cursor = cursor.getParent()) {
            chain.addFirst(cursor);
        }
        List<List<String>> variants = new ArrayList<>();
        for (int index = 0; index < chain.size(); index++) {
            for (String form : forms(chain.get(index))) {
                List<String> variant = new ArrayList<>();
                for (int other = 0; other < chain.size(); other++) {
                    variant.add(other == index ? form : chain.get(other).getDescriptor().getName());
                }
                variants.add(variant);
            }
        }
        return variants;
    }

    private static Set<String> forms(DirectorRuntimeNode node) {
        Set<String> forms = new LinkedHashSet<>();
        for (String name : node.allNames()) {
            forms.add(name);
            forms.add(name.toUpperCase(Locale.ROOT));
            if (name.length() > 4) {
                forms.add(name.substring(0, name.length() - 1));
                forms.add(name.substring(0, 1) + name.charAt(2) + name.charAt(1) + name.substring(3));
            }
        }
        return forms;
    }

    private RiftCommandService commandService() {
        Rift plugin = mock(Rift.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(RiftCommandContractTest.class.getName()));
        RiftLocalization language = new RiftLocalization(plugin, () -> new RiftConfig().normalize(),
                temporaryDirectory.resolve("languages").toFile());
        assertThat(language.loadInitial()).isTrue();
        when(plugin.language()).thenReturn(language);
        return new RiftCommandService(plugin);
    }
}
