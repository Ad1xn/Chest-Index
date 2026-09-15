package dev.adrian.chestindex.client.highlight;

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
        emit(pose, lines, x, y, z, 1, 1, red, green, blue, alpha, grow, lineWidth);
    }

    public static void emit(PoseStack.Pose pose, VertexConsumer lines,
                            double x, double y, double z, int sizeX, int sizeZ,
                            float red, float green, float blue, float alpha,
                            double grow, float lineWidth) {
        emit(pose, lines, x, y, z, sizeX, 1.0, sizeZ, red, green, blue, alpha, grow, lineWidth);
    }

    public static void emit(PoseStack.Pose pose, VertexConsumer lines,
                            double x, double y, double z,
                            double sizeX, double sizeY, double sizeZ,
                            float red, float green, float blue, float alpha,
                            double grow, float lineWidth) {

        double swell = SWELL + grow;
        float x0 = (float) (x - swell);
        float y0 = (float) (y - swell);
        float z0 = (float) (z - swell);
        float x1 = (float) (x + sizeX + swell);
        float y1 = (float) (y + sizeY + swell);
        float z1 = (float) (z + sizeZ + swell);

        edge(pose, lines, x0, y0, z0, x0, y1, z0, 0, 1, 0, red, green, blue, alpha, lineWidth);
        edge(pose, lines, x1, y0, z0, x1, y1, z0, 0, 1, 0, red, green, blue, alpha, lineWidth);
        edge(pose, lines, x1, y0, z1, x1, y1, z1, 0, 1, 0, red, green, blue, alpha, lineWidth);
        edge(pose, lines, x0, y0, z1, x0, y1, z1, 0, 1, 0, red, green, blue, alpha, lineWidth);

        ring(pose, lines, x0, x1, y0, z0, z1, red, green, blue, alpha, lineWidth);
        ring(pose, lines, x0, x1, y1, z0, z1, red, green, blue, alpha, lineWidth);
    }

    public static void cube(PoseStack.Pose pose, VertexConsumer faces,
                            double x, double y, double z,
                            double sizeX, double sizeY, double sizeZ,
                            float red, float green, float blue, float alpha) {
        float x0 = (float) x;
        float y0 = (float) y;
        float z0 = (float) z;
        float x1 = (float) (x + sizeX);
        float y1 = (float) (y + sizeY);
        float z1 = (float) (z + sizeZ);

        quad(pose, faces, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, red, green, blue, alpha);
        quad(pose, faces, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, red, green, blue, alpha);
        quad(pose, faces, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, red, green, blue, alpha);
        quad(pose, faces, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, red, green, blue, alpha);
        quad(pose, faces, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, red, green, blue, alpha);
        quad(pose, faces, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, red, green, blue, alpha);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer faces,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float red, float green, float blue, float alpha) {
        faces.addVertex(pose, ax, ay, az).setColor(red, green, blue, alpha);
        faces.addVertex(pose, bx, by, bz).setColor(red, green, blue, alpha);
        faces.addVertex(pose, cx, cy, cz).setColor(red, green, blue, alpha);
        faces.addVertex(pose, dx, dy, dz).setColor(red, green, blue, alpha);
    }

    private static final int BEAM_MARKS = 14;

    private static final double BEAM_DUTY = 0.18;

    private static final float BEAM_TOP_ALPHA = 0.4f;

    public static void beam(PoseStack.Pose pose, VertexConsumer lines,
                            double x, double y, double z, double height,
                            float red, float green, float blue, float alpha, float lineWidth) {
        double step = height / BEAM_MARKS;
        double mark = step * BEAM_DUTY;
        for (int i = 0; i < BEAM_MARKS; i++) {
            double at = i * step;
            float up = (float) i / BEAM_MARKS;
            float fade = alpha * (1.0f - up * (1.0f - BEAM_TOP_ALPHA));
            if (fade <= 0.02f) break;
            line(pose, lines, x, y + at, z, x, y + at + mark, z,
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
