// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import net.minecraft.client.renderer.block.dispatch.*;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.model.geom.builders.UVPair;
import java.util.*;

/** Retextures actual baked geometry; never substitutes a block state, shape or mining target. */
public final class MaterialModel implements BlockStateModel {
    private static final Direction[] FACES=Direction.values();
    private static final int MAX_MODELS=128,MAX_PARTS=256;
    private static final ThreadLocal<Models> MODELS=ThreadLocal.withInitial(Models::new);
    private static final class Models {
        Object generation;int size;
        final IdentityHashMap<BlockStateModel,IdentityHashMap<BlockStateModel,MaterialModel>> models=new IdentityHashMap<>();
    }
    /** Chunk builders own their cache; random source-part selection is still performed for each block. */
    static MaterialModel cached(Object generation,BlockStateModel original,BlockStateModel target,boolean hidden){
        var cache=MODELS.get();
        if(cache.generation!=generation){cache.models.clear();cache.size=0;cache.generation=generation;}
        var targets=cache.models.get(original);var model=targets==null?null:targets.get(target);
        if(model!=null&&model.hidden==hidden)return model;
        if(cache.size>=MAX_MODELS){cache.models.clear();cache.size=0;targets=null;}
        if(targets==null){targets=new IdentityHashMap<>();cache.models.put(original,targets);}
        model=new MaterialModel(original,target,hidden);targets.put(target,model);cache.size++;return model;
    }
    private final BlockStateModel original,target;private final boolean hidden;
    private final ArrayList<BlockStateModelPart> parts=new ArrayList<>();
    private final IdentityHashMap<BlockStateModelPart,BlockStateModelPart> retextured=new IdentityHashMap<>();
    private BakedQuad.MaterialInfo[] materials;
    private boolean stableTarget;
    public MaterialModel(BlockStateModel original,BlockStateModel target,boolean hidden){this.original=original;this.target=target;this.hidden=hidden;}
    @Override public void collectParts(RandomSource random,List<BlockStateModelPart> output){
        if(hidden)return;
        parts.clear();original.collectParts(random,parts);
        if(materials==null||!stableTarget){
            retextured.clear();
            var type=target.getClass();
            stableTarget=type==SingleVariant.class||type==WeightedVariants.class||type==net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel.class;
            materials=new BakedQuad.MaterialInfo[FACES.length];
            var targetParts=new ArrayList<BlockStateModelPart>();target.collectParts(RandomSource.create(0),targetParts);
            for(var part:targetParts){if(!(part instanceof SimpleModelWrapper))stableTarget=false;for(Direction face:FACES){
                var quads=part.getQuads(face);if(quads.isEmpty())quads=part.getQuads(null);
                for(var q:quads)if(materials[q.direction().ordinal()]==null)materials[q.direction().ordinal()]=q.materialInfo();
            }}
        }
        for(var part:parts){
            boolean immutable=part instanceof SimpleModelWrapper;
            var changed=immutable?retextured.get(part):null;
            if(changed==null){if(retextured.size()>=MAX_PARTS)retextured.clear();changed=retexture(part,immutable);if(immutable)retextured.put(part,changed);}
            output.add(changed);
        }
        parts.clear();
    }
    private BlockStateModelPart retexture(BlockStateModelPart part,boolean immutable){
        var palette=materials;
        var quads=new ArrayList<List<BakedQuad>>(FACES.length+1);
        if(immutable)for(int i=0;i<=FACES.length;i++)quads.add(retexture(part.getQuads(i==FACES.length?null:FACES[i]),palette));
        return new BlockStateModelPart(){
            @Override public List<BakedQuad> getQuads(Direction face){return immutable?quads.get(face==null?FACES.length:face.ordinal()):retexture(part.getQuads(face),palette);}
            @Override public boolean useAmbientOcclusion(){return part.useAmbientOcclusion();}
            @Override public Material.Baked particleMaterial(){return target.particleMaterial();}
            @Override public int materialFlags(){return target.materialFlags();}
        };
    }
    private List<BakedQuad> retexture(List<BakedQuad> source,BakedQuad.MaterialInfo[] palette){
        if(source.isEmpty())return List.of();
        var changed=new ArrayList<BakedQuad>(source.size());
        for(var q:source)changed.add(retexture(q,palette[q.direction().ordinal()],target.particleMaterial().sprite()));
        return List.copyOf(changed);
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
