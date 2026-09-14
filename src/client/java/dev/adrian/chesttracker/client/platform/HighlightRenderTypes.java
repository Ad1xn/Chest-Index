package dev.adrian.chesttracker.client.platform;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.adrian.chesttracker.mixin.client.RenderPipelinesAccessor;
import dev.adrian.chesttracker.mixin.client.RenderTypeInvoker;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * The two ways this mod draws a line: through the world, and against it.
 *
 * <p>The box around a container has to be visible through the wall it is behind
 * - a marker you can only see once you can already see the chest is telling you
 * something you no longer need to know. The trail above it must <em>not</em> be:
 * a column that ignores the terrain reads as floating in front of the landscape
 * rather than standing somewhere in it, and at that point it has stopped saying
 * where the container is and is only saying which direction to look.
 *
 * <p>Vanilla has no see-through line type, so one is built here: vanilla's own
 * line pipeline with the depth test taken out and depth writes left off, so the
 * marker neither hides behind the world nor stamps itself into the depth buffer
 * for everything drawn afterwards to hide behind.
 *
 * <p>Built once, lazily, on the render thread. Not in a static initialiser:
 * this touches the render pipeline, and the class must not be loaded while the
 * game is still starting up.
 */
public final class HighlightRenderTypes {

    private HighlightRenderTypes() {}

    private static RenderType throughWalls;

    /**
     * Lines drawn wherever they are, whatever is in front of them.
     *
     * <p>Falls back to the ordinary line type if the pipeline cannot be built.
     * A marker that is merely occluded is worth far more than a crash on the
     * render thread, and this is the one piece of the highlight that reaches
     * past the API into vanilla's own rendering.
     */
    public static RenderType throughWalls() {
        if (!dev.adrian.chesttracker.config.ChestTrackerConfig.get().highlightThroughWalls) {
            return occluded();
        }
        if (throughWalls == null) {
            throughWalls = build();
        }
        return throughWalls;
    }

    /** Lines the world can stand in front of - vanilla's own. */
    public static RenderType occluded() {
        return RenderTypes.lines();
    }

    private static RenderType build() {
        try {
            RenderPipeline pipeline = RenderPipeline
                    .builder(RenderPipelinesAccessor.chesttracker$linesSnippet())
                    .withLocation("pipeline/chest_tracker_lines_through_walls")
                    //? if >=26.1 {
                    /*.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
                            com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS, false))
                    *///?} else {
                    .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    //?}
                    .build();

            return RenderTypeInvoker.chesttracker$create("chest_tracker:lines_through_walls",
                    RenderSetup.builder(pipeline).createRenderSetup());
        } catch (Throwable t) {
            dev.adrian.chesttracker.ChestTracker.LOG.warn(
                    "Could not build the see-through marker type, falling back to plain lines: {}",
                    t.toString());
            return RenderTypes.lines();
        }
    }
}
