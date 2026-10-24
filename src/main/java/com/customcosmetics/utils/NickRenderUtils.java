/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.utils;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.gui.module.Module;
import com.customcosmetics.gui.module.ModuleManager;
import com.customcosmetics.sync.PresenceSync;
import com.customcosmetics.features.nickname.NicknameGradient;
import com.customcosmetics.features.nickname.NicknameGlyphEffect;
import net.minecraft.client.gui.Font;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Environment(EnvType.CLIENT)
public final class NickRenderUtils {
    private static final long TARGET_CACHE_MS = 1000L;
    private static volatile long targetsCacheAt = 0L;
    private static final AtomicLong TARGET_VERSION = new AtomicLong();
    private static volatile String cachePlayerName = "";
    private static volatile UUID cachePlayerUuid;
    private static volatile List<Target> cachedTargets = List.of();
    private static volatile boolean cachedSmooth;
    private static final ThreadLocal<Boolean> PREVIEW_OVERRIDE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> CLICK_GUI_RENDER_SCOPE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> MOD_MENU_TEXT_RENDER_SCOPE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Integer> GUI_TEXT_RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final int MATCH_CACHE_MAX = 2048;
    private static final Object MATCH_CACHE_LOCK = new Object();
    private static final LinkedHashMap<String, TargetMatch[]> MATCH_CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TargetMatch[]> eldest) {
            return size() > MATCH_CACHE_MAX;
        }
    };

    private static final int REPLACEMENT_CACHE_MAX = 512;
    private static final Object REPLACEMENT_CACHE_LOCK = new Object();
    private static final LinkedHashMap<String, List<ReplacementCodepoint>> REPLACEMENT_CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<ReplacementCodepoint>> eldest) {
            return size() > REPLACEMENT_CACHE_MAX;
        }
    };

    private static final int REPLACEMENT_AFFIX_CACHE_MAX = 512;
    private static final Object REPLACEMENT_AFFIX_CACHE_LOCK = new Object();
    private static final LinkedHashMap<String, List<ReplacementAffix>> REPLACEMENT_AFFIX_CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<ReplacementAffix>> eldest) {
            return size() > REPLACEMENT_AFFIX_CACHE_MAX;
        }
    };
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final TargetMatch[] NO_MATCH=new TargetMatch[0];
    private static volatile long renderFrame;
    private static final class TextCaches {
        final IdentityFrameCache<FormattedCharSequence,FormattedCharSequence> resolved=new IdentityFrameCache<>(4096),measured=new IdentityFrameCache<>(4096);
        void begin(){long version=TARGET_VERSION.get();resolved.begin(renderFrame,version);measured.begin(renderFrame,version);}
    }
    private static final class ScopedTextCaches {
        final TextCaches[] contexts=new TextCaches[com.customcosmetics.features.nickname.RankText.Context.values().length];
        TextCaches current(){int context=com.customcosmetics.features.nickname.RankDisplayContext.current().ordinal();var cache=contexts[context];if(cache==null)contexts[context]=cache=new TextCaches();return cache;}
        void clear(){for(var cache:contexts)if(cache!=null){cache.resolved.begin(Long.MIN_VALUE,Long.MIN_VALUE);cache.measured.begin(Long.MIN_VALUE,Long.MIN_VALUE);}}
    }
    private static final ThreadLocal<ScopedTextCaches> TEXT_CACHES=ThreadLocal.withInitial(ScopedTextCaches::new);
    public static void beginFrame(){renderFrame++;com.customcosmetics.features.nickname.RankAppearance.beginFrame();}

    private NickRenderUtils() {
    }

    public static void invalidateLocalTargetsCache() {
        com.customcosmetics.features.nickname.RankAppearance.invalidate();
        targetsCacheAt = 0L;
        cachePlayerName = "";
        cachePlayerUuid = null;
        cachedTargets = List.of();
        cachedSmooth=false;
        TARGET_VERSION.incrementAndGet();
        synchronized (MATCH_CACHE_LOCK) { MATCH_CACHE.clear(); }
        TEXT_CACHES.get().clear();
    }

    public static long getTargetsCacheAt() {
        return targetsCacheAt;
    }

    private static boolean shouldSkipNickTweakSubstitution() {
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.NICK))return true;
        if (Boolean.TRUE.equals(PREVIEW_OVERRIDE.get())) {
            return false;
        }
        if (Boolean.TRUE.equals(CLICK_GUI_RENDER_SCOPE.get())) {
            return true;
        }
        if (Boolean.TRUE.equals(MOD_MENU_TEXT_RENDER_SCOPE.get())) {
            return true;
        }
        recoverGuiTextRenderScopeIfStale();
        if (GUI_TEXT_RENDER_DEPTH.get() > 0) {
            return true;
        }
        return false;
    }

    private static void recoverGuiTextRenderScopeIfStale() {
        if (GUI_TEXT_RENDER_DEPTH.get() <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc == null ? null : com.customcosmetics.compatibility.ClientUiAccess.screen(mc);
        if (screen == null) {
            GUI_TEXT_RENDER_DEPTH.remove();
            return;
        }
        if (screen instanceof AbstractContainerScreen<?> || screen instanceof ChatScreen) {
            GUI_TEXT_RENDER_DEPTH.remove();
            return;
        }
        if (!shouldEnterGuiTextSkipScope(screen)) {
            GUI_TEXT_RENDER_DEPTH.remove();
        }
    }

    public static boolean shouldEnterGuiTextSkipScope(Screen screen) {
        if (screen == null) {
            return false;
        }
        if (screen instanceof AbstractContainerScreen<?> || screen instanceof ChatScreen) {
            return false;
        }
        if (isModMenuScreen(screen)) {
            return false;
        }
        if (screen.isPauseScreen()) {
            return true;
        }
        String className = screen.getClass().getName();
        return className.startsWith("me.shedaniel.clothconfig")
            || className.startsWith("me.shedaniel.autoconfig")
            || className.startsWith("dev.isxander.yacl")
            || className.startsWith("io.wispforest.owo.config");
    }

    private static boolean isModMenuScreen(Screen screen) {
        return screen.getClass().getName().startsWith("com.terraformersmc.modmenu");
    }

    public static void enterGuiTextRenderScope() {
        GUI_TEXT_RENDER_DEPTH.set(GUI_TEXT_RENDER_DEPTH.get() + 1);
    }

    public static void exitGuiTextRenderScope() {
        int depth = GUI_TEXT_RENDER_DEPTH.get() - 1;
        if (depth <= 0) {
            GUI_TEXT_RENDER_DEPTH.remove();
        } else {
            GUI_TEXT_RENDER_DEPTH.set(depth);
        }
    }

    public static void beginModMenuTextRenderScope() {
        MOD_MENU_TEXT_RENDER_SCOPE.set(Boolean.TRUE);
    }

    public static void endModMenuTextRenderScope() {
        MOD_MENU_TEXT_RENDER_SCOPE.set(Boolean.FALSE);
    }

    public static String handleString(String text) {
        if (text == null || text.isEmpty()) return text;
        if (shouldSkipNickTweakSubstitution()) return text;
        var targets=collectTargets();
        if(com.customcosmetics.features.nickname.RankAppearance.active()){
            var ranked=com.customcosmetics.features.nickname.RankAppearance.apply(text);
            if(targets.isEmpty())return ranked;
            FormattedCharSequence input=sink->net.minecraft.util.StringDecomposer.iterateFormatted(ranked,Style.EMPTY,sink);
            return com.customcosmetics.features.nickname.RankText.legacy(resolveCharSequence(input,targets));
        }
        return renderString(text, targets);
    }

    static String renderString(String text, List<Target> targets) {
        if (targets.isEmpty()) return text;
        long version = TARGET_VERSION.get();
        String cacheKey = version + "|" + text;
        TargetMatch[] matchByIndex;
        int[] codePoints = null;
        synchronized (MATCH_CACHE_LOCK) {
            matchByIndex = MATCH_CACHE.get(cacheKey);
        }

        if (matchByIndex == null) {
            String lower = text.toLowerCase(Locale.ROOT);
            List<Target> matchingTargets = null;
            for (Target target : targets) {
                if (!lower.contains(target.nameLower())) continue;
                if (matchingTargets == null) matchingTargets = new ArrayList<>();
                matchingTargets.add(target);
            }
            if (matchingTargets == null || matchingTargets.isEmpty()) {
                synchronized(MATCH_CACHE_LOCK){MATCH_CACHE.put(cacheKey,NO_MATCH);}return text;
            }

            codePoints = text.codePoints().toArray();
            matchByIndex = matchTargets(codePoints, matchingTargets);
            synchronized (MATCH_CACHE_LOCK) {
                MATCH_CACHE.put(cacheKey, matchByIndex);
            }
        }

        boolean matched = false;
        for (TargetMatch value : matchByIndex) {
            if (value != null) {
                matched = true;
                break;
            }
        }
        if (!matched) return text;

        if (codePoints == null) codePoints = text.codePoints().toArray();
        java.util.ArrayList<Glyph> glyphs = new java.util.ArrayList<>(codePoints.length);
        for (int cp : codePoints) glyphs.add(new Glyph(cp, Style.EMPTY));

        String out = applySectionColorToStringWithReplacement(text, glyphs, matchByIndex);
        return out;
    }

    public static void beginPreviewOverride() {
        PREVIEW_OVERRIDE.set(Boolean.TRUE);
    }
    public static void endPreviewOverride() {
        PREVIEW_OVERRIDE.set(Boolean.FALSE);
    }

    public static void beginClickGuiRenderScope() {
        CLICK_GUI_RENDER_SCOPE.set(Boolean.TRUE);
    }

    public static void endClickGuiRenderScope() {
        CLICK_GUI_RENDER_SCOPE.set(Boolean.FALSE);
    }

    public static FormattedText handleFormattedText(FormattedText text) {
        if (text == null) return null;
        if (shouldSkipNickTweakSubstitution()) return text;
        if(com.customcosmetics.features.nickname.RankAppearance.active()){
            var source=com.customcosmetics.features.nickname.RankText.sequence(text);
            var sequence=com.customcosmetics.features.nickname.RankAppearance.apply(source);
            if(sequence!=source)text=com.customcosmetics.features.nickname.RankText.component(sequence);
        }
        return renderFormattedText(text, collectTargets());
    }

    static FormattedText renderFormattedText(FormattedText text, List<Target> targets) {
        if (targets.isEmpty()) return text;
        StringBuilder sbPlain = new StringBuilder();
        List<Glyph> originalGlyphs = new ArrayList<>();
        try {
            text.visit((style, str) -> {
                if (str == null || str.isEmpty()) return java.util.Optional.empty();
                for (int i = 0; i < str.length();) {
                    int cp = str.codePointAt(i);
                    sbPlain.appendCodePoint(cp);
                    originalGlyphs.add(new Glyph(cp, style));
                    i += Character.charCount(cp);
                }
                return java.util.Optional.empty();
            }, Style.EMPTY);
        } catch (Throwable ignore) {
            String plainFallback = text.toString();
            for (int i = 0; i < plainFallback.length();) {
                int cp = plainFallback.codePointAt(i);
                sbPlain.appendCodePoint(cp);
                originalGlyphs.add(new Glyph(cp, Style.EMPTY));
                i += Character.charCount(cp);
            }
        }
        String plain = sbPlain.toString();
        if (plain.isEmpty()) return text;

        long version = TARGET_VERSION.get();
        String cacheKey = version + "|" + plain;
        TargetMatch[] matchByIndex;
        synchronized (MATCH_CACHE_LOCK) {
            matchByIndex = MATCH_CACHE.get(cacheKey);
        }
        if (matchByIndex == null) {
            int[] codePoints = plain.codePoints().toArray();
            if (codePoints.length == 0) return text;
            String lower = plain.toLowerCase(Locale.ROOT);
            List<Target> matchingTargets = null;
            for (Target target : targets) {
                if (!lower.contains(target.nameLower())) continue;
                if (matchingTargets == null) matchingTargets = new ArrayList<>();
                matchingTargets.add(target);
            }
            if (matchingTargets == null || matchingTargets.isEmpty()) return text;
            matchByIndex = matchTargets(codePoints, matchingTargets);
            synchronized (MATCH_CACHE_LOCK) {
                MATCH_CACHE.put(cacheKey, matchByIndex);
            }
        }
        boolean matched = false;
        for (TargetMatch value : matchByIndex) {
            if (value != null) {
                matched = true;
                break;
            }
        }
        if (!matched) return text;

        List<Glyph> outGlyphs = applyReplacementAndStyle(originalGlyphs, matchByIndex, System.currentTimeMillis());
        if (outGlyphs.isEmpty()) return text;

        net.minecraft.network.chat.MutableComponent result = Component.empty();
        StringBuilder run = new StringBuilder();
        Style current = outGlyphs.get(0).style();
        for (Glyph g : outGlyphs) {
            if (!g.style().equals(current) || !java.util.Objects.equals(NicknameGlyphEffect.from(g.style()), NicknameGlyphEffect.from(current))) {
                if (run.length() > 0) {
                    result = result.append(Component.literal(run.toString()).setStyle(current));
                    run.setLength(0);
                }
                current = g.style();
            }
            run.appendCodePoint(g.codepoint());
        }
        if (run.length() > 0) {
            result = result.append(Component.literal(run.toString()).setStyle(current));
        }
        return result;
    }

    public static Component handleComponent(Component component) {
        return component;
    }

    /** Show the configured style in the editor, including before joining a world. */
    public static Component previewNickname(String name) {
        if (name == null || name.isBlank()) name = "YourName";
        int[] codepoints = name.codePoints().toArray();
        List<Glyph> glyphs = new ArrayList<>();
        for (int codepoint : codepoints) glyphs.add(new Glyph(codepoint, Style.EMPTY));
        TargetMatch[] matches = matchTargets(codepoints, List.of(Target.local(name)));
        List<Glyph> rendered = applyReplacementAndStyle(glyphs, matches, System.currentTimeMillis());
        var result = Component.empty();
        for (Glyph glyph : rendered) {
            result.append(Component.literal(new String(Character.toChars(glyph.codepoint()))).setStyle(glyph.style()));
        }
        return result;
    }

    public static String visibleNickname(String raw) {
        var result=new StringBuilder();for(var cp:parseCustomNick(raw==null?"":raw))result.appendCodePoint(cp.codepoint());return result.toString();
    }

    /** Draft-only preview; no active settings or remote state are changed. */
    public static Component previewSegments(String raw,java.util.List<com.customcosmetics.features.nickname.NicknameSegments.Segment> segments,int start,int end) {
        var codepoints=parseCustomNick(raw==null?"":raw);
        var paints=com.customcosmetics.features.nickname.NicknameSegments.paints(codepoints.size(),start,end,segments);
        var result=Component.empty();
        for(int i=0;i<codepoints.size();i++){
            var cp=codepoints.get(i);var paint=paints.get(i);Style style=Style.EMPTY.withBold(ConfigManager.nickTweaksBoldSelf);
            if(cp.explicitColor()!=null)style=style.withColor(cp.explicitColor());
            else style=NicknameGlyphEffect.attach(style.withColor(paint.color(0)),new NicknameGlyphEffect(paint.gradient(),paint.from(),paint.to()));
            result.append(Component.literal(new String(Character.toChars(cp.codepoint()))).setStyle(style));
        }return result;
    }


    public static FormattedCharSequence handleCharSequence(FormattedCharSequence original) {
        if (original == null) return null;
        if(original instanceof com.customcosmetics.features.nickname.NicknameText)return original;
        if (shouldSkipNickTweakSubstitution()) return original;
        List<Target> targets = collectTargets();
        boolean rank=com.customcosmetics.features.nickname.RankAppearance.active();
        if (targets.isEmpty()&&!rank) return original;
        var caches=TEXT_CACHES.get().current();caches.begin();var cached=caches.resolved.get(original);if(cached!=null)return cached;
        var ranked=rank?com.customcosmetics.features.nickname.RankAppearance.apply(original):original;
        var result=targets.isEmpty()?ranked:resolveCharSequence(ranked,targets);
        if(result!=original&&!(result instanceof com.customcosmetics.features.nickname.NicknameText))result=new com.customcosmetics.features.nickname.ProcessedNicknameText(result);
        caches.resolved.put(original,result);return result;
    }

    private static FormattedCharSequence resolveCharSequence(FormattedCharSequence original,List<Target> targets) {

        StringBuilder sbPlain = new StringBuilder();
        boolean[] styled = {false};
        original.accept((index, style, codepoint) -> {
            sbPlain.appendCodePoint(codepoint);
            if (NicknameGlyphEffect.from(style) != null) styled[0] = true;
            return true;
        });
        if (styled[0]) return original;
        String plain = sbPlain.toString();
        if (plain.isEmpty()) return original;

        long version = TARGET_VERSION.get();
        String cacheKey = version + "|" + plain;
        TargetMatch[] matchByIndex;
        synchronized (MATCH_CACHE_LOCK) {
            matchByIndex = MATCH_CACHE.get(cacheKey);
        }

        if (matchByIndex == null) {
            String lower = plain.toLowerCase(Locale.ROOT);
            List<Target> matchingTargets = null;
            for (Target target : targets) {
                if (!lower.contains(target.nameLower())) continue;
                if (matchingTargets == null) matchingTargets = new ArrayList<>();
                matchingTargets.add(target);
            }
            if (matchingTargets == null || matchingTargets.isEmpty()) {
                synchronized(MATCH_CACHE_LOCK){MATCH_CACHE.put(cacheKey,NO_MATCH);}return original;
            }

            int[] codePoints = plain.codePoints().toArray();
            matchByIndex = matchTargets(codePoints, matchingTargets);
            synchronized (MATCH_CACHE_LOCK) {
                MATCH_CACHE.put(cacheKey, matchByIndex);
            }
        }

        boolean matched = false;
        for (TargetMatch value : matchByIndex) {
            if (value != null) {
                matched = true;
                break;
            }
        }
        if (!matched) return original;

        long nowMs = System.currentTimeMillis();

        List<Glyph> glyphs = new ArrayList<>();
        original.accept((index, style, codepoint) -> {
            glyphs.add(new Glyph(codepoint, style));
            return true;
        });
        if (glyphs.isEmpty()) return original;

        List<Glyph> outGlyphs = applyReplacementAndStyle(glyphs, matchByIndex, nowMs);
        List<FormattedCharSequence> out = new ArrayList<>(outGlyphs.size());
        for (Glyph glyph : outGlyphs) {
            out.add(FormattedCharSequence.codepoint(glyph.codepoint(), glyph.style()));
        }
        FormattedCharSequence result = FormattedCharSequence.composite(out);
        return new com.customcosmetics.features.nickname.ProcessedNicknameText(result);
    }

    public static boolean hasSmoothGradient() {
        if (shouldSkipNickTweakSubstitution()) return false;
        return !collectTargets().isEmpty()&&cachedSmooth;
    }
    public static boolean hasActiveTargets(){return !shouldSkipNickTweakSubstitution()&&(!collectTargets().isEmpty()||com.customcosmetics.features.nickname.RankAppearance.active());}

    /** Measure actual advances so narrow Latin letters and wide CJK glyphs share one gradient. */
    public static FormattedCharSequence measureGradient(FormattedCharSequence original, Font font) {
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.NICK))return original;
        if(original==null)return null;
        var caches=TEXT_CACHES.get().current();caches.begin();var cached=caches.measured.get(original);if(cached!=null)return cached;
        var result=measureUncachedGradient(original,font);caches.measured.put(original,result);return result;
    }
    private static FormattedCharSequence measureUncachedGradient(FormattedCharSequence original,Font font){
        boolean[] containsEffect={false};original.accept((index,style,codepoint)->{if(NicknameGlyphEffect.from(style)!=null){containsEffect[0]=true;return false;}return true;});
        if(!containsEffect[0])return original;
        List<Glyph> glyphs = new ArrayList<>();
        original.accept((index, style, codepoint) -> { glyphs.add(new Glyph(codepoint, style)); return true; });
        boolean changed = false;
        for (int i = 0; i < glyphs.size();) {
            NicknameGlyphEffect first = NicknameGlyphEffect.from(glyphs.get(i).style());
            if (first == null) { i++; continue; }
            int end = i + 1;
            NicknameGlyphEffect last = first;
            while (end < glyphs.size()) {
                var next = NicknameGlyphEffect.from(glyphs.get(end).style());
                if (next == null || next.gradient() != first.gradient() || next.from() < last.to() - 1e-8) break;
                last = next; end++;
            }
            double[] widths = new double[end - i];
            double total = 0;
            for (int n = i; n < end; n++) {
                var glyph = glyphs.get(n);
                widths[n - i] = Math.max(0, font.getSplitter().stringWidth(FormattedCharSequence.codepoint(glyph.codepoint(), glyph.style())));
                total += widths[n - i];
            }
            if (total > 0) {
                double position = 0, range = last.to() - first.from();
                for (int n = i; n < end; n++) {
                    var glyph = glyphs.get(n);
                    var effect = new NicknameGlyphEffect(first.gradient(), first.from() + range * position / total,
                        first.from() + range * (position + widths[n - i]) / total);
                    glyphs.set(n, new Glyph(glyph.codepoint(), NicknameGlyphEffect.attach(glyph.style(), effect)));
                    position += widths[n - i];
                }
                changed = true;
            }
            i = end;
        }
        if (!changed) return original;
        return FormattedCharSequence.composite(glyphs.stream().map(glyph -> FormattedCharSequence.codepoint(glyph.codepoint(),glyph.style())).toList());
    }

    private static String applySectionColorToStringWithReplacement(String text, List<Glyph> glyphs, TargetMatch[] matchByIndex) {
        List<Glyph> outGlyphs = applyReplacementAndStyle(glyphs, matchByIndex, System.currentTimeMillis());
        StringBuilder sb = new StringBuilder();
        for (Glyph glyph : outGlyphs) {
            if (glyph.style().getColor() != null) {
                appendHexColor(sb, glyph.style().getColor().getValue());
            } else {
                sb.append('\u00A7').append('r');
            }
            if (glyph.style().isBold()) {
                sb.append('\u00A7').append('l');
            }
            sb.appendCodePoint(glyph.codepoint());
        }
        sb.append('\u00A7').append('r');
        return sb.toString();
    }

    private static List<Glyph> applyReplacementAndStyle(List<Glyph> glyphs, TargetMatch[] matchByIndex, long nowMs) {
        List<Glyph> out = new ArrayList<>(glyphs.size() + 16);
        int i = 0;
        while (i < glyphs.size()) {
            TargetMatch match = matchByIndex[i];
            if (match == null || i != match.start()) {
                out.add(glyphs.get(i));
                i++;
                continue;
            }
            Target target = match.target();
            List<ReplacementCodepoint> replacement = target.replacementCodepoints();
            if (replacement.isEmpty()) {
                i += match.length();
                continue;
            }
            Style base = glyphs.get(i).style();
            NicknameGradient gradient = target.gradient();
            int len = replacement.size();
            var segmentPaints = gradient != null && gradient.style().equals("segmented")
                ? com.customcosmetics.features.nickname.NicknameSegments.paints(len,gradient.start(),gradient.end(),target.segments()) : null;
            for (int idx = 0; idx < len; idx++) {
                ReplacementCodepoint cp = replacement.get(idx);
                Style style = base;
                if (target.bold()) {
                    style = style.withBold(true);
                }
                if (cp.explicitColor() != null) {
                    style = style.withColor(cp.explicitColor());
                } else {
                    int rgb = segmentPaints == null ? target.colorAt(len == 1 ? 0.0 : (double) idx / (len - 1), nowMs) : segmentPaints.get(idx).color(nowMs);
                    if (rgb >= 0) {
                        style = style.withColor(rgb);
                    }
                }
                if (gradient != null && cp.explicitColor() == null) {
                    var paint=segmentPaints==null?null:segmentPaints.get(idx);
                    style = NicknameGlyphEffect.attach(style, paint==null?new NicknameGlyphEffect(gradient, idx / (double)len, (idx + 1) / (double)len)
                        :new NicknameGlyphEffect(paint.gradient(),paint.from(),paint.to()));
                }
                out.add(new Glyph(cp.codepoint(), style));
            }
            i += match.length();
        }
        return out;
    }

    private static void appendHexColor(StringBuilder sb, int rgb) {
        int v = rgb & 0xFFFFFF;
        sb.append('\u00A7').append('x');
        sb.append('\u00A7').append(HEX[(v >> 20) & 0xF]);
        sb.append('\u00A7').append(HEX[(v >> 16) & 0xF]);
        sb.append('\u00A7').append(HEX[(v >> 12) & 0xF]);
        sb.append('\u00A7').append(HEX[(v >> 8) & 0xF]);
        sb.append('\u00A7').append(HEX[(v >> 4) & 0xF]);
        sb.append('\u00A7').append(HEX[v & 0xF]);
    }

    private static TargetMatch[] matchTargets(int[] codePoints, List<Target> targets) {
        TargetMatch[] out = new TargetMatch[codePoints.length];

        int[] lowerCodePoints = new int[codePoints.length];
        for (int i = 0; i < codePoints.length; i++) {
            lowerCodePoints[i] = Character.toLowerCase(codePoints[i]);
        }

        for (Target target : targets) {
            int[] targetLower = target.codePointsLower();
            int tLen = targetLower.length;
            if (tLen == 0 || tLen > codePoints.length) continue;
            int targetFirstLower = target.codePointsFirstLower();
            List<ReplacementAffix> affixes = target.replacementAffixes();

            for (int i = 0; i <= codePoints.length - tLen; i++) {
                if (lowerCodePoints[i] != targetFirstLower) continue;
                if (!isRangeFree(out, i, tLen)) continue;

                boolean ok = true;
                for (int j = 0; j < tLen; j++) {
                    if (lowerCodePoints[i + j] != targetLower[j]) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;

                int end = i + tLen;
                boolean leftBoundary = i == 0 || !isNameCodepoint(codePoints[i - 1]);
                boolean rightBoundary = end >= codePoints.length || !isNameCodepoint(codePoints[end]);
                if (!leftBoundary || !rightBoundary) continue;
                if (isAlreadyReplaced(codePoints, i, end, affixes)) continue;

                TargetMatch match = new TargetMatch(target, i, tLen);
                for (int j = 0; j < tLen; j++) {
                    out[i + j] = match;
                }
            }
        }
        return out;
    }

    private static boolean isRangeFree(TargetMatch[] matches, int start, int len) {
        for (int i = 0; i < len; i++) {
            if (matches[start + i] != null) return false;
        }
        return true;
    }

    private static boolean isNameCodepoint(int codepoint) {
        return Character.isLetterOrDigit(codepoint) || codepoint == '_';
    }

    private static boolean isAlreadyReplaced(int[] codePoints, int start, int end, List<ReplacementAffix> affixes) {
        for (ReplacementAffix affix : affixes) {
            int[] prefix = affix.prefix();
            int[] suffix = affix.suffix();

            int before = start - prefix.length;
            if (before < 0) continue;
            if (end + suffix.length > codePoints.length) continue;

            boolean matches = true;
            for (int j = 0; j < prefix.length; j++) {
                if (Character.toLowerCase(codePoints[before + j]) != Character.toLowerCase(prefix[j])) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            for (int j = 0; j < suffix.length; j++) {
                if (Character.toLowerCase(codePoints[end + j]) != Character.toLowerCase(suffix[j])) {
                    matches = false;
                    break;
                }
            }
            if (matches) return true;
        }
        return false;
    }

    private static boolean hasDiscriminatingCodepoint(int[] codePoints) {
        for (int cp : codePoints) {
            if (cp != '\u00A7' && !Character.isWhitespace(cp)) {
                return true;
            }
        }
        return false;
    }

    private static List<Target> collectTargets() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return List.of();
        String selfName = client.getUser().getName();
        UUID selfUuid = client.getUser().getProfileId();
        long now = System.currentTimeMillis();
        if (now - targetsCacheAt <= TARGET_CACHE_MS && selfName.equals(cachePlayerName) && selfUuid.equals(cachePlayerUuid)) {
            return cachedTargets;
        }

        Module module = ModuleManager.getModuleByName("NickTweaks");
        boolean localNickTweaksEnabled = module != null && module.isEnabled();

        List<Target> targets = buildTargets(selfUuid, selfName,
            localNickTweaksEnabled, PresenceSync.remoteNicknames());

        targets.removeIf(t -> t.name().isBlank());
        targets.sort(Comparator.comparingInt((Target t) -> t.codePoints().length).reversed());
        cachedTargets = List.copyOf(targets);
        cachedSmooth=false;for(var target:targets)if(target.gradient()!=null){cachedSmooth=true;break;}
        cachePlayerName = selfName;
        cachePlayerUuid = selfUuid;
        targetsCacheAt = now;
        TARGET_VERSION.incrementAndGet();
        return cachedTargets;
    }

    static List<Target> buildTargets(UUID selfUuid, String selfName, boolean localEnabled,
                                     List<PresenceSync.RemoteNickname> remote) {
        List<Target> targets = new ArrayList<>();
        if (localEnabled && selfName != null && !selfName.isBlank()) targets.add(Target.local(selfName));
        Set<String> seen = new HashSet<>();
        for (PresenceSync.RemoteNickname entry : remote) {
            // A local player never uses a remote record, including their own stale server-side settings.
            if (entry.uuid().equals(selfUuid) || entry.name().equalsIgnoreCase(selfName)) continue;
            if (com.customcosmetics.config.PlayerBlocklist.contains(ConfigManager.nickTweaksBlockedPlayers, entry.uuid(), entry.name())) continue;
            if (entry.name().isBlank() || !seen.add(entry.name().toLowerCase(Locale.ROOT))) continue;
            targets.add(Target.remote(entry.name(), entry.profile()));
        }
        return targets;
    }

    private static int lerpRgb(int a, int b, double t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int rr = (int) Math.round(ar + (br - ar) * t);
        int rg = (int) Math.round(ag + (bg - ag) * t);
        int rb = (int) Math.round(ab + (bb - ab) * t);
        return (rr << 16) | (rg << 8) | rb;
    }

    private static double positiveModulo(double value, double mod) {
        double result = value % mod;
        return result < 0 ? result + mod : result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Glyph(int codepoint, Style style) {
    }

    private record TargetMatch(Target target, int start, int length) {
    }

    record Target(
            String name,
            String nameLower,
            int[] codePoints,
            int[] codePointsLower,
            int codePointsFirstLower,
            String displayName,
            boolean local,
            boolean bold,
            int[] palette,
            double speed,
            boolean remoteChromaEnabled,
            boolean customNickColorEnabled,
            int remoteGradientStart,
            int remoteGradientEnd,
            String gradientStyle,
            double shimmerSpeed,
            int shimmerColor, int shimmerHighlightColor, double shimmerSize, double shimmerInterval,
            java.util.List<com.customcosmetics.features.nickname.NicknameSegments.Segment> segments
    ) {
        static Target local(String name) {
            int[] cps = name.codePoints().toArray();
            int[] lower = new int[cps.length];
            for (int i = 0; i < cps.length; i++) lower[i] = Character.toLowerCase(cps[i]);
            return new Target(
                    name,
                    name.toLowerCase(Locale.ROOT),
                    cps,
                    lower,
                    lower.length == 0 ? 0 : lower[0],
                    null,
                    true,
                    ConfigManager.nickTweaksBoldSelf,
                    new int[0],
                    clamp(ConfigManager.nickTweaksChromaSpeed, 0.0, 8.0),
                    false,
                    ConfigManager.nickTweaksCustomNickColorEnabled,
                    0,
                    0,
                    ConfigManager.nickTweaksGradientStyle,
                    ConfigManager.nickTweaksShimmerSpeed,
                    ConfigManager.nickTweaksShimmerColor, ConfigManager.nickTweaksShimmerHighlightColor, ConfigManager.nickTweaksShimmerSize,ConfigManager.nickTweaksShimmerInterval,
                    com.customcosmetics.features.nickname.NicknameSegments.normalize(ConfigManager.nickTweaksGradientSegments)
            );
        }

        static Target remote(String name, PresenceSync.ChromaProfile profile) {
            int[] cps = name.codePoints().toArray();
            int[] lower = new int[cps.length];
            for (int i = 0; i < cps.length; i++) lower[i] = Character.toLowerCase(cps[i]);
            return new Target(
                    name,
                    name.toLowerCase(Locale.ROOT),
                    cps,
                    lower,
                    lower.length == 0 ? 0 : lower[0],
                    profile.nickChanger(),
                    false,
                    profile.boldSelf(),
                    profile.paletteView(),
                    clamp(profile.speed(), 0.0, 8.0),
                    profile.chromaEnabled(),
                    profile.customNickColorEnabled(),
                    profile.gradientStart(),
                    profile.gradientEnd(),
                    profile.gradientStyle(),
                    profile.shimmerSpeed(), profile.shimmerColor(), profile.shimmerHighlightColor(), profile.shimmerSize(),profile.shimmerInterval(),profile.segments()
            );
        }

        int colorAt(double progress, long nowMs) {
            NicknameGradient gradient = gradient();
            if (gradient != null) return gradient.color(progress, 0.5, nowMs);
            if (!local) {
                if (remoteChromaEnabled && palette.length > 0) {
                    double phase = (nowMs / 1000.0) * speed;
                    double position = positiveModulo(progress + phase, 1.0) * palette.length;
                    int fromIndex = Math.floorMod((int) Math.floor(position), palette.length);
                    int toIndex = Math.floorMod(fromIndex + 1, palette.length);
                    double frac = position - Math.floor(position);
                    return lerpRgb(palette[fromIndex], palette[toIndex], frac);
                }
                if (!customNickColorEnabled) {
                    return -1;
                }
                if (remoteGradientStart == remoteGradientEnd) {
                    return remoteGradientStart;
                }
                return lerpRgb(remoteGradientStart, remoteGradientEnd, progress);
            }

            if (!ConfigManager.nickTweaksChromaEnabled) {
                if (!ConfigManager.nickTweaksCustomNickColorEnabled) {
                    return -1;
                }
                int localStart = ConfigManager.nickTweaksGradientStartColor & 0xFFFFFF;
                int localEnd = ConfigManager.nickTweaksGradientEndColor & 0xFFFFFF;
                if (localStart == localEnd) {
                    return localStart;
                }
                return lerpRgb(
                        localStart,
                        localEnd,
                        progress
                );
            }

            double lightness = clamp(ConfigManager.nickTweaksChromaLightness, 0.2, 1.0);
            double chroma = clamp(ConfigManager.nickTweaksChromaChroma, 0.0, 0.4);
            double size = Math.max(0.1, ConfigManager.nickTweaksChromaSize);
            double phase = (nowMs / 1000.0) * (speed * 0.5);
            float saturation = (float) (chroma / 0.4);
            float hue = (float) positiveModulo((progress / size) - phase, 1.0);
            return Mth.hsvToRgb(hue, saturation, (float) lightness);
        }

        NicknameGradient gradient() {
            boolean custom = local ? ConfigManager.nickTweaksCustomNickColorEnabled : customNickColorEnabled;
            boolean chroma = local ? ConfigManager.nickTweaksChromaEnabled : remoteChromaEnabled;
            String kind = NicknameGradient.normalizeStyle(local ? ConfigManager.nickTweaksGradientStyle : gradientStyle);
            if (!custom || chroma || kind.equals("character")) return null;
            return new NicknameGradient(kind, local ? ConfigManager.nickTweaksGradientStartColor : remoteGradientStart,
                local ? ConfigManager.nickTweaksGradientEndColor : remoteGradientEnd,
                local ? ConfigManager.nickTweaksShimmerSpeed : shimmerSpeed,
                local ? ConfigManager.nickTweaksShimmerColor : shimmerColor,
                local ? ConfigManager.nickTweaksShimmerHighlightColor : shimmerHighlightColor,
                local ? ConfigManager.nickTweaksShimmerSize : shimmerSize,
                local ? ConfigManager.nickTweaksShimmerInterval : shimmerInterval);
        }

        String replacementRaw() {
            if (local) {
                String raw = com.customcosmetics.config.NicknameLimit.limit(ConfigManager.nickTweaksNickChanger);
                return raw == null || raw.isBlank() ? name : raw;
            }
            return displayName == null || displayName.isBlank() ? name : displayName;
        }

        List<ReplacementCodepoint> replacementCodepoints() {
            String raw = replacementRaw();

            List<ReplacementCodepoint> cached;
            synchronized (REPLACEMENT_CACHE_LOCK) {
                cached = REPLACEMENT_CACHE.get(raw);
            }
            if (cached != null) return cached;
            List<ReplacementCodepoint> parsed = parseCustomNick(raw);
            synchronized (REPLACEMENT_CACHE_LOCK) {
                REPLACEMENT_CACHE.put(raw, parsed);
            }
            return parsed;
        }

        List<ReplacementAffix> replacementAffixes() {
            String key = name + "\u0000" + replacementRaw();
            synchronized (REPLACEMENT_AFFIX_CACHE_LOCK) {
                List<ReplacementAffix> cached = REPLACEMENT_AFFIX_CACHE.get(key);
                if (cached != null) return cached;
            }
            List<ReplacementAffix> computed = computeReplacementAffixes(replacementCodepoints());
            synchronized (REPLACEMENT_AFFIX_CACHE_LOCK) {
                REPLACEMENT_AFFIX_CACHE.put(key, computed);
            }
            return computed;
        }

        private List<ReplacementAffix> computeReplacementAffixes(List<ReplacementCodepoint> replacement) {
            int replacementLength = replacement.size();
            int targetLength = codePoints.length;
            if (replacementLength == 0 || targetLength == 0 || targetLength > replacementLength) {
                return List.of();
            }

            int[] codepoints = new int[replacementLength];
            int[] lower = new int[replacementLength];
            for (int i = 0; i < replacementLength; i++) {
                int cp = replacement.get(i).codepoint();
                codepoints[i] = cp;
                lower[i] = Character.toLowerCase(cp);
            }

            List<ReplacementAffix> affixes = new ArrayList<>();
            for (int start = 0; start <= replacementLength - targetLength; start++) {
                boolean same = true;
                for (int j = 0; j < targetLength; j++) {
                    if (lower[start + j] != codePointsLower[j]) {
                        same = false;
                        break;
                    }
                }
                if (!same) continue;

                int[] prefix = new int[start];
                System.arraycopy(codepoints, 0, prefix, 0, start);
                int suffixLength = replacementLength - start - targetLength;
                int[] suffix = new int[suffixLength];
                System.arraycopy(codepoints, start + targetLength, suffix, 0, suffixLength);

                if (!hasDiscriminatingCodepoint(prefix) && !hasDiscriminatingCodepoint(suffix)) continue;
                affixes.add(new ReplacementAffix(prefix, suffix));
            }
            return List.copyOf(affixes);
        }
    }


    private static List<ReplacementCodepoint> parseCustomNick(String raw) {
        ArrayList<ReplacementCodepoint> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        Integer explicitColor = null;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            int cpl = Character.charCount(cp);
            if (cp == '&' && i + cpl < raw.length()) {
                int ncp = raw.codePointAt(i + cpl);
                char ch = Character.toLowerCase((char) ncp);
                Integer mapped = mapLegacyColor(ch);
                if (mapped != null) {
                    explicitColor = mapped;
                    i += cpl + Character.charCount(ncp);
                    continue;
                }
                if (ch == 'r') {
                    explicitColor = null;
                    i += cpl + Character.charCount(ncp);
                    continue;
                }
            }
            out.add(new ReplacementCodepoint(cp, explicitColor));
            i += cpl;
        }
        return out;
    }

    public static Integer legacyColorRgb(char c) {
        return mapLegacyColor(Character.toLowerCase(c));
    }

    private static Integer mapLegacyColor(char c) {
        return switch (c) {
            case '0' -> 0x000000;
            case '1' -> 0x0000AA;
            case '2' -> 0x00AA00;
            case '3' -> 0x00AAAA;
            case '4' -> 0xAA0000;
            case '5' -> 0xAA00AA;
            case '6' -> 0xFFAA00;
            case '7' -> 0xAAAAAA;
            case '8' -> 0x555555;
            case '9' -> 0x5555FF;
            case 'a' -> 0x55FF55;
            case 'b' -> 0x55FFFF;
            case 'c' -> 0xFF5555;
            case 'd' -> 0xFF55FF;
            case 'e' -> 0xFFFF55;
            case 'f' -> 0xFFFFFF;
            default -> null;
        };
    }

    private record ReplacementAffix(int[] prefix, int[] suffix) {
    }

    private record ReplacementCodepoint(int codepoint, Integer explicitColor) {
    }
}
