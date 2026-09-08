package dev.adrian.chesttracker.core.bench;

import dev.adrian.chesttracker.core.index.IndexQuery;
import dev.adrian.chesttracker.core.index.SearchResult;
import dev.adrian.chesttracker.core.index.WorldIndex;
import dev.adrian.chesttracker.core.model.ContainerRecord;
import dev.adrian.chesttracker.core.model.Origin;
import dev.adrian.chesttracker.core.model.StackDetail;
import dev.adrian.chesttracker.core.model.StackEntry;
import dev.adrian.chesttracker.core.store.IndexCodec;
import dev.adrian.chesttracker.core.store.StringPalette;
import dev.adrian.chesttracker.core.util.BlockKey;
import dev.adrian.chesttracker.core.util.SearchText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@EnabledIfEnvironmentVariable(named = "CT_BENCH", matches = "1")
class IndexBench {

    private static final int CONTAINERS = 60_000;
    private static final int STACKS_PER_CONTAINER = 8;
    private static final int DISTINCT_ITEMS = 2_000;

    private static final double DETAILED_FRACTION = 0.05;

    private static final int WARMUP = 3;
    private static final int RUNS = 10;

    @Test
    void report() throws IOException {
        StringPalette palette = new StringPalette();
        World world = build(palette);

        System.out.println();
        System.out.println("=== ChestTracker index benchmark ===");
        System.out.printf("  %,d containers, %,d stacks, palette %,d entries%n",
                CONTAINERS, CONTAINERS * STACKS_PER_CONTAINER, palette.size());

        long centre = BlockKey.pack(0, 64, 0);

        time("summarise, cold", new Work() {
            private int n;
            @Override public Object run() {
                return world.index.summarise(IndexQuery.builder()
                        .center(BlockKey.pack(n++, 64, 0)).build()).size();
            }
        });

        IndexQuery repeated = IndexQuery.builder().center(centre).build();
        time("summarise, repeated", () -> world.index.summarise(repeated).size());

        String needle = "diamond";
        time("palette scan for a name", () -> matchingItemIds(palette, needle).size());

        Set<Integer> diamondish = matchingItemIds(palette, needle);
        time("summarise, text filter", () ->
                world.index.summarise(IndexQuery.builder()
                        .center(centre).items(diamondish).build()).size());

        Set<Integer> one = Set.of(world.commonItemId);
        time("query, one item", () ->
                world.index.query(IndexQuery.builder()
                        .center(centre).items(one).limit(64).build()).size());

        Set<Integer> mending = detailIds(palette, "mending");
        time("summarise, detail filter", () ->
                world.index.summarise(IndexQuery.builder()
                        .center(centre).details(mending).build()).size());
        time("query, detail filter", () ->
                world.index.query(IndexQuery.builder()
                        .center(centre).details(mending).limit(64).build()).size());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IndexCodec.write(out, palette, world.index);
        byte[] written = out.toByteArray();
        System.out.printf("  index file: %,d KB%n", written.length / 1024);

        time("codec write", () -> {
            ByteArrayOutputStream sink = new ByteArrayOutputStream(written.length);
            try {
                IndexCodec.write(sink, palette, world.index);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return sink.size();
        });
        time("codec read", () -> {
            try {
                return IndexCodec.read(new ByteArrayInputStream(written)).index().size();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println();
    }

    private record World(WorldIndex index, int commonItemId) {}

    private static World build(StringPalette palette) {
        int[] items = new int[DISTINCT_ITEMS];
        for (int i = 0; i < DISTINCT_ITEMS; i++) {
            items[i] = palette.intern("minecraft:item_" + i);
        }
        int diamond = palette.intern("minecraft:diamond");
        int diamondBlock = palette.intern("minecraft:diamond_block");
        int diamondPickaxe = palette.intern("minecraft:diamond_pickaxe");
        int chest = palette.intern("minecraft:chest");
        int dimension = palette.intern("minecraft:overworld");

        int mending = palette.intern(StackDetail.enchantment("minecraft:mending"));
        int unbreaking = palette.intern(StackDetail.enchantment("minecraft:unbreaking"));
        int healing = palette.intern(StackDetail.potion("minecraft:healing"));

        WorldIndex index = new WorldIndex(dimension);
        Random random = new Random(1234);

        for (int i = 0; i < CONTAINERS; i++) {
            int x = random.nextInt(-3000, 3000);
            int y = random.nextInt(-60, 200);
            int z = random.nextInt(-3000, 3000);
            if (!BlockKey.isRepresentable(x, y, z)) continue;

            List<StackEntry> contents = new ArrayList<>(STACKS_PER_CONTAINER);
            for (int s = 0; s < STACKS_PER_CONTAINER; s++) {
                int item = s == 0 ? diamond : items[random.nextInt(DISTINCT_ITEMS)];
                List<Integer> details = List.of();
                if (random.nextDouble() < DETAILED_FRACTION) {
                    item = diamondPickaxe;
                    details = random.nextBoolean()
                            ? List.of(mending, unbreaking) : List.of(healing);
                }
                contents.add(new StackEntry(item, 1 + random.nextInt(64),
                        random.nextInt(4) == 0 ? 1 : 0, null, details));
            }
            index.put(new ContainerRecord(BlockKey.pack(x, y, z), dimension, chest,
                    Origin.PLAYER_PLACED, null, false, true, null, i, contents));
        }
        return new World(index, diamond);
    }

    private static Set<Integer> matchingItemIds(StringPalette palette, String needle) {
        String[] tokens = SearchText.tokens(needle);
        Set<Integer> matches = new HashSet<>();
        List<String> entries = palette.entries();
        for (int id = 0; id < entries.size(); id++) {
            if (SearchText.matches(entries.get(id), tokens)) matches.add(id);
        }
        return matches;
    }

    private static Set<Integer> detailIds(StringPalette palette, String needle) {
        String[] tokens = SearchText.tokens(needle);
        Set<Integer> matches = new HashSet<>();
        List<String> entries = palette.entries();
        for (int id = 0; id < entries.size(); id++) {
            String entry = entries.get(id);
            if (!StackDetail.is(entry, StackDetail.ENCHANTMENT)) continue;
            if (SearchText.matches(StackDetail.value(entry), tokens)) matches.add(id);
        }
        return matches;
    }

    private interface Work {
        Object run();
    }

    private static void time(String what, Work work) {
        for (int i = 0; i < WARMUP; i++) work.run();

        long[] taken = new long[RUNS];
        Object last = null;
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            last = work.run();
            taken[i] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(taken);

        System.out.printf("  %-28s median %7.2f ms   worst %7.2f ms   (= %s)%n",
                what, taken[RUNS / 2] / 1e6, taken[RUNS - 1] / 1e6, last);
    }
}
