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
 * about it and then closing the settings, walking somewhere, and searching for
 * something in order to find out is a slow way to answer "is this the colour I
 * meant". So the answer is on the same screen as the question, and it changes
 * as the rows under it are clicked.
 *
 * <p>Three things are drawn, because a marker is three things:
 *
 * <ul>
 *   <li>the nearest match, boxed in the colour that picks it out;
 *   <li>a further match behind it in the resting colour, with the trail
 *       standing on it if trails are on - the pair is the point, since one
 *       colour alone says nothing about whether the two can be told apart;
 *   <li>the slots of a container you have walked to and opened: the item
 *       marked where it lies, and a shulker box marked in the other colour
 *       because it holds the answer rather than being it.
 * </ul>
 *
 * <h2>Why a block and not a picture of one</h2>
 *
 * <p>The three-dimensional chest is the game's own: the model it draws into an
 * item slot, scaled up through {@link Gfx#item(ItemStack, int, int, float)}.
 * That keeps the preview honest with the player's resource pack, and it is the
 * only route to a rendered block a screen has - the pose stack is
 * two-dimensional on both targets, so a real rotation is not available and is
 * not pretended at.
 *
 * <p>Clicking the scene walks through the container types, entity ones
 * included. A minecart with a chest is found and marked like anything else, and
 * it is the case somebody is most likely to doubt.
 */
public final class MarkerPreview {

    /**
     * What the scene can show, in the order clicking walks through them.
     *
     * <p>Chest first because it is what everyone pictures. Then a barrel and a
     * shulker box, which are the other two that hold a base's worth of things;
     * then the small ones, whose marker has to sit on a block that is not a
     * cube; then the two that move, which are the ones worth proving.
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

    /** What the marked slots hold. Any item would do; a recognisable one reads faster. */
    private static final String MARKED_ITEM = "minecraft:diamond";

    private static final String NESTED_CONTAINER = "minecraft:shulker_box";

    /** Registry lookups are not free and this redraws every frame. */
    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    /** Four rows of the settings well, which is what the scene needs to read at all. */
    public static final int HEIGHT = 80;

    private static final int SCENE_W = 78;

    /** The big block, in slots: sixteen pixels times this. */
    private static final float NEAR_SCALE = 2.75f;

    /** And the one standing behind it, smaller because it is further away. */
    private static final float FAR_SCALE = 1.5f;

    private static final int SLOT = 18;

    private final ChestIndexConfig config = ChestIndexConfig.get();

    private int container;

    /** The scene's bounds, written while drawing and read when clicked. */
    private int sceneX;
    private int sceneY;
    private int sceneW;
    private int sceneH;

    /** Advances the container shown. */
    public boolean click(double mouseX, double mouseY) {
        if (mouseX < sceneX || mouseX >= sceneX + sceneW) return false;
        if (mouseY < sceneY || mouseY >= sceneY + sceneH) return false;
        container = (container + 1) % CONTAINERS.length;
        return true;
    }

    /** One line, shown while the scene is hovered. */
    public String hint() {
        return "Click to see another container";
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= sceneX && mouseX < sceneX + sceneW
                && mouseY >= sceneY && mouseY < sceneY + sceneH;
    }

    public void draw(Gfx gfx, Font font, int x, int y, int width, int height) {
        sceneX = x;
        sceneY = y;
        sceneW = Math.min(SCENE_W, width);
        sceneH = height;

        Panel.well(gfx, sceneX, sceneY, sceneW, sceneH);
        drawScene(gfx, sceneX, sceneY, sceneW, sceneH);

        int slotsX = sceneX + sceneW + 4;
        drawSlots(gfx, font, slotsX, y, x + width - slotsX, height);
    }

    // --- the world, from outside --------------------------------------------

    private void drawScene(Gfx gfx, int x, int y, int width, int height) {
        int near = 0xFF000000 | config.nearestColour;
        int far = 0xFF000000 | config.otherColour;

        int block = Math.round(16 * NEAR_SCALE);
        int small = Math.round(16 * FAR_SCALE);

        // The far one first, so the near one stands in front of it the way
        // distance puts it there.
        int farX = x + width - small - 6;
        int farY = y + 8;
        if (config.guideBeam) drawTrail(gfx, farX + small / 2, y + 2, farY, far);
        gfx.item(stack(CONTAINERS[container]), farX, farY, FAR_SCALE);
        marker(gfx, farX, farY, small, far);

        int nearX = x + 6;
        int nearY = y + height - block - 6;
        gfx.item(stack(CONTAINERS[container]), nearX, nearY, NEAR_SCALE);
        marker(gfx, nearX, nearY, block, near);
    }

    /**
     * A marker, in whichever of its shapes is chosen.
     *
     * <p>The cube is drawn under the outline rather than over it, the same
     * order the world renderer uses: a translucent face over its own edges
     * dulls them, and the edges are what says which block it is.
     */
    private void marker(Gfx gfx, int x, int y, int size, int colour) {
        ChestIndexConfig.Shape shape = config.highlightShape();

        if (shape != ChestIndexConfig.Shape.OUTLINE) {
            int alpha = Math.round(config.cubeAlpha() * 255) << 24;
            gfx.fill(x, y, x + size, y + size, alpha | (colour & 0xFFFFFF));
        }
        if (shape != ChestIndexConfig.Shape.CUBE) {
            gfx.fill(x - 1, y - 1, x + size + 1, y, colour);
            gfx.fill(x - 1, y + size, x + size + 1, y + size + 1, colour);
            gfx.fill(x - 1, y, x, y + size, colour);
            gfx.fill(x + size, y, x + size + 1, y + size, colour);
        }
    }

    /**
     * The column of marks that stands on a match too far off to box.
     *
     * <p>Drawn as the marks it is rather than a solid line, and thinning as it
     * rises, because that is what it does in the world - this is the one
     * setting whose effect nobody can see without walking far enough away that
     * the container itself is gone.
     */
    private void drawTrail(Gfx gfx, int centreX, int top, int bottom, int colour) {
        for (int markY = bottom - 4; markY > top; markY -= 5) {
            int half = markY > bottom - 14 ? 2 : 1;
            gfx.fill(centreX - half, markY, centreX + half, markY + 2, colour);
        }
    }

    // --- the container, from inside -----------------------------------------

    /**
     * Two slots of an open container: the item where it lies, and a shulker box
     * holding one.
     *
     * <p>Both are here because they mean different things and are marked in
     * different colours - one is the answer, the other is the next thing to
     * open - and a preview that showed only the first would leave the second
     * to be discovered in a chest somewhere.
     */
    private void drawSlots(Gfx gfx, Font font, int x, int y, int width, int height) {
        if (width < SLOT * 2 + 8) return;

        ChestIndexConfig.SlotStyle style = config.slotHighlightStyle();
        int direct = 0xFF000000 | config.otherColour;
        int inside = 0xFF000000 | config.nearestColour;

        int rowY = y + (height - SLOT) / 2;
        int firstX = x + 2;

        slot(gfx, font, firstX, rowY, stack(MARKED_ITEM), style, direct);
        slot(gfx, font, firstX + SLOT + 4, rowY, stack(NESTED_CONTAINER), style, inside);

        // A third, unmarked, so the marked two have something to be unlike.
        slot(gfx, font, firstX + (SLOT + 4) * 2, rowY, stack("minecraft:redstone"), style, 0);
    }

    private void slot(Gfx gfx, Font font, int x, int y, ItemStack stack,
                      ChestIndexConfig.SlotStyle style, int colour) {
        Panel.well(gfx, x, y, SLOT, SLOT);

        int itemX = x + 1;
        int itemY = y + 1;

        // Unmarked: just the item, which is the whole point of drawing one.
        if (colour == 0) {
            gfx.item(stack, itemX, itemY);
            return;
        }

        // The wash goes under the item the same way it does in a real
        // container - painted first, then the item put back over it. See
        // SlotHighlight, which this has to keep agreeing with.
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
