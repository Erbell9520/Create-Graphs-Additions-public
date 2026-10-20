package com.createstats.seismic.archive;

import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicWave;
import com.createstats.seismic.VeinBook;
import com.createstats.seismic.VeinOutline;
import com.createstats.seismic.VeinVisibility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyRereadTest {
    private static final double R = 8.0;
    private static final long NEVER = SurveyReread.NEVER;

    @Test
    @DisplayName("at the rig means within eight blocks of its middle, the edge included, measured in the world")
    void atTheRig() {
        assertTrue(SurveyReread.inReach(0.0, 0.0, 0.0, R));
        assertTrue(SurveyReread.inReach(8.0, 0.0, 0.0, R), "the edge counts, as the witnesses' circle does");
        assertTrue(SurveyReread.inReach(4.0, 4.0, 4.0, R));
        assertFalse(SurveyReread.inReach(8.001, 0.0, 0.0, R));
        assertFalse(SurveyReread.inReach(5.0, 5.0, 5.0, R), "8.66 blocks on the diagonal is out");

        final double plotX = 20_480_000.5;
        final double deckX = 312.5;
        final double playerX = 315.0;
        assertFalse(SurveyReread.inReach(playerX - plotX, 0.0, 0.0, R), "a raw block position: never at the rig");
        assertTrue(SurveyReread.inReach(playerX - deckX, 0.0, 0.0, R), "projected to the world: at the rig");
    }

    @Test
    @DisplayName("a blow is listened to until its last echo and the record has closed it, never before it landed")
    void listening() {
        final long at = 10_000L;
        assertFalse(SurveyReread.listening(NEVER, 64, at), "a rig never fired is not listening");
        assertTrue(SurveyReread.listening(at, 64, at));
        final int last = SeismicWave.listenTicks(64);
        assertTrue(SurveyReread.listening(at, 64, at + last + SurveyReread.LISTEN_GRACE));
        assertFalse(SurveyReread.listening(at, 64, at + last + SurveyReread.LISTEN_GRACE + 1));
        assertFalse(SurveyReread.listening(at, 64, at - 5), "a clock behind the blow is not inside it");
    }

    @Test
    @DisplayName("a client asks once a session and once each new blow; again only while it lacks the newest survey")
    void whenAClientAsks() {
        final long last = 5_000L;
        final long now = 6_000L;
        assertFalse(SurveyReread.shouldAsk(NEVER, 0, false, NEVER, NEVER, NEVER, now), "a rig never fired");
        assertFalse(SurveyReread.shouldAsk(last, 0, true, NEVER, NEVER, NEVER, now), "its echoes still coming in");
        assertTrue(SurveyReread.shouldAsk(last, 0, false, NEVER, NEVER, NEVER, now), "after a relog: nothing held");
        assertTrue(SurveyReread.shouldAsk(last, 0, false, last, NEVER, NEVER, now),
                "a witness, once a session: the rig's older targets come back too");
        assertFalse(SurveyReread.shouldAsk(last, 0, false, last, last, now - 1, now),
                "asked for this blow and holds it: done");
        assertFalse(SurveyReread.shouldAsk(last, 0, false, NEVER, last, now - 1, now),
                "asked a tick ago and still lacking: wait for the answer");
        assertFalse(SurveyReread.shouldAsk(last, 0, false, NEVER, last, now - SurveyReread.RETRY_TICKS + 1, now));
        assertTrue(SurveyReread.shouldAsk(last, 0, false, NEVER, last, now - SurveyReread.RETRY_TICKS, now),
                "still lacking it a bucket's refill later: the answer was refused, ask again");
        assertTrue(SurveyReread.shouldAsk(last + 500, 0, false, last, last, now - 1, now + 600),
                "a new blow since the ask");
        assertFalse(SurveyReread.shouldAsk(last, 2, false, NEVER, last, now - 10_000, now),
                "a blow that made no survey is not in the record: one ask brought what there is, no retries");
        assertFalse(SurveyReread.shouldAsk(last, 0, false, NEVER, last, now + 50, now),
                "a clock behind the ask never reads as long ago");
    }

    @Test
    @DisplayName("never-asked is a sentinel no subtraction meets: Long.MIN_VALUE never wraps into 'just asked'")
    void theSentinelNeverWraps() {
        assertTrue(SurveyReread.shouldAsk(1L, 0, false, NEVER, NEVER, NEVER, 0L));
        assertTrue(SurveyReread.shouldAsk(1L, 0, false, NEVER, 1L, NEVER, Long.MAX_VALUE));
    }

    @Test
    @DisplayName("a read-back never replaces a witness's own map, nothing older replaces a newer one, live opens anew")
    void theRoute() {
        final long old = 1_000L;
        final long blow = 2_000L;
        final long newer = 3_000L;
        final boolean live = false;
        final boolean read = true;

        assertEquals(SurveyReread.Route.OPEN, SurveyReread.route(NEVER, live, blow, live, true), "a first map");
        assertEquals(SurveyReread.Route.OPEN, SurveyReread.route(old, read, blow, live, true),
                "a new blow retires the map an older one left, read back or not");
        assertEquals(SurveyReread.Route.JOIN, SurveyReread.route(blow, live, blow, live, false), "the blow's later veins");
        assertEquals(SurveyReread.Route.DROP, SurveyReread.route(newer, live, blow, live, false),
                "a late packet of an older blow never replaces a newer map");

        assertEquals(SurveyReread.Route.DROP, SurveyReread.route(blow, live, blow, read, true),
                "the witness keeps their own map of that blow");
        assertEquals(SurveyReread.Route.DROP, SurveyReread.route(blow, live, blow, read, false),
                "and the rest of that read-back adds nothing to it");
        assertEquals(SurveyReread.Route.OPEN, SurveyReread.route(NEVER, live, blow, read, true), "after a relog");
        assertEquals(SurveyReread.Route.JOIN, SurveyReread.route(blow, read, blow, read, false), "its later packets");
        assertEquals(SurveyReread.Route.OPEN, SurveyReread.route(old, live, blow, read, true),
                "a newer survey from the record retires an older map held");
        assertEquals(SurveyReread.Route.RESTART, SurveyReread.route(blow, read, blow, read, true),
                "the same blow read back again starts over, never drawn twice");
        assertEquals(SurveyReread.Route.DROP, SurveyReread.route(newer, live, blow, read, true),
                "a newer map held (a blow the record has not closed) is kept");
        assertEquals(SurveyReread.Route.DROP, SurveyReread.route(NEVER, live, blow, read, false),
                "a read-back's later packet whose opening never arrived");
        assertEquals(SurveyReread.Route.DROP, SurveyReread.route(old, read, blow, read, false),
                "nor joins an older map");
    }

    private record E(String station, long at) {
    }

    private static List<E> send(List<E> record, Map<String, Long> held) {
        return SurveyReread.toSend(record, E::station, E::at, held);
    }

    @Test
    @DisplayName("what is sent: every target the asker lacks, newest first; a claim to hold more only sends less")
    void whatIsSent() {
        final List<E> record = List.of(new E("c:ores/iron", 3_000), new E("c:ores/diamond", 2_000),
                new E(SeismicCatalog.VOIDS, 1_000));
        assertEquals(record, send(record, Map.of()), "nothing held: the whole record, in its order");
        assertEquals(List.of(record.get(1), record.get(2)), send(record, Map.of("c:ores/iron", 3_000L)),
                "the witness of the iron blow is sent the rest");
        assertEquals(List.of(record.get(0), record.get(1), record.get(2)), send(record, Map.of("c:ores/iron", 2_500L)),
                "an older iron map is replaced by the record's newer one");
        assertEquals(List.of(record.get(1), record.get(2)), send(record, Map.of("c:ores/iron", 9_999L)),
                "a newer one held (live, a blow the record has not closed) is kept");
        final Map<String, Long> lie = new HashMap<>();
        for (final E e : record) {
            lie.put(e.station(), Long.MAX_VALUE);
        }
        assertTrue(send(record, lie).isEmpty(), "a client claiming everything is sent nothing");
        assertEquals(List.of(record.get(0)), send(List.of(record.get(0), new E("c:ores/iron", 1_500)), Map.of()),
                "one map a station, the first (newest) the record lists");
    }

    @Test
    @DisplayName("packets: every vein in exactly one, in order, within the vein and cell bounds a live survey keeps")
    void batches() {
        assertTrue(SurveyReread.batches(new int[0], 96, 16_384).isEmpty(), "no vein, no batch");
        final java.util.Random rnd = new java.util.Random(7);
        for (int round = 0; round < 200; round++) {
            final int n = rnd.nextInt(400);
            final int[] cells = new int[n];
            for (int i = 0; i < n; i++) {
                cells[i] = rnd.nextInt(10) == 0 ? rnd.nextInt(40_000) : rnd.nextInt(300);
            }
            final int maxVeins = 1 + rnd.nextInt(96);
            final int maxCells = 64 + rnd.nextInt(16_384);
            int next = 0;
            for (final int[] b : SurveyReread.batches(cells, maxVeins, maxCells)) {
                assertEquals(next, b[0], "batches are consecutive, none skipped or repeated");
                assertTrue(b[1] > b[0], "no empty batch");
                assertTrue(b[1] - b[0] <= maxVeins, "at most " + maxVeins + " veins");
                int sum = 0;
                for (int i = b[0]; i < b[1]; i++) {
                    sum += Math.min(maxCells, cells[i]);
                }
                assertTrue(sum <= maxCells, "at most " + maxCells + " cells, a vein bigger than that trimmed alone");
                next = b[1];
            }
            assertEquals(n, next, "every vein lands");
        }
    }

    @Test
    @DisplayName("the bucket: three answers at once, one more every five seconds, never two within half a second")
    void theBucket() {
        final SurveyReread.Throttle t = new SurveyReread.Throttle();
        final UUID a = new UUID(1, 1);
        final int gap = SurveyReread.Throttle.MIN_GAP;
        final int refill = SurveyReread.Throttle.REFILL_TICKS;
        long now = 1_000;
        assertTrue(t.allow(a, now));
        assertFalse(t.allow(a, now + gap - 1), "within the gap");
        for (int i = 1; i < SurveyReread.Throttle.CAPACITY; i++) {
            now += gap;
            assertTrue(t.allow(a, now), "answer " + (i + 1) + " of the bucket");
        }
        now += gap;
        assertFalse(t.allow(a, now), "the bucket is empty");
        assertFalse(t.allow(a, 1_000 + refill - 1), "not yet refilled");
        assertTrue(t.allow(a, 1_000 + refill), "one token a refill");
        assertFalse(t.allow(a, 1_000 + refill + gap), "and only one");

        final SurveyReread.Throttle s = new SurveyReread.Throttle();
        final UUID b = new UUID(2, 2);
        int answered = 0;
        for (long tick = 0; tick < 1_200; tick++) {
            if (s.allow(b, tick)) {
                answered++;
            }
        }
        assertEquals(SurveyReread.Throttle.CAPACITY + 1_200 / refill - 1, answered, "a minute of spam");
        assertTrue(t.allow(new UUID(3, 3), now), "another player has a bucket of their own");
    }

    @Test
    @DisplayName("the bucket after a long wait is full, not overfull; a clock that went back fills it, never wraps")
    void theBucketsClock() {
        final SurveyReread.Throttle t = new SurveyReread.Throttle();
        final UUID a = new UUID(1, 1);
        for (long tick = 0; tick < 30; tick += SurveyReread.Throttle.MIN_GAP) {
            t.allow(a, tick);
        }
        int answered = 0;
        for (long tick = 100_000; tick < 100_000 + 10 * SurveyReread.Throttle.MIN_GAP;
             tick += SurveyReread.Throttle.MIN_GAP) {
            if (t.allow(a, tick)) {
                answered++;
            }
        }
        assertEquals(SurveyReread.Throttle.CAPACITY, answered, "an hour idle refills the bucket to its capacity");
        assertTrue(t.allow(a, 5), "a new server run starts its tick count again: the bucket starts full");
    }

    @Test
    @DisplayName("players long gone are forgotten, players still asking are not")
    void prune() {
        final SurveyReread.Throttle t = new SurveyReread.Throttle();
        for (int i = 0; i < 300; i++) {
            t.allow(new UUID(9, i), 0);
        }
        final UUID busy = new UUID(1, 1);
        t.allow(busy, 10_000);
        t.prune(10_000);
        assertEquals(1, t.remembered(), "only the player who just asked is kept");
        assertFalse(t.allow(busy, 10_001), "and their gap still holds");
    }

    @Test
    @DisplayName("a read-back's targets rise one after another, newest first, all within a second and a half")
    void sheets() {
        assertEquals(0, VeinVisibility.sheetDelay(0, 1));
        assertEquals(0, VeinVisibility.sheetDelay(0, 5));
        for (int n = 1; n <= 20; n++) {
            int before = -1;
            for (int k = 0; k < n; k++) {
                final int d = VeinVisibility.sheetDelay(k, n);
                assertTrue(d > before || n == 1, "each later than the one before: " + k + " of " + n);
                assertTrue(d <= VeinVisibility.SHEETS_SPAN, "the last within the span");
                if (k > 0) {
                    assertTrue(d - before <= VeinVisibility.SHEET_TICKS + 1, "never further apart than a beat");
                }
                before = d;
            }
        }
        assertEquals((int) VeinVisibility.SHEET_TICKS, VeinVisibility.sheetDelay(1, 2), "two targets: a beat apart");
        assertEquals((int) VeinVisibility.SHEETS_SPAN, VeinVisibility.sheetDelay(12, 13),
                "thirteen targets: the last at the end of the span");
    }

    @Test
    @DisplayName("the record gives every target's newest finished survey of a rig, newest first, and nothing else")
    void recallAll() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final int[] r = SiteGround.OWNER_IRON_RIG;
        assertTrue(a.recallAll(r[0], r[1], r[2]).isEmpty(), "nothing struck");
        g.strike(a, r, "c:ores/iron", 32, 1_000L);
        g.strike(a, r, "c:ores/diamond", 32, 2_000L);
        g.strike(a, r, "c:ores/iron", 40, 3_000L);
        g.strike(a, SiteGround.OWNER_DIAMOND_RIG, "c:ores/gold", 32, 3_500L);
        g.strike(a, r, SeismicCatalog.VOIDS, 64, 4_000L, 0L, 0L, 5);
        final List<SurveyRecall> all = a.recallAll(r[0], r[1], r[2]);
        assertEquals(2, all.size(), "iron and diamond; another rig's gold and a running void survey are not here");
        assertEquals("c:ores/iron", all.get(0).target());
        assertEquals(3_000L, all.get(0).at(), "the newer iron blow");
        assertEquals("c:ores/diamond", all.get(1).target());
        for (final SurveyRecall rc : all) {
            final SurveyRecall one = a.recall(r[0], r[1], r[2], rc.target());
            assertNotNull(one);
            assertEquals(one.at(), rc.at());
            assertEquals(one.kept(), rc.kept(), "the same survey as a recall of its target");
            assertEquals(one.veins().size(), rc.veins().size());
        }
    }

    @Test
    @DisplayName("a rig's whole record, every target at radius 64 on the owner's ground, travels in bounded packets")
    void aWholeRecordTravels() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final int[] r = SiteGround.OWNER_IRON_RIG;
        final List<String> targets = new ArrayList<>();
        for (final String o : SiteGround.ORES) {
            targets.add(SeismicCatalog.ORE_PREFIX + o);
        }
        targets.add(SeismicCatalog.VOIDS);
        targets.add(SeismicCatalog.LIQUIDS);
        long at = 1_000L;
        for (final String t : targets) {
            g.strike(a, r, t, 64, at);
            at += 200L;
        }
        final List<SurveyRecall> all = a.recallAll(r[0], r[1], r[2]);
        assertEquals(targets.size(), all.size(), "every target struck comes back");
        assertEquals(SeismicCatalog.LIQUIDS, all.get(0).target(), "newest first");
        long bytes = 0;
        int packets = 0;
        for (final SurveyRecall rc : all) {
            final int[] cells = new int[rc.veins().size()];
            for (int i = 0; i < cells.length; i++) {
                final VeinBook.Vein v = rc.veins().get(i);
                cells[i] = VeinOutline.cells(v.blocks(), VeinOutline.cellFor(v.count())).length;
            }
            final List<int[]> b = SurveyReread.batches(cells, 96, 16_384);
            packets += Math.max(1, b.size());
            for (final int c : cells) {
                bytes += 3L * Math.min(16_384, c) + 40;
            }
        }

        assertTrue(bytes < 256_000, "a whole read-back stays a few tens of kilobytes: " + bytes);
        assertTrue(packets < 64, "and a few dozen packets: " + packets);
    }
}
