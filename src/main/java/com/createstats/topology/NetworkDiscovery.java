package com.createstats.topology;

import com.createstats.ModConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NetworkDiscovery
{
    private static Field f_network;
    private static Field f_source;
    private static Field f_lastStressApplied;
    private static Field f_lastCapacityProvided;
    private static Field f_overStressed;
    private static Field f_networkSources;
    private static Method m_isSource;
    private static Method m_getSpeed;
    private static Method m_hasNetwork;
    private static Method m_hasSource;
    private static Method m_getOrCreateNetwork;
    private static Class<?> kineticClass;
    private static Method m_getSpeedModifier;
    private static Method m_isConnected;
    private static Method m_getPotentialNeighbourLocations;
    private static Method m_getConnectedNeighbours;
    private static Class<?> propagatorClass;
    private static boolean initialized;
    private static final long FALLBACK_NETWORK_ID = Long.MIN_VALUE;
    private static final int PREFERRED_SEED_SEARCH_RADIUS = 4;

    private static synchronized void init() {
        if (NetworkDiscovery.initialized) {
            return;
        }
        NetworkDiscovery.initialized = true;
        try {
            NetworkDiscovery.kineticClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
            NetworkDiscovery.f_network = findField(NetworkDiscovery.kineticClass, "network");
            if (NetworkDiscovery.f_network != null) {
                NetworkDiscovery.f_network.setAccessible(true);
            }
            NetworkDiscovery.f_source = findField(NetworkDiscovery.kineticClass, "source");
            if (NetworkDiscovery.f_source != null) {
                NetworkDiscovery.f_source.setAccessible(true);
            }
            NetworkDiscovery.f_lastStressApplied = findField(NetworkDiscovery.kineticClass, "lastStressApplied");
            if (NetworkDiscovery.f_lastStressApplied != null) {
                NetworkDiscovery.f_lastStressApplied.setAccessible(true);
            }
            NetworkDiscovery.f_lastCapacityProvided = findField(NetworkDiscovery.kineticClass, "lastCapacityProvided");
            if (NetworkDiscovery.f_lastCapacityProvided != null) {
                NetworkDiscovery.f_lastCapacityProvided.setAccessible(true);
            }
            NetworkDiscovery.f_overStressed = findField(NetworkDiscovery.kineticClass, "overStressed");
            if (NetworkDiscovery.f_overStressed != null) {
                NetworkDiscovery.f_overStressed.setAccessible(true);
            }
            NetworkDiscovery.m_isSource = findNoArgMethod(NetworkDiscovery.kineticClass, "isSource");
            NetworkDiscovery.m_getSpeed = findNoArgMethod(NetworkDiscovery.kineticClass, "getSpeed");
            NetworkDiscovery.m_hasNetwork = findNoArgMethod(NetworkDiscovery.kineticClass, "hasNetwork");
            NetworkDiscovery.m_hasSource = findNoArgMethod(NetworkDiscovery.kineticClass, "hasSource");
            NetworkDiscovery.m_getOrCreateNetwork = findNoArgMethod(NetworkDiscovery.kineticClass, "getOrCreateNetwork");
            try {
                final Class<?> kineticNetworkClass = Class.forName("com.simibubi.create.content.kinetics.KineticNetwork");
                NetworkDiscovery.f_networkSources = findField(kineticNetworkClass, "sources");
                if (NetworkDiscovery.f_networkSources != null) {
                    NetworkDiscovery.f_networkSources.setAccessible(true);
                }
            }
            catch (Exception ex) {}
        }
        catch (Exception ex2) {}
        final String[] propagatorClasses = { "com.simibubi.create.content.kinetics.RotationPropagator", "com.simibubi.create.content.kinetics.transmission.RotationPropagator" };
        for (String propagatorName : propagatorClasses) {
            try {
                NetworkDiscovery.propagatorClass = Class.forName(propagatorName);
                break;
            }
            catch (Exception ex3) {}
        }
        if (NetworkDiscovery.propagatorClass != null) {
            for (Method m : NetworkDiscovery.propagatorClass.getDeclaredMethods()) {
                if (NetworkDiscovery.m_getSpeedModifier == null && (m.getName().equals("getRotationSpeedModifier") || m.getName().contains("SpeedModifier") || m.getName().contains("speedModifier"))) {
                    m.setAccessible(true);
                    NetworkDiscovery.m_getSpeedModifier = m;
                }
                else if (NetworkDiscovery.m_isConnected == null && m.getName().equals("isConnected")) {
                    m.setAccessible(true);
                    NetworkDiscovery.m_isConnected = m;
                }
                else if (NetworkDiscovery.m_getPotentialNeighbourLocations == null && m.getName().equals("getPotentialNeighbourLocations")) {
                    m.setAccessible(true);
                    NetworkDiscovery.m_getPotentialNeighbourLocations = m;
                }
                else if (NetworkDiscovery.m_getConnectedNeighbours == null && m.getName().equals("getConnectedNeighbours")) {
                    m.setAccessible(true);
                    NetworkDiscovery.m_getConnectedNeighbours = m;
                }
                if (NetworkDiscovery.m_getSpeedModifier != null && NetworkDiscovery.m_isConnected != null && NetworkDiscovery.m_getPotentialNeighbourLocations != null && NetworkDiscovery.m_getConnectedNeighbours != null) {
                    break;
                }
            }
        }
    }

    private static Field findField(Class<?> cls, String name) {
        Class<?> current = cls;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            }
            catch (NoSuchFieldException ex) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> cls, String name) {
        try {
            return cls.getMethod(name, new Class[0]);
        }
        catch (NoSuchMethodException ex) {
            try {
                final Method method = cls.getDeclaredMethod(name, new Class[0]);
                method.setAccessible(true);
                return method;
            }
            catch (Exception inner) {
                return null;
            }
        }
    }

    public static BlockPos findSeed(Level level, BlockPos panelPos, int radius) {
        return findSeed(level, panelPos, radius, null);
    }

    public static BlockPos findSeed(Level level, BlockPos panelPos, int radius, Iterable<BlockPos> preferredSeeds) {
        init();
        if (NetworkDiscovery.kineticClass == null) {
            return null;
        }
        final BlockPos preferredSeed = findBestSeed(level, panelPos, preferredSeeds);
        if (preferredSeed != null) {
            return promoteSeed(level, preferredSeed);
        }
        final BlockPos nearbyPreferredSeed = findNearbyPreferredSeed(level, panelPos, preferredSeeds, Math.max(2, Math.min(NetworkDiscovery.PREFERRED_SEED_SEARCH_RADIUS, radius + 1)));
        if (nearbyPreferredSeed != null) {
            return promoteSeed(level, nearbyPreferredSeed);
        }
        return promoteSeed(level, findBestSeed(level, panelPos, BlockPos.betweenClosed(panelPos.offset(-radius, -radius, -radius), panelPos.offset(radius, radius, radius))));
    }

    private static BlockPos findBestSeed(Level level, BlockPos panelPos, Iterable<BlockPos> candidates) {
        if (candidates == null) {
            return null;
        }
        BlockPos bestSeed = null;
        int bestPriority = Integer.MAX_VALUE;
        long bestDistanceSq = Long.MAX_VALUE;
        for (BlockPos pos : candidates) {
            if (pos == null || !level.isLoaded(pos)) {
                continue;
            }
            final BlockEntity be = level.getBlockEntity(pos);
            if (!isValidSeed(be)) {
                continue;
            }
            final int priority = getSeedPriority(be);
            final long distanceSq = distanceSq(panelPos, pos);
            if (priority > bestPriority || priority == bestPriority && distanceSq >= bestDistanceSq) {
                continue;
            }
            bestPriority = priority;
            bestDistanceSq = distanceSq;
            bestSeed = pos.immutable();
        }
        return bestSeed;
    }

    private static BlockPos findNearbyPreferredSeed(Level level, BlockPos panelPos, Iterable<BlockPos> candidates, int searchRadius) {
        if (candidates == null || searchRadius <= 0) {
            return null;
        }
        BlockPos bestSeed = null;
        int bestPriority = Integer.MAX_VALUE;
        int bestCandidateDistance = Integer.MAX_VALUE;
        long bestPanelDistanceSq = Long.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            final BlockPos resolvedSeed = resolveSeedNearCandidate(level, panelPos, candidate, searchRadius);
            if (resolvedSeed == null) {
                continue;
            }
            final BlockEntity resolvedBE = level.getBlockEntity(resolvedSeed);
            if (!isValidSeed(resolvedBE)) {
                continue;
            }
            final int priority = getSeedPriority(resolvedBE);
            final int candidateDistance = manhattanDistance(candidate, resolvedSeed);
            final long panelDistanceSq = distanceSq(panelPos, resolvedSeed);
            if (priority > bestPriority || priority == bestPriority && (candidateDistance > bestCandidateDistance || candidateDistance == bestCandidateDistance && panelDistanceSq >= bestPanelDistanceSq)) {
                continue;
            }
            bestPriority = priority;
            bestCandidateDistance = candidateDistance;
            bestPanelDistanceSq = panelDistanceSq;
            bestSeed = resolvedSeed.immutable();
        }
        return bestSeed;
    }

    private static BlockPos resolveSeedNearCandidate(Level level, BlockPos panelPos, BlockPos candidate, int searchRadius) {
        if (candidate == null) {
            return null;
        }
        final Deque<BlockPos> queue = new ArrayDeque<BlockPos>();
        final Set<BlockPos> visited = new HashSet<BlockPos>();
        queue.addLast(candidate);
        visited.add(candidate);
        BlockPos bestSeed = null;
        int bestPriority = Integer.MAX_VALUE;
        int bestCandidateDistance = Integer.MAX_VALUE;
        long bestPanelDistanceSq = Long.MAX_VALUE;
        while (!queue.isEmpty()) {
            final BlockPos current = queue.removeFirst();
            final int candidateDistance = manhattanDistance(candidate, current);
            if (candidateDistance > searchRadius) {
                continue;
            }
            if (level.isLoaded(current)) {
                final BlockEntity be = level.getBlockEntity(current);
                if (isValidSeed(be)) {
                    final int priority = getSeedPriority(be);
                    final long panelDistanceSq = distanceSq(panelPos, current);
                    if (priority < bestPriority || priority == bestPriority && (candidateDistance < bestCandidateDistance || candidateDistance == bestCandidateDistance && panelDistanceSq < bestPanelDistanceSq)) {
                        bestPriority = priority;
                        bestCandidateDistance = candidateDistance;
                        bestPanelDistanceSq = panelDistanceSq;
                        bestSeed = current.immutable();
                    }
                }
            }
            if (candidateDistance >= searchRadius) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                final BlockPos next = current.relative(dir);
                if (visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return bestSeed;
    }

    private static BlockPos promoteSeed(Level level, BlockPos seed) {
        if (seed == null) {
            return null;
        }
        BlockPos current = seed.immutable();
        BlockPos best = current;
        final Set<BlockPos> seen = new HashSet<BlockPos>();
        for (int depth = 0; depth < 96 && seen.add(current); ++depth) {
            if (!level.isLoaded(current)) {
                break;
            }
            final BlockEntity be = level.getBlockEntity(current);
            if (!isValidSeed(be)) {
                break;
            }
            best = current.immutable();
            if (isSource(be)) {
                return best;
            }
            final BlockPos upstream = getSourcePos(be);
            if (upstream != null && !upstream.equals(current)) {
                current = upstream.immutable();
                continue;
            }
            final BlockPos networkSource = selectBestNetworkSource(be, current);
            if (networkSource != null && !networkSource.equals(current)) {
                current = networkSource.immutable();
                continue;
            }
            break;
        }
        return best;
    }

    private static boolean isValidSeed(BlockEntity be) {
        return be != null && NetworkDiscovery.kineticClass != null && NetworkDiscovery.kineticClass.isInstance(be);
    }

    private static int getSeedPriority(BlockEntity be) {
        if (be == null) {
            return 6;
        }
        if (isSource(be)) {
            return 0;
        }
        final BlockState state = be.getBlockState();
        final Block block = state.getBlock();
        final NodeType nodeType = NodeType.fromIdentity(block.getName().getString(), block.getClass().getSimpleName(), be.getClass().getSimpleName());
        if (nodeType.isSourceType()) {
            return 1;
        }
        if (nodeType.isObserverLike()) {
            return 2;
        }
        if (nodeType.isMachineLike()) {
            return 3;
        }
        if (nodeType.isTransferOrgan()) {
            return (nodeType == NodeType.SPEED_CONTROLLER) ? 5 : 4;
        }
        return 3;
    }

    private static long distanceSq(BlockPos a, BlockPos b) {
        final long dx = (long)a.getX() - b.getX();
        final long dy = (long)a.getY() - b.getY();
        final long dz = (long)a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static int manhattanDistance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    public static NetworkGraph discover(Level level, BlockPos seedPos) {
        return discover(level, seedPos, (int)ModConfiguration.COMMON.topologyMaxNodes.get());
    }

    public static NetworkGraph discover(Level level, BlockPos seedPos, int requestedMaxNodes) {
        init();
        if (NetworkDiscovery.kineticClass == null) {
            return NetworkGraph.EMPTY;
        }
        final BlockPos promotedSeed = promoteSeed(level, seedPos);
        if (promotedSeed != null) {
            seedPos = promotedSeed;
        }
        final BlockEntity seedBE = level.getBlockEntity(seedPos);
        if (seedBE == null || !NetworkDiscovery.kineticClass.isInstance(seedBE)) {
            return NetworkGraph.EMPTY;
        }
        final Long networkId = getNetworkId(seedBE);
        final long graphNetworkId = (networkId != null) ? networkId : NetworkDiscovery.FALLBACK_NETWORK_ID;
        final Map<BlockPos, DiscoveryNode> discoveredNodes = new LinkedHashMap<BlockPos, DiscoveryNode>();
        final List<TopologyEdge> edges = new ArrayList<TopologyEdge>();
        final Set<EdgeKey> edgeKeys = new HashSet<EdgeKey>();
        final Set<BlockPos> visited = new HashSet<BlockPos>();
        final Set<BlockPos> unloaded = new HashSet<BlockPos>();
        final Set<BlockPos> networkSourcePositions = new LinkedHashSet<BlockPos>();
        final Deque<BlockPos> queue = new ArrayDeque<BlockPos>();
        queue.addLast(seedPos.immutable());
        visited.add(seedPos.immutable());
        final int maxNodes = Math.max(10, Math.min(1000, requestedMaxNodes));
        while (!queue.isEmpty() && discoveredNodes.size() < maxNodes) {
            final BlockPos currentPos = queue.removeFirst();
            final BlockEntity currentBE = level.getBlockEntity(currentPos);
            if (currentBE == null || !NetworkDiscovery.kineticClass.isInstance(currentBE)) {
                continue;
            }
            final DiscoveryNode snapshot = buildDiscoveryNode(currentPos, currentBE);
            discoveredNodes.put(currentPos.immutable(), snapshot);
            if (snapshot.explicitSource) {
                networkSourcePositions.add(currentPos.immutable());
            }
            networkSourcePositions.addAll(getNetworkSourcePositions(currentBE));
            final List<BlockPos> potentialNeighbours = getPotentialNeighbours(level, currentPos, currentBE);
            for (BlockPos candidate : potentialNeighbours) {
                if (!level.isLoaded(candidate)) {
                    unloaded.add(candidate.immutable());
                }
            }
            final List<BlockPos> neighbours = getConnectedNeighbourPositions(level, currentBE, potentialNeighbours);
            for (BlockPos neighbourPos : neighbours) {
                if (!level.isLoaded(neighbourPos)) {
                    unloaded.add(neighbourPos.immutable());
                    continue;
                }
                final BlockEntity neighbourBE = level.getBlockEntity(neighbourPos);
                if (neighbourBE == null || !NetworkDiscovery.kineticClass.isInstance(neighbourBE)) {
                    continue;
                }
                final float speedRatio = resolveSpeedRatio(currentBE, neighbourBE);
                final EdgeKey edgeKey = EdgeKey.of(currentPos, neighbourPos);
                if (edgeKeys.add(edgeKey)) {
                    final float stressTransfer = Math.max(Math.abs(getStressImpact(currentBE)), Math.abs(getStressImpact(neighbourBE)));
                    edges.add(new TopologyEdge(currentPos.immutable(), neighbourPos.immutable(), (speedRatio == 0.0f) ? 1.0f : speedRatio, stressTransfer));
                }
                if (visited.add(neighbourPos.immutable())) {
                    queue.addLast(neighbourPos.immutable());
                }
            }
        }
        if (discoveredNodes.isEmpty()) {
            return NetworkGraph.EMPTY;
        }
        final Map<BlockPos, Integer> inboundCounts = new LinkedHashMap<BlockPos, Integer>();
        for (DiscoveryNode node : discoveredNodes.values()) {
            if (node.upstreamPos != null) {
                inboundCounts.merge(node.upstreamPos, 1, Integer::sum);
            }
        }
        final Map<BlockPos, TopologyNode> nodes = new LinkedHashMap<BlockPos, TopologyNode>();
        for (Map.Entry<BlockPos, DiscoveryNode> entry : discoveredNodes.entrySet()) {
            final BlockPos pos = entry.getKey();
            final DiscoveryNode node = entry.getValue();
            final boolean explicitSource = node.explicitSource || networkSourcePositions.contains(pos);
            boolean rootLike = explicitSource || node.type.isSourceType();
            if (!rootLike && isLikelyInferredRoot(node, inboundCounts.getOrDefault(pos, 0))) {
                rootLike = true;
            }
            nodes.put(pos, new TopologyNode(pos, node.displayName, node.type, node.speed, node.stressImpact, node.capacityProvided, explicitSource, rootLike, node.overStressed, node.upstreamPos));
        }
        final boolean truncated = !queue.isEmpty() && discoveredNodes.size() >= maxNodes;
        return new NetworkGraph(nodes, edges, graphNetworkId, level.getGameTime(), unloaded, truncated);
    }

    private static List<BlockPos> getPotentialNeighbours(Level level, BlockPos pos, BlockEntity kbe) {
        final List<BlockPos> createNeighbours = getCreatePotentialNeighbours(kbe);
        if (createNeighbours != null) {
            return createNeighbours;
        }
        final List<BlockPos> neighbours = new ArrayList<BlockPos>();
        for (Direction dir : Direction.values()) {
            neighbours.add(pos.relative(dir));
        }
        try {
            final BlockState state = kbe.getBlockState();
            Class<?> iRotate = null;
            try {
                iRotate = Class.forName("com.simibubi.create.content.kinetics.base.IRotate");
            }
            catch (ClassNotFoundException ex) {}
            if (iRotate != null && iRotate.isInstance(state.getBlock())) {
                final Method getAxis = iRotate.getMethod("getRotationAxis", BlockState.class);
                final Object axis = getAxis.invoke(state.getBlock(), state);
                if (axis instanceof Direction.Axis rotAxis) {
                    final Direction[] perp = getPerpendicularDirections(rotAxis);
                    if (perp.length >= 4) {
                        for (int i = 0; i < perp.length; ++i) {
                            for (int j = i + 1; j < perp.length; ++j) {
                                neighbours.add(pos.relative(perp[i]).relative(perp[j]));
                            }
                        }
                        for (Direction d : perp) {
                            neighbours.add(pos.relative(d, 2));
                        }
                    }
                }
            }
        }
        catch (Exception ex2) {}
        return neighbours;
    }

    private static List<BlockPos> getConnectedNeighbourPositions(Level level, BlockEntity kbe, List<BlockPos> potentialNeighbours) {
        if (NetworkDiscovery.m_getConnectedNeighbours != null) {
            try {
                final Object result = NetworkDiscovery.m_getConnectedNeighbours.invoke(null, kbe);
                if (result instanceof Iterable<?> iterable) {
                    final LinkedHashSet<BlockPos> neighbours = new LinkedHashSet<BlockPos>();
                    for (Object obj : iterable) {
                        if (obj instanceof BlockEntity be && NetworkDiscovery.kineticClass != null && NetworkDiscovery.kineticClass.isInstance(be)) {
                            neighbours.add(be.getBlockPos().immutable());
                        }
                    }
                    return new ArrayList<BlockPos>(neighbours);
                }
            }
            catch (Exception ex) {}
        }
        final LinkedHashSet<BlockPos> neighbours = new LinkedHashSet<BlockPos>();
        for (BlockPos candidate : potentialNeighbours) {
            if (!level.isLoaded(candidate)) {
                continue;
            }
            final BlockEntity neighbourBE = level.getBlockEntity(candidate);
            if (neighbourBE == null || NetworkDiscovery.kineticClass == null || !NetworkDiscovery.kineticClass.isInstance(neighbourBE)) {
                continue;
            }
            if (areConnected(kbe, neighbourBE)) {
                neighbours.add(candidate.immutable());
            }
        }
        return new ArrayList<BlockPos>(neighbours);
    }

    private static List<BlockPos> getCreatePotentialNeighbours(BlockEntity kbe) {
        if (NetworkDiscovery.m_getPotentialNeighbourLocations == null || NetworkDiscovery.kineticClass == null || !NetworkDiscovery.kineticClass.isInstance(kbe)) {
            return null;
        }
        try {
            final Object result = NetworkDiscovery.m_getPotentialNeighbourLocations.invoke(null, kbe);
            if (!(result instanceof Iterable<?> iterable)) {
                return Collections.emptyList();
            }
            final LinkedHashSet<BlockPos> neighbours = new LinkedHashSet<BlockPos>();
            for (Object obj : iterable) {
                if (obj instanceof BlockPos blockPos) {
                    neighbours.add(blockPos.immutable());
                }
            }
            return new ArrayList<BlockPos>(neighbours);
        }
        catch (Exception ex) {
            return null;
        }
    }

    private static Direction[] getPerpendicularDirections(Direction.Axis axis) {
        return switch (axis) {
            default -> throw new IncompatibleClassChangeError();
            case X -> new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN };
            case Y -> new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
            case Z -> new Direction[] { Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN };
        };
    }

    private static Long getNetworkId(BlockEntity be) {
        if (NetworkDiscovery.f_network != null) {
            try {
                final Object val = NetworkDiscovery.f_network.get(be);
                return (val instanceof Long) ? ((Long)val) : null;
            }
            catch (Exception ex) {}
        }
        if (hasNetwork(be)) {
            return Long.MIN_VALUE;
        }
        return null;
    }

    private static boolean hasNetwork(BlockEntity be) {
        if (NetworkDiscovery.m_hasNetwork != null) {
            try {
                final Object result = NetworkDiscovery.m_hasNetwork.invoke(be, new Object[0]);
                return Boolean.TRUE.equals(result);
            }
            catch (Exception ex) {}
        }
        if (NetworkDiscovery.f_network != null) {
            try {
                return NetworkDiscovery.f_network.get(be) != null;
            }
            catch (Exception ex2) {}
        }
        return false;
    }

    private static boolean isSource(BlockEntity be) {
        if (NetworkDiscovery.m_isSource != null) {
            try {
                final Object result = NetworkDiscovery.m_isSource.invoke(be, new Object[0]);
                return Boolean.TRUE.equals(result);
            }
            catch (Exception ex) {}
        }
        return false;
    }

    private static boolean hasSource(BlockEntity be) {
        if (NetworkDiscovery.m_hasSource != null) {
            try {
                final Object result = NetworkDiscovery.m_hasSource.invoke(be, new Object[0]);
                return Boolean.TRUE.equals(result);
            }
            catch (Exception ex) {}
        }
        return getSourcePos(be) != null;
    }

    private static float getSpeed(BlockEntity be) {
        if (NetworkDiscovery.m_getSpeed != null) {
            try {
                final Object result = NetworkDiscovery.m_getSpeed.invoke(be, new Object[0]);
                if (result instanceof Number n) {
                    return n.floatValue();
                }
            }
            catch (Exception ex) {}
        }
        return 0.0f;
    }

    private static float getStressImpact(BlockEntity be) {
        return scaleCoefficientBySpeed(be, readFloatField(NetworkDiscovery.f_lastStressApplied, be));
    }

    private static float getCapacityProvided(BlockEntity be) {
        return scaleCoefficientBySpeed(be, readFloatField(NetworkDiscovery.f_lastCapacityProvided, be));
    }

    private static float readFloatField(Field field, BlockEntity be) {
        if (field != null) {
            try {
                final Object val = field.get(be);
                if (val instanceof Number n) {
                    return n.floatValue();
                }
            }
            catch (Exception ex) {}
        }
        return 0.0f;
    }

    private static float scaleCoefficientBySpeed(BlockEntity be, float coefficient) {
        if (Math.abs(coefficient) <= 0.001f) {
            return 0.0f;
        }
        return coefficient * Math.abs(getSpeed(be));
    }

    private static boolean isOverStressed(BlockEntity be) {
        if (NetworkDiscovery.f_overStressed != null) {
            try {
                final Object val = NetworkDiscovery.f_overStressed.get(be);
                return Boolean.TRUE.equals(val);
            }
            catch (Exception ex) {}
        }
        return false;
    }

    private static float getSpeedModifier(BlockEntity from, BlockEntity to) {
        if (NetworkDiscovery.m_getSpeedModifier != null) {
            try {
                final Object result = NetworkDiscovery.m_getSpeedModifier.invoke(null, from, to);
                if (result instanceof Number n) {
                    return n.floatValue();
                }
            }
            catch (Exception ex) {}
        }
        return 0.0f;
    }

    private static BlockPos getSourcePos(BlockEntity be) {
        if (NetworkDiscovery.f_source != null) {
            try {
                final Object val = NetworkDiscovery.f_source.get(be);
                if (val instanceof BlockPos blockPos) {
                    return blockPos.immutable();
                }
            }
            catch (Exception ex) {}
        }
        return null;
    }

    private static Object getNetworkObject(BlockEntity be) {
        if (!hasNetwork(be) || NetworkDiscovery.m_getOrCreateNetwork == null) {
            return null;
        }
        try {
            return NetworkDiscovery.m_getOrCreateNetwork.invoke(be, new Object[0]);
        }
        catch (Exception ex) {
            return null;
        }
    }

    private static Set<BlockPos> getNetworkSourcePositions(BlockEntity be) {
        if (NetworkDiscovery.f_networkSources == null) {
            return Collections.emptySet();
        }
        final Object networkObject = getNetworkObject(be);
        if (networkObject == null) {
            return Collections.emptySet();
        }
        try {
            final Object sourcesObj = NetworkDiscovery.f_networkSources.get(networkObject);
            if (!(sourcesObj instanceof Map<?, ?> sourceMap) || sourceMap.isEmpty()) {
                return Collections.emptySet();
            }
            final LinkedHashSet<BlockPos> positions = new LinkedHashSet<BlockPos>();
            for (Object source : sourceMap.keySet()) {
                if (source instanceof BlockEntity sourceBE) {
                    positions.add(sourceBE.getBlockPos().immutable());
                }
            }
            return positions;
        }
        catch (Exception ex) {
            return Collections.emptySet();
        }
    }

    private static BlockPos selectBestNetworkSource(BlockEntity be, BlockPos referencePos) {
        BlockPos best = null;
        long bestDistanceSq = Long.MAX_VALUE;
        for (BlockPos candidate : getNetworkSourcePositions(be)) {
            final long candidateDistanceSq = distanceSq(referencePos, candidate);
            if (candidateDistanceSq < bestDistanceSq) {
                bestDistanceSq = candidateDistanceSq;
                best = candidate.immutable();
            }
        }
        return best;
    }

    private static boolean areConnected(BlockEntity from, BlockEntity to) {
        if (NetworkDiscovery.m_isConnected != null) {
            try {
                if (Boolean.TRUE.equals(NetworkDiscovery.m_isConnected.invoke(null, from, to))) {
                    return true;
                }
                if (Boolean.TRUE.equals(NetworkDiscovery.m_isConnected.invoke(null, to, from))) {
                    return true;
                }
            }
            catch (Exception ex) {}
        }
        return resolveSpeedRatio(from, to) != 0.0f;
    }

    private static float resolveSpeedRatio(BlockEntity from, BlockEntity to) {
        final float forward = getSpeedModifier(from, to);
        if (forward != 0.0f) {
            return forward;
        }
        final float reverse = getSpeedModifier(to, from);
        if (reverse != 0.0f && Math.abs(reverse) > 1.0E-4f) {
            return 1.0f / reverse;
        }
        final float fromSpeed = getSpeed(from);
        final float toSpeed = getSpeed(to);
        if (Math.abs(fromSpeed) > 1.0E-4f && Math.abs(toSpeed) > 1.0E-4f) {
            return toSpeed / fromSpeed;
        }
        return 0.0f;
    }

    private static String resolveDisplayName(String blockName, String className) {
        if (className != null && className.contains("PoweredShaft")) {
            return "Steam Engine";
        }
        if (blockName != null) {
            final String trimmed = blockName.trim();
            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("Air")) {
                return trimmed;
            }
        }
        return NodeType.displayNameFromClassName(className);
    }

    private static DiscoveryNode buildDiscoveryNode(BlockPos pos, BlockEntity be) {
        final String className = be.getClass().getSimpleName();
        final BlockState state = be.getBlockState();
        final Block block = state.getBlock();
        final String blockName = block.getName().getString();
        final String blockClassName = block.getClass().getSimpleName();
        final NodeType nodeType = NodeType.fromIdentity(blockName, blockClassName, className);
        final String displayName = resolveDisplayName(blockName, className);
        BlockPos upstreamPos = hasSource(be) ? getSourcePos(be) : null;
        if (upstreamPos != null && upstreamPos.equals(pos)) {
            upstreamPos = null;
        }
        return new DiscoveryNode(displayName, nodeType, getSpeed(be), getStressImpact(be), getCapacityProvided(be), isSource(be), isOverStressed(be), upstreamPos);
    }

    private static boolean isLikelyInferredRoot(DiscoveryNode node, int inboundCount) {
        if (node.explicitSource || node.upstreamPos != null || inboundCount <= 0) {
            return false;
        }
        if (node.type == NodeType.SPEED_CONTROLLER || node.type.isObserverLike()) {
            return false;
        }
        if (Math.abs(node.speed) <= 0.01f && node.capacityProvided <= 0.01f) {
            return false;
        }
        if (node.type.isTransferOrgan() && node.capacityProvided <= 0.01f && !node.displayName.contains("Steam Engine")) {
            return false;
        }
        return true;
    }

    private static final class DiscoveryNode {
        private final String displayName;
        private final NodeType type;
        private final float speed;
        private final float stressImpact;
        private final float capacityProvided;
        private final boolean explicitSource;
        private final boolean overStressed;
        private final BlockPos upstreamPos;

        private DiscoveryNode(String displayName, NodeType type, float speed, float stressImpact, float capacityProvided, boolean explicitSource, boolean overStressed, BlockPos upstreamPos) {
            this.displayName = displayName;
            this.type = type;
            this.speed = speed;
            this.stressImpact = stressImpact;
            this.capacityProvided = capacityProvided;
            this.explicitSource = explicitSource;
            this.overStressed = overStressed;
            this.upstreamPos = upstreamPos;
        }
    }

    private record EdgeKey(long a, long b) {
        private static EdgeKey of(BlockPos first, BlockPos second) {
            final long x = first.asLong();
            final long y = second.asLong();
            return (x <= y) ? new EdgeKey(x, y) : new EdgeKey(y, x);
        }
    }

    static {
        NetworkDiscovery.initialized = false;
    }
}
