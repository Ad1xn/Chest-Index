package dev.adrian.chestindex.client;

import dev.adrian.chestindex.ChestIndex;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class LitematicaGui {

    private LitematicaGui() {}

    private static final String BUTTON_BASE = "fi.dy.masa.malilib.gui.button.ButtonBase";

    private static final String EXPORT_KEY = "litematica.gui.button.material_list.export";

    private static boolean warned;

    private static final java.util.Map<String, java.lang.reflect.AccessibleObject> MEMBERS =
            new java.util.HashMap<>();

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        public int right() {
            return x + width;
        }

        public boolean overlaps(Rect other) {
            return x < other.right() && other.x() < right()
                    && y < other.y() + other.height() && other.y() < y + height;
        }
    }

    public record Row(Rect anchor, String itemId, int needed) {}

    public static List<Row> rows(Object screen) {
        try {
            Object listWidget = invoke(screen, "getListWidget");
            if (listWidget == null) return List.of();

            Object raw = read(listWidget, "listWidgets");
            if (!(raw instanceof List<?> widgets)) return List.of();

            List<Row> rows = new ArrayList<>(widgets.size());
            for (Object widget : widgets) {
                Row row = rowOf(widget);
                if (row != null) rows.add(row);
            }
            return rows;
        } catch (RuntimeException e) {
            warn(e);
            return List.of();
        }
    }

    private static Row rowOf(Object widget) {
        Object entry = invoke(widget, "getEntry");
        if (entry == null) return null;

        Object stack = invoke(entry, "getStack");
        if (!(stack instanceof ItemStack item) || item.isEmpty()) return null;
        Identifier id = BuiltInRegistries.ITEM.getKey(item.getItem());
        if (id == null) return null;

        Rect anchor = buttonIn(widget);
        if (anchor == null) return null;

        Object total = invoke(entry, "getCountTotal");
        int needed = total instanceof Integer count && count > 0 ? count : 1;

        return new Row(anchor, id.toString(), needed);
    }

    private static Rect buttonIn(Object rowWidget) {
        Object raw = read(rowWidget, "subWidgets");
        if (!(raw instanceof List<?> subWidgets)) return null;

        Rect leftmost = null;
        for (Object sub : subWidgets) {
            if (!isButton(sub)) continue;
            if (Boolean.FALSE.equals(read(sub, "visible"))) continue;

            Rect bounds = boundsOf(sub);
            if (bounds == null) continue;

            if (leftmost == null || bounds.x() < leftmost.x()) leftmost = bounds;
        }
        return leftmost;
    }

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

    public static List<Rect> buttons(Object screen) {
        try {
            Object raw = read(screen, "buttons");
            if (!(raw instanceof List<?> buttons)) return List.of();

            List<Rect> found = new ArrayList<>(buttons.size());
            for (Object button : buttons) {
                if (!isButton(button)) continue;
                if (Boolean.FALSE.equals(read(button, "visible"))) continue;
                Rect bounds = boundsOf(button);
                if (bounds != null) found.add(bounds);
            }
            return found;
        } catch (RuntimeException e) {
            warn(e);
            return List.of();
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

    private static Object invoke(Object target, String name) {
        if (target == null) return null;
        String key = key(target, name);
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
            } catch (RuntimeException e) {
                warn(e);
                return null;
            }
        }
        return null;
    }

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
        ChestIndex.LOG.warn("Could not read Litematica's material list widgets, "
                + "so the search buttons will not be shown: {}", e.toString());
    }
}
