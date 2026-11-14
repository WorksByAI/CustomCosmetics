// SPDX-License-Identifier: LGPL-3.0-or-later
package com.customcosmetics.features.bobby.v261.mixin;

import com.customcosmetics.features.bobby.v261.Bobby;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @ModifyArg(method = "tickServer", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"), index = 1)
    private int bobbyViewDistanceOverwrite(int viewDistance) {
        int overwrite = Bobby.getInstance().getConfig().getViewDistanceOverwrite();
        if (overwrite != 0) {
            viewDistance = overwrite;
        }
        return viewDistance;
    }
}
