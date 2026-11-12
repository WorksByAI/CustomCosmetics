// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.lwjgl.util.freetype.FreeType.*;

/** Finds real weights once per font selection, off the render thread. */
final class LocalFontFaces {
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private static final int MAX_FACES = 32;
    record Face(Path path, int index, String family, boolean bold, boolean italic) {}
    record Family(Path path, Face selected, Face regular, Face bold) {
        boolean canSwitch() { return regular != null && bold != null; }
        Face choose(Boolean preferBold) {
            return preferBold == null || !canSwitch() ? selected : preferBold ? bold : regular;
        }
    }

    private LocalFontFaces() {}

    static Family resolve(Path path) {
        List<Face> original = faces(path);
        if (original.isEmpty()) return null;
        Face selected = original.getFirst();
        Face opposite = matching(selected, original);
        if (opposite == null) {
            List<Path> nearby = nearbyFonts(path.getParent());
            opposite = find(selected, nearby, nearby.size() <= 24);
            if (opposite == null) opposite = find(selected, UiFonts.systemFonts(), false);
        }
        return selected.bold() ? new Family(path, selected, opposite, selected) : new Family(path, selected, selected, opposite);
    }

    private static List<Path> nearbyFonts(Path directory) {
        if (directory == null) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(UiFonts::isFont).filter(Files::isRegularFile).sorted().limit(512).toList();
        } catch (IOException | java.io.UncheckedIOException | SecurityException unavailable) {
            return List.of();
        }
    }

    private static Face find(Face selected, List<Path> candidates, boolean inspectAll) {
        var candidatesToRead = candidates.stream().map(p -> p.toAbsolutePath().normalize())
            .filter(path -> !path.equals(selected.path()))
            .filter(path -> inspectAll || relatedName(path, selected)).limit(32);
        return candidatesToRead.map(path -> matching(selected, faces(path))).filter(java.util.Objects::nonNull)
            .findFirst().orElse(null);
    }

    private static Face matching(Face selected, List<Face> faces) {
        if (selected.family().isEmpty()) return null;
        return faces.stream().filter(candidate -> candidate.family().equals(selected.family())
                && candidate.italic() == selected.italic() && candidate.bold() != selected.bold())
            .findFirst().orElse(null);
    }

    private static boolean relatedName(Path path, Face selected) {
        String candidate = stem(path), reference = stem(selected.path()), family = selected.family();
        return candidate.startsWith(family) || reference.length() >= 3 && candidate.startsWith(reference)
            || candidate.length() >= 3 && reference.startsWith(candidate);
    }

    private static String stem(Path path) {
        String name = path.getFileName().toString();
        return normalize(name.substring(0, name.lastIndexOf('.')))
            .replaceFirst("(regular|normal|medium|book|light|thin|bold|variable)(italic|oblique)?$", "");
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static boolean readable(Path path) throws IOException {
        return UiFonts.isFont(path) && Files.isRegularFile(path) && Files.size(path) <= MAX_BYTES;
    }

    private static List<Face> faces(Path path) {
        ByteBuffer data = null;
        try {
            if (!readable(path)) return List.of();
            try (var stream = Files.newInputStream(path)) {
                data = TextureUtil.readResource(stream);
            }
            var faces = new ArrayList<Face>();
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                try (var stack = MemoryStack.stackPush()) {
                    var pointer = stack.mallocPointer(1);
                    long library = FreeTypeUtil.getLibrary();
                    int count = 1;
                    for (int index = 0; index < count; index++) {
                        if (FT_New_Memory_Face(library, data, index, pointer) != 0) continue;
                        var face = FT_Face.create(pointer.get(0));
                        int instances;
                        try {
                            if (index == 0) count = (int) Math.clamp(face.num_faces(), 1, MAX_FACES);
                            instances = (int) Math.min(32, face.style_flags() >>> 16 & 0x7FFF);
                            addFace(faces, path, index, face);
                        } finally {
                            FT_Done_Face(face);
                        }
                        for (int instance = 1; instance <= instances; instance++) {
                            int faceIndex = instance << 16 | index;
                            if (FT_New_Memory_Face(library, data, faceIndex, pointer) != 0) continue;
                            var named = FT_Face.create(pointer.get(0));
                            try { addFace(faces, path, faceIndex, named); }
                            finally { FT_Done_Face(named); }
                        }
                    }
                }
            }
            return faces;
        } catch (IOException | RuntimeException | LinkageError unavailable) {
            return List.of();
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static void addFace(List<Face> faces, Path path, int index, FT_Face face) {
        if (!"TrueType".equals(FT_Get_Font_Format(face))) return;
        String family = normalize(face.family_nameString()).replaceFirst("(regular|medium|semibold|light|bold)$", "");
        String style = normalize(face.style_nameString());
        long flags = face.style_flags();
        boolean bold = (flags & FT_STYLE_FLAG_BOLD) != 0 || style.matches("bold(italic|oblique)?");
        boolean italic = (flags & FT_STYLE_FLAG_ITALIC) != 0 || style.endsWith("italic") || style.endsWith("oblique");
        faces.add(new Face(path, index, family, bold, italic));
    }
}
