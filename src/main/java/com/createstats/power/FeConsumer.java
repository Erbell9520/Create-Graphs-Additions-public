package com.createstats.power;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

// composition, the consumer roster straddles BlockEntity and SmartBlockEntity
public final class FeConsumer implements IEnergyStorage {
    public static final int LOW_POWER_PCT = 20;

    private static final int TRANSITION_QUIET_TICKS = 40;

    private static final int MAX_BEATS_PER_BURST = 2;

    private static final int MIN_SYNC_GAP_TICKS = 4;

    private static final int SYNC_HEARTBEAT_TICKS = 200;

    public interface Settings {
        boolean enabled();

        int capacity();

        int idleDrain();

        int activeDrain();

        int burstCost();

        default int rebootThresholdPct() {
            return FeProfiles.rebootThresholdPct();
        }
    }

    private final Settings settings;
    @Nullable
    private final Runnable onChanged;

    @Nullable
    private BlockEntity owner;

    @Nullable
    private Settings mirrorSettings;

    private int energy;
    private boolean powered = true;
    private boolean hasTicked;

    private int measuredDrain;

    private int inputAccumulator;
    private int measuredInput;

    private float appliedMultiplier = 1.0f;

    private int transition;
    private int ticksSinceTransition = TRANSITION_QUIET_TICKS;
    private int flipsThisBurst;

    private boolean burstStarved;

    private FePriority priority = FePriority.NORMAL;

    private FeLock lock = FeLock.NONE;

    private boolean loadShed;

    private int lastSyncSignature = Integer.MIN_VALUE;
    private int ticksSinceSync = SYNC_HEARTBEAT_TICKS;
    private int lastNotifiedSignature = Integer.MIN_VALUE;

    public FeConsumer(Settings settings) {
        this(settings, null);
    }

    public FeConsumer(Settings settings, @Nullable Runnable onChanged) {
        this.settings = settings;
        this.onChanged = onChanged;
    }

    public static FeConsumer forBlockEntity(BlockEntity owner, Settings settings) {
        final FeConsumer consumer = new FeConsumer(settings, null);
        consumer.owner = owner;
        return consumer;
    }

    private Settings active() {
        return mirrorSettings != null ? mirrorSettings : settings;
    }

    public boolean requiresPower() {
        return active().enabled();
    }

    public boolean isCreativePowered() {
        return !active().enabled();
    }

    public Settings settings() {
        return active();
    }

    public int measuredDrainFePerTick() {
        return measuredDrain;
    }

    public int measuredInputFePerTick() {
        return measuredInput;
    }

    public float appliedLoadMultiplier() {
        return appliedMultiplier;
    }

    public boolean isBurstStarved() {
        return burstStarved;
    }

    public FePriority priority() {
        return priority;
    }

    public void setPriority(FePriority priority) {
        if (priority == null || this.priority == priority) {
            return;
        }
        this.priority = priority;
        if (owner != null) {
            owner.setChanged();
        }
    }

    public FeLock lock() {
        return lock;
    }

    public void setLock(FeLock lock) {
        if (lock == null || this.lock == lock) {
            return;
        }
        this.lock = lock;
        if (owner != null) {
            owner.setChanged();
        }
    }

    public boolean isLoadShed() {
        return loadShed;
    }

    public boolean isSuppressed() {
        return lock == FeLock.FORCE_OFF || (loadShed && lock != FeLock.FORCE_ON);
    }

    public void setLoadShed(boolean shed) {
        this.loadShed = shed;
    }

    public int rebootThresholdPct() {
        return clampedRebootThreshold(active());
    }

    public boolean serviceTick(boolean active) {
        return serviceTick(active, 1.0f);
    }

    public boolean serviceTick(boolean active, float loadMultiplier) {
        measuredInput = inputAccumulator;
        inputAccumulator = 0;
        final float multiplier = Math.max(1.0f, loadMultiplier);
        appliedMultiplier = active ? multiplier : 1.0f;
        if (ticksSinceTransition < TRANSITION_QUIET_TICKS) {
            ticksSinceTransition++;
        }

        final Settings tuning = active();
        if (!tuning.enabled()) {
            powered = true;
            hasTicked = true;
            measuredDrain = 0;
            burstStarved = false;
            notifyIfChanged();
            syncIfDue();
            return true;
        }

        final int drain = active
                ? Math.max(0, Math.round(tuning.activeDrain() * multiplier))
                : Math.max(0, tuning.idleDrain());
        clampToCapacity();

        final boolean reserveOk = powered || fillPct() >= clampedRebootThreshold(tuning);

        final boolean ok;
        if (drain <= 0) {
            ok = reserveOk && (energy > 0 || powered);
            measuredDrain = 0;
        } else if (reserveOk && energy >= drain) {
            energy -= drain;
            measuredDrain = drain;
            ok = true;
        } else {
            if (energy < drain) {
                measuredDrain = energy;
                energy = 0;
            } else {
                measuredDrain = 0;
            }
            ok = false;
        }

        if (hasTicked && ok != powered) {
            if (ticksSinceTransition >= TRANSITION_QUIET_TICKS) {
                flipsThisBurst = 0;
            }
            if (flipsThisBurst < MAX_BEATS_PER_BURST) {
                transition = ok ? 1 : -1;
            }
            flipsThisBurst++;

            ticksSinceTransition = 0;
        }
        powered = ok;
        hasTicked = true;
        notifyIfChanged();
        syncIfDue();
        return ok;
    }

    public boolean spendBurst() {
        return spendBurst(active().burstCost());
    }

    public boolean spendBurst(int amount) {
        final Settings tuning = active();
        if (!tuning.enabled() || amount <= 0) {
            burstStarved = false;
            return true;
        }

        burstStarved = amount > tuning.capacity();
        clampToCapacity();
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        return true;
    }

