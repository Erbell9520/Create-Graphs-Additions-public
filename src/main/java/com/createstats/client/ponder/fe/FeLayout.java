package com.createstats.client.ponder.fe;

import com.createstats.power.CableTier;
import com.createstats.power.ConnectorTier;
import com.createstats.power.FeRates;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;
import com.createstats.power.ponder.FePonderChapters;
import com.createstats.power.ponder.FePonderGrid;
import com.createstats.power.ponder.FePonderPlant;
import com.createstats.power.ponder.FePonderPlant.Recording;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

// ponder puts caption boxes at 3/4 width and placeNearTarget only pulls left. fun
public final class FeLayout {
    private FeLayout() {
    }

    public static final int UPPER = 40;

    public static final int[][] REASON = {{-18, 2, 0}, {-18, 2, 0}, {2, 2, 1}, {-18, 2, 0}, {6, 2, 0}};

    private static Vec3 top(BlockPos p, double up) {
        return new Vec3(p.getX() + 0.5, p.getY() + up, p.getZ() + 0.5);
    }

    private static Vec3 centre(BlockPos p) {
        return new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    public record Reveal(List<BlockPos> blocks, int then) {
        public BlockPos[] array() {
            return blocks.toArray(new BlockPos[0]);
        }
    }

    private static Reveal reveal(int then, BlockPos... blocks) {
        return new Reveal(List.of(blocks), then);
    }

    private static List<BlockPos> row(int x0, int x1, int y, int z0, int z1) {
        final List<BlockPos> out = new ArrayList<>();
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) out.add(new BlockPos(x, y, z));
        return out;
    }

    public static List<Reveal> benchBuild() {
        final List<BlockPos> casing = new ArrayList<>();
        for (BlockPos p : FeStage.BENCH_RUN) casing.add(p.below());
        return List.of(reveal(3, FeStage.BENCH_MOTOR), reveal(4, FeStage.BENCH_DYNAMO), new Reveal(casing, 4),
                reveal(3, FeStage.BENCH_PEDESTAL), reveal(3, FeStage.BENCH_FAN_MOTOR), reveal(0, FeStage.BENCH_FAN));
    }

    public static List<Reveal> benchRoute() {
        final List<Reveal> out = new ArrayList<>();
        for (int k = 1; k < FeStage.BENCH_RUN.length; k++) out.add(reveal(2, FeStage.BENCH_RUN[k]));
        return out;
    }

    public static List<BlockPos> ladderRow(int i) {
        return row(FeStage.LADDER_X0, FeStage.LADDER_X1, 1, FeStage.LADDER_Z[i], FeStage.LADDER_Z[i]);
    }

    public static final int LADDER_STEP = 6;
    public static final int LAMP_STEP = 3;

    public static List<Reveal> plant(boolean board) {
        final List<Reveal> out = new ArrayList<>();
        out.add(reveal(4, FeStage.MOTOR, FeStage.GEARBOX_A, FeStage.SHAFT_AB, FeStage.CLUTCH, FeStage.GEARBOX_B,
                FeStage.SHAFT_BC, FeStage.GEARBOX_C, FeStage.LEVER_BASE));
        out.add(reveal(3, FeStage.DYNAMOS));
        out.add(reveal(3, FeStage.LEVER));
        final List<BlockPos> plinth = new ArrayList<>(row(9, 9, 1, 3, 9));
        plinth.addAll(row(7, 8, 1, 3, 3));
        plinth.add(FeStage.PEDESTAL);
        out.add(new Reveal(plinth, 4));
        out.add(reveal(4, FeStage.DUCT));
        if (board) out.add(reveal(4, FeStage.PDU));
        out.add(reveal(3, FeStage.FAN_MOTOR, FeStage.FAN));
        out.add(reveal(3, FeStage.REPELLER, FeStage.REPELLER_CONN));
        out.add(reveal(3, FeStage.RANGE, FeStage.RANGE_CONN));
        out.add(reveal(3, FeStage.MILL_MOTOR, FeStage.MILL));
        out.add(reveal(3, FeStage.LAMP, FeStage.LAMP_CONN));
        return out;
    }

    public static Vec3 benchAnchor() {
        return FePaths.faceCentre(FeStage.BENCH_DYNAMO, Direction.WEST);
    }

    public static Vec3 benchMachine() {
        return FePaths.faceCentre(FeStage.BENCH_FAN_MOTOR, Direction.EAST);
    }

