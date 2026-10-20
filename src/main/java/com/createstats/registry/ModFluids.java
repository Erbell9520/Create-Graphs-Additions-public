package com.createstats.registry;

import java.util.EnumMap;
import java.util.Map;

import com.createstats.CreateStats;
import com.createstats.baking.CakeFlavor;
import com.simibubi.create.AllFluids;
import com.simibubi.create.content.fluids.VirtualFluid;
import com.tterrag.registrate.util.entry.FluidEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

// fluids so the spout fills the mold. that's the picture
public class ModFluids {
    private static final ResourceLocation STILL =
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "fluid/cake_batter_still");
    private static final ResourceLocation FLOW =
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "fluid/cake_batter_flow");

    public static final FluidEntry<VirtualFluid> CAKE_BATTER;

    private static final ResourceLocation RESIN_STILL =
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "fluid/insulating_resin_still");
    private static final ResourceLocation RESIN_FLOW =
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "fluid/insulating_resin_flow");

    public static final FluidEntry<VirtualFluid> INSULATING_RESIN;

    private static final Map<CakeFlavor, FluidEntry<VirtualFluid>> FLAVOURED =
            new EnumMap<>(CakeFlavor.class);

    public static void register() {
        CreateStats.LOGGER.debug("Registering fluids for {}", CreateStats.MOD_ID);
    }

    private static FluidEntry<VirtualFluid> batter(String name, int tint) {
        return virtual(name, STILL, FLOW, tint);
    }

    private static FluidEntry<VirtualFluid> virtual(String name, ResourceLocation stillSprite,
                                                    ResourceLocation flowSprite, int tint) {
        return CreateStats.registrate()
                .virtualFluid(name, stillSprite, flowSprite,
                        (properties, still, flowing) -> new AllFluids.TintedFluidType(properties, still, flowing) {
                            @Override
                            protected int getTintColor(FluidStack stack) {
                                return 0xFF000000 | tint;
                            }

                            @Override
                            protected int getTintColor(FluidState state, BlockAndTintGetter level, BlockPos pos) {
                                return 0xFF000000 | tint;
                            }
                        },
                        VirtualFluid::createSource, VirtualFluid::createFlowing)
                .register();
    }

    static {
        CAKE_BATTER = batter("cake_batter", 0xF2E4C4);
        for (final CakeFlavor flavour : CakeFlavor.values()) {
            FLAVOURED.put(flavour, batter(flavour.batterName(), flavour.tint()));
        }
        INSULATING_RESIN = virtual("insulating_resin", RESIN_STILL, RESIN_FLOW, 0xFFFFFF);
    }

    public static FluidEntry<VirtualFluid> batter(CakeFlavor flavour) {
        return FLAVOURED.get(flavour);
    }
}
