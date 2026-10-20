package com.createstats.client.ponder.powertrack;

import com.createstats.CreateStats;
import com.createstats.block.CapacitorBankBlock;
import com.createstats.blockentity.TrackFeederBE;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.powertrack.SectionInsulatorBE;
import com.createstats.powertrack.SectionView;
import com.createstats.powertrack.core.SectionGraph;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

// nothing ticks a feeder server side in a scene, so write it like the update packet would
final class BlockDriver extends KitInstruction {
    record FeederAt(BlockPos pos, int index) {
    }

    record InsulatorAt(BlockPos pos, int lo, int hi, double cutAt) {
    }

    record BankAt(BlockPos pos, java.util.function.IntToDoubleFunction fill) {
    }

    private final List<RailStory.Frame> frames;
    private final SectionGraph sections;
    private final List<FeederAt> feeders;
    private final InsulatorAt insulator;
    private final List<BankAt> banks;
    private final int[] shownLevel;

    BlockDriver(List<RailStory.Frame> frames, SectionGraph sections, List<FeederAt> feeders, InsulatorAt insulator,
                List<BankAt> banks, int start, int ticks) {
        super(start, ticks);
        this.frames = frames;
        this.sections = sections;
        this.feeders = feeders;
        this.insulator = insulator;
        this.banks = banks;
        this.shownLevel = new int[banks.size()];
    }

    @Override
    protected void begin(PonderScene scene) {
        java.util.Arrays.fill(shownLevel, -1);
    }

    @Override
    protected void step(PonderScene scene, int tick, boolean last) {
        if (tick < 0 || tick >= frames.size()) {
            return;
        }
        final RailStory.Frame f = frames.get(tick);
        for (FeederAt at : feeders) {
            if (scene.getWorld().getBlockEntity(at.pos()) instanceof TrackFeederBE be) {
                write(scene, be, f.feeders()[at.index()]);
            }
        }
        if (insulator != null && scene.getWorld().getBlockEntity(insulator.pos()) instanceof SectionInsulatorBE be) {
            final CompoundTag tag = be.saveWithoutMetadata(scene.getWorld().registryAccess());
            tag.putByte("Status", (byte) SectionInsulatorBE.Status.WORKING.ordinal());
            tag.put("Lamp0", RailStory.lampView(f.sections(), sections, insulator.lo(), insulator.hi(), insulator.cutAt(), true).write());
            tag.put("Lamp1", RailStory.lampView(f.sections(), sections, insulator.lo(), insulator.hi(), insulator.cutAt(), false).write());
            tag.putBoolean("Oriented", true);
            load(scene, be, tag);
        }
        boolean redraw = false;
        for (int i = 0; i < banks.size(); i++) {
            final BankAt bank = banks.get(i);
            final BlockState state = scene.getWorld().getBlockState(bank.pos());
            if (!state.hasProperty(CapacitorBankBlock.LEVEL)) {
                continue;
            }

            final float scaled = (float) (Math.max(0.0, Math.min(1.0, bank.fill().applyAsDouble(tick))) * 8.0);
            int lit = Math.max(0, Math.min(8, Math.round(scaled)));
            if (shownLevel[i] >= 0 && Math.abs(scaled - shownLevel[i]) < 0.65f) {
                lit = shownLevel[i];
            }
            if (lit != shownLevel[i]) {
                shownLevel[i] = lit;
                if (state.getValue(CapacitorBankBlock.LEVEL) != lit) {
                    scene.getWorld().setBlock(bank.pos(), state.setValue(CapacitorBankBlock.LEVEL, lit), 2);
                    redraw = true;
                }
            }
        }
        if (redraw) {
            scene.forEach(WorldSectionElement.class, WorldSectionElement::queueRedraw);
        }
    }

    private static void write(PonderScene scene, TrackFeederBE be, RailStory.FeederFrame f) {
        final CompoundTag tag = be.saveWithoutMetadata(scene.getWorld().registryAccess());
        tag.putInt("Energy", f.buffer());
        tag.putBoolean("FloorHolding", f.floorHolding());
        tag.putBoolean("AutoSupplying", f.autoSupplying());
        tag.putInt("Reason", f.reason().ordinal());
        tag.putInt("RailFlow", f.railFlow());
        tag.putInt("Lamp", f.lamp());
        tag.put("Section", f.view().write());
        tag.putInt("ScrollValue", PowerTrackPonderBeats.dialValue(f.mode(), f.dialFe()));
        tag.putInt("FloorSetting", Math.max(0, Math.min(9, f.floorPct() / 10)));
        load(scene, be, tag);
    }

    private static void load(PonderScene scene, BlockEntity be, CompoundTag tag) {
        try {
            be.loadCustomOnly(tag, scene.getWorld().registryAccess());
        } catch (RuntimeException e) {
            CreateStats.LOGGER.warn("[Ponder] a Power Track block could not be written: {}", e.toString());
        }
    }

    static SectionView lamp(RailStory.Frame f, SectionGraph sections, InsulatorAt insulator, boolean west) {
        return RailStory.lampView(f.sections(), sections, insulator.lo(), insulator.hi(), insulator.cutAt(), west);
    }
}
