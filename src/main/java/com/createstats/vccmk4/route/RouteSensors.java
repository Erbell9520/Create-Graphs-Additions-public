package com.createstats.vccmk4.route;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.surveyor.PathwayChecks;
import com.createstats.surveyor.core.PathProjection;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.StopRules;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class RouteSensors {
    public static final double MARGIN = 3.0;
    public static final double MAX_SWEEP = 32.0;

    public record Obstacle(double distance, boolean player, String what, boolean block) {
        public static final Obstacle NONE = new Obstacle(Double.POSITIVE_INFINITY, false, "", false);

        public Obstacle(double distance, boolean player, String what) {
            this(distance, player, what, false);
        }
    }

    private RouteSensors() {
    }

    public static boolean isHostile(Entity entity) {
        return entity instanceof Enemy;
    }

    public static Obstacle sweep(ServerLevel level, PathSpline sp, double s, double front, double half, double reach,
                                 List<UUID> own, List<double[]> ownBounds) {
        double look = Math.min(MAX_SWEEP, Math.max(4.0, reach));
        double from = s + front;
        Obstacle best = Obstacle.NONE;

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (double d = 0.0; d <= look; d += 1.0) {
            double at = sp.closed() ? from + d : Math.min(sp.length(), from + d);
            minX = Math.min(minX, sp.x(at));
            maxX = Math.max(maxX, sp.x(at));
            minY = Math.min(minY, sp.y(at));
            maxY = Math.max(maxY, sp.y(at));
            minZ = Math.min(minZ, sp.z(at));
            maxZ = Math.max(maxZ, sp.z(at));
        }
        AABB box = new AABB(minX - half - 1, minY - 2, minZ - half - 1, maxX + half + 1, maxY + 4, maxZ + half + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
            if (isHostile(e) || onOwn(e, ownBounds)) continue;
            PathProjection.Result r = PathProjection.window(sp, e.getX(), e.getY(), e.getZ(), from + look * 0.5, look * 0.5 + 2.0, look * 0.5 + 2.0);
            if (!r.found() || Math.abs(r.dy()) > 3.0) continue;
            double width = e.getBbWidth() * 0.5;
            if (Math.abs(r.lateral()) > half + width + 0.3) continue;
            double ahead = sp.closed() ? sp.signedDelta(from, r.s()) : r.s() - from;
            if (ahead < -1.0 || ahead > look) continue;
            ahead -= width;
            if (ahead < best.distance()) {
                boolean player = e instanceof Player;
                best = new Obstacle(Math.max(0.0, ahead), player, player ? "a player (" + e.getName().getString() + ")" : e.getName().getString());
            }
        }

        for (Object sub : SableBridge.listSubLevels(level)) {
            UUID id = SableBridge.subLevelId(sub);
            if (id == null || own.contains(id)) continue;
            double[] b = RouteReflect.worldBounds(sub);
            if (b == null || b[3] < box.minX || b[0] > box.maxX || b[5] < box.minZ || b[2] > box.maxZ) continue;
            for (double d = 0.0; d <= look && d < best.distance(); d += 0.5) {
                double at = sp.closed() ? from + d : Math.min(sp.length(), from + d);
                double x = sp.x(at);
                double y = sp.y(at);
                double z = sp.z(at);
                if (x + half < b[0] || x - half > b[3] || z + half < b[2] || z - half > b[5]) continue;
                if (y + 3.0 < b[1] || y - 0.5 > b[4]) continue;
                best = new Obstacle(d, false, "another vehicle");
                break;
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double d = 0.5; d <= look && d < best.distance(); d += 0.5) {
            double at = sp.closed() ? from + d : Math.min(sp.length(), from + d);
            double x = sp.x(at);
            double z = sp.z(at);
            double g = PathwayChecks.groundAt(level, x, sp.y(at), z, pos);
            if (Double.isNaN(g)) continue;
            double[] t = sp.tangent(at);
            for (double w = -half + 0.5; w <= half - 0.5 + 1.0e-6; w += 1.0) {
                int bx = (int) Math.floor(x - t[1] * w);
                int bz = (int) Math.floor(z + t[0] * w);
                for (int h = 1; h <= 2; h++) {
                    int by = (int) Math.floor(g + 0.01) + h;
                    pos.set(bx, by, bz);
                    if (!level.isLoaded(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) continue;
                    String name = state.getBlock().getName().getString().toLowerCase(Locale.ROOT);
                    return new Obstacle(d, false, name + " at " + bx + " " + by + " " + bz, true);
                }
            }
        }
        return best;
    }

    private static boolean onOwn(LivingEntity e, List<double[]> ownBounds) {
        double x = e.getX();
        double y = e.getY();
        double z = e.getZ();
        for (double[] b : ownBounds) {
            if (x >= b[0] - 0.5 && x <= b[3] + 0.5 && y >= b[1] - 1.0 && y <= b[4] + 1.0 && z >= b[2] - 0.5 && z <= b[5] + 0.5) return true;
        }
        return false;
    }

    public static final class CargoWatch implements StopRules.Cargo {
        private List<IItemHandler> itemHandlers = List.of();
        private List<IFluidHandler> fluidHandlers = List.of();
        private long checksum = Long.MIN_VALUE;
        private int idle;
        private long lastRead = Long.MIN_VALUE;
        private long lastTick = Long.MIN_VALUE;
        private final java.util.Map<String, Long> itemCache = new java.util.HashMap<>();
        private final java.util.Map<String, Long> fluidCache = new java.util.HashMap<>();
        private int totalItems = -1;
        @Nullable private ServerLevel level;

        private long dayTime = -1L;
        private int seated;
        private boolean stopPowered;
        private boolean docked;

        public void readings(long dayTime, int seated, boolean stopPowered, boolean docked) {
            this.dayTime = dayTime;
            this.seated = seated;
            this.stopPowered = stopPowered;
            this.docked = docked;
        }

        @Override
        public long dayTime() {
            return dayTime;
        }

        @Override
        public int seated() {
            return seated;
        }

        @Override
        public boolean stopPowered() {
            return stopPowered;
        }

        @Override
        public boolean docked() {
            return docked;
        }

        public void tick(ServerLevel level, List<BlockEntity> containers, long now) {
            this.level = level;
            if (lastTick != Long.MIN_VALUE) idle = (int) Math.min(Integer.MAX_VALUE, idle + Math.max(0L, now - lastTick));
            lastTick = now;
            if (now - lastRead < 20 && lastRead != Long.MIN_VALUE) return;
            int elapsed = lastRead == Long.MIN_VALUE ? 0 : (int) Math.min(20, now - lastRead);
            lastRead = now;
            itemCache.clear();
            fluidCache.clear();
            java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            List<IItemHandler> itemList = new java.util.ArrayList<>();
            List<IFluidHandler> fluidList = new java.util.ArrayList<>();
            for (BlockEntity be : containers) {
                IItemHandler items = itemHandler(level, be);
                if (items != null && seen.add(items)) itemList.add(items);
                IFluidHandler fluids = fluidHandler(level, be);
                if (fluids != null && seen.add(fluids)) fluidList.add(fluids);
            }
            itemHandlers = itemList;
            fluidHandlers = fluidList;
            long sum = 17;
            int total = 0;
            boolean any = !itemList.isEmpty() || !fluidList.isEmpty();
            for (IItemHandler items : itemList) {
                for (int i = 0; i < items.getSlots(); i++) {
                    ItemStack st = items.getStackInSlot(i);
                    if (st.isEmpty()) continue;
                    sum = sum * 31 + BuiltInRegistries.ITEM.getId(st.getItem()) * 131L + st.getCount();
                    total += st.getCount();
                }
            }
            for (IFluidHandler fluids : fluidList) {
                for (int t = 0; t < fluids.getTanks(); t++) {
                    FluidStack fs = fluids.getFluidInTank(t);
                    if (fs.isEmpty()) continue;
                    sum = sum * 31 + BuiltInRegistries.FLUID.getId(fs.getFluid()) * 137L + fs.getAmount();
                }
            }
            if (sum != checksum) {
                checksum = sum;
                idle = 0;
            } else if (elapsed > 0) {
                idle = Math.max(idle, 0);
            }
            totalItems = any ? total : -1;
        }

        public long readAt() { return lastRead; }

        public com.createstats.surveyor.core.CargoSnapshot snapshot(long now) {
            java.util.Map<String, Long> counts = new java.util.HashMap<>();
            java.util.Map<String, Long> stacks = new java.util.HashMap<>();
            java.util.Map<String, Long> fluids = new java.util.HashMap<>();
            for (IItemHandler handler : itemHandlers) for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack st = handler.getStackInSlot(i); if (st.isEmpty()) continue;
                String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
                counts.merge(id, (long) st.getCount(), Long::sum);
                if (st.getCount() >= st.getMaxStackSize()) stacks.merge(id, 1L, Long::sum);
            }
            for (IFluidHandler handler : fluidHandlers) for (int i = 0; i < handler.getTanks(); i++) {
                FluidStack fs = handler.getFluidInTank(i); if (fs.isEmpty()) continue;
                fluids.merge(BuiltInRegistries.FLUID.getKey(fs.getFluid()).toString(), (long) fs.getAmount(), Long::sum);
            }
            return new com.createstats.surveyor.core.CargoSnapshot(counts, stacks, fluids, idle, now, true);
        }

        public int totalItems() {
            return totalItems;
        }

        @Override
        public long items(String item, boolean stacks) {
            String key = item + (stacks ? "#s" : "#i");
            Long cached = itemCache.get(key);
            if (cached != null) return cached;
            long n = 0;
            Item want = item.isEmpty() ? null : BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(item)).orElse(null);
            if (level != null && (item.isEmpty() || want != null)) {
                for (IItemHandler items : itemHandlers) {
                    for (int i = 0; i < items.getSlots(); i++) {
                        ItemStack st = items.getStackInSlot(i);
                        if (st.isEmpty() || (want != null && st.getItem() != want)) continue;
                        n += stacks ? (st.getCount() >= st.getMaxStackSize() ? 1 : 0) : st.getCount();
                    }
                }
            }
            itemCache.put(key, n);
            return n;
        }

        @Override
        public long fluidMb(String fluid) {
            Long cached = fluidCache.get(fluid);
            if (cached != null) return cached;
            long mb = 0;
            if (level != null) {
                for (IFluidHandler fluids : fluidHandlers) {
                    for (int t = 0; t < fluids.getTanks(); t++) {
                        FluidStack fs = fluids.getFluidInTank(t);
                        if (fs.isEmpty()) continue;
                        if (!fluid.isEmpty() && !BuiltInRegistries.FLUID.getKey(fs.getFluid()).toString().equals(fluid)) continue;
                        mb += fs.getAmount();
                    }
                }
            }
            fluidCache.put(fluid, mb);
            return mb;
        }

        @Override
        public int idleTicks() {
            return idle;
        }

        @Override
        public boolean linkPowered(String first, String second) {
            try {
                Couple<RedstoneLinkNetworkHandler.Frequency> key = Couple.create(frequency(first), frequency(second));
                return Create.REDSTONE_LINK_NETWORK_HANDLER.hasAnyLoadedPower(key);
            } catch (RuntimeException | LinkageError e) {
                RouteReflect.warnOnce("linkPower", e.toString());
                return false;
            }
        }

        public void reset() {
            idle = 0;
            checksum = Long.MIN_VALUE;
            lastRead = Long.MIN_VALUE;
            lastTick = Long.MIN_VALUE;
        }
    }

    public static boolean stopPowered(ServerLevel level, BlockPos at) {
        for (BlockPos p : new BlockPos[]{at, at.below()}) {
            boolean loaded = level.isLoaded(p);
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) loaded &= level.isLoaded(p.relative(d));
            if (loaded && level.hasNeighborSignal(p)) return true;
        }
        return false;
    }

    public static RedstoneLinkNetworkHandler.Frequency frequency(String id) {
        if (id == null || id.isEmpty()) return RedstoneLinkNetworkHandler.Frequency.EMPTY;
        Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(id)).orElse(null);
        return item == null ? RedstoneLinkNetworkHandler.Frequency.EMPTY : RedstoneLinkNetworkHandler.Frequency.of(new ItemStack(item));
    }

    @Nullable
    private static IItemHandler itemHandler(ServerLevel level, BlockEntity be) {
        try {
            return level.getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static IFluidHandler fluidHandler(ServerLevel level, BlockEntity be) {
        try {
            return level.getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
