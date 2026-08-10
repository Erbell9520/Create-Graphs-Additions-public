package com.createstats.vccmk2;

import com.createstats.CreateStats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server action for the VCC Mk II console.
 * <ul>
 *   <li>{@link #SET_PROFILE} (value = profile ordinal)</li>
 *   <li>{@link #TOGGLE_ARM}</li>
 *   <li>{@link #TOGGLE_MODE} (manual / auto)</li>
 * </ul>
 */
public record VccMk2ActionPacket(int containerId, int action, int value) implements CustomPacketPayload {

    public static final int SET_PROFILE = 0;
    public static final int TOGGLE_ARM = 1;
    public static final int TOGGLE_MODE = 2;
    public static final int TOGGLE_SLOT = 3;
    public static final int BIND_ALL = 4;
    public static final int CLEAR_ALL = 5;
    /** value packs the engine row and role: {@code (row << 4) | roleOrdinal}; toggles that role bit. */
    public static final int SET_ROLE = 6;
    public static final int AUTO_ASSIGN = 7;
    public static final int NOSE_ROTATE = 8;
    public static final int TOGGLE_STAB = 9;
    /** value = frequencySlot * 64 + inventoryIndex. */
    public static final int FREQ_ASSIGN = 10;
    /** value = frequencySlot. */
    public static final int FREQ_RETURN = 11;
    /** value = cardSlot * 64 + inventoryIndex (manual card placement). */
    public static final int PLACE_CARD = 12;
    /** value = engine row (toggles that row between kinetic and redstone output). */
    public static final int SET_ENGINE_TYPE = 13;
    /** value = (axis << 12) | round((demand + 1) * 1000); a cockpit set-and-hold axis demand. */
    public static final int SET_AXIS = 14;
    /** value = gear ordinal (0=Park, 1=Reverse, 2=Neutral, 3=Drive). */
    public static final int SET_GEAR = 15;
    /** value = steer mode ordinal (0=Front, 1=All-Wheel, 2=Crab). */
    public static final int SET_STEER_MODE = 16;
    /** value = drive mode ordinal (0=RWD, 1=FWD, 2=4x4). */
    public static final int SET_DRIVE_MODE = 17;
    /** value = 1/0 mid-axle drive on/off. */
    public static final int SET_MID_AXLE = 18;
    /** value = diff mode ordinal (0=Open, 1=Rear, 2=Full). */
    public static final int SET_DIFF = 19;
    /** value = 1/0 steering invert on/off. */
    public static final int SET_STEER_INVERT = 20;
    /** value = (auxIndex << 1) | on; a cockpit aux toggle (lights etc.). */
    public static final int SET_AUX = 21;
    /** value = 1/0 handbrake on/off. */
    public static final int SET_HANDBRAKE = 22;
    /** value = which * 64 + inventoryIndex; place an item into a lights frequency slot. */
    public static final int LIGHTS_FREQ_ASSIGN = 23;
    /** value = which (0/1); return a lights frequency item. */
    public static final int LIGHTS_FREQ_RETURN = 24;
    /** value = sensor slot (0=gimbal,1=altitude,2=velocity,3=nav table); clear its manual bind. */
    public static final int SENSOR_CLEAR = 25;

    public static final Type<VccMk2ActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "vcc_mk2_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VccMk2ActionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, VccMk2ActionPacket::containerId,
                    ByteBufCodecs.VAR_INT, VccMk2ActionPacket::action,
                    ByteBufCodecs.VAR_INT, VccMk2ActionPacket::value,
                    VccMk2ActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VccMk2ActionPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player == null) {
                return;
            }
            if (!(player.containerMenu instanceof VccMk2Menu menu) || menu.containerId != msg.containerId()) {
                return;
            }
            VccMk2BlockEntity be = menu.blockEntity();
            if (be == null) {
                return;
            }
            switch (msg.action()) {
                case SET_PROFILE -> be.setProfile(VccMk2CraftProfile.byOrdinal(msg.value()));
                case TOGGLE_ARM -> be.toggleArmed();
                case TOGGLE_MODE -> be.toggleAutoMode();
                case TOGGLE_SLOT -> be.toggleCardSlot(player, msg.value());
                case BIND_ALL -> be.bindAllCards(player);
                case CLEAR_ALL -> be.clearAllCards(player);
                case SET_ROLE -> be.toggleRole(msg.value() >> 4, VccMk2Role.byOrdinal(msg.value() & 0xF));
                case AUTO_ASSIGN -> be.autoAssignRoles();
                case NOSE_ROTATE -> be.rotateNose();
                case TOGGLE_STAB -> be.toggleStabilization();
                case FREQ_ASSIGN -> be.assignFreq(player, msg.value() >> 6, msg.value() & 0x3F);
                case FREQ_RETURN -> be.returnFreq(player, msg.value());
                case PLACE_CARD -> be.placeCardInSlot(player, msg.value() >> 6, msg.value() & 0x3F);
                case SET_ENGINE_TYPE -> be.setRedstoneEngine(player, msg.value(), !be.isRedstoneEngine(msg.value()));
                case SET_AXIS -> be.setAxisDemand(msg.value() >> 12, (msg.value() & 0xFFF) / 1000.0f - 1.0f);
                case SET_GEAR -> be.setGear(msg.value());
                case SET_STEER_MODE -> be.setSteerMode(msg.value());
                case SET_DRIVE_MODE -> be.setDriveMode(msg.value());
                case SET_MID_AXLE -> be.setMidAxleDrive(msg.value() != 0);
                case SET_DIFF -> be.setDiffMode(msg.value());
                case SET_STEER_INVERT -> be.setSteerInvert(msg.value() != 0);
                case SET_AUX -> be.setAux(msg.value() >> 1, (msg.value() & 1) != 0);
                case SET_HANDBRAKE -> be.setHandbrake(msg.value() != 0);
                case LIGHTS_FREQ_ASSIGN -> be.assignLightsFreq(player, msg.value() >> 6, msg.value() & 0x3F);
                case LIGHTS_FREQ_RETURN -> be.returnLightsFreq(player, msg.value());
                case SENSOR_CLEAR -> be.clearSensor(msg.value());
                default -> {
                }
            }
        });
    }
}
