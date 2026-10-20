package com.createstats.vccmk4;

import com.createstats.CreateStats;
import com.createstats.vccmk4.core.LinkLogic;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.VehicleSetup;
import com.createstats.vehiclelight.HailSequence;
import com.createstats.vehiclelight.VehicleLightBE;
import com.createstats.vehiclelight.VehicleLightKind;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Vcc4Links {
    private final Map<Long, Transmitter> transmitters = new HashMap<>();
    private final Map<Long, LinkLogic.State> states = new HashMap<>();

    private final Map<Long, VehicleLightKind> lamps = new HashMap<>();
    private final Map<Long, Integer> lampStrength = new HashMap<>();

    private final Map<Long, Boolean> savedCouplers = new HashMap<>();
    private boolean warned;

    Map<Long, Boolean> couplerStates(VehicleSetup setup) {
        Map<Long, Boolean> out = new HashMap<>(savedCouplers);
        for (Map.Entry<Long, LinkLogic.State> e : states.entrySet()) {
            if (setup.couplers.contains(e.getKey())) out.put(e.getKey(), e.getValue().on());
        }
        return out;
    }

    void restoreCouplers(Map<Long, Boolean> saved) {
        savedCouplers.clear();
        savedCouplers.putAll(saved);
        for (Map.Entry<Long, Boolean> e : saved.entrySet()) {
            LinkLogic.State s = states.get(e.getKey());
            if (s != null) s.restore(e.getValue());
        }
    }

    void sync(LevelAccessor level, List<Vcc4Scanner.Link> links, VehicleSetup setup) {
        Set<Long> keep = new HashSet<>();
        Set<Long> keptLamps = new HashSet<>();
        for (Vcc4Scanner.Link link : links) {
            long key = link.pos().asLong();
            if (link.isLamp()) {
                if (transmitters.containsKey(key)) remove(level, key);

                keptLamps.add(key);
                keep.add(key);
                lamps.put(key, link.lamp());
                continue;
            }
            if (setup.linkSourceFor(key) == LinkSource.OFF) continue;
            keep.add(key);
            Transmitter existing = transmitters.get(key);
            if (existing != null && existing.key.equals(link.key())) continue;
            if (existing != null) remove(level, key);
            Transmitter t = new Transmitter(link.pos(), link.key());
            transmitters.put(key, t);
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, t);
            } catch (RuntimeException | LinkageError e) {
                transmitters.remove(key);
                warn(e);
            }
        }
        for (Long key : new ArrayList<>(transmitters.keySet())) {
            if (!keep.contains(key)) remove(level, key);
        }
        for (Long key : new ArrayList<>(lamps.keySet())) {
            if (!keptLamps.contains(key)) releaseLamp(level, key);
        }
        states.keySet().retainAll(keep);
    }

    void tick(LevelAccessor level, VehicleSetup setup, LinkLogic.Signals signals) {
        for (Map.Entry<Long, Transmitter> e : transmitters.entrySet()) {
            long key = e.getKey();
            int strength = states.computeIfAbsent(key, k -> {
                LinkLogic.State s = new LinkLogic.State();
                Boolean saved = savedCouplers.remove(k);
                if (saved != null) s.restore(saved);
                return s;
            }).step(setup.linkSourceFor(key), setup.linkToggle(key), signals, setup.couplers.contains(key));
            Transmitter t = e.getValue();
            if (t.strength == strength) continue;
            t.strength = strength;
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, t);
            } catch (RuntimeException | LinkageError ex) {
                warn(ex);
            }
        }
        long now = level.getLevelData().getGameTime();
        for (Map.Entry<Long, VehicleLightKind> e : lamps.entrySet()) {
            long key = e.getKey();
            LinkSource source = setup.linkSourceFor(key);
            int strength = states.computeIfAbsent(key, k -> new LinkLogic.State())
                    .step(source, setup.linkToggle(key), signals, false, e.getValue().flashes());
            strength = LinkLogic.forLamp(source, e.getValue() == VehicleLightKind.WHITE, strength);
            lampStrength.put(key, strength);
            VehicleLightBE lamp = lampAt(level, key);
            if (lamp == null) continue;
            if (source == LinkSource.OFF) lamp.release();
            else lamp.drive(strength, now);
        }
    }

    void hail(LevelAccessor level, long start, Map<Long, HailSequence.Role> roles, Map<Long, HailSequence.Place> places) {
        for (Map.Entry<Long, HailSequence.Role> e : roles.entrySet()) {
            if (e.getValue() == null || !lamps.containsKey(e.getKey())) continue;
            VehicleLightBE lamp = lampAt(level, e.getKey());
            if (lamp != null) lamp.hail(start, e.getValue(), places.getOrDefault(e.getKey(), HailSequence.Place.MIDDLE));
        }
    }

    @Nullable
    private static VehicleLightBE lampAt(LevelAccessor level, long key) {
        BlockPos pos = BlockPos.of(key);
        if (!level.hasChunkAt(pos)) return null;
        return level.getBlockEntity(pos) instanceof VehicleLightBE lamp ? lamp : null;
    }

    private void releaseLamp(LevelAccessor level, long key) {
        lamps.remove(key);
        lampStrength.remove(key);
        VehicleLightBE lamp = lampAt(level, key);
        if (lamp != null) lamp.release();
    }

    int strength(long receiver) {
        Transmitter t = transmitters.get(receiver);
        if (t != null) return t.strength;
        return lampStrength.getOrDefault(receiver, 0);
    }

    void removeAll(LevelAccessor level) {
        for (Long key : new ArrayList<>(transmitters.keySet())) remove(level, key);
        lamps.clear();
        lampStrength.clear();
        states.clear();
    }

    private void remove(LevelAccessor level, long key) {
        Transmitter t = transmitters.remove(key);
        if (t == null) return;
        t.alive = false;
        t.strength = 0;
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, t);
        } catch (RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    private void warn(Throwable e) {
        if (!warned) {
            warned = true;
            CreateStats.LOGGER.warn("[VCC Mk IV] redstone link network unavailable: {}", e.toString());
        }
    }

    private static final class Transmitter implements IRedstoneLinkable {
        private final BlockPos at;
        private final Couple<RedstoneLinkNetworkHandler.Frequency> key;
        private int strength;
        private boolean alive = true;

        Transmitter(BlockPos at, Couple<RedstoneLinkNetworkHandler.Frequency> key) {
            this.at = at.immutable();
            this.key = key;
        }

        @Override
        public int getTransmittedStrength() {
            return alive ? strength : 0;
        }

        @Override
        public void setReceivedStrength(int power) {
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return key;
        }

        @Override
        public BlockPos getLocation() {
            return at;
        }
    }
}
