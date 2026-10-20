package com.createstats.powertrack;

import com.createstats.power.FeGoggle;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SectionInsulatorBE extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final double[] LAMP_X = {4.0, 12.0};

    public enum Status {
        SEARCHING,

        NOT_POWER_TRACK,

        WORKING
    }

    public TrackTargetingBehaviour<InsulatorPoint> edgePoint;

    private Status status = Status.SEARCHING;
    private SectionView left = SectionView.NONE;
    private SectionView right = SectionView.NONE;
    private boolean oriented;
    private long reportedAt = Long.MIN_VALUE;

    private final com.createstats.powertrack.core.LampGlow[] glows = {
            new com.createstats.powertrack.core.LampGlow(), new com.createstats.powertrack.core.LampGlow()};

    public SectionInsulatorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        edgePoint = new TrackTargetingBehaviour<>(this, PowerTrack.INSULATOR);
        behaviours.add(edgePoint);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            boolean lit = false;
            for (int i = 0; i < 2; i++) {
                final int s = status == Status.WORKING ? lamp(i).state() : SectionView.DEAD;
                glows[i].tick(s == SectionView.LIVE || s == SectionView.COLLECTING ? s : 0);
                lit |= glows[i].colour() != 0 && glows[i].glow(1f) > com.createstats.powertrack.client.PowerTrackLights.LIT;
            }

            if (lit) {
                com.createstats.powertrack.client.PowerTrackLights.lamp(this, worldPosition);
            }
            return;
        }

        if (reportedAt != Long.MIN_VALUE && level.getGameTime() - reportedAt > 20 && status != Status.SEARCHING) {
            report(Status.SEARCHING, SectionView.NONE, SectionView.NONE, level.getGameTime());
        }
    }

    public void report(Status status, SectionView lamp0, SectionView lamp1, long now) {
        reportedAt = now;
        final boolean changed = status != this.status || !lamp0.equals(left) || !lamp1.equals(right);

        final boolean visible = status != this.status || lamp0.state() != left.state()
                || lamp1.state() != right.state() || lamp0.feeders() != left.feeders()
                || lamp1.feeders() != right.feeders() || lamp0.cars() != left.cars() || lamp1.cars() != right.cars();
        this.status = status;
        this.left = lamp0;
        this.right = lamp1;
        if (visible || (changed && now % 10 == 0)) {
            setChanged();
            sendData();
        }
    }

    public void orientOnce(Direction acrossTrack) {
        if (oriented || level == null || acrossTrack.getAxis().isVertical()) {
            return;
        }
        oriented = true;
        final BlockState state = getBlockState();
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)
                && state.getValue(HorizontalDirectionalBlock.FACING).getAxis() != acrossTrack.getAxis()) {
            level.setBlock(worldPosition, state.setValue(HorizontalDirectionalBlock.FACING, acrossTrack), 3);
        }
        setChanged();
    }

    public Direction facing() {
        final BlockState state = getBlockState();
        return state.hasProperty(HorizontalDirectionalBlock.FACING)
                ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    public Direction lampDirection(int i) {
        return i == 0 ? facing().getCounterClockWise() : facing().getClockWise();
    }

    public SectionView lamp(int i) {
        return i == 0 ? left : right;
    }

    public int lampColour(int i) {
        return glows[i].colour();
    }

    public float lampGlow(int i, float partialTicks) {
        return glows[i].glow(partialTicks);
    }

    public Status status() {
        return status;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(Component.translatable(getBlockState().getBlock().getDescriptionId())));
        switch (status) {
            case SEARCHING -> tooltip.add(FeGoggle.warn("    Not on a track yet"));
            case NOT_POWER_TRACK -> tooltip.add(FeGoggle.warn("    On ordinary track: nothing to insulate"));
            case WORKING -> {
                tooltip.add(FeGoggle.dim("    Ends a section: nothing crosses the gap"));
                for (int i = 0; i < 2; i++) {
                    final SectionView v = lamp(i);
                    final String side = lampDirection(i).getName();
                    tooltip.add(v.stateLine("    " + Character.toUpperCase(side.charAt(0)) + side.substring(1)
                            + " lamp: "));
                    if (isPlayerSneaking) {
                        tooltip.add(v.countsLine());
                    }
                }
            }
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putByte("Status", (byte) status.ordinal());
        tag.put("Lamp0", left.write());
        tag.put("Lamp1", right.write());
        tag.putBoolean("Oriented", oriented);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        final Status[] all = Status.values();
        status = all[Math.floorMod(tag.getByte("Status"), all.length)];
        left = SectionView.read(tag.getCompound("Lamp0"));
        right = SectionView.read(tag.getCompound("Lamp1"));
        oriented = tag.getBoolean("Oriented");
    }
}
