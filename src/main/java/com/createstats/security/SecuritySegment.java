package com.createstats.security;

import com.createstats.power.FeWireNetwork;
import com.createstats.power.HomeSegment;
import com.createstats.power.SecurityDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SecuritySegment {
    private SecuritySegment() {
    }

    public static final int MAX_ROWS = 128;

    public record Device(BlockPos pos, String name, SecurityDevice.DeviceClass deviceClass,
                         SecurityDevice.DeviceState state, float integrity, int required,
                         String bearing, boolean manual, boolean named, int units) {
        public long key() {
            return pos.asLong();
        }

        public boolean damageable() {
            return integrity >= 0.0f;
        }

        public boolean paired() {
            return units > 1;
        }

        Device merged(Device other) {
            final boolean rated = integrity >= 0.0f && other.integrity() >= 0.0f;
            return new Device(pos, name, deviceClass,
                    state.ordinal() >= other.state().ordinal() ? state : other.state(),
                    rated ? Math.min(integrity, other.integrity()) : -1.0f,
                    required, bearing, manual || other.manual(), named, units + other.units());
        }
    }

    public static List<Device> scan(Level level, BlockPos origin, FeWireNetwork network,
                                    List<BlockPos> manual, SecurityHousehold house) {
        if (level == null || house == null) {
            return List.of();
        }

        final Set<Long> seen = new HashSet<>();
        final List<Device> rows = new ArrayList<>();

        if (network != null) {
            for (long key : network.reachedPositions()) {
                final BlockPos pos = BlockPos.of(key);
                if (pos.equals(origin) || !level.isLoaded(pos) || !seen.add(key)) {
                    continue;
                }
                final Device row = describe(level, origin, pos, house, false);
                if (row != null) {
                    rows.add(row);
                }
            }
        }

        for (BlockPos pos : manual) {
            if (!level.isLoaded(pos) || !seen.add(pos.asLong())) {
                continue;
            }
            final Device row = describe(level, origin, pos, house, true);
            if (row != null) {
                rows.add(row);
            }
        }

        final List<Device> grouped = groupByAnchor(level, rows);
        grouped.sort(order());

        return grouped;
    }

    private static Device describe(Level level, BlockPos origin, BlockPos pos,
                                   SecurityHousehold house, boolean manual) {
        final BlockEntity be = level.getBlockEntity(pos);
        final SecurityDevice device = SecurityDevices.of(be);
        if (device == null) {
            return null;
        }
        if (!answersTo(level, pos, device, house)) {
            return null;
        }

        final SecurityEndpoint key = SecurityIdentity.anchorOf(level, pos);
        final String name = house.nameOf(key, device.deviceName().getString());
        final int required = house.requiredLevel(key);
        return new Device(pos.immutable(), name, device.deviceClass(), device.deviceState(),
                device.integrity(), required, bearing(origin, pos), manual, house.hasName(key), 1);
    }

    private static boolean answersTo(Level level, BlockPos pos, SecurityDevice device,
                                     SecurityHousehold house) {
        final java.util.UUID remembered = device.governingHousehold();
        if (remembered != null) {
            return remembered.equals(house.id()) || SecurityIdentity.free(level, remembered, pos);
        }
        final BlockPos panel = device.governingPanel();
        if (panel == null) {
            return true;
        }
        if (!level.isLoaded(panel)) {
            return house.isEnrolled(SecurityIdentity.anchorOf(level, pos));
        }
        if (!(level.getBlockEntity(panel)
                instanceof com.createstats.blockentity.HomeSecurityPanelBE governor)) {
            return true;
        }
        final java.util.UUID id = governor.householdId();
        return id == null || id.equals(house.id());
    }

    public static boolean devicePresent(Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return true;
        }
        if (SecurityDevices.isDevice(level.getBlockEntity(pos))) {
            return true;
        }
        final net.minecraft.world.level.block.Block block = level.getBlockState(pos).getBlock();
        return block instanceof com.createstats.block.GarageDoorHeaderBlock
                || block instanceof com.createstats.block.GarageDoorBodyBlock
                || block instanceof com.createstats.block.PoweredDoorBlock
                || block instanceof com.createstats.block.BlastShutterBlock;
    }

    public static BlockPos policyAnchor(Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return pos;
        }
        final net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof com.createstats.block.BlastShutterBlock) {
            return com.createstats.security.ShutterBank.anchorOf(level, pos, state);
        }

        if (state.getBlock() instanceof com.createstats.block.GarageDoorHeaderBlock) {
            return HoodRun.anchorOf(level, pos, state);
        }
        if (!(state.getBlock() instanceof com.createstats.block.PoweredDoorBlock)
                || state.getValue(com.createstats.block.PoweredDoorBlock.HALF)
                        != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
            return pos;
        }
        final BlockPos partner = com.createstats.block.PoweredDoorBlock.partnerOf(level, pos, state);
        return partner != null && partner.asLong() < pos.asLong() ? partner.immutable() : pos;
    }

    private static List<Device> groupByAnchor(Level level, List<Device> rows) {
        final Map<Long, Device> byKey = new java.util.HashMap<>(rows.size() * 2);
        for (Device row : rows) {
            byKey.put(row.key(), row);
        }

        final Map<Long, List<Device>> groups = new java.util.LinkedHashMap<>();
        final List<Device> loose = new ArrayList<>(rows.size());
        for (Device row : rows) {
            final BlockPos anchor = policyAnchor(level, row.pos());
            if (byKey.containsKey(anchor.asLong())) {
                groups.computeIfAbsent(anchor.asLong(), key -> new ArrayList<>(2)).add(row);
            } else {
                loose.add(row);
            }
        }
        final List<Device> out = new ArrayList<>(rows.size());
        for (Map.Entry<Long, List<Device>> group : groups.entrySet()) {
            Device folded = byKey.get(group.getKey());
            for (Device member : group.getValue()) {
                if (member.key() != group.getKey()) {
                    folded = folded.merged(member);
                }
            }
            out.add(folded);
        }
        out.addAll(loose);
        return out;
    }

    public static BlockPos pairedWith(Level level, BlockPos pos) {
        return partnerOf(level, pos);
    }

    private static BlockPos partnerOf(Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return null;
        }
        final net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof com.createstats.block.PoweredDoorBlock
                ? com.createstats.block.PoweredDoorBlock.partnerOf(level, pos, state) : null;
    }

    private static Comparator<Device> order() {
        return Comparator
                .comparingInt((Device d) -> d.deviceClass().ordinal())
                .thenComparing(Comparator.comparingInt(Device::required).reversed())
                .thenComparing(Device::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(Device::key);
    }

    public static String bearing(BlockPos origin, BlockPos target) {
        final Component c = HomeSegment.bearing(origin, target);
        return c == null ? "" : c.getString();
    }
}
