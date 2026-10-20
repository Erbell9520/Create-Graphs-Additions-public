package com.createstats.train.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.createstats.statsmk3.ui.Easing;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.train.smart.RailSnapshot;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// stateful camera. the old one refit every frame so one train leaving the bounds rescaled everything
@OnlyIn(Dist.CLIENT)
public final class MapView {
    private static final float MIN_SCALE = 0.004f;
    private static final float MAX_SCALE = 6.0f;

    private static final int TRAIL_POINTS = 14;
    private static final float TRAIL_SAMPLE_SECONDS = 0.1f;

    private final Spring centreX = Spring.ui(0.0f);
    private final Spring centreY = Spring.ui(0.0f);
    private final Spring scale = Spring.ui(0.1f);

    private Rect viewport = Rect.EMPTY;
    private boolean everFitted;

    private MapLayout.Mode mode = MapLayout.Mode.GEOGRAPHIC;
    private MapLayout.Mode previousMode = MapLayout.Mode.GEOGRAPHIC;
    private float morph = 1.0f;

    private static final float MORPH_SECONDS = 0.85f;

    private UUID selectedTrain;
    private int selectedNode = -1;
    private int selectedLine = -1;
    private UUID followTrain;

    private int hoverNode = -1;
    private UUID hoverTrain;

    private String search = "";
    private boolean searchFocused;

    private final Set<String> expandedGroups = new LinkedHashSet<>();
    private boolean autoGroup = true;
    private String dimension = "";

    private boolean showGrid = true;
    private boolean showLabels = true;
    private boolean animate = true;
    private boolean showTrack = true;
    // dim idle services, big railways are unreadable otherwise
    private boolean dimIdle = true;

    public enum Gesture {
        PENDING,

        PANNING,

        MOVING_NODE,
        NONE
    }

    private static final float DRAG_SLOP = 3.5f;

    private Gesture gesture = Gesture.NONE;
    private int grabbedNode = -1;
    private double pressX;
    private double pressY;
    private double lastX;
    private double lastY;

    private float flingX;
    private float flingY;

    private float cursorX = Float.NaN;
    private float cursorY = Float.NaN;

    private float playerX = Float.NaN;
    private float playerZ;
    private float playerFacing;

    private final Spring focusFade = Spring.ui(0.0f);

    private final Map<UUID, Motion> motions = new HashMap<>();
    private float clock;

    private final float[][] runPath = new float[1][];

    public static final class Motion {
        public float x;
        public float y;
        public float headingX = 1.0f;
        public float headingY;

        public final float[] trail = new float[TRAIL_POINTS * 2];
        public int trailCount;
        int trailHead;
        float sampleTimer;
        boolean seeded;

        public float trailX(int i) {
            return this.trail[((this.trailHead - this.trailCount + i + TRAIL_POINTS)
                    % TRAIL_POINTS) * 2];
        }

        public float trailY(int i) {
            return this.trail[((this.trailHead - this.trailCount + i + TRAIL_POINTS)
                    % TRAIL_POINTS) * 2 + 1];
        }
    }

    public MapLayout.Mode mode() {
        return this.mode;
    }

    public MapLayout.Mode previousMode() {
        return this.previousMode;
    }

    public float morph() {
        return Easing.easeInOut(this.morph);
    }

    public boolean morphing() {
        return this.morph < 1.0f;
    }

    public void setMode(MapLayout.Mode next) {
        if (next == this.mode) {
            return;
        }

        this.previousMode = this.morph < 0.5f ? this.previousMode : this.mode;
        this.mode = next;
        this.morph = 0.0f;
    }

    public UUID selectedTrain() {
        return this.selectedTrain;
    }

    public void selectTrain(UUID id) {
        this.selectedTrain = id == null || id.equals(this.selectedTrain) ? null : id;
        if (this.selectedTrain == null) {
            this.followTrain = null;
        }
    }

    public int selectedNode() {
        return this.selectedNode;
    }

    public void selectNode(int index) {
        this.selectedNode = index == this.selectedNode ? -1 : index;
    }

