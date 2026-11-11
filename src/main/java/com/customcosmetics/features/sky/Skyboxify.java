// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.sky;

import com.customcosmetics.features.sky.skybox.SkyboxResourceHelper;
import com.customcosmetics.features.sky.skybox.impl.Skybox;
import com.customcosmetics.features.sky.skybox.impl.components.Blend;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.FeatureModules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.List;

public final class Skyboxify {
    @FunctionalInterface public interface LayerSink {
        void draw(Blend blend,Matrix4f modelView,Vector4f color,Identifier texture);
    }
    public interface Renderer extends AutoCloseable {
        void prepare();
        void render(Object pass,ClientLevel level,float delta);
        @Override void close();
    }
    private static final boolean EXTERNAL=FabricLoader.getInstance().isModLoaded("skyboxify");
    private static List<Skybox> skies=List.of();
    private static Renderer renderer;
    private static List<Skybox> preparedSkies;
    private static ResourceKey<Level> preparedDimension;
    private static boolean preparedFallback;
    private static boolean failed;
    private Skyboxify(){}
    public static void init(){
        if(EXTERNAL)return;
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(locationOrNull("skybox_reader"),new SkyboxResourceHelper());
        ClientTickEvents.END_LEVEL_TICK.register(Skyboxify::tick);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client->{if(renderer!=null)renderer.close();});
    }
    public static Identifier locationOrNull(String path){return Identifier.fromNamespaceAndPath("customcosmetics",path);}
    public static Identifier dimension(int old){return switch(old){
        case -1 -> Identifier.withDefaultNamespace("the_nether");
        case 0 -> Identifier.withDefaultNamespace("overworld");
        case 1 -> Identifier.withDefaultNamespace("the_end");
        case 4 -> Identifier.fromNamespaceAndPath("aether","the_aether");
        case 7 -> Identifier.fromNamespaceAndPath("twilightforest","twilight_forest");
        default -> null;
    };}
    public static void replace(List<Skybox> loaded){skies=loaded;preparedSkies=null;failed=false;}
    public static List<Skybox> skies(){return skies;}
    public static boolean enabled(){return !EXTERNAL&&!failed&&ConfigManager.customSkyEnabled&&com.customcosmetics.sync.ExperimentalAccess.allowed()&&FeatureModules.enabled(FeatureModules.Feature.SKYBOX);}
    public static boolean debug(){return ConfigManager.customSkyDebug;}
    public static boolean fallback(){return ConfigManager.customSkyFallback;}
    public static boolean active(ClientLevel level){
        if(!enabled()||level==null)return false;
        var key=level.dimension();
        for(int i=0;i<skies.size();i++)if(matches(skies.get(i),key))return true;
        return false;
    }
    private static boolean matches(Skybox sky,ResourceKey<Level> key){
        var dimension=sky.dimension();
        return dimension.equals(key)||fallback()&&dimension.equals(Level.OVERWORLD)&&!key.equals(Level.NETHER)&&!key.equals(Level.END);
    }
    public static boolean disc(){return ConfigManager.customSkyDisc||!active(Minecraft.getInstance().level);}
    public static boolean sunMoon(){return !enabled()||ConfigManager.customSkySunMoon;}
    public static boolean stars(){return ConfigManager.customSkyStars||!active(Minecraft.getInstance().level);}
    private static void tick(ClientLevel level){if(active(level))for(var sky:skies)sky.tick(level);}
    public static void extract(ClientLevel level,float delta,Matrix4f view,LayerSink sink){
        for(var sky:skies)if(sky.isActive()&&matches(sky,level.dimension()))sky.extractRenderState(sink,level,view,delta);
    }
    private static String suffix(){return FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString().split("[.+-]")[1];}
    public static Camera camera(){
        var game=Minecraft.getInstance().gameRenderer;
        return "1".equals(Version.SUFFIX)?Camera26_1.get(game):game.mainCamera();
    }
    private static final class Version { static final String SUFFIX=suffix(); }
    private static final class Camera26_1 {
        private static final java.lang.invoke.MethodHandle ACCESS=access();
        private static java.lang.invoke.MethodHandle access(){
            try{return java.lang.invoke.MethodHandles.publicLookup().findVirtual(net.minecraft.client.renderer.GameRenderer.class,"getMainCamera",java.lang.invoke.MethodType.methodType(Camera.class));}
            catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        private static Camera get(net.minecraft.client.renderer.GameRenderer game){
            try{return (Camera)ACCESS.invokeExact(game);}catch(Throwable e){throw new IllegalStateException(e);}
        }
    }
    public static void prepare(ClientLevel level){
        if(level==null||ready(level)||!active(level))return;
        try{
            // Texture uploads must finish before opening the world sky pass.
            var textures=Minecraft.getInstance().getTextureManager();
            var dimension=level.dimension();
            for(var sky:skies)if(matches(sky,dimension))for(var layer:sky.layers())textures.getTexture(layer.texture());
            renderer().prepare();
            preparedSkies=skies;
            preparedDimension=dimension;
            preparedFallback=fallback();
        }catch(Exception|LinkageError e){reportFailure(e);}
    }
    private static boolean ready(ClientLevel level){return preparedSkies==skies&&level.dimension().equals(preparedDimension)&&preparedFallback==fallback();}
    public static boolean render(Object pass){
        var client=Minecraft.getInstance();
        if(!active(client.level))return false;
        if(pass==null)prepare(client.level);
        if(!ready(client.level))return false;
        try{
            renderer().render(pass,client.level,client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            return true;
        }catch(Exception|LinkageError e){
            reportFailure(e);
            return false;
        }
    }
    private static Renderer renderer() throws ReflectiveOperationException {
        if(renderer==null)renderer=(Renderer)Class.forName("com.customcosmetics.features.sky.skybox.renderer.SkyRenderer26_"+Version.SUFFIX).getConstructor().newInstance();
        return renderer;
    }
    private static void reportFailure(Throwable error){
        failed=true;
        com.customcosmetics.client.CustomCosmeticsClient.LOGGER.error("Custom sky rendering failed",error);
    }
}
