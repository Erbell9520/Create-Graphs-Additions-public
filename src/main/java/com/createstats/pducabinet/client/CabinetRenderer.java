package com.createstats.pducabinet.client;

import com.createstats.block.PduCabinetBlock;
import com.createstats.blockentity.PduCabinetBE;
import com.createstats.pducabinet.CabinetGeometry;
import com.createstats.renderer.PowerConnectorRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public final class CabinetRenderer implements BlockEntityRenderer<PduCabinetBE> {
    public CabinetRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PduCabinetBE be,float partial,PoseStack ps,MultiBufferSource buffers,int light,int overlay) {
        Direction facing=be.getBlockState().getValue(PduCabinetBlock.FACING);
        int turn=PduCabinetBlock.turn(facing);
        if(be.getLevel()!=null && be.wiresOnStuds()>0) {
            var vc=PowerConnectorRenderer.wireBuffer(buffers);
            var origin=be.getBlockPos();
            for(int i=0;i<8;i++) {
                double[] from=CabinetGeometry.stud(i,turn);
                for(var link:be.studLinks(i)) {
                    var to=PowerConnectorRenderer.attachPoint(be.getLevel(),link.to(),origin);
                    PowerConnectorRenderer.drawCable(vc,ps.last().pose(),be.getLevel(),origin,link.to(),
                            (float)from[0],(float)from[1],(float)from[2],
                            link.to().getX()-origin.getX()+(float)to.x,
                            link.to().getY()-origin.getY()+(float)to.y,
                            link.to().getZ()-origin.getZ()+(float)to.z,light,link.tier());
                }
            }
        }
        ps.pushPose();ps.translate(.5,0,.5);ps.mulPose(Axis.YP.rotationDegrees(-90*turn));ps.translate(-.5,0,-.5);
        CabinetMesh.draw(ps,buffers,light,overlay,CabinetTextures.get(be,be.readout()),be.doorAngle(partial),
                be.flagHeight(partial),be.getLevel()==null?0:blinking(be.readout(),be.getLevel().getGameTime()));
        ps.popPose();
    }

    static int blinking(com.createstats.pducabinet.CabinetReadout readout,long gameTime) {
        int mask=Math.floorMod(gameTime,20L)<5?0:1<<9;
        if(((gameTime>>3)&1)==0)return mask;
        for(int i=0;i<readout.ports().size();i++)if(readout.ports().get(i).tripped())mask|=1<<i;
        return mask;
    }

    @Override public boolean shouldRenderOffScreen(PduCabinetBE be) { return true; }

    @Override
    public AABB getRenderBoundingBox(PduCabinetBE be) {
        AABB box=new AABB(be.getBlockPos()).inflate(1.2).expandTowards(0,1,0);
        for(int i=0;i<8;i++)for(var link:be.studLinks(i))box=box.minmax(new AABB(link.to()).inflate(.5));
        return box;
    }
}
