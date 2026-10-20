package com.createstats.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.createstats.ModConfiguration;
import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.network.PaSpeakPacket;
import com.createstats.pa.Utterance;
import com.createstats.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PaVoicePlayer {
    private PaVoicePlayer() {
    }

    private record Word(String clip, int atTick) {
    }

    private static final Deque<Word> PENDING = new ArrayDeque<>();
    private static BlockPos source = BlockPos.ZERO;
    private static AlarmSeverity severity = AlarmSeverity.NORMAL;

    private static float blockVolume = 1.0f;
    private static float blockPitch = 1.0f;

    private static int reach = 16;
    private static int tick;

    public static BlockPos speakingAt() {
        return PENDING.isEmpty() ? null : source;
    }

    public static boolean isSpeaking() {
        return !PENDING.isEmpty();
    }

    public static String currentClip() {
        final Word head = PENDING.peek();
        return head == null ? "" : head.clip();
    }

    public static void accept(PaSpeakPacket pkt) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (pkt.stopFirst()) {
            PENDING.clear();
        }
        if (pkt.clips().isEmpty()) {
            return;
        }
        source = pkt.pos();
        severity = pkt.severityValue();
        blockVolume = pkt.volume();
        blockPitch = pkt.pitch();
        reach = pkt.range();
        int at = tick;
        for (String clip : pkt.clips()) {
            PENDING.addLast(new Word(clip, at));
            at += Utterance.TICKS_PER_CLIP;
        }
    }

    public static void clientTick() {
        tick++;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            PENDING.clear();
            return;
        }
        while (!PENDING.isEmpty() && PENDING.peek().atTick() <= tick) {
            final Word word = PENDING.pollFirst();
            play(word.clip());
        }
    }

    private static void play(String clip) {
        final SoundEvent sound = ModSounds.voiceClip(clip);
        if (sound == null) {
            com.createstats.DebugLogging.pa("no baked clip for '{}', sentence will be short", clip);
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        final float mine = (float) (double) ModConfiguration.CLIENT.paVoiceVolume.get();
        final float volume = mine * blockVolume * fade(mc);
        if (volume <= 0.0f) {
            return;
        }

        final float nudge = switch (severity) {
            case CRITICAL -> 1.06f;
            case WARNING -> 1.03f;
            default -> 1.0f;
        };
        mc.level.playLocalSound(source.getX() + 0.5, source.getY() + 0.5, source.getZ() + 0.5,
                sound, SoundSource.BLOCKS, volume, blockPitch * nudge, false);
    }

    private static float fade(Minecraft mc) {
        final double dx = source.getX() + 0.5 - mc.player.getX();
        final double dz = source.getZ() + 0.5 - mc.player.getZ();
        final double flat = Math.sqrt(dx * dx + dz * dz);
        final float span = Math.max(1.0f, reach);
        return (float) Math.max(0.45, 1.0 - 0.55 * Math.min(1.0, flat / span));
    }

    public static void stop() {
        PENDING.clear();
    }

    public static int remaining() {
        return PENDING.size();
    }

    public static void previewLocally(List<String> clips, BlockPos at) {
        accept(new PaSpeakPacket(at, clips, AlarmSeverity.NORMAL.ordinal(), true, 1.0f, 1.0f,
                (int) com.createstats.registry.ModSounds.PA_VOICE_RANGE));
    }
}
