package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.block.ProductionDetectorBlock;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.blockentity.inventory.InventoryFace;
import com.createstats.blockentity.inventory.InventorySettings;
import com.createstats.inventory.core.BinCardLedger;
import com.createstats.power.FeLock;
import com.createstats.power.PduMode;
import com.createstats.power.WireTier;
import com.createstats.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InventoryDisplayGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("create_stats/gametest");

    private static final long FILL_ONLY_END = 1700L;
    private static final long DRAIN_END = 3500L;
    private static final long END = 4400L;

    private InventoryDisplayGameTest() {
    }

    @GameTest(template = "fe_bench", timeoutTicks = 4600)
    public static void aVaultFilledAndDrained(GameTestHelper h) {
        final BlockPos pduRel = new BlockPos(3, 1, 3);
        h.setBlock(pduRel, ModBlocks.POWER_DISTRIBUTION_UNIT.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        final BlockPos vaultRel = new BlockPos(10, 2, 10);
        h.setBlock(vaultRel, AllBlocks.ITEM_VAULT.getDefaultState());
        final BlockPos fillRel = vaultRel.above();
        h.setBlock(fillRel, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));

        final BlockPos displayRel = new BlockPos(10, 2, 11);
        h.setBlock(displayRel.below(), Blocks.STONE);
        final BlockState lower = ModBlocks.PRODUCTION_DETECTOR.get().defaultBlockState()
                .setValue(ProductionDetectorBlock.FACING, Direction.NORTH)
                .setValue(ProductionDetectorBlock.HALF, DoubleBlockHalf.LOWER);
        h.setBlock(displayRel, lower);
        h.setBlock(displayRel.above(), lower.setValue(ProductionDetectorBlock.HALF, DoubleBlockHalf.UPPER));

        final BlockPos connRel = displayRel.east();
        h.setBlock(connRel, ModBlocks.POWER_CONNECTOR.get().defaultBlockState()
                .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.EAST));

        final BlockPos spareRel = new BlockPos(6, 2, 1);
        h.setBlock(spareRel.below(), Blocks.STONE);
        h.setBlock(spareRel, ModBlocks.POWER_CONNECTOR.get().defaultBlockState()
                .setValue(com.createstats.block.PowerConnectorBlock.FACING, Direction.UP));

        final PowerDistributionUnitBE pdu = h.getBlockEntity(pduRel);
        pdu.linkStud(0, h.<PowerConnectorBE>getBlockEntity(connRel), WireTier.COPPER);
        pdu.linkStud(1, h.<PowerConnectorBE>getBlockEntity(spareRel), WireTier.COPPER);
        pdu.setMode(PduMode.AUTO);
        final Direction facing = pdu.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        final IEnergyStorage inCw = pdu.inputHandle(facing.getClockWise());
        final IEnergyStorage inCcw = pdu.inputHandle(facing.getCounterClockWise());

        final ProductionDetectorBE display = h.getBlockEntity(displayRel);

        display.setMarks(InventorySettings.CONTAINER, 90, 95);

        final Map<Long, long[]> atSecond = new HashMap<>();
        final int[] tripTicks = {0};
        final boolean[] checked = {false, false};
        final long[] darkAt = {Long.MIN_VALUE};
        final boolean[] relayWhileCoasting = {true};
        final BlockPos sinkRel = vaultRel.below(2);
        h.onEachTick(() -> {
            final long t = h.getTick();
            final long now = h.getLevel().getGameTime();

            inCw.receiveEnergy(150_000, false);
            inCcw.receiveEnergy(150_000, false);
            final HopperBlockEntity fill = h.getBlockEntity(fillRel);
            if (fill.getItem(0).getCount() < 16) {
                fill.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
            }
            if (pdu.isLegTripped(0)) {
                tripTicks[0]++;
            }
            if (now % 20L == 0L) {
                atSecond.put(now / 20L, new long[]{count(h, vaultRel), t >= FILL_ONLY_END ? count(h, sinkRel) : 0L});
            }
            if (t == FILL_ONLY_END) {
                h.setBlock(sinkRel, Blocks.CHEST);
                h.setBlock(vaultRel.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
            }
            final InventoryFace f = display.serverFace();
            if (t % 100L == 0L) {
                LOG.info("[INV-GT] t={} powered={} lock={} page={} fill={} in={} out={} vault={} relay={} comp={} trips={}",
                        t, display.feConsumer().isPowered(), display.feConsumer().lock(), f == null ? "-" : f.page,
                        f == null ? "-" : f.fill, f == null ? "-" : f.inPerMinute, f == null ? "-" : f.outPerMinute,
                        count(h, vaultRel), display.lowRelayOn(), display.comparatorLevel(), tripTicks[0]);
            }

            if (!checked[0] && t >= FILL_ONLY_END - 40 && now % 20L == 10L) {
                checked[0] = true;
                fillOnly(h, display, f, atSecond, now / 20L);
            }
            if (!checked[1] && t >= DRAIN_END - 40 && now % 20L == 10L) {
                checked[1] = true;
                draining(h, display, f, atSecond, now / 20L, vaultRel);
            }
            if (t == DRAIN_END) {
                display.feConsumer().setLock(FeLock.FORCE_OFF);
            }
            if (t > DRAIN_END) {
                if (display.feConsumer().isPowered()) {
                    relayWhileCoasting[0] &= display.lowRelayOn();
                } else if (darkAt[0] == Long.MIN_VALUE) {
                    darkAt[0] = t;
                }
            }
        });
        h.runAtTickTime(END, () -> {
            final BlockPos abs = h.absolutePos(displayRel);
            final int signal = h.getLevel().getSignal(abs, Direction.EAST);
            final BlockState lowerNow = h.getLevel().getBlockState(abs);
            final BlockState upperNow = h.getLevel().getBlockState(abs.above());
            LOG.info("[INV-GT] END dark at t={} relay={} signal={} comparator={} powered {} / {} trips={}", darkAt[0],
                    display.lowRelayOn(), signal, display.comparatorLevel(), lowerNow.getValue(ProductionDetectorBlock.POWERED),
                    upperNow.getValue(ProductionDetectorBlock.POWERED), tripTicks[0]);
            if (tripTicks[0] > 0) {
                h.fail("the display's stud tripped for " + tripTicks[0] + " ticks");
            }
            if (darkAt[0] == Long.MIN_VALUE) {
                h.fail("locked off at t " + DRAIN_END + ", the display never went dark: its buffer is 96,000 FE at 150 FE/t");
            }
            if (!relayWhileCoasting[0]) {
                h.fail("the LOW relay dropped while the display still had power: it should coast on its buffer");
            }
            if (display.lowRelayOn() || signal != 0) {
                h.fail("dark since t " + darkAt[0] + ", the display still asserts its LOW relay (signal " + signal + "): DI-3");
            }
            if (display.comparatorLevel() != 0) {
                h.fail("dark, the comparator still reads " + display.comparatorLevel());
            }
            if (lowerNow.getValue(ProductionDetectorBlock.POWERED) || upperNow.getValue(ProductionDetectorBlock.POWERED)) {
                h.fail("dark, the block still says POWERED (a relay is on)");
            }
            h.succeed();
        });
    }

    private static void fillOnly(GameTestHelper h, ProductionDetectorBE display, InventoryFace f, Map<Long, long[]> atSecond,
                                 long second) {
        if (f == null || !display.feConsumer().isPowered()) {
            h.fail("at the end of the fill the display is " + (f == null ? "blank" : "unpowered"));
            return;
        }
        final long[] end = atSecond.get(second);
        final long[] start = atSecond.get(second - 60L);
        if (end == null || start == null || f.gatherSeconds >= 0) {
            h.fail("no full minute to compare (gathering " + f.gatherSeconds + " s)");
            return;
        }
        final long rose = end[0] - start[0];
        LOG.info("[INV-GT] FILL: display in {} out {}, the vault rose {} in the same minute", f.inPerMinute, f.outPerMinute,
                rose);
        if (Math.abs(f.inPerMinute - rose) > 1.0f) {
            h.fail("filling, the display reads IN " + f.inPerMinute + " a minute; the vault rose " + rose);
        }
        if (f.outPerMinute > 0.0f) {
            h.fail("filling only, the display reads OUT " + f.outPerMinute + " a minute");
        }
        if (!display.lowRelayOn()) {
            h.fail("the vault is far below its LOW mark of 90 percent and the LOW relay is off");
        }
    }

    private static void draining(GameTestHelper h, ProductionDetectorBE display, InventoryFace f, Map<Long, long[]> atSecond,
                                 long second, BlockPos vaultRel) {
        if (f == null || !display.feConsumer().isPowered()) {
            h.fail("at the end of the drain the display is " + (f == null ? "blank" : "unpowered"));
            return;
        }
        final long[] end = atSecond.get(second);
        final long[] start = atSecond.get(second - 60L);
        if (end == null || start == null) {
            h.fail("no full minute to compare while draining");
            return;
        }
        final long net = end[0] - start[0];
        final long out = end[1] - start[1];
        final long in = net + out;
        final float shownNet = f.inPerMinute - f.outPerMinute;
        LOG.info("[INV-GT] DRAIN: display in {} out {} net {}; moved in {} out {} net {}", f.inPerMinute, f.outPerMinute,
                shownNet, in, out, net);
        if (Math.abs(shownNet - net) > 1.0f) {
            h.fail("draining, the display reads NET " + shownNet + " a minute; the vault changed by " + net);
        }
        if (f.inPerMinute > in + 1.0f || f.outPerMinute > out + 1.0f) {
            h.fail("gross rates are lower bounds, yet the display reads IN " + f.inPerMinute + " OUT " + f.outPerMinute
                    + " against " + in + " and " + out + " moved");
        }
        if (f.outPerMinute <= 0.0f) {
            h.fail("a hopper drains the vault and the display reads no OUT");
        }
        final BinCardLedger ledger = ledger(display);
        final long balance = ledger.total().balance();
        final long read = display.getTotalItemsSeen();
        final long actual = count(h, vaultRel);
        LOG.info("[INV-GT] BOOKS: balance {} read {} vault {}", balance, read, actual);
        if (balance != read) {
            h.fail("the books do not balance: " + balance + " on the card against " + read + " read");
        }
        if (Math.abs(read - actual) > 1L) {
            h.fail("the display read " + read + " items; the vault holds " + actual);
        }
    }

    private static long count(GameTestHelper h, BlockPos rel) {
        final IItemHandler inv = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(rel), null);
        long n = 0L;
        if (inv != null) {
            for (int i = 0; i < inv.getSlots(); i++) {
                n += inv.getStackInSlot(i).getCount();
            }
        }
        return n;
    }

    private static BinCardLedger ledger(ProductionDetectorBE display) {
        try {
            final java.lang.reflect.Field f = ProductionDetectorBE.class.getDeclaredField("ledger");
            f.setAccessible(true);
            return (BinCardLedger) f.get(display);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no ledger on the Inventory Display", e);
        }
    }
}
