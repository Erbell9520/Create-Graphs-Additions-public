package com.createstats.client.ponder.seismic;

import com.createstats.CreateStats;
import com.createstats.blockentity.SeismicScannerBE;
import com.createstats.client.ponder.KitInstruction;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class RigDriver {
    private static final Map<PonderScene, List<Driven>> DRIVEN = new WeakHashMap<>();

    private static final class Driven {
        final BlockPos pos;
        final RigScript script;
        int tick = Integer.MIN_VALUE;
        int sceneTime;
        long now;

        Driven(BlockPos pos, RigScript script) {
            this.pos = pos;
            this.script = script;
        }
    }

    private RigDriver() {
    }

    public static KitInstruction drive(BlockPos pos, RigScript script, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private RigLook look;
            private Driven driven;

            @Override
            protected void begin(PonderScene scene) {
                look = new RigLook();
                driven = new Driven(pos, script);
                synchronized (DRIVEN) {
                    final List<Driven> list = DRIVEN.computeIfAbsent(scene, s -> new ArrayList<>());
                    list.removeIf(d -> d.pos.equals(pos));
                    list.add(driven);
                }
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (!(scene.getWorld().getBlockEntity(pos) instanceof SeismicScannerBE be) || look == null) {
                    return;
                }
                final RigScript.Frame f = script.at(tick);
                final long now = scene.getWorld().getGameTime();
                anchor(scene, be, f, tick, now);
                driven.tick = tick;
                driven.sceneTime = scene.getCurrentTime();
                driven.now = now;
                if (last) {
                    synchronized (DRIVEN) {
                        final List<Driven> list = DRIVEN.get(scene);
                        if (list != null) {
                            list.remove(driven);
                        }
                    }
                }
                if (tick == start) {
                    look.settle(f, tick);
                    be.tickShown = be.tickShownOld = look.dogs;
                    be.specimenShown = f.target();
                    be.specimenOut = be.specimenOutOld = 0f;
                } else {
                    look.step(f, tick);
                }

                be.weightShown = look.weight;
                be.weightShownOld = look.weightOld;
                be.lampGlow = look.glow;
                be.lampGlowOld = look.glowOld;
                System.arraycopy(look.rgb, 0, be.lampRgb, 0, 3);
                System.arraycopy(look.rgbOld, 0, be.lampRgbOld, 0, 3);
            }
        };
    }

    private static void anchor(PonderScene scene, SeismicScannerBE be, RigScript.Frame f, int tick, long now) {
        be.loadCustomOnly(tag(f, tick, now), scene.getWorld().registryAccess());
        if (f.dropTick() >= 0 && f.fallTicks() > 0) {
            be.blowAt = now - (tick - f.dropTick()) + f.fallTicks();
            be.blowShown = f.blow();
            be.blowEchoes = f.echoes();
        } else {
            be.blowAt = Long.MIN_VALUE;
            be.blowShown = 0.0;
            be.blowEchoes = new int[0];
        }
        be.prevBlowAt = Long.MIN_VALUE;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!(Minecraft.getInstance().screen instanceof PonderUI ui)) {
            return;
        }
        final PonderScene scene = ui.getActiveScene();
        final List<Driven> rigs;
        synchronized (DRIVEN) {
            final List<Driven> list = DRIVEN.get(scene);
            if (list == null || list.isEmpty()) {
                return;
            }
            rigs = List.copyOf(list);
        }
        try {
            for (final Driven d : rigs) {
                if (d.tick == Integer.MIN_VALUE || d.sceneTime != scene.getCurrentTime()) {
                    continue;
                }
                final long now = scene.getWorld().getGameTime();
                if (now == d.now || !(scene.getWorld().getBlockEntity(d.pos) instanceof SeismicScannerBE be)) {
                    continue;
                }
                anchor(scene, be, d.script.at(d.tick), d.tick, now);
                d.now = now;
            }
        } catch (RuntimeException e) {
            synchronized (DRIVEN) {
                DRIVEN.remove(scene);
            }
            CreateStats.LOGGER.warn("[Ponder] seismic rig re-anchoring stopped: {}", e.toString());
        }
    }

    static CompoundTag tag(RigScript.Frame f, int tick, long now) {
        final CompoundTag t = new CompoundTag();
        t.putInt("Energy", f.stored());
        t.putString("Target", f.target());
        t.putInt("Radius", f.radius());
        t.putInt("Tier", 3);
        t.putInt("Lock", 0);
        t.putString("LastTarget", f.target());
        t.putInt("LastVeins", -1);
        t.putInt("LastNearest", -1);
        t.putInt("State", f.state().ordinal());
        t.putBoolean("Coupled", f.coupled());
        t.putString("Ground", f.coupled() ? "minecraft:grass_block" : "minecraft:oak_planks");
        t.putBoolean("OnCraft", false);
        if (f.dropTick() >= 0) {
            t.putLong("DropAt", now - (tick - f.dropTick()));
        }
        t.putFloat("DropHeight", f.dropHeight());
        t.putInt("Fall", f.fallTicks());
        if (f.impactTick() >= 0) {
            t.putLong("ImpactAt", now - (tick - f.impactTick()));
        }
        t.putInt("Reach", f.reach());
        t.putFloat("Blow", f.blow());
        t.put("Echoes", new IntArrayTag(f.echoes()));
        t.putInt("Getting", f.intake());
        t.putInt("Draw", f.arriving());
        t.putInt("Front", 0);
        t.putInt("FoundVeins", 0);
        return t;
    }
}
