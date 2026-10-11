/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.mixin;

import com.customcosmetics.utils.NickRenderUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class ModMenuNickTweaksMixin {

    @Mixin(
        targets = {
            "com.terraformersmc.modmenu.gui.ModsScreen",
            "com.terraformersmc.modmenu.gui.ModMenuOptionsScreen"
        },
        remap = false
    )
    public static class ModMenuScreenMixin {

        @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                shift = At.Shift.AFTER
            )
        )
        private void customcosmetics$beginModMenuTextRenderScope(
            GuiGraphicsExtractor drawContext,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
        ) {
            NickRenderUtils.beginModMenuTextRenderScope();
        }

        @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN")
        )
        private void customcosmetics$endModMenuTextRenderScope(
            GuiGraphicsExtractor drawContext,
            int mouseX,
            int mouseY,
            float delta,
            CallbackInfo ci
        ) {
            NickRenderUtils.endModMenuTextRenderScope();
        }
    }
}
