package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.client.platform.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

public final class ConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int WIDGET_WIDTH = 200;

    private static final int COLUMN_GAP = 8;

    private static final int ROWS_PER_COLUMN = 8;

    private static final int TOP = 30;

    private static final int MAX_RESULTS_CEILING = 2000;
    private static final int RESULTS_STEP = 50;

    private static final int HIGHLIGHT_MAX = 300;
    private static final int GRACE_MAX = 120;

    private final Screen parent;
    private final ChestTrackerConfig config = ChestTrackerConfig.get();

    public ConfigScreen(Screen parent) {
        super(Component.literal("ChestTracker Settings"));
        this.parent = parent;
    }

    private int placed;

    private int columnX() {
        int left = width / 2 - WIDGET_WIDTH - COLUMN_GAP / 2;
        return placed < ROWS_PER_COLUMN ? left : left + WIDGET_WIDTH + COLUMN_GAP;
    }

    private int rowY() {
        return TOP + (placed % ROWS_PER_COLUMN) * ROW_HEIGHT;
    }

    private <T extends net.minecraft.client.gui.components.AbstractWidget> T place(T widget) {
        addRenderableWidget(widget);
        placed++;
        return widget;
    }

    @Override
    protected void init() {
        placed = 0;
        int x = columnX();
        int y = rowY();

        place(toggle(x, y, "Scan world on join",
                "Reads the world off disk in the background when you join, so containers in\nchunks you have never visited are found too. Costs some throughput for a\nfew seconds on a large world.",
                () -> config.scanOnWorldJoin, value -> config.scanOnWorldJoin = value));

        x = columnX(); y = rowY();
        place(toggle(x, y, "Count items inside shulker boxes",
                "Whether a shulker box in a chest contributes its contents to that chest,\nor only counts as a shulker box.",
                () -> config.includeNested, value -> config.includeNested = value));

        x = columnX(); y = rowY();
        place(toggle(x, y, "Show machines by default",
                "Hoppers, furnaces, droppers and the like. Always indexed; this is only\nwhether the search screen starts with them shown.",
                () -> config.showMachines, value -> config.showMachines = value));

        x = columnX(); y = rowY();

        place(new IntSlider(x, y, config.maxResults, 0, MAX_RESULTS_CEILING, RESULTS_STEP) {
            @Override
            String label(int value) {
                return "Items shown: " + (value >= MAX_RESULTS_CEILING ? "unlimited" : Integer.toString(value));
            }

            @Override
            void apply(int value) {
                config.maxResults = value >= MAX_RESULTS_CEILING
                        ? ChestTrackerConfig.UNLIMITED_RESULTS : value;
            }
        });

        x = columnX(); y = rowY();
        place(displayCycle(x, y));

        x = columnX(); y = rowY();
        place(new IntSlider(x, y, config.highlightSeconds, 5, HIGHLIGHT_MAX, 5) {
            @Override
            String label(int value) {
                return "Guidance lasts: " + value + "s";
            }

            @Override
            void apply(int value) {
                config.highlightSeconds = value;
            }
        });

        x = columnX(); y = rowY();
        place(new IntSlider(x, y, config.highlightRecedingGraceSeconds, 1, GRACE_MAX, 1) {
            @Override
            String label(int value) {
                return "Grace when walking away: " + value + "s";
            }

            @Override
            void apply(int value) {
                config.highlightRecedingGraceSeconds = value;
            }
        });

        x = columnX(); y = rowY();
        place(toggle(x, y, "Trail of marks above matches",
                "Stands a column of fading marks on every match. This is the part that\nstill works past render distance, where there is no terrain drawn to place\na box against.",
                () -> config.guideBeam, value -> config.guideBeam = value));

        x = columnX(); y = rowY();
        place(toggle(x, y, "Open a match already in reach",
                "If a container you just searched for is within normal reach, open it\ninstead of pointing at it. Sends the same interaction as right-clicking,\nso a server checks the distance as usual.",
                () -> config.openInReach, value -> config.openInReach = value));

        x = columnX(); y = rowY();
        place(toggle(x, y, "Turn to face a match",
                "Turns your view towards the nearest match when a search lands. Worth\nknowing on a server you do not run: a client that moves the view is the\nshape of thing some anti-cheats watch for.",
                () -> config.turnToTarget, value -> config.turnToTarget = value));

        x = columnX(); y = rowY();
        place(toggle(x, y, "Search button on containers",
                "A small magnifier on chests and other container windows. Left-click opens\nthe search screen; right-drag moves the button and remembers where.",
                () -> config.containerSearchButton, value -> config.containerSearchButton = value));

        x = columnX(); y = rowY();
        place(toggle(x, y, "Ender chest view",
                "Offers your ender chest as a view of its own, beside the dimension\nbuttons. It lists only what is in there - never mixed with a\ndimension - and appears only when it is not empty.",
                () -> config.enderChestView, value -> config.enderChestView = value));

        x = columnX(); y = rowY();
        place(toggle(x, y, "Shift shows item detail",
                "Holding shift over an item in the search grid describes it: how many\nthere are, in how many containers, how many are sealed inside shulker\nboxes, and how far the nearest is.",
                () -> config.nestedTooltip, value -> config.nestedTooltip = value));

        x = columnX(); y = rowY();
        place(Button.builder(Component.literal("Highlight colours..."),
                        button -> minecraft.setScreenAndShow(new HighlightColourScreen(this)))
                .bounds(x, y, WIDGET_WIDTH, 20)
                .tooltip(Tooltip.create(Component.literal(
                        "The colours the in-world markers are drawn in - one for the\nnearest match, one for the rest.")))
                .build());

        x = columnX(); y = rowY();
        place(accessCycle(x, y));

        int bottom = TOP + ROWS_PER_COLUMN * ROW_HEIGHT + 10;
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 60, bottom, 120, 20).build());
    }

    abstract static class IntSlider extends AbstractSliderButton {

        private final int min;
        private final int max;
        private final int step;

        IntSlider(int x, int y, int current, int min, int max, int step) {
            this(x, y, WIDGET_WIDTH, current, min, max, step, true);
        }

        IntSlider(int x, int y, int width, int current, int min, int max, int step) {
            this(x, y, width, current, min, max, step, false);
        }

        private IntSlider(int x, int y, int width, int current,
                          int min, int max, int step, boolean zeroIsMax) {
            super(x, y, width, 20, Component.empty(), fraction(current, min, max, zeroIsMax));
            this.min = min;
            this.max = max;
            this.step = step;
            updateMessage();
        }

        private static double fraction(int current, int min, int max, boolean zeroIsMax) {
            int effective = zeroIsMax && current <= 0 ? max : current;
            return Math.min(1.0, Math.max(0.0, (effective - min) / (double) (max - min)));
        }

        private int current() {
            int raw = (int) Math.round(min + value * (max - min));
            int snapped = Math.round(raw / (float) step) * step;
            return Math.min(max, Math.max(min, snapped));
        }

        abstract String label(int value);

        abstract void apply(int value);

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label(current())));
        }

        @Override
        protected void applyValue() {
            apply(current());
        }
    }

    private Button toggle(int x, int y, String label,
                          java.util.function.BooleanSupplier getter,
                          java.util.function.Consumer<Boolean> setter) {
        return toggle(x, y, label, null, getter, setter);
    }

    private Button toggle(int x, int y, String label, String help,
                          java.util.function.BooleanSupplier getter,
                          java.util.function.Consumer<Boolean> setter) {
        Button button = Button.builder(Component.literal(label + ": " + onOff(getter.getAsBoolean())), it -> {
            setter.accept(!getter.getAsBoolean());
            it.setMessage(Component.literal(label + ": " + onOff(getter.getAsBoolean())));
        }).bounds(x, y, WIDGET_WIDTH, 20).build();
        if (help != null) button.setTooltip(Tooltip.create(Component.literal(help)));
        return button;
    }

    private Button displayCycle(int x, int y) {
        return Button.builder(Component.literal(displayLabel()), button -> {
            ChestTrackerConfig.Display[] modes = ChestTrackerConfig.Display.values();
            int next = (config.highlightDisplay().ordinal() + 1) % modes.length;
            config.highlightDisplay = modes[next].name();
            button.setMessage(Component.literal(displayLabel()));
        }).bounds(x, y, WIDGET_WIDTH, 20).build();
    }

    private String displayLabel() {
        String mode = switch (config.highlightDisplay()) {
            case BOXES -> "boxes in the world";
            case ACTION_BAR -> "text above the hotbar";
            case BOTH -> "boxes and text";
            case NONE -> "nothing";
        };
        return "Show found containers as: " + mode;
    }

    private Button accessCycle(int x, int y) {
        return Button.builder(Component.literal(accessLabel()), button -> {
            ChestTrackerConfig.Access[] tiers = ChestTrackerConfig.Access.values();
            int next = (config.permissionTier().ordinal() + 1) % tiers.length;
            config.permissionTier = tiers[next].name();
            button.setMessage(Component.literal(accessLabel()));
        }).bounds(x, y, WIDGET_WIDTH, 20).build();
    }

    private String accessLabel() {
        String tier = switch (config.permissionTier()) {
            case ALL -> "everyone";
            case OWNED -> "own containers";
            case OP -> "operators";
        };
        return "LAN/server guests may search: " + tier;
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    @Override
    public void onClose() {
        config.save();
        minecraft.setScreenAndShow(parent);
    }

    private void draw(Gfx gfx) {
        gfx.text(font, Component.literal("ChestTracker Settings"),
                width / 2 - font.width("ChestTracker Settings") / 2, 18, 0xFFFFFFFF);

        String footer = Minecraft.getInstance().hasSingleplayerServer()
                ? "The access setting only applies once you open this world to LAN."
                : "Scanning happens in the background; a large world fills in over time.";
        gfx.text(font, Component.literal(footer),
                width / 2 - font.width(footer) / 2, height - 34, 0xFFA0A0A0);
    }

    //? if >=26.1 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        draw(new Gfx(graphics));
    }
    *///?} else {
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        draw(new Gfx(graphics));
    }
    //?}
}
