package dev.adrian.chestindex.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Where a block change on the <em>client's</em> copy of the world is announced.
 *
 * <p>{@code Level.setBlock} is the one place every block change funnels
 * through, on both sides, so the mixin that watches it is already common code.
 * The server half calls {@link dev.adrian.chestindex.server.Trackers}
 * directly; the client half cannot, because the code that wants to hear about
 * it lives in the client source set and nothing in {@code main} may reference
 * it.
 *
 * <p>Hence one slot for a listener, filled by the client at startup and left
 * empty on a dedicated server. That is the whole class.
 *
 * <h2>Why not a block-entity unload event</h2>
 *
 * <p>Fabric offers one, and it is the wrong hook: it also fires for every
 * container in a chunk being unloaded as the player walks away, which is not
 * the container going away. Watching {@code setBlock} distinguishes them
 * because a chunk unload does not set any blocks - only a break, an explosion,
 * a piston or a server-sent update does.
 */
public final class ClientBlockChanges {

    /** Told about a block change on a client level, or null. */
    public interface Listener {
        void onBlockChanged(Level level, BlockPos pos);
    }

    private static volatile Listener listener;

    private ClientBlockChanges() {}

    public static void listen(Listener value) {
        listener = value;
    }

    /** Called from the mixin. Silent and free when nothing is listening. */
    public static void fire(Level level, BlockPos pos) {
        Listener current = listener;
        if (current != null) current.onBlockChanged(level, pos);
    }
}
