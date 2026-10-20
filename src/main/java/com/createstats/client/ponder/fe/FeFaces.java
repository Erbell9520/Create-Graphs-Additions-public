package com.createstats.client.ponder.fe;

import com.createstats.CreateStats;
import com.createstats.client.ponder.KitInstruction;
import com.createstats.power.FeLock;
import com.createstats.power.PduMode;
import com.createstats.power.PduReason;
import com.createstats.power.ponder.FePonderChapters.Warp;
import com.createstats.power.ponder.FePonderPlant.Recording;
import com.createstats.renderer.PowerDistributionUnitTextureManager;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

public final class FeFaces {
    private FeFaces() {
    }

    public static KitInstruction pdu(BlockPos board, Recording rec, Warp warp, IntUnaryOperator on, IntPredicate tiered,
                                     int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private int signature = Integer.MIN_VALUE;
            private int lastRefresh = Integer.MIN_VALUE / 2;

            @Override
            protected void begin(PonderScene scene) {
                PowerDistributionUnitTextureManager.disposeAll();
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                final BlockEntity be = level.getBlockEntity(board);
                if (be == null) return;
                final int r = rec.at(warp.tick(tick));
                final int legs = Math.max(0, Math.min(rec.machines(), on.applyAsInt(tick)));
                final boolean tiers = tiered.test(tick);
                final CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                final PduMode mode = rec.mode[r];
                tag.putInt("Mode", mode.ordinal());
                tag.putInt("Energy", rec.energy[r]);
                tag.putInt("Cap", rec.setup.tuning().bufferCapacity());
                tag.putInt("Flr", rec.setup.tuning().floorPct());
                tag.putInt("Upk", rec.setup.tuning().upkeep());
                tag.putInt("Gen", rec.generation[r]);
                tag.putInt("Dmd", rec.demandShown[r]);
                tag.putInt("Svd", rec.servedDemand[r]);
                tag.putInt("Bud", rec.runBudget[r] == Integer.MAX_VALUE ? 0 : rec.runBudget[r]);
                final ListTag roster = new ListTag();
                int served = 0;
                int shed = 0;
                final int[] total = new int[5];
                final int[] lit = new int[5];
                for (int i = 0; i < legs; i++) {
                    if (!rec.connected[i][r]) continue;
                    final PduReason reason = rec.reason[i][r];
                    final boolean isShed = reason.isShed() && !rec.admitted[i][r];
                    final CompoundTag e = new CompoundTag();
                    e.putLong("P", FeStage.MACHINE[i].asLong());
                    e.putString("Nk", "");
                    e.putString("N", rec.machine(i).name());
                    e.putInt("Id", i);
                    e.putInt("Leg", FeStage.STUD[i]);
                    final int tier = tiers ? rec.tier[i][r] : com.createstats.power.FePriority.DEFAULT.ordinal();
                    e.putInt("T", tier);
                    e.putInt("D", rec.admitted[i][r] ? rec.machine(i).draw() : 0);
                    e.putInt("C", rec.machine(i).draw());
                    e.putBoolean("S", isShed);
                    e.putInt("L", FeLock.NONE.ordinal());
                    e.putInt("R", reason.ordinal());
                    e.putBoolean("Lt", false);
                    roster.add(e);
                    total[Math.max(0, Math.min(4, tier))]++;
                    if (!isShed) lit[Math.max(0, Math.min(4, tier))]++;
                }
                tag.put("Roster", roster);

                served = legs < rec.machines() ? countServed(rec, r, legs) : rec.servedCount[r];
                shed = legs < rec.machines() ? countShed(rec, r, legs) : rec.shedCount[r];
                tag.putInt("Served", served);
                tag.putInt("Shed", shed);
                be.loadWithComponents(tag, level.registryAccess());

                int sig = mode.ordinal();
                sig = sig * 31 + (rec.energy[r] <= 0 ? 1 : 0);
                sig = sig * 31 + Math.round(Math.min(1f, rec.fill(r)) * 1024f);
                if (mode == PduMode.AUTO) {
                    sig = sig * 31 + rec.generation[r];
                    sig = sig * 31 + rec.demandShown[r];
                    sig = sig * 31 + rec.servedDemand[r];
                }
                boolean blinking = false;
                for (int t = 0; t < 5; t++) {
                    sig = sig * 31 + total[t];
                    sig = sig * 31 + lit[t];
                    blinking |= total[t] > 0 && lit[t] == 0 && rec.energy[r] > 0;
                }
                sig = sig * 31 + served;
                sig = sig * 31 + shed;
                if (blinking) sig = sig * 31 + (int) ((System.currentTimeMillis() / 900L) % 2L);
                if (sig != signature && tick - lastRefresh >= 2) {
                    signature = sig;
                    lastRefresh = tick;
                    try {
                        PowerDistributionUnitTextureManager.disposeAll();
                    } catch (RuntimeException e) {
                        CreateStats.LOGGER.warn("[Ponder] the board's face could not be refreshed: {}", e.toString());
                    }
                }
            }
        };
    }

    public static KitInstruction controller(BlockPos pos, com.createstats.power.ponder.FePonderController.Recording rec,
                                            Warp warp, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private int signature = Integer.MIN_VALUE;
            private int lastRefresh = Integer.MIN_VALUE / 2;

            @Override
            protected void begin(PonderScene scene) {
                com.createstats.renderer.BatteryControllerTextureManager.disposeAll();
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                final BlockEntity be = level.getBlockEntity(pos);
                if (be == null) return;
                final int r = rec.at(warp.tick(tick));
                final com.createstats.power.ponder.FePonderController.Setup s = rec.setup;
                final CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                int cap = 0;
                for (int b = 0; b < s.banks().length; b++) if (rec.wired(1 + b) <= r) cap += s.banks()[b];
                tag.putInt("Energy", rec.bus[r]);
                tag.putInt("Reserve", rec.reserve[r]);
                tag.putInt("ReserveCap", cap);
                tag.putInt("Gen", rec.generation[r]);
                tag.putInt("Delivered", rec.delivered[r]);
                tag.putInt("Rate", rec.rate[r]);
                tag.putInt("Claim", rec.claim[r]);
                tag.putInt("Stance", rec.stance[r].ordinal());
                tag.putInt("Sustain", rec.sustained[r]);
                tag.putInt("CritDraw", rec.critical[r]);
                tag.putInt("BandFloor", s.band().floorPct());
                tag.putInt("BandCeil", s.band().ceilingPct());
                tag.putInt("BandHealthy", s.band().healthyPct());
                tag.putInt("CfgFloor", s.band().floorPct());
                tag.putInt("RideT", s.rideTicks());
                tag.putInt("ClaimPct", s.claimPct());
                for (int i = 0; i < s.outputs().size(); i++) {
                    final com.createstats.power.ponder.FePonderController.Output o = s.outputs().get(i);
                    final CompoundTag out = new CompoundTag();
                    out.putBoolean("On", true);
                    out.putInt("Mode", com.createstats.power.BatteryOutputMode.DIRECT.ordinal());
                    out.putInt("Limit", 0);
                    out.putInt("Floor", o.floorPct());
                    out.putInt("Prio", o.priority().ordinal());
                    out.putBoolean("Held", rec.held[i][r]);
                    out.putInt("Flow", rec.flow[i][r]);
                    out.putInt("Want", rec.want[i][r]);
                    out.putInt("Ceil", s.portCeiling());
                    out.putInt("Devs", 1);
                    out.putInt("Why", rec.why[i][r].ordinal());
                    tag.put("Out" + i, out);
                }
                for (int i = 0; i < 3; i++) {
                    final CompoundTag in = new CompoundTag();
                    in.putBoolean("On", true);
                    in.putInt("Flow", i == 0 ? rec.generation[r] : 0);
                    in.putInt("Tanks", i == 0 ? 0 : 1);
                    in.putInt("Srcs", i == 0 ? 1 : 0);
                    in.putInt("BMove", i == 0 ? 0 : Math.abs(rec.bankFlow[i - 1][r]));
                    tag.put("In" + i, in);
                }
                be.loadWithComponents(tag, level.registryAccess());

                int sig = cap > 0 ? (int) ((long) rec.reserve[r] * 100L / cap) : -1;
                sig = sig * 31 + Integer.signum(rec.rate[r]);
                sig = sig * 31 + rec.rate[r] / 10;
                sig = sig * 31 + rec.generation[r];
                sig = sig * 31 + rec.delivered[r];
                sig = sig * 31 + rec.stance[r].ordinal();
                for (int i = 0; i < s.outputs().size(); i++) sig = sig * 31 + rec.why[i][r].ordinal();
                if (sig != signature && tick - lastRefresh >= 2) {
                    signature = sig;
                    lastRefresh = tick;
                    try {
                        com.createstats.renderer.BatteryControllerTextureManager.disposeAll();
                    } catch (RuntimeException e) {
                        CreateStats.LOGGER.warn("[Ponder] the controller's face could not be refreshed: {}", e.toString());
                    }
                }
            }
        };
    }

    public static KitInstruction board(BlockPos pos, com.createstats.power.ponder.FePonderDeal.Recording rec, int b,
                                       BlockPos[] machines, int[] studs, Warp warp, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private int signature = Integer.MIN_VALUE;
            private int lastRefresh = Integer.MIN_VALUE / 2;

            @Override
            protected void begin(PonderScene scene) {
                PowerDistributionUnitTextureManager.disposeAll();
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                final BlockEntity be = level.getBlockEntity(pos);
                if (be == null) return;
                final int r = rec.at(warp.tick(tick));
                final com.createstats.power.ponder.FePonderPlant.Tuning tune = rec.setup.boardTuning();
                final java.util.List<com.createstats.power.ponder.FePonderDeal.Device> devices =
                        rec.setup.boards().get(b).devices();
                final CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                tag.putInt("Mode", PduMode.AUTO.ordinal());
                tag.putInt("Energy", rec.boardEnergy[b][r]);
                tag.putInt("Cap", tune.bufferCapacity());
                tag.putInt("Flr", tune.floorPct());
                tag.putInt("Upk", tune.upkeep());
                tag.putInt("Gen", rec.intake[b][r]);
                int demand = 0;
                int served = 0;
                int shed = 0;
                int admittedDemand = 0;
                final ListTag roster = new ListTag();
                final int[] total = new int[5];
                final int[] lit = new int[5];
                for (int d = 0; d < devices.size(); d++) {
                    final com.createstats.power.ponder.FePonderDeal.Device dev = devices.get(d);
                    final boolean on = rec.admitted[b][d][r];
                    final PduReason reason = rec.why[b][d][r] == null ? PduReason.RUNNING : rec.why[b][d][r];
                    final CompoundTag e = new CompoundTag();
                    e.putLong("P", machines[d].asLong());
                    e.putString("Nk", "");
                    e.putString("N", dev.load().name().toLowerCase(java.util.Locale.ROOT));
                    e.putInt("Id", d);
                    e.putInt("Leg", studs[d]);
                    e.putInt("T", dev.tier().ordinal());
                    e.putInt("D", on ? dev.load().draw() : 0);
                    e.putInt("C", dev.load().draw());
                    e.putBoolean("S", !on);
                    e.putInt("L", FeLock.NONE.ordinal());
                    e.putInt("R", reason.ordinal());
                    e.putBoolean("Lt", false);
                    roster.add(e);
                    demand += dev.load().draw();
                    if (on) {
                        served++;
                        admittedDemand += dev.load().draw();
                        lit[dev.tier().ordinal()]++;
                    } else {
                        shed++;
                    }
                    total[dev.tier().ordinal()]++;
                }
                tag.put("Roster", roster);
                tag.putInt("Dmd", demand);
                tag.putInt("Svd", admittedDemand);
                tag.putInt("Bud", rec.granted(b, r));
                tag.putInt("Served", served);
                tag.putInt("Shed", served > 0 ? shed : 0);
                be.loadWithComponents(tag, level.registryAccess());

                int sig = Math.round(Math.min(1f, rec.boardEnergy[b][r] / (float) tune.bufferCapacity()) * 1024f);
                sig = sig * 31 + rec.intake[b][r];
                sig = sig * 31 + demand;
                sig = sig * 31 + admittedDemand;
                for (int t = 0; t < 5; t++) sig = sig * 31 + total[t] * 7 + lit[t];
                if (sig != signature && tick - lastRefresh >= 2) {
                    signature = sig;
                    lastRefresh = tick;
                    try {
                        PowerDistributionUnitTextureManager.disposeAll();
                    } catch (RuntimeException e) {
                        CreateStats.LOGGER.warn("[Ponder] a board's face could not be refreshed: {}", e.toString());
                    }
                }
            }
        };
    }

    public static KitInstruction dealController(BlockPos pos, com.createstats.power.ponder.FePonderDeal.Recording rec,
                                                Warp warp, int start, int ticks) {
        return new KitInstruction(start, ticks) {
            private int signature = Integer.MIN_VALUE;
            private int lastRefresh = Integer.MIN_VALUE / 2;

            @Override
            protected void begin(PonderScene scene) {
                com.createstats.renderer.BatteryControllerTextureManager.disposeAll();
            }

            @Override
            protected void step(PonderScene scene, int tick, boolean last) {
                final PonderLevel level = scene.getWorld();
                final BlockEntity be = level.getBlockEntity(pos);
                if (be == null) return;
                final int r = rec.at(warp.tick(tick));
                final com.createstats.power.ponder.FePonderDeal.Setup s = rec.setup;
                final CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
                final int rate = r == 0 ? 0 : rec.reserve[r] - rec.reserve[r - 1];
                int delivered = 0;
                for (int b = 0; b < s.boards().size(); b++) delivered += rec.intake[b][r];
                tag.putInt("Energy", 0);
                tag.putInt("Reserve", rec.reserve[r]);
                tag.putInt("ReserveCap", rec.capacity());
                tag.putInt("Gen", 0);
                tag.putInt("Delivered", delivered);
                tag.putInt("Rate", rate);
                tag.putInt("Claim", 0);
                tag.putInt("Stance", rec.stance[r].ordinal());
                tag.putInt("BandFloor", s.band().floorPct());
                tag.putInt("BandCeil", s.band().ceilingPct());
                tag.putInt("BandHealthy", s.band().healthyPct());
                tag.putInt("CfgFloor", s.band().floorPct());
                tag.putInt("RideT", s.rideTicks());
                tag.putInt("ClaimPct", s.claimPct());
                for (int i = 0; i < 3; i++) {
                    final CompoundTag out = new CompoundTag();
                    final boolean board = i < s.boards().size();
                    out.putBoolean("On", true);
                    out.putInt("Mode", com.createstats.power.BatteryOutputMode.DIRECT.ordinal());
                    out.putInt("Floor", 0);
                    out.putInt("Prio", com.createstats.power.FePriority.NORMAL.ordinal());
                    out.putInt("Flow", board ? rec.intake[i][r] : 0);
                    out.putInt("Want", board ? rec.asked(i, r) : 0);
                    out.putInt("Ceil", board ? s.portCeiling() : 0);
                    out.putInt("Devs", board ? 1 : 0);
                    out.putInt("Why", (board ? com.createstats.power.BatteryControllerPlan.Reason.SERVED
                            : com.createstats.power.BatteryControllerPlan.Reason.UNWIRED).ordinal());
                    out.putInt("Panels", board ? 1 : 0);
                    out.putInt("Crit", board ? rec.askCritical[i][r] : 0);
                    out.putInt("BAsk", board ? rec.asked(i, r) : 0);
                    out.putInt("BGot", board ? rec.granted(i, r) : 0);
                    out.putInt("BWhy", board ? rec.reason[i][r].ordinal() : 0);
                    tag.put("Out" + i, out);
                }
                final com.createstats.power.ReserveProjection.Result outlook = rec.outlook[r];
                tag.putByteArray("FcTrace", outlook.trace());
                tag.putIntArray("FcMarks", outlook.packMilestones());
                tag.putBoolean("FcNow", outlook.shortNow());
                tag.putIntArray("FcSupply", outlook.supply());
                be.loadWithComponents(tag, level.registryAccess());

                int sig = rec.capacity() > 0 ? (int) ((long) rec.reserve[r] * 100L / rec.capacity()) : -1;
                sig = sig * 31 + Integer.signum(rate);
                sig = sig * 31 + delivered;
                sig = sig * 31 + rec.stance[r].ordinal();
                sig = sig * 31 + java.util.Arrays.hashCode(outlook.packMilestones());
                sig = sig * 31 + (outlook.shortNow() ? 1 : 0);
                if (sig != signature && tick - lastRefresh >= 2) {
                    signature = sig;
                    lastRefresh = tick;
                    try {
                        com.createstats.renderer.BatteryControllerTextureManager.disposeAll();
                    } catch (RuntimeException e) {
                        CreateStats.LOGGER.warn("[Ponder] the controller's face could not be refreshed: {}", e.toString());
                    }
                }
            }
        };
    }

    private static int countServed(Recording rec, int r, int legs) {
        int n = 0;
        for (int i = 0; i < legs; i++) if (rec.connected[i][r] && rec.admitted[i][r] && rec.fed[i][r] > 0) n++;
        return n;
    }

    private static int countShed(Recording rec, int r, int legs) {
        int n = 0;
        for (int i = 0; i < legs; i++) if (rec.connected[i][r] && !rec.admitted[i][r]) n++;
        return n;
    }
}
