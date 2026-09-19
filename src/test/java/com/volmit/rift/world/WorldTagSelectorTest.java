package com.volmit.rift.world;

import com.volmit.rift.storage.WorldProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class WorldTagSelectorTest {
    @Test
    void filtersCaseInsensitivelyAndIncludesUnmanagedOnlyForAll() {
        WorldProfile profile = new WorldProfile();
        profile.setName("Adventure");
        profile.setTags(List.of("public", "survival"));
        WorldTagSelector selector = new WorldTagSelector(List.of(profile));
        List<WorldSnapshot> worlds = List.of(world("lobby"), world("Adventure"));

        assertThat(selector.select(worlds, " PUBLIC ")).extracting(WorldSnapshot::name).containsExactly("Adventure");
        assertThat(selector.select(worlds, "all")).extracting(WorldSnapshot::name).containsExactly("Adventure", "lobby");
        assertThat(selector.select(worlds, "missing")).isEmpty();
        assertThat(selector.group(worlds, "all")).containsOnlyKeys("", "public", "survival");
        assertThat(selector.group(worlds, "public")).containsOnlyKeys("public");
        assertThat(WorldTagSelector.normalize("Team:Builders")).isEqualTo("team:builders");
    }

    @Test
    void capturesTagsWithoutFollowingLaterMutationsAndRejectsCommandInjection() {
        WorldProfile profile = new WorldProfile();
        profile.setName("world");
        profile.setTags(List.of("public"));
        WorldTagSelector selector = new WorldTagSelector(List.of(profile));
        profile.setTags(List.of("private"));

        assertThat(selector.tags("WORLD")).containsExactly("public");
        assertThatThrownBy(() -> WorldTagSelector.normalize("public group=true"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private WorldSnapshot world(String name) {
        return new WorldSnapshot(name, false, false, true, false, false, "NORMAL", "", 0);
    }
}
