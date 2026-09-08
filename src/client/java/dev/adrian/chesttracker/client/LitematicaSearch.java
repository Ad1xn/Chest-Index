package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.ChestTracker;
import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import dev.adrian.chesttracker.client.platform.ClientCompat;
import dev.adrian.chesttracker.client.platform.Gfx;
import dev.adrian.chesttracker.client.ui.VanillaButton;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.net.QueryDto;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Search buttons on Litematica's material list.
 *
 * <p>The material list answers "what does this schematic need". The obvious
 * next question is "and where is any of it", which is what this mod already
 * knows - so the two are one click apart rather than a list to copy out by
 * hand and search for one item at a time.
 *
 * <p>There are two of them, and where they sit is the point. One per row, in
 * the row it belongs to, immediately left of that row's {@code Ignore} button:
 * "where is this one" is the question a player has while looking at a single
 * line, and answering it from that line is one movement. One beside
 * {@code Export}, among the buttons that act on the whole list, for "where is
 * any of this".
 *
 * <p>Both are drawn from vanilla's button texture through
 * {@link dev.adrian.chesttracker.client.ui.VanillaButton} and are positioned
 * off the real widgets they sit beside - see {@link LitematicaGui}. They
 * replace a floating pair drawn in malilib's colours that had to be dragged
 * into place by hand, which made them look like something a different mod had
 * spilled onto the screen.
 *
 * <h2>Why reflection, and why no mixin</h2>
 *
 * <p>Litematica is not a build dependency and must not become one: it does not
 * exist for every version this mod targets, and a hard dependency would mean
 * shipping a build that refuses to load without it.
 *
 * <p>No mixin is needed either, which was the surprise. Litematica's GUIs are
 * built on malilib, whose {@code GuiBase} extends vanilla's {@code Screen} -
 * so Fabric's ordinary screen events fire for the material list like any other
 * screen, and the button can be drawn and clicked through the public API.
 *
 * <p>What does need reflection is reading the list itself, and the API for that
 * is small and identical across every Litematica checked - {@code 0.26.8} and
 * {@code 0.26.15} for 1.21.11, {@code 0.27.12} for 26.1, and {@code 0.28.7} for
 * 26.2, which spans two of masa's release branches:
 *
 * <pre>
 *   GuiMaterialList.getMaterialList()            -&gt; MaterialListBase
 *   MaterialListBase.getMaterialsMissingOnly(boolean) -&gt; List&lt;MaterialListEntry&gt;
 *   MaterialListBase.getMaterialsAll()           -&gt; List&lt;MaterialListEntry&gt;
 *   MaterialListEntry.getStack()                 -&gt; ItemStack
 *   MaterialListEntry.getCountTotal()            -&gt; int
 * </pre>
 *
 * <p>Every value that crosses back is a vanilla type or a primitive, so only
 * the call itself is reflective and nothing here holds a Litematica type.
 *
 * <p>Everything is wrapped: a Litematica version that renames any of this makes
 * the button not appear, which is the correct failure. Taking somebody's
 * schematic GUI down with a crash because our button could not find a method
 * would not be.
 */
public final class LitematicaSearch {

    private LitematicaSearch() {}

    private static final String MATERIAL_LIST_GUI = "fi.dy.masa.litematica.gui.GuiMaterialList";

    /** Kept small: the highlight caps its boxes anyway, and so does the wire. */
    private static final int MAX_CONTAINERS = 64;

    /** Between one of our buttons and the Litematica button it sits beside. */
    private static final int BUTTON_GAP = 2;

    private static final String ALL_LABEL = "Search all";

    /** Logged once, so an integration that never fires can be told from one that does. */
    private static boolean reported;

    /** The material list currently open, or null. Set by the screen hook. */
    private static Screen openList;

