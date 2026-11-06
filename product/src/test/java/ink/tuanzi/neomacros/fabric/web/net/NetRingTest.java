package ink.tuanzi.neomacros.fabric.web.net;

import ink.tuanzi.neomacros.core.net.NetTraceEvent;
import ink.tuanzi.neomacros.fabric.web.v1.NetworkEntry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The net ring's payload bookkeeping: bodies ride along with their entry,
 * share one byte budget (oldest seq evicted first), and vanish with the
 * ring slot they belong to.
 */
class NetRingTest {

    private static NetTraceEvent event(String url, String requestBody, byte[] responseBody) {
        return new NetTraceEvent(1234L, "GET", url, null, null,
                Map.of(), null, requestBody, false,
                200, Map.of("content-type", "text/plain"), null,
                responseBody, false, false, responseBody == null ? null : (long) responseBody.length,
                5L, 2L, 3L, null);
    }

    @Test
    void bodiesRideAlongWithTheirEntries() {
        NetRing ring = new NetRing(8);
        ring.append(event("https://example.com/a", "req-a", "resp-a".getBytes(StandardCharsets.UTF_8)));

        NetworkEntry entry = ring.entry(1);
        assertNotNull(entry);
        assertTrue(entry.hasRequestBody());
        assertTrue(entry.hasResponseBody());
        assertEquals("req-a", ring.requestBody(1));
        assertEquals("resp-a", new String(ring.responseBody(1), StandardCharsets.UTF_8));
        assertEquals(1, ring.lastSeq());
    }

    @Test
    void budgetEvictsOldestSeqAcrossSides() {
        NetRing ring = new NetRing(8, 12); // tiny budget
        ring.append(event("https://example.com/a", "aaa", "aaaaa".getBytes(StandardCharsets.UTF_8)));
        ring.append(event("https://example.com/b", "bbbb", "bbb".getBytes(StandardCharsets.UTF_8)));

        // seq 1 charged 3*2 + 5 = 11; seq 2's request pushes the total past 12,
        // and both seq 1 bodies evict (oldest first) to fit again
        assertNull(ring.requestBody(1), "the oldest request body yields to the budget");
        assertNull(ring.responseBody(1), "the oldest response body yields to the budget");
        assertTrue(ring.entry(1).hasRequestBody() && ring.entry(1).hasResponseBody(),
                "the entry keeps its recorded flags; the panel reads 410 from the endpoint");
        assertEquals("bbbb", ring.requestBody(2));
        assertEquals("bbb", new String(ring.responseBody(2), StandardCharsets.UTF_8));
    }

    @Test
    void ringOverflowDropsBodiesWithTheirSlots() {
        NetRing ring = new NetRing(2, 10_000);
        ring.append(event("https://example.com/a", "aaa", "aaa".getBytes(StandardCharsets.UTF_8)));
        ring.append(event("https://example.com/b", "bbb", "bbb".getBytes(StandardCharsets.UTF_8)));
        ring.append(event("https://example.com/c", "ccc", "ccc".getBytes(StandardCharsets.UTF_8)));

        assertNull(ring.entry(1), "the oldest ring slot is gone");
        assertNull(ring.requestBody(1), "the dropped slot's request body is gone too");
        assertNull(ring.responseBody(1));
        assertNotNull(ring.entry(2));
        assertNotNull(ring.entry(3));
        assertEquals(3, ring.lastSeq());
    }

    @Test
    void unknownSeqReadsBackNull() {
        NetRing ring = new NetRing(8);
        ring.append(event("https://example.com/a", null, null));
        assertNull(ring.entry(99));
        assertNull(ring.requestBody(99));
        assertNull(ring.responseBody(99));
    }
}
