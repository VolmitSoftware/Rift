package com.volmit.rift;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.*;

public final class FileUtils {

    public static void deleteWorld(File f) {
        System.gc(); // Windows is annoying
        deleteFiles(f);
        file("worlds/" + f.getName() + ".json").delete();
        Rift.INSTANCE.getConfigs().removeIf(i -> i.getName().equals(f.getName()));

        if(f.exists()) {
            RiftConfig.get().getDeleting().add(f.getPath());
            Rift.warn("Some files couldn't be deleted in " + f.getPath() + " at this time. They have been marked for deletion on shutdown & rift will double check it's gone on the next startup!");
        } else {
            RiftConfig.get().getDeleting().remove(f.getPath());
            Rift.info("Fully deleted " + f.getPath() + ". All files have been removed.");
        }

        RiftConfig.get().save();
    }

    public static void deleteFiles(File folder) {
        if(folder.isDirectory())
            for(File i : folder.listFiles())
                deleteFiles(i);

        if(!folder.delete()) {
            folder.deleteOnExit();
            Rift.warn("Couldn't delete " + folder.getName() + " at this time. Marked for deletion on shutdown.");
        }
    }

    public static void evacuate(Player i) {
        i.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
    }

    public static File file(String s) {
        File f = new File(Rift.INSTANCE.getDataFolder(), s);
        f.getParentFile().mkdirs();
        return f;
    }

    public static File folder(String s) {
        File f = new File(Rift.INSTANCE.getDataFolder(), s);
        f.mkdirs();
        return f;
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
            sb.append(line + "\n");

        bu.close();
        return sb.toString();
    }
}
