package com.customcosmetics.gametest.mixin;
import com.customcosmetics.features.map.MapEditor;
import com.customcosmetics.gametest.MapAdventureGameTest;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Exercise the remote-server branch while using an isolated local test server without Mod API events. */
@Mixin(MapEditor.class)
public class MapAdventureScopeMixin {
    @WrapOperation(method="allowed",at=@At(value="INVOKE",target="Lnet/minecraft/client/Minecraft;hasSingleplayerServer()Z"))
    private static boolean remote(Minecraft client,Operation<Boolean> original){return !MapAdventureGameTest.remoteWorld&&original.call(client);}
}
