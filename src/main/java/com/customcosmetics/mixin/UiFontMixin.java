// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;
import com.customcosmetics.gui.SystemUiFont;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.font.providers.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.io.*;
import java.nio.file.Files;

/** Scoped exclusively to the mod's font definition; all other font providers are untouched. */
@Mixin(TrueTypeGlyphProviderDefinition.class)
public abstract class UiFontMixin {
    @Shadow @Final private Identifier location;
    @Shadow private GlyphProvider load(ResourceManager manager)throws IOException {throw new AssertionError();}
    @Inject(method="unpack",at=@At("HEAD"),cancellable=true)
    private void localFont(CallbackInfoReturnable<Either<GlyphProviderDefinition.Loader,GlyphProviderDefinition.Reference>> result){
        if(!location.equals(SystemUiFont.FILE))return;
        result.setReturnValue(Either.left(manager->{
            SystemUiFont.loaded(false);
            if(SystemUiFont.source().isEmpty())return SystemUiFont.EMPTY;
            try{var provider=load(manager);SystemUiFont.loaded(true);return provider;}
            catch(IOException|RuntimeException failure){org.slf4j.LoggerFactory.getLogger("CustomCosmetics/UiFont").warn("System Microsoft YaHei could not be loaded; using Minecraft Unifont",failure);return SystemUiFont.EMPTY;}
        }));
    }
    @WrapOperation(method="load",at=@At(value="INVOKE",target="Lnet/minecraft/server/packs/resources/ResourceManager;open(Lnet/minecraft/resources/Identifier;)Ljava/io/InputStream;"))
    private InputStream systemFile(ResourceManager manager,Identifier id,Operation<InputStream> original)throws IOException{
        if(location.equals(SystemUiFont.FILE))return Files.newInputStream(SystemUiFont.source().orElseThrow(()->new IOException("Microsoft YaHei is unavailable")));
        return original.call(manager,id);
    }
}
