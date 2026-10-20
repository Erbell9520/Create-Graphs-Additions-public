package com.createstats.gui;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.alarm.annunciator.AnnunciatorEvent;
import com.createstats.alarm.annunciator.AnnunciatorSettings;
import com.createstats.alarm.annunciator.AnnunciatorState;
import com.createstats.alarm.annunciator.LampPattern;
import com.createstats.alarm.annunciator.RuleBinding;
import com.createstats.alarm.annunciator.RuleComparator;
import com.createstats.alarm.annunciator.RuleFault;
import com.createstats.alarm.annunciator.RuleMeasure;
import com.createstats.alarm.annunciator.RuleStatus;
import com.createstats.alarm.annunciator.ToggleMode;
import com.createstats.alarm.annunciator.WatchRule;
import com.createstats.antenna.AntennaTier;
import com.createstats.blockentity.AnnunciatorBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.MetricContract;
import com.createstats.gui.annunciator.SourceCatalog;
import com.createstats.gui.pdu.PduCard;
import com.createstats.gui.pdu.PduChip;
import com.createstats.gui.pdu.PduGauge;
import com.createstats.gui.pdu.PduIcons;
import com.createstats.gui.pdu.PduRail;
import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduSearchField;
import com.createstats.gui.pdu.PduSegmented;
import com.createstats.gui.pdu.PduStatusLine;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.network.AnnunciatorConfigPacket;
import com.createstats.network.AnnunciatorControlPacket;
import com.createstats.power.client.FeStatusWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AnnunciatorScreen extends Screen {
    private static final int IDEAL_W = 478;
    private static final int IDEAL_H = 348;
    private static final int HEADER_H = 24;
    private static final int FOOTER_H = 20;
    private static final int RAIL_W = 26;
    private static final int ROSTER_W = 200;
    private static final int ROW_H = 19;
    private static final int SEG_H = 13;

    private static final int CHIP_ROW = 14;

    private static final int NO_DATA = 0xFF7A6BC4;

    private static final int ACT_ARM = 1;
    private static final int ACT_SELECT = 2;
    private static final int ACT_LATCH = 3;
    private static final int ACT_OPEN_PICKER = 4;
    private static final int ACT_PICK = 5;
    private static final int ACT_CLOSE_PICKER = 6;
    private static final int ACT_THRESHOLD_STEP = 7;
    private static final int ACT_DEADBAND_STEP = 8;
    private static final int ACT_ADD_RULE = 9;
    private static final int ACT_DELETE_RULE = 10;
    private static final int ACT_DUPLICATE_RULE = 11;
    private static final int ACT_MOVE_RULE = 12;
    private static final int ACT_ACK = 13;
    private static final int ACT_RESET = 14;
    private static final int ACT_TOGGLE = 15;
    private static final int ACT_APPLY = 16;
    private static final int ACT_DISCARD = 17;
    private static final int ACT_IMPORT = 18;
    private static final int ACT_HORN_PICK = 19;
    private static final int ACT_HORN_PREVIEW = 20;
    private static final int ACT_VOLUME = 21;
    private static final int ACT_PITCH = 22;
    private static final int ACT_INTERVAL = 23;
    private static final int ACT_HEAR_RANGE = 24;
    private static final int ACT_HORN_AUDIENCE = 25;
    private static final int ACT_PARTICLE_PICK = 26;
    private static final int ACT_PARTICLE_COUNT = 27;
    private static final int ACT_HUD_TOGGLE = 28;
    private static final int ACT_HUD_AUDIENCE = 29;
    private static final int ACT_HUD_RANGE = 30;
    private static final int ACT_REDSTONE = 31;
    private static final int ACT_SCAN_RADIUS = 32;
    private static final int ACT_CLEAR_LOG = 33;

    private enum Tab {
        WATCH, SOUND, SIGNAL, DEVICE, LOG
    }

    private final AnnunciatorBE device;
    private final AnnunciatorSettings settings;
    private final List<WatchRule> draft = new ArrayList<>();

    private final PduRail rail = new PduRail(
            new PduRail.Item(PduIcons.LOADS, "Watch rules"),
            new PduRail.Item(PduIcons.FLOW, "Sound"),
            new PduRail.Item(PduIcons.WIRING, "Signal"),
            new PduRail.Item(PduIcons.OVERVIEW, "Device"),
            new PduRail.Item(PduIcons.HISTORY, "Log"));

    private Tab tab = Tab.WATCH;
    private PduRect frame = new PduRect(0, 0, 0, 0);
    private PduRect content = new PduRect(0, 0, 0, 0);
    private int selected = -1;

    private boolean dirty;

    private String importNote = "";

    private EditBox nameBox;
    private EditBox thresholdBox;
    private EditBox deadbandBox;
    private EditBox messageBox;

    private boolean syncing;

    private final PduSegmented comparatorSeg = new PduSegmented("≥ ABOVE", "≤ BELOW", "≈ EQUALS");
    private final PduSegmented measureSeg = new PduSegmented("LEVEL", "RATE");
    private final PduSegmented severitySeg = new PduSegmented("NOTICE", "WARNING", "CRITICAL");

    private boolean pickerOpen;
    private int pickerScroll;
    private List<SourceCatalog.Option> catalog = List.of();
    private List<SourceCatalog.Option> pickerRows = List.of();
    private final PduSearchField pickerSearch =
            new PduSearchField("Search sources");
    private String lastQuery = "";

    private int logScroll;

    private int dragging;

    private final List<Hit> hits = new ArrayList<>();
    private final List<Component> railTooltip = new ArrayList<>();
    private Component tooltip;
    private int mouseX;
    private int mouseY;

    private record Hit(PduRect rect, int action, int value) {
    }

    public AnnunciatorScreen(AnnunciatorBE device) {
        super(Component.translatable("gui.create_stats.annunciator.title"));
        this.device = device;

        this.settings = device.getSettings().copy();
        for (WatchRule rule : device.getRules()) {
            this.draft.add(rule.copy());
        }
        if (!this.draft.isEmpty()) {
            this.selected = 0;
        }
    }

    @Override
    protected void init() {
        super.init();
        final int w = Math.min(IDEAL_W, this.width - 16);
        final int h = Math.min(IDEAL_H, this.height - 16);
        this.frame = new PduRect((this.width - w) / 2, (this.height - h) / 2, w, h);
        this.content = new PduRect(
                this.frame.x() + RAIL_W + PduTheme.GAP,
                this.frame.y() + HEADER_H + PduTheme.GAP,
                this.frame.w() - RAIL_W - PduTheme.GAP * 2 - 1,
                this.frame.h() - HEADER_H - FOOTER_H - PduTheme.GAP * 2);

        this.nameBox = this.addBox(28, Component.translatable("gui.create_stats.annunciator.label_hint"));
        this.nameBox.setResponder(text -> this.onEdit(() -> {
            if (this.current() != null) {
                this.current().setLabel(text);
            }
        }));
        this.thresholdBox = this.addBox(12, Component.literal("0"));
        this.thresholdBox.setFilter(AnnunciatorScreen::numeric);
        this.thresholdBox.setResponder(text -> this.onEdit(() -> {
            if (this.current() != null) {
                parse(text).ifPresent(v -> this.current().setThreshold((float) v));
            }
        }));
        this.deadbandBox = this.addBox(12, Component.literal("0"));
        this.deadbandBox.setFilter(AnnunciatorScreen::numeric);
        this.deadbandBox.setResponder(text -> this.onEdit(() -> {
            if (this.current() != null) {
                parse(text).ifPresent(v -> this.current().setDeadband((float) v));
            }
        }));
        this.messageBox = this.addBox(96,
                Component.translatable("gui.create_stats.annunciator.message_hint"));
        this.messageBox.setResponder(text -> this.onEdit(() -> this.settings.setMessage(text)));

        this.syncEditors();
    }

    private EditBox addBox(int maxLength, Component hint) {
        final EditBox box = new EditBox(this.font, 0, -100, 40, 12, Component.empty());
        box.setMaxLength(maxLength);
        box.setHint(hint);
        box.setBordered(true);
        this.addRenderableWidget(box);
        return box;
    }

    private void onEdit(Runnable change) {
        if (this.syncing) {
            return;
        }
        change.run();
        this.dirty = true;

        this.importNote = "";
    }

    private static boolean numeric(String text) {
        return text.isEmpty() || text.matches("-?[0-9]*\\.?[0-9]*");
    }

    private static java.util.OptionalDouble parse(String text) {
        try {
            return java.util.OptionalDouble.of(Double.parseDouble(text));
        } catch (NumberFormatException ignored) {
            return java.util.OptionalDouble.empty();
        }
    }

    private WatchRule current() {
        return this.selected >= 0 && this.selected < this.draft.size()
                ? this.draft.get(this.selected) : null;
    }

    private void syncEditors() {
        final WatchRule rule = this.current();
        this.syncing = true;
        final boolean has = rule != null;
        this.nameBox.setEditable(has);
        this.thresholdBox.setEditable(has);
        this.deadbandBox.setEditable(has);
        this.nameBox.setValue(has ? rule.getRawLabel() : "");
        this.thresholdBox.setValue(has ? trim(rule.getThreshold()) : "");
        this.deadbandBox.setValue(has ? trim(rule.getDeadband()) : "");
        this.messageBox.setValue(this.settings.getMessage());
        if (has) {
            this.comparatorSeg.setSelected(rule.getComparator().ordinal());
            this.measureSeg.setSelected(rule.getMeasure().ordinal());
            this.severitySeg.setSelected(Math.max(0, rule.getSeverity().ordinal() - 1));
        }
        this.syncing = false;
    }

    private static String trim(float value) {
        return Math.abs(value - Math.round(value)) < 0.005f
                ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
    }

    private void hideEditors() {
        this.nameBox.visible = false;
        this.thresholdBox.visible = false;
        this.deadbandBox.visible = false;
        this.messageBox.visible = false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        this.mouseX = mx;
        this.mouseY = my;
        this.hits.clear();
        this.railTooltip.clear();
        this.tooltip = null;
        this.hideEditors();

        g.fill(0, 0, this.width, this.height, 0xB8000000);
        g.fill(this.frame.x(), this.frame.y(), this.frame.right(), this.frame.bottom(), PduTheme.BG_DEEP);
        PduTheme.outline(g, this.frame.x(), this.frame.y(), this.frame.w(), this.frame.h(),
                PduTheme.STROKE);

        this.drawHeader(g);
        this.rail.setActive(this.tab.ordinal());
        this.rail.draw(g, new PduRect(this.frame.x() + 1, this.frame.y() + HEADER_H, RAIL_W,
                this.frame.h() - HEADER_H - FOOTER_H), mx, my, partialTick, this.railTooltip);

        if (this.pickerOpen) {
            this.drawPicker(g);
        } else {
            switch (this.tab) {
                case WATCH -> this.drawWatchTab(g);
                case SOUND -> this.drawSoundTab(g);
                case SIGNAL -> this.drawSignalTab(g);
                case DEVICE -> this.drawDeviceTab(g);
                case LOG -> this.drawLogTab(g);
            }
        }
        this.drawFooter(g);

        super.render(g, mx, my, partialTick);
        if (this.tooltip != null) {
            g.renderTooltip(this.font, this.font.split(this.tooltip, 200), mx, my);
        } else if (!this.railTooltip.isEmpty()) {
            g.renderTooltip(this.font, this.railTooltip.get(0), mx, my);
        }
    }

    private void drawHeader(GuiGraphics g) {
        final boolean wired = this.device.variant().isWired();
        final int y = this.frame.y();
        g.fill(this.frame.x() + 1, y + 1, this.frame.right() - 1, y + HEADER_H, PduTheme.BG_PANEL);
        g.fill(this.frame.x() + 1, y + HEADER_H - 1, this.frame.right() - 1, y + HEADER_H,
                PduTheme.STROKE);
        g.drawString(this.font, Component.translatable(wired
                        ? "block.create_stats.klaxon_alarm" : "block.create_stats.sentry_alarm"),
                this.frame.x() + PduTheme.PAD, y + 8, PduTheme.TEXT, false);

        final AnnunciatorState state = this.device.getDeviceState();
        int right = this.frame.right() - PduTheme.PAD;

        final String stateWord = Component.translatable("gui.create_stats.annunciator.state."
                + state.getSerializedName()).getString();
        final int stateW = PduChip.width(this.font, stateWord);
        right -= stateW;
        final int lampTint = state == AnnunciatorState.NORMAL
                ? PduTheme.OK : severityColour(this.device.getSeverity());
        final boolean lit = state.lamp().isLit(this.gameTime()) || state.lamp() == LampPattern.OFF;
        PduChip.draw(g, this.font, right, y + 6, stateWord,
                lit ? lampTint : PduTheme.withAlpha(lampTint, 0x66), false, false);

        final String reach = wired
                ? Component.translatable(this.device.hasCircuit()
                        ? "gui.create_stats.annunciator.on_circuit"
                        : "gui.create_stats.annunciator.no_circuit").getString()
                : Component.translatable("gui.create_stats.annunciator.scan_radius",
                        this.device.effectiveScanRadius()).getString();
        final int reachW = PduChip.width(this.font, reach);
        right -= reachW + PduTheme.BASE;
        final PduRect reachChip = new PduRect(right, y + 6, reachW, PduChip.HEIGHT);
        PduChip.draw(g, this.font, right, y + 6, reach,
                wired ? (this.device.hasCircuit() ? PduTheme.OK : PduTheme.CRIT) : PduTheme.INFO,
                reachChip.contains(this.mouseX, this.mouseY), false);
        if (reachChip.contains(this.mouseX, this.mouseY)) {
            this.tooltip = Component.translatable(wired
                    ? "gui.create_stats.annunciator.tip.circuit"
                    : "gui.create_stats.annunciator.tip.range");
        }

        final int faults = this.device.getFaultCount();
        if (faults > 0) {
            final String text = Component.translatable("gui.create_stats.annunciator.faults", faults)
                    .getString();
            final int fw = PduChip.width(this.font, text);
            right -= fw + PduTheme.BASE;
            final PduRect chip = new PduRect(right, y + 6, fw, PduChip.HEIGHT);
            PduChip.draw(g, this.font, right, y + 6, text, PduTheme.WARN,
                    chip.contains(this.mouseX, this.mouseY), false);
            if (chip.contains(this.mouseX, this.mouseY)) {
                this.tooltip = Component.translatable("gui.create_stats.annunciator.tip.faults");
            }
        }
    }

    private void drawFooter(GuiGraphics g) {
        final int y = this.frame.bottom() - FOOTER_H;
        g.fill(this.frame.x() + 1, y, this.frame.right() - 1, this.frame.bottom() - 1, PduTheme.BG_PANEL);
        g.fill(this.frame.x() + 1, y, this.frame.right() - 1, y + 1, PduTheme.STROKE);

        final PduRect apply = new PduRect(this.frame.right() - PduTheme.PAD - 56, y + 3, 56, CHIP_ROW);
        final PduRect discard = new PduRect(apply.x() - 62, y + 3, 58, CHIP_ROW);
        this.button(g, apply, Component.translatable("gui.create_stats.annunciator.apply").getString(),
                ACT_APPLY, 0, this.dirty ? PduTheme.OK : PduTheme.TEXT_FAINT, null);
        this.button(g, discard, Component.translatable("gui.create_stats.annunciator.discard").getString(),
                ACT_DISCARD, 0, this.dirty ? PduTheme.WARN : PduTheme.TEXT_FAINT, null);

        final String hint = !this.importNote.isEmpty() ? this.importNote
                : this.dirty
                        ? Component.translatable("gui.create_stats.annunciator.unapplied").getString()
                        : this.footerHint();
        g.drawString(this.font, this.trimTo(hint, discard.x() - this.frame.x() - PduTheme.PAD * 2),
                this.frame.x() + PduTheme.PAD, y + 6,
                this.dirty || !this.importNote.isEmpty() ? PduTheme.WARN : PduTheme.TEXT_DIM, false);
    }

    private String footerHint() {
        return Component.translatable("gui.create_stats.annunciator.hint."
                + this.tab.name().toLowerCase(Locale.ROOT)).getString();
    }

    private void drawWatchTab(GuiGraphics g) {
        final int rosterW = Math.min(ROSTER_W, this.content.w() / 2 + 10);
        final PduRect rosterCard = new PduRect(this.content.x(), this.content.y(), rosterW,
                this.content.h());
        final PduRect inspectCard = new PduRect(rosterCard.right() + PduTheme.GAP, this.content.y(),
                this.content.w() - rosterW - PduTheme.GAP, this.content.h());

        this.drawRoster(g, rosterCard);
        this.drawInspector(g, inspectCard);
    }

    private void drawRoster(GuiGraphics g, PduRect card) {
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.rules").getString()
                        + "  " + this.draft.size() + "/" + WatchRule.MAX_RULES);
        int y = in.y();
        for (int i = 0; i < this.draft.size(); i++) {
            this.drawRuleRow(g, in, y, i);
            y += ROW_H;
        }
        if (this.draft.size() < WatchRule.MAX_RULES) {
            final PduRect add = new PduRect(in.x(), y, in.w(), CHIP_ROW);
            this.button(g, add, Component.translatable("gui.create_stats.annunciator.add_rule")
                    .getString(), ACT_ADD_RULE, 0, PduTheme.TEXT_DIM, null);
            y += CHIP_ROW + PduTheme.BASE;
        }
        if (this.draft.isEmpty()) {
            this.wrapped(g, Component.translatable("gui.create_stats.annunciator.empty_hint")
                    .getString(), in.x(), y + 4, in.w(), 3);
        } else {
            y += 2;
            int outstanding = 0;
            int unreadable = 0;
            int off = 0;
            for (WatchRule rule : this.draft) {
                final RuleStatus status = this.device.statusOf(rule);
                if (!rule.isLive()) {
                    off++;
                } else if (!status.available()) {
                    unreadable++;
                } else if (status.isOutstanding()) {
                    outstanding++;
                }
            }
            y = this.tally(g, in, y, "gui.create_stats.annunciator.tally.outstanding", outstanding,
                    outstanding > 0 ? severityColour(this.device.getSeverity()) : PduTheme.OK);
            y = this.tally(g, in, y, "gui.create_stats.annunciator.tally.unreadable", unreadable,
                    unreadable > 0 ? NO_DATA : PduTheme.TEXT_FAINT);
            y = this.tally(g, in, y, "gui.create_stats.annunciator.tally.off", off,
                    off > 0 ? PduTheme.TEXT_DIM : PduTheme.TEXT_FAINT);
        }

        final PduRect importBtn = new PduRect(in.x(), in.bottom() - CHIP_ROW, in.w(), CHIP_ROW);
        if (importBtn.y() > y && this.draft.size() < WatchRule.MAX_RULES) {
            this.button(g, importBtn, Component.translatable("gui.create_stats.annunciator.import")
                            .getString(), ACT_IMPORT, 0, PduTheme.INFO,
                    "gui.create_stats.annunciator.tip.import");
        }
    }

    private int tally(GuiGraphics g, PduRect in, int y, String key, int count, int tint) {
        if (y + 10 > in.bottom() - CHIP_ROW - PduTheme.BASE) {
            return y;
        }
        g.fill(in.x(), y + 3, in.x() + 4, y + 7, PduTheme.withAlpha(tint, 0xCC));
        g.drawString(this.font, Component.translatable(key, count), in.x() + 9, y,
                count > 0 ? PduTheme.TEXT_DIM : PduTheme.TEXT_FAINT, false);
        return y + 11;
    }

    private void drawRuleRow(GuiGraphics g, PduRect in, int y, int index) {
        final WatchRule rule = this.draft.get(index);
        final RuleStatus status = this.device.statusOf(rule);
        final PduRect row = new PduRect(in.x(), y, in.w(), ROW_H - 2);
        final boolean chosen = index == this.selected;
        final boolean hovered = row.contains(this.mouseX, this.mouseY);
        g.fill(row.x(), row.y(), row.right(), row.bottom(),
                chosen ? PduTheme.BG_RAISED
                        : hovered ? PduTheme.withAlpha(PduTheme.BG_RAISED, 0x88) : PduTheme.BG_PANEL);
        PduTheme.outline(g, row.x(), row.y(), row.w(), row.h(),
                chosen ? PduTheme.STROKE_HOT : PduTheme.STROKE);

        final int tint = ruleTint(rule, status);

        g.fill(row.x(), row.y(), row.x() + 2, row.bottom(), tint);

        final PduRect arm = new PduRect(row.x() + 4, row.y() + 3, 9, 9);
        g.fill(arm.x(), arm.y(), arm.right(), arm.bottom(), PduTheme.withAlpha(tint, 0xCC));
        PduTheme.outline(g, arm.x(), arm.y(), arm.w(), arm.h(), PduTheme.STROKE);
        this.hits.add(new Hit(arm, ACT_ARM, index));
        if (arm.contains(this.mouseX, this.mouseY)) {
            this.tooltip = Component.translatable(rule.isArmed()
                    ? "gui.create_stats.annunciator.tip.disarm"
                    : "gui.create_stats.annunciator.tip.arm");
        }

        final String live = status.available() ? rule.formatValue(status.value())
                : Component.translatable("gui.create_stats.annunciator.no_data").getString();
        final int liveW = this.font.width(live);
        g.drawString(this.font, this.trimTo(rule.getLabel(), row.w() - liveW - 24), row.x() + 17,
                row.y() + 2, rule.isLive() ? PduTheme.TEXT : PduTheme.TEXT_FAINT, false);
        g.drawString(this.font, live, row.right() - 5 - liveW, row.y() + 2,
                status.available() ? PduTheme.TEXT : PduTheme.WARN, false);

        if (!status.available() && hovered && this.tooltip == null) {
            final String why = this.diagnose(rule);
            if (!why.isEmpty()) {
                this.tooltip = Component.literal(why);
            }
        }

        final String test = rule.getComparator().symbol() + " " + rule.formatValue(rule.getThreshold());
        g.drawString(this.font, this.trimTo(test, row.w() - 24), row.x() + 17, row.y() + 10,
                PduTheme.TEXT_DIM, false);
        final String since = this.sinceText(status);
        if (!since.isEmpty()) {
            final int sw = this.font.width(since);
            g.drawString(this.font, since, row.right() - 5 - sw, row.y() + 10,
                    PduTheme.withAlpha(tint, 0xDD), false);
        }
        this.hits.add(new Hit(row, ACT_SELECT, index));
    }

    private String sinceText(RuleStatus status) {
        if (!status.isOutstanding()) {
            return "";
        }
        final String word = shortState(status.state());
        final long elapsed = this.gameTime() - status.sinceTick();
        if (elapsed < 0L || elapsed > 20L * 60L * 60L * 24L) {
            return word;
        }
        return word + " " + PduStatusLine.formatDuration((int) Math.min(Integer.MAX_VALUE, elapsed));
    }

    private void drawInspector(GuiGraphics g, PduRect card) {
        final WatchRule rule = this.current();
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.rule").getString());
        if (rule == null) {
            g.drawString(this.font, Component.translatable("gui.create_stats.annunciator.no_selection"),
                    in.x(), in.y() + 4, PduTheme.TEXT_FAINT, false);
            return;
        }
        int y = in.y();

        g.drawString(this.font, Component.translatable("gui.create_stats.annunciator.name"),
                in.x(), y + 2, PduTheme.TEXT_DIM, false);
        this.placeBox(this.nameBox, in.x() + 40, y, in.w() - 40);
        y += 16;

        g.drawString(this.font, Component.translatable("gui.create_stats.annunciator.watching"),
                in.x(), y + 3, PduTheme.TEXT_DIM, false);
        final PduRect src = new PduRect(in.x() + 40, y, in.w() - 40, SEG_H);
        final boolean bound = rule.getBinding().isBound();
        final boolean srcHover = src.contains(this.mouseX, this.mouseY);
        g.fill(src.x(), src.y(), src.right(), src.bottom(),
                srcHover ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
        PduTheme.outline(g, src.x(), src.y(), src.w(), src.h(),
                bound ? PduTheme.STROKE : PduTheme.withAlpha(PduTheme.WARN, 0xAA));
        final String srcLabel = bound ? this.describeBinding(rule.getBinding())
                : Component.translatable("gui.create_stats.annunciator.pick_source").getString();
        g.drawString(this.font, this.trimTo(srcLabel, src.w() - 16), src.x() + 4, src.y() + 3,
                bound ? PduTheme.TEXT : PduTheme.WARN, false);
        g.drawString(this.font, "▾", src.right() - 9, src.y() + 3, PduTheme.TEXT_DIM, false);
        this.hits.add(new Hit(src, ACT_OPEN_PICKER, this.selected));
        y += SEG_H + 3;

        // diagnosis wins over location

        final String diagnosis = this.diagnose(rule);
        if (!diagnosis.isEmpty()) {
            g.drawString(this.font, this.trimTo(diagnosis, in.w() - 40), in.x() + 40, y,
                    PduTheme.WARN, false);
        } else {
            final String group = SourceCatalog.describeGroup(this.catalogOrBuild(), rule.getBinding());
            if (!group.isEmpty()) {
                g.drawString(this.font, this.trimTo(group, in.w()), in.x() + 40, y,
                        PduTheme.TEXT_FAINT, false);
            }
        }
        y += 11;

        this.comparatorSeg.draw(g, this.font, in.x(), y, SEG_H, this.mouseX, this.mouseY);
        y += SEG_H + 4;

        this.measureSeg.draw(g, this.font, in.x(), y, SEG_H, this.mouseX, this.mouseY);
        final int measureW = this.measureSeg.width(this.font);
        if (new PduRect(in.x(), y, measureW, SEG_H).contains(this.mouseX, this.mouseY)) {
            this.tooltip = Component.translatable("gui.create_stats.annunciator.tip.measure");
        }
        final String latch = Component.translatable(rule.isLatching()
                ? "gui.create_stats.annunciator.latching"
                : "gui.create_stats.annunciator.momentary").getString();
        final int latchW = PduChip.width(this.font, latch);
        final PduRect latchRect = new PduRect(in.x() + measureW + 6, y + 1, latchW, PduChip.HEIGHT);
        PduChip.draw(g, this.font, latchRect.x(), latchRect.y(), latch,
                rule.isLatching() ? PduTheme.ACCENT : PduTheme.TEXT_FAINT,
                latchRect.contains(this.mouseX, this.mouseY), false);
        this.hits.add(new Hit(latchRect, ACT_LATCH, this.selected));
        if (latchRect.contains(this.mouseX, this.mouseY)) {
            this.tooltip = Component.translatable("gui.create_stats.annunciator.tip.latch");
        }
        y += SEG_H + 4;

        this.severitySeg.draw(g, this.font, in.x(), y, SEG_H, this.mouseX, this.mouseY);
        y += SEG_H + 5;

        final String unit = rule.getMetricType() == null ? ""
                : rule.getMetricType().getUnit() + rule.getMeasure().unitSuffix();
        y = this.numberRow(g, in, y, "gui.create_stats.annunciator.threshold", this.thresholdBox,
                unit, ACT_THRESHOLD_STEP, null);
        y = this.numberRow(g, in, y, "gui.create_stats.annunciator.deadband", this.deadbandBox,
                unit, ACT_DEADBAND_STEP, "gui.create_stats.annunciator.tip.deadband");
        y += 3;

        y = this.drawTrend(g, in, y, rule);

        final int third = (in.w() - PduTheme.BASE * 2) / 3;
        final PduRect dup = new PduRect(in.x(), y, third, CHIP_ROW);
        final PduRect move = new PduRect(in.x() + third + PduTheme.BASE, y, third, CHIP_ROW);
        final PduRect del = new PduRect(in.right() - third, y, third, CHIP_ROW);
        this.button(g, dup, Component.translatable("gui.create_stats.annunciator.duplicate").getString(),
                ACT_DUPLICATE_RULE, this.selected,
                this.draft.size() < WatchRule.MAX_RULES ? PduTheme.TEXT_DIM : PduTheme.TEXT_FAINT,
                "gui.create_stats.annunciator.tip.duplicate");
        this.button(g, move, "▲ ▼", ACT_MOVE_RULE, this.selected, PduTheme.TEXT_DIM,
                "gui.create_stats.annunciator.tip.reorder");
        this.button(g, del, Component.translatable("gui.create_stats.annunciator.delete").getString(),
                ACT_DELETE_RULE, this.selected, PduTheme.CRIT, null);
    }

    // device's own fault first, catalog for unscanned drafts
    private String diagnose(WatchRule rule) {
        if (!rule.getBinding().isBound()) {
            return "";
        }
        for (SourceCatalog.Option option : this.catalogOrBuild()) {
            if (option.matches(rule.getBinding()) && !this.reachable(option)) {
                return Component.translatable("gui.create_stats.annunciator.out_of_reach",
                        option.distance(), this.device.effectiveScanRadius()).getString();
            }
        }
        final RuleFault fault = this.device.faultOf(rule);
        return fault.isFault() && rule.isLive()
                ? Component.translatable(fault.translationKey()).getString() : "";
    }

    private int drawTrend(GuiGraphics g, PduRect in, int y, WatchRule rule) {
        final int available = in.bottom() - CHIP_ROW - PduTheme.BASE - y;
        final int h = Math.min(84, available);
        final PduRect box = new PduRect(in.x(), y, in.w(), h);
        if (h < 26) {
            return y;
        }
        final float[] series = this.seriesFor(rule.getBinding());
        g.fill(box.x(), box.y(), box.right(), box.bottom(), PduTheme.BG_DEEP);
        PduTheme.outline(g, box.x(), box.y(), box.w(), box.h(), PduTheme.STROKE);
        if (series == null || series.length < 2) {
            g.drawString(this.font, Component.translatable("gui.create_stats.annunciator.no_trend"),
                    box.x() + 4, box.centerY() - 4, PduTheme.TEXT_FAINT, false);
            return box.bottom() + PduTheme.BASE;
        }

        float lo = rule.getThreshold();
        float hi = rule.getThreshold();
        for (float v : series) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        final float pad = Math.max(0.5f, (hi - lo) * 0.12f);
        lo -= pad;
        hi += pad;
        final float span = Math.max(0.0001f, hi - lo);
        final int tint = severityColour(rule.getSeverity());

        final int plotX = box.x() + 2;
        final int plotY = box.y() + 2;
        final int plotW = box.w() - 4;
        final int plotH = box.h() - 4;
        int prevX = -1;
        int prevY = -1;
        for (int i = 0; i < series.length; i++) {
            final int px = plotX + Math.round((plotW - 1) * (i / (float) (series.length - 1)));
            final int py = plotY + plotH - 1
                    - Math.round((plotH - 1) * Mth.clamp((series[i] - lo) / span, 0.0f, 1.0f));
            if (prevX >= 0) {
                this.plotLine(g, prevX, prevY, px, py, PduTheme.withAlpha(PduTheme.INFO, 0xEE));
            }
            prevX = px;
            prevY = py;
        }
        final int thresholdY = plotY + plotH - 1
                - Math.round((plotH - 1) * Mth.clamp((rule.getThreshold() - lo) / span, 0.0f, 1.0f));
        for (int x = plotX; x < plotX + plotW; x += 3) {
            g.fill(x, thresholdY, x + 2, thresholdY + 1, PduTheme.withAlpha(tint, 0xCC));
        }
        if (prevX >= 0) {
            g.fill(prevX - 1, prevY - 1, prevX + 2, prevY + 2, PduTheme.TEXT);
        }
        return box.bottom() + PduTheme.BASE;
    }

    private void plotLine(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        for (int guard = 0; guard < 512; guard++) {
            g.fill(x, y, x + 1, y + 1, colour);
            if (x == x1 && y == y1) {
                return;
            }
            final int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private int numberRow(GuiGraphics g, PduRect in, int y, String key, EditBox box, String unit,
                          int action, String tipKey) {
        final PduRect label = new PduRect(in.x(), y, 52, 12);
        g.drawString(this.font, Component.translatable(key), label.x(), y + 2, PduTheme.TEXT_DIM, false);
        if (label.contains(this.mouseX, this.mouseY) && tipKey != null) {
            this.tooltip = Component.translatable(tipKey);
        }
        this.placeBox(box, in.x() + 54, y, 48);
        g.drawString(this.font, unit, in.x() + 105, y + 2, PduTheme.TEXT_FAINT, false);
        this.miniButton(g, in.right() - 26, y, "-", action, -1);
        this.miniButton(g, in.right() - 12, y, "+", action, 1);
        return y + 15;
    }

    private void placeBox(EditBox box, int x, int y, int w) {
        box.setPosition(x, y);
        box.setWidth(Math.max(16, w));
        box.setHeight(12);
        box.visible = true;
    }

    private void miniButton(GuiGraphics g, int x, int y, String label, int action, int value) {
        final PduRect rect = new PduRect(x, y, 12, 12);
        final boolean hovered = rect.contains(this.mouseX, this.mouseY);
        g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                hovered ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
        PduTheme.outline(g, rect.x(), rect.y(), rect.w(), rect.h(), PduTheme.STROKE);
        g.drawCenteredString(this.font, label, rect.centerX(), rect.y() + 2, PduTheme.TEXT_DIM);
        this.hits.add(new Hit(rect, action, value));
    }

    private void drawSoundTab(GuiGraphics g) {
        final int leftW = (this.content.w() - PduTheme.GAP) * 55 / 100;

        final int hornH = 180;
        final int audienceH = 84;
        final PduRect hornCard = new PduRect(this.content.x(), this.content.y(), leftW, hornH);
        final int rightX = hornCard.right() + PduTheme.GAP;
        final int rightW = this.content.right() - rightX;
        final PduRect audienceCard = new PduRect(rightX, this.content.y(), rightW, audienceH);
        final PduRect particleCard = new PduRect(rightX, audienceCard.bottom() + PduTheme.GAP, rightW,
                hornCard.bottom() - audienceCard.bottom() - PduTheme.GAP);

        this.drawHornCard(g, hornCard);
        this.drawAudienceCard(g, audienceCard);
        this.drawParticleCard(g, particleCard);
        this.drawBehaviourCard(g, new PduRect(this.content.x(), hornCard.bottom() + PduTheme.GAP,
                this.content.w(), this.content.bottom() - hornCard.bottom() - PduTheme.GAP));
    }

    private void drawBehaviourCard(GuiGraphics g, PduRect card) {
        if (card.h() < 40) {
            return;
        }
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.behaviour").getString());
        int y = in.y();

        final boolean silent = this.settings.getHorn().isSilent();
        final boolean noParticles = this.settings.getParticle() == AnnunciatorSettings.Particle.NONE
                || this.settings.getParticleCount() <= 0;
        final boolean noHud = !this.settings.isHudAlert()
                || this.settings.getHudAudience() == AnnunciatorSettings.Audience.NOBODY;
        final boolean noRedstone = !this.settings.isRedstoneEnabled();
        final boolean mute = this.device.getToggle().isMuted();

        y = this.bullet(g, in, y, silent
                        ? Component.translatable("gui.create_stats.annunciator.b.silent").getString()
                        : Component.translatable("gui.create_stats.annunciator.b.horn",
                                this.settings.getHorn().getDisplayName(),
                                Math.round(this.settings.getVolume() * 100.0f),
                                String.format(Locale.ROOT, "%.1f", this.settings.getHornIntervalSeconds()),
                                this.audienceWord(this.settings.getHornAudience(),
                                        this.settings.getHearingRange())).getString(),
                silent ? PduTheme.TEXT_FAINT : PduTheme.TEXT_DIM);

        y = this.bullet(g, in, y, noParticles
                        ? Component.translatable("gui.create_stats.annunciator.b.no_particles").getString()
                        : Component.translatable("gui.create_stats.annunciator.b.particles",
                                this.settings.getParticleCount(),
                                this.settings.getParticle().getDisplayName()).getString(),
                noParticles ? PduTheme.TEXT_FAINT : PduTheme.TEXT_DIM);

        y = this.bullet(g, in, y, noHud
                        ? Component.translatable("gui.create_stats.annunciator.b.no_hud").getString()
                        : Component.translatable("gui.create_stats.annunciator.b.hud",
                                this.audienceWord(this.settings.getHudAudience(),
                                        this.settings.getHudRange())).getString(),
                noHud ? PduTheme.TEXT_FAINT : PduTheme.TEXT_DIM);

        y = this.bullet(g, in, y, Component.translatable(noRedstone
                        ? "gui.create_stats.annunciator.b.no_redstone"
                        : "gui.create_stats.annunciator.b.redstone").getString(),
                noRedstone ? PduTheme.TEXT_FAINT : PduTheme.TEXT_DIM);

        if (mute) {
            this.bullet(g, in, y, Component.translatable("gui.create_stats.annunciator.b.muted")
                    .getString(), PduTheme.WARN);
        } else if (silent && noParticles && noHud && noRedstone) {
            this.bullet(g, in, y, Component.translatable("gui.create_stats.annunciator.b.nothing")
                    .getString(), PduTheme.CRIT);
        }
    }

    private int bullet(GuiGraphics g, PduRect in, int y, String text, int tint) {
        if (y + 10 > in.bottom()) {
            return y;
        }
        g.fill(in.x(), y + 3, in.x() + 3, y + 6, PduTheme.withAlpha(tint, 0xAA));
        g.drawString(this.font, this.trimTo(text, in.w() - 8), in.x() + 8, y, tint, false);
        return y + 11;
    }

    private String audienceWord(AnnunciatorSettings.Audience audience, int range) {
        return switch (audience) {
            case ALL_PLAYERS -> Component.translatable("gui.create_stats.annunciator.aud.all")
                    .getString();
            case NEARBY_ONLY -> Component.translatable("gui.create_stats.annunciator.aud.nearby",
                    range).getString();
            case OWNER_ONLY -> Component.translatable("gui.create_stats.annunciator.aud.owner")
                    .getString();
            case NOBODY -> Component.translatable("gui.create_stats.annunciator.aud.nobody")
                    .getString();
        };
    }

    private void drawHornCard(GuiGraphics g, PduRect card) {
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.horn").getString());
        int y = in.y();

        final AnnunciatorSettings.Horn[] horns = AnnunciatorSettings.Horn.values();
        final int cols = 2;
        final int cellW = (in.w() - PduTheme.BASE) / cols;
        for (int i = 0; i < horns.length; i++) {
            final PduRect cell = new PduRect(in.x() + (i % cols) * (cellW + PduTheme.BASE),
                    y + (i / cols) * (CHIP_ROW - 1), cellW, PduChip.HEIGHT);
            final boolean on = this.settings.getHorn() == horns[i];
            PduChip.draw(g, this.font, cell.x(), cell.y(), horns[i].getDisplayName(),
                    on ? PduTheme.ACCENT : PduTheme.TEXT_DIM,
                    cell.contains(this.mouseX, this.mouseY), on);
            this.hits.add(new Hit(cell, ACT_HORN_PICK, i));
        }
        y += (horns.length + cols - 1) / cols * (CHIP_ROW - 1) + PduTheme.BASE;

        final PduRect preview = new PduRect(in.x(), y, in.w(), CHIP_ROW);
        this.button(g, preview, Component.translatable("gui.create_stats.annunciator.preview")
                        .getString(), ACT_HORN_PREVIEW, 0,
                this.settings.getHorn().isSilent() ? PduTheme.TEXT_FAINT : PduTheme.INFO,
                "gui.create_stats.annunciator.tip.preview");
        y += CHIP_ROW + PduTheme.GAP;

        y = this.slider(g, in, y, "gui.create_stats.annunciator.volume",
                String.format(Locale.ROOT, "%.0f%%", this.settings.getVolume() * 100.0f),
                this.settings.getVolume(), ACT_VOLUME, null);
        y = this.slider(g, in, y, "gui.create_stats.annunciator.pitch",
                String.format(Locale.ROOT, "%.2f", this.settings.getPitch()),
                (this.settings.getPitch() - 0.5f) / 1.5f, ACT_PITCH,
                "gui.create_stats.annunciator.tip.pitch");

        y = this.stepper(g, in, y, "gui.create_stats.annunciator.interval",
                String.format(Locale.ROOT, "%.1fs", this.settings.getHornIntervalSeconds()),
                ACT_INTERVAL, 10, "gui.create_stats.annunciator.tip.interval");
    }

    private void drawAudienceCard(GuiGraphics g, PduRect card) {
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.who_hears").getString());
        int y = this.audienceRow(g, in, in.y(), this.settings.getHornAudience(), ACT_HORN_AUDIENCE);
        y += PduTheme.BASE;
        final boolean ranged = this.settings.getHornAudience() == AnnunciatorSettings.Audience.NEARBY_ONLY;
        this.stepper(g, in, y, "gui.create_stats.annunciator.hear_range",
                ranged ? this.settings.getHearingRange() + "m"
                        : Component.translatable("gui.create_stats.annunciator.not_used").getString(),
                ACT_HEAR_RANGE, 8, "gui.create_stats.annunciator.tip.hear_range");
    }

    private int audienceRow(GuiGraphics g, PduRect in, int y, AnnunciatorSettings.Audience selected,
                            int action) {
        final AnnunciatorSettings.Audience[] all = AnnunciatorSettings.Audience.values();
        int x = in.x();
        int row = y;
        for (int i = 0; i < all.length; i++) {
            final int w = PduChip.width(this.font, all[i].getDisplayName());
            if (x + w > in.right() && x > in.x()) {
                x = in.x();
                row += PduChip.HEIGHT + 2;
            }
            final PduRect chip = new PduRect(x, row, w, PduChip.HEIGHT);
            final boolean on = selected == all[i];
            PduChip.draw(g, this.font, chip.x(), chip.y(), all[i].getDisplayName(),
                    on ? PduTheme.ACCENT : PduTheme.TEXT_DIM,
                    chip.contains(this.mouseX, this.mouseY), on);
            this.hits.add(new Hit(chip, action, i));
            x += w + PduTheme.BASE;
        }
        return row + PduChip.HEIGHT + 2;
    }

    private void drawParticleCard(GuiGraphics g, PduRect card) {
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.particles").getString());
        final AnnunciatorSettings.Particle[] all = AnnunciatorSettings.Particle.values();
        int x = in.x();
        int row = in.y();
        for (int i = 0; i < all.length; i++) {
            final int w = PduChip.width(this.font, all[i].getDisplayName());
            if (x + w > in.right() && x > in.x()) {
                x = in.x();
                row += PduChip.HEIGHT + 2;
            }
            final PduRect chip = new PduRect(x, row, w, PduChip.HEIGHT);
            final boolean on = this.settings.getParticle() == all[i];
            PduChip.draw(g, this.font, chip.x(), chip.y(), all[i].getDisplayName(),
                    on ? PduTheme.ACCENT : PduTheme.TEXT_DIM,
                    chip.contains(this.mouseX, this.mouseY), on);
            this.hits.add(new Hit(chip, ACT_PARTICLE_PICK, i));
            x += w + PduTheme.BASE;
        }
        row += PduChip.HEIGHT + PduTheme.BASE;
        if (this.settings.getParticle() != AnnunciatorSettings.Particle.NONE) {
            this.stepper(g, in, row, "gui.create_stats.annunciator.count",
                    String.valueOf(this.settings.getParticleCount()), ACT_PARTICLE_COUNT, 1, null);
        }
    }

    private void drawSignalTab(GuiGraphics g) {
        final int leftW = (this.content.w() - PduTheme.GAP) * 55 / 100;
        this.drawHudCard(g, new PduRect(this.content.x(), this.content.y(), leftW, this.content.h()));
        this.drawRedstoneCard(g, new PduRect(this.content.x() + leftW + PduTheme.GAP, this.content.y(),
                this.content.w() - leftW - PduTheme.GAP, this.content.h()));
    }

    private void drawHudCard(GuiGraphics g, PduRect card) {
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.hud").getString());
        int y = in.y();
        final boolean on = this.settings.isHudAlert();
        this.toggleRow(g, new PduRect(in.x(), y, in.w(), CHIP_ROW),
                Component.translatable(on ? "gui.create_stats.annunciator.hud_on"
                        : "gui.create_stats.annunciator.hud_off").getString(),
                ACT_HUD_TOGGLE, on, "gui.create_stats.annunciator.tip.hud");
        y += CHIP_ROW + PduTheme.GAP;

        if (!on) {
            g.drawString(this.font, Component.translatable("gui.create_stats.annunciator.hud_disabled"),
                    in.x(), y, PduTheme.TEXT_FAINT, false);
            return;
        }

        PduCard.drawHeading(g, this.font,
                Component.translatable("gui.create_stats.annunciator.who_sees").getString(),
                in.x(), y);
        y += PduCard.HEADING_H;
        y = this.audienceRow(g, in, y, this.settings.getHudAudience(), ACT_HUD_AUDIENCE);
        y += PduTheme.BASE;

        final boolean ranged = this.settings.getHudAudience() == AnnunciatorSettings.Audience.NEARBY_ONLY;
        y = this.stepper(g, in, y, "gui.create_stats.annunciator.hud_range",
                ranged ? this.settings.getHudRange() + "m"
                        : Component.translatable("gui.create_stats.annunciator.not_used").getString(),
                ACT_HUD_RANGE, 8, null);
        y += PduTheme.BASE;

        PduCard.drawHeading(g, this.font,
                Component.translatable("gui.create_stats.annunciator.message").getString(), in.x(), y);
        y += PduCard.HEADING_H;
        this.placeBox(this.messageBox, in.x(), y, in.w());
        y += 17;
        g.drawString(this.font, Component.translatable("gui.create_stats.annunciator.preview_label"),
                in.x(), y, PduTheme.TEXT_FAINT, false);
        y += 10;
        final PduRect strip = new PduRect(in.x(), y, in.w(), 14);
        g.fill(strip.x(), strip.y(), strip.right(), strip.bottom(), 0xFF000000);
        PduTheme.outline(g, strip.x(), strip.y(), strip.w(), strip.h(), PduTheme.STROKE);
        g.drawCenteredString(this.font, this.hudPreview(), strip.centerX(), strip.y() + 3,
                severityColour(this.previewSeverity()));
    }

    private String hudPreview() {
        final String base = this.settings.getMessage().isEmpty()
                ? (this.device.getCulpritLabel().isEmpty()
                        ? Component.translatable("gui.create_stats.annunciator.sample_rule").getString()
                        : this.device.getCulpritLabel())
                : this.settings.getMessage();
        final String value = this.device.getCulpritValue().isEmpty() ? "18.4%"
                : this.device.getCulpritValue();
        return this.trimTo("⚠ " + base + "  " + value, this.content.w() - 40);
    }

    private AlarmSeverity previewSeverity() {
        return this.device.getSeverity() == AlarmSeverity.NORMAL
                ? AlarmSeverity.CRITICAL : this.device.getSeverity();
    }

    private void drawRedstoneCard(GuiGraphics g, PduRect card) {
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.redstone").getString());
        int y = in.y();
        final boolean on = this.settings.isRedstoneEnabled();
        this.toggleRow(g, new PduRect(in.x(), y, in.w(), CHIP_ROW),
                Component.translatable(on ? "gui.create_stats.annunciator.redstone_on"
                        : "gui.create_stats.annunciator.redstone_off").getString(),
                ACT_REDSTONE, on, "gui.create_stats.annunciator.tip.redstone");
        y += CHIP_ROW + PduTheme.GAP;

        g.drawString(this.font, Component.translatable("gui.create_stats.annunciator.comparator"),
                in.x(), y, PduTheme.TEXT_DIM, false);
        y += 12;
        final AlarmSeverity live = this.device.getSeverity();
        for (AlarmSeverity severity : AlarmSeverity.values()) {
            final int tint = severityColour(severity);
            final boolean isNow = severity == live;
            g.fill(in.x(), y, in.x() + 8, y + 8, PduTheme.withAlpha(tint, isNow ? 0xFF : 0x77));
            PduTheme.outline(g, in.x(), y, 8, 8, isNow ? PduTheme.STROKE_HOT : PduTheme.STROKE);
            final String name = Component.translatable("gui.create_stats.annunciator.severity."
                    + severity.getSerializedName()).getString();
            g.drawString(this.font, name, in.x() + 12, y, isNow ? PduTheme.TEXT : PduTheme.TEXT_DIM,
                    false);
            final String strength = String.valueOf(severity.comparatorLevel());
            g.drawString(this.font, strength, in.right() - this.font.width(strength), y,
                    isNow ? PduTheme.TEXT : PduTheme.TEXT_DIM, false);
            y += 11;
        }
        y += PduTheme.BASE;
        g.drawString(this.font, this.font.plainSubstrByWidth(
                        Component.translatable("gui.create_stats.annunciator.light_note").getString(),
                        in.w()),
                in.x(), y, PduTheme.TEXT_FAINT, false);
    }

    private void drawDeviceTab(GuiGraphics g) {
        final int leftW = (this.content.w() - PduTheme.GAP) * 52 / 100;
        final PduRect left = new PduRect(this.content.x(), this.content.y(), leftW, this.content.h());
        final PduRect right = new PduRect(left.right() + PduTheme.GAP, this.content.y(),
                this.content.w() - leftW - PduTheme.GAP, this.content.h());

        final int reachH = this.device.variant().isWired() ? 66 : 90;
        this.drawReachCard(g, new PduRect(left.x(), left.y(), left.w(), reachH));
        final int powerH = 78;
        this.drawPowerCard(g, new PduRect(left.x(), left.y() + reachH + PduTheme.GAP, left.w(),
                powerH));
        final int coverageY = left.y() + reachH + powerH + PduTheme.GAP * 2;
        this.drawCoverageCard(g, new PduRect(left.x(), coverageY, left.w(),
                left.bottom() - coverageY));

        final int mirrorH = 104;
        this.drawMirrorCard(g, new PduRect(right.x(), right.y(), right.w(), mirrorH));
        this.drawControlsCard(g, new PduRect(right.x(), right.y() + mirrorH + PduTheme.GAP, right.w(),
                right.bottom() - right.y() - mirrorH - PduTheme.GAP));
    }

    private void drawReachCard(GuiGraphics g, PduRect card) {
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.reach").getString());
        if (this.device.variant().isWired()) {
            final boolean found = this.device.hasCircuit();
            g.drawString(this.font, Component.translatable(found
                            ? "gui.create_stats.annunciator.on_circuit"
                            : "gui.create_stats.annunciator.no_circuit"),
                    in.x(), in.y(), found ? PduTheme.OK : PduTheme.CRIT, false);
            this.wrapped(g, Component.translatable("gui.create_stats.annunciator.wired_note")
                    .getString(), in.x(), in.y() + 12, in.w(), 3);
            return;
        }

        int y = this.stepper(g, in, in.y(), "gui.create_stats.annunciator.scan_radius_label",
                this.settings.getScanRadius() + "m", ACT_SCAN_RADIUS, 2,
                "gui.create_stats.annunciator.tip.scan_radius");
        y += 2;

        final AntennaTier tier = this.device.getAntennaTier();
        final String aerial = tier == AntennaTier.NONE
                ? Component.translatable("gui.create_stats.annunciator.no_aerial").getString()
                : Component.translatable("gui.create_stats.annunciator.aerial",
                        tier.getSerializedName(), tier.getRangeMultiplier()).getString();
        g.drawString(this.font, Component.translatable("gui.create_stats.annunciator.aerial_label"),
                in.x(), y, PduTheme.TEXT_DIM, false);
        g.drawString(this.font, this.trimTo(aerial, in.w() - 46), in.x() + 46, y,
                tier == AntennaTier.NONE ? PduTheme.TEXT_FAINT : PduTheme.INFO, false);
        y += 12;

        final int effective = this.device.effectiveScanRadius();
        g.drawString(this.font, Component.translatable("gui.create_stats.annunciator.effective"),
                in.x(), y, PduTheme.TEXT_DIM, false);
        final String eff = effective + "m";
        g.drawString(this.font, eff, in.x() + 46, y, PduTheme.TEXT, false);
        if (effective >= AnnunciatorSettings.MAX_AERIAL_RADIUS) {
            final String capped = Component.translatable("gui.create_stats.annunciator.capped")
                    .getString();
            g.drawString(this.font, capped, in.x() + 46 + this.font.width(eff) + 6, y,
                    PduTheme.WARN, false);
        }
    }

    private void drawPowerCard(GuiGraphics g, PduRect card) {
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.power").getString());

        final var consumer = this.device.feConsumer();
        if (consumer == null) {
            g.drawString(this.font, Component.translatable("gui.create_stats.annunciator.fe_none"),
                    in.x(), in.y(), PduTheme.TEXT_FAINT, false);
            return;
        }
        FeStatusWidget.draw(g, this.font, in.x(), in.y(), in.w(), consumer);
    }

    private void drawCoverageCard(GuiGraphics g, PduRect card) {
        if (card.h() < 40) {
            return;
        }
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.coverage").getString());
        final List<SourceCatalog.Option> all = this.catalogOrBuild();

        int gridFacts = 0;
        int channels = 0;
        int machineMetrics = 0;
        int outOfRange = 0;
        final java.util.Set<String> panels = new java.util.HashSet<>();
        final java.util.Set<String> machines = new java.util.HashSet<>();
        for (SourceCatalog.Option option : all) {
            if (!this.reachable(option)) {
                outOfRange++;
                continue;
            }
            switch (option.binding().kind()) {
                case GRID -> gridFacts++;
                case PANEL_CHANNEL -> {
                    channels++;
                    panels.add(option.group());
                }
                case MACHINE -> {
                    machineMetrics++;
                    machines.add(option.group());
                }
                default -> {
                }
            }
        }

        int y = in.y();
        if (gridFacts > 0) {
            y = this.coverageRow(g, in, y, "gui.create_stats.annunciator.cov.grid", gridFacts,
                    this.device.hasCircuit() ? PduTheme.OK : PduTheme.CRIT);
        }
        y = this.coverageRow(g, in, y, "gui.create_stats.annunciator.cov.panels", panels.size(),
                panels.isEmpty() ? PduTheme.TEXT_FAINT : PduTheme.INFO);
        y = this.coverageRow(g, in, y, "gui.create_stats.annunciator.cov.channels", channels,
                channels == 0 ? PduTheme.TEXT_FAINT : PduTheme.TEXT_DIM);
        y = this.coverageRow(g, in, y, "gui.create_stats.annunciator.cov.machines", machines.size(),
                machines.isEmpty() ? PduTheme.TEXT_FAINT : PduTheme.INFO);
        y = this.coverageRow(g, in, y, "gui.create_stats.annunciator.cov.metrics", machineMetrics,
                machineMetrics == 0 ? PduTheme.TEXT_FAINT : PduTheme.TEXT_DIM);
        if (outOfRange > 0) {
            y = this.coverageRow(g, in, y, "gui.create_stats.annunciator.cov.out_of_range",
                    outOfRange, PduTheme.WARN);
        }
        y += 2;
        final boolean nothing = gridFacts == 0 && channels == 0 && machineMetrics == 0;
        this.wrapped(g, Component.translatable(nothing
                        ? (outOfRange > 0 ? "gui.create_stats.annunciator.cov.all_far"
                                : "gui.create_stats.annunciator.cov.none")
                        : "gui.create_stats.annunciator.cov.note").getString(),
                in.x(), y, in.w(), 3);
    }

    private boolean reachable(SourceCatalog.Option option) {
        return this.device.variant().isWired()
                || option.withinRadius(this.device.effectiveScanRadius());
    }

    private int coverageRow(GuiGraphics g, PduRect in, int y, String key, int count, int tint) {
        if (y + 10 > in.bottom()) {
            return y;
        }
        g.drawString(this.font, Component.translatable(key), in.x(), y, PduTheme.TEXT_DIM, false);
        final String value = String.valueOf(count);
        g.drawString(this.font, value, in.right() - this.font.width(value), y, tint, false);
        return y + 11;
    }

    private void drawMirrorCard(GuiGraphics g, PduRect card) {
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.panel").getString());
        final AnnunciatorState state = this.device.getDeviceState();
        final AlarmSeverity severity = this.device.getSeverity();
        final LampPattern lamp = state.lamp();
        final boolean lit = lamp.isLit(this.gameTime()) || lamp == LampPattern.OFF;
        final int tint = state == AnnunciatorState.NORMAL ? PduTheme.OK : severityColour(severity);

        final PduRect screen = new PduRect(in.x(), in.y(), in.w(), 30);
        g.fill(screen.x(), screen.y(), screen.right(), screen.bottom(),
                lit ? PduTheme.withAlpha(tint, 0x44) : 0xFF0E1A12);
        PduTheme.outline(g, screen.x(), screen.y(), screen.w(), screen.h(),
                PduTheme.withAlpha(tint, 0xAA));
        final String culprit = this.device.getCulpritLabel();
        if (culprit.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable(
                            "gui.create_stats.annunciator.standby").getString(),
                    screen.centerX(), screen.y() + 11, PduTheme.withAlpha(PduTheme.OK, 0xAA));
        } else {
            g.drawCenteredString(this.font, this.trimTo(culprit, screen.w() - 8), screen.centerX(),
                    screen.y() + 5, PduTheme.TEXT);
            g.drawCenteredString(this.font, this.device.getCulpritValue(), screen.centerX(),
                    screen.y() + 17, tint);
        }

        int y = screen.bottom() + PduTheme.BASE;
        final float fill = switch (severity) {
            case NORMAL -> 0.06f;
            case NOTICE -> 0.34f;
            case WARNING -> 0.68f;
            case CRITICAL -> 1.0f;
        };
        PduGauge.drawHorizontal(g, in.x(), y, in.w(), 5, fill, -1.0f,
                severity == AlarmSeverity.NORMAL ? PduTheme.OK : tint);
        y += 5 + PduTheme.BASE;

        final int cols = 4;
        final int tileW = (in.w() - (cols - 1) * 3) / cols;
        for (int i = 0; i < WatchRule.MAX_RULES; i++) {
            final int tx = in.x() + (i % cols) * (tileW + 3);
            final int ty = y + (i / cols) * 12;
            int colour = PduTheme.BG_RAISED;
            if (i < this.draft.size()) {
                colour = ruleTint(this.draft.get(i), this.device.statusOf(this.draft.get(i)));
            }
            final PduRect tile = new PduRect(tx, ty, tileW, 10);
            g.fill(tile.x(), tile.y(), tile.right(), tile.bottom(), PduTheme.withAlpha(colour, 0xCC));
            PduTheme.outline(g, tile.x(), tile.y(), tile.w(), tile.h(), PduTheme.STROKE);
            if (i < this.draft.size()) {
                this.hits.add(new Hit(tile, ACT_SELECT, i));
                if (tile.contains(this.mouseX, this.mouseY)) {
                    this.tooltip = Component.literal(this.draft.get(i).describe());
                }
            }
        }
    }

    private void drawControlsCard(GuiGraphics g, PduRect card) {
        final PduRect in = PduCard.draw(g, this.font, card,
                Component.translatable("gui.create_stats.annunciator.controls").getString());
        final AnnunciatorState state = this.device.getDeviceState();
        int y = in.y();

        final PduRect ack = new PduRect(in.x(), y, in.w(), CHIP_ROW);
        this.button(g, ack, Component.translatable("gui.create_stats.annunciator.ack").getString(),
                ACT_ACK, 0, state == AnnunciatorState.ALARM ? PduTheme.CRIT : PduTheme.TEXT_DIM,
                "gui.create_stats.annunciator.tip.ack");
        y += CHIP_ROW + PduTheme.BASE;

        final PduRect reset = new PduRect(in.x(), y, in.w(), CHIP_ROW);
        this.button(g, reset, Component.translatable("gui.create_stats.annunciator.reset").getString(),
                ACT_RESET, 0, state == AnnunciatorState.RINGBACK ? PduTheme.WARN : PduTheme.TEXT_DIM,
                "gui.create_stats.annunciator.tip.reset");
        y += CHIP_ROW + PduTheme.BASE;

        final ToggleMode mode = this.device.getToggle();
        final PduRect toggle = new PduRect(in.x(), y, in.w(), CHIP_ROW);
        this.button(g, toggle, mode.getSerializedName().toUpperCase(Locale.ROOT), ACT_TOGGLE, 0,
                mode.isMuted() ? PduTheme.WARN : mode.isTest() ? PduTheme.INFO : PduTheme.OK,
                "gui.create_stats.annunciator.tip.mode");
        y += CHIP_ROW + PduTheme.BASE;

        this.wrapped(g, Component.translatable("gui.create_stats.annunciator.controls_note")
                .getString(), in.x(), y, in.w(), 4);
    }

    private void drawLogTab(GuiGraphics g) {
        final PduRect in = PduCard.draw(g, this.font, this.content,
                Component.translatable("gui.create_stats.annunciator.log").getString());
        final List<AnnunciatorEvent> events = this.device.getLog();

        final PduRect clear = new PduRect(this.content.right() - PduTheme.PAD - 52,
                this.content.y() + PduTheme.PAD - 2, 52, PduChip.HEIGHT);
        PduChip.draw(g, this.font, clear.x(), clear.y(),
                Component.translatable("gui.create_stats.annunciator.clear_log").getString(),
                events.isEmpty() ? PduTheme.TEXT_FAINT : PduTheme.TEXT_DIM,
                clear.contains(this.mouseX, this.mouseY), false);
        if (!events.isEmpty()) {
            this.hits.add(new Hit(clear, ACT_CLEAR_LOG, 0));
        }

        if (events.isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.create_stats.annunciator.log_empty"),
                    in.x(), in.y() + 4, PduTheme.TEXT_FAINT, false);
            return;
        }

        final int rowH = 13;
        final int visible = Math.max(1, in.h() / rowH);
        this.logScroll = Mth.clamp(this.logScroll, 0, Math.max(0, events.size() - visible));
        int y = in.y();
        for (int i = this.logScroll; i < events.size() && y + rowH <= in.bottom(); i++) {
            this.drawLogRow(g, in, y, rowH, events.get(i));
            y += rowH;
        }
        if (events.size() > visible) {
            final int trackH = in.h();
            final int barH = Math.max(10, trackH * visible / events.size());
            final int barY = in.y() + (trackH - barH) * this.logScroll
                    / Math.max(1, events.size() - visible);
            g.fill(in.right() - 2, in.y(), in.right(), in.bottom(),
                    PduTheme.withAlpha(PduTheme.STROKE, 0x88));
            g.fill(in.right() - 2, barY, in.right(), barY + barH, PduTheme.ACCENT);
        }
    }

    private void drawLogRow(GuiGraphics g, PduRect in, int y, int rowH, AnnunciatorEvent event) {
        final int tint = eventTint(event);
        g.fill(in.x(), y, in.x() + 2, y + rowH - 2, tint);

        final String word = Component.translatable("gui.create_stats.annunciator.event."
                + event.kind().getSerializedName()).getString();
        final int wordW = 58;
        g.drawString(this.font, this.trimTo(word, wordW - 4), in.x() + 6, y + 2, tint, false);

        final long ago = this.gameTime() - event.tick();
        final String when = ago < 0L ? "" : Component.translatable(
                "gui.create_stats.annunciator.ago",
                PduStatusLine.formatDuration((int) Math.min(Integer.MAX_VALUE, ago))).getString();
        final int whenW = this.font.width(when);
        g.drawString(this.font, when, in.right() - 6 - whenW, y + 2, PduTheme.TEXT_FAINT, false);

        final String detail = event.reading().isEmpty() ? event.label()
                : event.label() + "  " + event.reading();
        g.drawString(this.font, this.trimTo(detail, in.w() - wordW - whenW - 20),
                in.x() + 6 + wordW, y + 2, PduTheme.TEXT, false);
    }

    private static int eventTint(AnnunciatorEvent event) {
        return switch (event.kind()) {
            case TRIP -> severityColour(event.severity());
            case ACK -> PduTheme.INFO;
            case RINGBACK -> PduTheme.WARN;
            case CLEAR -> PduTheme.OK;
            case LOST, RESTORED -> NO_DATA;
        };
    }

    private void drawPicker(GuiGraphics g) {
        final PduRect in = PduCard.draw(g, this.font, this.content,
                Component.translatable("gui.create_stats.annunciator.choose_source").getString());

        final PduRect close = new PduRect(this.content.right() - PduTheme.PAD - 12,
                this.content.y() + PduTheme.PAD - 2, 12, 12);
        final boolean closeHover = close.contains(this.mouseX, this.mouseY);
        g.fill(close.x(), close.y(), close.right(), close.bottom(),
                closeHover ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
        PduTheme.outline(g, close.x(), close.y(), close.w(), close.h(), PduTheme.STROKE);
        g.drawCenteredString(this.font, "✕", close.centerX(), close.y() + 2, PduTheme.TEXT_DIM);
        this.hits.add(new Hit(close, ACT_CLOSE_PICKER, 0));

        int y = in.y();
        this.pickerSearch.draw(g, this.font, in.x(), y, in.w() - 20, this.mouseX, this.mouseY);
        y += PduSearchField.HEIGHT + PduTheme.BASE;

        if (!this.pickerSearch.text().equals(this.lastQuery)) {
            this.lastQuery = this.pickerSearch.text();
            this.pickerRows = SourceCatalog.filter(this.catalog, this.lastQuery);
            this.pickerScroll = 0;
        }

        if (this.device.variant().isWired()) {
            this.wrapped(g, Component.translatable("gui.create_stats.annunciator.wired_picker_note")
                    .getString(), in.x(), y, in.w(), 2);
            y += 20;
        }

        if (this.pickerRows.isEmpty()) {
            g.drawString(this.font, Component.translatable(this.catalog.isEmpty()
                            ? "gui.create_stats.annunciator.no_sources"
                            : "gui.create_stats.annunciator.no_matches"),
                    in.x(), y + 4, PduTheme.TEXT_FAINT, false);
            return;
        }

        final PduRect list = new PduRect(in.x(), y, in.w(), in.bottom() - y);
        final int rowH = 13;
        int drawY = list.y() - this.pickerScroll;
        String group = null;
        final WatchRule rule = this.current();
        for (int i = 0; i < this.pickerRows.size(); i++) {
            final SourceCatalog.Option option = this.pickerRows.get(i);
            if (!option.group().equals(group)) {
                group = option.group();
                if (drawY + 10 > list.y() && drawY < list.bottom()) {
                    PduCard.drawHeading(g, this.font, this.trimTo(group, list.w()), list.x(), drawY + 1);
                }
                drawY += 12;
            }
            if (drawY + rowH > list.y() && drawY < list.bottom()) {
                final PduRect row = new PduRect(list.x(), drawY, list.w(), rowH - 1);
                final boolean bound = rule != null && option.matches(rule.getBinding());
                final boolean hovered = row.contains(this.mouseX, this.mouseY);

                final boolean inReach = this.reachable(option);
                g.fill(row.x(), row.y(), row.right(), row.bottom(),
                        bound ? PduTheme.BG_RAISED : hovered
                                ? PduTheme.withAlpha(PduTheme.BG_RAISED, 0x99) : PduTheme.BG_PANEL);
                PduTheme.outline(g, row.x(), row.y(), row.w(), row.h(),
                        bound ? PduTheme.ACCENT : PduTheme.STROKE);
                final String value = inReach ? option.value()
                        : Component.translatable("gui.create_stats.annunciator.too_far",
                                option.distance()).getString();
                final int vw = this.font.width(value);

                final String note = option.note();
                final int nw = note.isEmpty() ? 0 : this.font.width(note) + 8;
                final boolean showNote = nw > 0 && row.w() - vw - nw - 24 > 40;
                g.drawString(this.font,
                        this.trimTo(option.label(), row.w() - vw - (showNote ? nw : 0) - 16),
                        row.x() + 5, row.y() + 2, inReach ? PduTheme.TEXT : PduTheme.TEXT_FAINT,
                        false);
                if (showNote) {
                    g.drawString(this.font, note, row.right() - 5 - vw - nw, row.y() + 2,
                            PduTheme.INFO, false);
                }
                g.drawString(this.font, value, row.right() - 5 - vw, row.y() + 2,
                        inReach ? PduTheme.TEXT_DIM : PduTheme.WARN, false);
                this.hits.add(new Hit(row, ACT_PICK, i));
                if (hovered && !inReach) {
                    this.tooltip = Component.translatable("gui.create_stats.annunciator.tip.too_far",
                            option.distance(), this.device.effectiveScanRadius());
                }
            }
            drawY += rowH;
        }
        if (drawY > list.bottom()) {
            g.fill(list.x(), list.bottom() - 1, list.right(), list.bottom(),
                    PduTheme.withAlpha(PduTheme.ACCENT, 0x66));
        }
    }

    private int pickerContentHeight() {
        int h = 0;
        String group = null;
        for (SourceCatalog.Option option : this.pickerRows) {
            if (!option.group().equals(group)) {
                group = option.group();
                h += 12;
            }
            h += 13;
        }
        return h;
    }

    private List<SourceCatalog.Option> catalogOrBuild() {
        if (this.catalog.isEmpty()) {
            this.catalog = SourceCatalog.build(
                    this.minecraft == null ? null : this.minecraft.level,
                    this.device.getBlockPos(),
                    this.device.variant().canReadGrid(),
                    this.device.hasCircuit(),

                    SourceCatalog.sweepRadius(this.device.variant().isWired()
                            ? SourceCatalog.SCAN_RADIUS : this.device.effectiveScanRadius()));
        }
        return this.catalog;
    }

    private String describeBinding(RuleBinding binding) {
        return SourceCatalog.describe(this.catalogOrBuild(), binding);
    }

    private float[] seriesFor(RuleBinding binding) {
        for (SourceCatalog.Option option : this.catalogOrBuild()) {
            if (option.matches(binding)) {
                return option.series();
            }
        }
        return null;
    }

    private void button(GuiGraphics g, PduRect rect, String label, int action, int value, int tint,
                        String tipKey) {
        final boolean hovered = rect.contains(this.mouseX, this.mouseY);
        g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                hovered ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
        PduTheme.outline(g, rect.x(), rect.y(), rect.w(), rect.h(), PduTheme.withAlpha(tint, 0x99));
        g.drawCenteredString(this.font, label, rect.centerX(), rect.y() + (rect.h() - 8) / 2, tint);
        this.hits.add(new Hit(rect, action, value));
        if (hovered && tipKey != null) {
            this.tooltip = Component.translatable(tipKey);
        }
    }

    private void toggleRow(GuiGraphics g, PduRect rect, String label, int action, boolean on,
                           String tipKey) {
        final boolean hovered = rect.contains(this.mouseX, this.mouseY);
        g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(),
                hovered ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
        PduTheme.outline(g, rect.x(), rect.y(), rect.w(), rect.h(),
                PduTheme.withAlpha(on ? PduTheme.OK : PduTheme.STROKE_HOT, 0x99));
        final int pipW = 16;
        g.fill(rect.x() + 3, rect.y() + 3, rect.x() + 3 + pipW, rect.bottom() - 3,
                PduTheme.withAlpha(on ? PduTheme.OK : PduTheme.TEXT_FAINT, 0x66));
        g.fill(on ? rect.x() + 3 + pipW - 6 : rect.x() + 4, rect.y() + 4,
                on ? rect.x() + 3 + pipW - 1 : rect.x() + 9, rect.bottom() - 4,
                on ? PduTheme.OK : PduTheme.TEXT_FAINT);
        g.drawString(this.font, label, rect.x() + pipW + 9, rect.y() + (rect.h() - 8) / 2,
                on ? PduTheme.TEXT : PduTheme.TEXT_DIM, false);
        this.hits.add(new Hit(rect, action, 0));
        if (hovered && tipKey != null) {
            this.tooltip = Component.translatable(tipKey);
        }
    }

    private int slider(GuiGraphics g, PduRect in, int y, String key, String valueText, float fraction,
                       int action, String tipKey) {
        g.drawString(this.font, Component.translatable(key), in.x(), y, PduTheme.TEXT_DIM, false);
        final int vw = this.font.width(valueText);
        g.drawString(this.font, valueText, in.right() - vw, y, PduTheme.TEXT, false);
        final PduRect bar = new PduRect(in.x(), y + 10, in.w(), 6);
        g.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), PduTheme.BG_DEEP);
        final int fillW = Math.round((bar.w() - 2) * Mth.clamp(fraction, 0.0f, 1.0f));
        g.fill(bar.x() + 1, bar.y() + 1, bar.x() + 1 + fillW, bar.bottom() - 1,
                PduTheme.withAlpha(PduTheme.INFO, 0xCC));
        PduTheme.outline(g, bar.x(), bar.y(), bar.w(), bar.h(),
                bar.contains(this.mouseX, this.mouseY) ? PduTheme.STROKE_HOT : PduTheme.STROKE);
        final int handle = bar.x() + 1 + fillW;
        g.fill(handle - 1, bar.y() - 2, handle + 2, bar.bottom() + 2, PduTheme.ACCENT);
        this.hits.add(new Hit(bar, action, 0));
        if (bar.contains(this.mouseX, this.mouseY) && tipKey != null) {
            this.tooltip = Component.translatable(tipKey);
        }
        return bar.bottom() + PduTheme.GAP;
    }

    private int stepper(GuiGraphics g, PduRect in, int y, String key, String valueText, int action,
                        int step, String tipKey) {
        final PduRect down = new PduRect(in.x(), y, in.w() / 2, 12);
        final PduRect up = new PduRect(in.x() + in.w() / 2, y, in.w() - in.w() / 2, 12);
        final boolean hoverDown = down.contains(this.mouseX, this.mouseY);
        final boolean hoverUp = up.contains(this.mouseX, this.mouseY);
        final String label = Component.translatable(key).getString();

        g.drawString(this.font, label, in.x(), y + 2,
                hoverDown || hoverUp ? PduTheme.TEXT : PduTheme.TEXT_DIM, false);
        g.drawString(this.font, valueText, in.right() - this.font.width(valueText), y + 2,
                PduTheme.TEXT, false);
        if (hoverDown) {
            g.drawString(this.font, "-", in.x() + this.font.width(label) + 5, y + 2,
                    PduTheme.ACCENT, false);
        }
        if (hoverUp) {
            g.drawString(this.font, "+", in.right() - this.font.width(valueText) - 9, y + 2,
                    PduTheme.ACCENT, false);
        }
        this.hits.add(new Hit(down, action, -step));
        this.hits.add(new Hit(up, action, step));
        if ((hoverDown || hoverUp) && tipKey != null) {
            this.tooltip = Component.translatable(tipKey);
        }
        return y + 15;
    }

    private void wrapped(GuiGraphics g, String text, int x, int y, int w, int maxLines) {
        final List<net.minecraft.util.FormattedCharSequence> lines =
                this.font.split(Component.literal(text), w);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            g.drawString(this.font, lines.get(i), x, y + i * 10, PduTheme.TEXT_FAINT, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }

        final int item = this.rail.itemAt(mx, my);
        if (item >= 0) {
            this.tab = Tab.values()[item];
            this.pickerOpen = false;
            this.importNote = "";
            return true;
        }
        if (this.pickerOpen) {
            if (this.pickerSearch.clickedClear(mx, my)) {
                this.pickerSearch.clear();
                return true;
            }
            if (this.pickerSearch.clicked(mx, my)) {
                this.pickerSearch.setFocused(true);
                return true;
            }
            this.pickerSearch.setFocused(false);
        }

        final WatchRule rule = this.current();
        if (rule != null && this.tab == Tab.WATCH && !this.pickerOpen) {
            final int cmp = this.comparatorSeg.segmentAt(mx, my);
            if (cmp >= 0) {
                this.onEdit(() -> rule.setComparator(RuleComparator.values()[cmp]));
                this.syncEditors();
                return true;
            }
            final int mea = this.measureSeg.segmentAt(mx, my);
            if (mea >= 0) {
                this.onEdit(() -> rule.setMeasure(RuleMeasure.values()[mea]));
                this.syncEditors();
                return true;
            }
            final int sev = this.severitySeg.segmentAt(mx, my);
            if (sev >= 0) {
                this.onEdit(() -> rule.setSeverity(AlarmSeverity.values()[sev + 1]));
                this.syncEditors();
                return true;
            }
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }

        for (int i = this.hits.size() - 1; i >= 0; i--) {
            final Hit hit = this.hits.get(i);
            if (hit.rect().contains(mx, my)) {
                this.act(hit, mx);
                return true;
            }
        }
        return false;
    }

    private void act(Hit hit, double mx) {
        final int index = hit.value();
        final WatchRule rule = index >= 0 && index < this.draft.size() ? this.draft.get(index) : null;
        switch (hit.action()) {
            case ACT_ARM -> {
                if (rule != null) {
                    this.onEdit(() -> rule.setArmed(!rule.isArmed()));
                }
            }
            case ACT_SELECT -> {
                this.selected = index;
                this.tab = Tab.WATCH;
                this.syncEditors();
            }
            case ACT_LATCH -> {
                if (rule != null) {
                    this.onEdit(() -> rule.setLatching(!rule.isLatching()));
                }
            }
            case ACT_OPEN_PICKER -> {
                this.catalogOrBuild();
                this.pickerRows = SourceCatalog.filter(this.catalog, this.pickerSearch.text());
                this.lastQuery = this.pickerSearch.text();
                this.pickerOpen = true;
                this.pickerScroll = 0;
                this.pickerSearch.setFocused(true);
            }
            case ACT_CLOSE_PICKER -> this.closePicker();
            case ACT_PICK -> {
                this.applyBinding(index);
                this.closePicker();
            }
            case ACT_THRESHOLD_STEP -> this.nudge(this.current(), true, hit.value());
            case ACT_DEADBAND_STEP -> this.nudge(this.current(), false, hit.value());
            case ACT_ADD_RULE -> {
                if (this.draft.size() < WatchRule.MAX_RULES) {
                    this.onEdit(() -> this.draft.add(this.freshRule()));
                    this.selected = this.draft.size() - 1;
                    this.syncEditors();
                }
            }
            case ACT_DUPLICATE_RULE -> {
                if (rule != null && this.draft.size() < WatchRule.MAX_RULES) {
                    final WatchRule copy = this.freshRule();
                    copy.setBinding(rule.getBinding());
                    copy.setMetricType(rule.getMetricType());
                    copy.setMeasure(rule.getMeasure());
                    copy.setComparator(rule.getComparator());
                    copy.setThreshold(rule.getThreshold());
                    copy.setDeadband(rule.getDeadband());
                    copy.setSeverity(rule.getSeverity());
                    copy.setLatching(rule.isLatching());
                    copy.setArmed(rule.isArmed());
                    copy.setLabel(rule.getRawLabel());
                    this.onEdit(() -> this.draft.add(index + 1, copy));
                    this.selected = index + 1;
                    this.syncEditors();
                }
            }
            case ACT_MOVE_RULE -> {
                if (rule != null) {
                    final boolean up = mx < hit.rect().centerX();
                    final int target = up ? index - 1 : index + 1;
                    if (target >= 0 && target < this.draft.size()) {
                        this.onEdit(() -> {
                            this.draft.remove(index);
                            this.draft.add(target, rule);
                        });
                        this.selected = target;
                        this.syncEditors();
                    }
                }
            }
            case ACT_DELETE_RULE -> {
                if (rule != null) {
                    this.onEdit(() -> this.draft.remove(index));
                    this.selected = Math.min(this.selected, this.draft.size() - 1);
                    this.syncEditors();
                }
            }
            case ACT_ACK -> this.sendControl(AnnunciatorControlPacket.Control.ACKNOWLEDGE);
            case ACT_RESET -> this.sendControl(AnnunciatorControlPacket.Control.RESET);
            case ACT_TOGGLE -> this.sendControl(AnnunciatorControlPacket.Control.CYCLE_TOGGLE);
            case ACT_CLEAR_LOG -> {
                this.sendControl(AnnunciatorControlPacket.Control.CLEAR_LOG);
                this.logScroll = 0;
            }
            case ACT_IMPORT -> this.importLegacyAlerts();
            case ACT_APPLY -> this.applyAndClose();
            case ACT_DISCARD -> this.onClose();
            case ACT_HORN_PICK -> this.onEdit(() ->
                    this.settings.setHorn(AnnunciatorSettings.Horn.values()[hit.value()]));
            case ACT_HORN_PREVIEW -> this.previewHorn();
            case ACT_VOLUME -> this.applySliderFromMouse(ACT_VOLUME, hit.rect(), mx);
            case ACT_PITCH -> this.applySliderFromMouse(ACT_PITCH, hit.rect(), mx);
            case ACT_INTERVAL -> this.onEdit(() -> this.settings.setHornIntervalTicks(
                    this.settings.getHornIntervalTicks() + hit.value() * 2));
            case ACT_HEAR_RANGE -> this.onEdit(() -> this.settings.setHearingRange(
                    this.settings.getHearingRange() + hit.value()));
            case ACT_HORN_AUDIENCE -> this.onEdit(() -> this.settings.setHornAudience(
                    AnnunciatorSettings.Audience.values()[hit.value()]));
            case ACT_PARTICLE_PICK -> this.onEdit(() -> this.settings.setParticle(
                    AnnunciatorSettings.Particle.values()[hit.value()]));
            case ACT_PARTICLE_COUNT -> this.onEdit(() -> this.settings.setParticleCount(
                    this.settings.getParticleCount() + hit.value()));
            case ACT_HUD_TOGGLE -> this.onEdit(() ->
                    this.settings.setHudAlert(!this.settings.isHudAlert()));
            case ACT_HUD_AUDIENCE -> this.onEdit(() -> this.settings.setHudAudience(
                    AnnunciatorSettings.Audience.values()[hit.value()]));
            case ACT_HUD_RANGE -> this.onEdit(() -> this.settings.setHudRange(
                    this.settings.getHudRange() + hit.value()));
            case ACT_REDSTONE -> this.onEdit(() ->
                    this.settings.setRedstoneEnabled(!this.settings.isRedstoneEnabled()));
            case ACT_SCAN_RADIUS -> this.onEdit(() -> this.settings.setScanRadius(
                    this.settings.getScanRadius() + hit.value()));
            default -> {
            }
        }
    }

    private void closePicker() {
        this.pickerOpen = false;
        this.pickerSearch.setFocused(false);
        this.syncEditors();
    }

    private void previewHorn() {
        if (this.minecraft == null || this.minecraft.level == null
                || this.settings.getHorn().isSilent()) {
            return;
        }
        final net.minecraft.core.BlockPos pos = this.device.getBlockPos();
        this.minecraft.level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                this.settings.getHorn().soundEvent(), SoundSource.BLOCKS,
                this.settings.getVolume(), this.settings.getPitch(), false);
    }

    private void applySliderFromMouse(int which, PduRect bar, double mx) {
        final float t = Mth.clamp((float) (mx - bar.x()) / Math.max(1, bar.w()), 0.0f, 1.0f);
        this.onEdit(() -> {
            if (which == ACT_VOLUME) {
                this.settings.setVolume(t);
            } else {
                this.settings.setPitch(0.5f + t * 1.5f);
            }
        });
        this.dragging = which;
    }

    private void applyBinding(int optionIndex) {
        final WatchRule rule = this.current();
        if (rule == null || optionIndex < 0 || optionIndex >= this.pickerRows.size()) {
            return;
        }
        final SourceCatalog.Option option = this.pickerRows.get(optionIndex);
        this.onEdit(() -> {
            rule.setBinding(option.binding());

            if (rule.getThreshold() == 0.0f && rule.getDeadband() == 0.0f) {
                rule.applyDefaultsFor(option.metric(), 0.0f);
            } else {
                rule.setMetricType(option.metric());
            }
            if (rule.getRawLabel().isEmpty()) {
                rule.setLabel(option.label());
            }
            rule.setArmed(true);
        });
    }

    private void nudge(WatchRule rule, boolean threshold, int direction) {
        if (rule == null) {
            return;
        }
        final float[] range = MetricContract.fixedRange(rule.getMetricType());
        final float base = threshold ? rule.getThreshold() : rule.getDeadband();
        final float step = range != null
                ? Math.max(0.01f, Math.abs(range[1] - range[0]) * 0.01f)
                : Math.max(1.0f, Math.abs(base) * 0.05f);
        this.onEdit(() -> {
            if (threshold) {
                rule.setThreshold(base + step * direction);
            } else {
                rule.setDeadband(base + step * direction);
            }
        });
        this.syncEditors();
    }

    // explicit import, not behind your back. and only what the device can actually read
    private void importLegacyAlerts() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        int added = 0;
        int skipped = 0;
        int full = 0;
        for (SourceCatalog.Option option : this.catalogOrBuild()) {
            if (option.binding().kind() != RuleBinding.Kind.PANEL_CHANNEL) {
                continue;
            }
            final StatsDisplayPanelBE.DataChannel channel = this.channelOf(option.binding());
            if (channel == null || !channel.isAlertEnabled()) {
                continue;
            }
            if (!this.reachable(option)) {
                skipped++;
                continue;
            }
            if (this.draft.size() >= WatchRule.MAX_RULES) {
                full++;
                continue;
            }
            final WatchRule imported = this.freshRule();
            imported.setBinding(option.binding());
            imported.setLabel(channel.getLabel() == null || channel.getLabel().isEmpty()
                    ? channel.getId() : channel.getLabel());
            imported.setComparator(channel.isAlertAbove()
                    ? RuleComparator.ABOVE : RuleComparator.BELOW);
            imported.setThreshold(channel.getAlertThreshold());
            imported.setMeasure(RuleMeasure.LEVEL);
            if (channel.getStore() != null) {
                imported.setMetricType(channel.getStore().getMetricType());
            }

            final float[] range = MetricContract.fixedRange(imported.getMetricType());
            imported.setDeadband(range != null ? Math.abs(range[1] - range[0]) * 0.02f
                    : Math.abs(imported.getThreshold()) * 0.02f);
            imported.setArmed(true);
            this.draft.add(imported);
            added++;
        }
        this.importNote = importNote(added, skipped, full);
        if (added > 0) {
            this.dirty = true;
            this.selected = this.draft.size() - added;
            this.syncEditors();
        }
    }

    private static String importNote(int added, int skipped, int full) {
        if (added == 0 && skipped == 0 && full == 0) {
            return Component.translatable("gui.create_stats.annunciator.import.none").getString();
        }
        final StringBuilder note = new StringBuilder(
                Component.translatable("gui.create_stats.annunciator.import.added", added).getString());
        if (skipped > 0) {
            note.append("  ").append(Component.translatable(
                    "gui.create_stats.annunciator.import.too_far", skipped).getString());
        }
        if (full > 0) {
            note.append("  ").append(Component.translatable(
                    "gui.create_stats.annunciator.import.full", full).getString());
        }
        return note.toString();
    }

    private StatsDisplayPanelBE.DataChannel channelOf(RuleBinding binding) {
        if (this.minecraft == null || this.minecraft.level == null || binding.target() == null) {
            return null;
        }
        return this.minecraft.level.getBlockEntity(binding.target())
                instanceof StatsDisplayPanelBE panel ? panel.getChannel(binding.key()) : null;
    }

    private WatchRule freshRule() {
        WatchRule rule = this.device.createRule();
        for (int guard = 0; guard < WatchRule.MAX_RULES * 4 && this.draftHasId(rule.getId()); guard++) {
            rule = this.device.createRule();
        }
        return rule;
    }

    private boolean draftHasId(String id) {
        for (WatchRule rule : this.draft) {
            if (rule.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.dragging == ACT_VOLUME || this.dragging == ACT_PITCH) {
            for (Hit hit : this.hits) {
                if (hit.action() == this.dragging) {
                    this.applySliderFromMouse(this.dragging, hit.rect(), mx);
                    return true;
                }
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (this.dragging != 0) {
            this.dragging = 0;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (this.pickerOpen && this.content.contains(mx, my)) {
            final int max = Math.max(0, this.pickerContentHeight() - (this.content.h() - 48));
            this.pickerScroll = Mth.clamp(this.pickerScroll - (int) (scrollY * 16), 0, max);
            return true;
        }
        if (this.tab == Tab.LOG && this.content.contains(mx, my)) {
            this.logScroll = Math.max(0, this.logScroll - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.pickerOpen && this.pickerSearch.isFocused()) {
            return this.pickerSearch.charTyped(codePoint);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.pickerOpen) {
            if (keyCode == 256) {
                this.closePicker();
                return true;
            }
            if (this.pickerSearch.isFocused() && this.pickerSearch.keyPressed(keyCode)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendControl(AnnunciatorControlPacket.Control control) {
        PacketDistributor.sendToServer(new AnnunciatorControlPacket(this.device.getBlockPos(), control));
    }

    private void applyAndClose() {
        PacketDistributor.sendToServer(new AnnunciatorConfigPacket(
                this.device.getBlockPos(), this.settings, this.draft));
        this.dirty = false;
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private long gameTime() {
        return this.minecraft == null || this.minecraft.level == null
                ? 0L : this.minecraft.level.getGameTime();
    }

    private static int ruleTint(WatchRule rule, RuleStatus status) {
        if (!rule.isLive()) {
            return PduTheme.TEXT_FAINT;
        }
        if (!status.available()) {
            return NO_DATA;
        }
        return severityColour(status.effectiveSeverity());
    }

    private static int severityColour(AlarmSeverity severity) {
        return switch (severity) {
            case NORMAL -> PduTheme.OK;
            case NOTICE -> PduTheme.INFO;
            case WARNING -> PduTheme.WARN;
            case CRITICAL -> PduTheme.CRIT;
        };
    }

    private static String shortState(AnnunciatorState state) {
        return switch (state) {
            case NORMAL -> "";
            case ALARM -> "ALARM";
            case ACKNOWLEDGED -> "ACK";
            case RINGBACK -> "RINGBACK";
        };
    }

    private String trimTo(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("…"))) + "…";
    }
}
