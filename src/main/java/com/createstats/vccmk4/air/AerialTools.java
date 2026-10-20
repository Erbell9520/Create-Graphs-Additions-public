package com.createstats.vccmk4.air;

import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.Vcc4Reflect;
import com.createstats.vccmk4.Vcc4Scanner;
import com.createstats.vccmk4.air.core.AerialParts;
import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.core.AuxAxis;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vccmk4.core.PartInput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongPredicate;

final class AerialTools {
    private List<AerialParts.SwivelSpec> specs = List.of();
    private AerialParts.Assigned assigned = new AerialParts.Assigned(Map.of(), Map.of());

    private final Map<Long, Double> angle = new HashMap<>();
    private final Map<Long, AuxAxis> toolAxes = new HashMap<>();
    private final Map<Long, MachineAxis> machineAxes = new HashMap<>();
    private final Map<Long, Double> machineOut = new HashMap<>();
    private final Set<Long> ownedMachines = new HashSet<>();
    private final Set<Long> foreignMachines = new HashSet<>();

    private final Set<Long> bridgeMachines = new HashSet<>();
    private LongPredicate gunnery = pos -> false;

    void gunnery(LongPredicate isGun) {
        gunnery = isGun == null ? pos -> false : isGun;
    }

    boolean gunnery(long pos) {
        return gunnery.test(pos);
    }

    boolean bridgeHolds(long pos) {
        return bridgeMachines.contains(pos);
    }

    void assign(Vcc4Scanner.Scan scan, AerialSetup setup, Vector3d noseBody, Vector3d comLocal) {
        Vector3d right = new Vector3d(-noseBody.z, 0.0, noseBody.x);
        List<AerialParts.SwivelSpec> out = new ArrayList<>(scan.swivels().size());
        for (Vcc4Scanner.Swivel s : scan.swivels()) {
            Vector3d hinge = new Vector3d(s.hingeAxis());
            if (hinge.lengthSquared() > 1.0e-9) hinge.normalize();
            double lon = new Vector3d(Vcc4Scanner.centre(s.pos())).sub(comLocal).dot(noseBody);
            boolean turnedByMachine = false;
            for (Vcc4Scanner.Machine m : scan.machines()) {
                if (m.swivels().contains(s.pos())) {
                    turnedByMachine = true;
                    break;
                }
            }
            out.add(new AerialParts.SwivelSpec(s.pos().asLong(), lon, hinge.y, hinge.dot(right), s.body() == 0, turnedByMachine));
        }
        specs = out;
        List<AerialParts.MachineSpec> machines = new ArrayList<>(scan.machines().size());
        for (Vcc4Scanner.Machine m : scan.machines()) {
            long key = m.governor().asLong();
            machines.add(new AerialParts.MachineSpec(key, m.runner(), gunnery.test(key)));
        }
        assigned = AerialParts.assign(setup, out, machines);
    }

    List<AerialParts.SwivelSpec> specs() {
        return specs;
    }

    AerialSetup.Surface surface(long pos) {
        AerialSetup.Surface s = assigned.surfaces().get(pos);
        return s != null ? s : new AerialSetup.Surface(AerialSetup.SurfaceRole.OFF, AerialParts.TOOL_THROW, false, PartInput.OFF, false);
    }

    AerialSetup.Tool tool(long pos) {
        AerialSetup.Tool t = assigned.tools().get(pos);
        return t != null ? t : new AerialSetup.Tool(PartInput.OFF, MachineAxis.Setting.SPRUNG);
    }

    double angle(long pos) {
        return angle.getOrDefault(pos, 0.0);
    }

    double machineOutput(long pos) {
        return machineOut.getOrDefault(pos, 0.0);
    }

    boolean foreign(long pos) {
        return foreignMachines.contains(pos);
    }

    Map<Long, Double> angles() {
        return angle;
    }

