package com.createstats.manual.figure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One figure "sheet" (a flippable page within a manual entry): a short title plus a set of
 * {@link FigureElement}s. Use {@link #builder(String)} to author sheets compactly.
 */
public record FigureSheet(String title, List<FigureElement> elements) {

    public FigureSheet {
        elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    public static Builder builder(String title) {
        return new Builder(title);
    }

    /** Fluent authoring helper. */
    public static final class Builder {
        private final String title;
        private final List<FigureElement> elements = new ArrayList<>();

        private Builder(String title) {
            this.title = title;
        }

        /** Solid labelled block node. */
        public Builder block(String id, float nx, float ny, float nw, float nh,
                             String title, String subtitle, int color) {
            this.elements.add(new FigureElement.Node(id, nx, ny, nw, nh, title, subtitle, color,
                    FigureElement.Style.BLOCK));
            return this;
        }

        /** Display/readout panel node. */
        public Builder panel(String id, float nx, float ny, float nw, float nh,
                             String title, String subtitle, int color) {
            this.elements.add(new FigureElement.Node(id, nx, ny, nw, nh, title, subtitle, color,
                    FigureElement.Style.PANEL));
            return this;
        }

        /** Animated flow arrow between two node ids. */
        public Builder arrow(String fromId, String toId, int color) {
            this.elements.add(new FigureElement.Arrow(fromId, toId, color));
            return this;
        }

        /** Pill label. */
        public Builder chip(float nx, float ny, String label, int color) {
            this.elements.add(new FigureElement.Chip(nx, ny, label, color));
            return this;
        }

        /** Left-aligned caption. */
        public Builder note(float nx, float ny, String text, int color) {
            this.elements.add(new FigureElement.Note(nx, ny, text, color, false));
            return this;
        }

        /** Centered caption ({@code nx} is the center). */
        public Builder noteCenter(float nx, float ny, String text, int color) {
            this.elements.add(new FigureElement.Note(nx, ny, text, color, true));
            return this;
        }

        public FigureSheet build() {
            return new FigureSheet(this.title, this.elements);
        }
    }
}
