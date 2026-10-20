package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.blockentity.SparkCrownBE;
import com.createstats.power.PduMode;
import com.createstats.power.WireTier;
import com.createstats.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SparkCrownBoardGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("create_stats/gametest");

    private SparkCrownBoardGameTest() {
    }

    @GameTest(template = "fe_bench", timeoutTicks = 3800)
    public static void theOwnersEvening(GameTestHelper h) {
        final Bench b = Bench.buildControllerFed(h, 2, 1);
        final int[] heldDuringOpen = {Integer.MAX_VALUE};
        h.onEachTick(() -> {
            b.tick();
            final long t = h.getTick();
            if (t == 790) {
                if (b.picks() != 24 || b.held() != 24) {
                    h.fail("on studs 3 and 2 (98,304 FE/t) the crown should hold 24 and refuse the 25th; it has "
                            + b.held() + " of " + b.picks());
                }
                b.assertBoardLegs(1, 2);
            }
            if (t == 800) {
                b.pdu.dropStudWires(2);
                b.pdu.linkStud(0, b.studConnA, WireTier.HV);
            }
            if (t == 1100) {
                b.pdu.setLegClosed(0, true);
            }
            if (t == 1300) {
                b.pdu.setLegClosed(1, true);
            }
            if (t == 1500) {
                b.pdu.setLegClosed(0, true);
                b.assertBoardLegs(0, 1);
            }
            if (t == 1900) {
                b.pdu.setLegClosed(1, false);
            }
            if (t > 1900 && t <= 2100) {
                heldDuringOpen[0] = Math.min(heldDuringOpen[0], b.held());
            }
            if (t == 2100) {
                b.pdu.setLegClosed(1, true);
            }
            if (t == 2300) {
                b.pdu.setLegClosed(0, false);
                b.pdu.setLegClosed(1, false);
            }
            if (t == 2500) {
                if (b.held() != 0) {
                    h.fail("both studs open for ten seconds and the crown still holds " + b.held());
                }
                b.pdu.setLegClosed(0, true);
                b.pdu.setLegClosed(1, true);
            }
            b.log("evening");
        });
        h.runAtTickTime(3700, () -> {
            b.end("evening");
            b.assertNoTrip(0);
            b.assertNoTrip(1);
            b.assertNoTrip(2);
            if (heldDuringOpen[0] < 25) {
                h.fail("with stud 2 open the crown fell to " + heldDuringOpen[0] + " chunks; fed stud 1's 65,536 it should"
                        + " ride its store through ten seconds");
            }
            b.assertHeld(25);
            h.succeed();
        });
    }

    private static final class Bench {
        final GameTestHelper h;
        final PowerDistributionUnitBE pdu;
        final SparkCrownBE crown;
        final PowerConnectorBE studConnA;
        final ServerPlayer player;
        final IEnergyStorage inCw;
        final IEnergyStorage inCcw;
        final int[] trips = new int[3];

        boolean inject = true;

        com.createstats.blockentity.BatteryControllerBE controller;

        IEnergyStorage bank;

        private Bench(GameTestHelper h, PowerDistributionUnitBE pdu, SparkCrownBE crown, PowerConnectorBE studConnA) {
            this.h = h;
            this.pdu = pdu;
            this.crown = crown;
            this.studConnA = studConnA;

            this.player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "crown_tester"));
            final Direction facing = pdu.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
            this.inCw = pdu.inputHandle(facing.getClockWise());
            this.inCcw = pdu.inputHandle(facing.getCounterClockWise());
        }

        static Bench build(GameTestHelper h, int studA, int studB) {
            final BlockPos pduRel = new BlockPos(3, 1, 3);
            h.setBlock(pduRel, ModBlocks.POWER_DISTRIBUTION_UNIT.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));

            final BlockPos a1 = new BlockPos(6, 2, 2);
            final BlockPos b1 = new BlockPos(6, 2, 4);
            final BlockPos a2 = new BlockPos(14, 2, 12);
            final BlockPos b2 = new BlockPos(14, 2, 14);
            for (BlockPos p : new BlockPos[] {a1, b1, a2, b2}) {
                h.setBlock(p.below(), Blocks.STONE);
                h.setBlock(p, ModBlocks.INDUSTRIAL_POWER_CONNECTOR.get().defaultBlockState()
                        .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
            }
            final BlockPos crownRel = new BlockPos(17, 1, 13);
            h.setBlock(crownRel, ModBlocks.SPARK_CROWN.get().defaultBlockState()
                    .setValue(com.createstats.block.SparkCrownBlock.FACING, Direction.SOUTH));
            final PowerDistributionUnitBE pdu = h.getBlockEntity(pduRel);
            final PowerConnectorBE ca1 = h.getBlockEntity(a1);
            final PowerConnectorBE cb1 = h.getBlockEntity(b1);
            final PowerConnectorBE ca2 = h.getBlockEntity(a2);
            final PowerConnectorBE cb2 = h.getBlockEntity(b2);
            final SparkCrownBE crown = h.getBlockEntity(crownRel);

            pdu.linkStud(studA, ca1, WireTier.HV);
            span(ca1, ca2);
            span(ca2, crown);
            pdu.linkStud(studB, cb1, WireTier.HV);
            span(cb1, cb2);
            span(cb2, crown);
            pdu.setMode(PduMode.AUTO);
            return new Bench(h, pdu, crown, ca1);
        }

        static Bench buildControllerFed(GameTestHelper h, int studA, int studB) {
            final Bench b = build(h, studA, studB);
            b.inject = false;
            final BlockPos breakerRel = new BlockPos(1, 1, 20);
            h.setBlock(breakerRel, ModBlocks.CREATIVE_MAIN_BREAKER.get().defaultBlockState()
                    .setValue(com.createstats.block.MainBreakerBlock.FACING, Direction.NORTH)
                    .setValue(com.createstats.block.MainBreakerBlock.POWERED, true));
            final BlockPos genRel = new BlockPos(3, 2, 18);
            final BlockPos bankRel = new BlockPos(9, 1, 21);
            final BlockPos bankConnRel = bankRel.above();
            h.setBlock(genRel.below(), Blocks.STONE);
            h.setBlock(genRel, ModBlocks.INDUSTRIAL_POWER_CONNECTOR.get().defaultBlockState()
                    .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
            h.setBlock(bankRel, ModBlocks.CAPACITOR_BANK.get().defaultBlockState());
            h.setBlock(bankConnRel, ModBlocks.INDUSTRIAL_POWER_CONNECTOR.get().defaultBlockState()
                    .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
            final BlockPos controllerRel = new BlockPos(6, 1, 18);
            h.setBlock(controllerRel, ModBlocks.FE_BATTERY_CONTROLLER.get().defaultBlockState()
                    .setValue(com.createstats.block.BatteryControllerBlock.FACING, Direction.NORTH));

            final Direction facing = b.pdu.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
            final BlockPos pduRel = new BlockPos(3, 1, 3);
            final BlockPos bossCw = pduRel.relative(facing.getClockWise());
            final BlockPos bossCcw = pduRel.relative(facing.getCounterClockWise());
            h.setBlock(bossCw, ModBlocks.INDUSTRIAL_POWER_CONNECTOR.get().defaultBlockState()
                    .setValue(com.createstats.block.PowerConnectorBlock.FACING, facing.getClockWise()));
            h.setBlock(bossCcw, ModBlocks.INDUSTRIAL_POWER_CONNECTOR.get().defaultBlockState()
                    .setValue(com.createstats.block.PowerConnectorBlock.FACING, facing.getCounterClockWise()));
            final BlockPos breaker2Rel = new BlockPos(1, 1, 12);
            final BlockPos gen2Rel = new BlockPos(3, 2, 14);
            h.setBlock(breaker2Rel, ModBlocks.CREATIVE_MAIN_BREAKER.get().defaultBlockState()
                    .setValue(com.createstats.block.MainBreakerBlock.FACING, Direction.NORTH)
                    .setValue(com.createstats.block.MainBreakerBlock.POWERED, true));
            h.setBlock(gen2Rel.below(), Blocks.STONE);
            h.setBlock(gen2Rel, ModBlocks.INDUSTRIAL_POWER_CONNECTOR.get().defaultBlockState()
                    .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
            final com.createstats.blockentity.MainBreakerBE breaker = h.getBlockEntity(breakerRel);
            final com.createstats.blockentity.MainBreakerBE breaker2 = h.getBlockEntity(breaker2Rel);
            final PowerConnectorBE gen2 = h.getBlockEntity(gen2Rel);
            final com.createstats.blockentity.BatteryControllerBE controller = h.getBlockEntity(controllerRel);
            b.controller = controller;
            final PowerConnectorBE gen = h.getBlockEntity(genRel);
            final PowerConnectorBE bankConn = h.getBlockEntity(bankConnRel);
            final PowerConnectorBE outCw = h.getBlockEntity(bossCw);
            final PowerConnectorBE outCcw = h.getBlockEntity(bossCcw);
            breaker.linkStud(0, gen, WireTier.HV);
            controller.linkStud(0, gen, WireTier.HV);
            breaker2.linkStud(0, gen2, WireTier.HV);
            controller.linkStud(0, gen2, WireTier.HV);
            controller.linkStud(2, bankConn, WireTier.HV);
            controller.linkStud(3, outCw, WireTier.HV);
            controller.linkStud(4, outCcw, WireTier.HV);

            b.bank = h.getLevel().getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, h.absolutePos(bankRel), null);
            return b;
        }

        void tick() {
            final long t = h.getTick();
            if (inject) {
                inCw.receiveEnergy(150_000, false);
                inCcw.receiveEnergy(150_000, false);
            }
            if (bank != null && t == 1) {
                for (int i = 0; i < 8 && bank.receiveEnergy(Integer.MAX_VALUE, false) > 0; i++) {
                    LOG.trace("[CROWN-GT] bank charge {}", i);
                }
            }
            if (t == 5) {
                crown.setOwner(player.getUUID(), player.getGameProfile().getName());
            }
            if (t >= 20 && t % 20 == 0 && picks() < 25) {
                final int ox = crown.getBlockPos().getX() >> 4;
                final int oz = crown.getBlockPos().getZ() >> 4;
                com.createstats.chunkloader.core.CrownRegion.nextInSpiral(ox, oz, c -> !crown.hasPick(c))
                        .ifPresent(c -> crown.togglePick(player, c));
            }
            for (int leg = 0; leg < trips.length; leg++) {
                if (pdu.isLegTripped(leg)) {
                    trips[leg]++;
                }
            }
        }

        void log(String tag) {
            final long t = h.getTick();
            if (t % 20 != 10) {
                return;
            }
            final StringBuilder legs = new StringBuilder();
            for (int leg = 0; leg < 3; leg++) {
                legs.append(" | ").append(leg + 1).append(':').append(pdu.legCeiling(leg)).append(' ')
                        .append(pdu.legAdmitted(leg)).append('/').append(pdu.legDemand(leg))
                        .append(pdu.isLegClosed(leg) ? "" : " OPEN").append(pdu.isLegTripped(leg) ? " TRIP" : "");
            }
            if (t % 60 == 10) {
                LOG.info("[CROWN-GT {}] t={} controller {}", tag, t, controllerState());
            }
            LOG.info("[CROWN-GT {}] t={} picks={} held={} store={}/{} in={}{} | blocked={} | report={} | buf={} admit={}"
                            + " crit={} dealSupply={} deal={}", tag, t, picks(), held(), crown.crown().storedFe(),
                    crown.crown().capacityFe(), crown.crown().intakeFePerTick(), legs, blocked(), report(),
                    field(pdu, "energy"),
                    field(pdu, "lastAdmitBudget"), field(pdu, "lastCriticalBudget"), field(pdu, "lastDealSupply"),
                    field(pdu, "lastDeal"));
        }

        String controllerState() {
            if (controller == null) {
                return "-";
            }
            final net.minecraft.nbt.CompoundTag tag = controller.saveWithoutMetadata(h.getLevel().registryAccess());
            final StringBuilder out = new StringBuilder();
            for (String k : new String[] {"In0", "In1", "In2", "Out0", "Out1", "Out2"}) {
                if (tag.contains(k)) {
                    out.append(k).append(tag.getCompound(k)).append(' ');
                }
            }
            out.append("Reserve=").append(tag.getInt("Reserve")).append('/').append(tag.getInt("ReserveCap"))
                    .append(" Gen=").append(tag.getInt("Gen")).append(" Delivered=").append(tag.getInt("Delivered"))
                    .append(" Sustain=").append(tag.getInt("Sustain"));
            return out.toString();
        }

        void end(String tag) {
            LOG.info("[CROWN-GT {}] END picks={} held={} tripTicks 1={} 2={} 3={}", tag, picks(), held(), trips[0],
                    trips[1], trips[2]);
        }

        void assertNoTrip(int leg) {
            if (trips[leg] > 0) {
                h.fail("stud " + (leg + 1) + " tripped for " + trips[leg] + " ticks");
            }
        }

        void assertBoardLegs(int legX, int legY) {
            final Object feeds = field(crown, "boardFeeds");
            if (!(feeds instanceof com.createstats.power.FeStationFeed[] all)) {
                h.fail("no board report on the crown");
                return;
            }
            for (com.createstats.power.FeStationFeed f : all) {
                if (f != null && f.fresh(h.getLevel().getGameTime())) {
                    final boolean match = (f.legA() == legX && f.legB() == legY) || (f.legA() == legY && f.legB() == legX);
                    if (!match) {
                        h.fail("the board reports the crown on studs " + (f.legA() + 1) + " and " + (f.legB() + 1)
                                + ", expected " + (legX + 1) + " and " + (legY + 1));
                    }
                    return;
                }
            }
            h.fail("no fresh board report on the crown");
        }

        void assertHeld(int n) {
            if (held() < n) {
                h.fail("the crown holds " + held() + " of " + picks());
            }
        }

        int picks() {
            final Object p = field(crown, "picks");
            return p instanceof java.util.List<?> l ? l.size() : -1;
        }

        int held() {
            final Object v = field(crown, "held");
            return v instanceof Integer i ? i : -1;
        }

        String report() {
            final Object feeds = field(crown, "boardFeeds");
            if (!(feeds instanceof com.createstats.power.FeStationFeed[] all)) {
                return "-";
            }
            final StringBuilder out = new StringBuilder();
            for (com.createstats.power.FeStationFeed f : all) {
                if (f != null && f.fresh(h.getLevel().getGameTime())) {
                    out.append("A=").append(f.legA() + 1).append(f.liveA() ? "" : "x").append(':').append(f.shareA())
                            .append(" B=").append(f.legB() + 1).append(f.liveB() ? "" : "x").append(':')
                            .append(f.shareB()).append(" carry=").append(f.carried()).append(" cost=")
                            .append(f.cost()).append(' ').append(f.reason());
                }
            }
            return out.length() == 0 ? "-" : out.toString();
        }

        Object blocked() {
            return field(pdu, "lastScreened") instanceof com.createstats.power.PduLegPlan.Screened s ? s.blocked() : null;
        }
    }

    private static void span(com.createstats.power.FeWireTerminal a, com.createstats.power.FeWireTerminal b) {
        a.addLink(b.terminalPos(), WireTier.HV);
        b.addLink(a.terminalPos(), WireTier.HV);
    }

    private static Object field(Object owner, String name) {
        try {
            final java.lang.reflect.Field f = owner.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(owner);
        } catch (ReflectiveOperationException e) {
            return "?" + name;
        }
    }
}
