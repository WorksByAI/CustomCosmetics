package com.customcosmetics.features.skins;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class SkinImagesTest {
    private static byte[] png(BufferedImage image)throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();}
    @Test void highResolutionSkinsKeepPixelsAndOuterAlpha()throws Exception{var image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);image.setRGB(90,100,0xff1234ef);image.setRGB(180,160,0x77223344);var result=ImageIO.read(new ByteArrayInputStream(SkinImages.normalize(png(image))));assertEquals(256,result.getWidth());assertEquals(0xff1234ef,result.getRGB(90,100));assertEquals(0x77223344,result.getRGB(180,160));assertEquals(255,result.getRGB(1,1)>>>24);}
    @Test void legacySkinsMirrorBothLimbsWithoutScaling()throws Exception{var image=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);image.setRGB(4,20,0xff112233);image.setRGB(44,20,0xffabcdef);var result=ImageIO.read(new ByteArrayInputStream(SkinImages.normalize(png(image))));assertEquals(64,result.getHeight());assertEquals(0xff112233,result.getRGB(23,52));assertEquals(0xffabcdef,result.getRGB(39,52));}
    @Test void invalidDimensionsAndOversizePayloadsAreRejectedBeforeDecode()throws Exception{assertThrows(IOException.class,()->SkinImages.normalize(png(new BufferedImage(128,64,BufferedImage.TYPE_INT_ARGB))));assertThrows(IOException.class,()->SkinImages.normalize(png(new BufferedImage(65,65,BufferedImage.TYPE_INT_ARGB))));assertThrows(IOException.class,()->SkinImages.normalize(new byte[SkinImages.MAX_BYTES+1]));assertThrows(IOException.class,()->SkinImages.normalize("not png".getBytes()));}
}
