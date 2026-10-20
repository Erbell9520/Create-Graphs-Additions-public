package com.createstats.client.ponder;

import com.createstats.CreateStats;
import com.createstats.client.ponder.fe.FePaths;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;
import com.createstats.renderer.PowerConnectorRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

public final class StudSpans {
    public static final int FADE = 15;

    private StudSpans() {
    }

    static final class Growing extends Outline {
        List<Vec3> curve = List.of();
        WireTier tier = WireTier.COPPER;
        float grown;
        float prevGrown;
        boolean done;
        int light;

        @Override
        public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
            if (done || curve.size() < 2) return;
            final double g = Ease.OUT_CUBIC.clamped(Mth.lerp(pt, prevGrown, grown));
            if (g <= 0.001) return;
            final double last = (curve.size() - 1) * g;
            final int whole = (int) Math.floor(last);
            final List<double[]> pts = new ArrayList<>();
            for (int i = 0; i <= whole && i < curve.size(); i++) {
                final Vec3 p = curve.get(i);
                pts.add(new double[] {p.x, p.y, p.z});
            }
            if (whole < curve.size() - 1) {
                final Vec3 p = curve.get(whole).lerp(curve.get(whole + 1), last - whole);
                pts.add(new double[] {p.x, p.y, p.z});
            }
            if (pts.size() < 2) return;
            PowerConnectorRenderer.drawTube(PowerConnectorRenderer.wireBuffer(buffer), ms.last().pose(),
                    pts.toArray(new double[0][]), light, tier.halfThickness() * 1.15, tier.strandA());
        }
    }

    public static KitInstruction grow(Object slot, Vec3 from, BlockPos panel, BlockPos far, WireTier tier, int start,
                                      int ticks) {
        return new KitInstruction(start, ticks) {
            private Growing wire;

            @Override
            protected void begin(PonderScene scene) {
                wire = new Growing();
                wire.tier = tier;
                wire.curve = FePaths.catenary(from, FePaths.attach(scene.getWorld(), far), WireTension.STANDARD.slack());
                wire.light = LevelRenderer.getLightColor(scene.getWorld(), panel);
                scene.getOutliner().showOutline(slot, wire).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (wire == null) return;
                wire.prevGrown = wire.grown;
                wire.grown = Math.min(1f, (tick - start + 1) / (float) Math.max(1, totalTicks - 1));
                if (last) {
                    farEnd(scene.getWorld(), far, panel, tier);
                    wire.done = true;
                } else {
                    scene.getOutliner().keep(slot);
                }
            }
        };
    }

    public static void farEnd(PonderLevel level, BlockPos far, BlockPos panel, WireTier tier) {
        try {
            final BlockEntity end = level.getBlockEntity(far);
            if (end == null) return;
            final CompoundTag tag = end.saveWithoutMetadata(level.registryAccess());
            final long[] links = tag.getLongArray("Links");
            for (long l : links) {
                if (l == panel.asLong()) return;
            }
            final byte[] tiers = tag.getByteArray("LinkTiers");
            final byte[] ten = tag.getByteArray("LinkTension");
            final long[] nl = java.util.Arrays.copyOf(links, links.length + 1);
            final byte[] nt = java.util.Arrays.copyOf(tiers, links.length + 1);
            final byte[] nx = java.util.Arrays.copyOf(ten, links.length + 1);
            nl[links.length] = panel.asLong();
            nt[links.length] = (byte) tier.ordinal();
            nx[links.length] = (byte) -1;
            tag.putLongArray("Links", nl);
            tag.putByteArray("LinkTiers", nt);
            tag.putByteArray("LinkTension", nx);
            end.loadWithComponents(tag, level.registryAccess());
        } catch (RuntimeException e) {
            CreateStats.LOGGER.warn("[Ponder] a span could not be written: {}", e.toString());
        }
    }

    public static KitInstruction sections(List<Selection> cells, List<IntPredicate> shown, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private WorldSectionElementImpl[] elements;
            private boolean[] on;
            private int[] age;

            @Override
            protected void begin(PonderScene scene) {
                elements = new WorldSectionElementImpl[cells.size()];
                on = new boolean[cells.size()];
                age = new int[cells.size()];
                for (int i = 0; i < cells.size(); i++) {
                    final WorldSectionElementImpl e = new WorldSectionElementImpl(cells.get(i));
                    scene.addElement(e);
                    e.set(cells.get(i));
                    e.setVisible(false);
                    e.setFade(0f);
                    e.setFade(0f);
                    elements[i] = e;
                    age[i] = FADE;
                }
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (elements == null) return;
                for (int i = 0; i < elements.length; i++) {
                    final boolean want = shown.get(i).test(tick);
                    final WorldSectionElement e = elements[i];
                    if (want != on[i]) {
                        on[i] = want;
                        age[i] = 0;
                        e.setVisible(true);

                        e.setFadeVec(new Vec3(0, 0.5, 0));
                    }
                    if (age[i] < FADE) {
                        age[i]++;
                        final float u = age[i] / (float) FADE;
                        e.setFade(on[i] ? 1f - (1f - u) * (1f - u) : 1f - u * u);
                        if (!on[i] && age[i] >= FADE) {
                            e.setVisible(false);
                        }
                    }
                }
            }
        };
    }
}
