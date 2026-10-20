package com.createstats.compat.jei;

import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.power.CableTier;
import com.createstats.power.ConnectorTier;
import com.createstats.power.FeRates;
import com.createstats.power.WireTier;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ConductorLadderCategory implements IRecipeCategory<ConductorLadderCategory.TierPage> {
    public record TierPage(int tier, CableTier cable, WireTier wire, @Nullable ConnectorTier connector,
                           ItemStack builtFrom, List<ItemStack> machines, int operations,
                           int arcFePerIngot) {
        public ItemStack cableStack() {
            return switch (cable) {
                case SURFACE -> ModBlocks.SURFACE_CABLE.asStack();
                case TRAY -> ModBlocks.CABLE_TRAY.asStack();
                case DUCT -> ModBlocks.BUS_DUCT.asStack();
                case TRUNK -> ModBlocks.BUSBAR_TRUNK.asStack();
            };
        }

        public ItemStack wireStack() {
            return new ItemStack(ModItems.wireItem(wire));
        }

        @Nullable
        public ItemStack connectorStack() {
            if (connector == null) {
                return null;
            }
            return switch (connector) {
                case POWER -> ModBlocks.POWER_CONNECTOR.asStack();
                case ADVANCED -> ModBlocks.ADVANCED_POWER_CONNECTOR.asStack();
                case INDUSTRIAL -> ModBlocks.INDUSTRIAL_POWER_CONNECTOR.asStack();
            };
        }
    }

    public static final RecipeType<TierPage> TYPE =
            RecipeType.create("create_stats", "conductor_ladder", TierPage.class);

    private static final int WIDTH = 170;
    private static final int HEIGHT = 146;
    private static final int SLOT = 18;
    private static final int ROW_Y = 16;
    private static final int ROW_H = 19;
    private static final int FROM_Y = 76;
    private static final int FROM_SLOT_X = 64;
    private static final int TO_SLOT_X = 100;
    private static final int MACH_Y = 97;
    private static final int MACH_SLOT_Y = 106;
    private static final int FOOT_Y = 127;
    private static final int FOOT_H = 9;
    private static final int CHIP_W = 40;
    private static final int CHIP_H = 11;

    private static final int INK = 0xFF404040;
    private static final int INK_DIM = 0xFF7A7A7A;
    private static final int RUNG_OFF = 0xFFA8A8A8;

    private static final int[] ACCENT = {0, 0xFFA0441B, 0xFF3A8AD0, 0xFFC8AA3A, 0xFF5B3A9E};

    private final IDrawable icon;

    public ConductorLadderCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.HV_LINE.get());
    }

    public static List<TierPage> pagesFor(int arcFePerIngot) {
        final CableTier[] cables = CableTier.values();
        final WireTier[] wires = WireTier.values();
        final ConnectorTier[] connectors = ConnectorTier.values();
        final List<ItemStack> machines = new ArrayList<>();
        final int[] operations = {2, 4, 9, 19};
        final List<TierPage> pages = new ArrayList<>();
        for (int i = 0; i < wires.length; i++) {
            final int tier = i + 1;

            switch (tier) {
                case 1 -> {
                    machines.add(AllBlocks.MECHANICAL_PRESS.asStack());
                    machines.add(AllBlocks.MECHANICAL_SAW.asStack());
                }
                case 2 -> {
                    machines.add(AllBlocks.ENCASED_FAN.asStack());
                    machines.add(AllBlocks.DEPLOYER.asStack());
                }
                case 3 -> {
                    machines.add(AllBlocks.MECHANICAL_MIXER.asStack());
                    machines.add(AllBlocks.SPOUT.asStack());
                }
                default -> {
                    machines.add(ModBlocks.ARC_FURNACE.asStack());
                    machines.add(AllBlocks.CRUSHING_WHEEL.asStack());
                    machines.add(AllBlocks.MECHANICAL_CRAFTER.asStack());
                }
            }
            final ItemStack builtFrom = i == 0
                    ? AllItems.COPPER_SHEET.asStack()
                    : new ItemStack(ModItems.wireItem(wires[i - 1]));
            pages.add(new TierPage(tier, cables[Math.min(i, cables.length - 1)], wires[i],
                    i == 0 ? null : connectors[Math.min(i - 1, connectors.length - 1)],
                    builtFrom, List.copyOf(machines),
                    operations[Math.min(i, operations.length - 1)],
                    tier == 4 ? arcFePerIngot : 0));
        }
        return pages;
    }

    @Override
    public RecipeType<TierPage> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("create_stats.jei.conductor_ladder");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public ResourceLocation getRegistryName(TierPage page) {
        return ResourceLocation.fromNamespaceAndPath("create_stats", "conductor_ladder/tier_" + page.tier());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, TierPage page, IFocusGroup focuses) {
        final int base = FeRates.base();
        final int range = PowerConnectorBE.wireRange();

        final IRecipeSlotBuilder cable = builder.addSlot(RecipeIngredientRole.OUTPUT, 2, ROW_Y)
                .setStandardSlotBackground()
                .addItemStack(page.cableStack());
        cable.addRichTooltipCallback((view, tooltip) -> {
            tooltip.add(Component.translatable("create_stats.jei.conductor_ladder.tip.cable",
                    FeRates.perTick(page.cable().rate(base))));
            tooltip.add(Component.translatable("create_stats.jei.conductor_ladder.tip.half"));
        });

        final IRecipeSlotBuilder wire = builder.addSlot(RecipeIngredientRole.OUTPUT, 2, ROW_Y + ROW_H)
                .setStandardSlotBackground()
                .addItemStack(page.wireStack());
        wire.addRichTooltipCallback((view, tooltip) -> {
            tooltip.add(Component.translatable("create_stats.jei.conductor_ladder.tip.wire",
                    FeRates.perTick(base * page.wire().multiplier()), range));
            tooltip.add(Component.translatable("create_stats.jei.conductor_ladder.tip.built_from",
                    page.builtFrom().getHoverName()));
            final WireTier[] wires = WireTier.values();
            if (page.wire().ordinal() + 1 < wires.length) {
                tooltip.add(Component.translatable("create_stats.jei.conductor_ladder.tip.upgrades",
                        new ItemStack(ModItems.wireItem(wires[page.wire().ordinal() + 1])).getHoverName()));
            }
        });

        final ItemStack connectorStack = page.connectorStack();
        if (connectorStack != null && page.connector() != null) {
            final ConnectorTier tier = page.connector();
            builder.addSlot(RecipeIngredientRole.OUTPUT, 2, ROW_Y + 2 * ROW_H)
                    .setStandardSlotBackground()
                    .addItemStack(connectorStack)
                    .addRichTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.translatable("create_stats.jei.conductor_ladder.tip.connector",
                                FeRates.perTick(base * tier.multiplier()), tier.maxLinks()));
                        tooltip.add(Component.translatable("create_stats.jei.conductor_ladder.tip.equals"));
                    });
        }

        builder.addSlot(RecipeIngredientRole.INPUT, FROM_SLOT_X, FROM_Y)
                .setStandardSlotBackground()
                .addItemStack(page.builtFrom());
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, TO_SLOT_X, FROM_Y)
                .setStandardSlotBackground()
                .addItemStack(page.wireStack());

        int x = 2;
        for (ItemStack machine : page.machines()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, x, MACH_SLOT_Y)
                    .setStandardSlotBackground()
                    .addItemStack(machine);
            x += SLOT;
        }
    }

    @Override
    public void draw(TierPage page, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        final Font font = Minecraft.getInstance().font;
        final int base = FeRates.base();
        final int accent = ACCENT[page.tier()];

        g.fill(2, 2, 2 + CHIP_W, 2 + CHIP_H, accent);
        g.drawString(font, Component.translatable("create_stats.jei.conductor_ladder.tier", page.tier()),
                5, 4, 0xFFFFFFFF, false);
        for (int rung = 1; rung <= 4; rung++) {
            final int rx = 2 + CHIP_W + 4 + (rung - 1) * 9;
            g.fill(rx, 6, rx + 7, 10, rung <= page.tier() ? ACCENT[rung] : RUNG_OFF);
        }
        final Component tierName = Component.translatable(
                "create_stats.jei.conductor_ladder.name." + page.tier());
        g.drawString(font, tierName, WIDTH - 2 - font.width(tierName), 4, accent, false);

        row(g, font, 0, page.cableStack().getHoverName(), FeRates.perTick(page.cable().rate(base)),
                Component.translatable("create_stats.jei.conductor_ladder.per_block"), accent);
        row(g, font, 1, page.wireStack().getHoverName(), FeRates.perTick(base * page.wire().multiplier()),
                Component.translatable("create_stats.jei.conductor_ladder.per_span"), accent);
        final ItemStack connector = page.connectorStack();
        if (connector != null && page.connector() != null) {
            row(g, font, 2, connector.getHoverName(), FeRates.perTick(base * page.connector().multiplier()),
                    Component.translatable("create_stats.jei.conductor_ladder.links",
                            page.connector().maxLinks()), accent);
        } else {
            g.drawString(font, Component.translatable("create_stats.jei.conductor_ladder.no_connector"),
                    24, ROW_Y + 2 * ROW_H + 5, INK_DIM, false);
        }

        g.drawString(font, Component.translatable("create_stats.jei.conductor_ladder.built_from"),
                2, FROM_Y + 5, INK, false);
        final int ax = FROM_SLOT_X + SLOT + 4;
        g.fill(ax, FROM_Y + 8, ax + 10, FROM_Y + 10, INK);
        for (int k = 0; k < 3; k++) {
            g.fill(ax + 8 + k, FROM_Y + 6 + k, ax + 9 + k, FROM_Y + 7 + k, INK);
            g.fill(ax + 8 + k, FROM_Y + 11 - k, ax + 9 + k, FROM_Y + 12 - k, INK);
        }
        g.drawString(font, page.tier() == 1
                        ? Component.translatable("create_stats.jei.conductor_ladder.raw")
                        : Component.translatable("create_stats.jei.conductor_ladder.from_tier", page.tier() - 1),
                TO_SLOT_X + SLOT + 4, FROM_Y + 5, INK_DIM, false);

        g.drawString(font, Component.translatable("create_stats.jei.conductor_ladder.machines"),
                2, MACH_Y, INK, false);

        g.drawString(font, Component.translatable("create_stats.jei.conductor_ladder.ops", page.operations()),
                2, FOOT_Y, INK_DIM, false);
        g.drawString(font, page.arcFePerIngot() > 0
                        ? Component.translatable("create_stats.jei.conductor_ladder.fe",
                                FeRates.amount(page.arcFePerIngot()))
                        : Component.translatable("create_stats.jei.conductor_ladder.no_grid"),
                2, FOOT_Y + FOOT_H, INK_DIM, false);
    }

    private static void row(GuiGraphics g, Font font, int index, Component name, String rate,
                            Component per, int accent) {
        final int y = ROW_Y + index * ROW_H;
        g.drawString(font, name, 24, y + 2, INK, false);
        g.drawString(font, rate, 24, y + 10, INK_DIM, false);
        g.drawString(font, per, WIDTH - 2 - font.width(per), y + 10, accent, false);
    }
}
