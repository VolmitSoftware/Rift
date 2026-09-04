package com.volmit.rift.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class WorldNamePolicyTest {
    @TempDir
    Path worldContainer;

    @Test
    void rejectsTraversalSeparatorsAndDeviceNames() {
        WorldNamePolicy policy = new WorldNamePolicy(worldContainer);

        assertThatThrownBy(() -> policy.requireValid("../outside")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireValid("folder/world")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireValid("folder\\world")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireValid("CON.dat")).isInstanceOf(IllegalArgumentException.class);
    }
}
