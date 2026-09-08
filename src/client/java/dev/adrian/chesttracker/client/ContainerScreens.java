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

/**
 * What this mod adds to container screens that belong to somebody else.
 *
 * <p>Two ways in, both starting from a chest the player already has open,
 * because that is where the question "where is the rest of this?" gets asked:
 * a key over the stack, and a button on the window.
 *
 * <p>The button hangs off {@code AFTER_INIT} rather than a mixin on the screen
 * itself. A screen is re-initialised on every resize, so it is repositioned for
 * free, and it lands on modded containers - subclasses of the same class -
 * without knowing they exist.
 *
 * <h2>Why the key is read twice</h2>
 *
 * <p>A key pressed while a screen is open never reaches {@code consumeClick()}:
 * vanilla only queues those when no screen is up. The documented way in is
 * Fabric's per-screen keyboard events, and it is what the mod this one replaces
 * uses, so it is tried first.
 *
 * <p>It did not fire here. That path runs inside a MixinExtras wrapper around
 * the screen's {@code keyPressed}, so anything that replaces or short-circuits
 * that call takes the hook with it - and this profile carries several input
 * mods, two of them dedicated to rewriting macOS key handling. So the window is
 * also polled once a tick, which nothing sits in front of.
 *
 * <p>Both routes end in {@link #trigger}, which ignores a second call within
 * {@link #DEBOUNCE_MS} so having both cannot search twice for one press. It
 * logs which route won, because that is the only way to learn from here which
 * one this environment actually delivers.
 */
public final class ContainerScreens {

    private ContainerScreens() {}

    /** Short enough that pressing again works, long enough to swallow a repeat. */
    private static final long DEBOUNCE_MS = 300;

    private static KeyMapping searchKey;
    private static long lastTrigger;

    /** The button on the screen currently open, or null. */
    private static SearchButton button;

    private static boolean rightWasDown;
    private static boolean draggingButton;

    /** Logged once, to settle whether screen init events arrive here at all. */
    private static boolean reportedInit;

