package com.createstats.client.wire;

import com.createstats.block.PowerConnectorBlock;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.network.WireTensionPacket;
import com.createstats.power.WireTension;
import com.createstats.renderer.PowerConnectorRenderer;
import com.simibubi.create.AllItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class WireTensionWrenchPanel {
    @Nullable
    private static BlockPos target;

    private static int selected = -1;

    private static final Map<Long, WireTension> pending = new HashMap<>();

    private static boolean previewing;

    private static final int REMEMBER_TICKS = 60;
    private static long ticks;
    @Nullable
    private static BlockPos lastTarget;
    private static int lastSelected = -1;
    private static long lostAt;

    private WireTensionWrenchPanel() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ticks++;
        final PowerConnectorBE be = hovered(Minecraft.getInstance());
        if (be == null) {
            release();
            return;
        }
        final BlockPos pos = be.getBlockPos();
        if (!pos.equals(target)) {
            release();
            target = pos.immutable();
            if (pos.equals(lastTarget) && ticks - lostAt <= REMEMBER_TICKS) {
                selected = lastSelected;
            }
        }
        final List<BlockPos> links = be.links();
        if (selected >= links.size()) {
            selected = -1;
        }

        pending.entrySet().removeIf(e -> {
            final BlockPos far = BlockPos.of(e.getKey());
            return !links.contains(far)
                    || PowerConnectorBE.tensionBetween(be.getLevel(), pos, far) == e.getValue();
        });
        publishPreview(be);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        final Minecraft mc = Minecraft.getInstance();
        final PowerConnectorBE be = hovered(mc);
        if (be == null || !be.getBlockPos().equals(target) || be.links().isEmpty()
                || event.getScrollDeltaY() == 0.0) {
            return;
        }

        event.setCanceled(true);
        final int direction = event.getScrollDeltaY() > 0 ? 1 : -1;
        final List<BlockPos> links = be.links();
        if (mc.player != null && mc.player.isShiftKeyDown()) {
            selected = WireRingLayout.nextPick(selected, links.size(), direction);
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f));
            publishPreview(be);
            return;
        }

        if (selected >= 0) {
            final BlockPos far = links.get(selected);
            final WireTension now = shown(be, far);
            final WireTension next = now.step(direction);
            if (next != now) {
                send(be, List.of(far), next);
            }
            return;
        }
        final WireTension base = commonest(be);
        final WireTension next = base.step(direction);
        if (next != base || uniform(be) == null) {
            send(be, links, next);
        }
    }

    @EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Layer {
        private Layer() {
        }

        @SubscribeEvent
        public static void register(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT,
                    ResourceLocation.fromNamespaceAndPath("create_stats", "wire_tension_wrench"),
                    WireTensionWrenchPanel::render);
        }
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        final Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.options.hideGui || mc.level == null
                || !(mc.level.getBlockEntity(target) instanceof PowerConnectorBE be)) {
            return;
        }
        if (be.links().isEmpty()) {
            drawEmpty(g, mc.font);
            return;
        }
        draw(g, mc.font, be);
    }

    @Nullable
    private static PowerConnectorBE hovered(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.screen != null || mc.player.isSpectator()
                || !AllItems.WRENCH.isIn(mc.player.getMainHandItem())) {
            return null;
        }
        final HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                || !(mc.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof PowerConnectorBlock)
                || !(mc.level.getBlockEntity(blockHit.getBlockPos()) instanceof PowerConnectorBE be)) {
            return null;
        }
        return be;
    }

    private static void release() {
        if (target != null) {
            lastTarget = target;
            lastSelected = selected;
            lostAt = ticks;
        }
        target = null;
        selected = -1;
        pending.clear();
        if (previewing) {
            WireTensionView.clearPreview();
            previewing = false;
        }
    }

    private static void send(PowerConnectorBE be, List<BlockPos> wires, WireTension level) {
        final long[] far = new long[wires.size()];
        final byte[] tension = new byte[wires.size()];
        for (int i = 0; i < wires.size(); i++) {
            far[i] = wires.get(i).asLong();
            tension[i] = (byte) level.id();
            pending.put(far[i], level);
        }
        PacketDistributor.sendToServer(new WireTensionPacket(be.getBlockPos(), far, tension));
        publishPreview(be);
    }

    private static void publishPreview(PowerConnectorBE be) {
        final Map<Long, WireTension> tension = new HashMap<>();
        final Set<Long> highlight = new HashSet<>();
        final List<BlockPos> links = be.links();
        for (int i = 0; i < links.size(); i++) {
            final BlockPos far = links.get(i);
            tension.put(far.asLong(), shown(be, far));
            if (selected == -1 || selected == i) {
                highlight.add(far.asLong());
            }
        }
        WireTensionView.preview(be.getBlockPos(), tension, highlight);
        previewing = true;
    }

    private static WireTension shown(PowerConnectorBE be, BlockPos far) {
        final WireTension sent = pending.get(far.asLong());
        return sent != null ? sent : PowerConnectorBE.tensionBetween(be.getLevel(), be.getBlockPos(), far);
    }

    private static WireTension commonest(PowerConnectorBE be) {
        final Map<WireTension, Integer> count = new EnumMap<>(WireTension.class);
        WireTension best = WireTension.STANDARD;
        int bestN = 0;
        for (BlockPos far : be.links()) {
            final WireTension t = shown(be, far);
            final int n = count.merge(t, 1, Integer::sum);
            if (n > bestN) {
                best = t;
                bestN = n;
            }
        }
        return best;
    }

    @Nullable
    private static WireTension uniform(PowerConnectorBE be) {
        WireTension shared = null;
        for (BlockPos far : be.links()) {
            final WireTension t = shown(be, far);
            if (shared == null) {
                shared = t;
            } else if (shared != t) {
                return null;
            }
        }
        return shared;
    }

    private static double span(PowerConnectorBE be, BlockPos far) {
        final Vec3 from = Vec3.atLowerCornerOf(be.getBlockPos())
                .add(PowerConnectorRenderer.attachPoint(be.getBlockState()));
        final Vec3 to = Vec3.atLowerCornerOf(far)
                .add(PowerConnectorRenderer.attachPoint(be.getLevel(), far, be.getBlockPos()));
        return to.distanceTo(from);
    }

    // ---------- Drawing ----------

    private static void draw(GuiGraphics g, Font font, PowerConnectorBE be) {
        final List<BlockPos> links = be.links();
        final WireTension level;
        final Component detail;
        final int[] swatch;
        if (selected >= 0) {
            final BlockPos far = links.get(selected);
            level = shown(be, far);
            detail = Component.translatable(WireRing.SPAN_KEY, WireRing.spanArgs(span(be, far), level));
            swatch = be.tierOf(far).strandA();
        } else {
            final WireTension shared = uniform(be);
            level = shared != null ? shared : commonest(be);
            detail = shared != null
                    ? Component.translatable(WrenchPanel.COUNT_KEY, links.size())
                    : Component.translatable(WireRing.MIXED_KEY);
            swatch = null;
        }
        final List<int[]> tabs = new java.util.ArrayList<>(links.size());
        for (BlockPos link : links) {
            tabs.add(be.tierOf(link).strandA());
        }
        final WrenchPanel.View view = new WrenchPanel.View(tabs, selected,
                Component.translatable(WrenchPanel.ALL_KEY).getString(),
                Component.translatable(WireRing.levelKey(level)).getString(), swatch, level.ordinal(),
                WireTension.values().length, detail.getString(),
                Component.translatable(WrenchPanel.HINT_KEY).getString());
        final WireCanvas c = WireTensionScreen.canvas(g, font);
        final int[] size = WrenchPanel.size(c, view);
        WrenchPanel.draw(c, view, (g.guiWidth() - size[0]) / 2, g.guiHeight() - WrenchPanel.LIFT - size[1]);
    }

    public static com.createstats.book.specimen.ManualSpecimen specimen(int w, int h, WrenchPanel.View view, int[] at,
            int[] aim, java.util.function.Consumer<GuiGraphics> backdrop,
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
                backdrop.accept(g);
                g.blitSprite(ResourceLocation.withDefaultNamespace("hud/crosshair"), aim[0] - 7, aim[1] - 7, 15, 15);
                final Font font = Minecraft.getInstance().font;
                g.drawManaged(() -> WrenchPanel.draw(WireTensionScreen.canvas(g, font), view, at[0], at[1]));
            }

            @Override
            public com.createstats.book.specimen.SpecimenAnatomy anatomy() {
                return anatomy.get();
            }
        };
    }

    private static void drawEmpty(GuiGraphics g, Font font) {
        final String line = Component.translatable(WrenchPanel.EMPTY_KEY).getString();
        final WireCanvas c = WireTensionScreen.canvas(g, font);
        final int[] size = WrenchPanel.emptySize(c, line);
        WrenchPanel.drawEmpty(c, line, (g.guiWidth() - size[0]) / 2, g.guiHeight() - WrenchPanel.LIFT - size[1]);
    }
}
