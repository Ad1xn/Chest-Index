package dev.adrian.chestindex.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import dev.adrian.chestindex.ChestIndex;
import dev.adrian.chestindex.client.platform.ClientCompat;
import dev.adrian.chestindex.client.ui.SearchButton;
import dev.adrian.chestindex.client.ui.SlotHighlight;
import dev.adrian.chestindex.config.ChestIndexConfig;
import dev.adrian.chestindex.mixin.client.ContainerScreenAccessor;
import dev.adrian.chestindex.mixin.client.KeyMappingAccessor;
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
            if (!Session.active()) {
                button = null;
                return;
            }

            ContainerScreenAccessor access = (ContainerScreenAccessor) container;

            button = null;
            if (ChestIndexConfig.get().containerSearchButton) {
                button = new SearchButton(
                        menuKeyOf(container),
                        () -> access.chestindex$leftPos() + access.chestindex$imageWidth(),
                        access::chestindex$topPos);
                ClientCompat.addWidget(screen, button);
            }

            if (!reportedInit) {
                reportedInit = true;
                ChestIndex.LOG.info("Container screen hook installed on {}",
                        container.getClass().getName());
            }

            ClientCompat.afterScreenRender(screen, (gfx, mouseX, mouseY) -> {
                if (SlotHighlight.alreadyDrawn()) return;
                SlotHighlight.draw(gfx, container,
                        access.chestindex$leftPos(), access.chestindex$topPos());
            });

            ScreenKeyboardEvents.allowKeyPress(screen).register((ignored, keyEvent) -> {
                if (!searchHovered.matches(keyEvent)) return true;
                trigger(container);
                return false;
            });
        });
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (!(ClientCompat.currentScreen() instanceof AbstractContainerScreen<?>)) button = null;
        dragButton(client);
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

        if (down && !rightWasDown && target.isMouseOver(mouseX, mouseY)) {
            draggingButton = true;
            target.setDragging(true);
        }
        if (draggingButton && down) {
            target.setDragging(true);
            target.setX(mouseX - SearchButton.SIZE / 2);
            target.setY(mouseY - SearchButton.SIZE / 2);
        }
        if (draggingButton && !down) {
            draggingButton = false;
            ChestIndexConfig config = ChestIndexConfig.get();
            config.setSearchButtonOffset(target.menuKey(),
                    target.getX() - target.anchorX(),
                    target.getY() - target.anchorY());
            config.save();
            target.setDragging(false);
        }
        rightWasDown = down;
    }

    private static String menuKeyOf(AbstractContainerScreen<?> container) {
        try {
            var type = container.getMenu().getType();
            if (type != null) {
                var id = net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(type);
                if (id != null) return id.toString();
            }
        } catch (RuntimeException noType) {
        }
        return container.getClass().getName();
    }

    private static void trigger(AbstractContainerScreen<?> container) {
        long now = System.currentTimeMillis();
        if (now - lastTrigger < DEBOUNCE_MS) return;
        lastTrigger = now;

        Slot hovered = ((ContainerScreenAccessor) container).chestindex$hoveredSlot();
        if (hovered == null || !hovered.hasItem()) {
            ContainerSearch.sayNothingHovered();
            return;
        }

        if (ContainerSearch.findAndGuide(hovered.getItem())) {
            container.onClose();
        }
    }
}
