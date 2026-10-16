package com.customcosmetics.gametest.mixin;
import com.customcosmetics.sync.RealtimeSync;
import com.customcosmetics.gametest.SkinGameTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;
@Mixin(RealtimeSync.class)
public class SkinPeerFixtureMixin {
    @Inject(method="directPeers",at=@At("HEAD"),cancellable=true)
    private static void fixture(CallbackInfoReturnable<Set<UUID>> ci){ci.setReturnValue(SkinGameTest.DIRECT);}
}