    public static FePaths.Path benchRun() {
        return FePaths.floorRun(benchAnchor(), FeStage.BENCH_RUN, benchMachine(), CableTier.SURFACE);
    }

    public static FePaths.Path benchLampRun() {
        return FePaths.floorRun(centre(FeStage.BENCH_RUN[2]).add(0.0, -0.5, 0.0), new BlockPos[] {FeStage.BENCH_RUN[2]},
                FePaths.faceCentre(FeStage.BENCH_LAMP, Direction.SOUTH), CableTier.SURFACE);
    }

    public static Vec3 benchGoggles() {
        return top(FeStage.BENCH_RUN[0], 0.2);
    }

    public static Vec3 ladderTag(int grade) {
        return new Vec3(FeStage.LADDER_X1 + 1.0, 1.1, FeStage.LADDER_Z[grade] + 0.5);
    }

    public static Map<String, Vec3> cables() {
        final Map<String, Vec3> m = new HashMap<>();
        m.put("lay_caption", benchAnchor());
        final FePaths.Path run = benchRun();
        m.put("flow_caption", run.at(run.length() * 0.5).add(0.0, 0.2, 0.0));
        m.put("ladder_caption", new Vec3(FeStage.LADDER_X1 + 0.5, 1.2, FeStage.LADDER_Z[3] + 0.5));
        m.put("lamp_caption", top(FeStage.BENCH_LAMP, 0.6));
        m.put("goggles_caption", benchGoggles());
        return m;
    }

    public static FePaths.Path trunk(int d) {
        final BlockPos[] run = java.util.Arrays.copyOfRange(FeStage.DUCT, FeStage.DUCT_AT_DYNAMO[d], FeStage.DUCT.length);
        return FePaths.floorRun(FePaths.faceCentre(FeStage.DYNAMOS[d], Direction.WEST), run,
                FePaths.faceCentre(FeStage.PDU, Direction.EAST), CableTier.DUCT);
    }

    public static double trunkRate(Recording rec, int d, int r) {
        int all = 0;
        for (int k = 0; k < rec.rpm.length; k++) all += FePonderPlant.potentialOf(rec.rpm[k][r], rec.setup.tuning());
        final int mine = FePonderPlant.potentialOf(rec.rpm[d][r], rec.setup.tuning());
        return all <= 0 ? 0.0 : rec.intake[r] * (mine / (double) all);
    }

    public static Map<String, Vec3> pdu() {
        final Map<String, Vec3> m = new HashMap<>();
        m.put("place_caption", FePaths.faceCentre(FeStage.PDU, Direction.EAST));
        m.put("legs_caption", top(FeStage.PDU, 1.2));
        m.put("tiers_caption", FePaths.faceCentre(FeStage.PDU, Direction.NORTH));
        return m;
    }

    public static List<Reveal> spansBuild() {
        return List.of(reveal(3, FeStage.SPANS_MOTOR), reveal(3, FeStage.SPANS_DYNAMO), reveal(4, FeStage.SPANS_A[0]),
                reveal(3, FeStage.SPANS_RANGE), reveal(0, FeStage.SPANS_B));
    }

    public static List<Reveal> rack() {
        final List<Reveal> out = new ArrayList<>();
        for (int i = 0; i < FeStage.RACK_EAST.length; i++) {
            out.add(new Reveal(List.of(FeStage.RACK_EAST[i].below(), FeStage.RACK_WEST[i].below(), FeStage.RACK_EAST[i],
                    FeStage.RACK_WEST[i]), 3));
        }
        return out;
    }

    public static List<Reveal> pair() {
        return List.of(reveal(3, FeStage.SPANS_LAMP, FeStage.SPANS_C), reveal(0, FeStage.SPANS_D_POST, FeStage.SPANS_D));
    }

    public static String rackWireWords(int row) {
        final WireTier w = WireTier.values()[row];
        return w.getDisplayName() + " " + FeRates.perTick(FeRates.wire(w));
    }

    public static String rackConnectorWords(int row) {
        final ConnectorTier c = switch (row) {
            case 1 -> ConnectorTier.POWER;
            case 2 -> ConnectorTier.ADVANCED;
            case 3 -> ConnectorTier.INDUSTRIAL;
            default -> null;
        };
        if (c == null) return null;
        final String name = c == ConnectorTier.POWER ? c.getDisplayName() : c.getDisplayName().replace(" Power Connector", "");
        return name + " " + FeRates.perTick(FeRates.connector(c)) + (c.maxLinks() != 4 ? ", " + c.maxLinks() + " wires" : "");
    }

