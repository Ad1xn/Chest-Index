package dev.adrian.chestindex.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.adrian.chestindex.config.ChestIndexConfig;
import dev.adrian.chestindex.core.index.IndexQuery;
import dev.adrian.chestindex.core.index.SearchResult;
import dev.adrian.chestindex.core.index.WorldIndex;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.net.ChestIndexNetwork;
import dev.adrian.chestindex.server.scan.LiveScanner;
import dev.adrian.chestindex.server.scan.RegionScanner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChestIndexCommands {
    private static final int MAX_RESULTS = 20;

    private ChestIndexCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chestindex")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("scan")
                        .executes(ctx -> scan(ctx.getSource(), LiveScanner.EVERYWHERE))
                        .then(Commands.argument("chunkRadius", IntegerArgumentType.integer(0))
                                .executes(ctx -> scan(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "chunkRadius")))))
                .then(Commands.literal("scanworld")
                        .executes(ctx -> scanWorld(ctx.getSource(), false))
                        .then(Commands.literal("override")
                                .executes(ctx -> scanWorld(ctx.getSource(), true)))
                        .then(Commands.literal("cancel")
                                .executes(ctx -> cancelScan(ctx.getSource()))))
                .then(Commands.literal("access")
                        .executes(ctx -> showAccess(ctx.getSource()))
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (ChestIndexConfig.Access tier : ChestIndexConfig.Access.values()) {
                                        builder.suggest(tier.name());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> setAccess(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "tier")))))
                .then(Commands.literal("stats")
                        .executes(ctx -> stats(ctx.getSource())))
                .then(Commands.literal("find")
                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                .executes(ctx -> find(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "item"))))));
    }

    private static int showAccess(CommandSourceStack source) {
        ChestIndexConfig.Access tier = ChestIndexConfig.get().permissionTier();
        source.sendSuccess(() -> Component.literal(
                "Players served: " + describe(tier) + " (" + tier.name() + ")"), false);
        return 1;
    }

    private static int setAccess(CommandSourceStack source, String requested) {
        ChestIndexConfig config = ChestIndexConfig.get();
        ChestIndexConfig.Access tier = ChestIndexConfig.Access.parse(requested);

        if (!tier.name().equalsIgnoreCase(requested.trim())) {
            source.sendFailure(Component.literal(
                    "Unknown tier \"" + requested + "\". Use ALL, OWNED or OP."));
            return 0;
        }

        config.permissionTier = tier.name();
        config.save();

        ChestIndexNetwork.announceAccess(source.getServer());

        source.sendSuccess(() -> Component.literal(
                "Players served: " + describe(tier) + " (" + tier.name() + ")"), true);
        return 1;
    }

    private static String describe(ChestIndexConfig.Access tier) {
        return switch (tier) {
            case ALL -> "everyone";
            case OWNED -> "their own containers";
            case OP -> "operators only";
        };
    }

    private static int scan(CommandSourceStack source, int chunkRadius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        TrackerService tracker = requireTracker(source);
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.level();

        LiveScanner.ScanResult result =
                new LiveScanner(tracker).scanAround(level, player.blockPosition(), chunkRadius);

        String where = chunkRadius == LiveScanner.EVERYWHERE
                ? "loaded chunks"
                : "loaded chunks within " + chunkRadius;
        source.sendSuccess(() -> Component.literal(String.format(
                "Scanned %d %s: %d containers indexed, %d stale entries removed.",
                result.chunksScanned(), where,
                result.containersFound(), result.staleRemoved())), false);

        source.sendSuccess(() -> Component.literal(
                "Only chunks in memory are read here; scanworld covers the rest."), false);
        return result.containersFound();
    }

    private static int scanWorld(CommandSourceStack source, boolean override)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        requireTracker(source);
        RegionScanner scanner = Trackers.regionScanner();
        var server = Trackers.server();
        if (scanner == null || server == null) {
            source.sendFailure(Component.literal("ChestIndex is not active on this world."));
            return 0;
        }
        if (scanner.isRunning()) {
            source.sendFailure(Component.literal(
                    "A scan is already running. Use /chestindex scanworld cancel to stop it."));
            return 0;
        }

        if (override) {
            scanner.forgetScanned(Trackers.current().scanLogFile());
            Trackers.current().clearIndexes();
        }

        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        long tick = source.getLevel().getGameTime();
        if (!scanner.start(worldRoot, tick)) {
            source.sendFailure(Component.literal("Could not start the scan."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(override
                ? "Index cleared. Reading every region file again from scratch - "
                        + "searches will be empty until this finishes."
                : "Scanning the world in the background - only the region files that "
                        + "changed since the last scan. Use /chestindex scanworld override "
                        + "to throw the index away and read everything again."), false);
        return 1;
    }

    private static int cancelScan(CommandSourceStack source) {
        RegionScanner scanner = Trackers.regionScanner();
        if (scanner == null || !scanner.isRunning()) {
            source.sendFailure(Component.literal("No scan is running."));
            return 0;
        }
        scanner.cancel();
        source.sendSuccess(() -> Component.literal("Scan cancelled."), false);
        return 1;
    }

    private static int stats(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        TrackerService tracker = requireTracker(source);

        source.sendSuccess(() -> Component.literal("ChestIndex")
                .withStyle(ChatFormatting.WHITE)
                .append(bullet())
                .append(count(tracker.totalContainers()))
                .append(unit(tracker.totalContainers(), "container"))
                .append(bullet())
                .append(count(tracker.dimensions().size()))
                .append(unit(tracker.dimensions().size(), "dimension")), false);

        RegionScanner scanner = Trackers.regionScanner();
        if (scanner != null) {
            RegionScanner.Progress progress = scanner.progress();
            if (progress.running() || progress.regionsRead() > 0) {
                source.sendSuccess(() -> scanLine(progress, scanner.queuedBatches()), false);
            }
        }

        for (String dimensionId : tracker.dimensions()) {
            WorldIndex.Stats stats = tracker.index(dimensionId).stats();

            source.sendSuccess(() -> Component.literal("")
                    .append(label("  "))
                    .append(Component.literal(shortDimension(dimensionId))
                            .withStyle(ChatFormatting.WHITE)), false);

            source.sendSuccess(() -> Component.literal("    ")
                    .append(count(stats.containers())).append(unit(stats.containers(), "container"))
                    .append(bullet())
                    .append(count(stats.withKnownContents())).append(label(" read"))
                    .append(bullet())
                    .append(count(stats.distinctItems())).append(unit(stats.distinctItems(), "item"))
                    .append(bullet())
                    .append(count(stats.chunks())).append(unit(stats.chunks(), "chunk")), false);

            source.sendSuccess(() -> Component.literal("    ")
                    .append(count(origin(stats, Origin.PLAYER_PLACED))).append(label(" placed"))
                    .append(label(" ("))
                    .append(count(stats.placementGuesses())).append(label(" by elimination)"))
                    .append(bullet())
                    .append(count(origin(stats, Origin.NATURAL))).append(label(" generated"))
                    .append(bullet())
                    .append(count(stats.unlooted())).append(label(" unlooted"))
                    .append(stats.unlooted() > 0 && !ChestIndexConfig.get().indexesGeneratedChests()
                            ? label(", withheld")
                            : Component.empty()), false);
        }
        return tracker.totalContainers();
    }

    private static Component scanLine(RegionScanner.Progress progress, int queued) {
        MutableComponent line = Component.literal("  ")
                .append(Component.literal(progress.running() ? "scanning" : "scanned")
                        .withStyle(ChatFormatting.WHITE))
                .append(bullet())
                .append(count(progress.regionsRead()))
                .append(label(" of "))
                .append(count(progress.regionsTotal()))
                .append(unit(progress.regionsTotal(), "region"))
                .append(bullet())
                .append(count(progress.chunksRead()))
                .append(unit(progress.chunksRead(), "chunk"));

        if (progress.chunksFailed() > 0) {
            line.append(bullet()).append(count(progress.chunksFailed())).append(label(" unreadable"));
        }
        if (queued > 0) {
            line.append(bullet()).append(count(queued)).append(label(" queued"));
        }
        return line;
    }

    private static MutableComponent count(int value) {
        return Component.literal(String.format("%,d", value)).withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent label(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent unit(int value, String singular) {
        return label(" " + singular + (value == 1 ? "" : "s"));
    }

    private static MutableComponent bullet() {
        return Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static String shortDimension(String dimensionId) {
        return dimensionId.startsWith("minecraft:") ? dimensionId.substring(10) : dimensionId;
    }

    private static int origin(WorldIndex.Stats stats, Origin origin) {
        return stats.byOrigin().getOrDefault(origin, 0);
    }

    private static int find(CommandSourceStack source, String itemQuery) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        TrackerService tracker = requireTracker(source);
        ServerPlayer player = source.getPlayerOrException();
        String dimensionId = Trackers.dimensionId(player.level());

        Set<Integer> itemIds = matchingItemIds(tracker, itemQuery);
        if (itemIds.isEmpty()) {
            source.sendFailure(Component.literal("Nothing indexed matches \"" + itemQuery + "\"."));
            return 0;
        }

        var origin = player.blockPosition();
        IndexQuery query = IndexQuery.builder()
                .items(itemIds)
                .center(BlockKey.pack(origin.getX(), origin.getY(), origin.getZ()))
                .limit(MAX_RESULTS)
                .build();

        LiveScanner refresher = new LiveScanner(tracker);
        for (SearchResult candidate : tracker.search(dimensionId, query)) {
            refresher.refreshIfLoaded(player.level(), dimensionId, candidate.container().pos());
        }
        List<SearchResult> results = tracker.search(dimensionId, query);

        if (results.isEmpty()) {
            source.sendFailure(Component.literal("No indexed container holds that."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Found " + results.size() + " container(s):"), false);
        for (SearchResult result : results) {
            long pos = result.container().pos();
            String line = String.format("  %d x %s at %s (%.0fm)",
                    result.matchedCount(),
                    tracker.palette().value(result.matches().get(0).itemId()),
                    BlockKey.toString(pos),
                    result.distance());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return results.size();
    }

    private static Set<Integer> matchingItemIds(TrackerService tracker, String query) {
        String needle = query.toLowerCase(java.util.Locale.ROOT).trim();
        Set<Integer> matches = new HashSet<>();
        List<String> entries = tracker.palette().view();
        for (int id = 0; id < entries.size(); id++) {
            if (entries.get(id).toLowerCase(java.util.Locale.ROOT).contains(needle)) matches.add(id);
        }
        return matches;
    }

    private static TrackerService requireTracker(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        TrackerService tracker = Trackers.current();
        if (tracker == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.literal("ChestIndex is not active on this world.")).create();
        }
        return tracker;
    }
}
