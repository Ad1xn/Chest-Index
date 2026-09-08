package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.client.index.ClientIndex;
import dev.adrian.chesttracker.client.net.ServerLink;
import dev.adrian.chesttracker.config.ChestTrackerConfig;
import dev.adrian.chesttracker.core.net.QueryDto;
import dev.adrian.chesttracker.server.QueryService;
import dev.adrian.chesttracker.server.TrackerService;
import dev.adrian.chesttracker.server.Trackers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The client's route to the index, whichever end it lives on.
 *
 * <p>Both routes produce the same {@link QueryDto} shapes, and both run the
 * same {@link QueryService}. In singleplayer that service is called directly on
 * the integrated server's thread; on a remote server the request goes over the
 * wire and the far end calls it. The screen cannot tell the difference, which
 * is the point: a singleplayer-only shortcut would be the code path nobody
 * exercises while working on multiplayer, and the one that quietly breaks.
 *
 * <p>Queries are never run inline. The index is not thread-safe and the render
 * thread must never touch it, so even the local path is submitted to the server
 * thread and answered asynchronously.
 */
public final class ClientTracker {

    private ClientTracker() {}

    /** Why the screen can or cannot show anything. */
    public enum Availability {
        /** Our own world; full access, no networking. */
        LOCAL,
        /** A server with the mod, and permission to ask it. */
        SERVER,
        /**
         * A server without the mod, answered from what this client has seen
         * for itself. Locations from chunks the server sent, contents only for
         * containers the player has opened.
         */
        CLIENT_ONLY,
        /** A server with the mod that will not answer this player. */
        NOT_PERMITTED,
        /** Still deciding - the server has not announced itself yet. */
        CONNECTING,
        /** No index reachable from here. */
        NONE
    }

    public static Availability availability() {
        if (hasLocalIndex()) return Availability.LOCAL;
        return switch (ServerLink.state()) {
            case WAITING -> Availability.CONNECTING;
            case PRESENT -> ServerLink.canQuery() ? Availability.SERVER : Availability.NOT_PERMITTED;
            // No mod on the far end. Whatever this client has worked out for
            // itself is now the only index there is.
            case ABSENT -> hasClientIndex() ? Availability.CLIENT_ONLY : Availability.NONE;
        };
    }

    /** True when there is an index we can query without networking. */
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

    // --- Live updates -------------------------------------------------------

    /**
     * A value that changes whenever what the screen is showing may be out of
     * date.
     *
     * <p>One token for both routes, so the screen has no idea whether a network
     * was involved. On a server it counts pushes; in our own world it reads the
     * index's own change counter directly, which needs no networking and no
     * subscription at all.
     */
    public static long changeToken() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0L;
        String dimensionId = player.level().dimension().identifier().toString();

        if (!hasLocalIndex()) {
            // The client-side index changes as chunks arrive and containers are
            // opened, and its own counter says so - the same read as below,
            // just on the index that happens to be the live one here.
            if (availability() == Availability.CLIENT_ONLY) {
                return ClientIndex.tracker().generation(dimensionId);
            }
            return ServerLink.changeToken();
        }

