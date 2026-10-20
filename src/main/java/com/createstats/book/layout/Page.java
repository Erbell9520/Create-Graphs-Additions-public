package com.createstats.book.layout;

import com.createstats.book.content.Section;
import com.createstats.book.content.Sheet;

import java.util.List;
import java.util.function.Supplier;

public record Page(Section section, Sheet sheet, Kind kind, List<Placed> blocks, String bandKey, int index,
                   int count, String label, Supplier<Object[]> bandArgs) {
    public enum Kind {
        CONTENTS, DATA, NOTES
    }

    public record Placed(Block block, int top) {
    }

    public Page {
        blocks = List.copyOf(blocks);
    }

    public Page(Section section, Sheet sheet, Kind kind, List<Placed> blocks, String bandKey, int index, int count,
                String label) {
        this(section, sheet, kind, blocks, bandKey, index, count, label, null);
    }

    public Object[] bandArgsOrNone() {
        return bandArgs == null ? new Object[0] : bandArgs.get();
    }

    public String entryId() {
        return sheet != null ? sheet.id() : kind == Kind.CONTENTS ? "contents:" + section.key() : "notes:" + label;
    }
}
