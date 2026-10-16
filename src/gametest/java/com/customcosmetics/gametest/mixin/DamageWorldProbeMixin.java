package com.customcosmetics.gametest.mixin;
import com.customcosmetics.features.damage.DamageSplash;
import com.customcosmetics.gametest.DamageSplashGameTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Local integrated world only; no Hypixel connection is used by this fixture. */
@Mixin(DamageSplash.class)
public abstract class DamageWorldProbeMixin {
    @Inject(method="isSkyblock",at=@At("HEAD"),cancellable=true)
    private static void fixture(Minecraft client,CallbackInfoReturnable<Boolean> result){
        if(DamageSplashGameTest.fixtureWorld)result.setReturnValue(client.level!=null&&client.player!=null);
    }
}
