package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.client.platform.ClientCompat;
import dev.adrian.chesttracker.client.platform.Gfx;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * The little magnifier that sits on a container window.
 *
 * <p>Opening a chest is the moment a player wonders where the rest of their
 * stuff is, so the search is offered there rather than only behind a key they
 * have to remember.
 *
 * <p>Right-drag moves it and the new place is saved. That is not decoration:
 * the top-right corner is exactly where several popular mods put their own
 * buttons, and a fixed position would mean this one sits under somebody else's
 * for the players who have both. Left-click opens, right-drag moves - split by
 * button rather than by whether the mouse travelled far enough, because a
 * gesture that sometimes opens a window and sometimes does not is worse than
 * one the player has to learn.
 *
 * <p>The drag itself is driven from {@code ContainerScreens} by polling the
 * mouse, not by this widget's drag callbacks. A container screen handles
 * dragging for its own quick-craft and never forwards a right-drag on to its
 * widgets, so those callbacks are simply never delivered - which is exactly how
 * it was reported.
 *
 * <p>Drawn with flat colours rather than sampled from a texture, unlike the
 * search screen: this button lands on a furnace, a hopper, a modded machine -
 * whatever screen the player opened - and there is no one texture to take the
 * pixels from. Vanilla's container palette is the same grey in every one of
 * them, so a fixed colour is the honest match here.
 */
public final class SearchButton extends AbstractWidget {

    public static final int SIZE = 11;

    // Vanilla's container palette, shared by every container GUI in the game.
    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int ICON = 0xFF404040;
    private static final int HOVER = 0x80FFFFFF;

    /**
     * The container window's top-right corner, asked for afresh every frame.
     *
     * <p>It used to be captured once, when the button was built during the
     * screen's {@code init}. That is wrong because a container window moves
     * without being re-initialised: opening the recipe book shifts the whole
     * GUI sideways, and the button stayed where the window used to be. The same
     * staleness would leave it misplaced after anything else that repositions a
     * screen in place.
     *
     * <p>Measuring from the top-right rather than the top-left is what lets one
     * saved offset serve every container. A hopper, a single chest and a double
     * chest have different widths but the same right-hand edge relative to
     * their own window, so the button lands in the same corner of each.
     */
    private final java.util.function.IntSupplier anchorX;
    private final java.util.function.IntSupplier anchorY;

    /**
     * Which kind of window this button is on - {@code minecraft:hopper},
     * {@code minecraft:generic_9x6} and so on.
     *
     * <p>The offset is stored against this rather than shared by every
     * container, because "the same corner of every window" turned out not to
     * be what anybody wants. A hopper is five slots wide and its top-right
     * corner sits directly over its only row; a double chest has an empty
     * title bar up there. Somewhere good on one is in the way on the other.
     */
    private final String menuKey;

    /**
     * True while the player is dragging it, when the pointer decides where it
     * is and the saved offset must not pull it back.
     */
    private boolean dragging;

    public SearchButton(String menuKey,
                        java.util.function.IntSupplier anchorX,
                        java.util.function.IntSupplier anchorY) {
        super(0, 0, SIZE, SIZE, Component.literal("Search containers"));
        this.menuKey = menuKey;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        follow();
        setTooltip(Tooltip.create(Component.literal(
                "Search containers  (right-drag to move it on this kind of window)")));
    }

    /** Puts the button back where the offset says, relative to where the window is now. */
    public void follow() {
        if (dragging) return;
        int[] offset = ChestTrackerConfig.get().searchButtonOffset(menuKey);
        setX(anchorX.getAsInt() + offset[0]);
        setY(anchorY.getAsInt() + offset[1]);
    }

    /** Which window's offset this button reads and writes. */
    public String menuKey() {
        return menuKey;
    }

    public void setDragging(boolean value) {
        dragging = value;
    }

    // --- drawing ------------------------------------------------------------

    private void draw(Gfx gfx, int mouseX, int mouseY) {
        // Re-anchored here rather than on a tick, so it moves in the same frame
        // the window does instead of lagging a twentieth of a second behind it.
        follow();

        int x = getX();
        int y = getY();

        gfx.fill(x, y, x + SIZE, y + SIZE, BEVEL_DARK);
        gfx.fill(x, y, x + SIZE - 1, y + SIZE - 1, PANEL);
        gfx.fill(x, y, x + SIZE - 1, y + 1, BEVEL_LIGHT);
        gfx.fill(x, y, x + 1, y + SIZE - 1, BEVEL_LIGHT);
        if (isMouseOver(mouseX, mouseY)) {
            gfx.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, HOVER);
        }
        drawMagnifier(gfx, x + 2, y + 2);
    }

    /**
     * An eight-pixel magnifying glass: a ring and a handle.
     *
     * <p>Drawn rather than blitted because there is no vanilla sprite for it
     * that is named the same on both supported versions, and a bundled texture
     * would be one more thing a resource pack could not touch.
     */
    private void drawMagnifier(Gfx gfx, int x, int y) {
        gfx.fill(x + 1, y, x + 4, y + 1, ICON);
        gfx.fill(x + 1, y + 4, x + 4, y + 5, ICON);
        gfx.fill(x, y + 1, x + 1, y + 4, ICON);
        gfx.fill(x + 4, y + 1, x + 5, y + 4, ICON);
        gfx.fill(x + 4, y + 4, x + 6, y + 6, ICON);
        gfx.fill(x + 5, y + 5, x + 8, y + 8, ICON);
    }

    // --- input --------------------------------------------------------------

    /** Where the anchor is now, so the drag poll can turn a position into an offset. */
    public int anchorX() {
        return anchorX.getAsInt();
    }

    public int anchorY() {
        return anchorY.getAsInt();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!visible || !isMouseOver(event.x(), event.y())) return false;

        if (event.button() == 0) {
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            // Through the closing route: this button lives on somebody else's
            // container window, and swapping the screen without telling the
            // server would leave that container open at the far end.
            ClientCompat.openScreenFromContainer(new ChestTrackerScreen());
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    // The one signature the Gfx facade cannot hide: 26.x renamed the widget's
    // render entry point and changed its parameter type as part of the
    // deferred-rendering rework.
    //? if >=26.1 {
    /*@Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        draw(new Gfx(graphics), mouseX, mouseY);
    }
    *///?} else {
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(new Gfx(graphics), mouseX, mouseY);
    }
    //?}
}
