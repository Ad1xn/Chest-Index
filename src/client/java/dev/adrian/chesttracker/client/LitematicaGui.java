package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.ChestTracker;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Where things are on Litematica's material list, read off the real widgets.
 *
 * <p>This mod's buttons sit beside Litematica's own - one per row next to
 * {@code Ignore}, one beside {@code Export} - and that only works if their
 * positions come from those buttons rather than from numbers copied out of
 * Litematica's source. A hard-coded offset is a guess that a column width, a
 * font change or a translated label can invalidate silently, leaving a button
 * floating in the middle of somebody's list. Asking the widget where it is
 * cannot go stale.
 *
 * <h2>What is reflected, and why it is safe</h2>
 *
 * <p>Litematica and malilib are not build dependencies and must never become
 * them - they do not exist for every version this mod targets. So the GUI is
 * read through reflection, and every value that crosses back is a vanilla type,
 * a primitive or a string.
 *
 * <p>The surface is small and was checked against both supported pairs -
 * Litematica {@code 0.26.15} with malilib {@code 0.27.9} on 1.21.11, and
 * {@code 0.28.7} with {@code 0.29.5} on 26.2 - where it is identical:
 *
 * <pre>
 *   GuiListBase#getListWidget()          protected, walked up from the screen
 *   WidgetListBase.listWidgets           protected List, the visible rows
 *   WidgetContainer.subWidgets           protected List, a row's own buttons
 *   GuiBase.buttons                      private List, the screen's buttons
 *   WidgetBase#getX/getY/getWidth/getHeight   public
 *   WidgetListEntryBase#getEntry()       public
 *   ButtonBase.displayString/visible     protected
 * </pre>
 *
 * <p>Everything is wrapped and every failure is answered with "nothing here",
 * which shows up as our buttons not appearing. Taking somebody's schematic GUI
 * down because a field was renamed would not be an acceptable way to find out.
 */
public final class LitematicaGui {

    private LitematicaGui() {}

    private static final String BUTTON_BASE = "fi.dy.masa.malilib.gui.button.ButtonBase";

    /** Litematica's own keys, so the buttons are found in any language. */
    private static final String IGNORE_KEY = "litematica.gui.button.material_list.ignore";
    private static final String EXPORT_KEY = "litematica.gui.button.material_list.export";

    /** Logged once per session; a rename should be findable without being fatal. */
    private static boolean warned;

    /**
     * Resolved members, by the class they were found on and their name.
     *
     * <p>This runs for every row, every frame, on a screen somebody else is
     * drawing. Walking the class hierarchy and calling {@code setAccessible}
     * afresh each time would be a reflective lookup per widget per field per
     * frame for no gain: the answer cannot change while the game is running.
     * Misses are cached as {@code null} too, so a Litematica that has renamed
     * something does not pay for the failed walk sixty times a second.
     */
    private static final java.util.Map<String, java.lang.reflect.AccessibleObject> MEMBERS =
            new java.util.HashMap<>();

    /**
     * A rectangle on the screen: one of Litematica's widgets, or one of ours.
     */
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    /**
     * One row of the material list.
     *
     * @param anchor the row's {@code Ignore} button, which ours is placed to
     *               the left of
     * @param itemId the row's item, as a registry id
     * @param needed how many of it the schematic calls for, at least one
     */
    public record Row(Rect anchor, String itemId, int needed) {}

    /**
     * The rows currently drawn, with the button each of ours attaches to.
     *
     * <p>Only the rows malilib is actually showing: it rebuilds this list as
     * the player scrolls, so anything scrolled away is not in it and cannot be
     * drawn over the list's edge.
     */
    public static List<Row> rows(Object screen) {
        try {
            Object listWidget = invoke(screen, "getListWidget");
            if (listWidget == null) return List.of();

            Object raw = read(listWidget, "listWidgets");
            if (!(raw instanceof List<?> widgets)) return List.of();

            String ignoreLabel = I18n.get(IGNORE_KEY);
            List<Row> rows = new ArrayList<>(widgets.size());
            for (Object widget : widgets) {
                Row row = rowOf(widget, ignoreLabel);
                if (row != null) rows.add(row);
            }
            return rows;
        } catch (RuntimeException e) {
            warn(e);
            return List.of();
        }
    }

    private static Row rowOf(Object widget, String ignoreLabel) {
        Object entry = invoke(widget, "getEntry");
        if (entry == null) return null;

        Object stack = invoke(entry, "getStack");
        if (!(stack instanceof ItemStack item) || item.isEmpty()) return null;
        Identifier id = BuiltInRegistries.ITEM.getKey(item.getItem());
        if (id == null) return null;

        Rect anchor = buttonIn(widget, ignoreLabel);
        if (anchor == null) return null;

        Object total = invoke(entry, "getCountTotal");
        // At least one: a goal of zero would retire the material the instant it
        // was searched for.
        int needed = total instanceof Integer count && count > 0 ? count : 1;

        return new Row(anchor, id.toString(), needed);
    }

