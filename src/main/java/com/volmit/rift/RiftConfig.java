package com.volmit.rift;

import com.google.gson.Gson;
import lombok.Data;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Data
public class RiftConfig {

    private static RiftConfig instance;
    private boolean verbose = false;
    private Set<String> deleting = new HashSet<>();

    public static RiftConfig get() {
        if(instance != null)
            return instance;

        File file = FileUtils.file("config.json");
        Rift.info("Loading Rift Config at " + file.getPath() + "...");
        if(file.exists()) {
            try {
                return new Gson().fromJson(FileUtils.readAll(file), RiftConfig.class);
            } catch(Throwable e) {
                Rift.warn("Failed to load Rift Config at " + file.getPath() + ". Using default config.");
            }
        }

        RiftConfig config = new RiftConfig();
        try {
            FileUtils.writeAll(file, new Gson().toJson(config));
        } catch(Throwable e) {
            Rift.error("Failed to save default Rift Config!", e);
            e.printStackTrace();
        }

        return config;
    }

    public void save() {
        try {
            FileUtils.writeAll(FileUtils.file("config.json"), new Gson().toJson(instance));
        } catch(IOException e) {
            Rift.error("Failed to save Rift Config!", e);
            e.printStackTrace();
        }
    }
}