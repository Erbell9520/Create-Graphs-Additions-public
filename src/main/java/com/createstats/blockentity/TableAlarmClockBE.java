package com.createstats.blockentity;

import com.createstats.block.TableAlarmClockBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class TableAlarmClockBE extends BlockEntity {

    private static final long MAX_TIMER_TICKS = 20L * 60L * 60L * 6L;
    private static final long DEFAULT_TIMER_TICKS = 20L * 60L * 5L;
    private static final long RING_INTERVAL_TICKS = 40L;
    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final int DEFAULT_ALARM_MINUTE = 23 * 60 + 11;
    private Mode mode;
    private boolean stopwatchRunning;
    private long stopwatchAccumulatedTicks;
    private long stopwatchStartedAtTick;
    private long timerDurationTicks;
    private long timerRemainingTicks;
    private long timerTargetTick;
    private boolean timerRunning;
    private int alarmMinuteOfDay;
    private boolean alarmEnabled;
    private long lastAlarmDay;
    private boolean ringing;
    private RingSource ringSource;
    private long nextRingSoundTick;

    public TableAlarmClockBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.mode = Mode.CLOCK;
        this.stopwatchRunning = false;
        this.stopwatchAccumulatedTicks = 0L;
        this.stopwatchStartedAtTick = 0L;
        this.timerDurationTicks = DEFAULT_TIMER_TICKS;
        this.timerRemainingTicks = DEFAULT_TIMER_TICKS;
        this.timerTargetTick = 0L;
        this.timerRunning = false;
        this.alarmMinuteOfDay = DEFAULT_ALARM_MINUTE;
        this.alarmEnabled = true;
        this.lastAlarmDay = Long.MIN_VALUE;
        this.ringing = false;
        this.ringSource = RingSource.NONE;
        this.nextRingSoundTick = 0L;
    }

    public void serverTick() {
        Level level = this.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        long gameTime = level.getGameTime();
        if (this.timerRunning && gameTime >= this.timerTargetTick) {
            this.timerRunning = false;
            this.timerRemainingTicks = 0L;
            this.timerTargetTick = 0L;
            this.startRinging(RingSource.TIMER);
        }
        if (this.alarmEnabled) {
            long dayTime = level.getDayTime();
            long day = dayTime / 24000L;
            int currentMinute = displayedMinuteOfDay(dayTime);
            if (currentMinute == this.alarmMinuteOfDay && day != this.lastAlarmDay) {
                this.lastAlarmDay = day;
                this.startRinging(RingSource.ALARM);
            }
        }
        if (this.ringing && gameTime >= this.nextRingSoundTick) {
            this.nextRingSoundTick = gameTime + RING_INTERVAL_TICKS;
            this.playRingSound();
        }
    }

    public Mode getMode() {
        return this.mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.CLOCK : mode;
        this.markStateChanged();
    }

    public boolean isStopwatchRunning() {
        return this.stopwatchRunning;
    }

    public long getCurrentStopwatchTicks() {
        long ticks = this.stopwatchAccumulatedTicks;
        if (this.stopwatchRunning && this.level != null) {
            ticks += Math.max(0L, this.level.getGameTime() - this.stopwatchStartedAtTick);
        }
        return Math.max(0L, ticks);
    }

    public void toggleStopwatch() {
        if (this.stopwatchRunning) {
            this.pauseStopwatch();
        } else {
            this.startStopwatch();
        }
    }

    public void resetStopwatch() {
        this.stopwatchRunning = false;
        this.stopwatchAccumulatedTicks = 0L;
        this.stopwatchStartedAtTick = 0L;
        this.markStateChanged();
    }

    public boolean isTimerRunning() {
        return this.timerRunning;
    }

    public long getTimerDurationTicks() {
        return this.timerDurationTicks;
    }

    public long getCurrentTimerRemainingTicks() {
        if (this.timerRunning && this.level != null) {
            return Math.max(0L, this.timerTargetTick - this.level.getGameTime());
        }
        return Math.max(0L, this.timerRemainingTicks);
    }

    public void adjustTimerBySeconds(int deltaSeconds) {
        long deltaTicks = deltaSeconds * 20L;
        long updatedTicks = clampTicks(this.getCurrentTimerRemainingTicks() + deltaTicks);
        this.timerDurationTicks = updatedTicks;
        this.timerRemainingTicks = updatedTicks;
        if (this.timerRunning) {
            if (updatedTicks <= 0L) {
                this.timerRunning = false;
                this.timerTargetTick = 0L;
            } else {
                this.timerTargetTick = this.currentGameTime() + updatedTicks;
            }
        }
        this.markStateChanged();
    }

    public void toggleTimerRunning() {
        if (this.timerRunning) {
            this.pauseTimer();
        } else {
            this.startTimer();
        }
    }

    public void resetTimer() {
        this.timerRunning = false;
        this.timerTargetTick = 0L;
        this.timerRemainingTicks = this.timerDurationTicks;
        this.markStateChanged();
    }

    public int getAlarmMinuteOfDay() {
        return this.alarmMinuteOfDay;
    }

    public boolean isAlarmEnabled() {
        return this.alarmEnabled;
    }

    public void toggleAlarmEnabled() {
        this.setAlarmEnabled(!this.alarmEnabled);
    }

    public void setAlarmEnabled(boolean enabled) {
        this.alarmEnabled = enabled;
        if (!enabled && this.ringing && this.ringSource == RingSource.ALARM) {
            this.ringing = false;
            this.ringSource = RingSource.NONE;
            this.nextRingSoundTick = 0L;
            this.syncActiveState();
            return;
        }
        this.markStateChanged();
    }

    public void adjustAlarmMinutes(int deltaMinutes) {
        this.alarmMinuteOfDay = Math.floorMod(this.alarmMinuteOfDay + deltaMinutes, MINUTES_PER_DAY);
        this.markStateChanged();
    }

    public boolean isRinging() {
        return this.ringing;
    }

    public RingSource getRingSource() {
        return this.ringSource;
    }

    public void silence() {
        if (!this.ringing) {
            return;
        }
        this.ringing = false;
        this.ringSource = RingSource.NONE;
        this.nextRingSoundTick = 0L;
        this.syncActiveState();
    }

    public CompoundTag saveClockState() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Mode", this.mode.name());
        tag.putBoolean("StopwatchRunning", this.stopwatchRunning);
        tag.putLong("StopwatchAccumulatedTicks", this.stopwatchAccumulatedTicks);
        tag.putLong("StopwatchStartedAtTick", this.stopwatchStartedAtTick);
        tag.putLong("TimerDurationTicks", this.timerDurationTicks);
        tag.putLong("TimerRemainingTicks", this.timerRemainingTicks);
        tag.putLong("TimerTargetTick", this.timerTargetTick);
        tag.putBoolean("TimerRunning", this.timerRunning);
        tag.putInt("AlarmMinuteOfDay", this.alarmMinuteOfDay);
        tag.putBoolean("AlarmEnabled", this.alarmEnabled);
        tag.putLong("LastAlarmDay", this.lastAlarmDay);
        tag.putBoolean("Ringing", this.ringing);
        tag.putString("RingSource", this.ringSource.name());
        tag.putLong("NextRingSoundTick", this.nextRingSoundTick);
        return tag;
    }

    public void applyClientState(CompoundTag tag) {
        this.loadClockState(tag);
        this.syncActiveState();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("ClockState", this.saveClockState());
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ClockState")) {
            this.loadClockState(tag.getCompound("ClockState"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.handleUpdateTag(tag, registries);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }

    private void startStopwatch() {
        if (this.stopwatchRunning) {
            return;
        }
        this.stopwatchRunning = true;
        this.stopwatchStartedAtTick = this.currentGameTime();
        this.markStateChanged();
    }

    private void pauseStopwatch() {
        if (!this.stopwatchRunning) {
            return;
        }
        this.stopwatchAccumulatedTicks = this.getCurrentStopwatchTicks();
        this.stopwatchRunning = false;
        this.stopwatchStartedAtTick = 0L;
        this.markStateChanged();
    }

    private void startTimer() {
        long remaining = this.getCurrentTimerRemainingTicks();
        if (remaining <= 0L) {
            remaining = this.timerDurationTicks;
        }
        if (remaining <= 0L) {
            return;
        }
        this.timerRunning = true;
        this.timerRemainingTicks = remaining;
        this.timerTargetTick = this.currentGameTime() + remaining;
        this.markStateChanged();
    }

    private void pauseTimer() {
        if (!this.timerRunning) {
            return;
        }
        this.timerRemainingTicks = this.getCurrentTimerRemainingTicks();
        this.timerRunning = false;
        this.timerTargetTick = 0L;
        this.markStateChanged();
    }

    private void startRinging(RingSource source) {
        this.ringing = true;
        this.ringSource = source == null ? RingSource.NONE : source;
        this.nextRingSoundTick = 0L;
        this.syncActiveState();
    }

    private void playRingSound() {
        Level level = this.getLevel();
        if (level == null) {
            return;
        }
        SoundEvent sound = com.createstats.registry.ModSounds.ALARM_RING.get();
        float pitch = this.ringSource == RingSource.TIMER ? 1.15f : 1.0f;
        level.playSound(null, this.worldPosition, sound, SoundSource.BLOCKS, 0.8f, pitch);
    }

    private void markStateChanged() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private void syncActiveState() {
        this.setChanged();
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        BlockState state = this.getBlockState();
        BlockState syncedState = state;
        if (state.hasProperty(TableAlarmClockBlock.ACTIVE) && state.getValue(TableAlarmClockBlock.ACTIVE) != this.ringing) {
            syncedState = state.setValue(TableAlarmClockBlock.ACTIVE, this.ringing);
            this.level.setBlock(this.worldPosition, syncedState, 3);
            this.level.updateNeighborsAt(this.worldPosition, syncedState.getBlock());
        }
        this.level.sendBlockUpdated(this.worldPosition, syncedState, syncedState, 3);
    }

    private void loadClockState(CompoundTag tag) {
        this.mode = Mode.byName(tag.getString("Mode"));
        this.stopwatchRunning = tag.getBoolean("StopwatchRunning");
        this.stopwatchAccumulatedTicks = Math.max(0L, tag.getLong("StopwatchAccumulatedTicks"));
        this.stopwatchStartedAtTick = tag.getLong("StopwatchStartedAtTick");
        this.timerDurationTicks = clampTicks(tag.contains("TimerDurationTicks") ? tag.getLong("TimerDurationTicks") : DEFAULT_TIMER_TICKS);
        this.timerRemainingTicks = clampTicks(tag.contains("TimerRemainingTicks") ? tag.getLong("TimerRemainingTicks") : this.timerDurationTicks);
        this.timerTargetTick = tag.getLong("TimerTargetTick");
        this.timerRunning = tag.getBoolean("TimerRunning");
        this.alarmMinuteOfDay = Math.floorMod(tag.contains("AlarmMinuteOfDay") ? tag.getInt("AlarmMinuteOfDay") : DEFAULT_ALARM_MINUTE, MINUTES_PER_DAY);
        this.alarmEnabled = tag.getBoolean("AlarmEnabled");
        this.lastAlarmDay = tag.contains("LastAlarmDay") ? tag.getLong("LastAlarmDay") : Long.MIN_VALUE;
        this.ringing = tag.getBoolean("Ringing");
        this.ringSource = RingSource.byName(tag.getString("RingSource"));
        this.nextRingSoundTick = tag.getLong("NextRingSoundTick");
        if (!this.timerRunning) {
            this.timerTargetTick = 0L;
        }
        if (!this.stopwatchRunning) {
            this.stopwatchStartedAtTick = 0L;
        }
        if (!this.ringing) {
            this.ringSource = RingSource.NONE;
            this.nextRingSoundTick = 0L;
        }
    }

    private long currentGameTime() {
        return this.level != null ? this.level.getGameTime() : 0L;
    }

    private static long clampTicks(long ticks) {
        return Math.max(0L, Math.min(MAX_TIMER_TICKS, ticks));
    }

    private static int displayedMinuteOfDay(long dayTime) {
        long timeOfDay = dayTime % 24000L;
        int hours = (int) ((timeOfDay / 1000L + 6L) % 24L);
        int minutes = (int) (timeOfDay % 1000L * 60L / 1000L);
        return hours * 60 + minutes;
    }

    public enum Mode {
        CLOCK("Clock"),
        CALENDAR("Calendar"),
        STOPWATCH("Stopwatch"),
        TIMER("Timer"),
        ALARM("Alarm"),
        REALTIME("Real Time");  // client-side only: displays real wall-clock time

        private final String displayName;

        Mode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public static Mode byName(String name) {
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return CLOCK;
        }
    }

    public enum RingSource {
        NONE("Idle"),
        TIMER("Timer"),
        ALARM("Alarm");

        private final String displayName;

        RingSource(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public static RingSource byName(String name) {
            for (RingSource source : values()) {
                if (source.name().equalsIgnoreCase(name)) {
                    return source;
                }
            }
            return NONE;
        }
    }
}