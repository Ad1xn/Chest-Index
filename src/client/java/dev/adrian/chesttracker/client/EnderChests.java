package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.core.util.BlockKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where the nearest ender chests are, read straight off the loaded world.
 *
 * <p>An item in the ender chest is somewhere the search cannot point at: the
 * contents follow the player, so there are no coordinates to walk to. What
 * there <em>is</em> is the next step - the nearest ender chest to open - and
 * that is a perfectly ordinary block the client can already see.
 *
 * <p>Read from the client's own chunks rather than from the index, and
 * deliberately so. The index is queried by item, and asking it for "every
 * container of this type" would be a new shape of request on the wire for one
 * feature. The chunks are already here, an ender chest is a block entity in
 * them, and nothing is sent to find out - the same constraint the whole
 * client-side index is built under.
 *
 * <p>The cost of that is honest and small: only chunks the player has loaded
 * are looked at, so an ender chest in a base across the world is not offered.
 * That is the right answer anyway. "Open your ender chest" is about the one in
 * the room, not the one three thousand blocks away.
 */
public final class EnderChests {

    private EnderChests() {}

    /**
     * How far out to look, in chunks.
     *
     * <p>Eight is a hundred and twenty-eight blocks, which comfortably covers
     * a base and stops well short of walking the whole render distance for a
     * block that is nearly always in the same room.
     */
    private static final int RADIUS_CHUNKS = 8;

    /** Enough to say where they are without filling the screen with boxes. */
    private static final int MAX_RESULTS = 12;

    /**
     * Packed positions of the ender chests near the player, nearest first.
     *
     * <p>Empty when there are none in the loaded world, which is a real answer
     * and one the caller has to say out loud - "it is in your ender chest" is
     * only useful next to one.
     */
    public static List<Long> nearby() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) return List.of();

        int centreX = player.getBlockX() >> 4;
        int centreZ = player.getBlockZ() >> 4;

        List<Long> found = new ArrayList<>();
        for (int dx = -RADIUS_CHUNKS; dx <= RADIUS_CHUNKS; dx++) {
            for (int dz = -RADIUS_CHUNKS; dz <= RADIUS_CHUNKS; dz++) {
                int chunkX = centreX + dx;
                int chunkZ = centreZ + dz;
                if (!level.hasChunk(chunkX, chunkZ)) continue;

                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                    // The block state, not the block entity's type: an ender
                    // chest and a trapped chest share a class hierarchy but not
                    // a block, and it is the block the player right-clicks.
                    if (!chunk.getBlockState(pos).is(Blocks.ENDER_CHEST)) continue;
                    if (!BlockKey.isRepresentable(pos.getX(), pos.getY(), pos.getZ())) continue;
                    found.add(BlockKey.pack(pos.getX(), pos.getY(), pos.getZ()));
                }
            }
        }
        if (found.isEmpty()) return List.of();

        long centre = BlockKey.pack(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        found.sort(Comparator.comparingDouble(pos -> BlockKey.distanceSq(centre, pos)));
        return found.size() <= MAX_RESULTS ? List.copyOf(found) : List.copyOf(found.subList(0, MAX_RESULTS));
    }
}
