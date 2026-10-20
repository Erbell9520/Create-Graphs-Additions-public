package com.createstats.efficiency;

public final class GradeScale {
    public static final float HYSTERESIS = 0.6f;

    private static final Grade[] GRADES = {
            new Grade("A+", 95.0f, Tier.A, "Factory singing"),
            new Grade("A",  90.0f, Tier.A, "Very healthy"),
            new Grade("A-", 85.0f, Tier.A, "Running strong"),
            new Grade("B+", 80.0f, Tier.B, "Good load"),
            new Grade("B",  75.0f, Tier.B, "Steady"),
            new Grade("B-", 70.0f, Tier.B, "Usable"),
            new Grade("C+", 65.0f, Tier.C, "Working hard"),
            new Grade("C",  60.0f, Tier.C, "Tight margin"),
            new Grade("C-", 55.0f, Tier.C, "Shaky"),
            new Grade("D+", 50.0f, Tier.D, "Strained"),
            new Grade("D",  45.0f, Tier.D, "Near failure"),
            new Grade("D-", 40.0f, Tier.D, "Barely holding"),
            new Grade("F",   0.0f, Tier.F, "Overstressed"),
    };

    public static final String UNKNOWN_LETTER = "?";

    public static final int UNKNOWN_COLOR = 0xFF808080;

    private GradeScale() {
    }

    private enum Tier {
        A(0xFF00FF88),
        B(0xFFCCCC00),
        C(0xFFFF8800),
        D(0xFFFF4400),
        F(0xFFFF0000);

        final int color;

        Tier(int color) {
            this.color = color;
        }
    }

    private record Grade(String letter, float min, Tier tier, String phrase) {
    }

    public static int count() {
        return GRADES.length;
    }

    public static String letterAt(int index) {
        return GRADES[clampIndex(index)].letter();
    }

    public static int colorAt(int index) {
        return GRADES[clampIndex(index)].tier().color;
    }

    public static String phraseAt(int index) {
        return GRADES[clampIndex(index)].phrase();
    }

    public static float minScoreAt(int index) {
        return GRADES[clampIndex(index)].min();
    }

    public static String phraseFor(String letter) {
        for (Grade g : GRADES) {
            if (g.letter().equals(letter)) {
                return g.phrase();
            }
        }
        return "";
    }

    public static int indexFor(float score) {
        for (int i = 0; i < GRADES.length; i++) {
            if (score >= GRADES[i].min()) {
                return i;
            }
        }
        return GRADES.length - 1;
    }

    public static int resolve(float score, int previous) {
        if (previous < 0 || previous >= GRADES.length) {
            return indexFor(score);
        }
        final float floor = GRADES[previous].min();
        if (score >= floor && (previous == 0 || score < GRADES[previous - 1].min() + HYSTERESIS)) {
            return previous;
        }
        if (score >= floor) {
            for (int i = 0; i < previous; i++) {
                if (score >= GRADES[i].min() + HYSTERESIS) {
                    return i;
                }
            }
            return previous;
        }

        if (score > floor - HYSTERESIS) {
            return previous;
        }
        return indexFor(score);
    }

    private static int clampIndex(int index) {
        return Math.max(0, Math.min(GRADES.length - 1, index));
    }
}
