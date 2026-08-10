package com.createstats.mixin;

import com.createstats.DebugLogging;
import com.createstats.antenna.AntennaLinkHelper;
import com.createstats.antenna.AntennaRange;
import com.createstats.antenna.AntennaTier;
import com.createstats.display.CrossDimDisplayLinkContext;
import com.createstats.display.DisplayLinkDimensionExtension;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = DisplayLinkBlockEntity.class, remap = false)
public abstract class DisplayLinkBlockEntityMixin implements DisplayLinkDimensionExtension {
    @Shadow protected BlockPos targetOffset;
    @Shadow public DisplaySource activeSource;
    @Shadow private CompoundTag sourceConfig;
    @Shadow public DisplayTarget activeTarget;
    @Shadow public int targetLine;

    @Shadow public abstract BlockPos getSourcePosition();
    @Shadow public abstract BlockPos getTargetPosition();

    @Unique
    @Nullable
    private ResourceLocation createStats$targetDimension;

    @Unique
    @Nullable
    private AntennaTier createStats$targetAntennaTier;

    @Unique
    @Nullable
    private ResourceLocation createStats$targetTypeId;

    @Unique
    @Nullable
    private BlockPos createStats$storedTargetPos;

    @Unique
    private long createStats$lastDebugTick;

    @Inject(method = "writeGatheredData", at = @At("TAIL"))
    private void createStats$writeTargetDimension(CompoundTag tag, CallbackInfo ci) {
        if (this.createStats$targetDimension != null) {
            tag.putString("TargetDimension", this.createStats$targetDimension.toString());
        }
        if (this.createStats$targetAntennaTier != null) {
            tag.putString("TargetAntennaTier", this.createStats$targetAntennaTier.getSerializedName());
        }
        if (this.createStats$targetTypeId != null) {
            tag.putString("TargetType", this.createStats$targetTypeId.toString());
        }
        if (this.createStats$storedTargetPos != null) {
            tag.put("CreateStatsTargetPos", NbtUtils.writeBlockPos(this.createStats$storedTargetPos));
            DisplayLinkBlockEntity self = (DisplayLinkBlockEntity) (Object) this;
            tag.put("TargetOffset", NbtUtils.writeBlockPos(this.createStats$storedTargetPos.subtract(self.getBlockPos())));
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void createStats$readTargetDimension(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (tag.contains("TargetDimension", 8)) {
            this.createStats$targetDimension = ResourceLocation.tryParse(tag.getString("TargetDimension"));
        } else {
            this.createStats$targetDimension = null;
        }
        if (tag.contains("TargetType", 8)) {
            this.createStats$targetTypeId = ResourceLocation.tryParse(tag.getString("TargetType"));
        } else {
            this.createStats$targetTypeId = null;
        }
        if (this.activeTarget == null && this.createStats$targetTypeId != null) {
            this.activeTarget = DisplayTarget.get(this.createStats$targetTypeId);
        }
        if (tag.contains("TargetAntennaTier", 8)) {
            this.createStats$targetAntennaTier = AntennaTier.byName(tag.getString("TargetAntennaTier"));
        } else {
            this.createStats$targetAntennaTier = null;
        }
        if (tag.contains("CreateStatsTargetPos", 10)) {
            this.createStats$storedTargetPos = NbtUtils.readBlockPos(tag, "CreateStatsTargetPos").orElse(null);
        } else {
            BlockPos targetPosition = this.getTargetPosition();
            this.createStats$storedTargetPos = targetPosition.equals(BlockPos.ZERO) ? null : targetPosition;
        }
        this.createStats$applyStoredTargetOffset();
    }

    @Inject(method = "target", at = @At("HEAD"), cancellable = true)
    private void createStats$blockPlaceholderTargetReset(BlockPos targetPosition, CallbackInfo ci) {
        if (!this.createStats$shouldIgnoreZeroTargetReset(targetPosition)) {
            return;
        }
        this.createStats$applyStoredTargetOffset();
        DisplayLinkBlockEntity self = (DisplayLinkBlockEntity) (Object) this;
        if (self.getLevel() instanceof ServerLevel serverLevel) {
            createStats$debug(serverLevel, "blocked zero-target reset stored=" + this.createStats$storedTargetPos
                    + " targetDim=" + this.createStats$targetDimension);
        }
        ci.cancel();
    }

    @Inject(method = "target", at = @At("TAIL"))
    private void createStats$rememberLocalTargetDimension(BlockPos targetPosition, CallbackInfo ci) {
        DisplayLinkBlockEntity self = (DisplayLinkBlockEntity) (Object) this;
        boolean placeholderReset = this.createStats$shouldIgnoreZeroTargetReset(targetPosition);
        if (placeholderReset) {
            return;
        }
        if (!targetPosition.equals(BlockPos.ZERO) || this.createStats$storedTargetPos == null) {
            this.createStats$storedTargetPos = targetPosition;
        }
        if (self.getLevel() != null) {
            this.createStats$targetDimension = self.getLevel().dimension().location();
            this.createStats$targetAntennaTier = null;
            DisplayTarget target = DisplayTarget.get(self.getLevel(), targetPosition);
            this.createStats$targetTypeId = target == null ? null : CreateBuiltInRegistries.DISPLAY_TARGET.getKey(target);
        }
    }

    @Inject(method = "getTargetPosition", at = @At("HEAD"), cancellable = true)
    private void createStats$useStoredTargetPosition(CallbackInfoReturnable<BlockPos> cir) {
        if (this.createStats$storedTargetPos == null) {
            return;
        }
        DisplayLinkBlockEntity self = (DisplayLinkBlockEntity) (Object) this;
        BlockPos computedTarget = self.getBlockPos().offset(this.targetOffset);
        if (computedTarget.equals(this.createStats$storedTargetPos)) {
            return;
        }
        if (self.getLevel() == null || !this.createStats$shouldUseStoredTarget(self.getLevel())) {
            return;
        }
        this.targetOffset = this.createStats$storedTargetPos.subtract(self.getBlockPos());
        cir.setReturnValue(this.createStats$storedTargetPos);
    }

    @Inject(method = "updateGatheredData", at = @At("HEAD"), cancellable = true)
    private void createStats$updateCrossDimensionalData(CallbackInfo ci) {
        DisplayLinkBlockEntity self = (DisplayLinkBlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel sourceLevel)) {
            return;
        }
        BlockPos targetPosition = this.createStats$restoreStoredTargetPosition(sourceLevel);
        ResourceLocation linkDimension = sourceLevel.dimension().location();
        ResourceLocation targetDimension = this.createStats$targetDimension != null ? this.createStats$targetDimension : linkDimension;
        if (targetDimension.equals(linkDimension)) {
            double baseRange = AllConfigs.server().logistics.displayLinkRange.get();
            if (!AntennaLinkHelper.requiresAntennaForSameDimension(sourceLevel, self.getBlockPos(), targetPosition, baseRange)) {
                return;
            }
            ci.cancel();
            createStats$runLongSameDimensionUpdate(sourceLevel, targetPosition, baseRange);
            return;
        }
        ci.cancel();
        createStats$runCrossDimensionalUpdate(sourceLevel, targetDimension, targetPosition);
    }

    @Unique
    private void createStats$runLongSameDimensionUpdate(ServerLevel sourceLevel, BlockPos targetPosition, double baseRange) {
        DisplayLinkBlockEntity self = (DisplayLinkBlockEntity) (Object) this;
        BlockPos sourcePosition = this.getSourcePosition();
        if (!sourceLevel.isLoaded(sourcePosition) || !AntennaRange.isChunkLoaded(sourceLevel, targetPosition)) {
            createStats$debug(sourceLevel, "same-dim waiting sourceLoaded=" + sourceLevel.isLoaded(sourcePosition)
                    + " targetLoaded=" + AntennaRange.isChunkLoaded(sourceLevel, targetPosition)
                    + " target=" + targetPosition);
            return;
        }
        BlockEntity targetBE = sourceLevel.getBlockEntity(targetPosition);
        AntennaTier tier = AntennaLinkHelper.getAttachedTier(targetBE);
        this.createStats$targetAntennaTier = tier;
        if (!AntennaRange.canReach(sourceLevel, self.getBlockPos(), sourceLevel, targetPosition, tier, baseRange)) {
            createStats$debug(sourceLevel, "same-dim long link blocked tier=" + tier.getSerializedName()
                    + " link=" + self.getBlockPos() + " target=" + targetPosition + " base=" + baseRange);
            return;
        }
        DisplayTarget target = DisplayTarget.get(sourceLevel, targetPosition);
        this.createStats$targetTypeId = target == null ? null : CreateBuiltInRegistries.DISPLAY_TARGET.getKey(target);
        createStats$debug(sourceLevel, "same-dim tick tier=" + tier.getSerializedName()
                + " targetType=" + (target == null ? "none" : target.getClass().getSimpleName())
                + " sources=" + DisplaySource.getAll(sourceLevel, sourcePosition).size());
        List<DisplaySource> sources = DisplaySource.getAll(sourceLevel, sourcePosition);
        boolean notify = false;
        if (this.activeTarget != target) {
            this.activeTarget = target;
            notify = true;
        }
        if (this.activeSource != null && !sources.contains(this.activeSource)) {
            this.activeSource = null;
            this.sourceConfig = new CompoundTag();
            notify = true;
        }
        if (notify) {
            self.notifyUpdate();
        }
        if (this.activeSource == null || this.activeTarget == null) {
            return;
        }
        CrossDimDisplayLinkContext context = new CrossDimDisplayLinkContext(sourceLevel, self, sourceLevel, targetPosition);
        this.activeSource.transferData(context, this.activeTarget, this.targetLine);
        self.sendPulseNextSync();
        self.sendData();
        self.award(AllAdvancements.DISPLAY_LINK);
    }

    @Unique
    private void createStats$runCrossDimensionalUpdate(ServerLevel sourceLevel, ResourceLocation targetDimension, BlockPos targetPosition) {
        DisplayLinkBlockEntity self = (DisplayLinkBlockEntity) (Object) this;
        if (sourceLevel.getServer() == null) {
            return;
        }
        ServerLevel targetLevel = sourceLevel.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, targetDimension));
        BlockPos sourcePosition = this.getSourcePosition();
        if (targetLevel == null || !sourceLevel.isLoaded(sourcePosition) || !AntennaRange.isChunkLoaded(targetLevel, targetPosition)) {
            createStats$debug(sourceLevel, "cross-dim waiting targetDim=" + targetDimension
                    + " targetLevel=" + (targetLevel != null)
                    + " sourceLoaded=" + sourceLevel.isLoaded(sourcePosition)
                    + " targetLoaded=" + (targetLevel != null && AntennaRange.isChunkLoaded(targetLevel, targetPosition)));
            return;
        }
        BlockEntity targetBE = targetLevel.getBlockEntity(targetPosition);
        AntennaTier tier = AntennaLinkHelper.getAttachedTier(targetBE);
        this.createStats$targetAntennaTier = tier;
        if (!AntennaRange.canReach(sourceLevel, self.getBlockPos(), targetLevel, targetPosition, tier)) {
            createStats$debug(sourceLevel, "cross-dim blocked tier=" + tier.getSerializedName()
                    + " targetBE=" + (targetBE == null ? "none" : targetBE.getClass().getSimpleName())
                    + " target=" + targetPosition + " targetDim=" + targetDimension);
            return;
        }
        DisplayTarget target = DisplayTarget.get(targetLevel, targetPosition);
        this.createStats$targetTypeId = target == null ? null : CreateBuiltInRegistries.DISPLAY_TARGET.getKey(target);
        createStats$debug(sourceLevel, "cross-dim tick tier=" + tier.getSerializedName()
                + " targetType=" + (target == null ? "none" : target.getClass().getSimpleName())
                + " sources=" + DisplaySource.getAll(sourceLevel, sourcePosition).size());
        List<DisplaySource> sources = DisplaySource.getAll(sourceLevel, sourcePosition);
        boolean notify = false;
        if (this.activeTarget != target) {
            this.activeTarget = target;
            notify = true;
        }
        if (this.activeSource != null && !sources.contains(this.activeSource)) {
            this.activeSource = null;
            this.sourceConfig = new CompoundTag();
            notify = true;
        }
        if (notify) {
            self.notifyUpdate();
        }
        if (this.activeSource == null || this.activeTarget == null) {
            return;
        }
        CrossDimDisplayLinkContext context = new CrossDimDisplayLinkContext(sourceLevel, self, targetLevel, targetPosition);
        this.activeSource.transferData(context, this.activeTarget, this.targetLine);
        self.sendPulseNextSync();
        self.sendData();
        self.award(AllAdvancements.DISPLAY_LINK);
    }

    @Unique
    private BlockPos createStats$restoreStoredTargetPosition(ServerLevel sourceLevel) {
        DisplayLinkBlockEntity self = (DisplayLinkBlockEntity) (Object) this;
        BlockPos targetPosition = self.getBlockPos().offset(this.targetOffset);
        if (this.createStats$storedTargetPos == null || this.createStats$storedTargetPos.equals(targetPosition)) {
            return targetPosition;
        }
        if (!this.createStats$shouldUseStoredTarget(sourceLevel) && !targetPosition.equals(BlockPos.ZERO)) {
            return targetPosition;
        }
        BlockPos before = targetPosition;
        this.targetOffset = this.createStats$storedTargetPos.subtract(self.getBlockPos());
        BlockPos restored = self.getBlockPos().offset(this.targetOffset);
        createStats$debug(sourceLevel, "restored stored target previous=" + before
                + " restored=" + restored
                + " targetDim=" + this.createStats$targetDimension);
        return restored;
    }

    @Unique
    private boolean createStats$shouldIgnoreZeroTargetReset(BlockPos targetPosition) {
        if (!targetPosition.equals(BlockPos.ZERO) || this.createStats$storedTargetPos == null) {
            return false;
        }
        DisplayLinkBlockEntity self = (DisplayLinkBlockEntity) (Object) this;
        if (self.getLevel() == null) {
            return false;
        }
        return this.createStats$shouldUseStoredTarget(self.getLevel());
    }

    @Unique
    private boolean createStats$shouldUseStoredTarget(Level level) {
        if (this.createStats$storedTargetPos == null) {
            return false;
        }
        ResourceLocation linkDimension = level.dimension().location();
        ResourceLocation targetDimension = this.createStats$targetDimension != null ? this.createStats$targetDimension : linkDimension;
        if (!targetDimension.equals(linkDimension)) {
            return true;
        }
        double baseRange = AllConfigs.server().logistics.displayLinkRange.get();
        return AntennaLinkHelper.requiresAntennaForSameDimension(level, ((DisplayLinkBlockEntity) (Object) this).getBlockPos(), this.createStats$storedTargetPos, baseRange);
    }

    @Override
    @Nullable
    public ResourceLocation createStats$getTargetDimension() {
        return this.createStats$targetDimension;
    }

    @Override
    public void createStats$setTargetDimension(@Nullable ResourceLocation dimension) {
        this.createStats$targetDimension = dimension;
    }

    @Override
    @Nullable
    public AntennaTier createStats$getTargetAntennaTier() {
        return this.createStats$targetAntennaTier;
    }

    @Override
    public void createStats$setTargetAntennaTier(@Nullable AntennaTier tier) {
        this.createStats$targetAntennaTier = tier;
    }

    @Override
    @Nullable
    public ResourceLocation createStats$getTargetTypeId() {
        return this.createStats$targetTypeId;
    }

    @Override
    public void createStats$setTargetTypeId(@Nullable ResourceLocation targetTypeId) {
        this.createStats$targetTypeId = targetTypeId;
    }

    @Override
    @Nullable
    public BlockPos createStats$getStoredTargetPos() {
        return this.createStats$storedTargetPos;
    }

    @Override
    public void createStats$setStoredTargetPos(@Nullable BlockPos targetPos) {
        this.createStats$storedTargetPos = targetPos;
        this.createStats$applyStoredTargetOffset();
    }

    @Unique
    private void createStats$applyStoredTargetOffset() {
        if (this.createStats$storedTargetPos == null) {
            return;
        }
        DisplayLinkBlockEntity self = (DisplayLinkBlockEntity) (Object) this;
        this.targetOffset = this.createStats$storedTargetPos.subtract(self.getBlockPos());
    }

    @Unique
    private void createStats$debug(ServerLevel level, String message) {
        if (!DebugLogging.antennaDebugEnabled()) {
            return;
        }
        long now = level.getGameTime();
        if (now - this.createStats$lastDebugTick < 40L) {
            return;
        }
        this.createStats$lastDebugTick = now;
        DisplayLinkBlockEntity self = (DisplayLinkBlockEntity) (Object) this;
        DebugLogging.antennaInfo("[DL antenna BE@{}] {}", self.getBlockPos(), message);
    }
}