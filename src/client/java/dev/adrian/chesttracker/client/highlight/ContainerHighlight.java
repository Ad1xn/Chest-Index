package dev.adrian.chesttracker.client.highlight;

import dev.adrian.chesttracker.client.ActionBar;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.highlight.HighlightPulse;
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

/**
 * The container the player is currently being guided to.
 *
 * <p>Selecting a result closes the screen and starts a highlight. It persists
 * while the player is making progress towards it and fades shortly after they
 * give up - see {@link HighlightTimer}, where that rule lives and is tested.
 */
public final class ContainerHighlight {

    private static final ContainerHighlight INSTANCE = new ContainerHighlight();

    /** Close enough that the player can see the container for themselves. */
    private static final double ARRIVAL_DISTANCE = 3.0;

    /** How often progress towards the target is judged; matches the default. */
    private static final long SAMPLE_INTERVAL_MS = 1000;

    /**
     * Rebuilt on each selection from the current settings.
     *
     * <p>Built per selection rather than once: the timer holds its durations,
     * so a single instance made at startup would ignore the settings screen
     * until the game restarted - which is how these two settings came to have
     * no effect at all.
     */
    private HighlightTimer timer = HighlightTimer.defaults();

    /**
     * Every container being pointed at, nearest first is not assumed - the
     * nearest is recomputed as the player moves.
     */
    private List<Long> positions = List.of();

    /**
     * Which of {@link #positions} are entities, and which entity each one is.
     *
     * <p>A chest minecart's position is only true at the instant it is read.
     * The boxes used to be drawn from the packed positions the query answered
     * with, so a cart that was moving left its box standing in the rail bed
     * behind it - the mod pointing confidently at nothing, which is the one
     * failure it must not have.
     *
     * <p>So a position is kept as the target's <em>identity</em> - it is what
     * the nearest-target logic and {@link #forget} compare - and the entity id
     * is what the coordinates are read through, per frame, from the game
     * itself. When the entity is gone from the client (unloaded, or a chunk
     * away) the packed position is used as the last place it was seen, which
     * is the honest answer rather than no answer.
     */
    private java.util.Map<Long, Integer> entityIds = java.util.Map.of();

    private long pos;
    private String dimensionId;
    private String label;

    /**
     * The registry id of what was searched for, or null.
     *
     * <p>Kept beside the label because the label is for reading and this is
     * for comparing - marking slots in an open container needs to know which
     * item, not what it is called in the player's language.
     */
    private java.util.Set<String> searchedItemIds = java.util.Set.of();

    /** Seconds of turning left, counted down per frame. */
    private float turnSecondsLeft;

    /** When the last frame of turning was, for measuring the one after it. */
    private long turnLastFrameAt;

    /**
     * Beyond this a marker is not worth drawing.
     *
     * <p>Well past render distance on purpose: the containers hardest to find
     * are the ones in chunks that were never loaded, and those are exactly the
     * ones with no terrain drawn to hide them. Cheap, because the number of
     * markers is capped anyway.
     */
    private static final double DRAW_RADIUS = 512.0;

    /**
     * Enough to show a base's worth without filling the screen with wire.
     *
     * <p>Lowered from ninety-six: every marker is a box and a beam, so the
     * count is the one number that decides how much a search costs to draw,
     * and thirty-two containers is already more than anyone reads at once.
     */
    private static final int MAX_BOXES = 32;

    private ContainerHighlight() {}

    public static ContainerHighlight get() {
        return INSTANCE;
    }

    /** Points at one container - a row picked out of the list. */
    public void select(long pos, String dimensionId, String label) {
        select(List.of(pos), dimensionId, label);
    }

    /**
     * Points at every container holding the chosen item.
     *
     * <p>The action bar still describes only the nearest, because a bearing to
     * nine places at once is not guidance. The boxes are what say "and also
     * there, and there".
     */
    public void select(List<Long> positions, String dimensionId, String label) {
        select(positions, java.util.Map.of(), dimensionId, label);
    }

    /**
     * Points at every result of a query, entities included.
     *
     * <p>The entry point the search screens use, because a
     * {@link dev.adrian.chesttracker.core.net.QueryDto.ContainerHit} knows
     * whether it is a block or a cart and a bare position does not.
     */
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

