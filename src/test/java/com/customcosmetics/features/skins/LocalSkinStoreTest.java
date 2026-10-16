package com.customcosmetics.features.skins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class LocalSkinStoreTest {
    @TempDir Path directory;
    @Test void localSkinSurvivesRestartAndFailedImportKeepsThePreviousSkin()throws Exception{var store=new LocalSkinStore(directory);assertNull(store.load());var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);image.setRGB(8,8,0xffaabbcc);var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);byte[] saved=store.save(out.toByteArray());assertArrayEquals(saved,new LocalSkinStore(directory).load());assertThrows(IOException.class,()->store.save(new byte[20]));assertArrayEquals(saved,store.load());store.clear();assertNull(store.load());}
}
