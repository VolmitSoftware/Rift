package com.volmit.rift.gui;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.volmit.rift.Rift;
import com.volmit.rift.localization.RiftLocalization;
import com.volmit.rift.world.WorldPolicyService;
import com.volmit.rift.world.WorldPolicyService.GameRuleSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class RiftWorldPolicyMenuTest {
    @Test
    void pageSlotsStayInsideCenteredContentAndAwayFromNavigation() {
        Set<Integer> slots = new HashSet<>();
        for (int i = 0; i < 28; i++) {
            int slot = RiftWorldPolicyMenu.contentSlot(i);
            assertThat(slot).isBetween(10, 43);
            assertThat(slot % 9).isBetween(1, 7);
            slots.add(slot);
        }
        assertThat(slots).hasSize(28);
        assertThatThrownBy(() -> RiftWorldPolicyMenu.contentSlot(28)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stalePagesClampToLastCurrentPage() {
        assertThat(RiftWorldPolicyMenu.pageOffset(100, 29)).isEqualTo(28);
        assertThat(RiftWorldPolicyMenu.pageOffset(-1, 29)).isZero();
        assertThat(RiftWorldPolicyMenu.pageOffset(10, 0)).isZero();
    }

    @Test
    void numericClicksAdjustBothDirectionsAndShiftMultipliesByTen() {
        assertThat(RiftWorldPolicyMenu.steps(ClickType.LEFT)).isEqualTo(1);
        assertThat(RiftWorldPolicyMenu.steps(ClickType.RIGHT)).isEqualTo(-1);
        assertThat(RiftWorldPolicyMenu.steps(ClickType.SHIFT_LEFT)).isEqualTo(10);
        assertThat(RiftWorldPolicyMenu.steps(ClickType.SHIFT_RIGHT)).isEqualTo(-10);
        assertThat(RiftWorldPolicyMenu.reset(ClickType.RIGHT)).isFalse();
        assertThat(RiftWorldPolicyMenu.reset(ClickType.DROP)).isTrue();
        assertThat(RiftWorldPolicyMenu.reset(ClickType.CONTROL_DROP)).isTrue();
        assertThat(RiftWorldPolicyMenu.supported(ClickType.NUMBER_KEY)).isFalse();
    }

    @Test
    void openingAnotherInventoryCancelsPendingPrompt() throws Exception {
        Rift plugin = plugin();
        Player player = player();
        RiftWorldPolicyMenu menu = new RiftWorldPolicyMenu(plugin);
        UUID id = UUID.randomUUID();
        Object page = page();
        Class<?> promptClass = nested("Prompt");
        Constructor<?> constructor = promptClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Function<String, CompletableFuture<Boolean>> action = input -> CompletableFuture.completedFuture(true);
        map(menu, "prompts").put(player.getUniqueId(), constructor.newInstance(id, page, action));
        map(menu, "sessions").put(player.getUniqueId(), id);
        InventoryOpenEvent open = mock(InventoryOpenEvent.class);
        when(open.getPlayer()).thenReturn(player);
        when(open.getInventory()).thenReturn(mock(Inventory.class));
        menu.onOpen(open);

        AsyncPlayerChatEvent chat = mock(AsyncPlayerChatEvent.class);
        when(chat.getPlayer()).thenReturn(player);
        menu.onChat(chat);

        assertThat(map(menu, "sessions")).isEmpty();
        assertThat(map(menu, "prompts")).isEmpty();
        verify(chat, never()).setCancelled(true);
    }

    @Test
    void delayedSaveDoesNotReplaceAnInventoryOpenedMeanwhile() throws Exception {
        Rift plugin = plugin();
        Player player = player();
        when(player.hasPermission("rift.policy")).thenReturn(true);
        RiftWorldPolicyMenu menu = new RiftWorldPolicyMenu(plugin);
        CompletableFuture<Boolean> saved = new CompletableFuture<>();
        ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), eq(player), any(Runnable.class)))
                    .thenAnswer(call -> { callbacks.add(call.getArgument(2)); return true; });
            Method save = RiftWorldPolicyMenu.class.getDeclaredMethod("save", Player.class, nested("Page"), Supplier.class);
            save.setAccessible(true);
            save.invoke(menu, player, page(), (Supplier<CompletableFuture<Boolean>>) () -> saved);
            InventoryOpenEvent open = mock(InventoryOpenEvent.class);
            when(open.getPlayer()).thenReturn(player);
            when(open.getInventory()).thenReturn(mock(Inventory.class));
            menu.onOpen(open);
            saved.complete(true);
            callbacks.removeFirst().run();
        }
        verify(player, never()).openInventory(any(Inventory.class));
        assertThat(map(menu, "sessions")).isEmpty();
    }

    @Test
    void queuedClickChecksPermissionAgainBeforeMutation() throws Exception {
        Rift plugin = plugin();
        Player player = player();
        RiftWorldPolicyMenu menu = new RiftWorldPolicyMenu(plugin);
        Constructor<?> constructor = nested("Holder").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object holder = constructor.newInstance();
        AtomicBoolean changed = new AtomicBoolean();
        Map<Object, Object> actions = map(holder, "actions");
        actions.put(10, (BiConsumer<Player, ClickType>) (target, click) -> changed.set(true));
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn((InventoryHolder) holder);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        when(player.getOpenInventory()).thenReturn(view);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(10);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), eq(player), any(Runnable.class), eq(1L)))
                    .thenAnswer(call -> { callbacks.add(call.getArgument(2)); return true; });
            menu.onClick(event);
            callbacks.removeFirst().run();
        }
        verify(event).setCancelled(true);
        verify(player).closeInventory();
        assertThat(changed).isFalse();
    }

    @Test
    void asynchronousRuleInspectionDoesNotReopenAfterEscape() throws Exception {
        Rift plugin = plugin();
        Player player = player();
        when(player.hasPermission("rift.policy")).thenReturn(true);
        InventoryView view = mock(InventoryView.class);
        Inventory previous = mock(Inventory.class);
        when(view.getTopInventory()).thenReturn(previous);
        when(player.getOpenInventory()).thenReturn(view);
        CompletableFuture<GameRuleSnapshot> inspected = new CompletableFuture<>();
        when(plugin.worldPolicies().gameRuleValues("example")).thenReturn(inspected);
        RiftWorldPolicyMenu menu = new RiftWorldPolicyMenu(plugin);
        ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.runEntity(eq(plugin), eq(player), any(Runnable.class)))
                    .thenAnswer(call -> { callbacks.add(call.getArgument(2)); return true; });
            Constructor<?> constructor = nested("Page").getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object rules = constructor.newInstance("example", nested("View").getEnumConstants()[1], 0);
            Method show = RiftWorldPolicyMenu.class.getDeclaredMethod("show", Player.class, nested("Page"));
            show.setAccessible(true);
            show.invoke(menu, player, rules);
            when(view.getTopInventory()).thenReturn(mock(Inventory.class));
            inspected.complete(new GameRuleSnapshot(true, Map.of()));
            callbacks.removeFirst().run();
        }
        verify(player, never()).openInventory(any(Inventory.class));
    }

    @Test
    void spawnDraftChangesAreIndependentUntilExplicitApply() throws Exception {
        Constructor<?> constructor = nested("SpawnDraft").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object draft = constructor.newInstance("example", 0.5D, 64.0D, 0.5D, 0.0D);
        Object x = nested("SpawnAxis").getEnumConstants()[0];
        Method adjust = nested("SpawnDraft").getDeclaredMethod("adjust", nested("SpawnAxis"), long.class, boolean.class);
        Method value = nested("SpawnDraft").getDeclaredMethod("value", nested("SpawnAxis"));
        adjust.setAccessible(true);
        value.setAccessible(true);
        Object changed = adjust.invoke(draft, x, 10L, false);
        assertThat(value.invoke(draft, x)).isEqualTo(0.5D);
        assertThat(value.invoke(changed, x)).isEqualTo(10.5D);
        Object reset = adjust.invoke(changed, x, 0L, true);
        assertThat(value.invoke(reset, x)).isEqualTo(0.5D);
    }

    private static Rift plugin() {
        Rift plugin = mock(Rift.class);
        when(plugin.worldPolicies()).thenReturn(mock(WorldPolicyService.class));
        when(plugin.language()).thenReturn(mock(RiftLocalization.class));
        return plugin;
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static Object page() throws Exception {
        Constructor<?> constructor = nested("Page").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance("example", nested("View").getEnumConstants()[0], 0);
    }

    private static Class<?> nested(String name) throws ClassNotFoundException {
        return Class.forName(RiftWorldPolicyMenu.class.getName() + '$' + name);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(target);
    }
}
