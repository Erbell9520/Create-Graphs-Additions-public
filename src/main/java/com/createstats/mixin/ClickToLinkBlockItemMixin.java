package com.createstats.mixin;

import com.createstats.DebugLogging;
import com.createstats.antenna.AntennaLinkHelper;
import com.createstats.antenna.AntennaRange;
import com.createstats.antenna.AntennaTier;
import com.createstats.display.DisplayLinkDimensionExtension;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.redstone.displayLink.ClickToLinkBlockItem;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockItem;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClickToLinkBlockItem.class, remap = false)
public abstract class ClickToLinkBlockItemMixin {
    @Unique
    private static final String CREATE_STATS_TARGET_TIER_KEY = "CreateStatsTargetAntennaTier";
    @Unique
    private static final String CREATE_STATS_TARGET_TYPE_KEY = "CreateStatsTargetType";
    @Unique
    private static final String CREATE_STATS_PLACED_TARGET_TIER_KEY = "TargetAntennaTier";

    @Unique
    private static final ThreadLocal<UseOnContext> createStats$activeUseContext = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<ClickToLinkBlockItem.ClickToLinkData> createStats$activeClickData = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<ClickToLinkBlockItem.ClickToLinkData> createStats$forcedOriginalClickData = new ThreadLocal<>();

