package com.createstats.gui;

import com.createstats.client.ClientScreenHelper;
import com.createstats.display.InstrumentDensityPreset;
import com.createstats.display.InstrumentEffectPreset;
import com.createstats.display.InstrumentStyleFamily;
import com.createstats.display.InstrumentStyleOptionKeys;
import net.minecraft.client.gui.GuiGraphics;
import com.createstats.renderer.DisplayTextureManager;
import com.createstats.network.ConfigSyncPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import java.util.Iterator;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.EditBox;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.screens.Screen;

@OnlyIn(Dist.CLIENT)
public class SettingsScreen extends Screen
{
    private enum SettingsTier {
        QUICK("Quick Setup"),
        ADVANCED("Advanced Tuning");

        private final String label;

        SettingsTier(String label) {
            this.label = label;
        }
    }

    private final StatsDisplayPanelBE panel;
    private final StatsDisplaySettings settings;
    private static final int GUI_WIDTH = 740;
    private static final int GUI_MIN_WIDTH = 360;
    private static final int PANEL_PAD = 14;
    private static final int HEADER_H = 58;
    private static final int FOOTER_H = 24;
    private static final int RAIL_W = 154;
    private static final int HERO_H = 96;
    private static final int COL_BACKDROP_TOP = 0xF214171A;
    private static final int COL_BACKDROP_BOTTOM = 0xF2080A0D;
    private static final int COL_BODY = 0xEA0E1115;
    private static final int COL_CARD = 0xE01A2026;
    private static final int COL_CARD_DARK = 0xE0080A0D;
    private static final int COL_BRASS = 0xFFFFB967;
    private static final int COL_BRASS_DARK = 0xFF7B5432;
    private static final int COL_COPPER = 0xFFFF8A4A;
    private static final int COL_STEEL = 0xFF83939E;
    private static final int COL_TEXT_BRIGHT = 0xFFFFF4DB;
    private static final int COL_TEXT_MAIN = 0xFFF1E6D2;
    private static final int COL_MUTED = 0xFFC1B7A3;
    private static final int COL_GOOD = 0xFF4DD8CB;
    private static final int COL_WARN = 0xFFFFC857;
    private int scrollOffset;
    private int contentHeight;
    private int visibleHeight;
    private int alertChannelIdx;
    private EditBox alertThresholdBox;
    private EditBox redstoneThresholdBox;
    private SettingsTier settingsTier;
    private boolean secDisplay;
    private boolean secTime;
    private boolean secOverlays;
    private boolean secPrediction;
    private boolean secDashboard;
    private boolean secInstrument;
    private boolean secAnalog;
    private boolean secTopology;
    private boolean secTrainMap;
    private boolean secAlerts;
    private boolean secRedstone;
    private boolean secSources;
    private long openedAtMs;
    private static final int COL_TITLE = -8824;
    private static final int COL_SECTION = -2250172;
    private static final int COL_TEXT = -5588020;
    private static final int COL_DIM = -10061944;
    private static final int COL_BG = -585491922;
    private static final int COL_BORDER = -4947652;
    private static final int COL_SEP = 1157627903;
    private static final int BTN_H = 18;
    private static final int GAP = 2;
    private static final int SEC_GAP = 6;
    private static final String[] CLOCK_LABELS;

    public SettingsScreen(StatsDisplayPanelBE panel) {
        super(Component.literal("\u2699 Panel Configuration"));
        this.scrollOffset = 0;
        this.contentHeight = 0;
        this.alertChannelIdx = 0;
        this.settingsTier = SettingsTier.QUICK;
        this.secDisplay = true;
        this.secTime = true;
        this.secOverlays = true;
        this.secPrediction = false;
        this.secDashboard = false;
        this.secInstrument = true;
        this.secAnalog = false;
        this.secTopology = false;
        this.secTrainMap = false;
        this.secAlerts = false;
        this.secRedstone = false;
        this.secSources = false;
        if (!panel.isController() && panel.getControllerPos() != null && panel.getLevel() != null) {
            final BlockEntity ctrlBE = panel.getLevel().getBlockEntity(panel.getControllerPos());
            if (ctrlBE instanceof StatsDisplayPanelBE) {
                final StatsDisplayPanelBE controller = panel = (StatsDisplayPanelBE)ctrlBE;
            }
        }
        this.panel = panel;
        this.settings = panel.getSettings();
    }

