package com.createstats.chunkloader.map;

import com.createstats.chunkloader.core.CrownBoard;
import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownLaw;
import com.createstats.chunkloader.core.CrownState;
import com.createstats.gui.meter.plate.MeterText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CrownPlate {
    public static final String[] LEGENDS = {"CHUNKS", "DRAW", "POWER", "FEED"};

    public static final int HEIGHT = 21;

    public static final int BAR_WIDE = 36;
    public static final int BAR_NARROW = 24;

    public static final int NARROW_BELOW = 768;

    public static final int PITCH = 6;

    public enum Ink {
        GREEN, AMBER, RED, IDLE
    }

    public record Seg(String text, boolean figure) {
    }

    public record Field(String legend, Ink ink, List<Seg> segs, double bar) {
        public boolean hasBar() {
            return !Double.isNaN(bar);
        }

        public String text() {
            final StringBuilder b = new StringBuilder();
            for (Seg s : segs) {
                b.append(s.text());
            }
            return b.toString();
        }
    }

    public record Window(int legendX, int x0, int x1, int textX, int room, int barX, int barWidth) {
    }

    private CrownPlate() {
    }

    public static boolean narrow(int screenWidth) {
        return screenWidth < NARROW_BELOW;
    }

    public static Window window(int width, int index, int legendWidth, boolean bar, boolean narrow) {
        final int colw = width / 4;
        final int fx = index * colw + 5;
        final int x0 = fx + legendWidth + 5;
        final int x1 = (index + 1) * colw - 6;
        final int textX = x0 + 4;
        final int bw = narrow ? BAR_NARROW : BAR_WIDE;
        final int room = bar ? (x1 - 4) - bw - 4 - textX : (x1 - 4) - textX;
        return new Window(fx, x0, x1, textX, room, bar ? x1 - 4 - bw : -1, bar ? bw : 0);
    }

    public static int advance(List<Seg> segs) {
        int n = 0;
        for (Seg s : segs) {
            n += s.text().length();
        }
        return n * PITCH;
    }

    public static List<Field> layout(CrownState s, CrownBoard b, CrownLaw law, int width, int[] legendWidths) {
        final boolean narrow = narrow(width);
        final List<Field> chosen = fields(s, b, law, narrow);
        final List<Field> shortWords = narrow ? chosen : fields(s, b, law, true);
        final List<Field> out = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            Field f = chosen.get(i);
            final Window w = window(width, i, legendWidths[i], f.hasBar(), narrow);

            if (!f.hasBar() && advance(f.segs()) > w.room()) {
                f = shortWords.get(i);
            }
            out.add(new Field(f.legend(), f.ink(), fit(f.segs(), w.room()), f.bar()));
        }
        return out;
    }

    public static List<Seg> fit(List<Seg> segs, int room) {
        List<Seg> out = segs;
        if (advance(out) > room) {
            final List<Seg> noUnit = new ArrayList<>();
            for (Seg s : out) {
                noUnit.add(s.figure() ? s : new Seg(s.text().replace(" FE/t ", " ").replace(" FE/t", ""), false));
            }
            out = noUnit;
        }
        if (advance(out) > room) {
            final List<Seg> compact = new ArrayList<>();
            for (Seg s : out) {
                final String digits = s.text().replace(",", "");
                compact.add(s.figure() && digits.length() >= 5 && digits.chars().allMatch(Character::isDigit)
                        ? new Seg(MeterText.compact(Long.parseLong(digits)), true) : s);
            }
            out = compact;
        }
        return out;
    }

    public static List<Field> fields(CrownState s, CrownBoard b, CrownLaw law, boolean narrow) {
        final String full = s.picked() >= s.maxPerStation() || s.ownerTotal() >= s.maxPerPlayer() ? null
                : group(s.nextPriceFePerTick());
        final Field feed = feed(s, b, narrow);
        final List<Seg> chunks = List.of(fig(s.held() + "/" + s.maxPerStation()), word(" YOURS "),
                fig(s.ownerTotal() + "/" + s.maxPerPlayer()));
        final String unit = narrow ? " " : " FE/t ";
        final String nextWord = narrow ? "+" : "NEXT +";

        if (blocked(s)) {
            return List.of(new Field("CHUNKS", Ink.RED, chunks, Double.NaN),
                    new Field("DRAW", Ink.RED, draw("0", unit, nextWord, full), 0.0),
                    new Field("POWER", Ink.RED, List.of(word(blockedWord(s.reason()))), Double.NaN), feed);
        }
        if (charging(s)) {
            return List.of(new Field("CHUNKS", Ink.IDLE, chunks, Double.NaN),
                    new Field("DRAW", Ink.IDLE, List.of(fig("0"), word(unit), word("FIRST "),
                            fig(group(law.totalDraw(s.held() + 1)))), 0.0),
                    new Field("POWER", Ink.IDLE, List.of(word("CHARGING "), fig(seconds(chargeSeconds(s, law))),
                            word(narrow ? " s" : " OF " + seconds(law.rideSeconds()) + " s")), Double.NaN), feed);
        }
        if (s.held() == 0) {
            return List.of(new Field("CHUNKS", Ink.RED, chunks, Double.NaN),
                    new Field("DRAW", Ink.RED, draw("0", unit, nextWord, full), 0.0),
                    new Field("POWER", Ink.RED, List.of(word("DARK  "), fig(String.valueOf(s.picked())),
                            word(" WAITING")), Double.NaN), feed);
        }
        final double bar = barFraction(s, b);
        final Field draw = new Field("DRAW", Ink.GREEN, draw(group(s.drawFePerTick()), unit, nextWord, full), bar);
        if (s.phase() == CrownHold.Phase.RIDING) {
            return List.of(new Field("CHUNKS", Ink.GREEN, chunks, Double.NaN), draw,
                    new Field("POWER", Ink.AMBER, List.of(word(narrow ? "RIDE " : "RIDING "),
                            fig(seconds(rideSeconds(s))), word(narrow ? " s" : " s LEFT")), Double.NaN), feed);
        }
        return List.of(new Field("CHUNKS", Ink.GREEN, chunks, Double.NaN), draw,
                new Field("POWER", Ink.GREEN, List.of(word("FED  "), word(narrow ? "RIDE " : "RIDE-THROUGH "),
                        fig(seconds(rideSeconds(s))), word(" s")), Double.NaN), feed);
    }

    private static List<Seg> draw(String figure, String unit, String nextWord, String next) {
        final List<Seg> out = new ArrayList<>(List.of(fig(figure), word(unit)));
        if (next == null) {
            out.add(word("FULL"));
        } else {
            out.add(word(nextWord));
            out.add(fig(next));
        }
        return out;
    }

    static Field feed(CrownState s, CrownBoard b, boolean narrow) {
        if (charging(s) || (s.held() > 0 && s.phase() != CrownHold.Phase.RIDING) || blocked(s)) {
            return calmFeed(s, b, narrow);
        }
        if (s.held() == 0) {
            final List<Seg> segs = new ArrayList<>(List.of(word("SHORT "),
                    fig(group(Math.max(0, (long) s.pickedDrawFePerTick() - s.intakeFePerTick())))));
            if (!narrow) {
                segs.add(word(" FE/t"));
            }
            return new Field("FEED", Ink.RED, segs, Double.NaN);
        }
        final List<Seg> segs = new ArrayList<>();
        final int open = openStud(b);
        if (open > 0 && !narrow) {
            segs.add(word("STUD " + open + " OPEN  "));
        }
        segs.add(word("SHORT "));
        segs.add(fig(group(Math.max(0, (long) s.drawFePerTick() - s.intakeFePerTick()))));
        return new Field("FEED", Ink.AMBER, segs, Double.NaN);
    }

    private static Field calmFeed(CrownState s, CrownBoard b, boolean narrow) {
        final List<Seg> segs = new ArrayList<>();
        if (!b.present()) {
            segs.add(word("STUDS "));
            segs.add(fig(studs(s.studAWired(), s.studBWired(), narrow)));
            segs.add(word(narrow ? " IN " : "  IN "));
            segs.add(fig(group(s.intakeFePerTick())));
            return new Field("FEED", Ink.GREEN, segs, Double.NaN);
        }
        segs.add(word("PDU "));
        segs.add(fig(legs(b, narrow)));
        if (b.covers() && s.picked() < s.maxPerStation()
                && (long) s.pickedDrawFePerTick() + s.nextPriceFePerTick() > b.carried()) {
            segs.add(word(narrow ? " " : "  CARRY "));
            segs.add(fig(group(b.carried())));
            return new Field("FEED", Ink.GREEN, segs, Double.NaN);
        }
        if (b.onBanks()) {
            segs.add(word(narrow ? " BANKS " : "  BANKS "));
            segs.add(fig(group(b.banks())));
            return new Field("FEED", Ink.AMBER, segs, Double.NaN);
        }
        segs.add(word(narrow ? " GEN OK" : "  GENERATION OK"));
        return new Field("FEED", Ink.GREEN, segs, Double.NaN);
    }

    static String legs(CrownBoard b, boolean narrow) {
        final int a = b.legA();
        final int c = b.legB();
        if (a >= 0 && c >= 0 && a != c) {
            return (a + 1) + (narrow ? "+" : " + ") + (c + 1);
        }
        return String.valueOf(Math.max(a, c) + 1);
    }

    private static String studs(boolean a, boolean b, boolean narrow) {
        if (a && b) {
            return narrow ? "A+B" : "A + B";
        }
        return a ? "A" : b ? "B" : "NONE";
    }

    static int openStud(CrownBoard b) {
        if (b.legA() >= 0 && !b.liveA()) {
            return b.legA() + 1;
        }
        return b.legB() >= 0 && !b.liveB() ? b.legB() + 1 : 0;
    }

    static double barFraction(CrownState s, CrownBoard b) {
        final long carry = b.covers() ? b.carried()
                : (long) (s.studAWired() ? s.studARating() : 0) + (s.studBWired() ? s.studBRating() : 0);
        return carry <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, s.drawFePerTick() / (double) carry));
    }

    public static boolean charging(CrownState s) {
        return s.held() == 0 && s.reason() == CrownState.Reason.NO_POWER && s.intakeFePerTick() > 0;
    }

    public static boolean blocked(CrownState s) {
        return s.reason() != CrownState.Reason.NONE && s.reason() != CrownState.Reason.NO_POWER;
    }

    private static String blockedWord(CrownState.Reason r) {
        return switch (r) {
            case NO_OWNER -> "NO OWNER";
            case REGION_TAKEN -> "REGION TAKEN";
            case ON_SABLE -> "ON A SHIP";
            case SWITCHED_OFF -> "OFF AT BOARD";
            default -> "DARK";
        };
    }

    public static double rideSeconds(CrownState s) {
        if (s.drawFePerTick() <= 0) {
            return 0.0;
        }
        final long rate = s.phase() == CrownHold.Phase.RIDING
                ? Math.max(1L, (long) s.drawFePerTick() - s.intakeFePerTick()) : s.drawFePerTick();
        return s.storedFe() / (double) rate / 20.0;
    }

    public static double chargeSeconds(CrownState s, CrownLaw law) {
        final double sec = s.storedFe() / (double) Math.max(1, law.totalDraw(s.held() + 1)) / 20.0;
        return Math.min(law.rideSeconds(), sec);
    }

    public static String seconds(double s) {
        return String.format(Locale.ROOT, "%.1f", s);
    }

    public static String group(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    private static Seg fig(String t) {
        return new Seg(t, true);
    }

    private static Seg word(String t) {
        return new Seg(t, false);
    }
}
