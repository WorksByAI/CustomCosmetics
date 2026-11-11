/**
 * Skyboxify
 * A skybox mod that allows you to use OptiFine skies in Fabric 1.21+
 * <p>
 * Copyright (C) 2025-2026 lowercasebtw
 * Copyright (C) 2025-2026 Contributors to the project retain their copyright
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * "MINECRAFT" LINKING EXCEPTION TO THE GPL
 */

package com.customcosmetics.mixin;

import com.customcosmetics.features.sky.Skyboxify;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyPass26_3Mixin {
    @Inject(method="extractRenderState",at=@At("HEAD"))
    private void ccos$prepareSky(CallbackInfo callback,@Local(argsOnly=true) ClientLevel level){Skyboxify.prepare(level);}
    @WrapOperation(method="extractRenderState",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/dimension/DimensionType;skybox()Lnet/minecraft/world/level/dimension/DimensionType$Skybox;"))
    private DimensionType.Skybox ccos$nether(DimensionType type,Operation<DimensionType.Skybox> original,@Local(argsOnly=true) ClientLevel level){
        return Skyboxify.active(level)&&level.dimension().equals(Level.NETHER)?DimensionType.Skybox.OVERWORLD:original.call(type);
    }
    @WrapWithCondition(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SkyRenderer;renderSkyDisc(Lcom/mojang/renderpearl/api/commands/RenderPass;Lorg/joml/Vector3fc;)V"))
    private boolean ccos$disc(SkyRenderer sky,RenderPass pass,Vector3fc color){return Skyboxify.disc();}
    @WrapWithCondition(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SkyRenderer;renderDarkDisc(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private boolean ccos$darkDisc(SkyRenderer sky,RenderPass pass){return Skyboxify.disc();}
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SkyRenderer;renderEndSky(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void ccos$end(SkyRenderer sky,RenderPass pass,Operation<Void> original){original.call(sky,pass);Skyboxify.render(pass);}
    @WrapWithCondition(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SkyRenderer;renderSunMoonAndStars(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/blaze3d/vertex/PoseStack;FFFLnet/minecraft/world/level/MoonPhase;FF)V"))
    private boolean ccos$sky(SkyRenderer sky,RenderPass pass,PoseStack pose,float sun,float moon,float star,MoonPhase phase,float rain,float brightness){
        return !Skyboxify.render(pass)||!Minecraft.getInstance().level.dimension().equals(Level.NETHER);
    }
}
