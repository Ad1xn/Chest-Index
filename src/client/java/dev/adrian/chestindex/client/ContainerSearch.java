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

/**
 * Searching for an item without opening the search screen.
 *
 * <p>Hovering a stack and pressing the key is the shortest path there is from
 * "I want more of this" to being pointed at it: no window, no typing, no
 * reading a list. It answers straight into the guidance the screen already
 * hands off to, so the two routes end in the same place.
 */
public final class ContainerSearch {

    private ContainerSearch() {}

    /**
     * Containers asked for per search.
     *
     * <p>Matches the search screen's own cap. Guidance only ever points at one,
     * but the rest are what an in-world highlight will draw, so they are
     * fetched now rather than being a second query later.
     */
    private static final int MAX_TARGETS = 64;

    /**
     * Finds everywhere this item is and starts guiding to the nearest.
     *
     * <p>Filters come from the config rather than from the search screen's
     * toolbar: the screen's toggles belong to a window that is not open, and
     * inheriting whatever they were left on would make the key behave
     * differently depending on a screen the player cannot see.
     *
     * @return whether a search actually went out, so the caller knows whether
     *         to close the container the player was looking in
     */
    public static boolean findAndGuide(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return false;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;

        // A shulker box is usually not the question. Pointing at one that has
        // things in it and asking "where is this" almost always means "where is
        // the rest of what is in here" - the box itself is the packaging.
        List<String> wanted = contentsOf(stack);
        boolean byContents = !wanted.isEmpty();
        if (!byContents) wanted = List.of(id.toString());

        String label = byContents
                ? describeContents(stack, wanted.size())
                : stack.getHoverName().getString();

        if (!ClientTracker.isAvailable()) {
            // Not a hit, so the caller leaves the container open - the player is
            // still standing at it and has been told why nothing happened.
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
                        // Not the end of the search. The dimension the player
                        // is standing in is where they were asked about, not
                        // the only place the mod knows about.
                        lookElsewhere(searchFor, label, dimensionId);
                        return;
                    }
                    // The player may have walked through a portal while the
                    // server was answering; guiding them in the wrong world is
                    // worse than not answering.
                    LocalPlayer now = Minecraft.getInstance().player;
                    if (now == null
                            || !now.level().dimension().identifier().toString().equals(dimensionId)) {
                        return;
                    }
                    // The highlight goes up either way. Opening one container
                    // does not answer where the other nine are, and the boxes
                    // are still what says so.
                    ContainerHighlight.get().selectHits(hits, dimensionId, label);
                    ContainerHighlight.get().searchingFor(searchFor);
                    openIfInReach(hits, now);
                }));
        return true;
    }

    /**
     * Nothing here - so try the two places that are not here.
     *
     * <p>"Nothing indexed holds Sticky Piston" is a wrong answer often enough
     * to be worth this. It means nothing in <em>this dimension's</em> index,
     * and the two things it is quietly leaving out are the ender chest, which
     * is on the player and has no dimension at all, and every other dimension,
     * which is where a good deal of anybody's storage lives.
     *
     * <p>The ender chest is checked first because it is actionable from where
     * the player is standing. The other dimensions are only named - there is
     * nothing to guide to from the wrong side of a portal - but naming them
     * turns "you do not have any" into "you have some, in the Nether", which
     * is a different answer to a different question.
     */
    private static void lookElsewhere(List<String> wanted, String label, String dimensionId) {
        Minecraft client = Minecraft.getInstance();

        // whenComplete rather than thenAccept, all the way down this chain.
        // A CompletableFuture that completes exceptionally simply skips every
        // thenAccept after it, so a query that threw produced no boxes, no
        // message and no log line - the key looked like it had done nothing at
        // all. Every stage now ends in exactly one thing being said.
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

    /** Whether an ender chest summary mentions any of the items asked for. */
    private static boolean holdsAny(QueryDto.SummaryResponse summary, List<String> wanted) {
        for (QueryDto.ItemSummary item : summary.items()) {
            if (wanted.contains(item.itemId())) return true;
        }
        return false;
    }

    /**
     * It is in the ender chest, so mark the nearest one and say so.
     *
     * <p>The same three-step trail the search screen builds: the chest in the
     * world, the ender chest item wherever it turns up in an open container,
     * and the item itself once the chest is open.
     */
    private static void markInEnderChest(List<String> wanted, String label, String dimensionId) {
        List<Long> chests = EnderChests.nearby();
        ContainerHighlight.get().markCarried(wanted.get(0), label, chests, dimensionId);
        // Everything that was asked for, not only the first: pressing the key
        // over a full shulker asks about all of it.
        ContainerHighlight.get().searchingFor(wanted);
        say(chests.isEmpty()
                        ? label + " is in your ender chest - open it and it will be marked"
                        : label + " is in your ender chest - the nearest one is marked",
                ChatFormatting.AQUA);
    }

    /**
     * Names the other dimensions that hold any of it, or reports a real miss.
     *
     * <p>One small query per other dimension, asked only on this path - the
     * path where the answer would otherwise be wrong. There are two or three of
     * them in a vanilla world.
     */
    private static void lookInOtherDimensions(Minecraft client, List<String> wanted,
                                              String label, String dimensionId) {
        ClientTracker.status().whenComplete((status, statusError) -> client.execute(() -> {
            if (statusError != null) {
                failed(wanted, label, "the other dimensions", statusError);
                return;
            }

            // Written from whichever thread answers each query - the netty one
            // on a server with the mod - so it cannot be a plain list.
            List<String> elsewhere = new java.util.concurrent.CopyOnWriteArrayList<>();
            List<java.util.concurrent.CompletableFuture<?>> asked = new java.util.ArrayList<>();

            for (QueryDto.DimensionSummary dimension : status.dimensions()) {
                String id = dimension.dimensionId();
                if (id.equals(dimensionId) || QueryDto.ENDER_CHEST.equals(id)) continue;
                // handle(), so one dimension failing still lets the rest be
                // reported rather than taking the whole answer down with it.
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
                            // Sorted, because the queries answer in whatever
                            // order they finish and "nether and end" should not
                            // become "end and nether" between two presses.
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

    /**
     * A genuine miss: nowhere in any dimension, and not in the ender chest.
     *
     * <p>Written to the log as well as to the action bar. A miss is usually
     * honest - the index really does not hold any - but it is also what a bug
     * in the query looks like from the outside, and the two are impossible to
     * tell apart from a one-line message above the hotbar. The log line carries
     * what would be needed to tell: what was asked for, which route answered,
     * where the player was, and how much the index holds. If the mod is wrong,
     * that line is what says so.
     */
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

    /**
     * A query threw rather than answering.
     *
     * <p>Said out loud. Silence is the one response that cannot be acted on -
     * it is indistinguishable from the key not being bound - and this is a bug
     * in the mod rather than an answer about the world, so it should look like
     * one.
     */
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

    /**
     * The distinct items inside a container item, or empty if there is no
     * reason to search by contents.
     *
     * <p>Empty covers three cases that all mean "search for the box itself":
     * the setting is off, the stack is not a container, or it is an empty one.
     * An empty shulker box genuinely is the question when you point at one -
     * you are looking for more storage, not for nothing.
     *
     * <p>Reads the stack's own components rather than the index. The box in
     * this slot is the truth; what the index remembers about a box is about
     * some other box.
     */
    private static List<String> contentsOf(ItemStack stack) {
        if (!ChestIndexConfig.get().searchShulkerContents) return List.of();

        // Ordered, so the first item found is also the one named in the
        // message - "Diamond and 4 more" should not shuffle between presses.
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        collect(stack, ids, MAX_NESTING);
        return List.copyOf(ids);
    }

    /** A shulker inside a shulker is still worth looking through. */
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

    /** "Diamond and 4 more", so the action bar says what is being looked for. */
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

    /**
     * The key was pressed with the cursor over nothing.
     *
     * <p>Worth a message. The alternative - staying silent - cannot be told
     * apart from the key being unbound or the mod being the wrong build, and a
     * player who cannot tell those apart has no way to work out which.
     */
    public static void sayNothingHovered() {
        say("Point at an item to search for it", ChatFormatting.GRAY);
    }

    /**
     * The search screen's own filters, as the player last left them.
     *
     * <p>Read from the config rather than invented here: those three values are
     * what the menu writes when it closes, so the key searches what the menu
     * says it will. Hardcoding them meant the key quietly ignored every filter
     * the player had set - it always hid machines and always counted every
     * origin, whatever the menu was showing.
     */
    /**
     * Shared with the Litematica button and the material picker, which search
     * on the same terms.
     */
    public static QueryDto.Filters searchFilters() {
        return filters();
    }

    private static QueryDto.Filters filters() {
        ChestIndexConfig config = ChestIndexConfig.get();
        return new QueryDto.Filters(config.includeNested, config.showMachines,
                config.showUtility, config.showEntities, config.originFilter);
    }

    /**
     * Opens the richest match already within arm's reach, if there is one.
     *
     * <p>Standing at the chest and being told to walk to it is the one case
     * where guidance is worse than useless, so the mod just opens it. "Richest"
     * rather than "nearest" because two chests at the same counter are the same
     * distance and the question was where the item is, not where a chest is.
     *
     * <p>Falls through silently when nothing is in reach, leaving the boxes to
     * do their job - and refuses to act on a position the world no longer
     * agrees is a container, because the index can be a few ticks stale and
     * right-clicking thin air with a block in hand places it.
     *
     * @return true if a container was opened
     */
    private static boolean openIfInReach(List<QueryDto.ContainerHit> hits, LocalPlayer player) {
        // An interaction the player did not make is the one thing here a server
        // can actually see, so it never happens on somebody else's server
        // unless they have explicitly said it may.
        if (!Assist.allowed()) return false;
        if (!ChestIndexConfig.get().opensInReach()) return false;
        Minecraft client = Minecraft.getInstance();
        // Never while something is already on screen: the search that started
        // this may have been run from a container that has not closed yet.
        if (client.level == null || ClientCompat.currentScreen() != null) return false;

        double reach = player.blockInteractionRange();
        QueryDto.ContainerHit best = null;
        BlockPos bestPos = null;

        for (QueryDto.ContainerHit hit : hits) {
            BlockPos pos = new BlockPos(BlockKey.x(hit.pos()), BlockKey.y(hit.pos()), BlockKey.z(hit.pos()));
            if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) continue;
            // The index says there is a container here; the world has the vote.
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

    /**
     * Why nothing can be searched from here.
     *
     * <p>The same three cases the search screen distinguishes. One message for
     * all of them sends people looking in the wrong place.
     */
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
