package com.volmit.rift.world;

import com.volmit.rift.storage.WorldProfile;
import java.math.BigDecimal;

public enum WorldBorderSetting {
    SIZE, CENTER_X, CENTER_Z, WARNING_DISTANCE, WARNING_TIME, DAMAGE_AMOUNT, DAMAGE_BUFFER;

    public double read(WorldProfile profile) {
        return switch (this) {
            case SIZE -> profile.getBorderSize();
            case CENTER_X -> profile.getBorderCenterX();
            case CENTER_Z -> profile.getBorderCenterZ();
            case WARNING_DISTANCE -> profile.getBorderWarningDistance();
            case WARNING_TIME -> profile.getBorderWarningTime();
            case DAMAGE_AMOUNT -> profile.getBorderDamageAmount();
            case DAMAGE_BUFFER -> profile.getBorderDamageBuffer();
        };
    }

    public double step() {
        return this == DAMAGE_AMOUNT ? 0.1D : 1.0D;
    }

    public double defaultValue() {
        return read(new WorldProfile());
    }

    public void adjust(WorldProfile profile, double steps, boolean reset) {
        if (!Double.isFinite(steps)) {
            throw new IllegalArgumentException("Border adjustment must be finite");
        }
        double value = reset ? defaultValue() : clamp(this == DAMAGE_AMOUNT
                ? BigDecimal.valueOf(read(profile)).add(BigDecimal.valueOf(steps).multiply(BigDecimal.valueOf(step()))).doubleValue()
                : read(profile) + steps * step());
        switch (this) {
            case SIZE -> profile.setBorderSize(value);
            case CENTER_X -> profile.setBorderCenterX(value);
            case CENTER_Z -> profile.setBorderCenterZ(value);
            case WARNING_DISTANCE -> profile.setBorderWarningDistance((int) value);
            case WARNING_TIME -> profile.setBorderWarningTime((int) value);
            case DAMAGE_AMOUNT -> profile.setBorderDamageAmount(value);
            case DAMAGE_BUFFER -> profile.setBorderDamageBuffer(value);
        }
    }

    private double clamp(double value) {
        double minimum = switch (this) {
            case SIZE -> 1.0D;
            case CENTER_X, CENTER_Z -> -29_999_984.0D;
            default -> 0.0D;
        };
        double maximum = switch (this) {
            case SIZE -> 59_999_968.0D;
            case CENTER_X, CENTER_Z -> 29_999_984.0D;
            case WARNING_TIME -> Integer.MAX_VALUE / 20;
            case WARNING_DISTANCE -> Integer.MAX_VALUE;
            default -> Double.MAX_VALUE;
        };
        return Math.max(minimum, Math.min(maximum, value));
    }
}
