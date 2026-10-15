// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.distribution;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModIconTest {
    @Test void publishedMetadataResolvesToADecodableSquareIcon()throws Exception{
        try(var jar=new ZipFile(System.getProperty("customcosmetics.binaryJar"))){
            var metadata=JsonParser.parseString(new String(jar.getInputStream(jar.getEntry("fabric.mod.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            String icon=metadata.get("icon").getAsString();assertEquals("assets/customcosmetics/icon.png",icon);assertNotNull(jar.getEntry(icon));
            try(var input=jar.getInputStream(jar.getEntry(icon))){var image=ImageIO.read(input);assertNotNull(image);assertEquals(image.getWidth(),image.getHeight());assertTrue(image.getWidth()>=128);}
        }
    }
}
