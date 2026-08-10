package com.createstats.gui;

import net.minecraft.client.gui.GuiGraphics;
import com.createstats.network.AlarmConfigPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.EditBox;
import com.createstats.blockentity.AlarmSettings;
import com.createstats.blockentity.StatsAlarmBE;
import net.minecraft.client.gui.screens.Screen;

public class AlarmSettingsScreen extends Screen
{
    private static final int BG = -266856408;
    private static final int PANEL_BG = -266198472;
    private static final int ACCENT = -3366832;
    private static final int TEXT = -2236946;
    private static final int DIM = -7829351;
    private static final int BORDER = -12303258;
    private static final int WARN = -39339;
    private final StatsAlarmBE alarm;
    private final AlarmSettings settings;
    private boolean soundOpen;
    private boolean particleOpen;
    private boolean alertOpen;
    private boolean redstoneOpen;
    private boolean scanOpen;
    private EditBox msgBox;
    private int scrollOffset;
    private int contentHeight;
    private int visibleHeight;
    
    public AlarmSettingsScreen(StatsAlarmBE alarm) {
        super(Component.literal("Alarm Settings"));
        this.soundOpen = true;
        this.particleOpen = true;
        this.alertOpen = true;
        this.redstoneOpen = true;
        this.scanOpen = false;
        this.scrollOffset = 0;
        this.contentHeight = 0;
        this.alarm = alarm;
        this.settings = AlarmSettings.load(alarm.getAlarmSettings().save());
    }
    
