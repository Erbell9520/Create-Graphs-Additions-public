package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.blockentity.UniversalPowerCouplerBE;
import com.createstats.power.PowerCouplerMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CouplerForeignCompatGameTest {
    private static final int MEASURE = 200;
    private static final int SETTLE = 12;

    private static final int SETUP = 5;
    private static final int TIMEOUT = SETTLE + MEASURE + 100;

    private record Mod(String id, String name, String store) {
    }

    private static final Mod NEW_AGE = new Mod("create_new_age", "Create: New Age", "create_new_age:electrical_connector");
    private static final Mod CREATE_ADDITION =
            new Mod("createaddition", "Create Crafts & Additions", "createaddition:modular_accumulator");
    private static final Mod MEKANISM = new Mod("mekanism", "Mekanism", "mekanism:basic_energy_cube");
    private static final Mod POWAH = new Mod("powah", "Powah", "powah:energy_cell_basic");
    private static final Mod IMMERSIVE = new Mod("immersiveengineering", "Immersive Engineering",
            "immersiveengineering:capacitor_lv");
    private static final Mod ENDER_IO = new Mod("enderio", "Ender IO", "enderio:basic_capacitor_bank");

    private CouplerForeignCompatGameTest() {
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void createAdditionBoltedPull(GameTestHelper h) {
        store(h, CREATE_ADDITION, true, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void createAdditionBoltedPush(GameTestHelper h) {
        store(h, CREATE_ADDITION, false, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void createAdditionWiredPull(GameTestHelper h) {
        store(h, CREATE_ADDITION, true, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void createAdditionWiredPush(GameTestHelper h) {
        store(h, CREATE_ADDITION, false, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void mekanismBoltedPull(GameTestHelper h) {
        store(h, MEKANISM, true, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void mekanismBoltedPush(GameTestHelper h) {
        store(h, MEKANISM, false, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void mekanismWiredPull(GameTestHelper h) {
        store(h, MEKANISM, true, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void mekanismWiredPush(GameTestHelper h) {
        store(h, MEKANISM, false, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void powahBoltedPull(GameTestHelper h) {
        store(h, POWAH, true, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void powahBoltedPush(GameTestHelper h) {
        store(h, POWAH, false, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void powahWiredPull(GameTestHelper h) {
        store(h, POWAH, true, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void powahWiredPush(GameTestHelper h) {
        store(h, POWAH, false, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void immersiveBoltedPull(GameTestHelper h) {
        store(h, IMMERSIVE, true, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void immersiveBoltedPush(GameTestHelper h) {
        store(h, IMMERSIVE, false, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void immersiveWiredPull(GameTestHelper h) {
        store(h, IMMERSIVE, true, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void immersiveWiredPush(GameTestHelper h) {
        store(h, IMMERSIVE, false, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void enderIoBoltedPull(GameTestHelper h) {
        store(h, ENDER_IO, true, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void enderIoBoltedPush(GameTestHelper h) {
        store(h, ENDER_IO, false, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void enderIoWiredPull(GameTestHelper h) {
        store(h, ENDER_IO, true, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void enderIoWiredPush(GameTestHelper h) {
        store(h, ENDER_IO, false, true);
    }

    private static void store(GameTestHelper h, Mod mod, boolean pull, boolean wired) {
        final String name = caseName(pull, wired);
        if (skipped(h, mod, name)) {
            return;
        }
        final Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(mod.store()));
        if (block == null || block == Blocks.AIR) {
            fail(h, mod, name, "their block " + mod.store() + " is not registered");
            return;
        }
        final BlockPos cRel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = CouplerGameTest.coupler(h, cRel, Direction.EAST,
                pull ? PowerCouplerMode.PULL : PowerCouplerMode.PUSH);
        final BlockPos theirRel = wired ? new BlockPos(10, 1, 8) : cRel.east();
        h.setBlock(theirRel, block.defaultBlockState());

        final BlockEntity placedBe = h.getLevel().getBlockEntity(h.absolutePos(theirRel));
        if (placedBe != null) {
            placedBe.applyComponentsFromItemStack(new net.minecraft.world.item.ItemStack(block));
            placedBe.setChanged();
            h.getLevel().invalidateCapabilities(h.absolutePos(theirRel));
        }
        final CouplerBench.Store ours = CouplerGameTest.standIn(h, cRel.west(),
                pull ? CouplerBench.Kind.SINK : CouplerBench.Kind.SOURCE);
        final BlockPos theirAbs = h.absolutePos(theirRel);

        final long t0 = h.getTick();
        final long[] start = new long[3];
        final int[] over = {0};
        final int[] peak = {0};
        h.runAfterDelay(SETUP, () -> {
            describe(h, theirAbs, mod, name, "placed");

            if (pull) {
                fill(h, theirAbs);
            } else {
                drain(h, theirAbs);
            }

            prepare(h, theirAbs, wired ? null : Direction.WEST, !pull);
            describe(h, theirAbs, mod, name, "prepared");
            if (wired) {
                CouplerGameTest.wire(h, c, theirRel);
            }
        });
        h.runAfterDelay(SETTLE, () -> {
            start[0] = stored(h, theirAbs);
            start[1] = ours.getEnergyStored();
            start[2] = c.storedFe();
        });
        h.onEachTick(() -> {
            if (h.getTick() - t0 > SETTLE) {
                final int crossing = c.crossingFePerTick();
                peak[0] = Math.max(peak[0], crossing);
                if (crossing > c.ratingFePerTick()) {
                    over[0]++;
                }
            }
        });
        h.runAfterDelay(SETTLE + MEASURE, () -> judge(h, mod, name, c, stored(h, theirAbs) - start[0],
                ours.getEnergyStored() - start[1], c.storedFe() - start[2], pull, peak[0], over[0], 1));
    }

    private static final String CA_LARGE = "createaddition:large_connector";
    private static final String CA_SMALL = "createaddition:connector";
    private static final String CA_CREATIVE = "createaddition:creative_energy";

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void createAdditionConnectorWiredPull(GameTestHelper h) {
        additionConnectorWired(h, true, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void createAdditionConnectorWiredPush(GameTestHelper h) {
        additionConnectorWired(h, false, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void createAdditionCreativeWiredPull(GameTestHelper h) {
        additionConnectorWired(h, true, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void createAdditionNetworkWiredPull(GameTestHelper h) {
        additionNetworkWired(h, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void createAdditionNetworkWiredPush(GameTestHelper h) {
        additionNetworkWired(h, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void createAdditionConnectorBoltedPull(GameTestHelper h) {
        additionConnectorBolted(h, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void createAdditionConnectorBoltedPush(GameTestHelper h) {
        additionConnectorBolted(h, false);
    }

    private static void additionConnectorWired(GameTestHelper h, boolean pull, boolean creative) {
        final String name = (creative ? "creative " : "connector ") + caseName(pull, true);
        if (skipped(h, CREATE_ADDITION, name)) {
            return;
        }
        final BlockPos cRel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = CouplerGameTest.coupler(h, cRel, Direction.EAST,
                pull ? PowerCouplerMode.PULL : PowerCouplerMode.PUSH);
        final CouplerBench.Store ours = CouplerGameTest.standIn(h, cRel.west(),
                pull ? CouplerBench.Kind.SINK : CouplerBench.Kind.SOURCE);
        final BlockPos baseRel = new BlockPos(10, 1, 8);
        final BlockPos connectorRel = baseRel.above();
        if (!placeAddition(h, baseRel, creative ? CA_CREATIVE : CREATE_ADDITION.store(), null, null)
                || !placeAddition(h, connectorRel, CA_LARGE, Direction.DOWN, pull ? "pull" : "push")) {
            fail(h, CREATE_ADDITION, name, "their blocks could not be placed");
            return;
        }
        additionRun(h, name, c, ours, pull, creative ? null : baseRel, connectorRel, () -> {
            if (!creative) {
                fillOrDrain(h, h.absolutePos(baseRel), pull);
            }
            CouplerGameTest.wire(h, c, connectorRel);
        });
    }

    private static void additionNetworkWired(GameTestHelper h, boolean pull) {
        final String name = "network " + caseName(pull, true);
        if (skipped(h, CREATE_ADDITION, name)) {
            return;
        }
        final BlockPos cRel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = CouplerGameTest.coupler(h, cRel, Direction.EAST,
                pull ? PowerCouplerMode.PULL : PowerCouplerMode.PUSH);
        final CouplerBench.Store ours = CouplerGameTest.standIn(h, cRel.west(),
                pull ? CouplerBench.Kind.SINK : CouplerBench.Kind.SOURCE);
        final BlockPos storeRel = new BlockPos(12, 1, 8);
        final BlockPos largeRel = storeRel.above();
        final BlockPos relayRel = new BlockPos(9, 2, 8);
        h.setBlock(relayRel.below(), Blocks.STONE);
        if (!placeAddition(h, storeRel, CREATE_ADDITION.store(), null, null)
                || !placeAddition(h, largeRel, CA_LARGE, Direction.DOWN, pull ? "pull" : "push")
                || !placeAddition(h, relayRel, CA_SMALL, Direction.DOWN, "none")) {
            fail(h, CREATE_ADDITION, name, "their blocks could not be placed");
            return;
        }
        additionRun(h, name, c, ours, pull, storeRel, largeRel, () -> {
            fillOrDrain(h, h.absolutePos(storeRel), pull);
            if (!additionWire(h, relayRel, largeRel)) {
                fail(h, CREATE_ADDITION, name, "their connectors could not be wired by reflection");
                return;
            }
            CouplerGameTest.wire(h, c, relayRel);
        });
    }

    private static void additionConnectorBolted(GameTestHelper h, boolean pull) {
        final String name = "connector " + caseName(pull, false);
        if (skipped(h, CREATE_ADDITION, name)) {
            return;
        }
        final BlockPos cRel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = CouplerGameTest.coupler(h, cRel, Direction.EAST,
                pull ? PowerCouplerMode.PULL : PowerCouplerMode.PUSH);
        final CouplerBench.Store ours = CouplerGameTest.standIn(h, cRel.west(),
                pull ? CouplerBench.Kind.SINK : CouplerBench.Kind.SOURCE);
        final BlockPos boltedRel = cRel.east();
        final BlockPos storeRel = new BlockPos(10, 1, 8);
        final BlockPos farRel = storeRel.above();
        if (!placeAddition(h, boltedRel, CA_LARGE, Direction.WEST, pull ? "push" : "pull")
                || !placeAddition(h, storeRel, CREATE_ADDITION.store(), null, null)
                || !placeAddition(h, farRel, CA_LARGE, Direction.DOWN, pull ? "pull" : "push")) {
            fail(h, CREATE_ADDITION, name, "their blocks could not be placed");
            return;
        }
        additionRun(h, name, c, ours, pull, storeRel, farRel, () -> {
            fillOrDrain(h, h.absolutePos(storeRel), pull);
            if (!additionWire(h, boltedRel, farRel)) {
                fail(h, CREATE_ADDITION, name, "their connectors could not be wired by reflection");
            }
        });
    }

    private static void additionRun(GameTestHelper h, String name, UniversalPowerCouplerBE c, CouplerBench.Store ours,
                                    boolean pull, @Nullable BlockPos storeRel, BlockPos flightRel, Runnable rig) {
        final BlockPos storeAbs = storeRel == null ? null : h.absolutePos(storeRel);
        final BlockPos flightAbs = h.absolutePos(flightRel);
        foreignRun(h, CREATE_ADDITION, name, c, ours, pull, storeAbs == null ? null
                : () -> stored(h, storeAbs) + additionFlight(h, flightAbs), rig, moved -> 0L);
    }

    private static void foreignRun(GameTestHelper h, Mod mod, String name, UniversalPowerCouplerBE c,
                                   CouplerBench.Store ours, boolean pull, @Nullable java.util.function.LongSupplier theirs,
                                   Runnable rig, java.util.function.LongUnaryOperator loss) {
        final long t0 = h.getTick();
        final long[] start = new long[3];
        final int[] over = {0};
        final int[] peak = {0};
        h.runAfterDelay(SETUP, rig);
        h.runAfterDelay(SETTLE, () -> {
            start[0] = theirs == null ? 0 : theirs.getAsLong();
            start[1] = ours.getEnergyStored();
            start[2] = c.storedFe();
            CouplerGameTest.LOG.info("[COUPLER-GT] rig: {} \"{}\" endpoint={} fault={} store={} theirs={} ours={}",
                    mod.id(), name, c.foreignEndpoint(), c.fault(), c.storedFe(), start[0], ours.getEnergyStored());
        });
        h.onEachTick(() -> {
            if (h.getTick() - t0 > SETTLE) {
                final int crossing = c.crossingFePerTick();
                peak[0] = Math.max(peak[0], crossing);
                if (crossing > c.ratingFePerTick()) {
                    over[0]++;
                }
            }
        });
        h.runAfterDelay(SETTLE + MEASURE, () -> {
            final long ourDelta = ours.getEnergyStored() - start[1];
            final long storeDelta = c.storedFe() - start[2];
            final long theirDelta = theirs == null ? -(ourDelta + storeDelta) : theirs.getAsLong() - start[0];
            final long moved = Math.abs(pull ? -theirDelta : theirDelta);
            judge(h, mod, name, c, theirDelta, ourDelta, storeDelta, pull, peak[0], over[0], 1, loss.applyAsLong(moved));
        });
    }

    private static void fillOrDrain(GameTestHelper h, BlockPos abs, boolean fill) {
        if (fill) {
            fill(h, abs);
        } else {
            drain(h, abs);
        }
    }

    private static boolean placeAddition(GameTestHelper h, BlockPos rel, String id, @Nullable Direction facing,
                                         @Nullable String mode) {
        final Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        if (block == null || block == Blocks.AIR) {
            return false;
        }
        BlockState state = block.defaultBlockState();
        if (facing != null) {
            state = with(state, "facing", facing.getSerializedName());
        }
        if (mode != null) {
            state = with(state, "mode", mode);
        }
        h.setBlock(rel, state);
        final BlockPos abs = h.absolutePos(rel);
        final BlockEntity placed = h.getLevel().getBlockEntity(abs);
        if (facing == null && placed != null) {
            placed.applyComponentsFromItemStack(new net.minecraft.world.item.ItemStack(block));
            placed.setChanged();
            h.getLevel().invalidateCapabilities(abs);
        }
        return h.getLevel().getBlockState(abs).is(block);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean additionWire(GameTestHelper h, BlockPos aRel, BlockPos bRel) {
        final BlockPos a = h.absolutePos(aRel);
        final BlockPos b = h.absolutePos(bRel);
        final BlockEntity aBe = h.getLevel().getBlockEntity(a);
        final BlockEntity bBe = h.getLevel().getBlockEntity(b);
        if (aBe == null || bBe == null) {
            return false;
        }
        try {
            final ClassLoader loader = aBe.getClass().getClassLoader();
            final Class<?> node = Class.forName("com.mrh0.createaddition.energy.IWireNode", true, loader);
            final Class<?> wireType = Class.forName("com.mrh0.createaddition.energy.WireType", true, loader);
            final Method available = node.getMethod("getAvailableNode");
            final Method connect = node.getMethod("connect", net.minecraft.world.level.Level.class, BlockPos.class,
                    int.class, BlockPos.class, int.class, wireType);
            final Object result = connect.invoke(null, h.getLevel(), a, available.invoke(aBe), b, available.invoke(bBe),
                    Enum.valueOf((Class) wireType, "GOLD"));
            CouplerGameTest.LOG.info("[COUPLER-GT] rig: createaddition wire {} to {}: {}", a, b, result);
            return result != null && result.toString().startsWith("LINKED");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failed) {
            CouplerGameTest.LOG.info("[COUPLER-GT] createaddition connect failed: {}", failed.toString());
            return false;
        }
    }

    private static long additionFlight(GameTestHelper h, BlockPos connectorAbs) {
        final BlockEntity be = h.getLevel().getBlockEntity(connectorAbs);
        if (be == null) {
            return 0;
        }
        try {
            final Object network = be.getClass().getMethod("getNetwork", int.class).invoke(be, 0);
            if (network == null) {
                return 0;
            }
            long sum = 0;
            for (String half : new String[]{"inBuff", "outBuff"}) {
                final java.lang.reflect.Field f = network.getClass().getDeclaredField(half);
                f.setAccessible(true);
                sum += f.getInt(network);
            }
            return sum;
        } catch (ReflectiveOperationException | RuntimeException failed) {
            return 0;
        }
    }

    private static final String MEK_CABLE = "mekanism:basic_universal_cable";
    private static final String POWAH_CABLE = "powah:energy_cable_basic";
    private static final String EIO_CONDUIT = "enderio:conduit";
    private static final String IE_LV_CONNECTOR = "immersiveengineering:connector_lv";

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void mekanismCableBoltedPull(GameTestHelper h) {
        cableRun(h, MEKANISM, MEK_CABLE, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void mekanismCableBoltedPush(GameTestHelper h) {
        cableRun(h, MEKANISM, MEK_CABLE, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void powahCableBoltedPull(GameTestHelper h) {
        cableRun(h, POWAH, POWAH_CABLE, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void powahCableBoltedPush(GameTestHelper h) {
        cableRun(h, POWAH, POWAH_CABLE, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void enderIoConduitBoltedPull(GameTestHelper h) {
        cableRun(h, ENDER_IO, EIO_CONDUIT, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void enderIoConduitBoltedPush(GameTestHelper h) {
        cableRun(h, ENDER_IO, EIO_CONDUIT, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void immersiveConnectorBoltedPull(GameTestHelper h) {
        immersiveRun(h, true, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void immersiveConnectorBoltedPush(GameTestHelper h) {
        immersiveRun(h, false, false);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void immersiveConnectorWiredPull(GameTestHelper h) {
        immersiveRun(h, true, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void immersiveConnectorWiredPush(GameTestHelper h) {
        immersiveRun(h, false, true);
    }

    private static void cableRun(GameTestHelper h, Mod mod, String cableId, boolean pull) {
        final boolean conduit = cableId.equals(EIO_CONDUIT);
        final String name = (conduit ? "conduit " : "cable ") + caseName(pull, false);
        if (skipped(h, mod, name)) {
            return;
        }
        final BlockPos cRel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = CouplerGameTest.coupler(h, cRel, Direction.EAST,
                pull ? PowerCouplerMode.PULL : PowerCouplerMode.PUSH);
        final CouplerBench.Store ours = CouplerGameTest.standIn(h, cRel.west(),
                pull ? CouplerBench.Kind.SINK : CouplerBench.Kind.SOURCE);
        final BlockPos storeRel = cRel.east(3);
        final List<BlockPos> run = List.of(cRel.east(1), cRel.east(2));
        if (!placeAddition(h, storeRel, mod.store(), null, null)) {
            fail(h, mod, name, "their store " + mod.store() + " could not be placed");
            return;
        }
        for (BlockPos r : run) {
            if (!placeAddition(h, r, cableId, null, null)) {
                fail(h, mod, name, "their " + cableId + " could not be placed");
                return;
            }
        }
        final BlockPos storeAbs = h.absolutePos(storeRel);
        final List<BlockPos> runAbs = run.stream().map(h::absolutePos).toList();
        foreignRun(h, mod, name, c, ours, pull, () -> stored(h, storeAbs) + inFlight(h, runAbs), () -> {
            if (conduit) {
                for (BlockPos r : runAbs) {
                    if (!addEnergyConduit(h, r)) {
                        fail(h, mod, name, "their energy conduit could not be added by reflection");
                        return;
                    }
                }
            }
            fillOrDrain(h, storeAbs, pull);
            prepare(h, storeAbs, Direction.WEST, !pull);
            if (conduit && pull) {
                setIoMode(h, storeAbs, Direction.WEST, "PUSH");
            }
            for (BlockPos r : runAbs) {
                h.getLevel().invalidateCapabilities(r);
                h.getLevel().updateNeighborsAt(r, h.getLevel().getBlockState(r).getBlock());
            }
            h.getLevel().updateNeighborsAt(h.absolutePos(cRel), h.getLevel().getBlockState(h.absolutePos(cRel)).getBlock());
            describe(h, storeAbs, mod, name, "prepared");
            for (BlockPos r : runAbs) {
                describe(h, r, mod, name, "run");
            }
        }, moved -> 0L);
    }

    private static void immersiveRun(GameTestHelper h, boolean pull, boolean wired) {
        final String name = "connector " + caseName(pull, wired);
        if (skipped(h, IMMERSIVE, name)) {
            return;
        }
        final BlockPos cRel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = CouplerGameTest.coupler(h, cRel, Direction.EAST,
                pull ? PowerCouplerMode.PULL : PowerCouplerMode.PUSH);
        final CouplerBench.Store ours = CouplerGameTest.standIn(h, cRel.west(),
                pull ? CouplerBench.Kind.SINK : CouplerBench.Kind.SOURCE);
        final BlockPos storeRel = new BlockPos(9, 1, 5);
        final BlockPos farRel = storeRel.above();
        final BlockPos boltedRel = cRel.east();
        if (!placeAddition(h, storeRel, IMMERSIVE.store(), null, null)
                || !placeAddition(h, farRel, IE_LV_CONNECTOR, Direction.DOWN, null)
                || (!wired && !placeAddition(h, boltedRel, IE_LV_CONNECTOR, Direction.WEST, null))) {
            fail(h, IMMERSIVE, name, "their blocks could not be placed");
            return;
        }
        final BlockPos storeAbs = h.absolutePos(storeRel);
        final BlockPos farAbs = h.absolutePos(farRel);
        final BlockPos boltedAbs = h.absolutePos(boltedRel);
        final List<BlockPos> connectors = wired ? List.of(farAbs) : List.of(farAbs, boltedAbs);
        final Object[] wire = {null};
        foreignRun(h, IMMERSIVE, name, c, ours, pull, () -> stored(h, storeAbs) + immersiveBuffers(h, connectors), () -> {
            fillOrDrain(h, storeAbs, pull);
            prepare(h, storeAbs, Direction.UP, !pull);
            describe(h, storeAbs, IMMERSIVE, name, "prepared");
            if (wired) {
                CouplerGameTest.wire(h, c, farRel);
            } else {
                wire[0] = immersiveWire(h, boltedAbs, farAbs);
                if (wire[0] == null) {
                    fail(h, IMMERSIVE, name, "their connectors could not be wired by reflection");
                }
            }
        }, moved -> immersiveLoss(wire[0], moved));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setIoMode(GameTestHelper h, BlockPos abs, Direction side, String mode) {
        final BlockEntity be = h.getLevel().getBlockEntity(abs);
        if (be == null) {
            return;
        }
        try {
            final ClassLoader loader = be.getClass().getClassLoader();
            final Class<?> configurable = Class.forName("com.enderio.enderio.api.io.IOConfigurable", true, loader);
            final Class<?> ioMode = Class.forName("com.enderio.enderio.api.io.IOMode", true, loader);
            if (!configurable.isInstance(be)) {
                return;
            }
            configurable.getMethod("setIOMode", Direction.class, ioMode).invoke(be, side, Enum.valueOf((Class) ioMode, mode));
            h.getLevel().invalidateCapabilities(abs);
            CouplerGameTest.LOG.info("[COUPLER-GT] rig: {} side {} set to {} with their IO config", key(h, abs), side, mode);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failed) {
            CouplerGameTest.LOG.info("[COUPLER-GT] enderio setIOMode failed: {}", failed.toString());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean addEnergyConduit(GameTestHelper h, BlockPos abs) {
        final BlockEntity be = h.getLevel().getBlockEntity(abs);
        if (be == null) {
            return false;
        }
        try {
            final Class<?> keys = Class.forName("com.enderio.enderio.init.EIOConduits", true, be.getClass().getClassLoader());
            final net.minecraft.resources.ResourceKey key =
                    (net.minecraft.resources.ResourceKey) keys.getField("ENERGY").get(null);
            final net.minecraft.core.Registry registry = h.getLevel().registryAccess().registryOrThrow(key.registryKey());
            final net.minecraft.core.Holder holder = registry.getHolderOrThrow(key);
            for (Method m : be.getClass().getMethods()) {
                if (m.getName().equals("addConduit") && m.getParameterCount() == 3) {
                    final Object result = m.invoke(be, holder, null, null);
                    CouplerGameTest.LOG.info("[COUPLER-GT] rig: enderio conduit at {}: {}", abs, result);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failed) {
            CouplerGameTest.LOG.info("[COUPLER-GT] enderio addConduit failed: {}", failed.toString());
        }
        return false;
    }

    private static long inFlight(GameTestHelper h, List<BlockPos> run) {
        long sum = 0;
        for (BlockPos r : run) {
            sum += stored(h, r);
        }
        return sum;
    }

    @Nullable
    private static Object immersiveWire(GameTestHelper h, BlockPos aAbs, BlockPos bAbs) {
        final BlockEntity a = h.getLevel().getBlockEntity(aAbs);
        final BlockEntity b = h.getLevel().getBlockEntity(bAbs);
        if (a == null || b == null) {
            return null;
        }
        try {
            final ClassLoader loader = a.getClass().getClassLoader();
            final String api = "blusunrize.immersiveengineering.api.wires.";
            final Class<?> global = Class.forName(api + "GlobalWireNetwork", true, loader);
            final Class<?> point = Class.forName(api + "ConnectionPoint", true, loader);
            final Class<?> connection = Class.forName(api + "Connection", true, loader);
            final Class<?> wireType = Class.forName(api + "WireType", true, loader);
            final Class<?> connectable = Class.forName(api + "IImmersiveConnectable", true, loader);
            final Object net = global.getMethod("getNetwork", net.minecraft.world.level.Level.class).invoke(null, h.getLevel());
            final Object copper = wireType.getField("COPPER").get(null);
            final Object pa = point.getConstructor(BlockPos.class, int.class).newInstance(aAbs, 0);
            final Object pb = point.getConstructor(BlockPos.class, int.class).newInstance(bAbs, 0);
            final Object conn = connection.getConstructor(wireType, point, point, global).newInstance(copper, pa, pb, net);
            final Method connect = connectable.getMethod("connectCable", wireType, point, connectable, point);
            connect.invoke(a, copper, pa, b, pb);
            connect.invoke(b, copper, pb, a, pa);
            global.getMethod("addConnection", connection).invoke(net, conn);
            CouplerGameTest.LOG.info("[COUPLER-GT] rig: immersiveengineering copper wire {} to {}, loss rate {}", aAbs,
                    bAbs, immersiveLoss(conn, 1_000_000L));
            return conn;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failed) {
            CouplerGameTest.LOG.info("[COUPLER-GT] immersiveengineering wire failed: {}", failed.toString());
            return null;
        }
    }

    private static long immersiveLoss(@Nullable Object connection, long through) {
        if (connection == null) {
            return 0;
        }
        try {
            final Object wire = connection.getClass().getField("type").get(connection);

            final Class<?> energyWire = Class.forName(
                    "blusunrize.immersiveengineering.api.wires.localhandlers.EnergyTransferHandler$IEnergyWire", true,
                    connection.getClass().getClassLoader());
            if (energyWire.isInstance(wire)) {
                final double rate = ((Number) energyWire.getMethod("getBasicLossRate", connection.getClass())
                        .invoke(wire, connection)).doubleValue();
                return (long) Math.ceil(through * rate * 1.1);
            }
        } catch (ReflectiveOperationException | RuntimeException failed) {
            CouplerGameTest.LOG.info("[COUPLER-GT] immersiveengineering loss rate unreadable: {}", failed.toString());
        }
        return 0;
    }

    private static long immersiveBuffers(GameTestHelper h, List<BlockPos> connectors) {
        long sum = 0;
        for (BlockPos abs : connectors) {
            final BlockEntity be = h.getLevel().getBlockEntity(abs);
            if (be == null) {
                continue;
            }
            for (String field : new String[]{"storageToNet", "storageToMachine"}) {
                try {
                    final java.lang.reflect.Field f = be.getClass().getDeclaredField(field);
                    f.setAccessible(true);
                    final Object storage = f.get(be);
                    if (storage instanceof IEnergyStorage e) {
                        sum += e.getEnergyStored();
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }
        return sum;
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void newAgeBoltedPull(GameTestHelper h) {
        newAgeBolted(h, true);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void newAgeBoltedPush(GameTestHelper h) {
        newAgeBolted(h, false);
    }

    private static void newAgeBolted(GameTestHelper h, boolean pull) {
        final String name = caseName(pull, false);
        if (skipped(h, NEW_AGE, name)) {
            return;
        }
        final BlockPos cRel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = CouplerGameTest.coupler(h, cRel, Direction.EAST,
                pull ? PowerCouplerMode.PULL : PowerCouplerMode.PUSH);
        final CouplerBench.Store ours = CouplerGameTest.standIn(h, cRel.west(),
                pull ? CouplerBench.Kind.SINK : CouplerBench.Kind.SOURCE);
        final BlockPos farSupport = new BlockPos(10, 1, 5);

        final CouplerBench.Store theirs = CouplerGameTest.standIn(h, farSupport,
                pull ? CouplerBench.Kind.BANK : CouplerBench.Kind.SINK);
        if (!placeConnector(h, cRel.east(), Direction.EAST, !pull)
                || !placeConnector(h, farSupport.above(), Direction.UP, pull)) {
            fail(h, NEW_AGE, name, "their electrical connector could not be placed");
            return;
        }
        newAgeRun(h, name, c, ours, theirs, pull, cRel.east(), farSupport.above(), 2);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void newAgeWiredPull(GameTestHelper h) {
        final String name = caseName(true, true);
        if (skipped(h, NEW_AGE, name)) {
            return;
        }

        final BlockPos cRel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = CouplerGameTest.coupler(h, cRel, Direction.EAST, PowerCouplerMode.PULL);
        final CouplerBench.Store ours = CouplerGameTest.standIn(h, cRel.west(), CouplerBench.Kind.SINK);
        final BlockPos nodeRel = new BlockPos(9, 2, 8);
        h.setBlock(nodeRel.below(), Blocks.STONE);
        final BlockPos sourceRel = new BlockPos(12, 1, 8);
        final CouplerBench.Store theirs = CouplerGameTest.standIn(h, sourceRel, CouplerBench.Kind.BANK);
        if (!placeConnector(h, nodeRel, Direction.UP, false) || !placeConnector(h, sourceRel.above(), Direction.UP, true)) {
            fail(h, NEW_AGE, name, "their electrical connector could not be placed");
            return;
        }
        h.runAfterDelay(4, () -> CouplerGameTest.wire(h, c, nodeRel));
        newAgeRun(h, name, c, ours, theirs, true, nodeRel, sourceRel.above(), 1);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = TIMEOUT)
    public static void newAgeWiredPush(GameTestHelper h) {
        final String name = caseName(false, true);
        if (skipped(h, NEW_AGE, name)) {
            return;
        }

        final BlockPos cRel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = CouplerGameTest.coupler(h, cRel, Direction.EAST, PowerCouplerMode.PUSH);
        final CouplerBench.Store ours = CouplerGameTest.standIn(h, cRel.west(), CouplerBench.Kind.SOURCE);
        final BlockPos sinkRel = new BlockPos(9, 1, 8);
        final CouplerBench.Store theirs = CouplerGameTest.standIn(h, sinkRel, CouplerBench.Kind.SINK);
        if (!placeConnector(h, sinkRel.above(), Direction.UP, false)) {
            fail(h, NEW_AGE, name, "their electrical connector could not be placed");
            return;
        }
        h.runAfterDelay(4, () -> CouplerGameTest.wire(h, c, sinkRel.above()));
        newAgeRun(h, name, c, ours, theirs, false, null, null, 1);
    }

    private static void newAgeRun(GameTestHelper h, String name, UniversalPowerCouplerBE c, CouplerBench.Store ours,
                                  CouplerBench.Store theirs, boolean pull, @Nullable BlockPos aRel,
                                  @Nullable BlockPos bRel, int overAllowed) {
        final long t0 = h.getTick();
        if (aRel != null && bRel != null) {
            h.runAfterDelay(3, () -> {
                if (!connect(h, aRel, bRel)) {
                    fail(h, NEW_AGE, name, "their connectors could not be wired by reflection");
                }
            });
        }
        final long[] start = new long[3];
        final int[] over = {0};
        final int[] peak = {0};
        h.runAfterDelay(SETTLE, () -> {
            start[0] = theirs.getEnergyStored();
            start[1] = ours.getEnergyStored();
            start[2] = c.storedFe();
            CouplerGameTest.LOG.info("[COUPLER-GT] rig: create_new_age \"{}\" endpoint={} fault={} theirNodes={}"
                            + " theirPullSources={} theirWire={} store={} theirs={} ours={}", name, c.foreignEndpoint(),
                    c.fault(), c.theirNodes(), c.theirPullSources(), c.theirWire(), c.storedFe(),
                    theirs.getEnergyStored(), ours.getEnergyStored());
        });
        h.onEachTick(() -> {
            if (h.getTick() - t0 > SETTLE) {
                final int crossing = c.crossingFePerTick();
                peak[0] = Math.max(peak[0], crossing);
                if (crossing > c.ratingFePerTick()) {
                    over[0]++;
                }
            }
        });
        h.runAfterDelay(SETTLE + MEASURE, () -> judge(h, NEW_AGE, name, c, theirs.getEnergyStored() - start[0],
                ours.getEnergyStored() - start[1], c.storedFe() - start[2], pull, peak[0], over[0], overAllowed));
    }

    private static void judge(GameTestHelper h, Mod mod, String name, UniversalPowerCouplerBE c, long theirDelta,
                              long ourDelta, long storeDelta, boolean pull, int peak, int over, int overAllowed) {
        judge(h, mod, name, c, theirDelta, ourDelta, storeDelta, pull, peak, over, overAllowed, 0);
    }

    private static void judge(GameTestHelper h, Mod mod, String name, UniversalPowerCouplerBE c, long theirDelta,
                              long ourDelta, long storeDelta, boolean pull, int peak, int over, int overAllowed,
                              long loss) {
        final long moved = pull ? -theirDelta : theirDelta;
        final long error = theirDelta + ourDelta + storeDelta;
        final long tolerance = Math.max(16, Math.abs(moved) / 200);

        final boolean conserved = Math.abs(error) <= tolerance || (error < 0 && -error <= tolerance + Math.max(0, loss));
        final boolean pass = moved >= 1000 && conserved && over <= overAllowed;
        CouplerGameTest.report(mod.id(), mod.name(), version(mod), name, pass, String.format(
                "moved=%d theirs=%+d ours=%+d store=%+d error=%d tolerance=%d theirLoss=%d peak=%d rating=%d over=%d"
                        + " fault=%s endpoint=%s", moved, theirDelta, ourDelta, storeDelta, error, tolerance, loss, peak,
                c.ratingFePerTick(), over, c.fault(), c.foreignEndpoint()));
        if (!pass) {
            h.fail(mod.name() + " " + name + ": moved " + moved + ", conservation error " + error + " (tolerance "
                    + tolerance + "), ticks over the rating " + over + ", fault " + c.fault());
        }
        h.succeed();
    }

    private static boolean skipped(GameTestHelper h, Mod mod, String name) {
        if (ModList.get().isLoaded(mod.id())) {
            return false;
        }
        CouplerGameTest.LOG.info("[COUPLER-GT] mod={} name=\"{}\" version=- case=\"{}\" result=SKIP not loaded",
                mod.id(), mod.name(), name);
        h.succeed();
        return true;
    }

    private static void fail(GameTestHelper h, Mod mod, String name, String why) {
        CouplerGameTest.report(mod.id(), mod.name(), version(mod), name, false, "why=\"" + why + "\"");
        h.fail(mod.name() + " " + name + ": " + why);
    }

    private static String caseName(boolean pull, boolean wired) {
        return (wired ? "wired " : "bolted ") + (pull ? "PULL" : "PUSH");
    }

    private static String version(Mod mod) {
        return ModList.get().getModContainerById(mod.id())
                .map(container -> container.getModInfo().getVersion().toString()).orElse("-");
    }

    @Nullable
    private static IEnergyStorage handler(GameTestHelper h, BlockPos abs, boolean wantsReceive, boolean wantsExtract) {
        IEnergyStorage any = null;
        final Direction[] sides = {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST,
                Direction.WEST};
        for (Direction side : sides) {
            final IEnergyStorage e = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, abs, side);
            if (e == null) {
                continue;
            }
            if (any == null) {
                any = e;
            }
            if ((!wantsReceive || e.canReceive()) && (!wantsExtract || e.canExtract())) {
                return e;
            }
        }
        return any;
    }

    private static long stored(GameTestHelper h, BlockPos abs) {
        final IEnergyStorage e = handler(h, abs, false, false);
        return e == null ? 0 : e.getEnergyStored();
    }

    private static void fill(GameTestHelper h, BlockPos abs) {
        move(h, abs, true);
    }

    private static void drain(GameTestHelper h, BlockPos abs) {
        move(h, abs, false);
    }

    private static void move(GameTestHelper h, BlockPos abs, boolean in) {
        for (Direction side : SIDES) {
            final IEnergyStorage e = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, abs, side);
            for (int i = 0; e != null && i < 400; i++) {
                if ((in ? e.receiveEnergy(100_000, false) : e.extractEnergy(100_000, false)) <= 0) {
                    break;
                }
            }
        }
    }

    private static boolean probe(GameTestHelper h, BlockPos abs, @Nullable Direction side, boolean wantsReceive) {
        final IEnergyStorage e = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, abs, side);
        return e != null && (wantsReceive ? e.receiveEnergy(1000, true) : e.extractEnergy(1000, true)) > 0;
    }

    private static final Direction[] SIDES = {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH,
            Direction.EAST, Direction.WEST};

    private static void prepare(GameTestHelper h, BlockPos abs, @Nullable Direction side, boolean wantsReceive) {
        if (side == null) {
            for (Direction s : SIDES) {
                if (probe(h, abs, s, wantsReceive)) {
                    return;
                }
            }
            toggle(h, abs, Direction.DOWN, wantsReceive);
            return;
        }
        if (probe(h, abs, side, wantsReceive) || orient(h, abs, side, wantsReceive)) {
            return;
        }
        toggle(h, abs, side, wantsReceive);
    }

    private static boolean orient(GameTestHelper h, BlockPos abs, Direction side, boolean wantsReceive) {
        final BlockState placed = h.getLevel().getBlockState(abs);
        for (Property<?> p : placed.getProperties()) {
            if (!p.getName().equals("facing") && !p.getName().equals("horizontal_facing")) {
                continue;
            }
            for (Direction d : Direction.values()) {
                if (p.getValue(d.getSerializedName()).isEmpty()) {
                    continue;
                }
                h.getLevel().setBlock(abs, with(placed, p.getName(), d.getSerializedName()), 3);
                h.getLevel().invalidateCapabilities(abs);
                if (probe(h, abs, side, wantsReceive)) {
                    CouplerGameTest.LOG.info("[COUPLER-GT] rig: turned {} to face {}", key(h, abs), d);
                    return true;
                }
            }
            h.getLevel().setBlock(abs, placed, 3);
            h.getLevel().invalidateCapabilities(abs);
        }
        return false;
    }

    private static void toggle(GameTestHelper h, BlockPos abs, Direction side, boolean wantsReceive) {
        final BlockEntity be = h.getLevel().getBlockEntity(abs);
        if (be == null) {
            return;
        }
        for (Class<?> type = be.getClass(); type != null; type = type.getSuperclass()) {
            for (Method m : type.getDeclaredMethods()) {
                if (!m.getName().equals("toggleSide") || m.getParameterCount() != 2
                        || m.getParameterTypes()[0] != Direction.class) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    final net.minecraft.world.entity.player.Player player =
                            h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                    for (int click = 1; click <= 3; click++) {
                        m.invoke(be, side, player);
                        h.getLevel().invalidateCapabilities(abs);
                        if (probe(h, abs, side, wantsReceive)) {
                            CouplerGameTest.LOG.info("[COUPLER-GT] rig: {} side {} set with their tool in {} click(s)",
                                    key(h, abs), side, click);
                            return;
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException failed) {
                    CouplerGameTest.LOG.info("[COUPLER-GT] rig: toggleSide failed: {}", failed.toString());
                }
                return;
            }
        }
    }

    private static void describe(GameTestHelper h, BlockPos abs, Mod mod, String name, String when) {
        final StringBuilder out = new StringBuilder();
        for (Direction side : SIDES) {
            final IEnergyStorage e = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, abs, side);
            out.append(side == null ? "null" : side.getSerializedName()).append('=');
            if (e == null) {
                out.append("- ");
                continue;
            }
            out.append(e.canReceive() ? 'r' : '.').append(e.canExtract() ? 'x' : '.')
                    .append(':').append(e.receiveEnergy(1000, true)).append('/').append(e.extractEnergy(1000, true))
                    .append('/').append(e.getEnergyStored()).append(' ');
        }
        CouplerGameTest.LOG.info("[COUPLER-GT] rig: {} \"{}\" {} {} [{}]", mod.id(), name, when, key(h, abs),
                out.toString().trim());
    }

    private static String key(GameTestHelper h, BlockPos abs) {
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(h.getLevel().getBlockState(abs).getBlock()));
    }

    private static boolean placeConnector(GameTestHelper h, BlockPos rel, Direction facing, boolean pullMode) {
        final Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(NEW_AGE.store()));
        if (block == null || block == Blocks.AIR) {
            return false;
        }
        BlockState state = with(block.defaultBlockState(), "facing", facing.getSerializedName());
        state = with(state, "mode", pullMode ? "pull" : "inert");
        h.setBlock(rel, state);
        return h.getLevel().getBlockState(h.absolutePos(rel)).is(block);
    }

    private static BlockState with(BlockState state, String property, String value) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals(property)) {
                return set(state, p, value);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
        final Optional<T> parsed = property.getValue(value);
        return parsed.map(v -> state.setValue(property, v)).orElse(state);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean connect(GameTestHelper h, BlockPos aRel, BlockPos bRel) {
        final BlockEntity a = h.getLevel().getBlockEntity(h.absolutePos(aRel));
        final BlockEntity b = h.getLevel().getBlockEntity(h.absolutePos(bRel));
        if (a == null || b == null) {
            return false;
        }
        try {
            final Class<?> wireType = Class.forName("org.antarcticgardens.cna.content.electricity.wire.WireType",
                    true, a.getClass().getClassLoader());
            final Object diamond = Enum.valueOf((Class) wireType, "OVERCHARGED_DIAMOND");
            for (Class<?> type = a.getClass(); type != null; type = type.getSuperclass()) {
                for (Method m : type.getDeclaredMethods()) {
                    if (m.getName().equals("connect") && m.getParameterCount() == 2
                            && m.getParameterTypes()[1] == wireType) {
                        m.setAccessible(true);
                        m.invoke(a, b, diamond);
                        return true;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failed) {
            CouplerGameTest.LOG.info("[COUPLER-GT] New Age connect failed: {}", failed.toString());
        }
        return false;
    }
}
