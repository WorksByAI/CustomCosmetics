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
    public static final int MAX_SAMPLING = 6;
    private static final FontDescription.Resource LOCAL = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath("customcosmetics", "local"));
    private static final FontDescription.Resource UNIFORM = new FontDescription.Resource(
        Identifier.withDefaultNamespace("uniform"));
    public record Style(float size, float spacing, int oversample, float baseline, float shadow) {}
    private record Prepared(GlyphProvider provider, GlyphProvider inlineBold,
                            LocalFontFaces.Family family, boolean bold) implements AutoCloseable {
        @Override public void close() {
            try {
                provider.close();
            } finally {
                if (inlineBold != null) inlineBold.close();
            }
        }
    }
    private static long request;
    private static Font base, view;
    private static Loaded loaded;

    private UiFonts() {}

    public static boolean boldAvailable() { return loaded != null && loaded.family.canSwitch(); }
    public static boolean boldActive() { return loaded != null && loaded.bold; }

    public static long localFaceIndex(ResourceManager resources, long defaultIndex) {
        return resources instanceof FontFile file ? file.index() : defaultIndex;
    }

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
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(UiFonts::restore);
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
        if (loaded == null) return original;
        if (ConfigManager.useFontInGame) return original;
        if (base != original || view == null) {
            base = original;
            var vanilla = ((UiFontAccess) (Object) original).customcosmetics$provider();

            view = new Font(new Font.Provider() {
                @Override public GlyphSource glyphs(FontDescription description) {
                    var active = loaded;
                    if (active != null
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
        if (active == null || !ConfigManager.useFontInGame) return vanilla;
        if (!description.equals(FontDescription.DEFAULT) && !description.equals(UNIFORM)) return vanilla;
        return active.source(vanilla);
    }

    public static GlyphSource forTextStyle(GlyphSource source, net.minecraft.network.chat.Style style) {
        return source instanceof Loaded.LocalSource local && style.isBold() ? local.boldSource : source;
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
        load(client, path, style, ConfigManager.uiFontBold, done);
    }

    public static void load(Minecraft client, Path path, Style style, Boolean preferBold, Consumer<Boolean> done) {
        var normalized = path.toAbsolutePath().normalize();
        var known = loaded != null && loaded.family.path().equals(normalized) ? loaded.family : null;
        long id = ++request;
        CompletableFuture.supplyAsync(() -> read(normalized, known, style, preferBold)).thenAccept(prepared -> client.execute(() -> {
            if (request != id) {
                if (prepared != null) prepared.close();
                return;
            }
            if (prepared == null) { done.accept(false); return; }
            var atlas = new GlyphStitcher(client.getTextureManager(),
                Identifier.fromNamespaceAndPath("customcosmetics", ATLAS_PATH + id));
            swap(new Loaded(prepared, atlas, style));
            done.accept(true);
        }));
    }

    private static Prepared read(Path path, LocalFontFaces.Family known, Style style, Boolean preferBold) {
        try {
            var family = known == null ? LocalFontFaces.resolve(path) : known;
            if (family == null) return null;
            var face = family.choose(preferBold);
            if (!LocalFontFaces.readable(face.path())) return null;
            var definition = new TrueTypeGlyphProviderDefinition(FILE, style.size(), style.oversample(),
                new TrueTypeGlyphProviderDefinition.Shift(0, style.baseline()), "");
            var provider = definition.unpack().left().orElseThrow().load(new FontFile(face.path(), face.index()));
            var inlineBold = face.bold() ? null : loadInlineBold(definition, family.bold());
            return new Prepared(provider, inlineBold, family, face.bold());
        } catch (IOException | RuntimeException | LinkageError error) {
            CustomCosmeticsClient.LOGGER.debug("Could not load the selected interface font", error);
            return null;
        }
    }

    private static GlyphProvider loadInlineBold(TrueTypeGlyphProviderDefinition definition, LocalFontFaces.Face face) {
        if (face == null) return null;
        try {
            if (!LocalFontFaces.readable(face.path())) return null;
            return definition.unpack().left().orElseThrow().load(new FontFile(face.path(), face.index()));
        } catch (IOException | RuntimeException | LinkageError error) {
            CustomCosmeticsClient.LOGGER.debug("Could not load the font's inline bold face", error);
            return null;
        }
    }

    static boolean isFont(Path path) {
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
        private final Prepared fonts;
        private final GlyphStitcher atlas;
        private final Style style;
        private final LocalFontFaces.Family family;
        private final boolean bold;
        private final Int2ObjectOpenHashMap<BakedGlyph> glyphs = new Int2ObjectOpenHashMap<>();
        private final Int2ObjectOpenHashMap<BakedGlyph> boldGlyphs = new Int2ObjectOpenHashMap<>();
        private final IntFunction<BakedGlyph> bake = codepoint -> bake(codepoint, false);
        private final IntFunction<BakedGlyph> bakeBold = codepoint -> bake(codepoint, true);
        private final IdentityHashMap<net.minecraft.network.chat.Style, net.minecraft.network.chat.Style> drawStyles = new IdentityHashMap<>();
        private final IdentityHashMap<GlyphSource, GlyphSource> sources = new IdentityHashMap<>();
        private final IdentityHashMap<Object, AbstractTexture> atlasViews = new IdentityHashMap<>();
        private boolean reportedBadGlyph;

        private Loaded(Prepared prepared, GlyphStitcher atlas, Style style) {
            this.fonts = prepared;
            this.family = prepared.family();
            this.bold = prepared.bold();
            this.atlas = atlas;
            this.style = style;
        }

        private BakedGlyph bake(int codepoint, boolean formattedBold) {
            // SkyBlock icons and Devonian's symbol replacements use private-use glyphs.
            if (Character.getType(codepoint) == Character.PRIVATE_USE) return MISSING;
            try {
                var provider = formattedBold ? fonts.inlineBold() : fonts.provider();
                var unbaked = provider == null ? null : provider.getGlyph(codepoint);
                BakedGlyph glyph = unbaked == null ? null : unbaked.bake(this);
                if (formattedBold && (glyph == null || glyph == MISSING)) {
                    glyph = LocalBoldGlyphs.bake(fonts.provider(), codepoint, this);
                }
                if (glyph == null || glyph == MISSING) return MISSING;
                boolean bakedBold = bold || formattedBold;
                return !bakedBold && style.spacing() == 0 && style.shadow() == 1 ? glyph
                    : new StyledGlyph(glyph, style.spacing(), style.shadow(), bakedBold ? drawStyles : null);
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
            GlyphSource source = new LocalSource(fallback, false);
            sources.put(fallback, source);
            return source;
        }

        private final class LocalSource implements GlyphSource {
            private final GlyphSource fallback;
            private final boolean formattedBold;
            private final GlyphSource boldSource;

            private LocalSource(GlyphSource fallback, boolean formattedBold) {
                this.fallback = fallback;
                this.formattedBold = formattedBold;
                boldSource = bold || formattedBold ? this : new LocalSource(fallback, true);
            }

            @Override public BakedGlyph getGlyph(int codepoint) {
                if (formattedBold) {
                    var thick = boldGlyphs.computeIfAbsent(codepoint, bakeBold);
                    if (thick != MISSING) return thick;
                }
                var glyph = glyphs.computeIfAbsent(codepoint, bake);
                return glyph == MISSING ? fallback.getGlyph(codepoint) : glyph;
            }

            @Override public BakedGlyph getRandomGlyph(RandomSource random, int codepoint) {
                return fallback.getRandomGlyph(random, codepoint);
            }
        }

        @Override public void close() {
            try {
                atlas.close();
            } finally {
                fonts.close();
            }
        }
    }

    private record StyledGlyph(BakedGlyph base, float spacing, float shadow,
        IdentityHashMap<net.minecraft.network.chat.Style, net.minecraft.network.chat.Style> drawStyles) implements BakedGlyph, GlyphInfo {
        @Override public GlyphInfo info() { return this; }
        @Override public float getAdvance() { return Math.max(0, base.info().getAdvance() + spacing); }
        @Override public float getBoldOffset() { return drawStyles != null ? 0 : base.info().getBoldOffset(); }
        @Override public float getShadowOffset() { return base.info().getShadowOffset() * shadow; }
        @Override public net.minecraft.client.gui.font.TextRenderable.Styled createGlyph(
            float x, float y, int color, int shadow, net.minecraft.network.chat.Style style, float bold, float italic) {
            if (drawStyles != null && style.isBold()) {
                var normal = drawStyles.get(style);
                if (normal == null) {
                    // Only the draw instance drops Minecraft's duplicate pass. Layout keeps the text's actual style.
                    if (drawStyles.size() >= 128) drawStyles.clear();
                    normal = style.withBold(false);
                    drawStyles.put(style, normal);
                }
                style = normal;
            }
            return base.createGlyph(x, y, color, shadow, style, bold, italic);
        }
    }

    private record FontFile(Path path, int index) implements ResourceManager {
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