    public static Vec3 rackWireTag(int row) {
        return top(FeStage.RACK_EAST[row], 0.45);
    }

    public static Vec3 rackConnectorTag(int row) {
        return top(FeStage.RACK_WEST[row], 0.45);
    }

    public static Vec3 spansMiddle() {
        final Vec3 a = centre(FeStage.SPANS_A[0]);
        final Vec3 b = centre(FeStage.SPANS_B);
        return a.lerp(b, 0.5).add(0.0, 0.1, 0.0);
    }

    public static Vec3 spansGoggles() {
        return top(FeStage.SPANS_B, 0.7);
    }

    @FunctionalInterface
    public interface Hang {
        FePaths.Path between(BlockPos from, BlockPos to, WireTension tension);
    }

    public static List<FeFlow.Stream> spanStreams(Hang hang, FePonderGrid.Recording r) {
        final List<FeFlow.Stream> out = new ArrayList<>();
        final BlockPos[] a = FeStage.SPANS_A;
        final int range = FePonderChapters.Spans.RANGE;
        final int lamp = FePonderChapters.Spans.LAMP;
        final FeFlow.Rate share = t -> FePonderChapters.Spans.spansAt(t) == 0 ? 0.0
                : Math.max(0, r.flow[range][r.at(t)]) / (double) FePonderChapters.Spans.spansAt(t);
        final WireTension[] rehung = FePonderChapters.Spans.REHUNG;
        final int rehang = FePonderChapters.Spans.REHANG;
        final int step = FePonderChapters.Spans.REHANG_STEP;
        for (int s = 0; s <= rehung.length; s++) {
            final int from = s == 0 ? 0 : rehang + step * (s - 1);
            final int until = s == rehung.length ? Integer.MAX_VALUE : rehang + step * s;
            final IntPredicate there = t -> t >= from && t < until && hung(0, t);
            final FeFlow.Stream way = FeFlow.Stream.wire(hang.between(a[0], FeStage.SPANS_B,
                    s == 0 ? WireTension.STANDARD : rehung[s - 1]), t -> there.test(t) ? share.at(t) : 0.0,
                    WireTier.COPPER, there);
            out.add(s < rehung.length ? way.then(out.size() + 1) : way);
        }
        for (int k = 1; k < a.length; k++) {
            final int span = k;
            final IntPredicate there = t -> hung(span, t);
            out.add(FeFlow.Stream.wire(hang.between(a[k], FeStage.SPANS_B, WireTension.STANDARD),
                    t -> there.test(t) ? share.at(t) : 0.0, WireTier.COPPER, there));
        }
        final FeFlow.Rate lit = t -> Math.max(0, r.flow[lamp][r.at(t)]);
        out.add(FeFlow.Stream.wire(hang.between(a[0], FeStage.SPANS_D, WireTension.STANDARD), lit, WireTier.COPPER,
                t -> true));
        out.add(FeFlow.Stream.wire(hang.between(FeStage.SPANS_D, FeStage.SPANS_C, WireTension.STANDARD), lit,
                WireTier.COPPER, t -> true));
        return out;
    }

    private static boolean hung(int span, int t) {
        return t >= FePonderChapters.Spans.landed(span) && FePonderChapters.Spans.spansAt(t) > span;
    }

    public static Map<String, Vec3> wires() {
        final Map<String, Vec3> m = new HashMap<>();
        m.put("string_caption", top(FeStage.SPANS_A[0], 0.8));
        m.put("power_caption", spansMiddle());

        m.put("grades_caption", top(FeStage.RACK_WEST[0], 0.3));
        m.put("pair_caption", top(FeStage.SPANS_D, 0.7));
        return m;
    }

    public static List<Reveal> bankBuild() {
        final List<BlockPos> bench = new ArrayList<>();
        for (BlockPos p : FeStage.BANK_RUN) bench.add(p.below());
        return List.of(reveal(3, FeStage.BANK_MOTOR), reveal(3, FeStage.BANK_DYNAMO),
                reveal(3, FeStage.BANK_RANGE_BASE, FeStage.BANK_RANGE), new Reveal(bench, 4),
                new Reveal(List.of(FeStage.BANK_RUN), 4),
                reveal(0, FeStage.SPARE_BASE, FeStage.SPARE_LINK_BASE, FeStage.SPARE));
    }

    public static Vec3 bankFace() {
        return FePaths.faceCentre(FeStage.BANK_BLOCKS[6], Direction.EAST);
    }

    public static Vec3 bankTop() {
        return new Vec3(2.0, 3.0, 4.0);
    }

