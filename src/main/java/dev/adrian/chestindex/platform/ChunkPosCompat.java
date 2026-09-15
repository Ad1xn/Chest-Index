package dev.adrian.chestindex.platform;

import net.minecraft.world.level.ChunkPos;

public final class ChunkPosCompat {

    private ChunkPosCompat() {}

    public static int x(ChunkPos pos) {
        //? if >=26.1 {
        /*return pos.x();
        *///?} else {
        return pos.x;
        //?}
    }

    public static int z(ChunkPos pos) {
        //? if >=26.1 {
        /*return pos.z();
        *///?} else {
        return pos.z;
        //?}
    }
}
