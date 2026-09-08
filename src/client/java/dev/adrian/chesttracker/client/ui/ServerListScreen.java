package dev.adrian.chesttracker.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.adrian.chesttracker.client.platform.Gfx;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * Edits one list of text entries, in the chest window.
 *
 * <p>Four settings need exactly this - the servers the mod switches itself off
 * on, the servers the two assist features are trusted on, and the two container
 * groups the filters hide - and they differ only in which list they hold, what
 * the explanation says, and how a typed entry is tidied up before it is stored.
 * So the screen takes all three rather than knowing which one it is.
 *
 * <p>The list is edited in place and written when this screen closes.
 *
 * <h2>Why there are two panes</h2>
 *
 * <p>It used to be a text field and a column of rows, which meant the only way
 * to name a server was to remember its address and type it correctly - for a
 * setting whose whole job is "don't run on that one place I play". The screen
 * already has everything it needs to do better: Minecraft keeps the multiplayer
 * server list on disk and the client knows what it is connected to right now.
 * So the second pane is that list, and adding a server is pointing at it.
 *
 * <p>The container-type lists get the same pane filled from the block registry
 * instead, for the same reason: {@code minecraft:chiseled_bookshelf} is not
 * something anybody should have to spell from memory.
 *
 * <p>Typing still works, and is still the answer for a server that is not in
 * the list yet.
 */
public final class ServerListScreen extends Screen {

    /** One thing the right-hand pane can offer: what to show, and what to store. */
    private record Suggestion(String label, String value) {}

    private static final int ROW_H = 20;
    private static final int CONTENT_W = 258;
    private static final int SIDE_PAD = 8;
    private static final int PAD = 2;
    private static final int TAB_H = 16;
    private static final int BUTTON_SIZE = 12;
    private static final int SCROLLBAR_W = 8;

    private static final int MIN_ROWS = 4;
    private static final int MAX_ROWS = 9;

    /** How many registry ids the suggestion pane will offer at once. */
    private static final int MAX_SUGGESTIONS = 200;

    private static final int PANEL_W = (Panel.EDGE_W + SIDE_PAD) * 2 + CONTENT_W;

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
    private final UnaryOperator<String> tidy;

    /** What the box shows when empty, so the expected shape is visible. */
    private final String hint;

    /** Whether the offers pane is the multiplayer server list or the registry. */
    private final boolean offersServers;

    /** What the offers pane is called, on its tab. */
    private final String sourceName;

    private EditBox input;

    private int panelX;
    private int panelY;
    private int visibleRows;

    /** False while the listed entries are shown, true while the offers are. */
    private boolean showingSource;

    private int scroll;

    private String hoverTitle;

    /** Rebuilt when the typed text changes, because it filters them. */
    private List<Suggestion> suggestions;
    private String suggestionsFor;

    /** The server-address shape, which is what most of these are. */
    public ServerListScreen(Screen parent, String heading, String explanation, List<String> entries) {
        this(parent, heading, explanation, entries,
                ChestTrackerConfig::host, "play.example.net", "Your servers", true);
    }

    public ServerListScreen(Screen parent, String heading, String explanation, List<String> entries,
                            UnaryOperator<String> tidy, String hint) {
        this(parent, heading, explanation, entries, tidy, hint, "Block types", false);
    }