    public static FePaths.Path bankFeed() {
        return FePaths.floorRun(FePaths.faceCentre(FeStage.BANK_DYNAMO, Direction.WEST),
                java.util.Arrays.copyOfRange(FeStage.BANK_RUN, 0, 3), FePaths.faceCentre(FeStage.BANK_RANGE,
                        Direction.NORTH), CableTier.SURFACE);
    }

    public static FePaths.Path bankCharge() {
        return FePaths.floorRun(FePaths.faceCentre(FeStage.BANK_DYNAMO, Direction.WEST), FeStage.BANK_RUN, bankFace(),
                CableTier.SURFACE);
    }

    public static FePaths.Path bankCover() {
        return FePaths.floorRun(bankFace(), new BlockPos[] {FeStage.BANK_RUN[3], FeStage.BANK_RUN[2]},
                FePaths.faceCentre(FeStage.BANK_RANGE, Direction.NORTH), CableTier.SURFACE);
    }

    public static FePaths.Path spareCover() {
        return FePaths.floorRun(FePaths.faceCentre(FeStage.SPARE, Direction.SOUTH),
                new BlockPos[] {FeStage.SPARE_LINK, FeStage.BANK_RUN[1], FeStage.BANK_RUN[2]},
                FePaths.faceCentre(FeStage.BANK_RANGE, Direction.NORTH), CableTier.SURFACE);
    }

    public static List<FeFlow.Stream> bankStreams(FePonderChapters.Bank.Run run) {
        final FePonderGrid.Recording r = run.rec();
        final FePonderChapters.Warp warp = run.warp();
        final int range = FePonderChapters.Bank.RANGE;
        final int bank = FePonderChapters.Bank.BANK;
        final int spare = FePonderChapters.Bank.SPARE;
        final int dynamo = FePonderChapters.Bank.DYNAMO;
        final java.util.function.IntUnaryOperator at = t -> r.at(warp.tick(t));
        return List.of(
                new FeFlow.Stream(bankFeed(), t -> Math.min(Math.max(0, r.flow[range][at.applyAsInt(t)]),
                        Math.max(0, -r.flow[dynamo][at.applyAsInt(t)]))),
                new FeFlow.Stream(bankCharge(), t -> Math.max(0, r.flow[bank][at.applyAsInt(t)])),
                new FeFlow.Stream(bankCover(), t -> Math.max(0, -r.flow[bank][at.applyAsInt(t)])),
                new FeFlow.Stream(spareCover(), t -> Math.max(0, -r.flow[spare][at.applyAsInt(t)]), FeFlow.WIDTH,
                        t -> t >= FePonderChapters.Bank.NEVER && t < FePonderChapters.Bank.BREAK, -1));
    }

    public static List<FeDash.Row> bankRows(FePonderChapters.Bank.Run run, FePonderChapters.Bank.Stock stock) {
        final FePonderGrid.Recording r = run.rec();
        final FePonderChapters.Warp warp = run.warp();
        final int bank = FePonderChapters.Bank.BANK;
        final int spare = FePonderChapters.Bank.SPARE;
        final java.util.function.IntUnaryOperator at = t -> r.at(warp.tick(t));
        return List.of(
                new FeDash.Row(t -> r.fill(bank, at.applyAsInt(t)), t -> t < FePonderChapters.Bank.POWER
                        ? builtWords(FePonderChapters.Bank.builtAt(t), stock)
                        : FeDash.rowWords("bank", r.fill(bank, at.applyAsInt(t)), r.flow[bank][at.applyAsInt(t)]),
                        t -> t >= FePonderChapters.Bank.PLACE),
                new FeDash.Row(t -> r.fill(spare, at.applyAsInt(t)),
                        t -> FeDash.rowWords("spare", r.fill(spare, at.applyAsInt(t)), r.flow[spare][at.applyAsInt(t)]),
                        t -> t >= FePonderChapters.Bank.NEVER - 10 && t < FePonderChapters.Bank.BREAK));
    }

    public static String builtWords(int n, FePonderChapters.Bank.Stock stock) {
        if (n <= 1) return String.format(java.util.Locale.ROOT, "1 block holds %,d FE", stock.capacity(1));
        return String.format(java.util.Locale.ROOT, "%d blocks hold %,d FE, not %,d", n, stock.capacity(n),
                n * stock.capacity(1));
    }

    public static Map<String, Vec3> bank() {
        return new HashMap<>();
    }

