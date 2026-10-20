package com.createstats.book.layout;

import com.createstats.book.content.Book;
import com.createstats.book.content.FaultIndex;
import com.createstats.book.content.Part;
import com.createstats.book.content.Section;
import com.createstats.book.content.Sheet;
import com.createstats.book.content.Symptom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// every typed word has to appear somewhere
public final class IndexSearch {
    public record Result(List<FaultIndex.Entry> entries, List<Sheet> sheets, Map<Symptom, Integer> bySymptom,
                         Set<Section> indexed) {
        public int total() {
            return entries.size() + sheets.size();
        }

        public int count(Symptom s) {
            return bySymptom.getOrDefault(s, 0);
        }
    }

    private IndexSearch() {
    }

    public static Result run(BookText text, IndexState state) {
        return run(text, state, FaultIndex.entries(), Book.sheets());
    }

    public static Result run(BookText text, IndexState state, List<FaultIndex.Entry> all, List<Sheet> sheets) {
        final IndexState s = state == null ? IndexState.REST : state;
        final String[] words = words(s.query());
        final Map<Symptom, Integer> counts = new EnumMap<>(Symptom.class);
        final Set<Section> indexed = EnumSet.noneOf(Section.class);
        final List<FaultIndex.Entry> shown = new ArrayList<>();
        for (final FaultIndex.Entry e : all) {
            indexed.add(e.sheet().section());
            if (s.section() != null && e.sheet().section() != s.section()) {
                continue;
            }
            if (!matches(haystack(text, e), words)) {
                continue;
            }
            if (e.symptom() != null) {
                counts.merge(e.symptom(), 1, Integer::sum);
            }
            if (s.symptom() != null && e.symptom() != s.symptom()) {
                continue;
            }
            shown.add(e);
        }

        shown.sort(Comparator.comparingInt(e -> e.symptom() == null ? Symptom.values().length : e.symptom().ordinal()));
        final List<Sheet> hits = new ArrayList<>();
        if (words.length > 0 && s.symptom() == null) {
            for (final Sheet sheet : sheets) {
                if (isIndex(sheet) || (s.section() != null && sheet.section() != s.section())) {
                    continue;
                }
                if (matches(sheetHaystack(text, sheet), words)) {
                    hits.add(sheet);
                }
            }
        }
        return new Result(List.copyOf(shown), List.copyOf(hits), counts, indexed);
    }

    static boolean isIndex(Sheet sheet) {
        for (final Part p : sheet.parts()) {
            if (p instanceof Part.FaultIndex) {
                return true;
            }
        }
        return false;
    }

    public static String[] words(String query) {
        final String n = normalise(query);
        return n.isEmpty() ? new String[0] : n.split(" ");
    }

    public static String normalise(String s) {
        if (s == null) {
            return "";
        }
        final StringBuilder b = new StringBuilder(s.length());
        boolean space = true;
        for (final char ch : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                b.append(ch);
                space = false;
            } else if (!space) {
                b.append(' ');
                space = true;
            }
        }
        return b.toString().trim();
    }

    public static boolean matches(String hay, String[] words) {
        if (words.length == 0) {
            return true;
        }
        final String h = normalise(hay);
        for (final String w : words) {
            if (!h.contains(w)) {
                return false;
            }
        }
        return true;
    }

    static String haystack(BookText t, FaultIndex.Entry e) {
        final StringBuilder b = new StringBuilder();
        b.append(t.get(e.headKey(), e.headArgsOrNone())).append(' ').append(t.get(e.lineKey())).append(' ');
        b.append(t.get(e.sheet().titleKey())).append(' ');
        if (e.symptom() != null) {
            b.append(t.get(e.symptom().nameKey())).append(' ');
        }
        for (final String id : e.sheet().ids()) {
            b.append(id.substring(id.indexOf(':') + 1).replace('_', ' ')).append(' ');
        }
        return b.toString();
    }

    static String sheetHaystack(BookText t, Sheet sheet) {
        final StringBuilder b = new StringBuilder(t.get(sheet.titleKey())).append(' ');
        final List<String> ids = new ArrayList<>(sheet.ids());
        ids.addAll(sheet.also());
        for (final String id : ids) {
            b.append(t.itemName(id)).append(' ').append(id).append(' ')
                    .append(id.substring(id.indexOf(':') + 1).replace('_', ' ')).append(' ');
        }
        for (final Part p : sheet.parts()) {
            if (p instanceof Part.Status st) {
                for (final Part.Word w : st.rows()) {
                    b.append(t.get(w.wordKey(), w.argsOrNone())).append(' ');
                }
            }
        }
        return b.toString();
    }
}
