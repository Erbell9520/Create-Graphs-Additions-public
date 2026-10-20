package com.createstats.book.layout;

import com.createstats.book.content.Num;
import com.createstats.book.content.Recipes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class MadeText {
    private MadeText() {
    }

    private static final String P = "create_stats.book.made.";

    public static String machineKey(String type) {
        return P + "machine." + type.substring(type.indexOf(':') + 1);
    }

    public static String fluidKey(String id) {
        return "fluid_type." + id.replace(':', '.');
    }

    public static String stepKey(String type) {
        return P + "step." + type.substring(type.indexOf(':') + 1);
    }

    public static List<String> keys(Recipes.Recipe r) {
        final List<String> out = new ArrayList<>(List.of(machineKey(r.type()), P + "amount", P + "from"));
        for (final Recipes.Stack s : r.inputs()) {
            if (s.fluid()) {
                out.add(P + "fluid");
                out.add(fluidKey(s.id()));
            }
        }
        if (r.sequenced()) {
            out.add(r.loops() > 1 ? P + "sequence_loops" : P + "sequence");
            out.add(P + "between");
            for (final Recipes.Step st : r.steps()) {
                out.add(stepKey(st.type()));
                if (st.adds() != null && st.adds().fluid()) {
                    out.add(P + "fluid");
                    out.add(fluidKey(st.adds().id()));
                }
            }
        }
        if (r.made().chance() < 100.0) {
            out.add(P + "chance");
        }
        if (r.results().size() > 1) {
            out.add(P + "otherwise");
            out.add(P + "or");
        }
        return out;
    }

    public record Words(BiFunction<String, Object[], String> t, Function<String, String> itemName) {
        String t(String key, Object... args) {
            return t.apply(key, args);
        }

        String amount(Recipes.Stack s) {
            if (s.fluid()) {
                return t(P + "fluid", Num.of(s.mb()), t(fluidKey(s.id())));
            }
            return count(s.count(), itemName.apply(s.id()));
        }

        String count(int n, String name) {
            return n == 1 ? name : t(P + "amount", Num.of(n), name);
        }

        public String makes(Recipes.Recipe r) {
            final Recipes.Result m = r.made();
            final String what = count(m.count(), itemName.apply(m.id()));
            return m.chance() < 100.0 ? t(P + "chance", what, percent(m.chance())) : what;
        }

        public String machine(Recipes.Recipe r) {
            return t(machineKey(r.type()));
        }

        public String from(Recipes.Recipe r) {
            final List<String> parts = new ArrayList<>();
            for (final Recipes.Stack s : r.inputs()) {
                parts.add(amount(s));
            }
            final String inputs = String.join(", ", parts);
            final StringBuilder out = new StringBuilder();
            if (r.sequenced()) {
                final List<String> steps = new ArrayList<>();
                for (final Recipes.Step st : r.steps()) {
                    steps.add(st.adds() == null ? t(stepKey(st.type())) : t(stepKey(st.type()), amount(st.adds())));
                }
                final String chain = String.join(", ", steps);
                out.append(r.loops() > 1 ? t(P + "sequence_loops", inputs, Num.of(r.loops()), chain)
                        : t(P + "sequence", inputs, chain));
            } else {
                out.append(t(P + "from", inputs));
            }
            if (r.results().size() > 1) {
                final List<String> others = new ArrayList<>();
                for (final Recipes.Result o : r.results().subList(1, r.results().size())) {
                    others.add(t(P + "chance", count(o.count(), itemName.apply(o.id())), percent(o.chance())));
                }
                out.append(' ').append(t(P + "otherwise", String.join(t(P + "or"), others)));
            }
            if (r.transitional() != null) {
                out.append(' ').append(t(P + "between", itemName.apply(r.transitional())));
            }
            return out.toString();
        }
    }

    static String percent(double chance) {
        return Num.of(chance);
    }
}
