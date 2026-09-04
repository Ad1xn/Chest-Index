package dev.adrian.chesttracker.client.highlight;

import dev.adrian.chesttracker.client.platform.ClientCompat;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.highlight.HighlightTargets;
import dev.adrian.chesttracker.core.highlight.HighlightTimer;
import dev.adrian.chesttracker.core.util.BlockKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class ContainerHighlight {

    private static final ContainerHighlight INSTANCE = new ContainerHighlight();

    private static final double ARRIVAL_DISTANCE = 3.0;

    private static final long SAMPLE_INTERVAL_MS = 1000;

    private HighlightTimer timer = HighlightTimer.defaults();

    private List<Long> targets = List.of();

    private String dimensionId;
    private String label;

    private ContainerHighlight() {}

    public static ContainerHighlight get() {
        return INSTANCE;
    }

    public void select(long pos, String dimensionId, String label) {
        select(List.of(pos), dimensionId, label);
    }

    public void select(List<Long> positions, String dimensionId, String label) {
        if (positions == null || positions.isEmpty()) {
            clear();
            return;
        }
        ChestTrackerConfig config = ChestTrackerConfig.get();
        this.timer = new HighlightTimer(config.highlightDurationMs(),
                config.highlightRecedingGraceMs(), SAMPLE_INTERVAL_MS);
        this.targets = List.copyOf(positions);
        this.dimensionId = dimensionId;
        this.label = label;

        LocalPlayer player = Minecraft.getInstance().player;
        double distance = player == null ? 0 : distanceTo(player, nearestTo(player));
        timer.start(distance, System.currentTimeMillis());
    }

    public void clear() {
        timer.clear();
        targets = List.of();
    }

    public boolean isActive() {
        return timer.isActive();
    }

    public List<Long> targets() {
        return targets;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public String label() {
        return label;
    }

    public long pos() {
        LocalPlayer player = Minecraft.getInstance().player;
        return targets.isEmpty() || player == null ? 0L : nearestTo(player);
    }

    public void tick() {
        if (!timer.isActive()) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || targets.isEmpty()) {
            clear();
            return;
        }
        if (!player.level().dimension().identifier().toString().equals(dimensionId)) {
            clear();
            return;
        }

        long target = nearestTo(player);
        double distance = distanceTo(player, target);
        if (!timer.update(distance, System.currentTimeMillis())) {
            return;
        }

        if (distance <= ARRIVAL_DISTANCE) {
            ClientCompat.actionBar(Component.literal(label + " - you are here" + remainder())
                    .withStyle(ChatFormatting.GREEN));
            return;
        }

        ClientCompat.actionBar(Component.literal(String.format("%s  %s  %.0fm%s",
                label, bearing(player, target), distance, remainder()))
                .withStyle(ChatFormatting.AQUA));
    }

    private String remainder() {
        return targets.size() > 1 ? "  (+" + (targets.size() - 1) + " more)" : "";
    }

    private long nearestTo(LocalPlayer player) {
        return HighlightTargets.nearest(targets,
                player.getBlockX(), player.getBlockY(), player.getBlockZ());
    }

    private double distanceTo(LocalPlayer player, long pos) {
        return Math.sqrt(BlockKey.distanceSq(
                BlockKey.pack(player.getBlockX(), player.getBlockY(), player.getBlockZ()), pos));
    }

    private String bearing(LocalPlayer player, long pos) {
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
