package com.createstats.battle.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public final class PlaceName {
    public static final int FORE_STERN = 0;
    public static final int FORE_MID = 1;
    public static final int FORE_BOW = 2;
    public static final int SIDE_PORT = 0;
    public static final int SIDE_CENTRE = 1;
    public static final int SIDE_STBD = 2;
    public static final int LEVEL_BELOW = 0;
    public static final int LEVEL_MAIN = 1;
    public static final int LEVEL_UPPER = 2;

    private PlaceName() {
    }

    public static String marker(String name) {
        return "M:" + (name == null ? "" : name);
    }

    public static String gun(int number) {
        return "G:" + number;
    }

    public static String propeller(int number) {
        return "P:" + number;
    }

    public static String envelope(int fore) {
        return "E:" + fore;
    }

    public static String ship(int fore, int side, int level) {
        return "S:" + fore + ":" + side + ":" + level;
    }

    public static String compass(int northSouth, int westEast, int level) {
        return "C:" + northSouth + ":" + westEast + ":" + level;
    }

    public static String brokeAway() {
        return "B";
    }

    public static String describe(String token, BiFunction<String, Object[], String> tr) {
        if (token == null || token.isEmpty()) return tr.apply("place.unnamed", new Object[0]);
        char kind = token.charAt(0);
        String rest = token.length() > 2 ? token.substring(2) : "";
        String[] parts = rest.split(":");
        try {
            switch (kind) {
                case 'M':
                    return rest.isEmpty() ? tr.apply("place.unnamed", new Object[0]) : rest;
                case 'G':
                    return tr.apply("place.gun", new Object[]{Integer.parseInt(parts[0])});
                case 'P':
                    return tr.apply("place.propeller", new Object[]{Integer.parseInt(parts[0])});
                case 'E':
                    return tr.apply("place.envelope." + Integer.parseInt(parts[0]), new Object[0]);
                case 'S': {
                    List<String> words = new ArrayList<>(3);
                    words.add(tr.apply("place.fore." + Integer.parseInt(parts[0]), new Object[0]));
                    int side = Integer.parseInt(parts[1]);
                    if (side != SIDE_CENTRE) words.add(tr.apply("place.side." + side, new Object[0]));
                    int level = Integer.parseInt(parts[2]);
                    if (level != LEVEL_MAIN) words.add(tr.apply("place.level." + level, new Object[0]));
                    return String.join(", ", words);
                }
                case 'C': {
                    List<String> words = new ArrayList<>(3);
                    int ns = Integer.parseInt(parts[0]);
                    int we = Integer.parseInt(parts[1]);
                    if (ns != SIDE_CENTRE || we == SIDE_CENTRE) words.add(tr.apply("place.ns." + ns, new Object[0]));
                    if (we != SIDE_CENTRE) words.add(tr.apply("place.we." + we, new Object[0]));
                    int level = Integer.parseInt(parts[2]);
                    if (level != LEVEL_MAIN) words.add(tr.apply("place.level." + level, new Object[0]));
                    return capitalise(String.join(", ", words));
                }
                case 'B':
                    return tr.apply("place.broke_away", new Object[0]);
                default:
                    return tr.apply("place.unnamed", new Object[0]);
            }
        } catch (RuntimeException e) {
            return tr.apply("place.unnamed", new Object[0]);
        }
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
