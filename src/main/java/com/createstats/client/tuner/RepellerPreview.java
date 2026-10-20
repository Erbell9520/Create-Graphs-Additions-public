package com.createstats.client.tuner;

import com.createstats.registry.ModBlockEntities;
import com.createstats.repeller.VolumeOutline;
import com.createstats.tuner.TunerStation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class RepellerPreview {
    private static final int COLUMN_RING_EVERY = 16;

    private RepellerPreview() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> TunerPreviews.register(ModBlockEntities.MOB_REPELLER.get(), RepellerPreview::draw));
    }

    static void draw(PoseStack.Pose pose, VertexConsumer lines, BlockEntity be, @Nullable TunerStation station,
                     double radius, int argb) {
        if (radius <= 0.0) {
            return;
        }
        final int segments = (int) Math.max(32, Math.min(96, Math.round(radius * 2)));
        final List<double[]> polys;
        if (station != null && station.column()) {
            final Level level = be.getLevel();
            final double centre = be.getBlockPos().getY() + 0.5;
            final double low = (level == null ? -64 : level.getMinBuildHeight()) - centre;
            final double high = (level == null ? 320 : level.getMaxBuildHeight()) - centre;
            final int rings = (int) Math.max(2, Math.round((high - low) / COLUMN_RING_EVERY) + 1);
            polys = VolumeOutline.column(radius, low, high, rings, 12, segments);
        } else {
            polys = VolumeOutline.sphere(radius, 7, 12, segments);
        }
        TunerPreviews.polylines(pose, lines, polys, 0.5, 0.5, 0.5, argb);
    }
}
