package com.volmit.rift;

import com.google.gson.Gson;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.units.qual.C;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class Rift extends JavaPlugin {
    public static Rift instance;
    private List<RiftWorldConfig> configs;

    public void onEnable()
    {
        instance = this;
        configs = RiftWorldConfig.loadAll();

        for(String i : RiftConfig.get().getDeleting())
        {
            File f = new File(i);

            if(f.exists())
            {
                info("Attempting to cleanup remaining deleted world files for " + i);
                deleteWorld(f);
            }
        }

        configs.forEach(this::init);
        checkForBukkitWorlds();
    }

    private void checkForBukkitWorlds() {
        FileConfiguration fc = new YamlConfiguration();
        Set<String> hit = new HashSet<>();
        try {
            fc.load(new File("bukkit.yml"));
            searching: for(String i : fc.getKeys(true))
            {
                if(i.startsWith("worlds.")) {
                    String worldName = i.split("\\Q.\\E")[1];


                    if(hit.contains(worldName))
                    {
                        continue;
                    }

                    hit.add(worldName);
                    String generator = i.endsWith(".generator") ? fc.getString(i) : fc.getString("worlds." + worldName + ".generator");

                    if(generator != null && generator.startsWith("Iris"))
                    {
                        info("Skipping Iris world (hello!), because Iris is managing the bukkit.yml for itself.");
                        continue;
                    }

                    for(World j : Bukkit.getWorlds())
                    {
                        if(j.getName().equals(worldName))
                        {
                            continue searching;
                        }
                    }

                    info("Loading bukkit.yml " + worldName + " using generator " + generator);
                    World world = new WorldCreator(worldName)
                        .generator(generator)
                        .type(generator != null ? generator.equalsIgnoreCase("flat") ? WorldType.FLAT : generator.equalsIgnoreCase("amplified") ? WorldType.AMPLIFIED : generator.equalsIgnoreCase("largebiomes") ? WorldType.LARGE_BIOMES : WorldType.NORMAL : WorldType.NORMAL)
                        .createWorld();
                    info("Loaded bukkit.yml " + world.getName() + " using generator " + generator);
                }
            }
        } catch(Throwable e) {
            e.printStackTrace();
        }
    }

    public void onDisable()
    {

    }

    private void init(RiftWorldConfig c) {
        File f = new File(c.getName());
        String generator = c.getGenerator();
        boolean loaded = false;
        for(World i : Bukkit.getWorlds())
        {
            if(i.getWorldFolder().equals(f))
            {
                loaded = true;
                break;
            }
        }

        if(loaded)
        {
            return;
        }

        WorldCreator.name(f.getName()).generator(generator)
            .type(generator.equalsIgnoreCase("flat") ? WorldType.FLAT : generator.equalsIgnoreCase("amplified") ? WorldType.AMPLIFIED : generator.equalsIgnoreCase("largebiomes") ? WorldType.LARGE_BIOMES : WorldType.NORMAL)
            .createWorld();
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
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift load"+ ChatColor.GRAY+"  <name> [generator]");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift create"+ ChatColor.GRAY+" <name> <generator> [seed] [environment]");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift import"+ ChatColor.GRAY+"  <name> <generator>");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift unload"+ ChatColor.GRAY+" <name>");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift delete"+ ChatColor.GRAY+" <name>");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift to"+ ChatColor.GRAY+" <world>");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift list");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift generators");
        }

        else if(args[0].equalsIgnoreCase("create")&& args.length >= 3)
        {
            File f = new File(args[1]);

            if(f.exists())
            {
                sender.sendMessage(tag + ChatColor.RED + "Already Exists!");
                return true;
            }

            String generator = args[2];
            WorldCreator w = WorldCreator.name(f.getName())
                .type(generator.equalsIgnoreCase("flat") ? WorldType.FLAT : generator.equalsIgnoreCase("amplified") ? WorldType.AMPLIFIED : generator.equalsIgnoreCase("largebiomes") ? WorldType.LARGE_BIOMES : WorldType.NORMAL)
                .generator(generator);

            if(args.length > 3)
            {
                w.seed(Long.parseLong(args[3]));
            }

            if(args.length > 4)
            {
                w.environment(World.Environment.valueOf(args[4].toUpperCase(Locale.ROOT)));
            }

            World world = w.createWorld();
            RiftWorldConfig c = RiftWorldConfig.from(world, generator);
            configs.add(c);
            c.save();
            sender.sendMessage(tag + "Created & Imported " + args[1]);
        }else if(args[0].equalsIgnoreCase("load")&& args.length >= 2)
        {
            File f = new File(args[1]);
            String generator = null;

            boolean loaded = false;
            for(World i : Bukkit.getWorlds())
            {
                if(i.getWorldFolder().equals(f))
                {
                    loaded = true;
                    break;
                }
            }

            if(loaded)
            {
                sender.sendMessage(tag + ChatColor.RED + "Already Loaded!");
                return true;
            }

            if(args.length > 2)
            {
                generator = args[2];
            }

            if(generator == null)
            {
                for(RiftWorldConfig i : configs)
                {
                    if(i.getName().equals(f.getName()))
                    {
                        generator = i.getGenerator();
                    }
                }
            }

            WorldCreator.name(f.getName())
                .type(generator.equalsIgnoreCase("flat") ? WorldType.FLAT : generator.equalsIgnoreCase("amplified") ? WorldType.AMPLIFIED : generator.equalsIgnoreCase("largebiomes") ? WorldType.LARGE_BIOMES : WorldType.NORMAL)
                .generator(generator).createWorld();
            sender.sendMessage(tag + "Loaded " + args[1]);
        }else if(args[0].equalsIgnoreCase("import")&& args.length >= 3)
        {
            File f = new File(args[1]);
            String generator = args[2];

            if(f.exists())
            {
                for(RiftWorldConfig i : configs)
                {
                    if(i.getName().equals(f.getName()))
                    {
                        sender.sendMessage(tag + ChatColor.RED + "Already Imported!");
                        return true;
                    }
                }

                boolean loaded = false;
                for(World i : Bukkit.getWorlds())
                {
                    if(i.getWorldFolder().equals(f))
                    {
                        loaded = true;
                        break;
                    }
                }

                if(!loaded)
                {
                    sender.sendMessage(tag + "Loading " + f.getName());
                    World world = WorldCreator.name(f.getName())
                        .type(generator.equalsIgnoreCase("flat") ? WorldType.FLAT : generator.equalsIgnoreCase("amplified") ? WorldType.AMPLIFIED : generator.equalsIgnoreCase("largebiomes") ? WorldType.LARGE_BIOMES : WorldType.NORMAL)
                        .generator(generator).createWorld();
                    RiftWorldConfig c = RiftWorldConfig.from(world, generator);
                    configs.add(c);
                    c.save();
                    sender.sendMessage(tag + "Imported " + f.getName());
                }
            }
            else {
                sender.sendMessage(tag  + ChatColor.RED + "Can't find world " + args[1] + " to import.");
            }
        }else if(args[0].equalsIgnoreCase("delete")&& args.length >= 2)
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
            sender.sendMessage(tag + "Deleted " + ChatColor.WHITE + f.getName());
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
                    p.sendMessage(tag + ChatColor.RED + "Can't find world " + ChatColor.GRAY + args[1]);
                }
            }

            else
            {
                sender.sendMessage(tag + ChatColor.RED + "Players only!");
            }
        }else if(args[0].equalsIgnoreCase("list"))
        {
            for(World i : Bukkit.getWorlds())
            {
                boolean rift = false;
                boolean bukkit = false;

                for(RiftWorldConfig j : configs)
                {
                    if(j.getName().equals(i.getName()))
                    {
                        rift = true;
                        break;
                    }
                }
                FileConfiguration fc = new YamlConfiguration();
                try {
                    fc.load(new File("bukkit.yml"));

                    for(String j : fc.getKeys(true))
                    {
                        if(j.startsWith("worlds.")) {
                            if(j.split("\\Q.\\E")[1].equals(i.getName()))
                            {
                                bukkit = true;
                                break;
                            }
                        }
                    }
                }

                catch(Throwable e)
                {

                }

                if(rift)
                {
                    sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.LIGHT_PURPLE + i.getName() + " " + ChatColor.GRAY + " (Managed)");
                }

                else if(bukkit)
                {
                    sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.AQUA + i.getName() + " " + ChatColor.GRAY + " (bukkit.yml)");
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
        }else if(args[0].equalsIgnoreCase("generators"))
        {
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + "normal " + ChatColor.GRAY + " by " + ChatColor.WHITE + "Minecraft");
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + "flat " + ChatColor.GRAY + " by " + ChatColor.WHITE + "Minecraft");
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + "amplified " + ChatColor.GRAY + " by " + ChatColor.WHITE + "Minecraft");
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + "largebiomes " + ChatColor.GRAY + " by " + ChatColor.WHITE + "Minecraft");

            for(Plugin i : Bukkit.getPluginManager().getPlugins())
            {
                if(i.getName().equals("Iris"))
                {
                    sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.GREEN + i.getName() + ChatColor.DARK_GREEN + ":[dimension]" + " " + ChatColor.GRAY + " by " + ChatColor.WHITE + printAuthors(i.getDescription().getAuthors()));
                }

                else if(i.getDefaultWorldGenerator("testworld", null) != null)
                {
                    sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.GREEN + i.getName() + " " + ChatColor.GRAY + " by " + ChatColor.WHITE + printAuthors(i.getDescription().getAuthors()));
                }
            }
        }

        return true;
    }

    private String printAuthors(List<String> authors) {
        if(authors == null || authors.isEmpty())
        {
            return "Anonymous";
        }

        if(authors.size() == 1)
        {
            return authors.get(0);
        }

        StringBuilder s = new StringBuilder();

        for(String i : authors)
        {
            s.append(", ").append(i);
        }

        return s.substring(2);
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

        RiftConfig.get().save();
    }

    private void deleteFiles(File folder)
    {
        if(folder.isDirectory())
        {
            for(File i : folder.listFiles())
            {
                deleteFiles(i);
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

    public static void writeAll(File f, String s) throws FileNotFoundException {
        PrintWriter pw = new PrintWriter(f);
        pw.print(s);
        pw.close();
    }

    public static String readAll(File f) throws IOException {
        BufferedReader bu = new BufferedReader(new FileReader(f));
        StringBuilder sb = new StringBuilder();
        String line;

        while((line = bu.readLine()) != null)
        {
            sb.append(line + "\n");
        }

        bu.close();
        return sb.toString();
    }

    @Data
    public static class RiftConfig
    {
        private static RiftConfig instance;
        private boolean verbose = false;
        private Set<String> deleting = new HashSet<>();

        public static RiftConfig get() {
            if(instance != null) {
                return instance;
            }

            RiftConfig dummy = new RiftConfig();
            File file = Rift.file("config.json");
            Rift.info("Loading Config at " + file.getPath());

            if(file.exists()) {
                try {
                    instance = new Gson().fromJson(Rift.readAll(file), RiftConfig.class);
                } catch(Throwable e) {
                    instance = new RiftConfig();
                    Rift.warn("Failed to load config at " + file.getPath() + ". Using default config.");
                }
            } else
            {
                instance = new RiftConfig();
            }

            try
            {
                Rift.writeAll(file, new Gson().toJson(instance));
            }

            catch(Throwable e)
            {
                e.printStackTrace();
            }

            return instance;
        }

        public void save() {
            try {
                Rift.writeAll(Rift.file("config.json"), new Gson().toJson(instance));
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Data
    public static class RiftWorldConfig
    {
        private String name = "undefined";
        private long seed = new Random().nextLong();
        private World.Environment environment;
        private String generator;
        private WorldType type = WorldType.NORMAL;

        public void save()
        {
            try {
                Rift.writeAll(Rift.file("worlds/" + getName() + ".json"), new Gson().toJson(this));
            } catch(IOException e) {
                e.printStackTrace();
            }
        }

        public static List<RiftWorldConfig> loadAll()
        {
            List<RiftWorldConfig> configs = new ArrayList<>();

            for(File i : Rift.folder("worlds").listFiles())
            {
                if(i.isFile() && i.getName().endsWith(".json"))
                {
                    configs.add(get(i));
                }
            }

            return configs;
        }

        public static RiftWorldConfig from(World world, String generator)
        {
            RiftWorldConfig config = new RiftWorldConfig();
            config.setName(world.getName());
            config.setSeed(world.getSeed());
            config.setEnvironment(world.getEnvironment());
            config.setGenerator(generator);
            return config;
        }

        public static RiftWorldConfig get(File file) {
            RiftConfig dummy = new RiftConfig();
            Rift.info("Loading World Config at " + file.getPath());
            RiftWorldConfig instance = null;
            if(file.exists()) {
                try {
                    instance = new Gson().fromJson(Rift.readAll(file), RiftWorldConfig.class);
                } catch(Throwable e) {
                    instance = new RiftWorldConfig();
                    Rift.warn("Failed to load world config at " + file.getPath() + ". Using default config.");
                }
            } else
            {
                instance = new RiftWorldConfig();
            }

            try
            {
                Rift.writeAll(file, new Gson().toJson(instance));
            }

            catch(Throwable e)
            {
                e.printStackTrace();
            }

            return instance;
        }
    }
}
