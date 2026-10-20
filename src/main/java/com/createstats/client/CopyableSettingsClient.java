package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.util.CopyableSettings;
import com.createstats.util.CopyableSettingsHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class CopyableSettingsClient {
    private CopyableSettingsClient() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.hitResult instanceof BlockHitResult target)) {
            return;
        }
        if (!AllBlocks.CLIPBOARD.isIn(mc.player.getMainHandItem())) {
            return;
        }
        final CopyableSettings settings = CopyableSettingsHandler.target(mc.level.getBlockEntity(target.getBlockPos()));
        if (settings == null) {
            return;
        }
        final ClipboardContent content = mc.player.getMainHandItem().get(AllDataComponents.CLIPBOARD_CONTENT);
        if (content == null) {
            return;
        }
        final CompoundTag values = content.copiedValues().orElse(null);
        final boolean canCopy = settings.writeToClipboard(mc.level.registryAccess(), new CompoundTag(),
                target.getDirection());
        final boolean canPaste = values != null && settings.readFromClipboard(mc.level.registryAccess(),
                values.getCompound(settings.getClipboardKey()), mc.player, target.getDirection(), true);
        if (!canCopy && !canPaste) {
            return;
        }
        final List<MutableComponent> tip = new ArrayList<>();
        tip.add(CreateLang.translateDirect("clipboard.actions"));
        if (canCopy) {
            tip.add(CreateLang.translateDirect("clipboard.to_copy", Component.keybind("key.use")));
        }
        if (canPaste) {
            tip.add(CreateLang.translateDirect("clipboard.to_paste", Component.keybind("key.attack")));
        }
        CreateClient.VALUE_SETTINGS_HANDLER.showHoverTip(tip);
    }

    @SubscribeEvent
    public static void outline(RenderHighlightEvent.Block event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.isSpectator()) {
            return;
        }
        final BlockPos pos = event.getTarget().getBlockPos();
        if (!mc.level.getWorldBorder().isWithinBounds(pos) || !AllBlocks.CLIPBOARD.isIn(mc.player.getMainHandItem())) {
            return;
        }
        if (CopyableSettingsHandler.target(mc.level.getBlockEntity(pos)) == null) {
            return;
        }
        final VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        if (shape.isEmpty()) {
            return;
        }
        final VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack ms = event.getPoseStack();
        ms.pushPose();
        ms.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        TrackBlockOutline.renderShape(shape, ms, lines, true);
        event.setCanceled(true);
        ms.popPose();
    }
}
