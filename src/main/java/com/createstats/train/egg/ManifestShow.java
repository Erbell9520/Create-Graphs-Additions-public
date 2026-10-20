package com.createstats.train.egg;

import com.createstats.advancement.ModAdvancements;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

public final class ManifestShow {
    static final int STALE_TICKS = ManifestEgg.HALF_BEAT_TICKS * 4;

    private static final int BELL_GRACE_TICKS = 10;

    private static final int ROLL_CALL_TICKS = ManifestEgg.ROLL_CALL_SPACING * (ManifestEgg.CARRIAGES - 1);

    private static final float BELL_VOLUME = 2.0f;

    private static final int PUFF_COUNT = 6;
    private static final double PUFF_SPREAD = 0.3;

    private static final int RING_POINTS = 8;
    private static final double RING_RADIUS = 1.1;

    private static final DustParticleOptions[] PUFF = dust(1.6f);
    private static final DustParticleOptions[] RING = dust(1.1f);
    private static final double[] RING_X = ring(true);
    private static final double[] RING_Z = ring(false);

    private static final double[] NOTE_HUE = noteHues();

    private static final Map<UUID, Sighting> SIGHTINGS = new HashMap<>();

    private static final List<Bell> BELLS = new ArrayList<>();

    private ManifestShow() {
    }

    public static void tick(ServerLevel level) {
        final long now = level.getGameTime();
        if (ManifestEgg.isHalfBeat(now)) {
            survey(level, now);
        }
        if (!BELLS.isEmpty()) {
            ringDue(level, now);
        }
    }

    private static void survey(ServerLevel level, long now) {
        if (!SIGHTINGS.isEmpty()) {
            SIGHTINGS.values().removeIf(seen -> now < seen.tick || now - seen.tick > STALE_TICKS);
        }
        final Map<UUID, Train> trains = Create.RAILWAYS.sided(level).trains;
        if (trains.isEmpty()) {
            return;
        }
        final boolean beat = ManifestEgg.isBeat(now);
        for (Train train : trains.values()) {
            if (train.carriages.size() != ManifestEgg.CARRIAGES || train.invalid || train.derailed
                    || train.name == null) {
                continue;
            }
            final String name = train.name.getString();
            if (!ManifestEgg.isManifest(name, train.carriages.size())) {
                continue;
            }
            if (entityIn(train.carriages.get(0), level) != null) {
                watch(train, Math.abs(train.speed) > ManifestEgg.MIN_SPEED, now, level.dimension());
            }
            if (beat && ManifestEgg.matches(name, train.carriages.size(), train.speed)) {
                dance(level, train, ManifestEgg.isMove(now));
            }
        }
    }

    private static void watch(Train train, boolean moving, long now, ResourceKey<Level> dimension) {
        final Sighting seen = SIGHTINGS.get(train.id);
        if (seen == null) {
            SIGHTINGS.put(train.id, new Sighting(now, moving));
            return;
        }
        if (moving && !seen.moving) {
            final boolean backing = train.speed < 0.0;
            for (int call = 0; call < ManifestEgg.CARRIAGES; call++) {
                final int girl = backing ? ManifestEgg.CARRIAGES - 1 - call : call;
                BELLS.add(new Bell(train.id, girl, call, now + (long) call * ManifestEgg.ROLL_CALL_SPACING, dimension));
            }
        }
        seen.tick = now;
        seen.moving = moving;
    }

