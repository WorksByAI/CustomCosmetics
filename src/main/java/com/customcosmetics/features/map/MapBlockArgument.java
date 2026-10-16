// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;
import com.mojang.brigadier.*;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.*;
import java.util.*;

/** Namespaced block IDs and state properties are single tokens, including ':' and '[...]'. */
public final class MapBlockArgument implements ArgumentType<String> {
    private static final SimpleCommandExceptionType INVALID=new SimpleCommandExceptionType(new LiteralMessage("Expected a block ID, such as minecraft:quartz_block"));
    @Override public String parse(StringReader reader)throws CommandSyntaxException {
        int start=reader.getCursor();while(reader.canRead()&&!Character.isWhitespace(reader.peek()))reader.skip();String token=reader.getString().substring(start,reader.getCursor());
        try{new MapEdits.Edit(new MapEdits.Position(0,0,0),token.contains(":")?token:"minecraft:"+token,false);}catch(IllegalArgumentException error){reader.setCursor(start);throw INVALID.createWithContext(reader);}return token;
    }
    @Override public Collection<String> getExamples(){return List.of("minecraft:quartz_block","dark_oak_planks","minecraft:quartz_pillar[axis=x]");}
}
