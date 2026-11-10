package dev.adrian.chestindex.paper;

import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.core.util.BuiltEvidence;
import dev.adrian.chestindex.core.util.MonsterRooms;
import dev.adrian.chestindex.core.util.ShulkerMachines;
import dev.adrian.chestindex.core.util.WorldGenMarkers;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.Lootable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class LiveScan {
    private final Tracker tracker;

    LiveScan(Tracker tracker) {
        this.tracker = tracker;
    }

    record ChunkScan(int found, int removed) {}

    ChunkScan scanChunk(Chunk chunk, String dimensionId) {
        World world = chunk.getWorld();
        long chunkKey = BlockKey.chunkKey(chunk.getX(), chunk.getZ());
        BlockState[] states = chunk.getTileEntities(false);

        if (states.length == 0 && tracker.index(dimensionId).positionsInChunk(chunkKey).isEmpty()) {
            return new ChunkScan(0, 0);
        }

        long tick = world.getGameTime();
        Set<Long> actual = new HashSet<>();
        int found = 0;
        for (BlockState state : states) {
            if (!LoadedBlocks.isContainer(state)) continue;
            int x = state.getX();
            int y = state.getY();
            int z = state.getZ();
            if (!BlockKey.isRepresentable(x, y, z)) continue;

            String typeId = LoadedBlocks.typeIdOf(state.getType());
            tracker.learnType(typeId);
            long key = BlockKey.pack(x, y, z);
            actual.add(key);
            tracker.record(dimensionId, read(world, chunk, state, key, typeId, dimensionId, tick, null));
            found++;
        }

        return new ChunkScan(found, tracker.reconcileChunk(dimensionId, chunkKey, actual));
    }

    boolean refreshIfLoaded(World world, String dimensionId, long pos) {
        int x = BlockKey.x(pos);
        int y = BlockKey.y(pos);
        int z = BlockKey.z(pos);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return true;

        Block block = world.getBlockAt(x, y, z);
        BlockState state = block.getState(false);
        if (!LoadedBlocks.isContainer(state)) {
            tracker.remove(dimensionId, pos);
            return false;
        }

        String typeId = LoadedBlocks.typeIdOf(state.getType());
        tracker.learnType(typeId);
        tracker.record(dimensionId, read(world, null, state, pos, typeId, dimensionId, world.getGameTime(), null));
        return true;
    }

    void placed(Block block, String dimensionId, UUID placer) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        if (!BlockKey.isRepresentable(x, y, z)) return;

        BlockState state = block.getState(false);
        if (!LoadedBlocks.isContainer(state)) return;

        String typeId = LoadedBlocks.typeIdOf(state.getType());
        tracker.learnType(typeId);
        long key = BlockKey.pack(x, y, z);

        ContainerRecord existing = tracker.index(dimensionId).get(key);
        if (existing != null) {
            tracker.record(dimensionId, existing.withOrigin(Origin.PLAYER_PLACED, placer));
            return;
        }
        tracker.record(dimensionId, new ContainerRecord(key, tracker.palette().intern(dimensionId),
                tracker.palette().intern(typeId), Origin.PLAYER_PLACED, placer, false, true, null,
                block.getWorld().getGameTime(), List.of()));
    }

    private ContainerRecord read(World world, Chunk chunk, BlockState state, long pos, String typeId,
                                 String dimensionId, long tick, UUID owner) {
        int dimId = tracker.palette().intern(dimensionId);
        int typeKey = tracker.palette().intern(typeId);

        if (state instanceof Lootable lootable && lootable.getLootTable() != null) {
            return new ContainerRecord(pos, dimId, typeKey, Origin.NATURAL, owner,
                    true, false, null, tick, List.of());
        }

        LoadedBlocks blocks = new LoadedBlocks(world, chunk);
        boolean generated = naturalityOf(blocks, pos, dimensionId, typeId);
        List<StackEntry> contents = ItemReader.read(contentsOf(state), tracker.palette());
        ContainerRecord record = new ContainerRecord(pos, dimId, typeKey, ContainerRecord.decided(generated), owner,
                false, true, null, tick, contents, !generated);

        ContainerRecord settled = BuiltEvidence.settle(record, blocks);
        if (ShulkerMachines.isShulkerBox(typeId)) {
            settled = settled.inMachine(ShulkerMachines.inMachine(pos, blocks));
        }
        return settled;
    }

    private static ItemStack[] contentsOf(BlockState state) {
        if (state instanceof Chest chest) return chest.getBlockInventory().getContents();
        return ((BlockInventoryHolder) state).getInventory().getContents();
    }

    private boolean naturalityOf(LoadedBlocks blocks, long pos, String dimensionId, String typeId) {
        ContainerRecord known = tracker.index(dimensionId).get(pos);
        if (known != null && !known.isPlacementGuess()) return false;
        if (tracker.originChecked(dimensionId, pos)) return false;

        Verdict verdict = check(blocks, pos, typeId);
        if (verdict != Verdict.UNSURE) tracker.markOriginChecked(dimensionId, pos);
        return verdict == Verdict.GENERATED;
    }

    enum Verdict { GENERATED, PLACED, UNSURE }

    static Verdict check(LoadedBlocks blocks, long pos, String typeId) {
        if (MonsterRooms.CHEST_ID.equals(typeId) && nearSpawner(blocks, pos)) return Verdict.GENERATED;
        if (WorldGenMarkers.surroundedByWorldGen(pos, blocks)) return Verdict.GENERATED;
        return blocks.missed ? Verdict.UNSURE : Verdict.PLACED;
    }

    private static final String SPAWNER = LoadedBlocks.idOf(Material.SPAWNER);

    private static boolean nearSpawner(LoadedBlocks blocks, long pos) {
        int x = BlockKey.x(pos);
        int y = BlockKey.y(pos);
        int z = BlockKey.z(pos);
        for (int dx = -MonsterRooms.REACH; dx <= MonsterRooms.REACH; dx++) {
            for (int dz = -MonsterRooms.REACH; dz <= MonsterRooms.REACH; dz++) {
                if (SPAWNER.equals(blocks.idAt(x + dx, y, z + dz))) return true;
            }
        }
        return false;
    }
}
