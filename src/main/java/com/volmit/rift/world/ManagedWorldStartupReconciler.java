package com.volmit.rift.world;

import com.volmit.rift.storage.WorldDirectoryResolver;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ManagedWorldStartupReconciler {
    private final Logger logger;
    private final WorldDirectoryResolver directories;
    private final WorldProfileStore profiles;
    private final WorldInventory inventory;
    private final Predicate<String> loadedWorld;

    ManagedWorldStartupReconciler(Options options) {
        Options required = Objects.requireNonNull(options, "options");
        logger = Objects.requireNonNull(required.logger(), "logger");
        directories = Objects.requireNonNull(required.directories(), "directories");
        profiles = Objects.requireNonNull(required.profiles(), "profiles");
        inventory = Objects.requireNonNull(required.inventory(), "inventory");
        loadedWorld = Objects.requireNonNull(required.loadedWorld(), "loadedWorld");
    }

    List<WorldProfile> reconcile() {
        ArrayList<WorldProfile> availableProfiles = new ArrayList<>();
        for (WorldProfile profile : profiles.all()) {
            if (loadedWorld.test(profile.getName())) {
                availableProfiles.add(profile);
                continue;
            }
            reconcile(profile, availableProfiles);
        }
        return List.copyOf(availableProfiles);
    }

    private void reconcile(WorldProfile profile, List<WorldProfile> availableProfiles) {
        Optional<File> retiredProfile;
        try {
            if (directories.find(profile).isPresent()) {
                availableProfiles.add(profile);
                return;
            }
            if (!directories.isDefinitelyMissing(profile)) {
                throw new IOException("Managed world storage exists for " + profile.getName()
                        + " but is not a valid supported world directory");
            }
            if (profile.isProtectedWorld()) {
                logger.warning("Protected managed world " + profile.getName()
                        + " has no world storage; its profile remains managed until protection is removed");
                return;
            }
            retiredProfile = profiles.retire(profile.getName());
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.SEVERE,
                    "Unable to reconcile managed world " + profile.getName()
                            + " during startup; its profile remains managed",
                    exception);
            return;
        }
        try {
            inventory.markAbsent(profile.getName());
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE,
                    "Retired missing managed world " + profile.getName()
                            + " but could not update the world inventory cache",
                    exception);
        }
        logger.warning("Stopped managing " + profile.getName()
                + " during startup because its world storage no longer exists; retired profile: "
                + retiredProfile.map(File::getAbsolutePath).orElse("profile file was already absent")
                + "; restore the folder and run /rift import to manage it again");
    }

    record Options(
            Logger logger,
            WorldDirectoryResolver directories,
            WorldProfileStore profiles,
            WorldInventory inventory,
            Predicate<String> loadedWorld
    ) {
    }
}
