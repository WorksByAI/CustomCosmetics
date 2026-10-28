// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.gui.CosmeticsScreen.Page;
import com.customcosmetics.utils.MessageUtils;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.*;
import java.util.*;

public final class PlayerBlockCommands {
    private PlayerBlockCommands(){}
    public static LiteralArgumentBuilder<FabricClientCommandSource> command(){
        var root=ClientCommands.literal("block").executes(context->{MessageUtils.sendMessage("Usage: /customcosmetics block <add|remove> <accessories|nick|cape|skin> <nickname or account>");return 1;});
        for(boolean add:new boolean[]{true,false}){
            var action=ClientCommands.literal(add?"add":"remove");
            for(var kind:PlayerBlocks.Kind.values()){
                var type=ClientCommands.literal(kind.name().toLowerCase(Locale.ROOT)).executes(context->{CustomCosmeticsClient.requestGui(page(kind));return 1;});
                type.then(ClientCommands.argument("player",StringArgumentType.greedyString()).suggests((context,builder)->{
                    String prefix=builder.getRemainingLowerCase();var names=new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                    for(var player:PresenceSync.syncedPlayers()){names.add(player.name());names.add(player.nickname());}
                    for(var entry:PlayerBlocks.entries(kind))names.add(entry.account());
                    names.stream().filter(name->name.toLowerCase(Locale.ROOT).contains(prefix)).limit(50).forEach(builder::suggest);return builder.buildFuture();
                }).executes(context->{var result=PlayerBlocks.edit(add,kind,StringArgumentType.getString(context,"player"),PresenceSync.syncedPlayers());MessageUtils.sendMessage(result.message());return result.changed()?1:0;}));
                action.then(type);
            }root.then(action);
        }return root;
    }
    public static Page page(PlayerBlocks.Kind kind){return switch(kind){case NICK->Page.BLOCKED_NICKS;case ACCESSORIES->Page.BLOCKED_ACCESSORIES;case CAPE->Page.BLOCKED_CAPES;case SKIN->Page.BLOCKED_SKINS;};}
}
