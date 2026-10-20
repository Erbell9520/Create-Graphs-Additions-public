package com.createstats.mixin;

import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = Contraption.class, remap = false)
public interface ContraptionUpdateTagsAccessor {
    @Accessor("updateTags")
    Map<BlockPos, CompoundTag> createStats$updateTags();
}
