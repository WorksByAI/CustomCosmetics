// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.nickname.*;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.sync.PresenceSync;
import com.customcosmetics.utils.NickRenderUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class NicknameGradientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;
            ConfigManager.smolpeopleMode=false;ConfigManager.resetAnimations();
        });
        try(var world=context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear");
            context.waitTicks(15);check(context,world);context.waitTicks(5);
        }
    }
    static void check(ClientGameTestContext context, TestSingleplayerContext world) {
        context.getInput().resizeWindow(1280,720);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            ConfigManager.nickTweaksEnabled=true;ConfigManager.nickTweaksCustomNickColorEnabled=true;
            ConfigManager.nickTweaksChromaEnabled=false;ConfigManager.nickTweaksNickChanger="暮冬";
            ConfigManager.nickTweaksGradientStyle="shimmer";ConfigManager.nickTweaksShimmerSpeed=1;
            ConfigManager.nickTweaksShimmerSize=.16;ConfigManager.nickTweaksShimmerInterval=2;
            ConfigManager.nickTweaksGradientStartColor=NicknameGradient.PRESET_START;
            ConfigManager.nickTweaksGradientEndColor=NicknameGradient.PRESET_END;
            NickRenderUtils.invalidateLocalTargetsCache();
            checkPreparedText(client);
            ClientUiAccess.setScreen(client,new SampleScreen());
        });
        RenderAssertions.nicknameTime=0;context.waitTicks(4);
        PixelCounts base=capture(context,"shimmer-base");
        if(base.cyan<30||base.blue<30||base.pink<30)throw new AssertionError("GPU glyphs lack the cyan/blue/pink gradient: "+base);
        RenderAssertions.nicknameTime=400;context.waitTicks(3);
        PixelCounts left=capture(context,"shimmer-left");
        RenderAssertions.nicknameTime=1000;context.waitTicks(3);
        PixelCounts right=capture(context,"shimmer-right");
        if(left.leftGold<=base.leftGold+30||right.rightGold<=base.rightGold+30)
            throw new AssertionError("The highlight did not sweep inside both real Chinese glyphs: base="+base+" left="+left+" right="+right);
        context.runOnClient(client->{
            ConfigManager.nickTweaksGradientStartColor=0;ConfigManager.nickTweaksGradientEndColor=0;
            ConfigManager.nickTweaksShimmerColor=0x00FF00;ConfigManager.nickTweaksShimmerHighlightColor=0x00FF00;
            ClientUiAccess.setScreen(client,new SampleScreen());
        });RenderAssertions.nicknameTime=1000;context.waitTicks(3);
        PixelCounts custom=capture(context,"custom-green-shimmer");
        if(custom.green<30||custom.leftGold!=0||custom.rightGold!=0)
            throw new AssertionError("Custom shimmer colors did not reach GPU glyphs: "+custom);
        context.runOnClient(client->{ConfigManager.nickTweaksShimmerSize=.48;ClientUiAccess.setScreen(client,new SampleScreen());});
        RenderAssertions.nicknameTime=Math.round(new NicknameGradient("shimmer",0,0,1,0,0,.48,2).motionMillis()/2.0);context.waitTicks(3);
        PixelCounts wide=capture(context,"wide-green-shimmer");
        if(wide.green<custom.green*1.5)throw new AssertionError("Size did not widen real GPU shimmer: narrow="+custom+" wide="+wide);
        RenderAssertions.nicknameTime=new NicknameGradient("shimmer",0,0,1,0,0,.48,2).motionMillis()+500;context.waitTicks(3);
        PixelCounts gap=capture(context,"shimmer-interval-gap");if(gap.green!=0)throw new AssertionError("Sweep persisted during its idle interval");
        context.runOnClient(client->{
            ConfigManager.nickTweaksShimmerSize=.16;ConfigManager.nickTweaksShimmerInterval=2;
            ConfigManager.nickTweaksGradientStartColor=NicknameGradient.PRESET_START;ConfigManager.nickTweaksGradientEndColor=NicknameGradient.PRESET_END;
            ConfigManager.nickTweaksShimmerColor=NicknameGradient.DEFAULT_SHIMMER;ConfigManager.nickTweaksShimmerHighlightColor=NicknameGradient.DEFAULT_HIGHLIGHT;
            ClientUiAccess.setScreen(client,new SampleScreen());
        });context.waitTicks(3);
        if(Boolean.getBoolean("customcosmetics.nicknameAnimation")) {
            for(int frame=0;frame<23;frame++) {
                RenderAssertions.nicknameTime=frame*150;context.waitTicks(2);
                capture(context,String.format("animation-%02d",frame));
            }
        }
        context.runOnClient(client->{ConfigManager.nickTweaksGradientStyle="smooth";ClientUiAccess.setScreen(client,new SampleScreen());});
        context.waitTicks(3);PixelCounts smooth=capture(context,"smooth-gradient");
        if(smooth.leftGold!=base.leftGold||smooth.rightGold!=base.rightGold)throw new AssertionError("Smooth mode unexpectedly retained the moving shine");
        context.runOnClient(client->{
            ConfigManager.nickTweaksGradientStyle="shimmer";
            ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.COLOR));
        });context.waitTicks(3);context.takeScreenshot("nickname-shimmer-settings");
        checkRemoteNameTag(context);
        NicknamePresetGameTest.check(context);
        RenderAssertions.nicknameTime=-1;
        System.out.println("NICKNAME_SHIMMER_GPU_OK base="+base+" left="+left+" right="+right+" smooth="+smooth);
    }

    private static void checkPreparedText(Minecraft client) {
        Component preview=NickRenderUtils.previewNickname(client.getUser().getName());
        if(!preview.getString().equals("暮冬"))throw new AssertionError("Nick Changer was not preserved");
        var gradients=prepared(client.font.prepareText(preview.getVisualOrderText(),0,0,0xB2FFFFFF,true,false,0));
        if(gradients.size()!=2)throw new AssertionError("A short Chinese nickname did not decorate both glyphs: "+gradients.size());
        for(var glyph:gradients) {
            var wrapped=glyph.wrapped();
            if(wrapped.textureView()!=glyph.original().textureView()||wrapped.guiPipeline()!=glyph.original().guiPipeline()
                    ||wrapped.activeRight()!=glyph.original().activeRight())throw new AssertionError("The glyph's texture or interactive bounds changed");
            if(NicknameGlyphEffect.from(wrapped.style())==null)throw new AssertionError("Style inheritance lost render metadata");
        }
        if(NicknameGlyphEffect.from(Style.EMPTY)!=null)throw new AssertionError("The shared empty style was mutated");
        var firstStyle=gradients.getFirst().original().style();
        var identical=NicknameGlyphEffect.attach(firstStyle,gradients.getFirst().effect());
        if(!firstStyle.equals(identical)||firstStyle.hashCode()!=identical.hashCode())
            throw new AssertionError("Equal render styles lost their hash/equals contract");
        if(firstStyle.equals(gradients.getLast().original().style()))
            throw new AssertionError("Distinct nickname spans became interchangeable");
        String encoded=serializedStyle(gradients.getFirst().original().style());
        if(encoded.contains("shimmer")||encoded.contains("customcosmetics")||encoded.contains("gradient"))
            throw new AssertionError("Render-only metadata entered Minecraft's serialized chat style");
        ConfigManager.nickTweaksNickChanger="Wi";
        var latin=prepared(client.font.prepareText(NickRenderUtils.previewNickname(client.getUser().getName()).getVisualOrderText(),0,0,-1,false,false,0));
        double w=client.font.getSplitter().stringWidth(net.minecraft.util.FormattedCharSequence.forward("W",Style.EMPTY));
        double i=client.font.getSplitter().stringWidth(net.minecraft.util.FormattedCharSequence.forward("i",Style.EMPTY));
        if(latin.size()!=2||Math.abs(latin.getFirst().effect().to()-w/(w+i))>1e-6)
            throw new AssertionError("Latin glyphs were colored by character count instead of actual advance");
        ConfigManager.nickTweaksNickChanger="暮";
        var single=prepared(client.font.prepareText(NickRenderUtils.previewNickname(client.getUser().getName()).getVisualOrderText(),0,0,-1,false,false,0));
        if(single.size()!=1||single.getFirst().effect().from()!=0||single.getFirst().effect().to()!=1)
            throw new AssertionError("A one-character nickname lost the full gradient");
        ConfigManager.nickTweaksGradientStartColor=0;
        ConfigManager.nickTweaksGradientEndColor=0;
        ConfigManager.nickTweaksNickChanger="Wi";
        var equalColors=NickRenderUtils.handleFormattedText(Component.literal(client.getUser().getName()));
        var equalGlyphs=prepared(client.font.prepareText(net.minecraft.locale.Language.getInstance().getVisualOrder(equalColors),0,0,-1,true,false,0));
        if(equalGlyphs.size()!=2||Math.abs(equalGlyphs.getFirst().effect().to()-w/(w+i))>1e-6
                ||equalGlyphs.getLast().effect().to()!=1)
            throw new AssertionError("Equal endpoint colors merged independently positioned shimmer glyphs");
        ConfigManager.nickTweaksGradientStartColor=NicknameGradient.PRESET_START;
        ConfigManager.nickTweaksGradientEndColor=NicknameGradient.PRESET_END;
        ConfigManager.nickTweaksNickChanger="暮冬";
        var plain=client.font.prepareText("Unrelated label",0,0,-1,false,0);
        if(!prepared(plain).isEmpty())throw new AssertionError("The gradient leaked into unrelated text");
        var raw=client.font.prepareText("Hello "+client.getUser().getName()+"!",0,0,-1,false,0);
        if(prepared(raw).size()!=2)throw new AssertionError("The raw-string chat path lost gradient metadata");
        ConfigManager.nickTweaksNickChanger="&c暮冬";
        if(!prepared(client.font.prepareText(NickRenderUtils.previewNickname(client.getUser().getName()).getVisualOrderText(),0,0,-1,false,false,0)).isEmpty())
            throw new AssertionError("An explicit color code was overridden");
        ConfigManager.nickTweaksNickChanger="暮冬";ConfigManager.nickTweaksChromaEnabled=true;
        if(!prepared(client.font.prepareText(NickRenderUtils.previewNickname(client.getUser().getName()).getVisualOrderText(),0,0,-1,false,false,0)).isEmpty())
            throw new AssertionError("Shimmer overrode Chroma priority");
        ConfigManager.nickTweaksChromaEnabled=false;
    }
    private static List<GradientGlyph> prepared(Font.PreparedText text) {
        var result=new ArrayList<GradientGlyph>();
        text.visit(new Font.GlyphVisitor(){@Override public void acceptGlyph(TextRenderable.Styled glyph){var gradient=GradientGlyph.unwrap(glyph);if(gradient!=null)result.add(gradient);}});
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String serializedStyle(Style style) {
        try {
            java.lang.reflect.Field field;
            try { field=Style.class.getField("CODEC"); }
            catch(NoSuchFieldException modern){field=Class.forName("net.minecraft.network.chat.Style$Serializer").getField("CODEC");}
            var codec=(com.mojang.serialization.Codec<Style>)field.get(null);
            return codec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,style).getOrThrow().toString();
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }

    private static void checkRemoteNameTag(ClientGameTestContext context) {
        context.runOnClient(client->{
            try {
                var payload=new com.google.gson.JsonObject();payload.addProperty("authVersion",2);
                var users=new com.google.gson.JsonObject();var user=new com.google.gson.JsonObject();user.addProperty("name","Alice");
                var features=new com.google.gson.JsonObject();var nick=new com.google.gson.JsonObject();
                nick.addProperty("enabled",true);nick.addProperty("customNickColorEnabled",true);nick.addProperty("nickChanger","暮冬");
                var solid=new com.google.gson.JsonObject();solid.addProperty("customColorStart","#24CFDF");solid.addProperty("customColorEnd","#EA78C5");
                solid.addProperty("style","shimmer");solid.addProperty("shimmerSpeed",1.7);solid.addProperty("shimmerColor","#0055FF");solid.addProperty("shimmerHighlightColor","#66DDFF");solid.addProperty("shimmerSize",.35);solid.addProperty("shimmerInterval",4.7);nick.add("solid",solid);features.add("nickTweaks",nick);user.add("features",features);
                UUID id=UUID.fromString("12345678-1234-4234-8234-123456789abc");users.add(id.toString(),user);payload.add("users",users);
                var apply=PresenceSync.class.getDeclaredMethod("applyPayload",String.class,boolean.class,boolean.class);apply.setAccessible(true);apply.invoke(null,payload.toString(),false,true);
                var profile=PresenceSync.getChromaProfileByName("Alice");
                if(profile==null||!profile.gradientStyle().equals("shimmer")||profile.shimmerSpeed()!=1.7||profile.shimmerColor()!=0x0055FF||profile.shimmerHighlightColor()!=0x66DDFF||profile.shimmerSize()!=.35||profile.shimmerInterval()!=4.7)throw new AssertionError("The remote gradient settings were lost");
                var remoteGlyphs=prepared(client.font.prepareText(net.minecraft.locale.Language.getInstance().getVisualOrder(NickRenderUtils.handleFormattedText(Component.literal("Alice"))),0,0,-1,false,false,0));
                if(remoteGlyphs.size()!=2||remoteGlyphs.getFirst().effect().gradient().shimmerColor()!=0x0055FF||remoteGlyphs.getFirst().effect().gradient().shimmerHighlightColor()!=0x66DDFF)
                    throw new AssertionError("Remote formatted nickname did not create gradient glyphs");
                var player=new RemotePlayer(client.level,new com.mojang.authlib.GameProfile(id,"Alice"));
                player.setId(-12101);player.setPos(client.player.getX(),client.player.getY(),client.player.getZ()+3);
                client.level.addEntity(player);client.player.setYRot(0);client.player.setXRot(0);
                ClientUiAccess.chat(client).clearMessages(true);ClientUiAccess.setScreen(client,null);RenderAssertions.nicknameGlyphs.set(0);
            } catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });context.waitTicks(12);context.takeScreenshot("remote-shimmer-name-tag");
        if(RenderAssertions.nicknameGlyphs.get()==0)throw new AssertionError("No gradient glyph reached the real player name tag renderer");
        context.runOnClient(client->client.level.removeEntity(-12101,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED));
    }

    private static PixelCounts capture(ClientGameTestContext context,String name) {
        CompletableFuture<PixelCounts> pixels=new CompletableFuture<>();
        context.runOnClient(client->{
            double scale=client.getWindow().getGuiScale();
            int left=(int)(40*scale),top=(int)(64*scale);
            float first=client.font.getSplitter().stringWidth(net.minecraft.util.FormattedCharSequence.forward("暮",Style.EMPTY));
            float total=client.font.getSplitter().stringWidth(net.minecraft.util.FormattedCharSequence.forward("暮冬",Style.EMPTY));
            int middle=left+(int)(first*6*scale),right=left+(int)(total*6*scale),bottom=top+(int)(9*6*scale);
            Screenshot.takeScreenshot(renderTarget(client),image->{
                try(image){
                    int cyan=0,blue=0,pink=0,leftGold=0,rightGold=0,green=0;
                    for(int y=top;y<Math.min(bottom,image.getHeight());y++)for(int x=left;x<Math.min(right,image.getWidth());x++){
                        int c=image.getPixel(x,y),r=(c>>16)&255,g=(c>>8)&255,b=c&255;
                        if(r<110&&g>150&&b>150)cyan++;
                        if(r<150&&g<160&&b>180)blue++;
                        if(r>150&&g<160&&b>150)pink++;
                        if(g>160&&r<60&&b<60)green++;
                        if(r>220&&g>180&&b<230){if(x<middle)leftGold++;else rightGold++;}
                    }
                    var version=FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
                    var path=Path.of(System.getProperty("customcosmetics.testProjectDir"),"build/audit/nickname-game",version,name+".png");
                    Files.createDirectories(path.getParent());image.writeToFile(path);
                    pixels.complete(new PixelCounts(cyan,blue,pink,leftGold,rightGold,green));
                }catch(Throwable error){pixels.completeExceptionally(error);}
            });
        });context.waitFor(client->pixels.isDone());return pixels.join();
    }
    private record PixelCounts(int cyan,int blue,int pink,int leftGold,int rightGold,int green){}

    private static com.mojang.blaze3d.pipeline.RenderTarget renderTarget(Minecraft client) {
        try {
            try { return (com.mojang.blaze3d.pipeline.RenderTarget)client.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(client.gameRenderer); }
            catch(NoSuchMethodException old) { return (com.mojang.blaze3d.pipeline.RenderTarget)Minecraft.class.getMethod("getMainRenderTarget").invoke(client); }
        } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static final class SampleScreen extends Screen {
        SampleScreen(){super(Component.literal("Nickname Gradient"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
            g.fill(0,0,width,height,0xFF171923);
            g.text(font,"Nickname Gradient",40,25,0xFFFFA6CF,false);
            g.pose().pushMatrix();g.pose().translate(40,64);g.pose().scale(6,6);
            g.text(font,NickRenderUtils.previewNickname(minecraft.getUser().getName()),0,0,-1,false);g.pose().popMatrix();
            g.text(font,"Cyan / Blue / Purple / Pink",40,140,0xFFBCC1D0,false);
        }
    }
}
