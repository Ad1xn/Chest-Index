package dev.adrian.chesttracker.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.adrian.chesttracker.ChestTracker;
import dev.adrian.chesttracker.client.platform.ClientCompat;
import dev.adrian.chesttracker.client.ui.SearchButton;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.mixin.client.ContainerScreenAccessor;
import dev.adrian.chesttracker.mixin.client.KeyMappingAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

public final class ContainerScreens {

    private ContainerScreens() {}

    private static final long DEBOUNCE_MS = 300;

    private static KeyMapping searchKey;
    private static boolean keyWasDown;
    private static long lastTrigger;

    private static boolean reportedInit;

    public static void register(KeyMapping searchHovered) {
        searchKey = searchHovered;

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;

            ContainerScreenAccessor access = (ContainerScreenAccessor) container;

            if (ChestTrackerConfig.get().containerSearchButton) {
                ClientCompat.addWidget(screen, new SearchButton(
                        access.chesttracker$leftPos() + access.chesttracker$imageWidth(),
                        access.chesttracker$topPos()));
            }

            if (!reportedInit) {
                reportedInit = true;
                ChestTracker.LOG.info("Container screen hook installed on {}",
                        container.getClass().getName());
            }

            ScreenKeyboardEvents.beforeKeyPress(screen).register((ignored, keyEvent) -> {
                if (searchHovered.matches(keyEvent)) trigger(container, "screen event");
            });
        });
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        AbstractContainerScreen<?> container =
                ClientCompat.currentScreen() instanceof AbstractContainerScreen<?> open ? open : null;
        if (container == null || searchKey == null || client.getWindow() == null) {
            keyWasDown = false;
            return;
        }

        InputConstants.Key key = ((KeyMappingAccessor) searchKey).chesttracker$key();
        boolean down = key != null
                && key.getType() == InputConstants.Type.KEYSYM
                && key.getValue() >= 0
                && InputConstants.isKeyDown(client.getWindow(), key.getValue());

        if (down && !keyWasDown) trigger(container, "poll");
        keyWasDown = down;
    }

    private static void trigger(AbstractContainerScreen<?> container, String via) {
        long now = System.currentTimeMillis();
        if (now - lastTrigger < DEBOUNCE_MS) return;
        lastTrigger = now;

        ChestTracker.LOG.info("Container search key fired via {}", via);

        Slot hovered = ((ContainerScreenAccessor) container).chesttracker$hoveredSlot();
        if (hovered == null || !hovered.hasItem()) {
            ContainerSearch.sayNothingHovered();
            return;
        }

        if (ContainerSearch.findAndGuide(hovered.getItem())) {
            container.onClose();
        }
    }
}
