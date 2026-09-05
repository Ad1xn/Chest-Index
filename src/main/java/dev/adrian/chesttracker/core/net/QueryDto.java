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

        public Filters {
            if (originFilter < ORIGIN_ANY || originFilter > ORIGIN_NATURAL) originFilter = ORIGIN_ANY;
        }

        public static Filters defaults() {
            return new Filters(true, false, ORIGIN_ANY);
        }

        public Set<Origin> origins() {
            return switch (originFilter) {
                case ORIGIN_PLAYER_PLACED -> Set.of(Origin.PLAYER_PLACED, Origin.UNKNOWN);
                case ORIGIN_NATURAL -> Set.of(Origin.NATURAL);
                default -> Set.of();
            };
        }
    }

    public record SummaryRequest(int requestId, String text, Filters filters, int limit) {}

    public record ItemSummary(String itemId, int totalCount, int containerCount,
                              int nestedCount, double nearestDistSq) {}

    public record SummaryResponse(int requestId, boolean permitted, List<ItemSummary> items) {
        public SummaryResponse {
            items = items == null ? List.of() : List.copyOf(items);
        }

        public static SummaryResponse refused(int requestId) {
            return new SummaryResponse(requestId, false, List.of());
        }

        public static SummaryResponse of(int requestId, List<ItemSummary> items) {
            return new SummaryResponse(requestId, true, items);
        }
    }

    public record ContainerRequest(int requestId, String itemId, Filters filters, int limit) {}

    public record ContainerHit(String typeId, long pos, int matchedCount, double distanceSq,
                               boolean nested, boolean natural, boolean contentsKnown) {}

    public record ContainerResponse(int requestId, boolean permitted, List<ContainerHit> hits) {
        public ContainerResponse {
            hits = hits == null ? List.of() : List.copyOf(hits);
        }

        public static ContainerResponse refused(int requestId) {
            return new ContainerResponse(requestId, false, List.of());
        }

        public static ContainerResponse of(int requestId, List<ContainerHit> hits) {
            return new ContainerResponse(requestId, true, hits);
        }
    }

    public record Hello(int protocolVersion, boolean canQuery) {

        public static final int PROTOCOL_VERSION = 3;
    }
}
