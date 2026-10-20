package com.createstats.vccmk4.client;

import com.createstats.client.LocalGuiDensity;
import com.createstats.surveyor.PathwayChecks;
import com.createstats.surveyor.RouteEvents;
import com.createstats.surveyor.client.PathwayClientCache;
import com.createstats.surveyor.client.StaffInput;
import com.createstats.surveyor.client.StaffMode;
import com.createstats.surveyor.core.PathHealth;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.SegmentCheck;
import com.createstats.surveyor.network.RouteActionPacket;
import com.createstats.surveyor.network.SurveyorEditPacket;
import com.createstats.vccmk4.console.BoardHost;
import com.createstats.vccmk4.console.PathwayBoard;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class PathwayBoardScreen extends Screen implements LocalGuiDensity {
    @Nullable private static PathwayBoardScreen open;

    private final PathwayBoard board;
    private final Vcc4Screen.Recorder recorder = new Vcc4Screen.Recorder();
    @Nullable private EditBox edit;
    @Nullable private Consumer<String> editDone;

    private PathwayBoardScreen(@Nullable UUID pathway, @Nullable UUID vehicle, int node) {
        super(Component.translatable("gui.create_stats.pathways.title"));
        this.board = new PathwayBoard(new Host(), pathway, vehicle, node);
    }

    public static void open(@Nullable UUID pathway, @Nullable UUID vehicle, @Nullable Integer node) {
        Minecraft.getInstance().setScreen(new PathwayBoardScreen(pathway, vehicle, node == null ? -1 : node));
    }

    public static boolean isOpen() {
        return open != null && Minecraft.getInstance().screen == open;
    }

    public static void refresh() {
    }

    @Override
    protected void init() {
        open = this;
    }

    @Override
    public void removed() {
        if (open == this) open = null;
        super.removed();
    }

    @Override
    public int maxEffectiveGuiScale() {
        Window window = Minecraft.getInstance().getWindow();
        int own = Math.max(1, (int) Math.round(window.getGuiScale()));
        for (int scale = own; scale > 1; scale--) {
            if (window.getWidth() / scale >= PathwayBoard.MIN_W + 2 * PathwayBoard.MARGIN
                    && window.getHeight() / scale >= PathwayBoard.MIN_H + 2 * PathwayBoard.MARGIN) {
                return scale;
            }
        }
        return 1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        recorder.begin();
        board.render(recorder, width, height, edit != null ? -100 : mx, edit != null ? -100 : my);
        recorder.replay(g, font);
        if (edit != null) {
            g.fill(edit.getX() - 4, edit.getY() - 16, edit.getX() + edit.getWidth() + 4, edit.getY() + edit.getHeight() + 4, 0xE0101010);
            g.drawString(font, I18n.get("gui.create_stats.pathways.name_prompt"), edit.getX(), edit.getY() - 12, 0xFFE4B763, false);
            edit.render(g, mx, my, partialTick);
            return;
        }
        List<String> tip = board.tooltip();
        if (!tip.isEmpty()) {
            List<Component> lines = new ArrayList<>(tip.size());
            for (String line : tip) lines.add(Component.literal(line));
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (edit != null) {
            if (!edit.isMouseOver(mx, my)) closeEdit(false);
            else edit.mouseClicked(mx, my, button);
            return true;
        }
        if (board.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (edit == null && board.mouseDragged(mx, my, button)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (edit == null && board.mouseReleased(mx, my, button)) return true;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (edit != null) return true;
        return board.mouseScrolled(mx, my, scrollY) || super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (edit != null) {
            if (keyCode == 257 || keyCode == 335) {
                closeEdit(true);
                return true;
            }
            if (keyCode == 256) {
                closeEdit(false);
                return true;
            }
            return edit.keyPressed(keyCode, scanCode, modifiers) || true;
        }
        if (board.keyPressed(keyCode)) return true;
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (edit != null) return edit.charTyped(c, modifiers);
        return super.charTyped(c, modifiers);
    }

    private void closeEdit(boolean commit) {
        EditBox box = edit;
        Consumer<String> done = editDone;
        edit = null;
        editDone = null;
        if (box != null) removeWidget(box);
        if (commit && box != null && done != null) {
            String s = box.getValue().strip();
            if (!s.isEmpty()) done.accept(s);
        }
    }

    private final class Host implements BoardHost {
        @Override
        public List<Line> pathways() {
            UUID me = Minecraft.getInstance().player == null ? new UUID(0, 0) : Minecraft.getInstance().player.getUUID();
            List<Line> out = new ArrayList<>();
            for (PathwayClientCache.Entry e : PathwayClientCache.all()) {
                Pathway p = e.pathway;
                PathwayChecks.Result c = e.lastChecks();
                int warn = 0;
                String warning = "";

                List<PathHealth.Item> health = PathwayClientCache.health(p.id());
                PathHealth.Item first = PathHealth.first(health);
                if (first != null) {
                    warn = first.level() == SegmentCheck.Level.RED ? 2 : 1;
                    warning = (warn == 2 ? I18n.get("gui.create_stats.pathways.warn_red")
                            : I18n.get("gui.create_stats.pathways.warn_amber")) + " " + first.why();
                } else if (c != null) {
                    SegmentCheck.Level worst = SegmentCheck.worst(c.findings());
                    warn = worst == SegmentCheck.Level.RED ? 2 : worst == SegmentCheck.Level.AMBER ? 1 : 0;
                    for (SegmentCheck.Finding f : c.findings()) {
                        if (f.level() == worst && f.level() != SegmentCheck.Level.FINE && f.level() != SegmentCheck.Level.UNKNOWN) {
                            warning = (worst == SegmentCheck.Level.RED ? I18n.get("gui.create_stats.pathways.warn_red")
                                    : I18n.get("gui.create_stats.pathways.warn_amber")) + " " + f.why();
                            break;
                        }
                    }
                }
                boolean mine = p.owner().equals(me);
                out.add(new Line(p, e.owner, PathwayClientCache.vehiclesOn(p.id()).size(), warn, warning, mine, mine || p.shared()
                        || (Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2))));
            }
            out.sort((a, b) -> {
                if (a.mine() != b.mine()) return a.mine() ? -1 : 1;
                return a.pathway().name().compareToIgnoreCase(b.pathway().name());
            });
            return out;
        }

        @Override
        public List<Health> health(UUID pathway) {
            List<Health> out = new ArrayList<>();
            for (PathHealth.Item i : PathwayClientCache.health(pathway)) {
                out.add(new Health(i.level().ordinal(), i.from(), i.to(), i.why(), i.fix().ordinal(), i.node(), i.at()));
            }
            return out;
        }

        @Override
        public boolean healthKnown(UUID pathway) {
            return PathwayClientCache.healthKnown(pathway);
        }

        private final java.util.Map<UUID, Terrain> terrainCache = new java.util.HashMap<>();
        private final java.util.Map<UUID, com.createstats.surveyor.network.PathwayTerrainPacket> terrainFrom = new java.util.HashMap<>();

        @Override
        public List<Terrain> terrain() {
            List<Terrain> out = new ArrayList<>();
            for (com.createstats.surveyor.network.PathwayTerrainPacket t : PathwayClientCache.terrain()) {
                if (terrainFrom.get(t.pathway()) != t) {
                    int[] argb = new int[t.colours().length];
                    for (int i = 0; i < argb.length; i++) {
                        int packed = t.colours()[i] & 0xFF;
                        if (packed == 0) continue;
                        int abgr = net.minecraft.world.level.material.MapColor.getColorFromPackedId(packed);
                        argb[i] = 0xFF000000 | ((abgr & 0xFF) << 16) | (abgr & 0xFF00) | ((abgr >> 16) & 0xFF);
                    }
                    terrainCache.put(t.pathway(), new Terrain(t.x0(), t.z0(), t.cell(), t.w(), t.h(), argb));
                    terrainFrom.put(t.pathway(), t);
                }
                out.add(terrainCache.get(t.pathway()));
            }
            return out;
        }

        @Override
        public List<Vehicle> vehicles() {
            List<Vehicle> out = new ArrayList<>();
            double t = PathwayClientCache.serverTime();
            for (PathwayClientCache.Vehicle v : PathwayClientCache.vehicles()) {
                if (v.now == null) continue;
                var n = v.now;
                double[] at = v.at(Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime());
                out.add(new Vehicle(n.console(), n.pathway(), n.name(), n.state(), n.reason(), at[0], at[2], (float) at[3], n.speed(), n.s(),
                        n.nextStop(), n.eta(), n.waitFraction(), n.waitText(), n.laps(), n.lastLap(), n.cargo()));
            }
            return out;
        }

        @Override
        public List<Trail> trails(UUID pathway) {
            List<Trail> out = new ArrayList<>();
            for (PathwayClientCache.Vehicle v : PathwayClientCache.vehiclesOn(pathway)) {
                PathwayClientCache.Trail t = v.trail();
                if (t.time().length > 0) out.add(new Trail(v.now.console(), v.now.name(), t.time(), t.s(), t.laps()));
            }
            return out;
        }

        @Override
        public List<LogLine> log() {
            List<LogLine> out = new ArrayList<>();
            for (RouteEvents.Line l : PathwayClientCache.log()) out.add(new LogLine(l.time(), l.pathway(), l.text()));
            return out;
        }

        @Override
        public long now() {
            return PathwayClientCache.serverTime();
        }

        @Nullable
        @Override
        public double[] you() {
            var p = Minecraft.getInstance().player;
            return p == null ? null : new double[]{p.getX(), p.getZ()};
        }

        @Override
        public double groundY(double x, double z, double fallback) {
            var level = Minecraft.getInstance().level;
            if (level == null) return fallback;
            double g = PathwayChecks.groundAt(level, x, fallback, z, new BlockPos.MutableBlockPos());
            if (Double.isNaN(g)) g = PathwayChecks.groundAt(level, x, fallback + 16, z, new BlockPos.MutableBlockPos());
            return Double.isNaN(g) ? fallback : g;
        }

        @Override
        public String heldItem() {
            var p = Minecraft.getInstance().player;
            if (p == null) return "";
            ItemStack st = p.getMainHandItem();
            if (st.isEmpty() || st.getItem() instanceof com.createstats.surveyor.SurveyorStaffItem) st = p.getOffhandItem();
            return st.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        }

        @Override
        public String heldFluid() {
            var p = Minecraft.getInstance().player;
            if (p == null) return "";
            for (ItemStack st : List.of(p.getMainHandItem(), p.getOffhandItem())) {
                if (st.getItem() instanceof BucketItem b && b.content != Fluids.EMPTY) {
                    return BuiltInRegistries.FLUID.getKey(b.content).toString();
                }
            }
            return "";
        }

        @Override
        public String itemName(String id) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) return id;
            var item = BuiltInRegistries.ITEM.getOptional(rl);
            if (item.isPresent()) return new ItemStack(item.get()).getHoverName().getString();
            var fluid = BuiltInRegistries.FLUID.getOptional(rl);
            if (fluid.isPresent()) return fluid.get().getFluidType().getDescription().getString();
            return id;
        }

        @Override
        public void sendEdit(int op, UUID pathway, long version, int node, double x, double y, double z, int a, String text, byte[] data) {
            PacketDistributor.sendToServer(new SurveyorEditPacket(op, pathway, version, node, x, y, z, a, text, data));
        }

        @Override
        public void sendOrder(int op, UUID console, UUID pathway, int a) {
            PacketDistributor.sendToServer(new RouteActionPacket(op, console, 0L, pathway, a));
        }

        @Override
        public void useWithStaff(UUID pathway) {
            StaffInput.select(pathway);
            onClose();
        }

        @Override
        public void startNew() {
            StaffInput.select(null);
            StaffInput.setMode(StaffMode.SURVEY);
            onClose();
            var p = Minecraft.getInstance().player;
            if (p != null) p.displayClientMessage(Component.translatable("create_stats.surveyor.hud.start_hint"), true);
        }

        @Override
        public void askText(String current, int maxLength, Consumer<String> done) {
            int w = Math.min(200, width - 40);
            EditBox box = new EditBox(font, (width - w) / 2, height / 2 - 8, w, 16, Component.empty());
            box.setMaxLength(maxLength);
            box.setValue(current == null ? "" : current);
            box.setFocused(true);
            edit = box;
            editDone = done;
            addRenderableWidget(box);
            setFocused(box);
        }

        @Override
        public void pin(UUID console) {
            StaffInput.pin(console);
            StaffInput.setMode(StaffMode.WATCH);
            onClose();
        }

        @Override
        public int width(String text) {
            return font.width(text);
        }

        @Override
        public String tr(String key, Object... args) {
            return I18n.get(key, args);
        }

        @Override
        public String name(String idOrName) {
            if (idOrName == null || idOrName.isEmpty()) return "";
            return I18n.exists(idOrName) ? I18n.get(idOrName) : idOrName;
        }

        @Override
        public String keyName(int keyId) {
            return Vcc4Keys.name(keyId);
        }

        @Override
        public List<String> keyAlso(int keyId) {
            return Vcc4Keys.alsoBound(keyId);
        }

        @Override
        public boolean keyLeavesSeat(int keyId) {
            return Vcc4Keys.leavesSeat(keyId);
        }

        @Override
        public int resolveKey(int glfwKey, int scanCode) {
            return Vcc4Keys.resolve(glfwKey, scanCode);
        }

        @Override
        public int resolveMouse(int button) {
            return Vcc4Keys.resolveMouse(button);
        }

        @Override
        public void send(int action, int a, int b, long target) {
        }

        @Override
        public void click() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }
}
