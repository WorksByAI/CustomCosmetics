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
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.platform.DestFactor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import java.util.EnumMap;
import java.util.ArrayList;

/** Skyboxify's cube, blend modes and shaders using the Minecraft 26.1 renderer. */
public final class SkyRenderer26_1 implements Skyboxify.Renderer {
    private final EnumMap<Blend,RenderPipeline> pipelines=new EnumMap<>(Blend.class);
    private record Draw(RenderPipeline pipeline,GpuTextureView texture,GpuBufferSlice transform) {}
    private final ArrayList<Draw> draws=new ArrayList<>();
    private GpuBuffer vertices;
    private int indexCount;
    @Override public void prepare(){
        if(vertices==null)createGeometry();
        RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS).getBuffer(indexCount);
    }
    @Override public void render(Object current,ClientLevel level,float delta){
        var indices=RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        var index=indices.getBuffer(indexCount);
        var sampler=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var textures=Minecraft.getInstance().getTextureManager();
        var modelView=new Matrix4f(RenderSystem.getModelViewStack()).rotate(Axis.YP.rotationDegrees(-90));
        try {
            Skyboxify.extract(level,delta,modelView,(blend,matrix,color,texture)->draws.add(new Draw(
                pipelines.computeIfAbsent(blend,this::pipeline),textures.getTexture(texture).getTextureView(),
                RenderSystem.getDynamicUniforms().writeTransform(matrix,color,new org.joml.Vector3f(),new Matrix4f()))));
            if(draws.isEmpty())return;
            var target=Minecraft.getInstance().getMainRenderTarget();
            try(var pass=RenderSystem.getDevice().createCommandEncoder().createRenderPass(()->"CustomCosmetics sky",target.getColorTextureView(),
                java.util.OptionalInt.empty(),target.useDepth?target.getDepthTextureView():null,java.util.OptionalDouble.empty())){
                RenderSystem.bindDefaultUniforms(pass);
                pass.setVertexBuffer(0,vertices);pass.setIndexBuffer(index,indices.type());
                for(var draw:draws){
                    pass.setPipeline(draw.pipeline());
                    pass.setUniform("DynamicTransforms",draw.transform());
                    pass.bindTexture("Sampler0",draw.texture(),sampler);
                    pass.drawIndexed(0,0,indexCount,1);
                }
            }
        }finally{draws.clear();}
    }
    private RenderPipeline pipeline(Blend mode){
        var factors=mode.getBlendFunction();
        var blend=factors==null?null:new BlendFunction(SourceFactor.valueOf(factors.srcFactor().name()),DestFactor.valueOf(factors.dstFactor().name()));
        var shader=Skyboxify.locationOrNull("core/sky26_1");
        var pipeline=RenderPipeline.builder()
            .withUniform("DynamicTransforms",com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withUniform("Projection",com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
            .withLocation(Skyboxify.locationOrNull("pipeline/sky_"+mode.getSerializedName()))
            .withVertexShader(shader).withFragmentShader(shader).withColorTargetState(new ColorTargetState(java.util.Optional.ofNullable(blend),ColorTargetState.WRITE_COLOR))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX,VertexFormat.Mode.QUADS).withSampler("Sampler0").build();
        SkyIrisCompat.assign(pipeline);
        return pipeline;
    }
    private void createGeometry(){
        try(var bytes=ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_TEX.getVertexSize()*SkyPart.COUNT*4)){
            var builder=new BufferBuilder(bytes,VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
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