    public static List<Reveal> yardBuild() {
        final BlockPos[][] m = FeStage.BOARD_MACHINES;
        final BlockPos[][] e = FeStage.BOARD_ENDS;
        return List.of(reveal(4, FeStage.YC), reveal(3, FeStage.YARD_BANKS[0], FeStage.YARD_BANK_ENDS[0]),
                reveal(3, FeStage.YARD_BANKS[1], FeStage.YARD_BANK_ENDS[1]),
                reveal(3, FeStage.BOARDS[0], FeStage.BOARD_FEEDS[0]), reveal(3, m[0][0], e[0][0], m[0][1], e[0][1]),
                reveal(3, FeStage.BOARDS[1], FeStage.BOARD_FEEDS[1]), reveal(0, m[1][0], e[1][0], m[1][1], e[1][1]));
    }

    @FunctionalInterface
    public interface LegHang {
        FePaths.Path between(BlockPos board, int stud, BlockPos far);
    }

    public static FePaths.Path boardFeed(PortHang hang, int b) {
        return hang.between(FeStage.terminal(FeStage.YC, 3 + b), FeStage.BOARD_FEEDS[b]);
    }

    public static List<FeFlow.Stream> yardStreams(PortHang hang, LegHang legs, FePonderChapters.Deal.Run run) {
        final com.createstats.power.ponder.FePonderDeal.Recording r = run.rec();
        final FePonderChapters.Warp warp = run.warp();
        final java.util.function.IntUnaryOperator at = t -> r.at(warp.tick(t));
        final List<FeFlow.Stream> out = new ArrayList<>();
        for (int k = 0; k < 2; k++) {
            final int bank = k;
            out.add(FeFlow.Stream.wire(reversed(hang.between(FeStage.terminal(FeStage.YC, 1 + k),
                    FeStage.YARD_BANK_ENDS[k])), t -> Math.max(0, -r.bankFlow[bank][at.applyAsInt(t)]), WireTier.COPPER,
                    t -> true));
        }
        for (int b = 0; b < 2; b++) {
            final int board = b;
            out.add(FeFlow.Stream.wire(boardFeed(hang, b), t -> r.intake[board][at.applyAsInt(t)], WireTier.COPPER,
                    t -> true));
        }
        for (int b = 0; b < 2; b++) {
            for (int d = 0; d < 2; d++) {
                final int board = b;
                final int dev = d;
                out.add(FeFlow.Stream.wire(legs.between(FeStage.BOARDS[b], d, FeStage.BOARD_ENDS[b][d]),
                        t -> r.fed[board][dev][at.applyAsInt(t)], WireTier.COPPER, t -> true));
            }
        }
        return out;
    }

