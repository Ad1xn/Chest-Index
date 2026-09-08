package dev.adrian.chesttracker.client.ui;

import dev.adrian.chesttracker.client.platform.Gfx;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * Edits one list of text entries.
 *
 * <p>Four settings need exactly this - the servers the mod switches itself off
 * on, the servers the two assist features are trusted on, and the two container
 * groups the filters hide - and they differ only in which list they hold, what
 * the explanation says, and how a typed entry is tidied up before it is stored.
 * So the screen takes all three rather than knowing which one it is: a fifth
 * would be a call site, not a copy of this file.
 *
 * <p>The list is edited in place and written by the settings screen when it
 * closes, which is the one place that already saves. Editing a copy and
 * handing it back would mean the two screens disagreeing about the current
 * value for as long as this one is open.
 *
 * <p>Deliberately plain: a text field, an Add button, and a row per entry with
 * a remove button on it. There is no reordering because order does not mean
 * anything here - the list is a set of hosts to test against.
 */
public final class ServerListScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_WIDTH = 220;
    private static final int REMOVE_WIDTH = 22;
    private static final int TOP = 58;
    private static final int BOTTOM_RESERVED = 40;

    private final Screen parent;
    private final String heading;
    private final String explanation;
    private final List<String> entries;

    /**
     * Tidies a typed entry into whatever the list actually holds.
     *
     * <p>An address becomes a bare host; a container type becomes a namespaced
     * id. Doing it on the way in rather than on the way out means the list
     * cannot hold two spellings of one thing that fail to match each other.
     */
    private final java.util.function.UnaryOperator<String> tidy;

    /** What the box shows when empty, so the expected shape is visible. */
    private final String hint;

    private EditBox input;

    /** First entry shown; whole rows only, as in the settings screen. */
    private int scroll;

    /** The server-address shape, which is what most of these are. */
    public ServerListScreen(Screen parent, String heading, String explanation, List<String> entries) {
        this(parent, heading, explanation, entries,
                ChestTrackerConfig::host, "play.example.net");
    }

    public ServerListScreen(Screen parent, String heading, String explanation, List<String> entries,
                            java.util.function.UnaryOperator<String> tidy, String hint) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.explanation = explanation;
        this.entries = entries;
        this.tidy = tidy;
        this.hint = hint;
    }

    /**
     * A registry id, defaulted to the {@code minecraft} namespace.
     *
     * <p>So that typing {@code jukebox} works. Nobody types the namespace for a
     * vanilla block, and an entry without one would silently never match.
     */
    public static String asRegistryId(String typed) {
        String text = typed.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (text.isEmpty()) return "";
        return text.contains(":") ? text : "minecraft:" + text;
    }

    private int listX() {
        return (width - (LIST_WIDTH + REMOVE_WIDTH + 2)) / 2;
    }

    private int visibleRows() {
        return Math.max(1, (height - TOP - BOTTOM_RESERVED) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, entries.size() - visibleRows());
    }

    @Override
    protected void init() {
        int x = listX();

        input = new EditBox(font, x, 34, LIST_WIDTH, 18, Component.literal("Address"));
        input.setMaxLength(120);
        input.setHint(Component.literal(hint));
        addRenderableWidget(input);
        // Typing is the point of this screen, so it starts where the typing goes.
        setInitialFocus(input);

        addRenderableWidget(Button.builder(Component.literal("+"), button -> add())
                .bounds(x + LIST_WIDTH + 2, 34, REMOVE_WIDTH, 18).build());

        scroll = Math.min(scroll, maxScroll());
        int visible = visibleRows();
        for (int row = 0; row < visible; row++) {
            int index = scroll + row;
            if (index >= entries.size()) break;
            String entry = entries.get(index);
            int y = TOP + row * ROW_HEIGHT;

            // A disabled button rather than a label: it lines the host up with
            // the remove button beside it and needs no separate metrics.
            Button label = Button.builder(Component.literal(entry), button -> {})
                    .bounds(x, y, LIST_WIDTH, 20).build();
            label.active = false;
            addRenderableWidget(label);

            addRenderableWidget(Button.builder(Component.literal("x"), button -> remove(entry))
                    .bounds(x + LIST_WIDTH + 2, y, REMOVE_WIDTH, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 60, height - 28, 120, 20).build());
    }

    /**
     * Adds whatever is typed, tidied into the shape the list holds.
     *
     * <p>Stored tidied rather than as typed, so {@code Play.Example.NET:25565}
     * and {@code play.example.net} cannot both sit in the list looking like two
     * different servers.
     */
    private void add() {
        String typed = input.getValue();
        if (typed == null || typed.isBlank()) return;
        String entry = tidy.apply(typed);
        if (entry == null || entry.isBlank()) return;
        if (!entries.contains(entry)) entries.add(entry);
        input.setValue("");
        scroll = maxScroll();
        rebuild();
    }

    private void remove(String entry) {
        entries.remove(entry);
        scroll = Math.min(scroll, maxScroll());
        rebuild();
    }

    /** The list changed, so the rows built from it have to be built again. */
    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int max = maxScroll();
        if (max == 0) return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(deltaY)));
        if (next == scroll) return true;
        scroll = next;
        rebuild();
        return true;
    }

    @Override
    public void onClose() {
        // Saved here as well as by the settings screen: this list is edited in
        // place, and a player who closes the game from the settings screen
        // without touching anything else should still keep what they typed.
        ChestTrackerConfig.get().save();
        minecraft.setScreenAndShow(parent);
    }

    private void draw(Gfx gfx) {
        gfx.text(font, Component.literal(heading),
                width / 2 - font.width(heading) / 2, 12, 0xFFFFFFFF);

        int y = height - 42;
        for (String line : explanation.split("\n")) {
            gfx.text(font, Component.literal(line), width / 2 - font.width(line) / 2, y, 0xFFA0A0A0);
            y += 10;
        }

        if (entries.isEmpty()) {
            String empty = "Nothing listed.";
            gfx.text(font, Component.literal(empty),
                    width / 2 - font.width(empty) / 2, TOP + 4, 0xFF808080);
        }
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
