package com.createstats.power;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

/** [1] */
public final class FeConsumer implements IEnergyStorage {

    /** Buffer percentage under which HUDs and visuals treat the consumer as in brownout. */
    public static final int LOW_POWER_PCT = 20;

    /** [2] */
    private static final int TRANSITION_QUIET_TICKS = 40;

    /** [3] */
    private static final int MAX_BEATS_PER_BURST = 2;

    /** Minimum ticks between routine client syncs. A power flip bypasses this and sends at once. */
    private static final int MIN_SYNC_GAP_TICKS = 4;

    /** Resync interval when nothing has changed at all, purely as drift insurance. */
    private static final int SYNC_HEARTBEAT_TICKS = 200;

    /** Tuning source. Production code uses {@link FeProfiles#settings(String)}; tests supply their own. */
    public interface Settings {
        boolean enabled();

        int capacity();

        int idleDrain();

        int activeDrain();

        int burstCost();

        /**
         * Buffer percent a cut-off consumer must reach before it is allowed to run again. Global
         * rather than per-block, so the default reads it from config; overridable for tests.
         */
        default int rebootThresholdPct() {
            return FeProfiles.rebootThresholdPct();
        }
    }

    private final Settings settings;
    @Nullable
    private final Runnable onChanged;
    /** Set when built through {@link #forBlockEntity}: drives dirty marking and client sync. */
    @Nullable
    private BlockEntity owner;
    /** Client only. When present, every reported value comes from the server instead of config. */
    @Nullable
    private Settings mirrorSettings;

    private int energy;
    private boolean powered = true;
    private boolean hasTicked;
    /** FE actually spent by the last serviceTick, for the live GUI readout. */
    private int measuredDrain;
    /** FE taken in since the last serviceTick, for the live GUI readout. */
    private int inputAccumulator;
    private int measuredInput;
    /** Load multiplier applied by the last serviceTick (1.0 unless a source curve scaled it). */
    private float appliedMultiplier = 1.0f;
    /** +1 = power gained since last poll, -1 = power lost, 0 = no transition. */
    private int transition;
    private int ticksSinceTransition = TRANSITION_QUIET_TICKS;
    private int flipsThisBurst;
    /** True when the configured burst cost can never fit in the buffer, so bursts never run. */
    private boolean burstStarved;

    /**
     * Player-assigned load priority tier, read by a Power Distribution Unit to decide serve order
     * under scarcity. Persisted per block; defaults to {@link FePriority#NORMAL}.
     */
    private FePriority priority = FePriority.NORMAL;
    /**
     * Player override of the PDU's automatic decision for this device. Persisted per block, because
     * unlike {@link #loadShed} it is a standing instruction rather than a per-tick outcome.
     */
    private FeLock lock = FeLock.NONE;
    /**
     * Set by the PDU branch solve each tick: true when this consumer was deliberately not fed so a
     * higher tier could keep running. Distinct from unpowered, and never persisted.
     */
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

    /** [4] */
    public static FeConsumer forBlockEntity(BlockEntity owner, Settings settings) {
        final FeConsumer consumer = new FeConsumer(settings, null);
        consumer.owner = owner;
        return consumer;
    }

    // owner-facing API

    /** The tuning actually in force: the server's snapshot on a client, the config on a server. */
    private Settings active() {
        return mirrorSettings != null ? mirrorSettings : settings;
    }

    public boolean feModeEnabled() {
        return active().enabled();
    }

    /** The live tuning behind this consumer (used by GUI readouts to show the configured draw). */
    public Settings settings() {
        return active();
    }

    /** FE actually spent on the last serviced tick. Zero while FE mode is off. */
    public int measuredDrainFePerTick() {
        return measuredDrain;
    }

    /** FE received during the last tick, so the GUI can show incoming supply. */
    public int measuredInputFePerTick() {
        return measuredInput;
    }

    /** Load multiplier applied by the last serviceTick. 1.0 when idle or unscaled. */
    public float appliedLoadMultiplier() {
        return appliedMultiplier;
    }

    /** [5] */
    public boolean isBurstStarved() {
        return burstStarved;
    }

    /** Player-assigned load priority tier. Read by a PDU branch solve; defaults to NORMAL. */
    public FePriority priority() {
        return priority;
    }

    /** Sets the load priority tier and marks the owner for save. Server-side player state. */
    public void setPriority(FePriority priority) {
        if (priority == null || this.priority == priority) {
            return;
        }
        this.priority = priority;
        if (owner != null) {
            owner.setChanged();
        }
    }

    /** Player override of the PDU's automatic decision. Never shed, never run, or no override. */
    public FeLock lock() {
        return lock;
    }

    /** Sets the player override and marks the owner for save. Server-side player state. */
    public void setLock(FeLock lock) {
        if (lock == null || this.lock == lock) {
            return;
        }
        this.lock = lock;
        if (owner != null) {
            owner.setChanged();
        }
    }

    /** True when a PDU shed this consumer this tick to keep a higher tier alive. Not persisted. */
    public boolean isLoadShed() {
        return loadShed;
    }

    /** Set by the PDU branch solve; not persisted and never set on a client mirror. */
    public void setLoadShed(boolean shed) {
        this.loadShed = shed;
    }

    /** Buffer percent this consumer must reach to restart after a cutoff. Shown in the GUI readout. */
    public int rebootThresholdPct() {
        return clampedRebootThreshold(active());
    }

    /** [6] */
    public boolean serviceTick(boolean active) {
        return serviceTick(active, 1.0f);
    }

