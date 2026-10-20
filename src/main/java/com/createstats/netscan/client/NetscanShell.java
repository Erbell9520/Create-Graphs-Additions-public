package com.createstats.netscan.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.netscan.client.view.InspectorView;
import com.createstats.netscan.client.view.LedgerView;
import com.createstats.netscan.client.view.MapView;
import com.createstats.netscan.client.view.NetworkView;
import com.createstats.netscan.client.view.ScanView;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class NetscanShell {
    private static final int DIM = 0xC0070A0E;
    private static final float RAIL_W = 104.0f;
    private static final float RAIL_ITEM_H = 34.0f;
    private static final float HEADER_H = 46.0f;
    private static final float MARGIN = 26.0f;
    private static final float MAX_W = 720.0f;
    private static final float MAX_H = 460.0f;

    private final ScanContext ctx;
    private final List<ScanView> views = new ArrayList<>();
    private final Spring railIndicator = new Spring(0.0f, 120.0f, 21.0f, 1.0f);

    private Rect card = Rect.EMPTY;
    private Rect rail = Rect.EMPTY;
    private Rect header = Rect.EMPTY;
    private Rect bodyArea = Rect.EMPTY;
    private int activeIndex;
    private float width;
    private float height;

    public NetscanShell(ScanContext ctx) {
        this.ctx = ctx;
        this.views.add(new MapView(ctx));
        this.views.add(new InspectorView(ctx));
        this.views.add(new LedgerView(ctx));
        this.views.add(new NetworkView(ctx));
    }

    public ScanContext context() {
        return this.ctx;
    }

    public void layout(float width, float height) {
        this.width = width;
        this.height = height;
        final float w = Math.min(MAX_W, width - MARGIN * 2.0f);
        final float h = Math.min(MAX_H, height - MARGIN * 2.0f);
        this.card = new Rect((width - w) * 0.5f, (height - h) * 0.5f, w, h);
        this.rail = new Rect(this.card.x, this.card.y, RAIL_W, this.card.h);
        this.header = new Rect(this.card.x + RAIL_W, this.card.y, this.card.w - RAIL_W, HEADER_H);
        this.bodyArea = new Rect(this.card.x + RAIL_W + Tokens.S4, this.card.y + HEADER_H,
                this.card.w - RAIL_W - Tokens.S4 * 2.0f, this.card.h - HEADER_H - Tokens.S4);
        for (final ScanView view : this.views) {
            view.layout(this.bodyArea);
        }
        this.railIndicator.jump(this.activeIndex);
    }

    public void update(float dt) {
        this.railIndicator.set(this.activeIndex);
        this.railIndicator.step(dt);
        active().update(dt);
    }

    public void hover(double mx, double my) {
        active().hover(mx, my);
    }

    private ScanView active() {
        return this.views.get(Math.max(0, Math.min(this.activeIndex, this.views.size() - 1)));
    }

    public void draw(PanelSurface c, double mouseX, double mouseY) {
        Shapes.rect(c, new Rect(0.0f, 0.0f, this.width, this.height), DIM);
        c.shadow(this.card, Tokens.R_PANEL, 18.0f, 0x60000000);
        c.card(this.card, Tokens.R_PANEL, Tokens.CANVAS, Tokens.LINE);

        drawRail(c, mouseX, mouseY);
        drawHeader(c);

        c.clip(this.bodyArea.inset(-Tokens.S2));
        active().draw(c);
        c.unclip();
    }

    private void drawRail(PanelSurface c, double mouseX, double mouseY) {
        c.hairlineV(this.rail.right(), this.rail.y + 1.0f, this.rail.h - 2.0f, Tokens.LINE);
        c.text(Text.track("Survey"), this.rail.x + Tokens.S3, this.rail.y + Tokens.S3,
                Tokens.INK_3, Text.MICRO);

        final float top = this.rail.y + HEADER_H;
        Shapes.roundedRect(c, new Rect(this.rail.x + Tokens.S2,
                        top + this.railIndicator.value() * RAIL_ITEM_H + 4.0f, 2.0f,
                        RAIL_ITEM_H - 8.0f), 1.0f, Tokens.ACCENT);

        for (int i = 0; i < this.views.size(); i++) {
            final ScanView view = this.views.get(i);
            final Rect item = railItem(i);
            final boolean isActive = i == this.activeIndex;
            final boolean hovered = item.contains(mouseX, mouseY);
            if (isActive || hovered) {
                c.roundedRect(item, Tokens.R_CARD * 0.85f,
                        Tokens.alpha(Tokens.RAISED, isActive ? 1.0f : 0.6f));
            }
            final int color = isActive ? Tokens.INK : hovered ? Tokens.INK_2 : Tokens.INK_3;
            view.drawIcon(c, item.x + 16.0f, item.centerY(), color);
            c.textV(view.label(), item.x + 30.0f, item.centerY(), color, Text.META);
        }
    }

    private Rect railItem(int index) {
        return new Rect(this.rail.x + Tokens.S2, this.rail.y + HEADER_H + index * RAIL_ITEM_H,
                this.rail.w - Tokens.S2 * 2.0f, RAIL_ITEM_H);
    }

    private void drawHeader(PanelSurface c) {
        final ScanGraph g = this.ctx.graph;
        c.text("Topology Scanner", this.header.x, this.header.y + 10.0f, Tokens.INK, Text.SUB);

        final String grade = this.ctx.tier.name().charAt(0)
                + this.ctx.tier.name().substring(1).toLowerCase(Locale.ROOT);
        final String form = this.ctx.form.isInline() ? "inline" : "clamp-on";
        c.text(grade + " · " + form + " · " + this.ctx.pos.toShortString(),
                this.header.x, this.header.y + 28.0f, Tokens.INK_3, Text.MICRO);

        final String status = statusLine(g);
        c.textRight(status, this.header.right() - Tokens.S4, this.header.y + 12.0f,
                statusColor(g), Text.META);
        if (!g.isEmpty()) {
            c.textRight(g.size() + " of " + this.ctx.tier.elementBudget() + " elements",
                    this.header.right() - Tokens.S4, this.header.y + 28.0f, Tokens.INK_3, Text.MICRO);
        }
        c.hairlineH(this.header.x, this.header.bottom(), this.header.w - Tokens.S2, Tokens.LINE);

        if (this.ctx.sweeping) {
            final float frac = Math.min(1.0f,
                    this.ctx.sweepCount / (float) Math.max(1, this.ctx.tier.elementBudget()));
            Shapes.rect(c, this.header.x, this.header.bottom() - 1.0f,
                    (this.header.w - Tokens.S2) * frac, 2.0f, Tokens.ACCENT);
        }
    }

    private String statusLine(ScanGraph g) {
        if (!this.ctx.powered) {
            return this.ctx.form.isInline() ? "No rotation" : "No power";
        }
        if (this.ctx.seedMissing) {
            return "Not clamped";
        }
        if (this.ctx.sweeping) {
            return "Sweeping " + this.ctx.sweepCount;
        }
        if (g.isEmpty()) {
            return "No network";
        }
        if (g.isOverStressed()) {
            return "OVERSTRESSED";
        }
        return String.format(Locale.ROOT, "%.0f%% of capacity", g.loadFraction() * 100.0f);
    }

    private int statusColor(ScanGraph g) {
        if (!this.ctx.powered || this.ctx.seedMissing) {
            return Tokens.CAUTION;
        }
        if (g.isEmpty()) {
            return Tokens.INK_3;
        }
        return g.isOverStressed() ? Tokens.CRITICAL : Tokens.statusFor(g.loadFraction());
    }

    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < this.views.size(); i++) {
            if (railItem(i).contains(mx, my)) {
                if (i != this.activeIndex) {
                    this.activeIndex = i;
                    active().onEnter();
                }
                return true;
            }
        }
        return active().mouseClicked(mx, my, button);
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return active().mouseReleased(mx, my, button);
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return active().mouseDragged(mx, my, button, dx, dy);
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return active().mouseScrolled(mx, my, amount);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_1
                && keyCode < org.lwjgl.glfw.GLFW.GLFW_KEY_1 + this.views.size()) {
            this.activeIndex = keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
            active().onEnter();
            return true;
        }
        return active().keyPressed(keyCode, scanCode, modifiers);
    }
}
