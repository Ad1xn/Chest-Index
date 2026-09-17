package dev.adrian.chestindex.client.platform;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ClientCompat {

    private ClientCompat() {}

    public static void actionBar(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui == null) return;
        //? if >=26.1 {
        /*client.gui.hud.setOverlayMessage(message, false);
        *///?} else {
        client.gui.setOverlayMessage(message, false);
        //?}
    }

    public static void toast(Component title, Component message) {
        Minecraft client = Minecraft.getInstance();
        //? if >=26.1 {
        /*if (client.gui == null) return;
        net.minecraft.client.gui.components.toasts.SystemToast.add(
                client.gui.toastManager(),
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                title, message);
        *///?} else {
        net.minecraft.client.gui.components.toasts.SystemToast.add(
                client.getToastManager(),
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                title, message);
        //?}
    }

    public static KeyMapping registerKeyMapping(KeyMapping mapping) {
        //? if >=26.1 {
        /*return net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(mapping);
        *///?} else {
        return net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(mapping);
        //?}
    }

    public static Screen currentScreen() {
        Minecraft client = Minecraft.getInstance();
        //? if >=26.1 {
        /*return client.gui == null ? null : client.gui.screen();
        *///?} else {
        return client.screen;
        //?}
    }

    public static void openScreen(Screen screen) {
        Minecraft.getInstance().setScreenAndShow(screen);
    }

    public static void openScreenFromContainer(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        if (currentScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
                && client.player != null) {
            client.player.closeContainer();
        }
        client.setScreenAndShow(screen);
    }

    public interface ScreenDraw {
        void draw(Gfx gfx, int mouseX, int mouseY);
    }

    public static void afterScreenRender(Screen screen, ScreenDraw draw) {
        //? if >=26.1 {
        /*net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register(
                (target, graphics, mouseX, mouseY, tick) -> draw.draw(new Gfx(graphics), mouseX, mouseY));
        *///?} else {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register(
                (target, graphics, mouseX, mouseY, tick) -> draw.draw(new Gfx(graphics), mouseX, mouseY));
        //?}
    }

    public static void addWidget(Screen screen, AbstractWidget widget) {
        //? if >=26.1 {
        /*net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(widget);
        *///?} else {
        net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(widget);
        //?}
    }

    public static java.util.List<AbstractWidget> widgets(Screen screen) {
        //? if >=26.1 {
        /*return net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen);
        *///?} else {
        return net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen);
        //?}
    }
}
