package dev.adrian.chesttracker.client;

import dev.adrian.chesttracker.client.highlight.ContainerHighlight;
import dev.adrian.chesttracker.core.net.QueryDto;
import dev.adrian.chesttracker.platform.ItemContentsCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retires materials from a schematic search as the player collects them.
 *
 * <p>A material list search lights up every container holding anything the
 * schematic needs, which is right at the start and wrong by the end: once
 * enough cobblestone is in the player's pockets, the chests that hold
 * cobblestone are no longer an answer to anything, and leaving them lit means
 * the highlight slowly stops meaning "you still need this".
 *
 * <p>So each item drops out on its own the moment the player is carrying enough
 * of it, and the boxes are re-asked for whatever is left. When the last one
 * goes, so does the highlight - and it says so, rather than simply stopping.
 *
 * <p>Dropping out is permanent for the run. A builder's inventory count goes
 * down as well as up, because placing a block spends one, so a live comparison
 * relit every chest holding cobblestone the moment they started laying it.
 * Having had enough once answers the question this feature asks.
 *
 * <p>"Enough" is the schematic's own total for that item, taken from the
 * material list. Deliberately the total rather than what Litematica called
 * missing at the moment the button was pressed: that number is a snapshot
 * against an inventory that is about to change, so it would go stale exactly as
 * the player started fixing it. A total does not move.
 *
 * <p>Counting looks inside shulker boxes and bundles in the inventory, because
 * that is how anybody actually carries materials to a build. A stack of
 * cobblestone sealed in a shulker in your hotbar is cobblestone you have.
 */
public final class MaterialGoals {

    private MaterialGoals() {}

    /**
     * How often the inventory is counted.
     *
     * <p>Every tick would be twenty full inventory walks a second, descending
     * into every shulker, for a question whose answer changes at the speed of
     * picking things up. Half a second is imperceptible here.
     */
    private static final long CHECK_INTERVAL_MS = 500;

    /** Shulker inside a shulker, same as everywhere else this descends. */
    private static final int MAX_NESTING = 2;

    private static final int MAX_CONTAINERS = 64;

    /** What the schematic needs, by registry id. Empty when nothing is being tracked. */
    private static Map<String, Integer> goals = Map.of();

    /** The ids still outstanding, which is what the highlight is currently showing. */
    private static Set<String> outstanding = Set.of();

    /**
     * Materials the player has been seen to have enough of, which stay retired.
     *
     * <p>A latch rather than a live comparison, and this is the whole point.
     * The count in a builder's inventory does not only go up: every block they
     * place takes one back out of it. Re-testing the total each time meant a
     * material retired itself when they picked up a stack and then came back
     * the moment they started building with it - so the highlight relit chests
     * full of cobblestone while the player was in the middle of laying it,
     * which is the opposite of what this feature is for.
     *
     * <p>Having had enough once is the answer to "do you still need to go and
     * find some", and that answer does not change by using it.
     */
    private static Set<String> satisfied = new java.util.HashSet<>();

    private static String dimensionId;
    private static QueryDto.Filters filters;
    private static long lastCheck;

    /** Starts tracking a schematic's materials. */
    public static void track(Map<String, Integer> required, String dimensionId,
                             QueryDto.Filters filters) {
        goals = Map.copyOf(required);
        outstanding = Set.copyOf(required.keySet());
        satisfied = new java.util.HashSet<>();
        MaterialGoals.dimensionId = dimensionId;
        MaterialGoals.filters = filters;
        lastCheck = 0L;
    }

    public static void clear() {
        goals = Map.of();
        outstanding = Set.of();
        satisfied = new java.util.HashSet<>();
        dimensionId = null;
        filters = null;
    }

    public static boolean isTracking() {
        return !goals.isEmpty();
    }

