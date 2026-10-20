package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.RectSpring;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PeerRoster {
    public interface Delegate {
        // key detail off size, never mode, or the morph steps
        void paintPeer(PanelSurface c, Peers.Peer peer, Rect r, float big, float mid,
                       boolean hovered, float alpha);

        float urgency(Peers.Peer peer);

        int statusColor(Peers.Peer peer);

        float[] compareSeries(Peers.Peer peer);

        boolean alarming(Peers.Peer peer);
    }

    public enum Mode {
        ROSTER, FOCUS, COMPARE
    }

    private static final float COACH_H = 28.0f;
    private static final float CHIP_W = 74.0f;
    private static final float CHIP_H_MAX = 62.0f;
    private static final float CHIP_H_MIN = 34.0f;
    private static final float CHIP_GAP = 8.0f;

    private static final int COMPARE_MAX = 4;

    private final String pageId;
    private final BlockPos panelPos;

    private final Map<String, RectSpring> rects = new HashMap<>();
    private final Map<String, Spring> badges = new HashMap<>();
    private final Spring coach = Spring.ui(0.0f);

    private List<Peers.Peer> peers = new ArrayList<>();
    private List<Integer> order = new ArrayList<>();
    private final List<String> compare = new ArrayList<>();

    private Mode mode = Mode.ROSTER;
    private String focusId = "";
    private String hoverId = "";
    private boolean sortHold;
    private boolean coachDismissed;
    private boolean first = true;

    private final Spring identify = Spring.ui(0.0f);

    private Rect area = Rect.EMPTY;

    public PeerRoster(String pageId, BlockPos panelPos) {
        this.pageId = pageId;
        this.panelPos = panelPos;
    }

    public Mode mode() {
        return this.mode;
    }

    public List<Peers.Peer> peers() {
        return this.peers;
    }

    public int size() {
        return this.peers.size();
    }

    public String hoveredId() {
        return this.hoverId;
    }

    public float identifyAmount() {
        return this.identify.value();
    }

    public Peers.Peer hovered() {
        return byId(this.hoverId);
    }

    public Peers.Peer focused() {
        final Peers.Peer p = byId(this.focusId);
        return p != null ? p : (this.peers.isEmpty() ? null : this.peers.get(rankedIndex(0)));
    }

    public boolean isCompared(Peers.Peer p) {
        return p != null && this.compare.contains(p.key);
    }

    public float coachAmount() {
        return this.coach.value();
    }

    private Peers.Peer byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (final Peers.Peer p : this.peers) {
            if (p.key.equals(id)) {
                return p;
            }
        }
        return null;
    }

    private int rankedIndex(int rank) {
        return this.order.isEmpty() ? 0 : this.order.get(Math.max(0, Math.min(rank, this.order.size() - 1)));
    }

    public void setPeers(List<Peers.Peer> next, Delegate delegate) {
        this.peers = next == null ? new ArrayList<>() : next;
        for (final Peers.Peer p : this.peers) {
            p.urgency = delegate.urgency(p);
            this.rects.computeIfAbsent(p.key, k -> new RectSpring());
            final Spring badge = this.badges.computeIfAbsent(p.key, k -> Spring.ui(0.0f));

            final boolean show = delegate.alarming(p)
                    && !p.key.equals(this.focusId)
                    && this.mode != Mode.ROSTER;
            badge.set(show ? 1.0f : 0.0f);
        }
        this.rects.keySet().removeIf(id -> byId(id) == null);
        this.badges.keySet().removeIf(id -> byId(id) == null);

        if (byId(this.focusId) == null) {
            this.focusId = "";
        }
        this.compare.removeIf(id -> byId(id) == null);

        if (this.first && !this.peers.isEmpty()) {
            this.first = false;
            rank();

            final String remembered = PeerFocusMemory.recall(this.panelPos, this.pageId);
            if (this.peers.size() == 1) {
                this.focusId = this.peers.get(0).key;
                this.mode = Mode.FOCUS;
            } else if (byId(remembered) != null) {
                this.focusId = remembered;
                this.mode = Mode.FOCUS;
            } else {
                this.mode = Mode.ROSTER;
            }
            layout(true);
        }

        if (this.peers.size() == 1 && this.mode != Mode.FOCUS) {
            this.focusId = this.peers.get(0).key;
            this.mode = Mode.FOCUS;
        }
    }

    private void rank() {
        final List<Integer> next = new ArrayList<>();
        for (int i = 0; i < this.peers.size(); i++) {
            next.add(i);
        }
        next.sort(Comparator.comparingDouble((Integer i) -> -this.peers.get(i).urgency)
                .thenComparingInt(i -> this.peers.get(i).index));
        this.order = next;
    }

    public float headerExtra() {
        return coachShowing() ? COACH_H * this.coach.value() : 0.0f;
    }

    private boolean coachShowing() {
        return !this.coachDismissed && (this.mode == Mode.ROSTER || this.peers.size() < 2);
    }

    private boolean railed() {
        return this.peers.size() > 1;
    }

    private Rect body() {
        return new Rect(this.area.x, this.area.y + headerExtra(),
                this.area.w, this.area.h - headerExtra());
    }

    private Rect contentArea() {
        final Rect b = body();
        return railed() ? b.afterLeft(CHIP_W + 16.0f) : b;
    }

    public Rect heroRect() {
        return contentArea();
    }

    private float chipHeight() {
        final int slots = Math.max(1, this.peers.size() - 1);
        final float avail = body().h;
        return Math.max(CHIP_H_MIN,
                Math.min(CHIP_H_MAX, (avail - CHIP_GAP * (slots - 1)) / slots));
    }

    private Rect chipRect(int slot) {
        final float h = chipHeight();
        final Rect b = body();
        return new Rect(b.x, b.y + slot * (h + CHIP_GAP), CHIP_W, h);
    }

    private List<Rect> rosterGrid(int n) {
        final Rect a = body();
        final int cols = n <= 1 ? 1 : n <= 2 ? 2 : n <= 4 ? 2 : 3;
        final int rows = (int) Math.ceil(n / (float) cols);
        final float gap = 12.0f;
        final float cw = (a.w - gap * (cols - 1)) / cols;

        final float cap = rows <= 1 ? 340.0f : 250.0f;
        final float ch = Math.min((a.h - gap * (rows - 1)) / rows, cap);
        final List<Rect> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int c = i % cols;
            final int r = i / cols;
            out.add(new Rect(a.x + c * (cw + gap), a.y + r * (ch + gap), cw, ch));
        }
        return out;
    }

    public Rect compareChartRect() {
        final Rect c = contentArea();
        return new Rect(c.x, c.y, c.w, Math.min(196.0f, c.h * 0.42f));
    }

    private List<Rect> compareColumns() {
        final Rect c = contentArea();
        final Rect chart = compareChartRect();
        final float top = chart.bottom() + 14.0f;
        final float gap = 12.0f;
        final int n = Math.max(1, this.compare.size());
        final float cw = (c.w - gap * (n - 1)) / n;
        final List<Rect> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new Rect(c.x + i * (cw + gap), top, cw, Math.min(244.0f, c.bottom() - top)));
        }
        return out;
    }

    public void setArea(Rect area) {
        this.area = area;
        layout(true);
    }

    private void layout(boolean jump) {
        if (this.peers.isEmpty() || this.area.isEmpty()) {
            return;
        }
        if (this.order.size() != this.peers.size()) {
            rank();
        }
        switch (this.mode) {
            case ROSTER -> {
                final List<Rect> grid = rosterGrid(this.peers.size());
                for (int slot = 0; slot < this.order.size(); slot++) {
                    place(this.peers.get(this.order.get(slot)), grid.get(slot), jump);
                }
            }
            case FOCUS -> {
                final Rect hero = heroRect();
                int slot = 0;
                for (final int pi : this.order) {
                    final Peers.Peer p = this.peers.get(pi);
                    place(p, p.key.equals(this.focusId) ? hero : chipRect(slot++), jump);
                }
            }
            case COMPARE -> {
                final List<Rect> cols = compareColumns();
                int slot = 0;
                for (final int pi : this.order) {
                    final Peers.Peer p = this.peers.get(pi);
                    final int at = this.compare.indexOf(p.key);

                    place(p, at >= 0 && at < cols.size() ? cols.get(at) : chipRect(slot++), jump);
                }
            }
            default -> {
            }
        }
    }

    private void place(Peers.Peer p, Rect r, boolean jump) {
        final RectSpring rs = this.rects.get(p.key);
        if (rs == null) {
            return;
        }
        if (jump) {
            rs.jump(r);
        } else {
            rs.to(r);
        }
    }

    public Rect rectOf(Peers.Peer p) {
        final RectSpring rs = this.rects.get(p.key);
        return rs == null ? Rect.EMPTY : rs.get();
    }

    public void update(float dt) {
        this.coach.step(dt);
        this.identify.step(dt);
        for (final Spring b : this.badges.values()) {
            b.step(dt);
        }
        if (!this.coachDismissed && this.peers.size() >= 1) {
            this.coach.set(coachShowing() ? 1.0f : 0.0f);
        }

        if (this.mode == Mode.ROSTER && !this.sortHold) {
            rank();
        }

        layout(false);
        for (final RectSpring rs : this.rects.values()) {
            rs.step(dt);
        }
    }

    public void focusOn(Peers.Peer p) {
        if (p == null) {
            return;
        }
        clearPointer();
        this.focusId = p.key;
        this.mode = Mode.FOCUS;
        final Spring badge = this.badges.get(p.key);
        if (badge != null) {
            badge.set(0.0f);
        }
        PeerFocusMemory.remember(this.panelPos, this.pageId, p.key);
        layout(false);
    }

    public void toRoster() {
        if (this.peers.size() < 2) {
            return;
        }
        clearPointer();
        this.mode = Mode.ROSTER;
        this.focusId = "";
        PeerFocusMemory.remember(this.panelPos, this.pageId, "");
        layout(false);
    }

    public void toCompare() {
        if (this.peers.size() < 2) {
            return;
        }
        clearPointer();
        if (this.compare.isEmpty()) {
            final Peers.Peer f = byId(this.focusId);
            if (f != null) {
                this.compare.add(f.key);
            }
            for (final int pi : this.order) {
                if (this.compare.size() >= 3) {
                    break;
                }
                final String id = this.peers.get(pi).key;
                if (!this.compare.contains(id)) {
                    this.compare.add(id);
                }
            }
        }
        this.mode = Mode.COMPARE;
        layout(false);
    }

    public void step(int direction) {
        if (this.mode != Mode.FOCUS || this.peers.size() < 2) {
            return;
        }
        int at = 0;
        for (int i = 0; i < this.order.size(); i++) {
            if (this.peers.get(this.order.get(i)).key.equals(this.focusId)) {
                at = i;
                break;
            }
        }
        final int n = this.order.size();
        focusOn(this.peers.get(this.order.get(((at + direction) % n + n) % n)));
    }

    public void dismissCoach() {
        this.coachDismissed = true;
        this.coach.set(0.0f);
    }

    private void clearPointer() {
        this.hoverId = "";
        this.sortHold = false;
        this.identify.set(0.0f);
    }

    public boolean mouseMoved(double mx, double my) {
        String hit = "";
        for (final Peers.Peer p : this.peers) {
            if (rectOf(p).contains(mx, my)) {
                hit = p.key;
            }
        }
        if (!hit.equals(this.hoverId)) {
            this.hoverId = hit;
            this.identify.set(hit.isEmpty() ? 0.0f : 1.0f);
        }
        this.sortHold = this.mode == Mode.ROSTER && body().contains(mx, my);
        return !hit.isEmpty();
    }

    public boolean mouseDown(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        for (final Peers.Peer p : this.peers) {
            if (!rectOf(p).contains(mx, my)) {
                continue;
            }
            if (this.mode == Mode.COMPARE) {
                final int at = this.compare.indexOf(p.key);
                if (at >= 0) {
                    if (this.compare.size() > 2) {
                        this.compare.remove(at);
                    }
                } else {
                    this.compare.add(p.key);
                    while (this.compare.size() > COMPARE_MAX) {
                        this.compare.remove(0);
                    }
                }
                layout(false);
            } else if (p.key.equals(this.focusId)) {
                toRoster();
            } else {
                focusOn(p);
            }
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        return switch (keyCode) {
            case 262 -> {
                step(1);
                yield this.mode == Mode.FOCUS;
            }
            case 263 -> {
                step(-1);
                yield this.mode == Mode.FOCUS;
            }
            case 67 -> {
                if (this.mode == Mode.COMPARE) {
                    toRoster();
                } else {
                    toCompare();
                }
                yield true;
            }
            default -> false;
        };
    }

    public boolean escape() {
        if (this.mode != Mode.ROSTER && this.peers.size() > 1) {
            toRoster();
            return true;
        }
        return false;
    }

    public void draw(PanelSurface c, Delegate delegate) {
        if (this.peers.isEmpty()) {
            return;
        }
        final Rect hero = heroRect();
        final float heroArea = Math.max(1.0f, hero.w * hero.h);

        final List<Peers.Peer> painted = new ArrayList<>(this.peers);
        painted.sort(Comparator.comparingInt(p -> p.key.equals(this.focusId) ? 1 : 0));

        for (final Peers.Peer p : painted) {
            final Rect r = rectOf(p);
            if (r.w < 4.0f || r.h < 4.0f) {
                continue;
            }
            final float big = ramp((r.w * r.h) / heroArea, 0.45f, 0.88f);
            final float mid = ramp(r.w, 80.0f, 170.0f);
            delegate.paintPeer(c, p, r, big, mid, p.key.equals(this.hoverId), 1.0f);
            drawBadge(c, p, r, delegate);
        }
    }

    private void drawBadge(PanelSurface c, Peers.Peer p, Rect r, Delegate delegate) {
        final Spring s = this.badges.get(p.key);
        final float t = s == null ? 0.0f : s.value();
        if (t < 0.02f) {
            return;
        }
        final float bx = r.right() - 6.0f + (1.0f - t) * 18.0f;
        final float by = r.y + 6.0f;
        c.circle(bx, by, 6.0f, Tokens.alpha(Tokens.CRITICAL, 0.9f * t));
        c.textCenterV("!", bx, by, Tokens.alpha(Tokens.CANVAS, t), Text.MICRO);
    }

    public void drawCoach(PanelSurface c, String headline, String hint) {
        final float t = this.coach.value();
        if (t < 0.02f || !coachShowing()) {
            return;
        }
        final Rect r = new Rect(this.area.x, this.area.y, this.area.w, COACH_H - 8.0f);
        c.card(r, Tokens.R_CHIP + 2.0f, Tokens.alpha(Tokens.ACCENT, 0.07f * t),
                Tokens.alpha(Tokens.ACCENT, 0.32f * t));
        c.rect(r.x + 9.0f, r.y + 5.0f, 2.0f, r.h - 10.0f, Tokens.alpha(Tokens.ACCENT, t));
        c.textV(headline, r.x + 19.0f, r.centerY(), Tokens.alpha(Tokens.INK, t), Text.META);
        final float w = c.textWidth(headline, Text.META);
        c.textV(hint, r.x + 19.0f + w + 8.0f, r.centerY(), Tokens.alpha(Tokens.INK_2, t), Text.META);
        c.textRightV("got it", r.right() - 12.0f, r.centerY(), Tokens.alpha(Tokens.ACCENT, t),
                Text.META);
    }

    public boolean coachClicked(double mx, double my) {
        if (this.coach.value() < 0.5f || !coachShowing()) {
            return false;
        }
        return new Rect(this.area.x, this.area.y, this.area.w, COACH_H - 8.0f).contains(mx, my);
    }

    public List<Peers.Peer> compared() {
        final List<Peers.Peer> out = new ArrayList<>();
        for (final String id : this.compare) {
            final Peers.Peer p = byId(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    public static float ramp(float v, float lo, float hi) {
        if (hi <= lo) {
            return v >= hi ? 1.0f : 0.0f;
        }
        final float t = (v - lo) / (hi - lo);
        return t < 0.0f ? 0.0f : Math.min(t, 1.0f);
    }
}
