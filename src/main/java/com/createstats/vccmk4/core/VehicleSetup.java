package com.createstats.vccmk4.core;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VehicleSetup {
    public SteerPreset preset = SteerPreset.FRONT;

    public boolean presetAuto = true;

    public AxleRole[] customRoles = new AxleRole[0];

    public boolean[] driven = new boolean[0];
    public Keymap keymap = Keymap.defaults();
    public DriveModel.Options options = DriveModel.Options.DEFAULT;

    public boolean jetsSteerWithoutBoost;

    public Boolean liftHold;

    public boolean greeting = true;

    public final Map<Long, JetRole> jetRoles = new LinkedHashMap<>();

    public final Map<Long, Boolean> jetInverted = new LinkedHashMap<>();

    public final Map<Long, PartInput> swivelInputs = new LinkedHashMap<>();
    public final Map<Long, AuxAxis.Setting> auxSettings = new LinkedHashMap<>();

    public final Map<Long, PartInput> machineInputs = new LinkedHashMap<>();
    public final Map<Long, MachineAxis.Setting> machineSettings = new LinkedHashMap<>();

    public final Map<Long, LinkSource> linkSources = new LinkedHashMap<>();

    public final Map<Long, Boolean> linkToggle = new LinkedHashMap<>();

    public final Map<Long, Boolean> governorFlipped = new LinkedHashMap<>();

    public final Map<Long, Boolean> governorExcluded = new LinkedHashMap<>();

    public final Map<UUID, TrailerMode> trailerModes = new LinkedHashMap<>();

    public final Set<Long> couplers = new LinkedHashSet<>();

    public final Map<Long, LinkSource> linkProposals = new LinkedHashMap<>();

    public final Map<Long, LinkSource> lampProposals = new LinkedHashMap<>();

    public static final int MAX_TRAILER_MODES = 16;

    public record InputPart(long pos, boolean machine, PartInput proposal) {
    }

    public AxleRole[] rolesFor(int axles) {
        if (preset != SteerPreset.CUSTOM) return preset.roles(axles);
        AxleRole[] out = Arrays.copyOf(customRoles, axles);
        for (int i = 0; i < out.length; i++) {
            if (out[i] == null) out[i] = i == 0 ? AxleRole.STEER : AxleRole.FIXED;
        }
        return out;
    }

    public boolean[] drivenFor(int axles) {
        boolean[] out = new boolean[axles];
        Arrays.fill(out, true);
        for (int i = 0; i < Math.min(axles, driven.length); i++) out[i] = driven[i];
        return out;
    }

    public void applyPreset(SteerPreset p, int axles) {
        preset = p == null ? SteerPreset.FRONT : p;
        presetAuto = false;
        if (preset != SteerPreset.CUSTOM) customRoles = preset.roles(axles);
    }

    public void proposePreset(SteerPreset proposal) {
        if (presetAuto && proposal != null) preset = proposal;
    }

    public void setAxleRole(int axle, AxleRole role, int axles) {
        if (axle < 0 || axle >= axles) return;
        AxleRole[] roles = rolesFor(axles);
        roles[axle] = role == null ? AxleRole.FIXED : role;
        customRoles = roles;
        preset = SteerPreset.matching(roles, preset);
        presetAuto = false;
    }

    public void setAxleDriven(int axle, boolean value, int axles) {
        if (axle < 0 || axle >= axles) return;
        boolean[] d = drivenFor(axles);
        d[axle] = value;
        driven = d;
    }

    public JetRole jetRoleFor(long pos) {
        return jetRoles.getOrDefault(pos, JetRole.AUTO);
    }

    public boolean jetInverted(long pos) {
        return jetInverted.getOrDefault(pos, false);
    }

    public Map<Long, PartInput> inputs(List<InputPart> parts) {
        Map<Long, PartInput> out = new LinkedHashMap<>();
        boolean[] taken = new boolean[PartInput.AUTO_ORDER.length];
        for (InputPart p : parts) {
            PartInput c = chosen(p);
            if (c != null) take(taken, c);
            else if (p.proposal() != null) take(taken, p.proposal());
        }
        for (JetRole r : jetRoles.values()) {
            if (r.auxControl() != null) take(taken, PartInput.of(r.auxControl()));
        }
        for (LinkSource s : linkSources.values()) {
            if (s.control() != null) take(taken, PartInput.of(s.control()));
        }
        for (InputPart p : parts) {
            PartInput c = chosen(p);
            if (c == null) c = p.proposal();
            if (c == null) {
                c = PartInput.OFF;
                for (int i = 0; i < taken.length; i++) {
                    if (!taken[i]) {
                        c = PartInput.AUTO_ORDER[i];
                        taken[i] = true;
                        break;
                    }
                }
            }
            out.put(p.pos(), c);
        }
        return out;
    }

    private PartInput chosen(InputPart p) {
        return (p.machine() ? machineInputs : swivelInputs).get(p.pos());
    }

    private static void take(boolean[] taken, PartInput input) {
        for (int i = 0; i < taken.length; i++) taken[i] |= input == PartInput.AUTO_ORDER[i];
    }

    public AuxAxis.Setting auxSettingFor(long pos, PartInput input) {
        AuxAxis.Setting s = auxSettings.get(pos);
        if (s != null) return s.sane();
        return input != null && input.latchedByDefault() ? AuxAxis.Setting.LATCHED : AuxAxis.Setting.SPRUNG;
    }

    public MachineAxis.Setting machineSettingFor(long pos, boolean runner) {
        MachineAxis.Setting s = machineSettings.get(pos);
        return s != null ? s.sane() : MachineAxis.defaultSetting(runner);
    }

    public void setLinkSource(long pos, LinkSource source) {
        LinkSource s = source == null ? LinkSource.OFF : source;

        if (s == LinkSource.OFF && !couplers.contains(pos) && !lampProposals.containsKey(pos)) linkSources.remove(pos);
        else linkSources.put(pos, s);
    }

    public LinkSource linkSourceFor(long pos) {
        LinkSource chosen = linkSources.get(pos);
        if (chosen != null) return chosen;
        LinkSource coupler = linkProposals.get(pos);
        if (coupler != null) return coupler;
        return lampProposals.getOrDefault(pos, LinkSource.OFF);
    }

    public void proposeLamps(Map<Long, LinkSource> proposals) {
        lampProposals.clear();
        lampProposals.putAll(proposals);
    }

    public boolean linkToggle(long pos) {
        Boolean chosen = linkToggle.get(pos);
        if (chosen != null) return chosen;
        return couplers.contains(pos) || linkSourceFor(pos) == LinkSource.HEADLIGHT;
    }

    public void proposeCouplers(Map<Long, PartInput> partInputs) {
        linkProposals.clear();
        boolean[] taken = new boolean[PartInput.AUTO_ORDER.length];
        for (PartInput p : partInputs.values()) take(taken, p);
        for (JetRole r : jetRoles.values()) {
            if (r.auxControl() != null) take(taken, PartInput.of(r.auxControl()));
        }
        for (LinkSource s : linkSources.values()) {
            if (s.control() != null) take(taken, PartInput.of(s.control()));
        }
        for (long pos : couplers) {
            if (linkSources.containsKey(pos)) continue;
            for (int i = 0; i < taken.length; i++) {
                if (taken[i]) continue;
                taken[i] = true;
                linkProposals.put(pos, LinkSource.firstKeyOf(PartInput.AUTO_ORDER[i].control()));
                break;
            }
        }
    }

    public TrailerMode trailerModeFor(UUID trailer) {
        return trailer == null ? TrailerMode.DEFAULT : trailerModes.getOrDefault(trailer, TrailerMode.DEFAULT);
    }

    public void setTrailerMode(UUID trailer, TrailerMode mode) {
        if (trailer == null || mode == null) return;
        trailerModes.remove(trailer);
        trailerModes.put(trailer, mode);
        Iterator<UUID> it = trailerModes.keySet().iterator();
        while (trailerModes.size() > MAX_TRAILER_MODES && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    public boolean liftHoldFor(boolean jetDrive) {
        return liftHold != null ? liftHold : jetDrive;
    }

    public boolean governorFlipped(long pos) {
        return governorFlipped.getOrDefault(pos, false);
    }

    public boolean governorExcluded(long pos) {
        return governorExcluded.getOrDefault(pos, false);
    }

    public void resetToAuto() {
        preset = SteerPreset.FRONT;
        presetAuto = true;
        customRoles = new AxleRole[0];
        driven = new boolean[0];
        keymap = Keymap.defaults();
        options = DriveModel.Options.DEFAULT;
        jetsSteerWithoutBoost = false;
        liftHold = null;
        greeting = true;
        for (Map<Long, ?> m : overrideMaps()) m.clear();
        trailerModes.clear();
        linkProposals.clear();
    }

    public int forgetMissing(Set<Long> present) {
        int dropped = 0;
        for (Map<Long, ?> m : overrideMaps()) {
            int before = m.size();
            m.keySet().removeIf(k -> !present.contains(k));
            dropped += before - m.size();
        }
        return dropped;
    }

    private List<Map<Long, ?>> overrideMaps() {
        return List.of(jetRoles, jetInverted, swivelInputs, auxSettings, machineInputs, machineSettings, linkSources,
                linkToggle, governorFlipped, governorExcluded);
    }
}
