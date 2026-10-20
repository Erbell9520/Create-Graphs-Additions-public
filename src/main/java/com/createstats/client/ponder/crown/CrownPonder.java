package com.createstats.client.ponder.crown;

import com.createstats.CreateStats;
import com.createstats.block.ConnectorAnchor;
import com.createstats.blockentity.SparkCrownBE;
import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownRegion;
import com.createstats.chunkloader.core.CrownState;
import com.createstats.chunkloader.core.CrownStuds;
import com.createstats.chunkloader.ponder.CrownPonderScript;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.client.ponder.SceneClock;
import com.createstats.client.ponder.fe.FeFlow;
import com.createstats.client.ponder.fe.FePaths;
import com.createstats.power.MainBreakerStuds;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;
import com.createstats.renderer.PowerConnectorRenderer;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

public final class CrownPonder {
    public static final Direction FACING = Direction.SOUTH;

    static final int FED = 0xFFFFFF;
    static final int RIDING = 0xFFB347;
    static final int DARK = 0x9AA3AD;

    private CrownPonder() {
    }

    static BlockPos pos(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    public static Vec3 stud(int stud) {
        final double[] p = CrownStuds.attachPoint(CrownStuds.turnsFor(FACING.getName()), stud);
        final BlockPos c = pos(CrownPonderScript.CROWN);
        return new Vec3(c.getX() + p[0], c.getY() + p[1], c.getZ() + p[2]);
    }

    public static Vec3 connector(int[] at) {
        final Vec3 t = PowerConnectorRenderer.connectorTerminal(Direction.UP, ConnectorAnchor.CENTER);
        return new Vec3(at[0] + t.x, at[1] + t.y, at[2] + t.z);
    }

    public static Vec3 breakerStud(MainBreakerStuds.Stud stud) {
        final Vec3 a = MainBreakerStuds.anchor(Direction.UP, stud);
        return new Vec3(CrownPonderScript.BREAKER[0] + a.x, CrownPonderScript.BREAKER[1] + a.y,
                CrownPonderScript.BREAKER[2] + a.z);
    }

    static long[] picks(int n) {
        final long[] out = new long[Math.max(0, n)];
        for (int i = 0; i < out.length; i++) {
            final int[] t = CrownPonderScript.tile(i);
            out[i] = CrownRegion.pack(t[0], t[1]);
        }
        return out;
    }

    public static KitInstruction drive(int start, int ticks) {
        final BlockPos at = pos(CrownPonderScript.CROWN);
        final BlockPos farA = pos(CrownPonderScript.CONNECTOR_A);
        final BlockPos farB = pos(CrownPonderScript.CONNECTOR_B);
        return new KitInstruction(start, ticks) {
            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                SceneClock.set(level, tick);
                if (!(level.getBlockEntity(at) instanceof SparkCrownBE be)) {
                    return;
                }
                final CrownPonderScript.Frame f = CrownPonderScript.at(tick);
                final CompoundTag tag = new CompoundTag();
                tag.putInt("Tier", 0);
                tag.putInt("Lock", 0);
                if (f.lineA()) {
                    tag.putLong("Stud0", farA.asLong());
                    tag.putInt("StudTier0", WireTier.HV.ordinal());
                }
                if (f.lineB()) {
                    tag.putLong("Stud1", farB.asLong());
                    tag.putInt("StudTier1", WireTier.HV.ordinal());
                }
                tag.putString("OwnerName", "");
                tag.putLongArray("Crown", f.state().pack());
                tag.putString("Blocking", f.state().blockingId());
                tag.putLongArray("ClientPicks", picks(f.state().picked()));
                try {
                    be.readClient(tag, level.registryAccess());
                } catch (RuntimeException e) {
                    CreateStats.LOGGER.warn("[Ponder] the Spark Crown's state could not be set: {}", e.toString());
                }
            }
        };
    }

    public static KitInstruction power(Object slot, int start, int ticks) {
        final Vec3 a = connector(CrownPonderScript.CONNECTOR_A);
        final Vec3 b = connector(CrownPonderScript.CONNECTOR_B);
        final double slack = WireTension.STANDARD.slack();
        final FePaths.Path feedA = new FePaths.Path(FePaths.catenary(breakerStud(MainBreakerStuds.Stud.INPUT), a, slack));
        final FePaths.Path feedB = new FePaths.Path(FePaths.catenary(breakerStud(MainBreakerStuds.Stud.OUTPUT), b, slack));
        final FePaths.Path lineA = new FePaths.Path(FePaths.catenary(a, stud(0), slack));
        final FePaths.Path lineB = new FePaths.Path(FePaths.catenary(b, stud(1), slack));
        return FeFlow.beads(slot, List.of(
                FeFlow.Stream.wire(feedA, t -> share(t, 0), WireTier.HV, t -> true),
                FeFlow.Stream.wire(feedB, t -> share(t, 1), WireTier.HV, t -> true),
                FeFlow.Stream.wire(lineA, t -> share(t, 0), WireTier.HV, t -> CrownPonderScript.landed(0, t)),
                FeFlow.Stream.wire(lineB, t -> share(t, 1), WireTier.HV, t -> CrownPonderScript.landed(1, t))),
                FeFlow.GREEN, start, ticks);
    }

    public static double share(int tick, int stud) {
        final CrownState s = CrownPonderScript.at(tick).state();
        return CrownStuds.split(s.intakeFePerTick(), s.studARating(), s.studBRating(), s.studAWired(),
                s.studBWired())[stud];
    }

    public static String drawText(int tick) {
        return String.format(Locale.ROOT, "%,d FE/t", CrownPonderScript.at(tick).state().drawFePerTick());
    }

    public static int drawColour(int tick) {
        final CrownState s = CrownPonderScript.at(tick).state();
        return s.held() <= 0 ? DARK : s.phase() == CrownHold.Phase.RIDING ? RIDING : FED;
    }
}
