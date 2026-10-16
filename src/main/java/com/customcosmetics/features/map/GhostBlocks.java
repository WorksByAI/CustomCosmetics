// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import java.util.*;

/** Air-only visual blocks. No world writes, collision, ray-pick or interaction replacement. */
public final class GhostBlocks {
    public static int submittedGhosts;
    private GhostBlocks(){}
    public static void init(){LevelRenderEvents.COLLECT_SUBMITS.register(GhostBlocks::render);}
    private static void render(LevelRenderContext context){
        submittedGhosts=0;var client=Minecraft.getInstance();if(client.level==null||MapEditor.mode!=MapEdits.Mode.LEGIT)return;
        var camera=context.levelState().cameraRenderState;var queue=context.submitNodeCollector();
        for(var edit:MapEditor.visible().values()){
            var p=MapEditor.blockPos(edit.position());if(p.distToCenterSqr(camera.pos)>96*96||!client.level.hasChunkAt(p)||!edit.ghost(client.level.getBlockState(p).isAir()))continue;
            var state=MapEditor.state(edit.material());if(state==null||state.isAir())continue;
            var parts=new ArrayList<BlockStateModelPart>();client.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(0),parts);
            int light=(client.level.getBrightness(LightLayer.BLOCK,p)<<4)|(client.level.getBrightness(LightLayer.SKY,p)<<20);
            var pose=new PoseStack();pose.translate(p.getX()-camera.pos.x,p.getY()-camera.pos.y,p.getZ()-camera.pos.z);
            for(var part:parts){var quads=new ArrayList<>(part.getQuads(null));for(var face:Direction.values())quads.addAll(part.getQuads(face));
                for(var quad:quads)queue.submitCustomGeometry(pose,RenderTypes.entityCutout(quad.materialInfo().sprite().atlasLocation()),(matrix,vertices)->{
                    var n=quad.direction();for(int i=0;i<4;i++){var vertex=quad.position(i);long uv=quad.packedUV(i);vertices.addVertex(matrix.pose(),vertex.x(),vertex.y(),vertex.z()).setColor(0xffffffff).setUv(UVPair.unpackU(uv),UVPair.unpackV(uv)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,n.getStepX(),n.getStepY(),n.getStepZ());}
                });
            }submittedGhosts++;
        }
    }
}
