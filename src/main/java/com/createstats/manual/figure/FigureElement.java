package com.createstats.manual.figure;

/** [1] */
public sealed interface FigureElement {

    /** Visual treatment for a {@link Node}. */
    enum Style {
        /** Solid labelled block (machine / source). */
        BLOCK,
        /** A readout / display panel (two text rows). */
        PANEL
    }

    /**
     * A labelled box. {@code title} is the bold top line, {@code subtitle} the dim second line.
     * {@code id} lets arrows anchor to it.
     */
    record Node(String id, float nx, float ny, float nw, float nh,
                String title, String subtitle, int color, Style style) implements FigureElement {
    }

    /** An animated flow arrow from one node's edge to another's, clipped to their borders. */
    record Arrow(String fromId, String toId, int color) implements FigureElement {
    }

    /** A small pill label at a point. */
    record Chip(float nx, float ny, String label, int color) implements FigureElement {
    }

    /** A free caption. When {@code centered}, {@code nx} is the horizontal center. */
    record Note(float nx, float ny, String text, int color, boolean centered) implements FigureElement {
    }
}
