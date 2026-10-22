package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.blockentity.UniversalPowerCouplerBE;
import com.createstats.power.PduMode;
import com.createstats.power.PowerCouplerMode;
import com.createstats.power.WireTier;
import com.createstats.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CouplerBoardGameTest {
    private static final int LEG = 6;

    private static final int DRAW = 2000;
    private static final int RUN = 600;
    private static final int WARMUP = 120;

    private CouplerBoardGameTest() {
    }

    private static PowerDistributionUnitBE board(GameTestHelper h) {
        final BlockPos pduRel = new BlockPos(3, 1, 3);
        h.setBlock(pduRel, ModBlocks.POWER_DISTRIBUTION_UNIT.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        final PowerDistributionUnitBE pdu = h.getBlockEntity(pduRel);

        final BlockPos spare = new BlockPos(6, 2, 1);
        h.setBlock(spare.below(), Blocks.STONE);
        h.setBlock(spare, ModBlocks.POWER_CONNECTOR.get().defaultBlockState()
                .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
        pdu.linkStud(0, h.<PowerConnectorBE>getBlockEntity(spare), WireTier.COPPER);
        return pdu;
    }

    private static void consume(CouplerBench.Store sink) {
        sink.set(Math.min(sink.getEnergyStored(), sink.getMaxEnergyStored() - DRAW));
    }

    private static CouplerBench.Store machine(GameTestHelper h, BlockPos rel) {
        final CouplerBench.Store sink = CouplerGameTest.standIn(h, rel, CouplerBench.Kind.SINK);
        sink.set(sink.getMaxEnergyStored() - DRAW);
        return sink;
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = RUN + 40)
    public static void anExportingCouplerOnAnActiveAutoLeg(GameTestHelper h) {
        final PowerDistributionUnitBE pdu = board(h);
        final BlockPos connRel = new BlockPos(8, 2, 6);
        h.setBlock(connRel.below(), Blocks.STONE);
        h.setBlock(connRel, ModBlocks.POWER_CONNECTOR.get().defaultBlockState()
                .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
        final PowerConnectorBE conn = h.getBlockEntity(connRel);
        final BlockPos cRel = new BlockPos(12, 1, 6);
        final UniversalPowerCouplerBE c = CouplerGameTest.coupler(h, cRel, Direction.EAST, PowerCouplerMode.PUSH);
        final CouplerBench.Store sink = machine(h, cRel.east());
        pdu.linkStud(LEG, conn, WireTier.COPPER);
        c.addLink(conn.getBlockPos(), WireTier.COPPER);
        conn.addLink(c.getBlockPos(), WireTier.COPPER);
        pdu.setMode(PduMode.AUTO);
        final Direction facing = pdu.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        final IEnergyStorage inCw = pdu.inputHandle(facing.getClockWise());
        final IEnergyStorage inCcw = pdu.inputHandle(facing.getCounterClockWise());
        final int[] trips = {0};
        final int[] carrying = {0};
        final long[] delivered = {0};
        final int[] maxPrice = {0};
        h.onEachTick(() -> {
            final long t = h.getTick();
            inCw.receiveEnergy(150_000, false);
            inCcw.receiveEnergy(150_000, false);
            final int before = sink.getEnergyStored();
            consume(sink);
            delivered[0] += Math.max(0, before - sink.getEnergyStored());
            if (pdu.isLegTripped(LEG)) {
                trips[0]++;
            }
            if (t > WARMUP) {
                maxPrice[0] = Math.max(maxPrice[0], c.ratedDrawFePerTick());
                if (c.exportFePerTick() > 0) {
                    carrying[0]++;
                }
            }
            if (t % 40 == 0) {
                CouplerGameTest.LOG.info("[COUPLER-GT] board PUSH t={} export={} price={} fault={} stored={} leg {}/{} {}",
                        t, c.exportFePerTick(), c.ratedDrawFePerTick(), c.fault(), c.storedFe(),
                        pdu.legAdmitted(LEG), pdu.legDemand(LEG), pdu.isLegTripped(LEG) ? "TRIP" : "");
            }
        });
        h.runAtTickTime(RUN, () -> {
            final int window = RUN - WARMUP;
            final boolean pass = trips[0] == 0 && carrying[0] >= window * 8 / 10 && maxPrice[0] <= DRAW * 2;
            CouplerGameTest.report("create_stats_board", "Power Distribution Unit", "-", "board PUSH", pass,
                    String.format("trips=%d carrying=%d/%d delivered=%d maxPrice=%d rating=%d", trips[0],
                            carrying[0], window, delivered[0], maxPrice[0], c.ratingFePerTick()));
            if (!pass) {
                h.fail("PUSH under Active Auto: trip ticks " + trips[0] + ", exporting " + carrying[0] + " of "
                        + window + " ticks, highest price " + maxPrice[0] + " for a " + DRAW + " FE/t machine");
            }
            h.succeed();
        });
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = RUN + 40)
    public static void anImportingCouplerFeedingAnActiveAutoBoard(GameTestHelper h) {
        final PowerDistributionUnitBE pdu = board(h);
        final Direction facing = pdu.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        final Direction boss = facing.getClockWise();

        final BlockPos cRel = new BlockPos(3, 1, 3).relative(boss);
        final UniversalPowerCouplerBE c = CouplerGameTest.coupler(h, cRel, boss, PowerCouplerMode.PULL);
        CouplerGameTest.standIn(h, cRel.relative(boss), CouplerBench.Kind.SOURCE);

        final BlockPos connRel = new BlockPos(8, 2, 8);
        final CouplerBench.Store sink = machine(h, connRel.below());
        h.setBlock(connRel, ModBlocks.POWER_CONNECTOR.get().defaultBlockState()
                .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
        pdu.linkStud(LEG, h.<PowerConnectorBE>getBlockEntity(connRel), WireTier.COPPER);
        pdu.setMode(PduMode.AUTO);
        final int[] trips = {0};
        final int[] carrying = {0};
        final long[] delivered = {0};
        h.onEachTick(() -> {
            final long t = h.getTick();
            final int before = sink.getEnergyStored();
            consume(sink);
            delivered[0] += Math.max(0, before - sink.getEnergyStored());
            if (pdu.isLegTripped(LEG)) {
                trips[0]++;
            }
            if (t > WARMUP && c.importFePerTick() > 0) {
                carrying[0]++;
            }
            if (t % 40 == 0) {
                CouplerGameTest.LOG.info("[COUPLER-GT] board PULL t={} import={} supply={} fault={} stored={} leg {}/{} {}",
                        t, c.importFePerTick(), c.supplyFePerTick(), c.fault(), c.storedFe(),
                        pdu.legAdmitted(LEG), pdu.legDemand(LEG), pdu.isLegTripped(LEG) ? "TRIP" : "");
            }
        });
        h.runAtTickTime(RUN, () -> {
            final int window = RUN - WARMUP;
            final boolean pass = trips[0] == 0 && carrying[0] >= window * 8 / 10 && delivered[0] > 0;
            CouplerGameTest.report("create_stats_board", "Power Distribution Unit", "-", "board PULL", pass,
                    String.format("trips=%d carrying=%d/%d delivered=%d supply=%d rating=%d", trips[0],
                            carrying[0], window, delivered[0], c.supplyFePerTick(), c.ratingFePerTick()));
            if (!pass) {
                h.fail("PULL into Active Auto: trip ticks " + trips[0] + ", importing " + carrying[0] + " of "
                        + window + " ticks, delivered " + delivered[0]);
            }
            h.succeed();
        });
    }
}
