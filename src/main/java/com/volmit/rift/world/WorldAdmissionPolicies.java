package com.volmit.rift.world;

import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent;
import com.volmit.rift.Rift;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class WorldAdmissionPolicies implements Listener {
    private final Rift plugin;
    private final RiftLocalization language;
    private final WorldProfileStore profiles;
    private final TeleportService teleports;
    private final WorldAdmissionLedger ledger = new WorldAdmissionLedger();
    private final Map<UUID, JoinPlacement> joining = new ConcurrentHashMap<>();
    private final Map<PlayerTeleportEvent, WorldAdmissionLedger.Reservation> teleportReservations = new ConcurrentHashMap<>();
    private final Map<UUID, Evacuation> evacuating = new ConcurrentHashMap<>();
    private volatile Map<UUID, Spawn> spawns = Map.of();

    WorldAdmissionPolicies(Rift plugin, RiftLocalization language, WorldProfileStore profiles, TeleportService teleports) {
        this.plugin = plugin;
        this.language = language;
        this.profiles = profiles;
        this.teleports = teleports;
    }

    void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refresh();
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaScheduler.runEntity(plugin, player, () -> ledger.arrive(player.getUniqueId(), player.getWorld().getUID()));
        }
    }

    void refresh() {
        Map<UUID, Spawn> next = new LinkedHashMap<>();
        for (World world : Bukkit.getWorlds()) {
            Spawn spawn = snapshot(world);
            if (spawn != null) {
                next.put(spawn.id(), spawn);
            }
        }
        spawns = Collections.unmodifiableMap(next);
    }

    void capture(World world) {
        Spawn spawn = snapshot(world);
        if (spawn != null) {
            Map<UUID, Spawn> next = new LinkedHashMap<>(spawns);
            next.put(spawn.id(), spawn);
            spawns = Collections.unmodifiableMap(next);
        }
    }

    void close() {
        joining.clear();
        teleportReservations.clear();
        evacuating.clear();
        ledger.clear();
        spawns = Map.of();
    }

    boolean join(Player player) {
        JoinPlacement placement = joining.remove(player.getUniqueId());
        World current = player.getWorld();
        boolean entering = placement != null && placement.original().getWorld() != null
                && !current.getUID().equals(placement.original().getWorld().getUID());
        if (admit(player, current, entering)) {
            ledger.arrive(player.getUniqueId(), current.getUID());
            return true;
        }
        deny(player, current, entering);
        List<Location> alternatives = new ArrayList<>();
        if (placement != null && current.getUID().equals(placement.selectedWorld())) {
            Spawn login = namedSpawn(placement.login());
            if (login != null) {
                alternatives.add(login.location().clone());
            }
            alternatives.add(placement.original().clone());
        }
        evacuate(player, alternatives, entering);
        return false;
    }

    void changed(Player player) {
        ledger.arrive(player.getUniqueId(), player.getWorld().getUID());
    }

    void quit(Player player) {
        UUID id = player.getUniqueId();
        ledger.leave(id);
        joining.remove(id);
        evacuating.remove(id);
    }

    boolean enforceAccess(Player player) {
        if (hasAccess(player, player.getWorld())) {
            return true;
        }
        deny(player, player.getWorld(), false);
        evacuate(player, List.of(), false);
        return false;
    }

    Location respawn(Player player, Location requested) {
        World target = requested.getWorld();
        boolean crossWorld = target != null && !target.getUID().equals(player.getWorld().getUID());
        if (target != null && admit(player, target, crossWorld)) {
            return requested;
        }
        Location fallback = fallback(player, List.of(), target);
        if (fallback != null) {
            return fallback;
        }
        FoliaScheduler.runEntity(plugin, player, () -> disconnect(player), 1L);
        return requested;
    }

    void teleport(PlayerTeleportEvent event) {
        teleport(event, true);
    }

    private void teleport(PlayerTeleportEvent event, boolean reserve) {
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        Player player = event.getPlayer();
        World source = event.getFrom().getWorld();
        World target = event.getTo().getWorld();
        boolean crossWorld = source != null && !source.getUID().equals(target.getUID());
        if (!crossWorld) {
            return;
        }
        WorldProfile sourceProfile = profile(source);
        Evacuation evacuation = evacuating.get(player.getUniqueId());
        boolean recovery = evacuation != null && !evacuation.eventCaptured && evacuation.destination.equals(target.getUID())
                && event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN;
        if (!recovery && sourceProfile != null
                && sourceProfile.getTravelExit().equals("DENY") && !player.hasPermission("rift.travel.bypass")) {
            event.setCancelled(true);
            travelDenied(player, sourceProfile.getName());
            return;
        }
        if (!admit(player, target, true, reserve)) {
            event.setCancelled(true);
            deny(player, target, true);
            return;
        }
        WorldAdmissionLedger.Reservation reservation = reserve ? ledger.reservation(player.getUniqueId()) : null;
        if (recovery && reserve) {
            evacuation.eventCaptured = true;
        }
        if (reservation != null && reservation.world().equals(target.getUID())) {
            teleportReservations.put(event, reservation);
            if (recovery) {
                evacuation.reservation = reservation;
            }
        }
    }

    void afterTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        WorldAdmissionLedger.Reservation reservation = teleportReservations.remove(event);
        if (reservation == null) {
            return;
        }
        Location destination = event.getTo();
        if (event.isCancelled() || destination == null || destination.getWorld() == null
                || !reservation.world().equals(destination.getWorld().getUID())) {
            ledger.cancel(id, reservation);
            return;
        }
        if (!FoliaScheduler.runEntity(plugin, player,
                () -> ledger.reconcile(id, player.getWorld().getUID(), reservation), 1L, () -> ledger.leave(id))) {
            plugin.getLogger().warning("Unable to schedule teleport admission reconciliation for " + id);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        teleport(event, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGateway(PlayerTeleportEndGatewayEvent event) {
        teleport(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterGateway(PlayerTeleportEndGatewayEvent event) {
        afterTeleport(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(AsyncPlayerSpawnLocationEvent event) {
        UUID player = event.getConnection().getProfile().getId();
        if (player == null || ledger.contains(player)) {
            return;
        }
        Location original = event.getSpawnLocation();
        if (original.getWorld() == null) {
            return;
        }
        Spawn source = spawns.get(original.getWorld().getUID());
        if (source == null || profiles.find(source.name()).isEmpty()) {
            return;
        }
        Spawn target = event.isNewPlayer() ? namedSpawn(source.firstJoin()) : null;
        if (target == null) {
            target = namedSpawn(source.login());
        }
        if (target == null) {
            return;
        }
        Location selected = target.location().clone();
        joining.put(player, new JoinPlacement(original.clone(), target.id(), source.login()));
        event.setSpawnLocation(selected);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClosed(PlayerConnectionCloseEvent event) {
        joining.remove(event.getPlayerUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoad(WorldLoadEvent event) {
        refresh();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(WorldUnloadEvent event) {
        Map<UUID, Spawn> next = new LinkedHashMap<>(spawns);
        next.remove(event.getWorld().getUID());
        spawns = Collections.unmodifiableMap(next);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnChanged(SpawnChangeEvent event) {
        FoliaScheduler.runGlobal(plugin, this::refresh);
    }

    private boolean admit(Player player, World target, boolean entering) {
        return admit(player, target, entering, true);
    }

    private boolean admit(Player player, World target, boolean entering, boolean reserve) {
        WorldProfile profile = profile(target);
        if (!hasAccess(player, target)) {
            return false;
        }
        if (entering && profile != null && profile.getTravelEntry().equals("DENY")
                && !player.hasPermission("rift.travel.bypass")) {
            return false;
        }
        int limit = profile == null ? 0 : profile.getPlayerLimit();
        boolean bypass = player.hasPermission("rift.limit.bypass");
        return reserve ? ledger.reserve(player.getUniqueId(), target.getUID(), limit, bypass)
                : ledger.available(player.getUniqueId(), target.getUID(), limit, bypass);
    }

    private boolean hasAccess(Player player, World world) {
        WorldProfile profile = profile(world);
        return profile == null || profile.getAccessPermission().isBlank()
                || player.hasPermission(profile.getAccessPermission()) || player.hasPermission("rift.access.bypass")
                || player.hasPermission("rift.admin");
    }

    private void evacuate(Player player, List<Location> alternatives, boolean entering) {
        UUID id = player.getUniqueId();
        if (evacuating.containsKey(id)) {
            return;
        }
        Location destination = fallback(player, alternatives, player.getWorld());
        if (destination == null) {
            disconnect(player);
            return;
        }
        Evacuation evacuation = new Evacuation(destination.getWorld().getUID(), ledger.reservation(id));
        evacuating.put(id, evacuation);
        teleports.teleport(player, destination, success -> {
            if (!evacuating.remove(id, evacuation)) {
                return;
            }
            if (!FoliaScheduler.isOwnedByCurrentRegion(player)) {
                ledger.leave(id);
                return;
            }
            if (success) {
                ledger.arrive(id, player.getWorld().getUID());
            } else {
                WorldAdmissionLedger.Reservation current = ledger.reservation(id);
                if (current != null && current != evacuation.reservation) {
                    return;
                }
                ledger.cancel(id, evacuation.reservation);
                if (!admit(player, player.getWorld(), entering)) {
                    disconnect(player);
                } else {
                    ledger.arrive(id, player.getWorld().getUID());
                }
            }
        });
    }

    private Location fallback(Player player, List<Location> alternatives, World excluded) {
        for (Location candidate : alternatives) {
            World world = candidate.getWorld();
            if (world != null && spawns.containsKey(world.getUID()) && !world.equals(excluded)
                    && admit(player, world, true)) {
                return candidate.clone();
            }
        }
        for (Spawn candidate : spawns.values()) {
            World world = candidate.location().getWorld();
            if (world != null && !world.equals(excluded) && admit(player, world, true)) {
                return candidate.location().clone();
            }
        }
        return null;
    }

    private void deny(Player player, World world, boolean entering) {
        WorldProfile profile = profile(world);
        if (profile == null) {
            return;
        }
        if (!hasAccess(player, world)) {
            if (!profile.getAccessDeniedMessage().isBlank()) {
                player.sendPlainMessage(accessMessage(profile));
            } else {
                language.send(player, RiftMessages.POLICY_ACCESS_DENIED, MessageArgs.builder()
                        .untrusted("world", profile.getName()).untrusted("permission", profile.getAccessPermission()).build());
            }
        } else if (entering && profile.getTravelEntry().equals("DENY") && !player.hasPermission("rift.travel.bypass")) {
            travelDenied(player, profile.getName());
        } else {
            language.send(player, RiftMessages.POLICY_WORLD_FULL, MessageArgs.builder()
                    .untrusted("world", profile.getName()).untrusted("limit", Integer.toString(profile.getPlayerLimit())).build());
        }
    }

    private void disconnect(Player player) {
        ledger.arrive(player.getUniqueId(), player.getWorld().getUID());
        WorldProfile profile = profile(player.getWorld());
        if (profile != null && !hasAccess(player, player.getWorld()) && !profile.getAccessDeniedMessage().isBlank()) {
            player.kickPlayer(accessMessage(profile));
            return;
        }
        player.kickPlayer(language.text(player, RiftMessages.POLICY_PLACEMENT_FAILED).plain());
    }

    private void travelDenied(Player player, String world) {
        language.send(player, RiftMessages.POLICY_TRAVEL_DENIED, MessageArgs.builder().untrusted("world", world).build());
    }

    private String accessMessage(WorldProfile profile) {
        return profile.getAccessDeniedMessage().replace("{world}", profile.getName())
                .replace("{permission}", profile.getAccessPermission());
    }

    private WorldProfile profile(World world) {
        return world == null ? null : profiles.find(RiftWorldIdentity.logicalName(world, profiles)).orElse(null);
    }

    private Spawn snapshot(World world) {
        WorldProfile profile = profile(world);
        Location location = world.getSpawnLocation();
        return location == null ? null : new Spawn(world.getUID(), RiftWorldIdentity.logicalName(world, profiles),
                location.clone(), profile == null ? "" : profile.getFirstJoinWorld(),
                profile == null ? "" : profile.getLoginWorld());
    }

    private Spawn namedSpawn(String name) {
        if (name.isBlank() || profiles.find(name).isEmpty()) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT);
        for (Spawn spawn : spawns.values()) {
            if (spawn.name().toLowerCase(Locale.ROOT).equals(key)) {
                return spawn;
            }
        }
        return null;
    }

    private record Spawn(UUID id, String name, Location location, String firstJoin, String login) {
    }

    private record JoinPlacement(Location original, UUID selectedWorld, String login) {
    }

    private static final class Evacuation {
        private final UUID destination;
        private volatile WorldAdmissionLedger.Reservation reservation;
        private volatile boolean eventCaptured;

        private Evacuation(UUID destination, WorldAdmissionLedger.Reservation reservation) {
            this.destination = destination;
            this.reservation = reservation;
        }
    }
}
