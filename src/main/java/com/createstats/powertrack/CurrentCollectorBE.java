package com.createstats.powertrack;

import com.createstats.power.FeGoggle;
import com.createstats.power.FeRates;
import com.createstats.powertrack.core.RailPower;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class CurrentCollectorBE extends SmartBlockEntity implements IHaveGoggleInformation {
    public final com.createstats.powertrack.core.SlideChase drawnSlide = new com.createstats.powertrack.core.SlideChase();

    public CurrentCollectorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final int base = FeRates.base();
        tooltip.add(FeGoggle.title(Component.translatable(getBlockState().getBlock().getDescriptionId())));
        tooltip.add(FeGoggle.dim("    " + FeRates.perTick(RailPower.collectorRate(base)) + " a shoe, "
                + RailPower.MAX_COLLECTORS_PER_CAR + " shoes a car count"));
        if (com.createstats.train.power.CarriedReadout.active()) {
            return true;
        }
        if (level != null && level.isClientSide()) {
            com.createstats.powertrack.client.CollectorReadout.placed(tooltip, level, worldPosition, getBlockState());
        }
        tooltip.add(FeGoggle.dim("    Hang it under a car, one block above the track,"));
        tooltip.add(FeGoggle.dim("    on its centre line or a block to either side"));
        if (isPlayerSneaking) {
            tooltip.add(FeGoggle.dim("    The shoe slides across to the centre rail: it"));
            tooltip.add(FeGoggle.dim("    charges the car's Capacitor Banks on a live section"));
            tooltip.add(FeGoggle.dim("    and gives to a collecting one, down to its floor"));
        }
        return true;
    }
}
