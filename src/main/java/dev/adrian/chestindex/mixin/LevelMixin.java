package dev.adrian.chestindex.mixin;

import dev.adrian.chestindex.platform.ClientBlockChanges;
import dev.adrian.chestindex.server.Trackers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes containers from the index the moment they stop existing.
 *
 * <p>Hooked at {@code setBlock} rather than on a break event because every
 * block change funnels through here: creepers, TNT, pistons, fire and
 * worldedit-style bulk changes included. Injecting at RETURN means the world
 * already reflects the change, so the hook can simply ask what is there now.
 *
 * <p>Both sides run this method, and both have an index that can be wrong
 * about a chest that no longer exists - the server's, and a client's own copy
 * on a vanilla server. They are told through different routes only because one
 * of them lives in a source set this file may not import from.
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"))
    private void chestindex$afterSetBlock(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return; // The change did not take effect.
        Level level = (Level) (Object) this;
        // Both sides come through here. A client connected to a vanilla server
        // keeps an index of its own, and a chest broken in front of it has to
        // leave that index for the same reason it leaves the server's.
        if (level.isClientSide()) {
            ClientBlockChanges.fire(level, pos);
            return;
        }
        Trackers.onBlockChanged(level, pos);
    }
}
