package com.createstats.book.content;

import java.util.List;
import java.util.function.Supplier;

public sealed interface Part permits Part.Figure, Part.Controls, Part.World, Part.Status, Part.Ratings,
        Part.Grades, Part.Curve, Part.Application, Part.Faults, Part.SeeAlso, Part.Note, Part.Break, Part.Steps,
        Part.Terms, Part.Marks, Part.Retired, Part.FaultIndex, Part.AtoZ, Part.Ponder, Part.Made {
    record Figure(String specimen, String headingKey, String captionKey, Tab tab) implements Part {
        public Figure(String specimen, String headingKey, String captionKey) {
            this(specimen, headingKey, captionKey, null);
        }
    }

    record Tab(int index, Supplier<List<String>> labels) {
        public List<String> names() {
            final List<String> out = new java.util.ArrayList<>();
            for (final String s : labels.get()) {
                out.add(s.toUpperCase(java.util.Locale.ROOT));
            }
            return out;
        }

        public String name() {
            return names().get(index);
        }
    }

    record Controls(String specimen, String headingKey, List<Control> rows) implements Part {
        public Controls {
            rows = List.copyOf(rows);
        }

        public Controls(String specimen, List<Control> rows) {
            this(specimen, "create_stats.book.part.designations", rows);
        }
    }

    record Control(String id, String nameKey, String functionKey, String keysKey, Supplier<Object[]> keysArgs) {
    }

    record World(List<Gesture> rows) implements Part {
        public World {
            rows = List.copyOf(rows);
        }
    }

    record Gesture(String gestureKey, String effectKey) {
    }

    record Status(String headingKey, List<Word> rows) implements Part {
        public Status {
            rows = List.copyOf(rows);
        }

        public Status(List<Word> rows) {
            this("create_stats.book.part.status_words", rows);
        }
    }

    record Word(Mark mark, String wordKey, Supplier<Object[]> args, String meaningKey, Symptom symptom) {
        public Word(Mark mark, String wordKey, String meaningKey, Symptom symptom) {
            this(mark, wordKey, null, meaningKey, symptom);
        }

        public Object[] argsOrNone() {
            return args == null ? new Object[0] : args.get();
        }
    }

    record Ratings(String headingKey, List<Rating> rows) implements Part {
        public Ratings {
            rows = List.copyOf(rows);
        }

        public Ratings(List<Rating> rows) {
            this("create_stats.book.part.ratings", rows);
        }
    }

    record Rating(String labelKey, String valueKey, Supplier<Object[]> args) {
    }

    record Grades(String headingKey, String noteKey, Supplier<Object[]> noteArgs, List<Cell> columns,
                  List<Grade> rows) implements Part {
        public Grades {
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
            for (final Grade g : rows) {
                if (g.cells().size() != columns.size()) {
                    throw new IllegalArgumentException(g.itemId() + " has " + g.cells().size() + " figures for "
                            + columns.size() + " columns");
                }
            }
        }
    }

    record Grade(String itemId, String labelKey, List<Cell> cells, Supplier<Object[]> labelArgs) {
        public Grade {
            cells = List.copyOf(cells);
            if (itemId == null && labelKey == null) {
                throw new IllegalArgumentException("a row with no item needs a name of its own");
            }
        }

        public Grade(String itemId, String labelKey, List<Cell> cells) {
            this(itemId, labelKey, cells, null);
        }

        public Object[] labelArgsOrNone() {
            return labelArgs == null ? new Object[0] : labelArgs.get();
        }
    }

    record Cell(String valueKey, Supplier<Object[]> args) {
    }

    record Curve(String headingKey, String noteKey, Supplier<Object[]> noteArgs, double xMax, List<Tick> ticks,
                 String unitKey, List<Line> lines) implements Part {
        public Curve {
            ticks = List.copyOf(ticks);
            lines = List.copyOf(lines);
        }
    }

    record Tick(double x, String labelKey, Supplier<Object[]> args) {
        public Tick(double x, String labelKey) {
            this(x, labelKey, null);
        }

        public Object[] argsOrNone() {
            return args == null ? new Object[0] : args.get();
        }
    }

    record Line(String labelKey, java.util.function.DoubleUnaryOperator y, LineStyle style) {
    }

    enum LineStyle {
        SUBJECT,

        DOTTED,

        REFERENCE
    }

    record Application(String headingKey, List<Link> chain, String captionKey) implements Part {
        public Application {
            chain = List.copyOf(chain);
        }

        public Application(List<Link> chain, String captionKey) {
            this("create_stats.book.part.application", chain, captionKey);
        }
    }

    record Link(String itemId, String labelKey, boolean subject) {
    }

    record Faults(Symptom symptom, String symptomKey, List<Step> steps, String endKey, String endNoteKey,
                  boolean endCause) implements Part {
        public Faults {
            steps = List.copyOf(steps);
        }
    }

    record Step(String questionKey, String yesKey, String yesNoteKey) {
    }

    record SeeAlso(List<String> sheetIds) implements Part {
        public SeeAlso {
            sheetIds = List.copyOf(sheetIds);
        }
    }

    record Note(NoteKind kind, String key, Supplier<Object[]> args) implements Part {
        public Note(NoteKind kind, String key) {
            this(kind, key, null);
        }

        public Object[] argsOrNone() {
            return args == null ? new Object[0] : args.get();
        }
    }

    enum NoteKind {
        NOTE, CAUTION, REQUIRES
    }

    record Break(String bandKey, Supplier<Object[]> bandArgs, boolean left) implements Part {
        public Break(String bandKey) {
            this(bandKey, null, false);
        }

        public Object[] argsOrNone() {
            return bandArgs == null ? new Object[0] : bandArgs.get();
        }
    }

    record Steps(String headingKey, List<Instruction> steps) implements Part {
        public Steps {
            steps = List.copyOf(steps);
        }
    }

    record Instruction(String textKey, String itemId, List<String> argKeys) {
        public Instruction {
            argKeys = List.copyOf(argKeys);
        }

        public Instruction(String textKey, String itemId) {
            this(textKey, itemId, List.of());
        }
    }

    record Terms(String headingKey, List<Term> rows, boolean alphabetical) implements Part {
        public Terms {
            rows = List.copyOf(rows);
        }
    }

    record Term(String termKey, String meaningKey, Supplier<Object[]> args) {
        public Term(String termKey, String meaningKey) {
            this(termKey, meaningKey, null);
        }
    }

    record Marks(String headingKey) implements Part {
    }

    record Retired(List<Unlisted> rows) implements Part {
        public Retired {
            rows = List.copyOf(rows);
        }
    }

    record Unlisted(String itemId, boolean retired, String lineKey) {
    }

    record FaultIndex() implements Part {
    }

    record AtoZ() implements Part {
    }

    record Made(String headingKey, String noteKey, List<String> recipes) implements Part {
        public Made {
            recipes = List.copyOf(recipes);
        }
    }

    // only if ponder actually has scenes, never a button that opens nothing
    record Ponder(String itemId) implements Part {
    }
}
