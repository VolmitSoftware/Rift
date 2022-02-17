package com.volmit.rift;

import com.google.gson.Gson;
import lombok.Data;
import org.bukkit.World;
import org.bukkit.WorldType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Data
public class RiftWorldConfig  {

    private String name = "undefined";
    private long seed = new Random().nextLong();
    private World.Environment environment;
    private String generator;
    private WorldType type = WorldType.NORMAL;

    public void save() {
        try {
            FileUtils.writeAll(FileUtils.file("worlds/" + getName() + ".json"), new Gson().toJson(this));
        } catch(IOException e) {
            Rift.error("Failed to save World Config for \"" + name + "\"!", e);
            e.printStackTrace();
        }
    }

    public static List<RiftWorldConfig> loadAll() {
        List<RiftWorldConfig> configs = new ArrayList<>();
        Arrays.stream(FileUtils.folder("worlds").listFiles())
                .filter(f -> f.isFile() && f.getName().endsWith(".json"))
                .forEach(f -> configs.add(get(f)));
        return configs;
    }

    public static RiftWorldConfig from(World world, String generator) {
        RiftWorldConfig config = new RiftWorldConfig();
        config.setName(world.getName());
        config.setSeed(world.getSeed());
        config.setEnvironment(world.getEnvironment());
        config.setGenerator(generator);
        return config;
    }

    private static RiftWorldConfig get(File file) {
        Rift.info("Loading World Config at " + file.getPath() + "...");
        if(file.exists()) {
            try {
                return new Gson().fromJson(FileUtils.readAll(file), RiftWorldConfig.class);
            } catch(Throwable e) {
                Rift.warn("Failed to load World Config at " + file.getPath() + ". Using default config.");
            }
        }

        RiftWorldConfig config = new RiftWorldConfig();
        try {
            FileUtils.writeAll(file, new Gson().toJson(config));
        } catch(Throwable e) {
            Rift.error("Failed to save default World Config!", e);
            e.printStackTrace();
        }

        return config;
    }
}