    @Inject(method = "useOn", at = @At("HEAD"))
    private void createStats$captureUseContext(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        createStats$activeUseContext.set(context);
        ClickToLinkBlockItem.ClickToLinkData activeData = context.getItemInHand().get(AllDataComponents.CLICK_TO_LINK_DATA);
        createStats$activeClickData.set(activeData);
        if ((Object) this instanceof DisplayLinkBlockItem) {
            createStats$clearLegacyUnsafeCache(context.getItemInHand());
        }
        createStats$prepareForcedPlacement(context, activeData);
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void createStats$clearUseContext(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        ClickToLinkBlockItem.ClickToLinkData forcedOriginal = createStats$forcedOriginalClickData.get();
        createStats$repairPlacedDisplayLink(context, forcedOriginal, cir.getReturnValue());
        if (forcedOriginal != null) {
            ItemStack stack = context.getItemInHand();
            if (stack.has(AllDataComponents.CLICK_TO_LINK_DATA)) {
                stack.set(AllDataComponents.CLICK_TO_LINK_DATA, forcedOriginal);
            }
            createStats$forcedOriginalClickData.remove();
        }
        createStats$rememberSelectedDisplayTarget(context, cir.getReturnValue());
        createStats$activeClickData.remove();
        createStats$activeUseContext.remove();
    }

    @Redirect(method = "useOn", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/redstone/displayLink/ClickToLinkBlockItem;getMaxDistanceFromSelection()I"))
    private int createStats$overrideMaxDistance(ClickToLinkBlockItem item) {
        int configured = item.getMaxDistanceFromSelection();
        UseOnContext context = createStats$activeUseContext.get();
        if (context == null || !((Object) this instanceof DisplayLinkBlockItem)) {
            return configured;
        }
        ItemStack stack = context.getItemInHand();
        ClickToLinkBlockItem.ClickToLinkData data = createStats$getActiveClickData(stack);
        AntennaTier cachedTier = createStats$getCachedTargetTier(stack);
        if (data != null
                && data.selectedDim().equals(context.getLevel().dimension().location())
                && cachedTier != null
                && cachedTier.isAttached()) {
            return AntennaRange.scale(configured, cachedTier);
        }
        return configured;
    }

    @Redirect(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"))
    private boolean createStats$allowAntennaDistance(BlockPos selectedPos, Vec3i placedPos, double maxDistance) {
        if (selectedPos.closerThan(placedPos, maxDistance)) {
            return true;
        }
        return createStats$canUseDisplayLinkAntenna(selectedPos, createStats$asBlockPos(placedPos),
                ((ClickToLinkBlockItem) (Object) this).getMaxDistanceFromSelection());
    }

    @Redirect(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;equals(Ljava/lang/Object;)Z"))
    private boolean createStats$allowAntennaDimension(ResourceLocation selectedDimension, Object placedDimension) {
        if (selectedDimension.equals(placedDimension)) {
            return true;
        }
        UseOnContext context = createStats$activeUseContext.get();
        if (context == null || !(placedDimension instanceof ResourceLocation)) {
            return false;
        }
        ItemStack stack = context.getItemInHand();
        ClickToLinkBlockItem.ClickToLinkData data = stack.get(AllDataComponents.CLICK_TO_LINK_DATA);
        if (data == null) {
            return false;
        }
        BlockPos placedPos = context.getClickedPos().relative(context.getClickedFace(), context.getLevel().getBlockState(context.getClickedPos()).canBeReplaced() ? 0 : 1);
        return createStats$canUseDisplayLinkAntenna(data.selectedPos(), placedPos, ((ClickToLinkBlockItem) (Object) this).getMaxDistanceFromSelection());
    }

    @Redirect(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/component/CustomData;of(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/item/component/CustomData;"))
    private CustomData createStats$writeTargetTypeToPlacedDisplayLink(CompoundTag tag) {
        createStats$rewriteForcedPlacementTarget(tag);
        createStats$writeSelectedTargetType(tag);
        return CustomData.of(tag);
    }

    @Unique
    private boolean createStats$canUseDisplayLinkAntenna(BlockPos selectedPos, BlockPos placedPos, double maxDistance) {
        if (!((Object) this instanceof DisplayLinkBlockItem)) {
            return false;
        }
        UseOnContext context = createStats$activeUseContext.get();
        if (context == null) {
            DebugLogging.antennaInfo("[DL antenna] place DENY reason=no-active-context selected={} placed={} base={}",
                    createStats$formatPos(selectedPos), createStats$formatPos(placedPos), maxDistance);
            return false;
        }
        ItemStack stack = context.getItemInHand();
        ClickToLinkBlockItem.ClickToLinkData data = createStats$getActiveClickData(stack);
        if (data == null) {
            createStats$debug(context, "place DENY reason=no-click-data selected=" + createStats$formatPos(selectedPos)
                    + " placed=" + createStats$formatPos(placedPos)
                    + " base=" + maxDistance
                    + " cachedTier=" + createStats$tierName(createStats$getCachedTargetTier(stack)));
            return false;
        }
        AntennaTier cachedTier = createStats$getCachedTargetTier(stack);
        boolean allowed = AntennaLinkHelper.canUseDisplayLinkAntenna(context.getLevel(), placedPos, selectedPos, data.selectedDim(), maxDistance, cachedTier);
        createStats$debug(context, "place " + (allowed ? "ALLOW" : "DENY")
            + " side=" + (context.getLevel().isClientSide() ? "client" : "server")
            + " selected=" + createStats$formatPos(selectedPos)
            + " placed=" + createStats$formatPos(placedPos)
            + " linkDim=" + context.getLevel().dimension().location()
            + " targetDim=" + data.selectedDim()
            + " dist=" + String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(placedPos.distSqr(selectedPos)))
            + " base=" + maxDistance
            + " cachedTier=" + createStats$tierName(cachedTier)
            + createStats$liveTargetDebug(context, data.selectedDim(), selectedPos));
        return allowed;
    }

    @Unique
    private void createStats$prepareForcedPlacement(UseOnContext context,
                                                    @org.jetbrains.annotations.Nullable ClickToLinkBlockItem.ClickToLinkData activeData) {
        if (!((Object) this instanceof DisplayLinkBlockItem) || activeData == null) {
            return;
        }
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return;
        }
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockPos placedPos = clickedPos.relative(context.getClickedFace(), clickedState.canBeReplaced() ? 0 : 1);
        ResourceLocation placedDim = level.dimension().location();
        BlockPos selectedPos = activeData.selectedPos();
        ResourceLocation selectedDim = activeData.selectedDim();
        int maxDistance = ((ClickToLinkBlockItem) (Object) this).getMaxDistanceFromSelection();
        if (maxDistance == -1) {
            return;
        }
        if (selectedPos.closerThan(placedPos, maxDistance) && selectedDim.equals(placedDim)) {
            return;
        }
        AntennaTier cachedTier = createStats$getCachedTargetTier(stack);
        if (!AntennaLinkHelper.canUseDisplayLinkAntenna(level, placedPos, selectedPos, selectedDim, maxDistance, cachedTier)) {
            return;
        }
        createStats$forcedOriginalClickData.set(activeData);
        stack.set(AllDataComponents.CLICK_TO_LINK_DATA, new ClickToLinkBlockItem.ClickToLinkData(placedPos, placedDim));
        createStats$debug(context, "place PREPASS allow selected=" + createStats$formatPos(selectedPos)
                + " placed=" + createStats$formatPos(placedPos)
                + " targetDim=" + selectedDim
                + " placedDim=" + placedDim
                + " cachedTier=" + createStats$tierName(cachedTier));
    }

    @Unique
    private static void createStats$rewriteForcedPlacementTarget(CompoundTag tag) {
        ClickToLinkBlockItem.ClickToLinkData forcedOriginal = createStats$forcedOriginalClickData.get();
        UseOnContext context = createStats$activeUseContext.get();
        if (forcedOriginal == null || context == null) {
            return;
        }
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = context.getLevel().getBlockState(clickedPos);
        BlockPos placedPos = clickedPos.relative(context.getClickedFace(), clickedState.canBeReplaced() ? 0 : 1);
        tag.put("TargetOffset", NbtUtils.writeBlockPos(forcedOriginal.selectedPos().subtract(placedPos)));
        NBTHelper.writeResourceLocation(tag, "TargetDimension", forcedOriginal.selectedDim());
    }

    @Unique
    private void createStats$repairPlacedDisplayLink(UseOnContext context,
                                                     @org.jetbrains.annotations.Nullable ClickToLinkBlockItem.ClickToLinkData forcedOriginal,
                                                     InteractionResult result) {
        if (forcedOriginal == null || !result.consumesAction() || !(context.getLevel() instanceof ServerLevel sourceLevel)) {
            return;
        }
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = sourceLevel.getBlockState(clickedPos);
        BlockPos placedPos = clickedPos.relative(context.getClickedFace(), clickedState.canBeReplaced() ? 0 : 1);
        BlockEntity blockEntity = sourceLevel.getBlockEntity(placedPos);
        if (!(blockEntity instanceof DisplayLinkBlockEntity displayLink)) {
            createStats$debug(context, "place POSTFIX skip reason=no-display-link-be placed=" + createStats$formatPos(placedPos));
            return;
        }

        ItemStack stack = context.getItemInHand();

        ResourceLocation targetType = createStats$resolveTargetType(sourceLevel, stack, forcedOriginal);
        AntennaTier targetTier = createStats$getPlacedTargetTier(sourceLevel, forcedOriginal);
        if (targetTier == null) {
            targetTier = createStats$getCachedTargetTier(stack);
        }

        if (displayLink instanceof DisplayLinkDimensionExtension extension) {
            extension.createStats$setTargetDimension(forcedOriginal.selectedDim());
            extension.createStats$setTargetTypeId(targetType);
            extension.createStats$setTargetAntennaTier(targetTier);
            extension.createStats$setStoredTargetPos(forcedOriginal.selectedPos());
        } else {
            displayLink.target(forcedOriginal.selectedPos());
        }

        displayLink.updateGatheredData();
        displayLink.setChanged();
        displayLink.sendData();

        createStats$debug(context, "place POSTFIX repaired placed=" + createStats$formatPos(placedPos)
                + " target=" + createStats$formatPos(forcedOriginal.selectedPos())
                + " targetDim=" + forcedOriginal.selectedDim()
                + " targetType=" + (targetType == null ? "none" : targetType)
                + " targetTier=" + createStats$tierName(targetTier));
    }

    @Unique
    private void createStats$rememberSelectedDisplayTarget(UseOnContext context, InteractionResult result) {
        if (!result.consumesAction() || !((Object) this instanceof DisplayLinkBlockItem) || !(context.getLevel() instanceof ServerLevel targetLevel)) {
            return;
        }
        ItemStack stack = context.getItemInHand();
        ClickToLinkBlockItem.ClickToLinkData data = createStats$getActiveClickData(stack);
        if (data == null || !data.selectedPos().equals(context.getClickedPos()) || !data.selectedDim().equals(targetLevel.dimension().location())) {
            return;
        }
        BlockEntity targetBE = targetLevel.getBlockEntity(data.selectedPos());
        AntennaTier tier = AntennaLinkHelper.getAttachedTier(targetBE);
        DisplayTarget target = DisplayTarget.get(targetLevel, data.selectedPos());
        ResourceLocation targetType = target == null ? null : CreateBuiltInRegistries.DISPLAY_TARGET.getKey(target);
        ResourceLocation finalTargetType = targetType;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(CREATE_STATS_TARGET_TIER_KEY, tier.getSerializedName());
            if (finalTargetType != null) {
                tag.putString(CREATE_STATS_TARGET_TYPE_KEY, finalTargetType.toString());
            } else {
                tag.remove(CREATE_STATS_TARGET_TYPE_KEY);
            }
        });
        createStats$debug(context, "select target=" + createStats$formatPos(data.selectedPos())
                + " dim=" + data.selectedDim()
                + " tier=" + tier.getSerializedName()
                + " targetType=" + (targetType == null ? "none" : targetType)
                + " be=" + (targetBE == null ? "none" : targetBE.getClass().getSimpleName()));
    }

    @Unique
    private void createStats$writeSelectedTargetType(CompoundTag placementTag) {
        if (!((Object) this instanceof DisplayLinkBlockItem)) {
            return;
        }
        UseOnContext context = createStats$activeUseContext.get();
        if (context == null) {
            return;
        }
        ItemStack stack = context.getItemInHand();
        ClickToLinkBlockItem.ClickToLinkData data = createStats$getActiveClickData(stack);
        if (data == null || !(context.getLevel() instanceof ServerLevel sourceLevel) || sourceLevel.getServer() == null) {
            return;
        }
        ResourceLocation targetType = createStats$resolveTargetType(sourceLevel, stack, data);
        if (targetType != null) {
            placementTag.putString("TargetType", targetType.toString());
        }
        AntennaTier targetTier = createStats$getPlacedTargetTier(sourceLevel, data);
        if (targetTier == null) {
            targetTier = createStats$getCachedTargetTier(stack);
        }
        if (targetTier != null) {
            placementTag.putString(CREATE_STATS_PLACED_TARGET_TIER_KEY, targetTier.getSerializedName());
        }
        createStats$debug(context, "write placement tag targetType=" + (targetType == null ? "none" : targetType)
                + " targetTier=" + createStats$tierName(targetTier));
    }

    @Unique
    @org.jetbrains.annotations.Nullable
    private static ClickToLinkBlockItem.ClickToLinkData createStats$getActiveClickData(ItemStack stack) {
        ClickToLinkBlockItem.ClickToLinkData activeData = createStats$activeClickData.get();
        return activeData != null ? activeData : stack.get(AllDataComponents.CLICK_TO_LINK_DATA);
    }

    @Unique
    @org.jetbrains.annotations.Nullable
    private static AntennaTier createStats$getPlacedTargetTier(ServerLevel sourceLevel, ClickToLinkBlockItem.ClickToLinkData data) {
        if (sourceLevel.getServer() == null) {
            return null;
        }
        ServerLevel targetLevel = sourceLevel.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, data.selectedDim()));
        if (targetLevel == null || !AntennaRange.isChunkLoaded(targetLevel, data.selectedPos())) {
            return null;
        }
        return AntennaLinkHelper.getAttachedTier(targetLevel.getBlockEntity(data.selectedPos()));
    }

