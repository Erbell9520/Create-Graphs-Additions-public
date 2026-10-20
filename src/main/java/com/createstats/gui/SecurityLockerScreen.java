package com.createstats.gui;

import com.createstats.blockentity.SecurityLockerBE;
import com.createstats.menu.SecurityLockerMenu;
import com.createstats.network.LockerScrollPacket;
import com.createstats.security.Clearance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class SecurityLockerScreen extends AbstractContainerScreen<SecurityLockerMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "create_stats", "textures/gui/security_locker.png");

    private static final int PANEL_W = SecurityLockerMenu.PANEL_W;

    private static final int TOP_V = 0;
    private static final int ROW_V = SecurityLockerMenu.TOP_H;
    private static final int ROW_DEAD_V = ROW_V + SecurityLockerMenu.ROW_H;
    private static final int BOTTOM_V = ROW_DEAD_V + SecurityLockerMenu.ROW_H;

    private static final int TRACK_Y = SecurityLockerMenu.TOP_H;
    private static final int KNOB_U = 192;
    private static final int KNOB_LIVE_V = 0;
    private static final int KNOB_DEAD_V = SecurityLockerMenu.KNOB_H;

    private static final int RACK_U = 208;
    private static final int RACK_V = 0;

    private static final int COMPACT_ABOVE = 999;

    private static final int INK = 0x404040;
    private static final int INK_DIM = 0x707070;
    private static final int INK_WARN = 0x9A3F24;

    private boolean scrolling;

    private int requested;

    public SecurityLockerScreen(SecurityLockerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);

        this.imageWidth = SecurityLockerMenu.TOTAL_W;
        this.imageHeight = menu.panelHeight();
        this.inventoryLabelY = menu.invY() - 12;
        this.requested = menu.scrollRow();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        final int x = this.leftPos;
        int y = this.topPos;

        g.blit(TEXTURE, x, y, 0, TOP_V, PANEL_W, SecurityLockerMenu.TOP_H);
        y += SecurityLockerMenu.TOP_H;
        for (int row = 0; row < SecurityLockerMenu.VISIBLE_ROWS; row++) {
            g.blit(TEXTURE, x, y, 0, this.menu.rowLive(row) ? ROW_V : ROW_DEAD_V,
                    PANEL_W, SecurityLockerMenu.ROW_H);
            y += SecurityLockerMenu.ROW_H;
        }
        g.blit(TEXTURE, x, y, 0, BOTTOM_V, PANEL_W, SecurityLockerMenu.BOTTOM_H);

        final boolean live = this.menu.scrollable();
        g.blit(TEXTURE, x + SecurityLockerMenu.SCROLL_X, this.topPos + knobY(),
                KNOB_U, live ? KNOB_LIVE_V : KNOB_DEAD_V,
                SecurityLockerMenu.SCROLL_W, SecurityLockerMenu.KNOB_H);

        g.blit(TEXTURE, x + SecurityLockerMenu.RACK_X, this.topPos + SecurityLockerMenu.TOP_H,
                RACK_U, RACK_V, SecurityLockerMenu.RACK_W, gridHeight());
    }

    private static int gridHeight() {
        return SecurityLockerMenu.VISIBLE_ROWS * SecurityLockerMenu.ROW_H;
    }

    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {
        final ItemStack stack = slot.getItem();
        if (stack.isEmpty() || stack.getCount() <= COMPACT_ABOVE) {
            super.renderSlot(g, slot);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 100.0F);
        g.renderItem(stack, slot.x, slot.y, slot.x + slot.y * this.imageWidth);
        g.renderItemDecorations(this.font, stack, slot.x, slot.y, compact(stack.getCount()));
        g.pose().popPose();
    }

    private static String compact(int count) {
        if (count < 10_000) {
            final int tenths = (count + 50) / 100;
            return (tenths / 10) + "." + (tenths % 10) + "k";
        }
        return ((count + 500) / 1000) + "k";
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, INK, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX,
                this.inventoryLabelY, INK, false);

        drawReadout(g, SecurityLockerMenu.READ_DY0, String.valueOf(slotCount()));
        drawReadout(g, SecurityLockerMenu.READ_DY1, String.valueOf(this.menu.stackCap()));

        final SecurityLockerBE locker = this.menu.locker();
        if (locker == null) {
            return;
        }
        final String status = locker.governed()
                ? Clearance.badge(locker.requiredLevel()).getString()
                : Component.translatable("gui.create_stats.locker.ungoverned").getString();
        g.drawString(this.font, status, SecurityLockerMenu.GRID_X, 18,
                locker.governed() ? INK_DIM : INK_WARN, false);

        final Component code = Component.translatable(locker.hasCode()
                ? "gui.create_stats.locker.code_set" : "gui.create_stats.locker.no_code");
        final int width = this.font.width(code);
        g.drawString(this.font, code, PANEL_W - SecurityLockerMenu.GRID_X - width, 18,
                locker.hasCode() ? INK_DIM : INK_WARN, false);
    }

    private void drawReadout(GuiGraphics g, int dy, String text) {
        final int right = SecurityLockerMenu.READ_X + SecurityLockerMenu.READ_W - 2;
        g.drawString(this.font, text, right - this.font.width(text),
                SecurityLockerMenu.TOP_H + dy + 3, INK, false);
    }

    private int slotCount() {
        return this.menu.rows() * SecurityLockerBE.COLUMNS;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (!this.menu.getCarried().isEmpty()) {
            return;
        }
        final List<Component> lines = new ArrayList<>(2);
        if (within(mouseX, mouseY, SecurityLockerMenu.RACK_X + SecurityLockerMenu.RACK_LIP,
                SecurityLockerMenu.TOP_H + 2,
                SecurityLockerMenu.RACK_W - SecurityLockerMenu.RACK_LIP - 4,
                SecurityLockerMenu.RACK_HEAD_H - 2)) {
            lines.add(Component.translatable("gui.create_stats.locker.upgrades"));
            lines.add(Component.translatable("gui.create_stats.locker.upgrades_hint")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        } else if (within(mouseX, mouseY, SecurityLockerMenu.READ_X,
                SecurityLockerMenu.TOP_H + SecurityLockerMenu.READ_DY0,
                SecurityLockerMenu.READ_W, SecurityLockerMenu.READ_H)) {
            lines.add(Component.translatable("gui.create_stats.locker.capacity_slots", slotCount()));
            lines.add(Component.translatable("gui.create_stats.locker.capacity_rows",
                            this.menu.rows(), SecurityLockerBE.COLUMNS)
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        } else if (within(mouseX, mouseY, SecurityLockerMenu.READ_X,
                SecurityLockerMenu.TOP_H + SecurityLockerMenu.READ_DY1,
                SecurityLockerMenu.READ_W, SecurityLockerMenu.READ_H)) {
            lines.add(Component.translatable("gui.create_stats.locker.capacity_depth",
                    this.menu.stackCap()));
            lines.add(Component.translatable("gui.create_stats.locker.capacity_depth_hint")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        if (!lines.isEmpty()) {
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    private boolean within(int mouseX, int mouseY, int x, int y, int w, int h) {
        final int px = mouseX - this.leftPos;
        final int py = mouseY - this.topPos;
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private int trackHeight() {
        return gridHeight() - SecurityLockerMenu.KNOB_H;
    }

    private int knobY() {
        final int range = this.menu.scrollRange();
        if (range <= 0) {
            return TRACK_Y;
        }
        return TRACK_Y + Math.round((float) trackHeight() * this.menu.scrollRow() / range);
    }

    private void requestScroll(int row) {
        final int clamped = Math.max(0, Math.min(this.menu.scrollRange(), row));
        if (clamped == this.requested) {
            return;
        }
        this.requested = clamped;
        PacketDistributor.sendToServer(new LockerScrollPacket(this.menu.containerId, clamped));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (this.menu.scrollable() && scrollY != 0.0) {
            requestScroll(this.menu.scrollRow() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.menu.scrollable() && overBar(mx, my)) {
            this.scrolling = true;
            dragTo(my);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.scrolling) {
            dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.scrolling = false;
        return super.mouseReleased(mx, my, button);
    }

    private boolean overBar(double mx, double my) {
        final int x = this.leftPos + SecurityLockerMenu.SCROLL_X;
        final int y = this.topPos + TRACK_Y;
        return mx >= x && mx < x + SecurityLockerMenu.SCROLL_W
                && my >= y && my < y + gridHeight();
    }

    private void dragTo(double my) {
        final int track = trackHeight();
        if (track <= 0) {
            return;
        }
        final double top = this.topPos + TRACK_Y + SecurityLockerMenu.KNOB_H / 2.0;
        final double fraction = (my - top) / track;
        requestScroll((int) Math.round(fraction * this.menu.scrollRange()));
    }
}
