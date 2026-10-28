package com.customcosmetics.gui;

import com.google.gson.JsonParser;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiShapeAssetTest {
    @Test void masksStretchInsteadOfGeneratingOneQuadPerCenterTile() throws Exception {
        for(int radius=1;radius<=16;radius++)for(String kind:new String[]{"fill_","ring_"}){
            String path="/assets/customcosmetics/textures/gui/sprites/rounded/"+kind+radius+".png";
            try(var png=getClass().getResourceAsStream(path);var metadata=getClass().getResourceAsStream(path+".mcmeta")){
                assertNotNull(png,path);assertNotNull(metadata,path);
                var image=ImageIO.read(png);
                var scaling=JsonParser.parseString(new String(metadata.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("gui").getAsJsonObject("scaling");
                assertEquals("nine_slice",scaling.get("type").getAsString());
                assertTrue(scaling.get("stretch_inner").getAsBoolean(),"Tiling the tiny center causes severe GUI ordering overhead");
                assertEquals(radius,scaling.get("border").getAsInt());
                assertEquals((radius*2+2)*4,image.getWidth());assertEquals(image.getWidth(),image.getHeight());
                assertEquals(kind.equals("fill_")?255:0,image.getRGB(image.getWidth()/2,image.getHeight()/2)>>>24);
            }
        }
    }
}
