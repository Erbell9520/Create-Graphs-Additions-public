package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.guard.BlockFault;
import com.createstats.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockGuardGameTest {
    private static final BlockPos AT = new BlockPos(6, 2, 6);

    private BlockGuardGameTest() {
    }

    @GameTest(template = "fe_bench", batch = "block_guard", timeoutTicks = 100)
    public static void aFailingPduLeavesAndDropsItsItem(GameTestHelper h) {
        Block block = ModBlocks.POWER_DISTRIBUTION_UNIT.get();
        h.setBlock(AT, block.defaultBlockState());
        BlockEntity be = h.getBlockEntity(AT);
        h.assertTrue(be != null, "no block entity for the PDU");

        BlockFault.tick("Power Distribution Unit", be, () -> {
            throw new IllegalStateException("GameTest: a PDU tick that throws");
        });
        h.runAfterDelay(3, () -> {
            h.assertBlockNotPresent(block, AT);
            h.assertItemEntityPresent(block.asItem(), AT, 3.0);
            h.assertTrue(!BlockFault.isTicking(be), "a PDU that left is still ticked");
            CreateStats.LOGGER.info("[GUARD-GT] the failing PDU left, dropped its item, and the server ran on");
            h.succeed();
        });
    }

    @GameTest(template = "fe_bench", batch = "block_guard_pdu", timeoutTicks = 100)
    public static void aHealthyPduStaysAndIsTickedThroughTheGuard(GameTestHelper h) {
        healthyStays(h, ModBlocks.POWER_DISTRIBUTION_UNIT.get(), "PDU");
    }

    @GameTest(template = "fe_bench", batch = "block_guard_crown", timeoutTicks = 100)
    public static void aHealthySparkCrownStaysAndIsTickedThroughTheGuard(GameTestHelper h) {
        healthyStays(h, ModBlocks.SPARK_CROWN.get(), "Spark Crown");
    }

    private static void healthyStays(GameTestHelper h, Block block, String name) {
        h.setBlock(AT, block.defaultBlockState());
        BlockEntity be = h.getBlockEntity(AT);
        h.runAfterDelay(40, () -> {
            h.assertBlockPresent(block, AT);
            h.assertTrue(BlockFault.isTicking(be), "the " + name + "'s ticks do not run through BlockFault");
            CreateStats.LOGGER.info("[GUARD-GT] {}: healthy for 40 ticks, ticked through the guard", name);
            h.succeed();
        });
    }
}