    private ServerListScreen(Screen parent, String heading, String explanation, List<String> entries,
                             UnaryOperator<String> tidy, String hint, String sourceName,
                             boolean offersServers) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.explanation = explanation;
        this.entries = entries;
        this.tidy = tidy;
        this.hint = hint;
        this.sourceName = sourceName;
        // Said outright rather than inferred from which tidier was passed. That
        // read as a tidy trick and was simply wrong: two method references to
        // the same method are not the same object, so the test was always false
        // and every list got the registry pane.
        this.offersServers = offersServers;
    }

    /**
     * A registry id, defaulted to the {@code minecraft} namespace.
     *
     * <p>So that typing {@code jukebox} works. Nobody types the namespace for a
     * vanilla block, and an entry without one would silently never match.
     */
    public static String asRegistryId(String typed) {
        String text = typed.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (text.isEmpty()) return "";
        return text.contains(":") ? text : "minecraft:" + text;
    }

    // --- where the offers come from -----------------------------------------

    /**
     * The multiplayer server list, plus whatever is connected right now.
     *
     * <p>Read off disk rather than held: this screen is opened rarely and the
     * list is small, and reading it fresh means a server added five minutes ago
     * in the multiplayer menu is here.
     */
    private List<Suggestion> knownServers() {
        List<Suggestion> found = new ArrayList<>();

        ServerData current = minecraft == null ? null : minecraft.getCurrentServer();
        if (current != null && current.ip != null && !current.ip.isBlank()) {
            found.add(new Suggestion(
                    (current.name == null || current.name.isBlank() ? current.ip : current.name)
                            + "  (connected)", current.ip));
        }

        try {
            ServerList saved = new ServerList(minecraft);
            saved.load();
            for (int i = 0; i < saved.size(); i++) {
                ServerData data = saved.get(i);
                if (data == null || data.ip == null || data.ip.isBlank()) continue;
                String label = data.name == null || data.name.isBlank() ? data.ip : data.name;
                if (found.stream().anyMatch(s -> s.value().equals(data.ip))) continue;
                found.add(new Suggestion(label, data.ip));
            }
        } catch (RuntimeException unreadable) {
            // A missing or half-written servers.dat is not worth a crash on a
            // settings screen; typing still works.
        }
        return found;
    }

    /**
     * Block ids matching what has been typed.
     *
     * <p>Filtered rather than listed whole: the registry holds well over a
     * thousand blocks and a wall of them is no more useful than no list at all.
     */
    private List<Suggestion> matchingBlocks() {
        String typed = input == null ? "" : input.getValue().trim().toLowerCase(Locale.ROOT);
        List<Suggestion> found = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
            String full = id.toString();
            if (!typed.isEmpty() && !full.contains(typed)) continue;
            found.add(new Suggestion(full.startsWith("minecraft:") ? full.substring(10) : full, full));
            if (found.size() >= MAX_SUGGESTIONS) break;
        }
        return found;
    }

    /** The offers, rebuilt only when the typed text they filter on changes. */
    private List<Suggestion> offers() {
        String typed = input == null ? "" : input.getValue();
        if (suggestions == null || !typed.equals(suggestionsFor)) {
            suggestions = offersServers ? knownServers() : matchingBlocks();
            suggestionsFor = typed;
        }
        return suggestions;
    }

    private List<String> shownRows() {
        if (!showingSource) return entries;
        List<String> labels = new ArrayList<>();
        for (Suggestion suggestion : offers()) labels.add(suggestion.label());
        return labels;
    }

    // --- geometry ------------------------------------------------------------

    private static final int CHROME_H =
            Panel.TOP_H + Panel.SEARCH_H + PAD + TAB_H + PAD + 2 + PAD + Panel.BOTTOM_H;

    private int contentX() {
        return panelX + Panel.EDGE_W + SIDE_PAD;
    }

    private int tabsY() {
        return panelY + Panel.TOP_H + Panel.SEARCH_H + PAD;
    }

    private int contentTop() {
        return tabsY() + TAB_H + PAD;
    }

    private int contentH() {
        return visibleRows * ROW_H + 2;
    }

    private int panelH() {
        return CHROME_H + visibleRows * ROW_H;
    }

    private int rowsX() {
        return contentX() + 1;
    }

    private int rowsW() {
        return CONTENT_W - 2 - (overflows() ? SCROLLBAR_W + 1 : 0);
    }

    private int rowY(int index) {
        return contentTop() + 1 + index * ROW_H;
    }

    private int scrollbarX() {
        return contentX() + CONTENT_W - 1 - SCROLLBAR_W;
    }

    private boolean overflows() {
        return shownRows().size() > visibleRows;
    }

    private int maxScroll() {
        return Math.max(0, shownRows().size() - visibleRows);
    }

    private int tabW() {
        return (CONTENT_W - 4) / 2;
    }

    private int tabX(boolean listed) {
        return contentX() + (listed ? 0 : tabW() + 4);
    }

    private int closeX() {
        return panelX + PANEL_W - SIDE_PAD - BUTTON_SIZE;
    }

    private int titleWidth() {
        return closeX() - (panelX + SIDE_PAD) - 4;
    }

    @Override
    protected void init() {
        int room = height - 32 - CHROME_H;
        visibleRows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, room / ROW_H));

        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH()) / 2;

        // See SettingsScreen: an unbordered box draws its text at getY().
        input = new EditBox(font, panelX + 11, panelY + Panel.TOP_H + 4,
                PANEL_W - 22, 10, Component.literal("Add"));
        input.setBordered(false);
        input.setMaxLength(120);
        input.setResponder(typed -> {
            // The offers pane filters on what is typed, so it has to re-ask.
            suggestions = null;
            if (showingSource) scroll = 0;
        });
        addRenderableWidget(input);
        setInitialFocus(input);

        scroll = Math.min(scroll, maxScroll());
    }

    // --- drawing --------------------------------------------------------------

    private void draw(Gfx gfx, int mouseX, int mouseY) {
        hoverTitle = null;

        drawPlaceholder(gfx);
        drawCloseButton(gfx, mouseX, mouseY);
        drawTabs(gfx, mouseX, mouseY);
        Panel.well(gfx, contentX(), contentTop(), CONTENT_W, contentH());
        drawRows(gfx, mouseX, mouseY);

        if (overflows()) {
            Panel.scrollbar(gfx, scrollbarX(), contentTop() + 1, SCROLLBAR_W, contentH() - 2,
                    scroll, maxScroll(), shownRows().size(), visibleRows);
        }

        String title = hoverTitle != null ? hoverTitle : heading;
        gfx.text(font, Component.literal(truncate(title, titleWidth())),
                panelX + SIDE_PAD, panelY + 6, Panel.TEXT_MAIN);

        drawFooter(gfx);
    }

    private void drawPlaceholder(Gfx gfx) {
        if (input == null || !input.getValue().isEmpty() || input.isFocused()) return;
        gfx.text(font, Component.literal("Type one (e.g. " + hint + ") and press enter"),
                input.getX(), input.getY(), Panel.TEXT_MUTED);
    }

    private void drawCloseButton(Gfx gfx, int mouseX, int mouseY) {
        int x = closeX();
        int y = panelY + 3;
        boolean hovered = within(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE);
        Panel.raised(gfx, x, y, BUTTON_SIZE, BUTTON_SIZE, false);
        if (hovered) {
            gfx.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, Panel.HOVER);
            hoverTitle = "Back to settings";
        }
        gfx.text(font, "X", x + 3, y + 2, Panel.TEXT_MAIN);
    }

    private void drawTabs(Gfx gfx, int mouseX, int mouseY) {
        String[] labels = {"In the list (" + entries.size() + ")", sourceName};
        for (int i = 0; i < 2; i++) {
            boolean listed = i == 0;
            int x = tabX(listed);
            int y = tabsY();
            int w = tabW();
            boolean active = listed != showingSource;
            boolean hovered = within(mouseX, mouseY, x, y, w, TAB_H);

            Panel.raised(gfx, x, y, w, TAB_H, active);
            String label = truncate(labels[i], w - 8);
            gfx.text(font, Component.literal(label), x + (w - font.width(label)) / 2, y + 4,
                    active ? Panel.TEXT_MAIN : Panel.TEXT_MUTED);
            if (hovered) {
                gfx.fill(x + 1, y + 1, x + w - 1, y + TAB_H - 1, Panel.HOVER);
                hoverTitle = listed ? "What this setting holds" : "Pick one to add it";
            }
        }
    }

    private void drawRows(Gfx gfx, int mouseX, int mouseY) {
        List<String> rows = shownRows();
        if (rows.isEmpty()) {
            String empty = showingSource ? "Nothing to offer." : "Nothing listed.";
            gfx.text(font, Component.literal(empty),
                    contentX() + (CONTENT_W - font.width(empty)) / 2,
                    contentTop() + contentH() / 2 - 4, Panel.TEXT_MUTED);
            return;
        }

        int left = rowsX();
        int right = left + rowsW();
        int hovered = rowAt(mouseX, mouseY);
        List<Suggestion> offers = showingSource ? offers() : List.of();

        for (int i = 0; i < visibleRows; i++) {
            int index = scroll + i;
            if (index >= rows.size()) break;
            int y = rowY(i);

            if (i > 0) gfx.fill(left + 2, y, right - 2, y + 1, 0x18000000);
            if (index == hovered) {
                gfx.fill(left, y + 1, right, y + ROW_H, 0x30FFFFFF);
                hoverTitle = rows.get(index);
            }

            int textY = y + (ROW_H - 8) / 2;
            int markX = right - 4 - BUTTON_SIZE;
            gfx.text(font, Component.literal(truncate(rows.get(index), markX - left - 8)),
                    left + 4, textY, Panel.TEXT_MAIN);

            if (showingSource) {
                // A tick rather than a plus on anything already held, so the
                // pane says what is in the list without being read against it.
                boolean already = index < offers.size()
                        && entries.contains(tidy.apply(offers.get(index).value()));
                Panel.raised(gfx, markX, y + 4, BUTTON_SIZE, BUTTON_SIZE, already);
                if (already) {
                    Panel.tick(gfx, markX + 3, y + 6, Panel.TEXT_MAIN);
                } else {
                    gfx.fill(markX + 5, y + 7, markX + 8, y + 8, Panel.ICON);
                    gfx.fill(markX + 6, y + 6, markX + 7, y + 9, Panel.ICON);
                }
            } else {
                Panel.raised(gfx, markX, y + 4, BUTTON_SIZE, BUTTON_SIZE, false);
                gfx.text(font, "x", markX + 3, y + 6, Panel.TEXT_MAIN);
            }
        }
    }

    private void drawFooter(Gfx gfx) {
        int y = panelY + panelH() + 6;
        for (String line : explanation.split("\n")) {
            gfx.text(font, Component.literal(line),
                    panelX + (PANEL_W - font.width(line)) / 2, y, Panel.TEXT_MUTED);
            y += 10;
        }
    }

    private String truncate(String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static boolean within(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // --- input ----------------------------------------------------------------

    private int rowAt(int mouseX, int mouseY) {
        if (mouseX < rowsX() || mouseX >= rowsX() + rowsW()) return -1;
        int top = contentTop() + 1;
        if (mouseY < top || mouseY >= top + visibleRows * ROW_H) return -1;
        int index = scroll + (mouseY - top) / ROW_H;
        return index < shownRows().size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        if (within(mouseX, mouseY, closeX(), panelY + 3, BUTTON_SIZE, BUTTON_SIZE)) {
            VanillaButton.playClick();
            onClose();
            return true;
        }

        for (int i = 0; i < 2; i++) {
            boolean listed = i == 0;
            if (within(mouseX, mouseY, tabX(listed), tabsY(), tabW(), TAB_H)) {
                if (showingSource == listed) {
                    showingSource = !listed;
                    scroll = 0;
                    VanillaButton.playClick();
                }
                return true;
            }
        }

        if (overflows() && within(mouseX, mouseY, scrollbarX(), contentTop(), SCROLLBAR_W, contentH())) {
            dragScrollbar(mouseY);
            return true;
        }

        int index = rowAt(mouseX, mouseY);
        if (index >= 0) {
            if (showingSource) {
                List<Suggestion> offers = offers();
                if (index < offers.size()) addEntry(offers.get(index).value());
            } else {
                entries.remove(index);
                scroll = Math.min(scroll, maxScroll());
                VanillaButton.playClick();
            }
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Adds an entry, tidied into the shape the list holds.
     *
     * <p>Stored tidied rather than as typed, so {@code Play.Example.NET:25565}
     * and {@code play.example.net} cannot both sit in the list looking like two
     * different servers.
     */
    private void addEntry(String raw) {
        if (raw == null || raw.isBlank()) return;
        String entry = tidy.apply(raw);
        if (entry == null || entry.isBlank()) return;
        if (!entries.contains(entry)) entries.add(entry);
        VanillaButton.playClick();
    }

    private void dragScrollbar(int mouseY) {
        int max = maxScroll();
        if (max == 0) return;
        double fraction = (mouseY - (contentTop() + 1)) / (double) (contentH() - 2);
        scroll = Math.max(0, Math.min(max, (int) Math.round(fraction * max)));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (within(event.x(), event.y(), scrollbarX(), contentTop(), SCROLLBAR_W, contentH())) {
            dragScrollbar((int) event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int max = maxScroll();
        if (max == 0) return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(deltaY)));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        // Enter adds what is typed. Typing is still the answer for a server
        // that is not in the multiplayer list yet.
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && input != null && !input.getValue().isBlank()) {
            addEntry(input.getValue());
            input.setValue("");
            showingSource = false;
            scroll = maxScroll();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        // Saved here as well as by the settings screen: this list is edited in
        // place, and a player who closes the game from the settings screen
        // without touching anything else should still keep what they typed.
        ChestTrackerConfig.get().save();
        minecraft.setScreenAndShow(parent);
    }

    //? if >=26.1 {
    /*@Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Gfx gfx = new Gfx(graphics);
        Panel.window(gfx, panelX, panelY, PANEL_W, panelH());
        Panel.searchStrip(gfx, panelX, panelY + Panel.TOP_H, PANEL_W);
    }
    *///?} else {
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        Gfx gfx = new Gfx(graphics);
        Panel.window(gfx, panelX, panelY, PANEL_W, panelH());
        Panel.searchStrip(gfx, panelX, panelY + Panel.TOP_H, PANEL_W);
    }
    //?}

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
