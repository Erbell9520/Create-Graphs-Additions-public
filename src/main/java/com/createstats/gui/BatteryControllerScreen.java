package com.createstats.gui;

import com.createstats.blockentity.BatteryControllerBE;
import com.createstats.gui.battery.BatteryCanvas;
import com.createstats.gui.battery.BatteryHistory;
import com.createstats.gui.battery.BatteryPages;
import com.createstats.gui.battery.BatteryView;
import com.createstats.gui.pdu.PduRect;
import com.createstats.network.BatteryControllerHistoryRequestPacket;
import com.createstats.network.BatteryControllerSetPacket;
import com.createstats.power.BatteryControllerPorts;
import com.createstats.power.BatteryOutputMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class BatteryControllerScreen extends Screen {
    private final BatteryControllerBE controller;

    private PduRect panel = new PduRect(0, 0, 0, 0);
    private final List<BatteryPages.Hit> hits = new ArrayList<>();

    private BatteryPages.Tab tab = BatteryPages.Tab.NOW;
    private int selected = -1;

    private int dragAction;
    private int dragIndex = -1;
    private PduRect dragTrack;
    private int dragMax;

    private final BatteryHistory history = new BatteryHistory();
    private int range = BatteryHistory.RANGE_5M;
    private boolean subscribed;
    private int keepaliveCounter;

    public BatteryControllerScreen(BatteryControllerBE controller) {
        super(Component.translatable("block.create_stats.fe_battery_controller"));
        this.controller = controller;
    }

    @Override
    protected void init() {
        final int w;
        final int h;
        if (width >= 656 && height >= 416) {
            w = 640;
            h = 400;
        } else if (width >= 576 && height >= 376) {
            w = 560;
            h = 360;
        } else {
            w = Math.min(500, width - 12);
            h = Math.min(330, height - 12);
        }
        panel = new PduRect((width - w) / 2, (height - h) / 2, w, h);
        if (!subscribed) {
            subscribe();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.drawManaged(() -> {
            g.fill(0, 0, width, height, 0xB8000000);
            hits.clear();
            final BatteryPages.State state = new BatteryPages.State(tab, selected, mouseX, mouseY, history,
                    range, BatteryControllerBE.portRate(), hits);
            drawPanel(g, font, panel, snapshot(), state);
        });
        super.render(g, mouseX, mouseY, partialTick);
    }

    static void drawPanel(GuiGraphics g, Font font, PduRect panel, BatteryView view, BatteryPages.State state) {
        BatteryPages.draw(canvas(g, font), panel, view, state);
    }

    public static com.createstats.book.specimen.ManualSpecimen specimen(BatteryView view, BatteryPages.Tab tab,
            int selected, BatteryHistory history, int range, int portRate, int w, int h,
            java.util.function.Supplier<com.createstats.book.specimen.SpecimenAnatomy> anatomy) {
        return new com.createstats.book.specimen.ManualSpecimen() {
            @Override
            public int specimenWidth() {
                return w;
            }

            @Override
            public int specimenHeight() {
                return h;
            }

            @Override
            public void renderSpecimen(GuiGraphics g, int mouseX, int mouseY) {
                final Font font = net.minecraft.client.Minecraft.getInstance().font;
                g.drawManaged(() -> drawPanel(g, font, new PduRect(0, 0, w, h), view, new BatteryPages.State(tab,
                        selected, mouseX, mouseY, history, range, portRate, new ArrayList<>())));
            }

            @Override
            public com.createstats.book.specimen.SpecimenAnatomy anatomy() {
                return anatomy.get();
            }
        };
    }

    private static BatteryCanvas canvas(GuiGraphics g, Font font) {
        return new BatteryCanvas() {
            @Override
            public void fill(int x0, int y0, int x1, int y1, int argb) {
                g.fill(x0, y0, x1, y1, argb);
            }

            @Override
            public void text(String s, int x, int y, int argb) {
                g.drawString(font, s, x, y, argb, false);
            }

            @Override
            public int width(String s) {
                return font.width(s);
            }

            @Override
            public void bold(String s, int x, int y, int argb) {
                g.drawString(font, Component.literal(s).withStyle(ChatFormatting.BOLD), x, y, argb, false);
            }

            @Override
            public int boldWidth(String s) {
                return font.width(Component.literal(s).withStyle(ChatFormatting.BOLD));
            }
        };
    }

    private BatteryView snapshot() {
        final List<BatteryView.Input> inputs = new ArrayList<>(BatteryControllerPorts.INPUTS);
        for (int i = 0; i < BatteryControllerPorts.INPUTS; i++) {
            inputs.add(new BatteryView.Input(i, controller.isInputEnabled(i), controller.inputFlow(i),
                    controller.inputTankCount(i), controller.inputSourceCount(i), controller.portStoreCount(i),
                    controller.inputStoreFlow(i), controller.inputHasStrayPanel(i), controller.inputBankMove(i)));
        }
        final List<BatteryView.Output> outputs = new ArrayList<>(BatteryControllerPorts.OUTPUTS);
        for (int i = 0; i < BatteryControllerPorts.OUTPUTS; i++) {
            final int port = BatteryControllerPorts.outputPort(i);
            outputs.add(new BatteryView.Output(i, !controller.links(port).isEmpty(), controller.isOutputEnabled(i),
                    controller.outputMode(i), controller.outputLimit(i), controller.outputFloorPct(i),
                    controller.outputPriority(i), controller.outputReason(i), controller.outputFlow(i),
                    controller.outputDemand(i), controller.outputCeiling(i), controller.outputDeviceCount(i),
                    controller.outputPanelCount(i), controller.outputCriticalDemand(i),
                    controller.outputBoardAsked(i), controller.outputBoardGranted(i),
                    controller.outputBoardReason(i), controller.portStoreCount(port),
                    controller.outputStoreFlow(i)));
        }
        final com.createstats.power.FeReserveReport state = controller.reserveState();
        return new BatteryView(controller.reserveEnergy(), controller.reserveCapacity(),
                controller.generationFePerTick(), controller.deliveredFePerTick(), controller.bankDrawnFePerTick(),
                controller.bankChargedFePerTick(), controller.reserveRateFePerTick(),
                controller.chargeClaimFePerTick(), state.stance(), state.floorPct(), state.ceilingPct(),
                controller.bandHealthyPct(), controller.configuredFloorPct(), controller.holdTargetPct(),
                controller.holdTargetFe(), state.sustainedDraw(), state.criticalDraw(), controller.claimPctShown(),
                controller.storeCount(), controller.storeStored(), controller.storeCapacity(),
                controller.storeCharged(), controller.storeAsk(), controller.storeBudget(),
                controller.upstreamHoldShown(), inputs, outputs, controller.outlook(), controller.bandCriticalWant(),
                controller.bandSettings(), controller.isBandCustom(), controller.bandFullRate());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int k = hits.size() - 1; k >= 0; k--) {
                final BatteryPages.Hit hit = hits.get(k);
                if (!hit.r().contains(mouseX, mouseY)) {
                    continue;
                }
                switch (hit.action()) {
                    case BatteryPages.A_TAB -> {
                        tab = BatteryPages.Tab.values()[Mth.clamp(hit.value(), 0, BatteryPages.Tab.values().length - 1)];
                        selected = -1;
                    }
                    case BatteryPages.A_SELECT -> selected = selected == hit.index() ? -1 : hit.index();
                    case BatteryPages.A_DESELECT -> selected = -1;
                    case BatteryPages.A_RANGE -> selectRange(hit.value());
                    case BatteryPages.A_FLOOR_TRACK, BatteryPages.A_LIMIT_TRACK, BatteryPages.A_BAND_TRACK,
                         BatteryPages.A_CAP_TRACK -> {
                        dragAction = hit.action();
                        dragIndex = hit.index();
                        dragTrack = hit.r();
                        dragMax = hit.value();
                        applyDrag(mouseX);
                    }
                    case BatteryPages.A_BAND_HANDLE, BatteryPages.A_FLOOR_PIN -> {
                        dragAction = hit.action();
                        dragIndex = hit.index();
                        dragTrack = new PduRect(BatteryPages.rulerX0(hit.value()) - 3, hit.r().y(),
                                BatteryPages.rulerSpan(hit.value()) + 7, hit.r().h());
                        dragMax = 100;
                        if (hit.action() == BatteryPages.A_FLOOR_PIN) {
                            selected = hit.index();
                        }
                    }
                    default -> send(hit.action(), hit.index(), hit.value());
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragIndex >= 0) {
            applyDrag(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragIndex = -1;
        dragTrack = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void applyDrag(double mouseX) {
        if (dragTrack == null || dragIndex < 0) {
            return;
        }
        final int x0 = dragTrack.x() + 3;
        final int w = Math.max(1, dragTrack.w() - 7);
        final float f = Mth.clamp((float) (mouseX - x0) / w, 0.0f, 1.0f);
        if (dragAction == BatteryPages.A_FLOOR_TRACK) {
            send(BatteryControllerSetPacket.ACTION_OUTPUT_FLOOR, dragIndex, Math.round(f * dragMax));
        } else if (dragAction == BatteryPages.A_FLOOR_PIN) {
            send(BatteryControllerSetPacket.ACTION_OUTPUT_FLOOR, dragIndex, Math.min(95, Math.round(f * 100)));
        } else if (dragAction == BatteryPages.A_BAND_TRACK) {
            send(BatteryControllerSetPacket.ACTION_BAND_EDGE, dragIndex, Math.round(f * dragMax));
        } else if (dragAction == BatteryPages.A_CAP_TRACK) {
            send(BatteryControllerSetPacket.ACTION_BAND_CAP, dragIndex, Math.round(f * dragMax));
        } else if (dragAction == BatteryPages.A_BAND_HANDLE) {
            final int pct = Math.round(f * 100);
            final com.createstats.power.FeBandSettings band = controller.bandSettings();
            send(BatteryControllerSetPacket.ACTION_BAND_EDGE, dragIndex,
                    band.unit() == com.createstats.power.FeBandSettings.Unit.PERCENT ? pct
                            : com.createstats.power.FeBandSettings.minutesFor(pct, controller.reserveCapacity(),
                            controller.bandCriticalWant()));
        } else {
            final int step = Math.max(1, dragMax / 64);
            send(BatteryControllerSetPacket.ACTION_OUTPUT_LIMIT, dragIndex, Math.round(f * dragMax / step) * step);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == BatteryPages.Tab.NOW && selected >= 0 && scrollY != 0.0
                && controller.outputMode(selected) == BatteryOutputMode.LIMIT) {
            final int rate = BatteryControllerBE.portRate();
            final int step = hasShiftDown() ? Math.max(1, rate / 128) : Math.max(1, rate / 16);
            final int next = Mth.clamp(controller.outputLimit(selected) + (int) Math.signum(scrollY) * step, 0, rate);
            send(BatteryControllerSetPacket.ACTION_OUTPUT_LIMIT, selected, next);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == BatteryPages.Tab.FORECAST && keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_1
                && keyCode <= org.lwjgl.glfw.GLFW.GLFW_KEY_3) {
            selectRange(keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            final BatteryPages.Tab[] all = BatteryPages.Tab.values();
            tab = all[(tab.ordinal() + (hasShiftDown() ? all.length - 1 : 1)) % all.length];
            selected = -1;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send(int action, int index, int value) {
        PacketDistributor.sendToServer(new BatteryControllerSetPacket(controller.getBlockPos(), action, index, value));
    }

    private void subscribe() {
        subscribed = true;

        history.clear();
        sendHistory(BatteryControllerHistoryRequestPacket.ACTION_SUBSCRIBE, range);
    }

    private void selectRange(int next) {
        final int clamped = BatteryHistory.clampRange(next);
        if (clamped == range) {
            return;
        }
        range = clamped;
        subscribe();
    }

    public void receiveHistory(BlockPos pos, int sendRange, boolean full, byte[] soc, int[] in, int[] out) {
        if (!pos.equals(controller.getBlockPos()) || sendRange != range) {
            return;
        }
        if (full) {
            history.replace(soc, in, out, soc.length);
        } else if (soc.length > 0) {
            history.pushRaw(soc[0], in[0], out[0]);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (subscribed && ++keepaliveCounter % 40 == 0) {
            sendHistory(BatteryControllerHistoryRequestPacket.ACTION_KEEPALIVE, range);
        }
    }

    @Override
    public void onClose() {
        if (subscribed) {
            sendHistory(BatteryControllerHistoryRequestPacket.ACTION_CLOSE, range);
            subscribed = false;
        }
        super.onClose();
    }

    private void sendHistory(int action, int forRange) {
        PacketDistributor.sendToServer(new BatteryControllerHistoryRequestPacket(controller.getBlockPos(),
                forRange, action));
    }
}
