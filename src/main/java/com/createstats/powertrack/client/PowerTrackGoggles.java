package com.createstats.powertrack.client;

import com.createstats.power.FeGoggle;
import com.createstats.power.FeRates;
import com.createstats.powertrack.SectionView;
import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.RailPower;
import com.createstats.powertrack.core.SectionGraph;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.foundation.gui.RemovedGuiUtils;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CClient;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// straight track has no BE, so draw create's tooltip box ourselves
public final class PowerTrackGoggles {
    private static int hoverTicks;
    private static long lastTarget = Long.MIN_VALUE;

    private PowerTrackGoggles() {
    }

    public static boolean render(GuiGraphics graphics, DeltaTracker delta) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.gameMode == null
                || mc.gameMode.getPlayerMode() == GameType.SPECTATOR || !GogglesItem.isWearingGoggles(mc.player)
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !(mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof ITrackBlock)) {
            forget();
            return false;
        }
        final Vec3 at = hit.getLocation();
        final ClientRail.Under under = ClientRail.railUnder(mc.level, at.x, at.y, at.z, 0.9, -0.6, 1.2);
        if (under == null) {
            forget();
            return false;
        }
        final long key = hit.getBlockPos().asLong();
        hoverTicks = key == lastTarget ? hoverTicks + 1 : 0;
        lastTarget = key;
        final List<Component> tooltip = lines(under, mc.player.isShiftKeyDown());
        draw(graphics, delta, tooltip);
        return true;
    }

    static List<Component> lines(ClientRail.Under under, boolean sneaking) {
        final List<Component> tooltip = new ArrayList<>();
        final RailLayout.Hit hit = under.hit();
        tooltip.add(FeGoggle.title(Component.translatable("block.create_stats.power_track")));
        final SectionView view = under.view();
        if (hit.contact()) {
            tooltip.add(view == null ? FeGoggle.dim("    Conductor rail") : view.stateLine("    Section "));
        } else {
            final SectionGraph.Gap gap = under.gap();
            tooltip.add(FeGoggle.warn("    " + gapLine(gap)));
            tooltip.add(FeGoggle.dim("    No rail: a shoe here touches nothing"));
            if (view != null) {
                tooltip.add(view.stateLine(gap == SectionGraph.Gap.INSULATED || gap == SectionGraph.Gap.END
                        ? "    Nearest section " : "    Section "));
            }
        }
        if (sneaking) {
            if (view != null) {
                tooltip.add(view.countsLine());
            }
            final int base = FeRates.base();
            tooltip.add(FeGoggle.dim("    Rated " + FeRates.perTick(RailPower.sectionCeiling(base)) + " a section, "
                    + FeRates.perTick(RailPower.collectorRate(base)) + " a shoe"));
        }
        return tooltip;
    }

    static String gapLine(SectionGraph.Gap gap) {
        return switch (gap) {
            case INSULATED -> "Insulated gap: the section ends";
            case JUNCTION -> "Junction gap: jumpers carry it on";
            case GRADE -> "Grade change gap: jumpers carry it on";
            case CLIMBING_CURVE -> "Climbing curve: jumpers carry it on";
            case SHORT -> "Too short to lay: jumpers carry it on";
            case END -> "End of electrification";
            case NONE -> "Conductor rail";
        };
    }

    private static void forget() {
        hoverTicks = 0;
        lastTarget = Long.MIN_VALUE;
    }

    private static void draw(GuiGraphics graphics, DeltaTracker delta, List<Component> tooltip) {
        final Minecraft mc = Minecraft.getInstance();
        final PoseStack pose = graphics.pose();
        pose.pushPose();
        int textWidth = 0;
        for (FormattedText line : tooltip) {
            textWidth = Math.max(textWidth, mc.font.width(line));
        }
        int height = 8;
        if (tooltip.size() > 1) {
            height += 2 + (tooltip.size() - 1) * 10;
        }
        final int width = graphics.guiWidth();
        final int screenHeight = graphics.guiHeight();
        final CClient cfg = AllConfigs.client();
        final int x = Math.min(width / 2 + cfg.overlayOffsetX.get(), width - textWidth - 20);
        final int y = Math.min(screenHeight / 2 + cfg.overlayOffsetY.get(), screenHeight - height - 20);
        final float fade = Mth.clamp((hoverTicks + delta.getGameTimeDeltaPartialTick(false)) / 24f, 0, 1);
        final boolean custom = cfg.overlayCustomColor.get();
        final Color background = custom ? new Color(cfg.overlayBackgroundColor.get())
                : BoxElement.COLOR_VANILLA_BACKGROUND.scaleAlpha(.75f);
        final Color borderTop = custom ? new Color(cfg.overlayBorderColorTop.get())
                : BoxElement.COLOR_VANILLA_BORDER.getFirst().copy();
        final Color borderBottom = custom ? new Color(cfg.overlayBorderColorBot.get())
                : BoxElement.COLOR_VANILLA_BORDER.getSecond().copy();
        if (fade < 1) {
            pose.translate(Math.pow(1 - fade, 3) * Math.signum(cfg.overlayOffsetX.get() + .5f) * 8, 0, 0);
            background.scaleAlpha(fade);
            borderTop.scaleAlpha(fade);
            borderBottom.scaleAlpha(fade);
        }
        GuiGameElement.of(AllItems.GOGGLES.asStack()).at(x + 10, y - 16, 450).render(graphics);
        RemovedGuiUtils.drawHoveringText(graphics, tooltip, x, y, width, screenHeight, -1,
                background.getRGB(), borderTop.getRGB(), borderBottom.getRGB(), mc.font);
        pose.popPose();
    }
}
