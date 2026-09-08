package dev.adrian.chesttracker.client.highlight;

import dev.adrian.chesttracker.client.ActionBar;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ContainerHighlight {

    private static final ContainerHighlight INSTANCE = new ContainerHighlight();

    private static final double ARRIVAL_DISTANCE = 3.0;

    private static final long SAMPLE_INTERVAL_MS = 1000;

    private HighlightTimer timer = HighlightTimer.defaults();

    private List<Long> positions = List.of();

    private java.util.Map<Long, Integer> entityIds = java.util.Map.of();

    private long pos;
    private String dimensionId;
    private String label;

    private java.util.Set<String> searchedItemIds = java.util.Set.of();

    private float turnSecondsLeft;

    private long turnLastFrameAt;

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
        select(positions, java.util.Map.of(), dimensionId, label);
    }

    public void selectHits(List<dev.adrian.chesttracker.core.net.QueryDto.ContainerHit> hits,
                           String dimensionId, String label) {
        if (hits == null || hits.isEmpty()) {
            select(List.of(), dimensionId, label);
            return;
        }
        List<Long> positions = new java.util.ArrayList<>(hits.size());
        java.util.Map<Long, Integer> ids = new java.util.HashMap<>();
        for (var hit : hits) {
            positions.add(hit.pos());
            if (hit.isEntity()) ids.put(hit.pos(), hit.entityId());
        }
        select(positions, ids, dimensionId, label);
    }

    public void select(List<Long> positions, java.util.Map<Long, Integer> entityIds,
                       String dimensionId, String label) {
        this.positions = positions == null ? List.of() : List.copyOf(positions);
        this.entityIds = entityIds == null ? java.util.Map.of() : java.util.Map.copyOf(entityIds);
        long nearest = this.positions.isEmpty() ? 0 : this.positions.get(0);
        selectPrimary(nearest, dimensionId, label);
    }

    private Vec3 centre(long position) {
        Entity entity = entityAt(position);
        if (entity != null) {
            Vec3 at = livePosition(entity);
            return new Vec3(at.x, at.y + 0.5, at.z);
        }
        return new Vec3(BlockKey.x(position) + 0.5, BlockKey.y(position) + 0.5, BlockKey.z(position) + 0.5);
    }

    private static Vec3 livePosition(Entity entity) {
        float partialTick = Minecraft.getInstance().getDeltaTracker()
                .getGameTimeDeltaPartialTick(true);
        return entity.getPosition(partialTick);
    }

    private Entity entityAt(long position) {
        if (entityIds.isEmpty()) return null;
        Integer id = entityIds.get(position);
        if (id == null) return null;
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }

    private void selectPrimary(long pos, String dimensionId, String label) {
        this.carriedUntil = 0L;
        this.hintItemIds = java.util.Set.of();
        ChestTrackerConfig config = ChestTrackerConfig.get();
        this.timer = new HighlightTimer(config.highlightDurationMs(),
                config.highlightRecedingGraceMs(), SAMPLE_INTERVAL_MS);
        this.pos = pos;
        this.dimensionId = dimensionId;
        this.label = label;

        LocalPlayer player = Minecraft.getInstance().player;
        double distance = player == null ? 0 : distanceTo(player);
        timer.start(distance, System.currentTimeMillis());
        turnSecondsLeft = ChestTrackerConfig.get().turnToTarget ? TURN_MAX_SECONDS : 0.0f;
        turnLastFrameAt = System.nanoTime();
    }

    private long carriedUntil;

    public void markCarried(String itemId, String label) {
        markCarried(itemId, label, List.of(), null);
    }

    public void markCarried(String itemId, String label,
                            List<Long> positions, String dimensionId) {
        clear();
        this.searchedItemIds = java.util.Set.of(itemId);
        this.label = label;
        this.carriedUntil = System.currentTimeMillis() + ChestTrackerConfig.get().highlightDurationMs();

        if (positions == null || positions.isEmpty()) {
            this.positions = List.of();
            this.dimensionId = null;
            this.hintItemIds = ENDER_CHEST_HINT;
            return;
        }

        long carried = this.carriedUntil;
        java.util.Set<String> wanted = this.searchedItemIds;
        select(positions, dimensionId, label);
        this.carriedUntil = carried;
        this.searchedItemIds = wanted;
        this.hintItemIds = ENDER_CHEST_HINT;
    }

    private java.util.Set<String> hintItemIds = java.util.Set.of();

    private static final java.util.Set<String> ENDER_CHEST_HINT =
            java.util.Set.of("minecraft:ender_chest");

    public java.util.Set<String> hintItemIds() {
        return hintItemIds;
    }

    private boolean carriedActive() {
        return carriedUntil > 0 && System.currentTimeMillis() < carriedUntil;
    }

    public void forget(long position) {
        if (positions.isEmpty()) return;
        List<Long> remaining = new java.util.ArrayList<>(positions.size());
        for (Long candidate : positions) {
            if (candidate != position) remaining.add(candidate);
        }
        if (remaining.size() == positions.size()) return;

        if (remaining.isEmpty()) {
            clear();
            return;
        }
        positions = List.copyOf(remaining);
        if (!entityIds.isEmpty()) {
            java.util.Map<Long, Integer> kept = new java.util.HashMap<>(entityIds);
            kept.keySet().retainAll(positions);
            entityIds = java.util.Map.copyOf(kept);
        }
        if (pos == position) pos = positions.get(0);
    }

    public void clear() {
        timer.clear();
        positions = List.of();
        entityIds = java.util.Map.of();
        searchedItemIds = java.util.Set.of();
        hintItemIds = java.util.Set.of();
        carriedUntil = 0L;
    }

    public java.util.Set<String> searchedItemIds() {
        return searchedItemIds;
    }

    public void searchingFor(String itemId) {
        this.searchedItemIds = itemId == null ? java.util.Set.of() : java.util.Set.of(itemId);
    }

    public void retire(String itemId) {
        if (itemId == null || !searchedItemIds.contains(itemId)) return;

        java.util.Set<String> remaining = new java.util.HashSet<>(searchedItemIds);
        remaining.remove(itemId);
        if (remaining.isEmpty()) {
            clear();
            return;
        }
        searchedItemIds = java.util.Set.copyOf(remaining);
    }

    public void searchingFor(java.util.Collection<String> itemIds) {
        this.searchedItemIds = itemIds == null || itemIds.isEmpty()
                ? java.util.Set.of() : java.util.Set.copyOf(itemIds);
    }

    private static final double GROW_PER_BLOCK = 0.002;

    private static final double FAR_FROM = 200.0;

    private static final double FAR_GROW_PER_BLOCK = 0.004;

    private static final double GROW_STEP = 0.25;

    private static final double GROW_FROM = 24.0;

    private static final double MAX_GROW = 0.75;

    private static final double BEAM_MIN_DISTANCE = 8.0;

    private static final double BEAM_HEIGHT = 48.0;

    private static final double BEAM_HEIGHT_PER_BLOCK = 0.6;

    private static final double MAX_BEAM_HEIGHT = 420.0;

    private static final float TURN_EASE_PER_SECOND = 6.0f;

    private static final float TURN_DONE_DEGREES = 0.75f;

    private static final float TURN_MAX_PER_SECOND = 150.0f;

    private static final float MAX_FRAME_SECONDS = 0.1f;

    private static final float TURN_MAX_SECONDS = 1.5f;

    private static final float BASE_LINE_WIDTH = 2.0f;
    private static final float LINE_WIDTH_PER_BLOCK = 0.02f;
    private static final float MAX_LINE_WIDTH = 5.0f;

    public boolean hasBoxes() {
        return timer.isActive() && !positions.isEmpty()
                && dev.adrian.chesttracker.client.Session.active()
                && ChestTrackerConfig.get().highlightDisplay().drawsBoxes();
    }

    private java.util.Set<Long> positionSet() {
        if (positionSetSource != positions) {
            positionSetSource = positions;
            positionSet = positions.size() < 2
                    ? java.util.Set.of() : java.util.Set.copyOf(positions);
        }
        return positionSet;
    }

    private List<Long> positionSetSource;
    private java.util.Set<Long> positionSet = java.util.Set.of();

    public void drawBoxes(PoseStack.Pose pose, VertexConsumer lines, Vec3 eye) {
        ChestTrackerConfig config = ChestTrackerConfig.get();
        boolean beams = config.guideBeam;
        float[] nearestColour = config.nearestRgb();
        float[] otherColour = config.otherRgb();

        java.util.Set<Long> present = positionSet();

        int drawn = 0;
        for (Long position : positions) {
            if (drawn >= MAX_BOXES) break;

            Entity entity = entityAt(position);
            Span span = entity != null ? SINGLE_SPAN
                    : spanAt(BlockKey.x(position), BlockKey.y(position), BlockKey.z(position));

            if (span.partner() != NO_PARTNER && present.contains(span.partner())
                    && span.partner() < position) {
                continue;
            }

            Vec3 at = entity == null ? null : livePosition(entity);
            double width = entity == null ? 0 : entity.getBbWidth();
            double x = at != null ? at.x - width / 2 : BlockKey.x(position) + span.offsetX();
            double y = at != null ? at.y : BlockKey.y(position);
            double z = at != null ? at.z - width / 2 : BlockKey.z(position) + span.offsetZ();
            double sizeX = entity != null ? width : span.sizeX();
            double sizeY = entity != null ? entity.getBbHeight() : 1.0;
            double sizeZ = entity != null ? width : span.sizeZ();

            double dx = x + sizeX / 2.0 - eye.x;
            double dy = y + sizeY / 2.0 - eye.y;
            double dz = z + sizeZ / 2.0 - eye.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            double distance = Math.sqrt(distSq);

            double limit = horizon();
            double pull = distance > limit ? limit / distance : 1.0;

            double grow = growthAt(distance);
            float lineWidth = (float) Math.round(Math.min(MAX_LINE_WIDTH,
                    BASE_LINE_WIDTH + Math.max(0.0, distance - GROW_FROM) * LINE_WIDTH_PER_BLOCK));

            boolean nearest = position == pos
                    || (span.partner() != NO_PARTNER && span.partner() == pos);
            float[] colour = nearest ? nearestColour : otherColour;

            double drawX = dx * pull - sizeX / 2.0;
            double drawY = dy * pull - sizeY / 2.0;
            double drawZ = dz * pull - sizeZ / 2.0;

            HighlightBox.emit(pose, lines, drawX, drawY, drawZ, sizeX, sizeY, sizeZ,
                    colour[0], colour[1], colour[2], 0.9f, grow, lineWidth);

            if (beams && distance > BEAM_MIN_DISTANCE) {
                double beamHeight = Math.min(MAX_BEAM_HEIGHT,
                        BEAM_HEIGHT + distance * BEAM_HEIGHT_PER_BLOCK);
                HighlightBox.beam(pose, lines,
                        drawX + sizeX / 2.0, drawY + sizeY + 0.5, drawZ + sizeZ / 2.0,
                        beamHeight * pull,
                        colour[0], colour[1], colour[2], 0.75f, lineWidth);
            }
            drawn++;
        }
    }

    private static Span spanAt(int x, int y, int z) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return SINGLE_SPAN;

        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        if (!client.level.hasChunkAt(pos)) return SINGLE_SPAN;

        net.minecraft.world.level.block.state.BlockState state = client.level.getBlockState(pos);
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) return SINGLE_SPAN;
        if (net.minecraft.world.level.block.ChestBlock.getBlockType(state)
                == net.minecraft.world.level.block.DoubleBlockCombiner.BlockType.SINGLE) {
            return SINGLE_SPAN;
        }

        net.minecraft.core.Direction direction =
                net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state);
        int dx = direction.getStepX();
        int dz = direction.getStepZ();
        int otherX = x + dx;
        int otherZ = z + dz;
        if (!BlockKey.isRepresentable(otherX, y, otherZ)) return SINGLE_SPAN;

        return new Span(Math.min(0, dx), Math.min(0, dz),
                Math.abs(dx) + 1, Math.abs(dz) + 1,
                BlockKey.pack(otherX, y, otherZ));
    }

    private record Span(int offsetX, int offsetZ, int sizeX, int sizeZ, long partner) {}

    private static final long NO_PARTNER = Long.MIN_VALUE;

    private static final Span SINGLE_SPAN = new Span(0, 0, 1, 1, NO_PARTNER);

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

    public void turnTowardsTarget() {
        if (turnSecondsLeft <= 0.0f) return;

        if (!dev.adrian.chesttracker.client.Assist.allowed()) {
            turnSecondsLeft = 0.0f;
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || positions.isEmpty() || !timer.isActive()) {
            turnSecondsLeft = 0.0f;
            return;
        }

        long now = System.nanoTime();
        float seconds = Math.min(MAX_FRAME_SECONDS, (now - turnLastFrameAt) / 1_000_000_000.0f);
        turnLastFrameAt = now;
        if (seconds <= 0.0f) return;
        turnSecondsLeft -= seconds;

        Vec3 target = centre(pos);
        double dx = target.x - player.getX();
        double dy = target.y - player.getEyeY();
        double dz = target.z - player.getZ();

        float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        float yawGap = Mth.wrapDegrees(wantYaw - player.getYRot());
        float pitchGap = wantPitch - player.getXRot();

        if (Math.abs(yawGap) < TURN_DONE_DEGREES && Math.abs(pitchGap) < TURN_DONE_DEGREES) {
            turnSecondsLeft = 0.0f;
            return;
        }

        float ease = Math.min(1.0f, TURN_EASE_PER_SECOND * seconds);
        float limit = TURN_MAX_PER_SECOND * seconds;

        player.setYRot(player.getYRot() + Mth.clamp(yawGap * ease, -limit, limit));
        player.setXRot(player.getXRot() + Mth.clamp(pitchGap * ease, -limit, limit));
    }

    private void followNearest(LocalPlayer player) {
        if (positions.size() < 2) return;
        if (entityIds.isEmpty()) {
            pos = HighlightTargets.nearest(positions,
                    player.getBlockX(), player.getBlockY(), player.getBlockZ());
            return;
        }

        long best = positions.get(0);
        double bestDistSq = Double.MAX_VALUE;
        for (Long candidate : positions) {
            double distSq = centre(candidate).distanceToSqr(player.getX(), player.getY(), player.getZ());
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = candidate;
            }
        }
        pos = best;
    }

    public boolean isActive() {
        return timer.isActive() || carriedActive();
    }

    public long pos() {
        return pos;
    }

    public void tick() {
        if (!dev.adrian.chesttracker.client.Session.active()) {
            if (isActive()) clear();
            return;
        }
        if (carriedActive() && positions.isEmpty()) return;
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
            ActionBar.guidance(
                    Component.literal(label + " - you are here").withStyle(ChatFormatting.GREEN));
            return;
        }

        ActionBar.guidance(
                Component.literal(String.format("%s  %s  %.0fm",
                        label, bearing(player), distance)).withStyle(ChatFormatting.AQUA));
    }

    private double distanceTo(LocalPlayer player) {
        if (entityAt(pos) == null) {
            return Math.sqrt(BlockKey.distanceSq(
                    BlockKey.pack(player.getBlockX(), player.getBlockY(), player.getBlockZ()), pos));
        }
        return centre(pos).distanceTo(new Vec3(player.getX(), player.getY(), player.getZ()));
    }

    private String bearing(LocalPlayer player) {
        Vec3 target = centre(pos);
        double dx = target.x - player.getX();
        double dz = target.z - player.getZ();
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

        int dy = (int) Math.floor(target.y) - player.getBlockY();
        if (dy > 3) return horizontal + " and up";
        if (dy < -3) return horizontal + " and down";
        return horizontal;
    }
}
