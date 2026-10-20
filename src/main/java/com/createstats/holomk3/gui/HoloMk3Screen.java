package com.createstats.holomk3.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.createstats.blockentity.HolographicProjectorBE.DataChannel;
import com.createstats.data.MetricType;
import com.createstats.holomk3.HoloMk3Settings;
import com.createstats.holomk3.HoloProjectorMk3BE;
import com.createstats.holomk3.scene.SceneBuilder;
import com.createstats.network.HoloMk3ConfigPacket;
import com.createstats.statsmk3.ui.DrawCtx;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Widget;

@OnlyIn(Dist.CLIENT)
public class HoloMk3Screen extends Screen {
    private final HoloProjectorMk3BE projector;
    private final HoloConsole.Model model = new HoloConsole.Model();

    private HoloConsole.Layout layout;
    private List<Widget> widgets = new ArrayList<>();
    private long openedAtNanos;

    public HoloMk3Screen(HoloProjectorMk3BE projector) {
        super(Component.translatable("gui.create_stats.holo_mk3.title"));
        this.projector = projector;
        readFromBlock();
    }

    private void readFromBlock() {
        final HoloMk3Settings live = this.projector.mk3Settings();
        this.model.cfg = HoloMk3Settings.load(live.save());
        this.model.title = Component.translatable("block.create_stats.holographic_projector_mk3")
                .getString();
        this.model.position = this.projector.getBlockPos().toShortString();
        this.model.scanEnabled = this.projector.isScanEnabled();
        this.model.redstoneEnabled = this.projector.isRedstoneEnabled();
        this.model.redstoneChannelId = this.projector.getRedstoneChannelId();
        this.model.redstoneThreshold = this.projector.getRedstoneThreshold();
        this.model.redstoneAbove = this.projector.isRedstoneAbove();
        this.model.redstoneMode = this.projector.getRedstoneMode();
        this.model.hidden.clear();
        this.model.hidden.addAll(this.projector.getHologramSettings().getHiddenChannelIds());

        final var fe = this.projector.feConsumer();
        this.model.power = fe != null && fe.requiresPower() ? fe.fillPct() / 100.0f : -1.0f;

        this.model.channels.clear();
        for (final DataChannel ch : this.projector.getAllChannels().values()) {
            final MetricType kind = ch.getStore().getMetricType();
            final boolean live0 = ch.getStore().getSampleCount() > 0;
            this.model.channels.add(new HoloConsole.Channel(ch.getId(), ch.getLabel(),
                    live0 ? kind.formatValue(ch.getStore().getLatestValue()) : "",
                    live0));
        }
    }

    @Override
    protected void init() {
        this.openedAtNanos = System.nanoTime();
        rebuild();
    }

    private void rebuild() {
        this.layout = HoloConsole.layout(this.width, this.height);
        this.widgets = HoloConsole.controls(this.model, this.layout);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        final float seconds = (System.nanoTime() - this.openedAtNanos) / 1.0e9f;

        this.model.preview = SceneBuilder.build(
                this.projector.getAllChannels().values(),
                this.model.hidden,
                this.model.cfg,
                this.projector.getLevel() == null ? 0L : this.projector.getLevel().getGameTime(),
                seconds, 1.0f, 96);

        for (final Widget widget : this.widgets) {
            widget.update(partialTick / 20.0f);
            widget.mouseMoved(mouseX, mouseY);
        }

        final DrawCtx ctx = new DrawCtx(graphics, this.font);
        HoloConsole.paint(ctx, this.model, this.layout, this.widgets, seconds);
        ctx.flush();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < HoloConsole.Tab.values().length; i++) {
            if (HoloConsole.tabRect(this.layout.rail(), i).contains(mx, my)) {
                this.model.tab = HoloConsole.Tab.values()[i];
                rebuild();
                return true;
            }
        }
        for (final Widget widget : this.widgets) {
            if (widget.mouseDown(mx, my, button)) {
                return true;
            }
        }

        final Rect list = HoloConsole.channelList(this.layout, this.model);
        if (!list.isEmpty()) {
            for (int i = 0; i < this.model.channels.size(); i++) {
                final Rect row = HoloConsole.channelRect(list, i);
                if (!row.contains(mx, my)) {
                    continue;
                }
                final HoloConsole.Channel ch = this.model.channels.get(i);
                if (mx < row.x + 10.0f) {
                    this.model.toggleHidden(ch.id());
                } else {
                    this.model.cfg.setFocusChannelId(
                            ch.id().equals(this.model.cfg.getFocusChannelId()) ? "" : ch.id());
                    this.model.touch();
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean handled = false;
        for (final Widget widget : this.widgets) {
            handled |= widget.mouseUp(mx, my, button);
        }
        return handled || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        for (final Widget widget : this.widgets) {
            if (widget.mouseDragged(mx, my, button, dx, dy)) {
                return true;
            }
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int width, int height) {
        super.resize(mc, width, height);
        rebuild();
    }

    @Override
    public void onClose() {
        if (this.model.dirty) {
            apply();
        }
        super.onClose();
    }

    private void apply() {
        final CompoundTag hologram = this.projector.getHologramSettings().save();

        final net.minecraft.nbt.ListTag hiddenList = new net.minecraft.nbt.ListTag();
        for (final String id : this.model.hidden) {
            hiddenList.add(net.minecraft.nbt.StringTag.valueOf(id));
        }
        hologram.put("HiddenChannels", hiddenList);

        PacketDistributor.sendToServer(new HoloMk3ConfigPacket(
                this.projector.getBlockPos(),
                this.model.cfg.save(),
                hologram,
                this.model.redstoneEnabled,
                this.model.redstoneChannelId,
                this.model.redstoneThreshold,
                this.model.redstoneAbove,
                this.model.redstoneMode,
                this.model.scanEnabled));

        this.projector.setMk3Settings(HoloMk3Settings.load(this.model.cfg.save()));
        this.model.dirty = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
