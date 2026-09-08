package dev.adrian.chesttracker.client.highlight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * The wireframe drawn around a tracked container.
 *
 * <p>Written out edge by edge rather than through a vanilla helper, because
 * there is no longer one: the {@code renderLineBox} family is gone from both
 * targets. Twelve edges is little enough code that owning it is cheaper than
 * finding a moving equivalent on each version.
 *
 * <p>This is the half of the highlight that does <em>not</em> differ between
 * versions. {@code VertexConsumer}, {@code PoseStack.Pose} and
 * {@code RenderTypes.lines()} are identical on both, so only the hook that
 * hands them over is shimmed.
 */
public final class HighlightBox {

    /** Drawn slightly outside the block, so the lines are not inside its faces. */
    private static final double SWELL = 0.002;

    /**
     * Width carried by every vertex, because the line format demands one.
     *
     * <p>{@code RenderPipelines.LINES} is built on
     * {@code POSITION_COLOR_NORMAL_LINE_WIDTH} on <em>both</em> targets, and its
     * render type sets no default - the width is per vertex and nothing fills
     * it in. Omitting it does not draw a thin line, it throws
     * {@code IllegalStateException: Missing elements in vertex} on the second
     * vertex of the first edge, taking the render thread down with it.
     */
    private static final float LINE_WIDTH = 2.0f;

    private HighlightBox() {}

    /**
     * Emits one box in camera-relative coordinates.
     *
     * @param pose  the current transform; vertices are placed through it
     * @param lines a consumer opened on a line render type
     */
    public static void emit(PoseStack.Pose pose, VertexConsumer lines,
                            double x, double y, double z,
                            float red, float green, float blue, float alpha,
                            double grow, float lineWidth) {
        emit(pose, lines, x, y, z, 1, 1, red, green, blue, alpha, grow, lineWidth);
    }

    /**
     * Emits one box spanning {@code sizeX} by {@code sizeZ} blocks.
     *
     * <p>For the double chest, which is two blocks the game and the player both
     * treat as one container. The index files it under one of the two halves,
     * so a one-block box drew a line down the middle of a chest and left the
     * other half outside the marker - which reads as the mod pointing at the
     * wrong block rather than at a chest with a wide box.
     *
     * @param x the <em>lower</em> corner, not the indexed half
     */
    public static void emit(PoseStack.Pose pose, VertexConsumer lines,
                            double x, double y, double z, int sizeX, int sizeZ,
                            float red, float green, float blue, float alpha,
                            double grow, float lineWidth) {
        emit(pose, lines, x, y, z, sizeX, 1.0, sizeZ, red, green, blue, alpha, grow, lineWidth);
    }

    /**
     * Emits one box of any size, for containers that are not made of blocks.
     *
     * <p>A chest minecart is 0.98 blocks wide and 0.7 tall; a chest boat is
     * 1.375 by 0.5625. Drawing either as a one-block cube is a box that does
     * not fit the thing it is pointing at - too tall for both, too narrow for
     * the boat - and the eye reads that as the marker being slightly off rather
     * than as the marker being square. So an entity's box is its own size, read
     * from the entity itself, and every type gets the one that fits it.
     *
     * @param x the lower corner on each axis, not the centre
     */
    public static void emit(PoseStack.Pose pose, VertexConsumer lines,
                            double x, double y, double z,
                            double sizeX, double sizeY, double sizeZ,
                            float red, float green, float blue, float alpha,
                            double grow, float lineWidth) {

        // Grown about the container's centre, so it stays centred on it
        // however large it gets.
        double swell = SWELL + grow;
        float x0 = (float) (x - swell);
        float y0 = (float) (y - swell);
        float z0 = (float) (z - swell);
        float x1 = (float) (x + sizeX + swell);
        float y1 = (float) (y + sizeY + swell);
        float z1 = (float) (z + sizeZ + swell);

        // Four uprights.
        edge(pose, lines, x0, y0, z0, x0, y1, z0, 0, 1, 0, red, green, blue, alpha, lineWidth);
        edge(pose, lines, x1, y0, z0, x1, y1, z0, 0, 1, 0, red, green, blue, alpha, lineWidth);
        edge(pose, lines, x1, y0, z1, x1, y1, z1, 0, 1, 0, red, green, blue, alpha, lineWidth);
        edge(pose, lines, x0, y0, z1, x0, y1, z1, 0, 1, 0, red, green, blue, alpha, lineWidth);

        // Bottom and top rings.
        ring(pose, lines, x0, x1, y0, z0, z1, red, green, blue, alpha, lineWidth);
        ring(pose, lines, x0, x1, y1, z0, z1, red, green, blue, alpha, lineWidth);
    }

    /**
     * A column of light standing on the container, fading out with height.
     *
     * <p>The box alone is no use where it is most needed. Past render distance
     * there is no terrain drawn to place it against, and a wireframe cube
     * floating in an empty sky says nothing about where it is - the chunk it
     * sits in has never been loaded, so there is nothing around it to read. A
     * column is visible over whatever is in the way and reads as a position on
     * the ground rather than a shape in the air.
     *
     * <p>Drawn as segments rather than one line so the fade is visible: alpha
     * is a vertex attribute, and a two-vertex line can only fade linearly from
     * end to end, which at this length is barely a gradient at all.
     */
    /** Marks in a trail, however tall it is. */
    private static final int BEAM_MARKS = 14;

    /** How much of each step is drawn; the rest is the gap. */
    private static final double BEAM_DUTY = 0.18;

    public static void beam(PoseStack.Pose pose, VertexConsumer lines,
                            double x, double y, double z, double height,
                            float red, float green, float blue, float alpha, float lineWidth) {
        // A fixed number of marks rather than a fixed spacing: the trail grows
        // with distance, and fixed spacing on a four-hundred block trail is two
        // hundred segments of which none are individually visible anyway.
        double step = height / BEAM_MARKS;
        double mark = step * BEAM_DUTY;
        for (int i = 0; i < BEAM_MARKS; i++) {
            double at = i * step;
            // Fades out with height, so the trail reads as rising from the
            // container rather than falling on it.
            float fade = alpha * (1.0f - (float) i / BEAM_MARKS);
            if (fade <= 0.02f) break;
            line(pose, lines, x, y + at, z, x, y + at + mark, z,
                    red, green, blue, fade, lineWidth);
        }
    }

    /**
     * One free-standing segment, for the line drawn towards a distant match.
     *
     * <p>Takes its normal from its own direction, like the box edges do.
     */
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

    /**
     * One line segment.
     *
     * <p>The line format wants a normal per vertex, and it is the segment's own
     * direction - a line has no surface to face away from, so anything else
     * shades it inconsistently as the camera moves.
     */
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
