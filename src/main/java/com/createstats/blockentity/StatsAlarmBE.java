package com.createstats.blockentity;

import com.createstats.antenna.AntennaRange;
import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundSource;
import java.util.Iterator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.Property;
import com.createstats.block.StatsAlarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.UUID;
import net.minecraft.world.level.block.entity.BlockEntity;

public class StatsAlarmBE extends BlockEntity implements RangeAttachment, com.createstats.power.IFeConsumer
{
    /** FE Dependent Mode buffer. Receive-only; exposed as a block capability via FeCapabilities. */
    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_STATS_ALARM));

    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }

    private static final int CHECK_INTERVAL = 20;
    private int checkTimer;
    private int soundTimer;
    private boolean wasActive;
    private boolean needsSync;
    private AlarmSettings alarmSettings;
    private UUID ownerUUID;
    private AntennaTier antennaTier;

    private static final class AlertHit {
        private final StatsDisplayPanelBE panel;
        private final StatsDisplayPanelBE.DataChannel channel;

        private AlertHit(final StatsDisplayPanelBE panel, final StatsDisplayPanelBE.DataChannel channel) {
            this.panel = panel;
            this.channel = channel;
        }
    }
    
    public StatsAlarmBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super((BlockEntityType)type, pos, state);
        this.checkTimer = 0;
        this.soundTimer = 0;
        this.wasActive = false;
        this.needsSync = false;
        this.alarmSettings = new AlarmSettings();
        this.ownerUUID = null;
        this.antennaTier = AntennaTier.NONE;
    }
    
    public AlarmSettings getAlarmSettings() {
        return this.alarmSettings;
    }
    
    public void setAlarmSettings(AlarmSettings settings) {
        this.alarmSettings = settings;
        this.needsSync = true;
        this.setChanged();
    }
    
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }
    
    public void setOwnerUUID(UUID uuid) {
        this.ownerUUID = uuid;
    }

    @Override
    public AntennaTier getAntennaTier() {
        return this.antennaTier;
    }

    @Override
    public void setAntennaTier(AntennaTier tier) {
        AntennaTier resolvedTier = tier == null ? AntennaTier.NONE : tier;
        if (this.antennaTier == resolvedTier) {
            return;
        }
        this.antennaTier = resolvedTier;
        this.needsSync = true;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }
    
    public void tick() {
        final Level level = this.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (this.needsSync) {
            this.needsSync = false;
            level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
        // FE Dependent Mode gate: a dead alarm goes quiet and dark instead of scanning.
        final boolean alarmCurrentlyActive = (boolean) this.getBlockState().getValue(StatsAlarmBlock.ACTIVE);
        final boolean fePowered = this.feConsumer.serviceTick(alarmCurrentlyActive);
        com.createstats.power.FePowerFx.handleTransition(level, this.worldPosition, this.feConsumer);
        if (!fePowered) {
            if (alarmCurrentlyActive) {
                level.setBlock(this.worldPosition, this.getBlockState().setValue(StatsAlarmBlock.ACTIVE, false), 3);
            }
            this.wasActive = false;
            this.soundTimer = 0;
            return;
        }
        ++this.checkTimer;
        if (this.checkTimer < 20) {
            return;
        }
        this.checkTimer = 0;
        // FE burst: one alert network scan. Skipped (and retried next interval) if unaffordable.
        if (!this.feConsumer.spendBurst()) {
            return;
        }
        final AlertHit alertHit = this.scanForAlerts();
        final boolean alertFound = alertHit != null;
        final boolean currentlyActive = (boolean)this.getBlockState().getValue(StatsAlarmBlock.ACTIVE);
        final boolean shouldBeActive = alertFound;
        if (shouldBeActive != currentlyActive) {
            level.setBlock(this.worldPosition, this.getBlockState().setValue(StatsAlarmBlock.ACTIVE, shouldBeActive), 3);
        }
        if (alertFound) {
            ++this.soundTimer;
            final int soundCheckInterval = Math.max(1, this.alarmSettings.getSoundIntervalTicks() / 20);
            if (!this.wasActive || this.soundTimer >= soundCheckInterval) {
                this.soundTimer = 0;
                this.playAlarmEffects();
                if (this.alarmSettings.isScreenAlert()) {
                    this.sendTextAlerts(alertHit);
                }
            }
        }
        else {
            this.soundTimer = 0;
        }
        this.wasActive = alertFound;
    }
    
    private AlertHit scanForAlerts() {
        final Level level = this.getLevel();
        if (level == null) {
            return null;
        }
        final int r = AntennaRange.cappedScanRadius(this.alarmSettings.getScanRadius(), this.antennaTier, 16);
        final java.util.Set<BlockPos> checkedControllers = new java.util.HashSet<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(this.worldPosition.offset(-r, -r, -r), this.worldPosition.offset(r, r, r))) {
            if (pos.equals(this.worldPosition)) {
                continue;
            }
            final BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof StatsDisplayPanelBE panel) || !panel.isController() || !checkedControllers.add(pos)) {
                continue;
            }
            for (StatsDisplayPanelBE.DataChannel ch : panel.getActiveChannels()) {
                if (ch.isAlertTriggered()) {
                    return new AlertHit(panel, ch);
                }
            }
        }
        return null;
    }
    
    private void playAlarmEffects() {
        final Level level = this.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        final double x = this.worldPosition.getX() + 0.5;
        final double y = this.worldPosition.getY() + 0.5;
        final double z = this.worldPosition.getZ() + 0.5;
        if (this.alarmSettings.getSound() != AlarmSettings.AlarmSound.NONE) {
            final SoundEvent sound = this.getSoundEvent(this.alarmSettings.getSound());
            final float vol = this.alarmSettings.getVolume();
            final float pitch = this.alarmSettings.getPitch();
            final int range = AntennaRange.scale(this.alarmSettings.getHearingRange(), this.antennaTier);
            if (this.alarmSettings.getSoundVisibility() == AlarmSettings.AlertVisibility.ALL_PLAYERS) {
                level.playSound((Player)null, this.worldPosition, sound, SoundSource.BLOCKS, vol, pitch);
            }
            else if (level instanceof ServerLevel serverLevel) {
                for (ServerPlayer player : this.getTargetPlayers(serverLevel, this.alarmSettings.getSoundVisibility(), range)) {
                    player.playNotifySound(sound, SoundSource.BLOCKS, vol, pitch);
                }
            }
        }
        if (this.alarmSettings.getParticle() != AlarmSettings.AlarmParticle.NONE && this.alarmSettings.getParticleCount() > 0 && level instanceof ServerLevel) {
            final ServerLevel serverLevel2 = (ServerLevel)level;
            final int count = this.alarmSettings.getParticleCount();
            switch (this.alarmSettings.getParticle()) {
                case SMOKE: {
                    serverLevel2.sendParticles((ParticleOptions)ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 0.6, z, count, 0.15, 0.1, 0.15, 0.01);
                    break;
                }
                case FLAME: {
                    serverLevel2.sendParticles((ParticleOptions)ParticleTypes.FLAME, x, y + 0.5, z, count, 0.2, 0.15, 0.2, 0.02);
                    break;
                }
                case SOUL_FIRE: {
                    serverLevel2.sendParticles((ParticleOptions)ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.5, z, count, 0.2, 0.15, 0.2, 0.02);
                    break;
                }
                case REDSTONE: {
                    serverLevel2.sendParticles((ParticleOptions)new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.2f), 1.0f), x, y + 0.5, z, count, 0.2, 0.15, 0.2, 0.01);
                    break;
                }
                case SPARK: {
                    serverLevel2.sendParticles((ParticleOptions)ParticleTypes.ELECTRIC_SPARK, x, y + 0.5, z, count, 0.2, 0.15, 0.2, 0.05);
                    break;
                }
            }
        }
    }
    
    private void sendTextAlerts(final AlertHit hit) {
        final Level level = this.getLevel();
        if (level instanceof ServerLevel serverLevel && hit != null) {
            final String panelInfo = "panel " + hit.panel.getBlockPos().toShortString();
            final String channelInfo = "channel " + ((hit.channel.getLabel() != null && !hit.channel.getLabel().isEmpty()) ? hit.channel.getLabel() : hit.channel.getId());
            final String baseMsg = this.alarmSettings.getAlertMessage().isEmpty() ? "Stats Alarm triggered" : this.alarmSettings.getAlertMessage();
            final String msg = "§c\u26a0 " + baseMsg + " §8(" + panelInfo + " · " + channelInfo + ")";
            final int range = AntennaRange.scale(this.alarmSettings.getTextRange(), this.antennaTier);
            final List<ServerPlayer> targets = this.getTargetPlayers(serverLevel, this.alarmSettings.getTextVisibility(), range);
            for (ServerPlayer player : targets) {
                player.displayClientMessage(Component.literal(msg), true);
            }
        }
    }
    
    private List<ServerPlayer> getTargetPlayers(ServerLevel level, AlarmSettings.AlertVisibility vis, int range) {
        final double x = this.worldPosition.getX() + 0.5;
        final double y = this.worldPosition.getY() + 0.5;
        final double z = this.worldPosition.getZ() + 0.5;
        return switch (vis) {
            case ALL_PLAYERS -> level.players().stream().map(p -> p).toList();
            case NEARBY_ONLY -> level.players().stream().filter(p -> p.distanceToSqr(x, y, z) <= range * (double)range).map(p -> p).toList();
            case OWNER_ONLY -> level.players().stream().filter(p -> this.ownerUUID != null && p.getUUID().equals(this.ownerUUID)).map(p -> p).toList();
            case NOBODY -> List.of();
        };
    }
    
    private SoundEvent getSoundEvent(AlarmSettings.AlarmSound sound) {
        return switch (sound) {
            case BELL -> SoundEvents.BELL_BLOCK;
            case NOTE_PLING -> (SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value();
            case ANVIL -> SoundEvents.ANVIL_LAND;
            case RAID_HORN -> (SoundEvent)SoundEvents.RAID_HORN.value();
            case WITHER_SPAWN -> SoundEvents.WITHER_SPAWN;
            case NONE -> (SoundEvent)SoundEvents.UI_BUTTON_CLICK.value();
        };
    }
    
    public boolean isActive() {
        return (boolean)this.getBlockState().getValue(StatsAlarmBlock.ACTIVE);
    }
    
    public int getConnectedPanelCount() {
        final Level level = this.getLevel();
        if (level == null) {
            return 0;
        }
        int count = 0;
        final int r = AntennaRange.cappedScanRadius(this.alarmSettings.getScanRadius(), this.antennaTier, 16);
        final java.util.Set<BlockPos> countedControllers = new java.util.HashSet<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(this.worldPosition.offset(-r, -r, -r), this.worldPosition.offset(r, r, r))) {
            if (pos.equals(this.worldPosition)) {
                continue;
            }
            final BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof StatsDisplayPanelBE panel) || !panel.isController() || !countedControllers.add(pos)) {
                continue;
            }
            ++count;
        }
        return count;
    }
    
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.feConsumer.save(tag);
        tag.put("AlarmSettings", (Tag)this.alarmSettings.save());
        tag.putString("AntennaTier", this.antennaTier.name());
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
    }
    
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.feConsumer.load(tag);
        if (tag.contains("AlarmSettings")) {
            this.alarmSettings = AlarmSettings.load(tag.getCompound("AlarmSettings"));
        }
        this.antennaTier = AntennaTier.byName(tag.getString("AntennaTier"));
        if (tag.hasUUID("Owner")) {
            this.ownerUUID = tag.getUUID("Owner");
        }
    }
    
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }
    
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return (Packet<ClientGamePacketListener>)ClientboundBlockEntityDataPacket.create((BlockEntity)this);
    }
    
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        final CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.handleUpdateTag(tag, registries);
        }
    }
    
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }
}