    /**
     * A row's {@code Ignore} button.
     *
     * <p>Matched on the label Litematica itself would render, resolved through
     * the game's own translation lookup - so this holds in every language
     * rather than only in English. If that fails the leftmost button in the row
     * is used, which is what {@code Ignore} is: the alternative, giving up,
     * would cost the whole feature over a renamed translation key.
     */
    private static Rect buttonIn(Object rowWidget, String ignoreLabel) {
        Object raw = read(rowWidget, "subWidgets");
        if (!(raw instanceof List<?> subWidgets)) return null;

        Rect leftmost = null;
        for (Object sub : subWidgets) {
            if (!isButton(sub)) continue;
            if (Boolean.FALSE.equals(read(sub, "visible"))) continue;

            Rect bounds = boundsOf(sub);
            if (bounds == null) continue;

            if (ignoreLabel.equals(read(sub, "displayString"))) return bounds;
            if (leftmost == null || bounds.x() < leftmost.x()) leftmost = bounds;
        }
        return leftmost;
    }

    /** Litematica's {@code Export} button, or null if it is not on this screen. */
    public static Rect exportButton(Object screen) {
        try {
            Object raw = read(screen, "buttons");
            if (!(raw instanceof List<?> buttons)) return null;

            String exportLabel = I18n.get(EXPORT_KEY);
            for (Object button : buttons) {
                if (!isButton(button)) continue;
                if (Boolean.FALSE.equals(read(button, "visible"))) continue;
                if (exportLabel.equals(read(button, "displayString"))) return boundsOf(button);
            }
            return null;
        } catch (RuntimeException e) {
            warn(e);
            return null;
        }
    }

    private static Rect boundsOf(Object widget) {
        Object x = invoke(widget, "getX");
        Object y = invoke(widget, "getY");
        Object width = invoke(widget, "getWidth");
        Object height = invoke(widget, "getHeight");
        if (x instanceof Integer px && y instanceof Integer py
                && width instanceof Integer pw && height instanceof Integer ph) {
            return new Rect(px, py, pw, ph);
        }
        return null;
    }

    private static boolean isButton(Object candidate) {
        if (candidate == null) return false;
        for (Class<?> type = candidate.getClass(); type != null; type = type.getSuperclass()) {
            if (BUTTON_BASE.equals(type.getName())) return true;
        }
        return false;
    }

    // --- reflection ---------------------------------------------------------

    /**
     * Calls a no-argument method, walking up from the object's own class.
     *
     * <p>Walked rather than asked for directly because the ones that matter are
     * {@code protected} on a base class - {@code getListWidget} lives on
     * {@code GuiListBase} - and {@link Class#getMethod} only finds public ones.
     */
    private static Object invoke(Object target, String name) {
        if (target == null) return null;
        String key = key(target, name);
        // Not computeIfAbsent: it does not store a null result, so a miss would
        // walk the hierarchy again on every frame - which is exactly the case
        // worth caching.
        if (!MEMBERS.containsKey(key)) MEMBERS.put(key, findMethod(target.getClass(), name));
        if (!(MEMBERS.get(key) instanceof Method method)) return null;
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return null;
        }
    }

    private static Method findMethod(Class<?> from, String name) {
        for (Class<?> type = from; type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException keepLooking) {
                // Declared further up, or not at all.
            } catch (RuntimeException e) {
                warn(e);
                return null;
            }
        }
        return null;
    }

    /** Reads a field, walking up the same way and for the same reason. */
    private static Object read(Object target, String name) {
        if (target == null) return null;
        String key = key(target, name);
        if (!MEMBERS.containsKey(key)) MEMBERS.put(key, findField(target.getClass(), name));
        if (!(MEMBERS.get(key) instanceof Field field)) return null;
        try {
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return null;
        }
    }

    private static Field findField(Class<?> from, String name) {
        for (Class<?> type = from; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException keepLooking) {
                // Declared further up, or not at all.
            } catch (RuntimeException e) {
                warn(e);
                return null;
            }
        }
        return null;
    }

    private static String key(Object target, String name) {
        return target.getClass().getName() + '#' + name;
    }

    private static void warn(Exception e) {
        if (warned) return;
        warned = true;
        ChestTracker.LOG.warn("Could not read Litematica's material list widgets, "
                + "so the search buttons will not be shown: {}", e.toString());
    }
}
