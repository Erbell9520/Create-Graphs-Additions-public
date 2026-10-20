package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createstats.netscan.NetscanHost;
import com.createstats.netscan.NetscanLink;
import com.createstats.netscan.client.NetscanClient;
import com.createstats.netscan.client.NetscanScreen;
import com.createstats.netscan.client.ScanContext;
import com.createstats.netscan.client.view.MapView;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanLayout;
import com.createstats.netscan.scan.ScanLayoutMode;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.ui.Button;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TopologyView extends StudioView implements PeerRoster.Delegate {
    public static final String FAMILY = "kinetics.topology_scanner";

    private final PeerRoster roster;
    private final Map<String, Spring> printed = new HashMap<>();
    private final Map<String, TopoArt.Reading> readings = new HashMap<>();
    private final Map<String, Cached> layouts = new HashMap<>();

    private Segmented rosterControl;
    private Segmented layoutControl;
    private ScanLayoutMode mode = ScanLayoutMode.DRIVE;
    private String twinNotice = "";

    private final Map<String, MapView> focusMaps = new HashMap<>();
    private final Map<String, ScanContext> focusCtxs = new HashMap<>();
    private MapView activeMap;
    private String activeMapKey = "";
    private Rect activeMapArea = Rect.EMPTY;
    private Rect activeMapLaid = Rect.EMPTY;
    private double lastMx;
    private double lastMy;

    private Button faceButton;

    private Button openButton;

    private static final class Cached {
        private ScanLayout.Result result;
        private int hash = Integer.MIN_VALUE;
        private ScanLayoutMode mode;

        ScanLayout.Result of(ScanGraph graph, ScanLayoutMode want) {
            if (this.result == null || this.hash != graph.contentHash() || this.mode != want) {
                this.result = ScanLayout.layout(graph, want);
                this.hash = graph.contentHash();
                this.mode = want;
            }
            return this.result;
        }
    }

    public TopologyView(StudioContext ctx) {
        super(ctx);
        this.roster = new PeerRoster(id(), ctx.panel == null ? null : ctx.panel.getBlockPos());
    }

    @Override
    public String id() {
        return "topology";
    }

    @Override
    public String label() {
        return "Topology";
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.circle(c, cx - 5.0f, cy, 2.0f, color);
        Shapes.circle(c, cx + 4.0f, cy - 4.0f, 2.0f, color);
        Shapes.circle(c, cx + 4.0f, cy + 4.0f, 2.0f, color);
        Shapes.line(c, cx - 4.0f, cy, cx + 3.0f, cy - 4.0f, 1.1f, color);
        Shapes.line(c, cx - 4.0f, cy, cx + 3.0f, cy + 4.0f, 1.1f, color);
    }

    @Override
    protected void build() {
        this.roster.setArea(new Rect(this.area.x, this.area.y + TopoArt.HEADER_H,
                this.area.w, this.area.h - TopoArt.HEADER_H));

        this.rosterControl = new Segmented(new String[]{"Roster", "Focus", "Compare"},
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
        this.rosterControl.textScale(Text.META);
        final float rw = 200.0f;
        this.rosterControl.setBounds(new Rect(this.area.right() - rw, this.area.y, rw, 26.0f));
        this.rosterControl.setVisible(this.roster.size() > 1);
        this.widgets.add(this.rosterControl);

        final ScanLayoutMode[] modes = ScanLayoutMode.values();
        final String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            labels[i] = modes[i].label();
        }
        this.faceButton = new Button(faceLabel(), this::toggleFace);
        this.faceButton.textScale(Text.MICRO);
        this.faceButton.setBounds(new Rect(this.rosterControl.bounds().x - 8.0f - 112.0f,
                this.area.y + 2.0f, 112.0f, 22.0f));
        this.widgets.add(this.faceButton);

        this.openButton = new Button("Open survey  ↗", this::openFocusedSurvey);
        this.openButton.textScale(Text.MICRO);
        this.openButton.setBounds(new Rect(this.faceButton.bounds().x - 8.0f - 104.0f,
                this.area.y + 2.0f, 104.0f, 22.0f));
        this.openButton.setVisible(false);
        this.widgets.add(this.openButton);

        this.layoutControl = new Segmented(labels, () -> this.mode.ordinal(),
                i -> this.mode = ScanLayoutMode.byOrdinal(i));
        this.layoutControl.textScale(Text.MICRO);
        final float lw = 46.0f * modes.length;
        this.layoutControl.setBounds(
                new Rect(this.openButton.bounds().x - lw - 8.0f, this.area.y + 2.0f, lw, 22.0f));
        this.widgets.add(this.layoutControl);
    }

    private String faceLabel() {
        return this.ctx.settings().faceMode == Mk3Settings.FaceMode.TOPOLOGY
                ? "On face ✓" : "Show on face";
    }

    private void toggleFace() {
        final Mk3Settings settings = this.ctx.settings();
        settings.faceMode = settings.faceMode == Mk3Settings.FaceMode.TOPOLOGY
                ? Mk3Settings.FaceMode.INSTRUMENTS : Mk3Settings.FaceMode.TOPOLOGY;
        this.ctx.commit();
    }

    private float controlInset() {
        float inset = 0.0f;
        for (final com.createstats.statsmk3.ui.Widget w : new com.createstats.statsmk3.ui.Widget[]{
                this.rosterControl, this.faceButton, this.openButton, this.layoutControl}) {
            if (w != null && w.visible()) {
                inset += w.bounds().w + 8.0f;
            }
        }
        return inset;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        final List<Peers.Peer> peers = Peers.matching(this.ctx.panel,
                config -> NetscanLink.isScanner(level(), config));
        this.roster.setPeers(peers, this);
        if (this.rosterControl != null) {
            this.rosterControl.setVisible(this.roster.size() > 1);
        }

        this.readings.clear();
        for (final Peers.Peer p : this.roster.peers()) {
            final TopoArt.Reading r = TopoArt.readingOf(hostOf(p));
            this.readings.put(p.key, r);
            printed(p).set(r.live() ? r.pct() : 0.0f).step(dt);
        }
        this.twinNotice = twinNotice();
        if (this.faceButton != null) {
            this.faceButton.setLabel(faceLabel());
            this.faceButton.setVisible(!this.roster.peers().isEmpty());
        }
        subscribe();
        this.roster.update(dt);
        resolveFocusMap(dt);
    }

    private void resolveFocusMap(float dt) {
        MapView next = null;
        Rect nextArea = Rect.EMPTY;

        final Peers.Peer focused = this.roster.focused();
        final NetscanHost host = hostOf(focused);

        final boolean canOpen = this.roster.mode() == PeerRoster.Mode.FOCUS && host != null;
        if (this.openButton != null) {
            this.openButton.setVisible(canOpen);
        }
        if (canOpen) {
            final Rect card = this.roster.rectOf(focused);
            final Rect hero = this.roster.heroRect();
            final TopoArt.Reading r = reading(focused);

            final Rect slot = TopoArt.diagramRect(card);
            if (r.live() && sameRect(card, hero) && !slot.isEmpty()) {
                nextArea = slot;
                final ScanContext ctx = this.focusCtxs.computeIfAbsent(focused.key,
                        k -> new ScanContext(this.ctx.font, focused.pos()));
                ctx.tier = r.tier();
                ctx.form = r.form();
                ctx.graph = r.graph();
                ctx.powered = host.scanPowered();
                ctx.seedMissing = host.scanSeed() == null;
                ctx.sweeping = host.scanEngine().sweeping();
                ctx.sweepCount = host.scanEngine().sweepCount();
                ctx.history = host.scanEngine().history();

                next = this.focusMaps.computeIfAbsent(focused.key, k -> new MapView(ctx));
                final boolean fresh = !focused.key.equals(this.activeMapKey);
                if (fresh || !sameRect(this.activeMapLaid, nextArea)) {
                    next.layout(nextArea);
                    next.frame();
                    this.activeMapLaid = nextArea;
                    if (fresh) {
                        next.onEnter();
                    }
                }
                next.update(dt);
                next.hover(this.lastMx, this.lastMy);
                this.mode = ctx.mode;
            }
        }

        this.activeMap = next;
        this.activeMapArea = nextArea;
        this.activeMapKey = next == null ? "" : focused.key;
        if (this.layoutControl != null) {
            this.layoutControl.setVisible(next == null && !this.roster.peers().isEmpty());
        }
        this.focusMaps.keySet().removeIf(this::stale);
        this.focusCtxs.keySet().removeIf(this::stale);
    }

    private boolean stale(String key) {
        for (final Peers.Peer p : this.roster.peers()) {
            if (p.key.equals(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameRect(Rect a, Rect b) {
        return Math.abs(a.x - b.x) < 1.5f && Math.abs(a.y - b.y) < 1.5f
                && Math.abs(a.w - b.w) < 1.5f && Math.abs(a.h - b.h) < 1.5f;
    }

    private void subscribe() {
        final List<BlockPos> positions = new ArrayList<>();
        final Peers.Peer focused = this.roster.focused();
        if (focused != null && hostOf(focused) != null) {
            positions.add(focused.pos());
        }
        for (final Peers.Peer p : this.roster.peers()) {
            if (hostOf(p) != null && !positions.contains(p.pos())) {
                positions.add(p.pos());
            }
        }
        NetscanClient.watch(positions);
        NetscanClient.tickWatch();
    }

    private net.minecraft.world.level.Level level() {
        return this.ctx.panel == null ? null : this.ctx.panel.getLevel();
    }

    private NetscanHost hostOf(Peers.Peer peer) {
        final BlockPos pos = peer == null ? null : peer.pos();
        if (pos == null || this.ctx.panel == null || this.ctx.panel.getLevel() == null
                || !this.ctx.panel.getLevel().isLoaded(pos)) {
            return null;
        }
        return this.ctx.panel.getLevel().getBlockEntity(pos) instanceof NetscanHost host
                ? host : null;
    }

    private TopoArt.Reading reading(Peers.Peer p) {
        return this.readings.getOrDefault(p.key, TopoArt.Reading.absent());
    }

    private Spring printed(Peers.Peer p) {
        return this.printed.computeIfAbsent(p.key, k -> Spring.ui(0.0f));
    }

    private ScanLayout.Result layoutOf(Peers.Peer p) {
        return this.layouts.computeIfAbsent(p.key, k -> new Cached())
                .of(reading(p).graph(), this.mode);
    }

    private TopoArt.PeerArt art(Peers.Peer p) {
        return new TopoArt.PeerArt(p.numeral, p.label(this.roster.size() > 1), p.bearing, p.series,
                this.roster.isCompared(p), printed(p).value(), reading(p), layoutOf(p));
    }

    private String twinNotice() {
        final List<TopoArt.PeerArt> arts = new ArrayList<>();
        for (final Peers.Peer p : this.roster.peers()) {
            arts.add(art(p));
        }
        final List<int[]> groups = TopoArt.twins(arts);
        if (groups.isEmpty()) {
            return "";
        }
        return TopoArt.describe(arts, groups.get(0)) + " survey the same network";
    }

    @Override
    public float urgency(Peers.Peer peer) {
        final TopoArt.Reading r = reading(peer);

        return r.live() ? r.pct() : 85.0f;
    }

    @Override
    public int statusColor(Peers.Peer peer) {
        return TopoArt.statusColor(reading(peer));
    }

    @Override
    public boolean alarming(Peers.Peer peer) {
        final TopoArt.Reading r = reading(peer);
        return r.live() ? r.over() || r.pct() >= TopoArt.CRITICAL_AT : r.present();
    }

    @Override
    public float[] compareSeries(Peers.Peer peer) {
        return TopoArt.percentSeries(reading(peer));
    }

    @Override
    public void paintPeer(PanelSurface c, Peers.Peer peer, Rect r, float big, float mid,
                          boolean hovered, float alpha) {
        TopoArt.paintPeer(c, art(peer), r, big, mid, hovered, this.mode, this.ctx.time);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final List<Peers.Peer> peers = this.roster.peers();
        final List<TopoArt.PeerArt> arts = new ArrayList<>();
        for (final Peers.Peer p : peers) {
            arts.add(art(p));
        }
        TopoArt.header(c, this.area, arts, this.twinNotice, controlInset());

        if (peers.isEmpty()) {
            TopoArt.empty(c, this.area);
            return;
        }
        this.roster.drawCoach(c,
                peers.size() > 1
                        ? Peers.count(peers.size(), "Topology Scanner") + " are linked to this panel."
                        : "One Topology Scanner is linked.",
                peers.size() > 1
                        ? "Ranked by load. Click any to open it, or Compare to overlay them."
                        : "Link another and this page becomes a roster you can compare.");

        if (this.roster.mode() == PeerRoster.Mode.COMPARE) {
            final List<TopoArt.PeerArt> compared = new ArrayList<>();
            for (final Peers.Peer p : this.roster.compared()) {
                compared.add(art(p));
            }
            TopoArt.compareChart(c, this.roster.compareChartRect(), compared);
        }
        this.roster.draw(c, this);

        if (this.activeMap != null) {
            this.activeMap.draw(c);
        }

        final Peers.Peer hovered = this.roster.hovered();
        if (hovered != null) {
            if (this.activeMap == null || !hovered.key.equals(this.activeMapKey)) {
                TopoArt.identify(c, this.area, this.roster.rectOf(hovered), art(hovered),
                        this.roster.identifyAmount());
            }

            com.createstats.client.PeerIdentifyHighlight.show(hovered.pos(),
                    TopoArt.statusColor(reading(hovered)) & 0xFFFFFF);
        } else {
            com.createstats.client.PeerIdentifyHighlight.clear();
        }
    }

    private void openFocusedSurvey() {
        final NetscanHost host = hostOf(this.roster.focused());
        if (host != null) {
            Minecraft.getInstance().setScreen(new NetscanScreen(host));
        }
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        if (this.activeMap != null && this.activeMapArea.contains(mx, my)
                && this.activeMap.mouseClicked(mx, my, button)) {
            return true;
        }
        if (this.roster.coachClicked(mx, my)) {
            this.roster.dismissCoach();
            return true;
        }
        return this.roster.mouseDown(mx, my, button);
    }

    @Override
    public boolean mouseUp(double mx, double my, int button) {
        if (this.activeMap != null) {
            this.activeMap.mouseReleased(mx, my, button);
        }
        return super.mouseUp(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.activeMap != null && this.activeMap.mouseDragged(mx, my, button, dx, dy)) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        return this.activeMap != null && this.activeMapArea.contains(mx, my)
                && this.activeMap.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean mouseMoved(double mx, double my) {
        this.lastMx = mx;
        this.lastMy = my;
        final boolean widget = super.mouseMoved(mx, my);
        if (this.activeMap != null) {
            this.activeMap.hover(mx, my);
        }
        return this.roster.mouseMoved(mx, my) || widget;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (this.activeMap != null && this.activeMap.keyPressed(keyCode, scanCode, modifiers)) {
            final ScanContext ctx = this.focusCtxs.get(this.activeMapKey);
            if (ctx != null) {
                this.mode = ctx.mode;
            }
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_M) {
            this.mode = ScanLayoutMode.byOrdinal(
                    (this.mode.ordinal() + 1) % ScanLayoutMode.values().length);
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

    public ScanLayoutMode mode() {
        return this.mode;
    }
}
