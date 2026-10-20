package com.createstats.gui;

import com.createstats.network.OpenSecurityPanelPacket;
import com.createstats.network.PanelKeypadPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class PanelKeypadScreen extends KeypadScreen {
    private String house = "";

    private int purpose = OpenSecurityPanelPacket.PLAIN;

    public PanelKeypadScreen(BlockPos pos) {
        super(pos, Component.translatable("gui.create_stats.security.keypad"));
    }

    public void accept(OpenSecurityPanelPacket packet) {
        this.house = packet.house();
        this.purpose = packet.purpose();
        acceptVerdict(packet.resultOf(), packet.lockoutSeconds());
    }

    @Override
    protected Component headline() {
        return this.house.isEmpty() ? super.headline() : Component.literal(this.house);
    }

    protected int purpose() {
        return this.purpose;
    }

    @Override
    protected Component restingLine() {
        return Component.translatable(this.purpose == OpenSecurityPanelPacket.JOIN
                ? "gui.create_stats.security.keypad_join"
                : this.purpose == OpenSecurityPanelPacket.RELAY
                ? "gui.create_stats.security.keypad_relay"
                : "gui.create_stats.security.keypad_hint");
    }

    @Override
    protected void submit(String digits) {
        PacketDistributor.sendToServer(new PanelKeypadPacket(this.pos, digits));
    }
}
