package dev.adrian.chesttracker.client.highlight;

import dev.adrian.chesttracker.client.platform.ClientCompat;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.highlight.HighlightTargets;
import dev.adrian.chesttracker.core.highlight.HighlightTimer;
import dev.adrian.chesttracker.core.util.BlockKey;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ContainerHighlight {

    private static final ContainerHighlight INSTANCE = new ContainerHighlight();

    private static final double ARRIVAL_DISTANCE = 3.0;

    private static final long SAMPLE_INTERVAL_MS = 1000;

    private HighlightTimer timer = HighlightTimer.defaults();

    private List<Long> positions = List.of();

    private long pos;
    private String dimensionId;
    private String label;

    private String searchedItemId;

    private int turnTicksLeft;

    private static final double DRAW_RADIUS = 512.0;

    private static final int MAX_BOXES = 32;

    private ContainerHighlight() {}

    public static ContainerHighlight get() {
        return INSTANCE;
    }

    public void select(long pos, String dimensionId, String label) {
        select(List.of(pos), dimensionId, label);
    }

    public void select(List<Long> positions, String dimensionId, String label) {
        this.positions = positions == null ? List.of() : List.copyOf(positions);
        long nearest = this.positions.isEmpty() ? 0 : this.positions.get(0);
        selectPrimary(nearest, dimensionId, label);
    }

    private void selectPrimary(long pos, String dimensionId, String label) {
        ChestTrackerConfig config = ChestTrackerConfig.get();
        this.timer = new HighlightTimer(config.highlightDurationMs(),
                config.highlightRecedingGraceMs(), SAMPLE_INTERVAL_MS);
        this.pos = pos;
        this.dimensionId = dimensionId;
        this.label = label;

        LocalPlayer player = Minecraft.getInstance().player;
        double distance = player == null ? 0 : distanceTo(player);
        timer.start(distance, System.currentTimeMillis());
        turnTicksLeft = ChestTrackerConfig.get().turnToTarget ? TURN_MAX_TICKS : 0;
    }

    public void clear() {
        timer.clear();
        positions = List.of();
        searchedItemId = null;
    }

    public String searchedItemId() {
        return searchedItemId;
    }

    public void searchingFor(String itemId) {
        this.searchedItemId = itemId;
    }

    private static final double GROW_PER_BLOCK = 0.008;

    private static final double FAR_FROM = 200.0;

    private static final double FAR_GROW_PER_BLOCK = 0.04;

    private static final double GROW_STEP = 0.5;

    private static final double GROW_FROM = 24.0;

    private static final double MAX_GROW = 8.0;

    private static final double BEAM_MIN_DISTANCE = 8.0;

    private static final double BEAM_HEIGHT = 32.0;

    private static final float TURN_EASE = 0.28f;

    private static final float TURN_DONE_DEGREES = 0.75f;

    private static final float TURN_MAX_STEP = 7.0f;

    private static final int TURN_MAX_TICKS = 30;

    private static final float BASE_LINE_WIDTH = 2.0f;
    private static final float LINE_WIDTH_PER_BLOCK = 0.02f;
    private static final float MAX_LINE_WIDTH = 5.0f;

    public boolean hasBoxes() {
        return timer.isActive() && !positions.isEmpty()
                && ChestTrackerConfig.get().highlightDisplay().drawsBoxes();
    }

    public void drawBoxes(PoseStack.Pose pose, VertexConsumer lines, Vec3 eye) {
        ChestTrackerConfig config = ChestTrackerConfig.get();
        boolean beams = config.guideBeam;
        float[] nearestColour = config.nearestRgb();
        float[] otherColour = config.otherRgb();

        int drawn = 0;
        for (Long position : positions) {
            if (drawn >= MAX_BOXES) break;

            double x = BlockKey.x(position);
            double y = BlockKey.y(position);
            double z = BlockKey.z(position);

            double dx = x + 0.5 - eye.x;
            double dy = y + 0.5 - eye.y;
            double dz = z + 0.5 - eye.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            double distance = Math.sqrt(distSq);

            double limit = horizon();
            double pull = distance > limit ? limit / distance : 1.0;

            double grow = growthAt(distance);
            float width = (float) Math.round(Math.min(MAX_LINE_WIDTH,
                    BASE_LINE_WIDTH + Math.max(0.0, distance - GROW_FROM) * LINE_WIDTH_PER_BLOCK));

            boolean nearest = position == pos;
            float[] colour = nearest ? nearestColour : otherColour;

            double drawX = dx * pull - 0.5;
            double drawY = dy * pull - 0.5;
            double drawZ = dz * pull - 0.5;

            HighlightBox.emit(pose, lines, drawX, drawY, drawZ,
                    colour[0], colour[1], colour[2], 0.9f, grow, width);

            if (beams && distance > BEAM_MIN_DISTANCE) {
                HighlightBox.beam(pose, lines,
                        drawX + 0.5, drawY + 1.5, drawZ + 0.5, BEAM_HEIGHT * pull,
                        colour[0], colour[1], colour[2], 0.75f, width);
            }
            drawn++;
        }
    }

    private static double horizon() {
        Minecraft client = Minecraft.getInstance();
        int chunks = client.options == null ? 8 : client.options.getEffectiveRenderDistance();
        return Math.max(48.0, chunks * 16.0 * 0.85);
    }

    private static double growthAt(double distance) {
        double grow = Math.max(0.0, distance - GROW_FROM) * GROW_PER_BLOCK;
        if (distance > FAR_FROM) grow += (distance - FAR_FROM) * FAR_GROW_PER_BLOCK;
        grow = Math.min(MAX_GROW, grow);
        return Math.round(grow / GROW_STEP) * GROW_STEP;
    }

    private void turnTowards(LocalPlayer player) {
        if (turnTicksLeft <= 0) return;

        double dx = BlockKey.x(pos) + 0.5 - player.getX();
        double dy = BlockKey.y(pos) + 0.5 - player.getEyeY();
        double dz = BlockKey.z(pos) + 0.5 - player.getZ();

        float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        float yawGap = Mth.wrapDegrees(wantYaw - player.getYRot());
        float pitchGap = wantPitch - player.getXRot();

        if (Math.abs(yawGap) < TURN_DONE_DEGREES && Math.abs(pitchGap) < TURN_DONE_DEGREES) {
            turnTicksLeft = 0;
            return;
        }

        player.setYRot(player.getYRot()
                + Mth.clamp(yawGap * TURN_EASE, -TURN_MAX_STEP, TURN_MAX_STEP));
        player.setXRot(player.getXRot()
                + Mth.clamp(pitchGap * TURN_EASE, -TURN_MAX_STEP, TURN_MAX_STEP));
        turnTicksLeft--;
    }

    private void followNearest(LocalPlayer player) {
        if (positions.size() < 2) return;
        pos = HighlightTargets.nearest(positions,
                player.getBlockX(), player.getBlockY(), player.getBlockZ());
    }

    public boolean isActive() {
        return timer.isActive();
    }

    public long pos() {
        return pos;
    }

    public void tick() {
        if (!timer.isActive()) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            timer.clear();
            return;
        }
        if (!player.level().dimension().identifier().toString().equals(dimensionId)) {
            timer.clear();
            return;
        }

        followNearest(player);
        turnTowards(player);

        double distance = distanceTo(player);
        if (!timer.update(distance, System.currentTimeMillis())) {
            return;
        }

        if (!ChestTrackerConfig.get().highlightDisplay().writesActionBar()) return;

        if (distance <= ARRIVAL_DISTANCE) {
            ClientCompat.actionBar(Component.literal(label + " - you are here")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }

        ClientCompat.actionBar(Component.literal(String.format("%s  %s  %.0fm",
                label, bearing(player), distance)).withStyle(ChatFormatting.AQUA));
    }

    private double distanceTo(LocalPlayer player) {
        return Math.sqrt(BlockKey.distanceSq(
                BlockKey.pack(player.getBlockX(), player.getBlockY(), player.getBlockZ()), pos));
    }

    private String bearing(LocalPlayer player) {
        double dx = BlockKey.x(pos) + 0.5 - player.getX();
        double dz = BlockKey.z(pos) + 0.5 - player.getZ();
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = Math.floorMod((long) (targetYaw - player.getYRot() + 360 + 22.5), 360L) / 45;

        String horizontal = switch ((int) relative) {
            case 0 -> "ahead";
            case 1 -> "ahead-right";
            case 2 -> "right";
            case 3 -> "behind-right";
            case 4 -> "behind";
            case 5 -> "behind-left";
            case 6 -> "left";
            default -> "ahead-left";
        };

        int dy = BlockKey.y(pos) - player.getBlockY();
        if (dy > 3) return horizontal + " and up";
        if (dy < -3) return horizontal + " and down";
        return horizontal;
    }
}
