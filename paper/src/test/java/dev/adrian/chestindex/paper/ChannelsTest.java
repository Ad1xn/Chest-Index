package dev.adrian.chestindex.paper;

import dev.adrian.chestindex.core.net.QueryDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelsTest {
    @Test
    void varIntsMatchTheVanillaLayout() {
        assertArrayEquals(new byte[] {0}, new Wire.Writer().varInt(0).toByteArray());
        assertArrayEquals(new byte[] {(byte) 0x80, 0x01}, new Wire.Writer().varInt(128).toByteArray());
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F},
                new Wire.Writer().varInt(-1).toByteArray());
        assertEquals(-1, new Wire.Reader(new Wire.Writer().varInt(-1).toByteArray()).varInt());
    }

    @Test
    void stringsAreByteLengthPrefixed() {
        byte[] bytes = new Wire.Writer().utf("ä", 16).toByteArray();
        assertArrayEquals(new byte[] {2, (byte) 0xC3, (byte) 0xA4}, bytes);
    }

    @Test
    void summaryRequestsSurviveTheTrip() {
        QueryDto.SummaryRequest sent = new QueryDto.SummaryRequest(42, "diamond",
                new QueryDto.Filters(true, false, true, false, QueryDto.Filters.ORIGIN_PLAYER_PLACED),
                500, "minecraft:the_nether");
        assertEquals(sent, Channels.summaryRequest(Channels.summaryRequest(sent)));
    }

    @Test
    void containerRequestsSurviveTheTrip() {
        QueryDto.ContainerRequest sent = new QueryDto.ContainerRequest(7,
                List.of("minecraft:diamond", "minecraft:emerald"), QueryDto.Filters.defaults(), 64,
                "minecraft:overworld", "@chest");
        assertEquals(sent, Channels.containerRequest(Channels.containerRequest(sent)));
    }

    @Test
    void containerResponsesReadBackInTheModsOrder() {
        QueryDto.ContainerHit hit = new QueryDto.ContainerHit("minecraft:barrel", 123456789L, 12, 30.5,
                true, false, true, 99);
        Wire.Reader in = new Wire.Reader(Channels.containerResponse(QueryDto.ContainerResponse.of(3, List.of(hit))));
        assertEquals(3, in.varInt());
        assertTrue(in.bool());
        assertEquals(1, in.varInt());
        assertEquals(hit, new QueryDto.ContainerHit(in.utf(256), in.int64(), in.varInt(), in.float64(),
                in.bool(), in.bool(), in.bool(), in.varInt()));
    }

    @Test
    void oversizedSummariesAreCutToFitOneMessage() {
        List<QueryDto.ItemSummary> items = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            items.add(new QueryDto.ItemSummary("somemod:a_rather_long_item_name_number_" + i, i, 1, 0, i));
        }
        byte[] body = Channels.summaryResponse(QueryDto.SummaryResponse.of(1, items));
        assertTrue(body.length <= Channels.MAX_MESSAGE);

        Wire.Reader in = new Wire.Reader(body);
        in.varInt();
        in.bool();
        int count = in.varInt();
        for (int i = 0; i < count; i++) {
            assertEquals(items.get(i).itemId(), in.utf(256));
            in.varInt();
            in.varInt();
            in.varInt();
            in.float64();
        }
        assertThrows(IllegalArgumentException.class, in::bool);
    }

    @Test
    void helloCarriesTheProtocolVersion() {
        Wire.Reader in = new Wire.Reader(Channels.hello(new QueryDto.Hello(QueryDto.Hello.PROTOCOL_VERSION, true)));
        assertEquals(QueryDto.Hello.PROTOCOL_VERSION, in.varInt());
        assertTrue(in.bool());
    }
}
