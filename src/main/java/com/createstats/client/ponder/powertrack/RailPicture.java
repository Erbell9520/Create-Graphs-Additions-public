package com.createstats.client.ponder.powertrack;

import com.createstats.client.ponder.KitInstruction;
import com.createstats.powertrack.client.ArcEffects;
import com.createstats.powertrack.client.PowerRailRenderer;
import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.RailMesher;
import com.createstats.powertrack.core.SectionGraph;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

// ponder has no track graph, so draw the real rail mesh ourselves
public final class RailPicture extends Outline {
    private static final class Edge {
        final int index;
        final double[] origin;
        final RailMesher.Quad[] quads;
        final double[] along;
        final boolean[] chair;

        final List<RailMesher.Quad[]> tapQuads = new ArrayList<>();
        final List<Double> tapAt = new ArrayList<>();

        Edge(int index, double[] origin, RailMesher.Quad[] quads, double[] along, boolean[] chair) {
            this.index = index;
            this.origin = origin;
            this.quads = quads;
            this.along = along;
            this.chair = chair;
        }
    }

    public record Arc(Vec3 top, Vec3 bottom, int seed, int tick) {
    }

    public interface Reveal {
        double chairs(int edge, double tick);

        double rail(int edge, double tick);

        int tapAt(int i);
    }

    private final List<Edge> edges = new ArrayList<>();
    private final Reveal reveal;
    private final List<Arc> arcs = new ArrayList<>();
    private int tick;
    private int tickOld;

    private static final int TAP_TICKS = 6;

    public RailPicture(PowerTrackStage.Stage stage, Reveal reveal) {
        this.reveal = reveal;
        final RailLayout layout = stage.layout();
        final List<RailMesher.Tap> taps = stage.meshTaps();
        final List<RailLayout.Shape> shapes = layout.shapes();
        for (int i = 0; i < shapes.size(); i++) {
            final RailLayout.Shape shape = shapes.get(i);
            final List<SectionGraph.Piece> pieces = layout.sections().pieces(shape.lo(), shape.hi());
            final double[] origin = shape.point(0);
            final List<RailMesher.Quad> base = RailMesher.meshEdge(shape, pieces, List.of(), origin);
            final RailMesher.Quad[] quads = base.toArray(new RailMesher.Quad[0]);
            final double[] along = new double[quads.length];
            final boolean[] chair = new boolean[quads.length];
            for (int q = 0; q < quads.length; q++) {
                along[q] = alongOf(shape, origin, quads[q]);
                chair[q] = quads[q].uv() == RailMesher.UV_PORCELAIN || quads[q].uv() == RailMesher.UV_IRON;
            }
            final Edge edge = new Edge(i, origin, quads, along, chair);

            for (RailMesher.Tap t : taps) {
                if (t.lo() != shape.lo() || t.hi() != shape.hi()) {
                    continue;
                }
                final List<RailMesher.Quad> withTap = RailMesher.meshEdge(shape, pieces, List.of(t), origin);
                final List<RailMesher.Quad> own = withTap.subList(base.size(), withTap.size());
                edge.tapQuads.add(own.toArray(new RailMesher.Quad[0]));
                edge.tapAt.add(t.at());
            }
            edges.add(edge);
        }
    }

    static double alongOf(RailLayout.Shape shape, double[] origin, RailMesher.Quad q) {
        final float[] p = q.xyz();
        final double cx = origin[0] + (p[0] + p[3] + p[6] + p[9]) / 4.0;
        final double cz = origin[2] + (p[2] + p[5] + p[8] + p[11]) / 4.0;
        final int n = shape.samples();
        double best = Double.MAX_VALUE;
        double at = 0.0;
        for (int k = 0; k + 1 < n; k++) {
            final double[] a = shape.point(shape.length() * k / (n - 1));
            final double[] b = shape.point(shape.length() * (k + 1) / (n - 1));
            final double dx = b[0] - a[0];
            final double dz = b[2] - a[2];
            final double len2 = dx * dx + dz * dz;
            double u = len2 < 1.0e-12 ? 0.0 : ((cx - a[0]) * dx + (cz - a[2]) * dz) / len2;
            u = Math.max(0.0, Math.min(1.0, u));
            final double px = a[0] + dx * u;
            final double pz = a[2] + dz * u;
            final double d = (cx - px) * (cx - px) + (cz - pz) * (cz - pz);
            if (d < best) {
                best = d;
                at = shape.length() * (k + u) / (n - 1);
            }
        }
        return at;
    }

