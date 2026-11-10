package dev.adrian.chestindex.paper;

import dev.adrian.chestindex.core.util.WorldGenMarkers;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.BlockInventoryHolder;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LoadedBlocks implements WorldGenMarkers.Blocks {
    private static final Map<Material, String> IDS = new ConcurrentHashMap<>();

    private final World world;
    private final Chunk own;
    boolean missed;

    LoadedBlocks(World world, Chunk own) {
        this.world = world;
        this.own = own;
    }

    static String idOf(Material material) {
        return IDS.computeIfAbsent(material, m -> m.getKey().toString());
    }

    static boolean isContainer(BlockState state) {
        return state instanceof BlockInventoryHolder;
    }

    static String typeIdOf(Material material) {
        if (Tag.SHULKER_BOXES.isTagged(material)) return "minecraft:shulker_box";
        if (material == Material.SOUL_CAMPFIRE) return "minecraft:campfire";
        return idOf(material);
    }

    private Block blockAt(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return null;
        int cx = x >> 4;
        int cz = z >> 4;
        if (own != null && own.getX() == cx && own.getZ() == cz) return own.getBlock(x & 15, y, z & 15);
        if (!world.isChunkLoaded(cx, cz)) {
            missed = true;
            return null;
        }
        return world.getBlockAt(x, y, z);
    }

    @Override
    public String idAt(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return "minecraft:void_air";
        Block block = blockAt(x, y, z);
        return block == null ? null : idOf(block.getType());
    }

    @Override
    public String facingAt(int x, int y, int z) {
        Block block = blockAt(x, y, z);
        if (block == null) return null;
        BlockData data = block.getBlockData();
        return data instanceof Directional directional
                ? directional.getFacing().name().toLowerCase(Locale.ROOT)
                : null;
    }

    @Override
    public boolean blocksLid(int x, int y, int z) {
        Block block = blockAt(x, y, z);
        return block != null && !block.getCollisionShape().getBoundingBoxes().isEmpty();
    }
}
