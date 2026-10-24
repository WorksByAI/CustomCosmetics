// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.accessories.*;
import com.customcosmetics.gui.*;
import com.customcosmetics.sync.PresenceSync;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.player.RemotePlayer;

public final class AccessoriesGameTest implements FabricClientGameTest {
    public static final Map<Integer,Integer> observed=new ConcurrentHashMap<>();
    public static volatile int previewPose;
    public static final Set<Integer> motionPhases=ConcurrentHashMap.newKeySet();
    public static final Map<net.minecraft.client.model.geom.ModelPart,String> expectedTextures=new ConcurrentHashMap<>();
    private static final UUID REMOTE=UUID.fromString("12345678-1234-4234-8234-123456789abc");private static final int REMOTE_ID=-12160;
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.offlineMode=false;ConfigManager.accessories=AccessoriesSettings.NONE;ConfigManager.capeEnabled=false;ConfigManager.smolpeopleMode=false;ConfigManager.playerSizeEnabled=false;ConfigManager.nickTweaksEnabled=false;});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear");context.waitTicks(10);context.getInput().resizeWindow(1600,1000);
            if("true".equals(System.getenv("CUSTOMCOSMETICS_FIT_QUICK"))){fitting(context);motions(context);return;}
            var self=new AtomicReference<Integer>();context.runOnClient(client->{client.options.guiScale().set(2);self.set(client.player.getId());ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.ACCESSORIES));});context.waitTicks(3);
            check(self.get(),0);
            NicknamePresetGameTest.click(context,"Open Wardrobe",PinkUi.Action.class);click(context,"Feather Wings");context.waitTicks(3);check(self.get(),4);context.takeScreenshot("accessories-original-feather-wings");
            click(context,"Halo");click(context,"Golden Halo");context.waitTicks(3);check(self.get(),6);
            click(context,"Ears");click(context,"Cat Ears");context.waitTicks(3);check(self.get(),10);
            context.runOnClient(client->{if(!ConfigManager.accessories.wings().enabled()||!ConfigManager.accessories.halo().enabled()||!ConfigManager.accessories.ears().enabled())throw new AssertionError("GUI did not enable independent parts");ClientUiAccess.setScreen(client,new WardrobeScreen(null));});context.waitTicks(3);visibleHalo(context);context.takeScreenshot("accessories-original-full-outfit");
            context.runOnClient(client->{ConfigManager.smolpeopleMode=true;ConfigManager.playerSizeEnabled=true;ConfigManager.playerSizeX=.8;ConfigManager.playerSizeY=1.2;ConfigManager.playerSizeZ=1;});context.waitTicks(3);check(self.get(),10);context.takeScreenshot("accessories-smolpeople-and-player-size");
            context.runOnClient(client->ClientUiAccess.setScreen(client,new CapeScreen(null)));context.waitTicks(3);check(self.get(),0);
            context.runOnClient(client->{ConfigManager.smolpeopleMode=false;ConfigManager.playerSizeEnabled=false;
                var remote=new RemotePlayer(client.level,new com.mojang.authlib.GameProfile(REMOTE,"Alice"));remote.setId(REMOTE_ID);remote.setPos(client.player.getX(),client.player.getY(),client.player.getZ()+4);client.level.addEntity(remote);client.player.setYRot(0);client.player.setXRot(0);ClientUiAccess.setScreen(client,null);});context.waitTicks(5);check(REMOTE_ID,0);
            context.runOnClient(client->receive(ConfigManager.accessories));context.waitTicks(4);check(REMOTE_ID,10);context.takeScreenshot("accessories-synced-remote-player");
            context.runOnClient(client->ConfigManager.offlineMode=true);context.waitTicks(3);check(REMOTE_ID,0);
            context.runOnClient(client->ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.ACCESSORIES)));context.waitTicks(3);check(self.get(),10);
            camera(context);collections(context);fitting(context);catalog(context,self.get());motions(context);
            context.runOnClient(client->{client.level.removeEntity(REMOTE_ID,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);ConfigManager.accessories=AccessoriesSettings.NONE;ClientUiAccess.setScreen(client,null);});
            System.out.println("ACCESSORIES_REAL_GUI_MESHES_REMOTE_SYNC_OFFLINE_CAPE_ISOLATION_AND_SMOL_OK");
        }catch(Exception failure){throw new AssertionError(failure);}
    }
    private static void check(int id,int parts){if(!Objects.equals(parts,observed.get(id)))throw new AssertionError("Accessory parts id="+id+" expected="+parts+" actual="+observed.get(id));}
    private static void camera(ClientGameTestContext context)throws Exception{
        context.runOnClient(client->ClientUiAccess.setScreen(client,new WardrobeScreen(null)));context.waitTicks(2);
        context.runOnClient(client->{var screen=(WardrobeScreen)ClientUiAccess.screen(client);var x=WardrobeScreen.class.getDeclaredField("contentX");var y=WardrobeScreen.class.getDeclaredField("y");var camera=WardrobeScreen.class.getDeclaredField("camera");x.setAccessible(true);y.setAccessible(true);camera.setAccessible(true);double px=x.getInt(screen)+100,py=y.getInt(screen)+80;var event=new net.minecraft.client.input.MouseButtonEvent(px,py,new net.minecraft.client.input.MouseButtonInfo(0,0));var before=ConfigManager.accessories;
            if(!screen.mouseClicked(event,false)||!screen.mouseDragged(event,100,10))throw new AssertionError("Native wardrobe preview cannot be dragged");screen.mouseReleased(event);screen.mouseScrolled(px,py,0,1);var rotated=(WardrobeCamera)camera.get(screen);if(Math.abs(rotated.yaw()-92)>.01||rotated.zoom()<=1)throw new AssertionError("Preview camera did not turn and zoom");if(!before.equals(ConfigManager.accessories))throw new AssertionError("Turning a preview changed synced outfit settings");
        });context.waitTicks(3);context.takeScreenshot("wardrobe-sculpted-side-view");
        context.runOnClient(client->ClientUiAccess.setScreen(client,new WardrobeScreen(null)));
    }
    private static void collections(ClientGameTestContext context)throws Exception{
        var before=new AtomicReference<AccessoriesSettings>();context.runOnClient(client->{before.set(ConfigManager.accessories);ClientUiAccess.setScreen(client,new WardrobeScreen(null));});context.waitTicks(2);
        click(context,"Detailed");context.waitTicks(2);context.runOnClient(client->{if(!before.get().equals(ConfigManager.accessories))throw new AssertionError("Browsing a collection changed the outfit");var names=ClientUiAccess.screen(client).children().stream().filter(PinkUi.Action.class::isInstance).map(PinkUi.Action.class::cast).map(w->w.getMessage().getString()).toList();if(!names.contains("Aether Wings")||names.contains("Feather Wings"))throw new AssertionError("Detailed collection mixes its selectors with pixel styles");});
        context.runOnClient(client->ConfigManager.accessories=ConfigManager.accessories.with("wings",ConfigManager.accessories.wings().scale(1.3).speed(.6)));
        click(context,"Aether Wings");context.waitTicks(2);context.runOnClient(client->{if(!ConfigManager.accessories.wings().style().equals("aether")||!ConfigManager.accessories.ears().style().equals("cat"))throw new AssertionError("Detailed part did not preserve the equipped pixel ears");if(ConfigManager.accessories.wings().scale()!=1.3||ConfigManager.accessories.wings().speed()!=.6)throw new AssertionError("Choosing a style reset recently edited size or flap speed");before.set(ConfigManager.accessories);});
        click(context,"Pixel");context.waitTicks(2);context.runOnClient(client->{if(!before.get().equals(ConfigManager.accessories))throw new AssertionError("Returning to pixel collection rewrote a detailed part");});context.takeScreenshot("wardrobe-mixed-pixel-and-detailed");
        context.runOnClient(client->{ConfigManager.offlineMode=false;receive(ConfigManager.accessories);ClientUiAccess.setScreen(client,null);});context.waitTicks(4);check(REMOTE_ID,10);context.takeScreenshot("wardrobe-mixed-synced-remote-player");
        context.runOnClient(client->{ConfigManager.offlineMode=true;ClientUiAccess.setScreen(client,new WardrobeScreen(null));});context.waitTicks(3);check(REMOTE_ID,0);
    }
    private static void visibleHalo(ClientGameTestContext context)throws Exception{
        var result=new java.util.concurrent.CompletableFuture<Integer>();context.runOnClient(client->{
            var screen=ClientUiAccess.screen(client);var xField=WardrobeScreen.class.getDeclaredField("contentX");xField.setAccessible(true);var yField=WardrobeScreen.class.getDeclaredField("y");yField.setAccessible(true);var wField=WardrobeScreen.class.getDeclaredField("contentW");wField.setAccessible(true);
            int x=xField.getInt(screen),y=yField.getInt(screen)+43,w=wField.getInt(screen);double scale=client.getWindow().getGuiScale();
            java.lang.reflect.Method method;try{method=client.gameRenderer.getClass().getMethod("mainRenderTarget");}catch(NoSuchMethodException legacy){method=client.getClass().getMethod("getMainRenderTarget");}
            var target=method.invoke(method.getDeclaringClass().isInstance(client.gameRenderer)?client.gameRenderer:client);
            net.minecraft.client.Screenshot.takeScreenshot((com.mojang.blaze3d.pipeline.RenderTarget)target,image->{try(image){int gold=0;for(int py=(int)((y+5)*scale);py<(int)((y+150)*scale);py++)for(int px=(int)(x*scale);px<(int)((x+w)*scale);px++){int c=image.getPixel(px,py),r=(c>>16)&255,g=(c>>8)&255,b=c&255;if(r>170&&g>110&&b<170&&r>g+10)gold++;}result.complete(gold);}catch(Throwable error){result.completeExceptionally(error);}});
        });context.waitFor(client->result.isDone());if(result.join()<10)throw new AssertionError("Halo is clipped or missing from the native preview: "+result.join());
    }
    private static void click(ClientGameTestContext context,String label)throws Exception{
        var position=new AtomicReference<double[]>();context.runOnClient(client->{var widget=ClientUiAccess.screen(client).children().stream().filter(PinkUi.Action.class::isInstance).map(PinkUi.Action.class::cast).filter(w->w.getMessage().getString().equals(label)).findFirst().orElseThrow();double scale=client.getWindow().getGuiScale();position.set(new double[]{(widget.getX()+widget.getWidth()/2.0)*scale,(widget.getY()+widget.getHeight()/2.0)*scale});});
        context.getInput().setCursorPos(position.get()[0],position.get()[1]);context.getInput().pressMouse(com.customcosmetics.compatibility.ClientInput.MOUSE_LEFT);context.waitTicks(2);
    }
    private static void catalog(ClientGameTestContext context,int self)throws Exception{
        context.runOnClient(client->ClientUiAccess.setScreen(client,new WardrobeScreen(null,"hat")));context.waitTicks(2);click(context,"Gem Crown");
        var coords=new AtomicReference<double[]>();context.runOnClient(client->{var field=ClientUiAccess.screen(client).children().stream().filter(PinkUi.Field.class::isInstance).map(PinkUi.Field.class::cast).filter(w->w.getMessage().getString().equals("Color Hex")).findFirst().orElseThrow();double scale=client.getWindow().getGuiScale();coords.set(new double[]{(field.getX()+field.getWidth()/2.0)*scale,(field.getY()+field.getHeight()/2.0)*scale});});
        context.getInput().setCursorPos(coords.get()[0],coords.get()[1]);context.getInput().pressMouse(com.customcosmetics.compatibility.ClientInput.MOUSE_LEFT);context.waitTicks(1);
        context.runOnClient(client->{var field=ClientUiAccess.screen(client).children().stream().filter(PinkUi.Field.class::isInstance).map(PinkUi.Field.class::cast).filter(w->w.getMessage().getString().equals("Color Hex")).findFirst().orElseThrow();if(!field.isFocused())throw new AssertionError("Wardrobe color field did not receive focus");field.setValue("");});context.getInput().typeChars("#336699");context.waitTicks(2);
        context.runOnClient(client->{if(ConfigManager.accessories.part("hat").color()!=0x336699)throw new AssertionError("Wardrobe typing did not change the selected color");});click(context,"None");context.waitTicks(2);check(self,10);
        click(context,"Wings");click(context,"›");click(context,"›");click(context,"Mechanical Wings");context.waitTicks(2);
        context.runOnClient(client->{if(!ConfigManager.accessories.wings().style().equals("mechanical"))throw new AssertionError("Wardrobe pagination did not select the requested style");});
        for(var slot:AccessoryCatalog.slots()){
            for(var style:slot.styles()){
                context.runOnClient(client->checkSculpture(slot,style));
                context.runOnClient(client->{ConfigManager.accessories=AccessoriesSettings.NONE.with(slot.id(),AccessoriesSettings.NONE.part(slot.id()).enabled(true).style(style.id()).color(style.color()).accent(style.accent()));ClientUiAccess.setScreen(client,new WardrobeScreen(null,slot.id(),style.collection()));});context.waitTicks(2);
                check(self,MiniMePet.selected(style)?0:switch(slot.id()){case "wings","ears","arms","feet","pants","head","pet","shoulder"->4;case "particles"->12;case "outfit"->style.collection()==AccessoryCatalog.Collection.DETAILED||style.variant()==2?10:style.variant()==1?6:2;case "hat"->style.collection()==AccessoryCatalog.Collection.DETAILED&&style.variant()==3?3:2;default->2;});
                context.takeScreenshot("wardrobe-"+slot.id()+"-"+style.id());
            }
        }
        for(var theme:List.of(
            Map.of("wings","seraph","halo","star","ears","rabbit","neck","bow","feet","paws","pet","cat"),
            Map.of("wings","pixie","ears","cat","hat","flowers","neck","ribbon","tail","fox","outfit","apron","feet","paws","pet","cat"),
            Map.of("wings","raven","hat","witch","halo","double","neck","chain","back","staff","outfit","robe","tail","kitsune","pet","dragon","particles","stars"))){
            var themed=AccessoriesSettings.NONE;for(var entry:theme.entrySet()){var style=AccessoryCatalog.style(entry.getKey(),entry.getValue());themed=themed.with(entry.getKey(),themed.part(entry.getKey()).enabled(true).style(style.id()).color(style.color()).accent(style.accent()));}var preview=themed;
            context.runOnClient(client->{ConfigManager.accessories=preview;ClientUiAccess.setScreen(client,new WardrobeScreen(null));});context.waitTicks(4);context.takeScreenshot("wardrobe-curated-"+theme.get("wings"));
        }
        for(var theme:List.of(
            Map.of("wings","aether","halo","starcircuit","ears","velvetcat","neck","royalbow","pet","mooncat","outfit","celestialcoat"),
            Map.of("wings","moonlace","hat","roseberet","ears","arcticfox","tail","plumefox","pet","snowowl","neck","moonpendant"),
            Map.of("wings","emberdrake","hat","moonwitch","halo","moonorbit","back","arcanestaff","tail","luminousdragon","pet","stardragon","outfit","nightrobe"))){
            var themed=AccessoriesSettings.NONE;for(var entry:theme.entrySet()){var style=AccessoryCatalog.style(entry.getKey(),entry.getValue());themed=themed.with(entry.getKey(),themed.part(entry.getKey()).enabled(true).style(style.id()).color(style.color()).accent(style.accent()));}var preview=themed;
            context.runOnClient(client->{ConfigManager.accessories=preview;ClientUiAccess.setScreen(client,new WardrobeScreen(null,"wings",AccessoryCatalog.Collection.DETAILED));});context.waitTicks(4);context.takeScreenshot("wardrobe-detailed-curated-"+theme.get("wings"));
        }
        var combination=AccessoriesSettings.NONE;for(var slot:AccessoryCatalog.slots())combination=combination.with(slot.id(),combination.part(slot.id()).enabled(true));var outfit=combination;
        context.runOnClient(client->{ConfigManager.accessories=outfit;ClientUiAccess.setScreen(client,new WardrobeScreen(null));});context.waitTicks(3);check(self,58);context.takeScreenshot("wardrobe-all-parts-combined");
        context.runOnClient(client->{
            var physical=client.player.getBoundingBox();java.lang.reflect.Method method;
            try{method=net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredMethod("getBoundingBoxForCulling",net.minecraft.world.entity.LivingEntity.class);}
            catch(NoSuchMethodException modern){method=net.minecraft.client.renderer.entity.LivingEntityRenderer.class.getDeclaredMethod("getBoundingBoxForCulling",net.minecraft.world.entity.LivingEntity.class,float.class);}
            method.setAccessible(true);var renderer=client.getEntityRenderDispatcher().getRenderer(client.player);
            Object[] arguments=method.getParameterCount()==1?new Object[]{client.player}:new Object[]{client.player,0f};
            var expanded=(net.minecraft.world.phys.AABB)method.invoke(renderer,arguments);
            if(expanded.getXsize()<=physical.getXsize()||expanded.getYsize()<=physical.getYsize())throw new AssertionError("Large accessories can be culled at the viewport edge");if(!client.player.getBoundingBox().equals(physical))throw new AssertionError("Cosmetic culling changed a gameplay hitbox");
            int modules=ConfigManager.disabledModules;var saved=ConfigManager.accessories;
            try{
                com.customcosmetics.config.FeatureModules.set(com.customcosmetics.config.FeatureModules.Feature.EQUIPMENT,false);
                var disabled=(net.minecraft.world.phys.AABB)method.invoke(renderer,arguments);
                ConfigManager.accessories=AccessoriesSettings.NONE;
                com.customcosmetics.config.FeatureModules.set(com.customcosmetics.config.FeatureModules.Feature.EQUIPMENT,true);
                var noParts=(net.minecraft.world.phys.AABB)method.invoke(renderer,arguments);
                if(!disabled.equals(noParts))throw new AssertionError("Disabled accessories still expand culling bounds");
            }finally{ConfigManager.disabledModules=modules;ConfigManager.accessories=saved;}
        });
        context.runOnClient(client->{client.options.guiScale().set(3);});context.getInput().resizeWindow(1280,720);context.waitTicks(3);
        context.runOnClient(client->{var screen=ClientUiAccess.screen(client);for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.visible&&(widget.getY()<0||widget.getBottom()>screen.height||widget.getX()<0||widget.getRight()>screen.width))throw new AssertionError("Compact wardrobe control exceeds viewport: "+widget.getMessage().getString());});context.takeScreenshot("wardrobe-compact-viewport");
        System.out.println("WARDROBE_"+AccessoryCatalog.count()+"_ORIGINAL_STYLES_NATIVE_MESHES_AND_COMPACT_GUI_OK");
    }
    private static void checkSculpture(AccessoryCatalog.Slot slot,AccessoryCatalog.Style style){
        if(MiniMePet.selected(style))return; // Vanilla skin model is covered by MiniMeGameTest.
        var mesh=AccessoryMeshes.get(style);if(mesh!=AccessoryMeshes.get(style))throw new AssertionError("Models are rebuilt per frame");var min=new float[]{Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE};var max=new float[]{-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE};var count=new int[1];
        var texture="customcosmetics:textures/accessories/"+(style.collection()==AccessoryCatalog.Collection.DETAILED?"detailed-materials.png":"materials.png");for(var part:new net.minecraft.client.model.geom.ModelPart[]{mesh.primary(),mesh.accent(),mesh.details(),mesh.highlights(),mesh.sleeves(),mesh.cuffs(),mesh.skirt(),mesh.hem()})if(part!=null)expectedTextures.put(part,texture);
        for(var model:List.of(mesh.primary(),mesh.accent()))model.visit(new com.mojang.blaze3d.vertex.PoseStack(),(pose,path,index,cube)->{for(var face:cube.polygons){if(!Float.isFinite(face.normal().x())||Math.abs(face.normal().length()-1)>.01)throw new AssertionError("Invalid sculpted face normal: "+style.id());for(var vertex:face.vertices()){float[] coords={vertex.x(),vertex.y(),vertex.z()};for(int i=0;i<3;i++){if(!Float.isFinite(coords[i]))throw new AssertionError("Invalid mesh vertex");min[i]=Math.min(min[i],coords[i]);max[i]=Math.max(max[i],coords[i]);}if(vertex.u()<0||vertex.v()<0||vertex.u()>1||vertex.v()>1)throw new AssertionError("Material UV escaped the bundled atlas");count[0]++;}}});
        if(count[0]<32)throw new AssertionError("Accessory has no real front/back/edge geometry");if(slot.id().equals("wings")&&max[2]-min[2]<.65f)throw new AssertionError("Wing reverted to a flat plate: "+style.id());
        if(style.collection()==AccessoryCatalog.Collection.DETAILED&&count[0]>40000)throw new AssertionError("Detailed mesh exceeds its face budget: "+style.id());
    }
    private static void fitting(ClientGameTestContext context){context.runOnClient(client->{
        var boots=AccessoryMeshes.get(AccessoryCatalog.style("feet","boots"));var gloves=AccessoryMeshes.get(AccessoryCatalog.style("arms","gloves"));var pants=AccessoryMeshes.get(AccessoryCatalog.style("pants","jeans"));
        if(AccessoryModelFit.bounds(boots.primary()).maxZ()<=AccessoryModelFit.bounds(pants.accent()).maxZ()+.1)throw new AssertionError("Boots and pants have coincident back faces");
        for(var style:AccessoryCatalog.slot("feet").styles()){var shoe=AccessoryMeshes.get(style);if(AccessoryModelFit.bounds(shoe.accent()).maxY()<=AccessoryModelFit.bounds(shoe.primary()).maxY()+.2||AccessoryModelFit.bounds(shoe.accent()).maxY()<12.4)throw new AssertionError("Sole intersects its shoe base or vanilla skin overlay: "+style.id());}
        for(String style:List.of("robe","celestialcoat","nightrobe")){var coat=AccessoryMeshes.get(AccessoryCatalog.style("outfit",style));
            if(AccessoryModelFit.bounds(coat.hem()).maxY()>=AccessoryModelFit.bounds(boots.primary()).minY()-.2)throw new AssertionError("Robe hem touches the boot top plane");
            if(AccessoryModelFit.bounds(gloves.primary()).maxY()<=AccessoryModelFit.bounds(coat.sleeves()).maxY()+.1)throw new AssertionError("Gloves and sleeves have coincident wrist faces");
        }
        for(boolean slim:new boolean[]{false,true}){
            var model=new net.minecraft.client.model.player.PlayerModel(net.minecraft.client.model.geom.builders.LayerDefinition.create(net.minecraft.client.model.player.PlayerModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(0),slim),64,64).bakeRoot(),slim);
            var sleeve=AccessoryMeshes.get(AccessoryCatalog.style("outfit","celestialcoat")).sleeves();
            for(var bone:List.of(model.rightArm,model.leftArm))for(float rotation:new float[]{0,.9f,-1.2f})for(double armor:new double[]{0,1.1}){
                bone.xRot=rotation;bone.zRot=.1f;var base=new com.mojang.blaze3d.vertex.PoseStack();bone.translateAndRotate(base);var inverse=new org.joml.Matrix4f(base.last().pose()).invert();
                var fitted=new com.mojang.blaze3d.vertex.PoseStack();AccessoryModelFit.garment(fitted,bone,1,armor,4.8,12.8,4.8,4);var matrix=inverse.mul(fitted.last().pose());
                float[] min={Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE},max={-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE};
                sleeve.visit(new com.mojang.blaze3d.vertex.PoseStack(),(p,path,index,cube)->{for(var face:cube.polygons)for(var vertex:face.vertices()){var v=matrix.transformPosition(new org.joml.Vector3f(vertex.x()/16,vertex.y()/16,vertex.z()/16));for(int i=0;i<3;i++){min[i]=Math.min(min[i],v.get(i));max[i]=Math.max(max[i],v.get(i));}}});
                var b=AccessoryModelFit.bounds(bone);float[] lo={b.minX(),b.minY(),b.minZ()},hi={b.maxX(),b.maxY(),b.maxZ()};
                for(int i=0;i<3;i++)if(min[i]>(lo[i]-.25f)/16+1e-5||max[i]<(hi[i]+.25f)/16-1e-5)throw new AssertionError("Sleeve does not enclose real "+(slim?"slim":"wide")+" arm after movement");
            }
        }
    });System.out.println("REAL_WIDE_SLIM_BONES_SLEEVE_SKIN_ARMOR_MOVEMENT_FIT_OK");}
    private static void motions(ClientGameTestContext context)throws Exception{
        var settings=AccessoriesSettings.NONE;for(var entry:Map.of("wings","aether","halo","starcircuit","ears","velvetcat","hat","roseberet","back","starpack","tail","plumefox","outfit","nightrobe","arms","gloves","feet","boots").entrySet()){var style=AccessoryCatalog.style(entry.getKey(),entry.getValue());settings=settings.with(entry.getKey(),settings.part(entry.getKey()).enabled(true).style(style.id()).color(style.color()).accent(style.accent()));}var outfit=settings;
        context.getInput().resizeWindow(1600,1000);context.runOnClient(client->{client.options.guiScale().set(2);ConfigManager.accessories=outfit;ClientUiAccess.setScreen(client,new WardrobeScreen(null,"wings",AccessoryCatalog.Collection.DETAILED));});
        try{for(int phase=0;phase<=7;phase++){int value=phase;context.runOnClient(client->{previewPose=value;var camera=WardrobeScreen.class.getDeclaredField("camera");camera.setAccessible(true);camera.set(ClientUiAccess.screen(client),new WardrobeCamera(value%2==0?0:90,-8,1.2f));});context.waitTicks(3);context.takeScreenshot("wardrobe-fit-"+(phase==0?"standing":phase<5?"walk-turn-"+phase:"attack-"+phase));}}
        finally{previewPose=0;}
        if("true".equals(System.getenv("CUSTOMCOSMETICS_FIT_DIAG"))){
            for(String slot:List.of("outfit","arms","feet")){context.runOnClient(client->{previewPose=4;ConfigManager.accessories=AccessoriesSettings.NONE.with(slot,outfit.part(slot));ClientUiAccess.setScreen(client,new WardrobeScreen(null,slot));var camera=WardrobeScreen.class.getDeclaredField("camera");camera.setAccessible(true);camera.set(ClientUiAccess.screen(client),new WardrobeCamera(0,-8,1.6f));});context.waitTicks(3);context.takeScreenshot("fit-isolated-"+slot);}
            context.runOnClient(client->ConfigManager.accessories=outfit);previewPose=0;
        }
        context.runOnClient(client->{if(!ConfigManager.accessories.equals(outfit))throw new AssertionError("Rendering movement changed accessory choices");if(motionPhases.size()!=7)throw new AssertionError("Motion snapshots never reached the actual player model: "+motionPhases);});
        System.out.println("ACCESSORIES_NATIVE_STANDING_HEAD_TURN_WALK_ATTACK_VIEWS_OK");
    }
    private static void receive(AccessoriesSettings settings){try{
        var user=new JsonObject();user.addProperty("name","Alice");var features=new JsonObject();features.add("accessories",settings.json());user.add("features",features);
        var users=new JsonObject();users.add(REMOTE.toString(),user);var root=new JsonObject();root.addProperty("authVersion",2);root.add("users",users);
        var apply=PresenceSync.class.getDeclaredMethod("applyPayload",String.class,boolean.class,boolean.class);apply.setAccessible(true);apply.invoke(null,root.toString(),false,true);
    }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
}
