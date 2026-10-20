package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.blockentity.BatteryControllerBE;
import com.createstats.blockentity.LampBE;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.power.FePriority;
import com.createstats.power.FeReserveReport;
import com.createstats.power.WireTier;
import com.createstats.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReserveBandGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("create_stats/gametest");

    private ReserveBandGameTest() {
    }

    @GameTest(template = "fe_bench", timeoutTicks = 400)
    public static void aLampsBandIsTenMinutesOfIt(GameTestHelper h) {
        final BlockPos controllerRel = new BlockPos(6, 1, 18);
        final BlockPos bankRel = new BlockPos(9, 1, 21);
        final BlockPos lampRel = new BlockPos(3, 1, 21);
        h.setBlock(bankRel, ModBlocks.CAPACITOR_BANK.get().defaultBlockState());
        h.setBlock(bankRel.above(), ModBlocks.INDUSTRIAL_POWER_CONNECTOR.get().defaultBlockState()
                .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
        h.setBlock(lampRel, ModBlocks.FLOOD_LAMP.get().defaultBlockState());
        h.setBlock(lampRel.above(), ModBlocks.INDUSTRIAL_POWER_CONNECTOR.get().defaultBlockState()
                .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));
        h.setBlock(controllerRel, ModBlocks.FE_BATTERY_CONTROLLER.get().defaultBlockState()
                .setValue(com.createstats.block.BatteryControllerBlock.FACING, Direction.NORTH));
        final BatteryControllerBE controller = h.getBlockEntity(controllerRel);
        final PowerConnectorBE bankConn = h.getBlockEntity(bankRel.above());
        final PowerConnectorBE lampConn = h.getBlockEntity(lampRel.above());
        final LampBE lamp = h.getBlockEntity(lampRel);
        controller.linkStud(2, bankConn, WireTier.HV);
        controller.linkStud(3, lampConn, WireTier.COPPER);
        controller.setOutputPriority(0, FePriority.CRITICAL);
        final IEnergyStorage bank = h.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, h.absolutePos(bankRel), null);
        if (bank == null) {
            h.fail("no energy handler on the bank");
            return;
        }

        h.runAtTickTime(1, () -> {
            int left = bank.getMaxEnergyStored() * 7 / 100;
            for (int i = 0; i < 16 && left > 0; i++) {
                left -= bank.receiveEnergy(left, false);
            }
        });
        h.runAtTickTime(100, () -> {
            final FeReserveReport r = controller.reserveState();
            LOG.info("[BAND-GT] lamp lit: want {}, band {}-{}%, healthy {}%, {} of {}, {}, ordinary {} FE/t",
                    controller.bandCriticalWant(), r.floorPct(), r.ceilingPct(), controller.bandHealthyPct(), r.stored(),
                    r.capacity(), r.stance(), r.sustainedDraw());
            if (controller.bandCriticalWant() != LampBE.drawPerTick()) {
                h.fail("the band is sized on " + controller.bandCriticalWant() + " FE/t of Critical, not the lamp's "
                        + LampBE.drawPerTick());
            }
            if (r.floorPct() != 2 || r.ceilingPct() != 4) {
                h.fail("the band is " + r.floorPct() + "-" + r.ceilingPct() + "%, not ten and thirty minutes of the lamp"
                        + " (2-4%)");
            }
            if (r.stance() != FeReserveReport.Stance.NOMINAL || r.sustainedDraw() <= 0) {
                h.fail("at 7% the banks read " + r.stance() + " offering ordinary load " + r.sustainedDraw()
                        + " FE/t; as shipped that was under a 10% floor, and now it is above a 2% one");
            }
            lamp.setGated(true);
        });
        h.runAtTickTime(200, () -> {
            final FeReserveReport r = controller.reserveState();
            final CompoundTag saved = controller.saveWithoutMetadata(h.getLevel().registryAccess());
            LOG.info("[BAND-GT] lamp off: asking {}, want {}, band {}-{}%, saved want {} at {}",
                    controller.outputDemand(0), controller.bandCriticalWant(), r.floorPct(), r.ceilingPct(),
                    saved.getInt("BandWant"), saved.getLong("BandWantAt"));
            if (controller.outputDemand(0) != 0) {
                h.fail("the lamp is off and its output still asks " + controller.outputDemand(0)
                        + " FE/t, so this proves nothing about the hold");
            }
            if (controller.bandCriticalWant() != LampBE.drawPerTick() || r.floorPct() != 2) {
                h.fail("with the lamp off the band fell to " + controller.bandCriticalWant() + " FE/t and a "
                        + r.floorPct() + "% floor; a Critical machine asking nothing for a while keeps its band a day");
            }
            if (saved.getInt("BandWant") != LampBE.drawPerTick() || saved.getLong("BandWantAt") <= 0) {
                h.fail("the save does not carry the want: " + saved.getInt("BandWant") + " at "
                        + saved.getLong("BandWantAt"));
            }
            if (saved.contains("BandCfg") || controller.isBandCustom()) {
                h.fail("an untouched controller saved a band of its own");
            }

            controller.setBandEdge(0, 30);
        });
        h.runAtTickTime(230, () -> {
            final FeReserveReport r = controller.reserveState();
            final CompoundTag saved = controller.saveWithoutMetadata(h.getLevel().registryAccess());
            LOG.info("[BAND-GT] floor set to 30 min: band {}-{}%, custom {}, saved {}", r.floorPct(), r.ceilingPct(),
                    controller.isBandCustom(), saved.contains("BandCfg"));
            if (r.floorPct() != 4 || !controller.isBandCustom() || !saved.contains("BandCfg")) {
                h.fail("a floor of 30 min of the lamp read " + r.floorPct() + "%, custom " + controller.isBandCustom()
                        + ", saved " + saved.contains("BandCfg") + "; it should be 4%, the controller's own, and saved");
            }

            controller.setBandUnit(com.createstats.power.FeBandSettings.Unit.PERCENT);
        });
        h.runAtTickTime(260, () -> {
            final FeReserveReport r = controller.reserveState();
            LOG.info("[BAND-GT] in percent: band {}-{}%, settings {}", r.floorPct(), r.ceilingPct(),
                    controller.bandSettings());
            if (controller.bandSettings().unit() != com.createstats.power.FeBandSettings.Unit.PERCENT
                    || r.floorPct() != 4) {
                h.fail("switching to percent moved the floor to " + r.floorPct() + "% (" + controller.bandSettings()
                        + ")");
            }
            controller.resetBand();
        });
        h.runAtTickTime(290, () -> {
            final FeReserveReport r = controller.reserveState();
            LOG.info("[BAND-GT] defaults: band {}-{}%, custom {}", r.floorPct(), r.ceilingPct(), controller.isBandCustom());
            if (r.floorPct() != 2 || controller.isBandCustom()) {
                h.fail("Defaults left the floor at " + r.floorPct() + "%, custom " + controller.isBandCustom());
            }
            h.succeed();
        });
    }
}
