// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.gui.UiLocale;
import com.customcosmetics.sync.ServerAnnouncements;
import com.customcosmetics.sync.ServerNoticeInbox;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;

final class ServerAnnouncementGameTest {
    static void check(ClientGameTestContext context){
        var language=new java.util.concurrent.atomic.AtomicReference<String>();
        context.runOnClient(c->{
            language.set(ConfigManager.uiLanguage);ConfigManager.uiLanguage="zh_cn";UiLocale.refresh();
            try{
                Method show=ServerAnnouncements.class.getDeclaredMethod("display",ServerNoticeInbox.Notice.class);show.setAccessible(true);
                show.invoke(null,new ServerNoticeInbox.Notice("chat-fixture","chat","中文公告 /plain-text"));
                var chat=ClientUiAccess.chat(c);var messages=chat.getClass().getDeclaredField("allMessages");messages.setAccessible(true);
                Object latest=((java.util.List<?>)messages.get(chat)).getFirst();String content=((Component)latest.getClass().getMethod("content").invoke(latest)).getString();
                if(!content.contains("公告")||!content.contains("中文公告 /plain-text"))throw new AssertionError("Chat announcement missing or not localized");
                ConfigManager.uiLanguage="en_us";UiLocale.refresh();
                show.invoke(null,new ServerNoticeInbox.Notice("title-fixture","title","CustomCosmetics\nAnnouncement test"));
                var hud=c.gui.getClass().getField("hud").get(c.gui);var title=hud.getClass().getDeclaredField("title");title.setAccessible(true);var subtitle=hud.getClass().getDeclaredField("subtitle");subtitle.setAccessible(true);
                if(!((Component)title.get(hud)).getString().equals("CustomCosmetics")||!((Component)subtitle.get(hud)).getString().equals("Announcement test"))throw new AssertionError("Title and subtitle were not displayed");
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });
        context.waitTicks(12);context.takeScreenshot("server-announcement-chat-and-title");
        context.runOnClient(c->{
            try{var hud=c.gui.getClass().getField("hud").get(c.gui);hud.getClass().getMethod("clearTitles").invoke(hud);}catch(ReflectiveOperationException error){throw new AssertionError(error);}
            ConfigManager.uiLanguage=language.get();UiLocale.refresh();ClientUiAccess.chat(c).clearMessages(true);
            System.out.println("SERVER_ANNOUNCEMENT_CHAT_AND_TITLE_NATIVE_OK");
        });
    }
}
