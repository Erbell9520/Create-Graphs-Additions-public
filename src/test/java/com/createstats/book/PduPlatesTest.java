package com.createstats.book;

import com.createstats.book.content.ControlSheets;
import com.createstats.book.content.Part;
import com.createstats.book.layout.BookGeometry;
import com.createstats.book.layout.BookPages;
import com.createstats.book.layout.BookPainter;
import com.createstats.book.layout.BookText;
import com.createstats.book.layout.Callouts;
import com.createstats.book.layout.Ctx;
import com.createstats.book.layout.Page;
import com.createstats.book.specimen.PduPlates;
import com.createstats.book.specimen.SpecimenAnatomy;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import com.createstats.power.PduReason;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduPlatesTest {
    @Test
    void theRecordIsWhatTheScreenDrawsNow() throws IOException {
        final String now = PduPlates.json(PduRig.record());

        final java.nio.file.Path record = java.nio.file.Path.of("src/main/resources" + PduPlates.RECORD);
        assertTrue(java.nio.file.Files.exists(record), PduPlates.RECORD + " is missing: run PduRig.main to write it");
        final String kept = java.nio.file.Files.readString(record, StandardCharsets.UTF_8);
        assertEquals(kept, now, PduPlates.RECORD + " is stale: the screen or the example moved. Run PduRig.main "
                + "(python -B book.py run com.createstats.book.PduRig) and look at the plates again");
    }

    @Test
    void everyPinReadsCleanlyAtTheScalesThePlatesAreDrawnAt() {
        for (final String id : PduPlates.IDS) {
            final SpecimenAnatomy a = PduPlates.anatomy(id);
            assertFalse(a.controls().isEmpty(), id + " has pins");
            assertEquals(0, Callouts.faultsAt(a, PduPlates.print(id), PduPlates.DRAWN),
                    id + ": a leader crosses, a mark runs into another, or a leader runs through a word");
        }
    }

    @Test
    void everyPinIsOnAPartTheScreenDrew() {
        for (final String id : PduPlates.IDS) {
            final PduPlates.Drawing d = PduPlates.drawing(id);
            final int[] drawer = d.parts().get("drawer");
            for (final SpecimenAnatomy.Hot h : PduPlates.anatomy(id).controls()) {
                final String where = id + " " + h.id();
                assertTrue(h.anchorX() >= 0 && h.anchorX() < PduPlates.W && h.anchorY() >= 0
                        && h.anchorY() < PduPlates.H, where + ": the mark is on the panel");
                assertTrue(h.x() >= 0 && h.y() >= 0 && h.x() + h.w() <= PduPlates.W && h.y() + h.h() <= PduPlates.H,
                        where + ": its part is on the panel");

                boolean inside = false;
                for (final int[] r : d.parts().values()) {
                    inside |= h.x() >= r[0] && h.y() >= r[1] && h.x() + h.w() <= r[0] + r[2]
                            && h.y() + h.h() <= r[1] + r[3];
                }
                for (final PduPlates.Text s : d.text()) {
                    inside |= h.x() == s.x() && h.y() == s.y() && h.w() == s.w() && h.h() == s.h();
                }
                assertTrue(inside, where + ": stands on nothing the screen drew");
                final boolean underDrawer = drawer != null && h.anchorX() >= drawer[0]
                        && h.anchorX() < drawer[0] + drawer[2] && h.anchorY() >= drawer[1]
                        && h.anchorY() < drawer[1] + drawer[3];
                if (underDrawer) {
                    assertTrue(h.id().startsWith("inspector") || h.id().equals("locate"),
                            where + ": its mark is under the inspector, where a reader cannot see the part");
                }
            }
        }
    }

    @Test
    void theExampleIsTheBoardsOwnDecision() {
        final PduPlates.Decision d = PduPlates.decide();
        assertTrue(PduPlates.BUFFER_PCT < com.createstats.book.content.Configs.pduRestoreSocPct(),
                "under the spare mark");
        assertEquals(List.of(PduPlates.DEVICES.get(6), PduPlates.DEVICES.get(7)), PduPlates.shed(),
                "the drive and the furnace are shed");
        for (final PduPlates.Device dev : PduPlates.shed()) {
            assertEquals(FePriority.LOW, dev.tier());
            assertEquals(PduReason.OVER_BUDGET, PduPlates.reasonOf(dev), dev.block() + " is over budget");
        }
        int optionalRunning = 0;
        for (final PduPlates.Device dev : PduPlates.DEVICES) {
            if (dev.tier() == FePriority.OPTIONAL && dev.lock() == FeLock.NONE) {
                assertTrue(PduPlates.runs(dev), dev.block() + ": an Optional light runs past the shed Low machines");
                optionalRunning++;
            }
            if (dev.lock() == FeLock.FORCE_ON) {
                assertTrue(PduPlates.runs(dev));
                assertEquals(PduReason.LOCKED_ON, PduPlates.reasonOf(dev));
            }
            if (dev.lock() == FeLock.FORCE_OFF) {
                assertFalse(PduPlates.runs(dev));
                assertEquals(PduReason.LOCKED_OFF, PduPlates.reasonOf(dev));
            }
        }
        assertTrue(optionalRunning > 0, "the walk goes on past a shed machine");

        assertTrue(d.plan().admittedDemand() <= d.runBudget());

        final CompoundTag tag = PduPlates.fixtureTag(null, true);
        assertEquals(d.plan().admitted().size(), tag.getInt("Served"));
        assertEquals(PduPlates.shed().size(), tag.getInt("Shed"));
        assertEquals(PduPlates.generation(), tag.getInt("Gen"));
        assertEquals(PduPlates.demand(), tag.getInt("Dmd"));
        assertEquals(d.plan().admittedDemand(), tag.getInt("Svd"));
    }

    @Test
    void theLegsAreTheLegScreensOwn() {
        final int[] legs = PduPlates.fixtureTag(null, true).getIntArray("Legs");
        final com.createstats.power.PduLegPlan.Screened screened = PduPlates.legScreen();
        for (int leg = 0; leg < com.createstats.blockentity.PowerDistributionUnitBE.LEGS; leg++) {
            assertEquals(screened.legDemand()[leg], legs[leg * 4 + 2], "leg " + leg + ": asked");
            assertEquals(screened.legAdmitted()[leg], legs[leg * 4 + 3], "leg " + leg + ": fitted its wire");
            assertFalse(screened.isAtCapacity(leg), "leg " + leg + ": nothing in the example is over its wire");
        }
        final PduPlates.Device furnace = PduPlates.shed().get(1);
        assertTrue(legs[furnace.leg() * 4 + 3] >= furnace.cost(), "a machine shed for supply still fitted its leg");

        assertEquals(PduPlates.studsWired(), PduPlates.drawing(PduPlates.LEGS).text().stream()
                .filter(t -> t.s().equals(com.createstats.gui.pdu.PduLegRow.State.LIVE.label())).count());
    }

    @Test
    void theFlowSceneIsTheScreensScene() {
        final int[] vp = PduPlates.drawing(PduPlates.FLOW).part("flow.viewport");
        final com.createstats.book.scene.BlockFigure scene = PduPlates.flowScene();
        assertEquals(vp[2], scene.width());
        assertEquals(vp[3], scene.height());
        final List<net.minecraft.core.BlockPos> positions = PduPlates.scenePositions();
        final List<net.minecraft.core.BlockPos> blocks = new ArrayList<>();
        final Set<net.minecraft.core.BlockPos> glass = new HashSet<>();
        final net.minecraft.core.BlockPos board = PduPlates.board();
        assertTrue(positions.contains(board), "the board is in its own scene");
        for (final com.createstats.book.scene.BlockFigure.Placement p : scene.placements()) {
            final net.minecraft.core.BlockPos at = new net.minecraft.core.BlockPos((int) p.x(), (int) p.y(), (int) p.z());
            if (PduPlates.isGlass(p)) {
                glass.add(at);
            } else {
                blocks.add(at);
            }
        }
        assertEquals(positions.size(), blocks.size(), "each block of the scene once");
        final Set<net.minecraft.core.BlockPos> dark = new HashSet<>();
        dark.add(new net.minecraft.core.BlockPos(0, 0, 0));
        for (final PduPlates.Device d : PduPlates.DEVICES) {
            if (!PduPlates.runs(d)) {
                dark.add(d.pos().subtract(board));
            }
        }
        assertEquals(dark, glass, "glass on the board and on every machine nothing reaches");

        final com.createstats.gui.pdu.PduSceneFrame.Frame f = com.createstats.gui.pdu.PduSceneFrame.of(positions);
        final float s = (float) f.scale(vp[2], vp[3], 1.0f);
        final org.joml.Matrix4f m = new org.joml.Matrix4f()
                .translate(vp[0] + vp[2] / 2.0f, vp[1] + vp[3] / 2.0f, 250.0f)
                .scale(s, -s, s)
                .rotate(com.mojang.math.Axis.XP.rotationDegrees(com.createstats.gui.pdu.PduSceneFrame.START_PITCH))
                .rotate(com.mojang.math.Axis.YP.rotationDegrees(com.createstats.gui.pdu.PduSceneFrame.START_YAW));
        final net.minecraft.core.BlockPos drive = PduPlates.shed().get(0).pos();
        final org.joml.Vector3f corner = m.transformPosition((float) (drive.getX() - f.cx()) + 1.0f,
                (float) (drive.getY() - f.cy()) + 1.0f, (float) (drive.getZ() - f.cz()), new org.joml.Vector3f());
        final double[] book = PduPlates.sceneView(vp).project(drive.getX() - board.getX() + 1.0,
                drive.getY() - board.getY() + 1.0, drive.getZ() - board.getZ());
        assertEquals(corner.x(), book[0], 0.01, "the book's frame puts the drive where PduScene3D draws it, across");
        assertEquals(corner.y(), book[1], 0.01, "and down");
    }

    @Test
    void theHistoryIsTheExampleHeld() {
        final PduPlates.Drawing d = PduPlates.drawing(PduPlates.HISTORY);
        assertNotNull(d.text(String.format(java.util.Locale.ROOT, "Supply %,d", PduPlates.generation())));
        assertNotNull(d.text(String.format(java.util.Locale.ROOT, "Demand %,d",
                PduPlates.demand() + PduPlates.upkeep())));
    }

    @Test
    void theRiverIsTheExamplesFlow() {
        final PduPlates.Drawing d = PduPlates.drawing(PduPlates.IN_OUT);
        assertTrue(d.parts().containsKey("inout.box.from_buffer"), "the running set costs more than the supply");
        assertNotNull(d.text("Shed " + PduPlates.shedFe() + " FE/t"));
        assertNotNull(d.text("From buffer"));
    }

    @Test
    void theFaceIsThePlatesBoardWithBareStuds() {
        final CompoundTag wired = PduPlates.fixtureTag(null, true);
        final CompoundTag bare = PduPlates.fixtureTag(null, false);
        final Set<String> keys = new HashSet<>(wired.getAllKeys());
        keys.addAll(bare.getAllKeys());
        final List<String> differ = new ArrayList<>();
        for (final String k : keys) {
            if (!java.util.Objects.equals(wired.get(k), bare.get(k))) {
                differ.add(k);
            }
        }
        assertEquals(List.of("StudWires"), differ, "only the studs' wires differ");
        assertTrue(ControlSheets.PDU.parts().stream().anyMatch(p -> p instanceof Part.Figure f
                && f.specimen().equals("power_distribution_unit") && f.tab() == null), "the block figure");
        final Map<String, PduPlates.Drawing> recorded = PduPlates.recorded();
        assertEquals(PduPlates.IDS, List.copyOf(recorded.keySet()), "the record holds every plate, in order");
    }
}
