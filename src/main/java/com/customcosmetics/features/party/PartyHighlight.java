// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.sync.PartySync;
import com.customcosmetics.sync.RemoteAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import java.util.*;

public final class PartyHighlight{
    public record Target(UUID uuid,String name,AABB box,boolean loaded,boolean party){
        public Target(UUID uuid,String name,AABB box,boolean loaded){this(uuid,name,box,loaded,true);}
    }
    private static final Map<UUID,PartyPositionTrack> TRACKS=new HashMap<>();private static Object level;
    private PartyHighlight(){}
    public static void init(){LevelRenderEvents.COLLECT_SUBMITS.register(PartyHighlight::render);}
    public static List<Target> targets(Minecraft client){
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SHARING)){if(level!=null){level=null;TRACKS.clear();}return List.of();}
        if(level!=client.level){level=client.level;TRACKS.clear();}
        boolean partyPositions=PartyFeatures.sharingPositions(),shared=PositionSharing.receiving(client);
        if((!partyPositions&&!shared)||!RemoteAccess.online()||client.level==null||client.player==null){TRACKS.clear();return List.of();}
        var roster=PartyFeatures.roster();var targets=new LinkedHashMap<UUID,Target>();
        float partial=client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var origin=client.player.getPosition(partial);var active=new HashSet<UUID>();
        for(var player:client.level.players())if(partyPositions&&player!=client.player&&roster.contains(player.getUUID(),player.getGameProfile().name())){
            var pos=player.getPosition(partial);targets.put(player.getUUID(),new Target(player.getUUID(),player.getGameProfile().name(),player.getBoundingBox().move(pos.subtract(player.position())),true));
        }
        if(shared||ConfigManager.partyFarMarkers||ConfigManager.partyTracersEnabled)for(var entry:PartySync.teammates().values()){
            boolean party=partyPositions&&roster.contains(entry.uuid(),entry.name());
            if(entry.expiresAt()<=System.currentTimeMillis()||entry.uuid().equals(client.player.getUUID())||!party&&!shared||targets.containsKey(entry.uuid()))continue;
            if(party&&!ConfigManager.partyFarMarkers&&!ConfigManager.partyTracersEnabled)continue;
            var loaded=client.level.getPlayerByUUID(entry.uuid());
            if(loaded!=null){var pos=loaded.getPosition(partial);targets.put(entry.uuid(),new Target(entry.uuid(),entry.name(),loaded.getBoundingBox().move(pos.subtract(loaded.position())),true,party));continue;}
            var raw=entry.position();
            if(!PartyDisplayRange.visible(ConfigManager.partyMaxDisplayDistance,origin.distanceToSqr(raw.x(),raw.y(),raw.z())))continue;
            active.add(entry.uuid());var p=TRACKS.computeIfAbsent(entry.uuid(),ignored->new PartyPositionTrack()).render(entry,System.nanoTime()/1000000);
            targets.put(entry.uuid(),new Target(entry.uuid(),entry.name(),new AABB(p.x()-p.width()/2,p.y(),p.z()-p.width()/2,p.x()+p.width()/2,p.y()+p.height(),p.z()+p.width()/2),false,party));
        }
        TRACKS.keySet().retainAll(active);
        targets.values().removeIf(target->{if(!target.loaded())return false;var box=target.box();double x=(box.minX+box.maxX)/2,y=box.minY,z=(box.minZ+box.maxZ)/2;
            return !PartyDisplayRange.visible(ConfigManager.partyMaxDisplayDistance,origin.distanceToSqr(x,y,z));});
        return List.copyOf(targets.values());
    }
    private static void render(LevelRenderContext context){
        var client=Minecraft.getInstance();var targets=targets(client);if(targets.isEmpty())return;
        var camera=context.levelState().cameraRenderState;var queue=context.submitNodeCollector();
        var origin=client.player.getPosition(client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        for(var target:targets)submit(target,camera,queue,client.font,origin);
    }
    public static void submit(Target target,CameraRenderState camera,SubmitNodeCollector queue,Font font){
        var client=Minecraft.getInstance();
        var origin=client.player==null?camera.pos:client.player.getPosition(client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        submit(target,camera,queue,font,origin);
    }
    private static void submit(Target target,CameraRenderState camera,SubmitNodeCollector queue,Font font,Vec3 origin){
        var box=target.box().move(-camera.pos.x,-camera.pos.y,-camera.pos.z);double distance=target.box().getCenter().distanceTo(camera.pos);
        if(PartyTracer.enabled(target.party())){
            var forward=new org.joml.Vector3f(0,0,-1).rotate(camera.orientation);
            var right=new org.joml.Vector3f(1,0,0).rotate(camera.orientation);
            var line=PartyTracer.ribbon(new Vec3(forward.x,forward.y,forward.z),new Vec3(right.x,right.y,right.z),box.getCenter(),Math.max(8,Math.min(128,camera.depthFar*.5)));
            if(!line.isEmpty())queue.submitCustomGeometry(new PoseStack(),PartyRenderPipeline.type(),(matrix,vertices)->{
                for(var point:line)vertices.addVertex(matrix.pose(),(float)point.x,(float)point.y,(float)point.z).setColor(PartyTracer.color(target.party()));
            });
        }
        if(target.party()&&!ConfigManager.partyEspEnabled)return;
        int color=ConfigManager.partyEspColor&0xFFFFFF;String mode=ConfigManager.partyEspStyle;
        if(target.party()&&target.loaded()&&!mode.equals("none")){
            var pose=new PoseStack();int fill=(Math.clamp((int)Math.round(ConfigManager.partyEspOpacity*255),1,220)<<24)|color;
            double thickness=Math.min(.12,.008*ConfigManager.partyEspLineWidth*Math.max(1,distance/24));
            queue.submitCustomGeometry(pose,PartyRenderPipeline.type(),(matrix,vertices)->{
                if(!mode.equals("outline"))PartyGeometry.box(vertices,matrix.pose(),box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,fill);
                if(!mode.equals("fill"))PartyGeometry.outline(vertices,matrix.pose(),box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,thickness,0xFF000000|color);
            });
        }
        double nameDistanceSquared=origin.distanceToSqr((target.box().minX+target.box().maxX)/2,target.box().minY,(target.box().minZ+target.box().maxZ)/2);
        if(PartyDisplayRange.nametagVisible(ConfigManager.partyNametagHideDistance,nameDistanceSquared)&&
            (!target.party()||!target.loaded()&&ConfigManager.partyFarMarkers||target.loaded()&&ConfigManager.partyShowNames)){
            Vec3 rel=target.box().getCenter().add(0,target.box().getYsize()/2+.3,0).subtract(camera.pos);
            double range=Math.max(8,Math.min(128,camera.depthFar*.5));double length=rel.length();if(length>range)rel=rel.scale(range/length);
            var pose=new PoseStack();pose.translate(rel.x,rel.y,rel.z);pose.mulPose(new org.joml.Matrix4f().rotation(camera.orientation));
            float scale=PartyNametagScale.scale(ConfigManager.partyNametagMode,ConfigManager.partyNametagSize,length,Math.min(length,range));pose.scale(scale,-scale,scale);
            boolean vanilla="vanilla".equals(ConfigManager.partyNametagStyle);
            var label=vanilla?SharingNametag.label(Minecraft.getInstance(),target.uuid(),target.name()):Component.literal(target.name()+" · "+Math.round(distance)+"m");
            queue.submitText(pose,-font.width(label)/2f,0,label.getVisualOrderText(),false,Font.DisplayMode.SEE_THROUGH,0xF000F0,vanilla?0xFFFFFFFF:0xFF000000|color,0x40000000,0);
        }
    }
}
