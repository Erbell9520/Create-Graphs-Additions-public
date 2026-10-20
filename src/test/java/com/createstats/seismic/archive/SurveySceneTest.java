package com.createstats.seismic.archive;

import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicWave;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveySceneTest {
    private static final String IRON = "c:ores/iron";
    private static final long IRON_RIG = ArchiveBlow.key(158, 69, -359);
    private static final long WEST = ArchiveBlow.key(140, 68, -385);
    private static final long DEEP = ArchiveBlow.key(164, -8, -353);

    private static SurveyScene build(SeismicArchive a, List<Long> rigs, int budget) {
        return SceneBuilder.build(a, rigs, rigs, Long.MIN_VALUE, 199, 69, -374, budget, 3_000_000L, a.revision());
    }

    @Test
    @DisplayName("a panel sees its own rigs' blows and nothing else")
    void onlyKnownRigs() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, 1_000L);
        g.strike(a, SiteGround.WEST_RIG, "c:ores/copper", 48, 2_000L);
        final SurveyScene mine = build(a, List.of(IRON_RIG), SceneBuilder.DEFAULT_BUDGET);
        assertEquals(1, mine.bowls.size());
        assertNotNull(mine.layer(IRON));
        assertEquals(null, mine.layer("c:ores/copper"));
        assertEquals(1, mine.rigs.size());
        assertTrue(mine.rigs.get(0).linked);
        final SurveyScene since = SceneBuilder.build(a, List.of(IRON_RIG, WEST), List.of(WEST), 1_500L, 199, 69, -374,
                SceneBuilder.DEFAULT_BUDGET, 3_000L, 1L);
        assertEquals(1, since.bowls.size(), "a blow before the panel's clear is left out");
        assertFalse(since.rigs.get(0).linked, "the iron rig is only remembered");
    }

    @Test
    @DisplayName("bodies hold every block exactly once, joined as the survey joins them")
    void bodiesPartitionTheCells() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final ArchiveBlow west = g.strike(a, SiteGround.WEST_RIG, IRON, 48, 1_000L);
        final ArchiveBlow east = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, 2_000L);
        final SurveyScene s = build(a, List.of(IRON_RIG, WEST), SceneBuilder.DEFAULT_BUDGET);
        final SurveyScene.Layer layer = s.layer(IRON);
        assertEquals(west.cellCount() + east.cellCount(), layer.blocks, "counts are exact");

        final Set<Long> seen = new HashSet<>();
        for (final SurveyScene.Body b : layer.bodies) {
            assertEquals(1, b.grain);
            assertEquals(b.count, b.cells.length);
            for (final int c : b.cells) {
                final int x = b.cellX(c);
                final int y = b.cellY(c);
                final int z = b.cellZ(c);
                assertTrue(x >= b.minX && x <= b.maxX && y >= b.minY && y <= b.maxY && z >= b.minZ && z <= b.maxZ);
                assertTrue(seen.add(ArchiveBlow.key(x, y, z)), "a block in two bodies");
            }
        }

        for (final SurveyScene.Body b : layer.bodies) {
            for (final SurveyScene.Body o : layer.bodies) {
                if (b == o || b.maxX + 1 < o.minX || o.maxX + 1 < b.minX || b.maxY + 1 < o.minY || o.maxY + 1 < b.minY
                        || b.maxZ + 1 < o.minZ || o.maxZ + 1 < b.minZ) {
                    continue;
                }
                for (final int c : b.cells) {
                    for (final int d : o.cells) {
                        assertTrue(Math.abs(b.cellX(c) - o.cellX(d)) > 1 || Math.abs(b.cellY(c) - o.cellY(d)) > 1
                                || Math.abs(b.cellZ(c) - o.cellZ(d)) > 1, "two bodies touch");
                    }
                }
            }
        }

        for (int i = 1; i < layer.bodies.size(); i++) {
            assertTrue(layer.bodies.get(i - 1).count >= layer.bodies.get(i).count);
        }
    }

    @Test
    @DisplayName("the nearest block and its echo are exact")
    void nearestAndEcho() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final ArchiveBlow b = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 64, 1_000L);
        final SurveyScene s = build(a, List.of(IRON_RIG), SceneBuilder.DEFAULT_BUDGET);
        for (final SurveyScene.Body body : s.layer(IRON).bodies) {
            double best = Double.MAX_VALUE;
            double toPlate = Double.MAX_VALUE;
            for (final int c : body.cells) {
                final int x = body.cellX(c);
                final int y = body.cellY(c);
                final int z = body.cellZ(c);
                final double dx = x + 0.5 - 199.5;
                final double dy = y + 0.5 - 69.5;
                final double dz = z + 0.5 - (-374 + 0.5);
                best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
                toPlate = Math.min(toPlate, b.distanceTo(x, y, z));
            }
            assertEquals(best, body.nearDist, 1.0e-3);
            assertEquals(SeismicWave.echoTick(toPlate), body.echo, "lit when its echo got back to the plate");
            assertEquals(1_000L, body.newest);
        }
    }

    @Test
    @DisplayName("a great cave folds to a grain, and the scene stays inside its budget")
    void budget() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        g.strike(a, SiteGround.OWNER_IRON_RIG, SeismicCatalog.VOIDS, 64, 1_000L);
        g.strike(a, SiteGround.DEEP_RIG, SeismicCatalog.VOIDS, 64, 2_000L);
        g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 64, 3_000L);
        final List<Long> rigs = List.of(IRON_RIG, DEEP);
        for (final int budget : new int[] {SceneBuilder.DEFAULT_BUDGET, 6_000, 1_500}) {
            final SurveyScene s = build(a, rigs, budget);
            int cells = 0;
            int blocks = 0;
            for (final SurveyScene.Layer l : s.layers) {
                for (final SurveyScene.Body b : l.bodies) {
                    cells += b.cells.length;
                    if (l.target.equals(SeismicCatalog.VOIDS)) {
                        blocks += b.count;
                    }
                }
            }
            final long held = a.blows().stream().filter(x -> x.target.equals(SeismicCatalog.VOIDS))
                    .mapToLong(ArchiveBlow::cellCount).sum();
            assertEquals(held, blocks, "folding never changes a count");
            final boolean folded = s.layer(SeismicCatalog.VOIDS).bodies.stream().allMatch(b -> b.grain == 8);
            assertTrue(cells <= budget || folded, cells + " cells for a budget of " + budget);
            assertTrue(s.layer(IRON).bodies.stream().allMatch(b -> b.grain == 1 && b.cells.length == b.count),
                    "an ore vein is never folded");
        }
    }

    @Test
    @DisplayName("a scene round trips the wire, and a forged one is refused")
    void wire() throws IOException {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 64, 1_000L);
        g.strike(a, SiteGround.OWNER_IRON_RIG, SeismicCatalog.VOIDS, 64, 2_000L);
        g.strike(a, SiteGround.DEEP_RIG, SeismicCatalog.LIQUIDS, 64, 3_000L);
        final SurveyScene s = build(a, List.of(IRON_RIG, DEEP), SceneBuilder.DEFAULT_BUDGET);
        final byte[] bytes = SceneCodec.encode(s);
        assertTrue(bytes.length < 40_000, "a full scene is " + bytes.length + " bytes on the wire");
        final SurveyScene t = SceneCodec.decode(bytes);
        assertEquals(s.stamp, t.stamp);
        assertEquals(s.bowls.size(), t.bowls.size());
        assertEquals(s.rigs.size(), t.rigs.size());
        assertEquals(s.layers.size(), t.layers.size());
        for (int i = 0; i < s.layers.size(); i++) {
            final SurveyScene.Layer p = s.layers.get(i);
            final SurveyScene.Layer q = t.layers.get(i);
            assertEquals(p.target, q.target);
            assertEquals(p.blocks, q.blocks);
            for (int k = 0; k < p.bodies.size(); k++) {
                final SurveyScene.Body x = p.bodies.get(k);
                final SurveyScene.Body y = q.bodies.get(k);
                assertEquals(x.count, y.count);
                assertEquals(x.grain, y.grain);
                assertEquals(x.maxY, y.maxY);
                assertEquals(x.newest, y.newest);
                assertEquals(x.echo, y.echo);
                assertEquals(x.nearDist, y.nearDist);
                assertArrayEquals(x.cells, y.cells);
            }
        }
        assertEquals(s.surface.nx(), t.surface.nx());
        assertEquals(s.surface.nz(), t.surface.nz());
        assertArrayEquals(s.surface.heights(), t.surface.heights());
        assertTrue(!t.surface.isEmpty(), "the scene carries the surface the blows read");
        final byte[] bad = bytes.clone();
        bad[bad.length / 3] ^= 0x5A;
        assertThrows(IOException.class, () -> SceneCodec.decode(bad));
        assertThrows(IOException.class, () -> SceneCodec.decode(java.util.Arrays.copyOf(bytes, bytes.length / 2)));

        final java.util.zip.Deflater d = new java.util.zip.Deflater(9);
        d.setInput(new byte[SceneCodec.MAX_INFLATED + 10]);
        d.finish();
        final byte[] buf = new byte[1 << 20];
        final int n = d.deflate(buf);
        d.end();
        assertThrows(IOException.class, () -> SceneCodec.decode(java.util.Arrays.copyOf(buf, n)));
        assertEquals(SurveyScene.EMPTY.bowls, SceneCodec.decode(new byte[0]).bowls);
    }

    @Test
    @DisplayName("a scene at its bounds still travels: under a packet's megabyte deflated, under the decoder's cap raw")
    void aSceneAtItsBoundsStillTravels() throws IOException {
        final java.util.Random rnd = new java.util.Random(7L);
        final List<SurveyScene.Body> ores = new java.util.ArrayList<>();
        final int perVein = SceneBuilder.ORE_CELLS / (SceneBuilder.MAX_BODIES - 100);
        for (int v = 0; v < SceneBuilder.MAX_BODIES - 100; v++) {
            final int x = -2_000 + rnd.nextInt(4_000);
            final int y = -64 + rnd.nextInt(128);
            final int z = -2_000 + rnd.nextInt(4_000);
            final int[] cells = new int[perVein];
            for (int k = 0; k < perVein; k++) {
                cells[k] = SurveyScene.cell(k, k % 2, k % 3);
            }
            ores.add(new SurveyScene.Body("c:ores/coal", 0, perVein, 1, x, y, z, cells, x, y, z, x + perVein, y + 1,
                    z + 2, x + 1.5f, y + 0.5f, z + 1.0f, 5_000_000L - v, 4_000_000L - v, 40 + v % 80, x, y, z,
                    10.0f + v * 0.1f));
        }
        final List<SurveyScene.Body> caves = new java.util.ArrayList<>();
        for (int v = 0; v < 100; v++) {
            final int[] cells = new int[SceneBuilder.DEFAULT_BUDGET / 100];
            for (int k = 0; k < cells.length; k++) {
                cells[k] = SurveyScene.cell(k % 20, (k / 20) % 10, k / 200);
            }

            java.util.Arrays.sort(cells);
            final int x = rnd.nextInt(1_000);
            caves.add(new SurveyScene.Body(SeismicCatalog.VOIDS, 0, cells.length * 8, 2, x, 0, x, cells, x, 0, x,
                    x + 40, 20, x + 4, x + 20.0f, 10.0f, x + 2.0f, 5_000_000L, 4_000_000L, 90, x, 0, x, 50.0f));
        }
        final SurveyScene scene = new SurveyScene(1L, 5_000_000L, List.of(new SurveyScene.Layer("c:ores/coal", ores),
                new SurveyScene.Layer(SeismicCatalog.VOIDS, caves)), List.of(), List.of(), SurveyScene.Surface.NONE);
        final byte[] wire = SceneCodec.encode(scene);
        assertTrue(wire.length < 1_048_576, "a clientbound payload's megabyte: " + wire.length + " bytes");
        final SurveyScene back = SceneCodec.decode(wire);
        assertEquals(SceneBuilder.MAX_BODIES - 100, back.layer("c:ores/coal").bodies.size());
        assertEquals(100, back.layer(SeismicCatalog.VOIDS).bodies.size());
    }

    private static SurveyScene.Body vein(long oldest, long newest, int echo) {
        return new SurveyScene.Body("c:ores/diamond", 0, 1, 1, 0, -40, 0, new int[] {0}, 0, -40, 0, 0, -40, 0, 0.5f,
                -39.5f, 0.5f, newest, oldest, echo, 0, -40, 0, 40.0f);
    }

    @Test
    @DisplayName("a face is sent what it draws: the board each target's nearest, the scan all but its hidden targets")
    void aFaceIsSentWhatItDraws() {
        final SurveyScene s = SurveyFixtures.afternoon().scene();
        final double rx = SurveyFixtures.PANEL_X + 0.5;
        final double rz = SurveyFixtures.PANEL_Z + 0.5;
        final SurveyScene board = s.faceView(SurveyScene.FACE_BOARD, Set.of(), Long.MIN_VALUE, rx, rz);
        for (final SurveyScene.Layer l : board.layers) {
            assertTrue(l.bodies.size() <= 2, l.target + " sent " + l.bodies.size() + " veins to a board");
            final SurveyFinds.Find near = SurveyFinds.nearest(s, l.target, -1, rx, rz);
            assertTrue(l.bodies.contains(near.body()), "the board's own find is sent");
        }
        final SurveyScene scan = s.faceView(SurveyScene.FACE_SCAN, Set.of("c:ores/coal"), Long.MIN_VALUE, rx, rz);
        assertNull(scan.layer("c:ores/coal"), "a hidden target is not drawn, so not sent");
        assertEquals(s.layer("c:ores/iron").bodies.size(), scan.layer("c:ores/iron").bodies.size());
        final SurveyScene.Body pinned = s.layer("c:ores/iron").bodies.get(s.layer("c:ores/iron").bodies.size() - 1);
        final SurveyScene withPin = s.faceView(SurveyScene.FACE_BOARD, Set.of(), ArchiveBlow.key(pinned.nearX,
                pinned.nearY, pinned.nearZ), rx, rz);
        assertTrue(withPin.layer("c:ores/iron").bodies.contains(pinned), "a pinned find is sent to every face");
    }

    @Test
    @DisplayName("a panel knows a vein when the plate hears it: not when the wave closes it, a little before")
    void aSceneIsHeardAsThePlateHearsIt() {
        final long at = 1_000L;
        final SurveyScene.Body fresh = vein(at, at, 50);
        final SurveyScene.Body known = vein(at - 9_000L, at, 50);
        final SurveyScene s = new SurveyScene(1L, at + 5L, List.of(new SurveyScene.Layer("c:ores/diamond",
                List.of(fresh, known))), List.of(), List.of(), SurveyScene.Surface.NONE);
        final SurveyScene early = s.heardAt(at + 10L);
        assertEquals(1, early.layer("c:ores/diamond").bodies.size(), "the new vein waits for its echo");
        assertTrue(early.layer("c:ores/diamond").bodies.get(0) == known, "a vein an older blow found stays");
        assertTrue(s.heardAt(at + 50L) == s, "heard: the scene itself, no copy");
        assertTrue(s.heardAt(at - 1L) == s, "before the blow: nothing waits");
        final SurveyScene alone = new SurveyScene(1L, at, List.of(new SurveyScene.Layer("c:ores/diamond",
                List.of(fresh))), List.of(), List.of(), SurveyScene.Surface.NONE);
        assertNull(alone.heardAt(at + 1L).layer("c:ores/diamond"), "a target with nothing heard yet has no layer");
    }

    private static void struck(SeismicArchive a, int[] rig, String target, int veins, int side, long at) {
        final ArchiveBlow b = a.begin(rig[0], rig[1], rig[2], target, 64, at, -64, 0L, 0L);
        final List<com.createstats.seismic.VeinBook.Vein> closed = new java.util.ArrayList<>();
        for (int i = 0; i < veins; i++) {
            final int dx = (i % 20) * 3 - 30;
            final int dy = -6 - (i / 20) * 3;
            final int dz = target.equals(IRON) ? 4 : -4;
            final int[] blocks = new int[side * side * side];
            int k = 0;
            for (int x = 0; x < side; x++) {
                for (int y = 0; y < side; y++) {
                    for (int z = 0; z < side; z++) {
                        blocks[k++] = com.createstats.seismic.SurveyShells.pack(dx + x, dy - y, dz + z * (dz > 0 ? 1 : -1));
                    }
                }
            }
            final float near = (float) com.createstats.seismic.SurveyShells.distanceOf(dx, dy, dz);
            closed.add(new com.createstats.seismic.VeinBook.Vein(0, blocks.length, near, near + side * 2, blocks, dx,
                    dy, dz));
        }
        a.step(b, 64.0, false, closed, true);
    }

    @Test
    @DisplayName("a crowded scene keeps its hardest echo whole and gives up distant soft ore first")
    void aCrowdedSceneStaysWithinItsBounds() {
        final SeismicArchive a = new SeismicArchive();
        final int[] rig = {0, 70, 0};
        struck(a, rig, IRON, 300, 2, 1_000L);
        struck(a, rig, "c:ores/diamond", 20, 2, 2_000L);
        final List<Long> rigs = List.of(ArchiveBlow.key(0, 70, 0));
        final SurveyScene whole = SceneBuilder.build(a, rigs, rigs, Long.MIN_VALUE, 0, 70, 0, 20_000, 3_000L, 1L);
        assertEquals(320, whole.layer(IRON).bodies.size() + whole.layer("c:ores/diamond").bodies.size());

        final SurveyScene s = SceneBuilder.build(a, rigs, rigs, Long.MIN_VALUE, 0, 70, 0, 20_000, 3_000L, 1L, 1_000,
                250);
        int cells = 0;
        int bodies = 0;
        for (final SurveyScene.Layer l : s.layers) {
            for (final SurveyScene.Body b : l.bodies) {
                cells += b.cells.length;
                bodies++;
                assertEquals(8, b.count, "a vein's count stays exact, whole or thinned");
            }
        }
        assertTrue(bodies <= 250, "veins: " + bodies);
        final SurveyScene.Layer diamond = s.layer("c:ores/diamond");
        assertEquals(20, diamond.bodies.size(), "every diamond kept");
        for (final SurveyScene.Body b : diamond.bodies) {
            assertEquals(8, b.cells.length, "every diamond whole");
        }

        float farthestWhole = 0.0f;
        float nearestThin = Float.MAX_VALUE;
        for (final SurveyScene.Body b : s.layer(IRON).bodies) {
            if (b.cells.length == 8) {
                farthestWhole = Math.max(farthestWhole, b.nearDist);
            } else {
                assertEquals(1, b.cells.length);
                nearestThin = Math.min(nearestThin, b.nearDist);
                assertEquals(b.nearX, b.cellX(b.cells[0]), "a thinned vein travels as its nearest block");
                assertEquals(b.nearY, b.cellY(b.cells[0]));
                assertEquals(b.nearZ, b.cellZ(b.cells[0]));
            }
        }
        assertTrue(farthestWhole <= nearestThin, "farthest first: " + farthestWhole + " vs " + nearestThin);
        float farthestKept = 0.0f;
        for (final SurveyScene.Body b : s.layer(IRON).bodies) {
            farthestKept = Math.max(farthestKept, b.nearDist);
        }
        for (final SurveyScene.Body b : whole.layer(IRON).bodies) {
            boolean kept = false;
            for (final SurveyScene.Body k : s.layer(IRON).bodies) {
                kept |= k.nearX == b.nearX && k.nearY == b.nearY && k.nearZ == b.nearZ;
            }
            assertTrue(kept || b.nearDist >= farthestKept, "a nearer iron vein was left out");
        }
        assertTrue(cells <= 1_000, "ore blocks within the bound: " + cells);
    }
}
