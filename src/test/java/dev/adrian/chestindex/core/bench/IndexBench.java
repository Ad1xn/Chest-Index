package dev.adrian.chestindex.core.bench;

import dev.adrian.chestindex.core.index.IndexQuery;
import dev.adrian.chestindex.core.index.SearchResult;
import dev.adrian.chestindex.core.index.WorldIndex;
import dev.adrian.chestindex.core.model.ContainerRecord;
import dev.adrian.chestindex.core.model.Origin;
import dev.adrian.chestindex.core.model.StackDetail;
import dev.adrian.chestindex.core.model.StackEntry;
import dev.adrian.chestindex.core.store.IndexCodec;
import dev.adrian.chestindex.core.store.StringPalette;
import dev.adrian.chestindex.core.util.BlockKey;
import dev.adrian.chestindex.core.util.SearchText;
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

/**
 * Times the search path against a world the size of a real one.
 *
 * <p>Not a test - it asserts nothing and it takes about a minute - so it only
 * runs when asked for:
 *
 * <pre>
 *   CT_BENCH=1 ./gradlew :1.21.11:test --tests '*IndexBench*' -i
 * </pre>
 *
 * <p>It exists because the performance work before 1.0 has to be aimed at
 * something. The costs here are the ones a player actually waits on: the grid
 * refreshing on every keystroke, a "where is this" click, and loading the index
 * at world join. Everything else in the mod happens once or happens per frame on
 * a handful of boxes.
 *
 * <p>The world it builds is deliberately at the large end of plausible - sixty
 * thousand containers, eight stacks each, a palette of a couple of thousand
 * items - because the numbers that matter are the ones somebody with a real
 * base sees, not the ones a fresh world does.
 */
@EnabledIfEnvironmentVariable(named = "CT_BENCH", matches = "1")
class IndexBench {

    private static final int CONTAINERS = 60_000;
    private static final int STACKS_PER_CONTAINER = 8;
    private static final int DISTINCT_ITEMS = 2_000;

    /** Roughly how much of a real world's storage is enchanted or written on. */
    private static final double DETAILED_FRACTION = 0.05;

    private static final int WARMUP = 3;
    private static final int RUNS = 10;

    @Test
    void report() throws IOException {
        StringPalette palette = new StringPalette();
        World world = build(palette);

        System.out.println();
        System.out.println("=== ChestIndex index benchmark ===");
        System.out.printf("  %,d containers, %,d stacks, palette %,d entries%n",
                CONTAINERS, CONTAINERS * STACKS_PER_CONTAINER, palette.size());

        long centre = BlockKey.pack(0, 64, 0);

        // The grid, with nothing typed: what opening the screen costs. Timed
        // with a query that differs every run, because the same one twice is
        // answered from the cache - which is the next measurement, not this one.
        time("summarise, cold", new Work() {
            private int n;
            @Override public Object run() {
                // The centre moves by a block, which changes the query - so
                // the cache misses - without changing which way the walk goes.
                // A distance limit would have sent it down the chunk-radius
                // path and measured something else entirely.
                return world.index.summarise(IndexQuery.builder()
                        .center(BlockKey.pack(n++, 64, 0)).build()).size();
            }
        });

        // What the screen actually does: the same question again a second
        // later, with nothing changed in between.
        IndexQuery repeated = IndexQuery.builder().center(centre).build();
        time("summarise, repeated", () -> world.index.summarise(repeated).size());

        // The grid, one keystroke in. The palette scan that turns text into item
        // ids is timed apart from the walk it feeds, because they are fixed by
        // different things - the palette's size, and the world's.
        String needle = "diamond";
        time("palette scan for a name", () -> matchingItemIds(palette, needle).size());

        Set<Integer> diamondish = matchingItemIds(palette, needle);
        time("summarise, text filter", () ->
                world.index.summarise(IndexQuery.builder()
                        .center(centre).items(diamondish).build()).size());

        // One item, which is what clicking a row asks.
        Set<Integer> one = Set.of(world.commonItemId);
        time("query, one item", () ->
                world.index.query(IndexQuery.builder()
                        .center(centre).items(one).limit(64).build()).size());

        // The new one: everything enchanted with a thing.
        Set<Integer> mending = detailIds(palette, "mending");
        time("summarise, detail filter", () ->
                world.index.summarise(IndexQuery.builder()
                        .center(centre).details(mending).build()).size());
        time("query, detail filter", () ->
                world.index.query(IndexQuery.builder()
                        .center(centre).details(mending).limit(64).build()).size());

        // Joining a world, and leaving it.
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

    // --- the world under test ------------------------------------------------

    private record World(WorldIndex index, int commonItemId) {}

    private static World build(StringPalette palette) {
        // Items first, so their ids are the low ones - as in a real palette,
        // where the details are interned later, as containers are read.
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
        // Something present in every container, which is the worst case for a
        // "where is this" click.
        return new World(index, diamond);
    }

    // --- the palette scans, as QueryService does them -------------------------

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

    // --- timing ---------------------------------------------------------------

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
