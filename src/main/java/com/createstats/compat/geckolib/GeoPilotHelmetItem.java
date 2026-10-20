package com.createstats.compat.geckolib;

import com.createstats.client.PilotHelmetVisorState;
import com.createstats.item.PilotHelmetItem;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class GeoPilotHelmetItem extends PilotHelmetItem implements GeoItem {
    private static final RawAnimation DEPLOY = RawAnimation.begin().thenPlayAndHold("deploy");
    private static final RawAnimation RETRACT = RawAnimation.begin().thenPlayAndHold("retract");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = newRenderProvider();

    public GeoPilotHelmetItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "visor", 5, state -> {
            final Entity entity = state.getData(DataTickets.ENTITY);
            boolean deployed = false;
            if (entity instanceof LivingEntity wearer) {
                deployed = PilotHelmetVisorState.effectiveDeployed(wearer, wearer.getItemBySlot(EquipmentSlot.HEAD));
            }
            return state.setAndContinue(deployed ? DEPLOY : RETRACT);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept((GeoRenderProvider) this.renderProvider.get());
    }

    @Override
    public Object getRenderProvider() {
        return this.renderProvider.get();
    }

    private static Supplier<Object> newRenderProvider() {
        return com.google.common.base.Suppliers.memoize(() -> new GeoRenderProvider() {
            private PilotHelmetGeoArmorRenderer armorRenderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(
                    T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot,
                    HumanoidModel<T> original) {
                if (this.armorRenderer == null) {
                    this.armorRenderer = new PilotHelmetGeoArmorRenderer();
                }
                return this.armorRenderer;
            }
        });
    }
}
