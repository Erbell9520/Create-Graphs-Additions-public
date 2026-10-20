package com.createstats.pa;

import java.util.ArrayList;
import java.util.List;

import com.createstats.alarm.annunciator.AlarmSeverity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// clip ids on the wire, text would need a second Verbalizer that can disagree
public record Utterance(List<String> clips, AlarmSeverity severity, String dedupeKey) {
    public static final Utterance NOTHING = new Utterance(List.of(), AlarmSeverity.NORMAL, "");

    public static final StreamCodec<RegistryFriendlyByteBuf, Utterance> STREAM_CODEC =
            StreamCodec.of(Utterance::encode, Utterance::decode);

    public Utterance {
        clips = List.copyOf(clips);
    }

    public boolean isEmpty() {
        return this.clips.isEmpty();
    }

    public static final int TICKS_PER_CLIP = 13;

    public int estimatedTicks() {
        return this.clips.isEmpty() ? 0 : TICKS_PER_CLIP * this.clips.size() + 8;
    }

    public boolean bargesIn(Utterance current) {
        return current == null || current.isEmpty()
                || this.severity.ordinal() > current.severity.ordinal();
    }

    private static void encode(RegistryFriendlyByteBuf buf, Utterance u) {
        buf.writeVarInt(u.clips.size());
        for (String clip : u.clips) {
            ByteBufCodecs.STRING_UTF8.encode(buf, clip);
        }
        buf.writeVarInt(u.severity.ordinal());
        ByteBufCodecs.STRING_UTF8.encode(buf, u.dedupeKey);
    }

    private static Utterance decode(RegistryFriendlyByteBuf buf) {
        final int size = buf.readVarInt();
        final List<String> clips = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            clips.add(ByteBufCodecs.STRING_UTF8.decode(buf));
        }
        final AlarmSeverity[] severities = AlarmSeverity.values();
        final int ordinal = buf.readVarInt();
        final AlarmSeverity severity = severities[Math.floorMod(ordinal, severities.length)];
        return new Utterance(clips, severity, ByteBufCodecs.STRING_UTF8.decode(buf));
    }

    public static final class Builder {
        private final List<String> clips = new ArrayList<>();
        private final List<String> dropped = new ArrayList<>();
        private AlarmSeverity severity = AlarmSeverity.NORMAL;
        private String dedupeKey = "";

        public Builder say(String... tokens) {
            for (String token : tokens) {
                if (token == null || token.isEmpty()) {
                    continue;
                }
                if (Lexicon.allClips().contains(token)) {
                    this.clips.add(token);
                } else {
                    this.dropped.add(token);
                }
            }
            return this;
        }

        public Builder say(List<String> tokens) {
            return this.say(tokens.toArray(new String[0]));
        }

        public Builder quantity(double value, String unitClip) {
            this.say(Lexicon.quantity(value));
            return this.say(unitClip);
        }

        public Builder severity(AlarmSeverity value) {
            this.severity = value == null ? AlarmSeverity.NORMAL : value;
            return this;
        }

        public Builder dedupe(String key) {
            this.dedupeKey = key == null ? "" : key;
            return this;
        }

        public List<String> dropped() {
            return List.copyOf(this.dropped);
        }

        public Utterance build() {
            return new Utterance(this.clips, this.severity, this.dedupeKey);
        }
    }
}
