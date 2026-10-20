package com.createstats.display;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class InstrumentStyleOptions
{
    private final LinkedHashMap<String, Boolean> options;

    public InstrumentStyleOptions() {
        this.options = new LinkedHashMap<String, Boolean>();
    }

    public InstrumentStyleOptions copy() {
        final InstrumentStyleOptions copy = new InstrumentStyleOptions();
        copy.options.putAll(this.options);
        return copy;
    }

    public boolean isEmpty() {
        return this.options.isEmpty();
    }

    public boolean getOption(String key, boolean defaultValue) {
        return this.options.getOrDefault(key, defaultValue);
    }

    public void setOption(String key, boolean enabled) {
        if (key == null || key.isEmpty()) {
            return;
        }
        this.options.put(key, enabled);
    }

    public void clearOption(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        this.options.remove(key);
    }

    public Map<String, Boolean> asMap() {
        return Collections.unmodifiableMap(this.options);
    }

    public void replaceAll(Map<String, Boolean> values) {
        this.options.clear();
        if (values != null) {
            this.options.putAll(values);
        }
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Boolean> entry : this.options.entrySet()) {
            tag.putBoolean(entry.getKey(), entry.getValue());
        }
        return tag;
    }

    public static InstrumentStyleOptions load(CompoundTag tag) {
        final InstrumentStyleOptions options = new InstrumentStyleOptions();
        for (String key : tag.getAllKeys()) {
            options.options.put(key, tag.getBoolean(key));
        }
        return options;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.options.size());
        for (Map.Entry<String, Boolean> entry : this.options.entrySet()) {
            buf.writeUtf(entry.getKey(), 128);
            buf.writeBoolean(entry.getValue());
        }
    }

    public static InstrumentStyleOptions decode(FriendlyByteBuf buf) {
        final InstrumentStyleOptions options = new InstrumentStyleOptions();
        final int count = buf.readVarInt();
        for (int i = 0; i < count; ++i) {
            options.options.put(buf.readUtf(128), buf.readBoolean());
        }
        return options;
    }
}
