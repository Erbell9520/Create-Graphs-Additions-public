package com.createstats.blockentity;

import com.createstats.block.VacuumValueBox;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.vacuum.CollectorButton;
import com.createstats.vacuum.SuctionEngine;
import com.createstats.vacuum.SuctionReach;
import com.createstats.vacuum.VacuumCatch;
import com.createstats.vacuum.VacuumDraw;
import com.createstats.vacuum.VacuumFrame;
import com.createstats.vacuum.VacuumNet;
import com.createstats.vacuum.XpCondenser;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class VacuumCollectorBE extends SmartBlockEntity implements FeGridMember, FeManagedLoad,
        com.createstats.vacuum.VacuumSoundHost,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public enum State {
        OFF, IDLE, PULL, FULL, DISABLED, SWITCHED_OFF;

        static State of(int i) {
            final State[] all = values();
            return i >= 0 && i < all.length ? all[i] : OFF;
        }
    }

    public static final double MOUTH_X = 5.0;
    public static final double MOUTH_Y = 9.75;
    public static final double MOUTH_Z = -2.0;

    public static final double SCREEN_Z = 0.6;

    public static final double DUCT_FRONT = 0.0;
    public static final double DUCT_TOP = 12.0;

    private static final int AIRLOCK_PERIOD = 4;
    private static final int AIRLOCK_POCKET = 16;

    private int energy;
    private int ratedDraw;
    private int measuredDraw;
    private final FeManagedState managed = new FeManagedState();
    @Nullable
    private FeWireNetwork network;

    private final VacuumCatch catchInv = new VacuumCatch(9, this::onCatchChanged);
    private final XpCondenser condenser = new XpCondenser();
    private ScrollValueBehaviour radius;
    private FilteringBehaviour filter;

    private State state = State.OFF;

    private boolean switchedOn = true;

    private long lastPull = -1000;

    private long lastDischarge = -1000;
    private int airlockClock;
    private boolean syncDue;
    private int syncCooldown;

    private final int[] perSecond = new int[60];
    private int caughtThisSecond;

    public float fanAngle;
    public float fanSpeed;
    public float sprocketAngle;
    public float damperOpen;
    public float swirlAngle;
    private boolean soundPlaying;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, VacuumDraw.COLLECTOR_BUFFER - energy);
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
            return VacuumDraw.COLLECTOR_BUFFER;
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

    public VacuumCollectorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.VACUUM_COLLECTOR.get(),
                (be, side) -> be.catchInv.outside());
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        radius = new ScrollValueBehaviour(Component.translatable("block.create_stats.vacuum_collector.radius"),
                this, new VacuumValueBox(5.0, 12.15, 3.0, 0.35f, VacuumValueBox.Mount.TOP, DUCT_TOP));
        radius.between(VacuumDraw.COLLECTOR_MIN_RADIUS, VacuumDraw.COLLECTOR_MAX_RADIUS);
        radius.withFormatter(v -> v + (v == 1 ? " block" : " blocks"));
        radius.withCallback(v -> {
            setChanged();
            syncDue = true;
        });
        radius.setValue(4);
        behaviours.add(radius);

        filter = new FilteringBehaviour(this, new VacuumValueBox(MOUTH_X, MOUTH_Y, SCREEN_Z, 0.4f,
                VacuumValueBox.Mount.FRONT, DUCT_FRONT));
        behaviours.add(filter);
    }

    public boolean screened() {
        return filter != null && !filter.getFilter().isEmpty();
    }

    private String takes() {
        final ItemStack set = filter == null ? ItemStack.EMPTY : filter.getFilter();
        if (set.isEmpty()) {
            return "Takes anything: no filter in the mouth";
        }
        if (set.getItem() instanceof com.simibubi.create.content.logistics.filter.FilterItem) {
            return "Takes what its " + set.getHoverName().getString() + " allows";
        }
        return "Takes only " + set.getHoverName().getString();
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public Direction facing() {
        final BlockState s = getBlockState();
        return s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;
    }

    public State state() {
        return state;
    }

    public boolean switchedOn() {
        return switchedOn;
    }

    public void pressButton() {
        if (level == null || level.isClientSide()) {
            return;
        }
        switchedOn = !switchedOn;
        level.playSound(null, worldPosition,
                switchedOn ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                SoundSource.BLOCKS, 0.5f, switchedOn ? 0.9f : 0.7f);
        setChanged();
        syncDue = true;
        syncCooldown = 0;
    }

    public int radius() {
        return radius == null ? 4 : Mth.clamp(radius.getValue(), VacuumDraw.COLLECTOR_MIN_RADIUS,
                VacuumDraw.COLLECTOR_MAX_RADIUS);
    }

    public VacuumCatch catchInventory() {
        return catchInv;
    }

    public float xpCharge() {
        return condenser.charge();
    }

    public boolean discharging(long now) {
        return now - lastDischarge < 12;
    }

    public Vec3 mouth() {
        return VacuumFrame.horizontal(worldPosition, facing(), MOUTH_X, MOUTH_Y, MOUTH_Z);
    }

    public List<ItemStack> dropContents() {
        final List<ItemStack> out = catchInv.drain();
        final int nuggets = condenser.takeNuggets(Integer.MAX_VALUE);
        if (nuggets > 0) {
            out.add(new ItemStack(com.simibubi.create.AllItems.EXP_NUGGET.get(), nuggets));
        }
        return out;
    }

    private void onCatchChanged() {
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
        final long now = level.getGameTime();
        final boolean redstone = level.hasNeighborSignal(worldPosition);
        final int fan = VacuumDraw.collectorFan(radius());
        ratedDraw = VacuumDraw.collectorRated(switchedOn, redstone, managed.isForcedOff(), radius());

        final State before = state;
        int spent = 0;
        if (!switchedOn) {
            state = State.SWITCHED_OFF;
        } else if (redstone || managed.isSuppressed()) {
            state = State.DISABLED;
        } else if (!catchInv.hasEmptySlot() && !anyRoomInHeldStacks()) {
            state = State.FULL;
        } else if (energy < fan) {
            state = State.OFF;
        } else {
            energy -= fan;
            spent += fan;
            spent += pullAndCatch(now);
            state = now - lastPull < 20 ? State.PULL : State.IDLE;
        }
        measuredDraw = spent;
        condense();
        airlock(now);
        rollStats(now);
        if (state != before) {
            syncDue = true;
        }
        if (syncDue && --syncCooldown <= 0) {
            syncDue = false;
            syncCooldown = 4;
            sendData();
        }
        setChanged();
    }

    private boolean anyRoomInHeldStacks() {
        for (int i = 0; i < catchInv.getSlots(); i++) {
            final ItemStack s = catchInv.getStackInSlot(i);
            if (!s.isEmpty() && s.getCount() < Math.min(s.getMaxStackSize(), catchInv.getSlotLimit(i))) {
                return true;
            }
        }
        return false;
    }

    private int pullAndCatch(long now) {
        final Direction facing = facing();
        final Vec3 mouth = mouth();
        final Vec3 axis = Vec3.atLowerCornerOf(facing.getNormal());
        final SuctionReach.Box b = SuctionReach.collector(worldPosition.getX(), worldPosition.getY(),
                worldPosition.getZ(), facing.getStepX(), facing.getStepZ(), radius());
        final AABB box = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        int spent = 0;
        int caught = 0;
        for (ItemEntity e : SuctionEngine.items(level, mouth, box,
                s -> filter.test(s) && catchInv.room(s) > 0, SuctionEngine.SCAN_LIMIT)) {
            lastPull = now;
            final double d = SuctionEngine.centre(e).distanceTo(mouth);
            if (d > SuctionEngine.CATCH_DISTANCE) {
                SuctionEngine.pull(e, mouth, axis, 1.0, e.getId());
                continue;
            }
            if (caught >= VacuumDraw.COLLECTOR_CAPTURES_PER_TICK
                    || !VacuumDraw.canAfford(energy, 0, VacuumDraw.COLLECTOR_PER_ITEM)) {
                continue;
            }
            final ItemStack held = e.getItem();
            final ItemStack rest = catchInv.accept(held);
            if (rest.getCount() == held.getCount()) {
                continue;
            }
            energy -= VacuumDraw.COLLECTOR_PER_ITEM;
            spent += VacuumDraw.COLLECTOR_PER_ITEM;
            caught++;
            caughtThisSecond++;
            VacuumNet.gulp(level, SuctionEngine.centre(e), mouth, held, e.getId(), 0.35f);
            if (rest.isEmpty()) {
                e.discard();
            } else {
                e.setItem(rest);
            }
        }
        for (ExperienceOrb orb : SuctionEngine.orbs(level, mouth, box, 8)) {
            final int points = SuctionEngine.orbPoints(orb);
            if (condenser.room() < points) {
                continue;
            }
            lastPull = now;
            if (orb.position().distanceTo(mouth) > SuctionEngine.CATCH_DISTANCE) {
                SuctionEngine.pull(orb, mouth, axis, 1.0, orb.getId());
                continue;
            }
            condenser.add(points);
            orb.discard();
            syncDue = true;
        }
        return spent;
    }

    private void condense() {
        if (condenser.nuggetsReady() <= 0) {
            return;
        }
        final ItemStack nugget = new ItemStack(com.simibubi.create.AllItems.EXP_NUGGET.get(), condenser.nuggetsReady());
        final int fits = catchInv.room(nugget);
        if (fits > 0) {
            final int made = condenser.takeNuggets(fits);
            catchInv.accept(new ItemStack(com.simibubi.create.AllItems.EXP_NUGGET.get(), made));
        }
    }

    private void airlock(long now) {
        if (++airlockClock < AIRLOCK_PERIOD) {
            return;
        }
        airlockClock = 0;
        final IItemHandler below = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.below(), Direction.UP);
        if (below == null) {
            return;
        }
        for (int i = 0; i < catchInv.getSlots(); i++) {
            final ItemStack peek = catchInv.extractItem(i, AIRLOCK_POCKET, true);
            if (peek.isEmpty()) {
                continue;
            }
            final ItemStack rest = ItemHandlerHelper.insertItemStacked(below, peek, true);
            final int moved = peek.getCount() - rest.getCount();
            if (moved <= 0) {
                continue;
            }
            final ItemStack out = catchInv.extractItem(i, moved, false);
            ItemHandlerHelper.insertItemStacked(below, out, false);
            if (now - lastDischarge > 8) {
                syncDue = true;
            }
            lastDischarge = now;
            return;
        }
    }

    private void rollStats(long now) {
        if (now % 20 == 0) {
            System.arraycopy(perSecond, 0, perSecond, 1, perSecond.length - 1);
            perSecond[0] = caughtThisSecond;
            caughtThisSecond = 0;
        }
    }

    private int caughtLastMinute() {
        int n = 0;
        for (int v : perSecond) {
            n += v;
        }
        return n;
    }

    private void clientTick() {
        final long now = level.getGameTime();
        final boolean running = state == State.IDLE || state == State.PULL;
        final float targetSpeed = running ? (state == State.PULL ? 48.0f : 30.0f) : 0.0f;
        fanSpeed += (targetSpeed - fanSpeed) * 0.08f;
        fanAngle = (fanAngle + fanSpeed) % 360.0f;
        swirlAngle = (swirlAngle + (state == State.PULL ? 22.0f : running ? 4.0f : 0.0f)) % 360.0f;
        if (discharging(now)) {
            sprocketAngle = (sprocketAngle + 9.0f) % 360.0f;
        }
        final boolean shut = state == State.DISABLED || state == State.OFF || state == State.SWITCHED_OFF;
        final float open = shut ? 0.0f : 12.0f + (radius() - 1) * (70.0f / 7.0f);
        damperOpen += (open - damperOpen) * 0.2f;
        if (running) {
            com.createstats.client.VacuumAirStream.collector(this);
            com.createstats.client.VacuumSoundInstance.startIfNeeded(this, worldPosition, 0.55f + radius() * 0.03f);
        }
    }

    public boolean isSoundPlaying() {
        return soundPlaying;
    }

    public void setSoundPlaying(boolean playing) {
        soundPlaying = playing;
    }

    public boolean fanRunning() {
        return state == State.IDLE || state == State.PULL;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(Component.translatable("block.create_stats.vacuum_collector")));
        tooltip.add(switch (state) {
            case PULL -> FeGoggle.ok("Pulling");
            case IDLE -> FeGoggle.ok(screened() ? "Running, nothing the filter takes in reach" : "Running, nothing in reach");
            case FULL -> FeGoggle.warn("Catch full: empty it through the airlock or a funnel");
            case DISABLED -> FeGoggle.warn(managed.isSuppressed() ? "Off at the panel" : "Damper shut by redstone");
            case SWITCHED_OFF -> FeGoggle.warn("Switched off: press the button under the mouth to start it");
            default -> FeGoggle.bad("No power");
        });
        tooltip.add(FeGoggle.dim(takes()));
        tooltip.add(FeGoggle.dim("Reach " + radius() + (radius() == 1 ? " block" : " blocks") + " out of the mouth"));
        tooltip.add(FeGoggle.amount("Catch", catchInv.stacksHeld(), catchInv.getSlots()));
        if (condenser.points() > 0) {
            tooltip.add(FeGoggle.dim("Condenser " + condenser.points() + " XP"));
        }
        tooltip.add(FeGoggle.dim(caughtLastMinute() + " items in the last minute"));
        tooltip.add(FeGoggle.draw(measuredDraw));
        tooltip.add(FeGoggle.stored(energy, VacuumDraw.COLLECTOR_BUFFER));
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
        return 256;
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

    // same Critical tier trap as the cleaner
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
        tag.put("Catch", catchInv.serializeNBT(registries));
        tag.putInt("Xp", condenser.points());
        tag.putBoolean("On", switchedOn);
        if (clientPacket) {
            tag.putInt("State", state.ordinal());
            tag.putInt("Draw", measuredDraw);
            tag.putLong("Discharge", lastDischarge);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy = Mth.clamp(tag.getInt("Energy"), 0, VacuumDraw.COLLECTOR_BUFFER);
        managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        if (tag.contains("Catch")) {
            catchInv.deserializeNBT(registries, tag.getCompound("Catch"));
        }
        condenser.load(tag.getInt("Xp"));
        switchedOn = !tag.contains("On") || tag.getBoolean("On");
        if (clientPacket) {
            state = State.of(tag.getInt("State"));
            measuredDraw = tag.getInt("Draw");
            lastDischarge = tag.getLong("Discharge");
        }
    }
}
