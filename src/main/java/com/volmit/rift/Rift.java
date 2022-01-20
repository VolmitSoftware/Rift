package com.volmit.rift;

import com.volmit.rift.config.RiftConfig;
import com.volmit.rift.config.RiftWorldConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Rift extends JavaPlugin {
    public static Rift instance;
    private List<RiftWorldConfig> configs;

    public void onEnable()
    {
        instance = this;

        for(String i : RiftConfig.get().getDeleting())
        {
            File f = new File(i);

            if(f.exists())
            {
                info("Attempting to cleanup remaining deleted world files for " + i);
                deleteWorld(f);
            }
        }

        configs = RiftWorldConfig.loadAll();
    }

    public void onDisable()
    {

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!command.getName().equals("rift"))
        {
            return super.onCommand(sender, command, label, args);
        }

        String tag = ChatColor.DARK_GRAY + "[" + ChatColor.LIGHT_PURPLE + "Rift" + ChatColor.DARK_GRAY + "]" + ChatColor.GRAY + ": ";

        if(args.length == 0)
        {
            sender.sendMessage(tag + configs.size() + " World" + (configs.size() == 1 ? "" : "s") + ", Version " + getDescription().getVersion());
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift create"+ ChatColor.GRAY+" <name> [generator] [seed] [environment]");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift load"+ ChatColor.GRAY+"  <name> [generator]");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift import"+ ChatColor.GRAY+"  <name> <generator>");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift unload"+ ChatColor.GRAY+" <name>");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift delete"+ ChatColor.GRAY+" <name>");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift to"+ ChatColor.GRAY+" <world>");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift list");
        }

        else if(args[0].equalsIgnoreCase("create"))
        {

        }else if(args[0].equalsIgnoreCase("load"))
        {

        }else if(args[0].equalsIgnoreCase("import"))
        {
            
        }else if(args[0].equalsIgnoreCase("delete"))
        {
            World w = Bukkit.getWorld(args[1]);

            if(w != null)
            {
                for(Player i : w.getPlayers())
                {
                    evacuate(i);
                }

                for(Chunk i : w.getLoadedChunks())
                {
                    i.unload(true);
                }

                String n = w.getName();
                Bukkit.unloadWorld(w, true);
                sender.sendMessage(tag + "Unloaded " + ChatColor.WHITE + n);
            }

            File f = new File(args[1]);

            if(f.isDirectory() && f.exists())
            {
                deleteWorld(f);
            }
        }else if(args[0].equalsIgnoreCase("unload") && args.length >= 2)
        {
            World w = Bukkit.getWorld(args[1]);

            if(w != null)
            {
                for(Player i : w.getPlayers())
                {
                    evacuate(i);
                }

                for(Chunk i : w.getLoadedChunks())
                {
                    i.unload(true);
                }

                String n = w.getName();
                Bukkit.unloadWorld(w, true);
                sender.sendMessage(tag + "Unloaded " + ChatColor.WHITE + n);
            }

            else
            {
                sender.sendMessage(ChatColor.RED + "Can't find world " + ChatColor.GRAY + args[1]);
            }

        }else if(args[0].equalsIgnoreCase("to") && args.length >= 2)
        {
            if(sender instanceof Player p)
            {
                try
                {
                    p.teleport(Bukkit.getWorld(args[1]).getSpawnLocation());
                }
                catch(Throwable e)
                {
                    p.sendMessage(ChatColor.RED + "Can't find world " + ChatColor.GRAY + args[1]);
                }
            }

            else
            {
                sender.sendMessage(ChatColor.RED + "Players only!");
            }
        }else if(args[0].equalsIgnoreCase("list"))
        {
            for(World i : Bukkit.getWorlds())
            {
                boolean rift = false;

                for(RiftWorldConfig j : configs)
                {
                    if(j.getName().equals(i.getName()))
                    {
                        rift = true;
                        break;
                    }
                }

                if(rift)
                {
                    sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.LIGHT_PURPLE + i.getName() + " " + ChatColor.GRAY + " (Managed)");
                }

                else
                {
                    sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.GREEN + i.getName() + " " + ChatColor.GRAY + " (Loaded)");
                }
            }

            searching: for(File i : new File(".").listFiles())
            {
                if(i.isDirectory() && new File(i, "level.dat").exists())
                {
                    for(World j : Bukkit.getWorlds())
                    {
                        if(j.getWorldFolder().equals(i))
                        {
                            continue searching;
                        }
                    }

                    sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + i.getName() + " " + ChatColor.GRAY + " (Not Loaded)");
                }
            }
        }

        return true;
    }

    private void deleteWorld(File f) {
        System.gc(); // Windows is annoying
        deleteFiles(f);
        file("worlds/" + f.getName() + ".json").delete();
        configs.removeIf(i -> i.getName().equals(f.getName()));

        if(f.exists())
        {
            RiftConfig.get().getDeleting().add(f.getPath());
            Rift.warn("Some files couldn't be deleted in " + f.getPath() + " at this time. They have been marked for deletion on shutdown & rift will double check it's gone on the next startup!");
        }

        else
        {
            RiftConfig.get().getDeleting().remove(f.getPath());
            Rift.info("Fully deleted " + f.getPath() + ". All files have been removed.");
        }
    }

    private void deleteFiles(File folder)
    {
        if(folder.isDirectory())
        {
            for(File i : folder.listFiles())
            {
                deleteFiles(folder);
            }
        }

        if(!folder.delete())
        {
            folder.deleteOnExit();
            Rift.warn("Couldn't delete " + folder.getName() + " at this time. Marked for deletion on shutdown.");
        }
    }

    private void evacuate(Player i) {
        i.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
    }

    public static File file(String s)
    {
        File f = new File(instance.getDataFolder(), s);
        f.getParentFile().mkdirs();
        return f;
    }

    public static File folder(String s)
    {
        File f = new File(instance.getDataFolder(), s);
        f.mkdirs();
        return f;
    }

    public static void info(String message)
    {
        instance.getLogger().info(message);
    }

    public static void warn(String message)
    {
        instance.getLogger().warning(message);
    }

    public static void error(String message)
    {
        instance.getLogger().severe(message);
    }

    public static void verbose(String message)
    {
        if(RiftConfig.get().isVerbose())
        {
            instance.getLogger().fine(message);
        }
    }
}
