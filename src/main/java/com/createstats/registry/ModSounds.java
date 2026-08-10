package com.createstats.registry;

import com.createstats.CreateStats;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, CreateStats.MOD_ID);

    /** Classic mechanical alarm ringtone, double-burst bell pattern. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_RING =
            SOUNDS.register("alarm_ring", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "alarm_ring")));

    /** Looping mechanical drone of a spinning Kinetic Flywheel (pitch follows RPM). */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYWHEEL_LOOP =
            SOUNDS.register("flywheel_loop", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "flywheel_loop")));

    /** One-shot metallic clunk when a Kinetic Flywheel trips on an overstressed network. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYWHEEL_OVERSTRESS =
            SOUNDS.register("flywheel_overstress", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "flywheel_overstress")));

    /** One-shot soft chime when a Kinetic Flywheel's reserve tops off. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYWHEEL_CHARGED =
            SOUNDS.register("flywheel_charged", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "flywheel_charged")));

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
