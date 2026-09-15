package dev.adrian.chestindex.client;

import dev.adrian.chestindex.client.highlight.ContainerHighlight;
import dev.adrian.chestindex.core.net.QueryDto;
import dev.adrian.chestindex.platform.ItemContentsCompat;
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

public final class MaterialGoals {

    private MaterialGoals() {}

    private static final long CHECK_INTERVAL_MS = 500;

    private static final int MAX_NESTING = 2;

    private static final int MAX_CONTAINERS = 64;

    private static Map<String, Integer> goals = Map.of();

    private static Set<String> outstanding = Set.of();

    private static Set<String> satisfied = new java.util.HashSet<>();

    private static String dimensionId;
    private static QueryDto.Filters filters;
    private static long lastCheck;

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

        if (stillNeeded.isEmpty()) {
            ActionBar.say(Component.literal("You have every material the schematic needs")
                    .withStyle(ChatFormatting.GREEN));
            ContainerHighlight.get().clear();
            clear();
            return;
        }

        if (!ContainerHighlight.get().isActive()) {
            clear();
            return;
        }

        if (stillNeeded.size() == outstanding.size() && outstanding.containsAll(stillNeeded)) {
            return;
        }

        outstanding = Set.copyOf(stillNeeded);

        ContainerHighlight.get().searchingFor(outstanding);
        refresh(client, stillNeeded);
    }

    private static void refresh(Minecraft client, List<String> stillNeeded) {
        String label = stillNeeded.size() == 1
                ? "1 material left" : stillNeeded.size() + " materials left";

        ClientTracker.containers(stillNeeded, filters, MAX_CONTAINERS, "")
                .thenAccept(response -> client.execute(() -> {
                    if (goals.isEmpty()) return;

                    LocalPlayer now = Minecraft.getInstance().player;
                    if (now == null
                            || !now.level().dimension().identifier().toString().equals(dimensionId)) {
                        return;
                    }
                    if (response.hits().isEmpty()) {
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

    public static Map<String, Integer> emptyGoals() {
        return new LinkedHashMap<>();
    }
}
