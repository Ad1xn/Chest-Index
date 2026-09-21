package dev.adrian.chestindex.client.index;

import dev.adrian.chestindex.client.net.ServerLink;
import dev.adrian.chestindex.config.ChestIndexConfig;
import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.net.QueryDto;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.platform.ChunkPosCompat;
import dev.adrian.chestindex.platform.ContainerTypes;
import dev.adrian.chestindex.platform.LiveContainerReader;
import dev.adrian.chestindex.server.TrackerService;
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

public final class ClientObserver {
    private ClientObserver() {}

    private static final long ATTRIBUTION_WINDOW_MS = 2000;

    private static long lastUsePos;
    private static String lastUseDimension;
    private static long lastUseAt;

    private static Screen watching;
    private static long watchingPos;
    private static String watchingDimension;

    private static Screen considered;

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            forget();
            considered = null;
            dev.adrian.chestindex.client.Session.forget();
            dev.adrian.chestindex.client.ui.ChestIndexScreen.forgetPinnedDimension();
            dev.adrian.chestindex.client.ui.ChestIndexScreen.forgetItemNames();
            dev.adrian.chestindex.client.SearchCategories.forget();
            dev.adrian.chestindex.client.highlight.ContainerHighlight.get().clear();
            dev.adrian.chestindex.client.MaterialGoals.clear();
            if (enabled()) ClientIndex.bind();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            forget();
            considered = null;
            dev.adrian.chestindex.client.highlight.ContainerHighlight.get().clear();
            dev.adrian.chestindex.client.MaterialGoals.clear();
            ClientIndex.unbind();
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() && player == Minecraft.getInstance().player) {
                lastUsePos = packed(hit.getBlockPos());
                lastUseDimension = dimensionOf(level);
                lastUseAt = System.currentTimeMillis();
            }
            return InteractionResult.PASS;
        });

        ClientChunkEvents.CHUNK_LOAD.register(ClientObserver::onChunkLoad);

        dev.adrian.chestindex.platform.ClientBlockChanges.listen(ClientObserver::onBlockChanged);

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            if (screen == considered) return;
            considered = screen;
            beginWatching(screen);
        });
    }

    private static void onChunkLoad(ClientLevel level, LevelChunk chunk) {
        TrackerService tracker = active();
        if (tracker == null) return;

        String dimensionId = dimensionOf(level);
        long chunkKey = BlockKey.chunkKey(
                ChunkPosCompat.x(chunk.getPos()), ChunkPosCompat.z(chunk.getPos()));

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

            if (tracker.index(dimensionId).get(key) != null) continue;

            tracker.record(dimensionId, ContainerRecord.locationOnly(
                    key, dimensionKey, tracker.palette().intern(typeId), Origin.UNKNOWN, tick));
        }

        dropBrokenContainers(tracker, dimensionId, chunkKey, chunk);
        if (tracker.generation(dimensionId) != before) ClientIndex.touch();
    }

    private static void dropBrokenContainers(TrackerService tracker, String dimensionId,
                                             long chunkKey, LevelChunk chunk) {
        Set<Long> indexed = tracker.index(dimensionId).positionsInChunk(chunkKey);
        if (indexed.isEmpty()) return;

        for (long pos : new ArrayList<>(indexed)) {
            BlockPos blockPos = new BlockPos(BlockKey.x(pos), BlockKey.y(pos), BlockKey.z(pos));
            if (chunk.getBlockState(blockPos).isAir()) {
                tracker.remove(dimensionId, pos);
            }
        }
    }

    private static void onBlockChanged(Level level, BlockPos pos) {
        if (level != Minecraft.getInstance().level) return;
        if (!BlockKey.isRepresentable(pos.getX(), pos.getY(), pos.getZ())) return;

        long key = BlockKey.pack(pos.getX(), pos.getY(), pos.getZ());

        dev.adrian.chestindex.client.highlight.ContainerHighlight highlight =
                dev.adrian.chestindex.client.highlight.ContainerHighlight.get();
        boolean highlighted = highlight.holds(key);

        TrackerService tracker = active();
        String dimensionId = tracker == null ? null : dimensionOf(level);
        boolean indexed = tracker != null && tracker.index(dimensionId).get(key) != null;

        if (!highlighted && !indexed) return;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && ContainerTypes.isContainer(blockEntity)) return;

        if (indexed) {
            tracker.remove(dimensionId, key);
            ClientIndex.touch();
        }
        if (highlighted) highlight.forget(key);
    }

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

        ScreenEvents.remove(screen).register(closed -> {
            if (closed == watching) capture((AbstractContainerScreen<?>) closed);
            forget();
            if (closed == considered) considered = null;
        });
    }

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
        if (stacks.isEmpty()) return;

        if ("minecraft:ender_chest".equals(typeId)) {
            store(tracker, QueryDto.ENDER_CHEST, 0L, typeId, stacks, client.level.getGameTime());
            return;
        }

        long target = canonical(client.level, pos, watchingPos);
        store(tracker, watchingDimension, target, typeId, stacks, client.level.getGameTime());

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

    private static long canonical(Level level, BlockPos pos, long packed) {
        long other = otherHalf(level, pos);
        return other == Long.MIN_VALUE ? packed : Math.min(packed, other);
    }

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

    private static TrackerService active() {
        if (!enabled()) return null;
        if (ServerLink.state() == ServerLink.State.PRESENT) return null;
        ClientIndex.bindIfNeeded();
        return ClientIndex.tracker();
    }

    private static boolean enabled() {
        Minecraft client = Minecraft.getInstance();
        if (!dev.adrian.chestindex.client.Session.active()) return false;
        return ChestIndexConfig.get().clientSideIndex && !client.hasSingleplayerServer();
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
