package com.createstats.book.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class Book {
    private static final List<Sheet> SHEETS;

    static {
        final List<Sheet> all = new ArrayList<>();
        all.addAll(FrontMatter.all());
        all.addAll(PowerSheets.all());
        all.addAll(BackMatter.all());

        all.sort((a, b) -> Integer.compare(a.section().ordinal(), b.section().ordinal()));
        SHEETS = Collections.unmodifiableList(all);
    }

    private Book() {
    }

    public static List<Sheet> sheets() {
        return SHEETS;
    }

    public static List<Sheet> in(Section section) {
        final List<Sheet> out = new ArrayList<>();
        for (final Sheet s : SHEETS) {
            if (s.section() == section) {
                out.add(s);
            }
        }
        return out;
    }

    public static List<Sheet> blockSheets() {
        final List<Sheet> out = new ArrayList<>();
        for (final Sheet s : SHEETS) {
            if (s.coversBlocks() && s.section() != Section.BASICS) {
                out.add(s);
            }
        }
        return out;
    }

    public static Optional<Sheet> byId(String id) {
        for (final Sheet s : SHEETS) {
            if (s.id().equals(id)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public static Optional<Sheet> find(String idOrRegistryId) {
        if (idOrRegistryId == null || idOrRegistryId.isEmpty()) {
            return Optional.empty();
        }
        final Optional<Sheet> direct = byId(idOrRegistryId);
        if (direct.isPresent()) {
            return direct;
        }
        final String full = idOrRegistryId.contains(":") ? idOrRegistryId : "create_stats:" + idOrRegistryId;
        for (final Sheet s : SHEETS) {
            if (s.covers(full)) {
                return Optional.of(s);
            }
        }

        for (final Sheet s : SHEETS) {
            if (s.explains(full)) {
                return Optional.of(s);
            }
        }
        for (final Sheet s : SHEETS) {
            for (final Part p : s.parts()) {
                if (p instanceof Part.Retired r) {
                    for (final Part.Unlisted u : r.rows()) {
                        if (u.itemId().equals(full)) {
                            return Optional.of(s);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
