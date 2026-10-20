package com.createstats.chunkloader.client;

import com.createstats.chunkloader.look.CrownMaterial;
import com.createstats.chunkloader.look.CrownMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public final class CrownMatterSink implements CrownMesh.Sink {
    private static final CrownMaterial[] MATERIALS = CrownMaterial.values();
    private static final ResourceLocation[] IDS = new ResourceLocation[MATERIALS.length];

    static {
        for (CrownMaterial m : MATERIALS) {
            IDS[m.ordinal()] = ResourceLocation.fromNamespaceAndPath("create_stats", "block/" + m.path);
        }
    }

    private final TextureAtlasSprite[] sprites = new TextureAtlasSprite[MATERIALS.length];
    private final Vector3f pos = new Vector3f();
    private final Vector3f nrm = new Vector3f();
    private final int[] layerLight = new int[3];

    private VertexConsumer vc;
    private Matrix4f pose;
    private Matrix3f normal;
    private int turns;
    private boolean item;
    private int itemLight;
    private int overlay;

    public void beginWorld(VertexConsumer consumer, PoseStack.Pose at, int turns, int[] lights) {
        begin(consumer, at);
        this.turns = Math.floorMod(turns, 4);
        System.arraycopy(lights, 0, layerLight, 0, 3);
        this.item = false;
    }

    public void beginItem(VertexConsumer consumer, PoseStack.Pose at, int light, int itemOverlay) {
        begin(consumer, at);
        this.turns = 0;
        this.item = true;
        this.itemLight = light;
        this.overlay = itemOverlay;
    }

    private void begin(VertexConsumer consumer, PoseStack.Pose at) {
        this.vc = consumer;
        this.pose = at.pose();
        this.normal = at.normal();
        final TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        for (int i = 0; i < MATERIALS.length; i++) {
            sprites[i] = atlas.getSprite(IDS[i]);
        }
    }

    public void end() {
        vc = null;
    }

    @Override
    public void quad(float[] p, float[] uv, float nx, float ny, float nz, CrownMaterial material, boolean glow) {
        final TextureAtlasSprite s = sprites[material.ordinal()];
        int c = 255;
        if (!item && !glow) {
            float wx = nx;
            float wz = nz;
            for (int t = 0; t < turns; t++) {
                final float x = -wz;
                wz = wx;
                wx = x;
            }
            final float shade = Math.min(1f, wx * wx * 0.6f + ny * ny * ((3f + ny) / 4f) + wz * wz * 0.8f);
            c = Math.round(255 * shade);
        }

        final float ox = item && glow ? 0f : nx;
        final float oy = item && glow ? 1f : ny;
        final float oz = item && glow ? 0f : nz;
        normal.transform(ox, oy, oz, nrm);
        nrm.normalize();
        for (int i = 0; i < 4; i++) {
            final float y = p[i * 3 + 1];
            pose.transformPosition(p[i * 3], y, p[i * 3 + 2], pos);
            final int light = glow ? LightTexture.FULL_BRIGHT
                    : item ? itemLight : layerLight[Math.max(0, Math.min(2, (int) Math.floor(y / 16f)))];
            vc.addVertex(pos.x, pos.y, pos.z)
                    .setColor(c, c, c, 255)
                    .setUv(s.getU(uv[i * 2] / 16f), s.getV(uv[i * 2 + 1] / 16f));
            if (item) {
                vc.setOverlay(overlay);
            }
            vc.setLight(light).setNormal(nrm.x, nrm.y, nrm.z);
        }
    }
}