    /**
     * Whether the buttons were already drawn this frame, from inside malilib's
     * own render.
     *
     * <p>The mixin that does that is allowed to fail to apply - a malilib that
     * reshuffled its render method - and the buttons must still appear when it
     * does. So it reports having drawn, and the after-render fallback stands
     * down for that frame rather than drawing them twice.
     */
    private static boolean drawnBeforeTooltips;

    /**
     * Draws the buttons from inside malilib's render, before it draws its
     * tooltips. Called by {@code MalilibScreenMixin}; see the note there.
     */
    public static void drawBeforeTooltips(Gfx gfx, Screen screen, int mouseX, int mouseY) {
        if (screen != openList) return;
        drawnBeforeTooltips = true;
        drawButtons(gfx, screen, mouseX, mouseY);
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!isMaterialList(screen)) {
                // Any other screen means the list is gone; a stale reference
                // would leave the drag poll acting on a screen nobody can see.
                if (openList != null && screen != openList) openList = null;
                return;
            }
            if (!Session.active()) return;
            if (!ChestTrackerConfig.get().litematicaButton) return;

            openList = screen;
            if (!reported) {
                reported = true;
                ChestTracker.LOG.info("Litematica material list hook installed");
            }

            // The fallback route, and only that: this fires after the whole
            // screen is drawn, so anything painted here sits on top of the
            // tooltips too. MalilibScreenMixin draws them at the right point
            // instead, and this runs only when that injection did not apply -
            // see drawBeforeTooltips.
            ClientCompat.afterScreenRender(screen, (gfx, mouseX, mouseY) -> {
                if (drawnBeforeTooltips) {
                    drawnBeforeTooltips = false;
                    return;
                }
                drawButtons(gfx, screen, mouseX, mouseY);
            });

