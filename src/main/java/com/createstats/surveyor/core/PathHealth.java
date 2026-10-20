package com.createstats.surveyor.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PathHealth {
    public static final int MAX_ITEMS = 32;
    public static final int MAX_WHY = 120;
    static final int FORMAT = 1;

    public record Item(SegmentCheck.Level level, double from, double to, String why, PathSurvey.Fix fix, int node, double at) {
    }

    private PathHealth() {
    }

    public static List<Item> of(Pathway p, PathSurvey survey, boolean amphibious, double height, double own, String vehicle) {
        List<TightBends.Bend> bends = own > 0.0 && p.nodes().size() >= 3
                ? TightBends.of(p, survey != null ? survey.spline() : PathSpline.of(p), own).bends() : List.of();
        return of(p, survey, amphibious, height, bends, own, vehicle);
    }

    public static List<Item> of(Pathway p, PathSurvey survey, boolean amphibious, double height, List<TightBends.Bend> bends,
                                double own, String vehicle) {
        List<Item> out = new ArrayList<>();
        if (survey != null) {
            for (PathSurvey.Issue i : survey.issues(p, amphibious, height)) {
                out.add(new Item(i.level(), i.from(), i.to(), i.why(), i.fix(), i.node(), mid(survey.spline(), i.from(), i.to())));
            }
        }
        if (!bends.isEmpty()) {
            PathSpline sp = survey != null ? survey.spline() : PathSpline.of(p);
            for (TightBends.Bend b : bends) {
                String where = b.node() >= 0 ? p.label(b.node()) : "here";
                String why = String.format(Locale.ROOT, "the bend at %s is tighter than %s can turn: radius %.1f, its own %.1f",
                        where, vehicle == null || vehicle.isEmpty() ? "the vehicle" : vehicle, b.radius(), own);
                out.add(new Item(SegmentCheck.Level.RED, b.from(), b.to(), why, PathSurvey.Fix.WIDEN, b.node(), b.middle(sp)));
            }
        }
        out.sort(Comparator.comparingInt((Item i) -> -i.level().ordinal()).thenComparingDouble(Item::from));
        return out.size() > MAX_ITEMS ? new ArrayList<>(out.subList(0, MAX_ITEMS)) : out;
    }

    private static double mid(PathSpline sp, double from, double to) {
        return sp.closed() ? sp.wrap(from + sp.ahead(from, to) / 2.0) : (from + to) / 2.0;
    }

    public static SegmentCheck.Level worst(List<Item> items) {
        SegmentCheck.Level l = SegmentCheck.Level.FINE;
        for (Item i : items) l = l.worst(i.level());
        return l;
    }

    public static Item first(List<Item> items) {
        Item best = null;
        for (Item i : items) {
            if (i.level() == SegmentCheck.Level.FINE || i.level() == SegmentCheck.Level.UNKNOWN) continue;
            if (best == null || i.level().ordinal() > best.level().ordinal()) best = i;
        }
        return best;
    }

    public static byte[] encode(List<Item> items) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(16 + items.size() * 48);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(FORMAT);
            int n = Math.min(items.size(), MAX_ITEMS);
            out.writeByte(n);
            for (int k = 0; k < n; k++) {
                Item i = items.get(k);
                out.writeByte(i.level().ordinal());
                out.writeFloat((float) i.from());
                out.writeFloat((float) i.to());
                out.writeUTF(i.why().length() > MAX_WHY ? i.why().substring(0, MAX_WHY) : i.why());
                out.writeByte(i.fix().ordinal());
                out.writeInt(i.node());
                out.writeFloat((float) i.at());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    public static List<Item> decode(byte[] data) throws IOException {
        List<Item> out = new ArrayList<>();
        if (data == null || data.length < 2) return out;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int format = in.readUnsignedByte();
            if (format != FORMAT) throw new IOException("health format " + format);
            int n = Math.min(in.readUnsignedByte(), MAX_ITEMS);
            SegmentCheck.Level[] levels = SegmentCheck.Level.values();
            PathSurvey.Fix[] fixes = PathSurvey.Fix.values();
            for (int k = 0; k < n; k++) {
                int level = in.readUnsignedByte();
                double from = in.readFloat();
                double to = in.readFloat();
                String why = in.readUTF();
                int fix = in.readUnsignedByte();
                int node = in.readInt();
                double at = in.readFloat();
                out.add(new Item(levels[Math.min(level, levels.length - 1)], from, to, why,
                        fixes[Math.min(fix, fixes.length - 1)], node, at));
            }
        }
        return out;
    }
}