    private static void ringDue(ServerLevel level, long now) {
        final ResourceKey<Level> dimension = level.dimension();
        Map<UUID, Train> trains = null;
        for (Iterator<Bell> it = BELLS.iterator(); it.hasNext(); ) {
            final Bell bell = it.next();
            if (now - bell.due() > BELL_GRACE_TICKS || bell.due() - now > ROLL_CALL_TICKS) {
                it.remove();
                continue;
            }
            if (bell.due() > now || !bell.dimension().equals(dimension)) {
                continue;
            }
            it.remove();
            if (trains == null) {
                trains = Create.RAILWAYS.sided(level).trains;
            }
            final Train train = trains.get(bell.train());
            if (train == null || bell.girl() >= train.carriages.size()) {
                continue;
            }
            final CarriageContraptionEntity entity = entityIn(train.carriages.get(bell.girl()), level);
            if (entity == null) {
                continue;
            }
            final AABB box = entity.getBoundingBox();
            final double x = (box.minX + box.maxX) / 2.0;
            final double z = (box.minZ + box.maxZ) / 2.0;
            level.playSound(null, x, box.maxY, z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.RECORDS,
                    BELL_VOLUME, ManifestEgg.ROLL_CALL_PITCHES[bell.call()]);

            level.sendParticles(ParticleTypes.NOTE, x, box.maxY + 0.7, z, 0, NOTE_HUE[bell.girl()], 0.0, 0.0, 1.0);
        }
    }

    private static void dance(ServerLevel level, Train train, boolean bar) {
        for (int girl = 0; girl < ManifestEgg.CARRIAGES; girl++) {
            final CarriageContraptionEntity entity = entityIn(train.carriages.get(girl), level);
            if (entity == null) {
                continue;
            }
            final AABB box = entity.getBoundingBox();
            final double x = (box.minX + box.maxX) / 2.0;
            final double y = box.maxY + 0.35;
            final double z = (box.minZ + box.maxZ) / 2.0;
            level.sendParticles(PUFF[girl], x, y, z, PUFF_COUNT, PUFF_SPREAD, PUFF_SPREAD / 2.0, PUFF_SPREAD, 0.0);
            if (bar) {
                for (int k = 0; k < RING_POINTS; k++) {
                    level.sendParticles(RING[girl], x + RING_X[k], y, z + RING_Z[k], 1, 0.0, 0.0, 0.0, 0.0);
                }
                level.sendParticles(ParticleTypes.NOTE, x, y + 0.6, z, 0, NOTE_HUE[girl], 0.0, 0.0, 1.0);
            }
            ModAdvancements.awardNearby(level, entity.blockPosition(), ManifestEgg.AUDIENCE_RADIUS,
                    ModAdvancements.MANIFEST_YAAA);
        }
    }

    @Nullable
    private static CarriageContraptionEntity entityIn(Carriage carriage, ServerLevel level) {
        final Carriage.DimensionalCarriageEntity here = carriage.getDimensionalIfPresent(level.dimension());
        if (here == null || here.entity == null) {
            return null;
        }
        final CarriageContraptionEntity entity = here.entity.get();
        return entity != null && !entity.isRemoved() && entity.level() == level ? entity : null;
    }

    private static DustParticleOptions[] dust(float scale) {
        final DustParticleOptions[] out = new DustParticleOptions[ManifestEgg.CARRIAGES];
        for (int i = 0; i < out.length; i++) {
            final int rgb = ManifestEgg.COLOURS[i];
            out[i] = new DustParticleOptions(new Vector3f((rgb >> 16 & 0xFF) / 255.0f, (rgb >> 8 & 0xFF) / 255.0f,
                    (rgb & 0xFF) / 255.0f), scale);
        }
        return out;
    }

    private static double[] ring(boolean across) {
        final double[] out = new double[RING_POINTS];
        for (int k = 0; k < RING_POINTS; k++) {
            final double angle = 2.0 * Math.PI * k / RING_POINTS;
            out[k] = RING_RADIUS * (across ? Math.cos(angle) : Math.sin(angle));
        }
        return out;
    }

    private static double[] noteHues() {
        final double[] out = new double[ManifestEgg.CARRIAGES];
        for (int i = 0; i < out.length; i++) {
            out[i] = ManifestEgg.noteHue(ManifestEgg.COLOURS[i]);
        }
        return out;
    }

    private static final class Sighting {
        private long tick;
        private boolean moving;

        private Sighting(long tick, boolean moving) {
            this.tick = tick;
            this.moving = moving;
        }
    }

    private record Bell(UUID train, int girl, int call, long due, ResourceKey<Level> dimension) {
    }
}
