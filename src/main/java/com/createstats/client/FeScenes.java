package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.ModConfiguration;
import com.createstats.client.ponder.Labels;
import com.createstats.client.ponder.fe.FeBanks;
import com.createstats.client.ponder.fe.FeDash;
import com.createstats.client.ponder.fe.FeFaces;
import com.createstats.client.ponder.fe.FeFlow;
import com.createstats.client.ponder.fe.FeGauge;
import com.createstats.client.ponder.fe.FeGoggles;
import com.createstats.client.ponder.fe.FeLabels;
import com.createstats.client.ponder.fe.FeLayout;
import com.createstats.client.ponder.fe.FePaths;
import com.createstats.client.ponder.fe.FeProps;
import com.createstats.client.ponder.fe.FeStage;
import com.createstats.client.ponder.fe.FeWires;
import com.createstats.gui.pdu.PduGlyphs;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.power.CableTier;
import com.createstats.power.FeGoggle;
import com.createstats.power.FePriority;
import com.createstats.power.FeRates;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;
import com.createstats.power.ponder.FePonderChapters;
import com.createstats.power.ponder.FePonderChapters.Sheet;
import com.createstats.power.ponder.FePonderChapters.Warp;
import com.createstats.power.ponder.FePonderGrid;
import com.createstats.power.ponder.FePonderPlant;
import com.createstats.power.ponder.FePonderPlant.Recording;
import com.createstats.power.ponder.FePonderPlant.Tuning;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// nothing ticks in ponder, so replay through the real board policy at build
public final class FeScenes {
    private FeScenes() {
    }

    private static final class Clock {
        int now;

        void idle(CreateSceneBuilder scene, int ticks) {
            scene.idle(ticks);
            now += ticks;
        }

        void until(CreateSceneBuilder scene, int tick) {
            if (tick < now) {
                CreateStats.LOGGER.warn("[Ponder] FE chapter cue {} is behind the scene clock {}", tick, now);
                return;
            }
            if (tick > now) idle(scene, tick - now);
        }
    }

