package com.createstats.vccmk4;

import com.createstats.vccmk4.core.BtaG1Motion;
import com.createstats.vccmk4.core.BtaG1State;
import com.createstats.vccmk4.core.DriveModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class BtaG1BE extends Vcc4BE {
    static final String ANIM = "BtaAnim";

    static final int IDLE_SPEED_INTERVAL = 10;

    private BtaG1State anim = BtaG1State.IDLE;

    @Nullable private BtaG1State sent;

    private boolean fullDirty = true;

    private boolean animOnly;
    private double lastSpeed;
    private int speedCooldown;

    private final BtaG1Motion.Pose pose = new BtaG1Motion.Pose();

    public BtaG1BE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void tickServer() {
        serverTick();
        if (!(level instanceof ServerLevel server)) return;
        BtaG1State now = capture();
        if (now.equals(sent)) return;
        sent = now;
        anim = now;
        animOnly = true;
        server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private BtaG1State capture() {
        boolean route = route().drives();
        boolean driving = seatedDriver() || route;
        DriveModel.Output out = lastOutput();
        double speed = speed(driving);
        boolean reversing = driving && (out.throttle() < -1.0e-6 || speed < -DriveModel.STOPPED_SPEED);
        boolean braking = driving && out.braking();
        return BtaG1State.of(driving, route, braking, driving && out.holding(), parked(), driving && out.boosting(),
                        reversing, route ? route().indicate() : 0,
                        driving ? out.throttle() : 0.0, driving ? out.steer() : 0.0, braking ? out.brake() : 0.0, speed)
                .with(BtaG1State.SEATED, seatedDriver())
                .with(BtaG1State.GREETING, setup().greeting);
    }

    private double speed(boolean driving) {
        boolean moving = Math.abs(lastSpeed) > BtaG1State.REST_BPS;
        if (driving || moving || --speedCooldown <= 0) {
            lastSpeed = measuredSpeed();
            speedCooldown = IDLE_SPEED_INTERVAL;
        }
        return lastSpeed;
    }

    @Override
    public void setChanged() {
        fullDirty = true;
        super.setChanged();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        if (animOnly && !fullDirty) {
            animOnly = false;
            return ClientboundBlockEntityDataPacket.create(this, (be, registries) -> animationTag());
        }
        animOnly = false;
        fullDirty = false;
        return super.getUpdatePacket();
    }

    private CompoundTag animationTag() {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray(ANIM, anim.encode());
        return tag;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putByteArray(ANIM, anim.encode());
        return tag;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag.size() == 1 && tag.contains(ANIM, Tag.TAG_BYTE_ARRAY)) {
            anim = BtaG1State.decode(tag.getByteArray(ANIM));
            return;
        }
        super.onDataPacket(net, pkt, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ANIM, Tag.TAG_BYTE_ARRAY)) anim = BtaG1State.decode(tag.getByteArray(ANIM));
    }

    public void clientTick() {
        if (level != null) pose.tick(anim, level.getGameTime());
    }

    public BtaG1Motion.Frame frame(float partial) {
        return pose.frame(partial);
    }
}
