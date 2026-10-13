// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.features.blocking.BlockingPose;
import com.customcosmetics.features.animations.ViewModelAnimations;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Uses the normal GUI picture-in-picture surface with the actual first-person item model. */
public final class BlockingPreview {
    public static final class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public final Matrix4f transform;
        public final Projection projection;
        State(Matrix4f transform,Projection projection) { this.transform=transform;this.projection=projection; }
    }
    private static Method entity, update;
    private static net.minecraft.client.renderer.ProjectionMatrixBuffer projectionBuffer;
    private static Object previousProjection;
    private static Method getProjection,setProjection,getBuffer;
    private static com.mojang.blaze3d.ProjectionType previousType;
    private BlockingPreview() {}
    public static void beginProjection(Projection projection) {
        if(projectionBuffer==null)projectionBuffer=new net.minecraft.client.renderer.ProjectionMatrixBuffer("CustomCosmetics blocking preview");
        try {
            if(getProjection==null){
                var type=com.mojang.blaze3d.systems.RenderSystem.class;
                getProjection=type.getMethod("getProjectionMatrixBuffer");
                setProjection=java.util.Arrays.stream(type.getMethods()).filter(m->m.getName().equals("setProjectionMatrix")&&m.getParameterCount()==2).findFirst().orElseThrow();
                getBuffer=projectionBuffer.getClass().getMethod("getBuffer",Projection.class);
            }
            previousProjection=getProjection.invoke(null);
            previousType=com.mojang.blaze3d.systems.RenderSystem.getProjectionType();
            setProjection.invoke(null,getBuffer.invoke(projectionBuffer,projection),com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot set preview projection",error);}
    }
    public static void restoreProjection() {
        if(previousProjection!=null){
            try{setProjection.invoke(null,previousProjection,previousType);}
            catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot restore preview projection",error);}
            finally{previousProjection=null;}
        }
    }
    public static void close(){if(projectionBuffer!=null){projectionBuffer.close();projectionBuffer=null;}}
    public static void draw(GuiGraphicsExtractor graphics,int x,int y,int width,int height,float swing) {
        if(width<1||height<1)return;
        var client=Minecraft.getInstance();float screenScale=client.getWindow().getGuiScale();
        float w=(float)Math.ceil(width*screenScale),h=(float)Math.ceil(height*screenScale);
        var camera=new Projection();camera.setupPerspective(.05f,10,70,w,h);
        var pose=new PoseStack();
        ViewModelAnimations.position(pose,1);BlockingPose.apply(pose,1,swing,0);ViewModelAnimations.model(pose,1);
        var state=new State(new Matrix4f(pose.last().pose()),camera);
        ItemStack held=client.player==null?ItemStack.EMPTY:client.player.getMainHandItem();
        if(held.isEmpty()||!com.customcosmetics.features.blocking.BlockingItems.allowed(held))held=new ItemStack(Items.IRON_SWORD);
        try {
            if(update==null)update=java.util.Arrays.stream(client.getItemModelResolver().getClass().getMethods())
                .filter(method->method.getName().equals("updateForTopItem")&&method.getParameterCount()==6).findFirst().orElseThrow();
            update.invoke(client.getItemModelResolver(),state.item,held.copy(),ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,client.level,client.player,0);
            if(entity==null)entity=java.util.Arrays.stream(graphics.getClass().getMethods())
                .filter(method->method.getName().equals("entity")&&method.getParameterCount()==9).findFirst().orElseThrow();
            entity.invoke(graphics,state,1f,new Vector3f(),new Quaternionf(),null,x,y,x+width,y+height);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot draw blocking preview",error);}
    }
}
