package com.example.oxyarena.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.oxyarena.Config;
import com.example.oxyarena.network.InventorySortRequestPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class InventoryInteractionController {
    private static final int MOUSE_LEFT = 0;
    private static final int MOUSE_RIGHT = 1;
    private static final int MOUSE_MIDDLE = 2;

    private static DragMode dragMode = DragMode.NONE;
    private static final Set<Integer> visitedSlots = new HashSet<>();
    private static Screen activeScreen;

    private InventoryInteractionController() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(InventoryInteractionController::onMousePressed);
        NeoForge.EVENT_BUS.addListener(InventoryInteractionController::onMouseReleased);
        NeoForge.EVENT_BUS.addListener(InventoryInteractionController::onMouseDragged);
        NeoForge.EVENT_BUS.addListener(InventoryInteractionController::onMouseScrolled);
        NeoForge.EVENT_BUS.addListener(InventoryInteractionController::onScreenClosing);
    }

    private static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        AbstractContainerScreen<?> screen = getSupportedScreen(event.getScreen());
        if (screen == null) {
            return;
        }

        syncScreen(screen);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null || minecraft.player.isSpectator()) {
            return;
        }

        Slot hoveredSlot = screen.getSlotUnderMouse();
        ItemStack carried = screen.getMenu().getCarried();
        int button = event.getButton();

        if (button == MOUSE_MIDDLE && Config.inventoryQolSortingEnabled() && carried.isEmpty() && isSortableTriggerSlot(hoveredSlot, minecraft.player.getInventory())) {
            PacketDistributor.sendToServer(new InventorySortRequestPayload(screen.getMenu().containerId, hoveredSlot.index));
            event.setCanceled(true);
            return;
        }

        if (!Config.inventoryQolMouseDragEnabled()) {
            return;
        }

        if (button == MOUSE_RIGHT && !carried.isEmpty()) {
            dragMode = DragMode.RMB_DISTRIBUTE;
            visitedSlots.clear();
            event.setCanceled(true);
            return;
        }

        if (button == MOUSE_LEFT) {
            visitedSlots.clear();
            if (!carried.isEmpty()) {
                dragMode = DragMode.LMB_PLACE;
                event.setCanceled(true);
                return;
            }

            if (Screen.hasShiftDown()) {
                dragMode = DragMode.LMB_SHIFT_QUICK_MOVE;
                event.setCanceled(true);
            }
        }
    }

    private static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (dragMode == DragMode.NONE) {
            return;
        }

        if (event.getButton() == MOUSE_LEFT || event.getButton() == MOUSE_RIGHT) {
            resetDragState();
            event.setCanceled(true);
        }
    }

    private static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        AbstractContainerScreen<?> screen = getSupportedScreen(event.getScreen());
        if (screen == null) {
            resetDragState();
            return;
        }

        syncScreen(screen);
        if (dragMode == DragMode.NONE || !Config.inventoryQolMouseDragEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) {
            resetDragState();
            return;
        }

        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (!isProcessableSlot(hoveredSlot) || !visitedSlots.add(hoveredSlot.index)) {
            event.setCanceled(true);
            return;
        }

        boolean handled = switch (dragMode) {
            case RMB_DISTRIBUTE -> handleRightDrag(screen, hoveredSlot, minecraft);
            case LMB_PLACE -> handleLeftDrag(screen, hoveredSlot, minecraft);
            case LMB_SHIFT_QUICK_MOVE -> handleShiftQuickMove(screen, hoveredSlot, minecraft);
            case NONE -> false;
        };

        if (handled) {
            event.setCanceled(true);
        }
    }

    private static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        AbstractContainerScreen<?> screen = getSupportedScreen(event.getScreen());
        if (screen == null || !Config.inventoryQolWheelMoveEnabled()) {
            return;
        }

        syncScreen(screen);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null || minecraft.player.isSpectator()) {
            return;
        }

        Slot hoveredSlot = screen.getSlotUnderMouse();
        if (!isProcessableSlot(hoveredSlot)) {
            return;
        }

        int direction = (int)Math.signum(event.getScrollDeltaY());
        if (direction == 0) {
            return;
        }

        boolean handled = direction > 0
                ? pullOneItemToHovered(screen, hoveredSlot, minecraft)
                : pushOneItemFromHovered(screen, hoveredSlot, minecraft);
        if (handled) {
            event.setCanceled(true);
        }
    }

    private static void onScreenClosing(ScreenEvent.Closing event) {
        resetDragState();
        activeScreen = null;
    }

    private static boolean handleRightDrag(AbstractContainerScreen<?> screen, Slot slot, Minecraft minecraft) {
        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty() || isIgnoredSlot(slot) || !slot.mayPlace(carried)) {
            return false;
        }

        ItemStack slotStack = slot.getItem();
        if (!slotStack.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(slotStack, carried) || slotStack.getCount() >= slot.getMaxStackSize(slotStack)) {
                return false;
            }
        }

        clickSlot(screen, slot.index, MOUSE_RIGHT, ClickType.PICKUP, minecraft);
        return true;
    }

    private static boolean handleLeftDrag(AbstractContainerScreen<?> screen, Slot slot, Minecraft minecraft) {
        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty() || isIgnoredSlot(slot) || !slot.mayPlace(carried)) {
            return false;
        }

        clickSlot(screen, slot.index, MOUSE_LEFT, ClickType.PICKUP, minecraft);
        if (screen.getMenu().getCarried().isEmpty()) {
            resetDragState();
        }
        return true;
    }

    private static boolean handleShiftQuickMove(AbstractContainerScreen<?> screen, Slot slot, Minecraft minecraft) {
        if (isIgnoredSlot(slot) || !slot.hasItem() || !slot.mayPickup(minecraft.player)) {
            return false;
        }

        clickSlot(screen, slot.index, MOUSE_LEFT, ClickType.QUICK_MOVE, minecraft);
        return true;
    }

    private static boolean pushOneItemFromHovered(AbstractContainerScreen<?> screen, Slot hoveredSlot, Minecraft minecraft) {
        if (isIgnoredSlot(hoveredSlot) || !hoveredSlot.hasItem() || !hoveredSlot.mayPickup(minecraft.player)) {
            return false;
        }

        Slot targetSlot = findPushTarget(screen.getMenu(), hoveredSlot, minecraft.player.getInventory());
        if (targetSlot == null) {
            return false;
        }

        clickSlot(screen, hoveredSlot.index, MOUSE_LEFT, ClickType.PICKUP, minecraft);
        if (screen.getMenu().getCarried().isEmpty()) {
            return false;
        }

        clickSlot(screen, targetSlot.index, MOUSE_RIGHT, ClickType.PICKUP, minecraft);
        clickSlot(screen, hoveredSlot.index, MOUSE_LEFT, ClickType.PICKUP, minecraft);
        return true;
    }

    private static boolean pullOneItemToHovered(AbstractContainerScreen<?> screen, Slot hoveredSlot, Minecraft minecraft) {
        if (isIgnoredSlot(hoveredSlot)) {
            return false;
        }

        ItemStack hoveredStack = hoveredSlot.getItem();
        if (!hoveredStack.isEmpty() && hoveredStack.getCount() >= hoveredSlot.getMaxStackSize(hoveredStack)) {
            return false;
        }

        if (!hoveredStack.isEmpty() && !hoveredSlot.mayPlace(hoveredStack)) {
            return false;
        }

        Slot sourceSlot = findPullSource(screen.getMenu(), hoveredSlot, minecraft.player.getInventory());
        if (sourceSlot == null) {
            return false;
        }

        clickSlot(screen, sourceSlot.index, MOUSE_LEFT, ClickType.PICKUP, minecraft);
        ItemStack carried = screen.getMenu().getCarried();
        if (carried.isEmpty() || !hoveredSlot.mayPlace(carried)) {
            if (!carried.isEmpty()) {
                clickSlot(screen, sourceSlot.index, MOUSE_LEFT, ClickType.PICKUP, minecraft);
            }
            return false;
        }

        clickSlot(screen, hoveredSlot.index, MOUSE_RIGHT, ClickType.PICKUP, minecraft);
        clickSlot(screen, sourceSlot.index, MOUSE_LEFT, ClickType.PICKUP, minecraft);
        return true;
    }

    private static Slot findPushTarget(AbstractContainerMenu menu, Slot sourceSlot, Inventory playerInventory) {
        List<Slot> sameSide = getOppositeSideSlots(menu, sourceSlot, playerInventory);
        ItemStack sourceStack = sourceSlot.getItem();

        for (Slot slot : sameSide) {
            ItemStack candidate = slot.getItem();
            if (!candidate.isEmpty() && slot.mayPlace(sourceStack) && ItemStack.isSameItemSameComponents(candidate, sourceStack)
                    && candidate.getCount() < slot.getMaxStackSize(candidate)) {
                return slot;
            }
        }

        for (Slot slot : sameSide) {
            if (slot.getItem().isEmpty() && slot.mayPlace(sourceStack)) {
                return slot;
            }
        }

        return null;
    }

    private static Slot findPullSource(AbstractContainerMenu menu, Slot targetSlot, Inventory playerInventory) {
        List<Slot> sameSide = getOppositeSideSlots(menu, targetSlot, playerInventory);
        ItemStack targetStack = targetSlot.getItem();

        for (Slot slot : sameSide) {
            if (!slot.hasItem() || isIgnoredSlot(slot)) {
                continue;
            }

            ItemStack sourceStack = slot.getItem();
            if (targetStack.isEmpty()) {
                if (targetSlot.mayPlace(sourceStack)) {
                    return slot;
                }
            } else if (ItemStack.isSameItemSameComponents(sourceStack, targetStack) && targetSlot.mayPlace(sourceStack)) {
                return slot;
            }
        }

        return null;
    }

    private static List<Slot> getOppositeSideSlots(AbstractContainerMenu menu, Slot anchorSlot, Inventory playerInventory) {
        boolean anchorIsPlayer = anchorSlot.container == playerInventory;
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (!isProcessableSlot(slot) || isIgnoredSlot(slot)) {
                continue;
            }

            boolean slotIsPlayer = slot.container == playerInventory;
            if (slotIsPlayer != anchorIsPlayer) {
                slots.add(slot);
            }
        }

        return slots;
    }

    private static boolean isSortableTriggerSlot(Slot slot, Inventory playerInventory) {
        if (!isProcessableSlot(slot)) {
            return false;
        }

        if (slot.container == playerInventory) {
            return slot.getContainerSlot() >= 9 && slot.getContainerSlot() < 36;
        }

        return slot.getClass() == Slot.class;
    }

    private static boolean isProcessableSlot(Slot slot) {
        return slot != null && slot.isActive() && !slot.isFake();
    }

    private static boolean isIgnoredSlot(Slot slot) {
        return slot instanceof ResultSlot;
    }

    private static void clickSlot(AbstractContainerScreen<?> screen, int slotIndex, int button, ClickType clickType, Minecraft minecraft) {
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        if (minecraft.player == null || gameMode == null) {
            return;
        }

        gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slotIndex, button, clickType, minecraft.player);
    }

    private static AbstractContainerScreen<?> getSupportedScreen(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen) || screen instanceof CreativeModeInventoryScreen) {
            return null;
        }

        return containerScreen;
    }

    private static void syncScreen(Screen screen) {
        if (activeScreen != screen) {
            activeScreen = screen;
            resetDragState();
        }
    }

    private static void resetDragState() {
        dragMode = DragMode.NONE;
        visitedSlots.clear();
    }

    private enum DragMode {
        NONE,
        RMB_DISTRIBUTE,
        LMB_PLACE,
        LMB_SHIFT_QUICK_MOVE
    }
}
