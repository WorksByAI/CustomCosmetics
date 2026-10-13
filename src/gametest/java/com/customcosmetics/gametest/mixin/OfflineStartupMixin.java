// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;
import com.customcosmetics.sync.PresenceSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Keep an isolated game test from probing the production cosmetics backend. */
@Mixin(PresenceSync.class)
public abstract class OfflineStartupMixin {
    @Inject(method="runPresenceConnectivityProbe",at=@At("HEAD"),cancellable=true)
    private static void disableProbe(CallbackInfo callback){callback.cancel();}
}
