package com.customcosmetics.gametest.mixin;
import com.customcosmetics.gametest.MapSpecialGameTest;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class)
public class MapSpecialProbe {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V",at=@At("HEAD"))
    private void observe(Packet<?> packet,CallbackInfo ci){if(MapSpecialGameTest.observe)MapSpecialGameTest.packets.add(packet.getClass().getSimpleName());}
}
