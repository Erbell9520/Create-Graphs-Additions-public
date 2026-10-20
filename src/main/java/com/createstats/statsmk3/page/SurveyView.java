package com.createstats.statsmk3.page;

import java.util.Set;
import java.util.TreeSet;

import com.createstats.blockentity.SeismicLinks;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.network.SeismicPanelPacket;
import com.createstats.seismic.archive.ScanCamera;
import com.createstats.seismic.archive.SurveyScene;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.SurveySettings;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class SurveyView extends StudioView {
    static final double YAW_PER_PX = 0.5;
    static final double PITCH_PER_PX = 0.4;

    static final double ZOOM_STEP = 1.18;

    static final double CLICK_SLOP = 3.0;

    static final double PICK_REACH = 9.0;

    static final int CLICK_BAND = 16;

    private int tab;
    private final SurveyArt.Hits hits = new SurveyArt.Hits();
    private SurveyArt.Hit hover;

    private long selectedKey = Long.MIN_VALUE;
    private long ringKey = Long.MIN_VALUE;
    private int ledgerScroll;
    private int logScroll;
    private int legendScroll;
    private int rigScroll;
    private int chipScroll;
    private long lastClick = Long.MIN_VALUE / 2;
    private boolean newOnly;

    private long armedKey = Long.MIN_VALUE;
    private float armedFor;
    private float sentFor;
    private SurveyArt.Kind sentKind;
    private long sentKey;

    private SurveyArt.Hit pressed;
    private int pressButton = -1;
    private double downX;
    private double downY;
    private double travel;
    private double bandFrom = Double.NaN;

    private final SurveySettings local = new SurveySettings();

    public SurveyView(StudioContext ctx) {
        super(ctx);
        this.local.copyFrom(ctx.settings().survey);

        this.tab = switch (ctx.settings().faceMode) {
            case SURVEY_DEPTH -> SurveyArt.DEPTH;
            case SURVEY_FINDS -> SurveyArt.FINDS;
            case SURVEY_ECHO -> SurveyArt.HISTORY;
            default -> SurveyArt.SCAN;
        };
    }

    @Override
    public String id() {
        return "survey";
    }

    @Override
    public String label() {
        return "Survey";
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        c.rect(cx - 7.0f, cy - 5.0f, 14.0f, 1.2f, color);
        c.rect(cx - 5.0f, cy - 1.0f, 10.0f, 1.0f, color);
        c.roundedRect(new Rect(cx + 0.5f, cy + 2.5f, 3.5f, 3.5f), 1.0f, color);
    }

    @Override
    protected void build() {
    }

    private StatsDisplayPanelBE controller() {
        if (this.ctx.panel == null) {
            return null;
        }
        return this.ctx.panel.getControllerBE() != null ? this.ctx.panel.getControllerBE() : this.ctx.panel;
    }

    private SurveySettings view() {
        return this.local;
    }

    private long tick() {
        final StatsDisplayPanelBE p = controller();
        return p == null || p.getLevel() == null ? 0L : p.getLevel().getGameTime();
    }

    private SurveyArt.Model model() {
        final StatsDisplayPanelBE p = controller();
        final SurveyArt.Model m = p == null ? new SurveyArt.Model()
                : SurveyArt.modelOf(p, this.ctx.settings(), this.local, tick());
        m.offWall = !this.local.frames(this.ctx.settings().survey);
        m.chipScroll = this.chipScroll;
        m.newOnly = this.newOnly;
        m.selectedKey = this.selectedKey;
        m.ledgerScroll = this.ledgerScroll;
        m.logScroll = this.logScroll;
        m.legendScroll = this.legendScroll;
        m.rigScroll = this.rigScroll;
        m.hover = this.hover;
        m.ring = SurveyArt.bodyAt(m.scene, this.ringKey);
        m.armed = this.armedFor > 0.0f ? this.armedKey : Long.MIN_VALUE;
        if (this.sentFor > 0.0f) {
            m.sentKind = this.sentKind;
            m.sentKey = this.sentKey;
        }
        return m;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.sentFor = Math.max(0.0f, this.sentFor - dt);
        this.armedFor = Math.max(0.0f, this.armedFor - dt);
        final StatsDisplayPanelBE p = controller();
        if (p != null) {
            p.seismicLinks().requestIfNeeded(tick(), true);
        }
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final SurveyArt.Model m = model();
        this.hits.clear();
        SurveyArt.paint(c, this.area, m, this.tab, this.hits);
    }

    private void commit() {
    }

    private void frameWall() {
        final Mk3Settings settings = this.ctx.settings();
        final long pin = settings.survey.pin;
        settings.survey.copyFrom(this.local);
        settings.survey.pin = pin;
        this.ctx.commit();
    }

    private void send(int op, long arg, SurveyArt.Kind kind, long key) {
        final StatsDisplayPanelBE p = controller();
        if (p == null) {
            return;
        }
        PacketDistributor.sendToServer(new SeismicPanelPacket(p.getBlockPos(), op, arg));
        this.sentKind = kind;
        this.sentKey = key;
        this.sentFor = 2.0f;
    }

    @Override
    public boolean mouseMoved(double mx, double my) {
        this.hover = this.hits.at(mx, my);
        return super.mouseMoved(mx, my);
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        final SurveyArt.Hit h = this.hits.at(mx, my);
        if (h == null) {
            return false;
        }
        this.pressed = h;
        this.pressButton = button;
        this.downX = mx;
        this.downY = my;
        this.travel = 0.0;
        if (button != 0 && h.kind() != SurveyArt.Kind.SCAN_FIELD) {
            return false;
        }
        final SurveySettings v = view();
        switch (h.kind()) {
            case TAB -> this.tab = h.index();
            case SHOW_FACE -> toggleFace();
            case FRAME_WALL -> frameWall();
            case VIEW_3D -> {
                if (v.pitch <= 0.5f || v.pitch >= 89.5f) {
                    v.pitch = SurveySettings.DEFAULT_PITCH;
                    v.yaw = v.yaw == 0.0f ? SurveySettings.DEFAULT_YAW : v.yaw;
                    commit();
                }
            }
            case VIEW_PLAN -> {
                v.pitch = 90.0f;
                v.yaw = 0.0f;
                commit();
            }
            case VIEW_SECTION -> {
                v.pitch = 0.0f;
                commit();
            }
            case VIEW_RESET -> {
                v.resetView();
                this.ringKey = Long.MIN_VALUE;
                commit();
            }
            case LEGEND_ROW -> legend(h.target(), Screen.hasShiftDown());
            case LEGEND_SWATCH -> toggleHidden(h.target());
            case LOG_PLOT -> bandAt(my, true);
            case CLEAR_BAND -> {
                v.sliceLo = Float.NaN;
                v.sliceHi = Float.NaN;
                commit();
            }
            case CHIP -> {
                v.focus = h.target();
                this.selectedKey = Long.MIN_VALUE;
                this.ledgerScroll = 0;
                commit();
            }
            case LEDGER_ROW -> this.selectedKey = h.key();
            case PIN -> {
                v.pin = v.pin == h.key() ? Long.MIN_VALUE : h.key();
                this.ctx.settings().survey.pin = v.pin;
                this.ctx.commit();
            }
            case SHOW_IN_SCAN -> showInScan(h.key());
            case RELEASE -> this.ringKey = Long.MIN_VALUE;
            case NEW_FINDS -> {
                this.newOnly = !this.newOnly || h.index() == 0;
                if (h.index() == 1) {
                    this.newOnly = false;
                }
                this.tab = SurveyArt.FINDS;
                this.ledgerScroll = 0;
            }
            case WORKED -> {
                final boolean done = model().worked.containsKey(h.key());
                send(done ? SeismicLinks.OP_UNWORK : SeismicLinks.OP_WORK, h.key(), SurveyArt.Kind.WORKED, h.key());
            }
            case FORGET -> {
                if (this.armedFor > 0.0f && this.armedKey == h.key()) {
                    this.armedFor = 0.0f;
                    this.armedKey = Long.MIN_VALUE;
                    send(SeismicLinks.OP_FORGET, h.key(), SurveyArt.Kind.FORGET, h.key());
                } else {
                    this.armedKey = h.key();
                    this.armedFor = 3.0f;
                }
            }
            case HIDE_BEFORE -> send(SeismicLinks.OP_CLEAR, 0L, SurveyArt.Kind.SHOW_ALL, 0L);
            case SHOW_ALL -> send(SeismicLinks.OP_UNCLEAR, 0L, SurveyArt.Kind.HIDE_BEFORE, 0L);
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (super.mouseDragged(mx, my, button, dx, dy)) {
            return true;
        }
        if (this.pressed == null) {
            return false;
        }
        this.travel = Math.max(this.travel, Math.hypot(mx - this.downX, my - this.downY));
        if (this.pressed.kind() == SurveyArt.Kind.SCAN_FIELD) {
            if (this.travel < CLICK_SLOP) {
                return true;
            }
            final SurveySettings v = view();
            if (this.pressButton == 0) {
                v.yaw = (float) ScanCamera.wrap(v.yaw + dx * YAW_PER_PX);
                v.pitch = (float) Math.max(0.0, Math.min(90.0, v.pitch + dy * PITCH_PER_PX));
            } else {
                pan(dx, dy);
            }
            commit();
            return true;
        }
        if (this.pressed.kind() == SurveyArt.Kind.LOG_PLOT && this.pressButton == 0) {
            bandAt(my, false);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseUp(double mx, double my, int button) {
        final boolean handled = super.mouseUp(mx, my, button);
        final SurveyArt.Hit h = this.pressed;
        this.pressed = null;
        this.bandFrom = Double.NaN;
        if (h != null && h.kind() == SurveyArt.Kind.SCAN_FIELD && button == 0 && this.travel < CLICK_SLOP) {
            final long now = System.currentTimeMillis();
            if (now - this.lastClick < 350L) {
                view().resetView();
                this.ringKey = Long.MIN_VALUE;
                this.lastClick = Long.MIN_VALUE / 2;
                return true;
            }
            this.lastClick = now;
            pickAt(mx, my);
            return true;
        }
        return handled || h != null;
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        final SurveyArt.Hit h = this.hits.at(mx, my);
        final Rect body = SurveyArt.content(this.area);
        if (h != null && h.kind() == SurveyArt.Kind.SCAN_FIELD) {
            zoomAt(mx, my, amount);
            return true;
        }
        if (h != null && h.kind() == SurveyArt.Kind.LOG_PLOT) {
            final SurveySettings v = view();
            if (v.hasSlice()) {
                final float step = (float) Math.signum(amount) * 4.0f;
                v.sliceLo += step;
                v.sliceHi += step;
                commit();
            }
            return true;
        }
        if (this.tab == SurveyArt.FINDS && SurveyArt.findsLayout(body).chips().contains(mx, my)) {
            final int rows = SurveyArt.legendOrder(model().scene).size();
            this.chipScroll = clampScroll(this.chipScroll - (int) Math.signum(amount), rows, 1);
            return true;
        }
        if (this.tab == SurveyArt.FINDS && SurveyArt.findsLayout(body).table().contains(mx, my)) {
            final int rows = SurveyArt.finds(model()).size();
            final int page = SurveyArt.ledgerRows(SurveyArt.findsLayout(body).ledger());
            this.ledgerScroll = clampScroll(this.ledgerScroll - (int) Math.signum(amount) * 3, rows, page);
            return true;
        }
        if (this.tab == SurveyArt.HISTORY && SurveyArt.historyLayout(body).logCard().contains(mx, my)) {
            final int rows = model().scene.bowls.size();
            this.logScroll = clampScroll(this.logScroll - (int) Math.signum(amount), rows, 3);
            return true;
        }
        if (this.tab == SurveyArt.HISTORY && SurveyArt.historyLayout(body).rigsCard().contains(mx, my)) {
            final int rows = model().scene.rigs.size();
            this.rigScroll = clampScroll(this.rigScroll - (int) Math.signum(amount), rows, 1);
            return true;
        }
        if (this.tab == SurveyArt.SCAN && SurveyArt.scanLayout(body).legend().contains(mx, my)) {
            final int rows = SurveyArt.legendOrder(model().scene).size();
            this.legendScroll = clampScroll(this.legendScroll - (int) Math.signum(amount), rows, 1);
            return true;
        }
        return false;
    }

    private static int clampScroll(int v, int rows, int page) {
        return Math.max(0, Math.min(Math.max(0, rows - page), v));
    }

    private Mk3Settings.FaceMode faceForTab() {
        return SurveyArt.faceFor(this.tab);
    }

    private void toggleFace() {
        final Mk3Settings settings = this.ctx.settings();
        if (settings.faceMode == faceForTab()) {
            settings.faceMode = Mk3Settings.FaceMode.INSTRUMENTS;
            this.ctx.commit();
            return;
        }
        settings.faceMode = faceForTab();

        frameWall();
    }

    private void legend(String target, boolean alone) {
        final SurveySettings v = view();
        final SurveyArt.Model m = model();
        if (alone) {
            final Set<String> others = new TreeSet<>(m.scene.targetsSurveyed());
            others.remove(target);
            final String csv = String.join(",", others);
            v.hidden = csv.equals(v.hidden) ? "" : csv;
            v.focus = "";
        } else {
            v.focus = target.equals(v.focus) ? "" : target;

            final Set<String> hidden = new TreeSet<>(SurveyArt.hiddenSet(v.hidden));
            if (hidden.remove(target)) {
                v.hidden = String.join(",", hidden);
            }
        }
        this.selectedKey = Long.MIN_VALUE;
        commit();
    }

    private void toggleHidden(String target) {
        final SurveySettings v = view();
        final Set<String> hidden = new TreeSet<>(SurveyArt.hiddenSet(v.hidden));
        if (!hidden.remove(target)) {
            hidden.add(target);
            if (target.equals(v.focus)) {
                v.focus = "";
            }
        }
        v.hidden = String.join(",", hidden);
        commit();
    }

    private void bandAt(double my, boolean start) {
        final SurveyArt.Model m = model();
        if (m.scene.isEmpty()) {
            return;
        }
        final Rect plot = SurveyArt.logPlot(SurveyArt.depthLayout(SurveyArt.content(this.area)).log(), Text.MICRO,
                SurveyArt.legendOrder(m.scene).size());
        final double y = SurveyArt.logY(plot, m.scene, my);
        final SurveySettings v = view();
        if (start || Double.isNaN(this.bandFrom)) {
            this.bandFrom = y;
            final int mid = (int) Math.round(y);
            v.sliceLo = mid - CLICK_BAND / 2;
            v.sliceHi = mid + CLICK_BAND / 2 - 1;
        } else if (Math.abs(y - this.bandFrom) >= 2.0) {
            v.sliceLo = (float) Math.floor(Math.min(y, this.bandFrom));
            v.sliceHi = (float) Math.ceil(Math.max(y, this.bandFrom));
        }
        commit();
    }

    private Rect field() {
        return SurveyArt.scanLayout(SurveyArt.content(this.area)).field();
    }

    private ScanCamera camera(SurveyArt.Model m) {
        return SurveyKit.camera(m.scene, field(), SurveyArt.pageScan(m));
    }

    private void setCentre(double[] c) {
        final SurveySettings v = view();
        v.cx = (float) c[0];
        v.cy = (float) c[1];
        v.cz = (float) c[2];
    }

    private void pan(double dx, double dy) {
        final SurveyArt.Model m = model();
        if (m.scene.isEmpty()) {
            return;
        }
        final Rect f = field();
        setCentre(camera(m).unproject(f.centerX() - dx, f.centerY() - dy));
    }

    private void zoomAt(double mx, double my, double amount) {
        final SurveyArt.Model m = model();
        if (m.scene.isEmpty() || amount == 0.0) {
            return;
        }
        final SurveySettings v = view();
        final ScanCamera cam = camera(m);
        final float zoom = (float) Math.max(SurveySettings.MIN_ZOOM, Math.min(SurveySettings.MAX_ZOOM,
                v.zoom * Math.pow(ZOOM_STEP, amount)));
        if (zoom == v.zoom) {
            return;
        }
        final double[] w = cam.unproject(mx, my);
        final double s2 = cam.scale * zoom / v.zoom;
        final Rect f = field();

        final ScanCamera at = new ScanCamera(cam.yaw, cam.pitch, s2, w[0], w[1], w[2], mx, my);
        setCentre(at.unproject(f.centerX(), f.centerY()));
        v.zoom = zoom;
        commit();
    }

    private void pickAt(double mx, double my) {
        final SurveyArt.Model m = model();
        if (m.scene.isEmpty()) {
            return;
        }
        final SurveyScene.Body b = SurveyKit.pick(m.scene, field(), SurveyArt.pageScan(m), mx, my, PICK_REACH);
        if (b == null) {
            return;
        }
        focusCamera(m, b);
    }

    private void focusCamera(SurveyArt.Model m, SurveyScene.Body b) {
        final SurveySettings v = view();
        this.ringKey = BlockPos.asLong(b.nearX, b.nearY, b.nearZ);
        final Rect f = field();
        final SurveyKit.Scan o = SurveyArt.pageScan(m);
        o.centre = null;
        o.zoom = 1.0;
        final ScanCamera whole = SurveyKit.camera(m.scene, f, o);
        final double extent = Math.max(Math.max(b.maxX - b.minX, b.maxZ - b.minZ), b.maxY - b.minY) + 1.0 + 16.0;
        final double want = Math.min(f.w, f.h) * 0.36 / extent;
        v.zoom = (float) Math.max(Math.max(1.0, v.zoom), Math.min(SurveySettings.MAX_ZOOM, want / whole.scale));
        setCentre(new double[] {b.cx, b.cy, b.cz});
        commit();
    }

    private void showInScan(long key) {
        final SurveyArt.Model m = model();
        final int x = BlockPos.getX(key);
        final int y = BlockPos.getY(key);
        final int z = BlockPos.getZ(key);
        SurveyScene.Body best = null;
        for (final SurveyScene.Layer l : m.scene.layers) {
            for (final SurveyScene.Body b : l.bodies) {
                if (b.nearX == x && b.nearY == y && b.nearZ == z) {
                    best = b;
                }
            }
        }
        if (best == null) {
            return;
        }
        this.tab = SurveyArt.SCAN;
        focusCamera(m, best);
    }
}
