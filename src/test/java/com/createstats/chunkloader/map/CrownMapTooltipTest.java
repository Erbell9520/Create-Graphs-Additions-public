package com.createstats.chunkloader.map;

import com.createstats.chunkloader.core.CrownBoard;
import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownPicks;
import com.createstats.chunkloader.core.CrownRegion;
import com.createstats.chunkloader.core.CrownState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.createstats.chunkloader.map.CrownMapFixtures.LAW;
import static com.createstats.chunkloader.map.CrownMapFixtures.board;
import static com.createstats.chunkloader.map.CrownMapFixtures.crown;
import static com.createstats.chunkloader.map.CrownMapFixtures.fed;
import static com.createstats.chunkloader.map.CrownMapFixtures.state;
import static com.createstats.chunkloader.map.CrownMapFixtures.studs12;
import static com.createstats.chunkloader.map.CrownMapTooltip.AMBER;
import static com.createstats.chunkloader.map.CrownMapTooltip.GREY;
import static com.createstats.chunkloader.map.CrownMapTooltip.RED;
import static com.createstats.chunkloader.map.CrownMapTooltip.ROSE;
import static com.createstats.chunkloader.map.CrownMapTooltip.WHITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownMapTooltipTest {
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static JsonObject lang;

    private static String text(CrownMapTooltip.Line l) {
        if (lang == null) {
            try {
                lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException e) {
                throw new AssertionError("no en_us.json at " + LANG.toAbsolutePath(), e);
            }
        }
        assertTrue(lang.has(l.key()), "missing language key: " + l.key());
        final String format = lang.get(l.key()).getAsString();
        assertFalse(format.contains("\u2014") || format.contains("\u2013"), "an em or en dash in " + l.key());
        final List<Object> args = new ArrayList<>();
        for (Object a : l.args()) {
            args.add(a instanceof CrownMapTooltip.Line nested ? text(nested) : String.valueOf(a));
        }
        return format(format, args);
    }

    private static String format(String f, List<Object> args) {
        final Matcher m = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)").matcher(f);
        final StringBuilder out = new StringBuilder();
        int next = 0;
        int at = 0;
        while (m.find()) {
            out.append(f, at, m.start());
            if ("%".equals(m.group(2))) {
                out.append('%');
            } else {
                final int i = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                out.append(i < args.size() ? args.get(i) : "");
            }
            at = m.end();
        }
        return out.append(f.substring(at)).toString();
    }

    private static void says(List<CrownMapTooltip.Line> lines, Object... textThenColour) {
        assertEquals(textThenColour.length / 2, lines.size(), "lines: " + texts(lines));
        for (int i = 0; i < lines.size(); i++) {
            assertEquals(textThenColour[2 * i], text(lines.get(i)), "line " + i);
            assertEquals(textThenColour[2 * i + 1], lines.get(i).colour(), "colour of line " + i);
        }
    }

    private static List<String> texts(List<CrownMapTooltip.Line> lines) {
        final List<String> out = new ArrayList<>();
        lines.forEach(l -> out.add(text(l)));
        return out;
    }

    private static String g(long v) {
        return CrownPlate.group(v);
    }

    private static final CrownMapTooltip.Crown MID = crown(fed(12, 31), studs12(), 12, true);

    @Test
    @DisplayName("mid: a free chunk's price, the draw then, and the action (v11 mid)")
    void pick() {
        says(CrownMapTooltip.lines(MID, 12, -22),
                "Chunk 12, -22", WHITE,
                "x 192 to 207, z -352 to -337", GREY,
                "Chunk 13 of 25: +3,213 FE/t", GREY,
                "Draw then 25,111 FE/t", GREY,
                "Click to hold it", ROSE);
    }

    @Test
    @DisplayName("drop: which held chunk, the draw after and what it saves (v11 drop)")
    void drop() {
        says(CrownMapTooltip.lines(MID, 10, -23),
                "Chunk 10, -23", WHITE,
                "x 160 to 175, z -368 to -353", GREY,
                "Held: chunk 6 of 12", GREY,
                "Draw then " + g(LAW.totalDraw(11)) + " FE/t, saves " + g(LAW.nextPrice(11)), GREY,
                "Click to let it go", ROSE);
    }

    @Test
    @DisplayName("the crown's own chunk refuses with its reason and its way out (v11 own)")
    void own() {
        assertEquals(CrownPicks.Verdict.OWN_CHUNK, CrownMapTooltip.verdict(MID, 9, -24));
        says(CrownMapTooltip.body(MID, CrownPicks.Verdict.OWN_CHUNK, 9, -24),
                "The crown's own chunk", WHITE,
                "Held while the crown holds anything", GREY,
                "Break the crown to free it", GREY);
    }

    @Test
    @DisplayName("the surround: outside the crown's region, which region it is in (v11 outside)")
    void outside() {
        assertEquals(CrownPicks.Verdict.OUTSIDE_REGION, CrownMapTooltip.verdict(MID, 34, -26));
        says(CrownMapTooltip.body(MID, CrownPicks.Verdict.OUTSIDE_REGION, 34, -26),
                "Outside region 0, -1", RED,
                "Region 1, -1 needs a crown of its own", GREY);
    }

    @Test
    @DisplayName("at the station's cap and at the player's (v11 cap, playerfull)")
    void caps() {
        final CrownMapTooltip.Crown cap = crown(fed(25, 44), studs12(), 25, true);
        assertEquals(CrownPicks.Verdict.STATION_FULL, CrownMapTooltip.verdict(cap, 14, -20));
        says(CrownMapTooltip.body(cap, CrownPicks.Verdict.STATION_FULL, 14, -20),
                "This crown holds 25, its most", RED, "Drop a chunk to pick another", GREY);
        final CrownMapTooltip.Crown full = crown(fed(12, 50), studs12(), 12, true);
        assertEquals(CrownPicks.Verdict.PLAYER_FULL, CrownMapTooltip.verdict(full, 12, -22));
        says(CrownMapTooltip.body(full, CrownPicks.Verdict.PLAYER_FULL, 12, -22),
                "You hold 50 across your crowns", RED, "Drop one here or at another crown", GREY);
    }

    @Test
    @DisplayName("the studs cannot carry the next chunk: what they carry, what it needs, where to move a line (v11 carry)")
    void boardCarry() {
        final CrownMapTooltip.Crown c = crown(fed(24, 43), board(2, 1, true, true, 98_304, 0, -1), 24, true);
        assertEquals(CrownPicks.Verdict.BOARD_CARRY, CrownMapTooltip.verdict(c, 12, -21));
        says(CrownMapTooltip.body(c, CrownPicks.Verdict.BOARD_CARRY, 12, -21),
                "Your studs carry 98,304 FE/t;", RED,
                "25 chunks need 100,707", RED,
                "Move a line to PDU stud 1 or 2", GREY);
        final CrownMapTooltip.Crown hv = crown(fed(24, 43), board(0, 1, true, true, 98_304, 0, -1), 24, true);
        assertEquals("Both lines are on HV studs: drop a chunk to pick this one",
                text(CrownMapTooltip.body(hv, CrownPicks.Verdict.BOARD_CARRY, 12, -21).get(2)),
                "already on PDU studs 1 and 2, moving a line is no way out");
    }

    @Test
    @DisplayName("dark: a pick joins the queue, and says what brings them all back (v11 dark)")
    void dark() {
        final CrownMapTooltip.Crown c = crown(state(0, 16, CrownHold.Phase.DARK, 0, 0, 35, CrownState.Reason.NO_POWER),
                studs12(), 16, true);
        says(CrownMapTooltip.body(c, CrownPicks.Verdict.PICK, 13, -24),
                "Chunk 17 of 25: +" + g(LAW.nextPrice(16)) + " FE/t", GREY,
                "Draw then " + g(LAW.totalDraw(17)) + " FE/t", GREY,
                "The crown is dark: it joins the queue", AMBER,
                "All 17 come back once " + g(LAW.totalDraw(17)) + " FE/t arrives", GREY);
    }

    @Test
    @DisplayName("charging: a pick is queued until the crown is charged (v11 empty)")
    void charging() {
        final CrownMapTooltip.Crown c = crown(state(0, 1, CrownHold.Phase.DARK, 35_840, 512, 20,
                CrownState.Reason.NO_POWER), studs12(), 1, true);
        says(CrownMapTooltip.body(c, CrownMapTooltip.verdict(c, 10, -24), 10, -24),
                "Chunk 2 of 25: +1,126 FE/t", GREY,
                "Draw then 2,150 FE/t", GREY,
                "Queued until the crown is charged", GREY,
                "Click to pick it", ROSE);
    }

    @Test
    @DisplayName("a paint is priced as one (v11 paint)")
    void paint() {
        final List<Long> p = List.of(CrownRegion.pack(12, -25), CrownRegion.pack(12, -24), CrownRegion.pack(12, -23));
        says(CrownMapTooltip.paint(MID, false, p),
                "Paint: 3 chunks", WHITE,
                "Chunks 13 to 15 of 25: +10,637 FE/t", GREY,
                "Draw then 32,535 FE/t", GREY,
                "Release to hold them", ROSE);
    }

    @Test
    @DisplayName("a paint past the cap is priced up to it, then says where it stops")
    void paintPastTheCap() {
        final CrownMapTooltip.Crown c = crown(fed(23, 42), studs12(), 23, true);
        final List<Long> p = List.of(CrownRegion.pack(13, -20), CrownRegion.pack(14, -20), CrownRegion.pack(15, -20));
        says(CrownMapTooltip.paint(c, false, p),
                "Paint: 3 chunks", WHITE,
                "Chunks 24 to 25 of 25: +" + g(LAW.totalDraw(25) - LAW.totalDraw(23)) + " FE/t", GREY,
                "Draw then 100,707 FE/t", GREY,
                "This crown holds 25, its most", RED,
                "Release to hold the first 2", ROSE);
    }

    @Test
    @DisplayName("a paint that lets go: how many, the draw after, what it saves")
    void paintDrop() {
        final List<Long> p = List.of(CrownRegion.pack(11, -24), CrownRegion.pack(11, -23));
        says(CrownMapTooltip.paint(MID, true, p),
                "Let go: 2 chunks", WHITE,
                "Draw then " + g(LAW.totalDraw(10)) + " FE/t, saves " + g(LAW.totalDraw(12) - LAW.totalDraw(10)), GREY,
                "Release to let them go", ROSE);
    }

    @Test
    @DisplayName("every one of the twelve verdicts has words, a refusal its problem first and its way out second")
    void everyVerdict() {
        assertEquals(List.of("PICK", "DROP", "NO_OWNER", "NOT_OWNER", "STATION_DARK_FOR_GOOD", "OUTSIDE_REGION",
                        "SABLE_PLOT", "OWN_CHUNK", "STATION_FULL", "PLAYER_FULL", "BOARD_CARRY", "BOARD_SUPPLY"),
                Arrays.stream(CrownPicks.Verdict.values()).map(Enum::name).toList(),
                "ordinals are the reply packet's contract: never reordered, new ones on the end");
        for (CrownPicks.Verdict v : CrownPicks.Verdict.values()) {
            final List<CrownMapTooltip.Line> lines = CrownMapTooltip.body(MID, v, 10, -23);
            assertTrue(lines.size() >= 2, v + ": " + texts(lines));
            if (!v.changes()) {
                assertTrue(lines.get(0).colour() == RED || lines.get(0).colour() == WHITE, v + " opens on its problem");

                assertEquals(GREY, lines.get(lines.size() - 1).colour(), v + " ends on its way out");
            }
            texts(lines).forEach(t -> assertFalse(t.isBlank() || t.contains("%"), v + ": " + t));
        }
    }

    @Test
    @DisplayName("the refusals the mock has no state for say whose, where and when")
    void otherRefusals() {
        final CrownMapTooltip.Crown other = crown(fed(12, 31), studs12(), 12, false);
        assertEquals(CrownPicks.Verdict.NOT_OWNER, CrownMapTooltip.verdict(other, 12, -22));
        says(CrownMapTooltip.body(other, CrownPicks.Verdict.NOT_OWNER, 12, -22),
                "Bolat's crown", WHITE, "Only its owner can pick or drop here", GREY);
        final CrownState taken = new CrownState(0, 1, 0, 0, CrownHold.Phase.DARK, 0f, 0, 0, 0, 1024, 0, 1126, 1, 25, 50,
                3, 65_536, 65_536, 0, 0, CrownState.Reason.REGION_TAKEN,
                net.minecraft.core.BlockPos.asLong(100, 64, -200), false, 0, 0, 0, "", 0, -1);
        final CrownMapTooltip.Crown t = crown(taken, studs12(), 1, true);
        assertEquals(CrownPicks.Verdict.STATION_DARK_FOR_GOOD, CrownMapTooltip.verdict(t, 12, -22));
        says(CrownMapTooltip.body(t, CrownPicks.Verdict.STATION_DARK_FOR_GOOD, 12, -22),
                "Another crown holds this region", RED, "It stands at 100 64 -200: break one of the two", GREY);
        final CrownMapTooltip.Crown supply = crown(fed(12, 31), board(0, 1, true, true, 131_072, 900, 900), 12, true);
        assertEquals(CrownPicks.Verdict.BOARD_SUPPLY, CrownMapTooltip.verdict(supply, 12, -22));
        assertEquals("Its PDU's supply runs short in 45 s",
                text(CrownMapTooltip.body(supply, CrownPicks.Verdict.BOARD_SUPPLY, 12, -22).get(0)));
        final CrownMapTooltip.Crown off = crown(state(0, 3, CrownHold.Phase.DARK, 0, 0, 22,
                CrownState.Reason.SWITCHED_OFF), studs12(), 3, true);
        assertEquals(CrownPicks.Verdict.PICK, CrownMapTooltip.verdict(off, 12, -22),
                "a force off at a board still lets the owner pick, as togglePick does");
    }

    @Test
    @DisplayName("the reply: a word only when the server stopped short, and how far it got")
    void reply() {
        final CrownMapTooltip.Crown cap = crown(fed(25, 44), studs12(), 25, true);
        final long at = CrownRegion.pack(14, -20);
        assertEquals("2 of 3 held: This crown holds 25, its most", text(CrownMapTooltip.reply(cap, false, 3,
                List.of(CrownPicks.Verdict.PICK, CrownPicks.Verdict.PICK, CrownPicks.Verdict.STATION_FULL), at)));
        assertEquals("This crown holds 25, its most", text(CrownMapTooltip.reply(cap, false, 1,
                List.of(CrownPicks.Verdict.STATION_FULL), at)));
        assertNull(CrownMapTooltip.reply(cap, false, 2, List.of(CrownPicks.Verdict.PICK, CrownPicks.Verdict.PICK), at));
        assertNotNull(CrownMapTooltip.reply(cap, true, 1, List.of(CrownPicks.Verdict.OWN_CHUNK),
                CrownRegion.pack(9, -24)));
    }

    @Test
    @DisplayName("the client's judge is CrownPicks.toggle with the board's carry and outlook, not a copy of it")
    void sameJudge() {
        final CrownBoard b = board(2, 1, true, true, 98_304, 0, -1);
        final CrownMapTooltip.Crown c = crown(fed(24, 43), b, 24, true);
        final CrownPicks.Verdict server = CrownPicks.toggle(new CrownPicks.Request(true, true, true, 9, -24, 12, -21,
                false, 24, 43, b.pickCarry(), b.pickShortIn()), LAW);
        assertEquals(server, CrownMapTooltip.verdict(c, 12, -21));
    }
}
