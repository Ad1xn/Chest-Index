package dev.adrian.chesttracker.net;

import dev.adrian.chesttracker.ChestTracker;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.net.QueryDto;
import dev.adrian.chesttracker.platform.NetworkCompat;
import dev.adrian.chesttracker.server.QueryService;
import dev.adrian.chesttracker.server.TrackerService;
import dev.adrian.chesttracker.server.Trackers;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChestTrackerNetwork {

    private static final int SIGNAL_INTERVAL_TICKS = 10;

    private static final Map<UUID, Watcher> WATCHERS = new ConcurrentHashMap<>();

    private record Watcher(String dimensionId, long generation) {}

    private static int sinceLastSignal;

    private ChestTrackerNetwork() {}

    public static void registerTypes() {
        NetworkCompat.playC2S().register(
                ChestTrackerPayloads.SummaryRequestPayload.TYPE,
                ChestTrackerPayloads.SummaryRequestPayload.CODEC);
        NetworkCompat.playC2S().register(
                ChestTrackerPayloads.ContainerRequestPayload.TYPE,
                ChestTrackerPayloads.ContainerRequestPayload.CODEC);
        NetworkCompat.playC2S().register(
                ChestTrackerPayloads.SubscribePayload.TYPE,
                ChestTrackerPayloads.SubscribePayload.CODEC);

        NetworkCompat.playS2C().register(
                ChestTrackerPayloads.SummaryResponsePayload.TYPE,
                ChestTrackerPayloads.SummaryResponsePayload.CODEC);
        NetworkCompat.playS2C().register(
                ChestTrackerPayloads.ContainerResponsePayload.TYPE,
                ChestTrackerPayloads.ContainerResponsePayload.CODEC);
        NetworkCompat.playS2C().register(
                ChestTrackerPayloads.IndexChangedPayload.TYPE,
                ChestTrackerPayloads.IndexChangedPayload.CODEC);
        NetworkCompat.playS2C().register(
                ChestTrackerPayloads.HelloPayload.TYPE,
                ChestTrackerPayloads.HelloPayload.CODEC);
    }

    public static void registerServerHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(
                ChestTrackerPayloads.SummaryRequestPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    TrackerService tracker = Trackers.current();
                    if (tracker == null) return;
                    QueryDto.SummaryResponse response =
                            QueryService.summarise(tracker, player, payload.request(), access());
                    send(player, new ChestTrackerPayloads.SummaryResponsePayload(response));
                });

        ServerPlayNetworking.registerGlobalReceiver(
                ChestTrackerPayloads.ContainerRequestPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    TrackerService tracker = Trackers.current();
                    if (tracker == null) return;
                    QueryDto.ContainerResponse response =
                            QueryService.containers(tracker, player, payload.request(), access());
                    send(player, new ChestTrackerPayloads.ContainerResponsePayload(response));
                });

        ServerPlayNetworking.registerGlobalReceiver(
                ChestTrackerPayloads.SubscribePayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    if (!payload.watching()) {
                        WATCHERS.remove(player.getUUID());
                        return;
                    }
                    TrackerService tracker = Trackers.current();
                    if (tracker == null) return;
                    String dimensionId = Trackers.dimensionId(player.level());
                    WATCHERS.put(player.getUUID(),
                            new Watcher(dimensionId, tracker.generation(dimensionId)));
                });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (Trackers.current() == null) return;
            send(player, new ChestTrackerPayloads.HelloPayload(new QueryDto.Hello(
                    QueryDto.Hello.PROTOCOL_VERSION,
                    QueryService.mayQuery(player, access()))));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
    }

    public static void flushChanges(MinecraftServer server) {
        if (WATCHERS.isEmpty()) return;
        if (++sinceLastSignal < SIGNAL_INTERVAL_TICKS) return;
        sinceLastSignal = 0;

        TrackerService tracker = Trackers.current();
        if (tracker == null) return;

        for (Map.Entry<UUID, Watcher> entry : WATCHERS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                WATCHERS.remove(entry.getKey());
                continue;
            }

            String dimensionId = Trackers.dimensionId(player.level());
            long generation = tracker.generation(dimensionId);
            Watcher watcher = entry.getValue();
            if (watcher.generation() == generation && watcher.dimensionId().equals(dimensionId)) continue;

            WATCHERS.put(entry.getKey(), new Watcher(dimensionId, generation));
            send(player, ChestTrackerPayloads.IndexChangedPayload.INSTANCE);
        }
    }

    public static void forget(ServerPlayer player) {
        WATCHERS.remove(player.getUUID());
    }

    public static void forgetAll() {
        WATCHERS.clear();
        sinceLastSignal = 0;
    }

    private static ChestTrackerConfig.Access access() {
        return ChestTrackerConfig.get().permissionTier();
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (!ServerPlayNetworking.canSend(player, payload.type())) {
            ChestTracker.LOG.debug("{} cannot receive {}", player.getName().getString(), payload.type().id());
            return;
        }
        ServerPlayNetworking.send(player, payload);
    }
}
