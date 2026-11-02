package com.volmit.rift.storage;

import art.arcane.volmlib.util.config.ConfigJson;
import art.arcane.volmlib.util.config.TomlCodec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class TrashEntryTest {
    @Test
    void quarantineRoundTripPreservesEveryProfileField() throws Exception {
        WorldProfile source = fullyCustomizedProfile();
        String manifest = TomlCodec.toToml(TrashEntry.from("20261101T120000000Z-policy-world", source), "test");

        WorldProfile restored = TrashEntry.parse(manifest).toProfile();

        assertThat(ConfigJson.toJson(restored, false)).isEqualTo(ConfigJson.toJson(source, false));
    }

    @Test
    void fixtureSetsEveryProfileFieldAwayFromItsDefault() throws Exception {
        WorldProfile defaults = new WorldProfile();
        WorldProfile custom = fullyCustomizedProfile();
        for (Field field : WorldProfile.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            assertThat(field.get(custom))
                    .as("fixture must customize WorldProfile.%s so quarantine coverage stays complete", field.getName())
                    .isNotEqualTo(field.get(defaults));
        }
    }

    @Test
    void manifestsWithoutAnEmbeddedProfileStillRestore() throws Exception {
        String legacy = """
                id = "20261020T120000000Z-old-world"
                worldName = "old-world"
                deletedAtEpochMillis = 1
                difficulty = "HARD"
                tags = ["event"]
                """;

        WorldProfile restored = TrashEntry.parse(legacy).toProfile();

        assertThat(restored.getName()).isEqualTo("old-world");
        assertThat(restored.getDifficulty()).isEqualTo("HARD");
        assertThat(restored.getTags()).containsExactly("event");
    }

    private static WorldProfile fullyCustomizedProfile() {
        WorldProfile profile = new WorldProfile();
        profile.setName("policy-world");
        profile.setEnvironment("NETHER");
        profile.setGenerator("void");
        profile.setWorldType("FLAT");
        profile.setSeed(42L);
        profile.setDirectory("world/dimensions/rift/policy-world");
        profile.setAutoLoad(false);
        profile.setProtectedWorld(true);
        profile.setDifficulty("HARD");
        profile.setPvp("DENY");
        profile.setGameMode("CREATIVE");
        profile.setFlight("ALLOW");
        profile.setWeather("RAIN");
        profile.setNaturalAnimals("DENY");
        profile.setNaturalMonsters("DENY");
        profile.setPlayerLimit(12);
        profile.setTravelEntry("DENY");
        profile.setTravelExit("DENY");
        profile.setFirstJoinWorld("lobby");
        profile.setLoginWorld("hub");
        profile.setGameRules(Map.of("minecraft:keep_inventory", "true"));
        profile.setCustomSpawn(true);
        profile.setSpawnX(2.5D);
        profile.setSpawnY(80.0D);
        profile.setSpawnZ(-7.5D);
        profile.setSpawnYaw(90.0F);
        profile.setManagedBorder(true);
        profile.setBorderSize(500.0D);
        profile.setBorderCenterX(12.0D);
        profile.setBorderCenterZ(-12.0D);
        profile.setBorderWarningDistance(9);
        profile.setBorderWarningTime(8);
        profile.setBorderDamageAmount(0.4D);
        profile.setBorderDamageBuffer(3.0D);
        profile.setAccessPermission("rift.world.policy-world");
        profile.setAccessDeniedMessage("No entry to {world}");
        profile.setRespawnWorld("lobby");
        profile.setTags(List.of("event", "private"));
        return profile;
    }
}
