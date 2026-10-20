package com.createstats.book.layout;

import com.createstats.book.content.Section;
import com.createstats.book.content.Symptom;

public record IndexState(String query, int cursor, int anchor, boolean focused, boolean caret, Symptom symptom,
                         Section section, int offset, Section near) {
    public static final IndexState REST = new IndexState("", 0, 0, false, false, null, null, 0, null);

    public IndexState {
        query = query == null ? "" : query;
        cursor = Math.max(0, Math.min(query.length(), cursor));
        anchor = Math.max(0, Math.min(query.length(), anchor));
        offset = Math.max(0, offset);
    }

    public static IndexState searching(String query) {
        final String q = query == null ? "" : query;
        return new IndexState(q, q.length(), q.length(), true, true, null, null, 0, null);
    }

    public IndexState withSymptom(Symptom s) {
        return new IndexState(query, cursor, anchor, focused, caret, s, section, 0, near);
    }

    public IndexState withSection(Section s) {
        return new IndexState(query, cursor, anchor, focused, caret, symptom, s, 0, near);
    }

    public IndexState withOffset(int o) {
        return new IndexState(query, cursor, anchor, focused, caret, symptom, section, o, near);
    }

    public IndexState withNear(Section s) {
        return new IndexState(query, cursor, anchor, focused, caret, symptom, section, offset, s);
    }
}
