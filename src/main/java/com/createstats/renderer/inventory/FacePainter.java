package com.createstats.renderer.inventory;

import com.createstats.blockentity.inventory.InventoryFace;
import com.createstats.inventory.core.EnergyReadout;
import com.createstats.inventory.core.FaceFormat;
import com.createstats.inventory.core.FlowClock;
import com.createstats.inventory.core.SightGlassLayout;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FacePainter {
    public static final int W = 224;
    public static final int H = 192;
    public static final int VERDICT_Y = 128;

    public record Icon(ItemStack stack, int x, int y, int size) {
    }

    public static final class Layers {
        public final GlassCanvas base = new GlassCanvas(W, H);
        public final GlassCanvas over = new GlassCanvas(W, H);
        public final List<Icon> icons = new ArrayList<>();

        public int[] fluid;

        public int[] belt;
    }

    private final Layers g = new Layers();

    private FacePainter() {
        g.base.fill(0, 0, W, H, FaceInks.GLASS);
        for (int y = 1; y < H; y += 3) {
            for (int x = 1; x < W; x += 3) {
                g.base.put(x, y, FaceInks.GLASS_DOT);
            }
        }
    }

    public static Layers paint(InventoryFace f) {
        final FacePainter p = new FacePainter();
        p.page(f);
        return p.g;
    }

    public static Layers blank() {
        final FacePainter p = new FacePainter();
        p.glare();
        return p.g;
    }

    public static Layers warming(InventoryFace f) {
        final FacePainter p = new FacePainter();
        p.header(f.source == InventoryFace.SOURCE_LINK, title(f),
                s(Component.translatable("create_stats.inventory_display.face.self_test")), FaceInks.GREEN, FaceInks.GREEN);
        p.glowCentre(s(Component.translatable("create_stats.inventory_display.face.warming_up")), 80, FaceInks.GREEN, 2);
        p.g.base.textCentre(s(Component.translatable("create_stats.inventory_display.face.warming_line")), 0, 104, W,
                FaceInks.GREEN.label, 1);
        p.glare();
        return p.g;
    }

    private static String s(Component c) {
        return glassSafe(c.getString());
    }

    static String glassSafe(String raw) {
        final String up = raw.toUpperCase(Locale.ROOT);
        final StringBuilder b = new StringBuilder(up.length());
        for (int i = 0; i < up.length(); i++) {
            final char c = up.charAt(i);
            b.append(c >= 32 && c <= 126 ? c : '?');
        }
        return b.toString();
    }

    static String title(InventoryFace f) {
        final String name = glassSafe(f.title.getString());
        if (f.sizeA > 0 && f.sizeC > 0) {
            return s(Component.translatable("create_stats.inventory_display.face.sized", name, f.sizeA, f.sizeB, f.sizeC));
        }
        return name;
    }

    static int pyRound(double v) {
        return (int) Math.rint(v);
    }

    private void rule(int x0, int x1, int y, int c) {
        g.base.fill(x0, y, x1, y + 1, c);
    }

    private void glow(String s, int x, int y, FaceInks ink, int scale) {
        g.base.glow(s, x, y, ink.main, ink.halo, scale);
    }

    private void glowRight(String s, int rx, int y, FaceInks ink, int scale) {
        glow(s, rx - GlassCanvas.width(s, scale), y, ink, scale);
    }

    private void glowCentre(String s, int y, FaceInks ink, int scale) {
        glow(s, (W - GlassCanvas.width(s, scale)) / 2, y, ink, scale);
    }

    private void glowCentreIn(String s, int x, int w, int y, FaceInks ink, int scale) {
        glow(s, x + (w - GlassCanvas.width(s, scale)) / 2, y, ink, scale);
    }

    private void well(int x0, int y0, int x1, int y1, FaceInks ink) {
        g.base.fill(x0, y0, x1, y1, FaceInks.GLASS_DEEP);
        rule(x0, x1, y0, ink.faint);
        g.base.fill(x0, y0, x0 + 1, y1, ink.faint);
    }

    private void bar(int x0, int y0, int x1, int y1, double frac, FaceInks ink, double[] marks, FaceInks[] markInks) {
        g.base.fill(x0, y0, x1, y1, ink.faint);
        final int w = pyRound((x1 - x0) * Math.max(0.0, Math.min(1.0, frac)));
        if (w > 0) {
            g.base.fill(x0, y0, x0 + w, y1, ink.mid);
            g.base.fill(x0, y0, x0 + w, y0 + 1, ink.main);
            g.base.fill(x0 + w - 1, y0, x0 + w, y1, ink.bright);
        }
        for (int i = 0; i < marks.length; i++) {
            final int mx = x0 + pyRound((x1 - x0) * Math.max(0.0, Math.min(1.0, marks[i])));
            g.base.fill(mx, y0 - 2, mx + 1, y1 + 2, markInks[i].bright);
        }
    }

    private void glare() {
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                final double d = (x + y * 1.4) - 120.0;
                if (d >= 0.0 && d < 26.0) {
                    g.base.put(x, y, GlassCanvas.over(g.base.get(x, y), FaceInks.GLARE));
                }
            }
        }
    }

    private void icon(ItemStack stack, int x, int y, int size) {
        if (stack != null && !stack.isEmpty()) {
            g.icons.add(new Icon(stack, x, y, size));
        }
    }

    private void count(String s, int rx, int by) {
        final int x = rx - GlassCanvas.width(s, 1);
        final int y = by - 7;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}}) {
            g.over.text(s, x + d[0], y + d[1], FaceInks.GLASS_DEEP, 1);
        }
        g.over.text(s, x, y, FaceInks.IDLE.bright, 1);
    }

    private void chevron(int x, int y, boolean up, int c) {
        final String[] rows = up ? new String[]{"..#..", ".###.", "#####"} : new String[]{"#####", ".###.", "..#.."};
        for (int r = 0; r < 3; r++) {
            for (int cc = 0; cc < 5; cc++) {
                if (rows[r].charAt(cc) == '#') {
                    g.over.put(x + cc, y + r, c);
                }
            }
        }
        for (int cc = -1; cc < 6; cc++) {
            for (int rr : new int[]{-1, 3}) {
                if ((g.over.get(x + cc, y + rr) >>> 24) == 0) {
                    g.over.put(x + cc, y + rr, FaceInks.GLASS_DEEP);
                }
            }
        }
    }

    private static final String[] SNOUT = {"..#####..", ".#.....#.", "#..###..#", "#.#...#.#", "#.#.#.#.#",
            "#.#...#.#", "#..###..#", ".#.....#.", "..#####.."};
    private static final String[] LINK = {"...###...", "..#...#..", "..#.#.#..", "..#...#..", "...###...",
            "....#....", "..#####..", ".#.....#.", "#########"};

    private void picto(boolean link, int x, int y, int c) {
        final String[] map = link ? LINK : SNOUT;
        for (int r = 0; r < map.length; r++) {
            for (int cc = 0; cc < map[r].length(); cc++) {
                if (map[r].charAt(cc) == '#') {
                    g.base.put(x + cc, y + r, c);
                }
            }
        }
    }

    private void pip(int x, int y, FaceInks ink) {
        for (int yy = 0; yy < 9; yy++) {
            for (int xx = 0; xx < 9; xx++) {
                final double d = Math.hypot(xx - 4, yy - 4);
                if (d <= 4.4) {
                    g.base.put(x + xx, y + yy, d < 1.6 ? ink.bright : d < 3.2 ? ink.main : ink.mid);
                }
            }
        }
    }

    private void header(boolean link, String name, String right, FaceInks rightInk, FaceInks nameInk) {
        picto(link, 5, 4, nameInk.label);
        final int maxW = W - 22 - (right.isEmpty() ? 0 : GlassCanvas.width(right, 1) + 10);
        g.base.text(FaceFormat.trimToGlass(name, maxW), 18, 5, nameInk.main, 1);
        if (!right.isEmpty()) {
            g.base.textRight(right, W - 5, 5, rightInk.main, 1);
        }
        rule(4, W - 4, 16, FaceInks.GREEN.faint);
    }

    private static String clock(double seconds) {
        return FaceFormat.clock(seconds,
                Component.translatable("create_stats.inventory_display.face.clock.hm").getString(),
                Component.translatable("create_stats.inventory_display.face.clock.days").getString());
    }

    private static String rate(double perMinute) {
        return FaceFormat.rate(perMinute, Component.translatable("create_stats.inventory_display.face.per_min").getString());
    }

    private static String clockLine(FlowClock.Kind kind, double seconds) {
        final String c = clock(seconds);
        return switch (kind) {
            case FULL -> s(Component.translatable("create_stats.inventory_display.face.full_in", c));
            case EMPTY -> s(Component.translatable("create_stats.inventory_display.face.empty_in", c));
            case LOW -> s(Component.translatable("create_stats.inventory_display.face.low_in", c));
            case HIGH -> s(Component.translatable("create_stats.inventory_display.face.high_in", c));
            case RUNTIME -> s(Component.translatable("create_stats.inventory_display.face.runtime")) + " " + c;
            default -> s(Component.translatable("create_stats.inventory_display.face.steady"));
        };
    }

    private static String clockWord(FlowClock.Kind kind) {
        return switch (kind) {
            case FULL -> s(Component.translatable("create_stats.inventory_display.face.full_in_word"));
            case EMPTY -> s(Component.translatable("create_stats.inventory_display.face.empty_in_word"));
            case LOW -> s(Component.translatable("create_stats.inventory_display.face.low_in_word"));
            case HIGH -> s(Component.translatable("create_stats.inventory_display.face.high_in_word"));
            case RUNTIME -> s(Component.translatable("create_stats.inventory_display.face.runtime"));
            default -> "";
        };
    }

    private static FaceInks clockInk(FlowClock.Kind kind, double seconds) {
        return switch (kind) {
            case EMPTY, LOW, RUNTIME -> seconds < 900.0 ? FaceInks.AMBER : FaceInks.GREEN;
            case FULL, HIGH -> seconds < 300.0 ? FaceInks.AMBER : FaceInks.GREEN;
            default -> FaceInks.GREEN;
        };
    }

    private void footerClock(int y, FlowClock.Kind kind, double seconds, boolean caption) {
        final FaceInks ink = clockInk(kind, seconds);
        if (kind == FlowClock.Kind.STEADY || kind == FlowClock.Kind.NONE) {
            glowCentre(s(Component.translatable("create_stats.inventory_display.face.steady")), y, ink, 2);
            if (caption) {
                g.base.textCentre(s(Component.translatable("create_stats.inventory_display.face.nothing_moving")), 0, y + 18,
                        W, ink.label, 1);
            }
            return;
        }
        glowCentre(clockLine(kind, seconds), y, ink, 2);
        if (caption) {
            g.base.textCentre(s(Component.translatable("create_stats.inventory_display.face.at_this_rate")), 0, y + 18, W,
                    ink.label, 1);
        }
    }

    private void verdict(int x, String word, String value, FaceInks ink, String caption, int width) {
        if (word != null && !word.isEmpty()) {
            put(word, x, VERDICT_Y, ink, 2, width);
            put(value, x, VERDICT_Y + 18, ink, 3, width);
        } else {
            put(value, x, VERDICT_Y + 12, ink, 3, width);
        }
        if (caption != null && !caption.isEmpty()) {
            if (width > 0) {
                g.base.textCentre(caption, x, 172, width, ink.label, 1);
            } else {
                g.base.text(caption, x, 172, ink.label, 1);
            }
        }
    }

    private void put(String s, int x, int y, FaceInks ink, int scale, int width) {
        if (width > 0) {
            glowCentreIn(s, x, width, y, ink, scale);
        } else {
            glow(s, x, y, ink, scale);
        }
    }

    private void flowLine(int y, double in, double out) {
        g.base.text(s(Component.translatable("create_stats.inventory_display.face.in")), 8, y, FaceInks.GREEN.label, 1);
        g.base.text(rate(in), 26, y, FaceInks.GREEN.main, 1);
        g.base.text(s(Component.translatable("create_stats.inventory_display.face.out")), 120, y, FaceInks.GREEN.label, 1);
        g.base.text(rate(out), 144, y, FaceInks.GREEN.main, 1);
    }

    private void fillLine(int y, double fill) {
        g.base.text(s(Component.translatable("create_stats.inventory_display.face.fill")), 8, y + 1, FaceInks.GREEN.label, 1);
        final String pct = FaceFormat.percent(fill);
        bar(34, y, W - 14 - GlassCanvas.width(pct, 2), y + 8, fill, FaceInks.GREEN, new double[0], new FaceInks[0]);
        glow(pct, W - 6 - GlassCanvas.width(pct, 2), y - 3, FaceInks.GREEN, 2);
    }

    private String linkReading(InventoryFace f) {
        return s(Component.translatable("create_stats.inventory_display.face.link", Integer.toString(Math.max(0, f.linkDistance))));
    }

    private boolean viaLink(InventoryFace f) {
        return f.source == InventoryFace.SOURCE_LINK;
    }

    private void page(InventoryFace f) {
        switch (f.page) {
            case CONTAINER -> contents(f);
            case VAULT -> vault(f);
            case BELT -> belt(f);
            case TANK, DIESEL -> tank(f);
            case ENERGY -> energy(f);
            case WATCH_ONE -> watchOne(f);
            case WATCH_MANY -> watchMany(f);
            case LINK_TEXT -> feed(f);
            case NOTHING -> message(viaLink(f), s(Component.translatable("create_stats.inventory_display.face.nothing_behind")),
                    s(Component.translatable("create_stats.inventory_display.face.nothing_to_read")),
                    List.of(s(Component.translatable("create_stats.inventory_display.face.no_target.1")),
                            s(Component.translatable("create_stats.inventory_display.face.no_target.2")),
                            s(Component.translatable("create_stats.inventory_display.face.no_target.3"))),
                    FaceInks.IDLE, "");
            case CANT_READ -> message(viaLink(f), title(f), s(Component.translatable("create_stats.inventory_display.face.cant_read")),
                    List.of(s(Component.translatable("create_stats.inventory_display.face.cant_read.1", glassSafe(f.title.getString()))),
                            s(Component.translatable("create_stats.inventory_display.face.cant_read.2"))),
                    FaceInks.RED, "");
            case OUT_OF_REACH -> outOfReach(f);
            case NOT_LOADED -> {
                final List<String> lines = new ArrayList<>();
                lines.add(s(Component.translatable("create_stats.inventory_display.face.not_loaded.1", glassSafe(f.title.getString()))));
                lines.add(s(Component.translatable("create_stats.inventory_display.face.not_loaded.2")));
                if (f.lastReadSeconds >= 0) {
                    lines.add(s(Component.translatable("create_stats.inventory_display.face.not_loaded.3", clock(f.lastReadSeconds))));
                }
                message(true, title(f), s(Component.translatable("create_stats.inventory_display.face.not_loaded")), lines,
                        FaceInks.IDLE, f.linkDistance >= 0 ? s(Component.translatable("create_stats.inventory_display.face.link_short",
                                Integer.toString(f.linkDistance))) : "");
            }
            default -> glare();
        }
    }

    private void contents(InventoryFace f) {
        final int slots = Math.max(1, f.totalSlots);
        final String right = viaLink(f) ? linkReading(f)
                : s(Component.translatable("create_stats.inventory_display.face.slots", Integer.toString(f.usedSlots),
                Integer.toString(slots)));
        header(viaLink(f), title(f), right, FaceInks.GREEN, FaceInks.GREEN);
        final SightGlassLayout.Layout l = SightGlassLayout.forSlots(slots);
        for (int i = 0; i < l.shown() && i < f.slots.size(); i++) {
            final int cx = l.cellX(i);
            final int cy = l.cellY(i);
            well(cx, cy, cx + l.cellW(), cy + l.cellH(), FaceInks.GREEN);
            final ItemStack st = f.slots.get(i);
            if (st.isEmpty()) {
                continue;
            }
            icon(st, cx + 3, cy + 3, l.cellW() - 6);
            if (st.getCount() > 1) {
                count(Integer.toString(st.getCount()), cx + l.cellW() - 1, cy + l.cellH() - 1);
            }
            final byte flow = i < f.slotFlow.length ? f.slotFlow[i] : InventoryFace.FLOW_NONE;
            if (flow != InventoryFace.FLOW_NONE) {
                chevron(cx + 1, cy + 1, flow == InventoryFace.FLOW_UP,
                        flow == InventoryFace.FLOW_UP ? FaceInks.GREEN.bright : FaceInks.AMBER.bright);
            }
        }
        int y = l.bottom() + 7;
        fillLine(y, f.fill);
        final boolean compact = H - y < SightGlassLayout.FULL_FOOT;
        final boolean gathering = f.gatherSeconds >= 0;
        if (!compact) {
            y += 16;
            if (!gathering) {
                movers(y - 2, f);
            }
            y += 38;
        } else {
            y += 14;
        }
        if (gathering) {
            final int gy = compact ? y : y - 26;
            glowCentre(s(Component.translatable("create_stats.inventory_display.face.gathering")), gy, FaceInks.IDLE,
                    compact ? 2 : 3);
            g.base.textCentre(s(Component.translatable("create_stats.inventory_display.face.rates_in", clock(f.gatherSeconds))),
                    0, gy + (compact ? 18 : 27), W, FaceInks.IDLE.label, 1);
        } else {
            footerClock(y, f.clock, f.clockSeconds, !compact);
        }
        glare();
    }

    private void movers(int y, InventoryFace f) {
        for (int row = 0; row < 2; row++) {
            final boolean up = row == 0;
            final List<InventoryFace.Mover> items = up ? f.risers : f.fallers;
            final FaceInks ink = up ? FaceInks.GREEN : FaceInks.AMBER;
            final int yy = y + row * 18;
            chevron(6, yy + 6, up, ink.bright);
            final String total = rate(up ? f.inPerMinute : f.outPerMinute);
            g.base.text(up ? s(Component.translatable("create_stats.inventory_display.face.in_rate", total))
                    : s(Component.translatable("create_stats.inventory_display.face.out_rate", total)), 14, yy + 5, ink.main, 1);
            int x = 82;
            for (int i = 0; i < items.size() && i < 2; i++) {
                icon(items.get(i).icon(), x, yy, 16);
                g.base.text(rate(items.get(i).perMinute()), x + 19, yy + 5, ink.label, 1);
                x += 72;
            }
            if (items.isEmpty()) {
                g.base.text(s(Component.translatable("create_stats.inventory_display.face.nothing")), x, yy + 5,
                        FaceInks.IDLE.label, 1);
            }
        }
    }

    private void vault(InventoryFace f) {
        header(viaLink(f), title(f), viaLink(f) ? linkReading(f)
                : s(Component.translatable("create_stats.inventory_display.face.types", Integer.toString(f.totalTypes))),
                FaceInks.GREEN, FaceInks.GREEN);
        final SightGlassLayout.Layout l = SightGlassLayout.forSlots(Integer.MAX_VALUE);
        final boolean more = f.totalTypes > l.shown();
        final int shown = Math.min(f.tiles.size(), l.shown() - (more ? 1 : 0));
        for (int i = 0; i < shown; i++) {
            final int cx = l.cellX(i);
            final int cy = l.cellY(i);
            well(cx, cy, cx + l.cellW(), cy + l.cellH(), FaceInks.GREEN);
            icon(f.tiles.get(i).icon(), cx + 3, cy + 3, 16);
            g.base.textRight(FaceFormat.compact(f.tiles.get(i).count()), cx + l.cellW() - 4, cy + 8, FaceInks.GREEN.main, 1);
        }
        if (more) {
            final int i = l.shown() - 1;
            final int cx = l.cellX(i);
            final int cy = l.cellY(i);
            well(cx, cy, cx + l.cellW(), cy + l.cellH(), FaceInks.IDLE);
            g.base.textCentre(s(Component.translatable("create_stats.inventory_display.face.more",
                    Integer.toString(f.totalTypes - shown))), cx, cy + 8, l.cellW(), FaceInks.IDLE.main, 1);
        }
        int y = l.y0() + l.rows() * (l.cellH() + l.gap()) + 5;
        fillLine(y, f.fill);
        y += 16;
        if (f.gatherSeconds >= 0) {
            glowCentre(s(Component.translatable("create_stats.inventory_display.face.gathering")), y + 4, FaceInks.IDLE, 2);
            g.base.textCentre(s(Component.translatable("create_stats.inventory_display.face.rates_in", clock(f.gatherSeconds))),
                    0, y + 22, W, FaceInks.IDLE.label, 1);
        } else {
            flowLine(y, f.inPerMinute, f.outPerMinute);
            y += 15;
            footerClock(y, f.clock, f.clockSeconds, true);
        }
        glare();
    }

    private void watchOne(InventoryFace f) {
        final InventoryFace.WatchRow r = f.rows.get(0);
        final FaceInks ink = r.state() == InventoryFace.STATE_OK ? FaceInks.GREEN : FaceInks.AMBER;
        header(viaLink(f), title(f), s(Component.translatable("create_stats.inventory_display.face.watching")),
                FaceInks.GREEN, FaceInks.GREEN);
        well(8, 24, 48, 64, ink);
        icon(r.icon(), 12, 28, 32);
        glow(FaceFormat.grouped(r.count()), 58, 26, ink, 3);
        g.base.text(FaceFormat.trimToGlass(glassSafe(r.name().getString()), W - 30 - 58), 58, 53, ink.label, 1);
        pip(W - 20, 28, ink);
        final int y = 74;
        final double top = Math.max(Math.max(r.high() * 1.25, r.count() * 1.05), 1.0);
        bar(8, y, W - 8, y + 12, r.count() / top, ink, new double[]{r.low() / top, r.high() / top},
                new FaceInks[]{FaceInks.AMBER, FaceInks.AMBER});
        final int lx = 8 + pyRound((W - 16) * r.low() / top);
        final int hx = 8 + pyRound((W - 16) * r.high() / top);
        final String low = s(Component.translatable("create_stats.inventory_display.face.mark_low", FaceFormat.compact(r.low())));
        final String high = s(Component.translatable("create_stats.inventory_display.face.mark_high", FaceFormat.compact(r.high())));
        final int lowX = Math.max(8, lx - 22);
        g.base.text(low, lowX, y + 17, FaceInks.AMBER.label, 1);
        final int highRight = Math.min(W - 8, hx + 30);

        final int highX = Math.max(highRight - GlassCanvas.width(high, 1), lowX + GlassCanvas.width(low, 1) + 6);
        g.base.text(high, Math.min(highX, W - 8 - GlassCanvas.width(high, 1)), y + 17, FaceInks.AMBER.label, 1);
        if (f.gatherSeconds >= 0) {
            verdict(0, null, s(Component.translatable("create_stats.inventory_display.face.gathering")), FaceInks.IDLE,
                    s(Component.translatable("create_stats.inventory_display.face.rates_in", clock(f.gatherSeconds))), W);
        } else {
            flowLine(108, r.inPerMinute(), r.outPerMinute());
            final boolean shortClock = (r.clock() == FlowClock.Kind.LOW || r.clock() == FlowClock.Kind.EMPTY)
                    && r.clockSeconds() < 900.0f;
            final FaceInks vink = shortClock ? FaceInks.AMBER : ink;
            if (r.clock() == FlowClock.Kind.STEADY || r.clock() == FlowClock.Kind.NONE) {
                verdict(0, null, s(Component.translatable("create_stats.inventory_display.face.steady")), vink,
                        s(Component.translatable("create_stats.inventory_display.face.nothing_moving")), W);
            } else {
                verdict(0, clockWord(r.clock()), clock(r.clockSeconds()), vink,
                        s(Component.translatable("create_stats.inventory_display.face.at_this_rate")), W);
            }
        }
        glare();
    }

    private void watchMany(InventoryFace f) {
        header(viaLink(f), title(f), s(Component.translatable("create_stats.inventory_display.face.watching_n",
                Integer.toString(f.rows.size()))), FaceInks.GREEN, FaceInks.GREEN);
        int y = 20;
        for (InventoryFace.WatchRow r : f.rows) {
            final FaceInks ink = r.state() == InventoryFace.STATE_OK ? FaceInks.GREEN : FaceInks.AMBER;
            well(6, y, 30, y + 24, ink);
            icon(r.icon(), 10, y + 4, 16);
            final String n = FaceFormat.grouped(r.count());
            final int nameMax = (W - 20 - GlassCanvas.width(n, 2)) - 36 - 6;
            g.base.text(FaceFormat.trimToGlass(glassSafe(r.name().getString()), nameMax), 36, y + 2, ink.label, 1);
            glowRight(n, W - 20, y, ink, 2);
            pip(W - 14, y + 3, ink);
            final double top = Math.max(Math.max(r.high() * 1.25, r.count() * 1.05), 1.0);
            bar(36, y + 18, W - 8, y + 23, r.count() / top, ink, new double[]{r.low() / top, r.high() / top},
                    new FaceInks[]{FaceInks.AMBER, FaceInks.AMBER});
            final double net = r.netPerMinute();
            final String netText = f.gatherSeconds >= 0 ? s(Component.translatable("create_stats.inventory_display.face.gathering"))
                    : Math.abs(net) < 0.05 ? s(Component.translatable("create_stats.inventory_display.face.steady"))
                    : (net > 0 ? "+" : "-") + rate(Math.abs(net));
            g.base.text(netText, 36, y + 30, ink.main, 1);
            final String rightText;
            if (r.state() == InventoryFace.STATE_HIGH) {
                rightText = s(Component.translatable("create_stats.inventory_display.face.above_high"));
            } else if (r.state() == InventoryFace.STATE_LOW) {
                rightText = s(Component.translatable("create_stats.inventory_display.face.below_low"));
            } else if (r.clock() != FlowClock.Kind.STEADY && r.clock() != FlowClock.Kind.NONE && f.gatherSeconds < 0) {
                rightText = clockLine(r.clock(), r.clockSeconds());
            } else {
                rightText = "";
            }
            g.base.textRight(rightText, W - 8, y + 30, ink.main, 1);
            y += 50;
            if (y < 170) {
                rule(6, W - 6, y - 6, FaceInks.GREEN.faint);
            }
        }
        glare();
    }

    private void tank(InventoryFace f) {
        final boolean diesel = f.page == InventoryFace.Page.DIESEL;
        final String fluidName = f.fluid.isEmpty() ? "" : glassSafe(f.fluid.getHoverName().getString());
        String right = fluidName;
        if (f.boilerLevel >= 0) {
            right = s(Component.translatable("create_stats.inventory_display.face.level", Integer.toString(f.boilerLevel)));
        }
        if (viaLink(f)) {
            right = linkReading(f);
        }
        header(viaLink(f), title(f), right, FaceInks.GREEN, FaceInks.GREEN);
        final int tx0 = 10, ty0 = 22, tx1 = 58, ty1 = 184;
        g.base.fill(tx0 - 2, ty0 - 2, tx1 + 2, ty1 + 2, FaceInks.GREEN.faint);
        g.base.fill(tx0, ty0, tx1, ty1, FaceInks.GLASS_DEEP);
        final double frac = f.fluidCapacity > 0 ? Math.max(0.0, Math.min(1.0, f.fluidAmount / (double) f.fluidCapacity)) : 0.0;
        final int level = ty1 - pyRound((ty1 - ty0) * frac);
        if (!f.fluid.isEmpty() && level < ty1) {
            g.fluid = new int[]{tx0, level, tx1, ty1};
        } else if (diesel && level < ty1) {
            g.base.fill(tx0, level, tx1, ty1, FaceInks.AMBER.dim);
            g.base.fill(tx0, level, tx1, level + 1, FaceInks.AMBER.mid);
        }
        final int steps = f.cauldron || f.fluidCapacity <= 1000 ? 3 : 8;
        for (int i = 0; i <= steps; i++) {
            final int y = ty1 - pyRound((ty1 - ty0) * i / (double) steps);
            final boolean longMark = i % 2 == 0 || steps == 3;
            g.over.fill(tx1 - (longMark ? 14 : 7), y, tx1, y + 1, FaceInks.GREEN.bright);
            if (steps == 8 && longMark && i > 0 && i < 8) {
                final String lbl = FaceFormat.compact(Math.round(f.fluidCapacity * i / 8.0 / 1000.0));
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    g.over.text(lbl, tx0 + 3 + d[0], y - 3 + d[1], FaceInks.GLASS_DEEP, 1);
                }
                g.over.text(lbl, tx0 + 3, y - 3, FaceInks.GREEN.bright, 1);
            }
        }
        final int x = 70;
        glow(FaceFormat.buckets(f.fluidAmount) + " B", x, 24, FaceInks.GREEN, 3);
        g.base.text(s(Component.translatable("create_stats.inventory_display.face.of", FaceFormat.buckets(f.fluidCapacity) + " B")),
                x, 53, FaceInks.GREEN.label, 1);
        glowRight(FaceFormat.percent(frac), W - 8, 49, FaceInks.GREEN, 2);
        if (f.gatherSeconds < 0) {
            g.base.text(s(Component.translatable("create_stats.inventory_display.face.in")), x, 74, FaceInks.GREEN.label, 1);
            g.base.text(s(Component.translatable("create_stats.inventory_display.face.buckets_per_min",
                    FaceFormat.buckets(Math.round(f.inPerMinute)))), x + 24, 74, FaceInks.GREEN.main, 1);
            g.base.text(s(Component.translatable("create_stats.inventory_display.face.out")), x, 88, FaceInks.GREEN.label, 1);
            g.base.text(s(Component.translatable("create_stats.inventory_display.face.buckets_per_min",
                    FaceFormat.buckets(Math.round(f.outPerMinute)))), x + 24, 88, FaceInks.GREEN.main, 1);
        }
        if (f.boilerLevel >= 0) {
            g.base.text(s(Component.translatable("create_stats.inventory_display.face.heat")), x, 103, FaceInks.AMBER.label, 1);
            for (int i = 0; i < 18; i++) {
                g.base.fill(x + 28 + i * 6, 103, x + 32 + i * 6, 110, i < f.boilerLevel ? FaceInks.AMBER.main : FaceInks.AMBER.faint);
            }
            g.base.text(s(Component.translatable("create_stats.inventory_display.face.engines", Integer.toString(f.engines)))
                    + "  " + s(Component.translatable("create_stats.inventory_display.face.whistles", Integer.toString(f.whistles))),
                    x, 114, FaceInks.GREEN.main, 1);
        }
        if (diesel) {
            final String state = f.dieselState == 2 ? s(Component.translatable("create_stats.inventory_display.face.nothing"))
                    : f.dieselRpm > 0.01f ? Math.round(f.dieselRpm) + " RPM"
                    : s(Component.translatable("create_stats.inventory_display.face.steady"));
            g.base.text(state, x, 103, f.dieselState == 2 ? FaceInks.AMBER.main : FaceInks.GREEN.main, 1);
        }
        if (f.gatherSeconds >= 0) {
            verdict(x, null, s(Component.translatable("create_stats.inventory_display.face.gathering")), FaceInks.IDLE,
                    s(Component.translatable("create_stats.inventory_display.face.rates_in", clock(f.gatherSeconds))), 0);
        } else if (f.clock == FlowClock.Kind.STEADY || f.clock == FlowClock.Kind.NONE) {
            verdict(x, null, s(Component.translatable("create_stats.inventory_display.face.steady")), FaceInks.GREEN,
                    s(Component.translatable("create_stats.inventory_display.face.nothing_moving")), 0);
        } else {
            verdict(x, clockWord(f.clock), clock(f.clockSeconds), clockInk(f.clock, f.clockSeconds),
                    s(Component.translatable("create_stats.inventory_display.face.at_this_rate")), 0);
        }
        glare();
    }

    private void energy(InventoryFace f) {
        final EnergyReadout.Count kind = EnergyReadout.Count.values()[Math.max(0, Math.min(2, f.energyCountKind))];
        String right = kind == EnergyReadout.Count.BLOCKS
                ? s(Component.translatable("create_stats.inventory_display.face.blocks", Long.toString(f.energyCount)))
                : f.energyGrid ? s(Component.translatable("create_stats.inventory_display.face.grid")) : "";
        if (viaLink(f)) {
            right = linkReading(f);
        }
        header(viaLink(f), title(f), right, FaceInks.GREEN, FaceInks.GREEN);
        if (!f.energyStored) {
            // not published = not drawn. never drawn as zero

            if (f.energyFlows) {
                glow(feT(f.energyIn), 10, 24, FaceInks.GREEN, 3);
                energyRows(f, 10);
            }
            glare();
            return;
        }
        final int cx0 = 10, cy0 = 22, cx1 = 50, cy1 = 184;
        g.base.fill(cx0 - 2, cy0 - 2, cx1 + 2, cy1 + 2, FaceInks.GREEN.faint);
        final double frac = f.energyCapacity > 0 ? Math.max(0.0, Math.min(1.0, f.energy / (double) f.energyCapacity)) : 0.0;
        final int segs = 12;
        final double sh = (cy1 - cy0 - (segs - 1) * 2) / (double) segs;
        final int lit = (int) Math.ceil(frac * segs - 1e-6);
        for (int i = 0; i < segs; i++) {
            final int y1 = cy1 - pyRound(i * (sh + 2));
            final int y0 = pyRound(y1 - sh);
            final boolean on = i < lit;
            g.base.fill(cx0, y0, cx1, y1, on ? FaceInks.GREEN.main : FaceInks.GLASS_DEEP);
            if (on) {
                g.base.fill(cx0, y0, cx1, y0 + 1, FaceInks.GREEN.bright);
                g.base.fill(cx0, y1 - 2, cx1, y1, FaceInks.GREEN.mid);
            }
        }
        final int x = 62;
        glow(FaceFormat.fe(f.energy), x, 24, FaceInks.GREEN, 3);
        g.base.text(s(Component.translatable("create_stats.inventory_display.face.of", FaceFormat.fe(f.energyCapacity))), x, 53,
                FaceInks.GREEN.label, 1);
        glowRight(FaceFormat.percent(frac), W - 8, 49, FaceInks.GREEN, 2);
        if (f.energyFlows) {
            energyRows(f, x);
        }

        if (f.energyClock != EnergyReadout.Clock.NONE.ordinal()) {
            energyVerdict(f, x);
        }
        glare();
    }

    private void energyVerdict(InventoryFace f, int x) {
        if (f.gatherSeconds >= 0) {
            verdict(x, null, s(Component.translatable("create_stats.inventory_display.face.gathering")), FaceInks.IDLE,
                    s(Component.translatable("create_stats.inventory_display.face.rates_in", clock(f.gatherSeconds))), 0);
        } else if (f.clock == FlowClock.Kind.STEADY || f.clock == FlowClock.Kind.NONE) {
            verdict(x, null, s(Component.translatable("create_stats.inventory_display.face.steady")), FaceInks.GREEN,
                    s(Component.translatable("create_stats.inventory_display.face.nothing_moving")), 0);
        } else {
            final FaceInks ink = f.clock == FlowClock.Kind.RUNTIME && f.clockSeconds <= 300.0f ? FaceInks.AMBER : FaceInks.GREEN;

            final String caption = f.energyClock == EnergyReadout.Clock.QUOTED.ordinal()
                    ? s(Component.translatable("create_stats.inventory_display.face.as_forecast"))
                    : s(Component.translatable("create_stats.inventory_display.face.at_this_rate"));
            verdict(x, clockWord(f.clock), clock(f.clockSeconds), ink, caption, 0);
        }
    }

    private static String feT(long perTick) {
        return s(Component.translatable("create_stats.inventory_display.face.fe_per_tick", FaceFormat.grouped(perTick)));
    }

    private void energyRows(InventoryFace f, int x) {
        g.base.text(s(Component.translatable("create_stats.inventory_display.face.in")), x, 72, FaceInks.GREEN.label, 1);
        g.base.text(feT(f.energyIn), x + 24, 72, FaceInks.GREEN.main, 1);
        g.base.text(s(Component.translatable("create_stats.inventory_display.face.out")), x, 86, FaceInks.GREEN.label, 1);
        g.base.text(feT(f.energyOut), x + 24, 86, FaceInks.GREEN.main, 1);
        final long net = f.energyIn - f.energyOut;
        g.base.text(s(Component.translatable("create_stats.inventory_display.face.net")), x, 100, FaceInks.GREEN.label, 1);
        g.base.text((net > 0 ? "+" : "") + feT(net), x + 24, 100, net >= 0 ? FaceInks.GREEN.main : FaceInks.AMBER.main, 1);
    }

    private void belt(InventoryFace f) {
        header(viaLink(f), title(f), s(Component.translatable("create_stats.inventory_display.face.on_belt",
                Integer.toString(f.itemsOnBelt))), FaceInks.GREEN, FaceInks.GREEN);
        final int x0 = 4, y0 = 26, x1 = W - 4, y1 = 54;
        g.base.fill(x0 - 1, y0 - 2, x1 + 1, y1 + 2, FaceInks.GREEN.faint);
        g.belt = new int[]{x0, y0, x1, y1};
        final int gx = (x0 + x1) / 2;
        for (int y = y0 - 4; y < y1 + 4; y += 2) {
            g.over.fill(gx, y, gx + 1, y + 1, FaceInks.GREEN.bright);
        }
        g.over.text(s(Component.translatable("create_stats.inventory_display.face.gate")), gx - 14, y1 + 6, FaceInks.GREEN.label, 1);
        int y = 70;
        for (int i = 0; i < f.passing.size() && i < 3; i++) {
            final InventoryFace.Mover m = f.passing.get(i);
            well(8, y, 26, y + 18, FaceInks.GREEN);
            icon(m.icon(), 9, y + 1, 16);
            final String r = rate(m.perMinute());
            final int nameMax = W - 8 - GlassCanvas.width(r, 1) - 6 - 32;
            g.base.text(FaceFormat.trimToGlass(glassSafe(m.icon().getHoverName().getString()), nameMax), 32, y + 6,
                    FaceInks.GREEN.label, 1);
            g.base.textRight(r, W - 8, y + 6, m.perMinute() > 0.0f ? FaceInks.GREEN.main : FaceInks.IDLE.main, 1);
            y += 19;
        }
        if (f.stalledSeconds >= 0) {
            verdict(0, s(Component.translatable("create_stats.inventory_display.face.stalled")), clock(f.stalledSeconds),
                    FaceInks.RED, s(Component.translatable("create_stats.inventory_display.face.nothing_passed")), W);
        } else if (f.gatherSeconds >= 0) {
            verdict(0, null, s(Component.translatable("create_stats.inventory_display.face.gathering")), FaceInks.IDLE,
                    s(Component.translatable("create_stats.inventory_display.face.rates_in", clock(f.gatherSeconds))), W);
        } else {
            verdict(0, s(Component.translatable("create_stats.inventory_display.face.passing")), rate(f.passPerMinute),
                    FaceInks.GREEN, s(Component.translatable("create_stats.inventory_display.face.passing_line")), W);
        }
        glare();
    }

    private void feed(InventoryFace f) {
        header(true, s(Component.translatable("create_stats.inventory_display.face.display_link")), title(f), FaceInks.GREEN,
                FaceInks.GREEN);
        int y = 24;
        for (int i = 0; i < f.lines.size() && i < 8; i++) {
            final String line = glassSafe(f.lines.get(i));
            g.base.text(line.length() > 36 ? line.substring(0, 36) : line, 8, y, FaceInks.GREEN.main, 1);
            y += 12;
        }
        glare();
    }

    private void outOfReach(InventoryFace f) {
        final List<String> lines = new ArrayList<>();
        if (f.otherDimension) {
            lines.addAll(wrap(s(Component.translatable("create_stats.inventory_display.face.other_dimension")), 36));
        } else {
            lines.add(s(Component.translatable("create_stats.inventory_display.face.out_of_reach.1", Integer.toString(f.linkDistance))));
            lines.add(s(Component.translatable("create_stats.inventory_display.face.out_of_reach.2", Integer.toString(f.baseReach))));
            if (f.neededTier > 0) {
                lines.add(s(Component.translatable("create_stats.inventory_display.face.out_of_reach.3", tierName(f.neededTier))));
                lines.add(s(Component.translatable("create_stats.inventory_display.face.out_of_reach.4", Integer.toString(f.neededReach))));
            }
        }
        message(true, title(f), s(Component.translatable("create_stats.inventory_display.face.out_of_reach")), lines,
                FaceInks.RED, f.linkDistance >= 0 ? s(Component.translatable("create_stats.inventory_display.face.link_short",
                        Integer.toString(f.linkDistance))) : "");
    }

    private static String tierName(int ordinal) {
        return switch (ordinal) {
            case 1 -> s(Component.translatable("create_stats.inventory_display.antenna.small"));
            case 2 -> s(Component.translatable("create_stats.inventory_display.antenna.advanced"));
            default -> s(Component.translatable("create_stats.inventory_display.antenna.trt"));
        };
    }

    static List<String> wrap(String text, int width) {
        final List<String> out = new ArrayList<>();
        final StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    private void message(boolean link, String title, String big, List<String> lines, FaceInks ink, String right) {
        header(link, title, right, ink, ink == FaceInks.RED ? FaceInks.IDLE : ink);
        final List<String> words = new ArrayList<>();
        for (String w : big.split("\n")) {
            if (GlassCanvas.width(w, 3) > W - 24 && w.contains(" ")) {
                int best = -1;
                int bestW = Integer.MAX_VALUE;
                for (int i = 0; i < w.length(); i++) {
                    if (w.charAt(i) == ' ') {
                        final int m = Math.max(GlassCanvas.width(w.substring(0, i), 3), GlassCanvas.width(w.substring(i + 1), 3));
                        if (m < bestW) {
                            bestW = m;
                            best = i;
                        }
                    }
                }
                words.add(w.substring(0, best));
                words.add(w.substring(best + 1));
            } else {
                words.add(w);
            }
        }
        final int step = 7 * 3 + 4;
        int y = 74 - (words.size() - 1) * step / 2;
        for (int i = 0; i < words.size(); i++) {
            glowCentre(words.get(i), y + i * step, ink, 3);
        }
        y += step * words.size() + 8;
        for (String ln : lines) {
            g.base.textCentre(ln, 0, y, W, ink == FaceInks.RED ? FaceInks.IDLE.main : ink.label, 1);
            y += 11;
        }
        glare();
    }
}
