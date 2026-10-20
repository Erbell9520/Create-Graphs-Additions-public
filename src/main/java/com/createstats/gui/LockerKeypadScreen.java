package com.createstats.gui;

import com.createstats.network.LockerKeypadPacket;
import com.createstats.network.OpenLockerKeypadPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class LockerKeypadScreen extends KeypadScreen {
    private boolean claim;

    private final boolean personal;

    public LockerKeypadScreen(BlockPos pos) {
        this(pos, false);
    }

    public LockerKeypadScreen(BlockPos pos, boolean personal) {
        super(pos, Component.translatable("gui.create_stats.locker.keypad"));
        this.personal = personal;
        this.claim = personal;
    }

    public void accept(OpenLockerKeypadPacket packet) {
        this.claim = packet.claim();
        acceptVerdict(packet.resultOf(), packet.lockoutSeconds());
    }

    @Override
    protected Component headline() {
        return this.claim
                ? Component.translatable("gui.create_stats.locker.keypad_set")
                : super.headline();
    }

    @Override
    protected Component restingLine() {
        if (this.personal) {
            return Component.translatable("gui.create_stats.locker.keypad_personal");
        }
        return this.claim
                ? Component.translatable("gui.create_stats.locker.keypad_claim")
                : Component.translatable("gui.create_stats.locker.keypad_hint");
    }

    @Override
    protected void submit(String digits) {
        if (this.personal) {
            PacketDistributor.sendToServer(
                    com.createstats.network.LockerModePacket.personal(this.pos, digits));
            onClose();
            return;
        }
        PacketDistributor.sendToServer(new LockerKeypadPacket(this.pos, digits));
    }
}
