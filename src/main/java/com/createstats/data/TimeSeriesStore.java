package com.createstats.data;

import com.createstats.ModConfiguration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import java.util.Arrays;
import com.createstats.prediction.PredictionConfig;
import com.createstats.util.RingBuffer;

public class TimeSeriesStore
{
    public static final int DEFAULT_PANEL_CAPACITY = 600;
    public static final int DEFAULT_RECORDER_CAPACITY = 1800;
    public static final int DEFAULT_SAMPLE_INTERVAL_TICKS = 20;
    public static final int DEFAULT_SYNC_PREVIEW_SAMPLES = 180;
    private final RingBuffer buffer;
    private MetricType metricType;
    private long lastSampleTick;
    private long oldestSampleTick;
    private int sampleIntervalTicks;
    private PredictionConfig predictionConfig;
    
    public TimeSeriesStore(int capacity) {
        this.predictionConfig = new PredictionConfig();
        this.buffer = new RingBuffer(capacity);
        this.metricType = MetricType.GENERIC;
        this.lastSampleTick = 0L;
        this.oldestSampleTick = 0L;
        this.sampleIntervalTicks = 20;
    }

    private static TimeSeriesStore createConfiguredStore(int capacity, int sampleIntervalTicks) {
        final TimeSeriesStore store = new TimeSeriesStore(capacity);
        store.sampleIntervalTicks = normalizeSampleInterval(sampleIntervalTicks);
        return store;
    }

    private static int normalizeSampleInterval(int ticks) {
        return Math.max(1, ticks);
    }
    
    public static TimeSeriesStore forPanel() {
        return createConfiguredStore((int)ModConfiguration.COMMON.panelBufferSize.get(), (int)ModConfiguration.COMMON.sampleIntervalTicks.get());
    }
    
    public static TimeSeriesStore forRecorder() {
        return createConfiguredStore((int)ModConfiguration.COMMON.recorderBufferSize.get(), (int)ModConfiguration.COMMON.sampleIntervalTicks.get());
    }
    
    public void addSample(float value, long gameTick) {
        if (gameTick - this.lastSampleTick >= this.sampleIntervalTicks || this.lastSampleTick == 0L) {
            if (this.buffer.size() == 0) {
                this.oldestSampleTick = gameTick;
            }
            else if (this.buffer.isFull()) {
                this.oldestSampleTick += this.sampleIntervalTicks;
            }
            this.buffer.add(value);
            this.lastSampleTick = gameTick;
        }
    }
    
    public void addSampleDirect(float value) {
        this.buffer.add(value);
    }
    
    public RingBuffer getBuffer() {
        return this.buffer;
    }
    
    public MetricType getMetricType() {
        return this.metricType;
    }
    
    public void setMetricType(MetricType type) {
        this.metricType = type;
    }
    
    public int getSampleIntervalTicks() {
        return this.sampleIntervalTicks;
    }
    
    public void setSampleIntervalTicks(int ticks) {
        this.sampleIntervalTicks = normalizeSampleInterval(ticks);
    }
    
    public PredictionConfig getPredictionConfig() {
        return this.predictionConfig;
    }
    
    public void setPredictionConfig(PredictionConfig config) {
        this.predictionConfig = config;
    }
    
    public int getRecentDataHash() {
        int hash = this.buffer.size();
        for (int count = Math.min(8, this.buffer.size()), i = 0; i < count; ++i) {
            hash = 31 * hash + Float.floatToIntBits(this.buffer.get(i));
        }
        return hash;
    }
    
    public float getLatestValue() {
        return this.buffer.latest();
    }
    
    public float getMin() {
        return this.buffer.min();
    }
    
    public float getMax() {
        return this.buffer.max();
    }
    
    public float getAverage() {
        return this.buffer.average();
    }
    
    public int getSampleCount() {
        return this.buffer.size();
    }
    
    public long getLastSampleTick() {
        return this.lastSampleTick;
    }
    
