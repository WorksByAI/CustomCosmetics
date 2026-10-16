// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.map.Freecam;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class FreecamMixin {
    @Mixin(GameRenderer.class)
    public static class Hands {
        @Inject(method="renderItemInHand",at=@At("HEAD"),cancellable=true)
        private void hands(CallbackInfo ci){if(Freecam.active())ci.cancel();}
    }
    @Mixin(Entity.class)
    public static class Look {
        @Inject(method="turn",at=@At("HEAD"),cancellable=true)
        private void turn(double x,double y,CallbackInfo ci){if(Freecam.turn((Entity)(Object)this,x,y))ci.cancel();}
    }
    @Mixin(LocalPlayer.class)
    public static class Body {
        // Keep the real entity in place. Server teleports still update it and receive their normal ACK.
        @Inject(method="aiStep",at=@At("HEAD"),cancellable=true)
        private void body(CallbackInfo ci){if(Freecam.controls((Entity)(Object)this))ci.cancel();}
        @Inject(method="sendPosition",at=@At("HEAD"),cancellable=true)
        private void movement(CallbackInfo ci){if(Freecam.controls((Entity)(Object)this))ci.cancel();}
    }
}
