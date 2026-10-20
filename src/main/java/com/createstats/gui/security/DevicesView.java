package com.createstats.gui.security;

import com.createstats.block.PoweredDoorBlock;
import com.createstats.client.PeerIdentifyHighlight;
import com.createstats.network.SecurityActionPacket;
import com.createstats.network.SecuritySyncPacket;
import com.createstats.power.SecurityDevice;
import com.createstats.security.Clearance;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.TextField;
import com.createstats.statsmk3.ui.Tokens;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public class DevicesView extends SecurityView {
    protected final SecurityDevice.DeviceClass deviceClass;
    private final Scroll scroll = new Scroll();

    // the inspector is ~300px, the dock at 1080p default is 254. overflow was silent
    private final Scroll dockScroll = new Scroll();
    private Rect dockArea = Rect.EMPTY;

    private static final float NAME_H = 18.0f;

    private static final float IDENTIFY_SECONDS = 8.0f;

    private SecuritySyncPacket.Row hovered;

    private final TextField rename;

    private BlockPos renaming;

    private String armedWhat;
    private BlockPos armedPos;
    private float armedAge;
    private static final float ARMED_SECONDS = 3.0f;

    public DevicesView(SecurityContext ctx, SecurityDevice.DeviceClass deviceClass) {
        super(ctx);
        this.deviceClass = deviceClass;
        this.rename = new TextField(this::commitRename)
                .palette(SecurityTheme.CANVAS, SecurityTheme.TEXT, SecurityTheme.TEXT_FAINT,
                        SecurityTheme.LINE, SecurityTheme.STEEL)
                .textScale(Text.BODY);
        this.rename.setVisible(false);
    }

    @Override
    public String id() {
        return this.deviceClass.key();
    }

    @Override
    public String label() {
        return this.deviceClass.label().getString();
    }

    @Override
    public String group() {
        return "devices";
    }

    @Override
    public int badge() {
        return this.ctx.devicesOf(this.deviceClass).size();
    }

    // forget the dock rect when it isn't drawn or the wheel scrolls a ghost
    @Override
    public void layout(Rect area) {
        super.layout(area);
        this.dockArea = Rect.EMPTY;

        cancelRename();
    }

    @Override
    public void drawIcon(PanelSurface s, float cx, float cy, int color) {
        switch (this.deviceClass) {
            case DOOR -> SecurityIcons.door(s, cx, cy, color);
            case SENSOR -> SecurityIcons.sensor(s, cx, cy, color);
            case LOCKER -> SecurityIcons.locker(s, cx, cy, color);
            case SHUTTER -> SecurityIcons.shutter(s, cx, cy, color);
            case REPELLER -> SecurityIcons.repeller(s, cx, cy, color);
            case RELAY -> SecurityIcons.relay(s, cx, cy, color);
            case COIL -> SecurityIcons.coil(s, cx, cy, color);
        }
    }

    @Override
    public void draw(PanelSurface s, float mx, float my) {
        final List<SecuritySyncPacket.Row> rows = this.ctx.devicesOf(this.deviceClass);
        this.hovered = null;

        float y = this.area.y;
        s.text(label(), this.area.x, y, SecurityTheme.TEXT, Text.SUB);

        final boolean capped = rows.size() >= com.createstats.security.SecuritySegment.MAX_ROWS
                && this.ctx.hiddenRows() > 0;
        final String count = rows.isEmpty()
                ? "NONE ON THIS CIRCUIT"
                : capped ? net.minecraft.network.chat.Component.translatable(
                        "gui.create_stats.security.relays.list.capped", rows.size()).getString()
                : rows.size() + " FOUND";
        s.textRight(Text.track(count), this.area.right(), y + 4.0f,
                capped ? SecurityTheme.AMBER_INK : SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        y += Text.lineHeight(Text.SUB) + Tokens.S2;

        final Rect list = new Rect(this.area.x, y, this.area.w, this.area.bottom() - y);
        if (rows.isEmpty()) {
            drawEmpty(s, list);
            publishHighlight();
            return;
        }

        final boolean folded = this.area.w < SecurityLayout.COLUMNS_MIN_W;
        final float rowH = folded ? SecurityTheme.ROW_H : SecurityTheme.ROW_H;
        final float pitch = rowH + Tokens.S1;
        this.scroll.setMetrics(rows.size() * pitch, list.h);

        s.clip(list);
        final float top = list.y - this.scroll.value();
        for (int i = 0; i < rows.size(); i++) {
            final Rect r = new Rect(list.x, top + i * pitch, list.w - (this.scroll.scrollable()
                    ? Tokens.S2 : 0.0f), rowH);

            if (r.bottom() < list.y || r.y > list.bottom()) {
                continue;
            }
            drawRow(s, r, rows.get(i), mx, my, folded);
        }
        s.unclip();

        if (this.scroll.scrollable()) {
            drawScrollbar(s, new Rect(list.right() - 3.0f, list.y, 3.0f, list.h), this.scroll);
        }
        publishHighlight();
    }

    private void drawRow(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float mx, float my,
                         boolean folded) {
        final boolean selected = row.pos().equals(this.ctx.selectedDevice);
        final boolean hot = hot(r, mx, my);
        SecurityTheme.card(s, r, hot, selected);
        hit(r, () -> this.ctx.selectedDevice = row.pos());
        if (hot) {
            this.hovered = row;
        }

        final Rect in = r.inset(Tokens.S3, 0.0f, Tokens.S2, 0.0f);

        rowIcon(s, in.x + 7.0f, r.centerY(),
                selected ? SecurityTheme.STEEL_DEEP : SecurityTheme.TEXT_DIM, row);
        final float textX = in.x + 18.0f;

        final Rect state = stateChip(s, in.right(), r.centerY(), row);
        float rightEdge = state.x - Tokens.S2;

        final String note = rowNote(row);
        if (!folded) {
            if (usesClearance(row)) {
                final Rect level = SecurityTheme.levelChip(s,
                        rightEdge - levelChipWidth(s, row), r.centerY(), row.required(),
                        Clearance.plain(row.required()));
                rightEdge = level.x - Tokens.S2;
            }
            s.textRightV(note, rightEdge, r.centerY(), SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);
            rightEdge -= s.textWidth(note, SecurityTheme.MICRO) + Tokens.S3;
        }

        final float nameW = Math.max(20.0f, rightEdge - textX);

        final String title = groupLabel(row);
        if (folded) {
            s.text(Text.truncate(s, title, nameW, Text.BODY), textX, r.y + 4.0f,
                    SecurityTheme.TEXT, Text.BODY);
            final String sub = usesClearance(row)
                    ? row.required() + " " + Clearance.plain(row.required()) + "  .  " + note
                    : note;
            s.text(Text.truncate(s, sub, nameW, SecurityTheme.MICRO), textX, r.y + 15.0f,
                    SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        } else {
            s.textV(Text.truncate(s, title, nameW, Text.BODY), textX, r.centerY(),
                    SecurityTheme.TEXT, Text.BODY);
        }

        if (row.manual()) {
            SecurityIcons.link(s, r.right() - 6.0f, r.y + 6.0f, SecurityTheme.STEEL_EDGE);
        }
    }

    protected String rowNote(SecuritySyncPacket.Row row) {
        return row.bearing();
    }

    private String groupLabel(SecuritySyncPacket.Row row) {
        if (!row.paired()) {
            return row.name();
        }
        return row.isShutter()
                ? row.name() + " (bank of " + row.units() + ")"
                : row.name() + " (pair)";
    }

    private String groupNote(SecuritySyncPacket.Row row) {
        return row.isShutter() ? row.units() + " units, one bank" : "double, both leaves";
    }

    private float levelChipWidth(PanelSurface s, SecuritySyncPacket.Row row) {
        return s.textWidth(row.required() + " " + Clearance.plain(row.required()), SecurityTheme.MICRO)
                + Tokens.S3;
    }

    protected Rect stateChip(PanelSurface s, float rightX, float centerY,
                             SecuritySyncPacket.Row row) {
        final SecurityDevice.DeviceState state = row.stateOf();

        final String label = this.deviceClass.stateLabel(state).getString().toUpperCase(Locale.ROOT);
        return switch (state) {
            case ACTIVE -> SecurityTheme.chipRight(s, rightX, centerY, label,
                    SecurityTheme.GREEN_WASH, SecurityTheme.GREEN_DEEP, SecurityTheme.GREEN_EDGE);
            case DAMAGED -> SecurityTheme.chipRight(s, rightX, centerY, label,
                    SecurityTheme.AMBER_WASH, SecurityTheme.AMBER_INK, SecurityTheme.AMBER_EDGE);
            case BREACHED, OFFLINE -> SecurityTheme.chipRight(s, rightX, centerY, label,
                    SecurityTheme.RED_WASH, SecurityTheme.RED, SecurityTheme.RED_EDGE);
            case UNPOWERED -> SecurityTheme.chipRight(s, rightX, centerY, label,
                    SecurityTheme.CANVAS, SecurityTheme.OFF, SecurityTheme.LINE);
            default -> SecurityTheme.chipRight(s, rightX, centerY, label,
                    SecurityTheme.CANVAS, SecurityTheme.TEXT_DIM, SecurityTheme.LINE);
        };
    }

    private void drawEmpty(PanelSurface s, Rect list) {
        s.card(list, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);
        final float cx = list.centerX();
        float cy = list.centerY() - 24.0f;
        drawIcon(s, cx, cy, SecurityTheme.OFF);
        cy += 24.0f;
        s.textCenterV(this.deviceClass.emptyLine().getString(), cx, cy, SecurityTheme.TEXT_DIM,
                Text.BODY);
        cy += 16.0f;
        final List<String> lines = Text.wrap(s,
                "Anything on this circuit that reports as a " + this.deviceClass.key()
                        + " appears here on its own. Nothing else has to change.",
                Math.min(320.0f, list.w - Tokens.S6), SecurityTheme.MICRO);
        for (String line : lines) {
            s.textCenterV(line, cx, cy, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            cy += Text.lineHeight(SecurityTheme.MICRO) + 2.0f;
        }
    }

    private void drawScrollbar(PanelSurface s, Rect track, Scroll sc) {
        s.roundedRect(track, track.w * 0.5f, SecurityTheme.LINE);
        final float span = Math.max(1.0f, sc.max());
        final float frac = track.h / (track.h + span);
        final float thumbH = Math.max(18.0f, track.h * frac);
        final float t = sc.value() / span;
        final float y = track.y + t * (track.h - thumbH);
        s.roundedRect(new Rect(track.x, y, track.w, thumbH), track.w * 0.5f, SecurityTheme.OFF);
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        if (this.dockArea.contains(mx, my)) {
            return this.dockScroll.wheel(amount);
        }
        return this.scroll.wheel(amount);
    }

    @Override
    public void update(float dt) {
        this.scroll.update(dt);
        this.dockScroll.update(dt);
        if (this.armedWhat != null && (this.armedAge += dt) > ARMED_SECONDS) {
            this.armedWhat = null;
            this.armedPos = null;
        }

        this.rename.update(dt);
    }

    @Override
    public void drawDock(PanelSurface s, Rect dock, float mx, float my) {
        this.dockArea = dock;
        final SecuritySyncPacket.Row row = this.ctx.selectedOr(this.deviceClass);

        if (this.renaming != null
                && (!this.rename.editing() || row == null || !this.renaming.equals(row.pos()))) {
            cancelRename();
        }
        if (row == null) {
            this.dockScroll.setMetrics(0.0f, dock.h);
            SecurityTheme.head(s, dock.x, dock.y, "NOTHING SELECTED");
            return;
        }

        final float btnY = dock.bottom() - SecurityTheme.BTN_H;
        final Rect pane = new Rect(dock.x, dock.y, dock.w,
                Math.max(0.0f, btnY - dock.y - Tokens.S2));
        final boolean bar = this.dockScroll.scrollable();

        final Rect r = new Rect(pane.x, pane.y, pane.w - (bar ? Tokens.S2 : 0.0f), pane.h);
        s.clip(pane);
        this.pushHitClip(pane);

        final float top = r.y - this.dockScroll.value();
        float y = top;
        SecurityTheme.head(s, r.x, y, "SELECTED");
        y += Text.lineHeight(SecurityTheme.MICRO) + 3.0f;
        y = drawNameLine(s, r, row, y, mx, my);
        s.text(row.paired() ? row.bearing() + "  .  " + groupNote(row) : row.bearing(),
                r.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        y += Text.lineHeight(SecurityTheme.MICRO) + Tokens.S4;

        if (row.stateOf() == SecurityDevice.DeviceState.OFFLINE) {
            y = drawForget(s, r, row, y, mx, my);
        }

        y = usesClearance(row) ? drawLadder(s, r, row, y, mx, my)
                : noClearanceLine(row) == null ? y : drawNoLadder(s, r, row, y);

        if (row.damageable()) {
            SecurityTheme.head(s, r.x, y, "INTEGRITY");
            y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
            final int fill = row.integrity() > 0.5f ? SecurityTheme.GREEN
                    : row.integrity() > 0.0f ? SecurityTheme.AMBER : SecurityTheme.RED;
            SecurityTheme.bar(s, new Rect(r.x, y, r.w, 5.0f), row.integrity(), fill);
            y += 5.0f + 4.0f;
            s.text(Math.round(row.integrity() * 100.0f) + "%", r.x, y, SecurityTheme.TEXT_FAINT,
                    SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + Tokens.S3;
        }

        y = drawExtras(s, r, row, y, mx, my);

        this.dockScroll.setMetrics(y - top, pane.h);
        s.unclip();
        this.popHitClip();
        if (bar) {
            drawScrollbar(s, new Rect(pane.right() - 3.0f, pane.y, 3.0f, pane.h), this.dockScroll);
        }

        final float btnW = (pane.w - Tokens.S1) * 0.5f;
        final Rect identify = new Rect(pane.x, btnY, btnW, SecurityTheme.BTN_H);
        SecurityTheme.button(s, identify, "Identify", false, hot(identify, mx, my), true);
        hit(identify, () -> identify(row));

        final Rect unlink = new Rect(pane.x + btnW + Tokens.S1, btnY, btnW, SecurityTheme.BTN_H);
        SecurityTheme.button(s, unlink, "Unlink", false, hot(unlink, mx, my),
                row.manual() && this.ctx.mayEditDevices());
        if (row.manual() && this.ctx.mayEditDevices()) {
            hit(unlink, () -> this.ctx.send(SecurityActionPacket.device(
                    this.ctx.panel.getBlockPos(), SecurityActionPacket.UNLINK, row.pos(), 0)));
        }
    }

    protected float drawLadder(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                             float mx, float my) {
        SecurityTheme.head(s, r.x, y, "REQUIRED CLEARANCE");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        final boolean may = this.ctx.mayEditDevices();
        for (int level = Clearance.MIN; level <= Clearance.MAX; level++) {
            final Rect opt = new Rect(r.x, y, r.w, SecurityTheme.OPT_H);
            final boolean on = row.required() == level;
            final boolean hot = may && hot(opt, mx, my);
            s.card(opt, Tokens.R_CHIP, on ? SecurityTheme.ladderBg(level)
                            : hot ? SecurityTheme.CARD_HOT : SecurityTheme.CANVAS,
                    on ? SecurityTheme.ladderEdge(level) : SecurityTheme.LINE);
            final int ink = on ? SecurityTheme.ladderInk(level) : SecurityTheme.TEXT_DIM;
            s.textV(String.valueOf(level), opt.x + Tokens.S2, opt.centerY(), ink, SecurityTheme.MICRO);
            s.textV(Clearance.plain(level), opt.x + Tokens.S2 + 10.0f, opt.centerY(), ink,
                    SecurityTheme.MICRO);

            s.textRightV(opensAt(level) + passLabel(), opt.right() - Tokens.S2, opt.centerY(),
                    on ? ink : SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            if (may) {
                final int chosen = level;
                hit(opt, () -> this.ctx.setLevel(row.pos(), chosen));
            }
            y += SecurityTheme.OPT_H + 2.0f;
        }
        return y + Tokens.S3;
    }

    private float drawNoLadder(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y) {
        SecurityTheme.head(s, r.x, y, "CLEARANCE");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        for (String line : Text.wrap(s, noClearanceLine(row), r.w, SecurityTheme.MICRO)) {
            s.text(line, r.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        return y + Tokens.S3;
    }

    protected boolean usesClearance(SecuritySyncPacket.Row row) {
        return true;
    }

    protected String passLabel() {
        return " can open";
    }

    protected String noClearanceLine(SecuritySyncPacket.Row row) {
        return "Nothing on this device is gated by clearance.";
    }

    protected float drawExtras(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                               float mx, float my) {
        return y;
    }

    protected void toggleRow(PanelSurface s, Rect box, String label, String foot, boolean on,
                             boolean may, float mx, float my, Runnable action) {
        final boolean warm = may && hot(box, mx, my);
        s.card(box, Tokens.R_CHIP,
                on ? SecurityTheme.STEEL_WASH : warm ? SecurityTheme.CARD_HOT : SecurityTheme.CANVAS,
                on ? SecurityTheme.STEEL_EDGE : SecurityTheme.LINE);
        final int ink = on ? SecurityTheme.STEEL_DEEP : may ? SecurityTheme.TEXT_DIM : SecurityTheme.OFF;

        final float footW = s.textWidth(foot, SecurityTheme.MICRO);
        s.textV(Text.truncate(s, label, box.w - footW - Tokens.S2 * 3.0f, SecurityTheme.MICRO), box.x + Tokens.S2,
                box.centerY(), ink, SecurityTheme.MICRO);
        s.textRightV(foot, box.right() - Tokens.S2, box.centerY(),
                on ? ink : SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        if (may) {
            hit(box, action);
        }
    }

    protected float drawForget(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                               float mx, float my) {
        final float inner = r.w - Tokens.S2 * 2.0f;
        final float lh = Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        final List<String> what = Text.wrap(s, net.minecraft.network.chat.Component.translatable(
                "gui.create_stats.security.relays.forget.line").getString(), inner, SecurityTheme.MICRO);
        final List<String> foot = Text.wrap(s, net.minecraft.network.chat.Component.translatable(
                "gui.create_stats.security.relays.forget.what").getString(), inner, SecurityTheme.MICRO);
        final float h = Tokens.S2 + Text.lineHeight(SecurityTheme.MICRO) + 4.0f + what.size() * lh + 4.0f
                + SecurityTheme.BTN_H + 4.0f + foot.size() * lh + Tokens.S2 - 1.0f;
        final Rect card = new Rect(r.x, y, r.w, h);
        s.card(card, Tokens.R_CHIP, SecurityTheme.RED_WASH, SecurityTheme.RED_EDGE);
        float cy = card.y + Tokens.S2;
        s.text(net.minecraft.network.chat.Component.translatable(
                        "gui.create_stats.security.relays.forget.head").getString(),
                card.x + Tokens.S2, cy, SecurityTheme.RED, SecurityTheme.MICRO);
        cy += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        for (String line : what) {
            s.text(line, card.x + Tokens.S2, cy, SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);
            cy += lh;
        }
        cy += 4.0f;
        final boolean may = this.ctx.mayEditDevices();
        final Rect btn = new Rect(card.x + Tokens.S2, cy, inner, SecurityTheme.BTN_H);
        final boolean armed = armedFor("forget", row.pos());
        danger(s, btn, net.minecraft.network.chat.Component.translatable(armed
                        ? "gui.create_stats.security.relays.forget.confirm"
                        : "gui.create_stats.security.relays.forget").getString(),
                armed, may && hot(btn, mx, my), may);
        if (may) {
            hit(btn, () -> {
                if (armedFor("forget", row.pos())) {
                    disarm();
                    this.ctx.forgetDevice(row.pos());
                } else {
                    arm("forget", row.pos());
                }
            });
        }
        cy += SecurityTheme.BTN_H + 4.0f;
        for (String line : foot) {
            s.text(line, card.x + Tokens.S2, cy, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            cy += lh;
        }
        return y + h + Tokens.S4;
    }

    protected final boolean armedFor(String what, BlockPos pos) {
        return what.equals(this.armedWhat) && pos != null && pos.equals(this.armedPos);
    }

    protected final void arm(String what, BlockPos pos) {
        this.armedWhat = what;
        this.armedPos = pos;
        this.armedAge = 0.0f;
    }

    protected final void disarm() {
        this.armedWhat = null;
        this.armedPos = null;
    }

    protected static void danger(PanelSurface s, Rect r, String label, boolean armed, boolean hot,
                                 boolean enabled) {
        if (!enabled) {
            SecurityTheme.button(s, r, label, false, false, false);
            return;
        }
        if (armed) {
            s.card(r, Tokens.R_CHIP, hot ? SecurityTheme.SEAL : SecurityTheme.RED, SecurityTheme.SEAL);
            s.textCenterV(label, r.centerX(), r.centerY(), 0xFFFFFFFF, SecurityTheme.MICRO);
            return;
        }
        s.card(r, Tokens.R_CHIP, hot ? SecurityTheme.RED_WASH : SecurityTheme.CARD,
                hot ? SecurityTheme.RED_EDGE : SecurityTheme.OFF);
        s.textCenterV(label, r.centerX(), r.centerY(), hot ? SecurityTheme.RED : SecurityTheme.TEXT_DIM,
                SecurityTheme.MICRO);
    }

    private int opensAt(int level) {
        int n = 0;
        for (SecuritySyncPacket.Person p : this.ctx.people()) {
            if (p.isOwner() || p.level() >= level) {
                n++;
            }
        }
        return n;
    }

    @Override
    public boolean click(double mx, double my) {
        if (this.rename.visible() && this.rename.mouseDown(mx, my, 0)) {
            return true;
        }
        if (this.rename.editing()) {
            this.rename.commit();
            return true;
        }
        return super.click(mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.rename.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return this.rename.charTyped(c, modifiers);
    }

    private float drawNameLine(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                               float mx, float my) {
        final Rect line = new Rect(r.x, y, r.w, NAME_H);
        if (row.pos().equals(this.renaming)) {
            this.rename.setBounds(line);
            this.rename.setVisible(true);
            this.rename.draw(s);
            return line.bottom() + 2.0f;
        }

        final boolean may = this.ctx.mayEditDevices();
        rowIcon(s, line.x + 7.0f, line.centerY(), SecurityTheme.STEEL_DEEP, row);
        final float textX = line.x + 18.0f;
        final float room = line.right() - textX - (may ? 18.0f : 0.0f);
        s.textV(Text.truncate(s, row.name(), room, Text.SUB), textX, line.centerY(),
                SecurityTheme.TEXT, Text.SUB);

        if (may) {
            final Rect pencil = new Rect(line.right() - 16.0f, line.centerY() - 8.0f, 16.0f, 16.0f);
            final boolean warm = hot(pencil, mx, my);
            if (warm) {
                s.roundedRect(pencil, Tokens.R_CHIP, SecurityTheme.CARD_HOT);
            }
            SecurityIcons.pencil(s, pencil.centerX(), pencil.centerY(),
                    warm ? SecurityTheme.STEEL_DEEP : SecurityTheme.OFF);
            hit(pencil, () -> beginRename(row));
        }
        return line.bottom() + 2.0f;
    }

    private void beginRename(SecuritySyncPacket.Row row) {
        this.renaming = row.pos();
        this.rename.placeholder(row.name());
        this.rename.begin(row.named() ? row.name() : "");
        this.rename.setVisible(true);
    }

    private void commitRename(String name) {
        if (this.renaming != null && this.ctx.mayEditDevices()) {
            this.ctx.send(SecurityActionPacket.rename(this.ctx.panel.getBlockPos(), this.renaming,
                    name));
        }
    }

    private void cancelRename() {
        this.rename.cancel();
        this.rename.setVisible(false);
        this.renaming = null;
    }

    static List<BlockPos> blocksOf(BlockGetter level, SecuritySyncPacket.Row row) {
        final List<BlockPos> out = new ArrayList<>(4);
        final BlockPos pos = row.pos();
        out.add(pos);
        if (level == null) {
            return out;
        }
        final BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof com.createstats.block.BlastShutterBlock) {
            out.clear();
            for (BlockPos member
                    : com.createstats.security.ShutterBank.membersOf(level, pos, state)) {
                out.addAll(com.createstats.block.BlastShutterBlock.columnFrom(level, member));
            }
            if (out.isEmpty()) {
                out.addAll(com.createstats.block.BlastShutterBlock.columnFrom(level, pos));
            }
            if (out.isEmpty()) {
                out.add(pos);
            }
            return out;
        }
        if (!(state.getBlock() instanceof PoweredDoorBlock)) {
            return out;
        }
        out.add(pos.above());
        final BlockPos partner = PoweredDoorBlock.partnerOf(level, pos, state);
        if (partner != null) {
            out.add(partner);
            out.add(partner.above());
        }
        return out;
    }

    private List<BlockPos> blocksOf(SecuritySyncPacket.Row row) {
        return blocksOf(Minecraft.getInstance().level, row);
    }

    private void publishHighlight() {
        final SecuritySyncPacket.Row target =
                this.hovered != null ? this.hovered : this.ctx.selectedOr(this.deviceClass);
        if (target == null) {
            PeerIdentifyHighlight.clear();
            return;
        }
        PeerIdentifyHighlight.show(blocksOf(target), outlineOf(target) & 0x00FFFFFF);
    }

    private void identify(SecuritySyncPacket.Row row) {
        PeerIdentifyHighlight.pin(blocksOf(row), outlineOf(row) & 0x00FFFFFF, IDENTIFY_SECONDS);
        final Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            mc.screen.onClose();
        }
    }

    protected int outlineOf(SecuritySyncPacket.Row row) {
        return SecurityTheme.outlineColor(row);
    }

    protected void rowIcon(PanelSurface s, float cx, float cy, int color,
                           SecuritySyncPacket.Row row) {
        drawIcon(s, cx, cy, color);
    }

    @Override
    public String toString() {
        return "DevicesView[" + this.deviceClass.name().toLowerCase(Locale.ROOT) + "]";
    }
}
