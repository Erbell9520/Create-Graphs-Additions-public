package com.createstats.compat.jei;

import com.createstats.ModConfiguration;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** [1] */
@JeiPlugin
public class CreateStatsJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath("create_stats", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // The Induction Heater cooks the vanilla campfire recipe list (see InductionHeaterBE); the
        // heat tier only changes cook speed, not the recipe set, so one catalyst is complete.
        registration.addRecipeCatalyst(ModBlocks.INDUCTION_HEATER.get(), RecipeTypes.CAMPFIRE_COOKING);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // Mirror ModCreativeTab: hide the experimental blocks/items from JEI's list unless opted in.
        // Only the JEI list is touched - the items stay registered and /give-able either way.
        boolean showExperimental;
        try {
            showExperimental = ModConfiguration.COMMON.showExperimentalBlocks.get();
        } catch (RuntimeException ignored) {
            showExperimental = false;
        }
        if (!showExperimental) {
            jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(
                VanillaTypes.ITEM_STACK,
                List.of(
                    ModBlocks.VCC_MK_II.asStack(),
                    ModBlocks.STATS_DISPLAY_MK2.asStack(),
                    ModItems.HINGE.asStack()
                )
            );
        }
    }
}
