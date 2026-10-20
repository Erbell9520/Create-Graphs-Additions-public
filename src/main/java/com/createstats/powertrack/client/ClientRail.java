package com.createstats.powertrack.client;

import com.createstats.CreateStats;
import com.createstats.powertrack.CollectorGeometry;
import com.createstats.powertrack.GraphRail;
import com.createstats.powertrack.PowerTrack;
import com.createstats.powertrack.SectionView;
import com.createstats.powertrack.core.CollectorPose;
import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.ShoeSlide;
import com.createstats.powertrack.net.CarCurrentPacket;
import com.createstats.powertrack.net.RailStatePacket;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class ClientRail {
    private static final int CHECK_INTERVAL = 10;

    private static final double PADDLE_HALF_LENGTH = 4.5 / 16.0;

    private static final int LOAD_MEMORY = 40;

    private static final Map<UUID, GraphRail> RAILS = new HashMap<>();
    private static final Map<UUID, RailStatePacket> STATES = new HashMap<>();

    private static final Map<Level, SceneRail> SCENES = new java.util.WeakHashMap<>();

    private static final Map<Integer, long[]> CURRENTS = new HashMap<>();

    private static final Map<Long, long[]> SHOES = new HashMap<>();

    private static final Map<Long, Carried> CARRIED = new HashMap<>();
    private static long revision;
    private static long lastCheck = Long.MIN_VALUE;
    @Nullable
    private static Level lastLevel;

    private ClientRail() {
    }

    public static void acceptStates(RailStatePacket packet) {
        STATES.put(packet.graph(), packet);
    }

    public static void acceptCurrent(CarCurrentPacket packet) {
        final Level level = Minecraft.getInstance().level;
        final long now = level == null ? 0 : level.getGameTime();
        final long[] old = CURRENTS.get(packet.entityId());
        final long lastLoad = packet.current() != 0 ? now : old == null ? Long.MIN_VALUE / 2 : old[3];
        CURRENTS.put(packet.entityId(), new long[]{packet.current(), packet.rating(), now, lastLoad});
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        final Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        final long now = level.getGameTime();
        if (level != lastLevel) {
            lastLevel = level;
            STATES.clear();
            lastCheck = Long.MIN_VALUE;
        }
        if (lastCheck != Long.MIN_VALUE && now - lastCheck < CHECK_INTERVAL && now >= lastCheck) {
            return;
        }
        lastCheck = now;
        final Map<UUID, TrackGraph> graphs = Create.RAILWAYS.sided(level).trackNetworks;
        boolean changed = false;
        for (Iterator<UUID> it = RAILS.keySet().iterator(); it.hasNext(); ) {
            final UUID id = it.next();
            if (!graphs.containsKey(id)) {
                it.remove();
                STATES.remove(id);
                changed = true;
            }
        }
        for (TrackGraph graph : graphs.values()) {
            final GraphRail known = RAILS.get(graph.id);
            if (known == null || known.fingerprint != GraphRail.fingerprint(graph)) {
                RAILS.put(graph.id, GraphRail.read(graph));
                changed = true;
            }
        }
        if (changed) {
            revision++;
        }

        CURRENTS.values().removeIf(c -> now - c[2] > 1200 || c[2] > now);
        SHOES.values().removeIf(s -> now - s[1] > 200 || s[1] > now);
        CARRIED.values().removeIf(c -> now - c.tick() > 200 || c.tick() > now);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RAILS.clear();
        STATES.clear();
        CURRENTS.clear();
        SHOES.clear();
        CARRIED.clear();
        revision++;
        lastCheck = Long.MIN_VALUE;
        lastLevel = null;
    }

    public static long revision() {
        return revision;
    }

    public static Collection<GraphRail> rails() {
        return RAILS.values();
    }

    public interface SceneRail {
        RailLayout layout();

        Vec3 shift(BlockPos local, float partialTicks);

        default int trailSign(BlockPos local) {
            return 1;
        }

        default double angle(BlockPos local, float partialTicks) {
            return Double.NaN;
        }
    }

    public static void scene(Level level, @Nullable SceneRail rail) {
        if (rail == null) {
            SCENES.remove(level);
        } else {
            SCENES.put(level, rail);
        }
    }

    @Nullable
    public static SceneRail scene(Level level) {
        return SCENES.isEmpty() ? null : SCENES.get(level);
    }

    @Nullable
    public static Under railUnder(Level level, double x, double y, double z, double reach, double minAbove,
                                  double maxAbove) {
        final SceneRail scene = scene(level);
        if (scene != null) {
            final RailLayout layout = scene.layout();
            final RailLayout.Hit hit = layout.nearest(x, y, z, reach, minAbove, maxAbove);
            return hit == null ? null : new Under(null, hit, layout.sections());
        }
        if (level != Minecraft.getInstance().level) {
            return null;
        }
        Under best = null;
        for (GraphRail rail : RAILS.values()) {
            if (!rail.hasPower()) {
                continue;
            }
            final RailLayout layout = rail.layout(level.dimension());
            final RailLayout.Hit hit = layout.nearest(x, y, z, reach, minAbove, maxAbove);
            if (hit != null && (best == null || hit.lateral() < best.hit().lateral())) {
                best = new Under(rail, hit, layout.sections());
            }
        }
        return best;
    }

    public static ShoeSlide.Locator locator(Level level) {
        return (x, y, z, reach, minAbove, maxAbove) -> {
            final Under under = railUnder(level, x, y, z, reach, minAbove, maxAbove);
            return under == null ? null : under.hit();
        };
    }

    public static CollectorGeometry.Placement place(Level level, Vec3 centre, Vec3 trail, Vec3 side, double centreOffset,
                                                    boolean onCar) {
        return CollectorGeometry.place(locator(level), centre, trail, side, centreOffset, onCar);
    }

    public record Carried(Vec3 centre, Vec3 trail, Vec3 side, double centreOffset, long tick) {
    }

    @Nullable
    public static Carried carried(int entityId, BlockPos localPos, long now) {
        final Carried c = CARRIED.get(((long) entityId << 32) ^ localPos.asLong());
        return c == null || now - c.tick() > 20 ? null : c;
    }

    public record Under(@Nullable GraphRail rail, RailLayout.Hit hit, com.createstats.powertrack.core.SectionGraph sections) {
        public com.createstats.powertrack.core.SectionGraph.Gap gap() {
            return sections.gapAt(hit.lo(), hit.hi(), hit.at());
        }

        @Nullable
        public SectionView view() {
            if (rail == null) {
                return null;
            }
            final RailStatePacket p = STATES.get(rail.graphId);
            final int s = hit.section();
            if (p == null || p.fingerprint() != rail.fingerprint || s < 0 || s >= p.sections().size()) {
                return null;
            }
            return p.sections().get(s);
        }
    }

    public static int currentOf(int entityId, long now) {
        final long[] c = CURRENTS.get(entityId);
        return c == null || now - c[2] > 30 ? 0 : (int) c[0];
    }

    public static int ratingOf(int entityId) {
        final long[] c = CURRENTS.get(entityId);
        return c == null ? 0 : (int) c[1];
    }

    private static boolean underLoad(int entityId, long now) {
        final long[] c = CURRENTS.get(entityId);
        return c != null && now - c[3] <= LOAD_MEMORY;
    }

    public static void collectorTick(MovementContext context) {
        final Level level = context.world;
        if (level == null || context.contraption == null || context.contraption.entity == null) {
            return;
        }
        final Vec3 trail = CollectorGeometry.trailDirection(context);
        final Vec3 side = CollectorGeometry.sideDirection(context);
        if (trail == null || side == null) {
            return;
        }
        final long now = level.getGameTime();
        final int entityId = context.contraption.entity.getId();

        final Vec3 centre = context.position;
        final double offset = CollectorGeometry.centreOffset(context.contraption, context.localPos,
                CollectorGeometry.gearAxis(context.contraption, context.state));
        final CollectorGeometry.Placement placed = place(level, centre, trail, side, offset, true);
        final CollectorPose.Rest rest = placed.rest();
        final Vec3 shoe = CollectorGeometry.shoeAt(placed.slid(), trail, rest.angleDeg());
        final Under under = rest.touching()
                ? railUnder(level, shoe.x, shoe.y, shoe.z, CollectorPose.CONTACT_HALF_WIDTH_PX / 16.0, 1.0, 2.0) : null;
        final boolean contact = under != null && under.hit().contact();
        final long key = ((long) entityId << 32) ^ context.localPos.asLong();
        CARRIED.put(key, new Carried(centre, trail, side, offset, now));
        final long[] memory = SHOES.computeIfAbsent(key, k -> new long[]{contact ? 1 : 0, now});
        final boolean wasOn = memory[0] != 0;
        memory[0] = contact ? 1 : 0;
        memory[1] = now;
        if (!contact) {
            return;
        }
        final RandomSource random = level.getRandom();
        final double railTop = under.hit().railTopY();
        final int current = currentOf(entityId, now);
        final int rating = Math.max(1, ratingOf(entityId));
        if (current != 0) {
            final double load = Math.min(1.0, Math.abs(current) / (double) rating);
            final double chance = 0.12 + 0.6 * load;
            final int count = random.nextDouble() < chance ? 1 + (int) (load * 2.5) : 0;
            final Vec3 motion = context.motion == null ? Vec3.ZERO : context.motion;

            final double speed = Math.hypot(motion.x, motion.z);
            final double ex = speed > 1e-3 ? -motion.x / speed : trail.x;
            final double ez = speed > 1e-3 ? -motion.z / speed : trail.z;
            final double edge = PADDLE_HALF_LENGTH;
            for (int i = 0; i < count; i++) {
                final double spread = (random.nextDouble() - 0.5) * 0.2;
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, shoe.x + ex * edge - ez * spread,
                        railTop + 0.03, shoe.z + ez * edge + ex * spread,
                        -motion.x * 0.4 + ex * 0.04 + (random.nextDouble() - 0.5) * 0.08, 0.05 + random.nextDouble() * 0.07,
                        -motion.z * 0.4 + ez * 0.04 + (random.nextDouble() - 0.5) * 0.08);
            }
        }
        if (!wasOn && underLoad(entityId, now)) {
            final SectionView view = under.view();
            final boolean live = view == null || view.state() == SectionView.LIVE || view.state() == SectionView.COLLECTING;
            if (live) {
                strike(level, shoe, railTop, random);
            }
        }
    }

    private static void strike(Level level, Vec3 shoe, double railTop, RandomSource random) {
        final double soleY = railTop + 0.02;

        ArcEffects.add(new Vec3(shoe.x, soleY + 0.28, shoe.z), new Vec3(shoe.x, railTop, shoe.z), random.nextInt());
        for (int i = 0; i < 8; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, shoe.x, railTop + 0.05, shoe.z,
                    (random.nextDouble() - 0.5) * 0.25, 0.05 + random.nextDouble() * 0.15, (random.nextDouble() - 0.5) * 0.25);
        }
        final SoundEvent sound = PowerTrack.arcSound();
        if (sound != null) {
            level.playLocalSound(shoe.x, railTop, shoe.z, sound, SoundSource.BLOCKS, 1.0f,
                    0.9f + random.nextFloat() * 0.2f, false);
        }
    }

    public static List<GraphRail.At<com.createstats.powertrack.FeederPoint>> feeders(GraphRail rail, Level level) {
        return rail.feeders(level.dimension());
    }
}
