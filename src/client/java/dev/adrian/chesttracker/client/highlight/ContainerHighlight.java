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

    private static final double DRAW_RADIUS = 160.0;

    private static final int MAX_BOXES = 96;

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
    }

    public void clear() {
        timer.clear();
        positions = List.of();
    }

    private static final double GROW_PER_BLOCK = 0.03;

    private static final double MAX_GROW = 3.0;

    private static final float BASE_LINE_WIDTH = 2.5f;
    private static final float LINE_WIDTH_PER_BLOCK = 0.06f;
    private static final float MAX_LINE_WIDTH = 8.0f;

    public boolean hasBoxes() {
        return timer.isActive() && !positions.isEmpty()
                && ChestTrackerConfig.get().highlightDisplay().drawsBoxes();
    }

    public void drawBoxes(PoseStack.Pose pose, VertexConsumer lines, Vec3 eye) {
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
            if (distSq > DRAW_RADIUS * DRAW_RADIUS) continue;

            double distance = Math.sqrt(distSq);
            double grow = Math.min(MAX_GROW, distance * GROW_PER_BLOCK);
            float width = (float) Math.min(MAX_LINE_WIDTH,
                    BASE_LINE_WIDTH + distance * LINE_WIDTH_PER_BLOCK);

            boolean nearest = position == pos;
            HighlightBox.emit(pose, lines, x - eye.x, y - eye.y, z - eye.z,
                    nearest ? 1.0f : 0.25f,
                    nearest ? 0.82f : 0.85f,
                    nearest ? 0.2f : 1.0f,
                    0.9f, grow, width);
            drawn++;
        }
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
