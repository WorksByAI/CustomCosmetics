// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.window;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.CosmeticsConfigDir;
import com.customcosmetics.features.cape.CapeImages;
import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.Platform;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class WindowAppearance {
    public static final int MAX_TITLE_LENGTH = 120;
    private static final boolean SDL = FabricLoader.getInstance().getModContainer("minecraft")
        .map(mod -> mod.getMetadata().getVersion().getFriendlyString().startsWith("26.3")).orElse(false);
    private static final List<Integer> ICON_SIZES = List.of(16, 20, 24, 28, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 256);

    private record Icons(List<IoSupplier<InputStream>> standard, IoSupplier<InputStream> mac) {}

    private static Icons icons;
    private static boolean checkedSavedIcon;
    private static boolean busy;

    private WindowAppearance() {}

    public static String normalizeTitle(String value) {
        if (value == null || value.isEmpty()) return "";
        var clean = new StringBuilder();
        value.codePoints().filter(point -> !Character.isISOControl(point)).limit(MAX_TITLE_LENGTH)
            .forEach(clean::appendCodePoint);
        return clean.toString().strip();
    }

    public static boolean usesSdl() { return SDL; }
    public static boolean busy() { return busy; }

    public static boolean iconSupported() {
        if (SDL) return true;
        int platform = GLFW.glfwGetPlatform();
        return platform == GLFW.GLFW_PLATFORM_WIN32 || platform == GLFW.GLFW_PLATFORM_X11
            || platform == GLFW.GLFW_PLATFORM_COCOA;
    }

    public static List<IoSupplier<InputStream>> standardIcons() {
        Icons current = loadSavedIcon();
        return current == null ? null : current.standard();
    }

    public static List<IoSupplier<InputStream>> preferTaskbarIcon(Window window, List<IoSupplier<InputStream>> standard) {
        if (!SDL || Platform.get() != Platform.WINDOWS || icons == null || standard != icons.standard()) return standard;
        float scale = 1;
        try {
            scale = (float) Class.forName("org.lwjgl.sdl.SDLVideo")
                .getMethod("SDL_GetWindowDisplayScale", long.class).invoke(null, window.handle());
        } catch (ReflectiveOperationException | LinkageError failure) {
            CustomCosmeticsClient.LOGGER.debug("Could not read window display scale for the custom icon", failure);
        }
        // SDL Win32 uses the primary surface for both native icons. Supply the large icon at the window's display scale.
        var ordered = new ArrayList<>(standard);
        ordered.addFirst(ordered.remove(ICON_SIZES.indexOf(taskbarIconSize(scale))));
        return ordered;
    }

    static int taskbarIconSize(float scale) {
        int desired = Float.isFinite(scale) ? Math.max(32, Math.round(32 * scale)) : 32;
        for (int size : ICON_SIZES) if (size >= desired) return size;
        return ICON_SIZES.getLast();
    }

    public static IoSupplier<InputStream> macIcon() {
        Icons current = loadSavedIcon();
        return current == null ? null : current.mac();
    }

    public static boolean hasCustomIcon() {
        return loadSavedIcon() != null;
    }

    public static void start(Minecraft client) {
        if (!ConfigManager.windowTitle.isEmpty()) client.updateTitle();
    }

    private static Icons loadSavedIcon() {
        if (checkedSavedIcon) return icons;
        checkedSavedIcon = true;
        // Supply the saved image to vanilla's initial icon assignment.
        Path path = iconPath();
        if (Files.isRegularFile(path)) {
            try {
                icons = makeIcons(CapeImages.read(path));
            } catch (IOException | RuntimeException failure) {
                CustomCosmeticsClient.LOGGER.warn("Could not load the saved window icon", failure);
            }
        }
        return icons;
    }

    public static void importIcon(Minecraft client, Path selected, Consumer<Boolean> done) {
        updateIcon(client, () -> {
            var image = CapeImages.read(selected);
            if (Math.max(image.getWidth(), image.getHeight()) > ICON_SIZES.getLast()) {
                image = square(image, ICON_SIZES.getLast());
            }
            Icons prepared = makeIcons(image);
            saveIcon(CapeImages.png(image));
            return prepared;
        }, done);
    }

    public static void resetIcon(Minecraft client, Consumer<Boolean> done) {
        updateIcon(client, () -> {
            Files.deleteIfExists(iconPath());
            return null;
        }, done);
    }

    private static void updateIcon(Minecraft client, Callable<Icons> load, Consumer<Boolean> done) {
        if (busy) return;
        busy = true;
        Util.ioPool().execute(() -> {
            try {
                Icons prepared = load.call();
                client.execute(() -> {
                    busy = false;
                    icons = prepared;
                    done.accept(refreshIcon(client));
                });
            } catch (Exception failure) {
                CustomCosmeticsClient.LOGGER.warn("Could not read or save the local window icon", failure);
                client.execute(() -> {
                    busy = false;
                    done.accept(false);
                });
            }
        });
    }

    private static boolean refreshIcon(Minecraft client) {
        var window = client.getWindow();
        var resources = client.getVanillaPackResources();
        var fallback = SharedConstants.getCurrentVersion().stable() ? IconSet.RELEASE : IconSet.SNAPSHOT;
        try {
            if (SDL) {
                // 26.3 exposes the full pack through a separate view and uses its metadata interface here.
                var pack = resources.getClass().getMethod("fullResources").invoke(resources);
                var packType = Class.forName("net.minecraft.server.packs.PackMetadataResources");
                Window.class.getMethod("setIcon", packType, IconSet.class).invoke(window, pack, fallback);
            } else {
                window.setIcon(resources, fallback);
            }
            return true;
        } catch (IOException | ReflectiveOperationException | LinkageError failure) {
            CustomCosmeticsClient.LOGGER.warn("Could not apply the local window icon", failure);
            return false;
        }
    }

    private static Icons makeIcons(BufferedImage image) throws IOException {
        var standard = new ArrayList<IoSupplier<InputStream>>(ICON_SIZES.size());
        byte[] large = null;
        for (int size : ICON_SIZES) {
            byte[] png = CapeImages.png(square(image, size));
            standard.add(() -> new ByteArrayInputStream(png));
            large = png;
        }
        byte[] icns = macContainer(large);
        return new Icons(List.copyOf(standard), () -> new ByteArrayInputStream(icns));
    }

    private static BufferedImage square(BufferedImage source, int size) {
        int sourceSize = Math.max(source.getWidth(), source.getHeight());
        double scale = size / (double) sourceSize;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        var canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, sourceSize <= 32
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, (size - width) / 2, (size - height) / 2, width, height, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private static byte[] macContainer(byte[] png) throws IOException {
        var out = new ByteArrayOutputStream(png.length + 16);
        try (var data = new DataOutputStream(out)) {
            data.writeBytes("icns");
            data.writeInt(png.length + 16);
            data.writeBytes("ic08");
            data.writeInt(png.length + 8);
            data.write(png);
        }
        return out.toByteArray();
    }

    private static Path iconPath() {
        return CosmeticsConfigDir.getCosmeticsConfigDir().resolve("window-icon.png");
    }

    private static void saveIcon(byte[] png) throws IOException {
        var path = iconPath();
        var temporary = Files.createTempFile(path.getParent(), "window-icon-", ".png");
        try {
            Files.write(temporary, png);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException fallback) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
