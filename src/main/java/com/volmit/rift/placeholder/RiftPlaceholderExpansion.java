package com.volmit.rift.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

final class RiftPlaceholderExpansion extends PlaceholderExpansion {
    private final RiftPlaceholderValues values;
    private final String version;

    RiftPlaceholderExpansion(RiftPlaceholderValues values, String version) {
        this.values = values;
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "rift";
    }

    @Override
    public String getAuthor() {
        return "VolmitSoftware";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return values.resolve(player == null ? null : player.getUniqueId(), params);
    }
}