    /**
     * Re-checks what is still needed, and re-asks where it is when that changes.
     *
     * <p>Called from the client tick.
     */
    public static void tick() {
        if (goals.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL_MS) return;
        lastCheck = now;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        Map<String, Integer> carried = countInventory(player);

        List<String> stillNeeded = new ArrayList<>();
        for (Map.Entry<String, Integer> goal : goals.entrySet()) {
            String itemId = goal.getKey();
            if (satisfied.contains(itemId)) continue;
            if (carried.getOrDefault(itemId, 0) >= goal.getValue()) {
                satisfied.add(itemId);
                continue;
            }
            stillNeeded.add(itemId);
        }

        // Checked before the highlight's own state, so the one message worth
        // saying is said. Testing whether the highlight was still up first meant
        // that collecting the last material a moment after it timed out - or
        // while stood at the chest, which is when it usually happens - dropped
        // the tracking silently and the player was never told they were done.
        if (stillNeeded.isEmpty()) {
            ActionBar.say(Component.literal("You have every material the schematic needs")
                    .withStyle(ChatFormatting.GREEN));
            ContainerHighlight.get().clear();
            clear();
            return;
        }

        // The player dismissed it, walked away from it, or it timed out. Either
        // way this is no longer describing anything on screen.
        if (!ContainerHighlight.get().isActive()) {
            clear();
            return;
        }

        // Nothing has been picked up since the last look.
        if (stillNeeded.size() == outstanding.size() && outstanding.containsAll(stillNeeded)) {
            return;
        }

        outstanding = Set.copyOf(stillNeeded);

        // The slot marks can be narrowed immediately - they are read from this
        // set every frame. The boxes need a fresh answer, because a container
        // that only held what has now been collected should stop being lit.
        ContainerHighlight.get().searchingFor(outstanding);
        refresh(client, stillNeeded);
    }

    private static void refresh(Minecraft client, List<String> stillNeeded) {
        String label = stillNeeded.size() == 1
                ? "1 material left" : stillNeeded.size() + " materials left";

        ClientTracker.containers(stillNeeded, filters, MAX_CONTAINERS, "")
                .thenAccept(response -> client.execute(() -> {
                    // The search may have been replaced or dropped while the
                    // server was answering.
                    if (goals.isEmpty()) return;

                    LocalPlayer now = Minecraft.getInstance().player;
                    if (now == null
                            || !now.level().dimension().identifier().toString().equals(dimensionId)) {
                        return;
                    }
                    if (response.hits().isEmpty()) {
                        // Nothing indexed holds the rest. Said out loud rather
                        // than merely dropped: the highlight vanishing on its
                        // own looks like it expired, and "you have collected
                        // everything that was findable" is a different thing
                        // from "time is up".
                        ActionBar.say(Component.literal(
                                        "Nothing indexed holds the rest of the materials")
                                .withStyle(ChatFormatting.YELLOW));
                        ContainerHighlight.get().clear();
                        clear();
                        return;
                    }
                    ContainerHighlight.get().selectHits(response.hits(), dimensionId, label);
                    ContainerHighlight.get().searchingFor(outstanding);
                }));
    }

    // --- counting -----------------------------------------------------------

    /**
     * Everything the player is carrying, by registry id, including what is
     * sealed inside shulker boxes and bundles.
     *
     * <p>Only ids the schematic actually asked for are counted. A builder's
     * inventory is mostly not materials, and totalling all of it would be work
     * thrown away twice a second.
     */
    private static Map<String, Integer> countInventory(LocalPlayer player) {
        Map<String, Integer> counts = new HashMap<>();
        Container inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            add(inventory.getItem(slot), counts, MAX_NESTING);
        }
        return counts;
    }

    private static void add(ItemStack stack, Map<String, Integer> counts, int depth) {
        if (stack == null || stack.isEmpty()) return;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            String key = id.toString();
            if (goals.containsKey(key)) counts.merge(key, stack.getCount(), Integer::sum);
        }
        if (depth <= 0) return;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            ItemContentsCompat.stacks(contents).forEach(inner -> add(inner, counts, depth - 1));
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            bundle.itemCopyStream().forEach(inner -> add(inner, counts, depth - 1));
        }
    }

    /** Preserves the material list's own order, so the first item still names the search. */
    public static Map<String, Integer> emptyGoals() {
        return new LinkedHashMap<>();
    }
}
