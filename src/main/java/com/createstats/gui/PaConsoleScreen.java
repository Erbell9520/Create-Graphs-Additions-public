package com.createstats.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.client.PaVoicePlayer;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.network.PaActionPacket;
import com.createstats.pa.PaCatalog;
import com.createstats.pa.PaConsoleRequests;
import com.createstats.pa.PaSettings;
import com.createstats.pa.PaSnapshot;
import com.createstats.pa.PaSubscription;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class PaConsoleScreen extends Screen {
    private static final int MAX_W = 720;
    private static final int MAX_H = 520;
    private static final int MIN_W = 420;
    private static final int MIN_H = 300;
    private static final int MARGIN = 12;

    private static final int HEADER_H = 30;
    private static final int FOOTER_H = 24;
    private static final int RAIL_W = 30;

    private static final int TARGET_H = 26;
    private static final int GROUP_H = 20;
    private static final int TOPIC_H = 22;

    private static final int SEG_H = 15;
    private static final int CHIP_ROW = 15;
    private static final int BASE = PduTheme.BASE;
    private static final int CHIP_H = 12;
    private static final int HEADING_H = 11;
    private static final int WORD_H = 13;
    private static final int PAD = PduTheme.PAD;
    private static final int GAP = PduTheme.GAP;
    private static final int INDENT = 11;

    private static final int TEAL = 0xFF43D2B8;

    private static final String[] TABS = {"LIVE", "WATCH", "VOICE", "DEVICE", "LOG"};

    private static final int REFRESH_TICKS = 10;

    private PaSnapshot snapshot;
    private int tab;
    private String selectedKey = "";
    private int scroll;
    private int left;
    private int top;
    private int width0;
    private int height0;

    private final Set<String> expanded = new HashSet<>();

    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }

    private PaConsoleScreen(PaSnapshot snapshot) {
        super(Component.translatable("block.create_stats.pa_brain"));
        this.snapshot = snapshot;
    }

    public static void accept(PaSnapshot snapshot) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PaConsoleScreen open
                && open.snapshot.pos().equals(snapshot.pos())) {
            open.snapshot = snapshot;
            open.seedExpansion();
            return;
        }
        final PaConsoleScreen screen = new PaConsoleScreen(snapshot);
        screen.seedExpansion();
        mc.setScreen(screen);
    }

    private void seedExpansion() {
        if (!this.expanded.isEmpty()) {
            return;
        }
        String only = null;
        for (PaSnapshot.Row row : this.snapshot.catalog()) {
            if (row.depth() != 0) {
                continue;
            }
            if (only != null) {
                return;
            }
            only = row.key();
        }
        if (only != null) {
            this.expanded.add(only);
        }
    }

    @Override
    protected void init() {
        this.width0 = Math.max(MIN_W, Math.min(MAX_W, this.width - MARGIN * 2));
        this.height0 = Math.max(MIN_H, Math.min(MAX_H, this.height - MARGIN * 2));
        this.left = (this.width - this.width0) / 2;
        this.top = (this.height - this.height0) / 2;
    }

    private void send(PaActionPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    private int sinceRefresh;

    @Override
    public void tick() {
        super.tick();
        if (++this.sinceRefresh >= REFRESH_TICKS) {
            this.sinceRefresh = 0;
            this.send(PaActionPacket.of(this.snapshot.pos(), "refresh"));
        }
    }

    private int textW(String s) {
        return this.font.width(s);
    }

    private void text(GuiGraphics g, String s, int x, int y, int colour) {
        g.drawString(this.font, s, x, y, colour, false);
    }

    private void textRight(GuiGraphics g, String s, int x, int y, int colour) {
        this.text(g, s, x - this.textW(s), y, colour);
    }

    private void textCentre(GuiGraphics g, String s, int cx, int y, int colour) {
        this.text(g, s, cx - this.textW(s) / 2, y, colour);
    }

    private static int alpha(int argb, float a) {
        final int aa = (int) (((argb >>> 24) & 0xFF) * a);
        return (aa << 24) | (argb & 0xFFFFFF);
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    private String ellipsise(String s, int w) {
        if (this.textW(s) <= w) {
            return s;
        }
        String out = s;
        while (!out.isEmpty() && this.textW(out + "…") > w) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    private int chipW(String label) {
        return this.textW(label) + 10;
    }

    private int chip(GuiGraphics g, int x, int y, String label, int tint, boolean filled) {
        final int w = this.chipW(label);
        if (filled) {
            g.fill(x, y, x + w, y + CHIP_H, alpha(tint, 0.22f));
        }
        this.outline(g, x, y, w, CHIP_H, tint);
        this.text(g, label, x + 5, y + 2, tint);
        return w;
    }

    private int[] card(GuiGraphics g, int x, int y, int w, int h, String heading) {
        g.fill(x, y, x + w, y + h, PduTheme.BG_PANEL);
        this.outline(g, x, y, w, h, PduTheme.STROKE);
        if (heading != null) {
            g.fill(x + 1, y + 1, x + w - 1, y + HEADING_H + 3, PduTheme.BG_RAISED);
            this.text(g, heading, x + PAD / 2 + 2, y + 3, PduTheme.TEXT_DIM);
        }
        return new int[]{x + PAD / 2 + 2, y + HEADING_H + 8, w - PAD - 2};
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, int tint,
                        boolean filled, Runnable action) {
        if (filled) {
            g.fill(x, y, x + w, y + h, alpha(tint, 0.24f));
        }
        this.outline(g, x, y, w, h, tint);
        this.textCentre(g, label, x + w / 2, y + (h - 8) / 2, tint);
        this.hits.add(new Hit(x, y, w, h, action));
    }

    private int seg(GuiGraphics g, int x, int y, int w, String[] opts, int sel,
                    java.util.function.IntConsumer onPick) {
        final int each = w / opts.length;
        for (int i = 0; i < opts.length; i++) {
            final int ox = x + i * each;
            final boolean on = i == sel;
            if (on) {
                g.fill(ox, y, ox + each, y + SEG_H, alpha(PduTheme.ACCENT, 0.26f));
            }
            this.outline(g, ox, y, each, SEG_H, on ? PduTheme.STROKE_HOT : PduTheme.STROKE);
            this.textCentre(g, opts[i], ox + each / 2, y + 4, on ? PduTheme.TEXT : PduTheme.TEXT_FAINT);
            final int pick = i;
            this.hits.add(new Hit(ox, y, each, SEG_H, () -> onPick.accept(pick)));
        }
        return SEG_H;
    }

    private int stepper(GuiGraphics g, int x, int y, int w, String label, String value,
                        Runnable minus, Runnable plus) {
        this.text(g, label, x, y + 4, PduTheme.TEXT_DIM);
        this.outline(g, x + w - 60, y, 17, CHIP_ROW, PduTheme.STROKE);
        this.textCentre(g, "-", x + w - 51, y + 4, PduTheme.TEXT_DIM);
        this.hits.add(new Hit(x + w - 60, y, 17, CHIP_ROW, minus));
        this.outline(g, x + w - 43, y, 26, CHIP_ROW, PduTheme.STROKE_HOT);
        this.textCentre(g, value, x + w - 30, y + 4, PduTheme.TEXT);
        this.outline(g, x + w - 17, y, 17, CHIP_ROW, PduTheme.STROKE);
        this.textCentre(g, "+", x + w - 8, y + 4, PduTheme.TEXT_DIM);
        this.hits.add(new Hit(x + w - 17, y, 17, CHIP_ROW, plus));
        return CHIP_ROW;
    }

    private int slider(GuiGraphics g, int x, int y, int w, String label, String value,
                       float fraction, java.util.function.DoubleConsumer onDrag) {
        this.text(g, label, x, y, PduTheme.TEXT_DIM);
        this.textRight(g, value, x + w, y, PduTheme.TEXT);
        final int ty = y + 12;
        g.fill(x, ty, x + w, ty + 3, PduTheme.BG_DEEP);
        g.fill(x, ty, x + (int) (w * fraction), ty + 3, PduTheme.ACCENT);
        final int knob = x + (int) (w * fraction);
        g.fill(knob - 1, ty - 2, knob + 2, ty + 5, PduTheme.TEXT);
        final int sx = x;
        final int sw = w;
        this.hits.add(new Hit(x, ty - 4, w, 11,
                () -> onDrag.accept(Math.max(0.0, Math.min(1.0, (this.lastClickX - sx) / (double) sw)))));
        return 20;
    }

    private int sentence(GuiGraphics g, int x, int y, int w, List<String> words) {
        return this.sentence(g, x, y, w, words, -1);
    }

    private int sentence(GuiGraphics g, int x, int y, int w, List<String> words, int at) {
        int cx = x;
        int cy = y;
        final int lineH = WORD_H + 4;
        for (int i = 0; i < words.size(); i++) {
            final String word = words.get(i).replace('_', ' ').toUpperCase(Locale.ROOT);
            final int ww = this.textW(word) + 10;
            if (cx + ww > x + w) {
                cx = x;
                cy += lineH;
            }
            final int base = i == 0 ? severityTint(words.get(0)) : TEAL;
            final boolean spoken = at >= 0 && i < at;
            final boolean now = i == at;
            g.fill(cx, cy, cx + ww, cy + WORD_H, alpha(base, now ? 0.42f : spoken ? 0.05f : 0.16f));
            this.outline(g, cx, cy, ww, WORD_H, alpha(base, now ? 1.0f : spoken ? 0.16f : 0.55f));
            this.text(g, word, cx + 5, cy + 3, now ? PduTheme.TEXT : spoken ? alpha(base, 0.8f) : base);
            if (i < words.size() - 1 && cx + ww + 6 <= x + w) {
                g.fill(cx + ww + 1, cy + WORD_H / 2, cx + ww + 4, cy + WORD_H / 2 + 1,
                        PduTheme.TEXT_FAINT);
            }
            cx += ww + 5;
        }
        return cy + WORD_H - y;
    }

    private static int severityTint(String firstWord) {
        return switch (firstWord) {
            case "alert" -> PduTheme.CRIT;
            case "warning" -> PduTheme.WARN;
            case "caution" -> PduTheme.INFO;
            default -> TEAL;
        };
    }

    private static int tintOf(AlarmSeverity severity) {
        return switch (severity) {
            case CRITICAL -> PduTheme.CRIT;
            case WARNING -> PduTheme.WARN;
            case NOTICE -> PduTheme.INFO;
            case NORMAL -> PduTheme.OK;
        };
    }

    private void severityDot(GuiGraphics g, int x, int y, AlarmSeverity severity) {
        final int tint = tintOf(severity);
        g.fill(x, y, x + 5, y + 5, tint);
        g.fill(x + 1, y - 1, x + 4, y + 6, alpha(tint, 0.45f));
    }

    private static String fmt(float v) {
        if (Math.abs(v) >= 10_000) {
            return String.format(Locale.ROOT, "%.1fk", v / 1000.0f);
        }
        return Math.abs(v - Math.round(v)) < 0.05f ? String.valueOf(Math.round(v))
                : String.format(Locale.ROOT, "%.1f", v);
    }

    private int lastClickX;

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xB8000000);
        this.hits.clear();

        final int x = this.left;
        final int y = this.top;
        g.fill(x, y, x + this.width0, y + this.height0, PduTheme.BG_DEEP);
        this.outline(g, x, y, this.width0, this.height0, PduTheme.STROKE);

        this.drawChrome(g, x, y);

        final int cx = x + RAIL_W + GAP;
        final int cy = y + HEADER_H + GAP;
        final int cw = this.width0 - RAIL_W - GAP * 2 - 1;
        final int ch = this.height0 - HEADER_H - FOOTER_H - GAP * 2;

        switch (this.tab) {
            case 0 -> this.drawLive(g, cx, cy, cw, ch);
            case 1 -> this.drawWatch(g, cx, cy, cw, ch);
            case 2 -> this.drawVoice(g, cx, cy, cw, ch);
            case 3 -> this.drawDevice(g, cx, cy, cw, ch);
            default -> this.drawLog(g, cx, cy, cw, ch);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawChrome(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 1, x + this.width0 - 1, y + HEADER_H, PduTheme.BG_PANEL);
        g.fill(x + 1, y + HEADER_H - 1, x + this.width0 - 1, y + HEADER_H, PduTheme.STROKE);
        this.text(g, "Announcement Computer", x + PAD, y + 7, PduTheme.TEXT);
        this.text(g, this.snapshot.subs().size() + " announced · "
                        + this.snapshot.catalog().size() + " in reach", x + PAD, y + 18,
                PduTheme.TEXT_FAINT);

        int right = x + this.width0 - PAD;
        final PaSettings s = this.snapshot.settings();
        final boolean powered = this.snapshot.powered();

        if (this.snapshot.importable() > 0) {
            final String label = "Adopt " + this.snapshot.importable() + " existing";
            final int w = this.chipW(label) + 8;
            right -= w;
            this.button(g, right, y + 8, w, CHIP_ROW, label, PduTheme.OK, true,
                    () -> this.send(PaActionPacket.of(this.snapshot.pos(), "adopt_all")));
            right -= BASE * 2;
        }

        final PaSnapshot.Voice voice = this.snapshot.voice();
        final String state = !powered ? "NO POWER"
                : this.snapshot.speaking() ? "SPEAKING"
                : voice.starved() ? "LOW POWER"
                : voice.held() ? "HELD"
                : s.muted() ? "MUTED" : "IDLE";
        final String[] labels = {
            state,
            this.snapshot.queued() + " queued",
            this.snapshot.ratedDraw() + " FE/t",
        };
        final int[] tints = {
            !powered ? PduTheme.CRIT
                    : this.snapshot.speaking() ? PduTheme.OK
                    : voice.starved() ? PduTheme.CRIT
                    : voice.held() || s.muted() ? PduTheme.WARN
                    : PduTheme.TEXT_DIM,
            this.snapshot.queued() > 0 ? PduTheme.INFO : PduTheme.TEXT_FAINT,
            powered ? PduTheme.TEXT_DIM : PduTheme.CRIT,
        };
        for (int i = 0; i < labels.length; i++) {
            right -= this.chipW(labels[i]);
            this.chip(g, right, y + 9, labels[i], tints[i],
                    i == 0 && (this.snapshot.speaking() || !powered));
            right -= BASE;
        }

        final int railH = this.height0 - HEADER_H - FOOTER_H;
        g.fill(x + RAIL_W, y + HEADER_H, x + RAIL_W + 1, y + HEADER_H + railH, PduTheme.STROKE);
        final int itemH = Math.max(20, Math.min(30, (railH - 2 * BASE) / TABS.length));
        for (int i = 0; i < TABS.length; i++) {
            final int iy = y + HEADER_H + BASE + i * itemH;
            final boolean on = i == this.tab;
            if (on) {
                g.fill(x + 1, iy + 2, x + 3, iy + itemH - 2, PduTheme.ACCENT);
            }

            this.textCentre(g, TABS[i].substring(0, 2), x + 1 + RAIL_W / 2, iy + (itemH - 8) / 2,
                    on ? PduTheme.TEXT : PduTheme.TEXT_FAINT);
            final int pick = i;
            this.hits.add(new Hit(x + 1, iy, RAIL_W, itemH, () -> {
                this.tab = pick;
                this.scroll = 0;
            }));
        }

        final int fy = y + this.height0 - FOOTER_H;
        g.fill(x + 1, fy, x + this.width0 - 1, y + this.height0 - 1, PduTheme.BG_PANEL);
        g.fill(x + 1, fy, x + this.width0 - 1, fy + 1, PduTheme.STROKE);
        final int ax = x + this.width0 - PAD - 60;
        this.button(g, ax, fy + 5, 60, CHIP_ROW, "Silence", PduTheme.WARN, false,
                () -> this.send(PaActionPacket.of(this.snapshot.pos(), "silence")));
        this.button(g, ax - 66, fy + 5, 62, CHIP_ROW, "Test Say", PduTheme.INFO, false, this::testSay);
        this.text(g, this.statusLine(), x + PAD, fy + 8, PduTheme.TEXT_DIM);
    }

    private String statusLine() {
        if (!this.snapshot.powered()) {
            return "No power. Wire the terminal on the back, then it will listen and speak.";
        }
        if (this.snapshot.speaking()) {
            return "Speaking now";
        }
        if (this.snapshot.voice().starved()) {
            return "Holding " + this.snapshot.queued() + ": the buffer cannot cover a whole sentence.";
        }
        if (this.snapshot.voice().held()) {
            return "Muted, holding " + this.snapshot.queued() + ". Unmute and it says them.";
        }
        if (this.snapshot.speakers().isEmpty() && this.snapshot.circuitSize() > 0) {
            return "No speakers wired. Heard within " + this.snapshot.settings().hearingRange()
                    + " blocks of this block only.";
        }
        if (this.snapshot.catalog().isEmpty()) {
            return "Nothing in reach. It reads displays and alarms, not machines directly.";
        }
        if (this.snapshot.subs().isEmpty()) {
            return this.snapshot.importable() > 0
                    ? "Nothing announced yet. " + this.snapshot.importable()
                            + " alarms are already set up and can be adopted."
                    : "Nothing announced yet. Open WATCH and pick something.";
        }
        final int unreadable = this.snapshot.unreadable();
        return unreadable > 0
                ? "Idle. " + this.snapshot.subs().size() + " announced, " + unreadable
                        + " out of reach."
                : "Idle. " + this.snapshot.subs().size() + " announced.";
    }

    private void testSay() {
        final PaSnapshot.Row row = this.selectedRow();
        if (row != null && this.snapshot.subFor(row.key()) != null) {
            this.send(PaActionPacket.of(this.snapshot.pos(), "test", row.key()));
        } else {
            PaVoicePlayer.previewLocally(List.of("attention", "report", "no_faults"),
                    this.snapshot.pos());
        }
    }

    private PaSnapshot.Row selectedRow() {
        return this.selectedKey.isEmpty() ? null : this.snapshot.rowFor(this.selectedKey);
    }

    private void reveal(String key) {
        this.selectedKey = key;
        this.tab = 1;
        String parent = key;
        for (int guard = 0; guard < 8; guard++) {
            final PaSnapshot.Row row = this.snapshot.rowFor(parent);
            if (row == null || row.parentKey().isEmpty()) {
                break;
            }
            parent = row.parentKey();
            this.expanded.add(parent);
        }

        final List<PaSnapshot.Row> rows = this.visibleRows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).key().equals(key)) {
                this.scroll = Math.max(0, i - 2);
                return;
            }
        }
    }

    // left = now, right = what's wrong
    private void drawLive(GuiGraphics g, int cx, int cy, int cw, int ch) {
        final int colW = (cw - GAP) / 2;
        final int rightX = cx + colW + GAP;

        final int airH = Math.max(104, Math.min(132, ch / 3));
        this.drawOnAir(g, cx, cy, colW, airH);
        this.drawOutstanding(g, cx, cy + airH + GAP, colW, ch - airH - GAP);

        final int queueH = Math.max(112, Math.min(150, ch * 2 / 5));
        this.drawNextUp(g, rightX, cy, colW, queueH);
        this.drawAnnounced(g, rightX, cy + queueH + GAP, colW, ch - queueH - GAP);
    }

    private void drawOnAir(GuiGraphics g, int cx, int cy, int cw, int ch) {
        final int[] c = this.card(g, cx, cy, cw, ch, "ON AIR");
        final PaSnapshot.Voice voice = this.snapshot.voice();
        final AlarmSeverity sev = this.snapshot.severity();
        final boolean live = !voice.onAir().isEmpty() && this.snapshot.speaking();

        this.chip(g, c[0] + c[2] - this.chipW(sev.name()), c[1] - 2, sev.name(), tintOf(sev), true);
        this.text(g, this.airHeadline(), c[0], c[1], live ? PduTheme.TEXT : PduTheme.TEXT_DIM);

        final List<String> words = live ? voice.onAir() : this.idleWords();
        final int used = this.sentence(g, c[0], c[1] + 16, c[2], words,
                live ? voice.onAirWord() : -1);

        final int fy = c[1] + 16 + used + 8;
        if (fy + 10 > cy + ch - 3) {
            return;
        }
        if (live) {
            final int at = Math.max(0, voice.onAirWord()) + 1;
            final int left = Math.max(0,
                    (words.size() - at) * com.createstats.pa.Utterance.TICKS_PER_CLIP);
            this.text(g, "word " + at + " of " + words.size()
                            + (left < 20 ? " · last word" : " · " + secs(left) + " to go"),
                    c[0], fy, PduTheme.TEXT_FAINT);
        } else {
            this.text(g, this.snapshot.settings().muted()
                            ? "Muted, so nothing is being said out loud."
                            : "Nothing on air. This is what the selected topic would say.",
                    c[0], fy, PduTheme.TEXT_FAINT);
        }
    }

    private String airHeadline() {
        if (!this.snapshot.powered()) {
            return "Off. No power.";
        }
        final PaSnapshot.Voice voice = this.snapshot.voice();
        if (!voice.onAir().isEmpty() && this.snapshot.speaking()) {
            return "Speaking now";
        }
        if (voice.starved()) {
            return "Not enough power to say the next one";
        }
        if (voice.held()) {
            return "Holding " + this.snapshot.queued()
                    + (this.snapshot.queued() == 1 ? " announcement" : " announcements");
        }
        return this.snapshot.settings().muted() ? "Muted" : "Idle";
    }

    private List<String> idleWords() {
        final PaSnapshot.Row row = this.selectedRow();
        if (row == null || !row.bindable()) {
            return List.of("report", "no_faults");
        }
        return PaConsoleRequests.previewClips(row, this.snapshot.subFor(row.key()));
    }

    private void drawNextUp(GuiGraphics g, int cx, int cy, int cw, int ch) {
        final PaSnapshot.Voice voice = this.snapshot.voice();
        final int[] c = this.card(g, cx, cy, cw, ch,
                voice.queue().isEmpty() ? "NEXT UP" : "NEXT UP · " + voice.queue().size());
        int y = c[1];
        final int maxY = cy + ch - 4;

        if (voice.held() || voice.starved()) {
            final String why = voice.starved()
                    ? "Waiting for FE. A sentence is paid for in full before it starts."
                    : !this.snapshot.powered() ? "No power. Held until the wire is live."
                    : "Muted. Held, not dropped: unmute and it says these.";
            g.fill(c[0], y - 2, c[0] + c[2], y + 20, alpha(PduTheme.WARN, 0.12f));
            this.text(g, this.ellipsise(why, c[2] - 4), c[0] + 2, y + 4, PduTheme.WARN);
            y += 26;
        }

        if (voice.queue().isEmpty()) {
            this.text(g, "Nothing waiting.", c[0], y, PduTheme.TEXT_FAINT);
            y += 13;
            if (voice.onDemand() > 0) {
                this.text(g, voice.onDemand() + " topics answer only when asked.", c[0], y,
                        PduTheme.INFO);
                y += 11;
            }
            if (y + CHIP_ROW <= maxY) {
                this.button(g, c[0], y + 2, Math.min(96, c[2]), CHIP_ROW, "Read report now",
                        PduTheme.INFO, false,
                        () -> this.send(PaActionPacket.of(this.snapshot.pos(), "ask")));
            }
            return;
        }

        for (int i = 0; i < voice.queue().size(); i++) {
            if (y + 20 > maxY) {
                this.text(g, "+" + (voice.queue().size() - i) + " more waiting", c[0], y,
                        PduTheme.TEXT_FAINT);
                return;
            }
            final PaSnapshot.Waiting waiting = voice.queue().get(i);
            if (i % 2 == 0) {
                g.fill(c[0] - 2, y - 2, c[0] + c[2] + 2, y + 18, PduTheme.BG_DEEP);
            }
            this.severityDot(g, c[0], y + 5, waiting.severity());
            this.textRight(g, String.valueOf(i + 1), c[0] + 22, y + 3,
                    i == 0 ? PduTheme.TEXT : PduTheme.TEXT_FAINT);
            final String said = String.join(" ", waiting.words()).replace('_', ' ');
            this.text(g, this.ellipsise(said, c[2] - 66), c[0] + 26, y + 3,
                    i == 0 ? PduTheme.TEXT : PduTheme.TEXT_DIM);

            this.textRight(g, secs(waiting.waitedTicks()), c[0] + c[2], y + 3, PduTheme.TEXT_FAINT);
            y += 20;
        }
    }

    private void drawOutstanding(GuiGraphics g, int cx, int cy, int cw, int ch) {
        final List<PaSnapshot.Sub> tripped = new ArrayList<>();
        for (PaSnapshot.Sub sub : this.snapshot.subs()) {
            if (sub.tripped()) {
                tripped.add(sub);
            }
        }
        final int[] q = this.card(g, cx, cy, cw, ch,
                tripped.isEmpty() ? "OUTSTANDING" : "OUTSTANDING · " + tripped.size());
        if (tripped.isEmpty()) {
            this.text(g, "No faults.", q[0], q[1] + 3, PduTheme.OK);
            return;
        }
        final int maxY = cy + ch - 4;
        for (int i = 0; i < tripped.size(); i++) {
            final int ry = q[1] + i * TOPIC_H;
            if (ry + TOPIC_H > maxY) {
                this.text(g, "+" + (tripped.size() - i) + " more", q[0] + 12, ry + 2,
                        PduTheme.TEXT_FAINT);
                return;
            }
            final PaSnapshot.Sub sub = tripped.get(i);
            final PaSnapshot.Row row = this.snapshot.rowFor(sub.key());
            this.severityDot(g, q[0] + 2, ry + 5, AlarmSeverity.byName(sub.severity()));
            this.text(g, this.ellipsise(row == null ? sub.key() : row.title(), q[2] - 60),
                    q[0] + 12, ry + 2, i == 0 ? PduTheme.TEXT : PduTheme.TEXT_DIM);
            if (row != null) {
                this.text(g, this.ellipsise(row.subtitle(), q[2] - 60), q[0] + 12, ry + 11,
                        PduTheme.TEXT_FAINT);
                this.textRight(g, fmt(row.value()), q[0] + q[2], ry + 6, PduTheme.TEXT);
            }

            this.hits.add(new Hit(q[0], ry, q[2], TOPIC_H, () -> this.reveal(sub.key())));
        }
    }

    private void drawAnnounced(GuiGraphics g, int cx, int cy, int cw, int ch) {
        final List<PaSnapshot.Sub> subs = this.snapshot.subs();
        final int[] gc = this.card(g, cx, cy, cw, ch,
                subs.isEmpty() ? "ANNOUNCED" : "ANNOUNCED · " + subs.size());
        if (subs.isEmpty()) {
            this.text(g, "Nothing announced yet.", gc[0], gc[1], PduTheme.TEXT_FAINT);
            if (this.snapshot.importable() > 0) {
                this.text(g, this.snapshot.importable() + " alarms already exist nearby:",
                        gc[0], gc[1] + 11, PduTheme.TEXT_FAINT);
                this.text(g, "press Adopt in the header.", gc[0], gc[1] + 20, PduTheme.TEXT_FAINT);
            } else {
                this.text(g, "Open WATCH and pick something.", gc[0], gc[1] + 11,
                        PduTheme.TEXT_FAINT);
            }
            return;
        }
        final int rowH = 17;
        final int maxY = cy + ch - 4;
        for (int i = 0; i < subs.size(); i++) {
            final int ey = gc[1] + i * rowH;
            if (ey + rowH > maxY) {
                this.text(g, "+" + (subs.size() - i) + " more", gc[0] + 12, ey + 2,
                        PduTheme.TEXT_FAINT);
                return;
            }
            final PaSnapshot.Sub sub = subs.get(i);
            final PaSnapshot.Row row = this.snapshot.rowFor(sub.key());
            if (i % 2 == 0) {
                g.fill(gc[0] - 2, ey - 1, gc[0] + gc[2] + 2, ey + rowH - 2, PduTheme.BG_DEEP);
            }
            this.severityDot(g, gc[0] + 1, ey + 4, sub.tripped()
                    ? AlarmSeverity.byName(sub.severity()) : AlarmSeverity.NORMAL);

            final String tag = PaConsoleRequests.disciplineTag(sub);
            final int tagW = this.textW(tag) + 4;
            this.text(g, tag, gc[0] + gc[2] - tagW, ey + 3,
                    sub.enabled() ? disciplineTint(sub) : PduTheme.TEXT_FAINT);

            final String name = row == null ? sub.key() : row.title();
            final int valueW = 42;
            this.text(g, this.ellipsise(name, gc[2] - tagW - valueW - 14), gc[0] + 10, ey + 3,
                    !sub.enabled() ? PduTheme.TEXT_FAINT
                            : row == null ? PduTheme.CRIT : PduTheme.TEXT_DIM);
            if (row != null) {
                this.textRight(g, row.has(PaCatalog.FLAG_HAS_VALUE) ? fmt(row.value()) : "--",
                        gc[0] + gc[2] - tagW - 6, ey + 3,
                        sub.enabled() ? PduTheme.TEXT : PduTheme.TEXT_FAINT);
                this.hits.add(new Hit(gc[0], ey - 1, gc[2] - tagW - 2, rowH,
                        () -> this.reveal(sub.key())));
            } else {
                this.textRight(g, "drop", gc[0] + gc[2] - tagW - 6, ey + 3, PduTheme.CRIT);
                this.hits.add(new Hit(gc[0], ey - 1, gc[2] - tagW - 2, rowH,
                        () -> this.send(PaActionPacket.of(this.snapshot.pos(), "unsubscribe",
                                sub.key()))));
            }

            this.hits.add(new Hit(gc[0] + gc[2] - tagW - 2, ey - 1, tagW + 4, rowH,
                    () -> this.send(PaActionPacket.of(this.snapshot.pos(), "enabled", sub.key(),
                            sub.enabled() ? 0.0f : 1.0f))));
        }
    }

    private static int disciplineTint(PaSnapshot.Sub sub) {
        return switch (PaConsoleRequests.disciplineTag(sub)) {
            case "RPT" -> PduTheme.INFO;
            case "ASK" -> PduTheme.TEXT_DIM;
            default -> PduTheme.OK;
        };
    }

    private static String secs(int ticks) {
        if (ticks < 20) {
            return "now";
        }
        final int whole = ticks / 20;
        return whole < 60 ? whole + "s" : (whole / 60) + "m" + (whole % 60) + "s";
    }

    private void drawWatch(GuiGraphics g, int cx, int cy, int cw, int ch) {
        final int detailW = Math.max(196, Math.min(250, cw * 2 / 5));
        final int treeW = cw - detailW - GAP;
        this.drawTree(g, cx, cy, treeW, ch);
        this.drawDetail(g, cx + treeW + GAP, cy, detailW, ch);
    }

    private List<PaSnapshot.Row> visibleRows() {
        final List<PaSnapshot.Row> out = new ArrayList<>();
        final Set<String> shown = new HashSet<>();
        for (PaSnapshot.Row row : this.snapshot.catalog()) {
            if (row.depth() == 0) {
                out.add(row);
                if (this.expanded.contains(row.key())) {
                    shown.add(row.key());
                }
                continue;
            }
            if (!shown.contains(row.parentKey())) {
                continue;
            }
            out.add(row);
            if (this.expanded.contains(row.key())) {
                shown.add(row.key());
            }
        }
        return out;
    }

    private int watchedUnder(PaSnapshot.Row parent) {
        final Set<String> inside = new HashSet<>();
        inside.add(parent.key());
        int count = 0;
        for (PaSnapshot.Row row : this.snapshot.catalog()) {
            if (!inside.contains(row.parentKey())) {
                continue;
            }
            inside.add(row.key());
            if (row.bindable() && this.snapshot.subFor(row.key()) != null) {
                count++;
            }
        }
        return count;
    }

    private void drawTree(GuiGraphics g, int cx, int cy, int cw, int ch) {
        final int[] r = this.card(g, cx, cy, cw, ch, "WHAT IS AROUND IT");
        final List<PaSnapshot.Row> rows = this.visibleRows();
        if (rows.isEmpty()) {
            this.text(g, "No displays or alarms in reach.", r[0], r[1], PduTheme.TEXT_FAINT);
            this.text(g, "This block reads what your screens", r[0], r[1] + 14, PduTheme.TEXT_FAINT);
            this.text(g, "already show, and what your alarms", r[0], r[1] + 23, PduTheme.TEXT_FAINT);
            this.text(g, "already decided. Put one in range,", r[0], r[1] + 32, PduTheme.TEXT_FAINT);
            this.text(g, "or run a cable to one.", r[0], r[1] + 41, PduTheme.TEXT_FAINT);
            return;
        }

        final int maxY = cy + ch - 4;
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, rows.size() - 3)));
        int y = r[1];
        for (int i = this.scroll; i < rows.size(); i++) {
            final PaSnapshot.Row row = rows.get(i);
            final int rowH = switch (row.rowKind()) {
                case TARGET -> TARGET_H;
                case GROUP -> GROUP_H;
                case TOPIC -> TOPIC_H;
            };
            if (y + rowH > maxY) {
                break;
            }
            this.drawTreeRow(g, r[0], y, r[2], rowH, row);
            y += rowH;
        }
        if (this.scroll > 0) {
            this.textRight(g, "▲", r[0] + r[2], r[1] - 10, PduTheme.TEXT_FAINT);
        }
    }

    private void drawTreeRow(GuiGraphics g, int x, int y, int w, int h, PaSnapshot.Row row) {
        final boolean selected = row.key().equals(this.selectedKey);
        final boolean open = this.expanded.contains(row.key());
        final boolean branch = row.rowKind() != PaCatalog.RowKind.TOPIC;
        final int indent = row.depth() * INDENT;
        final PaSnapshot.Sub sub = row.bindable() ? this.snapshot.subFor(row.key()) : null;

        if (selected) {
            g.fill(x - 2, y, x + w + 2, y + h - 1, alpha(PduTheme.ACCENT, 0.16f));
            g.fill(x - 2, y, x - 1, y + h - 1, PduTheme.ACCENT);
        } else if (row.rowKind() == PaCatalog.RowKind.TARGET) {
            g.fill(x - 2, y, x + w + 2, y + h - 1, PduTheme.BG_RAISED);
        }

        final int tx = x + indent;
        if (branch) {
            this.text(g, open ? "▾" : "▸", tx, y + (h - 8) / 2, PduTheme.TEXT_DIM);
        }

        final int labelX = tx + (branch ? 9 : 10);
        final boolean tripped = row.has(PaCatalog.FLAG_TRIPPED);
        if (!branch) {
            final int tint = sub != null ? (tripped ? PduTheme.CRIT : PduTheme.OK)
                    : row.has(PaCatalog.FLAG_ARMED) ? PduTheme.WARN : PduTheme.TEXT_FAINT;
            if (sub != null) {
                g.fill(tx + 1, y + h / 2 - 2, tx + 6, y + h / 2 + 3, tint);
            } else {
                this.outline(g, tx + 1, y + h / 2 - 2, 5, 5, tint);
            }
        } else if (tripped) {
            this.severityDot(g, tx + 10, y + h / 2 - 2, AlarmSeverity.WARNING);
        }

        final String tag = sub == null ? "" : PaConsoleRequests.disciplineTag(sub);
        final int tagW = tag.isEmpty() ? 0 : this.textW(tag) + 6;
        if (tagW > 0) {
            this.text(g, tag, x + w - tagW, y + (h - 8) / 2,
                    sub.enabled() ? disciplineTint(sub) : PduTheme.TEXT_FAINT);
        }

        final int valueW = (row.has(PaCatalog.FLAG_HAS_VALUE) ? 46 : 0) + tagW;
        final int titleW = w - indent - 22 - valueW;
        final int titleTint = selected ? PduTheme.TEXT
                : row.rowKind() == PaCatalog.RowKind.TARGET ? PduTheme.TEXT : PduTheme.TEXT_DIM;
        final int textX = branch && tripped ? labelX + 8 : labelX;

        if (h >= TOPIC_H) {
            this.text(g, this.ellipsise(row.title(), titleW), textX, y + 3, titleTint);
            this.text(g, this.ellipsise(row.subtitle(), titleW), textX, y + 12,
                    sub != null ? PduTheme.INFO : PduTheme.TEXT_FAINT);
        } else {
            this.text(g, this.ellipsise(row.title(), titleW - 40), textX, y + (h - 8) / 2, titleTint);
            this.textRight(g, this.ellipsise(row.subtitle(), 40), x + w - 2, y + (h - 8) / 2,
                    PduTheme.TEXT_FAINT);
        }

        if (row.has(PaCatalog.FLAG_HAS_VALUE)) {
            this.textRight(g, fmt(row.value()), x + w - 2 - tagW, y + (h - 8) / 2,
                    sub == null || sub.enabled() ? PduTheme.TEXT : PduTheme.TEXT_FAINT);
        } else if (row.rowKind() == PaCatalog.RowKind.TARGET) {
            final int watched = this.watchedUnder(row);
            if (watched > 0) {
                this.textRight(g, watched + " on", x + w - 2, y + (h - 8) / 2, PduTheme.OK);
            } else if (row.has(PaCatalog.FLAG_VIA_CABLE)) {
                this.textRight(g, "cable", x + w - 2, y + (h - 8) / 2, PduTheme.INFO);
            }
        }

        this.hits.add(new Hit(x - 2, y, w + 4, h - 1, () -> {
            this.selectedKey = row.key();
            if (branch) {
                if (!this.expanded.remove(row.key())) {
                    this.expanded.add(row.key());
                }
            }
        }));
    }

    private void drawDetail(GuiGraphics g, int dx0, int cy, int dw, int ch) {
        final PaSnapshot.Row row = this.selectedRow();
        if (row == null) {
            final int[] d = this.card(g, dx0, cy, dw, ch, "WHAT TO SAY");
            this.text(g, "Nothing selected.", d[0], d[1], PduTheme.TEXT_FAINT);
            this.text(g, "Open a display on the left and", d[0], d[1] + 16, PduTheme.TEXT_FAINT);
            this.text(g, "pick one of its readings.", d[0], d[1] + 25, PduTheme.TEXT_FAINT);
            return;
        }
        if (!row.bindable()) {
            this.drawBranchDetail(g, dx0, cy, dw, ch, row);
            return;
        }
        this.drawTopicDetail(g, dx0, cy, dw, ch, row);
    }

    private void drawBranchDetail(GuiGraphics g, int dx0, int cy, int dw, int ch,
                                  PaSnapshot.Row row) {
        final int[] d = this.card(g, dx0, cy, dw, ch, "WHAT TO SAY");
        this.text(g, this.ellipsise(row.title(), d[2]), d[0], d[1], PduTheme.TEXT);
        this.text(g, this.ellipsise(row.subtitle(), d[2]), d[0], d[1] + 10, PduTheme.TEXT_FAINT);

        final List<PaSnapshot.Row> topics = this.topicsUnder(row);
        int armed = 0;
        int watched = 0;
        for (PaSnapshot.Row topic : topics) {
            if (topic.has(PaCatalog.FLAG_ARMED)) {
                armed++;
            }
            if (this.snapshot.subFor(topic.key()) != null) {
                watched++;
            }
        }

        int y = d[1] + 26;
        this.text(g, topics.size() + " readings under it", d[0], y, PduTheme.TEXT_DIM);
        y += 11;
        this.text(g, armed + " already have an alarm set", d[0], y,
                armed > 0 ? PduTheme.WARN : PduTheme.TEXT_FAINT);
        y += 11;
        this.text(g, watched + " announced by this block", d[0], y,
                watched > 0 ? PduTheme.OK : PduTheme.TEXT_FAINT);
        y += 18;

        if (armed > watched) {
            this.button(g, d[0], y, d[2], CHIP_ROW, "Adopt the " + (armed - watched) + " alarms set here",
                    PduTheme.OK, true, () -> {
                        for (PaSnapshot.Row topic : topics) {
                            if (topic.has(PaCatalog.FLAG_ARMED)
                                    && this.snapshot.subFor(topic.key()) == null) {
                                this.send(PaActionPacket.of(this.snapshot.pos(), "adopt",
                                        topic.key()));
                            }
                        }
                    });
            y += CHIP_ROW + BASE;
        }
        if (watched < topics.size()) {
            this.button(g, d[0], y, d[2], CHIP_ROW, "Announce all " + topics.size(), PduTheme.INFO,
                    false, () -> {
                        for (PaSnapshot.Row topic : topics) {
                            if (this.snapshot.subFor(topic.key()) == null) {
                                this.send(PaActionPacket.of(this.snapshot.pos(), "subscribe",
                                        topic.key()));
                            }
                        }
                    });
            y += CHIP_ROW + BASE;
        }
        if (watched > 0) {
            this.button(g, d[0], y, d[2], CHIP_ROW, "Stop announcing these", PduTheme.CRIT, false,
                    () -> {
                        for (PaSnapshot.Row topic : topics) {
                            if (this.snapshot.subFor(topic.key()) != null) {
                                this.send(PaActionPacket.of(this.snapshot.pos(), "unsubscribe",
                                        topic.key()));
                            }
                        }
                    });
            y += CHIP_ROW + BASE;
        }

        y += BASE;
        this.text(g, "A voice is serial, so every extra", d[0], y, PduTheme.TEXT_FAINT);
        this.text(g, "topic is time taken from another.", d[0], y + 9, PduTheme.TEXT_FAINT);
    }

    private List<PaSnapshot.Row> topicsUnder(PaSnapshot.Row parent) {
        final Set<String> inside = new HashSet<>();
        inside.add(parent.key());
        final List<PaSnapshot.Row> out = new ArrayList<>();
        for (PaSnapshot.Row row : this.snapshot.catalog()) {
            if (!inside.contains(row.parentKey())) {
                continue;
            }
            inside.add(row.key());
            if (row.bindable()) {
                out.add(row);
            }
        }
        return out;
    }

    private void drawTopicDetail(GuiGraphics g, int dx0, int cy, int dw, int ch,
                                 PaSnapshot.Row row) {
        final PaSnapshot.Sub sub = this.snapshot.subFor(row.key());
        final boolean inherited = row.has(PaCatalog.FLAG_INHERITED);

        final int topH = sub == null ? 96 : (inherited ? 128 : 160);
        final int[] d = this.card(g, dx0, cy, dw, topH, "WHAT TO SAY");
        this.text(g, this.ellipsise(row.title(), d[2] - 34), d[0], d[1], PduTheme.TEXT);
        this.text(g, this.ellipsise(row.subtitle(), d[2] - 34), d[0], d[1] + 10, PduTheme.TEXT_FAINT);
        if (sub != null) {
            final String onOff = sub.enabled() ? "ON" : "OFF";
            final int w = this.chipW(onOff);
            this.chip(g, d[0] + d[2] - w, d[1] - 1, onOff,
                    sub.enabled() ? PduTheme.OK : PduTheme.TEXT_FAINT, sub.enabled());
            this.hits.add(new Hit(d[0] + d[2] - w, d[1] - 1, w, CHIP_H,
                    () -> this.send(PaActionPacket.of(this.snapshot.pos(), "enabled", row.key(),
                            sub.enabled() ? 0.0f : 1.0f))));
        }
        int dy = d[1] + 24;

        if (sub == null) {
            if (row.has(PaCatalog.FLAG_ARMED) && !inherited) {
                this.text(g, "An alarm is already set here.", d[0], dy, PduTheme.WARN);
                dy += 10;
                this.text(g, "Announcing it keeps that limit,", d[0], dy, PduTheme.TEXT_FAINT);
                this.text(g, "and follows it if you change it.", d[0], dy + 9, PduTheme.TEXT_FAINT);
                dy += 22;
                this.button(g, d[0], dy, d[2], CHIP_ROW, "Announce it, keep that limit", PduTheme.OK,
                        true, () -> this.send(PaActionPacket.of(this.snapshot.pos(), "adopt",
                                row.key())));
            } else {
                this.text(g, inherited ? "Voices what it has already decided."
                        : "Not announced.", d[0], dy, PduTheme.TEXT_FAINT);
                dy += 16;
                this.button(g, d[0], dy, d[2], CHIP_ROW,
                        inherited ? "Announce its verdict" : "Announce this", PduTheme.OK, true,
                        () -> this.send(PaActionPacket.of(this.snapshot.pos(), "subscribe",
                                row.key())));
            }
            this.drawPreviewCard(g, dx0, cy + topH + GAP, dw, ch - topH - GAP, row, null);
            return;
        }

        this.text(g, "Say it", d[0], dy, PduTheme.TEXT_FAINT);
        dy += 10;
        final PaSubscription.Discipline[] disciplines = PaSubscription.Discipline.values();
        final String[] discNames = new String[disciplines.length];
        int discSel = 0;
        for (int i = 0; i < disciplines.length; i++) {
            discNames[i] = disciplines[i].displayName();
            if (disciplines[i].getSerializedName().equals(sub.discipline())) {
                discSel = i;
            }
        }
        dy += this.seg(g, d[0], dy, d[2], discNames, discSel, i -> this.send(PaActionPacket.of(
                this.snapshot.pos(), "discipline", row.key(), disciplines[i].getSerializedName())))
                + 3;

        this.text(g, this.ellipsise(switch (disciplines[discSel]) {
            case ON_EVENT -> "When it crosses, and when it clears.";
            case PERIODIC -> "Only in the timed report.";
            case ON_DEMAND -> "Only when you ask for a report.";
        }, d[2]), d[0], dy, PduTheme.TEXT_FAINT);
        dy += 9 + GAP;

        this.text(g, "How much", d[0], dy, PduTheme.TEXT_FAINT);
        dy += 10;
        final PaSubscription.Verbosity[] verbs = PaSubscription.Verbosity.values();
        final String[] verbNames = new String[verbs.length];
        int verbSel = 1;
        for (int i = 0; i < verbs.length; i++) {
            verbNames[i] = verbs[i].displayName();
            if (verbs[i].getSerializedName().equals(sub.verbosity())) {
                verbSel = i;
            }
        }
        dy += this.seg(g, d[0], dy, d[2], verbNames, verbSel, i -> this.send(PaActionPacket.of(
                this.snapshot.pos(), "verbosity", row.key(), verbs[i].getSerializedName()))) + GAP;

        if (!inherited) {
            this.text(g, "Urgency", d[0], dy, PduTheme.TEXT_FAINT);
            dy += 10;
            int px = d[0];
            for (AlarmSeverity sev : new AlarmSeverity[]{AlarmSeverity.NOTICE, AlarmSeverity.WARNING,
                                                         AlarmSeverity.CRITICAL}) {
                final String label = sev.name().charAt(0)
                        + sev.name().substring(1).toLowerCase(Locale.ROOT);
                final boolean on = sev.getSerializedName().equals(sub.severity());
                final int w = this.chip(g, px, dy, label, tintOf(sev), on);
                this.hits.add(new Hit(px, dy, w, CHIP_H, () -> this.send(PaActionPacket.of(
                        this.snapshot.pos(), "severity", row.key(), sev.getSerializedName()))));
                px += w + BASE;
            }
        }

        int nextY = cy + topH + GAP;
        if (!inherited) {
            final int limitH = 74;
            final int[] t = this.card(g, dx0, nextY, dw, limitH, "LIMIT");
            int ty = t[1];
            if (sub.following()) {
                this.text(g, "Set on the display: "
                                + (sub.comparator().equals("below") ? "below " : "above ")
                                + row.type().formatValue(sub.threshold()),
                        t[0], ty, PduTheme.INFO);
                ty += 12;
                this.text(g, "Change it there and this follows.", t[0], ty, PduTheme.TEXT_FAINT);
                ty += 14;
                this.button(g, t[0], ty, t[2], CHIP_ROW, "Use my own limit instead",
                        PduTheme.TEXT_DIM, false,
                        () -> this.send(PaActionPacket.of(this.snapshot.pos(), "follow", row.key(),
                                0.0f)));
            } else {
                final float step = Math.max(1.0f, Math.abs(sub.threshold()) * 0.05f);
                ty += this.stepper(g, t[0], ty, t[2], "Announce above", fmt(sub.threshold()),
                        () -> this.send(PaActionPacket.of(this.snapshot.pos(), "threshold",
                                row.key(), sub.threshold() - step)),
                        () -> this.send(PaActionPacket.of(this.snapshot.pos(), "threshold",
                                row.key(), sub.threshold() + step))) + BASE;
                ty += this.stepper(g, t[0], ty, t[2], "Clear below",
                        fmt(sub.threshold() - sub.deadband()),
                        () -> this.send(PaActionPacket.of(this.snapshot.pos(), "deadband",
                                row.key(), sub.deadband() + step)),
                        () -> this.send(PaActionPacket.of(this.snapshot.pos(), "deadband",
                                row.key(), Math.max(0.0f, sub.deadband() - step)))) + 3;
                this.text(g, row.has(PaCatalog.FLAG_ARMED)
                                ? "The display alerts here too."
                                : "A gap between the two stops it repeating.",
                        t[0], ty, PduTheme.TEXT_FAINT);
            }
            nextY += limitH + GAP;
        }

        this.drawPreviewCard(g, dx0, nextY, dw, ch - (nextY - cy), row, sub);
    }

    private void drawPreviewCard(GuiGraphics g, int x, int y, int w, int h, PaSnapshot.Row row,
                                 PaSnapshot.Sub sub) {
        if (h < HEADING_H + 30) {
            return;
        }
        final int[] sc = this.card(g, x, y, w, h, "IT WILL SAY");
        final List<String> words = PaConsoleRequests.previewClips(row, sub);
        final int used = this.sentence(g, sc[0], sc[1], sc[2], words);
        this.text(g, "about " + Math.max(1, Math.round(words.size() * 13 / 20.0)) + " seconds",
                sc[0], sc[1] + used + 6, PduTheme.TEXT_FAINT);
        if (sub != null && sc[1] + used + 20 + CHIP_ROW <= y + h - 3) {
            this.button(g, sc[0], sc[1] + used + 18, 58, CHIP_ROW, "Remove", PduTheme.CRIT, false,
                    () -> this.send(PaActionPacket.of(this.snapshot.pos(), "unsubscribe",
                            row.key())));
        }
    }

    private void drawVoice(GuiGraphics g, int cx, int cy, int cw, int ch) {
        final PaSettings s = this.snapshot.settings();
        final int colW = (cw - GAP) / 2;

        final int[] a = this.card(g, cx, cy, colW, 112, "SOUND");
        int y = a[1];
        y += this.slider(g, a[0], y, a[2], "Volume", Math.round(s.volume() * 100) + "%", s.volume(),
                v -> this.send(PaActionPacket.of(this.snapshot.pos(), "volume", "", (float) v)));
        y += this.slider(g, a[0], y, a[2], "Pitch", String.format(Locale.ROOT, "%.2f", s.pitch()),
                (s.pitch() - 0.5f) / 1.0f,
                v -> this.send(PaActionPacket.of(this.snapshot.pos(), "pitch", "",
                        (float) (0.5 + v))));
        this.button(g, a[0], y + 4, 66, CHIP_ROW, s.muted() ? "Unmute" : "Mute",
                s.muted() ? PduTheme.WARN : PduTheme.TEXT_DIM, s.muted(),
                () -> this.send(PaActionPacket.of(this.snapshot.pos(), "mute", "",
                        s.muted() ? 0.0f : 1.0f)));

        final int[] b = this.card(g, cx, cy + 112 + GAP, colW, 122, "BULLETIN");
        int by = b[1];

        final boolean on = s.bulletinEnabled();
        this.text(g, on ? "Reads a report on a timer" : "No timed report", b[0], by + 3,
                on ? PduTheme.TEXT_DIM : PduTheme.TEXT_FAINT);
        this.button(g, b[0] + b[2] - 42, by, 42, CHIP_ROW, on ? "On" : "Off",
                on ? PduTheme.OK : PduTheme.TEXT_FAINT, on,
                () -> this.send(PaActionPacket.of(this.snapshot.pos(), "bulletin_on", "",
                        on ? 0.0f : 1.0f)));
        by += CHIP_ROW + BASE;
        by += this.stepper(g, b[0], by, b[2], "Every", (s.bulletinIntervalTicks() / 1200) + " min",
                () -> this.send(PaActionPacket.of(this.snapshot.pos(), "bulletin_every", "",
                        s.bulletinIntervalTicks() - 1200)),
                () -> this.send(PaActionPacket.of(this.snapshot.pos(), "bulletin_every", "",
                        s.bulletinIntervalTicks() + 1200))) + BASE;
        by += this.stepper(g, b[0], by, b[2], "Facts per report", String.valueOf(s.bulletinFacts()),
                () -> this.send(PaActionPacket.of(this.snapshot.pos(), "bulletin_facts", "",
                        s.bulletinFacts() - 1)),
                () -> this.send(PaActionPacket.of(this.snapshot.pos(), "bulletin_facts", "",
                        s.bulletinFacts() + 1))) + BASE;
        this.text(g, "Detail", b[0], by, PduTheme.TEXT_FAINT);
        by += 10;
        final PaSubscription.Verbosity[] verbs = PaSubscription.Verbosity.values();
        final String[] names = new String[verbs.length];
        int sel = 0;
        for (int i = 0; i < verbs.length; i++) {
            names[i] = verbs[i].displayName();
            if (verbs[i] == s.bulletinVerbosity()) {
                sel = i;
            }
        }
        this.seg(g, b[0], by, b[2], names, sel, i -> this.send(PaActionPacket.of(
                this.snapshot.pos(), "bulletin_detail", "", verbs[i].getSerializedName())));

        final int px0 = cx + colW + GAP;
        final int pw = cw - colW - GAP;
        final int[] p = this.card(g, px0, cy, pw, ch, "SAMPLE ANNOUNCEMENT");
        final PaSnapshot.Row row = this.selectedRow();
        int py = p[1];
        if (row == null || !row.bindable()) {
            this.text(g, "Select a reading in WATCH.", p[0], py, PduTheme.TEXT_FAINT);
            return;
        }
        for (PaSubscription.Verbosity v : verbs) {
            this.text(g, "At " + v.displayName() + ":", p[0], py, PduTheme.TEXT_DIM);
            final List<String> words = PaConsoleRequests.previewClips(
                    row, new PaSnapshot.Sub(row.key(), "on_event", v.getSerializedName(),
                            "warning", "above", 0, 0, true, false, false));
            py += 12 + this.sentence(g, p[0], py + 12, p[2], words) + GAP;
        }
        g.fill(p[0], py, p[0] + p[2], py + 1, PduTheme.STROKE);
        this.text(g, "Longer reports crowd out the next one.", p[0], py + 6, PduTheme.TEXT_FAINT);
        this.button(g, p[0], py + 19, 66, CHIP_ROW, "Hear it", PduTheme.OK, false, this::testSay);

        this.button(g, p[0] + 72, py + 19, 88, CHIP_ROW,
                "Ask" + (this.snapshot.voice().onDemand() > 0
                        ? " (" + this.snapshot.voice().onDemand() + ")" : ""),
                this.snapshot.voice().onDemand() > 0 ? PduTheme.INFO : PduTheme.TEXT_DIM, false,
                () -> this.send(PaActionPacket.of(this.snapshot.pos(), "ask")));
    }

    private void drawDevice(GuiGraphics g, int cx, int cy, int cw, int ch) {
        final PaSettings s = this.snapshot.settings();
        final int colW = (cw - GAP) / 2;

        final int[] a = this.card(g, cx, cy, colW, 92, "REACH");
        int y = a[1];
        y += this.stepper(g, a[0], y, a[2], "Reads within", String.valueOf(s.scanRadius()),
                () -> this.send(PaActionPacket.of(this.snapshot.pos(), "scan", "", s.scanRadius() - 4)),
                () -> this.send(PaActionPacket.of(this.snapshot.pos(), "scan", "", s.scanRadius() + 4)))
                + BASE;
        y += this.stepper(g, a[0], y, a[2], "Heard within", String.valueOf(s.hearingRange()),
                () -> this.send(PaActionPacket.of(this.snapshot.pos(), "hearing", "",
                        s.hearingRange() - 4)),
                () -> this.send(PaActionPacket.of(this.snapshot.pos(), "hearing", "",
                        s.hearingRange() + 4))) + 3;
        this.text(g, "Reading and hearing are separate.", a[0], y, PduTheme.TEXT_FAINT);

        this.text(g, "Heard within only covers this block.", a[0], y + 9, PduTheme.TEXT_FAINT);

        final int[] b = this.card(g, cx, cy + 92 + GAP, colW, 58, "WHO HEARS IT");
        final PaSettings.Audience[] auds = PaSettings.Audience.values();
        final String[] names = new String[auds.length];
        int sel = 0;
        for (int i = 0; i < auds.length; i++) {
            names[i] = auds[i].displayName();
            if (auds[i] == s.audience()) {
                sel = i;
            }
        }
        final int by = b[1] + this.seg(g, b[0], b[1], b[2], names, sel,
                i -> this.send(PaActionPacket.of(this.snapshot.pos(), "audience", "",
                        auds[i].getSerializedName()))) + GAP;
        this.text(g, "Nearby means inside the hearing range.", b[0], by, PduTheme.TEXT_FAINT);

        final int wy0 = cy + 92 + GAP + 58 + GAP;
        final int wh = ch - (wy0 - cy);
        if (wh > HEADING_H + 40) {
            this.drawSpeakerRoster(g, cx, wy0, colW, wh);
        }

        final int ix0 = cx + colW + GAP;
        final int iw = cw - colW - GAP;
        final int[] n = this.card(g, ix0, cy, iw, 96, "POWER");
        final boolean powered = this.snapshot.powered();
        this.text(g, powered ? "Running" : "No power", n[0], n[1],
                powered ? PduTheme.OK : PduTheme.CRIT);
        this.textRight(g, this.snapshot.ratedDraw() + " FE/t", n[0] + n[2], n[1], PduTheme.TEXT_DIM);

        final int frac = this.snapshot.energyMax() <= 0 ? 0
                : (this.snapshot.energy() * 100) / this.snapshot.energyMax();
        final int barY = n[1] + 13;
        g.fill(n[0], barY, n[0] + n[2], barY + 8, PduTheme.BG_DEEP);
        g.fill(n[0], barY, n[0] + n[2] * frac / 100, barY + 8,
                frac < 15 ? PduTheme.CRIT : frac < 40 ? PduTheme.WARN : PduTheme.OK);
        this.outline(g, n[0], barY, n[2], 8, PduTheme.STROKE);
        this.text(g, this.snapshot.energy() + " / " + this.snapshot.energyMax() + " FE",
                n[0], barY + 12, PduTheme.TEXT_DIM);
        this.text(g, "Listening is cheap. Talking is charged", n[0], barY + 25, PduTheme.TEXT_FAINT);
        this.text(g, "by the word, so a wordy watch list", n[0], barY + 34, PduTheme.TEXT_FAINT);
        this.text(g, "draws more.", n[0], barY + 43, PduTheme.TEXT_FAINT);

        final int dy0 = cy + 96 + GAP;
        final int[] h = this.card(g, ix0, dy0, iw, 70, "HEALTH");
        int targets = 0;
        for (PaSnapshot.Row row : this.snapshot.catalog()) {
            if (row.depth() == 0) {
                targets++;
            }
        }
        final int unreadable = this.snapshot.unreadable();
        final String[][] tiles = {
            {"Announced", String.valueOf(this.snapshot.subs().size())},
            {"Out of reach", String.valueOf(unreadable)},
            {"Displays & alarms", String.valueOf(targets)},
            {"Not adopted", String.valueOf(this.snapshot.importable())},
        };
        final int[] tints = {PduTheme.OK, unreadable > 0 ? PduTheme.WARN : PduTheme.TEXT_FAINT,
                             PduTheme.TEXT_DIM,
                             this.snapshot.importable() > 0 ? PduTheme.INFO : PduTheme.TEXT_FAINT};
        final int tw = (h[2] - BASE) / 2;
        for (int i = 0; i < tiles.length; i++) {
            final int tx = h[0] + (i % 2) * (tw + BASE);
            final int tyy = h[1] + (i / 2) * 24;
            g.fill(tx, tyy, tx + tw, tyy + 21, PduTheme.BG_DEEP);
            this.outline(g, tx, tyy, tw, 21, PduTheme.STROKE);
            this.text(g, this.ellipsise(tiles[i][0], tw - 8), tx + 4, tyy + 3, PduTheme.TEXT_FAINT);
            this.text(g, tiles[i][1], tx + 4, tyy + 12, tints[i]);
        }

        final int my0 = dy0 + 70 + GAP;
        final int mh = ch - (my0 - cy);
        if (mh < HEADING_H + 30) {
            return;
        }
        final int[] m = this.card(g, ix0, my0, iw, mh, "REACH PLAN");
        final int planH = mh - (HEADING_H + 8) - 14;
        final int ccx = m[0] + m[2] / 2;
        final int ccy = m[1] + 12 + planH / 2;

        final int outer = Math.max(8, Math.min(46, planH / 2 - 4));
        this.ring(g, ccx, ccy, Math.min((int) (outer * 1.7), m[2] / 2 - 4), outer,
                alpha(PduTheme.INFO, 0.85f));
        final int inner = Math.max(5, outer - 10);
        this.ring(g, ccx, ccy, Math.min((int) (inner * 1.7), m[2] / 2 - 6), inner,
                alpha(PduTheme.OK, 0.85f));
        g.fill(ccx - 3, ccy - 3, ccx + 4, ccy + 4, PduTheme.ACCENT);
        this.text(g, "reads " + s.scanRadius(), m[0], m[1], PduTheme.INFO);
        this.textRight(g, "heard " + s.hearingRange(), m[0] + m[2], m[1], PduTheme.OK);
    }

    // a list, because the question is "why can't I hear it in the west wing", not "how many"
    private void drawSpeakerRoster(GuiGraphics g, int cx, int cy, int cw, int ch) {
        final List<PaSnapshot.SpeakerRow> speakers = this.snapshot.speakers();
        final int[] wc = this.card(g, cx, cy, cw, ch,
                speakers.isEmpty() ? "SPEAKERS" : "SPEAKERS · " + speakers.size());
        final boolean wired = this.snapshot.circuitSize() > 0;
        int y = wc[1];

        this.text(g, wired ? "On a circuit of " + this.snapshot.circuitSize() + " blocks"
                : "Not wired to anything", wc[0], y, wired ? PduTheme.OK : PduTheme.WARN);
        y += 12;

        if (speakers.isEmpty()) {
            this.text(g, wired ? "No speakers on it." : "Run a cable from the terminal on the back.",
                    wc[0], y, PduTheme.TEXT_FAINT);
            this.text(g, "Only players within " + this.snapshot.settings().hearingRange(),
                    wc[0], y + 11, PduTheme.TEXT_FAINT);
            this.text(g, "blocks of this block hear anything.", wc[0], y + 20, PduTheme.TEXT_FAINT);
            return;
        }

        final int rowH = 16;
        final int maxY = cy + ch - 4;
        for (int i = 0; i < speakers.size(); i++) {
            if (y + rowH > maxY) {
                this.text(g, "+" + (speakers.size() - i) + " more", wc[0], y, PduTheme.TEXT_FAINT);
                return;
            }
            final PaSnapshot.SpeakerRow speaker = speakers.get(i);
            if (i % 2 == 0) {
                g.fill(wc[0] - 2, y - 1, wc[0] + wc[2] + 2, y + rowH - 2, PduTheme.BG_DEEP);
            }

            final int tint = !speaker.powered() ? PduTheme.CRIT
                    : speaker.spoke() ? PduTheme.OK : PduTheme.TEXT_DIM;
            g.fill(wc[0], y + 4, wc[0] + 5, y + 9, tint);
            final String kind = "megaphone".equals(speaker.kind()) ? "Horn" : "Cabinet";
            this.text(g, kind, wc[0] + 10, y + 3,
                    speaker.powered() ? PduTheme.TEXT_DIM : PduTheme.CRIT);
            this.text(g, speaker.pos().getX() + " " + speaker.pos().getY() + " "
                            + speaker.pos().getZ(), wc[0] + 56, y + 3, PduTheme.TEXT_FAINT);
            this.textRight(g, speaker.powered() ? speaker.range() + "m" : "dark",
                    wc[0] + wc[2], y + 3,
                    speaker.powered() ? PduTheme.TEXT_FAINT : PduTheme.CRIT);
            y += rowH;
        }
    }

    private void ring(GuiGraphics g, int cx, int cy, int rx, int ry, int tint) {
        for (int t = 0; t < 360; t += 3) {
            final double a = Math.toRadians(t);
            final int px = cx + (int) (rx * Math.cos(a));
            final int py = cy + (int) (ry * Math.sin(a));
            g.fill(px, py, px + 2, py + 2, tint);
        }
    }

    private void drawLog(GuiGraphics g, int cx, int cy, int cw, int ch) {
        final int[] c = this.card(g, cx, cy, cw, ch, "WHAT IT HAS SAID");
        final List<String> log = this.snapshot.log();
        if (log.isEmpty()) {
            this.text(g, "Nothing said yet.", c[0], c[1], PduTheme.TEXT_FAINT);
            return;
        }
        final int rowH = TOPIC_H - 2;
        for (int i = 0; i < log.size(); i++) {
            final int ry = c[1] + i * rowH;
            if (ry + rowH > cy + ch - 2) {
                break;
            }
            g.fill(c[0], ry - 1, c[0] + c[2], ry + rowH - 2,
                    i % 2 == 0 ? PduTheme.BG_DEEP : PduTheme.BG_PANEL);

            final String spoken = log.get(i).replace('_', ' ');
            this.text(g, this.ellipsise(spoken, c[2] - 64), c[0] + 4, ry + 4,
                    i == 0 ? PduTheme.TEXT : PduTheme.TEXT_DIM);
            final int bx = c[0] + c[2] - 56;
            final List<String> clips = List.of(log.get(i).split(" "));
            this.button(g, bx, ry, 54, rowH - 3, "say again", PduTheme.INFO, false,
                    () -> PaVoicePlayer.previewLocally(clips, this.snapshot.pos()));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.lastClickX = (int) mouseX;

            for (int i = this.hits.size() - 1; i >= 0; i--) {
                final Hit hit = this.hits.get(i);
                if (hit.contains(mouseX, mouseY)) {
                    hit.action().run();

                    Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                                    1.0f, 0.35f));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (this.tab == 1) {
            this.scroll = Math.max(0, this.scroll - (int) Math.signum(dy));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key >= 49 && key <= 53) {
            this.tab = key - 49;
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
