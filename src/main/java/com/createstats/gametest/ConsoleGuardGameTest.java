package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.registry.ModBlocks;
import com.createstats.vccmk4.ConsoleFault;
import com.createstats.vccmk4.Mk4DisarmData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConsoleGuardGameTest {
    private static final BlockPos AT = new BlockPos(6, 2, 6);
    private static final BlockPos AT2 = new BlockPos(14, 2, 6);

    private ConsoleGuardGameTest() {
    }

    @GameTest(template = "fe_bench", batch = "vcc4_guard", timeoutTicks = 100)
    public static void aFailingAerialLeavesAndDropsItsItem(GameTestHelper h) {
        failingConsoleLeaves(h, ModBlocks.VCC_MK4_AERIAL.get());
    }

    @GameTest(template = "fe_bench", batch = "vcc4_guard", timeoutTicks = 100)
    public static void aFailingGroundConsoleLeavesAndDropsItsItem(GameTestHelper h) {
        failingConsoleLeaves(h, ModBlocks.VCC_MK4.get());
    }

    @GameTest(template = "fe_bench", batch = "vcc4_guard", timeoutTicks = 100)
    public static void aHealthyAerialStaysAndIsTickedThroughTheGuard(GameTestHelper h) {
        healthyConsoleStays(h, ModBlocks.VCC_MK4_AERIAL.get());
    }

    @GameTest(template = "fe_bench", batch = "vcc4_guard", timeoutTicks = 100)
    public static void aHealthyGroundConsoleStaysAndIsTickedThroughTheGuard(GameTestHelper h) {
        healthyConsoleStays(h, ModBlocks.VCC_MK4.get());
    }

    @GameTest(template = "fe_bench", batch = "vcc4_disarm", timeoutTicks = 100)
    public static void disarmRemovesAnAerialOnItsFirstTick(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Mk4DisarmData.get(server).setDisarmed(true);
        h.setBlock(AT, ModBlocks.VCC_MK4_AERIAL.get().defaultBlockState());
        h.runAfterDelay(4, () -> {
            Mk4DisarmData.get(server).setDisarmed(false);
            h.assertBlockNotPresent(ModBlocks.VCC_MK4_AERIAL.get(), AT);
            h.assertItemEntityPresent(ModBlocks.VCC_MK4_AERIAL.get().asItem(), AT, 3.0);
            CreateStats.LOGGER.info("[VCC4-GT] disarm: the Aerial left on its first tick and dropped its item");
            h.succeed();
        });
    }

    @GameTest(template = "fe_bench", batch = "vcc4_command", timeoutTicks = 100)
    public static void destroyFromTheServerConsoleRemovesEveryLoadedConsole(GameTestHelper h) {
        h.setBlock(AT, ModBlocks.VCC_MK4_AERIAL.get().defaultBlockState());
        h.setBlock(AT2, ModBlocks.VCC_MK4.get().defaultBlockState());
        MinecraftServer server = h.getLevel().getServer();
        h.runAfterDelay(3, () -> {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "vcc4 destroy");
            h.runAfterDelay(3, () -> {
                h.assertBlockNotPresent(ModBlocks.VCC_MK4_AERIAL.get(), AT);
                h.assertBlockNotPresent(ModBlocks.VCC_MK4.get(), AT2);
                h.assertItemEntityPresent(ModBlocks.VCC_MK4_AERIAL.get().asItem(), AT, 3.0);
                h.assertItemEntityPresent(ModBlocks.VCC_MK4.get().asItem(), AT2, 3.0);
                CreateStats.LOGGER.info("[VCC4-GT] /vcc4 destroy: both consoles left and dropped their items");
                h.succeed();
            });
        });
    }

    private static void failingConsoleLeaves(GameTestHelper h, Block block) {
        h.setBlock(AT, block.defaultBlockState());
        BlockEntity be = h.getBlockEntity(AT);
        h.assertTrue(be != null, "no block entity for " + block);

        ConsoleFault.tick(be, () -> {
            throw new IllegalStateException("GameTest: a console tick that throws");
        });
        h.runAfterDelay(3, () -> {
            h.assertBlockNotPresent(block, AT);
            h.assertItemEntityPresent(block.asItem(), AT, 3.0);
            h.assertTrue(!ConsoleFault.isTicking(be), "a console that left is still ticked");
            CreateStats.LOGGER.info("[VCC4-GT] {}: the failing console left, dropped its item, and the server ran on", block);
            h.succeed();
        });
    }

    private static void healthyConsoleStays(GameTestHelper h, Block block) {
        h.setBlock(AT, block.defaultBlockState());
        BlockEntity be = h.getBlockEntity(AT);
        h.runAfterDelay(40, () -> {
            h.assertBlockPresent(block, AT);
            h.assertTrue(ConsoleFault.isTicking(be), "the console's ticks do not run through ConsoleFault");
            CreateStats.LOGGER.info("[VCC4-GT] {}: healthy for 40 ticks, ticked through the guard", block);
            h.succeed();
        });
    }
}
