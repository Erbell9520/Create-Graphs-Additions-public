package com.createstats.util;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;

public class RingBuffer
{
    public static final int MAX_SERIALIZED_SAMPLES = 4096;
    private final float[] data;
    private int head;
    private int count;
    private float[] cachedArray;
    private boolean cacheDirty;
    
    public RingBuffer(int capacity) {
        this.cacheDirty = true;
        this.data = new float[Math.max(capacity, 1)];
        this.head = 0;
        this.count = 0;
    }
    
    public void add(float value) {
        this.data[this.head] = value;
        this.head = (this.head + 1) % this.data.length;
        if (this.count < this.data.length) {
            ++this.count;
        }
        this.cacheDirty = true;
    }
    
    public float get(int age) {
        if (age < 0 || age >= this.count) {
            return 0.0f;
        }
        final int index = ((this.head - 1 - age) % this.data.length + this.data.length) % this.data.length;
        return this.data[index];
    }
    
    public float latest() {
        return (this.count > 0) ? this.get(0) : 0.0f;
    }
    
    public int size() {
        return this.count;
    }
    
    public int capacity() {
        return this.data.length;
    }
    
    public boolean isEmpty() {
        return this.count == 0;
    }
    
    public boolean isFull() {
        return this.count == this.data.length;
    }
    
    public float[] toArray() {
        if (!this.cacheDirty && this.cachedArray != null && this.cachedArray.length == this.count) {
            return this.cachedArray;
        }
        final float[] result = new float[this.count];
        for (int i = 0; i < this.count; ++i) {
            result[i] = this.get(this.count - 1 - i);
        }
        this.cachedArray = result;
        this.cacheDirty = false;
        return result;
    }
    
    public float min() {
        if (this.count == 0) {
            return 0.0f;
        }
        float min = Float.MAX_VALUE;
        for (int i = 0; i < this.count; ++i) {
            min = Math.min(min, this.get(i));
        }
        return min;
    }
    
    public float max() {
        if (this.count == 0) {
            return 0.0f;
        }
        float max = -3.4028235E38f;
        for (int i = 0; i < this.count; ++i) {
            max = Math.max(max, this.get(i));
        }
        return max;
    }
    
    public float average() {
        if (this.count == 0) {
            return 0.0f;
        }
        float sum = 0.0f;
        for (int i = 0; i < this.count; ++i) {
            sum += this.get(i);
        }
        return sum / this.count;
    }
    
    public void clear() {
        this.head = 0;
        this.count = 0;
        this.cacheDirty = true;
        this.cachedArray = null;
    }
    
    public CompoundTag save() {
        return this.saveLimited(this.count);
    }

    public CompoundTag saveLimited(int maxSamples) {
        final CompoundTag tag = new CompoundTag();
        final int sampleCount = Math.max(0, Math.min(Math.min(this.count, maxSamples), MAX_SERIALIZED_SAMPLES));
        final int capacity = Math.max(1, Math.min(this.data.length, Math.max(1, sampleCount)));
        tag.putInt("Capacity", capacity);
        tag.putInt("Head", sampleCount % capacity);
        tag.putInt("Count", sampleCount);
        final ListTag list = new ListTag();
        final int start = Math.max(0, this.count - sampleCount);
        for (int i = start; i < this.count; ++i) {
            final int index = ((this.head - this.count + i) % this.data.length + this.data.length) % this.data.length;
            list.add(FloatTag.valueOf(this.data[index]));
        }
        tag.put("Data", (Tag)list);
        return tag;
    }
    
    public static RingBuffer load(CompoundTag tag) {
        final int capacity = Math.max(1, Math.min(MAX_SERIALIZED_SAMPLES, tag.getInt("Capacity")));
        final RingBuffer buffer = new RingBuffer(capacity);
        final ListTag list = tag.getList("Data", 5);
        final int start = Math.max(0, list.size() - capacity);
        for (int i = start; i < list.size(); ++i) {
            buffer.add(list.getFloat(i));
        }
        return buffer;
    }
    
    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeVarInt(this.data.length);
        buf.writeVarInt(this.count);
        final float[] array;
        final float[] arr = array = this.toArray();
        for (float v : array) {
            buf.writeFloat(v);
        }
    }
    
    public static RingBuffer readFromBuf(FriendlyByteBuf buf) {
        final int encodedCapacity = buf.readVarInt();
        final int encodedCount = buf.readVarInt();
        final int capacity = Math.max(1, Math.min(MAX_SERIALIZED_SAMPLES, encodedCapacity));
        final int keptSamples = Math.min(capacity, Math.min(encodedCount, MAX_SERIALIZED_SAMPLES));
        final int start = Math.max(0, encodedCount - keptSamples);
        final RingBuffer buffer = new RingBuffer(capacity);
        for (int i = 0; i < encodedCount; ++i) {
            final float value = buf.readFloat();
            if (i >= start) {
                buffer.add(value);
            }
        }
        return buffer;
    }
}
