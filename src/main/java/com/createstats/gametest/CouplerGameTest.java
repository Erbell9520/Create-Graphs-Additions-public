package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.block.UniversalPowerCouplerBlock;
import com.createstats.blockentity.UniversalPowerCouplerBE;
import com.createstats.power.CouplerFault;
import com.createstats.power.CouplerLever;
import com.createstats.power.CouplerSockets;
import com.createstats.power.NewAgeFeed;
import com.createstats.power.PowerCouplerMode;
import com.createstats.power.WireTier;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CouplerGameTest {
    static final Logger LOG = LoggerFactory.getLogger("create_stats/gametest");

    private CouplerGameTest() {
    }

    static UniversalPowerCouplerBE coupler(GameTestHelper h, BlockPos rel, Direction foreign, PowerCouplerMode mode) {
        h.setBlock(rel, ModBlocks.UNIVERSAL_POWER_COUPLER.get().defaultBlockState()
                .setValue(UniversalPowerCouplerBlock.FACING, foreign)
                .setValue(UniversalPowerCouplerBlock.MODE, mode));
        return h.getBlockEntity(rel);
    }

    static CouplerBench.Store standIn(GameTestHelper h, BlockPos rel, CouplerBench.Kind kind) {
        h.setBlock(rel, switch (kind) {
            case SINK -> CouplerBench.SINK_BLOCK;
            case SOURCE -> CouplerBench.SOURCE_BLOCK;
            case BATTERY -> CouplerBench.BATTERY_BLOCK;
            case BANK -> CouplerBench.BANK_BLOCK;
        });
        final BlockPos abs = h.absolutePos(rel);
        final CouplerBench.Store store = CouplerBench.reset(h.getLevel(), abs, kind);
        h.getLevel().invalidateCapabilities(abs);
        return store;
    }

    static void wire(GameTestHelper h, UniversalPowerCouplerBE coupler, BlockPos farRel) {
        coupler.addLink(h.absolutePos(farRel), WireTier.COPPER);
    }

    static void cable(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel.below(), Blocks.STONE);
        h.setBlock(rel, ModBlocks.SURFACE_CABLE.get().defaultBlockState()
                .setValue(com.createstats.block.CableBlock.faceProperty(Direction.DOWN), true));
    }

    static void floor(GameTestHelper h, BlockPos rel, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                h.setBlock(new BlockPos(rel.getX() + dx, 0, rel.getZ() + dz), Blocks.STONE);
            }
        }
    }

    static void report(String mod, String name, String version, String testCase, boolean pass, String figures) {
        LOG.info("[COUPLER-GT] mod={} name=\"{}\" version={} case=\"{}\" result={} {}", mod, name, version, testCase,
                pass ? "PASS" : "FAIL", figures);
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = 100)
    public static void faceToFaceNeverRecurses(GameTestHelper h) {
        final UniversalPowerCouplerBE a = coupler(h, new BlockPos(5, 1, 5), Direction.EAST, PowerCouplerMode.PULL);
        final UniversalPowerCouplerBE b = coupler(h, new BlockPos(6, 1, 5), Direction.WEST, PowerCouplerMode.PUSH);
        final ServerLevel level = h.getLevel();
        try {
            final Object capA = level.getCapability(Capabilities.EnergyStorage.BLOCK, a.getBlockPos(), a.foreignFace());
            final Object capB = level.getCapability(Capabilities.EnergyStorage.BLOCK, b.getBlockPos(), b.foreignFace());
            final boolean seamA = NewAgeFeed.isForeignSeam(level, a.getBlockPos(), a.foreignFace());
            if (capA != null || capB != null || seamA) {
                h.fail("a Coupler against a Coupler opened a seam");
            }
        } catch (StackOverflowError overflow) {
            h.fail("two Couplers face to face recursed until the stack overflowed (KI-201)");
            return;
        }
        h.runAtTickTime(45, () -> {
            final boolean ok = a.fault() == CouplerFault.FOREIGN_REFUSED && b.fault() == CouplerFault.FOREIGN_REFUSED;
            LOG.info("[COUPLER-GT] faceToFace a={} b={}", a.fault(), b.fault());
            if (!ok) {
                h.fail("face to face should read FOREIGN_REFUSED on both, read " + a.fault() + " and " + b.fault());
            }
            h.succeed();
        });
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = 100)
    public static void oursIsNeverForeign(GameTestHelper h) {
        final UniversalPowerCouplerBE c = coupler(h, new BlockPos(5, 1, 5), Direction.EAST, PowerCouplerMode.PULL);
        cable(h, new BlockPos(6, 1, 5));
        h.setBlock(new BlockPos(5, 1, 9), ModBlocks.CAPACITOR_BANK.get().defaultBlockState());
        h.runAtTickTime(45, () -> {
            final ServerLevel level = h.getLevel();
            final boolean seam = level.getCapability(Capabilities.EnergyStorage.BLOCK, c.getBlockPos(),
                    c.foreignFace()) != null;
            final BlockPos bank = h.absolutePos(new BlockPos(5, 1, 9));
            final boolean bankIsTarget = UniversalPowerCouplerBE.isSeamTarget(level, bank);
            LOG.info("[COUPLER-GT] ours: seam={} fault={} bankIsTarget={}", seam, c.fault(), bankIsTarget);
            if (seam || c.fault() != CouplerFault.FOREIGN_REFUSED) {
                h.fail("our cable on the foreign face opened the seam or was not refused: " + c.fault());
            }
            if (bankIsTarget) {
                h.fail("a Capacitor Bank of ours was accepted as a seam wire's far end");
            }
            h.succeed();
        });
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = 260)
    public static void boltedPushPumpsAPlainSink(GameTestHelper h) {
        shape(h, false, false, "bolted PUSH");
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = 260)
    public static void boltedPullPumpsAPlainSource(GameTestHelper h) {
        shape(h, true, false, "bolted PULL");
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = 260)
    public static void wiredPushReachesAFarSink(GameTestHelper h) {
        shape(h, false, true, "wired PUSH");
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = 260)
    public static void wiredPullDrawsAFarSource(GameTestHelper h) {
        shape(h, true, true, "wired PULL");
    }

    private static void shape(GameTestHelper h, boolean pull, boolean wired, String name) {
        final BlockPos cRel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = coupler(h, cRel, Direction.EAST,
                pull ? PowerCouplerMode.PULL : PowerCouplerMode.PUSH);
        final BlockPos theirRel = wired ? new BlockPos(9, 1, 8) : cRel.east();
        final BlockPos oursRel = cRel.west();
        final CouplerBench.Store theirs = standIn(h, theirRel, pull ? CouplerBench.Kind.SOURCE : CouplerBench.Kind.SINK);
        final CouplerBench.Store ours = standIn(h, oursRel, pull ? CouplerBench.Kind.SINK : CouplerBench.Kind.SOURCE);
        if (wired) {
            wire(h, c, theirRel);
        }
        final int theirStart = theirs.getEnergyStored();
        final int ourStart = ours.getEnergyStored();
        final int[] peak = {0};
        final int[] over = {0};
        h.onEachTick(() -> {
            final int crossing = c.crossingFePerTick();
            peak[0] = Math.max(peak[0], crossing);
            if (crossing > c.ratingFePerTick()) {
                over[0]++;
            }
        });
        h.runAtTickTime(240, () -> {
            final long theirDelta = (long) theirs.getEnergyStored() - theirStart;
            final long ourDelta = (long) ours.getEnergyStored() - ourStart;
            final long store = c.storedFe();

            final long error = theirDelta + ourDelta + store;
            final long moved = Math.abs(pull ? theirDelta : ourDelta);
            final boolean pass = error == 0 && moved > 0 && over[0] == 0 && c.fault() == CouplerFault.NONE;
            report("create_stats_bench", "stand-in", "-", name, pass, String.format(
                    "moved=%d theirs=%+d ours=%+d store=%d error=%d peak=%d rating=%d over=%d fault=%s endpoint=%s",
                    moved, theirDelta, ourDelta, store, error, peak[0], c.ratingFePerTick(), over[0], c.fault(),
                    c.foreignEndpoint()));
            if (!pass) {
                h.fail(name + ": moved " + moved + ", conservation error " + error + ", ticks over the rating "
                        + over[0] + ", fault " + c.fault());
            }
            h.succeed();
        });
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = 200)
    public static void aDeadSeamSpanIsRefunded(GameTestHelper h) {
        floor(h, new BlockPos(5, 0, 5), 3);
        final UniversalPowerCouplerBE c = coupler(h, new BlockPos(5, 1, 5), Direction.EAST, PowerCouplerMode.PUSH);
        final BlockPos far = new BlockPos(9, 1, 8);
        standIn(h, far, CouplerBench.Kind.SINK);
        wire(h, c, far);
        h.runAtTickTime(25, () -> h.setBlock(far, Blocks.AIR));
        h.runAtTickTime(30, () -> {
            if (c.wiredSeamPos() == null) {
                h.fail("the seam span was dropped on the first miss, not after three");
            }
        });
        h.runAtTickTime(140, () -> {
            final AABB around = new AABB(c.getBlockPos()).inflate(3);
            final List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, around);
            final boolean refunded = drops.stream().anyMatch(e -> e.getItem().is(
                    com.createstats.registry.ModItems.wireItem(WireTier.COPPER)));
            LOG.info("[COUPLER-GT] deadSpan: link={} refunded={}", c.wiredSeamPos(), refunded);
            if (c.wiredSeamPos() != null || !refunded) {
                h.fail("a seam span whose far end was mined must be let go and refunded (link " + c.wiredSeamPos()
                        + ", refunded " + refunded + ")");
            }
            h.succeed();
        });
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = 120)
    public static void theStoreTravelsInTheItem(GameTestHelper h) {
        final BlockPos rel = new BlockPos(5, 1, 5);
        floor(h, new BlockPos(5, 0, 5), 3);
        final UniversalPowerCouplerBE c = coupler(h, rel, Direction.EAST, PowerCouplerMode.PULL);
        standIn(h, rel.east(), CouplerBench.Kind.SOURCE);
        final int[] stored = {0};
        h.runAtTickTime(40, () -> {
            stored[0] = c.storedFe();
            h.getLevel().destroyBlock(h.absolutePos(rel), true);
        });
        h.runAtTickTime(45, () -> {
            final List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(h.absolutePos(rel)).inflate(2));
            final int carried = drops.stream().map(ItemEntity::getItem)
                    .filter(s -> s.is(ModBlocks.UNIVERSAL_POWER_COUPLER.get().asItem()))
                    .mapToInt(s -> s.getOrDefault(ModDataComponents.STORED_FE.get(), 0)).sum();
            LOG.info("[COUPLER-GT] storeInItem: stored={} carried={}", stored[0], carried);
            if (stored[0] <= 0 || carried != stored[0]) {
                h.fail("the Coupler held " + stored[0] + " FE and its item carries " + carried);
            }
            h.succeed();
        });
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = 100)
    public static void wrenchTurnsLeverFlips(GameTestHelper h) {
        final BlockPos rel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = coupler(h, rel, Direction.EAST, PowerCouplerMode.PULL);
        standIn(h, rel.east(), CouplerBench.Kind.SOURCE);
        final BlockPos far = new BlockPos(9, 1, 8);
        standIn(h, far, CouplerBench.Kind.SINK);
        h.runAtTickTime(30, () -> {
            final int before = c.storedFe();
            final Player player = h.makeMockPlayer(GameType.SURVIVAL);
            final BlockPos abs = h.absolutePos(rel);
            final BlockState state = h.getLevel().getBlockState(abs);
            final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            ((UniversalPowerCouplerBlock) state.getBlock()).onWrenched(state, new UseOnContext(h.getLevel(), player,
                    InteractionHand.MAIN_HAND, new ItemStack(com.simibubi.create.AllItems.WRENCH.get()), hit));
            final BlockState turned = h.getLevel().getBlockState(abs);
            final UniversalPowerCouplerBE after = h.getBlockEntity(rel);
            if (turned.getValue(UniversalPowerCouplerBlock.FACING) != Direction.SOUTH
                    || turned.getValue(UniversalPowerCouplerBlock.MODE) != PowerCouplerMode.PULL
                    || after != c || after.storedFe() != before) {
                h.fail("the wrench must turn the Coupler clockwise and keep its mode and store: "
                        + turned + " stored " + after.storedFe() + " of " + before);
            }

            final Vec3 lever = CouplerLever.local(Direction.SOUTH).getCenter().add(abs.getX(), abs.getY(), abs.getZ());
            turned.useWithoutItem(h.getLevel(), player, new BlockHitResult(lever, Direction.UP, abs, false));
            final BlockState flipped = h.getLevel().getBlockState(abs);
            if (flipped.getValue(UniversalPowerCouplerBlock.MODE) != PowerCouplerMode.PUSH
                    || c.modeChangedAt() != h.getLevel().getGameTime()) {
                h.fail("the lever did not flip the mode and date the throw: " + flipped + " at " + c.modeChangedAt());
            }

            final Vec3 socket = CouplerSockets.anchor(flipped, CouplerSockets.End.GRID)
                    .add(abs.getX(), abs.getY(), abs.getZ());
            flipped.useWithoutItem(h.getLevel(), player, new BlockHitResult(socket, Direction.NORTH, abs, false));
            if (h.getLevel().getBlockState(abs).getValue(UniversalPowerCouplerBlock.MODE) != PowerCouplerMode.PUSH) {
                h.fail("a click on the grid socket flipped the mode");
            }
            LOG.info("[COUPLER-GT] wrenchLever: ok");
            h.succeed();
        });
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = 140)
    public static void offMovesNothingAndComesBack(GameTestHelper h) {
        final BlockPos rel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = coupler(h, rel, Direction.EAST, PowerCouplerMode.PUSH);
        final CouplerBench.Store theirs = standIn(h, rel.east(), CouplerBench.Kind.SINK);
        final CouplerBench.Store ours = standIn(h, rel.west(), CouplerBench.Kind.SOURCE);
        final long[] at = new long[3];
        h.runAtTickTime(30, () -> {
            if (c.exportFePerTick() <= 0) {
                h.fail("the exporting Coupler was not carrying before the switch");
            }
            click(h, rel, true);
            if (c.mode() != PowerCouplerMode.OFF) {
                h.fail("a sneaking click on the lever did not switch the Coupler off: " + c.mode());
            }
        });
        h.runAtTickTime(32, () -> {
            at[0] = theirs.getEnergyStored();
            at[1] = ours.getEnergyStored();
            at[2] = c.storedFe();
        });
        h.runAtTickTime(80, () -> {
            final long theirs80 = theirs.getEnergyStored() - at[0];
            final long ours80 = ours.getEnergyStored() - at[1];
            final long store80 = c.storedFe() - at[2];
            LOG.info("[COUPLER-GT] off: theirs={} ours={} store={} held={} fault={} price={} remembered={}",
                    theirs80, ours80, store80, c.storedFe(), c.fault(), c.ratedDrawFePerTick(),
                    c.rememberedDirection());
            if (theirs80 != 0 || ours80 != 0 || store80 != 0) {
                h.fail("OFF moved FE: theirs " + theirs80 + ", ours " + ours80 + ", store " + store80);
            }
            if (c.fault() != CouplerFault.SWITCHED_OFF || c.ratedDrawFePerTick() != 0
                    || c.rememberedDirection() != PowerCouplerMode.PUSH) {
                h.fail("OFF should read SWITCHED_OFF, price 0 and remember PUSH: " + c.fault() + ", "
                        + c.ratedDrawFePerTick() + ", " + c.rememberedDirection());
            }
            click(h, rel, false);
            if (c.mode() != PowerCouplerMode.PUSH || c.modeChangedAt() != h.getLevel().getGameTime()) {
                h.fail("a click from OFF must return to PUSH and date the throw: " + c.mode() + " at "
                        + c.modeChangedAt());
            }
        });
        h.runAtTickTime(110, () -> {
            if (c.exportFePerTick() <= 0) {
                h.fail("back in PUSH the Coupler did not carry again");
            }
            h.succeed();
        });
    }

    private static void click(GameTestHelper h, BlockPos rel, boolean sneaking) {
        final Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(sneaking);
        final BlockPos abs = h.absolutePos(rel);
        final BlockState state = h.getLevel().getBlockState(abs);
        final Vec3 lever = CouplerLever.local(state.getValue(UniversalPowerCouplerBlock.FACING)).getCenter()
                .add(abs.getX(), abs.getY(), abs.getZ());
        state.useWithoutItem(h.getLevel(), player, new BlockHitResult(lever, Direction.UP, abs, false));
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = 140)
    public static void aBatteryOnOurCircuitIsALoop(GameTestHelper h) {
        final BlockPos rel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = coupler(h, rel, Direction.EAST, PowerCouplerMode.PULL);

        for (int x = 4; x >= 2; x--) {
            cable(h, new BlockPos(x, 1, 5));
        }
        final BlockPos battery = new BlockPos(1, 1, 5);
        standIn(h, battery, CouplerBench.Kind.BATTERY);
        wire(h, c, battery);
        h.runAtTickTime(110, () -> {
            LOG.info("[COUPLER-GT] batteryLoop: looped={} fault={} gridFace={} cable={}", c.isLooped(), c.fault(),
                    c.gridFaceLive(), h.getLevel().getBlockState(h.absolutePos(new BlockPos(4, 1, 5))));
            if (!c.isLooped() || c.fault() != CouplerFault.LOOPED) {
                h.fail("a battery reached by our cable and by the seam wire must read as a loop, read " + c.fault());
            }
            h.succeed();
        });
    }

    @GameTest(template = "fe_bench", templateNamespace = CreateStats.MOD_ID, timeoutTicks = 160)
    public static void anExportIsPricedAtWhatTheyTake(GameTestHelper h) {
        final BlockPos rel = new BlockPos(5, 1, 5);
        final UniversalPowerCouplerBE c = coupler(h, rel, Direction.EAST, PowerCouplerMode.PUSH);
        final CouplerBench.Store sink = standIn(h, rel.east(), CouplerBench.Kind.SINK);
        standIn(h, rel.west(), CouplerBench.Kind.SOURCE);

        sink.set(sink.getMaxEnergyStored() - 200);
        h.onEachTick(() -> sink.set(Math.min(sink.getEnergyStored(), sink.getMaxEnergyStored() - 200)));
        h.runAtTickTime(130, () -> {
            final int price = c.ratedDrawFePerTick();
            LOG.info("[COUPLER-GT] pushPrice: price={} rating={} export={}", price, c.ratingFePerTick(),
                    c.exportFePerTick());
            if (price <= 0 || price > 400) {
                h.fail("a 200 FE/t sink should price near 200, priced " + price + " (the rating is "
                        + c.ratingFePerTick() + ")");
            }
            h.succeed();
        });
    }
}
