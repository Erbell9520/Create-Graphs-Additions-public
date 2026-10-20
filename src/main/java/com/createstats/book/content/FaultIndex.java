package com.createstats.book.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FaultIndex {
    public enum Kind {
        WORD, CAUSE
    }

    public record Entry(Sheet sheet, Part part, int row, Kind kind, Mark mark, Symptom symptom, String headKey,
                        String lineKey, java.util.function.Supplier<Object[]> headArgs) {
        public Entry(Sheet sheet, Part part, int row, Kind kind, Mark mark, Symptom symptom, String headKey,
                     String lineKey) {
            this(sheet, part, row, kind, mark, symptom, headKey, lineKey, null);
        }

        public String target() {
            return (kind == Kind.WORD ? "word:" : "cause:") + sheet.id() + ":" + row;
        }

        public Object[] headArgsOrNone() {
            return headArgs == null ? new Object[0] : headArgs.get();
        }
    }

    private static List<Entry> entries;

    private FaultIndex() {
    }

    public static synchronized List<Entry> entries() {
        if (entries == null) {
            entries = Collections.unmodifiableList(collect(Book.sheets()));
        }
        return entries;
    }

    public static List<Entry> collect(List<Sheet> sheets) {
        final List<Entry> out = new ArrayList<>();
        for (final Sheet sheet : sheets) {
            for (final Part p : sheet.parts()) {
                if (p instanceof Part.Status s) {
                    final int base = wordBase(sheet, s);
                    for (int i = 0; i < s.rows().size(); i++) {
                        final Part.Word w = s.rows().get(i);
                        out.add(new Entry(sheet, p, base + i, Kind.WORD, w.mark(), w.symptom(), w.wordKey(),
                                w.meaningKey(), w.args()));
                    }
                } else if (p instanceof Part.Faults f) {
                    final int base = causeBase(sheet, f);
                    for (int i = 0; i < f.steps().size(); i++) {
                        final Part.Step st = f.steps().get(i);
                        out.add(new Entry(sheet, p, base + i, Kind.CAUSE, null, f.symptom(), st.yesKey(),
                                st.yesNoteKey()));
                    }
                    if (f.endCause()) {
                        out.add(new Entry(sheet, p, base + f.steps().size(), Kind.CAUSE, null, f.symptom(), f.endKey(),
                                f.endNoteKey()));
                    }
                }
            }
        }
        return out;
    }

    public static int wordBase(Sheet sheet, Part.Status part) {
        int base = 0;
        for (final Part p : sheet.parts()) {
            if (p == part) {
                return base;
            }
            if (p instanceof Part.Status s) {
                base += s.rows().size();
            }
        }
        return base;
    }

    public static int causeBase(Sheet sheet, Part.Faults part) {
        int base = 0;
        for (final Part p : sheet.parts()) {
            if (p == part) {
                return base;
            }
            if (p instanceof Part.Faults f) {
                base += f.steps().size() + 1;
            }
        }
        return base;
    }

    public static List<Entry> of(Symptom symptom) {
        final List<Entry> out = new ArrayList<>();
        for (final Entry e : entries()) {
            if (e.symptom() == symptom) {
                out.add(e);
            }
        }
        return out;
    }
}