    /** @param entityIds see {@link #entityIds}; empty when nothing found moves */
    public void select(List<Long> positions, java.util.Map<Long, Integer> entityIds,
                       String dimensionId, String label) {
        this.positions = positions == null ? List.of() : List.copyOf(positions);
        this.entityIds = entityIds == null ? java.util.Map.of() : java.util.Map.copyOf(entityIds);
        long nearest = this.positions.isEmpty() ? 0 : this.positions.get(0);
        selectPrimary(nearest, dimensionId, label);
    }

    /**
     * Where the container identified by {@code position} is <em>now</em>.
     *
     * <p>The centre of the block for anything that stands still, and the live
     * position of the entity for anything that does not. See {@link #entityIds}.
     */
    private Vec3 centre(long position) {
        Entity entity = entityAt(position);
        if (entity != null) {
            Vec3 at = livePosition(entity);
            return new Vec3(at.x, at.y + 0.5, at.z);
        }
        return new Vec3(BlockKey.x(position) + 0.5, BlockKey.y(position) + 0.5, BlockKey.z(position) + 0.5);
    }

    /**
     * Where an entity is <em>this frame</em>, not this tick.
     *
     * <p>Entities move twenty times a second and are drawn sixty or more, so
     * the game interpolates between the last two ticks when it renders them.
     * The camera is interpolated the same way. Reading {@code getX()} instead
     * takes the position from the last whole tick and pairs it with a camera
     * that is part-way to the next one, so the box lands in a slightly
     * different place relative to the cart on each frame - which is seen as a
     * box that shivers around a minecart that is itself moving smoothly.
     *
     * <p>{@code getPosition(partialTick)} is the same interpolation vanilla
     * uses to draw the entity, so the box sits still on it.
     */
    private static Vec3 livePosition(Entity entity) {
        float partialTick = Minecraft.getInstance().getDeltaTracker()
                // True: entities are frozen when the game is paused, and a box
                // that carries on interpolating past a frozen cart would drift
                // off it.
                .getGameTimeDeltaPartialTick(true);
        return entity.getPosition(partialTick);
    }

    /**
     * The still-loaded entity behind a highlighted position, or null.
     *
     * <p>Null covers both "this was never an entity" and "it has since gone",
     * and the callers want the same thing in either case: fall back to the
     * position the query gave, which is where it was last seen.
     */
    private Entity entityAt(long position) {
        if (entityIds.isEmpty()) return null;
        Integer id = entityIds.get(position);
        if (id == null) return null;
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(id);
    }