    public long getOldestSampleTick() {
        return this.oldestSampleTick;
    }
    
    public boolean hasValidTimestamps() {
        return this.oldestSampleTick > 0L && this.lastSampleTick > this.oldestSampleTick;
    }
    
    public long getActualWindowTicks() {
        if (this.oldestSampleTick > 0L && this.lastSampleTick > this.oldestSampleTick) {
            return this.lastSampleTick - this.oldestSampleTick;
        }
        return (this.buffer.size() - 1) * (long)this.sampleIntervalTicks;
    }
    
    public float[] getData() {
        return this.buffer.toArray();
    }
    
    public float[] getDataWindow(int maxSamples) {
        final float[] all = this.buffer.toArray();
        if (all.length <= maxSamples) {
            return all;
        }
        final float[] window = new float[maxSamples];
        System.arraycopy(all, all.length - maxSamples, window, 0, maxSamples);
        return window;
    }

    public int getSampleCountForSeconds(int timeScaleSeconds) {
        if (timeScaleSeconds <= 0 || timeScaleSeconds == Integer.MAX_VALUE) {
            return this.buffer.size();
        }
        final int interval = Math.max(1, this.sampleIntervalTicks);
        final int samplesToShow = (int)Math.ceil(timeScaleSeconds * 20.0 / interval);
        return Math.min(Math.max(1, samplesToShow), this.buffer.size());
    }
    
    public float[] getDataForTimeScale(int timeScaleSeconds) {
        return this.getDataWindow(this.getSampleCountForSeconds(timeScaleSeconds));
    }
    
    public float getStdDev() {
        return this.getStdDevWindow(0);
    }
    
    public float getRateOfChange() {
        if (this.buffer.size() < 2) {
            return 0.0f;
        }
        final float latest = this.buffer.get(0);
        final float prev = this.buffer.get(1);
        final float intervalSec = this.sampleIntervalTicks / 20.0f;
        return (latest - prev) / intervalSec;
    }
    
    public float getAvgRateOfChange(int samples) {
        if (this.buffer.size() < 2) {
            return 0.0f;
        }
        final int n = Math.min(samples, this.buffer.size());
        final float oldest = this.buffer.get(n - 1);
        final float newest = this.buffer.get(0);
        final float totalTimeSec = (n - 1) * this.sampleIntervalTicks / 20.0f;
        if (totalTimeSec < 0.001f) {
            return 0.0f;
        }
        return (newest - oldest) / totalTimeSec;
    }
    
    public float getMedian() {
        return this.getMedianWindow(0);
    }
    
    public float getPercentile(float percentile) {
        return this.getPercentileWindow(percentile, 0);
    }
    
    public int getMinIndex() {
        if (this.buffer.size() == 0) {
            return -1;
        }
        final float[] data = this.buffer.toArray();
        int minIdx = 0;
        for (int i = 1; i < data.length; ++i) {
            if (data[i] < data[minIdx]) {
                minIdx = i;
            }
        }
        return minIdx;
    }
    
    public int getMaxIndex() {
        if (this.buffer.size() == 0) {
            return -1;
        }
        final float[] data = this.buffer.toArray();
        int maxIdx = 0;
        for (int i = 1; i < data.length; ++i) {
            if (data[i] > data[maxIdx]) {
                maxIdx = i;
            }
        }
        return maxIdx;
    }
    
    public String getTimeAgo(int sampleIndex) {
        final int samplesAgo = this.buffer.size() - 1 - sampleIndex;
        final int secondsAgo = samplesAgo * this.sampleIntervalTicks / 20;
        if (secondsAgo < 60) {
            return secondsAgo + "s ago";
        }
        if (secondsAgo < 3600) {
            return secondsAgo / 60 + "m " + secondsAgo % 60 + "s ago";
        }
        return secondsAgo / 3600 + "h " + secondsAgo % 3600 / 60 + "m ago";
    }
    
