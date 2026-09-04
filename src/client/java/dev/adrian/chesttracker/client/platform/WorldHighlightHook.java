package dev.adrian.chesttracker.client.platform;

import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

public final class WorldHighlightHook {

    private WorldHighlightHook() {}

    public static void register() {
        //? if >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN
                .register(context -> {
                    if (!ContainerHighlight.get().hasBoxes()) return;
                    Vec3 eye = context.gameRenderer().mainCamera().position();
                    context.submitNodeCollector().submitCustomGeometry(
                            context.poseStack(), RenderTypes.lines(),
                            (pose, lines) -> ContainerHighlight.get().drawBoxes(pose, lines, eye));
                });
        *///?} else {
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.AFTER_ENTITIES
                .register(context -> {
                    if (!ContainerHighlight.get().hasBoxes()) return;
                    Camera camera = context.gameRenderer().getMainCamera();
                    ContainerHighlight.get().drawBoxes(
                            context.matrices().last(),
                            context.consumers().getBuffer(RenderTypes.lines()),
                            camera.position());
                });
        //?}
    }
}
