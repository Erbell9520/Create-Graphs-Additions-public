package com.createstats.vccmk4.air;

import com.createstats.CreateStats;
import com.createstats.vccmk4.Vcc4Scanner;
import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.AirLight;
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

final class AerialLinks {
    private final Map<Long, Transmitter> transmitters = new HashMap<>();
    private final Map<Long, AirLight.Latch> latches = new HashMap<>();

    private final Map<Long, Integer> lamps = new HashMap<>();
    private final Map<Long, Integer> lampStrength = new HashMap<>();
    private boolean warned;

    static AerialSetup.Light lightFor(AerialSetup setup, long pos, @Nullable Map<Long, AerialSetup.Light> proposals) {
        AerialSetup.Light proposal = proposals == null ? null : proposals.get(pos);
        return setup.light(pos, proposal == null ? new AerialSetup.Light(AirLight.OFF, false) : proposal);
    }

    static int lampKind(Vcc4Scanner.Link link) {
        VehicleLightKind k = link.lamp();
        if (k == null) return AirLight.RECEIVER;
        return switch (k) {
            case WHITE -> AirLight.WHITE;
            case RED -> AirLight.RED;
            case AMBER -> AirLight.AMBER;
        };
    }

    void sync(LevelAccessor level, List<Vcc4Scanner.Link> links, AerialSetup setup, Map<Long, AerialSetup.Light> proposals) {
        Set<Long> keep = new HashSet<>();
        Set<Long> keptLamps = new HashSet<>();
        for (Vcc4Scanner.Link link : links) {
            long key = link.pos().asLong();
            if (link.lamp() != null) {
                if (transmitters.containsKey(key)) remove(level, key);
                keptLamps.add(key);
                keep.add(key);
                lamps.put(key, lampKind(link));
                continue;
            }
            if (link.key() == null || lightFor(setup, key, proposals).job() == AirLight.OFF) continue;
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
        latches.keySet().retainAll(keep);
    }

    void tick(LevelAccessor level, AerialSetup setup, Map<Long, AerialSetup.Light> proposals, AirLight.Signals signals) {
        for (Map.Entry<Long, Transmitter> e : transmitters.entrySet()) {
            long key = e.getKey();
            int strength = strengthFor(key, setup, proposals, signals, AirLight.RECEIVER);
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
        for (Map.Entry<Long, Integer> e : lamps.entrySet()) {
            long key = e.getKey();
            AerialSetup.Light light = lightFor(setup, key, proposals);
            int strength = strengthFor(key, setup, proposals, signals, e.getValue());
            lampStrength.put(key, strength);
            VehicleLightBE lamp = lampAt(level, key);
            if (lamp == null) continue;
            if (light.job() == AirLight.OFF) lamp.release();
            else lamp.drive(strength, now);
        }
    }

    private int strengthFor(long key, AerialSetup setup, Map<Long, AerialSetup.Light> proposals, AirLight.Signals signals,
                            int lamp) {
        AerialSetup.Light light = lightFor(setup, key, proposals);
        AirLight job = light.job();
        boolean held = false;
        if (job.control != null) {
            held = latches.computeIfAbsent(key, k -> new AirLight.Latch()).step(job.keyHeld(signals),
                    light.toggle() && job.keyed());
        }
        return job.strength(signals, held, lamp);
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

    int strength(long pos) {
        Transmitter t = transmitters.get(pos);
        if (t != null) return t.strength;
        return lampStrength.getOrDefault(pos, 0);
    }

    void darken(LevelAccessor level) {
        for (Transmitter t : transmitters.values()) {
            if (t.strength == 0) continue;
            t.strength = 0;
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, t);
            } catch (RuntimeException | LinkageError ex) {
                warn(ex);
            }
        }
        for (Long key : lamps.keySet()) {
            lampStrength.put(key, 0);
            VehicleLightBE lamp = lampAt(level, key);
            if (lamp != null) lamp.release();
        }
    }

    void removeAll(LevelAccessor level) {
        for (Long key : new ArrayList<>(transmitters.keySet())) remove(level, key);
        lamps.clear();
        lampStrength.clear();
        latches.clear();
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
            CreateStats.LOGGER.warn("[VCC Mk IV Aerial] redstone link network unavailable: {}", e.toString());
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
