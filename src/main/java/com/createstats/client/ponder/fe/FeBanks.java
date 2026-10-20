package com.createstats.client.ponder.fe;

import com.createstats.CreateStats;
import com.createstats.block.CapacitorBankBlock;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.power.FeBankMerge;
import com.createstats.power.FeStackFill;
import com.createstats.power.ponder.FePonderChapters.Bank.Stock;
import com.createstats.power.ponder.FePonderChapters.Warp;
import com.createstats.power.ponder.FePonderGrid;
import com.createstats.registry.ModBlocks;
import com.createstats.renderer.CapacitorBankTextureManager;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.IntUnaryOperator;

// nothing ticks in ponder, so write what the bank tick would have
public final class FeBanks {
    private FeBanks() {
    }

    public record Pool(BlockPos[] members, int part) {
    }

    public static KitInstruction place(Pool pool, int from, int to, Stock stock, int start) {
        return new KitInstruction(start, 1) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                final BlockState bank = ModBlocks.CAPACITOR_BANK.get().defaultBlockState();
                for (int i = from; i < to; i++) level.setBlock(pool.members()[i], bank, 0);
                write(level, pool.members(), to, 0, 0, 0, stock);
                scene.forEach(WorldSectionElementImpl.class, WorldSectionElementImpl::queueRedraw);
                refresh();
            }
        };
    }

    public static KitInstruction charge(List<Pool> pools, List<IntUnaryOperator> standing, FePonderGrid.Recording rec,
                                        Warp warp, Stock stock, int start, int ticks) {
        final List<IntUnaryOperator> stored = new java.util.ArrayList<>();
        final List<IntUnaryOperator> flow = new java.util.ArrayList<>();
        for (Pool pool : pools) {
            stored.add(t -> rec.energy[pool.part()][rec.at(warp.tick(t))]);
            flow.add(t -> rec.flow[pool.part()][rec.at(warp.tick(t))]);
        }
        return charge(pools, standing, stored, flow, stock, start, ticks);
    }

    public static KitInstruction charge(List<Pool> pools, List<IntUnaryOperator> standing,
                                        List<IntUnaryOperator> storedAt, List<IntUnaryOperator> flowAt, Stock stock,
                                        int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private final int[] signature = new int[pools.size()];

            @Override
            protected void begin(PonderScene scene) {
                java.util.Arrays.fill(signature, Integer.MIN_VALUE);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                boolean changed = false;
                for (int k = 0; k < pools.size(); k++) {
                    final Pool pool = pools.get(k);
                    final int n = standing.get(k).applyAsInt(tick);
                    if (n <= 0) continue;
                    final int stored = storedAt.get(k).applyAsInt(tick);
                    final int flow = flowAt.get(k).applyAsInt(tick);
                    final int in = Math.max(0, flow);
                    final int out = Math.max(0, -flow);

                    int sig = n;
                    sig = sig * 31 + stored / Math.max(1, stock.capacity(n) / 2048);
                    sig = sig * 31 + in;
                    sig = sig * 31 + out;
                    if (sig == signature[k]) continue;
                    signature[k] = sig;
                    write(level, pool.members(), n, stored, in, out, stock);
                    changed = true;
                }
                if (changed) refresh();
            }
        };
    }

    static void write(PonderLevel level, BlockPos[] members, int n, int stored, int in, int out, Stock stock) {
        final int[] caps = FeBankMerge.shareCapacity(stock.capacity(n), n);
        final int[] energy = new int[n];
        FeStackFill.fillBottomUp(energy, caps, stored);
        for (int i = 0; i < n; i++) {
            final BlockPos pos = members[i];
            final BlockState state = level.getBlockState(pos);
            if (state.hasProperty(CapacitorBankBlock.SEGMENT)) {
                final CapacitorBankBlock.Segment segment = segment(members, n, pos);
                if (state.getValue(CapacitorBankBlock.SEGMENT) != segment) {
                    level.setBlock(pos, state.setValue(CapacitorBankBlock.SEGMENT, segment), 0);
                }
            }
            final BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;
            try {
                final CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                tag.putInt("Energy", energy[i]);
                tag.putInt("Cap", caps[i]);
                tag.putInt("InRate", i == 0 ? in : 0);
                tag.putInt("OutRate", i == 0 ? out : 0);
                tag.putInt("Rate", i == 0 ? in - out : 0);
                be.loadWithComponents(tag, level.registryAccess());
            } catch (RuntimeException e) {
                CreateStats.LOGGER.warn("[Ponder] a bank's charge could not be written: {}", e.toString());
            }
        }
    }

    static CapacitorBankBlock.Segment segment(BlockPos[] members, int n, BlockPos pos) {
        boolean above = false;
        boolean below = false;
        for (int i = 0; i < n; i++) {
            final BlockPos p = members[i];
            if (p.getX() != pos.getX() || p.getZ() != pos.getZ()) continue;
            if (p.getY() == pos.getY() + 1) above = true;
            else if (p.getY() == pos.getY() - 1) below = true;
        }
        return above && below ? CapacitorBankBlock.Segment.MIDDLE : above ? CapacitorBankBlock.Segment.BOTTOM
                : below ? CapacitorBankBlock.Segment.TOP : CapacitorBankBlock.Segment.SINGLE;
    }

    static void refresh() {
        try {
            CapacitorBankTextureManager.disposeAll();
        } catch (RuntimeException e) {
            CreateStats.LOGGER.warn("[Ponder] the bank's face could not be refreshed: {}", e.toString());
        }
    }
}
