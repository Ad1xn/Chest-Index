package dev.adrian.chestindex.client.platform;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.adrian.chestindex.mixin.client.RenderPipelinesAccessor;
import dev.adrian.chestindex.mixin.client.RenderTypeInvoker;
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
    private static RenderType filledThroughWalls;

    /**
     * Lines drawn wherever they are, whatever is in front of them.
     *
     * <p>Falls back to the ordinary line type if the pipeline cannot be built.
     * A marker that is merely occluded is worth far more than a crash on the
     * render thread, and this is the one piece of the highlight that reaches
     * past the API into vanilla's own rendering.
     */
    public static RenderType throughWalls() {
        if (!dev.adrian.chestindex.config.ChestIndexConfig.get().highlightThroughWalls) {
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

    /**
     * Solid faces, drawn through whatever is in front of them.
     *
     * <p>The cube half of a marker. The same trick as {@link #throughWalls()},
     * on the pipeline vanilla fills its debug boxes with - already translucent,
     * and already drawing both sides of every face, so a camera standing inside
     * the box still sees it rather than looking through the back of it.
     */
    public static RenderType filledThroughWalls() {
        if (!dev.adrian.chestindex.config.ChestIndexConfig.get().highlightThroughWalls) {
            return filled();
        }
        if (filledThroughWalls == null) filledThroughWalls = buildFilled();
        return filledThroughWalls;
    }

    /** Solid faces the world can stand in front of - vanilla's own. */
    public static RenderType filled() {
        return RenderTypes.debugFilledBox();
    }
    private static RenderType build() {
        return build(RenderPipelinesAccessor.chestindex$linesSnippet(),
                "chestindex_lines_through_walls", "chestindex:lines_through_walls",
                RenderTypes.lines());
    }

    private static RenderType buildFilled() {
        return build(RenderPipelinesAccessor.chestindex$filledSnippet(),
                "chestindex_filled_through_walls", "chestindex:filled_through_walls",
                RenderTypes.debugFilledBox());
    }

    /**
     * One see-through render type, built from a vanilla snippet.
     *
     * <p>Both markers want the same thing done to them - vanilla's own way of
     * drawing a shape, with the depth test taken out and depth writes left off
     * - so the two differ only in which snippet they start from.
     *
     * @param fallback what to use if the pipeline cannot be built. A marker
     *                 that is merely occluded is worth far more than a crash on
     *                 the render thread, and this is the one place this mod
     *                 reaches past the API into how the game draws.
     */
    private static RenderType build(RenderPipeline.Snippet snippet, String location,
                                    String name, RenderType fallback) {
        try {
            RenderPipeline pipeline = RenderPipeline
                    .builder(snippet)
                    .withLocation("pipeline/" + location)
                    //? if >=26.1 {
                    /*.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(
                            com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS, false))
                    *///?} else {
                    .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    //?}
                    .build();

            return RenderTypeInvoker.chestindex$create(name,
                    RenderSetup.builder(pipeline).createRenderSetup());
        } catch (Throwable t) {
            dev.adrian.chestindex.ChestIndex.LOG.warn(
                    "Could not build the see-through marker type {}, falling back: {}",
                    name, t.toString());
            return fallback;
        }
    }
}
