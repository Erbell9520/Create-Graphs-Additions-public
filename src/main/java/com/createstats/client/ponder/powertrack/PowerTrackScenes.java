package com.createstats.client.ponder.powertrack;

import com.createstats.CreateStats;
import com.createstats.blockentity.TrackFeederBE;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.client.ponder.fe.FeBanks;
import com.createstats.client.ponder.fe.FeFlow;
import com.createstats.client.ponder.fe.FePaths;
import com.createstats.client.ponder.powertrack.PowerTrackPonderBeats.Cue;
import com.createstats.client.ponder.seismic.SlowMotion;
import com.createstats.power.CableTier;
import com.createstats.power.FeRates;
import com.createstats.power.ponder.FePonderChapters;
import com.createstats.powertrack.core.FeederPlan;
import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.RailPower;
import com.createstats.registry.ModBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntUnaryOperator;

public final class PowerTrackScenes {
    private PowerTrackScenes() {
    }

    private static final class Clock {
        int now;

        void idle(CreateSceneBuilder scene, int ticks) {
            scene.idle(ticks);
            now += ticks;
        }

        void until(CreateSceneBuilder scene, int tick) {
            if (tick < now) {
                CreateStats.LOGGER.warn("[Ponder] power track cue {} is behind the scene clock {}", tick, now);
                return;
            }
            if (tick > now) {
                idle(scene, tick - now);
            }
        }
    }

