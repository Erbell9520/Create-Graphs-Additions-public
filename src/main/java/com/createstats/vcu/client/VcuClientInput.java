package com.createstats.vcu.client;

import com.createstats.CreateStats;
import com.createstats.vcu.VehicleControlUnitBE;
import com.createstats.vcu.core.PilotInput;
import com.createstats.vcu.network.VcuInputPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// seated only, or walking with W throws the craft
@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class VcuClientInput {
    public static final KeyMapping DESCEND = com.createstats.vccmk4.client.Vcc4Keys.AUX3;
    public static final KeyMapping AUX_LEFT = com.createstats.vccmk4.client.Vcc4Keys.AUX1;
    public static final KeyMapping AUX_RIGHT = com.createstats.vccmk4.client.Vcc4Keys.AUX2;

    private static int lastBits = -1;
    private static BlockPos lastPos;
    private static long lastSent;

    private VcuClientInput() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            lastBits = -1;
            lastPos = null;
            return;
        }
        VehicleControlUnitBE vcu = VehicleControlUnitBE.clientPilotedBy(player.getUUID());
        if (vcu == null) {
            if (lastPos != null && lastBits != 0) send(lastPos, 0);
            lastBits = -1;
            lastPos = null;
            return;
        }

        int bits = 0;
        if (mc.screen == null && player.isPassenger()) {
            Options o = mc.options;
            if (o.keyUp.isDown()) bits |= PilotInput.FORWARD;
            if (o.keyDown.isDown()) bits |= PilotInput.BACK;
            if (o.keyLeft.isDown()) bits |= PilotInput.LEFT;
            if (o.keyRight.isDown()) bits |= PilotInput.RIGHT;
            if (o.keyJump.isDown()) bits |= PilotInput.UP;
            if (DESCEND.isDown()) bits |= PilotInput.DOWN;
            if (AUX_LEFT.isDown()) bits |= PilotInput.AUX_LEFT;
            if (AUX_RIGHT.isDown()) bits |= PilotInput.AUX_RIGHT;
            if (o.keySprint.isDown()) bits |= PilotInput.BOOST;
        }

        BlockPos pos = vcu.getBlockPos();
        long now = mc.level.getGameTime();
        if (bits != lastBits || !pos.equals(lastPos) || now - lastSent >= 10) {
            send(pos, bits);
            lastBits = bits;
            lastPos = pos;
            lastSent = now;
        }
    }

    private static void send(BlockPos pos, int bits) {
        PacketDistributor.sendToServer(new VcuInputPacket(pos, bits));
    }
}
