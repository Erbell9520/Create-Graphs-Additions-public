package com.createstats.network;

import com.createstats.battle.BattleLightSnapshot;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.DamageObserverBE;
import com.createstats.blockentity.StageLightBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Battle Screen -> server control for Stage Lights surfaced by a Damage Observer. Mirrors
 * {@link BattleScreenWeaponActionPacket}: the action resolves the linked observer's lights server-side
 * and writes the change onto each {@link StageLightBE}.
 */
public class BattleScreenLightActionPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BattleScreenLightActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("create_stats", "battle_screen_light_action"));

    public static final StreamCodec<FriendlyByteBuf, BattleScreenLightActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), BattleScreenLightActionPacket::new);

    private final BlockPos displayPos;
    private final BlockPos observerPos;
    private final long lightPos;
    private final Action action;
    @Nullable
    private final UUID targetId;

    public BattleScreenLightActionPacket(BlockPos displayPos, BlockPos observerPos, long lightPos, Action action) {
        this(displayPos, observerPos, lightPos, action, null);
    }

    public BattleScreenLightActionPacket(BlockPos displayPos, BlockPos observerPos, long lightPos, Action action, @Nullable UUID targetId) {
        this.displayPos = displayPos.immutable();
        this.observerPos = observerPos.immutable();
        this.lightPos = lightPos;
        this.action = action;
        this.targetId = targetId;
    }

    public BattleScreenLightActionPacket(FriendlyByteBuf buf) {
        this.displayPos = buf.readBlockPos();
        this.observerPos = buf.readBlockPos();
        this.lightPos = buf.readLong();
        this.action = buf.readEnum(Action.class);
        this.targetId = buf.readBoolean() ? buf.readUUID() : null;
    }

    private void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.displayPos);
        buf.writeBlockPos(this.observerPos);
        buf.writeLong(this.lightPos);
        buf.writeEnum(this.action);
        buf.writeBoolean(this.targetId != null);
        if (this.targetId != null) {
            buf.writeUUID(this.targetId);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BattleScreenLightActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (player.distanceToSqr(packet.displayPos.getX() + 0.5, packet.displayPos.getY() + 0.5, packet.displayPos.getZ() + 0.5) > 256.0) {
                player.displayClientMessage(Component.translatable("message.create_stats.battle_screen.too_far"), true);
                return;
            }
            if (!(level.getBlockEntity(packet.displayPos) instanceof BattleScreenDisplayBE display)) {
                player.displayClientMessage(Component.translatable("message.create_stats.battle_screen.link_missing"), true);
                return;
            }

            if (packet.action == Action.ALL_ON || packet.action == Action.ALL_OFF) {
                boolean lit = packet.action == Action.ALL_ON;
                for (BattleScreenDisplayBE.LinkedObserver linked : display.getLinkedObservers()) {
                    if (level.getBlockEntity(linked.sourcePos()) instanceof DamageObserverBE observer) {
                        forEachLight(observer, light -> light.setLit(lit));
                    }
                }
                return;
            }

            int sceneSlot = sceneSlotOf(packet.action);
            if (sceneSlot >= 0) {
                if (isSceneSave(packet.action)) {
                    int saved = display.saveLightScene(sceneSlot, level);
                    player.displayClientMessage(saved > 0
                            ? Component.translatable("message.create_stats.battle_screen.scene_saved", sceneSlot + 1, saved)
                            : Component.translatable("message.create_stats.battle_screen.scene_save_empty", sceneSlot + 1), true);
                } else {
                    int applied = display.recallLightScene(sceneSlot, level);
                    player.displayClientMessage(applied > 0
                            ? Component.translatable("message.create_stats.battle_screen.scene_recalled", sceneSlot + 1, applied)
                            : Component.translatable("message.create_stats.battle_screen.scene_empty", sceneSlot + 1), true);
                }
                return;
            }

            if (!(level.getBlockEntity(packet.observerPos) instanceof DamageObserverBE observer)
                    || observer.getLevel() == null) {
                player.displayClientMessage(Component.translatable("message.create_stats.battle_screen.light_link_missing"), true);
                return;
            }
            if (!(observer.getLevel().getBlockEntity(BlockPos.of(packet.lightPos)) instanceof StageLightBE light)) {
                player.displayClientMessage(Component.translatable("message.create_stats.battle_screen.light_missing"), true);
                return;
            }
            switch (packet.action) {
                case TOGGLE -> light.toggleLit();
                case RANGE_DOWN -> light.adjustRange(-4);
                case RANGE_UP -> light.adjustRange(4);
                case BRIGHT_DOWN -> light.adjustBrightness(-1);
                case BRIGHT_UP -> light.adjustBrightness(1);
                case FOCUS_DOWN -> light.adjustFocus(-1);
                case FOCUS_UP -> light.adjustFocus(1);
                case WIDTH_DOWN -> light.adjustBeamWidth(-1);
                case WIDTH_UP -> light.adjustBeamWidth(1);
                case SPREAD_DOWN -> light.adjustLightSpread(-1);
                case SPREAD_UP -> light.adjustLightSpread(1);
                case COLOR_PREV -> light.cycleColor(-1);
                case COLOR_NEXT -> light.cycleColor(1);
                case TOGGLE_BEAM -> light.toggleBeamVisible();
                case TOGGLE_FOG -> light.toggleVolumetric();
                case EFFECT_PREV -> light.cycleEffect(-1);
                case EFFECT_NEXT -> light.cycleEffect(1);
                case EFFECT_SPEED_DOWN -> light.adjustEffectSpeed(-1);
                case EFFECT_SPEED_UP -> light.adjustEffectSpeed(1);
                case SET_TARGET -> {
                    if (packet.targetId != null) {
                        light.setBattleTarget(packet.targetId);
                        player.displayClientMessage(Component.translatable("message.create_stats.battle_screen.light_tracking"), true);
                    }
                }
                case CLEAR_TARGET -> {
                    light.clearBattleTarget();
                    player.displayClientMessage(Component.translatable("message.create_stats.battle_screen.light_tracking_cleared"), true);
                }
                default -> {
                }
            }
        });
    }

    /** Scene slot index (0-based) of a scene action, or -1 for every other action. */
    private static int sceneSlotOf(Action action) {
        return switch (action) {
            case SCENE_SAVE_1, SCENE_RECALL_1 -> 0;
            case SCENE_SAVE_2, SCENE_RECALL_2 -> 1;
            case SCENE_SAVE_3, SCENE_RECALL_3 -> 2;
            default -> -1;
        };
    }

    private static boolean isSceneSave(Action action) {
        return action == Action.SCENE_SAVE_1 || action == Action.SCENE_SAVE_2 || action == Action.SCENE_SAVE_3;
    }

    private static void forEachLight(DamageObserverBE observer, java.util.function.Consumer<StageLightBE> action) {
        if (observer.getLevel() == null) {
            return;
        }
        observer.forceScanObservedVolume();
        for (BattleLightSnapshot.Entry entry : observer.getDetectedLights()) {
            if (observer.getLevel().getBlockEntity(BlockPos.of(entry.pos())) instanceof StageLightBE light) {
                action.accept(light);
            }
        }
    }

    public enum Action {
        TOGGLE,
        RANGE_DOWN,
        RANGE_UP,
        BRIGHT_DOWN,
        BRIGHT_UP,
        FOCUS_DOWN,
        FOCUS_UP,
        WIDTH_DOWN,
        WIDTH_UP,
        SPREAD_DOWN,
        SPREAD_UP,
        COLOR_PREV,
        COLOR_NEXT,
        TOGGLE_BEAM,
        TOGGLE_FOG,
        SET_TARGET,
        CLEAR_TARGET,
        ALL_ON,
        ALL_OFF,
        EFFECT_PREV,
        EFFECT_NEXT,
        EFFECT_SPEED_DOWN,
        EFFECT_SPEED_UP,
        SCENE_SAVE_1,
        SCENE_SAVE_2,
        SCENE_SAVE_3,
        SCENE_RECALL_1,
        SCENE_RECALL_2,
        SCENE_RECALL_3
    }
}
