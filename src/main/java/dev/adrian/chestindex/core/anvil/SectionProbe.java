package dev.adrian.chestindex.core.anvil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SectionProbe {
    public static final String SECTIONS_KEY = "sections";

    private record Section(String[] palette, String[] facings, long[] data, int bits) {}

    private final Map<Integer, Section> byY = new HashMap<>();

    private SectionProbe() {}

    public static SectionProbe of(NbtCompound chunk) {
        SectionProbe probe = new SectionProbe();
        if (chunk == null) return probe;

        for (NbtCompound section : chunk.getCompoundList(SECTIONS_KEY)) {
            NbtCompound states = section.getCompound("block_states");
            if (states == null) continue;

            List<NbtCompound> entries = states.getCompoundList("palette");
            if (entries.isEmpty()) continue;
            String[] palette = new String[entries.size()];
            String[] facings = new String[entries.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = entries.get(i).getString("Name");
                NbtCompound properties = entries.get(i).getCompound("Properties");
                if (properties != null) facings[i] = properties.getString("facing");
            }

            long[] data = states.getLongArray("data");
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
            probe.byY.put((int) section.getByte("Y", (byte) 0), new Section(palette, facings, data, bits));
        }
        return probe;
    }

    public String idAt(int x, int y, int z) {
        int entry = paletteIndex(x, y, z);
        return entry < 0 ? null : byY.get(y >> 4).palette[entry];
    }

    public String facingAt(int x, int y, int z) {
        int entry = paletteIndex(x, y, z);
        return entry < 0 ? null : byY.get(y >> 4).facings[entry];
    }

    private int paletteIndex(int x, int y, int z) {
        Section section = byY.get(y >> 4);
        if (section == null) return -1;
        if (section.palette.length == 1 || section.data == null) return 0;

        int index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
        int perLong = 64 / section.bits;
        int word = index / perLong;
        if (word >= section.data.length) return -1;
        int shift = (index % perLong) * section.bits;
        int value = (int) ((section.data[word] >>> shift) & ((1L << section.bits) - 1));
        return value < section.palette.length ? value : -1;
    }
}
