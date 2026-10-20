package com.createstats.statsmk3.page;

import com.createstats.aerial.AerialCommand;
import com.createstats.aerial.AerialFrame;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.network.AerialCommandPacket;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.ui.Button;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class AeroView extends StudioView {
    public static final int BRIDGE = 0;
    public static final int LIFT = 1;
    public static final int ENGINES = 2;
    public static final int NAV = 3;
    public static final int LOG = 4;
    public static final int TRAFFIC = 5;

    private static final float BUTTON_W = 96.0f;

    private int tab;
    private Rect[] tabRects = new Rect[0];
    private Button faceButton;
    private Button saveRoute;
    private Button clearTrail;
    private Button resetEnvelope;

    private float sentFor;
    private AerialCommand sent;

    public AeroView(StudioContext ctx) {
        super(ctx);

        this.tab = tabFor(ctx.settings().faceMode);
    }

    @Override
    public String id() {
        return "aero";
    }

    @Override
    public String label() {
        return "Flight";
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        AeroInstruments.chevron(c, cx, cy, 6.0f, 0.0f, color);
    }

    @Override
    protected void build() {
        final float y = this.area.y + AeroBridge.STRIP_H + 4.0f - 2.0f;
        this.faceButton = new Button(faceLabel(), this::toggleFace);
        this.faceButton.textScale(Text.META);
        this.faceButton.setBounds(new Rect(this.area.right() - 104.0f, y, 104.0f, AeroBridge.TABS_H + 2.0f));
        this.widgets.add(this.faceButton);
        final float bx = this.area.right() - 104.0f - 6.0f - BUTTON_W;
        this.saveRoute = command("Save as route", AerialCommand.SAVE_TRAIL_AS_ROUTE,
                new Rect(bx, y, BUTTON_W, AeroBridge.TABS_H + 2.0f));
        this.clearTrail = command("Clear trail", AerialCommand.CLEAR_TRAIL,
                new Rect(bx - 6.0f - BUTTON_W, y, BUTTON_W, AeroBridge.TABS_H + 2.0f));
        this.resetEnvelope = command("Reset learned", AerialCommand.RESET_ENVELOPE,
                new Rect(bx, y, BUTTON_W, AeroBridge.TABS_H + 2.0f));
        refreshButtons();
    }

    private Button command(String label, AerialCommand command, Rect bounds) {
        final Button b = new Button(label, () -> send(command));
        b.textScale(Text.META);
        b.setBounds(bounds);
        this.widgets.add(b);
        return b;
    }

    private void send(AerialCommand command) {
        if (this.ctx.panel == null) {
            return;
        }
        PacketDistributor.sendToServer(new AerialCommandPacket(this.ctx.panel.getBlockPos(), command.ordinal()));
        this.sent = command;
        this.sentFor = 2.0f;
    }

    private AerialFrame frame() {
        if (this.ctx.panel == null) {
            return null;
        }
        final StatsDisplayPanelBE p = this.ctx.panel.getControllerBE() != null ? this.ctx.panel.getControllerBE()
                : this.ctx.panel;
        return p.aerialLinks().feed().frame(System.nanoTime());
    }

    private boolean linked() {
        if (this.ctx.panel == null) {
            return false;
        }
        final StatsDisplayPanelBE p = this.ctx.panel.getControllerBE() != null ? this.ctx.panel.getControllerBE()
                : this.ctx.panel;
        return p.aerialLinks().hasLinks();
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.sentFor = Math.max(0.0f, this.sentFor - dt);
        if (this.faceButton != null) {
            this.faceButton.setLabel(faceLabel());
        }
        refreshButtons();
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        for (int i = 0; i < this.tabRects.length; i++) {
            if (this.tabRects[i].contains(mx, my)) {
                this.tab = i;
                refreshButtons();
                return true;
            }
        }
        return false;
    }

    private void refreshButtons() {
        if (this.saveRoute == null) {
            return;
        }
        final AerialFrame f = frame();
        final boolean nav = this.tab == NAV;
        final boolean log = this.tab == LOG;
        visible(this.saveRoute, nav && f != null && f.snapshot().accepts(AerialCommand.SAVE_TRAIL_AS_ROUTE),
                AerialCommand.SAVE_TRAIL_AS_ROUTE, "Save as route");
        visible(this.clearTrail, nav && f != null && f.snapshot().accepts(AerialCommand.CLEAR_TRAIL),
                AerialCommand.CLEAR_TRAIL, "Clear trail");
        visible(this.resetEnvelope, log && f != null && f.snapshot().accepts(AerialCommand.RESET_ENVELOPE),
                AerialCommand.RESET_ENVELOPE, "Reset learned");
    }

    private void visible(Button b, boolean on, AerialCommand command, String label) {
        b.setVisible(on);
        b.setLabel(this.sentFor > 0.0f && this.sent == command ? "Sent" : label);
    }

    static Mk3Settings.FaceMode faceFor(int tab) {
        return switch (tab) {
            case LIFT -> Mk3Settings.FaceMode.AERO_BUDGET;
            case ENGINES -> Mk3Settings.FaceMode.AERO_TELEGRAPH;
            case NAV -> Mk3Settings.FaceMode.AERO_MAP;
            case LOG -> Mk3Settings.FaceMode.AERO_VOYAGE;
            case TRAFFIC -> Mk3Settings.FaceMode.AERO_SCOPE;
            default -> Mk3Settings.FaceMode.AERO_DECK;
        };
    }

    static int tabFor(Mk3Settings.FaceMode face) {
        for (int t = 0; t < AeroBridge.TABS.length; t++) {
            if (face != null && face != Mk3Settings.FaceMode.AERO_DECK && faceFor(t) == face) {
                return t;
            }
        }
        return BRIDGE;
    }

    private boolean showingOnFace() {
        return this.ctx.settings().faceMode == faceFor(this.tab);
    }

    private String faceLabel() {
        return showingOnFace() ? "On face" : "Show on face";
    }

    private void toggleFace() {
        final Mk3Settings settings = this.ctx.settings();
        settings.faceMode = showingOnFace() ? Mk3Settings.FaceMode.INSTRUMENTS : faceFor(this.tab);
        this.ctx.commit();
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final AerialFrame f = frame();
        final AeroSkin k = AeroSkin.DECK;
        if (f == null) {
            c.text("Flight", this.area.x, this.area.y, k.ink(), Text.SUB);
            AeroArt.empty(c, AeroBridge.content(this.area), linked());
            this.tabRects = new Rect[0];
            return;
        }
        this.tabRects = AeroBridge.tabRects(c, this.area);
        paintPage(c, this.area, f, this.tab, k);
    }

    public static void paintPage(PanelSurface c, Rect area, AerialFrame f, int tab, AeroSkin k) {
        AeroBridge.frame(c, area, f, tab, k);
        paint(c, AeroBridge.content(area), f, tab, k);
    }

    public static void paint(PanelSurface c, Rect content, AerialFrame f, int tab, AeroSkin k) {
        switch (tab) {
            case LIFT -> AeroTabs.lift(c, content, f, k);
            case ENGINES -> AeroTabs.engines(c, content, f, k);
            case NAV -> AeroTabs.nav(c, content, f, k);
            case LOG -> AeroBridge.log(c, content, f, k);
            case TRAFFIC -> AeroTabs.traffic(c, content, f, k);
            default -> AeroBridge.bridge(c, content, f, k);
        }
        if (f.stale()) {
            AeroArt.staleVeil(c, content, f);
        }
    }

    public static void paint(PanelSurface c, Rect content, AerialFrame f, int tab) {
        paint(c, content, f, tab, AeroSkin.DECK);
    }
}
