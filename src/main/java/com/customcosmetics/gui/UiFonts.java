// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.compatibility.UiFontAccess;
import com.customcosmetics.config.ConfigManager;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.client.renderer.texture.AbstractTexture;
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
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Uses Minecraft's FreeType provider and glyph atlas for local fonts. */
public final class UiFonts {
    private static final String ATLAS_PATH = "local_font/";
    private static final Identifier FILE = Identifier.fromNamespaceAndPath("customcosmetics", "local.ttf");
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private static final FontDescription.Resource LOCAL = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath("customcosmetics", "local"));
    private static final FontDescription.Resource UNIFORM = new FontDescription.Resource(
        Identifier.withDefaultNamespace("uniform"));
    public record Style(float size, float spacing, int oversample, float baseline, float shadow) {}
    private static long request;
    private static Font base, view;
    private static Loaded loaded;

    private UiFonts() {}

    // The GPU may discard texture labels. Use the actual view to identify local text.
    public static boolean registerLocalAtlas(Supplier<String> name, AbstractTexture texture, Object view) {
        var active = loaded;
        if (active == null || name == null) return false;
        String label = name.get();
        if (label == null || !label.startsWith("customcosmetics:" + ATLAS_PATH)) return false;
        active.atlasViews.put(view, texture);
        return true;
    }

    public static AbstractTexture localAtlas(Object view) {
        var active = loaded;
        return active == null ? null : active.atlasViews.get(view);
    }

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
        if (ConfigManager.useFontInGame) return original;
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

    public static GlyphSource gameSource(FontDescription description, GlyphSource vanilla) {
        var active = loaded;
        if (active == null || !ConfigManager.useFontInGame || !com.customcosmetics.sync.ExperimentalAccess.allowed()) return vanilla;
        if (!description.equals(FontDescription.DEFAULT) && !description.equals(UNIFORM)) return vanilla;
        return active.source(vanilla);
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
        load(client, path, new Style(size, spacing, ConfigManager.uiFontOversample, ConfigManager.uiFontBaseline,
            ConfigManager.uiFontShadow), done);
    }

    public static void load(Minecraft client, Path path, Style style, Consumer<Boolean> done) {
        if (!com.customcosmetics.sync.ExperimentalAccess.allowed()) { done.accept(false); return; }
        long id = ++request;
        CompletableFuture.supplyAsync(() -> read(path, style)).thenAccept(provider -> client.execute(() -> {
            if (request != id || !com.customcosmetics.sync.ExperimentalAccess.allowed()) {
                if (provider != null) provider.close();
                return;
            }
            if (provider == null) { done.accept(false); return; }
            var atlas = new GlyphStitcher(client.getTextureManager(),
                Identifier.fromNamespaceAndPath("customcosmetics", ATLAS_PATH + id));
            swap(new Loaded(provider, atlas, style));
            done.accept(true);
        }));
    }

    private static GlyphProvider read(Path path, Style style) {
        try {
            if (!isFont(path) || !Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) return null;
            var definition = new TrueTypeGlyphProviderDefinition(FILE, style.size(), style.oversample(),
                new TrueTypeGlyphProviderDefinition.Shift(0, style.baseline()), "");
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

    private static final class Loaded implements AutoCloseable, UnbakedGlyph.Stitcher {
        private static final BakedGlyph MISSING = new EmptyGlyph(0).bake(null);
        private final GlyphProvider provider;
        private final GlyphStitcher atlas;
        private final Style style;
        private final Int2ObjectOpenHashMap<BakedGlyph> glyphs = new Int2ObjectOpenHashMap<>();
        private final IntFunction<BakedGlyph> bake = this::bake;
        private final IdentityHashMap<GlyphSource, GlyphSource> sources = new IdentityHashMap<>();
        private final IdentityHashMap<Object, AbstractTexture> atlasViews = new IdentityHashMap<>();
        private boolean reportedBadGlyph;

        private Loaded(GlyphProvider provider, GlyphStitcher atlas, Style style) {
            this.provider = provider;
            this.atlas = atlas;
            this.style = style;
        }

        private BakedGlyph bake(int codepoint) {
            // SkyBlock icons and Devonian's symbol replacements use private-use glyphs.
            if (Character.getType(codepoint) == Character.PRIVATE_USE) return MISSING;
            try {
                var unbaked = provider.getGlyph(codepoint);
                if (unbaked == null) return MISSING;
                var glyph = unbaked.bake(this);
                if (glyph == null || glyph == MISSING) return MISSING;
                return style.spacing() == 0 && style.shadow() == 1 ? glyph
                    : new StyledGlyph(glyph, style.spacing(), style.shadow());
            } catch (RuntimeException error) {
                if (!reportedBadGlyph) {
                    reportedBadGlyph = true;
                    CustomCosmeticsClient.LOGGER.warn("Could not bake a local font glyph", error);
                }
                return MISSING;
            }
        }

        @Override public BakedGlyph stitch(GlyphInfo info, GlyphBitmap bitmap) { return atlas.stitch(info, bitmap); }
        @Override public BakedGlyph getMissing() { return MISSING; }

        GlyphSource source(GlyphSource fallback) {
            var cached = sources.get(fallback);
            if (cached != null) return cached;
            if (sources.size() >= 4) sources.clear();
            GlyphSource source = new GlyphSource() {
                @Override public BakedGlyph getGlyph(int codepoint) {
                    var glyph = glyphs.computeIfAbsent(codepoint, bake);
                    return glyph == MISSING ? fallback.getGlyph(codepoint) : glyph;
                }
                @Override public BakedGlyph getRandomGlyph(RandomSource random, int codepoint) {
                    return fallback.getRandomGlyph(random, codepoint);
                }
            };
            sources.put(fallback, source);
            return source;
        }
        @Override public void close() { atlas.close(); provider.close(); }
    }

    private record StyledGlyph(BakedGlyph base, float spacing, float shadow) implements BakedGlyph, GlyphInfo {
        @Override public GlyphInfo info() { return this; }
        @Override public float getAdvance() { return Math.max(0, base.info().getAdvance() + spacing); }
        @Override public float getBoldOffset() { return base.info().getBoldOffset(); }
        @Override public float getShadowOffset() { return base.info().getShadowOffset() * shadow; }
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
