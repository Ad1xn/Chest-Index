package dev.adrian.chestindex.core.util;

import java.util.Collection;

public final class MonsterRooms {
    public static final String SPAWNER_ID = "minecraft:mob_spawner";
    public static final String CHEST_ID = "minecraft:chest";
    public static final int REACH = 3;

    private MonsterRooms() {}

    public static boolean nearSpawner(long chestPos, long spawnerPos) {
        return BlockKey.y(chestPos) == BlockKey.y(spawnerPos)
                && Math.abs(BlockKey.x(chestPos) - BlockKey.x(spawnerPos)) <= REACH
                && Math.abs(BlockKey.z(chestPos) - BlockKey.z(spawnerPos)) <= REACH;
    }

    public static boolean nearAnySpawner(long chestPos, Collection<Long> spawners) {
        for (long spawner : spawners) {
            if (nearSpawner(chestPos, spawner)) return true;
        }
        return false;
    }
}
