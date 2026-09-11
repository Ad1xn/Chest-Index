package dev.adrian.chesttracker.client.index;

import dev.adrian.chesttracker.client.net.ServerLink;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.model.ContainerRecord;
import dev.adrian.chesttracker.core.model.Origin;
import dev.adrian.chesttracker.core.model.StackEntry;
import dev.adrian.chesttracker.core.net.QueryDto;
import dev.adrian.chesttracker.core.util.BlockKey;
import dev.adrian.chesttracker.platform.ChunkPosCompat;
import dev.adrian.chesttracker.platform.ContainerTypes;
import dev.adrian.chesttracker.platform.LiveContainerReader;
import dev.adrian.chesttracker.server.TrackerService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the client-side index out of what the player can already see.
 *
 * <p>Two sources, and neither of them asks the server for anything:
 *
 * <ul>
 *   <li><b>Where containers are</b>, from chunks the server has already sent.
 *       A chunk packet carries every block entity in it, so the client knows a
 *       chest is at a position the moment it can render one. It carries no
 *       inventory, which is why this half can only ever record a location.
 *   <li><b>What is in them</b>, from containers the player opened themselves.
 *       The stacks in an open menu are the ones already on screen; reading them
 *       is reading this client's own memory.
 * </ul>
 *
 * <p><b>What this deliberately does not do.</b> It never opens anything, never
 * moves the player, and never sends a packet. There is no scanning, no walking
 * a chest line, no interaction the player did not make. A vanilla server cannot
 * tell this code is running, because from its side nothing is different - which
 * is the entire requirement, since the servers this exists for are the ones
 * that would ban you for the alternative.
 *
 * <p>The consequence is honest and worth stating in the UI: a chest is empty
 * here until you open it once.
 */
public final class ClientObserver {

    private ClientObserver() {}

    /**
     * How long after a block is right-clicked a container screen still counts
     * as belonging to it.
     *
     * <p>The client is never told which block a menu came from - the server
     * just sends "open a container with these slots". Pairing it with the
     * interaction that caused it is the only way to know, and on a laggy server
     * the reply can take a moment. Long enough to survive that, short enough
     * that an unrelated screen opening later is not attributed to a chest the
     * player walked away from.
     */
    private static final long ATTRIBUTION_WINDOW_MS = 2000;

    /** The last block this player right-clicked, and when. */
    private static long lastUsePos;
    private static String lastUseDimension;
    private static long lastUseAt;

    /** The container screen currently being watched, and where it came from. */
    private static Screen watching;
    private static long watchingPos;
    private static String watchingDimension;

