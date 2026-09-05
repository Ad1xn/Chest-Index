package dev.adrian.chesttracker.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import dev.adrian.chesttracker.ChestTracker;
import dev.adrian.chesttracker.client.platform.ClientCompat;
import dev.adrian.chesttracker.client.ui.SearchButton;
import dev.adrian.chesttracker.client.ui.SlotHighlight;
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
    private static long lastTrigger;

    private static SearchButton button;

    private static boolean rightWasDown;
    private static boolean draggingButton;

    private static boolean reportedInit;

    public static void register(KeyMapping searchHovered) {
        searchKey = searchHovered;

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;

            ContainerScreenAccessor access = (ContainerScreenAccessor) container;

            button = null;
            if (ChestTrackerConfig.get().containerSearchButton) {
                button = new SearchButton(
                        access.chesttracker$leftPos() + access.chesttracker$imageWidth(),
                        access.chesttracker$topPos());
                ClientCompat.addWidget(screen, button);
            }

            if (!reportedInit) {
                reportedInit = true;
                ChestTracker.LOG.info("Container screen hook installed on {}",
                        container.getClass().getName());
            }

            ClientCompat.afterScreenRender(screen, (gfx, mouseX, mouseY) ->
                    SlotHighlight.draw(gfx, container,
                            access.chesttracker$leftPos(), access.chesttracker$topPos()));

            ScreenKeyboardEvents.allowKeyPress(screen).register((ignored, keyEvent) -> {
                if (!searchHovered.matches(keyEvent)) return true;
                trigger(container);
                return false;
            });
        });
    }

    public static void tick() {
        dragButton(Minecraft.getInstance());
    }

    private static void dragButton(Minecraft client) {
        SearchButton target = button;
        if (target == null || client.getWindow() == null) {
            rightWasDown = false;
            draggingButton = false;
            return;
        }

        boolean down = GLFW.glfwGetMouseButton(
                client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        double scaleX = client.getWindow().getGuiScaledWidth()
                / (double) client.getWindow().getScreenWidth();
        double scaleY = client.getWindow().getGuiScaledHeight()
                / (double) client.getWindow().getScreenHeight();
        int mouseX = (int) (client.mouseHandler.xpos() * scaleX);
        int mouseY = (int) (client.mouseHandler.ypos() * scaleY);

        if (down && !rightWasDown && target.isMouseOver(mouseX, mouseY)) draggingButton = true;
        if (draggingButton && down) {
            target.setX(mouseX - SearchButton.SIZE / 2);
            target.setY(mouseY - SearchButton.SIZE / 2);
        }
        if (draggingButton && !down) {
            draggingButton = false;
            ChestTrackerConfig config = ChestTrackerConfig.get();
            config.searchButtonX = target.getX() - target.anchorX();
            config.searchButtonY = target.getY() - target.anchorY();
            config.save();
        }
        rightWasDown = down;
    }

    private static void trigger(AbstractContainerScreen<?> container) {
        long now = System.currentTimeMillis();
        if (now - lastTrigger < DEBOUNCE_MS) return;
        lastTrigger = now;

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
