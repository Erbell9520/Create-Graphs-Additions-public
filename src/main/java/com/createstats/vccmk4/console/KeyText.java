package com.createstats.vccmk4.console;

final class KeyText {
    private static final String[][] WHOLE = {
            {"Up Arrow", "Up"}, {"Down Arrow", "Down"}, {"Left Arrow", "Left"}, {"Right Arrow", "Right"},
            {"Page Up", "PgUp"}, {"Page Down", "PgDn"}, {"Backspace", "Bksp"}, {"Caps Lock", "Caps"},
            {"Scroll Lock", "ScrLk"}, {"Num Lock", "NumLk"}, {"Print Screen", "PrtSc"}, {"Insert", "Ins"},
            {"Delete", "Del"}, {"Escape", "Esc"}};

    private KeyText() {
    }

    static String shortName(String full) {
        if (full == null || full.isEmpty()) return "-";
        for (String[] rule : WHOLE) {
            if (full.equals(rule[0])) return rule[1];
        }
        String s = full;
        if (s.startsWith("Left ")) s = "L." + s.substring(5);
        else if (s.startsWith("Right ")) s = "R." + s.substring(6);
        if (s.startsWith("Keypad ")) s = "Num " + s.substring(7);
        if (s.startsWith("Button ")) s = "Mouse " + s.substring(7);
        return s.replace("Control", "Ctrl").replace("Enter", "Ent");
    }
}
