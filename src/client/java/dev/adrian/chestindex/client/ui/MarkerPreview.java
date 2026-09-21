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

    private static final String PLAIN_ITEM = "minecraft:redstone";

    private static final Map<String, ItemStack> STACKS = new HashMap<>();

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

    private int hitX;
    private int hitY;
    private int hitW;
    private int hitH;

    public boolean click(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return false;
        container = (container + 1) % CONTAINERS.length;
        return true;
    }

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

    private void drawWorld(Gfx gfx, int x, int y, int width, int height) {
        int near = 0xFF000000 | config.nearestColour;
        int far = 0xFF000000 | config.otherColour;

        ItemStack shown = stack(CONTAINERS[container]);

        int standY = y + height - 16 - 8;

        int farX = x + width - 16 - 24;
        if (config.guideBeam) drawTrail(gfx, farX + 8, y + 5, standY - 2, far);
        gfx.item(shown, farX, standY);
        marker(gfx, farX, standY, far);

        int nearX = x + 24;
        gfx.item(shown, nearX, standY);
        marker(gfx, nearX, standY, near);
    }

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

    private void drawTrail(Gfx gfx, int centreX, int top, int bottom, int colour) {
        for (int markY = bottom - 3; markY > top; markY -= 5) {
            int half = markY > bottom - 12 ? 2 : 1;
            gfx.fill(centreX - half, markY, centreX + half, markY + 2, colour);
        }
    }

    private void drawInside(Gfx gfx, int x, int y, int width, int height) {
        ChestIndexConfig.SlotStyle style = config.slotHighlightStyle();

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

        try {
            ItemStack resolved = new ItemStack(item);
            STACKS.put(itemId, resolved);
            return resolved;
        } catch (RuntimeException notBoundYet) {
            return ItemStack.EMPTY;
        }
    }
}
