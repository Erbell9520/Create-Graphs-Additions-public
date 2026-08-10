package com.createstats.network;

import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.DamageObserverBE;
import com.createstats.blockentity.DamageObserverSettings;
import com.createstats.menu.BattleScreenLinkedControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BattleScreenWeaponActionPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BattleScreenWeaponActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "battle_screen_weapon_action"));

    public static final StreamCodec<FriendlyByteBuf, BattleScreenWeaponActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), BattleScreenWeaponActionPacket::new);

    private final BlockPos displayPos;
    private final BlockPos observerPos;
    private final Action action;
    @Nullable
    private final UUID targetId;
    @Nullable
    private final Float responseMultiplier;
    @Nullable
    private final Integer linkedControllerAimRpm;

    public BattleScreenWeaponActionPacket(BlockPos displayPos, BlockPos observerPos, Action action) {
        this(displayPos, observerPos, action, null, null, null);
    }

    public BattleScreenWeaponActionPacket(BlockPos displayPos, BlockPos observerPos, Action action, @Nullable UUID targetId) {
        this(displayPos, observerPos, action, targetId, null, null);
    }

    public BattleScreenWeaponActionPacket(BlockPos displayPos, BlockPos observerPos, Action action, @Nullable UUID targetId, @Nullable Float responseMultiplier) {
        this(displayPos, observerPos, action, targetId, responseMultiplier, null);
    }

    public BattleScreenWeaponActionPacket(BlockPos displayPos, BlockPos observerPos, Action action, int linkedControllerAimRpm) {
        this(displayPos, observerPos, action, null, null, linkedControllerAimRpm);
    }

    public BattleScreenWeaponActionPacket(BlockPos displayPos, BlockPos observerPos, Action action, @Nullable UUID targetId, @Nullable Float responseMultiplier, @Nullable Integer linkedControllerAimRpm) {
        this.displayPos = displayPos.immutable();
        this.observerPos = observerPos.immutable();
        this.action = action;
        this.targetId = targetId;
        this.responseMultiplier = responseMultiplier;
        this.linkedControllerAimRpm = linkedControllerAimRpm;
    }

    public BattleScreenWeaponActionPacket(FriendlyByteBuf buf) {
        this.displayPos = buf.readBlockPos();
        this.observerPos = buf.readBlockPos();
        this.action = buf.readEnum(Action.class);
        this.targetId = buf.readBoolean() ? buf.readUUID() : null;
        this.responseMultiplier = buf.readBoolean() ? buf.readFloat() : null;
        this.linkedControllerAimRpm = buf.readBoolean() ? buf.readVarInt() : null;
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.displayPos);
        buf.writeBlockPos(this.observerPos);
        buf.writeEnum(this.action);
        buf.writeBoolean(this.targetId != null);
        if (this.targetId != null) {
            buf.writeUUID(this.targetId);
        }
        buf.writeBoolean(this.responseMultiplier != null);
        if (this.responseMultiplier != null) {
            buf.writeFloat(this.responseMultiplier);
        }
        buf.writeBoolean(this.linkedControllerAimRpm != null);
        if (this.linkedControllerAimRpm != null) {
            buf.writeVarInt(this.linkedControllerAimRpm);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BattleScreenWeaponActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (player.distanceToSqr(packet.displayPos.getX() + 0.5, packet.displayPos.getY() + 0.5, packet.displayPos.getZ() + 0.5) > 256.0) {
                player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.too_far"), true);
                return;
            }
            BlockEntity displayBlockEntity = level.getBlockEntity(packet.displayPos);
            if (!(displayBlockEntity instanceof BattleScreenDisplayBE display)) {
                player.displayClientMessage(Component.translatable("message.create_stats.battle_screen.link_missing"), true);
                return;
            }
            if (!display.hasLinkedObserver(packet.observerPos)) {
                player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.link_missing"), true);
                return;
            }
            BlockEntity observerBlockEntity = level.getBlockEntity(packet.observerPos);
            if (!(observerBlockEntity instanceof DamageObserverBE observer) || observer.getSettings().getCategory() != DamageObserverSettings.ObserverCategory.WEAPONS) {
                player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.observer_link_missing"), true);
                return;
            }
            if (packet.action == Action.OPEN_LINKED_CONTROLLER) {
                player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new BattleScreenLinkedControllerMenu(containerId, inventory, display, packet.observerPos),
                        Component.translatable("gui.create_stats.battle_screen.linked_controller")),
                    buffer -> {
                        buffer.writeBlockPos(packet.displayPos);
                        buffer.writeBlockPos(packet.observerPos);
                    });
                return;
            }
            observer.forceScanObservedVolume();
            if (packet.action == Action.SET_LINKED_CONTROLLER_MANUAL_AIM_RPM) {
                if (!observer.isWeaponDetected() || (!"AUTOCANNON".equals(observer.getWeaponKind()) && !"BIG_CANNON".equals(observer.getWeaponKind()))) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.rpm_requires_cannon"), true);
                    return;
                }
                if (packet.linkedControllerAimRpm != null) {
                    display.setLinkedControllerManualAimRpm(packet.observerPos, packet.linkedControllerAimRpm);
                }
                return;
            }
            if (packet.action == Action.SET_AUTO_AIM_RESPONSE_MULTIPLIER) {
                if (!observer.isWeaponDetected() || (!"AUTOCANNON".equals(observer.getWeaponKind()) && !"BIG_CANNON".equals(observer.getWeaponKind()))) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.response_requires_cannon"), true);
                    return;
                }
                if (packet.responseMultiplier != null) {
                    display.setAutoAimResponseMultiplier(packet.observerPos, packet.responseMultiplier);
                }
                return;
            }
            if (packet.action == Action.SELECT_AUTO_AIM_TARGET) {
                if (!observer.isWeaponDetected() || (!"AUTOCANNON".equals(observer.getWeaponKind()) && !"BIG_CANNON".equals(observer.getWeaponKind()))) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.lock_requires_cannon"), true);
                    return;
                }
                if (observer.getWeaponMissingBlocks() > 0) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.lock_blocked_damage"), true);
                    return;
                }
                if (packet.targetId == null) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.lock_failed_missing"), true);
                    return;
                }
                boolean locked = display.lockAutoAimTarget(packet.observerPos, packet.targetId);
                player.displayClientMessage(Component.translatable(locked
                        ? "message.create_stats.battle_weapon.target_locked"
                        : "message.create_stats.battle_weapon.lock_failed_unavailable"), true);
                return;
            }
            if (packet.action == Action.CLEAR_AUTO_AIM_LOCK) {
                display.clearAutoAimLock(packet.observerPos);
                player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.lock_released"), true);
                return;
            }
            if (packet.action == Action.LOCK_FACING_TARGET) {
                if (!observer.isWeaponDetected() || (!"AUTOCANNON".equals(observer.getWeaponKind()) && !"BIG_CANNON".equals(observer.getWeaponKind()))) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.facing_requires_cannon"), true);
                    return;
                }
                if (observer.getWeaponMissingBlocks() > 0) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.facing_blocked_damage"), true);
                    return;
                }
                boolean locked = display.lockFacingAutoAimTarget(packet.observerPos);
                player.displayClientMessage(Component.translatable(locked
                        ? "message.create_stats.battle_weapon.facing_locked"
                        : "message.create_stats.battle_weapon.facing_unavailable"), true);
                return;
            }
            if (packet.action == Action.TOGGLE_AUTO_AIM_LOCK) {
                if (!observer.isWeaponDetected() || (!"AUTOCANNON".equals(observer.getWeaponKind()) && !"BIG_CANNON".equals(observer.getWeaponKind()))) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.lock_requires_cannon"), true);
                    return;
                }
                if (observer.getWeaponMissingBlocks() > 0) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.lock_blocked_damage"), true);
                    return;
                }
                boolean locked = display.toggleAutoAimLock(packet.observerPos);
                player.displayClientMessage(Component.translatable(locked
                        ? "message.create_stats.battle_weapon.lock_engaged"
                        : "message.create_stats.battle_weapon.lock_released"), true);
                return;
            }
            if (packet.action == Action.TOGGLE_AUTO_AIM_HOLD) {
                if (!observer.isWeaponDetected() || (!"AUTOCANNON".equals(observer.getWeaponKind()) && !"BIG_CANNON".equals(observer.getWeaponKind()))) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.hold_requires_cannon"), true);
                    return;
                }
                boolean holdEnabled = display.toggleAutoAimHold(packet.observerPos);
                player.displayClientMessage(Component.translatable(holdEnabled
                        ? "message.create_stats.battle_weapon.hold_enabled"
                        : "message.create_stats.battle_weapon.hold_disabled"), true);
                return;
            }
            if (packet.action == Action.TOGGLE_AUTO_AIM_PREDICTION) {
                if (!observer.isWeaponDetected() || !"AUTOCANNON".equals(observer.getWeaponKind())) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.prediction_requires_autocannon"), true);
                    return;
                }
                boolean predictionEnabled = display.toggleAutoAimPrediction(packet.observerPos);
                player.displayClientMessage(Component.translatable(predictionEnabled
                        ? "message.create_stats.battle_weapon.prediction_enabled"
                        : "message.create_stats.battle_weapon.prediction_disabled"), true);
                return;
            }
            if (packet.action == Action.TOGGLE_AUTO_AIM_ADVANCED_PREDICTION) {
                if (!observer.isWeaponDetected() || !"AUTOCANNON".equals(observer.getWeaponKind())) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.advanced_prediction_requires_autocannon"), true);
                    return;
                }
                boolean advancedEnabled = display.toggleAutoAimAdvancedPrediction(packet.observerPos);
                player.displayClientMessage(Component.translatable(advancedEnabled
                        ? "message.create_stats.battle_weapon.advanced_prediction_enabled"
                        : "message.create_stats.battle_weapon.advanced_prediction_disabled"), true);
                return;
            }
            if (packet.action == Action.TOGGLE_AUTO_FIRE) {
                if (!observer.isWeaponDetected() || !"AUTOCANNON".equals(observer.getWeaponKind())) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.auto_fire_requires_autocannon"), true);
                    return;
                }
                boolean enabled = display.toggleAutoFire(packet.observerPos);
                Component rateText = observer.getWeaponFireRateRpm() > 0
                        ? Component.translatable("message.create_stats.battle_weapon.fire_rate", observer.getWeaponFireRateRpm())
                        : Component.translatable("message.create_stats.battle_weapon.fire_rate_unknown");
                player.displayClientMessage(enabled
                        ? Component.translatable("message.create_stats.battle_weapon.auto_fire_armed", rateText)
                        : Component.translatable("message.create_stats.battle_weapon.auto_fire_disengaged"), true);
                return;
            }
            if (packet.action == Action.FIRE && "AUTOCANNON".equals(observer.getWeaponKind())) {
                if (display.pulseAutocannonFire(observer)) {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.fire_pulse_sent"), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.fire_no_hook"), true);
                }
            } else if (packet.action == Action.FIRE && display.tryFireObservedWeapon(observer)) {
                player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.cannon_fired"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.create_stats.battle_weapon.cannon_misfire"), true);
            }
        });
    }

    public enum Action {
        FIRE,
        TOGGLE_AUTO_AIM_LOCK,
        TOGGLE_AUTO_AIM_HOLD,
        TOGGLE_AUTO_AIM_PREDICTION,
        TOGGLE_AUTO_AIM_ADVANCED_PREDICTION,
        TOGGLE_AUTO_FIRE,
        SET_LINKED_CONTROLLER_MANUAL_AIM_RPM,
        SET_AUTO_AIM_RESPONSE_MULTIPLIER,
        SELECT_AUTO_AIM_TARGET,
        CLEAR_AUTO_AIM_LOCK,
        LOCK_FACING_TARGET,
        OPEN_LINKED_CONTROLLER
    }
}