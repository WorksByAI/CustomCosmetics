/**
 * Skyboxify
 * A skybox mod that allows you to use OptiFine skies in Fabric 1.21+
 * <p>
 * Copyright (C) 2025-2026 lowercasebtw
 * Copyright (C) 2025-2026 Contributors to the project retain their copyright
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * "MINECRAFT" LINKING EXCEPTION TO THE GPL
 */

package com.customcosmetics.features.sky.skybox.renderer;

import com.customcosmetics.features.sky.Skyboxify;
import com.customcosmetics.features.sky.skybox.SkyPart;
import com.customcosmetics.features.sky.skybox.impl.components.Blend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import java.util.EnumMap;

/** Skyboxify's cube, blend modes and shaders using the Minecraft 26.3 renderer. */
public final class SkyRenderer26_3 implements Skyboxify.Renderer {
    private final EnumMap<Blend,RenderPipeline> pipelines=new EnumMap<>(Blend.class);
    private GpuBuffer vertices;
    private int indexCount;
    @Override public void prepare(){
        if(vertices==null)createGeometry();
        RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(indexCount);
    }
    @Override public void render(Object current,ClientLevel level,float delta){
        draw((RenderPass)current,level,delta);
    }
    private void draw(RenderPass pass,ClientLevel level,float delta){
        var indices=RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var index=indices.getBuffer(indexCount);
        var sampler=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var textures=Minecraft.getInstance().getTextureManager();
        var modelView=RenderSystem.getModelViewMatrixCopy().rotate(Axis.YP.rotationDegrees(-90));
        RenderSystem.bindDefaultUniforms(pass);
        Skyboxify.extract(level,delta,modelView,(blend,matrix,color,texture)->{
            var textureView=textures.getTexture(texture).getTextureView();
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipelines.computeIfAbsent(blend,this::pipeline)));
            pass.setVertexBuffer(0,vertices.slice());pass.setIndexBuffer(index,indices.type());
            pass.setUniform("DynamicTransforms",RenderSystem.getDynamicUniforms().writeTransform(matrix,color));
            pass.setUniform("Sampler0",textureView,sampler);
            pass.drawIndexed(indexCount,1,0,0,0);
        });
    }
    private RenderPipeline pipeline(Blend mode){
        var factors=mode.getBlendFunction();
        var blend=factors==null?null:new BlendFunction(com.mojang.renderpearl.api.pipeline.BlendFactor.valueOf(factors.srcFactor().name()),com.mojang.renderpearl.api.pipeline.BlendFactor.valueOf(factors.dstFactor().name()));
        var shader=Skyboxify.locationOrNull("core/sky26_3");
        var pipeline=RenderPipeline.builder()
            .withLocation(Skyboxify.locationOrNull("pipeline/sky_"+mode.getSerializedName()))
            .withVertexShader(shader).withFragmentShader(shader).withColorTargetState(new ColorTargetState(java.util.Optional.ofNullable(blend),GpuFormat.RGBA8_UNORM,ColorTargetState.WRITE_COLOR))
            .withVertexBinding(0,DefaultVertexFormat.POSITION_TEX).withPrimitiveTopology(PrimitiveTopology.QUADS).withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS).withBindGroupLayout(BindGroupLayouts.PROJECTION).withBindGroupLayout(BindGroupLayouts.SAMPLER0).build();
        SkyIrisCompat.assign(pipeline);
        return pipeline;
    }
    private void createGeometry(){
        try(var bytes=ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_TEX.getVertexSize()*SkyPart.COUNT*4)){
            var builder=new BufferBuilder(bytes,PrimitiveTopology.QUADS,DefaultVertexFormat.POSITION_TEX);
            for(var part:SkyPart.VALUES){
                var uv=part.getUv();var rotation=part.getRotationMatrix();float size=100;
                builder.addVertex(rotation,-size,-size,-size).setUv(uv.minU(),uv.minV());
                builder.addVertex(rotation,-size,-size,size).setUv(uv.minU(),uv.maxV());
                builder.addVertex(rotation,size,-size,size).setUv(uv.maxU(),uv.maxV());
                builder.addVertex(rotation,size,-size,-size).setUv(uv.maxU(),uv.minV());
            }
            try(var mesh=builder.buildOrThrow()){
                indexCount=mesh.drawState().indexCount();
                vertices=RenderSystem.getDevice().createBuffer(()->"CustomCosmetics sky cube",GpuBuffer.USAGE_VERTEX,mesh.vertexBuffer());
            }
        }
    }
    @Override public void close(){if(vertices!=null){vertices.close();vertices=null;}}
}
