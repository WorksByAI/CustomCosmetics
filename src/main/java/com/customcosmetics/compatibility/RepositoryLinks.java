// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import java.lang.reflect.Method;
import java.net.URI;
import net.minecraft.util.Util;
import com.mojang.blaze3d.Blaze3D;

/** Opening a user-clicked link moved from Util.OS to Blaze3D in 26.3. */
public final class RepositoryLinks {
    private static final Method OPEN;
    private static final Object PLATFORM;
    static {
        try {
            Method method; Object platform;
            try { method = Blaze3D.class.getMethod("openUri", URI.class); platform = null; }
            catch (NoSuchMethodException legacy) {
                platform = Util.getPlatform(); method = platform.getClass().getMethod("openUri", URI.class);
            }
            OPEN = method; PLATFORM = platform;
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private RepositoryLinks() {}
    public static void openUri(URI uri) {
        try { OPEN.invoke(PLATFORM, uri); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot open repository link", e); }
    }
}
