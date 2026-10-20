package com.createstats.seismic.archive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyWiringTest {
    private static final String J = "src/main/java/com/createstats/";

    static List<String> statements(String rel) throws IOException {
        String src = Files.readString(Path.of(rel), StandardCharsets.UTF_8).replace("\r\n", "\n");
        src = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        src = src.replaceAll("(?m)^\\s*//.*$", " ");
        src = src.replaceAll("(?m)\\s//[^\"\\n]*$", " ");
        final List<String> out = new ArrayList<>();
        for (final String part : src.split(";")) {
            out.add(lead(part).replaceAll("\\s+", " ").trim());
        }
        return out;
    }

    static String lead(String s) {
        int depth = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            final char ch = s.charAt(i);
            if (ch == ')') {
                depth++;
            } else if (ch == '(') {
                depth--;
            } else if ((ch == '{' || ch == '}') && depth == 0) {
                return s.substring(i + 1);
            }
        }
        return s;
    }

    static int indexOf(List<String> st, String statement) {
        for (int i = 0; i < st.size(); i++) {
            if (st.get(i).equals(statement)) {
                return i;
            }
        }
        return -1;
    }

    private static void has(List<String> problems, String file, List<String> st, String statement) {
        if (indexOf(st, statement) < 0) {
            problems.add(file + ": missing `" + statement + ";`");
        }
    }

    private static void follows(List<String> problems, String file, List<String> st, String after, String statement) {
        final int a = indexOf(st, after);
        final int b = indexOf(st, statement);
        if (a < 0 || b != a + 1) {
            problems.add(file + ": `" + statement + ";` must come straight after `" + after + ";` (found at "
                    + b + ", after at " + a + ")");
        }
    }

    private static void precedes(List<String> problems, String file, List<String> st, String statement, String before) {
        final int a = indexOf(st, statement);
        final int b = indexOf(st, before);
        if (a < 0 || b < 0 || a > b) {
            problems.add(file + ": `" + statement + ";` must come before `" + before + ";`");
        }
    }

    @Test
    void theRigWritesTheRecordAsItsSurveyRuns() throws IOException {
        final List<String> problems = new ArrayList<>();
        final String f = J + "blockentity/SeismicScannerBE.java";
        final List<String> st = statements(f);
        follows(problems, f, st, "survey = new SeismicSurvey(server, worldPosition, t, reach)",
                "com.createstats.seismic.archive.SeismicArchiveHook.begin(server, worldPosition, t.id(), reach, now)");
        follows(problems, f, st, "final List<VeinBook.Vein> closed = survey.step()",
                "com.createstats.seismic.archive.SeismicArchiveHook.step(server, worldPosition, survey, closed)");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void thePanelKeepsTicksSavesAndLoadsItsRigs() throws IOException {
        final List<String> problems = new ArrayList<>();
        final String f = J + "blockentity/StatsDisplayPanelBE.java";
        final List<String> st = statements(f);
        has(problems, f, st, "private final SeismicLinks seismicLinks = new SeismicLinks(this)");
        has(problems, f, st, "return this.seismicLinks");

        follows(problems, f, st, "this.groundLinks.serverTick(serverLevel, serverLevel.getGameTime())",
                "this.seismicLinks.serverTick(serverLevel, serverLevel.getGameTime())");
        follows(problems, f, st, "this.groundLinks.save(tag, false)", "this.seismicLinks.save(tag, false)");
        follows(problems, f, st, "this.groundLinks.save(tag, true)", "this.seismicLinks.save(tag, true)");
        follows(problems, f, st, "this.groundLinks.load(tag, this.level != null && this.level.isClientSide())",
                "this.seismicLinks.load(tag, this.level != null && this.level.isClientSide())");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theDisplayLinkIsTheHeartbeat() throws IOException {
        final List<String> problems = new ArrayList<>();
        final String f = J + "display/StatsDisplayTarget.java";
        final List<String> st = statements(f);
        follows(problems, f, st, "panelTarget.groundLinks().remove(sourcePos)",
                "panelTarget.seismicLinks().remove(sourcePos)");

        precedes(problems, f, st, "panelTarget.seismicLinks().heartbeat(seismicRig.getBlockPos(), gameTick)",
                "panelTarget.setSourceType(BattleCompactTargetHandler.SOURCE_TYPE)");
        has(problems, f, st, "return com.createstats.blockentity.SeismicLinks.SOURCE_TYPE");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theRegistriesNameEveryPart() throws IOException {
        final List<String> problems = new ArrayList<>();
        final String packets = J + "network/ModPackets.java";
        final List<String> p = statements(packets);
        has(problems, packets, p,
                "registrar.playToClient(SeismicScenePacket.TYPE, SeismicScenePacket.STREAM_CODEC, SeismicScenePacket::handle)");
        has(problems, packets, p,
                "registrar.playToServer(SeismicPanelPacket.TYPE, SeismicPanelPacket.STREAM_CODEC, SeismicPanelPacket::handle)");

        final Matcher v = Pattern.compile("\\.versioned\\(\"(\\d+)\"\\)").matcher(String.join(";", p));
        if (!v.find() || Integer.parseInt(v.group(1)) < 58) {
            problems.add(packets + ": the protocol must be 58 or later with seismic_scene and seismic_panel");
        }
        final String main = J + "CreateStats.java";
        final List<String> m = statements(main);
        has(problems, main, m, "SEISMIC_SOURCE = CreateStats.DISPLAY_SOURCES.register(\"seismic_scanner\", "
                + "com.createstats.display.SeismicDisplaySource::new)");
        has(problems, main, m,
                "this.registerDisplaySourceMapping(CreateStats.SEISMIC_SOURCE.get(), ModBlocks.SEISMIC_SCANNER)");
        final String catalog = J + "display/CustomScreenSourceFamilyCatalog.java";
        boolean family = false;
        for (final String s : statements(catalog)) {
            family |= s.startsWith("register(def(\"survey.seismic\"")
                    && s.contains("com.createstats.blockentity.SeismicLinks.SOURCE_TYPE");
        }
        if (!family) {
            problems.add(catalog + ": no survey.seismic family filed under SeismicLinks.SOURCE_TYPE");
        }
        final String pages = J + "statsmk3/page/Mk3PageRegistry.java";
        has(problems, pages, statements(pages), "register(new SurveyPage())");
        final String lang = "src/main/resources/assets/create_stats/lang/en_us.json";
        if (!Files.readString(Path.of(lang), StandardCharsets.UTF_8)
                .contains("\"create_stats.display_source.seismic_scanner\":")) {
            problems.add(lang + ": no name for the Seismic Survey display source");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    static String code(String rel) throws IOException {
        String src = Files.readString(Path.of(rel), StandardCharsets.UTF_8).replace("\r\n", "\n");
        src = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        src = src.replaceAll("(?m)^\\s*//.*$", " ");
        return src.replaceAll("(?m)\\s//[^\"\\n]*$", " ");
    }

    static List<int[]> workerSpans(String src) {
        final List<int[]> out = new ArrayList<>();
        int at = src.indexOf("WORKER.submit(");
        while (at >= 0) {
            final int open = at + "WORKER.submit".length();
            int depth = 0;
            int end = open;
            for (int i = open; i < src.length(); i++) {
                final char c = src.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            out.add(new int[] {open, end});
            at = src.indexOf("WORKER.submit(", end);
        }
        return out;
    }

    @Test
    void scenesAndChannelsAreWorkedOffTheTick() throws IOException {
        final List<String> problems = new ArrayList<>();
        final String f = J + "blockentity/SeismicLinks.java";
        final String src = code(f);
        final List<int[]> spans = workerSpans(src);
        if (spans.size() < 2) {
            problems.add(f + ": the scene and the channels each need their own WORKER.submit, found " + spans.size());
        }
        for (final String call : new String[] {"SceneBuilder.build(", "SceneCodec.encode(", "SurveyChannels.compute("}) {
            int at = src.indexOf(call);
            if (at < 0) {
                problems.add(f + ": no " + call + " at all");
            }
            while (at >= 0) {
                boolean inside = false;
                for (final int[] s : spans) {
                    inside |= at > s[0] && at < s[1];
                }
                if (!inside) {
                    problems.add(f + ": " + call + " on the server thread, outside WORKER.submit");
                }
                at = src.indexOf(call, at + 1);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theWholeSceneGoesOnlyWithinReach() throws IOException {
        final List<String> problems = new ArrayList<>();
        final String p = J + "network/SeismicPanelPacket.java";
        final String ps = code(p).replaceAll("\\s+", " ");
        final int gate = ps.indexOf("if (!controller.isPlayerInInteractionRange(player)) { return; }");
        final int page = ps.indexOf("controller.seismicLinks().onRequest(player, packet.arg(), true)");
        if (gate < 0 || page < 0 || page < gate) {
            problems.add(p + ": the page's ask must be answered only after the reach gate");
        }
        final String l = J + "blockentity/SeismicLinks.java";
        final String ls = code(l).replaceAll("\\s+", " ");
        final int far = ls.indexOf("if (page && !near) { return; }");
        final int face = ls.indexOf("if (!page && faceSpec() == 0L) { return; }");
        final int watch = ls.indexOf("this.watchers.put(player.getUUID()");
        if (far < 0 || face < 0 || watch < 0 || far > watch || face > watch) {
            problems.add(l + ": onRequest must refuse the page from afar and a face's ask with no survey face up, "
                    + "before it registers a watcher");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theFacesResolveAndDrawTheSurvey() throws IOException {
        final List<String> problems = new ArrayList<>();
        final String settings = J + "statsmk3/Mk3Settings.java";
        final List<String> s = statements(settings);
        has(problems, settings, s, "this.survey.save(t)");
        has(problems, settings, s, "this.survey.load(t)");
        final String cache = J + "statsmk3/paint/PanelModelCache.java";
        final List<String> c = statements(cache);
        has(problems, cache, c, "controller.seismicLinks().requestIfNeeded(tick)");
        has(problems, cache, c,
                "this.model.survey = com.createstats.statsmk3.page.SurveyArt.modelOf(controller, settings, tick)");
        final String painter = J + "statsmk3/paint/PanelPainter.java";
        has(problems, painter, statements(painter), "SurveyFacePainter.paint(s, area, model.survey, "
                + "SurveyFacePainter.modeOf(model.faceMode), lod, style.ground)");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
