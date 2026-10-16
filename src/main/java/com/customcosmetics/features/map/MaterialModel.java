// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import net.minecraft.client.renderer.block.dispatch.*;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.model.geom.builders.UVPair;
import java.util.*;

/** Retextures actual baked geometry; never substitutes a block state, shape or mining target. */
public final class MaterialModel implements BlockStateModel {
    private final BlockStateModel original,target;private final boolean hidden;
    public MaterialModel(BlockStateModel original,BlockStateModel target,boolean hidden){this.original=original;this.target=target;this.hidden=hidden;}
    @Override public void collectParts(RandomSource random,List<BlockStateModelPart> output){
        if(hidden)return;
        var parts=new ArrayList<BlockStateModelPart>();original.collectParts(random,parts);
        var targetParts=new ArrayList<BlockStateModelPart>();target.collectParts(RandomSource.create(0),targetParts);
        var materials=new EnumMap<Direction,BakedQuad.MaterialInfo>(Direction.class);
        for(var part:targetParts)for(Direction face:Direction.values()){
            var quads=part.getQuads(face);if(quads.isEmpty())quads=part.getQuads(null);
            for(var q:quads)materials.putIfAbsent(q.direction(),q.materialInfo());
        }
        for(var part:parts)output.add(new BlockStateModelPart(){
            @Override public List<BakedQuad> getQuads(Direction face){return part.getQuads(face).stream().map(q->retexture(q,materials.get(q.direction()),target.particleMaterial().sprite())).toList();}
            @Override public boolean useAmbientOcclusion(){return part.useAmbientOcclusion();}
            @Override public Material.Baked particleMaterial(){return target.particleMaterial();}
            @Override public int materialFlags(){return target.materialFlags();}
        });
    }
    @Override public Material.Baked particleMaterial(){return target.particleMaterial();}
    @Override public int materialFlags(){return target.materialFlags();}
    public static BakedQuad retexture(BakedQuad q,BakedQuad.MaterialInfo replacement,TextureAtlasSprite fallback){
        var from=q.materialInfo().sprite();var to=replacement==null?fallback:replacement.sprite();
        // Preserve geometry, shade and light. Target tint is not evaluated using the original block's tint provider.
        var info=MaterialInfoCopy.withTexture(q.materialInfo(),replacement,to);
        long[] uv=new long[4];for(int i=0;i<4;i++){
            long packed=q.packedUV(i);float u=UVPair.unpackU(packed),v=UVPair.unpackV(packed);
            float nu=remap(u,from.getU0(),from.getU1(),to.getU0(),to.getU1()),nv=remap(v,from.getV0(),from.getV1(),to.getV0(),to.getV1());
            uv[i]=UVPair.pack(nu,nv);
        }
        return new BakedQuad(q.position0(),q.position1(),q.position2(),q.position3(),uv[0],uv[1],uv[2],uv[3],q.direction(),info);
    }
    public static float remap(float value,float oldMin,float oldMax,float newMin,float newMax){return newMin+(value-oldMin)/Math.max(1e-8f,oldMax-oldMin)*(newMax-newMin);}
}
