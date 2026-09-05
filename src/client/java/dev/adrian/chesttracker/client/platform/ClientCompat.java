package dev.adrian.chesttracker.client.platform;

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
}
