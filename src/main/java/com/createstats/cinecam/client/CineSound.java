package com.createstats.cinecam.client;

import com.createstats.CreateStats;
import com.createstats.cinecam.film.LensMixer;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

// what someone standing at the lens would hear
final class CineSound implements SoundEventListener {
    static final int RATE = LensMixer.RATE;

    private final Minecraft mc;
    private final LensMixer mixer;

    private final Map<SoundInstance, Integer> mirrored = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();

    private volatile long asked;
    private volatile boolean closing;

    private CineSound(Minecraft mc, Consumer<LensMixer.Chunk> out) {
        this.mc = mc;
        this.mixer = new LensMixer("Cine Camera sound", out, this::decode);
    }

    static CineSound open(Minecraft mc, Consumer<LensMixer.Chunk> out) {
        CineSound s = new CineSound(mc, out);
        mc.getSoundManager().addListener(s);
        s.joinPlaying();
        return s;
    }

    private void joinPlaying() {
        for (SoundInstance s : playing(mc.getSoundManager())) {
            if (s.isLooping() && s.getDelay() <= 0) mirror(s);
        }
    }

    @Override
    public void onPlaySound(SoundInstance instance, WeighedSoundEvents events, float range) {
        if (mc.options.getSoundSourceVolume(SoundSource.MASTER) <= 0.0F) return;
        mirror(instance);
    }

    private void mirror(SoundInstance instance) {
        if (closing || instance.isRelative() || mirrored.containsKey(instance)) return;
        Sound sound = instance.getSound();
        if (sound == null || sound == SoundManager.EMPTY_SOUND || sound == SoundManager.INTENTIONALLY_EMPTY_SOUND) return;
        float gain = gain(instance);
        float pitch = Mth.clamp(instance.getPitch(), 0.5F, 2.0F);
        boolean linear = instance.getAttenuation() == SoundInstance.Attenuation.LINEAR;
        float reach = Math.max(instance.getVolume(), 1.0F) * sound.getAttenuationDistance();

        boolean loop = instance.isLooping() && instance.getDelay() <= 0;
        Vec3 at = world(instance.getX(), instance.getY(), instance.getZ());
        int id = ids.incrementAndGet();
        mirrored.put(instance, id);
        mixer.play(id, sound.getPath().toString(), at.x, at.y, at.z, gain, pitch, linear, reach, loop, asked);
    }

    static final String SOUND_ENGINE = "soundEngine";
    static final String PLAYING = "instanceToChannel";
    @Nullable private static Field engineField;
    @Nullable private static Field playingField;
    private static boolean looked;

    static List<SoundInstance> playing(SoundManager manager) {
        if (!looked) {
            looked = true;
            engineField = field(SoundManager.class, SOUND_ENGINE, SoundEngine.class);
            playingField = field(SoundEngine.class, PLAYING, Map.class);
        }
        if (engineField == null || playingField == null) return List.of();
        try {
            Object engine = engineField.get(manager);
            if (engine == null || !(playingField.get(engine) instanceof Map<?, ?> map)) return List.of();
            List<SoundInstance> out = new ArrayList<>();
            for (Object k : map.keySet()) {
                if (k instanceof SoundInstance s) out.add(s);
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    @Nullable
    static Field field(Class<?> owner, String name, Class<?> type) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            if (f.getType() == type) return f;
            CreateStats.LOGGER.warn("Cine Camera: {}.{} is not a {} here; a take hears only the sounds that start in it",
                    owner.getSimpleName(), name, type.getSimpleName());
        } catch (ReflectiveOperationException | RuntimeException e) {
            CreateStats.LOGGER.warn("Cine Camera: {}.{} cannot be reached here ({}); a take hears only the sounds that start in it",
                    owner.getSimpleName(), name, e.toString());
        }
        return null;
    }

    private float gain(SoundInstance instance) {
        SoundSource source = instance.getSource();
        float category = source == null || source == SoundSource.MASTER ? 1.0F : mc.options.getSoundSourceVolume(source);
        return Mth.clamp(instance.getVolume() * category, 0.0F, 1.0F);
    }

    private Vec3 world(double x, double y, double z) {
        Vec3 v = new Vec3(x, y, z);
        try {
            return mc.level == null ? v : SableBridge.projectOutOfSubLevel(mc.level, v);
        } catch (RuntimeException e) {
            return v;
        }
    }

    void tick() {
        for (Iterator<Map.Entry<SoundInstance, Integer>> it = mirrored.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<SoundInstance, Integer> e = it.next();
            SoundInstance s = e.getKey();
            int id = e.getValue();
            boolean stopped = s instanceof TickableSoundInstance t && t.isStopped();
            if (stopped || !mc.getSoundManager().isActive(s)) {
                it.remove();
                mixer.stop(id);
            } else if (s instanceof TickableSoundInstance) {
                Vec3 at = world(s.getX(), s.getY(), s.getZ());
                mixer.move(id, at.x, at.y, at.z, gain(s), Mth.clamp(s.getPitch(), 0.5F, 2.0F));
            }
        }
    }

    void frame(Vec3 eye, double[] forward, long sample) {
        mixer.listen(eye.x, eye.y, eye.z, LensMixer.orientation(forward[0], forward[1], forward[2]));
        if (sample > asked) {
            asked = sample;
            mixer.mix(sample);
        }
    }

    void finish(long end) {
        if (closing) return;
        closing = true;
        mc.getSoundManager().removeListener(this);
        mirrored.clear();
        mixer.finish(Math.max(end, asked));
    }

    private CompletableFuture<LensMixer.Pcm> decode(String key) {
        ResourceLocation path = ResourceLocation.parse(key);
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream in = mc.getResourceManager().open(path); JOrbisAudioStream s = new JOrbisAudioStream(in)) {
                ByteBuffer pcm = s.readAll();
                AudioFormat f = s.getFormat();
                return f.getSampleSizeInBits() == 16 ? new LensMixer.Pcm(pcm, f.getChannels(), (int) f.getSampleRate()) : null;
            } catch (Exception e) {
                return null;
            }
        }, Util.nonCriticalIoPool());
    }
}
