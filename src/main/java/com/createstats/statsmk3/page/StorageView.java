package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createstats.blockentity.SourceConfig;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.page.StorageReadings.Kind;
import com.createstats.statsmk3.page.StorageReadings.Vessel;
import com.createstats.statsmk3.ui.Button;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StorageView extends StudioView implements PeerRoster.Delegate {
    private final PeerRoster roster;

    private final Map<String, Spring> gauges = new HashMap<>();

    private final Map<String, Spring> printed = new HashMap<>();

    private final Map<String, Spring> levels = new HashMap<>();

    private Segmented modeControl;
    private Button faceButton;

    private Kind filter;

    private final Map<String, Vessel> frame = new HashMap<>();

    private List<Peers.Peer> everything = new ArrayList<>();
    private List<StorageArt.Category> categories = List.of();
    private int hidden;

    public StorageView(StudioContext ctx) {
        super(ctx);
        this.roster = new PeerRoster(id(), ctx.panel == null ? null : ctx.panel.getBlockPos());
    }

    @Override
    public String id() {
        return "storage";
    }

    @Override
    public String label() {
        return "Storage";
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        StorageArt.kindIcon(c, cx, cy, 6.0f, Kind.ITEMS, color);
    }

    @Override
    protected void build() {
        this.roster.setArea(new Rect(this.area.x, this.area.y + StorageArt.HEADER_H,
                this.area.w, this.area.h - StorageArt.HEADER_H));

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

        this.faceButton = new Button(faceLabel(), this::toggleFace);
        this.faceButton.textScale(Text.MICRO);
        this.faceButton.setBounds(new Rect(this.area.right() - w - 120.0f, this.area.y + 2.0f,
                112.0f, 22.0f));
        this.widgets.add(this.faceButton);
    }

    @Override
    public void update(float dt) {
        super.update(dt);

        this.frame.clear();
        this.everything = readPeers();
        final List<Peers.Peer> visible = applyFilter(this.everything);
        this.hidden = visible.size() - Math.min(visible.size(), StorageReadings.ROSTER_CAP);
        this.roster.setPeers(cap(visible), this);

        final List<Vessel> vessels = new ArrayList<>(this.everything.size());
        for (final Peers.Peer p : this.everything) {
            vessels.add(vesselOf(p));
        }
        this.categories = StorageArt.categorise(vessels);

        for (final Peers.Peer p : this.roster.peers()) {
            final Vessel v = vesselOf(p);
            gauge(p).set(Math.max(0.0f, Math.min(100.0f, v.pct()))).step(dt);
            printed(p).set(Math.max(0.0f, v.pct())).step(dt);
            level(p).set(v.level()).step(dt);
        }
        if (this.modeControl != null) {
            this.modeControl.setVisible(this.roster.size() > 1);
        }
        if (this.faceButton != null) {
            this.faceButton.setLabel(faceLabel());
        }
        this.roster.update(dt);
    }

    private List<Peers.Peer> readPeers() {
        final List<Peers.Peer> out = new ArrayList<>();
        final BlockPos here = this.ctx.panel == null ? null : this.ctx.panel.getBlockPos();
        int i = 0;
        for (final SourceProbe.Unit u : SourceProbe.all(this.ctx.panel)) {
            final SourceConfig config = u.config();
            final String bearing = Peers.bearing(here,
                    config == null ? null : config.getSourcePos());
            for (final String variant : StorageReadings.variantsOf(u)) {
                out.add(new Peers.Peer(u, i++, bearing, variant));
            }
        }
        return out;
    }

    private List<Peers.Peer> applyFilter(List<Peers.Peer> all) {
        if (this.filter == null) {
            return all;
        }
        final List<Peers.Peer> out = new ArrayList<>();
        for (final Peers.Peer p : all) {
            if (vesselOf(p).kind() == this.filter) {
                out.add(p);
            }
        }
        return out;
    }

    private List<Peers.Peer> cap(List<Peers.Peer> peers) {
        if (peers.size() <= StorageReadings.ROSTER_CAP) {
            return peers;
        }
        final List<Peers.Peer> sorted = new ArrayList<>(peers);
        sorted.sort(Comparator.comparingDouble((Peers.Peer p) -> -urgency(p))
                .thenComparingInt(p -> p.index));
        final List<Peers.Peer> out = new ArrayList<>(sorted.subList(0, StorageReadings.ROSTER_CAP));

        out.sort(Comparator.comparingInt(p -> p.index));
        return out;
    }

    private Vessel vesselOf(Peers.Peer p) {
        return this.frame.computeIfAbsent(p.key, k -> StorageReadings.read(p.unit, p.variant));
    }

    private Spring gauge(Peers.Peer p) {
        return this.gauges.computeIfAbsent(p.key, k -> Spring.needle(0.0f));
    }

    private Spring printed(Peers.Peer p) {
        return this.printed.computeIfAbsent(p.key, k -> Spring.ui(0.0f));
    }

    private Spring level(Peers.Peer p) {
        return this.levels.computeIfAbsent(p.key, k -> Spring.ui(0.0f));
    }

    private StorageArt.PeerArt art(Peers.Peer p) {
        final Vessel v = vesselOf(p);
        return new StorageArt.PeerArt(p.numeral, nameOf(p), p.bearing, p.series,
                this.roster.isCompared(p), gauge(p).value(), printed(p).value(),
                level(p).value(), v);
    }

    private String nameOf(Peers.Peer p) {
        return StorageReadings.nameOf(p.unit.config(),
                StorageReadings.ambiguousType(this.ctx.panel, p.unit.config()));
    }

    private String faceLabel() {
        return showingOnFace() ? "On face ✓" : "Show on face";
    }

    private boolean showingOnFace() {
        return this.ctx.settings().faceMode == Mk3Settings.FaceMode.STORAGE;
    }

    private void toggleFace() {
        final Mk3Settings settings = this.ctx.settings();
        final boolean showing = showingOnFace();
        settings.faceMode = showing
                ? Mk3Settings.FaceMode.INSTRUMENTS : Mk3Settings.FaceMode.STORAGE;

        settings.storageKind = showing || this.filter == null ? "" : this.filter.variant;
        this.ctx.commit();
    }

    @Override
    public float urgency(Peers.Peer peer) {
        return vesselOf(peer).urgency();
    }

    @Override
    public int statusColor(Peers.Peer peer) {
        return vesselOf(peer).statusColor();
    }

    @Override
    public boolean alarming(Peers.Peer peer) {
        return vesselOf(peer).alarming();
    }

    @Override
    public float[] compareSeries(Peers.Peer peer) {
        return vesselOf(peer).percentSeries();
    }

    @Override
    public void paintPeer(PanelSurface c, Peers.Peer peer, Rect r, float big, float mid,
                          boolean hovered, float alpha) {
        StorageArt.paintPeer(c, art(peer), r, big, mid, hovered, this.ctx.time);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final List<Peers.Peer> peers = this.roster.peers();
        final List<StorageArt.PeerArt> arts = new ArrayList<>(peers.size());
        for (final Peers.Peer p : peers) {
            arts.add(art(p));
        }
        final float rightInset = (this.modeControl != null && this.modeControl.visible()
                ? 224.0f : 0.0f) + 122.0f;
        StorageArt.header(c, this.area, this.categories, arts, this.filter, rightInset, this.hidden);

        if (peers.isEmpty()) {
            if (this.filter != null && !this.everything.isEmpty()) {
                StorageArt.emptyFilter(c, this.area, this.filter);
            } else {
                StorageArt.empty(c, this.area);
            }
            return;
        }
        this.roster.drawCoach(c,
                peers.size() > 1
                        ? Peers.count(peers.size(), "store") + " are linked to this panel."
                        : "One store is linked.",
                peers.size() > 1
                        ? "Ranked by how close each is to an end. Click one to open it."
                        : "Link another and this page becomes a roster you can compare.");

        if (this.roster.mode() == PeerRoster.Mode.COMPARE) {
            final List<StorageArt.PeerArt> compared = new ArrayList<>();
            for (final Peers.Peer p : this.roster.compared()) {
                compared.add(art(p));
            }
            StorageArt.compareChart(c, this.roster.compareChartRect(), compared);
        }
        this.roster.draw(c, this);

        final Peers.Peer hovered = this.roster.hovered();
        if (hovered != null) {
            StorageArt.identify(c, this.area, this.roster.rectOf(hovered), art(hovered),
                    this.roster.identifyAmount());

            com.createstats.client.PeerIdentifyHighlight.show(hovered.pos(),
                    statusColor(hovered) & 0xFFFFFF);
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
        if (categoryClicked(mx, my)) {
            return true;
        }
        return this.roster.mouseDown(mx, my, button);
    }

    private boolean categoryClicked(double mx, double my) {
        for (int i = 0; i < this.categories.size(); i++) {
            if (!StorageArt.categoryChip(this.area, i).contains(mx, my)) {
                continue;
            }
            final Kind kind = this.categories.get(i).kind();
            this.filter = this.filter == kind ? null : kind;

            this.roster.toRoster();
            if (showingOnFace()) {
                this.ctx.settings().storageKind = this.filter == null ? "" : this.filter.variant;
                this.ctx.commit();
            }
            return true;
        }
        return false;
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
            if (this.filter != null) {
                this.filter = null;
                return true;
            }
            return this.roster.escape();
        }
        return this.roster.keyPressed(keyCode);
    }

    public PeerRoster roster() {
        return this.roster;
    }

    public Kind filter() {
        return this.filter;
    }
}
