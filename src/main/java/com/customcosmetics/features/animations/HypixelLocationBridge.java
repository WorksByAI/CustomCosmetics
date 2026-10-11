// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.animations;

import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Optional official API integration. Registers only Hello/Location handlers and a Location subscription. */
public final class HypixelLocationBridge {
    private HypixelLocationBridge() {}
    public static boolean register(ClassLoader loader, IntConsumer environment, Consumer<String> instance) {
        try {
            var apiClass = Class.forName("net.hypixel.modapi.HypixelModAPI", false, loader);
            var handler = Class.forName("net.hypixel.modapi.handler.ClientboundPacketHandler", false, loader);
            var api = apiClass.getMethod("getInstance").invoke(null);
            var create = apiClass.getMethod("createHandler", Class.class, handler);
            for (String name : new String[] {"impl.clientbound.ClientboundHelloPacket", "impl.clientbound.event.ClientboundLocationPacket"}) {
                var type = Class.forName("net.hypixel.modapi.packet." + name, false, loader);
                Object callback = Proxy.newProxyInstance(loader, new Class<?>[]{handler}, (proxy, method, args) -> {
                    if (!method.getName().equals("handle")) return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy); case "equals" -> proxy == args[0];
                        case "toString" -> "CustomCosmetics world listener"; default -> null;
                    };
                    if (name.endsWith("HelloPacket")) {
                        Object env = type.getMethod("getEnvironment").invoke(args[0]);
                        environment.accept(((Number) env.getClass().getMethod("getId").invoke(env)).intValue());
                    } else instance.accept((String) type.getMethod("getServerName").invoke(args[0]));
                    return null;
                });
                create.invoke(api, type, callback);
            }
            var location = Class.forName("net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket", false, loader);
            apiClass.getMethod("subscribeToEventPacket", Class.class).invoke(api, location);
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) { return false; }
    }
}
