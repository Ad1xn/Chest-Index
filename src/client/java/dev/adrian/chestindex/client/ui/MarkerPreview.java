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

public final class MarkerPreview {

    private static final String[] CONTAINERS = {
            "minecraft:chest",
            "minecraft:barrel",
            "minecraft:shulker_box",
            "minecraft:hopper",
            "minecraft:furnace",
            "minecraft:chest_minecart",
            "minecraft:oak_chest_boat",
    };

    private static final String MARKED_ITEM = "minecraft:diamond";

    private static final String NESTED_CONTAINER = "minecraft:shulker_box";

    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    public static final int HEIGHT = 80;

    private static final int SCENE_W = 78;

    private static final float NEAR_SCALE = 2.75f;

    private static final float FAR_SCALE = 1.5f;

    private static final int SLOT = 18;

    private final ChestIndexConfig config = ChestIndexConfig.get();

    private int container;

    private int sceneX;
    private int sceneY;
    private int sceneW;
    private int sceneH;

    public boolean click(double mouseX, double mouseY) {
        if (mouseX < sceneX || mouseX >= sceneX + sceneW) return false;
        if (mouseY < sceneY || mouseY >= sceneY + sceneH) return false;
        container = (container + 1) % CONTAINERS.length;
        return true;
    }

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

    private void drawScene(Gfx gfx, int x, int y, int width, int height) {
        int near = 0xFF000000 | config.nearestColour;
        int far = 0xFF000000 | config.otherColour;

        int block = Math.round(16 * NEAR_SCALE);
        int small = Math.round(16 * FAR_SCALE);

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

    private void drawTrail(Gfx gfx, int centreX, int top, int bottom, int colour) {
        for (int markY = bottom - 4; markY > top; markY -= 5) {
            int half = markY > bottom - 14 ? 2 : 1;
            gfx.fill(centreX - half, markY, centreX + half, markY + 2, colour);
        }
    }

    private void drawSlots(Gfx gfx, Font font, int x, int y, int width, int height) {
        if (width < SLOT * 2 + 8) return;

        ChestIndexConfig.SlotStyle style = config.slotHighlightStyle();
        int direct = 0xFF000000 | config.otherColour;
        int inside = 0xFF000000 | config.nearestColour;

        int rowY = y + (height - SLOT) / 2;
        int firstX = x + 2;

        slot(gfx, font, firstX, rowY, stack(MARKED_ITEM), style, direct);
        slot(gfx, font, firstX + SLOT + 4, rowY, stack(NESTED_CONTAINER), style, inside);

        slot(gfx, font, firstX + (SLOT + 4) * 2, rowY, stack("minecraft:redstone"), style, 0);
    }

    private void slot(Gfx gfx, Font font, int x, int y, ItemStack stack,
                      ChestIndexConfig.SlotStyle style, int colour) {
        Panel.well(gfx, x, y, SLOT, SLOT);

        int itemX = x + 1;
        int itemY = y + 1;

        if (colour == 0) {
            gfx.item(stack, itemX, itemY);
            return;
        }

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
        if (item == null) return ItemStack.EMPTY;

        ItemStack resolved = new ItemStack(item);
        STACKS.put(itemId, resolved);
        return resolved;
    }
}
