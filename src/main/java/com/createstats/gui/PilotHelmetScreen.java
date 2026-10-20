package com.createstats.gui;

import com.createstats.client.ModKeyMappings;
import com.createstats.item.PilotHelmetData;
import com.createstats.item.PilotHelmetItem;
import com.createstats.network.PilotHelmetSettingsPacket;
import com.createstats.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class PilotHelmetScreen extends Screen {
    private static final int PANEL_W = 296;

    private static final int NV_BLOCK_H = 46;
    private static final int PANEL_H = 280 + NV_BLOCK_H;

    private static final int OUTLINE = 0xFF050A12;
    private static final int PANEL_BG = 0xF00B1018;
    private static final int HEADER_BG = 0xFF0F2434;
    private static final int HEADER_ACCENT = 0xFF42E8C2;
    private static final int SECTION_DIVIDER = 0xFF1A2632;
    private static final int SECTION_LABEL = 0xFFB7E6C3;
    private static final int SECTION_LABEL_DIM = 0xFF7CB5A0;
    private static final int LINK_FRAME = 0xFF182230;
    private static final int LINK_FRAME_BG = 0xFF0E1620;
    private static final int LINK_LABEL = 0xFFEAF2F8;
    private static final int LINK_VALUE_OK = 0xFFFFD575;
    private static final int LINK_VALUE_OFF = 0xFF6C7480;
    private static final int STATUS_OK = 0xFF4EE89A;
    private static final int STATUS_OFF = 0xFF50616E;
    private static final int FOOTER_TEXT = 0xFF8898A8;

    static final float DEFAULT_HUD_SCALE_FACTOR = 1.0f;
    private static final float[] HUD_SCALE_CYCLE = {1.0f, 1.5f, 2.0f, 3.0f};

    private final InteractionHand hand;
    private PilotHelmetData.Mode pendingMode = PilotHelmetData.Mode.FIGHTER;
    private boolean pendingAutoIfSeated = true;
    private int pendingVignetteStrength = 5;
    private float pendingHudScaleFactor = DEFAULT_HUD_SCALE_FACTOR;
    private boolean pendingNightVisionEnabled = false;
    private PilotHelmetData.NightVisionMode pendingNightVisionMode = PilotHelmetData.NightVisionMode.CLASSIC;

    public PilotHelmetScreen(InteractionHand hand) {
        super(Component.translatable("gui.create_stats.pilot_helmet.title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        PilotHelmetData data = PilotHelmetItem.getData(this.currentHelmet());
        this.pendingMode = data.getMode();
        this.pendingAutoIfSeated = data.isAutoIfSeated();
        this.pendingVignetteStrength = data.getVignetteStrength();
        this.pendingHudScaleFactor = data.getHudScaleFactor();
        this.pendingNightVisionEnabled = data.isNightVisionEnabled();
        this.pendingNightVisionMode = data.getNightVisionMode();

        int rowY = top + 56;
        this.addRenderableWidget(Button.builder(Component.literal("Mode: " + this.pendingMode.getDisplayName()),
                btn -> {
                    this.pendingMode = nextMode(this.pendingMode);
                    btn.setMessage(Component.literal("Mode: " + this.pendingMode.getDisplayName()));
                    this.pushSettings();
                })
                .bounds(left + 16, rowY, 160, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(this.autoLabel()),
                btn -> {
                    this.pendingAutoIfSeated = !this.pendingAutoIfSeated;
                    btn.setMessage(Component.literal(this.autoLabel()));
                    this.pushSettings();
                })
                .bounds(left + 184, rowY, 96, 20)
                .build());

        this.addRenderableWidget(new VignetteSlider(left + 16, top + 102, PANEL_W - 32, 20, this.pendingVignetteStrength));

        this.addRenderableWidget(Button.builder(Component.literal(hudScaleLabel()),
                btn -> {
                    this.pendingHudScaleFactor = nextHudScale(this.pendingHudScaleFactor);
                    btn.setMessage(Component.literal(hudScaleLabel()));
                    this.pushSettings();
                })
                .bounds(left + 16, top + 148, PANEL_W - 32, 20)
                .build());

        int nvY = top + 194;
        this.addRenderableWidget(Button.builder(Component.literal(nvToggleLabel()),
                btn -> {
                    this.pendingNightVisionEnabled = !this.pendingNightVisionEnabled;
                    btn.setMessage(Component.literal(nvToggleLabel()));
                    this.pushSettings();
                })
                .bounds(left + 16, nvY, 160, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal(nvModeLabel()),
                btn -> {
                    this.pendingNightVisionMode = nextNvMode(this.pendingNightVisionMode);
                    btn.setMessage(Component.literal(nvModeLabel()));
                    this.pushSettings();
                })
                .bounds(left + 184, nvY, 96, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Close"), btn -> this.onClose())
                .bounds(left + PANEL_W - 70, top + PANEL_H - 26, 58, 20)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        ItemStack stack = this.currentHelmet();
        if (stack.isEmpty() || !stack.is(ModItems.PILOT_HELMET.get())) {
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // no-op so the 1.21 blur never runs

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_W) / 2;
        int top = (this.height - PANEL_H) / 2;
        int right = left + PANEL_W;
        int bottom = top + PANEL_H;

        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, OUTLINE);
        graphics.fill(left, top, right, bottom, PANEL_BG);

        graphics.fill(left, top, left + 8, top + 2, HEADER_ACCENT);
        graphics.fill(left, top, left + 2, top + 8, HEADER_ACCENT);
        graphics.fill(right - 8, top, right, top + 2, HEADER_ACCENT);
        graphics.fill(right - 2, top, right, top + 8, HEADER_ACCENT);
        graphics.fill(left, bottom - 2, left + 8, bottom, HEADER_ACCENT);
        graphics.fill(left, bottom - 8, left + 2, bottom, HEADER_ACCENT);
        graphics.fill(right - 8, bottom - 2, right, bottom, HEADER_ACCENT);
        graphics.fill(right - 2, bottom - 8, right, bottom, HEADER_ACCENT);

        int headerH = 28;
        graphics.fill(left + 2, top + 2, right - 2, top + headerH, HEADER_BG);
        graphics.fill(left + 2, top + headerH, right - 2, top + headerH + 1, HEADER_ACCENT);
        graphics.drawString(this.font, this.title, left + 12, top + 10, 0xFFE6F7F0, false);
        String stamp = "PILOT v1";
        int stampW = this.font.width(stamp);
        graphics.fill(right - stampW - 18, top + 8, right - 8, top + 20, 0xFF1B2D3D);
        graphics.drawString(this.font, stamp, right - stampW - 13, top + 10, HEADER_ACCENT, false);

        drawSectionLabel(graphics, left + 16, top + 40, "OPERATING MODE", "mode + seat behavior");

        int s2Y = top + 86;
        graphics.fill(left + 12, s2Y - 6, right - 12, s2Y - 5, SECTION_DIVIDER);
        drawSectionLabel(graphics, left + 16, s2Y, "CANOPY DISPLAY", "visor mask intensity");

        int s3Y = top + 132;
        graphics.fill(left + 12, s3Y - 6, right - 12, s3Y - 5, SECTION_DIVIDER);
        drawSectionLabel(graphics, left + 16, s3Y, "INTERFACE SCALE", "size of worn HUD elements");

        int sNvY = top + 178;
        graphics.fill(left + 12, sNvY - 6, right - 12, sNvY - 5, SECTION_DIVIDER);
        drawSectionLabel(graphics, left + 16, sNvY, "NIGHT VISION", "toggle key: " + currentNvKeyText());

        int s4Y = top + 178 + NV_BLOCK_H;
        graphics.fill(left + 12, s4Y - 6, right - 12, s4Y - 5, SECTION_DIVIDER);
        drawSectionLabel(graphics, left + 16, s4Y, "LINKED NODES", "right-click on block to assign");

        PilotHelmetData data = PilotHelmetItem.getData(this.currentHelmet());
        int linkY = s4Y + 14;
        int linkRowH = 18;
        int valueColX = 16 + this.font.width("BATTLE BRIDGE") + 22;
        drawLinkRow(graphics, left + 16, linkY, PANEL_W - 32, linkRowH, "FCC", data.getFccLink(), valueColX);
        linkY += linkRowH + 2;
        drawLinkRow(graphics, left + 16, linkY, PANEL_W - 32, linkRowH, "BATTLE BRIDGE", data.getBattleBridgeLink(), valueColX);
        linkY += linkRowH + 2;
        drawLinkRow(graphics, left + 16, linkY, PANEL_W - 32, linkRowH, "STATS DISPLAY", data.getStatsDisplayLink(), valueColX);

        int footerY = bottom - 30;
        graphics.fill(left + 2, footerY, right - 2, footerY + 1, SECTION_DIVIDER);
        String footerMsg = "Fighter HUD active.";
        int footerAvail = (PANEL_W - 70) - 12 - 6;
        if (this.font.width(footerMsg) > footerAvail) {
            footerMsg = this.font.plainSubstrByWidth(footerMsg, footerAvail - this.font.width("…")) + "…";
        }
        graphics.drawString(this.font, footerMsg, left + 12, footerY + 10, FOOTER_TEXT, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSectionLabel(GuiGraphics graphics, int x, int y, String label, String sublabel) {
        graphics.drawString(this.font, label, x, y, SECTION_LABEL, false);
        if (sublabel != null && !sublabel.isEmpty()) {
            int lw = this.font.width(label);
            graphics.drawString(this.font, "// " + sublabel, x + lw + 8, y, SECTION_LABEL_DIM, false);
        }
    }

    private void drawLinkRow(GuiGraphics graphics, int x, int y, int w, int h, String label, PilotHelmetData.LinkedNode link, int valueColRel) {
        graphics.fill(x, y, x + w, y + h, LINK_FRAME_BG);
        graphics.fill(x, y, x + w, y + 1, LINK_FRAME);
        graphics.fill(x, y + h - 1, x + w, y + h, LINK_FRAME);
        graphics.fill(x, y, x + 1, y + h, LINK_FRAME);
        graphics.fill(x + w - 1, y, x + w, y + h, LINK_FRAME);
        int dotColor = link.isLinked() ? STATUS_OK : STATUS_OFF;
        graphics.fill(x + 6, y + h / 2 - 2, x + 10, y + h / 2 + 2, dotColor);
        graphics.drawString(this.font, label, x + 16, y + (h - 8) / 2, LINK_LABEL, false);
        String value;
        int valueColor;
        if (link.isLinked()) {
            BlockPos pos = link.getTargetPos();
            String name = link.getTargetName();
            String coord = pos == null ? "" : "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
            String displayName = (name == null || name.isEmpty()) ? "Linked" : name;

            int available = w - valueColRel - 6;
            value = displayName + "  " + coord;
            if (this.font.width(value) > available) {
                if (this.font.width(coord) <= available) {
                    value = coord;
                } else {
                    value = this.font.plainSubstrByWidth(value, available - this.font.width("…")) + "…";
                }
            }
            valueColor = LINK_VALUE_OK;
        } else {
            value = "Not linked";
            valueColor = LINK_VALUE_OFF;
        }
        graphics.drawString(this.font, value, x + valueColRel, y + (h - 8) / 2, valueColor, false);
    }

    private ItemStack currentHelmet() {
        Player player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getItemInHand(this.hand);
    }

    private void pushSettings() {
        PacketDistributor.sendToServer(new PilotHelmetSettingsPacket(
            this.hand, this.pendingMode, this.pendingAutoIfSeated,
            this.pendingVignetteStrength, this.pendingHudScaleFactor,
            this.pendingNightVisionEnabled, this.pendingNightVisionMode));
    }

    private String nvToggleLabel() {
        return "NV: " + (this.pendingNightVisionEnabled ? "ON" : "OFF") + " [" + currentNvKeyText() + "]";
    }

    private String nvModeLabel() {
        return "NV " + this.pendingNightVisionMode.getDisplayName();
    }

    private static String currentNvKeyText() {
        try {
            return ModKeyMappings.TOGGLE_HELMET_NIGHT_VISION.getTranslatedKeyMessage().getString();
        } catch (Throwable ignored) {
            return "G";
        }
    }

    private static PilotHelmetData.NightVisionMode nextNvMode(PilotHelmetData.NightVisionMode current) {
        PilotHelmetData.NightVisionMode[] modes = PilotHelmetData.NightVisionMode.values();
        return modes[(current.ordinal() + 1) % modes.length];
    }

    private String hudScaleLabel() {
        String suffix = (Math.abs(this.pendingHudScaleFactor - DEFAULT_HUD_SCALE_FACTOR) < 0.001f) ? " (default)" : "";
        return "HUD Scale: " + formatHudScale(this.pendingHudScaleFactor) + "x" + suffix;
    }

    private static String formatHudScale(float factor) {
        return (factor == Math.floor(factor)) ? String.valueOf((int) factor) : String.valueOf(factor);
    }

    private static float nextHudScale(float current) {
        for (float v : HUD_SCALE_CYCLE) {
            if (v > current + 0.001f) {
                return v;
            }
        }
        return HUD_SCALE_CYCLE[0];
    }

    private String autoLabel() {
        return this.pendingAutoIfSeated ? "Seat Auto: On" : "Seat Auto: Off";
    }

    private static PilotHelmetData.Mode nextMode(PilotHelmetData.Mode current) {
        PilotHelmetData.Mode[] modes = PilotHelmetData.Mode.values();
        return modes[(current.ordinal() + 1) % modes.length];
    }

    private class VignetteSlider extends AbstractSliderButton {
        VignetteSlider(int x, int y, int w, int h, int initialStrength) {
            super(x, y, w, h, Component.empty(), Math.max(0, Math.min(100, initialStrength)) / 100.0D);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal("Canopy Vignette: " + Math.round(this.value * 100.0D) + "%"));
        }

        @Override
        protected void applyValue() {
            PilotHelmetScreen.this.pendingVignetteStrength = (int) Math.round(this.value * 100.0D);
            PilotHelmetScreen.this.pushSettings();
        }
    }
}