    private void selectPrimary(long pos, String dimensionId, String label) {
        // A search that found somewhere to walk replaces a carried mark, rather
        // than leaving the two of them both notionally active.
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

    /**
     * When a carried mark runs out, or 0 when there is not one.
     *
     * <p>Kept apart from {@link HighlightTimer} because that timer's whole rule
     * is "is the player still moving towards it", and a carried mark has
     * nothing to move towards - the thing is already on them. A plain deadline
     * is the honest version of "how long should this stay up".
     */
    private long carriedUntil;

    /**
     * Marks an item in whatever container is opened next, without pointing
     * anywhere in the world.
     *
     * <p>For the ender chest, whose contents are on the player rather than at
     * coordinates. Clicking an item there used to run the ordinary search,
     * which correctly found nowhere to walk to and then said "Nothing indexed
     * holds Diamond" - flatly contradicting the grid that had just shown them
     * the diamonds. There is a real answer to give: the item is in the ender
     * chest, probably inside one of the shulker boxes in it, and marking the
     * slots is what says which.
     */
    public void markCarried(String itemId, String label) {
        markCarried(itemId, label, List.of(), null);
    }

    /**
     * As above, and also points at the ender chests the player could open.
     *
     * <p>"It is in your ender chest" is only half an answer while you are
     * standing in a room with one. So the blocks are boxed like any other
     * match, the ender chest <em>item</em> is marked in whatever container is
     * open - it might be in a shulker in the chest in front of you - and the
     * item itself is marked once the ender chest is finally open. Three steps
     * of the same trail, each one marked as it becomes the next thing to do.
     *
     * @param positions ender chests to box, nearest first; may be empty
     */
    public void markCarried(String itemId, String label,
                            List<Long> positions, String dimensionId) {
        clear();
        this.searchedItemIds = java.util.Set.of(itemId);
        this.label = label;
        this.carriedUntil = System.currentTimeMillis() + ChestTrackerConfig.get().highlightDurationMs();

        if (positions == null || positions.isEmpty()) {
            this.positions = List.of();
            // Null rather than a dimension: there is no world position, and
            // tick() reads this to know not to draw guidance to one.
            this.dimensionId = null;
            this.hintItemIds = ENDER_CHEST_HINT;
            return;
        }

        // A carried mark and a place to walk to at once, which nothing else
        // does: the timer runs the guidance and the deadline runs the marking,
        // and they are about the same search.
        long carried = this.carriedUntil;
        java.util.Set<String> wanted = this.searchedItemIds;
        select(positions, dimensionId, label);
        this.carriedUntil = carried;
        this.searchedItemIds = wanted;
        this.hintItemIds = ENDER_CHEST_HINT;
    }

    /**
     * Items that are not the answer but lead to it - the ender chest, when
     * what was asked for is inside it.
     *
     * <p>Kept apart from {@link #searchedItemIds} because the two mean
     * different things to the slot marker: one is "this is your item", the
     * other is "open this next", and they are drawn in different colours for
     * exactly that reason.
     */
    private java.util.Set<String> hintItemIds = java.util.Set.of();

    private static final java.util.Set<String> ENDER_CHEST_HINT =
            java.util.Set.of("minecraft:ender_chest");

    /** Container items worth opening next; see {@link #hintItemIds}. */
    public java.util.Set<String> hintItemIds() {
        return hintItemIds;
    }

    /** Whether a carried mark is still up. */
    private boolean carriedActive() {
        return carriedUntil > 0 && System.currentTimeMillis() < carriedUntil;
    }

    /**
     * Whether this position is one of the ones being pointed at.
     *
     * <p>Asked for every block change on the client's copy of the world, which
     * on a busy server is a great many - so it walks the list by hand rather
     * than through {@code contains}, which would box the key each time. The
     * list is empty whenever no search is being shown, which is almost always,
     * and then this does nothing at all.
     */
    public boolean holds(long position) {
        for (int i = 0; i < positions.size(); i++) {
            if (positions.get(i) == position) return true;
        }
        return false;
    }

    /**
     * Drops one container from the highlight, because it no longer exists.
     *
     * <p>The last one going takes the whole highlight with it: boxes over
     * nothing are worse than no boxes, and there is nothing left to guide to.
     */
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

    /**
     * The registry ids being looked for, so open containers can mark them.
     *
     * <p>Empty when nothing is being searched for. A set rather than one id
     * because a search can legitimately be about many at once - everything
     * inside a shulker box, or everything a schematic still needs - and the
     * marks in an open container should show all of them, not an arbitrary
     * one.
     *
     * <p>Returned as an immutable set whose identity changes only when the
     * search does, which is what lets the slot marker cache its resolved items
     * instead of parsing registry ids every frame.
     */
    public java.util.Set<String> searchedItemIds() {
        return searchedItemIds;
    }

    /** Names the item a selection is about, so open containers can mark it. */
    public void searchingFor(String itemId) {
        this.searchedItemIds = itemId == null ? java.util.Set.of() : java.util.Set.of(itemId);
    }

    /**
     * Drops one item from what is being marked, because the player has it now.
     *
     * <p>The mark exists to answer "which slot"; once the cursor is on that
     * slot, or the stack is on the cursor, it has been answered and going on
     * pulsing is the mod still shouting after the question was settled.
     *
     * <p>The last one going takes the whole highlight with it - the boxes point
     * at containers holding a thing the player is now carrying.
     */
    public void retire(String itemId) {
        if (itemId == null || !searchedItemIds.contains(itemId)) return;

        java.util.Set<String> remaining = new java.util.HashSet<>(searchedItemIds);
        remaining.remove(itemId);
        if (remaining.isEmpty()) {
            clear();
            return;
        }
        // Replaced rather than edited: the slot marker caches what it resolved
        // against this set's identity.
        searchedItemIds = java.util.Set.copyOf(remaining);
    }

    /** Names every item a selection is about. */
    public void searchingFor(java.util.Collection<String> itemIds) {
        this.searchedItemIds = itemIds == null || itemIds.isEmpty()
                ? java.util.Set.of() : java.util.Set.copyOf(itemIds);
    }

    /**
     * How much bigger a box gets per block of distance.
     *
     * <p>A one-block cube is a couple of pixels across at a hundred blocks -
     * findable only if you already know where to look, which defeats the point.
     * Growing it with distance keeps roughly the apparent size instead of
     * shrinking to nothing, so scanning a base for the box actually works.
     */
    private static final double GROW_PER_BLOCK = 0.002;

    /** Where "far" begins, and the box starts growing in earnest. */
    private static final double FAR_FROM = 200.0;

    /** Growth per block past {@link #FAR_FROM}. */
    private static final double FAR_GROW_PER_BLOCK = 0.004;

    /**
     * Growth is snapped to this, and line width to a whole pixel.
     *
     * <p>Because growth depends on distance, a box that grows smoothly also
     * <em>shrinks</em> smoothly as the player walks towards it - and a box
     * quietly changing size every frame does not read as a box changing size.
     * It reads as a box that will not sit still on the chest, which is exactly
     * how it was reported. Snapping means it changes in occasional steps
     * instead, and holds still in between.
     */
    private static final double GROW_STEP = 0.25;

    /**
     * Distance at which a box starts growing at all.
     *
     * <p>Nothing nearer gets any bigger than the block it sits on. Growing
     * from zero distance made close boxes fat enough to lose the chest inside
     * them, which is the opposite of pointing at it - and up close the box is
     * perfectly legible at its true size.
     */
    private static final double GROW_FROM = 24.0;

    /**
     * Past this the box stops growing.
     *
     * <p>Cut hard, twice. The box is the precise half of a marker: it says
     * which block, and a box that fills the screen from four hundred blocks
     * away says nothing at all - it is not a bigger answer, it is a lost one.
     * Distance is the trail's job, and the trail is what was supposed to grow.
     * Three quarters of a block of swell is enough to keep the outline from
     * collapsing into a dot without it ever stopping being a box.
     */
    private static final double MAX_GROW = 0.75;

    /**
     * The shortest a trail may be, for a container already at the build limit.
     *
     * <p>The trail runs from the container to the top of the world, which for
     * one built on the roof is no distance at all - and a trail of zero height
     * is fourteen marks stacked on one another, which is a dot. This is the
     * floor under that, so the marks are still a column.
     */
    private static final double MIN_BEAM_HEIGHT = 24.0;

    /** Where the world ends when it has not said - overworld, since 1.18. */
    private static final int DEFAULT_WORLD_TOP = 320;

    /**
     * How much of what is left to turn is taken per second.
     *
     * <p>Per second, and applied per frame, because a turn driven from the game
     * tick moves the view twenty times a second however fast the game is
     * drawing. Vanilla's own interpolation hides some of that but not the
     * change in speed, which is what still read as steppy at a hundred frames a
     * second. Framerate now changes how smooth it looks, not how fast it turns.
     */
    private static final float TURN_EASE_PER_SECOND = 6.0f;

    /** Close enough to stop turning; below this the correction is invisible. */
    private static final float TURN_DONE_DEGREES = 0.75f;

    /**
     * The fastest the view may swing, in degrees a second.
     *
     * <p>Easing alone still snapped: a quarter of a hundred-and-eighty degree
     * gap is a huge first step, and no amount of smoothing after that hides it.
     * Capping the speed makes a long turn take longer rather than start
     * violently.
     */
    private static final float TURN_MAX_PER_SECOND = 150.0f;

    /** A frame longer than this is a stutter; treat it as one frame's worth. */
    private static final float MAX_FRAME_SECONDS = 0.1f;

    /** A turn is abandoned after this, so it can never fight the mouse for long. */
    private static final float TURN_MAX_SECONDS = 1.5f;

    private static final float BASE_LINE_WIDTH = 2.0f;
    private static final float LINE_WIDTH_PER_BLOCK = 0.02f;
    private static final float MAX_LINE_WIDTH = 5.0f;

    /**
     * Whether there is anything for the world renderer to draw.
     *
     * <p>Asked every frame whether or not anything is highlighted, which is
     * almost always nothing - so the two free field tests come first and the
     * two that do work (walking the connection, and parsing the display mode
     * out of the config's string) are only reached when there is really a
     * highlight to draw.
     */
    public boolean hasBoxes() {
        return timer.isActive() && !positions.isEmpty()
                && dev.adrian.chesttracker.client.Session.active()
                && ChestTrackerConfig.get().highlightDisplay().drawsBoxes();
    }

    /**
     * Whether anything is far enough away to have a trail stood on it.
     *
     * <p>Asked before the trail pass is submitted at all, because the trails
     * are drawn in a second pass against a different render type and a pass
     * that turns out to emit nothing still costs a node, a buffer and a draw.
     * In a base - which is where this mod is used most - nothing is ever twenty
     * chunks away and the answer is no on every frame.
     */
    public boolean hasTrails() {
        if (!hasBoxes() || !ChestTrackerConfig.get().guideBeam) return false;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;

        double from = ChestTrackerConfig.get().guideBeamFromBlocks();
        double fromSq = from * from;
        Vec3 eye = client.player.position();
        for (Long position : positions) {
            double dx = BlockKey.x(position) + 0.5 - eye.x;
            double dy = BlockKey.y(position) + 0.5 - eye.y;
            double dz = BlockKey.z(position) + 0.5 - eye.z;
            if (dx * dx + dy * dy + dz * dz >= fromSq) return true;
        }
        return false;
    }

    /**
     * The highlighted positions as a set, rebuilt only when they change.
     *
     * <p>Used to spot a double chest whose halves are both hits. It was a fresh
     * {@code Set.copyOf} every frame for a list that only moves when a new
     * search runs.
     */
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

    /**
     * Which of the two passes a walk of the markers is drawing.
     *
     * <p>Two passes rather than one because the box and the trail are drawn
     * against different render types - the box through the world, the trail
     * behind it - and a vertex consumer is opened on exactly one. The geometry
     * either one needs is worked out the same way, so the walk is shared and
     * only the emitting differs.
     */
    private enum Pass { BOXES, TRAILS }

    /**
     * Draws a box around each highlighted container, through whatever is in
     * front of it.
     *
     * <p>Called from the render thread, once per frame, so it reads state and
     * allocates nothing. Positions far enough away to be off screen are skipped
     * rather than drawn and clipped - a base with hundreds of matching barrels
     * would otherwise submit hundreds of boxes to be thrown away.
     *
     * @param eye the camera position; boxes are drawn relative to it
     */
    public void drawBoxes(PoseStack.Pose pose, VertexConsumer lines, Vec3 eye) {
        draw(pose, lines, eye, Pass.BOXES);
    }

    /**
     * Stands a trail of marks on every match far enough away to need one.
     *
     * <p>A separate call from {@link #drawBoxes} because it is drawn against
     * the world rather than through it - see {@code HighlightRenderTypes}.
     */
    public void drawTrails(PoseStack.Pose pose, VertexConsumer lines, Vec3 eye) {
        draw(pose, lines, eye, Pass.TRAILS);
    }

    /**
     * The colour of the marker being drawn this iteration.
     *
     * <p>One array, reused down the loop and between frames. The pulse is the
     * same for every marker in a frame but the resting colour is not - the
     * nearest does not pulse at all - so the mix is per marker, and a fresh
     * three-float array per marker per frame is a few thousand allocations a
     * second to say something about a colour. Render thread only.
     */
    private final float[] pulsed = new float[3];

    private void draw(PoseStack.Pose pose, VertexConsumer lines, Vec3 eye, Pass pass) {
        // Read once. This runs per container per frame, and the config lookup
        // does not change between two boxes of the same frame.
        ChestTrackerConfig config = ChestTrackerConfig.get();
        float[] nearestColour = config.nearestRgb();
        float[] otherColour = config.otherRgb();

        // One phase for the whole frame, so the markers breathe together rather
        // than each on its own clock - which would read as flickering rather
        // than as one thing the mod is saying about all of them.
        float pulse = HighlightPulse.at(System.currentTimeMillis());
        HighlightPulse.mix(otherColour, nearestColour, pulse, pulsed);

        double trailFrom = pass == Pass.TRAILS ? config.guideBeamFromBlocks() : 0.0;
        int worldTop = worldTop();

        // Which positions are in play, so a double chest whose halves are both
        // hits can be recognised as one chest rather than drawn twice.
        java.util.Set<Long> present = positionSet();

        int drawn = 0;
        for (int index = 0; index < positions.size(); index++) {
            if (drawn >= MAX_BOXES) break;
            long position = positions.get(index);

            // A double chest is two blocks the player thinks of as one, and the
            // index agrees with the game rather than with the player: each half
            // is a container of its own, so an item in both halves is two hits.
            // Drawn literally that is two boxes with a line down the middle of
            // one chest, two trails, and one half picked out as "nearest" -
            // which reads as the mod pointing at half a chest.
            // An entity is wherever it is this frame, and is its own size;
            // only a block can be half of a double chest.
            Entity entity = entityAt(position);
            Span span = entity != null ? SINGLE_SPAN : spanFor(index, position);

            // Both halves hit: one of them draws the pair and the other stands
            // down. Either may do it - the box is anchored at the pair's lower
            // corner from whichever half is asked - so the tie is broken on the
            // packed value simply to make it the same one every frame.
            if (span.partner() != NO_PARTNER && present.contains(span.partner())
                    && span.partner() < position) {
                continue;
            }

            // The box's lower corner, and how far it reaches. For an entity
            // both come off the entity itself: the position interpolated to
            // this frame, so the box travels with the cart rather than standing
            // where it was when the query was answered, and the size from its
            // own hitbox, so a chest boat gets a boat-shaped box and a minecart
            // a minecart-shaped one instead of both being drawn as a cube.
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

            // Counted as drawn either way, so the cap picks the same markers in
            // both passes and a trail can never belong to a box that was cut.
            drawn++;

            if (pass == Pass.TRAILS && distance < trailFrom) continue;

            // Past the far plane nothing is drawn at all - the projection
            // clips it, which is why a container thousands of blocks away
            // showed nothing whatever its size. Those are pulled in to the
            // edge of what can be drawn and marked there, pointing the right
            // way, like a waypoint on the horizon. The size still comes from
            // the real distance, so a clamped marker reads as a far one.
            double limit = horizon();
            double pull = distance > limit ? limit / distance : 1.0;

            // Both the box and its lines grow with distance, so a container
            // across a base is something you can find by looking rather than
            // something you have to already be pointing at.
            double grow = growthAt(distance);
            float lineWidth = (float) Math.round(Math.min(MAX_LINE_WIDTH,
                    BASE_LINE_WIDTH + Math.max(0.0, distance - GROW_FROM) * LINE_WIDTH_PER_BLOCK));

            // The nearest one holds the accent colour outright; the rest rest
            // at the other colour and swell through it. That is what picks out
            // the one the guidance is talking about, without a second shape.
            // A pair drawn as one box is the nearest if either of its halves is.
            boolean nearest = position == pos
                    || (span.partner() != NO_PARTNER && span.partner() == pos);
            float[] colour = nearest ? nearestColour : pulsed;

            double drawX = dx * pull - sizeX / 2.0;
            double drawY = dy * pull - sizeY / 2.0;
            double drawZ = dz * pull - sizeZ / 2.0;

            if (pass == Pass.BOXES) {
                HighlightBox.emit(pose, lines, drawX, drawY, drawZ, sizeX, sizeY, sizeZ,
                        colour[0], colour[1], colour[2], 0.9f, grow, lineWidth);
                continue;
            }

            // The column is what carries at range, and the only part of this
            // that means anything where no terrain is drawn to place it. It
            // runs from the top of the container to the build limit rather than
            // to a height worked out from the distance: the limit is a real
            // place in the world, so the trail ends where the world does and
            // reads as standing in it.
            double height = Math.max(MIN_BEAM_HEIGHT, worldTop - (y + sizeY));
            HighlightBox.beam(pose, lines,
                    drawX + sizeX / 2.0, drawY + sizeY, drawZ + sizeZ / 2.0,
                    height * pull,
                    colour[0], colour[1], colour[2], 0.75f, lineWidth);
        }
    }

    /**
     * The build limit of the world the player is in.
     *
     * <p>Asked of the level rather than assumed to be 320: a superflat, a
     * datapack or the nether all put it somewhere else, and a trail that
     * overshoots into empty sky is as wrong as one that stops short.
     */
    private static int worldTop() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? DEFAULT_WORLD_TOP : client.level.getMaxY() + 1;
    }

