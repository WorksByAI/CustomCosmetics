// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;

import com.customcosmetics.update.GitHubUpdateCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only the isolated game test uses a local release fixture, without contacting GitHub. */
@Mixin(GitHubUpdateCheck.class)
public class UpdateCheckOfflineMixin {
    @Inject(method = "start()V", at = @At("HEAD"), cancellable = true)
    private void offlineCheck(CallbackInfo ci) { ci.cancel(); }
}
