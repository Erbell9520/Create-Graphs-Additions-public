package com.createstats.gui;

import com.createstats.blockentity.SmartHomePanelBE;
import com.createstats.client.LocalGuiDensity;
import com.createstats.client.PeerIdentifyHighlight;
import com.createstats.gui.home.FloorPlan;
import com.createstats.gui.home.HomeGroups;
import com.createstats.gui.home.HomeKit;
import com.createstats.gui.home.HomeLayout;
import com.createstats.gui.home.LabelPlacer;
import com.createstats.gui.home.LampLight;
import com.createstats.gui.home.LampSpot;
import com.createstats.home.DayClock;
import com.createstats.gui.home.PlanGrid;
import com.createstats.gui.home.PlanPainter;
import com.createstats.gui.home.WireGraph;
import com.createstats.gui.home.WirePlan;
import com.createstats.home.FloorBands;
import com.createstats.home.HouseModel;
import com.createstats.home.MapFit;
import com.createstats.home.RoomFill;
import com.createstats.home.SceneDiff;
import com.createstats.network.SmartHomeActionPacket;
import com.createstats.network.SmartHomeRenamePacket;
import com.createstats.network.SmartHomeSyncPacket;
import com.createstats.power.ApplianceStatus;
import com.createstats.power.FeRates;
import com.createstats.power.HomeAppliance;
import com.createstats.power.HomeSegment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SmartHomePanelScreen extends Screen implements LocalGuiDensity {
    private static final int POLL_TICKS = 10;

    private enum View { PLAN, POWER, LIGHT }

    private enum Kind {
        VIEW, FLOOR_PREV, FLOOR_NEXT, FIT, MARK, TITLE, ALL_ON, ALL_OFF, STANDBY_OFF, LIGHT_FIX,
        GROUP_SWITCH, GROUP_PICK, UNLISTED_FOLD, ROW_TOGGLE, ROW_OPEN, ROW_RENAME, MODE, COLOUR, UNLINK,
        ROOM_ICON, ROOM_RENAME, ROOM_FOLLOW, SCENE, SCENE_NAME, SCENE_ICON, SCENE_SAVE_ALL, SCENE_SAVE_ROOM,
        SCENE_SAVE_LIGHTS, SCENE_DELETE, SCENE_DONE, SCENE_RULE, SET_NAME, SET_FACE, SET_TOASTS, SET_RULE, SET_DONE,
        LIST, SCROLLER, EDITOR
    }

    private record Hit(int x, int y, int w, int h, Kind kind, Object arg, int value) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final SmartHomePanelBE panel;
    private final FloorPlan plan;

    @Nullable
    private SmartHomeSyncPacket data;
    @Nullable
    private SmartHomeSyncPacket.Config config;
    private int knownRevision = -1;
    private List<SmartHomeSyncPacket.Wire> wiring = List.of();

    @Nullable
    private WireGraph graph;
    @Nullable
    private List<SmartHomeSyncPacket.Wire> graphSource;

    private View view = View.PLAN;
    private int storey = -1;
    private int zoom;
    private int panX;
    private int panZ;

    private int selectedRoom = -1;

    @Nullable
    private BlockPos openRow;
    private boolean unlistedOpen;
    private int listScroll;

    private float listScrollShown;
    private int listContent;

    private long scrollToGroup = Long.MIN_VALUE;

    @Nullable
    private BlockPos scrollToRow;
    private int editingScene = -1;
    private boolean settingsOpen;
    private int pollCounter;

    private int resampleIn;

    private boolean dragging;
    private double dragStartX;
    private double dragStartY;
    private int dragPanX;
    private int dragPanZ;
    private boolean pressedOnPlan;
    @Nullable
    private Hit sliderHeld;

    private EditBox nameBox;
    private int renameKind = -1;
    @Nullable
    private BlockPos renameRow;
    private int renameId;
    private String renameOriginal = "";

    private HomeLayout layout;
    private MapFit fit;
    private final List<Hit> hits = new ArrayList<>();

    @Nullable
    private List<Component> tooltip;
    @Nullable
    private List<BlockPos> identify;

    @Nullable
    private SmartHomeSyncPacket.Row listHover;

    @Nullable
    private SmartHomeSyncPacket.Row mapHover;
    private int previewScene = -1;

    private final Map<Integer, Set<Long>> roomCells = new HashMap<>();
    private final Map<Integer, Long> roomCentres = new HashMap<>();

    public SmartHomePanelScreen(SmartHomePanelBE panel) {
        super(Component.translatable("block.create_stats.smart_home_panel"));
        this.panel = panel;
        this.plan = new FloorPlan(ResourceLocation.fromNamespaceAndPath("create_stats",
                "smart_home_plan/" + Long.toHexString(panel.getBlockPos().asLong())));
    }

    // player's own gui scale when it fits. the density cap cost a third of every click target here
    @Override
    public int maxEffectiveGuiScale() {
        final var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return Math.max(1, Math.min(window.getWidth() / 400, window.getHeight() / 230));
    }

    @Override
    protected void init() {
        layout = HomeLayout.of(width, height);
        nameBox = new EditBox(font, 0, -100, 40, 16, Component.empty());
        nameBox.setMaxLength(HouseModel.MAX_NAME);
        nameBox.setBordered(true);
        nameBox.visible = false;

        addWidget(nameBox);
        renameKind = -1;
        plan.invalidate();
        poll();
    }

    public void accept(SmartHomeSyncPacket packet) {
        if (!packet.panelPos().equals(panel.getBlockPos())) {
            return;
        }
        data = packet;
        knownRevision = packet.revision();
        if (packet.config() != null) {
            config = packet.config();
            roomCells.clear();
            roomCentres.clear();
        }
        if (packet.wiring() != null) {
            wiring = packet.wiring();
        }
        if (storey < 0 || storey >= packet.storeys().size()) {
            storey = Math.max(0, Math.min(packet.storeys().size() - 1, packet.panelStorey()));
        }
        if (selectedRoom >= 0 && room(selectedRoom) == null) {
            selectedRoom = -1;
        }
        if (editingScene >= 0 && scene(editingScene) == null) {
            editingScene = -1;
        }

        if ((renameKind == SmartHomeRenamePacket.ROOM && room(renameId) == null)
                || (renameKind == SmartHomeRenamePacket.SCENE && scene(renameId) == null)
                || (renameKind == SmartHomeRenamePacket.ROW && (renameRow == null || rowAt(renameRow) == null))) {
            cancelRename();
        }
    }

    public BlockPos panelPos() {
        return panel.getBlockPos();
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null || minecraft.level == null || panel.isRemoved()
                || minecraft.level.getBlockEntity(panel.getBlockPos()) != panel) {
            onClose();
            return;
        }
        if (++pollCounter % POLL_TICKS == 0) {
            poll();
        }
        if (resampleIn > 0 && --resampleIn == 0) {
            plan.invalidate();
        }
    }

    private void poll() {
        PacketDistributor.sendToServer(SmartHomeActionPacket.refresh(panel.getBlockPos(), knownRevision,
                view == View.POWER));
    }

    @Override
    public void onClose() {
        PeerIdentifyHighlight.clear();
        super.onClose();
    }

    @Override
    public void removed() {
        plan.close();
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        nameBox.visible = false;
        hits.clear();
        tooltip = null;
        identify = null;
        listHover = null;
        previewScene = -1;
        final HomeLayout l = layout;
        final boolean modal = editingScene >= 0 || settingsOpen;
        final int mx = modal ? Integer.MIN_VALUE : mouseX;
        final int my = modal ? Integer.MIN_VALUE : mouseY;
        g.fill(0, 0, width, height, 0x66000000);

        HomeKit.frame(g, l.x, l.y, l.w, l.h);
        drawTitle(g, l, mx, my);

        drawSide(g, l, mx, my);
        drawScenes(g, l, mx, my);
        drawMap(g, l, mx, my, partialTick);
        drawFoot(g, l, mx, my);
        if (editingScene >= 0) {
            drawSceneEditor(g, l, mouseX, mouseY);
        } else if (settingsOpen) {
            drawSettings(g, l, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (nameBox.visible && (renameKind == SmartHomeRenamePacket.SCENE || renameKind == SmartHomeRenamePacket.HOUSE)) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            nameBox.render(g, mouseX, mouseY, partialTick);
            g.pose().popPose();
        }
        if (identify != null && !identify.isEmpty()) {
            PeerIdentifyHighlight.show(identify, 0x55FF55);
        } else {
            PeerIdentifyHighlight.clear();
        }
        if (tooltip != null && !tooltip.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    private void drawTitle(GuiGraphics g, HomeLayout l, int mx, int my) {
        final String house = panel.houseName().getString();

        final int nameW = font.width(house) + 12;
        final boolean hot = l.onTitle(mx, my, nameW);
        g.drawString(font, house, l.x + 8, l.y + 6, HomeKit.TITLE, false);
        g.pose().pushPose();
        g.pose().translate(l.x + 10 + font.width(house), l.y + 5, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        g.renderItem(new ItemStack(Items.WRITABLE_BOOK), 0, 0);
        g.pose().popPose();
        if (hot) {
            g.fill(l.x + 8, l.y + 15, l.x + 8 + font.width(house), l.y + 16, HomeKit.TITLE);
            tooltip = List.of(Component.literal(house),
                    Component.translatable("gui.create_stats.smart_home.title.hint").withStyle(ChatFormatting.GRAY));
        }
        hits.add(new Hit(l.x + 6, l.y + 3, nameW + 4, HomeLayout.TITLE_H - 4, Kind.TITLE, null, 0));
        final Component right;
        final int ink;
        if (data != null && !data.awake()) {
            right = Component.translatable("gui.create_stats.smart_home.mains_lost");
            ink = HomeKit.WARN_INK;
        } else {
            int on = 0;
            int all = 0;
            for (SmartHomeSyncPacket.Row r : flatRows()) {
                all++;
                on += isOn(r) ? 1 : 0;
            }
            right = Component.translatable("gui.create_stats.smart_home.count_on", on, all);
            ink = HomeKit.TITLE;
        }
        g.drawString(font, right, l.x + l.w - 8 - font.width(right), l.y + 6, ink, false);
    }

    private int storeyY() {
        if (data == null || data.storeys().isEmpty()) {
            return panel.getBlockPos().getY() - 1;
        }
        return data.storeys().get(Math.max(0, Math.min(data.storeys().size() - 1, storey)));
    }

    private boolean onStorey(int floorY) {
        if (data == null || data.storeys().isEmpty()) {
            return true;
        }
        return FloorBands.storeyOf(data.storeys(), floorY) == Math.max(0, storey);
    }

    private void drawMap(GuiGraphics g, HomeLayout l, int mx, int my, float partialTick) {
        HomeKit.well(g, l.mapX, l.mapY, l.mapSize, l.mapSize);
        HomeKit.parchment(g, l.mapX + 1, l.mapY + 1, l.mapSize - 2, l.mapSize - 2);
        final int size = l.planSize;
        if (data == null) {
            fit = MapFit.fit(size, panel.getBlockPos().getX(), panel.getBlockPos().getZ(), panel.getBlockPos().getX(),
                    panel.getBlockPos().getZ(), zoom, panX, panZ);
        } else {
            fit = MapFit.fit(size, data.minX(), data.minZ(), data.maxX(), data.maxZ(), panel.getBlockPos().getX(),
                    panel.getBlockPos().getZ(), zoom, panX, panZ);
        }
        final int fy = storeyY();
        if (minecraft != null && minecraft.level != null) {
            final int x0 = fit.firstX();
            final int z0 = fit.firstZ();

            plan.ensure(minecraft.level, x0, z0, fit.lastX() - x0 + 1, fit.lastZ() - z0 + 1, fy,
                    view == View.LIGHT, minecraft.level.getGameTime(), dragging);
        }

        final List<PlanPainter.Lamp> lamps = litLamps();
        plan.paint(fit.scale(), lamps, lampsKey(lamps));
        g.enableScissor(l.planX, l.planY, l.planX + size, l.planY + size);
        plan.draw(g, l.planX + fit.px(plan.firstX()), l.planY + fit.pz(plan.firstZ()));

        final boolean overPlan = l.onPlan(mx, my);
        final int hx = overPlan ? fit.blockX(mx - l.planX) : Integer.MIN_VALUE;
        final int hz = overPlan ? fit.blockZ(my - l.planY) : Integer.MIN_VALUE;

        switch (view) {
            case PLAN -> {
            }
            case POWER -> drawPowerOverlay(g, l, mx, my);
            case LIGHT -> drawSpawnMarks(g, l);
        }
        final SmartHomeSyncPacket.Room pickedRoom = selectedRoom >= 0 ? room(selectedRoom) : null;
        if (pickedRoom != null && onStorey(pickedRoom.floorY())) {
            contour(g, cellSet(pickedRoom), 0xFFFFFFFF, 1);
        }
        drawRoomLabels(g, l);
        final boolean onMark = drawMarks(g, l, mx, my, overPlan);
        drawPlayers(g, l);
        if (previewScene >= 0) {
            drawScenePreview(g, l);
        }

        if (listHover != null && onStorey(listHover.floorY())) {
            outlineGroup(g, listHover.members(), 0xFFFFFFFF);
        }
        if (overPlan && !onMark && tooltip == null) {
            hoverFloor(g, l, hx, hz);
        }
        g.disableScissor();
        drawOffMap(g, l);

        HomeKit.northMark(g, l.mapX + l.mapSize - 7, l.mapY + 1);
    }

    private void hoverFloor(GuiGraphics g, HomeLayout l, int hx, int hz) {
        final List<Component> lines = new ArrayList<>();
        final Component thing = nameAt(hx, hz);
        final SmartHomeSyncPacket.Room room = roomAt(hx, hz);

        final byte kind = plan.kind(hx, hz);
        final boolean plainFloor = (kind == PlanGrid.FLOOR || kind == PlanGrid.GROUND) && plan.sym(hx, hz) == PlanGrid.SYM_NONE;
        if (thing != null && !plainFloor) {
            lines.add(thing);
            if (room != null) {
                lines.add(Component.literal(room.name()).withStyle(ChatFormatting.GRAY));
            }
        } else if (room != null) {
            lines.add(Component.literal(room.name()));
        } else if (thing != null) {
            lines.add(thing);
        }
        if (room != null) {
            if (room.id() != selectedRoom) {
                contour(g, cellSet(room), 0xC0FFFFFF, 1);
            }
            int on = 0;
            int all = 0;
            int draw = 0;
            for (SmartHomeSyncPacket.Row r : flatRows()) {
                if (r.roomId() == room.id()) {
                    all++;
                    on += isOn(r) ? 1 : 0;
                    draw = FeRates.plus(draw, Math.max(0, r.drawFe()));
                }
            }
            lines.add(Component.translatable("gui.create_stats.smart_home.room.summary", on, all, FeRates.perTick(draw))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (view == View.LIGHT && plan.kind(hx, hz) != PlanGrid.NONE) {
            lines.add(Component.translatable("gui.create_stats.smart_home.plan.light", plan.light(hx, hz))
                    .withStyle(ChatFormatting.GRAY));
            if (plan.spawnable(hx, hz)) {
                lines.add(Component.translatable("gui.create_stats.smart_home.plan.spawn").withStyle(ChatFormatting.RED));
            }
            final long here = RoomFill.pack(hx, hz);
            if (lampSpot != null && lampSpot.x() == hx && lampSpot.z() == hz) {
                lines.add(0, Component.translatable("gui.create_stats.smart_home.light.best").withStyle(ChatFormatting.YELLOW));
                lines.add(1, Component.translatable("gui.create_stats.smart_home.light.best.count", lampSpot.lit(),
                        darkCells.size()).withStyle(ChatFormatting.GRAY));
            } else if (!darkCells.isEmpty() && standsOnFloor(here) && passesLight(here)) {
                lines.add(Component.translatable("gui.create_stats.smart_home.light.what_if",
                        LampSpot.lights(here, darkCells, this::passesLight, LampSpot.REACH), darkCells.size())
                        .withStyle(ChatFormatting.YELLOW));
                for (long c : LampSpot.reach(here, this::passesLight, 2).keySet()) {
                    final int gx = RoomFill.unpackX(c);
                    final int gz = RoomFill.unpackZ(c);
                    if (fit.visible(gx, gz)) {
                        g.fill(l.planX + fit.px(gx), l.planY + fit.pz(gz), l.planX + fit.px(gx) + fit.scale(),
                                l.planY + fit.pz(gz) + fit.scale(), 0x40FFE066);
                    }
                }
            }
        } else if (thing != null && room == null && !plan.has(hx, hz, PlanGrid.F_ROOFED)) {
            lines.add(Component.translatable("gui.create_stats.smart_home.plan.sky").withStyle(ChatFormatting.GRAY));
        }
        final int storeys = data == null ? 1 : data.storeys().size();
        if (kind == PlanGrid.STAIRS && storey < storeys - 1) {
            lines.add(Component.translatable("gui.create_stats.smart_home.plan.stairs.up").withStyle(ChatFormatting.DARK_GRAY));
        } else if (kind == PlanGrid.STAIRS_BELOW && storey > 0) {
            lines.add(Component.translatable("gui.create_stats.smart_home.plan.stairs.down").withStyle(ChatFormatting.DARK_GRAY));
        } else if (room != null) {
            lines.add(Component.translatable("gui.create_stats.smart_home.room.pick").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!lines.isEmpty()) {
            tooltip = lines;
        }
    }

    @Nullable
    private Component nameAt(int x, int z) {
        final int f = plan.floorY();
        final byte kind = plan.kind(x, z);
        if (minecraft == null || minecraft.level == null || f == Integer.MIN_VALUE || kind == PlanGrid.NONE) {
            return null;
        }
        final BlockPos feet = new BlockPos(x, f + 1, z);
        BlockState st = minecraft.level.getBlockState(feet);
        if (kind == PlanGrid.STAIRS || kind == PlanGrid.STAIRS_BELOW) {
            final int from = kind == PlanGrid.STAIRS ? f + 1 : f - 3;
            final int to = kind == PlanGrid.STAIRS ? f + 7 : f;
            for (int y = from; y <= to; y++) {
                final BlockState step = minecraft.level.getBlockState(new BlockPos(x, y, z));
                if (step.getBlock() instanceof StairBlock) {
                    st = step;
                    break;
                }
            }
            return Component.translatable(kind == PlanGrid.STAIRS ? "gui.create_stats.smart_home.plan.up"
                    : "gui.create_stats.smart_home.plan.down", st.getBlock().getName());
        }
        final BlockState head = minecraft.level.getBlockState(feet.above());
        if (!head.isAir() && (st.isAir() || kind == PlanGrid.WINDOW)) {
            st = head;
        }
        if (st.isAir()) {
            st = minecraft.level.getBlockState(feet.below());
        }
        if (st.isAir()) {
            return null;
        }
        final Component name = st.getBlock().getName();
        if ((kind == PlanGrid.DOOR || kind == PlanGrid.GATE) && plan.has(x, z, PlanGrid.F_OPEN)) {
            return Component.translatable("gui.create_stats.smart_home.plan.open", name);
        }
        return name;
    }

    private int cx(int x) {
        return layout.planX + fit.px(x) + fit.scale() / 2;
    }

    private int cz(int z) {
        return layout.planY + fit.pz(z) + fit.scale() / 2;
    }

    private boolean drawMarks(GuiGraphics g, HomeLayout l, int mx, int my, boolean overPlan) {
        if (data == null) {
            return false;
        }
        final int s = fit.scale();
        SmartHomeSyncPacket.Row hovered = null;
        int best = 26;
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            if (!onStorey(r.floorY())) {
                continue;
            }
            final boolean lamp = r.category() == HomeAppliance.Category.LIGHTING.ordinal();
            if (view == View.LIGHT && !lamp) {
                continue;
            }
            if (lamp) {
                drawLamp(g, r);
            }
            for (BlockPos p : r.members()) {
                if (!fit.visible(p.getX(), p.getZ())) {
                    continue;
                }
                final int x = cx(p.getX());
                final int z = cz(p.getZ());
                if (!lamp) {
                    HomeKit.mark(g, markOf(r), x, z, isOn(r), ledOf(r), false);
                    if (r.running() && r.progress() >= 0 && s >= 8) {
                        g.fill(x - 3, z + 4, x + 4, z + 6, 0xFF000000);
                        g.fill(x - 3, z + 4, x - 3 + Math.round(7 * r.progress()), z + 5, HomeKit.FILL_BAR);
                    }
                }
                if (overPlan) {
                    final int d = (mx - x) * (mx - x) + (my - z) * (my - z);
                    if (d < best) {
                        best = d;
                        hovered = r;
                    }
                }
                hits.add(new Hit(x - 4, z - 4, 9, 9, Kind.MARK, r.anchor(), 0));
            }
        }
        if (view == View.PLAN) {
            for (SmartHomeSyncPacket.Hint h : data.unlisted()) {
                if (onStorey(h.floorY()) && fit.visible(h.pos().getX(), h.pos().getZ())) {
                    HomeKit.mark(g, HomeKit.Mark.BOX, cx(h.pos().getX()), cz(h.pos().getZ()), false, 0xFF3A3A3A,
                            true);
                }
            }
        }
        final BlockPos panelPos = panel.getBlockPos();
        if (fit.visible(panelPos.getX(), panelPos.getZ())) {
            g.blit(HomeKit.decoration("frame"), cx(panelPos.getX()) - 4, cz(panelPos.getZ()) - 4, 0, 0, 8, 8, 8, 8);
        }
        mapHover = hovered;
        if (hovered != null) {
            outlineGroup(g, hovered.members(), 0xFFFFFFFF);
            identify = hovered.members();
            tooltip = rowTooltip(hovered, true);
            return true;
        }
        return false;
    }

    private void drawLamp(GuiGraphics g, SmartHomeSyncPacket.Row r) {
        final HomeKit.Lamp state = switch (state(r)) {
            case ON -> HomeKit.Lamp.LIT;
            case NO_POWER, SHED -> HomeKit.Lamp.STARVED;
            default -> HomeKit.Lamp.OFF;
        };
        final List<BlockPos> members = r.members();
        if (members.size() > 1 && isStrip(r)) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos p : members) {
                minX = Math.min(minX, p.getX());
                maxX = Math.max(maxX, p.getX());
                minZ = Math.min(minZ, p.getZ());
                maxZ = Math.max(maxZ, p.getZ());
            }
            if (minZ == maxZ) {
                HomeKit.lampBar(g, cx(minX) - 3, cz(minZ) - 2, cx(maxX) + 4, cz(minZ) + 3, state);
                return;
            }
            if (minX == maxX) {
                HomeKit.lampBar(g, cx(minX) - 2, cz(minZ) - 3, cx(minX) + 3, cz(maxZ) + 4, state);
                return;
            }
        }

        final int size = HomeKit.discSize(fit.scale(), members.size() > 1);
        final int tint = discTint(r.colour());
        for (BlockPos p : members) {
            if (fit.visible(p.getX(), p.getZ())) {
                HomeKit.lampDisc(g, cx(p.getX()), cz(p.getZ()), state, size, tint);
            }
        }
    }

    private static int discTint(int dye) {
        if (dye <= DyeColor.WHITE.getId()) {
            return HomeKit.LAMP_WARM;
        }
        return PlanPainter.mix(HomeKit.LAMP_WARM, DyeColor.byId(dye).getTextureDiffuseColor() & 0xFFFFFF, 0.7f);
    }

    private boolean isStrip(SmartHomeSyncPacket.Row r) {
        return BuiltInRegistries.ITEM.getKey(rowStack(r).getItem()).getPath().equals("strip_lamp");
    }

    private List<PlanPainter.Lamp> litLamps() {
        final List<PlanPainter.Lamp> out = new ArrayList<>();
        if (data == null || view == View.LIGHT) {
            return out;
        }
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            if (r.category() != HomeAppliance.Category.LIGHTING.ordinal() || !LampLight.pools(state(r))
                    || !onStorey(r.floorY())) {
                continue;
            }
            final long[] cells = new long[r.members().size()];
            for (int i = 0; i < cells.length; i++) {
                cells[i] = PlanPainter.pack(r.members().get(i).getX(), r.members().get(i).getZ());
            }
            out.add(new PlanPainter.Lamp(cells, LampLight.level(r.mode(), r.modes().size()), lampTint(r.colour())));
        }
        return out;
    }

    private static int lampTint(int dye) {
        if (dye <= DyeColor.WHITE.getId()) {
            return PlanPainter.GLOW;
        }
        return PlanPainter.mix(PlanPainter.GLOW, DyeColor.byId(dye).getTextureDiffuseColor() & 0xFFFFFF, 0.7f);
    }

    private static long lampsKey(List<PlanPainter.Lamp> lamps) {
        long key = lamps.size();
        for (PlanPainter.Lamp lamp : lamps) {
            for (long c : lamp.cells()) {
                key = key * 1_000_003L + c;
            }
            key = key * 1_000_003L + Float.floatToIntBits(lamp.level());
            key = key * 1_000_003L + lamp.tint();
        }
        return key;
    }

    private static HomeKit.Mark markOf(SmartHomeSyncPacket.Row r) {
        if (r.category() == HomeAppliance.Category.LIGHTING.ordinal()) {
            return HomeKit.Mark.BULB;
        }
        if (r.category() == HomeAppliance.Category.SOCKETS.ordinal()) {
            return HomeKit.Mark.SOCKET;
        }
        return HomeKit.Mark.BOX;
    }

    private static int ledOf(SmartHomeSyncPacket.Row r) {
        return switch (state(r)) {
            case ON -> r.category() == HomeAppliance.Category.SOCKETS.ordinal() ? 0xFFF0EBD8 : 0xFF55FF55;
            case OFF, MISSING -> r.category() == HomeAppliance.Category.SOCKETS.ordinal() ? 0xFFB8B4A8 : 0xFF3A3A3A;
            case NO_POWER, SHED -> 0xFFFFAA00;
        };
    }

    private void outlineGroup(GuiGraphics g, List<BlockPos> members, int colour) {
        final Set<Long> cells = new HashSet<>();
        for (BlockPos p : members) {
            cells.add(RoomFill.pack(p.getX(), p.getZ()));
        }
        contour(g, cells, colour, 1);
    }

    private void contour(GuiGraphics g, Set<Long> cells, int colour, int pad) {
        final int s = fit.scale();
        for (long c : cells) {
            final int x = RoomFill.unpackX(c);
            final int z = RoomFill.unpackZ(c);
            if (!fit.visible(x, z)) {
                continue;
            }
            final int x0 = layout.planX + fit.px(x) - pad;
            final int z0 = layout.planY + fit.pz(z) - pad;
            final int x1 = x0 + s + 2 * pad;
            final int z1 = z0 + s + 2 * pad;
            if (!cells.contains(RoomFill.pack(x, z - 1))) {
                g.fill(x0, z0, x1, z0 + 1, colour);
            }
            if (!cells.contains(RoomFill.pack(x, z + 1))) {
                g.fill(x0, z1 - 1, x1, z1, colour);
            }
            if (!cells.contains(RoomFill.pack(x - 1, z))) {
                g.fill(x0, z0, x0 + 1, z1, colour);
            }
            if (!cells.contains(RoomFill.pack(x + 1, z))) {
                g.fill(x1 - 1, z0, x1, z1, colour);
            }
        }
    }

    private Set<Long> cellSet(SmartHomeSyncPacket.Room room) {
        return roomCells.computeIfAbsent(room.id(), id -> {
            final Set<Long> cells = new HashSet<>(room.cells().length * 2);
            for (long c : room.cells()) {
                cells.add(c);
            }
            return cells;
        });
    }

    private long centreOf(SmartHomeSyncPacket.Room room) {
        return roomCentres.computeIfAbsent(room.id(), id -> HouseModel.centreCell(cellSet(room)));
    }

    private void drawPlayers(GuiGraphics g, HomeLayout l) {
        if (data == null || view == View.LIGHT) {
            return;
        }
        final String me = minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().getName() : "";
        for (SmartHomeSyncPacket.Player p : data.players()) {
            if (!onStorey(p.floorY()) || !fit.visible(p.x(), p.z())) {
                continue;
            }
            final int x = cx(p.x());
            final int z = cz(p.z());
            final var pose = g.pose();
            pose.pushPose();
            pose.translate(x, z, 0);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(p.yaw() + 180f));

            HomeKit.viewCone(g, Math.min(24, 2 * fit.scale() + 4));
            g.blit(HomeKit.decoration("player"), -4, -4, 0, 0, 8, 8, 8, 8);
            pose.popPose();
            if (!p.name().equals(me)) {
                HomeKit.labelBox(g, font, p.name(), x + 6, z - 4);
            }
        }
    }

    private void drawOffMap(GuiGraphics g, HomeLayout l) {
        if (data == null || view == View.LIGHT) {
            return;
        }
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            final BlockPos p = r.anchor();
            if (fit.visible(p.getX(), p.getZ()) || !onStorey(r.floorY())) {
                continue;
            }
            final int x = l.planX + fit.clampInside(fit.px(p.getX()) + fit.scale() / 2, 3);
            final int z = l.planY + fit.clampInside(fit.pz(p.getZ()) + fit.scale() / 2, 3);
            g.blit(HomeKit.decoration("player_off_map"), x - 4, z - 4, 0, 0, 8, 8, 8, 8);
            hits.add(new Hit(x - 4, z - 4, 9, 9, Kind.MARK, r.anchor(), 0));
        }
    }

    // label inside the room, never across a wall, never over a fixture
    private void drawRoomLabels(GuiGraphics g, HomeLayout l) {
        if (config == null) {
            return;
        }
        final Map<Integer, Integer> draws = view == View.POWER ? drawByRoom() : Map.of();
        final Set<Long> marked = new HashSet<>();
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            for (BlockPos p : r.members()) {
                marked.add(RoomFill.pack(p.getX(), p.getZ()));
            }
        }

        final Set<Long> taken = new HashSet<>();
        for (SmartHomeSyncPacket.Room room : config.rooms()) {
            if (!onStorey(room.floorY()) || room.cells().length == 0) {
                continue;
            }
            final String text;
            if (view == View.POWER) {
                final int d = draws.getOrDefault(room.id(), 0);
                if (d <= 0) {
                    continue;
                }
                text = FeRates.perTick(d);
            } else {
                text = room.name();
            }

            final long key = (((long) plan.generation()) << 40) ^ (((long) fit.scale()) << 32) ^ (((long) view.ordinal()) << 28)
                    ^ (((long) knownRevision) << 8) ^ marked.size() ^ (((long) text.hashCode()) << 44);

            final boolean withIcon = view != View.POWER;
            final int fullWidth = font.width(text) + (withIcon ? HomeKit.LABEL_ICON : 0);
            LabelPlacer.Spot spot = labelSpots.get(room.id());
            if (spot == null || labelKeys.getOrDefault(room.id(), Long.MIN_VALUE) != key) {
                spot = LabelPlacer.place(cellSet(room), centreOf(room), marked,
                        c -> plan.isClearFloor(RoomFill.unpackX(c), RoomFill.unpackZ(c)),
                        c -> innerWall(RoomFill.unpackX(c), RoomFill.unpackZ(c)), taken, fullWidth, fit.scale());
                if (spot == null && withIcon) {
                    spot = LabelPlacer.place(cellSet(room), centreOf(room), marked,
                            c -> plan.isClearFloor(RoomFill.unpackX(c), RoomFill.unpackZ(c)),
                            c -> innerWall(RoomFill.unpackX(c), RoomFill.unpackZ(c)), taken, 8, fit.scale());
                    if (spot != null) {
                        spot = new LabelPlacer.Spot(spot.x(), spot.z(), -spot.inset() - 1);
                    }
                }
                labelKeys.put(room.id(), key);
                if (spot == null) {
                    labelSpots.remove(room.id());
                } else {
                    labelSpots.put(room.id(), spot);
                }
            }
            if (spot == null) {
                continue;
            }

            final boolean iconOnly = spot.inset() < 0;
            final int inset = iconOnly ? -spot.inset() - 1 : spot.inset();
            LabelPlacer.take(taken, spot, iconOnly ? 8 : fullWidth, fit.scale());
            if (!fit.visible(spot.x(), spot.z())) {
                continue;
            }
            final int lx = l.planX + fit.px(spot.x()) + inset;
            final int ly = cz(spot.z()) - 4;
            if (withIcon) {
                HomeKit.labelBoxIcon(g, font, iconStack(room.icon()), iconOnly ? "" : text, lx, ly);
            } else {
                HomeKit.labelBox(g, font, text, lx, ly);
            }
        }
    }

    private boolean innerWall(int x, int z) {
        if (!PlanGrid.isWallish(plan.kind(x, z))) {
            return false;
        }
        for (int d = 0; d < 4; d++) {
            final int nx = x + PlanGrid.dx(d);
            final int nz = z + PlanGrid.dz(d);
            if (!PlanGrid.isWallish(plan.kind(nx, nz)) && !plan.has(nx, nz, PlanGrid.F_ROOFED)) {
                return false;
            }
        }
        return true;
    }

    private final Map<Integer, LabelPlacer.Spot> labelSpots = new HashMap<>();
    private final Map<Integer, Long> labelKeys = new HashMap<>();

    private void drawPowerOverlay(GuiGraphics g, HomeLayout l, int mx, int my) {
        if (data == null) {
            return;
        }

        final int low = storeyY() - 2;
        final int high = data.storeys().size() > storey + 1 ? data.storeys().get(storey + 1) - 2 : storeyY() + 8;
        final WireGraph graph = wireGraph();
        if (graph.isEmpty()) {
            return;
        }
        final SmartHomeSyncPacket.Row focus = listHover != null ? listHover : mapHover;
        List<int[]> blocks = null;
        if (focus != null) {
            blocks = new ArrayList<>();
            for (BlockPos p : focus.members()) {
                blocks.add(new int[] {p.getX(), p.getY(), p.getZ()});
            }
        }

        WirePlan.draw(g::fill, graph, low, high, fit.scale(), this::cx, this::cz, blocks);

        if (wireFocus != null) {
            final WireGraph.Wire fw = graph.at(wireFocus[0], wireFocus[1], wireFocus[2]);
            if (fw == null) {
                wireFocus = null;
            } else {
                final Set<Long> fed = graph.downstream(fw.x(), fw.y(), fw.z());
                for (SmartHomeSyncPacket.Row r : fedRows(fed)) {
                    if (onStorey(r.floorY())) {
                        outlineGroup(g, r.members(), 0xFF55FFFF);
                    }
                }
                if (fit.visible(fw.x(), fw.z()) && fw.y() >= low && fw.y() < high) {
                    g.fill(cx(fw.x()) - 1, cz(fw.z()) - 1, cx(fw.x()) + 2, cz(fw.z()) + 2, 0xFF55FFFF);
                }
            }
        }

        if (!l.onPlan(mx, my) || minecraft == null || minecraft.level == null) {
            return;
        }
        final int hx = fit.blockX(mx - l.planX);
        final int hz = fit.blockZ(my - l.planY);
        for (WireGraph.Wire w : graph.band(low, high)) {
            if (w.x() != hx || w.z() != hz) {
                continue;
            }
            final BlockPos at = new BlockPos(w.x(), w.y(), w.z());
            final List<Component> lines = new ArrayList<>();
            lines.add(minecraft.level.getBlockState(at).getBlock().getName());
            lines.add(w.flow() < 0
                    ? Component.translatable("gui.create_stats.smart_home.wire.unknown").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("gui.create_stats.smart_home.wire.flow", FeRates.perTick(w.flow()))
                            .withStyle(ChatFormatting.GOLD));
            if (graph.hops(w.x(), w.y(), w.z()) == 0) {
                lines.add(Component.translatable("gui.create_stats.smart_home.wire.entry").withStyle(ChatFormatting.AQUA));
            }

            final List<SmartHomeSyncPacket.Row> fed = fedRows(graph.downstream(w.x(), w.y(), w.z()));
            int draw = 0;
            for (SmartHomeSyncPacket.Row r : fed) {
                draw = FeRates.plus(draw, Math.max(0, r.drawFe()));
            }
            lines.add(fed.isEmpty()
                    ? Component.translatable("gui.create_stats.smart_home.wire.feeds_none").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("gui.create_stats.smart_home.wire.feeds", fed.size(), FeRates.perTick(draw))
                            .withStyle(ChatFormatting.AQUA));
            final boolean focused = wireFocus != null && wireFocus[0] == w.x() && wireFocus[1] == w.y() && wireFocus[2] == w.z();
            lines.add(Component.translatable(focused ? "gui.create_stats.smart_home.wire.click_clear"
                    : "gui.create_stats.smart_home.wire.click").withStyle(ChatFormatting.DARK_GRAY));
            tooltip = lines;
            return;
        }
    }

    @Nullable
    private int[] wireFocus;

    private List<SmartHomeSyncPacket.Row> fedRows(Set<Long> keys) {
        final List<SmartHomeSyncPacket.Row> out = new ArrayList<>();
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            for (BlockPos p : r.members()) {
                if (WireGraph.touches(keys, p.getX(), p.getY(), p.getZ())) {
                    out.add(r);
                    break;
                }
            }
        }
        return out;
    }

    @Nullable
    private WireGraph.Wire wireAt(int bx, int bz) {
        if (data == null || view != View.POWER) {
            return null;
        }
        final int low = storeyY() - 2;
        final int high = data.storeys().size() > storey + 1 ? data.storeys().get(storey + 1) - 2 : storeyY() + 8;
        for (WireGraph.Wire w : wireGraph().band(low, high)) {
            if (w.x() == bx && w.z() == bz) {
                return w;
            }
        }
        return null;
    }

    private WireGraph wireGraph() {
        if (graph == null || graphSource == null || (graphSource != wiring && !graphSource.equals(wiring))) {
            final List<WireGraph.Wire> wires = new ArrayList<>(wiring.size());
            for (SmartHomeSyncPacket.Wire w : wiring) {
                wires.add(new WireGraph.Wire(w.pos().getX(), w.pos().getY(), w.pos().getZ(), w.flow()));
            }
            graph = new WireGraph(wires);
            graphSource = wiring;
        }
        return graph;
    }

    private Map<Integer, Integer> drawByRoom() {
        final Map<Integer, Integer> out = new HashMap<>();
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            out.merge(r.roomId(), Math.max(0, r.drawFe()), FeRates::plus);
        }
        return out;
    }

    private void drawSpawnMarks(GuiGraphics g, HomeLayout l) {
        final int s = fit.scale();
        final Set<Long> dark = new HashSet<>();
        for (int z = plan.firstZ(); z < plan.firstZ() + plan.rows(); z++) {
            for (int x = plan.firstX(); x < plan.firstX() + plan.cols(); x++) {
                if (plan.spawnable(x, z)) {
                    dark.add(RoomFill.pack(x, z));
                }
            }
        }
        darkCells = dark;

        if (plan.generation() != spotGeneration || storey != spotStorey) {
            spotGeneration = plan.generation();
            spotStorey = storey;
            lampSpot = LampSpot.best(dark, this::standsOnFloor, this::passesLight, LampSpot.REACH);
        }
        if (dark.isEmpty()) {
            return;
        }
        contour(g, dark, 0xFFB03030, 0);
        for (long c : dark) {
            final int px = l.planX + fit.px(RoomFill.unpackX(c));
            final int pz = l.planY + fit.pz(RoomFill.unpackZ(c));
            if (s >= 6) {
                HomeKit.spawnCross(g, px + (s - 5) / 2, pz + (s - 5) / 2);
            } else {
                g.fill(px + s / 2 - (s >= 3 ? 1 : 0), pz + s / 2 - (s >= 3 ? 1 : 0), px + s / 2 + 1, pz + s / 2 + 1,
                        0xFFFF5555);
            }
        }
        if (lampSpot != null && s >= 6 && fit.visible(lampSpot.x(), lampSpot.z())) {
            HomeKit.lampSpot(g, cx(lampSpot.x()), cz(lampSpot.z()));
        }
    }

    private Set<Long> darkCells = Set.of();
    @Nullable
    private LampSpot.Spot lampSpot;
    private int spotGeneration = -1;
    private int spotStorey = -1;

    private boolean standsOnFloor(long c) {
        final byte k = plan.kind(RoomFill.unpackX(c), RoomFill.unpackZ(c));
        return k == PlanGrid.FLOOR || k == PlanGrid.GROUND;
    }

    private boolean passesLight(long c) {
        final int x = RoomFill.unpackX(c);
        final int z = RoomFill.unpackZ(c);
        final byte k = plan.kind(x, z);
        if (PlanGrid.stopsLight(k)) {
            return false;
        }
        return k != PlanGrid.DOOR || plan.has(x, z, PlanGrid.F_OPEN);
    }

    private void drawScenePreview(GuiGraphics g, HomeLayout l) {
        final SmartHomeSyncPacket.Scene scene = scene(previewScene);
        if (scene == null) {
            return;
        }
        final Map<Long, SceneDiff.State> now = new LinkedHashMap<>();
        final Map<Long, SmartHomeSyncPacket.Row> byAnchor = new HashMap<>();
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            now.put(r.anchor().asLong(), stateOf(r));
            byAnchor.put(r.anchor().asLong(), r);
        }
        for (long key : SceneDiff.changes(now, scene.states())) {
            final SmartHomeSyncPacket.Row r = byAnchor.get(key);
            if (r != null && onStorey(r.floorY())) {
                outlineGroup(g, r.members(), 0xFFFFFF55);
            }
        }
    }

    private static SceneDiff.State stateOf(SmartHomeSyncPacket.Row r) {
        return new SceneDiff.State(r.state() != HomeSegment.State.OFF.ordinal(), r.modes().isEmpty() ? -1 : r.mode(),
                r.colour());
    }

    private void drawFoot(GuiGraphics g, HomeLayout l, int mx, int my) {
        final int y = l.footY;
        final int bh = HomeLayout.BUTTON_H;
        final int storeys = data == null ? 1 : Math.max(1, data.storeys().size());
        int x = l.mapX;

        if (storeys > 1) {
            buttonHit(g, Component.literal("<"), x, y, 14, bh, mx, my, storey > 0, Kind.FLOOR_PREV, null, 0);
            final Component name = storeyName();
            g.drawString(font, name, x + 18, y + 3, HomeKit.TITLE, false);
            x += 22 + font.width(name);
            buttonHit(g, Component.literal(">"), x, y, 14, bh, mx, my, storey < storeys - 1, Kind.FLOOR_NEXT, null, 0);
            x += 18;
        }

        int rx = l.mapX + l.mapSize;
        final View[] views = View.values();
        for (int i = views.length - 1; i >= 0; i--) {
            final Component label = Component.translatable("gui.create_stats.smart_home.view." + key(views[i]));
            final int w = font.width(label) + 12;
            rx -= w;
            buttonHit(g, label, rx, y, w, bh, mx, my, views[i] != view, Kind.VIEW, views[i], 0);
            if (mx >= rx && mx < rx + w && my >= y && my < y + bh) {
                tooltip = List.of(label, Component.translatable("gui.create_stats.smart_home.view." + key(views[i])
                        + ".hint").withStyle(ChatFormatting.GRAY));
            }
            rx -= 2;
        }

        if (zoom != 0 || panX != 0 || panZ != 0) {
            final Component fitLabel = Component.translatable("gui.create_stats.smart_home.fit");
            final int w = font.width(fitLabel) + 10;
            if (x + w <= rx) {
                buttonHit(g, fitLabel, x, y, w, bh, mx, my, true, Kind.FIT, null, 0);
            }
        }
    }

    private static String key(View v) {
        return v.name().toLowerCase(java.util.Locale.ROOT);
    }

    private Component storeyName() {
        final int shown = Math.max(0, storey);
        final boolean mine = data != null && !data.storeys().isEmpty() && minecraft != null && minecraft.player != null
                && FloorBands.storeyOf(data.storeys(), HomeSegment.floorUnder(minecraft.level, minecraft.player.blockPosition()))
                        == shown;
        return Component.translatable(mine ? "gui.create_stats.smart_home.floor.n_you" : "gui.create_stats.smart_home.floor.n",
                shown + 1);
    }

    private void drawSide(GuiGraphics g, HomeLayout l, int mx, int my) {
        final int x = l.sideX;
        final int w = l.sideW;
        int y = l.sideY;
        final int bh = HomeLayout.BUTTON_H;

        boolean anyLamp = false;
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            anyLamp |= r.category() == HomeAppliance.Category.LIGHTING.ordinal();
        }
        final Component lightsOn = Component.translatable("gui.create_stats.smart_home.lights_on");
        final Component lightsOff = Component.translatable("gui.create_stats.smart_home.lights_off");
        final int onW = font.width(lightsOn) + 12;
        final int offW = font.width(lightsOff) + 12;
        buttonHit(g, lightsOn, x, y, onW, bh, mx, my, anyLamp, Kind.ALL_ON, null, 0);
        buttonHit(g, lightsOff, x + onW + 3, y, offW, bh, mx, my, anyLamp, Kind.ALL_OFF, null, 0);
        if (mx >= x && mx < x + onW + 3 + offW && my >= y && my < y + bh) {
            tooltip = List.of(Component.translatable("gui.create_stats.smart_home.lights.hint").withStyle(ChatFormatting.GRAY));
        }
        int total = 0;
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            total = FeRates.plus(total, Math.max(0, r.drawFe()));
        }
        final String fe = FeRates.perTick(total);
        g.drawString(font, fe, x + w - font.width(fe), y + 3, HomeKit.TITLE, false);
        y += bh + 4;
        if (view == View.POWER) {
            y = drawPowerStrip(g, x, y, w, mx, my) + 3;
        } else if (view == View.LIGHT) {
            y = drawLightStrip(g, x, y, w, mx, my) + 3;
        }
        drawList(g, l, x, y, w, mx, my);
    }

    private void drawList(GuiGraphics g, HomeLayout l, int x, int top, int w, int mx, int my) {
        final int bottom = l.sideBottom;
        final int lw = w - HomeLayout.SCROLLER_W + 1;
        HomeKit.well(g, x, top, lw, bottom - top);
        g.enableScissor(x + 1, top + 1, x + lw - 1, bottom - 1);
        listScrollShown += (listScroll - listScrollShown) * 0.35f;
        if (Math.abs(listScroll - listScrollShown) < 0.5f) {
            listScrollShown = listScroll;
        }
        final int shown = Math.round(listScrollShown);
        final boolean inFold = my >= top && my < bottom && mx >= x && mx < x + lw;
        int yy = top + 2 - shown;
        final List<SmartHomeSyncPacket.Row> rows = topRows();
        if (rows.isEmpty() && (data == null || data.unlisted().isEmpty())) {
            final Component empty = Component.translatable(data != null && !data.awake()
                    ? "gui.create_stats.smart_home.empty_dark" : "gui.create_stats.smart_home.empty");
            for (var line : font.split(empty, lw - 10)) {
                g.drawString(font, line, x + 5, yy + 3, 0xFFE8E8E8, true);
                yy += 10;
            }
        }
        final Map<String, Integer> names = new HashMap<>();
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            names.merge(r.name().getString(), 1, Integer::sum);
        }
        for (HomeGroups.Group group : HomeGroups.of(rows, roomOrder(), view == View.POWER)) {
            final boolean summary = group.kind() == HomeGroups.Kind.NOW;
            if (scrollToGroup == group.key()) {
                listScroll = yy + shown - top - 2;
                scrollToGroup = Long.MIN_VALUE;
            }
            yy = drawGroupHeader(g, x, yy, lw, group, mx, my, inFold, top, bottom);
            if (group.kind() == HomeGroups.Kind.ROOM && group.id() == selectedRoom) {
                yy = drawRoomEditor(g, x, yy, lw, group.id(), mx, my, inFold, top, bottom);
            }
            for (SmartHomeSyncPacket.Row r : group.rows()) {
                if (!summary && scrollToRow != null && scrollToRow.equals(r.anchor())) {
                    listScroll = yy + shown - top - 22;
                    scrollToRow = null;
                }
                yy = drawRow(g, x, yy, lw, r, false, summary, names, mx, my, inFold, top, bottom);
                if (summary) {
                    continue;
                }
                for (SmartHomeSyncPacket.Row c : r.plugged()) {
                    if (scrollToRow != null && scrollToRow.equals(c.anchor())) {
                        listScroll = yy + shown - top - 22;
                        scrollToRow = null;
                    }
                    yy = drawRow(g, x, yy, lw, c, true, false, names, mx, my, inFold, top, bottom);
                }
            }
            yy += 3;
        }
        if (data != null && !data.unlisted().isEmpty()) {
            yy = drawUnlisted(g, x, yy, lw, mx, my, inFold, top, bottom);
        }
        if (data != null && data.dropped() > 0) {
            final Component note = Component.translatable("gui.create_stats.smart_home.list.dropped", data.dropped());
            if (yy + 20 > top && yy < bottom) {
                for (var line : font.split(note, lw - 10)) {
                    g.drawString(font, line, x + 5, yy + 3, 0xFFD8D8D8, true);
                    yy += 10;
                }
            } else {
                yy += 10 * font.split(note, lw - 10).size();
            }
            yy += 3;
        }
        g.disableScissor();
        listContent = yy + shown - top;
        final int span = bottom - top;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, listContent - span + 2)));
        HomeKit.well(g, x + lw - 1, top, HomeLayout.SCROLLER_W, span);
        if (listContent > span) {
            final int knob = Math.max(12, span * span / Math.max(1, listContent));
            final int travel = span - 2 - knob;
            final int ky = top + 1 + (int) ((long) travel * shown / Math.max(1, listContent - span + 2));
            g.blitSprite(HomeKit.SCROLLER, x + lw, ky, 5, knob);

            hits.add(new Hit(x + lw - 1, top, HomeLayout.SCROLLER_W, span, Kind.SCROLLER, null, knob));
        }
        listTop = top;
        listSpan = span;
        hits.add(new Hit(x, top, w, span, Kind.LIST, null, 0));
    }

    private int listTop;
    private int listSpan;
    private boolean scrollerHeld;

    private void dragScroller(double my, int knob) {
        final int travel = Math.max(1, listSpan - 2 - knob);
        final double frac = Math.max(0, Math.min(1, (my - listTop - 1 - knob / 2.0) / travel));
        listScroll = (int) Math.round(frac * Math.max(0, listContent - listSpan + 2));
        listScrollShown = listScroll;
    }

    private List<Integer> roomOrder() {
        if (config == null || config.rooms().isEmpty()) {
            return List.of();
        }
        final int here = panelRoom();
        final List<SmartHomeSyncPacket.Room> rooms = new ArrayList<>(config.rooms());
        rooms.sort(Comparator.<SmartHomeSyncPacket.Room>comparingInt(r -> r.id() == here ? 0 : 1)
                .thenComparing(SmartHomeSyncPacket.Room::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(SmartHomeSyncPacket.Room::id));
        final List<Integer> out = new ArrayList<>(rooms.size());
        for (SmartHomeSyncPacket.Room r : rooms) {
            out.add(r.id());
        }
        return out;
    }

    private int panelRoom() {
        if (config == null || data == null) {
            return -1;
        }
        final BlockPos p = panel.getBlockPos();
        for (SmartHomeSyncPacket.Room r : config.rooms()) {
            if (!onStoreyOf(r.floorY(), data.panelStorey())) {
                continue;
            }
            final Set<Long> cells = cellSet(r);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (cells.contains(RoomFill.pack(p.getX() + d.getStepX(), p.getZ() + d.getStepZ()))
                        || cells.contains(RoomFill.pack(p.getX(), p.getZ()))) {
                    return r.id();
                }
            }
        }
        return -1;
    }

    private boolean onStoreyOf(int floorY, int storeyIndex) {
        if (data == null || data.storeys().isEmpty()) {
            return true;
        }
        return FloorBands.storeyOf(data.storeys(), floorY) == storeyIndex;
    }

    private int drawGroupHeader(GuiGraphics g, int x, int y, int w, HomeGroups.Group group, int mx, int my,
                                boolean inFold, int top, int bottom) {
        final int h = 18;
        if (y + h <= top || y >= bottom) {
            return y + h + 1;
        }
        final SmartHomeSyncPacket.Room room = group.kind() == HomeGroups.Kind.ROOM ? room(group.id()) : null;
        final boolean picked = room != null && room.id() == selectedRoom;
        g.fill(x + 1, y - 1, x + w - 1, y + h - 1, picked ? HomeKit.BAND_PICKED : HomeKit.BAND);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, HomeKit.BAND_EDGE);
        final ItemStack icon;
        final String name;
        switch (group.kind()) {
            case ROOM -> {
                icon = room == null ? new ItemStack(Items.LANTERN) : iconStack(room.icon());
                name = room == null ? "" : room.name();
            }
            case KIND -> {
                icon = kindIcon(HomeAppliance.Category.byOrdinal(group.id()));
                name = HomeAppliance.Category.byOrdinal(group.id()).label().getString();
            }
            case NOW -> {
                icon = new ItemStack(Items.CLOCK);
                name = Component.translatable("gui.create_stats.smart_home.now_group").getString();
            }
            default -> {
                icon = new ItemStack(Items.COMPASS);
                name = Component.translatable("gui.create_stats.smart_home.elsewhere").getString();
            }
        }
        g.renderItem(icon, x + 3, y);
        final List<SmartHomeSyncPacket.Row> members = group.kind() == HomeGroups.Kind.NOW ? group.rows()
                : flatten(group.rows());
        int on = 0;
        int all = 0;

        final boolean lampsOnly = group.kind() == HomeGroups.Kind.ROOM || group.kind() == HomeGroups.Kind.ELSEWHERE;
        boolean anyToSwitch = false;
        boolean anySwitchedOn = false;
        for (SmartHomeSyncPacket.Row r : members) {
            all++;
            on += isOn(r) ? 1 : 0;
            if (!lampsOnly || r.category() == HomeAppliance.Category.LIGHTING.ordinal()) {
                anyToSwitch = true;
                anySwitchedOn |= switchedOn(r);
            }
        }
        final boolean hasSwitch = group.kind() != HomeGroups.Kind.NOW && anyToSwitch;
        final int switchX = hasSwitch ? x + w - 21 : x + w - 3;
        final String count = group.kind() == HomeGroups.Kind.NOW
                ? Component.translatable("gui.create_stats.smart_home.now_group.count", all).getString()
                : Component.translatable("gui.create_stats.smart_home.count_on", on, all).getString();
        final int nameMax = switchX - 6 - (x + 22) - font.width(count) - 6;
        final String shownName = HomeKit.trim(font, name, Math.max(20, nameMax));
        g.drawString(font, shownName, x + 22, y + 5, 0xFFFFFFFF, true);
        g.drawString(font, count, x + 22 + font.width(shownName) + 6, y + 5, 0xFFD8D8D8, false);
        final boolean switchHot = hasSwitch && inFold && mx >= switchX && mx < switchX + 18 && my >= y && my < y + 18;
        if (hasSwitch) {
            HomeKit.lampSwitch(g, switchX, y - 1, anySwitchedOn, switchHot);
        }
        if (inFold) {
            if (hasSwitch) {
                hits.add(new Hit(switchX, y - 1, 18, 18, Kind.GROUP_SWITCH, group, anySwitchedOn ? 1 : 0));
            }
            if (group.kind() == HomeGroups.Kind.ROOM) {
                hits.add(new Hit(x, y - 1, switchX - x, h, Kind.GROUP_PICK, group.id(), 0));
            }
        }
        if (switchHot) {
            tooltip = List.of(Component.literal(name), Component.translatable(lampsOnly
                    ? (anySwitchedOn ? "gui.create_stats.smart_home.group.lamps_off" : "gui.create_stats.smart_home.group.lamps_on")
                    : (anySwitchedOn ? "gui.create_stats.smart_home.group.off" : "gui.create_stats.smart_home.group.on"))
                    .withStyle(ChatFormatting.GRAY));
        } else if (inFold && room != null && mx >= x && mx < switchX && my >= y && my < y + h) {
            tooltip = List.of(Component.literal(name), Component.translatable(picked
                    ? "gui.create_stats.smart_home.room.unpick" : "gui.create_stats.smart_home.room.settings")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return y + h + 1;
    }

    private static ItemStack kindIcon(HomeAppliance.Category kind) {
        return new ItemStack(switch (kind) {
            case LIGHTING -> Items.REDSTONE_LAMP;
            case KITCHEN -> Items.CAKE;
            case LAUNDRY -> Items.WATER_BUCKET;
            case SOCKETS -> Items.REDSTONE;
            case OTHER -> Items.COMPARATOR;
        });
    }

    private int drawRoomEditor(GuiGraphics g, int x, int y, int w, int roomId, int mx, int my, boolean inFold,
                               int top, int bottom) {
        final int h = 40;
        final SmartHomeSyncPacket.Room room = room(roomId);
        if (room == null) {
            return y;
        }
        if (y + h <= top || y >= bottom) {
            return y + h + 1;
        }
        g.fill(x + 1, y - 1, x + w - 1, y + h, HomeKit.SELECTED);
        HomeKit.slot(g, x + 3, y);
        g.renderItem(iconStack(room.icon()), x + 4, y + 1);
        if (inFold) {
            hits.add(new Hit(x + 3, y, 18, 18, Kind.ROOM_ICON, room.id(), 0));
        }
        if (inFold && mx >= x + 3 && mx < x + 21 && my >= y && my < y + 18) {
            HomeKit.slotHighlight(g, x + 3, y);
            tooltip = List.of(Component.translatable("gui.create_stats.smart_home.rooms.icon"),
                    Component.translatable("gui.create_stats.smart_home.rooms.icon.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (renameKind == SmartHomeRenamePacket.ROOM && renameId == room.id()) {
            placeBox(x + 24, y + 1, w - 28, 16);
            nameBox.render(g, mx, my, 0f);
        } else {
            buttonHit(g, Component.literal(room.name()), x + 24, y + 2, w - 28, HomeLayout.BUTTON_H, inFold ? mx : -1, my,
                    inFold, Kind.ROOM_RENAME, room.id(), 0);
            if (inFold && mx >= x + 24 && mx < x + w - 4 && my >= y + 2 && my < y + 16) {
                tooltip = List.of(Component.translatable("gui.create_stats.smart_home.rename.hint")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        final Component follow = Component.translatable("gui.create_stats.smart_home.rooms.follow");
        final boolean hot = inFold && mx >= x + 3 && mx < x + 24 + font.width(follow) && my >= y + 21 && my < y + 38;
        HomeKit.checkbox(g, font, follow, x + 3, y + 21, room.follow(), hot, 0xFFFFFFFF);
        if (inFold) {
            hits.add(new Hit(x + 3, y + 21, 21 + font.width(follow), 17, Kind.ROOM_FOLLOW, room.id(), room.follow() ? 1 : 0));
        }
        if (hot) {
            tooltip = List.of(follow, Component.translatable("gui.create_stats.smart_home.rooms.follow.hint")
                    .withStyle(ChatFormatting.GRAY));
        }
        return y + h + 1;
    }

    private int drawRow(GuiGraphics g, int x, int y, int w, SmartHomeSyncPacket.Row r, boolean child, boolean summary,
                        Map<String, Integer> names, int mx, int my, boolean inFold, int top, int bottom) {
        final boolean open = !summary && r.anchor().equals(openRow);
        final int indent = child ? 9 : 0;
        final int h = open ? rowControlsHeight(r) + 21 : 20;
        final boolean visible = y + h > top && y < bottom;
        if (!visible) {
            return y + h + 1;
        }
        if (open) {
            g.fill(x + 1, y - 1, x + w - 1, y + h - 1, HomeKit.SELECTED);
        } else if (mapHover != null && r.anchor().equals(mapHover.anchor())) {
            g.fill(x + 1, y - 1, x + w - 1, y + h - 1, HomeKit.BAND_PICKED);
        }
        if (child) {
            g.fill(x + 6, y - 2, x + 7, y + 9, HomeKit.SHADOW);
            g.fill(x + 6, y + 8, x + 2 + indent, y + 9, HomeKit.SHADOW);
        }
        final int sx = x + 3 + indent;
        final boolean slotHot = inFold && mx >= sx && mx < sx + 18 && my >= y && my < y + 18;
        drawSlot(g, sx, y, r, slotHot);
        if (inFold) {
            hits.add(new Hit(x, y, w, 20, Kind.ROW_OPEN, r.anchor(), summary ? 1 : 0));
            hits.add(new Hit(sx, y, 18, 18, Kind.ROW_TOGGLE, r.anchor(), switchedOn(r) ? 1 : 0));
        }
        final int tx = sx + 21;
        final String drawText = String.valueOf(Math.max(0, r.drawFe()));
        final int right = x + w - 5;
        final int nameMax = right - font.width(drawText) - 4 - tx;
        String name = r.name().getString() + (r.members().size() > 1 ? "  x" + r.members().size() : "");
        name = HomeKit.trim(font, name, nameMax);
        g.drawString(font, name, tx, y + 1, 0xFFFFFFFF, true);
        if (names.getOrDefault(r.name().getString(), 0) > 1) {
            final String where = HomeSegment.bearing(panel.getBlockPos(), r.anchor()).getString();
            final int wx = tx + font.width(name) + 4;
            if (wx + font.width(where) <= right - font.width(drawText) - 4) {
                g.drawString(font, where, wx, y + 1, 0xFFD0D0D0, false);
            }
        }
        g.drawString(font, drawText, right - font.width(drawText), y + 1, r.drawFe() > 0 ? 0xFFFFFFFF : 0xFFB8B8B8,
                true);
        final Component status = statusLine(r);
        HomeKit.dot(g, tx, y + 12, statusDot(r));
        g.drawString(font, HomeKit.trim(font, status.getString(), right - (tx + 6)), tx + 6, y + 10, statusInk(r), false);
        if (inFold && mx >= x && mx < x + w && my >= y && my < y + 20) {
            identify = r.members();
            listHover = r;
            tooltip = slotHot ? rowTooltip(r, false) : List.of(r.name(), Component.translatable(open
                    ? "gui.create_stats.smart_home.row.close" : "gui.create_stats.smart_home.row.open")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (open) {
            drawRowControls(g, tx, y + 21, right - tx, r, mx, my, inFold);
        }
        return y + h + 1;
    }

    private int rowControlsHeight(SmartHomeSyncPacket.Row r) {
        int h = 0;
        if (r.modes().size() > 1) {
            h += 17;
        }
        if (r.colour() >= 0) {
            h += 28;
        }
        return h + 17;
    }

    private void drawRowControls(GuiGraphics g, int x, int y, int w, SmartHomeSyncPacket.Row r, int mx, int my,
                                 boolean inFold) {
        if (r.modes().size() > 1) {
            final int n = r.modes().size();
            final Component label = Component.translatable(modeLabelKey(r), r.modes().get(Math.max(0, Math.min(n - 1,
                    r.mode()))));
            final boolean hot = inFold && mx >= x && mx < x + w && my >= y && my < y + 14;
            HomeKit.slider(g, font, label, x, y, w, 14, n <= 1 ? 0f : r.mode() / (float) (n - 1), hot);
            if (inFold) {
                hits.add(new Hit(x, y, w, 14, Kind.MODE, r.anchor(), n));
            }
            y += 17;
        }
        if (r.colour() >= 0) {
            final Component colourLabel = Component.translatable("gui.create_stats.smart_home.colour");
            g.drawString(font, colourLabel, x, y + 4, HomeKit.TITLE, false);
            final int dx = x + Math.max(34, font.width(colourLabel) + 4);
            for (int i = 0; i < 16; i++) {
                final DyeColor dye = DyeColor.byId(i);
                final int sx = dx + (i % 8) * 16;
                final int sy = y + (i / 8) * 13;
                g.renderItem(new ItemStack(net.minecraft.world.item.DyeItem.byColor(dye)), sx, sy - 2);
                if (i == r.colour()) {
                    HomeKit.outline(g, sx - 1, sy - 3, 18, 14, 0xFFFFFFFF);
                }
                if (inFold) {
                    hits.add(new Hit(sx, sy - 2, 16, 13, Kind.COLOUR, r.anchor(), i));
                }
                if (inFold && mx >= sx && mx < sx + 16 && my >= sy - 2 && my < sy + 11) {
                    tooltip = List.of(Component.translatable("color.minecraft." + dye.getName()));
                }
            }
            y += 28;
        }
        final int bw = (w - 3) / 2;
        buttonHit(g, Component.translatable("gui.create_stats.smart_home.rename"), x, y, bw, 14, inFold ? mx : -1, my,
                inFold, Kind.ROW_RENAME, r.anchor(), 0);
        buttonHit(g, Component.translatable("gui.create_stats.smart_home.unlink"), x + bw + 3, y, w - bw - 3, 14,
                inFold ? mx : -1, my, inFold && r.manual(), Kind.UNLINK, r.anchor(), 0);
        if (inFold && !r.manual() && mx >= x + bw + 3 && mx < x + w && my >= y && my < y + 14) {
            tooltip = List.of(Component.translatable("gui.create_stats.smart_home.unlink.found")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (renameKind == SmartHomeRenamePacket.ROW && r.anchor().equals(renameRow)) {
            placeBox(x, y - 1, w, 16);
            nameBox.render(g, mx, my, 0f);
        }
    }

    private static String modeLabelKey(SmartHomeSyncPacket.Row r) {
        if (r.category() == HomeAppliance.Category.LIGHTING.ordinal()) {
            return "gui.create_stats.smart_home.setting.brightness";
        }
        return "gui.create_stats.smart_home.setting.generic";
    }

    private int drawUnlisted(GuiGraphics g, int x, int y, int w, int mx, int my, boolean inFold, int top, int bottom) {
        final List<SmartHomeSyncPacket.Hint> hints = data.unlisted();
        if (y + 18 > top && y < bottom) {
            g.fill(x + 1, y - 1, x + w - 1, y + 17, HomeKit.BAND);
            g.fill(x + 1, y + 17, x + w - 1, y + 18, HomeKit.BAND_EDGE);
            g.drawString(font, unlistedOpen ? "v" : ">", x + 6, y + 5, 0xFFE0E0E0, true);
            final String label = Component.translatable("gui.create_stats.smart_home.unlisted.group", hints.size())
                    .getString();
            g.drawString(font, HomeKit.trim(font, label, w - 24), x + 16, y + 5, 0xFFE0E0E0, true);
            if (inFold) {
                hits.add(new Hit(x, y - 1, w, 18, Kind.UNLISTED_FOLD, null, 0));
            }
            if (inFold && mx >= x && mx < x + w && my >= y - 1 && my < y + 17) {
                tooltip = List.of(Component.literal(label), Component.translatable("gui.create_stats.smart_home.unlisted.tip")
                        .withStyle(ChatFormatting.GRAY), Component.translatable("gui.create_stats.smart_home.link_hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        y += 19;
        if (!unlistedOpen) {
            return y;
        }
        for (SmartHomeSyncPacket.Hint h : hints) {
            if (y + 20 > top && y < bottom) {
                HomeKit.slot(g, x + 3, y);
                final Item item = minecraft != null && minecraft.level != null
                        ? minecraft.level.getBlockState(h.pos()).getBlock().asItem() : Items.AIR;
                if (item != Items.AIR) {
                    g.renderItem(new ItemStack(item), x + 4, y + 1);
                    g.fill(x + 4, y + 1, x + 20, y + 17, 0x998B8B8B);
                }
                g.drawString(font, HomeKit.trim(font, h.name().getString(), w - 34), x + 24, y + 1, 0xFFE0E0E0, true);
                g.drawString(font, HomeSegment.bearing(panel.getBlockPos(), h.pos()), x + 24, y + 10, 0xFF4A4A4A, false);
                if (inFold && mx >= x && mx < x + w && my >= y && my < y + 20) {
                    identify = List.of(h.pos());
                    tooltip = List.of(h.name(), Component.translatable("gui.create_stats.smart_home.unlisted.tip")
                            .withStyle(ChatFormatting.GRAY), Component.translatable("gui.create_stats.smart_home.link_hint")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            y += 21;
        }
        return y;
    }

    private void drawSlot(GuiGraphics g, int x, int y, SmartHomeSyncPacket.Row r, boolean hot) {
        HomeKit.slot(g, x, y);
        final HomeSegment.State s = state(r);
        if (s == HomeSegment.State.ON) {
            HomeKit.glow(g, x, y);
        }
        final ItemStack stack = rowStack(r);
        g.renderItem(stack, x + 1, y + 1);
        if (r.members().size() > 1) {
            g.renderItemDecorations(font, stack, x + 1, y + 1, String.valueOf(r.members().size()));
        }
        if (s == HomeSegment.State.OFF || s == HomeSegment.State.MISSING) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x998B8B8B);
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        if (r.running() && r.progress() >= 0) {
            g.fill(x + 3, y + 14, x + 16, y + 16, 0xFF000000);
            g.fill(x + 3, y + 14, x + 3 + Math.round(13 * r.progress()), y + 15, HomeKit.FILL_BAR);
        }
        if (s == HomeSegment.State.SHED || s == HomeSegment.State.NO_POWER) {
            g.fill(x + 1, y + 1, x + 5, y + 2, 0xFFFFAA00);
            g.fill(x + 1, y + 2, x + 4, y + 3, 0xFFFFAA00);
            g.fill(x + 1, y + 3, x + 3, y + 4, 0xFFFFAA00);
            g.fill(x + 1, y + 4, x + 2, y + 5, 0xFFFFAA00);
        }
        if (r.manual()) {
            g.fill(x + 13, y + 1, x + 17, y + 2, 0xFF55FFFF);
            g.fill(x + 16, y + 1, x + 17, y + 5, 0xFF55FFFF);
        }
        if (hot) {
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
        }
        g.pose().popPose();
    }

    private ItemStack rowStack(SmartHomeSyncPacket.Row r) {
        if (state(r) == HomeSegment.State.MISSING) {
            return new ItemStack(com.createstats.registry.ModItems.SWITCH_LINK_CARD.get());
        }
        if (minecraft != null && minecraft.level != null) {
            final Item item = minecraft.level.getBlockState(r.anchor()).getBlock().asItem();
            if (item != Items.AIR) {
                return new ItemStack(item);
            }
        }
        return new ItemStack(Items.REDSTONE_LAMP);
    }

    private static ItemStack iconStack(String id) {
        final ResourceLocation rl = ResourceLocation.tryParse(id);
        final Item item = rl == null ? Items.PAPER : BuiltInRegistries.ITEM.get(rl);
        return new ItemStack(item == Items.AIR ? Items.PAPER : item);
    }

    private static HomeSegment.State state(SmartHomeSyncPacket.Row r) {
        return HomeSegment.State.values()[Math.max(0, Math.min(HomeSegment.State.values().length - 1, r.state()))];
    }

    private static boolean isOn(SmartHomeSyncPacket.Row r) {
        return r.state() == HomeSegment.State.ON.ordinal();
    }

    private static boolean switchedOn(SmartHomeSyncPacket.Row r) {
        final HomeSegment.State s = state(r);
        return s != HomeSegment.State.OFF && s != HomeSegment.State.MISSING;
    }

    private Component statusLine(SmartHomeSyncPacket.Row r) {
        final HomeSegment.State s = state(r);
        if (s == HomeSegment.State.ON && r.status() != null) {
            return r.status();
        }
        return s.label();
    }

    private static int statusDot(SmartHomeSyncPacket.Row r) {
        return switch (state(r)) {
            case ON -> 0xFF55FF55;
            case OFF, MISSING -> 0xFF777777;
            case NO_POWER, SHED -> 0xFFFFAA00;
        };
    }

    private static int statusInk(SmartHomeSyncPacket.Row r) {
        return switch (state(r)) {
            case ON -> HomeKit.WELL_OK;
            case OFF, MISSING -> HomeKit.WELL_OFF;
            case NO_POWER, SHED -> HomeKit.WELL_WARN;
        };
    }

    private List<Component> rowTooltip(SmartHomeSyncPacket.Row r, boolean onMap) {
        final List<Component> out = new ArrayList<>();
        out.add(r.members().size() > 1
                ? Component.empty().append(r.name()).append(Component.literal("  x" + r.members().size()))
                : r.name());
        out.add(statusLine(r).copy().withStyle(switch (state(r)) {
            case ON -> ChatFormatting.GREEN;
            case OFF, MISSING -> ChatFormatting.GRAY;
            case NO_POWER, SHED -> ChatFormatting.GOLD;
        }));
        if (r.modes().size() > 1) {
            out.add(Component.translatable(modeLabelKey(r),
                    r.modes().get(Math.max(0, Math.min(r.modes().size() - 1, r.mode())))).withStyle(ChatFormatting.GRAY));
        }
        out.add(Component.translatable("gui.create_stats.smart_home.draws",
                FeRates.perTick(Math.max(0, r.drawFe()))).withStyle(ChatFormatting.GRAY));
        final SmartHomeSyncPacket.Room room = r.roomId() >= 0 ? room(r.roomId()) : null;
        final Component where = Component.translatable("gui.create_stats.smart_home.bearing",
                HomeSegment.bearing(panel.getBlockPos(), r.anchor()));
        out.add((room == null ? where.copy() : Component.literal(room.name() + ", ").append(where))
                .withStyle(ChatFormatting.GRAY));
        if (r.manual()) {
            out.add(Component.translatable("gui.create_stats.smart_home.linked").withStyle(ChatFormatting.AQUA));
        }
        if (state(r) == HomeSegment.State.NO_POWER || state(r) == HomeSegment.State.SHED) {
            out.add(Component.translatable("gui.create_stats.smart_home.hint.starved").withStyle(ChatFormatting.GOLD));
        }
        if (state(r) != HomeSegment.State.MISSING) {
            out.add(Component.translatable(switchedOn(r)
                    ? "gui.create_stats.smart_home.hint.click_off" : "gui.create_stats.smart_home.hint.click_on")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        out.add(Component.translatable("gui.create_stats.smart_home.hint.right_click").withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    private int drawPowerStrip(GuiGraphics g, int x, int y, int w, int mx, int my) {
        final int h = 50;
        HomeKit.well(g, x, y, w, h);
        g.drawString(font, Component.translatable("gui.create_stats.smart_home.today"), x + 4, y + 3, 0xFFFFFFFF, true);
        int total = 0;
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            total = FeRates.plus(total, Math.max(0, r.drawFe()));
        }
        final String now = Component.translatable("gui.create_stats.smart_home.now", FeRates.perTick(total)).getString();
        HomeKit.xpText(g, font, now, x + w - 5 - font.width(now), y + 3);
        if (data != null) {
            final int[] chart = data.dayChart();
            long peak = 1;
            for (int v : chart) {
                peak = Math.max(peak, v);
            }
            final int top = y + 13;
            final int base = y + 34;
            final int pitch = Math.max(2, (w - 8) / Math.max(1, chart.length));
            for (int i = 0; i < chart.length; i++) {
                final int bh = (int) Math.max(chart[i] > 0 ? 1 : 0, Math.round((base - top) * (chart[i] / (double) peak)));
                final int bx = x + 4 + i * pitch;
                final boolean last = i == chart.length - 1;
                if (bh > 0) {
                    g.fill(bx, base - bh, bx + pitch - 2, base, last ? HomeKit.XP_GREEN : 0xFF3F7F10);
                    g.fill(bx, base - bh, bx + pitch - 2, base - bh + 1, HomeKit.XP_GREEN);
                }
                if (mx >= bx && mx < bx + pitch && my >= top && my < base) {
                    final int hour = Math.floorMod(data.hourOfDay() - (chart.length - 1 - i) + 6, 24);
                    tooltip = List.of(Component.translatable("gui.create_stats.smart_home.hour", String.format("%02d:00", hour)),
                            Component.translatable("gui.create_stats.smart_home.hour_fe", FeRates.amount(chart[i]))
                                    .withStyle(ChatFormatting.GRAY),
                            Component.translatable("gui.create_stats.smart_home.hour_note").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        final List<SmartHomeSyncPacket.Row> standby = new ArrayList<>();
        int waste = 0;
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            if (r.standby()) {
                standby.add(r);
                waste = FeRates.plus(waste, Math.max(0, r.drawFe()));
            }
        }
        if (standby.isEmpty()) {
            g.drawString(font, Component.translatable("gui.create_stats.smart_home.standby.none"), x + 4, y + 38,
                    0xFFD8D8D8, false);
        } else {
            final Component line = Component.translatable("gui.create_stats.smart_home.standby.line", standby.size(),
                    FeRates.perTick(waste));

            HomeKit.dot(g, x + 4, y + 40, 0xFFFFAA00);
            g.drawString(font, HomeKit.trim(font, line.getString(), w - 46), x + 10, y + 38, 0xFFFFFFFF, true);
            buttonHit(g, Component.translatable("gui.create_stats.smart_home.standby.off"), x + w - 32, y + 36, 28, 12,
                    mx, my, true, Kind.STANDBY_OFF, null, 0);
            if (mx >= x && mx < x + w - 34 && my >= y + 36 && my < y + 48) {
                final List<Component> tip = new ArrayList<>();
                tip.add(line);
                for (SmartHomeSyncPacket.Row r : standby) {
                    tip.add(Component.empty().append(r.name()).append(": " + FeRates.perTick(Math.max(0, r.drawFe())))
                            .withStyle(ChatFormatting.GRAY));
                }
                tooltip = tip;
                final List<BlockPos> all = new ArrayList<>();
                for (SmartHomeSyncPacket.Row r : standby) {
                    all.addAll(r.members());
                }
                identify = all;
            }
        }
        return y + h;
    }

    private int drawLightStrip(GuiGraphics g, int x, int y, int w, int mx, int my) {
        final int h = 44;
        HomeKit.well(g, x, y, w, h);

        if (minecraft != null && minecraft.level != null) {
            final long dayTime = minecraft.level.getDayTime();
            final Component night = DayClock.isNight(dayTime)
                    ? Component.translatable("gui.create_stats.smart_home.light.night_now")
                    : Component.translatable("gui.create_stats.smart_home.light.night_in",
                            ApplianceStatus.clock(DayClock.untilNight(dayTime)));
            g.drawString(font, HomeKit.trim(font, night.getString(), w - 8), x + 4, y + 31,
                    DayClock.isNight(dayTime) ? 0xFFFF7A7A : 0xFFFFD84A, true);
        }
        final Map<Integer, Integer> spots = new HashMap<>();
        int outside = 0;
        int total = 0;
        for (int z = plan.firstZ(); z < plan.firstZ() + plan.rows(); z++) {
            for (int xx = plan.firstX(); xx < plan.firstX() + plan.cols(); xx++) {
                if (!plan.spawnable(xx, z)) {
                    continue;
                }
                total++;
                final SmartHomeSyncPacket.Room room = roomAt(xx, z);
                if (room == null) {
                    outside++;
                } else {
                    spots.merge(room.id(), 1, Integer::sum);
                }
            }
        }
        if (total == 0) {
            g.drawString(font, Component.translatable("gui.create_stats.smart_home.light.none"), x + 4, y + 3,
                    0xFF80FF80, true);
            g.drawString(font, HomeKit.trim(font, Component.translatable("gui.create_stats.smart_home.light.note_short")
                    .getString(), w - 8), x + 4, y + 15, 0xFFD8D8D8, false);
            return y + h;
        }
        HomeKit.spawnCross(g, x + 4, y + 4);
        g.drawString(font, HomeKit.trim(font, Component.translatable("gui.create_stats.smart_home.light.count", total)
                .getString(), w - 16), x + 12, y + 3, 0xFFFFFFFF, true);

        final List<int[]> order = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : spots.entrySet()) {
            order.add(new int[] {e.getKey(), e.getValue()});
        }
        order.sort(Comparator.<int[]>comparingInt(a -> -a[1]).thenComparingInt(a -> a[0]));
        final StringBuilder where = new StringBuilder();
        final List<Integer> fixable = new ArrayList<>();
        for (int[] a : order) {
            final SmartHomeSyncPacket.Room room = room(a[0]);
            if (room == null) {
                continue;
            }
            if (where.length() > 0) {
                where.append("  ");
            }
            where.append(room.name()).append(": ").append(a[1]);
            if (lampsOff(room.id()) > 0) {
                fixable.add(room.id());
            }
        }
        // "Elsewhere", not "Outside". no rooms meant every lamp was Outside and the fix button never worked

        final boolean anyRoom = config != null && !config.rooms().isEmpty();
        if (outside > 0) {
            if (anyRoom) {
                if (where.length() > 0) {
                    where.append("  ");
                }
                where.append(Component.translatable("gui.create_stats.smart_home.elsewhere").getString()).append(": ")
                        .append(outside);
            }
            if (lampsOff(-1) > 0) {
                fixable.add(-2);
            }
        }
        final String whereText = where.length() > 0 ? where.toString()
                : Component.translatable("gui.create_stats.smart_home.light.note_short").getString();
        final Component fix = Component.translatable("gui.create_stats.smart_home.light.fix");
        final int fw = font.width(fix) + 10;
        g.drawString(font, HomeKit.trim(font, whereText, w - fw - 12), x + 4, y + 17, 0xFFE0E0E0, false);
        buttonHit(g, fix, x + w - fw - 4, y + 15, fw, 14, mx, my, !fixable.isEmpty(), Kind.LIGHT_FIX, fixable, 0);
        if (mx >= x + w - fw - 4 && mx < x + w - 4 && my >= y + 15 && my < y + 29) {
            tooltip = List.of(fix, Component.translatable(fixable.isEmpty() ? "gui.create_stats.smart_home.light.fix.none"
                    : anyRoom ? "gui.create_stats.smart_home.light.fix.hint"
                    : "gui.create_stats.smart_home.light.fix.hint_all").withStyle(ChatFormatting.GRAY));
        }
        return y + h;
    }

    @Nullable
    private SmartHomeSyncPacket.Room roomAt(int x, int z) {
        if (config == null) {
            return null;
        }
        final long key = RoomFill.pack(x, z);
        for (SmartHomeSyncPacket.Room room : config.rooms()) {
            if (onStorey(room.floorY()) && cellSet(room).contains(key)) {
                return room;
            }
        }
        return null;
    }

    private int lampsOff(int roomId) {
        int n = 0;
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            if (r.roomId() == roomId && r.category() == HomeAppliance.Category.LIGHTING.ordinal()
                    && state(r) == HomeSegment.State.OFF) {
                n += r.members().size();
            }
        }
        return n;
    }

    private void drawScenes(GuiGraphics g, HomeLayout l, int mx, int my) {
        boolean anyScene = false;
        for (int i = 0; i < HouseModel.SCENE_SLOTS; i++) {
            anyScene |= scene(i) != null;
        }
        g.drawString(font, Component.translatable("gui.create_stats.smart_home.scenes"), l.scenesX, l.scenesY - 10,
                HomeKit.TITLE, false);
        final Component hint = Component.translatable(anyScene
                ? "gui.create_stats.smart_home.scenes.keys" : "gui.create_stats.smart_home.scenes.empty");
        g.drawString(font, hint, l.scenesX + HomeLayout.HOTBAR_W - font.width(hint), l.scenesY - 10, HomeKit.FACE_SOFT,
                false);
        g.blitSprite(HomeKit.HOTBAR, l.scenesX, l.scenesY, HomeLayout.HOTBAR_W, HomeLayout.HOTBAR_H);

        final Map<Long, SceneDiff.State> now = new LinkedHashMap<>();
        final Map<Long, SmartHomeSyncPacket.Row> byAnchor = new HashMap<>();
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            now.put(r.anchor().asLong(), stateOf(r));
            byAnchor.put(r.anchor().asLong(), r);
        }
        for (int i = 0; i < HouseModel.SCENE_SLOTS; i++) {
            final int sx = l.scenesX + 3 + i * 20;
            final int sy = l.scenesY + 3;
            final SmartHomeSyncPacket.Scene s = scene(i);
            final Set<Long> diff = s == null ? Set.of() : SceneDiff.changes(now, s.states());
            if (s != null && !s.states().isEmpty() && diff.isEmpty()) {
                g.fill(sx, sy, sx + 16, sy + 16, HomeKit.LAMP_EDGE);
                g.fill(sx + 1, sy + 1, sx + 15, sy + 15, HomeKit.LAMP_MID);
                g.fill(sx + 3, sy + 3, sx + 13, sy + 13, HomeKit.LAMP_CORE);
            }
            if (s != null) {
                g.renderItem(iconStack(s.icon()), sx, sy);
                final HomeKit.Badge badge = badgeOf(i);
                if (badge != null) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 250);
                    HomeKit.badge(g, badge, sx + 12, sy - 1);
                    g.pose().popPose();
                }
            }
            hits.add(new Hit(sx - 2, sy - 2, 20, 20, Kind.SCENE, null, i));
            if (mx >= sx - 2 && mx < sx + 18 && my >= sy - 2 && my < sy + 18) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 260);
                g.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
                g.pose().popPose();
                if (s == null) {
                    tooltip = List.of(Component.translatable("gui.create_stats.smart_home.scene.empty"),
                            Component.translatable("gui.create_stats.smart_home.scene.save_hint").withStyle(ChatFormatting.GRAY));
                } else {
                    previewScene = i;
                    final List<Component> tip = new ArrayList<>();
                    tip.add(Component.literal(s.name()));

                    int lampsHeld = 0;
                    int machinesHeld = 0;
                    for (long key : s.states().keySet()) {
                        final SmartHomeSyncPacket.Row held = byAnchor.get(key);
                        if (held == null) {
                            continue;
                        }
                        if (held.category() == HomeAppliance.Category.LIGHTING.ordinal()) {
                            lampsHeld++;
                        } else {
                            machinesHeld++;
                        }
                    }
                    tip.add(Component.translatable("gui.create_stats.smart_home.scene.holds", lampsHeld, machinesHeld)
                            .withStyle(ChatFormatting.GRAY));
                    final int changes = diff.size();
                    int elsewhere = 0;
                    for (long key : diff) {
                        final SmartHomeSyncPacket.Row r = byAnchor.get(key);
                        if (r != null && !onStorey(r.floorY())) {
                            elsewhere++;
                        }
                    }
                    tip.add(changes > 0
                            ? Component.translatable("gui.create_stats.smart_home.scene.changes", changes).withStyle(ChatFormatting.YELLOW)
                            : Component.translatable("gui.create_stats.smart_home.scene.same").withStyle(ChatFormatting.GRAY));
                    if (elsewhere > 0) {
                        tip.add(Component.translatable("gui.create_stats.smart_home.scene.changes.other", elsewhere)
                                .withStyle(ChatFormatting.GRAY));
                    }
                    final HomeKit.Badge badge = badgeOf(i);
                    if (badge != null) {
                        tip.add(Component.translatable("gui.create_stats.smart_home.rule.runs." + badgeKey(badge))
                                .withStyle(ChatFormatting.AQUA));
                    }
                    tip.add(Component.translatable("gui.create_stats.smart_home.scene.recall", i + 1)
                            .withStyle(ChatFormatting.DARK_GRAY));
                    tip.add(Component.translatable("gui.create_stats.smart_home.scene.edit").withStyle(ChatFormatting.DARK_GRAY));
                    tooltip = tip;
                }
            }
        }
    }

    @Nullable
    private HomeKit.Badge badgeOf(int slot) {
        if (config == null) {
            return null;
        }
        if (config.bedtime() == slot) {
            return HomeKit.Badge.BEDTIME;
        }
        if (config.away() == slot) {
            return HomeKit.Badge.AWAY;
        }
        if (config.home() == slot) {
            return HomeKit.Badge.HOME;
        }
        return null;
    }

    private static String badgeKey(HomeKit.Badge b) {
        return switch (b) {
            case BEDTIME -> "bedtime";
            case AWAY -> "away";
            case HOME -> "home";
        };
    }

    private void drawSceneEditor(GuiGraphics g, HomeLayout l, int mx, int my) {
        final SmartHomeSyncPacket.Scene s = scene(editingScene);
        if (s == null) {
            editingScene = -1;
            return;
        }
        final int w = HomeLayout.HOTBAR_W + 14;
        final int h = 192;
        final int x = l.scenesX - 7;
        final int y = Math.max(l.y + 2, l.scenesY - h - 4);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        HomeKit.frame(g, x, y, w, h);
        hits.add(new Hit(x, y, w, h, Kind.EDITOR, null, 0));
        g.drawString(font, Component.translatable("gui.create_stats.smart_home.scene.title", editingScene + 1), x + 7,
                y + 6, HomeKit.TITLE, false);
        HomeKit.slot(g, x + 7, y + 18);
        g.renderItem(iconStack(s.icon()), x + 8, y + 19);
        hits.add(new Hit(x + 7, y + 18, 18, 18, Kind.SCENE_ICON, null, editingScene));
        if (mx >= x + 7 && mx < x + 25 && my >= y + 18 && my < y + 36) {
            HomeKit.slotHighlight(g, x + 7, y + 18);
            tooltip = List.of(Component.translatable("gui.create_stats.smart_home.scene.icon"),
                    Component.translatable("gui.create_stats.smart_home.rooms.icon.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (renameKind == SmartHomeRenamePacket.SCENE && renameId == editingScene) {
            placeBox(x + 29, y + 19, w - 36, 16);
        } else {
            buttonHit(g, Component.literal(s.name()), x + 29, y + 19, w - 36, 16, mx, my, true, Kind.SCENE_NAME, null,
                    editingScene);
        }

        buttonHit(g, Component.translatable("gui.create_stats.smart_home.scene.save_lights"), x + 7, y + 40, w - 14,
                16, mx, my, true, Kind.SCENE_SAVE_LIGHTS, null, editingScene);
        buttonHit(g, Component.translatable("gui.create_stats.smart_home.scene.save_all"), x + 7, y + 59, w - 14, 16,
                mx, my, true, Kind.SCENE_SAVE_ALL, null, editingScene);
        if (mx >= x + 7 && mx < x + w - 7 && my >= y + 59 && my < y + 75) {
            tooltip = List.of(Component.translatable("gui.create_stats.smart_home.scene.save_all.hint")
                    .withStyle(ChatFormatting.GRAY));
        }
        final SmartHomeSyncPacket.Room room = selectedRoom >= 0 ? room(selectedRoom) : null;
        buttonHit(g, room == null ? Component.translatable("gui.create_stats.smart_home.scene.save_room_none")
                        : Component.translatable("gui.create_stats.smart_home.scene.save_room", room.name()),
                x + 7, y + 78, w - 14, 16, mx, my, room != null, Kind.SCENE_SAVE_ROOM, null, editingScene);
        g.drawString(font, Component.translatable("gui.create_stats.smart_home.rule.title"), x + 7, y + 100,
                HomeKit.TITLE, false);
        final HouseModel.Rule[] rules = HouseModel.Rule.values();
        final int[] slots = ruleValues();
        for (int i = 0; i < rules.length; i++) {
            final int ry = y + 110 + i * 18;
            final String k = rules[i].name().toLowerCase(java.util.Locale.ROOT);
            final Component label = Component.translatable("gui.create_stats.smart_home.rule.check." + k);
            final boolean on = slots[i] == editingScene;
            final boolean hot = mx >= x + 7 && mx < x + 28 + font.width(label) && my >= ry && my < ry + 17;
            HomeKit.checkbox(g, font, label, x + 7, ry, on, hot, HomeKit.TITLE);
            hits.add(new Hit(x + 7, ry, 21 + font.width(label), 17, Kind.SCENE_RULE, rules[i], on ? 1 : 0));
            if (hot) {
                final List<Component> tip = new ArrayList<>();
                tip.add(label);
                tip.add(Component.translatable("gui.create_stats.smart_home.rule." + k + ".hint").withStyle(ChatFormatting.GRAY));
                if (slots[i] >= 0 && slots[i] != editingScene) {
                    tip.add(Component.translatable("gui.create_stats.smart_home.rule.moves", ruleName(slots[i], rules[i]))
                            .withStyle(ChatFormatting.YELLOW));
                }
                tip.add(Component.translatable("gui.create_stats.smart_home.rule.powered").withStyle(ChatFormatting.DARK_GRAY));
                tooltip = tip;
            }
        }

        final boolean armed = deleteArmedScene == editingScene && minecraft != null && minecraft.level != null
                && minecraft.level.getGameTime() - deleteArmedAt < DELETE_ARM_TICKS;
        if (!armed) {
            deleteArmedScene = -1;
        }
        buttonHit(g, Component.translatable(armed ? "gui.create_stats.smart_home.scene.delete.sure"
                : "gui.create_stats.smart_home.scene.delete"), x + 7, y + h - 23, (w - 17) / 2,
                16, mx, my, true, Kind.SCENE_DELETE, null, editingScene);
        buttonHit(g, Component.translatable("gui.create_stats.smart_home.done"), x + 10 + (w - 17) / 2, y + h - 23,
                w - 17 - (w - 17) / 2, 16, mx, my, true, Kind.SCENE_DONE, null, editingScene);
        g.pose().popPose();
    }

    private int[] ruleValues() {
        return config == null ? new int[] {-1, -1, -1} : new int[] {config.bedtime(), config.away(), config.home()};
    }

    private int deleteArmedScene = -1;
    private long deleteArmedAt;
    private static final int DELETE_ARM_TICKS = 80;

    private Component ruleName(int value, HouseModel.Rule rule) {
        if (value == HouseModel.LIGHTS) {
            return Component.translatable(rule == HouseModel.Rule.HOME
                    ? "gui.create_stats.smart_home.rule.lights_on" : "gui.create_stats.smart_home.rule.lights_off");
        }
        final SmartHomeSyncPacket.Scene s = scene(value);
        return s == null ? Component.translatable("gui.create_stats.smart_home.rule.off") : Component.literal(s.name());
    }

    private void drawSettings(GuiGraphics g, HomeLayout l, int mx, int my) {
        final int w = 232;
        final int h = 174;
        final int x = l.x + 4;
        final int y = Math.min(l.y + 16, height - h - 2);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        HomeKit.frame(g, x, y, w, h);
        hits.add(new Hit(x, y, w, h, Kind.EDITOR, null, 0));
        g.drawString(font, Component.translatable("gui.create_stats.smart_home.settings.title"), x + 7, y + 6,
                HomeKit.TITLE, false);
        if (renameKind == SmartHomeRenamePacket.HOUSE) {
            placeBox(x + 7, y + 18, w - 14, 16);
        } else {
            buttonHit(g, panel.houseName(), x + 7, y + 18, w - 14, 16, mx, my, true, Kind.SET_NAME, null, 0);
            if (mx >= x + 7 && mx < x + w - 7 && my >= y + 18 && my < y + 34) {
                tooltip = List.of(Component.translatable("gui.create_stats.smart_home.settings.name.hint")
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        g.drawString(font, Component.translatable("gui.create_stats.smart_home.rules"), x + 7, y + 40, HomeKit.TITLE,
                false);
        final HouseModel.Rule[] rules = HouseModel.Rule.values();
        final int[] values = ruleValues();
        for (int i = 0; i < rules.length; i++) {
            final int ry = y + 50 + i * 18;
            final String k = rules[i].name().toLowerCase(java.util.Locale.ROOT);
            final Component label = Component.translatable("gui.create_stats.smart_home.rule.cycle." + k,
                    ruleName(values[i], rules[i]));
            buttonHit(g, label, x + 7, ry, w - 14, 16, mx, my, true, Kind.SET_RULE, rules[i], values[i]);
            if (mx >= x + 7 && mx < x + w - 7 && my >= ry && my < ry + 16) {
                tooltip = List.of(label, Component.translatable("gui.create_stats.smart_home.rule." + k + ".hint")
                                .withStyle(ChatFormatting.GRAY),
                        Component.translatable("gui.create_stats.smart_home.rule.cycle_hint").withStyle(ChatFormatting.DARK_GRAY),
                        Component.translatable("gui.create_stats.smart_home.rule.powered").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        final Component face = Component.translatable(panel.isHiFiFace()
                ? "gui.create_stats.smart_home.face_hifi" : "gui.create_stats.smart_home.face_classic");
        buttonHit(g, face, x + 7, y + 108, w - 14, 16, mx, my, true, Kind.SET_FACE, null, panel.isHiFiFace() ? 1 : 0);
        if (mx >= x + 7 && mx < x + w - 7 && my >= y + 108 && my < y + 124) {
            tooltip = List.of(face, Component.translatable("gui.create_stats.smart_home.face.tip").withStyle(ChatFormatting.GRAY));
        }
        final Component toasts = Component.translatable("gui.create_stats.smart_home.toasts");
        final boolean on = config == null || config.toasts();
        final boolean hot = mx >= x + 7 && mx < x + 28 + font.width(toasts) && my >= y + 128 && my < y + 145;
        HomeKit.checkbox(g, font, toasts, x + 7, y + 128, on, hot, HomeKit.TITLE);
        hits.add(new Hit(x + 7, y + 128, 21 + font.width(toasts), 17, Kind.SET_TOASTS, null, on ? 1 : 0));
        if (hot) {
            tooltip = List.of(toasts, Component.translatable("gui.create_stats.smart_home.toasts.hint")
                    .withStyle(ChatFormatting.GRAY));
        }
        buttonHit(g, Component.translatable("gui.create_stats.smart_home.done"), x + w - 7 - 60, y + h - 23, 60, 16, mx,
                my, true, Kind.SET_DONE, null, 0);
        g.pose().popPose();
    }

    private void buttonHit(GuiGraphics g, Component label, int x, int y, int w, int h, int mx, int my, boolean active,
                           Kind kind, @Nullable Object arg, int value) {
        final boolean hot = active && mx >= x && mx < x + w && my >= y && my < y + h;
        HomeKit.button(g, font, label, x, y, w, h, hot, active);
        if (active) {
            hits.add(new Hit(x, y, w, h, kind, arg, value));
        }
    }

    private void placeBox(int x, int y, int w, int h) {
        nameBox.setPosition(x, y);
        nameBox.setWidth(Math.max(16, w));
        nameBox.setHeight(h);
        nameBox.visible = true;
    }

    private List<SmartHomeSyncPacket.Row> topRows() {
        return data == null ? List.of() : data.rows();
    }

    private List<SmartHomeSyncPacket.Row> flatRows() {
        if (flatCache == null || flatSource != data) {
            flatCache = flatten(topRows());
            flatSource = data;
        }
        return flatCache;
    }

    @Nullable
    private List<SmartHomeSyncPacket.Row> flatCache;
    @Nullable
    private SmartHomeSyncPacket flatSource;

    private static List<SmartHomeSyncPacket.Row> flatten(List<SmartHomeSyncPacket.Row> rows) {
        final List<SmartHomeSyncPacket.Row> out = new ArrayList<>(rows.size());
        for (SmartHomeSyncPacket.Row r : rows) {
            out.add(r);
            out.addAll(r.plugged());
        }
        return out;
    }

    @Nullable
    private SmartHomeSyncPacket.Room room(int id) {
        if (config == null) {
            return null;
        }
        for (SmartHomeSyncPacket.Room r : config.rooms()) {
            if (r.id() == id) {
                return r;
            }
        }
        return null;
    }

    @Nullable
    private SmartHomeSyncPacket.Scene scene(int slot) {
        return config == null || slot < 0 || slot >= config.scenes().size() ? null : config.scenes().get(slot);
    }

    @Nullable
    private SmartHomeSyncPacket.Row rowAt(BlockPos anchor) {
        for (SmartHomeSyncPacket.Row r : flatRows()) {
            if (r.anchor().equals(anchor)) {
                return r;
            }
        }
        return null;
    }

    private void send(SmartHomeActionPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.4f));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (renameKind >= 0 && !nameBox.isMouseOver(mx, my)) {
            commitRename();
        }
        if (nameBox.visible && nameBox.isMouseOver(mx, my)) {
            return super.mouseClicked(mx, my, button);
        }
        final BlockPos pos = panel.getBlockPos();
        final boolean modal = editingScene >= 0 || settingsOpen;

        for (int i = hits.size() - 1; i >= 0; i--) {
            final Hit hit = hits.get(i);
            if (!hit.contains(mx, my)) {
                continue;
            }
            if (modal && !isEditorKind(hit.kind())) {
                editingScene = -1;
                settingsOpen = false;
                return true;
            }
            if (onHit(hit, button, mx, my, pos)) {
                return true;
            }
        }
        if (modal) {
            editingScene = -1;
            settingsOpen = false;
            return true;
        }
        if (layout.onPlan(mx, my) && button == 0) {
            pressedOnPlan = true;
            dragging = false;
            dragStartX = mx;
            dragStartY = my;
            dragPanX = panX;
            dragPanZ = panZ;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean isEditorKind(Kind k) {
        return switch (k) {
            case SCENE_NAME, SCENE_ICON, SCENE_SAVE_ALL, SCENE_SAVE_LIGHTS, SCENE_SAVE_ROOM, SCENE_DELETE, SCENE_DONE,
                 SCENE_RULE -> editingScene >= 0;
            case SET_NAME, SET_FACE, SET_TOASTS, SET_RULE, SET_DONE -> settingsOpen;
            case EDITOR -> true;
            default -> false;
        };
    }

    private boolean onHit(Hit hit, int button, double mx, double my, BlockPos pos) {
        switch (hit.kind()) {
            case VIEW -> {
                view = (View) hit.arg();
                wireFocus = null;
                plan.invalidate();
                click();
                poll();
                return true;
            }
            case FLOOR_PREV -> {
                storey = Math.max(0, storey - 1);
                plan.invalidate();
                click();
                return true;
            }
            case FLOOR_NEXT -> {
                storey = Math.min(data == null ? 0 : data.storeys().size() - 1, storey + 1);
                plan.invalidate();
                click();
                return true;
            }
            case FIT -> {
                zoom = 0;
                panX = 0;
                panZ = 0;
                plan.invalidate();
                click();
                return true;
            }
            case TITLE -> {
                settingsOpen = true;
                editingScene = -1;
                click();
                return true;
            }
            case MARK -> {
                final BlockPos anchor = (BlockPos) hit.arg();
                final SmartHomeSyncPacket.Row r = rowAt(anchor);
                if (r == null) {
                    return false;
                }
                if (button == 1) {
                    openRow = r.anchor();
                    scrollToRow = r.anchor();
                } else if (state(r) != HomeSegment.State.MISSING) {
                    send(SmartHomeActionPacket.gate(pos, anchor, switchedOn(r)));
                    lightChanged();
                }
                click();
                return true;
            }
            case ALL_ON, ALL_OFF -> {
                send(SmartHomeActionPacket.roomLights(pos, -1, hit.kind() == Kind.ALL_OFF));
                lightChanged();
                click();
                return true;
            }
            case GROUP_SWITCH -> {
                final HomeGroups.Group group = (HomeGroups.Group) hit.arg();
                final boolean off = hit.value() != 0;
                switch (group.kind()) {
                    case ROOM -> send(SmartHomeActionPacket.roomLights(pos, group.id(), off));
                    case KIND -> send(SmartHomeActionPacket.kindAll(pos, group.id(), off));
                    case ELSEWHERE -> send(SmartHomeActionPacket.roomLights(pos, -2, off));
                    default -> {
                    }
                }
                lightChanged();
                click();
                return true;
            }
            case GROUP_PICK -> {
                final int id = (Integer) hit.arg();
                selectedRoom = selectedRoom == id ? -1 : id;
                click();
                return true;
            }
            case UNLISTED_FOLD -> {
                unlistedOpen = !unlistedOpen;
                click();
                return true;
            }
            case ROW_TOGGLE -> {
                if (button == 1) {
                    final BlockPos anchor = (BlockPos) hit.arg();
                    openRow = anchor.equals(openRow) ? null : anchor;
                    scrollToRow = openRow;
                } else {
                    final SmartHomeSyncPacket.Row r = rowAt((BlockPos) hit.arg());
                    if (r != null && state(r) != HomeSegment.State.MISSING) {
                        send(SmartHomeActionPacket.gate(pos, (BlockPos) hit.arg(), hit.value() != 0));
                        lightChanged();
                    }
                }
                click();
                return true;
            }
            case ROW_OPEN -> {
                final BlockPos anchor = (BlockPos) hit.arg();
                if (hit.value() != 0) {
                    openRow = anchor;
                    scrollToRow = anchor;
                } else {
                    openRow = anchor.equals(openRow) ? null : anchor;
                }
                return true;
            }
            case ROW_RENAME -> {
                final SmartHomeSyncPacket.Row r = rowAt((BlockPos) hit.arg());
                if (r != null) {
                    beginRename(SmartHomeRenamePacket.ROW, r.anchor(), 0, r.name().getString());
                }
                click();
                return true;
            }
            case MODE -> {
                sliderHeld = hit;
                applySlider(hit, mx);
                return true;
            }
            case COLOUR -> {
                send(SmartHomeActionPacket.colour(pos, (BlockPos) hit.arg(), hit.value()));
                click();
                return true;
            }
            case UNLINK -> {
                send(SmartHomeActionPacket.unlink(pos, (BlockPos) hit.arg()));
                openRow = null;
                click();
                return true;
            }
            case STANDBY_OFF -> {
                send(SmartHomeActionPacket.standbyOff(pos));
                click();
                return true;
            }
            case LIGHT_FIX -> {
                @SuppressWarnings("unchecked")
                final List<Integer> rooms = (List<Integer>) hit.arg();
                for (int id : rooms) {
                    send(SmartHomeActionPacket.roomLights(pos, id, false));
                }
                lightChanged();
                click();
                return true;
            }
            case ROOM_RENAME -> {
                final SmartHomeSyncPacket.Room room = room((Integer) hit.arg());
                if (room != null) {
                    beginRename(SmartHomeRenamePacket.ROOM, null, room.id(), room.name());
                }
                click();
                return true;
            }
            case ROOM_ICON -> {
                send(SmartHomeActionPacket.roomIcon(pos, (Integer) hit.arg(), button == 1));
                click();
                return true;
            }
            case ROOM_FOLLOW -> {
                send(SmartHomeActionPacket.roomFollow(pos, (Integer) hit.arg(), hit.value() == 0));
                click();
                return true;
            }
            case SCENE -> {
                final int slot = hit.value();
                final SmartHomeSyncPacket.Scene s = scene(slot);
                if (s == null) {
                    if (button == 0) {
                        send(SmartHomeActionPacket.sceneSave(pos, slot, -1, true));
                    }
                } else if (button == 1) {
                    editingScene = slot;
                    settingsOpen = false;
                } else {
                    send(SmartHomeActionPacket.sceneRecall(pos, slot));
                }
                click();
                return true;
            }
            case SCENE_NAME -> {
                final SmartHomeSyncPacket.Scene s = scene(hit.value());
                if (s != null) {
                    beginRename(SmartHomeRenamePacket.SCENE, null, hit.value(), s.name());
                }
                click();
                return true;
            }
            case SCENE_ICON -> {
                send(SmartHomeActionPacket.sceneIcon(pos, hit.value(), button == 1));
                click();
                return true;
            }
            case SCENE_SAVE_LIGHTS -> {
                send(SmartHomeActionPacket.sceneSave(pos, hit.value(), -1, true));
                click();
                return true;
            }
            case SCENE_SAVE_ALL -> {
                send(SmartHomeActionPacket.sceneSave(pos, hit.value(), -1, false));
                click();
                return true;
            }
            case SCENE_SAVE_ROOM -> {
                send(SmartHomeActionPacket.sceneSave(pos, hit.value(), selectedRoom, false));
                click();
                return true;
            }
            case SCENE_DELETE -> {
                final long now = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
                if (deleteArmedScene == hit.value() && now - deleteArmedAt < DELETE_ARM_TICKS) {
                    send(SmartHomeActionPacket.sceneDelete(pos, hit.value()));
                    editingScene = -1;
                    deleteArmedScene = -1;
                } else {
                    deleteArmedScene = hit.value();
                    deleteArmedAt = now;
                }
                click();
                return true;
            }
            case SCENE_RULE -> {
                final HouseModel.Rule rule = (HouseModel.Rule) hit.arg();

                send(SmartHomeActionPacket.rule(pos, rule, hit.value() != 0 ? -1 : editingScene));
                click();
                return true;
            }
            case SCENE_DONE, SET_DONE -> {
                commitRename();
                editingScene = -1;
                settingsOpen = false;
                click();
                return true;
            }
            case SET_NAME -> {
                beginRename(SmartHomeRenamePacket.HOUSE, null, 0, panel.houseName().getString());
                click();
                return true;
            }
            case SET_FACE -> {
                final boolean hiFi = hit.value() == 0;
                panel.setHiFiFace(hiFi);
                send(SmartHomeActionPacket.face(pos, hiFi));
                click();
                return true;
            }
            case SET_TOASTS -> {
                send(SmartHomeActionPacket.toasts(pos, hit.value() == 0));
                click();
                return true;
            }
            case SET_RULE -> {
                final HouseModel.Rule rule = (HouseModel.Rule) hit.arg();
                final List<Integer> choices = ruleChoices();
                final int at = Math.max(0, choices.indexOf(hit.value()));
                send(SmartHomeActionPacket.rule(pos, rule,
                        choices.get(Math.floorMod(at + (button == 1 ? -1 : 1), choices.size()))));
                click();
                return true;
            }
            case SCROLLER -> {
                scrollerHeld = true;
                scrollerKnob = hit.value();
                dragScroller(my, hit.value());
                return true;
            }
            case EDITOR -> {
                return !layout.onPlan(mx, my) || editingScene >= 0 || settingsOpen;
            }
            default -> {
                return false;
            }
        }
    }

    private int scrollerKnob;

    private List<Integer> ruleChoices() {
        final List<Integer> choices = new ArrayList<>();
        choices.add(-1);
        choices.add(HouseModel.LIGHTS);
        for (int s = 0; s < HouseModel.SCENE_SLOTS; s++) {
            if (scene(s) != null) {
                choices.add(s);
            }
        }
        return choices;
    }

    private void lightChanged() {
        if (view == View.LIGHT) {
            resampleIn = 8;
        }
    }

    private void applySlider(Hit hit, double mx) {
        final int n = hit.value();
        if (n <= 1) {
            return;
        }
        final double frac = Math.max(0, Math.min(1, (mx - hit.x() - 4) / (double) Math.max(1, hit.w() - 8)));
        final int mode = (int) Math.round(frac * (n - 1));
        final SmartHomeSyncPacket.Row r = rowAt((BlockPos) hit.arg());
        if (r != null && r.mode() != mode) {
            send(SmartHomeActionPacket.mode(panel.getBlockPos(), r.anchor(), mode));
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (sliderHeld != null) {
            applySlider(sliderHeld, mx);
            return true;
        }
        if (scrollerHeld) {
            dragScroller(my, scrollerKnob);
            return true;
        }
        if (pressedOnPlan && button == 0) {
            if (!dragging && (Math.abs(mx - dragStartX) > 3 || Math.abs(my - dragStartY) > 3)) {
                dragging = true;
            }
            if (dragging && fit != null) {
                panX = dragPanX - (int) Math.round((mx - dragStartX) / fit.scale());
                panZ = dragPanZ - (int) Math.round((my - dragStartY) / fit.scale());
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        sliderHeld = null;
        scrollerHeld = false;
        if (pressedOnPlan) {
            pressedOnPlan = false;
            if (dragging) {
                plan.invalidate();
            }
            if (!dragging && button == 0 && fit != null) {
                final int bx = fit.blockX((int) mx - layout.planX);
                final int bz = fit.blockZ((int) my - layout.planY);

                final byte kind = plan.kind(bx, bz);
                final int storeys = data == null ? 1 : data.storeys().size();
                if (kind == PlanGrid.STAIRS && storey < storeys - 1) {
                    storey++;
                    plan.invalidate();
                    click();
                    dragging = false;
                    return true;
                }
                if (kind == PlanGrid.STAIRS_BELOW && storey > 0) {
                    storey--;
                    plan.invalidate();
                    click();
                    dragging = false;
                    return true;
                }

                final WireGraph.Wire wire = wireAt(bx, bz);
                if (wire != null) {
                    final boolean same = wireFocus != null && wireFocus[0] == wire.x() && wireFocus[1] == wire.y()
                            && wireFocus[2] == wire.z();
                    wireFocus = same ? null : new int[] {wire.x(), wire.y(), wire.z()};
                    click();
                    dragging = false;
                    return true;
                }
                wireFocus = null;

                final SmartHomeSyncPacket.Room room = roomAt(bx, bz);
                if (room == null) {
                    selectedRoom = -1;
                } else {
                    selectedRoom = room.id();
                    scrollToGroup = new HomeGroups.Group(HomeGroups.Kind.ROOM, room.id(), List.of()).key();
                    click();
                }
            }
            dragging = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (editingScene >= 0 || settingsOpen) {
            return true;
        }
        if (layout.onPlan(mx, my)) {
            final int next = Math.max(-4, Math.min(6, zoom + (sy > 0 ? 1 : -1)));

            if (next < zoom && data != null) {
                final MapFit at = MapFit.fit(layout.planSize, data.minX(), data.minZ(), data.maxX(), data.maxZ(),
                        panel.getBlockPos().getX(), panel.getBlockPos().getZ(), next, panX, panZ);
                if (at.scale() < MapFit.MIN_FIT_SCALE) {
                    return true;
                }
            }
            if (next != zoom && data != null) {
                final int[] pan = MapFit.panToKeep(layout.planSize, data.minX(), data.minZ(), data.maxX(), data.maxZ(),
                        panel.getBlockPos().getX(), panel.getBlockPos().getZ(), zoom, next, panX, panZ,
                        (int) mx - layout.planX, (int) my - layout.planY);
                panX = pan[0];
                panZ = pan[1];
            }
            zoom = next;
            plan.invalidate();
            return true;
        }
        if (mx >= layout.sideX && mx < layout.sideX + layout.sideW && my >= layout.sideY && my < layout.sideBottom) {
            listScroll = Math.max(0, listScroll - (int) Math.round(sy * 21));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renameKind >= 0 && nameBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }

            return super.keyPressed(keyCode, scanCode, modifiers) || true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (editingScene >= 0 || settingsOpen)) {
            editingScene = -1;
            settingsOpen = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (openRow != null || selectedRoom >= 0)) {
            openRow = null;
            selectedRoom = -1;
            return true;
        }

        if (editingScene < 0 && !settingsOpen && fit != null) {
            final int step = Math.max(1, layout.planSize / fit.scale() / 4);
            boolean moved = true;
            switch (keyCode) {
                case GLFW.GLFW_KEY_LEFT -> panX -= step;
                case GLFW.GLFW_KEY_RIGHT -> panX += step;
                case GLFW.GLFW_KEY_UP -> panZ -= step;
                case GLFW.GLFW_KEY_DOWN -> panZ += step;
                case GLFW.GLFW_KEY_PAGE_UP -> storey = Math.min(data == null ? 0 : data.storeys().size() - 1, storey + 1);
                case GLFW.GLFW_KEY_PAGE_DOWN -> storey = Math.max(0, storey - 1);
                default -> moved = false;
            }
            if (moved) {
                plan.invalidate();
                return true;
            }
        }

        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9 && editingScene < 0 && !settingsOpen) {
            final int slot = keyCode - GLFW.GLFW_KEY_1;
            if (scene(slot) != null) {
                send(SmartHomeActionPacket.sceneRecall(panel.getBlockPos(), slot));
                click();
                return true;
            }
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void beginRename(int kind, @Nullable BlockPos row, int id, String current) {
        commitRename();
        renameKind = kind;
        renameRow = row;
        renameId = id;
        renameOriginal = current;
        nameBox.setValue(current);
        nameBox.setFocused(true);
        setFocused(nameBox);
    }

    private void commitRename() {
        if (renameKind < 0) {
            return;
        }
        final String text = nameBox.getValue();
        if (!text.equals(renameOriginal)) {
            final BlockPos pos = panel.getBlockPos();
            switch (renameKind) {
                case SmartHomeRenamePacket.ROW -> {
                    if (renameRow != null) {
                        PacketDistributor.sendToServer(SmartHomeRenamePacket.row(pos, renameRow, text));
                    }
                }
                case SmartHomeRenamePacket.ROOM -> PacketDistributor.sendToServer(
                        SmartHomeRenamePacket.room(pos, renameId, text));
                case SmartHomeRenamePacket.SCENE -> PacketDistributor.sendToServer(
                        SmartHomeRenamePacket.scene(pos, renameId, text));
                case SmartHomeRenamePacket.HOUSE -> PacketDistributor.sendToServer(
                        SmartHomeRenamePacket.house(pos, text));
                default -> {
                }
            }
        }
        cancelRename();
    }

    private void cancelRename() {
        renameKind = -1;
        renameRow = null;
        nameBox.setFocused(false);
        setFocused(null);
    }
}
