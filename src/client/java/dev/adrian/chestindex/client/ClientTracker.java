package dev.adrian.chestindex.client;

import dev.adrian.chestindex.client.index.ClientIndex;
import dev.adrian.chestindex.client.net.ServerLink;
import dev.adrian.chestindex.config.ChestIndexConfig;
import dev.adrian.chestindex.core.net.QueryDto;
import dev.adrian.chestindex.server.QueryService;
import dev.adrian.chestindex.server.TrackerService;
import dev.adrian.chestindex.server.Trackers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ClientTracker {
    private ClientTracker() {}

    public enum Availability {
        LOCAL,
        SERVER,
        CLIENT_ONLY,
        NOT_PERMITTED,
        CONNECTING,
        NONE
    }

    public static Availability availability() {
        if (hasLocalIndex()) return Availability.LOCAL;
        return switch (ServerLink.state()) {
            case WAITING -> Availability.CONNECTING;
            case PRESENT -> ServerLink.canQuery() ? Availability.SERVER : Availability.NOT_PERMITTED;
            case ABSENT -> hasClientIndex() ? Availability.CLIENT_ONLY : Availability.NONE;
        };
    }

    public static boolean isAvailable() {
        Availability availability = availability();
        return availability == Availability.LOCAL
                || availability == Availability.SERVER
                || availability == Availability.CLIENT_ONLY;
    }

    private static boolean hasClientIndex() {
        return ClientIndex.isBound() && ClientIndex.tracker().totalContainers() > 0;
    }

    private static boolean hasLocalIndex() {
        Minecraft client = Minecraft.getInstance();
        return client.hasSingleplayerServer() && client.getSingleplayerServer() != null;
    }

    public static long changeToken() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0L;
        String dimensionId = player.level().dimension().identifier().toString();

        if (!hasLocalIndex()) {
            if (availability() == Availability.CLIENT_ONLY) {
                return ClientIndex.tracker().generation(dimensionId);
            }
            return ServerLink.changeToken();
        }

        TrackerService tracker = Trackers.current();
        if (tracker == null) return 0L;
        return tracker.generation(dimensionId);
    }

    public static void setWatching(boolean watching) {
        if (hasLocalIndex()) return;
        ServerLink.setWatching(watching);
    }

    public static CompletableFuture<QueryDto.SummaryResponse> summarise(
            String text, QueryDto.Filters filters, int limit) {
        return summarise(text, filters, limit, "");
    }

    public static CompletableFuture<QueryDto.SummaryResponse> summarise(
            String text, QueryDto.Filters filters, int limit, String dimensionId) {
        int requestId = ServerLink.nextRequestId();
        QueryDto.SummaryRequest request =
                new QueryDto.SummaryRequest(requestId, text, filters, limit, dimensionId);

        if (!hasLocalIndex()) {
            if (availability() == Availability.CLIENT_ONLY) {
                return locally(() -> QueryService.summarise(
                        ClientIndex.tracker(), request, centre(), here(), null, null,
                        entitySource()),
                        QueryDto.SummaryResponse.of(requestId, List.of()));
            }
            return ServerLink.summarise(request);
        }

        return onServerThread(
                (tracker, player) -> QueryService.summarise(tracker, player, request, localAccess()),
                QueryDto.SummaryResponse.of(requestId, List.of()));
    }

    public static CompletableFuture<QueryDto.ContainerResponse> containers(
            String itemId, QueryDto.Filters filters, int limit) {
        return containers(itemId, filters, limit, "");
    }

    public static CompletableFuture<QueryDto.ContainerResponse> containers(
            String itemId, QueryDto.Filters filters, int limit, String dimensionId) {
        return containers(List.of(itemId), filters, limit, dimensionId);
    }

    public static CompletableFuture<QueryDto.ContainerResponse> containers(
            List<String> itemIds, QueryDto.Filters filters, int limit, String dimensionId) {
        return containers(itemIds, filters, limit, dimensionId, "");
    }

    public static CompletableFuture<QueryDto.ContainerResponse> containers(
            List<String> itemIds, QueryDto.Filters filters, int limit, String dimensionId, String text) {
        int requestId = ServerLink.nextRequestId();
        QueryDto.ContainerRequest request =
                new QueryDto.ContainerRequest(requestId, itemIds, filters, limit, dimensionId, text);

        if (!hasLocalIndex()) {
            if (availability() == Availability.CLIENT_ONLY) {
                return locally(() -> QueryService.containers(
                        ClientIndex.tracker(), request, centre(), here(), null,
                        QueryService.Refresher.NONE, entitySource()),
                        QueryDto.ContainerResponse.of(requestId, List.of()));
            }
            return ServerLink.containers(request);
        }

        return onServerThread(
                (tracker, player) -> QueryService.containers(tracker, player, request, localAccess()),
                QueryDto.ContainerResponse.of(requestId, List.of()));
    }

    public static CompletableFuture<QueryDto.StatusResponse> status() {
        int requestId = ServerLink.nextRequestId();
        QueryDto.StatusRequest request = new QueryDto.StatusRequest(requestId);

        if (!hasLocalIndex()) {
            if (availability() == Availability.CLIENT_ONLY) {
                return locally(() -> QueryService.status(
                        ClientIndex.tracker(), request, hasStoredEnderChest()),
                        QueryDto.StatusResponse.empty(requestId));
            }
            return ServerLink.status(request);
        }

        return onServerThread(
                (tracker, player) -> QueryService.status(tracker, player, request, localAccess()),
                QueryDto.StatusResponse.empty(requestId));
    }

    private static ChestIndexConfig.Access localAccess() {
        return ChestIndexConfig.Access.ALL;
    }

    private static <T> CompletableFuture<T> locally(java.util.function.Supplier<T> query, T empty) {
        try {
            return CompletableFuture.completedFuture(query.get());
        } catch (RuntimeException e) {
            dev.adrian.chestindex.ChestIndex.LOG.warn(
                    "Client-side query failed: {}", e.toString());
            return CompletableFuture.completedFuture(empty);
        }
    }

    private static Level entitySource() {
        ChestIndexConfig config = ChestIndexConfig.get();
        if (!config.trackEntityContainers) return null;
        if (!config.readsEntitiesOnVanillaServers()) return null;
        return Minecraft.getInstance().level;
    }

    private static long centre() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0L;
        return dev.adrian.chestindex.core.util.BlockKey.pack(
                player.getBlockX(), player.getBlockY(), player.getBlockZ());
    }

    private static String here() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? "" : player.level().dimension().identifier().toString();
    }

    private static boolean hasStoredEnderChest() {
        if (!ClientIndex.isBound()) return false;
        TrackerService tracker = ClientIndex.tracker();
        if (!tracker.dimensions().contains(QueryDto.ENDER_CHEST)) return false;
        return !tracker.index(QueryDto.ENDER_CHEST).isEmpty();
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