    public static void register(KeyMapping searchHovered) {
        searchKey = searchHovered;

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            // Checked here rather than once at startup: the switch is a
            // per-server answer, and screens are built after the join that
            // decides it.
            if (!Session.active()) {
                button = null;
                return;
            }

            // init() has already run, so the window's position is settled.
            ContainerScreenAccessor access = (ContainerScreenAccessor) container;

            button = null;
            if (ChestTrackerConfig.get().containerSearchButton) {
                // Suppliers, not values: the window moves without the screen
                // being re-initialised - opening the recipe book slides it
                // sideways - and a position captured here would be stale from
                // that moment on.
                button = new SearchButton(
                        menuKeyOf(container),
                        () -> access.chesttracker$leftPos() + access.chesttracker$imageWidth(),
                        access::chesttracker$topPos);
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

            // "allow" rather than "before", so a match is also swallowed. The
            // key reaches nothing else in the screen after this, which is what
            // stops a second binding on the same key firing behind it.
            ScreenKeyboardEvents.allowKeyPress(screen).register((ignored, keyEvent) -> {
                if (!searchHovered.matches(keyEvent)) return true;
                trigger(container);
                return false;
            });
        });
    }

    /** Per-tick work: only the button drag, which has no event to listen to. */
    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        // The button belongs to a container window, and is only replaced when
        // another one opens - so after a chest closed it stayed here, and the
        // drag poll below went on watching for a right-press over coordinates
        // nothing occupies any more. A right-click landing there on whatever
        // screen came next moved the chest's button and wrote the config: the
        // search screen's own "right-click to list" is directly over it.
        if (!(ClientCompat.currentScreen() instanceof AbstractContainerScreen<?>)) button = null;
        dragButton(client);
    }

    /**
     * Moves the search button while the right mouse button is held on it.
     *
     * <p>Polled, like the key, and for the same reason: a container screen
     * handles dragging for its own quick-craft and never forwards a right-drag
     * to its widgets, so the widget's own drag callbacks never arrive. Reading
     * the mouse asks nobody's permission.
     */
    private static void dragButton(Minecraft client) {
        SearchButton target = button;
        if (target == null || client.getWindow() == null) {
            rightWasDown = false;
            draggingButton = false;
            return;
        }

        boolean down = GLFW.glfwGetMouseButton(
                client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        // The mouse is in real pixels; widgets live in GUI-scaled ones.
        double scaleX = client.getWindow().getGuiScaledWidth()
                / (double) client.getWindow().getScreenWidth();
        double scaleY = client.getWindow().getGuiScaledHeight()
                / (double) client.getWindow().getScreenHeight();
        int mouseX = (int) (client.mouseHandler.xpos() * scaleX);
        int mouseY = (int) (client.mouseHandler.ypos() * scaleY);

        if (down && !rightWasDown && target.isMouseOver(mouseX, mouseY)) {
            draggingButton = true;
            // Tells the button to stop pulling itself back to the saved offset
            // while the pointer is deciding where it goes.
            target.setDragging(true);
        }
        if (draggingButton && down) {
            // Restated every tick rather than only on the press: a screen
            // re-initialised mid-drag builds a fresh button that has not been
            // told, and it would spend the rest of the drag snapping itself
            // back to the saved offset.
            target.setDragging(true);
            target.setX(mouseX - SearchButton.SIZE / 2);
            target.setY(mouseY - SearchButton.SIZE / 2);
        }
        if (draggingButton && !down) {
            draggingButton = false;
            // Written once on release rather than on every frame of the drag,
            // and against the anchor as it is now - so a button dropped while
            // the recipe book is open still lands where it was put once it
            // closes again.
            ChestTrackerConfig config = ChestTrackerConfig.get();
            // Against this kind of window rather than against all of them, so
            // moving the button on a hopper leaves the chest's where it was.
            config.setSearchButtonOffset(target.menuKey(),
                    target.getX() - target.anchorX(),
                    target.getY() - target.anchorY());
            config.save();
            target.setDragging(false);
        }
        rightWasDown = down;
    }

    /**
     * A stable name for the kind of window this is, so the button's position
     * can be remembered per container rather than for all of them at once.
     *
     * <p>The menu type is the right key: it is what decides the window's shape,
     * it is the same for every chest in the world, and a modded container has
     * one too. Two things can go wrong with it and both fall back to the class
     * name, which is stable enough for the same purpose - the player inventory
     * menu has no registered type and throws when asked, and a menu built
     * outside the registry has one that resolves to nothing.
     */
    private static String menuKeyOf(AbstractContainerScreen<?> container) {
        try {
            var type = container.getMenu().getType();
            if (type != null) {
                var id = net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(type);
                if (id != null) return id.toString();
            }
        } catch (RuntimeException noType) {
            // Falls through to the class name.
        }
        return container.getClass().getName();
    }

    /** Searches for whatever the cursor is over. */
    private static void trigger(AbstractContainerScreen<?> container) {
        long now = System.currentTimeMillis();
        if (now - lastTrigger < DEBOUNCE_MS) return;
        lastTrigger = now;

        // Requiring a hovered stack is also what keeps this from firing while
        // somebody types a Z into an anvil or the creative search: the cursor
        // cannot be over a slot and in a text field at once.
        Slot hovered = ((ContainerScreenAccessor) container).chesttracker$hoveredSlot();
        if (hovered == null || !hovered.hasItem()) {
            // Saying so rather than doing nothing. A silent no-op is
            // indistinguishable from the key not being bound, or from the mod
            // not being installed - which is exactly how this was first
            // reported.
            ContainerSearch.sayNothingHovered();
            return;
        }

        // Closing on a hit matches what clicking a row in the search screen
        // already does: the question is answered, and the player is about to
        // walk. Staying open would hide the guidance behind the very chest they
        // are leaving.
        if (ContainerSearch.findAndGuide(hovered.getItem())) {
            container.onClose();
        }
    }
}
