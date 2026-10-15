// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;
import com.customcosmetics.compatibility.*;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.party.*;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.sync.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.RemotePlayer;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class PartyGameTest implements FabricClientGameTest{
    public static final UUID A=UUID.fromString("12345678-1234-4234-8234-123456789abc"),FAR=UUID.fromString("22345678-1234-4234-8234-123456789abc");
    public static volatile Map<UUID,PartyClient.Teammate> remote=Map.of();
    public static volatile String connectionStatus;
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.offlineMode=false;ConfigManager.partyMaxDisplayDistance=0;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear");world.getServer().runCommand("fill -4 -60 2 4 -55 2 stone");
            context.waitTicks(15);world.getServer().runCommand("tp @a 0.5 -60 -0.5 0 0");context.waitTicks(10);context.getInput().resizeWindow(1280,720);
            context.runOnClient(client->{try{
                client.options.guiScale().set(2);client.player.setYRot(0);client.player.setXRot(0);
                var player=new RemotePlayer(client.level,new com.mojang.authlib.GameProfile(A,"Alice"));player.setId(-12181);player.setPos(.5,-60,4.5);client.level.addEntity(player);
                var other=new RemotePlayer(client.level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Outsider"));other.setId(-12182);other.setPos(3.5,-60,4.5);client.level.addEntity(other);
                var field=PartyFeatures.class.getDeclaredField("roster");field.setAccessible(true);field.set(null,new PartyRoster(Set.of(A,FAR),Set.of(),"fixture"));
                remote=Map.of(FAR,new PartyClient.Teammate(FAR,"FarMate",new PartyClient.Position(1500,-60,10000,.6,1.8),Long.MAX_VALUE));
                ConfigManager.partyTracersEnabled=false;ConfigManager.partyEspEnabled=true;ConfigManager.partyEspStyle="outline";ConfigManager.partyEspOpacity=.4;ConfigManager.partyShowNames=true;ConfigManager.partyFarMarkers=true;
                var targets=PartyHighlight.targets(client);if(targets.size()!=2||targets.stream().noneMatch(t->!t.loaded()&&t.name().equals("FarMate")))throw new AssertionError("Party ESP included strangers or lost the unloaded teammate");
                ClientUiAccess.setScreen(client,null);ClientUiAccess.chat(client).clearMessages(true);
            }catch(ReflectiveOperationException problem){throw new AssertionError(problem);}});
            context.waitTicks(5);
            context.runOnClient(client->{
                ConfigManager.partyMaxDisplayDistance=10;var near=PartyHighlight.targets(client);
                if(near.size()!=1||!near.getFirst().loaded())throw new AssertionError("Distance limit did not filter the distant marker");
                ConfigManager.partyMaxDisplayDistance=1;if(!PartyHighlight.targets(client).isEmpty())throw new AssertionError("Distance limit did not filter loaded ESP and nametags");
                ConfigManager.partyMaxDisplayDistance=0;if(PartyHighlight.targets(client).size()!=2)throw new AssertionError("Unlimited range did not restore teammates");
            });int outline=capture(context,"party-outline-through-wall");
            context.runOnClient(client->ConfigManager.partyEspStyle="fill");context.waitTicks(4);int fill=capture(context,"party-fill-through-wall");
            context.runOnClient(client->ConfigManager.partyEspStyle="both");context.waitTicks(4);int both=capture(context,"party-fill-outline-and-distant-marker");
            if(outline<100||fill<=outline*2||both<outline)throw new AssertionError("Party ESP GPU fill/outline failed behind opaque stone: outline="+outline+",fill="+fill+",both="+both);
            checkNamesOnly(context);
            context.runOnClient(client->{ConfigManager.partyEspEnabled=false;ConfigManager.partyTracersEnabled=true;ConfigManager.partyTracerColor=0xFFA6CF;
                if(PartyHighlight.targets(client).size()!=2||!PartyFeatures.sharingPositions())throw new AssertionError("Tracers must work independently of ESP");});
            context.waitTicks(4);int tracers=capture(context,"party-tracers-through-wall");
            if(tracers<40)throw new AssertionError("Tracer lines were not rendered through opaque terrain: "+tracers);
            context.runOnClient(client->{ConfigManager.partyTracerColor=0x00FF00;});context.waitTicks(4);
            int recolored=capture(context,"party-tracers-green");if(recolored>=tracers*.25)throw new AssertionError("Tracer color ignored the custom setting");
            context.runOnClient(client->{ConfigManager.partyTracersEnabled=false;ConfigManager.partyEspEnabled=true;ConfigManager.partyTracerColor=0xFFA6CF;});
            checkNametagModes(context);
            context.runOnClient(client->{
                ConfigManager.partyChatEnabled=true;
                if(ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage("`hello"))throw new AssertionError("IRC escaped into vanilla chat");
                if(ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage("`"))throw new AssertionError("Empty IRC escaped into vanilla chat");
                if(!ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage("vanilla text"))throw new AssertionError("Normal chat was intercepted");
                ConfigManager.partyChatPrefix="!!";
                if(ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage("!!hello"))throw new AssertionError("Custom prefix was ignored");
                if(!ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage("`hello"))throw new AssertionError("Old prefix still intercepted input");
                ConfigManager.partyChatPrefix="";
                if(ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage("ordinary CC message"))throw new AssertionError("Empty prefix did not route ordinary chat");
                if(!ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage("/p list"))throw new AssertionError("A slash command was intercepted");
                ConfigManager.partyChatEnabled=false;
                if(!ClientSendMessageEvents.ALLOW_CHAT.invoker().allowSendChatMessage("`disabled"))throw new AssertionError("Disabled party chat intercepted input");
                ConfigManager.partyChatPrefix="`";ConfigManager.partyChatEnabled=true;
                try{
                    ClientUiAccess.chat(client).clearMessages(true);
                    var messages=net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("allMessages");messages.setAccessible(true);
                    ConfigManager.partyConnectionNotifications=false;
                    PartyFeatures.connected(false);PartyFeatures.connected(true);
                    ConfigManager.partyConnectionNotifications=true;PartyFeatures.connected(true);
                    if(!((List<?>)messages.get(ClientUiAccess.chat(client))).isEmpty())throw new AssertionError("Disabled notices appeared or enabling caused a stale notice");
                    var rosterField=PartyFeatures.class.getDeclaredField("roster");rosterField.setAccessible(true);var savedRoster=rosterField.get(null);
                    rosterField.set(null,PartyRoster.EMPTY);PartyFeatures.connected(true);rosterField.set(null,savedRoster);
                    PartyFeatures.connected(false);PartyFeatures.connected(true);PartyFeatures.connected(true);
                    if(((List<?>)messages.get(ClientUiAccess.chat(client))).size()!=1)throw new AssertionError("Connection notice was missing or repeated");
                    var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();SyncCommands.register(dispatcher);
                    if(dispatcher.execute(PartyConnectionNotice.SETTINGS_COMMAND.substring(1),null)!=1)throw new AssertionError("Party settings command failed");
                }catch(ReflectiveOperationException|com.mojang.brigadier.exceptions.CommandSyntaxException error){throw new AssertionError(error);}
            });context.waitTicks(4);
            context.runOnClient(client->{try{
                if(!(ClientUiAccess.screen(client) instanceof CosmeticsScreen screen))throw new AssertionError("Chat click did not open settings");
                var page=CosmeticsScreen.class.getDeclaredField("page");page.setAccessible(true);
                if(page.get(screen)!=CosmeticsScreen.Page.PARTY)throw new AssertionError("Chat click opened the wrong tab");
                var irc=screen.children().stream().filter(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().replaceAll(": (On|Off)$","").equals("IRC For Party Members")).findFirst();
                if(irc.isEmpty())throw new AssertionError("Party settings lost the renamed IRC toggle");
                if(screen.children().stream().noneMatch(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().startsWith("Connection Notifications")))throw new AssertionError("Party connection notice switch is missing");
                if(screen.children().stream().noneMatch(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().equals("Max Display Distance")))throw new AssertionError("Party distance editor is missing");
                if(screen.children().stream().noneMatch(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().startsWith("Tracers")))throw new AssertionError("Party tracer switch is missing");
                if(screen.children().stream().anyMatch(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().contains("P2P Connections")))throw new AssertionError("P2P switch must be in Sync DEV, not Party");
                if(screen.children().stream().noneMatch(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().equals("Tracer Color")))throw new AssertionError("Party tracer color editor is missing");
                var width=CosmeticsScreen.class.getDeclaredField("contentWidth");width.setAccessible(true);
                if(client.font.width("IRC For Party Members")>width.getInt(screen)-68)throw new AssertionError("IRC feature name does not fit its setting row");
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
            context.takeScreenshot("party-settings");
            checkSyncDev(context);
            context.runOnClient(client->{try{
                var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();SyncCommands.register(dispatcher);
                if(SyncDebug.enabled())throw new AssertionError("Debug mode must default to off");
                dispatcher.execute("customcosmetics dev on",null);if(!SyncDebug.enabled())throw new AssertionError("Debug command did not enable sampling");
                dispatcher.execute("customcosmetics dev status",null);
                dispatcher.execute("customcosmetics dev off",null);if(SyncDebug.enabled())throw new AssertionError("Debug command did not stop sampling");
                dispatcher.execute("customcosmetics dev",null);if(!SyncDebug.enabled())throw new AssertionError("Debug toggle did not enable sampling");
                dispatcher.execute("customcosmetics dev",null);if(SyncDebug.enabled())throw new AssertionError("Debug toggle did not disable sampling");
                for(String feature:List.of("accessories","nick","cape")){
                    if(dispatcher.execute("customcosmetics block add "+feature+" NativeMate",null)!=1)throw new AssertionError("Block command failed for "+feature);
                    if(dispatcher.execute("customcosmetics block remove "+feature+" NativeMate",null)!=1)throw new AssertionError("Unblock command failed for "+feature);
                }
                System.out.println("LOCAL_BLOCK_COMMAND_ADD_REMOVE_ALL_THREE_FEATURES_OK");
                System.out.println("SYNC_DEBUG_NATIVE_COMMAND_ON_OFF_STATUS_AND_LOCAL_COPY_REPORT_OK");
            }catch(com.mojang.brigadier.exceptions.CommandSyntaxException error){throw new AssertionError(error);}});
            context.runOnClient(client->{ConfigManager.partyEspEnabled=false;if(!PartyHighlight.targets(client).isEmpty())throw new AssertionError("Disabled ESP still rendered");});
            System.out.println("PARTY_NATIVE_GPU_WALL_OUTLINE_FILL_UNLOADED_MARKER_STRANGER_FILTER_CHAT_PREFIX_AND_GUI_OK outline="+outline+" fill="+fill+" both="+both);
        }
    }
    private static void checkNamesOnly(ClientGameTestContext context){
        context.runOnClient(client->{
            ConfigManager.partyEspStyle="none";
            if(PartyHighlight.targets(client).size()!=2||!PartyFeatures.sharingPositions())throw new AssertionError("Names-only must retain near and distant teammates and position sharing");
            var camera=new net.minecraft.client.renderer.state.level.CameraRenderState();camera.pos=client.player.position();camera.orientation=new org.joml.Quaternionf();camera.depthFar=256;
            var calls=new java.util.concurrent.atomic.AtomicIntegerArray(2);
            var queue=(net.minecraft.client.renderer.SubmitNodeCollector)java.lang.reflect.Proxy.newProxyInstance(PartyGameTest.class.getClassLoader(),new Class<?>[]{net.minecraft.client.renderer.SubmitNodeCollector.class},(proxy,method,args)->{
                if(method.getName().equals("submitCustomGeometry"))calls.incrementAndGet(0);
                if(method.getName().equals("submitText"))calls.incrementAndGet(1);
                return null;
            });
            for(var target:PartyHighlight.targets(client))PartyHighlight.submit(target,camera,queue,client.font);
            if(calls.get(0)!=0||calls.get(1)!=2)throw new AssertionError("None must submit names without any fill/outline geometry: "+calls.get(0)+"/"+calls.get(1));
            ConfigManager.partyShowNames=false;ConfigManager.partyFarMarkers=false;calls.set(1,0);
            PartyHighlight.submit(new PartyHighlight.Target(A,"Alice",new net.minecraft.world.phys.AABB(0,0,4,1,2,5),true),camera,queue,client.font);
            PartyHighlight.submit(new PartyHighlight.Target(FAR,"FarMate",new net.minecraft.world.phys.AABB(0,0,400,1,2,401),false),camera,queue,client.font);
            if(calls.get(0)!=0||calls.get(1)!=0)throw new AssertionError("None ignored name/marker switches");
            ConfigManager.partyShowNames=true;ConfigManager.partyFarMarkers=true;
        });
        context.waitTicks(4);capture(context,"party-none-names-only-through-wall");
        System.out.println("PARTY_NONE_NEAR_AND_FAR_NAMES_WITHOUT_BOX_GEOMETRY_OK");
    }
    private static void checkSyncDev(ClientGameTestContext context){
        context.runOnClient(client->{try{
            var field=PartyFeatures.class.getDeclaredField("roster");field.setAccessible(true);field.set(null,new PartyRoster(Set.of(A,FAR),Set.of(),"Hypixel Mod API"));
            connectionStatus="Connected\nP2P: 1\nUDP: 1\nTCP: 1";
            ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SYNC));
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
        context.waitTicks(3);
        for(boolean disabled:new boolean[]{true,false}){
            var location=new java.util.concurrent.atomic.AtomicReference<double[]>();
            context.runOnClient(client->{try{
                var screen=(CosmeticsScreen)ClientUiAccess.screen(client);
                var toggle=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Toggle widget&&widget.getMessage().getString().startsWith("Disable P2P Connections")).findFirst().orElseThrow();
                if(!disabled&&ConfigManager.partyP2pEnabled||disabled&&!ConfigManager.partyP2pEnabled)throw new AssertionError("Unexpected P2P state before click");
                var scroll=CosmeticsScreen.class.getDeclaredField("scroll");scroll.setAccessible(true);
                var top=CosmeticsScreen.class.getDeclaredField("viewTop");top.setAccessible(true);
                var max=CosmeticsScreen.class.getDeclaredMethod("maxScroll");max.setAccessible(true);
                scroll.setDouble(screen,Math.clamp(scroll.getDouble(screen)+toggle.getY()-top.getInt(screen)-62,0,(Double)max.invoke(screen)));
                var apply=CosmeticsScreen.class.getDeclaredMethod("positionControls");apply.setAccessible(true);apply.invoke(screen);
                double scale=client.getWindow().getGuiScale();location.set(new double[]{(toggle.getX()+toggle.getWidth()/2.0)*scale,(toggle.getY()+toggle.getHeight()/2.0)*scale});
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
            context.waitTicks(3);
            if(disabled)context.runOnClient(client->{
                var toggle=ClientUiAccess.screen(client).children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Toggle widget&&widget.getMessage().getString().startsWith("Disable P2P Connections")).map(child->(net.minecraft.client.gui.components.AbstractWidget)child).findFirst().orElseThrow();
                if(!toggle.getMessage().getString().endsWith(": Off"))throw new AssertionError("Disable P2P must default to off");
            });
            context.getInput().setCursorPos(location.get()[0],location.get()[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.waitTicks(3);
            context.runOnClient(client->{if(ConfigManager.partyP2pEnabled==disabled)throw new AssertionError("Disable P2P toggle is not inverted");});
        }
        context.takeScreenshot("sync-dev-connections");
        context.getInput().resizeWindow(854,480);context.waitTicks(3);context.takeScreenshot("sync-dev-connections-compact");
        context.runOnClient(client->connectionStatus=null);
        System.out.println("SYNC_DEV_INVERTED_P2P_SWITCH_AND_MULTILINE_PROTOCOL_COUNTS_OK");
    }
    private static void checkNametagModes(ClientGameTestContext context){
        context.runOnClient(client->{try{
            var field=PartyFeatures.class.getDeclaredField("roster");field.setAccessible(true);field.set(null,new PartyRoster(Set.of(FAR),Set.of(),"fixture"));
            ConfigManager.partyNametagMode="fixed";ConfigManager.partyNametagSize=1;marker(20);
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
        context.waitTicks(4);int fixedNear=capture(context,"party-nametag-fixed-near");
        context.runOnClient(client->marker(200));context.waitTicks(4);int fixedFar=capture(context,"party-nametag-fixed-far");
        context.runOnClient(client->{ConfigManager.partyNametagMode="distance";marker(20);});context.waitTicks(4);int distanceNear=capture(context,"party-nametag-distance-near");
        context.runOnClient(client->marker(200));context.waitTicks(4);int distanceFar=capture(context,"party-nametag-distance-far");
        context.runOnClient(client->{ConfigManager.partyNametagMode="fixed";ConfigManager.partyNametagSize=2;});context.waitTicks(4);int larger=capture(context,"party-nametag-size-two");
        if(fixedNear<80||fixedFar<fixedNear*.6||fixedFar>fixedNear*1.6||distanceFar>=distanceNear*.5||larger<=fixedFar*2)
            throw new AssertionError("Nametag GPU scaling failed: fixed="+fixedNear+"/"+fixedFar+", distance="+distanceNear+"/"+distanceFar+", larger="+larger);
        context.runOnClient(client->ConfigManager.partyNametagSize=1);
        System.out.println("PARTY_NAMETAG_FIXED_DISTANCE_AND_SIZE_GPU_OK fixed="+fixedNear+"/"+fixedFar+" distance="+distanceNear+"/"+distanceFar+" larger="+larger);
    }
    private static void marker(double z){remote=Map.of(FAR,new PartyClient.Teammate(FAR,"FarMate",new PartyClient.Position(.5,-60,z,.6,1.8),Long.MAX_VALUE));}
    private static int capture(ClientGameTestContext context,String name){
        var count=new CompletableFuture<Integer>();context.runOnClient(client->Screenshot.takeScreenshot(renderTarget(client),image->{try(image){int total=0;
            for(int y=150;y<600;y++)for(int x=440;x<840;x++){int color=image.getPixel(x,y),r=color>>>16&255,g=color>>>8&255,b=color&255;if(r>g*1.2&&b>g*1.05&&r>130)total++;}
            count.complete(total);
        }catch(Throwable error){count.completeExceptionally(error);}}));context.waitFor(client->count.isDone());context.takeScreenshot(name);return count.join();
    }
    private static com.mojang.blaze3d.pipeline.RenderTarget renderTarget(Minecraft client){try{
        try{return (com.mojang.blaze3d.pipeline.RenderTarget)client.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(client.gameRenderer);}
        catch(NoSuchMethodException old){return (com.mojang.blaze3d.pipeline.RenderTarget)Minecraft.class.getMethod("getMainRenderTarget").invoke(client);}
    }catch(ReflectiveOperationException error){throw new AssertionError(error);}}
}
