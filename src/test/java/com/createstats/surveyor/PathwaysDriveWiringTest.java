package com.createstats.surveyor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PathwaysDriveWiringTest {
    private static final Path SRC = Path.of("src/main/java/com/createstats");

    static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    static String read(String rel) throws IOException {
        return read(SRC.resolve(rel));
    }

    static String method(String s, String header) {
        int at = s.indexOf(header);
        if (at < 0) return "";
        int open = s.indexOf('{', at);
        int depth = 0;
        for (int i = open; i >= 0 && i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return s.substring(open, i + 1);
        }
        return "";
    }

    static List<String> problems(String mixins, String yawMixin, String steerMixin, String be, String train, String route) {
        List<String> out = new ArrayList<>();
        if (!mixins.contains("\"WheelMountYawMixin\"")) out.add("the yaw mixin is not in create_stats.mixins.json");
        if (!yawMixin.contains("method = \"computeYaw()D\"") || !yawMixin.contains("@At(\"RETURN\")")) {
            out.add("the yaw mixin no longer answers computeYaw at its return");
        }
        if (!yawMixin.contains("Vcc4YawOverrides.forTick(")) out.add("the yaw mixin does not read the console's angle");
        String steerBody = method(steerMixin, "private void createStats$injectFccSteeringSignal");
        if (!steerBody.contains(".sendData()")) out.add("the steering override never sends the block to clients");

        String wheels = method(be, "private void driveWheels(");
        if (!wheels.contains("Vcc4YawOverrides.setForTick(w.pos(), c.angleDeg(), now)")) out.add("driveWheels does not send the exact angle");
        if (!wheels.contains("int brakeSignal = out.brakeSignal();")) out.add("driveWheels reads the brake as all or nothing");
        String tick = method(be, "private void driveTick(");
        if (!tick.contains("out = route.output(setup.options, forwardSpeed);")) out.add("a route drives through the player's ramps");
        if (!tick.contains("drive.follow(out);")) out.add("the player's model does not follow the route (a lurch at takeover)");
        String serverTick = method(be, "public void serverTick(");
        if (!serverTick.contains("route.afterDrive(")) out.add("the route's recorder is never fed");

        String assist = method(train, "void tick(ServerLevel server, long now, DriveModel.Output out");
        if (!assist.contains("out.brakeSignal(), host.setup().options.maxRpm())")) out.add("Assist trailers get the brake as all or nothing");
        String accel = method(train, "private double tractorAcceleration(");
        if (!accel.contains("double brake = out.brakeSignal() / 15.0;")) out.add("the tractor's own acceleration ignores the brake level");
        String trailerWheel = method(train, "void driveWheel(");
        if (!trailerWheel.contains("Vcc4YawOverrides.setForTick(w.pos(), angle, now)")) out.add("trailer wheels steer in steps");

        String save = method(route, "public CompoundTag save()");
        if (!save.contains("tag.putByteArray(\"Trace\", trace.save())")) out.add("the route's trace is not saved with the console");
        return out;
    }

    @Test
    void theDriveIsWiredToTheGame() throws IOException {
        List<String> found = problems(read(Path.of("src/main/resources/create_stats.mixins.json")), read("mixin/WheelMountYawMixin.java"),
                read("mixin/WheelMountBlockEntityMixin.java"), read("vccmk4/Vcc4BE.java"), read("vccmk4/Vcc4Train.java"),
                read("vccmk4/route/Vcc4Route.java"));
        assertTrue(found.isEmpty(), String.join("\n", found));
    }
}
