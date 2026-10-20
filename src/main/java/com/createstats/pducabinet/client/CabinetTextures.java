package com.createstats.pducabinet.client;

import com.createstats.pducabinet.CabinetFace;
import com.createstats.pducabinet.CabinetReadout;
import com.createstats.pducabinet.CabinetSurface;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.IdentityHashMap;

final class CabinetTextures {
    private static final Map<Object,Entry> CACHE=new IdentityHashMap<>();
    private static final Object ITEM=new Object();
    private CabinetTextures() {}

    private static final class Entry {
        final DynamicTexture texture=new DynamicTexture(CabinetFace.ATLAS_WIDTH,CabinetFace.ATLAS_HEIGHT,true);
        final ResourceLocation location=Minecraft.getInstance().getTextureManager().register("pdu_cabinet",texture);
        CabinetReadout shown;
        long touched;
    }

    static ResourceLocation get(Object owner,CabinetReadout face) {
        Entry entry=CACHE.computeIfAbsent(owner,k->new Entry());
        entry.touched=System.currentTimeMillis();
        if (!face.equals(entry.shown)) {
            CabinetSurface pixels=CabinetFace.atlas(face);
            NativeImage image=entry.texture.getPixels();
            if (image!=null) {
                for(int y=0;y<pixels.height;y++)for(int x=0;x<pixels.width;x++) {
                    int c=pixels.pixels[y*pixels.width+x];
                    image.setPixelRGBA(x,y,(c&0xff00ff00)|((c>>16)&255)|((c&255)<<16));
                }
                entry.texture.upload();
                entry.shown=face;
            }
        }

        var it=CACHE.entrySet().iterator();
        while(it.hasNext()) {
            Entry stale=it.next().getValue();
            if (entry.touched-stale.touched>20000) {
                Minecraft.getInstance().getTextureManager().release(stale.location);it.remove();
            }
        }
        return entry.location;
    }

    static ResourceLocation item() { return get(ITEM,CabinetReadout.empty()); }

    static void clear() {
        for(Entry e:CACHE.values())Minecraft.getInstance().getTextureManager().release(e.location);
        CACHE.clear();
    }
}
