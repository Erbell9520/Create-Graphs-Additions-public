package com.createstats.client.ponder.coupler;

import com.createstats.CreateStats;
import com.createstats.block.UniversalPowerCouplerBlock;
import com.createstats.blockentity.UniversalPowerCouplerBE;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.client.ponder.SceneClock;
import com.createstats.power.CouplerEndpoint;
import com.createstats.power.CouplerFault;
import com.createstats.power.PowerCouplerMode;
import com.createstats.power.WireTier;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class CouplerPonder {
    private CouplerPonder() {
    }

    public static BlockPos pos(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    public static Vec3 px(double x, double y, double z) {
        final int[] c = CouplerPonderScript.COUPLER;
        return new Vec3(c[0] + x / 16.0, c[1] + y / 16.0, c[2] + z / 16.0);
    }

    public static KitInstruction drive(int start, int ticks) {
        final BlockPos at = pos(CouplerPonderScript.COUPLER);
        final BlockPos ours = pos(CouplerPonderScript.OURS);
        return new KitInstruction(start, ticks) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                SceneClock.set(level, tick);
                if (!(level.getBlockEntity(at) instanceof UniversalPowerCouplerBE be)) {
                    return;
                }
                final CouplerPonderScript.Frame f = CouplerPonderScript.at(tick);
                final PowerCouplerMode mode = switch (f.mode()) {
                    case PUSH -> PowerCouplerMode.PUSH;
                    case OFF -> PowerCouplerMode.OFF;
                    default -> PowerCouplerMode.PULL;
                };
                final BlockState state = level.getBlockState(at);
                if (state.hasProperty(UniversalPowerCouplerBlock.MODE)
                        && state.getValue(UniversalPowerCouplerBlock.MODE) != mode) {
                    final BlockState next = state.setValue(UniversalPowerCouplerBlock.MODE, mode);
                    level.setBlock(at, next, 0);
                    be.setBlockState(next);
                }
                final CompoundTag tag = new CompoundTag();
                tag.putInt("Energy", f.stored());
                tag.putInt("ImportFe", f.importFe());
                tag.putInt("ExportFe", f.exportFe());
                tag.putString("Direction", f.remembered());
                tag.putInt("Fault", CouplerFault.valueOf(f.fault()).ordinal());
                tag.putInt("Endpoint", CouplerEndpoint.valueOf(f.endpoint()).ordinal());
                tag.putBoolean("GridFace", false);
                tag.putBoolean("Looped", "LOOPED".equals(f.fault()));
                tag.putInt("Rating", 8192);
                final ListTag wires = new ListTag();
                if (f.wired()) {
                    final CompoundTag span = new CompoundTag();
                    span.putLong("Pos", ours.asLong());
                    span.putInt("Tier", WireTier.COPPER.ordinal());
                    span.putBoolean("Seam", false);
                    wires.add(span);
                }
                tag.put("SeamWires", wires);
                try {
                    be.readClient(tag, level.registryAccess());
                } catch (RuntimeException e) {
                    CreateStats.LOGGER.warn("[Ponder] the Coupler's state could not be set: {}", e.toString());
                }
            }
        };
    }

    public static KitInstruction orbit(float degrees, int start, int ticks) {
        return new KitInstruction(start, ticks + 1) {
            private float base;

            @Override
            protected void begin(PonderScene scene) {
                base = scene.getTransform().yRotation.getChaseTarget();
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final double u = Math.min(1.0, Math.max(0.0, (tick - start) / (double) ticks));
                final double k = u * u * (3.0 - 2.0 * u);
                scene.getTransform().yRotation.chase(base + degrees * k, 1.0, LerpedFloat.Chaser.EXP);
            }
        };
    }
}
