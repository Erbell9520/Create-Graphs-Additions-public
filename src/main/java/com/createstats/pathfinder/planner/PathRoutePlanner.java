package com.createstats.pathfinder.planner;

import com.createstats.pathfinder.blockentity.PathScannerBE;
import com.createstats.pathfinder.model.ChunkScanCacheEntry;
import com.createstats.pathfinder.model.HazardFlags;
import com.createstats.pathfinder.model.Passability;
import com.createstats.pathfinder.model.PlannerJobStage;
import com.createstats.pathfinder.model.RouteGuidance;
import com.createstats.pathfinder.model.ScanRegion;
import com.createstats.pathfinder.model.UnknownPolicy;
import com.createstats.pathfinder.settings.PathScanSettings;
import com.createstats.pathfinder.util.PathPayloadDecoder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

public final class PathRoutePlanner {
    private static final int CHUNK_SIZE = 16;
    private static final int CELL_COUNT = CHUNK_SIZE * CHUNK_SIZE;
    private static final int MAX_ROUTE_POINTS = 160;
    private static final int MAX_TARGET_ATTEMPTS = 96;
    private static final int MAX_VERTICAL_STEP = 2;
    // [1]
    private static final int MAX_EXPANDED_STATES = 30000;
    // [2]
    private static final double SLOPE_STEP_BASE = 3.0;
    private static final double SLOPE_STEP_ESCALATION = 1.6;
    private static final double SLOPE_STEP_MAX = 22.0;
    private static final double DESCENT_STEP_PENALTY = 0.6;
    // [3]
    private static final double OBSTACLE_BERTH_EXTRA = 1.0;
    private static final double OBSTACLE_BERTH_WEIGHT = 2.6;
    private static final int[][] NEIGHBORS = {
        { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
        { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
    };

    private PathRoutePlanner() {
    }

    public static PlannedRoute planLocalCorridor(PathScannerBE scanner, BlockPos scannerPos, float maxSpeed) {
        return planLocalCorridor(scanner, scannerPos, maxSpeed, null);
    }

    public static PlannedRoute planLocalCorridor(PathScannerBE scanner, BlockPos scannerPos, float maxSpeed,
                                                 @Nullable Vec3 navTarget) {
        return planLocalCorridor(scanner, scannerPos, maxSpeed, navTarget, 0.0);
    }

    public static PlannedRoute planLocalCorridor(PathScannerBE scanner, BlockPos scannerPos, float maxSpeed,
                                                 @Nullable Vec3 navTarget, double vehicleClearanceRadius) {
        return planLocalCorridor(scanner, scannerPos, maxSpeed, navTarget, vehicleClearanceRadius, vehicleClearanceRadius);
    }

    public static PlannedRoute planLocalCorridor(PathScannerBE scanner, BlockPos scannerPos, float maxSpeed,
                                                 @Nullable Vec3 navTarget, double hardClearanceRadius,
                                                 double preferredClearanceRadius) {
        if (scanner == null || scannerPos == null) {
            return empty("No scanner linked", null, 0, 0, 0, 0, PlannerJobStage.WAITING_FOR_SCAN, 0, 0);
        }
        ScanRegion region = scanner.currentRegion();
        if (region == null) {
            return empty("Waiting for 5x5 silhouette", scanner.scannerId(), 0, 0, 0, 0,
                PlannerJobStage.WAITING_FOR_SCAN, 10, 0);
        }
        if (navTarget == null) {
            return empty("Waiting for Nav Table target", scanner.scannerId(), 0, 0, 0, 0,
                PlannerJobStage.HOLD, 0, 0);
        }

        DirectionBasis basis = DirectionBasis.fromYaw(region.yawDeg());
        Map<Long, Cell> cells = collectSilhouetteCells(scanner, region, basis);
        int inspected = cells.size();
        if (cells.isEmpty()) {
            return empty("No top-view silhouette cells", scanner.scannerId(), 0, 0, 0, 0,
                PlannerJobStage.WAITING_FOR_SCAN, 20, 0);
        }

        Cell start = anchorStartCell(cells, region.origin());

        double hardClearance = clampDouble(hardClearanceRadius, 0.0, 2.0);
        // Preferred clearance is at least hardClearance + 3 blocks so the path stays well clear of
        // trees, trunks, and other same-height obstacles.  Cap at 6 to remain tractable.
        double preferredClearance = clampDouble(Math.max(hardClearance + 3.0, preferredClearanceRadius), 0.0, 6.0);
        // Minimum turning radius the smoother rounds corners to. Larger vehicles (more clearance) get a
        // wider radius. Clamped to a sane driving range.
        double turnRadius = clampDouble(preferredClearance + 1.5, 3.0, 8.0);

        // [4]
        if (navTarget != null) {
            double hsDx = navTarget.x - (start.x() + 0.5);
            double hsDz = navTarget.z - (start.z() + 0.5);
            if (hsDx * hsDx + hsDz * hsDz <= 32.0 * 32.0
                    && lineOfSightClearToPoint(start, navTarget, cells, hardClearance)) {
                Vec3 startWp = new Vec3(start.x() + 0.5, start.y() + 1.0, start.z() + 0.5);
                double hsDist = Math.hypot(hsDx, hsDz);
                List<Vec3> hsWps = List.of(startWp, navTarget);
                float cappedHsSpeed = clamp(maxSpeed, 1.0f, 32.0f);
                List<Float> hsSpeeds = List.of(cappedHsSpeed, cappedHsSpeed);
                RouteGuidance hsGuidance = new RouteGuidance(
                    routeId(scanner.scannerId(), scannerPos, List.of(start), 0),
                    "Home stretch (direct)", scanner.scannerId().toString(),
                    0.95f, hsWps, hsSpeeds, List.of(), hsDist, 0L, 0);
                String hsStatus = String.format(Locale.ROOT, "Home stretch: %.0fm direct to target", hsDist);
                return new PlannedRoute(hsGuidance, hsStatus, inspected, cells.size(), 0, 0,
                    PlannerJobStage.READY, 100, 0, false);
            }
        }

        List<Cell> targets = targetCandidates(cells, start, region, basis, navTarget, hardClearance);
        if (targets.isEmpty()) {
            return empty("No sensible target inside 5x5 silhouette", scanner.scannerId(), inspected, cells.size(), 0, 0,
                PlannerJobStage.HOLD, 100, 0);
        }

        // [5]
        Map<Long, Double> berthCache = new HashMap<>();

        GridRoute bestRoute = null;
        int expandedStates = 0;
        int attempts = Math.min(MAX_TARGET_ATTEMPTS, targets.size());
        for (int index = 0; index < attempts; index++) {
            GridRoute route = findAStarRoute(cells, start, targets.get(index), hardClearance, preferredClearance,
                berthCache, MAX_EXPANDED_STATES - expandedStates);
            expandedStates += route.expandedStates();
            if (route.reachedTarget() && route.path().size() >= 2) {
                bestRoute = route;
                break;
            }
            if (bestRoute == null || route.path().size() > bestRoute.path().size()) {
                bestRoute = route;
            }
            if (expandedStates >= MAX_EXPANDED_STATES) break;
        }

        if (bestRoute == null || bestRoute.path().size() < 2) {
            return empty("Dynamic A* found no reachable silhouette point", scanner.scannerId(), inspected,
                cells.size(), 0, 0, PlannerJobStage.HOLD, 100, expandedStates);
        }

        List<Cell> rawPath = bestRoute.path();
        int hazardFlags = pathHazards(rawPath);
        List<Vec3> waypoints = smoothPath(rawPath, cells, hardClearance, turnRadius);
        float cappedSpeed = clamp(maxSpeed, 1.0f, 32.0f);
        float confidence = confidence(bestRoute.reachedTarget(), rawPath.size(), cells.size(), hazardFlags);
        String routeName = "Nav Table 5x5 route";
        RouteGuidance guidance = new RouteGuidance(routeId(scanner.scannerId(), scannerPos, rawPath, hazardFlags), routeName,
            scanner.scannerId().toString(), confidence, waypoints, vehicleSpeeds(waypoints, cappedSpeed, hazardFlags, turnRadius),
            List.of(), pathLength(waypoints), 0L, hazardFlags);
        String status = String.format(Locale.ROOT, "Dynamic A* ready: %d pts %.0fm inside 5x5%s",
            waypoints.size(), guidance.estimatedDistance(), bestRoute.reachedTarget() ? "" : " closest-point");
        return new PlannedRoute(guidance, status, inspected, cells.size(), 0, hazardFlags,
            PlannerJobStage.READY, 100, expandedStates, false);
    }

    private static Map<Long, Cell> collectSilhouetteCells(PathScannerBE scanner, ScanRegion region, DirectionBasis basis) {
        Map<Long, Cell> cells = new HashMap<>();
        GridFacing facing = GridFacing.fromYaw(region.yawDeg());
        int baseChunkX = region.origin().getX() >> 4;
        int baseChunkZ = region.origin().getZ() >> 4;
        int maxForwardBlocks = Math.max(CHUNK_SIZE, region.forwardChunks() * CHUNK_SIZE);
        int maxSideBlocks = Math.max(CHUNK_SIZE, (region.sideChunks() + 1) * CHUNK_SIZE);
        for (int forwardChunk = -PathScanSettings.BACKWARD_CHUNKS; forwardChunk < region.forwardChunks(); forwardChunk++) {
            for (int sideChunk = -region.sideChunks(); sideChunk <= region.sideChunks(); sideChunk++) {
                ChunkScanCacheEntry entry = scanner.getCacheEntry(
                    baseChunkX + facing.forwardX() * forwardChunk + facing.rightX() * sideChunk,
                    baseChunkZ + facing.forwardZ() * forwardChunk + facing.rightZ() * sideChunk);
                collectSilhouetteEntry(cells, region, basis, maxForwardBlocks, maxSideBlocks, entry);
            }
        }
        return cells;
    }

    private static void collectSilhouetteEntry(Map<Long, Cell> cells, ScanRegion region, DirectionBasis basis,
                                               int maxForwardBlocks, int maxSideBlocks,
                                               @Nullable ChunkScanCacheEntry entry) {
        if (entry == null || !entry.loaded()) return;
        byte[] payload = entry.compressedCellPayload();
        byte[] heightPayload = entry.compressedHeightPayload();
        if (payload == null || payload.length < CELL_COUNT) return;
        for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
            for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                int worldX = (entry.chunkX() << 4) + localX;
                int worldZ = (entry.chunkZ() << 4) + localZ;
                int dx = worldX - region.origin().getX();
                int dz = worldZ - region.origin().getZ();
                double forward = basis.forward(dx, dz);
                double side = basis.side(dx, dz);
                if (forward < -(PathScanSettings.BACKWARD_CHUNKS * CHUNK_SIZE)
                    || forward > maxForwardBlocks || Math.abs(side) > maxSideBlocks) continue;

                int cellIndex = localZ * CHUNK_SIZE + localX;
                byte packed = payload[cellIndex];
                Passability passability = PathPayloadDecoder.passability(packed);
                int hazards = PathPayloadDecoder.hazards(packed);
                if (!isPassable(passability, hazards, region.unknownPolicy())) continue;
                int supportY = PathPayloadDecoder.supportY(heightPayload, cellIndex, region.origin().getY());
                Cell cell = new Cell(worldX, supportY, worldZ, hazards);
                cells.put(cell.key(), cell);
            }
        }
    }

