package com.createstats.battle.console;

// interface so the layout test runs with real glyph widths and the real lang file
public interface ConsoleHost {
    int width(String text);

    String tr(String key, Object... args);

    String name(String idOrName);

    void send(int action, int a, int b, String s);

    void click();
}
