package dev.adrian.chestindex.core.net;

import dev.adrian.chestindex.core.model.Origin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class QueryDtoTest {
    @Test
    void originFilterMapsToTheMatchingOrigin() {
        assertEquals(Set.of(Origin.PLAYER_PLACED, Origin.UNKNOWN),
                new QueryDto.Filters(true, false, false, false, QueryDto.Filters.ORIGIN_PLAYER_PLACED).origins());
        assertEquals(Set.of(Origin.NATURAL),
                new QueryDto.Filters(true, false, false, false, QueryDto.Filters.ORIGIN_NATURAL).origins());
    }

    @Test
    void anyOriginMeansNoConstraint() {
        assertTrue(new QueryDto.Filters(true, false, false, false, QueryDto.Filters.ORIGIN_ANY).origins().isEmpty());
    }

    @Test
    void outOfRangeOriginFallsBackToAny() {
        assertEquals(QueryDto.Filters.ORIGIN_ANY, new QueryDto.Filters(true, false, false, false, 99).originFilter());
        assertEquals(QueryDto.Filters.ORIGIN_ANY, new QueryDto.Filters(true, false, false, false, -3).originFilter());
        assertTrue(new QueryDto.Filters(true, false, false, false, 99).origins().isEmpty());
    }

    @Test
    void theBuiltFilterKeepsContainersWhosePlacementWasNeverSeen() {
        Set<Origin> built = new QueryDto.Filters(true, false, false, false, QueryDto.Filters.ORIGIN_PLAYER_PLACED).origins();

        assertTrue(built.contains(Origin.PLAYER_PLACED));
        assertTrue(built.contains(Origin.UNKNOWN));
        assertFalse(built.contains(Origin.NATURAL));
    }

    @Test
    void theGeneratedFilterStaysExact() {
        Set<Origin> generated = new QueryDto.Filters(true, false, false, false, QueryDto.Filters.ORIGIN_NATURAL).origins();

        assertEquals(Set.of(Origin.NATURAL), generated);
    }

    @Test
    void refusalAndAnswerAreDistinguishable() {
        assertFalse(QueryDto.SummaryResponse.refused(1).permitted());
        assertTrue(QueryDto.SummaryResponse.of(1, List.of()).permitted());
        assertFalse(QueryDto.ContainerResponse.refused(1).permitted());
        assertTrue(QueryDto.ContainerResponse.of(1, List.of()).permitted());
    }

    @Test
    void defaultsCountNestedItemsAndHideMachines() {
        QueryDto.Filters defaults = QueryDto.Filters.defaults();
        assertTrue(defaults.includeNested());
        assertFalse(defaults.includeMachines());
        assertEquals(QueryDto.Filters.ORIGIN_ANY, defaults.originFilter());
    }

    @Test
    void responsesCopyTheirListsAndTolerateNull() {
        List<QueryDto.ItemSummary> source = new java.util.ArrayList<>();
        source.add(new QueryDto.ItemSummary("minecraft:redstone", 64, 1, 0, 4.0));
        QueryDto.SummaryResponse response = QueryDto.SummaryResponse.of(7, source);

        source.clear();
        assertEquals(1, response.items().size());
        assertEquals(7, response.requestId());

        assertTrue(QueryDto.SummaryResponse.of(1, null).items().isEmpty());
        assertTrue(QueryDto.ContainerResponse.of(1, null).hits().isEmpty());
    }
}
