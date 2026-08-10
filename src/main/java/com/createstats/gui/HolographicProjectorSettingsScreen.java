package com.createstats.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import com.createstats.renderer.ProjectorTextureManager;
import com.createstats.network.HologramConfigPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.EditBox;
import com.createstats.display.HologramSettings;
import com.createstats.blockentity.HolographicProjectorBE;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.screens.Screen;

@OnlyIn(Dist.CLIENT)
public class HolographicProjectorSettingsScreen extends Screen
{
    private final HolographicProjectorBE projector;
    private final HologramSettings settings;
    private static final int GUI_WIDTH = 260;
    private int scrollOffset;
    private int contentHeight;
    private int visibleHeight;
    private boolean secDisplay;
    private boolean secSize;
    private boolean secVisual;
    private boolean secTime;
    private boolean secRedstone;
    private boolean secScanner;
    private boolean secChannels;
    private boolean secAdvanced;
    private static final int BTN_H = 18;
    private static final int GAP = 2;
    private static final int SEC_GAP = 6;
    private static final int COL_TITLE = -5579265;
    private static final int COL_DIM = -10061944;
    private static final int COL_BG = -586278358;
    private static final int COL_BORDER = -13399877;
    private static final int COL_WARN = -39356;
    private EditBox redstoneThresholdBox;
    
    public HolographicProjectorSettingsScreen(HolographicProjectorBE projector) {
        super(Component.literal("\u2728 Hologram Configuration"));
        this.scrollOffset = 0;
        this.contentHeight = 0;
        this.secDisplay = true;
        this.secSize = true;
        this.secVisual = true;
        this.secTime = false;
        this.secRedstone = false;
        this.secScanner = false;
        this.secChannels = false;
        this.secAdvanced = false;
        this.projector = projector;
        this.settings = projector.getHologramSettings();
    }
    
