package com.createstats.chunkloader.map;

import com.createstats.chunkloader.core.CrownBoard;
import com.createstats.chunkloader.core.CrownLaw;
import com.createstats.chunkloader.core.CrownPicks;
import com.createstats.chunkloader.core.CrownRegion;
import com.createstats.chunkloader.core.CrownState;

import java.util.ArrayList;
import java.util.List;

// same judge as the server, run on the client's copy
public final class CrownMapTooltip {
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GREY = 0xFFAAAAAA;
    public static final int RED = 0xFFFF5555;
    public static final int ROSE = 0xFFFF7FA8;
    public static final int AMBER = 0xFFF6B64C;

    public static final String KEY = "gui.create_stats.crown_map.";

    public record Line(String key, List<Object> args, int colour) {
        public Line {
            args = List.copyOf(args);
        }
    }

    public record Crown(int ownChunkX, int ownChunkZ, long[] picks, CrownState state, CrownBoard board, CrownLaw law,
                        boolean viewerIsOwner, boolean ownerKnown, String ownerName) {
        public int held() {
            return Math.min(state.held(), picks.length);
        }

        public int indexOf(long chunk) {
            for (int i = 0; i < picks.length; i++) {
                if (picks[i] == chunk) {
                    return i;
                }
            }
            return -1;
        }

        public boolean allHeld() {
            return held() > 0 && held() == picks.length;
        }

        public boolean dark() {
            return held() == 0 && !CrownPlate.charging(state);
        }
    }

    private CrownMapTooltip() {
    }

    public static boolean mayHold(CrownState.Reason r) {
        return r == CrownState.Reason.NONE || r == CrownState.Reason.NO_POWER || r == CrownState.Reason.SWITCHED_OFF;
    }

    public static CrownPicks.Verdict verdict(Crown c, int cx, int cz) {
        return judge(c, cx, cz, c.indexOf(CrownRegion.pack(cx, cz)) >= 0, c.picks().length, c.state().ownerTotal());
    }

    private static CrownPicks.Verdict judge(Crown c, int cx, int cz, boolean picked, int stationPicks, int ownerTotal) {
        return CrownPicks.toggle(new CrownPicks.Request(c.ownerKnown(), c.viewerIsOwner(), mayHold(c.state().reason()),
                c.ownChunkX(), c.ownChunkZ(), cx, cz, picked, stationPicks, ownerTotal, c.board().pickCarry(),
                c.board().pickShortIn()), c.law());
    }

    public static List<Line> head(int cx, int cz) {
        final List<Line> out = new ArrayList<>();
        out.add(line("chunk", WHITE, cx, cz));
        out.add(line("range", GREY, cx * 16, cx * 16 + 15, cz * 16, cz * 16 + 15));
        return out;
    }

    public static List<Line> lines(Crown c, int cx, int cz) {
        final List<Line> out = head(cx, cz);
        out.addAll(body(c, verdict(c, cx, cz), cx, cz));
        return out;
    }

    public static List<Line> body(Crown c, CrownPicks.Verdict v, int cx, int cz) {
        final CrownLaw law = c.law();
        final int n = c.picks().length;
        final List<Line> out = new ArrayList<>();
        switch (v) {
            case PICK -> {
                out.add(line("pick_price", GREY, n + 1, law.maxPerStation(), group(law.nextPrice(n))));
                out.add(line("draw_then", GREY, group(law.totalDraw(n + 1))));
                if (c.allHeld()) {
                    out.add(line("pick_click", ROSE));
                } else if (c.dark()) {
                    out.add(line("pick_dark", AMBER));
                    out.add(line("pick_dark_back", GREY, n + 1, group(law.totalDraw(n + 1))));
                } else {
                    out.add(line("pick_queued", GREY));
                    out.add(line("pick_queued_click", ROSE));
                }
            }
            case DROP -> {
                final int at = c.indexOf(CrownRegion.pack(cx, cz));
                final int held = c.held();
                if (at >= 0 && at < held) {
                    out.add(line("held", GREY, at + 1, n));
                    out.add(line("drop_then", GREY, group(law.totalDraw(held - 1)), group(law.nextPrice(held - 1))));
                } else {
                    out.add(line("waiting", GREY, at + 1, n));
                }
                out.add(line("drop_click", ROSE));
            }
            case OWN_CHUNK -> {
                out.add(line("own", WHITE));
                out.add(line("own_held", GREY));
                out.add(line("own_free", GREY));
            }
            case OUTSIDE_REGION -> {
                out.add(line("outside", RED, c.state().regionX(), c.state().regionZ()));
                out.add(line("outside_fix", GREY, CrownRegion.regionOf(cx), CrownRegion.regionOf(cz)));
            }
            case STATION_FULL -> {
                out.add(line("station_full", RED, law.maxPerStation()));
                out.add(line("station_full_fix", GREY));
            }
            case PLAYER_FULL -> {
                out.add(line("player_full", RED, law.maxPerPlayer()));
                out.add(line("player_full_fix", GREY));
            }
            case BOARD_CARRY -> {
                final CrownBoard b = c.board();
                out.add(line("board_carry", RED, group(b.carried())));
                out.add(line("board_carry_need", RED, n + 1, group(law.totalDraw(n + 1))));

                final boolean onHv = Math.min(b.legA(), b.legB()) == 0 && Math.max(b.legA(), b.legB()) == 1;
                out.add(line(onHv ? "board_carry_fix_hv" : "board_carry_fix", GREY));
            }
            case BOARD_SUPPLY -> {
                out.add(line("board_supply", RED, duration(c.board().shortIn())));
                out.add(line("board_supply_fix", GREY));
            }
            case NO_OWNER -> {
                out.add(line("no_owner", RED));
                out.add(line("no_owner_fix", GREY));
            }
            case NOT_OWNER -> {
                out.add(line("not_owner", WHITE, c.ownerName()));
                out.add(line("not_owner_fix", GREY));
            }
            case STATION_DARK_FOR_GOOD -> {
                final CrownState s = c.state();
                if (s.reason() == CrownState.Reason.REGION_TAKEN) {
                    final long p = s.otherStation();
                    out.add(line("blocked_region", RED));

                    out.add(line("blocked_region_fix", GREY, (int) (p >> 38), (int) (p << 52 >> 52),
                            (int) (p << 26 >> 38)));
                } else if (s.reason() == CrownState.Reason.ON_SABLE) {
                    out.add(line("blocked_sable", RED));
                    out.add(line("blocked_sable_fix", GREY));
                } else {
                    out.add(line("blocked_other", RED));
                    out.add(line("blocked_other_fix", GREY));
                }
            }
            case SABLE_PLOT -> {
                out.add(line("sable_plot", RED));
                out.add(line("sable_plot_fix", GREY));
            }
        }
        return out;
    }