    /**
     * The last screen {@link #beginWatching} was offered, whether or not it
     * could be attributed to a block.
     *
     * <p>Tracked separately from {@link #watching} because a screen is
     * re-initialised on every window resize. Keying off {@code watching} alone
     * would re-run attribution for any screen that failed it the first time,
     * and register a second close listener on it each time the player resized
     * the window.
     */
    private static Screen considered;

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            forget();
            considered = null;
            // A new server is a new answer to "is the mod on here", so it is
            // worth saying again if it turns out to be off.
            dev.adrian.chesttracker.client.Session.forget();
            // And a dimension pinned in the last world says nothing about this
            // one, where it is most likely simply empty.
            dev.adrian.chesttracker.client.ui.ChestTrackerScreen.forgetPinnedDimension();
            dev.adrian.chesttracker.client.ui.ChestTrackerScreen.forgetItemNames();
            // The search box completes from this world's registries - its
            // enchantments are its datapacks' - so last world's are wrong here.
            dev.adrian.chesttracker.client.SearchCategories.forget();
            // A highlight outlives the world it was found in. Its only check is
            // that the dimension id still matches, and "minecraft:overworld" is
            // the overworld of whatever server you just joined - so a search run
            // shortly before a transfer drew boxes and gave a bearing for
            // coordinates in the world you had left. The same for a material
            // list, which is counted against an inventory that is now somewhere
            // else.
            dev.adrian.chesttracker.client.highlight.ContainerHighlight.get().clear();
            dev.adrian.chesttracker.client.MaterialGoals.clear();
            if (enabled()) ClientIndex.bind();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            forget();
            considered = null;
            // As on join: nothing found in the world being left should still be
            // pointed at in the next one.
            dev.adrian.chesttracker.client.highlight.ContainerHighlight.get().clear();
            dev.adrian.chesttracker.client.MaterialGoals.clear();
            // Writes out what this session saw before the index is dropped.
            ClientIndex.unbind();
        });

        // Fires on both sides; only the client's copy is ours to read, and the
        // result must always be PASS - returning anything else would cancel the
        // player's own interaction.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() && player == Minecraft.getInstance().player) {
                lastUsePos = packed(hit.getBlockPos());
                lastUseDimension = dimensionOf(level);
                lastUseAt = System.currentTimeMillis();
            }
            return InteractionResult.PASS;
        });

        ClientChunkEvents.CHUNK_LOAD.register(ClientObserver::onChunkLoad);

        // A container that stops existing has to leave the index now, not at
        // the next time its chunk happens to be sent again. Breaking a chest
        // and still being shown its contents is the index lying about the
        // world, which is worse than knowing nothing about it.
        dev.adrian.chesttracker.platform.ClientBlockChanges.listen(ClientObserver::onBlockChanged);

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            // A screen is re-initialised on every resize. Only the first one
            // pairs with an interaction; a later one would read an interaction
            // from whenever the player last right-clicked something.
            if (screen == considered) return;
            considered = screen;
            beginWatching(screen);
        });
    }

    // --- Where containers are -----------------------------------------------

    /**
     * Records every container in a chunk the server just sent, and drops the
     * ones it says are no longer there.
     *
     * <p>Contents already known are never overwritten. A chunk load tells us a
     * chest exists, not what is in it, so writing a location-only record over
     * one whose contents the player opened last week would silently throw those
     * contents away - which is most of what this feature is for.
     */
    private static void onChunkLoad(ClientLevel level, LevelChunk chunk) {
        TrackerService tracker = active();
        if (tracker == null) return;

        String dimensionId = dimensionOf(level);
        long chunkKey = BlockKey.chunkKey(
                ChunkPosCompat.x(chunk.getPos()), ChunkPosCompat.z(chunk.getPos()));

        // Nothing in it and nothing recorded in it. Most chunks a player walks
        // through are exactly that, and this runs for every one of them.
        Map<BlockPos, BlockEntity> blockEntities = chunk.getBlockEntities();
        if (blockEntities.isEmpty()
                && tracker.index(dimensionId).positionsInChunk(chunkKey).isEmpty()) {
            return;
        }

        int dimensionKey = tracker.palette().intern(dimensionId);
        long tick = level.getGameTime();
        long before = tracker.generation(dimensionId);

        for (Map.Entry<BlockPos, BlockEntity> entry : blockEntities.entrySet()) {
            BlockEntity blockEntity = entry.getValue();
            if (!ContainerTypes.isContainer(blockEntity)) continue;

            BlockPos pos = entry.getKey();
            if (!BlockKey.isRepresentable(pos.getX(), pos.getY(), pos.getZ())) continue;

            String typeId = ContainerTypes.idOf(blockEntity);
            if (typeId == null) continue;

            long key = BlockKey.pack(pos.getX(), pos.getY(), pos.getZ());

            // Already known, contents and all. Leave it exactly as it is.
            if (tracker.index(dimensionId).get(key) != null) continue;

            tracker.record(dimensionId, ContainerRecord.locationOnly(
                    key, dimensionKey, tracker.palette().intern(typeId), Origin.UNKNOWN, tick));
        }

        dropBrokenContainers(tracker, dimensionId, chunkKey, chunk);
        // Only if the chunk actually said something new - which most of them do
        // not, on ground already walked. Marking the index dirty for every
        // chunk load meant the autosave fired every minute for the life of the
        // session and rewrote files that had not changed.
        if (tracker.generation(dimensionId) != before) ClientIndex.touch();
    }

    /**
     * Removes indexed containers the chunk says are gone - and only those.
     *
     * <p>The server's own scanner reconciles against "every container the chunk
     * has", because on the server a chunk is complete by definition. A client's
     * copy is not: anti-xray plugins, chunk-data strippers and outright
     * malicious servers all send chunks with things left out, and this index is
     * built for exactly the servers most likely to do that. Reconciling that way
     * here would quietly delete chests the player had opened - the one thing in
     * this index that cannot be re-observed by walking past.
     *
     * <p>So the test is air, which is not ambiguous. A chest that was broken
     * leaves air behind and goes; a chest a server declines to tell us about
     * stays, at worst slightly stale. Losing a real record is much worse than
     * keeping a dead one, and only one of the two is recoverable.
     */
    private static void dropBrokenContainers(TrackerService tracker, String dimensionId,
                                             long chunkKey, LevelChunk chunk) {
        Set<Long> indexed = tracker.index(dimensionId).positionsInChunk(chunkKey);
        if (indexed.isEmpty()) return;

        // Copied, because removing walks the same set.
        for (long pos : new ArrayList<>(indexed)) {
            BlockPos blockPos = new BlockPos(BlockKey.x(pos), BlockKey.y(pos), BlockKey.z(pos));
            if (chunk.getBlockState(blockPos).isAir()) {
                tracker.remove(dimensionId, pos);
            }
        }
    }

    /**
     * Drops a container the world says is no longer there - from the index if
     * this client keeps one, and from the highlight either way.
     *
     * <p>Only ever removes, and only when the position is one of those two
     * knows about and the block there is now not a container. Every other
     * change through {@code setBlock} - a torch placed next to a chest, a
     * server correcting a block a hundred blocks away - costs a walk of an
     * empty list and at most one map lookup.
     *
     * <p>Unlike the chunk-load reconciliation this does not need the air test.
     * A {@code setBlock} is a definite statement that the block at this
     * position is now that one; it is not a chunk that may have had things
     * left out of it.
     */
    private static void onBlockChanged(Level level, BlockPos pos) {
        if (level != Minecraft.getInstance().level) return;
        if (!BlockKey.isRepresentable(pos.getX(), pos.getY(), pos.getZ())) return;

        long key = BlockKey.pack(pos.getX(), pos.getY(), pos.getZ());

        // Two separate reasons to care, and they do not come together. The
        // index only exists on a vanilla server; the highlight is the client's
        // own however the search was answered, so a box left standing on a
        // broken chest has to be taken down in singleplayer and on a server
        // running the mod too - where there is no client index at all. Asking
        // for the tracker first, as this did, meant the box only ever came down
        // in the one case out of three where a client index happened to exist.
        dev.adrian.chesttracker.client.highlight.ContainerHighlight highlight =
                dev.adrian.chesttracker.client.highlight.ContainerHighlight.get();
        boolean highlighted = highlight.holds(key);

        TrackerService tracker = active();
        String dimensionId = tracker == null ? null : dimensionOf(level);
        boolean indexed = tracker != null && tracker.index(dimensionId).get(key) != null;

        // Every other change through setBlock - a torch placed next to a chest,
        // a server correcting a block a hundred blocks away - is two cheap
        // tests and nothing else. Only a position one of them knows about is
        // worth reading the block entity for.
        if (!highlighted && !indexed) return;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && ContainerTypes.isContainer(blockEntity)) return;

        if (indexed) {
            tracker.remove(dimensionId, key);
            ClientIndex.touch();
        }
        // The boxes are drawn from a list captured when the search ran, so one
        // standing on a chest that has just been broken would keep standing
        // there until the highlight timed out.
        if (highlighted) highlight.forget(key);
    }

    // --- What is in them ----------------------------------------------------

    /**
     * Pairs a freshly opened container screen with the block that opened it.
     *
     * <p>Nothing in the packet that opens a menu says which block it belongs
     * to, so the only honest answer is the one the player just right-clicked.
     * If that does not line up - no recent interaction, or the block there is
     * not a container - the screen is left unwatched rather than guessed at.
     * Attributing a plugin's menu, or a shulker opened from an inventory, to
     * whatever chest happened to be under the crosshair would put items in the
     * index that are not in the world.
     */
    private static void beginWatching(Screen screen) {
        forget();

        TrackerService tracker = active();
        if (tracker == null) return;
        if (System.currentTimeMillis() - lastUseAt > ATTRIBUTION_WINDOW_MS) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !dimensionOf(client.level).equals(lastUseDimension)) return;

        BlockPos pos = new BlockPos(
                BlockKey.x(lastUsePos), BlockKey.y(lastUsePos), BlockKey.z(lastUsePos));
        BlockEntity blockEntity = client.level.getBlockEntity(pos);
        if (blockEntity == null || !ContainerTypes.isContainer(blockEntity)) return;

        watching = screen;
        watchingPos = lastUsePos;
        watchingDimension = lastUseDimension;

        // Captured on close rather than on open, so it records what the player
        // left behind rather than what they found - they are usually there to
        // take something out.
        ScreenEvents.remove(screen).register(closed -> {
            if (closed == watching) capture((AbstractContainerScreen<?>) closed);
            forget();
            // Dropped here as well as in forget(), so a closed screen - which
            // holds its widgets and its menu - is not kept alive until the next
            // container is opened.
            if (closed == considered) considered = null;
        });
    }

    /**
     * Writes an open container's stacks into the index.
     *
     * <p>Slots backed by the player's own inventory are skipped: every
     * container screen shows them, and counting them would report the contents
     * of the player's pockets as being inside every chest they ever opened.
     */
    private static void capture(AbstractContainerScreen<?> screen) {
        TrackerService tracker = active();
        if (tracker == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        BlockPos pos = new BlockPos(
                BlockKey.x(watchingPos), BlockKey.y(watchingPos), BlockKey.z(watchingPos));
        BlockEntity blockEntity = client.level.getBlockEntity(pos);
        if (blockEntity == null || !ContainerTypes.isContainer(blockEntity)) return;

        String typeId = ContainerTypes.idOf(blockEntity);
        if (typeId == null) return;
        tracker.containerTypes().learn(typeId);

        Inventory playerInventory = client.player.getInventory();
        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == playerInventory) continue;
            stacks.add(slot.getItem());
        }
        // No container half at all means this was not the kind of screen we can
        // learn anything from.
        if (stacks.isEmpty()) return;

        // An ender chest is not the block's contents, it is the player's, and
        // the same six rows follow them to every ender chest in the world.
        // Filed under the reserved view rather than at these coordinates.
        if ("minecraft:ender_chest".equals(typeId)) {
            store(tracker, QueryDto.ENDER_CHEST, 0L, typeId, stacks, client.level.getGameTime());
            return;
        }

        long target = canonical(client.level, pos, watchingPos);
        store(tracker, watchingDimension, target, typeId, stacks, client.level.getGameTime());

        // The other half of a double chest is shown by the same menu, so its
        // contents are now recorded against the half we chose. Leaving its own
        // record intact would count everything in the chest twice.
        long other = otherHalf(client.level, pos);
        if (other != Long.MIN_VALUE && other != target) {
            ContainerRecord existing = tracker.index(watchingDimension).get(other);
            if (existing != null && existing.contentsKnown()) {
                tracker.record(watchingDimension, ContainerRecord.locationOnly(
                        other, existing.dimensionId(), existing.typeId(), existing.origin(),
                        client.level.getGameTime()));
            }
        }
        ClientIndex.touch();
    }

    private static void store(TrackerService tracker, String dimensionId, long pos,
                              String typeId, List<ItemStack> stacks, long tick) {
        List<StackEntry> contents = LiveContainerReader.read(stacks, tracker.palette());
        tracker.record(dimensionId, new ContainerRecord(
                pos,
                tracker.palette().intern(dimensionId),
                tracker.palette().intern(typeId),
                Origin.UNKNOWN,
                null,
                false,
                true,
                null,
                tick,
                contents));
        ClientIndex.touch();
    }

    /**
     * Which half of a double chest a pair is filed under.
     *
     * <p>Always the same one whichever half was clicked, so opening a chest
     * from the left and then from the right updates one record rather than
     * building two that each claim the whole contents.
     */
    private static long canonical(Level level, BlockPos pos, long packed) {
        long other = otherHalf(level, pos);
        return other == Long.MIN_VALUE ? packed : Math.min(packed, other);
    }

    /**
     * The packed position of the other half of a double chest, or
     * {@link Long#MIN_VALUE} for anything that is not one.
     */
    private static long otherHalf(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return Long.MIN_VALUE;
        if (ChestBlock.getBlockType(state) == DoubleBlockCombiner.BlockType.SINGLE) return Long.MIN_VALUE;

        Direction direction = ChestBlock.getConnectedDirection(state);
        BlockPos neighbour = pos.relative(direction);
        if (!BlockKey.isRepresentable(neighbour.getX(), neighbour.getY(), neighbour.getZ())) {
            return Long.MIN_VALUE;
        }
        return BlockKey.pack(neighbour.getX(), neighbour.getY(), neighbour.getZ());
    }

    // --- Plumbing -----------------------------------------------------------

    /**
     * The index to write into, or null when nothing should be recorded.
     *
     * <p>Silent on a server that has the mod: its index is built from the world
     * itself rather than from what this player happened to walk past, so
     * keeping a second, worse one alongside it would only cost disk.
     */
    private static TrackerService active() {
        if (!enabled()) return null;
        if (ServerLink.state() == ServerLink.State.PRESENT) return null;
        // Cheap when it is already bound, which is every call but the first.
        ClientIndex.bindIfNeeded();
        return ClientIndex.tracker();
    }

    private static boolean enabled() {
        Minecraft client = Minecraft.getInstance();
        if (!dev.adrian.chesttracker.client.Session.active()) return false;
        return ChestTrackerConfig.get().clientSideIndex && !client.hasSingleplayerServer();
    }

    private static void forget() {
        watching = null;
        watchingPos = 0L;
        watchingDimension = null;
    }

    private static String dimensionOf(Level level) {
        return level.dimension().identifier().toString();
    }

    private static long packed(BlockPos pos) {
        if (!BlockKey.isRepresentable(pos.getX(), pos.getY(), pos.getZ())) return 0L;
        return BlockKey.pack(pos.getX(), pos.getY(), pos.getZ());
    }
}
