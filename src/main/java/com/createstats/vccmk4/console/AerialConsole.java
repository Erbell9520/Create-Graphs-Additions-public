package com.createstats.vccmk4.console;

import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.AirLight;
import com.createstats.vccmk4.air.core.Feel;
import com.createstats.vccmk4.air.core.LeaveMode;
import com.createstats.vccmk4.air.core.Telegraph;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.HeldKeys;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.KeyIds;
import com.createstats.vccmk4.core.Keymap;
import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.network.AerialActionPacket;
import com.createstats.vccmk4.network.AerialView;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class AerialConsole extends ConsoleKit {
    public static final int MIN_W = Vcc4Console.MIN_W;
    public static final int MIN_H = Vcc4Console.MIN_H;
    public static final int MAX_W = Vcc4Console.MAX_W;
    public static final int MAX_H = Vcc4Console.MAX_H;
    public static final int MARGIN = Vcc4Console.MARGIN;

    static final String L = "gui.create_stats.vcc_mk4_aerial.";

    static final String CONTROL = "gui.create_stats.vcc_mk4.control.";

    static final int DIAL_AHEAD = IVORY;
    static final int DIAL_AHEAD_ALT = 0xFFE6DCC0;
    static final int DIAL_ASTERN = 0xFFF0C4B4;
    static final int DIAL_ASTERN_ALT = 0xFFE2AE9C;
    static final int DIAL_STOP = 0xFFFFFFFF;
    static final int DIAL_LIT = BRASS_HI;

    static final int SKY_HIGH = 0xFF78A7FF;
    static final int SKY_LOW = 0xFFC8DEFF;
    static final int GRASS = 0xFF5E9D34;
    static final int GRASS_DARK = 0xFF4A7D28;
    static final int EARTH = 0xFF79553A;
    static final int EARTH_DARK = 0xFF5E4029;
    static final int EARTH_TOP = GRASS;

    static final int HOT_AIR = 0xFFFF9E2C;
    static final int STEAM = 0xFFE8F4FF;

    static final int FABRIC_LIGHT = 0xFFF6F6F4;
    static final int FABRIC_DARK = 0xFFA6AAAB;
    static final int SEAM = 0x26000000;

    static final int HULL_LIGHT = 0xFFA8ADA5;
    static final int HULL = 0xFF7F847D;
    static final int HULL_DARK = 0xFF553A1F;

    static final int FIRE = 0xFFFF8A1E;
    static final int FIRE_CORE = 0xFFFFE66D;
    static final int SMOKE = 0x70B4B4B4;

    static final int CLOUD = 0xDDFFFFFF;
    static final int CLOUD_SHADE = 0xCCDCE6F0;
    static final int SUN = 0xFFFFF8B0;

    static final double TAPE_PPB = 4.0;

    static final double COMPASS_SPAN = 150.0;

    static final int TURN_STEP = 15;

    static final int CAP_STEP = AerialSetup.CAP_STEP;

    static final double PITCH_DRAWN = 30.0;

    public enum Page {
        FLIGHT("flight"), LIFT("lift"), ENGINES("engines"), FINS("fins"), TOOLS("tools"), LIGHTS("lights"), KEYS("keys"),
        SHIP("ship");

        final String key;

        Page(String key) {
            this.key = key;
        }
    }

    private AerialView v;
    private Page page;
    @Nullable private Control captureControl;
    private int captureSlot;
    @Nullable private String captureNote;
    private final Set<Integer> previewKeys = new TreeSet<>();
    private int ticks;
    private int lastRings = Integer.MIN_VALUE;
    private int ringFlash;

    @Nullable private String armed;
    private int armedUntil;
    private double clickX;
    private double clickY;

    private long pickedEngine = Long.MIN_VALUE;
    private long pickedFin = Long.MIN_VALUE;
    private long pickedTool = Long.MIN_VALUE;
    private long pickedLight = Long.MIN_VALUE;

    private int balloonFirst;

    int dialCx;
    int dialCy;
    int dialR;
    int tapeX0;
    int tapeY0;
    int tapeX1;
    int tapeY1;
    int stripX0;
    int stripY0;
    int stripX1;
    int stripY1;
    int stepY;
    int stepAsternX;
    int stepAheadX;

    public AerialConsole(ConsoleHost host, AerialView view, Page page) {
        super(host, L);
        this.v = view;
        this.page = page == null ? Page.FLIGHT : page;
        this.lastRings = view.flight().rings();
    }

    public void accept(AerialView view) {
        if (view.flight().rings() != lastRings) ringFlash = 8;
        lastRings = view.flight().rings();
        this.v = view;
    }

    public AerialView view() {
        return v;
    }

    public Page page() {
        return page;
    }

    public void setPage(Page p) {
        page = p;
        popup = null;
        captureControl = null;
        armed = null;
    }

    public static int width(int screenW) {
        return clampInt(screenW - 2 * MARGIN, MIN_W, MAX_W);
    }

    public static int height(int screenH) {
        return clampInt(screenH - 2 * MARGIN, MIN_H, MAX_H);
    }

    public boolean capturing() {
        return captureControl != null;
    }

    public boolean passesKeys() {
        return captureControl == null;
    }

    public void tick() {
        ticks++;
        if (ringFlash > 0) ringFlash--;
    }

    public void render(ConsoleCanvas canvas, int screenW, int screenH, int mouseX, int mouseY) {
        begin(canvas, screenW, screenH, mouseX, mouseY, width(screenW), height(screenH));
        panel();
        header();
        switch (page) {
            case FLIGHT -> flightPage();
            case LIFT -> liftPage();
            case ENGINES -> enginesPage();
            case FINS -> finsPage();
            case TOOLS -> toolsPage();
            case LIGHTS -> lightsPage();
            case KEYS -> keysPage();
            case SHIP -> shipPage();
        }
        end();
    }

    private void panel() {
        casing(left, top, w, h);
        brassStrip(left + 3, top + 3, w - 6, 15);
        sunk(left + 4, top + TAB_Y + TAB_H, w - 8, h - TAB_Y - TAB_H - 4, SLATE, CASING_SHADE, CASING_LIGHT);
    }

    private void header() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        String status;
        int colour;
        if (!v.standDown().isEmpty()) {
            status = tr("status.stood_down");
            colour = BAD;
        } else if (v.staff() != AerialView.STAFF_NONE) {
            status = tr(v.staff() == AerialView.STAFF_LOCKED ? "status.staff_locked" : "status.staff");
            colour = WARN;
        } else if (v.flight().weighing()) {
            status = tr("status.weighing");
            colour = WARN;
        } else if (v.youPilot()) {
            status = tr("status.you");
            colour = GOOD;
        } else if (!v.pilot().isEmpty()) {
            status = tr("status.pilot", v.pilot());
            colour = GOOD;
        } else {
            status = tr("status.mode." + modeKey(v.mode()));
            colour = v.mode() == 5 || v.mode() == 0 ? TEXT_DIM : INFO;
        }

        status = fit(status.toUpperCase(Locale.ROOT), w / 3);
        int sw = host.width(status) + 13;
        int sx = x1 - sw;
        display(sx, top + 5, sw, 11);
        c.fill(sx + 3, top + 8, sx + 7, top + 12, colour);
        c.fill(sx + 4, top + 8, sx + 5, top + 9, mix(colour, 0xFFFFFFFF, 0.6));
        c.text(status, sx + 10, top + 7, colour);

        emblem(x0, top + 6);
        int tx = x0 + 22;
        String name = v.name().isEmpty() ? tr("title") : v.name();
        int room = sx - 10 - tx;
        String shown = fit(name, room);
        c.text(shown, tx, top + 7, ON_BRASS);
        if (!shown.equals(name)) hit(tx, top + 6, host.width(shown), 10, null, null, List.of(name));
        String sub = tr("title");
        int subX = tx + host.width(shown) + 8;
        if (!v.name().isEmpty() && subX + host.width(sub) <= sx - 10) c.text(sub, subX, top + 7, mix(ON_BRASS, BRASS, 0.45));

        int tabX = x0;
        for (Page p : Page.values()) {
            String label = tr("tab." + p.key);
            boolean alert = alert(p);
            int tw = host.width(label) + 12 + (alert ? 7 : 0);
            tab(tabX, top + TAB_Y, tw, label, p == page, alert);
            final Page target = p;
            hit(tabX, top + TAB_Y, tw, TAB_H, () -> setPage(target), null, List.of(tr("tab." + p.key + ".hint")));
            tabX += tw + 1;
        }
        if (page != Page.FLIGHT && v.onCraft() && Float.isFinite(v.flight().altitude())) {
            AerialView.Flight f = v.flight();
            String live = tr("live", num0(f.altitude()), orderName(f.order()), num1(f.airForward()), heading3(f.heading()));
            if (host.width(live) <= x1 - tabX - 8) textRight(live, x1, top + TAB_Y + 4, ON_CASING);
        }
    }

    private void emblem(int x, int y) {
        c.fill(x + 1, y, x + 17, y + 7, ON_BRASS);
        c.fill(x, y + 1, x + 18, y + 6, ON_BRASS);
        c.fill(x + 2, y + 1, x + 16, y + 6, FABRIC_LIGHT);
        c.fill(x + 1, y + 2, x + 17, y + 5, FABRIC_LIGHT);
        c.fill(x + 2, y + 5, x + 16, y + 6, FABRIC_DARK);
        c.fill(x + 3, y + 2, x + 15, y + 3, HOT_AIR);
        c.fill(x + 6, y + 7, x + 12, y + 9, ON_BRASS);
        c.fill(x + 7, y + 7, x + 11, y + 8, HULL_LIGHT);
    }

    boolean alert(Page p) {
        return switch (p) {
            case FLIGHT -> !v.standDown().isEmpty();
            case LIFT -> v.flight().liftShort();
            case ENGINES -> v.engines().stream().anyMatch(AerialConsole::engineTrouble);
            case FINS -> v.surfaces().stream().anyMatch(s -> s.role() != AerialSetup.SurfaceRole.OFF.ordinal()
                    && (!s.assembled() || !s.locked()));
            case TOOLS -> v.tools().stream().anyMatch(AerialView.Tool::foreign);
            default -> false;
        };
    }

    static boolean engineTrouble(AerialView.Engine e) {
        return e.state() == AerialView.ENGINE_STARVED || e.state() == AerialView.ENGINE_OVERSTRESSED
                || e.state() == AerialView.ENGINE_FOREIGN;
    }

    private void flightPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y0 = top + CONTENT_Y;
        int y1 = top + h - PAD;
        AerialView.Flight f = v.flight();
        int stripY = y1 - BUTTON_H;
        int noteY = stripY - 14;
        int bottom = noteY - 5;
        int tw = clampInt(w * 30 / 100, 128, 176);
        int ta = w >= 520 ? 58 : 50;
        int ax1 = x0 + tw;
        int bx0 = ax1 + 6;
        int bx1 = bx0 + ta;
        int cx0 = bx1 + 6;

        telegraph(x0, y0, ax1, bottom, f);
        altitude(bx0, y0, bx1, bottom, f);
        int stripBottom = compass(cx0, y0, x1, f);
        sideView(cx0, stripBottom + 4, x1, bottom, SideMarks.FORCES);
        notesRow(x0, noteY, x1);
        bottomStrip(x0, stripY, x1, f);
    }

    private void telegraph(int x0, int y0, int x1, int y1, AerialView.Flight f) {
        int body = card(x0, y0, x1 - x0, y1 - y0, tr("telegraph"));
        int tw = x1 - x0;
        int room = y1 - body - 70;
        int r = clampInt(Math.min((tw - 20) / 2, room), 34, 64);
        int cx = x0 + tw / 2;
        int cy = body + r + 5;
        dialCx = cx;
        dialCy = cy;
        dialR = r;
        dial(cx, cy, r, f);
        c.text(tr("astern"), cx - r - 1, cy + 4, mix(BAD, TEXT_DIM, 0.3));
        textRight(tr("ahead"), cx + r + 2, cy + 4, TEXT_DIM);

        int py = cy + 15;
        String order = fit(orderName(f.order()), tw - 16);
        int pw = Math.max(host.width(order) + 14, 60);
        display(cx - pw / 2, py, pw, 13);
        textCentre(order, cx, py + 3, f.order() < -1.0e-3 ? BAD : f.order() > 1.0e-3 ? YELLOW : TEXT);

        int by = py + 17;
        String stop = tr("stop");
        int stopW = host.width(stop) + 12;
        int rowW = 18 + 2 + stopW + 2 + 18;
        int bx = cx - rowW / 2;
        stepY = by;
        stepAsternX = bx;
        stepAheadX = bx + 22 + stopW;

        Telegraph.Notch astern = Telegraph.nextNotch(f.order(), -1);
        Telegraph.Notch ahead = Telegraph.nextNotch(f.order(), 1);
        button(bx, by, 18, "<", false, astern == null ? null : () -> ring(astern.ordinal()), null, List.of(tr("step_astern.hint")));
        button(bx + 20, by, stopW, stop, Math.abs(f.order()) < 1.0e-3, () -> ring(Telegraph.Notch.STOP.ordinal()), null,
                List.of(tr("stop.hint")));
        button(bx + 22 + stopW, by, 18, ">", false, ahead == null ? null : () -> ring(ahead.ordinal()), null,
                List.of(tr("step_ahead.hint")));

        int ry = by + BUTTON_H + 5;
        if (ry + 8 <= y1 - 3) textFit(engineWords(f), x0 + 6, ry, tw - 12, f.braking() ? TEXT : TEXT_DIM);
        ry += LINE;
        if (ry + 8 <= y1 - 3) {
            String speed = Float.isFinite(f.topSpeed())
                    ? tr("speed_top", num1(f.airForward()), num1(f.topSpeed()))
                    : tr("speed", num1(f.airForward()));
            textFit(speed, x0 + 6, ry, tw - 12, TEXT_DIM);
        }
    }

    private void dial(int cx, int cy, int r, AerialView.Flight f) {
        Telegraph.Notch[] notches = Telegraph.Notch.values();
        int n = notches.length;
        double step = 180.0 / n;
        int hover = dialSector(cx, cy, r, mx, my);
        Telegraph.Notch ordered = Telegraph.Notch.byOrdinal(nearest(f.order()).ordinal());
        boolean onNotch = Math.abs(f.order() - ordered.value) < 1.0e-3;

        boolean flash = ringFlash > 0 && (ringFlash / 2) % 2 == 0;
        for (int k = 0; k < 24; k++) {
            double a0 = 180.0 - k * 7.5;
            wedge(cx, cy, r + 5, a0, a0 - 7.5, EDGE);
        }
        for (int k = 0; k < 24; k++) {
            double a0 = 180.0 - k * 7.5;
            wedge(cx, cy, r + 4, a0, a0 - 7.5, flash ? BRASS_HI : k % 2 == 0 ? BRASS : mix(BRASS, BRASS_DIM, 0.35));
        }
        for (int i = 0; i < n; i++) {
            int colour;
            if (i == Telegraph.Notch.STOP.ordinal()) colour = DIAL_STOP;
            else if (i < Telegraph.Notch.STOP.ordinal()) colour = i % 2 == 0 ? DIAL_ASTERN : DIAL_ASTERN_ALT;
            else colour = i % 2 == 0 ? DIAL_AHEAD : DIAL_AHEAD_ALT;
            if (onNotch && i == ordered.ordinal()) colour = DIAL_LIT;
            else if (i == hover) colour = mix(colour, BRASS_HI, 0.5);
            double a0 = 180.0 - i * step;
            for (int k = 0; k < 4; k++) wedge(cx, cy, r, a0 - k * step / 4.0, a0 - (k + 1) * step / 4.0, colour);
        }
        for (int b = 1; b < n; b++) {
            double a = Math.toRadians(180.0 - b * step);
            line(cx + (r - 9) * Math.cos(a), cy - (r - 9) * Math.sin(a), cx + r * Math.cos(a), cy - r * Math.sin(a), 1.0, 0x88231E17);
        }

        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(180.0 - (i + 0.5) * step);
            int mark = i < Telegraph.Notch.STOP.ordinal() ? 0xFFA23A2C : i == Telegraph.Notch.STOP.ordinal() ? ON_IVORY : 0xFF6E5B3A;
            line(cx + (r - 5) * Math.cos(a), cy - (r - 5) * Math.sin(a), cx + (r - 1) * Math.cos(a), cy - (r - 1) * Math.sin(a), 2.0, mark);
        }

        c.fill(cx - r - 5, cy, cx + r + 6, cy + 3, EDGE);
        c.fill(cx - r - 4, cy, cx + r + 5, cy + 2, BRASS_DIM);

        double answer = Math.toRadians(dialAngle(f.engine()));
        int answerColour = Math.abs(f.engine()) > 0.02f ? (f.engine() < 0 ? 0xFFC0503E : 0xFFB0852F) : 0xFF8C8272;
        line(cx, cy, cx + 0.58 * r * Math.cos(answer), cy - 0.58 * r * Math.sin(answer), 5.0, answerColour);
        double order = Math.toRadians(dialAngle(f.order()));
        double tipX = cx + (r - 4) * Math.cos(order);
        double tipY = cy - (r - 4) * Math.sin(order);
        line(cx, cy, tipX, tipY, 2.0, ON_IVORY);
        double back = order + Math.PI;
        line(cx, cy, cx + 8 * Math.cos(back), cy - 8 * Math.sin(back), 3.0, ON_IVORY);
        disc(cx, cy, 4.5, BRASS_DIM);
        disc(cx, cy, 3.2, BRASS);
        disc(cx, cy, 1.2, ON_IVORY);

        List<String> tip = new ArrayList<>();
        if (hover >= 0) tip.add(tr("ring", orderName(notches[hover].value)));
        tip.add(tr("telegraph.hint"));
        tip.add(tr("telegraph.answer"));
        hit(cx - r - 3, cy - r - 3, 2 * r + 6, r + 6, () -> {
            int s = dialSector(cx, cy, r, clickX, clickY);
            if (s >= 0) ring(s);
        }, null, tip);
    }

    private void wedge(double cx, double cy, double r, double a0, double a1, int colour) {
        double t0 = Math.toRadians(a0);
        double t1 = Math.toRadians(a1);
        triangle((float) cx, (float) cy, (float) (cx + r * Math.cos(t0)), (float) (cy - r * Math.sin(t0)),
                (float) (cx + r * Math.cos(t1)), (float) (cy - r * Math.sin(t1)), colour);
    }

    static int dialSector(double cx, double cy, double r, double px, double py) {
        double dx = px - cx;
        double dy = cy - py;
        if (dy < -2.0 || Math.hypot(dx, dy) > r + 3.0 || Math.hypot(dx, dy) < 4.0) return -1;
        double a = Math.toDegrees(Math.atan2(Math.max(0.0, dy), dx));
        int n = Telegraph.Notch.values().length;
        return clampInt((int) Math.floor((180.0 - a) / (180.0 / n)), 0, n - 1);
    }

    static double dialAngle(double order) {
        Telegraph.Notch[] notches = Telegraph.Notch.values();
        double p = Double.isFinite(order) ? Math.max(-1.0, Math.min(1.0, order)) : 0.0;
        double s = 0.0;
        for (int i = 0; i < notches.length - 1; i++) {
            double lo = notches[i].value;
            double hi = notches[i + 1].value;
            if (p <= hi || i == notches.length - 2) {
                s = i + Math.max(0.0, Math.min(1.0, (p - lo) / (hi - lo)));
                break;
            }
        }
        return 180.0 - (s + 0.5) * (180.0 / notches.length);
    }

    private static Telegraph.Notch nearest(double order) {
        Telegraph.Notch best = Telegraph.Notch.STOP;
        for (Telegraph.Notch n : Telegraph.Notch.values()) {
            if (Math.abs(n.value - order) < Math.abs(best.value - order)) best = n;
        }
        return best;
    }

    private void ring(int notch) {
        send(AerialActionPacket.TELEGRAPH, notch, 0, 0L);
    }

    String orderName(double order) {
        Telegraph.Notch n = nearest(order);
        if (Math.abs(n.value - order) < 1.0e-3) return tr("order." + n.name().toLowerCase(Locale.ROOT));
        return tr(order > 0.0 ? "order.ahead_share" : "order.astern_share", pct(Math.abs(order)));
    }

    private String engineWords(AerialView.Flight f) {
        if (!(f.thrustAhead() + f.thrustAstern() > 0f) && Math.abs(f.order()) > 1.0e-3) return tr("engines.none");
        int mode = v.mode();
        if (mode == MODE_ANCHOR) return tr("engines.anchored");
        if (f.braking()) return Math.abs(f.airForward()) > 0.3f ? tr("engines.braking") : tr("engines.holding");

        if (Float.isFinite(f.targetSpeed())) return tr("engines.speed", num1(f.targetSpeed()));
        if (Math.abs(f.engine()) < 0.01f) return tr("engines.stopped");
        String pct = Math.round(Math.abs(f.engine()) * 100f) + "%";
        return f.engine() > 0 ? tr("engines.ahead", pct) : tr("engines.astern", pct);
    }

    private void altitude(int x0, int y0, int x1, int y1, AerialView.Flight f) {
        int bw = (x1 - x0 - 2) / 2;
        int bw2 = x1 - x0 - bw - 2;
        button(x0, y0, bw, "+1", false, () -> send(AerialActionPacket.ALTITUDE, 1, 0, 0L), null, List.of(tr("alt.up.hint", 1)));
        button(x0 + bw + 2, y0, bw2, "+10", false, () -> send(AerialActionPacket.ALTITUDE, 10, 0, 0L), null,
                List.of(tr("alt.up.hint", 10)));
        int ty0 = y0 + BUTTON_H + 3;
        int low = y1 - 21 - BUTTON_H;
        int ty1 = low - 3;
        tape(x0, ty0, x1, ty1, f);
        button(x0, low, bw, "-1", false, () -> send(AerialActionPacket.ALTITUDE, -1, 0, 0L), null, List.of(tr("alt.down.hint", 1)));
        button(x0 + bw + 2, low, bw2, "-10", false, () -> send(AerialActionPacket.ALTITUDE, -10, 0, 0L), null,
                List.of(tr("alt.down.hint", 10)));
        int ry = low + BUTTON_H + 3;
        String climb = tr("climb", signed1(f.climb()));
        textFit(climb, x0, ry, x1 - x0, Math.abs(f.climb()) > 0.2f ? (f.climb() > 0 ? GOOD : WARN) : TEXT_DIM);
        String hold = Float.isFinite(f.targetAltitude()) ? tr("hold_alt", num0(f.targetAltitude())) : tr("hold_alt.none");
        textFit(hold, x0, ry + LINE, x1 - x0, YELLOW);
    }

    private void tape(int x0, int y0, int x1, int y1, AerialView.Flight f) {
        tapeX0 = x0;
        tapeY0 = y0;
        tapeX1 = x1;
        tapeY1 = y1;
        display(x0, y0, x1 - x0, y1 - y0);
        int ix0 = x0 + 1;
        int iy0 = y0 + 1;
        int ix1 = x1 - 1;
        int iy1 = y1 - 1;
        int mid = (iy0 + iy1) / 2;
        if (!Float.isFinite(f.altitude())) {
            textCentre("-", (ix0 + ix1) / 2, mid - 4, TEXT_FAINT);
            return;
        }
        double alt = f.altitude();
        if (Float.isFinite(f.ceiling())) {
            int cy = tapeY(f.ceiling(), alt, mid);
            if (cy > iy0) {
                int end = Math.min(cy, iy1);
                c.fill(ix0, iy0, ix1, end, 0xFF3A1E1E);
                for (int y = end - 1; y >= iy0; y -= 3) c.fill(ix0, y, ix1, y + 1, 0xFF6E2A26);
                if (cy < iy1) c.fill(ix0, cy, ix1, cy + 1, BAD);
            }
        }
        if (Float.isFinite(f.cap()) && (!Float.isFinite(f.ceiling()) || f.cap() < f.ceiling())) {
            int cy = tapeY(f.cap(), alt, mid);
            if (cy > iy0 && cy < iy1) c.fill(ix0, cy, ix1, cy + 1, INFO);
        }
        if (Float.isFinite(f.ground())) {
            int gy = tapeY(f.ground(), alt, mid);
            if (gy < iy1) {
                c.fill(ix0, Math.max(gy, iy0), ix1, iy1, EARTH);
                if (gy >= iy0) c.fill(ix0, gy, ix1, gy + 1, EARTH_TOP);
            }
        }
        int lo = (int) Math.floor(alt - (mid - iy0) / TAPE_PPB);
        int hi = (int) Math.ceil(alt + (iy1 - mid) / TAPE_PPB);
        for (int a = lo - Math.floorMod(lo, 2); a <= hi; a += 2) {
            int y = tapeY(a, alt, mid);
            if (y <= iy0 || y >= iy1) continue;
            boolean major = Math.floorMod(a, 10) == 0;
            c.fill(ix0, y, ix0 + (major ? 7 : 3), y + 1, major ? TEXT_DIM : TEXT_FAINT);
            if (major && Math.abs(y - mid) > 10 && y - 4 > iy0 + 1 && y + 5 < iy1) c.text(Integer.toString(a), ix0 + 9, y - 4, TEXT_DIM);
        }
        if (Float.isFinite(f.targetAltitude())) {
            int ty = tapeY(f.targetAltitude(), alt, mid);
            boolean off = ty < iy0 + 4 || ty > iy1 - 4;
            ty = clampInt(ty, iy0 + 4, iy1 - 4);
            triangle(ix1, ty - 4, ix1, ty + 4, ix1 - 7, ty, off ? mix(YELLOW, DISPLAY, 0.5) : YELLOW);
            if (!off) c.fill(ix1 - 12, ty, ix1 - 7, ty + 1, YELLOW);
        }

        raised(ix0, mid - 6, ix1 - ix0, 13, IVORY, 0xFFFFFFFF, IVORY_DIM);
        triangle(ix0 - 1, mid - 3, ix0 - 1, mid + 4, ix0 - 4, mid, YELLOW);
        textCentre(num1(alt), (ix0 + ix1) / 2, mid - 3, ON_IVORY);

        double at = alt + (mid - my) / TAPE_PPB;
        List<String> tip = new ArrayList<>();
        tip.add(tr("tape.hint", num0(Math.round(at))));
        if (Float.isFinite(f.ceiling())) tip.add(tr("tape.ceiling", num0(f.ceiling())));
        if (Float.isFinite(f.keel())) tip.add(tr("tape.keel", num1(f.keel())));
        hit(x0, y0, x1 - x0, y1 - y0, () -> {
            double want = alt + (mid - clickY) / TAPE_PPB;
            double from = Float.isFinite(f.targetAltitude()) ? f.targetAltitude() : alt;
            send(AerialActionPacket.ALTITUDE, (int) Math.round(want - from), 0, 0L);
        }, null, tip);
    }

    private static int tapeY(double a, double alt, int mid) {
        return (int) Math.round(mid - (a - alt) * TAPE_PPB);
    }

    private int compass(int x0, int y0, int x1, AerialView.Flight f) {
        boolean known = v.onCraft() && Float.isFinite(f.altitude());
        int sh = 20;
        button(x0, y0 + 3, 14, "<", false, () -> send(AerialActionPacket.HEADING, -TURN_STEP, 0, 0L), null,
                List.of(tr("turn.hint", tr("turn.left"), TURN_STEP)));
        button(x1 - 14, y0 + 3, 14, ">", false, () -> send(AerialActionPacket.HEADING, TURN_STEP, 0, 0L), null,
                List.of(tr("turn.hint", tr("turn.right"), TURN_STEP)));
        strip(x0 + 16, y0, x1 - 16, y0 + sh, f, known);
        return y0 + sh;
    }

    private void strip(int x0, int y0, int x1, int y1, AerialView.Flight f, boolean known) {
        stripX0 = x0;
        stripY0 = y0;
        stripX1 = x1;
        stripY1 = y1;
        display(x0, y0, x1 - x0, y1 - y0);
        int ix0 = x0 + 1;
        int ix1 = x1 - 1;
        int mid = (ix0 + ix1) / 2;
        if (!known) {
            textCentre("-", mid, y0 + 6, TEXT_FAINT);
            return;
        }
        double ppd = (ix1 - ix0) / COMPASS_SPAN;
        double hdg = f.heading();
        String box = heading3(hdg);
        int boxW = host.width(box) + 8;
        int lo = (int) Math.floor(hdg - COMPASS_SPAN / 2);
        int hi = (int) Math.ceil(hdg + COMPASS_SPAN / 2);
        for (int d = lo - Math.floorMod(lo, 5); d <= hi; d += 5) {
            int x = (int) Math.round(mid + wrap180(d - hdg) * ppd);
            if (x <= ix0 + 1 || x >= ix1 - 1) continue;
            int bearing = Math.floorMod(d, 360);
            boolean major = bearing % 15 == 0;
            c.fill(x, y1 - 1 - (major ? 5 : 2), x + 1, y1 - 1, major ? TEXT_DIM : TEXT_FAINT);
            if (bearing % 30 == 0) {
                String label = switch (bearing) {
                    case 0 -> "N";
                    case 90 -> "E";
                    case 180 -> "S";
                    case 270 -> "W";
                    default -> Integer.toString(bearing);
                };
                int lw = host.width(label);
                boolean underBox = Math.abs(x - mid) < lw / 2 + boxW / 2 + 3;
                if (!underBox && x - lw / 2 > ix0 + 1 && x + lw - lw / 2 < ix1 - 1) {
                    c.text(label, x - lw / 2, y0 + 3, bearing % 90 == 0 ? YELLOW : TEXT_DIM);
                }
            }
        }

        AerialView.Nav nav = v.nav();
        if (nav.found() && Float.isFinite(nav.bearing()) && (v.mode() == MODE_GO_TO || v.leave() == LeaveMode.GO_TO.ordinal())) {
            double off = wrap180(nav.bearing() - hdg);
            int fx = (int) Math.round(mid + off * ppd);
            boolean outside = fx < ix0 + 3 || fx > ix1 - 7;
            fx = clampInt(fx, ix0 + 3, ix1 - 7);
            int flag = outside ? mix(INFO, DISPLAY, 0.45) : INFO;
            c.fill(fx, y0 + 3, fx + 1, y1 - 2, flag);
            triangle(fx + 1, y0 + 3, fx + 1, y0 + 9, fx + 6, y0 + 6, flag);
        }
        if (Float.isFinite(f.targetHeading())) {
            double off = wrap180(f.targetHeading() - hdg);
            int tx = (int) Math.round(mid + off * ppd);
            boolean outside = tx < ix0 + 5 || tx > ix1 - 5;
            tx = clampInt(tx, ix0 + 5, ix1 - 5);
            triangle(tx - 4, y1 - 1, tx, y1 - 7, tx + 4, y1 - 1, outside ? mix(YELLOW, DISPLAY, 0.5) : YELLOW);
        }

        raised(mid - boxW / 2, y0 + 1, boxW, 11, IVORY, 0xFFFFFFFF, IVORY_DIM);
        c.text(box, mid - host.width(box) / 2, y0 + 3, ON_IVORY);
        triangle(mid - 3, y0 + 12, mid + 4, y0 + 12, mid + 0.5f, y0 + 16, YELLOW);

        double at = hdg + (mx - mid) / ppd;
        List<String> tip = new ArrayList<>();
        tip.add(tr("compass.hint", heading3(at)));
        tip.add(Float.isFinite(f.targetHeading()) ? tr("holds", heading3(f.targetHeading())) : tr("holds.none"));
        if (nav.found() && (v.mode() == MODE_GO_TO || v.leave() == LeaveMode.GO_TO.ordinal())) {
            tip.add(tr("goto.flag", heading3(nav.bearing()), num0(nav.distance())));
        }
        hit(x0, y0, x1 - x0, y1 - y0, () -> {
            double want = hdg + (clickX - mid) / ppd;

            if (Math.abs(clickX - mid) <= boxW / 2.0) {
                send(AerialActionPacket.HEADING, 0, 1, 0L);
                return;
            }
            double from = Float.isFinite(f.targetHeading()) ? f.targetHeading() : hdg;
            send(AerialActionPacket.HEADING, (int) Math.round(wrap180(want - from)), 0, 0L);
        }, null, tip);
    }

    static double wrap180(double d) {
        double x = ((d + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return x == -180.0 ? 180.0 : x;
    }

    enum SideMarks {
        FORCES,

        FINS
    }

    private record ShipFrame(double ox, double oy, double px, int rows, boolean fromAbove, double originLon, double originAcross,
                             double cell, double comX, double comY, double cos, double sin) {
        double[] at(double lon, double across) {
            double sx = ox + (lon - originLon) / cell * px;
            double sy = fromAbove ? oy + (across - originAcross) / cell * px : oy + rows * px - (across - originAcross) / cell * px;
            return turn(sx, sy);
        }

        double[] turn(double x, double y) {
            double dx = x - comX;
            double dy = y - comY;
            return new double[] {comX + dx * cos + dy * sin, comY - dx * sin + dy * cos};
        }

        double[] dir(double lonPart, double acrossPart) {
            double dx = lonPart;
            double dy = fromAbove ? acrossPart : -acrossPart;
            return new double[] {dx * cos + dy * sin, -dx * sin + dy * cos};
        }

        double pxPerBlock() {
            return px / cell;
        }
    }

    private void pictureBox(int x0, int y0, int x1, int y1, boolean sky) {
        tags.clear();
        sunk(x0, y0, x1 - x0, y1 - y0, sky ? SKY_HIGH : GRASS, SLATE_EDGE, SLATE_RIM);
        int ix0 = x0 + 1;
        int iy0 = y0 + 1;
        int ix1 = x1 - 1;
        int iy1 = y1 - 1;
        if (!sky) {
            grass(ix0, iy0, ix1, iy1);
            return;
        }
        gradient(ix0, iy0, ix1, iy1, SKY_HIGH, SKY_LOW, 16);

        int sx = ix1 - 22;
        int sy = iy0 + 7;
        clipped(sx - 3, sy - 3, sx + 13, sy + 13, 0x33FFFFD0, ix0, iy0, ix1, iy1);
        clipped(sx, sy, sx + 10, sy + 10, SUN, ix0, iy0, ix1, iy1);
        clipped(sx + 2, sy + 2, sx + 8, sy + 8, 0xFFFFFFE8, ix0, iy0, ix1, iy1);

        int span = ix1 - ix0 + 80;
        for (int k = 0; k < 3; k++) {
            int cw = 30 + 10 * k;
            int cx = ix0 - 40 + Math.floorMod(k * 131 + ticks / (3 + k), span);
            int cy = iy0 + 10 + k * Math.max(6, (iy1 - iy0) / 7);
            clipped(cx, cy, cx + cw, cy + 5, CLOUD, ix0, iy0, ix1, iy1);
            clipped(cx + 6, cy - 3, cx + cw - 10, cy, CLOUD, ix0, iy0, ix1, iy1);
            clipped(cx + 2, cy + 5, cx + cw - 2, cy + 7, CLOUD_SHADE, ix0, iy0, ix1, iy1);
        }
    }

    private void clipped(int x0, int y0, int x1, int y1, int colour, int bx0, int by0, int bx1, int by1) {
        int a = Math.max(x0, bx0);
        int b = Math.max(y0, by0);
        int e = Math.min(x1, bx1);
        int f = Math.min(y1, by1);
        if (e > a && f > b) c.fill(a, b, e, f, colour);
    }

    private void grass(int x0, int y0, int x1, int y1) {
        c.fill(x0, y0, x1, y1, GRASS);
        for (int gy = y0; gy < y1; gy += 6) {
            for (int gx = x0; gx < x1; gx += 8) {
                int hsh = hash(gx - x0, gy - y0);
                if ((hsh & 7) < 4) clipped(gx + (hsh >>> 3 & 5), gy + (hsh >>> 6 & 3), gx + (hsh >>> 3 & 5) + 2,
                        gy + (hsh >>> 6 & 3) + 2, GRASS_DARK, x0, y0, x1, y1);
                if ((hsh >>> 9 & 63) == 0) clipped(gx + 3, gy + 2, gx + 4, gy + 3, (hsh & 1) == 0 ? YELLOW : BAD, x0, y0, x1, y1);
            }
        }
    }

    private void ground(int x0, int gy, int x1, int y1) {
        if (gy >= y1) return;
        c.fill(x0, gy, x1, y1, EARTH);
        c.fill(x0, gy, x1, Math.min(y1, gy + 2), GRASS);
        if (gy + 2 < y1) {
            for (int gx = x0; gx < x1; gx += 3) {
                if ((hash(gx, 7) & 3) == 0) c.fill(gx, gy + 2, gx + 1, Math.min(y1, gy + 3), GRASS_DARK);
            }
        }
        for (int yy = gy + 4; yy < y1; yy += 4) {
            for (int gx = x0; gx < x1; gx += 5) {
                int hsh = hash(gx, yy);
                if ((hsh & 3) == 0) c.fill(gx + (hsh >>> 2 & 3), yy, Math.min(x1, gx + (hsh >>> 2 & 3) + 2), Math.min(y1, yy + 1), EARTH_DARK);
            }
        }
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    private final List<int[]> tags = new ArrayList<>();

    private boolean tag(String text, int x, int y, int colour, int bx0, int by0, int bx1, int by1) {
        int tw = host.width(text) + 4;
        if (x < bx0 + 1 || y < by0 + 1 || x + tw > bx1 - 1 || y + 10 > by1 - 1) return false;
        for (int[] b : tags) if (x < b[2] && b[0] < x + tw && y < b[3] && b[1] < y + 10) return false;
        nameplate(text, x, y, colour);
        tags.add(new int[] {x, y, x + tw, y + 10});
        return true;
    }

    private void sideView(int x0, int y0, int x1, int y1, SideMarks marks) {
        if (y1 - y0 < 30) return;
        pictureBox(x0, y0, x1, y1, true);
        AerialView.Side s = v.side();
        AerialView.Flight f = v.flight();
        tag(tr("side"), x0 + 4, y0 + 4, TEXT, x0, y0, x1, y1);
        if (s.cols() <= 0 || s.rows() <= 0) {
            String none = fit(tr("side.none"), x1 - x0 - 16);
            tag(none, (x0 + x1) / 2 - host.width(none) / 2 - 2, (y0 + y1) / 2 - 5, TEXT, x0, y0, x1, y1);
            return;
        }
        if (Float.isFinite(f.windSpeed())) {
            String wind = f.windSpeed() < 0.3f ? tr("wind.calm") : tr("wind", num1(f.windSpeed()), heading3(f.windFrom()));
            tag(wind, x1 - 5 - host.width(wind) - 4, y0 + 4, TEXT, x0, y0, x1, y1);
        }
        double pitch = Double.isFinite(f.pitch()) ? clamp(f.pitch(), -PITCH_DRAWN, PITCH_DRAWN) : 0.0;
        ShipFrame fr = frame(s, x0 + 8, y0 + 16, x1 - 8, y1 - 16, false, pitch);
        double ppb = fr.pxPerBlock();
        boolean known = Float.isFinite(f.altitude());

        double gy = known && Float.isFinite(f.ground()) ? fr.comY() + (f.altitude() - f.ground()) * ppb : Double.NaN;
        boolean groundInView = Double.isFinite(gy) && gy < y1 - 3;
        if (groundInView) ground(x0 + 1, (int) Math.max(y0 + 1, Math.round(gy)), x1 - 1, y1 - 1);
        if (marks == SideMarks.FORCES) {
            streaks(s, fr, x0, y0, x1, y1, groundInView ? (int) Math.round(gy) : y1);
            if (known) heightLines(fr, f, x0, y0, x1, y1, false);
        }
        drawShip(s, fr, false, marks == SideMarks.FORCES);
        if (marks == SideMarks.FORCES) {
            forces(s, fr, x0, y0, x1, y1);
            if (known) heightLines(fr, f, x0, y0, x1, y1, true);
            if (groundInView) clearance(s, fr, f, gy, x0, y0, x1, y1);
        } else {
            finMarks(fr, x0, y0, x1, y1);
        }
        if (!groundInView && Float.isFinite(f.keel())) {
            String below = tr("side.ground", num0(f.keel()));
            tag(below, x1 - 5 - host.width(below) - 4, y1 - 14, TEXT, x0, y0, x1, y1);
        }

        tag(tr("attitude", signed1(f.pitch()), signed1(f.roll())), x0 + 4, y1 - 14, TEXT, x0, y0, x1, y1);
    }

    private void streaks(AerialView.Side s, ShipFrame fr, int x0, int y0, int x1, int y1, int floor) {
        double speed = v.flight().airForward();
        if (!Float.isFinite(v.flight().airForward()) || Math.abs(speed) < 0.3) return;
        double top = y0 + 20;
        double bottom = Math.min(floor, y1) - 6;
        if (bottom - top < 6) return;
        int width = x1 - x0 - 8;
        int len = (int) Math.round(Math.min(20.0, 5.0 + 2.0 * Math.abs(speed)));
        for (int k = 0; k < 10; k++) {
            double y = Math.round(top + (bottom - top) * ((k * 0.618034 + 0.13) % 1.0));
            int travel = (int) Math.round(ticks * Math.min(6.0, 0.6 + Math.abs(speed) * 0.5)) + k * 53;
            int px = Math.floorMod(speed > 0 ? -travel : travel, Math.max(1, width - len));
            double sx = x0 + 4 + px;
            rectQuad(sx, y, sx + len, y + 1, 0xB0FFFFFF);
        }
    }

    private void heightLines(ShipFrame fr, AerialView.Flight f, int x0, int y0, int x1, int y1, boolean labels) {
        double ppb = fr.pxPerBlock();
        if (Float.isFinite(f.ceiling())) {
            double cy = fr.comY() + (f.altitude() - f.ceiling()) * ppb;
            if (cy > y0 + 16 && cy < y1 - 16) {
                String lab = tr("side.ceiling", num0(f.ceiling()));
                if (labels) tag(lab, x1 - 6 - host.width(lab) - 4, (int) Math.round(cy) - 11, BAD, x0, y0, x1, y1);
                else for (int x = x0 + 2; x < x1 - 2; x += 6) rectQuad(x, cy, Math.min(x1 - 2, x + 3), cy + 1, BAD);
            }
        }
        if (Float.isFinite(f.targetAltitude()) && Math.abs(f.targetAltitude() - f.altitude()) >= 0.5f) {
            double hy = fr.comY() + (f.altitude() - f.targetAltitude()) * ppb;
            if (hy > y0 + 16 && hy < y1 - 16) {
                String lab = tr("side.hold", num0(f.targetAltitude()));
                if (labels) tag(lab, x1 - 6 - host.width(lab) - 4, (int) Math.round(hy) + 2, YELLOW, x0, y0, x1, y1);
                else for (int x = x0 + 2; x < x1 - 2; x += 6) rectQuad(x, hy, Math.min(x1 - 2, x + 3), hy + 1, YELLOW);
            }
        }
    }

    private void clearance(AerialView.Side s, ShipFrame fr, AerialView.Flight f, double gy, int x0, int y0, int x1, int y1) {
        if (!Float.isFinite(f.keel()) || f.keel() < 1.0f) return;
        double ky = gy - f.keel() * fr.pxPerBlock();
        double x = fr.ox() - 6;
        if (x < x0 + 8 || ky < y0 + 4 || gy - ky < 10) return;
        rectQuad(x, ky, x + 1, gy, TEXT);
        rectQuad(x - 2, ky, x + 3, ky + 1, TEXT);
        rectQuad(x - 2, gy - 1, x + 3, gy, TEXT);
        String lab = tr("side.clearance", num0(f.keel()));
        int ly = (int) Math.round((ky + gy) / 2.0) - 5;
        if (!tag(lab, (int) x + 4, ly, TEXT, x0, y0, x1, y1)) tag(lab, (int) x - host.width(lab) - 8, ly, TEXT, x0, y0, x1, y1);
    }

    private ShipFrame frame(AerialView.Side s, int bx0, int by0, int bx1, int by1, boolean fromAbove, double pitch) {
        int rows = fromAbove ? s.topRows() : s.rows();
        double iw = bx1 - bx0;
        double ih = by1 - by0;
        double tilt = Math.abs(Math.sin(Math.toRadians(pitch)));
        double pxw = iw / (s.cols() + rows * tilt);
        double pxh = ih / (rows + s.cols() * tilt);
        double px = Math.max(0.5, Math.min(pxw, pxh));
        double dw = s.cols() * px;
        double dh = rows * px;
        double ox = bx0 + (iw - dw) / 2.0;
        double oy = by0 + (ih - dh) / 2.0;
        double originAcross = fromAbove ? s.originLat() : s.originUp();
        double comX = ox + (0.0 - s.originLon()) / s.cell() * px;
        double comY = fromAbove ? oy + (0.0 - originAcross) / s.cell() * px : oy + dh - (0.0 - originAcross) / s.cell() * px;
        double a = Math.toRadians(fromAbove ? 0.0 : pitch);
        return new ShipFrame(ox, oy, px, rows, fromAbove, s.originLon(), originAcross, s.cell(), comX, comY, Math.cos(a), Math.sin(a));
    }

    private static int cellKind(AerialView.Side s, boolean fromAbove, int col, int row) {
        boolean set = fromAbove ? s.topAt(col, row) : s.at(col, row);
        if (!set) return 0;
        boolean env = fromAbove ? s.topEnv(col, row) : s.env(col, row);
        return env ? 2 : 1;
    }

    private void runQuad(ShipFrame fr, int row, double a, double b, double grow, double yFrom, double yTo, int colour) {
        double xa = fr.ox() + a * fr.px() - grow;
        double xb = fr.ox() + b * fr.px() + grow;
        double ya;
        double yb;
        if (fr.fromAbove()) {
            ya = fr.oy() + (row + yFrom) * fr.px() - grow;
            yb = fr.oy() + (row + yTo) * fr.px() + grow;
        } else {
            ya = fr.oy() + (fr.rows() - row - 1 + yFrom) * fr.px() - grow;
            yb = fr.oy() + (fr.rows() - row - 1 + yTo) * fr.px() + grow;
        }
        double[] p0 = fr.turn(xa, ya);
        double[] p1 = fr.turn(xb, ya);
        double[] p2 = fr.turn(xb, yb);
        double[] p3 = fr.turn(xa, yb);
        quadCw(p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], p3[0], p3[1], colour);
    }

    private void drawShip(AerialView.Side s, ShipFrame fr, boolean fromAbove, boolean gas) {
        int rows = fromAbove ? s.topRows() : s.rows();
        int cols = s.cols();
        int envLo = rows;
        int envHi = -1;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (cellKind(s, fromAbove, col, row) == 2) {
                    envLo = Math.min(envLo, row);
                    envHi = Math.max(envHi, row);
                    break;
                }
            }
        }
        double grow = Math.max(0.6, Math.min(1.2, fr.px() * 0.35));

        for (int row = 0; row < rows; row++) {
            int col = 0;
            while (col < cols) {
                if (cellKind(s, fromAbove, col, row) == 0) {
                    col++;
                    continue;
                }
                int start = col;
                while (col < cols && cellKind(s, fromAbove, col, row) != 0) col++;
                runQuad(fr, row, start, col, grow, 0.0, 1.0, EDGE);
            }
        }

        for (int row = 0; row < rows; row++) {
            int col = 0;
            while (col < cols) {
                int kind = cellKind(s, fromAbove, col, row);
                if (kind == 0) {
                    col++;
                    continue;
                }
                int start = col;
                while (col < cols && cellKind(s, fromAbove, col, row) == kind) col++;
                int colour = kind == 2 ? fabric(row, envLo, envHi, fromAbove) : HULL;

                runQuad(fr, row, start, col, 0.35, 0.0, 1.0, colour);
            }
        }

        double lip = Math.max(0.2, Math.min(0.45, 1.2 / Math.max(1.0, fr.px())));
        for (int row = 0; row < rows; row++) {
            int col = 0;
            int onTop = fromAbove ? row - 1 : row + 1;
            while (col < cols) {
                boolean edge = cellKind(s, fromAbove, col, row) == 1 && cellKind(s, fromAbove, col, onTop) == 0;
                if (!edge) {
                    col++;
                    continue;
                }
                int start = col;
                while (col < cols && cellKind(s, fromAbove, col, row) == 1 && cellKind(s, fromAbove, col, onTop) == 0) col++;
                if (fromAbove) runQuad(fr, row, start, col, 0.0, 0.0, lip, HULL_LIGHT);
                else runQuad(fr, row, start, col, 0.0, 0.0, lip, HULL_LIGHT);
            }
        }

        boolean blocks = fr.px() >= 4.0;
        if (fr.px() >= 5.0) {
            int joint = alpha(HULL_DARK, 0x9A);
            for (int row = 0; row < rows; row++) {
                for (int col = 1; col < cols; col++) {
                    if (cellKind(s, fromAbove, col, row) == 1 && cellKind(s, fromAbove, col - 1, row) == 1) {
                        runQuad(fr, row, col - 0.06, col + 0.06, 0.0, 0.0, 1.0, joint);
                    }
                }
                if (row == 0) continue;
                int col = 0;
                while (col < cols) {
                    if (cellKind(s, fromAbove, col, row) != 1 || cellKind(s, fromAbove, col, row - 1) != 1) {
                        col++;
                        continue;
                    }
                    int start = col;
                    while (col < cols && cellKind(s, fromAbove, col, row) == 1 && cellKind(s, fromAbove, col, row - 1) == 1) col++;
                    runQuad(fr, row, start, col, 0.0, fromAbove ? -0.06 : 0.94, fromAbove ? 0.06 : 1.06, joint);
                }
            }
        }
        if (envHi < 0) return;

        int every = blocks ? 1 : Math.max(3, (int) Math.ceil(6.0 / Math.max(0.5, fr.px())));
        if (blocks) {
            for (int row = 1; row < rows; row++) {
                int col = 0;
                while (col < cols) {
                    if (cellKind(s, fromAbove, col, row) != 2 || cellKind(s, fromAbove, col, row - 1) != 2) {
                        col++;
                        continue;
                    }
                    int start = col;
                    while (col < cols && cellKind(s, fromAbove, col, row) == 2 && cellKind(s, fromAbove, col, row - 1) == 2) col++;

                    runQuad(fr, row, start, col, 0.0, fromAbove ? -0.05 : 0.95, fromAbove ? 0.05 : 1.05, SEAM);
                }
            }
        }
        for (int col = every; col < cols; col += every) {
            int row = 0;
            while (row < rows) {
                if (cellKind(s, fromAbove, col, row) != 2) {
                    row++;
                    continue;
                }
                int start = row;
                while (row < rows && cellKind(s, fromAbove, col, row) == 2) row++;
                seam(fr, col, start, row);
            }
        }
        if (!gas) return;
        double full = gasShare();
        if (!(full > 0.005)) return;

        int lit = (int) Math.round((envHi - envLo + 1) * Math.min(1.0, full));
        int base = steamy() ? STEAM : HOT_AIR;
        for (int row = envHi; row > envHi - lit && row >= envLo; row--) {
            double depth = lit <= 1 ? 0.0 : (envHi - row) / (double) (lit - 1);
            int glow = alpha(base, (int) Math.round((steamy() ? 0x50 : 0x46) * (1.0 - 0.6 * depth)));
            int col = 0;
            while (col < cols) {
                if (cellKind(s, fromAbove, col, row) != 2) {
                    col++;
                    continue;
                }
                int start = col;
                while (col < cols && cellKind(s, fromAbove, col, row) == 2) col++;
                runQuad(fr, row, start, col, 0.35, 0.0, 1.0, glow);
            }
        }
    }

    private static int fabric(int row, int lo, int hi, boolean fromAbove) {
        double span = Math.max(1.0, hi - lo);
        double t = (row - lo) / span;
        double light;
        if (fromAbove) light = 1.0 - Math.abs(t - 0.5) * 2.0;
        else light = t;
        light = Math.pow(Math.max(0.0, Math.min(1.0, light)), 0.8);
        return mix(FABRIC_DARK, FABRIC_LIGHT, 0.15 + 0.85 * light);
    }

    private void seam(ShipFrame fr, int col, int rowFrom, int rowTo) {
        double x = fr.ox() + col * fr.px();
        double ya;
        double yb;
        if (fr.fromAbove()) {
            ya = fr.oy() + rowFrom * fr.px();
            yb = fr.oy() + rowTo * fr.px();
        } else {
            ya = fr.oy() + (fr.rows() - rowTo) * fr.px();
            yb = fr.oy() + (fr.rows() - rowFrom) * fr.px();
        }
        double[] p0 = fr.turn(x, ya);
        double[] p1 = fr.turn(x, yb);
        line(p0[0], p0[1], p1[0], p1[1], 1.0, SEAM);
    }

    private double gasShare() {
        double gas = 0.0;
        double cap = 0.0;
        for (AerialView.Balloon b : v.balloons()) {
            if (!(b.capacity() > 0f)) continue;
            gas += Math.max(0.0, b.gas());
            cap += b.capacity();
        }
        return cap > 0.0 ? gas / cap : 0.0;
    }

    private boolean steamy() {
        double steam = 0.0;
        double all = 0.0;
        for (AerialView.Balloon b : v.balloons()) {
            all += Math.max(0.0, b.gas());
            if ("steam".equals(b.kind())) steam += Math.max(0.0, b.gas());
        }
        return all > 0.0 && steam > all / 2.0;
    }

    private void forces(AerialView.Side s, ShipFrame fr, int x0, int y0, int x1, int y1) {
        AerialView.Flight f = v.flight();
        double box0x = x0 + 4;
        double box0y = y0 + 4;
        double box1x = x1 - 4;
        double box1y = y1 - 4;

        for (int i = 0; i < v.balloons().size(); i++) {
            AerialView.Balloon b = v.balloons().get(i);
            double under = envelopeBottom(s, b.lon());
            double[] at = fr.at(b.lon(), Double.isFinite(under) ? under - 0.4 : b.up() - 2.0);
            if (!inBox(at, box0x, box0y, box1x, box1y)) continue;
            if (b.lit() > 0) flame(at[0], at[1], b.heaters() > 0 ? b.lit() / (double) b.heaters() : 1.0, i, fr, false);
            else square(at[0], at[1], 1.2, TEXT_FAINT);
            List<String> tip = new ArrayList<>();
            tip.add(tr("side.balloon", "B" + (i + 1), tr("kind." + b.kind()), num0(b.gas()), num0(b.capacity()), pct(b.lift())));
            tip.add(tr("side.burners", b.lit(), b.heaters()));
            if (!b.driven()) tip.add(tr("side.balloon.hand"));
            hit((int) at[0] - 4, (int) at[1] - 6, 9, 10, null, null, tip);
        }
        for (int i = 0; i < v.engines().size(); i++) {
            AerialView.Engine e = v.engines().get(i);
            double[] at = fr.at(e.lon(), e.up());
            if (!inBox(at, box0x, box0y, box1x, box1y)) continue;
            engineGlyph(fr, at, e, i, false);
        }

        double weight = 1.0 + load();
        double liftNow = 0.0;
        double liftLon = 0.0;
        double liftUp = 0.0;
        for (AerialView.Balloon b : v.balloons()) {
            double l = Math.max(0.0, b.lift());
            liftNow += l;
            liftLon += l * b.lon();
            liftUp += l * b.up();
        }
        double[] com = fr.at(0.0, 0.0);
        double base = Math.max(10.0, Math.min(26.0, (y1 - y0) * 0.2));

        int cy = y0 + 16;
        if (v.staff() != AerialView.STAFF_NONE && tag(tr(v.staff() == AerialView.STAFF_LOCKED ? "side.staff_locked" : "side.staff"),
                x0 + 4, cy, WARN, x0, y0, x1, y1)) {
            cy += 12;
        }

        if (v.mode() == MODE_GO_TO || v.leave() == LeaveMode.GO_TO.ordinal()) {
            AerialView.Nav nav = v.nav();
            List<String> tip = new ArrayList<>();
            String say;
            if (nav.found()) {
                say = tr("goto.tag", tr(nav.source() == AerialView.NAV_LODESTONE ? "goto.lodestone" : "goto.table"), num0(nav.distance()));
                tip.add(tr("goto.line", tr(nav.source() == AerialView.NAV_LODESTONE ? "goto.lodestone" : "goto.table"),
                        num0(nav.distance()), heading3(nav.bearing())));
                tip.add(tr("goto.at", num0(nav.x()), num0(nav.y()), num0(nav.z())));
                if (nav.tableElsewhere()) tip.add(tr("goto.table_elsewhere"));
            } else {
                say = tr("goto.none");
                if (!nav.problem().isEmpty()) tip.add(nav.problem());
            }
            if (tag(say, x0 + 4, cy, nav.found() ? INFO : WARN, x0, y0, x1, y1)) {
                if (!tip.isEmpty()) hit(x0 + 4, cy, host.width(say) + 4, 10, null, null, tip);
                cy += 12;
            }
        }
        if (f.weighing() && tag(tr("side.weighing"), x0 + 4, cy, WARN, x0, y0, x1, y1)) cy += 12;
        if (load() > 0.02 && Float.isFinite(v.mass())) tag(tr("side.load", big(v.mass() * load())), x0 + 4, cy, WARN, x0, y0, x1, y1);
        if (liftNow > 1.0e-3) {
            double[] col = fr.at(liftLon / liftNow, liftUp / liftNow);
            double len = base * Math.min(2.0, liftNow / weight);
            double[] up = fr.dir(0.0, 1.0);
            if (inBox(col, box0x, box0y, box1x, box1y)) {
                double tx = col[0] + up[0] * len;
                double ty = col[1] + up[1] * len;
                outlinedArrow(col[0], col[1], tx, ty, 2.0, 4.0, YELLOW);
                hit((int) col[0] - 4, (int) (col[1] - len), 9, (int) len + 4, null, null, List.of(tr("side.lift", pct(liftNow))));
                String lab = tr("side.lift.tag", pct(liftNow));
                if (!tag(lab, (int) tx + 5, (int) ty - 5, YELLOW, x0, y0, x1, y1)) {
                    tag(lab, (int) tx - host.width(lab) - 9, (int) ty - 5, YELLOW, x0, y0, x1, y1);
                }
            }
        }
        if (inBox(com, box0x, box0y, box1x, box1y)) {
            outlinedArrow(com[0], com[1] + 4, com[0], com[1] + 4 + base, 2.0, 4.0, 0xFFE8E8E8);
            centreOfMass(com[0], com[1]);
            List<String> tip = new ArrayList<>();
            tip.add(tr("side.com"));
            tip.add(tr("side.weight", big(v.mass() * (1.0 + load())), load() > 0.005 ? tr("side.weight.load", big(v.mass() * load())) : ""));
            hit((int) com[0] - 4, (int) com[1] - 4, 9, (int) base + 10, null, null, tip);
            String lab = tr("side.weight.tag", big(v.mass() * (1.0 + load())));
            int ly = (int) (com[1] + 4 + base) - 6;
            if (!tag(lab, (int) com[0] + 6, ly, TEXT, x0, y0, x1, y1)) tag(lab, (int) com[0] - host.width(lab) - 10, ly, TEXT, x0, y0, x1, y1);
        }
    }

    private void outlinedArrow(double x0, double y0, double x1, double y1, double width, double head, int colour) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.hypot(dx, dy);
        if (len < 1.0e-6) return;
        double ux = dx / len;
        double uy = dy / len;
        arrow(x0 - ux, y0 - uy, x1 + ux * 1.5, y1 + uy * 1.5, width + 2.0, head + 1.8, EDGE);
        arrow(x0, y0, x1, y1, width, head, colour);
    }

    private double load() {
        float hidden = v.learned().hidden();
        double weight = v.mass() * v.gravity();
        if (!Float.isFinite(hidden) || !(weight > 0.0)) return 0.0;
        return Math.max(0.0, -hidden / weight);
    }

    private static double envelopeBottom(AerialView.Side s, double lon) {
        int col = (int) Math.floor((lon - s.originLon()) / s.cell());
        if (col < 0 || col >= s.cols()) return Double.NaN;
        for (int row = 0; row < s.rows(); row++) {
            if (s.env(col, row)) return s.originUp() + row * s.cell();
        }
        return Double.NaN;
    }

    private static boolean inBox(double[] p, double x0, double y0, double x1, double y1) {
        return p[0] >= x0 && p[0] <= x1 && p[1] >= y0 && p[1] <= y1;
    }

    private static double glyphScale(ShipFrame fr) {
        return Math.max(1.0, Math.min(1.7, fr.pxPerBlock() / 3.5));
    }

    private void flame(double x, double y, double strength, int seed, ShipFrame fr, boolean jet) {
        double g = glyphScale(fr);
        double s = (0.5 + 0.5 * Math.max(0.0, Math.min(1.0, strength))) * g;
        double flick = ((ticks + seed * 5) % 6) / 6.0;
        double hgt = (5.0 + 2.0 * flick) * s;
        double wid = 2.2 * s + 0.6;
        double[] up = fr.dir(0.0, 1.0);
        double[] side = {-up[1], up[0]};
        double tipX = x + up[0] * hgt;
        double tipY = y + up[1] * hgt;
        triangle((float) (x - side[0] * wid), (float) (y - side[1] * wid), (float) (x + side[0] * wid), (float) (y + side[1] * wid),
                (float) tipX, (float) tipY, FIRE);
        triangle((float) (x - side[0] * wid * 0.5), (float) (y - side[1] * wid * 0.5), (float) (x + side[0] * wid * 0.5),
                (float) (y + side[1] * wid * 0.5), (float) (x + up[0] * hgt * 0.6), (float) (y + up[1] * hgt * 0.6), FIRE_CORE);

        double rise = ((ticks + seed * 7) % 12) / 12.0;
        square(x + side[0] * (rise - 0.5) * wid + up[0] * (hgt + 2.0 + 5.0 * rise * g), y + up[1] * (hgt + 2.0 + 5.0 * rise * g),
                0.6 * g, alpha(FIRE_CORE, (int) (220 * (1.0 - rise))));
    }

    static final int BLADE = 0xFF9E6947;
    static final int IRON = 0xFF6B6B6B;

    private void engineGlyph(ShipFrame fr, double[] at, AerialView.Engine e, int index, boolean picked) {
        boolean trouble = engineTrouble(e);
        boolean off = e.state() == AerialView.ENGINE_OFF;
        double push = Math.abs(e.push());
        int dir = e.push() < 0f ? (e.dir() ^ 1) : e.dir();
        double[] d = pushDir(fr, dir);
        double g = glyphScale(fr);
        if (picked) ring(at[0], at[1], 6.0 * g, 6.0 * g + 1.5, YELLOW);
        if (e.thruster()) {
            double nx = -d[0];
            double ny = -d[1];
            double px = -ny * g;
            double py = nx * g;
            int iron = trouble ? mix(BAD, IRON, 0.35) : off ? mix(IRON, SLATE, 0.5) : IRON;
            if (d[0] == 0 && d[1] == 0) {
                disc(at[0], at[1], 3.4 * g, EDGE);
                disc(at[0], at[1], 3.4 * g - 0.8, iron);
                disc(at[0], at[1], 1.2 * g, BRASS);
            } else {
                double mouth = 4.4 * g;
                quadCw(at[0] + px * 1.9, at[1] + py * 1.9, at[0] - px * 1.9, at[1] - py * 1.9, at[0] - px * 2.9 + nx * mouth,
                        at[1] - py * 2.9 + ny * mouth, at[0] + px * 2.9 + nx * mouth, at[1] + py * 2.9 + ny * mouth, EDGE);
                quadCw(at[0] + px * 1.2, at[1] + py * 1.2, at[0] - px * 1.2, at[1] - py * 1.2, at[0] - px * 2.1 + nx * (mouth - 0.8),
                        at[1] - py * 2.1 + ny * (mouth - 0.8), at[0] + px * 2.1 + nx * (mouth - 0.8), at[1] + py * 2.1 + ny * (mouth - 0.8),
                        iron);
                line(at[0] + px * 1.5 + nx * 0.8 * g, at[1] + py * 1.5 + ny * 0.8 * g, at[0] - px * 1.5 + nx * 0.8 * g,
                        at[1] - py * 1.5 + ny * 0.8 * g, g, BRASS);
                if (push > 0.03 && !trouble) {
                    double flick = ((ticks + index * 3) % 5) / 5.0;
                    double len = (4.0 + 10.0 * push + 2.0 * flick) * g;
                    double bx = at[0] + nx * mouth;
                    double by = at[1] + ny * mouth;

                    for (int k = 0; k < 3; k++) {
                        double t = ((ticks + index * 5 + k * 7) % 21) / 21.0;
                        double dist = len + (2.0 + t * 12.0) * g;
                        square(bx + nx * dist + px * (k - 1) * 1.5 * t, by + ny * dist + py * (k - 1) * 1.5 * t, (1.0 + 1.6 * t) * g,
                                alpha(SMOKE, (int) (112 * (1.0 - t))));
                    }
                    triangle((float) (bx + px * 2.2), (float) (by + py * 2.2), (float) (bx - px * 2.2), (float) (by - py * 2.2),
                            (float) (bx + nx * len), (float) (by + ny * len), FIRE);
                    triangle((float) (bx + px * 1.1), (float) (by + py * 1.1), (float) (bx - px * 1.1), (float) (by - py * 1.1),
                            (float) (bx + nx * len * 0.6), (float) (by + ny * len * 0.6), FIRE_CORE);
                }
            }
        } else {
            double bxp = -d[1];
            double byp = d[0];
            boolean across = d[0] == 0 && d[1] == 0;
            int wood = trouble ? mix(BAD, BLADE, 0.35) : off ? mix(BLADE, SLATE, 0.5) : BLADE;
            double blade = 5.0 * g;
            if (across) {
                ring(at[0], at[1], 3.0 * g, 3.0 * g + 1.6, EDGE);
                ring(at[0], at[1], 3.0 * g + 0.4, 3.0 * g + 1.2, wood);
            } else {
                boolean spin = push > 0.03 && !trouble && !off;
                double reach = spin ? blade * Math.abs(Math.cos(ticks * 0.9 + index * 1.7)) : blade;
                if (spin) line(at[0] - bxp * blade, at[1] - byp * blade, at[0] + bxp * blade, at[1] + byp * blade, 2.6 * g, 0x40FFFFFF);
                line(at[0] - bxp * (reach + 0.6), at[1] - byp * (reach + 0.6), at[0] + bxp * (reach + 0.6),
                        at[1] + byp * (reach + 0.6), 1.4 * g + 1.0, EDGE);
                line(at[0] - bxp * reach, at[1] - byp * reach, at[0] + bxp * reach, at[1] + byp * reach, 1.4 * g, wood);
            }
            square(at[0], at[1], 1.2 * g + 0.7, EDGE);
            square(at[0], at[1], 1.2 * g, trouble ? BAD : BRASS);
        }
        boolean acrossPush = d[0] == 0 && d[1] == 0;
        if (acrossPush) {
            boolean toward = dir == AerialView.DIR_RIGHT && !fr.fromAbove() || dir == AerialView.DIR_UP && fr.fromAbove();
            if (toward) square(at[0], at[1], 0.9 * g, EDGE);
            else {
                double arm = 1.8 * g;
                line(at[0] - arm, at[1] - arm, at[0] + arm, at[1] + arm, 0.9 * g, EDGE);
                line(at[0] - arm, at[1] + arm, at[0] + arm, at[1] - arm, 0.9 * g, EDGE);
            }
        } else if (push > 0.03 && !off) {
            double len = 5.0 + 11.0 * Math.min(1.0, push);
            double sx = at[0] + d[0] * 5.5 * g;
            double sy = at[1] + d[1] * 5.5 * g;
            outlinedArrow(sx, sy, sx + d[0] * len, sy + d[1] * len, 1.4, 3.5, trouble ? BAD : GOOD);
        }
        List<String> tip = new ArrayList<>();
        tip.add(tr("side.engine", engineName(index), engineNow(e)));
        final long pos = e.pos();
        int reachBox = (int) Math.ceil(5.0 * g);
        hit((int) at[0] - reachBox, (int) at[1] - reachBox, 2 * reachBox + 1, 2 * reachBox + 1, () -> {
            pickedEngine = pos;
            if (page == Page.FLIGHT) setPage(Page.ENGINES);
        }, null, tip);
    }

    private static double[] pushDir(ShipFrame fr, int dir) {
        return switch (dir) {
            case AerialView.DIR_AHEAD -> fr.dir(1.0, 0.0);
            case AerialView.DIR_ASTERN -> fr.dir(-1.0, 0.0);
            case AerialView.DIR_UP -> fr.fromAbove() ? new double[] {0.0, 0.0} : fr.dir(0.0, 1.0);
            case AerialView.DIR_DOWN -> fr.fromAbove() ? new double[] {0.0, 0.0} : fr.dir(0.0, -1.0);
            case AerialView.DIR_RIGHT -> fr.fromAbove() ? fr.dir(0.0, 1.0) : new double[] {0.0, 0.0};
            default -> fr.fromAbove() ? fr.dir(0.0, -1.0) : new double[] {0.0, 0.0};
        };
    }

    private void centreOfMass(double x, double y) {
        disc(x, y, 4.4, EDGE);
        disc(x, y, 3.5, 0xFFFFFFFF);
        for (int q = 0; q < 2; q++) {
            double a0 = q * Math.PI;
            for (int k = 0; k < 3; k++) {
                double t0 = a0 + k * Math.PI / 6;
                double t1 = a0 + (k + 1) * Math.PI / 6;
                triangle((float) x, (float) y, (float) (x + 3.5 * Math.cos(t0)), (float) (y + 3.5 * Math.sin(t0)),
                        (float) (x + 3.5 * Math.cos(t1)), (float) (y + 3.5 * Math.sin(t1)), EDGE);
            }
        }
    }

    private void finMarks(ShipFrame fr, int x0, int y0, int x1, int y1) {
        for (int i = 0; i < v.surfaces().size(); i++) {
            AerialView.Surface sf = v.surfaces().get(i);
            double[] at = fr.at(sf.lon(), finUp(sf));
            if (!inBox(at, x0 + 6, y0 + 6, x1 - 6, y1 - 6)) continue;
            AerialSetup.SurfaceRole role = AerialSetup.SurfaceRole.byOrdinal(sf.role());
            boolean bad = role != AerialSetup.SurfaceRole.OFF && (!sf.assembled() || !sf.locked());
            boolean picked = sf.pos() == pickedFin;
            int colour = bad ? BAD : role == AerialSetup.SurfaceRole.OFF ? TEXT_FAINT : BRASS;
            if (picked) ring(at[0], at[1], 6.0, 7.5, YELLOW);

            double ang = Math.toRadians(Double.isFinite(sf.angle()) ? clamp(sf.angle(), -60.0, 60.0) : 0.0);
            double[] aft = fr.dir(-Math.cos(ang), Math.sin(ang) * (sf.hinge() == 1 ? 1.0 : 0.35));
            line(at[0], at[1], at[0] + aft[0] * 9.0, at[1] + aft[1] * 9.0, 2.2, EDGE);
            line(at[0], at[1], at[0] + aft[0] * 8.0, at[1] + aft[1] * 8.0, 1.4, colour);
            disc(at[0], at[1], 2.4, EDGE);
            disc(at[0], at[1], 1.6, colour);
            final long pos = sf.pos();
            hit((int) at[0] - 5, (int) at[1] - 5, 11, 11, () -> pickedFin = pos, null, List.of(finName(i) + ": " + finAt(sf)));
        }
    }

    private double finUp(AerialView.Surface sf) {
        if (Float.isFinite(sf.up())) return sf.up();
        AerialView.Side s = v.side();
        return s.originUp() + s.rows() * s.cell() * 0.5;
    }

    private ShipFrame topView(int x0, int y0, int x1, int y1, String caption) {
        pictureBox(x0, y0, x1, y1, false);
        tag(caption, x0 + 4, y0 + 4, TEXT, x0, y0, x1, y1);
        AerialView.Side s = v.side();
        if (s.cols() <= 0 || s.topRows() <= 0) {
            String none = fit(tr("side.none"), x1 - x0 - 16);
            tag(none, (x0 + x1) / 2 - host.width(none) / 2 - 2, (y0 + y1) / 2 - 5, TEXT, x0, y0, x1, y1);
            return null;
        }
        ShipFrame fr = frame(s, x0 + 10, y0 + 16, x1 - 18, y1 - 10, true, 0.0);

        double off = Math.max(2.0, Math.min(6.0, fr.px() * 0.8));
        ShipFrame sh = new ShipFrame(fr.ox() + off, fr.oy() + off, fr.px(), fr.rows(), true, fr.originLon(), fr.originAcross(), fr.cell(),
                fr.comX() + off, fr.comY() + off, 1.0, 0.0);
        for (int row = 0; row < s.topRows(); row++) {
            int col = 0;
            while (col < s.cols()) {
                if (cellKind(s, true, col, row) == 0) {
                    col++;
                    continue;
                }
                int start = col;
                while (col < s.cols() && cellKind(s, true, col, row) != 0) col++;
                runQuad(sh, row, start, col, 0.0, 0.0, 1.0, 0x48000000);
            }
        }
        drawShip(s, fr, true, false);

        double ay = fr.comY();
        double ax = fr.ox() + s.cols() * fr.px() + 3;
        if (ax + 12 < x1) {
            outlinedArrow(ax, ay, ax + 10, ay, 2.0, 4.0, YELLOW);
            hit((int) ax - 2, (int) ay - 6, 16, 12, null, null, List.of(tr("top.nose")));
        }
        return fr;
    }

    private void notesRow(int x0, int y, int x1) {
        List<String> notes = v.notes();
        if (notes.isEmpty()) {
            textFit(v.pilot().isEmpty() && v.onCraft() ? sitHint() : keysHint(), x0, y + 2, x1 - x0, TEXT_FAINT);
            return;
        }
        String more = notes.size() > 1 ? tr("notes.more", notes.size() - 1) : "";
        int moreW = more.isEmpty() ? 0 : host.width(more) + 12;
        display(x0, y, x1 - x0, 12);
        c.fill(x0 + 3, y + 3, x0 + 5, y + 7, WARN);
        c.fill(x0 + 3, y + 8, x0 + 5, y + 10, WARN);
        textFit(notes.get(0), x0 + 9, y + 2, x1 - x0 - 12 - moreW, WARN);
        if (!more.isEmpty()) textRight(more, x1 - 4, y + 2, TEXT_DIM);
        List<String> tip = new ArrayList<>();
        for (String n : notes) tip.add("! " + n);
        hit(x0, y, x1 - x0, 12, null, null, tip);
    }

    private String keysHint() {
        Keymap km = Keymap.fromInts(v.keymap());
        return tr("hint.keys", capsText(km, Control.THROTTLE), capsText(km, Control.STEER), capsText(km, Control.LIFT));
    }

    private String sitHint() {
        Keymap km = Keymap.fromInts(v.keymap());
        return tr("hint.sit", capsText(km, Control.THROTTLE), capsText(km, Control.STEER), capsText(km, Control.LIFT));
    }

    private void bottomStrip(int x0, int y, int x1, AerialView.Flight f) {
        String label = tr("lift");
        c.text(label, x0, y + 3, TEXT_DIM);
        int x = x0 + host.width(label) + 5;
        int barW = clampInt((x1 - x0) / 8, 40, 100);
        double use = f.liftUse();
        int colour = f.liftShort() ? BAD : use > 0.9 ? WARN : GOOD;
        bar(x, y + 3, barW, 8, Double.isFinite(use) ? use : 0.0, colour);
        hit(x, y, barW, BUTTON_H, null, null, List.of(tr("lift.hint")));
        x += barW + 4;
        String pct = Double.isFinite(use) ? Math.round(use * 100.0) + "%" : "-";
        c.text(pct, x, y + 3, f.liftShort() ? BAD : TEXT);
        x += host.width("100%") + 12;

        LeaveMode leave = LeaveMode.byOrdinal(v.leave());
        String leaveLabel = tr("leave");
        String leaveValue = tr("leave." + leave.name().toLowerCase(Locale.ROOT)) + " v";
        int lw = 0;
        for (LeaveMode m : LeaveMode.values()) lw = Math.max(lw, host.width(tr("leave." + m.name().toLowerCase(Locale.ROOT)) + " v"));
        lw += 12;
        int lx = x1 - lw;
        int llx = lx - host.width(leaveLabel) - 5;
        final int menuX = lx;
        final int menuY = y;
        button(lx, y, lw, leaveValue, false, () -> openLeaveMenu(menuX, menuY), null,
                List.of(tr("leave." + leave.name().toLowerCase(Locale.ROOT) + ".hint"), tr("leave.now.hint")));

        String trim = tr("trim");
        String value = trimText(f.trim());
        int valueW = host.width("+100%");
        int gaugeW = 36;
        int groupW = host.width(trim) + 5 + 12 + 2 + gaugeW + 2 + 12 + 4 + valueW;
        boolean leaveLabelFits = llx - 12 - groupW >= x;
        if (leaveLabelFits) c.text(leaveLabel, llx, y + 3, TEXT_DIM);
        int right = leaveLabelFits ? llx - 12 : lx - 12;
        int gx = right - groupW;
        if (gx < x) return;
        List<String> tip = List.of(tr("trim.hint"), tr("trim.level.hint.click"));
        c.text(trim, gx, y + 3, TEXT_DIM);
        gx += host.width(trim) + 5;
        button(gx, y, 12, "-", false, () -> send(AerialActionPacket.TRIM, -1, 0, 0L), null, tip);
        gx += 14;
        centredBar(gx, y + 3, gaugeW, 8, f.trim(), f.trim() >= 0 ? GOOD : WARN);
        hit(gx, y, gaugeW, BUTTON_H, () -> send(AerialActionPacket.TRIM, 0, 1, 0L), null, tip);
        gx += gaugeW + 2;
        button(gx, y, 12, "+", false, () -> send(AerialActionPacket.TRIM, 1, 0, 0L), null, tip);
        gx += 16;
        c.text(value, gx + valueW - host.width(value), y + 3, Math.abs(f.trim()) < 1.0e-3 ? TEXT_DIM : TEXT);
    }

    void openLeaveMenu(int x, int y) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (LeaveMode m : LeaveMode.values()) {
            labels.add(tr("leave." + m.name().toLowerCase(Locale.ROOT)));
            final int ordinal = m.ordinal();
            actions.add(() -> send(AerialActionPacket.LEAVE, ordinal, 0, 0L));
        }
        openPopup(x, y - labels.size() * 12 - 6, labels, actions, v.leave());
    }

    private void liftPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y0 = top + CONTENT_Y;
        int y1 = top + h - PAD;
        int th = clampInt((y1 - y0) * 56 / 100, 104, 200);
        int split = x0 + (x1 - x0) * 44 / 100;
        budgetCard(x0, y0, split - 3, y0 + th);
        ceilingCard(split + 3, y0, x1, y0 + th);
        balloonsCard(x0, y0 + th + 6, x1, y1);
    }

    private double[] liftFigures() {
        double mass = Float.isFinite(v.mass()) ? v.mass() : 0.0;
        double now = 0.0;
        double most = 0.0;
        for (AerialView.Balloon b : v.balloons()) {
            double l = Math.max(0.0, b.lift()) * mass;
            now += l;
            if (b.gas() > 1.0e-3f) most += l * b.maxGas() / b.gas();
            else most += Math.max(0.0, b.maxGas()) * 1.5 * (Float.isFinite(b.pressure()) ? b.pressure() : 1.0);
        }
        return new double[] {now, most, mass, mass * load()};
    }

    private void budgetCard(int x0, int y0, int x1, int y1) {
        int body = card(x0, y0, x1 - x0, y1 - y0, tr("budget.title"));
        AerialView.Flight f = v.flight();
        double[] fig = liftFigures();
        double now = fig[0];
        double most = fig[1];
        double own = fig[2];
        double carried = fig[3];
        double weight = own + carried;
        String[] labels = {tr("budget.weight"), tr("budget.now"), tr("budget.most")};
        int lw = 0;
        for (String l : labels) lw = Math.max(lw, host.width(l));
        int valueW = host.width("00.0k") + 4;
        int bx0 = x0 + 6 + lw + 6;
        int bx1 = x1 - 6 - valueW;
        int bw = bx1 - bx0;
        if (bw < 30 || !(weight > 0.0)) {
            textFit(tr("budget.none"), x0 + 6, body + 2, x1 - x0 - 12, TEXT_DIM);
            return;
        }
        double scale = Math.max(weight, most) * 1.08;
        int ry = body + 3;
        int barH = 9;
        int gap = clampInt((y1 - body - 30) / 4, 5, 14);

        c.text(labels[0], x0 + 6, ry + 1, TEXT_DIM);
        well(bx0, ry, bw, barH, WELL);
        int ownW = (int) Math.round((bw - 2) * Math.min(1.0, own / scale));
        int loadW = (int) Math.round((bw - 2) * Math.min(1.0, weight / scale)) - ownW;
        segments(bx0 + 1, ry + 1, bx0 + 1 + ownW, ry + barH - 1, HULL_LIGHT);
        if (loadW > 0) {
            c.fill(bx0 + 1 + ownW, ry + 1, bx0 + 1 + ownW + loadW, ry + barH - 1, 0xFFB07A3A);
            for (int k = bx0 + 1 + ownW; k < bx0 + 1 + ownW + loadW; k += 3) c.fill(k, ry + 1, k + 1, ry + barH - 1, 0xFF7A4E20);
        }
        textRight(big(weight), x1 - 6, ry + 1, TEXT);
        List<String> wTip = new ArrayList<>();
        wTip.add(tr("budget.weight.hint", big(own)));
        if (carried > 0.5) wTip.add(tr("budget.load.hint", big(carried)));
        hit(bx0, ry, bw, barH, null, null, wTip);
        ry += barH + gap;

        c.text(labels[1], x0 + 6, ry + 1, TEXT_DIM);
        well(bx0, ry, bw, barH, WELL);
        int nowW = (int) Math.round((bw - 2) * Math.min(1.0, now / scale));
        int liftColour = f.liftShort() ? BAD : steamy() ? STEAM : HOT_AIR;
        if (nowW > 0) segments(bx0 + 1, ry + 1, bx0 + 1 + nowW, ry + barH - 1, liftColour);
        textRight(big(now), x1 - 6, ry + 1, TEXT);
        hit(bx0, ry, bw, barH, null, null, List.of(tr("budget.now.hint")));
        ry += barH + gap;

        c.text(labels[2], x0 + 6, ry + 1, TEXT_DIM);
        well(bx0, ry, bw, barH, WELL);
        int mostW = (int) Math.round((bw - 2) * Math.min(1.0, most / scale));
        if (mostW > 0) segments(bx0 + 1, ry + 1, bx0 + 1 + mostW, ry + barH - 1, 0xFF6A5A3A);
        textRight(big(most), x1 - 6, ry + 1, TEXT_DIM);
        hit(bx0, ry, bw, barH, null, null, List.of(tr("budget.most.hint")));

        int wx = bx0 + 1 + (int) Math.round((bw - 2) * Math.min(1.0, weight / scale));
        c.fill(wx, body + 1, wx + 1, ry + barH + 1, TEXT);
        ry += barH + gap + 1;
        String say;
        int sayColour;
        if (f.weighing()) {
            say = tr("budget.weighing");
            sayColour = WARN;
        } else if (carried > 0.5) {
            say = tr("budget.load", big(carried));
            sayColour = WARN;
        } else if (v.balloons().isEmpty()) {
            say = tr("budget.none");
            sayColour = TEXT_DIM;
        } else {
            say = most > 0.0 ? tr("budget.margin", pct(Math.max(0.0, most / weight - 1.0))) : tr("budget.none");
            sayColour = most > weight ? GOOD : BAD;
        }
        for (String line : wrap(say, x1 - x0 - 12)) {
            if (ry + 8 > y1 - 3) break;
            c.text(line, x0 + 6, ry, sayColour);
            ry += LINE;
        }
    }

    // drawn through the server's own ceiling so they can't disagree
    private void ceilingCard(int x0, int y0, int x1, int y1) {
        int body = card(x0, y0, x1 - x0, y1 - y0, tr("ceiling.title"));
        AerialView.Flight f = v.flight();
        String cap = Float.isFinite(f.cap()) ? tr("cap.value", num0(f.cap())) : tr("cap.off");
        String capLabel = tr("cap");
        int capY = y1 - 4 - BUTTON_H;

        c.text(capLabel, x0 + 6, capY + 3, TEXT_DIM);
        int cx = x0 + 6 + host.width(capLabel) + 5;
        int capW = Math.max(host.width(cap), host.width(tr("cap.value", "000")));
        List<String> capTip = List.of(tr("cap.hint", CAP_STEP));
        stepper(cx, capY, cap, capW, () -> send(AerialActionPacket.CAP, -CAP_STEP, 0, 0L), () -> send(AerialActionPacket.CAP, CAP_STEP, 0, 0L),
                capTip);
        cx += stepperWidth(capW) + 4;
        int hereW = host.width(tr("cap.here")) + 10;
        int clearW = host.width(tr("cap.clear")) + 10;
        if (cx + hereW + 2 + clearW <= x1 - 6) {
            button(cx, capY, hereW, tr("cap.here"), false, () -> send(AerialActionPacket.CAP, 0, 2, 0L), null, List.of(tr("cap.here.hint")));
            button(cx + hereW + 2, capY, clearW, tr("cap.clear"), !Float.isFinite(f.cap()), () -> send(AerialActionPacket.CAP, 0, 1, 0L), null,
                    List.of(tr("cap.clear.hint")));
        }

        int gx0 = x0 + 6 + host.width("000") + 4;
        int gx1 = x1 - 8;
        int gy0 = body + 2;
        int gy1 = capY - 6;
        if (gy1 - gy0 < 30 || gx1 - gx0 < 40 || !Float.isFinite(f.altitude())) {
            String none = Float.isFinite(f.ceiling()) ? tr("ceiling", num0(f.ceiling())) : tr("ceiling.none");
            textFit(none, x0 + 6, body + 2, x1 - x0 - 12, TEXT_DIM);
            return;
        }
        display(gx0, gy0, gx1 - gx0, gy1 - gy0);
        double[] fig = liftFigures();
        double weight = fig[2] + fig[3];
        double alt = f.altitude();
        double ceil = f.ceiling();
        double top = f.worldTop();
        double lo = Math.floor((Float.isFinite(f.ground()) ? Math.min(f.ground(), alt) : alt) - 8.0);
        double hi = Math.max(alt + 24.0, Double.isFinite(ceil) ? ceil + 16.0 : alt + 60.0);
        if (Double.isFinite(top)) hi = Math.min(hi, top);
        if (hi - lo < 16.0) hi = lo + 16.0;
        double most = fig[1];
        double xMax = Math.max(weight, most) * 1.25;
        if (!(xMax > 0.0)) return;
        final double yLo = lo;
        final double yHi = hi;
        java.util.function.DoubleUnaryOperator ya = y -> gy1 - 1 - (y - yLo) / (yHi - yLo) * (gy1 - gy0 - 2);
        java.util.function.DoubleUnaryOperator xa = kpg -> gx0 + 1 + Math.min(1.0, kpg / xMax) * (gx1 - gx0 - 2);

        int stepY = (hi - lo) > 160 ? 50 : 25;
        for (int yy = (int) (Math.ceil(lo / stepY) * stepY); yy <= hi; yy += stepY) {
            int py = (int) Math.round(ya.applyAsDouble(yy));
            if (py < gy0 + 4 || py > gy1 - 4) continue;
            c.fill(gx0 + 1, py, gx1 - 1, py + 1, 0xFF2A2A2A);
            textRight(Integer.toString(yy), gx0 - 3, py - 4, TEXT_FAINT);
        }

        if (Float.isFinite(f.ground())) {
            int gy = (int) Math.round(ya.applyAsDouble(f.ground()));
            if (gy < gy1 - 1) {
                c.fill(gx0 + 1, Math.max(gy0 + 1, gy), gx1 - 1, gy1 - 1, EARTH);
                c.fill(gx0 + 1, Math.max(gy0 + 1, gy), gx1 - 1, Math.max(gy0 + 1, gy) + 1, EARTH_TOP);
            }
        }

        int wx = (int) Math.round(xa.applyAsDouble(weight));
        for (int yy = gy0 + 1; yy < gy1 - 1; yy += 4) c.fill(wx, yy, wx + 1, Math.min(gy1 - 1, yy + 2), TEXT_DIM);

        double usable = 0.9 * most;
        if (Double.isFinite(ceil) && ceil > lo && ceil < hi && weight > 0.0) usable = weight * Math.exp(-0.004 * (alt - ceil));
        for (int pass = 0; pass < 2; pass++) {
            double scale = pass == 0 ? 1.0 / 0.9 : 1.0;
            double prevX = Double.NaN;
            double prevY = Double.NaN;
            for (int k = 0; k <= 24; k++) {
                double yy = lo + (hi - lo) * k / 24.0;
                double kpg = usable * scale * Math.exp(-0.004 * (yy - alt));
                double px = xa.applyAsDouble(kpg);
                double py = ya.applyAsDouble(yy);
                if (Double.isFinite(prevX)) line(prevX, prevY, px, py, pass == 0 ? 1.0 : 2.0, pass == 0 ? alpha(BRASS, 0x70) : BRASS);
                prevX = px;
                prevY = py;
            }
        }

        if (Double.isFinite(ceil) && ceil > lo && ceil < hi) {
            int cy = (int) Math.round(ya.applyAsDouble(ceil));
            for (int xx = gx0 + 1; xx < gx1 - 1; xx += 5) c.fill(xx, cy, Math.min(gx1 - 1, xx + 3), cy + 1, BAD);
            String lab = tr("ceiling.short", num0(ceil));
            nameplate(lab, gx0 + 3, cy - 11, BAD);
        }
        if (Float.isFinite(f.cap()) && f.cap() > lo && f.cap() < hi) {
            int cy = (int) Math.round(ya.applyAsDouble(f.cap()));
            c.fill(gx0 + 1, cy, gx1 - 1, cy + 1, INFO);
        }

        double sx = xa.applyAsDouble(fig[0]);
        double sy = ya.applyAsDouble(alt);
        disc(sx, sy, 3.2, EDGE);
        disc(sx, sy, 2.4, f.liftShort() ? BAD : GOOD);
        List<String> tip = new ArrayList<>();
        tip.add(Double.isFinite(ceil) ? (Float.isFinite(f.balloonCeiling()) && Math.abs(f.balloonCeiling() - ceil) > 0.5f
                ? tr("ceiling.balloons", num0(ceil), num0(f.balloonCeiling())) : tr("ceiling", num0(ceil))) : tr("ceiling.none"));
        tip.add(tr("ceiling.hint"));
        hit(gx0, gy0, gx1 - gx0, gy1 - gy0, null, null, tip);
    }

    private void balloonsCard(int x0, int y0, int x1, int y1) {
        int n = v.balloons().size();
        int heaters = 0;
        int lit = 0;
        for (AerialView.Balloon b : v.balloons()) {
            heaters += b.heaters();
            lit += b.lit();
        }
        String title = tr("balloons.title", n, heaters, lit);
        int body = card(x0, y0, x1 - x0, y1 - y0, title);
        if (n == 0) {
            int ry = body + 2;
            for (String line : wrap(tr("lift.empty"), x1 - x0 - 12)) {
                if (ry + 8 > y1 - 3) break;
                c.text(line, x0 + 6, ry, TEXT_DIM);
                ry += LINE;
            }
            return;
        }
        int tileW = 66;
        int fit = Math.max(1, (x1 - x0 - 8) / (tileW + 4));
        balloonFirst = clampInt(balloonFirst, 0, Math.max(0, n - fit));
        if (n > fit) {
            button(x1 - 32, y0 + 1, 14, "<", false, balloonFirst > 0 ? () -> balloonFirst = Math.max(0, balloonFirst - fit) : null, null,
                    List.of(tr("balloons.prev")));
            button(x1 - 16, y0 + 1, 14, ">", false,
                    balloonFirst + fit < n ? () -> balloonFirst = Math.min(n - fit, balloonFirst + fit) : null, null,
                    List.of(tr("balloons.next")));
        }
        int tx = x0 + 5;
        int ty = body;
        int tileH = y1 - 4 - ty;
        for (int i = balloonFirst; i < Math.min(n, balloonFirst + fit); i++) {
            balloonTile(tx, ty, tileW, tileH, i);
            tx += tileW + 4;
        }
    }

    private void balloonTile(int x, int y, int tw, int th, int i) {
        AerialView.Balloon b = v.balloons().get(i);
        sunk(x, y, tw, th, CARD, SLATE_EDGE, SLATE_RIM);
        if (!b.driven()) outline(x, y, tw, th, WARN);
        boolean steam = "steam".equals(b.kind());
        int gasColour = steam ? STEAM : HOT_AIR;
        c.text("B" + (i + 1), x + 4, y + 3, TEXT);
        textRight(pct(b.lift()), x + tw - 4, y + 3, TEXT_DIM);

        int gx = x + 5;
        int gy = y + 14;
        int gw = tw - 10;
        int gh = clampInt(th - 44, 12, 26);
        raised(gx, gy, gw, gh, FABRIC_DARK, FABRIC_LIGHT, 0xFF7E8283);
        if (b.capacity() > 0f) {
            double cap = b.capacity();
            int inner = gw - 4;
            int reach = (int) Math.round(inner * Math.max(0.0, Math.min(1.0, b.maxGas() / cap)));
            if (reach < inner) {
                for (int k = gx + 2 + reach; k < gx + 2 + inner; k += 2) c.fill(k, gy + 2, k + 1, gy + gh - 2, 0xFF6A6E6F);
            }
            int gas = (int) Math.round(inner * Math.max(0.0, Math.min(1.0, b.gas() / cap)));
            if (gas > 0) segments(gx + 2, gy + 2, gx + 2 + gas, gy + gh - 2, gasColour);
            if (b.target() > 0f) {
                int t = gx + 2 + (int) Math.round(inner * Math.max(0.0, Math.min(1.0, b.target() / cap)));
                t = Math.min(t, gx + gw - 3);
                c.fill(t, gy - 1, t + 1, gy + gh + 1, YELLOW);
            }
            hit(gx, gy, gw, gh, null, null, List.of(tr("gas.hint", num0(b.gas()), num0(cap), num0(b.target()), num0(b.maxGas()))));
        }

        int ly = gy + gh + 4;
        int shown = Math.min(b.heaters(), (gw - 2) / 5);
        for (int k = 0; k < shown; k++) {
            int lx = gx + 1 + k * 5;
            boolean on = k < b.lit();
            c.fill(lx, ly, lx + 4, ly + 4, EDGE);
            c.fill(lx + 1, ly + 1, lx + 3, ly + 3, on ? FIRE : 0xFF3A3A3A);
            if (on) c.fill(lx + 1, ly + 1, lx + 2, ly + 2, FIRE_CORE);
        }
        hit(gx, ly - 1, gw, 6, null, null, List.of(tr("heaters", b.heaters(), b.lit()), tr("where.is", where(b.lon()))));

        final long id = b.id();
        String who = b.driven() ? tr("driven") : tr("driven.hand");
        int sy = y + th - 13;
        checkbox(x + 4, sy + 1, b.driven(), "", () -> send(AerialActionPacket.BALLOON_DRIVEN, 0, 0, id), List.of(tr("driven.hint")));
        textFit(who, x + 24, sy + 2, tw - 28, b.driven() ? TEXT_DIM : WARN);
    }

    private static int pickIndex(long picked, int count, java.util.function.IntToLongFunction posOf) {
        for (int i = 0; i < count; i++) if (posOf.applyAsLong(i) == picked) return i;
        return count > 0 ? 0 : -1;
    }

    private void enginesPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y0 = top + CONTENT_Y;
        int y1 = top + h - PAD;
        int split = x0 + (x1 - x0) * 47 / 100;
        int n = v.engines().size();
        int picked = pickIndex(pickedEngine, n, i -> v.engines().get(i).pos());
        ShipFrame fr = topView(x0, y0, split - 3, y1, tr("top.engines"));
        if (fr != null) {
            for (int i = 0; i < n; i++) {
                AerialView.Engine e = v.engines().get(i);
                double[] at = fr.at(e.lon(), e.lat());
                if (!inBox(at, x0 + 4, y0 + 4, split - 7, y1 - 4)) continue;
                engineGlyph(fr, at, e, i, i == picked);
            }
        }
        int rx0 = split + 3;
        AerialView.Flight f = v.flight();
        double weight = v.mass() * v.gravity();
        String summary = !(weight > 0.0) || n == 0 ? tr("engines.summary.none")
                : Float.isFinite(f.topSpeed()) ? tr("engines.summary.short", num1(f.topSpeed())) : tr("engines.summary.none");
        String relearn = tr("relearn");
        int relearnW = host.width(relearn) + 10;
        button(x1 - relearnW, y0, relearnW, relearn, false, () -> send(AerialActionPacket.RELEARN, 0, 0, 0L), null,
                List.of(tr("relearn.hint")));
        textFit(summary, rx0, y0 + 3, x1 - relearnW - 6 - rx0, TEXT_DIM);
        if (n == 0) {
            int ry = y0 + 22;
            for (String line : wrap(tr("engines.empty"), x1 - rx0)) {
                if (ry + 8 > y1) break;
                c.text(line, rx0, ry, TEXT_DIM);
                ry += LINE;
            }
            return;
        }
        int detailH = clampInt((y1 - y0) * 48 / 100, 92, 132);
        int listY0 = y0 + BUTTON_H + 5;
        int listY1 = y1 - detailH - 5;
        list(rx0, listY0, x1, listY1, n, "engines", (i, lx0, ry, lx1) -> engineRow(i, lx0, ry, lx1, i == picked));
        engineDetail(rx0, y1 - detailH, x1, y1, picked);
    }

    private void engineRow(int i, int x0, int y, int x1, boolean picked) {
        AerialView.Engine e = v.engines().get(i);
        if (picked) pickedRow(x0, y, x1);
        else if (hot(x0, y, x1 - x0, rowH - 1)) c.fill(x0, y, x1, y + rowH - 1, SLATE_LIGHT);
        int ty = y + (rowH - 8) / 2;
        int dot = engineTrouble(e) ? BAD : e.state() == AerialView.ENGINE_OFF ? TEXT_FAINT : e.thruster() ? INFO : BRASS;
        c.fill(x0 + 4, ty + 1, x0 + 9, ty + 6, dot);
        String name = engineName(i);
        int barW = 30;
        String state = engineState(e);
        int stateW = Math.min(host.width(state), (x1 - x0) / 3);
        int nameW = x1 - x0 - 14 - barW - 8 - stateW - 6;
        textFit(name, x0 + 13, ty, nameW, e.state() == AerialView.ENGINE_OFF ? TEXT_DIM : TEXT);
        int bx = x1 - 4 - stateW - 6 - barW;
        if (e.thruster()) bar(bx, ty + 1, barW, 7, e.push(), dot);
        else centredBar(bx, ty + 1, barW, 7, e.push(), e.push() >= 0 ? GOOD : WARN);
        textFit(state, x1 - 4 - stateW, ty, stateW, engineTrouble(e) ? BAD : TEXT_DIM);
        final long pos = e.pos();
        hit(x0, y, x1 - x0, rowH - 1, () -> pickedEngine = pos, null, List.of(tr("engine.hint", name, engineNow(e))));
    }

    private void engineDetail(int x0, int y0, int x1, int y1, int i) {
        if (i < 0) return;
        AerialView.Engine e = v.engines().get(i);
        String where = tr("engine.where.short", tr("dir." + DIRS[e.dir()]), place(e.lon(), e.lat(), e.up()));
        int body = card(x0, y0, x1 - x0, y1 - y0, engineName(i));
        long pos = e.pos();
        int labelW = detailLabelWidth("detail.job", "detail.reverse", "detail.power", "detail.now", "detail.ramp");
        int cx = x0 + 6 + labelW + 6;
        int ry = body;
        textFit(where, x0 + 6, ry, x1 - x0 - 12, TEXT_DIM);
        ry += LINE + 3;
        detailLabel(x0 + 6, ry, "detail.job");
        List<String> jobs = new ArrayList<>();
        List<Runnable> jobActions = new ArrayList<>();
        List<List<String>> jobTips = new ArrayList<>();
        for (AerialSetup.EngineJob j : AerialSetup.EngineJob.values()) {
            jobs.add(tr("job." + j.key));
            final int ordinal = j.ordinal();
            jobActions.add(() -> send(AerialActionPacket.ENGINE_JOB, ordinal, 0, pos));
            jobTips.add(List.of(tr("job." + j.key + ".hint")));
        }
        final int jobY = ry;
        if (cx + segmentedWidth(jobs) <= x1 - 6) segmented(cx, ry, jobs, e.job(), jobActions, jobTips);
        else button(cx, ry, Math.min(x1 - 6 - cx, host.width(jobs.get(e.job())) + 12), jobs.get(e.job()), false,
                () -> openJobMenu(cx, jobY + BUTTON_H, pos, e.job()), null, jobTips.get(e.job()));
        ry += BUTTON_H + 4;
        detailLabel(x0 + 6, ry, "detail.power");
        List<String> limits = new ArrayList<>();
        List<Runnable> limitActions = new ArrayList<>();
        int chosen = -1;
        for (int k = 0; k < AerialSetup.LIMITS.length; k++) {
            limits.add(AerialSetup.LIMITS[k] + "%");
            final int limit = AerialSetup.LIMITS[k];
            if (limit == e.limit()) chosen = k;
            limitActions.add(() -> send(AerialActionPacket.ENGINE_LIMIT, limit, 0, pos));
        }
        int powerW = segmented(cx, ry, limits, chosen, limitActions, List.of(List.of(tr(e.thruster() ? "power.thruster.hint" : "power.hint"))));
        if (!e.thruster()) {
            int rx = cx + powerW + 10;
            String rev = tr("detail.reverse");
            if (rx + host.width(rev) + 24 <= x1 - 6) {
                c.text(rev, rx, ry + 3, TEXT_DIM);
                checkbox(rx + host.width(rev) + 5, ry + 2, e.reverse(), "", () -> send(AerialActionPacket.ENGINE_REVERSE, 0, 0, pos),
                        List.of(tr("reverse.hint")));
            }
        }
        ry += BUTTON_H + 5;
        if (ry + 8 <= y1 - 3) {
            detailLabel(x0 + 6, ry - 2, "detail.now");
            textFit(engineNow(e), cx, ry - 2, x1 - 6 - cx, engineTrouble(e) ? BAD : TEXT);
        }
        ry += LINE;

        if (e.thruster() && e.kind() != AerialView.KIND_BOOSTER && e.state() != AerialView.ENGINE_OFF && ry + 8 <= y1 - 3) {
            int step = Feel.byOrdinal(v.feel()).jetTicksPerStep;
            int aim = Float.isFinite(e.aim()) ? Math.round(e.aim() * 100f) : e.rpm();
            String ramp = Math.abs(aim - e.rpm()) >= 2 ? tr("ramp.walking", e.rpm(), aim, step) : tr("ramp.steady", e.rpm(), step);
            detailLabel(x0 + 6, ry - 2, "detail.ramp");
            textFit(ramp, cx, ry - 2, x1 - 6 - cx, Math.abs(aim - e.rpm()) >= 2 ? YELLOW : TEXT_DIM);
        }
    }

    private int detailLabelWidth(String... keys) {
        int wd = 0;
        for (String k : keys) wd = Math.max(wd, host.width(tr(k)));
        return wd;
    }

    private void detailLabel(int x, int y, String key) {
        c.text(tr(key), x, y + 3, TEXT_DIM);
    }

    private void pickedRow(int x0, int y, int x1) {
        c.fill(x0, y, x1, y + rowH - 1, SLATE_LIGHT);
        outline(x0, y, x1 - x0, rowH - 1, BRASS);
    }

    static final String[] DIRS = {"ahead", "astern", "up", "down", "right", "left"};

    private String engineName(int i) {
        AerialView.Engine e = v.engines().get(i);
        int governors = 0;
        int thrusters = 0;
        for (int k = 0; k <= i; k++) {
            if (v.engines().get(k).thruster()) thrusters++;
            else governors++;
        }
        if (e.thruster()) {
            String kind = switch (e.kind()) {
                case AerialView.KIND_VECTOR -> tr("kind.vector");
                case AerialView.KIND_BOOSTER -> tr("kind.booster");
                default -> tr("kind.thruster");
            };
            return "T" + thrusters + " " + kind;
        }
        return e.props() > 1 ? tr("engine.name", "G" + governors, e.props()) : "G" + governors;
    }

    private String engineState(AerialView.Engine e) {
        if (e.thruster()) {
            return switch (e.state()) {
                case AerialView.ENGINE_STARVED -> tr("state.supply." + JetSupply.byOrdinal(e.supply()).key());
                case AerialView.ENGINE_FOREIGN -> tr("state.foreign");
                case AerialView.ENGINE_OFF -> tr("state.off");
                default -> e.rpm() > 0 ? tr("throttle", e.rpm()) : tr("state.idle");
            };
        }
        return switch (e.state()) {
            case AerialView.ENGINE_STARVED -> tr("state.starved");
            case AerialView.ENGINE_OVERSTRESSED -> tr("state.over");
            case AerialView.ENGINE_FOREIGN -> tr("state.foreign");
            case AerialView.ENGINE_OFF -> tr("state.off");
            default -> e.known() ? tr("rpm", e.rpm()) : tr("state.learning");
        };
    }

    private String engineNow(AerialView.Engine e) {
        if (e.state() == AerialView.ENGINE_OFF) return tr("state.off");
        if (e.thruster()) {
            if (e.state() == AerialView.ENGINE_STARVED) return tr("state.supply." + JetSupply.byOrdinal(e.supply()).key());
            if (e.state() == AerialView.ENGINE_FOREIGN) return tr("state.foreign");
            return tr("thruster.now", e.rpm(), tr("dir." + DIRS[e.dir()]), big(e.thrust()));
        }
        if (engineTrouble(e)) return engineState(e);
        String dir = tr("dir." + DIRS[e.push() < 0f ? e.dir() ^ 1 : e.dir()]);
        return tr("engine.now", pct(Math.abs(e.push())), dir, e.rpm());
    }

    void openJobMenu(int x, int y, long pos, int current) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (AerialSetup.EngineJob j : AerialSetup.EngineJob.values()) {
            labels.add(tr("job." + j.key));
            final int ordinal = j.ordinal();
            actions.add(() -> send(AerialActionPacket.ENGINE_JOB, ordinal, 0, pos));
        }
        openPopup(x, y, labels, actions, current);
    }

    private void finsPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y0 = top + CONTENT_Y;
        int y1 = top + h - PAD;
        int split = x0 + (x1 - x0) * 47 / 100;
        int n = v.surfaces().size();
        int picked = pickIndex(pickedFin, n, i -> v.surfaces().get(i).pos());
        if (picked >= 0) pickedFin = v.surfaces().get(picked).pos();
        sideView(x0, y0, split - 3, y1, SideMarks.FINS);
        int rx0 = split + 3;
        if (n == 0) {
            int ry = y0 + 2;
            for (String line : wrap(tr("fins.empty"), x1 - rx0)) {
                if (ry + 8 > y1) break;
                c.text(line, rx0, ry, TEXT_DIM);
                ry += LINE;
            }
            return;
        }
        textFit(tr("fins.summary.short"), rx0, y0 + 3, x1 - rx0, TEXT_DIM);
        int detailH = clampInt((y1 - y0) * 52 / 100, 100, 140);
        list(rx0, y0 + BUTTON_H + 5, x1, y1 - detailH - 5, n, "fins", (i, lx0, ry, lx1) -> finRow(i, lx0, ry, lx1, i == picked));
        finDetail(rx0, y1 - detailH, x1, y1, picked);
    }

    private String finName(int i) {
        return "S" + (i + 1);
    }

    private void finRow(int i, int x0, int y, int x1, boolean picked) {
        AerialView.Surface s = v.surfaces().get(i);
        AerialSetup.SurfaceRole role = AerialSetup.SurfaceRole.byOrdinal(s.role());
        boolean bad = role != AerialSetup.SurfaceRole.OFF && (!s.assembled() || !s.locked());
        if (picked) pickedRow(x0, y, x1);
        else if (hot(x0, y, x1 - x0, rowH - 1)) c.fill(x0, y, x1, y + rowH - 1, SLATE_LIGHT);
        int ty = y + (rowH - 8) / 2;
        c.fill(x0 + 4, ty + 1, x0 + 9, ty + 6, bad ? BAD : role == AerialSetup.SurfaceRole.OFF ? TEXT_FAINT : BRASS);
        String label = finName(i) + "  " + tr("role." + role.key);
        String right = bad ? (!s.assembled() ? tr("fin.empty") : tr("fin.unlocked"))
                : role == AerialSetup.SurfaceRole.OFF ? where(s.lon()) : tr("deg", signed0(s.angle()));
        int rw = Math.min(host.width(right), (x1 - x0) / 2);
        textFit(label, x0 + 13, ty, x1 - x0 - 13 - rw - 10, role == AerialSetup.SurfaceRole.OFF ? TEXT_DIM : TEXT);
        textFit(right, x1 - 4 - rw, ty, rw, bad ? BAD : TEXT_DIM);
        final long pos = s.pos();
        hit(x0, y, x1 - x0, rowH - 1, () -> pickedFin = pos, null, List.of(finName(i) + ": " + finAt(s)));
    }

    private void finDetail(int x0, int y0, int x1, int y1, int i) {
        if (i < 0) return;
        AerialView.Surface s = v.surfaces().get(i);
        long pos = s.pos();
        AerialSetup.SurfaceRole role = AerialSetup.SurfaceRole.byOrdinal(s.role());
        int body = card(x0, y0, x1 - x0, y1 - y0, finName(i) + "  " + finAt(s));
        int labelW = detailLabelWidth("detail.job", "detail.throw", "detail.key", "detail.now");
        int cx = x0 + 6 + labelW + 6;
        int ry = body;
        detailLabel(x0 + 6, ry, "detail.job");
        List<String> roles = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        List<List<String>> tips = new ArrayList<>();
        for (AerialSetup.SurfaceRole r : AerialSetup.SurfaceRole.values()) {
            roles.add(tr("role." + r.key));
            final int ordinal = r.ordinal();
            acts.add(() -> send(AerialActionPacket.SURFACE_ROLE, ordinal, 0, pos));
            tips.add(List.of(tr("role." + r.key + ".hint")));
        }
        final int roleY = ry;
        if (cx + segmentedWidth(roles) <= x1 - 6) segmented(cx, ry, roles, s.role(), acts, tips);
        else button(cx, ry, host.width(roles.get(s.role())) + 12, roles.get(s.role()), false,
                () -> openRoleMenu(cx, roleY + BUTTON_H, pos, s.role()), null, tips.get(s.role()));
        ry += BUTTON_H + 4;
        if (role != AerialSetup.SurfaceRole.OFF) {
            detailLabel(x0 + 6, ry, "detail.throw");
            int deg = s.degrees();
            String widest = "180";
            stepper(cx, ry, Integer.toString(deg), host.width(widest),
                    () -> send(AerialActionPacket.SURFACE_THROW, AerialSetup.stepThrow(role, deg, -1), 0, pos),
                    () -> send(AerialActionPacket.SURFACE_THROW, AerialSetup.stepThrow(role, deg, 1), 0, pos),
                    List.of(tr(role == AerialSetup.SurfaceRole.TOOL ? "throw.tool.hint" : "throw.hint")));
            int ix = cx + stepperWidth(host.width(widest)) + 10;
            String inv = tr("detail.invert");
            if (ix + host.width(inv) + 24 <= x1 - 6) {
                c.text(inv, ix, ry + 3, TEXT_DIM);
                checkbox(ix + host.width(inv) + 5, ry + 2, s.invert(), "", () -> send(AerialActionPacket.SURFACE_INVERT, 0, 0, pos),
                        List.of(tr("fin.invert.hint")));
            }
            ry += BUTTON_H + 4;
            if (role == AerialSetup.SurfaceRole.TOOL) {
                detailLabel(x0 + 6, ry, "detail.key");
                PartInput in = PartInput.byOrdinal(s.input());
                int kw = inputButtonWidth();
                final int keyY = ry;
                button(cx, ry, kw, inputName(in), false, () -> openInputMenu(cx, keyY + BUTTON_H, pos, s.input(), false), null,
                        List.of(tr("key.hint")));
                int hx = cx + kw + 10;
                String hold = tr("detail.hold");
                if (hx + host.width(hold) + 24 <= x1 - 6) {
                    c.text(hold, hx, ry + 3, TEXT_DIM);
                    checkbox(hx + host.width(hold) + 5, ry + 2, s.latched(), "", () -> send(AerialActionPacket.SURFACE_LATCH, 0, 0, pos),
                            List.of(tr("latch.hint")));
                }
                ry += BUTTON_H + 4;
            }
        }
        if (ry + 8 > y1 - 3) return;
        detailLabel(x0 + 6, ry - 2, "detail.now");
        if (role != AerialSetup.SurfaceRole.OFF && (!s.assembled() || !s.locked())) {
            String why = tr(!s.assembled() ? "fin.empty.hint" : "fin.unlocked.hint");
            textFit(why, cx, ry - 2, x1 - 6 - cx, BAD);
            return;
        }
        int barW = Math.min(46, x1 - 6 - cx);
        centredBar(cx, ry - 1, barW, 8, s.degrees() > 0 ? s.angle() / s.degrees() : 0.0, BRASS);
        textFit(tr("deg", signed0(s.angle())), cx + barW + 5, ry - 2, x1 - 6 - cx - barW - 5, TEXT);
    }

    private String finAt(AerialView.Surface s) {
        return tr("fin.at", where(s.lon()), tr("hinge." + (s.hinge() == 0 ? "up" : s.hinge() == 1 ? "across" : "other")));
    }

    private void openRoleMenu(int x, int y, long pos, int current) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (AerialSetup.SurfaceRole r : AerialSetup.SurfaceRole.values()) {
            labels.add(tr("role." + r.key));
            final int ordinal = r.ordinal();
            actions.add(() -> send(AerialActionPacket.SURFACE_ROLE, ordinal, 0, pos));
        }
        openPopup(x, y, labels, actions, current);
    }

    private void toolsPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y0 = top + CONTENT_Y;
        int y1 = top + h - PAD;
        int n = v.tools().size();
        int y = y0;
        int guns = 0;
        for (AerialView.Tool t : v.tools()) if (t.gunnery()) guns++;
        if (v.bridge()) {
            String say = guns > 0 ? tr("tools.bridge", guns) : tr("tools.bridge.none");
            List<String> lines = wrap(say, x1 - x0 - 24);
            int bh = 6 + Math.min(2, lines.size()) * LINE;
            display(x0, y, x1 - x0, bh);
            crosshair(x0 + 9, y + bh / 2.0, INFO);
            int ly = y + 4;
            for (String line : lines.subList(0, Math.min(2, lines.size()))) {
                c.text(line, x0 + 19, ly, INFO);
                ly += LINE;
            }
            if (lines.size() > 2) hit(x0, y, x1 - x0, bh, null, null, List.of(say));
            y += bh + 5;
        } else {
            textFit(tr("tools.summary.short"), x0, y + 1, x1 - x0, TEXT_DIM);
            y += LINE + 4;
        }
        if (n == 0) {
            for (String line : wrap(tr("tools.empty"), x1 - x0)) {
                if (y + 8 > y1) break;
                c.text(line, x0, y, TEXT_DIM);
                y += LINE;
            }
            return;
        }
        int picked = pickIndex(pickedTool, n, i -> v.tools().get(i).pos());
        int split = x0 + (x1 - x0) * 48 / 100;
        final int listTop = y;
        list(x0, listTop, split - 3, y1, n, "tools", (i, lx0, ry, lx1) -> toolRow(i, lx0, ry, lx1, i == picked));
        toolDetail(split + 3, listTop, x1, y1, picked);
    }

    private String toolName(int i) {
        AerialView.Tool t = v.tools().get(i);
        String what = host.name(t.first());
        return what.isEmpty() ? "M" + (i + 1) : "M" + (i + 1) + " " + what;
    }

    private void toolRow(int i, int x0, int y, int x1, boolean picked) {
        AerialView.Tool t = v.tools().get(i);
        PartInput in = PartInput.byOrdinal(t.input());
        if (picked) pickedRow(x0, y, x1);
        else if (hot(x0, y, x1 - x0, rowH - 1)) c.fill(x0, y, x1, y + rowH - 1, SLATE_LIGHT);
        int ty = y + (rowH - 8) / 2;
        String right;
        int rightColour;
        if (t.gunnery() || t.bridge()) {
            crosshair(x0 + 6.5, ty + 3.5, INFO);
            right = t.bridge() ? tr("tool.bridge.now") : tr("tool.bridge");
            rightColour = INFO;
        } else {
            c.fill(x0 + 4, ty + 1, x0 + 9, ty + 6, t.foreign() ? BAD : in == PartInput.OFF ? TEXT_FAINT : BRASS);
            right = t.foreign() ? tr("state.foreign") : in == PartInput.OFF ? tr("input.off") : inputName(in);
            rightColour = t.foreign() ? BAD : TEXT_DIM;
        }
        int rw = Math.min(host.width(right), (x1 - x0) / 2);
        textFit(toolName(i), x0 + 13, ty, x1 - x0 - 13 - rw - 10, in == PartInput.OFF && !t.gunnery() ? TEXT_DIM : TEXT);
        textFit(right, x1 - 4 - rw, ty, rw, rightColour);
        final long pos = t.pos();
        hit(x0, y, x1 - x0, rowH - 1, () -> pickedTool = pos, null, null);
    }

    private void toolDetail(int x0, int y0, int x1, int y1, int i) {
        if (i < 0) return;
        AerialView.Tool t = v.tools().get(i);
        long pos = t.pos();
        PartInput in = PartInput.byOrdinal(t.input());
        int body = card(x0, y0, x1 - x0, y1 - y0, toolName(i));
        int ry = body;
        if (t.gunnery() || t.bridge()) {
            String say = t.bridge() ? tr("tool.bridge.detail.now") : tr("tool.bridge.detail");
            for (String line : wrap(say, x1 - x0 - 12)) {
                if (ry + 8 > y1 - 4 - BUTTON_H - 4) break;
                c.text(line, x0 + 6, ry, INFO);
                ry += LINE;
            }
            ry += 3;
        }
        int labelW = detailLabelWidth("detail.key", "detail.rpm", "detail.now");
        int cx = x0 + 6 + labelW + 6;
        if (ry + BUTTON_H > y1 - 4) return;
        detailLabel(x0 + 6, ry, "detail.key");
        int kw = inputButtonWidth();
        final int keyY = ry;
        button(cx, ry, kw, inputName(in), false, () -> openInputMenu(cx, keyY + BUTTON_H, pos, t.input(), true), null, List.of(tr("key.hint")));
        ry += BUTTON_H + 4;
        if (in == PartInput.OFF || ry + BUTTON_H > y1 - 4) return;
        detailLabel(x0 + 6, ry, "detail.rpm");
        int rpm = t.rpm();
        String widest = Integer.toString(MachineAxis.MAX_RPM);
        stepper(cx, ry, Integer.toString(rpm), host.width(widest),
                () -> send(AerialActionPacket.TOOL_RPM, Math.max(MachineAxis.MIN_RPM, rpm - MachineAxis.RPM_STEP), 0, pos),
                () -> send(AerialActionPacket.TOOL_RPM, Math.min(MachineAxis.MAX_RPM, rpm + MachineAxis.RPM_STEP), 0, pos),
                List.of(tr("rpm.hint")));
        ry += BUTTON_H + 4;
        if (ry + BUTTON_H > y1 - 4) return;
        String hold = tr("detail.hold");
        String inv = tr("detail.invert");
        c.text(hold, x0 + 6, ry + 3, TEXT_DIM);
        checkbox(x0 + 6 + host.width(hold) + 5, ry + 2, t.latched(), "", () -> send(AerialActionPacket.TOOL_LATCH, 0, 0, pos),
                List.of(tr("tool.latch.hint")));
        int ix = x0 + 6 + host.width(hold) + 5 + 16 + 12;
        if (ix + host.width(inv) + 24 <= x1 - 6) {
            c.text(inv, ix, ry + 3, TEXT_DIM);
            checkbox(ix + host.width(inv) + 5, ry + 2, t.invert(), "", () -> send(AerialActionPacket.TOOL_INVERT, 0, 0, pos),
                    List.of(tr("tool.invert.hint")));
        }
        ry += BUTTON_H + 4;
        if (ry + 8 > y1 - 3) return;
        detailLabel(x0 + 6, ry - 2, "detail.now");
        if (t.foreign()) {
            textFit(tr("state.foreign"), cx, ry - 2, x1 - 6 - cx, BAD);
            return;
        }
        if (t.bridge()) {
            textFit(tr("tool.bridge.now"), cx, ry - 2, x1 - 6 - cx, INFO);
            return;
        }
        int barW = Math.min(46, x1 - 6 - cx);
        centredBar(cx, ry - 1, barW, 8, t.output(), t.output() >= 0 ? GOOD : WARN);
        String state = Math.abs(t.output()) < 0.01f ? tr("tool.stopped") : t.output() > 0 ? tr("tool.running") : tr("tool.back");
        textFit(state, cx + barW + 5, ry - 2, x1 - 6 - cx - barW - 5, TEXT);
    }

    private void crosshair(double x, double y, int colour) {
        ring(x, y, 2.6, 3.6, colour);
        line(x - 5, y, x - 2, y, 1.0, colour);
        line(x + 2, y, x + 5, y, 1.0, colour);
        line(x, y - 5, x, y - 2, 1.0, colour);
        line(x, y + 2, x, y + 5, 1.0, colour);
    }

    private void lightsPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y0 = top + CONTENT_Y;
        int y1 = top + h - PAD;
        int n = v.lights().size();
        int split = x0 + (x1 - x0) * 47 / 100;
        int picked = pickIndex(pickedLight, n, i -> v.lights().get(i).pos());
        ShipFrame fr = topView(x0, y0, split - 3, y1, tr("top.lights"));
        if (fr != null) {
            for (int i = 0; i < n; i++) {
                AerialView.Light l = v.lights().get(i);
                double[] at = fr.at(l.lon(), l.lat());
                if (!inBox(at, x0 + 4, y0 + 4, split - 7, y1 - 4)) continue;
                lampMark(at[0], at[1], l, i == picked);
                final long pos = l.pos();
                final int index = i;
                hit((int) at[0] - 4, (int) at[1] - 4, 9, 9, () -> pickedLight = pos, null, List.of(lightName(index)));
            }
        }
        int rx0 = split + 3;
        if (n == 0) {
            int ry = y0 + 2;
            for (String line : wrap(tr("lights.empty"), x1 - rx0)) {
                if (ry + 8 > y1) break;
                c.text(line, rx0, ry, TEXT_DIM);
                ry += LINE;
            }
            return;
        }
        textFit(tr("lights.summary.short"), rx0, y0 + 3, x1 - rx0, TEXT_DIM);
        int detailH = clampInt((y1 - y0) * 40 / 100, 78, 110);
        list(rx0, y0 + BUTTON_H + 5, x1, y1 - detailH - 5, n, "lights", (i, lx0, ry, lx1) -> lightRow(i, lx0, ry, lx1, i == picked));
        lightDetail(rx0, y1 - detailH, x1, y1, picked);
    }

    private void lampMark(double x, double y, AerialView.Light l, boolean picked) {
        int colour = lampColour(l.lamp());
        if (picked) ring(x, y, 4.5, 6.0, BRASS_HI);
        if (l.strength() > 0) disc(x, y, 4.0, alpha(colour, 0x55));
        disc(x, y, 2.4, EDGE);
        disc(x, y, 1.7, l.strength() > 0 ? colour : mix(colour, EDGE, 0.65));
    }

    private void lightRow(int i, int x0, int y, int x1, boolean picked) {
        AerialView.Light l = v.lights().get(i);
        AirLight job = AirLight.byOrdinal(l.job());
        if (picked) pickedRow(x0, y, x1);
        else if (hot(x0, y, x1 - x0, rowH - 1)) c.fill(x0, y, x1, y + rowH - 1, SLATE_LIGHT);
        int ty = y + (rowH - 8) / 2;
        int lamp = lampColour(l.lamp());
        c.fill(x0 + 4, ty + 1, x0 + 10, ty + 7, EDGE);
        c.fill(x0 + 5, ty + 2, x0 + 9, ty + 6, l.strength() > 0 ? lamp : mix(lamp, EDGE, 0.65));
        String right = tr("light." + job.key);
        int rw = Math.min(host.width(right), (x1 - x0) / 2);
        textFit(lightName(i), x0 + 14, ty, x1 - x0 - 14 - rw - 10, job == AirLight.OFF ? TEXT_DIM : TEXT);
        textFit(right, x1 - 4 - rw, ty, rw, job == AirLight.OFF ? TEXT_FAINT : BRASS);
        final long pos = l.pos();
        hit(x0, y, x1 - x0, rowH - 1, () -> pickedLight = pos, null, null);
    }

    private void lightDetail(int x0, int y0, int x1, int y1, int i) {
        if (i < 0) return;
        AerialView.Light l = v.lights().get(i);
        long pos = l.pos();
        AirLight job = AirLight.byOrdinal(l.job());
        int body = card(x0, y0, x1 - x0, y1 - y0, lightName(i));
        int labelW = detailLabelWidth("detail.job", "detail.now");
        int cx = x0 + 6 + labelW + 6;
        int ry = body;
        textFit(place(l.lon(), l.lat(), l.up()), x0 + 6, ry, x1 - x0 - 12, TEXT_DIM);
        ry += LINE + 3;
        detailLabel(x0 + 6, ry, "detail.job");
        int jw = 0;
        for (AirLight j : AirLight.values()) jw = Math.max(jw, host.width(tr("light." + j.key)) + 16);
        jw = Math.min(jw, x1 - 6 - cx);
        final int jobY = ry;
        button(cx, ry, jw, fit(tr("light." + job.key) + " v", jw - 6), false, () -> openLightMenu(cx, jobY + BUTTON_H, pos, l.job(), l.lamp()),
                null, List.of(tr("light." + job.key + ".hint")));
        int tx = cx + jw + 10;
        if (job.keyed()) {
            String tog = tr("col.toggle");
            if (tx + host.width(tog) + 24 <= x1 - 6) {
                c.text(tog, tx, ry + 3, TEXT_DIM);
                checkbox(tx + host.width(tog) + 5, ry + 2, l.toggle(), "", () -> send(AerialActionPacket.LIGHT_TOGGLE, 0, 0, pos),
                        List.of(tr("toggle.hint")));
            }
        }
        ry += BUTTON_H + 5;
        if (ry + 8 > y1 - 3) return;
        detailLabel(x0 + 6, ry - 2, "detail.now");
        String now = l.strength() <= 0 ? tr("light.dark") : l.strength() >= AirLight.FULL ? tr("light.full") : tr("light.low");
        textFit(now, cx, ry - 2, x1 - 6 - cx, l.strength() > 0 ? TEXT : TEXT_DIM);
    }

    private String lightName(int i) {
        AerialView.Light l = v.lights().get(i);
        String kind = switch (l.lamp()) {
            case AirLight.WHITE -> tr("lamp.white");
            case AirLight.RED -> tr("lamp.red");
            case AirLight.AMBER -> tr("lamp.amber");
            default -> {
                String a = host.name(l.first());
                String b = host.name(l.second());
                String freq = a.isEmpty() && b.isEmpty() ? tr("link.blank") : a.isEmpty() ? b : b.isEmpty() ? a : a + " + " + b;
                yield tr("link", freq);
            }
        };
        return "L" + (i + 1) + " " + kind;
    }

    private static int lampColour(int lamp) {
        return switch (lamp) {
            case AirLight.WHITE -> LAMP_WHITE;
            case AirLight.RED -> LAMP_RED;
            case AirLight.AMBER -> LAMP_ON;
            default -> INFO;
        };
    }

    void openLightMenu(int x, int y, long pos, int current, int lamp) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        int currentRow = -1;
        for (AirLight j : AirLight.values()) {
            if (j.ordinal() == current) currentRow = labels.size();
            labels.add(tr("light." + j.key));
            final int ordinal = j.ordinal();
            actions.add(() -> send(AerialActionPacket.LIGHT_JOB, ordinal, 0, pos));
        }
        openPopup(x, Math.max(top + CONTENT_Y, Math.min(y, top + h - 4 - labels.size() * 12 - 4)), labels, actions, currentRow);
    }

    private void keysPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y = top + CONTENT_Y;
        int y1 = top + h - PAD;
        Keymap km = Keymap.fromInts(v.keymap());
        HeldKeys held = HeldKeys.of(v.held());
        String note;
        int noteColour;
        if (captureControl != null) {
            String slot = captureControl.isAxis()
                    ? controlName(captureControl) + " " + tr(captureSlot == Keymap.PLUS ? "slot.plus" : "slot.minus")
                    : controlName(captureControl);
            note = captureNote != null ? captureNote : tr("keys.capture", slot);
            noteColour = captureNote != null ? WARN : BRASS_HI;
        } else if (v.youPilot()) {
            note = tr("keys.live");
            noteColour = GOOD;
        } else {
            note = tr("keys.hint");
            noteColour = TEXT_DIM;
        }
        List<String> noteLines = wrap(note, x1 - x0);
        for (String line : noteLines.subList(0, Math.min(2, noteLines.size()))) {
            c.text(line, x0, y, noteColour);
            y += LINE;
        }
        y += 3;

        String reset = tr("reset_keys");
        int resetW = Math.max(host.width(reset), host.width(tr("confirm"))) + 12;
        confirmButton(x1 - resetW, y1 - BUTTON_H, resetW, "reset_keys", () -> send(AerialActionPacket.KEYS_RESET, 0, 0, 0L));

        List<Control> all = AerialSetup.CONTROLS;
        int nameW = host.width(tr("col.control"));
        for (Control ctl : all) nameW = Math.max(nameW, host.width(controlName(ctl)));
        int capW = 40;
        for (Control ctl : all) {
            capW = Math.max(capW, host.width(capLabel(km.plus(ctl))) + 16);
            capW = Math.max(capW, host.width(capLabel(km.minus(ctl))) + 16);
        }
        capW = Math.min(capW, 76);
        capW = Math.max(capW, Math.max(host.width(tr("col.key1")), host.width(tr("col.key2"))) + 2);
        int invW = host.width(tr("invert")) + 12;
        int[] cols = columns(x0 + 4, x1 - 12, new int[] {nameW, capW, capW, invW, -1}, 8);
        int doesX = cols[4];

        brassStrip(x0, y - 2, x1 - x0, 13);
        c.text(tr("col.control"), cols[0], y + 1, ON_BRASS);
        c.text(fit(tr("col.key1"), capW), cols[1], y + 1, ON_BRASS);
        c.text(fit(tr("col.key2"), capW), cols[2], y + 1, ON_BRASS);
        c.text(fit(tr("col.does"), x1 - 12 - doesX), doesX, y + 1, ON_BRASS);
        y += 14;
        final int cw = capW;
        final int iw = invW;
        list(x0, y, x1, y1 - BUTTON_H - 6, all.size(), "keys", (i, rx0, ry, rx1) -> {
            Control ctl = all.get(i);
            boolean on = controlHeld(ctl, km, held);
            if (i % 2 == 1) c.fill(rx0, ry, rx1, ry + rowH - 1, 0x0CFFFFFF);
            c.text(controlName(ctl), cols[0], ry + 3, on ? BRASS_HI : TEXT);
            keyCap(cols[1], ry, cw, km, ctl, Keymap.PLUS, held);
            if (ctl.isAxis()) {
                keyCap(cols[2], ry, cw, km, ctl, Keymap.MINUS, held);
                final int ordinal = ctl.ordinal();
                button(cols[3], ry, iw, tr("invert"), km.inverted(ctl), () -> send(AerialActionPacket.KEY_INVERT, ordinal, 0, 0L),
                        null, List.of(tr("invert.hint")));
            }
            textFit(does(ctl), doesX, ry + 3, rx1 - doesX, on ? TEXT : TEXT_DIM);
        });
    }

    private String does(Control ctl) {
        if (ctl.isAux()) {
            List<String> parts = new ArrayList<>();
            PartInput in = PartInput.of(ctl);
            for (int i = 0; i < v.surfaces().size(); i++) {
                AerialView.Surface s = v.surfaces().get(i);
                if (s.role() == AerialSetup.SurfaceRole.TOOL.ordinal() && s.input() == in.ordinal()) parts.add("S" + (i + 1));
            }
            for (int i = 0; i < v.tools().size(); i++) if (v.tools().get(i).input() == in.ordinal()) parts.add(toolName(i));
            for (int i = 0; i < v.lights().size(); i++) {
                AirLight job = AirLight.byOrdinal(v.lights().get(i).job());
                if (job.control == ctl) parts.add("L" + (i + 1));
            }
            return parts.isEmpty() ? tr("does.nothing") : String.join(", ", parts);
        }
        return tr("does." + ctl.key());
    }

    private void keyCap(int x, int y, int capW, Keymap km, Control ctl, int slot, HeldKeys held) {
        int id = km.key(ctl, slot);
        boolean picking = captureControl == ctl && captureSlot == slot;
        boolean on = id != KeyIds.NONE && (held.has(id) || previewKeys.contains(id));
        boolean shared = id != KeyIds.NONE && km.slotsOf(id).size() > 1;
        String text;
        if (picking) text = (ticks / 8) % 2 == 0 ? "> ? <" : ">   <";
        else text = capLabel(id) + (shared ? "*" : "");
        List<String> tip = new ArrayList<>();
        tip.add(id == KeyIds.NONE ? tr("key.none_tip") : tr("key.is", keyLabel(id)));
        if (shared) {
            List<String> others = new ArrayList<>();
            for (Keymap.Slot sl : km.slotsOf(id)) if (sl.control() != ctl || sl.slot() != slot) others.add(controlName(sl.control()));
            tip.add(tr("key.shared", String.join(", ", others)));
        }
        if (id != KeyIds.NONE) {
            List<String> also = host.keyAlso(id);
            if (!also.isEmpty()) tip.add(tr("key.also", String.join(", ", also)));
        }
        tip.add(tr("key.cap_hint"));
        String shown = fit(text, capW - 6);
        boolean isHot = hot(x, y, capW, BUTTON_H);

        if (on || picking) {
            raised(x, y + 1, capW, BUTTON_H - 1, picking ? BRASS : 0xFFFFFFFF, picking ? BRASS_HI : 0xFFFFFFFF,
                    picking ? BRASS_DIM : 0xFFC6C6C6);
            c.text(shown, x + (capW - host.width(shown)) / 2, y + 4, picking ? ON_BRASS : ON_CASING);
        } else {
            raised(x, y, capW, BUTTON_H, isHot ? BUTTON_HOT : BUTTON, isHot ? BUTTON_HOT_LIGHT : BUTTON_LIGHT,
                    isHot ? BUTTON_HOT_SHADE : BUTTON_SHADE);
            c.fill(x + 1, y + BUTTON_H - 3, x + capW - 1, y + BUTTON_H - 1, BUTTON_SHADE);
            if (isHot) outline(x, y, capW, BUTTON_H, CASING_LIGHT);
            c.text(shown, x + (capW - host.width(shown)) / 2, y + 2, id == KeyIds.NONE ? ON_BUTTON_DEAD : ON_BUTTON);
        }
        hit(x, y, capW, BUTTON_H, () -> pick(ctl, slot), () -> send(AerialActionPacket.SET_KEY, ctl.ordinal(), KeyIds.NONE, slot), tip);
    }

    private boolean controlHeld(Control ctl, Keymap km, HeldKeys held) {
        for (int slot = 0; slot < 2; slot++) {
            int id = km.key(ctl, slot);
            if (id != KeyIds.NONE && (held.has(id) || previewKeys.contains(id))) return true;
        }
        return false;
    }

    private void shipPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y0 = top + CONTENT_Y;
        int y1 = top + h - PAD;
        int by = y1 - BUTTON_H;
        int split = x0 + (x1 - x0) * 46 / 100;
        noseCard(x0, y0, split - 3, by - 6);
        int ry = feelCard(split + 3, y0, x1);
        ry = statsCard(split + 3, ry + 5, x1, by - 6);
        housekeeping(x0, by, x1);
    }

    private void noseCard(int x0, int y0, int x1, int y1) {
        AerialView.Flight f = v.flight();
        String nose;
        if (v.noseAuto()) nose = v.noseTurn() == 0 ? tr("nose.text.auto") : tr("nose.text.auto.turned", v.noseTurn() * 90);
        else nose = v.noseTurn() == 0 ? tr("nose.text.facing") : tr("nose.text", v.noseTurn() * 90);
        int rowY = y1 - BUTTON_H;
        int textY = rowY - 4 - 2 * LINE;
        topView(x0, y0, x1, textY - 4, tr("top"));
        int ty = textY;
        List<String> lines = wrap(nose, x1 - x0);
        for (String line : lines.subList(0, Math.min(2, lines.size()))) {
            c.text(line, x0, ty, v.noseAuto() ? BRASS_HI : TEXT);
            ty += LINE;
        }
        if (v.onCraft() && Float.isFinite(f.altitude()) && lines.size() < 2) {
            textFit(tr("nose.heading", heading3(f.heading())), x0, ty, x1 - x0, TEXT_DIM);
        }
        int x = x0;
        x += button(x, rowY, tr("nose.left"), false, () -> send(AerialActionPacket.NOSE, 3, 0, 0L), List.of(tr("nose.hint"))) + 2;
        x += button(x, rowY, tr("nose.right"), false, () -> send(AerialActionPacket.NOSE, 1, 0, 0L), List.of(tr("nose.hint"))) + 2;
        if (x + host.width(tr("nose.auto")) + 12 <= x1) {
            button(x, rowY, tr("nose.auto"), v.noseAuto(), () -> send(AerialActionPacket.NOSE, 0, 1, 0L), List.of(tr("nose.auto.hint")));
        }
    }

    private int feelCard(int x0, int y0, int x1) {
        boolean jets = v.engines().stream().anyMatch(AerialView.Engine::thruster);
        int ch = CARD_HEAD + 4 + BUTTON_H + 4 + LINE + 4 + 12 + (jets ? LINE : 0);
        int body = card(x0, y0, x1 - x0, ch, tr("feel"));
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        List<List<String>> tips = new ArrayList<>();
        for (Feel feel : Feel.values()) {
            String key = "feel." + feel.name().toLowerCase(Locale.ROOT);
            labels.add(tr(key));
            final int ordinal = feel.ordinal();
            acts.add(() -> send(AerialActionPacket.FEEL, ordinal, 0, 0L));
            tips.add(List.of(tr(key + ".hint")));
        }
        segmented(x0 + 6, body, labels, v.feel(), acts, tips);
        Feel feel = Feel.byOrdinal(v.feel());
        int ry = body + BUTTON_H + 4;
        textFit(tr("feel.text", num1(feel.climb), num1(feel.accel), num0(feel.turn)), x0 + 6, ry, x1 - x0 - 12, TEXT_DIM);
        if (jets) {
            ry += LINE;
            textFit(tr("feel.jets", feel.jetTicksPerStep, num1(feel.jetSeconds())), x0 + 6, ry, x1 - x0 - 12, TEXT_DIM);
        }
        checkbox(x0 + 6, ry + LINE + 3, v.bell(), tr("bell"), () -> send(AerialActionPacket.BELL, 0, 0, 0L),
                List.of(tr("bell.hint")));
        return y0 + ch;
    }

    private int statsCard(int x0, int y0, int x1, int y1) {
        if (y1 - y0 < 40) return y0;
        int body = card(x0, y0, x1 - x0, y1 - y0, tr("ship"));
        AerialView.Learned l = v.learned();
        double loadShare = load();
        List<String[]> tiles = new ArrayList<>();
        tiles.add(new String[] {tr("stat.mass"), Float.isFinite(v.mass()) ? big(v.mass()) : "-"});
        tiles.add(new String[] {tr("stat.blocks"), v.blocks() >= 0 ? Integer.toString(v.blocks()) : "-"});
        tiles.add(new String[] {tr("stat.balloons"), Integer.toString(v.balloons().size())});
        tiles.add(new String[] {tr("stat.engines"), Integer.toString(v.engines().size())});
        tiles.add(new String[] {loadShare > 0.005 ? tr("stat.load") : tr("stat.hidden"),
                loadShare > 0.005 ? big(v.mass() * loadShare) : num0(l.hidden())});
        tiles.add(new String[] {tr("stat.drag"), num2(l.drag())});
        tiles.add(new String[] {tr("stat.wing"), num1(l.wing())});
        tiles.add(new String[] {tr("stat.turn"), big(l.turnDrag())});
        tiles.add(new String[] {tr("stat.props"), l.propsKnown() + " / " + l.props()});
        int cols = x1 - x0 >= 280 ? 4 : 3;
        int tw = (x1 - x0 - 12 - (cols - 1) * 4) / cols;
        int th = 22;
        int rows = Math.max(1, Math.min((tiles.size() + cols - 1) / cols, (y1 - 4 - body) / (th + 4)));
        for (int k = 0; k < Math.min(tiles.size(), rows * cols); k++) {
            int tx = x0 + 6 + (k % cols) * (tw + 4);
            int ty = body + (k / cols) * (th + 4);

            display(tx, ty, tw, th);
            textFit(tiles.get(k)[0], tx + 4, ty + 2, tw - 8, TEXT_FAINT);
            textFit(tiles.get(k)[1], tx + 4, ty + 12, tw - 8, k == 4 && loadShare > 0.005 ? WARN : TEXT);
        }
        hit(x0, body, x1 - x0, rows * (th + 4), null, null, List.of(tr("learned.text", num2(l.drag()), num1(l.wing()), num2(l.lean()),
                num0(l.hidden()), big(l.turnDrag()), l.propsKnown(), l.props())));
        return y1;
    }

    private void housekeeping(int x0, int by, int x1) {
        String[][] buttons = {
                {"rescan", Integer.toString(AerialActionPacket.RESCAN)},
                {"relearn", Integer.toString(AerialActionPacket.RELEARN)},
                {"forget", Integer.toString(AerialActionPacket.FORGET)},
                {"reset_parts", Integer.toString(AerialActionPacket.PARTS_RESET)},
                {"reset_keys", Integer.toString(AerialActionPacket.KEYS_RESET)}};
        int bx = x0;
        for (String[] b : buttons) {
            String label = tr(b[0]);
            boolean wipes = WIPES.contains(b[0]);
            int bw = (wipes ? Math.max(host.width(label), host.width(tr("confirm"))) : host.width(label)) + 12;
            if (bx + bw > x1) break;
            final int action = Integer.parseInt(b[1]);
            if (wipes) confirmButton(bx, by, bw, b[0], () -> send(action, 0, 0, 0L));
            else button(bx, by, bw, label, false, () -> send(action, 0, 0, 0L), null, List.of(tr(b[0] + ".hint")));
            bx += bw + 4;
        }
    }

    static final Set<String> WIPES = Set.of("forget", "reset_parts", "reset_keys");

    static final int ARM_TICKS = 60;

    private void confirmButton(int x, int y, int bw, String key, Runnable action) {
        boolean live = key.equals(armed) && ticks < armedUntil;
        button(x, y, bw, live ? tr("confirm") : tr(key), live, () -> {
            if (key.equals(armed) && ticks < armedUntil) {
                armed = null;
                action.run();
            } else {
                armed = key;
                armedUntil = ticks + ARM_TICKS;
            }
        }, null, List.of(tr(key + ".hint"), tr("confirm.hint")));
    }

    void arm(String key) {
        armed = key;
        armedUntil = ticks + ARM_TICKS;
    }

    void pickEngine(long pos) {
        pickedEngine = pos;
    }

    private int inputButtonWidth() {
        int wd = 0;
        for (PartInput in : PartInput.values()) wd = Math.max(wd, host.width(inputName(in)) + 12);
        return wd;
    }

    private String inputName(PartInput in) {
        return in == PartInput.OFF ? tr("input.off") : controlName(in.control());
    }

    void openInputMenu(int x, int y, long pos, int current, boolean machine) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        int currentRow = -1;
        for (PartInput in : PartInput.values()) {
            if (in == PartInput.THROTTLE || in == PartInput.LIFT || in == PartInput.STEER) continue;
            if (in.ordinal() == current) currentRow = labels.size();
            labels.add(inputName(in));
            final int ordinal = in.ordinal();
            actions.add(() -> send(machine ? AerialActionPacket.TOOL_INPUT : AerialActionPacket.SURFACE_INPUT, ordinal, 0, pos));
        }
        openPopup(x, y, labels, actions, currentRow);
    }

    public boolean mouseClicked(double x, double y, int button) {
        clickX = x;
        clickY = y;
        if (captureControl != null && button >= 2) {
            finishCapture(host.resolveMouse(button));
            return true;
        }
        if (popup != null) return click(x, y, button);
        boolean wasPicking = captureControl != null;
        captureControl = null;
        captureNote = null;
        return click(x, y, button) || wasPicking;
    }

    public boolean keyPressed(int glfwKey, int scanCode) {
        if (captureControl != null) {
            if (glfwKey == KEY_ESCAPE) {
                captureControl = null;
                captureNote = null;
            } else if (glfwKey == 259 || glfwKey == 261) {
                send(AerialActionPacket.SET_KEY, captureControl.ordinal(), KeyIds.NONE, captureSlot);
                captureControl = null;
                captureNote = null;
            } else {
                finishCapture(host.resolveKey(glfwKey, scanCode));
            }
            return true;
        }
        if (popup != null && glfwKey == KEY_ESCAPE) {
            popup = null;
            return true;
        }
        int id = host.resolveKey(glfwKey, scanCode);
        if (id != KeyIds.NONE) previewKeys.add(id);
        return false;
    }

    public void keyReleased(int glfwKey, int scanCode) {
        previewKeys.remove(host.resolveKey(glfwKey, scanCode));
    }

    void pick(Control ctl, int slot) {
        captureControl = ctl;
        captureSlot = slot;
        captureNote = null;
        popup = null;
    }

    private void finishCapture(int id) {
        if (captureControl == null) return;
        if (id == KeyIds.NONE) {
            captureNote = tr("capture.unknown");
            return;
        }
        if (host.keyLeavesSeat(id)) {
            captureNote = tr("capture.sneak");
            return;
        }
        send(AerialActionPacket.SET_KEY, captureControl.ordinal(), id, captureSlot);
        captureControl = null;
        captureNote = null;
    }

    private void send(int action, int a, int b, long target) {
        host.send(action, a, b, target);
    }

    static final int MODE_ANCHOR = 2;
    static final int MODE_GO_TO = 4;

    static String modeKey(int mode) {
        return switch (mode) {
            case 1 -> "helm";
            case 2 -> "anchor";
            case 3 -> "keep_course";
            case 4 -> "go_to";
            case 5 -> "moored";
            default -> "off";
        };
    }

    String controlName(Control ctl) {
        return host.tr(CONTROL + ctl.key());
    }

    String capLabel(int id) {
        if (id == KeyIds.NONE) return "-";
        return KeyText.shortName(host.keyName(id));
    }

    String keyLabel(int id) {
        String name = host.keyName(id);
        return name.isEmpty() ? tr("key.unbound") : name;
    }

    String capsText(Keymap km, Control ctl) {
        String plus = capLabel(km.plus(ctl));
        if (!ctl.isAxis() || km.minus(ctl) == KeyIds.NONE) return km.plus(ctl) == KeyIds.NONE ? capLabel(km.minus(ctl)) : plus;
        if (km.plus(ctl) == KeyIds.NONE) return capLabel(km.minus(ctl));
        return plus + "/" + capLabel(km.minus(ctl));
    }

    private String where(double lon) {
        if (!Double.isFinite(lon) || Math.abs(lon) < 0.5) return tr("where.mid");
        return lon > 0 ? tr("where.fore", num1(lon)) : tr("where.aft", num1(-lon));
    }

    private String place(double lon, double lat, double up) {
        List<String> bits = new ArrayList<>();
        bits.add(where(lon));
        if (Double.isFinite(lat) && Math.abs(lat) >= 1.5) bits.add(lat > 0 ? tr("where.right", num0(lat)) : tr("where.left", num0(-lat)));
        if (Double.isFinite(up) && Math.abs(up) >= 2.5) bits.add(up > 0 ? tr("where.high") : tr("where.low"));
        return String.join(", ", bits);
    }

    private String trimText(double trim) {
        long p = Math.round(trim * 100.0);
        return (p > 0 ? "+" : "") + p + "%";
    }

    static String heading3(double h) {
        if (!Double.isFinite(h)) return "---";
        long d = Math.round(((h % 360.0) + 360.0) % 360.0) % 360;
        return String.format(Locale.ROOT, "%03d", d);
    }

    static String num0(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%.0f", v) : "-";
    }

    static String num1(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%.1f", v) : "-";
    }

    static String num2(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%.2f", v) : "-";
    }

    static String signed0(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%+.0f", v) : "-";
    }

    static String signed1(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%+.1f", v) : "-";
    }

    static String pct(double share) {
        return Double.isFinite(share) ? Math.round(share * 100.0) + "%" : "-";
    }

    static String big(double v) {
        if (!Double.isFinite(v)) return "-";
        double a = Math.abs(v);
        if (a >= 1.0e6) return String.format(Locale.ROOT, "%.1fM", v / 1.0e6);
        if (a >= 1.0e4) return String.format(Locale.ROOT, "%.0fk", v / 1.0e3);
        if (a >= 1.0e3) return String.format(Locale.ROOT, "%.1fk", v / 1.0e3);
        return String.format(Locale.ROOT, "%.0f", v);
    }

    static int[] columns(int x0, int x1, int[] widths, int gap) {
        int fixed = 0;
        int flex = 0;
        for (int wd : widths) {
            if (wd < 0) flex++;
            else fixed += wd;
        }
        int spare = Math.max(0, (x1 - x0) - fixed - gap * (widths.length - 1));
        int[] out = new int[widths.length];
        int x = x0;
        for (int i = 0; i < widths.length; i++) {
            out[i] = x;
            int wd = widths[i] < 0 ? (flex == 0 ? 0 : spare / flex) : widths[i];
            x += wd + gap;
        }
        return out;
    }
}
