// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.sky.skybox.renderer;

public final class SkyIrisCompat {
    private SkyIrisCompat(){}
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void assign(Object pipeline){
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris"))return;
        try{
            var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            var kind=Class.forName("net.irisshaders.iris.api.v0.IrisProgram").asSubclass(Enum.class);
            api.getMethod("assignPipeline",pipeline.getClass(),kind).invoke(api.getMethod("getInstance").invoke(null),pipeline,Enum.valueOf(kind,"SKY_TEXTURED"));
        }catch(ReflectiveOperationException|LinkageError ignored){}
    }
}