    public int selectedLine() {
        return this.selectedLine;
    }

    public void selectLine(int index) {
        this.selectedLine = index == this.selectedLine ? -1 : index;
        this.focusFade.set(this.selectedLine >= 0 ? 1.0f : 0.0f);
    }

    public float focus() {
        return this.focusFade.value();
    }

    public UUID followTrain() {
        return this.followTrain;
    }

    public void toggleFollow(UUID id) {
        this.followTrain = id != null && id.equals(this.followTrain) ? null : id;
        if (this.followTrain != null) {
            this.selectedTrain = this.followTrain;
        }
    }

    public int hoverNode() {
        return this.hoverNode;
    }

    public UUID hoverTrain() {
        return this.hoverTrain;
    }

    public void setHover(int node, UUID train) {
        this.hoverNode = node;
        this.hoverTrain = train;
    }

    public String search() {
        return this.search;
    }

    public void setSearch(String s) {
        this.search = s == null ? "" : s;
    }

    public boolean searchFocused() {
        return this.searchFocused;
    }

    public void setSearchFocused(boolean focused) {
        this.searchFocused = focused;
    }

    public boolean matchesSearch(String name) {
        return !this.search.isEmpty() && name != null
                && name.toLowerCase(Locale.ROOT).contains(this.search.toLowerCase(Locale.ROOT));
    }

    public Set<String> expandedGroups() {
        return this.expandedGroups;
    }

    public void toggleGroup(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (!this.expandedGroups.remove(key)) {
            this.expandedGroups.add(key);
        }
    }

    public boolean autoGroup() {
        return this.autoGroup;
    }

    public void setAutoGroup(boolean on) {
        this.autoGroup = on;
    }

    public String dimension() {
        return this.dimension;
    }

    public void setDimension(String id) {
        this.dimension = id == null ? "" : id;
    }

    public boolean showGrid() {
        return this.showGrid;
    }

    public void toggleGrid() {
        this.showGrid = !this.showGrid;
    }

    public boolean showLabels() {
        return this.showLabels;
    }

    public void toggleLabels() {
        this.showLabels = !this.showLabels;
    }

    public boolean animate() {
        return this.animate;
    }

    public void toggleAnimate() {
        this.animate = !this.animate;
    }

    public boolean dimIdle() {
        return this.dimIdle;
    }

    public void toggleDimIdle() {
        this.dimIdle = !this.dimIdle;
    }

    public boolean showTrack() {
        return this.showTrack;
    }

    public void toggleTrack() {
        this.showTrack = !this.showTrack;
    }

    public float clock() {
        return this.clock;
    }

    public Rect viewport() {
        return this.viewport;
    }

    public float screenX(float layoutX) {
        return this.viewport.centerX() + (layoutX - this.centreX.value()) * this.scale.value();
    }

    public float screenY(float layoutY) {
        return this.viewport.centerY() + (layoutY - this.centreY.value()) * this.scale.value();
    }

    public float layoutX(double screenX) {
        return this.centreX.value()
                + (float) (screenX - this.viewport.centerX()) / this.scale.value();
    }

    public float layoutY(double screenY) {
        return this.centreY.value()
                + (float) (screenY - this.viewport.centerY()) / this.scale.value();
    }

    public float scale() {
        return this.scale.value();
    }

    public void setViewport(Rect r) {
        this.viewport = r;
    }

    public void zoomAt(double screenX, double screenY, float factor) {
        final float before = this.scale.target();
        final float after = Math.max(MIN_SCALE, Math.min(MAX_SCALE, before * factor));
        if (Math.abs(after - before) < 1.0e-6f) {
            return;
        }
        final float anchorX = this.centreX.target()
                + (float) (screenX - this.viewport.centerX()) / before;
        final float anchorY = this.centreY.target()
                + (float) (screenY - this.viewport.centerY()) / before;
        this.scale.set(after);
        this.centreX.set(anchorX - (float) (screenX - this.viewport.centerX()) / after);
        this.centreY.set(anchorY - (float) (screenY - this.viewport.centerY()) / after);
        this.followTrain = null;
    }