    public int taps() {
        int n = 0;
        for (Edge e : edges) {
            n += e.tapQuads.size();
        }
        return n;
    }

    public void strike(Arc arc) {
        arcs.add(arc);
    }

    void at(int tick) {
        this.tickOld = this.tick;
        this.tick = tick;
    }

    @Override
    public void tick() {
    }

    @Override
    public void render(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt) {
        final Minecraft mc = Minecraft.getInstance();
        if (scene == null) {
            return;
        }
        final double now = tickOld + (tick - tickOld) * pt;
        final TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(PowerRailRenderer.RAIL_TEXTURE);
        final VertexConsumer vc = buffer.getBuffer(RenderType.cutoutMipped());
        final Map<Long, Integer> lights = new HashMap<>();
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int tapIndex = 0;
        for (Edge e : edges) {
            final double chairs = reveal.chairs(e.index, now);
            final double rail = reveal.rail(e.index, now);
            ms.pushPose();
            ms.translate(e.origin[0], e.origin[1], e.origin[2]);
            final PoseStack.Pose pose = ms.last();
            for (int q = 0; q < e.quads.length; q++) {
                if (e.along[q] > (e.chair[q] ? chairs : rail)) {
                    continue;
                }
                emit(vc, pose, e.quads[q], sprite, scene, lights, probe);
            }
            for (int t = 0; t < e.tapQuads.size(); t++, tapIndex++) {
                final double since = now - reveal.tapAt(tapIndex);
                if (since < 0) {
                    continue;
                }
                final RailMesher.Quad[] own = e.tapQuads.get(t);
                final int shown = (int) Math.min(own.length, Math.ceil(own.length * Math.min(1.0, since / TAP_TICKS)));
                for (int q = 0; q < shown; q++) {
                    emit(vc, pose, own[q], sprite, scene, lights, probe);
                }
            }
            ms.popPose();
        }
        if (!arcs.isEmpty()) {
            final VertexConsumer lightning = buffer.getBuffer(RenderType.lightning());
            for (Arc arc : arcs) {
                final double age = tick - arc.tick() + pt;
                if (age < 0 || age >= ArcEffects.LIFE) {
                    continue;
                }
                final float fade = (float) Math.max(0.0, 1.0 - age / ArcEffects.LIFE);
                ms.pushPose();
                ms.translate(arc.bottom().x, arc.bottom().y, arc.bottom().z);
                ArcEffects.bolt(ms.last().pose(), lightning, arc.top().subtract(arc.bottom()), arc.seed(),
                        tick - arc.tick(), fade);
                ms.popPose();
            }
            arcs.removeIf(a -> tick - a.tick() >= ArcEffects.LIFE);
        }
    }

    private static void emit(VertexConsumer vc, PoseStack.Pose pose, RailMesher.Quad q, TextureAtlasSprite sprite,
                             PonderScene scene, Map<Long, Integer> lights, BlockPos.MutableBlockPos probe) {
        final long key = BlockPos.asLong(q.lightX(), q.lightY(), q.lightZ());
        Integer light = lights.get(key);
        if (light == null) {
            light = LevelRenderer.getLightColor(scene.getWorld(), probe.set(q.lightX(), q.lightY(), q.lightZ()));
            lights.put(key, light);
        }
        PowerRailRenderer.emit(vc, pose, q, sprite, light);
    }

    private PonderScene scene;

    public static KitInstruction show(Object slot, PowerTrackStage.Stage stage, Reveal reveal, RailPicture[] out,
                                      int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private RailPicture picture;

            @Override
            protected void begin(PonderScene scene) {
                picture = new RailPicture(stage, reveal);
                picture.scene = scene;
                picture.tick = start;
                picture.tickOld = start;
                out[0] = picture;
                scene.getOutliner().showOutline(slot, picture).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (picture == null) {
                    return;
                }
                picture.at(tick);
                if (!last) {
                    scene.getOutliner().keep(slot);
                }
            }
        };
    }

    public static Reveal together(int from, double speed, IntToDoubleFunction tapAt) {
        return new Reveal() {
            @Override
            public double chairs(int edge, double tick) {
                return Math.max(0.0, (tick - from) * speed);
            }

            @Override
            public double rail(int edge, double tick) {
                return Math.max(0.0, (tick - from) * speed);
            }

            @Override
            public int tapAt(int i) {
                return (int) tapAt.applyAsDouble(i);
            }
        };
    }

    public static Reveal all() {
        return together(Integer.MIN_VALUE / 4, 1.0e6, i -> Integer.MIN_VALUE / 4);
    }
}
