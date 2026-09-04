package dev.adrian.chesttracker.client.highlight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class HighlightBox {

    private static final double SWELL = 0.002;

    private static final float LINE_WIDTH = 2.0f;

    private HighlightBox() {}

    public static void emit(PoseStack.Pose pose, VertexConsumer lines,
                            double x, double y, double z,
                            float red, float green, float blue, float alpha,
                            double grow, float lineWidth) {

        double swell = SWELL + grow;
        float x0 = (float) (x - swell);
        float y0 = (float) (y - swell);
        float z0 = (float) (z - swell);
        float x1 = (float) (x + 1 + swell);
        float y1 = (float) (y + 1 + swell);
        float z1 = (float) (z + 1 + swell);

        edge(pose, lines, x0, y0, z0, x0, y1, z0, 0, 1, 0, red, green, blue, alpha, lineWidth);
        edge(pose, lines, x1, y0, z0, x1, y1, z0, 0, 1, 0, red, green, blue, alpha, lineWidth);
        edge(pose, lines, x1, y0, z1, x1, y1, z1, 0, 1, 0, red, green, blue, alpha, lineWidth);
        edge(pose, lines, x0, y0, z1, x0, y1, z1, 0, 1, 0, red, green, blue, alpha, lineWidth);

        ring(pose, lines, x0, x1, y0, z0, z1, red, green, blue, alpha, lineWidth);
        ring(pose, lines, x0, x1, y1, z0, z1, red, green, blue, alpha, lineWidth);
    }

    private static final double BEAM_SPACING = 2.5;

    private static final double BEAM_MARK = 0.45;

    public static void beam(PoseStack.Pose pose, VertexConsumer lines,
                            double x, double y, double z, double height,
                            float red, float green, float blue, float alpha, float lineWidth) {
        for (double at = 0; at < height; at += BEAM_SPACING) {
            float fade = alpha * (float) (1.0 - at / height);
            if (fade <= 0.02f) break;
            line(pose, lines, x, y + at, z, x, y + at + BEAM_MARK, z,
                    red, green, blue, fade, lineWidth);
        }
    }

    public static void line(PoseStack.Pose pose, VertexConsumer lines,
                            double ax, double ay, double az,
                            double bx, double by, double bz,
                            float red, float green, float blue, float alpha, float lineWidth) {
        float dx = (float) (bx - ax);
        float dy = (float) (by - ay);
        float dz = (float) (bz - az);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-4f) return;

        edge(pose, lines, (float) ax, (float) ay, (float) az, (float) bx, (float) by, (float) bz,
                dx / length, dy / length, dz / length, red, green, blue, alpha, lineWidth);
    }

    private static void ring(PoseStack.Pose pose, VertexConsumer lines,
                             float x0, float x1, float y, float z0, float z1,
                             float red, float green, float blue, float alpha, float lineWidth) {
        edge(pose, lines, x0, y, z0, x1, y, z0, 1, 0, 0, red, green, blue, alpha, lineWidth);
        edge(pose, lines, x1, y, z0, x1, y, z1, 0, 0, 1, red, green, blue, alpha, lineWidth);
        edge(pose, lines, x1, y, z1, x0, y, z1, -1, 0, 0, red, green, blue, alpha, lineWidth);
        edge(pose, lines, x0, y, z1, x0, y, z0, 0, 0, -1, red, green, blue, alpha, lineWidth);
    }

    private static void edge(PoseStack.Pose pose, VertexConsumer lines,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float nx, float ny, float nz,
                             float red, float green, float blue, float alpha, float lineWidth) {
        lines.addVertex(pose, ax, ay, az).setColor(red, green, blue, alpha)
                .setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
        lines.addVertex(pose, bx, by, bz).setColor(red, green, blue, alpha)
                .setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
    }
}
