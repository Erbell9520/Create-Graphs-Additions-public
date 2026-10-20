package com.createstats.client.tuner;

import com.createstats.network.TunerApplyPacket;
import com.createstats.tuner.TunerBand;
import com.createstats.tuner.TunerHost;
import com.createstats.tuner.TunerRange;
import com.createstats.tuner.TunerStation;
import com.simibubi.create.AllItems;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class TunerWrenchPanel {
    private static final int OUTLINE = 0xFF000000;
    private static final int HIGHLIGHT = 0xFFFFFFFF;
    private static final int FACE = 0xFFC6C6C6;
    private static final int RECESSED = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SHADOW = 0xFF555555;
    private static final int LABEL = 0xFF404040;
    private static final int WARN = 0xFFAA0000;

    private static final int PAD = 5;

    static final int LIFT = 62;
    private static final long PENDING_MS = 3_000L;

    private static final Object TOKEN = new Object();

    @Nullable
    private static BlockPos target;
    @Nullable
    private static String pendingStation;
    private static int pendingRange = -1;
    private static long pendingAt;

    private TunerWrenchPanel() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        final BlockEntity be = hovered(mc);
        if (!(be instanceof TunerHost host)) {
            release();
            return;
        }
        final BlockPos pos = be.getBlockPos();
        if (!pos.equals(target)) {
            release();
            target = pos.immutable();
        }
        TunerSurveys.request(pos);

        final long now = Util.getMillis();
        if (pendingStation != null && (pendingStation.equals(host.tunedStation()) || now - pendingAt > PENDING_MS)) {
            pendingStation = null;
        }
        if (pendingRange >= 0 && (pendingRange == host.tunedRange() || now - pendingAt > PENDING_MS)) {
            pendingRange = -1;
        }
        TunerPreviews.show(TOKEN, pos, TunerSurveys.find(pos, station(host)), range(host), host.tunedRange());
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        final Minecraft mc = Minecraft.getInstance();
        final BlockEntity be = hovered(mc);
        if (!(be instanceof TunerHost host) || !be.getBlockPos().equals(target) || event.getScrollDeltaY() == 0.0) {
            return;
        }

        event.setCanceled(true);
        if (host.tunerLock() != null) {
            TunerSounds.stop();
            return;
        }
        final int direction = event.getScrollDeltaY() > 0 ? 1 : -1;
        final String station = station(host);
        final int range = range(host);
        if (mc.player != null && mc.player.isShiftKeyDown()) {
            final List<String> dial = dial(host, target, station, range);
            final int at = dial.indexOf(station);
            final int next = Math.max(0, Math.min(dial.size() - 1, (at < 0 ? 0 : at) + direction));
            final String picked = dial.get(next);
            if (picked.equals(station)) {
                TunerSounds.stop();
                return;
            }
            final TunerStation s = TunerSurveys.find(target, picked);
            final TunerBand band = host.dialBand();
            if (s == null) {
                TunerSounds.stop();
            } else {
                TunerSounds.sweep(band != null ? band.fraction(s.dial()) : next / (double) Math.max(1, dial.size() - 1));
            }
            send(target, picked, range);
            return;
        }
        final TunerRange r = host.tunerRange();
        final int next = r.step(range, direction);
        if (next == range) {
            TunerSounds.stop();
            return;
        }
        TunerSounds.detent(r.notchOf(next), r.notches());
        send(target, station, next);
    }

    @EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Layer {
        private Layer() {
        }

        @SubscribeEvent
        public static void register(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT,
                    ResourceLocation.fromNamespaceAndPath("create_stats", "tuner_wrench"), TunerWrenchPanel::render);
        }
    }

    @Nullable
    private static BlockEntity hovered(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.screen != null || mc.player.isSpectator()
                || !AllItems.WRENCH.isIn(mc.player.getMainHandItem())) {
            return null;
        }
        final HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        final BlockPos at = TunerHost.hostPos(mc.level, blockHit.getBlockPos());
        final BlockEntity be = at == null ? null : mc.level.getBlockEntity(at);
        return be instanceof TunerHost ? be : null;
    }

    private static void release() {
        target = null;
        pendingStation = null;
        pendingRange = -1;
        TunerPreviews.release(TOKEN);
    }

    private static void send(BlockPos pos, String station, int range) {
        pendingStation = station;
        pendingRange = range;
        pendingAt = Util.getMillis();
        PacketDistributor.sendToServer(new TunerApplyPacket(pos, station, range));
    }

    private static String station(TunerHost host) {
        return pendingStation != null ? pendingStation : host.tunedStation();
    }

    private static int range(TunerHost host) {
        return pendingRange >= 0 ? pendingRange : host.tunedRange();
    }

    private static List<String> dial(TunerHost host, BlockPos pos, String station, int range) {
        final List<TunerStation> survey = TunerSurveys.of(pos);
        final List<TunerStation> shown = new ArrayList<>();
        if (survey != null) {
            for (TunerStation s : survey) {
                if (s.shownAt(range, host.tunedStation()) || s.id().equals(station)) {
                    shown.add(s);
                }
            }
        }
        if (host.dialBand() != null) {
            shown.sort(Comparator.comparingInt(TunerStation::dial));
        }
        final List<String> out = new ArrayList<>(shown.size() + 1);
        out.add("");
        for (TunerStation s : shown) {
            out.add(s.id());
        }
        if (!out.contains(station)) {
            out.add(station);
        }
        return out;
    }

    // ---------- Drawing ----------

    private static void render(GuiGraphics g, DeltaTracker delta) {
        final Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.options.hideGui || mc.level == null
                || !(mc.level.getBlockEntity(target) instanceof TunerHost host)) {
            return;
        }
        draw(g, mc.font, host, target);
    }

    static void draw(GuiGraphics g, Font font, TunerHost host, BlockPos pos) {
        final String id = station(host);
        final int range = range(host);
        final TunerRange r = host.tunerRange();
        final List<TunerStation> survey = TunerSurveys.of(pos);
        final TunerStation s = TunerSurveys.find(pos, id);

        final Component name;
        final Component sub;
        if (s != null) {
            name = Component.translatable(s.labelKey());
            sub = host.stationLine(s, range, false);
        } else if (!id.isEmpty()) {
            name = Component.literal(id);
            sub = Component.translatable("create_stats.tuner.listening");
        } else {
            name = Component.translatable("create_stats.tuner.no_station");
            sub = Component.translatable("create_stats.tuner.draws_nothing");
        }

        final Component cost = host.costLabel(range, s == null && id.isEmpty());
        final Component what = host.rangeLabel(s, range);
        Component note = null;
        if (survey != null && (survey.isEmpty() || (survey.size() == 1 && survey.get(0).minRange() <= 0))) {
            note = Component.translatable("create_stats.tuner.nothing_spawns", r.max());
        } else if (s != null && s.minRange() > range) {
            note = Component.translatable("create_stats.tuner.out_of_reach", s.minRange());
        } else {
            note = host.tunerNote(s, range);
        }
        final Component lock = host.tunerLock();
        final Component hint = lock != null ? lock : Component.translatable("create_stats.tuner.wrench_hint");

        final int notchPitch = 5;
        final int notchesW = r.notches() * notchPitch;
        final int top = 22 + font.width(name) + 8 + font.width(cost);
        final int mid = font.width(what) + 8 + notchesW;
        int w = Math.max(Math.max(top, mid), Math.max(font.width(hint), 22 + font.width(sub)));
        if (note != null) {
            w = Math.max(w, font.width(note));
        }
        w += PAD * 2;
        final int h = PAD + 18 + 3 + 10 + (note != null ? 10 : 0) + 3 + 10 + PAD - 2;
        final int x = (g.guiWidth() - w) / 2;
        final int y = g.guiHeight() - LIFT - h;

        bevel(g, x, y, w, h);
        slot(g, x + PAD, y + PAD);
        if (s != null) {
            TunerIcons.draw(g, s.icon(), x + PAD + 1, y + PAD + 1, 16);
        }
        g.drawString(font, name, x + PAD + 22, y + PAD, LABEL, false);
        g.drawString(font, sub, x + PAD + 22, y + PAD + 10, SHADOW, false);
        g.drawString(font, cost, x + w - PAD - font.width(cost), y + PAD, LABEL, false);

        int row = y + PAD + 21;
        g.drawString(font, what, x + PAD, row, LABEL, false);
        notches(g, x + w - PAD - notchesW + 1, row + 2, r, range, notchPitch);
        row += 10;
        if (note != null) {
            g.drawString(font, note, x + PAD, row, WARN, false);
            row += 10;
        }
        g.fill(x + PAD, row + 1, x + w - PAD, row + 2, RECESSED);
        g.drawString(font, hint, x + PAD, row + 4, lock != null ? WARN : SHADOW, false);
    }

    private static void notches(GuiGraphics g, int x, int y, TunerRange r, int range, int pitch) {
        final int on = r.notchOf(range);
        for (int i = 0; i < r.notches(); i++) {
            final int px = x + i * pitch;
            g.fill(px, y, px + pitch - 1, y + 4, i == on ? OUTLINE : SHADOW);
            g.fill(px + 1, y + 1, px + pitch - 2, y + 3, i <= on ? HIGHLIGHT : RECESSED);
        }
    }

    private static void bevel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, FACE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, HIGHLIGHT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, SHADOW);
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 18, y + 18, HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 17, y + 17, RECESSED);
    }
}
