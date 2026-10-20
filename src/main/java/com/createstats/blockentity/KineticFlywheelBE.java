package com.createstats.blockentity;

import com.createstats.block.FlywheelValueBoxTransform;
import com.createstats.block.KineticFlywheelBlock;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.lang.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class KineticFlywheelBE extends GeneratingKineticBlockEntity {
    // one model: SU-second battery, flat round trip efficiency. rpm never follows the reserve

    public static final float OUTPUT_RPM = 256f;
    // measured against capacity WITHOUT this bank, or it engages, fixes it, releases, repeat every tick
    private static final float ASSIST_ENGAGE = 0.98f;
    private static final float ASSIST_RELEASE = 0.85f;

    private static final float CHARGE_ENGAGE = ASSIST_RELEASE;
    private static final float CHARGE_RELEASE = ASSIST_ENGAGE;

    private static final float RECOVERY_FRACTION = 0.15f;

    private static final int OVERSTRESS_SETTLE_TICKS = 10;

    private static final int SPEED_SETTLE_TICKS = 10;

    private static final float DERATE_LOADING = 0.90f;
    private static final float DERATE_DROP_ABOVE = 0.97f;
    private static final float DERATE_RAISE_BELOW = 0.75f;
    private static final int DERATE_RAISE_TICKS = 60;

    private static final float MIN_HOLD_RPM = 4f;

    private static final int DEFAULT_HOLD_RPM = 32;

    private static final float CHARGE_HEADROOM_MARGIN = 0.9f;

    private static final float CHARGE_SLEW = 0.025f;

    private static final int VISUAL_SETTLE_TICKS = 5;

    public static final int MAX_TIER = 3;

    private static final float OUTPUT_BASE = 32_768f;

    private static final float RESERVE_BASE = 3_276_800f;

    private static final float CHARGE_BASE = RESERVE_BASE / 180f;

    private static final float EFFICIENCY = 0.90f;

    private static final float FRICTION_FLOOR = 0.005f;

    private static final int BANK_REFRESH_TICKS = 20;

    private static final float MIN_RESERVE = 250f;

    private static final float[] CAPACITY_TIER_MULT = {1f, 2f, 3f, 4f};

    private static final float[] CHARGE_GEAR_MULT = {1f, 2f, 3f};

    private static final float[] DISCHARGE_GEAR_MULT = {1f, 2f, 3f};
    private static final float EXTERNAL_EPS = 1f;

    private static final int OVERSTRESS_RETRY_TICKS = 40;

    private static final int OVERSTRESS_MAX_RETRY_TICKS = 400;
    private static final int TRIP_RESET_TICKS = 200;

    private static final float ARM_COST_FRACTION = 0.05f;

    private static final float STRESS_PUSH_EPS = 0.5f;

    public static float ratedOutputRpm() {
        return Math.min(OUTPUT_RPM, AllConfigs.server().kinetics.maxRotationSpeed.get());
    }

    public enum Mode { IDLE, CHARGING, DISCHARGING }

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

    private float reserve;
    private int spinSign = 1;
    private Mode mode = Mode.IDLE;

    private boolean depleted;

    private boolean parked;

    private int tier = 0;

    private float outputSpeed;
    private int comparatorBucket = -1;
    private int sendCooldown;
    private float chargeLoad;
    private float chargeDraw;
    private float lastPushedCapacity = Float.NaN;
    private Mode pendingVisual;
    private int pendingVisualTicks;
    private long lastSyncSignature = Long.MIN_VALUE;

    private final List<KineticFlywheelBE> bankGroup = new java.util.ArrayList<>();
    private int bankRefreshTicks;
    private Long bankNetworkKey;
    private float learnedRpm;
    private int sourcingTicks;
    private boolean assistEngaged;
    private int bankSize = 1;
    private float bankAverageCharge;
    private float bankTotalOutput;
    private boolean chargeEngaged;
    private boolean waitingForSurplus;
    private int overstressHoldTicks;
    private int overstressBackoff;
    private boolean overstressDisarmed;
    private float lastPushedStress = Float.NaN;
    private float chargeCoeff;
    private float lastOutputStress;
    private boolean redstoneHeld;
    private float speedFactor = 1f;
    private int speedSettleTicks;
    private int factorStableTicks;
    private float learnedLoadPerRpm;
    private float backupCapacity;
    private float commandedRpm;
    private int commandTicks;

    private static final BehaviourType<GearScrollOptionBehaviour<Gear>> CHARGE_GEAR_TYPE = new BehaviourType<>();
    private static final BehaviourType<GearScrollOptionBehaviour<Gear>> DISCHARGE_GEAR_TYPE = new BehaviourType<>();
    private static final BehaviourType<FlywheelRpmScrollBehaviour> HOLD_RPM_TYPE = new BehaviourType<>();
    private GearScrollOptionBehaviour<Gear> chargeGear;
    private GearScrollOptionBehaviour<Gear> dischargeGear;
    private FlywheelRpmScrollBehaviour holdRpmDial;

    private KineticFlywheelSettings settings = new KineticFlywheelSettings();

    private float clientAngle;
    private float prevClientAngle;
    private float rotorRpm;
    private boolean rotorPrimed;

    private final FlywheelVoice.Cues cues = new FlywheelVoice.Cues();

    public KineticFlywheelBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        chargeGear = new GearScrollOptionBehaviour<>(Gear.class,
            Component.translatable("block.create_stats.kinetic_flywheel.charge_gear"),
            this, new FlywheelValueBoxTransform(FlywheelValueBoxTransform.Slot.CHARGE_GEAR),
            CHARGE_GEAR_TYPE, "ChargeGear", 0);
        chargeGear.withCallback(i -> onGearChanged());
        behaviours.add(chargeGear);

        dischargeGear = new GearScrollOptionBehaviour<>(Gear.class,
            Component.translatable("block.create_stats.kinetic_flywheel.discharge_gear"),
            this, new FlywheelValueBoxTransform(FlywheelValueBoxTransform.Slot.DISCHARGE_GEAR),
            DISCHARGE_GEAR_TYPE, "DischargeGear", 1);
        dischargeGear.withCallback(i -> onGearChanged());
        behaviours.add(dischargeGear);

        holdRpmDial = new FlywheelRpmScrollBehaviour(
            Component.translatable("block.create_stats.kinetic_flywheel.hold_rpm"),
            this, new FlywheelValueBoxTransform(FlywheelValueBoxTransform.Slot.HOLD_RPM),
            HOLD_RPM_TYPE, "HoldRpm", 2);
        holdRpmDial.between(0, (int) ratedOutputRpm());
        holdRpmDial.withFormatter(v -> v == 0 ? "Auto" : v + " RPM");
        holdRpmDial.withCallback(v -> onHoldRpmChanged());
        behaviours.add(holdRpmDial);
    }

    private void onHoldRpmChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            pushCapacity(true);
            sendData();
        }
    }

    public int getHoldRpmSetting() {
        return holdRpmDial == null ? 0 : Mth.clamp(holdRpmDial.getValue(), 0, (int) ratedOutputRpm());
    }

    private void onGearChanged() {
        pushStress(true);
        pushCapacity(true);
    }

    public int getChargeGearIndex() {
        return chargeGear == null ? 0 : Mth.clamp(chargeGear.getValue(), 0, CHARGE_GEAR_MULT.length - 1);
    }

    public int getDischargeGearIndex() {
        return dischargeGear == null ? 0 : Mth.clamp(dischargeGear.getValue(), 0, DISCHARGE_GEAR_MULT.length - 1);
    }

    public float getChargeFullLoadStress() {
        return chargePower();
    }

    public float getChargeDraw() {
        return mode == Mode.CHARGING ? chargeDraw : 0f;
    }

    public float getDischargeMaxCapacity() {
        return output();
    }

    public int getTier() {
        return tier;
    }

    public int getMaxTier() {
        return MAX_TIER;
    }

    public int getFlywheelCount() {
        return tier + 1;
    }

    public float capacity() {
        return RESERVE_BASE * CAPACITY_TIER_MULT[Mth.clamp(tier, 0, MAX_TIER)];
    }

    public float output() {
        return OUTPUT_BASE * CAPACITY_TIER_MULT[Mth.clamp(tier, 0, MAX_TIER)]
            * DISCHARGE_GEAR_MULT[getDischargeGearIndex()];
    }

    private float ratedOutput() {
        return OUTPUT_BASE * CAPACITY_TIER_MULT[Mth.clamp(tier, 0, MAX_TIER)];
    }

    private float chargePower() {
        return CHARGE_BASE * CAPACITY_TIER_MULT[Mth.clamp(tier, 0, MAX_TIER)]
            * CHARGE_GEAR_MULT[getChargeGearIndex()];
    }

    public void setTier(int newTier) {
        newTier = Mth.clamp(newTier, 0, MAX_TIER);
        if (newTier == tier) {
            return;
        }
        tier = newTier;
        reserve = Mth.clamp(reserve, 0f, capacity());

        pushCapacity(true);
        pushStress(true);
        setChanged();
        sendData();
    }

    public float getStoredRpm() {
        return spinSign * FlywheelVoice.rotorRpm(getChargePercent() / 100f);
    }

    public float getOutputRpm() {
        return outputSpeed;
    }

    private float holdTarget() {
        int dial = getHoldRpmSetting();
        float want = dial > 0 ? dial : (learnedRpm > 0f ? learnedRpm : DEFAULT_HOLD_RPM);
        return Math.min(want, ratedOutputRpm());
    }

    private float targetRpm() {
        return commandedRpm > 0f ? Math.min(commandedRpm, ratedOutputRpm()) : holdTarget();
    }

    private float heldSpeed(float target, float factor) {
        return FlywheelBankPlan.heldSpeed(target, ratedOutputRpm(), factor, hasForeignSource(),
            Math.abs(getTheoreticalSpeed()), EXTERNAL_EPS);
    }

    public float holdRpm() {
        return heldSpeed(holdTarget(), speedFactor);
    }

    public float getChargePercent() {
        return Mth.clamp(reserve / capacity(), 0f, 1f) * 100f;
    }

    public Long getNetworkKey() {
        if (!hasNetwork()) {
            return null;
        }
        var network = getOrCreateNetwork();
        return network == null ? null : network.id;
    }

    public float effectiveOutputRpm() {
        return spinSign * heldSpeed(targetRpm(), speedFactor);
    }

    public void commandOutputRpm(float rpm) {
        float clamped = Mth.clamp(rpm, 0f, ratedOutputRpm());
        commandTicks = 30;
        if (clamped != commandedRpm) {
            commandedRpm = clamped;
            setChanged();
            sendData();
        }
    }

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

    public int getBankSize() {
        return bankSize;
    }

    public float getBankAverageCharge() {
        return bankAverageCharge;
    }

    public float getBankTotalOutput() {
        return bankTotalOutput;
    }

    public int getModeIndex() {
        return mode.ordinal();
    }

    public float getOutputStress() {
        return mode == Mode.DISCHARGING ? lastOutputStress : 0f;
    }

    public boolean isOverstressHalted() {
        return overstressHoldTicks > 0;
    }

    public float getReArmSeconds() {
        return overstressHoldTicks > 0 ? overstressHoldTicks / 20f : 0f;
    }

    public static float getReArmCostPercent() {
        return ARM_COST_FRACTION * 100f;
    }

    public int getComparatorOutput() {
        return Mth.clamp(Math.round(getChargePercent() / 100f * 15f), 0, 15);
    }

    public float getChargeLoad() {
        return mode == Mode.CHARGING ? chargeLoad : 0f;
    }

    public boolean isToppedOff() {
        return mode == Mode.CHARGING && reserve >= capacity() - 1f;
    }

    public boolean isLowReserve() {
        return getChargePercent() < settings.getLowReservePct();
    }

    public KineticFlywheelSettings getSettings() {
        return this.settings;
    }

    public void setSettings(KineticFlywheelSettings newSettings) {
        if (newSettings == null) {
            return;
        }
        this.settings = newSettings;
        setChanged();
        sendData();
    }

    public boolean isDischargeArmed() {
        return settings.isDischargeArmed();
    }

    public void forcePark() {
        parked = true;
        if (mode == Mode.DISCHARGING) {
            mode = Mode.IDLE;
        }
        assistEngaged = false;
        chargeEngaged = false;
        setChanged();
        sendData();
    }

    public void release() {
        parked = false;
        overstressHoldTicks = 0;
        overstressBackoff = 0;
        overstressDisarmed = false;
        assistEngaged = false;
        setChanged();
        sendData();
    }

    public boolean isParked() {
        return parked;
    }

    public boolean isDepleted() {
        return depleted;
    }

    public static float getRecoveryPercent() {
        return RECOVERY_FRACTION * 100f;
    }

    public boolean isWaitingForSurplus() {
        return waitingForSurplus;
    }

    public boolean isRedstoneHeld() {
        return redstoneHeld;
    }

    public boolean isDerated() {
        return mode == Mode.DISCHARGING && speedFactor < 0.999f;
    }

    public float getHumActivity() {
        float chargeFraction = mode == Mode.CHARGING ? chargeDraw / Math.max(chargePower(), 1f) : 0f;
        float outputFraction = mode == Mode.DISCHARGING ? lastOutputStress / Math.max(output(), 1f) : 0f;
        return FlywheelVoice.activity(mode == Mode.CHARGING, mode == Mode.DISCHARGING,
            chargeFraction, outputFraction);
    }

    private void payReArmCost() {
        reserve = Math.max(0f, reserve - ARM_COST_FRACTION * capacity());
    }

    public float getEstimatedSustainSeconds() {
        if (mode != Mode.DISCHARGING) {
            return 0f;
        }
        float usable = reserve - MIN_RESERVE;
        if (usable <= 0f) {
            return 0f;
        }
        return FlywheelBankPlan.sustainSeconds(usable, lastOutputStress, EFFICIENCY, friction());
    }

    @Override
    public float getGeneratedSpeed() {
        return outputSpeed;
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (mode != Mode.DISCHARGING) {
            this.lastCapacityProvided = 0f;
            return 0f;
        }

        final float coeff = output() / Math.max(Math.abs(outputSpeed), 1f);
        this.lastCapacityProvided = coeff;
        return coeff;
    }

    @Override
    public float calculateStressApplied() {
        float impact = mode == Mode.CHARGING ? chargeCoeff : 0f;
        this.lastStressApplied = impact;
        return impact;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            clientTick();
            return;
        }
        serverTick();
    }

    private void clientTick() {
        prevClientAngle = clientAngle;
        boolean ponder = com.createstats.client.FlywheelClientFx.isPonder(level);
        float target = ponder ? Math.abs(getSpeed()) : FlywheelVoice.rotorRpm(getChargePercent() / 100f);
        if (!rotorPrimed) {
            rotorRpm = target;
            rotorPrimed = true;
        }

        rotorRpm += (target - rotorRpm) * 0.05f;
        float direction = ponder ? Math.signum(getSpeed()) : spinSign;
        clientAngle += direction * rotorRpm * 3f / 10f;

        if (Math.abs(clientAngle) > 3600f) {
            float wrap = 360f * (int) (clientAngle / 360f);
            clientAngle -= wrap;
            prevClientAngle -= wrap;
        }
        if (!ponder) {
            com.createstats.client.FlywheelClientFx.tick(this);
        }
    }

    public FlywheelVoice.Cues getCues() {
        return cues;
    }

    public float getRotorFraction() {
        return rotorRpm / FlywheelVoice.STORED_TOP_RPM;
    }

    private void serverTick() {
        Mode previousMode = mode;
        boolean wasHeld = redstoneHeld;

        redstoneHeld = level.hasNeighborSignal(worldPosition);
        if (commandTicks > 0 && --commandTicks == 0 && commandedRpm != 0f) {
            commandedRpm = 0f;
            sendData();
        }

        boolean foreign = hasForeignSource();
        if (foreign) {
            float ext = getTheoreticalSpeed();
            if (Math.abs(ext) > EXTERNAL_EPS) {
                learnedRpm = Math.abs(ext);
                spinSign = ext >= 0 ? 1 : -1;

                learnedLoadPerRpm = loadWithoutBanks() / learnedRpm;
                backupCapacity = backupCapacityOfGroup();
            }
        }
        updateDepletedLatch();
        updateAssistLatch(foreign);
        updateChargeLatch(foreign);
        mode = decideMode(foreign);
        if (mode != Mode.DISCHARGING || foreign) {
            speedFactor = 1f;
        } else if (previousMode != Mode.DISCHARGING) {
            speedFactor = backupStartFactor();
            factorStableTicks = 0;
        }

        waitingForSurplus = foreign && !assistEngaged && !chargeEngaged && !redstoneHeld
            && !parked && overstressHoldTicks == 0 && getChargePercent() < 99f;
        sourcingTicks = mode == Mode.DISCHARGING ? sourcingTicks + 1 : 0;
        if (speedSettleTicks > 0) {
            speedSettleTicks--;
        }
        factorStableTicks++;

        if (mode == Mode.DISCHARGING && !foreign) {
            steerBackupSpeed();
        }
        switch (mode) {
            case CHARGING -> tickCharge();
            case DISCHARGING -> tickDischarge(foreign);
            case IDLE -> tickIdle();
        }

        float desired = mode == Mode.DISCHARGING ? effectiveOutputRpm() : 0f;
        if (desired != outputSpeed) {
            outputSpeed = desired;
            updateGeneratedRotation();
            speedSettleTicks = SPEED_SETTLE_TICKS;
        }

        updateVisualState();

        bankGroup();

        boolean forced = mode != previousMode || redstoneHeld != wasHeld;
        pushStress(forced);

        pushCapacity(forced);
        refreshComparator();
        emitParticles();
        maybeSync(forced);
    }

    private void steerBackupSpeed() {
        List<KineticFlywheelBE> group = bankGroup();
        if (speedSettleTicks > 0 || outputSpeed == 0f || !isSpeedLeader(group)) {
            return;
        }
        if (Math.abs(getTheoreticalSpeed()) <= EXTERNAL_EPS) {
            return;
        }
        float next = FlywheelBankPlan.steerFactor(speedFactor, sourcingCapacity(group), stress,
            isOverStressed(), factorStableTicks, DERATE_LOADING, DERATE_DROP_ABOVE, DERATE_RAISE_BELOW,
            DERATE_RAISE_TICKS);
        next = Mth.clamp(next, minimumFactor(), 1f);

        if (Math.abs(next - speedFactor) * targetRpm() < 0.25f) {
            return;
        }
        applyGroupFactor(group, next);
    }

    private void applyGroupFactor(List<KineticFlywheelBE> group, float factor) {
        List<KineticFlywheelBE> moved = new java.util.ArrayList<>();
        for (KineticFlywheelBE bank : group) {
            if (bank.mode != Mode.DISCHARGING || bank.isRemoved()) {
                continue;
            }
            bank.speedFactor = factor;
            bank.factorStableTicks = 0;
            float speed = bank.effectiveOutputRpm();
            if (speed != bank.outputSpeed) {
                bank.outputSpeed = speed;
                moved.add(bank);
            }
        }

        moved.sort(java.util.Comparator.comparing(KineticFlywheelBE::hasSource));
        for (KineticFlywheelBE bank : moved) {
            bank.updateGeneratedRotation();
            bank.speedSettleTicks = SPEED_SETTLE_TICKS;
        }
    }

    private boolean isSpeedLeader(List<KineticFlywheelBE> group) {
        for (KineticFlywheelBE bank : group) {
            if (bank.mode == Mode.DISCHARGING && !bank.isRemoved()) {
                return bank == this;
            }
        }
        return true;
    }

    private static float sourcingCapacity(List<KineticFlywheelBE> group) {
        float total = 0f;
        for (KineticFlywheelBE bank : group) {
            if (bank.mode == Mode.DISCHARGING && !bank.isRemoved()) {
                total += bank.output();
            }
        }
        return total;
    }

    private boolean canTakeOver() {
        return settings.isDischargeArmed() && !depleted && reserve > MIN_RESERVE && !parked && !redstoneHeld
            && overstressHoldTicks == 0;
    }

    private float backupCapacityOfGroup() {
        float total = 0f;
        for (KineticFlywheelBE bank : bankGroup()) {
            if (bank.canTakeOver()) {
                total += bank.output();
            }
        }
        return total;
    }

    private float minimumFactor() {
        float target = targetRpm();
        return target > 0f ? Math.min(1f, MIN_HOLD_RPM / target) : 1f;
    }

    private float backupStartFactor() {
        float capacity = backupCapacity > 0f ? backupCapacity : output();
        float loadAtTarget = learnedLoadPerRpm * targetRpm();
        float start = FlywheelBankPlan.startFactor(capacity, loadAtTarget, DERATE_LOADING, DERATE_DROP_ABOVE);
        return Mth.clamp(start, minimumFactor(), 1f);
    }

    private boolean slowingCanCarry() {
        float loadAtFull = stress / Math.max(speedFactor, 1e-3f);
        float fit = FlywheelBankPlan.fitFactor(sourcingCapacity(bankGroup()), loadAtFull, DERATE_LOADING);
        return fit >= minimumFactor();
    }

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

    private List<KineticFlywheelBE> bankGroup() {
        if (level == null || level.isClientSide) {
            return bankGroup;
        }
        Long key = getNetworkKey();
        boolean networkChanged = !java.util.Objects.equals(key, bankNetworkKey);
        if (--bankRefreshTicks > 0 && !networkChanged) {
            return bankGroup;
        }
        bankRefreshTicks = BANK_REFRESH_TICKS;
        bankNetworkKey = key;
        bankGroup.clear();
        bankGroup.add(this);
        if (hasNetwork()) {
            var network = getOrCreateNetwork();
            if (network != null) {
                for (var member : network.members.keySet()) {
                    if (member != this && member instanceof KineticFlywheelBE sibling && !sibling.isRemoved()) {
                        bankGroup.add(sibling);
                    }
                }
            }
        }
        bankGroup.sort(java.util.Comparator.comparingLong(be -> be.getBlockPos().asLong()));
        refreshBankSummary();
        return bankGroup;
    }

    private void refreshBankSummary() {
        int size = 0;
        float charge = 0f;
        float out = 0f;
        for (KineticFlywheelBE bank : bankGroup) {
            size++;
            charge += bank.getChargePercent();
            out += bank.output();
        }
        bankSize = Math.max(1, size);
        bankAverageCharge = size > 0 ? charge / size : 0f;
        bankTotalOutput = out;
    }

    public boolean hasForeignSource() {
        if (level == null || level.isClientSide || !hasNetwork()) {
            return false;
        }
        var network = getOrCreateNetwork();
        if (network == null) {
            return false;
        }
        for (var source : network.sources.keySet()) {
            if (!(source instanceof KineticFlywheelBE) && source.isSource() && !source.isRemoved()) {
                return true;
            }
        }
        return false;
    }

    // create has no partial service so "out of power" = whole network stalls. this props it up
    private void updateAssistLatch(boolean foreign) {
        if (!foreign) {
            assistEngaged = false;
            return;
        }

        assistEngaged = FlywheelBankPlan.assistLatch(assistEngaged, loadWithoutBanks(),
            capacityWithoutBanks(), ASSIST_ENGAGE, ASSIST_RELEASE);
    }

    private void updateChargeLatch(boolean foreign) {
        if (!foreign || assistEngaged || parked || redstoneHeld || !hasNetwork()) {
            chargeEngaged = false;
            return;
        }

        chargeEngaged = FlywheelBankPlan.chargeLatch(chargeEngaged, loadWithoutBanks(),
            capacityWithoutBanks(), CHARGE_ENGAGE, CHARGE_RELEASE);
    }

    private void updateDepletedLatch() {
        if (depleted) {
            if (reserve >= RECOVERY_FRACTION * capacity()) {
                depleted = false;
            }
        } else if (reserve <= MIN_RESERVE) {
            depleted = true;
        }
    }

    private float capacityWithoutBanks() {
        float banks = 0f;
        for (KineticFlywheelBE bank : bankGroup()) {
            if (bank.isSource()) {
                banks += bank.output();
            }
        }
        return Math.max(0f, capacity - banks);
    }

    public boolean isAssisting() {
        return assistEngaged && mode == Mode.DISCHARGING;
    }

    private float shareOfDemand(float demand) {
        List<KineticFlywheelBE> group = bankGroup();
        if (group.size() <= 1) {
            return Math.min(Math.max(0f, demand), output());
        }

        int n = group.size();
        float[] weight = new float[n];
        float[] cap = new float[n];
        int me = -1;
        for (int i = 0; i < n; i++) {
            KineticFlywheelBE bank = group.get(i);
            if (bank == this) {
                me = i;
            }
            boolean sourcing = bank.mode == Mode.DISCHARGING && bank.reserve > MIN_RESERVE;

            weight[i] = sourcing ? Math.max(0f, bank.reserve - MIN_RESERVE) : 0f;
            cap[i] = sourcing ? bank.output() : 0f;
        }
        if (me < 0 || cap[me] <= 0f) {
            return 0f;
        }
        return FlywheelBankPlan.share(demand, weight, cap)[me];
    }

    private float friction() {
        return FRICTION_FLOOR * ratedOutput();
    }

    private float availableChargePower() {
        if (!hasNetwork()) {
            return 0f;
        }

        float myRoom = Math.max(0f, capacity() - reserve);
        float totalRoom = 0f;
        for (KineticFlywheelBE bank : bankGroup()) {
            if (bank.mode == Mode.CHARGING) {
                totalRoom += Math.max(0f, bank.capacity() - bank.reserve);
            }
        }

        float surplus = Math.max(0f, (capacityWithoutBanks() - loadWithoutBanks()) * CHARGE_HEADROOM_MARGIN);

        return FlywheelBankPlan.chargeShare(surplus, myRoom, totalRoom);
    }

    private float loadWithoutBanks() {
        float bankLoad = 0f;
        for (KineticFlywheelBE bank : bankGroup()) {
            float pushed = Float.isNaN(bank.lastPushedStress) ? bank.chargeCoeff : bank.lastPushedStress;
            bankLoad += pushed * Math.abs(bank.getTheoreticalSpeed());
        }
        return Math.max(0f, stress - bankLoad);
    }

    private void pushCapacity(boolean force) {
        if (level == null || level.isClientSide || !hasNetwork()) {
            return;
        }
        float current = calculateAddedStressCapacity();
        if (force || Float.isNaN(lastPushedCapacity)
                || Math.abs(current - lastPushedCapacity) > STRESS_PUSH_EPS) {
            notifyStressCapacityChange(current);
            lastPushedCapacity = current;
        }
    }

    private Mode decideMode(boolean foreign) {
        if (redstoneHeld || parked || overstressHoldTicks > 0) {
            return Mode.IDLE;
        }

        boolean canSource = settings.isDischargeArmed()
            && !depleted
            && reserve > MIN_RESERVE
            && heldSpeed(targetRpm(), 1f) >= MIN_HOLD_RPM;
        if (!foreign) {
            return canSource ? Mode.DISCHARGING : Mode.IDLE;
        }
        if (assistEngaged) {
            return canSource ? Mode.DISCHARGING : Mode.IDLE;
        }
        return chargeEngaged ? Mode.CHARGING : Mode.IDLE;
    }

    private void tickCharge() {
        float ext = Math.abs(getTheoreticalSpeed());
        if (ext <= EXTERNAL_EPS) {
            clearChargeState();
            return;
        }

        reserve = Math.max(0f, reserve - friction() / 20f);
        float room = capacity() - reserve;
        float full = chargePower();
        if (room <= 0f) {
            clearChargeState();
            return;
        }
        float target = Math.min(full, availableChargePower());

        chargeDraw = chargeDraw < target ? Math.min(target, chargeDraw + full * CHARGE_SLEW) : target;
        float add = Math.min(chargeDraw / 20f, room);
        chargeDraw = add * 20f;
        reserve = Mth.clamp(reserve + add, 0f, capacity());
        chargeCoeff = chargeDraw / ext;
        chargeLoad = Mth.clamp(chargeDraw / Math.max(full, 1e-4f), 0f, 1f);
    }

    private void tickDischarge(boolean foreign) {
        if (reserve <= MIN_RESERVE || !settings.isDischargeArmed()) {
            depleted = depleted || reserve <= MIN_RESERVE;
            mode = Mode.IDLE;
            lastOutputStress = 0f;
            clearChargeState();
            return;
        }
        if (sourcingTicks > OVERSTRESS_SETTLE_TICKS && speedSettleTicks == 0 && isOverStressed()
                && (foreign || !slowingCanCarry())) {
            mode = Mode.IDLE;
            assistEngaged = false;
            overstressDisarmed = true;
            lastOutputStress = 0f;
            overstressBackoff = overstressBackoff <= 0
                ? OVERSTRESS_RETRY_TICKS
                : Math.min(overstressBackoff * 2, OVERSTRESS_MAX_RETRY_TICKS);
            overstressHoldTicks = overstressBackoff;
            overstressBurst();
            return;
        }
        if (sourcingTicks > TRIP_RESET_TICKS) {
            overstressBackoff = 0;
        }

        float shortfall = Math.max(0f, stress - capacityWithoutBanks());
        float delivered = shareOfDemand(shortfall);
        lastOutputStress = delivered;

        reserve -= FlywheelBankPlan.drainPerSecond(delivered, EFFICIENCY, friction()) / 20f;
        reserve = Math.max(0f, reserve);
    }

    private void tickIdle() {
        clearChargeState();
        lastOutputStress = 0f;
        if (overstressHoldTicks > 0) {
            overstressHoldTicks--;
            if (overstressHoldTicks == 0 && overstressDisarmed) {
                overstressDisarmed = false;
                if (settings.isDischargeArmed() && !depleted && reserve > MIN_RESERVE) {
                    payReArmCost();
                }
            }
        }
        if (reserve > 0f) {
            reserve = Math.max(0f, reserve - friction() / 20f);
        }
    }

    private void clearChargeState() {
        chargeDraw = 0f;
        chargeCoeff = 0f;
        chargeLoad = 0f;
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
        if (state.getValue(KineticFlywheelBlock.VISUAL_STATE) == desired) {
            pendingVisual = null;
            return;
        }
        if (pendingVisual != mode) {
            pendingVisual = mode;
            pendingVisualTicks = VISUAL_SETTLE_TICKS;
            return;
        }
        if (--pendingVisualTicks > 0) {
            return;
        }
        pendingVisual = null;
        level.setBlock(worldPosition, state.setValue(KineticFlywheelBlock.VISUAL_STATE, desired),
            Block.UPDATE_CLIENTS);
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
        long signature = syncSignature();
        if (!force && signature == lastSyncSignature) {
            return;
        }
        lastSyncSignature = signature;
        sendData();
        setChanged();
    }

    private long syncSignature() {
        long gaugeStep = Math.round(getChargePercent() * 2f);
        long powerStep = Math.round((lastOutputStress + chargeDraw) / 64f);
        long halted = overstressHoldTicks > 0 ? 1L : 0L;
        long assisting = assistEngaged ? 1L : 0L;
        long spent = depleted ? 1L : 0L;
        long waiting = waitingForSurplus ? 1L : 0L;
        long speedStep = Math.round(outputSpeed * 4f);
        return (redstoneHeld ? 1L : 0L) << 61
            | (parked ? 1L : 0L) << 60
            | spent << 59
            | waiting << 58
            | (long) mode.ordinal() << 56
            | halted << 55
            | assisting << 54
            | (long) (bankSize & 0xFF) << 46
            | (powerStep & 0x3FFFF) << 28
            | (speedStep & 0xFFF) << 16
            | (gaugeStep & 0xFFFF);
    }

    private void emitParticles() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        if (mode == Mode.DISCHARGING && lastOutputStress > 0f && level.getGameTime() % 6 == 0) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                1, 0.34, 0.34, 0.34, 0.0);
        }
    }

    private void overstressBurst() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + 0.5;
        double cz = worldPosition.getZ() + 0.5;
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, cx, cy + 0.2, cz, 12, 0.26, 0.26, 0.26, 0.02);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, cx, cy, cz, 8, 0.3, 0.3, 0.3, 0.05);
    }

    public float getRenderAngle(float partialTicks) {
        return Mth.lerp(partialTicks, prevClientAngle, clientAngle);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("Reserve", reserve);
        compound.putInt("SpinSign", spinSign);
        compound.putInt("Mode", mode.ordinal());
        compound.putFloat("OutputSpeed", outputSpeed);
        compound.putInt("Tier", tier);

        if (clientPacket) {
            compound.putFloat("CommandedRpm", commandedRpm);
        }
        compound.putFloat("LearnedRpm", learnedRpm);
        compound.putFloat("LearnedLoadPerRpm", learnedLoadPerRpm);

        compound.putFloat("SpeedFactor", speedFactor);
        compound.putBoolean("Depleted", depleted);
        compound.putBoolean("Parked", parked);
        compound.put("Settings", settings.save());
        if (clientPacket) {
            compound.putBoolean("RedstoneHeld", redstoneHeld);

            compound.putFloat("OutputStress", lastOutputStress);
            compound.putFloat("ChargeDraw", chargeDraw);
            compound.putInt("HoldTicks", overstressHoldTicks);
            compound.putBoolean("Assisting", assistEngaged);
            compound.putBoolean("WaitingForSurplus", waitingForSurplus);
            compound.putInt("BankSize", bankSize);
            compound.putFloat("BankCharge", bankAverageCharge);
            compound.putFloat("BankOutput", bankTotalOutput);
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);

        reserve = compound.contains("Reserve") ? compound.getFloat("Reserve") : compound.getFloat("StoredEnergy");
        spinSign = compound.getInt("SpinSign");
        if (spinSign == 0) {
            spinSign = 1;
        }
        int modeIdx = compound.getInt("Mode");
        Mode[] modes = Mode.values();
        mode = modeIdx >= 0 && modeIdx < modes.length ? modes[modeIdx] : Mode.IDLE;

        outputSpeed = compound.getFloat("OutputSpeed");
        tier = Mth.clamp(compound.getInt("Tier"), 0, MAX_TIER);

        commandedRpm = clientPacket ? compound.getFloat("CommandedRpm") : 0f;
        learnedRpm = compound.getFloat("LearnedRpm");
        learnedLoadPerRpm = Math.max(0f, compound.getFloat("LearnedLoadPerRpm"));
        speedFactor = compound.contains("SpeedFactor") ? Mth.clamp(compound.getFloat("SpeedFactor"), 0f, 1f) : 1f;
        if (!(speedFactor > 0f)) {
            speedFactor = 1f;
        }
        depleted = compound.getBoolean("Depleted");
        parked = compound.getBoolean("Parked");
        if (compound.contains("Settings")) {
            settings = KineticFlywheelSettings.load(compound.getCompound("Settings"));
        }
        if (clientPacket) {
            redstoneHeld = compound.getBoolean("RedstoneHeld");
            lastOutputStress = compound.getFloat("OutputStress");
            chargeDraw = compound.getFloat("ChargeDraw");
            overstressHoldTicks = compound.getInt("HoldTicks");
            assistEngaged = compound.getBoolean("Assisting");
            waitingForSurplus = compound.getBoolean("WaitingForSurplus");
            bankSize = Math.max(1, compound.getInt("BankSize"));
            bankAverageCharge = compound.getFloat("BankCharge");
            bankTotalOutput = compound.getFloat("BankOutput");
        }
    }

    private static String duration(float seconds) {
        if (seconds >= 43_200f) {
            return "12h+";
        }
        if (seconds >= 3600f) {
            int h = (int) (seconds / 3600f);
            return String.format("%dh %02dm", h, (int) ((seconds - h * 3600f) / 60f));
        }
        if (seconds >= 60f) {
            int m = (int) (seconds / 60f);
            return String.format("%dm %02ds", m, (int) (seconds - m * 60f));
        }
        return String.format("%.0fs", Math.max(0f, seconds));
    }

    private static Component line(String key, ChatFormatting style, Object... args) {
        return Component.literal("    ").append(
            Component.translatable("block.create_stats.kinetic_flywheel.goggle." + key, args)
                .withStyle(style));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        float chargePct = getChargePercent();

        tooltip.add(Component.literal(" ").append(
            Component.translatable("block.create_stats.kinetic_flywheel.goggle.title")
                .withStyle(ChatFormatting.GRAY)));

        if (isPlayerSneaking) {
            tooltip.add(line("stage", ChatFormatting.LIGHT_PURPLE, getFlywheelCount(), MAX_TIER + 1));
            if (getTier() < MAX_TIER) {
                tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.kinetic_flywheel.goggle.upgrade_hint")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
            }
        }

        float low = settings.getLowReservePct();
        ChatFormatting barColor = chargePct < low ? ChatFormatting.RED
            : chargePct < low * 2f ? ChatFormatting.GOLD
            : ChatFormatting.GREEN;
        int filled = Mth.clamp(Math.round(chargePct / 10f), 0, 10);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? '\u25A0' : '\u25A1');
        }
        tooltip.add(Component.literal("    ")
            .append(Component.literal(bar.toString()).withStyle(barColor))
            .append(Component.literal(String.format(" %.0f%%", chargePct)).withStyle(ChatFormatting.GRAY)));

        if (isPlayerSneaking) {
            tooltip.add(line("reserve", ChatFormatting.AQUA,
                String.format("%,.0f", reserve), String.format("%,.0f", capacity())));
        }

        Component modeText = switch (mode) {
            case DISCHARGING -> assistEngaged
                ? Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.assisting")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                : Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.discharging")
                    .withStyle(ChatFormatting.GOLD);
            case CHARGING -> isToppedOff()
                ? Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.topped_off")
                    .withStyle(ChatFormatting.DARK_GREEN)
                : Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.charging")
                    .withStyle(ChatFormatting.GREEN);

            case IDLE -> redstoneHeld
                ? Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.held")
                    .withStyle(ChatFormatting.AQUA)
                : isOverstressHalted()
                ? Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.halted")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                : parked
                ? Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.parked")
                    .withStyle(ChatFormatting.AQUA)
                : depleted
                ? Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.depleted")
                    .withStyle(ChatFormatting.RED)
                : waitingForSurplus
                ? Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.waiting")
                    .withStyle(ChatFormatting.GOLD)
                : Component.translatable("block.create_stats.kinetic_flywheel.goggle.mode.idle")
                    .withStyle(ChatFormatting.DARK_GRAY);
        };
        tooltip.add(Component.literal("    ").append(modeText));

        if (isDerated()) {
            tooltip.add(line("hold_derated", ChatFormatting.GOLD,
                String.format("%.0f", Math.abs(outputSpeed)), String.format("%.0f", targetRpm())));
        } else if (isPlayerSneaking || mode == Mode.DISCHARGING) {
            final int dial = getHoldRpmSetting();
            final float shown = mode == Mode.DISCHARGING ? Math.abs(outputSpeed) : holdTarget();
            tooltip.add(line(dial > 0 ? "hold_set" : "hold_auto",
                mode == Mode.DISCHARGING ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY,
                String.format("%.0f", shown)));
        }

        switch (mode) {
            case DISCHARGING -> {
                if (isPlayerSneaking) {
                    tooltip.add(line("output", ChatFormatting.YELLOW,
                        String.format("%,.0f", lastOutputStress), String.format("%,.0f", output())));
                }

                float loadPct = output() > 0f ? lastOutputStress / output() * 100f : 0f;
                tooltip.add(line("sustain", ChatFormatting.DARK_AQUA,
                    duration(getEstimatedSustainSeconds()), String.format("%.0f", loadPct)));
            }
            case CHARGING -> {
                if (!isToppedOff()) {
                    if (chargeDraw <= 1f) {
                        tooltip.add(line("charge_starved", ChatFormatting.GOLD));
                    } else {
                        if (isPlayerSneaking) {
                            tooltip.add(line("charge_draw", ChatFormatting.GREEN,
                                String.format("%,.0f", chargeDraw),
                                String.format("%,.0f", getChargeFullLoadStress())));
                        }

                        tooltip.add(line("charge_full", ChatFormatting.GREEN,
                            duration((capacity() - reserve) / chargeDraw)));
                    }
                }
            }
            case IDLE -> {
                if (redstoneHeld) {
                    tooltip.add(line("held_hint", ChatFormatting.DARK_AQUA));
                } else if (isOverstressHalted()) {
                    tooltip.add(line("rearm", ChatFormatting.RED,
                        String.format("%.0f", getReArmSeconds()),
                        String.format("%.0f", getReArmCostPercent())));
                } else if (depleted) {
                    tooltip.add(line("recovering", ChatFormatting.GOLD,
                        String.format("%.0f", getRecoveryPercent())));
                } else if (waitingForSurplus) {
                    tooltip.add(line("charge_starved", ChatFormatting.GOLD));
                } else if (reserve > MIN_RESERVE) {
                    float coast = friction() > 0f ? (reserve - MIN_RESERVE) / friction() : 0f;
                    tooltip.add(line("coasting", ChatFormatting.DARK_AQUA, duration(coast)));
                }
            }
        }

        if (isPlayerSneaking) {
            tooltip.add(line("charge_gear", mode == Mode.CHARGING ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY,
                getChargeGearIndex() + 1, String.format("%,.0f", getChargeFullLoadStress())));
            tooltip.add(line("discharge_gear", mode == Mode.DISCHARGING ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY,
                getDischargeGearIndex() + 1, String.format("%,.0f", output())));
        }

        if (bankSize > 1) {
            tooltip.add(line("bank", ChatFormatting.BLUE, bankSize,
                String.format("%,.0f", bankTotalOutput), String.format("%.0f", bankAverageCharge)));
        }

        if (isLowReserve()) {
            tooltip.add(Component.literal("    ").append(
                Component.translatable("block.create_stats.kinetic_flywheel.goggle.low_reserve")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        }
        if (!isPlayerSneaking) {
            tooltip.add(com.createstats.power.FeGoggle.more());
        }
        return true;
    }
}
