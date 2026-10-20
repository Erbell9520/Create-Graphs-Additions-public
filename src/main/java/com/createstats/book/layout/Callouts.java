package com.createstats.book.layout;

import com.createstats.book.BookTokens;
import com.createstats.book.specimen.SpecimenAnatomy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Callouts {
    public record Pin(String control, int number, boolean left, int boxX, int boxY, int anchorX, int anchorY) {
        public int leaderX() {
            return left ? boxX + BookTokens.PIN_W : boxX - 1;
        }

        public int leaderY() {
            return boxY + BookTokens.PIN_H / 2 - 1;
        }
    }

    private Callouts() {
    }

    public static Map<String, Integer> numbering(SpecimenAnatomy a) {
        final int[] k = a.referenceScale();
        final int figW = (int) Math.ceil(a.width() * k[0] / (double) k[1]);
        final int figH = (int) Math.ceil(a.height() * k[0] / (double) k[1]);
        final int fx = BookTokens.PIN_W + BookTokens.PIN_RUN;
        final List<Pin> pins = layout(a, fx, 0, k[0], k[1], fx - BookTokens.PIN_RUN - BookTokens.PIN_W,
                fx + figW + BookTokens.PIN_RUN, -2, figH + 2, null);
        final Map<String, Integer> out = new HashMap<>();
        for (final Pin p : pins) {
            out.put(p.control(), p.number());
        }
        return out;
    }

    public static List<Pin> layout(SpecimenAnatomy a, int fx, int fy, int num, int den, int leftX, int rightX,
                                   int top, int bottom, Map<String, Integer> numbers) {
        final List<Anchor> left = new ArrayList<>();
        final List<Anchor> right = new ArrayList<>();
        for (final SpecimenAnatomy.Hot h : a.controls()) {
            final Anchor an = new Anchor(h.id(), fx + Math.round(h.anchorX() * num / (float) den),
                    fy + Math.round(h.anchorY() * num / (float) den),
                    fx + h.x() * num / den, fy + h.y() * num / den,
                    fx + (int) Math.ceil((h.x() + h.w()) * num / (double) den),
                    fy + (int) Math.ceil((h.y() + h.h()) * num / (double) den), h.point());
            (h.anchorX() < a.width() / 2 ? left : right).add(an);
        }
        final List<Pin> out = new ArrayList<>();
        column(left, true, leftX, top, bottom, numbers, out);
        column(right, false, rightX, top, bottom, numbers, out);
        if (numbers == null) {
            out.sort(Comparator.comparing((Pin p) -> !p.left()).thenComparingInt(Pin::boxY));
            final List<Pin> numbered = new ArrayList<>();
            for (int i = 0; i < out.size(); i++) {
                final Pin p = out.get(i);
                numbered.add(new Pin(p.control(), i + 1, p.left(), p.boxX(), p.boxY(), p.anchorX(), p.anchorY()));
            }
            return numbered;
        }
        out.sort(Comparator.comparingInt(Pin::number));
        return out;
    }

    private record Anchor(String id, int x, int y, int x0, int y0, int x1, int y1, boolean point) {
        private static final int SMALL = 18;
        private static final int INSET = 2;

        int[] end(boolean left, int boxCentreY) {
            if (point || (x1 - x0 <= SMALL && y1 - y0 <= SMALL)) {
                return new int[]{x, y};
            }
            final int ex = left ? x0 + INSET : x1 - 1 - INSET;
            final int ey = Math.max(y0 + INSET + 1, Math.min(y1 - INSET - 2, boxCentreY));
            return new int[]{ex, ey};
        }
    }

    private static void column(List<Anchor> anchors, boolean left, int boxX, int top, int bottom,
                               Map<String, Integer> numbers, List<Pin> out) {
        if (anchors.isEmpty()) {
            return;
        }
        final int edgeX = left ? boxX + BookTokens.PIN_W : boxX - 1;
        if (numbers != null) {
            anchors.sort(Comparator.comparingInt(an -> numbers.getOrDefault(an.id(), 0)));
        } else {
            anchors.sort(Comparator.comparingInt(Anchor::y).thenComparingInt(an -> -Math.abs(an.x() - edgeX)));
        }
        final int[] ys = spread(anchors, top, bottom);
        if (numbers == null) {
            untangle(anchors, ys, edgeX, left);
        }
        for (int i = 0; i < anchors.size(); i++) {
            final Anchor an = anchors.get(i);
            final int n = numbers == null ? 0 : numbers.getOrDefault(an.id(), 0);
            final int[] e = an.end(left, ys[i] + BookTokens.PIN_H / 2 - 1);
            out.add(new Pin(an.id(), n, left, boxX, ys[i], e[0], e[1]));
        }
    }

    static int[] spread(List<Anchor> anchors, int top, int bottom) {
        final int n = anchors.size();
        final int pitch = BookTokens.PIN_PITCH;
        final List<int[]> runs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int target = anchors.get(i).y() - BookTokens.PIN_H / 2;
            runs.add(new int[]{target, 1, target});
            while (runs.size() > 1) {
                final int[] b = runs.get(runs.size() - 1);
                final int[] a = runs.get(runs.size() - 2);
                if (a[0] + a[1] * pitch <= b[0]) {
                    break;
                }
                final int count = a[1] + b[1];
                final int sum = a[2] + b[2];
                final int start = Math.round((float) sum / count - (count - 1) * pitch / 2.0f);
                runs.remove(runs.size() - 1);
                runs.set(runs.size() - 1, new int[]{start, count, sum});
            }
        }
        final int[] ys = new int[n];
        int k = 0;
        for (final int[] r : runs) {
            for (int j = 0; j < r[1]; j++) {
                ys[k++] = r[0] + j * pitch;
            }
        }

        for (int i = 0; i < n; i++) {
            final int min = i == 0 ? top : ys[i - 1] + pitch;
            ys[i] = Math.max(ys[i], min);
        }
        for (int i = n - 1; i >= 0; i--) {
            final int max = i == n - 1 ? bottom - BookTokens.PIN_H : ys[i + 1] - pitch;
            ys[i] = Math.min(ys[i], max);
        }
        return ys;
    }

    private static void untangle(List<Anchor> anchors, int[] ys, int edgeX, boolean left) {
        for (int pass = 0; pass < anchors.size() * anchors.size(); pass++) {
            boolean swapped = false;
            for (int i = 0; i + 1 < anchors.size(); i++) {
                final Anchor a = anchors.get(i);
                final Anchor b = anchors.get(i + 1);
                final int ay = ys[i] + BookTokens.PIN_H / 2 - 1;
                final int by = ys[i + 1] + BookTokens.PIN_H / 2 - 1;
                final int[] ea = a.end(left, ay);
                final int[] eb = b.end(left, by);
                if (crosses(edgeX, ay, ea[0], ea[1], edgeX, by, eb[0], eb[1])) {
                    anchors.set(i, b);
                    anchors.set(i + 1, a);
                    swapped = true;
                }
            }
            if (!swapped) {
                return;
            }
        }
    }

    public record Choice(String id, List<SpecimenAnatomy.Hot> places) {
        public Choice {
            places = List.copyOf(places);
            if (places.isEmpty()) {
                throw new IllegalArgumentException(id + " has nowhere to be pinned");
            }
        }
    }

    public static SpecimenAnatomy cleanest(String id, int width, int height, SpecimenAnatomy.Kind kind,
                                           List<Choice> choices, List<int[]> print) {
        return cleanest(id, width, height, kind, choices, print, SCALES);
    }

    public static SpecimenAnatomy cleanest(String id, int width, int height, SpecimenAnatomy.Kind kind,
                                           List<Choice> choices, List<int[]> print, int[][] scales) {
        final int[] pick = new int[choices.size()];
        int faults = faultsAt(build(id, width, height, kind, choices, pick), print, scales);

        for (int round = 0; round < 64 && faults > 0; round++) {
            int bestFaults = faults;
            int bestI = -1;
            int bestJ = -1;
            for (int i = 0; i < choices.size(); i++) {
                for (int j = 0; j < choices.get(i).places().size(); j++) {
                    if (j == pick[i]) {
                        continue;
                    }
                    final int was = pick[i];
                    pick[i] = j;
                    final int f = faultsAt(build(id, width, height, kind, choices, pick), print, scales);
                    pick[i] = was;
                    if (f < bestFaults) {
                        bestFaults = f;
                        bestI = i;
                        bestJ = j;
                    }
                }
            }
            if (bestI >= 0) {
                pick[bestI] = bestJ;
                faults = bestFaults;
                continue;
            }
            final int[] pair = bestPair(id, width, height, kind, choices, pick, print, faults, scales);
            if (pair == null) {
                break;
            }
            pick[pair[0]] = pair[1];
            pick[pair[2]] = pair[3];
            faults = pair[4];
        }

        double length = length(build(id, width, height, kind, choices, pick));
        for (int round = 0; round < 64; round++) {
            boolean moved = false;
            for (int i = 0; i < choices.size(); i++) {
                for (int j = 0; j < choices.get(i).places().size(); j++) {
                    if (j == pick[i]) {
                        continue;
                    }
                    final int was = pick[i];
                    pick[i] = j;
                    final SpecimenAnatomy trial = build(id, width, height, kind, choices, pick);
                    final double l = length(trial);
                    if (faultsAt(trial, print, scales) <= faults && l < length - 0.5) {
                        length = l;
                        moved = true;
                    } else {
                        pick[i] = was;
                    }
                }
            }
            if (!moved) {
                break;
            }
        }
        return build(id, width, height, kind, choices, pick);
    }

    private static int[] bestPair(String id, int width, int height, SpecimenAnatomy.Kind kind, List<Choice> choices,
                                  int[] pick, List<int[]> print, int faults, int[][] scales) {
        int[] best = null;
        int bestFaults = faults;
        for (int i = 0; i < choices.size(); i++) {
            for (int a = 0; a < choices.get(i).places().size(); a++) {
                if (a == pick[i]) {
                    continue;
                }
                for (int j = i + 1; j < choices.size(); j++) {
                    for (int b = 0; b < choices.get(j).places().size(); b++) {
                        if (b == pick[j]) {
                            continue;
                        }
                        final int wasI = pick[i];
                        final int wasJ = pick[j];
                        pick[i] = a;
                        pick[j] = b;
                        final int f = faultsAt(build(id, width, height, kind, choices, pick), print, scales);
                        pick[i] = wasI;
                        pick[j] = wasJ;
                        if (f < bestFaults) {
                            bestFaults = f;
                            best = new int[]{i, a, j, b, f};
                        }
                    }
                }
            }
        }
        return best;
    }

    private static SpecimenAnatomy build(String id, int width, int height, SpecimenAnatomy.Kind kind,
                                         List<Choice> choices, int[] pick) {
        final List<SpecimenAnatomy.Hot> hots = new ArrayList<>(choices.size());
        for (int i = 0; i < choices.size(); i++) {
            hots.add(choices.get(i).places().get(pick[i]));
        }
        return new SpecimenAnatomy(id, width, height, hots, kind);
    }

    public static List<Pin> reference(SpecimenAnatomy a) {
        final int[] k = a.referenceScale();
        final int figW = (int) Math.ceil(a.width() * k[0] / (double) k[1]);
        final int figH = (int) Math.ceil(a.height() * k[0] / (double) k[1]);
        final int fx = BookTokens.PIN_W + BookTokens.PIN_RUN;
        return layout(a, fx, 0, k[0], k[1], fx - BookTokens.PIN_RUN - BookTokens.PIN_W,
                fx + figW + BookTokens.PIN_RUN, -2, figH + 2, null);
    }

    private static double length(SpecimenAnatomy a) {
        double sum = 0.0;
        for (final Pin p : reference(a)) {
            sum += Math.hypot(p.anchorX() - p.leaderX(), p.anchorY() - p.leaderY());
        }
        return sum;
    }

    public static final int[][] SCALES = {{1, 2}, {2, 2}, {1, 3}, {2, 3}, {3, 3}};

    public static int faultsAtEveryScale(SpecimenAnatomy a, List<int[]> print) {
        return faultsAt(a, print, SCALES);
    }

    public static int faultsAt(SpecimenAnatomy a, List<int[]> print, int[][] scales) {
        int sum = faults(a, print);
        final Map<String, Integer> numbers = numbering(a);
        final int fx = BookTokens.PIN_W + BookTokens.PIN_RUN;
        for (final int[] s : scales) {
            final int fw = (int) Math.ceil(a.width() * s[0] / (double) s[1]);
            final int fh = (int) Math.ceil(a.height() * s[0] / (double) s[1]);
            final List<Pin> pins = layout(a, fx, 0, s[0], s[1], fx - BookTokens.PIN_RUN - BookTokens.PIN_W,
                    fx + fw + BookTokens.PIN_RUN, -2, fh + 2, numbers);
            sum += faults(pins, fx, 0, s[0], s[1], print);
        }
        return sum;
    }

    public static int faults(SpecimenAnatomy a, List<int[]> print) {
        final int[] k = a.referenceScale();
        return faults(reference(a), BookTokens.PIN_W + BookTokens.PIN_RUN, 0, k[0], k[1], print);
    }

    public static final int MARKS_APART = 8;

    public static int faults(List<Pin> pins, int fx, int fy, int num, int den, List<int[]> print) {
        int faults = 0;
        for (int i = 0; i < pins.size(); i++) {
            final Pin p = pins.get(i);
            for (int j = i + 1; j < pins.size(); j++) {
                final Pin q = pins.get(j);
                if (crosses(p.leaderX(), p.leaderY(), p.anchorX(), p.anchorY(), q.leaderX(), q.leaderY(), q.anchorX(),
                        q.anchorY())) {
                    faults++;
                }
                if (Math.abs(p.anchorX() - q.anchorX()) < MARKS_APART
                        && Math.abs(p.anchorY() - q.anchorY()) < MARKS_APART) {
                    faults++;
                }
            }
            for (final Pin q : pins) {
                if (q != p && crossesRect(p.leaderX(), p.leaderY(), p.anchorX(), p.anchorY(), q.anchorX() - 2,
                        q.anchorY() - 2, q.anchorX() + 3, q.anchorY() + 3)) {
                    faults++;
                }
            }
            for (final int[] r : print) {
                final double x0 = fx + r[0] * num / (double) den;
                final double y0 = fy + r[1] * num / (double) den;
                final double x1 = fx + (r[0] + r[2]) * num / (double) den;
                final double y1 = fy + (r[1] + r[3]) * num / (double) den;

                final boolean own = p.anchorX() >= x0 && p.anchorX() <= x1 && p.anchorY() >= y0 && p.anchorY() <= y1;
                if (!own && crossesRect(p.leaderX(), p.leaderY(), p.anchorX(), p.anchorY(), x0, y0, x1, y1)) {
                    faults++;
                }
            }
        }
        return faults;
    }

    public static boolean crossesRect(double ax, double ay, double bx, double by, double x0, double y0, double x1,
                                      double y1) {
        final double l = x0 + 0.5;
        final double t = y0 + 0.5;
        final double r = x1 - 0.5;
        final double b = y1 - 0.5;
        if (r <= l || b <= t) {
            return false;
        }
        final double dx = bx - ax;
        final double dy = by - ay;
        double u0 = 0.0;
        double u1 = 1.0;
        final double[] p = {-dx, dx, -dy, dy};
        final double[] q = {ax - l, r - ax, ay - t, b - ay};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) {
                    return false;
                }
            } else {
                final double u = q[i] / p[i];
                if (p[i] < 0.0) {
                    u0 = Math.max(u0, u);
                } else {
                    u1 = Math.min(u1, u);
                }
            }
        }
        return u0 < u1;
    }

    public static boolean crosses(int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4) {
        final long d1 = cross(x3, y3, x4, y4, x1, y1);
        final long d2 = cross(x3, y3, x4, y4, x2, y2);
        final long d3 = cross(x1, y1, x2, y2, x3, y3);
        final long d4 = cross(x1, y1, x2, y2, x4, y4);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static long cross(int ax, int ay, int bx, int by, int px, int py) {
        return (long) (bx - ax) * (py - ay) - (long) (by - ay) * (px - ax);
    }
}
