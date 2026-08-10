package com.createstats.blockentity;

import com.createstats.block.FlywheelGearValueBoxTransform;
import com.createstats.block.KineticFlywheelBlock;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import net.createmod.catnip.lang.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Block entity for {@link KineticFlywheelBlock}: a kinetic inertia bank.
 *
 * <h3>Energy model</h3>
 * State is a single scalar {@code storedEnergy} (0..{@link #MAX_ENERGY}) plus a locked
 * {@code spinSign}. The visible/output speed is <em>derived</em> from energy using the
 * physical relation {@code E = ½·I·ω²} ⇒ {@code ω ∝ √E}:
 * <pre>storedRpm = spinSign · MAX_RPM · √(storedEnergy / MAX_ENERGY)</pre>
 * so as energy bleeds away the wheel naturally coasts down along a √-curve, no separate
 * RPM integration needed.
 *
 * <h3>Role flip (no network contention)</h3>
 * <ul>
 *   <li><b>CHARGING</b>, {@code getGeneratedSpeed()==0} (not a source) and
 *       {@code calculateStressApplied()>0} (a consumer): charging costs SU. We read the
 *       external network speed via {@link #getSpeed()} and pull energy toward it.</li>
 *   <li><b>DISCHARGING</b>, the sole source: {@code getGeneratedSpeed()} returns the
 *       derived stored RPM and {@code calculateAddedStressCapacity()} scales with energy.
 *       Entered only when the network is otherwise idle, so two sources never collide.</li>
 * </ul>
 * A conflict heuristic yields the source role within a few ticks if an external source
 * returns while discharging.
 */
public class KineticFlywheelBE extends GeneratingKineticBlockEntity {

    // balance constants (the clean "SU Battery" model)
    //
    // ONE model: the bank is a pure SU-second battery. Reserve is denominated directly in SU·s.
    // Charging banks 100% of what it draws; discharging spends reserve at 1/EFFICIENCY of what it
    // delivers, so the round-trip is a flat EFFICIENCY at every tier and gear. Output RPM is a fixed
    // constant (no overdrive, no √-curve decay). The √-curve getStoredRpm() is CLIENT VISUAL ONLY and
    // never feeds the network or drain math. Mass tiers scale output + reserve together (seconds and
    // RPM stay fixed); gears change power (rate) only, never efficiency.
    /** Constant RPM the bank sources at while discharging at FULL reserve (sags with reserve below that). */
    public static final float OUTPUT_RPM = 256f;
    /** Speed-sag exponent: sourced RPM = OUTPUT_RPM · (reserve%)^EXP (0.4 ≈ 75%→90%, 50%→75%). */
    private static final double SPEED_CURVE_EXP = 0.4;
    /** Smart command: below this reserve fraction the flywheel can no longer hold a commanded RPM (brownout). */
    private static final float BROWNOUT_FRAC = 0.05f;
    /** Highest number of Create flywheels that can be added on top of the base block. */
    public static final int MAX_TIER = 3;
    /** Base output stress capacity (su) at tier 0, gear 1. */
    private static final float OUTPUT_BASE = 32_768f;
    /** Base reserve capacity (su·s) at tier 0: 32,768 su × 90 s ÷ 0.9. */
    private static final float RESERVE_BASE = 3_276_800f;
    /** Base charge power (su) drawn at tier 0, gear 1: fills the base reserve in 180 s. */
    private static final float CHARGE_BASE = RESERVE_BASE / 180f; // 18,204.4 su
    /** Round-trip efficiency: delivered su·s ÷ charged su·s. All loss is taken on discharge. */
    private static final float EFFICIENCY = 0.90f;
    /** Friction floor: whenever the bank is not charging (discharging unloaded, parked, or held) it bleeds
     *  at 10% of rated output, so idle life is finite (~900 s base) and a held bank is never a free battery. */
    private static final float IDLE_FLOOR = 0.10f;
    /** Reserve (su·s) below which the bank stops sourcing (clean cutoff). */
    private static final float MIN_RESERVE = 250f;
    /** Output + reserve multiplier per mass stage (base ×1 … +3 ×4). */
    private static final float[] CAPACITY_TIER_MULT = {1f, 2f, 3f, 4f};
    /** Charge-gear power multipliers: ×1/×2/×3 → fills in 180/90/60 s. */
    private static final float[] CHARGE_GEAR_MULT = {1f, 2f, 3f};
    /** Discharge-gear output multipliers: ×1/×2/×3 → sustain 90/45/30 s. */
    private static final float[] DISCHARGE_GEAR_MULT = {1f, 2f, 3f};
    private static final float EXTERNAL_EPS = 1f;
    /** Ticks the bank stays disarmed after an overstress trip before it spends a re-arm attempt (2 s). */
    private static final int OVERSTRESS_RETRY_TICKS = 40;
    /** Fixed re-arm cost charged on each post-overstress retry: 5% of TOTAL capacity, drained from reserve. */
    private static final float ARM_COST_FRACTION = 0.05f;
    /** Re-push the network stress when the coefficient moves more than this (su-per-RPM). */
    private static final float STRESS_PUSH_EPS = 0.5f;

    public enum Mode { IDLE, CHARGING, DISCHARGING }

    /** Three discrete gears used by both the charge and discharge value boxes. */
    public enum Gear implements INamedIconOptions {
        X1(AllIcons.I_PRIORITY_LOW),
        X2(AllIcons.I_PRIORITY_HIGH),
        X3(AllIcons.I_PRIORITY_VERY_HIGH);

        private final AllIcons icon;
        private final String translationKey;

        Gear(AllIcons icon) {
            this.icon = icon;
            this.translationKey = "create_stats.kinetic_flywheel.gear." + Lang.asId(name());
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }

    // simulation state (persisted)
    /** The battery charge, in SU-seconds. The single source of truth for the bank's energy. */
    private float reserve;
    private int spinSign = 1;
    private Mode mode = Mode.IDLE;
    /** Number of Create flywheels added on top of the base block (0..MAX_TIER). */
    private int tier = 0;

    // derived / transient
    private int outputSpeed;          // value getGeneratedSpeed() returns
    private int conflictTicks;        // discharge conflict debounce
    private int comparatorBucket = -1;
    private int sendCooldown;
    private float chargeLoad;         // 0..1 absorption demand while charging (drives stress impact)
    private int overstressHoldTicks;  // >0: halted because the powered network was overstressed
    private boolean overstressDisarmed; // true while disarmed by an overstress trip (awaiting a paid re-arm)
    private float lastPushedStress = Float.NaN; // last consumer coefficient pushed to the network
    private float chargeCoeff;        // SU-per-RPM presented to the network while charging
    private float lastOutputStress;   // SU actually delivered to the network last discharge tick
    private int brakeSignal;          // redstone brake/dump lever (0..15), re-read each server tick
    private boolean brakeFullStop;    // true at signal 15: no network output, no charging, full dump
    private boolean toppedSoundPlayed; // chime guard: true while sitting at full reserve
    private float commandedRpm;        // FLC Smart command: hold this output RPM (0 = none, natural sag)
    private int commandTicks;          // server-side expiry; the command lapses if the FLC stops refreshing

    // in-world gear selectors (rear face; auto-persisted + auto-synced)
    private static final BehaviourType<GearScrollOptionBehaviour<Gear>> CHARGE_GEAR_TYPE = new BehaviourType<>();
    private static final BehaviourType<GearScrollOptionBehaviour<Gear>> DISCHARGE_GEAR_TYPE = new BehaviourType<>();
    private GearScrollOptionBehaviour<Gear> chargeGear;
    private GearScrollOptionBehaviour<Gear> dischargeGear;

    // panel-editable settings
    private KineticFlywheelSettings settings = new KineticFlywheelSettings();

    // client animation
    private float clientAngle;        // accumulated degrees for the spinning wheel
    private float prevClientAngle;

    public KineticFlywheelBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        chargeGear = new GearScrollOptionBehaviour<>(Gear.class,
            Component.translatable("block.create_stats.kinetic_flywheel.charge_gear"),
            this, new FlywheelGearValueBoxTransform(true), CHARGE_GEAR_TYPE, "ChargeGear", 0);
        chargeGear.withCallback(i -> onGearChanged());
        behaviours.add(chargeGear);

        dischargeGear = new GearScrollOptionBehaviour<>(Gear.class,
            Component.translatable("block.create_stats.kinetic_flywheel.discharge_gear"),
            this, new FlywheelGearValueBoxTransform(false), DISCHARGE_GEAR_TYPE, "DischargeGear", 1);
        dischargeGear.withCallback(i -> onGearChanged());
        behaviours.add(dischargeGear);
    }

    /** Re-evaluate the network impact immediately when a gear changes. */
    private void onGearChanged() {
        pushStress(true);
    }

    /** Charge-gear index (0..2); 0 if the behaviour is not yet attached. */
    public int getChargeGearIndex() {
        return chargeGear == null ? 0 : Mth.clamp(chargeGear.getValue(), 0, CHARGE_GEAR_MULT.length - 1);
    }

    /** Discharge-gear index (0..2); 0 if the behaviour is not yet attached. */
    public int getDischargeGearIndex() {
        return dischargeGear == null ? 0 : Mth.clamp(dischargeGear.getValue(), 0, DISCHARGE_GEAR_MULT.length - 1);
    }

    /** SU drawn from the network while charging at the current tier + charge gear (constant power). */
    public float getChargeFullLoadStress() {
        return chargePower();
    }

    /** Output stress capacity (su) this flywheel supplies at the current tier + discharge gear. */
    public float getDischargeMaxCapacity() {
        return output();
    }

    // ============================ Mass-stage (tier) ============================

    /** Current mass stage: number of added Create flywheels (0 = base block). */
    public int getTier() {
        return tier;
    }

    /** Highest reachable mass stage. */
    public int getMaxTier() {
        return MAX_TIER;
    }

    /** Total flywheel discs visible/present (base + added). */
    public int getFlywheelCount() {
        return tier + 1;
    }

    /** Total reserve capacity (su·s) for the current mass stage (×1/×2/×3/×4). */
    public float capacity() {
        return RESERVE_BASE * CAPACITY_TIER_MULT[Mth.clamp(tier, 0, MAX_TIER)];
    }

    /** Output stress capacity (su) for the current mass stage + discharge gear. */
    public float output() {
        return OUTPUT_BASE * CAPACITY_TIER_MULT[Mth.clamp(tier, 0, MAX_TIER)]
            * DISCHARGE_GEAR_MULT[getDischargeGearIndex()];
    }

    /** Charge power (su) drawn while charging for the current mass stage + charge gear. */
    private float chargePower() {
        return CHARGE_BASE * CAPACITY_TIER_MULT[Mth.clamp(tier, 0, MAX_TIER)]
            * CHARGE_GEAR_MULT[getChargeGearIndex()];
    }

    /**
     * Set the mass stage (server side). Reserve is conserved but clamped to the new capacity; both
     * output and reserve scale by the same tier multiplier, so sustain seconds and RPM stay fixed.
     */
    public void setTier(int newTier) {
        newTier = Mth.clamp(newTier, 0, MAX_TIER);
        if (newTier == tier) {
            return;
        }
        tier = newTier;
        reserve = Mth.clamp(reserve, 0f, capacity());
        setChanged();
        sendData();
    }

    // ============================ Derived values ============================

    /**
     * Signed sourced/visible RPM: sags with the reserve along {@code OUTPUT_RPM · (reserve%)^EXP}, so
     * the wheel (and the network it drives) slows down as the bank depletes. Drives BOTH the client
     * animation AND the network output speed, so they always match. The SU·s battery accounting is
     * separate (see tickDischarge), this only sets the speed; rated stress capacity stays constant.
     */
    public float getStoredRpm() {
        float frac = Mth.clamp(reserve / capacity(), 0f, 1f);
        return spinSign * OUTPUT_RPM * (float) Math.pow(frac, SPEED_CURVE_EXP);
    }

    public float getChargePercent() {
        return Mth.clamp(reserve / capacity(), 0f, 1f) * 100f;
    }

    /** The kinetic network this flywheel belongs to, as a stable key (null when unconnected). */
    public Long getNetworkKey() {
        if (!hasNetwork()) {
            return null;
        }
        var network = getOrCreateNetwork();
        return network == null ? null : network.id;
    }

    /**
     * Output RPM actually driven onto the network: a Smart command holds a fixed RPM while the reserve
     * can sustain it; otherwise the natural sag ({@link #getStoredRpm()}). A brownout (reserve below
     * {@link #BROWNOUT_FRAC}) drops the command and sags, as agreed.
     */
    public float effectiveOutputRpm() {
        if (commandedRpm > 0f && reserve > capacity() * BROWNOUT_FRAC) {
            return spinSign * Math.min(commandedRpm, OUTPUT_RPM);
        }
        return getStoredRpm();
    }

    /** FLC Smart authority: hold a target output RPM (re-applied each control pass; lapses if it stops). */
    public void commandOutputRpm(float rpm) {
        float clamped = Mth.clamp(rpm, 0f, OUTPUT_RPM);
        commandTicks = 30;
        if (clamped != commandedRpm) {
            commandedRpm = clamped;
            setChanged();
            sendData();
        }
    }

    /** Drop any Smart RPM command so the bank returns to its natural sag. */
    public void clearOutputCommand() {
        commandTicks = 0;
        if (commandedRpm != 0f) {
            commandedRpm = 0f;
            setChanged();
            sendData();
        }
    }

    public Mode getMode() {
        return mode;
    }

    public int getModeIndex() {
        return mode.ordinal();
    }

    /** Real added stress capacity (su) for telemetry: the SU actually delivered to the network. */
    public float getOutputStress() {
        return mode == Mode.DISCHARGING ? lastOutputStress : 0f;
    }

    /** True while the bank has stopped discharging because the network it powered was overstressed. */
    public boolean isOverstressHalted() {
        return overstressHoldTicks > 0;
    }

    /** Seconds remaining before the next post-overstress re-arm attempt (0 when not halted). */
    public float getReArmSeconds() {
        return overstressHoldTicks > 0 ? overstressHoldTicks / 20f : 0f;
    }

    /** Reserve cost charged on each post-overstress re-arm attempt, as a percent of total capacity. */
    public static float getReArmCostPercent() {
        return ARM_COST_FRACTION * 100f;
    }

    public int getComparatorOutput() {
        return Mth.clamp(Math.round(getChargePercent() / 100f * 15f), 0, 15);
    }

    /** Current charge absorption demand (0 topped-off .. 1 full load); only meaningful while charging. */
    public float getChargeLoad() {
        return mode == Mode.CHARGING ? chargeLoad : 0f;
    }

    /** True while charging but already at/near full reserve (drawing only maintenance stress). */
    public boolean isToppedOff() {
        return mode == Mode.CHARGING && chargeLoad <= 0.05f;
    }

    /** True when the reserve is critically low (mirrors the charge bar going red). */
    public boolean isLowReserve() {
        return getChargePercent() < settings.getLowReservePct();
    }

    // ============================ Panel control surface ============================

    public KineticFlywheelSettings getSettings() {
        return this.settings;
    }

    /** Apply settings pushed from the Stats Display panel (server side). */
    public void setSettings(KineticFlywheelSettings newSettings) {
        if (newSettings == null) {
            return;
        }
        this.settings = newSettings;
        setChanged();
        sendData();
    }

    /** True while discharge is permitted by policy (panel arm switch). */
    public boolean isDischargeArmed() {
        return settings.isDischargeArmed();
    }

    /** Manually park the bank: stop any active discharge and hold the reserve (uses the hold latch). */
    public void forcePark() {
        overstressHoldTicks = OVERSTRESS_RETRY_TICKS;
        if (mode == Mode.DISCHARGING) {
            mode = Mode.IDLE;
        }
        conflictTicks = 0;
        setChanged();
        sendData();
    }

    /** Release a current hold (manual park or overstress halt) so the bank can retry discharge now. */
    public void release() {
        overstressHoldTicks = 0;
        overstressDisarmed = false;
        setChanged();
        sendData();
    }

    /** Deduct the fixed re-arm cost (5% of total capacity) from the reserve, floored at 0. */
    private void payReArmCost() {
        reserve = Math.max(0f, reserve - ARM_COST_FRACTION * capacity());
    }

    /**
     * Estimate (seconds) the bank can keep sustaining the network at the CURRENT delivered load.
     * Single source of truth: {@code reserve × EFFICIENCY ÷ (output × max(loadFraction, IDLE_FLOOR))}.
     * The 10% floor keeps idle life finite (900 s base) instead of reporting an absurd value.
     */
    public float getEstimatedSustainSeconds() {
        if (mode != Mode.DISCHARGING) {
            return 0f;
        }
        float usable = reserve - MIN_RESERVE;
        if (usable <= 0f) {
            return 0f;
        }
        float out = output();
        if (out <= 0f) {
            return 0f;
        }
        float loadFraction = Math.max(lastOutputStress / out, IDLE_FLOOR);
        return reserve * EFFICIENCY / (out * loadFraction);
    }

    // ============================ Kinetic source contract ============================

    @Override
    public float getGeneratedSpeed() {
        return outputSpeed;
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (mode != Mode.DISCHARGING || brakeFullStop) {
            return 0f;
        }
        // Create scales this coefficient by |generatedSpeed| (the sagging RPM), so output()/rpm × rpm
        // = output(), the rated stress capacity stays CONSTANT even as the speed sags. Machines run
        // slower as the bank depletes but the network never overstresses from the slowdown.
        return output() / Math.max(Math.abs(effectiveOutputRpm()), 1f);
    }

    @Override
    public float calculateStressApplied() {
        // While charging, present the exact SU-per-RPM coefficient we are actually drawing (set in
        // tickAbsorb). The network's real SU cost = coeff × |speed|, which is precisely what we bank
        // into the reserve that tick, so charging is fully conserved.
        float impact = mode == Mode.CHARGING ? chargeCoeff : 0f;
        this.lastStressApplied = impact;
        return impact;
    }

    // ============================ Tick ============================

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            prevClientAngle = clientAngle;
            // The wheel normally spins at its stored momentum (so it visibly coasts down while
            // discharging). While it is being driven, spin it with the shaft instead so it turns
            // immediately instead of lagging behind the stored-energy build-up. This also makes the
            // wheel animate inside Ponder, where the server simulation never runs but the network
            // speed is set directly.
            float spin = effectiveOutputRpm();
            if (mode != Mode.DISCHARGING) {
                float net = getSpeed();
                if (Math.abs(net) > Math.abs(spin)) {
                    spin = net;
                }
            }
            clientAngle += spin * 3f / 10f;
            // Ambient hum: driven from the client tick so it plays for every loaded flywheel near the
            // player regardless of whether it is on screen (mirroring how Create's kinetic blocks make
            // sound). The instance handles its own fade-out, distance and de-duplication.
            if (mode != Mode.IDLE) {
                com.createstats.client.FlywheelSoundInstance.startIfNeeded(this);
            }
            return;
        }
        serverTick();
    }

    private void serverTick() {
        Mode previousMode = mode;
        // Redstone brake/dump lever: a higher signal bleeds the reserve faster as heat; at 15 it stops
        // powering the network and blocks charging (a pure resistive dump). Re-read every tick.
        brakeSignal = level.getBestNeighborSignal(worldPosition);
        brakeFullStop = brakeSignal >= 15;
        if (commandTicks > 0 && --commandTicks == 0 && commandedRpm != 0f) {
            commandedRpm = 0f;
            sendData();
        }

        if (mode == Mode.DISCHARGING) {
            tickDischarge();
        } else {
            tickAbsorb();
        }
        applyBrakeDump();

        // Soft chime the instant the reserve tops off while charging (once per fill cycle).
        boolean reserveFull = reserve >= capacity() - 1f;
        if (reserveFull && !toppedSoundPlayed && mode == Mode.CHARGING) {
            // Pleasant "charged" cue: a layered two-note amethyst chime (vanilla, improved by stacking).
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f);
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.55f, 1.5f);
            toppedSoundPlayed = true;
        } else if (!reserveFull) {
            toppedSoundPlayed = false;
        }

        // The bank sources at an RPM that sags with the reserve (quantised to limit network churn).
        // A full brake (signal 15) cuts network output entirely.
        int desired = (mode == Mode.DISCHARGING && !brakeFullStop) ? quantize(effectiveOutputRpm()) : 0;
        if (desired != outputSpeed) {
            outputSpeed = desired;
            updateGeneratedRotation();
        }

        if (mode != previousMode) {
            updateVisualState();
        }
        // Charging consumes SU but never changes outputSpeed, so the network is not otherwise told
        // about our impact. Push the consumer coefficient ourselves (throttled, or forced on a mode flip).
        pushStress(mode != previousMode);
        refreshComparator();
        emitParticles();
        maybeSync(mode != previousMode);
    }

    /**
     * Tell our kinetic network how much stress this flywheel now applies as a consumer. Create
     * caches each member's contribution and only re-aggregates when triggered, so a mid-tick charge
     * (which leaves outputSpeed at 0) would otherwise draw no real SU and show nothing on the
     * stressometer. Throttled by {@link #STRESS_PUSH_EPS}; pass {@code force} to push immediately.
     */
    private void pushStress(boolean force) {
        if (level == null || level.isClientSide || !hasNetwork()) {
            return;
        }
        float current = calculateStressApplied();
        if (force || Float.isNaN(lastPushedStress) || Math.abs(current - lastPushedStress) > STRESS_PUSH_EPS) {
            getOrCreateNetwork().updateStressFor(this, current);
            lastPushedStress = current;
        }
    }

    /** Not generating: absorb from an external source, or begin discharge if the net is idle. */
    private void tickAbsorb() {
        float ext = getSpeed();
        if (Math.abs(ext) > EXTERNAL_EPS && !brakeFullStop) {
            mode = Mode.CHARGING;
            overstressHoldTicks = 0; // a live driver supersedes any overstress hold
            // Direction-agnostic: a battery stores energy whichever way it is driven. The output sign
            // simply follows the last drive direction. No braking / spin-matching.
            spinSign = ext >= 0 ? 1 : -1;
            float absExt = Math.abs(ext);
            float room = capacity() - reserve;
            if (room > 0f && absExt > EXTERNAL_EPS) {
                // Constant-power charge: draw up to chargePower() su and bank 100% of it (no charge-side
                // loss). Cap to the remaining room so the topping tick neither over-charges nor over-bills.
                float maxBankThisTick = chargePower() / 20f; // su·s drawable this tick
                float add = Math.min(maxBankThisTick, room);
                reserve = Mth.clamp(reserve + add, 0f, capacity());
                // Bill the network exactly the su·s we banked (coefficient = banked-su ÷ |speed|).
                chargeCoeff = (add * 20f) / absExt;
                chargeLoad = Mth.clamp(add / Math.max(maxBankThisTick, 1e-4f), 0f, 1f);
            } else {
                // Reserve full: hold, draw nothing.
                chargeCoeff = 0f;
                chargeLoad = 0f;
            }
        } else {
            chargeLoad = 0f;
            chargeCoeff = 0f;
            if (overstressHoldTicks > 0) {
                // Disarmed after an overstress trip: preserve the reserve and stay fully idle (neither
                // seizing the source role nor fighting a sibling flywheel) for the 2 s cooldown.
                overstressHoldTicks--;
                mode = Mode.IDLE;
                if (overstressHoldTicks == 0 && overstressDisarmed) {
                    // 2 s elapsed: spend one re-arm attempt. Pay the fixed 5%-of-capacity cost and force a
                    // discharge retry. If the net is still overstressed, tickDischarge trips and disarms us
                    // for another 2 s, a bounded, self-limiting retry that can't spin every tick.
                    overstressDisarmed = false;
                    if (settings.isDischargeArmed() && reserve > MIN_RESERVE) {
                        payReArmCost();
                        mode = Mode.DISCHARGING;
                        conflictTicks = 0;
                    }
                }
            } else if (isOverStressed()) {
                // A source is present but the network is stalled by overstress. Reading speed 0 here
                // does NOT mean the net is idle, so stay a (zero-impact at 0 rpm) consumer instead of
                // seizing the source role and oscillating CHARGING<->DISCHARGING.
                mode = Mode.CHARGING;
            } else if (settings.isDischargeArmed() && reserve > MIN_RESERVE) {
                // network's actually idle and discharge is armed - sustain from the reserve
                mode = Mode.DISCHARGING;
                conflictTicks = 0;
            } else {
                // Parked or held (disarmed): not being driven. Friction is applied centrally below.
                mode = Mode.IDLE;
            }
        }
        // Idle friction: any bank that is NOT actively charging (held, disarmed, or the brief post-
        // overstress cooldown) still winds down at the same 10% floor as a spinning-but-unloaded
        // discharge, so a parked flywheel is never a free permanent battery (finite ~900 s life).
        // Identical per-tick drain to the IDLE_FLOOR branch in tickDischarge so the rate matches in
        // every non-charging state. Only an external driver (CHARGING) preserves the reserve.
        if (mode == Mode.IDLE && reserve > 0f) {
            reserve = Math.max(0f, reserve - (IDLE_FLOOR * output() / EFFICIENCY) / 20f);
        }
    }

    /** Generating: spend the reserve at the network's load (capacity-shared), with a 10% idle floor. */
    private void tickDischarge() {
        if (reserve <= MIN_RESERVE) {
            reserve = Math.max(0f, reserve);
            mode = Mode.IDLE;
            return;
        }

        // Discharge disarmed by the panel mid-run: stop sourcing immediately and hold the reserve.
        if (!settings.isDischargeArmed()) {
            mode = Mode.IDLE;
            conflictTicks = 0;
            return;
        }

        // Overstressed network: DISARM the bank (stop discharging, hold the reserve) instead of bleeding
        // it into a stalled network. It stays disarmed for 2 s, then spends one paid re-arm attempt; this
        // breaks the per-tick overstress loop and the oscillation seen with 2+ flywheels on one net.
        if (isOverStressed()) {
            mode = Mode.IDLE;
            overstressDisarmed = true;
            overstressHoldTicks = OVERSTRESS_RETRY_TICKS;
            conflictTicks = 0;
            overstressBurst();
            return;
        }

        // Conflict heuristic: we command a non-zero speed but the network collapsed -> someone
        // else is fighting us. Yield the source role so we can read the external speed again.
        if (outputSpeed != 0 && Math.abs(getSpeed()) < 1f) {
            if (++conflictTicks > 3) {
                mode = Mode.CHARGING;
                conflictTicks = 0;
                return;
            }
        } else {
            conflictTicks = 0;
        }

        // A foreign source returned and is driving the network against or faster than our output.
        // Create lets the stronger source win, so yield the source role and go back to CHARGING.
        float netSpeed = getSpeed();
        if (sign(netSpeed) != sign(outputSpeed) && Math.abs(netSpeed) > EXTERNAL_EPS) {
            mode = Mode.CHARGING;
            conflictTicks = 0;
            return;
        }
        if (Math.abs(netSpeed) > Math.abs(outputSpeed) + 1f) {
            mode = Mode.CHARGING;
            conflictTicks = 0;
            return;
        }

        // Capacity-share delivery: with N flywheels on one net, each delivers its own share of the
        // network's total demand (myCap / totalCap), so the sum equals the demand exactly, no N×
        // over-drain. Drain the reserve by delivered/EFFICIENCY (all loss on discharge), floored at
        // IDLE_FLOOR × output so a spinning-but-idle bank still winds down (finite 900 s).
        float myCap = output();
        float demand = 0f;
        float totalCap = myCap;
        if (hasNetwork()) {
            var net = getOrCreateNetwork();
            demand = Math.max(0f, net.calculateStress());
            totalCap = Math.max(myCap, net.calculateCapacity());
        }
        float delivered = totalCap > 0f ? Mth.clamp(demand, 0f, totalCap) * (myCap / totalCap) : 0f;
        if (brakeFullStop) {
            // Full brake: stop powering the network; only friction + the brake dump bleed the reserve.
            delivered = 0f;
        }
        lastOutputStress = delivered;
        float effectiveLoad = Math.max(delivered, IDLE_FLOOR * myCap);
        reserve -= (effectiveLoad / EFFICIENCY) / 20f;
        reserve = Math.max(0f, reserve);
    }

    private void updateVisualState() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof KineticFlywheelBlock)) {
            return;
        }
        KineticFlywheelBlock.VisualState desired = switch (mode) {
            case DISCHARGING -> KineticFlywheelBlock.VisualState.DISCHARGING;
            case CHARGING -> KineticFlywheelBlock.VisualState.CHARGING;
            case IDLE -> KineticFlywheelBlock.VisualState.IDLE;
        };
        if (state.getValue(KineticFlywheelBlock.VISUAL_STATE) != desired) {
            level.setBlock(worldPosition, state.setValue(KineticFlywheelBlock.VISUAL_STATE, desired), 3);
        }
    }

    private void refreshComparator() {
        if (level == null) {
            return;
        }
        int bucket = getComparatorOutput();
        if (bucket != comparatorBucket) {
            comparatorBucket = bucket;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private void maybeSync(boolean force) {
        if (force) {
            sendCooldown = 0;
        }
        if (sendCooldown > 0) {
            sendCooldown--;
            return;
        }
        sendCooldown = 8;
        sendData();
        setChanged();
    }

    private static int sign(float v) {
        return v >= 0 ? 1 : -1;
    }

    /** Round the sourced RPM to a 4-rpm step so the network re-solves less often as the bank sags. */
    private static int quantize(float rpm) {
        return Math.round(rpm / 4f) * 4;
    }

    /**
     * Redstone brake/dump: while a signal is present, bleed the reserve as heat (lost, not delivered).
     * The dump load scales from ~5% of rated output at signal 1 to ~90% at signal 15, so a full base
     * reserve empties in roughly 100 s at full brake. At signal 15 the network output and charging are
     * cut elsewhere, so this becomes a pure resistive dump.
     */
    private void applyBrakeDump() {
        if (brakeSignal <= 0 || reserve <= MIN_RESERVE) {
            return;
        }
        float brakeFrac = 0.05f + 0.85f * (brakeSignal - 1) / 14f;
        reserve = Math.max(0f, reserve - (brakeFrac * output()) / 20f);
    }

    /** Ambient feedback: faint sparks while delivering, smoke/sparks while braking energy off as heat. */
    private void emitParticles() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        long t = level.getGameTime();
        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 0.5;
        double cz = worldPosition.getZ() + 0.5;
        if (mode == Mode.DISCHARGING && !brakeFullStop && lastOutputStress > 0f && t % 6 == 0) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                cx, cy, cz, 1, 0.34, 0.34, 0.34, 0.0);
        }
        if (brakeSignal > 0 && reserve > MIN_RESERVE) {
            int every = Math.max(2, 8 - brakeSignal / 2);
            if (t % every == 0) {
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    cx, cy + 0.1, cz, 1, 0.22, 0.22, 0.22, 0.01);
                if (brakeSignal >= 8) {
                    server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        cx, cy, cz, 2, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }
    }

    /** One-shot burst when the bank trips on an overstressed network. */
    private void overstressBurst() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 0.5;
        double cz = worldPosition.getZ() + 0.5;
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, cx, cy + 0.2, cz, 12, 0.26, 0.26, 0.26, 0.02);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, cx, cy, cz, 8, 0.3, 0.3, 0.3, 0.05);
        server.playSound(null, worldPosition, com.createstats.registry.ModSounds.FLYWHEEL_OVERSTRESS.get(),
            net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1.0f);
    }

    // ============================ Client render accessors ============================

    /** Smoothly interpolated accumulated wheel angle in degrees. */
    public float getRenderAngle(float partialTicks) {
        return Mth.lerp(partialTicks, prevClientAngle, clientAngle);
    }

    // ============================ Persistence ============================

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("Reserve", reserve);
        compound.putInt("SpinSign", spinSign);
        compound.putInt("Mode", mode.ordinal());
        compound.putInt("OutputSpeed", outputSpeed);
        compound.putInt("Tier", tier);
        compound.putFloat("CommandedRpm", commandedRpm);
        compound.put("Settings", settings.save());
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        // Migrate the old "StoredEnergy" key (pre-redesign saves) to the new "Reserve" key.
        reserve = compound.contains("Reserve") ? compound.getFloat("Reserve") : compound.getFloat("StoredEnergy");
        spinSign = compound.getInt("SpinSign");
        if (spinSign == 0) {
            spinSign = 1;
        }
        int modeIdx = compound.getInt("Mode");
        Mode[] modes = Mode.values();
        mode = modeIdx >= 0 && modeIdx < modes.length ? modes[modeIdx] : Mode.IDLE;
        outputSpeed = compound.getInt("OutputSpeed");
        tier = Mth.clamp(compound.getInt("Tier"), 0, MAX_TIER);
        commandedRpm = compound.getFloat("CommandedRpm");
        if (compound.contains("Settings")) {
            settings = KineticFlywheelSettings.load(compound.getCompound("Settings"));
        }
    }

    // ============================ Goggles ============================

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        float chargePct = getChargePercent();

        tooltip.add(Component.literal(" ").append(
            Component.translatable("block.create_stats.kinetic_flywheel.goggle.title")
                .withStyle(ChatFormatting.GRAY)));

        tooltip.add(Component.literal("    ").append(
            Component.translatable("block.create_stats.kinetic_flywheel.goggle.stage",
                getFlywheelCount(), MAX_TIER + 1)
                .withStyle(ChatFormatting.LIGHT_PURPLE)));

        // Discoverability: tell the player the block can still be upgraded with a Flywheel item.
        if (getTier() < MAX_TIER) {
            tooltip.add(Component.literal("    ").append(
                Component.translatable("block.create_stats.kinetic_flywheel.goggle.upgrade_hint")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
        }

        // Visual charge bar: ten segments coloured by reserve level.
        ChatFormatting barColor = chargePct >= 60f ? ChatFormatting.GREEN
            : chargePct >= 25f ? ChatFormatting.GOLD
            : ChatFormatting.RED;
        int filled = Mth.clamp(Math.round(chargePct / 10f), 0, 10);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? '\u25A0' : '\u25A1');
        }
        tooltip.add(Component.literal("    ")
            .append(Component.literal(bar.toString()).withStyle(barColor))
            .append(Component.literal(String.format(" %.0f%%", chargePct)).withStyle(ChatFormatting.GRAY)));

        tooltip.add(Component.literal("    ").append(
            Component.translatable("block.create_stats.kinetic_flywheel.goggle.reserve",
                String.format("%,.0f", reserve), String.format("%,.0f", capacity()))
                .withStyle(ChatFormatting.AQUA)));

        Component modeText = switch (mode) {
            case DISCHARGING -> Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.discharging")
                .withStyle(ChatFormatting.GOLD);
            case CHARGING -> isToppedOff()
                ? Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.topped_off")
                    .withStyle(ChatFormatting.DARK_GREEN)
                : Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.charging")
                    .withStyle(ChatFormatting.GREEN);
            case IDLE -> isOverstressHalted()
                ? Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.halted")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                : Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.idle")
                    .withStyle(ChatFormatting.DARK_GRAY);
        };
        tooltip.add(Component.literal("    ").append(modeText));

        // Gear readout (set in-world on the rear face) with the live SU each gear implies, so the
        // non-linear cost is visible at a glance. The active side is highlighted.
        boolean charging = mode == Mode.CHARGING;
        boolean discharging = mode == Mode.DISCHARGING;
        tooltip.add(Component.literal("    ").append(
            Component.translatable("block.create_stats.kinetic_flywheel.goggle.charge_gear",
                getChargeGearIndex() + 1, String.format("%.0f", getChargeFullLoadStress()))
                .withStyle(charging ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
        tooltip.add(Component.literal("    ").append(
            Component.translatable("block.create_stats.kinetic_flywheel.goggle.discharge_gear",
                getDischargeGearIndex() + 1, String.format("%.0f", getDischargeMaxCapacity()))
                .withStyle(discharging ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)));

        if (mode == Mode.CHARGING && !isToppedOff()) {
            float power = getChargeFullLoadStress();
            float eta = power > 0f ? (capacity() - reserve) / power : 0f;
            tooltip.add(Component.literal("    ").append(
                Component.translatable("block.create_stats.kinetic_flywheel.goggle.charge_eta",
                    String.format("%,.0f", power), String.format("%.0f", eta))
                    .withStyle(ChatFormatting.GREEN)));
        }

        if (mode == Mode.DISCHARGING) {
            tooltip.add(Component.literal("    ").append(
                Component.translatable("block.create_stats.kinetic_flywheel.goggle.output",
                    String.format("%,.0f", getOutputStress()), String.format("%,.0f", output()))
                    .withStyle(ChatFormatting.YELLOW)));
            tooltip.add(Component.literal("    ").append(
                Component.translatable("block.create_stats.kinetic_flywheel.goggle.sustain",
                    String.format("%.0f", getEstimatedSustainSeconds()))
                    .withStyle(ChatFormatting.DARK_AQUA)));
        }

        if (isLowReserve()) {
            tooltip.add(Component.literal("    ").append(
                Component.translatable("block.create_stats.kinetic_flywheel.goggle.low_reserve")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        }
        return true;
    }
}
