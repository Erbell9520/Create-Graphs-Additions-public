package com.createstats.client.ponder.powertrack;

import com.createstats.powertrack.core.FeederPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerTrackPonderStageTest {
    private static final Path STAGES = Path.of("src/main/resources/assets/create_stats/ponder/powertrack");

    record Block(String state, CompoundTag nbt) {
    }

    static Map<BlockPos, Block> load(String stage) throws IOException {
        final CompoundTag root = NbtIo.readCompressed(STAGES.resolve(stage + ".nbt"), NbtAccounter.unlimitedHeap());
        final ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        final List<String> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            final CompoundTag p = palette.getCompound(i);
            final StringBuilder s = new StringBuilder(p.getString("Name"));
            if (p.contains("Properties")) {
                final CompoundTag props = p.getCompound("Properties");
                final Map<String, String> sorted = new TreeMap<>();
                for (String k : props.getAllKeys()) {
                    sorted.put(k, props.getString(k));
                }
                s.append(sorted.toString().replace(" ", "").replace('{', '[').replace('}', ']'));
            }
            states.add(s.toString());
        }
        final Map<BlockPos, Block> blocks = new HashMap<>();
        final ListTag list = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag b = list.getCompound(i);
            final ListTag pos = b.getList("pos", Tag.TAG_INT);
            blocks.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)),
                    new Block(states.get(b.getInt("state")), b.contains("nbt") ? b.getCompound("nbt") : new CompoundTag()));
        }
        return blocks;
    }

    private static String at(Map<BlockPos, Block> blocks, int x, int y, int z) {
        final Block b = blocks.get(new BlockPos(x, y, z));
        return b == null ? "minecraft:air" : b.state();
    }

    private static CompoundTag nbt(Map<BlockPos, Block> blocks, int[] p) {
        final Block b = blocks.get(pos(p));
        return b == null ? new CompoundTag() : b.nbt();
    }

    private static BlockPos pos(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    private static String checker(int dx, int dz) {
        final boolean even = (dx / 3 + dz / 3) % 2 == 0;
        final boolean centre = dx % 3 == 1 && dz % 3 == 1;
        return (even ? centre : !centre) ? "minecraft:white_concrete" : "minecraft:snow_block";
    }

    private static void floor(Map<BlockPos, Block> blocks, int[] size, String stage) {
        for (int x = 0; x < size[0]; x++) {
            for (int z = 0; z < size[1]; z++) {
                assertEquals(checker(x, z), at(blocks, x, 0, z), stage + " floor at " + x + ", " + z);
            }
        }
        assertEquals("minecraft:air", at(blocks, size[0], 0, 0), stage + " floor is wider than its size");
    }

    private static void straight(Map<BlockPos, Block> blocks, int x0, int x1, int y, int z, String stage) {
        for (int x = x0; x <= x1; x++) {
            final String s = at(blocks, x, y, z);
            assertTrue(s.startsWith("create_stats:power_track[") && s.contains("shape=xo"), stage + ": track at " + x + " is " + s);
        }
        assertTrue(at(blocks, x0 - 1, y, z).equals("minecraft:air") && at(blocks, x1 + 1, y, z).equals("minecraft:air"),
                stage + ": the line runs beyond its ends");
    }

    private static void feeder(Map<BlockPos, Block> blocks, int[] p, int lineZ, Direction facing, String name) {
        final String s = at(blocks, p[0], p[1], p[2]);
        assertTrue(s.startsWith("create_stats:track_feeder[") && s.contains("facing=" + facing.getSerializedName()), name + " is " + s);
        final CompoundTag tag = nbt(blocks, p);
        assertEquals("create_stats:track_feeder", tag.getString("id"), name + " block entity");
        assertArrayEquals(new int[] {p[0], PowerTrackStage.TRACK_Y, lineZ}, tag.getIntArray("TargetTrack"), name + " targets its track block");
        assertEquals(PowerTrackPonderBeats.dialValue(FeederPlan.Mode.SUPPLY, PowerTrackPonderBeats.defaultDialFe()),
                tag.getInt("ScrollValue"), name + " starts on Supply at the ladder's default (the generator's DIAL_SUPPLY_DEFAULT)");
        assertEquals(5, tag.getInt("FloorSetting"), name + " starts at the game's 50% floor");
        assertEquals(2, tag.getInt("Reason"), name + " starts NO_RAIL until a scene drives it");
        assertTrue(tag.contains("Id") && tag.getBoolean("Ortho"), name + " carries its track target's keys");
    }

    private static void station(Map<BlockPos, Block> blocks, int[] feeder, int[] cable, int[] bank, int lineZ, int bankFe,
                                Direction facing, String name) {
        feeder(blocks, feeder, lineZ, facing, name);
        final Direction back = facing.getOpposite();
        assertEquals(pos(feeder).relative(back), pos(cable), name + "'s cable lies against its gland (its back)");
        assertEquals(pos(cable).relative(back), pos(bank), name + "'s bank stands against the cable, in the row");
        assertArrayEquals(cable, PowerTrackStage.cableOf(feeder), name + ": the mirror knows its cable, so its door anchors turn");
        assertEquals(PowerTrackStage.TRACK_Y, feeder[1], name + " stands on the plate like the track");
        assertEquals(2, Math.abs(feeder[2] - lineZ), name + " stands two blocks off the line's centre, clear of the sleepers");
        final String c = at(blocks, cable[0], cable[1], cable[2]);
        assertTrue(c.startsWith("create_stats:surface_cable[") && c.contains("down=true"), name + "'s cable is " + c);
        assertTrue(at(blocks, bank[0], bank[1], bank[2]).startsWith("create_stats:capacitor_bank"), name + "'s bank");
        assertEquals(bankFe, nbt(blocks, bank).getInt("Energy"), name + "'s bank charge");
    }

    private static void insulator(Map<BlockPos, Block> blocks, int[] p, int lineZ, String stage) {
        final String s = at(blocks, p[0], p[1], p[2]);
        assertTrue(s.startsWith("create_stats:section_insulator[") && s.contains("facing=north"), stage + ": the insulator is " + s);
        assertEquals(2, lineZ - p[2], stage + ": the insulator stands two blocks north of the line, its board parallel to it");
        final CompoundTag tag = nbt(blocks, p);
        assertEquals("create_stats:section_insulator", tag.getString("id"));
        assertArrayEquals(new int[] {p[0], PowerTrackStage.TRACK_Y, lineZ}, tag.getIntArray("TargetTrack"));
        assertEquals(2, tag.getByte("Status"), stage + ": WORKING");
    }

    private static void car(Map<BlockPos, Block> blocks, int x0, int z, String stage) {
        final int floor = PowerTrackStage.TRACK_Y + 1;
        for (int b : PowerTrackStage.CAR_BOGIES) {
            final String s = at(blocks, x0 + b, floor, z);
            assertTrue(s.startsWith("create:small_bogey[") && s.contains("axis=x"), stage + ": bogie at " + (x0 + b) + " is " + s);
            assertEquals("create:bogey", nbt(blocks, new int[] {x0 + b, floor, z}).getString("id"), stage + ": Create's bogie block entity");
        }
        for (int k : PowerTrackStage.CAR_COLLECTORS) {
            final String s = at(blocks, x0 + k, floor, z);
            assertTrue(s.startsWith("create_stats:current_collector[") && s.contains("axis=x"), stage + ": collector at " + (x0 + k) + " is " + s);
            assertEquals("create_stats:current_collector", nbt(blocks, new int[] {x0 + k, floor, z}).getString("id"));
            final int trail = PowerTrackStage.trailSign(k);
            final int nearest = Math.abs(k - PowerTrackStage.CAR_BOGIES[0]) <= Math.abs(k - PowerTrackStage.CAR_BOGIES[1])
                    ? PowerTrackStage.CAR_BOGIES[0] : PowerTrackStage.CAR_BOGIES[1];
            assertEquals(k >= nearest ? 1 : -1, trail, "the shoe trails away from the nearest bogie");
            assertTrue(Math.abs(k - nearest) >= 2 && Math.abs(k - nearest) <= 3, "two or three blocks from a bogie, as the tooltip says");
        }
        for (int dx = 0; dx < PowerTrackStage.CAR_LENGTH; dx++) {
            final String s = at(blocks, x0 + dx, PowerTrackStage.CAR_DECK_Y, z);
            if (dx == PowerTrackStage.CAR_BANK_X) {
                assertTrue(s.startsWith("create_stats:capacitor_bank"), stage + ": the car's bank at " + dx + " is " + s);
            } else {
                assertEquals("create:railway_casing", s, stage + ": the deck at " + dx);
            }
        }
        assertEquals("minecraft:air", at(blocks, x0 + 1, floor, z), stage + ": air between the bogie and the collector");
    }

    @Test
    void theLineIsTheScenesLine() throws IOException {
        final Map<BlockPos, Block> b = load("line");
        floor(b, PowerTrackStage.LINE_FLOOR, "line");
        assertEquals(PowerTrackStage.LINE_PLATE_X + PowerTrackStage.LINE_PLATE, PowerTrackStage.LINE_X[1] + 1,
                "Ponder's plate ends where the line ends: the plate's x offset and the track agree on where x 0 is");
        straight(b, PowerTrackStage.LINE_X[0], PowerTrackStage.LINE_X[1], PowerTrackStage.TRACK_Y, PowerTrackStage.LINE_Z, "line");
        station(b, PowerTrackStage.FEEDER_A, PowerTrackStage.CABLE_A, PowerTrackStage.BANK_A, PowerTrackStage.LINE_Z,
                PowerTrackPonderBeats.BANK_FE, Direction.WEST, "feeder A");
        station(b, PowerTrackStage.FEEDER_B, PowerTrackStage.CABLE_B, PowerTrackStage.BANK_B, PowerTrackStage.LINE_Z, 0,
                Direction.WEST, "feeder B");

        assertTrue(PowerTrackStage.FEEDER_A[2] < PowerTrackStage.LINE_Z && PowerTrackStage.FEEDER_B[2] < PowerTrackStage.LINE_Z,
                "the line's stations stand north of it, between the camera and the car");
        insulator(b, PowerTrackStage.INSULATOR, PowerTrackStage.LINE_Z, "line");
        car(b, PowerTrackStage.LINE_CAR_X, PowerTrackStage.LINE_Z, "line");

        assertTrue(PowerTrackStage.LINE_CAR_X + PowerTrackStage.CAR_LENGTH <= PowerTrackStage.LINE_X[0] + 1,
                "the car's authored place overlaps the rail");
        assertEquals("minecraft:air", at(b, PowerTrackStage.LINE_CAR_X, PowerTrackStage.TRACK_Y, PowerTrackStage.LINE_Z),
                "no track under the car's authored place: it starts off the line");
    }

    @Test
    void theYardIsTheScenesYard() throws IOException {
        final Map<BlockPos, Block> b = load("yard");
        floor(b, PowerTrackStage.YARD_FLOOR, "yard");
        straight(b, PowerTrackStage.YARD_X[0], PowerTrackStage.YARD_X[1], PowerTrackStage.TRACK_Y, PowerTrackStage.LINE_Z, "yard");
        station(b, PowerTrackStage.YARD_FEEDER_A, PowerTrackStage.YARD_CABLE_A, PowerTrackStage.YARD_BANK_A, PowerTrackStage.LINE_Z,
                PowerTrackPonderBeats.BANK_FE, Direction.NORTH, "yard feeder A");
        station(b, PowerTrackStage.YARD_FEEDER_B, PowerTrackStage.YARD_CABLE_B, PowerTrackStage.YARD_BANK_B, PowerTrackStage.LINE_Z,
                0, Direction.NORTH, "yard feeder B");
        insulator(b, PowerTrackStage.YARD_INSULATOR, PowerTrackStage.LINE_Z, "yard");
        car(b, PowerTrackStage.YARD_CAR_X, PowerTrackStage.LINE_Z, "yard");

        final int[] from = PowerTrackStage.YARD_TURN_FROM;
        final int[] to = PowerTrackStage.YARD_TURN_TO;
        assertTrue(at(b, from[0], from[1], from[2]).contains("turn=true") && at(b, to[0], to[1], to[2]).contains("shape=zo,turn=true"),
                "the curve's two ends carry turn=true");
        final CompoundTag here = nbt(b, from).getList("Connections", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals("create_stats:power", here.getString("Material"), "the curve is Power Track");
        assertTrue(here.getBoolean("Primary"), "the junction's end is the primary");
        final ListTag positions = here.getList("Positions", Tag.TAG_COMPOUND);
        assertArrayEquals(new int[] {to[0] - from[0], to[1] - from[1], to[2] - from[2]}, positions.getCompound(1).getIntArray("Pos"));
        final ListTag starts = here.getList("Starts", Tag.TAG_COMPOUND);
        final ListTag s0 = starts.getCompound(0).getList("V", Tag.TAG_DOUBLE);
        final ListTag s1 = starts.getCompound(1).getList("V", Tag.TAG_DOUBLE);
        assertEquals(1.0, s0.getDouble(0), 1e-9, "starts at this block's east edge");
        assertEquals(0.5, s0.getDouble(2), 1e-9);
        assertEquals(to[0] - from[0] + 0.5, s1.getDouble(0), 1e-9, "ends at the far block's centre line");
        assertEquals(to[2] - from[2] + 1.0, s1.getDouble(2), 1e-9, "ends at the far block's south edge");
        final double[] node = PowerTrackStage.yardNode(PowerTrackStage.YARD_JUNCTION);
        assertEquals(from[0] + s0.getDouble(0), node[0], 1e-9, "the junction node is the curve's start");
        final double[] end = PowerTrackStage.yardNode(PowerTrackStage.YARD_CURVE_END);
        assertEquals(from[0] + s1.getDouble(0), end[0], 1e-9);
        assertEquals(from[2] + s1.getDouble(2), end[2], 1e-9);
        final CompoundTag there = nbt(b, to).getList("Connections", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(!here.getBoolean("Primary"), there.getBoolean("Primary"), "the far end is the secondary");
        assertArrayEquals(new int[] {from[0] - to[0], 0, from[2] - to[2]},
                there.getList("Positions", Tag.TAG_COMPOUND).getCompound(1).getIntArray("Pos"), "the far end points back");
        final int[] branch = PowerTrackStage.YARD_BRANCH_END;
        assertTrue(at(b, branch[0], branch[1], branch[2]).contains("shape=zo"), "the branch runs on north");
        assertEquals(pos(to).north(), pos(branch));

        final int z = PowerTrackStage.YARD_SLOPE_Z;
        for (int x = PowerTrackStage.YARD_SLOPE_LOW[0]; x <= PowerTrackStage.YARD_SLOPE_LOW[1]; x++) {
            assertTrue(at(b, x, PowerTrackStage.TRACK_Y, z).contains("shape=xo"), "level at the foot " + x);
        }
        for (int i = 0; i <= PowerTrackStage.YARD_SLOPE_RISE[1] - PowerTrackStage.YARD_SLOPE_RISE[0]; i++) {
            final String s = at(b, PowerTrackStage.YARD_SLOPE_RISE[0] + i, PowerTrackStage.TRACK_Y + i, z);
            assertTrue(s.contains("shape=ae"), "ascending east at " + (PowerTrackStage.YARD_SLOPE_RISE[0] + i) + ": " + s);
        }
        assertEquals(PowerTrackStage.TRACK_Y + PowerTrackStage.YARD_SLOPE_RISE[1] - PowerTrackStage.YARD_SLOPE_RISE[0] + 1,
                PowerTrackStage.YARD_SLOPE_TOP_Y, "the crest is one block above the last ascending block's foot");
        for (int x = PowerTrackStage.YARD_SLOPE_HIGH[0]; x <= PowerTrackStage.YARD_SLOPE_HIGH[1]; x++) {
            assertTrue(at(b, x, PowerTrackStage.YARD_SLOPE_TOP_Y, z).contains("shape=xo"), "level on top " + x);
        }
        assertEquals(PowerTrackStage.YARD_SLOPE_RISE[0], PowerTrackStage.YARD_SLOPE_LOW[1] + 1, "the slope starts where the level ends");
        assertEquals(PowerTrackStage.YARD_SLOPE_HIGH[0], PowerTrackStage.YARD_SLOPE_RISE[1] + 1, "the top starts where the slope ends");

        assertArrayEquals(new double[] {PowerTrackStage.YARD_SLOPE_RISE[0], PowerTrackStage.TRACK_Y, z + 0.5},
                PowerTrackStage.yardNode(PowerTrackStage.YARD_SLOPE_FOOT), 1e-9);
        assertArrayEquals(new double[] {PowerTrackStage.YARD_SLOPE_RISE[1] + 1, PowerTrackStage.YARD_SLOPE_TOP_Y, z + 0.5},
                PowerTrackStage.yardNode(PowerTrackStage.YARD_SLOPE_CREST), 1e-9);
    }
}
