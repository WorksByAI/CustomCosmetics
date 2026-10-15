// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;
import com.customcosmetics.compatibility.*;
import com.customcosmetics.config.*;
import com.customcosmetics.features.nickname.*;
import com.customcosmetics.sync.PresenceSync;
import com.customcosmetics.utils.NickRenderUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModernUiNicknameGameTest implements FabricClientGameTest {
    public static volatile long time;
    public static final AtomicInteger passes=new AtomicInteger(),worldPasses=new AtomicInteger();
    private static String alias="ToryHi";
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.smolpeopleMode=false;CosmeticsMode.setOffline(true);});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear");context.waitTicks(12);
            context.getInput().resizeWindow(1280,720);
            context.runOnClient(client->{
                client.options.guiScale().set(2);
                if(!ModernUiNicknameText.active(client.font))throw new AssertionError("Modern UI text engine is not active");
                ConfigManager.nickTweaksEnabled=true;ConfigManager.nickTweaksNickChanger=alias;ConfigManager.nickTweaksChromaEnabled=false;
                ConfigManager.nickTweaksCustomNickColorEnabled=false;NickRenderUtils.invalidateLocalTargetsCache();
                checkLayout(client,false);ClientUiAccess.setScreen(client,new Samples());
            });context.waitTicks(4);captureFrame(context,"modernui-custom-font-nickname-changer");
            context.runOnClient(client->{
                alias="暮冬";ConfigManager.nickTweaksNickChanger=alias;ConfigManager.nickTweaksCustomNickColorEnabled=true;
                ConfigManager.nickTweaksGradientStyle="shimmer";ConfigManager.nickTweaksGradientStartColor=0x24CFDF;ConfigManager.nickTweaksGradientEndColor=0xEA78C5;
                ConfigManager.nickTweaksShimmerColor=0xFFCF64;ConfigManager.nickTweaksShimmerHighlightColor=0xFFF0BB;ConfigManager.nickTweaksShimmerSize=.16;ConfigManager.nickTweaksShimmerInterval=2;
                NickRenderUtils.invalidateLocalTargetsCache();checkLayout(client,true);
            });time=0;context.waitTicks(3);var base=capture(context,"modernui-shimmer-base");
            time=450;context.waitTicks(3);var swept=capture(context,"modernui-shimmer-sweep");
            if(passes.get()==0||base[0]<30||base[1]<30||swept[2]<=base[2]+10)throw new AssertionError("Real Modern UI GPU text did not retain dynamic gradient/shimmer: "+Arrays.toString(base)+" -> "+Arrays.toString(swept)+" passes="+passes.get());
            context.runOnClient(client->{ConfigManager.nickTweaksCustomNickColorEnabled=false;ConfigManager.nickTweaksChromaEnabled=true;NickRenderUtils.invalidateLocalTargetsCache();checkLayout(client,false);});
            context.waitTicks(3);captureFrame(context,"modernui-chroma-custom-font");
            checkRemoteNameTag(context);
            context.runOnClient(client->{
                alias="ToryHi";ConfigManager.nickTweaksNickChanger=alias;ConfigManager.nickTweaksChromaEnabled=false;
                ConfigManager.nickTweaksCustomNickColorEnabled=true;ConfigManager.nickTweaksGradientStyle="segmented";
                ConfigManager.nickTweaksGradientSegments=List.of(new NicknameSegments.Segment(1,4,0xCC66FF,0xFF88CC),new NicknameSegments.Segment(5,6,0x43D6EC,0x549FF5));
                NickRenderUtils.invalidateLocalTargetsCache();checkLayout(client,true);ClientUiAccess.setScreen(client,new Samples());
            });context.waitTicks(3);captureFrame(context,"modernui-segmented-nickname");
            context.runOnClient(client->ClientUiAccess.setScreen(client,new com.customcosmetics.gui.CosmeticsScreen(null,com.customcosmetics.gui.CosmeticsScreen.Page.COLOR)));
            context.waitTicks(3);captureFrame(context,"modernui-nicktweaks-settings-preview");
            context.runOnClient(client->{ConfigManager.nickTweaksEnabled=false;NickRenderUtils.invalidateLocalTargetsCache();
                var plain=NickRenderUtils.handleCharSequence(ModernUiNicknameText.sequence(client.getUser().getName()));
                var text=new StringBuilder();plain.accept((i,s,c)->{text.appendCodePoint(c);return true;});if(!text.toString().equals(client.getUser().getName()))throw new AssertionError("Disabling NickTweaks did not restore the account name");
            });
            System.out.println("MODERNUI_NATIVE_CUSTOM_FONT_ALIAS_CHROMA_GRADIENT_SHIMMER_SEGMENTS_PREVIEW_AND_DISABLE_OK");
        }catch(Exception problem){throw new AssertionError(problem);}
    }
    // Capture the last completed frame. Forcing an extra render between Sodium terrain passes is invalid.
    private static void captureFrame(ClientGameTestContext context,String name){
        var saved=new CompletableFuture<Void>();
        context.runOnClient(client->Screenshot.takeScreenshot(renderTarget(client),image->{try(image){
            var path=java.nio.file.Path.of("screenshots",name+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);saved.complete(null);
        }catch(Throwable problem){saved.completeExceptionally(problem);}}));
        context.waitFor(client->saved.isDone());saved.join();
    }
    private static void checkLayout(Minecraft client,boolean gradient){
        try{
            var input=ModernUiNicknameText.sequence("prefix "+client.getUser().getName()+" suffix");
            var prepared=ModernUiNicknameText.prepare(input,client.font,-1,0);
            var engineClass=Class.forName("icyllis.modernui.mc.text.TextLayoutEngine");var engine=engineClass.getMethod("getInstance").invoke(null);
            var layout=engineClass.getMethod("lookupFormattedLayout",FormattedCharSequence.class).invoke(engine,prepared.masked());
            String visible=new String((char[])layout.getClass().getMethod("getTextBuf").invoke(layout));
            if(!visible.equals("prefix "+alias+" suffix"))throw new AssertionError("Modern UI nickname replacement failed: "+visible);
            var fonts=(Object[])layout.getClass().getMethod("getFontVector").invoke(layout);System.out.println("MODERNUI_REAL_LAYOUT_FONTS "+Arrays.toString(fonts));
            if(fonts.length==0||!Arrays.toString(fonts).contains("霞鹜文楷"))throw new AssertionError("The configured custom font was not retained");
            if(gradient&&prepared.palette().empty())throw new AssertionError("Native glyph palette was not retained");
            var state=new net.minecraft.client.renderer.state.gui.GuiTextRenderState(client.font,input,new org.joml.Matrix3x2f(),0,0,-1,0,true,false,null);
            var text=state.ensurePrepared();
            if(!text.getClass().getName().equals("icyllis.modernui.mc.text.ModernPreparedText"))throw new AssertionError("Nickname rendering stopped using Modern UI's selected font pipeline: "+text.getClass());
            if(!(text instanceof ModernUiNicknameText.Carrier carrier)||gradient&&carrier.customcosmetics$palette().empty())throw new AssertionError("GUI preparation lost the nickname palette");
            float aliasWidth=client.font.getSplitter().stringWidth(new ProcessedNicknameText(ModernUiNicknameText.sequence(alias)));
            float accountWidth=client.font.getSplitter().stringWidth(client.getUser().getName());
            if(Math.abs(accountWidth-aliasWidth)>.01)throw new AssertionError("Native font measurement did not follow nickname replacement");
        }catch(ReflectiveOperationException problem){throw new AssertionError(problem);}
    }
    private static int[] capture(ClientGameTestContext context,String name){
        var pixels=new CompletableFuture<int[]>();context.runOnClient(client->{
            Screenshot.takeScreenshot(renderTarget(client),image->{try(image){
                int[] count=new int[3];double scale=client.getWindow().getGuiScale();
                for(int y=(int)(58*scale);y<(int)(112*scale);y++)for(int x=(int)(35*scale);x<(int)(220*scale);x++){
                    int color=image.getPixel(x,y),r=color>>>16&255,g=color>>>8&255,b=color&255;
                    if(r<130&&g>140&&b>150)count[0]++;if(r>150&&g<170&&b>150)count[1]++;if(r>210&&g>160&&b<225)count[2]++;
                }pixels.complete(count);
            }catch(Throwable problem){pixels.completeExceptionally(problem);}});
        });context.waitFor(client->pixels.isDone());captureFrame(context,name);return pixels.join();
    }
    private static void checkRemoteNameTag(ClientGameTestContext context){
        context.runOnClient(client->{try{
            CosmeticsMode.setOffline(false);
            var payload=com.google.gson.JsonParser.parseString("{\"authVersion\":2,\"users\":{\"12345678-1234-4234-8234-123456789abc\":{\"name\":\"Alice\",\"features\":{\"nickTweaks\":{\"enabled\":true,\"customNickColorEnabled\":true,\"nickChanger\":\"暮冬\",\"solid\":{\"customColorStart\":\"#24CFDF\",\"customColorEnd\":\"#EA78C5\",\"style\":\"shimmer\"}}}}}}");
            var apply=PresenceSync.class.getDeclaredMethod("applyPayload",String.class,boolean.class,boolean.class);apply.setAccessible(true);apply.invoke(null,payload.toString(),false,true);
            if(PresenceSync.getChromaProfileByName("Alice")==null)throw new AssertionError("Local remote-profile fixture was not loaded");
            var player=new RemotePlayer(client.level,new com.mojang.authlib.GameProfile(UUID.fromString("12345678-1234-4234-8234-123456789abc"),"Alice"));
            player.setId(-12101);player.setPos(client.player.getX(),client.player.getY(),client.player.getZ()+3);
            client.level.addEntity(player);client.player.setYRot(0);client.player.setXRot(0);
            ClientUiAccess.chat(client).clearMessages(true);ClientUiAccess.setScreen(client,null);worldPasses.set(0);
        }catch(ReflectiveOperationException problem){throw new AssertionError(problem);}});
        context.waitTicks(12);captureFrame(context,"modernui-remote-shimmer-nametag");
        var worldPixels=new CompletableFuture<int[]>();
        context.runOnClient(client->Screenshot.takeScreenshot(renderTarget(client),image->{try(image){int cyan=0,pink=0;
            for(int y=210;y<300;y++)for(int x=560;x<720;x++){int color=image.getPixel(x,y),r=color>>>16&255,g=color>>>8&255,b=color&255;
                if(r<165&&g>r*1.12&&g>b*.8)cyan++;if(r>150&&r>g*1.3&&b>g*1.2)pink++;
            }worldPixels.complete(new int[]{cyan,pink});
        }catch(Throwable problem){worldPixels.completeExceptionally(problem);}}));
        context.waitFor(client->worldPixels.isDone());var colors=worldPixels.join();
        if(colors[0]<15||colors[1]<15)throw new AssertionError("Remote custom-font nickname lost its GPU gradient: "+Arrays.toString(colors));
        context.runOnClient(client->{
            ClientUiAccess.setScreen(client,new com.customcosmetics.gui.CosmeticsScreen(null,com.customcosmetics.gui.CosmeticsScreen.Page.NICK));
            var button=(com.customcosmetics.gui.PinkUi.Action)ClientUiAccess.screen(client).children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Action action&&action.getMessage().getString().equals("Blocked Nicknames")).findFirst().orElseThrow();
            button.onClick(null,false);
            var search=(com.customcosmetics.gui.PinkUi.Field)ClientUiAccess.screen(client).children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Field field&&field.getMessage().getString().equals("Search Nickname or Account")).findFirst().orElseThrow();
            if(!search.visible||search.getY()+search.getHeight()>ClientUiAccess.screen(client).height-26)throw new AssertionError("Nickname search must be visible without scrolling");
            search.setValue("暮冬");
            var block=(com.customcosmetics.gui.PinkUi.Action)ClientUiAccess.screen(client).children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Action action&&action.getMessage().getString().equals("Block")&&action.getWidth()==80).findFirst().orElseThrow();
            if(!block.visible||block.getY()+block.getHeight()>ClientUiAccess.screen(client).height-26)throw new AssertionError("A nickname search result must be visible without scrolling");
            block.onClick(null,false);
            if(!ConfigManager.nickTweaksBlockedPlayers.contains(new PlayerBlocklist.Entry(UUID.fromString("12345678-1234-4234-8234-123456789abc"),"Alice")))throw new AssertionError("Visible nickname search did not block the stable player identity");
            if(!NickRenderUtils.handleString("Alice").equals("Alice"))throw new AssertionError("Blocked remote nickname was still rendered");
        });context.waitTicks(3);captureFrame(context,"modernui-block-nickname-by-visible-alias");
        context.runOnClient(client->{
            var unblock=(com.customcosmetics.gui.PinkUi.Action)ClientUiAccess.screen(client).children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Action action&&action.getMessage().getString().equals("Unblock")).findFirst().orElseThrow();
            unblock.onClick(null,false);ClientUiAccess.setScreen(client,null);
            if(NickRenderUtils.handleString("Alice").equals("Alice"))throw new AssertionError("Unblocking did not invalidate the nickname cache");
        });context.waitTicks(3);captureFrame(context,"modernui-unblocked-remote-nickname-restored");
        System.out.println("NICKNAME_BLOCK_VISIBLE_ALIAS_SEARCH_STABLE_IDENTITY_LOCAL_GUI_AND_RESTORE_OK");
        context.runOnClient(client->{client.level.removeEntity(-12101,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);CosmeticsMode.setOffline(true);});
        System.out.println("MODERNUI_REMOTE_NAMETAG_NATIVE_WORLD_RENDERER_OK passes="+worldPasses.get());
    }
    private static com.mojang.blaze3d.pipeline.RenderTarget renderTarget(Minecraft client){try{
        try{return (com.mojang.blaze3d.pipeline.RenderTarget)client.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(client.gameRenderer);}
        catch(NoSuchMethodException old){return (com.mojang.blaze3d.pipeline.RenderTarget)Minecraft.class.getMethod("getMainRenderTarget").invoke(client);}
    }catch(ReflectiveOperationException problem){throw new AssertionError(problem);}}
    private static final class Samples extends Screen {
        private Samples(){super(Component.literal("Modern UI NickTweaks"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void extractRenderState(GuiGraphicsExtractor graphics,int x,int y,float delta){
            graphics.fill(0,0,width,height,0xFF171923);graphics.text(font,"Modern UI / custom font",36,24,-1,false);
            NickRenderUtils.beginPreviewOverride();try{
                graphics.pose().pushMatrix();graphics.pose().translate(36,60);graphics.pose().scale(4,4);
                graphics.text(font,minecraft.getUser().getName(),0,0,-1,false);graphics.pose().popMatrix();
                graphics.text(font,Component.literal("chat: "+minecraft.getUser().getName()+" > normal suffix").withStyle(Style.EMPTY.withBold(true).withUnderlined(true)),36,140,-1,true);
                graphics.text(font,FormattedCharSequence.forward(minecraft.getUser().getName(),Style.EMPTY),36,175,-1,true);
            }finally{NickRenderUtils.endPreviewOverride();}
            super.extractRenderState(graphics,x,y,delta);
        }
    }
}
