package dev.adrian.chesttracker.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {

    @Accessor("LINES_SNIPPET")
    static RenderPipeline.Snippet chesttracker$linesSnippet() {
        throw new AssertionError();
    }
}
