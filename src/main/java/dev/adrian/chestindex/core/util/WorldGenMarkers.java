package dev.adrian.chestindex.core.util;

import java.util.Set;

public final class WorldGenMarkers {
    public enum Strength { NONE, WEAK, STRONG }

    public static final int WEAK_NEEDED = 4;

    private static final Set<String> STRONG = Set.of(
            "minecraft:spawner",
            "minecraft:trial_spawner",
            "minecraft:vault",
            "minecraft:end_portal_frame",
            "minecraft:reinforced_deepslate",
            "minecraft:infested_stone",
            "minecraft:infested_cobblestone",
            "minecraft:infested_stone_bricks",
            "minecraft:infested_mossy_stone_bricks",
            "minecraft:infested_cracked_stone_bricks",
            "minecraft:infested_chiseled_stone_bricks",
            "minecraft:infested_deepslate");

    private static final Set<String> WEAK = Set.of(
            "minecraft:mossy_cobblestone",
            "minecraft:mossy_stone_bricks",
            "minecraft:cracked_stone_bricks",
            "minecraft:cobweb",
            "minecraft:nether_bricks",
            "minecraft:cracked_nether_bricks",
            "minecraft:nether_brick_fence",
            "minecraft:nether_brick_stairs",
            "minecraft:cracked_polished_blackstone_bricks",
            "minecraft:gilded_blackstone",
            "minecraft:cracked_deepslate_bricks",
            "minecraft:cracked_deepslate_tiles",
            "minecraft:prismarine",
            "minecraft:prismarine_bricks",
            "minecraft:dark_prismarine",
            "minecraft:purpur_block",
            "minecraft:purpur_pillar",
            "minecraft:end_stone_bricks",
            "minecraft:chiseled_tuff",
            "minecraft:tuff_bricks",
            "minecraft:chiseled_tuff_bricks");

    private WorldGenMarkers() {}

    public static Strength of(String blockId) {
        if (blockId == null) return Strength.NONE;
        if (STRONG.contains(blockId)) return Strength.STRONG;
        if (WEAK.contains(blockId)) return Strength.WEAK;
        return Strength.NONE;
    }

    public static boolean suggestsGenerated(int strong, int weak) {
        return strong > 0 || weak >= WEAK_NEEDED;
    }

    public interface Blocks {
        String idAt(int x, int y, int z);
    }

    public static boolean surroundedByWorldGen(long pos, Blocks blocks) {
        int x = BlockKey.x(pos);
        int y = BlockKey.y(pos);
        int z = BlockKey.z(pos);
        int weak = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    switch (of(blocks.idAt(x + dx, y + dy, z + dz))) {
                        case STRONG -> { return true; }
                        case WEAK -> weak++;
                        case NONE -> { }
                    }
                }
            }
        }
        return suggestsGenerated(0, weak);
    }
}
