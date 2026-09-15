package dev.adrian.chestindex.client;

import dev.adrian.chestindex.core.util.BlockKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EnderChests {

    private EnderChests() {}

    private static final int RADIUS_CHUNKS = 8;

    private static final int MAX_RESULTS = 12;

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
