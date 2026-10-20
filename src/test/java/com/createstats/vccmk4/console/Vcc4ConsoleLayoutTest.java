package com.createstats.vccmk4.console;

import com.createstats.surveyor.core.RouteRunner;
import com.createstats.vccmk4.core.AxleRole;
import com.createstats.vccmk4.core.Channel;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetMixer;
import com.createstats.vccmk4.core.JetRole;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.KeyIds;
import com.createstats.vccmk4.core.Keymap;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.TrailerMode;
import com.createstats.vccmk4.network.RouteSnapshot;
import com.createstats.vccmk4.network.Vcc4Snapshot;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Vcc4ConsoleLayoutTest {
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Path FONT = Path.of("assets_ascii.png");
    private static final Path OUT = Path.of("build/vcc4-console");

    private static final int[][] SCREENS = {{464, 280}, {544, 334}, {853, 533}};

    @Test
    void thePanelGrowsWithTheScreenAndStopsAtItsLimits() {
        assertTrue(Vcc4Console.width(853) == Vcc4Console.MAX_W, "the owner's 2560x1600 at scale 3 gets the largest panel");
        assertTrue(Vcc4Console.height(533) == Vcc4Console.MAX_H);
        assertTrue(Vcc4Console.width(300) == Vcc4Console.MIN_W, "never narrower than the layout was checked at");
        assertTrue(Vcc4Console.width(544) > Vcc4Console.MIN_W && Vcc4Console.width(544) < Vcc4Console.MAX_W);
    }

    private interface Setup {
        void accept(Vcc4Console console);
    }

    private record Scenario(String name, Vcc4Snapshot snapshot, Setup setup, int[] mouse) {
        int mouseX(int[] screen) {
            return mouse == null ? -1 : screen[0] * mouse[0] / 100;
        }

        int mouseY(int[] screen) {
            return mouse == null ? -1 : screen[1] * mouse[1] / 100;
        }
    }

    private static List<Scenario> scenarios() {
        List<Scenario> out = new ArrayList<>();
        out.add(new Scenario("car", car(), c -> { }, null));
        out.add(new Scenario("car_hover", car(), c -> { }, new int[]{30, 45}));
        out.add(new Scenario("truck6", truck(), c -> { }, null));
        out.add(new Scenario("long16", longHauler(), c -> { }, null));
        out.add(new Scenario("hover", hovercraft(), c -> { }, null));
        out.add(new Scenario("tank", tank(), c -> { }, null));
        out.add(new Scenario("forklift", forklift(), c -> { }, null));
        out.add(new Scenario("crane", crane(), c -> { }, null));
        out.add(new Scenario("articulated", articulated(), c -> { }, null));
        out.add(new Scenario("roadtrain", roadTrain(), c -> { }, null));
        out.add(new Scenario("towing", towing(), c -> { }, null));
        out.add(new Scenario("towing_hover", towing(), c -> { }, new int[]{70, 40}));
        out.add(new Scenario("towing_parked", towingParked(), c -> { }, null));
        out.add(new Scenario("towing_lost", towingLost(), c -> { }, null));
        out.add(new Scenario("thrusters", thrusters(), c -> { }, null));
        out.add(new Scenario("thrusters_hover", thrusters(), c -> { }, new int[]{62, 42}));
        out.add(new Scenario("rebound", rebound(), c -> c.pick(Control.LIFT, Keymap.MINUS), null));
        out.add(new Scenario("menu", car(), c -> c.openJetMenu(200, 120, 1003L, JetRole.AUTO.ordinal()), null));
        out.add(new Scenario("lamps", lamps(true), c -> { }, null));
        out.add(new Scenario("lamps_bound", lamps(false), c -> { }, new int[]{40, 30}));
        out.add(new Scenario("empty", Vcc4Snapshot.EMPTY, c -> { }, null));
        out.add(new Scenario("nothing", nothing(), c -> { }, null));

        out.add(new Scenario("route_running", withRoute(car(), false, new RouteData()), c -> { }, null));
        out.add(new Scenario("route_waiting", withRoute(car(), false, RouteData.waiting()), c -> { }, null));
        out.add(new Scenario("route_schedule", withRoute(car(), false, RouteData.scheduled()), c -> { }, null));
        out.add(new Scenario("route_waiting_hover", withRoute(car(), false, RouteData.waiting()), c -> { }, new int[]{40, 42}));
        out.add(new Scenario("route_trouble", withRoute(car(), false, RouteData.trouble()), c -> { }, null));
        out.add(new Scenario("route_manual", withRoute(car(), true, RouteData.manual()), c -> { }, null));
        out.add(new Scenario("route_choices", withRoute(car(), false, RouteData.choices(14)), c -> { }, null));
        out.add(new Scenario("route_none", withRoute(car(), false, RouteData.choices(0)), c -> { }, null));
        out.add(new Scenario("route_recording", withRoute(car(), true, RouteData.recording(true)), c -> { }, null));
        out.add(new Scenario("route_armed", withRoute(car(), false, RouteData.recording(false)), c -> { }, null));
        return out;
    }

    static Vcc4Snapshot withRoute(Vcc4Snapshot vehicle, boolean seated, RouteData r) {
        Vcc4Snapshot.Builder b = new Vcc4Snapshot.Builder(vehicle);
        if (!seated) {
            b.driver = "";
            b.youDrive = false;
        }
        b.console = RouteData.CONSOLE;
        b.route = r.build();
        return b.build();
    }

    static Vcc4Snapshot carForRoutes() {
        return car();
    }

    static final class RouteData {
        static final UUID CONSOLE = new UUID(0x4DL, 0x4L);
        static final UUID QUARRY = new UUID(0x77L, 0x1L);
        boolean assigned = true;
        String name = "Quarry loop";
        int colour = 3;
        int state = RouteRunner.State.RUNNING.ordinal();
        String reason = "";
        float progress = 43f;
        float length = 214f;
        boolean closed = true;
        int nextStop = 7;
        String nextName = "Crusher";
        float eta = 42f;
        String rules = "wait 10 s";
        float wait = -1f;
        String waitText = "";
        int laps = 3;
        float lastLap = 131f;
        boolean recording;
        boolean armed;
        int points;
        boolean learned = true;
        float cruise;
        int comfort = -1;
        boolean startOnAssign = true;
        boolean horn = true;
        float[] stops = {20f, 96f, 180f};
        float planned = 6f;
        float pathwayCruise = 6f;
        List<RouteSnapshot.Choice> choices = List.of(new RouteSnapshot.Choice(QUARRY, "Quarry loop", 3, 12),
                new RouteSnapshot.Choice(new UUID(0x77L, 0x2L), "Farm run", 5, 6));

        String schedule;

        static RouteData scheduled() {
            RouteData r = waiting();
            r.schedule = "Ore run, 2 of 4: waiting at Quarry face: cargo 620 > 1000 items";
            return r;
        }

        static RouteData waiting() {
            RouteData r = new RouteData();
            r.state = RouteRunner.State.WAITING.ordinal();
            r.nextName = "Quarry face";
            r.rules = "cargo > 1000 items of cobblestone and cargo idle 20 s or wait 2 min, leave after 10 min";
            r.wait = 0.62f;
            r.waitText = "cargo 620 > 1000 items, idle 12 of 20 s, wait 34 of 120 s";
            return r;
        }

        static RouteData trouble() {
            RouteData r = new RouteData();
            r.name = "Northern ore line to the smelter";
            r.state = RouteRunner.State.BLOCKED.ordinal();
            r.reason = "a player (Steve_the_builder_of_long_names) at 1204 64 -3090";
            r.nextName = "The very long named stop of all";
            r.eta = 3725f;
            r.rules = "cargo < 64 stacks of minecraft:deepslate_diamond_ore or fluid > 1000 buckets of water, leave after 30 min";
            r.laps = 1204;
            r.lastLap = 5405f;
            r.learned = false;
            r.cruise = 12.5f;
            r.comfort = 2;
            r.horn = false;
            r.stops = new float[]{0f, 10f, 11f, 12f, 100f, 213f};
            r.progress = 214f;
            r.closed = false;
            List<RouteSnapshot.Choice> many = new ArrayList<>();
            for (int i = 0; i < 20; i++) many.add(new RouteSnapshot.Choice(new UUID(0x88L, i), "Route " + i + " to the far field", i % 16, 4 + i));
            r.choices = many;
            return r;
        }

        static RouteData manual() {
            RouteData r = new RouteData();
            r.state = RouteRunner.State.MANUAL.ordinal();
            return r;
        }

        static RouteData choices(int n) {
            RouteData r = new RouteData();
            r.assigned = false;
            r.state = RouteRunner.State.PAUSED.ordinal();
            List<RouteSnapshot.Choice> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                list.add(new RouteSnapshot.Choice(new UUID(0x88L, i), i == 3 ? "Northern ore line to the smelter" : "Route " + i, i % 16, 4 + i * 7));
            }
            r.choices = list;
            return r;
        }

        static RouteData recording(boolean running) {
            RouteData r = choices(3);
            r.state = RouteRunner.State.RECORDING.ordinal();
            r.recording = running;
            r.armed = !running;
            r.points = running ? 143 : 0;
            return r;
        }

        RouteSnapshot build() {
            return new RouteSnapshot(assigned, assigned ? QUARRY : new UUID(0L, 0L), assigned ? name : "", colour, state, reason, progress,
                    length, closed, assigned ? nextStop : -1, assigned ? nextName : "", eta, rules, wait, waitText, laps, lastLap, recording,
                    armed, points, learned, cruise, comfort, startOnAssign, horn, assigned ? stops : new float[0], planned,
                    pathwayCruise, choices, assigned && schedule != null, schedule == null ? "" : schedule);
        }
    }

    private static int[] keymap(java.util.function.Consumer<Keymap> edit) {
        Keymap k = Keymap.defaults();
        edit.accept(k);
        return k.toInts();
    }

    private static float[] values(Map<Control, Float> v) {
        float[] out = new float[Control.values().length];
        v.forEach((c, f) -> out[c.ordinal()] = f);
        return out;
    }

    private static Vcc4Snapshot.Builder base() {
        Vcc4Snapshot.Builder b = new Vcc4Snapshot.Builder();
        b.onCraft = true;
        b.keymap = Keymap.defaults().toInts();
        b.controlValues = new float[Control.values().length];
        b.comMeasured = true;
        return b;
    }

    private static Vcc4Snapshot.Wheel wheel(long pos, float lat, float lon, int axle, float angle, int governor, float rpm) {
        return new Vcc4Snapshot.Wheel(pos, lat, lon, axle, angle, true, governor, rpm, 0);
    }

    private static Vcc4Snapshot.Swivel swivel(long pos, float lat, float lon, PartInput input, float angle, int travel,
                                              boolean latched, boolean ready) {
        return new Vcc4Snapshot.Swivel(pos, lat, lon, input.ordinal(), angle, travel, latched, false, ready, 0, -1, ready, ready);
    }

    private static Vcc4Snapshot.Machine machine(long pos, float lat, float lon, PartInput input, int rpm, boolean latched,
                                                float output, String... drives) {
        return new Vcc4Snapshot.Machine(pos, lat, lon, 0, input.ordinal(), rpm, latched, false, output, Math.round(output * rpm),
                false, latched, List.of(drives));
    }

    private static Vcc4Snapshot.Link link(long pos, float lat, float lon, LinkSource source, boolean toggle, int strength,
                                          String first, String second) {
        return new Vcc4Snapshot.Link(pos, lat, lon, 0, source.ordinal(), toggle, strength, first, second);
    }

    private static Vcc4Snapshot lamps(boolean greeting) {
        Vcc4Snapshot.Builder b = new Vcc4Snapshot.Builder(car());
        String white = "block.create_stats.vehicle_light_white";
        String red = "block.create_stats.vehicle_light_red";
        String amber = "block.create_stats.vehicle_light_amber";
        b.links = List.of(
                new Vcc4Snapshot.Link(3001, -1.5f, 3f, 0, LinkSource.HEADLIGHT.ordinal(), true, 8, white, "", false, 1, true),
                new Vcc4Snapshot.Link(3002, 1.5f, 3f, 0, LinkSource.HEADLIGHT.ordinal(), true, 15, white, "", false, 1, true),
                new Vcc4Snapshot.Link(3003, 0f, -3.5f, 0, LinkSource.REVERSE.ordinal(), false, 0, white, "", false, 1, true),
                new Vcc4Snapshot.Link(3004, -1.5f, -3.5f, 0, LinkSource.TAIL_BRAKE.ordinal(), false, 6, red, "", false, 2, true),
                new Vcc4Snapshot.Link(3005, 1.5f, -3.5f, 0, LinkSource.TAIL_BRAKE.ordinal(), false, 15, red, "", false, 2, false),
                new Vcc4Snapshot.Link(3006, -1.5f, 3.5f, 0, LinkSource.LEFT.ordinal(), false, 15, amber, "", false, 3, true),
                new Vcc4Snapshot.Link(3007, 1.5f, 3.5f, 0, LinkSource.RIGHT.ordinal(), false, 0, amber, "", false, 3, true),
                link(3008, 0f, 1f, LinkSource.AUX_B_PLUS, false, 0, "item.minecraft.redstone", "item.minecraft.note_block"));
        b.greeting = greeting;
        if (!greeting) b.keymap = keymap(k -> k.setPlus(Control.BEAM, KeyIds.key(66)));
        return b.build();
    }

    private static Vcc4Snapshot car() {
        Vcc4Snapshot.Builder b = base();
        b.wheels = List.of(
                wheel(1, -1.5f, 1.5f, 0, -18f, 0, 64f),
                wheel(2, 1.5f, 1.5f, 0, -14f, 1, 64f),
                wheel(3, -1.5f, -2.5f, 1, 0f, 0, 64f),
                wheel(4, 1.5f, -2.5f, 1, 0f, 1, 64f));
        b.governors = List.of(
                new Vcc4Snapshot.Governor(10, -1f, -1f, 64, 64, false, false, false, 2, 0, 1f),
                new Vcc4Snapshot.Governor(11, 1f, -1f, -64, -64, false, false, false, 2, 0, -1f),
                new Vcc4Snapshot.Governor(12, 0f, -3f, 256, 256, false, false, false, 0, 2, 0f),
                new Vcc4Snapshot.Governor(13, 0f, 3f, 128, 128, false, false, false, 0, 1, 0f),
                new Vcc4Snapshot.Governor(14, 0f, 0f, 0, 0, false, false, false, 0, 1, 0f));
        b.jets = List.of(
                new Vcc4Snapshot.Jet(1001, -1f, -3.5f, 0f, 0f, 1f, -1f, -3f, JetRole.AUTO.ordinal(), JetMixer.Job.PUSH.ordinal(), 2, 180f, 0f, false, true, false, 0),
                new Vcc4Snapshot.Jet(1002, 1f, -3.5f, 0f, 0f, 1f, 1f, -3f, JetRole.AUTO.ordinal(), JetMixer.Job.PUSH.ordinal(), 2, 180f, 1f, false, true, false, 0),
                new Vcc4Snapshot.Jet(1003, 0f, 3f, -1f, 0f, 0f, 0f, 3.5f, JetRole.AUTO.ordinal(), JetMixer.Job.TURN.ordinal(), 3, 256f, 1f, false, true, false, 0),
                new Vcc4Snapshot.Jet(1004, 0f, 0.5f, 0f, 1f, 0f, 0f, 1f, JetRole.AUTO.ordinal(), JetMixer.Job.LIFT.ordinal(), 4, 0f, 0f, false, false, false, 0));
        b.swivels = List.of(swivel(2001, 0f, -1.5f, PartInput.AUX_A, 22f, 45, true, true));
        b.youDrive = true;
        b.driver = "Bolat";
        b.nose = 2;
        b.throttle = 0.4f;
        b.steer = -0.8f;
        b.boosting = true;
        b.gear = 'D';
        b.speed = 9.3f;
        b.preset = SteerPreset.FRONT.ordinal();
        b.presetAuto = true;
        b.roles = new int[]{AxleRole.STEER.ordinal(), AxleRole.FIXED.ordinal()};
        b.driven = new boolean[]{true, true};
        b.axleZ = new float[]{1.5f, -2.5f};
        b.comLongitudinal = -0.5f;
        b.held = new int[]{Channel.SPRINT.id(), Channel.LEFT.id()};
        b.controlValues = values(Map.of(Control.THROTTLE, 0.4f, Control.STEER, -0.8f, Control.BOOST, 1f));
        b.problems = List.of("J4 waits for Lift, which has no key yet. Bind one on the Keys page.");
        return b.build();
    }

    private static Vcc4Snapshot thrusters() {
        Vcc4Snapshot.Builder b = new Vcc4Snapshot.Builder(car());
        int auto = JetRole.AUTO.ordinal();
        int push = JetMixer.Job.PUSH.ordinal();
        int thruster = JetKind.THRUSTER.ordinal();
        b.jets = List.of(
                new Vcc4Snapshot.Jet(1001, -1f, -3.5f, 0f, 0f, 1f, -1f, -3f, auto, push, -1, 0f, 0.75f, false, true, false, 0,
                        thruster, 400f, 533f, 1f, JetSupply.OK.ordinal(), 0f, 0f, false),
                new Vcc4Snapshot.Jet(1002, 1f, -3.5f, 0f, 0f, 1f, 1f, -3f, auto, push, -1, 0f, 0f, false, true, false, 0,
                        thruster, 0f, 533f, 0.6f, JetSupply.LOW.ordinal(), 0f, 0f, false),
                new Vcc4Snapshot.Jet(1003, 0f, -4f, 0f, 0f, 1f, 0f, -3.5f, auto, push, -1, 0f, 0.75f, false, true, false, 0,
                        JetKind.VECTOR.ordinal(), 550f, 733f, 1f, JetSupply.POWERED.ordinal(), -0.4f, 0f, false),
                new Vcc4Snapshot.Jet(1004, 2f, 2.5f, -1f, 0f, 0f, 2f, 3f, auto, JetMixer.Job.TURN.ordinal(), -1, 0f, 1f, false, true, false, 0,
                        JetKind.BOOSTER.ordinal(), 200f, 200f, 1f, JetSupply.LOW.ordinal(), 0f, 0f, false),
                new Vcc4Snapshot.Jet(1005, 0f, 3.5f, 0f, 0f, -1f, 0f, 4f, auto, push, -1, 0f, 0f, false, true, false, 0,
                        thruster, 0f, 533f, 1f, JetSupply.EMPTY.ordinal(), 0f, 0f, false),
                new Vcc4Snapshot.Jet(1006, -1.5f, 2.5f, 0f, 0f, 1f, -1.5f, 3f, auto, push, -1, 0f, 0f, false, true, false, 0,
                        thruster, 0f, 533f, 1f, JetSupply.OK.ordinal(), 0f, 0f, true),
                new Vcc4Snapshot.Jet(1007, -2f, -3f, 0f, 0f, 1f, -2f, -2.5f, auto, push, -1, 0f, 0.5f, false, true, false, 0,
                        thruster, 0f, 533f, 1f, JetSupply.OK.ordinal(), 0f, 0f, false),
                new Vcc4Snapshot.Jet(1008, 0f, -9.5f, 0f, 0f, 1f, 0f, -9f, auto, push, -1, 0f, 0.56f, false, true, false, 1,
                        thruster, 300f, 533f, 1f, JetSupply.OK.ordinal(), 0f, 0f, false),
                new Vcc4Snapshot.Jet(1009, 1.5f, 0.5f, 0f, 1f, 0f, 1.5f, 1f, JetRole.OFF.ordinal(), -1, -1, 0f, 0f, false, true, false, 0,
                        thruster, 0f, 533f, 1f, JetSupply.OK.ordinal(), 0f, 0f, false),
                new Vcc4Snapshot.Jet(1010, 0f, 0.5f, 0f, 1f, 0f, 0f, 1f, auto, JetMixer.Job.LIFT.ordinal(), 4, 0f, 0f, false, false, false, 0));
        List<Vcc4Snapshot.Wheel> wheels = new ArrayList<>(b.wheels);
        wheels.add(new Vcc4Snapshot.Wheel(1020, -1.2f, -8f, -1, 0f, true, -1, 0f, 1));
        wheels.add(new Vcc4Snapshot.Wheel(1021, 1.2f, -8f, -1, 0f, true, -1, 0f, 1));
        b.wheels = wheels;
        b.bodies = List.of(new Vcc4Snapshot.Body(1, 0, 0f, -5f, false, false, true, 2, 3f, Vcc4Snapshot.ROLE_TRAILER,
                LinkKind.HITCH.ordinal(), TrailerMode.ASSIST.ordinal(), 301L, 3f, 120f, 0f, 0, -1f, 0.9f, Vcc4Snapshot.FLAG_POWERED));
        b.problems = List.of(
                "J2's exhaust is blocked behind its nozzle, so it makes at most 60% of its thrust: clear the blocks behind it.",
                "J5 has no fuel it can burn, so it cannot fire: pipe in a thruster fuel, or drain a wrong one.",
                "J6 is held by another console, so this one cannot fire it.",
                "J1's exhaust runs through B2, about 4 block(s) behind it, where someone is riding: above a third throttle the plume burns them. Move the seat out of line, or aim J1 clear of B2.",
                "J10 waits for Lift, which has no key yet. Bind one on the Keys page.");
        return b.build();
    }

    private static Vcc4Snapshot truck() {
        Vcc4Snapshot.Builder b = base();
        List<Vcc4Snapshot.Wheel> wheels = new ArrayList<>();
        float[] z = {3f, 0f, -3f};
        for (int a = 0; a < 3; a++) {
            wheels.add(wheel(10 + a * 2, -1.5f, z[a], a, a < 2 ? 12f - a * 6 : 0f, a, 0f));
            wheels.add(wheel(11 + a * 2, 1.5f, z[a], a, a < 2 ? 9f - a * 4 : 0f, a, 0f));
        }
        b.wheels = wheels;
        b.governors = List.of(
                new Vcc4Snapshot.Governor(30, 0f, 3.5f, 0, 0, false, false, false, 2, 0, 1f),
                new Vcc4Snapshot.Governor(31, 0f, 0.5f, 0, 0, true, false, false, 2, 0, -2.5f),
                new Vcc4Snapshot.Governor(32, 0f, -2.5f, 0, 0, false, true, false, 2, 0, 0f));
        b.swivels = List.of(
                swivel(40, 0f, -1.5f, PartInput.AUX_A, 30f, 60, true, true),
                swivel(41, 0f, -4f, PartInput.AUX_B, 0f, 45, false, false));
        b.parked = true;
        b.braking = true;
        b.gear = 'P';
        b.preset = SteerPreset.TWIN_FRONT.ordinal();
        b.roles = new int[]{0, 0, 1};
        b.driven = new boolean[]{true, true, true};
        b.axleZ = z;
        b.problems = List.of("S2 is unlocked (redstone or its value panel), so it cannot hold an angle. Lock it, or set it Off to let it swing free.");
        return b.build();
    }

    private static Vcc4Snapshot longHauler() {
        Vcc4Snapshot.Builder b = base();
        List<Vcc4Snapshot.Wheel> wheels = new ArrayList<>();
        float[] z = new float[8];
        int[] roles = new int[8];
        boolean[] driven = new boolean[8];
        for (int a = 0; a < 8; a++) {
            z[a] = 10.5f - a * 3f;
            roles[a] = AxleRole.STEER.ordinal();
            driven[a] = a % 3 != 1;
            wheels.add(wheel(100 + a * 2, -2f, z[a], a, 20f - a * 5, a, 48f));
            wheels.add(wheel(101 + a * 2, 2f, z[a], a, 16f - a * 4, a, 48f));
        }
        List<Vcc4Snapshot.Governor> govs = new ArrayList<>();
        for (int g = 0; g < 8; g++) govs.add(new Vcc4Snapshot.Governor(200 + g, g % 2 == 0 ? -0.5f : 0.5f, z[g], 48, 48, false, false, g == 5, 2, 0, 1f));
        List<Vcc4Snapshot.Jet> jets = new ArrayList<>();
        for (int j = 0; j < 4; j++) {
            jets.add(new Vcc4Snapshot.Jet(300 + j, j % 2 == 0 ? -2.5f : 2.5f, -12f + j, 0f, 0f, -1f, j % 2 == 0 ? -2.5f : 2.5f,
                    -12f + j, j == 3 ? JetRole.AUX_C.ordinal() : JetRole.AUTO.ordinal(), j == 3 ? -1 : JetMixer.Job.PUSH.ordinal(),
                    j == 2 ? -1 : 0, 0f, 0f, j == 1, false, true, 0));
        }
        List<Vcc4Snapshot.Swivel> swivels = new ArrayList<>();
        PartInput[] inputs = {PartInput.AUX_A, PartInput.AUX_B, PartInput.AUX_C, PartInput.STEER};
        for (int sv = 0; sv < 4; sv++) {
            swivels.add(new Vcc4Snapshot.Swivel(400 + sv, 0f, 6f - sv * 4f, inputs[sv].ordinal(), -40f + sv * 20, 90, sv == 0, sv == 2,
                    true, 0, -1, true, true));
        }
        List<String> problems = new ArrayList<>();
        for (int p = 0; p < 10; p++) problems.add("Problem number " + (p + 1) + ": something about governor G" + (p + 1) + " and its shaft line that is long enough to wrap onto a second line.");
        b.wheels = wheels;
        b.governors = govs;
        b.jets = jets;
        b.swivels = swivels;
        b.driver = "Notch_the_Builder";
        b.nose = 1;
        b.throttle = 0.2f;
        b.steer = 0.1f;
        b.braking = true;
        b.gear = 'B';
        b.speed = 22.4f;
        b.preset = SteerPreset.ALL.ordinal();
        b.roles = roles;
        b.driven = driven;
        b.axleZ = z;
        b.comLongitudinal = -1f;
        b.comMeasured = false;
        b.options = new DriveModel.Options(true, true, 12, 24, 200, 70, 10, 60, false, true);
        b.problems = problems;
        b.brakesAvailable = false;
        return b.build();
    }

    private static Vcc4Snapshot hovercraft() {
        Vcc4Snapshot.Builder b = base();
        b.governors = List.of(
                new Vcc4Snapshot.Governor(50, -1f, -2f, 200, 200, false, false, false, 0, 1, 0f),
                new Vcc4Snapshot.Governor(51, 1f, -2f, -200, -200, false, false, false, 0, 1, 0f),
                new Vcc4Snapshot.Governor(52, 0f, 2f, 0, 0, false, false, false, 0, 3, 0f));
        b.jets = List.of(
                new Vcc4Snapshot.Jet(60, -1.5f, -3f, 0f, 0f, 1f, -1.5f, -3f, 0, 0, 0, 200f, 1f, false, true, false, 0),
                new Vcc4Snapshot.Jet(61, 1.5f, -3f, 0f, 0f, 1f, 1.5f, -3f, 0, 0, 1, -200f, -1f, false, true, false, 0),
                new Vcc4Snapshot.Jet(62, -1f, 3f, 1f, 0f, 0f, -1f, 3f, 0, 1, 2, 0f, 0f, false, true, false, 0),
                new Vcc4Snapshot.Jet(63, 1f, 3f, 1f, 0f, 0f, 1f, 3f, 0, 1, 2, 0f, 0f, false, true, false, 0),
                new Vcc4Snapshot.Jet(64, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0, JetMixer.Job.LIFT.ordinal(), 2, 120f, 0.6f, false, true, false, 0));
        b.driver = "Bolat";
        b.nose = 3;
        b.steer = -1f;
        b.gear = 'N';
        b.speed = 3.1f;
        b.jetDrive = true;
        b.liftHold = true;
        b.liftHoldAuto = true;
        b.controlValues = values(Map.of(Control.LIFT, 0.6f));
        return b.build();
    }

    private static Vcc4Snapshot tank() {
        Vcc4Snapshot.Builder b = base();
        List<Vcc4Snapshot.Wheel> wheels = new ArrayList<>();
        float[] z = {4f, 2f, 0f, -2f, -4f};
        int[] roles = new int[5];
        boolean[] driven = new boolean[5];
        for (int a = 0; a < 5; a++) {
            roles[a] = AxleRole.FIXED.ordinal();
            driven[a] = true;
            wheels.add(wheel(500 + a * 2, -2.5f, z[a], a, 0f, 0, 96f));
            wheels.add(wheel(501 + a * 2, 2.5f, z[a], a, 0f, 1, -96f));
        }
        b.wheels = wheels;
        b.governors = List.of(
                new Vcc4Snapshot.Governor(520, -1.5f, -5f, 96, 96, false, false, false, 5, 0, 1f),
                new Vcc4Snapshot.Governor(521, 1.5f, -5f, -96, -96, false, false, false, 5, 0, -1f));
        b.machines = List.of(
                machine(530, 0f, 0.5f, PartInput.STEER, 32, false, 0f, "block.createbigcannons.cannon_mount"),
                machine(531, 0f, -1.5f, PartInput.AUX_B, 64, false, 0f, "block.createbigcannons.cannon_mount"));
        b.links = List.of(
                link(540, 0f, 3f, LinkSource.AUX_C_PLUS, false, 0, "item.minecraft.gunpowder", "item.minecraft.tnt"),
                link(541, -2f, -5f, LinkSource.BRAKE, false, 15, "item.minecraft.redstone", "item.minecraft.red_dye"),
                link(542, 2f, -5f, LinkSource.BRAKE, false, 15, "item.minecraft.redstone", "item.minecraft.red_dye"));
        b.driver = "Bolat";
        b.youDrive = true;
        b.steer = 0.6f;
        b.gear = 'H';
        b.holding = true;
        b.braking = true;
        b.preset = SteerPreset.SKID.ordinal();
        b.roles = roles;
        b.driven = driven;
        b.axleZ = z;
        b.keymap = keymap(k -> k.setPlus(Control.AUX_C, KeyIds.key(70)));
        return b.build();
    }

    private static Vcc4Snapshot forklift() {
        Vcc4Snapshot.Builder b = base();
        b.wheels = List.of(
                wheel(600, -1f, 1f, 0, 0f, 0, 32f),
                wheel(601, 1f, 1f, 0, 0f, 0, 32f),
                wheel(602, -1f, -1.5f, 1, 22f, -1, 0f),
                wheel(603, 1f, -1.5f, 1, 18f, -1, 0f));
        b.governors = List.of(new Vcc4Snapshot.Governor(610, 0f, 0.5f, 32, 32, false, false, false, 2, 0, 1f));
        b.swivels = List.of(swivel(620, 0f, 2f, PartInput.AUX_B, -8f, 15, true, true));
        b.machines = List.of(machine(630, 0f, 1.5f, PartInput.AUX_A, 48, false, 1f, "block.create.mechanical_piston"));
        b.links = List.of(
                link(640, -1f, -2f, LinkSource.BRAKE, false, 0, "item.minecraft.redstone", "item.minecraft.red_dye"),
                link(641, 1f, -2f, LinkSource.REVERSE, false, 15, "item.minecraft.redstone", "item.minecraft.white_dye"),
                link(642, -1f, 2.2f, LinkSource.LEFT, false, 15, "item.minecraft.glowstone_dust", "item.minecraft.orange_dye"),
                link(643, 1f, 2.2f, LinkSource.RIGHT, false, 0, "item.minecraft.glowstone_dust", "item.minecraft.yellow_dye"),
                link(644, 0f, 2.4f, LinkSource.AUX_D_PLUS, true, 15, "Headlights of the forklift", ""));
        b.driver = "Bolat";
        b.youDrive = true;
        b.throttle = -0.5f;
        b.steer = -0.7f;
        b.gear = 'R';
        b.speed = -1.2f;
        b.preset = SteerPreset.REAR.ordinal();
        b.roles = new int[]{AxleRole.FIXED.ordinal(), AxleRole.STEER.ordinal()};
        b.driven = new boolean[]{true, true};
        b.axleZ = new float[]{1f, -1.5f};
        b.keymap = keymap(k -> k.setPlus(Control.AUX_D, KeyIds.key(72)));
        b.controlValues = values(Map.of(Control.AUX_A, 1f));
        b.held = new int[]{Channel.AUX1.id()};
        return b.build();
    }

    private static Vcc4Snapshot crane() {
        Vcc4Snapshot.Builder b = base();
        List<Vcc4Snapshot.Wheel> wheels = new ArrayList<>();
        float[] z = {4f, 1f, -2f, -5f};
        for (int a = 0; a < 4; a++) {
            wheels.add(wheel(700 + a * 2, -1.5f, z[a], a, a < 2 ? 10f - a * 4 : 0f, a < 2 ? 0 : 1, 64f));
            wheels.add(wheel(701 + a * 2, 1.5f, z[a], a, a < 2 ? 8f - a * 3 : 0f, a < 2 ? 0 : 1, 64f));
        }
        b.wheels = wheels;
        b.governors = List.of(
                new Vcc4Snapshot.Governor(720, 0f, 2.5f, 64, 64, false, false, false, 4, 0, 1f),
                new Vcc4Snapshot.Governor(721, 0f, -3.5f, 64, 64, false, false, false, 4, 0, 1f));
        b.swivels = List.of(
                swivel(730, 0f, -1f, PartInput.AUX_A, 75f, 180, true, true),
                swivel(731, 0f, -2f, PartInput.AUX_B, 30f, 60, true, true),
                swivel(732, 0f, -3f, PartInput.OFF, 0f, 45, true, false));
        b.machines = List.of(
                machine(740, 0f, -4f, PartInput.AUX_C, 96, false, -1f, "block.simulated.rope_winch"),
                machine(741, -1.5f, -6f, PartInput.AUX_D, 32, false, 0f, "block.create.mechanical_piston", "block.create.gantry_carriage", "block.create.sticker"),
                machine(742, 1.5f, -6f, PartInput.OFF, 128, true, 0f, "block.create.mechanical_drill"));
        b.links = List.of(link(750, 0f, 5f, LinkSource.AUX_D_MINUS, false, 0, "item.minecraft.note_block", "item.minecraft.bell"));
        b.gear = 'P';
        b.parked = true;
        b.braking = true;
        b.preset = SteerPreset.TWIN_FRONT.ordinal();
        b.presetAuto = true;
        b.roles = new int[]{0, 0, 1, 1};
        b.driven = new boolean[]{true, true, true, true};
        b.axleZ = z;
        b.problems = List.of("M2 waits for Aux D, which has no key yet. Bind one on the Keys page.");
        return b.build();
    }

    private static Vcc4Snapshot articulated() {
        Vcc4Snapshot.Builder b = base();
        b.wheels = List.of(
                wheel(800, -1.5f, 1.5f, 0, 0f, 0, 48f),
                wheel(801, 1.5f, 1.5f, 0, 0f, 0, 48f),
                new Vcc4Snapshot.Wheel(802, -2.2f, -3.2f, -1, 0f, true, 1, 48f, 1),
                new Vcc4Snapshot.Wheel(803, 0.8f, -3.8f, -1, 0f, true, 1, 48f, 1));
        b.governors = List.of(
                new Vcc4Snapshot.Governor(810, 0f, 0.5f, 48, 48, false, false, false, 2, 0, 1f),
                new Vcc4Snapshot.Governor(811, -0.6f, -2.5f, 48, 48, false, false, false, 2, 0, 1f));
        b.swivels = List.of(new Vcc4Snapshot.Swivel(820, 0f, -0.5f, PartInput.STEER.ordinal(), -14f, 45, false, false, true, 0, 1,
                true, true));
        b.machines = List.of(machine(830, 0f, 2.5f, PartInput.AUX_A, 64, false, 0f, "block.create.mechanical_bearing"));
        b.bodies = List.of(new Vcc4Snapshot.Body(1, 0, 0f, -0.5f, true, false, true, 2, -14f));
        b.driver = "Bolat";
        b.throttle = 0.6f;
        b.steer = -0.5f;
        b.gear = 'D';
        b.speed = 4.1f;
        b.preset = SteerPreset.NONE.ordinal();
        b.presetAuto = true;
        b.roles = new int[]{AxleRole.FIXED.ordinal()};
        b.driven = new boolean[]{true};
        b.axleZ = new float[]{1.5f};
        return b.build();
    }

    private static Vcc4Snapshot roadTrain() {
        Vcc4Snapshot.Builder b = base();
        List<Vcc4Snapshot.Wheel> wheels = new ArrayList<>();
        float[] z = {3f, -1f, -2.5f};
        for (int a = 0; a < 3; a++) {
            wheels.add(wheel(900 + a * 2, -1.5f, z[a], a, a == 0 ? 6f : 0f, a == 0 ? -1 : 0, 80f));
            wheels.add(wheel(901 + a * 2, 1.5f, z[a], a, a == 0 ? 5f : 0f, a == 0 ? -1 : 0, 80f));
        }
        for (int t = 0; t < 4; t++) {
            wheels.add(new Vcc4Snapshot.Wheel(950 + t, t % 2 == 0 ? -1.8f : 1.2f, -9f - (t / 2) * 1.5f, -1, 0f, true, -1, 0f, 1));
        }
        b.wheels = wheels;
        b.governors = List.of(new Vcc4Snapshot.Governor(910, 0f, -1.8f, 80, 80, false, false, false, 4, 0, 2f));
        b.swivels = List.of(new Vcc4Snapshot.Swivel(920, 0f, -3.5f, PartInput.OFF.ordinal(), 0f, 45, false, false, false, 0, 1,
                true, false));
        b.links = List.of(
                new Vcc4Snapshot.Link(960, -1.8f, -11f, 1, LinkSource.BRAKE.ordinal(), false, 0, "item.minecraft.redstone", "item.minecraft.red_dye"),
                new Vcc4Snapshot.Link(961, 1.2f, -11f, 1, LinkSource.BRAKE.ordinal(), false, 0, "item.minecraft.redstone", "item.minecraft.red_dye"));
        b.bodies = List.of(
                new Vcc4Snapshot.Body(1, 0, 0f, -3.5f, false, false, true, 4, 12f, Vcc4Snapshot.ROLE_TRAILER,
                        LinkKind.HITCH.ordinal(), TrailerMode.ASSIST.ordinal(), 101L, 12f, 0f, 0f, 0, -3f, 1f, 0),
                new Vcc4Snapshot.Body(2, 1, 0f, -12f, false, true, false, 0, 0f, Vcc4Snapshot.ROLE_SEPARATE,
                        LinkKind.ROPE.ordinal(), TrailerMode.ASSIST.ordinal(), 102L, Float.NaN, 0f, 0f, 0, 0f, -1f, 0));
        b.driver = "Bolat";
        b.gear = 'N';
        b.speed = 12f;
        b.preset = SteerPreset.FRONT.ordinal();
        b.roles = new int[]{0, 1, 1};
        b.driven = new boolean[]{false, true, true};
        b.axleZ = z;
        return b.build();
    }

    private static Vcc4Snapshot towing() {
        Vcc4Snapshot.Builder b = base();
        List<Vcc4Snapshot.Wheel> wheels = new ArrayList<>();
        wheels.add(wheel(1000, -1.5f, 2f, 0, 8f, 0, 64f));
        wheels.add(wheel(1001, 1.5f, 2f, 0, 6f, 0, 64f));
        wheels.add(wheel(1002, -1.5f, -2f, 1, 0f, 0, 64f));
        wheels.add(wheel(1003, 1.5f, -2f, 1, 0f, 0, 64f));
        for (int t = 0; t < 4; t++) {
            wheels.add(new Vcc4Snapshot.Wheel(1010 + t, t % 2 == 0 ? -1.5f : 1.5f, -9f - (t / 2) * 2f, -1, -4f, true, 1, 60f, 1));
        }
        wheels.add(new Vcc4Snapshot.Wheel(1020, -1.2f, -16f, -1, 0f, true, -1, 0f, 2));
        wheels.add(new Vcc4Snapshot.Wheel(1021, 1.2f, -16f, -1, 0f, true, -1, 0f, 2));
        wheels.add(new Vcc4Snapshot.Wheel(1030, 2.5f, -2f, -1, 2f, true, -1, 0f, 3));
        wheels.add(new Vcc4Snapshot.Wheel(1031, 2.5f, -4f, -1, 2f, true, -1, 0f, 3));
        for (int t = 0; t < 4; t++) {
            wheels.add(new Vcc4Snapshot.Wheel(1040 + t, t % 2 == 0 ? -1.4f : 1.4f, -22f - (t / 2) * 2.5f, -1, 0f, false, 2, 0f, 4));
        }
        b.wheels = wheels;
        b.governors = List.of(
                new Vcc4Snapshot.Governor(1100, 0f, 0f, 64, 64, false, false, false, 4, 0, 1f),
                new Vcc4Snapshot.Governor(1101, 0f, -10f, 58, 58, false, false, false, 4, 0, 1f),
                new Vcc4Snapshot.Governor(1102, 0f, -23f, 0, 0, false, false, false, 4, 0, 0f));
        int powered = Vcc4Snapshot.FLAG_POWERED;
        b.bodies = List.of(
                new Vcc4Snapshot.Body(1, 0, 0f, -4f, false, false, true, 4, -5f, Vcc4Snapshot.ROLE_TRAILER, LinkKind.ROPE.ordinal(),
                        TrailerMode.ASSIST.ordinal(), 201L, -0.4f, 310f, 58f, 0, -4f, 0.82f, powered),
                new Vcc4Snapshot.Body(2, 1, 0f, -13f, false, false, true, 2, 8f, Vcc4Snapshot.ROLE_TRAILER, LinkKind.SPRING.ordinal(),
                        TrailerMode.MIRROR.ordinal(), 202L, 0.6f, 43.5f, 32f, 0, 0f, -1f, Vcc4Snapshot.FLAG_SNAPPING),
                new Vcc4Snapshot.Body(3, 0, 1.5f, -3f, false, false, true, 2, 0f, Vcc4Snapshot.ROLE_TRAILER, LinkKind.DOCK.ordinal(),
                        TrailerMode.ASSIST.ordinal(), 203L, 0f, 0f, 20f, 3, 2f, 1f, powered | Vcc4Snapshot.FLAG_DOCKING),
                new Vcc4Snapshot.Body(4, 2, 0f, -20f, false, true, true, 4, 3f, Vcc4Snapshot.ROLE_TOWED, LinkKind.ROPE.ordinal(),
                        TrailerMode.OFF.ordinal(), 204L, -1.8f, 0f, 0f, 0, 0f, -1f, 0),
                new Vcc4Snapshot.Body(5, 3, 4.5f, -6f, false, false, true, 0, 0f, Vcc4Snapshot.ROLE_LOAD, LinkKind.ROPE.ordinal(),
                        TrailerMode.ASSIST.ordinal(), 205L, Float.NaN, 0f, 0f, 0, 0f, -1f, 0),
                new Vcc4Snapshot.Body(6, 0, -3f, 1f, true, false, true, 0, 0f));
        b.ties = List.of(
                new Vcc4Snapshot.Tie(LinkKind.ROPE.ordinal(), 0, 0f, -3f, 1, 0f, -7.5f, -0.4f),
                new Vcc4Snapshot.Tie(LinkKind.SPRING.ordinal(), 1, 0f, -12f, 2, 0f, -14.5f, 0.6f),
                new Vcc4Snapshot.Tie(LinkKind.DOCK.ordinal(), 0, 1.5f, -3f, 3, 2.5f, -3f, Float.NaN),
                new Vcc4Snapshot.Tie(LinkKind.ROPE.ordinal(), 2, 0f, -17f, 4, 0f, -20.5f, -1.8f),
                new Vcc4Snapshot.Tie(LinkKind.ROPE.ordinal(), 3, 3f, -4f, 5, 4.5f, -6f, Float.NaN),
                new Vcc4Snapshot.Tie(LinkKind.ROPE.ordinal(), 0, -1f, -3f, -1, -6f, -9f, Float.NaN));
        b.links = List.of(
                new Vcc4Snapshot.Link(1200, 1.5f, -2.5f, 0, LinkSource.AUX_B_PLUS.ordinal(), true, 15, "item.minecraft.redstone",
                        "item.minecraft.chain", true),
                new Vcc4Snapshot.Link(1201, 0f, -12f, 1, LinkSource.BRAKE.ordinal(), false, 0, "item.minecraft.redstone",
                        "item.minecraft.red_dye"));
        b.driver = "Bolat";
        b.youDrive = true;
        b.gear = 'D';
        b.speed = 7.5f;
        b.throttle = 0.6f;
        b.steer = 0.3f;
        b.preset = SteerPreset.FRONT.ordinal();
        b.roles = new int[]{AxleRole.STEER.ordinal(), AxleRole.FIXED.ordinal()};
        b.driven = new boolean[]{true, true};
        b.axleZ = new float[]{2f, -2f};
        b.problems = List.of(
                "B5 is Off on a rope: nothing brakes it, so it runs into you when you brake. Assist brakes it first.",
                "B3's spring is near its breaking stretch: ease off, or it snaps and drops.",
                "A rope ties this vehicle to the ground: it holds the vehicle back until it is cut, unhooked or unpowered.");
        return b.build();
    }

    private static Vcc4Snapshot towingLost() {
        Vcc4Snapshot.Builder b = new Vcc4Snapshot.Builder(towing());
        List<Vcc4Snapshot.Body> bodies = new ArrayList<>(b.bodies);
        Vcc4Snapshot.Body first = bodies.get(0);
        bodies.set(0, new Vcc4Snapshot.Body(first.index(), first.parent(), first.lateral(), first.longitudinal(), first.locked(),
                first.foreign(), first.placed(), first.wheels(), first.yaw(), first.role(), first.link(), first.mode(), first.idLow(),
                Float.NaN, 0f, 0f, 15, 0f, -1f, first.flags() | Vcc4Snapshot.FLAG_LOST));
        b.bodies = bodies;
        b.problems = List.of("B2's rope has gone: its brakes are on until the console looks again.");
        return b.build();
    }

    private static Vcc4Snapshot towingParked() {
        Vcc4Snapshot.Builder b = new Vcc4Snapshot.Builder(towing());
        b.driver = "";
        b.youDrive = false;
        b.gear = 'P';
        b.parked = true;
        b.braking = true;
        b.speed = 0f;
        b.throttle = 0f;
        b.steer = 0f;
        List<Vcc4Snapshot.Body> idle = new ArrayList<>();
        for (Vcc4Snapshot.Body body : b.bodies) {
            idle.add(new Vcc4Snapshot.Body(body.index(), body.parent(), body.lateral(), body.longitudinal(), body.locked(),
                    body.foreign(), body.placed(), body.wheels(), body.yaw(), body.role(), body.link(), body.mode(), body.idLow(),
                    body.reading(), body.force(), 0f, 15, 0f, -1f, body.flags() & ~Vcc4Snapshot.FLAG_SNAPPING));
        }
        b.bodies = idle;
        b.problems = List.of();
        return b.build();
    }

    private static Vcc4Snapshot rebound() {
        Vcc4Snapshot.Builder b = new Vcc4Snapshot.Builder(car());
        b.keymap = keymap(k -> {
            k.setPlus(Control.LIFT, KeyIds.key(82));
            k.setMinus(Control.LIFT, KeyIds.key(70));
            k.setPlus(Control.AUX_C, KeyIds.key(328));
            k.setMinus(Control.AUX_C, KeyIds.key(322));
            k.setPlus(Control.AUX_D, KeyIds.mouse(4));
            k.setPlus(Control.SLIDE, KeyIds.key(263));
            k.setMinus(Control.SLIDE, KeyIds.key(262));
            k.setPlus(Control.BRAKE, KeyIds.key(335));
            k.setPlus(Control.AUX_B, Channel.SPRINT.id());
        });
        b.youDrive = false;
        b.driver = "";
        b.throttle = 0f;
        b.steer = 0f;
        b.boosting = false;
        b.speed = 0f;
        b.held = new int[0];
        b.controlValues = new float[Control.values().length];
        b.jetsSteerWithoutBoost = true;
        return b.build();
    }

    private static Vcc4Snapshot nothing() {
        Vcc4Snapshot.Builder b = base();
        b.comMeasured = false;
        b.problems = List.of("No wheel mounts or propellers found on this craft. Offroad wheel mounts with tyres, or propellers, are what this block drives.");
        return b.build();
    }

    static final class TestHost implements ConsoleHost {
        private static final String GRID = "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\b\t\n\u000b\f\r\u000e\u000f"
                + "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f"
                + " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u007f";

        private final Map<Character, Integer> advance = new HashMap<>();
        private final Map<String, String> lang = new HashMap<>();

        TestHost() throws IOException {
            BufferedImage sheet = ImageIO.read(FONT.toFile());
            int cell = sheet.getWidth() / 16;
            for (int i = 0; i < GRID.length(); i++) {
                int cx = (i % 16) * cell;
                int cy = (i / 16) * cell;
                int right = -1;
                for (int x = 0; x < cell; x++) {
                    for (int y = 0; y < cell; y++) {
                        if ((sheet.getRGB(cx + x, cy + y) >>> 24) != 0) {
                            right = Math.max(right, x);
                            break;
                        }
                    }
                }
                advance.put(GRID.charAt(i), right < 0 ? 0 : right + 2);
            }
            advance.put(' ', 4);
            JsonObject json = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : json.entrySet()) lang.put(e.getKey(), e.getValue().getAsString());
        }

        @Override
        public int width(String text) {
            int w = 0;
            for (int i = 0; i < text.length(); i++) w += advance.getOrDefault(text.charAt(i), 6);
            return w;
        }

        @Override
        public String tr(String key, Object... args) {
            String pattern = lang.get(key);
            if (pattern == null) throw new AssertionError("missing lang key " + key);
            try {
                return String.format(Locale.ROOT, pattern, args);
            } catch (IllegalFormatException e) {
                throw new AssertionError("bad format in " + key + ": " + pattern, e);
            }
        }

        @Override
        public String name(String idOrName) {
            if (idOrName == null || idOrName.isEmpty()) return "";
            if (!idOrName.startsWith("block.") && !idOrName.startsWith("item.")) return idOrName;
            String last = idOrName.substring(idOrName.lastIndexOf('.') + 1);
            StringBuilder out = new StringBuilder();
            for (String word : last.split("_")) {
                if (word.isEmpty()) continue;
                if (!out.isEmpty()) out.append(' ');
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return out.toString();
        }

        @Override
        public String keyName(int keyId) {
            if (keyId == KeyIds.NONE) return "";
            if (KeyIds.isBinding(keyId)) {
                return switch (Channel.byId(keyId)) {
                    case FORWARD -> "W";
                    case BACK -> "S";
                    case LEFT -> "A";
                    case RIGHT -> "D";
                    case JUMP -> "Space";
                    case SPRINT -> "Left Control";
                    case AUX1 -> "Z";
                    case AUX2 -> "X";
                    case AUX3 -> "C";
                    case NONE -> "";
                };
            }
            if (KeyIds.isMouse(keyId)) return "Button " + (KeyIds.mouseButton(keyId) + 1);
            int code = KeyIds.glfwKey(keyId);
            if (code >= 65 && code <= 90) return String.valueOf((char) code);
            return switch (code) {
                case 262 -> "Right Arrow";
                case 263 -> "Left Arrow";
                case 322 -> "Keypad 2";
                case 328 -> "Keypad 8";
                case 335 -> "Keypad Enter";
                default -> "Key " + code;
            };
        }

        @Override
        public List<String> keyAlso(int keyId) {
            return keyId == KeyIds.key(70) ? List.of("Swap Item With Offhand") : List.of();
        }

        @Override
        public boolean keyLeavesSeat(int keyId) {
            return keyId == KeyIds.key(340);
        }

        @Override
        public int resolveKey(int glfwKey, int scanCode) {
            return KeyIds.key(glfwKey);
        }

        @Override
        public int resolveMouse(int button) {
            return KeyIds.mouse(button);
        }

        @Override
        public void send(int action, int a, int b, long target) {
        }

        @Override
        public void click() {
        }
    }

    static final class Recorder implements ConsoleCanvas {
        private record Op(char kind, int layer, int[] box, float[] quad, String text, int colour) {
        }

        private final TestHost host;
        private final List<Op> ops = new ArrayList<>();
        private int layer;

        Recorder(TestHost host) {
            this.host = host;
        }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            ops.add(new Op('f', layer, new int[]{x0, y0, x1, y1}, null, null, argb));
        }

        @Override
        public void text(String text, int x, int y, int argb) {
            if (text == null || text.isEmpty()) return;
            ops.add(new Op('t', layer, new int[]{x, y, x + host.width(text) - 1, y + 8}, null, text, argb));
        }

        @Override
        public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
            ops.add(new Op('q', layer, null, new float[]{x0, y0, x1, y1, x2, y2, x3, y3}, null, argb));
        }

        @Override
        public void layer() {
            layer++;
        }

        int textCount() {
            int n = 0;
            for (Op o : ops) if (o.kind() == 't') n++;
            return n;
        }

        int[] where(String text) {
            for (Op o : ops) if (o.kind() == 't' && o.text().equals(text)) return new int[]{o.box()[0], o.box()[1]};
            return null;
        }

        List<int[]> everywhere(String text) {
            List<int[]> out = new ArrayList<>();
            for (Op o : ops) if (o.kind() == 't' && o.text().equals(text)) out.add(new int[]{o.box()[0], o.box()[1]});
            return out;
        }

        List<String> texts() {
            List<String> out = new ArrayList<>();
            for (Op o : ops) if (o.kind() == 't') out.add(o.text());
            return out;
        }

        List<String> faults(String id, int left, int top, int w, int h) {
            List<String> out = new ArrayList<>();
            List<Op> texts = ops.stream().filter(o -> o.kind() == 't').toList();
            for (Op t : texts) {
                int[] b = t.box();
                if (b[0] < left + 2 || b[1] < top + 2 || b[2] > left + w - 2 || b[3] > top + h - 1) {
                    out.add(id + ": \"" + t.text() + "\" at " + b[0] + "," + b[1] + " leaves the panel");
                }
            }
            for (int i = 0; i < texts.size(); i++) {
                for (int j = i + 1; j < texts.size(); j++) {
                    Op a = texts.get(i);
                    Op b = texts.get(j);
                    if (a.layer() != b.layer()) continue;
                    int[] p = a.box();
                    int[] q = b.box();
                    if (p[0] < q[2] && q[0] < p[2] && p[1] < q[3] && q[1] < p[3]) {
                        out.add(id + ": \"" + a.text() + "\" at " + p[0] + "," + p[1] + " overlaps \"" + b.text() + "\" at " + q[0] + "," + q[1]);
                    }
                }
            }
            return out;
        }

        String json(int[] screen, int left, int top, int w, int h, List<String> tooltip, int mx, int my) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"screen\":[").append(screen[0]).append(',').append(screen[1]).append("],");
            sb.append("\"panel\":[").append(left).append(',').append(top).append(',').append(w).append(',').append(h).append("],");
            sb.append("\"mouse\":[").append(mx).append(',').append(my).append("],\"tooltip\":[");
            for (int i = 0; i < tooltip.size(); i++) sb.append(i == 0 ? "" : ",").append(quote(tooltip.get(i)));
            sb.append("],\"ops\":[");
            for (int i = 0; i < ops.size(); i++) {
                Op o = ops.get(i);
                sb.append(i == 0 ? "" : ",\n");
                String colour = String.format(Locale.ROOT, "\"#%08X\"", o.colour());
                switch (o.kind()) {
                    case 'f' -> sb.append("[\"f\",").append(o.box()[0]).append(',').append(o.box()[1]).append(',')
                            .append(o.box()[2]).append(',').append(o.box()[3]).append(',').append(colour).append(',').append(o.layer()).append(']');
                    case 't' -> sb.append("[\"t\",").append(quote(o.text())).append(',').append(o.box()[0]).append(',')
                            .append(o.box()[1]).append(',').append(colour).append(',').append(o.layer()).append(']');
                    default -> {
                        sb.append("[\"q\",[");
                        for (int k = 0; k < 8; k++) sb.append(k == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.2f", o.quad()[k]));
                        sb.append("],").append(colour).append(',').append(o.layer()).append(']');
                    }
                }
            }
            sb.append("]}");
            return sb.toString();
        }

        private static String quote(String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