    @Unique
    @org.jetbrains.annotations.Nullable
    private static ResourceLocation createStats$resolveTargetType(ServerLevel sourceLevel,
                                                                 ItemStack stack,
                                                                 ClickToLinkBlockItem.ClickToLinkData data) {
        if (sourceLevel.getServer() != null) {
            ServerLevel targetLevel = sourceLevel.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, data.selectedDim()));
            if (targetLevel != null && AntennaRange.isChunkLoaded(targetLevel, data.selectedPos())) {
                DisplayTarget target = DisplayTarget.get(targetLevel, data.selectedPos());
                ResourceLocation targetType = target == null ? null : CreateBuiltInRegistries.DISPLAY_TARGET.getKey(target);
                if (targetType != null) {
                    return targetType;
                }
            }
        }
        return createStats$getCachedTargetType(stack);
    }

    @Unique
    @org.jetbrains.annotations.Nullable
    private static AntennaTier createStats$getCachedTargetTier(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag tag = customData.copyTag();
        return tag.contains(CREATE_STATS_TARGET_TIER_KEY, 8) ? AntennaTier.byName(tag.getString(CREATE_STATS_TARGET_TIER_KEY)) : null;
    }

    @Unique
    @org.jetbrains.annotations.Nullable
    private static ResourceLocation createStats$getCachedTargetType(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag tag = customData.copyTag();
        return tag.contains(CREATE_STATS_TARGET_TYPE_KEY, 8) ? ResourceLocation.tryParse(tag.getString(CREATE_STATS_TARGET_TYPE_KEY)) : null;
    }

    @Unique
    private static void createStats$clearLegacyUnsafeCache(ItemStack stack) {
        CustomData legacyData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (legacyData == null) {
            return;
        }
        CompoundTag tag = legacyData.copyTag();
        if (!tag.contains(CREATE_STATS_TARGET_TIER_KEY) && !tag.contains(CREATE_STATS_TARGET_TYPE_KEY)) {
            return;
        }
        tag.remove(CREATE_STATS_TARGET_TIER_KEY);
        tag.remove(CREATE_STATS_TARGET_TYPE_KEY);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.BLOCK_ENTITY_DATA);
        } else {
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
        }
    }

    @Unique
    private static String createStats$liveTargetDebug(UseOnContext context, ResourceLocation targetDimension, BlockPos targetPos) {
        if (!(context.getLevel() instanceof ServerLevel sourceLevel) || sourceLevel.getServer() == null) {
            return "";
        }
        ServerLevel targetLevel = sourceLevel.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, targetDimension));
        if (targetLevel == null) {
            return " liveTarget=missing-dim";
        }
        boolean loaded = AntennaRange.isChunkLoaded(targetLevel, targetPos);
        BlockEntity targetBE = loaded ? targetLevel.getBlockEntity(targetPos) : null;
        AntennaTier tier = targetBE == null ? AntennaTier.NONE : AntennaLinkHelper.getAttachedTier(targetBE);
        DisplayTarget target = loaded ? DisplayTarget.get(targetLevel, targetPos) : null;
        ResourceLocation targetType = target == null ? null : CreateBuiltInRegistries.DISPLAY_TARGET.getKey(target);
        return " liveLoaded=" + loaded
                + " liveTier=" + tier.getSerializedName()
                + " liveType=" + (targetType == null ? "none" : targetType)
                + " liveBE=" + (targetBE == null ? "none" : targetBE.getClass().getSimpleName());
    }

    @Unique
    private static String createStats$formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    @Unique
    private static String createStats$tierName(@org.jetbrains.annotations.Nullable AntennaTier tier) {
        return tier == null ? "missing" : tier.getSerializedName();
    }

    @Unique
    private static void createStats$debug(UseOnContext context, String message) {
        if (!DebugLogging.antennaDebugEnabled()) {
            return;
        }
        String full = "[DL antenna] " + message;
        DebugLogging.antennaInfo(full);
    }

    @Unique
    private static BlockPos createStats$asBlockPos(Vec3i pos) {
        return pos instanceof BlockPos blockPos ? blockPos : new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }
}