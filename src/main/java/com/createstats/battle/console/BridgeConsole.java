package com.createstats.battle.console;

import com.createstats.battle.core.BattleReport;
import com.createstats.battle.core.GunArc;
import com.createstats.battle.core.GunState;
import com.createstats.battle.core.HeaderStatus;
import com.createstats.battle.core.PackedPos;
import com.createstats.battle.core.PlaceName;
import com.createstats.battle.core.Projection;
import com.createstats.battle.core.RefitRules;
import com.createstats.battle.core.SalvoResult;
import com.createstats.battle.core.ThreatOrder;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

// the board is a damage control plotting table, grease pencil on acrylic over the ship plan
public final class BridgeConsole {
    public static final int MIN_W = 440;
    public static final int MIN_H = 256;
    public static final int MAX_W = 800;
    public static final int MAX_H = 490;
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

    static final int FRAME_OUT = 0xFF0B0D0E;
    static final int STEEL = 0xFF6F7C85;
    static final int STEEL_LIT = 0xFF96A3AB;
    static final int STEEL_DARK = 0xFF48535B;
    static final int STEEL_DEEP = 0xFF2E363C;
    static final int STEEL_STREAK = 0xFF78858E;
    static final int STEEL_STREAK_DARK = 0xFF66727B;
    static final int STENCIL = 0xFFECEDE6;
    static final int STENCIL_DIM = 0xFFC1C8C4;
    static final int PAPER = 0xFFDEE5E4;
    static final int PAPER_FLECK = 0xFFCFD8D8;
    static final int PAPER_HOT = 0xFFE9EFEE;

    static final int WIPED = 0xF0E4EAE9;
    static final int NOTE = 0xFFF1E08A;
    static final int NOTE_EDGE = 0xFFB9A64A;
    static final int PRINT = 0xFF2A4A78;
    static final int PRINT_SOFT = 0xFF6F86A6;
    static final int PRINT_RULE = 0xFFB4C3C8;
    static final int TINT_LIFT = 0xFFC8D6E1;
    static final int TINT_SEAM = 0xFFB6C6D3;
    static final int TINT_STRUCT = 0xFFA9BCCB;
    static final int RED = 0xFFCE2E24;
    static final int BLACK = 0xFF242424;
    static final int BLUE = 0xFF2752B8;
    static final int GREEN = 0xFF2C8A3E;
    static final int YELLOW = 0xFFE7AF0C;
    static final int TAG = 0xFFF4F4EE;
    static final int TAG_HOT = 0xFFFFFFFF;
    static final int TAG_EDGE = 0xFF151515;
    static final int TAG_INK = 0xFF1B1B1B;
    static final int TAG_FREE = 0xFFF2A62B;
    static final int TAG_ARMED = 0xFFE8574A;
    static final int TAB_OFF = 0xFFB9C3C4;
    static final int TAB_OFF_INK = 0xFF4F5A63;
    static final int BUTTON = 0xFFD8321F;
    static final int BUTTON_DEEP = 0xFF8E1C12;
    static final int BUTTON_LIT = 0xFFF2654E;
    static final int GLINT = 0x12FFFFFF;
    static final int HIGHLIGHTER = 0x55E7C21C;

    static final String L = "battle.create_stats.";

    static final int SIDE = 8;
    static final int GAP = 8;
    static final int LINE = 11;
    static final int TAG_H = 12;

    static final long CONFIRM_TICKS = 60L;

    static final long OPTIMISTIC_TICKS = 100L;

    static final long FRESH_TICKS = 600L;

    private static final int MOUSE_RIGHT = 1;

    public enum Page {
        BOARD("board"), GUNS("guns"), SETUP("setup");

        final String key;

        Page(String key) {
            this.key = key;
        }
    }

    private record Hit(int x, int y, int w, int h, @Nullable Runnable left, @Nullable List<String> tip) {
        boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    private final ConsoleHost host;
    private BattleReport r;
    @Nullable
    private ShapeView shape;
    @Nullable
    private DetailView detail;
    private Page page;
    private long clock;
    private JobBoard jobs = new JobBoard();
    @Nullable
    private String selectedJob;
    private int todoScroll;
    private final Map<String, Long> confirmUntil = new HashMap<>();

    private final Map<Integer, long[]> optimistic = new HashMap<>();

    private ConsoleCanvas c;
    private int left;
    private int top;
    private int w;
    private int h;
    private int mx;
    private int my;
    private boolean compact;
    private int topH;
    private int footH;
    private int todoX0;
    private int todoX1;
    private int todoY0;
    private int todoY1;
    private final List<Hit> hits = new ArrayList<>();
    private final List<int[]> marks = new ArrayList<>();
    private List<String> tooltip = List.of();

    public BridgeConsole(ConsoleHost host, BattleReport report, @Nullable ShapeView shape, @Nullable DetailView detail,
                         @Nullable Page page, long clock) {
        this.host = host;
        this.r = report;
        this.shape = shape;
        this.detail = detail;
        this.page = page == null ? Page.BOARD : page;
        this.clock = clock;
    }

    public void accept(BattleReport report, @Nullable ShapeView shape, @Nullable DetailView detail, long clock) {
        this.r = report;
        this.shape = shape;
        this.detail = detail;
        this.clock = clock;
    }

    public void jobs(JobBoard board) {
        if (board != null) this.jobs = board;
    }

    public Page page() {
        return page;
    }

    public void setPage(Page p) {
        page = p;
    }

    public List<String> tooltip() {
        return tooltip;
    }

    public static int width(int screenW) {
        return clampInt(screenW - 2 * MARGIN, MIN_W, MAX_W);
    }

    public static int height(int screenH) {
        return clampInt(screenH - 2 * MARGIN, MIN_H, MAX_H);
    }

