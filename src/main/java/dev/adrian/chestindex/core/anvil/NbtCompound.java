package dev.adrian.chestindex.core.anvil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NbtCompound {

    private final Map<String, Object> values;

    public NbtCompound() {
        this.values = new LinkedHashMap<>();
    }

    public NbtCompound(Map<String, Object> values) {
        this.values = values;
    }

    void put(String key, Object value) {
        values.put(key, value);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int size() {
        return values.size();
    }

    public Object raw(String key) {
        return values.get(key);
    }

    public String getString(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String s ? s : fallback;
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public int getInt(String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    public long getLong(String key, long fallback) {
        Object value = values.get(key);
        return value instanceof Number n ? n.longValue() : fallback;
    }

    public byte getByte(String key, byte fallback) {
        Object value = values.get(key);
        return value instanceof Number n ? n.byteValue() : fallback;
    }

    public boolean getBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Number n ? n.byteValue() != 0 : fallback;
    }

    public NbtCompound getCompound(String key) {
        Object value = values.get(key);
        return value instanceof NbtCompound c ? c : null;
    }

    public int[] getIntArray(String key) {
        Object value = values.get(key);
        return value instanceof int[] a ? a : null;
    }

    public long[] getLongArray(String key) {
        Object value = values.get(key);
        return value instanceof long[] a ? a : null;
    }

    @SuppressWarnings("unchecked")
    public List<Object> getList(String key) {
        Object value = values.get(key);
        return value instanceof List<?> l ? (List<Object>) l : List.of();
    }

    public List<NbtCompound> getCompoundList(String key) {
        List<Object> raw = getList(key);
        if (raw.isEmpty()) return List.of();
        List<NbtCompound> compounds = new java.util.ArrayList<>(raw.size());
        for (Object element : raw) {
            if (element instanceof NbtCompound c) compounds.add(c);
        }
        return compounds;
    }

    @Override
    public String toString() {
        return "NbtCompound" + values.keySet();
    }
}
