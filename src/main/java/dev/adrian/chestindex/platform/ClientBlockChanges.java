package dev.adrian.chestindex.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class ClientBlockChanges {
    public interface Listener {
        void onBlockChanged(Level level, BlockPos pos);
    }

    public interface PlacementListener {
        void onBlockPlaced(Level level, BlockPos pos, LivingEntity placer);
    }

    private static volatile Listener listener;
    private static volatile PlacementListener placementListener;

    private ClientBlockChanges() {}

    public static void listen(Listener value) {
        listener = value;
    }

    public static void listenForPlacements(PlacementListener value) {
        placementListener = value;
    }

    public static void firePlaced(Level level, BlockPos pos, LivingEntity placer) {
        PlacementListener current = placementListener;
        if (current != null) current.onBlockPlaced(level, pos, placer);
    }

    public static void fire(Level level, BlockPos pos) {
        Listener current = listener;
        if (current != null) current.onBlockChanged(level, pos);
    }
}
