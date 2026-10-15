// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.compatibility.ModernUiNicknameText;
import com.customcosmetics.utils.NickRenderUtils;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Modern UI remains responsible for shaping and drawing its selected fonts. */
public final class ModernUiNicknameMixin {
    @Mixin(GuiTextRenderState.class)
    public static class GuiText {
        @Shadow @Final private Font font;
        @Shadow @Final @Mutable private FormattedCharSequence text;
        @Shadow @Final private int color,backgroundColor,x,y;
        @Shadow @Final private boolean dropShadow,includeEmpty;
        @Shadow private Font.PreparedText preparedText;
        @Unique private boolean customcosmetics$resolved;
        @WrapMethod(method="ensurePrepared")
        private Font.PreparedText customcosmetics$modernNickname(Operation<Font.PreparedText> original){
            if(customcosmetics$resolved||!ModernUiNicknameText.active(font))return original.call();
            customcosmetics$resolved=true;
            var prepared=ModernUiNicknameText.prepare(text,font,color,backgroundColor);text=prepared.masked();
            var result=original.call();
            if(result instanceof ModernUiNicknameText.Carrier carrier)carrier.customcosmetics$palette(prepared.palette());
            else if(!prepared.palette().empty()){
                // Another mod may bypass Modern UI. Never expose internal marker colors.
                text=prepared.clean();result=preparedText=font.prepareText(text,x,y,color,dropShadow,includeEmpty,backgroundColor);
            }
            return result;
        }
    }
    @Pseudo @Mixin(targets="icyllis.modernui.mc.text.ModernPreparedText",remap=false)
    public static class PreparedText implements ModernUiNicknameText.Carrier {
        @Unique private ModernUiNicknameText.Palette customcosmetics$palette=ModernUiNicknameText.Palette.EMPTY;
        @Override public ModernUiNicknameText.Palette customcosmetics$palette(){return customcosmetics$palette;}
        @Override public void customcosmetics$palette(ModernUiNicknameText.Palette value){customcosmetics$palette=value;}
        @WrapMethod(method="submitRuns",require=0)
        private void customcosmetics$nativeRuns(GuiRenderState state,Matrix3x2fc pose,ScreenRectangle scissor,Operation<Void> original){
            try(var scope=ModernUiNicknameText.enter(customcosmetics$palette)){original.call(state,pose,scissor);}
        }
    }
    @Pseudo @Mixin(targets="icyllis.modernui.mc.text.TextRunRenderState",remap=false)
    public static class GlyphRun implements ModernUiNicknameText.Carrier {
        @Unique private ModernUiNicknameText.Palette customcosmetics$palette=ModernUiNicknameText.Palette.EMPTY;
        @Override public ModernUiNicknameText.Palette customcosmetics$palette(){return customcosmetics$palette;}
        @Override public void customcosmetics$palette(ModernUiNicknameText.Palette value){customcosmetics$palette=value;}
        @Inject(method="<init>",at=@At("RETURN"))
        private void customcosmetics$capturePalette(CallbackInfo ci){customcosmetics$palette=ModernUiNicknameText.current();}
        @WrapMethod(method="buildPass",require=0)
        private void customcosmetics$nativeGlyphs(VertexConsumer vertices,float density,int r,int g,int b,int a,float baseline,boolean shadow,Operation<Void> original){
            if(customcosmetics$palette.empty()){original.call(vertices,density,r,g,b,a,baseline,shadow);return;}
            var colored=customcosmetics$palette.wrap(vertices,shadow);
            try{original.call(colored,density,r,g,b,a,baseline,shadow);}finally{colored.finish();}
        }
    }
    @Pseudo @Mixin(targets="icyllis.modernui.mc.text.TextEffectRenderState",remap=false)
    public static class Effects implements ModernUiNicknameText.Carrier {
        @Unique private ModernUiNicknameText.Palette customcosmetics$palette=ModernUiNicknameText.Palette.EMPTY;
        @Override public ModernUiNicknameText.Palette customcosmetics$palette(){return customcosmetics$palette;}
        @Override public void customcosmetics$palette(ModernUiNicknameText.Palette value){customcosmetics$palette=value;}
        @Inject(method="<init>",at=@At("RETURN"))
        private void customcosmetics$capturePalette(CallbackInfo ci){customcosmetics$palette=ModernUiNicknameText.current();}
        @WrapMethod(method="buildPass",require=0)
        private void customcosmetics$nativeEffects(VertexConsumer vertices,int r,int g,int b,int a,float baseline,boolean shadow,Operation<Void> original){
            if(customcosmetics$palette.empty()){original.call(vertices,r,g,b,a,baseline,shadow);return;}
            var colored=customcosmetics$palette.wrap(vertices,shadow);
            try{original.call(colored,r,g,b,a,baseline,shadow);}finally{colored.finish();}
        }
    }
    @Pseudo @Mixin(targets="icyllis.modernui.mc.text.TextLayout",remap=false)
    public static class Layout {
        @WrapMethod(method="drawText",require=0)
        private float customcosmetics$nativeWorldGlyphs(Matrix4fc pose,@Coerce Object buffers,float x,float y,int r,int g,int b,int a,
                boolean shadow,int mode,boolean polygonOffset,float scale,int background,int light,Operation<Float> original){
            var palette=ModernUiNicknameText.current();if(palette.empty())return original.call(pose,buffers,x,y,r,g,b,a,shadow,mode,polygonOffset,scale,background,light);
            try(var colors=new ModernUiNicknameText.Buffers(buffers,palette,shadow)){
                return original.call(pose,colors.source(),x,y,r,g,b,a,shadow,mode,polygonOffset,scale,background,light);
            }
        }
    }
    @Pseudo @Mixin(targets="icyllis.modernui.mc.text.ModernTextRenderer",remap=false)
    public static class WorldText {
        @WrapMethod(method="drawText(Ljava/lang/String;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)F",require=0)
        private float customcosmetics$string(String text,float x,float y,int color,boolean shadow,Matrix4fc pose,@Coerce Object source,Font.DisplayMode mode,int background,int light,Operation<Float> original){
            var sequence=ModernUiNicknameText.sequence(text);var prepared=ModernUiNicknameText.prepare(sequence,Minecraft.getInstance().font,color,background);
            return prepared.masked()==sequence?original.call(text,x,y,color,shadow,pose,source,mode,background,light)
                :ModernUiNicknameText.drawSequence(this,prepared.masked(),x,y,color,shadow,pose,source,mode,background,light);
        }
        @WrapMethod(method="drawText(Lnet/minecraft/network/chat/FormattedText;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)F",require=0)
        private float customcosmetics$component(FormattedText text,float x,float y,int color,boolean shadow,Matrix4fc pose,@Coerce Object source,Font.DisplayMode mode,int background,int light,Operation<Float> original){
            var sequence=ModernUiNicknameText.sequence(text);var prepared=ModernUiNicknameText.prepare(sequence,Minecraft.getInstance().font,color,background);
            return prepared.masked()==sequence?original.call(text,x,y,color,shadow,pose,source,mode,background,light)
                :ModernUiNicknameText.drawSequence(this,prepared.masked(),x,y,color,shadow,pose,source,mode,background,light);
        }
        @WrapMethod(method="drawText(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)F",require=0)
        private float customcosmetics$sequence(FormattedCharSequence text,float x,float y,int color,boolean shadow,Matrix4fc pose,@Coerce Object source,Font.DisplayMode mode,int background,int light,Operation<Float> original){
            var prepared=ModernUiNicknameText.prepare(text,Minecraft.getInstance().font,color,background);
            try(var scope=ModernUiNicknameText.enter(prepared.palette())){return original.call(prepared.masked(),x,y,color,shadow,pose,source,mode,background,light);}
        }
        @WrapMethod(method="drawText8xOutline",require=0)
        private void customcosmetics$outline(FormattedCharSequence text,float x,float y,int color,int outline,Matrix4fc pose,@Coerce Object source,int light,Operation<Void> original){
            var prepared=ModernUiNicknameText.prepare(text,Minecraft.getInstance().font,color,outline);
            try(var scope=ModernUiNicknameText.enter(prepared.palette())){original.call(prepared.masked(),x,y,color,outline,pose,source,light);}
        }
    }
    @Pseudo @Mixin(targets="icyllis.modernui.mc.text.ModernStringSplitter",remap=false)
    public static class Width {
        @Inject(method="stringWidth(Ljava/lang/String;)F",at=@At("HEAD"),cancellable=true)
        private void customcosmetics$stringWidth(String text,CallbackInfoReturnable<Float> result){
            if(text==null)return;var sequence=ModernUiNicknameText.sequence(text);var processed=NickRenderUtils.handleCharSequence(sequence);
            if(processed!=sequence)result.setReturnValue(((StringSplitter)(Object)this).stringWidth(processed));
        }
        @Inject(method="stringWidth(Lnet/minecraft/network/chat/FormattedText;)F",at=@At("HEAD"),cancellable=true)
        private void customcosmetics$componentWidth(FormattedText text,CallbackInfoReturnable<Float> result){
            var sequence=ModernUiNicknameText.sequence(text);var processed=NickRenderUtils.handleCharSequence(sequence);
            if(processed!=sequence)result.setReturnValue(((StringSplitter)(Object)this).stringWidth(processed));
        }
        @ModifyVariable(method="stringWidth(Lnet/minecraft/util/FormattedCharSequence;)F",at=@At("HEAD"),argsOnly=true)
        private FormattedCharSequence customcosmetics$sequenceWidth(FormattedCharSequence text){return NickRenderUtils.handleCharSequence(text);}
    }
}
