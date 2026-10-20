package com.createstats.client.wire;

import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.network.WireTensionPacket;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;
import com.createstats.renderer.PowerConnectorRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WireTensionScreen extends Screen {
    private final BlockPos connector;
    private final List<Spoke> spokes = new ArrayList<>();

    private int hovered = -1;
    private boolean done;

    private static final class Spoke {
        final BlockPos far;
        final WireTier tier;
        final double span;
        final WireTension original;
        WireTension pending;
        double angle;

        Spoke(BlockPos far, WireTier tier, double span, WireTension original) {
            this.far = far;
            this.tier = tier;
            this.span = span;
            this.original = original;
            this.pending = original;
        }
    }

    public WireTensionScreen(PowerConnectorBE be) {
        super(Component.translatable("create_stats.wire_tension.title"));
        this.connector = be.getBlockPos().immutable();
        final Minecraft mc = Minecraft.getInstance();
        final Camera camera = mc.gameRenderer.getMainCamera();
        final Vector3f left = camera.getLeftVector();
        final Vector3f up = camera.getUpVector();
        final Vec3 fromLocal = PowerConnectorRenderer.attachPoint(be.getBlockState());
        final Vec3 from = Vec3.atLowerCornerOf(connector).add(fromLocal);
        final double[] raw = new double[be.links().size()];
        int i = 0;
        for (BlockPos far : be.links()) {
            final Vec3 to = Vec3.atLowerCornerOf(far)
                    .add(PowerConnectorRenderer.attachPoint(be.getLevel(), far, connector));
            final Vec3 d = to.subtract(from);

            final double sx = -(d.x * left.x() + d.y * left.y() + d.z * left.z());
            final double sy = -(d.x * up.x() + d.y * up.y() + d.z * up.z());
            raw[i++] = Math.atan2(sy, sx);
            spokes.add(new Spoke(far.immutable(), be.tierOf(far), d.length(),
                    PowerConnectorBE.tensionBetween(be.getLevel(), connector, far)));
        }
        final double[] spread = WireRingLayout.spread(raw, WireRing.MIN_GAP);
        for (int k = 0; k < spokes.size(); k++) {
            spokes.get(k).angle = spread[k];
        }
    }

    private WireRing.Frame frame;

    @Override
    protected void init() {
        frame = WireRing.Frame.of(width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void tick() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null
                || !(mc.level.getBlockEntity(connector) instanceof PowerConnectorBE be)
                || mc.player.distanceToSqr(connector.getX() + 0.5, connector.getY() + 0.5,
                        connector.getZ() + 0.5) > WireTension.REACH * WireTension.REACH) {
            onClose();
            return;
        }

        spokes.removeIf(s -> !be.links().contains(s.far));
        if (spokes.isEmpty()) {
            onClose();
            return;
        }

        if (!useHeld(mc)) {
            commit();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final List<WireRing.Panel> panels = panels();
        hovered = WireRing.pick(frame, panels, mouseX - frame.cx(), mouseY - frame.cy());
        publishPreview();
        final WireTension shared = uniform();
        WireRing.draw(canvas(g, font), frame, panels, Component.translatable(WireRing.ALL_KEY).getString(),
                (shared == null ? Component.translatable(WireRing.MIXED_KEY) : tensionName(shared)).getString(),
                Component.translatable(WireRing.HINT_KEY).getString(), hovered);
        super.render(g, mouseX, mouseY, partialTick);
    }

    public static com.createstats.book.specimen.ManualSpecimen specimen(int w, int h, List<WireRing.Panel> panels,
            String all, String hubSub, String hint, int resting,
            java.util.function.BiConsumer<GuiGraphics, int[]> backdrop,
            java.util.function.Supplier<com.createstats.book.specimen.SpecimenAnatomy> anatomy) {
        final WireRing.Frame frame = WireRing.Frame.of(w, h);
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
                backdrop.accept(g, new int[]{mouseX, mouseY});
                final boolean on = mouseX >= 0 && mouseY >= 0 && mouseX < w && mouseY < h;
                final int lit = on ? WireRing.pick(frame, panels, mouseX - frame.cx(), mouseY - frame.cy()) : resting;
                final net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
                g.drawManaged(() -> WireRing.draw(canvas(g, font), frame, panels, all, hubSub, hint, lit));
            }

            @Override
            public com.createstats.book.specimen.SpecimenAnatomy anatomy() {
                return anatomy.get();
            }
        };
    }

    private List<WireRing.Panel> panels() {
        final List<WireRing.Panel> out = new ArrayList<>(spokes.size());
        for (Spoke s : spokes) {
            out.add(new WireRing.Panel(s.tier.strandA(), tensionName(s.pending).getString(),
                    spanLine(s).getString(), s.pending.ordinal(), WireTension.values().length, s.angle));
        }
        return out;
    }

    static WireCanvas canvas(GuiGraphics g, net.minecraft.client.gui.Font font) {
        return new WireCanvas() {
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
            public void shadowText(String s, int x, int y, int argb) {
                g.drawString(font, s, x, y, argb, true);
            }
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0) {
            return false;
        }

        apply(scrollY > 0 ? 1 : -1, false);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            apply(1, true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (Minecraft.getInstance().options.keyUse.matchesMouse(button)) {
            commit();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (Minecraft.getInstance().options.keyUse.matches(keyCode, scanCode)) {
            commit();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        WireTensionView.clearPreview();
        WireTensionHold.ringClosed();
        super.removed();
    }

    private void apply(int direction, boolean cycle) {
        if (hovered >= 0) {
            final Spoke s = spokes.get(hovered);
            final WireTension next = cycle ? s.pending.cycle() : s.pending.step(direction);
            if (next != s.pending) {
                s.pending = next;
                click(next);
            }
            return;
        }

        final WireTension base = commonest();
        final WireTension next = cycle ? base.cycle() : base.step(direction);
        boolean changed = false;
        for (Spoke s : spokes) {
            if (s.pending != next) {
                s.pending = next;
                changed = true;
            }
        }
        if (changed || next != base) {
            click(next);
        }
    }

    private void commit() {
        if (done) {
            return;
        }
        done = true;
        final List<Spoke> changed = new ArrayList<>();
        for (Spoke s : spokes) {
            if (s.pending != s.original) {
                changed.add(s);
            }
        }
        if (!changed.isEmpty()) {
            final long[] far = new long[changed.size()];
            final byte[] tension = new byte[changed.size()];
            for (int k = 0; k < changed.size(); k++) {
                far[k] = changed.get(k).far.asLong();
                tension[k] = (byte) changed.get(k).pending.id();
            }
            PacketDistributor.sendToServer(new WireTensionPacket(connector, far, tension));
        }
        onClose();
    }

    private void publishPreview() {
        final Map<Long, WireTension> tension = new HashMap<>();
        final Set<Long> highlight = new HashSet<>();
        for (int k = 0; k < spokes.size(); k++) {
            final Spoke s = spokes.get(k);
            tension.put(s.far.asLong(), s.pending);
            if (hovered == -1 || hovered == k) {
                highlight.add(s.far.asLong());
            }
        }
        WireTensionView.preview(connector, tension, highlight);
    }

    private WireTension commonest() {
        final Map<WireTension, Integer> count = new EnumMap<>(WireTension.class);
        WireTension best = WireTension.STANDARD;
        int bestN = 0;
        for (Spoke s : spokes) {
            final int n = count.merge(s.pending, 1, Integer::sum);
            if (n > bestN) {
                best = s.pending;
                bestN = n;
            }
        }
        return best;
    }

    private static void click(WireTension t) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8f + 0.15f * t.ordinal()));
    }

    private static boolean useHeld(Minecraft mc) {
        final InputConstants.Key key = mc.options.keyUse.getKey();
        final long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }

    // ---------- Drawing ----------

    private WireTension uniform() {
        WireTension shared = null;
        for (Spoke s : spokes) {
            if (shared == null) {
                shared = s.pending;
            } else if (shared != s.pending) {
                return null;
            }
        }
        return shared;
    }

    private Component tensionName(WireTension t) {
        return Component.translatable(WireRing.levelKey(t));
    }

    private Component spanLine(Spoke s) {
        return Component.translatable(WireRing.SPAN_KEY, WireRing.spanArgs(s.span, s.pending));
    }
}
