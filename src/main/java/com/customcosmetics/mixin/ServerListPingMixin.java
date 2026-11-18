// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.network.ServerListDns;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.client.multiplayer.resolver.ServerRedirectHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import java.util.Optional;

public final class ServerListPingMixin {
    @Mixin(ServerStatusPinger.class)
    public static class Pinger {
        @WrapOperation(method = "pingServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/resolver/ServerNameResolver;resolveAddress(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;)Ljava/util/Optional;"))
        private Optional<ResolvedServerAddress> csb$resolveForPing(ServerNameResolver resolver, ServerAddress address,
                Operation<Optional<ResolvedServerAddress>> original) {
            if (!ConfigManager.fastIpPingEnabled) return original.call(resolver, address);
            return ScopedValue.where(ServerListDns.PING_LOOKUP, true).call(() -> original.call(resolver, address));
        }
    }

    @Mixin(ServerRedirectHandler.class)
    public interface Redirect {
        @WrapOperation(method = "lambda$createDnsSrvRedirectHandler$0", at = @At(value = "INVOKE",
            target = "Ljavax/naming/directory/DirContext;getAttributes(Ljava/lang/String;[Ljava/lang/String;)Ljavax/naming/directory/Attributes;"))
        private static Attributes csb$querySrv(DirContext context, String query, String[] types,
                Operation<Attributes> original, @Local(argsOnly = true) ServerAddress address) throws NamingException {
            if (!ServerListDns.PING_LOOKUP.isBound()) return original.call(context, query, types);
            return ServerListDns.querySrv(context, address.getHost(), query, types);
        }
    }
}
