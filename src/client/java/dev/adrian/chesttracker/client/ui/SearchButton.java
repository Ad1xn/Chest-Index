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

public final class SearchButton extends AbstractWidget {

    public static final int SIZE = 12;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int ICON = 0xFF404040;
    private static final int HOVER = 0x80FFFFFF;

    private final int anchorX;
    private final int anchorY;

    private boolean dragging;

    public SearchButton(int anchorX, int anchorY) {
        super(0, 0, SIZE, SIZE, Component.literal("Search containers"));
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        applyConfiguredPosition();
        setTooltip(Tooltip.create(Component.literal("Search containers  (right-drag to move)")));
    }

    private void applyConfiguredPosition() {
        ChestTrackerConfig config = ChestTrackerConfig.get();
        setX(anchorX + config.searchButtonX);
        setY(anchorY + config.searchButtonY);
    }

    private void draw(Gfx gfx, int mouseX, int mouseY) {
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!visible || !isMouseOver(event.x(), event.y())) return false;

        if (event.button() == 1) {
            dragging = true;
            return true;
        }
        if (event.button() == 0) {
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            ClientCompat.openScreen(new ChestTrackerScreen());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging) return false;
        setX(getX() + (int) dragX);
        setY(getY() + (int) dragY);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!dragging) return false;
        dragging = false;
        ChestTrackerConfig config = ChestTrackerConfig.get();
        config.searchButtonX = getX() - anchorX;
        config.searchButtonY = getY() - anchorY;
        config.save();
        return true;
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
