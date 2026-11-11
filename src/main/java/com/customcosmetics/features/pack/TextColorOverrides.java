// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.pack;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.sync.ExperimentalAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Local replacements for the RGB values submitted to Minecraft's text pipelines. */
public final class TextColorOverrides {
    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6})");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(");
    private static final Pattern HYPIXEL_COLOR = Pattern.compile("\\bgetModifiedVanillaColor\\s*\\(\\s*Color\\s*\\)");
    private static final Pattern VANILLA_COLOR = Pattern.compile("(\\bvertexColor\\s*=\\s*)Color\\b");
    private static volatile Map<Integer, Integer> active = Map.of();

    private TextColorOverrides() {}

    public static Integer parse(String input) {
        if (input == null) return null;
        String value = input.strip().toLowerCase(Locale.ROOT);
        var hex = HEX.matcher(value);
        if (hex.matches()) return Integer.parseInt(hex.group(1), 16);
        if (value.startsWith("#")) return null;
        if (value.length() == 2 && (value.charAt(0) == '&' || value.charAt(0) == '§')) {
            var format = ChatFormatting.getByCode(value.charAt(1));
            var color = format == null ? null : TextColor.fromLegacyFormat(format);
            return color == null ? null : color.getValue();
        }
        return TextColor.parseColor(value.replace(' ', '_').replace('-', '_')).result().map(TextColor::getValue).orElse(null);
    }

    public static String hex(int color) {
        return String.format(Locale.ROOT, "#%06X", color);
    }

    static boolean update() {
        Map<Integer, Integer> next = Map.of();
        if (ConfigManager.customTextColorsEnabled && ExperimentalAccess.allowed()) {
            var rules = new TreeMap<Integer, Integer>();
            ConfigManager.customTextColors.forEach((from, to) -> {
                Integer source = parse(from), target = parse(to);
                if (source != null && target != null && !source.equals(target)) rules.put(source, target);
            });
            next = java.util.Collections.unmodifiableMap(rules);
        }
        if (active.equals(next)) return false;
        active = next;
        return true;
    }

    public static String apply(Identifier location, String shader) {
        var rules = active;
        if (rules.isEmpty() || !"minecraft".equals(location.getNamespace())) return shader;
        switch (location.getPath()) {
            case "shaders/core/text.vsh", "shaders/core/rendertype_text.vsh", "shaders/core/rendertype_text_see_through.vsh",
                "shaders/core/rendertype_text_intensity.vsh", "shaders/core/rendertype_text_intensity_see_through.vsh" -> {}
            default -> { return shader; }
        }
        var main = MAIN.matcher(shader);
        if (!main.find()) return shader;
        String body = shader.substring(main.start());
        var hypixel = HYPIXEL_COLOR.matcher(body);
        String patched = hypixel.find()
            ? hypixel.replaceAll("ccosTextColor(Color, $0)")
            : VANILLA_COLOR.matcher(body).replaceAll("$1ccosTextColor(Color, Color)");
        if (body.equals(patched)) return shader;

        var function = new StringBuilder("int ccosTextReplacement(int rgb) {\n    switch (rgb) {\n");
        rules.forEach((from, to) -> function.append("        case ").append(from).append(": return ").append(to).append(";\n"));
        function.append("""
                    default: return -1;
                }
            }
            int ccosTextRgb(vec4 color) {
                ivec3 rgb = ivec3(round(color.rgb * 255.0));
                return (rgb.r << 16) | (rgb.g << 8) | rgb.b;
            }
            vec4 ccosTextColor(vec4 original, vec4 fallbackColor) {
                int source = ccosTextRgb(original);
                int replacement = ccosTextReplacement(source);
                if (replacement < 0) {
                    int fallback = ccosTextRgb(fallbackColor);
                    if (fallback != source) replacement = ccosTextReplacement(fallback);
                }
                if (replacement < 0) return fallbackColor;
                return vec4(vec3(replacement >> 16, (replacement >> 8) & 255, replacement & 255) / 255.0, original.a);
            }
            """);
        return shader.substring(0, main.start()) + function + patched;
    }
}
