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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Render-only blocks and container recolors. Temporary block entities never enter the world or tick. */
public final class GhostBlocks {
    public static int submittedGhosts,submittedSpecialBlocks,submittedFluidGhosts,submittedPaintedEntities;
    private static final Map<MapEdits.Position,BlockEntity> entities=new HashMap<>();
    private static Object world;
    private static Map<MapEdits.Position,MapEdits.Edit> indexed;
    private static GhostIndex index=new GhostIndex(List.of());
    private record Built(Map<MapEdits.Position,MapEdits.Edit> source,GhostIndex index){}
    private static java.util.concurrent.CompletableFuture<Built> building;
    private static void updateIndex(Map<MapEdits.Position,MapEdits.Edit> edits){
        if(indexed==edits)return;
        if(building!=null&&building.isDone()){var built=building.join();building=null;index=built.index;indexed=built.source;}
        var changes=indexed==null?null:MapEdits.differences(indexed,edits);
        if(changes!=null&&changes.size()<=64){for(var p:changes){index.update(p,edits.get(p));entities.remove(p);}indexed=edits;return;}
        if(edits.size()<8192){index=new GhostIndex(edits.values());indexed=edits;entities.keySet().retainAll(edits.keySet());}
        else if(building==null)building=java.util.concurrent.CompletableFuture.supplyAsync(()->new Built(edits,new GhostIndex(edits.values())),MapWork.EXECUTOR);
    }
    public static List<MapEdits.Edit> nearby(double x,double y,double z,int radius){var edits=MapEditor.visible();updateIndex(edits);var result=new ArrayList<MapEdits.Edit>();index.nearby(x,y,z,radius,4096,e->{var current=edits.get(e.position());if(current!=null&&current.originallyAir())result.add(current);});return result;}
    public static int examinedCandidates;
    private static final AABB UNIT=new AABB(0,0,0,1,1,1);
    private GhostBlocks(){}
    public static void init(){LevelRenderEvents.COLLECT_SUBMITS.register(GhostBlocks::render);}
    private static void render(LevelRenderContext context){
        submittedGhosts=submittedSpecialBlocks=submittedFluidGhosts=submittedPaintedEntities=0;var client=Minecraft.getInstance();if(world!=client.level){entities.clear();world=client.level;}if(client.level==null)return;
        var edits=MapEditor.visible();
        updateIndex(edits);
        examinedCandidates=0;if(edits.isEmpty())return;
        var camera=context.levelState().cameraRenderState;
        var candidates=new ArrayList<>(nearby(camera.pos.x,camera.pos.y,camera.pos.z,96));
        // Loaded block entities are sparse. Never read every retextured solid block each frame.
        int minX=(int)Math.floor(camera.pos.x-96)>>4,maxX=(int)Math.floor(camera.pos.x+96)>>4;
        int minZ=(int)Math.floor(camera.pos.z-96)>>4,maxZ=(int)Math.floor(camera.pos.z+96)>>4;
        for(int cx=minX;cx<=maxX;cx++)for(int cz=minZ;cz<=maxZ;cz++){
            var chunk=client.level.getChunkSource().getChunk(cx,cz,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);if(chunk==null)continue;
            for(var entity:chunk.getBlockEntities().values()){
                var edit=edits.get(MapEditor.position(entity.getBlockPos()));if(edit!=null&&!edit.originallyAir())candidates.add(edit);
            }
        }
        long deadline=System.nanoTime()+3_000_000;
        for(var edit:candidates){if(examinedCandidates>0&&System.nanoTime()>=deadline)break;examinedCandidates++;
            var p=MapEditor.blockPos(edit.position());if(p.distToCenterSqr(camera.pos)>96*96||!client.level.hasChunkAt(p))continue;
            var real=client.level.getBlockState(p);boolean ghost=edit.ghost(real.isAir()),container=real.hasBlockEntity()&&!hasGeometry(client.getModelManager().getBlockStateModelSet().get(real));if(!ghost&&!container)continue;
            var state=MapEditor.state(edit.material());if(state==null||state.isAir())continue;
            var model=client.getModelManager().getBlockStateModelSet().get(state);
            var parts=new ArrayList<BlockStateModelPart>();model.collectParts(RandomSource.create(0),parts);
            int light=(client.level.getBrightness(LightLayer.BLOCK,p)<<4)|(client.level.getBrightness(LightLayer.SKY,p)<<20);
            var pose=new PoseStack();pose.translate(p.getX()-camera.pos.x,p.getY()-camera.pos.y,p.getZ()-camera.pos.z);
            if(container){
                var cube=client.getModelManager().getBlockStateModelSet().get(Blocks.STONE.defaultBlockState());parts.clear();new MaterialModel(cube,model,false).collectParts(RandomSource.create(0),parts);
                for(var box:real.getShape(client.level,p).toAabbs())submit(parts,context,pose,box,0xffffffff,light,null,false);submittedPaintedEntities++;continue;
            }
            var source=client.getBlockColors().getTintSource(state,0);int color=source==null?0xffffffff:0xff000000|source.colorInWorld(state,client.level,p);submit(parts,context,pose,UNIT,color,light,null,false);
            if(state.getBlock() instanceof EntityBlock block){
                var entity=entities.get(edit.position());if(entity==null||entity.getBlockState()!=state){entity=block.newBlockEntity(p,state);if(entity!=null){entity.setLevel(client.level);entities.put(edit.position(),entity);}}
                if(entity!=null&&submitEntity(entity,context,pose))submittedSpecialBlocks++;
            }
            if(!state.getFluidState().isEmpty()){
                boolean lava=state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);var sprite=client.getAtlasManager().get(new SpriteId(TextureAtlas.LOCATION_BLOCKS,Identifier.fromNamespaceAndPath("minecraft",lava?"block/lava_still":"block/water_still")));
                var fluidParts=new ArrayList<BlockStateModelPart>();client.getModelManager().getBlockStateModelSet().get(Blocks.STONE.defaultBlockState()).collectParts(RandomSource.create(0),fluidParts);
                int tint=lava?0xffffffff:0xb0000000|net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(client.level,p);submit(fluidParts,context,pose,new AABB(0,0,0,1,state.getFluidState().getOwnHeight(),1),tint,lava?0x00f000f0:light,sprite,!lava);submittedFluidGhosts++;
            }
            submittedGhosts++;
        }
    }
    private static boolean hasGeometry(net.minecraft.client.renderer.block.dispatch.BlockStateModel model){var parts=new ArrayList<BlockStateModelPart>();model.collectParts(RandomSource.create(0),parts);for(var part:parts){if(!part.getQuads(null).isEmpty())return true;for(var face:Direction.values())if(!part.getQuads(face).isEmpty())return true;}return false;}
    @SuppressWarnings({"rawtypes","unchecked"}) private static boolean submitEntity(BlockEntity entity,LevelRenderContext context,PoseStack pose){
        BlockEntityRenderer renderer=Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);if(renderer==null)return false;
        BlockEntityRenderState state=renderer.createRenderState();renderer.extractRenderState(entity,state,0,context.levelState().cameraRenderState.pos,null);renderer.submit(state,pose,context.submitNodeCollector(),context.levelState().cameraRenderState);return true;
    }
    private static void submit(List<BlockStateModelPart> parts,LevelRenderContext context,PoseStack pose,AABB box,int tint,int light,TextureAtlasSprite override,boolean translucent){
        for(var part:parts){var quads=new ArrayList<>(part.getQuads(null));for(var face:Direction.values())quads.addAll(part.getQuads(face));
            for(var quad:quads){var sprite=override==null?quad.materialInfo().sprite():override;boolean blend=translucent||(quad.materialInfo().flags()&BakedQuad.FLAG_TRANSLUCENT)!=0;int color=override!=null||quad.materialInfo().isTinted()?tint:0xffffffff;
                context.submitNodeCollector().submitCustomGeometry(pose,blend?RenderTypes.entityTranslucent(sprite.atlasLocation()):RenderTypes.entityCutout(sprite.atlasLocation()),(matrix,vertices)->{
                    var n=quad.direction();for(int i=0;i<4;i++){var vertex=quad.position(i);long uv=quad.packedUV(i);float u=UVPair.unpackU(uv),v=UVPair.unpackV(uv);if(override!=null){var from=quad.materialInfo().sprite();u=MaterialModel.remap(u,from.getU0(),from.getU1(),sprite.getU0(),sprite.getU1());v=MaterialModel.remap(v,from.getV0(),from.getV1(),sprite.getV0(),sprite.getV1());}
                        vertices.addVertex(matrix.pose(),(float)(box.minX+vertex.x()*box.getXsize()),(float)(box.minY+vertex.y()*box.getYsize()),(float)(box.minZ+vertex.z()*box.getZsize())).setColor(color).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,n.getStepX(),n.getStepY(),n.getStepZ());}
                });
            }
        }
    }
}
