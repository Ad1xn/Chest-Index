package dev.adrian.chestindex.net;

import dev.adrian.chestindex.core.net.QueryDto;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PayloadCodecTest {
    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Test
    void summaryRequestSurvivesTheRoundTrip() {
        QueryDto.SummaryRequest original = new QueryDto.SummaryRequest(
                42, "redstone",
                new QueryDto.Filters(false, true, true, false, QueryDto.Filters.ORIGIN_NATURAL), 300);

        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.SummaryRequestPayload.CODEC.encode(
                buf, new ChestIndexPayloads.SummaryRequestPayload(original));
        QueryDto.SummaryRequest decoded =
                ChestIndexPayloads.SummaryRequestPayload.CODEC.decode(buf).request();

        assertEquals(original, decoded);
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void summaryResponseSurvivesTheRoundTrip() {
        QueryDto.SummaryResponse original = QueryDto.SummaryResponse.of(7, List.of(
                new QueryDto.ItemSummary("minecraft:redstone", 2304, 4, 1728, 18.5),
                new QueryDto.ItemSummary("minecraft:diamond", 12, 1, 0, 400.0)));

        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.SummaryResponsePayload.CODEC.encode(
                buf, new ChestIndexPayloads.SummaryResponsePayload(original));
        QueryDto.SummaryResponse decoded =
                ChestIndexPayloads.SummaryResponsePayload.CODEC.decode(buf).response();

        assertEquals(original, decoded);
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void containerRequestSurvivesTheRoundTrip() {
        QueryDto.ContainerRequest original = new QueryDto.ContainerRequest(
                3, "minecraft:redstone", QueryDto.Filters.defaults(), 64);

        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.ContainerRequestPayload.CODEC.encode(
                buf, new ChestIndexPayloads.ContainerRequestPayload(original));

        assertEquals(original, ChestIndexPayloads.ContainerRequestPayload.CODEC.decode(buf).request());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void containerResponseKeepsEveryFlagDistinct() {
        QueryDto.ContainerResponse original = QueryDto.ContainerResponse.of(9, List.of(
                new QueryDto.ContainerHit("minecraft:chest", -1234567890123L, 64, 91.25, true, false, true),
                new QueryDto.ContainerHit("minecraft:barrel", 42L, 1, 0.0, false, true, false)));

        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.ContainerResponsePayload.CODEC.encode(
                buf, new ChestIndexPayloads.ContainerResponsePayload(original));
        QueryDto.ContainerResponse decoded =
                ChestIndexPayloads.ContainerResponsePayload.CODEC.decode(buf).response();

        assertEquals(original, decoded);
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void containerResponseCarriesTheEntityId() {
        QueryDto.ContainerResponse original = QueryDto.ContainerResponse.of(3, List.of(
                new QueryDto.ContainerHit("minecraft:chest_minecart", 77L, 5, 4.0,
                        false, false, true, 40123),
                new QueryDto.ContainerHit("minecraft:chest", 78L, 5, 4.0,
                        false, false, true)));

        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.ContainerResponsePayload.CODEC.encode(
                buf, new ChestIndexPayloads.ContainerResponsePayload(original));
        QueryDto.ContainerResponse decoded =
                ChestIndexPayloads.ContainerResponsePayload.CODEC.decode(buf).response();

        assertEquals(original, decoded);
        assertEquals(40123, decoded.hits().get(0).entityId());
        assertTrue(decoded.hits().get(0).isEntity());
        assertFalse(decoded.hits().get(1).isEntity());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void helloSurvivesTheRoundTrip() {
        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.HelloPayload.CODEC.encode(buf,
                new ChestIndexPayloads.HelloPayload(new QueryDto.Hello(QueryDto.Hello.PROTOCOL_VERSION, true)));
        QueryDto.Hello decoded = ChestIndexPayloads.HelloPayload.CODEC.decode(buf).hello();

        assertEquals(QueryDto.Hello.PROTOCOL_VERSION, decoded.protocolVersion());
        assertTrue(decoded.canQuery());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void emptyResponsesRoundTrip() {
        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.SummaryResponsePayload.CODEC.encode(buf,
                new ChestIndexPayloads.SummaryResponsePayload(QueryDto.SummaryResponse.of(1, List.of())));

        assertTrue(ChestIndexPayloads.SummaryResponsePayload.CODEC.decode(buf).response().items().isEmpty());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void subscribeSurvivesTheRoundTripBothWays() {
        for (boolean watching : new boolean[]{true, false}) {
            FriendlyByteBuf buf = buffer();
            ChestIndexPayloads.SubscribePayload.CODEC.encode(
                    buf, new ChestIndexPayloads.SubscribePayload(watching));

            assertEquals(watching,
                    ChestIndexPayloads.SubscribePayload.CODEC.decode(buf).watching());
            assertEquals(0, buf.readableBytes());
        }
    }

    @Test
    void theChangeSignalCarriesNoBytes() {
        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.IndexChangedPayload.CODEC.encode(
                buf, ChestIndexPayloads.IndexChangedPayload.INSTANCE);

        assertEquals(0, buf.readableBytes());
        assertNotNull(ChestIndexPayloads.IndexChangedPayload.CODEC.decode(buf));
    }

    @Test
    void aRefusalSurvivesTheRoundTrip() {
        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.SummaryResponsePayload.CODEC.encode(buf,
                new ChestIndexPayloads.SummaryResponsePayload(QueryDto.SummaryResponse.refused(4)));
        QueryDto.SummaryResponse decoded =
                ChestIndexPayloads.SummaryResponsePayload.CODEC.decode(buf).response();

        assertFalse(decoded.permitted());
        assertEquals(4, decoded.requestId());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void anImplausibleEntryCountIsRefusedRatherThanAllocatedFor() {
        FriendlyByteBuf buf = buffer();
        buf.writeVarInt(1);
        buf.writeBoolean(true);
        buf.writeVarInt(Integer.MAX_VALUE);

        assertThrows(IllegalArgumentException.class,
                () -> ChestIndexPayloads.SummaryResponsePayload.CODEC.decode(buf));
    }

    @Test
    void statusResponseSurvivesTheRoundTrip() {
        QueryDto.StatusResponse original = new QueryDto.StatusResponse(11, true, 3, 40, 512, List.of(
                new QueryDto.DimensionSummary("minecraft:overworld", 120),
                new QueryDto.DimensionSummary("minecraft:the_nether", 4)));

        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.StatusResponsePayload.CODEC.encode(
                buf, new ChestIndexPayloads.StatusResponsePayload(original));
        QueryDto.StatusResponse decoded =
                ChestIndexPayloads.StatusResponsePayload.CODEC.decode(buf).response();

        assertEquals(original, decoded);
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void statusProgressIsZeroUntilTheTotalIsKnown() {
        assertEquals(0.0f, new QueryDto.StatusResponse(1, true, 5, 0, 90, List.of()).progress());
        assertEquals(0.5f, new QueryDto.StatusResponse(1, true, 5, 10, 90, List.of()).progress());
    }

    @Test
    void aRequestCarriesTheDimensionItMeans() {
        QueryDto.ContainerRequest original = new QueryDto.ContainerRequest(
                4, "minecraft:diamond", QueryDto.Filters.defaults(), 8, "minecraft:the_end");

        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.ContainerRequestPayload.CODEC.encode(
                buf, new ChestIndexPayloads.ContainerRequestPayload(original));

        assertEquals(original, ChestIndexPayloads.ContainerRequestPayload.CODEC.decode(buf).request());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void anOutOfRangeOriginOffTheWireDecodesToAnyRatherThanThrowing() {
        FriendlyByteBuf buf = buffer();
        buf.writeVarInt(1);
        buf.writeUtf("");
        buf.writeBoolean(true);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(true);
        buf.writeVarInt(9999);
        buf.writeVarInt(10);
        buf.writeUtf("");

        QueryDto.SummaryRequest decoded =
                ChestIndexPayloads.SummaryRequestPayload.CODEC.decode(buf).request();

        assertEquals(QueryDto.Filters.ORIGIN_ANY, decoded.filters().originFilter());
        assertEquals("", decoded.dimensionId());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void containerRequestRoundTripsManyItems() {
        QueryDto.ContainerRequest original = new QueryDto.ContainerRequest(
                11, List.of("minecraft:diamond", "minecraft:redstone", "minecraft:oak_planks"),
                QueryDto.Filters.defaults(), 32, "minecraft:overworld");

        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.ContainerRequestPayload.CODEC.encode(
                buf, new ChestIndexPayloads.ContainerRequestPayload(original));

        QueryDto.ContainerRequest read =
                ChestIndexPayloads.ContainerRequestPayload.CODEC.decode(buf).request();
        assertEquals(original, read);
        assertEquals(List.of("minecraft:diamond", "minecraft:redstone", "minecraft:oak_planks"),
                read.itemIds());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void containerRequestWithNoItemsSurvivesTheWire() {
        QueryDto.ContainerRequest original = new QueryDto.ContainerRequest(
                12, List.of(), QueryDto.Filters.defaults(), 8, "");

        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.ContainerRequestPayload.CODEC.encode(
                buf, new ChestIndexPayloads.ContainerRequestPayload(original));

        assertEquals(original, ChestIndexPayloads.ContainerRequestPayload.CODEC.decode(buf).request());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void containerRequestCapsHowManyItemsItWillCarry() {
        List<String> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < QueryDto.ContainerRequest.MAX_ITEMS + 50; i++) {
            tooMany.add("mod:item_" + i);
        }
        QueryDto.ContainerRequest capped = new QueryDto.ContainerRequest(
                13, tooMany, QueryDto.Filters.defaults(), 8, "");
        assertEquals(QueryDto.ContainerRequest.MAX_ITEMS, capped.itemIds().size());

        FriendlyByteBuf buf = buffer();
        ChestIndexPayloads.ContainerRequestPayload.CODEC.encode(
                buf, new ChestIndexPayloads.ContainerRequestPayload(capped));
        assertEquals(capped, ChestIndexPayloads.ContainerRequestPayload.CODEC.decode(buf).request());
        assertEquals(0, buf.readableBytes());
    }
}
