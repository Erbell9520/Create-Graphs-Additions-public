package com.createstats.book.specimen;

import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.book.content.Configs;
import com.createstats.gui.pdu.PduStatusLine;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.gui.pdu.PduTopology;
import com.createstats.power.DynamoTier;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import com.createstats.power.PduAutoController;
import com.createstats.power.PduDeviceIds;
import com.createstats.power.PduMode;
import com.createstats.power.PduLegPlan;
import com.createstats.power.PduReason;
import com.createstats.power.PduStudWiring;
import com.createstats.power.PduStuds;
import com.createstats.power.WireTier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class PduPlates {
    private PduPlates() {
    }

    public static final String OVERVIEW = "pdu.overview";
    public static final String LOADS = "pdu.loads";
    public static final String LIGHTING = "pdu.lighting";
    public static final String WIRING = "pdu.wiring";
    public static final String FLOW = "pdu.flow";
    public static final String LEGS = "pdu.legs";
    public static final String HISTORY = "pdu.history";

    public static final String IN_OUT = "pdu.in_out";

    public static final List<String> IDS = List.of(OVERVIEW, LOADS, LIGHTING, WIRING, FLOW, LEGS, HISTORY, IN_OUT);

    private static final Map<String, Integer> VIEWS = Map.of(OVERVIEW, 0, LOADS, 1, LIGHTING, 2, WIRING, 3, FLOW, 4,
            LEGS, 5, HISTORY, 6, IN_OUT, 7);

    public static final int HISTORY_RANGE = 0;

    public static final int W = PduTheme.Breakpoint.NORMAL.idealW;
    public static final int H = PduTheme.Breakpoint.NORMAL.idealH;

    public static final String FACE = "power_distribution_unit.face";

    public static final String RECORD = "/assets/create_stats/book/pdu_plates.json";

    public static boolean isPlate(String id) {
        return IDS.contains(id);
    }

    public static int view(String id) {
        final Integer v = VIEWS.get(id);
        if (v == null) {
            throw new IllegalArgumentException("not a plate of the Power Distribution Unit: " + id);
        }
        return v;
    }

    public static BlockPos selected(String id) {
        return LOADS.equals(id) ? ARC_FURNACE.pos() : null;
    }

    public static boolean selectAll(String id) {
        return LIGHTING.equals(id);
    }

    public static final int BUFFER_PCT = 38;

    public static final int DYNAMOS = 2;

    public record Device(String block, FePriority tier, int cost, int leg, boolean lighting, FeLock lock, BlockPos pos) {
    }

    static final BlockPos BOARD = new BlockPos(214, 64, -352);

    private static BlockPos at(int dx, int dy, int dz) {
        return BOARD.offset(dx, dy, dz);
    }

    public static BlockPos board() {
        return BOARD;
    }

    static final Device SECURITY = new Device("home_security_panel", FePriority.CRITICAL, 64, 0, false, FeLock.NONE,
            at(-3, 0, 5));
    static final Device REPELLER = new Device("mob_repeller", FePriority.HIGH, 256, 1, false, FeLock.NONE,
            at(2, 0, 7));
    static final Device DISPLAY = new Device("stats_display_mk3", FePriority.NORMAL, 96, 2, false, FeLock.NONE,
            at(5, 0, 4));
    static final Device HOME = new Device("smart_home_panel", FePriority.NORMAL, 48, 2, false, FeLock.NONE,
            at(8, 0, 3));
    static final Device SMELTER_A = new Device("induction_smelter", FePriority.LOW, 160, 3, false, FeLock.NONE,
            at(-9, 0, 3));
    static final Device SMELTER_B = new Device("induction_smelter", FePriority.LOW, 160, 3, false, FeLock.NONE,
            at(-8, 1, 3));
    static final Device DRIVE = new Device("fe_motor_mk1", FePriority.LOW, 192, 4, false, FeLock.NONE,
            at(11, 0, -3));
    static final Device ARC_FURNACE = new Device("arc_furnace", FePriority.LOW, 512, 4, false, FeLock.NONE,
            at(10, 0, -4));
    static final Device LAMP_A = new Device("pendant_lamp", FePriority.OPTIONAL, 1, 5, true, FeLock.NONE,
            at(-6, -1, -4));
    static final Device LAMP_B = new Device("pendant_lamp", FePriority.OPTIONAL, 1, 5, true, FeLock.NONE,
            at(-6, 1, -4));
    static final Device LAMP_C = new Device("pendant_lamp", FePriority.OPTIONAL, 1, 5, true, FeLock.FORCE_OFF,
            at(-7, 0, -4));
    static final Device STAGE = new Device("stage_light", FePriority.OPTIONAL, 16, 5, true, FeLock.FORCE_ON,
            at(-6, 0, -5));

    public static final List<Device> DEVICES = List.of(SECURITY, REPELLER, DISPLAY, HOME, SMELTER_A, SMELTER_B, DRIVE,
            ARC_FURNACE, LAMP_A, LAMP_B, LAMP_C, STAGE);

    static final WireTier[] STUD_WIRE = {WireTier.COPPER, WireTier.COPPER, WireTier.COPPER, WireTier.INSULATED,
            WireTier.INSULATED, WireTier.COPPER};
    static final BlockPos[] STUD_CONNECTOR = {at(-3, 0, 4), at(2, 0, 6), at(5, 0, 3),
            at(-6, 0, 3), at(8, 0, -3), at(-3, 0, -4)};

    static final BlockPos[] SURFACE = {at(6, 0, 3), at(7, 0, 3), at(-4, 0, -4),
            at(-6, 0, -4)};
    static final BlockPos[] TRAY = {at(-7, 0, 3), at(-8, 0, 3), at(9, 0, -3),
            at(10, 0, -3)};
    static final BlockPos SWITCH = at(-5, 0, -4);

    public static int generation() {
        return DYNAMOS * (int) Math.round(DynamoTier.STANDARD.fePerRpm()
                * com.createstats.block.KineticDynamoBlock.TOP_RPM);
    }

    public static int stored() {
        return (int) ((long) Configs.pduBuffer() * BUFFER_PCT / 100L);
    }

    public static int upkeep() {
        return Math.max(0, Configs.pduIdleDrain() / net.minecraft.SharedConstants.TICKS_PER_SECOND);
    }

    static int floorEnergy() {
        return Math.round(Configs.pduBuffer() * (Configs.pduFloorPct() / 100.0f));
    }

    static int legRating(int leg) {
        return leg < STUD_WIRE.length ? PduStudWiring.ceiling(PowerDistributionUnitBE.studRate(leg),
                PowerConnectorBE.baseTransferRate(), STUD_WIRE[leg]) : 0;
    }

    static int ceiling() {
        int total = 0;
        for (int leg = 0; leg < PowerDistributionUnitBE.LEGS; leg++) {
            total += legRating(leg);
        }
        return total;
    }

    public record Decision(PduAutoController.Plan plan, int runBudget, int admitBudget, int criticalBudget, int margin,
                           int endurance) {
    }

    private static Decision decision;

    public static synchronized Decision decide() {
        if (decision != null) {
            return decision;
        }
        final int gen = generation();
        final int standing = upkeep();
        final int energy = stored();
        final int floor = floorEnergy();
        final int ride = Math.max(0, energy - floor) / Configs.pduRideHorizonTicks();
        final int ceiling = ceiling();
        final boolean surplus = energy > Math.round(Configs.pduBuffer() * (Configs.pduRestoreSocPct() / 100.0f));
        if (surplus) {
            throw new IllegalStateException("the example is meant to sit under the restore mark");
        }
        final int run = Math.min(ceiling, Math.max(0, gen - standing) + ride);
        final int admit = Math.min(ceiling, Math.max(0, gen - standing));
        final int critical = Math.min(ceiling, gen + ride);
        final int margin = PduAutoController.restoreMargin(Math.round(gen * (Configs.pduBurstMarginPct() / 100.0f)),
                admit, Math.max(0, gen - standing));
        final List<PduAutoController.Device> devices = new ArrayList<>();
        for (final Device d : DEVICES) {
            if (d.lock() != FeLock.FORCE_OFF) {
                devices.add(new PduAutoController.Device(d.pos().asLong(), d.tier().ordinal(), d.cost(), d.lock(),
                        FePriority.DEFAULT.ordinal()));
            }
        }
        final PduAutoController controller = new PduAutoController();
        final PduAutoController.Plan plan = controller.decide(devices,
                new PduAutoController.Supply(run, admit, margin, energy < floor, false, ceiling < gen + ride, critical,
                        0, false, false, 0), 1_000L);
        final int netDrain = Math.max(0, plan.admittedDemand() + standing - gen);
        final int endurance = netDrain > 0 ? Math.max(0, energy - floor) / netDrain : Integer.MAX_VALUE;
        decision = new Decision(plan, run, admit, critical, margin, endurance);
        return decision;
    }

    public static boolean runs(Device d) {
        return d.lock() != FeLock.FORCE_OFF && decide().plan().isRunning(d.pos().asLong());
    }

    public static PduReason reasonOf(Device d) {
        return d.lock() == FeLock.FORCE_OFF ? PduReason.LOCKED_OFF : decide().plan().reasonFor(d.pos().asLong());
    }

    public static List<Device> shed() {
        final List<Device> out = new ArrayList<>();
        for (final Device d : DEVICES) {
            if (d.lock() != FeLock.FORCE_OFF && !runs(d)) {
                out.add(d);
            }
        }
        return out;
    }

    public static int demand() {
        int total = 0;
        for (final Device d : DEVICES) {
            if (d.lock() != FeLock.FORCE_OFF) {
                total += d.cost();
            }
        }
        return total;
    }

    public static String statusLine() {
        final Decision d = decide();
        final int shed = shed().size();
        final int spare = Math.max(0, generation() - demand() - upkeep());
        return PduStatusLine.derive(PduMode.AUTO, true, false, false, false, shed, spare, d.endurance(),
                BUFFER_PCT / 100.0f, Configs.pduFloorPct(), false, -1, 0, -1);
    }

    public static int shedFe() {
        int total = 0;
        for (final Device d : DEVICES) {
            if (reasonOf(d).isShed()) {
                total += d.cost();
            }
        }
        return total;
    }

    public static String verdict() {
        final CompoundTag t = fixtureTag(null, true);
        final com.createstats.gui.pdu.PduFlowModel.Budgets b = new com.createstats.gui.pdu.PduFlowModel.Budgets(
                t.getInt("Bud"), t.getInt("BudAdm"), t.getInt("BudCrit"), t.getInt("BudDrop"), t.getInt("BudStand"),
                t.getInt("BudClaim"), t.getInt("BudRide"), t.getBoolean("BudLow"), t.getBoolean("BudLnk"),
                t.getBoolean("Srp"), t.getBoolean("Thr"), t.getInt("Ceil"));
        final int[] byReason = new int[PduReason.values().length];
        int count = 0;
        for (final Device d : DEVICES) {
            if (reasonOf(d).isShed()) {
                byReason[reasonOf(d).ordinal()] += d.cost();
                count++;
            }
        }
        return com.createstats.gui.pdu.PduFlowModel.verdict(PduMode.AUTO, b, generation(), byReason, count);
    }

    public static com.createstats.gui.pdu.PduDayOutlook dayOutlook() {
        final CompoundTag t = fixtureTag(null, true);
        final com.createstats.power.SolarCurve.Sky[] skies = com.createstats.power.SolarCurve.Sky.values();
        return new com.createstats.gui.pdu.PduDayOutlook(Math.max(0, t.getInt("SerStep")), t.getIntArray("SerRate"),
                t.getIntArray("SerClr"), t.getLong("SerCold"), t.getLong("SerDay"), Math.max(0, t.getInt("OutCrit")),
                Math.max(0, t.getInt("OutShort")), t.getBoolean("OutSure"), Math.max(0, t.getInt("OutOwn")),
                t.getBoolean("OutOwnHeld"), Math.max(0, t.getInt("OutPeers")), Math.max(0, t.getInt("DayWarm")),
                skies[Math.floorMod(t.getInt("WxNow"), skies.length)],
                t.contains("WxIn") ? t.getInt("WxIn") : com.createstats.power.FeSupplyOutlook.UNKNOWN_CHANGE,
                skies[Math.floorMod(t.getInt("WxThen"), skies.length)]);
    }

    public static CompoundTag fixtureTag(Function<String, String> names, boolean studWires) {
        final Decision decision = decide();
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Energy", stored());
        final int served = decision.plan().admitted().size();
        final int shed = shed().size();
        tag.putInt("Served", served);
        tag.putInt("Shed", shed);
        tag.putInt("Delivered", decision.plan().admittedDemand());
        tag.putInt("Mode", PduMode.AUTO.ordinal());
        tag.putInt("Gen", generation());
        tag.putInt("Dmd", demand());
        tag.putInt("Bud", decision.runBudget());
        tag.putInt("BudAdm", decision.admitBudget());
        tag.putInt("BudCrit", Math.max(decision.criticalBudget(), decision.runBudget()));
        tag.putInt("BudStand", upkeep());
        tag.putInt("BudRide", Math.max(0, decision.runBudget() - Math.min(ceiling(), Math.max(0,
                generation() - upkeep()))));
        tag.putInt("Svd", decision.plan().admittedDemand());
        tag.putInt("End", decision.endurance());
        tag.putBoolean("Srp", false);
        tag.putBoolean("Thr", false);
        tag.putInt("Ceil", ceiling());
        tag.putInt("Cap", Configs.pduBuffer());
        tag.putInt("Flr", Configs.pduFloorPct());
        tag.putInt("Upk", upkeep());

        tag.putInt("InFe", generation());

        final Map<String, Integer> seen = new LinkedHashMap<>();
        final Map<String, Integer> count = new LinkedHashMap<>();
        for (final Device d : DEVICES) {
            count.merge(d.block(), 1, Integer::sum);
        }
        final ListTag roster = new ListTag();
        for (final Device d : DEVICES) {
            final CompoundTag e = new CompoundTag();
            e.putLong("P", d.pos().asLong());
            final String key = "block.create_stats." + d.block();
            if (names == null) {
                e.putString("Nk", key);
            } else {
                e.putString("N", names.apply(key));
            }
            final int id = count.get(d.block()) > 1 ? seen.merge(d.block(), 1, Integer::sum) : 0;
            e.putInt("Id", id);
            e.putInt("Leg", d.leg());
            e.putInt("T", d.tier().ordinal());
            final boolean running = runs(d);
            final int draw = running ? d.cost() : 0;
            e.putInt("D", draw);
            if (d.cost() != draw) {
                e.putInt("C", d.cost());
            }
            e.putBoolean("S", d.lock() != FeLock.FORCE_OFF && !running);
            e.putInt("L", d.lock().ordinal());
            e.putInt("R", reasonOf(d).ordinal());
            e.putBoolean("Lt", d.lighting());
            roster.add(e);
        }
        tag.put("Roster", roster);

        final PduLegPlan.Screened screened = legScreen();
        final int[] legs = new int[PowerDistributionUnitBE.LEGS * 4];
        for (int leg = 0; leg < PowerDistributionUnitBE.LEGS; leg++) {
            legs[leg * 4] = 1 | (FePriority.DEFAULT.ordinal() << 2);
            legs[leg * 4 + 1] = legRating(leg);
            legs[leg * 4 + 2] = screened.legDemand()[leg];
            legs[leg * 4 + 3] = screened.legAdmitted()[leg];
        }
        tag.putIntArray("Legs", legs);

        tag.putInt("SupPri", generation());
        tag.putInt("SupObs", generation());
        tag.putBoolean("SupAll", true);
        tag.putInt("Src", DYNAMOS);
        tag.putInt("EffFlr", Configs.pduFloorPct());

        final int conductors = STUD_CONNECTOR.length + SURFACE.length + TRAY.length;
        final int capacity = STUD_CONNECTOR.length * Configs.connectorBuffer()
                + (SURFACE.length + TRAY.length) * com.createstats.blockentity.CableBE.SEGMENT_BUFFER;
        tag.putInt("WireN", conductors);
        tag.putInt("WireE", capacity);
        tag.putInt("WireC", capacity);
        if (studWires) {
            final ListTag spans = new ListTag();
            for (int stud = 0; stud < STUD_WIRE.length; stud++) {
                final CompoundTag e = new CompoundTag();
                e.putByte("S", (byte) stud);
                e.putLong("P", STUD_CONNECTOR[stud].asLong());
                e.putByte("T", (byte) STUD_WIRE[stud].ordinal());
                spans.add(e);
            }
            tag.put("StudWires", spans);
        }
        final ListTag topo = new ListTag();
        for (final PduTopology.BranchNode node : topology().nodes()) {
            final CompoundTag e = new CompoundTag();
            e.putLong("P", node.pos().asLong());
            e.putByte("K", node.kind());
            e.putByte("T", node.tier());
            e.putBoolean("O", node.open());
            e.putInt("Pa", node.parentIndex());
            topo.add(e);
        }
        tag.put("Topo", topo);
        tag.putBoolean("TopoTrunc", false);
        return tag;
    }

    public static PduLegPlan.Screened legScreen() {
        final List<PduLegPlan.Device> devices = new ArrayList<>();
        for (final Device d : DEVICES) {
            if (d.lock() != FeLock.FORCE_OFF) {
                devices.add(new PduLegPlan.Device(d.pos().asLong(), d.leg(), d.tier().ordinal(), d.cost(), d.lock()));
            }
        }
        final PduLegPlan.Leg[] legs = new PduLegPlan.Leg[PowerDistributionUnitBE.LEGS];
        for (int leg = 0; leg < legs.length; leg++) {
            legs[leg] = new PduLegPlan.Leg(true, legRating(leg), FePriority.DEFAULT.ordinal());
        }
        return PduLegPlan.screen(devices, legs);
    }

    public static PduTopology.Result topology() {
        final List<PduTopology.RawNode> raws = new ArrayList<>();
        final List<BlockPos> wires = new ArrayList<>(List.of(STUD_CONNECTOR));
        raws.add(new PduTopology.RawNode(BOARD, kind(PduTopology.Kind.PDU), (byte) 0, false, wires));
        for (final BlockPos c : STUD_CONNECTOR) {
            raws.add(new PduTopology.RawNode(c, kind(PduTopology.Kind.CONNECTOR), (byte) 0, false, List.of(BOARD)));
        }
        for (final BlockPos c : SURFACE) {
            raws.add(new PduTopology.RawNode(c, kind(PduTopology.Kind.CABLE), (byte) 0, false, List.of()));
        }
        for (final BlockPos c : TRAY) {
            raws.add(new PduTopology.RawNode(c, kind(PduTopology.Kind.TRAY), (byte) 0, false, List.of()));
        }
        raws.add(new PduTopology.RawNode(SWITCH, kind(PduTopology.Kind.SWITCH), (byte) 0, false, List.of()));
        for (final Device d : DEVICES) {
            raws.add(new PduTopology.RawNode(d.pos(), kind(PduTopology.Kind.DEVICE), (byte) d.tier().ordinal(), false,
                    List.of()));
        }
        return PduTopology.build(raws, BOARD, PduTopology.MAX_NODES);
    }

    private static byte kind(PduTopology.Kind k) {
        return (byte) k.ordinal();
    }

    public static String label(Device d, Function<String, String> names) {
        int count = 0;
        int index = 0;
        for (final Device o : DEVICES) {
            if (o.block().equals(d.block())) {
                count++;
                if (o == d) {
                    index = count;
                }
            }
        }
        final int id = count > 1 ? index : 0;
        return PduDeviceIds.label(names.apply("block.create_stats." + d.block()), id, id <= 0);
    }

    public static int studsWired() {
        return STUD_WIRE.length;
    }

    private static final Map<String, SpecimenAnatomy> ANATOMY = new java.util.HashMap<>();

    public static synchronized SpecimenAnatomy anatomy(String id) {
        return ANATOMY.computeIfAbsent(id, k -> new SpecimenAnatomy(k, W, H, drawing(k).pins(),
                SpecimenAnatomy.Kind.SCREEN));
    }

    public static List<int[]> print(String id) {
        return print(drawing(id));
    }

    public static List<int[]> print(Drawing d) {
        final List<int[]> out = new ArrayList<>();
        int[] word = null;
        for (final Text t : d.text()) {
            final boolean letter = t.s().length() == 1;
            if (word != null && letter && t.y() == word[1] && t.x() >= word[0] + word[2]
                    && t.x() <= word[0] + word[2] + 2) {
                word[2] = t.x() + t.w() - word[0];
                continue;
            }
            if (word != null) {
                out.add(word);
                word = null;
            }
            if (letter) {
                word = new int[]{t.x(), t.y(), t.w(), t.h()};
            } else {
                out.add(new int[]{t.x(), t.y(), t.w(), t.h()});
            }
        }
        if (word != null) {
            out.add(word);
        }
        for (final Map.Entry<String, int[]> p : d.parts().entrySet()) {
            if (p.getKey().startsWith("node.")) {
                out.add(p.getValue());
            }

            if (p.getKey().matches("rail\\.\\d+")) {
                final int[] r = p.getValue();
                out.add(new int[]{r[0] + (r[2] - RAIL_ICON) / 2 - 1, r[1] + (r[3] - RAIL_ICON) / 2 - 1, RAIL_ICON + 2,
                        RAIL_ICON + 2});
            }

            if (p.getKey().startsWith("row.") && d.parts().containsKey("head.name")) {
                final int[] r = p.getValue();
                final int pipX = d.part("head.name")[0] - com.createstats.gui.pdu.PduLoadColumns.PIP_W;
                out.add(new int[]{pipX, r[1] + (r[3] - 6) / 2, 6, 6});
                out.add(new int[]{d.part("head.lock")[0], r[1] + (r[3] - 8) / 2, 8, 8});
            }
        }

        if (d.parts().containsKey("flow.viewport")) {
            final com.createstats.book.scene.BlockView v = sceneView(d.part("flow.viewport"));
            for (final BlockPos c : scenePositions()) {
                final double x0 = (c.getX() - BOARD.getX()) * 16.0;
                final double y0 = (c.getY() - BOARD.getY()) * 16.0;
                final double z0 = (c.getZ() - BOARD.getZ()) * 16.0;
                final double[] r = v.boundsPx(new double[]{x0, y0, z0, x0 + 16, y0 + 16, z0 + 16});
                final int rx = (int) Math.floor(r[0]);
                final int ry = (int) Math.floor(r[1]);
                out.add(new int[]{rx, ry, (int) Math.ceil(r[2]) - rx, (int) Math.ceil(r[3]) - ry});
            }
        }
        return out;
    }

    private static final int RAIL_ICON = 16;

    public static Drawing drawing(String id) {
        final Drawing d = recorded().get(id);
        if (d == null) {
            throw new IllegalStateException(RECORD + " has no plate " + id + ": measure the plates (PduPlatesTest)");
        }
        return d;
    }

    public static final int[][] DRAWN = {{1, 2}, {2, 3}};

    public static SpecimenAnatomy choose(String id, Drawing d) {
        final List<com.createstats.book.layout.Callouts.Choice> c = new ArrayList<>();

        c.add(framed("views", List.of(d.part("rail." + view(id)))));
        switch (id) {
            case OVERVIEW -> {
                c.add(framed("mode", List.of(d.part("mode"))));
                c.add(framed("assign", List.of(d.part("assign"))));
                c.add(framed("supply", List.of(d.part("card.supply"))));
                c.add(framed("demand", List.of(d.part("card.demand"))));
                c.add(framed("buffer", List.of(d.part("card.buffer"))));
                c.add(framed("gauge", List.of(d.part("gauge"))));
                c.add(framed("balance", List.of(d.part("balance"))));
                c.add(framed("status", List.of(d.part("card.status"))));
                c.add(framed("roll", List.of(d.part("card.roll"))));
                c.add(beside("footer", List.of(rect(d.containing(statusLine())))));
            }
            case LOADS -> {
                c.add(framed("search", List.of(d.part("search"))));
                c.add(framed("group", List.of(span(d.part("group.0"), d.part("group.3")))));
                c.add(framed("shed", List.of(d.part("shed"))));
                c.add(rowStart("grouprow", groupRows(d)));
                c.add(rowStart("row", rows(d, SECURITY, REPELLER, DISPLAY, HOME)));
                c.add(framed("tier", chips(d, SECURITY, REPELLER, DISPLAY, HOME, SMELTER_A, SMELTER_B)));
                c.add(framed("pip", pips(d, ARC_FURNACE, DRIVE)));
                c.add(bars("drawcol", cells(d, "draw", REPELLER, DISPLAY, HOME, SMELTER_A, SMELTER_B)));
                c.add(framed("inspector", List.of(d.part("drawer"))));
                c.add(framed("inspector_tier", List.of(span(d.part("drawer.tier.0"), d.part("drawer.tier.4")))));
                c.add(framed("locate", List.of(d.part("drawer.locate"))));
            }
            case LIGHTING -> {
                c.add(framed("count", List.of(d.part("lighting.header"))));
                c.add(framed("setall", List.of(span(d.part("lighting.setall"), d.part("lighting.tier.4")))));
                c.add(framed("bulk", List.of(span(d.part("bulk.tier.0"), d.part("bulk.tier.4")))));
                c.add(framed("bulk_lock", List.of(span(d.part("bulk.lockoff"), d.part("bulk.auto")))));
                c.add(framed("bulk_clear", List.of(d.part("bulk.clear"))));
                c.add(beside("state", words(d, "status", STAGE, LAMP_C, LAMP_A, LAMP_B)));
                c.add(beside("lock", locks(d, LAMP_C, STAGE, LAMP_A, LAMP_B)));
                c.add(framed("heads", List.of(d.part("head.draw"), d.part("head.status"), d.part("head.name"))));
            }
            case WIRING -> {
                c.add(framed("pdu", List.of(drawn(d.part("node." + BOARD.asLong())))));
                c.add(switchPin(d.part("node." + SWITCH.asLong())));
                c.add(framed("device", nodes(d, SECURITY, REPELLER, DISPLAY, HOME, SMELTER_A, SMELTER_B, LAMP_A,
                        LAMP_B)));
                c.add(framed("shed_device", nodes(d, ARC_FURNACE, DRIVE)));
                c.add(framed("locked_on", nodes(d, STAGE)));
                c.add(framed("locked_off", nodes(d, LAMP_C)));
                c.add(framed("card", List.of(d.part("card.wiring"))));
            }
            case FLOW -> {
                c.add(framed("scene", List.of(d.part("flow.scene"))));
                c.add(beside("hint", List.of(rect(d.containing("Drag to turn")))));
                c.add(beside("tally", List.of(rect(d.containing("marked red")))));
                c.add(framed("working", List.of(d.part("flow.working"))));

                c.add(beside("supply", List.of(heading(d, "SUPPLY"))));
                c.add(beside("budget", List.of(heading(d, "BUDGET"))));
                c.add(beside("demand", List.of(heading(d, "DEMAND"))));
            }
            case LEGS -> {
                c.add(framed("row", List.of(d.part("leg.row.0"), d.part("leg.row.1"), d.part("leg.row.2"))));
                c.add(framed("tab", List.of(d.part("leg.tab.3"), d.part("leg.tab.4"), d.part("leg.tab.5"))));

                c.add(overUnder("state", texts(d, "Live")));
                c.add(framed("rank", partsStarting(d, "leg.rank.")));
                c.add(framed("breaker", partsStarting(d, "leg.breaker.")));
                c.add(overUnder("unwired", texts(d, "Not wired").subList(0, 2)));
                c.add(beside("back", List.of(rect(d.containing("Back output")))));
            }
            case HISTORY -> {
                c.add(within("supply", d.part("history.area")));
                c.add(along("demand", d.part("history.line")));
                c.add(framed("ranges", List.of(span(d.part("range.0"), d.part("range.2")))));
                c.add(framed("day", List.of(d.part("range.3"))));
                c.add(framed("plot", List.of(d.part("history.plot"))));
                c.add(beside("scale", List.of(rect(d.containing(" FE/t")))));
                c.add(framed("rail", List.of(d.part("history.rail"))));
            }
            case IN_OUT -> {
                c.add(framed("toggle", List.of(d.part("chain.toggle"))));
                c.add(framed("source", List.of(d.part("inout.box.generators"))));
                c.add(framed("from_buffer", List.of(d.part("inout.box.from_buffer"))));
                c.add(framed("bus", List.of(d.part("inout.bus"))));
                c.add(framed("sink", List.of(d.part("inout.box.high"), d.part("inout.box.low"))));
                c.add(framed("others", List.of(d.part("inout.box.other_out"))));
                c.add(framed("shed", List.of(d.part("inout.shed"))));
                c.add(framed("ladder_supply", List.of(d.part("inout.ladder.supply"))));
                c.add(framed("ladder_demand", List.of(d.part("inout.ladder.demand"))));
                c.add(framed("admit", List.of(d.part("inout.marker.admit"))));
                c.add(framed("run", List.of(d.part("inout.marker.run"))));
                c.add(framed("critical", List.of(d.part("inout.marker.critical"))));
            }
            default -> throw new IllegalArgumentException(id);
        }

        final List<int[]> over = new ArrayList<>();
        if (d.parts().containsKey("drawer")) {
            over.add(d.part("drawer"));
        }
        final List<int[]> print = print(d);
        final List<com.createstats.book.layout.Callouts.Choice> seen = new ArrayList<>();
        for (final com.createstats.book.layout.Callouts.Choice ch : c) {
            final boolean ownsOverlay = ch.id().startsWith("inspector") || ch.id().equals("locate");
            final List<SpecimenAnatomy.Hot> keep = new ArrayList<>();
            for (final SpecimenAnatomy.Hot h : ch.places()) {
                if (!onALetter(h, print) && (ownsOverlay || !inside(h.anchorX(), h.anchorY(), over))) {
                    keep.add(h);
                }
            }
            if (keep.isEmpty()) {
                throw new IllegalStateException(id + ": " + ch.id() + " has no place a reader can see");
            }
            seen.add(new com.createstats.book.layout.Callouts.Choice(ch.id(), keep));
        }
        return com.createstats.book.layout.Callouts.cleanest(id, W, H, SpecimenAnatomy.Kind.SCREEN, seen, print,
                DRAWN);
    }

    private static boolean onALetter(SpecimenAnatomy.Hot h, List<int[]> print) {
        for (final int[] r : print) {
            if (r[0] <= h.x() && r[1] <= h.y() && r[0] + r[2] >= h.x() + h.w() && r[1] + r[3] >= h.y() + h.h()) {
                continue;
            }
            if (h.anchorX() + 3 > r[0] && h.anchorX() - 2 < r[0] + r[2] && h.anchorY() + 3 > r[1]
                    && h.anchorY() - 2 < r[1] + r[3]) {
                return true;
            }
        }
        return false;
    }

    private static boolean inside(int x, int y, List<int[]> rects) {
        for (final int[] r : rects) {
            if (x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]) {
                return true;
            }
        }
        return false;
    }

    private static com.createstats.book.layout.Callouts.Choice framed(String id, List<int[]> copies) {
        final List<SpecimenAnatomy.Hot> places = new ArrayList<>();
        final java.util.Set<Long> seen = new java.util.HashSet<>();
        for (final int[] r : copies) {
            final int x0 = r[0];
            final int y0 = r[1];
            final int x1 = r[0] + Math.max(1, r[2]) - 1;
            final int y1 = r[1] + Math.max(1, r[3]) - 1;
            for (final double f : new double[]{0.5, 0.0, 1.0, 0.25, 0.75}) {
                final int mx = x0 + (int) Math.round((x1 - x0) * f);
                final int my = y0 + (int) Math.round((y1 - y0) * f);
                for (final int[] p : new int[][]{{x0, my}, {x1, my}, {mx, y0}, {mx, y1}}) {
                    if (seen.add(((long) p[0] << 32) ^ (p[1] & 0xffffffffL))) {
                        places.add(new SpecimenAnatomy.Hot(id, r[0], r[1], Math.max(1, r[2]), Math.max(1, r[3]), p[0],
                                p[1], true));
                    }
                }
            }
        }
        return new com.createstats.book.layout.Callouts.Choice(id, places);
    }

    private static com.createstats.book.layout.Callouts.Choice rowStart(String id, List<int[]> copies) {
        final List<SpecimenAnatomy.Hot> places = new ArrayList<>();
        for (final int[] r : copies) {
            for (final double f : new double[]{0.5, 0.25, 0.75, 0.375, 0.625}) {
                places.add(new SpecimenAnatomy.Hot(id, r[0], r[1], r[2], r[3], r[0],
                        r[1] + (int) Math.round((r[3] - 1) * f), true));
            }
        }
        return new com.createstats.book.layout.Callouts.Choice(id, places);
    }

    private static com.createstats.book.layout.Callouts.Choice bars(String id, List<int[]> cells) {
        final List<SpecimenAnatomy.Hot> places = new ArrayList<>();
        for (final int[] r : cells) {
            places.add(new SpecimenAnatomy.Hot(id, r[0], r[1], r[2], r[3], r[0] + 1, r[1] + r[3] - 3, true));
        }
        return new com.createstats.book.layout.Callouts.Choice(id, places);
    }

    private static final int BESIDE = 4;

    private static com.createstats.book.layout.Callouts.Choice beside(String id, List<int[]> copies) {
        final List<SpecimenAnatomy.Hot> places = new ArrayList<>();
        for (final int[] r : copies) {
            final int my = r[1] + r[3] / 2;
            places.add(new SpecimenAnatomy.Hot(id, r[0], r[1], r[2], r[3], r[0] + r[2] - 1 + BESIDE, my, true));
            places.add(new SpecimenAnatomy.Hot(id, r[0], r[1], r[2], r[3], r[0] - BESIDE, my, true));
        }
        return new com.createstats.book.layout.Callouts.Choice(id, places);
    }

    private static int[] span(int[] a, int[] b) {
        final int x0 = Math.min(a[0], b[0]);
        final int y0 = Math.min(a[1], b[1]);
        final int x1 = Math.max(a[0] + a[2], b[0] + b[2]);
        final int y1 = Math.max(a[1] + a[3], b[1] + b[3]);
        return new int[]{x0, y0, x1 - x0, y1 - y0};
    }

    private static int[] rect(Text t) {
        return new int[]{t.x(), t.y(), t.w(), t.h()};
    }

    private static List<int[]> groupRows(Drawing d) {
        final List<int[]> out = new ArrayList<>();
        for (final Map.Entry<String, int[]> p : d.parts().entrySet()) {
            if (p.getKey().startsWith("grouprow.")) {
                out.add(p.getValue());
            }
        }
        return out;
    }

    private static List<int[]> rows(Drawing d, Device... devices) {
        final List<int[]> out = new ArrayList<>();
        for (final Device dev : devices) {
            out.add(d.part("row." + dev.pos().asLong()));
        }
        return out;
    }

    private static List<int[]> cells(Drawing d, String head, Device... devices) {
        final int[] h = d.part("head." + head);
        final List<int[]> out = new ArrayList<>();
        for (final int[] r : rows(d, devices)) {
            out.add(new int[]{h[0], r[1], h[2], r[3]});
        }
        return out;
    }

    private static List<int[]> chips(Drawing d, Device... devices) {
        final int chipH = com.createstats.gui.pdu.PduChip.HEIGHT;
        final List<int[]> out = new ArrayList<>();
        for (final int[] c : cells(d, "tier", devices)) {
            out.add(new int[]{c[0], c[1] + (c[3] - chipH) / 2, c[2], chipH});
        }
        return out;
    }

    private static List<int[]> pips(Drawing d, Device... devices) {
        final int[] name = d.part("head.name");
        final int pipX = name[0] - com.createstats.gui.pdu.PduLoadColumns.PIP_W;
        final List<int[]> out = new ArrayList<>();
        for (final int[] r : rows(d, devices)) {
            out.add(new int[]{pipX, r[1] + (r[3] - 6) / 2, 6, 6});
        }
        return out;
    }

    private static List<int[]> locks(Drawing d, Device... devices) {
        final int[] head = d.part("head.lock");
        final List<int[]> out = new ArrayList<>();
        for (final int[] r : rows(d, devices)) {
            out.add(new int[]{head[0], r[1] + (r[3] - 8) / 2, 8, 8});
        }
        return out;
    }

    private static List<int[]> words(Drawing d, String head, Device... devices) {
        final int[] h = d.part("head." + head);
        final List<int[]> out = new ArrayList<>();
        for (final int[] r : rows(d, devices)) {
            for (final Text t : d.text()) {
                if (t.y() > r[1] && t.y() < r[1] + r[3] && t.x() >= h[0] && t.x() < h[0] + h[2]) {
                    out.add(rect(t));
                    break;
                }
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("the Power Distribution Unit's table drew no word under " + head);
        }
        return out;
    }

    private static List<int[]> nodes(Drawing d, Device... devices) {
        final List<int[]> out = new ArrayList<>();
        for (final Device dev : devices) {
            out.add(drawn(d.part("node." + dev.pos().asLong())));
        }
        return out;
    }

    private static int[] drawn(int[] hit) {
        return new int[]{hit[0] + 1, hit[1] + 1, hit[2] - 2, hit[3] - 2};
    }

    private static com.createstats.book.layout.Callouts.Choice switchPin(int[] hit) {
        final int cx = hit[0] + 1 + 7;
        final int cy = hit[1] + 1 + 6;
        final int[] bar = {cx - 6, cy, 12, 2};
        final List<SpecimenAnatomy.Hot> places = new ArrayList<>(framed("switch", List.of(bar)).places());
        final int foot = hit[1] + hit[3] - 1;
        for (final int x : new int[]{cx, cx - 6, cx + 5, cx - 3, cx + 3}) {
            places.add(new SpecimenAnatomy.Hot("switch", bar[0], bar[1], bar[2], foot - bar[1] + 1, x, foot, true));
        }
        return new com.createstats.book.layout.Callouts.Choice("switch", places);
    }

    private static com.createstats.book.layout.Callouts.Choice overUnder(String id, List<int[]> copies) {
        final List<SpecimenAnatomy.Hot> places = new ArrayList<>();
        for (final int[] r : copies) {
            for (final int x : new int[]{r[0] + r[2] / 2, r[0] + 3}) {
                places.add(new SpecimenAnatomy.Hot(id, r[0], r[1], r[2], r[3], x, r[1] - 3, true));
                places.add(new SpecimenAnatomy.Hot(id, r[0], r[1], r[2], r[3], x, r[1] + r[3] + 2, true));
            }
        }
        return new com.createstats.book.layout.Callouts.Choice(id, places);
    }

    private static com.createstats.book.layout.Callouts.Choice within(String id, int[] area) {
        final List<SpecimenAnatomy.Hot> places = new ArrayList<>();
        for (final double fx : new double[]{0.9, 0.8, 0.95, 0.7}) {
            for (final double fy : new double[]{0.5, 0.75, 0.25}) {
                places.add(new SpecimenAnatomy.Hot(id, area[0], area[1], area[2], area[3],
                        area[0] + (int) Math.round((area[2] - 1) * fx), area[1] + (int) Math.round((area[3] - 1) * fy),
                        true));
            }
        }
        return new com.createstats.book.layout.Callouts.Choice(id, places);
    }

    private static com.createstats.book.layout.Callouts.Choice along(String id, int[] line) {
        final List<SpecimenAnatomy.Hot> places = new ArrayList<>();
        for (final double fx : new double[]{0.8, 0.9, 0.7, 0.6}) {
            places.add(new SpecimenAnatomy.Hot(id, line[0], line[1], line[2], line[3],
                    line[0] + (int) Math.round((line[2] - 1) * fx), line[1], true));
        }
        return new com.createstats.book.layout.Callouts.Choice(id, places);
    }

    private static List<int[]> partsStarting(Drawing d, String prefix) {
        final List<int[]> out = new ArrayList<>();
        for (final Map.Entry<String, int[]> p : d.parts().entrySet()) {
            if (p.getKey().startsWith(prefix)) {
                out.add(p.getValue());
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("the Power Distribution Unit's screen drew no part " + prefix + "*");
        }
        return out;
    }

    private static List<int[]> texts(Drawing d, String s) {
        final List<int[]> out = new ArrayList<>();
        for (final Text t : d.text()) {
            if (t.s().equals(s)) {
                out.add(rect(t));
            }
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("the Power Distribution Unit's screen drew no \"" + s + "\"");
        }
        return out;
    }

    private static int[] heading(Drawing d, String word) {
        final List<Text> text = d.text();
        for (int i = 0; i + word.length() <= text.size(); i++) {
            boolean spells = true;
            for (int j = 0; j < word.length() && spells; j++) {
                final Text t = text.get(i + j);
                spells = t.s().length() == 1 && t.s().charAt(0) == word.charAt(j) && t.y() == text.get(i).y()
                        && (j == 0 || t.x() >= text.get(i + j - 1).x() + text.get(i + j - 1).w()
                        && t.x() <= text.get(i + j - 1).x() + text.get(i + j - 1).w() + 2);
            }
            if (spells) {
                final Text first = text.get(i);
                final Text last = text.get(i + word.length() - 1);
                return new int[]{first.x(), first.y(), last.x() + last.w() - first.x(), first.h()};
            }
        }
        throw new IllegalStateException("the Power Distribution Unit's screen drew no heading " + word);
    }

    public static List<BlockPos> scenePositions() {
        final List<BlockPos> out = new ArrayList<>();
        for (final PduTopology.BranchNode n : topology().nodes()) {
            out.add(n.pos());
        }
        if (!out.contains(BOARD)) {
            out.add(BOARD);
        }
        return out;
    }

    public static com.createstats.book.scene.BlockView sceneView(int[] viewport) {
        final com.createstats.book.scene.BlockView v = flowScene(viewport).view();
        return new com.createstats.book.scene.BlockView(v.yaw(), v.pitch(), v.scale(), v.offsetX() + viewport[0],
                v.offsetY() + viewport[1]);
    }

    public static final String FLOW_SCENE = "pdu.flow.scene";

    private static final double[] CELL = {0, 0, 0, 16, 16, 16};

    private static final double[] FLOOR_RUN = {0, 0, 0, 16, 4, 16};

    private static Map<String, String> standing(BlockPos p) {
        final Map<Long, Map<String, String>> by = new java.util.HashMap<>();
        by.put(SECURITY.pos().asLong(), Map.of("facing", "south"));
        by.put(REPELLER.pos().asLong(), Map.of("facing", "south"));
        by.put(DISPLAY.pos().asLong(), Map.of("facing", "south", "lit", "true"));
        by.put(HOME.pos().asLong(), Map.of("facing", "east"));
        by.put(SMELTER_A.pos().asLong(), Map.of("facing", "west"));
        by.put(SMELTER_B.pos().asLong(), Map.of("facing", "west"));
        by.put(DRIVE.pos().asLong(), Map.of("facing", "east"));
        by.put(ARC_FURNACE.pos().asLong(), Map.of("facing", "north"));
        by.put(LAMP_A.pos().asLong(), Map.of("facing", "down", "lit", "true"));
        by.put(LAMP_B.pos().asLong(), Map.of("facing", "up", "lit", "true"));
        by.put(LAMP_C.pos().asLong(), Map.of("facing", "down", "lit", "false"));
        by.put(STAGE.pos().asLong(), Map.of("facing", "up", "lit", "true"));
        by.put(SWITCH.asLong(), Map.of("facing", "up", "powered", "true"));
        for (int i = 0; i < STUD_CONNECTOR.length; i++) {
            by.put(STUD_CONNECTOR[i].asLong(), Map.of("facing", i < 3 ? "north" : "up", "anchor", "center"));
        }
        return by.getOrDefault(p.asLong(), Map.of());
    }

    private static String blockAt(BlockPos p) {
        if (p.equals(BOARD)) {
            return "create_stats:power_distribution_unit";
        }
        if (p.equals(SWITCH)) {
            return "create_stats:rocker_switch";
        }
        for (final BlockPos c : STUD_CONNECTOR) {
            if (c.equals(p)) {
                return "create_stats:power_connector";
            }
        }
        for (final BlockPos c : SURFACE) {
            if (c.equals(p)) {
                return "create_stats:surface_cable";
            }
        }
        for (final BlockPos c : TRAY) {
            if (c.equals(p)) {
                return "create_stats:cable_tray";
            }
        }
        for (final Device d : DEVICES) {
            if (d.pos().equals(p)) {
                return "create_stats:" + d.block();
            }
        }
        throw new IllegalStateException("the example has no block at " + p);
    }

    public static boolean isGlass(com.createstats.book.scene.BlockFigure.Placement p) {
        return p.block().startsWith("minecraft:") && p.block().endsWith("_stained_glass");
    }

    private static com.createstats.book.scene.BlockFigure flowScene;

    public static synchronized com.createstats.book.scene.BlockFigure flowScene() {
        if (flowScene == null) {
            flowScene = flowScene(drawing(FLOW).part("flow.viewport"));
        }
        return flowScene;
    }

    public static com.createstats.book.scene.BlockFigure flowScene(int[] viewport) {
        final List<BlockPos> positions = scenePositions();
        final List<com.createstats.book.scene.BlockFigure.Placement> placed = new ArrayList<>();
        for (final BlockPos p : positions) {
            final boolean dark = DEVICES.stream().anyMatch(d -> d.pos().equals(p) && !runs(d));
            if (p.equals(BOARD) || dark) {
                placed.add(new com.createstats.book.scene.BlockFigure.Placement(
                        p.equals(BOARD) ? "minecraft:light_blue_stained_glass" : "minecraft:red_stained_glass",
                        Map.of(), p.getX() - BOARD.getX(), p.getY() - BOARD.getY(), p.getZ() - BOARD.getZ(), CELL,
                        false));
            }
        }
        for (final BlockPos p : positions) {
            final String block = blockAt(p);
            final int x = p.getX() - BOARD.getX();
            final int y = p.getY() - BOARD.getY();
            final int z = p.getZ() - BOARD.getZ();
            if (block.endsWith("_cable") || block.endsWith("cable_tray")) {
                placed.add(new com.createstats.book.scene.BlockFigure.Placement(block, Map.of("down", "true"), x, y, z,
                        FLOOR_RUN, true, CarryFixtures.CABLE));
            } else if (p.equals(BOARD)) {
                placed.add(new com.createstats.book.scene.BlockFigure.Placement(block, Map.of("facing", "south"), x, y,
                        z, CELL, true, FACE));
            } else {
                placed.add(new com.createstats.book.scene.BlockFigure.Placement(block, standing(p), x, y, z, CELL,
                        false));
            }
        }
        final com.createstats.gui.pdu.PduSceneFrame.Frame f = com.createstats.gui.pdu.PduSceneFrame.of(positions);
        final double[] centre = {(f.cx() - BOARD.getX()) * 16.0, (f.cy() - BOARD.getY()) * 16.0,
                (f.cz() - BOARD.getZ()) * 16.0};

        return new com.createstats.book.scene.BlockFigure(FLOW_SCENE, viewport[2], viewport[3], 0,
                com.createstats.gui.pdu.PduSceneFrame.START_YAW, com.createstats.gui.pdu.PduSceneFrame.START_PITCH,
                placed, List.of(), List.of(), com.createstats.book.scene.BlockFigure.Framing.camera(centre,
                f.scale(viewport[2], viewport[3], 1.0f), viewport[2] / 2.0, viewport[3] / 2.0));
    }

    public record Text(String s, int x, int y, int w, int h) {
    }

    public record Drawing(Map<String, int[]> parts, List<Text> text, List<SpecimenAnatomy.Hot> pins) {
        public Drawing {
            parts = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(parts));
            text = List.copyOf(text);
            pins = List.copyOf(pins);
        }

        public Drawing(Map<String, int[]> parts, List<Text> text) {
            this(parts, text, List.of());
        }

        public Drawing withPins(List<SpecimenAnatomy.Hot> placed) {
            return new Drawing(parts, text, placed);
        }

        public int[] part(String name) {
            final int[] r = parts.get(name);
            if (r == null) {
                throw new IllegalStateException("the Power Distribution Unit's screen drew no part " + name
                        + ": measure the plates again (PduPlatesTest)");
            }
            return r;
        }

        public Text text(String start) {
            for (final Text t : text) {
                if (t.s().startsWith(start)) {
                    return t;
                }
            }
            return null;
        }

        public Text containing(String part) {
            for (final Text t : text) {
                if (t.s().contains(part)) {
                    return t;
                }
            }
            throw new IllegalStateException("the Power Distribution Unit's screen drew no string holding \"" + part
                    + "\": measure the plates again (PduPlatesTest)");
        }
    }

    private static Map<String, Drawing> recorded;

    public static synchronized Map<String, Drawing> recorded() {
        if (recorded == null) {
            try (java.io.InputStream in = PduPlates.class.getResourceAsStream(RECORD)) {
                if (in == null) {
                    throw new IllegalStateException(RECORD + " is missing: measure the plates (PduPlatesTest)");
                }
                recorded = parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                throw new IllegalStateException("could not read " + RECORD, e);
            }
        }
        return recorded;
    }

    public static Map<String, Drawing> parse(String json) {
        final com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        final Map<String, Drawing> out = new LinkedHashMap<>();
        final com.google.gson.JsonObject plates = root.getAsJsonObject("plates");
        for (final String id : plates.keySet()) {
            final com.google.gson.JsonObject p = plates.getAsJsonObject(id);
            final Map<String, int[]> parts = new LinkedHashMap<>();
            final com.google.gson.JsonObject jp = p.getAsJsonObject("parts");
            for (final String name : jp.keySet()) {
                final com.google.gson.JsonArray a = jp.getAsJsonArray(name);
                parts.put(name, new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(),
                        a.get(3).getAsInt()});
            }
            final List<Text> text = new ArrayList<>();
            for (final com.google.gson.JsonElement e : p.getAsJsonArray("text")) {
                final com.google.gson.JsonArray a = e.getAsJsonArray();
                text.add(new Text(a.get(4).getAsString(), a.get(0).getAsInt(), a.get(1).getAsInt(),
                        a.get(2).getAsInt(), a.get(3).getAsInt()));
            }
            final List<SpecimenAnatomy.Hot> pins = new ArrayList<>();
            if (p.has("pins")) {
                for (final com.google.gson.JsonElement e : p.getAsJsonArray("pins")) {
                    final com.google.gson.JsonArray a = e.getAsJsonArray();
                    pins.add(new SpecimenAnatomy.Hot(a.get(0).getAsString(), a.get(1).getAsInt(), a.get(2).getAsInt(),
                            a.get(3).getAsInt(), a.get(4).getAsInt(), a.get(5).getAsInt(), a.get(6).getAsInt(), true));
                }
            }
            out.put(id, new Drawing(parts, text, pins));
        }
        return out;
    }

    public static String json(Map<String, Drawing> plates) {
        final StringBuilder b = new StringBuilder();
        b.append("{\n  \"note\": \"The Power Distribution Unit's screen as it drew the Student's Manual's example: every part")
                .append(" by name and every string, in panel pixels. Written by PduPlatesTest from the real screen; do not")
                .append(" edit by hand.\",\n");
        b.append("  \"w\": ").append(W).append(",\n  \"h\": ").append(H).append(",\n  \"plates\": {\n");
        int i = 0;
        for (final Map.Entry<String, Drawing> e : plates.entrySet()) {
            b.append("    ").append(quote(e.getKey())).append(": {\n      \"parts\": {\n");
            int j = 0;
            for (final Map.Entry<String, int[]> p : e.getValue().parts().entrySet()) {
                final int[] r = p.getValue();
                b.append("        ").append(quote(p.getKey())).append(": [").append(r[0]).append(", ").append(r[1])
                        .append(", ").append(r[2]).append(", ").append(r[3]).append("]")
                        .append(++j < e.getValue().parts().size() ? ",\n" : "\n");
            }
            b.append("      },\n      \"text\": [\n");
            int k = 0;
            for (final Text t : e.getValue().text()) {
                b.append("        [").append(t.x()).append(", ").append(t.y()).append(", ").append(t.w()).append(", ")
                        .append(t.h()).append(", ").append(quote(t.s())).append("]")
                        .append(++k < e.getValue().text().size() ? ",\n" : "\n");
            }
            b.append("      ],\n      \"pins\": [\n");
            int m = 0;
            for (final SpecimenAnatomy.Hot h : e.getValue().pins()) {
                b.append("        [").append(quote(h.id())).append(", ").append(h.x()).append(", ").append(h.y())
                        .append(", ").append(h.w()).append(", ").append(h.h()).append(", ").append(h.anchorX())
                        .append(", ").append(h.anchorY()).append("]")
                        .append(++m < e.getValue().pins().size() ? ",\n" : "\n");
            }
            b.append("      ]\n    }").append(++i < plates.size() ? ",\n" : "\n");
        }
        b.append("  }\n}\n");
        return b.toString();
    }

    private static String quote(String s) {
        return new com.google.gson.Gson().toJson(s);
    }
}
