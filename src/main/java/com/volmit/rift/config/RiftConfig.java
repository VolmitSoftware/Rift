package com.volmit.rift.config;

import com.google.gson.Gson;
import com.volmit.rift.Rift;
import lombok.Data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class RiftConfig
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
                instance = new Gson().fromJson(new FileReader(file), RiftConfig.class);
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
            new Gson().toJson(instance, new FileWriter(file));
        }

        catch(Throwable e)
        {
            e.printStackTrace();
        }

        return instance;
    }
}