        TrackerService tracker = Trackers.current();
        if (tracker == null) return 0L;
        // A plain volatile read; safe from the render thread, unlike the index.
        return tracker.generation(dimensionId);
    }

    /**
     * Says whether the screen is open, so a server only pushes to watchers.
     *
     * <p>Does nothing in our own world - there is nothing to subscribe to when
     * the index is right here.
     */
    public static void setWatching(boolean watching) {
        if (hasLocalIndex()) return;
        ServerLink.setWatching(watching);
    }

    // --- Queries ------------------------------------------------------------

    /** Totals every indexed item, for the item-first grid. */
    public static CompletableFuture<QueryDto.SummaryResponse> summarise(
            String text, QueryDto.Filters filters, int limit) {
        return summarise(text, filters, limit, "");
    }

    /** As above, but for a named dimension; blank means where the player is. */
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

    /** The containers holding one item, nearest first. */
    public static CompletableFuture<QueryDto.ContainerResponse> containers(
            String itemId, QueryDto.Filters filters, int limit) {
        return containers(itemId, filters, limit, "");
    }

    /** As above, but for a named dimension; blank means where the player is. */
    public static CompletableFuture<QueryDto.ContainerResponse> containers(
            String itemId, QueryDto.Filters filters, int limit, String dimensionId) {
        return containers(List.of(itemId), filters, limit, dimensionId);
    }

    /**
     * Where any of a set of items is, in one query.
     *
     * <p>One request rather than one per item, because the alternative is
     * dozens of round trips for a single keypress and a result limit applied
     * separately to each - which cannot pick the nearest containers overall,
     * only the nearest for each item in isolation.
     */
    public static CompletableFuture<QueryDto.ContainerResponse> containers(
            List<String> itemIds, QueryDto.Filters filters, int limit, String dimensionId) {
        return containers(itemIds, filters, limit, dimensionId, "");
    }

    /**
     * As above, and narrowed by the same typed search the grid was.
     *
     * <p>The text carries the parts of a search that are about the container or
     * the stack rather than about which item it is - ">barrel", "ench:mending".
     * Without it the list of places would answer a wider question than the
     * count the player clicked on.
     */
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

    /**
     * What the index holds, and whether it is still filling.
     *
     * <p>Same two routes as every other query, so the screen cannot tell
     * whether a network was involved.
     */
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

    /**
     * Our own world is never gated.
     *
     * <p>The configured tier governs players arriving over the network. This
     * path is only ever the host querying the world they are playing, and
     * making them op themselves to search their own chests would be absurd.
     */
    private static ChestTrackerConfig.Access localAccess() {
        return ChestTrackerConfig.Access.ALL;
    }

    /**
     * Runs a query against the client's own index.
     *
     * <p>Answered inline rather than handed to a thread. There is no server
     * here to submit to, and the index this reads is owned by the client thread
     * that is asking - so completing immediately is both correct and the only
     * option that does not invent a lock. The future is kept so the screen
     * cannot tell which of the three routes answered it.
     */
    private static <T> CompletableFuture<T> locally(java.util.function.Supplier<T> query, T empty) {
        try {
            return CompletableFuture.completedFuture(query.get());
        } catch (RuntimeException e) {
            // A broken query must not take the screen down with it; an empty
            // answer reads as "nothing here", which is survivable.
            dev.adrian.chesttracker.ChestTracker.LOG.warn(
                    "Client-side query failed: {}", e.toString());
            return CompletableFuture.completedFuture(empty);
        }
    }

    /**
     * The level container entities may be read from, or null.
     *
     * <p>Null on a server without the mod unless the player has said otherwise,
     * and that default is the careful one. A chest minecart on a server is
     * moved by other people and by rails this client is not simulating: what it
     * saw a moment ago is not where the cart is, and the mod would say
     * otherwise with complete confidence. In the player's own world the client
     * is the authority that moves them, so there is nothing to be wrong about.
     */
    private static Level entitySource() {
        ChestTrackerConfig config = ChestTrackerConfig.get();
        if (!config.trackEntityContainers) return null;
        if (!config.entityContainersOnVanillaServers) return null;
        return Minecraft.getInstance().level;
    }

    /** Where the asking player is, packed the way the index ranks distance from. */
    private static long centre() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0L;
        return dev.adrian.chesttracker.core.util.BlockKey.pack(
                player.getBlockX(), player.getBlockY(), player.getBlockZ());
    }

    /** The dimension the asking player is standing in. */
    private static String here() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? "" : player.level().dimension().identifier().toString();
    }

    /**
     * Whether the ender chest is worth offering as a view.
     *
     * <p>Unlike the server, this cannot read the player's ender chest live -
     * the contents are never sent to a client that is not looking at them. So
     * the button appears once the player has opened their ender chest at least
     * once and there was something in it.
     */
    private static boolean hasStoredEnderChest() {
        if (!ClientIndex.isBound()) return false;
        TrackerService tracker = ClientIndex.tracker();
        if (!tracker.dimensions().contains(QueryDto.ENDER_CHEST)) return false;
        return !tracker.index(QueryDto.ENDER_CHEST).isEmpty();
    }

    private interface LocalQuery<T> {
        T run(TrackerService tracker, ServerPlayer player);
    }

    /**
     * Runs a query on the integrated server's thread.
     *
     * <p>It resolves the <em>server's</em> player rather than using the local
     * one, so the query centre and dimension come from the same authoritative
     * place they would on a real server.
     */
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