    protected void init() {
        super.init();
        this.clearWidgets();
        this.visibleHeight = (int)(this.height * 0.88);
        final int cx = this.width / 2;
        final int panelW = 260;
        final int left = cx - panelW / 2;
        int y = 4;
        this.addSectionHeader("sound", "\ud83d\udd0a Sound", left, y, panelW, this.soundOpen, () -> {
            this.soundOpen = !this.soundOpen;
            this.rebuildWidgets();
            return;
        });
        y += 16;
        if (this.soundOpen) {
            this.addRenderableWidget(Button.builder(Component.literal("Sound: " + this.settings.getSound().getDisplayName()), b -> {
                this.settings.cycleSound();
                this.rebuildWidgets();
            }).bounds(left + 4, this.screenY(y), panelW - 8, 16).build());
            y += 20;
            y = this.addSliderRow(left, y, panelW, "Volume", String.format("%.0f%%", this.settings.getVolume() * 100.0f), () -> {
                this.settings.setVolume(this.settings.getVolume() - 0.1f);
                this.rebuildWidgets();
                return;
            }, () -> {
                this.settings.setVolume(this.settings.getVolume() + 0.1f);
                this.rebuildWidgets();
                return;
            });
            y = this.addSliderRow(left, y, panelW, "Pitch", String.format("%.1f", this.settings.getPitch()), () -> {
                this.settings.setPitch(this.settings.getPitch() - 0.1f);
                this.rebuildWidgets();
                return;
            }, () -> {
                this.settings.setPitch(this.settings.getPitch() + 0.1f);
                this.rebuildWidgets();
                return;
            });
            y = this.addSliderRow(left, y, panelW, "Ring Every", String.format("%.1fs", this.settings.getSoundIntervalSeconds()), () -> {
                this.settings.setSoundIntervalTicks(this.settings.getSoundIntervalTicks() - 20);
                this.rebuildWidgets();
                return;
            }, () -> {
                this.settings.setSoundIntervalTicks(this.settings.getSoundIntervalTicks() + 20);
                this.rebuildWidgets();
                return;
            });
            y = this.addSliderRow(left, y, panelW, "Hearing Range", this.settings.getHearingRange() + " blocks", () -> {
                this.settings.setHearingRange(this.settings.getHearingRange() - 8);
                this.rebuildWidgets();
                return;
            }, () -> {
                this.settings.setHearingRange(this.settings.getHearingRange() + 8);
                this.rebuildWidgets();
                return;
            });
            this.addRenderableWidget(Button.builder(Component.literal("Who Hears: " + this.settings.getSoundVisibility().getDisplayName()), b -> {
                this.settings.setSoundVisibility(this.settings.getSoundVisibility().next());
                this.rebuildWidgets();
            }).bounds(left + 4, this.screenY(y), panelW - 8, 16).build());
            y += 20;
        }
        y += 4;
        this.addSectionHeader("particle", "\u2728 Particles", left, y, panelW, this.particleOpen, () -> {
            this.particleOpen = !this.particleOpen;
            this.rebuildWidgets();
            return;
        });
        y += 16;
        if (this.particleOpen) {
            this.addRenderableWidget(Button.builder(Component.literal("Type: " + this.settings.getParticle().getDisplayName()), b -> {
                this.settings.cycleParticle();
                this.rebuildWidgets();
            }).bounds(left + 4, this.screenY(y), panelW - 8, 16).build());
            y += 20;
            y = this.addSliderRow(left, y, panelW, "Count", String.valueOf(this.settings.getParticleCount()), () -> {
                this.settings.setParticleCount(this.settings.getParticleCount() - 1);
                this.rebuildWidgets();
                return;
            }, () -> {
                this.settings.setParticleCount(this.settings.getParticleCount() + 1);
                this.rebuildWidgets();
                return;
            });
        }
        y += 4;
        this.addSectionHeader("alert", "\u26a0 HUD Alert", left, y, panelW, this.alertOpen, () -> {
            this.alertOpen = !this.alertOpen;
            this.rebuildWidgets();
            return;
        });
        y += 16;
        if (this.alertOpen) {
            this.addRenderableWidget(Button.builder(Component.literal(this.settings.isScreenAlert() ? "\u2714 HUD Alert: ON" : "\u2718 HUD Alert: OFF"), b -> {
                this.settings.setScreenAlert(!this.settings.isScreenAlert());
                this.rebuildWidgets();
            }).bounds(left + 4, this.screenY(y), panelW - 8, 16).build());
            y += 20;
            this.addRenderableWidget(Button.builder(Component.literal("HUD View: " + this.settings.getTextVisibility().getDisplayName()), b -> {
                this.settings.setTextVisibility(this.settings.getTextVisibility().next());
                this.rebuildWidgets();
            }).bounds(left + 4, this.screenY(y), panelW - 8, 16).build());
            y += 20;
            y = this.addSliderRow(left, y, panelW, "Text Range", this.settings.getTextRange() + " blocks", () -> {
                this.settings.setTextRange(this.settings.getTextRange() - 8);
                this.rebuildWidgets();
                return;
            }, () -> {
                this.settings.setTextRange(this.settings.getTextRange() + 8);
                this.rebuildWidgets();
                return;
            });
            (this.msgBox = new EditBox(this.font, left + 4, this.screenY(y), panelW - 8, 16, Component.literal(""))).setMaxLength(128);
            this.msgBox.setValue(this.settings.getAlertMessage());
            this.msgBox.setHint(Component.literal("Custom alert message..."));
            this.msgBox.setResponder(s -> this.settings.setAlertMessage(s));
            this.addRenderableWidget(this.msgBox);
            y += 20;
        }
        y += 4;
        this.addSectionHeader("redstone", "\u26a1 Redstone", left, y, panelW, this.redstoneOpen, () -> {
            this.redstoneOpen = !this.redstoneOpen;
            this.rebuildWidgets();
            return;
        });
        y += 16;
        if (this.redstoneOpen) {
            this.addRenderableWidget(Button.builder(Component.literal(this.settings.isRedstoneEnabled() ? "\u2714 Redstone Output: ON" : "\u2718 Redstone Output: OFF"), b -> {
                this.settings.setRedstoneEnabled(!this.settings.isRedstoneEnabled());
                this.rebuildWidgets();
            }).bounds(left + 4, this.screenY(y), panelW - 8, 16).build());
            y += 20;
        }
        y += 4;
        this.addSectionHeader("scan", "\ud83d\udd0d Scan (controllers)", left, y, panelW, this.scanOpen, () -> {
            this.scanOpen = !this.scanOpen;
            this.rebuildWidgets();
            return;
        });
        y += 16;
        if (this.scanOpen) {
            y = this.addSliderRow(left, y, panelW, "Scan Radius", this.settings.getScanRadius() + " block(s)", () -> {
                this.settings.setScanRadius(this.settings.getScanRadius() - 1);
                this.rebuildWidgets();
                return;
            }, () -> {
                this.settings.setScanRadius(this.settings.getScanRadius() + 1);
                this.rebuildWidgets();
                return;
            });
        }
        y += 12;
        final int btnW = 80;
        this.addRenderableWidget(Button.builder(Component.literal("\u2714 Apply"), b -> this.applyAndClose()).bounds(cx - btnW - 4, this.screenY(y), btnW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("\u2718 Cancel"), b -> this.onClose()).bounds(cx + 4, this.screenY(y), btnW, 20).build());
        y += 30;
        this.contentHeight = y;
        final int maxS = Math.max(0, this.contentHeight - this.visibleHeight + 20);
        if (this.scrollOffset > maxS) {
            this.scrollOffset = maxS;
        }
    }
    
    private void addSectionHeader(String id, String label, int x, int y, int w, boolean open, Runnable toggle) {
        final String arrow = open ? "\u25bc " : "\u25b6 ";
        this.addRenderableWidget(Button.builder(Component.literal(arrow + label), b -> toggle.run()).bounds(x, this.screenY(y), w, 14).build());
    }
    
