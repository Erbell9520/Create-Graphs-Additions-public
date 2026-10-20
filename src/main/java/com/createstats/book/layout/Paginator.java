package com.createstats.book.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class Paginator {
    public record Run(List<Page.Placed> blocks, String bandKey, Supplier<Object[]> bandArgs) {
        public Run(List<Page.Placed> blocks, String bandKey) {
            this(blocks, bandKey, null);
        }
    }

    private Paginator() {
    }

    public static List<Run> flow(Ctx x, List<Block> blocks, int w, int h) {
        return flow(x, blocks, w, h, false);
    }

    public static List<Run> flow(Ctx x, List<Block> blocks, int w, int h, boolean leftAligned) {
        final State s = new State(x, w, h);
        final List<Block> deferred = new ArrayList<>();
        for (final Block b : blocks) {
            if (b.isBreak()) {
                if (s.runs.size() < b.breakBefore()) {
                    s.breakTo(b.bandKey(), b.bandArgs(), leftAligned && b.breaksToLeft());
                }
                continue;
            }
            s.place(b, deferred);
        }
        for (final Block b : deferred) {
            s.place(b, null);
        }
        return s.close();
    }

    private static final class State {
        private final Ctx x;
        private final int w;
        private final int h;
        private final List<Run> runs = new ArrayList<>();
        private List<Page.Placed> cur = new ArrayList<>();
        private String band;
        private Supplier<Object[]> bandArgs;
        private int y;

        State(Ctx x, int w, int h) {
            this.x = x;
            this.w = w;
            this.h = h;
        }

        void breakTo(String bandKey, Supplier<Object[]> args, boolean left) {
            if (!cur.isEmpty()) {
                newSheet();
            }
            if (left && x.geo.spread() && runs.size() % 2 == 1) {
                runs.add(new Run(List.of(new Page.Placed(new ContentsBlocks.Notes(), 0)), "create_stats.book.part.notes"));
            }
            band = bandKey;
            bandArgs = args;
        }

        void newSheet() {
            runs.add(new Run(cur, band, bandArgs));
            cur = new ArrayList<>();
            band = null;
            bandArgs = null;
            y = 0;
        }

        void place(Block b, List<Block> deferred) {
            final int bh = b.height(x, w);
            if (bh <= 0) {
                return;
            }
            final int gap = cur.isEmpty() ? 0 : b.gap();
            if (y + gap + bh <= h) {
                cur.add(new Page.Placed(b, y + gap));
                y += gap + bh;
                return;
            }
            final Block[] parts = b.split(x, w, h - y - gap);
            if (parts != null) {
                cur.add(new Page.Placed(parts[0], y + gap));
                y += gap + parts[0].height(x, w);
                newSheet();
                place(parts[1], deferred);
                return;
            }
            if (deferred != null && b.deferrable()) {
                deferred.add(b);
                return;
            }
            if (!cur.isEmpty()) {
                newSheet();
                place(b, deferred);
                return;
            }

            cur.add(new Page.Placed(b, 0));
            y = bh;
        }

        List<Run> close() {
            if (!cur.isEmpty() || runs.isEmpty()) {
                runs.add(new Run(cur, band, bandArgs));
            }
            return runs;
        }
    }
}
