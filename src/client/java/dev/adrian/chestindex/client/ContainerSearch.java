package dev.adrian.chestindex.client;

import dev.adrian.chestindex.client.highlight.ContainerHighlight;
import dev.adrian.chestindex.client.platform.ClientCompat;
import dev.adrian.chestindex.config.ChestIndexConfig;
import dev.adrian.chestindex.core.net.QueryDto;
import dev.adrian.chestindex.core.util.BlockKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import dev.adrian.chestindex.platform.ItemContentsCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ContainerSearch {

    private ContainerSearch() {}

    private static final int MAX_TARGETS = 64;

    public static boolean findAndGuide(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return false;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;

        List<String> wanted = contentsOf(stack);
        boolean byContents = !wanted.isEmpty();
        if (!byContents) wanted = List.of(id.toString());

        String label = byContents
                ? describeContents(stack, wanted.size())
                : stack.getHoverName().getString();

        if (!ClientTracker.isAvailable()) {
            say(unavailableMessage(), ChatFormatting.RED);
            return false;
        }

        String dimensionId = player.level().dimension().identifier().toString();
        say("Looking for " + label + "...", ChatFormatting.GRAY);

        List<String> searchFor = wanted;
        ClientTracker.containers(searchFor, filters(), MAX_TARGETS, "").thenAccept(response ->
                client.execute(() -> {
                    List<QueryDto.ContainerHit> hits = response.hits();
                    if (hits.isEmpty()) {
                        lookElsewhere(searchFor, label, dimensionId);
                        return;
                    }
                    LocalPlayer now = Minecraft.getInstance().player;
                    if (now == null
                            || !now.level().dimension().identifier().toString().equals(dimensionId)) {
                        return;
                    }
                    ContainerHighlight.get().selectHits(hits, dimensionId, label);
                    ContainerHighlight.get().searchingFor(searchFor);
                    openIfInReach(hits, now);
                }));
        return true;
    }

    private static void lookElsewhere(List<String> wanted, String label, String dimensionId) {
        Minecraft client = Minecraft.getInstance();

        ClientTracker.summarise("", filters(), 0, QueryDto.ENDER_CHEST)
                .whenComplete((ender, error) -> client.execute(() -> {
                    if (error != null) {
                        failed(wanted, label, "the ender chest", error);
                        return;
                    }
                    if (holdsAny(ender, wanted)) {
                        markInEnderChest(wanted, label, dimensionId);
                        return;
                    }
                    lookInOtherDimensions(client, wanted, label, dimensionId);
                }));
    }

    private static boolean holdsAny(QueryDto.SummaryResponse summary, List<String> wanted) {
        for (QueryDto.ItemSummary item : summary.items()) {
            if (wanted.contains(item.itemId())) return true;
        }
        return false;
    }

    private static void markInEnderChest(List<String> wanted, String label, String dimensionId) {
        List<Long> chests = EnderChests.nearby();
        ContainerHighlight.get().markCarried(wanted.get(0), label, chests, dimensionId);
        ContainerHighlight.get().searchingFor(wanted);
        say(chests.isEmpty()
                        ? label + " is in your ender chest - open it and it will be marked"
                        : label + " is in your ender chest - the nearest one is marked",
                ChatFormatting.AQUA);
    }

    private static void lookInOtherDimensions(Minecraft client, List<String> wanted,
                                              String label, String dimensionId) {
        ClientTracker.status().whenComplete((status, statusError) -> client.execute(() -> {
            if (statusError != null) {
                failed(wanted, label, "the other dimensions", statusError);
                return;
            }

            List<String> elsewhere = new java.util.concurrent.CopyOnWriteArrayList<>();
            List<java.util.concurrent.CompletableFuture<?>> asked = new java.util.ArrayList<>();

            for (QueryDto.DimensionSummary dimension : status.dimensions()) {
                String id = dimension.dimensionId();
                if (id.equals(dimensionId) || QueryDto.ENDER_CHEST.equals(id)) continue;
                asked.add(ClientTracker.containers(wanted, filters(), 1, id)
                        .handle((response, error) -> {
                            if (error != null) {
                                dev.adrian.chestindex.ChestIndex.LOG.warn(
                                        "Could not search {} for {}: {}", id, wanted, error.toString());
                            } else if (!response.hits().isEmpty()) {
                                elsewhere.add(shortName(id));
                            }
                            return null;
                        }));
            }

            java.util.concurrent.CompletableFuture
                    .allOf(asked.toArray(new java.util.concurrent.CompletableFuture[0]))
                    .whenComplete((ignored, error) -> client.execute(() -> {
                        if (!elsewhere.isEmpty()) {
                            List<String> named = new java.util.ArrayList<>(elsewhere);
                            java.util.Collections.sort(named);
                            say(label + " is in " + String.join(" and ", named) + ", not here",
                                    ChatFormatting.AQUA);
                            return;
                        }
                        reportMiss(wanted, label, dimensionId, status);
                    }));
        }));
    }

    private static void reportMiss(List<String> wanted, String label,
                                   String dimensionId, QueryDto.StatusResponse status) {
        say("Nothing indexed holds " + label, ChatFormatting.YELLOW);

        StringBuilder held = new StringBuilder();
        for (QueryDto.DimensionSummary dimension : status.dimensions()) {
            if (held.length() > 0) held.append(", ");
            held.append(dimension.dimensionId()).append('=').append(dimension.containers());
        }
        dev.adrian.chestindex.ChestIndex.LOG.warn(
                "Search found nothing: items={} dimension={} route={} index=[{}]",
                wanted, dimensionId, ClientTracker.availability(),
                held.length() == 0 ? "empty" : held);
    }

    private static void failed(List<String> wanted, String label, String stage, Throwable error) {
        say("Could not finish the search for " + label + " - see the log", ChatFormatting.RED);
        dev.adrian.chestindex.ChestIndex.LOG.warn(
                "Search failed while checking {}: items={} route={}",
                stage, wanted, ClientTracker.availability(), error);
    }

    private static String shortName(String dimensionId) {
        int colon = dimensionId.indexOf(':');
        String name = colon < 0 ? dimensionId : dimensionId.substring(colon + 1);
        return name.startsWith("the_") ? name.substring(4) : name;
    }

    private static List<String> contentsOf(ItemStack stack) {
        if (!ChestIndexConfig.get().searchShulkerContents) return List.of();

        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        collect(stack, ids, MAX_NESTING);
        return List.copyOf(ids);
    }

    private static final int MAX_NESTING = 2;

    private static void collect(ItemStack stack, java.util.Set<String> into, int depth) {
        if (depth <= 0) return;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            ItemContentsCompat.stacks(contents).forEach(inner -> add(inner, into, depth));
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            bundle.itemCopyStream().forEach(inner -> add(inner, into, depth));
        }
    }

    private static void add(ItemStack inner, java.util.Set<String> into, int depth) {
        if (inner.isEmpty()) return;
        Identifier id = BuiltInRegistries.ITEM.getKey(inner.getItem());
        if (id != null) into.add(id.toString());
        collect(inner, into, depth - 1);
    }

    private static String describeContents(ItemStack stack, int distinct) {
        String first = firstItemName(stack);
        if (first == null) return stack.getHoverName().getString() + " contents";
        return distinct <= 1 ? first : first + " and " + (distinct - 1) + " more";
    }

    private static String firstItemName(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            java.util.Optional<ItemStack> first =
                    ItemContentsCompat.stacks(contents).filter(inner -> !inner.isEmpty()).findFirst();
            if (first.isPresent()) return first.get().getHoverName().getString();
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            java.util.Optional<ItemStack> first =
                    bundle.itemCopyStream().filter(inner -> !inner.isEmpty()).findFirst();
            if (first.isPresent()) return first.get().getHoverName().getString();
        }
        return null;
    }

    public static void sayNothingHovered() {
        say("Point at an item to search for it", ChatFormatting.GRAY);
    }

    public static QueryDto.Filters searchFilters() {
        return filters();
    }

    private static QueryDto.Filters filters() {
        ChestIndexConfig config = ChestIndexConfig.get();
        return new QueryDto.Filters(config.includeNested, config.showMachines,
                config.showUtility, config.showEntities, config.originFilter);
    }

    private static boolean openIfInReach(List<QueryDto.ContainerHit> hits, LocalPlayer player) {
        if (!Assist.allowed()) return false;
        if (!ChestIndexConfig.get().opensInReach()) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || ClientCompat.currentScreen() != null) return false;

        double reach = player.blockInteractionRange();
        QueryDto.ContainerHit best = null;
        BlockPos bestPos = null;

        for (QueryDto.ContainerHit hit : hits) {
            BlockPos pos = new BlockPos(BlockKey.x(hit.pos()), BlockKey.y(hit.pos()), BlockKey.z(hit.pos()));
            if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) continue;
            if (!(client.level.getBlockEntity(pos) instanceof Container)) continue;
            if (best == null || hit.matchedCount() > best.matchedCount()) {
                best = hit;
                bestPos = pos;
            }
        }

        if (best == null) return false;

        BlockHitResult where = new BlockHitResult(Vec3.atCenterOf(bestPos), Direction.UP, bestPos, false);
        client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, where);
        return true;
    }

    private static String unavailableMessage() {
        return switch (ClientTracker.availability()) {
            case CONNECTING -> "Still asking the server...";
            case NOT_PERMITTED -> "This server does not allow searching.";
            default -> "No index here yet.";
        };
    }

    private static void say(String message, ChatFormatting colour) {
        ActionBar.say(Component.literal(message).withStyle(colour));
    }
}
