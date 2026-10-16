package com.customcosmetics.gametest.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.customcosmetics.gametest.FreecamGameTest;

public final class FreecamProbe {
    @Mixin(Connection.class)
    public static class Packets {
        @Inject(method="send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",at=@At("HEAD"))
        private void record(Packet<?> packet,io.netty.channel.ChannelFutureListener listener,boolean flush,CallbackInfo ci){
            String name=packet.getClass().getName();name=name.substring(name.lastIndexOf('.')+1);if(FreecamGameTest.observe&&name.startsWith("Serverbound"))FreecamGameTest.packets.add(name);
        }
    }
    @Mixin(Minecraft.class)
    public interface Actions {
        @Invoker("startAttack") boolean attack();
        @Invoker("startUseItem") void use();
        @Invoker("pickBlockOrEntity") void pick();
        @Invoker("continueAttack") void hold(boolean value);
    }
}