    private static CreateSceneBuilder stage(SceneBuilder builder, String id, String title, int plate) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, plate);
        scene.scaleSceneView(plate == FeStage.WORKSHOP_PLATE ? FeStage.WORKSHOP_SCALE : FeStage.BENCH_SCALE);
        scene.setSceneOffsetY(FeStage.OFFSET_Y);
        return scene;
    }

    private static Selection plate(SceneBuildingUtil util, int size) {
        return util.select().fromTo(0, 0, 0, size - 1, 0, size - 1);
    }

    private static Selection at(SceneBuildingUtil util, BlockPos... points) {
        Selection s = util.select().position(points[0]);
        for (int i = 1; i < points.length; i++) s = s.add(util.select().position(points[i]));
        return s;
    }

    private static Vec3 centre(BlockPos p) {
        return new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    private static Vec3 top(BlockPos p, double up) {
        return new Vec3(p.getX() + 0.5, p.getY() + up, p.getZ() + 0.5);
    }

    static Tuning tuning() {
        try {
            final ModConfiguration.Common c = ModConfiguration.COMMON;
            return new Tuning(c.pduBufferCapacity.get(), c.pduReserveFloorPct.get(), c.pduRestoreSocPct.get(),
                    c.pduBurstMarginPct.get(), c.pduDwellTicks.get(), c.pduRestoreIntervalTicks.get(),
                    c.pduRideHorizonTicks.get(), c.pduSupplyReadIntervalTicks.get(), c.pduIdleDrain.get() / 20,
                    c.pduObservedDecayPct.get() / 100.0f, c.dynamoFePerRpm.get(), c.dynamoBufferCapacity.get(),
                    c.dynamoTransferRate.get());
        } catch (RuntimeException e) {
            return Tuning.defaults();
        }
    }

    private static String fe(int fePerTick) {
        return FeRates.perTick(fePerTick);
    }

    private static int tierColour(int tier) {
        return PduGlyphs.tierColour(FePriority.byOrdinal(tier)) & 0xFFFFFF;
    }

    private static List<FeFlow.Stream> trunk(Recording rec, Warp warp) {
        final List<FeFlow.Stream> out = new ArrayList<>();
        for (int d = 0; d < FeStage.DYNAMOS.length; d++) {
            final int dyn = d;
            out.add(new FeFlow.Stream(FeLayout.trunk(d), t -> FeLayout.trunkRate(rec, dyn, rec.at(warp.tick(t)))));
        }
        return out;
    }

    private static List<FeFlow.Stream> legs(net.minecraft.world.level.BlockGetter level, Recording rec, Warp warp) {
        final List<FeFlow.Stream> out = new ArrayList<>();
        for (int i = 0; i < FeStage.MACHINE.length; i++) {
            final int m = i;
            final FePaths.Path path = FePaths.leg(level, FeStage.PDU, Direction.NORTH, FeStage.STUD[i], FeStage.LEG_END[i]);
            out.add(FeFlow.Stream.wire(path, t -> rec.fed[m][rec.at(warp.tick(t))], WireTier.COPPER, t -> true));
        }
        return out;
    }

    private static Vec3 chipPoint(int i) {
        return FeStage.chipPoint(i);
    }

    private static Vec3[] chipPoints() {
        final Vec3[] out = new Vec3[FeStage.MACHINE.length];
        for (int i = 0; i < out.length; i++) out[i] = chipPoint(i);
        return out;
    }

    private static void tierChips(CreateSceneBuilder scene, Recording rec, Warp warp, int from, int to,
                                  java.util.function.IntPredicate tiered) {
        for (int i = 0; i < FeStage.MACHINE.length; i++) {
            final int m = i;
            final Vec3 at = chipPoint(i);
            scene.addInstruction(Labels.chip(pt -> at, t -> FePriority.byOrdinal(tiered.test(t)
                            ? rec.tier[m][rec.at(warp.tick(t))] : FePriority.DEFAULT.ordinal()).getShortName(),
                    j -> tierColour(tierOf(j)), from + 3 * i, to - from - 3 * i));
        }
    }

    private static int tierOf(String shortName) {
        for (FePriority p : FePriority.values()) if (p.getShortName().equals(shortName)) return p.ordinal();
        return FePriority.DEFAULT.ordinal();
    }

    private static void reasons(CreateSceneBuilder scene, Recording rec, Warp warp, int from, int to) {
        for (int i = 0; i < FeStage.MACHINE.length; i++) {
            final int m = i;
            final Vec3 at = chipPoint(i).add(0.0, -0.05, 0.0);
            final int[] hang = FeLayout.REASON[i];
            final java.util.function.IntFunction<String> words = t -> FeLabels.reason(rec, m, rec.at(warp.tick(t)));
            scene.addInstruction(hang[2] == 1
                    ? FeLabels.flagLeft(pt -> at, words, t -> PduTheme.WARN & 0xFFFFFF, hang[0], hang[1], from, to - from)
                    : FeLabels.flag(pt -> at, words, t -> PduTheme.WARN & 0xFFFFFF, hang[0], hang[1], from, to - from));
        }
    }

    public static void cables(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "fe_cables", "Laying cable", FeStage.BENCH_PLATE);
        final Clock c = new Clock();
        final Tuning tune = tuning();
        final Sheet sheet = FePonderChapters.Cables.sheet();
        final Map<String, Vec3> aim = FeLayout.cables();
        final int lay = FePonderChapters.Cables.LAY;
        final int spin = sheet.tick("power");
        final int gauge = sheet.tick("gauge");
        final int ladder = sheet.tick("ladder");
        final int goggles = sheet.tick("goggles");
        final int ripped = FePonderChapters.Cables.RIPPED;
        final int end = sheet.length();
        final int[] flow = FePonderChapters.Cables.flow(tune, end);
        final int rating = FeRates.cable(CableTier.SURFACE);

        final java.util.function.IntUnaryOperator carried = t -> t <= spin || t >= ripped ? 0
                : flow[Math.min(flow.length - 1, t - spin - 1)];

        scene.world().showSection(plate(util, FeStage.BENCH_PLATE), Direction.UP);
        c.idle(scene, 10);
        build(scene, util, c, FeLayout.benchBuild());

        c.until(scene, lay);
        final Vec3 anchorFace = FeLayout.benchAnchor();
        scene.overlay().showControls(anchorFace, Pointing.DOWN, 30).rightClick()
                .withItem(ModBlocks.SURFACE_CABLE.asStack());
        c.until(scene, sheet.tick("anchor"));
        scene.world().showSection(at(util, FeStage.BENCH_RUN[0]), Direction.DOWN);
        c.until(scene, sheet.tick("lay_caption"));
        scene.overlay().showText(sheet.get("lay_caption").hold())
                .text("Click a face to start a run, then click the machine: the route is laid at once")
                .pointAt(aim.get("lay_caption")).colored(PonderPalette.INPUT).attachKeyFrame();
        final Vec3 machineFace = FeLayout.benchMachine();
        c.until(scene, sheet.tick("route") - 8);
        scene.overlay().showControls(machineFace, Pointing.DOWN, 30).rightClick()
                .withItem(ModBlocks.SURFACE_CABLE.asStack());
        c.until(scene, sheet.tick("route"));
        build(scene, util, c, FeLayout.benchRoute());

        c.until(scene, spin);
        final int power = c.now;
        scene.addInstruction(FeProps.spin(List.of(
                new FeProps.Spin(FeStage.BENCH_MOTOR, t -> t >= power ? 256 : 0),
                new FeProps.Spin(FeStage.BENCH_DYNAMO, t -> t >= power ? 256 : 0),
                new FeProps.Spin(FeStage.BENCH_FAN_MOTOR, t -> t > power ? 128 : 0),
                new FeProps.Spin(FeStage.BENCH_FAN, t -> t > power ? 128 : 0)), power, end - power));
        final FePaths.Path run = FeLayout.benchRun();
        scene.addInstruction(FeFlow.beads("fe_bench_flow", List.of(new FeFlow.Stream(run, carried::applyAsInt)),
                FeFlow.GREEN, power, ripped - power));
        c.until(scene, sheet.tick("flow_caption"));
        scene.overlay().showText(sheet.get("flow_caption").hold()).text("The cable carries the dynamo's power to the machine")
                .pointAt(aim.get("flow_caption")).colored(PonderPalette.GREEN).attachKeyFrame();

        c.until(scene, gauge);
        final Vec3 g0 = FeStage.GAUGE_FROM;
        final int rip = FePonderChapters.Cables.RIP;
        scene.addInstruction(FeGauge.bar("fe_bench_gauge", g0, FeStage.GAUGE_TO, new Vec3(0, 0, 1),
                t -> carried.applyAsInt(t) / (double) rating, gauge, rip - gauge));
        scene.addInstruction(Labels.tag(pt -> g0.add(0.1, 0.0, 0.0),
                t -> String.format(Locale.ROOT, "%,d / %s", carried.applyAsInt(t), fe(rating)), t -> FeFlow.GREEN,
                6, -5, gauge + 6, rip - gauge - 6));
        c.until(scene, sheet.tick("gauge_caption"));
        scene.overlay().showText(sheet.get("gauge_caption").hold())
                .text("Surface Cable carries up to %s: one dynamo is less than a tenth of it", fe(rating))
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);

        c.until(scene, ladder);
        final CableTier[] grades = CableTier.values();
        for (int i = 0; i < grades.length; i++) {
            scene.world().showSection(at(util, FeLayout.ladderRow(i).toArray(new BlockPos[0])), Direction.UP);
            final Vec3 tagAt = FeLayout.ladderTag(i);
            final String figure = fe(FeRates.cable(grades[i]));
            scene.addInstruction(Labels.tag(pt -> tagAt, t -> figure, t -> 0xE4E9EF, 4, -5, c.now + 4, goggles - c.now - 4));
            c.idle(scene, FeLayout.LADDER_STEP);
        }
        c.until(scene, sheet.tick("ladder_caption"));
        scene.overlay().showText(sheet.get("ladder_caption").hold()).text("Four grades, each carrying twice the one before")
                .pointAt(aim.get("ladder_caption")).colored(PonderPalette.BLUE).attachKeyFrame();

        c.until(scene, sheet.tick("lamp"));
        scene.world().showSection(at(util, FeStage.BENCH_LAMP_BASE), Direction.DOWN);
        c.idle(scene, FeLayout.LAMP_STEP);
        scene.world().showSection(at(util, FeStage.BENCH_LAMP), Direction.DOWN);
        c.idle(scene, FeLayout.LAMP_STEP);
        scene.addInstruction(FeProps.setState(FeStage.BENCH_LAMP,
                st -> st.hasProperty(com.createstats.block.LampBlock.LIT)
                        ? st.setValue(com.createstats.block.LampBlock.LIT, true) : st, c.now));
        scene.addInstruction(FeFlow.beads("fe_bench_lamp", List.of(new FeFlow.Stream(FeLayout.benchLampRun(), t -> 1.0)),
                FeFlow.GREEN, c.now, ripped - c.now));

        final int dark = ripped + FePonderChapters.Cables.LAMP_COAST;
        if (dark < end) {
            scene.addInstruction(FeProps.setState(FeStage.BENCH_LAMP,
                    st -> st.hasProperty(com.createstats.block.LampBlock.LIT)
                            ? st.setValue(com.createstats.block.LampBlock.LIT, false) : st, dark));
        }
        c.until(scene, sheet.tick("lamp_caption"));
        scene.overlay().showText(sheet.get("lamp_caption").hold()).text("Most machines set against a run draw from it too")
                .pointAt(aim.get("lamp_caption")).colored(PonderPalette.GREEN).attachKeyFrame();

        c.until(scene, goggles);
        final Vec3 segment = FeLayout.benchGoggles();
        scene.addInstruction(FeGoggles.card(pt -> segment, t -> List.of(
                FeGoggle.title(CableTier.SURFACE.getDisplayName()),
                FeGoggle.carrying(carried.applyAsInt(t), rating),
                FeGoggle.capacity(rating)), c.now, FePonderChapters.Cables.GOGGLES_END - c.now));
        c.until(scene, sheet.tick("goggles_caption"));
        scene.overlay().showText(sheet.get("goggles_caption").hold())
                .text("Engineer's Goggles show what a run carries against its rating")
                .pointAt(aim.get("goggles_caption")).colored(PonderPalette.WHITE);

        c.until(scene, sheet.tick("dye"));
        final Vec3 middle = top(FeStage.BENCH_RUN[FeStage.BENCH_RUN.length / 2], 0.3);
        scene.overlay().showControls(middle, Pointing.DOWN, 30).rightClick()
                .withItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLUE_DYE));
        c.idle(scene, 6);
        scene.world().modifyBlocks(at(util, FeStage.BENCH_RUN), st -> st.hasProperty(com.createstats.block.CableBlock.COLOR)
                ? st.setValue(com.createstats.block.CableBlock.COLOR, net.minecraft.world.item.DyeColor.BLUE) : st, false);
        c.until(scene, sheet.tick("dye_caption"));
        scene.overlay().showText(sheet.get("dye_caption").hold())
                .text("Dye recolours the whole run: runs of different colours stay apart")
                .independent(FeLayout.UPPER).colored(PonderPalette.INPUT).attachKeyFrame();

        c.until(scene, rip);
        final Vec3 grip = top(FeStage.BENCH_RUN[1], 0.3);
        scene.overlay().showControls(grip, Pointing.DOWN, 30).rightClick().withItem(ModBlocks.SURFACE_CABLE.asStack())
                .whileSneaking();
        c.until(scene, ripped);
        scene.world().hideSection(at(util, FeStage.BENCH_RUN), Direction.UP);
        final net.minecraft.world.item.ItemStack taken = ModBlocks.SURFACE_CABLE.asStack();
        taken.setCount(FeStage.BENCH_RUN.length);
        scene.world().createItemEntity(grip, new Vec3(0.0, 0.2, -0.05), taken);
        c.until(scene, sheet.tick("rip_caption"));
        scene.overlay().showText(sheet.get("rip_caption").hold())
                .text("Sneak-click a run to take all of its cable back at once")
                .independent(FeLayout.UPPER).colored(PonderPalette.INPUT).attachKeyFrame();
        c.until(scene, end - 20);
        c.idle(scene, 20);
    }

    public static void pdu(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "fe_pdu", "The Power Distribution Unit", FeStage.WORKSHOP_PLATE);
        final Clock c = new Clock();
        Recording rec = FePonderChapters.Pdu.replay(tuning());
        if (FePonderChapters.Pdu.triage(rec) < 0) rec = FePonderChapters.Pdu.replay(Tuning.defaults());
        final Recording r = rec;
        final Warp warp = FePonderChapters.Pdu.warp(r);
        final Sheet sheet = FePonderChapters.Pdu.sheet(r, warp);
        final Map<String, Vec3> aim = FeLayout.pdu();
        final int end = sheet.length();
        final int place = sheet.tick("place");
        final int legsAt = sheet.tick("legs");
        final int tiersAt = sheet.tick("tiers");
        final int power = sheet.tick("power");
        final int lever = sheet.tick("lever");
        final int wireTicks = FePonderChapters.Pdu.WIRE_TICKS;
        final int wireGap = FePonderChapters.Pdu.WIRE_GAP;

        scene.addInstruction(FeWires.unstrung(FeStage.PDU, FeStage.LEG_END, 0));
        scene.world().showSection(plate(util, FeStage.WORKSHOP_PLATE), Direction.UP);
        c.idle(scene, 10);
        buildPlant(scene, util, c, false);

        c.until(scene, place);
        scene.world().showSection(at(util, FeStage.PDU), Direction.DOWN);
        scene.addInstruction(FeFaces.pdu(FeStage.PDU, r, warp, t -> strung(t, legsAt, wireTicks, wireGap),
                t -> t >= tiersAt + 20, place, end - place));
        c.until(scene, sheet.tick("place_caption"));
        scene.overlay().showText(sheet.get("place_caption").hold()).text("Feed the Power Distribution Unit through either side")
                .pointAt(aim.get("place_caption")).colored(PonderPalette.INPUT).placeNearTarget().attachKeyFrame();

        c.until(scene, legsAt);
        scene.overlay().showControls(top(FeStage.PDU, 1.3), Pointing.DOWN, 40).rightClick()
                .withItem(ModItems.COPPER_WIRE.asStack());
        for (int i = 0; i < FeStage.MACHINE.length; i++) {
            scene.addInstruction(FeWires.unspool("fe_leg_" + i, FeStage.PDU, Direction.NORTH, FeStage.STUD[i],
                    FeStage.LEG_END[i], WireTier.COPPER, legsAt + i * wireGap, wireTicks));
        }
        c.until(scene, sheet.tick("legs_caption"));
        scene.overlay().showText(sheet.get("legs_caption").hold())
                .text("Run a wire from a stud on top to each machine: each wire is a leg")
                .pointAt(aim.get("legs_caption")).colored(PonderPalette.INPUT);

        c.until(scene, tiersAt);
        scene.overlay().showControls(FePaths.faceCentre(FeStage.PDU, Direction.NORTH), Pointing.DOWN, 30).rightClick();
        tierChips(scene, r, warp, tiersAt, end, t -> t >= tiersAt + 20);
        c.until(scene, sheet.tick("tiers_caption"));
        scene.overlay().showText(sheet.get("tiers_caption").hold())
                .text("Open it and give each machine a tier, from Critical down to Optional")
                .pointAt(aim.get("tiers_caption")).colored(PonderPalette.BLUE).attachKeyFrame();

        c.until(scene, power - 10);
        scene.addInstruction(FeDash.show(new FeDash.Model(r, warp, false, chipPoints()), c.now, c.now, end - c.now));
        scene.addInstruction(FeProps.spin(FeProps.driveLine(r, warp), c.now, end - c.now));
        scene.addInstruction(FeProps.spin(FeProps.motors(r, warp), c.now, end - c.now));
        scene.addInstruction(FeProps.appliances(r, warp, c.now, end - c.now));
        scene.addInstruction(FeFlow.beads("fe_trunk", trunk(r, warp), FeFlow.GREEN, c.now, end - c.now));
        reasons(scene, r, warp, power, end);
        c.until(scene, power);
        c.idle(scene, 2);
        scene.addInstruction(FeFlow.beads("fe_legs", legs(scene.getScene().getWorld(), r, warp), FeFlow.GREEN, c.now,
                end - c.now));
        c.until(scene, sheet.tick("power_caption"));
        scene.overlay().showText(sheet.get("power_caption").hold())
                .text("It serves Critical first, and each tier below only from what is left")
                .independent(FeLayout.UPPER).colored(PonderPalette.GREEN).attachKeyFrame();

        c.until(scene, lever - 12);
        scene.overlay().showControls(top(FeStage.LEVER, 0.4), Pointing.DOWN, 24).rightClick();
        c.until(scene, lever);
        scene.addInstruction(FeProps.lever(true, lever));

        c.until(scene, sheet.tick("triage_caption"));
        scene.overlay().showText(sheet.get("triage_caption").hold())
                .text("In Manual it spends its whole buffer first, then cuts the tiers it cannot feed")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE).attachKeyFrame();
        c.until(scene, sheet.tick("payoff"));
        scene.addInstruction(com.createstats.client.ponder.Machines.sparkle(t -> centre(FeStage.FAN_MOTOR), c.now));
        c.until(scene, sheet.tick("payoff_caption"));
        scene.overlay().showText(sheet.get("payoff_caption").hold()).text("Critical keeps running on what still comes in")
                .independent(FeLayout.UPPER).colored(PonderPalette.GREEN);

        c.until(scene, sheet.tick("screen"));
        scene.overlay().showControls(FePaths.faceCentre(FeStage.PDU, Direction.NORTH), Pointing.DOWN, 30).rightClick();
        c.until(scene, sheet.tick("screen_caption"));
        scene.overlay().showText(sheet.get("screen_caption").hold())
                .text("Its screen also switches the mode, and has a breaker for every leg")
                .independent(FeLayout.UPPER).colored(PonderPalette.INPUT).attachKeyFrame();
        c.until(scene, end - 20);
        c.idle(scene, 20);
    }

    public static void wires(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "fe_wires", "Hanging wire and connectors", FeStage.SPANS_PLATE);
        final Clock c = new Clock();
        final Tuning tune = tuning();
        final FePonderGrid.Recording r = FePonderChapters.Spans.replay(tune);
        final Sheet sheet = FePonderChapters.Spans.sheet();
        final Map<String, Vec3> aim = FeLayout.wires();
        final int end = sheet.length();
        final net.minecraft.world.level.BlockGetter level = scene.getScene().getWorld();
        final BlockPos[] a = FeStage.SPANS_A;
        final BlockPos b = FeStage.SPANS_B;
        final int range = FePonderChapters.Spans.RANGE;
        final int lamp = FePonderChapters.Spans.LAMP;
        final net.minecraft.world.item.ItemStack wire = ModItems.COPPER_WIRE.asStack();

        final BlockPos[] rackEnds = new BlockPos[FeStage.RACK_EAST.length * 2];
        for (int i = 0; i < FeStage.RACK_EAST.length; i++) {
            rackEnds[2 * i] = FeStage.RACK_EAST[i];
            rackEnds[2 * i + 1] = FeStage.RACK_WEST[i];
        }
        scene.addInstruction(FeWires.cleared(rackEnds, 0));
        scene.world().showSection(plate(util, FeStage.SPANS_PLATE), Direction.UP);
        c.idle(scene, 10);
        build(scene, util, c, FeLayout.spansBuild());

        c.until(scene, FePonderChapters.Spans.STRING);
        scene.overlay().showControls(top(a[0], 0.8), Pointing.DOWN, 30).rightClick().withItem(wire);
        c.until(scene, sheet.tick("string_caption"));
        scene.overlay().showText(sheet.get("string_caption").hold())
                .text("Right-click a connector with wire, then another: a span hangs between them")
                .pointAt(aim.get("string_caption")).colored(PonderPalette.INPUT).attachKeyFrame();
        c.until(scene, FePonderChapters.Spans.CLICK_B);
        scene.overlay().showControls(top(b, 0.8), Pointing.DOWN, 30).rightClick().withItem(wire);
        c.idle(scene, 4);
        scene.addInstruction(FeWires.span("fe_span_0", a[0], b, WireTier.COPPER, WireTension.STANDARD, c.now,
                FePonderChapters.Spans.SPAN_TICKS));

        c.until(scene, FePonderChapters.Spans.POWER);
        final int power = c.now;
        scene.addInstruction(FeProps.spin(List.of(new FeProps.Spin(FeStage.SPANS_MOTOR, t -> t >= power ? 256 : 0),
                new FeProps.Spin(FeStage.SPANS_DYNAMO, t -> t >= power ? 256 : 0)), power, end - power));
        scene.addInstruction(FeProps.worked(List.of(
                new FeProps.Worked(FeStage.SPANS_RANGE, FePonderPlant.Kind.RANGE, t -> r.working[range][r.at(t)]),
                new FeProps.Worked(FeStage.SPANS_LAMP, FePonderPlant.Kind.LAMP, t -> r.working[lamp][r.at(t)])),
                power, end - power));
        scene.addInstruction(FeFlow.beads("fe_spans", FeLayout.spanStreams((from, to, tension) -> new FePaths.Path(
                FePaths.catenary(FePaths.attach(level, from), FePaths.attach(level, to), tension.slack())), r),
                FeFlow.GREEN, power, end - power));
        c.until(scene, sheet.tick("power_caption"));
        scene.overlay().showText(sheet.get("power_caption").hold())
                .text("Power rides the span to the block the far connector is bolted to")
                .pointAt(aim.get("power_caption")).colored(PonderPalette.GREEN).attachKeyFrame();

        c.until(scene, FePonderChapters.Spans.GOGGLES);
        final int base = FeRates.base();
        final Vec3 card = FeLayout.spansGoggles();
        scene.addInstruction(FeGoggles.card(pt -> card, t -> connectorLines(r, base, t), c.now,
                FePonderChapters.Spans.CARD_OFF - c.now));
        c.until(scene, sheet.tick("goggles_caption"));
        scene.overlay().showText(sheet.get("goggles_caption").hold())
                .text("A connector feeds its block what its wires add up to, up to its own rating")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE).attachKeyFrame();

        c.until(scene, FePonderChapters.Spans.SECOND);
        scene.world().showSection(at(util, a[1]), Direction.DOWN);
        c.until(scene, FePonderChapters.Spans.SECOND + 6);
        scene.overlay().showControls(top(a[1], 0.8), Pointing.DOWN, 16).rightClick().withItem(wire);
        c.until(scene, FePonderChapters.Spans.SECOND + 22);
        scene.overlay().showControls(top(b, 0.8), Pointing.DOWN, 16).rightClick().withItem(wire);
        c.until(scene, FePonderChapters.Spans.SECOND + 26);
        scene.addInstruction(FeWires.span("fe_span_1", a[1], b, WireTier.COPPER, WireTension.STANDARD, c.now,
                FePonderChapters.Spans.SPAN_TICKS));
        c.until(scene, sheet.tick("second_caption"));
        scene.overlay().showText(sheet.get("second_caption").hold())
                .text("A second span adds its rating: two Copper spans fill a Power Connector")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);

        c.until(scene, FePonderChapters.Spans.THIRD);
        scene.world().showSection(at(util, a[2]), Direction.DOWN);
        c.until(scene, FePonderChapters.Spans.THIRD + 6);
        scene.overlay().showControls(top(a[2], 0.8), Pointing.DOWN, 16).rightClick().withItem(wire);
        c.until(scene, FePonderChapters.Spans.THIRD + 22);
        scene.overlay().showControls(top(b, 0.8), Pointing.DOWN, 16).rightClick().withItem(wire);
        c.until(scene, FePonderChapters.Spans.THIRD + 26);
        scene.addInstruction(FeWires.span("fe_span_2", a[2], b, WireTier.COPPER, WireTension.STANDARD, c.now,
                FePonderChapters.Spans.SPAN_TICKS));
        c.until(scene, sheet.tick("third_caption"));
        scene.overlay().showText(sheet.get("third_caption").hold())
                .text("A third adds nothing: no connector feeds its block more than its own grade")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);

        c.until(scene, FePonderChapters.Spans.GRADES);
        final int grades = c.now;
        build(scene, util, c, FeLayout.rack());
        final WireTier[] wires = WireTier.values();
        final net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        for (int i = 0; i < FeStage.RACK_EAST.length; i++) {
            final int strung = grades + 15 + 10 * i;
            scene.addInstruction(FeWires.span("fe_rack_" + i, FeStage.RACK_EAST[i], FeStage.RACK_WEST[i], wires[i],
                    WireTension.STANDARD, strung, FePonderChapters.Spans.SPAN_TICKS));

            final String wireWords = FeLayout.rackWireWords(i);
            final Vec3 wireAt = FeLayout.rackWireTag(i);
            scene.addInstruction(Labels.tag(pt -> wireAt, t -> wireWords, t -> 0xE4E9EF, -6 - font.width(wireWords), -5,
                    strung + FePonderChapters.Spans.SPAN_TICKS, FePonderChapters.Spans.PAIR - strung
                            - FePonderChapters.Spans.SPAN_TICKS));
            final String words = FeLayout.rackConnectorWords(i);
            if (words != null) {
                final Vec3 at = FeLayout.rackConnectorTag(i);
                scene.addInstruction(Labels.tag(pt -> at, t -> words, t -> 0xB8C4D0, 6, -5,
                        strung + FePonderChapters.Spans.SPAN_TICKS, FePonderChapters.Spans.PAIR - strung
                                - FePonderChapters.Spans.SPAN_TICKS));
            }
        }
        c.until(scene, sheet.tick("grades_caption"));
        scene.overlay().showText(sheet.get("grades_caption").hold())
                .text("Each grade doubles the last, and each connector matches one grade of wire")
                .pointAt(aim.get("grades_caption")).colored(PonderPalette.BLUE).attachKeyFrame();

        c.until(scene, FePonderChapters.Spans.PAIR);
        build(scene, util, c, FeLayout.pair());
        c.until(scene, sheet.tick("pair_caption"));
        scene.overlay().showText(sheet.get("pair_caption").hold())
                .text("Two connectors side by side share nothing until a wire joins them")
                .pointAt(aim.get("pair_caption")).colored(PonderPalette.WHITE);
        c.until(scene, FePonderChapters.Spans.PAIR + 12);
        scene.addInstruction(FeWires.span("fe_span_d", a[0], FeStage.SPANS_D, WireTier.COPPER, WireTension.STANDARD,
                c.now, FePonderChapters.Spans.SPAN_TICKS));
        c.until(scene, FePonderChapters.Spans.JOIN);
        scene.overlay().showControls(top(FeStage.SPANS_D, 0.8), Pointing.DOWN, 10).rightClick().withItem(wire);
        c.until(scene, FePonderChapters.Spans.JOIN + 10);
        scene.overlay().showControls(top(FeStage.SPANS_C, 0.8), Pointing.DOWN, 16).rightClick().withItem(wire);
        c.until(scene, FePonderChapters.Spans.JOIN + 14);
        scene.addInstruction(FeWires.span("fe_span_join", FeStage.SPANS_D, FeStage.SPANS_C, WireTier.COPPER,
                WireTension.STANDARD, c.now, FePonderChapters.Spans.JOIN_TICKS));

        c.until(scene, FePonderChapters.Spans.TENSION);
        scene.overlay().showControls(top(a[0], 0.8), Pointing.DOWN, 80).rightClick();
        scene.addInstruction(FeGoggles.card(pt -> card, t -> connectorLines(r, base, t), c.now,
                FePonderChapters.Spans.CARD_GONE - c.now));
        c.until(scene, sheet.tick("tension_caption"));
        scene.overlay().showText(sheet.get("tension_caption").hold())
                .text("Tension changes how a span hangs, never what it carries: hold use empty-handed to set it")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);
        final WireTension[] rehung = FePonderChapters.Spans.REHUNG;
        for (int i = 0; i < rehung.length; i++) {
            scene.addInstruction(FeWires.tension(a[0], b, rehung[i], FePonderChapters.Spans.REHANG
                    + FePonderChapters.Spans.REHANG_STEP * i));
        }

        c.until(scene, FePonderChapters.Spans.STRIP);
        scene.overlay().showControls(top(b, 0.8), Pointing.DOWN, 20).rightClick().withItem(wire).whileSneaking();
        c.until(scene, sheet.tick("strip_caption"));
        scene.overlay().showText(sheet.get("strip_caption").hold())
                .text("Sneak-right-click a connector with wire to strip it: the wires drop back")
                .independent(FeLayout.UPPER).colored(PonderPalette.INPUT).attachKeyFrame();
        c.until(scene, FePonderChapters.Spans.STRIPPED);
        scene.addInstruction(FeWires.strip(b, c.now));
        for (int i = 0; i < 3; i++) {
            scene.world().createItemEntity(top(b, 0.6), new Vec3(0.04 * (i - 1), 0.18, -0.05), wire.copy());
        }
        c.until(scene, end - 20);
        c.idle(scene, 20);
    }

    public static void bank(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "fe_capacitor_bank", "Storing power", FeStage.BANK_PLATE);
        final Clock c = new Clock();
        final FePonderChapters.Bank.Stock stock = bankStock();
        final FePonderChapters.Bank.Run run = FePonderChapters.Bank.replay(tuning(), stock);
        final FePonderGrid.Recording r = run.rec();
        final Warp warp = run.warp();
        final Sheet sheet = FePonderChapters.Bank.sheet(run);
        final int end = sheet.length();
        final FeBanks.Pool bank = new FeBanks.Pool(FeStage.BANK_BLOCKS, FePonderChapters.Bank.BANK);
        final FeBanks.Pool spare = new FeBanks.Pool(new BlockPos[] {FeStage.SPARE}, FePonderChapters.Bank.SPARE);
        final net.minecraft.world.item.ItemStack bankItem = ModBlocks.CAPACITOR_BANK.asStack();
        final int place = FePonderChapters.Bank.PLACE;
        final int power = FePonderChapters.Bank.POWER;
        final int stop = FePonderChapters.Bank.STOP;
        final int never = FePonderChapters.Bank.NEVER;
        final int broken = FePonderChapters.Bank.BREAK;

        scene.world().showSection(plate(util, FeStage.BANK_PLATE), Direction.UP);
        c.idle(scene, 10);
        build(scene, util, c, FeLayout.bankBuild());

        c.until(scene, place);
        scene.addInstruction(FeDash.banks(new FeDash.Banks(warp, FeLayout.bankRows(run, stock)), c.now, end - c.now));
        scene.addInstruction(FeBanks.charge(List.of(bank, spare), List.of(FePonderChapters.Bank::builtAt,
                t -> t < broken ? 1 : 0), r, warp, stock, c.now, end - c.now));
        bankClick(scene, util, c, bank, stock, bankItem, 0);
        c.until(scene, sheet.tick("place_caption"));
        scene.overlay().showText(sheet.get("place_caption").hold())
                .text("Banks placed together merge into one, and a merged bank holds more than its blocks")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE).attachKeyFrame();
        for (int k = 1; k < FePonderChapters.Bank.BUILT.length; k++) bankClick(scene, util, c, bank, stock, bankItem, k);

        c.until(scene, power - 12);
        scene.overlay().showControls(top(FeStage.BANK_MOTOR, 0.6), Pointing.DOWN, 12).scroll();
        c.until(scene, power);
        final java.util.function.IntToDoubleFunction rpm = t -> r.rpm[FePonderChapters.Bank.DYNAMO][r.at(warp.tick(t))];
        scene.addInstruction(FeProps.spin(List.of(new FeProps.Spin(FeStage.BANK_MOTOR, rpm),
                new FeProps.Spin(FeStage.BANK_DYNAMO, rpm)), c.now, end - c.now));
        scene.addInstruction(FeProps.worked(List.of(new FeProps.Worked(FeStage.BANK_RANGE, FePonderPlant.Kind.RANGE,
                t -> r.working[FePonderChapters.Bank.RANGE][r.at(warp.tick(t))])), c.now, end - c.now));
        scene.addInstruction(FeFlow.beads("fe_bank_flow", FeLayout.bankStreams(run), FeFlow.GREEN, c.now, end - c.now));
        c.until(scene, sheet.tick("power_caption"));
        scene.overlay().showText(sheet.get("power_caption").hold())
                .text("Machines are served first: only what is left over charges the bank")
                .independent(FeLayout.UPPER).colored(PonderPalette.GREEN).attachKeyFrame();

        c.until(scene, stop - 12);
        scene.overlay().showControls(top(FeStage.BANK_MOTOR, 0.6), Pointing.DOWN, 12).scroll();
        c.until(scene, sheet.tick("stop_caption"));
        scene.overlay().showText(sheet.get("stop_caption").hold())
                .text("Stop the generator and the bank covers exactly what the machines draw")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE).attachKeyFrame();

        c.until(scene, never - 12);
        final net.minecraft.world.item.ItemStack cable = ModBlocks.SURFACE_CABLE.asStack();
        scene.overlay().showControls(top(FeStage.SPARE_LINK_BASE, 1.1), Pointing.DOWN, 12).rightClick().withItem(cable);
        c.until(scene, never);
        scene.world().showSection(at(util, FeStage.SPARE_LINK), Direction.DOWN);
        scene.world().setBlock(FeStage.SPARE_LINK, ModBlocks.SURFACE_CABLE.get().defaultBlockState()
                .setValue(com.createstats.block.CableBlock.DOWN, true), false);
        c.until(scene, sheet.tick("never_caption"));
        scene.overlay().showText(sheet.get("never_caption").hold())
                .text("A bank never charges another: the full one runs the range, the empty one stays empty")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);

        c.until(scene, broken - 12);
        scene.overlay().showControls(top(FeStage.SPARE, 1.0), Pointing.DOWN, 12).leftClick();
        c.until(scene, broken);
        scene.world().destroyBlock(FeStage.SPARE);
        scene.world().createItemEntity(top(FeStage.SPARE, 0.5), new Vec3(0.0, 0.2, -0.04), bankItem.copy());
        c.until(scene, sheet.tick("break_caption"));
        scene.overlay().showText(sheet.get("break_caption").hold())
                .text("Break a bank and its charge is gone: it drops as an empty block")
                .independent(FeLayout.UPPER).colored(PonderPalette.RED).attachKeyFrame();

        c.until(scene, sheet.tick("merge"));
        scene.overlay().showControls(FeLayout.bankTop(), Pointing.DOWN, 30).rightClick().withItem(bankItem.copy());
        final int[] size = bankSize();
        c.until(scene, sheet.tick("merge_caption"));
        scene.overlay().showText(sheet.get("merge_caption").hold())
                .text("Banks merge up to %s by %s and %s high: one cable serves them all", size[0], size[0], size[1])
                .independent(FeLayout.UPPER).colored(PonderPalette.INPUT).attachKeyFrame();
        c.until(scene, end - 20);
        c.idle(scene, 20);
    }

    static int[] bankSize() {
        try {
            return new int[] {com.createstats.blockentity.CapacitorBankBE.maxFootprint(),
                    com.createstats.blockentity.CapacitorBankBE.maxHeight()};
        } catch (RuntimeException e) {
            return new int[] {3, 6};
        }
    }

    private static void bankClick(CreateSceneBuilder scene, SceneBuildingUtil util, Clock c, FeBanks.Pool bank,
                                  FePonderChapters.Bank.Stock stock, net.minecraft.world.item.ItemStack item, int k) {
        final int[] built = FePonderChapters.Bank.BUILT;
        c.until(scene, FePonderChapters.Bank.PLACE + FePonderChapters.Bank.PLACE_GAP * k);
        final int from = k == 0 ? 0 : built[k - 1];
        scene.overlay().showControls(top(FeStage.BANK_BLOCKS[from], 1.0), Pointing.DOWN, 8).rightClick().withItem(item);
        scene.world().showSection(at(util, java.util.Arrays.copyOfRange(FeStage.BANK_BLOCKS, from, built[k])),
                Direction.DOWN);
        scene.addInstruction(FeBanks.place(bank, from, built[k], stock, c.now));
    }

    static FePonderChapters.Bank.Stock bankStock() {
        try {
            return new FePonderChapters.Bank.Stock(com.createstats.blockentity.CapacitorBankBE.blockCapacity(),
                    com.createstats.blockentity.CapacitorBankBE.mergeExponent(),
                    com.createstats.blockentity.CapacitorBankBE.transferRate());
        } catch (RuntimeException e) {
            return FePonderChapters.Bank.Stock.defaults();
        }
    }

    public static void board(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "fe_board_and_reserve", "A board and its reserve",
                FeStage.YARD_PLATE);
        final Clock c = new Clock();
        final FePonderChapters.Deal.Run run = FePonderChapters.Deal.replay(tuning(), controllerStock(),
                FeStage.YC.asLong(), FeStage.BOARDS[0].asLong(), FeStage.BOARDS[1].asLong());
        final com.createstats.power.ponder.FePonderDeal.Recording r = run.rec();
        final Warp warp = run.warp();
        final Sheet sheet = FePonderChapters.Deal.sheet(run);
        final int end = sheet.length();
        final net.minecraft.world.level.BlockGetter level = scene.getScene().getWorld();
        final java.util.function.IntUnaryOperator at = t -> r.at(warp.tick(t));
        final FeLayout.PortHang hang = (from, to) -> new FePaths.Path(FePaths.catenary(from, FePaths.attach(level, to),
                WireTension.STANDARD.slack()));
        final FeLayout.LegHang legs = (board, stud, far) -> FePaths.leg(level, board, Direction.NORTH, stud, far);

        scene.world().showSection(plate(util, FeStage.YARD_PLATE), Direction.UP);
        c.idle(scene, 10);
        build(scene, util, c, FeLayout.yardBuild());

        scene.addInstruction(FeFaces.dealController(FeStage.YC, r, warp, c.now, end - c.now));
        for (int b = 0; b < 2; b++) {
            scene.addInstruction(FeFaces.board(FeStage.BOARDS[b], r, b, FeStage.BOARD_MACHINES[b], new int[] {0, 1},
                    warp, c.now, end - c.now));
        }
        scene.addInstruction(FeBanks.charge(List.of(new FeBanks.Pool(new BlockPos[] {FeStage.YARD_BANKS[0]}, 0),
                        new FeBanks.Pool(new BlockPos[] {FeStage.YARD_BANKS[1]}, 1)), List.of(t -> 1, t -> 1),
                List.of(t -> r.bank[0][at.applyAsInt(t)], t -> r.bank[1][at.applyAsInt(t)]),
                List.of(t -> r.bankFlow[0][at.applyAsInt(t)], t -> r.bankFlow[1][at.applyAsInt(t)]), bankStock(), c.now,
                end - c.now));
        final int range1 = FePonderChapters.Deal.RANGE_1;
        final int range2 = FePonderChapters.Deal.RANGE_2;
        scene.addInstruction(FeProps.range(FeStage.BOARD_MACHINES[0][range1],
                t -> r.working[0][range1][at.applyAsInt(t)] ? 3 : 0, t -> false, c.now, end - c.now));
        scene.addInstruction(FeProps.range(FeStage.BOARD_MACHINES[1][range2],
                t -> r.working[1][range2][at.applyAsInt(t)] ? 3 : 0, t -> false, c.now, end - c.now));
        final int lamp1 = FePonderChapters.Deal.LAMP_1;
        final int lamp2 = FePonderChapters.Deal.LAMP_2;
        scene.addInstruction(FeProps.worked(List.of(
                new FeProps.Worked(FeStage.BOARD_MACHINES[0][lamp1], FePonderPlant.Kind.LAMP,
                        t -> r.working[0][lamp1][at.applyAsInt(t)]),
                new FeProps.Worked(FeStage.BOARD_MACHINES[1][lamp2], FePonderPlant.Kind.LAMP,
                        t -> r.working[1][lamp2][at.applyAsInt(t)])), c.now, end - c.now));
        scene.addInstruction(FeFlow.beads("fe_yard_flow", FeLayout.yardStreams(hang, legs, run), FeFlow.GREEN, c.now,
                end - c.now));
        scene.addInstruction(FeDash.banks(new FeDash.Banks(warp, FeLayout.yardRows(run)), c.now, end - c.now));

        c.until(scene, FePonderChapters.Deal.ASK);
        final int asked = at.applyAsInt(c.now);
        for (int b = 0; b < 2; b++) {
            scene.addInstruction(FeLabels.travel(FeLayout.reversed(FeLayout.boardFeed(hang, b)), askWords(r, b, asked),
                    INPUT_TAG, b == FePonderChapters.Deal.BOARD_2, c.now, 70));
        }
        c.until(scene, sheet.tick("ask_caption"));
        scene.overlay().showText(sheet.get("ask_caption").hold())
                .text("Each board asks for three things: its Critical, what else it runs, what it would restore")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE).attachKeyFrame();

        c.until(scene, FePonderChapters.Deal.ANSWER);
        final int answered = at.applyAsInt(c.now);
        for (int b = 0; b < 2; b++) {
            scene.addInstruction(FeLabels.travel(FeLayout.boardFeed(hang, b), r.reason[b][answered].sentence(),
                    ANSWER_TAG, b == FePonderChapters.Deal.BOARD_2, c.now, 70));
        }
        c.until(scene, sheet.tick("answer_caption"));
        scene.overlay().showText(sheet.get("answer_caption").hold())
                .text("The controller answers each board with a share and a reason, Critical served first")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);

        c.until(scene, sheet.tick("forecast_caption"));
        scene.overlay().showText(sheet.get("forecast_caption").hold())
                .text("It runs its own policy forward, and its face says when a board will go short")
                .independent(FeLayout.UPPER).colored(PonderPalette.BLUE).attachKeyFrame();

        c.until(scene, sheet.tick("split"));
        scene.addInstruction(FeLabels.travel(FeLayout.boardFeed(hang, FePonderChapters.Deal.BOARD_2),
                r.reason[FePonderChapters.Deal.BOARD_2][at.applyAsInt(c.now + 2)].sentence(), WARN_TAG, true, c.now, 90));
        c.until(scene, sheet.tick("split_caption"));
        scene.overlay().showText(sheet.get("split_caption").hold())
                .text("Short of both, it shares Critical first: board 2's range stops, board 1's runs on")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE).attachKeyFrame();

        c.until(scene, sheet.tick("floor"));
        for (int b = 0; b < 2; b++) {
            scene.addInstruction(FeLabels.travel(FeLayout.boardFeed(hang, b), r.reason[b][at.applyAsInt(c.now + 2)]
                    .sentence(), WARN_TAG, b == FePonderChapters.Deal.BOARD_2, c.now, 90));
        }
        c.until(scene, sheet.tick("floor_caption"));
        scene.overlay().showText(sheet.get("floor_caption").hold())
                .text("Under the floor it answers Critical only, and each board keeps its own Critical running")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);

        c.until(scene, sheet.tick("feed"));
        scene.overlay().showControls(top(FeStage.BOARD_FEEDS[0], 0.7), Pointing.DOWN, 30).rightClick()
                .withItem(ModItems.COPPER_WIRE.asStack());
        c.until(scene, sheet.tick("feed_caption"));
        scene.overlay().showText(sheet.get("feed_caption").hold())
                .text("Wire an output to a connector on a board's side to give that board a reserve")
                .independent(FeLayout.UPPER).colored(PonderPalette.INPUT).attachKeyFrame();
        c.until(scene, end - 20);
        c.idle(scene, 20);
    }

    private static String askWords(com.createstats.power.ponder.FePonderDeal.Recording r, int b, int t) {
        final String words = String.format(Locale.ROOT, "Critical %,d, running %,d", r.askCritical[b][t],
                r.askRunning[b][t]);
        return r.askRestore[b][t] > 0 ? words + String.format(Locale.ROOT, ", restore %,d", r.askRestore[b][t]) : words;
    }

    public static void controller(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "fe_battery_controller", "A reserve with a policy",
                FeStage.CONTROLLER_PLATE);
        final Clock c = new Clock();
        final FePonderChapters.Controller.Run run = FePonderChapters.Controller.replay(tuning(), controllerStock());
        final com.createstats.power.ponder.FePonderController.Recording r = run.rec();
        final Warp warp = run.warp();
        final Sheet sheet = FePonderChapters.Controller.sheet(run);
        final int end = sheet.length();
        final net.minecraft.world.level.BlockGetter level = scene.getScene().getWorld();
        final java.util.function.IntUnaryOperator at = t -> r.at(warp.tick(t));
        final net.minecraft.world.item.ItemStack wire = ModItems.COPPER_WIRE.asStack();

        scene.world().showSection(plate(util, FeStage.CONTROLLER_PLATE), Direction.UP);
        c.idle(scene, 10);
        build(scene, util, c, FeLayout.controllerBuild());

        c.until(scene, FePonderChapters.Controller.TERMINALS);
        final net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        final String inputs = "inputs A B C";
        final String outputs = "outputs 1 2 3";
        final Vec3 inputsAt = FeLayout.inputsTag();
        final Vec3 outputsAt = FeLayout.outputsTag();
        final int named = FePonderChapters.Controller.POWER - c.now;
        scene.addInstruction(Labels.tag(pt -> inputsAt, t -> inputs, t -> INPUT_TAG, 8, -5, c.now, named));
        scene.addInstruction(Labels.tag(pt -> outputsAt, t -> outputs, t -> OUTPUT_TAG, -12 - font.width(outputs), -5,
                c.now, named));
        c.until(scene, sheet.tick("terminals_caption"));
        scene.overlay().showText(sheet.get("terminals_caption").hold())
                .text("Inputs A, B and C take generators and banks, outputs 1, 2 and 3 each feed a circuit")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE).attachKeyFrame();

        c.until(scene, FePonderChapters.Controller.WIRES);
        scene.overlay().showControls(top(FeStage.BC, 1.4), Pointing.DOWN, 6 * FePonderChapters.Controller.WIRE_GAP)
                .rightClick().withItem(wire);
        for (int p = 0; p < 6; p++) {
            scene.addInstruction(FeWires.port("fe_bc_" + p, FeStage.BC, p, FeStage.terminal(p), FeStage.BC_ENDS[p],
                    WireTier.COPPER, FePonderChapters.Controller.WIRES + FePonderChapters.Controller.WIRE_GAP * p,
                    FePonderChapters.Controller.WIRE_TICKS));
        }
        scene.addInstruction(FeFaces.controller(FeStage.BC, r, warp, c.now, end - c.now));
        scene.addInstruction(FeBanks.charge(List.of(new FeBanks.Pool(new BlockPos[] {FeStage.BC_DEVICES[1]}, 0),
                        new FeBanks.Pool(new BlockPos[] {FeStage.BC_DEVICES[2]}, 1)), List.of(t -> 1, t -> 1),
                List.of(t -> r.bank[0][at.applyAsInt(t)], t -> r.bank[1][at.applyAsInt(t)]),
                List.of(t -> r.bankFlow[0][at.applyAsInt(t)], t -> r.bankFlow[1][at.applyAsInt(t)]), bankStock(), c.now,
                end - c.now));
        final int range = FePonderChapters.Controller.RANGE;
        scene.addInstruction(FeProps.range(FeStage.BC_DEVICES[3], t -> r.level[range][at.applyAsInt(t)],
                t -> r.starved[range][at.applyAsInt(t)], c.now, end - c.now));
        scene.addInstruction(FeProps.worked(List.of(
                new FeProps.Worked(FeStage.BC_DEVICES[4], FePonderPlant.Kind.LAMP,
                        t -> r.level[FePonderChapters.Controller.CRITICAL_LAMP][at.applyAsInt(t)] > 0),
                new FeProps.Worked(FeStage.BC_DEVICES[5], FePonderPlant.Kind.LAMP,
                        t -> r.level[FePonderChapters.Controller.FLOORED_LAMP][at.applyAsInt(t)] > 0)), c.now, end - c.now));
        scene.addInstruction(FeFlow.beads("fe_bc_flow", FeLayout.controllerStreams((from, to) -> new FePaths.Path(
                FePaths.catenary(from, FePaths.attach(level, to), WireTension.STANDARD.slack())), run), FeFlow.GREEN,
                c.now, end - c.now));
        scene.addInstruction(FeDash.banks(new FeDash.Banks(warp, FeLayout.controllerRows(run)), c.now, end - c.now));
        c.until(scene, sheet.tick("wires_caption"));
        scene.overlay().showText(sheet.get("wires_caption").hold())
                .text("Give every bank its own input: nothing is in series, and the reserve is one number")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);

        c.until(scene, FePonderChapters.Controller.POWER - 12);
        scene.overlay().showControls(top(FeStage.BC_MOTOR, 0.6), Pointing.DOWN, 12).scroll();
        c.until(scene, FePonderChapters.Controller.POWER);
        final java.util.function.IntToDoubleFunction rpm = t -> r.rpm[at.applyAsInt(t)];
        scene.addInstruction(FeProps.spin(List.of(new FeProps.Spin(FeStage.BC_MOTOR, rpm),
                new FeProps.Spin(FeStage.BC_DYNAMO, rpm)), c.now, end - c.now));
        c.until(scene, sheet.tick("power_caption"));
        scene.overlay().showText(sheet.get("power_caption").hold())
                .text("Generation is spent on the circuits first, and what is left charges the banks")
                .independent(FeLayout.UPPER).colored(PonderPalette.GREEN).attachKeyFrame();

        c.until(scene, FePonderChapters.Controller.FLOOR);
        final String floorWords = "floor " + FePonderChapters.Controller.FLOOR_PCT + "%";
        final Vec3 floorAt = FeLayout.floorTag();
        scene.addInstruction(Labels.tag(pt -> floorAt, t -> floorWords, t -> 0xFF6A3C, 8, -5, c.now,
                FePonderChapters.Controller.STOP - c.now));
        c.until(scene, sheet.tick("floor_caption"));
        scene.overlay().showText(sheet.get("floor_caption").hold())
                .text("Output 3 has a 70% floor: below it that circuit stays off, even with generation to spare")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);

        c.until(scene, FePonderChapters.Controller.STOP - 12);
        scene.overlay().showControls(top(FeStage.BC_MOTOR, 0.6), Pointing.DOWN, 12).scroll();
        c.until(scene, sheet.tick("stop_caption"));
        scene.overlay().showText(sheet.get("stop_caption").hold())
                .text("Off generation it spends the reserve, freely while the charge is deep")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE).attachKeyFrame();

        c.until(scene, sheet.tick("droop_caption"));
        scene.overlay().showText(sheet.get("droop_caption").hold())
                .text("Lower down it lets out less and less, and the range browns out rather than switching off")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);

        c.until(scene, sheet.tick("critical_caption"));
        final String critical = "Critical";
        final Vec3 criticalAt = FeLayout.criticalTag();
        scene.addInstruction(Labels.tag(pt -> criticalAt, t -> critical, t -> 0xFF4A4A, 8, -5, c.now,
                FePonderChapters.Controller.REFILL - c.now));
        scene.overlay().showText(sheet.get("critical_caption").hold())
                .text("Under the floor it is Critical: only a trickle comes out, and the Critical lamp stays lit")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);

        c.until(scene, FePonderChapters.Controller.REFILL - 12);
        scene.overlay().showControls(top(FeStage.BC_MOTOR, 0.6), Pointing.DOWN, 12).scroll();
        c.until(scene, sheet.tick("refill_caption"));
        scene.overlay().showText(sheet.get("refill_caption").hold())
                .text("Back on generation, part of it refills the banks first: output 1 reads Recharging reserve")
                .independent(FeLayout.UPPER).colored(PonderPalette.BLUE).attachKeyFrame();

        c.until(scene, sheet.tick("screen"));
        scene.overlay().showControls(FePaths.faceCentre(FeStage.BC, Direction.NORTH), Pointing.DOWN, 30).rightClick();
        c.until(scene, sheet.tick("screen_caption"));
        scene.overlay().showText(sheet.get("screen_caption").hold())
                .text("On its screen, drag an output's floor, cap its rate, or feed it surplus only")
                .independent(FeLayout.UPPER).colored(PonderPalette.INPUT).attachKeyFrame();
        c.until(scene, end - 20);
        c.idle(scene, 20);
    }

    private static final int INPUT_TAG = 0x9ECFE8;
    private static final int OUTPUT_TAG = 0xE0A060;

    private static final int ANSWER_TAG = 0x8CBA51;
    private static final int WARN_TAG = 0xD9A441;

    static FePonderChapters.Controller.Stock controllerStock() {
        try {
            final FePonderChapters.Controller.Stock d = FePonderChapters.Controller.Stock.defaults();
            return new FePonderChapters.Controller.Stock(com.createstats.blockentity.CapacitorBankBE.blockCapacity(),
                    com.createstats.blockentity.CapacitorBankBE.transferRate(),
                    com.createstats.blockentity.BatteryControllerBE.busCapacity(),
                    com.createstats.blockentity.BatteryControllerBE.portRate(), FeRates.wire(WireTier.COPPER),
                    com.createstats.blockentity.BatteryControllerBE.band(),
                    com.createstats.blockentity.BatteryControllerBE.rideTicks(),
                    com.createstats.blockentity.BatteryControllerBE.chargeClaimPct());
        } catch (RuntimeException e) {
            return FePonderChapters.Controller.Stock.defaults();
        }
    }

    private static List<net.minecraft.network.chat.Component> connectorLines(FePonderGrid.Recording r, int base, int t) {
        final List<WireTier> tiers = new ArrayList<>();
        for (int i = 0; i < FePonderChapters.Spans.spansAt(t); i++) tiers.add(WireTier.COPPER);
        return FeGoggles.connector(com.createstats.power.ConnectorTier.POWER, base,
                Math.max(0, r.flow[FePonderChapters.Spans.RANGE][r.at(t)]), tiers);
    }

    private static int strung(int t, int from, int ticks, int gap) {
        int n = 0;
        for (int i = 0; i < FeStage.MACHINE.length; i++) if (t >= from + i * gap + ticks) n++;
        return n;
    }

    private static void buildPlant(CreateSceneBuilder scene, SceneBuildingUtil util, Clock c, boolean board) {
        build(scene, util, c, FeLayout.plant(board));
    }

    private static void build(CreateSceneBuilder scene, SceneBuildingUtil util, Clock c, List<FeLayout.Reveal> steps) {
        for (FeLayout.Reveal step : steps) {
            scene.world().showSection(at(util, step.array()), Direction.DOWN);
            if (step.then() > 0) c.idle(scene, step.then());
        }
    }

    public static void auto(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "fe_pdu_auto", "Active Auto", FeStage.WORKSHOP_PLATE);
        final Clock c = new Clock();
        Recording rec = FePonderChapters.Auto.replay(tuning());
        if (FePonderChapters.Auto.mark(rec) < 0 || FePonderChapters.Auto.deeper(rec) < 0
                || FePonderChapters.Auto.restored(rec) < 0) {
            rec = FePonderChapters.Auto.replay(Tuning.defaults());
        }
        final Recording r = rec;
        final Warp warp = FePonderChapters.Auto.warp(r);
        final Sheet sheet = FePonderChapters.Auto.sheet(r, warp);
        final int end = sheet.length();
        final int start = sheet.tick("running");
        final int lever = sheet.tick("lever");
        final int back = sheet.tick("back");

        scene.world().showSection(plate(util, FeStage.WORKSHOP_PLATE), Direction.UP);
        c.idle(scene, 10);
        buildPlant(scene, util, c, true);

        c.until(scene, start);
        scene.addInstruction(FeFaces.pdu(FeStage.PDU, r, warp, t -> FeStage.MACHINE.length, t -> true, start, end - start));
        scene.addInstruction(FeProps.spin(FeProps.driveLine(r, warp), start, end - start));
        scene.addInstruction(FeProps.spin(FeProps.motors(r, warp), start, end - start));
        scene.addInstruction(FeProps.appliances(r, warp, start, end - start));
        scene.addInstruction(FeFlow.beads("fe_trunk", trunk(r, warp), FeFlow.GREEN, start, end - start));
        scene.addInstruction(FeFlow.beads("fe_legs", legs(scene.getScene().getWorld(), r, warp), FeFlow.GREEN, start,
                end - start));

        scene.addInstruction(FeDash.show(new FeDash.Model(r, warp, true, chipPoints()), sheet.tick("strip"), start + 2,
                end - start - 2));

        c.until(scene, sheet.tick("strip"));
        tierChips(scene, r, warp, c.now, end, t -> true);
        reasons(scene, r, warp, c.now, end);
        c.until(scene, sheet.tick("strip_caption"));
        scene.overlay().showText(sheet.get("strip_caption").hold())
                .text("Active Auto lines every machine up by tier, Critical first, as on its screen")
                .independent(FeLayout.UPPER).colored(PonderPalette.BLUE).attachKeyFrame();

        c.until(scene, lever - 12);
        scene.overlay().showControls(top(FeStage.LEVER, 0.4), Pointing.DOWN, 24).rightClick();
        c.until(scene, lever);
        scene.addInstruction(FeProps.lever(true, lever));
        c.until(scene, sheet.tick("ride_caption"));
        scene.overlay().showText(sheet.get("ride_caption").hold())
                .text("Short of power, it first rides its buffer and keeps everything running")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);

        c.until(scene, sheet.tick("mark_caption"));
        scene.overlay().showText(sheet.get("mark_caption").hold())
                .text("At the restore mark it keeps only what supply and a slow draw on the buffer can run")
                .independent(FeLayout.UPPER).colored(PonderPalette.RED).attachKeyFrame();
        c.until(scene, sheet.tick("skip_caption"));
        scene.overlay().showText(sheet.get("skip_caption").hold())
                .text("What does not fit is skipped, and the smaller machines after it keep running")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE);

        c.until(scene, sheet.tick("deeper_caption"));
        scene.overlay().showText(sheet.get("deeper_caption").hold())
                .text("As the buffer falls, less fits, and the least important go first")
                .independent(FeLayout.UPPER).colored(PonderPalette.WHITE).attachKeyFrame();

        c.until(scene, back - 12);
        scene.overlay().showControls(top(FeStage.LEVER, 0.4), Pointing.DOWN, 24).rightClick();
        c.until(scene, back);
        scene.addInstruction(FeProps.lever(false, back));
        c.until(scene, sheet.tick("restored_caption"));
        scene.overlay().showText(sheet.get("restored_caption").hold())
                .text("Back past the mark, it restarts them one at a time, most important first")
                .independent(FeLayout.UPPER).colored(PonderPalette.GREEN).attachKeyFrame();
        c.until(scene, sheet.tick("payoff"));
        scene.addInstruction(com.createstats.client.ponder.Machines.sparkle(t -> top(FeStage.PDU, 1.2), c.now));

        c.until(scene, sheet.tick("locks"));
        scene.overlay().showControls(FePaths.faceCentre(FeStage.PDU, Direction.NORTH), Pointing.DOWN, 30).rightClick();
        c.until(scene, sheet.tick("locks_caption"));
        scene.overlay().showText(sheet.get("locks_caption").hold())
                .text("On its screen, lock a machine on so it is never shed, or off so it never runs")
                .independent(FeLayout.UPPER).colored(PonderPalette.INPUT).attachKeyFrame();
        c.until(scene, end - 20);
        c.idle(scene, 20);
    }
}
