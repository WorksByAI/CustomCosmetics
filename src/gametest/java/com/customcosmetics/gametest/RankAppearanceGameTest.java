// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.*;
import com.customcosmetics.features.nickname.*;
import com.customcosmetics.gui.*;
import com.customcosmetics.sync.PresenceSync;
import com.customcosmetics.utils.NickRenderUtils;
import com.google.gson.*;
import java.util.*;
import java.lang.management.ManagementFactory;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Real Font/GUI integration, synchronized rank snapshots and allocation checks in an isolated client. */
public final class RankAppearanceGameTest implements FabricClientGameTest {
    private static final UUID ALICE=UUID.fromString("12345678-1234-4234-8234-123456789abc");
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.offlineMode=false;FeatureModules.all(false);FeatureModules.set(FeatureModules.Feature.NICK,true);});
        try(var world=context.worldBuilder().create()){
            context.waitTicks(5);context.getInput().resizeWindow(1600,1000);
            context.runOnClient(client->{
                setWorldFixture(true);client.options.guiScale().set(2);ConfigManager.nickTweaksEnabled=false;
                ConfigManager.nickTweaksRankEnabled=true;ConfigManager.nickTweaksRankPlusColor="dark_blue";ConfigManager.nickTweaksRankType="mvp_plus";ConfigManager.nickTweaksRankTagColor="gold";
                ConfigManager.nickTweaksBlockedPlayers=List.of();ConfigManager.nickTweaksChromaEnabled=false;
                ConfigManager.nickTweaksCustomNickColorEnabled=false;ConfigManager.nickTweaksNickChanger="";
                NickRenderUtils.invalidateLocalTargetsCache();apply(snapshot(client.getUser().getProfileId(),client.getUser().getName(),false));
                String self=client.getUser().getName();
                require(plain(NickRenderUtils.handleCharSequence(Component.literal("[VIP] "+self).getVisualOrderText())).equals("[MVP+] "+self),"Self rank depends on nickname toggle");
                require(plain(NickRenderUtils.handleCharSequence(Component.literal("[VIP] Alice: hello").getVisualOrderText())).equals("[MVP+] PrettyAlice: hello"),"Remote rank did not coexist with nickname replacement");
                var combined=NickRenderUtils.handleString("[VIP] Alice: hello");
                var decoded=RankText.sequence(Component.literal(combined));
                require(plain(decoded).equals("[MVP+] PrettyAlice: hello"),"String path broke rank/nickname formatting");
                decoded.accept((i,style,cp)->{if(cp=='+')require(style.getColor().getValue()==0x0000AA,"Nickname changed rank plus color");return true;});
                ConfigManager.nickTweaksRankType="mvp_plus_plus";
                String selected=RankAppearance.apply("[MVP+] "+self);
                require(plain(RankText.sequence(Component.literal(selected))).equals("[MVP++] "+self),"Rank type change reused cached MVP+");
                int[] pluses={0};RankText.sequence(Component.literal(selected)).accept((i,style,cp)->{if(cp=='+'){pluses[0]++;require(style.getColor().getValue()==0x0000AA,"MVP++ plus colors differ");}return true;});require(pluses[0]==2,"MVP++ lost a plus");
                ConfigManager.nickTweaksRankTagColor="aqua";
                RankText.sequence(Component.literal(RankAppearance.apply("[VIP] "+self))).accept((i,style,cp)->{if(cp=='['||cp==']')require(style.getColor().getValue()==0x55FFFF,"MVP++ style reused cached gold");return true;});
                ConfigManager.nickTweaksRankTagColor="gold";ConfigManager.nickTweaksRankType="mvp_plus";
                contextCheck(client);
                ConfigManager.nickTweaksBlockedPlayers=List.of(new PlayerBlocklist.Entry(ALICE,"Alice"));NickRenderUtils.invalidateLocalTargetsCache();
                require(plain(NickRenderUtils.handleCharSequence(Component.literal("[VIP] Alice").getVisualOrderText())).equals("[VIP] Alice"),"Blocked rank still appears");
                ConfigManager.nickTweaksBlockedPlayers=List.of();apply(snapshot(client.getUser().getProfileId(),self,true));
                require(plain(NickRenderUtils.handleCharSequence(Component.literal("[MVP+] "+self).getVisualOrderText())).equals("[ዞ] "+self),"Administrator did not override self");
                require(plain(NickRenderUtils.handleCharSequence(Component.literal("[VIP] Alice").getVisualOrderText())).equals("[YOUTUBE] PrettyAlice"),"Administrator did not override remote rank");
                var staff=new net.minecraft.client.multiplayer.PlayerInfo(new com.mojang.authlib.GameProfile(ALICE,"Alice"),false);
                staff.setTabListDisplayName(Component.literal("Alice"));
                require(tab(client).getNameForDisplay(staff).getString().equals("[YOUTUBE] Alice"),"SkyBlock Tab lost the YouTube exception");
                var selfInfo=new net.minecraft.client.multiplayer.PlayerInfo(new com.mojang.authlib.GameProfile(client.getUser().getProfileId(),self),false);selfInfo.setTabListDisplayName(Component.literal(self));
                require(tab(client).getNameForDisplay(selfInfo).getString().equals("[ዞ] "+self),"SkyBlock Tab lost the current staff exception");
                require(plain(NickRenderUtils.handleCharSequence(Component.literal(self).getVisualOrderText())).equals(self),"Staff prefix leaked to a bare world name");
                ConfigManager.offlineMode=true;
                require(RankAppearance.apply("[VIP] Alice").equals("[VIP] Alice"),"Offline transition reused a remote admin rank");
                ConfigManager.offlineMode=false;
                require(RankAppearance.apply("[VIP] Alice").contains("YOUTUBE"),"Online transition did not restore the admin rank");
                apply(snapshot(client.getUser().getProfileId(),self,false));
                require(plain(NickRenderUtils.handleCharSequence(Component.literal("[VIP] "+self).getVisualOrderText())).equals("[MVP+] "+self),"Cleared admin override stayed cached");
                ClientUiAccess.setScreen(client,new Samples());
            });context.waitTicks(3);context.takeScreenshot("rank-sync-nick-and-original-colors");
            context.runOnClient(client->ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.RANK)));
            context.waitTicks(3);context.takeScreenshot("rank-page-official-palette");
            click(context,"Rank Type: [MVP+]");
            context.runOnClient(client->require(ConfigManager.nickTweaksRankType.equals("mvp_plus_plus"),"Rank type button did not select MVP++"));
            context.takeScreenshot("rank-page-mvp-plus-plus");
            click(context,"MVP++ Style: Gold");
            context.runOnClient(client->require(ConfigManager.nickTweaksRankTagColor.equals("aqua"),"MVP++ style button did not select aqua"));
            context.takeScreenshot("rank-page-mvp-plus-plus-aqua");
            context.getInput().setCursorPos(1100,750);context.getInput().scroll(-3);context.waitTicks(2);
            click(context,"Black");
            context.runOnClient(client->require(ConfigManager.nickTweaksRankPlusColor.equals("black"),"Color selection did not save"));
            context.getInput().resizeWindow(854,480);context.waitTicks(3);context.takeScreenshot("rank-page-compact");
            context.runOnClient(client->{
                ClientUiAccess.setScreen(client,null);ConfigManager.nickTweaksRankEnabled=false;
                ConfigManager.offlineMode=true;NickRenderUtils.invalidateLocalTargetsCache();
                var source=Component.literal("[VIP] Alice").getVisualOrderText();require(NickRenderUtils.handleCharSequence(source)==source,"Inactive rank did not return input");
                ConfigManager.offlineMode=false;ConfigManager.nickTweaksRankEnabled=true;
                apply(snapshot(client.getUser().getProfileId(),client.getUser().getName(),false));
                allocationCheck(source);
                setWorldFixture(false);RankAppearance.invalidate();
                require(RankAppearance.apply("[VIP] Alice").equals("[VIP] Alice"),"Rank leaked outside Hypixel");
                System.out.println("RANK_SYNC_SELF_REMOTE_ADMIN_BLOCKLIST_PALETTE_FONT_AND_DISABLE_OK");
            });
        }finally{context.runOnClient(client->setWorldFixture(false));}
    }
    private static net.minecraft.client.gui.components.PlayerTabOverlay tab(net.minecraft.client.Minecraft client){
        try{Object hud;try{hud=client.gui.getClass().getField("hud").get(client.gui);}catch(NoSuchFieldException legacy){hud=client.gui;}
            return (net.minecraft.client.gui.components.PlayerTabOverlay)hud.getClass().getMethod("getTabList").invoke(hud);
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static void contextCheck(net.minecraft.client.Minecraft client){
        var bare=Component.literal("User999").getVisualOrderText();var sender=Component.literal("User999: hello User999").getVisualOrderText();
        NickRenderUtils.beginFrame();
        require(NickRenderUtils.handleCharSequence(bare)==bare,"A bare world name acquired an MVP prefix");
        require(plain(NickRenderUtils.handleCharSequence(sender)).equals("User999: hello User999"),"Default font path inserted a rank");
        try(var scope=RankDisplayContext.enter(RankText.Context.CHAT)){
            require(plain(NickRenderUtils.handleCharSequence(sender)).equals("[MVP+] User999: hello User999"),"Chat sender/mention policy failed");
            require(plain(RankText.sequence(Component.literal(RankAppearance.apply("User999: hello")))).equals("[MVP+] User999: hello"),"Chat string policy failed");
        }
        require(plain(NickRenderUtils.handleCharSequence(sender)).equals("User999: hello User999"),"Chat frame cache leaked to the world");
        require(RankAppearance.apply("User999: hello").equals("User999: hello"),"Chat string cache leaked to the world");
        var info=new net.minecraft.client.multiplayer.PlayerInfo(new com.mojang.authlib.GameProfile(new UUID(0,1000),"User999"),false);
        info.setTabListDisplayName(Component.literal("User999"));
        var api=net.hypixel.modapi.HypixelModAPI.getInstance();
        api.handle(new net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket("lobby1",null,null,null,null));
        require(tab(client).getNameForDisplay(info).getString().equals("[MVP+] User999"),"Lobby Tab did not show a rank");
        api.handle(new net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket("mini1",net.hypixel.data.type.ServerType.valueOf("SKYBLOCK").orElseThrow(),null,"hub",null));
        require(tab(client).getNameForDisplay(info).getString().equals("User999"),"SkyBlock Tab inserted an ordinary rank");
        require(info.getTabListDisplayName().getString().equals("User999"),"Tab rendering mutated the server name");
        ClientUiAccess.chat(client).addServerSystemMessage(Component.literal("User999: chat scope and mention User999"));
        System.out.println("RANK_REAL_TAB_LOCATION_CONTEXT_AND_BARE_WORLD_OK");
    }
    private static void allocationCheck(FormattedCharSequence source){
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();long id=Thread.currentThread().threadId();
        NickRenderUtils.beginFrame();var expected=NickRenderUtils.handleCharSequence(source);
        for(int i=0;i<10000;i++)NickRenderUtils.handleCharSequence(source);
        long before=bean.getThreadAllocatedBytes(id),start=System.nanoTime();
        for(int i=0;i<100000;i++)if(NickRenderUtils.handleCharSequence(source)!=expected)throw new AssertionError("Rank frame cache missed");
        long elapsed=System.nanoTime()-start,bytes=bean.getThreadAllocatedBytes(id)-before;
        require(bytes<1_000_000,"Repeated rank text allocated excessively: "+bytes);
        var tabSource=Component.literal("User999");var expectedTab=RankAppearance.tabName(tabSource,false);
        for(int i=0;i<10000;i++)RankAppearance.tabName(tabSource,false);
        long tabBefore=bean.getThreadAllocatedBytes(id),tabStart=System.nanoTime();
        for(int i=0;i<100000;i++)if(RankAppearance.tabName(tabSource,false)!=expectedTab)throw new AssertionError("Tab name frame cache missed");
        long tabTime=System.nanoTime()-tabStart,tabBytes=bean.getThreadAllocatedBytes(id)-tabBefore;
        require(tabBytes<1_000_000,"Repeated Tab names allocated excessively: "+tabBytes);
        System.out.printf(Locale.ROOT,"RANK_TAB_NAME_100000 ns=%d bytes=%d%n",tabTime,tabBytes);
        FeatureModules.set(FeatureModules.Feature.NICK,false);
        for(int i=0;i<10000;i++)NickRenderUtils.handleCharSequence(source);
        before=bean.getThreadAllocatedBytes(id);start=System.nanoTime();
        for(int i=0;i<100000;i++)if(NickRenderUtils.handleCharSequence(source)!=source)throw new AssertionError("Disabled Nick module processed ranks");
        long disabledTime=System.nanoTime()-start,disabledBytes=bean.getThreadAllocatedBytes(id)-before;
        require(disabledBytes<100_000,"Disabled path allocated: "+disabledBytes);
        FeatureModules.set(FeatureModules.Feature.NICK,true);
        for(int i=0;i<3000;i++)RankAppearance.apply("Message "+i+" Alice");
        System.out.printf(Locale.ROOT,"RANK_PERF_1000_USERS_REPEATED_100000 ns=%d bytes=%d disabledNs=%d disabledBytes=%d%n",elapsed,bytes,disabledTime,disabledBytes);
    }
    private static JsonObject snapshot(UUID self,String name,boolean overrides){
        var root=new JsonObject();root.addProperty("authVersion",2);var users=new JsonObject();root.add("users",users);
        for(int i=0;i<1000;i++){
            UUID id=i==0?ALICE:new UUID(0,i+1);String account=i==0?"Alice":"User"+i;
            var user=new JsonObject();user.addProperty("name",account);var features=new JsonObject();var nick=new JsonObject();
            nick.addProperty("enabled",i==0);nick.addProperty("nickChanger",i==0?"PrettyAlice":"");nick.add("rank",new RankSettings(true,"dark_blue").json());
            features.add("nickTweaks",nick);user.add("features",features);users.add(id.toString(),user);
        }
        var ranks=new JsonObject();root.add("rankOverrides",ranks);
        if(overrides)for(var entry:Map.of(self,new String[]{name,"&c[ዞ]"},ALICE,new String[]{"Alice","&c[&fYOUTUBE&c]"}).entrySet()){
            var value=new JsonObject();value.addProperty("name",entry.getValue()[0]);value.addProperty("prefix",entry.getValue()[1]);ranks.add(entry.getKey().toString(),value);
        }
        return root;
    }
    private static void apply(JsonObject root){try{var method=PresenceSync.class.getDeclaredMethod("applyPayload",String.class,boolean.class);method.setAccessible(true);method.invoke(null,root.toString(),false);}catch(Exception e){throw new AssertionError(e);}}
    private static void setWorldFixture(boolean active){try{var field=Class.forName("com.customcosmetics.features.animations.PresenceWorld").getDeclaredField("customcosmetics$rankTestWorld");field.setAccessible(true);field.setBoolean(null,active);}catch(Exception e){throw new AssertionError(e);}}
    private static String plain(FormattedCharSequence text){var out=new StringBuilder();text.accept((i,s,c)->{out.appendCodePoint(c);return true;});return out.toString();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void click(ClientGameTestContext context,String name){
        var position=new double[2];context.runOnClient(client->{var widget=ClientUiAccess.screen(client).children().stream().filter(PinkUi.Action.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.getMessage().getString().equals(name)).findFirst().orElseThrow();double scale=client.getWindow().getGuiScale();position[0]=(widget.getX()+widget.getWidth()/2d)*scale;position[1]=(widget.getY()+widget.getHeight()/2d)*scale;});
        context.getInput().setCursorPos(position[0],position[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.waitTicks(2);
    }
    private static final class Samples extends Screen{
        Samples(){super(Component.literal("Ranks"));}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){
            g.fill(0,0,width,height,0xFF18191F);String self=minecraft.getUser().getName();int at=35;
            for(String text:List.of("[VIP] "+self,"[MVP+] Alice: synchronized nickname and plus color","[YOUTUBE] User123: originally another rank","User999: originally no rank")){
                g.text(font,Component.literal(text).withStyle(Style.EMPTY.withColor(0xFFFFFF)),30,at,0xFFFFFFFF,false);at+=25;
            }
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
