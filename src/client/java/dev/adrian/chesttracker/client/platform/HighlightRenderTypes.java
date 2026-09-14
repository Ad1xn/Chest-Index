package dev.adrian.chesttracker.client.platform;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.adrian.chesttracker.mixin.client.RenderPipelinesAccessor;
import dev.adrian.chesttracker.mixin.client.RenderTypeInvoker;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public final class HighlightRenderTypes {

    private HighlightRenderTypes() {}

    private static RenderType throughWalls;

    public static RenderType throughWalls() {
        if (!dev.adrian.chesttracker.config.ChestTrackerConfig.get().highlightThroughWalls) {
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
