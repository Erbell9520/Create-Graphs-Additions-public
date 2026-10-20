package com.createstats.client.ponder.seismic;

import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicRig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicPonderRigTest {
    private static final Path SEISMIC = Path.of("src/main/java/com/createstats/seismic/client");

    @Test
    void theSetupRigWaitsThenHoistsThenIsReadyAtTheTripDogs() {
        final RigScript hero = SeismicPonderBeats.Setup.hero();
        final int release = SeismicPonderBeats.Setup.RELEASE;
        final int feed = SeismicPonderBeats.Setup.FEED;
        assertEquals(SeismicRig.State.NO_TARGET, hero.at(release - 1).state(), "untuned until the tuner is let go");
        assertEquals(SeismicRig.State.WAITING, hero.at(release).state(), "tuned with no FE: amber, blinking");
        assertEquals(SeismicRig.State.WAITING, hero.at(feed).state(), "FE is counted the tick after it arrives");
        assertEquals(SeismicRig.State.CHARGING, hero.at(feed + 1).state());
        final int ready = SeismicPonderBeats.Setup.ready();

        final int ticks = (int) Math.ceil(SeismicCharge.burstFor(64) / (double) SeismicCharge.WINCH_FE_PER_TICK);
        assertEquals(feed + ticks - 1, ready, "ready the tick the last FE arrives");
        assertEquals(SeismicCharge.burstFor(64), hero.at(ready).stored());
        assertEquals(SeismicCharge.burstFor(64), hero.at(SeismicPonderBeats.Setup.LENGTH - 1).stored(), "never past the tick");
        for (int t = 0; t < SeismicPonderBeats.Setup.LENGTH; t++) {
            assertTrue(hero.at(t).arriving() <= SeismicCharge.WINCH_FE_PER_TICK, "the winch's top rate");
        }
    }

    @Test
    void theDecoyOnPlanksIsRedAllAlong() {
        final RigScript decoy = SeismicPonderBeats.Setup.decoy();
        for (int t = 0; t < SeismicPonderBeats.Setup.LENGTH; t++) {
            assertEquals(SeismicRig.State.NO_GROUND, decoy.at(t).state());
            assertEquals(SeismicRig.Lamp.RED, decoy.at(t).lamp());
        }
    }

    @Test
    void theSurveyRigFallsTheWholeMastAndLandsWithTheGroundsEchoes() {
        final SeismicPonderSurvey.Survey ground = SeismicPonderBeats.Survey.ground();
        final RigScript hero = SeismicPonderBeats.Survey.hero(ground);
        final int pull = SeismicPonderBeats.Survey.PULL;
        assertEquals(SeismicRig.State.READY, hero.at(pull - 1).state());
        assertEquals(SeismicRig.State.DROPPING, hero.at(pull).state());
        assertEquals(SeismicCharge.FULL_FALL_TICKS, hero.at(pull).fallTicks(), "a full mast falls seven ticks");
        final int impact = SeismicPonderBeats.Survey.impact();
        assertEquals(pull + SeismicCharge.FULL_FALL_TICKS, impact);
        assertEquals(SeismicRig.State.DROPPING, hero.at(impact - 1).state());
        assertEquals(SeismicCharge.MAX_RADIUS, hero.at(impact).reach());
        assertArrayEquals(ground.echoes(), hero.at(impact).echoes());
        assertEquals(0, hero.at(impact).stored(), "a drop spends all of it");
        assertEquals(SeismicRig.State.WAITING, hero.at(impact + 1).state(), "no FE after the blow: amber, blinking");
        assertEquals(SeismicPonderBeats.Reading.PULL + SeismicCharge.FULL_FALL_TICKS, SeismicPonderBeats.Reading.impact());
    }

    @Test
    void theWeightComesDownOnceAndLandsWithTheBlow() {
        final RigScript hero = SeismicPonderBeats.Survey.hero(SeismicPonderBeats.Survey.ground());
        final int pull = SeismicPonderBeats.Survey.PULL;
        final int impact = SeismicPonderBeats.Survey.impact();
        final double hop = SeismicCharge.RESTITUTION * SeismicCharge.RESTITUTION * hero.at(pull).dropHeight() + 1e-6;
        final RigLook look = new RigLook();
        look.settle(hero.at(0), 0);
        double before = Double.MAX_VALUE;
        for (int t = 1; t < impact + 20; t++) {
            final RigScript.Frame f = hero.at(t);
            look.step(f, t);
            if (t < pull - 4) {
                continue;
            }
            for (int q = 0; q < 8; q++) {
                final double pt = q / 8.0;

                final double since = t - f.dropTick();
                final boolean falling = f.dropTick() >= 0 && since >= 0 && since < f.fallTicks();
                final double bounce = f.dropTick() < 0 ? 0.0
                        : SeismicCharge.bounce(since + pt - f.fallTicks(), f.dropHeight(), f.fallTicks());
                final double drawn = falling ? f.dropHeight() * (1.0 - SeismicCharge.fallen(since + pt, f.fallTicks()))
                        : look.weightOld + (look.weight - look.weightOld) * pt + bounce;
                if (t + pt < impact) {
                    assertTrue(drawn <= before + 1e-9, "the weight goes back up at tick " + t + " + " + pt + ": " + drawn
                            + " after " + before);
                    before = drawn;
                } else {
                    assertTrue(drawn <= hop, "the weight stands " + drawn + " up at tick " + t + " + " + pt
                            + ", after the blow at " + impact + ": more than the bounce's " + hop);
                }
            }
        }
    }

    @Test
    void theDriverWritesTheWeightEveryTick() throws IOException {
        final String driver = Files.readString(Path.of("src/main/java/com/createstats/client/ponder/seismic/RigDriver.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        final int head = driver.indexOf("protected void step(PonderScene scene, int tick, boolean last)");
        assertTrue(head >= 0, "RigDriver's step is gone");
        final int open = driver.indexOf('{', head);
        int depth = 0;
        int close = -1;
        for (int i = open; i < driver.length() && close < 0; i++) {
            if (driver.charAt(i) == '{') {
                depth++;
            } else if (driver.charAt(i) == '}' && --depth == 0) {
                close = i;
            }
        }
        final String step = driver.substring(open, close + 1).replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        final int branch = step.indexOf("} else { look.step(f, tick); }");
        assertTrue(branch >= 0, "the driver's first-tick branch moved: " + step);
        assertTrue(step.indexOf("be.weightShown = look.weight;", branch) > branch
                        && step.indexOf("be.weightShownOld = look.weightOld;", branch) > branch,
                "the driver must write RigLook's weight every tick, after its branch, or the rig lands a tick late");
    }

    @Test
    void theLanternBlinksOnTheScenesClock() {
        final RigLook look = new RigLook();
        final RigScript.Frame red = SeismicPonderBeats.Setup.decoy().at(0);
        look.settle(red, 0);
        float lit = 0f;
        float dark = 1f;
        for (int t = 1; t < 60; t++) {
            look.step(red, t);
            if (SeismicRig.lampLit(SeismicRig.Lamp.RED, t)) {
                lit = Math.max(lit, look.glow);
            } else if (t % SeismicRig.FAULT_PERIOD > 3 * SeismicRig.FAULT_FLASH + 4) {
                dark = Math.min(dark, look.glow);
            }
        }
        assertTrue(lit > 0.9f, "the flash warms the filament: " + lit);
        assertTrue(dark < 0.1f, "between the double flash it cools: " + dark);
        assertArrayEquals(RigLook.RED, look.rgb, 0.02f, "red behind the glass");
    }

    private static String read(String file) throws IOException {
        return Files.readString(SEISMIC.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static double constant(String source, String name) {
        final Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*([-0-9.]+)f?\\s*(/\\s*([0-9.]+)f?)?\\s*;").matcher(source);
        assertTrue(m.find(), "no constant " + name + ": it moved, and the Ponder's mirror of it can no longer be checked");
        final double v = Double.parseDouble(m.group(1));
        return m.group(3) == null ? v : v / Double.parseDouble(m.group(3));
    }

    @Test
    void theClientTicksNumbersAreTheGames() throws IOException {
        final String client = read("SeismicClient.java");
        assertEquals(RigLook.EASE, constant(client, "EASE"), 1e-6);
        assertEquals(RigLook.LAMP_WARM, constant(client, "LAMP_WARM"), 1e-6);
        assertEquals(RigLook.LAMP_COOL, constant(client, "LAMP_COOL"), 1e-6);
        assertEquals(RigLook.LAMP_HUE, constant(client, "LAMP_HUE"), 1e-6);
        assertEquals(RigLook.SWAP, constant(client, "SWAP"), 1e-6);
        final Matcher dogs = Pattern.compile("be\\.tickShown \\+= \\(float\\) \\(\\(SeismicCharge\\.heightFor\\(be\\.radius\\(\\)\\)"
                + " - be\\.tickShown\\) \\* ([0-9.]+)\\);").matcher(client);
        assertTrue(dogs.find(), "the trip dogs' easing moved");
        assertEquals(RigLook.DOGS, Double.parseDouble(dogs.group(1)), 1e-9);
    }

    @Test
    void theLanternsColoursAreTheRenderers() throws IOException {
        final String r = read("SeismicScannerRenderer.java");
        for (String[] c : new String[][] {{"RED", fmt(RigLook.RED)}, {"GREEN", fmt(RigLook.GREEN)}, {"WHITE", fmt(RigLook.WHITE)}}) {
            assertTrue(r.contains("case " + c[0] + " -> new float[] {" + c[1] + "};"), c[0] + " moved in lampColour");
        }
        assertTrue(r.contains("default -> new float[] {" + fmt(RigLook.AMBER) + "};"), "amber moved in lampColour");
    }

    private static String fmt(float[] c) {
        return c[0] + "f, " + c[1] + "f, " + c[2] + "f";
    }

    @Test
    void theRevealsNumbersAreTheGames() throws IOException {
        final String r = read("SeismicReveals.java");
        assertEquals(VeinView.WIDTH, constant(r, "WIDTH"), 1e-6);
        assertEquals(VeinView.LINE_ALPHA, constant(r, "LINE_ALPHA"), 1e-6);
        assertEquals(VeinView.FLASH_WHITE, constant(r, "FLASH_WHITE"), 1e-6);
        assertEquals(VeinView.MAX_LABELS, constant(r, "MAX_LABELS"), 1e-6);
        assertEquals(SeismicPonderBeats.DWELL_TICKS, constant(r, "DWELL_TICKS"), 1e-6);
    }
}
