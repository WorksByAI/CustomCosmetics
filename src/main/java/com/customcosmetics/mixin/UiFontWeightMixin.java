// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.compatibility.UiFontWeight;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.font.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

public final class UiFontWeightMixin {
    @Mixin(TrueTypeGlyphProviderDefinition.class)
    public abstract static class Definition {
        @Shadow @Final private Identifier location;
        @Inject(method="load",at=@At("RETURN"))
        private void customcosmetics$ownFont(ResourceManager resources,CallbackInfoReturnable<GlyphProvider> result){
            if(UiFontWeight.owns(location)&&result.getReturnValue() instanceof UiFontWeight.Provider provider)provider.customcosmetics$weight();
        }
    }
    @Mixin(TrueTypeGlyphProvider.class)
    public abstract static class Provider implements UiFontWeight.Provider {
        @Shadow private FT_Face face;
        public void customcosmetics$weight(){UiFontWeight.register(face);}
        @Inject(method="close",at=@At("HEAD"))
        private void customcosmetics$release(CallbackInfo callback){UiFontWeight.remove(face);}
        @WrapOperation(method="loadGlyph",at=@At(value="INVOKE",target="Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
        private int customcosmetics$metrics(FT_Face face,int glyph,int flags,Operation<Integer> original){return UiFontWeight.loaded(face,original.call(face,glyph,UiFontWeight.flags(face,flags)));}
    }
    @Mixin(NativeImage.class)
    public abstract static class Bitmap {
        @WrapOperation(method="copyFromFont",at=@At(value="INVOKE",target="Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I"))
        private int customcosmetics$bitmap(FT_Face face,int glyph,int flags,Operation<Integer> original){return UiFontWeight.loaded(face,original.call(face,glyph,UiFontWeight.flags(face,flags)));}
    }
}
