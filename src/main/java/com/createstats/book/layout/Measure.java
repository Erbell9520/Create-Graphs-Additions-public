package com.createstats.book.layout;

import java.util.ArrayList;
import java.util.List;

public interface Measure {
    int width(String s, boolean bold);

    default int width(String s) {
        return width(s, false);
    }

    default List<String> wrap(String s, int max, boolean bold) {
        final List<String> lines = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return lines;
        }
        String cur = "";
        for (final String word : s.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            final String trial = cur.isEmpty() ? word : cur + " " + word;
            if (width(trial, bold) <= max) {
                cur = trial;
                continue;
            }
            if (!cur.isEmpty()) {
                lines.add(cur);
            }
            cur = word;
            while (width(cur, bold) > max && cur.length() > 1) {
                int cut = cur.length() - 1;
                while (cut > 1 && width(cur.substring(0, cut), bold) > max) {
                    cut--;
                }
                lines.add(cur.substring(0, cut));
                cur = cur.substring(cut);
            }
        }
        if (!cur.isEmpty()) {
            lines.add(cur);
        }
        return lines;
    }

    default List<String> wrap(String s, int max) {
        return wrap(s, max, false);
    }
}
