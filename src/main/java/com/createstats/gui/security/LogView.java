package com.createstats.gui.security;

import com.createstats.network.SecuritySyncPacket;
import com.createstats.security.SecurityLog;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

// "2 < 5" not "denied". otherwise a locked door looks broken
@OnlyIn(Dist.CLIENT)
public final class LogView extends SecurityView {
    private final Scroll scroll = new Scroll();
    private boolean deniedOnly;

    public LogView(SecurityContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "log";
    }

    @Override
    public String label() {
        return "Log";
    }

    @Override
    public String group() {
        return "policy";
    }

    @Override
    public void drawIcon(PanelSurface s, float cx, float cy, int color) {
        SecurityIcons.clock(s, cx, cy, color);
    }

    @Override
    public void draw(PanelSurface s, float mx, float my) {
        float y = this.area.y;
        s.text("Log", this.area.x, y, SecurityTheme.TEXT, Text.SUB);

        final float bw = 62.0f;
        final Rect denied = new Rect(this.area.right() - bw, y, bw, SecurityTheme.BTN_H);
        SecurityTheme.button(s, denied, "Denied", this.deniedOnly, hot(denied, mx, my), true);
        hit(denied, () -> this.deniedOnly = true);
        final Rect all = new Rect(denied.x - bw - Tokens.S1, y, bw, SecurityTheme.BTN_H);
        SecurityTheme.button(s, all, "All", !this.deniedOnly, hot(all, mx, my), true);
        hit(all, () -> this.deniedOnly = false);

        y += Text.lineHeight(Text.SUB) + Tokens.S2;

        final List<SecuritySyncPacket.Line> lines = filtered();
        final Rect board = new Rect(this.area.x, y, this.area.w, this.area.bottom() - y);
        s.card(board, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);
        final Rect inner = board.inset(Tokens.S3, Tokens.S2, Tokens.S3, Tokens.S2);

        if (lines.isEmpty()) {
            s.textCenterV(this.deniedOnly ? "Nothing has been turned away" : "Nothing recorded yet",
                    board.centerX(), board.centerY(), SecurityTheme.TEXT_FAINT, Text.BODY);
            return;
        }

        final float pitch = 13.0f;
        this.scroll.setMetrics(lines.size() * pitch, inner.h);
        s.clip(inner);
        final float top = inner.y - this.scroll.value();
        for (int i = 0; i < lines.size(); i++) {
            final Rect r = new Rect(inner.x, top + i * pitch, inner.w, pitch);
            if (r.bottom() < inner.y || r.y > inner.bottom()) {
                continue;
            }
            drawLine(s, this.ctx, r, lines.get(i), true);
        }
        s.unclip();
    }

    private List<SecuritySyncPacket.Line> filtered() {
        if (!this.deniedOnly) {
            return this.ctx.log();
        }
        final List<SecuritySyncPacket.Line> out = new ArrayList<>();
        for (SecuritySyncPacket.Line l : this.ctx.log()) {
            if (l.verdictOf() == SecurityLog.Verdict.DENIED) {
                out.add(l);
            }
        }
        return out;
    }

    static void drawLine(PanelSurface s, SecurityContext ctx, Rect r, SecuritySyncPacket.Line line,
                         boolean wide) {
        final int ink = switch (line.verdictOf()) {
            case PASSED -> SecurityTheme.GREEN_DEEP;
            case DENIED -> SecurityTheme.RED;
            case FAULT -> SecurityTheme.AMBER_INK;
            default -> SecurityTheme.TEXT_DIM;
        };
        final int bg = switch (line.verdictOf()) {
            case PASSED -> SecurityTheme.GREEN_WASH;
            case DENIED -> SecurityTheme.RED_WASH;
            case FAULT -> SecurityTheme.AMBER_WASH;
            default -> SecurityTheme.CANVAS;
        };

        final String verdict = line.verdictOf().name();
        final float vw = s.textWidth(verdict, SecurityTheme.MICRO) + Tokens.S3;
        final Rect chip = new Rect(r.right() - vw, r.centerY() - 5.5f, vw, 11.0f);
        s.roundedRect(chip, Tokens.R_CHIP, bg);
        s.textCenterV(verdict, chip.centerX(), chip.centerY(), ink, SecurityTheme.MICRO);

        float right = chip.x - Tokens.S2;
        if (wide && r.w > 300.0f) {
            s.textRightV(line.detail(), right, r.centerY(), SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            right -= s.textWidth(line.detail(), SecurityTheme.MICRO) + Tokens.S3;
        }

        final String text = line.who() + "  .  " + line.target()
                + (wide ? "" : "  .  " + line.detail());
        s.textV(Text.truncate(s, text, Math.max(20.0f, right - r.x), SecurityTheme.MICRO), r.x,
                r.centerY(), SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);
    }

    @Override
    public void update(float dt) {
        this.scroll.update(dt);
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        return this.scroll.wheel(amount);
    }

    @Override
    public void drawDock(PanelSurface s, Rect r, float mx, float my) {
        SecurityDock.circuit(s, this.ctx, r);
    }
}
