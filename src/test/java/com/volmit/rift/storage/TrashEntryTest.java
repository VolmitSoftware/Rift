package com.volmit.rift.storage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class TrashEntryTest {
    @Test
    void quarantineRoundTripPreservesEveryWorldPolicy() {
        WorldProfile source = new WorldProfile();
        source.setName("policy-world");
        source.setDifficulty("HARD");
        source.setPvp("DENY");
        source.setGameRules(Map.of("minecraft:keep_inventory", "true"));
        source.setCustomSpawn(true);
        source.setSpawnX(2.5D);
        source.setSpawnY(80.0D);
        source.setSpawnZ(-7.5D);
        source.setSpawnYaw(90.0F);
        source.setManagedBorder(true);
        source.setBorderSize(500.0D);
        source.setBorderCenterX(12.0D);
        source.setBorderCenterZ(-12.0D);
        source.setBorderWarningDistance(9);
        source.setBorderWarningTime(8);
        source.setBorderDamageAmount(0.4D);
        source.setBorderDamageBuffer(3.0D);
        source.setAccessPermission("rift.world.policy-world");
        source.setAccessDeniedMessage("No entry to {world}");
        source.setRespawnWorld("lobby");
        source.setTags(List.of("event", "private"));

        WorldProfile restored = TrashEntry.from("entry", source).toProfile();

        assertThat(restored.getDifficulty()).isEqualTo(source.getDifficulty());
        assertThat(restored.getPvp()).isEqualTo(source.getPvp());
        assertThat(restored.getGameRules()).isEqualTo(source.getGameRules());
        assertThat(restored.isCustomSpawn()).isTrue();
        assertThat(restored.getSpawnX()).isEqualTo(source.getSpawnX());
        assertThat(restored.getSpawnY()).isEqualTo(source.getSpawnY());
        assertThat(restored.getSpawnZ()).isEqualTo(source.getSpawnZ());
        assertThat(restored.getSpawnYaw()).isEqualTo(source.getSpawnYaw());
        assertThat(restored.isManagedBorder()).isTrue();
        assertThat(restored.getBorderSize()).isEqualTo(source.getBorderSize());
        assertThat(restored.getBorderCenterX()).isEqualTo(source.getBorderCenterX());
        assertThat(restored.getBorderCenterZ()).isEqualTo(source.getBorderCenterZ());
        assertThat(restored.getBorderWarningDistance()).isEqualTo(source.getBorderWarningDistance());
        assertThat(restored.getBorderWarningTime()).isEqualTo(source.getBorderWarningTime());
        assertThat(restored.getBorderDamageAmount()).isEqualTo(source.getBorderDamageAmount());
        assertThat(restored.getBorderDamageBuffer()).isEqualTo(source.getBorderDamageBuffer());
        assertThat(restored.getAccessPermission()).isEqualTo(source.getAccessPermission());
        assertThat(restored.getAccessDeniedMessage()).isEqualTo(source.getAccessDeniedMessage());
        assertThat(restored.getRespawnWorld()).isEqualTo(source.getRespawnWorld());
        assertThat(restored.getTags()).containsExactlyElementsOf(source.getTags());
    }
}
