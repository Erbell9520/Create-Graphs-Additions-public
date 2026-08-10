package com.createstats.registry;

import com.createstats.arm.InductionHeaterArmPointType;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers this mod's custom Mechanical Arm interaction points into Create's built-in
 * {@code ARM_INTERACTION_POINT_TYPE} registry so the arm can select those blocks as input/output.
 */
public class ModArmInteractionPoints {

    public static final DeferredRegister<ArmInteractionPointType> ARM_INTERACTION_POINTS =
        DeferredRegister.create(CreateRegistries.ARM_INTERACTION_POINT_TYPE, "create_stats");

    public static final DeferredHolder<ArmInteractionPointType, InductionHeaterArmPointType> INDUCTION_HEATER =
        ARM_INTERACTION_POINTS.register("induction_heater", InductionHeaterArmPointType::new);

    public static void register(IEventBus modEventBus) {
        ARM_INTERACTION_POINTS.register(modEventBus);
    }
}
