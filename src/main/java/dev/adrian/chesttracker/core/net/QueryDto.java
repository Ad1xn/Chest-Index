package dev.adrian.chesttracker.core.net;

import dev.adrian.chesttracker.core.model.Origin;

import java.util.List;
import java.util.Set;

public final class QueryDto {

    private QueryDto() {}

    public record Filters(boolean includeNested, boolean includeMachines, int originFilter) {

        public static final int ORIGIN_ANY = 0;
        public static final int ORIGIN_PLAYER_PLACED = 1;
        public static final int ORIGIN_NATURAL = 2;

        public static Filters defaults() {
            return new Filters(true, false, ORIGIN_ANY);
        }

        public Set<Origin> origins() {
            return switch (originFilter) {
                case ORIGIN_PLAYER_PLACED -> Set.of(Origin.PLAYER_PLACED);
                case ORIGIN_NATURAL -> Set.of(Origin.NATURAL);
                default -> Set.of();
            };
        }
    }

    public record SummaryRequest(String text, Filters filters, long centre, int limit) {}

    public record ItemSummary(String itemId, int totalCount, int containerCount, double nearestDistSq) {}

    public record SummaryResponse(List<ItemSummary> items) {
        public SummaryResponse {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record ContainerRequest(String itemId, Filters filters, long centre, int limit) {}

    public record ContainerHit(String typeId, long pos, int matchedCount, double distanceSq,
                               boolean nested, boolean natural, boolean contentsKnown) {}

    public record ContainerResponse(List<ContainerHit> hits) {
        public ContainerResponse {
            hits = hits == null ? List.of() : List.copyOf(hits);
        }
    }
}
