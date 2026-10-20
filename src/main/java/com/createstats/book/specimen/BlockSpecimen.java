package com.createstats.book.specimen;

import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.BlockView;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class BlockSpecimen implements ManualSpecimen {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<BlockEntityType<?>> WARNED = ConcurrentHashMap.newKeySet();

    private static final float DEPTH = 150.0f;

    private static final long MODEL_SEED = 42L;

    private record Prepared(BlockState state, BlockEntity entity, double x, double y, double z) {
    }

    private final BlockFigure figure;
    private final BlockView view;
    private final List<Prepared> scene = new ArrayList<>();

    private final com.createstats.book.scene.FigureWorld world;

    public BlockSpecimen(BlockFigure figure) {
        this.figure = figure;
        this.view = figure.view();

        final List<BlockState> states = new ArrayList<>(figure.placements().size());
        for (final BlockFigure.Placement p : figure.placements()) {
            states.add(state(p.block(), p.props()));
        }
        final com.createstats.book.scene.FigureWorld world = com.createstats.book.scene.FigureWorld.of(figure);
        this.world = world;
        for (int i = 0; i < figure.placements().size(); i++) {
            final BlockFigure.Placement p = figure.placements().get(i);
            final BlockState state = states.get(i);
            BlockEntity entity = null;
            if (p.renderer() && p.fixture() != null) {
                entity = BlockFixtures.create(figure, i, state, cell -> {
                    final int at = world.indexAt(cell);
                    return at < 0 ? null : states.get(at);
                });
            } else if (p.renderer() && state.getBlock() instanceof EntityBlock eb) {
                entity = eb.newBlockEntity(BlockPos.ZERO, state);
            }
            if (entity != null && Minecraft.getInstance().level != null) {
                entity.setLevel(Minecraft.getInstance().level);
            }
            scene.add(new Prepared(state, entity, p.x(), p.y(), p.z()));
        }
    }

    static BlockState state(String id, Map<String, String> props) {
        final Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        BlockState state = block.defaultBlockState();
        for (final Map.Entry<String, String> e : props.entrySet()) {
            final Property<?> property = block.getStateDefinition().getProperty(e.getKey());
            if (property != null) {
                state = with(state, property, e.getValue());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    @Override
    public int specimenWidth() {
        return figure.width();
    }

    @Override
    public int specimenHeight() {
        return figure.height();
    }

    @Override
    public SpecimenAnatomy anatomy() {
        return figure.anatomy();
    }

    @Override
    public void renderSpecimen(GuiGraphics g, int mouseX, int mouseY) {
        final PoseStack pose = g.pose();
        final Matrix4f m = pose.last().pose();
        final Vector3f a = m.transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
        final Vector3f b = m.transformPosition(figure.width(), figure.height(), 0.0f, new Vector3f());
        final int x0 = (int) Math.floor(Math.min(a.x(), b.x()));
        final int y0 = (int) Math.floor(Math.min(a.y(), b.y()));
        final int x1 = (int) Math.ceil(Math.max(a.x(), b.x()));
        final int y1 = (int) Math.ceil(Math.max(a.y(), b.y()));
        if (x1 - x0 < 2 || y1 - y0 < 2) {
            return;
        }

        g.flush();
        g.enableScissor(x0, y0, x1, y1);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        pose.pushPose();
        applyView(pose, view, DEPTH);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        Lighting.setupFor3DItems();

        final BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        final MultiBufferSource.BufferSource buffer = g.bufferSource();
        for (final Prepared p : scene) {
            if (p.state().isAir()) {
                continue;
            }
            pose.pushPose();
            pose.translate((float) p.x(), (float) p.y(), (float) p.z());
            final BakedModel model = brd.getBlockModel(p.state());
            final int tint = Minecraft.getInstance().getBlockColors().getColor(p.state(), null, null, 0);
            final float red = (tint >> 16 & 0xFF) / 255.0f;
            final float green = (tint >> 8 & 0xFF) / 255.0f;
            final float blue = (tint & 0xFF) / 255.0f;
            for (final RenderType type : model.getRenderTypes(p.state(), RandomSource.create(MODEL_SEED), ModelData.EMPTY)) {
                brd.getModelRenderer().renderModel(pose.last(),
                        buffer.getBuffer(RenderTypeHelper.getEntityRenderType(type, true)), p.state(), model,
                        red, green, blue, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
            }
            if (p.entity() != null) {
                renderEntity(p.entity(), pose, buffer);
            }
            pose.popPose();
        }
        drawSpans(pose, buffer);
        buffer.endBatch();
        pose.popPose();

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        g.disableScissor();
        RenderSystem.disableDepthTest();
        Lighting.setupForFlatItems();
        g.flush();
    }

    private void drawSpans(PoseStack pose, MultiBufferSource.BufferSource buffer) {
        final List<BlockFigure.Tie> spans = world.spans();
        if (spans.isEmpty()) {
            return;
        }
        final com.mojang.blaze3d.vertex.VertexConsumer wire =
                com.createstats.renderer.PowerConnectorRenderer.wireBuffer(buffer);
        for (final BlockFigure.Tie t : spans) {
            final net.minecraft.world.phys.Vec3 a = world.terminalPoint(new BlockPos(t.from()[0], t.from()[1], t.from()[2]));
            final net.minecraft.world.phys.Vec3 b = world.terminalPoint(new BlockPos(t.to()[0], t.to()[1], t.to()[2]));
            com.createstats.renderer.PowerConnectorRenderer.drawCable(wire, pose.last().pose(),
                    (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z,
                    LightTexture.FULL_BRIGHT, t.tier(), t.tension().slack());
        }
    }

    public static void applyView(PoseStack pose, BlockView view, float depth) {
        pose.translate((float) view.offsetX(), (float) view.offsetY(), depth);
        pose.scale((float) view.scale(), (float) -view.scale(), (float) view.scale());
        pose.mulPose(Axis.XP.rotationDegrees((float) view.pitch()));
        pose.mulPose(Axis.YP.rotationDegrees((float) view.yaw()));
    }

    @SuppressWarnings("unchecked")
    private static void renderEntity(BlockEntity entity, PoseStack pose, MultiBufferSource.BufferSource buffer) {
        if (entity.getLevel() == null) {
            return;
        }
        final PoseStack isolated = new PoseStack();
        isolated.last().pose().set(pose.last().pose());
        isolated.last().normal().set(pose.last().normal());
        try {
            final BlockEntityRenderer<BlockEntity> renderer = (BlockEntityRenderer<BlockEntity>)
                    Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
            if (renderer != null) {
                renderer.render(entity, 0.0f, isolated, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        } catch (Throwable failure) {
            if (WARNED.add(entity.getType())) {
                LOGGER.warn("Student's Manual: the {} renderer failed in a figure; it draws without that detail",
                        entity.getType(), failure);
            }
        }
    }
}
