package com.createstats.mixin;

import com.createstats.client.DieselMultiblockOutlineClientHandler;
import com.createstats.compat.DieselCompatHelper;
import com.createstats.display.DisplayLinkDimensionExtension;
import com.createstats.network.DisplayLinkDieselFuelReservePacket;
import com.createstats.network.DisplayLinkDieselModePacket;
import com.createstats.registry.ModBlocks;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkScreen;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DisplayLinkScreen.class, remap = false)
public abstract class DisplayLinkScreenMixin extends AbstractSimiScreen {
    @Unique
    private static final ResourceLocation CREATE_STATS_STATS_PANEL_TARGET = ResourceLocation.tryParse("create_stats:stats_panel");

    @Shadow private DisplayLinkBlockEntity blockEntity;

    protected DisplayLinkScreenMixin() {
        super(Component.empty());
    }

    @Unique
    private Button createStats$dieselModeButton;

    @Unique
    private Button createStats$fuelReserveButton;

    @Unique
    private boolean createStats$dieselModeMulti;

    @Unique
    private String createStats$fuelReserveType = "auto";

    @Inject(method = "init", at = @At("TAIL"))
    private void createStats$addDieselModeButton(CallbackInfo ci) {
        final BlockEntity sourceBE = this.createStats$getSourceBE();
        final boolean showDieselMode = DieselCompatHelper.isDieselEngineBlockEntity(sourceBE);
        final boolean showFuelReserve = DieselCompatHelper.isFuelReserveCandidate(sourceBE) || DieselCompatHelper.hasFuelReserveConfig(this.blockEntity.getSourceConfig());
        if (!showDieselMode && !showFuelReserve) {
            this.createStats$dieselModeButton = null;
            this.createStats$fuelReserveButton = null;
            return;
        }
        int buttonY = this.guiTop + 87;
        if (showDieselMode) {
            this.createStats$dieselModeMulti = DieselCompatHelper.isMultiStructureMode(this.blockEntity.getSourceConfig(), sourceBE);
            this.createStats$dieselModeButton = Button.builder(this.createStats$dieselModeLabel(), button -> this.createStats$toggleDieselMode())
                    .bounds(this.guiLeft + 61, buttonY, 135, 16)
                    .build();
            this.addRenderableWidget(this.createStats$dieselModeButton);
            buttonY += 18;
            if (this.createStats$dieselModeMulti) {
                DieselMultiblockOutlineClientHandler.showFor(sourceBE, 80);
            }
        } else {
            this.createStats$dieselModeButton = null;
        }
        if (showFuelReserve) {
            this.createStats$fuelReserveType = DieselCompatHelper.getFuelReserveType(this.blockEntity.getSourceConfig(), sourceBE);
            this.createStats$fuelReserveButton = Button.builder(this.createStats$fuelReserveLabel(), button -> this.createStats$cycleFuelReserve())
                    .bounds(this.guiLeft + 61, buttonY, 135, 16)
                    .build();
            this.addRenderableWidget(this.createStats$fuelReserveButton);
        } else {
            this.createStats$fuelReserveButton = null;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void createStats$animateDieselModeButton(CallbackInfo ci) {
        if (this.createStats$dieselModeButton != null) {
            this.createStats$dieselModeButton.setMessage(this.createStats$dieselModeLabel());
        }
        if (this.createStats$fuelReserveButton != null) {
            this.createStats$fuelReserveButton.setMessage(this.createStats$fuelReserveLabel());
        }
    }

    @Inject(method = "onClose", at = @At("TAIL"))
    private void createStats$persistDieselModeOnClose(CallbackInfo ci) {
        if (this.createStats$dieselModeButton != null) {
            this.createStats$sendDieselMode();
        }
        if (this.createStats$fuelReserveButton != null) {
            this.createStats$sendFuelReserveMode();
        }
    }

    @Redirect(method = "initGathererOptions", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/api/behaviour/display/DisplayTarget;get(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Lcom/simibubi/create/api/behaviour/display/DisplayTarget;"))
    private DisplayTarget createStats$getStoredTargetFallback(LevelAccessor level, BlockPos pos) {
        DisplayTarget localTarget = DisplayTarget.get(level, pos);
        if (localTarget != null || !(level instanceof Level currentLevel) || !(this.blockEntity instanceof DisplayLinkDimensionExtension extension)) {
            return localTarget;
        }
        ResourceLocation targetDimension = extension.createStats$getTargetDimension();
        ResourceLocation currentDimension = currentLevel.dimension().location();
        boolean crossDimensional = targetDimension != null && !targetDimension.equals(currentDimension);
        boolean targetChunkLoaded = currentLevel.isLoaded(pos);
        if (!crossDimensional && targetChunkLoaded) {
            return null;
        }
        if (this.blockEntity.activeTarget != null) {
            return this.blockEntity.activeTarget;
        }
        ResourceLocation targetTypeId = extension.createStats$getTargetTypeId();
        return targetTypeId == null ? null : DisplayTarget.get(targetTypeId);
    }

    @Redirect(method = "initGathererOptions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 1))
    private BlockState createStats$getCrossDimensionalTargetPreviewState(ClientLevel level, BlockPos pos) {
        return createStats$getTargetPreviewState(level, pos);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 1))
    private BlockState createStats$getCrossDimensionalTickTargetState(ClientLevel level, BlockPos pos) {
        return createStats$getTargetPreviewState(level, pos);
    }

    @Unique
    private BlockState createStats$getTargetPreviewState(ClientLevel level, BlockPos pos) {
        BlockState localState = level.getBlockState(pos);
        if (!(this.blockEntity instanceof DisplayLinkDimensionExtension extension)) {
            return localState;
        }
        ResourceLocation targetDimension = extension.createStats$getTargetDimension();
        ResourceLocation currentDimension = level.dimension().location();
        boolean crossDimensional = targetDimension != null && !targetDimension.equals(currentDimension);
        boolean targetChunkLoaded = level.isLoaded(pos);
        if (!crossDimensional && targetChunkLoaded) {
            return localState;
        }
        ResourceLocation targetTypeId = extension.createStats$getTargetTypeId();
        if (CREATE_STATS_STATS_PANEL_TARGET != null && CREATE_STATS_STATS_PANEL_TARGET.equals(targetTypeId)) {
            return ModBlocks.STATS_DISPLAY_PANEL.get().defaultBlockState();
        }
        return localState;
    }

    @Unique
    private void createStats$toggleDieselMode() {
        this.createStats$dieselModeMulti = !this.createStats$dieselModeMulti;
        DieselCompatHelper.setMultiStructureMode(this.blockEntity.getSourceConfig(), this.createStats$dieselModeMulti);
        this.createStats$sendDieselMode();
        final BlockEntity sourceBE = this.createStats$getSourceBE();
        if (this.createStats$dieselModeMulti && sourceBE != null) {
            DieselMultiblockOutlineClientHandler.showFor(sourceBE, 100);
        }
        if (this.createStats$dieselModeButton != null) {
            this.createStats$dieselModeButton.setMessage(this.createStats$dieselModeLabel());
        }
    }

    @Unique
    private void createStats$sendDieselMode() {
        PacketDistributor.sendToServer(new DisplayLinkDieselModePacket(this.blockEntity.getBlockPos(), this.createStats$dieselModeMulti));
    }

    @Unique
    private void createStats$cycleFuelReserve() {
        this.createStats$fuelReserveType = DieselCompatHelper.nextFuelReserveType(this.createStats$fuelReserveType);
        DieselCompatHelper.setFuelReserveMode(this.blockEntity.getSourceConfig(), !"off".equals(this.createStats$fuelReserveType), this.createStats$fuelReserveType);
        this.createStats$sendFuelReserveMode();
        if (this.createStats$fuelReserveButton != null) {
            this.createStats$fuelReserveButton.setMessage(this.createStats$fuelReserveLabel());
        }
    }

    @Unique
    private void createStats$sendFuelReserveMode() {
        PacketDistributor.sendToServer(new DisplayLinkDieselFuelReservePacket(this.blockEntity.getBlockPos(), !"off".equals(this.createStats$fuelReserveType), this.createStats$fuelReserveType));
    }

    @Unique
    private Component createStats$dieselModeLabel() {
        if (!this.createStats$dieselModeMulti) {
            return Component.literal("Diesel Mode: Single");
        }
        final String[] frames = {"|", "/", "-", "\\"};
        final long tick = this.blockEntity.getLevel() != null ? this.blockEntity.getLevel().getGameTime() : 0L;
        return Component.literal("Diesel Mode: Multi " + frames[(int)(tick / 6L % frames.length)]);
    }

    @Unique
    private Component createStats$fuelReserveLabel() {
        final String label = switch (this.createStats$fuelReserveType) {
            case "diesel" -> "Diesel";
            case "gasoline" -> "Gasoline";
            case "biodiesel" -> "Biodiesel";
            case "ethanol" -> "Ethanol";
            case "plantoil" -> "Plant Oil";
            case "off" -> "Off";
            default -> "Auto";
        };
        return Component.literal("Fuel Reserve: " + label);
    }

    @Unique
    private BlockEntity createStats$getSourceBE() {
        final Level level = this.blockEntity.getLevel();
        return level == null ? null : level.getBlockEntity(this.blockEntity.getSourcePosition());
    }
}
