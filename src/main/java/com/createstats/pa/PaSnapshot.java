package com.createstats.pa;

import java.util.ArrayList;
import java.util.List;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.data.MetricType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record PaSnapshot(BlockPos pos, PaSettings settings, List<Row> catalog, List<Sub> subs,
                         List<String> log, int severityOrdinal, boolean speaking, int queued,
                         int energy, int energyMax, int ratedDraw, boolean powered,
                         int importable, int circuitSize, List<SpeakerRow> speakers, Voice voice) {
    // peekQueue had no callers, the console could only say "11"
    public record Voice(List<String> onAir, int onAirWord, List<Waiting> queue, boolean held,
                        boolean starved, int onDemand) {
        public static final Voice NOTHING =
                new Voice(List.of(), -1, List.of(), false, false, 0);
    }

    public record Waiting(int severityOrdinal, List<String> words, int waitedTicks) {
        public AlarmSeverity severity() {
            final AlarmSeverity[] values = AlarmSeverity.values();
            return values[Math.floorMod(this.severityOrdinal, values.length)];
        }
    }

    public record SpeakerRow(BlockPos pos, String kind, boolean powered, boolean spoke,
                             int distance, int range) {
    }

    public record Row(String key, String parentKey, int depth, int kind, String title,
                      String subtitle, String subjectClip, String measureClip, int metricType,
                      float value, int flags, float threshold, int idNumber, int distance) {
        public MetricType type() {
            final MetricType[] values = MetricType.values();
            return values[Math.floorMod(this.metricType, values.length)];
        }

        public PaCatalog.RowKind rowKind() {
            final PaCatalog.RowKind[] values = PaCatalog.RowKind.values();
            return values[Math.floorMod(this.kind, values.length)];
        }

        public boolean has(int flag) {
            return (this.flags & flag) != 0;
        }

        public boolean bindable() {
            return this.rowKind() == PaCatalog.RowKind.TOPIC;
        }

        public String displayName() {
            return this.idNumber > 0 ? this.title + " " + this.idNumber : this.title;
        }

        static Row of(PaCatalog.Row row) {
            return new Row(row.key(), row.parentKey(), row.depth(), row.kind().ordinal(),
                    row.title(), row.subtitle(), row.subjectClip(), row.measureClip(),
                    row.type().ordinal(), row.value(), row.flags(), row.threshold(),
                    row.id() == null ? 0 : row.id().number(), row.distance());
        }
    }

    public record Sub(String key, String discipline, String verbosity, String severity,
                      String comparator, float threshold, float deadband, boolean enabled,
                      boolean tripped, boolean following) {
    }

    public AlarmSeverity severity() {
        final AlarmSeverity[] values = AlarmSeverity.values();
        return values[Math.floorMod(this.severityOrdinal, values.length)];
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeNbt(this.settings.save());

        buf.writeVarInt(this.catalog.size());
        for (Row row : this.catalog) {
            buf.writeUtf(row.key(), 80);
            buf.writeUtf(row.parentKey(), 80);
            buf.writeVarInt(row.depth());
            buf.writeVarInt(row.kind());
            buf.writeUtf(row.title(), 64);
            buf.writeUtf(row.subtitle(), 64);
            buf.writeUtf(row.subjectClip(), 32);
            buf.writeUtf(row.measureClip(), 32);
            buf.writeVarInt(row.metricType());
            buf.writeFloat(row.value());
            buf.writeVarInt(row.flags());
            buf.writeFloat(row.threshold());
            buf.writeVarInt(row.idNumber());
            buf.writeVarInt(row.distance());
        }

        buf.writeVarInt(this.subs.size());
        for (Sub sub : this.subs) {
            buf.writeUtf(sub.key(), 80);
            buf.writeUtf(sub.discipline(), 16);
            buf.writeUtf(sub.verbosity(), 16);
            buf.writeUtf(sub.severity(), 16);
            buf.writeUtf(sub.comparator(), 16);
            buf.writeFloat(sub.threshold());
            buf.writeFloat(sub.deadband());
            buf.writeBoolean(sub.enabled());
            buf.writeBoolean(sub.tripped());
            buf.writeBoolean(sub.following());
        }

        buf.writeVarInt(this.log.size());
        for (String line : this.log) {
            buf.writeUtf(line, 256);
        }

        buf.writeVarInt(this.severityOrdinal);
        buf.writeBoolean(this.speaking);
        buf.writeVarInt(this.queued);
        buf.writeVarInt(this.energy);
        buf.writeVarInt(this.energyMax);
        buf.writeVarInt(this.ratedDraw);
        buf.writeBoolean(this.powered);
        buf.writeVarInt(this.importable);
        buf.writeVarInt(this.circuitSize);

        buf.writeVarInt(this.speakers.size());
        for (SpeakerRow speaker : this.speakers) {
            buf.writeBlockPos(speaker.pos());
            buf.writeUtf(speaker.kind(), 16);
            buf.writeBoolean(speaker.powered());
            buf.writeBoolean(speaker.spoke());
            buf.writeVarInt(speaker.distance());
            buf.writeVarInt(speaker.range());
        }

        writeWords(buf, this.voice.onAir());
        buf.writeVarInt(this.voice.onAirWord() + 1);
        buf.writeVarInt(this.voice.queue().size());
        for (Waiting waiting : this.voice.queue()) {
            buf.writeVarInt(waiting.severityOrdinal());
            writeWords(buf, waiting.words());
            buf.writeVarInt(waiting.waitedTicks());
        }
        buf.writeBoolean(this.voice.held());
        buf.writeBoolean(this.voice.starved());
        buf.writeVarInt(this.voice.onDemand());
    }

    private static void writeWords(FriendlyByteBuf buf, List<String> words) {
        buf.writeVarInt(words.size());
        for (String word : words) {
            buf.writeUtf(word, 48);
        }
    }

    private static List<String> readWords(FriendlyByteBuf buf) {
        final int count = buf.readVarInt();
        final List<String> out = new ArrayList<>(Math.min(count, 64));
        for (int i = 0; i < count; i++) {
            final String word = buf.readUtf(48);
            if (i < 64) {
                out.add(word);
            }
        }
        return out;
    }

    public static PaSnapshot decode(FriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final PaSettings settings = new PaSettings();
        settings.load(buf.readNbt());

        final int rowCount = Math.min(buf.readVarInt(), 768);
        final List<Row> catalog = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            catalog.add(new Row(buf.readUtf(80), buf.readUtf(80), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(64), buf.readUtf(64), buf.readUtf(32), buf.readUtf(32),
                    buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readFloat(),
                    buf.readVarInt(), buf.readVarInt()));
        }

        final int subCount = Math.min(buf.readVarInt(), 256);
        final List<Sub> subs = new ArrayList<>(subCount);
        for (int i = 0; i < subCount; i++) {
            subs.add(new Sub(buf.readUtf(80), buf.readUtf(16), buf.readUtf(16), buf.readUtf(16),
                    buf.readUtf(16), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean()));
        }

        final int logCount = Math.min(buf.readVarInt(), 64);
        final List<String> log = new ArrayList<>(logCount);
        for (int i = 0; i < logCount; i++) {
            log.add(buf.readUtf(256));
        }

        final int severity = buf.readVarInt();
        final boolean speaking = buf.readBoolean();
        final int queued = buf.readVarInt();
        final int energy = buf.readVarInt();
        final int energyMax = buf.readVarInt();
        final int ratedDraw = buf.readVarInt();
        final boolean powered = buf.readBoolean();
        final int importable = buf.readVarInt();
        final int circuitSize = buf.readVarInt();

        final int speakerCount = Math.min(buf.readVarInt(), 256);
        final List<SpeakerRow> speakers = new ArrayList<>(speakerCount);
        for (int i = 0; i < speakerCount; i++) {
            speakers.add(new SpeakerRow(buf.readBlockPos(), buf.readUtf(16), buf.readBoolean(),
                    buf.readBoolean(), buf.readVarInt(), buf.readVarInt()));
        }

        final List<String> onAir = readWords(buf);
        final int onAirWord = buf.readVarInt() - 1;
        final int waitingCount = Math.min(buf.readVarInt(), 32);
        final List<Waiting> queue = new ArrayList<>(waitingCount);
        for (int i = 0; i < waitingCount; i++) {
            queue.add(new Waiting(buf.readVarInt(), readWords(buf), buf.readVarInt()));
        }
        final boolean held = buf.readBoolean();
        final boolean starved = buf.readBoolean();
        final Voice voice = new Voice(onAir, onAirWord, queue, held, starved, buf.readVarInt());

        return new PaSnapshot(pos, settings, catalog, subs, log, severity, speaking, queued,
                energy, energyMax, ratedDraw, powered, importable, circuitSize, speakers, voice);
    }

    public Sub subFor(String key) {
        for (Sub sub : this.subs) {
            if (sub.key().equals(key)) {
                return sub;
            }
        }
        return null;
    }

    public Row rowFor(String key) {
        for (Row row : this.catalog) {
            if (row.key().equals(key)) {
                return row;
            }
        }
        return null;
    }

    public int unreadable() {
        int count = 0;
        for (Sub sub : this.subs) {
            if (this.rowFor(sub.key()) == null) {
                count++;
            }
        }
        return count;
    }
}
