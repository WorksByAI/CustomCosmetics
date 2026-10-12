/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.mixin;

import com.customcosmetics.utils.NickRenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class NickTweaksMixin {

    @Environment(EnvType.CLIENT)
    @Mixin(Font.class)
    public static class FontMixin {

        @ModifyVariable(
            method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            argsOnly = true
        )
        private String customcosmetics$chromaPrepareString(String text) {
            return NickRenderUtils.hasSmoothGradient() ? text : NickRenderUtils.handleString(text);
        }

        @Inject(method="prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",at=@At("HEAD"),cancellable=true)
        private void customcosmetics$prepareSmoothString(String text,float x,float y,int color,boolean shadow,int background,CallbackInfoReturnable<Font.PreparedText> result) {
            if (!NickRenderUtils.hasSmoothGradient()) return;
            Font font=(Font)(Object)this;
            String shaped=font.isBidirectional()?font.bidirectionalShaping(text):text;
            FormattedCharSequence sequence=sink -> StringDecomposer.iterateFormatted(shaped,Style.EMPTY,sink);
            result.setReturnValue(font.prepareText(sequence,x,y,color,shadow,false,background));
        }

        @ModifyVariable(
            method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"),
            argsOnly = true
        )
        private FormattedCharSequence customcosmetics$chromaPrepareSequence(FormattedCharSequence text) {
            return NickRenderUtils.measureGradient(NickRenderUtils.handleCharSequence(text),(Font)(Object)this);
        }

        @ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
        private String customcosmetics$chromaWidthString(String text) {
            return NickRenderUtils.handleString(text);
        }

        @Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), cancellable = true)
        private void customcosmetics$chromaWidthFormattedText(FormattedText text, CallbackInfoReturnable<Integer> cir) {
            FormattedCharSequence visualOrder = Language.getInstance().getVisualOrder(text);
            cir.setReturnValue(((Font) (Object) this).width(visualOrder));
        }

        @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
        private FormattedCharSequence customcosmetics$chromaWidthSequence(FormattedCharSequence text) {
            return NickRenderUtils.handleCharSequence(text);
        }
    }

    @Mixin(Screen.class)
    public static class ScreenGuiTextScopeMixin {

        @Inject(method = "extractRenderState", at = @At("HEAD"))
        private void customcosmetics$beginGuiTextRenderScope(
            GuiGraphicsExtractor guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
        ) {
            Screen self = (Screen) (Object) this;
            if (NickRenderUtils.shouldEnterGuiTextSkipScope(self)) {
                NickRenderUtils.enterGuiTextRenderScope();
            }
        }

        @Inject(method = "extractRenderState", at = @At("RETURN"))
        private void customcosmetics$endGuiTextRenderScope(
            GuiGraphicsExtractor guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
        ) {
            customcosmetics$exitGuiTextRenderScopeIfNeeded();
        }

        private void customcosmetics$exitGuiTextRenderScopeIfNeeded() {
            Screen self = (Screen) (Object) this;
            if (NickRenderUtils.shouldEnterGuiTextSkipScope(self)) {
                NickRenderUtils.exitGuiTextRenderScope();
            }
        }
    }
}