    /**
     * How often a box re-asks the world what shape it is.
     *
     * <p>Twice a second rather than sixty times. The question is whether this
     * block is half of a double chest, and the answer changes only when
     * somebody builds or breaks one - but asking it costs a {@code BlockPos},
     * a chunk test and a block-state lookup, per box, per frame.
     */
    private static final long SPAN_CACHE_MS = 500;

    /** Spans for the current {@link #positions}, and when they were worked out. */
    private List<Long> spanSource;
    private Span[] spans = new Span[0];
    private long spansAt;

    /**
     * The span of one highlighted position, remembered between frames.
     *
     * <p>Keyed by index into {@link #positions} rather than by the packed
     * position, so reading it costs an array access and boxes nothing. The
     * whole set is dropped when the selection changes or the timer runs out,
     * which also covers a chest that gained or lost its other half while the
     * highlight was standing on it.
     */
    private Span spanFor(int index, long position) {
        long now = System.currentTimeMillis();
        if (spanSource != positions || spans.length != positions.size()
                || now - spansAt >= SPAN_CACHE_MS) {
            spans = new Span[positions.size()];
            spanSource = positions;
            spansAt = now;
        }

        Span cached = spans[index];
        if (cached != null) return cached;

        Span span = spanAt(BlockKey.x(position), BlockKey.y(position), BlockKey.z(position));
        spans[index] = span;
        return span;
    }