    public int getDurationSeconds() {
        return Math.max(0, this.buffer.size() - 1) * this.sampleIntervalTicks / 20;
    }
    
    private float[] getWindowArray(int windowSamples) {
        if (windowSamples <= 0 || windowSamples >= this.buffer.size()) {
            return this.buffer.toArray();
        }
        return this.getDataWindow(windowSamples);
    }
    
    public float getMinWindow(int windowSamples) {
        final float[] d = this.getWindowArray(windowSamples);
        if (d.length == 0) {
            return 0.0f;
        }
        float m = d[0];
        for (int i = 1; i < d.length; ++i) {
            if (d[i] < m) {
                m = d[i];
            }
        }
        return m;
    }
    
    public float getMaxWindow(int windowSamples) {
        final float[] d = this.getWindowArray(windowSamples);
        if (d.length == 0) {
            return 0.0f;
        }
        float m = d[0];
        for (int i = 1; i < d.length; ++i) {
            if (d[i] > m) {
                m = d[i];
            }
        }
        return m;
    }
    
    public float getAverageWindow(int windowSamples) {
        final float[] d = this.getWindowArray(windowSamples);
        if (d.length == 0) {
            return 0.0f;
        }
        float sum = 0.0f;
        for (float v : d) {
            sum += v;
        }
        return sum / d.length;
    }
    
    public float getStdDevWindow(int windowSamples) {
        final float[] d = this.getWindowArray(windowSamples);
        if (d.length < 2) {
            return 0.0f;
        }
        float avg = 0.0f;
        for (float v : d) {
            avg += v;
        }
        avg /= d.length;
        float sumSq = 0.0f;
        for (float v2 : d) {
            final float diff = v2 - avg;
            sumSq += diff * diff;
        }
        return (float)Math.sqrt(sumSq / d.length);
    }
    
    public float getMedianWindow(int windowSamples) {
        final float[] d = this.getWindowArray(windowSamples);
        if (d.length == 0) {
            return 0.0f;
        }
        final float[] sorted = d.clone();
        Arrays.sort(sorted);
        final int mid = sorted.length / 2;
        return (sorted.length % 2 == 0) ? ((sorted[mid - 1] + sorted[mid]) / 2.0f) : sorted[mid];
    }
    
    public float getPercentileWindow(float percentile, int windowSamples) {
        final float[] d = this.getWindowArray(windowSamples);
        if (d.length == 0) {
            return 0.0f;
        }
        final float[] sorted = d.clone();
        Arrays.sort(sorted);
        final float idx = percentile / 100.0f * (sorted.length - 1);
        final int lower = (int)Math.floor(idx);
        final int upper = Math.min(lower + 1, sorted.length - 1);
        final float frac = idx - lower;
        return sorted[lower] * (1.0f - frac) + sorted[upper] * frac;
    }
    
    public void clear() {
        this.buffer.clear();
        this.lastSampleTick = 0L;
        this.oldestSampleTick = 0L;
    }
    
    public CompoundTag save() {
        return this.saveLimited(this.buffer.size());
    }

    public CompoundTag savePreview(int maxSamples) {
        return this.saveLimited(maxSamples);
    }

    public TimeSeriesStore copyPreview(int maxSamples) {
        return load(this.savePreview(maxSamples));
    }

    private CompoundTag saveLimited(int maxSamples) {
        final CompoundTag tag = new CompoundTag();
        final int sampleCount = Math.max(0, Math.min(this.buffer.size(), maxSamples));
        tag.put("Buffer", (Tag)this.buffer.saveLimited(sampleCount));
        tag.putString("MetricType", this.metricType.name());
        tag.putLong("LastSampleTick", sampleCount > 0 ? this.lastSampleTick : 0L);
        tag.putLong("OldestSampleTick", this.resolveOldestTickForSampleCount(sampleCount));
        tag.putInt("SampleInterval", this.sampleIntervalTicks);
        tag.put("PredictionConfig", (Tag)this.predictionConfig.save());
        return tag;
    }
    
