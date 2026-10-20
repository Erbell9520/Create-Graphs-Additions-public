package com.createstats.vehiclelight;

import com.createstats.dynlight.ClientLightOwners;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

// a lease, a dead console just stops renewing
public class VehicleLightBE extends BlockEntity {
    private static final int CRAFT_CHECK_TICKS = 40;

    private int commanded;
    private long commandedUntil = Long.MIN_VALUE;
    private boolean onCraft;
    private int craftCheck;

    private HailSequence.Hail hail;

    private float shown = -1.0f;
    private long shownNanos;

    private final HeadlightState headlight = new HeadlightState();

    public VehicleLightBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void drive(int strength, long gameTime) {
        commanded = Math.max(0, Math.min(15, strength));
        commandedUntil = gameTime + VehicleLightKind.LEASE_TICKS;
    }

    public void release() {
        commanded = 0;
        commandedUntil = Long.MIN_VALUE;
    }

    public int input() {
        final Level level = getLevel();
        if (level == null) {
            return 0;
        }
        final int vcc = level.getGameTime() <= commandedUntil ? commanded : 0;
        return Math.max(level.getBestNeighborSignal(worldPosition), vcc);
    }

    public void hail(long start, HailSequence.Role role, HailSequence.Place place) {
        final Level level = getLevel();
        if (level == null || level.isClientSide() || role == null) {
            return;
        }
        hail = new HailSequence.Hail(start, role, place == null ? HailSequence.Place.MIDDLE : place);
        final BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    public HailSequence.Hail hailing() {
        return hail;
    }

    void serverTick() {
        final Level level = getLevel();
        if (level == null || !(getBlockState().getBlock() instanceof VehicleLightBlock block)) {
            return;
        }
        if (--craftCheck <= 0) {
            onCraft = SableBridge.subLevelAccessOf(this) != null;
            craftCheck = CRAFT_CHECK_TICKS;
        }
        final BlockState state = getBlockState();
        final VehicleLightGlow glow = block.kind().glow(input(), level.getGameTime(), onCraft);
        if (state.getValue(VehicleLightBlock.GLOW) != glow) {
            level.setBlock(worldPosition, state.setValue(VehicleLightBlock.GLOW, glow), Block.UPDATE_CLIENTS);
        }
    }

    public float shownLevel() {
        return shown;
    }

    public long shownNanos() {
        return shownNanos;
    }

    public void show(float level, long nanos) {
        shown = level;
        shownNanos = nanos;
    }

    public HeadlightState headlight() {
        return headlight;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        if (hail != null) {
            tag.putLong("HailStart", hail.start());
            tag.putByte("HailRole", (byte) hail.role().ordinal());
            tag.putFloat("HailRadial", (float) hail.place().radial());
            tag.putFloat("HailLateral", (float) hail.place().lateral());
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains("HailStart")) {
            final HailSequence.Role role = HailSequence.Role.byOrdinal(tag.getByte("HailRole"));
            hail = role == null ? null : new HailSequence.Hail(tag.getLong("HailStart"), role,
                    new HailSequence.Place(tag.getFloat("HailRadial"), tag.getFloat("HailLateral")));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) {
            ClientLightOwners.loaded(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && level.isClientSide()) {
            ClientLightOwners.unloaded(this);
        }
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide()) {
            ClientLightOwners.unloaded(this);
        }
        super.setRemoved();
    }
}
