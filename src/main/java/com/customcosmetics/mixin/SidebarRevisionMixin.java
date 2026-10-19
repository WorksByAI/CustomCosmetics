// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.map.SidebarRevision;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Scoreboard.class)
public abstract class SidebarRevisionMixin implements SidebarRevision {
    @Unique private long customcosmetics$revision;
    @Override public long customcosmetics$sidebarRevision(){return customcosmetics$revision;}
    @Inject(method={"onObjectiveAdded","onObjectiveChanged","onObjectiveRemoved","onScoreChanged","onScoreLockChanged",
        "onPlayerRemoved","onPlayerScoreRemoved","onTeamAdded","onTeamChanged","onTeamRemoved","setDisplayObjective"},at=@At("RETURN"))
    private void customcosmetics$changed(CallbackInfo ci){customcosmetics$revision++;}
}
