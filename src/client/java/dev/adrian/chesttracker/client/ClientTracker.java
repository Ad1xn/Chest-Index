package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.client.net.ServerLink;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.net.QueryDto;
import dev.adrian.chesttracker.server.QueryService;
import dev.adrian.chesttracker.server.TrackerService;
import dev.adrian.chesttracker.server.Trackers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ClientTracker {

    private ClientTracker() {}

    public enum Availability {
        LOCAL,
        SERVER,
        NOT_PERMITTED,
        CONNECTING,
        NONE
    }

    public static Availability availability() {
        if (hasLocalIndex()) return Availability.LOCAL;
        return switch (ServerLink.state()) {
            case WAITING -> Availability.CONNECTING;
            case PRESENT -> ServerLink.canQuery() ? Availability.SERVER : Availability.NOT_PERMITTED;
            case ABSENT -> Availability.NONE;
        };
    }

    public static boolean isAvailable() {
        return availability() == Availability.LOCAL || availability() == Availability.SERVER;
    }

    private static boolean hasLocalIndex() {
        Minecraft client = Minecraft.getInstance();
        return client.hasSingleplayerServer() && client.getSingleplayerServer() != null;
    }

    public static long changeToken() {
        if (!hasLocalIndex()) return ServerLink.changeToken();

        TrackerService tracker = Trackers.current();
        LocalPlayer player = Minecraft.getInstance().player;
        if (tracker == null || player == null) return 0L;
        return tracker.generation(player.level().dimension().identifier().toString());
    }

    public static void setWatching(boolean watching) {
        if (hasLocalIndex()) return;
        ServerLink.setWatching(watching);
    }

    public static CompletableFuture<QueryDto.SummaryResponse> summarise(
            String text, QueryDto.Filters filters, int limit) {

        int requestId = ServerLink.nextRequestId();
        QueryDto.SummaryRequest request = new QueryDto.SummaryRequest(requestId, text, filters, limit);

        if (!hasLocalIndex()) return ServerLink.summarise(request);

        return onServerThread(
                (tracker, player) -> QueryService.summarise(tracker, player, request, localAccess()),
                QueryDto.SummaryResponse.of(requestId, List.of()));
    }

    public static CompletableFuture<QueryDto.ContainerResponse> containers(
            String itemId, QueryDto.Filters filters, int limit) {

        int requestId = ServerLink.nextRequestId();
        QueryDto.ContainerRequest request = new QueryDto.ContainerRequest(requestId, itemId, filters, limit);

        if (!hasLocalIndex()) return ServerLink.containers(request);

        return onServerThread(
                (tracker, player) -> QueryService.containers(tracker, player, request, localAccess()),
                QueryDto.ContainerResponse.of(requestId, List.of()));
    }

    private static ChestTrackerConfig.Access localAccess() {
        return ChestTrackerConfig.Access.ALL;
    }

    private interface LocalQuery<T> {
        T run(TrackerService tracker, ServerPlayer player);
    }

    private static <T> CompletableFuture<T> onServerThread(LocalQuery<T> query, T empty) {
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();
        LocalPlayer local = client.player;
        if (server == null || local == null) return CompletableFuture.completedFuture(empty);

        java.util.UUID uuid = local.getUUID();
        return server.submit(() -> {
            TrackerService tracker = Trackers.current();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (tracker == null || player == null) return empty;
            return query.run(tracker, player);
        });
    }
}
