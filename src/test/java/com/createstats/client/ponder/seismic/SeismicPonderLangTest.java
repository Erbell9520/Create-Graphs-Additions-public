package com.createstats.client.ponder.seismic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicPonderLangTest {
    private static final Path SCENES = Path.of("src/main/java/com/createstats/client/ponder/seismic/SeismicScenes.java");
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");

    private static final Pattern STAGE = Pattern.compile("\\bstage\\(builder,\\s*\"([a-z0-9_]+)\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final Pattern TEXT = Pattern.compile("\\.text\\(\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"\\s*([,)+])|[^\"\\s])");

    private static final Pattern UI = Pattern.compile("\\bui\\(\"([a-z_]+)\"");

    record Scene(String id, String title, List<String> captions) {
    }

    static List<Scene> scenes(String source) {
        final List<Scene> out = new ArrayList<>();
        final Matcher s = STAGE.matcher(source);
        final List<int[]> spans = new ArrayList<>();
        final List<String[]> heads = new ArrayList<>();
        while (s.find()) {
            spans.add(new int[] {s.end(), source.length()});
            heads.add(new String[] {s.group(1), s.group(2)});
            if (spans.size() > 1) {
                spans.get(spans.size() - 2)[1] = s.start();
            }
        }
        for (int i = 0; i < spans.size(); i++) {
            final String body = source.substring(spans.get(i)[0], spans.get(i)[1]);
            final List<String> captions = new ArrayList<>();
            final Matcher t = TEXT.matcher(body);
            while (t.find()) {
                if (t.group(2) == null) {
                    throw new AssertionError(heads.get(i)[0] + ": a caption that is not a string literal: "
                            + body.substring(t.start(), Math.min(body.length(), t.start() + 60)));
                }
                if ("+".equals(t.group(3))) {
                    throw new AssertionError(heads.get(i)[0] + ": a caption split with +: \"" + t.group(2) + "\"");
                }
                captions.add(t.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
            }
            out.add(new Scene(heads.get(i)[0], heads.get(i)[1], captions));
        }
        return out;
    }

    @Test
    void everyCaptionIsInTheLangFileInOrder() throws IOException {
        final String source = Files.readString(SCENES, StandardCharsets.UTF_8).replace("\r\n", "\n");
        final JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        final List<Scene> scenes = scenes(source);
        int methods = 0;
        final Matcher m = Pattern.compile("public static void \\w+\\(SceneBuilder builder, SceneBuildingUtil util\\)").matcher(source);
        while (m.find()) {
            methods++;
        }
        assertEquals(3, methods, "three scenes: setup, survey, reading");
        assertEquals(methods, scenes.size(), "a scene opens without a stage call this scan recognises");
        for (Scene scene : scenes) {
            final String prefix = "create_stats.ponder." + scene.id() + ".";
            assertTrue(lang.has(prefix + "header"), "no header for " + scene.id());
            assertEquals(scene.title(), lang.get(prefix + "header").getAsString(), scene.id() + " header");
            assertFalse(scene.captions().isEmpty(), scene.id() + " has no captions");
            for (int i = 0; i < scene.captions().size(); i++) {
                final String key = prefix + "text_" + (i + 1);
                final String caption = scene.captions().get(i);
                assertTrue(lang.has(key), "missing " + key + ": \"" + caption + "\"");
                assertEquals(caption, lang.get(key).getAsString(), key);
                assertTrue(caption.length() < 90, key + " is " + caption.length() + " characters; the standard keeps them under 90");
                assertFalse(caption.contains("\u2014") || caption.contains("\u2013"), key + " has a dash");
            }
            assertFalse(lang.has(prefix + "text_" + (scene.captions().size() + 1)),
                    scene.id() + " has more captions in the lang file than in the scene: one was removed or merged");
        }
    }

    @Test
    void theCaptionCountsMatchTheCueSheets() throws IOException {
        final String source = Files.readString(SCENES, StandardCharsets.UTF_8).replace("\r\n", "\n");
        final List<Scene> scenes = scenes(source);
        assertEquals(SeismicPonderBeats.Setup.cues().size(), scenes.get(0).captions().size(), "scene 1");
        assertEquals(SeismicPonderBeats.Survey.cues().size(), scenes.get(1).captions().size(), "scene 2");
        assertEquals(SeismicPonderBeats.Reading.cues().size(), scenes.get(2).captions().size(), "scene 3");
    }

    private static final Pattern CUE = Pattern.compile("\\bcue\\(cues,\\s*\"([a-z_]+)\"\\)");

    @Test
    void theScenesShowTheirCuesInTheCueSheetsOrder() throws IOException {
        final String source = Files.readString(SCENES, StandardCharsets.UTF_8).replace("\r\n", "\n");
        final Matcher s = STAGE.matcher(source);
        final List<Integer> starts = new ArrayList<>();
        while (s.find()) {
            starts.add(s.end());
        }
        assertEquals(3, starts.size(), "three scenes");
        final List<List<SeismicPonderBeats.Cue>> sheets = List.of(SeismicPonderBeats.Setup.cues(),
                SeismicPonderBeats.Survey.cues(), SeismicPonderBeats.Reading.cues());
        for (int i = 0; i < 3; i++) {
            final String body = source.substring(starts.get(i), i + 1 < 3 ? starts.get(i + 1) : source.length());
            final List<String> shown = new ArrayList<>();
            final Matcher m = CUE.matcher(body);
            while (m.find()) {
                shown.add(m.group(1));
            }
            final List<String> sheet = sheets.get(i).stream().map(SeismicPonderBeats.Cue::name).toList();
            assertEquals(sheet, shown, "scene " + (i + 1) + " shows its cues out of its cue sheet's order");
        }
    }

    @Test
    void everyOverlayWordIsInTheLangFile() throws IOException {
        final String source = Files.readString(SCENES, StandardCharsets.UTF_8).replace("\r\n", "\n");
        final JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        final Matcher m = UI.matcher(source);
        int n = 0;
        while (m.find()) {
            n++;
            assertTrue(lang.has("create_stats.ponder.seismic." + m.group(1)), "missing overlay word " + m.group(1));
        }
        assertTrue(n >= 3, "the scenes look up the recorder, the slow-motion note and the name tag");
    }
}