    public void panBy(double dxPixels, double dyPixels) {
        final float s = Math.max(1.0e-5f, this.scale.value());
        this.centreX.jump(this.centreX.value() - (float) (dxPixels / s));
        this.centreY.jump(this.centreY.value() - (float) (dyPixels / s));
        this.followTrain = null;
    }

    public void press(double mx, double my, int grabbable, MapLayout layout) {
        this.gesture = Gesture.PENDING;
        this.pressX = mx;
        this.pressY = my;
        this.lastX = mx;
        this.lastY = my;
        this.flingX = 0.0f;
        this.flingY = 0.0f;
        this.grabbedNode = MapLayout.arrangeable(this.mode) && layout != null ? grabbable : -1;
    }

    public boolean drag(double mx, double my, boolean forcePan, MapLayout layout, float dt) {
        if (this.gesture == Gesture.NONE) {
            return false;
        }
        if (this.gesture == Gesture.PENDING) {
            final double travelled = Math.hypot(mx - this.pressX, my - this.pressY);
            if (travelled < DRAG_SLOP) {
                return false;
            }
            this.gesture = !forcePan && this.grabbedNode >= 0 ? Gesture.MOVING_NODE : Gesture.PANNING;
        }

        final double dx = mx - this.lastX;
        final double dy = my - this.lastY;
        this.lastX = mx;
        this.lastY = my;

        if (this.gesture == Gesture.MOVING_NODE && layout != null) {
            layout.pin(this.grabbedNode, layoutX(mx), layoutY(my));
            return true;
        }

        panBy(dx, dy);
        if (dt > 1.0e-4f) {
            final float s = Math.max(1.0e-5f, this.scale.value());

            this.flingX = Easing.approach(this.flingX, (float) (-dx / s / dt), 0.05f, dt);
            this.flingY = Easing.approach(this.flingY, (float) (-dy / s / dt), 0.05f, dt);
        }
        return true;
    }

    public boolean release(MapLayout layout) {
        final Gesture what = this.gesture;
        this.gesture = Gesture.NONE;
        if (what == Gesture.MOVING_NODE && layout != null && this.grabbedNode >= 0) {
            layout.pin(this.grabbedNode,
                    layout.snapX(layout.x(this.grabbedNode, MapLayout.Mode.SCHEMATIC)),
                    layout.snapY(layout.y(this.grabbedNode, MapLayout.Mode.SCHEMATIC)));
        }
        this.grabbedNode = -1;
        if (what == Gesture.PANNING) {
            glide();
        }
        return what == Gesture.PENDING;
    }

    private void glide() {
        final float speed = (float) Math.hypot(this.flingX, this.flingY);
        if (speed < 12.0f) {
            return;
        }
        final float seconds = 0.34f;
        this.centreX.set(this.centreX.value() + this.flingX * seconds);
        this.centreY.set(this.centreY.value() + this.flingY * seconds);
        this.flingX = 0.0f;
        this.flingY = 0.0f;
    }

    public Gesture gesture() {
        return this.gesture;
    }

    public boolean dragging() {
        return this.gesture == Gesture.PANNING || this.gesture == Gesture.MOVING_NODE;
    }

    public int grabbedNode() {
        return this.gesture == Gesture.MOVING_NODE ? this.grabbedNode : -1;
    }

    public void cancelGesture() {
        this.gesture = Gesture.NONE;
        this.grabbedNode = -1;
        this.flingX = 0.0f;
        this.flingY = 0.0f;
    }

    public void nudge(float dxPixels, float dyPixels) {
        final float s = Math.max(1.0e-5f, this.scale.value());
        this.centreX.set(this.centreX.target() + dxPixels / s);
        this.centreY.set(this.centreY.target() + dyPixels / s);
        this.followTrain = null;
    }

    public void zoomBy(float factor) {
        zoomAt(this.viewport.centerX(), this.viewport.centerY(), factor);
    }

