package com.volmit.rift.config;

import com.google.gson.Gson;
import com.volmit.rift.Rift;
import lombok.Data;
import org.bukkit.World;
import org.bukkit.WorldType;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Data
public class RiftWorldConfig
{
    private String name = "undefined";
    private long seed = new Random().nextLong();
    private World.Environment environment;
    private String generator;
    private WorldType type = WorldType.NORMAL;

    public void save()
    {
        try {
            new Gson().toJson(this, new FileWriter(Rift.file("worlds/" + getName() + ".json")));
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
                instance = new Gson().fromJson(new FileReader(file), RiftWorldConfig.class);
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
            new Gson().toJson(instance, new FileWriter(file));
        }

        catch(Throwable e)
        {
            e.printStackTrace();
        }

        return instance;
    }
}