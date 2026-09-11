package dev.adrian.chesttracker.platform;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which block entities count as containers.
 *
 * <p>Live scanning does not need a list at all - {@code instanceof Container}
 * is exact, and covers modded containers for free. The offline region scanner
 * does need ids, because it reads serialised NBT with no classes involved.
 *
 * <p>Rather than try to instantiate every registered {@link BlockEntityType} to
 * ask whether it is a container, this ships the vanilla set and <em>learns</em>
 * additional ids from live scans. A modded container is therefore indexed
 * offline once it has been seen loaded at least once, and the learned set is
 * persisted alongside the index. The {@code ChunkExtractor} heuristic still
 * catches anything unlearned that serialises an {@code Items} list.
 */
public final class ContainerTypes {

    /**
     * Vanilla block entities that hold an inventory. Ender chests are included
     * deliberately even though the block stores nothing itself: the block is
     * worth showing on the map, and its contents come from player data.
     */
    private static final Set<String> VANILLA = Set.of(
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:barrel",
            "minecraft:shulker_box",
            "minecraft:ender_chest",
            "minecraft:hopper",
            "minecraft:dropper",
            "minecraft:dispenser",
            "minecraft:furnace",
            "minecraft:blast_furnace",
            "minecraft:smoker",
            "minecraft:brewing_stand",
            "minecraft:crafter",
            "minecraft:chiseled_bookshelf",
            "minecraft:decorated_pot",
            "minecraft:campfire",
            "minecraft:jukebox",
            "minecraft:lectern");

    private final Set<String> known = Collections.synchronizedSet(new LinkedHashSet<>(VANILLA));


    /**
     * Registry ids by block-entity type, worked out once each.
     *
     * <p>The id is asked for every container in every chunk that loads,
     * unloads or is re-read, and building it means joining a namespace and a
     * path into a fresh string every time. There are a few dozen types in a
     * game and they are fixed once the registries are frozen.
     */
    private static final java.util.Map<BlockEntityType<?>, String> IDS =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    /** Registry id of a block entity's type, or null if it is not registered. */
    public static String idOf(BlockEntity blockEntity) {
        BlockEntityType<?> type = blockEntity.getType();

        String cached = IDS.get(type);
        if (cached != null) return cached;

        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        if (key == null) return null;
        String id = key.toString();
        IDS.put(type, id);
        return id;
    }

    /** Exact test for a live block entity. Covers modded containers automatically. */
    public static boolean isContainer(BlockEntity blockEntity) {
        return blockEntity instanceof Container;
    }

    /**
     * Records that this block-entity type is a container, so the offline
     * scanner recognises it in chunks that were never loaded.
     *
     * @return true if this id was not already known
     */
    public boolean learn(String id) {
        return id != null && known.add(id);
    }

    /** Ids the offline scanner should treat as containers. */
    public Set<String> known() {
        synchronized (known) {
            return Set.copyOf(known);
        }
    }

    public boolean isKnown(String id) {
        return known.contains(id);
    }

    /** Restores learned ids from a saved index. */
    public void restore(Set<String> ids) {
        known.addAll(ids);
    }

    public static Set<String> vanilla() {
        return VANILLA;
    }
}
