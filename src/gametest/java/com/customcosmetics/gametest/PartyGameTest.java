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
    public static volatile boolean sharedWorld;
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.offlineMode=false;ConfigManager.partyMaxDisplayDistance=0;ConfigManager.partyNametagHideDistance=0;});
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
            checkNearNametagFilter(context);
            context.runOnClient(client->{ConfigManager.partyEspEnabled=false;ConfigManager.partyTracersEnabled=true;ConfigManager.partyTracerColor=0xFFA6CF;
                if(PartyHighlight.targets(client).size()!=2||!PartyFeatures.sharingPositions())throw new AssertionError("Tracers must work independently of ESP");});
            context.waitTicks(4);int tracers=capture(context,"party-tracers-through-wall");
            if(tracers<40)throw new AssertionError("Tracer lines were not rendered through opaque terrain: "+tracers);
            context.runOnClient(client->{ConfigManager.partyTracerColor=0x00FF00;});context.waitTicks(4);
            int recolored=capture(context,"party-tracers-green");if(recolored>=tracers*.25)throw new AssertionError("Tracer color ignored the custom setting");
            context.runOnClient(client->{ConfigManager.partyTracersEnabled=false;ConfigManager.partyEspEnabled=true;ConfigManager.partyTracerColor=0xFFA6CF;});
            checkNametagModes(context);
            checkLocationSharing(context);
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
                if(screen.children().stream().noneMatch(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().equals("Hide Names Within")))throw new AssertionError("Near nametag distance editor is missing");
                if(screen.children().stream().noneMatch(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().startsWith("Tracers")))throw new AssertionError("Party tracer switch is missing");
                if(screen.children().stream().anyMatch(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().contains("P2P Connections")))throw new AssertionError("P2P switch must be in Sync DEV, not Party");
                if(screen.children().stream().noneMatch(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getMessage().getString().equals("Tracer Color")))throw new AssertionError("Party tracer color editor is missing");
                var width=CosmeticsScreen.class.getDeclaredField("contentWidth");width.setAccessible(true);
                if(client.font.width("IRC For Party Members")>width.getInt(screen)-68)throw new AssertionError("IRC feature name does not fit its setting row");
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
            context.takeScreenshot("party-settings");
            checkNearNametagSetting(context);
            checkCompactSettings(context);
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
    private static void checkLocationSharing(ClientGameTestContext context){
        var previous=new java.util.concurrent.atomic.AtomicReference<Map<UUID,PartyClient.Teammate>>();
        var savedRoster=new java.util.concurrent.atomic.AtomicReference<PartyRoster>();
        UUID shared=UUID.fromString("62345678-1234-4234-8234-123456789abc");
        context.runOnClient(client->{try{
            if(ConfigManager.positionSharingEnabled||ConfigManager.positionSharingReceive)throw new AssertionError("Public location sharing must default off");
            previous.set(remote);var roster=PartyFeatures.class.getDeclaredField("roster");roster.setAccessible(true);savedRoster.set((PartyRoster)roster.get(null));roster.set(null,PartyRoster.EMPTY);
            var player=new RemotePlayer(client.level,new com.mojang.authlib.GameProfile(shared,"SharedMate"));player.setId(-12183);player.setPos(.5,-60,4.5);client.level.addEntity(player);
            remote=Map.of(shared,new PartyClient.Teammate(shared,"SharedMate",new PartyClient.Position(1,-60,5,.6,1.8),Long.MAX_VALUE),
                FAR,new PartyClient.Teammate(FAR,"SharedFar",new PartyClient.Position(.5,-60,400,.6,1.8),Long.MAX_VALUE));
            ConfigManager.partyEspEnabled=false;ConfigManager.partyTracersEnabled=false;
            if(!PartyHighlight.targets(client).isEmpty())throw new AssertionError("Default-off consent leaked a non-party marker");
            ConfigManager.positionSharingEnabled=true;ConfigManager.positionSharingReceive=true;
            if(!PositionSharing.audience(client).equals(PositionAudience.PARTY))throw new AssertionError("Public positions escaped the Hypixel-only restriction");
            sharedWorld=true;var targets=PartyHighlight.targets(client);
            if(targets.size()!=2||targets.stream().anyMatch(PartyHighlight.Target::party)||targets.stream().noneMatch(PartyHighlight.Target::loaded))throw new AssertionError("One-way non-party markers missing near or far target");
            var camera=new net.minecraft.client.renderer.state.level.CameraRenderState();camera.pos=client.player.position();camera.orientation=new org.joml.Quaternionf();camera.depthFar=256;
            var calls=new java.util.concurrent.atomic.AtomicIntegerArray(2);
            var queue=(net.minecraft.client.renderer.SubmitNodeCollector)java.lang.reflect.Proxy.newProxyInstance(PartyGameTest.class.getClassLoader(),new Class<?>[]{net.minecraft.client.renderer.SubmitNodeCollector.class},(proxy,method,args)->{
                if(method.getName().equals("submitCustomGeometry"))calls.incrementAndGet(0);if(method.getName().equals("submitText"))calls.incrementAndGet(1);return null;});
            ConfigManager.partyEspEnabled=true;ConfigManager.partyTracersEnabled=true;
            for(var target:targets)PartyHighlight.submit(target,camera,queue,client.font);
            if(calls.get(0)!=0||calls.get(1)!=2)throw new AssertionError("Non-party locations must draw names, without Party ESP or tracers");
            ConfigManager.partyEspEnabled=false;ConfigManager.partyTracersEnabled=false;
            ClientUiAccess.setScreen(client,null);
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
        context.waitTicks(4);
        context.runOnClient(client->{ConfigManager.partyMaxDisplayDistance=10;
            if(PartyHighlight.targets(client).size()!=1)throw new AssertionError("Shared positions ignored the distance limit");ConfigManager.partyMaxDisplayDistance=0;
        });capture(context,"shared-non-party-names-only");
        context.runOnClient(client->{
            ConfigManager.positionSharingReceive=false;if(!PartyHighlight.targets(client).isEmpty())throw new AssertionError("Opt-out retained shared names");
            ConfigManager.positionSharingEnabled=false;ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.PARTY));
        });
        context.waitTicks(3);clickSetting(context,"Share My Position");
        context.runOnClient(client->{if(!ConfigManager.positionSharingEnabled)throw new AssertionError("Share My Position switch failed");});
        clickSetting(context,"Audience: Public · Same World");
        context.runOnClient(client->{if(!ConfigManager.positionSharingMode.equals("selected"))throw new AssertionError("Audience switch failed");});
        context.takeScreenshot("location-sharing-selected-settings");
        clickSetting(context,"Selected Players: 0");
        context.runOnClient(client->{
            var screen=ClientUiAccess.screen(client);
            if(screen.children().stream().noneMatch(child->child instanceof com.customcosmetics.gui.PinkUi.Field field&&field.getMessage().getString().equals("Recipient Account Name")))throw new AssertionError("Recipient editor is missing");
        });
        context.takeScreenshot("selected-location-recipients-empty");
        context.runOnClient(client->{try{
            var screen=(CosmeticsScreen)ClientUiAccess.screen(client);
            var field=(com.customcosmetics.gui.PinkUi.Field)screen.children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Field f&&f.getMessage().getString().equals("Recipient Account Name")).findFirst().orElseThrow();field.setValue("NativeMate");
        }catch(RuntimeException error){throw new AssertionError(error);}});
        clickSetting(context,"Select");
        context.runOnClient(client->{if(ConfigManager.positionSharingTargets.size()!=1||!ConfigManager.positionSharingTargets.getFirst().account().equals("NativeMate"))throw new AssertionError("Manual recipient was not saved");});
        context.takeScreenshot("selected-location-recipients-saved");clickSetting(context,"Remove");
        context.runOnClient(client->{try{
            if(!ConfigManager.positionSharingTargets.isEmpty())throw new AssertionError("Recipient removal failed");
            ConfigManager.positionSharingEnabled=false;ConfigManager.positionSharingReceive=false;sharedWorld=false;ConfigManager.positionSharingMode="public";
            remote=previous.get();var roster=PartyFeatures.class.getDeclaredField("roster");roster.setAccessible(true);roster.set(null,savedRoster.get());
            client.level.removeEntity(-12183,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);ConfigManager.partyEspEnabled=true;ConfigManager.partyTracersEnabled=false;
            ClientUiAccess.setScreen(client,null);
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
        System.out.println("LOCATION_SHARING_DEFAULT_OFF_HYPIXEL_ONLY_NEAR_FAR_NAMES_DISTANCE_CONSENT_AND_NATIVE_RECIPIENT_EDITOR_OK");
    }
    private static void clickSetting(ClientGameTestContext context,String label){
        var location=new java.util.concurrent.atomic.AtomicReference<double[]>();
        context.runOnClient(client->{try{
            var screen=(CosmeticsScreen)ClientUiAccess.screen(client);var widget=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream().filter(child->child instanceof net.minecraft.client.gui.components.AbstractWidget w&&(w.getMessage().getString().equals(label)||w.getMessage().getString().replaceAll(": (On|Off)$","").equals(label))).findFirst().orElseThrow(()->new AssertionError("Missing setting: "+label));
            var scroll=CosmeticsScreen.class.getDeclaredField("scroll");scroll.setAccessible(true);var top=CosmeticsScreen.class.getDeclaredField("viewTop");top.setAccessible(true);var max=CosmeticsScreen.class.getDeclaredMethod("maxScroll");max.setAccessible(true);
            scroll.setDouble(screen,Math.clamp(scroll.getDouble(screen)+widget.getY()-top.getInt(screen)-52,0,(Double)max.invoke(screen)));var apply=CosmeticsScreen.class.getDeclaredMethod("positionControls");apply.setAccessible(true);apply.invoke(screen);
            double scale=client.getWindow().getGuiScale();location.set(new double[]{(widget.getX()+widget.getWidth()/2.0)*scale,(widget.getY()+widget.getHeight()/2.0)*scale});
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
        context.waitTicks(2);context.getInput().setCursorPos(location.get()[0],location.get()[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.waitTicks(3);
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
    private static void checkNearNametagFilter(ClientGameTestContext context){
        var previous=new java.util.concurrent.atomic.AtomicReference<Map<UUID,PartyClient.Teammate>>();
        context.runOnClient(client->{
            var feet=client.player.position();
            var nearBox=new net.minecraft.world.phys.AABB(feet.x-.3,feet.y,feet.z+4.7,feet.x+.3,feet.y+1.8,feet.z+5.3);
            var farBox=nearBox.move(0,0,100);
            var near=new PartyHighlight.Target(A,"Alice",nearBox,true);
            var far=new PartyHighlight.Target(FAR,"FarMate",farBox,false);
            var camera=new net.minecraft.client.renderer.state.level.CameraRenderState();camera.pos=feet;camera.orientation=new org.joml.Quaternionf();camera.depthFar=256;
            var calls=new java.util.concurrent.atomic.AtomicIntegerArray(2);
            var queue=(net.minecraft.client.renderer.SubmitNodeCollector)java.lang.reflect.Proxy.newProxyInstance(PartyGameTest.class.getClassLoader(),new Class<?>[]{net.minecraft.client.renderer.SubmitNodeCollector.class},(proxy,method,args)->{
                if(method.getName().equals("submitCustomGeometry"))calls.incrementAndGet(0);if(method.getName().equals("submitText"))calls.incrementAndGet(1);return null;});
            ConfigManager.partyNametagHideDistance=8;ConfigManager.partyEspStyle="outline";ConfigManager.partyTracersEnabled=true;
            PartyHighlight.submit(near,camera,queue,client.font);
            if(calls.get(0)!=2||calls.get(1)!=0)throw new AssertionError("Near name filtering removed ESP/tracers or retained the label");
            calls.set(0,0);PartyHighlight.submit(far,camera,queue,client.font);
            if(calls.get(0)!=1||calls.get(1)!=1)throw new AssertionError("Near name filtering removed the distant marker or tracer");
            calls.set(0,0);calls.set(1,0);camera.pos=feet.add(100,0,0);
            PartyHighlight.submit(near,camera,queue,client.font);
            if(calls.get(1)!=0)throw new AssertionError("A third-person camera changed the player-to-player hide distance");
            calls.set(0,0);PartyHighlight.submit(new PartyHighlight.Target(A,"SharedNear",nearBox,true,false),camera,queue,client.font);
            if(calls.get(0)!=0||calls.get(1)!=0)throw new AssertionError("Near shared names ignored the hide threshold");
            PartyHighlight.submit(new PartyHighlight.Target(FAR,"SharedFar",farBox,false,false),camera,queue,client.font);
            if(calls.get(0)!=0||calls.get(1)!=1)throw new AssertionError("Distant shared names were hidden");
            ConfigManager.partyNametagHideDistance=0;calls.set(1,0);PartyHighlight.submit(near,camera,queue,client.font);
            if(calls.get(1)!=1)throw new AssertionError("Zero did not restore the nearby name");
            ConfigManager.partyNametagHideDistance=8;
            if(PartyHighlight.targets(client).size()!=2)throw new AssertionError("Name hiding removed targets used by ESP and tracers");
            previous.set(remote);remote=Map.of();ConfigManager.partyEspStyle="none";ConfigManager.partyTracersEnabled=false;ConfigManager.partyNametagHideDistance=0;
        });
        context.waitTicks(4);int visible=capture(context,"party-near-name-visible");
        context.runOnClient(client->ConfigManager.partyNametagHideDistance=8);
        context.waitTicks(4);int hidden=capture(context,"party-near-name-hidden");
        if(visible<30||hidden>=visible*.2)throw new AssertionError("Near nametag GPU filtering failed: visible="+visible+",hidden="+hidden);
        context.runOnClient(client->{remote=previous.get();ConfigManager.partyNametagHideDistance=0;});
        System.out.println("PARTY_NEAR_NAMETAG_PLAYER_DISTANCE_SHARED_MARKERS_BOXES_TRACERS_AND_GPU_OK visible="+visible+" hidden="+hidden);
    }
    private static void checkNearNametagSetting(ClientGameTestContext context){
        clickSetting(context,"Hide Names Within");
        context.runOnClient(client->{
            var field=(com.customcosmetics.gui.PinkUi.Field)ClientUiAccess.screen(client).children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Field f&&f.getMessage().getString().equals("Hide Names Within")).findFirst().orElseThrow();
            if(!field.isFocused())throw new AssertionError("Near nametag field did not receive focus");field.setValue("");
        });
        context.getInput().typeChars("12");context.waitTicks(2);
        context.runOnClient(client->{
            if(ConfigManager.partyNametagHideDistance!=12)throw new AssertionError("Near nametag input did not update the local setting");
            var field=(com.customcosmetics.gui.PinkUi.Field)ClientUiAccess.screen(client).children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Field f&&f.getMessage().getString().equals("Hide Names Within")).findFirst().orElseThrow();
            for(String invalid:List.of("-1","abc","30000001")){field.setValue(invalid);if(ConfigManager.partyNametagHideDistance!=12)throw new AssertionError("Invalid near nametag distance was applied: "+invalid);}
            field.setValue("12");
        });context.takeScreenshot("party-near-name-distance-setting");
        context.runOnClient(client->{
            var field=(com.customcosmetics.gui.PinkUi.Field)ClientUiAccess.screen(client).children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Field f&&f.getMessage().getString().equals("Hide Names Within")).findFirst().orElseThrow();field.setValue("0");
            if(ConfigManager.partyNametagHideDistance!=0)throw new AssertionError("Zero could not disable the near nametag filter");
        });
        System.out.println("PARTY_NEAR_NAMETAG_NATIVE_INPUT_VALIDATION_AND_ZERO_DISABLE_OK");
    }
    private static void checkCompactSettings(ClientGameTestContext context){
        var style=new java.util.concurrent.atomic.AtomicReference<String>();context.runOnClient(client->{style.set(ConfigManager.nickTweaksGradientStyle);ConfigManager.nickTweaksGradientStyle="shimmer";});
        for(boolean compact:new boolean[]{false,true}){
            context.getInput().resizeWindow(compact?854:1600,compact?480:1000);context.waitTicks(3);
            for(var page:List.of(CosmeticsScreen.Page.SIZE,CosmeticsScreen.Page.NICK,CosmeticsScreen.Page.CHROMA,CosmeticsScreen.Page.COLOR,CosmeticsScreen.Page.ANIMATIONS,CosmeticsScreen.Page.PARTY,CosmeticsScreen.Page.SYNC,CosmeticsScreen.Page.ITEMS)){
                context.runOnClient(client->{try{
                    ClientUiAccess.setScreen(client,new CosmeticsScreen(null,page));var screen=(CosmeticsScreen)ClientUiAccess.screen(client);
                    int x=(Integer)uiField(screen,"contentX"),width=(Integer)uiField(screen,"contentWidth");
                    var names=List.of("Animations","Cape","Equipments (Beta)","Item Customization","NickTweaks","Player Size","Sharing");
                    var navigation=screen.children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Action widget&&names.contains(widget.getMessage().getString())).map(child->(net.minecraft.client.gui.components.AbstractWidget)child).sorted(Comparator.comparingInt(net.minecraft.client.gui.components.AbstractWidget::getY)).toList();
                    if(!navigation.stream().map(widget->widget.getMessage().getString()).toList().equals(names))throw new AssertionError("Sidebar is not sorted A-Z");
                    for(var nav:navigation)if(client.font.width(nav.getMessage())>nav.getWidth()-12)throw new AssertionError("Sidebar label is truncated: "+nav.getMessage().getString());
                    var settings=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Action widget&&widget.getMessage().getString().equals("Settings")).findFirst().orElseThrow();
                    var github=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream().filter(com.customcosmetics.gui.RepositoryButton.class::isInstance).findFirst().orElseThrow();
                    if(settings.getX()<github.getRight()||settings.getY()!=github.getY()||client.font.width("Settings")>settings.getWidth()-12)throw new AssertionError("Settings is not readable beside GitHub");
                    boolean mode=screen.children().stream().anyMatch(child->child instanceof com.customcosmetics.gui.PinkUi.Action widget&&widget.getMessage().getString().startsWith("Mode: "));
                    if(mode!=(page==CosmeticsScreen.Page.SYNC))throw new AssertionError("Online/offline mode must only appear in Settings");
                    if(page==CosmeticsScreen.Page.SYNC){
                        var auth=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Field field&&field.getMessage().getString().equals("Proxy Auth")).findFirst().orElseThrow();
                        var disable=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Toggle toggle&&toggle.getMessage().getString().startsWith("Disable P2P Connections")).findFirst().orElseThrow();
                        var port=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Field field&&field.getMessage().getString().equals("P2P UDP Port")).findFirst().orElseThrow();
                        if(auth.getBottom()>=disable.getY()||disable.getBottom()>=port.getY())throw new AssertionError("P2P controls must follow Proxy Auth, in order");
                    }
                    var sliders=screen.children().stream().filter(com.customcosmetics.gui.PinkUi.Slider.class::isInstance).map(com.customcosmetics.gui.PinkUi.Slider.class::cast).toList();
                    for(var slider:sliders)if(slider.getX()<x||slider.getRight()>x+width||!compact&&slider.getWidth()>width*.6)throw new AssertionError("Slider did not use the available grid: "+page);
                    if(compact&&sliders.stream().map(net.minecraft.client.gui.components.AbstractWidget::getY).distinct().count()!=sliders.size())throw new AssertionError("Narrow window did not stack sliders: "+page);
                    if(!compact&&sliders.size()>1&&sliders.get(0).getY()!=sliders.get(1).getY())throw new AssertionError("Wide window did not pair related sliders: "+page);
                    if(page==CosmeticsScreen.Page.SIZE){
                        if(screen.children().stream().anyMatch(child->child instanceof com.customcosmetics.gui.PinkUi.Action&&!(child instanceof com.customcosmetics.gui.PinkUi.Toggle)&&((net.minecraft.client.gui.components.AbstractWidget)child).getMessage().getString().equals("SmolPeople")))throw new AssertionError("SmolPeople still has a navigation entry");
                        if(screen.children().stream().noneMatch(child->child instanceof com.customcosmetics.gui.PinkUi.Toggle toggle&&toggle.getMessage().getString().startsWith("SmolPeople")))throw new AssertionError("Combined Player Size page lost the SmolPeople switch");
                    }
                }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
                context.waitTicks(2);context.takeScreenshot("compact-studio-"+(compact?"narrow-":"wide-")+page.name().toLowerCase(Locale.ROOT));
            }
        }
        context.getInput().resizeWindow(1280,720);context.waitTicks(3);
        context.runOnClient(client->{ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SIZE));ConfigManager.smolpeopleMode=false;});
        clickSetting(context,"SmolPeople");context.runOnClient(client->{if(!ConfigManager.smolpeopleMode)throw new AssertionError("Merged SmolPeople switch did not enable the model");});
        clickSetting(context,"SmolPeople");context.runOnClient(client->{if(ConfigManager.smolpeopleMode)throw new AssertionError("Merged SmolPeople switch did not disable the model");ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.CHROMA));});
        clickSetting(context,"Lightness value");
        context.runOnClient(client->{var field=(com.customcosmetics.gui.PinkUi.Field)ClientUiAccess.screen(client).children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Field f&&f.getMessage().getString().equals("Lightness value")).findFirst().orElseThrow();if(!field.isFocused())throw new AssertionError("Compact numeric field did not receive focus");field.setValue("");});
        context.getInput().typeChars("0.65");context.waitTicks(2);
        context.runOnClient(client->{if(Math.abs(ConfigManager.nickTweaksChromaLightness-.65)>.001)throw new AssertionError("Compact slider number input did not update its value");});
        clickSetting(context,"Reset");context.runOnClient(client->{if(ConfigManager.nickTweaksChromaLightness!=.8)throw new AssertionError("Compact reset button did not restore its slider");});
        clickSetting(context,"Settings");clickSetting(context,"Mode: Online");context.runOnClient(client->{if(!ConfigManager.offlineMode||RemoteAccess.online())throw new AssertionError("Settings offline mode did not disable remote access");});
        clickSetting(context,"Mode: Offline");context.runOnClient(client->{if(ConfigManager.offlineMode||!RemoteAccess.online())throw new AssertionError("Settings online mode did not restore remote access");ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.ITEMS));});
        clickSetting(context,"Customize Items");context.runOnClient(client->{if(!ClientUiAccess.screen(client).getTitle().getString().equals("Item Customization"))throw new AssertionError("Item editor still uses the old title");ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.PARTY));});
        var point=new java.util.concurrent.atomic.AtomicReference<double[]>();context.runOnClient(client->{try{var screen=ClientUiAccess.screen(client);double scale=client.getWindow().getGuiScale();point.set(new double[]{((Integer)uiField(screen,"contentX")+100)*scale,((Integer)uiField(screen,"viewTop")+20)*scale});}catch(ReflectiveOperationException error){throw new AssertionError(error);}});
        context.getInput().setCursorPos(point.get()[0],point.get()[1]);context.getInput().scroll(-1);context.waitTicks(2);
        context.runOnClient(client->{try{
            var screen=(CosmeticsScreen)ClientUiAccess.screen(client);if((Double)uiField(screen,"scroll")!=48)throw new AssertionError("Compact settings wheel did not advance 48 pixels");
            int x=(Integer)uiField(screen,"contentX")+(Integer)uiField(screen,"contentWidth")+8,top=(Integer)uiField(screen,"viewTop"),bottom=(Integer)uiField(screen,"viewBottom");
            var info=new net.minecraft.client.input.MouseButtonInfo(ClientInput.MOUSE_LEFT,0);var press=new net.minecraft.client.input.MouseButtonEvent(x,top+1,info);
            if(!screen.mouseClicked(press,false))throw new AssertionError("Settings scrollbar did not capture its thumb");var end=new net.minecraft.client.input.MouseButtonEvent(x,bottom-1,info);screen.mouseDragged(end,0,bottom-top);screen.mouseReleased(end);
            var maximum=CosmeticsScreen.class.getDeclaredMethod("maxScroll");maximum.setAccessible(true);if(Math.abs((Double)uiField(screen,"scroll")-(Double)maximum.invoke(screen))>.01)throw new AssertionError("Settings scrollbar did not reach the last row");
            ConfigManager.nickTweaksGradientStyle=style.get();
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
        System.out.println("COMPACT_SETTINGS_A_Z_NAVIGATION_FOOTER_MODE_WIDE_TWO_COLUMNS_NARROW_STACK_NUMERIC_INPUT_RESET_SCROLLBAR_AND_MERGED_SMOLPEOPLE_OK");
    }
    private static Object uiField(Object screen,String name)throws ReflectiveOperationException{var field=CosmeticsScreen.class.getDeclaredField(name);field.setAccessible(true);return field.get(screen);}
    private static void showDevHeading(ClientGameTestContext context,String screenshot){
        context.runOnClient(client->{try{
            var screen=(CosmeticsScreen)ClientUiAccess.screen(client);var host=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Field widget&&widget.getMessage().getString().equals("Proxy Host")).findFirst().orElseThrow();
            var scroll=CosmeticsScreen.class.getDeclaredField("scroll");scroll.setAccessible(true);var maximum=CosmeticsScreen.class.getDeclaredMethod("maxScroll");maximum.setAccessible(true);
            scroll.setDouble(screen,Math.clamp(scroll.getDouble(screen)+host.getY()-(Integer)uiField(screen,"viewTop")-180,0,(Double)maximum.invoke(screen)));var position=CosmeticsScreen.class.getDeclaredMethod("positionControls");position.setAccessible(true);position.invoke(screen);
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}});context.waitTicks(3);context.takeScreenshot(screenshot);
    }
    private static void checkSyncDev(ClientGameTestContext context){
        context.runOnClient(client->{try{
            var field=PartyFeatures.class.getDeclaredField("roster");field.setAccessible(true);field.set(null,new PartyRoster(Set.of(A,FAR),Set.of(),"Hypixel Mod API"));
            connectionStatus="Connected\nP2P: 1\nUDP: 1\nTCP: 1";
            ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SYNC));
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
        context.waitTicks(3);
        showDevHeading(context,"sync-dev-heading-inline");
        context.getInput().resizeWindow(854,480);context.waitTicks(3);showDevHeading(context,"sync-dev-heading-wrapped");
        context.getInput().resizeWindow(1280,720);context.waitTicks(3);
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
        var portLocation=new java.util.concurrent.atomic.AtomicReference<double[]>();
        context.runOnClient(client->{try{
            var screen=(CosmeticsScreen)ClientUiAccess.screen(client);
            var port=(com.customcosmetics.gui.PinkUi.Field)screen.children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Field field&&field.getMessage().getString().equals("P2P UDP Port")).findFirst().orElseThrow();
            var scroll=CosmeticsScreen.class.getDeclaredField("scroll");scroll.setAccessible(true);var top=CosmeticsScreen.class.getDeclaredField("viewTop");top.setAccessible(true);
            var max=CosmeticsScreen.class.getDeclaredMethod("maxScroll");max.setAccessible(true);scroll.setDouble(screen,Math.clamp(scroll.getDouble(screen)+port.getY()-top.getInt(screen)-38,0,(Double)max.invoke(screen)));
            var apply=CosmeticsScreen.class.getDeclaredMethod("positionControls");apply.setAccessible(true);apply.invoke(screen);
            double scale=client.getWindow().getGuiScale();portLocation.set(new double[]{(port.getX()+port.getWidth()/2.0)*scale,(port.getY()+port.getHeight()/2.0)*scale});
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
        context.waitTicks(3);context.getInput().setCursorPos(portLocation.get()[0],portLocation.get()[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.waitTicks(2);
        context.runOnClient(client->{var port=(com.customcosmetics.gui.PinkUi.Field)ClientUiAccess.screen(client).children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Field field&&field.getMessage().getString().equals("P2P UDP Port")).findFirst().orElseThrow();if(!port.isFocused())throw new AssertionError("P2P port field did not receive focus");port.setValue("");});
        context.getInput().typeChars("45566");context.waitTicks(2);
        context.runOnClient(client->{
            if(ConfigManager.getSyncP2pPort()!=45566)throw new AssertionError("P2P port input did not update the local setting");
            var port=(com.customcosmetics.gui.PinkUi.Field)ClientUiAccess.screen(client).children().stream().filter(child->child instanceof com.customcosmetics.gui.PinkUi.Field field&&field.getMessage().getString().equals("P2P UDP Port")).findFirst().orElseThrow();
            port.setValue("65536");if(ConfigManager.getSyncP2pPort()!=45566)throw new AssertionError("Out-of-range P2P port was applied");
            port.setValue("abc");if(ConfigManager.getSyncP2pPort()!=45566)throw new AssertionError("Non-numeric P2P port was applied");port.setValue("45566");
        });context.takeScreenshot("sync-dev-fixed-p2p-port");
        context.takeScreenshot("sync-dev-connections");
        context.getInput().resizeWindow(854,480);context.waitTicks(3);context.takeScreenshot("sync-dev-connections-compact");
        context.runOnClient(client->{connectionStatus=null;ConfigManager.syncP2pPort=0;});
        System.out.println("SYNC_DEV_NATIVE_P2P_PORT_INPUT_BOUNDS_AND_AUTOMATIC_DEFAULT_OK");
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
