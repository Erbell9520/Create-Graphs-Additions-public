package com.createstats.registry;

import com.createstats.CreateStats;
import com.createstats.seismic.SeismicCharge;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, CreateStats.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ALARM_RING =
            SOUNDS.register("alarm_ring", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "alarm_ring")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FLYWHEEL_LOOP =
            SOUNDS.register("flywheel_loop", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "flywheel_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FLYWHEEL_OVERSTRESS =
            SOUNDS.register("flywheel_overstress", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "flywheel_overstress")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FLYWHEEL_CHARGED =
            SOUNDS.register("flywheel_charged", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "flywheel_charged")));

    public static final DeferredHolder<SoundEvent, SoundEvent> STARTER_CAPACITOR_HUM =
            SOUNDS.register("starter_capacitor_hum", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "starter_capacitor_hum")));

    public static final DeferredHolder<SoundEvent, SoundEvent> STARTER_CAPACITOR_CLUNK =
            SOUNDS.register("starter_capacitor_clunk", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "starter_capacitor_clunk")));

    public static final DeferredHolder<SoundEvent, SoundEvent> STARTER_CAPACITOR_CRACK =
            SOUNDS.register("starter_capacitor_crack", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "starter_capacitor_crack")));

    public static final DeferredHolder<SoundEvent, SoundEvent> STARTER_CAPACITOR_WHINE =
            SOUNDS.register("starter_capacitor_whine", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "starter_capacitor_whine")));

    public static final DeferredHolder<SoundEvent, SoundEvent> STARTER_CAPACITOR_RELEASE =
            SOUNDS.register("starter_capacitor_release", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "starter_capacitor_release")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AIR_FRYER_FAN =
            SOUNDS.register("air_fryer_fan", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "air_fryer_fan")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AIR_FRYER_DING =
            SOUNDS.register("air_fryer_ding", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "air_fryer_ding")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DIMMER_DIAL_TICK =
            SOUNDS.register("dimmer_dial_tick", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "dimmer_dial_tick")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DIMMER_DIAL_STOP =
            SOUNDS.register("dimmer_dial_stop", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "dimmer_dial_stop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DIMMER_DIAL_PUSH =
            SOUNDS.register("dimmer_dial_push", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "dimmer_dial_push")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DIMMER_PUCK_TICK =
            SOUNDS.register("dimmer_puck_tick", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "dimmer_puck_tick")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DIMMER_PUCK_STOP =
            SOUNDS.register("dimmer_puck_stop", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "dimmer_puck_stop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DIMMER_PUCK_PUSH =
            SOUNDS.register("dimmer_puck_push", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "dimmer_puck_push")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_SWITCH_PIN_IN =
            SOUNDS.register("time_switch_pin_in", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "time_switch_pin_in")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_SWITCH_PIN_OUT =
            SOUNDS.register("time_switch_pin_out", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "time_switch_pin_out")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_SWITCH_DETENT =
            SOUNDS.register("time_switch_detent", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "time_switch_detent")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_SWITCH_CONTACT =
            SOUNDS.register("time_switch_contact", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "time_switch_contact")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TIME_SWITCH_SWEEP =
            SOUNDS.register("time_switch_sweep", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "time_switch_sweep")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CONTACTOR_PULL_IN =
            SOUNDS.register("contactor_pull_in", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "contactor_pull_in")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CONTACTOR_DROP_OUT =
            SOUNDS.register("contactor_drop_out", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "contactor_drop_out")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CONTACTOR_HUM =
            SOUNDS.register("contactor_hum", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "contactor_hum")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TUNER_SWEEP =
            SOUNDS.register("tuner_sweep", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "tuner_sweep")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TUNER_DETENT =
            SOUNDS.register("tuner_detent", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "tuner_detent")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MOB_REPELLER_ENGAGE =
            SOUNDS.register("mob_repeller_engage", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "mob_repeller_engage")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MOB_REPELLER_POWER_UP =
            SOUNDS.register("mob_repeller_power_up", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "mob_repeller_power_up")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MOB_REPELLER_POWER_DOWN =
            SOUNDS.register("mob_repeller_power_down", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "mob_repeller_power_down")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SEISMIC_WINCH =
            SOUNDS.register("seismic_winch", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "seismic_winch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SEISMIC_RATCHET =
            SOUNDS.register("seismic_ratchet", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "seismic_ratchet")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SEISMIC_PAWL =
            SOUNDS.register("seismic_pawl", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "seismic_pawl")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SEISMIC_LEVER =
            SOUNDS.register("seismic_lever", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "seismic_lever")));

    public static final List<DeferredHolder<SoundEvent, SoundEvent>> SEISMIC_FALL = seismicFalls();

    private static List<DeferredHolder<SoundEvent, SoundEvent>> seismicFalls() {
        final List<DeferredHolder<SoundEvent, SoundEvent>> falls = new ArrayList<>();
        for (int ticks = 1; ticks <= SeismicCharge.FULL_FALL_TICKS; ticks++) {
            final String name = "seismic_fall_" + ticks;
            falls.add(SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, name))));
        }
        return List.copyOf(falls);
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> SEISMIC_THUD =
            SOUNDS.register("seismic_thud", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "seismic_thud")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SEISMIC_RUMBLE =
            SOUNDS.register("seismic_rumble", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "seismic_rumble")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SEISMIC_PEN =
            SOUNDS.register("seismic_pen", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "seismic_pen")));

    public static final DeferredHolder<SoundEvent, SoundEvent> VACUUM_HUM =
            SOUNDS.register("vacuum_hum", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vacuum_hum")));

    public static final DeferredHolder<SoundEvent, SoundEvent> VACUUM_HOSE_TAKE =
            SOUNDS.register("vacuum_hose_take", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vacuum_hose_take")));

    public static final DeferredHolder<SoundEvent, SoundEvent> VACUUM_HOSE_RETURN =
            SOUNDS.register("vacuum_hose_return", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vacuum_hose_return")));

    public static final DeferredHolder<SoundEvent, SoundEvent> VACUUM_FLAP =
            SOUNDS.register("vacuum_flap", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vacuum_flap")));

    public static final DeferredHolder<SoundEvent, SoundEvent> VACUUM_SERVO =
            SOUNDS.register("vacuum_servo", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vacuum_servo")));

    public static final DeferredHolder<SoundEvent, SoundEvent> VACUUM_GULP =
            SOUNDS.register("vacuum_gulp", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vacuum_gulp")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVER_HELMET_VISOR_OPEN =
            SOUNDS.register("driver_helmet_visor_open", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "driver_helmet_visor_open")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVER_HELMET_VISOR_CLOSE =
            SOUNDS.register("driver_helmet_visor_close", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "driver_helmet_visor_close")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVER_HELMET_NIGHT_VISION_ON =
            SOUNDS.register("driver_helmet_night_vision_on", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "driver_helmet_night_vision_on")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVER_HELMET_NIGHT_VISION_OFF =
            SOUNDS.register("driver_helmet_night_vision_off", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "driver_helmet_night_vision_off")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DRIVER_HELMET_THEME =
            SOUNDS.register("driver_helmet_theme", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "driver_helmet_theme")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AVIATOR_GOGGLES_DOWN =
            SOUNDS.register("aviator_goggles_down", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "aviator_goggles_down")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AVIATOR_GOGGLES_UP =
            SOUNDS.register("aviator_goggles_up", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "aviator_goggles_up")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AVIATOR_HELMET_TONE =
            SOUNDS.register("aviator_helmet_tone", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "aviator_helmet_tone")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SURVEYOR_STAFF_STAKE =
            SOUNDS.register("surveyor_staff_stake", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "surveyor_staff_stake")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SURVEYOR_STAFF_CHALK =
            SOUNDS.register("surveyor_staff_chalk", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "surveyor_staff_chalk")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SURVEYOR_STAFF_LOOP =
            SOUNDS.register("surveyor_staff_loop", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "surveyor_staff_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SURVEYOR_STAFF_DELETE =
            SOUNDS.register("surveyor_staff_delete", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "surveyor_staff_delete")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SURVEYOR_STAFF_MODE =
            SOUNDS.register("surveyor_staff_mode", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "surveyor_staff_mode")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SURVEYOR_STAFF_LINK =
            SOUNDS.register("surveyor_staff_link", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "surveyor_staff_link")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SURVEYOR_RECORD_START =
            SOUNDS.register("surveyor_record_start", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "surveyor_record_start")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SURVEYOR_RECORD_STOP =
            SOUNDS.register("surveyor_record_stop", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "surveyor_record_stop")));

    private static final java.util.Map<String, DeferredHolder<SoundEvent, SoundEvent>> VOICE =
            new java.util.LinkedHashMap<>();

    // variable range events take attenuation from volume, capped 1.0 = 16 blocks. this is why the speakers were silent
    public static final float PA_VOICE_RANGE = 96.0f;

    static {
        for (String clip : com.createstats.pa.Lexicon.allClips()) {
            final String name = "pa_voice." + clip;
            VOICE.put(clip, SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, name),
                    PA_VOICE_RANGE)));
        }
    }

    private static final java.util.EnumMap<com.createstats.sound.DoorClip,
            DeferredHolder<SoundEvent, SoundEvent>> DOOR =
            new java.util.EnumMap<>(com.createstats.sound.DoorClip.class);

    static {
        for (com.createstats.sound.DoorClip clip : com.createstats.sound.DoorClip.values()) {
            final String id = clip.soundId();
            DOOR.put(clip, SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, id))));
        }
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_COIL_ZAP =
            SOUNDS.register("storm_coil_zap", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "storm_coil_zap")));

    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_COIL_HUM =
            SOUNDS.register("storm_coil_hum", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "storm_coil_hum")));

    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_COIL_BANK =
            SOUNDS.register("storm_coil_bank", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "storm_coil_bank")));

    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_COIL_CHARGE =
            SOUNDS.register("storm_coil_charge", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "storm_coil_charge")));

    public static final DeferredHolder<SoundEvent, SoundEvent> STORM_COIL_RUN =
            SOUNDS.register("storm_coil_run", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "storm_coil_run")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPARK_CROWN_HUM =
            SOUNDS.register("spark_crown_hum", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "spark_crown_hum")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPARK_CROWN_LATCH =
            SOUNDS.register("spark_crown_latch", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "spark_crown_latch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPARK_CROWN_UNLATCH =
            SOUNDS.register("spark_crown_unlatch", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "spark_crown_unlatch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPARK_CROWN_LAND =
            SOUNDS.register("spark_crown_land", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "spark_crown_land")));

    public static final DeferredHolder<SoundEvent, SoundEvent> COUPLER_CRACKLE =
            SOUNDS.register("coupler_crackle", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "coupler_crackle")));

    public static final DeferredHolder<SoundEvent, SoundEvent> COUPLER_SEAT =
            SOUNDS.register("coupler_seat", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "coupler_seat")));

    public static final DeferredHolder<SoundEvent, SoundEvent> COUPLER_INDEX =
            SOUNDS.register("coupler_index", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "coupler_index")));

    public static final DeferredHolder<SoundEvent, SoundEvent> COUPLER_TRIP =
            SOUNDS.register("coupler_trip", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "coupler_trip")));

    public static SoundEvent doorClip(com.createstats.sound.DoorClip clip) {
        return DOOR.get(clip).get();
    }

    public static SoundEvent voiceClip(String clip) {
        final DeferredHolder<SoundEvent, SoundEvent> holder = VOICE.get(clip);
        return holder == null ? null : holder.get();
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
