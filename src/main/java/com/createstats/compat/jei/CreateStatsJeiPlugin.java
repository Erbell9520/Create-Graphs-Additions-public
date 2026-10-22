package com.createstats.compat.jei;

import com.createstats.ModConfiguration;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import com.createstats.vccmk3.client.VccMk3Screen;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@JeiPlugin
public class CreateStatsJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath("create_stats", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(mezz.jei.api.registration.IRecipeCategoryRegistration registration) {
        final mezz.jei.api.helpers.IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new ArcFurnaceCategory(gui),
                new InductionSmelterCategory(gui),
                new AirFryerCategory(gui),
                new BulkWasherCategory(gui),
                new BiofuelFuelCategory(gui),
                new ConductorLadderCategory(gui));
    }

    @Override
    public void registerRecipes(mezz.jei.api.registration.IRecipeRegistration registration) {
        final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        final net.minecraft.world.item.crafting.RecipeManager recipes =
                mc.level.getRecipeManager();

        registration.addRecipes(ArcFurnaceCategory.TYPE, new java.util.ArrayList<>(
                recipes.getAllRecipesFor(com.createstats.registry.ModRecipes.ARC_TYPE.get())));

        final java.util.List<net.minecraft.world.item.crafting.RecipeHolder<
                net.minecraft.world.item.crafting.AbstractCookingRecipe>> cooking =
                new java.util.ArrayList<>();
        final net.minecraft.core.RegistryAccess access = mc.level.registryAccess();
        collectCooking(recipes, access, net.minecraft.world.item.crafting.RecipeType.SMELTING, cooking);
        collectCooking(recipes, access, net.minecraft.world.item.crafting.RecipeType.BLASTING, cooking);
        registration.addRecipes(InductionSmelterCategory.TYPE, cooking);

        final java.util.List<net.minecraft.world.item.crafting.RecipeHolder<
                net.minecraft.world.item.crafting.SmeltingRecipe>> frying = new java.util.ArrayList<>();
        for (net.minecraft.world.item.crafting.RecipeHolder<
                net.minecraft.world.item.crafting.SmeltingRecipe> holder
                : recipes.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING)) {
            final net.minecraft.world.item.crafting.SmeltingRecipe recipe = holder.value();
            if (recipe.isSpecial() || recipe.getResultItem(access).isEmpty()
                    || recipe.getIngredients().isEmpty()) {
                continue;
            }
            frying.add(holder);
        }
        registration.addRecipes(AirFryerCategory.TYPE, frying);

        // ProcessingRecipe.isSpecial() is always true, filtering it would empty the page

        final java.util.List<net.minecraft.world.item.crafting.RecipeHolder<
                com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe>> washing =
                new java.util.ArrayList<>();
        for (net.minecraft.world.item.crafting.RecipeHolder<
                com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe> holder
                : recipes.getAllRecipesFor(com.simibubi.create.AllRecipeTypes.SPLASHING
                        .<net.minecraft.world.item.crafting.SingleRecipeInput,
                                com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe>
                                getType())) {
            final com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe recipe =
                    holder.value();

            if (recipe.getIngredients().isEmpty() || recipe.getRollableResults().isEmpty()) {
                continue;
            }
            washing.add(holder);
        }
        registration.addRecipes(BulkWasherCategory.TYPE, washing);

        registration.addRecipes(BiofuelFuelCategory.TYPE,
                BiofuelFuelCategory.pagesFor(registration.getIngredientManager().getAllItemStacks()));

        registration.addRecipes(ConductorLadderCategory.TYPE,
                ConductorLadderCategory.pagesFor(arcFePerIngot(recipes)));
        registerLadderInfo(registration, recipes);

        registration.addIngredientInfo(ModBlocks.INDUCTION_SMELTER.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.induction_smelter.info"));
        registration.addIngredientInfo(ModBlocks.AIR_FRYER.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.air_fryer.info"));
        registration.addIngredientInfo(ModBlocks.BULK_WASHER.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.bulk_washer.info"));

        registration.addIngredientInfo(ModBlocks.OVEN.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.oven.info"));

        registration.addIngredientInfo(ModBlocks.BIOFUEL_BURNER.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.biofuel_burner.info",
                        com.createstats.power.BiofuelValue.OUTPUT_FE_PER_TICK,
                        com.createstats.blockentity.BiofuelBurnerBE.BUFFER));

        registration.addIngredientInfo(ModBlocks.SMART_HOME_PANEL.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.smart_home_panel.info",
                        com.createstats.blockentity.SmartHomePanelBE.DRAIN));
        registration.addIngredientInfo(ModBlocks.HOME_SECURITY_PANEL.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.home_security_panel.info",
                        com.createstats.blockentity.HomeSecurityPanelBE.DRAIN));
        registration.addIngredientInfo(ModBlocks.WEATHER_PANEL.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.weather_panel.info",
                        com.createstats.blockentity.WeatherPanelBE.DRAIN));

        registration.addIngredientInfo(ModBlocks.SEISMIC_SCANNER.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.seismic_scanner.info",
                        com.createstats.seismic.SeismicCharge.MIN_RADIUS,
                        com.createstats.seismic.SeismicCharge.MAX_RADIUS,
                        String.format(java.util.Locale.ROOT, "%,d", com.createstats.seismic.SeismicCharge.burstFor(
                                com.createstats.seismic.SeismicCharge.MIN_RADIUS)),
                        String.format(java.util.Locale.ROOT, "%,d", com.createstats.seismic.SeismicCharge.burstFor(
                                com.createstats.seismic.SeismicCharge.MAX_RADIUS)),
                        com.createstats.seismic.SeismicRig.comparator(1, 1),
                        (int) com.createstats.blockentity.SeismicScannerBE.WITNESS_RADIUS,
                        com.createstats.seismic.VeinVisibility.OVERVIEW_TICKS / 20,
                        (int) com.createstats.seismic.VeinVisibility.NEAR,
                        (int) com.createstats.seismic.VeinVisibility.FAR));

        registration.addIngredientInfo(ModBlocks.PORTABLE_POWER_INTERFACE.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.portable_power_interface.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.PortableFeLadder.feAt(0,
                                com.createstats.blockentity.PortablePowerInterfaceBE.ceiling())),
                        com.createstats.power.FeRates.perTick(com.createstats.power.PortableFeLadder.feAt(
                                com.createstats.power.PortableFeLadder.maxDial(
                                        com.createstats.blockentity.PortablePowerInterfaceBE.ceiling()),
                                com.createstats.blockentity.PortablePowerInterfaceBE.ceiling()))));

        registration.addIngredientInfo(ModBlocks.POWER_TRACK.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.power_track.info",
                        com.createstats.power.FeRates.perTick(com.createstats.powertrack.core.RailPower.sectionCeiling(com.createstats.power.FeRates.base())),
                        com.createstats.power.FeRates.perTick(com.createstats.powertrack.core.RailPower.collectorRate(com.createstats.power.FeRates.base())),
                        com.createstats.power.FeRates.perTick(com.createstats.powertrack.core.RailPower.carRate(com.createstats.power.FeRates.base(), com.createstats.powertrack.core.RailPower.MAX_COLLECTORS_PER_CAR))));
        registration.addIngredientInfo(ModBlocks.TRACK_FEEDER.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.track_feeder.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.PortableFeLadder.feAt(0, com.createstats.blockentity.TrackFeederBE.ceiling())),
                        com.createstats.power.FeRates.perTick(com.createstats.power.PortableFeLadder.feAt(com.createstats.power.PortableFeLadder.maxDial(com.createstats.blockentity.TrackFeederBE.ceiling()), com.createstats.blockentity.TrackFeederBE.ceiling()))));
        registration.addIngredientInfo(ModBlocks.SECTION_INSULATOR.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.section_insulator.info"));
        registration.addIngredientInfo(ModBlocks.CURRENT_COLLECTOR.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.current_collector.info",
                        com.createstats.power.FeRates.perTick(com.createstats.powertrack.core.RailPower.collectorRate(com.createstats.power.FeRates.base())),
                        com.createstats.powertrack.core.RailPower.MAX_COLLECTORS_PER_CAR));

        registration.addIngredientInfo(ModBlocks.UNIVERSAL_POWER_COUPLER.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.universal_power_coupler.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.CouplerRating.rating(
                                com.createstats.blockentity.PowerConnectorBE.baseTransferRate(),
                                com.createstats.compat.NewAgeCompatHelper.bestWireConductivity(),
                                com.createstats.blockentity.UniversalPowerCouplerBE.BUFFER_FE)),
                        com.createstats.power.FeRates.amount(com.createstats.blockentity.UniversalPowerCouplerBE.BUFFER_FE)));

        for (com.createstats.security.SensorKind kind : com.createstats.security.SensorKind.all()) {
            registration.addIngredientInfo(ModBlocks.securitySensor(kind).get(),
                    net.minecraft.network.chat.Component.translatable(
                            "create_stats.jei." + kind.blockId() + ".info"));
        }

        for (com.createstats.block.DoorMaterial material : com.createstats.block.DoorMaterial.all()) {
            registration.addIngredientInfo(ModBlocks.poweredDoor(material).get(),
                    net.minecraft.network.chat.Component.translatable(
                            "create_stats.jei.powered_door.info",
                            com.createstats.power.DoorPower.BUFFER,
                            com.createstats.power.DoorPower.IDLE_DRAIN,
                            com.createstats.power.DoorPower.CYCLE_COST));
        }
        registration.addIngredientInfo(ModBlocks.SECURITY_LOCKER.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.security_locker.info"));

        for (com.createstats.block.ShutterTier tier : com.createstats.block.ShutterTier.all()) {
            registration.addIngredientInfo(ModBlocks.blastShutter(tier).get(),
                    net.minecraft.network.chat.Component.translatable(
                            "create_stats.jei.blast_shutter.info"));
        }

        for (net.minecraft.world.level.block.Block block : ModBlocks.garageDoorFeBlocks()) {
            registration.addIngredientInfo(block,
                    net.minecraft.network.chat.Component.translatable(
                            "create_stats.jei.garage_door.info",
                            com.createstats.power.GarageDoorPower.CYCLE_PER_BLOCK,
                            com.createstats.power.GarageDoorPower.IDLE_BLOCKS_PER_FE,
                            com.createstats.power.GarageDoorPower.BUFFER_CYCLES));
        }
        registration.addIngredientInfo(ModBlocks.TOASTER.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.toaster.info"));
        registration.addIngredientInfo(ModBlocks.BREAD_SLICER.get(),
                net.minecraft.network.chat.Component.translatable(
                        "create_stats.jei.bread_slicer.info"));
    }

    private static int arcFePerIngot(net.minecraft.world.item.crafting.RecipeManager recipes) {
        for (net.minecraft.world.item.crafting.RecipeHolder<com.createstats.recipe.ArcRecipe> holder
                : recipes.getAllRecipesFor(com.createstats.registry.ModRecipes.ARC_TYPE.get())) {
            final com.createstats.recipe.ArcRecipe recipe = holder.value();
            if (recipe.result().is(ModItems.REFINED_COPPER.get()) && recipe.result().getCount() > 0) {
                final int tap = com.createstats.blockentity.ArcFurnaceBE.MIN_TAP;
                final int ticks = Math.max(1, (recipe.duration() + tap - 1) / tap);
                return com.createstats.blockentity.ArcFurnaceBE.drawFor(tap) * ticks / recipe.result().getCount();
            }
        }
        return 0;
    }

    private static void registerLadderInfo(mezz.jei.api.registration.IRecipeRegistration registration,
                                           net.minecraft.world.item.crafting.RecipeManager recipes) {
        final int range = com.createstats.blockentity.PowerConnectorBE.wireRange();

        registration.addIngredientInfo(ModBlocks.SURFACE_CABLE.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.surface_cable.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.cable(
                                com.createstats.power.CableTier.SURFACE))));
        registration.addIngredientInfo(ModBlocks.CABLE_TRAY.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.cable_tray.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.cable(
                                com.createstats.power.CableTier.TRAY))));
        registration.addIngredientInfo(ModBlocks.BUS_DUCT.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.bus_duct.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.cable(
                                com.createstats.power.CableTier.DUCT))));
        registration.addIngredientInfo(ModBlocks.BUSBAR_TRUNK.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.busbar_trunk.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.cable(
                                com.createstats.power.CableTier.TRUNK))));

        final java.util.List<net.minecraft.network.chat.Component> copperWire = new java.util.ArrayList<>();
        copperWire.add(net.minecraft.network.chat.Component.translatable("create_stats.jei.copper_wire.info",
                com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.wire(
                        com.createstats.power.WireTier.COPPER)), range));

        if (net.neoforged.fml.ModList.get().isLoaded("create_new_age")) {
            copperWire.add(net.minecraft.network.chat.Component.translatable(
                    "create_stats.jei.copper_wire.saw_note"));
        }
        registration.addIngredientInfo(ModItems.COPPER_WIRE.get(),
                copperWire.toArray(new net.minecraft.network.chat.Component[0]));
        registration.addIngredientInfo(ModItems.INSULATED_WIRE.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.insulated_wire.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.wire(
                                com.createstats.power.WireTier.INSULATED)), range));
        registration.addIngredientInfo(ModItems.SHIELDED_WIRE.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.shielded_wire.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.wire(
                                com.createstats.power.WireTier.SHIELDED)), range));
        registration.addIngredientInfo(ModItems.HV_LINE.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.hv_line.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.wire(
                                com.createstats.power.WireTier.HV)), range));

        registration.addIngredientInfo(ModBlocks.POWER_CONNECTOR.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.power_connector.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.connector(
                                com.createstats.power.ConnectorTier.POWER)),
                        com.createstats.power.ConnectorTier.POWER.maxLinks()));
        registration.addIngredientInfo(ModBlocks.ADVANCED_POWER_CONNECTOR.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.advanced_power_connector.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.connector(
                                com.createstats.power.ConnectorTier.ADVANCED)),
                        com.createstats.power.ConnectorTier.ADVANCED.maxLinks()));
        registration.addIngredientInfo(ModBlocks.INDUSTRIAL_POWER_CONNECTOR.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.industrial_power_connector.info",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.connector(
                                com.createstats.power.ConnectorTier.INDUSTRIAL)),
                        com.createstats.power.ConnectorTier.INDUSTRIAL.maxLinks()));

        final int perIngot = arcFePerIngot(recipes);
        registration.addIngredientInfo(ModItems.REFINED_COPPER.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.refined_copper.info",
                        com.createstats.power.FeRates.amount(perIngot),
                        "I".repeat(com.createstats.blockentity.ArcFurnaceBE.MIN_TAP)));
        registration.addIngredientInfo(ModItems.REFINED_COPPER_SHEET.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.refined_copper_sheet.info"));
        registration.addIngredientInfo(ModItems.UNFIRED_INSULATOR.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.unfired_insulator.info"));
        registration.addIngredientInfo(ModItems.PORCELAIN_INSULATOR.get(),
                net.minecraft.network.chat.Component.translatable("create_stats.jei.porcelain_insulator.info"));
        registration.addIngredientInfo(
                java.util.List.of(new net.neoforged.neoforge.fluids.FluidStack(
                        com.createstats.registry.ModFluids.INSULATING_RESIN.get(), 1000)),
                mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK,
                net.minecraft.network.chat.Component.translatable("create_stats.jei.insulating_resin.info"));
    }

    private static <R extends net.minecraft.world.item.crafting.AbstractCookingRecipe> void collectCooking(
            net.minecraft.world.item.crafting.RecipeManager recipes,
            net.minecraft.core.RegistryAccess access,
            net.minecraft.world.item.crafting.RecipeType<R> type,
            java.util.List<net.minecraft.world.item.crafting.RecipeHolder<
                    net.minecraft.world.item.crafting.AbstractCookingRecipe>> out) {
        for (net.minecraft.world.item.crafting.RecipeHolder<R> holder : recipes.getAllRecipesFor(type)) {
            final R recipe = holder.value();
            if (recipe.isSpecial() || recipe.getResultItem(access).isEmpty()
                    || recipe.getIngredients().isEmpty()) {
                continue;
            }
            out.add(new net.minecraft.world.item.crafting.RecipeHolder<>(holder.id(), recipe));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModBlocks.ARC_FURNACE.get(), ArcFurnaceCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.BIOFUEL_BURNER.get(), BiofuelFuelCategory.TYPE);

        registration.addRecipeCatalyst(ModBlocks.ARC_FURNACE.get(), ConductorLadderCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.INDUCTION_HEATER.get(), ConductorLadderCategory.TYPE);

        registration.addRecipeCatalyst(ModBlocks.INDUCTION_SMELTER.get(),
                InductionSmelterCategory.TYPE, RecipeTypes.SMELTING, RecipeTypes.BLASTING);

        registration.addRecipeCatalyst(ModBlocks.AIR_FRYER.get(),
                AirFryerCategory.TYPE, RecipeTypes.SMELTING);

        registration.addRecipeCatalyst(ModBlocks.INDUCTION_HEATER.get(), RecipeTypes.CAMPFIRE_COOKING);

        registration.addRecipeCatalyst(ModBlocks.BULK_WASHER.get(),
                BulkWasherCategory.TYPE, BulkWasherCategory.FAN_WASHING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(VccMk3Screen.class, new IGuiContainerHandler<VccMk3Screen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(VccMk3Screen screen) {
                return List.of(screen.consoleBounds());
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
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
                    ModBlocks.VCC_MK3.asStack(),
                    ModBlocks.STATS_DISPLAY_MK2.asStack(),
                    ModBlocks.HOLOGRAPHIC_PROJECTOR_MK3.asStack(),
                    ModItems.HINGE.asStack(),

                    new net.minecraft.world.item.ItemStack(com.createstats.vcu.VcuRegistry.ITEM)
                )
            );
        }
    }
}
