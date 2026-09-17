package dev.adrian.chestindex.client.platform;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Client-side calls that differ between versions, in one place.
 *
 * <p>Most of the client API turned out to be stable across the 26.x rework -
 * {@code setScreenAndShow}, {@code EditBox}, {@code KeyMapping.Category} and
 * the new {@code mouseClicked(MouseButtonEvent, boolean)} signature are all
 * identical on both. Only these two genuinely moved.
 */
public final class ClientCompat {

    private ClientCompat() {}

    /**
     * Shows a message above the hotbar.
     *
     * <p>26.x split the HUD out of {@code Gui} into its own {@code Hud} class,
     * so the overlay message moved one level down.
     */
    public static void actionBar(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui == null) return;
        //? if >=26.1 {
        /*client.gui.hud.setOverlayMessage(message, false);
        *///?} else {
        client.gui.setOverlayMessage(message, false);
        //?}
    }

    /**
     * A toast in the corner, the way an advancement announces itself.
     *
     * <p>For the one thing that has to be said when the mod is deliberately
     * doing nothing. An action-bar line would be an error message, and this is
     * not an error - it is the mod reporting that it is switched off here,
     * which is information the player wants once and never again.
     *
     * <p>26.x moved the toast manager off {@code Minecraft} onto the
     * {@code Gui}, along with the rest of the HUD. {@code SystemToast.add} is
     * identical on both.
     */
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

    /** Fabric API renamed its key-binding module to key-mapping for 26.x. */
    public static KeyMapping registerKeyMapping(KeyMapping mapping) {
        //? if >=26.1 {
        /*return net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(mapping);
        *///?} else {
        return net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(mapping);
        //?}
    }

    /**
     * The screen currently open, or null.
     *
     * <p>26.x moved the screen off {@code Minecraft} entirely - it lives on the
     * {@code Gui} now, and there is no accessor left on {@code Minecraft} at
     * all, where 1.21.11 still has it as a public field.
     *
     * <p>Wanted so that watching for an in-container key press does not have to
     * be told which screen is open by an event that may never arrive.
     */
    public static Screen currentScreen() {
        Minecraft client = Minecraft.getInstance();
        //? if >=26.1 {
        /*return client.gui == null ? null : client.gui.screen();
        *///?} else {
        return client.screen;
        //?}
    }

    /** {@code setScreenAndShow} exists on both versions; {@code setScreen} does not. */
    public static void openScreen(Screen screen) {
        Minecraft.getInstance().setScreenAndShow(screen);
    }

    /**
     * Opens a screen of ours in place of a container window, telling the server
     * the container is closed first.
     *
     * <p>Replacing the screen is not the same as closing the container. The
     * close packet is sent by {@code LocalPlayer.closeContainer()}, which
     * vanilla calls from the container screen's own {@code onClose} - so
     * setting a different screen over the top leaves the server believing the
     * player is still standing in the chest. On a vanilla server that means the
     * chest stays open, its lid stays up, and nobody else can use it until
     * something else closes it.
     *
     * <p>Closing first also lets the menu's own removal run, which is what the
     * client-side index reads the contents from - so the chest is still
     * recorded on the way out.
     */
    public static void openScreenFromContainer(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        if (currentScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
                && client.player != null) {
            client.player.closeContainer();
        }
        client.setScreenAndShow(screen);
    }

    /** What {@link #afterScreenRender} hands back, once per frame. */
    public interface ScreenDraw {
        void draw(Gfx gfx, int mouseX, int mouseY);
    }

    /**
     * Draws over a screen this mod did not write, after everything else on it.
     *
     * <p>Another of the deferred-renderer renames: the event is
     * {@code afterRender} on 1.21.11 and {@code afterExtract} on 26.x. The
     * callbacks are the same shape - screen, graphics, mouse, tick - so only
     * the name and the graphics type differ, and {@link Gfx} already absorbs
     * the second.
     */
    public static void afterScreenRender(Screen screen, ScreenDraw draw) {
        //? if >=26.1 {
        /*net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register(
                (target, graphics, mouseX, mouseY, tick) -> draw.draw(new Gfx(graphics), mouseX, mouseY));
        *///?} else {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register(
                (target, graphics, mouseX, mouseY, tick) -> draw.draw(new Gfx(graphics), mouseX, mouseY));
        //?}
    }

    /**
     * Adds a widget to a screen this mod did not write.
     *
     * <p>Fabric renamed this accessor for 26.x along with the rest of its screen
     * module. The list it hands back is live - what goes into it is rendered and
     * receives input - which is why this is the supported way onto somebody
     * else's screen rather than a mixin of our own.
     */
    public static void addWidget(Screen screen, AbstractWidget widget) {
        //? if >=26.1 {
        /*net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(widget);
        *///?} else {
        net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(widget);
        //?}
    }

    /**
     * The widgets already on a screen this mod did not write - including the
     * ones other mods put there.
     *
     * <p>The same live list {@link #addWidget} appends to, read rather than
     * written. It is how this mod can tell that somebody else has claimed a
     * piece of a screen before drawing into it.
     */
    public static java.util.List<AbstractWidget> widgets(Screen screen) {
        //? if >=26.1 {
        /*return net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen);
        *///?} else {
        return net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen);
        //?}
    }
}
