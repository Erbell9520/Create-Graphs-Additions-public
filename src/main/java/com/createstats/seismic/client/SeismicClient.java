package com.createstats.seismic.client;

import com.createstats.blockentity.SeismicScannerBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.registry.ModSounds;
import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicGeometry;
import com.createstats.seismic.SeismicRig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class SeismicClient {
    static final double EARSHOT = 16.0;

    static final float EASE = 0.35f;

    static final long RUN_ON = 2L;

    static final float LAMP_WARM = 0.6f;
    static final float LAMP_COOL = 0.42f;

    static final float LAMP_HUE = 0.45f;

    static final float SWAP = 0.25f;

    private SeismicClient() {
    }

    public static boolean advanced() {
        return Minecraft.getInstance().options.advancedItemTooltips;
    }

    public static boolean mayReadBack(SeismicScannerBE be) {
        final Level level = be.getLevel();
        return level != null && SeismicReveals.lacks(be, level.getGameTime());
    }

    public static void tick(SeismicScannerBE be) {
        final Level level = be.getLevel();
        if (level == null) {
            return;
        }
        final long now = level.getGameTime();
        final BlockPos pos = be.getBlockPos();

        final double away = away(be);
        final boolean near = away <= EARSHOT * EARSHOT;

        if (away <= SeismicScannerBE.WITNESS_RADIUS * SeismicScannerBE.WITNESS_RADIUS) {
            SeismicReveals.atRig(be, now);
        }

        weight(be, now);

        final int toothNow = SeismicGeometry.tooth(be.weightShown);
        if (toothNow > be.toothHeard && be.state() == SeismicRig.State.CHARGING && near) {
            level.playLocalSound(pos.getX() + 0.9, pos.getY() + 0.3, pos.getZ() + 0.5, ModSounds.SEISMIC_RATCHET.get(),
                    SoundSource.BLOCKS, 0.45f, 0.95f + 0.1f * (toothNow % 2), false);
        }
        be.toothHeard = toothNow;

        be.tickShownOld = be.tickShown;
        be.tickShown += (float) ((SeismicCharge.heightFor(be.radius()) - be.tickShown) * 0.25);

        be.paperDegOld = be.paperDeg;
        be.paperDeg += (float) SeismicGeometry.PAPER_DEG_PER_TICK;
        if (be.paperDeg >= 360.0f) {
            be.paperDeg -= 360.0f;
            be.paperDegOld -= 360.0f;
        }

        if (be.state() == SeismicRig.State.CHARGING && inWorld(be)) {
            SeismicWinchSound.keep(level, pos);
        }

        lamp(be, now);
        specimen(be);
        blow(be, now, near);
    }

    private static void weight(SeismicScannerBE be, long now) {
        be.weightShownOld = be.weightShown;
        final long landing = be.landingAt();
        if (be.falling(now)) {
            be.weightShown = (float) (be.dropHeight() * (1.0 - SeismicCharge.fallen(now - be.dropAt(), be.fallTicks())));
        } else if (landing != Long.MIN_VALUE && now - landing >= 0 && now - landing < 2 && be.weightShown > be.height()) {
            be.weightShown = (float) be.height();
            be.weightShownOld = be.weightShown;
        } else {
            final float eased = be.weightShown + (float) ((chargeGoal(be, now) - be.weightShown) * EASE);

            be.weightShown = be.state() == SeismicRig.State.CHARGING ? Math.max(be.weightShown, eased) : eased;
        }
    }

    static double chargeGoal(SeismicScannerBE be, long now) {
        if (be.state() != SeismicRig.State.CHARGING || be.intakeShown() <= 0 || be.syncedAt == Long.MIN_VALUE) {
            return be.height();
        }
        final long since = Math.max(0L, Math.min(RUN_ON, now - be.syncedAt));
        final double run = SeismicCharge.heightOf(be.stored() + (long) be.intakeShown() * since);
        return Math.max(be.height(), Math.min(run, SeismicCharge.heightFor(be.radius())));
    }

    private static void lamp(SeismicScannerBE be, long now) {
        final SeismicRig.Lamp lamp = SeismicRig.lamp(be.state());
        final boolean lit = SeismicRig.lampLit(lamp, now);
        be.lampGlowOld = be.lampGlow;
        be.lampGlow += ((lit ? 1.0f : 0.0f) - be.lampGlow) * (lit ? LAMP_WARM : LAMP_COOL);
        System.arraycopy(be.lampRgb, 0, be.lampRgbOld, 0, 3);
        if (lit) {
            final float[] c = SeismicScannerRenderer.lampColour(lamp);
            for (int i = 0; i < 3; i++) {
                be.lampRgb[i] += (c[i] - be.lampRgb[i]) * LAMP_HUE;
            }
        }
    }

    private static void specimen(SeismicScannerBE be) {
        be.specimenOutOld = be.specimenOut;
        if (be.specimenShown == null) {
            be.specimenShown = be.target();
        } else if (!be.specimenShown.equals(be.target())) {
            be.specimenOut = Math.min(1.0f, be.specimenOut + SWAP);
            if (be.specimenOut >= 1.0f) {
                be.specimenShown = be.target();
            }
        } else if (be.specimenOut > 0.0f) {
            be.specimenOut = Math.max(0.0f, be.specimenOut - SWAP);
        }
    }

    private static void blow(SeismicScannerBE be, long now, boolean near) {
        final Level level = be.getLevel();
        final BlockPos pos = be.getBlockPos();
        final long served = be.impactAt() != Long.MIN_VALUE && be.impactAt() >= be.dropAt() ? be.impactAt() : Long.MIN_VALUE;
        final long impact = served != Long.MIN_VALUE ? served : be.landingAt();
        if (impact != Long.MIN_VALUE && now >= impact && impact != be.blowAt) {
            be.prevBlowAt = be.blowAt;
            be.prevBlowShown = be.blowShown;
            be.prevBlowEchoes = be.blowEchoes;
            be.blowAt = impact;
            be.blowShown = be.blowStrength();
            be.blowEchoes = served == impact ? be.echoes() : new int[0];
            be.echoesHeard.clear();
            if (now - impact < 6) {
                if (inWorld(be)) {
                    SeismicShake.blow(be, be.blowStrength());
                }
            } else {
                be.echoesHeard.set(0, be.blowEchoes.length / 2);
            }
        }
        if (be.blowAt != Long.MIN_VALUE && be.impactAt() == be.blowAt) {
            be.blowEchoes = be.echoes();
        }
        final int[] e = be.blowEchoes;
        boolean scratch = false;
        for (int i = 0; i + 1 < e.length; i += 2) {
            if (!be.echoesHeard.get(i / 2) && be.blowAt + e[i] <= now) {
                be.echoesHeard.set(i / 2);
                scratch = true;
            }
        }
        if (scratch && near && level != null) {
            level.playLocalSound(pos.getX() + 0.2, pos.getY() + 0.35, pos.getZ() + 0.1, ModSounds.SEISMIC_PEN.get(),
                    SoundSource.BLOCKS, 0.5f, 0.9f + level.random.nextFloat() * 0.2f, false);
        }
    }

    static double away(SeismicScannerBE be) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !inWorld(be)) {
            return Double.POSITIVE_INFINITY;
        }
        final Vec3 mid = middle(be);
        return mid == null ? Double.POSITIVE_INFINITY : mc.player.distanceToSqr(mid);
    }

    static boolean inWorld(SeismicScannerBE be) {
        final Level level = be.getLevel();
        return level != null && level == Minecraft.getInstance().level;
    }

    @Nullable
    static Vec3 middle(SeismicScannerBE be) {
        final BlockPos pos = be.getBlockPos();
        return SableBridge.worldPointOf(be, new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
    }
}
