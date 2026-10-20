// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Registers official Hello/Location handlers without bundling a second Mod API implementation. */
final class HypixelWaypointBridge {
    private HypixelWaypointBridge() {}

    static boolean register(ClassLoader loader, Runnable hello, BiConsumer<String, String> location) {
        try {
            Class<?> apiType = Class.forName("net.hypixel.modapi.HypixelModAPI", false, loader);
            Class<?> handler = Class.forName("net.hypixel.modapi.handler.ClientboundPacketHandler", false, loader);
            Class<?> helloType = Class.forName("net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket", false, loader);
            Class<?> locationType = Class.forName("net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket", false, loader);
            Object api = apiType.getMethod("getInstance").invoke(null);
            var create = apiType.getMethod("createHandler", Class.class, handler);
            create.invoke(api, helloType, callback(loader, handler, packet -> hello.run()));
            create.invoke(api, locationType, callback(loader, handler, packet -> {
                try {
                    Optional<?> serverType = (Optional<?>) locationType.getMethod("getServerType").invoke(packet);
                    String name = serverType.isEmpty() ? "" : (String) serverType.get().getClass().getMethod("name").invoke(serverType.get());
                    Optional<?> mode = (Optional<?>) locationType.getMethod("getMode").invoke(packet);
                    location.accept(name, mode.map(Object::toString).orElse(""));
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Invalid Hypixel location packet", failure); }
            }));
            apiType.getMethod("subscribeToEventPacket", Class.class).invoke(api, locationType);
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) { return false; }
    }

    private static Object callback(ClassLoader loader, Class<?> handler, Consumer<Object> packet) {
        return Proxy.newProxyInstance(loader, new Class<?>[]{handler}, (proxy, method, args) -> {
            if (method.getName().equals("handle")) { packet.accept(args[0]); return null; }
            return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "CustomCosmetics waypoint listener";
                default -> null;
            };
        });
    }
}
