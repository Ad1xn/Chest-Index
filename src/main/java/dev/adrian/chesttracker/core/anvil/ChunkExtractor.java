package dev.adrian.chesttracker.core.anvil;

import dev.adrian.chesttracker.core.model.ContainerRecord;
import dev.adrian.chesttracker.core.model.Origin;
import dev.adrian.chesttracker.core.model.StackDetail;
import dev.adrian.chesttracker.core.model.StackEntry;
import dev.adrian.chesttracker.core.store.StringPalette;
import dev.adrian.chesttracker.core.util.BlockKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns a chunk's on-disk NBT into {@link ContainerRecord}s.
 *
 * <p>The serialised form is richer than anything a client can observe: a chest
 * on disk carries its {@code Items} <em>and</em>, if worldgen has not been
 * rolled yet, its {@code LootTable}. So one pass yields contents and the
 * "generated, never opened" signal together.
 *
 * <p>Nested storage is flattened rather than kept as a tree, so a single search
 * pass finds a diamond inside a shulker box inside a chest. Depth is recorded
 * so the UI can say where it actually is, and so a query can exclude nesting.
 */
public final class ChunkExtractor {

    /** Root chunk keys worth parsing; everything else is skipped unread. */
    public static final Set<String> CHUNK_KEYS =
            Set.of("xPos", "zPos", "Status", "DataVersion", "block_entities", "structures");

    /** Deeper than this and the data is malicious or broken, not a real shulker chain. */
    private static final int MAX_NESTING = 8;

    private ChunkExtractor() {}

    /**
     * @param containers      containers found, already classified as far as the
     *                        chunk alone allows
     * @param structureBoxes  bounding boxes of structure pieces starting in this
     *                        chunk, for the origin classifier
     */
    public record ChunkContents(List<ContainerRecord> containers, List<StructureBox> structureBoxes) {}

    /** An inclusive structure-piece bounding box, as stored in {@code structures.starts}. */
    public record StructureBox(String structureId, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    /**
     * @param chunk       chunk NBT, ideally read with {@link #CHUNK_KEYS}
     * @param trackedIds  block-entity ids to index. Callers should supply this,
     *                    built from the block-entity registry so modded
     *                    containers are covered; null falls back to a heuristic
     * @param dimensionId palette id of the dimension
     * @param palette     interns item and container registry names
     * @param tick        value to record as {@code lastSeenTick}
     */
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

            // An unrolled loot table means worldgen placed this and nobody has
            // opened it. Zero false positives, though it says nothing about
            // generated chests that have already been looted.
            boolean unlooted = blockEntity.contains("LootTable") || blockEntity.contains("loot_table");
            Origin origin = unlooted ? Origin.NATURAL : Origin.UNKNOWN;

            // A container whose loot has not been rolled yet holds nothing on
            // disk, because its items do not exist until someone opens it.
            // Recording that as a known-empty container would be a lie, and
            // would put a misleading entry in front of the player - so its
            // contents are marked unknown and it contributes nothing to the
            // item index. Its location is still worth knowing.
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

    /**
     * Whether a block entity is a container we should index.
     *
     * <p>The heuristic branch is a fallback for callers with no registry to
     * hand. It cannot be "has an {@code Items} list": an unlooted loot chest has
     * no items yet, only a loot table, and those are precisely the containers
     * worth finding. Genuinely empty containers are still missed, which is why
     * real callers pass an explicit id set.
     */
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
            // Serialised inventories do not normally write empty slots, but a
            // datapack or an older world can, and an "Air" row in the grid is
            // pure noise. The live reader drops these too.
            if (itemId.equals("minecraft:air") || itemId.equals("air")) continue;

            // 1.20.5+ writes a lowercase int `count`; older worlds wrote a byte
            // `Count`. Both targets are post-1.20.5, but reading either costs
            // nothing and keeps old fixture worlds working.
            int count = item.getInt("count", item.getInt("Count", 1));

            NbtCompound components = item.getCompound("components");
            String customName = components == null ? null
                    : PlainText.of(components.getString("minecraft:custom_name"));

            out.add(new StackEntry(palette.intern(itemId), count, depth, customName,
                    detailsOf(components, customName, palette)));

            if (components != null) {
                // A shulker box carries its contents as slot/item pairs.
                for (NbtCompound slot : components.getCompoundList("minecraft:container")) {
                    NbtCompound nested = slot.getCompound("item");
                    if (nested != null) collectItems(List.of(nested), out, depth + 1, palette);
                }
                // A bundle carries a plain list of items.
                collectItems(components.getCompoundList("minecraft:bundle_contents"), out, depth + 1, palette);
            }
        }
    }

    /**
     * What tells this stack apart from another of the same item, off the disk.
     *
     * <p>The same details the live reader takes off the components of a real
     * {@code ItemStack}, read here out of the serialised form of those same
     * components - because most of a world has never been loaded, and an index
     * that only knew about enchantments in chunks somebody had visited would
     * answer "no" to almost every search for one.
     *
     * <p>Every shape is read defensively. This is somebody's world file, it may
     * have been written by an older version or edited by a tool, and the right
     * answer to a component that does not look the way it should is to skip it.
     */
    private static List<Integer> detailsOf(NbtCompound components, String customName, StringPalette palette) {
        // Most stacks in a world are plain cobblestone with nothing to record,
        // and this runs once per stack in every chunk read off disk.
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

    /**
     * The enchantments on one stack.
     *
     * <p>Written either as the ids directly or wrapped in a {@code levels}
     * compound, depending on the version that wrote the chunk. Both are read:
     * a world is not re-serialised just because the format moved on, so both
     * shapes are still out there in chunks nobody has touched since.
     */
    private static void addEnchantments(NbtCompound enchantments, List<Integer> details, StringPalette palette) {
        if (enchantments == null) return;

        NbtCompound levels = enchantments.getCompound("levels");
        NbtCompound source = levels != null ? levels : enchantments;
        for (String id : source.keys()) {
            // The wrapper's own keys are not enchantments.
            if ("levels".equals(id) || "show_in_tooltip".equals(id)) continue;
            add(details, palette, StackDetail.enchantment(id));
        }
    }

    private static void addPotion(NbtCompound contents, List<Integer> details, StringPalette palette) {
        if (contents == null) return;
        add(details, palette, StackDetail.potion(contents.getString("potion")));
    }

    /** The short form, where the component is the potion id on its own. */
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

            // Pieces only, not the structure's own BB. A village's overall box
            // is enormous - it spans the whole settlement including the fields,
            // the paths and everything a player has since built among them - so
            // testing against it declares a base built in a village to be
            // generated. The pieces are the actual buildings.
            //
            // A structure with no children has nothing finer to offer, and its
            // own box is the piece.
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

    /**
     * Reduces a Minecraft text component to searchable plain text.
     *
     * <p>Core cannot depend on Minecraft's component parser, and full fidelity
     * is not needed here: this feeds search matching and a fallback label, while
     * the client renders the real styled name from the live block entity. So
     * handle the shapes that actually occur - a bare string, a quoted string, or
     * a simple {@code {"text":"…"}} object - and otherwise pass the value
     * through untouched rather than guessing.
     */
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
