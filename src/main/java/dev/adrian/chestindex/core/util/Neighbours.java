package dev.adrian.chestindex.core.util;

import java.util.Set;

public final class Neighbours {
    public enum Side {
        DOWN("down", 0, -1, 0), UP("up", 0, 1, 0), NORTH("north", 0, 0, -1),
        SOUTH("south", 0, 0, 1), WEST("west", -1, 0, 0), EAST("east", 1, 0, 0);

        public final String id;
        public final int dx;
        public final int dy;
        public final int dz;

        Side(String id, int dx, int dy, int dz) {
            this.id = id;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public Side opposite() {
            return values()[ordinal() ^ 1];
        }

        public static Side of(String id) {
            if (id == null) return null;
            for (Side side : values()) {
                if (side.id.equals(id)) return side;
            }
            return null;
        }
    }

    private static final Set<String> OPEN = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:water",
            "minecraft:lava", "minecraft:light", "minecraft:redstone_wire", "minecraft:tripwire",
            "minecraft:lever", "minecraft:short_grass", "minecraft:fern", "minecraft:vine",
            "minecraft:glow_lichen", "minecraft:sculk_vein", "minecraft:fire", "minecraft:soul_fire");

    private static final String[] OPEN_SUFFIXES = {
            "torch", "_button", "_sign", "_banner", "rail", "_pressure_plate", "_sapling", "_tulip",
            "_flower", "_mushroom", "_coral_fan"};

    private Neighbours() {}

    public static boolean blocksLid(String blockId) {
        if (blockId == null || OPEN.contains(blockId)) return false;
        for (String suffix : OPEN_SUFFIXES) {
            if (blockId.endsWith(suffix)) return false;
        }
        return true;
    }
}
