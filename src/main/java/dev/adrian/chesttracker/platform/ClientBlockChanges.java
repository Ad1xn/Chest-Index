package dev.adrian.chesttracker.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class ClientBlockChanges {

    public interface Listener {
        void onBlockChanged(Level level, BlockPos pos);
    }

    private static volatile Listener listener;

    private ClientBlockChanges() {}

    public static void listen(Listener value) {
        listener = value;
    }

    public static void fire(Level level, BlockPos pos) {
        Listener current = listener;
        if (current != null) current.onBlockChanged(level, pos);
    }
}
