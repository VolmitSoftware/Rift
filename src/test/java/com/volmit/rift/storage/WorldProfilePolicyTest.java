package com.volmit.rift.storage;

import art.arcane.volmlib.util.config.TomlCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class WorldProfilePolicyTest {
    @TempDir
    Path directory;

    @Test
    void omittedNewPoliciesInheritExistingServerBehavior() {
        WorldProfile profile = new WorldProfile().normalize(new WorldNamePolicy(directory));
        assertThat(profile.getGameMode()).isEqualTo("INHERIT");
        assertThat(profile.getFlight()).isEqualTo("INHERIT");
        assertThat(profile.getWeather()).isEqualTo("INHERIT");
        assertThat(profile.getNaturalAnimals()).isEqualTo("INHERIT");
        assertThat(profile.getNaturalMonsters()).isEqualTo("INHERIT");
        assertThat(profile.getPlayerLimit()).isZero();
        assertThat(profile.getTravelEntry()).isEqualTo("INHERIT");
        assertThat(profile.getTravelExit()).isEqualTo("INHERIT");
        assertThat(profile.getFirstJoinWorld()).isEmpty();
        assertThat(profile.getLoginWorld()).isEmpty();
    }

    @Test
    void policyChoicesNormalizeAndRoundTripThroughToml() throws IOException {
        WorldProfile profile = new WorldProfile();
        profile.setGameMode(" adventure ");
        profile.setFlight("allow");
        profile.setWeather("thunder");
        profile.setNaturalAnimals("deny");
        profile.setNaturalMonsters("allow");
        profile.setPlayerLimit(12);
        profile.setTravelEntry("deny");
        profile.setTravelExit(" allow ");
        profile.setFirstJoinWorld("welcome");
        profile.setLoginWorld("lobby");
        profile.normalize(new WorldNamePolicy(directory));
        WorldProfile parsed = TomlCodec.fromToml(TomlCodec.toToml(profile, "Rift world"), WorldProfile.class);
        assertThat(parsed.getGameMode()).isEqualTo("ADVENTURE");
        assertThat(parsed.getFlight()).isEqualTo("ALLOW");
        assertThat(parsed.getWeather()).isEqualTo("THUNDER");
        assertThat(parsed.getNaturalAnimals()).isEqualTo("DENY");
        assertThat(parsed.getNaturalMonsters()).isEqualTo("ALLOW");
        assertThat(parsed.getPlayerLimit()).isEqualTo(12);
        assertThat(parsed.getTravelEntry()).isEqualTo("DENY");
        assertThat(parsed.getTravelExit()).isEqualTo("ALLOW");
        assertThat(parsed.getFirstJoinWorld()).isEqualTo("welcome");
        assertThat(parsed.getLoginWorld()).isEqualTo("lobby");
    }

    @Test
    void invalidPoliciesAreRejectedBeforeInstallation() {
        List<Consumer<WorldProfile>> mutations = List.of(
                profile -> profile.setGameMode("builder"),
                profile -> profile.setFlight("yes"),
                profile -> profile.setWeather("snow"),
                profile -> profile.setNaturalAnimals("false"),
                profile -> profile.setNaturalMonsters("true"),
                profile -> profile.setPlayerLimit(-1),
                profile -> profile.setTravelEntry("true"),
                profile -> profile.setTravelExit("open"),
                profile -> profile.setFirstJoinWorld("../outside"),
                profile -> profile.setLoginWorld("bad name"));
        for (Consumer<WorldProfile> mutation : mutations) {
            WorldProfile profile = new WorldProfile();
            mutation.accept(profile);
            assertThatThrownBy(() -> profile.normalize(new WorldNamePolicy(directory)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
