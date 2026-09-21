package dev.adrian.chestindex.mixin;

import dev.adrian.chestindex.server.Trackers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "setPlacedBy", at = @At("RETURN"))
    private void chestindex$afterPlaced(Level level, BlockPos pos, BlockState state,
                                          LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        Trackers.onBlockPlaced(level, pos, placer);
    }
}
