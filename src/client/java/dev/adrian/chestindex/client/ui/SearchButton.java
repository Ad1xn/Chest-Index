package dev.adrian.chestindex.client.ui;

import dev.adrian.chestindex.client.platform.Keys;
import dev.adrian.chestindex.client.platform.ClientCompat;
import dev.adrian.chestindex.client.platform.Gfx;
import dev.adrian.chestindex.config.ChestIndexConfig;
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

public final class SearchButton extends AbstractWidget {
    public static final int SIZE = 11;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int ICON = 0xFF404040;
    private static final int HOVER = 0x80FFFFFF;

    private final java.util.function.IntSupplier anchorX;
    private final java.util.function.IntSupplier anchorY;

    private final String menuKey;

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

    public void follow() {
        if (dragging) return;
        int[] offset = ChestIndexConfig.get().searchButtonOffset(menuKey);
        setX(anchorX.getAsInt() + offset[0]);
        setY(anchorY.getAsInt() + offset[1]);
    }

    public String menuKey() {
        return menuKey;
    }

    public void setDragging(boolean value) {
        dragging = value;
    }

    private void draw(Gfx gfx, int mouseX, int mouseY) {
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

    private void drawMagnifier(Gfx gfx, int x, int y) {
        gfx.fill(x + 1, y, x + 4, y + 1, ICON);
        gfx.fill(x + 1, y + 4, x + 4, y + 5, ICON);
        gfx.fill(x, y + 1, x + 1, y + 4, ICON);
        gfx.fill(x + 4, y + 1, x + 5, y + 4, ICON);
        gfx.fill(x + 4, y + 4, x + 6, y + 6, ICON);
        gfx.fill(x + 5, y + 5, x + 8, y + 8, ICON);
    }

    public int anchorX() {
        return anchorX.getAsInt();
    }

    public int anchorY() {
        return anchorY.getAsInt();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!visible || !isMouseOver(event.x(), event.y())) return false;

        if (event.button() == Keys.MOUSE_LEFT) {
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            ClientCompat.openScreenFromContainer(new ChestIndexScreen());
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

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
