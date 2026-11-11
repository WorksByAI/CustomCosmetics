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
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=LevelRenderer.class,priority=900)
public abstract class SkyPass26_2Mixin {
    @WrapOperation(method="addSkyPass",at=@At(value="FIELD",target="Lnet/minecraft/client/renderer/state/level/SkyRenderState;skybox:Lnet/minecraft/world/level/dimension/DimensionType$Skybox;"))
    private Skybox ccos$nether(SkyRenderState state,Operation<Skybox> original){
        var level=Minecraft.getInstance().level;
        return Skyboxify.active(level)&&level.dimension().equals(Level.NETHER)?Skybox.OVERWORLD:original.call(state);
    }
    @WrapWithCondition(method="lambda$addSkyPass$0",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SkyRenderer;renderSkyDisc(I)V"))
    private boolean ccos$disc(SkyRenderer sky,int color){return Skyboxify.disc();}
    @WrapWithCondition(method="lambda$addSkyPass$0",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SkyRenderer;renderDarkDisc()V"))
    private boolean ccos$darkDisc(SkyRenderer sky){return Skyboxify.disc();}
    @WrapOperation(method="lambda$addSkyPass$0",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SkyRenderer;renderEndSky()V"))
    private void ccos$end(SkyRenderer sky,Operation<Void> original){
        original.call(sky);Skyboxify.render(null);
    }
    @WrapWithCondition(method="lambda$addSkyPass$0",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/SkyRenderer;renderSunMoonAndStars(Lcom/mojang/blaze3d/vertex/PoseStack;FFFLnet/minecraft/world/level/MoonPhase;FF)V"))
    private boolean ccos$sky(SkyRenderer sky,PoseStack pose,float sun,float moon,float star,MoonPhase phase,float rain,float brightness){
        return !Skyboxify.render(null)||!Minecraft.getInstance().level.dimension().equals(Level.NETHER);
    }
}
