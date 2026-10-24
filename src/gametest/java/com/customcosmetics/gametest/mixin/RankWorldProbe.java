// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;
import com.customcosmetics.features.animations.PresenceWorld;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Hypixel scope fixture only; no network packets or production bypass are installed. */
@Mixin(PresenceWorld.class)
public class RankWorldProbe {
    @org.spongepowered.asm.mixin.Unique private static boolean customcosmetics$rankTestWorld;
    @Inject(method="isHypixel",at=@At("HEAD"),cancellable=true)
    private static void rankTestWorld(Minecraft client,CallbackInfoReturnable<Boolean> result){if(customcosmetics$rankTestWorld)result.setReturnValue(true);}
}