    /** [7] */
    public boolean serviceTick(boolean active, float loadMultiplier) {
        // Roll the input meter over regardless of mode so the GUI can show incoming FE.
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

        // [8]
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
                // A genuine starve: the last partial tick burns whatever was left.
                measuredDrain = energy;
                energy = 0;
            } else {
                // Charging back up toward the reboot threshold. Do not burn the reserve being
                // accumulated, or the consumer could never climb out of a cutoff.
                measuredDrain = 0;
            }
            ok = false;
        }

        if (hasTicked && ok != powered) {
            if (ticksSinceTransition >= TRANSITION_QUIET_TICKS) {
                // Nothing has flipped for a while, so this starts a new burst.
                flipsThisBurst = 0;
            }
            if (flipsThisBurst < MAX_BEATS_PER_BURST) {
                transition = ok ? 1 : -1;
            }
            flipsThisBurst++;
            // Reset on every flip, suppressed or not, so a consumer that keeps cycling never
            // reaches the quiet period and never re-earns its beats.
            ticksSinceTransition = 0;
        }
        powered = ok;
        hasTicked = true;
        notifyIfChanged();
        syncIfDue();
        return ok;
    }

    /** [9] */
    public boolean spendBurst() {
        return spendBurst(active().burstCost());
    }

    public boolean spendBurst(int amount) {
        final Settings tuning = active();
        if (!tuning.enabled() || amount <= 0) {
            burstStarved = false;
            return true;
        }
        // [10]
        burstStarved = amount > tuning.capacity();
        clampToCapacity();
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        return true;
    }

    /** [11] */
    public void transferChargeTo(FeConsumer other) {
        if (other == this || energy <= 0) {
            return;
        }
        energy -= other.receiveEnergy(energy, false);
        notifyIfChanged();
    }

    /** True when the block may run: FE mode off, or the last serviceTick was covered. */
    public boolean isPowered() {
        return !active().enabled() || powered;
    }

    /** Buffer fill in percent, 0..100, always reflecting the real stored energy. */
    public float fillPct() {
        final int capacity = Math.max(1, active().capacity());
        return Math.max(0.0f, Math.min(100.0f, energy * 100.0f / capacity));
    }

    /** [12] */
    public float hudBufferPct() {
        return active().enabled() ? fillPct() : 100.0f;
    }

    /** True when the FE panel should be shown: the mode is on, or the buffer holds charge. */
    public boolean shouldDisplay() {
        return active().enabled() || energy > 0;
    }

    /** True while FE mode is on and the buffer sits under {@link #LOW_POWER_PCT}. */
    public boolean isBrownout() {
        return active().enabled() && fillPct() < LOW_POWER_PCT;
    }

    /** [13] */
    public boolean isRecharging() {
        final Settings tuning = active();
        return tuning.enabled() && !powered && fillPct() < clampedRebootThreshold(tuning);
    }

    /** [14] */
    public int pollTransition() {
        final int t = transition;
        transition = 0;
        return t;
    }

    // client mirror

    /** The server-authoritative picture of this consumer, for the sync packet and for grid reads. */
    public FePowerSnapshot snapshot() {
        final Settings tuning = active();
        return new FePowerSnapshot(tuning.enabled(), powered, burstStarved, energy, tuning.capacity(),
                tuning.idleDrain(), tuning.activeDrain(), tuning.burstCost(), clampedRebootThreshold(tuning),
                measuredDrain, measuredInput, appliedMultiplier, priority, loadShed, lock);
    }

    /**
     * Client only. Adopt a snapshot from the server as this mirror's entire truth, tuning included.
     * Never called on a server instance, so it cannot influence {@link #serviceTick}.
     */
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

    // persistence (server NBT only)

    public void save(CompoundTag tag) {
        tag.putInt("FeEnergy", energy);
        tag.putBoolean("FePowered", powered);
        tag.putString("FePriority", priority.name());
        if (lock != FeLock.NONE) {
            // Written only when set, so an untouched consumer's tag is byte-identical to before.
            tag.putString("FeLock", lock.name());
        }
    }

    public void load(CompoundTag tag) {
        // [15]
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
        // [16]
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

    // iEnergyStorage (receive-only)

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        // [17]
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
        // Always advertise as a valid FE sink so pushing sources (Create Crafts and Additions
        // connectors, other FE cables, our own dynamo) feed the buffer regardless of the mode.
        return true;
    }

    // internal

    /** Reboot threshold, clamped so a hostile config value cannot make a block unrecoverable. */
    private static int clampedRebootThreshold(Settings tuning) {
        return Math.max(0, Math.min(90, tuning.rebootThresholdPct()));
    }

    private void clampToCapacity() {
        final int capacity = Math.max(0, active().capacity());
        if (energy > capacity) {
            energy = capacity;
        }
    }

    /** [18] */
    private int currentSignature() {
        final Settings tuning = active();
        return FePowerSnapshot.signature(tuning.enabled(), powered, burstStarved, fillPct(),
                measuredDrain, measuredInput, appliedMultiplier, tuning.capacity(), tuning.activeDrain(),
                priority.ordinal(), loadShed, lock.ordinal());
    }

    /** Fires the owner's change callback when observable state moved. Unthrottled, unlike the sync. */
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

    /** [19] */
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
        // A power flip is what the player actually sees, so it never waits for the throttle.
        if (changed && transition == 0 && ticksSinceSync < MIN_SYNC_GAP_TICKS) {
            return;
        }
        lastSyncSignature = signature;
        ticksSinceSync = 0;
        owner.setChanged();
        FePowerSync.push(level, owner.getBlockPos(), snapshot());
    }
}
