// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Avoid a reverse lookup when the resolved address originated as a numeric literal. */
@Mixin(targets="net.minecraft.client.multiplayer.resolver.ServerAddressResolver")
public interface FastIpPingMixin {
    @WrapOperation(method="lambda$static$0",at=@At(value="INVOKE",target="Ljava/net/InetAddress;getByName(Ljava/lang/String;)Ljava/net/InetAddress;"))
    private static InetAddress ccos$resolve(String host,Operation<InetAddress> original) throws UnknownHostException {
        if(ConfigManager.fastIpPingEnabled && com.customcosmetics.sync.ExperimentalAccess.allowed()){
            try {
                var literal=InetAddress.ofLiteral(host);
                // A scoped IPv6 address must keep its scope ID.
                if(literal instanceof java.net.Inet6Address ipv6)
                    return java.net.Inet6Address.getByAddress(host,ipv6.getAddress(),ipv6.getScopeId());
                return InetAddress.getByAddress(host,literal.getAddress());
            }catch(IllegalArgumentException notLiteral){/* Let vanilla resolve domain names and validate invalid input. */}
        }
        return original.call(host);
    }
}
