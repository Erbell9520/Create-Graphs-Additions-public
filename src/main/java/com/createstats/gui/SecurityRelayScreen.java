package com.createstats.gui;

import com.createstats.client.LocalGuiDensity;
import com.createstats.gui.meter.plate.GlassFont;
import com.createstats.gui.meter.plate.PlateGeometry;
import com.createstats.gui.meter.plate.PlateInk;
import com.createstats.gui.meter.plate.RatingPlate;
import com.createstats.gui.security.RelaysView;
import com.createstats.network.SecurityRelayActionPacket;
import com.createstats.network.SecurityRelaySyncPacket;
import com.createstats.security.SecurityMode;
import com.createstats.security.Truth;
import com.createstats.security.relay.RelayBehavior;
import com.createstats.security.relay.RelayCondition;
import com.createstats.security.relay.RelayConditions;
import com.createstats.security.relay.RelayConfig;
import com.createstats.security.relay.RelayContact;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public class SecurityRelayScreen extends Screen implements LocalGuiDensity {
    private static final int POLL_TICKS = 10;
    private static final int SCRIM = 0xB8000000;
    private static final String K = "gui.create_stats.security_relay.";

    private static final int PW = 312;
    private static final int PH = 220;
    private static final int TITLE_Y = 8;
    private static final int HOUSE_Y = 20;
    private static final int LAMP_Y = 33;
    private static final int LENS = 13;
    private static final int WIN_X = 12;
    private static final int WIN_Y = 56;
    private static final int WIN_W = 226;
    private static final int WIN_H = 48;
    private static final int FLAG_X = PW - 34;
    private static final int FLAG_Y = 58;
    private static final int FLAG_W = 20;
    private static final int FLAG_H = 13;
    private static final int RESET_X = PW - 34;
    private static final int RESET_Y = 80;
    private static final int RESET_D = 20;
    private static final int RULE_Y = 110;
    private static final int[] COND_X = {14, 118};
    private static final int COND_Y = 125;
    private static final int COND_PITCH = 10;
    private static final int COND_W = 102;
    private static final int KEY_X = 226;
    private static final int KEY_W = 74;
    private static final int KEY_Y = 124;
    private static final int KEY_H = 12;
    private static final int KEY_PITCH = 14;
    private static final int PULSE_Y = 181;
    private static final int MODE_Y = 197;
    private static final int ACCEPT_Y = 176;

    private static final int[][] PULSE_KEYS = {{50, 20, -10}, {72, 14, -1}, {174, 14, 1}, {190, 20, 10}};

    private static final int ATLAS_W = 128;
    private static final int ATLAS_H = 64;
    private static final int[] SCREW = {0, 0, 5, 5};
    private static final int[] JEWEL_OFF = {8, 0, 5, 5};
    private static final int[] JEWEL_GREEN = {14, 0, 5, 5};
    private static final int[] JEWEL_AMBER = {20, 0, 5, 5};
    private static final int[] KEY_UP = {0, 24, 22, 16};
    private static final int[] KEY_DOWN = {24, 24, 22, 16};
    private static final int[] KEY_HOVER = {48, 24, 22, 16};

    private static final int BLANK_FLOOR = 0xFF9BA1A6;
    private static final int BLANK_INK = 0xFF7D848A;
    private static final int BLANK_LIT = 0xFFB4BABE;
    private static final int BRASS_INK_DIM = 0xFF7A6440;
    private static final int FLAG_FLOOR = 0xFF1D2124;

    private SecurityRelaySyncPacket view;
    private int sincePoll;

    private int sinceSync;
    private int ticks;
    private int resetPressAge = 99;

    private float flagT;
    private final RelaysView.Edits edits = new RelaysView.Edits();

    private int px;
    private int py;
    private RatingPlate plate;
    private int[] wear = new int[0];

    public SecurityRelayScreen(SecurityRelaySyncPacket first) {
        super(Component.translatable("block.create_stats.security_relay"));
        this.view = first;
        this.flagT = first.pending() ? 1.0f : 0.0f;
    }

    public BlockPos pos() {
        return this.view.pos();
    }

    // never shrunk, 312x220 fits every gui scale
    @Override
    public int maxEffectiveGuiScale() {
        return Integer.MAX_VALUE;
    }

    public void accept(SecurityRelaySyncPacket packet) {
        this.view = packet;
        this.sinceSync = 0;
        this.edits.onSync(packet.revision(), configOf(packet), scopeOf(packet), this::sendConfig);
    }

    @Override
    protected void init() {
        this.px = (this.width - PW) / 2;
        this.py = (this.height - PH) / 2;

        this.plate = new RatingPlate(this.font, PlateGeometry.of(this.width, this.height), List.of());
        GlassFont.ensure();
        final List<Integer> w = new ArrayList<>();
        for (int x = 4; x < PW - 4; x++) {
            if (vnoise(x, 4, 13) < 0.28f) {
                w.add(x);
                w.add(4);
            }
            if (vnoise(x, PH - 5, 17) < 0.22f) {
                w.add(x);
                w.add(PH - 5);
            }
        }
        for (int y = 5; y < PH - 5; y++) {
            if (vnoise(4, y, 19) < 0.24f) {
                w.add(4);
                w.add(y);
            }
        }
        this.wear = w.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public void tick() {
        super.tick();
        this.ticks++;
        this.sinceSync++;
        this.resetPressAge++;
        this.edits.update(0.05f);

        final float want = this.view.pending() ? 1.0f : 0.0f;
        if (want > this.flagT) {
            this.flagT = Math.min(1.0f, this.flagT + 0.2f + this.flagT * 0.3f);
        } else if (want < this.flagT) {
            this.flagT = Math.max(0.0f, this.flagT - 0.5f);
        }
        if (++this.sincePoll >= POLL_TICKS) {
            this.sincePoll = 0;
            send(SecurityRelayActionPacket.REFRESH);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    private RelayContact contact() {
        return RelayContact.byOrdinal(this.view.contact());
    }

    @Nullable
    private RelayCondition condition() {
        return RelayConditions.byId(this.view.condition());
    }

    private boolean editable() {
        final SecurityRelaySyncPacket v = this.view;
        return v.mayEdit() && contact() == RelayContact.LINKED && !v.pending();
    }

    @Nullable
    private String stampKey() {
        final SecurityRelaySyncPacket v = this.view;
        if (this.edits.busy()) {
            return K + "stamp.sending";
        }
        if (!v.mayEdit()) {
            return K + "stamp.read_only";
        }
        if (v.pending()) {
            return K + "stamp.held";
        }
        if (contact() != RelayContact.LINKED) {
            return K + "stamp.no_contact";
        }
        return null;
    }

    private String lockedTip() {
        final SecurityRelaySyncPacket v = this.view;
        if (!v.mayEdit()) {
            return tr(K + "tip.read_only");
        }
        if (v.pending()) {
            return tr(K + "tip.held");
        }
        return tr(K + "tip.contact." + contact().name().toLowerCase(Locale.ROOT));
    }

    private String houseName() {
        return this.view.house().isEmpty() ? tr("gui.create_stats.security.house_unnamed")
                : tr("gui.create_stats.security.house", this.view.house());
    }

    @Nullable
    private static BlockPos scopeOf(SecurityRelaySyncPacket p) {
        return p.scoped() ? p.scope() : null;
    }

    private static RelayConfig configOf(SecurityRelaySyncPacket p) {
        final RelayBehavior b = RelayBehavior.byKey(p.behavior());
        return new RelayConfig(p.condition(), b == null ? RelayBehavior.FOLLOW : b,
                p.pulse() <= 0 ? RelayConfig.DEFAULT_PULSE : p.pulse(),
                p.mode().isEmpty() ? SecurityMode.AWAY.key() : p.mode(), null);
    }

    private void send(int action) {
        PacketDistributor.sendToServer(SecurityRelayActionPacket.simple(pos(), false, pos(), action));
    }

    private void sendConfig(RelayConfig config, @Nullable BlockPos scope, long revision) {
        PacketDistributor.sendToServer(SecurityRelayActionPacket.configure(pos(), false, pos(), config, scope,
                revision));
    }

    private void ask(String asked, java.util.function.UnaryOperator<RelayConfig> change) {
        if (!editable()) {
            return;
        }
        final RelayConfig base = this.edits.base(configOf(this.view));
        RelayConfig wanted = change.apply(base);

        final BlockPos scope = wanted.conditionId().equals(base.conditionId())
                ? this.edits.baseScope(scopeOf(this.view)) : null;
        final RelayCondition c = wanted.condition();
        if (c != null && !c.offers(wanted.behavior())) {
            for (RelayBehavior b : RelayBehavior.all()) {
                if (c.offers(b)) {
                    wanted = wanted.withBehavior(b);
                    break;
                }
            }
        }
        if (c != null && c.needsMode() && SecurityMode.byKey(wanted.mode()) == null) {
            wanted = wanted.withMode(SecurityMode.AWAY.key());
        }
        this.edits.ask(this.view.revision(), wanted, scope, asked, this::sendConfig);
    }

    private record Hit(String kind, int x, int y, int w, int h, @Nullable RelayCondition cond,
                       @Nullable RelayBehavior behavior, int step, @Nullable SecurityMode mode) {
        boolean contains(int mx, int my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }

    private static Hit part(String kind, int x, int y, int w, int h) {
        return new Hit(kind, x, y, w, h, null, null, 0, null);
    }

    private List<Hit> hits() {
        final List<Hit> out = new ArrayList<>();
        out.add(part("contact", 12, 32, 70, 20));
        out.add(part("output", PW - 82, 32, 70, 20));
        out.add(part("window", WIN_X, WIN_Y, WIN_W, WIN_H));
        out.add(part("flag", FLAG_X, FLAG_Y, FLAG_W, FLAG_H));
        out.add(part("reset", RESET_X - 1, RESET_Y - 1, RESET_D + 2, RESET_D + 2));
        final RelayContact c = contact();
        if (c == RelayContact.CANDIDATE || c == RelayContact.UNBOUND) {
            if (c == RelayContact.CANDIDATE && this.view.mayEdit()) {
                final int kw = acceptWidth();
                out.add(part("accept", (PW - kw) / 2, ACCEPT_Y, kw, 16));
            }
            return out;
        }
        final List<RelayCondition> all = RelayConditions.all();
        for (int i = 0; i < all.size(); i++) {
            final int col = i < 5 ? 0 : 1;
            final int row = i < 5 ? i : i - 5;
            out.add(new Hit("cond", COND_X[col] - 1, COND_Y + row * COND_PITCH - 2, COND_W, COND_PITCH, all.get(i),
                    null, 0, null));
        }
        final RelayBehavior[] behaviors = RelayBehavior.all();
        for (int i = 0; i < behaviors.length; i++) {
            out.add(new Hit("beh", KEY_X, KEY_Y + i * KEY_PITCH, KEY_W, KEY_H, null, behaviors[i], 0, null));
        }
        for (int[] k : PULSE_KEYS) {
            out.add(new Hit("pulse", k[0], PULSE_Y - 2, k[1], 12, null, null, k[2], null));
        }
        final SecurityMode[] modes = SecurityMode.all();
        for (int i = 0; i < modes.length; i++) {
            out.add(new Hit("mode", 50 + i * 40, MODE_Y - 2, 38, 12, null, null, 0, modes[i]));
        }
        return out;
    }

    @Nullable
    private Hit at(double mouseX, double mouseY) {
        final int x = (int) Math.floor(mouseX) - this.px;
        final int y = (int) Math.floor(mouseY) - this.py;
        final List<Hit> hits = hits();
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(x, y)) {
                return hits.get(i);
            }
        }
        return null;
    }

    private int acceptWidth() {
        return this.font.width(tr(K + "accept").toUpperCase(Locale.ROOT)) + 16;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        final Hit h = button == 0 ? at(mouseX, mouseY) : null;
        if (h == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        final SecurityRelaySyncPacket v = this.view;
        switch (h.kind()) {
            case "reset" -> {
                this.resetPressAge = 0;
                send(SecurityRelayActionPacket.RESET);
            }
            case "accept" -> send(SecurityRelayActionPacket.ACCEPT);
            case "cond" -> {
                if (h.cond() != null && !h.cond().id().equals(v.condition())) {
                    ask("cond:" + h.cond().id(), w -> w.withCondition(h.cond().id()));
                }
            }
            case "beh" -> {
                final RelayCondition c = condition();
                if (c != null && c.offers(h.behavior()) && !h.behavior().key().equals(v.behavior())) {
                    ask("beh:" + h.behavior().key(), w -> w.withBehavior(h.behavior()));
                }
            }
            case "pulse" -> {
                if (RelayBehavior.PULSE.key().equals(v.behavior())) {
                    ask("pulse", w -> w.withPulse(Math.max(RelayConfig.MIN_PULSE,
                            Math.min(RelayConfig.MAX_PULSE, w.pulseTicks() + h.step()))));
                }
            }
            case "mode" -> {
                final RelayCondition c = condition();
                if (c != null && c.needsMode() && !h.mode().key().equals(v.mode())) {
                    ask("mode:" + h.mode().key(), w -> w.withMode(h.mode().key()));
                }
            }
            default -> {
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, this.width, this.height, SCRIM);
        final Hit hot = at(mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(this.px, this.py, 0.0f);
        drawPlate(g);
        drawHeader(g);
        drawLamps(g);
        drawWindow(g);
        drawFlag(g);
        drawResetButton(g, this.resetPressAge < 6, hot != null && "reset".equals(hot.kind()));
        this.plate.engraveRight(g, tr(K + "legend.held"), FLAG_X - 4, FLAG_Y + 3, PlateInk.ENGRAVE);
        this.plate.engraveRight(g, tr(K + "legend.reset"), RESET_X - 4, RESET_Y + 6, PlateInk.ENGRAVE);
        drawSettings(g, hot);
        g.pose().popPose();
        if (hot != null) {
            final List<FormattedCharSequence> tip = tipFor(hot);
            if (!tip.isEmpty()) {
                g.renderTooltip(this.font, tip, mouseX, mouseY);
            }
        }
    }

    private void drawPlate(GuiGraphics g) {
        g.fill(0, 0, PW, PH, PlateInk.RIM_OUT);
        g.fill(1, 1, PW - 1, PH - 1, PlateInk.RIM_FACE);
        g.fill(1, 1, PW - 1, 2, PlateInk.RIM_LIT);
        g.fill(1, 1, 2, PH - 1, PlateInk.RIM_LIT);
        g.fill(1, PH - 2, PW - 1, PH - 1, PlateInk.RIM_DARK);
        g.fill(PW - 2, 1, PW - 1, PH - 1, PlateInk.RIM_DARK);

        g.blit(RatingPlate.PLATE_TEXTURE, 3, 3, 0.0f, 0.0f, PW - 6, PH - 6, 352, 220);
        g.drawManaged(() -> {
            g.fill(3, 3, PW - 3, 4, PlateInk.PLATE_HI);
            g.fill(3, 3, 4, PH - 3, PlateInk.PLATE_HI);
            g.fill(3, PH - 4, PW - 3, PH - 3, PlateInk.PLATE_LO);
            g.fill(PW - 4, 3, PW - 3, PH - 3, PlateInk.PLATE_LO);
            for (int i = 0; i < this.wear.length; i += 2) {
                px(g, this.wear[i], this.wear[i + 1], PlateInk.WEAR);
            }
        });
        sprite(g, SCREW, 6, 6);
        sprite(g, SCREW, PW - 11, 6);
        sprite(g, SCREW, 6, PH - 11);
        sprite(g, SCREW, PW - 11, PH - 11);
    }

    private void drawHeader(GuiGraphics g) {
        this.plate.engrave(g, tr(K + "title"), 15, TITLE_Y);
        final BlockPos p = pos();
        this.plate.stampRight(g, "X" + p.getX() + " Y" + p.getY() + " Z" + p.getZ(), PW - 16, TITLE_Y);
        final String status = tr(K + "status." + contact().name().toLowerCase(Locale.ROOT), houseName());
        this.plate.engrave(g, trim(status, PW - 30), 15, HOUSE_Y, PlateInk.ENGRAVE_SOFT);
    }

    @Nullable
    private PlateInk.Ink contactInk() {
        if (!this.view.powered()) {
            return null;
        }
        return switch (contact()) {
            case LINKED -> PlateInk.GREEN;
            case UNKNOWN, CANDIDATE -> PlateInk.AMBER;
            case LOST -> PlateInk.RED;
            case UNBOUND -> null;
        };
    }

    private boolean contactLit() {
        return switch (contact()) {
            case UNKNOWN -> (this.ticks / 20) % 2 == 0;
            case CANDIDATE -> Math.sin(this.ticks / 8.0) > -0.2;
            default -> true;
        };
    }

    private void drawLamps(GuiGraphics g) {
        final SecurityRelaySyncPacket v = this.view;
        final PlateInk.Ink ci = contactInk();
        lens(g, 13, LAMP_Y, ci == null ? PlateInk.GREEN : ci, ci != null && contactLit());
        this.plate.engrave(g, tr(K + "lamp.contact"), 31, LAMP_Y);
        final String word = !v.powered() ? "no_power" : contact().name().toLowerCase(Locale.ROOT);
        final PlateInk.Ink wink = !v.powered() ? PlateInk.AMBER : ci == null ? PlateInk.IDLE : ci;
        this.plate.label(g, tr(K + "word." + word), 32, LAMP_Y + 11, wink.main());

        final boolean on = v.powered() && v.output() > 0;
        lens(g, PW - 26, LAMP_Y, PlateInk.RED, on);
        this.plate.engraveRight(g, tr(K + "lamp.output"), PW - 31, LAMP_Y, PlateInk.ENGRAVE);
        final String out = String.valueOf(v.powered() ? v.output() : 0);
        this.plate.label(g, out, PW - 31 - GlassFont.width(out), LAMP_Y + 11,
                on ? PlateInk.RED.main() : PlateInk.IDLE.mid());
    }

    private void drawWindow(GuiGraphics g) {
        final SecurityRelaySyncPacket v = this.view;
        grimeRing(g, WIN_X, WIN_Y, WIN_W, WIN_H);
        this.plate.window(g, new PlateGeometry.Rect(WIN_X, WIN_Y, WIN_W, WIN_H), true);
        final int x = WIN_X + 6;
        final int xr = WIN_X + WIN_W - 6;
        final int[] ys = {WIN_Y + 6, WIN_Y + 16, WIN_Y + 26, WIN_Y + 36};
        final RelayContact c = contact();
        if (c == RelayContact.CANDIDATE || c == RelayContact.UNBOUND) {
            GlassFont.glow(g, tr(K + (c == RelayContact.CANDIDATE ? "window.waiting" : "window.no_house")), x, ys[0],
                    PlateInk.AMBER, 1);
            if (!v.house().isEmpty()) {
                GlassFont.draw(g, glassFit(houseName().toUpperCase(Locale.ROOT), xr - x), x, ys[1],
                        PlateInk.IDLE.mid(), 1);
            }
            return;
        }
        final RelayCondition cond = condition();
        final String beh = RelaysView.behaviorName(v.behavior()).toUpperCase(Locale.ROOT)
                + (RelayBehavior.PULSE.key().equals(v.behavior()) ? " " + tr(K + "pulse_short", v.pulse()) : "");
        final String name = RelaysView.conditionName(v.condition()).toUpperCase(Locale.ROOT);
        GlassFont.glow(g, glassFit(name, xr - x - GlassFont.width(beh) - 6), x, ys[0], PlateInk.IDLE, 1);
        right(g, beh, xr, ys[0], PlateInk.IDLE.mid());

        if (!v.powered()) {
            GlassFont.draw(g, tr(K + "window.no_power"), x, ys[1], PlateInk.AMBER.main(), 1);
        } else if (cond == null || cond.isPoint()) {
            GlassFont.draw(g, tr(K + "reads.event"), x, ys[1], PlateInk.IDLE.mid(), 1);
        } else {
            final Truth t = Truth.byOrdinal(v.truth());
            final String text = tr(K + "reads." + t.name().toLowerCase(Locale.ROOT));

            final PlateInk.Ink ink = t == Truth.UNKNOWN ? PlateInk.AMBER
                    : t == Truth.FALSE ? null
                    : "healthy".equals(cond.langKey()) ? PlateInk.GREEN
                    : "mode".equals(cond.langKey()) ? PlateInk.IDLE : PlateInk.RED;
            if (ink != null) {
                GlassFont.glow(g, text, x, ys[1], ink, 1);
            } else {
                GlassFont.draw(g, text, x, ys[1], PlateInk.IDLE.mid(), 1);
            }
        }
        if (v.sources() > 0) {
            right(g, tr(K + "seen", v.sources() - v.unavailable(), v.sources()), xr, ys[1],
                    v.unavailable() > 0 ? PlateInk.AMBER.main() : PlateInk.IDLE.dim());
        }

        final int pulseLeft = Math.max(0, v.pulseLeft() - Math.min(POLL_TICKS * 2, this.sinceSync));
        if (pulseLeft > 0) {
            GlassFont.draw(g, tr(K + "window.pulse"), x, ys[2], PlateInk.GREEN.main(), 1);
            final int n = 20;
            final int lit = (int) Math.ceil(n * pulseLeft / (double) Math.max(1, v.pulse()));
            for (int i = 0; i < n; i++) {
                g.fill(x + 34 + i * 6, ys[2] + 1, x + 38 + i * 6, ys[2] + 6,
                        i < lit ? PlateInk.GREEN.main() : PlateInk.GREEN.faint());
            }
            right(g, tr(K + "pulse_short", pulseLeft), xr, ys[2], PlateInk.GREEN.main());
        } else {
            final int since = v.sinceTrigger() < 0 ? -1 : v.sinceTrigger() + Math.min(POLL_TICKS * 2, this.sinceSync);
            GlassFont.draw(g, since < 0 ? tr(K + "last.never") : tr(K + "last.ago", shortAgo(since)), x, ys[2],
                    since < 0 ? PlateInk.IDLE.dim() : PlateInk.IDLE.mid(), 1);
        }

        if (v.pending()) {
            GlassFont.glow(g, tr(K + "line.held"), x, ys[3], PlateInk.RED, 1);
        } else if (cond != null && cond.needsMode()) {
            final String mode = tr("relay.create_stats.mode." + (v.mode().isEmpty() ? "away" : v.mode()));
            GlassFont.draw(g, tr(K + "line.mode", mode.toUpperCase(Locale.ROOT)), x, ys[3], PlateInk.IDLE.mid(), 1);
        } else {
            GlassFont.draw(g, tr(K + (v.scoped() ? "line.device" : "line.house")), x, ys[3], PlateInk.IDLE.dim(), 1);
        }
    }

    private void drawSettings(GuiGraphics g, @Nullable Hit hot) {
        final SecurityRelaySyncPacket v = this.view;
        this.plate.rule(g, 12, RULE_Y, PW - 13, RULE_Y);
        final RelayContact c = contact();
        if (c == RelayContact.CANDIDATE || c == RelayContact.UNBOUND) {
            drawCandidate(g, hot);
            return;
        }
        final boolean live = editable();
        final RelayCondition cond = condition();

        this.plate.engrave(g, tr(K + "head.watches"), 14, RULE_Y + 4);
        final List<RelayCondition> all = RelayConditions.all();
        for (int i = 0; i < all.size(); i++) {
            final RelayCondition cd = all.get(i);
            final int x = COND_X[i < 5 ? 0 : 1];
            final int y = COND_Y + (i < 5 ? i : i - 5) * COND_PITCH;
            final boolean on = cd.id().equals(v.condition());
            final boolean hotHere = live && hot != null && "cond".equals(hot.kind()) && hot.cond() == cd && !on;
            sprite(g, on ? JEWEL_GREEN : asked("cond:" + cd.id()) || hotHere ? JEWEL_AMBER : JEWEL_OFF, x, y + 1);
            this.plate.engrave(g, tr("relay.create_stats.condition." + cd.langKey()), x + 8, y,
                    on ? PlateInk.ENGRAVE : PlateInk.ENGRAVE_SOFT);
        }

        this.plate.engrave(g, tr(K + "head.answers"), KEY_X, RULE_Y + 4);
        final RelayBehavior[] behaviors = RelayBehavior.all();
        for (int i = 0; i < behaviors.length; i++) {
            final RelayBehavior b = behaviors[i];
            final int y = KEY_Y + i * KEY_PITCH;
            final String word = RelaysView.behaviorName(b.key()).toUpperCase(Locale.ROOT);
            if (cond != null && !cond.offers(b)) {
                blank(g, word, KEY_X, y, KEY_W, KEY_H);
                continue;
            }
            final boolean on = b.key().equals(v.behavior());
            final boolean hotHere = live && hot != null && "beh".equals(hot.kind()) && hot.behavior() == b && !on;
            keyWord(g, word, KEY_X, y, KEY_W, KEY_H, on, asked("beh:" + b.key()), hotHere, live);
        }

        final boolean pulsing = RelayBehavior.PULSE.key().equals(v.behavior());
        this.plate.engrave(g, tr(K + "head.pulse"), 14, PULSE_Y, pulsing ? PlateInk.ENGRAVE : PlateInk.ENGRAVE_SOFT);
        for (int[] k : PULSE_KEYS) {
            final String word = (k[2] > 0 ? "+" : "-") + Math.abs(k[2]);
            if (!pulsing) {
                blank(g, word, k[0], PULSE_Y - 2, k[1], 12);
                continue;
            }
            final boolean hotHere = live && hot != null && "pulse".equals(hot.kind()) && hot.step() == k[2];
            key(g, hotHere ? KEY_HOVER : KEY_UP, k[0], PULSE_Y - 2, k[1], 12);
            g.drawString(this.font, word, k[0] + (k[1] - this.font.width(word)) / 2, PULSE_Y,
                    live ? PlateInk.BRASS_INK : BRASS_INK_DIM, false);
        }
        grimeRing(g, 88, PULSE_Y - 2, 84, 12);
        this.plate.window(g, new PlateGeometry.Rect(88, PULSE_Y - 2, 84, 12), false);

        final String secs = RelaysView.seconds(v.pulse());
        final String reading = v.pulse() == 1 ? tr(K + "pulse_reading.one", secs) : tr(K + "pulse_reading", v.pulse(), secs);
        GlassFont.draw(g, reading, 130 - GlassFont.width(reading) / 2, PULSE_Y + 1,
                !pulsing ? PlateInk.IDLE.dim() : asked("pulse") ? PlateInk.AMBER.main() : PlateInk.GREEN.main(), 1);

        final boolean moding = cond != null && cond.needsMode();
        this.plate.engrave(g, tr(K + "head.mode"), 14, MODE_Y, moding ? PlateInk.ENGRAVE : PlateInk.ENGRAVE_SOFT);
        final SecurityMode[] modes = SecurityMode.all();
        for (int i = 0; i < modes.length; i++) {
            final SecurityMode m = modes[i];
            final int x = 50 + i * 40;
            final String word = tr("relay.create_stats.mode." + m.key()).toUpperCase(Locale.ROOT);
            if (!moding) {
                blank(g, word, x, MODE_Y - 2, 38, 12);
                continue;
            }
            final boolean on = m.key().equals(v.mode().isEmpty() ? SecurityMode.AWAY.key() : v.mode());
            final boolean hotHere = live && hot != null && "mode".equals(hot.kind()) && hot.mode() == m && !on;
            keyWord(g, word, x, MODE_Y - 2, 38, 12, on, asked("mode:" + m.key()), hotHere, live);
        }

        final String stamp = stampKey();
        if (stamp != null) {
            stampCentre(g, tr(stamp), 262, PULSE_Y + 8);
        }
    }

    private void drawCandidate(GuiGraphics g, @Nullable Hit hot) {
        final SecurityRelaySyncPacket v = this.view;
        final boolean candidate = contact() == RelayContact.CANDIDATE;
        final String text = candidate ? tr(K + "candidate.line", houseName()) : tr(K + "unbound.line");
        int y = RULE_Y + 10;
        for (FormattedCharSequence line : this.font.split(Component.literal(text), PW - 40)) {
            engraveSeq(g, line, 20, y, PlateInk.ENGRAVE);
            y += 11;
        }
        if (!candidate) {
            return;
        }
        if (v.mayEdit()) {
            final String word = tr(K + "accept").toUpperCase(Locale.ROOT);
            final int kw = acceptWidth();
            final boolean hotHere = hot != null && "accept".equals(hot.kind());
            key(g, hotHere ? KEY_HOVER : KEY_UP, (PW - kw) / 2, ACCEPT_Y, kw, 16);
            g.drawString(this.font, word, (PW - this.font.width(word)) / 2, ACCEPT_Y + 4, PlateInk.BRASS_INK, false);
        } else {
            for (FormattedCharSequence line : this.font.split(Component.literal(tr(K + "candidate.member")), PW - 40)) {
                engraveSeq(g, line, 20, y + 6, PlateInk.ENGRAVE_SOFT);
                y += 11;
            }
        }
    }

    private List<FormattedCharSequence> tipFor(Hit h) {
        final SecurityRelaySyncPacket v = this.view;
        final List<FormattedCharSequence> out = new ArrayList<>();
        final String c = contact().name().toLowerCase(Locale.ROOT);
        switch (h.kind()) {
            case "contact" -> {
                if (!v.powered()) {
                    tip(out, tr(K + "word.no_power"), ChatFormatting.WHITE);
                    tip(out, tr(K + "tip.no_power"), ChatFormatting.GRAY);
                } else {
                    tip(out, tr(K + "status." + c, houseName()), ChatFormatting.WHITE);
                    tip(out, tr(K + "tip.contact." + c), ChatFormatting.GRAY);
                }
            }
            case "output" -> {
                tip(out, tr(K + "output", v.powered() ? v.output() : 0), ChatFormatting.WHITE);
                tip(out, tr(K + "tip.output"), ChatFormatting.GRAY);
            }
            case "window" -> tip(out, tr(K + "tip.window"), ChatFormatting.GRAY);
            case "flag" -> tip(out, tr(K + "tip.flag"), ChatFormatting.GRAY);
            case "reset" -> {
                tip(out, tr(K + "reset"), ChatFormatting.WHITE);
                tip(out, tr(v.pending() ? K + "tip.reset" : K + "tip.reset.none"), ChatFormatting.GRAY);
            }
            case "cond" -> {
                if (h.cond() != null) {
                    tip(out, tr("relay.create_stats.condition." + h.cond().langKey()), ChatFormatting.WHITE);
                    tip(out, tr("relay.create_stats.condition." + h.cond().langKey() + ".blurb"), ChatFormatting.GRAY);
                    if (!editable()) {
                        tip(out, lockedTip(), ChatFormatting.GOLD);
                    } else if (v.scoped() && !h.cond().id().equals(v.condition())) {
                        tip(out, tr(K + "tip.scoped"), ChatFormatting.GOLD);
                    }
                }
            }
            case "beh" -> {
                final RelayCondition cond = condition();
                tip(out, RelaysView.behaviorName(h.behavior().key()), ChatFormatting.WHITE);
                tip(out, tr("relay.create_stats.behavior." + h.behavior().key() + ".blurb"), ChatFormatting.GRAY);
                if (cond != null && !cond.offers(h.behavior())) {
                    tip(out, tr(cond.isPoint() ? K + "tip.point" : K + "tip.supervision"), ChatFormatting.GOLD);
                } else if (!editable()) {
                    tip(out, lockedTip(), ChatFormatting.GOLD);
                }
            }
            case "pulse" -> {
                if (!RelayBehavior.PULSE.key().equals(v.behavior())) {
                    tip(out, tr(K + "tip.not_pulse"), ChatFormatting.GRAY);
                }
            }
            case "mode" -> {
                final RelayCondition cond = condition();
                if (cond == null || !cond.needsMode()) {
                    tip(out, tr(K + "tip.not_mode"), ChatFormatting.GRAY);
                }
            }
            default -> {
            }
        }
        return out;
    }

    private void tip(List<FormattedCharSequence> out, String text, ChatFormatting colour) {
        out.addAll(this.font.split(Component.literal(text).withStyle(colour), 220));
    }

    private boolean asked(String key) {
        return this.edits.busy() && key.equals(this.edits.asked());
    }

    private static void px(GuiGraphics g, int x, int y, int c) {
        g.fill(x, y, x + 1, y + 1, c);
    }

    private static void sprite(GuiGraphics g, int[] s, int x, int y) {
        g.blit(RatingPlate.ATLAS, x, y, s[0], s[1], s[2], s[3], ATLAS_W, ATLAS_H);
    }

    private static float vnoise(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * (int) 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0f;
    }

    private static void grimeRing(GuiGraphics g, int x, int y, int w, int h) {
        g.drawManaged(() -> {
            for (int i = -1; i <= w; i++) {
                for (int yy : new int[]{y - 1, y + h}) {
                    if (vnoise(x + i, yy, 31) < 0.34f) {
                        px(g, x + i, yy, PlateInk.GRIME);
                    }
                }
            }
            for (int j = 0; j < h; j++) {
                for (int xx : new int[]{x - 1, x + w}) {
                    if (vnoise(xx, y + j, 37) < 0.34f) {
                        px(g, xx, y + j, PlateInk.GRIME);
                    }
                }
            }
        });
    }

    private static void key(GuiGraphics g, int[] s, int x, int y, int w, int h) {
        final int e = 2;
        final int mw = s[2] - e * 2;
        final int mh = s[3] - e * 2;
        final List<int[]> cols = new ArrayList<>();
        cols.add(new int[]{0, e, x});
        for (int xx = e; xx < w - e; xx += mw) {
            cols.add(new int[]{e, Math.min(mw, w - e - xx), x + xx});
        }
        cols.add(new int[]{s[2] - e, e, x + w - e});
        final List<int[]> rows = new ArrayList<>();
        rows.add(new int[]{0, e, y});
        for (int yy = e; yy < h - e; yy += mh) {
            rows.add(new int[]{e, Math.min(mh, h - e - yy), y + yy});
        }
        rows.add(new int[]{s[3] - e, e, y + h - e});
        for (int[] c : cols) {
            for (int[] r : rows) {
                g.blit(RatingPlate.ATLAS, c[2], r[2], s[0] + c[0], s[1] + r[0], c[1], r[1], ATLAS_W, ATLAS_H);
            }
        }
    }

    private void keyWord(GuiGraphics g, String word, int x, int y, int w, int h, boolean on, boolean asked,
                         boolean hot, boolean live) {
        final boolean down = on || asked;
        key(g, down ? KEY_DOWN : hot ? KEY_HOVER : KEY_UP, x, y, w, h);
        if (down) {
            sprite(g, asked && !on ? JEWEL_AMBER : JEWEL_GREEN, x + 3, y + (h - 5) / 2 + 1);
        }
        final int room = down ? 8 : 0;
        final int sink = down ? 1 : 0;
        g.drawString(this.font, word, x + room + (w - room - this.font.width(word)) / 2 + sink, y + 2 + sink,
                live || on ? PlateInk.BRASS_INK : BRASS_INK_DIM, false);
    }

    private void blank(GuiGraphics g, String word, int x, int y, int w, int h) {
        this.plate.cut(g, x, y, w, h, BLANK_FLOOR);
        final int tx = x + (w - this.font.width(word)) / 2;
        final int ty = y + (h - 8) / 2;
        g.drawString(this.font, word, tx + 1, ty + 1, BLANK_LIT, false);
        g.drawString(this.font, word, tx, ty, BLANK_INK, false);
    }

    private void stampCentre(GuiGraphics g, String text, int cx, int y) {
        final int w = this.font.width(text);
        final int x = cx - w / 2;
        this.plate.cut(g, x - 3, y - 2, w + 5, 11, PlateInk.STAMP_FIELD);
        g.drawString(this.font, text, x + 1, y + 1, PlateInk.STAMP_LIT, false);
        g.drawString(this.font, text, x, y, PlateInk.STAMP_INK, false);
    }

    private void engraveSeq(GuiGraphics g, FormattedCharSequence text, int x, int y, int ink) {
        g.drawString(this.font, text, x + 1, y + 1, PlateInk.ENGRAVE_LIT, false);
        g.drawString(this.font, text, x, y, ink, false);
    }

    private static void right(GuiGraphics g, String text, int rightX, int y, int c) {
        GlassFont.draw(g, text, rightX - GlassFont.width(text), y, c, 1);
    }

    private static void lens(GuiGraphics g, int x, int y, PlateInk.Ink ink, boolean lit) {
        final float c = (LENS - 1) / 2.0f;
        g.drawManaged(() -> {
            for (int j = -2; j < LENS + 2; j++) {
                for (int i = -2; i < LENS + 2; i++) {
                    final float dx = i - c;
                    final float dy = j - c;
                    final double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist > 6.6) {
                        if (lit && dist <= 8.4) {
                            px(g, x + i, y + j, (ink.main() & 0x00FFFFFF) | ((dist < 7.6 ? 0x46 : 0x22) << 24));
                        }
                        continue;
                    }
                    if (dist > 5.4) {
                        px(g, x + i, y + j, dx + dy < -1 ? 0xFF1A1D20 : dx + dy > 1 ? 0xFF5A6167 : 0xFF2B3035);
                        continue;
                    }
                    int col = lit ? (dist < 2.2 ? ink.bright() : dist > 4.2 ? ink.mid() : ink.main())
                            : (dist > 4.2 ? ink.faint() : ink.dim());
                    if ((dx == -2 && dy == -3) || (dx == -3 && dy == -2) || (dx == -2 && dy == -2)) {
                        col = lit ? 0xFFFFFFFF : 0xFF5A6167;
                    }
                    px(g, x + i, y + j, col);
                }
            }
        });
    }

    private static void drawResetButton(GuiGraphics g, boolean pressed, boolean hot) {
        final float c = (RESET_D - 1) / 2.0f;
        final int off = pressed ? 1 : 0;
        g.drawManaged(() -> {
            for (int j = 0; j < RESET_D; j++) {
                for (int i = 0; i < RESET_D; i++) {
                    final float dx = i - c;
                    final float dy = j - c;
                    final double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist > 10.0) {
                        continue;
                    }
                    if (dist > 9.1) {
                        px(g, RESET_X + i, RESET_Y + j, PlateInk.RIM_OUT);
                    } else if (dist > 7.4) {
                        px(g, RESET_X + i, RESET_Y + j, dx + dy < -2 ? 0xFF9AA1A7 : dx + dy > 2 ? 0xFF3A4046 : 0xFF6B7278);
                    }
                }
            }
            for (int j = 0; j < RESET_D; j++) {
                for (int i = 0; i < RESET_D; i++) {
                    final float dx = i - c - off;
                    final float dy = j - c - off;
                    final double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist > 6.9) {
                        continue;
                    }
                    final float shade = (-dx - dy) / 14.0f;
                    int col = pressed ? 0xFFA82A18 : hot ? 0xFFDE3A24 : 0xFFC8321E;
                    if (shade > 0.28f) {
                        col = pressed ? 0xFFC8321E : 0xFFE8604A;
                    }
                    if (shade > 0.55f && dist < 5.5) {
                        col = pressed ? 0xFFE0604A : 0xFFF79A86;
                    }
                    if (shade < -0.25f) {
                        col = pressed ? 0xFF6E140A : 0xFF8E1E12;
                    }
                    if (dist > 5.9) {
                        col = dx + dy < 0 ? (pressed ? 0xFF8E1E12 : 0xFFB02A18) : 0xFF5A1209;
                    }
                    px(g, RESET_X + i, RESET_Y + j, col);
                }
            }
        });
    }

    private void drawFlag(GuiGraphics g) {
        this.plate.cut(g, FLAG_X, FLAG_Y, FLAG_W, FLAG_H, FLAG_FLOOR);
        g.fill(FLAG_X + 1, FLAG_Y + 1, FLAG_X + FLAG_W - 1, FLAG_Y + 2, 0xFF3A4046);
        g.fill(FLAG_X + 2, FLAG_Y + FLAG_H - 3, FLAG_X + FLAG_W - 2, FLAG_Y + FLAG_H - 2, 0xFF15181B);
        final int ih = FLAG_H - 2;
        final int drop = Math.round(ih * this.flagT);
        if (drop <= 0) {
            return;
        }
        g.drawManaged(() -> {
            for (int j = 0; j < drop; j++) {
                for (int i = 0; i < FLAG_W - 2; i++) {
                    final int band = Math.floorMod((i + j + (ih - drop)) / 3, 2);
                    px(g, FLAG_X + 1 + i, FLAG_Y + 1 + j, band == 1 ? 0xFFE9E1CF : 0xFFC8321E);
                }
            }
            g.fill(FLAG_X + 1, FLAG_Y + drop, FLAG_X + FLAG_W - 1, FLAG_Y + 1 + drop, 0xFF5A1209);
        });
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private String trim(String text, int maxW) {
        if (this.font.width(text) <= maxW) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, maxW - this.font.width("...")) + "...";
    }

    private static String glassFit(String text, int maxW) {
        final int chars = Math.max(0, (maxW + 1) / GlassFont.PITCH);
        return text.length() <= chars ? text : text.substring(0, chars);
    }

    private static String shortAgo(int ticks) {
        if (ticks < 1200) {
            return tr(K + "time.seconds", ticks / 20);
        }
        if (ticks < 72000) {
            return tr(K + "time.minutes", ticks / 1200);
        }
        return tr(K + "time.hours", ticks / 72000);
    }
}
