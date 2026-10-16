// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

/** Replays the original appearance into an isolated depth buffer, then composites it over terrain.
 * Keeping model depth tests prevents capes and armor from showing through the player's body.
 * Main world depth, original textures, light values, poses and model tints remain unchanged. */
public final class ChamsRenderAccess {
    private static SubmitNodeStorage captured;
    private static boolean active,hasGeometry,replaying;
    private static Object target;
    private static Object targetColorFormat,targetDepthFormat;
    private static org.joml.Matrix4f view;
    private static final boolean EXPLICIT_PASS=Arrays.stream(RenderSetup.class.getDeclaredFields()).noneMatch(f->f.getName().equals("outputTarget"));
    private static Object legacyDispatcher;
    private static Double emptyDepth;
    private ChamsRenderAccess(){}

    public static void begin(){captured=null;view=null;active=true;hasGeometry=false;}
    public static SubmitNodeCollector capture(SubmitNodeCollector original){
        if(!active)return original; // GUI previews and first-person rendering are left alone.
        if(captured==null){captured=new SubmitNodeStorage();view=new org.joml.Matrix4f(RenderSystem.getModelViewStack());}
        return (SubmitNodeCollector)collector(original,captured,SubmitNodeCollector.class);
    }
    static Object collector(Object original,Object extra,Class<?> contract){
        return Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{
            Object result=invoke(method,original,args);
            if(method.getName().equals("order"))return collector(result,invoke(method,extra,args),method.getReturnType());
            if(geometry(method.getName())){
                invoke(method,extra,appearanceArguments(method,args));hasGeometry=true;
            }
            return result;
        });
    }
    /** Glow masks belong to vanilla's outline target, never to the textured appearance replay. */
    static Object[] appearanceArguments(Method method,Object[] args){
        int count=method.getParameterCount();
        int outline=switch(method.getName()){
            case "submitModel"->count==9||count==10?8:count==7||count==8?6:-1;
            case "submitModelPart"->count>=8&&method.getParameterTypes()[count-1]==int.class?count-1:-1;
            case "submitItem"->count==8?4:-1;
            case "submitBlockModel"->count==7?6:-1;
            default->-1;
        };
        if(outline<0||method.getParameterTypes()[outline]!=int.class)return args;
        var appearance=args.clone();appearance[outline]=0;return appearance;
    }
    private static boolean geometry(String method){return switch(method){
        case "submitModel","submitModelPart","submitItem","submitBlockModel","submitCustomGeometry"->true;
        default->false; // Names, shadows, fire and leashes are not duplicated through terrain.
    };}
    /** Only active while the independent appearance frame is being rendered. */
    public static Object replayTarget(){return replaying?target:null;}
    public static void finish(LevelRenderer renderer){
        active=false;
        if(!hasGeometry||captured==null){captured=null;return;}
        // Vanilla's frame is closed here; restore its view only for this independent replay.
        var modelView=RenderSystem.getModelViewStack();modelView.pushMatrix();modelView.set(view);
        try{
            Object main=mainTarget();ensureTarget(main);
            Object encoder=call(device(),"createCommandEncoder");
            var clear=method(encoder,"clearColorAndDepthTextures",4);
            Object clearColor=clear.getParameterTypes()[1]==int.class?0:new org.joml.Vector4f(0,0,0,0);
            clear.invoke(encoder,call(target,"getColorTexture"),clearColor,call(target,"getDepthTexture"),emptyDepth());
            var dispatcherField=LevelRenderer.class.getDeclaredField("featureRenderDispatcher");dispatcherField.setAccessible(true);Object dispatcher=dispatcherField.get(renderer);
            replaying=true;
            try{if(EXPLICIT_PASS){
                Object frame=call(dispatcher,"prepareFrame",captured);
                Object pass=method(encoder,"createRenderPass",5).invoke(encoder,(Supplier<String>)()->"CustomCosmetics original appearance",call(target,"getColorTextureView"),Optional.empty(),call(target,"getDepthTextureView"),OptionalDouble.empty());
                try{RenderSystem.class.getMethod("bindDefaultUniforms",Class.forName("com.mojang.renderpearl.api.commands.RenderPass")).invoke(null,pass);method(dispatcher,"renderAllFeatures",2).invoke(null,pass,frame);}
                finally{((AutoCloseable)pass).close();((AutoCloseable)frame).close();}
            }else{
                try{call(dispatcher,"renderAllFeatures",captured);}
                catch(NoSuchMethodException legacy){renderLegacy(dispatcher);}
            }}finally{replaying=false;}
            var blit=Arrays.stream(target.getClass().getMethods()).filter(m->m.getName().equals("blitAndBlendToTexture")).findFirst().orElseThrow();
            if(blit.getParameterCount()==1)blit.invoke(target,call(main,"getColorTextureView"));
            else blit.invoke(target,call(main,"getColorTextureView"),call(main,"getDepthTextureView"));
        }catch(Exception error){throw new IllegalStateException("Cannot render shared players' original appearance",error);}
        finally{modelView.popMatrix();captured=null;view=null;hasGeometry=false;}
    }
    private static Object mainTarget()throws ReflectiveOperationException{
        var client=Minecraft.getInstance();
        try{return call(client.gameRenderer,"mainRenderTarget");}catch(NoSuchMethodException older){return call(client,"getMainRenderTarget");}
    }
    private static Object device()throws ReflectiveOperationException{return RenderSystem.class.getMethod("getDevice").invoke(null);}
    private static void renderLegacy(Object original)throws ReflectiveOperationException{
        // 26.1's dispatcher owns its queue and writes into buffered render sources.
        if(legacyDispatcher==null){
            String[] names={"modelManager","bufferSource","atlasManager","outlineBufferSource","crumblingBufferSource","font","gameRenderState"};
            Object[] args=new Object[8];args[0]=captured;
            for(int i=0;i<names.length;i++)args[i+1]=field(original,names[i]);
            legacyDispatcher=original.getClass().getConstructors()[0].newInstance(args);
        }else{
            var storage=legacyDispatcher.getClass().getDeclaredField("submitNodeStorage");storage.setAccessible(true);storage.set(legacyDispatcher,captured);
        }
        call(legacyDispatcher,"renderAllFeatures");
        call(field(original,"bufferSource"),"endBatch");
        call(field(original,"crumblingBufferSource"),"endBatch");
    }
    private static Object field(Object object,String name)throws ReflectiveOperationException{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static double emptyDepth()throws ReflectiveOperationException{
        if(emptyDepth==null){
            var source=RenderTypes.entitySolid(net.minecraft.resources.Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png"));
            Object pipeline=RenderType.class.getMethod("pipeline").invoke(source);
            Object depth=call(pipeline,"getDepthStencilState");
            // 26.2 introduced reversed Z; 26.1 still clears conventional depth to one.
            emptyDepth=call(depth,"depthTest").toString().startsWith("GREATER")?0d:1d;
        }return emptyDepth;
    }
    private static void ensureTarget(Object main)throws ReflectiveOperationException{
        int width=main.getClass().getField("width").getInt(main),height=main.getClass().getField("height").getInt(main);
        Object colorFormat=call(call(main,"getColorTexture"),"getFormat"),depthFormat=call(call(main,"getDepthTexture"),"getFormat");
        if(target!=null&&target.getClass().getField("width").getInt(target)==width&&target.getClass().getField("height").getInt(target)==height&&Objects.equals(colorFormat,targetColorFormat)&&Objects.equals(depthFormat,targetDepthFormat))return;
        closeTarget();var ctor=Class.forName("com.mojang.blaze3d.pipeline.TextureTarget").getConstructors()[0];
        if(ctor.getParameterCount()==4)target=ctor.newInstance("CustomCosmetics Chams",width,height,true);
        else if(ctor.getParameterTypes()[3]==boolean.class)target=ctor.newInstance("CustomCosmetics Chams",width,height,true,colorFormat);
        else target=ctor.newInstance("CustomCosmetics Chams",width,height,colorFormat,depthFormat);
        targetColorFormat=colorFormat;targetDepthFormat=depthFormat;
    }
    private static void closeTarget()throws ReflectiveOperationException{if(target!=null){call(target,"destroyBuffers");target=null;}}
    public static void close(){try{closeTarget();if(legacyDispatcher!=null){call(legacyDispatcher,"close");legacyDispatcher=null;}}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}finally{captured=null;active=false;hasGeometry=false;replaying=false;}}
    private static Object call(Object receiver,String name,Object...args)throws ReflectiveOperationException{return method(receiver,name,args.length).invoke(receiver,args);}
    private static Method method(Object receiver,String name,int count)throws NoSuchMethodException{
        // Public declarations avoid methods declared on package-private backend classes.
        for(Class<?> type=receiver.getClass();type!=null;type=type.getSuperclass()){
            if(Modifier.isPublic(type.getModifiers()))for(var m:type.getDeclaredMethods())if(Modifier.isPublic(m.getModifiers())&&m.getName().equals(name)&&m.getParameterCount()==count)return m;
            for(var api:type.getInterfaces())for(var m:api.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==count)return m;
        }throw new NoSuchMethodException(name);
    }
    private static Object invoke(Method method,Object receiver,Object[] args)throws Throwable{try{return method.invoke(receiver,args);}catch(InvocationTargetException error){throw error.getCause();}}
}
