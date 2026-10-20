package com.createstats.train.map;

import java.util.UUID;

public final class TrainGraphView {
    public static final int[] SPANS = {60, 120, 300, 600, 900};
    public static final String[] SPAN_LABELS = {"1m", "2m", "5m", "10m", "15m"};

    private static final float FUTURE_FRACTION = 0.45f;

    private int lineIndex = -1;
    private int spanIndex = 2;

    private float scrollBack;

    private boolean evenSpacing;
    private boolean showPlanned = true;
    private boolean showConflicts = true;
    private boolean showHolds = true;
    private boolean showGrid = true;

    private UUID selectedTrain;
    private UUID hoverTrain;

    private float cursorTime = Float.NaN;
    private float cursorChain = Float.NaN;

    public int line() {
        return this.lineIndex;
    }

    public void setLine(int index) {
        if (this.lineIndex != index) {
            this.lineIndex = index;
            this.scrollBack = 0.0f;
        }
    }

    public void ensureLine(RailMap map) {
        if (map == null || map.lines().isEmpty()) {
            this.lineIndex = -1;
            return;
        }
        if (this.selectedTrain != null) {
            final int of = map.lineIndexOf(this.selectedTrain);
            if (of >= 0 && (this.lineIndex < 0 || this.lineIndex >= map.lines().size())) {
                this.lineIndex = of;
                return;
            }
        }
        if (this.lineIndex >= 0 && this.lineIndex < map.lines().size()
                && map.lines().get(this.lineIndex).nodes.size() > 1) {
            return;
        }
        int best = -1;
        int bestScore = -1;
        for (int i = 0; i < map.lines().size(); i++) {
            final RailMap.Line line = map.lines().get(i);
            if (line.nodes.size() < 2) {
                continue;
            }
            final int score = line.trains.size() * 100 + line.nodes.size();
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        this.lineIndex = best;
    }

    public float span() {
        return SPANS[this.spanIndex];
    }

    public int spanIndex() {
        return this.spanIndex;
    }

    public void setSpanIndex(int index) {
        this.spanIndex = Math.max(0, Math.min(SPANS.length - 1, index));
    }

    public void zoom(boolean in) {
        setSpanIndex(this.spanIndex + (in ? -1 : 1));
    }

    public float future() {
        return span() * FUTURE_FRACTION;
    }

    public float scrollBack() {
        return this.scrollBack;
    }

    public boolean live() {
        return this.scrollBack <= 0.5f;
    }

    public void scrollBy(float seconds, float available) {
        this.scrollBack = Math.max(0.0f, Math.min(Math.max(0.0f, available), this.scrollBack + seconds));
    }

    public void goLive() {
        this.scrollBack = 0.0f;
    }

    public boolean evenSpacing() {
        return this.evenSpacing;
    }

    public void toggleSpacing() {
        this.evenSpacing = !this.evenSpacing;
    }

    public boolean showPlanned() {
        return this.showPlanned;
    }

    public void togglePlanned() {
        this.showPlanned = !this.showPlanned;
    }

    public boolean showConflicts() {
        return this.showConflicts;
    }

    public void toggleConflicts() {
        this.showConflicts = !this.showConflicts;
    }

    public boolean showHolds() {
        return this.showHolds;
    }

    public void toggleHolds() {
        this.showHolds = !this.showHolds;
    }

    public boolean showGrid() {
        return this.showGrid;
    }

    public void toggleGrid() {
        this.showGrid = !this.showGrid;
    }

    public UUID selectedTrain() {
        return this.selectedTrain;
    }

    public void selectTrain(UUID id) {
        this.selectedTrain = id != null && id.equals(this.selectedTrain) ? null : id;
    }

    public void setSelectedTrain(UUID id) {
        this.selectedTrain = id;
    }

    public UUID hoverTrain() {
        return this.hoverTrain;
    }

    public void setHoverTrain(UUID id) {
        this.hoverTrain = id;
    }

    public void setCursor(float time, float chain) {
        this.cursorTime = time;
        this.cursorChain = chain;
    }

    public void clearCursor() {
        this.cursorTime = Float.NaN;
        this.cursorChain = Float.NaN;
    }

    public boolean hasCursor() {
        return !Float.isNaN(this.cursorTime);
    }

    public float cursorTime() {
        return this.cursorTime;
    }

    public float cursorChain() {
        return this.cursorChain;
    }

    public float rightEdgeTime(float now) {
        return now - this.scrollBack + future();
    }

    public float leftEdgeTime(float now) {
        return rightEdgeTime(now) - span() - future();
    }

    public float xOf(float time, float now, float plotX, float plotW) {
        final float left = leftEdgeTime(now);
        final float total = span() + future();
        return plotX + (time - left) / Math.max(1.0f, total) * plotW;
    }

    public float timeAt(float x, float now, float plotX, float plotW) {
        final float total = span() + future();
        return leftEdgeTime(now) + (x - plotX) / Math.max(1.0f, plotW) * total;
    }

    public float yOf(float chain, LineChainage chainage, float plotY, float plotH) {
        return plotY + fraction(chain, chainage) * plotH;
    }

    public float chainAt(float y, LineChainage chainage, float plotY, float plotH) {
        final float f = plotH <= 0.0f ? 0.0f : (y - plotY) / plotH;
        if (!this.evenSpacing || chainage == null || chainage.isEmpty()) {
            return chainage == null ? 0.0f : f * chainage.total;
        }
        final int legs = chainage.stopCount() - 1;
        final float scaled = Math.max(0.0f, Math.min(1.0f, f)) * legs;
        final int leg = Math.min(legs - 1, (int) scaled);
        final float within = scaled - leg;
        return chainage.chain[leg] + (chainage.chain[leg + 1] - chainage.chain[leg]) * within;
    }

    private float fraction(float chain, LineChainage chainage) {
        if (chainage == null || chainage.isEmpty() || chainage.total <= 0.0f) {
            return 0.0f;
        }
        if (!this.evenSpacing) {
            return chain / chainage.total;
        }
        final int legs = chainage.stopCount() - 1;
        for (int i = 0; i < legs; i++) {
            final float a = chainage.chain[i];
            final float b = chainage.chain[i + 1];
            if (chain <= b || i == legs - 1) {
                final float within = b - a <= 0.001f ? 0.0f : (chain - a) / (b - a);
                return (i + Math.max(0.0f, Math.min(1.0f, within))) / legs;
            }
        }
        return 1.0f;
    }
}