    private static CreateSceneBuilder stage(SceneBuilder builder, String id, String title, int plateX, int plate,
                                            float scale, float offsetY) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(plateX, 0, plate);
        scene.scaleSceneView(scale);
        scene.setSceneOffsetY(offsetY);
        return scene;
    }

    private static BlockPos pos(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    private static Vec3 vec(double[] p) {
        return new Vec3(p[0], p[1], p[2]);
    }

    private static Selection box(SceneBuildingUtil util, int x0, int y0, int z0, int x1, int y1, int z1) {
        return util.select().fromTo(x0, y0, z0, x1, y1, z1);
    }

    private static String ui(String key, Object... args) {
        return Component.translatable("create_stats.ponder.powertrack." + key, args).getString();
    }

    // camera set once, framed for the widest moment
    private static KitInstruction camera(float pitch, float yaw) {
        return new KitInstruction(0, 1) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                scene.getTransform().xRotation.startWithValue(pitch);
                scene.getTransform().yRotation.startWithValue(yaw);
            }
        };
    }

    private static Cue cue(List<Cue> cues, String name) {
        for (final Cue c : cues) {
            if (c.name().equals(name)) {
                return c;
            }
        }
        throw new IllegalArgumentException("no cue " + name);
    }

    private static TextElementBuilder say(CreateSceneBuilder scene, Clock c, Cue cue) {
        c.until(scene, cue.tick());
        TextElementBuilder b = scene.overlay().showText(cue.hold());
        b = cue.at() == null ? b.independent(cue.independent()) : b.pointAt(vec(cue.at()));
        return cue.key() ? b.attachKeyFrame() : b;
    }

    private static void cascadeX(CreateSceneBuilder scene, Clock c, SceneBuildingUtil util, int x0, int x1, int y, int z,
                                 int first) {
        for (int x = x0; x <= x1; x++) {
            c.until(scene, first + PowerTrackPonderBeats.CASCADE * (x - x0));
            scene.world().showSection(util.select().position(x, y, z), Direction.DOWN);
        }
    }

    private static void station(CreateSceneBuilder scene, Clock c, SceneBuildingUtil util, int[] feeder, int[] cable,
                                int[] bank, int at) {
        c.until(scene, at);
        scene.world().showSection(util.select().position(pos(feeder)), Direction.DOWN);
        c.until(scene, at + 3);
        scene.world().showSection(util.select().position(pos(cable)), Direction.DOWN);
        c.until(scene, at + 6);
        scene.world().showSection(util.select().position(pos(bank)), Direction.DOWN);
    }

    private static ElementLink<WorldSectionElement> car(CreateSceneBuilder scene, Clock c, SceneBuildingUtil util, int x0,
                                                        int z, int at, boolean cascade) {
        final int floor = PowerTrackStage.TRACK_Y + 1;
        final int deck = PowerTrackStage.CAR_DECK_Y;
        c.until(scene, at);
        if (!cascade) {
            return scene.world().showIndependentSection(box(util, x0, floor, z, x0 + PowerTrackStage.CAR_LENGTH - 1, deck, z),
                    Direction.DOWN);
        }
        Selection bogies = null;
        for (int b : PowerTrackStage.CAR_BOGIES) {
            final Selection one = util.select().position(x0 + b, floor, z);
            bogies = bogies == null ? one : bogies.add(one);
        }
        final ElementLink<WorldSectionElement> link = scene.world().showIndependentSection(bogies, Direction.DOWN);
        c.until(scene, at + PowerTrackPonderBeats.CAR_STEP);
        for (int k : PowerTrackStage.CAR_COLLECTORS) {
            scene.world().showSectionAndMerge(util.select().position(x0 + k, floor, z), Direction.DOWN, link);
        }
        c.until(scene, at + 2 * PowerTrackPonderBeats.CAR_STEP);
        scene.world().showSectionAndMerge(box(util, x0, deck, z, x0 + PowerTrackStage.CAR_LENGTH - 1, deck, z), Direction.DOWN, link);
        return link;
    }

    private static BlockPos[] bogies(int x0, int z) {
        final BlockPos[] out = new BlockPos[PowerTrackStage.CAR_BOGIES.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = new BlockPos(x0 + PowerTrackStage.CAR_BOGIES[i], PowerTrackStage.TRACK_Y + 1, z);
        }
        return out;
    }

    private static String modeText(FeederPlan.Mode mode, int fe) {
        return TrackFeederBE.glyph(mode) + " " + Component.translatable(TrackFeederBE.modeKey(mode)).getString() + ", "
                + FeRates.perTick(fe);
    }

    private static String floorText(int pct) {
        return ui("floor", pct);
    }

    private static int modeRgb(FeederPlan.Mode mode) {
        return TrackFeederBE.onDark(mode);
    }

    private static Vec3 overFeeder(int[] feeder) {
        return new Vec3(feeder[0] + 0.5, feeder[1] + 1.35, feeder[2] + 0.5);
    }

    private static Vec3 underDoor(int[] feeder) {
        return vec(PowerTrackPonderBeats.underDoor(feeder));
    }

    private static Vec3 between(int[] a, int[] b) {
        return new Vec3((a[0] + b[0]) / 2.0 + 0.5, a[1], (a[2] + b[2]) / 2.0 + 0.5);
    }

    private static KitInstruction beads(String slot, int[] feeder, int[] cable, int[] bank, List<RailStory.Frame> frames,
                                        int index, int start, int ticks) {
        final Vec3 bankFace = between(cable, bank);
        final Vec3 gland = between(feeder, cable);
        final BlockPos[] run = {pos(cable)};
        final FePaths.Path in = FePaths.floorRun(bankFace, run, gland, CableTier.SURFACE);
        final FePaths.Path out = FePaths.floorRun(gland, run, bankFace, CableTier.SURFACE);
        final FeFlow.Rate toFeeder = t -> t >= 0 && t < frames.size() ? Math.max(0, frames.get(t).feeders()[index].circuitFlow()) : 0;
        final FeFlow.Rate toBank = t -> t >= 0 && t < frames.size() ? Math.max(0, -frames.get(t).feeders()[index].circuitFlow()) : 0;
        return FeFlow.beads(slot, List.of(new FeFlow.Stream(in, toFeeder), new FeFlow.Stream(out, toBank)), FeFlow.GREEN, start, ticks);
    }

    private static KitInstruction bankFaces(List<RailStory.Frame> frames, List<BlockPos> banks, List<IntUnaryOperator> shown,
                                            List<IntUnaryOperator> stored, List<IntUnaryOperator> flow, int start, int ticks) {
        final List<FeBanks.Pool> pools = new ArrayList<>();
        for (BlockPos b : banks) {
            pools.add(new FeBanks.Pool(new BlockPos[] {b}, 0));
        }
        return FeBanks.charge(pools, shown, stored, flow, FePonderChapters.Bank.Stock.defaults(), start, ticks);
    }

    private static IntUnaryOperator from(int tick) {
        return t -> t >= tick ? 1 : 0;
    }

    private static int[] translate(int[] p, int dx) {
        return new int[] {p[0] + dx, p[1], p[2]};
    }

    public static void laying(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "power_track_laying", "Electrifying a line",
                PowerTrackStage.LINE_PLATE_X, PowerTrackStage.LINE_PLATE, PowerTrackStage.LINE_SCALE, PowerTrackStage.LINE_OFFSET_Y);
        final Clock c = new Clock();
        final PowerTrackStage.Stage st = PowerTrackPonderBeats.Laying.stage();
        final int length = PowerTrackPonderBeats.Laying.LENGTH;
        final List<RailStory.Frame> frames = PowerTrackPonderBeats.Laying.story(st.layout()).play(length);
        final List<Cue> cues = PowerTrackPonderBeats.Laying.cues();
        final int[] a = PowerTrackStage.FEEDER_A;
        final int z = PowerTrackStage.LINE_Z;
        scene.addInstruction(camera(PowerTrackStage.LINE_PITCH, PowerTrackStage.LINE_YAW));
        final RailPicture[] picture = new RailPicture[1];
        scene.addInstruction(RailPicture.show("power_track_rail", st, new RailPicture.Reveal() {
            @Override
            public double chairs(int edge, double tick) {
                return PowerTrackPonderBeats.Laying.chairFront(tick);
            }

            @Override
            public double rail(int edge, double tick) {
                return PowerTrackPonderBeats.Laying.railFront(tick);
            }

            @Override
            public int tapAt(int i) {
                return i == 0 ? PowerTrackPonderBeats.Laying.TAP : Integer.MAX_VALUE / 2;
            }
        }, picture, 0, length));
        scene.addInstruction(new BlockDriver(frames, st.layout().sections(),
                List.of(new BlockDriver.FeederAt(pos(a), 0)), null,
                List.of(new BlockDriver.BankAt(pos(PowerTrackStage.BANK_A), t -> frames.get(t).feeders()[0].stationStored()
                        / (double) PowerTrackPonderBeats.BANK_FE)), 0, length));

        scene.world().showSection(box(util, 0, 0, 0, PowerTrackStage.LINE_FLOOR[0] - 1, 0, PowerTrackStage.LINE_FLOOR[1] - 1), Direction.UP);
        cascadeX(scene, c, util, PowerTrackStage.LINE_X[0], PowerTrackStage.LINE_X[1], PowerTrackStage.TRACK_Y, z,
                PowerTrackPonderBeats.Laying.TRACK);
        say(scene, c, cue(cues, "intro"))
                .text("Power Track is Create's track with a centre conductor rail: lay it like any track")
                .colored(PonderPalette.BLUE);
        say(scene, c, cue(cues, "chairs"))
                .text("The rail rides on porcelain chairs, clear of every bogie: only a shoe touches it")
                .colored(PonderPalette.WHITE);

        c.until(scene, PowerTrackPonderBeats.Laying.FEEDER_CUE);
        scene.overlay().showControls(vec(PowerTrackPonderBeats.railAt(a[0] + 0.5, z)), Pointing.DOWN, 30).rightClick()
                .withItem(ModBlocks.TRACK_FEEDER.asStack());
        c.until(scene, PowerTrackPonderBeats.Laying.FEEDER);
        scene.world().showSection(util.select().position(pos(a)), Direction.DOWN);
        say(scene, c, cue(cues, "feeder"))
                .text("Click the track with a Track Feeder, then stand the cabinet beside the line")
                .colored(PonderPalette.INPUT);
        c.until(scene, PowerTrackPonderBeats.Laying.CABLE);
        scene.world().showSection(util.select().position(pos(PowerTrackStage.CABLE_A)), Direction.DOWN);
        c.until(scene, PowerTrackPonderBeats.Laying.BANK);
        scene.world().showSection(util.select().position(pos(PowerTrackStage.BANK_A)), Direction.DOWN);
        scene.addInstruction(beads("power_track_feed_a", a, PowerTrackStage.CABLE_A, PowerTrackStage.BANK_A, frames, 0, c.now, length - c.now));
        scene.addInstruction(bankFaces(frames, List.of(pos(PowerTrackStage.BANK_A)), List.of(from(c.now)),
                List.of(t -> frames.get(t).feeders()[0].stationStored()), List.of(t -> -frames.get(t).feeders()[0].circuitFlow()),
                c.now, length - c.now));
        say(scene, c, cue(cues, "wire"))
                .text("Wire its back like any machine: a cable to the station's banks")
                .colored(PonderPalette.INPUT);
        say(scene, c, cue(cues, "live"))
                .text("The section is live: the lantern says so. The needle rests at zero until a train comes")
                .colored(PonderPalette.GREEN);
        say(scene, c, cue(cues, "figures"))
                .text("A section carries up to %s; each shoe on a car takes %s",
                        FeRates.perTick(RailPower.sectionCeiling(PowerTrackPonderBeats.base())),
                        FeRates.perTick(RailPower.collectorRate(PowerTrackPonderBeats.base())))
                .colored(PonderPalette.WHITE);

        c.until(scene, PowerTrackPonderBeats.Laying.DIAL_CUE);
        scene.overlay().showControls(vec(PowerTrackPonderBeats.dialWindow(a)), Pointing.DOWN, 24).rightClick();
        c.until(scene, PowerTrackPonderBeats.Laying.MODE_TAG);
        final Vec3 over = overFeeder(a);
        scene.addInstruction(PowerTrackOverlays.tag(pt -> over, t -> modeText(frames.get(t).feeders()[0].mode(),
                frames.get(t).feeders()[0].dialFe()), t -> modeRgb(frames.get(t).feeders()[0].mode()), 0, -14, c.now,
                PowerTrackPonderBeats.Laying.MODE_TAG_END - c.now));
        c.until(scene, PowerTrackPonderBeats.Laying.SCROLL_CUE);
        scene.overlay().showControls(vec(PowerTrackPonderBeats.dialWindow(a)), Pointing.DOWN, 24).scroll();
        say(scene, c, cue(cues, "dial"))
                .text("Hold use on the rate window to pick Supply, Collect or Auto, and scroll the rate")
                .colored(PonderPalette.INPUT);
        say(scene, c, cue(cues, "goggles"))
                .text("Goggles on the track read the section, and name every gap in the rail")
                .colored(PonderPalette.WHITE);
        c.until(scene, length);
    }

    public static void collector(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "power_track_collector", "A train on the rail",
                PowerTrackStage.LINE_PLATE_X, PowerTrackStage.LINE_PLATE, PowerTrackStage.LINE_SCALE, PowerTrackStage.LINE_OFFSET_Y);
        final Clock c = new Clock();
        final PowerTrackStage.Stage st = PowerTrackPonderBeats.Collector.stage();
        final int length = PowerTrackPonderBeats.Collector.LENGTH;
        final List<RailStory.Frame> frames = PowerTrackPonderBeats.Collector.frames();
        final List<Cue> cues = PowerTrackPonderBeats.Collector.cues();
        final int z = PowerTrackStage.LINE_Z;
        final int[] a = PowerTrackStage.FEEDER_A;
        final int[] b = PowerTrackStage.FEEDER_B;
        scene.addInstruction(camera(PowerTrackStage.LINE_PITCH, PowerTrackStage.LINE_YAW));
        final RailPicture[] picture = new RailPicture[1];
        scene.addInstruction(RailPicture.show("power_track_rail", st, RailPicture.together(PowerTrackPonderBeats.Collector.RAIL,
                PowerTrackPonderBeats.Collector.RAIL_SPEED, i -> i == 0 ? PowerTrackPonderBeats.Collector.STATION_A
                        : PowerTrackPonderBeats.Collector.STATION_B), picture, 0, length));
        final BlockDriver.InsulatorAt ins = new BlockDriver.InsulatorAt(pos(PowerTrackStage.INSULATOR), PowerTrackStage.LINE_WEST,
                PowerTrackStage.LINE_EAST, PowerTrackStage.lineAt(PowerTrackStage.INSULATOR[0]));
        final BlockPos carBank = new BlockPos(PowerTrackStage.LINE_CAR_X + PowerTrackStage.CAR_BANK_X, PowerTrackStage.CAR_DECK_Y, z);
        scene.addInstruction(new BlockDriver(frames, st.layout().sections(),
                List.of(new BlockDriver.FeederAt(pos(a), 0), new BlockDriver.FeederAt(pos(b), 1)), ins,
                List.of(new BlockDriver.BankAt(pos(PowerTrackStage.BANK_A), t -> frames.get(t).feeders()[0].stationStored() / (double) PowerTrackPonderBeats.BANK_FE),
                        new BlockDriver.BankAt(pos(PowerTrackStage.BANK_B), t -> frames.get(t).feeders()[1].stationStored() / (double) PowerTrackPonderBeats.BANK_FE),
                        new BlockDriver.BankAt(carBank, t -> frames.get(t).carStored() / (double) PowerTrackPonderBeats.BANK_FE)),
                0, length));

        scene.world().showSection(box(util, 0, 0, 0, PowerTrackStage.LINE_FLOOR[0] - 1, 0, PowerTrackStage.LINE_FLOOR[1] - 1), Direction.UP);
        cascadeX(scene, c, util, PowerTrackStage.LINE_X[0], PowerTrackStage.LINE_X[1], PowerTrackStage.TRACK_Y, z,
                PowerTrackPonderBeats.Collector.TRACK);
        station(scene, c, util, a, PowerTrackStage.CABLE_A, PowerTrackStage.BANK_A, PowerTrackPonderBeats.Collector.STATION_A);
        c.until(scene, PowerTrackPonderBeats.Collector.INSULATOR);
        scene.world().showSection(util.select().position(pos(PowerTrackStage.INSULATOR)), Direction.DOWN);
        station(scene, c, util, b, PowerTrackStage.CABLE_B, PowerTrackStage.BANK_B, PowerTrackPonderBeats.Collector.STATION_B);
        scene.addInstruction(beads("power_track_feed_a", a, PowerTrackStage.CABLE_A, PowerTrackStage.BANK_A, frames, 0, c.now, length - c.now));
        scene.addInstruction(beads("power_track_feed_b", b, PowerTrackStage.CABLE_B, PowerTrackStage.BANK_B, frames, 1, c.now, length - c.now));
        scene.addInstruction(bankFaces(frames, List.of(pos(PowerTrackStage.BANK_A), pos(PowerTrackStage.BANK_B), carBank),
                List.of(from(c.now), from(c.now), from(PowerTrackPonderBeats.Collector.CAR + 2 * PowerTrackPonderBeats.CAR_STEP)),
                List.of(t -> frames.get(t).feeders()[0].stationStored(), t -> frames.get(t).feeders()[1].stationStored(), t -> frames.get(t).carStored()),
                List.of(t -> -frames.get(t).feeders()[0].circuitFlow(), t -> -frames.get(t).feeders()[1].circuitFlow(), t -> frames.get(t).carFlow()),
                c.now, length - c.now));

        final ElementLink<WorldSectionElement> car = car(scene, c, util, PowerTrackStage.LINE_CAR_X, z, PowerTrackPonderBeats.Collector.CAR, true);
        final SceneRailHook hook = new SceneRailHook(st.layout(), PowerTrackStage.LINE_CAR_X, PowerTrackStage.TRACK_Y + 1, z,
                PowerTrackStage.CAR_COLLECTORS);
        scene.addInstruction(new CarDriver(car, frames, hook, picture, bogies(PowerTrackStage.LINE_CAR_X, z), c.now, length - c.now));
        say(scene, c, cue(cues, "hang"))
                .text("Hang a Current Collector under a car, a block above the track, centred or a block aside")
                .colored(PonderPalette.BLUE);
        say(scene, c, cue(cues, "bogie"))
                .text("Two or three blocks from a bogie the shoe follows curves best; two shoes a car count")
                .colored(PonderPalette.WHITE);
        say(scene, c, cue(cues, "charge"))
                .text("On a live section the car's banks charge, %s a shoe; the sparks mean current",
                        FeRates.perTick(RailPower.collectorRate(PowerTrackPonderBeats.base())))
                .colored(PonderPalette.GREEN);
        say(scene, c, cue(cues, "standing"))
                .text("Standing or rolling, a shoe on live metal charges the car")
                .colored(PonderPalette.WHITE);

        c.until(scene, PowerTrackPonderBeats.Collector.SLOW_IN);
        final IntUnaryOperator slow = PowerTrackPonderBeats.Collector.slow();
        scene.addInstruction(SlowMotion.slow(slow, c.now, PowerTrackPonderBeats.Collector.SLOW_END - c.now));
        scene.addInstruction(PowerTrackOverlays.note(ui("slow", PowerTrackPonderBeats.Collector.SLOW), t -> slow.applyAsInt(t) > 1,
                c.now, PowerTrackPonderBeats.Collector.SLOW_END + 10 - c.now));
        say(scene, c, cue(cues, "insulator"))
                .text("At an insulator the rail stops: the shoe drops to its stop, then arcs as it lands beyond")
                .colored(PonderPalette.WHITE);
        say(scene, c, cue(cues, "give"))
                .text("On a collecting section the car gives, down to that feeder's floor")
                .colored(PonderPalette.GREEN);
        say(scene, c, cue(cues, "floor"))
                .text("At the floor the car keeps its charge: the sparks stop and the needle rests")
                .colored(PonderPalette.WHITE);
        c.until(scene, length);
    }

    public static void feeder(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "power_track_feeder", "Supply, Collect and Auto",
                PowerTrackStage.LINE_PLATE_X, PowerTrackStage.LINE_PLATE, PowerTrackStage.LINE_SCALE, PowerTrackStage.LINE_OFFSET_Y);
        final Clock c = new Clock();
        final PowerTrackStage.Stage st = PowerTrackPonderBeats.Feeder.stage();
        final int length = PowerTrackPonderBeats.Feeder.LENGTH;
        final List<RailStory.Frame> frames = PowerTrackPonderBeats.Feeder.frames();
        final List<Cue> cues = PowerTrackPonderBeats.Feeder.cues();
        final int z = PowerTrackStage.LINE_Z;
        final int[] a = PowerTrackStage.FEEDER_A;
        final int[] b = PowerTrackStage.FEEDER_B;
        scene.addInstruction(camera(PowerTrackStage.LINE_PITCH, PowerTrackStage.LINE_YAW));
        final RailPicture[] picture = new RailPicture[1];
        scene.addInstruction(RailPicture.show("power_track_rail", st, RailPicture.together(PowerTrackPonderBeats.Feeder.RAIL,
                PowerTrackPonderBeats.Feeder.RAIL_SPEED, i -> i == 0 ? PowerTrackPonderBeats.Feeder.STATION_A
                        : PowerTrackPonderBeats.Feeder.STATION_B), picture, 0, length));
        final BlockDriver.InsulatorAt ins = new BlockDriver.InsulatorAt(pos(PowerTrackStage.INSULATOR), PowerTrackStage.LINE_WEST,
                PowerTrackStage.LINE_EAST, PowerTrackStage.lineAt(PowerTrackStage.INSULATOR[0]));
        final BlockPos carBank = new BlockPos(PowerTrackStage.LINE_CAR_X + PowerTrackStage.CAR_BANK_X, PowerTrackStage.CAR_DECK_Y, z);
        scene.addInstruction(new BlockDriver(frames, st.layout().sections(),
                List.of(new BlockDriver.FeederAt(pos(a), 0), new BlockDriver.FeederAt(pos(b), 1)), ins,
                List.of(new BlockDriver.BankAt(pos(PowerTrackStage.BANK_A), t -> frames.get(t).feeders()[0].stationStored() / (double) PowerTrackPonderBeats.BANK_FE),
                        new BlockDriver.BankAt(pos(PowerTrackStage.BANK_B), t -> frames.get(t).feeders()[1].stationStored() / (double) PowerTrackPonderBeats.BANK_FE),
                        new BlockDriver.BankAt(carBank, t -> frames.get(t).carStored() / (double) PowerTrackPonderBeats.BANK_FE)),
                0, length));

        scene.world().showSection(box(util, 0, 0, 0, PowerTrackStage.LINE_FLOOR[0] - 1, 0, PowerTrackStage.LINE_FLOOR[1] - 1), Direction.UP);
        cascadeX(scene, c, util, PowerTrackStage.LINE_X[0], PowerTrackStage.LINE_X[1], PowerTrackStage.TRACK_Y, z,
                PowerTrackPonderBeats.Feeder.TRACK);
        station(scene, c, util, a, PowerTrackStage.CABLE_A, PowerTrackStage.BANK_A, PowerTrackPonderBeats.Feeder.STATION_A);
        c.until(scene, PowerTrackPonderBeats.Feeder.INSULATOR);
        scene.world().showSection(util.select().position(pos(PowerTrackStage.INSULATOR)), Direction.DOWN);
        station(scene, c, util, b, PowerTrackStage.CABLE_B, PowerTrackStage.BANK_B, PowerTrackPonderBeats.Feeder.STATION_B);
        scene.addInstruction(beads("power_track_feed_a", a, PowerTrackStage.CABLE_A, PowerTrackStage.BANK_A, frames, 0, c.now, length - c.now));
        scene.addInstruction(beads("power_track_feed_b", b, PowerTrackStage.CABLE_B, PowerTrackStage.BANK_B, frames, 1, c.now, length - c.now));
        scene.addInstruction(bankFaces(frames, List.of(pos(PowerTrackStage.BANK_A), pos(PowerTrackStage.BANK_B), carBank),
                List.of(from(c.now), from(c.now), from(PowerTrackPonderBeats.Feeder.CAR)),
                List.of(t -> frames.get(t).feeders()[0].stationStored(), t -> frames.get(t).feeders()[1].stationStored(), t -> frames.get(t).carStored()),
                List.of(t -> -frames.get(t).feeders()[0].circuitFlow(), t -> -frames.get(t).feeders()[1].circuitFlow(), t -> frames.get(t).carFlow()),
                c.now, length - c.now));

        final ElementLink<WorldSectionElement> car = car(scene, c, util, PowerTrackStage.LINE_CAR_X, z, PowerTrackPonderBeats.Feeder.CAR, false);
        final SceneRailHook hook = new SceneRailHook(st.layout(), PowerTrackStage.LINE_CAR_X, PowerTrackStage.TRACK_Y + 1, z,
                PowerTrackStage.CAR_COLLECTORS);
        scene.addInstruction(new CarDriver(car, frames, hook, picture, bogies(PowerTrackStage.LINE_CAR_X, z), c.now, length - c.now));
        say(scene, c, cue(cues, "intro"))
                .text("A Track Feeder ties a section of rail to an FE circuit, like a Portable Power Interface")
                .colored(PonderPalette.BLUE);

        final Vec3 overA = overFeeder(a);
        final Vec3 underA = underDoor(a);
        final Vec3 overB = overFeeder(b);
        final Vec3 underB = underDoor(b);
        c.until(scene, PowerTrackPonderBeats.Feeder.SUPPLY_CUE);
        scene.overlay().showControls(vec(PowerTrackPonderBeats.dialWindow(a)), Pointing.DOWN, 24).scroll();
        scene.addInstruction(PowerTrackOverlays.tag(pt -> overA, t -> modeText(frames.get(t).feeders()[0].mode(),
                frames.get(t).feeders()[0].dialFe()), t -> modeRgb(frames.get(t).feeders()[0].mode()), 0, -14, c.now, length - c.now));
        scene.addInstruction(PowerTrackOverlays.tag(pt -> underA, t -> floorText(frames.get(t).feeders()[0].floorPct()),
                t -> 0xE8E8E8, 0, 4, c.now, length - c.now));
        say(scene, c, cue(cues, "supply"))
                .text("Supply puts the station's power on the rail, and never drains it below the floor")
                .colored(PonderPalette.INPUT);
        say(scene, c, cue(cues, "floor"))
                .text("At the floor the feeder holds: the lantern goes dark and the needle rests at zero")
                .colored(PonderPalette.WHITE);

        c.until(scene, PowerTrackPonderBeats.Feeder.COLLECT_CUE);
        scene.overlay().showControls(vec(PowerTrackPonderBeats.dialWindow(b)), Pointing.DOWN, 24).scroll();
        scene.addInstruction(PowerTrackOverlays.tag(pt -> overB, t -> modeText(frames.get(t).feeders()[1].mode(),
                frames.get(t).feeders()[1].dialFe()), t -> modeRgb(frames.get(t).feeders()[1].mode()), 0, -14, c.now, length - c.now));
        scene.addInstruction(PowerTrackOverlays.tag(pt -> underB, t -> floorText(frames.get(t).feeders()[1].floorPct()),
                t -> 0xE8E8E8, 0, 4, c.now, length - c.now));
        say(scene, c, cue(cues, "collect"))
                .text("Collect takes power off passing trains, leaving each one its floor's charge")
                .colored(PonderPalette.INPUT);

        c.until(scene, PowerTrackPonderBeats.Feeder.AUTO_FLOOR_CUE);
        scene.overlay().showControls(vec(PowerTrackPonderBeats.floorWindow(a)), Pointing.DOWN, 24).scroll();
        c.until(scene, PowerTrackPonderBeats.Feeder.AUTO_A_CUE);
        scene.overlay().showControls(vec(PowerTrackPonderBeats.dialWindow(a)), Pointing.DOWN, 24).scroll();
        c.until(scene, PowerTrackPonderBeats.Feeder.AUTO_B_CUE);
        scene.overlay().showControls(vec(PowerTrackPonderBeats.dialWindow(b)), Pointing.DOWN, 24).scroll();
        say(scene, c, cue(cues, "auto"))
                .text("Auto supplies above the floor and collects below it: trains carry power where it is short")
                .colored(PonderPalette.GREEN);
        say(scene, c, cue(cues, "needle"))
                .text("The needle swings right while a feeder supplies and left while it collects")
                .colored(PonderPalette.WHITE);
        say(scene, c, cue(cues, "lantern"))
                .text("The lantern shows the rail: green energised, amber collecting, dark when nothing feeds it")
                .colored(PonderPalette.WHITE);
        c.until(scene, length);
    }

    public static void gaps(SceneBuilder builder, SceneBuildingUtil util) {
        final CreateSceneBuilder scene = stage(builder, "power_track_gaps", "Where the rail stops",
                PowerTrackStage.YARD_PLATE_X, PowerTrackStage.YARD_PLATE, PowerTrackStage.YARD_SCALE, PowerTrackStage.YARD_OFFSET_Y);
        final Clock c = new Clock();
        final PowerTrackStage.Stage st = PowerTrackPonderBeats.Gaps.stage();
        final int length = PowerTrackPonderBeats.Gaps.LENGTH;
        final List<RailStory.Frame> frames = PowerTrackPonderBeats.Gaps.frames();
        final List<Cue> cues = PowerTrackPonderBeats.Gaps.cues();
        final int z = PowerTrackStage.LINE_Z;
        final int[] a = PowerTrackStage.YARD_FEEDER_A;
        final int[] b = PowerTrackStage.YARD_FEEDER_B;
        scene.addInstruction(camera(PowerTrackStage.YARD_PITCH, PowerTrackStage.YARD_YAW));
        final RailPicture[] picture = new RailPicture[1];
        scene.addInstruction(RailPicture.show("power_track_rail", st, RailPicture.together(PowerTrackPonderBeats.Gaps.RAIL,
                PowerTrackPonderBeats.Gaps.RAIL_SPEED, i -> i == 0 ? PowerTrackPonderBeats.Gaps.STATION_A
                        : PowerTrackPonderBeats.Gaps.STATION_B), picture, 0, length));
        final BlockDriver.InsulatorAt ins = new BlockDriver.InsulatorAt(pos(PowerTrackStage.YARD_INSULATOR), PowerTrackStage.YARD_WEST,
                PowerTrackStage.YARD_JUNCTION, PowerTrackStage.yardWestAt(PowerTrackStage.YARD_INSULATOR[0]));
        final BlockPos carBank = new BlockPos(PowerTrackStage.YARD_CAR_X + PowerTrackStage.CAR_BANK_X, PowerTrackStage.CAR_DECK_Y, z);
        scene.addInstruction(new BlockDriver(frames, st.layout().sections(),
                List.of(new BlockDriver.FeederAt(pos(a), 0), new BlockDriver.FeederAt(pos(b), 1)), ins,
                List.of(new BlockDriver.BankAt(pos(PowerTrackStage.YARD_BANK_A), t -> frames.get(t).feeders()[0].stationStored() / (double) PowerTrackPonderBeats.BANK_FE),
                        new BlockDriver.BankAt(pos(PowerTrackStage.YARD_BANK_B), t -> frames.get(t).feeders()[1].stationStored() / (double) PowerTrackPonderBeats.BANK_FE),
                        new BlockDriver.BankAt(carBank, t -> frames.get(t).carStored() / (double) PowerTrackPonderBeats.BANK_FE)),
                0, length));

        scene.world().showSection(box(util, 0, 0, 0, PowerTrackStage.YARD_FLOOR[0] - 1, 0, PowerTrackStage.YARD_FLOOR[1] - 1), Direction.UP);
        cascadeX(scene, c, util, PowerTrackStage.YARD_X[0], PowerTrackStage.YARD_X[1], PowerTrackStage.TRACK_Y, z,
                PowerTrackPonderBeats.Gaps.TRACK);
        c.until(scene, PowerTrackPonderBeats.Gaps.BRANCH);
        scene.world().showSection(util.select().position(pos(PowerTrackStage.YARD_TURN_TO)), Direction.DOWN);
        c.idle(scene, PowerTrackPonderBeats.CASCADE);
        scene.world().showSection(util.select().position(pos(PowerTrackStage.YARD_BRANCH_END)), Direction.DOWN);

        cascadeX(scene, c, util, PowerTrackStage.YARD_SLOPE_LOW[0], PowerTrackStage.YARD_SLOPE_LOW[1], PowerTrackStage.TRACK_Y,
                PowerTrackStage.YARD_SLOPE_Z, PowerTrackPonderBeats.Gaps.SLOPE);
        for (int i = 0; i <= PowerTrackStage.YARD_SLOPE_RISE[1] - PowerTrackStage.YARD_SLOPE_RISE[0]; i++) {
            c.idle(scene, PowerTrackPonderBeats.CASCADE);
            scene.world().showSection(util.select().position(PowerTrackStage.YARD_SLOPE_RISE[0] + i, PowerTrackStage.TRACK_Y + i,
                    PowerTrackStage.YARD_SLOPE_Z), Direction.DOWN);
        }
        for (int x = PowerTrackStage.YARD_SLOPE_HIGH[0]; x <= PowerTrackStage.YARD_SLOPE_HIGH[1]; x++) {
            c.idle(scene, PowerTrackPonderBeats.CASCADE);
            scene.world().showSection(util.select().position(x, PowerTrackStage.YARD_SLOPE_TOP_Y, PowerTrackStage.YARD_SLOPE_Z), Direction.DOWN);
        }
        station(scene, c, util, a, PowerTrackStage.YARD_CABLE_A, PowerTrackStage.YARD_BANK_A, PowerTrackPonderBeats.Gaps.STATION_A);
        scene.addInstruction(beads("power_track_feed_a", a, PowerTrackStage.YARD_CABLE_A, PowerTrackStage.YARD_BANK_A, frames, 0, c.now, length - c.now));
        c.until(scene, PowerTrackPonderBeats.Gaps.INSULATOR);
        scene.world().showSection(util.select().position(pos(PowerTrackStage.YARD_INSULATOR)), Direction.DOWN);
        say(scene, c, cue(cues, "insulator"))
                .text("A Section Insulator ends a section: nothing crosses its gap; each lamp shows its own side")
                .colored(PonderPalette.BLUE);
        station(scene, c, util, b, PowerTrackStage.YARD_CABLE_B, PowerTrackStage.YARD_BANK_B, PowerTrackPonderBeats.Gaps.STATION_B);
        scene.addInstruction(beads("power_track_feed_b", b, PowerTrackStage.YARD_CABLE_B, PowerTrackStage.YARD_BANK_B, frames, 1, c.now, length - c.now));
        scene.addInstruction(bankFaces(frames, List.of(pos(PowerTrackStage.YARD_BANK_A), pos(PowerTrackStage.YARD_BANK_B), carBank),
                List.of(from(PowerTrackPonderBeats.Gaps.STATION_A + 6), from(c.now + 6), from(PowerTrackPonderBeats.Gaps.CAR + 2 * PowerTrackPonderBeats.CAR_STEP)),
                List.of(t -> frames.get(t).feeders()[0].stationStored(), t -> frames.get(t).feeders()[1].stationStored(), t -> frames.get(t).carStored()),
                List.of(t -> -frames.get(t).feeders()[0].circuitFlow(), t -> -frames.get(t).feeders()[1].circuitFlow(), t -> frames.get(t).carFlow()),
                c.now, length - c.now));
        say(scene, c, cue(cues, "lamp"))
                .text("A feeder on the far side lights its lamp for that section alone")
                .colored(PonderPalette.GREEN);
        say(scene, c, cue(cues, "junction"))
                .text("A junction keeps the section: the rail stops clear of the points and jumpers carry it on")
                .colored(PonderPalette.WHITE);
        say(scene, c, cue(cues, "points"))
                .text("The gap is as long as the points: a gentle turnout takes longer to clear than a sharp one")
                .colored(PonderPalette.WHITE);
        say(scene, c, cue(cues, "grade"))
                .text("At a change of grade the rail stops %s blocks short each side, clear of a pitching bogie",
                        String.format(Locale.ROOT, "%s", RailLayout.GRADE_GAP))
                .colored(PonderPalette.WHITE);
        say(scene, c, cue(cues, "slope"))
                .text("The slope keeps its own rail between the two ramps; a slope too short for one gets none")
                .colored(PonderPalette.WHITE);

        final ElementLink<WorldSectionElement> car = car(scene, c, util, PowerTrackStage.YARD_CAR_X, z, PowerTrackPonderBeats.Gaps.CAR, true);
        final SceneRailHook hook = new SceneRailHook(st.layout(), PowerTrackStage.YARD_CAR_X, PowerTrackStage.TRACK_Y + 1, z,
                PowerTrackStage.CAR_COLLECTORS);
        scene.addInstruction(new CarDriver(car, frames, hook, picture, bogies(PowerTrackStage.YARD_CAR_X, z), c.now, length - c.now));
        c.until(scene, PowerTrackPonderBeats.Gaps.TAGS);
        final RailLayout.Shape west = st.layout().shape(PowerTrackStage.YARD_WEST, PowerTrackStage.YARD_JUNCTION);
        final RailLayout.Shape east = st.layout().shape(PowerTrackStage.YARD_JUNCTION, PowerTrackStage.YARD_EAST);
        final double westEnd = st.layout().sections().pieces(west.lo(), west.hi()).get(1).railTo();
        final double eastStart = st.layout().sections().pieces(east.lo(), east.hi()).get(0).railFrom();
        final Vec3 insulatedGap = new Vec3(PowerTrackStage.YARD_INSULATOR[0] + 0.5, PowerTrackStage.TRACK_Y + 0.4, z + 0.5);
        final Vec3 junctionGap = new Vec3((west.point(westEnd)[0] + east.point(eastStart)[0]) / 2.0, PowerTrackStage.TRACK_Y + 0.4, z + 0.5);
        final Vec3 gradeGap = new Vec3(PowerTrackStage.yardNode(PowerTrackStage.YARD_SLOPE_FOOT)[0], PowerTrackStage.TRACK_Y + 0.4,
                PowerTrackStage.YARD_SLOPE_Z + 0.5);
        scene.addInstruction(PowerTrackOverlays.tag(pt -> insulatedGap, t -> ui("gap.insulated"), t -> 0xFFC860, 0, -12, c.now, length - c.now));
        scene.addInstruction(PowerTrackOverlays.tag(pt -> junctionGap, t -> ui("gap.junction"), t -> 0xFFC860, 0, -12, c.now + 4, length - c.now - 4));
        scene.addInstruction(PowerTrackOverlays.tag(pt -> gradeGap, t -> ui("gap.grade"), t -> 0xFFC860, 0, -12, c.now + 8, length - c.now - 8));
        say(scene, c, cue(cues, "gap"))
                .text("A shoe in any gap hangs on its stop and charges nothing: the goggles name each gap")
                .colored(PonderPalette.RED);
        say(scene, c, cue(cues, "end"))
                .text("Where the line ends, or ordinary track begins, the rail stops in a plain ramp")
                .colored(PonderPalette.WHITE);
        c.until(scene, length);
    }
}
