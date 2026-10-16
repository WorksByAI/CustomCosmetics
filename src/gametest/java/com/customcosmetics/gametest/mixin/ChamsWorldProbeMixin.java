package com.customcosmetics.gametest.mixin;
import com.customcosmetics.features.party.PartyFeatures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Do not start production network sessions during the isolated renderer test. */
@Mixin(PartyFeatures.class)
public class ChamsWorldProbeMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private static void fixtureRoster(CallbackInfo callback){callback.cancel();}
}
