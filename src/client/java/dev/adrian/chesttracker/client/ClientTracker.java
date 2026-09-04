package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.core.index.IndexQuery;
import dev.adrian.chesttracker.core.index.SearchResult;
import dev.adrian.chesttracker.core.index.WorldIndex;
import dev.adrian.chesttracker.core.util.BlockKey;
import dev.adrian.chesttracker.server.TrackerService;
import dev.adrian.chesttracker.server.Trackers;
import dev.adrian.chesttracker.server.scan.LiveScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ClientTracker {

    private ClientTracker() {}

    public static boolean isAvailable() {
        Minecraft client = Minecraft.getInstance();
        return client.hasSingleplayerServer() && client.getSingleplayerServer() != null;
    }

    public static CompletableFuture<List<SearchResult>> search(String query, int limit) {
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();
        LocalPlayer player = client.player;
        if (server == null || player == null) return CompletableFuture.completedFuture(List.of());

        String dimensionId = player.level().dimension().identifier().toString();
        long centre = BlockKey.pack(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        return server.submit(() -> {
            TrackerService tracker = Trackers.current();
            if (tracker == null) return List.<SearchResult>of();

            IndexQuery.Builder builder = IndexQuery.builder().center(centre).limit(limit);
            if (!needle.isEmpty()) {
                Set<Integer> itemIds = matchingItemIds(tracker, needle);
                if (itemIds.isEmpty()) return List.<SearchResult>of();
                builder.items(itemIds);
            }
            IndexQuery indexQuery = builder.build();

            ServerLevel level = Trackers.levelFor(dimensionId);
            if (level != null) {
                LiveScanner refresher = new LiveScanner(tracker);
                for (SearchResult candidate : tracker.search(dimensionId, indexQuery)) {
                    refresher.refreshIfLoaded(level, dimensionId, candidate.container().pos());
                }
            }
            return tracker.search(dimensionId, indexQuery);
        });
    }

    private static Set<Integer> matchingItemIds(TrackerService tracker, String needle) {
        Set<Integer> matches = new HashSet<>();
        List<String> entries = tracker.palette().entries();
        for (int id = 0; id < entries.size(); id++) {
            if (entries.get(id).toLowerCase(Locale.ROOT).contains(needle)) matches.add(id);
        }
        return matches;
    }

    public static CompletableFuture<List<WorldIndex.ItemSummary>> summarise(String query, int limit) {
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();
        LocalPlayer player = client.player;
        if (server == null || player == null) return CompletableFuture.completedFuture(List.of());

        String dimensionId = player.level().dimension().identifier().toString();
        long centre = BlockKey.pack(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        return server.submit(() -> {
            TrackerService tracker = Trackers.current();
            if (tracker == null) return List.<WorldIndex.ItemSummary>of();

            IndexQuery.Builder builder = IndexQuery.builder().center(centre);
            if (!needle.isEmpty()) {
                Set<Integer> itemIds = matchingItemIds(tracker, needle);
                if (itemIds.isEmpty()) return List.<WorldIndex.ItemSummary>of();
                builder.items(itemIds);
            }

            List<WorldIndex.ItemSummary> all = tracker.index(dimensionId).summarise(builder.build());
            return all.size() > limit ? List.copyOf(all.subList(0, limit)) : all;
        });
    }

    public static CompletableFuture<List<SearchResult>> containersFor(int itemId, int limit) {
        return search(itemId, limit);
    }

    private static CompletableFuture<List<SearchResult>> search(int itemId, int limit) {
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();
        LocalPlayer player = client.player;
        if (server == null || player == null) return CompletableFuture.completedFuture(List.of());

        String dimensionId = player.level().dimension().identifier().toString();
        long centre = BlockKey.pack(player.getBlockX(), player.getBlockY(), player.getBlockZ());

        return server.submit(() -> {
            TrackerService tracker = Trackers.current();
            if (tracker == null) return List.<SearchResult>of();

            IndexQuery query = IndexQuery.builder().item(itemId).center(centre).limit(limit).build();

            ServerLevel level = Trackers.levelFor(dimensionId);
            if (level != null) {
                LiveScanner refresher = new LiveScanner(tracker);
                for (SearchResult candidate : tracker.search(dimensionId, query)) {
                    refresher.refreshIfLoaded(level, dimensionId, candidate.container().pos());
                }
            }
            return tracker.search(dimensionId, query);
        });
    }

    public static String nameOf(int paletteId) {
        TrackerService tracker = Trackers.current();
        if (tracker == null) return "?";
        String value = tracker.palette().value(paletteId);
        return value == null ? "?" : value;
    }
}
