package com.createstats.display;

import com.createstats.antenna.AntennaTier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public interface DisplayLinkDimensionExtension {
    @Nullable
    ResourceLocation createStats$getTargetDimension();

    void createStats$setTargetDimension(@Nullable ResourceLocation dimension);

    @Nullable
    AntennaTier createStats$getTargetAntennaTier();

    void createStats$setTargetAntennaTier(@Nullable AntennaTier tier);

    @Nullable
    ResourceLocation createStats$getTargetTypeId();

    void createStats$setTargetTypeId(@Nullable ResourceLocation targetTypeId);

    @Nullable
    BlockPos createStats$getStoredTargetPos();

    void createStats$setStoredTargetPos(@Nullable BlockPos targetPos);
}