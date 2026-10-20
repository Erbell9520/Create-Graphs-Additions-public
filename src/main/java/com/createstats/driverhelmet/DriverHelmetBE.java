package com.createstats.driverhelmet;

import com.createstats.registry.ModDataComponents;
import com.createstats.registry.ModSounds;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class DriverHelmetBE extends BlockEntity implements IHaveGoggleInformation {
    private HelmetVisor visor = HelmetVisor.DEFAULT;

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.create_stats.driver_helmet"))
                .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal("    ").append(DriverHelmetItem.themeLine(visor)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("    ").append(DriverHelmetItem.odometerLine(visor)).withStyle(ChatFormatting.GRAY));
        return true;
    }

    public DriverHelmetBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isOpen() {
        return visor.open();
    }

    public long strokeTick() {
        return visor.strokeTick();
    }

    public boolean flick() {
        final Level level = getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        final long now = level.getGameTime();
        if (!VisorModes.acceptsFlick(visor.strokeTick(), now)) {
            return false;
        }
        visor = visor.flicked(now);
        sync(level);
        level.playSound(null, worldPosition,
                (visor.open() ? ModSounds.DRIVER_HELMET_VISOR_OPEN : ModSounds.DRIVER_HELMET_VISOR_CLOSE).get(),
                SoundSource.BLOCKS, 0.8f, 1.0f);
        return true;
    }

    public void nextTheme() {
        final Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        visor = visor.nextTheme();
        final BlockState state = getBlockState();
        if (state.hasProperty(DriverHelmetBlock.LIVERY)) {
            level.setBlock(worldPosition, state.setValue(DriverHelmetBlock.LIVERY, visor.theme()), Block.UPDATE_ALL);
        }
        sync(level);
        level.playSound(null, worldPosition, ModSounds.DRIVER_HELMET_THEME.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
    }

    private void sync(Level level) {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        final HelmetVisor v = input.getOrDefault(ModDataComponents.HELMET_VISOR.get(), HelmetVisor.DEFAULT);
        visor = new HelmetVisor(v.open(), 0L, v.nightVision(), v.theme(), v.odometer());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(ModDataComponents.HELMET_VISOR.get(),
                new HelmetVisor(visor.open(), 0L, visor.nightVision(), visor.theme(), visor.odometer()));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        HelmetVisor.CODEC.encodeStart(NbtOps.INSTANCE, visor).result().ifPresent(t -> tag.put("Helmet", t));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Helmet")) {
            visor = HelmetVisor.CODEC.parse(NbtOps.INSTANCE, tag.get("Helmet")).result().orElse(HelmetVisor.DEFAULT);
        } else {
            visor = new HelmetVisor(tag.getBoolean("Open"), tag.getLong("Stroke"), false, 0, 0.0);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
