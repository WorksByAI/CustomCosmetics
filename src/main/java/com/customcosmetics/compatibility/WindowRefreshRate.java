// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.mojang.blaze3d.platform.Window;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Cached access across the GLFW/SDL transition; invoked only when the monitor is sampled. */
public final class WindowRefreshRate {
    private static final MethodHandle READER = reader(Window.class);
    private WindowRefreshRate() {}

    public static int get(Window window) { return read(READER, window); }

    static MethodHandle reader(Class<?> windowType) {
        try {
            var lookup = MethodHandles.lookup();
            MethodHandle result;
            try {
                result = lookup.unreflect(windowType.getMethod("getRefreshRate"));
            } catch (NoSuchMethodException modern) {
                var mode = windowType.getMethod("getActiveVideoMode");
                var rate = mode.getReturnType().getMethod("getRefreshRate");
                result = MethodHandles.filterReturnValue(lookup.unreflect(mode), lookup.unreflect(rate));
            }
            // Both the legacy int and the modern fractional float widen without boxing.
            return result.asType(MethodType.methodType(double.class, Object.class));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    static int read(MethodHandle reader, Object window) {
        try {
            double rate = (double) reader.invokeExact(window);
            return Double.isFinite(rate) && rate > 0 ? (int)Math.clamp(Math.round(rate), 30, 1000) : 60;
        } catch (Throwable unavailable) {
            // A monitor/video mode may temporarily disappear while changing displays.
            return 60;
        }
    }
}