    protected void init() {
        super.init();
        if (this.openedAtMs <= 0L) {
            this.openedAtMs = System.currentTimeMillis();
        }
        this.visibleHeight = Math.max(190, Math.min(this.height - 18, (int)(this.height * 0.92)));
        final boolean rail = this.hasRail();
        final int bw = this.contentWidth();
        final int hw = (bw - 6) / 2;
        final int tw = (bw - 8) / 3;
        final int x0 = this.contentLeft();
        int y = 0;
        final int tierW = Math.max(70, bw / 2 - 3);
        if (rail) {
            this.addRailWidgets();
        }
        else {
            final Button quickBtn = Button.builder(this.lit(SettingsTier.QUICK.label), b -> this.switchTier(SettingsTier.QUICK)).bounds(x0, this.screenY(y), tierW, 18).build();
            quickBtn.active = this.settingsTier != SettingsTier.QUICK;
            this.addWidget((AbstractWidget)quickBtn);
            final Button advancedBtn = Button.builder(this.lit(SettingsTier.ADVANCED.label), b -> this.switchTier(SettingsTier.ADVANCED)).bounds(x0 + tierW + 4, this.screenY(y), tierW, 18).build();
            advancedBtn.active = this.settingsTier != SettingsTier.ADVANCED;
            this.addWidget((AbstractWidget)advancedBtn);
            y += 24;
        }
        final boolean quickTier = this.settingsTier == SettingsTier.QUICK;
        final boolean advancedTier = this.settingsTier == SettingsTier.ADVANCED;
        if (!rail) {
            final Button tierNote = Button.builder(this.lit(quickTier ? "Daily panel controls first" : "Rendering and deep tuning"), b -> {}).bounds(x0, this.screenY(y), bw, 18).build();
            tierNote.active = false;
            this.addWidget((AbstractWidget)tierNote);
            y += 24;
        }
        if (quickTier) {
            y = this.sectionHeader(x0, y, bw, "\u2550 Display", this.secDisplay, b -> {
                this.secDisplay = !this.secDisplay;
                this.rebuildWidgets();
            });
        }
        if (quickTier && this.secDisplay) {
            y = this.addCycleBtn(x0, y, bw, "Graph", this.settings.getGraphType().getDisplayName(), b -> {
                final StatsDisplaySettings.GraphType nextGraphType = StatsDisplaySettings.nextGraphType(this.settings.getGraphType(), this.panel.hasTrainConnection());
                this.settings.setGraphType(nextGraphType);
                this.settings.setDisplayMode(StatsDisplaySettings.normalizeDisplayMode(this.settings.getDisplayMode(), nextGraphType, this.panel.getMultiblockWidth(), this.panel.getMultiblockHeight()));
                this.updBtn(b, "Graph", this.settings.getGraphType().getDisplayName());
            });
            y = this.addCycleBtn2(x0, y, hw, "\u2728 Theme", this.settings.getDisplayTheme().getDisplayName(), b -> {
                this.settings.cycleDisplayTheme();
                this.updBtn(b, "\u2728 Theme", this.settings.getDisplayTheme().getDisplayName());
            }, "Colors", this.settings.getColorScheme().getDisplayName(), b -> {
                this.settings.cycleColorScheme();
                this.updBtn(b, "Colors", this.settings.getColorScheme().getDisplayName());
            });
            y = this.addCycleBtn(x0, y, bw, "Mode", this.settings.getDisplayMode().getDisplayName(), b -> {
                final StatsDisplaySettings.DisplayMode nextMode = StatsDisplaySettings.nextDisplayMode(this.settings.getDisplayMode(), this.settings.getGraphType(), this.panel.hasTrainConnection(), this.panel.getMultiblockWidth(), this.panel.getMultiblockHeight());
                this.settings.setDisplayMode(nextMode);
                this.settings.setGraphType(StatsDisplaySettings.normalizeGraphForDisplayMode(this.settings.getGraphType(), nextMode, this.panel.hasTrainConnection()));
                this.updBtn(b, "Mode", this.settings.getDisplayMode().getDisplayName());
            });
        }
        if (quickTier) {
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Time & Cycling", this.secTime, b -> {
                this.secTime = !this.secTime;
                this.rebuildWidgets();
            });
        }
        if (quickTier && this.secTime) {
            y = this.addCycleBtn2(x0, y, hw, "Range", this.settings.getTimeRange().getDisplayName(), b -> {
                this.settings.cycleTimeRange();
                this.updBtn(b, "Range", this.settings.getTimeRange().getDisplayName());
            }, "\u21bb Cycle", this.settings.getCycleSpeedLabel(), b -> {
                this.settings.cycleCycleSpeed();
                this.updBtn(b, "\u21bb Cycle", this.settings.getCycleSpeedLabel());
            });
        }
        if (advancedTier) {
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Overlays & Rendering", this.secOverlays, b -> {
                this.secOverlays = !this.secOverlays;
                this.rebuildWidgets();
            });
        }
        if (advancedTier && this.secOverlays) {
            y = this.addToggle3(x0, y, tw, "Grid", this.settings.showGrid(), b -> {
                this.settings.setShowGrid(!this.settings.showGrid());
                this.updTgl(b, "Grid", this.settings.showGrid());
            }, "Axis", this.settings.showAxisLabels(), b -> {
                this.settings.setShowAxisLabels(!this.settings.showAxisLabels());
                this.updTgl(b, "Axis", this.settings.showAxisLabels());
            }, "Stats", this.settings.showMinMaxAvg(), b -> {
                this.settings.setShowMinMaxAvg(!this.settings.showMinMaxAvg());
                this.updTgl(b, "Stats", this.settings.showMinMaxAvg());
            });
            y = this.addToggle3(x0, y, tw, "Overlay", this.settings.showValueOverlay(), b -> {
                this.settings.setShowValueOverlay(!this.settings.showValueOverlay());
                this.updTgl(b, "Overlay", this.settings.showValueOverlay());
            }, "AutoZoom", this.settings.isAutoScale(), b -> {
                this.settings.setAutoScale(!this.settings.isAutoScale());
                this.updTgl(b, "AutoZoom", this.settings.isAutoScale());
            }, "2-Sided", this.settings.isDoubleSided(), b -> {
                this.settings.setDoubleSided(!this.settings.isDoubleSided());
                this.updTgl(b, "2-Sided", this.settings.isDoubleSided());
            });
        }
        if (advancedTier) {
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Prediction", this.secPrediction, b -> {
                this.secPrediction = !this.secPrediction;
                this.rebuildWidgets();
            });
        }
        if (advancedTier && this.secPrediction) {
            this.addWidget((AbstractWidget)Button.builder(this.tglComp("Prediction", this.settings.isPredictionEnabled()), b -> {
                this.settings.togglePrediction();
                b.setMessage(this.tglComp("Prediction", this.settings.isPredictionEnabled()));
                this.apply();
            }).bounds(x0, this.screenY(y), hw, 18).build());
            this.addWidget((AbstractWidget)Button.builder(this.lit("Ratio: " + this.fmtRatio(this.settings.getPredictionRatio())), b -> {
                float r = this.settings.getPredictionRatio();
                r = ((r >= 2.0f) ? 0.25f : (r + 0.25f));
                this.settings.setPredictionRatio(r);
                b.setMessage(this.lit("Ratio: " + this.fmtRatio(this.settings.getPredictionRatio())));
                this.apply();
            }).bounds(x0 + hw + 4, this.screenY(y), hw, 18).build());
            y += 20;
        }
        if (quickTier) {
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Dashboard", this.secDashboard, b -> {
                this.secDashboard = !this.secDashboard;
                this.rebuildWidgets();
            });
        }
        if (quickTier && this.secDashboard) {
            final int ci = this.clamp(this.settings.getClockFormat(), 0, SettingsScreen.CLOCK_LABELS.length - 1);
            y = this.addCycleBtn(x0, y, bw, "Clock", SettingsScreen.CLOCK_LABELS[ci], b -> {
                int n = (this.settings.getClockFormat() + 1) % SettingsScreen.CLOCK_LABELS.length;
                this.settings.setClockFormat(n);
                this.updBtn(b, "Clock", SettingsScreen.CLOCK_LABELS[n]);
            });
            this.addWidget((AbstractWidget)Button.builder(this.tglComp("Layout Lock", this.settings.isDashboardLayoutLocked()), b -> {
                this.settings.setDashboardLayoutLocked(!this.settings.isDashboardLayoutLocked());
                b.setMessage(this.tglComp("Layout Lock", this.settings.isDashboardLayoutLocked()));
                this.apply();
            }).bounds(x0, this.screenY(y), bw, 18).build());
            y += 20;
            final List<StatsDisplayPanelBE.DataChannel> chs = this.panel.getVisibleChannels();
            final String mainId = this.settings.getDashboardMainChannelId();
            final String mainLabel = mainId.isEmpty() ? "Auto" : this.labelFor(chs, mainId);
            y = this.addCycleBtn(x0, y, bw, "Main Ch", mainLabel, b -> {
                List<StatsDisplayPanelBE.DataChannel> ch2 = this.panel.getVisibleChannels();
                if (ch2.isEmpty()) {
                    return;
                }
                final String cur = this.settings.getDashboardMainChannelId();
                int idx = -1;
                for (int i = 0; i < ch2.size(); ++i) {
                    if (ch2.get(i).getId().equals(cur)) {
                        idx = i;
                        break;
                    }
                }
                String next;
                if (idx < 0 || idx >= ch2.size() - 1) {
                    next = (cur.isEmpty() ? ch2.get(0).getId() : "");
                }
                else {
                    next = ch2.get(idx + 1).getId();
                }
                this.settings.setDashboardMainChannelId(next);
                this.updBtn(b, "Main Ch", next.isEmpty() ? "Auto" : this.labelFor(ch2, next));
            });
        }
        if (quickTier) {
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Alerts", this.secAlerts, b -> {
                this.secAlerts = !this.secAlerts;
                this.rebuildWidgets();
            });
        }
        if (quickTier && this.secAlerts) {
            final List<StatsDisplayPanelBE.DataChannel> chs2 = this.panel.getActiveChannels();
            if (chs2.isEmpty()) {
                y += 20;
            }
            else {
                if (this.alertChannelIdx >= chs2.size()) {
                    this.alertChannelIdx = 0;
                }
                final StatsDisplayPanelBE.DataChannel ach = chs2.get(this.alertChannelIdx);
                this.addWidget((AbstractWidget)Button.builder(this.lit("<"), b -> {
                    this.alertChannelIdx = (this.alertChannelIdx - 1 + chs2.size()) % chs2.size();
                    this.rebuildWidgets();
                }).bounds(x0, this.screenY(y), 20, 18).build());
                this.addWidget((AbstractWidget)Button.builder(this.lit(">"), b -> {
                    this.alertChannelIdx = (this.alertChannelIdx + 1) % chs2.size();
                    this.rebuildWidgets();
                }).bounds(x0 + bw - 20, this.screenY(y), 20, 18).build());
                y += 20;
                this.addWidget((AbstractWidget)Button.builder(this.tglComp("Alert", ach.isAlertEnabled()), b -> {
                    final boolean wasEnabled = ach.isAlertEnabled();
                    ach.setAlertEnabled(!wasEnabled);
                    if (!wasEnabled && ach.getAlertThreshold() == 0.0f) {
                        final float[] da = com.createstats.display.MetricContract.defaultAlarm(ach.getStore().getMetricType(), ach.getStore());
                        ach.setAlertThreshold(da[0]);
                        ach.setAlertAbove(da[1] > 0.5f);
                    }
                    b.setMessage(this.tglComp("Alert", ach.isAlertEnabled()));
                    this.applyAlert(ach);
                }).bounds(x0, this.screenY(y), hw, 18).build());
                this.addWidget((AbstractWidget)Button.builder(this.lit(ach.isAlertAbove() ? "\u25b2 Above" : "\u25bc Below"), b -> {
                    ach.setAlertAbove(!ach.isAlertAbove());
                    b.setMessage(this.lit(ach.isAlertAbove() ? "\u25b2 Above" : "\u25bc Below"));
                    this.applyAlert(ach);
                }).bounds(x0 + hw + 4, this.screenY(y), hw, 18).build());
                y += 20;
                (this.alertThresholdBox = new EditBox(this.font, x0, this.screenY(y), hw, 18, this.lit("Threshold"))).setMaxLength(12);
                this.alertThresholdBox.setValue(this.fmtVal(ach.getAlertThreshold()));
                this.alertThresholdBox.setFilter(s -> s.isEmpty() || s.matches("-?[0-9]*\\.?[0-9]*"));
                this.alertThresholdBox.setResponder(s -> {
                    final Float parsed = this.parseEditableFloat(s);
                    if (parsed != null) {
                        ach.setAlertThreshold(parsed);
                        this.applyAlert(ach);
                    }
                    return;
                });
                this.addWidget((AbstractWidget)this.alertThresholdBox);
                final int qw = hw / 3 - 1;
                final float min = ach.getStore().getMin();
                final float avg = (ach.getStore().getSampleCount() > 0) ? ach.getStore().getAverage() : 0.0f;
                final float max = ach.getStore().getMax();
                this.addWidget((AbstractWidget)Button.builder(this.lit("Min"), b -> {
                    ach.setAlertThreshold(min);
                    this.alertThresholdBox.setValue(this.fmtVal(min));
                    this.applyAlert(ach);
                }).bounds(x0 + hw + 4, this.screenY(y), qw, 18).build());
                this.addWidget((AbstractWidget)Button.builder(this.lit("Avg"), b -> {
                    ach.setAlertThreshold(avg);
                    this.alertThresholdBox.setValue(this.fmtVal(avg));
                    this.applyAlert(ach);
                }).bounds(x0 + hw + 4 + qw + 2, this.screenY(y), qw, 18).build());
                this.addWidget((AbstractWidget)Button.builder(this.lit("Max"), b -> {
                    ach.setAlertThreshold(max);
                    this.alertThresholdBox.setValue(this.fmtVal(max));
                    this.applyAlert(ach);
                }).bounds(x0 + hw + 4 + (qw + 2) * 2, this.screenY(y), qw, 18).build());
                y += 20;
            }
        }
        if (quickTier) {
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Redstone Output", this.secRedstone, b -> {
                this.secRedstone = !this.secRedstone;
                this.rebuildWidgets();
            });
        }
        if (quickTier && this.secRedstone) {
            this.addWidget((AbstractWidget)Button.builder(this.tglComp("Redstone", this.panel.isRedstoneEnabled()), b -> {
                this.panel.setRedstoneEnabled(!this.panel.isRedstoneEnabled());
                b.setMessage(this.tglComp("Redstone", this.panel.isRedstoneEnabled()));
                this.applyRedstone();
            }).bounds(x0, this.screenY(y), bw, 18).build());
            y += 20;
            final List<StatsDisplayPanelBE.DataChannel> chs2 = this.panel.getActiveChannels();
            final String rsChId = this.panel.getRedstoneChannelId();
            final String rsLabel = (rsChId == null || rsChId.isEmpty()) ? "None" : this.labelFor(chs2, rsChId);
            y = this.addCycleBtn(x0, y, bw, "Channel", rsLabel, b -> {
                List<StatsDisplayPanelBE.DataChannel> ch2 = this.panel.getActiveChannels();
                if (ch2.isEmpty()) {
                    return;
                }
                final String cur = this.panel.getRedstoneChannelId();
                int idx = -1;
                for (int i = 0; i < ch2.size(); ++i) {
                    if (ch2.get(i).getId().equals(cur)) {
                        idx = i;
                        break;
                    }
                }
                final String next = (idx < 0 || idx >= ch2.size() - 1) ? (((cur == null) || cur.isEmpty()) ? ch2.get(0).getId() : "") : ch2.get(idx + 1).getId();
                this.panel.setRedstoneChannelId(next);
                this.updBtn(b, "Channel", next.isEmpty() ? "None" : this.labelFor(ch2, next));
                this.applyRedstone();
            });
            this.addWidget((AbstractWidget)Button.builder(this.lit((this.panel.getRedstoneMode() == 0) ? "Binary (0/15)" : "Proportional"), b -> {
                this.panel.setRedstoneMode((this.panel.getRedstoneMode() == 0) ? 1 : 0);
                b.setMessage(this.lit((this.panel.getRedstoneMode() == 0) ? "Binary (0/15)" : "Proportional"));
                this.applyRedstone();
            }).bounds(x0, this.screenY(y), hw, 18).build());
            this.addWidget((AbstractWidget)Button.builder(this.lit(this.panel.isRedstoneAbove() ? "\u25b2 Above" : "\u25bc Below"), b -> {
                this.panel.setRedstoneAbove(!this.panel.isRedstoneAbove());
                b.setMessage(this.lit(this.panel.isRedstoneAbove() ? "\u25b2 Above" : "\u25bc Below"));
                this.applyRedstone();
            }).bounds(x0 + hw + 4, this.screenY(y), hw, 18).build());
            y += 20;
            (this.redstoneThresholdBox = new EditBox(this.font, x0, this.screenY(y), bw, 18, this.lit("Threshold"))).setMaxLength(12);
            this.redstoneThresholdBox.setValue(this.fmtVal(this.panel.getRedstoneThreshold()));
            this.redstoneThresholdBox.setFilter(s -> s.isEmpty() || s.matches("-?[0-9]*\\.?[0-9]*"));
            this.redstoneThresholdBox.setResponder(s -> {
                final Float parsed = this.parseEditableFloat(s);
                if (parsed != null) {
                    this.panel.setRedstoneThreshold(parsed);
                    this.applyRedstone();
                }
                return;
            });
            this.addWidget((AbstractWidget)this.redstoneThresholdBox);
            y += 20;
        }
        if (advancedTier) {
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Instrument Styles", this.secInstrument, b -> {
                this.secInstrument = !this.secInstrument;
                this.rebuildWidgets();
            });
        }
        if (advancedTier && this.secInstrument) {
            y = this.addCycleBtn(x0, y, bw, "Style", this.instrumentFamilyLabel(), b -> this.settings.cycleInstrumentStyleFamily());
            y = this.addCycleBtn2(x0, y, hw, "Density", this.instrumentDensityLabel(), b -> this.settings.cycleInstrumentDensityPreset(), "FX", this.instrumentEffectLabel(), b -> this.settings.cycleInstrumentEffectPreset());
        }
        if (advancedTier) {
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Gauge Hardware", this.secAnalog, b -> {
                this.secAnalog = !this.secAnalog;
                this.rebuildWidgets();
            });
        }
        if (advancedTier && this.secAnalog) {
            this.addWidget((AbstractWidget)Button.builder(this.lit("Min -: " + this.fmtVal(this.settings.getAnalogGaugeMin())), b -> this.adjustAnalogGaugeMin(-10.0f))
                .bounds(x0, this.screenY(y), hw, 18).build());
            this.addWidget((AbstractWidget)Button.builder(this.lit("Min +: " + this.fmtVal(this.settings.getAnalogGaugeMin())), b -> this.adjustAnalogGaugeMin(10.0f))
                .bounds(x0 + hw + 4, this.screenY(y), hw, 18).build());
            y += 20;
            this.addWidget((AbstractWidget)Button.builder(this.lit("Max -: " + this.fmtVal(this.settings.getAnalogGaugeMax())), b -> this.adjustAnalogGaugeMax(-10.0f))
                .bounds(x0, this.screenY(y), hw, 18).build());
            this.addWidget((AbstractWidget)Button.builder(this.lit("Max +: " + this.fmtVal(this.settings.getAnalogGaugeMax())), b -> this.adjustAnalogGaugeMax(10.0f))
                .bounds(x0 + hw + 4, this.screenY(y), hw, 18).build());
            y += 20;
            this.addWidget((AbstractWidget)Button.builder(this.lit("Auto Range: data-driven"), b -> this.resetAnalogGaugeRange())
                .bounds(x0, this.screenY(y), bw, 18).build());
            y += 20;
            final Button rangeHint = Button.builder(this.lit("Hover Min/Max and scroll: 10 per notch | Shift 1 | Ctrl 50"), b -> {}).bounds(x0, this.screenY(y), bw, 18).build();
            rangeHint.active = false;
            this.addWidget((AbstractWidget)rangeHint);
            y += 20;
            this.addWidget((AbstractWidget)Button.builder(this.lit("Yellow: " + this.pct(this.settings.getAnalogZoneYellow())), b -> {
                final float v = this.cycleZone(this.settings.getAnalogZoneYellow());
                this.settings.setAnalogZoneYellow(v);
                b.setMessage(this.lit("Yellow: " + this.pct(v)));
                this.apply();
            }).bounds(x0, this.screenY(y), hw, 18).build());
            this.addWidget((AbstractWidget)Button.builder(this.lit("Red: " + this.pct(this.settings.getAnalogZoneRed())), b -> {
                final float v = this.cycleZone(this.settings.getAnalogZoneRed());
                this.settings.setAnalogZoneRed(v);
                b.setMessage(this.lit("Red: " + this.pct(v)));
                this.apply();
            }).bounds(x0 + hw + 4, this.screenY(y), hw, 18).build());
            y += 20;
            y = this.addToggle3(x0, y, tw, "Zones", this.settings.isAnalogShowZones(), b -> {
                this.settings.setAnalogShowZones(!this.settings.isAnalogShowZones());
                this.updTgl(b, "Zones", this.settings.isAnalogShowZones());
            }, "Glass", this.settings.isAnalogShowGlass(), b -> {
                this.settings.setAnalogShowGlass(!this.settings.isAnalogShowGlass());
                this.updTgl(b, "Glass", this.settings.isAnalogShowGlass());
            }, "Rivets", this.settings.isAnalogShowRivets(), b -> {
                this.settings.setAnalogShowRivets(!this.settings.isAnalogShowRivets());
                this.updTgl(b, "Rivets", this.settings.isAnalogShowRivets());
            });
        }
        if (advancedTier) {
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Topology Map", this.secTopology, b -> {
                this.secTopology = !this.secTopology;
                this.rebuildWidgets();
            });
        }
        if (advancedTier && this.secTopology) {
            this.addWidget((AbstractWidget)Button.builder(this.tglComp("Labels", this.settings.topologyShowLabels()), b -> {
                this.settings.setTopologyShowLabels(!this.settings.topologyShowLabels());
                b.setMessage(this.tglComp("Labels", this.settings.topologyShowLabels()));
                this.apply();
            }).bounds(x0, this.screenY(y), hw, 18).build());
            this.addWidget((AbstractWidget)Button.builder(this.tglComp("Animate", this.settings.topologyAnimateEdges()), b -> {
                this.settings.setTopologyAnimateEdges(!this.settings.topologyAnimateEdges());
                b.setMessage(this.tglComp("Animate", this.settings.topologyAnimateEdges()));
                this.apply();
            }).bounds(x0 + hw + 4, this.screenY(y), hw, 18).build());
            y += 20;
            this.addWidget((AbstractWidget)Button.builder(this.tglComp("Stress Color", this.settings.topologyColorByStress()), b -> {
                this.settings.setTopologyColorByStress(!this.settings.topologyColorByStress());
                b.setMessage(this.tglComp("Stress Color", this.settings.topologyColorByStress()));
                this.apply();
            }).bounds(x0, this.screenY(y), hw, 18).build());
            this.addWidget((AbstractWidget)Button.builder(this.lit("Max Nodes: " + this.settings.getTopologyMaxNodes()), b -> {
                int v = this.settings.getTopologyMaxNodes();
                v = ((v >= 1000) ? 20 : (v + 20));
                this.settings.setTopologyMaxNodes(v);
                b.setMessage(this.lit("Max Nodes: " + this.settings.getTopologyMaxNodes()));
                this.apply();
            }).bounds(x0 + hw + 4, this.screenY(y), hw, 18).build());
            y += 20;
            final Button topoNote1 = Button.builder(this.lit("Scan unlock only changes radius"), b -> {}).bounds(x0, this.screenY(y), bw, 18).build();
            topoNote1.active = false;
            this.addWidget((AbstractWidget)topoNote1);
            y += 20;
            final Button topoNote2 = Button.builder(this.lit("Topology cap lives in Max Nodes"), b -> {}).bounds(x0, this.screenY(y), bw, 18).build();
            topoNote2.active = false;
            this.addWidget((AbstractWidget)topoNote2);
            y += 20;
        }
        if (advancedTier) {
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Train Map", this.secTrainMap, b -> {
                this.secTrainMap = !this.secTrainMap;
                this.rebuildWidgets();
            });
        }
        if (advancedTier && this.secTrainMap) {
            y = this.addCycleBtn(x0, y, bw, "In-World Map", this.settings.getTrainMapRenderQuality().getDisplayName(), b -> {
                this.settings.cycleTrainMapRenderQuality();
                this.updBtn(b, "In-World Map", this.settings.getTrainMapRenderQuality().getDisplayName());
            });
            final Button trainNote = Button.builder(this.lit(this.panel.hasTrainConnection() ? "High uses the richer in-world panel renderer" : "Train Map unlocks after a Train Station link"), b -> {}).bounds(x0, this.screenY(y), bw, 18).build();
            trainNote.active = false;
            this.addWidget((AbstractWidget)trainNote);
            y += 20;
        }
        if (quickTier) {
            y += 6;
            y = this.sectionHeader(x0, y, bw, "\u2550 Sources & Channels", this.secSources, b -> {
                this.secSources = !this.secSources;
                this.rebuildWidgets();
            });
        }
        if (quickTier && this.secSources) {
            final long liveSources = this.panel.getSourceRegistry().values().stream().filter(config -> config != null && config.isActive()).count();
            final int sourceCount = this.panel.getSourceRegistry().size();
            final int channelCount = this.panel.getAllChannels().size();
            final int visibleChannels = this.panel.getVisibleChannels().size();
            final Button summary = Button.builder(this.lit(sourceCount + " sources | " + liveSources + " live | " + visibleChannels + "/" + channelCount + " visible"), b -> {}).bounds(x0, this.screenY(y), bw, 18).build();
            summary.active = false;
            this.addWidget((AbstractWidget)summary);
            y += 20;
            final Button note1 = Button.builder(this.lit("Manage binding, labels, and channel visibility in Stats Display"), b -> {}).bounds(x0, this.screenY(y), bw, 18).build();
            note1.active = false;
            this.addWidget((AbstractWidget)note1);
            y += 20;
            final Button note2 = Button.builder(this.lit("This screen now stays focused on rendering and tuning"), b -> {}).bounds(x0, this.screenY(y), bw, 18).build();
            note2.active = false;
            this.addWidget((AbstractWidget)note2);
            y += 20;
            this.addWidget((AbstractWidget)Button.builder(this.lit("Open Stats Display Home"), b -> ClientScreenHelper.openStatsDisplayHome(this.panel)).bounds(x0, this.screenY(y), bw, 18).build());
            y += 20;
        }
        y += 6;
        y += 24;
        this.addWidget((AbstractWidget)Button.builder(this.lit("\u2714 Done"), b -> this.onClose()).bounds(x0, this.screenY(y), bw, 20).build());
        y += 30;
        this.contentHeight = y;
        final int maxS = Math.max(0, this.contentHeight - this.contentViewportHeight() + 12);
        if (this.scrollOffset > maxS) {
            this.scrollOffset = maxS;
        }
    }

    private void addWidget(AbstractWidget w) {
        final int contentTop = this.scrollContentTop();
        final int contentBottom = contentTop + this.contentViewportHeight();
        final boolean inViewport = w.getY() + w.getHeight() >= contentTop && w.getY() <= contentBottom;
        w.visible = inViewport;
        w.active = w.active && inViewport;
        this.addRenderableWidget(w);
    }

    private void addPinnedWidget(AbstractWidget w) {
        final int contentTop = this.panelTop() + HEADER_H;
        final int contentBottom = this.panelTop() + this.visibleHeight - FOOTER_H;
        final boolean inViewport = w.getY() + w.getHeight() >= contentTop && w.getY() <= contentBottom;
        w.visible = inViewport;
        w.active = w.active && inViewport;
        this.addRenderableWidget(w);
    }

    private void addRailWidgets() {
        final int x = this.railLeft() + 8;
        final int w = this.railWidth() - 16;
        int y = this.panelTop() + HEADER_H + 10;
        final Button quick = Button.builder(this.lit("Quick Setup"), b -> this.switchTier(SettingsTier.QUICK)).bounds(x, y, w, 22).build();
        quick.active = this.settingsTier != SettingsTier.QUICK;
        this.addPinnedWidget((AbstractWidget)quick);
        y += 26;
        final Button advanced = Button.builder(this.lit("Advanced Tuning"), b -> this.switchTier(SettingsTier.ADVANCED)).bounds(x, y, w, 22).build();
        advanced.active = this.settingsTier != SettingsTier.ADVANCED;
        this.addPinnedWidget((AbstractWidget)advanced);
        y += 50;
        if (this.settingsTier == SettingsTier.QUICK) {
            y = this.addRailToggle(x, y, w, "Display", this.secDisplay, b -> { this.secDisplay = !this.secDisplay; this.rebuildWidgets(); });
            y = this.addRailToggle(x, y, w, "Time", this.secTime, b -> { this.secTime = !this.secTime; this.rebuildWidgets(); });
            y = this.addRailToggle(x, y, w, "Dashboard", this.secDashboard, b -> { this.secDashboard = !this.secDashboard; this.rebuildWidgets(); });
            y = this.addRailToggle(x, y, w, "Alerts", this.secAlerts, b -> { this.secAlerts = !this.secAlerts; this.rebuildWidgets(); });
            y = this.addRailToggle(x, y, w, "Redstone", this.secRedstone, b -> { this.secRedstone = !this.secRedstone; this.rebuildWidgets(); });
            y = this.addRailToggle(x, y, w, "Sources", this.secSources, b -> { this.secSources = !this.secSources; this.rebuildWidgets(); });
        }
        else {
            y = this.addRailToggle(x, y, w, "Rendering", this.secOverlays, b -> { this.secOverlays = !this.secOverlays; this.rebuildWidgets(); });
            y = this.addRailToggle(x, y, w, "Prediction", this.secPrediction, b -> { this.secPrediction = !this.secPrediction; this.rebuildWidgets(); });
            y = this.addRailToggle(x, y, w, "Instruments", this.secInstrument, b -> { this.secInstrument = !this.secInstrument; this.rebuildWidgets(); });
            y = this.addRailToggle(x, y, w, "Gauge", this.secAnalog, b -> { this.secAnalog = !this.secAnalog; this.rebuildWidgets(); });
            y = this.addRailToggle(x, y, w, "Topology", this.secTopology, b -> { this.secTopology = !this.secTopology; this.rebuildWidgets(); });
            y = this.addRailToggle(x, y, w, "Train Map", this.secTrainMap, b -> { this.secTrainMap = !this.secTrainMap; this.rebuildWidgets(); });
        }
    }

    private int addRailToggle(int x, int y, int w, String label, boolean open, Button.OnPress action) {
        this.addPinnedWidget((AbstractWidget)Button.builder(this.lit((open ? "ON  " : "OFF ") + label), action).bounds(x, y, w, 18).build());
        return y + 21;
    }

    private void switchTier(SettingsTier tier) {
        if (this.settingsTier != tier) {
            this.settingsTier = tier;
            this.scrollOffset = 0;
            this.rebuildWidgets();
        }
    }

    private int panelWidth() {
        return Math.max(GUI_MIN_WIDTH, Math.min(GUI_WIDTH, this.width - 32));
    }

    private boolean hasRail() {
        return this.panelWidth() >= 560;
    }

    private int railWidth() {
        return this.hasRail() ? RAIL_W : 0;
    }

    private int railLeft() {
        return this.panelLeft() + PANEL_PAD;
    }

    private int panelLeft() {
        return (this.width - this.panelWidth()) / 2;
    }

    private int panelTop() {
        return (this.height - this.visibleHeight) / 2;
    }

    private int contentWidth() {
        return this.panelWidth() - PANEL_PAD * 2 - (this.hasRail() ? this.railWidth() + 12 : 0);
    }

    private int contentLeft() {
        return this.panelLeft() + PANEL_PAD + (this.hasRail() ? this.railWidth() + 12 : 0);
    }

    private int contentViewportHeight() {
        return Math.max(90, this.visibleHeight - HEADER_H - FOOTER_H - (this.hasRail() ? HERO_H + 12 : 0));
    }

    private int scrollContentTop() {
        return this.panelTop() + HEADER_H + (this.hasRail() ? HERO_H + 12 : 0);
    }

    private int screenY(int contentY) {
        return this.scrollContentTop() + contentY - this.scrollOffset;
    }

    private int sectionHeader(int x, int y, int w, String label, boolean open, Button.OnPress toggle) {
        final String cleanLabel = label.replace("\u2550 ", "");
        this.addWidget((AbstractWidget)Button.builder(this.lit((open ? "\u25bc " : "\u25b6 ") + cleanLabel), toggle).bounds(x, this.screenY(y), w, 20).build());
        return y + 20 + 4;
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

    private int addToggle3(int x, int y, int tw, String n1, boolean s1, Button.OnPress a1, String n2, boolean s2, Button.OnPress a2, String n3, boolean s3, Button.OnPress a3) {
        this.addWidget((AbstractWidget)Button.builder(this.tglComp(n1, s1), b -> {
            a1.onPress(b);
            this.apply();
        }).bounds(x, this.screenY(y), tw, 18).build());
        this.addWidget((AbstractWidget)Button.builder(this.tglComp(n2, s2), b -> {
            a2.onPress(b);
            this.apply();
        }).bounds(x + tw + 3, this.screenY(y), tw, 18).build());
        this.addWidget((AbstractWidget)Button.builder(this.tglComp(n3, s3), b -> {
            a3.onPress(b);
            this.apply();
        }).bounds(x + (tw + 3) * 2, this.screenY(y), tw, 18).build());
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

    private void updTgl(Button b, String name, boolean state) {
        b.setMessage(this.tglComp(name, state));
    }

    private String fmtVal(float v) {
        if (v == (int)v) {
            return String.valueOf((int)v);
        }
        return String.format("%.1f", v);
    }

    private String fmtRatio(float r) {
        return String.format("%.2f\u00d7", r);
    }

    private String pct(float v) {
        return String.format("%.0f%%", v * 100.0f);
    }

    private String instrumentFamilyLabel() {
        return switch (this.settings.getInstrumentStyleFamily()) {
            case LEGACY_ANALOG -> "Legacy Analog";
            case MECHANICAL_BRASS -> "Mechanical Brass";
            case LOCOMOTIVE_CAB -> "Locomotive Cab";
            case SCIENTIFIC_INSTRUMENT -> "Scientific Lab";
            case CONTROL_PANEL_WALL -> "Control Wall";
        };
    }

    private String instrumentDensityLabel() {
        return switch (this.settings.getInstrumentDensityPreset()) {
            case MINIMAL -> "Minimal";
            case MEDIUM -> "Medium";
            case DENSE -> "Dense";
        };
    }

    private String instrumentEffectLabel() {
        return switch (this.settings.getInstrumentEffectPreset()) {
            case LOW -> "Low";
            case BALANCED -> "Balanced";
            case RICH -> "Rich";
        };
    }

    private Float parseEditableFloat(String text) {
        if (text == null || !text.matches("-?(?:[0-9]+\\.?[0-9]*|[0-9]*\\.[0-9]+)")) {
            return null;
        }
        return Float.parseFloat(text);
    }

    private float cycleZone(float v) {
        v += 0.05f;
        return (v > 1.0f) ? 0.0f : (Math.round(v * 100.0f) / 100.0f);
    }

    private void adjustAnalogGaugeMin(float delta) {
        this.settings.setAnalogGaugeMin(this.settings.getAnalogGaugeMin() + delta);
        this.apply();
        this.rebuildWidgets();
    }

    private void adjustAnalogGaugeMax(float delta) {
        this.settings.setAnalogGaugeMax(this.settings.getAnalogGaugeMax() + delta);
        this.apply();
        this.rebuildWidgets();
    }

    private void resetAnalogGaugeRange() {
        this.settings.setAnalogGaugeMin(0.0f);
        this.settings.setAnalogGaugeMax(0.0f);
        this.apply();
        this.rebuildWidgets();
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private String labelFor(List<StatsDisplayPanelBE.DataChannel> channels, String id) {
        for (StatsDisplayPanelBE.DataChannel ch : channels) {
            if (ch.getId().equals(id)) {
                final String l = ch.getLabel();
                return (l.length() > 18) ? (l.substring(0, 16) + "..") : l;
            }
        }
        return (id.length() > 18) ? (id.substring(0, 16) + "..") : id;
    }

    private void apply() {
        this.panel.setSettings(this.settings);
        PacketDistributor.sendToServer(ConfigSyncPacket.settingsChange(this.panel.getBlockPos(), this.settings));
        DisplayTextureManager.disposeTexture(this.panel.getBlockPos());
    }

    private boolean instrumentOptionEnabled(String key) {
        return this.settings.getInstrumentStyleOptions().getOption(key, this.defaultInstrumentOption(key));
    }

    private void toggleInstrumentOption(String key) {
        final boolean enabled = !this.instrumentOptionEnabled(key);
        final boolean defaultValue = this.defaultInstrumentOption(key);
        if (enabled == defaultValue) {
            this.settings.getInstrumentStyleOptions().clearOption(key);
        }
        else {
            this.settings.getInstrumentStyleOptions().setOption(key, enabled);
        }
    }

    private boolean defaultInstrumentOption(String key) {
        final InstrumentStyleFamily family = this.settings.getInstrumentStyleFamily();
        final InstrumentDensityPreset density = this.settings.getInstrumentDensityPreset();
        final InstrumentEffectPreset effect = this.settings.getInstrumentEffectPreset();
        switch (key) {
            case InstrumentStyleOptionKeys.SHOW_WARNING_ZONES:
            case InstrumentStyleOptionKeys.SHOW_NUMERIC_CARDS:
                return true;
            case InstrumentStyleOptionKeys.SHOW_GLASS:
                return effect == InstrumentEffectPreset.RICH;
            case InstrumentStyleOptionKeys.SHOW_RIVETS:
                return family == InstrumentStyleFamily.MECHANICAL_BRASS || effect == InstrumentEffectPreset.RICH;
            case InstrumentStyleOptionKeys.SHOW_LAMPS:
                return effect != InstrumentEffectPreset.LOW || family == InstrumentStyleFamily.CONTROL_PANEL_WALL;
            case InstrumentStyleOptionKeys.SHOW_SHUTTERS:
                return family == InstrumentStyleFamily.LOCOMOTIVE_CAB || family == InstrumentStyleFamily.CONTROL_PANEL_WALL;
            case InstrumentStyleOptionKeys.SHOW_SPARKLINE:
                return density == InstrumentDensityPreset.DENSE;
            case InstrumentStyleOptionKeys.SHOW_DECOR_TRIM:
                return effect != InstrumentEffectPreset.LOW;
            case InstrumentStyleOptionKeys.HIGH_CONTRAST_TEXT:
                return family == InstrumentStyleFamily.SCIENTIFIC_INSTRUMENT;
            default:
                return false;
        }
    }

    private void applyAlert(StatsDisplayPanelBE.DataChannel ch) {
        PacketDistributor.sendToServer(ConfigSyncPacket.alertConfig(this.panel.getBlockPos(), ch.getId(), ch.getAlertThreshold(), ch.isAlertAbove(), ch.isAlertEnabled()));
    }

    private void applyRedstone() {
        PacketDistributor.sendToServer(ConfigSyncPacket.redstoneConfig(this.panel.getBlockPos(), this.panel.getRedstoneChannelId(), this.panel.getRedstoneThreshold(), this.panel.isRedstoneAbove(), this.panel.isRedstoneEnabled(), this.panel.getRedstoneMode()));
    }

    private void renderRail(GuiGraphics g) {
        final int x = this.railLeft();
        final int y = this.panelTop() + HEADER_H + 8;
        final int w = this.railWidth();
        final int h = this.visibleHeight - HEADER_H - FOOTER_H - 16;
        g.fillGradient(x, y, x + w, y + h, 0xE012161A, 0xD0080A0D);
        this.drawBorder(g, x, y, w, h, withAlpha(COL_BRASS_DARK, 190));
        g.fill(x + 1, y + 1, x + 3, y + h - 1, withAlpha(COL_COPPER, 120));
        g.drawString(this.font, "WORKFLOW", x + 10, y + 64, COL_MUTED, false);
        final int railBottom = y + h - 34;
        this.drawMiniRailGauge(g, x + 14, railBottom, w - 28, 22);
    }

    private void renderHeroDeck(GuiGraphics g, int mouseX, int mouseY) {
        final int x = this.contentLeft();
        final int y = this.panelTop() + HEADER_H + 8;
        final int w = this.contentWidth();
        final int h = HERO_H;
        g.fillGradient(x, y, x + w, y + h, 0xD51B2228, 0xC90B0E12);
        this.drawBorder(g, x, y, w, h, withAlpha(COL_BRASS, 160));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, withAlpha(0xFFFFFFFF, 26));
        final boolean advanced = this.settingsTier == SettingsTier.ADVANCED;
        g.drawString(this.font, advanced ? "ADVANCED TUNING DECK" : "QUICK SETUP DECK", x + 12, y + 10, COL_BRASS, false);
        g.drawString(this.font, advanced ? "Tune the instrument craft without guessing." : "Get the panel readable, linked, and cycling quickly.", x + 12, y + 24, COL_TEXT_MAIN, false);
        g.drawString(this.font, advanced ? "Open Gauge Hardware for range, zones, glass, and rivets." : "Start with display mode, time window, alerts, then sources.", x + 12, y + 38, COL_MUTED, false);
        if (advanced) {
            this.renderGaugeDiagram(g, x + w - 170, y + 12, 146, 70);
        }
        else {
            this.renderFlowDiagram(g, x + w - 176, y + 13, 152, 68);
        }
    }

    private void renderHeaderDiagram(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, withAlpha(0xFF000000, 92));
        this.drawBorder(g, x, y, w, h, withAlpha(COL_BRASS, 110));
        final long age = System.currentTimeMillis() - this.openedAtMs;
        final int sweep = x + 8 + (int)((age / 28L) % Math.max(1, w - 16));
        final int sources = Math.max(1, this.panel.getSourceRegistry().size());
        final int active = this.panel.getActiveChannels().size();
        final int visible = this.panel.getVisibleChannels().size();
        final int[] values = new int[] { sources, Math.max(1, active), Math.max(1, visible), Math.max(1, this.panel.getMultiblockWidth()), Math.max(1, this.panel.getMultiblockHeight()), Math.max(1, active / 2) };
        int highest = 1;
        for (int value : values) {
            highest = Math.max(highest, value);
        }
        for (int i = 0; i < values.length; ++i) {
            final int barX = x + 10 + i * 20;
            final int barH = 5 + values[i] * 22 / highest;
            g.fill(barX, y + h - 6 - barH, barX + 9, y + h - 6, withAlpha(COL_STEEL, 120));
            g.fill(barX, y + h - 6 - Math.max(2, barH / 2), barX + 9, y + h - 6, withAlpha(COL_BRASS, 160));
        }
        g.fill(sweep, y + 3, sweep + 2, y + h - 3, withAlpha(COL_GOOD, 150));
    }

    private void renderGaugeDiagram(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, withAlpha(0xFF080705, 160));
        this.drawBorder(g, x, y, w, h, withAlpha(COL_BRASS_DARK, 210));
        final int cx = x + 54;
        final int cy = y + 47;
        final int r = 34;
        final float yellow = Math.max(0.0f, Math.min(1.0f, this.settings.getAnalogZoneYellow()));
        final float red = Math.max(yellow, Math.min(1.0f, this.settings.getAnalogZoneRed()));
        for (int i = 0; i <= 28; ++i) {
            final float frac = i / 28.0f;
            final double angle = Math.toRadians(214.0 - i * 4.6);
            final int px = cx + (int)(Math.cos(angle) * r);
            final int py = cy - (int)(Math.sin(angle) * r);
            final int color = frac < yellow ? withAlpha(COL_GOOD, 150) : frac < red ? withAlpha(COL_WARN, 180) : withAlpha(0xFFFF6B5E, 195);
            g.fill(px, py, px + 2, py + 2, color);
        }
        final long age = System.currentTimeMillis() - this.openedAtMs;
        final float min = this.settings.getAnalogGaugeMin();
        final float max = this.settings.getAnalogGaugeMax();
        final float previewFrac = min < max ? 0.62f : 0.55f;
        final double needle = Math.toRadians(214.0 - previewFrac * 128.0);
        final int nx = cx + (int)(Math.cos(needle) * (r - 6));
        final int ny = cy - (int)(Math.sin(needle) * (r - 6));
        this.drawLineApprox(g, cx, cy, nx, ny, COL_BRASS);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, COL_COPPER);
        final float scanFrac = ((age / 45L) % 100) / 99.0f;
        final double scanAngle = Math.toRadians(214.0 - scanFrac * 128.0);
        final int sx = cx + (int)(Math.cos(scanAngle) * (r + 4));
        final int sy = cy - (int)(Math.sin(scanAngle) * (r + 4));
        g.fill(sx - 1, sy - 1, sx + 2, sy + 2, withAlpha(COL_GOOD, 210));
        g.drawString(this.font, "RANGE", x + 94, y + 13, COL_MUTED, false);
        g.drawString(this.font, min < max ? this.fmtVal(min) : "auto", x + 94, y + 28, COL_TEXT_MAIN, false);
        g.drawString(this.font, min < max ? this.fmtVal(max) : "data", x + 94, y + 42, COL_TEXT_BRIGHT, false);
    }

    private void renderFlowDiagram(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, withAlpha(0xFF080705, 155));
        this.drawBorder(g, x, y, w, h, withAlpha(COL_BRASS_DARK, 210));
        final String[] labels = {"LINK", "READ", "SHOW"};
        for (int i = 0; i < labels.length; ++i) {
            final int bx = x + 10 + i * 46;
            final int by = y + 20 + (i == 1 ? -4 : 0);
            g.fillGradient(bx, by, bx + 34, by + 22, withAlpha(COL_CARD, 235), withAlpha(0xFF070605, 235));
            this.drawBorder(g, bx, by, 34, 22, i == 1 ? COL_BRASS : withAlpha(COL_STEEL, 180));
            g.drawCenteredString(this.font, labels[i], bx + 17, by + 7, i == 1 ? COL_BRASS : COL_TEXT_MAIN);
            if (i < labels.length - 1) {
                final int ax = bx + 36;
                final int ay = by + 11;
                g.fill(ax, ay, ax + 8, ay + 1, withAlpha(COL_BRASS, 160));
                g.fill(ax + 7, ay - 2, ax + 9, ay + 3, withAlpha(COL_BRASS, 160));
            }
        }
    }

    private void drawMiniRailGauge(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, withAlpha(0xFF000000, 95));
        this.drawBorder(g, x, y, w, h, withAlpha(COL_BRASS_DARK, 155));
        final int live = Math.min(w - 8, Math.max(8, this.panel.getActiveChannels().size() % Math.max(9, w - 8)));
        g.fill(x + 4, y + h - 7, x + w - 4, y + h - 4, withAlpha(COL_STEEL, 90));
        g.fill(x + 4, y + h - 7, x + 4 + live, y + h - 4, withAlpha(COL_BRASS, 190));
        g.drawString(this.font, "live channels", x + 6, y + 5, COL_MUTED, false);
    }

    private void renderStyledButtons(GuiGraphics g, int mouseX, int mouseY) {
        for (GuiEventListener child : this.children()) {
            if (child instanceof Button button && button.visible) {
                this.renderStyledButton(g, button, mouseX, mouseY);
            }
        }
    }

    private void renderStyledButton(GuiGraphics g, Button button, int mouseX, int mouseY) {
        final int x = button.getX();
        final int y = button.getY();
        final int w = button.getWidth();
        final int h = button.getHeight();
        final String label = button.getMessage().getString();
        final boolean hovered = button.active && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        final boolean selectedTab = label.equals(this.settingsTier.label);
        final boolean section = label.startsWith("\u25bc") || label.startsWith("\u25b6") || label.startsWith("ON  ") || label.startsWith("OFF ");
        final boolean done = label.contains("Done");
        final boolean note = !button.active && !selectedTab;
        final int top = note ? withAlpha(COL_CARD_DARK, 235) : hovered ? 0xF0323D45 : selectedTab ? 0xF03A2D21 : section ? 0xEA202830 : 0xE51A2026;
        final int bottom = note ? withAlpha(0xFF050609, 235) : hovered ? 0xF0192026 : selectedTab ? 0xF01A1410 : 0xE50B0E12;
        final int border = done ? COL_GOOD : selectedTab ? COL_BRASS : hovered ? COL_COPPER : section ? withAlpha(COL_BRASS, 170) : withAlpha(COL_STEEL, 140);
        g.fillGradient(x, y, x + w, y + h, top, bottom);
        this.drawBorder(g, x, y, w, h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hovered ? withAlpha(0xFFFFFFFF, 50) : withAlpha(0xFFFFFFFF, 22));
        if (section || selectedTab || done) {
            g.fill(x + 1, y + 2, x + 4, y + h - 2, done ? withAlpha(COL_GOOD, 185) : withAlpha(COL_BRASS, selectedTab ? 210 : 135));
        }
        if (hovered) {
            final long age = System.currentTimeMillis() - this.openedAtMs;
            final int glintX = x + 5 + (int)((age / 11L) % Math.max(1, w - 10));
            g.fill(glintX, y + 1, glintX + 7, y + 2, withAlpha(0xFFFFFFFF, 90));
            g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, withAlpha(COL_COPPER, 90));
        }
        final int text = note ? COL_MUTED : selectedTab || done || hovered ? COL_TEXT_BRIGHT : COL_TEXT_MAIN;
        final String fitted = this.fit(label, w - 12);
        final int textY = y + Math.max(3, (h - this.font.lineHeight) / 2);
        g.drawCenteredString(this.font, fitted, x + w / 2 + 1, textY + 1, 0xDD000000);
        g.drawCenteredString(this.font, fitted, x + w / 2, textY, text);
    }

    private void drawLineApprox(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        final int dx = x2 - x1;
        final int dy = y2 - y1;
        final int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) {
            g.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        for (int i = 0; i <= steps; ++i) {
            final int x = x1 + dx * i / steps;
            final int y = y1 + dy * i / steps;
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private String fit(String text, int maxWidth) {
        String value = text == null ? "" : text;
        if (this.font.width(value) <= maxWidth) {
            return value;
        }
        while (value.length() > 1 && this.font.width(value + ".") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ".";
    }

    private static int withAlpha(int color, int alpha) {
        final int clamped = Math.max(0, Math.min(255, alpha));
        return (clamped << 24) | (color & 0x00FFFFFF);
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);

        com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.width - 130, 8, 120, this.panel.feConsumer());
        final int panelW = this.panelWidth();
        final int left = this.panelLeft();
        final int top = this.panelTop();
        final int right = left + panelW;
        final int bottom = top + this.visibleHeight;
        g.fillGradient(left, top, right, bottom, COL_BACKDROP_TOP, COL_BACKDROP_BOTTOM);
        this.drawBorder(g, left, top, panelW, this.visibleHeight, COL_BRASS_DARK);
        this.drawBorder(g, left + 1, top + 1, panelW - 2, this.visibleHeight - 2, withAlpha(COL_BRASS, 112));
        g.fillGradient(left + 3, top + 3, right - 3, top + HEADER_H, 0xF02C2215, 0xE419140E);
        g.fill(left + 8, top + HEADER_H - 3, right - 8, top + HEADER_H - 2, withAlpha(COL_BRASS, 170));
        g.fill(left + 8, top + HEADER_H - 2, right - 8, top + HEADER_H - 1, withAlpha(COL_COPPER, 90));
        g.drawString(this.font, "CREATE STATS", left + PANEL_PAD, top + 9, COL_BRASS, false);
        g.drawString(this.font, "Panel Configuration", left + PANEL_PAD, top + 23, COL_TEXT_BRIGHT, false);
        g.drawString(this.font, this.settingsTier.label.toUpperCase(), left + PANEL_PAD, top + 38, COL_MUTED, false);
        this.renderHeaderDiagram(g, right - 168, top + 9, 144, 36);
        g.fillGradient(left + 3, top + HEADER_H, right - 3, bottom - FOOTER_H, 0xEE11161B, COL_BODY);
        if (this.hasRail()) {
            this.renderRail(g);
            this.renderHeroDeck(g, mouseX, mouseY);
        }
        final int mbW = this.panel.getMultiblockWidth();
        final int mbH = this.panel.getMultiblockHeight();
        final int chCount = this.panel.getActiveChannels().size();
        final String info = mbW + "x" + mbH + " panel  |  " + chCount + " channel" + ((chCount != 1) ? "s" : "");
        g.fillGradient(left + 3, bottom - FOOTER_H, right - 3, bottom - 3, 0xF014181D, 0xF0080A0D);
        g.fill(left + 8, bottom - FOOTER_H, right - 8, bottom - FOOTER_H + 1, withAlpha(COL_BRASS, 115));
        g.drawCenteredString(this.font, info, this.width / 2, bottom - 16, COL_MUTED);
        final int viewportH = this.contentViewportHeight();
        if (this.contentHeight > viewportH) {
            final int maxS = Math.max(1, this.contentHeight - viewportH + 12);
            final int trackTop = this.scrollContentTop() + 4;
            final int trackH = Math.max(12, viewportH - 8);
            final int barH = Math.max(14, trackH * trackH / Math.max(trackH, this.contentHeight));
            final int barY = trackTop + (int)(this.scrollOffset / (float)maxS * Math.max(1, trackH - barH));
            g.fill(right - 9, trackTop, right - 5, trackTop + trackH, withAlpha(0xFF000000, 135));
            g.fill(right - 8, barY, right - 6, barY + barH, withAlpha(COL_BRASS, 210));
        }
        super.render(g, mouseX, mouseY, partialTick);
        this.renderStyledButtons(g, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (this.handleGaugeRangeScroll(mx, my, scrollY)) {
            return true;
        }
        final int maxS = Math.max(0, this.contentHeight - this.contentViewportHeight() + 12);
        if (maxS > 0) {
            this.scrollOffset = this.clamp(this.scrollOffset - (int)(scrollY * 14.0), 0, maxS);
            this.rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private boolean handleGaugeRangeScroll(double mx, double my, double scrollY) {
        if (scrollY == 0.0D || this.settingsTier != SettingsTier.ADVANCED || !this.secAnalog) {
            return false;
        }
        for (GuiEventListener child : this.children()) {
            if (child instanceof Button button && button.visible && button.active
                    && mx >= button.getX() && mx < button.getX() + button.getWidth()
                    && my >= button.getY() && my < button.getY() + button.getHeight()) {
                final String label = button.getMessage().getString();
                final float delta = (float)Math.signum(scrollY) * this.gaugeScrollStep();
                if (label.startsWith("Min -") || label.startsWith("Min +")) {
                    this.adjustAnalogGaugeMin(delta);
                    return true;
                }
                if (label.startsWith("Max -") || label.startsWith("Max +")) {
                    this.adjustAnalogGaugeMax(delta);
                    return true;
                }
            }
        }
        return false;
    }

    private float gaugeScrollStep() {
        if (hasControlDown()) {
            return 50.0f;
        }
        if (hasShiftDown()) {
            return 1.0f;
        }
        return 10.0f;
    }

    public boolean isPauseScreen() {
        return false;
    }

    static {
        CLOCK_LABELS = new String[] { "Game Time", "Game 12h", "Real Time", "Uptime", "Hidden" };
    }
}