            // "allow" rather than "before", so the click is also swallowed and
            // does not reach whatever Litematica has underneath the button.
            ScreenMouseEvents.allowMouseClick(screen).register((clicked, event) -> {
                if (event.button() != 0) return true;

                for (LitematicaGui.Row row : LitematicaGui.rows(clicked)) {
                    if (rowButton(row).contains(event.x(), event.y())) {
                        VanillaButton.playClick();
                        searchOne(clicked, row);
                        return false;
                    }
                }

                LitematicaGui.Rect all = allButton(clicked);
                if (all != null && all.contains(event.x(), event.y())) {
                    VanillaButton.playClick();
                    run(clicked);
                    return false;
                }
                return true;
            });
        });
    }

    // --- the buttons --------------------------------------------------------

    /**
     * Where one row's button goes: square, the height of the row's own
     * {@code Ignore} button, immediately to its left.
     *
     * <p>Square because there is a column of counts to the left of
     * {@code Ignore} and the gap between them is small. A magnifier says
     * "search" in the width of an icon; a word would not fit without pushing
     * into the numbers.
     */
    private static LitematicaGui.Rect rowButton(LitematicaGui.Row row) {
        LitematicaGui.Rect anchor = row.anchor();
        int size = anchor.height();
        return new LitematicaGui.Rect(anchor.x() - size - BUTTON_GAP, anchor.y(), size, size);
    }

    /**
     * Where the whole-list button goes: right of {@code Export}, matching its
     * height - or nowhere, if that button is not on this screen.
     */
    private static LitematicaGui.Rect allButton(Screen screen) {
        LitematicaGui.Rect export = LitematicaGui.exportButton(screen);
        if (export == null) return null;

        int width = VanillaButton.widthFor(ALL_LABEL);
        // Clamped, because Litematica's own row of buttons can already reach
        // the edge on a narrow window, and a button off the screen is a button
        // nobody can press.
        int x = Math.min(export.x() + export.width() + BUTTON_GAP, screen.width - width - 1);
        return new LitematicaGui.Rect(x, export.y(), width, export.height());
    }

    /**
     * Drawn by hand rather than added as widgets.
     *
     * <p>malilib draws its own screens and does not render vanilla's widget
     * list, so a {@code Button} added here would be clickable and invisible -
     * which is worse than not being there. The art is still the game's own:
     * see {@link VanillaButton}, which blits vanilla's button texture, so a
     * resource pack that restyles buttons restyles these with them.
     */
    private static void drawButtons(Gfx gfx, Screen screen, int mouseX, int mouseY) {
        for (LitematicaGui.Row row : LitematicaGui.rows(screen)) {
            LitematicaGui.Rect button = rowButton(row);
            VanillaButton.draw(gfx, button.x(), button.y(), button.width(), button.height(),
                    button.contains(mouseX, mouseY));
            VanillaButton.magnifier(gfx,
                    button.x() + (button.width() - 8) / 2,
                    button.y() + (button.height() - 8) / 2,
                    VanillaButton.ICON);
        }

        LitematicaGui.Rect all = allButton(screen);
        if (all != null) {
            VanillaButton.draw(gfx, all.x(), all.y(), all.width(), all.height(),
                    all.contains(mouseX, mouseY), ALL_LABEL);
        }
    }

    /**
     * Looks for one row's material.
     *
     * <p>The count comes from the row rather than from the player's inventory,
     * so the highlight retires itself once they are carrying enough - the same
     * rule the whole-list search uses, applied to a single material.
     */
    private static void searchOne(Screen screen, LitematicaGui.Row row) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        if (!ClientTracker.isAvailable()) {
            say("No index here yet.", ChatFormatting.RED);
            return;
        }

        String itemId = row.itemId();
        String label = displayName(itemId);
        String dimensionId = player.level().dimension().identifier().toString();
        Map<String, Integer> needed = Map.of(itemId, row.needed());

        ClientTracker.containers(List.of(itemId), ContainerSearch.searchFilters(), MAX_CONTAINERS, "")
                .thenAccept(response -> client.execute(() -> {
                    List<QueryDto.ContainerHit> hits = response.hits();
                    if (hits.isEmpty()) {
                        say("Nothing indexed holds " + label, ChatFormatting.YELLOW);
                        return;
                    }
                    LocalPlayer now = Minecraft.getInstance().player;
                    if (now == null
                            || !now.level().dimension().identifier().toString().equals(dimensionId)) {
                        return;
                    }
                    ContainerHighlight.get().selectHits(hits, dimensionId, label);
                    ContainerHighlight.get().searchingFor(List.of(itemId));
                    MaterialGoals.track(needed, dimensionId, ContainerSearch.searchFilters());
                    // Closed, because the answer is in the world rather than in
                    // this window - the same thing clicking a result does.
                    screen.onClose();
                }));
    }

    /** The item's name as the player reads it, falling back to its id. */
    private static String displayName(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return itemId;
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == null ? itemId : new ItemStack(item).getHoverName().getString();
    }


    private static boolean isMaterialList(Screen screen) {
        return MATERIAL_LIST_GUI.equals(screen.getClass().getName());
    }

    // --- the search ---------------------------------------------------------

    /**
     * Looks for everything the schematic still needs, and highlights it.
     *
     * <p>Missing-only rather than everything: a material list is read while
     * building, and the items already in the player's inventory are not the
     * ones they are looking for. If nothing is missing - the schematic is
     * fully supplied - it falls back to the whole list, because a button that
     * silently does nothing reads as broken.
     */
    private static void run(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        if (!ClientTracker.isAvailable()) {
            say("No index here yet.", ChatFormatting.RED);
            return;
        }

        Map<String, Integer> needed = itemsNeeded(screen);
        if (needed.isEmpty()) {
            say("Could not read the material list", ChatFormatting.YELLOW);
            return;
        }
        List<String> wanted = new ArrayList<>(needed.keySet());

        String dimensionId = player.level().dimension().identifier().toString();
        String label = wanted.size() == 1
                ? "1 material" : wanted.size() + " materials";
        say("Looking for " + label + "...", ChatFormatting.GRAY);

        ClientTracker.containers(wanted, ContainerSearch.searchFilters(), MAX_CONTAINERS, "")
                .thenAccept(response -> client.execute(() -> {
                    List<QueryDto.ContainerHit> hits = response.hits();
                    if (hits.isEmpty()) {
                        say("Nothing indexed holds any of it", ChatFormatting.YELLOW);
                        return;
                    }
                    LocalPlayer now = Minecraft.getInstance().player;
                    if (now == null
                            || !now.level().dimension().identifier().toString().equals(dimensionId)) {
                        return;
                    }
                    ContainerHighlight.get().selectHits(hits, dimensionId, label);
                    ContainerHighlight.get().searchingFor(wanted);
                    // From here the highlight narrows itself: each material
                    // drops out as the player picks up enough of it, and the
                    // whole thing clears when the last one does.
                    MaterialGoals.track(needed, dimensionId, ContainerSearch.searchFilters());
                    // Closed, because the answer is in the world rather than in
                    // this window - the same thing clicking a result does.
                    screen.onClose();
                }));
    }

    /**
     * What the schematic needs, by registry id, and how much of each.
     *
     * <p>The count is what lets a material retire itself once the player is
     * carrying enough - see {@link MaterialGoals}. It is the entry's
     * <em>total</em>, not what Litematica currently calls missing: missing is a
     * snapshot against an inventory that is about to change, so it would go
     * stale the moment the player started acting on it.
     *
     * <p>Insertion-ordered, so the list keeps the order Litematica sorted it
     * into rather than a hash order that shuffles between presses.
     *
     * <p>Returns empty on any reflective failure, which the caller reports as
     * "could not read the material list" rather than as "you have everything".
     */
    private static Map<String, Integer> itemsNeeded(Screen screen) {
        try {
            Method getMaterialList = screen.getClass().getMethod("getMaterialList");
            Object materialList = getMaterialList.invoke(screen);
            if (materialList == null) return Map.of();

            List<?> entries = missingOnly(materialList);
            // Nothing missing means the schematic is already supplied. Falling
            // back to the whole list keeps the button from silently doing
            // nothing, which reads as broken.
            if (entries == null || entries.isEmpty()) entries = all(materialList);
            if (entries == null) return Map.of();

            Map<String, Integer> needed = new LinkedHashMap<>();
            for (Object entry : entries) {
                ItemStack stack = stackOf(entry);
                if (stack == null || stack.isEmpty()) continue;
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id == null) continue;

                int count = countOf(entry);
                // Several entries can share an item; the schematic needs the sum.
                needed.merge(id.toString(), count, Integer::sum);
                if (needed.size() >= QueryDto.ContainerRequest.MAX_ITEMS) break;
            }
            return needed;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A Litematica that renamed something, or a malilib that changed
            // shape. Not our crash to cause.
            ChestTracker.LOG.warn("Could not read Litematica's material list: {}", e.toString());
            return Map.of();
        }
    }

    /**
     * How many of an entry the schematic needs.
     *
     * <p>Falls back to one rather than zero: a material whose count could not
     * be read should still be looked for, and a goal of zero would retire it
     * immediately.
     */
    private static int countOf(Object entry) {
        try {
            Object total = entry.getClass().getMethod("getCountTotal").invoke(entry);
            if (total instanceof Integer count && count > 0) return count;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            // Fall through.
        }
        return 1;
    }

    private static List<?> missingOnly(Object materialList) {
        try {
            Method method = materialList.getClass()
                    .getMethod("getMaterialsMissingOnly", boolean.class);
            return (List<?>) method.invoke(materialList, true);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    private static List<?> all(Object materialList) {
        try {
            Method method = materialList.getClass().getMethod("getMaterialsAll");
            return (List<?>) method.invoke(materialList);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    private static ItemStack stackOf(Object entry) {
        try {
            Object stack = entry.getClass().getMethod("getStack").invoke(entry);
            return stack instanceof ItemStack item ? item : null;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    private static void say(String message, ChatFormatting colour) {
        ActionBar.say(Component.literal(message).withStyle(colour));
    }
}
