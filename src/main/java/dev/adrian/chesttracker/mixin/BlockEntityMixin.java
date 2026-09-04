package dev.adrian.chesttracker.mixin;

import dev.adrian.chesttracker.server.Trackers;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {

    @Inject(method = "setChanged()V", at = @At("RETURN"))
    private void chesttracker$afterSetChanged(CallbackInfo ci) {
        Trackers.onContainerChanged((BlockEntity) (Object) this);
    }
}
