package com.createstats.statsmk3;

import com.createstats.statsmk3.instrument.Instrument;
import com.createstats.statsmk3.instrument.InstrumentCtx;
import com.createstats.statsmk3.instrument.InstrumentRegistry;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.ui.Rect;

import org.junit.jupiter.api.Test;

class InstrumentCostTest {
    private static final Rect CARD_ART = new Rect(0.0f, 0.0f, 138.0f, 62.0f);

    private static final Rect RULE_ART = new Rect(0.0f, 0.0f, 96.0f, 40.0f);

    @Test
    void reportsPerInstrumentCost() {
        final CountingSurface s = new CountingSurface();
        final InstrumentCtx ctx = new InstrumentCtx();
        ctx.primary = channel(120);
        ctx.all = java.util.List.of(ctx.primary, channel(120), channel(120));
        ctx.lod = Lod.preview(48);

        System.out.println();
        System.out.println("=== one Instruments frame, card art " + (int) CARD_ART.w
                + " x " + (int) CARD_ART.h + ", preview level (history budget "
                + ctx.lod.historyBudget() + ") ===");
        int cardTotal = 0;
        for (final Instrument instrument : InstrumentRegistry.all()) {
            s.reset();
            instrument.draw(s, CARD_ART, ctx);
            cardTotal += s.triangles;
            System.out.printf("  %-10s %7d tris %7d verts %3d text%n",
                    instrument.id(), s.triangles, s.vertices(), s.textCalls);
        }

        int ruleTotal = 0;
        for (final String id : new String[]{"numeric", "spark", "arc", "area"}) {
            s.reset();
            InstrumentRegistry.get(id).draw(s, RULE_ART, ctx);
            ruleTotal += s.triangles;
        }

        System.out.println("  catalog (" + InstrumentRegistry.all().size() + " cards):    "
                + cardTotal + " tris");
        System.out.println("  auto rules (4 slots): " + ruleTotal + " tris");
        System.out.println("  page total:           " + (cardTotal + ruleTotal) + " tris, "
                + (cardTotal + ruleTotal) * 3 + " verts per frame");
    }

    @Test
    void reportsFaceTileCost() {
        final CountingSurface s = new CountingSurface();
        final InstrumentCtx ctx = new InstrumentCtx();
        ctx.primary = channel(120);
        ctx.all = java.util.List.of(ctx.primary);
        ctx.lod = Lod.FULL;

        System.out.println();
        System.out.println("=== one tile of a 3x3 signal wall, 40 x 34 panel pixels ===");
        final Rect tile = new Rect(0.0f, 0.0f, 40.0f, 34.0f);
        for (final Instrument instrument : InstrumentRegistry.all()) {
            s.reset();
            instrument.draw(s, tile, ctx);
            System.out.printf("  %-10s %7d tris%n", instrument.id(), s.triangles);
        }
    }

    private static ChannelView channel(int samples) {
        final ChannelView ch = new ChannelView("src_a_auto_m" + samples);
        ch.label = "Current Stress (SU)";
        ch.unit = "SU";
        ch.historyCount = samples;
        ch.history = new float[samples];
        for (int i = 0; i < samples; i++) {
            ch.history[i] = (float) (40.0 + i * 0.28 + Math.sin(i * 0.21) * 7.0);
        }
        ch.value = ch.history[samples - 1];
        ch.min = 0.0f;
        ch.max = 130.0f;
        return ch;
    }
}
