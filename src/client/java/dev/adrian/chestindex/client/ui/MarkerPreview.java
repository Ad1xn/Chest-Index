package dev.adrian.chestindex.client.ui;

import dev.adrian.chestindex.client.platform.Gfx;
import dev.adrian.chestindex.config.ChestIndexConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * What the markers look like, shown rather than described.
 *
 * <p>Every setting under Markers changes something that is only ever seen out
 * in the world or inside an open container - a colour, how solid a box is, what
 * shape it takes, whether a slot is washed or outlined. Reading a sentence
 * about it and then closing the settings, walking somewhere and searching for
 * something in order to find out is a slow way to answer "is this the colour I
 * meant". So the answer is on the same screen as the question, and it changes
 * as the rows under it are clicked.
 *
 * <h2>Two boxes, because a marker means two things</h2>
 *
 * <p>Left is the view from outside: the nearest match in the colour that picks
 * it out, and a further one in the resting colour, with the trail standing on
 * it when trails are on. The pair is the point - one colour alone says nothing
 * about whether the two can be told apart, which is the only question the
 * colour settings are really asking.
 *
 * <p>Right is the view from inside, once the walk is over and the container is
 * open: the item marked where it lies, a shulker box marked in the other colour
 * because it holds the answer rather than being it, and one unmarked slot so
 * the first two have something to be unlike.
 *
 * <h2>Drawn flat, at the size the game draws these things</h2>
 *
 * <p>Everything here is at the sixteen pixels an item icon is, inset into the
 * panel the way vanilla insets a slot. Nothing is scaled up: a blown-up block
 * model is a picture of a chest rather than a picture of the marker, and the
 * marker is what is being chosen. Keeping to slot size also means the right-
 * hand box <em>is</em> a row of container slots rather than a drawing of one.
 *
 * <p>Clicking either box walks through the container types, entity ones
 * included. A minecart with a chest is found and marked like anything else, and
 * it is the case somebody is most likely to doubt.
 */
public final class MarkerPreview {

    /**
     * What the boxes show, in the order clicking walks through them.
     *
     * <p>Chest first because it is what everyone pictures. Then a barrel and a
     * shulker box, the other two that hold a base's worth of things; then the
     * small ones; then the two that move, which are the ones worth proving.
     */
    private static final String[] CONTAINERS = {
            "minecraft:chest",
            "minecraft:barrel",
            "minecraft:shulker_box",
            "minecraft:hopper",
            "minecraft:furnace",
            "minecraft:chest_minecart",
            "minecraft:oak_chest_boat",
    };

    /** What the marked slot holds. Any item would do; a recognisable one reads faster. */
    private static final String MARKED_ITEM = "minecraft:diamond";

    private static final String NESTED_CONTAINER = "minecraft:shulker_box";

    /** The slot that is deliberately not marked. */
    private static final String PLAIN_ITEM = "minecraft:redstone";

    /** Registry lookups are not free and this redraws every frame. */
    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    /** Three rows of the settings well: a caption, and a box deep enough for a trail. */
    public static final int HEIGHT = 60;

    private static final int CAPTION_H = 10;
    private static final int BOX_H = 44;

    private static final int WORLD_W = 118;
    private static final int SLOT = 18;
    private static final int SLOT_GAP = 4;
    private static final int INSIDE_W = SLOT * 3 + SLOT_GAP * 2 + 8;

    private static final int GAP = 4;

    private final ChestIndexConfig config = ChestIndexConfig.get();

    private int container;

    /** The two boxes, written while drawing and read when clicked. */
    private int hitX;
    private int hitY;
    private int hitW;
    private int hitH;

    /** Advances the container shown. */
    public boolean click(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return false;
        container = (container + 1) % CONTAINERS.length;
        return true;
    }

    /** One line, shown while the preview is hovered. */
    public String hint() {
        return "Click to see another container";
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= hitX && mouseX < hitX + hitW
                && mouseY >= hitY && mouseY < hitY + hitH;
    }

    public void draw(Gfx gfx, Font font, int x, int y, int width, int height) {
        int total = WORLD_W + GAP + INSIDE_W;
        int left = x + Math.max(0, (width - total) / 2);
        int top = y + Math.max(0, (height - CAPTION_H - BOX_H) / 2);

        hitX = left;
        hitY = top;
        hitW = Math.min(total, width);
        hitH = CAPTION_H + BOX_H;

        int boxY = top + CAPTION_H;

        gfx.text(font, "In the world", left + 1, top, Panel.TEXT_MUTED);
        Panel.well(gfx, left, boxY, WORLD_W, BOX_H);
        drawWorld(gfx, left, boxY, WORLD_W, BOX_H);

        int insideX = left + WORLD_W + GAP;
        gfx.text(font, "Once it is open", insideX + 1, top, Panel.TEXT_MUTED);
        Panel.well(gfx, insideX, boxY, INSIDE_W, BOX_H);
        drawInside(gfx, insideX, boxY, INSIDE_W, BOX_H);
    }

    // --- the view from outside ----------------------------------------------

