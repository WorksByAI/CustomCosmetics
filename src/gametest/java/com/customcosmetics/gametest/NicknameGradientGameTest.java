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
        checkSegments(context);
        RenderAssertions.nicknameTime=-1;
        System.out.println("NICKNAME_SHIMMER_GPU_OK base="+base+" left="+left+" right="+right+" smooth="+smooth);
    }

    private static void checkSegments(ClientGameTestContext context) {
        try {
            context.getInput().resizeWindow(1600,1000);
            context.runOnClient(client->{
                client.options.guiScale().set(2);ConfigManager.nickTweaksNickChanger="ToryHi";
                ConfigManager.nickTweaksGradientStyle="segmented";ConfigManager.nickTweaksGradientSegments=List.of();
                ConfigManager.nickTweaksCustomNickColorEnabled=true;ConfigManager.nickTweaksChromaEnabled=false;
                ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.COLOR));
            });context.waitTicks(3);
            context.runOnClient(client->{
                var screen=ClientUiAccess.screen(client);
                var cape=editorWidget(client,"Cape",0,com.customcosmetics.gui.PinkUi.Action.class);
                var sync=editorWidget(client,"Sync",0,com.customcosmetics.gui.PinkUi.Action.class);
                if(cape.getBottom()>sync.getY()||screen.children().indexOf(cape)>=screen.children().indexOf(sync))
                    throw new AssertionError("Cape must precede Sync in both sidebar layout and keyboard navigation");
            });context.takeScreenshot("release-cape-above-sync");
            editorClick(context,"Cape",0,com.customcosmetics.gui.PinkUi.Action.class);
            context.runOnClient(client->{if(!(ClientUiAccess.screen(client) instanceof com.customcosmetics.gui.CapeScreen))throw new AssertionError("Cape navigation opened the wrong page");ClientUiAccess.screen(client).onClose();});context.waitTicks(2);
            NicknamePresetGameTest.click(context,"Edit Gradient Segments",com.customcosmetics.gui.PinkUi.Action.class);
            editorType(context,"To",0,"4");editorType(context,"From",1,"5");
            editorType(context,"Start Color",0,"#CC66FF");editorType(context,"End Color",0,"#FF88CC");
            editorType(context,"Start Color",1,"#43D6EC");editorType(context,"End Color",1,"#549FF5");
            context.runOnClient(client->{if(!ConfigManager.nickTweaksGradientSegments.isEmpty())throw new AssertionError("Draft edits applied before Apply");});
            context.takeScreenshot("nickname-segment-editor");
            editorClick(context,"Add Segment",0,com.customcosmetics.gui.PinkUi.Action.class);
            context.runOnClient(client->{
                var screen=ClientUiAccess.screen(client);int x=editorInt(screen,"x"),w=editorInt(screen,"w"),top=editorInt(screen,"top"),bottom=editorInt(screen,"bottom");
                var max=screen.getClass().getDeclaredMethod("maxScroll");max.setAccessible(true);int range=(Integer)max.invoke(screen);
                if(range<=0)throw new AssertionError("Added segment did not make the editor scrollable");
                var info=new net.minecraft.client.input.MouseButtonInfo(com.customcosmetics.compatibility.ClientInput.MOUSE_LEFT,0);
                var press=new net.minecraft.client.input.MouseButtonEvent(x+w-13,top+2,info);if(!screen.mouseClicked(press,false))throw new AssertionError("Segment scrollbar did not capture the thumb");
                var drag=new net.minecraft.client.input.MouseButtonEvent(x+w-13,bottom-1,info);screen.mouseDragged(drag,0,bottom-top);screen.mouseReleased(drag);
                if(editorInt(screen,"scroll")!=range)throw new AssertionError("Segment scrollbar did not reach the last row");
            });context.waitTicks(2);
            editorClick(context,"Remove",2,com.customcosmetics.gui.PinkUi.Action.class);
            editorClick(context,"Apply Segments",0,com.customcosmetics.gui.PinkUi.Action.class);
            var expected=List.of(new com.customcosmetics.features.nickname.NicknameSegments.Segment(1,4,0xCC66FF,0xFF88CC),new com.customcosmetics.features.nickname.NicknameSegments.Segment(5,6,0x43D6EC,0x549FF5));
            context.runOnClient(client->{
                if(!expected.equals(ConfigManager.nickTweaksGradientSegments))throw new AssertionError("Apply lost segmented ranges or colors");
                var local=prepared(client.font.prepareText(NickRenderUtils.previewNickname(client.getUser().getName()).getVisualOrderText(),0,0,-1,false,false,0));
                assertSegments(local);
                double tory=client.font.width("Tory"),t=client.font.width("T");
                if(Math.abs(local.getFirst().effect().to()-t/tory)>1e-6)throw new AssertionError("Segment glyph widths were measured across the full name");
                if(!NickRenderUtils.visibleNickname("&d暮&r😀冬").equals("暮😀冬"))throw new AssertionError("Formatting codes or surrogate pairs changed visible positions");
                var unicode=NickRenderUtils.previewSegments("&d暮&r😀冬",List.of(new com.customcosmetics.features.nickname.NicknameSegments.Segment(2,3,0x00FF00,0x0000FF)),0,0);
                var visible=new ArrayList<Style>();unicode.getVisualOrderText().accept((index,style,cp)->{visible.add(style);return true;});
                if(visible.size()!=3||visible.get(0).getColor().getValue()!=0xFF55FF||NicknameGlyphEffect.from(visible.get(0))!=null
                    ||NicknameGlyphEffect.from(visible.get(1)).gradient().start()!=0x00FF00||NicknameGlyphEffect.from(visible.get(2)).to()!=1)
                    throw new AssertionError("Unicode ranges or explicit color priority were lost");
                var payload=com.google.gson.JsonParser.parseString("{\"authVersion\":2,\"users\":{\"12345678-1234-4234-8234-123456789abc\":{\"name\":\"Alice\",\"features\":{\"nickTweaks\":{\"enabled\":true,\"customNickColorEnabled\":true,\"nickChanger\":\"ToryHi\",\"solid\":{\"style\":\"segmented\"}}}}}}").getAsJsonObject();
                payload.getAsJsonObject("users").getAsJsonObject("12345678-1234-4234-8234-123456789abc").getAsJsonObject("features").getAsJsonObject("nickTweaks").getAsJsonObject("solid").add("segments",com.customcosmetics.features.nickname.NicknameSegments.toJson(expected));
                var apply=PresenceSync.class.getDeclaredMethod("applyPayload",String.class,boolean.class,boolean.class);apply.setAccessible(true);apply.invoke(null,payload.toString(),false,true);
                var remote=prepared(client.font.prepareText(net.minecraft.locale.Language.getInstance().getVisualOrder(NickRenderUtils.handleFormattedText(Component.literal("Alice"))),0,0,-1,false,false,0));assertSegments(remote);
                ClientUiAccess.setScreen(client,new SegmentedSampleScreen());
            });context.waitTicks(3);
            checkSegmentPixels(context);context.takeScreenshot("nickname-segmented-toryhi");
            context.runOnClient(client->ClientUiAccess.setScreen(client,new com.customcosmetics.gui.NicknameSegmentsScreen(null)));
            editorType(context,"From",1,"4");editorClick(context,"Apply Segments",0,com.customcosmetics.gui.PinkUi.Action.class);
            context.runOnClient(client->{if(!(ClientUiAccess.screen(client) instanceof com.customcosmetics.gui.NicknameSegmentsScreen)||!expected.equals(ConfigManager.nickTweaksGradientSegments))throw new AssertionError("Overlapping ranges overwrote active settings");});
            editorClick(context,"Cancel",0,com.customcosmetics.gui.PinkUi.Action.class);
            context.runOnClient(client->{if(!expected.equals(ConfigManager.nickTweaksGradientSegments))throw new AssertionError("Cancel applied the draft");});
            System.out.println("NICKNAME_SEGMENTS_GUI_GPU_LOCAL_REMOTE_UNICODE_AND_CANCEL_OK");
        }catch(Exception error){throw new AssertionError(error);}
    }
    private static void assertSegments(List<GradientGlyph> glyphs){
        if(glyphs.size()!=6)throw new AssertionError("Segmented nickname lost glyphs");
        var first=glyphs.getFirst().effect();var fourth=glyphs.get(3).effect();var fifth=glyphs.get(4).effect();var last=glyphs.getLast().effect();
        if(first.gradient()!=fourth.gradient()||first.gradient()==fifth.gradient()||fifth.gradient()!=last.gradient()
            ||first.from()!=0||fourth.to()!=1||fifth.from()!=0||last.to()!=1
            ||first.gradient().start()!=0xCC66FF||first.gradient().end()!=0xFF88CC||last.gradient().start()!=0x43D6EC||last.gradient().end()!=0x549FF5)
            throw new AssertionError("The two native gradient spans bled into each other");
    }
    private static int editorInt(Screen screen,String name)throws ReflectiveOperationException{var field=screen.getClass().getDeclaredField(name);field.setAccessible(true);return field.getInt(screen);}
    private static net.minecraft.client.gui.components.AbstractWidget editorWidget(Minecraft client,String name,int index,Class<?> type){
        return ClientUiAccess.screen(client).children().stream().filter(type::isInstance).map(child->(net.minecraft.client.gui.components.AbstractWidget)child).filter(child->child.getMessage().getString().equals(name)).toList().get(index);
    }
    private static void editorClick(ClientGameTestContext context,String name,int index,Class<?> type){
        var position=new java.util.concurrent.atomic.AtomicReference<double[]>();
        context.runOnClient(client->{var widget=editorWidget(client,name,index,type);if(!widget.visible)throw new AssertionError("Hidden segment control "+name);double scale=client.getWindow().getGuiScale();position.set(new double[]{(widget.getX()+widget.getWidth()/2.0)*scale,(widget.getY()+widget.getHeight()/2.0)*scale});});
        context.getInput().setCursorPos(position.get()[0],position.get()[1]);context.getInput().pressMouse(com.customcosmetics.compatibility.ClientInput.MOUSE_LEFT);context.waitTicks(2);
    }
    private static void editorType(ClientGameTestContext context,String name,int index,String value){
        editorClick(context,name,index,com.customcosmetics.gui.PinkUi.Field.class);
        context.runOnClient(client->{var field=(com.customcosmetics.gui.PinkUi.Field)editorWidget(client,name,index,com.customcosmetics.gui.PinkUi.Field.class);if(!field.isFocused())throw new AssertionError("Segment field did not receive focus");field.setValue("");});
        context.getInput().typeChars(value);context.waitTicks(2);
    }
    private static void checkSegmentPixels(ClientGameTestContext context){
        var result=new CompletableFuture<int[]>();
        context.runOnClient(client->{double scale=client.getWindow().getGuiScale();int left=(int)(40*scale),top=(int)(64*scale),split=left+(int)(client.font.width("Tory")*8*scale),right=left+(int)(client.font.width("ToryHi")*8*scale),bottom=top+(int)(9*8*scale);
            Screenshot.takeScreenshot(renderTarget(client),image->{try(image){int pink=0,cyan=0,wrongLeft=0,wrongRight=0;
                for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){int c=image.getPixel(x,y),r=(c>>16)&255,g=(c>>8)&255,b=c&255;if(r>150&&g<170&&b>150){if(x<split)pink++;else wrongRight++;}if(r<110&&g>150&&b>170){if(x>=split)cyan++;else wrongLeft++;}}
                result.complete(new int[]{pink,cyan,wrongLeft,wrongRight});}catch(Throwable error){result.completeExceptionally(error);}});
        });context.waitFor(client->result.isDone());var pixels=result.join();if(pixels[0]<50||pixels[1]<50||pixels[2]>5||pixels[3]>5)throw new AssertionError("Actual GPU segment colors crossed the boundary: "+java.util.Arrays.toString(pixels));
    }
    private static final class SegmentedSampleScreen extends Screen {
        SegmentedSampleScreen(){super(Component.literal("Segmented Gradients"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
            g.fill(0,0,width,height,0xFF171923);g.text(font,"Segmented Gradients",40,25,0xFFFFA6CF,false);
            g.pose().pushMatrix();g.pose().translate(40,64);g.pose().scale(8,8);g.text(font,NickRenderUtils.previewNickname(minecraft.getUser().getName()),0,0,-1,false);g.pose().popMatrix();
            g.text(font,"Tory: #CC66FF -> #FF88CC     Hi: #43D6EC -> #549FF5",40,155,0xFFBCC1D0,false);
        }
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
