package com.createstats.client;

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

class AerialPonderLangTest {
    private static final Path SCENES = Path.of("src/main/java/com/createstats/client/AerialScenes.java");
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");

    private static final Pattern STAGE = Pattern.compile("\\b(?:stage|tallStage)\\(builder,\\s*\"([a-z0-9_]+)\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\)");

    private static final Pattern TEXT = Pattern.compile("\\.text\\(\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"\\s*([,)+])|[^\"\\s])");

    record Scene(String id, String title, List<String> captions) {
    }

    static List<Scene> scenes(String source) {
        List<Scene> out = new ArrayList<>();
        Matcher s = STAGE.matcher(source);
        List<int[]> spans = new ArrayList<>();
        List<String[]> heads = new ArrayList<>();
        while (s.find()) {
            spans.add(new int[] {s.end(), source.length()});
            heads.add(new String[] {s.group(1), s.group(2)});
            if (spans.size() > 1) spans.get(spans.size() - 2)[1] = s.start();
        }
        for (int i = 0; i < spans.size(); i++) {
            String body = source.substring(spans.get(i)[0], spans.get(i)[1]);
            List<String> captions = new ArrayList<>();
            Matcher t = TEXT.matcher(body);
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
        String source = Files.readString(SCENES, StandardCharsets.UTF_8).replace("\r\n", "\n");
        JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        List<Scene> scenes = scenes(source);
        assertFalse(scenes.isEmpty(), "found no scenes: has stage(builder, id, title) been renamed? This test then checks nothing.");

        int chapters = 0;
        Matcher m = Pattern.compile("public static void \\w+\\(SceneBuilder builder, SceneBuildingUtil util\\)").matcher(source);
        while (m.find()) chapters++;
        assertEquals(chapters, scenes.size(), "a chapter opens without a stage call this scan recognises");
        for (Scene scene : scenes) {
            String prefix = "create_stats.ponder." + scene.id() + ".";
            assertTrue(lang.has(prefix + "header"), "no header for " + scene.id());
            assertEquals(scene.title(), lang.get(prefix + "header").getAsString(), scene.id() + " header");
            assertFalse(scene.captions().isEmpty(), scene.id() + " has no captions");
            for (int i = 0; i < scene.captions().size(); i++) {
                String key = prefix + "text_" + (i + 1);
                String caption = scene.captions().get(i);
                assertTrue(lang.has(key), "missing " + key + ": \"" + caption + "\"");
                assertEquals(caption, lang.get(key).getAsString(), key);
                assertTrue(caption.length() < 90, key + " is " + caption.length() + " characters; the standard keeps them under 90");
                assertFalse(caption.contains("\u2014"), key + " has an em dash");
            }
            assertFalse(lang.has(prefix + "text_" + (scene.captions().size() + 1)),
                    scene.id() + " has more captions in the lang file than in the scene: one was removed or merged");
        }
    }
}
