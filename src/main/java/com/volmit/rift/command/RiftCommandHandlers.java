package com.volmit.rift.command;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import com.volmit.rift.Rift;
import com.volmit.rift.storage.TrashEntry;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.world.WorldSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Set;

public final class RiftCommandHandlers {
    private RiftCommandHandlers() {
    }

    public static final class ManagedWorld extends StringHandler {
        @Override
        protected Set<String> values() {
            Set<String> values = new LinkedHashSet<>();
            for (WorldProfile profile : Rift.get().profiles().all()) {
                values.add(profile.getName());
            }
            return values;
        }
    }

    public static final class KnownWorld extends StringHandler {
        @Override
        protected Set<String> values() {
            Set<String> values = new LinkedHashSet<>();
            for (WorldSnapshot snapshot : Rift.get().worldInventory().snapshots()) {
                values.add(snapshot.name());
            }
            return values;
        }
    }

    public static final class LoadedWorld extends StringHandler {
        @Override
        protected Set<String> values() {
            Set<String> values = new LinkedHashSet<>();
            for (WorldSnapshot snapshot : Rift.get().worldInventory().snapshots()) {
                if (snapshot.loaded()) {
                    values.add(snapshot.name());
                }
            }
            return values;
        }
    }

    public static final class TrashId extends StringHandler {
        @Override
        protected Set<String> values() {
            Set<String> values = new LinkedHashSet<>();
            for (TrashEntry entry : Rift.get().trash().all()) {
                values.add(entry.getId());
            }
            return values;
        }
    }

    public static final class Generator extends StringHandler {
        @Override
        protected Set<String> values() {
            return new LinkedHashSet<>(Rift.get().lifecycle().configuredGenerators());
        }
    }

    public static final class OnlinePlayer extends StringHandler {
        @Override
        protected Set<String> values() {
            Set<String> values = new LinkedHashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                values.add(player.getName());
            }
            return values;
        }
    }

    public abstract static class StringHandler implements DirectorParameterHandler<String> {
        @Override
        public KList<String> getPossibilities() {
            return new KList<>(values());
        }

        @Override
        public String toString(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String parse(String input, boolean force) throws DirectorParsingException {
            if (input == null || input.isBlank()) {
                throw new DirectorParsingException("Value cannot be empty");
            }
            for (String candidate : values()) {
                if (candidate.equalsIgnoreCase(input)) {
                    return candidate;
                }
            }
            return input.trim();
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == String.class;
        }

        protected abstract Set<String> values();
    }
}
