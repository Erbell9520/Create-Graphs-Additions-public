package com.createstats.blockentity;

import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.registry.ModSounds;
import com.createstats.vacuum.ItemKey;
import com.createstats.vacuum.SnifferSlots;
import com.createstats.vacuum.SuctionEngine;
import com.createstats.vacuum.SuctionReach;
import com.createstats.vacuum.TypeSlots;
import com.createstats.vacuum.VacuumDraw;
import com.createstats.vacuum.VacuumFrame;
import com.createstats.vacuum.VacuumNet;
import com.createstats.vacuum.VacuumSoundHost;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// 3 types, 2 in flight, room reserved when picked. those were the rules, word for word
public class VacuumSnifferBE extends SmartBlockEntity implements FeGridMember, FeManagedLoad, VacuumSoundHost,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public enum State {
        OFF, IDLE, PULL, FULL, DISABLED;

        static State of(int i) {
            final State[] all = values();
            return i >= 0 && i < all.length ? all[i] : OFF;
        }
    }

    public static final double PIVOT_X = 8.0;
    public static final double PIVOT_Y = 10.0;
    public static final double PIVOT_Z = 8.5;
    public static final double MOUTH_REACH = 6.4 / 16.0;

    private static final double IDLE_SWEEP = 1.5;

    private int energy;
    private int ratedDraw;
    private int measuredDraw;
    private final FeManagedState managed = new FeManagedState();
    @Nullable
    private FeWireNetwork network;

    private final SnifferSlots slots = new SnifferSlots(this::onSlotsChanged);

    private record Target(UUID id, ItemKey key, int count) {
    }

    private final List<Target> targets = new ArrayList<>(VacuumDraw.SNIFFER_IN_FLIGHT);
    private State state = State.OFF;

    @Nullable
    private Vec3 aim;
    private boolean syncDue;
    private int syncCooldown;
    private int caughtTotal;

    public float yaw;
    public float pitch;
    public float yawVel;
    public float pitchVel;
    public float drum;
    private boolean soundPlaying;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, VacuumDraw.SNIFFER_BUFFER - energy);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
                setChanged();
            }
            return room;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return VacuumDraw.SNIFFER_BUFFER;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public VacuumSnifferBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.VACUUM_SNIFFER.get(),
                (be, side) -> be.slots);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public Direction facing() {
        final BlockState s = getBlockState();
        return s.hasProperty(DirectionalBlock.FACING) ? s.getValue(DirectionalBlock.FACING) : Direction.UP;
    }

    public State state() {
        return state;
    }

    public SnifferSlots slots() {
        return slots;
    }

    @Nullable
    public Vec3 aim() {
        return aim;
    }

    public Vec3 pivot() {
        return VacuumFrame.mounted(worldPosition, facing(), PIVOT_X, PIVOT_Y, PIVOT_Z);
    }

    public List<ItemStack> dropContents() {
        releaseAll();
        return slots.drain();
    }

    private void onSlotsChanged() {
        setChanged();
        syncDue = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            clientTick();
            return;
        }
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }
        final boolean redstone = level.hasNeighborSignal(worldPosition);
        ratedDraw = redstone || managed.isForcedOff() ? 0 : VacuumDraw.SNIFFER_IDLE;
        final State before = state;
        final Vec3 aimBefore = aim;
        int spent = 0;
        if (redstone || managed.isSuppressed()) {
            releaseAll();
            state = State.DISABLED;
        } else if (slots.full() && targets.isEmpty()) {
            state = State.FULL;
        } else if (energy < VacuumDraw.SNIFFER_IDLE) {
            releaseAll();
            state = State.OFF;
        } else {
            energy -= VacuumDraw.SNIFFER_IDLE;
            spent += VacuumDraw.SNIFFER_IDLE;
            spent += work();
            state = targets.isEmpty() ? (slots.full() ? State.FULL : State.IDLE) : State.PULL;
        }
        if (state != State.PULL) {
            aim = null;
        }
        measuredDraw = spent;
        if (state != before || moved(aimBefore, aim)) {
            syncDue = true;
        }
        if (syncDue && --syncCooldown <= 0) {
            syncDue = false;
            syncCooldown = 2;
            sendData();
        }
        setChanged();
    }

    private static boolean moved(@Nullable Vec3 a, @Nullable Vec3 b) {
        if (a == null || b == null) {
            return a != b;
        }
        return a.distanceToSqr(b) > 0.04;
    }

    private int work() {
        final ServerLevel server = (ServerLevel) level;
        final Direction facing = facing();
        final Vec3 pivot = pivot();

        final SuctionReach.Box b = SuctionReach.sniffer(worldPosition.getX(), worldPosition.getY(),
                worldPosition.getZ());
        final AABB box = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        final Vec3 outward = Vec3.atLowerCornerOf(facing.getNormal());
        int spent = 0;

        for (int i = targets.size() - 1; i >= 0; i--) {
            final Target t = targets.get(i);
            final Entity e = server.getEntity(t.id);
            if (!(e instanceof ItemEntity item) || !item.isAlive() || !box.contains(SuctionEngine.centre(item))
                    || !SuctionEngine.clearAirFrom(level, worldPosition, pivot, SuctionEngine.centre(item))) {
                slots.release(t.key, t.count);
                targets.remove(i);
            }
        }

        if (targets.size() < VacuumDraw.SNIFFER_IN_FLIGHT) {
            for (ItemEntity e : SuctionEngine.itemsFrom(level, worldPosition, pivot, box, slots::accepts,
                    SuctionEngine.SCAN_LIMIT)) {
                if (targets.size() >= VacuumDraw.SNIFFER_IN_FLIGHT) {
                    break;
                }
                if (targeted(e.getUUID())) {
                    continue;
                }
                final ItemKey key = ItemKey.of(e.getItem());
                final int count = e.getItem().getCount();
                if (slots.reserve(key, count)) {
                    targets.add(new Target(e.getUUID(), key, count));
                    if (targets.size() == 1) {
                        level.playSound(null, worldPosition, ModSounds.VACUUM_SERVO.get(), SoundSource.BLOCKS, 0.35f,
                                0.9f + level.random.nextFloat() * 0.2f);
                    }
                }
            }
        }
        if (targets.isEmpty()) {
            return 0;
        }

        final Entity first = server.getEntity(targets.get(0).id);
        aim = first == null ? null : SuctionEngine.centre(first);
        final Vec3 mouth = aim == null ? pivot.add(outward.scale(MOUTH_REACH))
                : pivot.add(aim.subtract(pivot).normalize().scale(MOUTH_REACH));
        final Vec3 axis = aim == null ? outward : aim.subtract(pivot).normalize();
        for (int i = targets.size() - 1; i >= 0; i--) {
            final Target t = targets.get(i);
            if (!(server.getEntity(t.id) instanceof ItemEntity item)) {
                continue;
            }
            final double d = SuctionEngine.centre(item).distanceTo(mouth);
            if (d > SuctionEngine.CATCH_DISTANCE) {
                SuctionEngine.pull(item, mouth, axis, 1.0, item.getId());
                continue;
            }
            if (!VacuumDraw.canAfford(energy, 0, VacuumDraw.SNIFFER_PER_ITEM)) {
                continue;
            }
            final ItemStack held = item.getItem();
            final int n = Math.min(t.count, held.getCount());
            slots.commit(t.key, n);
            if (t.count > n) {
                slots.release(t.key, t.count - n);
            }
            energy -= VacuumDraw.SNIFFER_PER_ITEM;
            spent += VacuumDraw.SNIFFER_PER_ITEM;
            caughtTotal++;
            VacuumNet.gulp(level, SuctionEngine.centre(item), mouth, held, item.getId(), 0.3f);
            if (held.getCount() <= n) {
                item.discard();
            } else {
                item.setItem(held.copyWithCount(held.getCount() - n));
            }
            targets.remove(i);
        }
        return spent;
    }

    private boolean targeted(UUID id) {
        for (Target t : targets) {
            if (t.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void releaseAll() {
        for (Target t : targets) {
            slots.release(t.key, t.count);
        }
        targets.clear();
    }

    private void clientTick() {
        final long now = level.getGameTime();
        float goalYaw;
        float goalPitch;
        switch (state) {
            case PULL -> {
                if (aim != null) {
                    final Vec3 local = VacuumFrame.toMounted(facing(), aim.subtract(pivot()));
                    goalYaw = (float) Math.toDegrees(Math.atan2(-local.x, -local.z));
                    goalPitch = (float) Math.toDegrees(Math.atan2(local.y, Math.hypot(local.x, local.z)));
                } else {
                    goalYaw = yaw;
                    goalPitch = pitch;
                }
            }
            case IDLE -> {
                goalYaw = Mth.wrapDegrees((float) (now * IDLE_SWEEP + (worldPosition.hashCode() & 0xFF)));
                goalPitch = -8.0f + (float) Math.sin(now * 0.021) * 6.0f;
            }
            case FULL -> {
                goalYaw = 0.0f;
                goalPitch = -58.0f;
            }
            case DISABLED -> {
                goalYaw = 0.0f;
                goalPitch = 72.0f;
            }
            default -> {
                goalYaw = 0.0f;
                goalPitch = -48.0f;
            }
        }
        final float dy = Mth.wrapDegrees(goalYaw - yaw);
        final float stiff = state == State.PULL ? 0.30f : 0.08f;
        yawVel = (yawVel + dy * stiff) * 0.62f;
        pitchVel = (pitchVel + (goalPitch - pitch) * stiff) * 0.62f;

        yaw = Mth.wrapDegrees(yaw + yawVel);
        pitch = Mth.clamp(pitch + pitchVel, -80.0f, 85.0f);
        if (state == State.PULL || state == State.IDLE) {
            drum += state == State.PULL ? 18.0f : 3.0f;
        }
        if (state == State.PULL && aim != null) {
            final Vec3 pivot = pivot();
            final Vec3 mouth = pivot.add(aim.subtract(pivot).normalize().scale(MOUTH_REACH));
            com.createstats.client.VacuumAirStream.cone(level, mouth, aim.subtract(mouth), Math.min(4.0, aim.distanceTo(mouth)),
                    0.5, 2);
        }
        if (state == State.PULL) {
            com.createstats.client.VacuumSoundInstance.startIfNeeded(this, worldPosition, 1.3f);
        }
    }

    @Override
    public boolean fanRunning() {
        return state == State.PULL;
    }

    @Override
    public boolean isSoundPlaying() {
        return soundPlaying;
    }

    @Override
    public void setSoundPlaying(boolean playing) {
        soundPlaying = playing;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(Component.translatable("block.create_stats.vacuum_sniffer")));
        tooltip.add(switch (state) {
            case PULL -> FeGoggle.ok("Inhaling");
            case IDLE -> FeGoggle.ok("Scanning");
            case FULL -> FeGoggle.warn("Full: all three kinds are topped up");
            case DISABLED -> FeGoggle.warn(managed.isSuppressed() ? "Off at the panel" : "Parked by redstone");
            default -> FeGoggle.bad("No power");
        });
        final TypeSlots<ItemKey> rules = slots.rules();
        for (int i = 0; i < TypeSlots.SLOTS; i++) {
            final ItemStack s = slots.getStackInSlot(i);
            if (s.isEmpty()) {
                tooltip.add(FeGoggle.dim("Slot " + (i + 1) + ": free, takes anything"));
            } else {
                tooltip.add(FeGoggle.dim(s.getHoverName().getString() + " " + s.getCount() + " / " + s.getMaxStackSize()));
            }
        }
        tooltip.add(FeGoggle.draw(measuredDraw));
        tooltip.add(FeGoggle.stored(energy, VacuumDraw.SNIFFER_BUFFER));
        return true;
    }

    @Override
    public int ratedDrawFePerTick() {
        return ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
    }

    @Override
    public FePriority managedPriority() {
        return managed.priority();
    }

    @Override
    public void setManagedPriority(FePriority tier) {
        managed.setPriority(tier);
        setChanged();
    }

    @Override
    public FeLock managedLock() {
        return managed.lock();
    }

    @Override
    public void setManagedLock(FeLock lock) {
        managed.setLock(lock);
        setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        managed.setShed(shed);
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    @Override
    public long nodeKey() {
        return worldPosition.asLong();
    }

    @Override
    public int storedEnergy() {
        return 0;
    }

    @Override
    public void setStoredEnergy(int amount) {
    }

    @Override
    public int bufferCapacity() {
        return 0;
    }

    @Override
    public int transferRate() {
        return 128;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return energyView;
    }

    @Override
    public boolean isGone(net.minecraft.world.level.Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
    }

    @Override
    public void adoptNetwork(FeWireNetwork network) {
        this.network = network;
    }

    @Override
    @Nullable
    public FeWireNetwork network() {
        return network;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
    }

    @Override
    public void remove() {
        invalidateNetwork();
        super.remove();
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        tag.putInt("Tier", FePriority.DEFAULT.ordinal());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", energy);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        tag.put("Slots", slots.save(registries));
        if (clientPacket) {
            tag.putInt("State", state.ordinal());
            tag.putInt("Draw", measuredDraw);
            if (aim != null) {
                tag.putDouble("AimX", aim.x);
                tag.putDouble("AimY", aim.y);
                tag.putDouble("AimZ", aim.z);
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy = Mth.clamp(tag.getInt("Energy"), 0, VacuumDraw.SNIFFER_BUFFER);
        managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        if (tag.contains("Slots", Tag.TAG_LIST)) {
            slots.load(tag.getList("Slots", Tag.TAG_COMPOUND), registries);
        }
        if (clientPacket) {
            state = State.of(tag.getInt("State"));
            measuredDraw = tag.getInt("Draw");
            aim = tag.contains("AimX") ? new Vec3(tag.getDouble("AimX"), tag.getDouble("AimY"), tag.getDouble("AimZ")) : null;
        }
    }
}
