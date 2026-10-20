package com.createstats.vccmk3.alert;

import com.createstats.vccmk3.avionics.AvionicsState;
import com.createstats.vccmk3.avionics.SensorDiscovery;
import com.createstats.vccmk3.hardware.LinkHealth;
import com.createstats.vccmk3.hardware.LinkSlot;
import com.createstats.vccmk3.model.Airframe;
import com.createstats.vccmk3.model.ControlAxis;
import com.createstats.vccmk3.model.SlotSpec;
import com.createstats.vccmk3.runtime.RigSolution;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record ConsoleAlert(AlertLevel level, String code, String message) {
    public ConsoleAlert {
        if (level == null) level = AlertLevel.ADVISORY;
        if (code == null || code.isBlank()) code = "---";
        if (message == null) message = "";
    }

    public static final ConsoleAlert NOMINAL =
            new ConsoleAlert(AlertLevel.OK, "RDY", "All systems nominal");

    public static List<ConsoleAlert> evaluate(boolean armed,
                                              boolean flipGuardActive,
                                              Airframe airframe,
                                              List<LinkSlot> slots,
                                              RigSolution rig,
                                              AvionicsState avionics,
                                              @Nullable SensorDiscovery sensors,
                                              @Nullable net.minecraft.world.level.Level level,
                                              int manualOverrides,
                                              boolean autoPilotEngaged,
                                              boolean autoPilotHasFix,
                                              String autoPilotStatus,
                                              int kineticallyDrivenSurfaces) {
        List<ConsoleAlert> alerts = new ArrayList<>();

        if (flipGuardActive) {
            alerts.add(new ConsoleAlert(AlertLevel.WARNING, "FLIP",
                    "Flip guard engaged. Lift is at full and every other output is cut."));
        }

        int commandable = 0;
        int broken = 0;
        int needsSetup = 0;
        boolean governorTaken = false;
        for (LinkSlot slot : slots) {
            if (slot.isCommandable()) commandable++;
            else if (slot.health().severity() == LinkHealth.Severity.BAD) {
                broken++;
                if (slot.health() == LinkHealth.GOVERNOR_CLAIMED) governorTaken = true;
            } else if (slot.health().severity() == LinkHealth.Severity.WARN) {
                needsSetup++;
            }
        }

        if (armed && commandable == 0) {
            alerts.add(new ConsoleAlert(AlertLevel.WARNING, "HARD",
                    "Armed with no working hardware. Nothing will move."));
        }
        if (governorTaken) {
            alerts.add(new ConsoleAlert(AlertLevel.WARNING, "GOV",
                    "A governor is owned by another controller. Disarm that one first."));
        }

        if (broken > 0) {
            alerts.add(new ConsoleAlert(AlertLevel.CAUTION, "LINK",
                    broken + " hardware link" + (broken == 1 ? " is" : "s are") + " broken. See HARDWARE."));
        }
        if (needsSetup > 0) {
            alerts.add(new ConsoleAlert(AlertLevel.CAUTION, "WIRE",
                    needsSetup + " link" + (needsSetup == 1 ? "" : "s") + " half-finished. See HARDWARE."));
        }

        if (sensors != null && level != null && sensors.isWarmedUp()) {
            List<SensorDiscovery.Kind> missing = new ArrayList<>(sensors.missing(level));
            // nav table and pathfinder are optional, nagging about them trains you to ignore the strip

            missing.remove(SensorDiscovery.Kind.NAV_TABLE);
            missing.remove(SensorDiscovery.Kind.PATHFINDER);
            if (!missing.isEmpty()) {
                StringBuilder what = new StringBuilder();
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) what.append(i == missing.size() - 1 ? " and " : ", ");
                    what.append(missing.get(i).provides());
                }
                alerts.add(new ConsoleAlert(AlertLevel.CAUTION, "SENS",
                        "No sensor for " + what + ". Those readings are estimated from physics."));
            }
        }

        if (avionics.airPressure().present() && avionics.airPressure().value() < 0.45) {
            alerts.add(new ConsoleAlert(AlertLevel.CAUTION, "ALT",
                    "Air is thin. Propeller thrust is badly reduced at this altitude."));
        }

        if (kineticallyDrivenSurfaces > 0) {
            alerts.add(new ConsoleAlert(AlertLevel.CAUTION, "SURF",
                    kineticallyDrivenSurfaces + " control surface"
                            + (kineticallyDrivenSurfaces == 1 ? " is" : "s are")
                            + " still driven by a shaft, and will drift away from your commands."));
        }

        if (manualOverrides > 0) {
            alerts.add(new ConsoleAlert(AlertLevel.CAUTION, "OVRD",
                    manualOverrides + " manual override" + (manualOverrides == 1 ? "" : "s")
                            + " active, ignoring your controls."));
        }

        boolean anythingFitted = false;
        for (ControlAxis axis : ControlAxis.values()) {
            if (!rig.painted(axis).isEmpty()) {
                anythingFitted = true;
                break;
            }
        }

        if (!anythingFitted && commandable > 0) {
            alerts.add(new ConsoleAlert(AlertLevel.ADVISORY, "RIG",
                    "Hardware is linked but none of it is on the craft yet. Fit it on RIG."));
        } else if (anythingFitted) {
            List<ControlAxis> unfitted = new ArrayList<>();
            List<ControlAxis> dead = new ArrayList<>();
            for (ControlAxis axis : ControlAxis.values()) {
                if (axis == ControlAxis.AUX || !hasSlotFor(airframe, axis)) continue;
                if (rig.painted(axis).isEmpty()) {
                    unfitted.add(axis);
                    continue;
                }
                boolean works = false;
                for (com.createstats.vccmk3.model.VccDevice device : rig.painted(axis)) {
                    if (Math.abs(rig.gain(axis, device.id())) > 1.0e-6) {
                        works = true;
                        break;
                    }
                }
                if (!works) dead.add(axis);
            }
            if (!dead.isEmpty()) {
                alerts.add(new ConsoleAlert(AlertLevel.CAUTION, "RIG",
                        capitalise(listOf(dead)) + " fitted but cannot act. Check RIG."));
            }
            if (!unfitted.isEmpty()) {
                alerts.add(new ConsoleAlert(AlertLevel.ADVISORY, "AXIS",
                        "Nothing on " + listOf(unfitted) + " yet."));
            }
        }

        RigSolution.Coupling coupling = rig.worstCoupling();
        if (!coupling.isClean() && coupling.magnitude() > 1.0) {
            alerts.add(new ConsoleAlert(AlertLevel.ADVISORY, "MIX",
                    coupling.title() + ". " + coupling.detail()));
        }

        if (autoPilotEngaged && !autoPilotStatus.isEmpty()) {
            alerts.add(new ConsoleAlert(
                    autoPilotHasFix ? AlertLevel.ADVISORY : AlertLevel.CAUTION,
                    "AUTO", autoPilotStatus));
        }

        if (!armed && commandable > 0 && anythingFitted) {
            alerts.add(new ConsoleAlert(AlertLevel.ADVISORY, "SAFE",
                    "Ready to fly. Click SAFE in the header to arm."));
        }

        if (alerts.isEmpty()) return List.of(NOMINAL);

        alerts.sort(Comparator.comparingInt((ConsoleAlert a) -> a.level().ordinal()).reversed());
        return alerts;
    }

    private static boolean hasSlotFor(Airframe airframe, ControlAxis axis) {
        return airframe.serves(axis);
    }

    private static String listOf(List<ControlAxis> axes) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < axes.size(); i++) {
            if (i > 0) text.append(i == axes.size() - 1 ? " and " : ", ");
            text.append(axes.get(i).label().toLowerCase(java.util.Locale.ROOT));
        }
        return text.toString();
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
