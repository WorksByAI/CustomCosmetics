// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AccessoryMaterialsTest {
    @Test void detailedMaterialsHaveTheirOwnHigherResolutionAtlasWithBroadShading()throws Exception{try(var input=getClass().getResourceAsStream("/assets/customcosmetics/textures/accessories/detailed-materials.png")){assertNotNull(input);var image=ImageIO.read(input);assertEquals(256,image.getWidth());assertEquals(256,image.getHeight());var palette=new java.util.HashSet<Integer>();for(int y=0;y<256;y++)for(int x=0;x<256;x++){int p=image.getRGB(x,y),r=(p>>16)&255;assertEquals(255,p>>>24);assertEquals(r,(p>>8)&255);assertEquals(r,p&255);palette.add(r);}assertTrue(palette.size()>40);}}
    @Test void bundledAtlasUsesOpaqueCoarsePixelBlocksAndARestrainedPalette()throws Exception{
        try(var input=getClass().getResourceAsStream("/assets/customcosmetics/textures/accessories/materials.png")){
            assertNotNull(input);var image=ImageIO.read(input);assertEquals(64,image.getWidth());assertEquals(64,image.getHeight());
            for(int tile=0;tile<16;tile++){
                var palette=new java.util.HashSet<Integer>();
                for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                    int px=tile%4*16+x,py=tile/4*16+y,pixel=image.getRGB(px,py),r=(pixel>>16)&255;
                    assertEquals(255,pixel>>>24);assertEquals(r,(pixel>>8)&255);assertEquals(r,pixel&255);
                    assertEquals(image.getRGB(px-x%4,py-y%4),pixel,"Fine noise breaks the four-pixel blocks");palette.add(r);
                }
                assertTrue(palette.size()>=3&&palette.size()<=4,"Each material needs a few broad tones");
            }
        }
    }
}
