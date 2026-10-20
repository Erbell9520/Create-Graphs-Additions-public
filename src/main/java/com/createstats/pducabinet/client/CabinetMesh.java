package com.createstats.pducabinet.client;

import com.createstats.pducabinet.CabinetGeometry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// exact saved blockbench quads
final class CabinetMesh {
    private static final ResourceLocation DATA=ResourceLocation.fromNamespaceAndPath("create_stats","pdu_cabinet/mesh.json");
    private static final ResourceLocation MATERIALS=ResourceLocation.fromNamespaceAndPath("create_stats","textures/block/pdu_cabinet/materials.png");
    private static final int LAYERS=4, PARTS=4;
    private static List<List<Quad>> groups;

    private record Quad(int part,int blink,float[][] points,float[] normal,float[][] uv) {}
    private CabinetMesh() {}

    static void clear() { groups=null; }

    private static float[] values(JsonArray a) {
        float[] result=new float[a.size()];
        for(int i=0;i<result.length;i++)result[i]=a.get(i).getAsFloat();
        return result;
    }

    private static float[][] matrix(JsonArray a) {
        float[][] result=new float[a.size()][];
        for(int i=0;i<result.length;i++)result[i]=values(a.get(i).getAsJsonArray());
        return result;
    }

    private static int layer(JsonObject q) {
        if(q.has("glass"))return "glint".equals(q.get("glass").getAsString())?3:2;
        return q.get("live").getAsBoolean()?1:0;
    }

    private static List<List<Quad>> load() {
        if(groups!=null)return groups;
        List<List<Quad>> result=new ArrayList<>();
        for(int i=0;i<LAYERS*PARTS;i++)result.add(new ArrayList<>());
        try(var reader=new InputStreamReader(Minecraft.getInstance().getResourceManager().getResourceOrThrow(DATA).open(),StandardCharsets.UTF_8)) {
            JsonObject object=JsonParser.parseReader(reader).getAsJsonObject();
            for(var element:object.getAsJsonArray("quads")) {
                JsonObject q=element.getAsJsonObject();
                float[][] pts=matrix(q.getAsJsonArray("points"));
                for(float[] p:pts)for(int i=0;i<3;i++)p[i]/=16;
                int blink=q.has("lamp")?q.get("lamp").getAsInt():q.has("blink")?q.get("blink").getAsInt():-1;
                Quad quad=new Quad(q.get("part").getAsInt(),blink,pts,
                        values(q.getAsJsonArray("normal")),matrix(q.getAsJsonArray("uv")));
                result.get(layer(q)*PARTS+quad.part).add(quad);
            }
        } catch(java.io.IOException ex) { throw new IllegalStateException("PDU Cabinet saved mesh missing",ex); }
        return groups=result.stream().map(List::copyOf).toList();
    }

    private static RenderType type(int layer,ResourceLocation instruments) {
        return switch(layer) {
            case 0 -> RenderType.entityCutoutNoCull(MATERIALS);
            case 1 -> RenderType.entityCutoutNoCull(instruments);
            case 2 -> CabinetRenderTypes.PANE;
            default -> CabinetRenderTypes.GLINT;
        };
    }

    static void draw(PoseStack ps,MultiBufferSource buffers,int light,int overlay,
                     ResourceLocation instruments,double door,double flag,int darkLamps) {
        List<List<Quad>> mesh=load();
        for(int layer=0;layer<LAYERS;layer++) {
            boolean live=layer==1;
            VertexConsumer vc=null;
            for(int part=0;part<PARTS;part++) {
                List<Quad> posed=mesh.get(layer*PARTS+part);
                if(posed.isEmpty())continue;

                if(vc==null)vc=buffers.getBuffer(type(layer,instruments));
                ps.pushPose();
                if(part==1) {
                    double[] p=CabinetGeometry.DOOR_PIVOT;
                    ps.translate(p[0],p[1],p[2]);ps.mulPose(Axis.YP.rotationDegrees((float)door));
                    ps.translate(-p[0],-p[1],-p[2]);
                } else if(part==3) ps.translate(0,flag*.95/16,0);
                var pose=ps.last();

                for(Quad q:posed) {
                    if(q.blink>=0 && (darkLamps>>q.blink&1)!=0)continue;
                    for(int i=0;i<4;i++)vc.addVertex(pose,q.points[i][0],q.points[i][1],q.points[i][2])
                            .setColor(0xffffffff).setUv(q.uv[i][0],q.uv[i][1]).setOverlay(overlay)
                            .setLight(live?LightTexture.FULL_BRIGHT:light)
                            .setNormal(pose,live?0:q.normal[0],live?1:q.normal[1],live?0:q.normal[2]);
                }
                ps.popPose();
            }
        }
    }
}
