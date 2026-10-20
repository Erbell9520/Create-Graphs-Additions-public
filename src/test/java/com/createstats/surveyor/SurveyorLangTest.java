package com.createstats.surveyor;

import com.createstats.surveyor.client.StaffMode;
import com.createstats.surveyor.core.Comfort;
import com.createstats.surveyor.core.Corner;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.surveyor.core.StopCondition;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyorLangTest {
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Path SRC = Path.of("src/main/java/com/createstats");
    private static final String BOARD = "gui.create_stats.pathways.";
    private static final String CONSOLE = "gui.create_stats.vcc_mk4.";
    private static final String SURVEYOR = "create_stats.surveyor.";

    private static Map<String, JsonElement> lang() throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        return json.asMap();
    }

    @Test
    void everyKeyTheSurveyorAsksForHasItsRow() throws IOException {
        Map<String, JsonElement> lang = lang();
        TreeSet<String> wanted = new TreeSet<>();
        for (Corner c : Corner.values()) {
            wanted.add(SURVEYOR + "corner." + c.key);
            wanted.add(BOARD + "corner." + c.key);
            wanted.add(BOARD + "corner." + c.key + ".hint");
        }
        for (Comfort c : Comfort.values()) {
            wanted.add(BOARD + "comfort." + c.key);
            wanted.add(BOARD + "comfort." + c.key + ".hint");
        }
        for (StopCondition c : List.of(new StopCondition.Wait(1), new StopCondition.Idle(1),
                new StopCondition.Items(StopCondition.Op.GREATER, 1, false, ""), new StopCondition.Fluid(StopCondition.Op.GREATER, 1, ""),
                new StopCondition.Link("", "", true), new StopCondition.TimeOfDay(8, 0, 0), new StopCondition.Players(1, true),
                new StopCondition.Powered(), new StopCondition.Docked())) {
            wanted.add(BOARD + "cond." + c.kind());
        }
        for (int r = 0; r < StopCondition.TimeOfDay.ROTATION_TICKS.length; r++) wanted.add(BOARD + "cond.time_of_day.every." + r);
        for (int i = 0; i < 16; i++) wanted.add(BOARD + "dye." + i);
        for (RouteRunner.State s : RouteRunner.State.values()) {
            wanted.add(SURVEYOR + "state." + s.key);
            wanted.add(CONSOLE + "route.state." + s.key);
        }
        for (Comfort c : Comfort.values()) wanted.add(CONSOLE + "route.comfort." + c.key);
        for (StaffMode m : StaffMode.values()) {
            wanted.add(SURVEYOR + "mode." + m.key);
            wanted.add(SURVEYOR + "hint." + m.key);
        }
        for (String s : new String[]{"paused", "manual", "finished", "no_budget", "calibrating", "lost", "stuck"}) {
            wanted.add(CONSOLE + "route.hint." + s);
        }
        wanted.add("item.create_stats.surveyor_staff");

        for (String f : new String[]{"move", "fair", "amphibious", "widen"}) {
            wanted.add(BOARD + "fix." + f);
            wanted.add(BOARD + "fix." + f + ".hint");
        }

        Pattern full = Pattern.compile("\"((?:gui\\.|item\\.)?create_stats\\.(?:surveyor|pathways|vcc_mk4\\.route|vcc_mk4\\.tab\\.route)[a-z0-9_.]*)\"");

        Pattern tr = Pattern.compile("(?<![.\\w])tr\\(\\s*(?:[^\"()]*\\?\\s*)?\"([a-z0-9_.]+)\"(?:\\s*:\\s*\"([a-z0-9_.]+)\")?");
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(SRC.resolve("surveyor"))) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        try (Stream<Path> walk = Files.walk(SRC.resolve("vccmk4/route"))) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
        }
        files.add(SRC.resolve("vccmk4/client/PathwayBoardScreen.java"));
        for (Path f : files) {
            Matcher m = full.matcher(Files.readString(f, StandardCharsets.UTF_8));
            while (m.find()) if (!m.group(1).endsWith(".")) wanted.add(m.group(1));
        }
        String board = Files.readString(SRC.resolve("vccmk4/console/PathwayBoard.java"), StandardCharsets.UTF_8);
        Matcher b = tr.matcher(board);
        while (b.find()) {
            if (!b.group(1).endsWith(".")) wanted.add(BOARD + b.group(1));
            if (b.group(2) != null) wanted.add(BOARD + b.group(2));
        }
        String console = Files.readString(SRC.resolve("vccmk4/console/Vcc4Console.java"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        int from = console.indexOf("    // Route\n");
        int to = console.indexOf("    // Lists and flow");
        assertTrue(from > 0 && to > from, "the console has its Route section");
        Matcher c = tr.matcher(console.substring(from, to));
        while (c.find()) {
            if (!c.group(1).endsWith(".")) wanted.add(CONSOLE + c.group(1));
            if (c.group(2) != null) wanted.add(CONSOLE + c.group(2));
        }

        List<String> missing = wanted.stream().filter(k -> !lang.containsKey(k)).toList();
        assertTrue(wanted.size() > 150, "the scan found only " + wanted.size() + " keys: it has stopped reading something");
        assertTrue(missing.isEmpty(), "Surveyor keys with no en_us.json row (they would print raw):\n  " + String.join("\n  ", missing));
    }

    @Test
    void noSurveyorStringHoldsADash() throws IOException {
        List<String> dashed = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : lang().entrySet()) {
            String k = e.getKey();
            boolean ours = k.startsWith(SURVEYOR) || k.startsWith(BOARD) || k.startsWith(CONSOLE + "route.")
                    || k.startsWith(CONSOLE + "tab.route") || k.startsWith("item.create_stats.surveyor_staff");
            String v = e.getValue().getAsString();
            if (ours && (v.indexOf('\u2014') >= 0 || v.indexOf('\u2013') >= 0)) dashed.add(k);
        }
        assertTrue(dashed.isEmpty(), "em or en dashes in: " + dashed);
    }
}
