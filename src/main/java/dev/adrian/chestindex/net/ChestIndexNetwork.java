package dev.adrian.chestindex.net;

import dev.adrian.chestindex.ChestIndex;
import dev.adrian.chestindex.config.ChestIndexConfig;
import dev.adrian.chestindex.core.net.QueryDto;
import dev.adrian.chestindex.platform.NetworkCompat;
import dev.adrian.chestindex.server.QueryService;
import dev.adrian.chestindex.server.TrackerService;
import dev.adrian.chestindex.server.Trackers;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChestIndexNetwork {

    private static final int SIGNAL_INTERVAL_TICKS = 10;

    private static final Map<UUID, Watcher> WATCHERS = new ConcurrentHashMap<>();

    private record Watcher(String dimensionId, long generation) {}

    private static int sinceLastSignal;

    private ChestIndexNetwork() {}

    public static void registerTypes() {
        NetworkCompat.playC2S().register(
                ChestIndexPayloads.SummaryRequestPayload.TYPE,
                ChestIndexPayloads.SummaryRequestPayload.CODEC);
        NetworkCompat.playC2S().register(
                ChestIndexPayloads.ContainerRequestPayload.TYPE,
                ChestIndexPayloads.ContainerRequestPayload.CODEC);
        NetworkCompat.playC2S().register(
                ChestIndexPayloads.SubscribePayload.TYPE,
                ChestIndexPayloads.SubscribePayload.CODEC);
        NetworkCompat.playC2S().register(
                ChestIndexPayloads.StatusRequestPayload.TYPE,
                ChestIndexPayloads.StatusRequestPayload.CODEC);

        NetworkCompat.playS2C().register(
                ChestIndexPayloads.StatusResponsePayload.TYPE,
                ChestIndexPayloads.StatusResponsePayload.CODEC);
        NetworkCompat.playS2C().register(
                ChestIndexPayloads.SummaryResponsePayload.TYPE,
                ChestIndexPayloads.SummaryResponsePayload.CODEC);
        NetworkCompat.playS2C().register(
                ChestIndexPayloads.ContainerResponsePayload.TYPE,
                ChestIndexPayloads.ContainerResponsePayload.CODEC);
        NetworkCompat.playS2C().register(
                ChestIndexPayloads.IndexChangedPayload.TYPE,
                ChestIndexPayloads.IndexChangedPayload.CODEC);
        NetworkCompat.playS2C().register(
                ChestIndexPayloads.HelloPayload.TYPE,
                ChestIndexPayloads.HelloPayload.CODEC);
    }

    public static void registerServerHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(
                ChestIndexPayloads.SummaryRequestPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    TrackerService tracker = Trackers.current();
                    if (tracker == null) return;
                    QueryDto.SummaryResponse response =
                            QueryService.summarise(tracker, player, payload.request(), access());
                    send(player, new ChestIndexPayloads.SummaryResponsePayload(response));
                });

        ServerPlayNetworking.registerGlobalReceiver(
                ChestIndexPayloads.ContainerRequestPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    TrackerService tracker = Trackers.current();
                    if (tracker == null) return;
                    QueryDto.ContainerResponse response =
                            QueryService.containers(tracker, player, payload.request(), access());
                    send(player, new ChestIndexPayloads.ContainerResponsePayload(response));
                });

        ServerPlayNetworking.registerGlobalReceiver(
                ChestIndexPayloads.StatusRequestPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    TrackerService tracker = Trackers.current();
                    if (tracker == null) return;
                    QueryDto.StatusResponse response =
                            QueryService.status(tracker, player, payload.request(), access());
                    send(player, new ChestIndexPayloads.StatusResponsePayload(response));
                });

        ServerPlayNetworking.registerGlobalReceiver(
                ChestIndexPayloads.SubscribePayload.TYPE,
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
            send(player, new ChestIndexPayloads.HelloPayload(new QueryDto.Hello(
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
            send(player, ChestIndexPayloads.IndexChangedPayload.INSTANCE);
        }
    }

    public static void forget(ServerPlayer player) {
        WATCHERS.remove(player.getUUID());
    }

    public static void announceAccess(MinecraftServer server) {
        if (server == null || Trackers.current() == null) return;
        ChestIndexConfig.Access tier = access();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, new ChestIndexPayloads.HelloPayload(new QueryDto.Hello(
                    QueryDto.Hello.PROTOCOL_VERSION, QueryService.mayQuery(player, tier))));
        }
    }

    public static void forgetAll() {
        WATCHERS.clear();
        sinceLastSignal = 0;
    }

    private static ChestIndexConfig.Access access() {
        return ChestIndexConfig.get().permissionTier();
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (!ServerPlayNetworking.canSend(player, payload.type())) {
            ChestIndex.LOG.debug("{} cannot receive {}", player.getName().getString(), payload.type().id());
            return;
        }
        ServerPlayNetworking.send(player, payload);
    }
}
