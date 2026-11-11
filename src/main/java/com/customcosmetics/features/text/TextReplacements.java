// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.text;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.nickname.RankText;
import com.customcosmetics.sync.ExperimentalAccess;
import com.customcosmetics.utils.IdentityFrameCache;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Local display substitutions, with the original components and messages kept by Minecraft. */
public final class TextReplacements {
    public static final int MAX_RULES = 64;
    public static final int MAX_SOURCE = 128;
    public static final int MAX_REPLACEMENT = 256;

    private static final ThreadLocal<Boolean> RAW_WIDTH = new ThreadLocal<>();
    private static final ThreadLocal<TextCache> CACHE = ThreadLocal.withInitial(TextCache::new);
    private static volatile Rules rules = new Rules(Map.of());
    private static volatile long frame;
    private static volatile long revision;

    private TextReplacements() {}

    public static boolean valid(String source, String replacement) {
        return source != null && !source.isBlank() && source.length() <= MAX_SOURCE && clean(source)
            && replacement != null && replacement.length() <= MAX_REPLACEMENT && clean(replacement)
            && !source.equals(replacement);
    }

    private static boolean clean(String text) {
        return text.indexOf('§') < 0 && text.chars().noneMatch(Character::isISOControl);
    }

    public static Map<String, String> normalize(Map<String, String> input) {
        if (input == null || input.isEmpty()) return Map.of();
        var result = new LinkedHashMap<String, String>();
        for (var entry : input.entrySet()) {
            if (valid(entry.getKey(), entry.getValue())) result.put(entry.getKey(), entry.getValue());
            if (result.size() == MAX_RULES) break;
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    public static void setRules(Map<String, String> input) {
        rules = new Rules(normalize(input));
        revision++;
    }

    public static boolean active() {
        return ConfigManager.textReplacementsEnabled && rules.pattern != null && ExperimentalAccess.allowed()
            && RAW_WIDTH.get() == null;
    }

    public static void beginFrame() { if (ConfigManager.textReplacementsEnabled) frame++; }

    public static void refreshChat(Minecraft client) {
        if (client.gui != null) ClientUiAccess.chat(client).rescaleChat();
    }

    public static int rawWidth(IntSupplier measure) {
        if (!active()) return measure.getAsInt();
        RAW_WIDTH.set(Boolean.TRUE);
        try { return measure.getAsInt(); }
        finally { RAW_WIDTH.remove(); }
    }

    public static String apply(String text) {
        return text == null || text.isEmpty() || !active() ? text : rules.replace(text);
    }

    public static FormattedCharSequence apply(FormattedCharSequence text) {
        if (text == null || text instanceof Resolved || !active()) return text;
        var cache = CACHE.get();
        cache.sequences.begin(frame, revision);
        var cached = cache.sequences.get(text);
        if (cached != null) return cached;
        var result = rules.replace(text);
        cache.sequences.put(text, result);
        return result;
    }

    public static FormattedText apply(FormattedText text) {
        if (text == null || text instanceof ResolvedText || !active()) return text;
        var cache = CACHE.get();
        cache.formatted.begin(frame, revision);
        var cached = cache.formatted.get(text);
        if (cached != null) return cached;
        var sequence = RankText.sequence(text);
        var replaced = rules.replace(sequence);
        FormattedText result = replaced == sequence ? text : new ResolvedText(RankText.component(replaced));
        cache.formatted.put(text, result);
        return result;
    }

    public static FormattedCharSequence visualOrder(Language language, FormattedText text) {
        var replacement = apply(text);
        var sequence = language.getVisualOrder(replacement);
        return replacement instanceof ResolvedText ? resolved(sequence) : sequence;
    }

    public interface Resolved extends FormattedCharSequence {}

    public static FormattedCharSequence resolved(FormattedCharSequence text) {
        if (text instanceof Resolved) return text;
        return text instanceof com.customcosmetics.features.nickname.NicknameText
            ? new ResolvedNicknameSequence(text) : new ResolvedSequence(text);
    }

    public static List<FormattedCharSequence> resolvedLines(List<FormattedCharSequence> lines) {
        return lines.stream().map(TextReplacements::resolved).toList();
    }

    private record ResolvedSequence(FormattedCharSequence delegate) implements Resolved {
        @Override public boolean accept(FormattedCharSink sink) { return delegate.accept(sink); }
    }

    private record ResolvedNicknameSequence(FormattedCharSequence delegate) implements Resolved, com.customcosmetics.features.nickname.NicknameText {
        @Override public boolean accept(FormattedCharSink sink) { return delegate.accept(sink); }
    }

    private record ResolvedText(FormattedText delegate) implements FormattedText {
        @Override public <T> Optional<T> visit(ContentConsumer<T> consumer) { return delegate.visit(consumer); }
        @Override public <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style style) { return delegate.visit(consumer, style); }
    }

    private static final class TextCache {
        final IdentityFrameCache<FormattedCharSequence, FormattedCharSequence> sequences = new IdentityFrameCache<>(2048);
        final IdentityFrameCache<FormattedText, FormattedText> formatted = new IdentityFrameCache<>(1024);
    }

    private record Match(int start, int end, String replacement) {}
    private record Glyph(int codepoint, Style style) {}

    static final class Rules {
        private final Map<String, String> replacements;
        private final Pattern pattern;
        private final Map<String, List<Match>> plans = new LinkedHashMap<>(32, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, List<Match>> eldest) { return size() > 512; }
        };

        Rules(Map<String, String> replacements) {
            this.replacements = replacements;
            pattern = replacements.isEmpty() ? null : Pattern.compile(replacements.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote).collect(Collectors.joining("|")));
        }

