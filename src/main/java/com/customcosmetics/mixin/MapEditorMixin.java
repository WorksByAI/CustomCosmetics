// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;
import com.customcosmetics.features.map.MapEditor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

public final class MapEditorMixin {
    @Pseudo @Mixin(targets="net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl",remap=false)
    public static class Indigo {
        @WrapMethod(method="tesselateBlock")
        private void material(net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter output,float x,float y,float z,BlockAndTintGetter world,BlockPos p,BlockState state,BlockStateModel model,long seed,Operation<Void> original){original.call(output,x,y,z,world,p,state,MapEditor.wrap(model,p),seed);}
    }
    @Mixin(ModelBlockRenderer.class)
    public static class Vanilla {
        @WrapMethod(method="tesselateBlock")
        private void material(BlockQuadOutput output,float x,float y,float z,BlockAndTintGetter world,BlockPos p,BlockState state,BlockStateModel model,long seed,Operation<Void> original){original.call(output,x,y,z,world,p,state,MapEditor.wrap(model,p),seed);}
    }
    @Pseudo @Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",remap=false)
    public static class Sodium {
        @WrapMethod(method="renderModel")
        private void material(BlockStateModel model,BlockState state,BlockPos p,BlockPos origin,Operation<Void> original){original.call(MapEditor.wrap(model,p),state,p,origin);}
    }
    @Mixin(ClientLevel.class)
    public static class Updates {
        @Inject(method="setServerVerifiedBlockState",at=@At("HEAD"))
        private void updated(BlockPos p,BlockState state,int flags,CallbackInfo ci){MapEditor.serverUpdate(p,state);}
    }
    @Mixin(Minecraft.class)
    public static class Input {
        @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
        private void left(CallbackInfoReturnable<Boolean> cir){if(MapEditor.click(0))cir.setReturnValue(false);}
        @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
        private void hold(boolean attacking,CallbackInfo ci){if(MapEditor.editing&&MapEditor.enabled&&MapEditor.allowed((Minecraft)(Object)this))ci.cancel();}
        @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
        private void right(CallbackInfo ci){if(MapEditor.click(1))ci.cancel();}
        @Inject(method="pickBlockOrEntity",at=@At("HEAD"),cancellable=true)
        private void middle(CallbackInfo ci){if(MapEditor.click(2))ci.cancel();}
    }
}