    public static TimeSeriesStore load(CompoundTag tag) {
        final RingBuffer buffer = RingBuffer.load(tag.getCompound("Buffer"));
        final TimeSeriesStore store = new TimeSeriesStore(buffer.capacity());
        final float[] array;
        final float[] data = array = buffer.toArray();
        for (float v : array) {
            store.buffer.add(v);
        }
        try {
            store.metricType = MetricType.valueOf(tag.getString("MetricType"));
        }
        catch (IllegalArgumentException e) {
            store.metricType = MetricType.GENERIC;
        }
        store.lastSampleTick = tag.getLong("LastSampleTick");
        store.oldestSampleTick = (tag.contains("OldestSampleTick") ? tag.getLong("OldestSampleTick") : 0L);
        store.sampleIntervalTicks = normalizeSampleInterval(tag.contains("SampleInterval") ? tag.getInt("SampleInterval") : 20);
        if (tag.contains("PredictionConfig")) {
            store.predictionConfig = PredictionConfig.load(tag.getCompound("PredictionConfig"));
        }
        store.normalizeLoadedTimestamps();
        return store;
    }
    
    public void writeToBuf(FriendlyByteBuf buf) {
        this.buffer.writeToBuf(buf);
        buf.writeEnum((Enum)this.metricType);
        buf.writeVarInt(this.sampleIntervalTicks);
        buf.writeLong(this.lastSampleTick);
        buf.writeLong(this.oldestSampleTick);
        this.predictionConfig.writeToBuf(buf);
    }
    
    public static TimeSeriesStore readFromBuf(FriendlyByteBuf buf) {
        final RingBuffer buffer = RingBuffer.readFromBuf(buf);
        final TimeSeriesStore store = new TimeSeriesStore(buffer.capacity());
        final float[] array;
        final float[] data = array = buffer.toArray();
        for (float v : array) {
            store.buffer.add(v);
        }
        store.metricType = (MetricType)buf.readEnum((Class)MetricType.class);
        store.sampleIntervalTicks = normalizeSampleInterval(buf.readVarInt());
        store.lastSampleTick = buf.readLong();
        store.oldestSampleTick = buf.readLong();
        store.predictionConfig = PredictionConfig.readFromBuf(buf);
        store.normalizeLoadedTimestamps();
        return store;
    }

    private long resolveOldestTickForSampleCount(int sampleCount) {
        if (sampleCount <= 0) {
            return 0L;
        }
        if (sampleCount >= this.buffer.size()) {
            return this.oldestSampleTick;
        }
        if (this.lastSampleTick <= 0L) {
            return 0L;
        }
        final long derivedOldest = this.lastSampleTick - (long)(sampleCount - 1) * (long)this.sampleIntervalTicks;
        if (this.oldestSampleTick > 0L) {
            return Math.max(this.oldestSampleTick, derivedOldest);
        }
        return Math.max(0L, derivedOldest);
    }

    private void normalizeLoadedTimestamps() {
        final int sampleCount = this.buffer.size();
        if (sampleCount <= 0) {
            this.lastSampleTick = 0L;
            this.oldestSampleTick = 0L;
            return;
        }
        if (this.lastSampleTick < 0L) {
            this.lastSampleTick = 0L;
        }
        if (this.oldestSampleTick < 0L) {
            this.oldestSampleTick = 0L;
        }
        final long minimumOldest = this.lastSampleTick > 0L ? Math.max(0L, this.lastSampleTick - (long)(sampleCount - 1) * (long)this.sampleIntervalTicks) : 0L;
        if (this.oldestSampleTick == 0L) {
            this.oldestSampleTick = minimumOldest;
        }
        else if (this.lastSampleTick > 0L) {
            this.oldestSampleTick = Math.min(this.oldestSampleTick, this.lastSampleTick);
            this.oldestSampleTick = Math.max(this.oldestSampleTick, minimumOldest);
        }
    }
}