    public static Line problem(Crown c, CrownPicks.Verdict v, int cx, int cz) {
        final List<Line> b = body(c, v, cx, cz);
        return b.isEmpty() ? line("blocked_other", RED) : b.get(0);
    }

    public static List<Line> paint(Crown c, boolean drop, List<Long> chunks) {
        final CrownLaw law = c.law();
        final List<Line> out = new ArrayList<>();
        final int count = chunks.size();
        if (drop) {
            int heldGone = 0;
            for (long ch : chunks) {
                final int at = c.indexOf(ch);
                if (at >= 0 && at < c.held()) {
                    heldGone++;
                }
            }
            final int after = c.held() - heldGone;
            out.add(line(count == 1 ? "paint_drop_one" : "paint_drop", WHITE, count));
            out.add(line("drop_then", GREY, group(law.totalDraw(after)),
                    group(law.totalDraw(c.held()) - law.totalDraw(after))));
            out.add(line("paint_drop_release", ROSE));
            return out;
        }
        final int n = c.picks().length;
        int ok = 0;
        CrownPicks.Verdict refused = null;
        long at = 0;
        for (long ch : chunks) {
            final CrownPicks.Verdict v = judge(c, CrownRegion.x(ch), CrownRegion.z(ch), false, n + ok,
                    c.state().ownerTotal() + ok);
            if (v != CrownPicks.Verdict.PICK) {
                refused = v;
                at = ch;
                break;
            }
            ok++;
        }
        out.add(line(count == 1 ? "paint_one" : "paint", WHITE, count));
        if (ok > 0) {
            out.add(ok == 1 ? line("pick_price", GREY, n + 1, law.maxPerStation(), group(law.nextPrice(n)))
                    : line("paint_price", GREY, n + 1, n + ok, law.maxPerStation(),
                    group(law.totalDraw(n + ok) - law.totalDraw(n))));
            out.add(line("draw_then", GREY, group(law.totalDraw(n + ok))));
        }
        if (refused != null) {
            final List<Line> why = body(c, refused, CrownRegion.x(at), CrownRegion.z(at));
            out.addAll(ok > 0 ? why.subList(0, 1) : why);
        }
        if (ok > 0) {
            final String key = refused != null ? "paint_release_some" : c.allHeld() ? "paint_release" : "paint_release_queue";
            out.add(refused != null ? line(key, ROSE, ok) : line(key, ROSE));
        }
        return out;
    }

    public static Line reply(Crown c, boolean drop, int asked, List<CrownPicks.Verdict> verdicts, long refusedAt) {
        if (verdicts.isEmpty() || verdicts.get(verdicts.size() - 1).changes()) {
            return null;
        }
        final CrownPicks.Verdict last = verdicts.get(verdicts.size() - 1);
        final Line why = problem(c, last, CrownRegion.x(refusedAt), CrownRegion.z(refusedAt));
        if (asked <= 1) {
            return why;
        }
        return line(drop ? "reply_some_drop" : "reply_some", WHITE, verdicts.size() - 1, asked, why);
    }

    public static Line duration(int ticks) {
        final int seconds = Math.max(0, ticks) / 20;
        return seconds >= 60 ? new Line("block.create_stats.spark_crown.minutes", List.of((seconds + 30) / 60), GREY)
                : new Line("block.create_stats.spark_crown.seconds", List.of(seconds), GREY);
    }

    public static Line line(String name, int colour, Object... args) {
        return new Line(KEY + name, List.of(args), colour);
    }

    private static String group(long v) {
        return CrownPlate.group(v);
    }
}
