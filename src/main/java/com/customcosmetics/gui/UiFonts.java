// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.compatibility.UiFontAccess;
import com.customcosmetics.config.ConfigManager;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Uses Minecraft's FreeType provider and glyph atlas for mod text only. */
public final class UiFonts {
    private static final Identifier FILE = Identifier.fromNamespaceAndPath("customcosmetics", "local.ttf");
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private static final FontDescription.Resource LOCAL = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath("customcosmetics", "local"));
    private static long request;
    private static Font base, view;
    private static Loaded loaded;

    private UiFonts() {}

    public static void init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> clear());
    }

    public static void restore(Minecraft client) {
        if (ConfigManager.uiFontPath.isBlank()) return;
        try {
            load(client, Path.of(ConfigManager.uiFontPath), ConfigManager.uiFontSize,
                ConfigManager.uiFontSpacing, ignored -> {});
        } catch (RuntimeException invalidPath) {
            CustomCosmeticsClient.LOGGER.debug("Saved interface font path is invalid", invalidPath);
        }
    }

    public static Font get(Font original) {
        if (loaded == null || !com.customcosmetics.sync.ExperimentalAccess.allowed()) return original;
        if (base != original || view == null) {
            base = original;
            var vanilla = ((UiFontAccess) (Object) original).customcosmetics$provider();

            view = new Font(new Font.Provider() {
                @Override public GlyphSource glyphs(FontDescription description) {
                    var active = loaded;
                    if (active != null && com.customcosmetics.sync.ExperimentalAccess.allowed()
                            && (description.equals(FontDescription.DEFAULT) || description.equals(LOCAL))) {
                        return active.source(vanilla.glyphs(FontDescription.DEFAULT));
                    }
                    return vanilla.glyphs(description);
                }
                @Override public EffectGlyph effect() { return vanilla.effect(); }
            });
        }
        return view;
    }

    public static void clear() {
        request++;
        swap(null);
    }

    private static void swap(Loaded next) {
        var old = loaded;
        loaded = next;
        base = view = null;
        if (old != null) old.close();
    }

    public static void load(Minecraft client, Path path, float size, float spacing, Consumer<Boolean> done) {
        if (!com.customcosmetics.sync.ExperimentalAccess.allowed()) { done.accept(false); return; }
        long id = ++request;
        CompletableFuture.supplyAsync(() -> read(path, size)).thenAccept(provider -> client.execute(() -> {
            if (request != id || !com.customcosmetics.sync.ExperimentalAccess.allowed()) {
                if (provider != null) provider.close();
                return;
            }
            if (provider == null) { done.accept(false); return; }
            var set = new FontSet(new GlyphStitcher(client.getTextureManager(),
                Identifier.fromNamespaceAndPath("customcosmetics", "local_font/" + id)));
            try {
                set.reload(List.of(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS)), Set.of());
            } catch (RuntimeException failure) {
                set.close();
                provider.close();
                CustomCosmeticsClient.LOGGER.warn("Could not prepare the selected interface font", failure);
                done.accept(false);
                return;
            }
            swap(new Loaded(provider, set, spacing));
            done.accept(true);
        }));
    }

    private static GlyphProvider read(Path path, float size) {
        try {
            if (!isFont(path) || !Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) return null;
            var definition = new TrueTypeGlyphProviderDefinition(FILE, size, 2,
                TrueTypeGlyphProviderDefinition.Shift.NONE, "");
            return definition.unpack().left().orElseThrow().load(new FontFile(path));
        } catch (IOException | RuntimeException | LinkageError error) {
            CustomCosmeticsClient.LOGGER.debug("Could not load the selected interface font", error);
            return null;
        }
    }

    private static boolean isFont(Path path) {
        if (path.getFileName() == null) return false;
        var name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc");
    }

    public static List<Path> systemFonts() {
        var roots = new ArrayList<Path>();
        var home = Path.of(System.getProperty("user.home"));
        String windows = System.getenv("WINDIR"), local = System.getenv("LOCALAPPDATA");
        if (windows != null) roots.add(Path.of(windows, "Fonts"));
        if (local != null) roots.add(Path.of(local, "Microsoft", "Windows", "Fonts"));
        roots.addAll(List.of(home.resolve(".local/share/fonts"), home.resolve(".fonts"),
            Path.of("/usr/share/fonts"), Path.of("/usr/local/share/fonts"), home.resolve("Library/Fonts"),
            Path.of("/Library/Fonts"), Path.of("/System/Library/Fonts")));
        var fonts = new TreeSet<Path>(Comparator.comparing((Path p) -> p.getFileName().toString(),
            String.CASE_INSENSITIVE_ORDER).thenComparing(Path::toString));
        for (var dir : roots) {
            if (!Files.isDirectory(dir)) continue;
            try (var paths = Files.walk(dir, 6)) {
                paths.filter(UiFonts::isFont).filter(Files::isRegularFile).limit(3000).forEach(fonts::add);
            } catch (IOException | java.io.UncheckedIOException | SecurityException ignored) {
                // One unreadable system directory should not hide fonts from other directories.
            }
        }
        return List.copyOf(fonts);
    }

    public static void drawTitle(GuiGraphicsExtractor g, String text, int x, int y, int color, int maxWidth) {
        g.pose().pushMatrix();
        g.pose().translate(x, y - 2);
        g.pose().scale(1.4f, 1.4f);
        PinkUi.text(g, get(Minecraft.getInstance().font), text, 0, 0, color, (int) (maxWidth / 1.4f));
        g.pose().popMatrix();
    }

    private static final class Loaded implements AutoCloseable {
        private final GlyphProvider provider;
        private final FontSet set;
        private final float spacing;
        private GlyphSource cachedFallback, cachedSource;

        private Loaded(GlyphProvider provider, FontSet set, float spacing) {
            this.provider = provider;
            this.set = set;
            this.spacing = spacing;
        }

        GlyphSource source(GlyphSource fallback) {
            if (cachedSource != null && cachedFallback == fallback) return cachedSource;
            cachedFallback = fallback;
            var local = set.source(false);
            var supported = provider.getSupportedGlyphs();
            var cache = new Int2ObjectOpenHashMap<BakedGlyph>();
            return cachedSource = new GlyphSource() {
                @Override public BakedGlyph getGlyph(int codepoint) {
                    return cache.computeIfAbsent(codepoint, (int cp) -> {
                        var glyph = supported.contains(cp) ? local.getGlyph(cp) : fallback.getGlyph(cp);
                        return spacing == 0 ? glyph : new SpacedGlyph(glyph, spacing);
                    });
                }
                @Override public BakedGlyph getRandomGlyph(RandomSource random, int codepoint) {
                    return fallback.getRandomGlyph(random, codepoint);
                }
            };
        }
        @Override public void close() { set.close(); provider.close(); }
    }

    private record SpacedGlyph(BakedGlyph base, float spacing) implements BakedGlyph, GlyphInfo {
        @Override public GlyphInfo info() { return this; }
        @Override public float getAdvance() { return Math.max(0, base.info().getAdvance() + spacing); }
        @Override public float getBoldOffset() { return base.info().getBoldOffset(); }
        @Override public float getShadowOffset() { return base.info().getShadowOffset(); }
        @Override public net.minecraft.client.gui.font.TextRenderable.Styled createGlyph(
            float x, float y, int color, int shadow, net.minecraft.network.chat.Style style, float bold, float italic) {
            return base.createGlyph(x, y, color, shadow, style, bold, italic);
        }
    }

    private record FontFile(Path path) implements ResourceManager {
        @Override public Optional<Resource> getResource(Identifier id) {
            return id.equals(FILE.withPrefix("font/"))
                ? Optional.of(new Resource(null, () -> Files.newInputStream(path))) : Optional.empty();
        }
        @Override public Set<String> getNamespaces() { return Set.of(FILE.getNamespace()); }
        @Override public List<Resource> getResourceStack(Identifier id) { return getResource(id).stream().toList(); }
        @Override public Map<Identifier, Resource> listResources(String path, Predicate<Identifier> filter) { return Map.of(); }
        @Override public Map<Identifier, List<Resource>> listResourceStacks(String path, Predicate<Identifier> filter) { return Map.of(); }
        @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
    }
}
