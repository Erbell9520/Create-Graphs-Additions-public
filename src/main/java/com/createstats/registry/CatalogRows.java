package com.createstats.registry;

import java.util.ArrayList;
import java.util.List;

// pad each row to 9 so families never share a row
public final class CatalogRows {
    public static final int COLUMNS = 9;

    private CatalogRows() {
    }

    public static <T> List<T> pad(List<? extends List<? extends T>> rows, T filler) {
        final List<T> out = new ArrayList<>();
        for (List<? extends T> row : rows) {
            if (row.isEmpty()) {
                continue;
            }
            out.addAll(row);
            while (out.size() % COLUMNS != 0) {
                out.add(filler);
            }
        }
        return out;
    }
}
