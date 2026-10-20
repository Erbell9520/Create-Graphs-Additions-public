package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StressometerView extends StudioView implements PeerRoster.Delegate {
    public static final String FAMILY = "kinetics.stressometer";

    private static final String[] SHARED_NETWORK_METRICS = {"capacity_su", "stress_su"};

    private final PeerRoster roster;
    private final Map<String, Spring> needles = new HashMap<>();
    private final Map<String, Spring> printed = new HashMap<>();

    private final Map<String, float[]> peaks = new HashMap<>();

    private Segmented modeControl;
    private String twinNotice = "";

    public StressometerView(StudioContext ctx) {
        super(ctx);
        this.roster = new PeerRoster(id(), ctx.panel == null ? null : ctx.panel.getBlockPos());
    }

    @Override
    public String id() {
        return "stress";
    }

    @Override
    public String label() {
        return "Stress";
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.arc(c, cx, cy + 2.0f, 5.5f, 1.4f, StressArt.DIAL_START, StressArt.DIAL_SWEEP, color);
        Shapes.line(c, cx, cy + 2.0f, cx + 2.6f, cy - 3.2f, 1.4f, color);
    }

    @Override
    protected void build() {
        this.roster.setArea(new Rect(this.area.x, this.area.y + StressArt.HEADER_H,
                this.area.w, this.area.h - StressArt.HEADER_H));

        this.modeControl = new Segmented(new String[]{"Roster", "Focus", "Compare"},
                () -> switch (this.roster.mode()) {
                    case ROSTER -> 0;
                    case FOCUS -> 1;
                    case COMPARE -> 2;
                },
                i -> {
                    switch (i) {
                        case 0 -> this.roster.toRoster();
                        case 1 -> this.roster.focusOn(this.roster.focused());
                        default -> this.roster.toCompare();
                    }
                });
        this.modeControl.textScale(Text.META);
        final float w = 216.0f;
        this.modeControl.setBounds(new Rect(this.area.right() - w, this.area.y, w, 26.0f));

        this.modeControl.setVisible(this.roster.size() > 1);
        this.widgets.add(this.modeControl);
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.roster.setPeers(Peers.of(this.ctx.panel, FAMILY), this);
        if (this.modeControl != null) {
            this.modeControl.setVisible(this.roster.size() > 1);
        }
        for (final Peers.Peer p : this.roster.peers()) {
            final StressArt.Reading r = StressArt.readingOf(p.unit);
            final float pct = Math.max(0.0f, r.pct());
            needle(p).set(Math.min(pct, 115.0f)).step(dt);
            printed(p).set(pct).step(dt);
            final float[] peak = this.peaks.computeIfAbsent(p.sourceId, k -> new float[]{0.0f, 0.0f});
            if (pct > peak[0]) {
                peak[0] = pct;
                peak[1] = r.used();
            }
        }
        this.twinNotice = twinNotice(this.roster.peers());
        this.roster.update(dt);
    }

    private static String twinNotice(List<Peers.Peer> peers) {
        final List<int[]> groups = Peers.twins(peers, SHARED_NETWORK_METRICS);
        if (groups.isEmpty()) {
            return "";
        }
        return Peers.describe(peers, groups.get(0)) + " report the same network";
    }

    private Spring needle(Peers.Peer p) {
        return this.needles.computeIfAbsent(p.sourceId, k -> Spring.needle(0.0f));
    }

    private Spring printed(Peers.Peer p) {
        return this.printed.computeIfAbsent(p.sourceId, k -> Spring.ui(0.0f));
    }

    private float[] peak(Peers.Peer p) {
        return this.peaks.getOrDefault(p.sourceId, new float[]{0.0f, 0.0f});
    }

    private StressArt.PeerArt art(Peers.Peer p) {
        return new StressArt.PeerArt(p.numeral, p.label(this.roster.size() > 1), p.bearing, p.series,
                this.roster.isCompared(p), needle(p).value(), printed(p).value(),
                peak(p)[0], peak(p)[1], StressArt.readingOf(p.unit));
    }

    @Override
    public float urgency(Peers.Peer peer) {
        return StressArt.readingOf(peer.unit).pct();
    }

    @Override
    public int statusColor(Peers.Peer peer) {
        return StressArt.statusColor(StressArt.readingOf(peer.unit));
    }

    @Override
    public boolean alarming(Peers.Peer peer) {
        final StressArt.Reading r = StressArt.readingOf(peer.unit);
        return r.live() && (r.overstressed() || r.pct() >= StressArt.CRITICAL_AT);
    }

    @Override
    public float[] compareSeries(Peers.Peer peer) {
        return StressArt.percentSeries(StressArt.readingOf(peer.unit));
    }

    @Override
    public void paintPeer(PanelSurface c, Peers.Peer peer, Rect r, float big, float mid,
                          boolean hovered, float alpha) {
        StressArt.paintPeer(c, art(peer), r, big, mid, hovered, this.ctx.time);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final List<Peers.Peer> peers = this.roster.peers();
        final List<StressArt.PeerArt> arts = new ArrayList<>();
        for (final Peers.Peer p : peers) {
            arts.add(art(p));
        }
        StressArt.header(c, this.area, arts, this.twinNotice,
                this.modeControl != null && this.modeControl.visible() ? 224.0f : 0.0f);

        if (peers.isEmpty()) {
            StressArt.empty(c, this.area);
            return;
        }
        this.roster.drawCoach(c,
                peers.size() > 1
                        ? Peers.count(peers.size(), "stress network") + " are linked to this panel."
                        : "One stressometer is linked.",
                peers.size() > 1
                        ? "Ranked by load. Click any to open it, or Compare to overlay them."
                        : "Link another and this page becomes a roster you can compare.");

        if (this.roster.mode() == PeerRoster.Mode.COMPARE) {
            final List<StressArt.PeerArt> compared = new ArrayList<>();
            for (final Peers.Peer p : this.roster.compared()) {
                compared.add(art(p));
            }
            StressArt.compareChart(c, this.roster.compareChartRect(), compared);
        }
        this.roster.draw(c, this);

        final Peers.Peer hovered = this.roster.hovered();
        if (hovered != null) {
            StressArt.identify(c, this.area, this.roster.rectOf(hovered), art(hovered),
                    this.roster.identifyAmount());
            // self expiring outline, a closed studio can't leave a box floating

            com.createstats.client.PeerIdentifyHighlight.show(hovered.pos(),
                    StressArt.statusColor(StressArt.readingOf(hovered.unit)) & 0xFFFFFF);
        } else {
            com.createstats.client.PeerIdentifyHighlight.clear();
        }
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        if (this.roster.coachClicked(mx, my)) {
            this.roster.dismissCoach();
            return true;
        }
        return this.roster.mouseDown(mx, my, button);
    }

    @Override
    public boolean mouseMoved(double mx, double my) {
        final boolean widget = super.mouseMoved(mx, my);
        return this.roster.mouseMoved(mx, my) || widget;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 256) {
            return this.roster.escape();
        }
        return this.roster.keyPressed(keyCode);
    }

    public PeerRoster roster() {
        return this.roster;
    }
}
