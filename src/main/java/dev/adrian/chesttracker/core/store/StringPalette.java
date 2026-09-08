package dev.adrian.chesttracker.core.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StringPalette {
    private final Map<String, Integer> toId = new HashMap<>();
    private final List<String> byId = new ArrayList<>();

    public int intern(String value) {
        Integer existing = toId.get(value);
        if (existing != null) return existing;
        int id = byId.size();
        byId.add(value);
        toId.put(value, id);
        return id;
    }

    public int lookup(String value) {
        return toId.getOrDefault(value, -1);
    }

    public String value(int id) {
        return (id < 0 || id >= byId.size()) ? null : byId.get(id);
    }

    public int size() {
        return byId.size();
    }

    public List<String> entries() {
        return List.copyOf(byId);
    }

    public List<String> view() {
        return Collections.unmodifiableList(byId);
    }

    public static StringPalette fromEntries(List<String> entries) {
        StringPalette palette = new StringPalette();
        for (String entry : entries) palette.intern(entry);
        return palette;
    }
}
