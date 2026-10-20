package com.createstats.aviatorhelmet;

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

public class AviatorHelmetBE extends BlockEntity implements IHaveGoggleInformation {
    private AviatorGoggles goggles = AviatorGoggles.DEFAULT;

    public AviatorHelmetBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public AviatorGoggles goggles() {
        return goggles;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.create_stats.aviator_helmet"))
                .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal("    ").append(AviatorHelmetItem.toneLine(goggles)).withStyle(ChatFormatting.GRAY));
        return true;
    }

    public boolean flick() {
        final Level level = getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        final long now = level.getGameTime();
        if (!AviatorModes.acceptsFlick(goggles.strokeTick(), now)) {
            return false;
        }
        goggles = goggles.flicked(now);
        sync(level);
        level.playSound(null, worldPosition,
                (goggles.down() ? ModSounds.AVIATOR_GOGGLES_DOWN : ModSounds.AVIATOR_GOGGLES_UP).get(),
                SoundSource.BLOCKS, 0.8f, 1.0f);
        return true;
    }

    public void nextTone() {
        final Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        goggles = goggles.nextTone();
        sync(level);
        level.playSound(null, worldPosition, ModSounds.AVIATOR_HELMET_TONE.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
    }

    private void sync(Level level) {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        goggles = input.getOrDefault(ModDataComponents.AVIATOR_GOGGLES.get(), AviatorGoggles.DEFAULT).settled();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(ModDataComponents.AVIATOR_GOGGLES.get(), goggles.settled());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        AviatorGoggles.CODEC.encodeStart(NbtOps.INSTANCE, goggles).result().ifPresent(t -> tag.put("Goggles", t));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        goggles = tag.contains("Goggles")
                ? AviatorGoggles.CODEC.parse(NbtOps.INSTANCE, tag.get("Goggles")).result().orElse(AviatorGoggles.DEFAULT)
                : AviatorGoggles.DEFAULT;
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
