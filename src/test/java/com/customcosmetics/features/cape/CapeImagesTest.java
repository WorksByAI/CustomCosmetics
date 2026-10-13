// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.cape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CapeImagesTest {
    @Test void highResolutionAtlasesKeepTheirDimensionsAndElytraPixelsAcrossLocalReload() throws Exception {
        var image=new BufferedImage(2048,1024,BufferedImage.TYPE_INT_ARGB);image.setRGB(1700,600,0xFF123456);
        byte[] png=CapeImages.atlasPng(image);var store=new LocalCapeStore(directory);store.save(png);
        var restored=CapeImages.decode(new LocalCapeStore(directory).copy());
        assertEquals(2048,restored.getWidth());assertEquals(1024,restored.getHeight());assertEquals(0xFF123456,restored.getRGB(1700,600));
        assertTrue(CapeImages.atlas(new BufferedImage(640,320,BufferedImage.TYPE_INT_ARGB)));
        assertFalse(CapeImages.atlas(new BufferedImage(300,150,BufferedImage.TYPE_INT_ARGB)));
    }
    @Test void fileAndEncodedPngLimitsAllowMoreThan128KiBButNeverMoreThanTwoMiB() throws Exception {
        assertEquals(2*1024*1024,CapeImages.MAX_FILE);assertEquals(CapeImages.MAX_FILE,CapeImages.MAX_PNG);
        var random=new Random(42);var accepted=new BufferedImage(640,320,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<accepted.getHeight();y++)for(int x=0;x<accepted.getWidth();x++)accepted.setRGB(x,y,random.nextInt());
        byte[] png=CapeImages.atlasPng(accepted);assertTrue(png.length>131072);assertTrue(png.length<=CapeImages.MAX_PNG);
        var large=new BufferedImage(1024,512,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<large.getHeight();y++)for(int x=0;x<large.getWidth();x++)large.setRGB(x,y,random.nextInt());
        assertTrue(assertThrows(java.io.IOException.class,()->CapeImages.atlasPng(large)).getMessage().contains("2 MiB"));
        var oversized=directory.resolve("too-big.png");java.nio.file.Files.write(oversized,new byte[CapeImages.MAX_FILE+1]);
        assertTrue(assertThrows(java.io.IOException.class,()->CapeImages.read(oversized)).getMessage().contains("2 MiB"));
    }
    @Test void administrationUsesTheVerifiedAccountUuidRatherThanASpoofableNickname(){
        assertTrue(CapeAdministrators.includes(UUID.fromString("db78ac18-99d5-4aee-b1d7-893cfc9e358d")));
        assertEquals("ToryHi",CapeAdministrators.ACCOUNTS.get(UUID.fromString("db78ac18-99d5-4aee-b1d7-893cfc9e358d")));
        assertFalse(CapeAdministrators.includes(UUID.nameUUIDFromBytes("OfflinePlayer:ToryHi".getBytes())));
        assertFalse(CapeAdministrators.includes(null));
    }
    @TempDir Path directory;
    @Test void croppedFacesUseCorrectAtlasUvsAndCropMovementStaysInsideImage()throws Exception{
        var source=new BufferedImage(100,200,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<200;y++)for(int x=0;x<100;x++)source.setRGB(x,y,0xFF000000|x<<16|y<<8);
        var crop=CapeImages.Crop.centered(100,200);assertEquals(10.0/16,crop.width()/crop.height());
        var moved=crop.move(1000,-1000,100,200);assertEquals(0,moved.x());assertEquals(0,moved.y());
        var zoomed=crop.zoom(.5,100,200);assertEquals(50,zoomed.width());assertEquals(80,zoomed.height());
        var atlas=CapeImages.decode(CapeImages.crop(source,crop));assertEquals(512,atlas.getWidth());assertEquals(256,atlas.getHeight());
        assertEquals(source.getRGB(0,20),atlas.getRGB(8,8));assertEquals(atlas.getRGB(8,8),atlas.getRGB(96,8));
        assertEquals(atlas.getRGB(8,8),atlas.getRGB(0,8));assertEquals(0,atlas.getRGB(400,200));
        assertThrows(java.io.IOException.class,()->CapeImages.crop(source,new CapeImages.Crop(-1,0,10,16)));
    }
    @Test void nativeAtlasPreservesElytraPixelsAndLocalFileSurvivesRestart()throws Exception{
        var atlas=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);atlas.setRGB(52,20,0xFF123456);
        byte[] bytes=CapeImages.atlasPng(atlas);var store=new LocalCapeStore(directory);store.save(bytes);String hash=store.hash();
        byte[] copy=store.copy();copy[0]=0;assertNotEquals(copy[0],store.copy()[0]);
        var restored=new LocalCapeStore(directory);assertEquals(hash,restored.hash());assertEquals(0xFF123456,CapeImages.decode(restored.copy()).getRGB(52,20));
        restored.clear();assertNull(new LocalCapeStore(directory).copy());
    }
    @Test void offlineOnlyShowsLocalSelfAndReturningOnlineCannotExposeUnreviewedLocalCape(){
        UUID self=UUID.randomUUID(),other=UUID.randomUUID();var approved=Map.of(self,"approved-self",other,"approved-other");
        assertEquals("private",CapeVisibility.hash(self,self,false,true,false,"private",approved));
        assertNull(CapeVisibility.hash(other,self,false,true,false,"private",approved));
        assertEquals("approved-self",CapeVisibility.hash(self,self,true,true,false,"private",approved));
        assertEquals("approved-other",CapeVisibility.hash(other,self,true,true,false,"private",approved));
        assertNull(CapeVisibility.hash(self,self,true,true,false,"private",Map.of()));
        assertEquals("private",CapeVisibility.hash(self,self,true,true,true,"private",approved));
        assertNull(CapeVisibility.hash(self,self,true,false,true,"private",approved));
    }
    @Test void imageDecodeAndAtlasEncodingRejectBadFormatsAndDimensions()throws Exception{
        assertThrows(java.io.IOException.class,()->CapeImages.decode(new byte[CapeImages.MAX_FILE+1]));
        assertThrows(java.io.IOException.class,()->CapeImages.decode("not an image".getBytes()));
        assertThrows(java.io.IOException.class,()->CapeImages.atlasPng(new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB)));
    }
}