    public void setCursor(float layoutXAt, float layoutYAt) {
        this.cursorX = layoutXAt;
        this.cursorY = layoutYAt;
    }

    public void clearCursor() {
        this.cursorX = Float.NaN;
        this.cursorY = Float.NaN;
    }

    public boolean hasCursor() {
        return !Float.isNaN(this.cursorX);
    }

    public float cursorLayoutX() {
        return this.cursorX;
    }

    public float cursorLayoutY() {
        return this.cursorY;
    }

    public void setPlayer(float worldX, float worldZ, float facingDegrees) {
        this.playerX = worldX;
        this.playerZ = worldZ;
        this.playerFacing = facingDegrees;
    }

    public void clearPlayer() {
        this.playerX = Float.NaN;
    }

    public boolean hasPlayer() {
        return !Float.isNaN(this.playerX);
    }

    public float playerX() {
        return this.playerX;
    }

    public float playerZ() {
        return this.playerZ;
    }

    public float playerFacing() {
        return this.playerFacing;
    }

    public void centreOnPlayer() {
        if (hasPlayer()) {
            centreOn(this.playerX, this.playerZ);
        }
    }

    public void centreOn(float layoutX, float layoutY) {
        this.centreX.set(layoutX);
        this.centreY.set(layoutY);
        this.followTrain = null;
    }

    public void centreOn(float layoutX, float layoutY, float wantScale) {
        centreOn(layoutX, layoutY);
        this.scale.set(Math.max(MIN_SCALE, Math.min(MAX_SCALE, wantScale)));
    }

    public void centreOn(MapLayout layout, int node) {
        if (node < 0) {
            return;
        }
        centreOn(layout.x(node, this.mode), layout.y(node, this.mode));
    }

    public void fit(RailMap map, MapLayout layout, boolean immediate) {
        if (map.isEmpty() || this.viewport.isEmpty()) {
            return;
        }
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < map.nodes().size(); i++) {
            if (!layout.visible(i, this.mode)) {
                continue;
            }
            final float x = layout.x(i, this.mode);
            final float y = layout.y(i, this.mode);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        if (minX > maxX) {
            return;
        }

        final float spanX = Math.max(maxX - minX, 24.0f);
        final float spanY = Math.max(maxY - minY, 24.0f);
        final float margin = 0.84f;
        final float want = Math.max(MIN_SCALE, Math.min(MAX_SCALE,
                Math.min(this.viewport.w * margin / spanX, this.viewport.h * margin / spanY)));

        this.centreX.set((minX + maxX) * 0.5f);
        this.centreY.set((minY + maxY) * 0.5f);
        this.scale.set(want);
        if (immediate) {
            this.centreX.jump((minX + maxX) * 0.5f);
            this.centreY.jump((minY + maxY) * 0.5f);
            this.scale.jump(want);
        }
        this.everFitted = true;
        this.followTrain = null;
    }

