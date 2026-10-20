package com.createstats.vccmk4.core;

import java.util.ArrayList;
import java.util.List;

public final class JoinEscapeFile {
    public static final String NAME = "create_stats-join-escape.txt";
    public static final String DEFAULT = "# Create: Stats & Power join escape.\n"
            + "# While this file exists, joining any server presses sneak (off a seat) and sends these commands at once.\n"
            + "# One command a line, no slash needed. Delete this file to turn the escape off.\n"
            + "spawn\n";
    static final int MAX = 4;
    static final int MAX_LENGTH = 256;

    private JoinEscapeFile() {
    }

    public static List<String> commands(List<String> lines) {
        List<String> out = new ArrayList<>();
        if (lines == null) return out;
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.strip();
            if (line.startsWith("﻿")) line = line.substring(1).strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            while (line.startsWith("/")) line = line.substring(1).strip();
            if (line.isEmpty() || line.length() > MAX_LENGTH) continue;
            out.add(line);
            if (out.size() >= MAX) break;
        }
        return out;
    }
}
