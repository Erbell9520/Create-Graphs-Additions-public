package com.createstats.vccmk4.console;

import com.createstats.surveyor.core.Comfort;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.vccmk4.core.AxleRole;
import com.createstats.vccmk4.core.BodyRole;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.DriveModel;
import com.createstats.vccmk4.core.HeldKeys;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetMixer;
import com.createstats.vccmk4.core.JetRole;
import com.createstats.vccmk4.core.JetSupply;
import com.createstats.vccmk4.core.KeyIds;
import com.createstats.vccmk4.core.Keymap;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkSource;
import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.core.SteerPreset;
import com.createstats.vccmk4.core.TrailerMode;
import com.createstats.vccmk4.core.VectorGimbal;
import com.createstats.vccmk4.network.RouteSnapshot;
import com.createstats.vccmk4.network.Vcc4ActionPacket;
import com.createstats.vccmk4.network.Vcc4Snapshot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class Vcc4Console {
    public static final int MIN_W = 440;
    public static final int MIN_H = 256;
    public static final int MAX_W = 600;
    public static final int MAX_H = 372;

    public static final int MARGIN = 12;

    static final int OUTLINE = 0xFF000000;
    static final int HIGHLIGHT = 0xFFFFFFFF;
    static final int FACE = 0xFFC6C6C6;
    static final int FACE_HOT = 0xFFD8D8D8;
    static final int RECESSED = 0xFF8B8B8B;
    static final int SHADOW = 0xFF555555;
    static final int LABEL = 0xFF404040;
    static final int DIM = 0xFF6B6B6B;
    static final int INK = 0xFF202020;
    static final int GOOD = 0xFF2E7D32;
    static final int WARN = 0xFFA02814;
    static final int BOARD_FACE = 0xFF9C9C9C;
    static final int JET_ON = 0xFFC0392B;
    static final int WHEEL_DRIVEN = 0xFF303030;

    static final int LAMP_ON = 0xFFE0A000;

    static final int LAMP_WHITE = 0xFFF4EEDC;
    static final int LAMP_RED = 0xFFE0301E;

    static int litColour(Vcc4Snapshot.Link l) {
        return switch (l.lampKind()) {
            case 0 -> LAMP_WHITE;
            case 1 -> LAMP_RED;
            default -> LAMP_ON;
        };
    }

    static final String L = "gui.create_stats.vcc_mk4.";

    static final int PAD = 8;
    static final int TAB_Y = 19;
    static final int TAB_H = 14;
    static final int CONTENT_Y = 40;
    static final int BUTTON_H = 14;
    static final int LINE = 10;

    private static final int KEY_ESCAPE = 256;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int MOUSE_RIGHT = 1;

    public enum Page {
        BOARD("board"), WHEELS("wheels"), KEYS("keys"), JETS("jets"), TOOLS("tools"), LIGHTS("lights"), TRAILERS("trailers"),
        PARTS("parts"), ROUTE("route");

        final String key;

        Page(String key) {
            this.key = key;
        }
    }

    private record Hit(int x, int y, int w, int h, @Nullable Runnable left, @Nullable Runnable right,
                       @Nullable List<String> tip, boolean popup) {
        boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    private record Popup(int x, int y, int w, List<String> labels, List<Runnable> actions, int current) {
    }

    private record ScrollArea(int x0, int y0, int x1, int y1, Page page) {
    }

    private final ConsoleHost host;
    private Vcc4Snapshot s;
    private Page page;
    private final int[] scroll = new int[Page.values().length];
    @Nullable private Control captureControl;
    private int captureSlot;
    @Nullable private String captureNote;
    @Nullable private Popup popup;
    private final Set<Integer> previewKeys = new TreeSet<>();
    private int lastPreview = KeyIds.NONE;
    private int ticks;

    private ConsoleCanvas c;
    private int left;
    private int top;
    private int w;
    private int h;
    private int mx;
    private int my;
    private int rowH;
    private final List<Hit> hits = new ArrayList<>();
    private final List<ScrollArea> scrollAreas = new ArrayList<>();
    private List<String> tooltip = List.of();
    private Set<PartIndex.Ref> focus = Set.of();

    public Vcc4Console(ConsoleHost host, Vcc4Snapshot snapshot, Page page) {
        this.host = host;
        this.s = snapshot;
        this.page = page == null ? Page.BOARD : page;
    }

    public void accept(Vcc4Snapshot snapshot) {
        this.s = snapshot;
    }

    public Vcc4Snapshot snapshot() {
        return s;
    }

    public Page page() {
        return page;
    }

    public void setPage(Page p) {
        page = p;
        popup = null;
        captureControl = null;
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
    }

    public List<String> tooltip() {
        return tooltip;
    }

    public void render(ConsoleCanvas canvas, int screenW, int screenH, int mouseX, int mouseY) {
        c = canvas;
        w = width(screenW);
        h = height(screenH);
        left = (screenW - w) / 2;
        top = (screenH - h) / 2;
        mx = mouseX;
        my = mouseY;
        rowH = h >= 320 ? 18 : 16;
        hits.clear();
        scrollAreas.clear();
        tooltip = List.of();
        focus = heldFocus();

        bevel(left, top, w, h, false);
        header();
        switch (page) {
            case BOARD -> boardPage();
            case WHEELS -> wheelsPage();
            case KEYS -> keysPage();
            case JETS -> jetsPage();
            case TOOLS -> toolsPage();
            case LIGHTS -> lightsPage();
            case TRAILERS -> trailersPage();
            case PARTS -> partsPage();
            case ROUTE -> routePage();
        }
        if (popup != null) drawPopup();
        pickTooltip();
    }

    private void pickTooltip() {
        boolean popupOpen = popup != null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (popupOpen && !hit.popup()) continue;
            if (hit.tip() != null && !hit.tip().isEmpty() && hit.contains(mx, my)) {
                tooltip = wrapAll(hit.tip(), 220);
                return;
            }
        }
    }

    private Set<PartIndex.Ref> heldFocus() {
        HeldKeys held = s.heldKeys();
        Keymap km = Keymap.fromInts(s.keymap());
        Set<PartIndex.Ref> out = new LinkedHashSet<>();
        for (Control ctl : Control.DISPLAY) {
            if (controlHeld(ctl, km, held)) out.addAll(PartIndex.partsOf(ctl, s));
        }
        return out;
    }

    private boolean controlHeld(Control ctl, Keymap km, HeldKeys held) {
        for (int slot = 0; slot < 2; slot++) {
            int id = km.key(ctl, slot);
            if (id != KeyIds.NONE && (held.has(id) || previewKeys.contains(id))) return true;
        }
        return false;
    }

    private void header() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        String status;
        int statusColour;
        RouteRunner.State routing = RouteRunner.State.byOrdinal(s.route().state());
        if (s.driver().isEmpty() && s.route().assigned() && routing.drives()) {
            status = tr("route.header", routeWord(s.route().state()));
            statusColour = routeColour(routing) == WARN ? WARN : GOOD;
        } else if (s.driver().isEmpty()) {
            status = tr(s.parked() ? "no_driver_parked" : "no_driver");
            statusColour = DIM;
        } else {
            status = s.youDrive() ? tr("driver_you") : tr("driver", s.driver());
            statusColour = GOOD;
        }
        status = fit(status, w / 3);
        int sw = host.width(status);
        int sx = x1 - sw;
        c.fill(sx - 9, top + 8, sx - 3, top + 14, OUTLINE);
        c.fill(sx - 8, top + 9, sx - 4, top + 13, statusColour == GOOD ? GOOD : RECESSED);
        c.text(status, sx, top + 7, statusColour);
        String title = tr("title");
        if (host.width(title) > sx - 14 - x0) title = tr("title_short");
        c.text(fit(title, sx - 14 - x0), x0, top + 7, LABEL);

        int tx = x0;
        for (Page p : Page.values()) {
            String name = tr("tab." + p.key);
            boolean alert = p == Page.PARTS && !s.problems().isEmpty();
            int tw = host.width(name) + 12 + (alert ? host.width("!") + 3 : 0);
            tab(tx, top + TAB_Y, tw, name, p == page, alert);
            final Page target = p;
            hit(tx, top + TAB_Y, tw, TAB_H, () -> setPage(target), null, List.of(tr("tab." + p.key + ".hint")));
            tx += tw + 2;
        }
        c.fill(x0, top + TAB_Y + TAB_H, x1, top + TAB_Y + TAB_H + 1, SHADOW);
        if (page != Page.BOARD && s.onCraft()) {
            List<String> bits = new ArrayList<>();
            if (s.gear() != '-') bits.add(String.valueOf(s.gear()));
            bits.add(tr("speed_value", String.format(Locale.ROOT, "%.1f", s.speed())));
            if (s.braking()) bits.add(tr("brake_pip"));
            if (s.boosting()) bits.add(tr("boost_pip"));
            String live = String.join("  ", bits);
            int lw = host.width(live);
            if (x1 - lw > tx + 8) c.text(live, x1 - lw, top + TAB_Y + 3, s.braking() || s.boosting() ? INK : DIM);
        }
    }

    private void boardPage() {
        int x0 = left + PAD;
        int y0 = top + CONTENT_Y;
        int x1 = left + w - PAD;
        int y1 = top + h - PAD;
        int colW = clampInt((x1 - x0) * 44 / 100, 180, 262);
        int bx1 = x1 - colW - 8;
        int rx0 = bx1 + 8;

        List<Control> rows = legendControls();
        int legendTop = legendTop(y0);
        Control hovered = null;
        for (int i = 0; i < rows.size(); i++) {
            int ry = legendTop + i * 11;
            if (mx >= rx0 && mx < x1 && my >= ry - 1 && my < ry + 10) hovered = rows.get(i);
        }
        if (hovered != null) {
            Set<PartIndex.Ref> f = new LinkedHashSet<>(focus);
            f.addAll(PartIndex.partsOf(hovered, s));
            focus = f;
        }

        BoardView.draw(this, x0, y0, bx1, y1);
        readouts(rx0, y0, x1, y1, rows, legendTop, hovered);
    }

    private static int legendTop(int y0) {
        return y0 + 11 + 12 + 11 + 11 + 16 + 5 + LINE;
    }

    private List<Control> legendControls() {
        Keymap km = Keymap.fromInts(s.keymap());
        List<Control> out = new ArrayList<>();
        for (Control ctl : Control.DISPLAY) {
            boolean moves = !PartIndex.partsOf(ctl, s).isEmpty();
            boolean advertised = km.bound(ctl) && ctl != Control.LIFT && ctl != Control.SLIDE
                    && ctl != Control.AUX_C && ctl != Control.AUX_D;
            if (moves || advertised) out.add(ctl);
        }
        return out;
    }

    private void readouts(int x0, int y0, int x1, int y1, List<Control> rows, int legendTop, @Nullable Control hovered) {
        String profile = profile();
        if (!profile.isEmpty()) {
            c.text(fit(profile, x1 - x0), x0, y0, INK);
            hit(x0, y0 - 1, Math.min(x1 - x0, host.width(profile)), 10, () -> setPage(Page.WHEELS), null,
                    List.of(profile, tr("profile.hint")));
        }
        int y = y0 + 11;
        int labelW = Math.max(host.width(tr("speed")), Math.max(host.width(tr("drive")), host.width(tr("steer")))) + 6;
        c.text(tr("speed"), x0, y, DIM);
        c.text(tr("speed_value", String.format(Locale.ROOT, "%.1f", s.speed())), x0 + labelW, y, LABEL);
        gearBox(x1, y);
        y += 12;
        bar(x0, y, labelW, x1, tr("drive"), s.throttle());
        y += 11;
        bar(x0, y, labelW, x1, tr("steer"), s.steer());
        y += 11;
        int px = x0;
        px = pip(px, y, tr("brake_pip"), s.braking()) + 3;
        px = pip(px, y, tr("boost_pip"), s.boosting()) + 3;
        if (hasJob(JetMixer.Job.LIFT)) pip(px, y, tr("lift_pip"), controlValue(Control.LIFT) != 0f);
        y += 16;
        c.fill(x0, y, x1, y + 1, RECESSED);
        y += 5;
        c.text(tr("legend_title"), x0, y, DIM);
        String hint = tr("legend_hint");
        if (host.width(tr("legend_title")) + 8 + host.width(hint) <= x1 - x0) c.text(hint, x1 - host.width(hint), y, DIM);

        Keymap km = Keymap.fromInts(s.keymap());
        HeldKeys held = s.heldKeys();
        int capsW = 0;
        int nameW = 0;
        for (Control ctl : rows) {
            capsW = Math.max(capsW, host.width(capsText(km, ctl)));
            nameW = Math.max(nameW, host.width(controlName(ctl)));
        }
        capsW = Math.min(capsW, 70);
        for (int i = 0; i < rows.size(); i++) {
            Control ctl = rows.get(i);
            int ry = legendTop + i * 11;
            if (ry + 9 > y1 - 22) {
                c.text(fit(tr("legend_more", rows.size() - i), x1 - x0), x0, ry, DIM);
                break;
            }
            boolean on = controlHeld(ctl, km, held);
            boolean hot = ctl == hovered;
            if (on) well(x0 - 2, ry - 2, x1 - x0 + 4, 12, INK);
            else if (hot) c.fill(x0 - 2, ry - 2, x1 + 2, ry + 10, FACE_HOT);
            String caps = fit(capsText(km, ctl), capsW);
            boolean unbound = !km.bound(ctl);

            int unboundColour = ctl == Control.BEAM ? DIM : WARN;
            c.text(unbound ? tr("legend_no_key") : caps, x0, ry, on ? HIGHLIGHT : (unbound ? unboundColour : INK));
            c.text(controlName(ctl), x0 + capsW + 6, ry, on ? HIGHLIGHT : LABEL);
            String moves = movesShort(ctl);
            int mxs = x0 + capsW + 6 + nameW + 6;
            if (mxs < x1 - 10) c.text(fit(moves, x1 - mxs), mxs, ry, on ? HIGHLIGHT : DIM);
            hit(x0 - 2, ry - 1, x1 - x0 + 4, 11, () -> setPage(Page.KEYS), null, controlTip(ctl, km));
        }

        int afterLegend = legendTop + Math.min(rows.size(), Math.max(0, (y1 - 22 - legendTop) / 11)) * 11 + 6;
        problemsPreview(x0, afterLegend, x1, y1 - 30);

        List<String> lines;
        int colour;
        boolean problems = !s.problems().isEmpty();
        if (!s.onCraft()) {
            lines = wrap(tr("not_on_craft_short"), x1 - x0);
            colour = WARN;
        } else if (problems) {
            lines = wrap(tr("status.problems", s.problems().size()), x1 - x0);
            colour = WARN;
        } else if (s.driver().isEmpty()) {
            lines = wrap(tr("status.sit"), x1 - x0);
            colour = DIM;
        } else if (s.youDrive()) {
            lines = wrap(tr("status.live"), x1 - x0);
            colour = GOOD;
        } else {
            lines = wrap(tr("status.ready"), x1 - x0);
            colour = GOOD;
        }
        lines = lines.subList(0, Math.min(2, lines.size()));
        int sy = y1 - lines.size() * LINE + 1;
        c.fill(x0, sy - 4, x1, sy - 3, RECESSED);
        for (String line : lines) {
            c.text(line, x0, sy, colour);
            sy += LINE;
        }
        if (problems && s.onCraft()) hit(x0, y1 - lines.size() * LINE - 2, x1 - x0, lines.size() * LINE + 2,
                () -> setPage(Page.PARTS), null, List.of(tr("status.problems.hint")));
    }

    private void gearBox(int x1, int y) {
        char g = s.gear();
        if (g == '-' || !s.onCraft()) return;
        String letter = String.valueOf(g);
        int bw = host.width(letter) + 6;
        int bx = x1 - bw;
        boolean alert = g == 'H' || g == 'B' || g == 'P';
        well(bx, y - 2, bw, 11, alert ? SHADOW : RECESSED);
        c.text(letter, bx + 3, y, HIGHLIGHT);
        hit(bx, y - 2, bw, 11, null, null, List.of(tr("gear." + gearKey(g))));
    }

    private static String gearKey(char g) {
        return switch (g) {
            case 'D' -> "drive";
            case 'R' -> "reverse";
            case 'B' -> "brake";
            case 'H' -> "hold";
            case 'P' -> "park";
            default -> "neutral";
        };
    }

    String profile() {
        if (!s.onCraft()) return "";
        int wheels = 0;
        int driven = 0;
        for (Vcc4Snapshot.Wheel wh : s.wheels()) {
            if (wh.body() != 0) continue;
            wheels++;
            if (PartIndex.drives(s, wh)) driven++;
        }
        String text;
        if (wheels == 0) {
            text = tr(s.jets().isEmpty() ? "profile.empty" : "profile.jets");
        } else {
            SteerPreset p = SteerPreset.byOrdinal(s.preset());
            String steering = p == SteerPreset.NONE && jointSteers() ? tr("profile.joint") : tr("profile." + p.key());
            text = tr(s.presetAuto() ? "profile.wheeled_auto" : "profile.wheeled", wheels + "x" + driven, steering);
        }
        int jointed = 0;
        int trailers = 0;
        for (Vcc4Snapshot.Body b : s.bodies()) {
            if (b.roleOf() == BodyRole.PART) jointed++;
            else if (b.takesMode()) trailers++;
        }
        if (jointSteers()) text += tr("profile.articulated");
        else if (jointed > 0) text += tr("profile.bodies", jointed);
        if (trailers > 0) text += tr(trailers == 1 ? "profile.trailer" : "profile.trailers", trailers);
        return text;
    }

    private boolean jointSteers() {
        for (Vcc4Snapshot.Swivel sw : s.swivels()) {
            boolean joint = (sw.body() == 0 && sw.carries() > 0) || (sw.body() > 0 && sw.carries() == 0);
            if (joint && PartInput.byOrdinal(sw.input()) == PartInput.STEER) return true;
        }
        return false;
    }

    private void problemsPreview(int x0, int y0, int x1, int y1) {
        if (s.problems().isEmpty() || y1 - y0 < 3 * LINE) return;
        c.fill(x0, y0, x1, y0 + 1, RECESSED);
        int y = y0 + 4;
        c.text(tr("section.problems", s.problems().size()), x0, y, DIM);
        y += LINE + 1;
        for (String problem : s.problems()) {
            List<String> lines = wrap("! " + problem, x1 - x0);
            if (y + lines.size() * LINE > y1) {
                if (y + LINE <= y1) c.text(tr("problems_more"), x0, y, DIM);
                break;
            }
            int top0 = y;
            for (String line : lines) {
                c.text(line, x0, y, WARN);
                y += LINE;
            }
            hit(x0, top0 - 1, x1 - x0, y - top0, () -> setPage(Page.PARTS), null, List.of(tr("status.problems.hint")));
            y += 2;
        }
    }

    private void bar(int x0, int y, int labelW, int x1, String name, float value) {
        c.text(name, x0, y, DIM);
        int bx = x0 + labelW;
        int bw = Math.max(20, x1 - bx);
        well(bx, y, bw, 8, RECESSED);
        int mid = bx + bw / 2;
        int half = bw / 2 - 1;
        int v = Math.round(clamp(value, -1f, 1f) * half);
        if (v > 0) c.fill(mid, y + 2, mid + v, y + 7, INK);
        else if (v < 0) c.fill(mid + v, y + 2, mid, y + 7, INK);
        c.fill(mid, y + 1, mid + 1, y + 8, SHADOW);
    }

    private int pip(int x, int y, String text, boolean on) {
        int pw = host.width(text) + 8;
        well(x, y, pw, 11, on ? INK : RECESSED);
        c.text(text, x + 4, y + 2, on ? HIGHLIGHT : SHADOW);
        return x + pw;
    }

    private void wheelsPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y = top + CONTENT_Y;
        int y1 = top + h - PAD;

        String label = tr(s.presetAuto() ? "steering_label_auto" : "steering_label");
        int labelW = host.width(label);
        c.text(label, x0, y + 3, s.presetAuto() ? GOOD : DIM);
        hit(x0, y, labelW, BUTTON_H, s.presetAuto() ? null : () -> send(Vcc4ActionPacket.SET_PRESET, Vcc4ActionPacket.PRESET_AUTO, 0, 0L),
                null, List.of(tr(s.presetAuto() ? "steering_label_auto.hint" : "steering_label.hint")));
        int bx = x0 + labelW + 6;
        SteerPreset current = SteerPreset.byOrdinal(s.preset());
        for (SteerPreset p : SteerPreset.DISPLAY) {
            String name = tr("preset." + p.key());
            int bw = host.width(name) + 12;
            if (bx + bw > x1) {
                bx = x0 + labelW + 6;
                y += BUTTON_H + 3;
            }
            final int ordinal = p.ordinal();
            button(bx, y, bw, name, p == current, () -> send(Vcc4ActionPacket.SET_PRESET, ordinal, 0, 0L), null,
                    List.of(tr("preset." + p.key() + ".hint")));
            bx += bw + 3;
        }
        if (current == SteerPreset.CUSTOM) {
            String custom = tr("preset.custom");
            if (bx + host.width(custom) <= x1) c.text(custom, bx + 3, y + 3, INK);
        }
        y += BUTTON_H + 6;

        DriveModel.Options o = s.options();
        List<Group> groups = new ArrayList<>();
        groups.add(stepperGroup(tr("lock"), tr("deg", o.lockDeg()), tr("deg", 30),
                () -> option(Vcc4ActionPacket.OPTION_LOCK, o.lockDeg() - 2), () -> option(Vcc4ActionPacket.OPTION_LOCK, o.lockDeg() + 2),
                tr("lock.hint")));
        groups.add(stepperGroup(tr("steer_speed"), tr("ticks", o.steerTicks()), tr("ticks", 30),
                () -> option(Vcc4ActionPacket.OPTION_STEER_TICKS, o.steerTicks() - 2),
                () -> option(Vcc4ActionPacket.OPTION_STEER_TICKS, o.steerTicks() + 2), tr("steer_speed.hint")));
        String ramp = o.throttleTicks() == 0 ? tr("instant") : tr("ticks", o.throttleTicks());
        groups.add(stepperGroup(tr("throttle_ramp"), ramp, tr("ticks", 60),
                () -> option(Vcc4ActionPacket.OPTION_THROTTLE_TICKS, o.throttleTicks() - 2),
                () -> option(Vcc4ActionPacket.OPTION_THROTTLE_TICKS, o.throttleTicks() + 2), tr("throttle_ramp.hint")));

        String release = o.releaseTicks() == DriveModel.Options.AS_RAMP ? tr("as_ramp")
                : o.releaseTicks() == 0 ? tr("instant") : tr("ticks", o.releaseTicks());
        String releaseWidest = host.width(tr("as_ramp")) > host.width(tr("ticks", 60)) ? tr("as_ramp") : tr("ticks", 60);
        groups.add(stepperGroup(tr("throttle_release"), release, releaseWidest,
                () -> option(Vcc4ActionPacket.OPTION_RELEASE_TICKS, o.releaseStep(-2)),
                () -> option(Vcc4ActionPacket.OPTION_RELEASE_TICKS, o.releaseStep(2)), tr("throttle_release.hint")));
        groups.add(stepperGroup(tr("top_speed"), tr("rpm", o.maxRpm()), tr("rpm", 256),
                () -> option(Vcc4ActionPacket.OPTION_MAX_RPM, o.maxRpm() - 16),
                () -> option(Vcc4ActionPacket.OPTION_MAX_RPM, o.maxRpm() + 16), tr("top_speed.hint")));
        groups.add(stepperGroup(tr("reverse"), tr("percent", o.reversePercent()), tr("percent", 100),
                () -> option(Vcc4ActionPacket.OPTION_REVERSE, o.reversePercent() - 10),
                () -> option(Vcc4ActionPacket.OPTION_REVERSE, o.reversePercent() + 10), tr("reverse.hint")));
        groups.add(checkGroup(tr("brake_then_reverse"), o.brakeThenReverse(),
                () -> option(Vcc4ActionPacket.OPTION_BRAKE_THEN_REVERSE, o.brakeThenReverse() ? 0 : 1), tr("brake_then_reverse.hint")));
        groups.add(checkGroup(tr("speed_steer"), o.speedSteer(),
                () -> option(Vcc4ActionPacket.OPTION_SPEED_STEER, o.speedSteer() ? 0 : 1), tr("speed_steer.hint")));
        groups.add(checkGroup(tr("hold"), o.holdWhenStopped(),
                () -> option(Vcc4ActionPacket.OPTION_HOLD, o.holdWhenStopped() ? 0 : 1), tr("hold.hint")));
        groups.add(checkGroup(tr("park_brake"), o.parkBrake(),
                () -> option(Vcc4ActionPacket.OPTION_PARK_BRAKE, o.parkBrake() ? 0 : 1), tr("park_brake.hint")));
        int optionsTop = flow(groups, x0, x1, y1);
        c.fill(x0, optionsTop - 5, x1, optionsTop - 4, RECESSED);

        int others = 0;
        for (Vcc4Snapshot.Wheel wh : s.wheels()) if (wh.body() != 0) others++;
        int tableBottom = optionsTop - 8;
        if (others > 0) {
            tableBottom -= LINE + 2;
            String note = tr("wheels.other_bodies", others);
            c.text(fit(note, x1 - x0), x0, tableBottom + 3, DIM);
            if (host.width(note) > x1 - x0) hit(x0, tableBottom + 2, x1 - x0, LINE, null, null, List.of(note));
        }

        int n = s.axleZ().length;
        String[] axleNames = new String[n];
        int axleW = host.width(tr("col.axle"));
        for (int i = 0; i < n; i++) {
            String suffix = n > 1 && i == 0 ? tr("axle_front") : (n > 1 && i == n - 1 ? tr("axle_rear") : "");
            axleNames[i] = tr("axle", i + 1) + suffix;
            axleW = Math.max(axleW, host.width(axleNames[i]));
        }
        int roleBw = 0;
        for (AxleRole r : AxleRole.values()) roleBw = Math.max(roleBw, host.width(tr("role." + r.key())) + 12);
        int roleW = Math.max(host.width(tr("col.steering")), roleBw * 3 + 4);
        int drivenW = Math.max(host.width(tr("col.driven")), 11);
        int wheelsW = Math.max(host.width(tr("col.wheels")), host.width("16"));
        int angleW = Math.max(host.width(tr("col.angle")), host.width(tr("deg", "+30")));
        int[] cols = columns(x0, x1 - 8, new int[]{axleW, roleW, drivenW, wheelsW, -1, angleW}, 10);
        int govX = cols[4];
        int govW = cols[5] - 10 - govX;
        boolean showAngle = govW >= 30;
        if (!showAngle) govW = x1 - 8 - govX;

        c.text(tr("col.axle"), cols[0], y, DIM);
        c.text(tr("col.steering"), cols[1], y, DIM);
        c.text(tr("col.driven"), cols[2], y, DIM);
        c.text(tr("col.wheels"), cols[3], y, DIM);
        if (govW >= 30) c.text(fit(tr("col.governors"), govW), govX, y, DIM);
        if (showAngle) c.text(tr("col.angle"), cols[5], y, DIM);
        y += LINE + 2;
        if (n == 0) {
            c.text(fit(tr(s.onCraft() ? "no_axles" : "not_on_craft_short"), x1 - x0), x0, y + 3, DIM);
            return;
        }
        final int roleX = cols[1];
        final int drivenX = cols[2];
        final int wheelsX = cols[3];
        final int angleX = cols[5];
        final int gW = govW;
        final int bw = roleBw;
        list(x0, y, x1, tableBottom, n, Page.WHEELS, (i, rx0, ry, rx1) -> {
            boolean lit = focusHasAxle(i);
            c.text(axleNames[i], rx0, ry + 3, lit ? INK : LABEL);
            int role = i < s.roles().length ? s.roles()[i] : AxleRole.FIXED.ordinal();
            int x = roleX;
            for (AxleRole r : AxleRole.values()) {
                final int axle = i;
                final int ordinal = r.ordinal();
                button(x, ry, bw, tr("role." + r.key()), role == r.ordinal(),
                        () -> send(Vcc4ActionPacket.SET_AXLE_ROLE, axle, ordinal, 0L), null, List.of(tr("role." + r.key() + ".hint")));
                x += bw + 2;
            }
            boolean driven = i >= s.driven().length || s.driven()[i];
            final int axle = i;
            checkbox(drivenX, ry + 2, driven, "", () -> send(Vcc4ActionPacket.TOGGLE_AXLE_DRIVE, axle, 0, 0L),
                    List.of(tr("col.driven.hint")));
            int count = 0;
            List<String> govs = new ArrayList<>();
            float angle = 0f;
            boolean anyCommanded = false;
            for (Vcc4Snapshot.Wheel wh : s.wheels()) {
                if (wh.axle() != i || wh.body() != 0) continue;
                count++;
                if (wh.governor() >= 0 && !govs.contains("G" + (wh.governor() + 1))) govs.add("G" + (wh.governor() + 1));
                if (wh.commanded() && !anyCommanded) {
                    angle = wh.angle();
                    anyCommanded = true;
                }
            }
            c.text(Integer.toString(count), wheelsX, ry + 3, LABEL);
            if (gW >= 20) {
                String g = govs.isEmpty() ? tr("free_rolling") : String.join(" ", govs);
                c.text(fit(g, gW), govX, ry + 3, govs.isEmpty() ? DIM : LABEL);
            }
            if (showAngle) {
                String a = anyCommanded ? tr("deg", String.format(Locale.ROOT, "%+.0f", angle)) : "-";
                c.text(a, angleX, ry + 3, DIM);
            }
        });
    }

    private boolean focusHasAxle(int axle) {
        for (PartIndex.Ref r : focus) {
            if (r.kind() == PartIndex.Kind.WHEEL && r.index() < s.wheels().size() && s.wheels().get(r.index()).axle() == axle) return true;
        }
        return false;
    }

    private void keysPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y = top + CONTENT_Y;
        int y1 = top + h - PAD;
        Keymap km = Keymap.fromInts(s.keymap());

        String note;
        int noteColour;
        if (captureControl != null) {
            String slot = captureControl.isAxis()
                    ? controlName(captureControl) + " " + tr(captureSlot == Keymap.PLUS ? "slot.plus" : "slot.minus")
                    : controlName(captureControl);
            note = captureNote != null ? captureNote : tr("keys.capture", slot);
            noteColour = captureNote != null ? WARN : INK;
        } else if (lastPreview != KeyIds.NONE && previewKeys.contains(lastPreview) && km.slotsOf(lastPreview).isEmpty()) {
            note = tr("keys.unused", keyLabel(lastPreview));
            noteColour = DIM;
        } else if (s.youDrive()) {
            note = tr("keys.live");
            noteColour = GOOD;
        } else {
            note = tr("keys.hint");
            noteColour = DIM;
        }
        List<String> noteLines = wrap(note, x1 - x0);
        for (String line : noteLines.subList(0, Math.min(2, noteLines.size()))) {
            c.text(line, x0, y, noteColour);
            y += LINE;
        }
        y += 3;

        String reset = tr("reset_keys");
        int resetW = host.width(reset) + 12;
        button(x1 - resetW, y1 - BUTTON_H, resetW, reset, false, () -> send(Vcc4ActionPacket.RESET_KEYS, 0, 0, 0L), null,
                List.of(tr("reset_keys.hint")));
        String starNote = tr("keys.star");
        if (host.width(starNote) < x1 - x0 - resetW - 10) c.text(starNote, x0, y1 - BUTTON_H + 3, DIM);

        int nameW = host.width(tr("col.control"));
        for (Control ctl : Control.DISPLAY) nameW = Math.max(nameW, host.width(controlName(ctl)));
        int capW = 40;
        for (Control ctl : Control.DISPLAY) {
            capW = Math.max(capW, host.width(capLabel(km.plus(ctl))) + 16);
            capW = Math.max(capW, host.width(capLabel(km.minus(ctl))) + 16);
        }
        capW = Math.min(capW, 76);
        capW = Math.max(capW, Math.max(host.width(tr("col.key")), host.width(tr("col.key2"))) + 2);
        int invW = host.width(tr("invert")) + 12;
        int[] cols = columns(x0, x1 - 8, new int[]{nameW, capW, capW, invW, -1}, 8);
        int movesX = cols[4];
        c.text(tr("col.control"), cols[0], y, DIM);
        c.text(fit(tr("col.key"), capW), cols[1], y, DIM);
        c.text(fit(tr("col.key2"), capW), cols[2], y, DIM);
        c.text(fit(tr("col.moves"), x1 - 8 - movesX), movesX, y, DIM);
        y += LINE + 2;

        HeldKeys held = s.heldKeys();
        List<Control> all = Control.DISPLAY;
        final int capWidth = capW;
        final int invWidth = invW;
        list(x0, y, x1, y1 - BUTTON_H - 6, all.size(), Page.KEYS, (i, rx0, ry, rx1) -> {
            Control ctl = all.get(i);
            boolean on = controlHeld(ctl, km, held);
            c.text(controlName(ctl), rx0, ry + 3, on ? INK : LABEL);
            keyCap(cols[1], ry, capWidth, km, ctl, Keymap.PLUS, held);
            if (ctl.isAxis()) {
                keyCap(cols[2], ry, capWidth, km, ctl, Keymap.MINUS, held);
                final int ordinal = ctl.ordinal();
                button(cols[3], ry, invWidth, tr("invert"), km.inverted(ctl),
                        () -> send(Vcc4ActionPacket.TOGGLE_INVERT, ordinal, 0, 0L), null, List.of(tr("invert.hint")));
            }
            String moves = movesLong(ctl, km);
            boolean nothing = PartIndex.partsOf(ctl, s).isEmpty();
            c.text(fit(moves, rx1 - movesX), movesX, ry + 3, nothing ? DIM : (on ? INK : LABEL));
            if (host.width(moves) > rx1 - movesX) hit(movesX, ry, rx1 - movesX, rowH - 2, null, null, List.of(moves));
        });
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
        button(x, y, capW, fit(text, capW - 6), on || picking, () -> pick(ctl, slot),
                () -> send(Vcc4ActionPacket.SET_KEY, ctl.ordinal(), KeyIds.NONE, slot), tip);
    }

    private void jetsPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y = top + CONTENT_Y;
        int y1 = top + h - PAD;

        if (s.jetDrive()) {
            List<String> noteLines = wrap(tr("jets.drive_note"), x1 - x0);
            for (String line : noteLines.subList(0, Math.min(2, noteLines.size()))) {
                c.text(line, x0, y, GOOD);
                y += LINE;
            }
            y += 2;
        } else if (!s.jets().isEmpty()) {
            checkbox(x0, y, s.jetsSteerWithoutBoost(), fit(tr("jets.steer_alone"), x1 - x0 - 14),
                    () -> option(Vcc4ActionPacket.OPTION_JETS_STEER_ALONE, s.jetsSteerWithoutBoost() ? 0 : 1),
                    List.of(tr("jets.steer_alone.hint")));
            y += LINE + 4;
        }
        if (hasJob(JetMixer.Job.LIFT)) {
            String label = tr(s.liftHoldAuto() ? "jets.lift_hold_auto" : "jets.lift_hold");
            List<String> tip = List.of(tr("jets.lift_hold.hint"), tr(s.liftHoldAuto() ? "jets.lift_hold.auto_hint" : "jets.lift_hold.reset_hint"));
            checkbox(x0, y, s.liftHold(), fit(label, x1 - x0 - 14), () -> option(Vcc4ActionPacket.OPTION_LIFT_HOLD, s.liftHold() ? 0 : 1), tip);

            hits.add(new Hit(x0, y - 1, 12 + host.width(fit(label, x1 - x0 - 14)), 11, null,
                    () -> option(Vcc4ActionPacket.OPTION_LIFT_HOLD, 2), tip, false));
            y += LINE + 4;
        }
        if (s.jets().isEmpty()) {
            for (String line : wrap(tr(s.onCraft() ? "no_jets" : "not_on_craft_short"), x1 - x0)) {
                c.text(line, x0, y + 3, DIM);
                y += LINE;
            }
            return;
        }

        Keymap km = Keymap.fromInts(s.keymap());
        int jetW = Math.max(host.width(tr("col.jet")), host.width("J" + s.jets().size()));
        int whereW = host.width(tr("col.where"));
        for (Vcc4Snapshot.Jet j : s.jets()) whereW = Math.max(whereW, host.width(where(j)));
        int jobW = 0;
        for (Vcc4Snapshot.Jet j : s.jets()) jobW = Math.max(jobW, host.width(jobLabel(j)) + 12);
        jobW = Math.max(jobW, host.width(tr("col.job")));
        int firesW = host.width(tr("col.fires"));
        for (Vcc4Snapshot.Jet j : s.jets()) firesW = Math.max(firesW, host.width(firesOn(j, km)));
        firesW = Math.min(firesW, 96);
        int govW = Math.max(host.width(tr("col.gov")), Math.max(host.width("G99"), host.width(tr("jet.direct"))));
        int invW = Math.max(host.width(tr("jet.inv")) + 12, host.width(tr("jet.one_way")));
        int[] cols = columns(x0, x1 - 8, new int[]{jetW, whereW, jobW, firesW, govW, invW, -1}, 8);
        int outX = cols[6];
        int outW = x1 - 8 - outX;
        c.text(tr("col.jet"), cols[0], y, DIM);
        c.text(fit(tr("col.where"), whereW), cols[1], y, DIM);
        c.text(tr("col.job"), cols[2], y, DIM);
        c.text(fit(tr("col.fires"), firesW), cols[3], y, DIM);
        c.text(tr("col.gov"), cols[4], y, DIM);
        if (outW >= 24) c.text(fit(tr("col.output"), outW), outX, y, DIM);
        y += LINE + 2;
        final int jobWidth = jobW;
        final int firesWidth = firesW;
        final int invWidth = invW;
        list(x0, y, x1, y1, s.jets().size(), Page.JETS, (i, rx0, ry, rx1) -> {
            Vcc4Snapshot.Jet j = s.jets().get(i);
            boolean lit = focus.contains(new PartIndex.Ref(PartIndex.Kind.JET, i));
            boolean trouble = j.direct() ? j.foreign() || j.supplyOf().out() : j.governor() < 0;
            c.text("J" + (i + 1), rx0, ry + 3, trouble ? WARN : (lit ? INK : LABEL));
            c.text(where(j), cols[1], ry + 3, DIM);
            final long pos = j.pos();
            button(cols[2], ry, jobWidth, fit(jobLabel(j), jobWidth - 6), false,
                    () -> openJetMenu(cols[2], ry + BUTTON_H, pos, j.role()), null, jobTip(j));
            String fires = firesOn(j, km);
            Control need = PartIndex.primaryControl(j, s);
            boolean waiting = need != null && !km.bound(need);
            c.text(fit(fires, firesWidth), cols[3], ry + 3, waiting ? WARN : LABEL);
            if (j.direct()) {
                String direct = tr("jet.direct");
                c.text(direct, cols[4], ry + 3, j.foreign() ? WARN : LABEL);
                hit(cols[4], ry, host.width(direct), rowH - 2, null, null, List.of(tr(j.foreign() ? "jet.foreign.hint" : "jet.direct.hint")));
                String oneWay = tr("jet.one_way");
                c.text(oneWay, cols[5], ry + 3, DIM);
                hit(cols[5], ry, host.width(oneWay), rowH - 2, null, null, List.of(tr("jet.one_way.hint")));
                if (rx1 - outX >= 24) {
                    levelBar(outX, ry + 3, rx1 - outX, j.command(), directReading(j), trouble ? WARN : JET_ON);
                    hit(outX, ry, rx1 - outX, rowH - 2, null, null, List.of(tr("jet.output.hint")));
                }
                return;
            }
            String gov = j.governor() < 0 ? tr("no_governor") : "G" + (j.governor() + 1) + (j.sharesWheels() ? "*" : "");
            c.text(gov, cols[4], ry + 3, j.governor() < 0 || j.sharesWheels() ? WARN : LABEL);
            if (j.governor() < 0) hit(cols[4], ry, host.width(gov), rowH - 2, null, null, List.of(tr("no_governor.hint")));
            else if (j.sharesWheels()) hit(cols[4], ry, host.width(gov), rowH - 2, null, null, List.of(tr("shares_wheels.hint")));
            button(cols[5], ry, invWidth, tr("jet.inv"), j.inverted(),
                    () -> send(Vcc4ActionPacket.TOGGLE_JET_INVERT, 0, 0, pos), null, List.of(tr("jet.inv.hint")));
            if (rx1 - outX >= 24) outputBar(outX, ry + 3, rx1 - outX, j.command(), tr("rpm", Math.round(j.rpm())), JET_ON);
        });
    }

    private void outputBar(int x, int y, int width, float value, String reading, int onColour) {
        int textW = host.width(reading);
        int bw = width - textW - 4;
        if (bw < 16) {
            c.text(fit(reading, width), x, y, DIM);
            return;
        }
        well(x, y - 1, bw, 9, RECESSED);
        int mid = x + bw / 2;
        int v = Math.round(clamp(value, -1f, 1f) * (bw / 2 - 1));
        int colour = v != 0 ? onColour : INK;
        if (v > 0) c.fill(mid, y + 1, mid + v, y + 6, colour);
        else if (v < 0) c.fill(mid + v, y + 1, mid, y + 6, colour);
        c.fill(mid, y, mid + 1, y + 7, SHADOW);
        c.text(reading, x + bw + 4, y, DIM);
    }

    private void levelBar(int x, int y, int width, float value, String reading, int onColour) {
        int textW = host.width(reading);
        int bw = width - textW - 4;
        if (bw < 16) {
            c.text(fit(reading, width), x, y, DIM);
            return;
        }
        well(x, y - 1, bw, 9, RECESSED);
        int v = Math.round(clamp(value, 0f, 1f) * (bw - 2));
        if (v > 0) c.fill(x + 1, y + 1, x + 1 + v, y + 6, onColour);
        c.text(reading, x + bw + 4, y, DIM);
    }

    String directReading(Vcc4Snapshot.Jet j) {
        if (j.foreign()) return tr("jet.foreign");
        JetSupply supply = j.supplyOf();
        if (supply.out()) return tr("jet.supply." + supply.key());
        return tr("force_of", Math.round(j.force()), Math.round(j.ceiling()));
    }

    // only judge with the nozzle straight or the warning flickers every turn
    static boolean blocked(Vcc4Snapshot.Jet j) {
        return j.direct() && j.clear() < 0.995f && (j.kindOf() != JetKind.VECTOR || Math.abs(j.nozzleX()) < 0.02f);
    }

    String nozzleText(Vcc4Snapshot.Jet j) {
        double side = -j.nozzleX() * j.axisForward();
        double ahead = j.nozzleX() * j.axisRight();
        String way = Math.abs(side) >= Math.abs(ahead) ? tr(side > 0 ? "side.right" : "side.left") : tr(ahead > 0 ? "where.front" : "where.rear");
        return tr("jet.nozzle", Math.round(Math.abs(j.nozzleX()) * VectorGimbal.MAX_TILT_DEG), way);
    }

    void openJetMenu(int x, int y, long pos, int currentRole) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (JetRole r : JetRole.values()) {
            labels.add(tr("jetrole." + r.key()));
            final int ordinal = r.ordinal();
            actions.add(() -> send(Vcc4ActionPacket.SET_JET_ROLE, ordinal, 0, pos));
        }
        openPopup(x, y, labels, actions, currentRole);
    }

    private String jobLabel(Vcc4Snapshot.Jet j) {
        JetRole role = JetRole.byOrdinal(j.role());
        JetMixer.Job job = JetMixer.Job.byOrdinal(j.job());
        if (role == JetRole.AUTO) return tr("jetrole.auto_job", job == null ? "-" : tr("job." + job.key()));
        return tr("jetrole." + role.key());
    }

    private List<String> jobTip(Vcc4Snapshot.Jet j) {
        List<String> tip = new ArrayList<>();
        JetRole role = JetRole.byOrdinal(j.role());
        JetMixer.Job job = JetMixer.Job.byOrdinal(j.job());
        tip.add(tr("jet.points", axisWord(j), where(j)));
        if (j.direct()) {
            if (j.foreign()) tip.add(tr("jet.foreign.hint"));
            JetSupply supply = j.supplyOf();
            if (supply == JetSupply.LOW || supply.out()) tip.add(tr("jet.supply." + supply.key() + ".hint"));
            if (blocked(j)) tip.add(tr("jet.clear", Math.round(j.clear() * 100f)));
        }
        if (role == JetRole.AUTO && job != null) tip.add(tr("job." + job.key() + ".why"));
        else tip.add(tr("jetrole." + role.key() + ".hint"));
        if (j.direct()) {
            JetKind kind = j.kindOf();
            if (kind == JetKind.VECTOR && Math.abs(j.nozzleX()) > 0.02f) tip.add(nozzleText(j));
            tip.add(tr("jetkind." + kind.key()) + ": " + tr("jetkind." + kind.key() + ".hint"));
        }
        if (!j.signKnown()) tip.add(tr("jet.sign_unknown"));
        if (j.body() > 0) tip.add(tr("tip.on_body", "B" + (j.body() + 1)));
        tip.add(tr("jet.menu_hint"));
        return tip;
    }

    private String where(Vcc4Snapshot.Jet j) {
        String along = j.armForward() > 0.75f ? tr("where.front") : (j.armForward() < -0.75f ? tr("where.rear") : "");
        String side = j.armRight() > 0.75f ? tr("where.right") : (j.armRight() < -0.75f ? tr("where.left") : "");
        if (along.isEmpty() && side.isEmpty()) return tr("where.centre");
        if (along.isEmpty()) return side;
        if (side.isEmpty()) return along;
        return along + " " + side;
    }

    private String axisWord(Vcc4Snapshot.Jet j) {
        if (Math.abs(j.axisUp()) >= JetMixer.VERTICAL) return tr("axis.vertical");
        return Math.abs(j.axisForward()) >= Math.abs(j.axisRight()) ? tr("axis.fore_aft") : tr("axis.sideways");
    }

    String firesOn(Vcc4Snapshot.Jet j, Keymap km) {
        Control ctl = PartIndex.primaryControl(j, s);
        if (ctl == null) return tr("fires.never");
        if (!km.bound(ctl)) return tr("fires.no_key", controlName(ctl));
        int sign = PartIndex.firingSign(j, s);
        String keys;
        if (sign == 0 || !ctl.isAxis()) {
            keys = capsText(km, ctl);
        } else {
            int id = sign > 0 ? km.plus(ctl) : km.minus(ctl);
            if (id == KeyIds.NONE) return tr("fires.no_key", controlName(ctl));
            keys = capLabel(id);
        }
        if (ctl == Control.BOOST && sign < 0) {
            int reverse = km.minus(Control.THROTTLE);
            if (reverse == KeyIds.NONE) return tr("fires.no_key", controlName(Control.THROTTLE));
            keys = tr("fires.with", keys, capLabel(reverse));
        }
        if (PartIndex.turnNeedsBoost(j, s) && km.bound(Control.BOOST)) keys = tr("fires.with", capsText(km, Control.BOOST), keys);
        return keys;
    }

    private record PartRow(char kind, int index, String text) {
    }

    private void toolsPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y = top + CONTENT_Y;
        int y1 = top + h - PAD;
        if (s.swivels().isEmpty() && s.machines().isEmpty()) {
            for (String line : wrap(tr(s.onCraft() ? "no_tools" : "not_on_craft"), x1 - x0)) {
                c.text(line, x0, y, DIM);
                y += LINE;
            }
            return;
        }
        List<PartRow> rows = new ArrayList<>();
        if (!s.swivels().isEmpty()) {
            rows.add(new PartRow('h', 0, tr("section.swivels", s.swivels().size())));
            for (int i = 0; i < s.swivels().size(); i++) rows.add(new PartRow('s', i, ""));
        }
        if (!s.machines().isEmpty()) {
            rows.add(new PartRow('h', 1, tr("section.machines", s.machines().size())));
            for (int i = 0; i < s.machines().size(); i++) rows.add(new PartRow('m', i, ""));
        }
        Keymap km = Keymap.fromInts(s.keymap());
        int nameW = host.width("M" + Math.max(9, Math.max(s.swivels().size(), s.machines().size())) + "!") + 6;
        list(x0, y, x1, y1, rows.size(), Page.TOOLS, (i, rx0, ry, rx1) -> {
            PartRow row = rows.get(i);
            switch (row.kind()) {
                case 'h' -> heading(row.text(), rx0, ry, rx1);
                case 's' -> swivelRow(rx0, ry, rx1, nameW, row.index(), km);
                default -> machineRow(rx0, ry, rx1, nameW, row.index(), km);
            }
        });
    }

    private void heading(String text, int x0, int y, int x1) {
        c.text(text, x0, y + 5, INK);
        c.fill(x0 + host.width(text) + 4, y + 9, x1, y + 10, RECESSED);
    }

    private int inputButtonWidth() {
        int inputW = 0;
        for (PartInput in : PartInput.values()) inputW = Math.max(inputW, host.width(tr("input." + in.key())) + 12);
        return inputW;
    }

    private void swivelRow(int x0, int y, int x1, int nameW, int i, Keymap km) {
        Vcc4Snapshot.Swivel sw = s.swivels().get(i);
        boolean lit = focus.contains(new PartIndex.Ref(PartIndex.Kind.SWIVEL, i));
        PartInput input = PartInput.byOrdinal(sw.input());
        boolean problem = input != PartInput.OFF && !sw.ready();
        boolean hitch = input == PartInput.OFF && sw.assembled() && !sw.locked() && sw.carries() >= 0;
        c.text("S" + (i + 1) + (problem ? "!" : ""), x0, y + 3, problem ? WARN : (lit ? INK : LABEL));
        List<String> nameTip = new ArrayList<>();
        nameTip.add(tr("tip.swivel", "S" + (i + 1)));
        if (sw.carries() >= 0) nameTip.add(tr("swivel.carries", "B" + (sw.carries() + 1)));
        if (sw.body() > 0) nameTip.add(tr("tip.on_body", "B" + (sw.body() + 1)));
        if (problem) nameTip.add(tr("swivel.not_ready"));
        if (input == PartInput.STEER && isJoint(sw)) nameTip.add(tr("swivel.joint_hint"));
        hit(x0, y, nameW, rowH - 2, null, null, nameTip);
        int inputW = inputButtonWidth();
        String latched = tr(sw.latched() ? "latched" : "sprung");
        int latchW = Math.max(host.width(tr("latched")), host.width(tr("sprung"))) + 12;
        int invW = host.width(tr("invert")) + 12;
        String keys;
        if (input.control() == null) keys = hitch ? tr("swivel.hitch") : tr("input.off_note");
        else keys = km.bound(input.control()) ? capsText(km, input.control()) : tr("legend_no_key");
        int travelW = 12 + 4 + host.width(tr("deg", 180)) + 4 + 12;
        int[] cols = columns(x0 + nameW, x1, new int[]{inputW, -1, travelW, latchW, invW, host.width(tr("deg", "+180"))}, 6);
        final long pos = sw.pos();
        button(cols[0], y, inputW, tr("input." + input.key()), false, () -> openInputMenu(cols[0], y + BUTTON_H, pos, sw.input(), false),
                null, List.of(tr("swivel.input.hint")));
        boolean waiting = input.control() != null && !km.bound(input.control());
        String keysFit = fit(keys, cols[2] - 6 - cols[1]);
        c.text(keysFit, cols[1], y + 3, waiting ? WARN : (input.control() == null ? DIM : LABEL));
        if (!keysFit.equals(keys)) hit(cols[1], y, cols[2] - 6 - cols[1], rowH - 2, null, null, List.of(keys));
        stepper(cols[2], y, tr("deg", sw.travel()), host.width(tr("deg", 180)),
                () -> send(Vcc4ActionPacket.SET_SWIVEL_TRAVEL, sw.travel() - 5, 0, pos),
                () -> send(Vcc4ActionPacket.SET_SWIVEL_TRAVEL, sw.travel() + 5, 0, pos), tr("travel.hint"));
        button(cols[3], y, latchW, latched, false, () -> send(Vcc4ActionPacket.TOGGLE_SWIVEL_LATCHED, 0, 0, pos), null,
                List.of(tr("latched.hint")));
        button(cols[4], y, invW, tr("invert"), sw.inverted(), () -> send(Vcc4ActionPacket.TOGGLE_SWIVEL_INVERT, 0, 0, pos), null,
                List.of(tr("swivel.invert.hint")));
        c.text(tr("deg", String.format(Locale.ROOT, "%+.0f", sw.angle())), cols[5], y + 3, DIM);
    }

    private static boolean isJoint(Vcc4Snapshot.Swivel sw) {
        return (sw.body() == 0 && sw.carries() > 0) || (sw.body() > 0 && sw.carries() == 0);
    }

    private void machineRow(int x0, int y, int x1, int nameW, int i, Keymap km) {
        Vcc4Snapshot.Machine m = s.machines().get(i);
        boolean lit = focus.contains(new PartIndex.Ref(PartIndex.Kind.MACHINE, i));
        PartInput input = PartInput.byOrdinal(m.input());
        String drives = machineNames(m);
        c.text("M" + (i + 1), x0, y + 3, m.foreign() ? WARN : (lit ? INK : LABEL));
        List<String> nameTip = new ArrayList<>();
        nameTip.add(tr("tip.machine", "M" + (i + 1), drives));
        nameTip.add(tr(m.runner() ? "machine.runner_why" : "machine.positioner_why"));
        if (m.body() > 0) nameTip.add(tr("tip.on_body", "B" + (m.body() + 1)));
        if (m.foreign()) nameTip.add(tr("foreign.hint"));
        hit(x0, y, nameW, rowH - 2, null, null, nameTip);
        int inputW = inputButtonWidth();
        String mode = tr(m.latched() ? "machine.latched" : "machine.sprung");
        int modeW = Math.max(host.width(tr("machine.latched")), host.width(tr("machine.sprung"))) + 12;
        int flipW = host.width(tr("flip")) + 12;
        int rpmValueW = host.width(tr("rpm", 256));
        int rpmW = 12 + 4 + rpmValueW + 4 + 12;
        int outW = host.width(tr("rpm", "-256"));
        String keys;
        if (input.control() == null) keys = tr("input.off_note");
        else keys = km.bound(input.control()) ? capsText(km, input.control()) : tr("legend_no_key");
        int keysW = Math.max(host.width(keys), 20);
        int[] cols = columns(x0 + nameW, x1, new int[]{inputW, Math.min(keysW, 60), rpmW, modeW, flipW, -1, outW}, 6);
        final long pos = m.pos();
        button(cols[0], y, inputW, tr("input." + input.key()), false, () -> openInputMenu(cols[0], y + BUTTON_H, pos, m.input(), true),
                null, List.of(tr("machine.input.hint")));
        boolean waiting = input.control() != null && !km.bound(input.control());
        String keysFit = fit(keys, cols[2] - 6 - cols[1]);
        c.text(keysFit, cols[1], y + 3, waiting ? WARN : (input.control() == null ? DIM : LABEL));
        if (!keysFit.equals(keys)) hit(cols[1], y, cols[2] - 6 - cols[1], rowH - 2, null, null, List.of(keys));
        stepper(cols[2], y, tr("rpm", m.rpm()), rpmValueW,
                () -> send(Vcc4ActionPacket.SET_MACHINE_RPM, m.rpm() - MachineAxis.RPM_STEP, 0, pos),
                () -> send(Vcc4ActionPacket.SET_MACHINE_RPM, m.rpm() + MachineAxis.RPM_STEP, 0, pos), tr("machine.rpm.hint"));
        button(cols[3], y, modeW, mode, false, () -> send(Vcc4ActionPacket.TOGGLE_MACHINE_LATCHED, 0, 0, pos), null,
                List.of(tr("machine.mode.hint")));
        button(cols[4], y, flipW, tr("flip"), m.inverted(), () -> send(Vcc4ActionPacket.TOGGLE_MACHINE_INVERT, 0, 0, pos), null,
                List.of(tr("machine.flip.hint")));
        int namesW = cols[6] - 6 - cols[5];
        String namesFit = fit(drives, namesW);
        c.text(namesFit, cols[5], y + 3, DIM);
        if (!namesFit.equals(drives)) hit(cols[5], y, namesW, rowH - 2, null, null, List.of(drives));
        String out = tr("rpm", String.format(Locale.ROOT, "%+d", m.applied()));
        c.text(out, cols[6], y + 3, m.output() != 0f ? JET_ON : DIM);
    }

    String machineNames(Vcc4Snapshot.Machine m) {
        List<String> names = new ArrayList<>();
        for (String id : m.drives()) names.add(host.name(id));
        return names.isEmpty() ? tr("machine.unknown") : String.join(", ", names);
    }

    private void openInputMenu(int x, int y, long pos, int current, boolean machine) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        int action = machine ? Vcc4ActionPacket.SET_MACHINE_INPUT : Vcc4ActionPacket.SET_SWIVEL_INPUT;
        for (PartInput in : PartInput.values()) {
            labels.add(tr("input." + in.key()));
            final int ordinal = in.ordinal();
            actions.add(() -> send(action, ordinal, 0, pos));
        }
        openPopup(x, y, labels, actions, current);
    }

    private void lightsPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y = top + CONTENT_Y;
        int y1 = top + h - PAD;
        if (s.links().isEmpty()) {
            for (String line : wrap(tr(s.onCraft() ? "no_lights" : "not_on_craft"), x1 - x0)) {
                c.text(line, x0, y, DIM);
                y += LINE;
            }
            return;
        }
        List<String> noteLines = wrap(tr("lights.hint"), x1 - x0);
        for (String line : noteLines.subList(0, Math.min(2, noteLines.size()))) {
            c.text(line, x0, y, DIM);
            y += LINE;
        }
        y += 3;
        // only vehicle lights greet
        if (s.links().stream().anyMatch(l -> l.lamp() > 0)) {
            checkbox(x0, y, s.greeting(), fit(tr("lights.greeting"), x1 - x0 - 14),
                    () -> option(Vcc4ActionPacket.OPTION_GREETING, s.greeting() ? 0 : 1), List.of(tr("lights.greeting.hint")));
            y += LINE + 4;
        }

        Keymap km = Keymap.fromInts(s.keymap());
        int nameW = Math.max(host.width(tr("col.light")), host.width("L" + s.links().size()) + 12);
        int sourceW = 0;
        for (LinkSource src : LinkSource.values()) sourceW = Math.max(sourceW, host.width(tr("source." + src.key())) + 12);
        int toggleW = host.width(tr("toggle")) + 12;
        int keyW = host.width(tr("col.key"));
        for (Vcc4Snapshot.Link l : s.links()) {
            LinkSource source = LinkSource.byOrdinal(l.source());
            String keys = sourceKeys(source, km);
            keyW = Math.max(keyW, host.width(source.keyed() && keys.isEmpty() ? tr("legend_no_key") : keys));
        }
        keyW = Math.min(keyW, 70);
        int[] cols = columns(x0, x1 - 8, new int[]{nameW, -1, sourceW, toggleW, keyW}, 8);
        c.text(tr("col.light"), cols[0], y, DIM);
        c.text(fit(tr("col.frequency"), cols[2] - 8 - cols[1]), cols[1], y, DIM);
        c.text(fit(tr("col.lit_by"), sourceW), cols[2], y, DIM);
        c.text(fit(tr("col.key"), keyW), cols[4], y, DIM);
        y += LINE + 2;
        final int sourceWidth = sourceW;
        final int toggleWidth = toggleW;
        final int keyWidth = keyW;
        list(x0, y, x1, y1, s.links().size(), Page.LIGHTS, (i, rx0, ry, rx1) -> {
            Vcc4Snapshot.Link l = s.links().get(i);
            LinkSource source = LinkSource.byOrdinal(l.source());
            boolean lit = focus.contains(new PartIndex.Ref(PartIndex.Kind.LINK, i));
            c.text("L" + (i + 1), rx0, ry + 3, lit ? INK : LABEL);
            int lampX = rx0 + host.width("L" + s.links().size()) + 4;
            well(lampX, ry + 2, 8, 8, l.strength() > 0 ? litColour(l) : RECESSED);
            String freq = frequency(l);
            int freqW = cols[2] - 8 - cols[1];
            String freqFit = fit(freq, freqW);
            c.text(freqFit, cols[1], ry + 3, LABEL);
            List<String> tip = new ArrayList<>();
            if (l.lamp() > 0) {
                tip.add(tr("tip.lamp", "L" + (i + 1), freq));
                if (l.proposed()) tip.add(tr("tip.lamp_proposed"));
            } else {
                tip.add(tr("tip.link", "L" + (i + 1), freq));
            }
            if (l.body() > 0) tip.add(tr("tip.on_body", "B" + (l.body() + 1)));
            if (l.coupler()) tip.add(tr("tip.coupler"));
            hit(rx0, ry, cols[2] - 8 - rx0, rowH - 2, null, null, tip);
            final long pos = l.pos();
            button(cols[2], ry, sourceWidth, fit(tr("source." + source.key()), sourceWidth - 6), false,
                    () -> openSourceMenu(cols[2], ry + BUTTON_H, pos, l.source()), null,
                    List.of(tr("source." + source.key() + ".hint"), tr("source.menu_hint")));
            if (source.keyed()) {
                button(cols[3], ry, toggleWidth, tr("toggle"), l.toggle(), () -> send(Vcc4ActionPacket.TOGGLE_LINK_TOGGLE, 0, 0, pos),
                        null, List.of(tr("toggle.hint")));
            }
            String keys = sourceKeys(source, km);
            boolean unbound = source.keyed() && keys.isEmpty();

            boolean fault = unbound && source != LinkSource.HEADLIGHT;
            c.text(fit(unbound ? tr("legend_no_key") : keys, keyWidth), cols[4], ry + 3, fault ? WARN : DIM);
        });
    }

    private String frequency(Vcc4Snapshot.Link l) {
        String a = host.name(l.first());
        String b = host.name(l.second());
        if (a.isEmpty() && b.isEmpty()) return tr("frequency.none");
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return tr("frequency.pair", a, b);
    }

    private String sourceKeys(LinkSource source, Keymap km) {
        int id = switch (source) {
            case OFF, DRIVING -> KeyIds.NONE;
            case BRAKE, TAIL_BRAKE -> km.plus(Control.BRAKE);
            case REVERSE -> km.minus(Control.THROTTLE);
            case BOOST -> km.plus(Control.BOOST);
            case LEFT -> km.minus(Control.STEER);
            case RIGHT -> km.plus(Control.STEER);
            default -> source.sign() > 0 ? km.plus(source.control()) : km.minus(source.control());
        };
        return id == KeyIds.NONE ? "" : capLabel(id);
    }

    private void openSourceMenu(int x, int y, long pos, int current) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (LinkSource src : LinkSource.values()) {
            labels.add(tr("source." + src.key()));
            final int ordinal = src.ordinal();
            actions.add(() -> send(Vcc4ActionPacket.SET_LINK_SOURCE, ordinal, 0, pos));
        }
        openPopup(x, y, labels, actions, current);
    }

    private void trailersPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y = top + CONTENT_Y;
        int y1 = top + h - PAD;
        if (s.bodies().isEmpty()) {
            for (String line : wrap(tr(s.onCraft() ? "trailers.none" : "not_on_craft"), x1 - x0)) {
                c.text(line, x0, y, DIM);
                y += LINE;
            }
            return;
        }
        List<String> intro = wrap(tr("trailers.intro"), x1 - x0);
        for (String line : intro.subList(0, Math.min(2, intro.size()))) {
            c.text(line, x0, y, DIM);
            y += LINE;
        }
        y += 3;
        List<PartRow> rows = new ArrayList<>();
        for (int i = 0; i < s.bodies().size(); i++) {
            rows.add(new PartRow('t', i, ""));
            rows.add(new PartRow('u', i, ""));
        }
        for (int i = 0; i < s.links().size(); i++) {
            if (s.links().get(i).coupler()) rows.add(new PartRow('c', i, ""));
        }
        int nameW = host.width("B" + Math.max(9, s.bodies().size() + 1)) + 6;
        int modeW = 0;
        for (TrailerMode m : TrailerMode.values()) modeW = Math.max(modeW, host.width(tr("trailermode." + m.key())) + 12);
        final int buttonW = modeW;
        list(x0, y, x1, y1, rows.size(), Page.TRAILERS, (i, rx0, ry, rx1) -> {
            PartRow row = rows.get(i);
            switch (row.kind()) {
                case 't' -> trailerRow(rx0, ry, rx1, nameW, buttonW, row.index());
                case 'u' -> trailerDetailRow(rx0, ry, rx1, nameW, row.index());
                default -> couplerRow(rx0, ry, rx1, nameW, row.index());
            }
        });
    }

    private void trailerRow(int x0, int y, int x1, int nameW, int buttonW, int i) {
        Vcc4Snapshot.Body b = s.bodies().get(i);
        String name = "B" + (b.index() + 1);
        boolean alert = b.has(Vcc4Snapshot.FLAG_SWAYING) || b.has(Vcc4Snapshot.FLAG_SNAPPING) || b.has(Vcc4Snapshot.FLAG_OVERRUN)
                || b.has(Vcc4Snapshot.FLAG_LOST);
        c.text(name, x0, y + 3, alert ? WARN : (b.takesMode() ? INK : LABEL));
        String what = trailerWhat(b);
        int modesX = b.takesMode() ? x1 - 3 * buttonW - 4 : x1;
        int whatW = modesX - 6 - (x0 + nameW);
        String fitted = fit(what, whatW);
        c.text(fitted, x0 + nameW, y + 3, b.roleOf().neverTouched() ? DIM : LABEL);
        List<String> tip = new ArrayList<>();
        tip.add(what);
        tip.add(tr("trailers.role." + b.roleOf().key() + ".hint"));
        hit(x0, y, modesX - x0 - 6, rowH - 2, null, null, tip);
        if (!b.takesMode()) return;
        int bx = modesX;
        for (TrailerMode m : TrailerMode.values()) {
            final int ordinal = m.ordinal();
            final int index = b.index();
            final long id = b.idLow();
            button(bx, y, buttonW, tr("trailermode." + m.key()), b.mode() == m.ordinal(),
                    () -> send(Vcc4ActionPacket.SET_TRAILER_MODE, index, ordinal, id), null,
                    List.of(tr("trailermode." + m.key()) + ": " + tr("trailermode." + m.key() + ".hint")));
            bx += buttonW + 2;
        }
    }

    private void trailerDetailRow(int x0, int y, int x1, int nameW, int i) {
        Vcc4Snapshot.Body b = s.bodies().get(i);
        int lx = x0 + nameW;
        String link = linkText(b);
        String live = liveText(b);
        int liveW = live.isEmpty() ? 0 : Math.min(host.width(live), (x1 - lx) / 2);
        String linkFit = fit(link, x1 - lx - liveW - (liveW > 0 ? 8 : 0));
        c.text(linkFit, lx, y + 2, DIM);
        if (!linkFit.equals(link)) hit(lx, y, host.width(linkFit), rowH - 2, null, null, List.of(link));
        if (liveW > 0) {
            String liveFit = fit(live, liveW);
            boolean warn = b.has(Vcc4Snapshot.FLAG_SWAYING) || b.has(Vcc4Snapshot.FLAG_SNAPPING) || b.has(Vcc4Snapshot.FLAG_OVERRUN)
                    || b.has(Vcc4Snapshot.FLAG_LOST);
            c.text(liveFit, x1 - host.width(liveFit), y + 2, warn ? WARN : (b.share() >= 0f ? GOOD : DIM));
            if (!liveFit.equals(live)) hit(x1 - host.width(liveFit), y, host.width(liveFit), rowH - 2, null, null, List.of(live));
        }
    }

    private void couplerRow(int x0, int y, int x1, int nameW, int i) {
        Vcc4Snapshot.Link l = s.links().get(i);
        LinkSource source = LinkSource.byOrdinal(l.source());
        Keymap km = Keymap.fromInts(s.keymap());
        String keys = sourceKeys(source, km);
        String text;
        if (source == LinkSource.OFF) text = tr("trailers.coupler_off", "L" + (i + 1));
        else if (keys.isEmpty()) text = tr("trailers.coupler_no_key", "L" + (i + 1), controlName(source.control() == null ? Control.AUX_A : source.control()));
        else text = tr("trailers.coupler", "L" + (i + 1), keys);
        c.text(fit(text, x1 - x0), x0, y + 3, l.strength() > 0 ? GOOD : LABEL);
        hit(x0, y, x1 - x0, rowH - 2, () -> setPage(Page.LIGHTS), null, List.of(text, tr("trailers.coupler.hint")));
    }

    String trailerWhat(Vcc4Snapshot.Body b) {
        return switch (b.roleOf()) {
            case TRAILER -> tr(b.has(Vcc4Snapshot.FLAG_POWERED) ? "trailers.trailer_driven" : "trailers.trailer", b.wheels());
            case TOWED -> tr("trailers.towed", b.wheels());
            case SEPARATE -> tr("trailers.separate");
            case LOAD -> tr("trailers.load");
            case PART -> tr(b.locked() ? "trailers.part_held" : "trailers.part");
        };
    }

    String linkText(Vcc4Snapshot.Body b) {
        LinkKind kind = LinkKind.byOrdinal(b.link());
        String name = tr("link." + kind.key());
        if (b.has(Vcc4Snapshot.FLAG_LOST)) return tr("link.gone", name);
        if (b.has(Vcc4Snapshot.FLAG_DOCKING)) return tr("link.settling", name);
        float r = b.reading();
        boolean read = Float.isFinite(r);
        return switch (kind.reading()) {
            case GAP -> !read ? name : (r < -0.15f ? tr("link.gap_slack", name, String.format(Locale.ROOT, "%.1f", -r))
                    : (r > 0.15f ? tr("link.gap_stretched", name, String.format(Locale.ROOT, "%.1f", r)) : tr("link.gap_taut", name)));
            case STRETCH -> !read ? name : tr("link.stretch_reading", name, String.format(Locale.ROOT, "%+.1f", r),
                    Math.round(Math.abs(b.force())), tr(b.force() >= 0f ? "link.pulling" : "link.pushing"));
            case ANGLE -> !read ? name : tr("link.angle_reading", name, Math.round(Math.abs(r)),
                    tr(Math.abs(r) < 0.5f ? "side.centre" : (r > 0 ? "side.right" : "side.left")));
            case NONE -> tr("link.rigid", name);
        };
    }

    String liveText(Vcc4Snapshot.Body b) {
        if (!b.takesMode()) return "";
        if (b.has(Vcc4Snapshot.FLAG_SWAYING)) return tr("trailers.swaying");
        if (b.has(Vcc4Snapshot.FLAG_SNAPPING)) return tr("trailers.snapping");
        if (b.has(Vcc4Snapshot.FLAG_OVERRUN)) return tr("trailers.overrun");
        if (b.has(Vcc4Snapshot.FLAG_LOST)) return tr("trailers.lost");
        if (s.driver().isEmpty()) return TrailerMode.byOrdinal(b.mode()) == TrailerMode.OFF ? tr("trailers.live_off") : "";
        TrailerMode mode = TrailerMode.byOrdinal(b.mode());
        if (mode == TrailerMode.OFF) return tr("trailers.live_off");
        List<String> bits = new ArrayList<>();
        if (mode == TrailerMode.ASSIST) {
            if (!b.has(Vcc4Snapshot.FLAG_POWERED)) bits.add(tr("trailers.brakes_only"));
            else if (b.share() >= 0.95f) bits.add(tr("trailers.carries_itself"));
            else if (b.share() >= 0f) bits.add(tr("trailers.carries", Math.round(b.share() * 100f)));
        }
        if (b.brake() > 0) bits.add(tr("trailers.brake", b.brake()));
        else if (Math.abs(b.drive()) >= 1f) bits.add(tr("rpm", Math.round(b.drive())));
        if (mode == TrailerMode.ASSIST && Math.abs(b.steer()) >= 1f) bits.add(tr("trailers.steer", String.format(Locale.ROOT, "%+.0f", b.steer())));
        return String.join("  ", bits);
    }

    private void partsPage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y = top + CONTENT_Y;
        int y1 = top + h - PAD;

        List<Group> actions = new ArrayList<>();
        for (Object[] a : new Object[][]{{"rescan", Vcc4ActionPacket.RESCAN}, {"relearn", Vcc4ActionPacket.RELEARN},
                {"turn_nose", Vcc4ActionPacket.TURN_NOSE}, {"flip_nose", Vcc4ActionPacket.FLIP_NOSE},
                {"reset_auto", Vcc4ActionPacket.RESET_AUTO}}) {
            String key = (String) a[0];
            int action = (int) a[1];
            actions.add(buttonGroup(tr(key), () -> send(action, 0, 0, 0L), tr(key + ".hint")));
        }
        int actionsTop = flow(actions, x0, x1, y1);

        List<PartRow> rows = new ArrayList<>();
        if (!s.governors().isEmpty()) {
            rows.add(new PartRow('h', 0, tr("section.governors", s.governors().size())));
            for (int i = 0; i < s.governors().size(); i++) rows.add(new PartRow('g', i, ""));
        }
        if (!s.bodies().isEmpty()) {
            rows.add(new PartRow('h', 1, tr("section.bodies", s.bodies().size())));
            for (int i = 0; i < s.bodies().size(); i++) rows.add(new PartRow('b', i, ""));
        }
        if (!s.wheels().isEmpty()) {
            rows.add(new PartRow('h', 2, tr("section.wheels", s.wheels().size())));
            for (int i = 0; i < s.wheels().size(); i++) rows.add(new PartRow('w', i, ""));
        }
        if (!s.problems().isEmpty()) {
            rows.add(new PartRow('h', 3, tr("section.problems", s.problems().size())));
            for (String p : s.problems()) {
                List<String> lines = wrap("! " + p, x1 - x0 - 12);
                for (String line : lines) rows.add(new PartRow('p', 0, line));
            }
        }
        if (rows.isEmpty()) {
            for (String line : wrap(tr(s.onCraft() ? "nothing_found" : "not_on_craft"), x1 - x0)) {
                c.text(line, x0, y, DIM);
                y += LINE;
            }
            return;
        }

        int nameW = host.width("G" + Math.max(9, s.governors().size())) + 6;
        list(x0, y, x1, actionsTop - 6, rows.size(), Page.PARTS, (i, rx0, ry, rx1) -> {
            PartRow row = rows.get(i);
            switch (row.kind()) {
                case 'h' -> heading(row.text(), rx0, ry, rx1);
                case 'g' -> governorRow(rx0, ry, rx1, nameW, row.index());
                case 'b' -> bodyRow(rx0, ry, rx1, nameW, row.index());
                case 'w' -> wheelRow(rx0, ry, rx1, nameW, row.index());
                default -> c.text(row.text(), rx0, ry + 3, WARN);
            }
        });
    }

    private void governorRow(int x0, int y, int x1, int nameW, int i) {
        Vcc4Snapshot.Governor g = s.governors().get(i);
        c.text("G" + (i + 1), x0, y + 3, g.foreign() ? WARN : LABEL);
        List<String> drives = new ArrayList<>();
        if (g.wheels() > 0) drives.add(tr("n_wheels", g.wheels()));
        if (g.jets() > 0) drives.add(tr("n_jets", g.jets()));
        String flip = tr("flip");
        String exclude = tr(g.excluded() ? "excluded" : "exclude");
        int flipW = host.width(flip) + 12;
        int exW = Math.max(host.width(tr("exclude")), host.width(tr("excluded"))) + 12;
        int bx = x1 - exW - flipW - 3;
        int ratioW = Math.max(host.width(tr("ratio", "-10.00")), host.width(tr("ratio_unknown")));
        int[] cols = columns(x0 + nameW, bx - 6, new int[]{-1, host.width(tr("rpm", -256)), ratioW}, 8);
        c.text(fit(String.join(", ", drives), cols[1] - 8 - cols[0]), cols[0], y + 3, LABEL);
        c.text(tr("rpm", g.applied()), cols[1], y + 3, LABEL);
        String ratio = g.ratio() == 0f ? tr("ratio_unknown") : tr("ratio", String.format(Locale.ROOT, "%.2f", g.ratio()));
        c.text(fit(ratio, bx - 6 - cols[2]), cols[2], y + 3, DIM);
        final long pos = g.pos();
        button(bx, y, flipW, flip, g.flipped(), () -> send(Vcc4ActionPacket.TOGGLE_GOVERNOR_FLIP, 0, 0, pos), null,
                List.of(tr("flip.hint")));
        button(bx + flipW + 3, y, exW, exclude, g.excluded(), () -> send(Vcc4ActionPacket.TOGGLE_GOVERNOR_EXCLUDE, 0, 0, pos),
                null, List.of(tr("exclude.hint")));
        if (g.foreign()) hit(x0, y, nameW, rowH - 2, null, null, List.of(tr("foreign.hint")));
    }

    private void bodyRow(int x0, int y, int x1, int nameW, int i) {
        Vcc4Snapshot.Body b = s.bodies().get(i);
        String name = "B" + (b.index() + 1);
        boolean alone = b.roleOf().neverTouched();
        c.text(name, x0, y + 3, alone ? DIM : LABEL);
        String what = trailerWhat(b) + ", " + linkText(b);
        String wheels = b.wheels() > 0 ? tr("n_wheels", b.wheels()) : tr("body.no_wheels");
        int[] cols = columns(x0 + nameW, x1, new int[]{-1, host.width(tr("n_wheels", 16)) + 8, host.width(tr("body.unplaced"))}, 8);
        c.text(fit(what, cols[1] - 8 - cols[0]), cols[0], y + 3, LABEL);
        c.text(wheels, cols[1], y + 3, DIM);
        if (!b.placed()) {
            c.text(tr("body.unplaced"), cols[2], y + 3, WARN);
            hit(cols[2], y, host.width(tr("body.unplaced")), rowH - 2, null, null, List.of(tr("body.unplaced.hint")));
        }
        List<String> tip = new ArrayList<>();
        tip.add(what);
        tip.add(tr("trailers.role." + b.roleOf().key() + ".hint"));
        if (b.takesMode()) tip.add(tr("body.mode_hint", tr("trailermode." + TrailerMode.byOrdinal(b.mode()).key())));
        hit(x0, y, cols[1] - 8 - x0, rowH - 2, b.takesMode() ? () -> setPage(Page.TRAILERS) : null, null, tip);
    }

    private void wheelRow(int x0, int y, int x1, int nameW, int i) {
        Vcc4Snapshot.Wheel wh = s.wheels().get(i);
        String side = wh.lateral() < -1.0e-3 ? tr("side.left") : (wh.lateral() > 1.0e-3 ? tr("side.right") : tr("side.centre"));
        String name = (wh.body() == 0 ? "A" + (wh.axle() + 1) : "B" + (wh.body() + 1)) + " " + side;
        boolean lit = focus.contains(new PartIndex.Ref(PartIndex.Kind.WHEEL, i));
        String role = wh.body() == 0 ? tr("role." + PartIndex.roleOf(s, wh.axle()).key()) : bodyWheelRole(wh.body());
        String gov = wh.governor() >= 0 ? "G" + (wh.governor() + 1) : tr("free_rolling");
        String angle = wh.commanded() ? tr("deg", String.format(Locale.ROOT, "%+.0f", wh.angle())) : "-";
        String rpm = tr("rpm", Math.round(wh.rpm()));
        int roleW = Math.max(host.width(tr("role.steer")), host.width(tr("role.straight")));
        for (TrailerMode m : TrailerMode.values()) roleW = Math.max(roleW, host.width(tr("trailermode." + m.key())));
        roleW = Math.max(roleW, host.width(tr("trailers.not_driven")));
        int[] cols = columns(x0, x1, new int[]{Math.max(nameW, host.width(name)), roleW + 4,
                host.width(tr("free_rolling")) + 4, host.width(tr("deg", "+30")), -1}, 10);
        c.text(name, cols[0], y + 3, lit ? INK : LABEL);
        c.text(role, cols[1], y + 3, LABEL);
        c.text(gov, cols[2], y + 3, wh.governor() >= 0 ? LABEL : DIM);
        c.text(angle, cols[3], y + 3, DIM);
        c.text(fit(rpm, x1 - cols[4]), cols[4], y + 3, DIM);
    }

    String bodyWheelRole(int body) {
        Vcc4Snapshot.Body b = PartIndex.bodyOf(s, body);
        if (b != null && b.takesMode()) return tr("trailermode." + TrailerMode.byOrdinal(b.mode()).key());
        if (b != null && b.roleOf().neverTouched()) return tr("trailers.not_driven");
        return tr("role.straight");
    }

    String controlName(Control ctl) {
        return tr("control." + ctl.key());
    }

    String capsText(Keymap km, Control ctl) {
        String plus = capLabel(km.plus(ctl));
        if (!ctl.isAxis() || km.minus(ctl) == KeyIds.NONE) return km.plus(ctl) == KeyIds.NONE ? capLabel(km.minus(ctl)) : plus;
        if (km.plus(ctl) == KeyIds.NONE) return capLabel(km.minus(ctl));
        return plus + "/" + capLabel(km.minus(ctl));
    }

    String capLabel(int id) {
        if (id == KeyIds.NONE) return "-";
        return KeyText.shortName(host.keyName(id));
    }

    String keyLabel(int id) {
        String name = host.keyName(id);
        return name.isEmpty() ? tr("key.unbound") : name;
    }

    private String movesShort(Control ctl) {
        Set<PartIndex.Ref> refs = PartIndex.partsOf(ctl, s);
        if (refs.isEmpty()) return tr("moves.nothing");
        List<String> parts = new ArrayList<>();
        int wheels = PartIndex.wheelCount(refs);
        if (wheels > 0 && ctl != Control.STEER) parts.add(tr("n_wheels", wheels));
        parts.addAll(PartIndex.labels(refs, s, ctl));
        return String.join(", ", parts);
    }

    private String movesLong(Control ctl, Keymap km) {
        Set<PartIndex.Ref> refs = PartIndex.partsOf(ctl, s);
        if (refs.isEmpty()) {
            if (ctl.isAux()) return tr("moves.nothing_aux", controlName(ctl));
            if (ctl == Control.LIFT) return tr("moves.nothing_lift");
            if (ctl == Control.SLIDE) return tr("moves.nothing_slide");
            if (ctl == Control.BEAM) return tr("moves.nothing_beam");
            return tr("moves.nothing");
        }
        List<String> parts = new ArrayList<>();
        int wheels = PartIndex.wheelCount(refs);
        List<String> labels = PartIndex.labels(refs, s, ctl);
        switch (ctl) {
            case THROTTLE -> {
                if (wheels > 0) parts.add(tr("moves.drives", wheels));
                parts.addAll(labels);
            }
            case STEER -> {
                if (SteerPreset.byOrdinal(s.preset()) == SteerPreset.SKID && wheels > 0) parts.add(tr("moves.skid"));
                parts.addAll(labels);
            }
            case BRAKE -> {
                if (wheels > 0) parts.add(tr("moves.brakes", wheels));
                for (String l : labels) {
                    if (l.startsWith("J")) parts.add(tr("moves.stops", l));
                    else parts.add(l);
                }
            }
            default -> parts.addAll(labels);
        }
        return String.join(", ", parts);
    }

    private List<String> controlTip(Control ctl, Keymap km) {
        List<String> tip = new ArrayList<>();
        tip.add(controlName(ctl) + ": " + (km.bound(ctl) ? capsText(km, ctl) : tr("legend_no_key")));
        tip.add(tr("control." + ctl.key() + ".hint"));
        tip.add(movesLong(ctl, km));
        tip.add(tr("legend.click"));
        return tip;
    }

    private boolean hasJob(JetMixer.Job job) {
        for (Vcc4Snapshot.Jet j : s.jets()) if (j.job() == job.ordinal() && j.role() != JetRole.OFF.ordinal()) return true;
        return false;
    }

    private float controlValue(Control ctl) {
        return ctl.ordinal() < s.controlValues().length ? s.controlValues()[ctl.ordinal()] : 0f;
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (captureControl != null && button >= 2) {
            finishCapture(host.resolveMouse(button));
            return true;
        }
        if (popup != null) {
            popup = null;
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.popup() && hit.contains(x, y) && hit.left() != null) {
                    hit.left().run();
                    host.click();
                    break;
                }
            }
            return true;
        }
        boolean wasPicking = captureControl != null;

        captureControl = null;
        captureNote = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.popup() || !hit.contains(x, y)) continue;
            Runnable action = button == MOUSE_RIGHT ? hit.right() : hit.left();
            if (action == null) continue;
            action.run();
            host.click();
            return true;
        }
        return wasPicking;
    }

    public boolean mouseScrolled(double x, double y, double dy) {
        if (dy == 0.0 || popup != null) return false;
        for (ScrollArea a : scrollAreas) {
            if (x >= a.x0() && x < a.x1() && y >= a.y0() && y < a.y1()) {
                scroll[a.page().ordinal()] += dy > 0 ? -1 : 1;
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int glfwKey, int scanCode) {
        if (captureControl != null) {
            if (glfwKey == KEY_ESCAPE) {
                captureControl = null;
                captureNote = null;
            } else if (glfwKey == KEY_BACKSPACE || glfwKey == KEY_DELETE) {
                send(Vcc4ActionPacket.SET_KEY, captureControl.ordinal(), KeyIds.NONE, captureSlot);
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
        if (id != KeyIds.NONE) {
            previewKeys.add(id);
            lastPreview = id;
        }
        return false;
    }

    public void keyReleased(int glfwKey, int scanCode) {
        int id = host.resolveKey(glfwKey, scanCode);
        previewKeys.remove(id);
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
        send(Vcc4ActionPacket.SET_KEY, captureControl.ordinal(), id, captureSlot);
        captureControl = null;
        captureNote = null;
    }

    private void send(int action, int a, int b, long target) {
        host.send(action, a, b, target);
    }

    private void option(int id, int value) {
        send(Vcc4ActionPacket.SET_OPTION, id, value, 0L);
    }

    // Route

    static final int ROUTE_ASSIGN = 1;
    static final int ROUTE_UNASSIGN = 2;
    static final int ROUTE_START = 3;
    static final int ROUTE_PAUSE = 4;
    static final int ROUTE_SKIP_STOP = 5;
    static final int ROUTE_ARM_RECORD = 8;
    static final int ROUTE_STOP_RECORD = 9;
    static final int ROUTE_OPTION = 10;
    static final int ROUTE_CANCEL_RECORD = 11;
    static final int ROUTE_KEEP_TRACE = 12;
    static final int ROUTE_REMOVE_SCHEDULE = 13;

    static final int ROUTE_OPT_CRUISE = 1;
    static final int ROUTE_OPT_COMFORT = 2;
    static final int ROUTE_OPT_START_ON_ASSIGN = 3;
    static final int ROUTE_OPT_HORN = 4;

    static final int ROUTE_COMFORT_PATHWAY = 999;

    static final float ROUTE_CRUISE_MAX = 40f;

    private void routePage() {
        int x0 = left + PAD;
        int x1 = left + w - PAD;
        int y = top + CONTENT_Y;
        int y1 = top + h - PAD;
        RouteSnapshot r = s.route();
        if (!s.onCraft()) {
            for (String line : wrap(tr("not_on_craft"), x1 - x0)) {
                c.text(line, x0, y, DIM);
                y += LINE;
            }
            return;
        }
        if (r.recording() || r.recordArmed()) {
            recordPanel(r, x0, y, x1, y1);
            return;
        }
        int optionsTop = flow(routeOptions(r), x0, x1, y1);
        int foot = flow(routeOrders(r), x0, x1, optionsTop - 4) - 6;
        if (r.assigned()) routeStatus(r, x0, y, x1, foot);
        else routeChoices(r, x0, y, x1, foot);
    }

    private List<Group> routeOrders(RouteSnapshot r) {
        List<Group> groups = new ArrayList<>();
        if (r.assigned()) {
            RouteRunner.State st = RouteRunner.State.byOrdinal(r.state());
            if (st.drives()) {
                groups.add(buttonGroup(tr("route.pause"), () -> routeOrder(ROUTE_PAUSE, 0), tr("route.pause.hint")));
            } else {
                boolean resume = st == RouteRunner.State.PAUSED || st == RouteRunner.State.MANUAL;
                groups.add(buttonGroup(tr(resume ? "route.resume" : "route.start"), () -> routeOrder(ROUTE_START, 0),
                        tr("route.start.hint")));
            }
            if (r.stopS().length > 0) {
                groups.add(buttonGroup(tr("route.skip"), () -> routeOrder(ROUTE_SKIP_STOP, 0), tr("route.skip.hint")));
            }
            groups.add(buttonGroup(tr("route.unassign"), () -> routeOrder(ROUTE_UNASSIGN, 0), tr("route.unassign.hint")));
            groups.add(buttonGroup(tr("route.keep_trace"), () -> routeOrder(ROUTE_KEEP_TRACE, 0), tr("route.keep_trace.hint")));
            if (r.hasSchedule()) {
                groups.add(buttonGroup(tr("route.remove_schedule"), () -> routeOrder(ROUTE_REMOVE_SCHEDULE, 0),
                        tr("route.remove_schedule.hint")));
            }
        }
        groups.add(buttonGroup(tr("route.record"), () -> routeOrder(ROUTE_ARM_RECORD, 0), tr("route.record.hint")));
        return groups;
    }

    private List<Group> routeOptions(RouteSnapshot r) {
        List<Group> groups = new ArrayList<>();
        float cruise = r.cruiseOverride();
        float own = r.pathwayCruise();
        String value = cruise > 0f ? tr("speed_value", String.format(Locale.ROOT, "%.1f", cruise)) : tr("route.cruise_pathway");
        String fastest = tr("speed_value", "40.0");
        String widest = host.width(fastest) >= host.width(tr("route.cruise_pathway")) ? fastest : tr("route.cruise_pathway");
        groups.add(stepperGroup(tr("route.cruise"), value, widest,
                () -> routeOption(ROUTE_OPT_CRUISE, Math.round(stepCruise(cruise, own, -1) * 10f)),
                () -> routeOption(ROUTE_OPT_CRUISE, Math.round(stepCruise(cruise, own, 1) * 10f)),
                tr("route.cruise.hint")));
        String comfort = tr("route.comfort", comfortName(r.comfort()));
        int cw = host.width(comfort) + 12;
        groups.add(new Group(cw, (x, y) -> button(x, y, cw, comfort, false, () -> comfortMenu(x, y + BUTTON_H, r.comfort()),
                null, List.of(tr("route.comfort.hint")))));
        groups.add(checkGroup(tr("route.start_on_assign"), r.startOnAssign(),
                () -> routeOption(ROUTE_OPT_START_ON_ASSIGN, r.startOnAssign() ? 0 : 1), tr("route.start_on_assign.hint")));
        groups.add(checkGroup(tr("route.horn"), r.horn(), () -> routeOption(ROUTE_OPT_HORN, r.horn() ? 0 : 1),
                tr("route.horn.hint")));
        return groups;
    }

    private void routeStatus(RouteSnapshot r, int x0, int y, int x1, int limit) {
        RouteRunner.State st = RouteRunner.State.byOrdinal(r.state());
        boolean others = false;
        for (RouteSnapshot.Choice ch : r.choices()) others |= !ch.id().equals(r.pathway());
        int changeW = others ? host.width(tr("route.change")) + 12 : 0;
        routeSwatch(x0, y + 3, r.colour());
        String state = routeWord(r.state());
        int stateW = host.width(state);
        int nameX = x0 + 13;
        int nameMax = Math.max(24, x1 - nameX - stateW - 10 - (changeW > 0 ? changeW + 6 : 0));
        String name = fit(r.pathwayName(), nameMax);
        c.text(name, nameX, y + 3, INK);
        if (!name.equals(r.pathwayName())) hit(nameX, y, host.width(name), BUTTON_H, null, null, List.of(r.pathwayName()));
        c.text(state, nameX + host.width(name) + 10, y + 3, routeColour(st));
        if (changeW > 0) {
            int bx = x1 - changeW;
            int by = y;
            button(bx, by, changeW, tr("route.change"), false, () -> choicesMenu(bx, by + BUTTON_H, r), null,
                    List.of(tr("route.change.hint")));
        }
        y += BUTTON_H + 3;

        List<String> why = new ArrayList<>();
        if (!r.reason().isEmpty()) why.add(r.reason());
        String hint = routeHint(st);
        if (!hint.isEmpty()) why.addAll(wrap(hint, x1 - x0));
        for (int i = 0; i < Math.min(3, why.size()); i++) {
            if (y + LINE > limit) return;
            routeText(why.get(i), x0, y, x1 - x0, i == 0 && !r.reason().isEmpty() && routeColour(st) == WARN ? WARN : DIM);
            y += LINE;
        }

        if (r.hasSchedule() && !r.schedule().isEmpty()) {
            if (y + LINE > limit) return;
            routeText(r.schedule(), x0, y, x1 - x0, LABEL);
            y += LINE;
        }
        y += 3;

        if (r.length() > 0f && y + 14 + LINE <= limit) {
            routeStrip(r, x0, y, x1);
            y += 15;
            String where = tr("route.progress", Math.round(r.progress()), Math.round(r.length()));
            String kind = tr(r.closed() ? "route.kind.loop" : "route.kind.one_way");
            routeText(where, x0, y, x1 - x0 - host.width(kind) - 8, DIM);
            c.text(kind, x1 - host.width(kind), y, DIM);
            y += LINE + 4;
        }

        if (y + LINE > limit) return;
        String next;
        if (st == RouteRunner.State.WAITING) next = tr("route.waiting_at", r.nextStopName().isEmpty() ? "?" : r.nextStopName());
        else if (r.nextStop() >= 0) next = r.eta() >= 0f
                ? tr("route.next_eta", r.nextStopName(), routeSeconds(Math.round(r.eta())))
                : tr("route.next", r.nextStopName());
        else next = tr(r.closed() ? "route.no_stops_loop" : "route.no_stops_one_way");
        routeText(next, x0, y, x1 - x0, INK);
        y += LINE;
        if (st == RouteRunner.State.WAITING && r.waitFraction() >= 0f) {
            if (y + 11 > limit) return;
            int bw = Math.min(120, (x1 - x0) / 3);
            well(x0, y, bw, 9, RECESSED);
            int fill = Math.round((bw - 2) * clamp(r.waitFraction(), 0f, 1f));
            if (fill > 0) c.fill(x0 + 1, y + 1, x0 + 1 + fill, y + 8, GOOD);
            routeText(r.waitText(), x0 + bw + 6, y, x1 - (x0 + bw + 6), LABEL);
            y += 12;
        } else if (!r.rules().isEmpty()) {
            if (y + LINE > limit) return;
            routeText(tr("route.leaves_when", r.rules()), x0, y, x1 - x0, DIM);
            y += LINE;
        }

        List<String> bits = new ArrayList<>();
        if (r.laps() > 0 && r.lastLap() > 0f) bits.add(tr("route.laps", r.laps(), routeSeconds(Math.round(r.lastLap()))));
        if (st.drives() && r.plannedSpeed() > 0f) bits.add(tr("route.planned", String.format(Locale.ROOT, "%.1f", r.plannedSpeed())));
        if (!bits.isEmpty()) {
            if (y + LINE > limit) return;
            routeText(String.join("   ", bits), x0, y, x1 - x0, DIM);
            y += LINE;
        }
        if (!r.learned() && y + LINE <= limit) routeText(tr("route.learning"), x0, y, x1 - x0, DIM);
    }

    private void routeChoices(RouteSnapshot r, int x0, int y, int x1, int limit) {
        List<RouteSnapshot.Choice> choices = r.choices();
        List<String> intro = wrap(tr(choices.isEmpty() ? "route.none_empty" : "route.none"), x1 - x0);
        for (String line : intro.subList(0, Math.min(3, intro.size()))) {
            if (y + LINE > limit) return;
            c.text(line, x0, y, DIM);
            y += LINE;
        }
        if (choices.isEmpty() || y + 4 + 2 * rowH > limit) return;
        y += 2;
        heading(tr("route.pathways"), x0, y - 4, x1);
        y += 10;
        int sendW = host.width(tr("route.send")) + 12;
        list(x0, y, x1, limit, choices.size(), Page.ROUTE, (i, rx0, ry, rx1) -> {
            RouteSnapshot.Choice ch = choices.get(i);
            routeSwatch(rx0, ry + (BUTTON_H - 9) / 2, ch.colour());
            String nodes = tr("route.choice_nodes", ch.nodes());
            int nw = host.width(nodes);
            int nodesX = rx1 - sendW - 8 - nw;
            String name = fit(ch.name(), Math.max(10, nodesX - 8 - (rx0 + 13)));
            c.text(name, rx0 + 13, ry + 3, INK);
            c.text(nodes, nodesX, ry + 3, DIM);
            final UUID id = ch.id();
            button(rx1 - sendW, ry, sendW, tr("route.send"), false, () -> host.sendOrder(ROUTE_ASSIGN, s.console(), id, 0), null,
                    List.of(tr("route.send.hint", ch.name())));
        });
    }

    private void recordPanel(RouteSnapshot r, int x0, int y, int x1, int y1) {
        List<Group> groups = new ArrayList<>();
        if (r.recording()) {
            groups.add(buttonGroup(tr("route.stop_keep"), () -> routeOrder(ROUTE_STOP_RECORD, 0), tr("route.stop_keep.hint")));
            groups.add(buttonGroup(tr("route.stop_loop"), () -> routeOrder(ROUTE_STOP_RECORD, 1), tr("route.stop_loop.hint")));
        }
        groups.add(buttonGroup(tr("route.cancel"), () -> routeOrder(ROUTE_CANCEL_RECORD, 0), tr("route.cancel.hint")));
        int foot = flow(groups, x0, x1, y1) - 6;
        String head = r.recording() ? tr("route.recording", r.recordedPoints()) : tr("route.record_armed");
        routeText(head, x0, y, x1 - x0, r.recording() ? WARN : INK);
        y += LINE + 3;
        for (String line : wrap(tr(r.recording() ? "route.recording.hint" : "route.record_armed.hint"), x1 - x0)) {
            if (y + LINE > foot) break;
            c.text(line, x0, y, DIM);
            y += LINE;
        }
    }

    private void routeStrip(RouteSnapshot r, int x0, int y, int x1) {
        well(x0, y, x1 - x0, 12, RECESSED);
        int ix0 = x0 + 4;
        int ix1 = x1 - 4;
        float len = Math.max(1f, r.length());
        c.fill(ix0, y + 5, ix1, y + 7, PathwayBoard.DYE[r.colour() & 15]);
        for (float stop : r.stopS()) {
            int px = ix0 + Math.round((ix1 - ix0) * clamp(stop / len, 0f, 1f));
            c.fill(px - 1, y + 2, px + 1, y + 10, INK);
        }
        int vx = ix0 + Math.round((ix1 - ix0) * clamp(r.progress() / len, 0f, 1f));
        c.fill(vx - 3, y + 1, vx + 3, y + 11, OUTLINE);
        c.fill(vx - 2, y + 2, vx + 2, y + 10, HIGHLIGHT);
        hit(x0, y, x1 - x0, 12, null, null, List.of(tr("route.strip.hint")));
    }

    private void routeSwatch(int x, int y, int dye) {
        c.fill(x, y, x + 9, y + 9, OUTLINE);
        c.fill(x + 1, y + 1, x + 8, y + 8, PathwayBoard.DYE[dye & 15]);
    }

    private void routeText(String text, int x, int y, int max, int colour) {
        if (text == null || text.isEmpty() || max <= 0) return;
        String shown = fit(text, max);
        c.text(shown, x, y, colour);
        if (!shown.equals(text)) hit(x, y - 1, host.width(shown), LINE, null, null, List.of(text));
    }

    private void choicesMenu(int x, int y, RouteSnapshot r) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        int current = -1;
        for (RouteSnapshot.Choice ch : r.choices()) {
            if (ch.id().equals(r.pathway())) current = labels.size();
            labels.add(fit(ch.name(), 200));
            final UUID id = ch.id();
            actions.add(() -> host.sendOrder(ROUTE_ASSIGN, s.console(), id, 0));
        }
        openPopup(x, y, labels, actions, current);
    }

    private void comfortMenu(int x, int y, int current) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (Comfort cf : Comfort.values()) {
            labels.add(comfortName(cf.ordinal()));
            final int v = cf.ordinal();
            actions.add(() -> routeOption(ROUTE_OPT_COMFORT, v));
        }
        labels.add(comfortName(-1));
        actions.add(() -> routeOption(ROUTE_OPT_COMFORT, ROUTE_COMFORT_PATHWAY));
        openPopup(x, y, labels, actions, current < 0 || current >= Comfort.values().length ? Comfort.values().length : current);
    }

    private String comfortName(int ordinal) {
        if (ordinal < 0 || ordinal >= Comfort.values().length) return tr("route.comfort_pathway");
        return tr("route.comfort." + Comfort.byOrdinal(ordinal).key);
    }

    private String routeWord(int state) {
        return tr("route.state." + RouteRunner.State.byOrdinal(state).key);
    }

    private static int routeColour(RouteRunner.State st) {
        return switch (st) {
            case BLOCKED, STUCK, LOST, NO_BUDGET, HAZARD -> WARN;
            case PAUSED, MANUAL, FINISHED, RECORDING -> DIM;
            default -> GOOD;
        };
    }

    private String routeHint(RouteRunner.State st) {
        return switch (st) {
            case PAUSED, MANUAL, FINISHED, NO_BUDGET, CALIBRATING, LOST, STUCK, HAZARD -> tr("route.hint." + st.key);
            default -> "";
        };
    }

    private String routeSeconds(int sec) {
        if (sec < 60) return tr("route.time.s", sec);
        int m = sec / 60;
        int rest = sec % 60;
        return rest == 0 ? tr("route.time.min", m) : tr("route.time.min_s", m, rest);
    }

    private void routeOrder(int op, int a) {
        host.sendOrder(op, s.console(), s.route().pathway(), a);
    }

    private void routeOption(int id, int value) {
        routeOrder(ROUTE_OPTION, id * 1000 + value);
    }

    static float stepCruise(float override, float pathwayCruise, int dir) {
        float from = override > 0f ? override : Math.max(1f, pathwayCruise);
        float step = from < 4f || (from == 4f && dir < 0) ? 0.5f : 1f;
        float v = Math.round((from + dir * step) * 2f) / 2f;
        if (v < 1f) return 0f;
        return Math.min(ROUTE_CRUISE_MAX, v);
    }

    // Lists and flow

    interface RowDrawer {
        void row(int index, int x0, int y, int x1);
    }

    private void list(int x0, int y0, int x1, int y1, int count, Page p, RowDrawer drawer) {
        int visible = Math.max(1, (y1 - y0) / rowH);
        int max = Math.max(0, count - visible);
        int first = clampInt(scroll[p.ordinal()], 0, max);
        scroll[p.ordinal()] = first;
        boolean bar = count > visible;
        int rx1 = bar ? x1 - 10 : x1;
        for (int i = first; i < Math.min(count, first + visible); i++) {
            drawer.row(i, x0, y0 + (i - first) * rowH, rx1);
        }
        if (bar) {
            int sh = visible * rowH - 2;
            well(x1 - 6, y0, 6, sh, RECESSED);
            int knob = Math.max(10, sh * visible / count);
            int ky = y0 + (sh - knob) * first / Math.max(1, max);
            bevel(x1 - 6, ky, 6, knob, false);
        }
        scrollAreas.add(new ScrollArea(x0, y0, x1, y0 + visible * rowH, p));
    }

    private record Group(int width, GroupDrawer drawer) {
    }

    interface GroupDrawer {
        void draw(int x, int y);
    }

    private Group stepperGroup(String label, String value, String widest, Runnable minus, Runnable plus, String tip) {
        int lw = host.width(label);
        int vw = Math.max(host.width(value), host.width(widest));
        return new Group(lw + 4 + 12 + 4 + vw + 4 + 12, (x, y) -> {
            c.text(label, x, y + 3, LABEL);
            stepper(x + lw + 4, y, value, vw, minus, plus, tip);
        });
    }

    private Group checkGroup(String label, boolean on, Runnable toggle, String tip) {
        return new Group(12 + host.width(label), (x, y) -> checkbox(x, y + 2, on, label, toggle, List.of(tip)));
    }

    private Group buttonGroup(String label, Runnable action, String tip) {
        int bw = host.width(label) + 12;
        return new Group(bw, (x, y) -> button(x, y, bw, label, false, action, null, List.of(tip)));
    }

    private int flow(List<Group> groups, int x0, int x1, int bottom) {
        List<List<Group>> lines = new ArrayList<>();
        List<Group> line = new ArrayList<>();
        int used = 0;
        for (Group g : groups) {
            int need = (line.isEmpty() ? 0 : 12) + g.width();
            if (!line.isEmpty() && used + need > x1 - x0) {
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
                need = g.width();
            }
            line.add(g);
            used += need;
        }
        if (!line.isEmpty()) lines.add(line);
        int lineH = BUTTON_H + 3;
        int topY = bottom - lines.size() * lineH + 3;
        int y = topY;
        for (List<Group> l : lines) {
            int x = x0;
            for (Group g : l) {
                g.drawer().draw(x, y);
                x += g.width() + 12;
            }
            y += lineH;
        }
        return topY;
    }

    private static int[] columns(int x0, int x1, int[] widths, int gap) {
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

    ConsoleCanvas canvas() {
        return c;
    }

    ConsoleHost host() {
        return host;
    }

    Set<PartIndex.Ref> focus() {
        return focus;
    }

    int mouseX() {
        return mx;
    }

    int mouseY() {
        return my;
    }

    void hit(int x, int y, int hw, int hh, @Nullable Runnable onLeft, @Nullable Runnable onRight, @Nullable List<String> tip) {
        hits.add(new Hit(x, y, hw, hh, onLeft, onRight, tip, false));
    }

    private void button(int x, int y, int bw, String text, boolean pressed, @Nullable Runnable onLeft, @Nullable Runnable onRight,
                        @Nullable List<String> tip) {
        boolean hot = popup == null && mx >= x && my >= y && mx < x + bw && my < y + BUTTON_H;
        if (pressed) {
            well(x, y, bw, BUTTON_H, SHADOW);
            c.text(text, x + (bw - host.width(text)) / 2 + 1, y + 4, HIGHLIGHT);
        } else {
            bevel(x, y, bw, BUTTON_H, hot);
            c.text(text, x + (bw - host.width(text)) / 2, y + 3, LABEL);
        }
        hit(x, y, bw, BUTTON_H, onLeft, onRight, tip);
    }

    private void stepper(int x, int y, String value, int valueW, Runnable minus, Runnable plus, String tip) {
        button(x, y, 12, "-", false, minus, null, List.of(tip));
        c.text(value, x + 16 + (valueW - host.width(value)) / 2, y + 3, INK);
        button(x + 16 + valueW + 4, y, 12, "+", false, plus, null, List.of(tip));
    }

    private void checkbox(int x, int y, boolean on, String text, Runnable toggle, List<String> tip) {
        well(x, y, 9, 9, RECESSED);
        if (on) {
            c.fill(x + 2, y + 2, x + 8, y + 8, INK);
            c.fill(x + 3, y + 3, x + 7, y + 7, HIGHLIGHT);
            c.fill(x + 4, y + 4, x + 6, y + 6, INK);
        }
        if (!text.isEmpty()) c.text(text, x + 12, y + 1, LABEL);
        hit(x, y - 1, 12 + (text.isEmpty() ? 0 : host.width(text)), 11, toggle, null, tip);
    }

    private void tab(int x, int y, int tw, String text, boolean on, boolean alert) {
        c.fill(x, y, x + tw, y + TAB_H, on ? HIGHLIGHT : SHADOW);
        c.fill(x + 1, y + 1, x + tw - 1, y + TAB_H - 1, on ? FACE : RECESSED);
        if (on) {
            c.fill(x + 1, y + TAB_H - 1, x + tw, y + TAB_H, SHADOW);
            c.fill(x + tw - 1, y + 1, x + tw, y + TAB_H, SHADOW);
        }
        c.text(text, x + 6, y + 3, on ? LABEL : INK);
        if (alert) c.text("!", x + 6 + host.width(text) + 3, y + 3, WARN);
    }

    private void openPopup(int x, int y, List<String> labels, List<Runnable> actions, int current) {
        int pw = 0;
        for (String l : labels) pw = Math.max(pw, host.width(l) + 14);
        popup = new Popup(x, y, pw, labels, actions, current);
    }

    private void drawPopup() {
        Popup p = popup;
        if (p == null) return;
        c.layer();
        int ph = p.labels().size() * 12 + 4;
        int px = Math.min(p.x(), left + w - PAD - p.w());
        int py = p.y();
        if (py + ph > top + h - 2) py = Math.max(top + 2, p.y() - BUTTON_H - ph);
        bevel(px, py, p.w(), ph, false);
        for (int i = 0; i < p.labels().size(); i++) {
            int ry = py + 2 + i * 12;
            boolean hot = mx >= px && mx < px + p.w() && my >= ry && my < ry + 12;
            if (i == p.current()) c.fill(px + 2, ry, px + p.w() - 2, ry + 12, RECESSED);
            else if (hot) c.fill(px + 2, ry, px + p.w() - 2, ry + 12, FACE_HOT);
            c.text(p.labels().get(i), px + 7, ry + 2, i == p.current() ? HIGHLIGHT : LABEL);
            hits.add(new Hit(px + 2, ry, p.w() - 4, 12, p.actions().get(i), null, null, true));
        }
    }

    void bevel(int x, int y, int bw, int bh, boolean hot) {
        c.fill(x, y, x + bw, y + bh, hot ? HIGHLIGHT : OUTLINE);
        c.fill(x + 1, y + 1, x + bw - 1, y + bh - 1, hot ? FACE_HOT : FACE);
        c.fill(x + 1, y + 1, x + bw - 1, y + 2, HIGHLIGHT);
        c.fill(x + 1, y + 1, x + 2, y + bh - 1, HIGHLIGHT);
        c.fill(x + 1, y + bh - 2, x + bw - 1, y + bh - 1, SHADOW);
        c.fill(x + bw - 2, y + 1, x + bw - 1, y + bh - 1, SHADOW);
    }

    void well(int x, int y, int ww, int wh, int face) {
        c.fill(x, y, x + ww, y + wh, SHADOW);
        c.fill(x + 1, y + 1, x + ww, y + wh, HIGHLIGHT);
        c.fill(x + 1, y + 1, x + ww - 1, y + wh - 1, face);
    }

    String tr(String key, Object... args) {
        return host.tr(L + key, args);
    }

    String fit(String text, int max) {
        if (text == null) return "";
        if (host.width(text) <= max) return text;
        String dots = "...";
        int dw = host.width(dots);
        if (max <= dw) return "";
        int end = text.length();
        while (end > 0 && host.width(text.substring(0, end)) + dw > max) end--;
        return text.substring(0, end).stripTrailing() + dots;
    }

    List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (host.width(candidate) <= max) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) out.add(line.toString());
            line.setLength(0);
            line.append(host.width(word) <= max ? word : fit(word, max));
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    private List<String> wrapAll(List<String> lines, int max) {
        List<String> out = new ArrayList<>();
        for (String l : lines) out.addAll(wrap(l, max));
        return out;
    }

    static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
