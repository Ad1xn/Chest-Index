package dev.adrian.chestindex.core.util;

import java.util.Set;

public final class ShulkerMachines {
    private static final Set<String> PLACERS_AND_BREAKERS = Set.of(
            "minecraft:dispenser", "minecraft:piston", "minecraft:sticky_piston");

    private static final String HOPPER = "minecraft:hopper";
    private static final String DROPPER = "minecraft:dropper";

    private ShulkerMachines() {}

    public static boolean isShulkerBox(String typeId) {
        return typeId != null && typeId.startsWith("minecraft:") && typeId.endsWith("shulker_box");
    }

    public static boolean inMachine(long pos, WorldGenMarkers.Blocks blocks) {
        int x = BlockKey.x(pos);
        int y = BlockKey.y(pos);
        int z = BlockKey.z(pos);

        boolean wired = false;
        for (Neighbours.Side side : Neighbours.Side.values()) {
            int nx = x + side.dx;
            int ny = y + side.dy;
            int nz = z + side.dz;
            String id = blocks.idAt(nx, ny, nz);
            if (id == null) continue;

            boolean facesIn = Neighbours.Side.of(blocks.facingAt(nx, ny, nz)) == side.opposite();
            if (facesIn && PLACERS_AND_BREAKERS.contains(id)) return true;
            if (id.equals(HOPPER) && (facesIn || side == Neighbours.Side.DOWN)) wired = true;
            if (id.equals(DROPPER) && facesIn) wired = true;
        }
        if (!wired) return false;

        Neighbours.Side lid = Neighbours.Side.of(blocks.facingAt(x, y, z));
        if (lid == null) lid = Neighbours.Side.UP;
        return blocks.blocksLid(x + lid.dx, y + lid.dy, z + lid.dz);
    }
}
