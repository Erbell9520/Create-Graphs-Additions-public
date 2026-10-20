package com.createstats.power;

import com.createstats.registry.ModBlockEntities;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class FeCapabilities {
    private FeCapabilities() {
    }

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity>
            net.neoforged.neoforge.capabilities.ICapabilityProvider<T, net.minecraft.core.Direction,
                    net.neoforged.neoforge.energy.IEnergyStorage> guard(
            net.neoforged.neoforge.capabilities.ICapabilityProvider<T, net.minecraft.core.Direction,
                    net.neoforged.neoforge.energy.IEnergyStorage> inner) {
        return (be, side) -> NewAgeFeed.refusesSide(be, side) ? null : inner.getCapability(be, side);
    }

    private static <T> net.neoforged.neoforge.capabilities.IBlockCapabilityProvider<T,
            net.minecraft.core.Direction> guardBlock(
            net.neoforged.neoforge.capabilities.IBlockCapabilityProvider<T,
                    net.minecraft.core.Direction> inner) {
        return (level, pos, state, be, side) -> NewAgeFeed.refusesSideAt(level, pos, side)
                ? null : inner.getCapability(level, pos, state, be, side);
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.FLIGHT_CONTROL_COMPUTER.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.VCC_MK_II.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.VCC_MK3.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.PATHFINDER_COMPUTER.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.PATH_SCANNER.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.BATTLE_SCREEN_DISPLAY.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.HOLOGRAPHIC_PROJECTOR.get(), guard((be, side) -> be.feConsumer()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.HOLOGRAPHIC_PROJECTOR_MK3.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.FACTORY_LOGIC_CONTROLLER.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STAGE_LIGHT.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STAGE_LIGHT_MK2.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STATUS_TOWER_LIGHT.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STATS_DISPLAY_PANEL.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STATS_DISPLAY_MK2.get(), guard((be, side) -> be.feConsumer()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STATS_DISPLAY_MK3.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STATS_DISPLAY_MK3_CONSOLE.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STATS_ALARM.get(), guard((be, side) -> be.feConsumer()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.PA_SPEAKER.get(),
                guard((be, side) -> be.energyHandle()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.PA_MEGAPHONE.get(),
                guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ANNOUNCEMENT_COMPUTER.get(),
                guard((be, side) -> side == null || side == be.backFace() ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.ANNUNCIATOR.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.DISPATCH_BOARD.get(), guard((be, side) -> be.feConsumer()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.PRODUCTION_DETECTOR.get(), guard((be, side) -> be.feConsumer()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.WALL_OUTLET.get(), guard((be, side) -> be.feConsumer()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CINE_CAMERA.get(), guard((be, side) -> be.feConsumer()));

        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new com.createstats.cinecam.CineCameraItem.Battery(stack),
                com.createstats.registry.ModBlocks.CINE_CAMERA.asItem());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.TOPOLOGY_SCANNER_PROBE.get(), guard((be, side) -> be.feConsumer()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.KINETIC_DYNAMO.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STARTER_CAPACITOR.get(),
                guard((be, side) -> be.handleFor(side)));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.BIOFUEL_BURNER.get(),
                guard((be, side) -> side == null
                        || (side.getAxis().isHorizontal() && side != be.frontFace())
                        ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SOLAR_PANEL.get(),
                guard((be, side) -> side != Direction.UP ? be.energyHandle() : null));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.POWER_CONNECTOR.get(), guard((be, side) -> be.energyHandle()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CAPACITOR_BANK.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CABLE.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.INDOOR_CABLE.get(), guard((be, side) -> be.energyHandle()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.DIAGNOSTIC_JUNCTION.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.LAMP.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SMART_HOME_PANEL.get(), guard((be, side) -> be.energyHandle()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.WEATHER_PANEL.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.HOME_SECURITY_PANEL.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SECURITY_SENSOR.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SECURITY_RELAY.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SECURITY_LOCKER.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                guardBlock((level, pos, state, be, side) -> {
                    final com.createstats.blockentity.PoweredDoorBE door =
                            com.createstats.block.PoweredDoorBlock.leafAt(level, pos, state);
                    return door == null ? null : door.energyHandle();
                }),
                com.createstats.registry.ModBlocks.poweredDoorBlocks());

        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                guardBlock((level, pos, state, be, side) -> {
                    final com.createstats.blockentity.BlastShutterBE unit =
                            com.createstats.block.BlastShutterBlock.unitAt(level, pos, state);
                    return unit == null ? null : unit.energyHandle();
                }),
                com.createstats.registry.ModBlocks.blastShutterBlocks());

        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                guardBlock((level, pos, state, be, side) -> {
                    final com.createstats.blockentity.GarageDoorBE door =
                            com.createstats.block.GarageDoorHeaderBlock.controllerFrom(level, pos, state);
                    return door == null ? null : door.energyHandle();
                }),
                com.createstats.registry.ModBlocks.garageDoorFeBlocks());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.INDUCTION_RANGE.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SWITCH.get(), guard((be, side) -> be.energyHandle()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.FLUID_PUMP.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.POWER_METER.get(),
                guard((be, side) -> {
                    if (side == null || side == be.inputFace()) {
                        return be.inputHandle();
                    }
                    return side == be.outputFace() ? be.outputHandle() : null;
                }));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.POWER_LIMITER.get(),
                guard((be, side) -> {
                    if (side == null || side == be.inputFace()) {
                        return be.inputHandle();
                    }
                    return side == be.outputFace() ? be.outputHandle() : null;
                }));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.UNIVERSAL_POWER_COUPLER.get(),
                (be, side) -> {
                    if (side != null && side == be.foreignFace()) {
                        return be.seamHandle();
                    }
                    if (NewAgeFeed.refusesSide(be, side)) {
                        return null;
                    }
                    return (side == null || side == be.gridFace()) ? be.gridHandle() : null;
                });

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.FE_MOTOR.get(), guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.PORTABLE_POWER_INTERFACE.get(),
                guard((be, side) -> side == be.front() ? null : be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.TRACK_FEEDER.get(),
                guard((be, side) -> side == null || side == be.backFace() ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.ARC_FURNACE.get(),
                guard((be, side) -> be.energyHandle()));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.INDUCTION_SMELTER.get(),
                guard((be, side) -> side == null || side == be.backFace() ? be.energyHandle() : null));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.COFFEE_MACHINE.get(),
                guard((be, side) -> side == null || side == be.backFace() ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.WATER_COOLER.get(),
                guard((be, side) -> side == null || side == be.backFace() ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.AIR_FRYER.get(),
                guard((be, side) -> side == null || side == be.backFace() ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.BULK_WASHER.get(),
                guard((be, side) -> side == null || side == be.backFace() ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.TOASTER.get(),
                guard((be, side) -> side == null || side.getAxis().isHorizontal() ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.BREAD_SLICER.get(),
                guard((be, side) -> side == null || side.getAxis().isHorizontal() ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.VACUUM_COLLECTOR.get(),
                guard((be, side) -> side == null || (side.getAxis().isHorizontal() && side != be.facing())
                        ? be.energyHandle() : null));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.VACUUM_CLEANER.get(),
                guard((be, side) -> side == null || (side.getAxis().isHorizontal() && side != be.facing())
                        ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.VACUUM_SNIFFER.get(),
                guard((be, side) -> side == null || side != be.facing() ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.MOB_REPELLER.get(),
                guard((be, side) -> be.takesPowerOn(side) ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SEISMIC_SCANNER.get(),
                guard((be, side) -> be.takesPowerOn(side) ? be.energyHandle() : null));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SPARK_CROWN.get(),
                guard((be, side) -> be.takesPowerOn(side) ? be.energyHandle() : null));
        // back and flanks only. the front is a door that swings away, the top is the hob

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.OVEN.get(),
                guard((be, side) -> side == null
                        || (side.getAxis().isHorizontal() && side != be.frontFace())
                        ? be.energyHandle() : null));

        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.WATER_COOLER.get(),
                (be, side) -> side == null || side == be.backFace() ? be.fluidHandle() : null);

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.PDU_CABINET.get(),
                guard((be, side) -> side == null ? be.energyHandle()
                        : side == be.backFace() ? be.backOutputHandle() : be.inputHandle(side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.POWER_DISTRIBUTION_UNIT.get(),
                guard((be, side) -> {
                    if (side == null) {
                        return be.energyHandle();
                    }
                    if (side == be.backFace()) {
                        return be.backOutputHandle();
                    }

                    return be.inputHandle(side);
                }));
    }
}
