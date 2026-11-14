// SPDX-License-Identifier: LGPL-3.0-or-later
package com.customcosmetics.features.bobby.v262.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.customcosmetics.features.bobby.v262.FakeChunkManager;
import com.customcosmetics.features.bobby.v262.ext.ClientChunkCacheExt;
import com.customcosmetics.features.bobby.v262.ext.GameRendererExt;
import com.customcosmetics.features.bobby.v262.util.FlawlessFrames;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements GameRendererExt {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void blockingBobbyUpdate(CallbackInfo ci) {
        if (!FlawlessFrames.isActive()) {
            return;
        }

        ClientLevel world = this.minecraft.level;
        if (world == null) {
            return;
        }

        FakeChunkManager bobbyChunkManager = ((ClientChunkCacheExt) world.getChunkSource()).bobby_getFakeChunkManager();
        if (bobbyChunkManager == null) {
            return;
        }

        ProfilerFiller profiler = Profiler.get();
        profiler.push("bobbyUpdate");

        bobbyChunkManager.update(true, () -> true);

        profiler.pop();
    }

    @Unique
    private final FogRenderer skyFogRenderer = new FogRenderer();

    @Override
    public FogRenderer bobby_getSkyFogRenderer() {
        return skyFogRenderer;
    }

    @WrapOperation(method = "extractCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;"))
    private FogData updateSkyFogRenderer(FogRenderer instance, Camera camera, int viewDistance, DeltaTracker tickCounter, float skyDarkness, ClientLevel world, Operation<FogData> operation) {
        if (viewDistance >= 32) {
            // FIXME should be passed via CameraRenderState
            skyFogRenderer.updateBuffer(skyFogRenderer.setupFog(camera, 32, tickCounter, skyDarkness, world));
        }
        return operation.call(instance, camera, viewDistance, tickCounter, skyDarkness, world);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void rotateSkyFogRenderer(CallbackInfo ci) {
        skyFogRenderer.endFrame();
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void closeSkyFogRenderer(CallbackInfo ci) {
        skyFogRenderer.close();
    }
}
