package com.createstats.client.ponder.fe;

import com.createstats.power.ponder.FePonderChapters;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FePonderStageTest {
    private static final Path STAGES = Path.of("src/main/resources/assets/create_stats/ponder/fe");

    record Block(String state, CompoundTag nbt) {
    }

    private static Map<BlockPos, Block> load(String stage) throws IOException {
        final CompoundTag root = NbtIo.readCompressed(STAGES.resolve(stage + ".nbt"), NbtAccounter.unlimitedHeap());
        final ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        final List<String> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            final CompoundTag p = palette.getCompound(i);
            final StringBuilder s = new StringBuilder(p.getString("Name"));
            if (p.contains("Properties")) {
                final CompoundTag props = p.getCompound("Properties");
                final Map<String, String> sorted = new TreeMap<>();
                for (String k : props.getAllKeys()) sorted.put(k, props.getString(k));
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

    private static String at(Map<BlockPos, Block> blocks, BlockPos p) {
        final Block b = blocks.get(p);
        return b == null ? "minecraft:air" : b.state();
    }

    private static void is(Map<BlockPos, Block> blocks, BlockPos p, String prefix) {
        assertTrue(at(blocks, p).startsWith(prefix), p + " is " + at(blocks, p) + ", not " + prefix);
    }

    @Test
    void everyStageStandsOnCreatesChecker() throws IOException {
        final List<String> stages = new ArrayList<>();
        try (var files = Files.list(STAGES)) {
            for (Path f : files.toList()) {
                final String n = f.getFileName().toString();
                if (n.endsWith(".nbt")) stages.add(n.substring(0, n.length() - 4));
            }
        }
        assertFalse(stages.isEmpty(), "no stages found: has the folder moved? This test is then checking nothing.");
        for (String stage : stages) {
            final Map<BlockPos, Block> blocks = load(stage);

            int plate = 0;
            for (BlockPos p : blocks.keySet()) if (p.getY() == 0) plate = Math.max(plate, p.getX() + 1);
            assertEquals(0, plate % 3, stage + " is " + plate + " wide: not a whole number of Create's tiles");
            for (int x = 0; x < plate; x++) {
                for (int z = 0; z < plate; z++) {
                    final boolean even = (x / 3 + z / 3) % 2 == 0;
                    final boolean centre = x % 3 == 1 && z % 3 == 1;
                    final String want = (even != centre) ? "minecraft:snow_block" : "minecraft:white_concrete";
                    assertEquals(want, at(blocks, new BlockPos(x, 0, z)), stage + " floor at " + x + ", " + z);
                }
            }
        }
    }

    @Test
    void theWorkshopMatchesItsConstants() throws IOException {
        final Map<BlockPos, Block> b = load("workshop");
        is(b, FeStage.MOTOR, "create:creative_motor[facing=north]");
        for (BlockPos g : new BlockPos[] {FeStage.GEARBOX_A, FeStage.GEARBOX_B, FeStage.GEARBOX_C}) {
            is(b, g, "create:gearbox[axis=x]");
        }
        is(b, FeStage.SHAFT_AB, "create:shaft[axis=z]");
        is(b, FeStage.SHAFT_BC, "create:shaft[axis=z]");
        is(b, FeStage.CLUTCH, "create:clutch[axis=z,powered=false]");
        is(b, FeStage.LEVER, "minecraft:lever[");
        is(b, FeStage.LEVER_BASE, "create:andesite_casing");
        for (int d = 0; d < FeStage.DYNAMOS.length; d++) {
            is(b, FeStage.DYNAMOS[d], "create_stats:kinetic_dynamo");

            is(b, FeStage.DYNAMOS[d].below(), "create:gearbox");

            final BlockPos duct = FeStage.DUCT[FeStage.DUCT_AT_DYNAMO[d]];
            assertEquals(1, duct.distManhattan(FeStage.DYNAMOS[d]), "duct beside dynamo " + d);
        }
        for (BlockPos p : FeStage.DUCT) {
            assertTrue(at(b, p).startsWith("create_stats:bus_duct[") && at(b, p).contains("down=true"),
                    "duct at " + p + " is " + at(b, p));
            is(b, p.below(), "create:andesite_casing");
        }

        assertEquals(FeStage.PDU.east(), FeStage.DUCT[FeStage.DUCT.length - 1]);
        is(b, FeStage.PDU, "create_stats:power_distribution_unit[facing=north]");
        is(b, FeStage.PEDESTAL, "create:andesite_casing");
        is(b, FeStage.FAN_MOTOR, "create_stats:fe_motor_mk1[facing=west]");
        is(b, FeStage.FAN, "create:encased_fan[facing=west]");
        is(b, FeStage.REPELLER, "create_stats:mob_repeller[facing=north]");
        is(b, FeStage.RANGE, "create_stats:induction_range[");
        is(b, FeStage.MILL_MOTOR, "create_stats:fe_motor_mk1[facing=up]");
        is(b, FeStage.MILL, "create:millstone");
        is(b, FeStage.LAMP, "create_stats:flood_lamp[");

        mounted(b, FeStage.REPELLER_CONN, FeStage.REPELLER, "south");
        mounted(b, FeStage.RANGE_CONN, FeStage.RANGE, "east");
        mounted(b, FeStage.LAMP_CONN, FeStage.LAMP, "east");

        assertTrue(at(b, FeStage.REPELLER).contains("facing=north"), "the repeller faces north, its back south");
    }

    private static void mounted(Map<BlockPos, Block> b, BlockPos conn, BlockPos host, String facing) {
        assertTrue(at(b, conn).startsWith("create_stats:power_connector[") && at(b, conn).contains("facing=" + facing),
                conn + " is " + at(b, conn));
        final BlockPos served = conn.relative(net.minecraft.core.Direction.byName(facing).getOpposite());
        assertEquals(host, served, "the connector at " + conn + " serves " + served);
    }

    @Test
    void theMachinesAreTheReplaysMachines() throws IOException {
        final Map<BlockPos, Block> b = load("workshop");
        final int[] tiers = FePonderChapters.TIERS;
        assertEquals(tiers[FePonderChapters.FAN], b.get(FeStage.FAN_MOTOR).nbt().getInt("Priority"));
        assertEquals(tiers[FePonderChapters.REPELLER], b.get(FeStage.REPELLER).nbt().getInt("Tier"));
        assertEquals(tiers[FePonderChapters.RANGE], b.get(FeStage.RANGE).nbt().getInt("Tier"));
        assertEquals(tiers[FePonderChapters.MILL], b.get(FeStage.MILL_MOTOR).nbt().getInt("Priority"));
        assertEquals(tiers[FePonderChapters.LAMP], b.get(FeStage.LAMP).nbt().getInt("Tier"));

        final List<com.createstats.power.ponder.FePonderPlant.Machine> hero = FePonderChapters.hero(true, true);
        assertEquals(hero.get(FePonderChapters.FAN).draw(),
                (int) Math.round(b.get(FeStage.FAN_MOTOR).nbt().getInt("ScrollValue") * 2.25));
        assertEquals(hero.get(FePonderChapters.MILL).draw(),
                (int) Math.round(b.get(FeStage.MILL_MOTOR).nbt().getInt("ScrollValue") * 2.25));
        assertEquals(hero.get(FePonderChapters.REPELLER).draw(),
                com.createstats.repeller.RepellerDraw.drawFor(b.get(FeStage.REPELLER).nbt().getInt("Radius")));
        assertEquals(3, b.get(FeStage.RANGE).nbt().getInt("Setting"), "the range on high");
        assertEquals(com.createstats.blockentity.InductionRangeBE.DRAW_HIGH, hero.get(FePonderChapters.RANGE).draw());
    }

    @Test
    void everyStudWireIsWrittenOnBothEnds() throws IOException {
        final Map<BlockPos, Block> b = load("workshop");
        final ListTag spans = b.get(FeStage.PDU).nbt().getList("StudWires", Tag.TAG_COMPOUND);
        assertEquals(FeStage.MACHINE.length, spans.size(), "one wire a machine");
        for (int i = 0; i < spans.size(); i++) {
            final CompoundTag e = spans.getCompound(i);
            final int stud = e.getByte("S");
            final BlockPos far = BlockPos.of(e.getLong("P"));
            assertEquals(FeStage.STUD[i], stud, "leg " + i + "'s stud");
            assertEquals(FeStage.LEG_END[i], far, "leg " + i + " lands on its machine's terminal");
            final CompoundTag end = b.get(far).nbt();
            final long[] links = end.getLongArray("Links");
            boolean back = false;
            for (long l : links) back |= BlockPos.of(l).equals(FeStage.PDU);
            assertTrue(back, far + " does not list the board: the span is written on one end only");
            assertEquals(links.length, end.getByteArray("LinkTiers").length, far + " LinkTiers");
            if (at(b, far).startsWith("create_stats:power_connector")) {
                assertEquals(links.length, end.getByteArray("LinkTension").length, far + " LinkTension");
            }
        }
    }

    @Test
    void theSpansMatchItsConstants() throws IOException {
        final Map<BlockPos, Block> b = load("spans");
        is(b, FeStage.SPANS_MOTOR, "create:creative_motor[facing=up]");
        is(b, FeStage.SPANS_DYNAMO, "create_stats:kinetic_dynamo");
        assertEquals(FeStage.SPANS_MOTOR, FeStage.SPANS_DYNAMO.below(), "the dynamo on its motor's shaft");

        final String[] faces = {"west", "south", "up"};
        for (int i = 0; i < FeStage.SPANS_A.length; i++) mounted(b, FeStage.SPANS_A[i], FeStage.SPANS_DYNAMO, faces[i]);
        is(b, FeStage.SPANS_RANGE, "create_stats:induction_range[");
        mounted(b, FeStage.SPANS_B, FeStage.SPANS_RANGE, "east");
        is(b, FeStage.SPANS_LAMP, "create_stats:flood_lamp[");
        mounted(b, FeStage.SPANS_C, FeStage.SPANS_LAMP, "east");
        is(b, FeStage.SPANS_D_POST, "create:andesite_casing");
        mounted(b, FeStage.SPANS_D, FeStage.SPANS_D_POST, "west");
        assertEquals(1, FeStage.SPANS_C.distManhattan(FeStage.SPANS_D), "the pair stands side by side (INV-16)");

        for (BlockPos p : new BlockPos[] {FeStage.SPANS_A[0], FeStage.SPANS_A[1], FeStage.SPANS_A[2], FeStage.SPANS_B,
                FeStage.SPANS_C, FeStage.SPANS_D}) {
            assertEquals(0, b.get(p).nbt().getLongArray("Links").length, p + " carries a span the chapter strings");
        }

        final String[] grade = {"power_connector", "power_connector", "advanced_power_connector",
                "industrial_power_connector"};
        for (int i = 0; i < FeStage.RACK_EAST.length; i++) {
            for (BlockPos end : new BlockPos[] {FeStage.RACK_EAST[i], FeStage.RACK_WEST[i]}) {
                is(b, end, "create_stats:" + grade[i] + "[");
                assertTrue(at(b, end).contains("facing=up"), end + " stands on its post");
                is(b, end.below(), "create:andesite_casing");
                final BlockPos other = end.equals(FeStage.RACK_EAST[i]) ? FeStage.RACK_WEST[i] : FeStage.RACK_EAST[i];
                final CompoundTag nbt = b.get(end).nbt();
                final long[] links = nbt.getLongArray("Links");
                assertEquals(1, links.length, end + " holds one span");
                assertEquals(other, BlockPos.of(links[0]), end + " links to the other end of its row");
                assertEquals(i, nbt.getByteArray("LinkTiers")[0], end + " span's wire grade");
                assertEquals(1, nbt.getByteArray("LinkTension").length, end + " LinkTension");
            }
        }
    }

    @Test
    void theYardMatchesItsConstants() throws IOException {
        final Map<BlockPos, Block> b = load("yard");
        is(b, FeStage.YC, "create_stats:fe_battery_controller[facing=north]");
        final ListTag ports = b.get(FeStage.YC).nbt().getList("Ports", 10);
        assertEquals(6, ports.size(), "every terminal saved");

        final BlockPos[] ends = {null, FeStage.YARD_BANK_ENDS[0], FeStage.YARD_BANK_ENDS[1], FeStage.BOARD_FEEDS[0],
                FeStage.BOARD_FEEDS[1], null};
        for (int p = 0; p < 6; p++) {
            final long[] to = ports.getCompound(p).getLongArray("To");
            if (ends[p] == null) {
                assertEquals(0, to.length, "terminal " + p + " is bare");
                continue;
            }
            assertEquals(1, to.length, "terminal " + p);
            assertEquals(ends[p], BlockPos.of(to[0]), "terminal " + p + "'s wire");
            final long[] back = b.get(ends[p]).nbt().getLongArray("Links");
            assertEquals(1, back.length);
            assertEquals(FeStage.YC, BlockPos.of(back[0]), ends[p] + " links back");
        }
        for (int k = 0; k < 2; k++) {
            is(b, FeStage.YARD_BANKS[k], "create_stats:capacitor_bank[");
            mounted(b, FeStage.YARD_BANK_ENDS[k], FeStage.YARD_BANKS[k], "east");
            assertEquals(1_024_000 * 55 / 100, b.get(FeStage.YARD_BANKS[k]).nbt().getInt("Energy"));
        }
        assertTrue(FeStage.YARD_BANKS[0].distManhattan(FeStage.YARD_BANKS[1]) > 1, "two banks, not one");
        final String[][] kinds = {{"create_stats:induction_range[", "create_stats:flood_lamp["},
                {"create_stats:flood_lamp[", "create_stats:induction_range["}};
        final int[][] tiers = {{0, 2}, {0, 2}};
        for (int k = 0; k < 2; k++) {
            is(b, FeStage.BOARDS[k], "create_stats:power_distribution_unit[facing=north]");

            mounted(b, FeStage.BOARD_FEEDS[k], FeStage.BOARDS[k], "west");
            final CompoundTag board = b.get(FeStage.BOARDS[k]).nbt();
            assertEquals(2, board.getInt("Mode"), "Active Auto");
            final ListTag studs = board.getList("StudWires", 10);
            assertEquals(2, studs.size());
            for (int d = 0; d < 2; d++) {
                is(b, FeStage.BOARD_MACHINES[k][d], kinds[k][d]);
                assertEquals(tiers[k][d], b.get(FeStage.BOARD_MACHINES[k][d]).nbt().getInt("Tier"), "the machine's tier");
                assertTrue(at(b, FeStage.BOARD_ENDS[k][d]).startsWith("create_stats:power_connector[")
                        && at(b, FeStage.BOARD_ENDS[k][d]).contains("facing=west"), FeStage.BOARD_ENDS[k][d] + "");
                assertEquals(FeStage.BOARD_MACHINES[k][d], FeStage.BOARD_ENDS[k][d].east(), "bolted to its machine");
                assertEquals(d, studs.getCompound(d).getByte("S"));
                assertEquals(FeStage.BOARD_ENDS[k][d], BlockPos.of(studs.getCompound(d).getLong("P")), "stud " + d);
                assertEquals(FeStage.BOARDS[k], BlockPos.of(b.get(FeStage.BOARD_ENDS[k][d]).nbt().getLongArray("Links")[0]));
            }
        }
    }

    @Test
    void theControllerStageMatchesItsConstants() throws IOException {
        final Map<BlockPos, Block> b = load("controller");
        is(b, FeStage.BC, "create_stats:fe_battery_controller[facing=north]");
        assertEquals(0, b.get(FeStage.BC).nbt().getList("Ports", 10).size(), "the chapter wires every terminal itself");
        is(b, FeStage.BC_MOTOR, "create:creative_motor[facing=up]");
        assertEquals(FeStage.BC_MOTOR, FeStage.BC_DYNAMO.below(), "the dynamo on its motor's shaft");
        final String[] devices = {"create_stats:kinetic_dynamo", "create_stats:capacitor_bank[",
                "create_stats:capacitor_bank[", "create_stats:induction_range[", "create_stats:flood_lamp[",
                "create_stats:flood_lamp["};
        for (int p = 0; p < 6; p++) {
            is(b, FeStage.BC_DEVICES[p], devices[p]);

            mounted(b, FeStage.BC_ENDS[p], FeStage.BC_DEVICES[p], p < 3 ? "east" : "west");
            assertEquals(0, b.get(FeStage.BC_ENDS[p]).nbt().getLongArray("Links").length, FeStage.BC_ENDS[p]
                    + " carries a wire the chapter strings");
            final boolean input = com.createstats.power.BatteryControllerPorts.isInput(p);
            assertTrue(input ? FeStage.BC_ENDS[p].getX() < FeStage.BC.getX() : FeStage.BC_ENDS[p].getX() > FeStage.BC.getX(),
                    p + " stands on its terminal's side");
        }

        for (int p : new int[] {1, 2, 4, 5}) {
            assertTrue(FeStage.terminal(p).z > FeStage.terminal(p - 1).z, p + "'s terminal behind the one before");
            assertTrue(FeStage.BC_ENDS[p].getZ() > FeStage.BC_ENDS[p - 1].getZ(), p + "'s device behind the one before");
        }

        assertTrue(FeStage.BC_DEVICES[1].distManhattan(FeStage.BC_DEVICES[2]) > 1, "the banks stay two banks");
        for (int k = 1; k <= 2; k++) {
            final CompoundTag nbt = b.get(FeStage.BC_DEVICES[k]).nbt();
            assertEquals(1_024_000 * 65 / 100, nbt.getInt("Energy"));
            assertEquals(1_024_000, nbt.getInt("Cap"));
        }

        assertTrue(FeStage.BC_ENDS[1].asLong() < FeStage.BC_ENDS[2].asLong(), "bank B's connector sorts first");
    }

    @Test
    void theBankStageMatchesItsConstants() throws IOException {
        final Map<BlockPos, Block> b = load("bank");
        is(b, FeStage.BANK_MOTOR, "create:creative_motor[facing=up]");
        is(b, FeStage.BANK_DYNAMO, "create_stats:kinetic_dynamo");
        assertEquals(FeStage.BANK_MOTOR, FeStage.BANK_DYNAMO.below(), "the dynamo on its motor's shaft");
        for (BlockPos p : FeStage.BANK_RUN) {
            assertTrue(at(b, p).startsWith("create_stats:surface_cable[") && at(b, p).contains("down=true"), p + " " + at(b, p));
            is(b, p.below(), "create:andesite_casing");
        }
        assertEquals(1, FeStage.BANK_RUN[0].distManhattan(FeStage.BANK_DYNAMO), "the run starts beside the dynamo");
        is(b, FeStage.BANK_RANGE, "create_stats:induction_range[");
        is(b, FeStage.BANK_RANGE_BASE, "create:andesite_casing");
        assertEquals(1, FeStage.BANK_RANGE.distManhattan(FeStage.BANK_RUN[2]), "the range stands against the run");

        for (BlockPos p : FeStage.BANK_BLOCKS) assertEquals("minecraft:air", at(b, p), p + " is the chapter's bank");
        assertEquals("minecraft:air", at(b, FeStage.SPARE_LINK), "the spare's cable is the chapter's");
        final BlockPos end = FeStage.BANK_RUN[FeStage.BANK_RUN.length - 1];
        boolean touches = false;
        for (BlockPos p : FeStage.BANK_BLOCKS) touches |= p.distManhattan(end) == 1;
        assertTrue(touches, "the run ends against the bank");
        final java.util.List<BlockPos> sorted = new java.util.ArrayList<>(java.util.List.of(FeStage.BANK_BLOCKS));
        sorted.sort(com.createstats.power.FeBankMerge.ORDER);
        assertEquals(java.util.List.of(FeStage.BANK_BLOCKS), sorted, "the bank's members in the merge's own order");

        is(b, FeStage.SPARE, "create_stats:capacitor_bank[");
        assertTrue(at(b, FeStage.SPARE).contains("segment=single"), at(b, FeStage.SPARE));
        is(b, FeStage.SPARE_BASE, "create:andesite_casing");
        is(b, FeStage.SPARE_LINK_BASE, "create:andesite_casing");
        final CompoundTag spare = b.get(FeStage.SPARE).nbt();
        assertEquals(1_024_000, spare.getInt("Energy"), "the spare stands full");
        assertEquals(1_024_000, spare.getInt("Cap"), "and says so: a client's Cap defaults to 1,024");
        for (BlockPos p : FeStage.BANK_RUN) assertTrue(p.distManhattan(FeStage.SPARE) > 1, "the spare starts off the run");
        assertEquals(1, FeStage.SPARE_LINK.distManhattan(FeStage.SPARE), "its cable lands beside it");
        assertEquals(1, FeStage.SPARE_LINK.distManhattan(FeStage.BANK_RUN[1]), "and beside the run");
        for (BlockPos p : FeStage.BANK_BLOCKS) {
            assertTrue(p.distManhattan(FeStage.SPARE) > 1, "the spare never touches the bank: they would merge");
        }
    }

    @Test
    void theBenchMatchesItsConstants() throws IOException {
        final Map<BlockPos, Block> b = load("bench");
        is(b, FeStage.BENCH_MOTOR, "create:creative_motor[facing=up]");
        is(b, FeStage.BENCH_DYNAMO, "create_stats:kinetic_dynamo");
        assertEquals(FeStage.BENCH_MOTOR, FeStage.BENCH_DYNAMO.below(), "the dynamo on its motor's shaft");
        for (BlockPos p : FeStage.BENCH_RUN) {
            assertTrue(at(b, p).startsWith("create_stats:surface_cable[") && at(b, p).contains("down=true"), p + " " + at(b, p));
            is(b, p.below(), "create:andesite_casing");
        }
        assertEquals(1, FeStage.BENCH_RUN[0].distManhattan(FeStage.BENCH_DYNAMO), "the run starts beside the dynamo");
        assertEquals(1, FeStage.BENCH_RUN[FeStage.BENCH_RUN.length - 1].distManhattan(FeStage.BENCH_FAN_MOTOR),
                "and ends beside the machine");
        is(b, FeStage.BENCH_FAN_MOTOR, "create_stats:fe_motor_mk1[facing=up]");
        is(b, FeStage.BENCH_FAN, "create:encased_fan[facing=up]");
        is(b, FeStage.BENCH_PEDESTAL, "create:andesite_casing");
        is(b, FeStage.BENCH_LAMP, "create_stats:flood_lamp[");
        is(b, FeStage.BENCH_LAMP_BASE, "create:andesite_casing");
        assertEquals(1, FeStage.BENCH_LAMP.distManhattan(FeStage.BENCH_RUN[2]), "the lamp is set against the run");
        final String[] grades = {"surface_cable", "cable_tray", "bus_duct", "busbar_trunk"};
        for (int i = 0; i < grades.length; i++) {
            for (int x = FeStage.LADDER_X0; x <= FeStage.LADDER_X1; x++) {
                is(b, new BlockPos(x, 1, FeStage.LADDER_Z[i]), "create_stats:" + grades[i] + "[");
            }
        }
    }
}
