package com.createstats.client.statspower;

import java.util.ArrayList;
import java.util.List;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.gui.pdu.Ease;
import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.statsmk3.page.SourceProbe;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StatsPowerPage {
    public enum Sub {
        PDU("Distribution", "Power Distribution Unit"),
        BANK("Battery", "FE Battery Controller"),
        FLOW("Flow", "Flow Meter");

        private final String tab;
        private final String device;

        Sub(String tab, String device) {
            this.tab = tab;
            this.device = device;
        }

        public String tab() {
            return this.tab;
        }

        public String device() {
            return this.device;
        }
    }

    private static final int TAB_H = 19;
    private static final int GAP = 6;

    private Sub sub = Sub.PDU;
    private final int[] deviceIndex = new int[Sub.values().length];
    private float underlineX = -1.0f;
    private float underlineW = 0.0f;

    private final PduRect[] tabHit = new PduRect[Sub.values().length];
    private PduRect prevHit;
    private PduRect nextHit;
    private PduRect helpHit;
    private PduRect pageArea;
    private boolean helpOpen;
    private int deviceCount;

    public static boolean available(StatsDisplayPanelBE panel) {
        return PowerReadings.hasAny(panel);
    }

    public Sub sub() {
        return this.sub;
    }

    public void normalise(StatsDisplayPanelBE panel) {
        if (!unitsFor(panel, this.sub).isEmpty()) {
            return;
        }
        for (final Sub candidate : Sub.values()) {
            if (!unitsFor(panel, candidate).isEmpty()) {
                this.sub = candidate;
                return;
            }
        }
    }

    public void render(GuiGraphics g, Font font, StatsDisplayPanelBE panel, int x, int y, int w,
                       int h, int mouseX, int mouseY, float phase, float partialTick) {
        final PowerHover hover = new PowerHover(mouseX, mouseY);
        final boolean reduceMotion = Ease.reduceMotion();
        this.pageArea = new PduRect(x, y, w, h);

        final List<SourceProbe.Unit> units = unitsFor(panel, this.sub);
        this.deviceCount = units.size();
        final int index = clampIndex(this.sub, units.size());

        header(g, font, panel, new PduRect(x, y, w, TAB_H), units, index, hover, partialTick);

        final PduRect body = new PduRect(x, y + TAB_H + GAP, w, Math.max(0, h - TAB_H - GAP));

        final PowerHover bodyHover = this.helpOpen ? new PowerHover(-9999, -9999) : hover;
        if (units.isEmpty()) {
            empty(g, font, body, this.sub, panel);
        } else {
            final SourceProbe.Unit unit = units.get(index);
            switch (this.sub) {
                case PDU -> PowerPduView.render(g, font, body, PowerReadings.readPdu(unit),
                        bodyHover, phase, reduceMotion);
                case BANK -> PowerBatteryView.render(g, font, body, PowerReadings.readBank(unit),
                        bodyHover, phase, reduceMotion);
                case FLOW -> PowerFlowView.render(g, font, body, PowerReadings.readMeter(unit),
                        bodyHover, phase, reduceMotion);
            }
        }
        if (this.helpOpen) {
            PowerLegend.draw(g, font, body, this.sub, phase);
        }

        if (hover.has()) {
            final List<Component> lines = new ArrayList<>(hover.lines().size());
            for (int i = 0; i < hover.lines().size(); i++) {
                final String text = hover.lines().get(i);
                lines.add(Component.literal(i == 0 ? text : ("§7" + text)));
            }
            g.pose().pushPose();
            g.pose().translate(0.0f, 0.0f, 400.0f);
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    private void header(GuiGraphics g, Font font, StatsDisplayPanelBE panel, PduRect r,
                        List<SourceProbe.Unit> units, int index, PowerHover hover,
                        float partialTick) {
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), PduTheme.STROKE);

        int tx = r.x();
        int selX = tx;
        int selW = 0;
        for (final Sub candidate : Sub.values()) {
            final int count = unitsFor(panel, candidate).size();
            final String label = candidate.tab();
            final int tw = 20 + font.width(label) + (count > 0 ? 16 : 0);
            final PduRect tab = new PduRect(tx, r.y(), tw, r.h() - 1);
            this.tabHit[candidate.ordinal()] = tab;

            final boolean active = candidate == this.sub;
            final boolean hot = hover.over(tab);
            if (active) {
                g.fill(tab.x(), tab.y(), tab.right(), tab.bottom(), PduTheme.BG_PANEL);
            } else if (hot) {
                g.fill(tab.x(), tab.y() + 2, tab.right(), tab.bottom(),
                        PowerArt.alpha(PduTheme.BG_RAISED, 0xAA));
            }
            final int tint = active ? PduTheme.TEXT : (count > 0 ? PduTheme.TEXT_DIM : PduTheme.TEXT_FAINT);
            glyph(g, candidate, tab.x() + 5, tab.y() + 5,
                    active ? PduTheme.ACCENT : (count > 0 ? PduTheme.TEXT_DIM : PduTheme.TEXT_FAINT));
            g.drawString(font, label, tab.x() + 18, tab.y() + 6, tint, false);
            if (count > 0) {
                final String badge = String.valueOf(count);
                final int bx = tab.right() - 14;
                g.fill(bx, tab.y() + 5, bx + 11, tab.y() + 14,
                        PowerArt.alpha(active ? PduTheme.ACCENT : PduTheme.STROKE, active ? 0x44 : 0xEE));
                g.drawString(font, badge, bx + 6 - font.width(badge) / 2, tab.y() + 6,
                        active ? PduTheme.ACCENT : PduTheme.TEXT_DIM, false);
            }
            if (active) {
                selX = tab.x();
                selW = tab.w();
            }
            hover.tip(tab,
                    candidate.device(),
                    count == 0 ? "None linked to this panel"
                            : (count + (count == 1 ? " linked" : " linked") + " — click to open"),
                    subBlurb(candidate));
            tx += tw + 2;
        }

        this.helpHit = new PduRect(tx + 2, r.y() + 3, 13, r.h() - 7);
        final boolean helpHot = hover.over(this.helpHit) || this.helpOpen;
        g.fill(this.helpHit.x(), this.helpHit.y(), this.helpHit.right(), this.helpHit.bottom(),
                PowerArt.alpha(this.helpOpen ? PduTheme.ACCENT : PduTheme.BG_PANEL,
                        this.helpOpen ? 0x33 : 0xEE));
        PduTheme.outline(g, this.helpHit.x(), this.helpHit.y(), this.helpHit.w(), this.helpHit.h(),
                helpHot ? PduTheme.ACCENT : PduTheme.STROKE);
        g.drawString(font, "?", this.helpHit.centerX() - font.width("?") / 2, this.helpHit.y() + 2,
                helpHot ? PduTheme.ACCENT : PduTheme.TEXT_DIM, false);
        hover.tip(this.helpHit, this.helpOpen ? "Close the key" : "What every symbol here means",
                "Breaker states, the load ramp, the hatching and the dashed references");
        tx += 17;

        if (this.underlineX < 0.0f) {
            this.underlineX = selX;
            this.underlineW = selW;
        }
        this.underlineX = Ease.ease(this.underlineX, selX, 0.35f, partialTick);
        this.underlineW = Ease.ease(this.underlineW, selW, 0.35f, partialTick);
        g.fill(Math.round(this.underlineX), r.bottom() - 2,
                Math.round(this.underlineX + this.underlineW), r.bottom(), PduTheme.ACCENT);

        this.prevHit = null;
        this.nextHit = null;
        if (units.isEmpty()) {
            final String hint = "nothing linked";
            g.drawString(font, hint, r.right() - font.width(hint), r.y() + 6, PduTheme.TEXT_FAINT, false);
            return;
        }
        final SourceProbe.Unit unit = units.get(index);
        final String name = unit.label();
        final String counter = units.size() > 1 ? ((index + 1) + "/" + units.size()) : "";
        final int arrowW = units.size() > 1 ? 13 : 0;
        final int nameW = Math.min(font.width(name) + 8, Math.max(40, r.w() / 3));
        final int totalW = nameW + arrowW * 2 + (counter.isEmpty() ? 0 : font.width(counter) + 6);
        int sx = r.right() - totalW;
        if (sx < tx + 4) {
            sx = tx + 4;
        }

        if (units.size() > 1) {
            this.prevHit = new PduRect(sx, r.y() + 2, arrowW, r.h() - 4);
            drawArrow(g, font, this.prevHit, "‹", hover);
            sx += arrowW;
        }
        g.drawString(font, fit(font, name, nameW - 4), sx + 2, r.y() + 6, PduTheme.TEXT, false);
        sx += nameW;
        if (!counter.isEmpty()) {
            g.drawString(font, counter, sx, r.y() + 6, PduTheme.TEXT_FAINT, false);
            sx += font.width(counter) + 6;
        }
        if (units.size() > 1) {
            this.nextHit = new PduRect(sx, r.y() + 2, arrowW, r.h() - 4);
            drawArrow(g, font, this.nextHit, "›", hover);
        }
        if (unit.config() != null && unit.config().getSourcePos() != null) {
            hover.tip(r.right() - totalW, r.y(), totalW, r.h(),
                    unit.label(),
                    unit.config().getSourceType() + " at " + unit.config().getSourcePos().toShortString(),
                    units.size() > 1 ? "Scroll here to step between linked devices" : null);
        }
    }

    private void drawArrow(GuiGraphics g, Font font, PduRect r, String glyph, PowerHover hover) {
        final boolean hot = hover.over(r);
        g.fill(r.x(), r.y(), r.right(), r.bottom(),
                PowerArt.alpha(hot ? PduTheme.BG_RAISED : PduTheme.BG_PANEL, 0xEE));
        PduTheme.outline(g, r.x(), r.y(), r.w(), r.h(), hot ? PduTheme.STROKE_HOT : PduTheme.STROKE);
        g.drawString(font, glyph, r.centerX() - font.width(glyph) / 2, r.y() + 4,
                hot ? PduTheme.TEXT : PduTheme.TEXT_DIM, false);
    }

    private static void glyph(GuiGraphics g, Sub sub, int x, int y, int tint) {
        switch (sub) {
            case PDU -> {
                g.fill(x, y, x + 1, y + 9, tint);
                for (int i = 0; i < 3; i++) {
                    final int ly = y + 1 + i * 3;
                    g.fill(x + 1, ly, x + 8, ly + 1, tint);
                }
            }
            case BANK -> {
                g.fill(x + 1, y + 1, x + 8, y + 9, PowerArt.alpha(tint, 0x66));
                g.fill(x + 3, y, x + 6, y + 1, tint);
                g.fill(x + 2, y + 5, x + 7, y + 8, tint);
            }
            case FLOW -> {
                g.fill(x, y + 2, x + 9, y + 3, PowerArt.alpha(tint, 0x99));
                g.fill(x, y + 7, x + 9, y + 8, PowerArt.alpha(tint, 0x99));
                for (int i = 0; i < 4; i++) {
                    g.fill(x + 1 + i * 2, y + 4 + (i % 2), x + 3 + i * 2, y + 5 + (i % 2), tint);
                }
            }
        }
    }

    private static String subBlurb(Sub sub) {
        return switch (sub) {
            case PDU -> "One bus, nine breakers, and the policy dividing them";
            case BANK -> "A pooled reserve, three feeds in, three metered circuits out";
            case FLOW -> "One pipe segment, its medium, and everything that has crossed it";
        };
    }

    private static void empty(GuiGraphics g, Font font, PduRect area, Sub sub,
                              StatsDisplayPanelBE panel) {
        final int cardW = Math.min(area.w(), 320);
        final int cardH = Math.min(area.h(), 132);
        final PduRect card = new PduRect(area.centerX() - cardW / 2,
                area.y() + Math.max(0, (area.h() - cardH) / 2), cardW, cardH);
        final PduRect inner = PowerArt.card(g, card);

        int y = inner.y() + 2;
        glyph(g, sub, inner.x(), y, PduTheme.ACCENT);
        g.drawString(font, "No " + sub.device() + " linked", inner.x() + 14, y + 1,
                PduTheme.TEXT, false);
        y += 15;
        g.fill(inner.x(), y, inner.right(), y + 1, PowerArt.alpha(PduTheme.STROKE, 0x99));
        y += 6;

        for (final String step : new String[]{
                "1. Place a Display Link facing this panel",
                "2. Point it at the " + sub.device().toLowerCase(java.util.Locale.ROOT),
                "3. Choose Stats Link as the source mode"}) {
            g.drawString(font, step, inner.x(), y, PduTheme.TEXT_DIM, false);
            y += 11;
        }
        y += 4;
        g.drawString(font, "Once linked, this page shows:", inner.x(), y, PduTheme.TEXT_FAINT, false);
        y += 11;
        for (final String line : promise(sub)) {
            if (y + 9 > inner.bottom()) {
                break;
            }
            g.fill(inner.x() + 1, y + 3, inner.x() + 3, y + 5, PduTheme.ACCENT);
            g.drawString(font, line, inner.x() + 7, y, PduTheme.TEXT_DIM, false);
            y += 10;
        }

        final int linked = PowerReadings.pduUnits(panel).size()
                + PowerReadings.bankUnits(panel).size()
                + PowerReadings.meterUnits(panel).size();
        if (linked > 0 && card.bottom() + 12 <= area.bottom()) {
            final String note = linked + " other power device"
                    + (linked == 1 ? "" : "s") + " linked — see the other tabs";
            g.drawString(font, note, area.centerX() - font.width(note) / 2, card.bottom() + 4,
                    PduTheme.TEXT_FAINT, false);
        }
    }

    private static String[] promise(Sub sub) {
        return switch (sub) {
            case PDU -> new String[]{
                    "A single-line diagram of all nine circuits",
                    "Demand over served, so a shortage is a shape",
                    "Every breaker's load against its own rating",
                    "The live position of the load shedding line"};
            case BANK -> new String[]{
                    "Charge and discharge either side of zero",
                    "State of charge with every output's floor on it",
                    "Why each circuit is doing what it is doing",
                    "Time to empty, or time to full"};
            case FLOW -> new String[]{
                    "The medium, moving, in cross-section",
                    "Rate, direction and a running totaliser",
                    "The window's spread and distribution"};
        };
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        if (this.helpHit != null && this.helpHit.contains(mx, my)) {
            this.helpOpen = !this.helpOpen;
            return true;
        }
        for (final Sub candidate : Sub.values()) {
            final PduRect hit = this.tabHit[candidate.ordinal()];
            if (hit != null && hit.contains(mx, my)) {
                this.sub = candidate;
                this.helpOpen = false;
                return true;
            }
        }

        if (this.helpOpen && this.pageArea != null && this.pageArea.contains(mx, my)) {
            this.helpOpen = false;
            return true;
        }
        if (this.prevHit != null && this.prevHit.contains(mx, my)) {
            step(-1);
            return true;
        }
        if (this.nextHit != null && this.nextHit.contains(mx, my)) {
            step(1);
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (scrollY == 0.0 || this.deviceCount <= 1) {
            return false;
        }

        final PduRect first = this.tabHit[0];
        if (first == null || my < first.y() - 2 || my > first.bottom() + 2) {
            return false;
        }
        step(scrollY > 0.0 ? -1 : 1);
        return true;
    }

    private void step(int delta) {
        if (this.deviceCount <= 0) {
            return;
        }
        final int i = this.sub.ordinal();
        this.deviceIndex[i] = Math.floorMod(this.deviceIndex[i] + delta, this.deviceCount);
    }

    private int clampIndex(Sub sub, int size) {
        if (size <= 0) {
            this.deviceIndex[sub.ordinal()] = 0;
            return 0;
        }
        final int clamped = Math.floorMod(this.deviceIndex[sub.ordinal()], size);
        this.deviceIndex[sub.ordinal()] = clamped;
        return clamped;
    }

    private static List<SourceProbe.Unit> unitsFor(StatsDisplayPanelBE panel, Sub sub) {
        return switch (sub) {
            case PDU -> PowerReadings.pduUnits(panel);
            case BANK -> PowerReadings.bankUnits(panel);
            case FLOW -> PowerReadings.meterUnits(panel);
        };
    }

    private static String fit(Font font, String text, int max) {
        if (font.width(text) <= max) {
            return text;
        }
        String cut = text;
        while (cut.length() > 3 && font.width(cut + "…") > max) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