    private int addSliderRow(int left, int y, int panelW, String label, String valueText, Runnable onMinus, Runnable onPlus) {
        final int btnW = 20;
        final int innerW = panelW - 8;
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> onMinus.run()).bounds(left + 4, this.screenY(y), btnW, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> onPlus.run()).bounds(left + 4 + innerW - btnW, this.screenY(y), btnW, 16).build());
        return y + 20;
    }
    
    private void applyAndClose() {
        PacketDistributor.sendToServer(new AlarmConfigPacket(this.alarm.getBlockPos(), this.settings));
        this.alarm.setAlarmSettings(this.settings);
        this.onClose();
    }
    
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderTransparentBackground(g);
        // FE Dependent Mode panel in the free screen corner (self-gating).
        com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.width - 130, 8, 120, this.alarm.feConsumer());
        final int cx = this.width / 2;
        final int panelW = 260;
        final int left = cx - panelW / 2;
        final int top = (this.height - this.visibleHeight) / 2;
        g.fill(left, top, left + panelW, top + this.visibleHeight, PANEL_BG);
        g.fill(left, top, left + panelW, top + 1, BORDER);
        g.fill(left, top + this.visibleHeight - 1, left + panelW, top + this.visibleHeight, BORDER);
        g.fill(left, top, left + 1, top + this.visibleHeight, BORDER);
        g.fill(left + panelW - 1, top, left + panelW, top + this.visibleHeight, BORDER);
        g.drawCenteredString(this.font, "§6§l\u2699 Alarm Settings", cx, top + 5, ACCENT);
        final boolean active = this.alarm.isActive();
        final int panels = this.alarm.getConnectedPanelCount();
        final String status = active ? "§c\u26a0 ACTIVE" : "§a\u2714 Idle";
        final String redstoneState = this.settings.isRedstoneEnabled() ? "" : " §8• redstone off";
        g.drawCenteredString(this.font, status + "  §7| " + panels + " controller(s) in range" + redstoneState, cx, top + this.visibleHeight - 10, DIM);
        int y = 4;
        y += 16;
        if (this.soundOpen) {
            y += 20;
            this.drawSliderLabel(g, left, this.screenY(y), panelW, "Volume", String.format("%.0f%%", this.settings.getVolume() * 100.0f));
            y += 20;
            this.drawSliderLabel(g, left, this.screenY(y), panelW, "Pitch", String.format("%.1f", this.settings.getPitch()));
            y += 20;
            this.drawSliderLabel(g, left, this.screenY(y), panelW, "Ring Every", String.format("%.1fs", this.settings.getSoundIntervalSeconds()));
            y += 20;
            this.drawSliderLabel(g, left, this.screenY(y), panelW, "Hearing Range", this.settings.getHearingRange() + " blocks");
            y += 20;
            y += 20;
        }
        y += 4;
        y += 16;
        if (this.particleOpen) {
            y += 20;
            this.drawSliderLabel(g, left, this.screenY(y), panelW, "Count", String.valueOf(this.settings.getParticleCount()));
            y += 20;
        }
        y += 4;
        y += 16;
        if (this.alertOpen) {
            y += 40;
            this.drawSliderLabel(g, left, this.screenY(y), panelW, "Text Range", this.settings.getTextRange() + " blocks");
            y += 20;
            g.drawString(this.font, "§8Message:", left + 4, this.screenY(y) - 3, DIM, false);
            y += 20;
        }
        y += 4;
        y += 16;
        if (this.redstoneOpen) {
            y += 20;
        }
        y += 4;
        y += 16;
        if (this.scanOpen) {
            this.drawSliderLabel(g, left, this.screenY(y), panelW, "Scan Radius", this.settings.getScanRadius() + " block(s)");
        }
        if (this.contentHeight > this.visibleHeight) {
            final int maxS = Math.max(1, this.contentHeight - this.visibleHeight + 20);
            final int barH = Math.max(12, this.visibleHeight * this.visibleHeight / this.contentHeight);
            final int barY = top + (int)(this.scrollOffset / (float)maxS * (this.visibleHeight - barH));
            g.fill(left + panelW - 5, barY, left + panelW - 2, barY + barH, 1728053247);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        final int maxS = Math.max(0, this.contentHeight - this.visibleHeight + 20);
        if (maxS > 0) {
            this.scrollOffset = this.clamp(this.scrollOffset - (int)(scrollY * 14.0), 0, maxS);
            this.rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private int screenY(int contentY) {
        final int panelTop = (this.height - this.visibleHeight) / 2;
        return panelTop + 16 + contentY - this.scrollOffset;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private void drawSliderLabel(GuiGraphics g, int left, int y, int panelW, String label, String value) {
        final int innerLeft = left + 28;
        final int innerRight = left + panelW - 28;
        final int centerX = (innerLeft + innerRight) / 2;
        g.drawCenteredString(this.font, "§7" + label + ": §f" + value, centerX, y + 4, -2236946);
    }
    
    public boolean isPauseScreen() {
        return false;
    }
}