    public void fitLine(RailMap map, MapLayout layout, int lineIndex) {
        if (lineIndex < 0 || lineIndex >= map.lines().size()) {
            return;
        }
        final RailMap.Line line = map.lines().get(lineIndex);
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (final int node : line.nodes) {
            final float x = layout.x(node, this.mode);
            final float y = layout.y(node, this.mode);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        if (minX > maxX || this.viewport.isEmpty()) {
            return;
        }
        final float spanX = Math.max(maxX - minX, 24.0f);
        final float spanY = Math.max(maxY - minY, 24.0f);
        this.centreX.set((minX + maxX) * 0.5f);
        this.centreY.set((minY + maxY) * 0.5f);
        this.scale.set(Math.max(MIN_SCALE, Math.min(MAX_SCALE,
                Math.min(this.viewport.w * 0.7f / spanX, this.viewport.h * 0.7f / spanY))));
    }

    public void frameTrains(java.util.Collection<UUID> ids, float marginFactor) {
        if (ids == null || this.viewport.isEmpty()) {
            return;
        }
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        int found = 0;
        for (final UUID id : ids) {
            final Motion m = this.motions.get(id);
            if (m == null) {
                continue;
            }
            minX = Math.min(minX, m.x);
            maxX = Math.max(maxX, m.x);
            minY = Math.min(minY, m.y);
            maxY = Math.max(maxY, m.y);
            found++;
        }
        if (found == 0) {
            return;
        }
        final float spanX = Math.max(maxX - minX, 48.0f);
        final float spanY = Math.max(maxY - minY, 48.0f);
        final float margin = Math.max(0.1f, marginFactor);
        this.centreX.set((minX + maxX) * 0.5f);
        this.centreY.set((minY + maxY) * 0.5f);
        this.scale.set(Math.max(MIN_SCALE, Math.min(MAX_SCALE,
                Math.min(this.viewport.w * margin / spanX, this.viewport.h * margin / spanY))));
        this.followTrain = null;
    }

    public boolean everFitted() {
        return this.everFitted;
    }

    public void update(float dt, RailMap map, MapLayout layout) {
        this.clock += dt;
        this.centreX.step(dt);
        this.centreY.step(dt);
        this.scale.step(dt);
        this.focusFade.step(dt);

        if (this.morph < 1.0f) {
            this.morph = Math.min(1.0f, this.morph + dt / MORPH_SECONDS);
        }
        updateMotion(dt, map, layout);

        if (this.followTrain != null) {
            final Motion m = this.motions.get(this.followTrain);
            if (m != null) {
                this.centreX.set(m.x);
                this.centreY.set(m.y);
            }
        }
    }

    private void updateMotion(float dt, RailMap map, MapLayout layout) {
        final RailSnapshot snap = map.snapshot();
        final Set<UUID> alive = new LinkedHashSet<>();

        for (final RailSnapshot.TrainState t : snap.trains()) {
            final float[] target = trainLayoutPosition(map, layout, t);
            if (target == null) {
                continue;
            }
            alive.add(t.id);
            final Motion m = this.motions.computeIfAbsent(t.id, k -> new Motion());
            if (!m.seeded) {
                m.x = target[0];
                m.y = target[1];
                m.seeded = true;
            }
            final float previousX = m.x;
            final float previousY = m.y;

            m.x = Easing.approach(m.x, target[0], 0.09f, dt);
            m.y = Easing.approach(m.y, target[1], 0.09f, dt);

            final float dx = m.x - previousX;
            final float dy = m.y - previousY;
            final float moved = (float) Math.sqrt(dx * dx + dy * dy);
            if (moved > 1.0e-3f) {
                m.headingX = Easing.approach(m.headingX, dx / moved, 0.08f, dt);
                m.headingY = Easing.approach(m.headingY, dy / moved, 0.08f, dt);
            }

            m.sampleTimer += dt;
            if (m.sampleTimer >= TRAIL_SAMPLE_SECONDS) {
                m.sampleTimer = 0.0f;
                m.trail[m.trailHead * 2] = m.x;
                m.trail[m.trailHead * 2 + 1] = m.y;
                m.trailHead = (m.trailHead + 1) % TRAIL_POINTS;
                m.trailCount = Math.min(TRAIL_POINTS, m.trailCount + 1);
            }
        }

        this.motions.keySet().retainAll(alive);
    }

    public Motion motion(UUID trainId) {
        return this.motions.get(trainId);
    }

    private float[] trainLayoutPosition(RailMap map, MapLayout layout, RailSnapshot.TrainState t) {
        final int toNode = map.nodeOfStation(t.destinationStation);
        final int docked = map.nodeOfStation(t.currentStation);

        final int fromNode = docked >= 0 ? docked : map.originNodeOf(t);
        final float geoWeight = geographicWeight();

        float geoX = (float) t.x;
        float geoY = (float) t.z;
        if (!t.positionValid) {
            if (toNode < 0 && fromNode < 0) {
                return null;
            }
            final int a = fromNode >= 0 ? fromNode : toNode;
            final int b = toNode >= 0 ? toNode : fromNode;
            final float[] at = alongRun(map, layout, a, b, t.legProgress(),
                    MapLayout.Mode.GEOGRAPHIC);
            geoX = at[0];
            geoY = at[1];
        }
        if (geoWeight > 0.999f) {
            return new float[]{geoX, geoY};
        }

        final MapLayout.Mode diagram = this.mode == MapLayout.Mode.GEOGRAPHIC
                ? this.previousMode : this.mode;
        float diagramX = geoX;
        float diagramY = geoY;
        if (fromNode >= 0 || toNode >= 0) {
            final int a = fromNode >= 0 ? fromNode : toNode;
            final int b = toNode >= 0 ? toNode : fromNode;
            if (layout.visible(a, diagram) && layout.visible(b, diagram)) {
                final float[] at = alongRun(map, layout, a, b,
                        a == b ? 0.0f : t.legProgress(), diagram);
                diagramX = at[0];
                diagramY = at[1];
            } else if (layout.visible(a, diagram)) {
                diagramX = layout.x(a, diagram);
                diagramY = layout.y(a, diagram);
            } else if (!layout.visible(a, diagram) && !layout.visible(b, diagram)) {
                return geoWeight > 0.01f ? new float[]{geoX, geoY} : null;
            }
        }
        return new float[]{
                diagramX + (geoX - diagramX) * geoWeight,
                diagramY + (geoY - diagramY) * geoWeight};
    }

    private float[] alongRun(RailMap map, MapLayout layout, int a, int b, float fraction,
                             MapLayout.Mode mode) {
        final float ax = layout.x(a, mode);
        final float ay = layout.y(a, mode);
        final float bx = layout.x(b, mode);
        final float by = layout.y(b, mode);
        final RailMap.Edge edge = map.edgeBetween(a, b);
        if (edge == null) {
            return new float[]{ax + (bx - ax) * fraction, ay + (by - ay) * fraction};
        }
        final int points = layout.edgePath(edge, mode, mode, 1.0f, this.runPath);
        if (points < 2) {
            return new float[]{ax + (bx - ax) * fraction, ay + (by - ay) * fraction};
        }

        final float t = edge.a == a ? fraction : 1.0f - fraction;
        final float[] at = MapLayout.along(this.runPath[0], points, t);
        return new float[]{at[0], at[1]};
    }

    public float geographicWeight() {
        float w = 0.0f;
        final float b = morph();
        if (this.previousMode == MapLayout.Mode.GEOGRAPHIC) {
            w += 1.0f - b;
        }
        if (this.mode == MapLayout.Mode.GEOGRAPHIC) {
            w += b;
        }
        return w;
    }

    public UUID trainAt(RailMap map, double mx, double my, float radius) {
        UUID best = null;
        double bestDistance = radius * radius;
        for (final RailSnapshot.TrainState t : map.snapshot().trains()) {
            final Motion m = this.motions.get(t.id);
            if (m == null) {
                continue;
            }
            final double dx = screenX(m.x) - mx;
            final double dy = screenY(m.y) - my;
            final double d = dx * dx + dy * dy;
            if (d < bestDistance) {
                bestDistance = d;
                best = t.id;
            }
        }
        return best;
    }

    public int nodeAt(RailMap map, MapLayout layout, double mx, double my, float radius) {
        int best = -1;
        double bestDistance = radius * radius;
        final float blend = morph();
        for (int i = 0; i < map.nodes().size(); i++) {
            if (layout.presence(i, this.previousMode, this.mode, blend) < 0.5f) {
                continue;
            }
            final double dx = screenX(layout.x(i, this.previousMode, this.mode, blend)) - mx;
            final double dy = screenY(layout.y(i, this.previousMode, this.mode, blend)) - my;
            final double d = dx * dx + dy * dy;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    public List<Integer> searchHits(RailMap map) {
        final List<Integer> out = new ArrayList<>();
        if (this.search.isEmpty()) {
            return out;
        }
        for (int i = 0; i < map.nodes().size(); i++) {
            if (matchesSearch(map.nodes().get(i).name)) {
                out.add(i);
            }
        }
        return out;
    }
}
