// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;
import com.customcosmetics.features.map.MapEditor;
import com.customcosmetics.features.map.Freecam;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

public final class MapEditorMixin {
    @Mixin(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class)
    public static class BlockEntities {
        @Inject(method="tryExtractRenderState",at=@At("HEAD"),cancellable=true)
        private void hidden(CallbackInfoReturnable<net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> cir,@com.llamalad7.mixinextras.sugar.Local(argsOnly=true) net.minecraft.world.level.block.entity.BlockEntity entity){if(MapEditor.hideBlockEntity(entity))cir.setReturnValue(null);}
    }
    @Pseudo @Mixin(targets="net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl",remap=false)
    public static class Indigo {
        @Shadow private BlockPos pos;
        @Inject(method="shouldCullFace",at=@At("HEAD"),cancellable=true)
        private void adjacent(net.minecraft.core.Direction face,CallbackInfoReturnable<Boolean> cir){if(!MapEditor.visible().isEmpty()&&face!=null&&MapEditor.revealsFace(pos.relative(face)))cir.setReturnValue(false);}
        @WrapMethod(method="tesselateBlock")
        private void material(net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter output,float x,float y,float z,BlockAndTintGetter world,BlockPos p,BlockState state,BlockStateModel model,long seed,Operation<Void> original){original.call(output,x,y,z,world,p,state,MapEditor.wrap(model,p),seed);}
    }
    @Mixin(ModelBlockRenderer.class)
    public static class Vanilla {
        @Inject(method="shouldRenderFace",at=@At("HEAD"),cancellable=true)
        private void adjacent(BlockAndTintGetter world,BlockState state,net.minecraft.core.Direction face,BlockPos neighbor,CallbackInfoReturnable<Boolean> cir){if(MapEditor.revealsFace(neighbor))cir.setReturnValue(true);}
        @WrapMethod(method="tesselateBlock")
        private void material(BlockQuadOutput output,float x,float y,float z,BlockAndTintGetter world,BlockPos p,BlockState state,BlockStateModel model,long seed,Operation<Void> original){original.call(output,x,y,z,world,p,state,MapEditor.wrap(model,p),seed);}
    }
    @Pseudo @Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",remap=false)
    public static class Sodium {
        @WrapMethod(method="renderModel")
        private void material(BlockStateModel model,BlockState state,BlockPos p,BlockPos origin,Operation<Void> original){original.call(MapEditor.wrap(model,p),state,p,origin);}
    }
    @Mixin(net.minecraft.client.renderer.chunk.VisGraph.class)
    public static class Visibility {
        @Inject(method="setOpaque",at=@At("HEAD"),cancellable=true)
        private void transparent(BlockPos p,CallbackInfo ci){if(MapEditor.revealsFace(p))ci.cancel();}
    }
    @Mixin(net.minecraft.client.renderer.block.BlockModelLighter.class)
    public static class Lighting {
        @Inject(method={"prepareQuadAmbientOcclusion","prepareQuadFlat"},at=@At("HEAD"),cancellable=true)
        private void exposed(CallbackInfo ci,@com.llamalad7.mixinextras.sugar.Local(argsOnly=true) BlockAndTintGetter world,@com.llamalad7.mixinextras.sugar.Local(argsOnly=true) BlockPos p,@com.llamalad7.mixinextras.sugar.Local(argsOnly=true) net.minecraft.client.resources.model.geometry.BakedQuad quad,@com.llamalad7.mixinextras.sugar.Local(argsOnly=true) com.mojang.blaze3d.vertex.QuadInstance output){if(MapEditor.visible().isEmpty())return;var neighbor=p.relative(quad.direction());if(MapEditor.revealsFace(neighbor)){output.setLightCoords(MapEditor.visualLight(world,neighbor));output.setColor(net.minecraft.util.ARGB.gray(world.cardinalLighting().byFace(quad.direction())));ci.cancel();}}
    }
    @Pseudo @Mixin(targets="net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator",remap=false)
    public static class IndigoLighting {
        @Shadow private BlockPos pos;
        @Shadow private BlockAndTintGetter level;
        @Shadow @Final public float[] ao;
        @Shadow @Final public int[] light;
        @Inject(method="compute",at=@At("HEAD"),cancellable=true)
        private void exposed(@org.spongepowered.asm.mixin.injection.Coerce net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView quad,boolean vanilla,CallbackInfo ci){if(MapEditor.visible().isEmpty())return;var neighbor=pos.relative(quad.lightFace());if(MapEditor.revealsFace(neighbor)){java.util.Arrays.fill(ao,level.cardinalLighting().byFace(quad.lightFace()));java.util.Arrays.fill(light,MapEditor.visualLight(level,neighbor));ci.cancel();}}
    }
    @Pseudo @Mixin(targets="net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess",remap=false)
    public static class SodiumLighting {
        @Shadow protected BlockAndTintGetter level;
        @Shadow public static int packBL(int value){throw new AssertionError();}
        @Shadow public static int packSL(int value){throw new AssertionError();}
        @Shadow public static int packAO(float value){throw new AssertionError();}
        @Inject(method="compute",at=@At("HEAD"),cancellable=true)
        private void transparent(int x,int y,int z,CallbackInfoReturnable<Integer> cir){if(MapEditor.visible().isEmpty())return;var p=new BlockPos(x,y,z);if(MapEditor.revealsFace(p)){int light=MapEditor.visualLight(level,p);cir.setReturnValue(packBL(light>>4&15)|packSL(light>>20&15)|packAO(1f));}}
    }
    @Pseudo @Mixin(targets="net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext",remap=false)
    public static class SodiumFaces {
        @Shadow protected BlockPos pos;
        @Inject(method="shouldDrawSide",at=@At("HEAD"),cancellable=true)
        private void adjacent(net.minecraft.core.Direction face,CallbackInfoReturnable<Boolean> cir){if(!MapEditor.visible().isEmpty()&&MapEditor.revealsFace(pos.relative(face)))cir.setReturnValue(true);}
    }
    @Pseudo @Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask",remap=false)
    public static class SodiumVisibility {
        @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="execute",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;isSolidRender()Z"))
        private boolean transparent(BlockState state,Operation<Boolean> original,@com.llamalad7.mixinextras.sugar.Local(ordinal=0) BlockPos.MutableBlockPos p){return original.call(state)&&!MapEditor.revealsFace(p);}
    }
    @Mixin(Minecraft.class)
    public static class Input {
        @Shadow private int rightClickDelay;
        @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
        private void left(CallbackInfoReturnable<Boolean> cir){if(MapEditor.click(0)||Freecam.active())cir.setReturnValue(false);}
        @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
        private void hold(boolean attacking,CallbackInfo ci){if(Freecam.active()||MapEditor.editing&&MapEditor.enabled)ci.cancel();}
        @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
        private void right(CallbackInfo ci){if(MapEditor.click(1)||Freecam.active()){rightClickDelay=4;ci.cancel();}}
        @Inject(method="pickBlockOrEntity",at=@At("HEAD"),cancellable=true)
        private void middle(CallbackInfo ci){if(MapEditor.click(2)||Freecam.active())ci.cancel();}
    }
}