    public int drawDown(int amount) {
        if (amount <= 0) {
            return 0;
        }
        if (!active().enabled()) {
            return amount;
        }
        clampToCapacity();
        final int taken = Math.min(energy, amount);
        if (taken > 0) {
            energy -= taken;
            notifyIfChanged();
        }
        return taken;
    }

    public void transferChargeTo(FeConsumer other) {
        if (other == this || energy <= 0) {
            return;
        }
        energy -= other.receiveEnergy(energy, false);
        notifyIfChanged();
    }

    public boolean isPowered() {
        return !active().enabled() || powered;
    }

    public float fillPct() {
        final int capacity = Math.max(1, active().capacity());
        return Math.max(0.0f, Math.min(100.0f, energy * 100.0f / capacity));
    }

    public float hudBufferPct() {
        return active().enabled() ? fillPct() : 100.0f;
    }

    public boolean shouldDisplay() {
        return true;
    }

    public boolean isBrownout() {
        return active().enabled() && fillPct() < LOW_POWER_PCT;
    }

    public boolean isRecharging() {
        final Settings tuning = active();
        return tuning.enabled() && !powered && fillPct() < clampedRebootThreshold(tuning);
    }

    public int pollTransition() {
        final int t = transition;
        transition = 0;
        return t;
    }

    public FePowerSnapshot snapshot() {
        final Settings tuning = active();
        return new FePowerSnapshot(tuning.enabled(), powered, burstStarved, energy, tuning.capacity(),
                tuning.idleDrain(), tuning.activeDrain(), tuning.burstCost(), clampedRebootThreshold(tuning),
                measuredDrain, measuredInput, appliedMultiplier, priority, loadShed, lock);
    }

    public void applyClientSnapshot(FePowerSnapshot snapshot) {
        mirrorSettings = snapshot.asSettings();
        energy = snapshot.energy();
        powered = snapshot.powered();
        burstStarved = snapshot.burstStarved();
        measuredDrain = snapshot.measuredDrain();
        measuredInput = snapshot.measuredInput();
        appliedMultiplier = snapshot.loadMultiplier();
        priority = snapshot.priority();
        loadShed = snapshot.shed();
        lock = snapshot.lock();
        hasTicked = true;
    }

    public void save(CompoundTag tag) {
        tag.putInt("FeEnergy", energy);
        tag.putBoolean("FePowered", powered);
        tag.putString("FePriority", priority.name());
        if (lock != FeLock.NONE) {
            tag.putString("FeLock", lock.name());
        }
    }

    public void load(CompoundTag tag) {
        if (mirrorSettings != null) {
            return;
        }
        if (tag.contains("FeEnergy")) {
            energy = Math.max(0, tag.getInt("FeEnergy"));
        }
        if (tag.contains("FePowered")) {
            powered = tag.getBoolean("FePowered");
        }
        if (tag.contains("FePriority")) {
            try {
                priority = FePriority.valueOf(tag.getString("FePriority"));
            } catch (IllegalArgumentException ignored) {
                priority = FePriority.NORMAL;
            }
        }
        if (tag.contains("FeLock")) {
            try {
                lock = FeLock.valueOf(tag.getString("FeLock"));
            } catch (IllegalArgumentException ignored) {
                lock = FeLock.NONE;
            }
        } else {
            lock = FeLock.NONE;
        }

        if (tag.contains("FeDrain")) {
            measuredDrain = Math.max(0, tag.getInt("FeDrain"));
        }
        if (tag.contains("FeInput")) {
            measuredInput = Math.max(0, tag.getInt("FeInput"));
        }
        if (tag.contains("FeMult")) {
            appliedMultiplier = Math.max(1.0f, tag.getFloat("FeMult"));
        }
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) {
            return 0;
        }
        clampToCapacity();
        final int space = Math.max(0, active().capacity() - energy);
        final int accepted = Math.min(space, maxReceive);
        if (!simulate && accepted > 0) {
            energy += accepted;
            inputAccumulator += accepted;
            notifyIfChanged();
        }
        return accepted;
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
        return Math.max(0, active().capacity());
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    private static int clampedRebootThreshold(Settings tuning) {
        return Math.max(0, Math.min(90, tuning.rebootThresholdPct()));
    }

    private void clampToCapacity() {
        final int capacity = Math.max(0, active().capacity());
        if (energy > capacity) {
            energy = capacity;
        }
    }

    private int currentSignature() {
        final Settings tuning = active();
        return FePowerSnapshot.signature(tuning.enabled(), powered, burstStarved, fillPct(),
                measuredDrain, measuredInput, appliedMultiplier, tuning.capacity(), tuning.activeDrain(),
                priority.ordinal(), loadShed, lock.ordinal());
    }

    private void notifyIfChanged() {
        if (onChanged == null) {
            return;
        }
        final int signature = currentSignature();
        if (signature != lastNotifiedSignature) {
            lastNotifiedSignature = signature;
            onChanged.run();
        }
    }

    private void syncIfDue() {
        if (owner == null) {
            return;
        }
        final Level level = owner.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (ticksSinceSync < SYNC_HEARTBEAT_TICKS) {
            ticksSinceSync++;
        }
        final int signature = currentSignature();
        final boolean changed = signature != lastSyncSignature;
        if (!changed && ticksSinceSync < SYNC_HEARTBEAT_TICKS) {
            return;
        }

        if (changed && transition == 0 && ticksSinceSync < MIN_SYNC_GAP_TICKS) {
            return;
        }
        lastSyncSignature = signature;
        ticksSinceSync = 0;
        owner.setChanged();
        FePowerSync.push(level, owner.getBlockPos(), snapshot());
    }
}
