package com.createstats.client.train;

import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.network.TrainChargeSyncPacket;
import com.createstats.power.FeGoggle;
import com.createstats.train.power.CarriedReadout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.equipment.goggles.IHaveCustomOverlayIcon;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.TrainRelocator;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import com.simibubi.create.foundation.gui.RemovedGuiUtils;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CClient;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ContraptionGoggleOverlay {
    private static int hoverTicks;
    private static long lastTarget = Long.MIN_VALUE;

    private ContraptionGoggleOverlay() {
    }

    private record Target(AbstractContraptionEntity entity, BlockPos localPos) {
    }

    public static boolean render(GuiGraphics graphics, DeltaTracker delta) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui
                || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR
                || !GogglesItem.isWearingGoggles(mc.player) || valueBoxOpen()) {
            forget();
            return false;
        }
        final Target target = find(mc);
        if (target == null) {
            forget();
            return false;
        }
        final long key = ((long) target.entity().getId() << 32) ^ target.localPos().asLong();
        hoverTicks = key == lastTarget ? hoverTicks + 1 : 0;
        lastTarget = key;

        final boolean sneaking = mc.player.isShiftKeyDown();
        final BlockEntity copy = target.entity().getContraption().getBlockEntityClientSide(target.localPos());
        final List<Component> tooltip = new ArrayList<>();
        if (copy instanceof IHaveGoggleInformation info) {
            CarriedReadout.set(true);
            try {
                info.addToGoggleTooltip(tooltip, sneaking);
            } finally {
                CarriedReadout.set(false);
            }
        }
        if (copy instanceof com.createstats.powertrack.CurrentCollectorBE) {
            com.createstats.powertrack.client.CollectorReadout.carried(tooltip, target.entity(), target.localPos());
        }
        if (target.entity() instanceof CarriageContraptionEntity car && car.trainId != null) {
            appendTrain(tooltip, ClientTrainCharge.get(car.trainId), sneaking, copy instanceof CapacitorBankBE);
        }
        if (tooltip.isEmpty()) {
            return false;
        }
        final List<Component> hint = new ArrayList<>();
        if (TrainRelocator.addToTooltip(hint, sneaking)) {
            tooltip.add(CommonComponents.EMPTY);
            tooltip.addAll(hint);
        }
        final ItemStack icon = copy instanceof IHaveCustomOverlayIcon custom
                ? custom.getIcon(sneaking) : AllItems.GOGGLES.asStack();
        draw(graphics, delta, tooltip, icon);
        return true;
    }

    private static void forget() {
        hoverTicks = 0;
        lastTarget = Long.MIN_VALUE;
    }

    private static boolean valueBoxOpen() {
        for (var entry : Outliner.getInstance().getOutlines().values()) {
            if (entry.isAlive() && entry.getOutline() instanceof ValueBox box && !box.isPassive) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Target find(Minecraft mc) {
        final Couple<Vec3> rays = ContraptionHandlerClient.getRayInputs(mc.player);
        final Vec3 origin = rays.getFirst();
        final Vec3 end = rays.getSecond();
        final AABB reach = new AABB(origin, end).inflate(16);
        Target best = null;
        double bestDistance = Double.MAX_VALUE;
        for (WeakReference<AbstractContraptionEntity> ref
                : ContraptionHandler.loadedContraptions.get(mc.level).values()) {
            final AbstractContraptionEntity entity = ref.get();
            if (entity == null || entity.getContraption() == null
                    || !entity.getBoundingBox().intersects(reach)) {
                continue;
            }
            final BlockHitResult hit = ContraptionHandlerClient.rayTraceContraption(origin, end, entity);
            if (hit == null) {
                continue;
            }
            final double distance = entity.toGlobalVector(hit.getLocation(), 1).distanceTo(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Target(entity, hit.getBlockPos());
            }
        }
        return best;
    }

    private static void appendTrain(List<Component> tooltip, @Nullable TrainChargeSyncPacket.Summary summary,
                                    boolean sneaking, boolean onBank) {
        if (summary == null || summary.capacity() <= 0) {
            return;
        }
        if (!tooltip.isEmpty()) {
            tooltip.add(CommonComponents.EMPTY);
        }
        tooltip.add(FeGoggle.title("Train charge"));
        tooltip.add(FeGoggle.stored(summary.stored(), summary.capacity()));
        if (summary.netRate() != 0) {
            tooltip.add(FeGoggle.chargeFlow(summary.netRate()));
            final int ticks = summary.netRate() > 0
                    ? (summary.capacity() - summary.stored()) / summary.netRate()
                    : summary.stored() / -summary.netRate();
            if (ticks > 0) {
                tooltip.add(FeGoggle.dim((summary.netRate() > 0 ? "Full in ~" : "Empty in ~")
                        + FeGoggle.time(ticks)));
            }
        }
        if ((sneaking || onBank) && summary.lowestCar() >= 0) {
            tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "Lowest: car %d at %d%%",
                    summary.lowestCar() + 1, summary.lowestPct())));
        }
    }

    private static void draw(GuiGraphics graphics, DeltaTracker delta, List<Component> tooltip, ItemStack icon) {
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
        GuiGameElement.of(icon).at(x + 10, y - 16, 450).render(graphics);
        RemovedGuiUtils.drawHoveringText(graphics, tooltip, x, y, width, screenHeight, -1,
                background.getRGB(), borderTop.getRGB(), borderBottom.getRGB(), mc.font);
        pose.popPose();
    }
}
