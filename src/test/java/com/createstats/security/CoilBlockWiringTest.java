package com.createstats.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilBlockWiringTest {
    private static final Path BE = Path.of("src/main/java/com/createstats/blockentity/StormCoilBE.java");
    private static final Path RENDERER = Path.of("src/main/java/com/createstats/renderer/StormCoilRenderer.java");

    private static String code(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String body(String source, String marker) {
        final int at = source.indexOf(marker);
        assertTrue(at >= 0, "not found, so this test now guards nothing: " + marker);
        final int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open + 1, i);
            }
        }
        throw new IllegalStateException("unbalanced braces after " + marker);
    }

    @Test
    void theCoilIsASecurityDevice() throws IOException {
        final String be = code(BE);
        final int decl = be.indexOf("public class StormCoilBE");
        final String head = be.substring(decl, be.indexOf('{', decl));
        assertTrue(head.contains("SecurityDevice") && head.contains("CoilDevice"),
                "no panel can find, govern or repoll a coil that is not one: " + head);
    }

    @Test
    void everyVerdictIsThePolicys() throws IOException {
        final String be = code(BE);
        assertFalse(be.contains("CoilTargets.allows("), "a verdict outside the policy: the house's grid never sees it");
        assertTrue(body(be, "private List<LivingEntity> pickVolley(").contains("policy.allows(describe(e, now), personOf(e))"));
        final String fire = body(be, "private void fireVolley(long now)");
        assertTrue(fire.contains("policy.allows(describe(e, now), personOf(e))"), "the fire's re-check: " + fire);
        assertTrue(fire.contains("heldFromArcing()") && fire.contains("policy.arcsAtAnything()"), fire);
        final String aim = body(be, "private void aimVolley(long now)");
        assertTrue(aim.contains("heldFromArcing()") && aim.contains("policy.arcsAtAnything()"), aim);
        final String look = body(be, "private void look(long now)");
        assertTrue(look.contains("CoilWatch.pick(policy,") && look.contains("personOf(e)"), look);
    }

    @Test
    void heldAndZapsAreTheHouses() throws IOException {
        final String be = code(BE);
        assertTrue(body(be, "public boolean heldFromArcing()").contains("(redstone && !deaf) || panelOff"));
        assertTrue(body(be, "private CoilStores.Terms terms()").contains("zapsAtAll()"),
                "the stores must hear the grid's zaps, or a governed coil's FE moves");
    }

    @Test
    void bothBoxesAskTheGate() throws IOException {
        final String be = code(BE);
        final String targets = body(be, "private static final class TargetsBox");
        assertTrue(targets.contains("if (!coil.refuse(player, CoilGate.Box.TARGETS))")
                && targets.contains("super.setValueSettings(player, settings, ctrlDown)"), targets);
        final String height = body(be, "private static final class HeightBox");
        assertTrue(height.contains("if (!coil.refuse(player, CoilGate.Box.HEIGHT))")
                && height.contains("super.setValueSettings(player, settings, ctrlDown)"), height);
        final String behaviours = body(be, "public void addBehaviours(");
        assertTrue(behaviours.contains("new TargetsBox(this)") && behaviours.contains("new HeightBox(this)"), behaviours);
    }

    @Test
    void thePollCopiesEveryFlagAndLosingThePanelClearsThem() throws IOException {
        final String be = code(BE);
        final String poll = body(be, "private void findPolicy()");
        for (String copy : new String[] {"roster = found.rosterSnapshot();", "required = found.requiredLevel(worldPosition);",
                "panelOff = !found.coilOn(worldPosition);", "locked = found.coilLocked(worldPosition);",
                "deaf = !found.coilHearsRedstone(worldPosition);", "found.coilRules(worldPosition);",
                "houseState = found.houseState().ordinal();", "governed = true;"}) {
            assertTrue(poll.contains(copy), "the poll misses " + copy);
        }
        final String lost = body(be, "private void loseLink()");
        for (String clear : new String[] {"panelOff = false;", "locked = false;", "deaf = false;", "rules = -1;",
                "governed = false;", "panelPos = null;", "roster = null;"}) {
            assertTrue(lost.contains(clear), "losing the panel keeps " + clear + ": nothing could ever undo it");
        }
    }

    @Test
    void everyFlagIsSavedAndSynced() throws IOException {
        final String write = body(code(BE), "protected void write(CompoundTag tag");
        for (String key : new String[] {"SecGoverned", "SecOff", "SecLocked", "SecDeaf", "SecRules", "SecState",
                "SecHouse", "LastArc"}) {
            assertTrue(write.contains("tag.put") && write.contains("\"" + key + "\""), "write misses " + key);
        }
        final String saveOnly = body(write, "if (!clientPacket)");
        assertTrue(saveOnly.contains("\"SecPanel\"") && saveOnly.contains("\"SecHousehold\""),
                "the panel's place and household are the save's, never a client's");
        final String read = body(code(BE), "protected void read(CompoundTag tag");
        for (String key : new String[] {"SecGoverned", "SecOff", "SecLocked", "SecDeaf", "SecRules", "SecState",
                "SecHouse", "LastArc", "SecPanel", "SecHousehold"}) {
            assertTrue(read.contains("\"" + key + "\""), "read misses " + key);
        }
    }

    @Test
    void theClockRunsThePollAndTheFightsLine() throws IOException {
        final String be = code(BE);
        final String tick = body(be, "public void tick()");
        assertTrue(tick.contains("findPolicy();") && tick.contains("endFightIfOver(now);"), tick);
        assertTrue(body(be, "private void clientTick()").contains("com.createstats.stormcoil.client.CoilSounds.tick(this);"),
                "the main session's sound line");
        assertTrue(body(be, "private void onStrike()").contains("coilBanked("));
        assertTrue(body(be, "private void noteHit(").contains("coilArcedAt("));
    }

    @Test
    void theRendererDrawsTheLampOnlyForAGovernedCoil() throws IOException {
        final String r = code(RENDERER);
        assertTrue(body(r, "public void render(StormCoilBE be").contains("drawSecurityLamp(mesh, ps.last(), lamp, t, light);"));
        final String lamp = body(r, "private static void drawSecurityLamp(");
        assertTrue(lamp.contains("if (lamp == com.createstats.security.CoilStatus.Lamp.NONE)"), lamp);
    }
}
