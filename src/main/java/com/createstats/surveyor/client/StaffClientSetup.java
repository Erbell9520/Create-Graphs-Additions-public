package com.createstats.surveyor.client;

import com.createstats.CreateStats;
import com.createstats.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class StaffClientSetup {
    static final float TAP_DEPTH = 0.09f;

    private StaffClientSetup() {
    }

    @SubscribeEvent
    public static void parts(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation part : SurveyorStaffRenderer.PARTS) {
            event.register(ModelResourceLocation.standalone(part));
        }
    }

    @SubscribeEvent
    public static void itemRenderer(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return SurveyorStaffRenderer.get();
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack ps, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                                   float partialTick, float equipProcess, float swingProcess) {
                final int side = arm == HumanoidArm.RIGHT ? 1 : -1;
                final float tap = -TAP_DEPTH * Mth.sin(Mth.sqrt(swingProcess) * Mth.PI);
                ps.translate(side * 0.56f, -0.52f + equipProcess * -0.6f + tap, -0.72f);
                return true;
            }
        }, ModItems.SURVEYOR_STAFF.get());
    }
}