    /**
     * How far the marker at a position has to reach to cover the whole
     * container: {@code {offsetX, offsetZ, sizeX, sizeZ}}.
     *
     * <p>Only the double chest needs this, and only when its chunk is loaded -
     * which is exactly when the player can see that the box is wrong. Out at
     * the horizon, where the block state is not available, one block is both
     * all we can know and all the difference the eye could tell.
     *
     * <p>Asked per box per frame, but there are at most thirty-two of them and
     * a loaded block state is a lookup, not a load.
     */
    private static Span spanAt(int x, int y, int z) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return SINGLE_SPAN;

        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        // Never force a chunk to load for the sake of a box.
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

        // The neighbour can be on either side; the box starts at whichever
        // block is lower on that axis.
        return new Span(Math.min(0, dx), Math.min(0, dz),
                Math.abs(dx) + 1, Math.abs(dz) + 1,
                BlockKey.pack(otherX, y, otherZ));
    }

    /**
     * How far a marker at a position has to reach to cover the whole container,
     * and which other position is the same container.
     *
     * @param offsetX where the box starts relative to this block
     * @param partner the other half of a double chest, or {@link #NO_PARTNER}
     */
    private record Span(int offsetX, int offsetZ, int sizeX, int sizeZ, long partner) {}

    /** No other block is part of this container. */
    private static final long NO_PARTNER = Long.MIN_VALUE;

    /** The answer for everything that is not half of a double chest. */
    private static final Span SINGLE_SPAN = new Span(0, 0, 1, 1, NO_PARTNER);

    /**
     * How far out geometry can still be drawn.
     *
     * <p>The projection's far plane follows the render distance, so anything
     * beyond it is clipped no matter how large it is drawn. Held just inside
     * that, because a marker sitting exactly on the plane flickers in and out
     * as the camera moves.
     */
    private static double horizon() {
        Minecraft client = Minecraft.getInstance();
        int chunks = client.options == null ? 8 : client.options.getEffectiveRenderDistance();
        return Math.max(48.0, chunks * 16.0 * 0.85);
    }

    /**
     * How much larger than its block a marker is drawn at a given distance.
     *
     * <p>Two rates. Up to two hundred blocks the box only has to stay legible,
     * so it barely grows; past that it is competing with the horizon and grows
     * five times as fast. The result is snapped to half a block so that walking
     * changes it in steps rather than continuously - see {@link #GROW_STEP}.
     */
    private static double growthAt(double distance) {
        double grow = Math.max(0.0, distance - GROW_FROM) * GROW_PER_BLOCK;
        if (distance > FAR_FROM) grow += (distance - FAR_FROM) * FAR_GROW_PER_BLOCK;
        grow = Math.min(MAX_GROW, grow);
        return Math.round(grow / GROW_STEP) * GROW_STEP;
    }

    /**
     * Eases the view round to face the nearest match.
     *
     * <p>Over a few ticks rather than in one frame: snapping the camera is
     * disorienting, and a player who was already turning finds themselves
     * fighting it. Short enough to be over before it feels like a fight.
     */
    public void turnTowardsTarget() {
        if (turnSecondsLeft <= 0.0f) return;

        // A view that moves on its own is what an anti-cheat calls aim assist,
        // and rotation reaches the server in the ordinary movement packets -
        // so on somebody else's server this does nothing at all unless it has
        // been deliberately allowed. The boxes still point the way; they are
        // drawn on this machine and the server never learns they exist.
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

        // Shortest way round, so facing 179 and wanting -179 turns two degrees
        // rather than three hundred and fifty eight.
        float yawGap = Mth.wrapDegrees(wantYaw - player.getYRot());
        float pitchGap = wantPitch - player.getXRot();

        // Arrived. Stopping here rather than nudging on for another second is
        // what keeps it from feeling like the mouse is being held.
        if (Math.abs(yawGap) < TURN_DONE_DEGREES && Math.abs(pitchGap) < TURN_DONE_DEGREES) {
            turnSecondsLeft = 0.0f;
            return;
        }

        // The ease is expressed per second and converted for this frame's
        // length, so the curve is the same at thirty frames a second as at two
        // hundred - only the number of steps along it changes.
        float ease = Math.min(1.0f, TURN_EASE_PER_SECOND * seconds);
        float limit = TURN_MAX_PER_SECOND * seconds;

        player.setYRot(player.getYRot() + Mth.clamp(yawGap * ease, -limit, limit));
        player.setXRot(player.getXRot() + Mth.clamp(pitchGap * ease, -limit, limit));
    }

    /**
     * Re-points the guidance at whichever highlighted container is closest now.
     *
     * <p>The choosing itself lives in {@code core} so it can be tested without
     * a game - which is where it caught that height has to count, a case this
     * loop got right but nothing was checking.
     */
    private void followNearest(LocalPlayer player) {
        if (positions.size() < 2) return;
        if (entityIds.isEmpty()) {
            pos = HighlightTargets.nearest(positions,
                    player.getBlockX(), player.getBlockY(), player.getBlockZ());
            return;
        }

        // Anything that moves has to be measured from where it is, so this
        // cannot go through the packed-position version. The rule is the same
        // one, and the reason it is not shared is that the core has no way to
        // ask the game where an entity got to.
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

    /**
     * Advances the highlight and shows guidance.
     *
     * <p>Guidance goes to the action bar rather than a custom HUD overlay: it is
     * plain vanilla API, behaves identically on every supported version, and in
     * a base of any size a bearing and a distance are more use than an outline
     * the player cannot see through a wall anyway.
     */
    public void tick() {
        // Switched off mid-guidance - the player joined a server on their own
        // off-limits list. Nothing left standing, rather than boxes with
        // nothing behind them.
        if (!dev.adrian.chesttracker.client.Session.active()) {
            if (isActive()) clear();
            return;
        }
        // A carried mark with nowhere to walk to has no bearing to write and no
        // dimension it can be left. It just runs out on its own. One that does
        // have ender chests to point at falls through and guides to them like
        // any other search.
        if (carriedActive() && positions.isEmpty()) return;
        if (!timer.isActive()) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            timer.clear();
            return;
        }
        if (!player.level().dimension().identifier().toString().equals(dimensionId)) {
            // Guidance across dimensions would be nonsense.
            timer.clear();
            return;
        }

        // Walking past one of them makes another the nearest; the arrow should
        // follow rather than keep pointing behind.
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

        // Guidance, not an answer: it yields to whatever the player last asked
        // for, and is restated a tick later anyway.
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

    /** Where the container is relative to where the player is facing. */
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
