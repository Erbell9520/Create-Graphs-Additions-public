package com.createstats.vccmk4;

import com.createstats.vccmk4.core.AuxAxis;
import com.createstats.vccmk4.core.AxleRole;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.JetRole;
import com.createstats.vccmk4.core.Keymap;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.TrailerMode;
import com.createstats.vccmk4.core.VehicleSetup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

final class Vcc4Nbt {
    static final int VERSION = 4;

    private Vcc4Nbt() {
    }

    static CompoundTag write(VehicleSetup s) {
        CompoundTag tag = tuning(s);
        tag.put("JetRoles", strings(s.jetRoles, JetRole::key));
        tag.put("JetInverted", booleans(s.jetInverted));
        tag.put("SwivelInputs", strings(s.swivelInputs, PartInput::key));
        ListTag auxSettings = new ListTag();
        s.auxSettings.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("Pos", k);
            e.putInt("Travel", v.travelDeg());
            e.putBoolean("Latched", v.latched());
            e.putBoolean("Inverted", v.inverted());
            auxSettings.add(e);
        });
        tag.put("AuxSettings", auxSettings);
        tag.put("MachineInputs", strings(s.machineInputs, PartInput::key));
        ListTag machines = new ListTag();
        s.machineSettings.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("Pos", k);
            e.putInt("Rpm", v.rpm());
            e.putBoolean("Latched", v.latched());
            e.putBoolean("Inverted", v.inverted());
            machines.add(e);
        });
        tag.put("MachineSettings", machines);
        tag.put("LinkSources", strings(s.linkSources, LinkSource::key));
        tag.put("LinkToggle", booleans(s.linkToggle));
        tag.put("GovernorFlipped", booleans(s.governorFlipped));
        tag.put("GovernorExcluded", booleans(s.governorExcluded));
        ListTag trailers = new ListTag();
        s.trailerModes.forEach((id, mode) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.putString("Mode", mode.key());
            trailers.add(e);
        });
        tag.put("TrailerModes", trailers);
        return tag;
    }

    static CompoundTag tuning(VehicleSetup s) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.putInt("Preset", s.preset.ordinal());
        tag.putBoolean("PresetAuto", s.presetAuto);
        int[] roles = new int[s.customRoles.length];
        for (int i = 0; i < roles.length; i++) roles[i] = s.customRoles[i] == null ? AxleRole.FIXED.ordinal() : s.customRoles[i].ordinal();
        tag.putIntArray("Roles", roles);
        int[] driven = new int[s.driven.length];
        for (int i = 0; i < driven.length; i++) driven[i] = s.driven[i] ? 1 : 0;
        tag.putIntArray("Driven", driven);
        tag.putIntArray("Keymap", s.keymap.toInts());
        DriveModel.Options o = s.options;
        tag.putBoolean("BrakeThenReverse", o.brakeThenReverse());
        tag.putBoolean("SpeedSteer", o.speedSteer());
        tag.putInt("SteerTicks", o.steerTicks());
        tag.putInt("Lock", o.lockDeg());
        tag.putInt("MaxRpm", o.maxRpm());
        tag.putInt("Reverse", o.reversePercent());
        tag.putInt("ThrottleTicks", o.throttleTicks());
        tag.putInt("ReleaseTicks", o.releaseTicks());
        tag.putBoolean("HoldWhenStopped", o.holdWhenStopped());
        tag.putBoolean("ParkBrake", o.parkBrake());
        tag.putBoolean("JetsSteerAlone", s.jetsSteerWithoutBoost);
        if (s.liftHold != null) tag.putBoolean("LiftHold", s.liftHold);
        tag.putBoolean("Greeting", s.greeting);
        return tag;
    }

    static void read(CompoundTag tag, VehicleSetup s) {
        s.resetToAuto();
        if (tag.isEmpty()) return;
        int version = tag.getInt("Version");
        readTuning(tag, version, s);
        if (version >= 2) {
            readStrings(tag.getCompound("JetRoles"), (key, v) -> {
                JetRole role = JetRole.byKey(v);
                if (role != JetRole.AUTO) s.jetRoles.put(key, role);
            });
            readStrings(tag.getCompound("SwivelInputs"), (key, v) -> s.swivelInputs.put(key, PartInput.byKey(v)));
        } else {
            CompoundTag props = tag.getCompound("PropRoles");
            for (String k : props.getAllKeys()) {
                Long key = parse(k);
                JetRole role = JetRole.fromLegacyOrdinal(props.getInt(k));
                if (key != null && role != JetRole.AUTO) s.jetRoles.put(key, role);
            }
            CompoundTag auxRoles = tag.getCompound("AuxRoles");
            for (String k : auxRoles.getAllKeys()) {
                Long key = parse(k);
                if (key != null) s.swivelInputs.put(key, PartInput.fromLegacyOrdinal(auxRoles.getInt(k)));
            }
        }
        readBooleans(tag.getCompound("JetInverted"), s.jetInverted::put);
        for (Tag t : tag.getList("AuxSettings", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            s.auxSettings.put(e.getLong("Pos"), new AuxAxis.Setting(e.getInt("Travel"), e.getBoolean("Latched"), e.getBoolean("Inverted")).sane());
        }
        readStrings(tag.getCompound("MachineInputs"), (key, v) -> s.machineInputs.put(key, PartInput.byKey(v)));
        for (Tag t : tag.getList("MachineSettings", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            s.machineSettings.put(e.getLong("Pos"), new MachineAxis.Setting(e.getInt("Rpm"), e.getBoolean("Latched"), e.getBoolean("Inverted")).sane());
        }

        readStrings(tag.getCompound("LinkSources"), (key, v) -> {
            LinkSource src = LinkSource.byKey(v);
            if (src != LinkSource.OFF || LinkSource.OFF.key().equals(v)) s.linkSources.put(key, src);
        });
        readBooleans(tag.getCompound("LinkToggle"), s.linkToggle::put);
        readBooleans(tag.getCompound("GovernorFlipped"), s.governorFlipped::put);
        readBooleans(tag.getCompound("GovernorExcluded"), s.governorExcluded::put);

        for (Tag t : tag.getList("TrailerModes", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            if (e.hasUUID("Id")) s.setTrailerMode(e.getUUID("Id"), TrailerMode.byKey(e.getString("Mode")));
        }
    }

    static boolean applyTuning(CompoundTag tag, VehicleSetup s) {
        if (!tag.contains("Preset") || !tag.contains("Keymap")) return false;
        readTuning(tag, tag.getInt("Version"), s);
        return true;
    }

    private static void readTuning(CompoundTag tag, int version, VehicleSetup s) {
        s.preset = SteerPreset.byOrdinal(tag.getInt("Preset"));

        s.presetAuto = version >= 3 && tag.getBoolean("PresetAuto");
        int[] roles = tag.getIntArray("Roles");
        s.customRoles = new AxleRole[roles.length];
        for (int i = 0; i < roles.length; i++) s.customRoles[i] = AxleRole.byOrdinal(roles[i]);
        int[] driven = tag.getIntArray("Driven");
        s.driven = new boolean[driven.length];
        for (int i = 0; i < driven.length; i++) s.driven[i] = driven[i] != 0;
        s.keymap = tag.contains("Keymap") ? Keymap.fromInts(tag.getIntArray("Keymap")) : Keymap.defaults();
        DriveModel.Options d = DriveModel.Options.DEFAULT;
        s.options = new DriveModel.Options(
                tag.contains("BrakeThenReverse") ? tag.getBoolean("BrakeThenReverse") : d.brakeThenReverse(),
                tag.getBoolean("SpeedSteer"),
                tag.contains("SteerTicks") ? tag.getInt("SteerTicks") : d.steerTicks(),
                tag.contains("Lock") ? tag.getInt("Lock") : d.lockDeg(),
                tag.contains("MaxRpm") ? tag.getInt("MaxRpm") : d.maxRpm(),
                tag.contains("Reverse") ? tag.getInt("Reverse") : d.reversePercent(),
                tag.contains("ThrottleTicks") ? tag.getInt("ThrottleTicks") : d.throttleTicks(),
                tag.contains("ReleaseTicks") ? tag.getInt("ReleaseTicks") : d.releaseTicks(),
                tag.contains("HoldWhenStopped") ? tag.getBoolean("HoldWhenStopped") : d.holdWhenStopped(),
                tag.contains("ParkBrake") ? tag.getBoolean("ParkBrake") : d.parkBrake()).sane();
        s.jetsSteerWithoutBoost = tag.getBoolean("JetsSteerAlone");
        s.liftHold = tag.contains("LiftHold") ? tag.getBoolean("LiftHold") : null;
        s.greeting = !tag.contains("Greeting") || tag.getBoolean("Greeting");
    }

    static CompoundTag couplers(Map<Long, Boolean> states) {
        return booleans(states);
    }

    static Map<Long, Boolean> readCouplers(CompoundTag tag) {
        Map<Long, Boolean> out = new HashMap<>();
        readBooleans(tag, out::put);
        return out;
    }

    private static <V> CompoundTag strings(Map<Long, V> map, java.util.function.Function<V, String> name) {
        CompoundTag out = new CompoundTag();
        map.forEach((k, v) -> out.putString(Long.toString(k), name.apply(v)));
        return out;
    }

    private static CompoundTag booleans(Map<Long, Boolean> map) {
        CompoundTag out = new CompoundTag();
        map.forEach((k, v) -> out.putBoolean(Long.toString(k), v));
        return out;
    }

    private static void readStrings(CompoundTag tag, BiConsumer<Long, String> sink) {
        for (String k : tag.getAllKeys()) {
            Long key = parse(k);
            if (key != null) sink.accept(key, tag.getString(k));
        }
    }

    private static void readBooleans(CompoundTag tag, BiConsumer<Long, Boolean> sink) {
        for (String k : tag.getAllKeys()) {
            Long key = parse(k);
            if (key != null) sink.accept(key, tag.getBoolean(k));
        }
    }

    static CompoundTag doubles(Map<Long, Double> map) {
        CompoundTag tag = new CompoundTag();
        map.forEach((k, v) -> tag.putDouble(Long.toString(k), v));
        return tag;
    }

    static CompoundTag ints(Map<Long, Integer> map) {
        CompoundTag tag = new CompoundTag();
        map.forEach((k, v) -> tag.putInt(Long.toString(k), v));
        return tag;
    }

    static Map<Long, Double> readDoubles(CompoundTag tag) {
        Map<Long, Double> out = new HashMap<>();
        for (String k : tag.getAllKeys()) {
            Long key = parse(k);
            if (key != null) out.put(key, tag.getDouble(k));
        }
        return out;
    }

    static Map<Long, Integer> readInts(CompoundTag tag) {
        Map<Long, Integer> out = new HashMap<>();
        for (String k : tag.getAllKeys()) {
            Long key = parse(k);
            if (key != null) out.put(key, tag.getInt(k));
        }
        return out;
    }

    private static Long parse(String k) {
        try {
            return Long.parseLong(k);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
