package com.customcosmetics.distribution;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LicensePackagingTest {
    private static final Path ROOT = Path.of(System.getProperty("customcosmetics.projectDir"));
    private static final String SPDX = "AGPL-3.0-or-later";

    @Test
    void licenseKeepsTheCompleteStandardTextWithoutProjectSpecificEdits() throws Exception {
        // Pinned after comparing the complete standard text with SPDX and the Baity terms.
        // Preserve the existing HTTPS links; normalize Windows line endings only.
        String license = Files.readString(ROOT.resolve("LICENSE")).replace("\r\n", "\n");
        assertEquals("8486a10c4393cee1c25392769ddd3b2d6c242d6ec7928e1414efff7dfb2f07ef",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(license.getBytes(StandardCharsets.UTF_8))),
            "The AGPL text must not be truncated or replaced with a project attribution");
    }

    @Test
    void noticesPreserveOriginalCopyrightAndIdentifyDatedModifications() throws Exception {
        String notice = Files.readString(ROOT.resolve("NOTICE")).replace("\r\n", "\n");
        assertTrue(notice.contains("Copyright (C) 2025  raueyhs"));
        assertTrue(notice.contains("11YearCookieBuff") && notice.contains("shyeuar"));
        assertTrue(notice.contains("https://github.com/raueyhs/Baity"));
        assertTrue(notice.contains("SmolPeople") && notice.contains("NickTweaks"));
        assertTrue(notice.contains("2026-10-11") && notice.contains("modified derivative project"));
        assertTrue(notice.contains("SPDX-License-Identifier: " + SPDX));
        assertTrue(notice.contains("either version 3 of the License, or\n    (at your option) any later version."));
        assertTrue(notice.contains("WITHOUT ANY WARRANTY"));
        String readme = Files.readString(ROOT.resolve("README.md"));
        assertTrue(readme.contains(SPDX));
        assertTrue(readme.contains("[LICENSE](https://github.com/WorksByAI/CustomCosmetics/blob/main/LICENSE)")
            && readme.contains("[NOTICE](https://github.com/WorksByAI/CustomCosmetics/blob/main/NOTICE)"));
    }

    @Test
    void binaryContainsTheCompleteLicenseNoticesAndMatchingLicenseMetadata() throws Exception {
        try (var jar = new ZipFile(System.getProperty("customcosmetics.binaryJar"))) {
            assertBundled(jar, "LICENSE_customcosmetics", "LICENSE");
            assertBundled(jar, "NOTICE", "NOTICE");
            assertBundled(jar, "LICENSES/Oklab-MIT.txt", "LICENSES/Oklab-MIT.txt");
            assertBundled(jar, "LICENSES/Apache-2.0.txt", "LICENSES/Apache-2.0.txt");
            assertBundled(jar, "LICENSES/SwordBlocking-MIT.txt", "LICENSES/SwordBlocking-MIT.txt");
            assertBundled(jar, "LICENSES/NoammAddons-CC0.txt", "LICENSES/NoammAddons-CC0.txt");
            assertBundled(jar, "LICENSES/BringBlockingBack-MIT.txt", "LICENSES/BringBlockingBack-MIT.txt");
            assertBundled(jar, "assets/customcosmetics/textures/gui/github.png",
                "src/main/resources/assets/customcosmetics/textures/gui/github.png");
            var entry = jar.getEntry("fabric.mod.json");
            assertNotNull(entry);
            try (var input = jar.getInputStream(entry)) {
                var metadata = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals(SPDX, metadata.get("license").getAsString());
                assertEquals(System.getProperty("customcosmetics.version"), metadata.get("version").getAsString());
                assertEquals("https://github.com/WorksByAI/CustomCosmetics",
                    metadata.getAsJsonObject("contact").get("sources").getAsString());
            }
            assertTrue(jar.stream().noneMatch(item -> item.getName().startsWith("net/minecraft/")
                || item.getName().startsWith("net/fabricmc/") || item.getName().endsWith(".jar")),
                "The mod must not redistribute game binaries or dependencies");
            assertTrue(jar.stream().noneMatch(item -> item.getName().startsWith("com/customcosmetics/gametest/")),
                "Game input automation belongs only in the isolated test mod");
            assertNull(jar.getEntry("customcosmetics-test.mixins.json"));
            assertNull(jar.getEntry("assets/customcosmetics/items/appearance_test.json"), "Test-only item models must not enter the client JAR");
        }
    }

    @Test
    void sourcesJarAlsoContainsTheCompleteLicenseAndCopyrightNotices() throws Exception {
        try (var jar = new ZipFile(System.getProperty("customcosmetics.sourcesJar"))) {
            assertBundled(jar, "LICENSE", "LICENSE");
            assertBundled(jar, "NOTICE", "NOTICE");
            assertBundled(jar, "LICENSES/Oklab-MIT.txt", "LICENSES/Oklab-MIT.txt");
        }
    }

    @Test
    void fullSourceContainsBuildAndInstallationInputsWithoutRuntimeData() throws Exception {
        try (var zip = new ZipFile(System.getProperty("customcosmetics.fullSourceZip"))) {
            String prefix = "CustomCosmetics-" + System.getProperty("customcosmetics.version") + "/";
            for (String file : new String[] { "LICENSE", "NOTICE", "LICENSES/Oklab-MIT.txt",
                "LICENSES/Apache-2.0.txt", "LICENSES/SwordBlocking-MIT.txt", "build.gradle", "settings.gradle", "gradle.properties",
                "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
                "gradle/wrapper/gradle-wrapper.properties", "src/main/resources/fabric.mod.json",
                "src/main/java/com/customcosmetics/utils/ColorGradientUtils.java",
                "src/test/java/com/customcosmetics/distribution/LicensePackagingTest.java",
                "src/gametest/java/com/customcosmetics/gametest/BlockingGameTest.java",
                "src/gametest/java/com/customcosmetics/gametest/SettingsGuiGameTest.java",
                "src/main/java/com/customcosmetics/config/RemoteServerConfig.java",
                "src/main/java/com/customcosmetics/gui/ScreenSession.java",
                "src/gametest/resources/fabric.mod.json", "src/gametest/resources/customcosmetics-test.mixins.json",
                "tools/update_weapon_catalog.py", "tools/test_update_weapon_catalog.py",
                "README.md", "CHANGELOG.md" }) {
                assertBundled(zip, prefix + file, file);
            }
            assertCleanSource(zip, prefix);
        }
    }

    @Test
    void readmeStaysConciseAndThirdPartyLogoHasSeparateRights() throws Exception {
        String readme = Files.readString(ROOT.resolve("README.md"));
        assertTrue(readme.length() < 3800, "The bilingual README should stay a short feature overview");
        assertTrue(readme.contains("## English") && readme.contains("基于 Fabric") && readme.contains("A Fabric mod"));
        assertFalse(readme.contains("/docs/") || readme.contains("SOURCE-DELIVERY.md"));
        String changes = Files.readString(ROOT.resolve("CHANGELOG.md"));
        assertEquals("NickTweaks: Add color presets and customizable shimmer colors.", changes.split("## 1.0.2\\n\\n")[1].split("\\n\\n##")[0].trim());
        assertFalse(readme.contains("syncServerIp") || readme.contains("gradlew.bat"));
        String notice = Files.readString(ROOT.resolve("NOTICE"));
        assertTrue(notice.contains("Copyright (c) 2020 MidnightDust"));
        assertTrue(notice.contains("GitHub retains its copyright and trademark rights"));
        byte[] logo = Files.readAllBytes(ROOT.resolve("src/main/resources/assets/customcosmetics/textures/gui/github.png"));
        // Official GitHub Invertocat White PNG, preserved byte-for-byte.
        assertEquals("0d4c235fef9efec54174a7c005fc0fe0ce2d63d35c21898ab5148587111397a9",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(logo)));
    }

    @Test
    void publicSnapshotContainsOnlyReleaseDocumentationAndClientSources() throws Exception {
        assertFalse(Files.exists(ROOT.resolve("docs")));
        assertFalse(Files.exists(ROOT.resolve("SOURCE-DELIVERY.md")));
        assertFalse(Files.exists(ROOT.resolve("server")));
        try (var zip = new ZipFile(System.getProperty("customcosmetics.fullSourceZip"))) {
            String prefix = "CustomCosmetics-" + System.getProperty("customcosmetics.version") + "/";
            for (String type : new String[]{"UdpPresenceClient", "WebSocketPresenceClient"})
                assertNotNull(zip.getEntry(prefix + "src/main/java/com/customcosmetics/sync/" + type + ".java"));
            assertTrue(zip.stream().noneMatch(entry -> entry.getName().startsWith(prefix + "server/") || entry.getName().contains("/docs/")));
            assertTrue(zip.stream().filter(entry -> entry.getName().endsWith(".md"))
                .allMatch(entry -> entry.getName().equals(prefix + "README.md") || entry.getName().equals(prefix + "CHANGELOG.md")));
        }
        assertFalse(Files.readString(ROOT.resolve("NOTICE")).contains("docs/"));
    }

    @Test
    void wrapperAndDownloadArePinnedToVerifiedOfficialGradleRelease() throws Exception {
        byte[] wrapper = Files.readAllBytes(ROOT.resolve("gradle/wrapper/gradle-wrapper.jar"));
        assertEquals("497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(wrapper)));
        String properties = Files.readString(ROOT.resolve("gradle/wrapper/gradle-wrapper.properties"));
        assertTrue(properties.contains("services.gradle.org/distributions/gradle-9.5.1-bin.zip"));
        assertTrue(properties.contains("distributionSha256Sum=bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f"));
        assertTrue(properties.contains("validateDistributionUrl=true"));
        String mit = Files.readString(ROOT.resolve("LICENSES/Oklab-MIT.txt"));
        assertTrue(mit.contains("Copyright (c) 2020 Björn Ottosson") && mit.contains("Permission is hereby granted"));
        assertTrue(mit.contains("THE SOFTWARE IS PROVIDED") && mit.contains("copies or substantial portions"));
    }

    private static void assertCleanSource(ZipFile zip, String prefix) {
        zip.stream().forEach(entry -> {
            String name = entry.getName();
            assertTrue(name.startsWith(prefix));
            assertFalse(name.matches(".*(?:^|/)(?:\\.git|\\.private|\\.gradle|\\.runtime|data|logs|build|run|node_modules)(?:/.*)?$"), name);
            assertFalse(name.matches(".*(?:\\.sqlite(?:-wal|-shm)?|\\.log|/\\.env(?:\\..*)?)$"), name);
            assertFalse(name.endsWith("worker.js") || name.endsWith("wrangler.toml") || name.endsWith("server/server.zip"), name);
            assertFalse(name.endsWith(".jar") && !name.endsWith("gradle/wrapper/gradle-wrapper.jar"), name);
        });
    }

    private static void assertBundled(ZipFile jar, String entryName, String sourceName) throws Exception {
        var entry = jar.getEntry(entryName);
        assertNotNull(entry, "Missing distribution notice: " + entryName);
        try (var input = jar.getInputStream(entry)) {
            assertArrayEquals(Files.readAllBytes(ROOT.resolve(sourceName)), input.readAllBytes());
        }
    }
}
