package com.createstats.pducabinet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class CabinetAssetsTest {
    private static final Path ASSETS=Path.of("src/main/resources/assets/create_stats");
    private static JsonObject read(Path p) throws Exception { return JsonParser.parseString(Files.readString(p)).getAsJsonObject(); }

    @Test void instrumentAndLabelGlyphTexelsHaveTheSameWorldWidthAndHeight() throws Exception {
        JsonObject mesh=read(ASSETS.resolve("pdu_cabinet/mesh.json"));

        double atlasWidth=mesh.has("live_width")?mesh.get("live_width").getAsDouble():1024;
        double atlasHeight=mesh.has("live_height")?mesh.get("live_height").getAsDouble():1024;
        for(var element:mesh.getAsJsonArray("quads")) {
            JsonObject q=element.getAsJsonObject();
            boolean live=q.get("live").getAsBoolean();
            String texture=q.get("texture").getAsString();
            if(!live && !texture.startsWith("number_") && !java.util.Set.of("outputs","bus","badge","input","hv",
                    "inside_door_legend","dispatch_porcelain").contains(texture))continue;
            var pts=q.getAsJsonArray("points");var uv=q.getAsJsonArray("uv");
            double[] world=new double[2],pixels=new double[2];
            for(int edge=0;edge<2;edge++) {
                int next=edge+1;
                for(int axis=0;axis<3;axis++) {
                    double d=pts.get(next).getAsJsonArray().get(axis).getAsDouble()-pts.get(edge).getAsJsonArray().get(axis).getAsDouble();
                    world[edge]+=d*d;
                }
                world[edge]=Math.sqrt(world[edge]);
                double dx=(uv.get(next).getAsJsonArray().get(0).getAsDouble()-uv.get(edge).getAsJsonArray().get(0).getAsDouble())*(live?atlasWidth:1024);
                double dy=(uv.get(next).getAsJsonArray().get(1).getAsDouble()-uv.get(edge).getAsJsonArray().get(1).getAsDouble())*(live?atlasHeight:1024);
                pixels[edge]=Math.hypot(dx,dy);
            }
            assertEquals(1,(world[0]/pixels[0])/(world[1]/pixels[1]),.035,q.get("element").getAsString()+" squeezed glyphs");
        }
    }
}
