package dev.adrian.chesttracker.core;

import dev.adrian.chesttracker.core.index.IndexQuery;
import dev.adrian.chesttracker.core.index.SearchResult;
import dev.adrian.chesttracker.core.index.WorldIndex;
import dev.adrian.chesttracker.core.model.ContainerRecord;
import dev.adrian.chesttracker.core.model.Origin;
import dev.adrian.chesttracker.core.model.StackEntry;
import dev.adrian.chesttracker.core.util.BlockKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorldIndexTest {

    private static final int DIAMOND = 1;
    private static final int EMERALD = 2;
    private static final int STONE = 3;
    private static final int CHEST = 10;
    private static final int BARREL = 11;

    private WorldIndex index;

    @BeforeEach
    void setUp() {
        index = new WorldIndex(0);
    }

    private ContainerRecord chest(int x, int y, int z, StackEntry... contents) {
        return new ContainerRecord(BlockKey.pack(x, y, z), 0, CHEST, Origin.UNKNOWN,
                null, false, true, null, 0L, List.of(contents));
    }

    private ContainerRecord ownedChest(int x, int y, int z, UUID owner, StackEntry... contents) {
        return new ContainerRecord(BlockKey.pack(x, y, z), 0, CHEST, Origin.PLAYER_PLACED,
                owner, false, true, null, 0L, List.of(contents));
    }

    @Test
    void ownerFilterKeepsOnlyThatPlayersContainers() {
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        index.put(ownedChest(0, 64, 0, mine, new StackEntry(DIAMOND, 5)));
        index.put(ownedChest(0, 64, 4, theirs, new StackEntry(DIAMOND, 5)));

        List<SearchResult> results = index.query(
                IndexQuery.builder().item(DIAMOND).owner(mine).build());

        assertEquals(1, results.size());
        assertEquals(mine, results.get(0).container().owner());
    }

    @Test
    void ownerFilterExcludesContainersWithNoKnownOwner() {
        UUID mine = UUID.randomUUID();
        // A generated chest nobody placed, so its owner was never observed. An
        // owner-restricted query must drop it rather than treat "unknown" as
        // "yours" - that is the difference between a permission tier and a leak.
        index.put(chest(0, 64, 0, new StackEntry(DIAMOND, 5)));

        assertTrue(index.query(IndexQuery.builder().item(DIAMOND).owner(mine).build()).isEmpty());
    }

    @Test
    void noOwnerFilterMatchesEveryOwner() {
        index.put(ownedChest(0, 64, 0, UUID.randomUUID(), new StackEntry(DIAMOND, 5)));
        index.put(chest(0, 64, 4, new StackEntry(DIAMOND, 5)));

        assertEquals(2, index.query(IndexQuery.builder().item(DIAMOND).build()).size());
    }

    @Test
    void ownerFilterAppliesToSummaries() {
        UUID mine = UUID.randomUUID();
        index.put(ownedChest(0, 64, 0, mine, new StackEntry(DIAMOND, 5)));
        index.put(ownedChest(0, 64, 4, UUID.randomUUID(), new StackEntry(DIAMOND, 60)));

        List<WorldIndex.ItemSummary> summaries =
                index.summarise(IndexQuery.builder().owner(mine).build());

        assertEquals(1, summaries.size());
        assertEquals(5, summaries.get(0).totalCount(),
                "another player's stacks must not be counted into the total");
    }

    @Test
    void storesAndRetrievesByPosition() {
        ContainerRecord record = chest(0, 64, 0, new StackEntry(DIAMOND, 5));
        index.put(record);

        assertEquals(1, index.size());
        assertTrue(index.contains(record.pos()));
        assertEquals(record, index.get(record.pos()));
    }

    @Test
    void removingClearsEverySecondaryIndex() {
        ContainerRecord record = chest(0, 64, 0, new StackEntry(DIAMOND, 5));
        index.put(record);
        index.remove(record.pos());

        assertEquals(0, index.size());
        assertTrue(index.positionsInChunk(record.chunkKey()).isEmpty());
        assertTrue(index.query(IndexQuery.builder().item(DIAMOND).build()).isEmpty(),
                "removed container must not remain reachable through the inverted index");
    }

    @Test
    void replacingDropsItemsThatAreNoLongerPresent() {
        ContainerRecord before = chest(0, 64, 0, new StackEntry(DIAMOND, 5));
        index.put(before);
        // Someone emptied the diamonds out and left emeralds.
        index.put(chest(0, 64, 0, new StackEntry(EMERALD, 2)));

        assertTrue(index.query(IndexQuery.builder().item(DIAMOND).build()).isEmpty(),
                "a stale item must not survive a replace and produce a phantom hit");
        assertEquals(1, index.query(IndexQuery.builder().item(EMERALD).build()).size());
        assertEquals(1, index.size());
    }

    // --- Removal of containers that no longer exist -------------------------

    @Test
    void reconcileDropsContainersThatAreNoLongerThere() {
        ContainerRecord stillThere = chest(1, 64, 1, new StackEntry(DIAMOND, 1));
        ContainerRecord broken = chest(2, 64, 2, new StackEntry(EMERALD, 1));
        index.put(stillThere);
        index.put(broken);
        long chunk = stillThere.chunkKey();
        assertEquals(chunk, broken.chunkKey(), "test fixture expects both in one chunk");

        int dropped = index.reconcileChunk(chunk, Set.of(stillThere.pos()));

        assertEquals(1, dropped);
        assertTrue(index.contains(stillThere.pos()));
        assertFalse(index.contains(broken.pos()), "a container that no longer exists must leave the index");
        assertTrue(index.query(IndexQuery.builder().item(EMERALD).build()).isEmpty(),
                "and must not still be findable by its former contents");
    }

    @Test
    void reconcileKeepsEverythingWhenNothingChanged() {
        ContainerRecord a = chest(1, 64, 1);
        ContainerRecord b = chest(2, 64, 2);
        index.put(a);
        index.put(b);

        assertEquals(0, index.reconcileChunk(a.chunkKey(), Set.of(a.pos(), b.pos())));
        assertEquals(2, index.size());
    }

    @Test
    void reconcileEmptiesAChunkWhoseContainersAreAllGone() {
        ContainerRecord a = chest(1, 64, 1);
        index.put(a);

        assertEquals(1, index.reconcileChunk(a.chunkKey(), Set.of()));
        assertEquals(0, index.size());
    }

    @Test
    void reconcileIgnoresChunksItDoesNotTrack() {
        assertEquals(0, index.reconcileChunk(BlockKey.chunkKey(99, 99), Set.of()));
    }

    @Test
    void reconcileLeavesOtherChunksAlone() {
        ContainerRecord near = chest(1, 64, 1);
        ContainerRecord far = chest(500, 64, 500);
        index.put(near);
        index.put(far);

        index.reconcileChunk(near.chunkKey(), Set.of());

        assertEquals(1, index.size());
        assertTrue(index.contains(far.pos()));
    }

    // --- Queries ------------------------------------------------------------

    @Test
    void findsContainersHoldingAnItem() {
        index.put(chest(0, 64, 0, new StackEntry(DIAMOND, 3)));
        index.put(chest(10, 64, 0, new StackEntry(STONE, 64)));

        List<SearchResult> results = index.query(IndexQuery.builder().item(DIAMOND).build());

        assertEquals(1, results.size());
        assertEquals(3, results.get(0).matchedCount());
    }

    @Test
    void ranksResultsNearestFirst() {
        index.put(chest(100, 64, 0, new StackEntry(DIAMOND, 1)));
        index.put(chest(10, 64, 0, new StackEntry(DIAMOND, 1)));
        index.put(chest(50, 64, 0, new StackEntry(DIAMOND, 1)));

        List<SearchResult> results = index.query(IndexQuery.builder()
                .item(DIAMOND).center(BlockKey.pack(0, 64, 0)).build());

        assertEquals(3, results.size());
        assertEquals(10, BlockKey.x(results.get(0).container().pos()));
        assertEquals(50, BlockKey.x(results.get(1).container().pos()));
        assertEquals(100, BlockKey.x(results.get(2).container().pos()));
    }

    @Test
    void appliesDistanceLimit() {
        index.put(chest(10, 64, 0, new StackEntry(DIAMOND, 1)));
        index.put(chest(1000, 64, 0, new StackEntry(DIAMOND, 1)));

        List<SearchResult> results = index.query(IndexQuery.builder()
                .item(DIAMOND).center(BlockKey.pack(0, 64, 0)).maxDistance(100).build());

        assertEquals(1, results.size());
        assertEquals(10, BlockKey.x(results.get(0).container().pos()));
    }

    @Test
    void distanceLimitWorksWithoutAnItemFilter() {
        // Exercises the chunk-radius candidate path rather than the inverted index.
        index.put(chest(10, 64, 0));
        index.put(chest(5000, 64, 5000));

        List<SearchResult> results = index.query(IndexQuery.builder()
                .center(BlockKey.pack(0, 64, 0)).maxDistance(64).build());

        assertEquals(1, results.size());
        assertEquals(10, BlockKey.x(results.get(0).container().pos()));
    }

    @Test
    void appliesResultLimit() {
        for (int x = 0; x < 10; x++) index.put(chest(x * 10, 64, 0, new StackEntry(DIAMOND, 1)));

        List<SearchResult> results = index.query(IndexQuery.builder()
                .item(DIAMOND).center(BlockKey.pack(0, 64, 0)).limit(3).build());

        assertEquals(3, results.size());
    }

    @Test
    void filtersByOriginAndType() {
        index.put(new ContainerRecord(BlockKey.pack(0, 64, 0), 0, CHEST, Origin.NATURAL,
                null, true, true, null, 0L, List.of(new StackEntry(DIAMOND, 1))));
        index.put(new ContainerRecord(BlockKey.pack(10, 64, 0), 0, BARREL, Origin.PLAYER_PLACED,
                null, false, true, null, 0L, List.of(new StackEntry(DIAMOND, 1))));

        assertEquals(1, index.query(IndexQuery.builder().item(DIAMOND).origin(Origin.NATURAL).build()).size());
        assertEquals(1, index.query(IndexQuery.builder().item(DIAMOND).types(Set.of(BARREL)).build()).size());
        assertEquals(1, index.query(IndexQuery.builder().item(DIAMOND).unlootedOnly(true).build()).size());
        assertEquals(2, index.query(IndexQuery.builder().item(DIAMOND).build()).size());
    }

    @Test
    void excludesContainerTypesWhenAsked() {
        index.put(new ContainerRecord(BlockKey.pack(0, 64, 0), 0, CHEST, Origin.UNKNOWN,
                null, false, true, null, 0L, List.of(new StackEntry(DIAMOND, 1))));
        index.put(new ContainerRecord(BlockKey.pack(10, 64, 0), 0, BARREL, Origin.UNKNOWN,
                null, false, true, null, 0L, List.of(new StackEntry(DIAMOND, 1))));

        List<SearchResult> withoutBarrels = index.query(IndexQuery.builder()
                .item(DIAMOND).excludeTypes(Set.of(BARREL)).build());

        assertEquals(1, withoutBarrels.size());
        assertEquals(CHEST, withoutBarrels.get(0).container().typeId());
    }

    @Test
    void excludesNestedHitsWhenAsked() {
        // A diamond inside a shulker box inside this chest.
        index.put(chest(0, 64, 0, new StackEntry(DIAMOND, 1, 1)));

        assertEquals(1, index.query(IndexQuery.builder().item(DIAMOND).includeNested(true).build()).size());
        assertTrue(index.query(IndexQuery.builder().item(DIAMOND).includeNested(false).build()).isEmpty());
    }

    @Test
    void canDropLocationOnlyEntries() {
        index.put(ContainerRecord.locationOnly(BlockKey.pack(0, 64, 0), 0, CHEST, Origin.UNKNOWN, 0L));

        assertEquals(1, index.query(IndexQuery.builder().build()).size());
        assertTrue(index.query(IndexQuery.builder().knownContentsOnly(true).build()).isEmpty(),
                "an entry whose contents we cannot know must be excludable");
    }

    @Test
    void summarisesItemsAcrossContainers() {
        index.put(chest(0, 64, 0, new StackEntry(DIAMOND, 30), new StackEntry(STONE, 64)));
        index.put(chest(10, 64, 0, new StackEntry(DIAMOND, 12)));
        index.put(chest(1000, 64, 0, new StackEntry(EMERALD, 1)));

        List<WorldIndex.ItemSummary> summary = index.summarise(
                IndexQuery.builder().center(BlockKey.pack(0, 64, 0)).build());

        WorldIndex.ItemSummary diamonds = summary.stream()
                .filter(entry -> entry.itemId() == DIAMOND).findFirst().orElseThrow();
        assertEquals(42, diamonds.totalCount(), "a total must span every container");
        assertEquals(2, diamonds.containerCount());
        assertEquals(0.0, diamonds.nearestDistSq(), 1e-9);
    }

    @Test
    void summaryCountsWhatIsSealedInsideShulkersSeparately() {
        // Twelve loose on the shelf, sixty-four sealed in a shulker in the
        // same chest. Both are "diamonds you own"; only one lot can be seen.
        index.put(chest(0, 64, 0,
                new StackEntry(DIAMOND, 12),
                new StackEntry(DIAMOND, 64, 1)));

        WorldIndex.ItemSummary diamonds = index.summarise(IndexQuery.builder().build()).get(0);
        assertEquals(76, diamonds.totalCount(), "nested stacks still count towards the total");
        assertEquals(64, diamonds.nestedCount(), "and are counted again on their own");
        assertEquals(1, diamonds.containerCount());
    }

    @Test
    void nothingIsNestedWhenEveryStackIsLoose() {
        index.put(chest(0, 64, 0, new StackEntry(DIAMOND, 30)));
        index.put(chest(9, 64, 0, new StackEntry(DIAMOND, 12)));

        WorldIndex.ItemSummary diamonds = index.summarise(IndexQuery.builder().build()).get(0);
        assertEquals(42, diamonds.totalCount());
        assertEquals(0, diamonds.nestedCount());
    }

    @Test
    void excludingNestedStacksLeavesNothingNestedToReport() {
        index.put(chest(0, 64, 0, new StackEntry(DIAMOND, 64, 1)));

        // With nesting off the sealed stack is not counted at all, so the
        // nested tally must not describe stacks the total does not include.
        List<WorldIndex.ItemSummary> summary =
                index.summarise(IndexQuery.builder().includeNested(false).build());
        assertTrue(summary.isEmpty() || summary.get(0).nestedCount() == 0,
                "a nested count without a matching total would be a lie");
    }

    @Test
    void summaryCountsAContainerOnceEvenWithSeveralStacks() {
        index.put(chest(0, 64, 0, new StackEntry(DIAMOND, 64), new StackEntry(DIAMOND, 32)));

        WorldIndex.ItemSummary diamonds = index.summarise(IndexQuery.builder().build()).get(0);
        assertEquals(96, diamonds.totalCount());
        assertEquals(1, diamonds.containerCount(), "two stacks in one chest is still one container");
    }

    @Test
    void summaryExcludesContainersWhoseContentsAreUnknown() {
        index.put(chest(0, 64, 0, new StackEntry(DIAMOND, 5)));
        index.put(ContainerRecord.locationOnly(BlockKey.pack(20, 64, 0), 0, CHEST, Origin.NATURAL, 0L));

        List<WorldIndex.ItemSummary> summary = index.summarise(IndexQuery.builder().build());
        assertEquals(1, summary.size());
        assertEquals(5, summary.get(0).totalCount());
    }

    @Test
    void summaryLeadsWithTheMostPlentifulItem() {
        index.put(chest(0, 64, 0, new StackEntry(STONE, 10)));
        index.put(chest(5, 64, 0, new StackEntry(DIAMOND, 100)));

        List<WorldIndex.ItemSummary> summary = index.summarise(
                IndexQuery.builder().center(BlockKey.pack(0, 64, 0)).build());
        assertEquals(DIAMOND, summary.get(0).itemId());
    }

    @Test
    void reportsStats() {
        index.put(chest(0, 64, 0, new StackEntry(DIAMOND, 1)));
        index.put(ContainerRecord.locationOnly(BlockKey.pack(10, 64, 0), 0, CHEST, Origin.NATURAL, 0L));

        WorldIndex.Stats stats = index.stats();
        assertEquals(2, stats.containers());
        assertEquals(1, stats.withKnownContents());
        assertEquals(1, stats.byOrigin().get(Origin.NATURAL));
    }

    /**
     * The summary no longer runs through {@link WorldIndex#query} - it walks
     * the index directly, because ranking every container by distance and then
     * sorting by quantity was most of the cost of every keystroke.
     *
     * <p>That leaves two ways through the same question, so they have to keep
     * agreeing. This asserts it over a spread wide enough to exercise both
     * candidate strategies: the inverted index for a narrow item filter, and
     * the full scan for a broad one.
     */
    @Test
    void theFastSummaryAgreesWithSummarisingRankedResults() {
        for (int i = 0; i < 60; i++) {
            index.put(chest(i * 3, 64, i, 
                    new StackEntry(DIAMOND, 1 + i % 7),
                    new StackEntry(STONE, 2 + i % 5, i % 3 == 0 ? 1 : 0),
                    new StackEntry(EMERALD, 1 + i % 3)));
        }

        for (IndexQuery query : List.of(
                IndexQuery.builder().center(BlockKey.pack(0, 64, 0)).build(),
                IndexQuery.builder().center(BlockKey.pack(90, 64, 30)).build(),
                IndexQuery.builder().items(Set.of(DIAMOND)).build(),
                IndexQuery.builder().items(Set.of(DIAMOND, STONE, EMERALD)).build(),
                IndexQuery.builder().includeNested(false).build(),
                IndexQuery.builder().maxDistance(40).center(BlockKey.pack(0, 64, 0)).build())) {

            // What the old implementation did, spelled out: rank, then total.
            Map<Integer, int[]> expected = new HashMap<>();
            Map<Integer, Double> nearest = new HashMap<>();
            for (SearchResult result : index.query(query)) {
                if (!result.container().contentsKnown()) continue;
                Set<Integer> seenHere = new HashSet<>();
                for (StackEntry entry : result.container().contents()) {
                    if (!query.includeNested() && entry.isNested()) continue;
                    if (!query.itemIds().isEmpty() && !query.itemIds().contains(entry.itemId())) continue;
                    int[] totals = expected.computeIfAbsent(entry.itemId(), id -> new int[3]);
                    totals[0] += entry.count();
                    if (entry.isNested()) totals[2] += entry.count();
                    if (seenHere.add(entry.itemId())) totals[1]++;
                    nearest.merge(entry.itemId(), result.distanceSq(), Math::min);
                }
            }

            List<WorldIndex.ItemSummary> actual = index.summarise(query);
            assertEquals(expected.size(), actual.size(), "same items reported");
            for (WorldIndex.ItemSummary summary : actual) {
                int[] totals = expected.get(summary.itemId());
                assertNotNull(totals, "reported an item the ranked path did not");
                assertEquals(totals[0], summary.totalCount());
                assertEquals(totals[1], summary.containerCount());
                assertEquals(totals[2], summary.nestedCount());
                assertEquals(nearest.get(summary.itemId()), summary.nearestDistSq(), 1e-9);
            }
        }
    }

    /**
     * A broad item filter is deliberately turned back into a full scan, because
     * the union of its holder lists is larger than the index and has to be
     * deduplicated first. The switch must not change the answer - and in
     * particular must not count a container twice for holding two of the
     * wanted items.
     */
    @Test
    void aContainerHoldingSeveralWantedItemsIsCountedOnce() {
        index.put(chest(0, 64, 0,
                new StackEntry(DIAMOND, 5), new StackEntry(STONE, 5), new StackEntry(EMERALD, 5)));

        List<WorldIndex.ItemSummary> summary =
                index.summarise(IndexQuery.builder().items(Set.of(DIAMOND, STONE, EMERALD)).build());

        assertEquals(3, summary.size());
        for (WorldIndex.ItemSummary entry : summary) {
            assertEquals(5, entry.totalCount(), "one chest visited once");
            assertEquals(1, entry.containerCount());
        }
    }

    @Test
    void aDetailFilterPicksOutOneStackOfTwoOfTheSameItem() {
        // The case the whole per-stack detail machinery exists for: two
        // pickaxes, same item id, and only one of them is the one being
        // looked for.
        int pickaxe = 7;
        int mending = 40;
        int unbreaking = 41;

        WorldIndex index = new WorldIndex(0);
        index.put(new ContainerRecord(BlockKey.pack(0, 64, 0), 0, 1, Origin.PLAYER_PLACED, null,
                false, true, null, 1L,
                List.of(new StackEntry(pickaxe, 1, 0, null, List.of(mending)))));
        index.put(new ContainerRecord(BlockKey.pack(10, 64, 0), 0, 1, Origin.PLAYER_PLACED, null,
                false, true, null, 1L,
                List.of(new StackEntry(pickaxe, 1, 0, null, List.of(unbreaking)))));
        index.put(new ContainerRecord(BlockKey.pack(20, 64, 0), 0, 1, Origin.PLAYER_PLACED, null,
                false, true, null, 1L,
                List.of(new StackEntry(pickaxe, 1, 0, null))));

        List<SearchResult> mended = index.query(IndexQuery.builder()
                .details(java.util.Set.of(mending))
                .build());

        assertEquals(1, mended.size());
        assertEquals(BlockKey.pack(0, 64, 0), mended.get(0).container().pos());

        // And the counts behind the grid have to agree with that list.
        List<WorldIndex.ItemSummary> summary = index.summarise(IndexQuery.builder()
                .details(java.util.Set.of(mending))
                .build());
        assertEquals(1, summary.size());
        assertEquals(1, summary.get(0).totalCount());
    }

    @Test
    void noDetailFilterStillFindsEverything() {
        WorldIndex index = new WorldIndex(0);
        index.put(new ContainerRecord(BlockKey.pack(0, 64, 0), 0, 1, Origin.PLAYER_PLACED, null,
                false, true, null, 1L,
                List.of(new StackEntry(7, 1, 0, null, List.of(40)),
                        new StackEntry(8, 3, 0, null))));

        assertEquals(1, index.query(IndexQuery.builder().build()).size());
        assertEquals(2, index.summarise(IndexQuery.builder().build()).size());
    }
}