    protected void init() {
        super.init();
        this.visibleHeight = (int)(this.height * 0.88);
        final int left = (this.width - 260) / 2;
        final int bw = 240;
        final int hw = bw / 2 - 2;
        final int tw = bw / 3 - 2;
        final int x0 = left + 10;
        int y = 18;
        y = this.sectionHeader(x0, y, bw, "\u2550 Display", this.secDisplay, b -> {
            this.secDisplay = !this.secDisplay;
            this.rebuildWidgets();
        });
        if (this.secDisplay) {
            y = this.addCycleBtn(x0, y, bw, "Style", this.settings.getDisplayMode().getDisplayName(), b -> {
                this.settings.cycleDisplayMode();
                this.updBtn(b, "Style", this.settings.getDisplayMode().getDisplayName());
            });
            y = this.addCycleBtn(x0, y, bw, "Theme", this.settings.getDisplayTheme().getDisplayName(), b -> {
                this.settings.cycleDisplayTheme();
                this.updBtn(b, "Theme", this.settings.getDisplayTheme().getDisplayName());
            });
        }
        y += 6;
        y = this.sectionHeader(x0, y, bw, "\u2550 Hologram Size", this.secSize, b -> {
            this.secSize = !this.secSize;
            this.rebuildWidgets();
        });
        if (this.secSize) {
            final int curW = this.settings.getHologramWidth();
            final int curH = this.settings.getHologramHeight();
            this.addWidget((AbstractWidget)Button.builder(this.lit("-"), b -> {
                this.settings.setHologramWidth(this.settings.getHologramWidth() - 1);
                this.apply();
                this.rebuildWidgets();
            }).bounds(x0, this.screenY(y), 24, 18).build());
            this.addWidget((AbstractWidget)Button.builder(this.lit("Width: " + curW), b -> {}).bounds(x0 + 26, this.screenY(y), hw - 26, 18).build());
            this.addWidget((AbstractWidget)Button.builder(this.lit("+"), b -> {
                this.settings.setHologramWidth(this.settings.getHologramWidth() + 1);
                this.apply();
                this.rebuildWidgets();
            }).bounds(x0 + hw + 2, this.screenY(y), 24, 18).build());
            this.addWidget((AbstractWidget)Button.builder(this.lit("-"), b -> {
                this.settings.setHologramHeight(this.settings.getHologramHeight() - 1);
                this.apply();
                this.rebuildWidgets();
            }).bounds(x0 + hw + 28, this.screenY(y), 24, 18).build());
            this.addWidget((AbstractWidget)Button.builder(this.lit("Height: " + curH), b -> {}).bounds(x0 + hw + 54, this.screenY(y), hw - 54, 18).build());
            this.addWidget((AbstractWidget)Button.builder(this.lit("+"), b -> {
                this.settings.setHologramHeight(this.settings.getHologramHeight() + 1);
                this.apply();
                this.rebuildWidgets();
            }).bounds(x0 + bw - 24, this.screenY(y), 24, 18).build());
            y += 20;
            final boolean unlocked = this.settings.isSizeUnlocked();
            final String lockLabel = unlocked ? "\ud83d\udd13 Extended (max 32)" : "\ud83d\udd12 Normal (max 16)";
            this.addWidget((AbstractWidget)Button.builder(this.lit(lockLabel), b -> {
                this.settings.setSizeUnlocked(!this.settings.isSizeUnlocked());
                this.apply();
                this.rebuildWidgets();
            }).bounds(x0, this.screenY(y), bw, 18).build());
            y += 20;
            if (unlocked) {
                y += 10;
            }
        }
        y += 6;
        y = this.sectionHeader(x0, y, bw, "\u2550 Advanced", this.secAdvanced, b -> {
            this.secAdvanced = !this.secAdvanced;
            this.rebuildWidgets();
        });
        if (this.secAdvanced) {
            y = this.sectionHeader(x0, y, bw, "\u2550 Visual", this.secVisual, b -> {
                this.secVisual = !this.secVisual;
                this.rebuildWidgets();
            });
            if (this.secVisual) {
                this.addWidget((AbstractWidget)Button.builder(this.lit("Rotation: " + this.fmtSpeed(this.settings.getRotationSpeed())), b -> {
                    float s = this.settings.getRotationSpeed();
                    s = ((s >= 3.0f) ? 0.0f : (s + 0.3f));
                    s = Math.round(s * 10.0f) / 10.0f;
                    this.settings.setRotationSpeed(s);
                    b.setMessage(this.lit("Rotation: " + this.fmtSpeed(s)));
                    this.apply();
                }).bounds(x0, this.screenY(y), hw, 18).build());
                this.addWidget((AbstractWidget)Button.builder(this.lit("Glow: " + this.pct(this.settings.getGlowIntensity())), b -> {
                    float g = this.settings.getGlowIntensity();
                    g = ((g >= 1.0f) ? 0.0f : (g + 0.1f));
                    g = Math.round(g * 10.0f) / 10.0f;
                    this.settings.setGlowIntensity(g);
                    b.setMessage(this.lit("Glow: " + this.pct(g)));
                    this.apply();
                }).bounds(x0 + hw + 4, this.screenY(y), hw, 18).build());
                y += 20;
                this.addWidget((AbstractWidget)Button.builder(this.tglComp("Labels", this.settings.isShowLabels()), b -> {
                    this.settings.setShowLabels(!this.settings.isShowLabels());
                    b.setMessage(this.tglComp("Labels", this.settings.isShowLabels()));
                    this.apply();
                }).bounds(x0, this.screenY(y), hw, 18).build());
                this.addWidget((AbstractWidget)Button.builder(this.tglComp("Grid", this.settings.isShowGrid()), b -> {
                    this.settings.setShowGrid(!this.settings.isShowGrid());
                    b.setMessage(this.tglComp("Grid", this.settings.isShowGrid()));
                    this.apply();
                }).bounds(x0 + hw + 4, this.screenY(y), hw, 18).build());
                y += 20;
            }
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Time & Cycling", this.secTime, b -> {
                this.secTime = !this.secTime;
                this.rebuildWidgets();
            });
            if (this.secTime) {
                final String cycleLabel = this.cycleLabelFor(this.settings.getCycleInterval());
                y = this.addCycleBtn2(x0, y, hw, "Range", this.settings.getTimeRange().getDisplayName(), b -> {
                    this.settings.cycleTimeRange();
                    this.updBtn(b, "Range", this.settings.getTimeRange().getDisplayName());
                }, "Cycle", cycleLabel, b -> {
                    int c = this.settings.getCycleInterval();
                    if (c >= 200) {
                        c = 20;
                    }
                    else if (c >= 100) {
                        c = 200;
                    }
                    else if (c >= 60) {
                        c = 100;
                    }
                    else if (c >= 40) {
                        c = 60;
                    }
                    else {
                        c = 40;
                    }
                    this.settings.setCycleInterval(c);
                    this.updBtn(b, "Cycle", this.cycleLabelFor(c));
                });
            }
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Redstone Output", this.secRedstone, b -> {
                this.secRedstone = !this.secRedstone;
                this.rebuildWidgets();
            });
            if (this.secRedstone) {
                this.addWidget((AbstractWidget)Button.builder(this.tglComp("Redstone", this.projector.isRedstoneEnabled()), b -> {
                    this.projector.setRedstoneEnabled(!this.projector.isRedstoneEnabled());
                    b.setMessage(this.tglComp("Redstone", this.projector.isRedstoneEnabled()));
                    this.apply();
                }).bounds(x0, this.screenY(y), bw, 18).build());
                y += 20;
                final List<HolographicProjectorBE.DataChannel> chs = this.projector.getActiveChannels();
                final String rsChId = this.projector.getRedstoneChannelId();
                final String rsLabel = (rsChId == null || rsChId.isEmpty()) ? "None" : this.chLabel(chs, rsChId);
                y = this.addCycleBtn(x0, y, bw, "Channel", rsLabel, b -> {
                    List<HolographicProjectorBE.DataChannel> ch2 = this.projector.getActiveChannels();
                    if (ch2.isEmpty()) {
                        return;
                    }
                    final String cur = this.projector.getRedstoneChannelId();
                    int idx = -1;
                    for (int i = 0; i < ch2.size(); ++i) {
                        if (ch2.get(i).getId().equals(cur)) {
                            idx = i;
                            break;
                        }
                    }
                    final String next = (idx < 0 || idx >= ch2.size() - 1) ? (((cur == null) || cur.isEmpty()) ? ch2.get(0).getId() : "") : ch2.get(idx + 1).getId();
                    this.projector.setRedstoneChannelId(next);
                    this.updBtn(b, "Channel", next.isEmpty() ? "None" : this.chLabel(ch2, next));
                    this.apply();
                });
                this.addWidget((AbstractWidget)Button.builder(this.lit((this.projector.getRedstoneMode() == 0) ? "Binary (0/15)" : "Proportional"), b -> {
                    this.projector.setRedstoneMode((this.projector.getRedstoneMode() == 0) ? 1 : 0);
                    b.setMessage(this.lit((this.projector.getRedstoneMode() == 0) ? "Binary (0/15)" : "Proportional"));
                    this.apply();
                }).bounds(x0, this.screenY(y), hw, 18).build());
                this.addWidget((AbstractWidget)Button.builder(this.lit(this.projector.isRedstoneAbove() ? "\u25b2 Above" : "\u25bc Below"), b -> {
                    this.projector.setRedstoneAbove(!this.projector.isRedstoneAbove());
                    b.setMessage(this.lit(this.projector.isRedstoneAbove() ? "\u25b2 Above" : "\u25bc Below"));
                    this.apply();
                }).bounds(x0 + hw + 4, this.screenY(y), hw, 18).build());
                y += 20;
                (this.redstoneThresholdBox = new EditBox(this.font, x0, this.screenY(y), bw, 18, this.lit("Threshold"))).setMaxLength(12);
                this.redstoneThresholdBox.setValue(this.fmtVal(this.projector.getRedstoneThreshold()));
                this.redstoneThresholdBox.setFilter(s -> s.isEmpty() || s.matches("-?[0-9]*\\.?[0-9]*"));
                this.redstoneThresholdBox.setResponder(s -> {
                    final Float parsed = this.parseEditableFloat(s);
                    if (parsed != null) {
                        this.projector.setRedstoneThreshold(parsed);
                        this.apply();
                    }
                    return;
                });
                this.addWidget((AbstractWidget)this.redstoneThresholdBox);
                y += 20;
            }
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Nearby Scanner", this.secScanner, b -> {
                this.secScanner = !this.secScanner;
                this.rebuildWidgets();
            });
            if (this.secScanner) {
                this.addWidget((AbstractWidget)Button.builder(this.tglComp("Auto-Scan", this.projector.isScanEnabled()), b -> {
                    this.projector.setScanEnabled(!this.projector.isScanEnabled());
                    b.setMessage(this.tglComp("Auto-Scan", this.projector.isScanEnabled()));
                    this.apply();
                }).bounds(x0, this.screenY(y), bw, 18).build());
                y += 20;
            }
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Channels", this.secChannels, b -> {
                this.secChannels = !this.secChannels;
                this.rebuildWidgets();
            });
            if (this.secChannels) {
                final List<HolographicProjectorBE.DataChannel> allCh = this.projector.getActiveChannels();
                final int colW = bw / 2 - 2;
                int col = 0;
                for (HolographicProjectorBE.DataChannel ch : allCh) {
                    final boolean hidden = this.settings.getHiddenChannelIds().contains(ch.getId());
                    String label = ch.getLabel();
                    if (label.length() > 16) {
                        label = label.substring(0, 14) + "..";
                    }
                    final String icon = hidden ? "\u2716 " : "\u2714 ";
                    final String chId = ch.getId();
                    final int bx = x0 + col * (colW + 4);
                    this.addWidget((AbstractWidget)Button.builder(this.lit(icon + label), b -> {
                        if (this.settings.getHiddenChannelIds().contains(chId)) {
                            this.settings.getHiddenChannelIds().remove(chId);
                        }
                        else {
                            this.settings.getHiddenChannelIds().add(chId);
                        }
                        this.apply();
                        this.rebuildWidgets();
                    }).bounds(bx, this.screenY(y), colW, 18).build());
                    if (++col >= 2) {
                        col = 0;
                        y += 20;
                    }
                }
                if (col != 0) {
                    y += 20;
                }
                if (allCh.isEmpty()) {
                    y += 18;
                }
            }
        }
        y += 6;
        this.addWidget((AbstractWidget)Button.builder(this.lit("\u2714 Done"), b -> this.onClose()).bounds(x0, this.screenY(y), bw, 20).build());
        y += 30;
        this.contentHeight = y;
        final int maxS = Math.max(0, this.contentHeight - this.visibleHeight + 20);
        if (this.scrollOffset > maxS) {
            this.scrollOffset = maxS;
        }
    }
    
    private void addWidget(AbstractWidget w) {
        this.addRenderableWidget(w);
    }
    
    private int screenY(int contentY) {
        final int panelTop = (this.height - this.visibleHeight) / 2;
        return panelTop + 16 + contentY - this.scrollOffset;
    }
    
    private int sectionHeader(int x, int y, int w, String label, boolean open, Button.OnPress toggle) {
        this.addWidget((AbstractWidget)Button.builder(this.lit((open ? "\u25bc " : "\u25b6 ") + label), toggle).bounds(x, this.screenY(y), w, 18).build());
        return y + 18 + 2;
    }
    
    private int addCycleBtn(int x, int y, int w, String prefix, String value, Button.OnPress action) {
        this.addWidget((AbstractWidget)Button.builder(this.lit(prefix + ": " + value), b -> {
            action.onPress(b);
            this.apply();
        }).bounds(x, this.screenY(y), w, 18).build());
        return y + 18 + 2;
    }
    
    private int addCycleBtn2(int x, int y, int hw, String p1, String v1, Button.OnPress a1, String p2, String v2, Button.OnPress a2) {
        this.addWidget((AbstractWidget)Button.builder(this.lit(p1 + ": " + v1), b -> {
            a1.onPress(b);
            this.apply();
        }).bounds(x, this.screenY(y), hw, 18).build());
        this.addWidget((AbstractWidget)Button.builder(this.lit(p2 + ": " + v2), b -> {
            a2.onPress(b);
            this.apply();
        }).bounds(x + hw + 4, this.screenY(y), hw, 18).build());
        return y + 18 + 2;
    }
    
    private Component lit(String s) {
        return Component.literal(s);
    }
    
    private Component tglComp(String name, boolean on) {
        return this.lit((on ? "\u25c9 " : "\u25cb ") + name);
    }
    
    private void updBtn(Button b, String prefix, String value) {
        b.setMessage(this.lit(prefix + ": " + value));
    }
    
    private String fmtVal(float v) {
        if (v == (int)v) {
            return String.valueOf((int)v);
        }
        return String.format("%.1f", v);
    }
    
    private String fmtSpeed(float v) {
        if (v == 0.0f) {
            return "Off";
        }
        return String.format("%.1f°/t", v);
    }
    
    private String pct(float v) {
        return String.format("%.0f%%", v * 100.0f);
    }

    private Float parseEditableFloat(String text) {
        if (text == null || !text.matches("-?(?:[0-9]+\\.?[0-9]*|[0-9]*\\.[0-9]+)")) {
            return null;
        }
        return Float.parseFloat(text);
    }

    private String cycleLabelFor(int ticks) {
        final float sec = ticks / 20.0f;
        return String.format("%.1fs", sec);
    }
    
    private String chLabel(List<HolographicProjectorBE.DataChannel> channels, String id) {
        for (HolographicProjectorBE.DataChannel ch : channels) {
            if (ch.getId().equals(id)) {
                final String l = ch.getLabel();
                return (l.length() > 18) ? (l.substring(0, 16) + "..") : l;
            }
        }
        return (id.length() > 18) ? (id.substring(0, 16) + "..") : id;
    }
    
    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
    
    private void apply() {
        this.projector.setHologramSettings(this.settings);
        PacketDistributor.sendToServer(new HologramConfigPacket(this.projector.getBlockPos(), this.settings.save(), this.projector.isRedstoneEnabled(), this.projector.getRedstoneChannelId(), this.projector.getRedstoneThreshold(), this.projector.isRedstoneAbove(), this.projector.getRedstoneMode(), this.projector.isScanEnabled()));
        ProjectorTextureManager.markDirty(this.projector.getBlockPos());
    }
    
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        // FE Dependent Mode panel in the free screen corner (self-gating).
        com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.width - 130, 8, 120, this.projector.feConsumer());
        final int left = (this.width - 260) / 2;
        final int top = (this.height - this.visibleHeight) / 2;
        g.fill(left, top, left + 260, top + this.visibleHeight, -586278358);
        g.fill(left, top, left + 260, top + 1, -13399877);
        g.fill(left, top + this.visibleHeight - 1, left + 260, top + this.visibleHeight, -13399877);
        g.fill(left, top, left + 1, top + this.visibleHeight, -13399877);
        g.fill(left + 260 - 1, top, left + 260, top + this.visibleHeight, -13399877);
        g.drawCenteredString(this.font, this.title, this.width / 2, top + 5, -5579265);
        g.drawCenteredString(this.font, this.lit("Right-click shows status; Stats Configurator opens settings."), this.width / 2, top + 15, -10061944);
        g.drawCenteredString(this.font, this.lit("Sneak-right-click cycles hologram style."), this.width / 2, top + 25, -10061944);
        final int chCount = this.projector.getActiveChannels().size();
        final String info = this.settings.getHologramWidth() + "\u00d7" + this.settings.getHologramHeight() + " hologram \u2502 " + chCount + " ch";
        g.drawCenteredString(this.font, info, this.width / 2, top + this.visibleHeight - 10, -10061944);
        if (this.secSize && this.settings.isSizeUnlocked()) {
            g.drawCenteredString(this.font, Component.literal("\u26a0 Large sizes may cause lag!").withStyle(ChatFormatting.RED), this.width / 2, top + 16 + 4 + 54 + 8 + 6 - this.scrollOffset, -39356);
        }
        if (this.secChannels && this.projector.getActiveChannels().isEmpty()) {
            g.drawCenteredString(this.font, "No data channels yet", this.width / 2, top + this.visibleHeight - 28, -10061944);
        }
        if (this.contentHeight > this.visibleHeight) {
            final int maxS = Math.max(1, this.contentHeight - this.visibleHeight + 20);
            final int barH = Math.max(12, this.visibleHeight * this.visibleHeight / this.contentHeight);
            final int barY = top + (int)(this.scrollOffset / (float)maxS * (this.visibleHeight - barH));
            g.fill(left + 260 - 5, barY, left + 260 - 2, barY + barH, 1728053247);
        }
        super.render(g, mouseX, mouseY, partialTick);
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
    
    public boolean isPauseScreen() {
        return false;
    }
}
