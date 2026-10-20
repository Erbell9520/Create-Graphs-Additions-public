package com.createstats.statsmk3.page;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.ground.GroundCommand;
import com.createstats.ground.GroundFrame;
import com.createstats.network.GroundCommandPacket;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.ui.Button;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class DriveView extends StudioView {
    private static final float TAB_PAD = 8.0f;

    private int tab;

    private float tabsNeed = 400.0f;
    private Segmented tabs;
    private Button faceButton;
    private Button lookButton;
    private Button newTrip;
    private Button clearTrail;
    private Button resetLearned;

    private float sentFor;
    private GroundCommand sent;

    public DriveView(StudioContext ctx) {
        super(ctx);

        this.tab = switch (ctx.settings().faceMode) {
            case DRIVE_GARAGE -> 5;
            case DRIVE_TRAIN -> 3;
            case DRIVE_PIT -> 4;
            default -> 0;
        };
    }

    @Override
    public String id() {
        return "drive";
    }

    @Override
    public String label() {
        return "Drive";
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        c.roundedRect(new Rect(cx - 3.0f, cy - 6.0f, 6.0f, 12.0f), 2.0f, color);
        c.rect(cx - 7.0f, cy - 0.5f, 4.0f, 1.0f, color);
        c.rect(cx + 3.0f, cy - 0.5f, 4.0f, 1.0f, color);
    }

    @Override
    protected void build() {
        this.tabs = new Segmented(DriveArt.TABS, () -> this.tab, i -> this.tab = i);
        this.tabs.textScale(Text.META);
        this.widgets.add(this.tabs);

        float longest = 0.0f;
        for (final String t : DriveArt.TABS) {
            longest = Math.max(longest, this.ctx.font.width(t) * Text.META);
        }
        this.tabsNeed = 6.0f + DriveArt.TABS.length * (longest + TAB_PAD * 2.0f);

        this.faceButton = new Button(faceLabel(), this::toggleFace);
        this.faceButton.textScale(Text.META);
        this.widgets.add(this.faceButton);

        this.lookButton = new Button(lookLabel(), this::toggleLook);
        this.lookButton.textScale(Text.META);
        this.widgets.add(this.lookButton);

        this.newTrip = command("New trip", GroundCommand.RESET_TRIP);
        this.clearTrail = command("Clear trail", GroundCommand.CLEAR_TRAIL);
        this.resetLearned = command("Reset learned", GroundCommand.RESET_LEARNED);
        refreshButtons();
        applyLayout();
    }

    private DriveArt.StudioLayout layout() {
        int commands = 0;
        for (final Button b : new Button[]{this.newTrip, this.clearTrail, this.resetLearned}) {
            if (b != null && b.visible()) {
                commands++;
            }
        }
        return DriveArt.studioLayout(this.area, this.tabsNeed, commands);
    }

    private void applyLayout() {
        if (this.tabs == null) {
            return;
        }
        final DriveArt.StudioLayout l = layout();
        this.tabs.setBounds(l.tabs());
        this.lookButton.setBounds(l.look());
        this.faceButton.setBounds(l.face());
        float x = l.commands().right();
        for (final Button b : new Button[]{this.newTrip, this.clearTrail, this.resetLearned}) {
            if (!b.visible()) {
                continue;
            }
            x -= DriveArt.COMMAND_W;
            b.setBounds(new Rect(x, l.commands().y, DriveArt.COMMAND_W, DriveArt.BUTTON_H));
            x -= DriveArt.WIDGET_GAP;
        }
    }

    private Button command(String label, GroundCommand command) {
        final Button b = new Button(label, () -> send(command));
        b.textScale(Text.META);
        this.widgets.add(b);
        return b;
    }

    private void send(GroundCommand command) {
        if (this.ctx.panel == null) {
            return;
        }
        PacketDistributor.sendToServer(new GroundCommandPacket(this.ctx.panel.getBlockPos(), command.ordinal()));
        this.sent = command;
        this.sentFor = 2.0f;
    }

    private StatsDisplayPanelBE controller() {
        if (this.ctx.panel == null) {
            return null;
        }
        return this.ctx.panel.getControllerBE() != null ? this.ctx.panel.getControllerBE() : this.ctx.panel;
    }

    private GroundFrame frame() {
        final StatsDisplayPanelBE p = controller();
        if (p == null || p.getLevel() == null) {
            return null;
        }
        return p.groundLinks().feed().frame(System.nanoTime(), p.getLevel().getGameTime(), 0.0f);
    }

    private boolean linked() {
        final StatsDisplayPanelBE p = controller();
        return p != null && p.groundLinks().hasLinks();
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.sentFor = Math.max(0.0f, this.sentFor - dt);
        if (this.faceButton != null) {
            this.faceButton.setLabel(faceLabel());
        }
        if (this.lookButton != null) {
            this.lookButton.setLabel(lookLabel());
        }
        refreshButtons();
        applyLayout();
    }

    private void refreshButtons() {
        if (this.newTrip == null) {
            return;
        }
        final GroundFrame f = frame();
        final boolean journey = this.tab == 5;
        final boolean powertrain = this.tab == 1;
        visible(this.newTrip, journey && f != null && f.snapshot().accepts(GroundCommand.RESET_TRIP),
                GroundCommand.RESET_TRIP, "New trip");
        visible(this.clearTrail, journey && f != null && f.snapshot().accepts(GroundCommand.CLEAR_TRAIL),
                GroundCommand.CLEAR_TRAIL, "Clear trail");
        visible(this.resetLearned, powertrain && f != null && f.snapshot().accepts(GroundCommand.RESET_LEARNED),
                GroundCommand.RESET_LEARNED, "Reset learned");
    }

    private void visible(Button b, boolean on, GroundCommand command, String label) {
        b.setVisible(on);
        b.setLabel(this.sentFor > 0.0f && this.sent == command ? "Sent" : label);
    }

    static Mk3Settings.FaceMode faceFor(int tab) {
        return switch (tab) {
            case 3 -> Mk3Settings.FaceMode.DRIVE_TRAIN;
            case 4 -> Mk3Settings.FaceMode.DRIVE_PIT;
            case 5 -> Mk3Settings.FaceMode.DRIVE_GARAGE;
            default -> Mk3Settings.FaceMode.DRIVE_DASH;
        };
    }

    private boolean showingOnFace() {
        return this.ctx.settings().faceMode == faceFor(this.tab);
    }

    private String faceLabel() {
        return showingOnFace() ? "On face" : "Show on face";
    }

    private void toggleFace() {
        final Mk3Settings settings = this.ctx.settings();
        settings.faceMode = showingOnFace() ? Mk3Settings.FaceMode.INSTRUMENTS : faceFor(this.tab);
        this.ctx.commit();
    }

    private DriveKit.Look look() {
        return DriveKit.Look.values()[Math.max(0, Math.min(DriveKit.Look.values().length - 1, this.ctx.settings().driveLook))];
    }

    private String lookLabel() {
        return look() == DriveKit.Look.RACING ? "Look: racing" : "Look: cluster";
    }

    private void toggleLook() {
        final Mk3Settings settings = this.ctx.settings();
        settings.driveLook = (settings.driveLook + 1) % DriveKit.Look.values().length;
        this.ctx.commit();
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final GroundFrame f = frame();
        final DriveArt.StudioLayout l = layout();
        if (f == null) {
            c.text("Drive", this.area.x, this.area.y, Tokens.INK, Text.TITLE);
            DriveArt.empty(c, l.content(), linked());
            return;
        }
        DriveArt.header(c, this.area.top(DriveArt.HEADER_H), f, l.headerReserved());
        DriveArt.paint(c, l.content(), f, this.tab, look());
    }
}