        String replace(String text) {
            if (text.indexOf('§') >= 0) {
                FormattedCharSequence source = sink -> StringDecomposer.iterateFormatted(text, Style.EMPTY, sink);
                var result = replace(source);
                return result == source ? text : RankText.legacy(result);
            }
            var matches = matches(text);
            if (matches.isEmpty()) return text;
            var output = new StringBuilder(text.length());
            int cursor = 0;
            for (var match : matches) {
                output.append(text, cursor, match.start()).append(match.replacement());
                cursor = match.end();
            }
            return output.append(text, cursor, text.length()).toString();
        }

        FormattedCharSequence replace(FormattedCharSequence source) {
            var plain = new StringBuilder();
            source.accept((index, style, codepoint) -> { plain.appendCodePoint(codepoint); return true; });
            var matches = matches(plain.toString());
            if (matches.isEmpty()) return source;

            var output = new ArrayList<Glyph>();
            int[] offset = {0}, next = {0};
            source.accept((index, style, codepoint) -> {
                int position = offset[0];
                offset[0] += Character.charCount(codepoint);
                if (next[0] < matches.size()) {
                    var match = matches.get(next[0]);
                    if (position >= match.start()) {
                        if (position == match.start()) {
                            String replacement = match.replacement();
                            for (int i = 0; i < replacement.length();) {
                                int cp = replacement.codePointAt(i);
                                output.add(new Glyph(cp, style));
                                i += Character.charCount(cp);
                            }
                        }
                        if (offset[0] >= match.end()) next[0]++;
                        return true;
                    }
                }
                output.add(new Glyph(codepoint, style));
                return true;
            });
            return resolved(sink -> {
                int index = 0;
                for (var glyph : output) {
                    if (!sink.accept(index, glyph.style(), glyph.codepoint())) return false;
                    index += Character.charCount(glyph.codepoint());
                }
                return true;
            });
        }

        private synchronized List<Match> matches(String text) {
            if (pattern == null || text.isEmpty()) return List.of();
            var cached = plans.get(text);
            if (cached != null) return cached;
            var matcher = pattern.matcher(text);
            List<Match> result = List.of();
            if (matcher.find()) {
                var found = new ArrayList<Match>();
                long renderedLength = text.length();
                int maximumLength = Math.max(16_384, text.length());
                do {
                    String replacement = replacements.get(matcher.group());
                    renderedLength += replacement.length() - (matcher.end() - matcher.start());
                    if (renderedLength > maximumLength) return List.of();
                    found.add(new Match(matcher.start(), matcher.end(), replacement));
                } while (matcher.find());
                result = List.copyOf(found);
            }
            if (text.length() <= 1024 && result.size() <= 64) plans.put(text, result);
            return result;
        }
    }
}
