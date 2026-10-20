package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.blockentity.StormCoilBE;
import com.createstats.power.PduMode;
import com.createstats.power.WireTier;
import com.createstats.registry.ModBlocks;
import com.createstats.stormcoil.StormCoilStuds;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StormCoilBoardGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("create_stats/gametest");

    private static final int LEG = 6;

    private StormCoilBoardGameTest() {
    }

    @GameTest(template = "fe_bench", timeoutTicks = 3000)
    public static void theOwnersCoil(GameTestHelper h) {
        final BlockPos pduRel = new BlockPos(3, 1, 3);
        h.setBlock(pduRel, ModBlocks.POWER_DISTRIBUTION_UNIT.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        final BlockPos connRel = new BlockPos(8, 2, 6);
        h.setBlock(connRel.below(), Blocks.STONE);
        h.setBlock(connRel, ModBlocks.POWER_CONNECTOR.get().defaultBlockState()
                .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
        final BlockPos bigRel = new BlockPos(16, 1, 16);
        h.setBlock(bigRel, ModBlocks.STORM_COIL.get().defaultBlockState()
                .setValue(com.createstats.block.StormCoilBlock.FACING, Direction.NORTH));

        final BlockPos padRel = new BlockPos(12, 0, 10);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                h.setBlock(padRel.offset(dx, 0, dz), Blocks.STONE);
            }
        }
        final BlockPos smallRel = new BlockPos(9, 1, 11);
        h.setBlock(smallRel, ModBlocks.STORM_COIL.get().defaultBlockState()
                .setValue(com.createstats.block.StormCoilBlock.FACING, Direction.EAST));
        final PowerDistributionUnitBE pdu = h.getBlockEntity(pduRel);
        final PowerConnectorBE conn = h.getBlockEntity(connRel);
        final StormCoilBE big = h.getBlockEntity(bigRel);
        final StormCoilBE small = h.getBlockEntity(smallRel);
        final BlockPos spareRel = new BlockPos(6, 2, 1);
        h.setBlock(spareRel.below(), Blocks.STONE);
        h.setBlock(spareRel, ModBlocks.POWER_CONNECTOR.get().defaultBlockState()
                .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
        pdu.linkStud(0, h.<PowerConnectorBE>getBlockEntity(spareRel), WireTier.COPPER);
        pdu.linkStud(LEG, conn, WireTier.SHIELDED);
        big.linkStud(StormCoilStuds.IN, conn, WireTier.SHIELDED);
        small.linkStud(StormCoilStuds.IN, conn, WireTier.COPPER);
        setHeight(big, 4);
        pdu.setMode(PduMode.AUTO);
        final Direction facing = pdu.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        final IEnergyStorage inCw = pdu.inputHandle(facing.getClockWise());
        final IEnergyStorage inCcw = pdu.inputHandle(facing.getCounterClockWise());

        final int[] tripTicks = {0, 0};
        final int[][] arcs = {{0, 0}, {0, 0}};
        final int[] widest = {0};
        final long[] lastZap = {Long.MIN_VALUE, Long.MIN_VALUE};
        final StormCoilBE[] coils = {big, small};
        h.onEachTick(() -> {
            final long t = h.getTick();

            inCw.receiveEnergy(150_000, false);
            inCcw.receiveEnergy(150_000, false);
            if (t % 20 == 0) {
                keepHusks(h, padRel);
            }
            if (t == 1400) {
                pdu.dropStudWires(LEG);
                pdu.linkStud(LEG, conn, WireTier.COPPER);
            }
            final int phase = t < 1400 ? 0 : 1;
            if (pdu.isLegTripped(LEG)) {
                tripTicks[phase]++;
            }
            for (int c = 0; c < coils.length; c++) {
                if (coils[c].lastZap() != lastZap[c]) {
                    lastZap[c] = coils[c].lastZap();
                    if (lastZap[c] != Long.MIN_VALUE) {
                        arcs[c][phase]++;
                        if (c == 0) {
                            widest[0] = Math.max(widest[0], coils[c].volleySize());
                        }
                    }
                }
            }
            if (t % 20 == 10) {
                LOG.info("[COIL-GT] t={} big {} cap={} in={} | small cap={} in={} | stud7 ceil={} adm={}/{} {} | reasons {}"
                                + " | arcs big {}/{} small {}/{} | trips {}/{}",
                        t, big.stage(), big.shownCapacitor(), big.shownIn(), small.shownCapacitor(), small.shownIn(),
                        pdu.legCeiling(LEG), pdu.legAdmitted(LEG), pdu.legDemand(LEG),
                        pdu.isLegTripped(LEG) ? "TRIP" : pdu.isLegClosed(LEG) ? "" : "OPEN",
                        reasons(pdu, big, small), arcs[0][0], arcs[0][1], arcs[1][0], arcs[1][1],
                        tripTicks[0], tripTicks[1]);
            }
            if (t == 1399) {
                LOG.info("[COIL-GT] END phase 1: arcs big {} small {}, trip ticks {}", arcs[0][0], arcs[1][0],
                        tripTicks[0]);
                if (tripTicks[0] > 0) {
                    h.fail("stud 7 tripped for " + tripTicks[0] + " ticks under the owner's stage 4 coil ("
                            + arcs[0][0] + " arcs)");
                }
                if (arcs[0][0] < 20 || arcs[1][0] < 10) {
                    h.fail("in 70 s with husks in reach the coils arced " + arcs[0][0] + " and " + arcs[1][0] + " times");
                }
                if (widest[0] < 2) {
                    h.fail("the stage 4 coil never struck both husks in one volley (widest " + widest[0] + ")");
                }
            }
        });
        h.runAtTickTime(2800, () -> {
            LOG.info("[COIL-GT] END phase 2: arcs big {} small {}, trip ticks {}", arcs[0][1], arcs[1][1], tripTicks[1]);
            if (tripTicks[1] > 0) {
                h.fail("stud 7 in Copper Wire tripped for " + tripTicks[1] + " ticks under two coils");
            }
            if (arcs[0][1] < 15 || arcs[1][1] < 10) {
                h.fail("on a Copper stud the coils arced " + arcs[0][1] + " and " + arcs[1][1] + " times in 70 s:"
                        + " the stud's 8,192 should keep both fighting");
            }
            h.succeed();
        });
    }

    private static String reasons(PowerDistributionUnitBE pdu, StormCoilBE big, StormCoilBE small) {
        try {
            final java.lang.reflect.Field f = PowerDistributionUnitBE.class.getDeclaredField("lastReasons");
            f.setAccessible(true);
            final java.util.Map<?, ?> all = (java.util.Map<?, ?>) f.get(pdu);
            return all.get(big.getBlockPos().asLong()) + "/" + all.get(small.getBlockPos().asLong());
        } catch (ReflectiveOperationException | ClassCastException e) {
            return "?";
        }
    }

    private static void keepHusks(GameTestHelper h, BlockPos padRel) {
        final AABB pad = new AABB(h.absolutePos(padRel)).inflate(3, 3, 3);
        final int alive = h.getLevel().getEntitiesOfClass(Husk.class, pad, Husk::isAlive).size();
        for (int i = alive; i < 2; i++) {
            h.spawnWithNoFreeWill(EntityType.HUSK, padRel.above().offset(i - 1, 0, 0));
        }
    }

    private static void setHeight(StormCoilBE coil, int stage) {
        try {
            final java.lang.reflect.Field f = StormCoilBE.class.getDeclaredField("height");
            f.setAccessible(true);
            ((ScrollValueBehaviour) f.get(coil)).setValue(stage);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no Height behaviour on the Storm Coil", e);
        }
    }
}
