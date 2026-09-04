package dev.adrian.chesttracker.client.highlight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class HighlightBox {

    private static final double SWELL = 0.002;

    private static final float LINE_WIDTH = 2.0f;

    private HighlightBox() {}

    public static void emit(PoseStack.Pose pose, VertexConsumer lines,
                            double x, double y, double z,
                            float red, float green, float blue, float alpha) {

        float x0 = (float) (x - SWELL);
        float y0 = (float) (y - SWELL);
        float z0 = (float) (z - SWELL);
        float x1 = (float) (x + 1 + SWELL);
        float y1 = (float) (y + 1 + SWELL);
        float z1 = (float) (z + 1 + SWELL);

        edge(pose, lines, x0, y0, z0, x0, y1, z0, 0, 1, 0, red, green, blue, alpha);
        edge(pose, lines, x1, y0, z0, x1, y1, z0, 0, 1, 0, red, green, blue, alpha);
        edge(pose, lines, x1, y0, z1, x1, y1, z1, 0, 1, 0, red, green, blue, alpha);
        edge(pose, lines, x0, y0, z1, x0, y1, z1, 0, 1, 0, red, green, blue, alpha);

        ring(pose, lines, x0, x1, y0, z0, z1, red, green, blue, alpha);
        ring(pose, lines, x0, x1, y1, z0, z1, red, green, blue, alpha);
    }

    private static void ring(PoseStack.Pose pose, VertexConsumer lines,
                             float x0, float x1, float y, float z0, float z1,
                             float red, float green, float blue, float alpha) {
        edge(pose, lines, x0, y, z0, x1, y, z0, 1, 0, 0, red, green, blue, alpha);
        edge(pose, lines, x1, y, z0, x1, y, z1, 0, 0, 1, red, green, blue, alpha);
        edge(pose, lines, x1, y, z1, x0, y, z1, -1, 0, 0, red, green, blue, alpha);
        edge(pose, lines, x0, y, z1, x0, y, z0, 0, 0, -1, red, green, blue, alpha);
    }

    private static void edge(PoseStack.Pose pose, VertexConsumer lines,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float nx, float ny, float nz,
                             float red, float green, float blue, float alpha) {
        lines.addVertex(pose, ax, ay, az).setColor(red, green, blue, alpha)
                .setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
        lines.addVertex(pose, bx, by, bz).setColor(red, green, blue, alpha)
                .setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
    }
}
