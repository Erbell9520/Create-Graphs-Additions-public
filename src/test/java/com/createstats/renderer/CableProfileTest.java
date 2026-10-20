package com.createstats.renderer;

import com.createstats.power.CableTier;
import com.createstats.renderer.CableProfile.Band;
import com.createstats.renderer.CableProfile.Grade;
import com.createstats.renderer.CableProfile.Part;
import com.createstats.renderer.CableProfile.Region;
import com.createstats.renderer.CableProfile.Skin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CableProfileTest {
    private static final String BLOCK_TEX = "src/main/resources/assets/create_stats/textures/block/";
    private static final float EPS = 1.0e-4f;

    @Test
    @DisplayName("every texel any face of any grade can sample is painted")
    void everySampledTexelIsOpaque() throws IOException {
        final List<String> holes = new ArrayList<>();
        for (CableTier tier : CableTier.values()) {
            final Grade g = CableProfile.of(tier);
            final BufferedImage atlas = ImageIO.read(new File(BLOCK_TEX + g.atlas() + ".png"));
            assertEquals(CableProfile.ATLAS, atlas.getWidth(), g.atlas() + " width");
            assertEquals(CableProfile.ATLAS, atlas.getHeight(), g.atlas() + " height");
            for (Band b : g.bands()) {
                final float w = b.c1() - b.c0();
                final float h = b.n1() - b.n0();

                check(atlas, b.skin().top(), CableProfile.ATLAS, w, tier + " band top", holes);
                check(atlas, b.skin().flank(), CableProfile.ATLAS, h, tier + " band flank", holes);
                check(atlas, b.skin().end(), w, h, tier + " band end", holes);
                check(atlas, b.skin().cut(), w, h, tier + " band cut", holes);
            }
            for (List<Part> parts : List.of(g.hardware(), g.fitting(), g.splice())) {
                for (Part p : parts) {
                    checkPart(atlas, p.skin(), p.a1() - p.a0(), p.c1() - p.c0(), p.n1() - p.n0(),
                            tier + " part " + p, holes);
                }
            }
            if (g.strands()) {
                final float collar = 2.0f * Math.max(1.5f, g.halfWidthPx() + 0.5f);
                checkPart(atlas, g.gland(), 1.0f, collar, g.thicknessPx(), tier + " gland", holes);
                check(atlas, g.paint(), 1.0f, 1.0f, tier + " strand paint", holes);
            }
        }
        if (!holes.isEmpty()) {
            fail("faces sample transparent texels:\n  " + String.join("\n  ", holes));
        }
    }

    @Test
    @DisplayName("CableTier's drawing numbers are the profile's, and its hitbox reaches the tallest part")
    void tierAgreesWithProfile() {
        for (CableTier tier : CableTier.values()) {
            final Grade g = CableProfile.of(tier);
            assertEquals(g.thicknessPx(), tier.thicknessPx(), EPS, tier + " thickness");
            assertEquals(g.halfWidthPx(), tier.halfWidthPx(), EPS, tier + " half width");
            assertTrue(tier.thicknessPx() + tier.reliefPx() >= g.reachPx() - EPS,
                    tier + ": something it draws stands above its hitbox, visible and unclickable");
        }
    }

    @Test
    @DisplayName("every grade shows its circuit colour somewhere, so the load pulse has somewhere to go")
    void everyGradeShowsItsCircuit() {
        for (CableTier tier : CableTier.values()) {
            final Grade g = CableProfile.of(tier);
            final boolean band = g.bands().stream().anyMatch(Band::dyed);
            final boolean hardware = g.hardware().stream().anyMatch(Part::dyed);
            assertTrue(band || hardware, tier + " never shows its colour on a straight run");
        }
    }

    @Test
    @DisplayName("the load pulse spans at least four bands on colour shown only every few blocks")
    void pulseTravelsAcrossBands() {
        for (CableTier tier : CableTier.values()) {
            final Grade g = CableProfile.of(tier);
            if (g.bands().stream().anyMatch(Band::dyed)) {
                continue;
            }
            assertTrue(CableProfile.PULSE_STEP * g.spacing() < Math.PI / 2.0,
                    tier + ": colour every " + g.spacing() + " blocks gives under four bands a wave at step "
                            + CableProfile.PULSE_STEP);
        }
    }

    @Test
    @DisplayName("stacked bands rest on the face or on each other, never float, and narrow as they rise")
    void bandsStack() {
        for (CableTier tier : CableTier.values()) {
            final Grade g = CableProfile.of(tier);
            if (g.nested()) {
                for (Band b : g.bands()) {
                    assertEquals(0.0f, b.n0(), EPS, tier + ": a core must lie on the face");
                }
                continue;
            }
            Band below = null;
            for (Band b : g.bands()) {
                if (below == null) {
                    assertEquals(0.0f, b.n0(), EPS, tier + ": the first band lies on the face");
                } else {
                    assertEquals(below.n1(), b.n0(), EPS, tier + ": a band must sit on the one below");
                    assertTrue(b.c0() > below.c0() - EPS && b.c1() < below.c1() + EPS,
                            tier + ": a raised band wider than its base overhangs into thin air");
                }
                below = b;
            }
        }
    }

    @Test
    @DisplayName("an elbow nests the trunk's cores rather than crossing them")
    void nestedCoresDoNotCross() {
        final Grade g = CableProfile.of(CableTier.TRUNK);

        for (int sp = -1; sp <= 1; sp += 2) {
            for (int ss = -1; ss <= 1; ss += 2) {
                float last = Float.NaN;
                for (Band b : g.bands()) {
                    final float centre = (b.c0() + b.c1()) * 0.5f;
                    final float turn = 8.0f + sp * ss * (centre - 8.0f);
                    final float w = (b.c1() - b.c0()) * 0.5f;
                    if (!Float.isNaN(last)) {
                        assertTrue(Math.abs(turn - last) >= 2.0f * w - EPS,
                                "cores overlap round an elbow at sp=" + sp + " ss=" + ss);
                    }
                    last = turn;
                }
            }
        }
        assertTrue(CableProfile.SPLICE_HALF >= g.halfWidthPx(),
                "the splice box must be wide enough to take every core");
    }

    private static void checkPart(BufferedImage atlas, Skin skin, float len, float width, float height,
                                  String what, List<String> holes) {
        check(atlas, skin.top(), len, width, what + " top", holes);
        check(atlas, skin.flank(), len, height, what + " flank", holes);
        check(atlas, skin.end(), width, height, what + " end", holes);
    }

    private static void check(BufferedImage atlas, Region r, float w, float h, String what,
                              List<String> holes) {
        final int tw = Math.max(1, (int) Math.ceil(w - EPS));
        final int th = Math.max(1, (int) Math.ceil(h - EPS));
        for (int y = r.v(); y < r.v() + th; y++) {
            for (int x = r.u(); x < r.u() + tw; x++) {
                if (x < 0 || y < 0 || x >= atlas.getWidth() || y >= atlas.getHeight()) {
                    holes.add(what + " reads off the atlas at " + x + "," + y);
                    return;
                }
                if (((atlas.getRGB(x, y) >>> 24) & 0xFF) != 0xFF) {
                    holes.add(what + " reads a transparent texel at " + x + "," + y);
                    return;
                }
            }
        }
    }
}
