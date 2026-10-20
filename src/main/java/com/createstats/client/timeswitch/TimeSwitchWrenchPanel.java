package com.createstats.client.timeswitch;

import com.createstats.blockentity.BlastShutterBE;
import com.createstats.blockentity.SwitchBE;
import com.createstats.timeswitch.TimeSwitchDial;
import com.createstats.timeswitch.TimeSwitchProgram;
import com.createstats.timeswitch.TimeSwitchProgram.Mode;
import com.createstats.timeswitch.TimeSwitchProgram.Preset;
import com.createstats.timeswitch.TimeSwitchText;
import com.simibubi.create.AllItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class TimeSwitchWrenchPanel {
    private static final int OUTLINE = 0xFF000000;
    private static final int HIGHLIGHT = 0xFFFFFFFF;
    private static final int FACE = 0xFFC6C6C6;
    private static final int RECESSED = 0xFF8B8B8B;
    private static final int SHADOW = 0xFF555555;
    private static final int LABEL = 0xFF404040;
    private static final int GOOD = 0xFF2E7D32;
    private static final int WARN = 0xFF9A5B0C;

    private static final int PAD = 5;
    private static final int TAB_H = 12;

    private static final int LIFT = 62;

    private static final Preset[] TABS = {Preset.DUSK_TO_DAWN, Preset.DAYTIME, Preset.CLEAR, Preset.INVERT};

    @Nullable
    private static BlockPos target;

    private static int picked;

    private TimeSwitchWrenchPanel() {
    }

    @Nullable
    static Preset picked(BlockPos pos) {
        return pos.equals(target) && picked > 0 ? TABS[picked - 1] : null;
    }

    static void applied() {
        picked = 0;
        if (target != null) {
            TimeSwitchView.clearPreview(target);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        final SwitchBE be = hovered(Minecraft.getInstance());
        if (be == null) {
            release();
            return;
        }
        if (!be.getBlockPos().equals(target)) {
            release();
            target = be.getBlockPos().immutable();
        }
        publishPreview(be);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        final Minecraft mc = Minecraft.getInstance();
        final SwitchBE be = hovered(mc);
        if (be == null || !be.getBlockPos().equals(target) || event.getScrollDeltaY() == 0.0) {
            return;
        }

        event.setCanceled(true);
        final int n = TABS.length + 1;
        picked = Math.floorMod(picked + (event.getScrollDeltaY() < 0 ? 1 : -1), n);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f, 0.35f));
        publishPreview(be);
    }

    @EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Layer {
        private Layer() {
        }

        @SubscribeEvent
        public static void register(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT,
                    ResourceLocation.fromNamespaceAndPath("create_stats", "time_switch_wrench"),
                    TimeSwitchWrenchPanel::render);
        }
    }

    @Nullable
    private static SwitchBE hovered(Minecraft mc) {
        if (mc.player == null || mc.screen != null || !AllItems.WRENCH.isIn(mc.player.getMainHandItem())) {
            return null;
        }
        return TimeSwitchInput.target(mc);
    }

    private static void release() {
        if (target != null) {
            TimeSwitchView.clearPreview(target);
        }
        target = null;
        picked = 0;
    }

    private static void publishPreview(SwitchBE be) {
        if (picked > 0) {
            TimeSwitchView.preview(be.getBlockPos(), result(TABS[picked - 1], TimeSwitchView.shownPins(be)));
        } else {
            TimeSwitchView.clearPreview(be.getBlockPos());
        }
    }

    private static int result(Preset preset, int current) {
        return TimeSwitchProgram.preset(preset, current, BlastShutterBE.DUSK, BlastShutterBE.DAWN);
    }

    // ---------- Drawing ----------

    private static void render(GuiGraphics g, DeltaTracker delta) {
        final Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.options.hideGui || mc.level == null
                || !(mc.level.getBlockEntity(target) instanceof SwitchBE be)) {
            return;
        }
        draw(g, mc.font, be, mc.level.getDayTime());
    }

    static void draw(GuiGraphics g, Font font, SwitchBE be, long dayTime) {
        final int current = TimeSwitchView.shownPins(be);
        final Mode mode = TimeSwitchView.shownMode(be);
        final boolean closed = be.isClosed();
        final int shownPins = picked > 0 ? result(TABS[picked - 1], current) : current;

        final Component title = Component.translatable("block.create_stats.time_switch");
        final Component state = Component.translatable("create_stats.time_switch.panel.state",
                TimeSwitchText.mode(mode), Component.translatable(closed
                        ? "create_stats.time_switch.panel.closed" : "create_stats.time_switch.panel.open"));
        final Component detail = detail(be, current, mode, closed, dayTime);
        final Component hint = Component.translatable("create_stats.time_switch.panel.hint");

        final Component[] names = new Component[TABS.length + 1];
        names[0] = Component.translatable("create_stats.time_switch.preset.keep");
        for (int k = 0; k < TABS.length; k++) {
            names[k + 1] = TimeSwitchText.preset(TABS[k]);
        }
        int tabs = 0;
        for (Component name : names) {
            tabs += font.width(name) + 8 + 2;
        }
        tabs -= 2;

        final int inner = Math.max(Math.max(tabs, TimeSwitchStrip.WIDTH),
                Math.max(font.width(title) + 8 + font.width(state), Math.max(font.width(detail), font.width(hint))));
        final int w = inner + PAD * 2;
        final int h = PAD + 10 + 10 + 3 + TimeSwitchStrip.HEIGHT_LABELLED + 3 + TAB_H + 4 + 11 + PAD - 2;
        final int x = (g.guiWidth() - w) / 2;
        final int y = g.guiHeight() - LIFT - h;

        bevel(g, x, y, w, h);
        int row = y + PAD;
        g.drawString(font, title, x + PAD, row, LABEL, false);
        g.drawString(font, state, x + w - PAD - font.width(state), row, closed ? GOOD : WARN, false);
        row += 10;
        g.drawString(font, detail, x + PAD, row, LABEL, false);
        row += 10 + 3;
        TimeSwitchStrip.draw(g, font, x + (w - TimeSwitchStrip.WIDTH) / 2, row, shownPins,
                picked > 0 ? current : -1, TimeSwitchProgram.hourOf(dayTime), true);
        row += TimeSwitchStrip.HEIGHT_LABELLED + 3;
        int tx = x + PAD;
        for (int k = 0; k < names.length; k++) {
            final int tw = font.width(names[k]) + 8;
            tab(g, tx, row, tw, k == picked);
            g.drawString(font, names[k], tx + 4, row + 2, LABEL, false);
            tx += tw + 2;
        }
        row += TAB_H + 4;
        g.fill(x + PAD, row - 2, x + w - PAD, row - 1, RECESSED);
        g.drawString(font, hint, x + PAD, row + 1, SHADOW, false);
    }

    private static Component detail(SwitchBE be, int pins, Mode mode, boolean closed, long dayTime) {
        final TimeSwitchDial.Aim aim = TimeSwitchView.aimed(be.getBlockPos());
        return switch (aim.kind()) {
            case PIN -> TimeSwitchText.pin(aim.index(), TimeSwitchProgram.isIn(pins, aim.index()));
            case MODE -> TimeSwitchText.detent(Mode.byId(aim.index()), aim.index() == mode.id());
            case HUB -> Component.translatable("create_stats.time_switch.hover.hub");
            default -> {
                final Component next = TimeSwitchText.next(closed, pins, mode, dayTime);

                yield next == null ? TimeSwitchText.program(pins, 2)
                        : Component.translatable("create_stats.time_switch.panel.program_next",
                        TimeSwitchText.program(pins, 2), next);
            }
        };
    }

    private static void bevel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, FACE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, HIGHLIGHT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, SHADOW);
    }

    private static void tab(GuiGraphics g, int x, int y, int w, boolean on) {
        g.fill(x, y, x + w, y + TAB_H, on ? HIGHLIGHT : SHADOW);
        g.fill(x + 1, y + 1, x + w - 1, y + TAB_H - 1, on ? FACE : RECESSED);
        if (on) {
            g.fill(x + 1, y + TAB_H - 1, x + w, y + TAB_H, SHADOW);
            g.fill(x + w - 1, y + 1, x + w, y + TAB_H, SHADOW);
        }
    }
}
