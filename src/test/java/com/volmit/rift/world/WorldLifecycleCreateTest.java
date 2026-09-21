package com.volmit.rift.world;

import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.config.RiftConfigManager;
import com.volmit.rift.feedback.RiftFeedbackService;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.localization.RiftMessages;
import com.volmit.rift.storage.RiftPaths;
import com.volmit.rift.storage.TrashStore;
import com.volmit.rift.storage.WorldDirectoryResolver;
import com.volmit.rift.storage.WorldNamePolicy;
import com.volmit.rift.storage.WorldProfile;
import com.volmit.rift.storage.WorldProfileStore;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

final class WorldLifecycleCreateTest {
    @Test
    void loadedWorldCollisionIsAnOrdinaryRejection() throws IOException {
        try (Context context = new Context()) {
            context.identity.when(() -> RiftWorldIdentity.findLoaded("existing")).thenReturn(mock(World.class));
            context.create();
            context.assertRejected();
            verifyNoInteractions(context.directories);
        }
    }

    @Test
    void directoryCollisionPreservesStorage() throws IOException {
        try (Context context = new Context()) {
            when(context.directories.exists("existing")).thenReturn(true);
            context.create();
            context.assertRejected();
            verifyNoInteractions(context.profiles);
        }
    }

    @Test
    void profileCollisionPreservesProfileAndReleasesLock() throws IOException {
        try (Context context = new Context()) {
            when(context.profiles.find("existing")).thenReturn(Optional.of(new WorldProfile()));
            context.create();
            context.assertRejected();
            context.create();
            verify(context.language, times(2)).send(eq(context.sender), eq(RiftMessages.WORLD_ALREADY_EXISTS), any(MessageArgs.class));
            verify(context.profiles, never()).save(any());
        }
    }

    @Test
    void collisionIsCheckedAgainWhenQueuedOperationExecutes() throws IOException {
        try (Context context = new Context()) {
            context.enqueue();
            verifyNoInteractions(context.directories);
            when(context.directories.exists("existing")).thenReturn(true);
            context.global.removeFirst().run();
            context.assertRejected();
        }
    }

    @Test
    void storageFailureStillLogsItsFullException() throws IOException {
        try (Context context = new Context()) {
            IOException failure = new IOException("storage unavailable");
            when(context.directories.exists("existing")).thenThrow(failure);
            context.create();
            verify(context.logger).log(Level.SEVERE, "Rift create failed for existing", failure);
            verify(context.language).send(eq(context.sender), eq(RiftMessages.OPERATION_FAILED), any(MessageArgs.class));
            verify(context.language, never()).send(eq(context.sender), eq(RiftMessages.WORLD_ALREADY_EXISTS), any(MessageArgs.class));
            verify(context.profiles, never()).save(any());
        }
    }

    private static final class Context implements AutoCloseable {
        private final Rift plugin = mock(Rift.class);
        private final RiftLocalization language = mock(RiftLocalization.class);
        private final WorldDirectoryResolver directories = mock(WorldDirectoryResolver.class);
        private final WorldProfileStore profiles = mock(WorldProfileStore.class);
        private final WorldPolicyService policies = mock(WorldPolicyService.class);
        private final RiftFeedbackService feedback = mock(RiftFeedbackService.class);
        private final CommandSender sender = mock(CommandSender.class);
        private final Logger logger = mock(Logger.class);
        private final Deque<Runnable> global = new ArrayDeque<>();
        private final MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class);
        private final MockedStatic<RiftWorldIdentity> identity = mockStatic(RiftWorldIdentity.class);
        private final WorldLifecycleService service;

        private Context() {
            WorldNamePolicy names = mock(WorldNamePolicy.class);
            PlatformCapabilities capabilities = mock(PlatformCapabilities.class);
            when(names.requireValid("existing")).thenReturn("existing");
            when(capabilities.supportsDynamicWorldLifecycle()).thenReturn(true);
            when(plugin.getLogger()).thenReturn(logger);
            scheduler.when(() -> FoliaScheduler.runGlobal(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
                global.addLast(invocation.getArgument(1));
                return true;
            });
            scheduler.when(() -> FoliaScheduler.runAsync(eq(plugin), any(Runnable.class))).thenReturn(true);
            service = new WorldLifecycleService(plugin, mock(RiftConfigManager.class), language,
                    mock(RiftPaths.class), names, directories, profiles, mock(TrashStore.class), capabilities,
                    mock(WorldInventory.class), feedback, policies);
        }

        private void enqueue() {
            service.create(sender, "existing", World.Environment.NORMAL, "vanilla", "random", WorldType.NORMAL);
            assertThat(global).hasSize(1);
        }

        private void create() {
            enqueue();
            global.removeFirst().run();
        }

        private void assertRejected() throws IOException {
            verify(language).send(eq(sender), eq(RiftMessages.WORLD_ALREADY_EXISTS), any(MessageArgs.class));
            verify(language, never()).send(eq(sender), eq(RiftMessages.OPERATION_FAILED), any(MessageArgs.class));
            verify(profiles, never()).save(any());
            verifyNoInteractions(logger, feedback, policies);
        }

        @Override
        public void close() {
            identity.close();
            scheduler.close();
        }
    }
}
