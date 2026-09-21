package dev.adrian.chestindex.mixin;

import dev.adrian.chestindex.server.Trackers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {
    @Inject(
            method = "setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("RETURN"))
    private static void chestindex$afterSetChanged(Level level, BlockPos pos, BlockState state,
                                                   CallbackInfo ci) {
        Trackers.onContainerChanged(level, pos);
    }
}
