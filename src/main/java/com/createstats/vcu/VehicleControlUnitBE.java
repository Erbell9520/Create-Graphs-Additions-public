package com.createstats.vcu;

import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.flight.runtime.WheelMountSteeringOverrides;
import com.createstats.vcu.core.Allocator;
import com.createstats.vcu.core.Axis6;
import com.createstats.vcu.core.BodyFrame;
import com.createstats.vcu.core.InputShaper;
import com.createstats.vcu.core.PilotInput;
import com.createstats.vcu.core.Scheme;
import com.createstats.vcu.core.SurfaceGeometry;
import com.createstats.vcu.core.V3;
import com.createstats.vcu.core.WheelSteer;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class VehicleControlUnitBE extends BlockEntity implements IHaveGoggleInformation {
    static final int MAX_PROP_RPM = 256;
    static final int MAX_WHEEL_RPM = 128;

    static final double CRUISE_CAP = 0.7;

    static final double MAX_SURFACE_DEG = 24.0;

    static final double SURFACE_SLEW_DEG = 6.0;

    static final double SURFACE_SPEED_FULL = 15.0;

    static final int INPUT_TIMEOUT_TICKS = 40;
    static final double PILOT_RANGE = 48.0;

    static final int CALIBRATION_TICKS = 20;
    static final int CALIBRATION_RPM = 3;

    private static final Set<VehicleControlUnitBE> CLIENT_INSTANCES =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private UUID controllerId = UUID.randomUUID();
    @Nullable private Scheme schemeOverride;

    private final Map<Long, Double> wheelRatio = new HashMap<>();

    private final Map<Long, Integer> propSign = new HashMap<>();

    // never saved armed, a reload must not fly off
    private boolean armed;
    @Nullable private UUID pilot;
    private int inputBits;
    private long lastInputTick = Long.MIN_VALUE;
    private final InputShaper shaper = new InputShaper();
    private VcuHardware.Scan scan = VcuHardware.Scan.EMPTY;
    private double[][] surfaceGains = new double[0][];
    private int scanCooldown = 5;
    private int calibrationTicks;
    private final Map<Long, Double> surfaceAngle = new HashMap<>();
    private final Set<BlockPos> ownedGovernors = new LinkedHashSet<>();
    private final List<String> runtimeProblems = new ArrayList<>();
    private String status = "";

    private boolean syncArmed;
    @Nullable private UUID syncPilot;
    private List<String> syncLines = List.of();

    public VehicleControlUnitBE(BlockPos pos, BlockState state) {
        super(VcuRegistry.BLOCK_ENTITY, pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        if (--scanCooldown <= 0) {
            rescan(server);
            scanCooldown = armed ? 40 : 100;
        }
        if (!armed) return;

        ServerPlayer p = pilot == null ? null : server.getServer().getPlayerList().getPlayer(pilot);
        if (p == null || !inRange(p)) {
            disarm(server, p, "The pilot left the helm.");
            return;
        }
        int bits = now - lastInputTick > INPUT_TIMEOUT_TICKS ? 0 : inputBits;
        fly(server, now, bits);
        if (calibrationTicks > 0) calibrationTicks--;
        if (now % 5 == 0) p.displayClientMessage(Component.literal(status), true);
    }

    private void rescan(ServerLevel server) {
        scan = VcuHardware.scan(server, this);
        BodyFrame frame = frame();
        double[][] columns = new double[scan.surfaces().size()][];
        for (int i = 0; i < columns.length; i++) {
            VcuHardware.Surface s = scan.surfaces().get(i);
            columns[i] = SurfaceGeometry.column(frame, s.hingePoint(), s.hingeAxis(), s.bodyCentre());
        }
        surfaceGains = SurfaceGeometry.gains(columns);
        publish();
    }

    private void fly(ServerLevel server, long now, int bits) {
        runtimeProblems.clear();
        BodyFrame frame = frame();
        Scheme scheme = effectiveScheme(frame);
        InputShaper.Demand demand = shaper.shape(scheme, bits, hasForwardThrust(frame));
        Map<BlockPos, double[]> governorDemand = new HashMap<>();

        driveWheels(now, frame, demand, governorDemand);
        moveSurfaces(server, demand);
        driveProps(frame, scheme, demand, governorDemand);
        setLift(server, demand.lift());
        commandGovernors(server, governorDemand);
        learn(server);
        status = statusLine(scheme, demand);
    }

    private void driveWheels(long now, BodyFrame frame, InputShaper.Demand d, Map<BlockPos, double[]> governorDemand) {
        List<VcuHardware.Wheel> wheels = scan.wheels();
        if (wheels.isEmpty()) return;
        List<WheelSteer.Wheel> geometry = new ArrayList<>(wheels.size());
        for (VcuHardware.Wheel w : wheels) {
            geometry.add(new WheelSteer.Wheel(frame.lateral(w.contact()), frame.longitudinal(w.contact())));
        }
        List<WheelSteer.Command> commands = WheelSteer.solve(geometry, d.wheelSteer(), d.wheelThrottle(), WheelSteer.Mode.FRONT);
        for (int i = 0; i < wheels.size(); i++) {
            VcuHardware.Wheel w = wheels.get(i);
            WheelSteer.Command c = commands.get(i);

            int steps = WheelSteer.signalSteps(c.angleDeg());
            int left = c.angleDeg() > 0 ? steps : 0;
            int right = c.angleDeg() < 0 ? steps : 0;
            WheelMountSteeringOverrides.setForTick(w.pos(), left, right, now);

            BlockPos governor = scan.governorFor(w.pos());
            if (governor == null) continue;
            double forward = wheelForwardSign(w, frame);
            if (forward == 0.0) continue;
            double mountSpeed = c.drive() * MAX_WHEEL_RPM * forward;
            double ratio = wheelRatio.getOrDefault(w.pos().asLong(), 1.0);
            accumulate(governorDemand, governor, mountSpeed / ratio);
        }
    }

    // offroad drives X mounts along +Z and Z mounts along -X. of course it does
    private static double wheelForwardSign(VcuHardware.Wheel w, BodyFrame frame) {
        V3 push = w.mountAxis() == Direction.Axis.X ? V3.UNIT_Z : new V3(-1, 0, 0);
        double along = push.dot(frame.forward());
        return Math.abs(along) < 0.5 ? 0.0 : Math.signum(along);
    }

    private void moveSurfaces(ServerLevel server, InputShaper.Demand d) {
        List<VcuHardware.Surface> surfaces = scan.surfaces();
        for (int i = 0; i < surfaces.size() && i < surfaceGains.length; i++) {
            VcuHardware.Surface s = surfaces.get(i);
            if (!s.assembled() || !s.locked()) continue;
            double target = SurfaceGeometry.deflection(surfaceGains[i], d.axes()) * MAX_SURFACE_DEG;
            slewSurface(server, s.pos(), target);
        }
    }

    private void slewSurface(ServerLevel server, BlockPos pos, double target) {
        double last = surfaceAngle.getOrDefault(pos.asLong(), 0.0);
        double next = last + Allocator.clamp(target - last, -SURFACE_SLEW_DEG, SURFACE_SLEW_DEG);
        surfaceAngle.put(pos.asLong(), next);
        BlockEntity be = SableBridge.getBlockEntityOnContraption(server, pos);
        if (be != null && SableBridge.matchesSwivelBearing(be)) {
            VcuActuators.setSwivelAngle(server, be, next);
        }
    }

    private void driveProps(BodyFrame frame, Scheme scheme, InputShaper.Demand d, Map<BlockPos, double[]> governorDemand) {
        Map<BlockPos, double[]> columns = new LinkedHashMap<>();
        Map<BlockPos, Boolean> reversible = new HashMap<>();
        for (VcuHardware.Prop p : scan.props()) {
            BlockPos governor = scan.governorFor(p.pos());
            if (governor == null) continue;
            int sign = propSign.getOrDefault(p.pos().asLong(), 1);
            double[] w = frame.wrench(p.centre(), p.axis().scale(sign * p.strength()));
            double[] col = columns.computeIfAbsent(governor, g -> new double[Axis6.COUNT]);
            for (int a = 0; a < Axis6.COUNT; a++) col[a] += w[a];

            boolean horizontal = Math.abs(p.axis().dot(frame.up())) < 0.7;
            reversible.merge(governor, horizontal, Boolean::logicalAnd);
        }
        if (columns.isEmpty()) return;

        boolean drivenWheels = scan.wheels().stream().anyMatch(w -> scan.governorFor(w.pos()) != null);
        List<BlockPos> order = new ArrayList<>(columns.keySet());
        int n = order.size();
        double[][] b = new double[Axis6.COUNT][n];
        double[] lo = new double[n];
        double[] hi = new double[n];
        for (int j = 0; j < n; j++) {
            double[] col = columns.get(order.get(j));
            for (int a = 0; a < Axis6.COUNT; a++) b[a][j] = col[a];

            boolean jet = scheme == Scheme.GROUND && drivenWheels && isForwardDominant(col);
            double cap = d.boost() ? 1.0 : (jet ? 0.0 : CRUISE_CAP);
            hi[j] = cap;
            lo[j] = reversible.getOrDefault(order.get(j), false) ? -cap : 0.0;
        }

        double beta = scan.surfaces().isEmpty() ? 0.0 : Math.min(1.0, airspeed() / SURFACE_SPEED_FULL);
        double[] scale = new double[Axis6.COUNT];
        for (Axis6 axis : Axis6.values()) scale[axis.index()] = axis.isMoment() ? 1.0 - beta : 1.0;

        Allocator.Result r = Allocator.fly(b, d.axes(), scale, lo, hi);
        for (int j = 0; j < n; j++) {
            accumulate(governorDemand, order.get(j), r.commands()[j] * MAX_PROP_RPM);
        }
    }

    private static boolean isForwardDominant(double[] col) {
        double forward = col[Axis6.FORWARD.index()];
        double up = Math.abs(col[Axis6.UP.index()]);
        double side = Math.abs(col[Axis6.RIGHT.index()]);
        return forward > 0 && forward >= up && forward >= side;
    }

    private double airspeed() {
        Vector3d v = SableBridge.linearVelocityWorld(this);
        return v == null ? 0.0 : v.length();
    }

    private boolean hasForwardThrust(BodyFrame frame) {
        for (VcuHardware.Prop p : scan.props()) {
            if (scan.governorFor(p.pos()) != null && Math.abs(p.axis().dot(frame.forward())) > 0.7) return true;
        }
        return false;
    }

    private void setLift(ServerLevel server, double lift) {
        int signal = (int) Math.round(Allocator.clamp(lift, 0.0, 1.0) * 15.0);
        for (VcuHardware.Lift l : scan.lifts()) {
            BlockEntity be = SableBridge.getBlockEntityOnContraption(server, l.pos());
            if (be == null) continue;
            if (l.vent()) VcuActuators.setVentSignal(be, signal);
            else VcuActuators.setBurnerSignal(be, signal);
        }
    }

    private void commandGovernors(ServerLevel server, Map<BlockPos, double[]> governorDemand) {
        Set<BlockPos> used = scan.usedGovernors();
        for (BlockPos pos : used) {
            double[] acc = governorDemand.get(pos);
            int rpm = acc == null ? 0 : (int) Math.round(acc[0] / acc[1]);
            if (rpm == 0 && calibrationTicks > 0 && needsCalibration(pos)) rpm = CALIBRATION_RPM;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(server, pos);
            if (!(be instanceof RpmGovernorBE governor)) continue;
            if (governor.setCommand(worldPosition, controllerId, Math.max(-MAX_PROP_RPM, Math.min(MAX_PROP_RPM, rpm)))) {
                ownedGovernors.add(pos);
            } else {
                runtimeProblems.add("Governor at " + pos.toShortString() + " belongs to another controller.");
            }
        }
    }

    private boolean needsCalibration(BlockPos governor) {
        for (VcuHardware.Wheel w : scan.wheels()) {
            if (governor.equals(scan.governorFor(w.pos())) && !wheelRatio.containsKey(w.pos().asLong())) return true;
        }
        for (VcuHardware.Prop p : scan.props()) {
            if (governor.equals(scan.governorFor(p.pos())) && !propSign.containsKey(p.pos().asLong())) return true;
        }
        return false;
    }

    private void learn(ServerLevel server) {
        for (VcuHardware.Wheel w : scan.wheels()) {
            RpmGovernorBE g = governorAt(server, scan.governorFor(w.pos()));
            if (g == null || Math.abs(g.getAppliedRpm()) < 2) continue;
            if (!(SableBridge.getBlockEntityOnContraption(server, w.pos()) instanceof KineticBlockEntity mount)) continue;
            float speed = mount.getSpeed();
            if (Math.abs(speed) < 0.5f) continue;
            wheelRatio.put(w.pos().asLong(), speed / (double) g.getAppliedRpm());
        }
        for (VcuHardware.Prop p : scan.props()) {
            RpmGovernorBE g = governorAt(server, scan.governorFor(p.pos()));
            if (g == null || Math.abs(g.getAppliedRpm()) < 2) continue;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(server, p.pos());
            double along = thrustAlongAxis(be, p);
            if (Math.abs(along) < 1.0e-4) continue;
            propSign.put(p.pos().asLong(), (int) Math.signum(along * g.getAppliedRpm()));
        }
    }

    private static double thrustAlongAxis(@Nullable BlockEntity be, VcuHardware.Prop p) {
        if (be == null) return 0.0;
        SableBridge.PropData data = SableBridge.readProp(be);
        if (data != null) {
            V3 dir = new V3(data.thrustDirLocal().x, data.thrustDirLocal().y, data.thrustDirLocal().z);
            if (dir.length() < 0.5) dir = p.axis();
            return dir.normalized().scale(data.thrustSigned()).dot(p.axis());
        }
        Double raw = SableBridge.propellerThrust(be);
        return raw == null ? 0.0 : -raw;
    }

    @Nullable
    private RpmGovernorBE governorAt(ServerLevel server, @Nullable BlockPos pos) {
        if (pos == null) return null;
        return SableBridge.getBlockEntityOnContraption(server, pos) instanceof RpmGovernorBE g ? g : null;
    }

    private static void accumulate(Map<BlockPos, double[]> acc, BlockPos governor, double value) {
        double[] a = acc.computeIfAbsent(governor, g -> new double[2]);
        a[0] += value;
        a[1] += 1.0;
    }

    public void toggleHelm(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return;
        if (armed && pilot != null && pilot.equals(player.getUUID())) {
            disarm(server, player, "You released the helm.");
            return;
        }
        if (armed && pilot != null) {
            ServerPlayer current = server.getServer().getPlayerList().getPlayer(pilot);
            if (current != null && inRange(current)) {
                player.displayClientMessage(Component.literal(current.getGameProfile().getName() + " has the helm."), true);
                return;
            }
        }
        rescan(server);
        scanCooldown = 40;
        if (!scan.onCraft()) {
            tell(player, scan.problems());
            return;
        }
        armed = true;
        pilot = player.getUUID();
        inputBits = 0;
        lastInputTick = server.getGameTime();
        shaper.reset();
        calibrationTicks = CALIBRATION_TICKS;
        surfaceAngle.clear();
        publish();
        player.sendSystemMessage(Component.literal("You have the helm. Sit in a seat on this craft to fly; right-click again to let go.")
                .withStyle(ChatFormatting.GREEN));
        tell(player, reportLines());
        player.sendSystemMessage(Component.literal("Wrong way? Turn the VCU with a wrench so its screen faces you while you look where the craft should go.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public void cycleScheme(ServerPlayer player) {
        Scheme[] all = Scheme.values();
        if (schemeOverride == null) schemeOverride = all[0];
        else if (schemeOverride.ordinal() == all.length - 1) schemeOverride = null;
        else schemeOverride = all[schemeOverride.ordinal() + 1];
        shaper.reset();
        setChanged();
        publish();
        tell(player, reportLines());
    }

    public void acceptInput(ServerPlayer player, int bits) {
        if (!armed || pilot == null || !pilot.equals(player.getUUID()) || level == null) return;
        if (!inRange(player)) return;
        inputBits = bits & PilotInput.ALL;
        lastInputTick = level.getGameTime();
    }

    private void disarm(ServerLevel server, @Nullable ServerPlayer player, String why) {
        armed = false;
        pilot = null;
        inputBits = 0;
        shaper.reset();
        releaseHardware(server);
        publish();
        if (player != null) player.displayClientMessage(Component.literal(why), true);
    }

    private void releaseHardware(ServerLevel server) {
        for (BlockPos pos : ownedGovernors) {
            RpmGovernorBE g = governorAt(server, pos);
            if (g != null) g.releaseOwnership(controllerId);
        }
        ownedGovernors.clear();
        for (VcuHardware.Surface s : scan.surfaces()) {
            BlockEntity be = SableBridge.getBlockEntityOnContraption(server, s.pos());
            if (be != null && s.assembled() && s.locked()) VcuActuators.setSwivelAngle(server, be, 0.0);
        }
        surfaceAngle.clear();
        setLift(server, 0.0);
    }

    private boolean inRange(Player player) {
        Vector3d here = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        Object craft = SableBridge.subLevelAccessOf(this);
        if (craft != null) {
            Vector3d world = SableBridge.localToWorld(craft, here);
            if (world != null) here = world;
        }
        return player.position().distanceToSqr(here.x, here.y, here.z) <= PILOT_RANGE * PILOT_RANGE;
    }

    BodyFrame frame() {
        Direction nose = getBlockState().getValue(VehicleControlUnitBlock.FACING);
        return BodyFrame.of(new V3(nose.getStepX(), 0, nose.getStepZ()), V3.UNIT_Y, scan.pivot());
    }

    Scheme effectiveScheme(BodyFrame frame) {
        return schemeOverride != null ? schemeOverride : Scheme.detect(traits(frame));
    }

    private Scheme.Traits traits(BodyFrame frame) {
        double up = 0.0;
        double forward = 0.0;
        double total = 0.0;
        for (VcuHardware.Prop p : scan.props()) {
            if (scan.governorFor(p.pos()) == null) continue;
            total += p.strength();
            if (Math.abs(p.axis().dot(frame.up())) > 0.7) up += p.strength();
            if (Math.abs(p.axis().dot(frame.forward())) > 0.7) forward += p.strength();
        }
        boolean roll = false;
        boolean pitch = false;
        for (double[] g : surfaceGains) {
            roll |= g[Axis6.ROLL.index()] != 0.0;
            pitch |= g[Axis6.PITCH.index()] != 0.0;
        }
        int driven = (int) scan.wheels().stream().filter(w -> scan.governorFor(w.pos()) != null).count();
        return new Scheme.Traits(scan.wheels().size(), driven, scan.surfaces().size(), roll && pitch,
                scan.lifts().size(), up, forward, total);
    }

    private List<String> reportLines() {
        List<String> lines = new ArrayList<>();
        BodyFrame frame = frame();
        Scheme scheme = effectiveScheme(frame);
        lines.add("Keys: " + scheme.label() + (schemeOverride == null ? " (picked from the hardware)" : " (set by hand, sneak-right-click to change)"));
        lines.add(keyHelp(scheme));
        long drivenWheels = scan.wheels().stream().filter(w -> scan.governorFor(w.pos()) != null).count();
        long governedProps = scan.props().stream().filter(p -> scan.governorFor(p.pos()) != null).count();
        lines.add(String.format(Locale.ROOT, "Found: %d wheel(s), %d driven; %d prop(s), %d throttled; %d surface(s); %d burner(s)/vent(s); %d governor(s)",
                scan.wheels().size(), drivenWheels, scan.props().size(), governedProps, scan.surfaces().size(),
                scan.lifts().size(), scan.usedGovernors().size()));
        int shown = 0;
        for (String problem : scan.problems()) {
            if (shown++ >= 5) break;
            lines.add("! " + problem);
        }
        for (String problem : runtimeProblems) {
            if (shown++ >= 6) break;
            lines.add("! " + problem);
        }
        return lines;
    }

    private static String keyHelp(Scheme scheme) {
        return switch (scheme) {
            case GROUND -> "W/S drive, A/D steer, Space/C lift, Z/X strafe, Sprint fires the jets";
            case AIRSHIP -> "W/S push, A/D turn, Space/C lift (stays set), Z/X strafe, Sprint boost";
            case PLANE -> "W/S dive/climb, A/D roll, Z/X rudder, Space/C throttle (stays set), Sprint boost";
            case HOVER -> "W/S tilt or push, A/D roll, Z/X turn, Space/C lift (stays set), Sprint boost";
        };
    }

    private String statusLine(Scheme scheme, InputShaper.Demand d) {
        double[] a = d.axes();
        String body = switch (scheme) {
            case GROUND -> "drive " + pct(d.wheelThrottle()) + "  steer " + pct(d.wheelSteer()) + "  lift " + pct(d.lift());
            case AIRSHIP -> "push " + pct(a[Axis6.FORWARD.index()]) + "  turn " + pct(a[Axis6.YAW.index()]) + "  lift " + pct(d.lift());
            case PLANE -> "throttle " + pct(a[Axis6.FORWARD.index()]) + "  pitch " + pct(a[Axis6.PITCH.index()])
                    + "  roll " + pct(a[Axis6.ROLL.index()]) + "  rudder " + pct(a[Axis6.YAW.index()]);
            case HOVER -> "lift " + pct(a[Axis6.UP.index()]) + "  pitch " + pct(a[Axis6.PITCH.index()])
                    + "  roll " + pct(a[Axis6.ROLL.index()]) + "  turn " + pct(a[Axis6.YAW.index()]);
        };
        String line = "VCU " + scheme.label() + " | " + body + (d.boost() ? " | BOOST" : "");
        if (!runtimeProblems.isEmpty()) line += " | " + runtimeProblems.get(0);
        return line;
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%+d%%", Math.round(v * 100.0));
    }

    private static void tell(ServerPlayer player, List<String> lines) {
        for (String line : lines) {
            player.sendSystemMessage(Component.literal(line).withStyle(line.startsWith("!") ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
    }

    private void publish() {
        syncArmed = armed;
        syncPilot = pilot;
        syncLines = List.copyOf(reportLines());
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    Vehicle Control Unit").withStyle(ChatFormatting.WHITE));
        String who = "nobody";
        if (syncPilot != null && level != null) {
            Player p = level.getPlayerByUUID(syncPilot);
            who = p != null ? p.getGameProfile().getName() : "someone";
        }
        tooltip.add(Component.literal("    " + (syncArmed ? "Flying, helm: " + who : "Idle, right-click to take the helm"))
                .withStyle(syncArmed ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        for (String line : syncLines) {
            tooltip.add(Component.literal("    " + line).withStyle(line.startsWith("!") ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
        return true;
    }

    @Nullable
    public static VehicleControlUnitBE clientPilotedBy(UUID player) {
        synchronized (CLIENT_INSTANCES) {
            for (VehicleControlUnitBE be : CLIENT_INSTANCES) {
                if (!be.isRemoved() && be.syncArmed && player.equals(be.syncPilot)) return be;
            }
        }
        return null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) CLIENT_INSTANCES.add(this);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel server && armed) {
            armed = false;
            releaseHardware(server);
        }
        CLIENT_INSTANCES.remove(this);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("ControllerId", controllerId);
        if (schemeOverride != null) tag.putString("Scheme", schemeOverride.name());
        CompoundTag ratios = new CompoundTag();
        wheelRatio.forEach((k, v) -> ratios.putDouble(Long.toString(k), v));
        tag.put("WheelRatio", ratios);
        CompoundTag signs = new CompoundTag();
        propSign.forEach((k, v) -> signs.putInt(Long.toString(k), v));
        tag.put("PropSign", signs);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("ControllerId")) controllerId = tag.getUUID("ControllerId");
        schemeOverride = null;
        if (tag.contains("Scheme")) {
            try {
                schemeOverride = Scheme.valueOf(tag.getString("Scheme"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        wheelRatio.clear();
        CompoundTag ratios = tag.getCompound("WheelRatio");
        for (String k : ratios.getAllKeys()) {
            try {
                wheelRatio.put(Long.parseLong(k), ratios.getDouble(k));
            } catch (NumberFormatException ignored) {
            }
        }
        propSign.clear();
        CompoundTag signs = tag.getCompound("PropSign");
        for (String k : signs.getAllKeys()) {
            try {
                propSign.put(Long.parseLong(k), signs.getInt(k));
            } catch (NumberFormatException ignored) {
            }
        }
        if (tag.contains("SyncLines")) {
            syncArmed = tag.getBoolean("SyncArmed");
            syncPilot = tag.hasUUID("SyncPilot") ? tag.getUUID("SyncPilot") : null;
            List<String> lines = new ArrayList<>();
            for (Tag t : tag.getList("SyncLines", Tag.TAG_STRING)) lines.add(t.getAsString());
            syncLines = List.copyOf(lines);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.putBoolean("SyncArmed", syncArmed);
        if (syncPilot != null) tag.putUUID("SyncPilot", syncPilot);
        ListTag lines = new ListTag();
        for (String line : syncLines) lines.add(StringTag.valueOf(line));
        tag.put("SyncLines", lines);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
