// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.sync.ExperimentalAccess;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

public final class LongSignTextMixin {
    @Mixin(AbstractSignEditScreen.class)
    public static class Editor {
        @Unique private static final int CCOS_MAX_LINE_LENGTH = 384;

        @Group(name = "ccos$signValidator", min = 1, max = 1)
        @Inject(method = "lambda$init$2(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true, require = 0)
        private void ccos$validateLegacyLine(String line, CallbackInfoReturnable<Boolean> result) {
            ccos$validateSignLine(line, result);
        }

        @Group(name = "ccos$signValidator", min = 1, max = 1)
        @Inject(method = "lambda$new$1(Lnet/minecraft/world/level/block/entity/SignBlockEntity;Ljava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
        private void ccos$validateModernLine(SignBlockEntity sign, String line, CallbackInfoReturnable<Boolean> result) {
            ccos$validateSignLine(line, result);
        }

        @Unique
        private static void ccos$validateSignLine(String line, CallbackInfoReturnable<Boolean> result) {
            // The server's sign update codec measures Java string length.
            if (ConfigManager.longSignTextEnabled && ExperimentalAccess.allowed())
                result.setReturnValue(line.length() <= CCOS_MAX_LINE_LENGTH);
        }
    }

    @Mixin(SignText.class)
    public static class Display {
        @Shadow private FormattedCharSequence[] renderMessages;
        @Unique private boolean ccos$expanded;

        @ModifyVariable(method = "getRenderMessages", at = @At("HEAD"), argsOnly = true)
        private Function<Component, FormattedCharSequence> ccos$renderSignLine(
                Function<Component, FormattedCharSequence> vanilla) {
            boolean expanded = ConfigManager.longSignTextEnabled && ExperimentalAccess.allowed();
            if (ccos$expanded != expanded) {
                // Let vanilla rebuild its cached visual lines and apply the player's text filter.
                ccos$expanded = expanded;
                renderMessages = null;
            }
            return expanded ? Component::getVisualOrderText : vanilla;
        }
    }
}
