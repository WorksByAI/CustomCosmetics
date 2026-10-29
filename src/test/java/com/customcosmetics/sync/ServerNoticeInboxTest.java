// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerNoticeInboxTest {
    private static JsonObject notice(String id,String display,String text){
        var value=new JsonObject();value.addProperty("type","announcement");value.addProperty("version",1);value.addProperty("id",id);value.addProperty("display",display);value.addProperty("message",text);value.addProperty("createdAt",1000);value.addProperty("expiresAt",121000);return value;
    }
    @Test void sameServerNoticeIsDisplayedOnceAcrossRealtimeHttpAndReconnects(){
        var inbox=new ServerNoticeInbox();var value=notice("12345678-1234-1234-1234-123456789abc","chat","公告 / 安全的纯文本 /cmd");
        assertEquals(value.get("message").getAsString(),inbox.offer(value,1000).message());assertNull(inbox.offer(value,1100));
        assertNull(inbox.offer(value,120000));assertNull(new ServerNoticeInbox().offer(value,121000));
    }
    @Test void rejectsInvalidModesOversizedTitlesControlsAndUnboundedLifetime(){
        for(var value:java.util.List.of(notice("bad","chat","text"),notice("12345678-1234-1234-1234-123456789abc","command","/warp hub"),notice("12345678-1234-1234-1234-123456789abc","title","x".repeat(121)),notice("12345678-1234-1234-1234-123456789abc","chat","a\u0000b"))){assertNull(new ServerNoticeInbox().offer(value,1000));}
        var value=notice("12345678-1234-1234-1234-123456789abc","title","Title\nSubtitle");assertNotNull(new ServerNoticeInbox().offer(value,1000));
        value.addProperty("expiresAt",1000000);assertNull(new ServerNoticeInbox().offer(value,1000));
        assertNull(new ServerNoticeInbox().offer(new JsonObject(),1000));
    }
    @Test void seenNoticesHaveABoundedCache(){
        var inbox=new ServerNoticeInbox();
        for(int i=0;i<140;i++)assertNotNull(inbox.offer(notice(String.format("12345678-1234-1234-1234-%012x",i),"chat","text"),1000));
        assertNull(inbox.offer(notice("12345678-1234-1234-1234-00000000008b","chat","text"),1001));
    }
}
