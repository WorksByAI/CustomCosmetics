package com.customcosmetics.features.skins;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class SkinImagesTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private static byte[] png(BufferedImage image)throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();}
    @Test void highResolutionSkinsKeepPixelsAndOuterAlpha()throws Exception{var image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);image.setRGB(90,100,0xff1234ef);image.setRGB(180,160,0x77223344);var result=ImageIO.read(new ByteArrayInputStream(SkinImages.normalize(png(image))));assertEquals(256,result.getWidth());assertEquals(0xff1234ef,result.getRGB(90,100));assertEquals(0x77223344,result.getRGB(180,160));assertEquals(255,result.getRGB(1,1)>>>24);}
    @Test void legacySkinsMirrorBothLimbsWithoutScaling()throws Exception{var image=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);image.setRGB(4,20,0xff112233);image.setRGB(44,20,0xffabcdef);var result=ImageIO.read(new ByteArrayInputStream(SkinImages.normalize(png(image))));assertEquals(64,result.getHeight());assertEquals(0xff112233,result.getRGB(23,52));assertEquals(0xffabcdef,result.getRGB(39,52));}
    @Test void invalidDimensionsAndOversizePayloadsAreRejectedBeforeDecode()throws Exception{assertThrows(IOException.class,()->SkinImages.normalize(png(new BufferedImage(128,64,BufferedImage.TYPE_INT_ARGB))));assertThrows(IOException.class,()->SkinImages.normalize(png(new BufferedImage(65,65,BufferedImage.TYPE_INT_ARGB))));assertThrows(IOException.class,()->SkinImages.normalize(new byte[SkinImages.MAX_BYTES+1]));assertThrows(IOException.class,()->SkinImages.normalize("not png".getBytes()));}
    @Test void highEntropyPngAboveTwoMiBImportsAndTenMiBBoundaryIsEnforced()throws Exception{
        assertEquals(10*1024*1024,SkinImages.MAX_BYTES);
        var image=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);var random=new java.util.Random(2026);
        for(int y=0;y<1024;y++)for(int x=0;x<1024;x++)image.setRGB(x,y,random.nextInt());
        byte[] input=png(image);assertTrue(input.length>2*1024*1024);
        byte[] normalized=SkinImages.normalize(input);assertEquals(1024,ImageIO.read(new ByteArrayInputStream(normalized)).getWidth());
        var path=directory.resolve("large.png");java.nio.file.Files.write(path,java.util.Arrays.copyOf(input,SkinImages.MAX_BYTES));assertTrue(SkinImages.read(path).length>2*1024*1024);
        java.nio.file.Files.write(path,new byte[]{0},java.nio.file.StandardOpenOption.APPEND);
        assertTrue(assertThrows(IOException.class,()->SkinImages.read(path)).getMessage().contains("10 MiB"));
        assertThrows(IllegalArgumentException.class,()->new SkinTransfer.Document(new byte[SkinImages.MAX_BYTES+1],false));
        assertEquals(SkinImages.MAX_BYTES,new SkinTransfer.Document(new byte[SkinImages.MAX_BYTES],false).size());
    }
}
