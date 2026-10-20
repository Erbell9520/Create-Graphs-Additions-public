package com.createstats.client;

import com.createstats.client.ponder.fe.FeLayout;
import com.createstats.power.ponder.FePonderChapters;
import com.createstats.power.ponder.FePonderChapters.Cue;
import com.createstats.power.ponder.FePonderChapters.Sheet;
import com.createstats.power.ponder.FePonderPlant.Recording;
import com.createstats.power.ponder.FePonderPlant.Tuning;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FePonderLangTest {
    private static final Path SCENES = Path.of("src/main/java/com/createstats/client/FeScenes.java");
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");

    static final Pattern STAGE = Pattern.compile("\\bstage\\(builder,\\s*\"([a-z0-9_]+)\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\",");

    static final Pattern TEXT = Pattern.compile("\\.text\\(\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"\\s*([,)+])|[^\"\\s])");

    static final Pattern CHAPTER = Pattern.compile("public static void \\w+\\(SceneBuilder builder, SceneBuildingUtil util\\)");

    static final Pattern SHOW = Pattern.compile(
            "showText\\(\\s*sheet\\.get\\(\"([a-z_]+)\"\\)\\.hold\\(\\)\\)((?:[^;\"]|\"(?:[^\"\\\\]|\\\\.)*\")*);");

    record Scene(String id, String title, String body, List<String> captions) {
    }

    static List<Scene> scenes(String source) {
        final List<Scene> out = new ArrayList<>();
        final Matcher s = STAGE.matcher(source);
        final List<int[]> spans = new ArrayList<>();
        final List<String[]> heads = new ArrayList<>();
        while (s.find()) {
            spans.add(new int[] {s.end(), source.length()});
            heads.add(new String[] {s.group(1), s.group(2)});
            if (spans.size() > 1) spans.get(spans.size() - 2)[1] = s.start();
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
            out.add(new Scene(heads.get(i)[0], heads.get(i)[1], body, captions));
        }
        return out;
    }

    @Test
    void everyCaptionIsInTheLangFileInOrder() throws IOException {
        final String source = Files.readString(SCENES, StandardCharsets.UTF_8).replace("\r\n", "\n");
        final JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        final List<Scene> scenes = scenes(source);
        assertFalse(scenes.isEmpty(), "found no scenes: has stage(builder, id, title, plate) been renamed? "
                + "This test then checks nothing.");
        int chapters = 0;
        final Matcher m = CHAPTER.matcher(source);
        while (m.find()) chapters++;
        assertEquals(chapters, scenes.size(), "a chapter opens without a stage call this scan recognises");
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
                assertFalse(caption.contains("\u2014"), key + " has an em dash");
            }
            assertFalse(lang.has(prefix + "text_" + (scene.captions().size() + 1)),
                    scene.id() + " has more captions in the lang file than in the scene: one was removed or merged");
        }
    }

    @Test
    void everyCaptionIsItsSheetsCueWithItsKeyframe() throws IOException {
        final String source = Files.readString(SCENES, StandardCharsets.UTF_8).replace("\r\n", "\n");
        final Tuning tune = Tuning.defaults();
        final Recording pdu = FePonderChapters.Pdu.replay(tune);
        final Recording auto = FePonderChapters.Auto.replay(tune);
        final Map<String, Sheet> sheets = Map.of(
                "fe_cables", FePonderChapters.Cables.sheet(),
                "fe_pdu", FePonderChapters.Pdu.sheet(pdu, FePonderChapters.Pdu.warp(pdu)),
                "fe_pdu_auto", FePonderChapters.Auto.sheet(auto, FePonderChapters.Auto.warp(auto)),
                "fe_wires", FePonderChapters.Spans.sheet(),
                "fe_capacitor_bank", FePonderChapters.Bank.sheet(FePonderChapters.Bank.replay(tune,
                        FePonderChapters.Bank.Stock.defaults())),
                "fe_battery_controller", FePonderChapters.Controller.sheet(FePonderChapters.Controller.replay(tune,
                        FePonderChapters.Controller.Stock.defaults())),
                "fe_board_and_reserve", FePonderChapters.Deal.sheet(FePonderChapters.Deal.replay(tune,
                        FePonderChapters.Controller.Stock.defaults(), 1L, 2L, 3L)));
        final Map<String, Map<String, net.minecraft.world.phys.Vec3>> targets = Map.of(
                "fe_cables", FeLayout.cables(), "fe_pdu", FeLayout.pdu(), "fe_pdu_auto", Map.<String, net.minecraft.world.phys.Vec3>of(),
                "fe_wires", FeLayout.wires(), "fe_capacitor_bank", FeLayout.bank(),
                "fe_battery_controller", FeLayout.controller(), "fe_board_and_reserve", FeLayout.yard());
        final List<Scene> scenes = scenes(source);
        assertEquals(sheets.size(), scenes.size(), "a chapter without a sheet, or a sheet without a chapter");
        for (Scene scene : scenes) {
            final Sheet sheet = sheets.get(scene.id());
            assertNotNull(sheet, scene.id() + " has no cue sheet here");
            final List<Cue> cues = sheet.captions();
            final Matcher m = SHOW.matcher(scene.body());
            int i = 0;
            while (m.find()) {
                assertTrue(i < cues.size(), scene.id() + " shows more captions than its sheet has");
                final Cue cue = cues.get(i++);
                assertEquals(cue.name(), m.group(1), scene.id() + " caption " + i + " is not its sheet's");
                assertEquals(cue.keyframe(), m.group(2).contains(".attachKeyFrame()"), scene.id() + " " + cue.name()
                        + ": the scene's keyframe is not the sheet's");

                final boolean pointed = m.group(2).contains(".pointAt(aim.get(\"" + cue.name() + "\"))");
                final boolean free = m.group(2).contains(".independent(FeLayout.UPPER)");
                assertTrue(pointed != free, scene.id() + " " + cue.name()
                        + " neither points at its FeLayout target nor stands free at FeLayout.UPPER");
                assertEquals(pointed, targets.get(scene.id()).containsKey(cue.name()), scene.id() + " " + cue.name()
                        + (pointed ? " points at a target FeLayout does not have" : " stands free but FeLayout has a target for it"));
            }
            assertEquals(cues.size(), i, scene.id() + " shows fewer captions than its sheet has");
            int shown = 0;
            for (int at = scene.body().indexOf("showText("); at >= 0; at = scene.body().indexOf("showText(", at + 1)) shown++;
            assertEquals(i, shown, scene.id() + " shows a caption its sheet does not time");
        }
    }
}
