package com.createstats.statsmk3.view;

import com.createstats.statsmk3.ui.Button;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Toggle;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.web.LocalWebServer;
import com.createstats.web.WebFeed;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WebView extends StudioView {
    private static final float ROW_H = 22.0f;

    private Rect switchArea = Rect.EMPTY;
    private Rect addressArea = Rect.EMPTY;
    private Rect factsArea = Rect.EMPTY;

    private String failure = "";

    public WebView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "web";
    }

    @Override
    public String label() {
        return "Web Interface";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.arc(c, cx, cy, 6.5f, 1.2f, 0.0f, (float) (Math.PI * 2.0), color);
        Shapes.rect(c, cx - 6.5f, cy - 0.5f, 13.0f, 1.0f, color);
        Shapes.arc(c, cx, cy, 3.4f, 1.1f, (float) (-Math.PI * 0.5), (float) Math.PI, color);
        Shapes.arc(c, cx, cy, 3.4f, 1.1f, (float) (Math.PI * 0.5), (float) Math.PI, color);
    }

    @Override
    public boolean usesDock() {
        return true;
    }

    @Override
    protected void build() {
        final Rect body = this.area.inset(0.0f, 46.0f, 0.0f, 0.0f);

        this.switchArea = body.top(56.0f);
        this.addressArea = new Rect(body.x, this.switchArea.bottom() + Tokens.S4, body.w, 74.0f);
        this.factsArea = new Rect(body.x, this.addressArea.bottom() + Tokens.S4, body.w,
                Math.max(0.0f, body.bottom() - this.addressArea.bottom() - Tokens.S4));

        final Toggle power = new Toggle(
                () -> LocalWebServer.get().isRunning(),
                on -> {
                    if (on) {
                        if (LocalWebServer.get().start()) {
                            this.failure = "";
                            WebFeed.bind(this.ctx.panel, panelName());
                        } else {
                            this.failure =
                                    "Could not open a port. Another program may be using them.";
                        }
                    } else {
                        LocalWebServer.get().stop();
                        this.failure = "";
                    }
                });
        power.setBounds(new Rect(this.switchArea.right() - Toggle.WIDTH,
                this.switchArea.y + 18.0f, Toggle.WIDTH, Toggle.HEIGHT));
        this.widgets.add(power);

        final Button copy = new Button("Copy address", () -> {
            final String url = LocalWebServer.get().url();
            if (!url.isEmpty()) {
                net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(url);
            }
        }).style(Button.Style.SECONDARY);
        copy.setBounds(new Rect(this.addressArea.right() - 96.0f,
                this.addressArea.y + 40.0f, 96.0f, 22.0f));
        this.widgets.add(copy);
    }

    @Override
    public void update(float dt) {
        super.update(dt);

        if (this.widgets.size() > 1) {
            this.widgets.get(1).setEnabled(LocalWebServer.get().isRunning());
        }
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final LocalWebServer server = LocalWebServer.get();
        final boolean on = server.isRunning();

        c.text("Web Interface", this.area.x, this.area.y, Tokens.INK, Text.SUB);
        c.text("Read this panel in a browser on this computer.",
                this.area.x, this.area.y + 20.0f, Tokens.INK_3, Text.META);

        drawSwitch(c, on);
        drawAddress(c, server, on);
        drawFacts(c);
    }

    private void drawSwitch(PanelSurface c, boolean on) {
        final Rect a = this.switchArea;
        if (a.isEmpty()) {
            return;
        }
        c.card(a, Tokens.R_CARD, Tokens.RAISED, on ? Tokens.alpha(Tokens.GOOD, 0.42f) : Tokens.LINE);

        final float x = a.x + Tokens.S3;
        c.text("Serve to this computer", x, a.y + 12.0f, Tokens.INK, Text.META);
        c.text(on ? "Running. Only this computer can reach it."
                        : "Off. Nothing is listening.",
                x, a.y + 27.0f, on ? Tokens.GOOD : Tokens.INK_3, Text.MICRO);

        if (!this.failure.isEmpty()) {
            c.text(this.failure, x, a.y + 40.0f, Tokens.CRITICAL, Text.MICRO);
        }
    }

    private void drawAddress(PanelSurface c, LocalWebServer server, boolean on) {
        final Rect a = this.addressArea;
        if (a.isEmpty()) {
            return;
        }
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track("Address"), a.x + Tokens.S3, a.y + 10.0f, Tokens.INK_3, Text.MICRO);

        if (!on) {
            c.text("Switch the interface on to get a web address.",
                    a.x + Tokens.S3, a.y + 28.0f, Tokens.INK_3, Text.META);
            return;
        }

        final String url = server.url();
        final float maxW = a.w - Tokens.S3 * 2.0f - 104.0f;
        c.text(Text.truncate(c, url, maxW, Text.META), a.x + Tokens.S3, a.y + 26.0f,
                Tokens.INK, Text.META);

        final String served = WebFeed.label();
        c.text(served.isEmpty()
                        ? "New address every time it starts. The old one stops working."
                        : "Serving " + served + ". New address every time it starts.",
                a.x + Tokens.S3, a.y + 45.0f, Tokens.INK_3, Text.MICRO);
    }

    private void drawFacts(PanelSurface c) {
        final Rect a = this.factsArea;
        if (a.isEmpty() || a.h < 40.0f) {
            return;
        }
        c.text(Text.track("What this does and does not do"), a.x, a.y, Tokens.INK_3, Text.MICRO);

        float y = a.y + 18.0f;
        y = fact(c, a, y, true, "Your computer only",
                "Bound to 127.0.0.1. Nothing on your network or the internet can reach it.");
        y = fact(c, a, y, true, "Read only",
                "It answers GET and nothing else. No page can change your world.");
        y = fact(c, a, y, true, "One time address",
                "A random token, made on start, never saved to disk, gone on stop.");
        fact(c, a, y, true, "No other page can read it",
                "Requests from other websites are refused, so a tab you have open cannot spy.");
    }

    private String panelName() {
        if (this.ctx.panel == null) {
            return "";
        }
        final net.minecraft.core.BlockPos pos = this.ctx.panel.getBlockPos();
        return "the panel at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private float fact(PanelSurface c, Rect a, float y, boolean good, String name, String detail) {
        if (y + ROW_H > a.bottom()) {
            return y;
        }
        final int color = good ? Tokens.GOOD : Tokens.CAUTION;
        Shapes.circle(c, a.x + 4.0f, y + 4.5f, 2.5f, color);
        c.text(name, a.x + 14.0f, y, Tokens.INK_2, Text.META);
        c.text(detail, a.x + 14.0f, y + 12.0f, Tokens.INK_3, Text.MICRO);
        return y + ROW_H + 12.0f;
    }
}
