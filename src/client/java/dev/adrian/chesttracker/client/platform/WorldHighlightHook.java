package dev.adrian.chesttracker.client.platform;

import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

public final class WorldHighlightHook {

    private WorldHighlightHook() {}

    public static void register() {
        //? if >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN
                .register(context -> {
                    ContainerHighlight.get().turnTowardsTarget();
                    if (!ContainerHighlight.get().hasBoxes()) return;
                    context.submitNodeCollector().submitCustomGeometry(
                            context.poseStack(), HighlightRenderTypes.throughWalls(),
                            (pose, lines) -> ContainerHighlight.get().drawBoxes(
                                    pose, lines, camera()));

                    if (!ContainerHighlight.get().hasTrails()) return;
                    context.submitNodeCollector().submitCustomGeometry(
                            context.poseStack(), HighlightRenderTypes.occluded(),
                            (pose, lines) -> ContainerHighlight.get().drawTrails(
                                    pose, lines, camera()));
                });
        *///?} else {
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.AFTER_ENTITIES
                .register(context -> {
                    ContainerHighlight.get().turnTowardsTarget();
                    if (!ContainerHighlight.get().hasBoxes()) return;
                    Camera camera = context.gameRenderer().getMainCamera();
                    ContainerHighlight.get().drawBoxes(
                            context.matrices().last(),
                            context.consumers().getBuffer(HighlightRenderTypes.throughWalls()),
                            camera.position());

                    if (!ContainerHighlight.get().hasTrails()) return;
                    ContainerHighlight.get().drawTrails(
                            context.matrices().last(),
                            context.consumers().getBuffer(HighlightRenderTypes.occluded()),
                            camera.position());
                });
        //?}
    }

    //? if >=26.1 {
    /*
    private static Vec3 camera() {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
    }
    *///?}
}
