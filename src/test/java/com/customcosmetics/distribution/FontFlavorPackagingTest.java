// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.distribution;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class FontFlavorPackagingTest {
    @Test void noFontKeepsTheSameClassesAndVersionButRemovesEveryBundledFont()throws Exception{
        try(var normal=new ZipFile(System.getProperty("customcosmetics.binaryJar"));var small=new ZipFile(System.getProperty("customcosmetics.noFontJar"))){
            assertTrue(normal.size()>0);assertTrue(small.size()>0);
            assertTrue(normal.stream().anyMatch(e->e.getName().endsWith(".ttf")));
            assertTrue(small.stream().noneMatch(e->e.getName().matches("(?i).*\\.(ttf|ttc|otf)$")));
            var metadata=JsonParser.parseString(text(normal,"fabric.mod.json")).getAsJsonObject();
            assertEquals(metadata,JsonParser.parseString(text(small,"fabric.mod.json")));
            for(var entry:normal.stream().filter(e->e.getName().endsWith(".class")).toList()){
                assertNotNull(small.getEntry(entry.getName()),entry.getName());
                assertArrayEquals(normal.getInputStream(entry).readAllBytes(),small.getInputStream(small.getEntry(entry.getName())).readAllBytes(),entry.getName());
            }
            for(String name:List.of("gui_chinese","gui_title")){
                String definition=text(small,"assets/customcosmetics/font/"+name+".json");assertFalse(definition.contains("ttf"));assertTrue(definition.contains("minecraft:default"));
            }
            for(String license:List.of("Baity-LGPL-3.0.txt","GPL-3.0.txt"))assertNotNull(small.getEntry("LICENSES/"+license));
        }
    }
    private static String text(ZipFile zip,String name)throws Exception{return new String(zip.getInputStream(zip.getEntry(name)).readAllBytes(),StandardCharsets.UTF_8);}
}
