package com.volmit.rift;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandListenerWrapper;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class RiftCommand {

    private static final String TAG = ChatColor.DARK_GRAY + "[" + ChatColor.LIGHT_PURPLE + "Rift" + ChatColor.DARK_GRAY + "]" + ChatColor.GRAY + ": ";

    private static final String CMD = "rift";
    private static final String CMD_CREATE = "create";
    private static final String CMD_LOAD = "load";
    private static final String CMD_IMPORT = "import";
    private static final String CMD_UNLOAD = "unload";
    private static final String CMD_DELETE = "delete";
    private static final String CMD_TO = "to";
    private static final String CMD_LIST = "list";
    private static final String CMD_GENS = "generators";

    public static void init(CommandDispatcher<CommandListenerWrapper> dispatcher) {
        LiteralCommandNode<CommandListenerWrapper> node = dispatcher.register(literal(CMD)
                .then(literal(CMD_TO).requires(src -> src.getBukkitSender().hasPermission("rift.teleport"))
                  .then(argument("name", StringArgumentType.string())
                    .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "name")))))
                .requires(src -> src.getBukkitSender().hasPermission("rift.admin"))
                .executes(ctx -> printUsage(ctx.getSource().getBukkitSender()))
                .then(literal(CMD_CREATE)
                  .then(argument("name", StringArgumentType.string())
                    .then(argument("generator", StringArgumentType.string())
                      .executes(ctx -> create(ctx,
                              StringArgumentType.getString(ctx, "name"),
                              StringArgumentType.getString(ctx, "generator"), Long.MAX_VALUE, null))
                        .then(argument("seed", LongArgumentType.longArg())
                          .executes(ctx -> create(ctx,
                                  StringArgumentType.getString(ctx, "name"),
                                  StringArgumentType.getString(ctx, "generator"),
                                  LongArgumentType.getLong(ctx, "seed"), null))
                            .then(argument("environment", StringArgumentType.string())
                              //.suggests(new EnumSuggestionProvider<>(World.Environment.class))
                              .executes(ctx -> create(ctx,
                                      StringArgumentType.getString(ctx, "name"),
                                      StringArgumentType.getString(ctx, "generator"),
                                      LongArgumentType.getLong(ctx, "seed"),
                                      StringArgumentType.getString(ctx, "environment"))))))))
                .then(literal(CMD_LOAD)
                  .then(argument("name", StringArgumentType.string())
                    .executes(ctx -> load(ctx,
                            StringArgumentType.getString(ctx, "name"), "normal"))
                      .then(argument("generator", StringArgumentType.string())
                        .executes(ctx -> load(ctx,
                                StringArgumentType.getString(ctx, "name"),
                                StringArgumentType.getString(ctx, "generator"))))))
                .then(literal(CMD_IMPORT)
                  .then(argument("name", StringArgumentType.string())
                    .then(argument("generator", StringArgumentType.string())
                      .executes(ctx -> importWorld(ctx,
                              StringArgumentType.getString(ctx, "name"),
                              StringArgumentType.getString(ctx, "generator"))))))
                .then(literal(CMD_UNLOAD)
                  .then(argument("name", StringArgumentType.string())
                    .executes(ctx -> unload(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(literal(CMD_DELETE)
                  .then(argument("name", StringArgumentType.string())
                    .executes(ctx -> delete(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(literal(CMD_LIST)
                  .executes(RiftCommand::list))
                .then(literal(CMD_GENS)
                  .executes(RiftCommand::generators)));

        dispatcher.register(literal("rft").redirect(node));
        dispatcher.register(literal("ri").redirect(node));
        dispatcher.register(literal("rt").redirect(node));
    }

    private static int printUsage(CommandSender sender) {
        List<RiftWorldConfig> configs = Rift.INSTANCE.getConfigs();
        sender.sendMessage(TAG + configs.size() + " World" + (configs.size() == 1 ? "" : "s") + ", Version " + Rift.INSTANCE.getDescription().getVersion());
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift load"+ ChatColor.GRAY+"  <name> [generator]");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift create"+ ChatColor.GRAY+" <name> <generator> [seed] [environment]");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift import"+ ChatColor.GRAY+"  <name> <generator>");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift unload"+ ChatColor.GRAY+" <name>");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift delete"+ ChatColor.GRAY+" <name>");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift to"+ ChatColor.GRAY+" <world>");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift list");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/rift generators");
        return 1;
    }

    private static int create(CommandContext<CommandListenerWrapper> ctx, String file, String generator, long seed, String environment) {
        CommandSender sender = ctx.getSource().getBukkitSender();
        File f = new File(file);

        if(f.exists()) {
            sender.sendMessage(TAG + ChatColor.RED + "World \"" + file + "\" already exists!");
            return 1;
        }
        sender.sendMessage(TAG + ChatColor.GREEN + "Creating new World \"" + file + "\"...");
        WorldCreator w = WorldCreator.name(f.getName())
                .type(generator.equalsIgnoreCase("flat") ? WorldType.FLAT : generator.equalsIgnoreCase("amplified") ? WorldType.AMPLIFIED : generator.equalsIgnoreCase("largebiomes") ? WorldType.LARGE_BIOMES : WorldType.NORMAL)
                .generator(generator);

        if(seed != Long.MAX_VALUE)
            w.seed(seed);
        World.Environment e = getEnv(environment);
        if(e != null)
            w.environment(e);

        RiftWorldConfig c = RiftWorldConfig.from(w.createWorld(), generator);
        Rift.INSTANCE.getConfigs().add(c);
        c.save();
        sender.sendMessage(TAG + ChatColor.GREEN + "Created and imported new World \"" + file + "\".");
        return 1;
    }

    private static int load(CommandContext<CommandListenerWrapper> ctx, String file, String generator) {
        CommandSender sender = ctx.getSource().getBukkitSender();
        File f = new File(file);

        if(Bukkit.getWorlds().stream().anyMatch(w -> w.getWorldFolder().equals(f))) {
            sender.sendMessage(TAG + ChatColor.RED + "World \"" + file + "\" is already loaded.");
            return 1;
        }

        if(generator.equalsIgnoreCase("normal")) {
            Optional<RiftWorldConfig> optionalConfig = Rift.INSTANCE.getConfigs().stream().filter(c -> c.getName().equals(f.getName())).findFirst();
            if(optionalConfig.isPresent())
                generator = optionalConfig.get().getGenerator();
        }

        sender.sendMessage(TAG + ChatColor.GREEN + "Loading World \"" + file + "\"...");
        WorldCreator.name(f.getName())
                .type(generator.equalsIgnoreCase("flat") ? WorldType.FLAT : generator.equalsIgnoreCase("amplified") ? WorldType.AMPLIFIED : generator.equalsIgnoreCase("largebiomes") ? WorldType.LARGE_BIOMES : WorldType.NORMAL)
                .generator(generator).createWorld();
        sender.sendMessage(TAG + ChatColor.GREEN + "Successfully loaded World \"" + file + "\".");
        return 1;
    }

    private static int importWorld(CommandContext<CommandListenerWrapper> ctx, String file, String generator) {
        CommandSender sender = ctx.getSource().getBukkitSender();
        File f = new File(file);

        if(!f.exists()) {
            sender.sendMessage(TAG + ChatColor.RED + "Unable to find world \"" + file + "\" to import.");
            return 1;
        }

        if(Rift.INSTANCE.getConfigs().stream().anyMatch(c -> c.getName().equals(f.getName()))) {
            sender.sendMessage(TAG + ChatColor.RED + "World \"" + file + "\" is already imported.");
            return 1;
        }

        if(Bukkit.getWorlds().stream().anyMatch(w -> w.getWorldFolder().equals(f))) {
            sender.sendMessage(TAG + ChatColor.RED + "World \"" + file + "\" already exists on the server.");
            return 1;
        }

        sender.sendMessage(TAG + ChatColor.GREEN + "Importing World \"" + f.getName() + "\"...");
        World world = WorldCreator.name(f.getName())
                .type(generator.equalsIgnoreCase("flat") ? WorldType.FLAT : generator.equalsIgnoreCase("amplified") ? WorldType.AMPLIFIED : generator.equalsIgnoreCase("largebiomes") ? WorldType.LARGE_BIOMES : WorldType.NORMAL)
                .generator(generator).createWorld();
        RiftWorldConfig c = RiftWorldConfig.from(world, generator);
        Rift.INSTANCE.getConfigs().add(c);
        c.save();
        sender.sendMessage(TAG + ChatColor.GREEN + "Successfully imported \"" + f.getName() + "\".");
        return 1;
    }

    private static int unload(CommandContext<CommandListenerWrapper> ctx, String file) {
        CommandSender sender = ctx.getSource().getBukkitSender();
        World w = Bukkit.getWorld(file);

        if(w == null) {
            sender.sendMessage(TAG + ChatColor.RED + "Unable to find world \"" + ChatColor.GRAY + file + "\".");
            return 1;
        }

        sender.sendMessage(TAG + ChatColor.GREEN + "Unloading World \"" + file + "\"...");
        w.getPlayers().forEach(FileUtils::evacuate);
        for(Chunk i : w.getLoadedChunks())
            i.unload(true);
        String world = w.getName();
        Bukkit.unloadWorld(w, true);
        sender.sendMessage(TAG + ChatColor.GREEN + "Successfully unloaded world \"" + world + "\".");
        return 1;
    }

    private static int delete(CommandContext<CommandListenerWrapper> ctx, String file) {
        CommandSender sender = ctx.getSource().getBukkitSender();
        World w = Bukkit.getWorld(file);

        sender.sendMessage(TAG + ChatColor.GREEN + "Deleting World \"" + file + "\"...");
        if(w != null)
            unload(ctx, file);

        File f = new File(file);
        if(f.isDirectory() && f.exists())
            FileUtils.deleteWorld(f);
        sender.sendMessage(TAG + ChatColor.RED + "Successfully deleted world \"" + f.getName() + "\".");
        return 1;
    }

    private static int teleport(CommandContext<CommandListenerWrapper> ctx, String world) {
        if(ctx.getSource().getBukkitSender() instanceof Player p) {
            try { p.teleport(Bukkit.getWorld(world).getSpawnLocation()); }
            catch(Throwable e) { p.sendMessage(TAG + ChatColor.RED + "Unable to find world \"" + world + "\"."); }
        } else
            ctx.getSource().getBukkitSender().sendMessage(TAG + ChatColor.RED + "Only players can be moved into a world.");
        return 1;
    }

    private static int list(CommandContext<CommandListenerWrapper> ctx) {
        CommandSender sender = ctx.getSource().getBukkitSender();
        for(World w : Bukkit.getWorlds()) {
            if(Rift.INSTANCE.getConfigs().stream().anyMatch(c -> c.getName().equals(w.getName()))) {
                sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.LIGHT_PURPLE + w.getName() + " | " + ChatColor.GREEN + "Managed");
                continue;
            }

            try {
                FileConfiguration fc = new YamlConfiguration();
                fc.load(new File("bukkit.yml"));
                if(fc.isConfigurationSection("worlds")) {
                    if(fc.getConfigurationSection("worlds").getKeys(false).contains(w.getName())) {
                        sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.LIGHT_PURPLE + w.getName() + " | " + ChatColor.GREEN + "bukkit.yml");
                        continue;
                    }
                }
            } catch(Throwable ignored) { }

            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.LIGHT_PURPLE + w.getName() + " | " + ChatColor.GREEN + "Loaded");
        }

        for(File i : new File(".").listFiles())
            if(i.isDirectory() && new File(i, "level.dat").exists() && Bukkit.getWorlds().stream().noneMatch(w -> w.getWorldFolder().equals(i)))
                sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.LIGHT_PURPLE + i.getName() + " | " + ChatColor.RED + "Not Loaded");

        return 1;
    }

    private static int generators(CommandContext<CommandListenerWrapper> ctx) {
        CommandSender sender = ctx.getSource().getBukkitSender();

        for(WorldType t : WorldType.values())
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + t.getName().toLowerCase() + ChatColor.GRAY + " by " + ChatColor.RED + "Mojang");

        for(Plugin i : Bukkit.getPluginManager().getPlugins()) {
            if(i.getName().equals("Iris"))
                sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.GREEN + i.getName() + ChatColor.DARK_GREEN + ":[dimension]" + " " + ChatColor.GRAY + " by " + ChatColor.WHITE + printAuthors(i.getDescription().getAuthors()));
            else if(i.getDefaultWorldGenerator("testworld", null) != null)
                sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.GREEN + i.getName() + " " + ChatColor.GRAY + " by " + ChatColor.WHITE + printAuthors(i.getDescription().getAuthors()));
        }

        return 1;
    }

    private static LiteralArgumentBuilder<CommandListenerWrapper> literal(String s) {
        return LiteralArgumentBuilder.literal(s);
    }

    private static <T> RequiredArgumentBuilder<CommandListenerWrapper, T> argument(String s, ArgumentType<T> argumentType) {
        return RequiredArgumentBuilder.argument(s, argumentType);
    }

    private static String printAuthors(List<String> authors) {
        if(authors == null || authors.isEmpty())
            return "Anonymous";

        if(authors.size() == 1)
            return authors.get(0);

        StringBuilder s = new StringBuilder();

        for(String i : authors)
            s.append(", ").append(i);

        return s.substring(2);
    }

    private static World.Environment getEnv(String s) {
        for(World.Environment e : World.Environment.values())
            if(e.name().equalsIgnoreCase(s))
                return e;
        return null;
    }
}
