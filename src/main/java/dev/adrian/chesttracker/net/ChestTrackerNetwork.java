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
import net.minecraft.server.level.ServerPlayer;

public final class ChestTrackerNetwork {

    private ChestTrackerNetwork() {}

    public static void registerTypes() {
        NetworkCompat.playC2S().register(
                ChestTrackerPayloads.SummaryRequestPayload.TYPE,
                ChestTrackerPayloads.SummaryRequestPayload.CODEC);
        NetworkCompat.playC2S().register(
                ChestTrackerPayloads.ContainerRequestPayload.TYPE,
                ChestTrackerPayloads.ContainerRequestPayload.CODEC);

        NetworkCompat.playS2C().register(
                ChestTrackerPayloads.SummaryResponsePayload.TYPE,
                ChestTrackerPayloads.SummaryResponsePayload.CODEC);
        NetworkCompat.playS2C().register(
                ChestTrackerPayloads.ContainerResponsePayload.TYPE,
                ChestTrackerPayloads.ContainerResponsePayload.CODEC);
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

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (Trackers.current() == null) return;
            send(player, new ChestTrackerPayloads.HelloPayload(new QueryDto.Hello(
                    QueryDto.Hello.PROTOCOL_VERSION,
                    QueryService.mayQuery(player, access()))));
        });
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
