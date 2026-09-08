package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.client.ClientTracker;
import dev.adrian.chesttracker.client.ContainerSearch;
import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import dev.adrian.chesttracker.client.ActionBar;
import dev.adrian.chesttracker.client.platform.Gfx;
import dev.adrian.chesttracker.core.net.QueryDto;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * A schematic's materials, one clickable row each.
 *
 * <p>"Where is everything this schematic needs" is the right question at the
 * start of a build and the wrong one in the middle of it. Halfway through, the
 * question is about one material - you have run out of stone brick stairs and
 * want to know where the rest of them are, not where the other forty materials
 * are - and a highlight covering all forty answers it by burying it.
 *
 * <p>So this is the material list as a list of searches. It reads the entries
 * Litematica already handed over, and clicking one runs exactly the search the
 * key over a stack would: highlight every container holding it, guide to the
 * nearest, and mark the slot once it is open.
 *
 * <p>Deliberately its own screen rather than a mode of the search grid. The
 * grid is "everything you own, filtered"; this is "a fixed list somebody else
 * wrote", and the two have different empty states, different sorting and
 * different questions behind them.
 */
public final class MaterialPickerScreen extends Screen {

    private static final int ROW_H = 18;
    private static final int WIDTH = 220;
    private static final int TOP = 34;
    private static final int BOTTOM_RESERVED = 34;

    private static final int BG = 0xC0101010;
    private static final int ROW_HOVER = 0x50FFFFFF;
    private static final int TEXT_MAIN = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFAAAAAA;
    private static final int BORDER = 0xFF909090;

    /** Matches the other searches; the highlight caps its boxes anyway. */
    private static final int MAX_CONTAINERS = 64;

    private record Material(String itemId, int count, ItemStack icon, String name) {}

    private final List<Material> materials = new ArrayList<>();

    private int scroll;

    public MaterialPickerScreen(Map<String, Integer> needed) {
        super(Component.literal("Schematic materials"));
        needed.forEach((itemId, count) -> {
            ItemStack icon = iconFor(itemId);
            materials.add(new Material(itemId, count, icon,
                    icon.isEmpty() ? shortName(itemId) : icon.getHoverName().getString()));
        });
    }

    private int listX() {
        return (width - WIDTH) / 2;
    }

    private int visibleRows() {
        return Math.max(1, (height - TOP - BOTTOM_RESERVED) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, materials.size() - visibleRows());
    }

    /** The row under the cursor, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < listX() || mouseX >= listX() + WIDTH) return -1;
        int offset = (int) mouseY - TOP;
        if (offset < 0) return -1;
        int row = offset / ROW_H;
        if (row >= visibleRows()) return -1;
        int index = scroll + row;
        return index < materials.size() ? index : -1;
    }

    private void draw(Gfx gfx, int mouseX, int mouseY) {
        String title = "Schematic materials";
        gfx.text(font, Component.literal(title), width / 2 - font.width(title) / 2, 14, TEXT_MAIN);

        int x = listX();
        int rows = Math.min(visibleRows(), materials.size() - scroll);
        int listHeight = Math.max(ROW_H, rows * ROW_H);

        gfx.fill(x - 1, TOP - 1, x + WIDTH + 1, TOP + listHeight + 1, BORDER);
        gfx.fill(x, TOP, x + WIDTH, TOP + listHeight, BG);

        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < rows; row++) {
            int index = scroll + row;
            Material material = materials.get(index);
            int y = TOP + row * ROW_H;

            if (index == hovered) gfx.fill(x, y, x + WIDTH, y + ROW_H, ROW_HOVER);
            gfx.item(material.icon(), x + 2, y + 1);

            String count = String.format("%,d", material.count());
            int countWidth = font.width(count);
            gfx.text(font, Component.literal(count), x + WIDTH - 4 - countWidth, y + 5, TEXT_MUTED);
            gfx.text(font, Component.literal(
                            truncate(material.name(), WIDTH - 28 - countWidth)),
                    x + 22, y + 5, TEXT_MAIN);
        }

        String hint = materials.isEmpty()
                ? "Nothing in the material list."
                : "Click a material to be shown where it is.";
        gfx.text(font, Component.literal(hint),
                width / 2 - font.width(hint) / 2, height - 22, TEXT_MUTED);
    }

    /** Trims text to a pixel width, with an ellipsis when it does not fit. */
    private String truncate(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        StringBuilder trimmed = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.width(trimmed.toString() + c + "...") > maxWidth) break;
            trimmed.append(c);
        }
        return trimmed + "...";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int index = rowAt(event.x(), event.y());
            if (index >= 0) {
                search(materials.get(index));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(deltaY)));
        return true;
    }

    /**
     * The same search the key over a stack runs, for one material.
     *
     * <p>Closes first and highlights when the answer arrives, as every other
     * route into guidance does: the player has said where they want to go, and
     * holding a window open over the world while a server replies reads as a
     * stall.
     */
    private void search(Material material) {
        if (minecraft == null || minecraft.player == null) return;
        if (!ClientTracker.isAvailable()) {
            say("No index here yet.", ChatFormatting.RED);
            onClose();
            return;
        }

        String dimensionId = minecraft.player.level().dimension().identifier().toString();
        String label = material.name();
        List<String> wanted = List.of(material.itemId());

        ClientTracker.containers(wanted, ContainerSearch.searchFilters(), MAX_CONTAINERS, "")
                .thenAccept(response -> minecraft.execute(() -> {
                    if (response.hits().isEmpty()) {
                        say("Nothing indexed holds " + label, ChatFormatting.YELLOW);
                        return;
                    }
                    ContainerHighlight.get().selectHits(response.hits(), dimensionId, label);
                    ContainerHighlight.get().searchingFor(wanted);
                }));
        onClose();
    }

    /**
     * Back to the world rather than to the material list.
     *
     * <p>The answer to what was asked here is in the world, so returning to the
     * schematic window would put the thing the player just asked about behind
     * the window they asked from - the same reason clicking a search result
     * closes the search screen.
     */
    @Override
    public void onClose() {
        // Vanilla's own close, which is the route every other screen here takes
        // back to the world.
        super.onClose();
    }

    private static void say(String message, ChatFormatting colour) {
        ActionBar.say(Component.literal(message).withStyle(colour));
    }

    private static ItemStack iconFor(String itemId) {
        Identifier identifier = Identifier.tryParse(itemId);
        if (identifier == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String shortName(String registryId) {
        int colon = registryId.indexOf(':');
        return colon < 0 ? registryId : registryId.substring(colon + 1);
    }

    //? if >=26.1 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        draw(new Gfx(graphics), mouseX, mouseY);
    }
    *///?} else {
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        draw(new Gfx(graphics), mouseX, mouseY);
    }
    //?}
}
