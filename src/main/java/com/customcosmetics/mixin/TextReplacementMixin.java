// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.text.TextReplacements;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

public final class TextReplacementMixin {
    @Mixin(Font.class)
    public static class FontLayout {
        @WrapMethod(method = "split")
        private List<FormattedCharSequence> customcosmetics$wrapReplacement(FormattedText text, int width, Operation<List<FormattedCharSequence>> original) {
            var replacement = TextReplacements.apply(text);
            var lines = original.call(replacement, width);
            return replacement == text ? lines : TextReplacements.resolvedLines(lines);
        }

        @ModifyVariable(method = "wordWrapHeight", at = @At("HEAD"), argsOnly = true)
        private FormattedText customcosmetics$replacementWrapHeight(FormattedText text) {
            return TextReplacements.apply(text);
        }

        @ModifyVariable(method = {"prepare8xTextOutline", "drawInBatch8xOutline"}, at = @At("HEAD"), argsOnly = true)
        private FormattedCharSequence customcosmetics$outlineReplacement(FormattedCharSequence text) {
            return TextReplacements.apply(text);
        }
    }

    @Mixin(ComponentRenderUtils.class)
    public static class ChatWrap {
        @WrapMethod(method = "wrapComponents")
        private static List<FormattedCharSequence> customcosmetics$wrapReplacement(FormattedText text, int width, Font font,
                                                                                    Operation<List<FormattedCharSequence>> original) {
            var replacement = TextReplacements.apply(text);
            var lines = original.call(replacement, width, font);
            return replacement == text ? lines : TextReplacements.resolvedLines(lines);
        }
    }

    @Mixin(MutableComponent.class)
    public static class ComponentText {
        @WrapMethod(method = "getVisualOrderText")
        private FormattedCharSequence customcosmetics$displayReplacement(Operation<FormattedCharSequence> original) {
            var component = (MutableComponent) (Object) this;
            var replacement = TextReplacements.apply(component);
            if (replacement == component) return original.call();
            // Minecraft keeps its own cached visual order for the original component.
            return TextReplacements.resolved(Language.getInstance().getVisualOrder(replacement));
        }
    }

    @Mixin(GuiGraphicsExtractor.class)
    public static class GuiText {
        @WrapOperation(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;getVisualOrder(Lnet/minecraft/network/chat/FormattedText;)Lnet/minecraft/util/FormattedCharSequence;"))
        private FormattedCharSequence customcosmetics$replaceBeforeShaping(Language language, FormattedText text,
                                                                           Operation<FormattedCharSequence> original) {
            var replacement = TextReplacements.apply(text);
            var sequence = original.call(language, replacement);
            return replacement == text ? sequence : TextReplacements.resolved(sequence);
        }
    }

    @Mixin(EditBox.class)
    public static class Editable {
        @ModifyReturnValue(method = "applyFormat", at = @At("RETURN"))
        private FormattedCharSequence customcosmetics$preserveInput(FormattedCharSequence text) {
            return TextReplacements.active() ? TextReplacements.resolved(text) : text;
        }

        @WrapOperation(method = {"extractWidgetRenderState", "updateTextPosition", "getScreenX"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"))
        private int customcosmetics$measureInput(Font font, String text, Operation<Integer> original) {
            if (!TextReplacements.active()) return original.call(font, text);
            return TextReplacements.rawWidth(() -> original.call(font, text));
        }
    }
}
