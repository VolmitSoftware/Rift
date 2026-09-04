package com.volmit.rift.command;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.MessageArgs;
import com.volmit.rift.Rift;
import com.volmit.rift.localization.RiftMessages;
import org.bukkit.command.CommandSender;

@Director(name = "debug", description = "Rift diagnostic tools", descriptionKey = "rift.command.debug")
public final class RiftDebugCommands {
    private final Rift plugin;

    public RiftDebugCommands(Rift plugin) {
        this.plugin = plugin;
    }

    @Director(name = "dump", sync = true, description = "Create a comprehensive Rift diagnostic report", descriptionKey = "rift.command.debug_dump")
    public void dump(
            @Param(name = "upload", defaultValue = "true", description = "Upload the report when public uploads are enabled", descriptionKey = "rift.parameter.upload") boolean upload,
            @Param(name = "sender", contextual = true) CommandSender sender
    ) {
        if (!sender.hasPermission("rift.debug") && !sender.hasPermission("rift.admin")) {
            plugin.language().send(sender, RiftMessages.PERMISSION_DENIED, MessageArgs.builder()
                    .untrusted("permission", "rift.debug")
                    .build());
            return;
        }
        plugin.debugDump().request(sender, upload);
    }
}
