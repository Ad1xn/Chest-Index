package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.client.platform.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A button drawn from vanilla's own button texture.
 *
 * <p>For the places this mod puts a button on somebody else's screen, where a
 * real {@code Button} widget cannot be used because that screen does not render
 * vanilla's widget list. Drawing the game's texture rather than approximating
 * it with fills means a resource pack that restyles buttons restyles these too,
 * which is the whole point: on a modded screen full of borrowed furniture, the
 * one button that is painted by hand is the one that looks wrong.
 *
 * <h2>How the texture is used</h2>
 *
 * <p>{@code widget/button.png} is 200x20 and its metadata declares a nine-slice
 * with a three pixel border, so the corners must not be scaled and the middle
 * must not be tiled visibly. Rather than reach for a scaling blit - whose
 * signature differs between the two targets - the button is drawn as four
 * quadrants taken from the four corners of the texture. Each quadrant is a 1:1
 * copy, so the three pixel border survives on all four sides at any size from
 * 6x6 up to the texture's own 200x20, which covers every button here.
 *
 * <p>The middle of a small button is a flat colour in vanilla's art, so taking
 * it from the corners loses nothing.
 */
public final class VanillaButton {

    private VanillaButton() {}

    private static final Identifier BUTTON =
            Identifier.parse("minecraft:textures/gui/sprites/widget/button.png");
    private static final Identifier BUTTON_HIGHLIGHTED =
            Identifier.parse("minecraft:textures/gui/sprites/widget/button_highlighted.png");

    private static final int SHEET_W = 200;
    private static final int SHEET_H = 20;

    /** Vanilla's own button text colours: white, and grey when it cannot be pressed. */
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DISABLED = 0xFFA0A0A0;

    /** The icon colour on this mod's icon buttons, matching the label's weight. */
    public static final int ICON = 0xFFE0E0E0;

    public static void draw(Gfx gfx, int x, int y, int width, int height, boolean hovered) {
        if (width < 2 || height < 2) return;

        Identifier texture = hovered ? BUTTON_HIGHLIGHTED : BUTTON;

        // Split as evenly as the size allows, and clamped so a button wider or
        // taller than the texture repeats its middle rather than reading past
        // the edge - which would sample whatever is next to it in the atlas.
        int leftW = Math.min(width / 2, SHEET_W / 2);
        int rightW = Math.min(width - leftW, SHEET_W - leftW);
        int topH = Math.min(height / 2, SHEET_H / 2);
        int bottomH = Math.min(height - topH, SHEET_H - topH);

        gfx.blit(texture, x, y, 0, 0, leftW, topH, SHEET_W, SHEET_H);
        gfx.blit(texture, x + width - rightW, y, SHEET_W - rightW, 0, rightW, topH, SHEET_W, SHEET_H);
        gfx.blit(texture, x, y + height - bottomH, 0, SHEET_H - bottomH, leftW, bottomH, SHEET_W, SHEET_H);
        gfx.blit(texture, x + width - rightW, y + height - bottomH,
                SHEET_W - rightW, SHEET_H - bottomH, rightW, bottomH, SHEET_W, SHEET_H);
    }

    /** As above, with a label centred in it the way vanilla centres its own. */
    public static void draw(Gfx gfx, int x, int y, int width, int height,
                            boolean hovered, String label) {
        draw(gfx, x, y, width, height, hovered);

        Minecraft client = Minecraft.getInstance();
        int textX = x + (width - client.font.width(label)) / 2;
        // Vanilla centres on the font's nine pixel line box, not on eight.
        int textY = y + (height - 8) / 2;
        gfx.text(client.font, Component.literal(label), textX, textY, TEXT);
    }

    /** Width a labelled button needs, with vanilla's own padding either side. */
    public static int widthFor(String label) {
        return Minecraft.getInstance().font.width(label) + 10;
    }

    /**
     * The eight-pixel magnifying glass, the same one the container button uses.
     *
     * <p>Drawn rather than blitted because the game has no magnifier sprite
     * that is named the same on both supported versions.
     */
    public static void magnifier(Gfx gfx, int x, int y, int colour) {
        gfx.fill(x + 1, y, x + 4, y + 1, colour);
        gfx.fill(x + 1, y + 4, x + 4, y + 5, colour);
        gfx.fill(x, y + 1, x + 1, y + 4, colour);
        gfx.fill(x + 4, y + 1, x + 5, y + 4, colour);
        gfx.fill(x + 4, y + 4, x + 6, y + 6, colour);
        gfx.fill(x + 5, y + 5, x + 8, y + 8, colour);
    }

    /** Vanilla's button click, so a borrowed button sounds like the ones beside it. */
    public static void playClick() {
        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
