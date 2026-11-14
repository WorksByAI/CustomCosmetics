// SPDX-License-Identifier: MIT
// HUD extraction/scoped styling adapted from HUD Manager, Copyright (c) 2025 i5wear.
// Modifications for CustomCosmetics. See LICENSES/HUD-Manager-MIT.txt.
package com.customcosmetics.mixin;

import com.customcosmetics.features.hud.HudLayout;
import com.customcosmetics.features.hud.HudLayout.Element;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class HudLayoutMixin {
    @Pseudo
    @Mixin(targets={"net.minecraft.client.gui.Hud","net.minecraft.client.gui.Gui"})
    public abstract static class Hud {
        @Inject(method="extractRenderState", at=@At("TAIL"))
        private void gameplayHud(GuiGraphicsExtractor graphics, DeltaTracker tick, CallbackInfo callback) {
            com.customcosmetics.features.gameplay.GameplayFeatures.drawHud(graphics, false);
        }

        @WrapMethod(method="extractOverlayMessage")
        private void ccos$OverlayMessage(GuiGraphicsExtractor g,DeltaTracker tick,Operation<Void> original){
            var o=HudLayout.active(Element.ACTION_BAR);
            if(o==null){original.call(g,tick);return;}
            if(!o.visible()||o.opacity()==0)return;
            float old=HudLayout.push(g,o);
            try{original.call(g,tick);}finally{HudLayout.pop(g,old);}
        }
        @WrapMethod(method="extractBossOverlay")
        private void ccos$BossOverlay(GuiGraphicsExtractor g,DeltaTracker tick,Operation<Void> original){
            var o=HudLayout.active(Element.BOSS_BAR);
            if(o==null){original.call(g,tick);return;}
            if(!o.visible()||o.opacity()==0)return;
            float old=HudLayout.push(g,o);
            try{original.call(g,tick);}finally{HudLayout.pop(g,old);}
        }
        @WrapMethod(method="extractCrosshair")
        private void ccos$Crosshair(GuiGraphicsExtractor g,DeltaTracker tick,Operation<Void> original){
            var o=HudLayout.active(Element.CROSSHAIR);
            if(o==null){original.call(g,tick);return;}
            if(!o.visible()||o.opacity()==0)return;
            float old=HudLayout.push(g,o);
            try{original.call(g,tick);}finally{HudLayout.pop(g,old);}
        }
        @WrapMethod(method="extractHotbarAndDecorations")
        private void ccos$HotbarAndDecorations(GuiGraphicsExtractor g,DeltaTracker tick,Operation<Void> original){
            var o=HudLayout.active(Element.HOTBAR);
            if(o==null){original.call(g,tick);return;}
            if(!o.visible()||o.opacity()==0)return;
            float old=HudLayout.push(g,o);
            try{original.call(g,tick);}finally{HudLayout.pop(g,old);}
        }
        @WrapMethod(method="extractTabList")
        private void ccos$TabList(GuiGraphicsExtractor g,DeltaTracker tick,Operation<Void> original){
            var o=HudLayout.active(Element.TAB);
            if(o==null){original.call(g,tick);return;}
            if(!o.visible()||o.opacity()==0)return;
            float old=HudLayout.push(g,o);
            try{original.call(g,tick);}finally{HudLayout.pop(g,old);}
        }
        @WrapMethod(method="extractScoreboardSidebar")
        private void ccos$ScoreboardSidebar(GuiGraphicsExtractor g,DeltaTracker tick,Operation<Void> original){
            var o=HudLayout.active(Element.SCOREBOARD);
            if(o==null){original.call(g,tick);return;}
            if(!o.visible()||o.opacity()==0)return;
            float old=HudLayout.push(g,o);
            try{original.call(g,tick);}finally{HudLayout.pop(g,old);}
        }
        @WrapMethod(method="extractTitle")
        private void ccos$Title(GuiGraphicsExtractor g,DeltaTracker tick,Operation<Void> original){
            var o=HudLayout.active(Element.TITLE);
            if(o==null){original.call(g,tick);return;}
            if(!o.visible()||o.opacity()==0)return;
            float old=HudLayout.push(g,o);
            try{original.call(g,tick);}finally{HudLayout.pop(g,old);}
        }
        @WrapMethod(method="extractEffects")
        private void ccos$Effects(GuiGraphicsExtractor g,DeltaTracker tick,Operation<Void> original){
            var o=HudLayout.active(Element.EFFECTS);
            if(o==null){original.call(g,tick);return;}
            if(!o.visible()||o.opacity()==0)return;
            float old=HudLayout.push(g,o);
            try{original.call(g,tick);}finally{HudLayout.pop(g,old);}
        }
        @WrapMethod(method="extractSelectedItemName")
        private void ccos$SelectedItemName(GuiGraphicsExtractor g,Operation<Void> original){
            var o=HudLayout.active(Element.ITEM_NAME);
            if(o==null){original.call(g);return;}
            if(!o.visible()||o.opacity()==0)return;
            float old=HudLayout.push(g,o);
            try{original.call(g);}finally{HudLayout.pop(g,old);}
        }
    }
    @Mixin(net.minecraft.client.gui.components.DebugScreenOverlay.class)
    public abstract static class Debug {
        @WrapMethod(method="extractRenderState")
        private void ccos$layout(GuiGraphicsExtractor g,Operation<Void> original){
            var o=HudLayout.active(Element.DEBUG);
            if(o==null){original.call(g);return;}
            if(!o.visible()||o.opacity()==0)return;
            float old=HudLayout.push(g,o);
            try{original.call(g);}finally{HudLayout.pop(g,old);}
        }
    }
    @Mixin(net.minecraft.client.gui.components.SubtitleOverlay.class)
    public abstract static class Subtitle {
        @WrapMethod(method="extractRenderState")
        private void ccos$layout(GuiGraphicsExtractor g,Operation<Void> original){
            var o=HudLayout.active(Element.SUBTITLES);
            if(o==null){original.call(g);return;}
            if(!o.visible()||o.opacity()==0)return;
            float old=HudLayout.push(g,o);
            try{original.call(g);}finally{HudLayout.pop(g,old);}
        }
    }
    @Mixin(net.minecraft.client.gui.components.toasts.ToastManager.class)
    public abstract static class Toast {
        @WrapMethod(method="extractRenderState")
        private void ccos$layout(GuiGraphicsExtractor g,Operation<Void> original){
            var o=HudLayout.active(Element.TOAST);
            if(o==null){original.call(g);return;}
            if(!o.visible()||o.opacity()==0)return;
            float old=HudLayout.push(g,o);
            try{original.call(g);}finally{HudLayout.pop(g,old);}
        }
    }
    @Mixin(GuiGraphicsExtractor.class)
    public abstract static class Tooltips {
        @WrapOperation(method="extractDeferredElements",at=@At(value="INVOKE",target="Ljava/lang/Runnable;run()V"))
        private void ccos$layout(Runnable task,Operation<Void> original){
            var o=HudLayout.active(Element.TOOLTIP);
            if(o==null){original.call(task);return;}
            if(!o.visible()||o.opacity()==0)return;
            var g=(GuiGraphicsExtractor)(Object)this;
            float old=HudLayout.push(g,o);
            try{original.call(task);}finally{HudLayout.pop(g,old);}
        }
    }
    @Mixin(GuiTextRenderState.class)
    public abstract static class Text {
        @ModifyVariable(method="<init>",at=@At("CTOR_HEAD"),ordinal=2,argsOnly=true)
        private static int ccos$foreground(int color){return HudLayout.tint(color);}
        @ModifyVariable(method="<init>",at=@At("CTOR_HEAD"),ordinal=3,argsOnly=true)
        private static int ccos$background(int color){return HudLayout.tint(color);}
    }
    @Mixin(GuiItemRenderState.class)
    public abstract static class Item implements HudLayout.OpacityState {
        @Unique private float ccos$alpha;
        @Inject(method="<init>",at=@At("RETURN"))
        private void ccos$capture(CallbackInfo ci){ccos$alpha=HudLayout.opacity();}
        @Override public float ccos$opacity(){return ccos$alpha;}
    }
    @Mixin(net.minecraft.client.gui.render.GuiRenderer.class)
    public abstract static class Items {
        @WrapMethod(method="submitBlitFromItemAtlas")
        private void ccos$item(GuiItemRenderState state,net.minecraft.client.gui.render.GuiItemAtlas.SlotView slot,Operation<Void> original){
            float opacity=((HudLayout.OpacityState)(Object)state).ccos$opacity();
            if(opacity==1){original.call(state,slot);return;}
            float old=HudLayout.opacity(opacity);
            try{original.call(state,slot);}finally{HudLayout.opacity(old);}
        }
    }
    @Mixin(BlitRenderState.class)
    public abstract static class BlitLegacy {
        @ModifyVariable(method="<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V",at=@At("CTOR_HEAD"),ordinal=4,argsOnly=true)
        private static int ccos$tint4(int color){return HudLayout.tint(color);}
    }
    @Mixin(BlitRenderState.class)
    public abstract static class Blit261 {
        @ModifyVariable(method="<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2f;IIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V",at=@At("CTOR_HEAD"),ordinal=4,argsOnly=true)
        private static int ccos$tint4(int color){return HudLayout.tint(color);}
    }
    @Mixin(ColoredRectangleRenderState.class)
    public abstract static class ColoredRectangleLegacy {
        @ModifyVariable(method="<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIIILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V",at=@At("CTOR_HEAD"),ordinal=4,argsOnly=true)
        private static int ccos$tint4(int color){return HudLayout.tint(color);}
        @ModifyVariable(method="<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIIILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V",at=@At("CTOR_HEAD"),ordinal=5,argsOnly=true)
        private static int ccos$tint5(int color){return HudLayout.tint(color);}
    }
    @Mixin(TiledBlitRenderState.class)
    public abstract static class TiledBlitLegacy {
        @ModifyVariable(method="<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2f;IIIIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V",at=@At("CTOR_HEAD"),ordinal=6,argsOnly=true)
        private static int ccos$tint6(int color){return HudLayout.tint(color);}
    }
    @Mixin(BlitRenderState.class)
    public abstract static class BlitModern {
        @ModifyVariable(method="<init>(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V",at=@At("CTOR_HEAD"),ordinal=4,argsOnly=true)
        private static int ccos$tint4(int color){return HudLayout.tint(color);}
    }
    @Mixin(ColoredRectangleRenderState.class)
    public abstract static class ColoredRectangleModern {
        @ModifyVariable(method="<init>(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIIILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V",at=@At("CTOR_HEAD"),ordinal=4,argsOnly=true)
        private static int ccos$tint4(int color){return HudLayout.tint(color);}
        @ModifyVariable(method="<init>(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIIILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V",at=@At("CTOR_HEAD"),ordinal=5,argsOnly=true)
        private static int ccos$tint5(int color){return HudLayout.tint(color);}
    }
    @Mixin(TiledBlitRenderState.class)
    public abstract static class TiledBlitModern {
        @ModifyVariable(method="<init>(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2f;IIIIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V",at=@At("CTOR_HEAD"),ordinal=6,argsOnly=true)
        private static int ccos$tint6(int color){return HudLayout.tint(color);}
    }
}