    private static Cell anchorStartCell(Map<Long, Cell> cells, BlockPos origin) {
        int x = origin.getX();
        int z = origin.getZ();
        Cell existing = cells.get(cellKey(x, z));
        if (existing != null) return existing;
        int y = nearestHeight(cells, x, z, origin.getY());
        Cell start = new Cell(x, y, z, 0);
        cells.put(start.key(), start);
        return start;
    }

    private static int nearestHeight(Map<Long, Cell> cells, int x, int z, int fallbackY) {
        Cell best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Cell cell : cells.values()) {
            int dx = cell.x() - x;
            int dz = cell.z() - z;
            int distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = cell;
            }
        }
        return best == null ? fallbackY : best.y();
    }

    private static List<Cell> targetCandidates(Map<Long, Cell> cells, Cell start, ScanRegion region,
                                               DirectionBasis basis, @Nullable Vec3 navTarget,
                                               double hardClearance) {
        List<CellScore> scores = new ArrayList<>();
        double originX = region.origin().getX() + 0.5;
        double originZ = region.origin().getZ() + 0.5;
        for (Cell cell : cells.values()) {
            if (cell.key() == start.key()) continue;
            if (!hasClearance(cells, cell, hardClearance)) continue;
            double dx = (cell.x() + 0.5) - originX;
            double dz = (cell.z() + 0.5) - originZ;
            double score;
            if (navTarget != null) {
                double targetDistance = Math.hypot((cell.x() + 0.5) - navTarget.x, (cell.z() + 0.5) - navTarget.z);
                double targetDx = navTarget.x - originX;
                double targetDz = navTarget.z - originZ;
                double targetLength = Math.max(1.0, Math.hypot(targetDx, targetDz));
                double progress = (dx * targetDx + dz * targetDz) / targetLength;
                score = targetDistance - progress * 0.15;
            } else {
                double forward = basis.forward(dx, dz);
                double side = Math.abs(basis.side(dx, dz));
                score = -forward + side * 0.35;
            }
            scores.add(new CellScore(cell, score));
        }
        scores.sort(Comparator.comparingDouble(CellScore::score));
        List<Cell> candidates = new ArrayList<>(Math.min(scores.size(), MAX_TARGET_ATTEMPTS));
        for (CellScore score : scores) candidates.add(score.cell());
        return List.copyOf(candidates);
    }

    private static GridRoute findAStarRoute(Map<Long, Cell> cells, Cell start, Cell target,
                                            double hardClearance, double preferredClearance,
                                            Map<Long, Double> berthCache, int budget) {
        if (budget <= 0 || start.key() == target.key()) return new GridRoute(List.of(), 0, false);
        // Precompute the start-to-target direction for the cross-product tie-breaker
        long stDx = target.x() - start.x();
        long stDz = target.z() - start.z();
        Map<Long, SearchNode> nodes = new HashMap<>();
        PriorityQueue<QueueEntry> open = new PriorityQueue<>(
            Comparator.comparingDouble(QueueEntry::f).thenComparingDouble(QueueEntry::tieBreak));
        SearchNode startNode = new SearchNode(start);
        startNode.g = 0.0;
        startNode.f = heuristic(start, target);
        nodes.put(start.key(), startNode);
        open.add(new QueueEntry(start.key(), startNode.f, startNode.g, 0.0));
        SearchNode best = startNode;
        double bestHeuristic = startNode.f;
        int expanded = 0;

        while (!open.isEmpty() && expanded < budget) {
            QueueEntry entry = open.poll();
            SearchNode current = nodes.get(entry.key());
            if (current == null || current.closed || entry.g() > current.g + 1.0e-8) continue;
            current.closed = true;
            expanded++;
            double currentHeuristic = heuristic(current.cell, target);
            if (currentHeuristic < bestHeuristic) {
                bestHeuristic = currentHeuristic;
                best = current;
            }
            if (current.cell.key() == target.key()) {
                return new GridRoute(reconstruct(current), expanded, true);
            }
            for (int[] neighbor : NEIGHBORS) {
                Cell next = cells.get(cellKey(current.cell.x() + neighbor[0], current.cell.z() + neighbor[1]));
                if (next == null || !canStep(cells, current.cell, next, hardClearance)) continue;
                long nextKey = next.key();
                SearchNode nextNode = nodes.computeIfAbsent(nextKey, ignored -> new SearchNode(next));
                if (nextNode.closed) continue;
                int rise = next.y() - current.cell.y();
                int nextClimbRun = rise > 0 ? current.climbRun + rise : 0;
                double slopeCost = rise > 0 ? slopeStepPenalty(current.climbRun, rise)
                    : (rise < 0 ? DESCENT_STEP_PENALTY * (-rise) : 0.0);
                double berth = berthCache.computeIfAbsent(nextKey,
                    ignored -> obstacleBerthPenalty(cells, next, preferredClearance));
                double tentativeG = current.g + movementCost(current.cell, next, berth) + slopeCost;
                if (tentativeG >= nextNode.g) continue;
                nextNode.g = tentativeG;
                nextNode.climbRun = nextClimbRun;
                nextNode.f = tentativeG + heuristic(next, target);
                nextNode.parent = current;
                // Cross-product tie-breaker: prefer nodes on the direct start-to-goal line
                long nodeDx = next.x() - start.x();
                long nodeDz = next.z() - start.z();
                double tieBreak = Math.abs(nodeDx * stDz - nodeDz * stDx) * 1e-5;
                open.add(new QueueEntry(nextKey, nextNode.f, tentativeG, tieBreak));
            }
        }
        return new GridRoute(best == startNode ? List.of() : reconstruct(best), expanded, false);
    }

    private static boolean canStep(Map<Long, Cell> cells, Cell from, Cell to, double hardClearance) {
        if (Math.abs(to.y() - from.y()) > MAX_VERTICAL_STEP) return false;
        if (!hasClearance(cells, to, hardClearance)) return false;
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1 || (dx == 0 && dz == 0)) return false;
        if (dx != 0 && dz != 0) {
            Cell sideA = cells.get(cellKey(from.x() + dx, from.z()));
            Cell sideB = cells.get(cellKey(from.x(), from.z() + dz));
            if (sideA == null || sideB == null) return false;
            if (Math.abs(sideA.y() - from.y()) > MAX_VERTICAL_STEP) return false;
            if (Math.abs(sideB.y() - from.y()) > MAX_VERTICAL_STEP) return false;
        }
        return true;
    }

    private static boolean hasClearance(Map<Long, Cell> cells, Cell center, double radius) {
        if (radius < 0.75) return true;
        int range = Math.max(1, (int) Math.ceil(radius));
        double radiusSq = radius * radius;
        for (int dz = -range; dz <= range; dz++) {
            for (int dx = -range; dx <= range; dx++) {
                if (dx * dx + dz * dz > radiusSq) continue;
                Cell nearby = cells.get(cellKey(center.x() + dx, center.z() + dz));
                if (nearby == null) return false;
                if (Math.abs(nearby.y() - center.y()) > MAX_VERTICAL_STEP) return false;
                if ((nearby.hazards() & (HazardFlags.LAVA | HazardFlags.UNSTABLE)) != 0) return false;
            }
        }
        return true;
    }

    private static double movementCost(Cell from, Cell to, double berthPenalty) {
        double cost = from.x() != to.x() && from.z() != to.z() ? Math.sqrt(2.0) : 1.0;
        // Slope cost (rise-dependent and climb-run-escalating) is applied by the A* loop, which holds
        // the sustained-climb state; here we only cover the flat step and static hazards.
        int hazards = to.hazards();
        if ((hazards & HazardFlags.TWO_BLOCK) != 0) cost += 1.4;
        // Water: extreme penalty so it is only ever used as an absolute last resort
        if ((hazards & HazardFlags.LIQUID) != 0) cost += 50.0;
        if ((hazards & HazardFlags.UNKNOWN_CHUNK) != 0) cost += 6.0;
        // Graduated obstacle/tree/structure berth (looked up from the lazy per-cell cache).
        cost += berthPenalty;
        return cost;
    }

    // [6]
    static double obstacleBerthPenalty(Map<Long, Cell> cells, Cell center, double preferredClearance) {
        if (preferredClearance < 1.0) return 0.0;
        double berth = preferredClearance + OBSTACLE_BERTH_EXTRA;
        int range = (int) Math.ceil(berth);
        double berthSq = berth * berth;
        int nearestSq = Integer.MAX_VALUE;
        for (int dz = -range; dz <= range; dz++) {
            for (int dx = -range; dx <= range; dx++) {
                if (dx == 0 && dz == 0) continue;
                int distSq = dx * dx + dz * dz;
                if (distSq > berthSq || distSq >= nearestSq) continue;
                Cell nearby = cells.get(cellKey(center.x() + dx, center.z() + dz));
                boolean blocked = nearby == null
                    || Math.abs(nearby.y() - center.y()) > MAX_VERTICAL_STEP
                    || (nearby.hazards() & (HazardFlags.LAVA | HazardFlags.UNSTABLE)) != 0;
                if (blocked) nearestSq = distSq;
            }
        }
        if (nearestSq == Integer.MAX_VALUE) return 0.0;
        double nearest = Math.sqrt(nearestSq);
        return OBSTACLE_BERTH_WEIGHT * Math.max(0.0, berth - nearest);
    }

    // [7]
    static double slopeStepPenalty(int climbRunBefore, int rise) {
        double penalty = 0.0;
        for (int k = 0; k < rise; k++) {
            penalty += Math.min(SLOPE_STEP_MAX, SLOPE_STEP_BASE + SLOPE_STEP_ESCALATION * (climbRunBefore + k));
        }
        return penalty;
    }

    private static double heuristic(Cell from, Cell target) {
        return Math.hypot(target.x() - from.x(), target.z() - from.z());
    }

    private static List<Cell> reconstruct(SearchNode node) {
        List<Cell> reversed = new ArrayList<>();
        SearchNode cursor = node;
        while (cursor != null) {
            reversed.add(cursor.cell);
            cursor = cursor.parent;
        }
        List<Cell> path = new ArrayList<>(reversed.size());
        for (int i = reversed.size() - 1; i >= 0; i--) path.add(reversed.get(i));
        return List.copyOf(path);
    }

    // [8]

    private static final double LOS_SAMPLE_STEP = 0.5;
    private static final double RESAMPLE_SPACING = 1.25;
    private static final double NEAR_STRAIGHT_RAD = Math.toRadians(7.0);

    // Package-private for unit testing (PathRoutePlannerSmoothingTest); otherwise treat as internal.
    static List<Vec3> smoothPath(List<Cell> path, Map<Long, Cell> cells, double clearance, double turnRadius) {
        if (path.size() < 2) return toWaypoints(path);
        List<Cell> corners = stringPull(path, cells, clearance);
        List<Vec3> polyline = new ArrayList<>(corners.size());
        for (Cell c : corners) polyline.add(worldPoint(c));
        List<Vec3> filleted = filletCorners(polyline, cells, clearance, turnRadius);
        List<Vec3> resampled = resample(filleted, RESAMPLE_SPACING);
        if (resampled.size() > MAX_ROUTE_POINTS) {
            double totalLen = pathLength(resampled);
            resampled = resample(filleted, Math.max(RESAMPLE_SPACING, totalLen / (MAX_ROUTE_POINTS - 1)));
        }
        return resampled.size() >= 2 ? List.copyOf(resampled) : toWaypoints(path);
    }

    private static Vec3 worldPoint(Cell c) {
        return new Vec3(c.x() + 0.5, c.y() + 1.0, c.z() + 0.5);
    }

    // Greedy string-pull: from each anchor, jump to the farthest path node still reachable by a clear,
    // clearance-respecting straight line. Collapses the staircase into straights plus real bends.
    static List<Cell> stringPull(List<Cell> path, Map<Long, Cell> cells, double clearance) {
        if (path.size() <= 2) return path;
        List<Cell> corners = new ArrayList<>();
        corners.add(path.get(0));
        int anchor = 0;
        while (anchor < path.size() - 1) {
            int farthest = anchor + 1;
            for (int j = path.size() - 1; j > anchor + 1; j--) {
                if (lineOfSightClear(path.get(anchor), path.get(j), cells, clearance)) {
                    farthest = j;
                    break;
                }
            }
            corners.add(path.get(farthest));
            anchor = farthest;
        }
        return corners;
    }

    private static boolean lineOfSightClear(Cell a, Cell b, Map<Long, Cell> cells, double clearance) {
        return lineOfSightClearToXZ(a, b.x() + 0.5, b.z() + 0.5, cells, clearance);
    }

    // LOS from a start cell to an arbitrary world XZ (also used to gate the home-stretch straight shot).
    private static boolean lineOfSightClearToPoint(Cell a, Vec3 target, Map<Long, Cell> cells, double clearance) {
        return lineOfSightClearToXZ(a, target.x, target.z, cells, clearance);
    }

    private static boolean lineOfSightClearToXZ(Cell a, double tx, double tz, Map<Long, Cell> cells, double clearance) {
        double x0 = a.x() + 0.5, z0 = a.z() + 0.5;
        double dist = Math.hypot(tx - x0, tz - z0);
        int steps = Math.max(1, (int) Math.ceil(dist / LOS_SAMPLE_STEP));
        int lastX = a.x(), lastZ = a.z(), lastY = a.y();
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int cx = (int) Math.floor(x0 + (tx - x0) * t);
            int cz = (int) Math.floor(z0 + (tz - z0) * t);
            if (cx == lastX && cz == lastZ) continue;
            Cell c = cells.get(cellKey(cx, cz));
            if (c == null) return false;
            if (Math.abs(c.y() - lastY) > MAX_VERTICAL_STEP) return false;
            if (!hasClearance(cells, c, clearance)) return false;
            lastX = cx; lastZ = cz; lastY = c.y();
        }
        return true;
    }

    // [9]
    private static final double[] FILLET_TRY_FACTORS = { 1.0, 0.6, 0.35 };

    static List<Vec3> filletCorners(List<Vec3> poly, Map<Long, Cell> cells, double clearance, double turnRadius) {
        if (poly.size() <= 2) return poly;
        List<Vec3> out = new ArrayList<>();
        out.add(poly.get(0));
        for (int i = 1; i < poly.size() - 1; i++) {
            Vec3 prev = poly.get(i - 1), cur = poly.get(i), next = poly.get(i + 1);
            double inX = cur.x - prev.x, inZ = cur.z - prev.z;
            double outX = next.x - cur.x, outZ = next.z - cur.z;
            double inLen = Math.hypot(inX, inZ), outLen = Math.hypot(outX, outZ);
            if (inLen < 1.0e-3 || outLen < 1.0e-3) { out.add(cur); continue; }
            double uix = inX / inLen, uiz = inZ / inLen;
            double uox = outX / outLen, uoz = outZ / outLen;
            double dot = clampDouble(uix * uox + uiz * uoz, -1.0, 1.0);
            double turn = Math.acos(dot);
            if (turn < NEAR_STRAIGHT_RAD) { out.add(cur); continue; }
            // Trim distance: larger = gentler curve, bounded by the turn radius and half of each edge
            // so adjacent fillets never overlap.
            double maxT = Math.min(turnRadius, Math.min(inLen * 0.5, outLen * 0.5));
            List<Vec3> arc = null;
            for (double factor : FILLET_TRY_FACTORS) {
                double t = maxT * factor;
                if (t < 0.5) break;
                arc = tryFillet(cur, uix, uiz, uox, uoz, t, turn, cells, clearance);
                if (arc != null) break;
            }
            if (arc != null) {
                out.addAll(arc);
            } else {
                out.add(cur); // no radius fits without clipping an obstacle; keep the sharp corner
            }
        }
        out.add(poly.get(poly.size() - 1));
        return out;
    }

    // [10]
    private static List<Vec3> tryFillet(Vec3 cur, double uix, double uiz, double uox, double uoz, double t,
                                        double turn, Map<Long, Cell> cells, double clearance) {
        Vec3 p1 = new Vec3(cur.x - uix * t, cur.y, cur.z - uiz * t);
        Vec3 p2 = new Vec3(cur.x + uox * t, cur.y, cur.z + uoz * t);
        int samples = Math.max(4, (int) Math.ceil(turn / Math.toRadians(18.0)) * 2);
        List<Vec3> arc = new ArrayList<>(samples + 1);
        int refY = (int) Math.floor(cur.y - 1.0);
        for (int s = 0; s <= samples; s++) {
            double u = (double) s / samples;
            double omu = 1.0 - u;
            double ax = omu * omu * p1.x + 2.0 * omu * u * cur.x + u * u * p2.x;
            double az = omu * omu * p1.z + 2.0 * omu * u * cur.z + u * u * p2.z;
            double ay = omu * omu * p1.y + 2.0 * omu * u * cur.y + u * u * p2.y;
            if (!worldCellDrivable(cells, ax, az, clearance, refY)) return null;
            arc.add(new Vec3(ax, ay, az));
        }
        return arc;
    }

    private static boolean worldCellDrivable(Map<Long, Cell> cells, double wx, double wz, double clearance, int refY) {
        Cell c = cells.get(cellKey((int) Math.floor(wx), (int) Math.floor(wz)));
        if (c == null) return false;
        if (Math.abs(c.y() - refY) > MAX_VERTICAL_STEP + 1) return false;
        return hasClearance(cells, c, clearance);
    }

    // Even arc-length resample so the follower's projection and lookahead stay well conditioned.
    static List<Vec3> resample(List<Vec3> points, double spacing) {
        if (points.size() <= 2 || spacing <= 1.0e-3) return points;
        List<Vec3> out = new ArrayList<>();
        out.add(points.get(0));
        double acc = 0.0;
        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1);
            Vec3 b = points.get(i);
            double segLen = Math.hypot(b.x - a.x, b.z - a.z);
            if (segLen < 1.0e-6) continue;
            double pos = 0.0;
            while (acc + (segLen - pos) >= spacing) {
                double advance = spacing - acc;
                pos += advance;
                double t = pos / segLen;
                out.add(new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t));
                acc = 0.0;
            }
            acc += (segLen - pos);
        }
        Vec3 last = points.get(points.size() - 1);
        Vec3 tail = out.get(out.size() - 1);
        if (Math.hypot(last.x - tail.x, last.z - tail.z) > 1.0e-3) out.add(last);
        return out;
    }

    private static boolean isPassable(Passability passability, int hazards, UnknownPolicy unknownPolicy) {
        if ((hazards & HazardFlags.LAVA) != 0) return false;
        if ((hazards & HazardFlags.UNSTABLE) != 0) return false;
        return switch (passability) {
            case PASSABLE, MARGINAL -> true;
            case UNKNOWN -> false;
            case BLOCKED -> false;
        };
    }

    private static List<Vec3> toWaypoints(List<Cell> path) {
        List<Vec3> waypoints = new ArrayList<>(path.size());
        for (Cell cell : path) {
            waypoints.add(new Vec3(cell.x() + 0.5, cell.y() + 1.0, cell.z() + 0.5));
        }
        return List.copyOf(waypoints);
    }

    // [11]
    static List<Float> vehicleSpeeds(List<Vec3> waypoints, float cappedSpeed, int hazards, double turnRadius) {
        float hazardSpeed = (hazards & (HazardFlags.TWO_BLOCK | HazardFlags.LIQUID | HazardFlags.UNKNOWN_CHUNK)) != 0
            ? Math.min(cappedSpeed, 4.0f) : cappedSpeed;
        int n = waypoints.size();
        if (n == 0) return List.of();
        float[] speeds = new float[n];
        final double aLat = 3.2;   // comfortable lateral acceleration (blocks/s^2)
        final double aDecel = 3.5; // comfortable braking (blocks/s^2)
        for (int i = 0; i < n; i++) {
            float speed = hazardSpeed;
            double radius = localRadius(waypoints, i, 2.0);
            if (Double.isFinite(radius) && radius > 0.0) {
                double vCurve = Math.sqrt(aLat * radius);
                speed = (float) Math.min(speed, Math.max(2.0, vCurve));
            }
            speeds[i] = clamp(speed, 1.0f, cappedSpeed);
        }
        for (int i = n - 2; i >= 0; i--) {
            double d = Math.hypot(waypoints.get(i + 1).x - waypoints.get(i).x,
                                  waypoints.get(i + 1).z - waypoints.get(i).z);
            float reachable = (float) Math.sqrt(speeds[i + 1] * speeds[i + 1] + 2.0 * aDecel * d);
            if (speeds[i] > reachable) speeds[i] = reachable;
        }
        List<Float> out = new ArrayList<>(n);
        for (float s : speeds) out.add(s);
        return List.copyOf(out);
    }

    // Local turn radius at index i via the Menger curvature of three points spaced ~arcDist blocks apart
    // (so smooth resampled curves are still detected instead of reading as near-straight).
    static double localRadius(List<Vec3> waypoints, int i, double arcDist) {
        int n = waypoints.size();
        if (i <= 0 || i >= n - 1) return Double.POSITIVE_INFINITY;
        int a = i;
        double back = 0.0;
        while (a > 0 && back < arcDist) { back += segLen(waypoints, a - 1, a); a--; }
        int b = i;
        double fwd = 0.0;
        while (b < n - 1 && fwd < arcDist) { fwd += segLen(waypoints, b, b + 1); b++; }
        Vec3 pa = waypoints.get(a), pb = waypoints.get(i), pc = waypoints.get(b);
        double area2 = Math.abs((pb.x - pa.x) * (pc.z - pa.z) - (pc.x - pa.x) * (pb.z - pa.z));
        if (area2 < 1.0e-6) return Double.POSITIVE_INFINITY;
        double ab = Math.hypot(pb.x - pa.x, pb.z - pa.z);
        double bc = Math.hypot(pc.x - pb.x, pc.z - pb.z);
        double ca = Math.hypot(pa.x - pc.x, pa.z - pc.z);
        return (ab * bc * ca) / (2.0 * area2);
    }

    private static double segLen(List<Vec3> waypoints, int i, int j) {
        return Math.hypot(waypoints.get(j).x - waypoints.get(i).x, waypoints.get(j).z - waypoints.get(i).z);
    }

    private static double pathLength(List<Vec3> waypoints) {
        double total = 0.0;
        for (int i = 1; i < waypoints.size(); i++) {
            Vec3 a = waypoints.get(i - 1);
            Vec3 b = waypoints.get(i);
            total += Math.hypot(b.x - a.x, b.z - a.z);
        }
        return total;
    }

    private static int pathHazards(List<Cell> path) {
        int hazards = 0;
        for (Cell cell : path) hazards |= cell.hazards();
        return hazards;
    }

    private static float confidence(boolean reachedTarget, int pathPoints, int usableCells, int hazards) {
        if (pathPoints < 2) return 0.0f;
        float base = reachedTarget ? 0.76f : 0.54f;
        float coverage = Math.min(0.18f, usableCells / 512.0f);
        float hazardPenalty = (hazards & HazardFlags.UNKNOWN_CHUNK) != 0 ? 0.16f
            : (hazards & (HazardFlags.LIQUID | HazardFlags.TWO_BLOCK)) != 0 ? 0.08f : 0.0f;
        return Math.max(0.05f, Math.min(1.0f, base + coverage - hazardPenalty));
    }

    private static PlannedRoute empty(String status, @Nullable UUID scannerId, int inspected, int usable, int blocked,
                                      int hazardFlags, PlannerJobStage stage, int progressPercent, int expandedStates) {
        RouteGuidance guidance = new RouteGuidance(routeId(scannerId, BlockPos.ZERO, List.of(), hazardFlags),
            "No 5x5 route", scannerId == null ? "" : scannerId.toString(), 0.0f,
            List.of(), List.of(), List.of(), 0.0, 0L, hazardFlags);
        return new PlannedRoute(guidance, status, inspected, usable, blocked, hazardFlags,
            stage == null ? PlannerJobStage.HOLD : stage, clamp(progressPercent, 0, 100), expandedStates, false);
    }

    private static UUID routeId(@Nullable UUID scannerId, BlockPos scannerPos, List<Cell> path, int hazardFlags) {
        StringBuilder seed = new StringBuilder("silhouette-a-star:")
            .append(scannerId == null ? "none" : scannerId)
            .append(':').append(scannerPos == null ? 0L : scannerPos.asLong())
            .append(':').append(hazardFlags);
        for (Cell cell : path) seed.append(':').append(cell.x()).append(',').append(cell.y()).append(',').append(cell.z());
        return UUID.nameUUIDFromBytes(seed.toString().getBytes(StandardCharsets.UTF_8));
    }

    static long cellKey(int x, int z) {
        return ((long) x & 0xffffffffL) | (((long) z & 0xffffffffL) << 32);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record PlannedRoute(RouteGuidance guidance, String status, int inspectedCells, int usableCells,
                               int blockedCells, int hazardFlags, PlannerJobStage stage, int progressPercent,
                               int expandedStates, boolean budgetExhausted) {
    }

    private record GridRoute(List<Cell> path, int expandedStates, boolean reachedTarget) {
    }

    private record QueueEntry(long key, double f, double g, double tieBreak) {
    }

    record Cell(int x, int y, int z, int hazards) {
        long key() {
            return cellKey(x, z);
        }
    }

    private record CellScore(Cell cell, double score) {
    }

    private record GridFacing(int forwardX, int forwardZ, int rightX, int rightZ) {
        static GridFacing fromYaw(float yaw) {
            float normalized = yaw % 360f;
            if (normalized < -180f) normalized += 360f;
            if (normalized > 180f) normalized -= 360f;
            if (normalized > 45f && normalized <= 135f) return new GridFacing(-1, 0, 0, -1);
            if (normalized < -45f && normalized >= -135f) return new GridFacing(1, 0, 0, 1);
            if (normalized > 135f || normalized <= -135f) return new GridFacing(0, -1, 1, 0);
            return new GridFacing(0, 1, -1, 0);
        }
    }

    private static final class SearchNode {
        private final Cell cell;
        private double g = Double.POSITIVE_INFINITY;
        private double f = Double.POSITIVE_INFINITY;
        private boolean closed;
        private int climbRun;
        @Nullable private SearchNode parent;

        private SearchNode(Cell cell) {
            this.cell = cell;
        }
    }

    private record DirectionBasis(double forwardX, double forwardZ, double rightX, double rightZ) {
        static DirectionBasis fromYaw(float yaw) {
            double radians = Math.toRadians(yaw);
            double forwardX = -Math.sin(radians);
            double forwardZ = Math.cos(radians);
            return new DirectionBasis(forwardX, forwardZ, -forwardZ, forwardX);
        }

        double forward(double dx, double dz) {
            return dx * forwardX + dz * forwardZ;
        }

        double side(double dx, double dz) {
            return dx * rightX + dz * rightZ;
        }
    }
}
