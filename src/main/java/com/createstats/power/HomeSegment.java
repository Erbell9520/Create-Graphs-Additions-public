package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// the flood already stops at stud panels, nothing new
public final class HomeSegment {
    private HomeSegment() {
    }

    public static final int MAX_ROWS = 128;

    public enum State {
        ON,

        OFF,

        NO_POWER,

        SHED,

        MISSING;

        public Component label() {
            return Component.translatable("state.create_stats.home." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public static final int MAX_PLUGGED = 8;

    public record Device(BlockPos anchor, List<BlockPos> members, HomeAppliance.Category category,
                         Component name, boolean manual, State state, int mode,
                         List<Component> modes, int drawFe, List<Device> plugged, int floorY, int colour,
                         @org.jetbrains.annotations.Nullable ApplianceStatus status) {
        public int count() {
            return members.size();
        }

        public boolean adjustable() {
            return !modes.isEmpty();
        }
    }

    public static List<Device> scan(Level level, BlockPos origin, FeWireNetwork network,
                                    List<BlockPos> manual, Map<Long, String> customNames) {
        return scanRoster(level, origin, network, manual, customNames).rows();
    }

    public record Roster(List<Device> rows, int dropped) {
    }

    public static Roster scanRoster(Level level, BlockPos origin, FeWireNetwork network,
                                    List<BlockPos> manual, Map<Long, String> customNames) {
        if (level == null) {
            return new Roster(List.of(), 0);
        }
        final Set<Long> manualKeys = new HashSet<>();
        for (BlockPos pos : manual) {
            manualKeys.add(pos.asLong());
        }

        final List<BlockPos> found = new ArrayList<>();
        if (network != null) {
            for (long key : network.reachedPositions()) {
                final BlockPos pos = BlockPos.of(key);
                if (pos.equals(origin) || !level.isLoaded(pos)) {
                    continue;
                }
                if (level.getBlockEntity(pos) instanceof HomeAppliance) {
                    found.add(pos);
                }
            }
        }
        for (BlockPos pos : manual) {
            if (!level.isLoaded(pos)) {
                continue;
            }

            if (level.getBlockEntity(pos) instanceof Gateable
                    && !containsPos(found, pos)) {
                found.add(pos);
            }
        }

        List<Device> rows = group(level, origin, found, manualKeys, customNames);
        rows.sort(order(origin));
        rows = attachPlugged(level, origin, rows, found, manualKeys, customNames);

        for (BlockPos pos : manual) {
            if (!level.isLoaded(pos)) {
                final String custom = customNames.get(pos.asLong());

                final Component name = custom != null && !custom.isEmpty() ? Component.literal(custom)
                        : Component.translatable("gui.create_stats.smart_home.linked_fixture");
                rows.add(new Device(pos.immutable(), List.of(pos.immutable()), HomeAppliance.Category.OTHER, name, true,
                        State.MISSING, 0, List.of(), 0, List.of(), pos.getY() - 1, -1, null));
            }
        }
        final List<Device> kept = cap(rows, MAX_ROWS, origin);
        return new Roster(kept, Math.max(0, rows.size() - kept.size()));
    }

    static List<Device> cap(List<Device> rows, int max, BlockPos origin) {
        if (rows.size() <= max) {
            return rows;
        }
        final List<Device> lamps = new ArrayList<>();
        int others = 0;
        for (Device d : rows) {
            if (d.category() == HomeAppliance.Category.LIGHTING) {
                lamps.add(d);
            } else {
                others++;
            }
        }
        final int lampRoom = Math.max(0, max - others);
        lamps.sort(Comparator.<Device>comparingDouble(d -> d.anchor().distSqr(origin))
                .thenComparingLong(d -> d.anchor().asLong()));
        final Set<Device> keptLamps = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        keptLamps.addAll(lamps.subList(0, Math.min(lampRoom, lamps.size())));
        final List<Device> out = new ArrayList<>(max);
        for (Device d : rows) {
            if (out.size() >= max) {
                break;
            }
            if (d.category() != HomeAppliance.Category.LIGHTING || keptLamps.contains(d)) {
                out.add(d);
            }
        }
        return out;
    }

    private static List<Device> attachPlugged(Level level, BlockPos origin, List<Device> rows,
                                               List<BlockPos> topLevel, Set<Long> manualKeys,
                                               Map<Long, String> customNames) {
        final Set<Long> claimed = new HashSet<>();
        for (BlockPos pos : topLevel) {
            claimed.add(pos.asLong());
        }
        boolean any = false;
        final List<Device> out = new ArrayList<>(rows.size());
        for (Device row : rows) {
            if (row.members().size() != 1
                    || !(level.getBlockEntity(row.anchor()) instanceof PlugHost host)) {
                out.add(row);
                continue;
            }
            final List<Device> children = new ArrayList<>();
            for (BlockPos plug : host.pluggedAppliances(level)) {
                if (children.size() >= MAX_PLUGGED || !claimed.add(plug.asLong())) {
                    continue;
                }
                children.add(describe(level, origin, new ArrayList<>(List.of(plug)), manualKeys,
                        customNames));
            }
            if (children.isEmpty()) {
                out.add(row);
                continue;
            }
            any = true;
            out.add(new Device(row.anchor(), row.members(), row.category(), row.name(), row.manual(),
                    row.state(), row.mode(), row.modes(), row.drawFe(), List.copyOf(children), row.floorY(),
                    row.colour(), row.status()));
        }
        return any ? out : rows;
    }

    private static boolean containsPos(List<BlockPos> list, BlockPos pos) {
        for (BlockPos other : list) {
            if (other.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private static List<Device> group(Level level, BlockPos origin, List<BlockPos> found,
                                      Set<Long> manualKeys, Map<Long, String> customNames) {
        final Map<Long, BlockPos> byKey = new HashMap<>();
        for (BlockPos pos : found) {
            byKey.put(pos.asLong(), pos);
        }
        final Set<Long> seen = new HashSet<>();
        final List<Device> rows = new ArrayList<>();

        for (BlockPos start : found) {
            if (!seen.add(start.asLong())) {
                continue;
            }
            final BlockEntity be = level.getBlockEntity(start);
            final List<BlockPos> members = new ArrayList<>(1);
            members.add(start);
            if (groupable(be)) {
                final net.minecraft.world.level.block.Block kind = level.getBlockState(start).getBlock();
                final Deque<BlockPos> queue = new ArrayDeque<>();
                queue.add(start);
                while (!queue.isEmpty()) {
                    final BlockPos at = queue.poll();
                    for (Direction dir : Direction.values()) {
                        final BlockPos next = at.relative(dir);
                        final long key = next.asLong();
                        if (!byKey.containsKey(key) || seen.contains(key)) {
                            continue;
                        }
                        if (level.getBlockState(next).getBlock() != kind) {
                            continue;
                        }
                        seen.add(key);
                        members.add(byKey.get(key));
                        queue.add(next);
                    }
                }
            }
            rows.add(describe(level, origin, members, manualKeys, customNames));
        }
        return rows;
    }

    private static boolean groupable(BlockEntity be) {
        return be instanceof HomeAppliance appliance
                && appliance.applianceCategory() == HomeAppliance.Category.LIGHTING;
    }

    private static Device describe(Level level, BlockPos origin, List<BlockPos> members,
                                   Set<Long> manualKeys, Map<Long, String> customNames) {
        members.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        final BlockPos anchor = members.get(0);
        final BlockEntity be = level.getBlockEntity(anchor);

        HomeAppliance.Category category = HomeAppliance.Category.OTHER;
        Component name = level.getBlockState(anchor).getBlock().getName();
        if (be instanceof HomeAppliance appliance) {
            category = appliance.applianceCategory();
            name = appliance.applianceName();
        }

        final String custom = customNames.get(anchor.asLong());
        if (custom != null && !custom.isEmpty()) {
            name = Component.literal(custom);
        }

        boolean manual = false;
        for (BlockPos pos : members) {
            manual |= manualKeys.contains(pos.asLong());
        }

        int draw = 0;
        for (BlockPos pos : members) {
            if (level.getBlockEntity(pos) instanceof FeRatedLoad load) {
                draw += Math.max(0, load.measuredDrawFePerTick());
            }
        }

        final List<Component> modes = be instanceof Adjustable adjustable
                ? adjustable.modeLabels() : List.of();
        final int mode = be instanceof Adjustable adjustable ? adjustable.getMode() : 0;

        return new Device(anchor, List.copyOf(members), category, name, manual,
                stateOf(be), mode, modes, draw, List.of(), floorUnder(level, anchor),
                colourOf(level, anchor), statusOf(be));
    }

    @org.jetbrains.annotations.Nullable
    static ApplianceStatus statusOf(BlockEntity be) {
        if (be == null || be.isRemoved()) {
            return null;
        }
        if (be instanceof com.createstats.blockentity.CoffeeMachineBE coffee) {
            return CoffeeMachineStatus.of(coffee);
        }
        return be instanceof HomeAppliance appliance ? appliance.applianceStatus() : null;
    }

    static int colourOf(Level level, BlockPos pos) {
        final net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return state.hasProperty(com.createstats.block.LampBlock.COLOR)
                ? state.getValue(com.createstats.block.LampBlock.COLOR).getId() : -1;
    }

    public static com.createstats.home.RoomFill.Cell classifyColumn(Level level, int x, int f, int z) {
        final BlockPos floor = new BlockPos(x, f, z);
        if (!level.isLoaded(floor)) {
            return com.createstats.home.RoomFill.Cell.OPEN;
        }
        final BlockPos feet = floor.above();
        final net.minecraft.world.level.block.state.BlockState at = level.getBlockState(feet);
        final boolean door = at.is(net.minecraft.tags.BlockTags.DOORS)
                || at.is(net.minecraft.tags.BlockTags.FENCE_GATES) || at.is(net.minecraft.tags.BlockTags.TRAPDOORS);
        final net.minecraft.world.phys.shapes.VoxelShape shape = at.getCollisionShape(level, feet);
        final double feetTop = shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y);
        final boolean sturdy = level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
        return com.createstats.home.ColumnRule.classify(true, door, feetTop, sturdy, roofed(level, x, f, z));
    }

    public static boolean roofed(Level level, int x, int f, int z) {
        return com.createstats.home.ColumnRule.roofed(f + 2,
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z));
    }

    private static final int FLOOR_SEARCH = 12;

    public static int floorUnder(Level level, BlockPos pos) {
        final BlockPos.MutableBlockPos at = pos.mutable();
        for (int i = 1; i <= FLOOR_SEARCH; i++) {
            at.setY(pos.getY() - i);
            if (!level.isLoaded(at)) {
                break;
            }
            final net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
            if (!state.getFluidState().isEmpty()) {
                return at.getY();
            }
            if (state.isFaceSturdy(level, at, Direction.UP)) {
                final BlockPos above = at.above();
                if (above.getY() == pos.getY()
                        || !level.getBlockState(above).isCollisionShapeFullBlock(level, above)) {
                    return at.getY();
                }
            }
        }
        return pos.getY() - 1;
    }

    public record Hint(BlockPos pos, Component name, int floorY) {
    }

    public static final int MAX_HINTS = 12;

    public static List<Hint> unlisted(Level level, BlockPos origin, FeWireNetwork network, List<BlockPos> manual) {
        if (level == null || network == null) {
            return List.of();
        }
        final Set<Long> bound = new HashSet<>();
        for (BlockPos pos : manual) {
            bound.add(pos.asLong());
        }
        final List<BlockPos> found = new ArrayList<>();
        for (long key : network.reachedPositions()) {
            if (bound.contains(key)) {
                continue;
            }
            final BlockPos pos = BlockPos.of(key);
            if (pos.equals(origin) || !level.isLoaded(pos)) {
                continue;
            }
            final BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Gateable && !(be instanceof HomeAppliance)) {
                found.add(pos);
            }
        }
        found.sort(Comparator.<BlockPos>comparingDouble(p -> p.distSqr(origin)).thenComparingLong(BlockPos::asLong));
        final List<Hint> out = new ArrayList<>();
        for (BlockPos pos : found) {
            if (out.size() >= MAX_HINTS) {
                break;
            }
            out.add(new Hint(pos, level.getBlockState(pos).getBlock().getName(), floorUnder(level, pos)));
        }
        return out;
    }

    private static State stateOf(BlockEntity be) {
        if (be == null || be.isRemoved()) {
            return State.MISSING;
        }
        if (be instanceof Gateable gateable && gateable.isGated()) {
            return State.OFF;
        }
        if (be instanceof FeManagedLoad managed && managed.isManagedShed()) {
            return State.SHED;
        }
        if (be instanceof FeRatedLoad load
                && load.ratedDrawFePerTick() > 0 && load.measuredDrawFePerTick() <= 0) {
            return State.NO_POWER;
        }
        return State.ON;
    }

    private static Comparator<Device> order(BlockPos origin) {
        return Comparator.<Device>comparingInt(row -> row.category().ordinal())
                .thenComparingDouble(row -> row.anchor().distSqr(origin))
                .thenComparingLong(row -> row.anchor().asLong());
    }

    public static Component bearing(BlockPos origin, BlockPos target) {
        final int dx = target.getX() - origin.getX();
        final int dz = target.getZ() - origin.getZ();
        final int dy = target.getY() - origin.getY();
        final int flat = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        final String compass = compass(dx, dz);
        if (flat == 0 && dy != 0) {
            return Component.literal((dy > 0 ? "+" : "") + dy + "m");
        }
        return Component.literal(flat + "m " + compass);
    }

    private static String compass(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return "here";
        }

        final int ax = Math.abs(dx);
        final int az = Math.abs(dz);
        final String ns = dz < 0 ? "N" : "S";
        final String ew = dx > 0 ? "E" : "W";
        if (ax == 0 || az > ax * 2) {
            return ns;
        }
        if (az == 0 || ax > az * 2) {
            return ew;
        }
        return ns + ew;
    }
}
