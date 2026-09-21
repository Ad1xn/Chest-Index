package dev.adrian.chestindex.client.platform;

//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.RenderPipeline;
*///?} else {
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?}
import dev.adrian.chestindex.mixin.client.RenderPipelinesAccessor;
import dev.adrian.chestindex.mixin.client.RenderTypeInvoker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public final class HighlightRenderTypes {
    private HighlightRenderTypes() {}

    private static RenderType throughWalls;
    private static RenderType filledThroughWalls;

    public static RenderType throughWalls() {
        if (!dev.adrian.chestindex.config.ChestIndexConfig.get().highlightThroughWalls) {
            return occluded();
        }
        if (throughWalls == null) {
            throughWalls = build();
        }
        return throughWalls;
    }

    public static RenderType occluded() {
        return RenderTypes.lines();
    }

    public static RenderType filledThroughWalls() {
        if (!dev.adrian.chestindex.config.ChestIndexConfig.get().highlightThroughWalls) {
            return filled();
        }
        if (filledThroughWalls == null) filledThroughWalls = buildFilled();
        return filledThroughWalls;
    }

    public static RenderType filled() {
        return RenderTypes.debugFilledBox();
    }
    private static RenderType build() {
        return build(RenderPipelinesAccessor.chestindex$linesSnippet(), RenderPipelines.LINES,
                "chestindex_lines_through_walls", "chestindex:lines_through_walls",
                RenderTypes.lines());
    }

    private static RenderType buildFilled() {
        return build(RenderPipelinesAccessor.chestindex$filledSnippet(),
                RenderPipelines.DEBUG_FILLED_BOX,
                "chestindex_filled_through_walls", "chestindex:filled_through_walls",
                RenderTypes.debugFilledBox());
    }

    private static RenderType build(RenderPipeline.Snippet snippet, RenderPipeline vanilla,
                                    String location, String name, RenderType fallback) {
        try {
            RenderPipeline.Builder builder = RenderPipeline
                    .builder(snippet)
                    .withLocation("pipeline/" + location);

            //? if >=26.3 {
            /*java.util.List<com.mojang.renderpearl.api.pipeline.ColorTargetState> targets =
                    vanilla.getColorTargetStates();
            for (int slot = 0; slot < targets.size(); slot++) {
                com.mojang.renderpearl.api.pipeline.ColorTargetState target = targets.get(slot);
                if (target == null) {
                    builder.withUnusedColorTargetState(slot);
                } else {
                    builder.withColorTargetState(slot, target);
                }
            }
            *///?}

            RenderPipeline pipeline = builder
                    //? if >=26.3 {
                    /*.withDepthStencilState(new com.mojang.renderpearl.api.pipeline.DepthStencilState(
                            com.mojang.renderpearl.api.pipeline.CompareOp.ALWAYS_PASS, false))
                    *///?} else if >=26.1 {
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
