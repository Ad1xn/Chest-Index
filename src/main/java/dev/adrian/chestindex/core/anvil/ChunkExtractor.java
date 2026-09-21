package dev.adrian.chestindex.core.anvil;

import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackDetail;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.store.StringPalette;
import dev.adrian.chestindex.core.util.BlockKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ChunkExtractor {
    public static final Set<String> CHUNK_KEYS =
            Set.of("xPos", "zPos", "Status", "DataVersion", "block_entities", "structures");

    private static final int MAX_NESTING = 8;

    private ChunkExtractor() {}

    public record ChunkContents(List<ContainerRecord> containers, List<StructureBox> structureBoxes) {}

    public record StructureBox(String structureId, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    public static ChunkContents extract(NbtCompound chunk, Set<String> trackedIds,
                                        int dimensionId, StringPalette palette, long tick) {
        List<ContainerRecord> containers = new ArrayList<>();

        for (NbtCompound blockEntity : chunk.getCompoundList("block_entities")) {
            String id = blockEntity.getString("id");
            if (id == null) continue;

            if (!isTracked(blockEntity, id, trackedIds)) continue;

            int x = blockEntity.getInt("x", Integer.MIN_VALUE);
            int y = blockEntity.getInt("y", Integer.MIN_VALUE);
            int z = blockEntity.getInt("z", Integer.MIN_VALUE);
            if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) continue;
            if (!BlockKey.isRepresentable(x, y, z)) continue;

            boolean unlooted = blockEntity.contains("LootTable") || blockEntity.contains("loot_table");
            Origin origin = unlooted ? Origin.NATURAL : Origin.UNKNOWN;

            List<StackEntry> contents = List.of();
            if (!unlooted) {
                List<StackEntry> found = new ArrayList<>();
                collectItems(blockEntity.getCompoundList("Items"), found, 0, palette);
                contents = found;
            }

            containers.add(new ContainerRecord(
                    BlockKey.pack(x, y, z),
                    dimensionId,
                    palette.intern(id),
                    origin,
                    null,
                    unlooted,
                    !unlooted,
                    PlainText.of(blockEntity.getString("CustomName")),
                    tick,
                    contents));
        }

        return new ChunkContents(containers, extractStructureBoxes(chunk));
    }

    private static boolean isTracked(NbtCompound blockEntity, String id, Set<String> trackedIds) {
        if (trackedIds != null) return trackedIds.contains(id);
        return blockEntity.contains("Items")
                || blockEntity.contains("LootTable")
                || blockEntity.contains("loot_table");
    }

    private static void collectItems(List<NbtCompound> items, List<StackEntry> out, int depth, StringPalette palette) {
        if (depth > MAX_NESTING) return;

        for (NbtCompound item : items) {
            String itemId = item.getString("id");
            if (itemId == null) continue;
            if (itemId.equals("minecraft:air") || itemId.equals("air")) continue;

            int count = item.getInt("count", item.getInt("Count", 1));

            NbtCompound components = item.getCompound("components");
            String customName = components == null ? null
                    : PlainText.of(components.getString("minecraft:custom_name"));

            out.add(new StackEntry(palette.intern(itemId), count, depth, customName,
                    detailsOf(components, customName, palette)));

            if (components != null) {
                for (NbtCompound slot : components.getCompoundList("minecraft:container")) {
                    NbtCompound nested = slot.getCompound("item");
                    if (nested != null) collectItems(List.of(nested), out, depth + 1, palette);
                }
                collectItems(components.getCompoundList("minecraft:bundle_contents"), out, depth + 1, palette);
            }
        }
    }

    private static List<Integer> detailsOf(NbtCompound components, String customName, StringPalette palette) {
        if (components == null && customName == null) return List.of();

        List<Integer> details = new ArrayList<>(2);
        if (components != null) {
            addEnchantments(components.getCompound("minecraft:enchantments"), details, palette);
            addEnchantments(components.getCompound("minecraft:stored_enchantments"), details, palette);
            addPotion(components.getCompound("minecraft:potion_contents"), details, palette);
            addPotion(components, "minecraft:potion_contents", details, palette);
            addLore(components, details, palette);
        }
        add(details, palette, StackDetail.name(customName));
        return details;
    }

    private static void addEnchantments(NbtCompound enchantments, List<Integer> details, StringPalette palette) {
        if (enchantments == null) return;

        NbtCompound levels = enchantments.getCompound("levels");
        NbtCompound source = levels != null ? levels : enchantments;
        for (String id : source.keys()) {
            if ("levels".equals(id) || "show_in_tooltip".equals(id)) continue;
            add(details, palette, StackDetail.enchantment(id));
        }
    }

    private static void addPotion(NbtCompound contents, List<Integer> details, StringPalette palette) {
        if (contents == null) return;
        add(details, palette, StackDetail.potion(contents.getString("potion")));
    }

    private static void addPotion(NbtCompound components, String key,
                                  List<Integer> details, StringPalette palette) {
        if (components.getCompound(key) != null) return;
        add(details, palette, StackDetail.potion(unquote(components.getString(key))));
    }

    private static void addLore(NbtCompound components, List<Integer> details, StringPalette palette) {
        List<Object> lines = components.getList("minecraft:lore");
        if (lines == null) return;

        int kept = 0;
        for (Object line : lines) {
            if (kept >= StackDetail.MAX_LORE_LINES) break;
            String text = PlainText.of(line == null ? null : line.toString());
            if (text == null) continue;
            add(details, palette, StackDetail.lore(text));
            kept++;
        }
    }

    private static void add(List<Integer> details, StringPalette palette, String detail) {
        if (detail == null) return;
        int id = palette.intern(detail);
        if (!details.contains(id)) details.add(id);
    }

    private static String unquote(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static List<StructureBox> extractStructureBoxes(NbtCompound chunk) {
        NbtCompound structures = chunk.getCompound("structures");
        if (structures == null) return List.of();
        NbtCompound starts = structures.getCompound("starts");
        if (starts == null) return List.of();

        List<StructureBox> boxes = new ArrayList<>();
        for (String structureId : starts.keys()) {
            NbtCompound start = starts.getCompound(structureId);
            if (start == null) continue;

            List<NbtCompound> pieces = start.getCompoundList("Children");
            if (pieces.isEmpty()) {
                addBox(boxes, structureId, start.getIntArray("BB"));
            } else {
                for (NbtCompound piece : pieces) {
                    addBox(boxes, structureId, piece.getIntArray("BB"));
                }
            }
        }
        return boxes;
    }

    private static void addBox(List<StructureBox> boxes, String structureId, int[] bb) {
        if (bb == null || bb.length != 6) return;
        boxes.add(new StructureBox(structureId, bb[0], bb[1], bb[2], bb[3], bb[4], bb[5]));
    }

    static final class PlainText {
        private PlainText() {}

        static String of(String raw) {
            if (raw == null || raw.isEmpty()) return null;

            String value = raw.trim();
            if (value.startsWith("{")) {
                int key = value.indexOf("\"text\"");
                if (key < 0) return value;
                int firstQuote = value.indexOf('"', value.indexOf(':', key) + 1);
                if (firstQuote < 0) return value;
                StringBuilder text = new StringBuilder();
                for (int i = firstQuote + 1; i < value.length(); i++) {
                    char c = value.charAt(i);
                    if (c == '\\' && i + 1 < value.length()) {
                        text.append(value.charAt(++i));
                    } else if (c == '"') {
                        return text.toString();
                    } else {
                        text.append(c);
                    }
                }
                return value;
            }
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
    }
}
