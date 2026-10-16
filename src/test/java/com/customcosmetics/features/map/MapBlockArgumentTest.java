package com.customcosmetics.features.map;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapBlockArgumentTest {
    @Test void namespacesAndStatePropertiesRemainOneArgument()throws Exception{var reader=new StringReader("minecraft:quartz_pillar[axis=x] minecraft:dark_oak_planks");assertEquals("minecraft:quartz_pillar[axis=x]",new MapBlockArgument().parse(reader));assertEquals(" minecraft:dark_oak_planks",reader.getRemaining());}
    @Test void namespaceCanBeOmittedWithoutEatingSelectionScope()throws Exception{var reader=new StringReader("dark_oak_planks selection");assertEquals("dark_oak_planks",new MapBlockArgument().parse(reader));assertEquals(" selection",reader.getRemaining());}
    @Test void invalidTokensDoNotAdvanceTheErrorCursor(){var reader=new StringReader("minecraft:stone;bad");assertThrows(CommandSyntaxException.class,()->new MapBlockArgument().parse(reader));assertEquals(0,reader.getCursor());assertThrows(CommandSyntaxException.class,()->new MapBlockArgument().parse(new StringReader("")));}
}
