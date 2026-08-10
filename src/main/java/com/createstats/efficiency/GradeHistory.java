package com.createstats.efficiency;

import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import com.createstats.data.TimeSeriesStore;

public class GradeHistory
{
    private static final int HISTORY_CAPACITY = 1080;
    private static final int HISTORY_INTERVAL = 200;
    private final TimeSeriesStore scoreHistory;
    private String sessionStartGrade;
    private float sessionStartScore;
    private long sessionStartTick;
    private String currentGrade;
    private float currentScore;
    private String bestGrade;
    private float bestScore;
    private String worstGrade;
    private float worstScore;
    private EfficiencyScore latestScore;
    private EfficiencyGrader.ODEState odeState;
    
    public GradeHistory() {
        this.sessionStartGrade = null;
        this.sessionStartScore = 0.0f;
        this.sessionStartTick = 0L;
        this.currentGrade = null;
        this.currentScore = 0.0f;
        this.bestGrade = null;
        this.bestScore = Float.MIN_VALUE;
        this.worstGrade = null;
        this.worstScore = Float.MAX_VALUE;
        this.latestScore = null;
        this.odeState = new EfficiencyGrader.ODEState();
        (this.scoreHistory = new TimeSeriesStore(1080)).setSampleIntervalTicks(200);
    }
    
    public void recordScore(EfficiencyScore score, long gameTick) {
        final float composite = score.compositeScore();
        final String grade = score.grade();
        this.scoreHistory.addSample(composite, gameTick);
        this.currentGrade = grade;
        this.currentScore = composite;
        this.latestScore = score;
        if (this.bestGrade == null || composite > this.bestScore) {
            this.bestScore = composite;
            this.bestGrade = grade;
        }
        if (this.worstGrade == null || composite < this.worstScore) {
            this.worstScore = composite;
            this.worstGrade = grade;
        }
        if (this.sessionStartTick == 0L) {
            this.sessionStartTick = gameTick;
            this.sessionStartGrade = grade;
            this.sessionStartScore = composite;
        }
    }
    
    public void detectNewSession(long currentTick) {
        if (currentTick <= 0L) {
            return;
        }
        final long lastTick = this.scoreHistory.getLastSampleTick();
        if (lastTick == 0L) {
            return;
        }
        final long gap = currentTick - lastTick;
        if (gap > 12000L) {
            this.sessionStartGrade = this.currentGrade;
            this.sessionStartScore = this.currentScore;
            this.sessionStartTick = currentTick;
        }
    }
    
    public float[] getSparklineData(int maxPoints) {
        return this.scoreHistory.getDataWindow(maxPoints);
    }
    
    @Nullable
    public String getSessionChangeText() {
        if (this.sessionStartGrade == null || this.currentGrade == null) {
            return null;
        }
        if ("?".equals(this.sessionStartGrade)) {
            return null;
        }
        if (this.sessionStartGrade.equals(this.currentGrade)) {
            return null;
        }
        return this.sessionStartGrade + " -> " + this.currentGrade;
    }
    
    public boolean hasImproved() {
        return this.currentScore > this.sessionStartScore;
    }
    
    @Nullable
    public String getCurrentGrade() {
        return this.currentGrade;
    }
    
    public float getCurrentScore() {
        return this.currentScore;
    }
    
    @Nullable
    public String getBestGrade() {
        return this.bestGrade;
    }
    
    public float getBestScore() {
        return this.bestScore;
    }
    
    @Nullable
    public EfficiencyScore getLatestScore() {
        return this.latestScore;
    }
    
    public EfficiencyGrader.ODEState getODEState() {
        return this.odeState;
    }
    
    public CompoundTag save() {
        return this.savePreview(this.scoreHistory.getSampleCount());
    }

    public CompoundTag savePreview(int maxSamples) {
        final CompoundTag tag = new CompoundTag();
        tag.put("ScoreHistory", (Tag)this.scoreHistory.savePreview(maxSamples));
        if (this.sessionStartGrade != null) {
            tag.putString("SessionStartGrade", this.sessionStartGrade);
        }
        tag.putFloat("SessionStartScore", this.sessionStartScore);
        tag.putLong("SessionStartTick", this.sessionStartTick);
        if (this.currentGrade != null) {
            tag.putString("CurrentGrade", this.currentGrade);
        }
        tag.putFloat("CurrentScore", this.currentScore);
        if (this.bestGrade != null) {
            tag.putString("BestGrade", this.bestGrade);
        }
        if (this.bestScore != Float.MIN_VALUE) {
            tag.putFloat("BestScore", this.bestScore);
        }
        if (this.worstGrade != null) {
            tag.putString("WorstGrade", this.worstGrade);
        }
        if (this.worstScore != Float.MAX_VALUE) {
            tag.putFloat("WorstScore", this.worstScore);
        }
        if (this.latestScore != null) {
            tag.put("LatestScore", (Tag)this.latestScore.save());
        }
        tag.put("ODEState", (Tag)this.odeState.save());
        return tag;
    }
    
    public static GradeHistory load(CompoundTag tag) {
        final GradeHistory h = new GradeHistory();
        if (tag.contains("ScoreHistory")) {
            final TimeSeriesStore loaded = TimeSeriesStore.load(tag.getCompound("ScoreHistory"));
            final float[] data2;
            final float[] data = data2 = loaded.getData();
            for (float v : data2) {
                h.scoreHistory.addSampleDirect(v);
            }
        }
        if (tag.contains("SessionStartGrade")) {
            h.sessionStartGrade = tag.getString("SessionStartGrade");
        }
        h.sessionStartScore = tag.getFloat("SessionStartScore");
        h.sessionStartTick = tag.getLong("SessionStartTick");
        if (tag.contains("CurrentGrade")) {
            h.currentGrade = tag.getString("CurrentGrade");
        }
        h.currentScore = tag.getFloat("CurrentScore");
        if (tag.contains("BestGrade")) {
            h.bestGrade = tag.getString("BestGrade");
        }
        if (tag.contains("BestScore")) {
            h.bestScore = tag.getFloat("BestScore");
        }
        if (tag.contains("WorstGrade")) {
            h.worstGrade = tag.getString("WorstGrade");
        }
        if (tag.contains("WorstScore")) {
            h.worstScore = tag.getFloat("WorstScore");
        }
        if (tag.contains("LatestScore")) {
            h.latestScore = EfficiencyScore.load(tag.getCompound("LatestScore"));
        }
        if (tag.contains("ODEState")) {
            h.odeState = EfficiencyGrader.ODEState.load(tag.getCompound("ODEState"));
        }
        return h;
    }
}