    public void render(ConsoleCanvas canvas, int screenW, int screenH, int mouseX, int mouseY) {
        c = canvas;
        w = width(screenW);
        h = height(screenH);
        left = (screenW - w) / 2;
        top = (screenH - h) / 2;
        mx = mouseX;
        my = mouseY;
        hits.clear();
        marks.clear();
        tooltip = List.of();
        compact = h < 320;
        topH = compact ? 22 : 40;
        footH = compact ? 22 : 26;
        jobs.update(r, detail, clock);

        frame();
        topBar();
        int bx0 = left + SIDE;
        int bx1 = left + w - SIDE;
        int by0 = top + topH;
        int by1 = top + h - footH;
        int todoW = clampInt(w * 29 / 100, 132, 226);
        todoBoard(bx0, by0, bx0 + todoW, by1);
        int px0 = bx0 + todoW + GAP;
        switch (page) {
            case BOARD -> boardPage(px0, by0, bx1, by1);
            case GUNS -> gunsPage(px0, by0, bx1, by1);
            case SETUP -> setupPage(px0, by0, bx1, by1);
        }
        footBar();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.tip() != null && !hit.tip().isEmpty() && hit.contains(mx, my)) {
                tooltip = wrapAll(hit.tip(), 220);
                break;
            }
        }
    }

    private void frame() {
        c.fill(left, top, left + w, top + h, FRAME_OUT);
        c.fill(left + 1, top + 1, left + w - 1, top + h - 1, STEEL);
        for (int y = top + 2; y < top + h - 2; y++) {
            boolean bar = y < top + topH || y >= top + h - footH;
            int k = hash(y, left, 7);
            int n = bar ? 2 : 1;
            for (int s = 0; s < n; s++) {
                int hs = hash(y, s, k);
                int len = 10 + hs % (bar ? 60 : 3);
                int x = left + 2 + (hs >>> 8) % Math.max(1, w - 4 - len);
                if (!bar && x > left + SIDE - 1 && x + len < left + w - SIDE + 1) continue;
                c.fill(x, y, Math.min(left + w - 2, x + len), y + 1, (hs & 1) == 0 ? STEEL_STREAK : STEEL_STREAK_DARK);
            }
        }
        c.fill(left + 1, top + 1, left + w - 1, top + 2, STEEL_LIT);
        c.fill(left + 1, top + 1, left + 2, top + h - 1, STEEL_LIT);
        c.fill(left + 1, top + h - 2, left + w - 1, top + h - 1, STEEL_DEEP);
        c.fill(left + w - 2, top + 1, left + w - 1, top + h - 1, STEEL_DEEP);
        screw(left + 3, top + 3);
        screw(left + w - 8, top + 3);
        screw(left + 3, top + h - 8);
        screw(left + w - 8, top + h - 8);
    }

    private void screw(int x, int y) {
        c.fill(x + 1, y, x + 4, y + 5, 0xFF262D32);
        c.fill(x, y + 1, x + 5, y + 4, 0xFF262D32);
        c.fill(x + 1, y + 1, x + 4, y + 4, 0xFFA9B3B9);
        c.fill(x + 1, y + 2, x + 4, y + 3, 0xFF39424A);
    }

    private void topBar() {
        String title = tr("frame.title");
        String station = tr("frame.station");
        int tx = left + 10;
        int ty = compact ? top + 7 : top + 9;
        int slateX0 = Math.max(tx + host.width(title) + 14, left + w * 38 / 100);
        int room = slateX0 - tx - 12;
        if (compact || host.width(station) > room) {
            c.text(title, tx, ty, STENCIL);
            if (!compact) c.text(fit(shipTitle(), room), tx, ty + 12, STENCIL_DIM);
        } else {
            c.text(station, tx, ty, STENCIL);
            String second = title + "   " + shipTitle();
            c.text(fit(second, room), tx, ty + 12, STENCIL_DIM);
        }
        statusSlate(slateX0, top + (compact ? 3 : 5), left + w - 10, top + topH - (compact ? 3 : 4));
    }

    private String shipTitle() {
        String ship = r.ground() ? tr("frame.ground", r.groundSize()) : r.shipName().isEmpty() ? tr("frame.ship") : r.shipName();
        return ship.toUpperCase(Locale.ROOT);
    }

    private void statusSlate(int x0, int y0, int x1, int y1) {
        paper(x0, y0, x1, y1, false);
        HeaderStatus.Status s = r.status(clock);
        HeaderStatus.Kind k = s.kind();
        int ink = switch (k.lamp()) {
            case RED -> RED;
            case AMBER -> BLACK;
            case GREEN -> GREEN;
            case GREY -> BLUE;
        };
        boolean blinkOff = k.blinks() && (clock / 10) % 2 == 1;
        String head = tr("slate." + k.key());
        List<String> subs = slateDetails(s);
        String integ = r.hasReference() ? permilleText(r.integrityPermille()) : "?";
        String lost = !r.hasReference() ? tr("integrity.unknown") : r.lostCount() == 0 ? tr("slate.none_lost", group(r.refCount()))
                : tr("slate.lost", group(r.lostCount()), group(r.refCount()));
        int room = x1 - x0 - 12;
        if (!compact) {
            int iw = gw(integ, 2);
            int headScale = gw(head, 2) + iw + 16 <= room ? 2 : 1;
            if (!blinkOff) grease(head, x0 + 6, y0 + 3, ink, headScale);
            grease(integ, x1 - 6 - iw, y0 + 3, BLACK, 2);
            int lw = host.width(lost);
            int lx = x1 - 6 - Math.min(lw, room / 2);
            c.text(fit(lost, room / 2), lx, y0 + 21, PRINT);

            grease(firstFitting(subs, lx - 8 - (x0 + 6)), x0 + 6, y0 + 21, ink, 1);
        } else {
            int iw = gw(integ, 1);
            int hw = gw(head, 1);
            if (!blinkOff) grease(fitG(head, room - iw - 10), x0 + 5, y0 + 4, ink, 1);
            grease(integ, x1 - 5 - iw, y0 + 4, BLACK, 1);
            int sx = x0 + 5 + Math.min(hw, room - iw - 10) + 8;
            int sroom = x1 - 5 - iw - 8 - sx;
            if (sroom > 30) grease(firstFitting(subs, sroom), sx, y0 + 4, ink, 1);
        }
        List<String> tip = new ArrayList<>();
        tip.add(statusText(s));
        tip.add(tr("status." + k.key() + ".hint"));
        tip.add(lost);
        hit(x0, y0, x1 - x0, y1 - y0, null, tip);
        acrylic(x0, y0, x1, y1);
    }

    private List<String> slateDetails(HeaderStatus.Status s) {
        long hitAge = r.hitAge(clock);
        String hitText = hitAge >= 0 && hitAge < HeaderStatus.UNDER_FIRE_TICKS ? tr("slate.hit", age(hitAge)) : "";
        return switch (s.kind()) {
            case FIRE -> hitText.isEmpty() ? List.of(place(s.place())) : List.of(place(s.place()) + ". " + hitText, hitText);
            case UNDER_FIRE -> r.hits().isEmpty() ? List.of(hitText) : List.of(hitText + ": " + place(r.hits().get(0).place()), hitText);
            case SURVEYING -> List.of(tr("slate.surveying.sub", s.number()));
            case NO_POWER -> List.of(tr("slate.no_power.sub"));
            case WEAPONS_FREE -> List.of(tr("slate.weapons_free.sub"));
            default -> List.of(r.hasReference() ? tr("slate.all_clear.sub") : "");
        };
    }

    private String firstFitting(List<String> options, int room) {
        for (String o : options) {
            String up = o.toUpperCase(Locale.ROOT);
            if (gw(up, 1) <= room) return up;
        }
        return fitG(options.get(options.size() - 1).toUpperCase(Locale.ROOT), room);
    }

    String statusText(HeaderStatus.Status s) {
        return switch (s.kind()) {
            case FIRE -> tr("status.fire", place(s.place()));
            case UNDER_FIRE -> tr("status.under_fire", s.number());
            case SURVEYING -> tr("status.surveying", s.number());
            default -> tr("status." + s.kind().key());
        };
    }

    private void footBar() {
        int y0 = top + h - footH;
        int x = left + 10;
        for (Page p : Page.values()) {
            String name = tr("tab." + p.key).toUpperCase(Locale.ROOT);
            int tw = host.width(name) + 14;
            boolean on = p == page;
            int ty = on ? y0 + 1 : y0 + 4;
            int ty1 = y0 + footH - 4;
            c.fill(x, ty, x + tw, ty1, TAG_EDGE);
            c.fill(x + 1, ty + 1, x + tw - 1, ty1 - 1, on ? PAPER : TAB_OFF);
            if (on) c.fill(x + 1, ty + 1, x + tw - 1, ty + 2, PAPER_HOT);
            c.text(name, x + 7, ty + (ty1 - ty - 7) / 2, on ? PRINT : TAB_OFF_INK);
            final Page target = p;
            hit(x, ty, tw, ty1 - ty, () -> setPage(target), List.of(tr("tab." + p.key + ".hint")));
            x += tw + 3;
        }

        int br = Math.max(7, footH / 2 - 3);
        int bcx = left + w - 12 - br;
        int bcy = y0 + footH / 2 - 1;
        disc(bcx, bcy + 1, br + 1, TAG_EDGE);
        disc(bcx, bcy, br, BUTTON_DEEP);
        disc(bcx, bcy, br - 1, BUTTON);
        disc(bcx - br / 3, bcy - br / 3, Math.max(1, br / 3), BUTTON_LIT);
        ring(bcx, bcy, br + 2, 0x9AE6F0F5, 1);
        hit(bcx - br - 2, bcy - br - 2, br * 2 + 5, br * 2 + 5, () -> send(BattleBridgeAction.FIRE_READY, 0, 0),
                List.of(tr("guns.fire"), tr("guns.fire.hint")));
        String fire = tr("foot.fire");
        int fx = bcx - br - 8 - host.width(fire);
        int textY = y0 + (footH - 7) / 2;

        String weapons = tr("foot.weapons");
        int ww = host.width(weapons);
        int slotW = Math.max(host.width(tr("foot.safe")), host.width(tr("foot.free"))) + 10;
        int plateX = x + 12 + ww + 5;
        int plateW = slotW * 2 + 6;
        boolean roomForFireText = fx > plateX + plateW + 12;
        if (roomForFireText) c.text(fire, fx, textY, STENCIL);
        c.text(weapons, x + 12, textY, STENCIL);
        c.fill(plateX, y0 + 4, plateX + plateW, y0 + footH - 4, TAG_EDGE);
        c.fill(plateX + 1, y0 + 5, plateX + plateW - 1, y0 + footH - 5, PAPER);
        boolean free = option(OPT_WEAPONS, r.weaponsFree() ? 1 : 0) == 1;
        int sy = y0 + (footH - TAG_H) / 2;
        List<String> safeTip = r.weaponsByRedstone() ? List.of(tr("weapons.by_redstone")) : List.of(tr("guns.safe"), tr("guns.safe.hint"));
        List<String> freeTip = r.weaponsByRedstone() ? List.of(tr("weapons.by_redstone")) : List.of(tr("guns.free"), tr("guns.free.hint"));
        slotRow(plateX + 2, sy, slotW, new String[]{tr("foot.safe"), tr("foot.free")}, free ? 1 : 0,
                r.weaponsByRedstone() ? null : v -> setWeapons(v == 1), new int[]{TAG, TAG_FREE}, List.of(safeTip, freeTip));

        int sx = plateX + plateW + 14;
        String surveyed = r.surveyedAt() < 0 ? "" : tr("foot.surveyed", age(clock - r.surveyedAt())).toUpperCase(Locale.ROOT);
        int limit = roomForFireText ? fx - 12 : bcx - br - 12;
        if (!surveyed.isEmpty() && sx + host.width(surveyed) <= limit) c.text(surveyed, sx, textY, STENCIL_DIM);
    }

    private void todoBoard(int x0, int y0, int x1, int y1) {
        todoX0 = x0;
        todoX1 = x1;
        todoY0 = y0;
        todoY1 = y1;
        paper(x0, y0, x1, y1, true);
        int ix0 = x0 + 5;
        int ix1 = x1 - 5;
        List<JobBoard.Job> open = jobs.jobs();
        List<JobBoard.Done> done = jobs.done(clock);
        c.text(tr("jobs.title"), ix0, y0 + 5, PRINT);
        String count = tr("jobs.open", open.size());
        c.text(count, ix1 - host.width(count), y0 + 5, PRINT_SOFT);
        c.fill(ix0 - 1, y0 + 15, ix1 + 1, y0 + 16, PRINT);
        boolean needsCol = x1 - x0 >= 176;
        int noX = ix0;
        int jobX = ix0 + 16;
        int needsX = needsCol ? ix1 - 50 : ix1 + 4;
        int headY = y0 + 19;
        c.text(tr("jobs.col.no"), noX, headY, PRINT_SOFT);
        c.text(fit(tr("jobs.col.job"), needsX - jobX - 4), jobX, headY, PRINT_SOFT);
        if (needsCol) c.text(tr("jobs.col.needs"), needsX, headY, PRINT_SOFT);
        c.fill(ix0 - 1, y0 + 29, ix1 + 1, y0 + 30, PRINT);

        int rowH = compact ? 21 : 29;
        int footArea = compact ? 12 : page == Page.BOARD ? 24 : 14;
        int rowsY0 = y0 + 31;
        int rowsY1 = y1 - footArea;
        int room = Math.max(1, (rowsY1 - rowsY0) / rowH);
        int entries = open.size() + done.size();
        todoScroll = clampInt(todoScroll, 0, Math.max(0, entries - room));
        c.fill(jobX - 3, y0 + 16, jobX - 2, rowsY0 + Math.min(room, Math.max(1, entries)) * rowH, PRINT_RULE);
        if (needsCol) c.fill(needsX - 4, y0 + 16, needsX - 3, rowsY0 + Math.min(room, Math.max(1, entries)) * rowH, PRINT_RULE);

        if (entries == 0) {
            int ty = (rowsY0 + rowsY1) / 2 - 12;
            String none = tr("jobs.none");
            c.text(none, (x0 + x1 - host.width(none)) / 2, ty, PRINT);
            for (String l : wrap(tr("jobs.none.hint"), ix1 - ix0 - 8)) {
                ty += LINE;
                if (ty + 9 > rowsY1) break;
                c.text(l, (x0 + x1 - host.width(l)) / 2, ty, PRINT_SOFT);
            }
        }
        int shown = 0;
        for (int i = todoScroll; i < entries && shown < room; i++, shown++) {
            int ry = rowsY0 + shown * rowH;
            if (i < open.size()) {
                jobRow(open.get(i), i == 0, noX, jobX, needsX, needsCol, ix0, ix1, ry, rowH);
            } else {
                doneRow(done.get(i - open.size()), noX, jobX, needsX, needsCol, ix1, ry, rowH);
            }
            c.fill(ix0 - 1, ry + rowH - 1, ix1 + 1, ry + rowH, PRINT_RULE);
        }

        int fy = y1 - footArea + 3;
        c.fill(ix0 - 1, fy - 2, ix1 + 1, fy - 1, PRINT);
        int below = Math.max(0, entries - todoScroll - room);
        String foot = tr("jobs.foot", open.size(), done.size());
        String more = below > 0 ? "  " + tr("jobs.more", below) : "";
        if (host.width(foot + more) > ix1 - ix0 && below > 0) more = "  " + tr("jobs.more.short", below);
        if (host.width(foot + more) > ix1 - ix0) foot = tr("jobs.open", open.size()).toUpperCase(Locale.ROOT);
        c.text(fit(foot + more, ix1 - ix0), ix0, fy, PRINT);
        if (!compact && page == Page.BOARD) c.text(fit(tr("jobs.foot.plan"), ix1 - ix0), ix0, fy + 10, PRINT_SOFT);
        acrylic(x0, y0, x1, y1);
    }

    private int jobInk(JobBoard.Job j) {
        return j.urgency() == JobBoard.RED ? RED : j.urgency() == JobBoard.WAIT ? BLUE : BLACK;
    }

    private void jobRow(JobBoard.Job j, boolean first, int noX, int jobX, int needsX, boolean needsCol, int ix0, int ix1,
                        int ry, int rowH) {
        int ink = jobInk(j);
        boolean selected = j.key().equals(selectedJob);
        boolean hot = mx >= ix0 - 1 && mx < ix1 + 1 && my >= ry && my < ry + rowH - 1;
        if (selected) c.fill(ix0 - 1, ry, ix1 + 1, ry + rowH - 1, HIGHLIGHTER);
        else if (hot) c.fill(ix0 - 1, ry, ix1 + 1, ry + rowH - 1, 0x22FFFFFF);
        String n = String.valueOf(j.number());
        int lineGap = compact ? 9 : 11;
        int l1y = ry + (rowH - 8 - lineGap) / 2;
        grease(n, noX + Math.max(0, (14 - gw(n, 1)) / 2), l1y + lineGap / 2, ink, 1);
        int verbRoom = needsX - jobX - 6;
        String verb = jobVerb(j).toUpperCase(Locale.ROOT);
        if (gw(verb, 1) > verbRoom) verb = jobShort(j).toUpperCase(Locale.ROOT);
        grease(fitG(verb, verbRoom), jobX, l1y, ink, 1);
        c.text(fit(jobWhere(j), needsX - jobX - 6), jobX, l1y + lineGap, PRINT);
        if (needsCol) {
            String[] need = jobNeeds(j);
            if (!need[0].isEmpty()) grease(fitG(need[0], ix1 - needsX), needsX, l1y, ink, 1);
            c.text(fit(need[1], ix1 - needsX), needsX, l1y + (need[0].isEmpty() ? 0 : lineGap), PRINT);
        }
        if (first) greaseRing((ix0 + ix1) / 2, ry + rowH / 2 - 1, (ix1 - ix0) / 2 + 2, rowH / 2 + 1, RED, 2);
        List<String> tip = new ArrayList<>();
        tip.add(jobVerb(j) + ": " + jobWhere(j));
        tip.add(jobSentence(j));
        tip.add(tr(j.kind() == JobBoard.Kind.GUN || j.kind() == JobBoard.Kind.LOAD ? "jobs.click.gun" : "jobs.click.place"));
        hit(ix0 - 1, ry, ix1 - ix0 + 2, rowH - 1, () -> selectJob(j), tip);
    }

    private void doneRow(JobBoard.Done d, int noX, int jobX, int needsX, boolean needsCol, int ix1, int ry, int rowH) {
        String n = String.valueOf(d.number());
        int ty = ry + (rowH - 8) / 2;
        grease(n, noX + Math.max(0, (14 - gw(n, 1)) / 2), ty, BLACK, 1);
        String text = fitG(doneText(d).toUpperCase(Locale.ROOT), needsX - jobX - 6);
        grease(text, jobX, ty, BLACK, 1);
        c.fill(jobX - 2, ty + 3, jobX + gw(text, 1) + 2, ty + 5, BLACK);
        String ok = tr("jobs.done.ok", clockText(clock - d.at()));
        if (needsCol) grease(fitG(ok, ix1 - needsX), needsX, ty, GREEN, 1);
        hit(jobX - 2, ry, ix1 - jobX, rowH - 1, null, List.of(doneText(d), tr("jobs.done.tip", age(clock - d.at()))));
    }

    private void selectJob(JobBoard.Job j) {
        selectedJob = j.key().equals(selectedJob) ? null : j.key();
        if (selectedJob == null) return;
        if (j.kind() == JobBoard.Kind.GUN || j.kind() == JobBoard.Kind.LOAD) {
            page = Page.GUNS;
            pickerOpen = false;
        } else {
            page = Page.BOARD;
        }
    }

    String jobVerb(JobBoard.Job j) {
        return switch (j.kind()) {
            case FIRE -> tr("jobs.fire");
            case LOAD -> tr("jobs.load");
            case GUN -> tr("jobs.gun." + GunState.byOrdinal(j.state()).key());
            case PLACE -> {
                if (j.urgency() == JobBoard.WAIT && j.place().startsWith("B")) yield tr("jobs.place.broke_away");
                char k = j.place().isEmpty() ? ' ' : j.place().charAt(0);
                yield tr(k == 'P' ? "jobs.place.propeller" : k == 'G' ? "jobs.place.gun" : k == 'E' ? "jobs.place.envelope" : "jobs.place.hull");
            }
        };
    }

    String jobShort(JobBoard.Job j) {
        return switch (j.kind()) {
            case FIRE -> tr("jobs.fire.short");
            case LOAD -> tr("jobs.load.short");
            case GUN -> tr("jobs.gun." + GunState.byOrdinal(j.state()).key() + ".short");
            case PLACE -> {
                if (j.urgency() == JobBoard.WAIT && j.place().startsWith("B")) yield tr("jobs.place.broke_away.short");
                char k = j.place().isEmpty() ? ' ' : j.place().charAt(0);
                yield tr(k == 'P' ? "jobs.place.propeller.short" : k == 'G' ? "jobs.place.gun.short"
                        : k == 'E' ? "jobs.place.envelope.short" : "jobs.place.hull.short");
            }
        };
    }

    String jobWhere(JobBoard.Job j) {
        return switch (j.kind()) {
            case FIRE -> place(j.place());
            case LOAD, GUN -> tr("jobs.gun.where", gunName(j.gunId()), gunPlace(j.gunId()));
            case PLACE -> j.urgency() == JobBoard.WAIT && !r.firePlace().isEmpty() && j.place().equals(r.firePlace())
                    ? tr("jobs.place.wait", place(j.place())) : place(j.place());
        };
    }

    String[] jobNeeds(JobBoard.Job j) {
        return switch (j.kind()) {
            case FIRE -> new String[]{j.count() > 0 ? String.valueOf(j.count()) : "?", tr("jobs.fire.needs")};
            case LOAD -> gunKind(j.gunId()) == 1 ? new String[]{"", tr("jobs.load.needs.auto")} : new String[]{"1", tr("jobs.load.needs.big")};
            case GUN -> {
                GunState st = GunState.byOrdinal(j.state());
                String unit = tr("jobs.gun.needs." + st.key());
                boolean one = st == GunState.DOWN || st == GunState.NO_YAW_DRIVE || st == GunState.NO_PITCH_DRIVE;
                yield new String[]{one ? "1" : "", unit};
            }
            case PLACE -> {
                boolean prop = j.place().startsWith("P");
                String unit = prop ? tr(j.count() == 1 ? "jobs.place.needs.prop" : "jobs.place.needs.props")
                        : tr(j.count() == 1 ? "jobs.place.needs.one" : "jobs.place.needs");
                yield new String[]{group(j.count()), unit};
            }
        };
    }

    String gunPlace(int gunId) {
        int packed = gunPacked(gunId);
        ShapeView s = shape;
        if (packed < 0 || s == null) return tr("jobs.gun.type." + clampInt(gunKind(gunId), 0, 2));
        int[] cell = s.planCell(packed);
        int pw = Math.max(1, s.frame().planW());
        int ph = Math.max(1, s.frame().planH());
        int third = clampInt(cell[0] * 3 / pw, 0, 2);
        int mid = ph / 2;
        int side = cell[1] < mid - 1 ? 0 : cell[1] > mid + 1 ? 2 : 1;
        if (r.ground()) return place(PlaceName.compass(side, third, 1));
        return place(PlaceName.ship(third, side, 1));
    }

    private String jobSentence(JobBoard.Job j) {
        return switch (j.kind()) {
            case FIRE -> tr("status.fire.hint");
            case LOAD, GUN -> {
                BattleReport.GunLine g = gunById(j.gunId());
                yield g == null ? tr("jobs.gun.gone") : j.kind() == JobBoard.Kind.LOAD && g.gunState() != GunState.EMPTY
                        ? tr("jobs.load.tip", g.name()) : sentence(g);
            }
            case PLACE -> tr("jobs.place.tip", "R" + j.placeId(), place(j.place()), group(j.count()));
        };
    }

    String doneText(JobBoard.Done d) {
        return switch (d.kind()) {
            case FIRE -> tr("jobs.done.fire");
            case LOAD -> tr("jobs.done.load", gunName(d.gunId()));
            case GUN -> tr("jobs.done.gun", gunName(d.gunId()));
            case PLACE -> tr("jobs.done.place", place(d.place()));
        };
    }

    private String gunName(int id) {
        BattleReport.GunLine g = gunById(id);
        return g == null ? "G" + id : g.name();
    }

    private int gunKind(int id) {
        BattleReport.GunLine g = gunById(id);
        return g == null ? 2 : g.kind();
    }

    private void boardPage(int x0, int y0, int x1, int y1) {
        boolean two = x1 - x0 >= 470;
        int mw = two ? clampInt((x1 - x0) * 47 / 100, 212, 264) : 0;
        int sx1 = two ? x1 - mw - GAP : x1;
        shipSheet(x0, y0, sx1, y1);
        if (two) plotBoard(sx1 + GAP, y0, x1, y1);
    }

    private void shipSheet(int x0, int y0, int x1, int y1) {
        paper(x0, y0, x1, y1, true);
        int ix0 = x0 + 6;
        int ix1 = x1 - 6;
        String name = r.ground() ? tr("frame.ground", r.groundSize()) : r.shipName().isEmpty() ? tr("frame.ship") : r.shipName();
        String sub = tr(r.ground() ? "sheet.north_up" : "sheet.profile");
        int subW = host.width(sub);
        boolean showSub = host.width(name.toUpperCase(Locale.ROOT)) + subW + 12 <= ix1 - ix0;
        c.text(fit(name.toUpperCase(Locale.ROOT), ix1 - ix0 - (showSub ? subW + 12 : 0)), ix0, y0 + 5, PRINT);
        if (showSub) c.text(sub, ix1 - subW, y0 + 5, PRINT_SOFT);
        c.fill(ix0 - 1, y0 + 15, ix1 + 1, y0 + 16, PRINT);
        int y = y0 + 19;
        if (r.banner() > 0 && RefitRules.banner(r.banner(), true)) y = noteStrip(ix0, y, ix1) + 4;

        if (!r.hasReference() || shape == null) {
            emptySheet(ix0, y, ix1, y1 - 6);
            acrylic(x0, y0, x1, y1);
            return;
        }
        drawings(ix0, y, ix1, y1 - 5);
        acrylic(x0, y0, x1, y1);
    }

    private int noteStrip(int x0, int y0, int x1) {
        String accept = confirming("banner") ? tr("confirm") : tr("banner.accept");
        String keep = tr("banner.keep");
        int aw = Math.max(host.width(tr("banner.accept")), host.width(tr("confirm"))) + 10;
        int kw = host.width(keep) + 10;
        int nh = 26;
        c.fill(x0 + 1, y0 + 1, x1 + 1, y0 + nh + 1, 0x40000000);
        c.fill(x0, y0, x1, y0 + nh, NOTE_EDGE);
        c.fill(x0 + 1, y0 + 1, x1 - 1, y0 + nh - 1, NOTE);
        c.fill(x0 + (x1 - x0) / 2 - 8, y0 - 2, x0 + (x1 - x0) / 2 + 8, y0 + 3, STEEL_DARK);
        int textMax = x1 - x0 - aw - kw - 14;
        List<String> lines = wrap(tr("banner.text", group(r.banner())), textMax);
        int ty = y0 + 4;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            c.text(i == 1 && lines.size() > 2 ? fit(lines.get(1) + " ...", textMax) : lines.get(i), x0 + 4, ty, BLACK);
            ty += 9;
        }
        int bx = x1 - 3 - kw;
        tag(bx, y0 + 7, kw, TAG_H, keep, TAG, () -> send(BattleBridgeAction.ACCEPT_REFIT, 0, 0), List.of(tr("banner.keep.hint")));
        confirmTag(bx - 3 - aw, y0 + 7, aw, accept, "banner", () -> send(BattleBridgeAction.ACCEPT_REFIT, 1, 0),
                List.of(tr("banner.accept.hint")));
        hit(x0, y0, textMax + 4, nh, null, List.of(tr("banner.text", group(r.banner())), tr("banner.why")));
        return y0 + nh;
    }

    private void emptySheet(int x0, int y0, int x1, int y1) {
        String head;
        String line;
        int ink;
        if (!r.powered()) {
            head = tr("slate.no_power");
            line = tr("empty.no_power");
            ink = RED;
        } else if (r.surveyPermille() >= 0) {
            head = tr("slate.surveying");
            line = tr("empty.surveying", shipOrBox(), r.surveyPermille() / 10);
            ink = BLUE;
        } else {
            head = tr("slate.surveying");
            line = tr("empty.waiting");
            ink = BLUE;
        }
        List<String> lines = wrap(line, x1 - x0 - 10);
        int ty = (y0 + y1) / 2 - (lines.size() * LINE + 18) / 2;
        int hw = gw(head, 1);
        grease(head, (x0 + x1 - hw) / 2, ty, ink, 1);
        ty += 14;
        for (String l : lines) {
            c.text(l, (x0 + x1 - host.width(l)) / 2, ty, PRINT);
            ty += LINE;
        }
        if (r.powered() && r.surveyPermille() >= 0) {
            int bw = Math.min(160, x1 - x0 - 20);
            int bx = (x0 + x1 - bw) / 2;
            c.fill(bx, ty + 2, bx + bw, ty + 9, PRINT);
            c.fill(bx + 1, ty + 3, bx + bw - 1, ty + 8, PAPER);
            c.fill(bx + 1, ty + 3, bx + 1 + (bw - 2) * r.surveyPermille() / 1000, ty + 8, BLUE);
        }
    }

    private String shipOrBox() {
        return r.ground() ? tr("empty.box", r.groundSize()) : r.shipName().isEmpty() ? tr("empty.ship") : r.shipName();
    }

    private void drawings(int x0, int y0, int x1, int y1) {
        Projection.Frame f = shape.frame();
        int pw = f.planW();
        int ph = f.planH();
        int qh = f.profileH();
        int nHits = Math.min(BattleReport.MAX_HITS, Math.max(1, r.hits().size()));
        int hitsH = compact ? 0 : 15 + nHits * 10;
        int markerRows = r.sections().isEmpty() ? 0 : r.sections().size() > 1 ? 2 : 1;
        boolean withPlan = true;
        int areaW = x1 - x0 - 4;
        int pool = 1;
        int scale;
        int fixed;
        while (true) {
            int cw = Projection.ceilDiv(pw, pool);
            int cph = Projection.ceilDiv(ph, pool);
            int cqh = Projection.ceilDiv(qh, pool);
            fixed = 3 + (compact ? 0 : 13) + markerRows * 9 + (withPlan ? 16 : 0) + 4;
            int availH = y1 - y0 - hitsH - fixed;
            int rows = cqh + (withPlan ? cph : 0);
            scale = Math.min(6, Math.min(areaW / Math.max(1, cw), availH / Math.max(1, rows)));
            if (scale >= 2 || (scale >= 1 && pool > 1)) break;
            if (hitsH > 0) {
                hitsH = Math.max(0, hitsH - 20);
                if (hitsH < 25) hitsH = 0;
                continue;
            }
            if (withPlan && scale < 1) {
                withPlan = false;
                continue;
            }
            if (scale >= 1 || pool > 64) break;
            pool++;
        }
        scale = Math.max(1, scale);
        int cw = Projection.ceilDiv(pw, pool);
        int cph = Projection.ceilDiv(ph, pool);
        int cqh = Projection.ceilDiv(qh, pool);
        byte[] plan = cells(shape.plan(), pw, ph, true);
        byte[] profile = cells(shape.profile(), pw, qh, false);
        if (pool > 1) {
            plan = Projection.pool(plan, pw, ph, pool);
            profile = Projection.pool(profile, pw, qh, pool);
        }
        int drawW = cw * scale;
        int ox = x0 + 2 + (areaW - drawW) / 2;
        int py = y0 + 2;
        clip = new int[]{x0 - 3, y0 - 1, x1 + 3, y1};
        drawShip(profile, cw, cqh, ox, py, scale, false);
        int keel = py + cqh * scale;
        fireScribble(ox, py, scale, pool, false);
        freshRing(ox, py, scale, pool, false);
        int y = keel + 3;
        if (!compact) {
            frameScale(ox, y, cw, scale, pool);
            y += 13;
        }
        if (markerRows > 0) {
            markerLabels(ox, keel, y, x0, x1, scale, pool, markerRows);
            y += markerRows * 9 + 2;
        }
        hoverCell(ox, py, cw, cqh, scale, pool, false);
        int planY = -1;
        if (withPlan) {
            y += 3;
            String title = tr(r.ground() ? "sheet.plan.ground" : "sheet.plan");
            c.text(title, x0, y, PRINT);
            legend(x0 + host.width(title) + 10, y, x1);
            marks.add(new int[]{x0 - 3, y - 1, x1 + 3, y + 9});
            planY = y + 11;
            drawShip(plan, cw, cph, ox, planY, scale, true);
            fireScribble(ox, planY, scale, pool, true);
            planSymbols(ox, planY, scale, pool, plan, cw, cph);
            hoverCell(ox, planY, cw, cph, scale, pool, true);
            y = planY + cph * scale + 4;
        }

        clip = new int[]{x0 - 3, y0 - 1, x1 + 3, Math.min(y1, y)};
        jobNumbers(ox, py, scale, pool, false);
        if (planY >= 0) jobNumbers(ox, planY, scale, pool, true);
        clip = null;

        if (hitsH > 0 && y1 - y >= 25) {
            int end = hitsLog(x0, y + 4, x1, y1);
            if (y1 - end >= 38) systemsTable(x0, end + 6, x1, y1);
        }
    }

    private void systemsTable(int x0, int y0, int x1, int y1) {
        c.text(tr("sheet.systems"), x0, y0, PRINT);
        c.fill(x0 - 1, y0 + 10, x1 + 1, y0 + 11, PRINT);
        List<com.createstats.battle.core.BattleSystem> shown = new ArrayList<>();
        for (com.createstats.battle.core.BattleSystem s : com.createstats.battle.core.BattleSystem.values()) {
            if (r.system(s).total() > 0 || s == com.createstats.battle.core.BattleSystem.HULL) shown.add(s);
        }
        int rows = Math.max(1, (y1 - (y0 + 14)) / 10);
        int perCol = Math.max(1, Math.min(rows, (shown.size() + 1) / 2));
        int colW = (x1 - x0 - 8) / 2;
        for (int i = 0; i < shown.size(); i++) {
            int col = i / perCol;
            int row = i % perCol;
            if (col > 1) break;
            com.createstats.battle.core.BattleSystem s = shown.get(i);
            BattleReport.SystemLine line = r.system(s);
            int cx = x0 + col * (colW + 8);
            int cy = y0 + 14 + row * 10;
            int total = line.total();
            int intact = line.intact();
            String value;
            int ink;
            if (total <= 0) {
                value = "?";
                ink = BLUE;
            } else if (s.unit() == com.createstats.battle.core.BattleSystem.Unit.BLOCKS) {
                int permille = (int) (intact * 1000L / total);
                value = pct(permille);
                ink = permille >= 1000 ? GREEN : permille >= 900 ? BLACK : RED;
            } else {
                value = tr("value.of", intact, total);
                ink = intact >= total ? GREEN : RED;
            }
            String name = tr("system." + s.key());
            int vw = gw(value, 1);
            c.text(fit(name, colW - vw - 6), cx, cy, PRINT);
            grease(value, cx + colW - vw, cy, ink, 1);
            List<String> tip = new ArrayList<>();
            tip.add(name + ": " + value);
            tip.add(tr("system." + s.key() + ".hint"));
            if (total <= 0) tip.add(tr("system.none"));
            hit(cx, cy - 1, colW, 10, null, tip);
        }
    }

    private void legend(int x, int y, int x1) {
        String[] keys = {"sheet.key.lost", "sheet.key.recent", "sheet.key.fire"};
        for (int i = 0; i < keys.length; i++) {
            String t = tr(keys[i]);
            int need = 8 + host.width(t) + 7;
            if (x + need > x1) break;
            c.fill(x, y + 1, x + 6, y + 7, PRINT);
            c.fill(x + 1, y + 2, x + 5, y + 6, i == 0 ? PAPER : i == 1 ? RED : YELLOW);
            if (i == 0) {
                for (int k = 0; k < 4; k++) c.fill(x + 1 + k, y + 5 - k, x + 2 + k, y + 6 - k, RED);
            }
            c.text(t, x + 8, y, PRINT_SOFT);
            x += need;
        }
    }

    private void frameScale(int ox, int y, int cw, int scale, int pool) {
        c.fill(ox, y, ox + cw * scale, y + 1, PRINT_SOFT);
        int step = 8;
        while (step / pool * scale < 24 && step < 1024) step *= 2;
        int blocks = cw * pool;
        for (int b = 0; b <= blocks; b += step) {
            int px = ox + cw * scale - (b / pool) * scale;
            if (px < ox) break;
            c.fill(px, y, px + 1, y + 3, PRINT_SOFT);
            String t = String.valueOf(b);
            int tx = px - host.width(t) / 2;
            if (tx < ox - 4) continue;
            c.text(t, tx, y + 4, PRINT_SOFT);
            marks.add(new int[]{tx - 1, y + 3, tx + host.width(t) + 1, y + 13});
        }
    }

    private void markerLabels(int ox, int keel, int y, int x0, int x1, int scale, int pool, int rows) {
        ShapeView s = shape;
        if (s == null) return;
        int[] rowEnd = new int[rows];
        java.util.Arrays.fill(rowEnd, Integer.MIN_VALUE);
        List<int[]> placed = new ArrayList<>();
        List<BattleReport.SectionLine> sorted = new ArrayList<>(r.sections());
        sorted.sort((a, b) -> Integer.compare(markerX(a, ox, scale, pool), markerX(b, ox, scale, pool)));
        for (BattleReport.SectionLine sec : sorted) {
            int px = markerX(sec, ox, scale, pool);
            if (px == Integer.MIN_VALUE) continue;
            String name = (sec.name().isEmpty() ? tr("sections.unnamed") : sec.name()).toUpperCase(Locale.ROOT);
            int lw = host.width(name);
            int lx = Math.max(x0, Math.min(x1 - lw, px - 2));
            for (int row = 0; row < rows; row++) {
                if (lx <= rowEnd[row] + 6) continue;
                int ly = y + row * 9;

                boolean crosses = false;
                for (int[] b : placed) crosses |= px >= b[0] && px < b[2] && b[1] < ly;
                if (!crosses) c.fill(px, keel + 1, px + 1, ly, PRINT_SOFT);
                placed.add(new int[]{lx - 1, ly - 1, lx + lw + 1, ly + 9});
                c.text(name, lx, ly, PRINT);
                marks.add(new int[]{lx - 1, ly - 1, lx + lw + 1, ly + 9});
                rowEnd[row] = lx + lw;
                hit(lx, ly, lw, 9, null, List.of(tr("sections.tip", sec.name().isEmpty() ? tr("sections.unnamed") : sec.name(),
                        sectionPct(sec.permille()), place(sec.place()))));
                break;
            }
        }
    }

    private int markerX(BattleReport.SectionLine sec, int ox, int scale, int pool) {
        ShapeView s = shape;
        if (s == null) return Integer.MIN_VALUE;
        int packed = s.packAbsolute(sec.pos());
        if (packed < 0) return Integer.MIN_VALUE;
        return ox + s.profileCell(packed)[0] / pool * scale + scale / 2;
    }

    private void drawShip(byte[] cells, int cw, int ch, int x, int y, int s, boolean plan) {
        if (plan) hiddenLine(cells, cw, ch, x, y, s);
        for (int row = 0; row < ch; row++) {
            int col = 0;
            while (col < cw) {
                if (!solid(cells[row * cw + col], plan)) {
                    col++;
                    continue;
                }
                int run = 1;
                while (col + run < cw && solid(cells[row * cw + col + run], plan)) run++;
                c.fill(x + col * s - 1, y + row * s - 1, x + (col + run) * s + 1, y + (row + 1) * s + 1, PRINT);
                col += run;
            }
        }
        boolean flicker = (clock / 4) % 2 == 0;
        for (int row = 0; row < ch; row++) {
            int col = 0;
            while (col < cw) {
                byte st = cells[row * cw + col];
                int run = 1;
                while (col + run < cw && cells[row * cw + col + run] == st) run++;
                int rx0 = x + col * s;
                int ry0 = y + row * s;
                int rx1 = rx0 + run * s;
                switch (st) {
                    case Projection.LIFT -> {
                        if (plan) break;
                        c.fill(rx0, ry0, rx1, ry0 + s, TINT_LIFT);
                        if (s >= 3) {
                            for (int k = col; k < col + run; k++) {
                                if (k % 4 == 0) c.fill(x + k * s, ry0, x + k * s + 1, ry0 + s, TINT_SEAM);
                            }
                        }
                    }
                    case Projection.STRUCTURE -> c.fill(rx0, ry0, rx1, ry0 + s, TINT_STRUCT);
                    case Projection.LOST -> {
                        c.fill(rx0, ry0, rx1, ry0 + s, PAPER);
                        for (int k = 0; k < run; k++) hatch(rx0 + k * s, ry0, s);
                    }
                    case Projection.RECENT -> c.fill(rx0, ry0, rx1, ry0 + s, RED);
                    case Projection.BURNING -> {
                        c.fill(rx0, ry0, rx1, ry0 + s, flicker ? YELLOW : 0xFFF0C23A);
                        if (s >= 3) {
                            for (int k = 0; k < run; k++) c.fill(rx0 + k * s + 1, ry0 + 1, rx0 + k * s + 2, ry0 + 2, RED);
                        }
                    }
                    default -> {
                    }
                }
                col += run;
            }
        }
    }

    private static boolean solid(byte state, boolean plan) {
        return state != Projection.EMPTY && !(plan && state == Projection.LIFT);
    }

    private void hiddenLine(byte[] cells, int cw, int ch, int x, int y, int s) {
        for (int row = 0; row < ch; row++) {
            for (int col = 0; col < cw; col++) {
                if (cells[row * cw + col] != Projection.LIFT) continue;
                int px = x + col * s;
                int py = y + row * s;
                if (row == 0 || cells[(row - 1) * cw + col] == Projection.EMPTY) dots(px - 1, py - 1, px + s + 1, py);
                if (row == ch - 1 || cells[(row + 1) * cw + col] == Projection.EMPTY) dots(px - 1, py + s, px + s + 1, py + s + 1);
                if (col == 0 || cells[row * cw + col - 1] == Projection.EMPTY) dots(px - 1, py - 1, px, py + s + 1);
                if (col == cw - 1 || cells[row * cw + col + 1] == Projection.EMPTY) dots(px + s, py - 1, px + s + 1, py + s + 1);
            }
        }
    }

    private void dots(int x0, int y0, int x1, int y1) {
        for (int py = y0; py < y1; py++) {
            for (int px = x0; px < x1; px++) {
                if (Math.floorMod(px + py, 3) == 0) c.fill(px, py, px + 1, py + 1, PRINT_SOFT);
            }
        }
    }

    private void hatch(int x, int y, int s) {
        if (s <= 1) {
            c.fill(x, y, x + 1, y + 1, RED);
            return;
        }
        for (int k = 0; k < s; k++) c.fill(x + k, y + s - 1 - k, x + k + 1, y + s - k, RED);
        if (s >= 4) {
            for (int k = 0; k + 2 < s; k++) c.fill(x + k + 2, y + s - 1 - k, x + k + 3, y + s - k, RED);
        }
    }

    private void fireScribble(int ox, int oy, int scale, int pool, boolean plan) {
        DetailView d = detail;
        ShapeView s = shape;
        if (d == null || s == null || plan || d.burning().length == 0) return;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int topY = Integer.MAX_VALUE;
        for (int p : d.burning()) {
            int[] cell = s.profileCell(p);
            minX = Math.min(minX, ox + cell[0] / pool * scale);
            maxX = Math.max(maxX, ox + (cell[0] / pool + 1) * scale);
            topY = Math.min(topY, oy + cell[1] / pool * scale);
        }
        int y = topY - 2;
        for (int x = minX; x + 2 <= maxX; x += 3) {
            line(x, y, x + 1, y - 5, RED);
            line(x + 1, y - 5, x + 3, y, RED);
        }
    }

    // one ring per cluster, a ring around bow+stern hits points at nothing
    private void freshRing(int ox, int oy, int scale, int pool, boolean plan) {
        DetailView d = detail;
        ShapeView s = shape;
        if (d == null || s == null) return;
        List<int[]> pts = new ArrayList<>();
        long youngest = Long.MAX_VALUE;
        for (int i = 0; i < d.lost().length; i++) {
            long a = d.age(i, clock);
            if (a >= 200) continue;
            int[] cell = plan ? s.planCell(d.lost()[i]) : s.profileCell(d.lost()[i]);
            pts.add(new int[]{ox + cell[0] / pool * scale + scale / 2, oy + cell[1] / pool * scale + scale / 2});
            youngest = Math.min(youngest, a);
        }
        if (pts.isEmpty()) return;
        pts.sort((a, b) -> Integer.compare(a[0], b[0]));

        List<int[]> clusters = new ArrayList<>();
        int gapPx = Math.max(10, scale * 4);
        int[] cur = null;
        for (int[] p : pts) {
            if (cur == null || p[0] - cur[1] > gapPx) {
                cur = new int[]{p[0], p[0], p[1], p[1], 0};
                clusters.add(cur);
            }
            cur[1] = Math.max(cur[1], p[0]);
            cur[2] = Math.min(cur[2], p[1]);
            cur[3] = Math.max(cur[3], p[1]);
            cur[4]++;
        }
        clusters.sort((a, b) -> Integer.compare(b[4], a[4]));
        for (int k = 0; k < Math.min(3, clusters.size()); k++) {
            int[] cl = clusters.get(k);
            int cx = (cl[0] + cl[1]) / 2;
            int cy = (cl[2] + cl[3]) / 2;
            int rad = clampInt((cl[1] - cl[0]) / 2 + 5, 6, 22);
            int ry = clampInt((cl[3] - cl[2]) / 2 + 5, 5, 16);
            greaseRing(cx, cy, rad, ry, RED, 1);
            marks.add(new int[]{cx - rad, cy - ry, cx + rad + 1, cy + ry + 1});
            if (k > 0) continue;
            String t = clockText(youngest);
            int tw = gw(t, 1);
            int[][] spots = {{cx + rad - 2, cy - ry - 10}, {cx + rad + 3, cy - 4}, {cx - rad - 3 - tw, cy - 4}, {cx - tw / 2, cy + ry + 2}};
            for (int[] spot : spots) {
                if (placeMark(spot[0], spot[1], tw, 9, () -> grease(t, spot[0], spot[1], RED, 1))) break;
            }
        }
    }

    private void jobNumbers(int ox, int oy, int scale, int pool, boolean plan) {
        ShapeView s = shape;
        if (s == null) return;
        for (JobBoard.Job j : jobs.jobs()) {
            boolean gunJob = j.kind() == JobBoard.Kind.GUN || j.kind() == JobBoard.Kind.LOAD;
            if (gunJob != plan) continue;
            int packed = gunJob ? gunPacked(j.gunId()) : j.anchor();
            if (packed < 0) continue;
            int[] cell = plan ? s.planCell(packed) : s.profileCell(packed);
            int ax = ox + cell[0] / pool * scale + scale / 2;
            int ay = oy + cell[1] / pool * scale + scale / 2;
            String n = String.valueOf(j.number());
            int rad = n.length() > 1 ? 7 : 6;
            int ink = jobInk(j);
            boolean picked = j.key().equals(selectedJob);

            List<int[]> offsets = new ArrayList<>();
            for (int d : new int[]{rad + 7, rad + 14, rad + 22}) {
                offsets.add(new int[]{-d, -d});
                offsets.add(new int[]{d, -d});
                offsets.add(new int[]{-d, d});
                offsets.add(new int[]{d, d});
                offsets.add(new int[]{0, -d - 3});
                offsets.add(new int[]{d + 3, 0});
                offsets.add(new int[]{0, d + 3});
                offsets.add(new int[]{-d - 3, 0});
            }
            for (int[] o : offsets) {
                int cx = ax + o[0];
                int cy = ay + o[1];
                if (!inClip(cx - rad - 1, cy - rad - 1, cx + rad + 2, cy + rad + 2)) continue;
                if (!free(cx - rad - 1, cy - rad - 1, cx + rad + 2, cy + rad + 2)) continue;
                marks.add(new int[]{cx - rad - 1, cy - rad - 1, cx + rad + 2, cy + rad + 2});
                double len = Math.max(1.0, Math.hypot(o[0], o[1]));
                int ex = cx - (int) Math.round(o[0] / len * (rad + 1));
                int ey = cy - (int) Math.round(o[1] / len * (rad + 1));
                line(ax, ay, ex, ey, ink);

                disc(cx, cy, rad - 1, picked ? 0xFFF3E7A8 : WIPED);
                greaseRing(cx, cy, rad, rad, ink, picked ? 2 : 1);
                int nw = gw(n, 1);
                grease(n, cx - nw / 2, cy - 3, ink, 1);
                List<String> tip = List.of(tr("jobs.mark", j.number()), jobVerb(j) + ": " + jobWhere(j));
                hit(cx - rad, cy - rad, rad * 2 + 1, rad * 2 + 1, () -> selectJob(j), tip);
                break;
            }
        }
    }

    private int gunPacked(int gunId) {
        BattleReport.GunLine g = gunById(gunId);
        ShapeView s = shape;
        if (g == null || s == null || g.mount() == BattleReport.GunLine.NO_MOUNT) return -1;
        return s.packAbsolute(g.mount());
    }

    private void planSymbols(int planX, int planY, int scale, int pool, byte[] cells, int cw, int ch) {
        ShapeView s = shape;
        if (s == null) return;
        for (int i = 0; i + 1 < s.props().length; i += 2) {
            int[] cell = s.planCell(s.props()[i + 1]);
            int px = planX + cell[0] / pool * scale;
            int py = planY + cell[1] / pool * scale;
            c.fill(px - 1, py - 2, px + Math.max(2, scale) + 1, py + Math.max(2, scale) + 2, PRINT);
            String label = "P" + s.props()[i];
            hit(px - 2, py - 2, 6, 6, null, List.of(tr("symbol.propeller", s.props()[i])));
            int lw = host.width(label);
            int[][] spots = {{px + 5, py - 10}, {px + 5, py + 4}, {px - lw - 3, py - 10}, {px - lw - 3, py + 4}};
            if (scale >= 2) {
                for (int[] spot : spots) {
                    if (placeMark(spot[0], spot[1], lw, 9, () -> c.text(label, spot[0], spot[1], PRINT))) break;
                }
            }

            marks.add(new int[]{px - 1, py - 2, px + Math.max(2, scale) + 1, py + Math.max(2, scale) + 2});
        }
        for (int packed : s.bridges()) {
            int[] cell = s.planCell(packed);
            int px = planX + cell[0] / pool * scale;
            int py = planY + cell[1] / pool * scale;
            c.fill(px - 2, py - 2, px + 3, py + 3, PRINT);
            c.fill(px - 1, py - 1, px + 2, py + 2, PAPER);
            marks.add(new int[]{px - 2, py - 2, px + 3, py + 3});
            hit(px - 2, py - 2, 5, 5, null, List.of(tr("symbol.bridge")));
        }
        for (BattleReport.SectionLine sec : r.sections()) {
            int packed = s.packAbsolute(sec.pos());
            if (packed < 0) continue;
            int[] cell = s.planCell(packed);
            int px = planX + cell[0] / pool * scale;
            int py = planY + cell[1] / pool * scale;
            c.fill(px, py - 5, px + 1, py + 2, PRINT);
            c.fill(px + 1, py - 5, px + 5, py - 2, sec.permille() >= 1000 || sec.permille() < 0 ? PRINT_SOFT : RED);
            marks.add(new int[]{px - 1, py - 5, px + 6, py + 3});
            hit(px - 1, py - 5, 7, 8, null, List.of(tr("symbol.section", sec.name(), sectionPct(sec.permille()))));
        }

        List<int[]> drawn = new ArrayList<>();
        for (int n = 0; n < r.guns().size(); n++) {
            BattleReport.GunLine g = r.guns().get(n);
            if (g.mount() == BattleReport.GunLine.NO_MOUNT) continue;
            int packed = s.packAbsolute(g.mount());
            if (packed < 0) continue;
            int[] cell = s.planCell(packed);
            int cx = planX + cell[0] / pool * scale + scale / 2;
            int cy = planY + cell[1] / pool * scale + scale / 2;
            int ink = lampInk(g.gunState().lamp());
            double a = Math.toRadians(g.train());
            double dx = r.ground() ? Math.sin(a) : Math.cos(a);
            double dy = r.ground() ? -Math.cos(a) : Math.sin(a);
            int len = Math.max(8, scale * 4);
            int bx = cx + (int) Math.round(dx * len);
            int by = cy + (int) Math.round(dy * len);
            line(cx + (int) Math.round(dx * 4), cy + (int) Math.round(dy * 4), bx, by, BLACK);
            disc(cx, cy, 3, PRINT);
            disc(cx, cy, 2, PAPER);
            c.fill(cx - 1, cy - 1, cx + 1, cy + 1, ink);
            marks.add(new int[]{cx - 4, cy - 4, cx + 5, cy + 5});

            markLine(cx, cy, bx, by);
            drawn.add(new int[]{n, cx, cy, dx > 0.3 ? 1 : 0});
            List<String> tip = List.of(g.name() + ": " + tr("gun.state." + g.gunState().key()), sentence(g));
            final int id = g.id();
            hit(cx - 4, cy - 4, 9, 9, () -> {
                page = Page.GUNS;
                arcGun = id;
                arcReveal = true;
            }, tip);
        }
        for (int[] d : drawn) {
            BattleReport.GunLine g = r.guns().get(d[0]);
            gunWord(g, d[1], d[2], d[3] == 1, planX, planY, scale, cells, cw, ch);
        }
    }

    private void gunWord(BattleReport.GunLine g, int cx, int cy, boolean barrelRight, int planX, int planY, int scale,
                         byte[] cells, int cw, int ch) {
        String word = tr("gun.brief." + g.gunState().key());
        int ink = lampInk(g.gunState().lamp());
        int ww = gw(word, 1);
        List<int[]> spots = new ArrayList<>();
        for (int reach : new int[]{6, 12, 22}) {
            int away = barrelRight ? cx - reach - ww : cx + reach;
            int toward = barrelRight ? cx + reach : cx - reach - ww;
            spots.add(new int[]{away, cy - 4, reach});
            spots.add(new int[]{cx - ww / 2, cy + reach, reach});
            spots.add(new int[]{cx - ww / 2, cy - reach - 8, reach});
            spots.add(new int[]{away, cy + reach - 2, reach});
            spots.add(new int[]{away, cy - reach - 6, reach});
            spots.add(new int[]{toward, cy - 4, reach});
            spots.add(new int[]{toward, cy + reach - 2, reach});
            spots.add(new int[]{toward, cy - reach - 6, reach});
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int[] spot : spots) {
                int x = spot[0];
                int y = spot[1];
                if (!inClip(x - 1, y - 1, x + ww + 1, y + 9) || !free(x - 1, y - 1, x + ww + 1, y + 9)) continue;
                boolean clear = clearOfDamage(cells, cw, ch, planX, planY, scale, x - 1, y - 1, x + ww + 1, y + 9);
                if (pass == 0 && !clear) continue;
                marks.add(new int[]{x - 1, y - 1, x + ww + 1, y + 9});
                if (spot[2] > 12) {
                    int nx = clampInt(cx, x, x + ww - 1);
                    int ny = clampInt(cy, y, y + 7);
                    double len = Math.max(1.0, Math.hypot(nx - cx, ny - cy));
                    int sx = cx + (int) Math.round((nx - cx) / len * 4);
                    int sy = cy + (int) Math.round((ny - cy) / len * 4);
                    int ex = nx - (int) Math.round((nx - cx) / len * 2);
                    int ey = ny - (int) Math.round((ny - cy) / len * 2);
                    line(sx, sy, ex, ey, ink);
                    markLine(sx, sy, ex, ey);
                }
                if (!clear) c.fill(x - 1, y - 1, x + ww + 1, y + 9, WIPED);
                grease(word, x, y, ink, 1);
                return;
            }
        }
    }

    private void markLine(int x0, int y0, int x1, int y1) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 2);
        for (int k = 0; k <= steps; k++) {
            int mx = x0 + (x1 - x0) * k / steps;
            int my = y0 + (y1 - y0) * k / steps;
            marks.add(new int[]{mx - 1, my - 1, mx + 2, my + 2});
        }
    }

    private static boolean clearOfDamage(byte[] cells, int cw, int ch, int ox, int oy, int scale, int x0, int y0, int x1, int y1) {
        int c0 = Math.max(0, Math.floorDiv(x0 - ox, scale));
        int c1 = Math.min(cw - 1, Math.floorDiv(x1 - 1 - ox, scale));
        int r0 = Math.max(0, Math.floorDiv(y0 - oy, scale));
        int r1 = Math.min(ch - 1, Math.floorDiv(y1 - 1 - oy, scale));
        for (int row = r0; row <= r1; row++) {
            for (int col = c0; col <= c1; col++) {
                if (cells[row * cw + col] >= Projection.LOST) return false;
            }
        }
        return true;
    }

    private byte[] cells(byte[] base, int cw, int ch, boolean plan) {
        byte[] out = base.clone();
        DetailView d = detail;
        if (d == null || shape == null) return out;
        for (int i = 0; i < d.lost().length; i++) {
            int[] cell = plan ? shape.planCell(d.lost()[i]) : shape.profileCell(d.lost()[i]);
            byte state = d.age(i, clock) < 200 ? Projection.RECENT : Projection.LOST;
            Projection.put(out, cw, cell[0], cell[1], state);
        }
        for (int p : d.burning()) {
            int[] cell = plan ? shape.planCell(p) : shape.profileCell(p);
            Projection.put(out, cw, cell[0], cell[1], Projection.BURNING);
        }
        return out;
    }

    private void hoverCell(int x, int y, int cw, int ch, int scale, int pool, boolean plan) {
        if (mx < x || my < y || mx >= x + cw * scale || my >= y + ch * scale || shape == null) return;
        int col = (mx - x) / scale;
        int row = (my - y) / scale;
        DetailView d = detail;
        if (d == null) return;
        int lost = 0;
        List<String> places = new ArrayList<>();
        for (int i = 0; i < d.lost().length; i++) {
            int[] cell = plan ? shape.planCell(d.lost()[i]) : shape.profileCell(d.lost()[i]);
            if (cell[0] / pool != col || cell[1] / pool != row) continue;
            lost++;
            String name = placeOf(d.lost()[i]);
            if (!name.isEmpty() && !places.contains(name)) places.add(name);
        }
        if (lost == 0) return;
        List<String> tip = new ArrayList<>();
        tip.add(places.isEmpty() ? tr("hover.lost", lost) : tr("hover.lost_in", lost, String.join(", ", places)));
        hit(x + col * scale, y + row * scale, scale, scale, null, tip);
    }

    private String placeOf(int packed) {
        DetailView d = detail;
        if (d == null || d.places().isEmpty()) return "";
        DetailView.Place best = null;
        int bestDist = Integer.MAX_VALUE;
        for (DetailView.Place p : d.places()) {
            int dist = PackedPos.chebyshev(p.anchor(), packed);
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }
        return best == null ? "" : "R" + best.id() + " " + place(best.name());
    }

    private int hitsLog(int x0, int y0, int x1, int y1) {
        c.text(tr("hits.label").toUpperCase(Locale.ROOT), x0, y0, PRINT);
        c.fill(x0 - 1, y0 + 10, x1 + 1, y0 + 11, PRINT);
        int y = y0 + 14;
        if (r.hits().isEmpty()) {
            c.text(fit(tr("hits.none"), x1 - x0), x0, y, PRINT_SOFT);
            return y + 10;
        }
        for (BattleReport.HitLine hl : r.hits()) {
            if (y + 9 > y1) break;
            long a = clock - hl.at();
            boolean fresh = a >= 0 && a < FRESH_TICKS;
            int ink = fresh ? RED : BLACK;
            String when = clockText(a);
            String what = hl.count() > 1 && hl.what().isEmpty() ? tr("hit.blocks", group(hl.count()))
                    : hl.count() > 1 ? tr("hit.blocks_of", group(hl.count()), host.name(hl.what())) : host.name(hl.what());
            int ww = gw(when, 1);
            grease(when, x0, y, ink, 1);
            String whatFit = fit(what, (x1 - x0) * 42 / 100);
            c.text(whatFit, x1 - host.width(whatFit), y, PRINT);
            String where = place(hl.place());
            grease(fitG(where.toUpperCase(Locale.ROOT), x1 - host.width(whatFit) - 8 - (x0 + ww + 6)), x0 + ww + 6, y, ink, 1);
            hit(x0, y - 1, x1 - x0, 10, null, List.of(tr("hit.tip", when, where, what)));
            y += 10;
        }
        return y;
    }

    private void plotBoard(int x0, int y0, int x1, int y1) {
        paper(x0, y0, x1, y1, true);
        int ix0 = x0 + 6;
        int ix1 = x1 - 6;
        String title = tr("plot.title");
        c.text(title, ix0, y0 + 5, PRINT);
        String up = tr(r.ground() ? "plot.north_up" : "plot.bow_up");
        if (host.width(title) + host.width(up) + 10 <= ix1 - ix0) c.text(up, ix1 - host.width(up), y0 + 5, PRINT_SOFT);
        c.fill(ix0 - 1, y0 + 15, ix1 + 1, y0 + 16, PRINT);

        List<BattleReport.GunLine> guns = r.guns();
        int gunRow = 14;
        int maxRows = Math.max(1, Math.min(guns.size(), 6));
        int gunsH = guns.isEmpty() ? 0 : 16 + maxRows * gunRow + 12;
        int targetH = 44;
        int plotY0 = y0 + 18;
        int plotY1 = y1 - 5 - gunsH - targetH;
        int size = Math.min(ix1 - ix0, plotY1 - plotY0);
        clip = new int[]{ix0 - 2, plotY0, ix1 + 2, plotY1};
        if (size >= 90) plot((ix0 + ix1 - size) / 2, plotY0, size, false);
        clip = null;
        int ty = plotY1 + 2;
        targetBlock(ix0, ty, ix1);
        if (!guns.isEmpty()) gunTags(ix0, ty + targetH, ix1, y1 - 5, gunRow);
        acrylic(x0, y0, x1, y1);
    }

    private void targetBlock(int x0, int y0, int x1) {
        c.fill(x0 - 1, y0, x1 + 1, y0 + 1, PRINT);
        String label = tr("plot.target");
        c.text(label, x0, y0 + 4, PRINT);
        BattleReport.TargetLine t = r.target();
        boolean answer = r.salvoAt() >= 0 && clock - r.salvoAt() < ANSWER_TICKS && !r.salvo().isEmpty();
        int tx = x0 + host.width(label) + 6;
        if (t.present() && t.lostSince() < 0) {
            String line = targetName(t).toUpperCase(Locale.ROOT) + "  " + meters(t.range()) + "  " + bearingText(t.bearing());
            grease(fitG(line, x1 - tx), tx, y0 + 4, RED, 1);
        } else if (t.present()) {
            grease(fitG(tr("guns.target_lost", targetName(t), age(clock - t.lostSince())).toUpperCase(Locale.ROOT), x1 - tx), tx, y0 + 4, BLACK, 1);
        } else {
            c.text(fit(tr("plot.no_target"), x1 - tx), tx, y0 + 4, PRINT_SOFT);
        }
        int tagW = Math.max(host.width(tr("plot.pick")), host.width(tr("plot.clear"))) + 10;
        int tagsX = x1 - (t.present() ? tagW * 2 + 3 : tagW);
        int room = tagsX - x0 - 6;
        if (answer) {
            String said = SalvoResult.decode(r.salvo()).describe((k, a) -> tr(k, a));
            List<String> lines = wrap(said, room);
            for (int i = 0; i < Math.min(2, lines.size()); i++) grease(fitG(lines.get(i), room), x0, y0 + 16 + i * 10, BLACK, 1);
            if (lines.size() > 2) hit(x0, y0 + 15, room, 20, null, List.of(said));
        } else if (t.present() && t.lostSince() < 0) {
            c.text(fit(closingText(t.closing()), room), x0, y0 + 17, PRINT);
            if (!t.by().isEmpty()) {
                String by = tr("plot.set_by", t.by(), age(clock - t.at()));
                c.text(fit(by, x1 - x0), x0, y0 + 28, PRINT_SOFT);
            }
        }
        tag(tagsX, y0 + 15, tagW, TAG_H, tr("plot.pick"), TAG, this::openPickerFromBoard, List.of(tr("guns.pick.hint")));
        if (t.present()) {
            String clear = t.kind() == 2 && confirming("clear") ? tr("confirm") : tr("plot.clear");
            Runnable act = () -> send(BattleBridgeAction.CLEAR_TARGET, 0, 0);
            if (t.kind() == 2) confirmTag(tagsX + tagW + 3, y0 + 15, tagW, clear, "clear", act, List.of(tr("guns.clear.hint")));
            else tag(tagsX + tagW + 3, y0 + 15, tagW, TAG_H, clear, TAG, act, List.of(tr("guns.clear.hint")));
        }
    }

    private void openPickerFromBoard() {
        page = Page.GUNS;
        pickerOpen = true;
        pickerScroll = 0;
    }

    private void gunTags(int x0, int y0, int x1, int y1, int rowH) {
        c.fill(x0 - 1, y0, x1 + 1, y0 + 1, PRINT);
        int slotW = Math.max(host.width(tr("mode.1")), Math.max(host.width(tr("mode.0")), host.width(tr("mode.2")))) + 8;
        int slotsX = x1 - (slotW * 3 + 4);
        c.text(tr("plot.gun"), x0, y0 + 4, PRINT_SOFT);
        for (int m = 0; m < 3; m++) {
            String t = tr("mode." + m).toUpperCase(Locale.ROOT);
            c.text(t, slotsX + m * (slotW + 2) + (slotW - host.width(t)) / 2, y0 + 4, PRINT_SOFT);
        }
        int y = y0 + 15;
        List<BattleReport.GunLine> guns = r.guns();
        int shown = 0;
        for (BattleReport.GunLine g : guns) {
            if (y + rowH > y1 - 11) break;
            int nameW = Math.min(host.width(g.name()), (slotsX - x0) * 45 / 100);
            String name = fit(g.name(), (slotsX - x0) * 45 / 100);
            c.text(name, x0, y + 2, PRINT);
            String word = tr("gun.state." + g.gunState().key());
            int sx = x0 + nameW + 5;
            grease(fitG(word, slotsX - sx - 4), sx, y + 2, lampInk(g.gunState().lamp()), 1);
            hit(x0, y, slotsX - x0 - 2, rowH - 1, null, List.of(g.name() + ": " + word, sentence(g)));
            final int id = g.id();
            int mode = option(OPT_MODE + g.id(), g.mode());
            String[] labels = {tr("mode.0"), tr("mode.1"), tr("mode.2")};
            List<List<String>> tips = List.of(List.of(tr("mode.0.hint")), List.of(tr("mode.1.hint")), List.of(tr("mode.2.hint")));
            slotRow(slotsX, y, slotW, labels, mode, m -> setMode(id, m), new int[]{TAG, TAG, TAG}, tips, false);
            y += rowH;
            shown++;
        }
        String note;
        int ink;
        if (shown < guns.size() || r.gunCount() > guns.size()) {
            note = tr("plot.more", r.gunCount() - shown);
            ink = PRINT_SOFT;
        } else {
            note = tr("plot.ready", r.gunsReady(), r.gunCount());
            ink = r.gunsReady() > 0 ? GREEN : BLACK;
        }
        if (ink == PRINT_SOFT) c.text(fit(note, x1 - x0), x0, y1 - 9, ink);
        else grease(fitG(note, x1 - x0), x0, y1 - 9, ink, 1);
    }

    private void plot(int x, int y, int size, boolean guns) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        boolean labels = size >= 140;
        int rad = size / 2 - (labels ? 12 : 4);
        double range = scanRadius();
        double step = niceStep(range / 4);
        int rings = (int) Math.min(6, Math.floor(range / step + 1e-6));
        for (int k = 1; k <= rings; k++) {
            int rr = (int) Math.round(rad * (step * k) / range);
            ring(cx, cy, rr, k % 2 == 0 || k == rings ? 0xFF5E7F6C : 0xFF9DB5A7, k == rings ? 1 : 2);
        }
        ring(cx, cy, rad, 0xFF5E7F6C, 1);
        for (int deg = 0; deg < 360; deg += 10) {
            double a = Math.toRadians(deg);
            int len = deg % 30 == 0 ? 5 : 3;
            for (int i = 0; i < len; i++) {
                int px = cx + (int) Math.round(Math.sin(a) * (rad - i));
                int py = cy - (int) Math.round(Math.cos(a) * (rad - i));
                c.fill(px, py, px + 1, py + 1, 0xFF5E7F6C);
            }
            if (labels && deg % 30 == 0) {
                String t = String.format(Locale.ROOT, "%03d", deg);
                int lx = cx + (int) Math.round(Math.sin(a) * (rad + 8)) - host.width(t) / 2;
                int ly = cy - (int) Math.round(Math.cos(a) * (rad + 7)) - 4;
                c.text(t, lx, ly, 0xFF5E7F6C);
                marks.add(new int[]{lx - 1, ly - 1, lx + host.width(t) + 1, ly + 9});
            }
        }
        if (labels) {
            for (int k = 1; k <= rings; k++) {
                String t = String.valueOf((int) Math.round(step * k));
                int ry = cy + (int) Math.round(rad * (step * k) / range);
                int lx = cx - 3 - host.width(t);
                c.fill(lx - 1, ry - 9, cx - 2, ry - 1, PAPER);
                c.text(t, lx, ry - 9, 0xFF5E7F6C);
                marks.add(new int[]{lx - 1, ry - 9, cx - 2, ry - 1});
            }
        }

        c.fill(cx, cy - 4, cx + 1, cy - 3, PRINT);
        c.fill(cx - 1, cy - 3, cx + 2, cy + 3, PRINT);
        c.fill(cx - 1, cy + 3, cx, cy + 4, PRINT);
        c.fill(cx + 1, cy + 3, cx + 2, cy + 4, PRINT);
        marks.add(new int[]{cx - 3, cy - 5, cx + 4, cy + 5});
        if (guns) {
            boolean named = r.guns().size() <= 6;
            for (BattleReport.GunLine g : r.guns()) {
                double a = Math.toRadians(g.train());
                int ink = lampInk(g.gunState().lamp());
                for (int i = 0; i < 5; i++) {
                    int px = cx + (int) Math.round(Math.sin(a) * (rad - 6 + i));
                    int py = cy - (int) Math.round(Math.cos(a) * (rad - 6 + i));
                    c.fill(px, py, px + 1, py + 1, ink);
                }
                if (named) {
                    String n = String.valueOf(g.id());
                    int lx = cx + (int) Math.round(Math.sin(a) * (rad - 14)) - host.width(n) / 2;
                    int ly = cy - (int) Math.round(Math.cos(a) * (rad - 14)) - 4;
                    final int gl = gw(n, 1);
                    placeMark(lx, ly, gl, 9, () -> grease(n, lx, ly, ink, 1));
                }
            }
            BattleReport.GunLine open = gunById(arcGun);
            if (open != null && !fixedMount(open.id()) && !shownArc(open).allRound()) arcWedge(cx, cy, rad, shownArc(open));
        }

        List<int[]> named = new ArrayList<>();
        for (WatchView.Contact k : watch.contacts()) {
            if (isTarget(k)) continue;
            int[] p = dialPoint(cx, cy, rad, range, k.bearing(), k.range());
            if (p == null) continue;
            int ink = k.kind() == WatchView.KIND_PLAYER ? BLUE : BLACK;
            if (k.kind() == WatchView.KIND_ENTITY && k.hostile()) {
                line(p[0] - 2, p[1] - 2, p[0] + 2, p[1] + 2, ink);
                line(p[0] - 2, p[1] + 2, p[0] + 2, p[1] - 2, ink);
                line(p[0] - 1, p[1] - 2, p[0] + 3, p[1] + 2, ink);
                line(p[0] - 1, p[1] + 2, p[0] + 3, p[1] - 2, ink);
            } else {
                symbol(p[0], p[1], k.kind(), ink);
            }
            marks.add(new int[]{p[0] - 3, p[1] - 3, p[0] + 4, p[1] + 4});
            List<String> tip = contactTip(k);
            final WatchView.Contact target = k;
            hit(p[0] - 3, p[1] - 3, 7, 7, () -> designate(target), tip);
            if (k.kind() == WatchView.KIND_SHIP && labels) named.add(new int[]{p[0], p[1], watch.contacts().indexOf(k)});
        }
        for (int[] n0 : named) {
            WatchView.Contact k = watch.contacts().get(n0[2]);
            String n = fitG(contactName(k), 70);
            int nw = gw(n, 1);
            int[][] spots = {{n0[0] + 5, n0[1] - 4}, {n0[0] - 5 - nw, n0[1] - 4}, {n0[0] - nw / 2, n0[1] + 5},
                    {n0[0] - nw / 2, n0[1] - 13}};
            for (int[] spot : spots) {
                if (placeMark(spot[0], spot[1], nw, 9, () -> {
                    c.fill(spot[0] - 1, spot[1] - 1, spot[0] + nw + 1, spot[1] + 9, PAPER);
                    grease(n, spot[0], spot[1], BLACK, 1);
                })) break;
            }
        }
        BattleReport.TargetLine t = r.target();
        if (t.present() && t.lostSince() < 0) {
            int[] p = dialPoint(cx, cy, rad, range, t.bearing(), t.range());
            if (p != null) {
                if (t.speed() > 0.5f) {
                    double ca = Math.toRadians(t.course());
                    int len = (int) Math.min(22, 6 + t.speed() / 2);
                    for (int i = 6; i < len; i += 2) {
                        int qx = p[0] + (int) Math.round(Math.sin(ca) * i);
                        int qy = p[1] - (int) Math.round(Math.cos(ca) * i);
                        c.fill(qx, qy, qx + 1, qy + 1, RED);
                    }
                }
                greaseRing(p[0], p[1], 5, 5, RED, 1);
                c.fill(p[0] - 1, p[1] - 1, p[0] + 2, p[1] + 2, RED);
                float flight = firstFlight();
                if (flight > 0 && t.speed() > 0.5f) {
                    double ca = Math.toRadians(t.course());
                    double dist = t.speed() * flight / 20.0;
                    double tx = t.range() * Math.sin(Math.toRadians(t.bearing())) + dist * Math.sin(ca);
                    double ty = t.range() * Math.cos(Math.toRadians(t.bearing())) + dist * Math.cos(ca);
                    int[] q = dialPoint(cx, cy, rad, range, (float) Math.toDegrees(Math.atan2(tx, ty)), (float) Math.sqrt(tx * tx + ty * ty));
                    if (q != null) {
                        line(q[0] - 2, q[1] - 2, q[0] + 2, q[1] + 2, BLACK);
                        line(q[0] - 2, q[1] + 2, q[0] + 2, q[1] - 2, BLACK);
                    }
                }
                if (labels) {
                    String n = fitG(targetName(t).toUpperCase(Locale.ROOT), 80);
                    int nw = gw(n, 1);
                    int[][] spots = {{p[0] + 8, p[1] - 4}, {p[0] - 8 - nw, p[1] - 4}, {p[0] - nw / 2, p[1] + 8},
                            {p[0] - nw / 2, p[1] - 16}};
                    for (int[] spot : spots) {
                        if (placeMark(spot[0], spot[1], nw, 9, () -> grease(n, spot[0], spot[1], RED, 1))) break;
                    }
                }
                hit(p[0] - 5, p[1] - 5, 11, 11, null, List.of(targetName(t), tr("guns.target_tip", meters(t.range()),
                        bearingText(t.bearing()), closingText(t.closing()))));
            }
        }
    }

    static double niceStep(double raw) {
        double[] ladder = {5, 10, 20, 25, 50, 100, 200, 250, 500};
        for (double s : ladder) if (s >= raw) return s;
        return 1000;
    }

    private float firstFlight() {
        for (WatchView.Gun g : watch.guns()) if (g.flight() > 0) return g.flight();
        return -1f;
    }

    private boolean isTarget(WatchView.Contact k) {
        BattleReport.TargetLine t = r.target();
        if (!t.present()) return false;
        return k.kind() == WatchView.KIND_SHIP ? k.shipId().equals(t.shipId()) : k.entityId() == t.entityId();
    }

    @Nullable
    private static int[] dialPoint(int cx, int cy, int rad, double range, float bearing, float dist) {
        if (dist > range * 1.02) return null;
        double k = rad * Math.min(1.0, dist / range);
        double a = Math.toRadians(bearing);
        return new int[]{cx + (int) Math.round(Math.sin(a) * k), cy - (int) Math.round(Math.cos(a) * k)};
    }

    private void symbol(int x, int y, int kind, int colour) {
        switch (kind) {
            case WatchView.KIND_SHIP -> {
                c.fill(x - 2, y - 2, x + 3, y - 1, colour);
                c.fill(x - 2, y + 2, x + 3, y + 3, colour);
                c.fill(x - 2, y - 2, x - 1, y + 3, colour);
                c.fill(x + 2, y - 2, x + 3, y + 3, colour);
            }
            case WatchView.KIND_PLAYER -> {
                c.fill(x, y - 2, x + 1, y - 1, colour);
                c.fill(x - 1, y - 1, x, y + 1, colour);
                c.fill(x + 1, y - 1, x + 2, y + 1, colour);
                c.fill(x - 2, y + 1, x + 3, y + 2, colour);
            }
            default -> {
                line(x - 1, y - 1, x + 1, y + 1, colour);
                line(x - 1, y + 1, x + 1, y - 1, colour);
            }
        }
    }

    private List<String> contactTip(WatchView.Contact k) {
        List<String> tip = new ArrayList<>();
        tip.add(contactName(k));
        tip.add(tr("guns.target_tip", meters(k.range()), bearingText(k.bearing()), closingText(k.closing())));
        if (k.targetsCrew()) tip.add(tr("picker.targets_crew"));
        tip.add(tr(k.kind() == WatchView.KIND_PLAYER ? "picker.click_player" : "picker.click"));
        return tip;
    }

    private void designate(WatchView.Contact k) {
        switch (k.kind()) {
            case WatchView.KIND_SHIP -> host.send(BattleBridgeAction.DESIGNATE, 1, 0, k.shipId());
            case WatchView.KIND_PLAYER -> {
                String key = "player" + k.entityId();
                if (confirming(key)) {
                    confirmUntil.remove(key);
                    host.send(BattleBridgeAction.DESIGNATE, 2, k.entityId(), "");
                } else {
                    confirmUntil.put(key, clock + CONFIRM_TICKS);
                    return;
                }
            }
            default -> host.send(BattleBridgeAction.DESIGNATE, 0, k.entityId(), "");
        }
        pickerOpen = false;
    }

    private WatchView watch = WatchView.empty("");
    private boolean pickerOpen;
    private int pickerScroll;
    private int tableScroll;
    private boolean problemsOpen;

    private int arcGun = -1;

    private boolean arcReveal;

    private int editingGun = -1;
    private String editText = "";
    private int tableX0;
    private int tableX1;
    private int tableY0;
    private int tableY1;
    private int pickerY0;
    private int pickerY1;

    static final long ANSWER_TICKS = 60L;
    static final int OPT_WEAPONS = 100;
    static final int OPT_MODE = 200;

    static final int OPT_ARC = 10_000;

    void openPicker() {
        page = Page.GUNS;
        pickerOpen = true;
        pickerScroll = 0;
    }

    void openArcs(int gunId) {
        page = Page.GUNS;
        arcGun = gunId;
        arcReveal = true;
    }

    public void watch(@Nullable WatchView view) {
        this.watch = view == null ? WatchView.empty(r.key()) : view;
    }

    private void gunsPage(int x0, int y0, int x1, int y1) {
        paper(x0, y0, x1, y1, true);
        int ix0 = x0 + 6;
        int ix1 = x1 - 6;
        int cy = targetLine(ix0, y0 + 5, ix1) + 4;
        if (pickerOpen) {
            picker(ix0, cy, ix1, y1 - 5);
            acrylic(x0, y0, x1, y1);
            return;
        }

        if (ix1 - ix0 < 350) {
            table(ix0, cy, ix1, y1 - 5);
            acrylic(x0, y0, x1, y1);
            return;
        }
        int dial = Math.min(y1 - 5 - cy, clampInt((ix1 - ix0) * 40 / 100, 104, 240));
        clip = new int[]{ix0 - 2, cy, ix0 + dial + 4, y1 - 3};
        plot(ix0, cy, dial, true);
        clip = null;
        table(ix0 + dial + 8, cy, ix1, y1 - 5);
        acrylic(x0, y0, x1, y1);
    }

    private int targetLine(int x0, int y0, int x1) {
        BattleReport.TargetLine t = r.target();
        boolean answer = r.salvoAt() >= 0 && clock - r.salvoAt() < ANSWER_TICKS && !r.salvo().isEmpty();
        String line;
        int ink = BLACK;

        boolean printed = false;
        if (answer) {
            line = SalvoResult.decode(r.salvo()).describe((k, a) -> tr(k, a));
        } else if (!t.present()) {
            line = tr("guns.no_target");
            printed = true;
        } else if (t.lostSince() >= 0) {
            line = tr("guns.target_lost", targetName(t), age(clock - t.lostSince()));
        } else {
            line = tr("guns.target", targetName(t), meters(t.range()), bearingText(t.bearing()), closingText(t.closing()));
            ink = RED;
        }
        String shown = printed ? fit(line, x1 - x0) : fitG(line, x1 - x0);
        if (printed) c.text(shown, x0, y0, PRINT_SOFT);
        else grease(shown, x0, y0, ink, 1);
        List<String> tip = new ArrayList<>();
        if (!shown.equals(line)) tip.add(line);
        if (t.present() && !t.by().isEmpty()) tip.add(tr("guns.set_by", t.by(), age(clock - t.at())));
        if (!tip.isEmpty()) hit(x0, y0 - 1, x1 - x0, 10, null, tip);

        int by = y0 + 12;
        String pick = tr(pickerOpen ? "guns.pick_close" : "guns.pick");
        int pw = host.width(pick) + 10;
        tag(x0, by, pw, TAG_H, pick, TAG, () -> {
            pickerOpen = !pickerOpen;
            pickerScroll = 0;
        }, List.of(tr("guns.pick.hint")));
        int bx = x0 + pw + 4;
        if (t.present()) {
            String clear = t.kind() == 2 && confirming("clear") ? tr("confirm") : tr("guns.clear");
            int cw = Math.max(host.width(tr("guns.clear")), t.kind() == 2 ? host.width(tr("confirm")) : 0) + 10;
            Runnable act = () -> send(BattleBridgeAction.CLEAR_TARGET, 0, 0);
            if (t.kind() == 2) confirmTag(bx, by, cw, clear, "clear", act, List.of(tr("guns.clear.hint")));
            else tag(bx, by, cw, TAG_H, clear, TAG, act, List.of(tr("guns.clear.hint")));
            bx += cw + 4;
        }
        if (!r.guns().isEmpty()) {
            String ready = tr("plot.ready", r.gunsReady(), r.gunCount());
            int rw = gw(ready, 1);
            if (x1 - rw > bx + 8) grease(ready, x1 - rw, by + 2, r.gunsReady() > 0 ? GREEN : BLACK, 1);
        }
        c.fill(x0 - 1, by + TAG_H + 3, x1 + 1, by + TAG_H + 4, PRINT);
        return by + TAG_H + 4;
    }

    private double scanRadius() {
        double best = 0;
        for (WatchView.Gun g : watch.guns()) best = Math.max(best, g.reach());
        return Math.max(32, Math.min(320, best));
    }

    private void picker(int x0, int y0, int x1, int y1) {
        pickerY0 = y0;
        pickerY1 = y1;
        List<WatchView.Contact> cs = watch.contacts();
        if (cs.isEmpty()) {
            int ty = y0 + 6;
            for (String l : wrap(tr("picker.none", (int) scanRadius()), x1 - x0 - 12)) {
                c.text(l, x0 + 6, ty, PRINT_SOFT);
                ty += LINE - 1;
            }
            return;
        }
        int rowH = LINE;
        int rows = Math.max(1, (y1 - y0 - 6) / rowH);
        int maxScroll = Math.max(0, cs.size() + 1 - rows);
        pickerScroll = clampInt(pickerScroll, 0, maxScroll);
        int rangeW = host.width("999 m") + 6;
        int brgW = host.width(tr("dial.bearing", 179)) + 8;
        int closeW = host.width(tr("dial.closing", -99)) + 4;
        int nameX = x0 + 14;
        int nameMax = x1 - 6 - closeW - brgW - rangeW - nameX;
        int ty = y0 + 3;
        int drawn = 0;
        boolean dividerDone = false;
        int index = 0;
        for (int i = 0; i < cs.size(); i++) {
            WatchView.Contact k = cs.get(i);
            boolean needDivider = !dividerDone && k.band() >= ThreatOrder.PLAYER_BAND && i > 0;
            if (needDivider) {
                dividerDone = true;
                if (index++ >= pickerScroll && drawn < rows) {
                    c.fill(x0 + 4, ty + 4, x1 - 4, ty + 5, PRINT_RULE);
                    c.text(tr("picker.players"), x0 + 6, ty, PRINT_SOFT);
                    ty += rowH;
                    drawn++;
                }
            }
            if (index++ < pickerScroll || drawn >= rows) continue;
            boolean hot = mx >= x0 && mx < x1 && my >= ty - 1 && my < ty + rowH - 1;
            if (hot) c.fill(x0 + 2, ty - 1, x1 - 2, ty + rowH - 1, 0x33FFFFFF);
            boolean armed = k.kind() == WatchView.KIND_PLAYER && confirming("player" + k.entityId());
            int ink = k.kind() == WatchView.KIND_PLAYER ? BLUE : BLACK;
            symbol(x0 + 7, ty + 4, k.kind(), ink);
            if (armed) {
                grease(fitG(tr("picker.again"), nameMax), nameX, ty, RED, 1);
            } else {
                String name = fit(contactName(k), nameMax);
                if (isTarget(k)) grease(fitG(contactName(k), nameMax), nameX, ty, RED, 1);
                else c.text(name, nameX, ty, PRINT);
            }
            String rangeText = meters(k.range());
            c.text(rangeText, x1 - 6 - closeW - brgW - host.width(rangeText), ty, PRINT);
            c.text(bearingText(k.bearing()), x1 - 6 - closeW - brgW + 6, ty, PRINT_SOFT);
            String cl = closingText(k.closing());
            c.text(cl, x1 - 6 - host.width(cl), ty, k.closing() > 0.5f ? RED : PRINT_SOFT);
            final WatchView.Contact target = k;
            hit(x0 + 2, ty - 1, x1 - x0 - 4, rowH, () -> designate(target), contactTip(k));
            ty += rowH;
            drawn++;
        }
    }

    private void table(int x0, int y0, int x1, int y1) {
        tableX0 = x0;
        tableX1 = x1;
        List<BattleReport.GunLine> guns = r.guns();
        if (guns.isEmpty()) {
            int ty = y0 + 2;
            for (String l : wrap(tr("guns.none"), x1 - x0)) {
                c.text(l, x0, ty, PRINT_SOFT);
                ty += LINE - 1;
            }
            return;
        }
        int modeW = Math.max(host.width(tr("mode.1")), Math.max(host.width(tr("mode.0")), host.width(tr("mode.2")))) + 10;
        int loadW = host.width("9999") + 4;
        int aimW = host.width("-179 -89") + 4;
        int typeW = Math.max(host.width(tr("gun.type.0")), Math.max(host.width(tr("gun.type.1")), host.width(tr("gun.type.2")))) + 4;
        int stateW = clampInt((x1 - x0) * 30 / 100, 50, 110);
        int nameW = x1 - x0 - modeW - loadW - aimW - typeW - stateW - 10;
        if (nameW < 36) {
            aimW = 0;
            nameW = x1 - x0 - modeW - loadW - typeW - stateW - 8;
        }
        if (nameW < 36) {
            typeW = 0;
            nameW = x1 - x0 - modeW - loadW - stateW - 6;
        }
        if (nameW < 36) {
            loadW = 0;
            nameW = x1 - x0 - modeW - stateW - 4;
        }
        int xType = x0 + nameW + 2;
        int xState = xType + typeW + (typeW > 0 ? 2 : 0);
        int xLoad = xState + stateW + 2;
        int xAim = xLoad + loadW + (loadW > 0 ? 2 : 0);
        int xMode = x1 - modeW;

        int y = y0;
        c.text(fit(tr("guns.col.gun"), nameW - 8), x0 + 8, y, PRINT_SOFT);
        if (typeW > 0) c.text(fit(tr("guns.col.type"), typeW), xType, y, PRINT_SOFT);
        c.text(fit(tr("guns.col.state"), stateW), xState, y, PRINT_SOFT);
        if (loadW > 0) c.text(fit(tr("guns.col.load"), loadW), xLoad, y, PRINT_SOFT);
        if (aimW > 0) c.text(fit(tr("guns.col.aim"), aimW), xAim, y, PRINT_SOFT);
        c.text(fit(tr("guns.col.mode"), modeW), xMode, y, PRINT_SOFT);
        y += LINE;

        c.text(tr("guns.all"), x0, y + 2, PRINT);
        int bw = Math.max(20, Math.min(modeW + 6, (x1 - x0 - nameW - 6) / 3 - 3));
        int ax = x1 - (bw + 3) * 3 + 3;
        for (int m = 0; m < 3; m++) {
            final int mode = m;
            tag(ax + m * (bw + 3), y, bw, TAG_H, fit(tr("mode." + m), bw - 4), TAG, () -> setMode(-1, mode),
                    List.of(tr("guns.all.hint", tr("mode." + m)), tr("mode." + m + ".hint")));
        }
        y += TAG_H + 4;
        c.fill(x0 - 1, y - 2, x1 + 1, y - 1, PRINT_RULE);

        List<String> problems = new ArrayList<>();
        for (BattleReport.GunLine g : guns) {
            GunState st = g.gunState();
            if (st.lamp() == HeaderStatus.Lamp.RED || st == GunState.BLOCKED || st == GunState.OUT_OF_RANGE
                    || st == GunState.OUT_OF_ARC || st == GunState.OFF_BORE) {
                problems.add(tr("guns.problem", g.name(), sentence(g)));
            }
        }
        List<String> wrapped = new ArrayList<>();
        for (String pr : problems) wrapped.addAll(wrap(pr, x1 - x0 - 4));
        List<int[]> lines = new ArrayList<>();
        int arcAt = -1;
        for (int i = 0; i < guns.size(); i++) {
            lines.add(new int[]{i, 0});
            if (guns.get(i).id() != arcGun) continue;
            arcAt = lines.size();
            lines.add(new int[]{i, 1});
            if (!fixedMount(arcGun)) lines.add(new int[]{i, 2});
        }
        int roomBelow = y1 - y;
        int rowsNeeded = lines.size() + (r.gunCount() > guns.size() ? 1 : 0);
        boolean showAll = problemsOpen || (rowsNeeded * LINE + LINE + wrapped.size() * (LINE - 1) <= roomBelow);
        int problemLines = problems.isEmpty() ? 0 : showAll ? 1 + wrapped.size() : 1;
        int problemsH = problems.isEmpty() ? 0 : problemLines * (LINE - 1) + 4;
        tableY0 = y;
        int rowsRoom = Math.max(1, (y1 - y - problemsH) / LINE);
        tableY1 = y + rowsRoom * LINE;
        int maxScroll = Math.max(0, rowsNeeded - rowsRoom);
        if (arcReveal && arcAt >= 0) {
            int last = arcAt + 1 < lines.size() && lines.get(arcAt + 1)[1] == 2 ? arcAt + 1 : arcAt;
            if (last >= tableScroll + rowsRoom) tableScroll = last - rowsRoom + 1;
            if (arcAt - 1 < tableScroll) tableScroll = arcAt - 1;
        }
        arcReveal = false;
        tableScroll = clampInt(tableScroll, 0, maxScroll);
        int shown = 0;
        for (int i = tableScroll; i < lines.size() && shown < rowsRoom; i++, shown++) {
            int[] line = lines.get(i);
            BattleReport.GunLine g = guns.get(line[0]);
            if (line[1] == 0) gunRow(g, x0, y, x1, nameW, xType, typeW, xState, stateW, xLoad, loadW, xAim, aimW, xMode, modeW);
            else arcRow(g, line[1] == 1, x0, y, x1);
            y += LINE;
        }
        if (r.gunCount() > guns.size() && shown < rowsRoom) {
            c.text(fit(tr("guns.more", r.gunCount() - guns.size()), x1 - x0), x0, y, PRINT_SOFT);
            y += LINE;
        }

        if (shown >= rowsNeeded - (r.gunCount() > guns.size() ? 1 : 0)) {
            gunsPlan(x0, y + 6, x1, y1 - problemsH - 4);
        }
        if (!problems.isEmpty()) {
            int py = y1 - problemsH + 3;
            c.fill(x0 - 1, py - 2, x1 + 1, py - 1, PRINT);
            String head = tr(showAll ? "guns.problems" : "guns.problems_closed", problems.size());
            grease(fitG(head, x1 - x0), x0, py, RED, 1);
            hit(x0, py - 1, gw(head, 1), LINE - 1, () -> problemsOpen = !problemsOpen, List.of(tr("guns.problems.hint")));
            py += LINE - 1;
            if (showAll) {
                for (String l : wrapped) {
                    if (py + LINE - 1 > y1 + 1) break;
                    c.text(l, x0 + 4, py, PRINT);
                    py += LINE - 1;
                }
            }
        }
    }

    private void gunRow(BattleReport.GunLine g, int x0, int y, int x1, int nameW, int xType, int typeW, int xState, int stateW,
                        int xLoad, int loadW, int xAim, int aimW, int xMode, int modeW) {
        boolean hot = mx >= x0 && mx < x1 && my >= y - 1 && my < y + LINE - 1;
        boolean picked = selectedJob != null && (selectedJob.equals("gun:" + g.id()) || selectedJob.equals("load:" + g.id()));
        if (picked) c.fill(x0 - 2, y - 1, x1, y + LINE - 1, HIGHLIGHTER);
        else if (hot) c.fill(x0 - 2, y - 1, x1, y + LINE - 1, 0x33FFFFFF);
        final int gunId = g.id();
        boolean open = arcGun == gunId;
        disclosure(x0, y, open);
        hit(x0 - 1, y - 1, 8, LINE, () -> toggleArcs(gunId), List.of(tr(open ? "guns.arcs.close" : "guns.arcs.open"), arcSummary(g)));
        int nx = x0 + 8;
        int nw = nameW - 8;
        if (editingGun == g.id()) {
            c.fill(nx - 1, y - 2, nx - 1 + nw, y + LINE - 2, PRINT);
            c.fill(nx, y - 1, nx - 2 + nw, y + LINE - 3, TAG);
            String shown = fitTail(editText + ((clock / 6) % 2 == 0 ? "_" : ""), nw - 4);
            c.text(shown, nx + 1, y, TAG_INK);
        } else {
            String name = fit(g.name(), nw);
            c.text(name, nx, y, PRINT);
            final String current = g.name();
            hit(nx, y - 1, Math.max(12, host.width(name)), LINE, () -> startEdit(gunId, current), List.of(g.name(), tr("guns.rename.hint")));
        }
        if (typeW > 0) c.text(fit(tr("gun.type." + Math.max(0, Math.min(2, g.kind()))), typeW), xType, y, PRINT_SOFT);
        GunState st = g.gunState();
        String word = tr("gun.state." + st.key());
        String shownWord = gw(word, 1) <= stateW ? word : tr("gun.brief." + st.key());
        grease(fitG(shownWord, stateW), xState, y, lampInk(st.lamp()), 1);
        hit(xState, y - 1, stateW, LINE, null, List.of(word, sentence(g)));
        if (loadW > 0) {
            String load = g.load() < 0 ? "?" : g.kind() == 1 ? (g.load() >= 9999 ? tr("gun.load.creative") : String.valueOf(g.load()))
                    : tr(g.load() > 0 ? "gun.load.yes" : "gun.load.no");
            c.text(fit(load, loadW), xLoad, y, PRINT);
        }
        if (aimW > 0) {
            String aim = String.format(Locale.ROOT, "%+04d %+03d", Math.round(g.train()), Math.round(g.elev()));
            c.text(fit(aim, aimW), xAim, y, PRINT_SOFT);
            hit(xAim, y - 1, aimW, LINE, () -> toggleArcs(gunId), rowDetail(g));
        }
        int mode = option(OPT_MODE + g.id(), g.mode());
        String modeText = tr("mode." + mode);
        final int next = (mode + 1) % 3;
        tag(xMode, y - 1, modeW, LINE - 1, fit(modeText, modeW - 4), TAG,
                () -> setMode(gunId, next), List.of(tr("mode." + mode + ".hint"), tr("guns.mode.cycle", tr("mode." + next))));
    }

    private void gunsPlan(int x0, int y0, int x1, int y1) {
        ShapeView s = shape;
        if (s == null || !r.hasReference() || y1 - y0 < 50) return;
        Projection.Frame f = s.frame();
        int pw = f.planW();
        int ph = f.planH();
        int avW = x1 - x0 - 4;
        int avH = y1 - y0 - 14;
        int pool = 1;
        int scale;
        while (true) {
            scale = Math.min(4, Math.min(avW / Math.max(1, Projection.ceilDiv(pw, pool)), avH / Math.max(1, Projection.ceilDiv(ph, pool))));
            if (scale >= 1 || pool > 64) break;
            pool++;
        }
        int cw = Projection.ceilDiv(pw, pool);
        int cph = Projection.ceilDiv(ph, pool);
        if (scale < 1 || cw * scale < 80) return;
        c.fill(x0 - 1, y0 - 3, x1 + 1, y0 - 2, PRINT_RULE);
        String title = tr(r.ground() ? "sheet.plan.ground" : "sheet.plan");
        c.text(title, x0, y0, PRINT);
        marks.add(new int[]{x0 - 3, y0 - 1, x1 + 3, y0 + 9});
        byte[] plan = cells(s.plan(), pw, ph, true);
        if (pool > 1) plan = Projection.pool(plan, pw, ph, pool);
        int ox = x0 + 2 + (avW - cw * scale) / 2;
        int oy = y0 + 13;
        clip = new int[]{x0 - 3, y0 - 1, x1 + 3, y1};
        drawShip(plan, cw, cph, ox, oy, scale, true);
        planSymbols(ox, oy, scale, pool, plan, cw, cph);
        BattleReport.GunLine open = gunById(arcGun);
        if (open != null && !fixedMount(open.id()) && !shownArc(open).allRound()) planArc(open, ox, oy, scale, pool);
        hoverCell(ox, oy, cw, cph, scale, pool, true);
        clip = null;
    }

    private void planArc(BattleReport.GunLine g, int ox, int oy, int scale, int pool) {
        int packed = gunPacked(g.id());
        ShapeView s = shape;
        if (packed < 0 || s == null) return;
        int[] cell = s.planCell(packed);
        int cx = ox + cell[0] / pool * scale + scale / 2;
        int cy = oy + cell[1] / pool * scale + scale / 2;
        GunArc a = shownArc(g);
        int radius = Math.max(14, scale * 6);
        for (int i = 0; i <= a.width(); i += 3) {
            double b = Math.toRadians(a.from() + i);
            double dx = r.ground() ? Math.sin(b) : Math.cos(b);
            double dy = r.ground() ? -Math.cos(b) : Math.sin(b);
            int px = cx + (int) Math.round(dx * radius);
            int py = cy + (int) Math.round(dy * radius);
            c.fill(px, py, px + 2, py + 2, GREEN);
        }
        for (int stop : new int[]{a.from(), a.to()}) {
            double b = Math.toRadians(stop);
            double dx = r.ground() ? Math.sin(b) : Math.cos(b);
            double dy = r.ground() ? -Math.cos(b) : Math.sin(b);
            for (int k = 5; k <= radius; k += 3) {
                int px = cx + (int) Math.round(dx * k);
                int py = cy + (int) Math.round(dy * k);
                c.fill(px, py, px + 1, py + 1, RED);
            }
        }
    }

    private List<String> rowDetail(BattleReport.GunLine g) {
        List<String> out = new ArrayList<>();
        out.add(tr("guns.aim_tip", Math.round(g.train()), Math.round(g.elev())));
        out.add(arcSummary(g));
        out.add(tr("guns.arcs.click"));
        WatchView.Gun d = watch.gun(g.id());
        if (d == null) return out;
        if (d.flight() > 0) out.add(tr("detail.flight", String.format(Locale.ROOT, "%.1f", d.flight() / 20f),
                String.format(Locale.ROOT, "%.1f", d.lead())));
        out.add(tr("detail.error", String.format(Locale.ROOT, "%.2f", d.error()), String.format(Locale.ROOT, "%.1f", d.miss())));
        if (d.speed() > 0) out.add(tr("detail.speed", String.format(Locale.ROOT, "%.1f", d.speed() * 20)));
        if (d.mount() != 2) {
            out.add(tr("detail.rpm", d.yawRpm(), d.pitchRpm()));
            out.add(tr("detail.gain", String.format(Locale.ROOT, "%+.3f", d.yawGain()), String.format(Locale.ROOT, "%+.3f", d.pitchGain())));
        }
        if (d.reach() > 0) out.add(tr("detail.reach", Math.round(d.reach())));
        return out;
    }

    private void setMode(int gunId, int mode) {
        if (gunId < 0) {
            for (BattleReport.GunLine g : r.guns()) optimistic.put(OPT_MODE + g.id(), new long[]{mode, clock + OPTIMISTIC_TICKS});
        } else {
            optimistic.put(OPT_MODE + gunId, new long[]{mode, clock + OPTIMISTIC_TICKS});
        }
        send(BattleBridgeAction.SET_MODE, gunId, mode);
    }

    private void setWeapons(boolean free) {
        optimistic.put(OPT_WEAPONS, new long[]{free ? 1 : 0, clock + OPTIMISTIC_TICKS});
        send(BattleBridgeAction.SET_WEAPONS, free ? 1 : 0, 0);
    }

    private void startEdit(int id, String current) {
        editingGun = id;
        editText = current.matches("G\\d+") ? "" : current;
    }

    private void toggleArcs(int gunId) {
        arcGun = arcGun == gunId ? -1 : gunId;
        arcReveal = arcGun >= 0;
    }

    @Nullable
    private BattleReport.GunLine gunById(int id) {
        if (id < 0) return null;
        for (BattleReport.GunLine g : r.guns()) if (g.id() == id) return g;
        return null;
    }

    private boolean fixedMount(int gunId) {
        WatchView.Gun d = watch.gun(gunId);
        return d != null && d.mount() == 2;
    }

    private void disclosure(int x, int y, boolean open) {
        for (int i = 0; i < 4; i++) {
            if (open) c.fill(x + i, y + 2 + i, x + 7 - i, y + 3 + i, PRINT);
            else c.fill(x + 1 + i, y + i, x + 2 + i, y + 7 - i, PRINT);
        }
    }

    private void arcRow(BattleReport.GunLine g, boolean train, int x0, int y, int x1) {
        c.fill(x0 + 3, y - 1, x0 + 4, y + LINE - 1, PRINT_SOFT);
        int x = x0 + 8;
        if (fixedMount(g.id())) {
            c.text(fit(tr("guns.arcs.fixed"), x1 - x), x, y, PRINT_SOFT);
            return;
        }
        GunArc a = shownArc(g);
        boolean on = train ? !a.allRound() : !a.fullElevation();
        final int here = Math.round(g.train());
        Runnable toggle = train ? () -> editArc(g, on ? GunArc.FIELD_ALL_ROUND : GunArc.FIELD_AROUND, on ? 1 : here)
                : () -> editArc(g, GunArc.FIELD_FULL, on ? 1 : 0);
        checkbox(x, y - 1, on, tr(train ? "guns.arcs.train" : "guns.arcs.elev"), toggle,
                List.of(tr(train ? "guns.arcs.train.hint" : "guns.arcs.elev.hint")));
        int sx = x + Math.max(host.width(tr("guns.arcs.train")), host.width(tr("guns.arcs.elev"))) + 16;
        if (!on) {
            c.text(fit(tr(train ? "guns.arcs.all_round_row" : "guns.arcs.full_row"), x1 - sx), sx, y, PRINT_SOFT);
            return;
        }
        int valueW = train ? stopTextWidth() : Math.max(host.width(elevText(-90)), host.width(elevText(90)));
        String to = tr("guns.arcs.to");
        int toX = sx + valueW + 28;
        List<String> tip = List.of(tr(train ? "guns.arcs.stop.hint" : "guns.arcs.limit.hint"));
        int lo = train ? a.from() : a.low();
        int hi = train ? a.to() : a.high();
        int fieldLo = train ? GunArc.FIELD_FROM : GunArc.FIELD_LOW;
        int fieldHi = train ? GunArc.FIELD_TO : GunArc.FIELD_HIGH;
        arcStepper(sx, y, train ? stopText(lo) : elevText(lo), valueW,
                () -> editArc(g, fieldLo, lo - GunArc.STEP), () -> editArc(g, fieldLo, lo + GunArc.STEP), tip);
        c.text(to, toX, y, PRINT_SOFT);
        arcStepper(toX + host.width(to) + 4, y, train ? stopText(hi) : elevText(hi), valueW,
                () -> editArc(g, fieldHi, hi - GunArc.STEP), () -> editArc(g, fieldHi, hi + GunArc.STEP), tip);
    }

    private void arcStepper(int x, int y, String value, int valueW, Runnable minus, Runnable plus, List<String> tip) {
        tag(x, y - 1, 9, LINE - 1, "-", TAG, minus, tip);
        grease(value, x + 12 + (valueW - gw(value, 1)) / 2, y, BLACK, 1);
        tag(x + 15 + valueW, y - 1, 9, LINE - 1, "+", TAG, plus, tip);
    }

    String stopText(int bearing) {
        if (r.ground()) return tr("dial.bearing", Math.floorMod(bearing, 360));
        if (bearing == 0) return tr("guns.arcs.bow");
        if (bearing == 180 || bearing == -180) return tr("guns.arcs.stern");
        return bearing < 0 ? tr("guns.arcs.port", -bearing) : tr("guns.arcs.stbd", bearing);
    }

    private int stopTextWidth() {
        List<String> widest = r.ground() ? List.of(tr("dial.bearing", 355))
                : List.of(tr("guns.arcs.port", 175), tr("guns.arcs.stbd", 175), tr("guns.arcs.bow"), tr("guns.arcs.stern"));
        int best = 0;
        for (String s : widest) best = Math.max(best, gw(s, 1));
        return best;
    }

    private String elevText(int degrees) {
        return tr("guns.arcs.deg", degrees);
    }

    private String arcSummary(BattleReport.GunLine g) {
        if (fixedMount(g.id())) return tr("guns.arcs.fixed");
        GunArc a = shownArc(g);
        String train = a.allRound() ? tr("guns.arcs.all_round") : tr("guns.arcs.span", stopText(a.from()), stopText(a.to()));
        String elev = a.fullElevation() ? tr("guns.arcs.full") : tr("guns.arcs.span", elevText(a.low()), elevText(a.high()));
        return tr("guns.arcs.summary", train, elev);
    }

    private GunArc shownArc(BattleReport.GunLine g) {
        int base = OPT_ARC + 8 * g.id();
        GunArc rep = g.arc();
        return new GunArc(option(base, rep.from()), option(base + 1, rep.to()), option(base + 2, rep.low()),
                option(base + 3, rep.high()));
    }

    private void editArc(BattleReport.GunLine g, int field, int value) {
        GunArc now = shownArc(g);
        GunArc next = now.with(field, value);
        if (next.equals(now)) return;
        int base = OPT_ARC + 8 * g.id();
        long until = clock + OPTIMISTIC_TICKS;
        optimistic.put(base, new long[]{next.from(), until});
        optimistic.put(base + 1, new long[]{next.to(), until});
        optimistic.put(base + 2, new long[]{next.low(), until});
        optimistic.put(base + 3, new long[]{next.high(), until});
        send(BattleBridgeAction.SET_ARC, g.id(), GunArc.pack(field, value));
    }

    private void arcWedge(int cx, int cy, int rad, GunArc a) {
        int band = rad - 9;
        int steps = a.width();
        for (int i = 0; i <= steps; i++) {
            double b = Math.toRadians(a.from() + i);
            int px = cx + (int) Math.round(Math.sin(b) * band);
            int py = cy - (int) Math.round(Math.cos(b) * band);
            c.fill(px, py, px + 2, py + 2, GREEN);
        }
        for (int stop : new int[]{a.from(), a.to()}) {
            double b = Math.toRadians(stop);
            for (int k = 5; k <= band; k += 3) {
                int px = cx + (int) Math.round(Math.sin(b) * k);
                int py = cy - (int) Math.round(Math.cos(b) * k);
                c.fill(px, py, px + 1, py + 1, RED);
            }
        }
    }

    public boolean charTyped(char ch) {
        if (editingGun < 0) return false;
        if (ch >= ' ' && ch != 127 && editText.length() < 24) editText += ch;
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (editingGun < 0) return false;
        switch (keyCode) {
            case 257, 335 -> {
                host.send(BattleBridgeAction.RENAME_GUN, editingGun, 0, editText.strip());
                editingGun = -1;
            }
            case 256 -> editingGun = -1;
            case 259 -> {
                if (!editText.isEmpty()) editText = editText.substring(0, editText.length() - 1);
            }
            default -> {
                return true;
            }
        }
        return true;
    }

    public boolean editing() {
        return editingGun >= 0;
    }

    private String fitTail(String text, int max) {
        String t = text;
        while (!t.isEmpty() && host.width(t) > max) t = t.substring(1);
        return t;
    }

    String sentence(BattleReport.GunLine g) {
        String name = g.name();
        String target = targetName(r.target());
        return switch (g.gunState()) {
            case DOWN -> g.argS().isEmpty() ? tr("gun.say.down", name) : tr("gun.say.down_at", name, place(g.argS()));
            case NOT_ASSEMBLED -> tr("gun.say.not_assembled");
            case HOLD -> tr("gun.say.hold");
            case NO_POWER -> tr("gun.say.no_power");
            case HAND_AIMED -> tr("gun.say.hand_aimed", name);
            case NO_YAW_DRIVE -> g.argA() == 2 ? tr("gun.say.shared", name) : g.argA() == 1 ? tr("gun.say.two_yaw", name)
                    : tr(g.argB() == 1 ? "gun.say.no_yaw_hanging" : "gun.say.no_yaw", name);
            case NO_PITCH_DRIVE -> g.argA() == 2 ? tr("gun.say.shared", name) : g.argA() == 1 ? tr("gun.say.two_pitch", name)
                    : tr("gun.say.no_pitch", name);
            case NO_ROTATION -> tr("gun.say.no_rotation", name, axisWord(g.axis()));
            case HELD_ELSEWHERE -> tr("vcc".equals(g.argS()) ? "gun.say.held_vcc" : "gun.say.held", name, axisWord(g.axis()));
            case OVERSTRESSED -> tr("gun.say.overstressed", name, axisWord(g.axis()));
            case AIM_FAULT -> tr("gun.say.aim_fault", name, axisWord(g.axis()));
            case IDLE -> tr("gun.say.idle");
            case OUT_OF_RANGE -> tr("gun.say.out_of_range", target, Math.round(g.argA()), name, Math.round(g.argB()));

            case OUT_OF_ARC -> g.axis() == GunState.AXIS_YAW
                    ? tr("gun.say.out_of_train", target, stopText(Math.round(g.argA())), name, stopText(g.arc().from()),
                    stopText(g.arc().to()))
                    : tr(g.argA() < g.argB() ? "gun.say.out_of_arc_low" : "gun.say.out_of_arc_high", target,
                    Math.round(g.argA()), name, Math.round(g.argB()));
            case OFF_BORE -> tr("gun.say.off_bore", turnText(g.argA()), upText(g.argB()), name);
            case SLEWING -> tr("gun.say.slewing", Math.max(1, Math.round(g.argA())));
            case BLOCKED -> g.argS().startsWith("p:") ? tr("gun.say.blocked_player", g.argS().substring(2))
                    : "?survey".equals(g.argS()) ? tr("gun.say.blocked_survey", name)
                    : "?far".equals(g.argS()) ? tr("gun.say.blocked_far", name)
                    : tr("gun.say.blocked", target, place(g.argS()), name);
            case EMPTY -> tr(switch ((int) g.argA()) {
                case GunState.EMPTY_AUTO -> "gun.say.empty_auto";
                case GunState.EMPTY_DRY -> "gun.say.dry";
                case GunState.EMPTY_NO_CHARGE -> "gun.say.empty_no_charge";
                case GunState.EMPTY_NO_SHELL -> "gun.say.empty_no_shell";
                case GunState.EMPTY_DEAD_CHARGE -> "gun.say.empty_dead_charge";
                case GunState.EMPTY_STUCK_SHELL -> "gun.say.empty_stuck_shell";
                case GunState.EMPTY_DROP_MORTAR -> "gun.say.empty_drop_mortar";
                default -> "gun.say.empty_big";
            }, name);
            case LOADING -> tr(g.argA() == GunState.LOADING_BREECH ? "gun.say.loading_breech"
                    : g.argA() == GunState.LOADING_SETTLE ? "gun.say.loading_settle" : "gun.say.loading");
            case SAFE -> tr("gun.say.safe");
            case READY -> tr(g.mode() == GunState.MODE_FIRE ? "gun.say.ready_fire" : "gun.say.ready");
            case FIRING -> tr("gun.say.firing");
            case UNSAFE -> unsafeSentence(g, name);
        };
    }

    private String unsafeSentence(BattleReport.GunLine g, String name) {
        int chance = Math.round(g.argB());
        return switch ((int) g.argA()) {
            case GunState.UNSAFE_OVERLOAD -> {
                String[] load = g.argS().split("/", 2);
                yield tr("gun.say.unsafe_overload", name, load[0], load.length > 1 ? load[1] : "?", chance);
            }
            case GunState.UNSAFE_BARREL -> tr("gun.say.unsafe_barrel", name, chance);
            case GunState.UNSAFE_ORDER -> tr("gun.say.unsafe_order", name);
            case GunState.UNSAFE_OBSTRUCTED -> tr("gun.say.unsafe_obstructed", name);
            case GunState.UNSAFE_SHELL -> tr("gun.say.unsafe_shell", name);
            case GunState.UNSAFE_UNDERCHARGED -> tr("gun.say.unsafe_undercharged", name);
            default -> tr("gun.say.unsafe_unreadable", name);
        };
    }

    private String axisWord(int axis) {
        return tr(axis == 1 ? "gun.axis.pitch" : "gun.axis.yaw");
    }

    private String turnText(float deg) {
        int d = Math.round(Math.abs(deg));
        return tr(deg >= 0 ? "gun.turn.stbd" : "gun.turn.port", d);
    }

    private String upText(float deg) {
        int d = Math.round(Math.abs(deg));
        return tr(deg >= 0 ? "gun.turn.up" : "gun.turn.down", d);
    }

    String targetName(BattleReport.TargetLine t) {
        return nameText(t.name());
    }

    private String contactName(WatchView.Contact k) {
        return nameText(k.name());
    }

    private String nameText(String name) {
        if (name == null || name.isEmpty()) return tr("target.unnamed");
        return name.charAt(0) == '#' ? host.name(name.substring(1)) : name;
    }

    private String meters(float m) {
        return tr("dial.metres", Math.round(m));
    }

    private String bearingText(float deg) {
        return tr("dial.bearing", Math.round(deg));
    }

    private String closingText(float mps) {
        return tr("dial.closing", Math.round(mps));
    }

    private void setupPage(int x0, int y0, int x1, int y1) {
        paper(x0, y0, x1, y1, true);
        int ix0 = x0 + 6;
        int ix1 = x1 - 6;
        int y = y0 + 5;

        int lx1 = r.guns().isEmpty() ? ix1 : ix0 + (ix1 - ix0) * 56 / 100;

        int ay = y1 - 5 - (TAG_H + 4) * 3 - LINE - 2;
        int bowRoom = TAG_H + 6;

        heading(tr("setup.survey"), ix0, y, lx1);
        y += LINE + 2;
        String acceptLabel = confirming("shape") ? tr("confirm") : tr("setup.accept_shape");
        int aw = Math.max(host.width(tr("confirm")), host.width(tr("setup.accept_shape"))) + 10;
        int textMax = lx1 - ix0 - aw - 8;
        String survey = !r.hasReference() ? tr("setup.survey_none")
                : r.ground() ? tr("setup.survey_ground", r.groundSize(), group(r.refCount()), age(clock - r.surveyedAt()))
                : tr("setup.survey_ship", r.shipName().isEmpty() ? tr("empty.ship") : r.shipName(), group(r.refCount()),
                age(clock - r.surveyedAt()));

        int beside = Math.max(1, Projection.ceilDiv(TAG_H - 2, LINE - 1));
        List<String> lines = new ArrayList<>(wrapAround(survey, textMax, beside, lx1 - ix0));
        int surveyLines = lines.size();
        List<String> notes = new ArrayList<>();
        notes.add(tr("setup.survey_rule"));
        if (r.newBlocks() > 0) notes.add(tr("setup.survey_new", group(r.newBlocks())));
        if (r.capped()) notes.add(tr("setup.survey_capped"));
        if (r.refitCount() > 0 && r.refitAt() >= 0) {
            notes.add(r.refitBy().isEmpty() ? tr("setup.refit_banner", group(r.refitCount()), age(clock - r.refitAt()))
                    : tr("setup.refit", group(r.refitCount()), r.refitBy(), age(clock - r.refitAt())));
        }
        for (String n : notes) lines.addAll(wrapAround(n, textMax, Math.max(0, beside - lines.size()), lx1 - ix0));
        confirmTag(lx1 - aw, y - 2, aw, acceptLabel, "shape", () -> send(BattleBridgeAction.ACCEPT_SHAPE, 0, 0),
                List.of(tr("setup.accept_shape.hint")));
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0 && y + LINE - 1 > ay - bowRoom - 2) break;
            int max = i < beside ? textMax : lx1 - ix0;
            c.text(fit(lines.get(i), max), ix0, y, i < surveyLines ? PRINT : PRINT_SOFT);
            y += LINE - 1;
        }
        y += 3;
        if (y + TAG_H > ay - 2) {
        } else if (r.ground()) {
            String label = tr("setup.box");
            String value = tr("setup.box_value", r.groundSize());
            c.text(label, ix0, y + 2, PRINT);
            int vx = ix0 + host.width(label) + 4;
            int vw = gw(tr("setup.box_value", 129), 1);
            stepper(vx, y, value, vw, () -> send(BattleBridgeAction.SET_GROUND_SIZE, Math.max(17, r.groundSize() - 8), 0),
                    () -> send(BattleBridgeAction.SET_GROUND_SIZE, Math.min(129, r.groundSize() + 8), 0), tr("setup.box.hint"));
            y += TAG_H + 5;
        } else {
            String bow = tr("setup.bow." + (r.bowSource() == BattleReport.BOW_THRUST ? "thrust" : r.bowSource() == BattleReport.BOW_MANUAL ? "manual" : "placer"));
            String turn = tr("setup.turn_bow");
            int tw = host.width(turn) + 10;
            c.text(fit(bow, lx1 - ix0 - tw - 8), ix0, y + 2, PRINT);
            tag(Math.min(ix0 + host.width(bow) + 6, lx1 - tw), y, tw, TAG_H, turn, TAG, () -> send(BattleBridgeAction.TURN_BOW, 0, 0),
                    List.of(tr("setup.turn_bow.hint")));
            y += TAG_H + 5;
        }

        if (y + LINE * 2 <= ay) {
            heading(tr("setup.sections"), ix0, y, lx1);
            y += LINE + 2;
            for (String l : wrap(tr("sections.explain"), lx1 - ix0)) {
                if (y + (LINE - 1) * 2 > ay) break;
                c.text(l, ix0, y, PRINT_SOFT);
                y += LINE - 1;
            }
            if (r.sections().isEmpty()) {
                if (y + LINE <= ay) {
                    c.text(fit(tr("sections.none_setup"), lx1 - ix0), ix0, y, PRINT_SOFT);
                    y += LINE;
                }
            } else {
                int nameW = 0;
                for (BattleReport.SectionLine s : r.sections()) nameW = Math.max(nameW, host.width(s.name().isEmpty() ? tr("sections.unnamed") : s.name()));
                nameW = Math.min(nameW, (lx1 - ix0) / 3);
                int shown = 0;
                for (int i = 0; i < r.sections().size(); i++) {
                    BattleReport.SectionLine s = r.sections().get(i);
                    boolean last = i == r.sections().size() - 1;
                    if (y + LINE > ay - 2 || (!last && y + 2 * LINE > ay - 2)) {
                        if (y + LINE <= ay - 2) c.text(tr("sections.more", r.sections().size() - shown), ix0, y, PRINT_SOFT);
                        break;
                    }
                    String name = s.name().isEmpty() ? tr("sections.unnamed") : s.name();
                    c.text(fit(name, nameW), ix0, y, PRINT);
                    String pctText = sectionPct(s.permille());
                    int ink = s.permille() < 0 ? BLUE : s.permille() >= 1000 ? GREEN : s.permille() >= 900 ? BLACK : RED;
                    grease(pctText, ix0 + nameW + 8, y, ink, 1);
                    int px = ix0 + nameW + 8 + gw("100%", 1) + 10;
                    c.text(fit(place(s.place()), lx1 - px), px, y, PRINT_SOFT);
                    y += LINE - 1;
                    shown++;
                }
            }
        }

        if (lx1 < ix1) setupGuns(lx1 + 10, y0 + 5, ix1, ay - 3);
        y = ay;
        heading(tr("setup.alarms"), ix0, y, ix1);
        y += LINE + 3;
        int half = (ix1 - ix0) / 2;
        checkbox(ix0, y + 1, option(0, r.option(BattleReport.OPT_BELL) ? 1 : 0) == 1, fit(tr("opt.bell"), half - 16),
                () -> setOption(0, r.option(BattleReport.OPT_BELL) ? 0 : 1), List.of(tr("opt.bell.hint")));
        checkbox(ix0, y + TAG_H + 5, option(1, r.option(BattleReport.OPT_LIGHTS) ? 1 : 0) == 1, fit(tr("opt.lights"), half - 16),
                () -> setOption(1, r.option(BattleReport.OPT_LIGHTS) ? 0 : 1), List.of(tr("opt.lights.hint")));
        checkbox(ix0, y + (TAG_H + 4) * 2 + 1, option(2, r.option(BattleReport.OPT_CHAT) ? 1 : 0) == 1, fit(tr("opt.chat"), half - 16),
                () -> setOption(2, r.option(BattleReport.OPT_CHAT) ? 0 : 1), List.of(tr("opt.chat.hint")));
        int rx = ix0 + half;
        String rsLabel = tr("rsin.label");
        String cmpLabel = tr("cmp.label");
        int labelW = Math.max(host.width(rsLabel), host.width(cmpLabel)) + 8;
        c.text(rsLabel, rx, y + 2, PRINT);
        optionSlots(rx + labelW, y, ix1, new String[]{tr("rsin.0"), tr("rsin.1")}, option(3, r.redstoneIn()), 3,
                new String[]{tr("rsin.0.hint"), tr("rsin.1.hint")});
        c.text(cmpLabel, rx, y + TAG_H + 6, PRINT);
        optionSlots(rx + labelW, y + TAG_H + 4, ix1, new String[]{tr("cmp.0"), tr("cmp.1"), tr("cmp.2")}, option(4, r.comparatorMode()), 4,
                new String[]{tr("cmp.0.hint"), tr("cmp.1.hint"), tr("cmp.2.hint")});
        acrylic(x0, y0, x1, y1);
    }

    private void optionSlots(int x, int y, int x1, String[] labels, int chosen, int optionId, String[] tips) {
        int total = 0;
        for (String l : labels) total += host.width(l) + 10 + 2;
        int room = x1 - x;
        List<List<String>> tipLists = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) tipLists.add(List.of(labels[i], tips[i]));
        int bx = x;
        for (int i = 0; i < labels.length; i++) {
            int bw = host.width(labels[i]) + 10;
            if (total > room) bw = Math.max(20, (room - 2 * labels.length) / labels.length);
            final int value = i;
            String text = fit(labels[i], bw - 6);
            if (i == chosen) tag(bx, y, bw, TAG_H, text, TAG, () -> setOption(optionId, value), tipLists.get(i));
            else slot(bx, y, bw, TAG_H, text, () -> setOption(optionId, value), tipLists.get(i));
            bx += bw + 2;
        }
    }

    private int option(int id, int reported) {
        long[] o = optimistic.get(id);
        if (o == null) return reported;
        if (o[0] == reported || clock > o[1]) {
            optimistic.remove(id);
            return reported;
        }
        return (int) o[0];
    }

    private void setOption(int id, int value) {
        optimistic.put(id, new long[]{value, clock + OPTIMISTIC_TICKS});
        send(BattleBridgeAction.SET_OPTION, id, value);
    }

    private void setupGuns(int x0, int y0, int x1, int y1) {
        heading(tr("setup.guns"), x0, y0, x1);
        int y = y0 + LINE + 2;
        String relearn = tr("setup.relearn");
        int rw = host.width(relearn) + 10;
        int shown = 0;
        List<BattleReport.GunLine> guns = r.guns();
        int tw = x1 - x0 - 6;
        for (BattleReport.GunLine g : guns) {
            WatchView.Gun d = watch.gun(g.id());
            int mount = d == null ? 0 : d.mount();
            // faults wrap, never cut. "Raises: two gov..." was useless

            List<String> body = new ArrayList<>();
            List<Boolean> greased = new ArrayList<>();
            if (mount == 2) {
                body.add(fit(tr("setup.fixed"), tw));
                greased.add(false);
            } else {
                boolean yawBad = d == null || d.yawWhere().isEmpty() || "!".equals(d.yawWhere()) || "=".equals(d.yawWhere());
                boolean pitchBad = d == null || d.pitchWhere().isEmpty() || "!".equals(d.pitchWhere()) || "=".equals(d.pitchWhere());
                String aim = tr("setup.aim." + (d == null ? 0 : d.aim()));
                String share = d == null ? "" : d.yawShare() > 0 ? tr("setup.shares_yaw", "G" + d.yawShare())
                        : d.pitchShare() > 0 ? tr("setup.shares_pitch", "G" + d.pitchShare()) : "";
                addSetupLine(body, greased, driveText(d == null ? "" : d.yawWhere(), true), yawBad, tw);
                addSetupLine(body, greased, driveText(d == null ? "" : d.pitchWhere(), false), pitchBad, tw);
                addSetupLine(body, greased, share.isEmpty() ? aim : aim + "  " + share, d != null && d.aim() == 2, tw);
            }
            if (y + (LINE - 1) * (1 + body.size()) > y1) {
                c.text(fit(tr("setup.guns_more", guns.size() - shown), x1 - x0), x0, y, PRINT_SOFT);
                break;
            }
            String head = g.name() + "  " + tr("setup.mount." + mount);
            c.text(fit(head, x1 - x0 - rw - 4), x0, y, PRINT);
            final int id = g.id();
            if (mount != 2) {
                tag(x1 - rw, y - 2, rw, LINE, relearn, TAG, () -> send(BattleBridgeAction.RELEARN_AIM, id, 0),
                        List.of(tr("setup.relearn.hint")));
            }
            for (int i = 0; i < body.size(); i++) {
                y += LINE - 1;
                if (greased.get(i)) grease(body.get(i), x0 + 6, y, RED, 1);
                else c.text(body.get(i), x0 + 6, y, PRINT_SOFT);
            }
            y += LINE + 1;
            shown++;
        }
    }

    private void addSetupLine(List<String> body, List<Boolean> greased, String text, boolean fault, int max) {
        if (!fault) {
            body.add(fit(text, max));
            greased.add(false);
            return;
        }
        for (String l : wrapG(text, max)) {
            body.add(l);
            greased.add(true);
        }
    }

    private String driveText(String where, boolean yaw) {
        String axis = yaw ? "setup.turns" : "setup.raises";
        if (where.isEmpty()) return tr(axis + ".none");
        if ("!".equals(where)) return tr(axis + ".two");
        if ("=".equals(where)) return tr("setup.one_for_both");
        if ("?".equals(where)) return tr(axis, tr("setup.on_turret"));
        return tr(axis, place(where));
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button == MOUSE_RIGHT) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(x, y) || hit.left() == null) continue;
            hit.left().run();
            host.click();
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double x, double y, double dy) {
        int step = dy > 0 ? -1 : 1;
        if (x >= todoX0 && x < todoX1 && y >= todoY0 && y < todoY1) {
            todoScroll = Math.max(0, todoScroll + step);
            return true;
        }
        if (page != Page.GUNS) return false;
        if (pickerOpen && y >= pickerY0 && y < pickerY1) {
            pickerScroll = Math.max(0, pickerScroll + step);
            return true;
        }
        if (!pickerOpen && x >= tableX0 && x < tableX1 && y >= tableY0 && y < tableY1) {
            tableScroll = Math.max(0, tableScroll + step);
            return true;
        }
        return false;
    }

    private void send(int action, int a, int b) {
        host.send(action, a, b, "");
    }

    private void hit(int x, int y, int hw, int hh, @Nullable Runnable onLeft, @Nullable List<String> tip) {
        hits.add(new Hit(x, y, hw, hh, onLeft, tip));
    }

    private void paper(int x0, int y0, int x1, int y1, boolean flecks) {
        c.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, STEEL_DEEP);
        c.fill(x0 - 1, y1, x1 + 2, y1 + 2, STEEL_LIT);
        c.fill(x1, y0 - 1, x1 + 2, y1 + 2, STEEL_LIT);
        c.fill(x0, y0, x1, y1, PAPER);
        if (!flecks) return;
        int n = (x1 - x0) * (y1 - y0) / 700;
        int bw = Math.max(1, x1 - x0);
        int bh = Math.max(1, y1 - y0);
        for (int i = 0; i < n; i++) {
            int k = hash(i, x0 - left, y0 - top);
            int px = x0 + k % bw;
            int py = y0 + (k >>> 11) % bh;
            c.fill(px, py, Math.min(x1, px + 1 + ((k >>> 27) & 1)), py + 1, PAPER_FLECK);
        }
    }

    private void acrylic(int x0, int y0, int x1, int y1) {
        c.fill(x0, y0, x1, y0 + 1, 0x96FFFFFF);
        c.fill(x0, y0 + 1, x0 + 1, y1, 0x6EFFFFFF);
        c.fill(x0 + 1, y1 - 1, x1, y1, 0x3C141E28);
        c.fill(x1 - 1, y0 + 1, x1, y1 - 1, 0x3C141E28);
        int k0 = x0 + (x1 - x0) * 18 / 100;
        for (int y = y0 + 1; y < y1 - 1; y += 3) {
            int gx = k0 + (y - y0) * 9 / 10;
            int a = Math.max(x0 + 1, gx);
            int b = Math.min(x1 - 1, gx + 14);
            if (b > a) c.fill(a, y, b, Math.min(y1 - 1, y + 3), GLINT);
        }
    }

    private void tag(int x, int y, int tw, int th, String text, int face, @Nullable Runnable onLeft, @Nullable List<String> tip) {
        boolean hot = onLeft != null && mx >= x && my >= y && mx < x + tw && my < y + th;
        c.fill(x + 1, y + th, x + tw + 1, y + th + 1, 0x46000000);
        c.fill(x + tw, y + 1, x + tw + 1, y + th, 0x46000000);
        c.fill(x, y, x + tw, y + th, TAG_EDGE);
        int f = hot ? (face == TAG ? TAG_HOT : lighten(face)) : face;
        c.fill(x + 1, y + 1, x + tw - 1, y + th - 1, f);
        c.fill(x + 1, y + 1, x + tw - 1, y + 2, lighten(f));
        c.fill(x + 1, y + th - 2, x + tw - 1, y + th - 1, darken(f));
        c.text(text, x + (tw - host.width(text)) / 2, y + (th - 7) / 2, TAG_INK);
        hit(x, y, tw, th, onLeft, tip);
    }

    private void confirmTag(int x, int y, int tw, String text, String key, Runnable action, List<String> tip) {
        boolean armed = confirming(key);
        tag(x, y, tw, TAG_H, text, armed ? TAG_ARMED : TAG, () -> {
            if (confirming(key)) {
                confirmUntil.remove(key);
                action.run();
            } else {
                confirmUntil.put(key, clock + CONFIRM_TICKS);
            }
        }, tip);
    }

    private boolean confirming(String key) {
        Long until = confirmUntil.get(key);
        return until != null && clock <= until;
    }

    private void slot(int x, int y, int sw, int sh, String text, @Nullable Runnable onLeft, @Nullable List<String> tip) {
        slot(x, y, sw, sh, text, true, onLeft, tip);
    }

    private void slot(int x, int y, int sw, int sh, String text, boolean legend, @Nullable Runnable onLeft,
                      @Nullable List<String> tip) {
        boolean hot = onLeft != null && mx >= x && my >= y && mx < x + sw && my < y + sh;
        for (int k = 0; k < sw; k += 3) {
            c.fill(x + k, y, Math.min(x + sw, x + k + 2), y + 1, PRINT_SOFT);
            c.fill(x + k, y + sh - 1, Math.min(x + sw, x + k + 2), y + sh, PRINT_SOFT);
        }
        for (int k = 0; k < sh; k += 3) {
            c.fill(x, y + k, x + 1, Math.min(y + sh, y + k + 2), PRINT_SOFT);
            c.fill(x + sw - 1, y + k, x + sw, Math.min(y + sh, y + k + 2), PRINT_SOFT);
        }
        if (hot) c.fill(x + 1, y + 1, x + sw - 1, y + sh - 1, 0x40FFFFFF);
        if (legend || hot) c.text(text, x + (sw - host.width(text)) / 2, y + (sh - 7) / 2, hot ? PRINT : 0xFF9DAFC4);
        hit(x, y, sw, sh, onLeft, tip);
    }

    private void slotRow(int x, int y, int sw, String[] labels, int chosen, @Nullable IntConsumer pick, int[] faces,
                         List<List<String>> tips) {
        slotRow(x, y, sw, labels, chosen, pick, faces, tips, true);
    }

    private void slotRow(int x, int y, int sw, String[] labels, int chosen, @Nullable IntConsumer pick, int[] faces,
                         List<List<String>> tips, boolean legends) {
        for (int i = 0; i < labels.length; i++) {
            int sx = x + i * (sw + 2);
            final int value = i;
            Runnable act = pick == null ? null : () -> pick.accept(value);
            String text = fit(labels[i], sw - 4);
            if (i == chosen) tag(sx, y, sw, TAG_H, text, faces[i], act, tips.get(i));
            else slot(sx, y, sw, TAG_H, text, legends, act, tips.get(i));
        }
    }

    private void stepper(int x, int y, String value, int valueW, Runnable minus, Runnable plus, String tip) {
        tag(x, y, 12, TAG_H, "-", TAG, minus, List.of(tip));
        grease(value, x + 16 + (valueW - gw(value, 1)) / 2, y + 2, BLACK, 1);
        tag(x + 16 + valueW + 4, y, 12, TAG_H, "+", TAG, plus, List.of(tip));
    }

    private void checkbox(int x, int y, boolean on, String text, Runnable toggle, List<String> tip) {
        c.fill(x, y, x + 9, y + 9, PRINT);
        c.fill(x + 1, y + 1, x + 8, y + 8, PAPER);
        if (on) {
            for (int k = 1; k < 8; k++) {
                c.fill(x + k, y + k, x + k + 1, y + k + 1, BLACK);
                c.fill(x + k, y + 8 - k, x + k + 1, y + 9 - k, BLACK);
                if (k < 7) {
                    c.fill(x + k + 1, y + k, x + k + 2, y + k + 1, BLACK);
                    c.fill(x + k + 1, y + 8 - k, x + k + 2, y + 9 - k, BLACK);
                }
            }
        }
        if (!text.isEmpty()) c.text(text, x + 12, y + 1, PRINT);
        hit(x, y - 1, 12 + host.width(text), 11, toggle, tip);
    }

    private void heading(String text, int x0, int y, int x1) {
        String t = text.toUpperCase(Locale.ROOT);
        c.text(t, x0, y, PRINT);
        c.fill(x0 - 1, y + 10, x1 + 1, y + 11, PRINT);
    }

    private void grease(String text, int x, int y, int ink, int scale) {
        if (text == null || text.isEmpty()) return;
        c.text(text, x, y, ink, scale, true);
    }

    int gw(String text, int scale) {
        if (text == null || text.isEmpty()) return 0;
        return (host.width(text) + text.length()) * scale;
    }

    String fitG(String text, int max) {
        if (text == null) return "";
        if (gw(text, 1) <= max) return text;
        String dots = "...";
        if (max <= gw(dots, 1)) return "";
        int end = text.length();
        while (end > 0 && gw(text.substring(0, end).stripTrailing() + dots, 1) > max) end--;
        return text.substring(0, end).stripTrailing() + dots;
    }

    private void greaseRing(int cx, int cy, int rx, int ry, int ink, int thick) {
        for (int t = 0; t < thick; t++) {
            int rrx = rx + t;
            int rry = ry + t;
            int prev = -1;
            for (int dy = -rry; dy <= rry; dy++) {
                double f = dy / (double) rry;
                int half = (int) Math.round(rrx * Math.sqrt(Math.max(0.0, 1.0 - f * f)));
                int lo = prev < 0 ? half : Math.min(half, prev);
                int hi = prev < 0 ? half : Math.max(half, prev);
                c.fill(cx - hi, cy + dy, cx - lo + 1, cy + dy + 1, ink);
                c.fill(cx + lo, cy + dy, cx + hi + 1, cy + dy + 1, ink);
                prev = half;
            }
        }
    }

    private void ring(int cx, int cy, int radius, int ink, int every) {
        if (radius < 3) return;
        if (every <= 1) {
            greaseRing(cx, cy, radius, radius, ink, 1);
            return;
        }
        int n = Math.max(24, (int) (2 * Math.PI * radius / every));
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            int px = cx + (int) Math.round(Math.sin(a) * radius);
            int py = cy - (int) Math.round(Math.cos(a) * radius);
            c.fill(px, py, px + 1, py + 1, ink);
        }
    }

    private void disc(int cx, int cy, int radius, int ink) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
            c.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, ink);
        }
    }

    private void line(int x0, int y0, int x1, int y1, int ink) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        for (int guard = 0; guard < 512; guard++) {
            c.fill(x, y, x + 1, y + 1, ink);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private boolean free(int x0, int y0, int x1, int y1) {
        for (int[] m : marks) {
            if (x0 < m[2] && m[0] < x1 && y0 < m[3] && m[1] < y1) return false;
        }
        return true;
    }

    private boolean placeMark(int x, int y, int mw, int mh, Runnable draw) {
        if (!inClip(x - 1, y - 1, x + mw + 1, y + mh) || !free(x - 1, y - 1, x + mw + 1, y + mh)) return false;
        marks.add(new int[]{x - 1, y - 1, x + mw + 1, y + mh});
        draw.run();
        return true;
    }

    private int[] clip = null;

    private boolean inClip(int x0, int y0, int x1, int y1) {
        int[] k = clip == null ? new int[]{left + 3, top + 3, left + w - 3, top + h - 3} : clip;
        return x0 >= k[0] && y0 >= k[1] && x1 <= k[2] && y1 <= k[3];
    }

    private static int lighten(int argb) {
        int r0 = Math.min(255, ((argb >> 16) & 0xFF) + 18);
        int g0 = Math.min(255, ((argb >> 8) & 0xFF) + 18);
        int b0 = Math.min(255, (argb & 0xFF) + 18);
        return (argb & 0xFF000000) | (r0 << 16) | (g0 << 8) | b0;
    }

    private static int darken(int argb) {
        int r0 = ((argb >> 16) & 0xFF) * 82 / 100;
        int g0 = ((argb >> 8) & 0xFF) * 82 / 100;
        int b0 = (argb & 0xFF) * 82 / 100;
        return (argb & 0xFF000000) | (r0 << 16) | (g0 << 8) | b0;
    }

    private static int lampInk(HeaderStatus.Lamp lamp) {
        return switch (lamp) {
            case RED -> RED;
            case AMBER -> BLACK;
            case GREEN -> GREEN;
            case GREY -> BLUE;
        };
    }

    static int hash(int a, int b, int c0) {
        int x = a * 0x9E3779B1 ^ b * 0x85EBCA6B ^ c0 * 0xC2B2AE35;
        x ^= x >>> 15;
        x *= 0x2C1B3C6D;
        x ^= x >>> 12;
        x *= 0x297A2D39;
        x ^= x >>> 15;
        return x & 0x7FFFFFFF;
    }

    String tr(String key, Object... args) {
        return host.tr(L + key, args);
    }

    String place(String token) {
        return PlaceName.describe(token, (k, a) -> tr(k, a));
    }

    private String sectionPct(int permille) {
        return permille < 0 ? "?" : pct(permille);
    }

    static String pct(int permille) {
        return (permille / 10) + "%";
    }

    static String permilleText(int permille) {
        return (permille / 10) + "." + (permille % 10) + "%";
    }

    static String group(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    String age(long ticks) {
        if (ticks < 0) ticks = 0;
        long s = ticks / 20;
        if (s < 60) return tr("time.s", s);
        if (s < 3600) return tr("time.min", s / 60);
        return tr("time.h", s / 3600);
    }

    static String clockText(long ticks) {
        long s = Math.max(0, ticks) / 20;
        if (s >= 3600) return (s / 3600) + "h";
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
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

    List<String> wrapAround(String text, int narrow, int n, int wide) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (host.width(candidate) <= (out.size() < n ? narrow : wide)) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) out.add(line.toString());
            line.setLength(0);
            while (out.size() < n && host.width(word) > narrow) out.add("");
            int max = out.size() < n ? narrow : wide;
            line.append(host.width(word) <= max ? word : fit(word, max));
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    List<String> wrapG(String text, int max) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (gw(candidate, 1) <= max) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) out.add(line.toString());
            line.setLength(0);
            line.append(gw(word, 1) <= max ? word : fitG(word, max));
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
        return v < lo ? lo : Math.min(v, hi);
    }

    public static final class BattleBridgeAction {
        public static final int WATCH = 0;
        public static final int REQUEST_SHAPE = 1;
        public static final int REQUEST_DETAIL = 2;
        public static final int ACCEPT_SHAPE = 3;
        public static final int ACCEPT_PLACE = 4;
        public static final int ACCEPT_REFIT = 5;
        public static final int TURN_BOW = 6;
        public static final int SET_GROUND_SIZE = 7;
        public static final int SET_OPTION = 8;
        public static final int DESIGNATE = 10;
        public static final int CLEAR_TARGET = 11;
        public static final int SET_MODE = 12;
        public static final int SET_WEAPONS = 13;
        public static final int FIRE_READY = 14;
        public static final int RENAME_GUN = 15;
        public static final int RELEARN_AIM = 16;
        public static final int SET_ARC = 17;

        private BattleBridgeAction() {
        }
    }
}
