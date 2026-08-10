package com.createstats.gui;

import com.createstats.alarm.TableAlarmCalendarEvent;
import com.createstats.blockentity.TableAlarmClockBE;
import com.createstats.network.RequestTableAlarmCalendarPacket;
import com.createstats.network.TableAlarmCalendarInvitePacket;
import com.createstats.network.TableAlarmCalendarSavePacket;
import com.createstats.network.TableAlarmClockStatePacket;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class TableAlarmClockScreen extends Screen {
    private static final int MAX_GUI_W = 720;
    private static final int MIN_GUI_W = 680;
    private static final int MAX_GUI_H = 386;
    private static final int MIN_GUI_H = 330;
    private static final int HEADER_H = 38;
    private static final int FOOTER_H = 36;

    private static final int C_BACKDROP = 0xD8080A0D;
    private static final int C_BG = 0xFF11151B;
    private static final int C_SURFACE = 0xFF181D24;
    private static final int C_SURFACE_2 = 0xFF202733;
    private static final int C_CARD = 0xFF151A21;
    private static final int C_CARD_2 = 0xFF1D242E;
    private static final int C_BORDER = 0xFF3A4554;
    private static final int C_BORDER_SOFT = 0xFF27313D;
    private static final int C_TEXT = 0xFFE8EEF5;
    private static final int C_MUTED = 0xFF8E9AA8;
    private static final int C_DIM = 0xFF5F6975;
    private static final int C_BRASS = 0xFFE0AA45;
    private static final int C_BRASS_DARK = 0xFF8B6427;
    private static final int C_BLUE = 0xFF58A6FF;
    private static final int C_GREEN = 0xFF4FCD82;
    private static final int C_RED = 0xFFEF5B5B;

    private final TableAlarmClockBE clock;
    private final List<TableAlarmCalendarEvent> calendarEvents = new ArrayList<>();
    private LocalDate selectedCalendarDate;
    private int selectedCalendarMinute;
    private UUID selectedCalendarEventId;
    private String draftEventTitle;
    private String draftInvitePlayer;
    private boolean calendarRequested;
    private boolean calendarLoaded;
    private boolean calendarInviteView;
    private EditBox eventTitleBox;
    private EditBox invitePlayerBox;
    private int leftPos;
    private int topPos;
    private int guiW;
    private int guiH;
    private int navW;

    public TableAlarmClockScreen(TableAlarmClockBE clock) {
        super(Component.literal("Alarm Clock"));
        this.clock = clock;
        LocalTime now = LocalTime.now();
        this.selectedCalendarDate = LocalDate.now();
        this.selectedCalendarMinute = now.getHour() * 60 + now.getMinute();
        this.draftEventTitle = "New event";
        this.draftInvitePlayer = "";
    }

    @Override
    protected void init() {
        super.init();
        this.guiW = Math.max(MIN_GUI_W, Math.min(MAX_GUI_W, this.width - 24));
        this.guiH = Math.max(MIN_GUI_H, Math.min(MAX_GUI_H, this.height - 24));
        this.navW = this.guiW < 340 ? 76 : 92;
        this.leftPos = (this.width - this.guiW) / 2;
        this.topPos = (this.height - this.guiH) / 2;
        this.eventTitleBox = null;
        this.invitePlayerBox = null;
        this.clearWidgets();
        this.addModeControls();
        this.addFooterButtons();
    }

    private void addModeControls() {
        final int x = this.contentX();
        final int y = this.contentY();
        final int w = this.contentW();
        final int h = this.contentH();
        final int controlY = y + h - 28;
        final int adjustY = controlY - 24;
        final int cx = x + w / 2;
        switch (this.clock.getMode()) {
            case STOPWATCH -> {
                this.addRenderableWidget(actionButton(cx - 88, controlY, 82, 20, this.clock.isStopwatchRunning() ? "Stop" : "Start", () -> this.clock.toggleStopwatch()));
                this.addRenderableWidget(actionButton(cx + 6, controlY, 82, 20, "Reset", () -> this.clock.resetStopwatch()));
            }
            case TIMER -> {
                final int bw = Math.max(32, Math.min(42, (w - 36) / 4));
                final int gap = 6;
                final int startX = cx - (bw * 4 + gap * 3) / 2;
                this.addRenderableWidget(actionButton(startX, adjustY, bw, 18, "-1m", () -> this.clock.adjustTimerBySeconds(-60)));
                this.addRenderableWidget(actionButton(startX + bw + gap, adjustY, bw, 18, "-10s", () -> this.clock.adjustTimerBySeconds(-10)));
                this.addRenderableWidget(actionButton(startX + (bw + gap) * 2, adjustY, bw, 18, "+10s", () -> this.clock.adjustTimerBySeconds(10)));
                this.addRenderableWidget(actionButton(startX + (bw + gap) * 3, adjustY, bw, 18, "+1m", () -> this.clock.adjustTimerBySeconds(60)));
                this.addRenderableWidget(actionButton(cx - 88, controlY, 82, 20, this.clock.isTimerRunning() ? "Pause" : "Start", () -> this.clock.toggleTimerRunning()));
                this.addRenderableWidget(actionButton(cx + 6, controlY, 82, 20, "Reset", () -> this.clock.resetTimer()));
            }
            case ALARM -> {
                final int bw = Math.max(32, Math.min(42, (w - 36) / 4));
                final int gap = 6;
                final int startX = cx - (bw * 4 + gap * 3) / 2;
                this.addRenderableWidget(actionButton(startX, adjustY, bw, 18, "-1h", () -> this.clock.adjustAlarmMinutes(-60)));
                this.addRenderableWidget(actionButton(startX + bw + gap, adjustY, bw, 18, "-10m", () -> this.clock.adjustAlarmMinutes(-10)));
                this.addRenderableWidget(actionButton(startX + (bw + gap) * 2, adjustY, bw, 18, "+10m", () -> this.clock.adjustAlarmMinutes(10)));
                this.addRenderableWidget(actionButton(startX + (bw + gap) * 3, adjustY, bw, 18, "+1h", () -> this.clock.adjustAlarmMinutes(60)));
                this.addRenderableWidget(actionButton(cx - 74, controlY, 148, 20, this.clock.isAlarmEnabled() ? "Alarm enabled" : "Alarm disabled", () -> this.clock.toggleAlarmEnabled()));
            }
            case CALENDAR -> this.addCalendarControls(x, y, w, h);
            default -> {
            }
        }
    }

    private void addCalendarControls(int x, int y, int w, int h) {
        this.requestCalendarEvents();
        final int editorY = this.calendarEditorY(y, h);
        this.addRenderableWidget(localButton(x + 128, y + 8, 34, 16, "<", () -> this.adjustCalendarMonth(-1)));
        this.addRenderableWidget(localButton(x + 166, y + 8, 34, 16, ">", () -> this.adjustCalendarMonth(1)));
        this.addRenderableWidget(localButton(x + 210, y + 8, 70, 16, "Calendar", () -> this.calendarInviteView = false));
        this.addRenderableWidget(localButton(x + 284, y + 8, 82, 16, "Invites " + this.pendingInviteCount(), () -> this.showInviteView()));
        if (this.calendarInviteView) {
            this.ensureSelectedPendingInvite();
            this.addRenderableWidget(localButton(x + w - 168, editorY + 20, 80, 20, "Accept", () -> this.acceptSelectedInvite()));
            this.addRenderableWidget(localButton(x + w - 82, editorY + 20, 70, 20, "Decline", () -> this.declineSelectedInvite()));
            return;
        }

        this.eventTitleBox = new EditBox(this.font, x + 58, editorY + 14, 168, 16, Component.literal("Event title"));
        this.eventTitleBox.setMaxLength(TableAlarmCalendarEvent.MAX_TITLE_LENGTH);
        this.eventTitleBox.setValue(this.draftEventTitle);
        this.addRenderableWidget(this.eventTitleBox);

        this.invitePlayerBox = new EditBox(this.font, x + 58, editorY + 40, 118, 16, Component.literal("Invite player"));
        this.invitePlayerBox.setMaxLength(32);
        this.invitePlayerBox.setValue(this.draftInvitePlayer);
        this.addRenderableWidget(this.invitePlayerBox);
        this.addRenderableWidget(localButton(x + 180, editorY + 40, 28, 16, "@a", () -> this.setInviteTarget("@a")));
        this.addRenderableWidget(localButton(x + 212, editorY + 40, 30, 16, "Fill", () -> this.completeInviteName()));

        final int timeX = x + 246;
        final int timeY = editorY + 38;
        this.addRenderableWidget(localButton(timeX, timeY, 30, 18, "-1h", () -> this.adjustCalendarMinute(-60)));
        this.addRenderableWidget(localButton(timeX + 34, timeY, 30, 18, "-15", () -> this.adjustCalendarMinute(-15)));
        this.addRenderableWidget(localButton(timeX + 68, timeY, 30, 18, "+15", () -> this.adjustCalendarMinute(15)));
        this.addRenderableWidget(localButton(timeX + 102, timeY, 30, 18, "+1h", () -> this.adjustCalendarMinute(60)));

        final int actionX = x + w - 168;
        this.addRenderableWidget(localButton(actionX, editorY + 12, 52, 18, this.selectedCalendarEventId == null ? "Add" : "Save", () -> this.saveCalendarEvent()));
        this.addRenderableWidget(localButton(actionX + 58, editorY + 12, 52, 18, "New", () -> this.beginNewCalendarEvent()));
        this.addRenderableWidget(localButton(actionX + 116, editorY + 12, 52, 18, "Delete", () -> this.deleteCalendarEvent()));
        this.addRenderableWidget(localButton(actionX, editorY + 38, 168, 18, "Invite Player", () -> this.inviteCalendarEvent()));
    }

    private void addFooterButtons() {
        final int y = this.topPos + this.guiH - 27;
        if (this.clock.isRinging()) {
            this.addRenderableWidget(actionButton(this.leftPos + 10, y, 70, 20, "Silence", () -> this.clock.silence()));
        }
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(this.leftPos + this.guiW - 62, y, 52, 20)
                .build());
    }

    private Button actionButton(int x, int y, int w, int h, String label, Runnable action) {
        return Button.builder(Component.literal(label), b -> {
            action.run();
            this.sync();
            this.rebuild();
        }).bounds(x, y, w, h).build();
    }

    private Button localButton(int x, int y, int w, int h, String label, Runnable action) {
        return Button.builder(Component.literal(label), b -> {
            action.run();
            this.rebuild();
        }).bounds(x, y, w, h).build();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderTransparentBackground(g);
        g.fill(0, 0, this.width, this.height, C_BACKDROP);
        this.drawFrame(g);
        this.drawHeader(g);
        this.drawNavigation(g, mx, my);
        this.drawContent(g);
        this.drawFooter(g);
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    private void drawFrame(GuiGraphics g) {
        final int l = this.leftPos;
        final int t = this.topPos;
        fillChamfered(g, l + 4, t + 5, l + this.guiW + 4, t + this.guiH + 5, 0x66000000);
        fillChamfered(g, l, t, l + this.guiW, t + this.guiH, C_BG);
        drawBorder(g, l, t, this.guiW, this.guiH, C_BORDER);
        g.fill(l + 3, t + 1, l + this.guiW - 3, t + 2, 0x33FFFFFF);
    }

    private void drawHeader(GuiGraphics g) {
        final int l = this.leftPos;
        final int t = this.topPos;
        fillGradientChamfered(g, l + 1, t + 1, l + this.guiW - 1, t + HEADER_H, C_SURFACE_2, C_SURFACE);
        g.drawString(this.font, "ALARM CLOCK", l + 14, t + 9, C_TEXT, false);
        g.drawString(this.font, "Desk time console", l + 14, t + 21, C_MUTED, false);
        final String time = formatGameTimeFull(this.dayTime());
        final int tw = this.font.width(time);
        g.drawString(this.font, time, l + this.guiW - tw - 14, t + 10, C_BRASS, false);
        drawBadge(g, l + this.guiW - 88, t + 23, 74, 12, this.clock.isRinging() ? "RINGING" : "READY", this.clock.isRinging() ? C_RED : C_GREEN);
    }

    private void drawNavigation(GuiGraphics g, int mx, int my) {
        final int x = this.leftPos + 10;
        final int y = this.topPos + HEADER_H + 10;
        final int w = this.navW;
        final int h = this.guiH - HEADER_H - FOOTER_H - 20;
        fillChamfered(g, x, y, x + w, y + h, C_CARD);
        drawBorder(g, x, y, w, h, C_BORDER_SOFT);
        g.drawString(this.font, "MODES", x + 9, y + 8, C_DIM, false);
        final TableAlarmClockBE.Mode[] modes = TableAlarmClockBE.Mode.values();
        final int itemH = Math.max(17, Math.min(22, (h - 26) / modes.length));
        int itemY = y + 23;
        for (TableAlarmClockBE.Mode mode : modes) {
            final boolean active = mode == this.clock.getMode();
            final boolean hover = mx >= x + 6 && mx < x + w - 6 && my >= itemY && my < itemY + itemH;
            final int fill = active ? 0xFF2C3542 : (hover ? 0xFF202833 : C_CARD);
            g.fill(x + 6, itemY, x + w - 6, itemY + itemH - 2, fill);
            if (active) {
                g.fill(x + 6, itemY, x + 9, itemY + itemH - 2, C_BRASS);
            }
            final String label = truncate(mode.getDisplayName(), w - 22);
            g.drawString(this.font, label, x + 13, itemY + 5, active ? C_TEXT : C_MUTED, false);
            itemY += itemH;
        }
    }

    private void drawContent(GuiGraphics g) {
        final int x = this.contentX();
        final int y = this.contentY();
        final int w = this.contentW();
        final int h = this.contentH();
        fillChamfered(g, x, y, x + w, y + h, C_CARD_2);
        drawBorder(g, x, y, w, h, C_BORDER_SOFT);
        switch (this.clock.getMode()) {
            case CLOCK -> drawClockMode(g, x, y, w, h);
            case CALENDAR -> drawCalendarMode(g, x, y, w, h);
            case STOPWATCH -> drawStopwatchMode(g, x, y, w, h);
            case TIMER -> drawTimerMode(g, x, y, w, h);
            case ALARM -> drawAlarmMode(g, x, y, w, h);
            case REALTIME -> drawRealtimeMode(g, x, y, w, h);
            default -> drawClockMode(g, x, y, w, h);
        }
    }

    private void drawClockMode(GuiGraphics g, int x, int y, int w, int h) {
        final long dayTime = this.dayTime();
        drawSectionTitle(g, x, y, "Minecraft time", "World day and active alarm status");
        final String time = formatGameClock(dayTime);
        drawScaledCentered(g, time, x + w / 2, y + 35, fitScale(time, w - 32, 3.9f), C_BRASS);
        final int cardY = y + 86;
        final int cardW = (w - 28) / 2;
        drawMetricCard(g, x + 10, cardY, cardW, 42, "WORLD DAY", String.valueOf(dayTime / 24000L + 1L), C_BLUE);
        drawMetricCard(g, x + 18 + cardW, cardY, cardW, 42, "ALARM", this.clock.isAlarmEnabled() ? formatAlarmMinute(this.clock.getAlarmMinuteOfDay()) : "Off", this.clock.isAlarmEnabled() ? C_GREEN : C_DIM);
    }

    private void drawCalendarMode(GuiGraphics g, int x, int y, int w, int h) {
        if (this.calendarInviteView) {
            this.drawPendingInvites(g, x, y, w, h);
            return;
        }
        final LocalDate today = LocalDate.now();
        final YearMonth month = YearMonth.from(this.selectedCalendarDate);
        drawSectionTitle(g, x, y, "Calendar", month.format(DateTimeFormatter.ofPattern("MMMM yyyy")) + "  -  " + this.calendarEvents.size() + " saved");
        final int gridX = x + 12;
        final int gridY = y + 44;
        final int gridW = this.calendarGridW(w);
        final int cellW = gridW / 7;
        final int cellH = this.calendarCellH(y, h);
        final String[] names = { "M", "T", "W", "T", "F", "S", "S" };
        for (int i = 0; i < 7; ++i) {
            drawCentered(g, names[i], gridX + i * cellW, gridY, cellW, C_DIM);
        }
        final int firstCol = month.atDay(1).getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        for (int day = 1; day <= month.lengthOfMonth(); ++day) {
            final LocalDate date = month.atDay(day);
            final int idx = firstCol + day - 1;
            final int col = idx % 7;
            final int row = idx / 7;
            final int cx = gridX + col * cellW;
            final int cy = gridY + 12 + row * cellH;
            final boolean isToday = date.equals(today);
            final boolean isSelected = date.equals(this.selectedCalendarDate);
            final int eventCount = this.eventCountOn(date);
            g.fill(cx + 1, cy, cx + cellW - 1, cy + cellH - 2, isSelected ? 0xFF26354B : (isToday ? 0xFF2D3D32 : C_CARD));
            if (isSelected) {
                drawBorder(g, cx + 1, cy, cellW - 2, cellH - 2, C_BLUE);
            } else if (isToday) {
                drawBorder(g, cx + 1, cy, cellW - 2, cellH - 2, C_GREEN);
            }
            drawCentered(g, String.valueOf(day), cx, cy + 4, cellW, isSelected ? C_TEXT : (isToday ? C_TEXT : C_MUTED));
            if (eventCount > 0) {
                g.fill(cx + 5, cy + cellH - 5, cx + cellW - 5, cy + cellH - 3, C_BRASS);
                if (eventCount > 1) {
                    drawCentered(g, String.valueOf(Math.min(9, eventCount)), cx, cy + cellH - 14, cellW, C_BRASS);
                }
            }
        }
        this.drawCalendarAgenda(g, x, y, w, h);
        this.drawCalendarEditor(g, x, y, w, h);
        this.drawInviteAutocomplete(g, x, y, w, h);
    }

    private void drawStopwatchMode(GuiGraphics g, int x, int y, int w, int h) {
        drawSectionTitle(g, x, y, "Stopwatch", this.clock.isStopwatchRunning() ? "Counting elapsed game ticks" : "Paused and ready");
        final String text = formatDuration(this.clock.getCurrentStopwatchTicks());
        drawScaledCentered(g, text, x + w / 2, y + 43, fitScale(text, w - 32, 3.55f), this.clock.isStopwatchRunning() ? C_GREEN : C_BRASS);
        drawStatusStrip(g, x + 12, y + h - 62, w - 24, this.clock.isStopwatchRunning() ? "RUNNING" : "STOPPED", this.clock.isStopwatchRunning() ? C_GREEN : C_DIM);
    }

    private void drawTimerMode(GuiGraphics g, int x, int y, int w, int h) {
        final long remaining = this.clock.getCurrentTimerRemainingTicks();
        final long duration = Math.max(1L, this.clock.getTimerDurationTicks());
        final float progress = 1.0f - Math.max(0.0f, Math.min(1.0f, remaining / (float)duration));
        final int color = this.clock.isTimerRunning() ? C_GREEN : (remaining <= 0 ? C_RED : C_BRASS);
        drawSectionTitle(g, x, y, "Timer", this.clock.isTimerRunning() ? "Counting down" : "Adjust duration below");
        final String text = formatDuration(remaining);
        drawScaledCentered(g, text, x + w / 2, y + 36, fitScale(text, w - 32, 3.35f), color);
        final int statusY = y + h - 82;
        drawProgress(g, x + 16, Math.max(y + 74, statusY - 20), w - 32, 12, progress, color);
        g.drawString(this.font, "Set " + formatDuration(duration), x + 16, Math.max(y + 91, statusY - 5), C_MUTED, false);
        drawStatusStrip(g, x + 12, statusY, w - 24, this.clock.isTimerRunning() ? "ACTIVE" : (remaining <= 0 ? "DONE" : "PAUSED"), color);
    }

    private void drawAlarmMode(GuiGraphics g, int x, int y, int w, int h) {
        final int currentMinute = displayedMinuteOfDay(this.dayTime());
        final int until = Math.floorMod(this.clock.getAlarmMinuteOfDay() - currentMinute, 24 * 60);
        final int color = this.clock.isAlarmEnabled() ? C_GREEN : C_BRASS_DARK;
        drawSectionTitle(g, x, y, "Daily alarm", this.clock.isAlarmEnabled() ? "Armed for the next matching game time" : "Disabled");
        final String text = formatAlarmMinute(this.clock.getAlarmMinuteOfDay());
        drawScaledCentered(g, text, x + w / 2, y + 37, fitScale(text, w - 32, 3.55f), color);
        final int statusY = y + h - 82;
        drawProgress(g, x + 16, Math.max(y + 74, statusY - 20), w - 32, 12, this.clock.isAlarmEnabled() ? 1.0f - until / (float)(24 * 60) : 0.0f, color);
        final String eta = this.clock.isAlarmEnabled() ? (until == 0 ? "Rings now" : "In " + until / 60 + "h " + until % 60 + "m") : "Alarm is off";
        drawStatusStrip(g, x + 12, statusY, w - 24, eta, color);
    }

    private void drawRealtimeMode(GuiGraphics g, int x, int y, int w, int h) {
        final String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        final String date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"));
        drawSectionTitle(g, x, y, "Real time", "Local wall clock");
        drawScaledCentered(g, time, x + w / 2, y + 38, fitScale(time, w - 32, 3.35f), C_BLUE);
        drawStatusStrip(g, x + 12, y + 86, w - 24, date, C_BLUE);
        drawMetricCard(g, x + 12, y + h - 62, w - 24, 36, "WORLD", formatGameTimeFull(this.dayTime()), C_BRASS);
    }

    private void drawFooter(GuiGraphics g) {
        final int x = this.leftPos;
        final int y = this.topPos + this.guiH - FOOTER_H;
        g.fill(x + 1, y, x + this.guiW - 1, y + FOOTER_H - 1, C_SURFACE);
        g.fill(x + 1, y, x + this.guiW - 1, y + 1, C_BORDER_SOFT);
        final String status = this.clock.isRinging() ? "Ringing: " + this.clock.getRingSource().getDisplayName() : "Idle";
        final int color = this.clock.isRinging() ? C_RED : C_MUTED;
        g.fill(x + 14, y + 14, x + 20, y + 20, color);
        g.drawString(this.font, truncate(status, this.guiW - 166), x + 26, y + 13, color, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && this.handleNavClick(mx, my)) {
            return true;
        }
        if (btn == 0 && this.clock.getMode() == TableAlarmClockBE.Mode.CALENDAR && this.handleCalendarClick(mx, my)) {
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.clock.getMode() == TableAlarmClockBE.Mode.CALENDAR && this.invitePlayerBox != null && this.invitePlayerBox.isFocused() && keyCode == GLFW.GLFW_KEY_TAB) {
            this.completeInviteName();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleCalendarClick(double mx, double my) {
        if (this.calendarInviteView) {
            return this.handlePendingInviteClick(mx, my);
        }
        final int x = this.contentX();
        final int y = this.contentY();
        final int w = this.contentW();
        final int h = this.contentH();
        final int gridX = x + 12;
        final int gridY = y + 44;
        final int gridW = this.calendarGridW(w);
        final int cellW = gridW / 7;
        final int cellH = this.calendarCellH(y, h);
        final YearMonth month = YearMonth.from(this.selectedCalendarDate);
        final int firstCol = month.atDay(1).getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        for (int day = 1; day <= month.lengthOfMonth(); ++day) {
            final int idx = firstCol + day - 1;
            final int col = idx % 7;
            final int row = idx / 7;
            final int cellX = gridX + col * cellW;
            final int cellY = gridY + 12 + row * cellH;
            if (mx >= cellX + 1 && mx < cellX + cellW - 1 && my >= cellY && my < cellY + cellH - 2) {
                this.captureCalendarDraft();
                this.selectedCalendarDate = month.atDay(day);
                List<TableAlarmCalendarEvent> dayEvents = this.eventsOn(this.selectedCalendarDate);
                if (dayEvents.isEmpty()) {
                    this.beginNewCalendarEvent(false);
                } else {
                    this.selectCalendarEvent(dayEvents.get(0));
                }
                this.rebuild();
                return true;
            }
        }
        List<TableAlarmCalendarEvent> dayEvents = this.eventsOn(this.selectedCalendarDate);
        final int agendaX = this.calendarAgendaX(x, w);
        final int agendaY = y + 44;
        final int agendaW = this.calendarAgendaW(w);
        final int agendaH = this.calendarEditorY(y, h) - agendaY - 10;
        final int rowX = agendaX + 10;
        final int rowW = agendaW - 20;
        final int firstRowY = agendaY + 42;
        final int visibleRows = Math.min(dayEvents.size(), Math.max(1, (agendaY + agendaH - 8 - firstRowY) / 22));
        for (int index = 0; index < visibleRows; ++index) {
            final int rowY = firstRowY + index * 22;
            if (mx >= rowX && mx < rowX + rowW && my >= rowY && my < rowY + 19) {
                this.selectCalendarEvent(dayEvents.get(index));
                this.rebuild();
                return true;
            }
        }
        return false;
    }

    private boolean handleNavClick(double mx, double my) {
        final int x = this.leftPos + 10;
        final int y = this.topPos + HEADER_H + 10;
        final int h = this.guiH - HEADER_H - FOOTER_H - 20;
        final TableAlarmClockBE.Mode[] modes = TableAlarmClockBE.Mode.values();
        final int itemH = Math.max(17, Math.min(22, (h - 26) / modes.length));
        int itemY = y + 23;
        for (TableAlarmClockBE.Mode mode : modes) {
            if (mx >= x + 6 && mx < x + this.navW - 6 && my >= itemY && my < itemY + itemH) {
                if (mode != this.clock.getMode()) {
                    this.clock.setMode(mode);
                    this.sync();
                    this.rebuild();
                }
                return true;
            }
            itemY += itemH;
        }
        return false;
    }

    private void rebuild() {
        if (this.minecraft != null) {
            this.init();
        }
    }

    private void sync() {
        PacketDistributor.sendToServer(new TableAlarmClockStatePacket(this.clock.getBlockPos(), this.clock.saveClockState()));
    }

    public void updateCalendarEvents(List<TableAlarmCalendarEvent> events) {
        this.calendarEvents.clear();
        if (events != null) {
            this.calendarEvents.addAll(events);
        }
        this.calendarEvents.sort(TableAlarmCalendarEvent.SORT_ORDER);
        this.calendarLoaded = true;
        TableAlarmCalendarEvent selectedEvent = this.findSelectedCalendarEvent();
        if (selectedEvent != null) {
            this.selectCalendarEvent(selectedEvent);
        }
        if (this.minecraft != null && this.minecraft.screen == this) {
            this.rebuild();
        }
    }

    private void requestCalendarEvents() {
        if (this.calendarRequested) {
            return;
        }
        this.calendarRequested = true;
        PacketDistributor.sendToServer(new RequestTableAlarmCalendarPacket());
    }

    private void syncCalendarEvents() {
        this.calendarEvents.sort(TableAlarmCalendarEvent.SORT_ORDER);
        PacketDistributor.sendToServer(new TableAlarmCalendarSavePacket(this.calendarEvents));
    }

    private void saveCalendarEvent() {
        TableAlarmCalendarEvent event = this.upsertCalendarEvent();
        this.selectCalendarEvent(event);
        this.syncCalendarEvents();
    }

    private TableAlarmCalendarEvent upsertCalendarEvent() {
        this.captureCalendarDraft();
        TableAlarmCalendarEvent previousEvent = this.findSelectedCalendarEvent();
        TableAlarmCalendarEvent event = new TableAlarmCalendarEvent(
            previousEvent == null ? UUID.randomUUID() : previousEvent.getId(),
            this.selectedCalendarDate,
            this.selectedCalendarMinute,
            this.draftEventTitle,
            previousEvent == null ? "" : previousEvent.getInvitedBy(),
            previousEvent != null && previousEvent.isInvitation());
        this.calendarEvents.removeIf(existingEvent -> existingEvent.getId().equals(event.getId()));
        this.calendarEvents.add(event);
        this.selectedCalendarEventId = event.getId();
        return event;
    }

    private void inviteCalendarEvent() {
        this.captureCalendarDraft();
        if (this.draftInvitePlayer.isBlank()) {
            return;
        }
        TableAlarmCalendarEvent event = this.upsertCalendarEvent();
        this.selectCalendarEvent(event);
        this.syncCalendarEvents();
        PacketDistributor.sendToServer(new TableAlarmCalendarInvitePacket(event, this.draftInvitePlayer));
    }

    private void deleteCalendarEvent() {
        if (this.selectedCalendarEventId == null) {
            this.beginNewCalendarEvent();
            return;
        }
        UUID eventId = this.selectedCalendarEventId;
        this.calendarEvents.removeIf(event -> event.getId().equals(eventId));
        this.beginNewCalendarEvent(false);
        this.syncCalendarEvents();
    }

    private void beginNewCalendarEvent() {
        this.beginNewCalendarEvent(true);
    }

    private void beginNewCalendarEvent(boolean captureDraft) {
        if (captureDraft) {
            this.captureCalendarDraft();
        }
        this.selectedCalendarEventId = null;
        this.draftEventTitle = "New event";
    }

    private void selectCalendarEvent(TableAlarmCalendarEvent event) {
        if (event == null) {
            this.beginNewCalendarEvent(false);
            return;
        }
        this.selectedCalendarEventId = event.getId();
        this.selectedCalendarDate = event.getDate();
        this.selectedCalendarMinute = event.getMinuteOfDay();
        this.draftEventTitle = event.getTitle();
    }

    private void captureCalendarDraft() {
        if (this.eventTitleBox != null) {
            this.draftEventTitle = TableAlarmCalendarEvent.sanitizeTitle(this.eventTitleBox.getValue());
        }
        if (this.invitePlayerBox != null) {
            this.draftInvitePlayer = this.invitePlayerBox.getValue().trim();
        }
    }

    private void adjustCalendarMonth(int deltaMonths) {
        this.captureCalendarDraft();
        this.selectedCalendarDate = this.selectedCalendarDate.plusMonths(deltaMonths);
    }

    private void adjustCalendarDate(int deltaDays) {
        this.captureCalendarDraft();
        this.selectedCalendarDate = this.selectedCalendarDate.plusDays(deltaDays);
    }

    private void adjustCalendarMinute(int deltaMinutes) {
        this.captureCalendarDraft();
        this.selectedCalendarMinute = Math.floorMod(this.selectedCalendarMinute + deltaMinutes, TableAlarmCalendarEvent.MINUTES_PER_DAY);
    }

    private TableAlarmCalendarEvent findSelectedCalendarEvent() {
        if (this.selectedCalendarEventId == null) {
            return null;
        }
        for (TableAlarmCalendarEvent event : this.calendarEvents) {
            if (event.getId().equals(this.selectedCalendarEventId)) {
                return event;
            }
        }
        return null;
    }

    private List<TableAlarmCalendarEvent> eventsOn(LocalDate date) {
        List<TableAlarmCalendarEvent> dayEvents = new ArrayList<>();
        for (TableAlarmCalendarEvent event : this.calendarEvents) {
            if (event.getDate().equals(date)) {
                dayEvents.add(event);
            }
        }
        dayEvents.sort(TableAlarmCalendarEvent.SORT_ORDER);
        return dayEvents;
    }

    private boolean hasEventOn(LocalDate date) {
        for (TableAlarmCalendarEvent event : this.calendarEvents) {
            if (event.getDate().equals(date)) {
                return true;
            }
        }
        return false;
    }

    private int eventCountOn(LocalDate date) {
        int count = 0;
        for (TableAlarmCalendarEvent event : this.calendarEvents) {
            if (event.getDate().equals(date)) {
                ++count;
            }
        }
        return count;
    }

    private int pendingInviteCount() {
        return this.pendingInvites().size();
    }

    private List<TableAlarmCalendarEvent> pendingInvites() {
        List<TableAlarmCalendarEvent> invites = new ArrayList<>();
        for (TableAlarmCalendarEvent event : this.calendarEvents) {
            if (event.isInvitation()) {
                invites.add(event);
            }
        }
        invites.sort(TableAlarmCalendarEvent.SORT_ORDER);
        return invites;
    }

    private void showInviteView() {
        this.captureCalendarDraft();
        this.calendarInviteView = true;
        this.ensureSelectedPendingInvite();
    }

    private void ensureSelectedPendingInvite() {
        TableAlarmCalendarEvent selected = this.findSelectedCalendarEvent();
        if (selected != null && selected.isInvitation()) {
            return;
        }
        List<TableAlarmCalendarEvent> invites = this.pendingInvites();
        if (invites.isEmpty()) {
            this.selectedCalendarEventId = null;
        } else {
            this.selectCalendarEvent(invites.get(0));
        }
    }

    private void acceptSelectedInvite() {
        TableAlarmCalendarEvent selected = this.findSelectedCalendarEvent();
        if (selected == null || !selected.isInvitation()) {
            return;
        }
        TableAlarmCalendarEvent accepted = new TableAlarmCalendarEvent(selected.getId(), selected.getDate(), selected.getMinuteOfDay(), selected.getTitle());
        this.calendarEvents.removeIf(event -> event.getId().equals(selected.getId()));
        this.calendarEvents.add(accepted);
        this.selectCalendarEvent(accepted);
        this.calendarInviteView = false;
        this.syncCalendarEvents();
    }

    private void declineSelectedInvite() {
        TableAlarmCalendarEvent selected = this.findSelectedCalendarEvent();
        if (selected == null || !selected.isInvitation()) {
            return;
        }
        this.calendarEvents.removeIf(event -> event.getId().equals(selected.getId()));
        this.selectedCalendarEventId = null;
        this.ensureSelectedPendingInvite();
        this.syncCalendarEvents();
    }

    private boolean handlePendingInviteClick(double mx, double my) {
        final int x = this.contentX();
        final int y = this.contentY();
        final int w = this.contentW();
        final int listX = x + 14;
        final int listY = y + 48;
        final int listW = w - 28;
        final List<TableAlarmCalendarEvent> invites = this.pendingInvites();
        final int visibleRows = Math.min(invites.size(), Math.max(1, (this.calendarEditorY(y, this.contentH()) - listY - 14) / 28));
        for (int index = 0; index < visibleRows; ++index) {
            int rowY = listY + index * 28;
            if (mx >= listX && mx < listX + listW && my >= rowY && my < rowY + 24) {
                this.selectCalendarEvent(invites.get(index));
                this.rebuild();
                return true;
            }
        }
        return false;
    }

    private void drawPendingInvites(GuiGraphics g, int x, int y, int w, int h) {
        drawSectionTitle(g, x, y, "Pending invites", this.pendingInviteCount() + " waiting in your calendar");
        final int listX = x + 14;
        final int listY = y + 48;
        final int listW = w - 28;
        final int listBottom = this.calendarEditorY(y, h) - 14;
        fillChamfered(g, listX, listY - 8, listX + listW, listBottom, C_CARD);
        drawBorder(g, listX, listY - 8, listW, listBottom - listY + 8, C_BORDER_SOFT);
        List<TableAlarmCalendarEvent> invites = this.pendingInvites();
        if (invites.isEmpty()) {
            g.drawString(this.font, "No pending invites", listX + 14, listY + 12, C_MUTED, false);
            this.drawPendingInviteFooter(g, x, y, w, h, null);
            return;
        }
        final int visibleRows = Math.min(invites.size(), Math.max(1, (listBottom - listY) / 28));
        for (int index = 0; index < visibleRows; ++index) {
            TableAlarmCalendarEvent event = invites.get(index);
            int rowY = listY + index * 28;
            boolean selected = event.getId().equals(this.selectedCalendarEventId);
            g.fill(listX + 8, rowY, listX + listW - 8, rowY + 24, selected ? 0xFF26354B : C_CARD_2);
            if (selected) {
                g.fill(listX + 8, rowY, listX + 11, rowY + 24, C_BLUE);
            }
            g.drawString(this.font, event.getDate().format(DateTimeFormatter.ofPattern("dd MMM")), listX + 18, rowY + 4, C_BRASS, false);
            g.drawString(this.font, formatAlarmMinute(event.getMinuteOfDay()), listX + 58, rowY + 4, C_BRASS, false);
            g.drawString(this.font, truncate(event.getTitle(), listW - 230), listX + 100, rowY + 4, C_TEXT, false);
            g.drawString(this.font, "from " + truncate(event.getInvitedBy(), 92), listX + listW - 126, rowY + 4, C_MUTED, false);
            drawBadge(g, listX + listW - 46, rowY + 6, 30, 11, "INV", C_BLUE);
        }
        this.drawPendingInviteFooter(g, x, y, w, h, this.findSelectedCalendarEvent());
    }

    private void drawPendingInviteFooter(GuiGraphics g, int x, int y, int w, int h, TableAlarmCalendarEvent selected) {
        final int editorY = this.calendarEditorY(y, h);
        final int editorH = y + h - editorY - 10;
        fillChamfered(g, x + 10, editorY, x + w - 10, editorY + editorH, C_CARD);
        drawBorder(g, x + 10, editorY, w - 20, editorH, C_BORDER_SOFT);
        if (selected == null || !selected.isInvitation()) {
            g.drawString(this.font, "Select an invite to accept or decline", x + 22, editorY + 25, C_MUTED, false);
            return;
        }
        g.drawString(this.font, truncate(selected.getTitle(), w - 220), x + 22, editorY + 16, C_TEXT, false);
        g.drawString(this.font, selected.getDate().format(DateTimeFormatter.ofPattern("EEE dd MMM")) + "  " + formatAlarmMinute(selected.getMinuteOfDay()), x + 22, editorY + 30, C_BRASS, false);
        if (!selected.getInvitedBy().isBlank()) {
            g.drawString(this.font, "from " + truncate(selected.getInvitedBy(), 96), x + 22, editorY + 44, C_MUTED, false);
        }
    }

    private void setInviteTarget(String targetName) {
        this.draftInvitePlayer = targetName == null ? "" : targetName.trim();
        if (this.invitePlayerBox != null) {
            this.invitePlayerBox.setValue(this.draftInvitePlayer);
        }
    }

    private void completeInviteName() {
        this.captureCalendarDraft();
        List<String> suggestions = this.inviteNameSuggestions();
        if (suggestions.isEmpty()) {
            return;
        }
        int index = suggestions.indexOf(this.draftInvitePlayer);
        this.setInviteTarget(suggestions.get((index + 1) % suggestions.size()));
    }

    private List<String> inviteNameSuggestions() {
        String prefix = this.draftInvitePlayer == null ? "" : this.draftInvitePlayer.trim();
        String normalizedPrefix = prefix.toLowerCase(java.util.Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        if (prefix.isBlank() || "@a".startsWith(normalizedPrefix)) {
            suggestions.add("@a");
        }
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            for (PlayerInfo info : this.minecraft.getConnection().getOnlinePlayers()) {
                String name = info.getProfile().getName();
                if (name != null && (prefix.isBlank() || name.toLowerCase(java.util.Locale.ROOT).startsWith(normalizedPrefix)) && !suggestions.contains(name)) {
                    suggestions.add(name);
                }
            }
        }
        return suggestions;
    }

    private void drawInviteAutocomplete(GuiGraphics g, int x, int y, int w, int h) {
        if (this.invitePlayerBox == null || !this.invitePlayerBox.isFocused()) {
            return;
        }
        List<String> suggestions = this.inviteNameSuggestions();
        if (suggestions.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        int max = Math.min(4, suggestions.size());
        for (int index = 0; index < max; ++index) {
            if (index > 0) {
                text.append("  ");
            }
            text.append(suggestions.get(index));
        }
        g.drawString(this.font, truncate(text.toString(), 176), x + 58, this.calendarEditorY(y, h) + 60, C_MUTED, false);
    }

    private void drawCalendarAgenda(GuiGraphics g, int x, int y, int w, int h) {
        final int agendaX = this.calendarAgendaX(x, w);
        final int agendaY = y + 44;
        final int agendaW = this.calendarAgendaW(w);
        final int agendaH = this.calendarEditorY(y, h) - agendaY - 10;
        fillChamfered(g, agendaX, agendaY, agendaX + agendaW, agendaY + agendaH, C_CARD);
        drawBorder(g, agendaX, agendaY, agendaW, agendaH, C_BORDER_SOFT);
        g.drawString(this.font, this.selectedCalendarDate.format(DateTimeFormatter.ofPattern("EEE dd MMM")), agendaX + 10, agendaY + 9, C_TEXT, false);
        drawBadge(g, agendaX + agendaW - 70, agendaY + 7, 58, 12, this.calendarLoaded ? "SYNCED" : "LOADING", this.calendarLoaded ? C_GREEN : C_BRASS);

        List<TableAlarmCalendarEvent> dayEvents = this.eventsOn(this.selectedCalendarDate);
        g.drawString(this.font, dayEvents.size() == 1 ? "1 event" : dayEvents.size() + " events", agendaX + 10, agendaY + 23, C_MUTED, false);
        if (dayEvents.isEmpty()) {
            g.fill(agendaX + 10, agendaY + 42, agendaX + agendaW - 10, agendaY + 68, C_CARD_2);
            drawBorder(g, agendaX + 10, agendaY + 42, agendaW - 20, 26, C_BORDER_SOFT);
            g.drawString(this.font, "No events on this day", agendaX + 18, agendaY + 51, C_DIM, false);
            return;
        }
        final int listY = agendaY + 42;
        final int visibleRows = Math.min(dayEvents.size(), Math.max(1, (agendaY + agendaH - 8 - listY) / 22));
        for (int index = 0; index < visibleRows; ++index) {
            TableAlarmCalendarEvent event = dayEvents.get(index);
            int rowY = listY + index * 22;
            boolean selected = event.getId().equals(this.selectedCalendarEventId);
            g.fill(agendaX + 10, rowY, agendaX + agendaW - 10, rowY + 19, selected ? 0xFF26354B : C_CARD_2);
            if (selected) {
                g.fill(agendaX + 10, rowY, agendaX + 13, rowY + 19, C_BLUE);
            }
            g.drawString(this.font, formatAlarmMinute(event.getMinuteOfDay()), agendaX + 18, rowY + 3, C_BRASS, false);
            g.drawString(this.font, truncate(event.getTitle(), Math.max(36, agendaW - 102)), agendaX + 58, rowY + 3, C_TEXT, false);
            if (event.isInvitation()) {
                drawBadge(g, agendaX + agendaW - 54, rowY + 3, 36, 11, "INV", C_BLUE);
            }
            if (event.isInvitation() && !event.getInvitedBy().isBlank()) {
                g.drawString(this.font, "from " + truncate(event.getInvitedBy(), agendaW - 70), agendaX + 58, rowY + 12, C_MUTED, false);
            }
        }
    }

    private void drawCalendarEditor(GuiGraphics g, int x, int y, int w, int h) {
        final int editorY = this.calendarEditorY(y, h);
        final int editorH = y + h - editorY - 10;
        fillChamfered(g, x + 10, editorY, x + w - 10, editorY + editorH, C_CARD);
        drawBorder(g, x + 10, editorY, w - 20, editorH, C_BORDER_SOFT);
        g.drawString(this.font, "Title", x + 18, editorY + 18, C_DIM, false);
        g.drawString(this.font, "Invite", x + 18, editorY + 44, C_DIM, false);
        g.drawString(this.font, "Date", x + 238, editorY + 16, C_DIM, false);
        g.drawString(this.font, this.selectedCalendarDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), x + 270, editorY + 16, C_TEXT, false);
        g.drawString(this.font, "Time", x + 238, editorY + 29, C_DIM, false);
        g.drawString(this.font, formatAlarmMinute(this.selectedCalendarMinute), x + 270, editorY + 29, C_BRASS, false);
        if (this.selectedCalendarEventId != null) {
            drawBadge(g, x + w - 88, editorY + editorH - 18, 70, 12, "EDITING", C_BLUE);
        }
    }

    private int calendarGridW(int contentWidth) {
        return Math.min(280, Math.max(238, contentWidth / 2 - 12));
    }

    private int calendarCellH(int contentY, int contentHeight) {
        return Math.max(19, Math.min(25, (this.calendarEditorY(contentY, contentHeight) - (contentY + 58)) / 6));
    }

    private int calendarAgendaX(int contentX, int contentWidth) {
        return contentX + 12 + this.calendarGridW(contentWidth) + 16;
    }

    private int calendarAgendaW(int contentWidth) {
        return Math.max(186, contentWidth - this.calendarGridW(contentWidth) - 40);
    }

    private int calendarEditorY(int contentY, int contentHeight) {
        return contentY + contentHeight - 86;
    }

    @Override
    public void onClose() {
        this.sync();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int contentX() {
        return this.leftPos + this.navW + 22;
    }

    private int contentY() {
        return this.topPos + HEADER_H + 10;
    }

    private int contentW() {
        return this.guiW - this.navW - 32;
    }

    private int contentH() {
        return this.guiH - HEADER_H - FOOTER_H - 20;
    }

    private long dayTime() {
        return this.clock.getLevel() != null ? this.clock.getLevel().getDayTime() : 0L;
    }

    private void drawSectionTitle(GuiGraphics g, int x, int y, String title, String subtitle) {
        g.drawString(this.font, title, x + 12, y + 10, C_TEXT, false);
        g.drawString(this.font, truncate(subtitle, Math.max(20, this.contentW() - 26)), x + 12, y + 22, C_MUTED, false);
    }

    private void drawMetricCard(GuiGraphics g, int x, int y, int w, int h, String label, String value, int color) {
        fillChamfered(g, x, y, x + w, y + h, C_CARD);
        drawBorder(g, x, y, w, h, C_BORDER_SOFT);
        g.fill(x + 2, y + 2, x + w - 2, y + 4, color);
        g.drawString(this.font, truncate(label, w - 10), x + 6, y + 9, C_DIM, false);
        g.drawString(this.font, truncate(value, w - 10), x + 6, y + 23, color, false);
    }

    private void drawStatusStrip(GuiGraphics g, int x, int y, int w, String text, int color) {
        g.fill(x, y, x + w, y + 20, C_CARD);
        drawBorder(g, x, y, w, 20, C_BORDER_SOFT);
        g.fill(x + 5, y + 6, x + 10, y + 14, color);
        g.drawString(this.font, truncate(text, w - 22), x + 16, y + 6, color, false);
    }

    private void drawProgress(GuiGraphics g, int x, int y, int w, int h, float progress, int color) {
        final float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        g.fill(x, y, x + w, y + h, C_CARD);
        drawBorder(g, x, y, w, h, C_BORDER_SOFT);
        final int fillW = Math.max(0, Math.round((w - 2) * clamped));
        if (fillW > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, color);
            g.fill(x + 1, y + 1, x + 1 + fillW, y + 3, brighten(color, 1.25f));
        }
    }

    private static void drawBadge(GuiGraphics g, int x, int y, int w, int h, String text, int color) {
        g.fill(x, y, x + w, y + h, darken(color, 0.55f));
        drawBorder(g, x, y, w, h, color);
        drawCentered(g, text, x, y + 2, w, 0xFFFFFFFF);
    }

    private static void drawCentered(GuiGraphics g, String text, int x, int y, int w, int color) {
        final net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        final int tw = font.width(text);
        g.drawString(font, text, x + (w - tw) / 2, y, color, false);
    }

    private void drawScaledCentered(GuiGraphics g, String text, int cx, int y, float scale, int color) {
        final int tw = this.font.width(text);
        g.pose().pushPose();
        g.pose().translate(cx - tw * scale / 2.0f, y, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(this.font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private float fitScale(String text, int maxWidth, float preferred) {
        float scale = preferred;
        while (scale > 1.0f && this.font.width(text) * scale > maxWidth) {
            scale -= 0.25f;
        }
        return Math.max(1.0f, scale);
    }

    private String truncate(String text, int maxPixels) {
        if (text == null) {
            return "";
        }
        if (this.font.width(text) <= maxPixels) {
            return text;
        }
        while (text.length() > 2 && this.font.width(text + "..") > maxPixels) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "..";
    }

    private static String formatGameTimeFull(long dayTime) {
        return "Day " + (dayTime / 24000L + 1L) + "  " + formatGameClock(dayTime);
    }

    private static String formatGameClock(long dayTime) {
        final long timeOfDay = dayTime % 24000L;
        final int hours = (int)((timeOfDay / 1000L + 6L) % 24L);
        final int minutes = (int)(timeOfDay % 1000L * 60L / 1000L);
        return String.format("%02d:%02d", hours, minutes);
    }

    private static String formatDuration(long ticks) {
        final long seconds = Math.max(0L, ticks) / 20L;
        final long hours = seconds / 3600L;
        final long minutes = seconds % 3600L / 60L;
        final long secs = seconds % 60L;
        return hours > 0L ? String.format("%02d:%02d:%02d", hours, minutes, secs) : String.format("%02d:%02d", minutes, secs);
    }

    private static String formatAlarmMinute(int minuteOfDay) {
        return String.format("%02d:%02d", Math.floorDiv(minuteOfDay, 60) % 24, Math.floorMod(minuteOfDay, 60));
    }

    private static int displayedMinuteOfDay(long dayTime) {
        final long timeOfDay = dayTime % 24000L;
        final int hours = (int)((timeOfDay / 1000L + 6L) % 24L);
        final int minutes = (int)(timeOfDay % 1000L * 60L / 1000L);
        return hours * 60 + minutes;
    }

    private static void fillChamfered(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1 + 3, y1, x2 - 3, y1 + 1, color);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
        g.fill(x1, y1 + 2, x2, y2 - 2, color);
        g.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, color);
        g.fill(x1 + 3, y2 - 1, x2 - 3, y2, color);
    }

    private static void fillGradientChamfered(GuiGraphics g, int x1, int y1, int x2, int y2, int colorTop, int colorBottom) {
        g.fill(x1 + 3, y1, x2 - 3, y1 + 1, colorTop);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, colorTop);
        g.fillGradient(x1, y1 + 2, x2, y2 - 2, colorTop, colorBottom);
        g.fill(x1 + 1, y2 - 2, x2 - 1, y2 - 1, colorBottom);
        g.fill(x1 + 3, y2 - 1, x2 - 3, y2, colorBottom);
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int brighten(int color, float factor) {
        final int a = color >>> 24;
        final int r = Math.min(255, Math.round((color >> 16 & 0xFF) * factor));
        final int g = Math.min(255, Math.round((color >> 8 & 0xFF) * factor));
        final int b = Math.min(255, Math.round((color & 0xFF) * factor));
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int darken(int color, float factor) {
        return brighten(color, factor);
    }
}
