package com.volmit.rift.gui;

import art.arcane.volmlib.util.inventorygui.BukkitInventoryShutdown;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

final class OpenMenus {
    private OpenMenus() {
    }

    /** Closes every open menu of this holder type so its items cannot be taken once the click listener is gone. */
    static void close(Plugin plugin, Class<? extends InventoryHolder> holderType) {
        List<BukkitInventoryShutdown.View> open = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (holderType.isInstance(top.getHolder())) {
                open.add(new BukkitInventoryShutdown.View(player, top));
            }
        }
        BukkitInventoryShutdown.drain(plugin, open);
    }
}
