// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class BorderlessWindowMixin {
    @Mixin(Window.class)
    public static class Legacy {
        @Unique private boolean ccos$borderless;
        @Unique private int ccos$decorated;

        @WrapOperation(method = "setMode", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowMonitor(JJIIIII)V"))
        private void ccos$fullscreen(long window, long monitor, int x, int y, int width, int height,
                                     int refresh, Operation<Void> original) {
            if (monitor != 0 && "borderless".equals(ConfigManager.fullscreenStyle)) {
                var mode = GLFW.glfwGetVideoMode(monitor);
                if (mode != null) {
                    var monitorX = new int[1];
                    var monitorY = new int[1];
                    GLFW.glfwGetMonitorPos(monitor, monitorX, monitorY);
                    if (!ccos$borderless) ccos$decorated = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_DECORATED);
                    GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                    ccos$borderless = true;
                    original.call(window, 0L, monitorX[0], monitorY[0], mode.width(), mode.height(), GLFW.GLFW_DONT_CARE);
                    return;
                }
            }
            if (ccos$borderless) {
                GLFW.glfwSetWindowAttrib(window, GLFW.GLFW_DECORATED, ccos$decorated);
                ccos$borderless = false;
            }
            original.call(window, monitor, x, y, width, height, refresh);
        }

        @WrapOperation(method = "setMode", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwGetWindowMonitor(J)J"))
        private long ccos$preserveWindowedBounds(long window, Operation<Long> original) {
            return ccos$borderless ? 1L : original.call(window);
        }
    }

    @Mixin(Window.class)
    public static class Modern {
        @org.spongepowered.asm.mixin.Shadow private boolean applyBorderlessFullscreen() { throw new AssertionError(); }

        @Inject(method = "applyFullscreenMode", at = @At("HEAD"), cancellable = true)
        private void ccos$fullscreen(CallbackInfoReturnable<Boolean> callback) {
            if ("borderless".equals(ConfigManager.fullscreenStyle)) callback.setReturnValue(applyBorderlessFullscreen());
        }
    }
}
