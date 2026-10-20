package com.createstats.book.specimen;

import java.util.List;

public record SpecimenAnatomy(String id, int width, int height, List<Hot> controls, Kind kind) {
    public enum Kind {
        SCREEN,

        DRAWING,

        BLOCK,

        OVERLAY
    }

    public SpecimenAnatomy {
        controls = List.copyOf(controls);
    }

    public SpecimenAnatomy(String id, int width, int height, List<Hot> controls) {
        this(id, width, height, controls, Kind.SCREEN);
    }

    public SpecimenAnatomy(String id, int width, int height, List<Hot> controls, boolean drawing) {
        this(id, width, height, controls, drawing ? Kind.DRAWING : Kind.SCREEN);
    }

    public boolean drawing() {
        return kind == Kind.DRAWING;
    }

    public boolean block() {
        return kind == Kind.BLOCK;
    }

    public boolean overlay() {
        return kind == Kind.OVERLAY;
    }

    public boolean cased() {
        return kind == Kind.BLOCK || kind == Kind.OVERLAY;
    }

    public record Hot(String id, int x, int y, int w, int h, int anchorX, int anchorY, boolean point) {
        public Hot(String id, int x, int y, int w, int h) {
            this(id, x, y, w, h, x + w / 2, y + h / 2, false);
        }

        public boolean contains(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    public Hot control(String controlId) {
        for (final Hot h : controls) {
            if (h.id().equals(controlId)) {
                return h;
            }
        }
        return null;
    }

    public int[] referenceScale() {
        return kind == Kind.SCREEN || kind == Kind.OVERLAY ? new int[]{2, 3} : new int[]{1, 1};
    }
}