    private void drawWorld(Gfx gfx, int x, int y, int width, int height) {
        int near = 0xFF000000 | config.nearestColour;
        int far = 0xFF000000 | config.otherColour;

        ItemStack shown = stack(CONTAINERS[container]);

        // Both stand on the same line, so the only difference between them is
        // the colour - which is the comparison the pair exists to make.
        int standY = y + height - 16 - 8;

        int farX = x + width - 16 - 24;
        if (config.guideBeam) drawTrail(gfx, farX + 8, y + 5, standY - 2, far);
        gfx.item(shown, farX, standY);
        marker(gfx, farX, standY, far);

        int nearX = x + 24;
        gfx.item(shown, nearX, standY);
        marker(gfx, nearX, standY, near);
    }

    /**
     * A marker, in whichever of its shapes is chosen.
     *
     * <p>The cube is drawn under the outline rather than over it, the same
     * order the world renderer uses: a translucent face over its own edges
     * dulls them, and the edges are what says which block it is.
     */
    private void marker(Gfx gfx, int x, int y, int colour) {
        ChestIndexConfig.Shape shape = config.highlightShape();

        if (shape != ChestIndexConfig.Shape.OUTLINE) {
            int alpha = Math.round(config.cubeAlpha() * 255) << 24;
            gfx.fill(x - 1, y - 1, x + 17, y + 17, alpha | (colour & 0xFFFFFF));
        }
        if (shape != ChestIndexConfig.Shape.CUBE) {
            gfx.fill(x - 2, y - 2, x + 18, y - 1, colour);
            gfx.fill(x - 2, y + 17, x + 18, y + 18, colour);
            gfx.fill(x - 2, y - 1, x - 1, y + 17, colour);
            gfx.fill(x + 17, y - 1, x + 18, y + 17, colour);
        }
    }

    /**
     * The column of marks that stands on a match too far off to box.
     *
     * <p>Drawn as the marks it is rather than a solid line, and thinning as it
     * rises, the way it does in the world. This is the one setting whose effect
     * nobody can see without walking far enough away that the container itself
     * has stopped being drawn.
     */
    private void drawTrail(Gfx gfx, int centreX, int top, int bottom, int colour) {
        for (int markY = bottom - 3; markY > top; markY -= 5) {
            int half = markY > bottom - 12 ? 2 : 1;
            gfx.fill(centreX - half, markY, centreX + half, markY + 2, colour);
        }
    }

    // --- the view from inside -----------------------------------------------

    private void drawInside(Gfx gfx, int x, int y, int width, int height) {
        ChestIndexConfig.SlotStyle style = config.slotHighlightStyle();

        // The resting colours, as a container that has been open a moment
        // shows them: the item itself settled on the second colour, whatever
        // holds it steady on the first. See SlotHighlight, which this has to
        // keep agreeing with.
        int direct = 0xFF000000 | config.otherColour;
        int inside = 0xFF000000 | config.nearestColour;

        int slotY = y + (height - SLOT) / 2;
        int first = x + (width - (SLOT * 3 + SLOT_GAP * 2)) / 2;

        slot(gfx, first, slotY, stack(MARKED_ITEM), style, direct);
        slot(gfx, first + SLOT + SLOT_GAP, slotY, stack(NESTED_CONTAINER), style, inside);
        slot(gfx, first + (SLOT + SLOT_GAP) * 2, slotY, stack(PLAIN_ITEM), style, 0);
    }

    private void slot(Gfx gfx, int x, int y, ItemStack stack,
                      ChestIndexConfig.SlotStyle style, int colour) {
        Panel.groove(gfx, x, y, SLOT, SLOT);

        int itemX = x + 1;
        int itemY = y + 1;

        if (colour == 0) {
            gfx.item(stack, itemX, itemY);
            return;
        }

        // The wash goes under the item the same way it does in a real
        // container - painted first, then the item put back over it.
        if (style.drawsBackground()) {
            gfx.fill(itemX, itemY, itemX + 16, itemY + 16, (colour & 0xFFFFFF) | 0x60000000);
        }
        gfx.item(stack, itemX, itemY);
        if (style.drawsOutline()) {
            gfx.fill(itemX - 1, itemY - 1, itemX + 17, itemY, colour);
            gfx.fill(itemX - 1, itemY + 16, itemX + 17, itemY + 17, colour);
            gfx.fill(itemX - 1, itemY, itemX, itemY + 16, colour);
            gfx.fill(itemX + 16, itemY, itemX + 17, itemY + 16, colour);
        }
    }

    private static ItemStack stack(String itemId) {
        ItemStack cached = STACKS.get(itemId);
        if (cached != null) return cached;

        Identifier identifier = Identifier.tryParse(itemId);
        Item item = identifier == null ? null : BuiltInRegistries.ITEM.getValue(identifier);
        // A miss is never cached, for the reason SettingsScreen gives: a lookup
        // that failed once during a reload would otherwise stay failed.
        if (item == null) return ItemStack.EMPTY;

        ItemStack resolved = new ItemStack(item);
        STACKS.put(itemId, resolved);
        return resolved;
    }
}
