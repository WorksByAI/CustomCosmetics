// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.gui.UiText;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Server control-plane messages only; no polling task, tick scanner or P2P entry point. */
public final class ServerAnnouncements {
    private static final ServerNoticeInbox INBOX=new ServerNoticeInbox();
    private ServerAnnouncements(){}
    public static void accept(JsonArray notices){
        if(notices==null)return;
        for(int i=0;i<Math.min(8,notices.size());i++)if(notices.get(i).isJsonObject())accept(notices.get(i).getAsJsonObject());
    }
    public static void accept(JsonObject message){
        if(!RemoteAccess.online())return;
        var notice=INBOX.offer(message,System.currentTimeMillis());if(notice==null)return;
        long generation=RemoteAccess.generation();
        Minecraft.getInstance().execute(()->{if(RemoteAccess.current(generation)&&Minecraft.getInstance().player!=null)display(notice);});
    }
    private static void display(ServerNoticeInbox.Notice notice){
        var client=Minecraft.getInstance();
        if(notice.display().equals("title")){
            var lines=notice.message().split("\n",2);
            if(ClientUiAccess.title(client,Component.literal(lines[0]),Component.literal(lines.length==2?lines[1].replace('\n',' '):"")))return;
        }
        String label=UiText.language().equals("zh_cn")?"公告":"Announcement";
        ClientUiAccess.chat(client).addClientSystemMessage(Component.literal("[CustomCosmetics · "+label+"] ").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE).append(Component.literal(notice.message()).withStyle(net.minecraft.ChatFormatting.WHITE)));
    }
}
