package com.createstats.statsmk3.page;

import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record AeroSkin(String name, int ground, int card, int cardEdge, int title, int ink, int ink2, int ink3,
                       int dialFace, int dialRim, int dialInk, int dialInk2, int astern, int ahead, int orderNeedle,
                       int answerNeedle, int tapeFace, int tapeInk, int window, int windowInk, int skyTop, int skyLow,
                       int earth, int earthEdge, int hull, int hullEdge, int envelope, int envelopeSeam, int gas,
                       int held, int limit, int engine, int destination) {
    public static final AeroSkin DECK = new AeroSkin("Flight deck",
            Tokens.CANVAS, Tokens.RAISED, Tokens.LINE, Tokens.INK_3, Tokens.INK, Tokens.INK_2, Tokens.INK_3,
            Tokens.SURFACE, Tokens.LINE_HI, Tokens.INK_2, Tokens.INK_3, Tokens.mix(Tokens.SURFACE, Tokens.LIMIT, 0.30f),
            Tokens.mix(Tokens.SURFACE, Tokens.INK_3, 0.18f), Tokens.INK, Tokens.ENGINE,
            Tokens.SURFACE, Tokens.INK_2, Tokens.CANVAS, Tokens.INK,
            Tokens.mix(Tokens.CANVAS, Tokens.SERIES[0], 0.20f), Tokens.mix(Tokens.CANVAS, Tokens.SERIES[0], 0.07f),
            Tokens.mix(Tokens.CANVAS, Tokens.EARTH, 0.75f), Tokens.EARTH_EDGE,
            Tokens.mix(Tokens.RAISED, Tokens.INK_3, 0.85f), Tokens.mix(Tokens.RAISED, Tokens.INK_2, 0.55f),
            Tokens.mix(Tokens.RAISED, Tokens.INK_2, 0.40f), Tokens.mix(Tokens.RAISED, Tokens.INK_2, 0.28f),
            Tokens.GAS, Tokens.HELD, Tokens.LIMIT, Tokens.ENGINE, Tokens.DESTINATION);
}
