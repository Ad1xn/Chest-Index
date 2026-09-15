package dev.adrian.chestindex.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Vanilla's own description of how a line is drawn.
 *
 * <p>The snippet carries the shaders, the vertex format, the blend function and
 * the uniforms that lines need - everything except the depth test. Borrowing it
 * and overriding that one thing is the whole of the see-through marker; writing
 * the pipeline out by hand instead would mean copying a dozen settings that
 * differ between the two targets and go stale on the next one.
 *
 * <p>Private static on both targets, hence the accessor.
 */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {

    @Accessor("LINES_SNIPPET")
    static RenderPipeline.Snippet chestindex$linesSnippet() {
        throw new AssertionError();
    }

    /**
     * The same, for solid geometry.
     *
     * <p>{@code POSITION_COLOR}, quads, translucent blending and no culling -
     * which is exactly a box the camera may be standing inside. Vanilla uses it
     * for the debug renderer's filled boxes; a marker cube is the same shape
     * drawn for a better reason.
     */
    @Accessor("DEBUG_FILLED_SNIPPET")
    static RenderPipeline.Snippet chestindex$filledSnippet() {
        throw new AssertionError();
    }
}
