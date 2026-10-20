package com.createstats.ground;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.zip.CRC32;

public final class GroundFeedPlanner {
    public static final int REFRESH_TICKS = 200;

    private final long[] sent = new long[GroundCodec.Section.values().length];
    private long lastFull = Long.MIN_VALUE;

    public GroundFeedPlanner() {
        reset();
    }

    public void reset() {
        Arrays.fill(this.sent, -1L);
        this.lastFull = Long.MIN_VALUE;
    }

    public byte[] next(GroundSnapshot s, long gameTime) {
        final boolean full = this.lastFull == Long.MIN_VALUE || gameTime - this.lastFull >= REFRESH_TICKS
                || gameTime < this.lastFull;
        final Map<GroundCodec.Section, byte[]> out = new EnumMap<>(GroundCodec.Section.class);
        for (final GroundCodec.Section sec : GroundCodec.Section.values()) {
            final byte[] bytes = GroundCodec.section(s, sec);
            if (sec.fast()) {
                out.put(sec, bytes);
                continue;
            }
            final long crc = crc(bytes);
            if (full || crc != this.sent[sec.ordinal()]) {
                out.put(sec, bytes);
                this.sent[sec.ordinal()] = crc;
            }
        }
        if (full) {
            this.lastFull = gameTime;
        }
        return GroundCodec.join(out);
    }

    static long crc(byte[] bytes) {
        final CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }
}
