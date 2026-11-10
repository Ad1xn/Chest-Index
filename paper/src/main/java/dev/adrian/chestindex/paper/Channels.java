package dev.adrian.chestindex.paper;

import dev.adrian.chestindex.core.net.QueryDto;

import java.util.ArrayList;
import java.util.List;

final class Channels {
    static final String SUMMARY_REQUEST = "chestindex:summary_request";
    static final String SUMMARY_RESPONSE = "chestindex:summary_response";
    static final String CONTAINER_REQUEST = "chestindex:container_request";
    static final String CONTAINER_RESPONSE = "chestindex:container_response";
    static final String STATUS_REQUEST = "chestindex:status_request";
    static final String STATUS_RESPONSE = "chestindex:status_response";
    static final String SUBSCRIBE = "chestindex:subscribe";
    static final String INDEX_CHANGED = "chestindex:index_changed";
    static final String HELLO = "chestindex:hello";

    static final List<String> INCOMING = List.of(SUMMARY_REQUEST, CONTAINER_REQUEST, STATUS_REQUEST, SUBSCRIBE);
    static final List<String> OUTGOING = List.of(SUMMARY_RESPONSE, CONTAINER_RESPONSE, STATUS_RESPONSE,
            INDEX_CHANGED, HELLO);

    static final int MAX_MESSAGE = 32_000;

    private static final int MAX_ID = 256;
    private static final int MAX_TEXT = 128;

    private Channels() {}

    private static QueryDto.Filters readFilters(Wire.Reader in) {
        return new QueryDto.Filters(in.bool(), in.bool(), in.bool(), in.bool(), in.varInt());
    }

    private static void writeFilters(Wire.Writer out, QueryDto.Filters filters) {
        QueryDto.Filters effective = filters == null ? QueryDto.Filters.defaults() : filters;
        out.bool(effective.includeNested())
                .bool(effective.includeMachines())
                .bool(effective.includeUtility())
                .bool(effective.includeEntities())
                .varInt(effective.originFilter());
    }

    static QueryDto.SummaryRequest summaryRequest(byte[] data) {
        Wire.Reader in = new Wire.Reader(data);
        return new QueryDto.SummaryRequest(in.varInt(), in.utf(MAX_TEXT), readFilters(in),
                in.varInt(), in.utf(MAX_ID));
    }

    static byte[] summaryRequest(QueryDto.SummaryRequest request) {
        Wire.Writer out = new Wire.Writer().varInt(request.requestId()).utf(request.text(), MAX_TEXT);
        writeFilters(out, request.filters());
        return out.varInt(request.limit()).utf(request.dimensionId(), MAX_ID).toByteArray();
    }

    static QueryDto.ContainerRequest containerRequest(byte[] data) {
        Wire.Reader in = new Wire.Reader(data);
        int requestId = in.varInt();
        int count = Math.min(Math.max(0, in.varInt()), QueryDto.ContainerRequest.MAX_ITEMS);
        List<String> itemIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) itemIds.add(in.utf(MAX_ID));
        return new QueryDto.ContainerRequest(requestId, itemIds, readFilters(in),
                in.varInt(), in.utf(MAX_ID), in.utf(MAX_TEXT));
    }

    static byte[] containerRequest(QueryDto.ContainerRequest request) {
        Wire.Writer out = new Wire.Writer().varInt(request.requestId()).varInt(request.itemIds().size());
        for (String itemId : request.itemIds()) out.utf(itemId, MAX_ID);
        writeFilters(out, request.filters());
        return out.varInt(request.limit()).utf(request.dimensionId(), MAX_ID)
                .utf(request.text(), MAX_TEXT).toByteArray();
    }

    static QueryDto.StatusRequest statusRequest(byte[] data) {
        return new QueryDto.StatusRequest(new Wire.Reader(data).varInt());
    }

    static boolean subscribe(byte[] data) {
        return new Wire.Reader(data).bool();
    }

    static byte[] summaryResponse(QueryDto.SummaryResponse response) {
        Wire.Writer body = new Wire.Writer();
        int written = 0;
        for (QueryDto.ItemSummary item : response.items()) {
            byte[] entry = new Wire.Writer()
                    .utf(item.itemId(), MAX_ID)
                    .varInt(item.totalCount())
                    .varInt(item.containerCount())
                    .varInt(item.nestedCount())
                    .float64(item.nearestDistSq())
                    .toByteArray();
            if (body.size() + entry.length > MAX_MESSAGE - 16) break;
            body.bytes(entry);
            written++;
        }
        return new Wire.Writer()
                .varInt(response.requestId())
                .bool(response.permitted())
                .varInt(written)
                .bytes(body.toByteArray())
                .toByteArray();
    }

    static byte[] containerResponse(QueryDto.ContainerResponse response) {
        Wire.Writer body = new Wire.Writer();
        int written = 0;
        for (QueryDto.ContainerHit hit : response.hits()) {
            byte[] entry = new Wire.Writer()
                    .utf(hit.typeId(), MAX_ID)
                    .int64(hit.pos())
                    .varInt(hit.matchedCount())
                    .float64(hit.distanceSq())
                    .bool(hit.nested())
                    .bool(hit.natural())
                    .bool(hit.contentsKnown())
                    .varInt(hit.entityId())
                    .toByteArray();
            if (body.size() + entry.length > MAX_MESSAGE - 16) break;
            body.bytes(entry);
            written++;
        }
        return new Wire.Writer()
                .varInt(response.requestId())
                .bool(response.permitted())
                .varInt(written)
                .bytes(body.toByteArray())
                .toByteArray();
    }

    static byte[] statusResponse(QueryDto.StatusResponse response) {
        Wire.Writer out = new Wire.Writer()
                .varInt(response.requestId())
                .bool(response.scanning())
                .varInt(response.regionsRead())
                .varInt(response.regionsTotal())
                .varInt(response.chunksRead())
                .varInt(response.dimensions().size());
        for (QueryDto.DimensionSummary dimension : response.dimensions()) {
            out.utf(dimension.dimensionId(), MAX_ID).varInt(dimension.containers());
        }
        return out.toByteArray();
    }

    static byte[] hello(QueryDto.Hello hello) {
        return new Wire.Writer().varInt(hello.protocolVersion()).bool(hello.canQuery()).toByteArray();
    }

    static final byte[] INDEX_CHANGED_BODY = new byte[0];
}
