package com.createstats.book.scene;

import com.createstats.book.specimen.SpecimenAnatomy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// the real model, a drawing goes stale the first rework
public record BlockFigure(String id, int width, int height, int margin, double yaw, double pitch,
                          List<Placement> placements, List<Pin> pins, List<Tie> ties, Framing framing) {
    public BlockFigure {
        placements = List.copyOf(placements);
        pins = List.copyOf(pins);
        ties = List.copyOf(ties);
        framing = framing == null ? Framing.FIT : framing;
    }

    public BlockFigure(String id, int width, int height, int margin, double yaw, double pitch,
                       List<Placement> placements, List<Pin> pins, List<Tie> ties) {
        this(id, width, height, margin, yaw, pitch, placements, pins, ties, Framing.FIT);
    }

    public BlockFigure(String id, int width, int height, int margin, double yaw, double pitch,
                       List<Placement> placements, List<Pin> pins) {
        this(id, width, height, margin, yaw, pitch, placements, pins, List.of());
    }

    public record Tie(Kind kind, int[] from, int[] to, com.createstats.power.WireTier tier,
                      com.createstats.power.WireTension tension) {
        public Tie {
            from = from.clone();
            to = to.clone();
            if (kind == Kind.SPAN && (tier == null || tension == null)) {
                throw new IllegalArgumentException("a span names its grade and its tension");
            }
        }

        public Tie(Kind kind, int[] from, int[] to) {
            this(kind, from, to, null, null);
        }

        public static Tie span(int[] from, int[] to, com.createstats.power.WireTier tier,
                               com.createstats.power.WireTension tension) {
            return new Tie(Kind.SPAN, from, to, tier, tension);
        }

        public enum Kind {
            BORE, CORD, SPAN
        }
    }

    public record Framing(double[] centre, double scale, double[] at) {
        public static final Framing FIT = new Framing(null, 0.0, null);

        public Framing {
            centre = centre == null ? null : centre.clone();
            at = at == null ? null : at.clone();
        }

        public static Framing camera(double[] centre, double scale) {
            return new Framing(centre, scale, null);
        }

        public static Framing camera(double[] centre, double scale, double x, double y) {
            return new Framing(centre, scale, new double[]{x, y});
        }

        public boolean fitted() {
            return centre == null;
        }
    }

    public record Placement(String block, Map<String, String> props, double x, double y, double z,
                            double[] occupies, boolean renderer, String fixture) {
        public Placement {
            props = Map.copyOf(props);
            occupies = occupies.clone();
        }

        public Placement(String block, Map<String, String> props, double x, double y, double z, double[] occupies,
                         boolean renderer) {
            this(block, props, x, y, z, occupies, renderer, null);
        }

        public double[] sceneBox() {
            return new double[]{x * 16 + occupies[0], y * 16 + occupies[1], z * 16 + occupies[2],
                    x * 16 + occupies[3], y * 16 + occupies[4], z * 16 + occupies[5]};
        }
    }

    public record Pin(String id, double x, double y, double z, double[] face, double[] box) {
        public Pin {
            face = face.clone();
            box = box.clone();
        }
    }

    public BlockView view() {
        if (!framing.fitted()) {
            final double[] c = framing.centre();
            final double[] at = new BlockView(yaw, pitch, framing.scale(), 0.0, 0.0).projectPx(c[0], c[1], c[2]);
            final double[] to = framing.at() == null ? new double[]{width / 2.0, height / 2.0} : framing.at();
            return new BlockView(yaw, pitch, framing.scale(), to[0] - at[0], to[1] - at[1]);
        }
        final List<double[]> boxes = new ArrayList<>();
        for (final Placement p : placements) {
            boxes.add(p.sceneBox());
        }
        return BlockView.fit(yaw, pitch, boxes, width, height, margin);
    }

    public SpecimenAnatomy anatomy() {
        final BlockView v = view();
        final List<SpecimenAnatomy.Hot> hots = new ArrayList<>();
        for (final Pin pin : pins) {
            final double[] a = v.projectPx(pin.x(), pin.y(), pin.z());
            final double[] r = v.boundsPx(pin.box());
            final int x0 = clamp((int) Math.floor(r[0]), 0, width - 1);
            final int y0 = clamp((int) Math.floor(r[1]), 0, height - 1);
            final int x1 = clamp((int) Math.ceil(r[2]), x0 + 1, width);
            final int y1 = clamp((int) Math.ceil(r[3]), y0 + 1, height);
            hots.add(new SpecimenAnatomy.Hot(pin.id(), x0, y0, x1 - x0, y1 - y0, (int) Math.round(a[0]),
                    (int) Math.round(a[1]), true));
        }
        return new SpecimenAnatomy(id, width, height, hots, SpecimenAnatomy.Kind.BLOCK);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public String toJson() {
        final BlockView v = view();
        final StringBuilder b = new StringBuilder();
        b.append("{\"id\": \"").append(id).append("\", \"width\": ").append(width).append(", \"height\": ")
                .append(height).append(", \"view\": {\"yaw\": ").append(num(v.yaw())).append(", \"pitch\": ")
                .append(num(v.pitch())).append(", \"scale\": ").append(num(v.scale())).append(", \"offsetX\": ")
                .append(num(v.offsetX())).append(", \"offsetY\": ").append(num(v.offsetY()))
                .append("}, \"placements\": [");
        for (int i = 0; i < placements.size(); i++) {
            final Placement p = placements.get(i);
            b.append(i == 0 ? "" : ", ").append("{\"block\": \"").append(p.block()).append("\", \"props\": {");
            final Map<String, String> sorted = new LinkedHashMap<>();
            p.props().keySet().stream().sorted().forEach(k -> sorted.put(k, p.props().get(k)));
            int k = 0;
            for (final Map.Entry<String, String> e : sorted.entrySet()) {
                b.append(k++ == 0 ? "" : ", ").append('"').append(e.getKey()).append("\": \"").append(e.getValue())
                        .append('"');
            }
            b.append("}, \"at\": [").append(num(p.x())).append(", ").append(num(p.y())).append(", ").append(num(p.z()))
                    .append("], \"renderer\": ").append(p.renderer());
            if (p.fixture() != null) {
                b.append(", \"fixture\": \"").append(p.fixture()).append('"');
            }
            b.append('}');
        }
        b.append(']');
        if (!ties.isEmpty()) {
            b.append(", \"ties\": [");
            for (int i = 0; i < ties.size(); i++) {
                final Tie t = ties.get(i);
                b.append(i == 0 ? "" : ", ").append("{\"kind\": \"").append(t.kind().name()).append("\", \"from\": [")
                        .append(t.from()[0]).append(", ").append(t.from()[1]).append(", ").append(t.from()[2])
                        .append("], \"to\": [").append(t.to()[0]).append(", ").append(t.to()[1]).append(", ")
                        .append(t.to()[2]).append(']');
                if (t.kind() == Tie.Kind.SPAN) {
                    b.append(", \"tier\": \"").append(t.tier().name()).append("\", \"tension\": \"")
                            .append(t.tension().name()).append('"');
                }
                b.append('}');
            }
            b.append(']');
        }
        return b.append('}').toString();
    }

    private static String num(double d) {
        return String.format(Locale.ROOT, "%.6f", d);
    }
}
