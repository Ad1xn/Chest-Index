package dev.adrian.chestindex.core.util;

import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BuiltEvidence {
    public static final int MOST_LOOT_OF_ONE_ITEM = 64;

    private static final Set<String> REDSTONE_PARTS = Set.of(
            "minecraft:hopper", "minecraft:comparator", "minecraft:dropper",
            "minecraft:observer", "minecraft:crafter");

    private BuiltEvidence() {}

    public static boolean inContents(List<StackEntry> contents) {
        Map<Integer, Integer> totals = null;
        for (StackEntry entry : contents) {
            if (entry.isNested()) return true;
            if (entry.count() > MOST_LOOT_OF_ONE_ITEM) return true;
            if (totals == null) totals = new HashMap<>();
            if (totals.merge(entry.itemId(), entry.count(), Integer::sum) > MOST_LOOT_OF_ONE_ITEM) return true;
        }
        return false;
    }

    public static boolean nextToRedstone(long pos, WorldGenMarkers.Blocks blocks) {
        int x = BlockKey.x(pos);
        int y = BlockKey.y(pos);
        int z = BlockKey.z(pos);
        for (Neighbours.Side side : Neighbours.Side.values()) {
            String id = blocks.idAt(x + side.dx, y + side.dy, z + side.dz);
            if (id != null && REDSTONE_PARTS.contains(id)) return true;
        }
        return false;
    }

    public static ContainerRecord settle(ContainerRecord container, WorldGenMarkers.Blocks blocks) {
        if (container.unlooted()) return container;
        if (container.origin() == Origin.PLAYER_PLACED && !container.originInferred()) return container;
        if (inContents(container.contents()) || (blocks != null && nextToRedstone(container.pos(), blocks))) {
            return container.withOrigin(Origin.PLAYER_PLACED, null);
        }
        return container;
    }
}
