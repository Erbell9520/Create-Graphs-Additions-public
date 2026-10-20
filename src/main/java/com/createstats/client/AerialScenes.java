package com.createstats.client;

import com.createstats.client.ponder.AerialStage;
import com.createstats.client.ponder.Ease;
import com.createstats.client.ponder.FlightRig;
import com.createstats.client.ponder.Labels;
import com.createstats.client.ponder.Machines;
import com.createstats.client.ponder.Marks;
import com.createstats.client.ponder.Motion;
import com.createstats.client.ponder.ShadowFade;
import com.createstats.vccmk4.air.core.AerialPonderChapters;
import com.createstats.vccmk4.air.core.AerialPonderFlight;
import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.AirLight;
import com.createstats.vccmk4.air.core.Telegraph;
import com.createstats.vccmk4.client.Vcc4Keys;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.Keymap;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.ParrotElement;
import net.createmod.ponder.api.element.ParrotPose;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AerialScenes {
    private AerialScenes() {
    }

    private static final class Clock {
        int now;

        void idle(CreateSceneBuilder scene, int ticks) {
            scene.idle(ticks);
            now += ticks;
        }

        void until(CreateSceneBuilder scene, int tick) {
            if (tick < now) {
                com.createstats.CreateStats.LOGGER.warn("[Ponder] VCC Mk IV Aerial cue {} is behind the scene clock {}", tick, now);
                return;
            }
            if (tick > now) idle(scene, tick - now);
        }
    }

    private static CreateSceneBuilder stage(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, AerialStage.PLATE);
        scene.scaleSceneView(0.6f);
        scene.setSceneOffsetY(-2f);
        return scene;
    }

    private static CreateSceneBuilder tallStage(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = stage(builder, id, title);
        scene.scaleSceneView(0.55f);
        scene.setSceneOffsetY(-3f);
        return scene;
    }

    private static BlockPos pos(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    private static Vec3 centre(int[] p) {
        return new Vec3(p[0] + 0.5, p[1] + 0.5, p[2] + 0.5);
    }

    private static Vec3 com() {
        return new Vec3(AerialStage.COM[0], AerialStage.COM[1], AerialStage.COM[2]);
    }

    private static Selection box(SceneBuildingUtil util, int x0, int y0, int z0, int x1, int y1, int z1) {
        return util.select().fromTo(x0, y0, z0, x1, y1, z1);
    }

    private static Selection at(SceneBuildingUtil util, int[]... points) {
        Selection s = util.select().position(pos(points[0]));
        for (int i = 1; i < points.length; i++) s = s.add(util.select().position(pos(points[i])));
        return s;
    }

    private static Selection plate(SceneBuildingUtil util) {
        return box(util, 0, 0, 0, AerialStage.PLATE - 1, 0, AerialStage.PLATE - 1);
    }

    private static Selection portWall(SceneBuildingUtil util) {
        int[] a = AerialStage.PORT_WALL_MIN;
        int[] b = AerialStage.PORT_WALL_MAX;
        return box(util, a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    @SuppressWarnings("unchecked")
    private static ElementLink<WorldSectionElement>[] buildShip(CreateSceneBuilder scene, SceneBuildingUtil util, Clock c,
                                                               FlightRig rig, int end, boolean wall) {
        ElementLink<WorldSectionElement> ship = scene.world().showIndependentSection(
                box(util, 2, 1, 4, 8, 2, 6).add(box(util, 9, 2, 4, 9, 2, 6)), Direction.DOWN);
        rig.ship(ship);
        scene.addInstruction(Motion.ship(rig, c.now, end - c.now));
        ElementLink<WorldSectionElement> port = null;
        c.idle(scene, 4);

        scene.world().showSectionAndMerge(box(util, 3, 3, 4, 3, 3, 6).add(at(util, AerialStage.ENGINE)), Direction.DOWN, ship);
        c.idle(scene, 3);
        scene.world().showSectionAndMerge(at(util, AerialStage.GOVERNOR_PORT, AerialStage.GOVERNOR_STARBOARD),
                Direction.DOWN, ship);
        c.idle(scene, 3);
        scene.world().showSectionAndMerge(at(util, AerialStage.BURNER_AFT, AerialStage.ASSEMBLER, AerialStage.BURNER_FORE),
                Direction.DOWN, ship);
        c.idle(scene, 3);

        scene.world().showSectionAndMerge(box(util, 4, 3, 4, 4, 4, 4).add(box(util, 4, 3, 6, 4, 4, 6))
                .add(box(util, 8, 3, 4, 8, 4, 4)).add(box(util, 8, 3, 6, 8, 4, 6))
                .add(box(util, 3, 4, 4, 3, 4, 4)).add(box(util, 3, 4, 6, 3, 4, 6)), Direction.DOWN, ship);
        c.idle(scene, 4);

        scene.world().showSectionAndMerge(box(util, 2, 5, 4, 10, 5, 6), Direction.DOWN, ship);
        c.idle(scene, 3);
        Selection belt = box(util, 2, 6, 3, 10, 6, 7);
        Selection upper = box(util, 2, 7, 3, 10, 7, 7);
        if (!wall) {
            belt = belt.substract(portWall(util));
            upper = upper.substract(portWall(util));
        }
        scene.world().showSectionAndMerge(belt, Direction.DOWN, ship);
        if (!wall) {
            port = scene.world().showIndependentSection(portWall(util), Direction.DOWN);
            scene.addInstruction(Motion.offsets(rig, port, rig::shipOffset, c.now, end - c.now));
        }
        c.idle(scene, 3);
        scene.world().showSectionAndMerge(upper, Direction.DOWN, ship);
        c.idle(scene, 3);
        scene.world().showSectionAndMerge(box(util, 3, 8, 4, 9, 8, 6), Direction.DOWN, ship);
        c.idle(scene, 4);

        scene.world().showSectionAndMerge(at(util, AerialStage.PROP_PORT, AerialStage.PROP_STARBOARD), Direction.EAST, ship);
        c.idle(scene, 3);
        scene.world().showSectionAndMerge(box(util, 0, 6, 5, 1, 8, 5), Direction.EAST, ship);
        c.idle(scene, 3);

        scene.world().showSectionAndMerge(at(util, AerialStage.LAMP_AMBER), Direction.DOWN, ship);
        c.idle(scene, 2);
        scene.world().showSectionAndMerge(at(util, AerialStage.LAMP_RED), Direction.SOUTH, ship);
        c.idle(scene, 2);
        scene.world().showSectionAndMerge(at(util, AerialStage.LAMP_WHITE), Direction.UP, ship);
        c.idle(scene, 3);
        scene.world().showSectionAndMerge(at(util, AerialStage.SEAT), Direction.DOWN, ship);
        c.idle(scene, 6);

        scene.world().showSectionAndMerge(at(util, AerialStage.AERIAL), Direction.DOWN, ship);
        return new ElementLink[] {ship, port};
    }

    private static List<BlockPos> burners() {
        return List.of(pos(AerialStage.BURNER_AFT), pos(AerialStage.BURNER_FORE));
    }

    private static List<Machines.Lamp> lamps() {
        return List.of(new Machines.Lamp(pos(AerialStage.LAMP_RED), AirLight.RED, AerialStage.redJob()),
                new Machines.Lamp(pos(AerialStage.LAMP_AMBER), AirLight.AMBER, AerialStage.amberJob()),
                new Machines.Lamp(pos(AerialStage.LAMP_WHITE), AirLight.WHITE, AerialStage.whiteJob()));
    }

    private static String key(Control control, boolean plus) {
        Keymap keys = AerialSetup.defaultKeys();
        String name = Vcc4Keys.name(plus ? keys.plus(control) : keys.minus(control));
        return name == null || name.isEmpty() ? "?" : name;
    }

    private static String hold(double y) {
        return I18n.get("gui.create_stats.vcc_mk4_aerial.side.hold", String.format(Locale.ROOT, "%.0f", y));
    }

    private static Vec3 seatTop() {
        return new Vec3(AerialStage.SEAT[0] + 0.5, AerialStage.SEAT[1] + 0.5, AerialStage.SEAT[2] + 0.5);
    }

    private static void faceBow(CreateSceneBuilder scene) {
        scene.special().movePointOfInterest(new Vec3(1000.0, 4.0, AerialStage.SEAT[2] + 0.5));
    }

    private static void seatedPilot(CreateSceneBuilder scene, FlightRig rig, Clock c, int end) {
        faceBow(scene);
        ElementLink<ParrotElement> bird = scene.special().createBirb(seatTop(), ParrotPose.FacePointOfInterestPose::new);
        scene.addInstruction(Motion.pilot(rig, bird, Vec3.ZERO, 0, c.now, end - c.now));
    }

    private static Labels.Anchor overHelm(FlightRig rig) {
        Vec3 rest = new Vec3(AerialStage.SEAT[0] + 0.5, AerialStage.SEAT[1] + 2.3, AerialStage.SEAT[2] + 0.5);
        return pt -> rig.onShip(rest, pt);
    }

    private static void heldMark(CreateSceneBuilder scene, FlightRig rig, int from, int to) {
        AerialPonderFlight.Recording flight = rig.flight();
        double z = AerialStage.SHIP_MIN[2] - 0.7;
        scene.addInstruction(Marks.dashes("aerial_held", t -> rig.sceneY(flight.held(t), t), -1.2, 12.4, z, Marks.HELD,
                from, to - from));
        Vec3 end = new Vec3(-1.2, 0.0, z);
        scene.addInstruction(Labels.tag(pt -> end.add(0.0, rig.sceneY(flight.held(rig.shown()), rig.shown()), 0.0),
                t -> hold(flight.held(t)), t -> Marks.HELD, 5, -5, from + 4, to - from - 4));
    }

    public static void firstFlight(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = stage(builder, "vcc_mk4_aerial_first_flight", "Flying a zeppelin from the helm");
        Clock c = new Clock();

        final int intro = AerialPonderChapters.FirstFlight.INTRO;
        final int place = AerialPonderChapters.FirstFlight.PLACE;
        final int scan = AerialPonderChapters.FirstFlight.SCAN;
        final int decide = AerialPonderChapters.FirstFlight.DECIDE;
        final int fly = AerialPonderChapters.FirstFlight.FLY;
        final int sit = AerialPonderChapters.FirstFlight.SIT;
        final int end = AerialPonderChapters.FirstFlight.END;
        AerialPonderFlight.Recording flight = AerialPonderFlight.fly(AerialPonderChapters.FirstFlight.script());
        FlightRig rig = new FlightRig(flight, com());
        int off = AerialPonderChapters.FirstFlight.off(flight);
        int parked = Math.min(end - 80, AerialPonderChapters.FirstFlight.parked(flight));

        scene.world().showSection(plate(util), Direction.UP);
        c.idle(scene, 10);
        ElementLink<WorldSectionElement> wall = buildShip(scene, util, c, rig, end, false)[1];
        scene.addInstruction(Machines.sparkle(t -> rig.shipOffset(t).add(centre(AerialStage.AERIAL)), c.now + 12));

        Vec3 aerial = centre(AerialStage.AERIAL);
        c.until(scene, intro);
        scene.overlay().showText(90).text("The VCC Mk IV Aerial flies a zeppelin for you, from its helm")
                .pointAt(aerial).colored(PonderPalette.BLUE).placeNearTarget().attachKeyFrame();
        c.idle(scene, 100);

        c.until(scene, place);
        Vec3 face = new Vec3(AerialStage.AERIAL[0] + 1.0, AerialStage.AERIAL[1] + 0.5, AerialStage.AERIAL[2] + 0.5);
        scene.addInstruction(Marks.arrow("aerial_facing", t -> new Vec3[] {face, face.add(2.6, 0.0, 0.0)}, 0xF2F2F2,
                1 / 14f, c.now, 90));
        c.idle(scene, 8);
        scene.overlay().showText(70).text("Build it onto the craft, facing the bow")
                .pointAt(face.add(1.3, 0.0, 0.0)).colored(PonderPalette.WHITE).placeNearTarget().attachKeyFrame();
        c.idle(scene, 80);

        c.until(scene, scan);
        int ring = 60;
        double reach = 9.4;
        scene.addInstruction(Marks.ring("aerial_scan", t -> aerial, reach, Ease.OUT_CUBIC, 0xE4B763, scan, ring));
        int chipsEnd = fly + 8;
        String lift = I18n.get(AerialStage.LIFT_KEY);
        String engine = I18n.get(AerialStage.key(AerialStage.engineJob()));
        String push = I18n.get(AerialStage.key(AerialSetup.EngineJob.PUSH));
        chip(scene, rig, AerialStage.LAMP_WHITE, -0.2, I18n.get(AerialStage.key(AerialStage.whiteJob())), 0xFFF6DC,
                scan, ring, reach, chipsEnd);
        chip(scene, rig, AerialStage.LAMP_RED, 0.9, I18n.get(AerialStage.key(AerialStage.redJob())), 0xFF4B3C,
                scan, ring, reach, chipsEnd);
        chip(scene, rig, AerialStage.BURNER_FORE, 1.2, lift, Marks.HELD, scan, ring, reach, chipsEnd);
        chip(scene, rig, AerialStage.LAMP_AMBER, 0.9, I18n.get(AerialStage.key(AerialStage.amberJob())), 0xFFAA00,
                scan, ring, reach, chipsEnd);
        chip(scene, rig, AerialStage.BURNER_AFT, 1.2, lift, Marks.HELD, scan, ring, reach, chipsEnd);
        chip(scene, rig, AerialStage.PROP_PORT, 1.0, engine, Marks.PUSH, scan, ring, reach, chipsEnd);

        Vec3 starRest = centre(AerialStage.PROP_STARBOARD).add(0.0, 1.0, 0.0);
        int starAt = crossing(aerial, starRest, scan, ring, reach);
        scene.addInstruction(Labels.chip(pt -> rig.onShip(starRest, pt),
                t -> t >= decide && t < decide + 44 ? push : engine, j -> Marks.PUSH, starAt, chipsEnd - starAt));
        chip(scene, rig, AerialStage.FIN_TIP, 1.1, I18n.get(AerialStage.key(AerialStage.finJob())), 0xE4B763,
                scan, ring, reach, chipsEnd);
        c.idle(scene, 8);
        scene.overlay().showText(80).text("It finds every burner, propeller, fin and lamp, and gives each a job")
                .pointAt(aerial).colored(PonderPalette.BLUE).placeNearTarget().attachKeyFrame();
        c.idle(scene, 90);

        c.until(scene, decide + 6);
        scene.overlay().showText(70).text("It proposes, you decide: every job can be changed on its console")
                .pointAt(starRest.add(0.0, 0.6, 0.0)).colored(PonderPalette.GREEN).placeNearTarget();
        c.idle(scene, 80);

        c.until(scene, fly);
        faceBow(scene);
        Vec3 from = new Vec3(-2.0, 2.5, -5.0);
        ElementLink<ParrotElement> bird = scene.special().createBirb(seatTop().add(from), ParrotPose.FlappyPose::new);
        scene.addInstruction(Motion.pilot(rig, bird, from, sit - fly, c.now, end - c.now));
        c.idle(scene, 8);
        scene.overlay().showControls(seatTop().add(0.0, 0.3, 0.0), Pointing.DOWN, 40).rightClick();
        scene.overlay().showText(60).text("Sit in a seat beside the Aerial to take the helm")
                .pointAt(seatTop()).colored(PonderPalette.INPUT).placeNearTarget().attachKeyFrame();
        c.until(scene, sit);
        scene.special().changeBirbPose(bird, ParrotPose.FacePointOfInterestPose::new);

        scene.addInstruction(Machines.burners(flight, burners(), c.now, end - c.now));
        scene.addInstruction(Machines.lamps(flight, lamps(), c.now, end - c.now));
        scene.addInstruction(Machines.shimmer(
                List.of(t -> rig.shipOffset(t).add(centre(AerialStage.BURNER_AFT).add(0, 1.0, 0)),
                        t -> rig.shipOffset(t).add(centre(AerialStage.BURNER_FORE).add(0, 1.0, 0))),
                List.of(t -> flight.signal(t, 0), t -> flight.signal(t, 1)), c.now, end - 40 - c.now));
        heldMark(scene, rig, c.now, end - 40);
        c.idle(scene, 8);

        scene.world().hideIndependentSection(wall, Direction.NORTH);
        Vec3 lo = new Vec3(AerialStage.INSIDE_MIN[0], AerialStage.INSIDE_MIN[1], AerialStage.INSIDE_MIN[2]);
        Vec3 hi = new Vec3(AerialStage.INSIDE_MAX[0], AerialStage.INSIDE_MAX[1], AerialStage.INSIDE_MAX[2]);
        int gasEnd = parked + 30;
        scene.addInstruction(Marks.gas("aerial_gas", lo, hi, rig::shipOffset, flight::fill, 0x70FF9E2C, c.now + 6,
                gasEnd - c.now - 6));
        c.until(scene, AerialPonderChapters.FirstFlight.GAS_CAPTION);
        scene.overlay().showText(90).text("Hot air gathers under the top of the envelope. Once there is enough, the ship lifts")
                .pointAt(new Vec3(6.0, 7.5, 3.0)).colored(PonderPalette.WHITE).placeNearTarget();
        c.idle(scene, 100);

        c.until(scene, Math.max(c.now, off + 20));
        Vec3 bow = centre(AerialStage.AERIAL).add(0.0, 0.8, 0.0);
        scene.overlay().showText(80).text("Seated on the ground, it lifts the ship three blocks and holds it there")
                .pointAt(bow.add(rig.shipOffset(c.now + 40))).colored(PonderPalette.GREEN).placeNearTarget()
                .attachKeyFrame();
        c.idle(scene, 90);

        c.until(scene, Math.max(c.now, parked));
        scene.addInstruction(Machines.sparkle(t -> rig.shipOffset(t).add(centre(AerialStage.AERIAL)), c.now));
        c.idle(scene, 16);
        ElementLink<WorldSectionElement> wallBack = scene.world().showIndependentSection(portWall(util), Direction.SOUTH);
        scene.addInstruction(Motion.offsets(rig, wallBack, rig::shipOffset, c.now, end - c.now));
        c.until(scene, end - 20);
        c.idle(scene, 20);
    }

    private static int crossing(Vec3 from, Vec3 point, int start, int ring, double reach) {
        double d = Math.hypot(point.x - from.x, point.z - from.z);
        double k = Math.max(0.0, Math.min(1.0, (d - 0.3) / (reach - 0.3)));
        double t = 1.0 - Math.cbrt(1.0 - k);
        return start + (int) Math.ceil(t * ring);
    }

    private static void chip(CreateSceneBuilder scene, FlightRig rig, int[] part, double up, String job, int rgb,
                             int scan, int ring, double reach, int until) {
        Vec3 rest = centre(part).add(0.0, up, 0.0);
        int at = crossing(centre(AerialStage.AERIAL), rest, scan, ring, reach);
        scene.addInstruction(Labels.chip(pt -> rig.onShip(rest, pt), t -> job, j -> rgb, at, until - at));
    }

    public static void altitude(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = tallStage(builder, "vcc_mk4_aerial_altitude", "Holding an altitude");
        Clock c = new Clock();

        final int raise = AerialPonderChapters.Altitude.RAISE;
        final int raiseFor = AerialPonderChapters.Altitude.RAISE_FOR;
        final int twinIn = AerialPonderChapters.Altitude.TWIN_IN;
        final int compare = AerialPonderChapters.Altitude.COMPARE;
        final int compareFor = AerialPonderChapters.Altitude.COMPARE_FOR;
        final int load = AerialPonderChapters.Altitude.LOAD;
        final int end = AerialPonderChapters.Altitude.END;
        AerialPonderFlight.Recording flight = AerialPonderFlight.fly(AerialPonderChapters.Altitude.script());
        FlightRig rig = new FlightRig(flight, com()).keepInFrame(1.5, 3.0);

        AerialPonderFlight.Recording lever = AerialPonderChapters.Altitude.twin(flight);
        int arrived = AerialPonderChapters.Altitude.arrived(flight);
        int past = AerialPonderChapters.Altitude.past(flight, lever);

        ElementLink<WorldSectionElement> ground = scene.world().showIndependentSection(plate(util), Direction.UP);
        scene.addInstruction(ShadowFade.fade(false, 60));
        ShadowFade.removeIfUnavailable(scene);
        c.idle(scene, 10);
        ElementLink<WorldSectionElement> ship = buildShip(scene, util, c, rig, end, true)[0];
        scene.addInstruction(Motion.world(rig, ground, false, c.now, end - c.now));
        scene.addInstruction(Machines.burners(flight, burners(), c.now, end - c.now));
        scene.addInstruction(Machines.lamps(flight, lamps(), c.now, end - c.now));
        c.idle(scene, 14);
        seatedPilot(scene, rig, c, end);
        c.idle(scene, 12);
        heldMark(scene, rig, c.now, end - 30);

        String up = key(Control.LIFT, true);
        String down = key(Control.LIFT, false);
        c.until(scene, raise - 20);
        scene.addInstruction(Labels.keycap(overHelm(rig), up, t -> t >= raise && t < raise + raiseFor, c.now,
                raise + raiseFor + 30 - c.now));
        c.until(scene, raise + 8);
        scene.overlay().showText(80).text("Hold %s to raise the height held, and %s to lower it", up, down)
                .pointAt(centre(AerialStage.SEAT).add(0.0, 4.0, 0.0)).colored(PonderPalette.INPUT).placeNearTarget()
                .attachKeyFrame();
        c.idle(scene, 90);

        int lookFrom = raise + 10;
        int lookTo = twinIn;
        Vec3 lookAt = new Vec3(12.0, 0.0, AerialStage.SHIP_MIN[2] - 0.7);
        scene.addInstruction(Marks.arrow("aerial_ahead", t -> {
            double now = rig.sceneY(flight.y(t), t);
            double ahead = rig.sceneY(flight.y(t) + flight.vy(t) * 1.5, t);
            return new Vec3[] {lookAt.add(0.0, now, 0.0), lookAt.add(0.0, ahead, 0.0)};
        }, 0xC8B85A, 1 / 18f, lookFrom, lookTo - lookFrom));
        scene.addInstruction(Labels.tag(pt -> lookAt.add(0.0, rig.sceneY(flight.y(rig.shown())
                        + flight.vy(rig.shown()) * 1.5, rig.shown()), 0.0), t -> I18n.get("create_stats.ponder.aerial.ahead"),
                t -> 0xC8B85A, -44, -5, lookFrom + 4, lookTo - lookFrom - 4));
        c.until(scene, raise + raiseFor + 50);
        scene.overlay().showText(90).text("It brakes before it arrives: gas is slow, so it plans that far ahead")
                .pointAt(lookAt.add(0.0, rig.sceneY(flight.held(c.now), c.now), 0.0)).colored(PonderPalette.GREEN)
                .placeNearTarget().attachKeyFrame();
        c.idle(scene, 100);

        c.until(scene, twinIn);
        ElementLink<WorldSectionElement> twin = scene.world().showIndependentSection(
                box(util, AerialStage.LEVER_MIN[0], AerialStage.LEVER_MIN[1], AerialStage.LEVER_MIN[2],
                        AerialStage.LEVER_MAX[0], AerialStage.LEVER_MAX[1], AerialStage.LEVER_MAX[2]), Direction.DOWN);

        double twinRest = flight.restY() + 3.0;
        scene.addInstruction(Motion.offsets(rig, twin, t -> {
            double y = t < compare ? flight.y(compare) : lever.y(t - compare);
            return new Vec3(0.0, y - twinRest - rig.frame(t), 0.0);
        }, c.now, end - c.now));
        scene.addInstruction(Machines.leverBalloon(lever, pos(AerialStage.LEVER), pos(AerialStage.LEVER_BURNER), compare,
                c.now, end - c.now));
        c.until(scene, compare);
        scene.addInstruction(Labels.keycap(overHelm(rig), up, t -> t >= compare && t < compare + compareFor, c.now,
                compareFor + 20));
        c.until(scene, Math.max(c.now, past + 40));
        scene.overlay().showText(80).text("Flown by a lever, a balloon sails past the mark")
                .pointAt(centre(AerialStage.LEVER_BURNER).add(0.0, 3.0 + lever.y(c.now - compare) - twinRest - rig.frame(c.now), 0.0))
                .colored(PonderPalette.RED).placeNearTarget().attachKeyFrame();
        c.idle(scene, 90);
        c.until(scene, Math.max(c.now, arrived + 10));
        scene.overlay().showText(70).text("The Aerial parks on the mark")
                .pointAt(centre(AerialStage.AERIAL).add(0.0, 1.0, 0.0).add(rig.shipOffset(c.now)))
                .colored(PonderPalette.GREEN).placeNearTarget();
        c.idle(scene, 80);
        scene.world().hideIndependentSection(twin, Direction.UP);

        c.until(scene, load - 16);
        for (int[] crate : AerialStage.CARGO) {
            scene.world().showSectionAndMerge(at(util, crate), Direction.DOWN, ship);
            c.idle(scene, 4);
        }
        c.until(scene, load + 30);
        scene.overlay().showText(90).text("Load it and the burners answer at once: it weighs the ship every tick")
                .pointAt(centre(AerialStage.CARGO[0]).add(rig.shipOffset(c.now))).colored(PonderPalette.GREEN)
                .placeNearTarget().attachKeyFrame();
        c.idle(scene, 100);
        c.until(scene, end - 20);
        c.idle(scene, 20);
    }

    private static final double PROP_RPM = 64.0;

    public static void telegraph(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = stage(builder, "vcc_mk4_aerial_telegraph", "Ringing the engine telegraph");
        Clock c = new Clock();

        final int slow = AerialPonderChapters.Telegraph.SLOW;
        final int half = AerialPonderChapters.Telegraph.HALF;
        final int full = AerialPonderChapters.Telegraph.FULL;
        final int stop = AerialPonderChapters.Telegraph.STOP;
        final int astern = AerialPonderChapters.Telegraph.ASTERN;
        final int rest = AerialPonderChapters.Telegraph.REST;
        final int end = AerialPonderChapters.Telegraph.END;
        AerialPonderFlight.Recording flight = AerialPonderFlight.fly(AerialPonderChapters.Telegraph.script());
        FlightRig rig = new FlightRig(flight, com());

        ElementLink<WorldSectionElement> ground = scene.world().showIndependentSection(plate(util), Direction.UP);
        ShadowFade.removeIfUnavailable(scene);
        c.idle(scene, 10);
        buildShip(scene, util, c, rig, end, true);
        scene.addInstruction(Machines.burners(flight, burners(), c.now, end - c.now));
        scene.addInstruction(Machines.lamps(flight, lamps(), c.now, end - c.now));
        c.idle(scene, 14);
        seatedPilot(scene, rig, c, end);
        c.idle(scene, 12);

        List<Selection> chunks = new ArrayList<>();
        for (int i = 0; i < AerialStage.CHUNKS; i++) {
            int x0 = AerialStage.STRIP_X0 + i * AerialStage.CHUNK;
            chunks.add(box(util, x0, 0, 0, x0 + AerialStage.CHUNK - 1, 10, AerialStage.PLATE - 1));
        }
        String ahead = key(Control.THROTTLE, true);
        String back = key(Control.THROTTLE, false);
        c.until(scene, slow - 24);
        scene.addInstruction(Labels.keycap(overHelm(rig), ahead,
                t -> (t >= slow && t < slow + 2) || (t >= half && t < half + 2) || (t >= full && t < full + 2), c.now,
                full + 40 - c.now));
        c.until(scene, slow - 12);
        scene.addInstruction(Motion.treadmill(rig, ground, AerialStage.PLATE, chunks, AerialStage.STRIP_X0,
                AerialStage.CHUNK, -12, 34, c.now, end - c.now));
        scene.addInstruction(ShadowFade.fade(false, c.now));

        List<BlockPos> props = List.of(pos(AerialStage.PROP_PORT), pos(AerialStage.PROP_STARBOARD));
        List<BlockPos> governors = List.of(pos(AerialStage.GOVERNOR_PORT), pos(AerialStage.GOVERNOR_STARBOARD));
        scene.addInstruction(Machines.props(props, governors, t -> {
            double rpm = -PROP_RPM * flight.push(t);
            return new double[] {rpm, rpm};
        }, c.now, end - c.now));
        for (int[] prop : new int[][] {AerialStage.PROP_PORT, AerialStage.PROP_STARBOARD}) {
            Vec3 disc = centre(prop).add(-0.45, 0.0, 0.0);
            scene.addInstruction(Marks.airflow("aerial_wake_" + prop[2], t -> rig.shipOffset(t).add(disc),
                    t -> new Vec3(-flight.push(t), 0.0, 0.0), new Vec3(0, 1, 0), new Vec3(0, 0, 1), 0.42, 7,
                    c.now, end - c.now));
            double side = prop[2] < AerialStage.COM[2] ? -0.75 : 0.75;
            Vec3 tail = centre(prop).add(0.3, 0.0, side);
            scene.addInstruction(Marks.arrow("aerial_push_" + prop[2], t -> {
                Vec3 from = rig.shipOffset(t).add(tail);
                return new Vec3[] {from, from.add(3.0 * flight.push(t), 0.0, 0.0)};
            }, Marks.PUSH, 1 / 16f, slow + 6, end - slow - 36));
        }
        Vec3 helm = centre(AerialStage.AERIAL);
        scene.addInstruction(Machines.bell(flight, t -> rig.shipOffset(t).add(helm), c.now, end - c.now));

        Vec3 orderAt = centre(AerialStage.AERIAL).add(0.6, 1.2, -0.5);
        scene.addInstruction(Labels.tag(pt -> rig.onShip(orderAt, pt), t -> order(flight.order(t)), t -> 0xE4B763, 6, -16,
                slow - 4, end - slow - 20));
        scene.addInstruction(Labels.tag(pt -> rig.onShip(orderAt, pt), t -> answer(flight.engine(t)), t -> Marks.PUSH, 6, -5,
                slow - 4, end - slow - 20));

        c.until(scene, slow + 8);
        scene.overlay().showText(80).text("Tap %s to ring the next order ahead, %s to ring astern", ahead, back)
                .pointAt(centre(AerialStage.SEAT).add(0.0, 4.0, 0.0)).colored(PonderPalette.INPUT).placeNearTarget()
                .attachKeyFrame();
        c.idle(scene, 90);

        c.until(scene, half + 8);
        scene.overlay().showText(80).text("Seven orders, from Full Astern through Stop to Full Ahead")
                .pointAt(orderAt).colored(PonderPalette.WHITE).placeNearTarget();
        c.idle(scene, 90);
        scene.overlay().showText(80).text("Each order is a share of engine power: Half is half what they can push")
                .pointAt(centre(AerialStage.PROP_PORT).add(1.5, 0.0, -0.75)).colored(PonderPalette.GREEN)
                .placeNearTarget().attachKeyFrame();
        c.idle(scene, 90);

        c.until(scene, full + 6);
        scene.overlay().showText(80).text("The engines answer in about a second, like an engine room")
                .pointAt(orderAt).colored(PonderPalette.WHITE).placeNearTarget();
        c.idle(scene, 90);

        c.until(scene, stop - 24);
        scene.addInstruction(Labels.keycap(overHelm(rig), back,
                t -> (t >= stop && t < stop + 2) || (t >= stop + 10 && t < stop + 12) || (t >= stop + 20 && t < stop + 22)
                        || (t >= astern && t < astern + 2), c.now, astern + 40 - c.now));
        c.until(scene, stop + 30);
        scene.overlay().showText(90).text("Stop does not coast: it brakes the ship to rest and holds it")
                .pointAt(centre(AerialStage.PROP_PORT).add(0.0, 1.0, 0.0)).colored(PonderPalette.GREEN)
                .placeNearTarget().attachKeyFrame();
        c.idle(scene, 100);

        c.until(scene, astern + 10);
        scene.overlay().showText(70).text("Astern backs the ship up")
                .pointAt(centre(AerialStage.PROP_PORT).add(-1.0, 0.0, 0.0)).colored(PonderPalette.WHITE)
                .placeNearTarget().attachKeyFrame();
        c.idle(scene, 80);

        c.until(scene, rest - 24);
        scene.addInstruction(Labels.keycap(overHelm(rig), ahead, t -> t >= rest && t < rest + 2, c.now, 60));
        c.until(scene, end - 20);
        c.idle(scene, 20);
    }

    private static String order(double position) {
        Telegraph t = new Telegraph();
        t.set(position);
        return t.nearest().label;
    }

    private static String answer(double engine) {
        return I18n.get("create_stats.ponder.aerial.engines", Math.round(Math.abs(engine) * 100.0));
    }
}
