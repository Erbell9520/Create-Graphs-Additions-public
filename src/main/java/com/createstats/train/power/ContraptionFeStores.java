package com.createstats.train.power;

import com.createstats.power.TrainBankLedger;
import com.createstats.registry.ModBlocks;
import com.createstats.block.CapacitorBankBlock;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// create stores items and fluids on contraptions, nothing else. so block saved data
public final class ContraptionFeStores {
    private static final ResourceLocation CA_ACCUMULATOR =
            ResourceLocation.fromNamespaceAndPath("createaddition", "modular_accumulator");

    private enum Kind { BANK, ACCUMULATOR }

    private record Cell(Kind kind, BlockPos pos, CompoundTag tag) {
    }

    private final Contraption contraption;
    private final List<Cell> cells;

    private final Map<BlockPos, Cell> byPos;

    private ContraptionFeStores(Contraption contraption, List<Cell> cells) {
        this.contraption = contraption;
        this.cells = cells;
        this.byPos = new HashMap<>(cells.size() * 2);
        for (Cell cell : cells) {
            byPos.put(cell.pos(), cell);
        }
    }

    public static ContraptionFeStores scan(Contraption contraption) {
        final List<StructureBlockInfo> found = new ArrayList<>();
        for (StructureBlockInfo info : contraption.getBlocks().values()) {
            if (info.nbt() != null && kindOf(info.state(), info.nbt()) != null) {
                found.add(info);
            }
        }

        found.sort(Comparator.comparingInt((StructureBlockInfo i) -> i.pos().getY())
                .thenComparingInt(i -> i.pos().getX())
                .thenComparingInt(i -> i.pos().getZ()));
        final List<Cell> cells = new ArrayList<>(found.size());
        for (StructureBlockInfo info : found) {
            cells.add(new Cell(kindOf(info.state(), info.nbt()), info.pos(), info.nbt()));
        }
        return new ContraptionFeStores(contraption, cells);
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    public TrainBankLedger ledger() {
        final int[] energies = new int[cells.size()];
        final int[] capacities = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            final Cell cell = cells.get(i);
            energies[i] = energyOf(cell);
            capacities[i] = capacityOf(cell);
        }
        return new TrainBankLedger(energies, capacities);
    }

    public boolean commit(TrainBankLedger ledger) {
        boolean changed = false;
        final int n = Math.min(cells.size(), ledger.cells());
        for (int i = 0; i < n; i++) {
            final Cell cell = cells.get(i);
            final int now = ledger.energyAt(i);
            if (now == energyOf(cell)) {
                continue;
            }
            changed = true;
            if (cell.kind() == Kind.BANK) {
                cell.tag().putInt("Energy", now);
            } else {
                cell.tag().getCompound("EnergyContent").putInt("energy", now);
            }
        }
        return changed;
    }

    public Contraption contraption() {
        return contraption;
    }

    public List<CarriedBankTelemetry.Cell> bankCells() {
        final Map<BlockPos, StructureBlockInfo> blocks = contraption.getBlocks();
        final List<CarriedBankTelemetry.Cell> out = new ArrayList<>();
        for (Cell cell : cells) {
            if (cell.kind() != Kind.BANK) {
                continue;
            }
            final StructureBlockInfo info = blocks.get(cell.pos());
            final int led = info != null && info.state().hasProperty(CapacitorBankBlock.LEVEL)
                    ? info.state().getValue(CapacitorBankBlock.LEVEL) : -1;
            out.add(new CarriedBankTelemetry.Cell(cell.pos(), energyOf(cell), capacityOf(cell), led));
        }
        return out;
    }

    @Nullable
    public CompoundTag tagAt(BlockPos localPos) {
        final Cell cell = byPos.get(localPos);
        return cell == null ? null : cell.tag();
    }

    private static Kind kindOf(BlockState state, CompoundTag tag) {
        if (state.is(ModBlocks.CAPACITOR_BANK.get())) {
            return Kind.BANK;
        }
        if (tag.contains("EnergyContent", Tag.TAG_COMPOUND) && tag.getInt("EnergyCapacity") > 0
                && CA_ACCUMULATOR.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
            return Kind.ACCUMULATOR;
        }
        return null;
    }

    private static int energyOf(Cell cell) {
        return cell.kind() == Kind.BANK
                ? Math.max(0, cell.tag().getInt("Energy"))
                : Math.max(0, cell.tag().getCompound("EnergyContent").getInt("energy"));
    }

    private static int capacityOf(Cell cell) {
        if (cell.kind() == Kind.ACCUMULATOR) {
            return Math.max(0, cell.tag().getInt("EnergyCapacity"));
        }

        return cell.tag().contains("Cap")
                ? Math.max(1, cell.tag().getInt("Cap"))
                : com.createstats.ModConfiguration.COMMON.capacitorBankCapacity.get();
    }

    public int size() {
        return cells.size();
    }
}
