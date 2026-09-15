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

@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"))
    private void chestindex$afterSetBlock(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        Level level = (Level) (Object) this;
        if (level.isClientSide()) {
            ClientBlockChanges.fire(level, pos);
            return;
        }
        Trackers.onBlockChanged(level, pos);
    }
}
