package com.createstats.power;

import com.createstats.registry.ModBlockEntities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** [1] */
public final class FeCapabilities {

    private FeCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.FLIGHT_CONTROL_COMPUTER.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.VCC_MK_II.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.PATHFINDER_COMPUTER.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.PATH_SCANNER.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.BATTLE_SCREEN_DISPLAY.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.DAMAGE_OBSERVER.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.HOLOGRAPHIC_PROJECTOR.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.FACTORY_LOGIC_CONTROLLER.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STAGE_LIGHT.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STAGE_LIGHT_MK2.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STATUS_TOWER_LIGHT.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STATS_DISPLAY_PANEL.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STATS_DISPLAY_MK2.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.STATS_ALARM.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.DISPATCH_BOARD.get(), (be, side) -> be.feConsumer());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.PRODUCTION_DETECTOR.get(), (be, side) -> be.feConsumer());
        // Built-in power infrastructure (always active, independent of feDependentMode):
        // the dynamo is an extract-only source, the connector a full passthrough node.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.KINETIC_DYNAMO.get(), (be, side) -> be.energyHandle());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.POWER_CONNECTOR.get(), (be, side) -> be.energyHandle());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CAPACITOR_BANK.get(), (be, side) -> be.energyHandle());
        // Surface cable accepts and gives on every face: it is a run, not a device with a back.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CABLE.get(), (be, side) -> be.energyHandle());
        // Lamps take power on every face, so a run reaching any side of one lights it.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.LAMP.get(), (be, side) -> be.energyHandle());
        // [2]
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.INDUCTION_RANGE.get(), (be, side) -> be.energyHandle());
        // [3]
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SWITCH.get(), (be, side) -> be.energyHandle());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.FLUID_PUMP.get(), (be, side) -> be.energyHandle());
        // The FE Motor takes power on its built-in connector exactly like the pump, and spins a shaft.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.FE_MOTOR.get(), (be, side) -> be.energyHandle());
        // [4]
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.POWER_DISTRIBUTION_UNIT.get(),
                (be, side) -> {
                    if (side == null) {
                        return be.energyHandle();
                    }
                    if (side == be.backFace()) {
                        return be.backOutputHandle();
                    }
                    return be.isInputFace(side) ? be.energyHandle() : null;
                });
    }
}