    boolean drive(ServerLevel level, Vcc4Scanner.Scan scan, double[] controls, boolean piloted, boolean flying, double turn,
                  double trim, double flow, BlockPos console, UUID id, List<String> problems) {
        boolean changed = false;
        int index = 0;
        for (Vcc4Scanner.Swivel s : scan.swivels()) {
            index++;
            long key = s.pos().asLong();
            AerialSetup.Surface surface = surface(key);
            AerialParts.SwivelSpec spec = specAt(key);
            if (surface.role() == AerialSetup.SurfaceRole.OFF || spec == null) continue;
            double before = angle(key);
            double next = before;
            if (surface.role() == AerialSetup.SurfaceRole.TOOL) {
                PartInput input = surface.input();
                if (!piloted || input == PartInput.OFF) continue;
                AuxAxis axis = toolAxes.computeIfAbsent(key, k -> {
                    AuxAxis a = new AuxAxis();
                    a.set(before);
                    return a;
                });
                Control c = input.control();
                next = axis.step(AerialParts.auxSetting(surface), c == null ? 0.0 : controls[c.ordinal()]);
            } else {
                double target = flying ? AerialParts.target(surface, spec, turn, trim, flow) : 0.0;
                next = AerialParts.slew(before, target);
                toolAxes.remove(key);
            }
            if (Math.abs(next - before) > 1.0e-6) {
                angle.put(key, next);
                changed |= surface.role() == AerialSetup.SurfaceRole.TOOL;
            }
            if (!s.assembled()) {
                problems.add("S" + index + " turns nothing: build the fin or the arm on the swivel bearing and assemble it.");
                continue;
            }
            if (!s.locked()) {
                problems.add("S" + index + " is not locked, so it cannot hold an angle: give its swivel bearing a redstone signal.");
                continue;
            }
            BlockEntity be = SableBridge.getBlockEntityOnContraption(level, s.pos());
            if (be != null && SableBridge.matchesSwivelBearing(be)) Vcc4Reflect.setSwivelAngle(level, be, angle(key));
        }
        index = 0;
        for (Vcc4Scanner.Machine m : scan.machines()) {
            index++;
            long key = m.governor().asLong();
            AerialSetup.Tool tool = tool(key);
            if (!piloted || tool.input() == PartInput.OFF) {
                releaseMachine(level, m.governor(), id);
                machineOut.remove(key);
                MachineAxis axis = machineAxes.get(key);
                if (axis != null) axis.reset();

                if (SableBridge.getBlockEntityOnContraption(level, m.governor()) instanceof RpmGovernorBE g && heldByBridge(level, g, id)) {
                    bridgeMachines.add(key);
                } else {
                    bridgeMachines.remove(key);
                }
                foreignMachines.remove(key);
                continue;
            }
            Control c = tool.input().control();
            double value = c == null ? 0.0 : controls[c.ordinal()];
            double out = machineAxes.computeIfAbsent(key, k -> new MachineAxis()).step(tool.setting(), value, twoKeys(c));
            machineOut.put(key, out);
            int rpm = (int) Math.round(out * tool.setting().rpm());
            if (!(SableBridge.getBlockEntityOnContraption(level, m.governor()) instanceof RpmGovernorBE governor)) continue;
            if (governor.setCommand(console, id, rpm)) {
                ownedMachines.add(key);
                foreignMachines.remove(key);
                bridgeMachines.remove(key);
            } else if (heldByBridge(level, governor, id)) {
                bridgeMachines.add(key);
                foreignMachines.remove(key);
            } else {
                foreignMachines.add(key);
                bridgeMachines.remove(key);
                problems.add("M" + index + " is held by another controller (a second console, or a Flight Control Computer), so this one cannot run it.");
            }
        }
        return changed;
    }

    private static boolean heldByBridge(ServerLevel level, RpmGovernorBE g, UUID self) {
        UUID owner = g.getCommanderUuid();
        if (owner == null || owner.equals(self) || g.isFailSafeActive() || g.getCommanderPos() == null) return false;
        return SableBridge.getBlockEntityOnContraption(level, g.getCommanderPos()) instanceof BattleScreenDisplayBE;
    }

    private boolean twoKeys(Control c) {
        return c == null || !c.isAxis() || twoKeyed.contains(c);
    }

    private final Set<Control> twoKeyed = new HashSet<>();

    void keysBound(Set<Control> bothKeys) {
        twoKeyed.clear();
        twoKeyed.addAll(bothKeys);
    }

    int rudders(Vcc4Scanner.Scan scan) {
        int n = 0;
        for (Vcc4Scanner.Swivel s : scan.swivels()) {
            if (s.assembled() && s.locked() && surface(s.pos().asLong()).role() == AerialSetup.SurfaceRole.RUDDER) n++;
        }
        return n;
    }

    private AerialParts.SwivelSpec specAt(long pos) {
        for (AerialParts.SwivelSpec s : specs) if (s.pos() == pos) return s;
        return null;
    }

    void release(ServerLevel level, UUID id) {
        for (Long key : new ArrayList<>(ownedMachines)) releaseMachine(level, BlockPos.of(key), id);
        machineOut.clear();
        for (MachineAxis a : machineAxes.values()) a.reset();
    }

    private void releaseMachine(ServerLevel level, BlockPos governor, UUID id) {
        if (!ownedMachines.remove(governor.asLong())) return;
        if (SableBridge.getBlockEntityOnContraption(level, governor) instanceof RpmGovernorBE g) g.releaseOwnership(id);
    }

    void resetMachine(long pos) {
        MachineAxis a = machineAxes.get(pos);
        if (a != null) a.reset();
    }

    void restoreAngles(Map<Long, Double> saved) {
        angle.clear();
        angle.putAll(saved);
        toolAxes.clear();
    }
}
