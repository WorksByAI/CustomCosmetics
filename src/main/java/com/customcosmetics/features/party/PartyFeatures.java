// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.sync.*;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class PartyFeatures{
    private static final HypixelPartyBridge API=new HypixelPartyBridge();private static final PartyListParser PARSER=new PartyListParser();
    private static volatile PartyRoster roster=PartyRoster.EMPTY;private static Object connection;private static long updated,nextRequest;private static boolean hasApi,initialized;
    private static final PartyConnectionNotice NOTICE=new PartyConnectionNotice();
    private PartyFeatures(){}
    public static boolean sharingPositions(){return ConfigManager.partyEspEnabled||ConfigManager.partyTracersEnabled||ConfigManager.sharingChamsEnabled;}
    public static boolean enabled(){return sharingPositions()||ConfigManager.partyChatEnabled||PositionSharing.enabled();}
    public static PartyRoster roster(){return roster;}
    public static void connected(boolean ready){
        NOTICE.membership(!roster.empty());
        if(NOTICE.connected(ready,ConfigManager.partyConnectionNotifications))ClientUiAccess.chat(Minecraft.getInstance()).addClientSystemMessage(PartyConnectionNotice.message());
    }
    public static void connectionFailed(){
        NOTICE.membership(!roster.empty());
        if(NOTICE.failed(ConfigManager.partyConnectionNotifications))ClientUiAccess.chat(Minecraft.getInstance()).addClientSystemMessage(PartyConnectionNotice.message(false));
    }
    public static void init(){
        if(initialized)return;initialized=true;
        hasApi=API.register(PartyFeatures.class.getClassLoader(),value->{var client=Minecraft.getInstance();var source=client.getConnection();client.execute(()->{
            if(!enabled()||!RemoteAccess.online()||source==null||source!=client.getConnection()||!PresenceWorld.isHypixel(client))return;
            var next=value;if(!value.empty()){var ids=new HashSet<>(value.members());ids.add(client.getUser().getProfileId());next=new PartyRoster(ids,value.names(),value.source());}
            roster=next;NOTICE.membership(!next.empty());updated=System.currentTimeMillis();
        });});
        ClientReceiveMessageEvents.GAME.register((message,overlay)->{
            if(overlay||!enabled()||!RemoteAccess.online())return;var client=Minecraft.getInstance();if(!PresenceWorld.isHypixel(client))return;
            String text=message.getString();if(PartyListParser.changed(text)){roster=PartyRoster.EMPTY;nextRequest=0;PARSER.reset();if(PartyListParser.leftOrJoined(text))NOTICE.membership(false);}
            if(!hasApi||System.currentTimeMillis()-updated>45000)PARSER.accept(text,System.currentTimeMillis()).filter(value->value.contains(client.getUser().getProfileId(),client.getUser().getName())).ifPresent(value->{roster=value;updated=System.currentTimeMillis();});
        });
        ClientSendMessageEvents.ALLOW_CHAT.register(message->{
            String prefix=ConfigManager.partyChatPrefix;
            if(!PartyChatRules.intercept(message,ConfigManager.partyChatEnabled,prefix))return true;
            String text=PartyChatRules.body(message,prefix);
            if(!PartyChatRules.valid(text))notice("Party IRC: enter 1–256 characters without formatting codes.");
            else PartySync.chat(text);
            return false;
        });
        PartyHighlight.init();
    }
    public static void tick(Minecraft client){
        SharingNametag.tick(client);
        init();boolean active=enabled()&&RemoteAccess.online()&&client.player!=null&&!client.hasSingleplayerServer()&&PresenceWorld.isHypixel(client);
        // A brief configuration/loading phase during a transfer is not a new party join.
        if(enabled()&&RemoteAccess.online()&&!client.hasSingleplayerServer()&&client.getCurrentServer()!=null&&(client.player==null||client.getConnection()==null)){PartySync.disconnect();return;}
        Object session=client.getConnection()==null?null:client.getConnection().getConnection();
        if(connection!=session||!active){connection=session;roster=PartyRoster.EMPTY;updated=nextRequest=0;PARSER.reset();NOTICE.membership(false);PartySync.disconnect();if(!active)return;}
        long now=System.currentTimeMillis();
        if(hasApi&&now>=nextRequest){nextRequest=now+15000;API.request();}
        if(hasApi&&roster.source().equals("Hypixel Mod API")&&now-updated>45000)roster=PartyRoster.EMPTY;
        PartySync.tick(client);
    }
    public static String membershipStatus(){return roster.empty()?(hasApi?"Waiting for Party information":"Run /p list to detect your party"):(roster.size()+" members · "+roster.source());}
    public static void notice(String text){var client=Minecraft.getInstance();client.execute(()->{if(client.player!=null)ClientUiAccess.chat(client).addClientSystemMessage(Component.literal("[CustomCosmetics] "+text).withColor(0xFFA6CF));});}
}
