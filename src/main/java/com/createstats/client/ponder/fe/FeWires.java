package com.createstats.client.ponder.fe;

import com.createstats.CreateStats;
import com.createstats.client.ponder.Ease;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;
import com.createstats.renderer.PowerConnectorRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class FeWires {
    private FeWires() {
    }

    static final class Growing extends Outline {
        List<Vec3> curve = List.of();
        WireTier tier = WireTier.COPPER;
        float grown;
        float prevGrown;
        boolean done;

        int light = LightTexture.FULL_BRIGHT;

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

    public static KitInstruction unspool(Object slot, BlockPos board, Direction facing, int stud, BlockPos far,
                                         WireTier tier, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Growing wire;

            @Override
            protected void begin(PonderScene scene) {
                wire = new Growing();
                wire.tier = tier;
                wire.curve = FePaths.catenary(FePaths.studPoint(board, facing, stud),
                        FePaths.attach(scene.getWorld(), far), WireTension.STANDARD.slack());
                wire.light = net.minecraft.client.renderer.LevelRenderer.getLightColor(scene.getWorld(), board);
                scene.getOutliner().showOutline(slot, wire).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (wire == null) return;
                wire.prevGrown = wire.grown;
                wire.grown = Math.min(1f, (tick - start + 1) / (float) Math.max(1, totalTicks - 1));
                if (last) {
                    strung(scene.getWorld(), board, stud, far, tier);
                    wire.done = true;
                } else {
                    scene.getOutliner().keep(slot);
                }
            }
        };
    }

    static void strung(PonderLevel level, BlockPos board, int stud, BlockPos far, WireTier tier) {
        try {
            final BlockEntity pdu = level.getBlockEntity(board);
            if (pdu != null) {
                final CompoundTag tag = pdu.saveWithoutMetadata(level.registryAccess());
                final ListTag spans = tag.getList("StudWires", Tag.TAG_COMPOUND);
                final CompoundTag e = new CompoundTag();
                e.putByte("S", (byte) stud);
                e.putLong("P", far.asLong());
                e.putByte("T", (byte) tier.ordinal());
                spans.add(e);
                tag.put("StudWires", spans);
                pdu.loadWithComponents(tag, level.registryAccess());
            }
            final BlockEntity end = level.getBlockEntity(far);
            if (end != null) {
                final CompoundTag tag = end.saveWithoutMetadata(level.registryAccess());
                final long[] links = tag.getLongArray("Links");
                final byte[] tiers = tag.getByteArray("LinkTiers");
                final long[] nl = java.util.Arrays.copyOf(links, links.length + 1);
                final byte[] nt = java.util.Arrays.copyOf(tiers, links.length + 1);
                nl[links.length] = board.asLong();
                nt[links.length] = (byte) tier.ordinal();
                tag.putLongArray("Links", nl);
                tag.putByteArray("LinkTiers", nt);
                if (tag.contains("LinkTension")) {
                    final byte[] ten = tag.getByteArray("LinkTension");
                    final byte[] nx = java.util.Arrays.copyOf(ten, links.length + 1);
                    nx[links.length] = (byte) -1;
                    tag.putByteArray("LinkTension", nx);
                }
                end.loadWithComponents(tag, level.registryAccess());
            }
        } catch (RuntimeException e) {
            CreateStats.LOGGER.warn("[Ponder] a wire could not be strung: {}", e.toString());
        }
    }

    public static KitInstruction port(Object slot, BlockPos controller, int port, Vec3 from, BlockPos far, WireTier tier,
                                      int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Growing wire;

            @Override
            protected void begin(PonderScene scene) {
                wire = new Growing();
                wire.tier = tier;
                wire.curve = FePaths.catenary(from, FePaths.attach(scene.getWorld(), far), WireTension.STANDARD.slack());
                wire.light = net.minecraft.client.renderer.LevelRenderer.getLightColor(scene.getWorld(), controller);
                scene.getOutliner().showOutline(slot, wire).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (wire == null) return;
                wire.prevGrown = wire.grown;
                wire.grown = Math.min(1f, (tick - start + 1) / (float) Math.max(1, totalTicks - 1));
                if (last) {
                    ported(scene.getWorld(), controller, port, far, tier);
                    wire.done = true;
                } else {
                    scene.getOutliner().keep(slot);
                }
            }
        };
    }

    static void ported(PonderLevel level, BlockPos controller, int port, BlockPos far, WireTier tier) {
        try {
            final BlockEntity bc = level.getBlockEntity(controller);
            if (bc != null) {
                final CompoundTag tag = bc.saveWithoutMetadata(level.registryAccess());
                final ListTag ports = tag.getList("Ports", Tag.TAG_COMPOUND);
                while (ports.size() < com.createstats.power.BatteryControllerPorts.COUNT) ports.add(new CompoundTag());
                final CompoundTag p = ports.getCompound(port);
                final long[] to = p.getLongArray("To");
                final byte[] tiers = p.getByteArray("Tiers");
                final long[] nt = java.util.Arrays.copyOf(to, to.length + 1);
                final byte[] nr = java.util.Arrays.copyOf(tiers, to.length + 1);
                nt[to.length] = far.asLong();
                nr[to.length] = (byte) tier.ordinal();
                p.putLongArray("To", nt);
                p.putByteArray("Tiers", nr);
                ports.set(port, p);
                tag.put("Ports", ports);
                bc.loadWithComponents(tag, level.registryAccess());
            }
        } catch (RuntimeException e) {
            CreateStats.LOGGER.warn("[Ponder] a controller's terminal could not be wired: {}", e.toString());
        }
        edit(level, far, s -> s.add(controller, tier, WireTension.STANDARD));
    }

    public static KitInstruction span(Object slot, BlockPos from, BlockPos to, WireTier tier, WireTension tension,
                                      int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private Growing wire;

            @Override
            protected void begin(PonderScene scene) {
                wire = new Growing();
                wire.tier = tier;
                wire.curve = FePaths.catenary(FePaths.attach(scene.getWorld(), from), FePaths.attach(scene.getWorld(), to),
                        tension.slack());
                wire.light = net.minecraft.client.renderer.LevelRenderer.getLightColor(scene.getWorld(), from);
                scene.getOutliner().showOutline(slot, wire).lineWidth(1 / 32f);
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                if (wire == null) return;
                wire.prevGrown = wire.grown;
                wire.grown = Math.min(1f, (tick - start + 1) / (float) Math.max(1, totalTicks - 1));
                if (last) {
                    linked(scene.getWorld(), from, to, tier, tension);
                    wire.done = true;
                } else {
                    scene.getOutliner().keep(slot);
                }
            }
        };
    }

    static void linked(PonderLevel level, BlockPos a, BlockPos b, WireTier tier, WireTension tension) {
        edit(level, a, links -> links.add(b, tier, tension));
        edit(level, b, links -> links.add(a, tier, tension));
    }

    static final class Spans {
        final List<Long> to = new ArrayList<>();
        final List<Byte> tiers = new ArrayList<>();
        final List<Byte> tension = new ArrayList<>();

        void add(BlockPos far, WireTier tier, WireTension t) {
            final int at = to.indexOf(far.asLong());
            if (at >= 0) {
                tiers.set(at, (byte) tier.ordinal());
                tension.set(at, (byte) t.id());
                return;
            }
            to.add(far.asLong());
            tiers.add((byte) tier.ordinal());
            tension.add((byte) t.id());
        }

        void remove(BlockPos far) {
            final int at = to.indexOf(far.asLong());
            if (at < 0) return;
            to.remove(at);
            tiers.remove(at);
            tension.remove(at);
        }

        void tension(BlockPos far, WireTension t) {
            final int at = to.indexOf(far.asLong());
            if (at >= 0) tension.set(at, (byte) t.id());
        }
    }

    private static void edit(PonderLevel level, BlockPos pos, java.util.function.Consumer<Spans> change) {
        try {
            final BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return;
            final CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
            final Spans s = new Spans();
            final long[] links = tag.getLongArray("Links");
            final byte[] tiers = tag.getByteArray("LinkTiers");
            final byte[] tension = tag.getByteArray("LinkTension");
            for (int i = 0; i < links.length; i++) {
                s.to.add(links[i]);
                s.tiers.add(i < tiers.length ? tiers[i] : (byte) WireTier.COPPER.ordinal());
                s.tension.add(i < tension.length ? tension[i] : (byte) -1);
            }
            change.accept(s);
            final long[] nl = new long[s.to.size()];
            final byte[] nt = new byte[s.to.size()];
            final byte[] nx = new byte[s.to.size()];
            for (int i = 0; i < nl.length; i++) {
                nl[i] = s.to.get(i);
                nt[i] = s.tiers.get(i);
                nx[i] = s.tension.get(i);
            }
            tag.putLongArray("Links", nl);
            tag.putByteArray("LinkTiers", nt);
            tag.putByteArray("LinkTension", nx);
            be.loadWithComponents(tag, level.registryAccess());
        } catch (RuntimeException e) {
            CreateStats.LOGGER.warn("[Ponder] a terminal's spans could not be written: {}", e.toString());
        }
    }

    public static KitInstruction strip(BlockPos at, int start) {
        return new KitInstruction(start, 1) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                final List<BlockPos> ends = new ArrayList<>();
                edit(level, at, s -> {
                    for (long p : s.to) ends.add(BlockPos.of(p));
                    s.to.clear();
                    s.tiers.clear();
                    s.tension.clear();
                });
                for (BlockPos far : ends) edit(level, far, s -> s.remove(at));
            }
        };
    }

    public static KitInstruction tension(BlockPos a, BlockPos b, WireTension tension, int start) {
        return new KitInstruction(start, 1) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                edit(scene.getWorld(), a, s -> s.tension(b, tension));
                edit(scene.getWorld(), b, s -> s.tension(a, tension));
            }
        };
    }

    public static KitInstruction cleared(BlockPos[] terminals, int start) {
        return new KitInstruction(start, 1) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                for (BlockPos p : terminals) {
                    edit(scene.getWorld(), p, s -> {
                        s.to.clear();
                        s.tiers.clear();
                        s.tension.clear();
                    });
                }
            }
        };
    }

    public static KitInstruction unstrung(BlockPos board, BlockPos[] ends, int start) {
        return new KitInstruction(start, 1) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                try {
                    final BlockEntity pdu = level.getBlockEntity(board);
                    if (pdu != null) {
                        final CompoundTag tag = pdu.saveWithoutMetadata(level.registryAccess());
                        tag.put("StudWires", new ListTag());
                        pdu.loadWithComponents(tag, level.registryAccess());
                    }
                    for (BlockPos far : ends) {
                        final BlockEntity end = level.getBlockEntity(far);
                        if (end == null) continue;
                        final CompoundTag tag = end.saveWithoutMetadata(level.registryAccess());
                        tag.putLongArray("Links", new long[0]);
                        tag.putByteArray("LinkTiers", new byte[0]);
                        if (tag.contains("LinkTension")) tag.putByteArray("LinkTension", new byte[0]);
                        end.loadWithComponents(tag, level.registryAccess());
                    }
                } catch (RuntimeException e) {
                    CreateStats.LOGGER.warn("[Ponder] the board's wires could not be taken off: {}", e.toString());
                }
            }
        };
    }
}
