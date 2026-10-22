package com.createstats.gametest;

import com.createstats.CreateStats;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = CreateStats.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CouplerBench {
    private static final Logger LOG = LoggerFactory.getLogger("create_stats/gametest");

    public enum Kind {
        SINK(10_000_000, 0, 1_000_000, 0),
        SOURCE(10_000_000, 10_000_000, 0, 1_000_000),
        BATTERY(1_000_000, 0, 100_000, 100_000),

        BANK(10_000_000, 10_000_000, 1_000_000, 1_000_000);

        final int capacity;
        final int start;
        final int maxIn;
        final int maxOut;

        Kind(int capacity, int start, int maxIn, int maxOut) {
            this.capacity = capacity;
            this.start = start;
            this.maxIn = maxIn;
            this.maxOut = maxOut;
        }
    }

    public static final Block SINK_BLOCK = Blocks.ENCHANTING_TABLE;
    public static final Block SOURCE_BLOCK = Blocks.LECTERN;
    public static final Block BATTERY_BLOCK = Blocks.DECORATED_POT;
    public static final Block BANK_BLOCK = Blocks.CHISELED_BOOKSHELF;

    private static final Map<Level, Map<Long, Store>> STORES = new WeakHashMap<>();

    private CouplerBench() {
    }

    public static boolean enabled() {
        return GameTestHooks.isGametestEnabled() || Boolean.getBoolean("create_stats.bench");
    }

    @SubscribeEvent
    public static void registerStandIns(RegisterCapabilitiesEvent event) {
        if (!enabled()) {
            return;
        }
        register(event, SINK_BLOCK, Kind.SINK);
        register(event, SOURCE_BLOCK, Kind.SOURCE);
        register(event, BATTERY_BLOCK, Kind.BATTERY);
        register(event, BANK_BLOCK, Kind.BANK);
        LOG.info("[COUPLER-GT] bench stand-ins registered: sink {}, source {}, battery {}", SINK_BLOCK, SOURCE_BLOCK,
                BATTERY_BLOCK);
    }

    private static void register(RegisterCapabilitiesEvent event, Block block, Kind kind) {
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, side) -> at(level, pos, kind), block);
    }

    @SubscribeEvent
    public static void benchHook(FMLCommonSetupEvent event) {
        if (!Boolean.getBoolean("create_stats.bench")) {
            return;
        }
        event.enqueueWork(() -> {
            GameTestRegistry.register(CouplerForeignCompatGameTest.class);
            GameTestRegistry.register(CouplerGameTest.class);
            GameTestRegistry.register(CouplerBoardGameTest.class);
            LOG.info("[COUPLER-GT] bench hook: registered {} test functions",
                    GameTestRegistry.getAllTestFunctions().size());
        });
    }

    @EventBusSubscriber(modid = CreateStats.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
    public static final class BenchTicker {
        private BenchTicker() {
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            if (Boolean.getBoolean("create_stats.bench") && !GameTestHooks.isGametestEnabled()
                    && event.getServer().tickRateManager().runsNormally()) {
                GameTestTicker.SINGLETON.tick();
            }
        }
    }

    public static synchronized Store at(Level level, BlockPos pos, Kind kind) {
        final Map<Long, Store> byPos = STORES.computeIfAbsent(level, ignored -> new HashMap<>());
        final Store found = byPos.get(pos.asLong());
        if (found != null && found.kind == kind) {
            return found;
        }
        final Store fresh = new Store(kind);
        byPos.put(pos.asLong(), fresh);
        return fresh;
    }

    public static synchronized Store reset(Level level, BlockPos pos, Kind kind) {
        STORES.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos.asLong(), new Store(kind));
        return at(level, pos, kind);
    }

    public static final class Store implements IEnergyStorage {
        private final Kind kind;
        private int energy;

        Store(Kind kind) {
            this.kind = kind;
            this.energy = kind.start;
        }

        public void set(int fe) {
            this.energy = Math.max(0, Math.min(this.kind.capacity, fe));
        }

        @Override
        public int receiveEnergy(int max, boolean simulate) {
            final int took = Math.max(0, Math.min(Math.min(max, this.kind.maxIn), this.kind.capacity - this.energy));
            if (!simulate) {
                this.energy += took;
            }
            return took;
        }

        @Override
        public int extractEnergy(int max, boolean simulate) {
            final int gave = Math.max(0, Math.min(Math.min(max, this.kind.maxOut), this.energy));
            if (!simulate) {
                this.energy -= gave;
            }
            return gave;
        }

        @Override
        public int getEnergyStored() {
            return this.energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return this.kind.capacity;
        }

        @Override
        public boolean canExtract() {
            return this.kind.maxOut > 0;
        }

        @Override
        public boolean canReceive() {
            return this.kind.maxIn > 0;
        }
    }
}