    public static String clock(int ticks) {
        final int s = Math.max(0, ticks) / 20;
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    public static List<FeDash.Row> yardRows(FePonderChapters.Deal.Run run) {
        final com.createstats.power.ponder.FePonderDeal.Recording r = run.rec();
        final FePonderChapters.Warp warp = run.warp();
        final java.util.function.IntUnaryOperator at = t -> r.at(warp.tick(t));
        final com.createstats.power.FeReserveBand.Band band = r.setup.band();
        final List<FeDash.Row> rows = new ArrayList<>();
        rows.add(new FeDash.Row(t -> r.fill(at.applyAsInt(t)), t -> reserveWords(r, at.applyAsInt(t)), t -> true,
                new int[] {band.floorPct(), band.ceilingPct(), band.healthyPct()}));
        for (int b = 0; b < 2; b++) {
            final int board = b;
            rows.add(new FeDash.Row(t -> {
                final int a = at.applyAsInt(t);
                return r.asked(board, a) <= 0 ? 0.0 : r.granted(board, a) / (double) r.asked(board, a);
            }, t -> String.format(java.util.Locale.ROOT, "board %d gets %,d of %,d FE/t", board + 1,
                    r.granted(board, at.applyAsInt(t)), r.asked(board, at.applyAsInt(t))), t -> true));
        }
        return rows;
    }

    public static String reserveWords(com.createstats.power.ponder.FePonderDeal.Recording r, int at) {
        final String head = String.format(java.util.Locale.ROOT, "reserve %d%%, %s", r.socPct[at], r.stance[at].label());
        final int in = r.shortIn[at];
        return in < 0 ? head : in == 0 ? head + ", short now" : head + ", short in " + clock(in);
    }

    public static Vec3 boardTag(int b) {
        return top(FeStage.BOARDS[b], 1.3);
    }

    public static Map<String, Vec3> yard() {
        return new HashMap<>();
    }

    public static List<Reveal> controllerBuild() {
        final BlockPos[] ends = FeStage.BC_ENDS;
        final BlockPos[] dev = FeStage.BC_DEVICES;
        return List.of(reveal(4, FeStage.BC), reveal(3, FeStage.BC_MOTOR), reveal(3, dev[0], ends[0]),
                reveal(3, dev[1], ends[1]), reveal(4, dev[2], ends[2]), reveal(3, dev[3], ends[3]),
                reveal(3, dev[4], ends[4]), reveal(0, dev[5], ends[5]));
    }

    public static Vec3 inputsTag() {
        return FeStage.terminal(1).add(0.0, 0.3, 0.0);
    }

    public static Vec3 outputsTag() {
        return FeStage.terminal(4).add(0.0, 0.3, 0.0);
    }

    public static Vec3 floorTag() {
        return top(FeStage.BC_DEVICES[5], 1.0);
    }

    public static Vec3 criticalTag() {
        return top(FeStage.BC_DEVICES[4], 1.0);
    }

    @FunctionalInterface
    public interface PortHang {
        FePaths.Path between(Vec3 from, BlockPos to);
    }

    public static FePaths.Path reversed(FePaths.Path p) {
        final List<Vec3> pts = new ArrayList<>(p.points());
        java.util.Collections.reverse(pts);
        return new FePaths.Path(pts);
    }

    public static List<FeFlow.Stream> controllerStreams(PortHang hang, FePonderChapters.Controller.Run run) {
        final com.createstats.power.ponder.FePonderController.Recording r = run.rec();
        final FePonderChapters.Warp warp = run.warp();
        final java.util.function.IntUnaryOperator at = t -> r.at(warp.tick(t));
        final List<FeFlow.Stream> out = new ArrayList<>();
        final FePaths.Path[] wire = new FePaths.Path[6];
        for (int p = 0; p < 6; p++) wire[p] = hang.between(FeStage.terminal(p), FeStage.BC_ENDS[p]);
        final java.util.function.IntFunction<IntPredicate> there =
                p -> t -> t >= FePonderChapters.Controller.wired(p);
        out.add(FeFlow.Stream.wire(reversed(wire[0]), t -> r.generation[at.applyAsInt(t)], WireTier.COPPER,
                there.apply(0)));
        for (int b = 0; b < 2; b++) {
            final int bank = b;
            out.add(FeFlow.Stream.wire(wire[1 + b], t -> Math.max(0, r.bankFlow[bank][at.applyAsInt(t)]),
                    WireTier.COPPER, there.apply(1 + b)));
            out.add(FeFlow.Stream.wire(reversed(wire[1 + b]), t -> Math.max(0, -r.bankFlow[bank][at.applyAsInt(t)]),
                    WireTier.COPPER, there.apply(1 + b)));
        }
        for (int i = 0; i < 3; i++) {
            final int o = i;
            out.add(FeFlow.Stream.wire(wire[3 + i], t -> r.flow[o][at.applyAsInt(t)], WireTier.COPPER,
                    there.apply(3 + i)));
        }
        return out;
    }

    public static List<FeDash.Row> controllerRows(FePonderChapters.Controller.Run run) {
        final com.createstats.power.ponder.FePonderController.Recording r = run.rec();
        final FePonderChapters.Warp warp = run.warp();
        final java.util.function.IntUnaryOperator at = t -> r.at(warp.tick(t));
        final com.createstats.power.FeReserveBand.Band band = r.setup.band();
        final int draw = com.createstats.power.ponder.FePonderController.Load.RANGE.draw();
        return List.of(
                new FeDash.Row(t -> r.fill(at.applyAsInt(t)), t -> String.format(java.util.Locale.ROOT,
                        "reserve %d%%, %s", r.socPct[at.applyAsInt(t)], r.stance[at.applyAsInt(t)].label()),
                        t -> t >= FePonderChapters.Controller.wired(2),
                        new int[] {band.floorPct(), band.ceilingPct(), band.healthyPct()}),
                new FeDash.Row(t -> r.flow[FePonderChapters.Controller.RANGE][at.applyAsInt(t)] / (double) draw,
                        t -> outputWords(r, at.applyAsInt(t)), t -> t >= FePonderChapters.Controller.wired(3)));
    }

    public static String outputWords(com.createstats.power.ponder.FePonderController.Recording r, int at) {
        final int i = FePonderChapters.Controller.RANGE;
        final com.createstats.power.BatteryControllerPlan.Reason why = r.why[i][at];
        final String figure = String.format(java.util.Locale.ROOT, "output 1: %,d of %,d FE/t", r.flow[i][at],
                com.createstats.power.ponder.FePonderController.Load.RANGE.draw());
        return why == com.createstats.power.BatteryControllerPlan.Reason.SERVED ? figure : figure + ", " + why.label();
    }

    public static Map<String, Vec3> controller() {
        return new HashMap<>();
    }

    public static Map<String, List<Vec3>> keepClear(String chapter) {
        final Map<String, List<Vec3>> m = new HashMap<>();
        switch (chapter) {
            case "fe_cables" -> {
                final List<Vec3> run = new ArrayList<>();
                for (BlockPos p : FeStage.BENCH_RUN) run.add(top(p, 0.2));
                final List<Vec3> route = new ArrayList<>(run);
                route.add(benchAnchor());
                route.add(benchMachine());
                route.add(top(FeStage.BENCH_FAN, 1.0));
                m.put("lay_caption", route);
                final List<Vec3> flow = new ArrayList<>(run);
                flow.add(top(FeStage.BENCH_FAN, 1.0));
                m.put("flow_caption", flow);
                m.put("gauge_caption", List.of(FeStage.GAUGE_FROM, FeStage.GAUGE_TO,
                        FeStage.GAUGE_FROM.lerp(FeStage.GAUGE_TO, 0.5)));
                final List<Vec3> ladder = new ArrayList<>();
                for (int i = 0; i < FeStage.LADDER_Z.length; i++) {
                    ladder.add(ladderTag(i));
                    ladder.add(new Vec3(FeStage.LADDER_X0 + 0.5, 1.1, FeStage.LADDER_Z[i] + 0.5));
                }
                m.put("ladder_caption", ladder);
                m.put("lamp_caption", List.of(top(FeStage.BENCH_LAMP, 0.9), top(FeStage.BENCH_RUN[2], 0.2)));
                m.put("goggles_caption", List.of(benchGoggles(), top(FeStage.BENCH_RUN[1], 0.2)));

                m.put("dye_caption", run);
                final List<Vec3> rip = new ArrayList<>(run);
                rip.add(top(FeStage.BENCH_FAN, 1.0));
                m.put("rip_caption", rip);
            }
            case "fe_pdu" -> {
                m.put("place_caption", List.of(top(FeStage.PDU, 1.0), FePaths.faceCentre(FeStage.PDU, Direction.EAST),
                        top(FeStage.DUCT[FeStage.DUCT.length - 1], 0.3)));
                final List<Vec3> legs = new ArrayList<>();
                legs.add(top(FeStage.PDU, 1.2));
                for (BlockPos p : FeStage.LEG_END) legs.add(top(p, 0.8));
                m.put("legs_caption", legs);
                final List<Vec3> chips = new ArrayList<>();
                for (int i = 0; i < FeStage.MACHINE.length; i++) chips.add(FeStage.chipPoint(i));
                final List<Vec3> tiers = new ArrayList<>(chips);
                tiers.add(FePaths.faceCentre(FeStage.PDU, Direction.NORTH));
                m.put("tiers_caption", tiers);
                m.put("power_caption", chips);
                m.put("triage_caption", chips);
                m.put("payoff_caption", List.of(FeStage.chipPoint(FePonderChapters.FAN), top(FeStage.FAN, 0.9),
                        FeStage.chipPoint(FePonderChapters.RANGE)));
                m.put("screen_caption", List.of(FePaths.faceCentre(FeStage.PDU, Direction.NORTH), top(FeStage.PDU, 1.0)));
            }
            case "fe_wires" -> {
                final List<Vec3> source = List.of(top(FeStage.SPANS_A[0], 0.5), top(FeStage.SPANS_DYNAMO, 0.5),
                        top(FeStage.SPANS_B, 0.5));
                m.put("string_caption", source);
                m.put("power_caption", List.of(spansMiddle(), top(FeStage.SPANS_RANGE, 1.0), top(FeStage.SPANS_B, 0.5)));
                m.put("goggles_caption", List.of(top(FeStage.SPANS_B, 0.5), spansMiddle()));
                m.put("second_caption", List.of(top(FeStage.SPANS_A[1], 0.5), top(FeStage.SPANS_B, 0.5)));
                m.put("third_caption", List.of(top(FeStage.SPANS_A[2], 0.5), top(FeStage.SPANS_B, 0.5)));
                final List<Vec3> rack = new ArrayList<>();
                for (int i = 0; i < FeStage.RACK_EAST.length; i++) {
                    rack.add(top(FeStage.RACK_EAST[i], 0.3));
                    rack.add(top(FeStage.RACK_WEST[i], 0.3));
                }
                m.put("grades_caption", rack);
                m.put("pair_caption", List.of(top(FeStage.SPANS_C, 0.5), top(FeStage.SPANS_D, 0.5),
                        top(FeStage.SPANS_LAMP, 0.6)));
                m.put("tension_caption", List.of(spansMiddle(), top(FeStage.SPANS_B, 0.5)));
                m.put("strip_caption", List.of(top(FeStage.SPANS_B, 0.5), top(FeStage.SPANS_RANGE, 1.0)));
            }
            case "fe_capacitor_bank" -> {
                final Vec3 runMiddle = top(FeStage.BANK_RUN[1], 0.2);
                final Vec3 range = top(FeStage.BANK_RANGE, 1.0);
                final Vec3 spare = top(FeStage.SPARE, 1.0);
                m.put("place_caption", List.of(bankTop(), bankFace()));
                m.put("power_caption", List.of(runMiddle, range, bankTop()));
                m.put("stop_caption", List.of(top(FeStage.BANK_DYNAMO, 1.0), bankTop(), range));
                m.put("never_caption", List.of(spare, top(FeStage.SPARE_LINK, 0.2), bankTop(), range));
                m.put("break_caption", List.of(spare, range));
                m.put("merge_caption", List.of(bankTop(), bankFace(), runMiddle));
            }
            case "fe_battery_controller" -> {
                final Vec3 bc = top(FeStage.BC, 1.2);
                final Vec3 range = top(FeStage.BC_DEVICES[3], 1.0);
                m.put("terminals_caption", List.of(bc, inputsTag(), outputsTag()));
                final List<Vec3> ends = new ArrayList<>();
                for (BlockPos p : FeStage.BC_ENDS) ends.add(top(p, 0.6));
                m.put("wires_caption", ends);
                m.put("power_caption", List.of(top(FeStage.BC_DYNAMO, 1.0), bc));
                m.put("floor_caption", List.of(top(FeStage.BC_DEVICES[5], 0.9), bc));
                m.put("stop_caption", List.of(bc, range));
                m.put("droop_caption", List.of(range, bc));
                m.put("critical_caption", List.of(top(FeStage.BC_DEVICES[4], 0.9), range));
                m.put("refill_caption", List.of(range, bc, top(FeStage.BC_DYNAMO, 1.0)));
                m.put("screen_caption", List.of(bc, FePaths.faceCentre(FeStage.BC, Direction.NORTH)));
            }
            case "fe_board_and_reserve" -> {
                final Vec3 bc = top(FeStage.YC, 1.2);
                final List<Vec3> boards = List.of(top(FeStage.BOARDS[0], 1.0), top(FeStage.BOARDS[1], 1.0), bc);
                m.put("ask_caption", boards);
                m.put("answer_caption", boards);
                m.put("forecast_caption", List.of(bc));
                m.put("split_caption", List.of(top(FeStage.BOARD_MACHINES[1][1], 1.0), top(FeStage.BOARD_MACHINES[0][0], 1.0),
                        boardTag(1)));
                m.put("floor_caption", List.of(top(FeStage.BOARD_MACHINES[0][0], 1.0), bc));
                m.put("feed_caption", List.of(top(FeStage.BOARD_FEEDS[0], 0.7), top(FeStage.BOARDS[0], 1.0),
                        top(FeStage.BOARD_FEEDS[1], 0.7), bc));
            }
            case "fe_pdu_auto" -> {
                final List<Vec3> chips = new ArrayList<>();
                for (int i = 0; i < FeStage.MACHINE.length; i++) chips.add(FeStage.chipPoint(i));
                m.put("strip_caption", chips);
                final List<Vec3> ride = new ArrayList<>(chips);
                ride.add(top(FeStage.LEVER, 0.5));
                ride.add(top(FeStage.PDU, 1.0));
                m.put("ride_caption", ride);
                m.put("mark_caption", chips);
                m.put("skip_caption", chips);
                m.put("deeper_caption", chips);
                m.put("restored_caption", chips);
                final List<Vec3> locks = new ArrayList<>(chips);
                locks.add(FePaths.faceCentre(FeStage.PDU, Direction.NORTH));
                m.put("locks_caption", locks);
            }
            default -> {
            }
        }
        return m;
    }
}
