// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.party.PartyFeatures;
import com.customcosmetics.utils.MessageUtils;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** User-controlled local reports; credentials, pair IDs and network addresses are never included. */
public final class SyncDebug {
    private static final DebugSession SESSION=new DebugSession();
    private SyncDebug(){}
    public static boolean enabled(){return SESSION.enabled();}
    public static int toggle(){return setEnabled(!enabled());}
    public static int setEnabled(boolean value){
        SESSION.enabled(value);
        if(value){MessageUtils.sendCustomMessage(message(true,report()));tick();}
        else {MessageUtils.sendMessage("Sync debug disabled.");CustomCosmeticsClient.LOGGER.info("[Sync debug] Disabled");}
        return 1;
    }
    public static void tick(){SESSION.tick(System.nanoTime()/1000000,()->String.join("\n",report()),SyncDebug::log);}
    private static void log(String report){CustomCosmeticsClient.LOGGER.info("[Sync debug]\n{}",report);}
    public static int status(){
        var lines=report();String complete=String.join("\n",lines);
        MessageUtils.sendCustomMessage(message(enabled(),lines));log(complete);return 1;
    }
    public static List<String> report(){
        var lines=new ArrayList<String>();
        String version=FabricLoader.getInstance().getModContainer("customcosmetics").map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        lines.add("CustomCosmetics "+version+" sync debug");
        lines.add("Mode: "+(RemoteAccess.online()?"online":"offline")+"; transport="+ConfigManager.syncBlockingTransport+"; P2P="+(ConfigManager.partyP2pEnabled?"enabled":"disabled"));
        var roster=PartyFeatures.roster();lines.add(roster.title()+" "+roster.sourceLabel());
        lines.add("Position sharing="+PartyFeatures.sharingPositions()+"; blocking sharing="+(ConfigManager.swordBlockingEnabled&&ConfigManager.swordBlockingSyncEnabled&&ConfigManager.syncEnabled));
        if(roster.empty())lines.add("P2P requires a detected party.");
        if(!PartyFeatures.sharingPositions())lines.add("P2P requires Party ESP or Tracers to share positions.");
        lines.addAll(RealtimeSync.diagnostics());
        lines.add("Probe loss is an estimate for control probes, not a measurement of all game data.");
        return List.copyOf(lines);
    }
    public static MutableComponent message(boolean enabled,List<String> lines){
        var text=Component.literal("Sync debug "+(enabled?"ON":"OFF")+". Details: logs/latest.log. ");
        text.append(Component.literal("[Copy report]").withStyle(s->s.withUnderlined(true).withClickEvent(new ClickEvent.CopyToClipboard(String.join("\n",lines)))));
        for(var line:lines)if(line.startsWith("Peer ")||line.startsWith("P2P:")||line.startsWith("P2P requires")||line.startsWith("Realtime:")||line.startsWith("No peer offer"))text.append("\n"+line);
        return MessageUtils.createMessageWithPrefix(text);
    }
}
