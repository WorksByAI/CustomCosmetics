// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;
import com.customcosmetics.features.party.PartyFeatures;
import com.customcosmetics.features.party.PositionSharing;
import com.customcosmetics.sync.*;
import com.customcosmetics.gametest.PartyGameTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;
public final class PartyProbeMixin{
    @Mixin(PartyFeatures.class)public static class Features{
        @Inject(method="tick",at=@At("HEAD"),cancellable=true)
        private static void fixtureWorld(CallbackInfo ci){ci.cancel();}
    }
    @Mixin(PartySync.class)public static class Sync{
        @Inject(method="teammates",at=@At("HEAD"),cancellable=true)
        private static void fixturePlayers(CallbackInfoReturnable<Map<UUID,PartyClient.Teammate>> cir){cir.setReturnValue(PartyGameTest.remote);}
    }
    @Mixin(RealtimeSync.class)public static class Realtime{
        @Inject(method="connectionStatus",at=@At("HEAD"),cancellable=true)
        private static void fixtureStatus(CallbackInfoReturnable<String> cir){if(PartyGameTest.connectionStatus!=null)cir.setReturnValue(PartyGameTest.connectionStatus);}
    }
    @Mixin(PositionSharing.class)public static class Locations{
        @Inject(method="receiving",at=@At("HEAD"),cancellable=true)
        private static void fixtureConsent(CallbackInfoReturnable<Boolean> cir){if(PartyGameTest.sharedWorld)cir.setReturnValue(RemoteAccess.online()&&com.customcosmetics.config.ConfigManager.positionSharingReceive);}
    }
}
