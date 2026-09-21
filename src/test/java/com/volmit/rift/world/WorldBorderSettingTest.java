package com.volmit.rift.world;

import com.volmit.rift.storage.WorldProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class WorldBorderSettingTest {
    @Test
    void adjustmentsPreserveComplementaryFieldsAndUseLatestValue() {
        WorldProfile profile = new WorldProfile();
        profile.setBorderCenterZ(73);
        WorldBorderSetting.CENTER_X.adjust(profile, 10, false);
        WorldBorderSetting.CENTER_X.adjust(profile, -1, false);
        assertThat(profile.getBorderCenterX()).isEqualTo(9);
        assertThat(profile.getBorderCenterZ()).isEqualTo(73);
        assertThat(profile.isManagedBorder()).isFalse();
    }

    @Test
    void damageStepsPreserveDecimalPrecision() {
        WorldProfile profile = new WorldProfile();
        WorldBorderSetting.DAMAGE_AMOUNT.adjust(profile, 1, false);
        assertThat(profile.getBorderDamageAmount()).isEqualTo(0.3D);
        WorldBorderSetting.DAMAGE_AMOUNT.adjust(profile, -1, false);
        assertThat(profile.getBorderDamageAmount()).isEqualTo(0.2D);
    }

    @Test
    void eachResetUsesTheDocumentedProfileDefault() {
        WorldProfile profile = new WorldProfile();
        for (WorldBorderSetting setting : WorldBorderSetting.values()) {
            setting.adjust(profile, -10, false);
            setting.adjust(profile, 10, false);
            setting.adjust(profile, 0, true);
            assertThat(setting.read(profile)).isEqualTo(setting.defaultValue());
        }
    }

    @Test
    void numericBoundsMatchThePersistedProfile() {
        WorldProfile profile = new WorldProfile();
        WorldBorderSetting.SIZE.adjust(profile, Double.MAX_VALUE, false);
        assertThat(profile.getBorderSize()).isEqualTo(59_999_968D);
        WorldBorderSetting.SIZE.adjust(profile, -Double.MAX_VALUE, false);
        assertThat(profile.getBorderSize()).isEqualTo(1D);
        WorldBorderSetting.CENTER_X.adjust(profile, -Double.MAX_VALUE, false);
        assertThat(profile.getBorderCenterX()).isEqualTo(-29_999_984D);
        WorldBorderSetting.WARNING_TIME.adjust(profile, Double.MAX_VALUE, false);
        assertThat(profile.getBorderWarningTime()).isEqualTo(Integer.MAX_VALUE / 20);
    }
}
