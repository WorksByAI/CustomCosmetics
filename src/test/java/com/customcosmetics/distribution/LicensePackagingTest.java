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
    @Test void readmeIncludesRequestedChineseIntroductionAndMatchingEnglishFeatures() throws Exception {
        String readme=Files.readString(ROOT.resolve("README.md"));
        String[] languages=readme.split("## English",2);
        assertEquals(2,languages.length);
        String version=System.getProperty("customcosmetics.version");
        assertTrue(languages[0].contains("CustomCosmetics v"+version+" 发布！"));
        assertTrue(languages[1].contains("CustomCosmetics v"+version));
        for(String section:languages){
            for(String feature:new String[]{"Player Size","NickTweaks","Animation","Cape","Equipments","Sharing","Skins","Item Customization","Damage Splash","Map Editor","Offline Mode"})
                assertTrue(section.contains(feature+"**"),feature);
            assertEquals(7,section.lines().filter(line->line.startsWith("- **[√] ")).count());
            for(String local:new String[]{"Item Customization","Damage Splash","Map Editor","Offline Mode"})
                assertTrue(section.contains("- **"+local+"**"),local);
            assertTrue(section.contains("1024×1024")&&section.contains("P2P")&&section.contains("Kuudra"));
        }
    }
    @Test void release153IncludesMapPerformanceKuudraAndUpdatePreferenceSources() throws Exception {
        try (var zip = new ZipFile(System.getProperty("customcosmetics.fullSourceZip"))) {
            String prefix = "CustomCosmetics-" + System.getProperty("customcosmetics.version") + "/";
            for (String name : new String[]{
                    "src/main/java/com/customcosmetics/features/map/RoomRestorer.java",
                    "src/main/java/com/customcosmetics/features/map/RoomSaveQueue.java",
                    "src/main/java/com/customcosmetics/features/map/MapRegionFill.java",
                    "src/main/java/com/customcosmetics/features/map/MapWork.java",
                    "src/test/java/com/customcosmetics/features/map/MapDeltaTest.java",
                    "src/test/java/com/customcosmetics/update/GitHubUpdateCheckTest.java",
                    "src/gametest/java/com/customcosmetics/gametest/KuudraMapGameTest.java",
                    "src/gametest/java/com/customcosmetics/gametest/MapEditPerformanceGameTest.java"}) assertBundled(zip, prefix + name, name);
        }
        String build = Files.readString(ROOT.resolve("build.gradle"));
        for (String profile : new String[]{"map_edit_perf_only", "kuudra_game_only"})
            assertTrue(build.contains("gradleProperty('" + profile + "')"), profile);
    }
    @Test void release141IncludesDamageAndSharingInputsInTheClientSnapshot() throws Exception {
        try (var zip = new ZipFile(System.getProperty("customcosmetics.fullSourceZip"))) {
            String prefix = "CustomCosmetics-" + System.getProperty("customcosmetics.version") + "/";
            for (String name : new String[]{"src/main/java/com/customcosmetics/features/damage/DamageSplash.java",
                    "src/main/java/com/customcosmetics/compatibility/ChamsRenderAccess.java",
                    "src/main/java/com/customcosmetics/features/party/TabNametags.java",
                    "src/gametest/java/com/customcosmetics/gametest/ChamsGameTest.java"})
                assertNotNull(zip.getEntry(prefix + name), name);
            assertTrue(zip.stream().noneMatch(entry -> entry.getName().startsWith(prefix + "server/")
                || entry.getName().startsWith(prefix + "ops/")));
        }
    }
    @Test void beta8IncludesBuildableVisualMapEditingSourcesAndTests() throws Exception {
        try (var zip = new ZipFile(System.getProperty("customcosmetics.fullSourceZip"))) {
            String prefix = "CustomCosmetics-" + System.getProperty("customcosmetics.version") + "/";
            for (String name : new String[]{
                    "src/main/java/com/customcosmetics/features/map/MaterialReplacement.java",
                    "src/test/java/com/customcosmetics/features/map/DenseReplacementTest.java",
                    "src/main/java/com/customcosmetics/features/map/GhostIndex.java",
                    "src/test/java/com/customcosmetics/features/map/GhostIndexTest.java",
                    "src/main/java/com/customcosmetics/features/map/MaterialReplacementPlan.java",
                    "src/main/java/com/customcosmetics/features/map/Freecam.java",
                    "src/main/java/com/customcosmetics/features/map/FmeImport.java",
                    "src/main/java/com/customcosmetics/gui/MapReplaceScreen.java",
                    "src/main/resources/assets/customcosmetics/map/rooms.json",
                    "src/test/java/com/customcosmetics/features/map/MaterialReplacementPlanTest.java",
                    "src/gametest/java/com/customcosmetics/gametest/MapReplacementGameTest.java",
                    "src/gametest/java/com/customcosmetics/gametest/FmeEditorGameTest.java"})
                assertBundled(zip, prefix + name, name);
        }
        String build = Files.readString(ROOT.resolve("build.gradle"));
        for (String profile : new String[]{"skin_game_only", "fme_game_only", "freecam_game_only", "map_replace_game_only"})
            assertTrue(build.contains("gradleProperty('" + profile + "')"), profile);
        try (var files = Files.walk(ROOT.resolve("src/test"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList())
                assertFalse(Files.readString(file).contains("\"server/" + "test/"), file.toString());
        }
    }
    @Test void publisherContactIsConsistentInReadmeNoticeAndDistributedMetadata() throws Exception {
        assertTrue(Files.readString(ROOT.resolve("README.md")).contains("https://qm.qq.com/q/Km3ScGpvga"));
        for(String name:new String[]{"README.md","NOTICE","src/main/resources/fabric.mod.json"}){
            String text=Files.readString(ROOT.resolve(name));
            assertTrue(text.contains("tory@toryhi.com"),name);
            assertFalse(text.contains("toryyyds@gmail.com"),name);
        }
        try(var zip=new ZipFile(System.getProperty("customcosmetics.binaryJar"))){
            var metadata=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("fabric.mod.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("tory@toryhi.com",metadata.getAsJsonObject("contact").get("email").getAsString());
        }
    }
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
            assertBundled(jar, "LICENSES/Odin-BSD-3-Clause.txt", "LICENSES/Odin-BSD-3-Clause.txt");
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
            assertBundled(jar, "LICENSES/Odin-BSD-3-Clause.txt", "LICENSES/Odin-BSD-3-Clause.txt");
        }
    }

    @Test
    void fullSourceContainsBuildAndInstallationInputsWithoutRuntimeData() throws Exception {
        try (var zip = new ZipFile(System.getProperty("customcosmetics.fullSourceZip"))) {
            String prefix = "CustomCosmetics-" + System.getProperty("customcosmetics.version") + "/";
            for (String file : new String[] { ".gitignore", ".gitattributes", "LICENSE", "NOTICE", "LICENSES/Oklab-MIT.txt",
                "LICENSES/Apache-2.0.txt", "LICENSES/SwordBlocking-MIT.txt", "build.gradle", "settings.gradle", "gradle.properties",
                "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
                "gradle/wrapper/gradle-wrapper.properties", "src/main/resources/fabric.mod.json",
                "src/main/java/com/customcosmetics/utils/ColorGradientUtils.java",
                "src/test/java/com/customcosmetics/distribution/LicensePackagingTest.java",
                "src/gametest/java/com/customcosmetics/gametest/BlockingGameTest.java",
                "src/gametest/java/com/customcosmetics/gametest/SettingsGuiGameTest.java",
                "src/main/java/com/customcosmetics/config/RemoteServerConfig.java",
                "src/main/java/com/customcosmetics/gui/ScreenSession.java",
                "src/main/java/com/customcosmetics/gui/NicknameSegmentsScreen.java",
                "src/main/java/com/customcosmetics/features/nickname/NicknameSegments.java",
                "src/test/java/com/customcosmetics/features/nickname/NicknameSegmentsTest.java",
                "src/main/java/com/customcosmetics/features/cape/CapeImages.java",
                "src/test/java/com/customcosmetics/features/cape/CapeImagesTest.java",
                "src/main/java/com/customcosmetics/sync/CapeRequestPolicy.java",
                "src/test/java/com/customcosmetics/sync/CapeRequestPolicyTest.java",
                "src/gametest/java/com/customcosmetics/gametest/mixin/CapePreviewProbeMixin.java",
                "src/main/java/com/customcosmetics/features/playersize/PlayerSize.java",
                "src/main/java/com/customcosmetics/features/playersize/PlayerSizeSettings.java",
                "src/test/java/com/customcosmetics/features/playersize/PlayerSizeSettingsTest.java",
                "src/gametest/java/com/customcosmetics/gametest/PlayerSizeGameTest.java",
                "LICENSES/Odin-BSD-3-Clause.txt",
                "src/gametest/resources/fabric.mod.json", "src/gametest/resources/customcosmetics-test.mixins.json",
                "tools/update_weapon_catalog.py", "tools/test_update_weapon_catalog.py",
                "src/main/java/com/customcosmetics/features/accessories/AccessoryLayer.java",
                "src/main/java/com/customcosmetics/features/accessories/AccessoryModelFit.java",
                "src/main/java/com/customcosmetics/features/accessories/AccessoryPlacement.java",
                "src/main/java/com/customcosmetics/gui/WardrobeScreen.java",
                "src/main/java/com/customcosmetics/features/cape/CapeVisibility.java",
                "src/test/java/com/customcosmetics/features/cape/CapePreviewSelectionTest.java",
                "src/gametest/java/com/customcosmetics/gametest/AccessoriesGameTest.java",
                "src/gametest/java/com/customcosmetics/gametest/mixin/AccessoryMotionPreviewMixin.java",
                "src/main/resources/assets/customcosmetics/accessories/catalog.json",
                "src/main/resources/assets/customcosmetics/textures/accessories/materials.png",
                "src/main/resources/assets/customcosmetics/textures/accessories/detailed-materials.png",
                "tools/generate-accessory-materials.py", "tools/test_accessory_materials.py",
                "src/main/java/com/customcosmetics/compatibility/ModernUiNicknameText.java",
                "src/main/java/com/customcosmetics/mixin/ModernUiNicknameMixin.java",
                "src/main/java/com/customcosmetics/features/nickname/NicknameText.java",
                "src/main/java/com/customcosmetics/features/nickname/ProcessedNicknameText.java",
                "src/test/java/com/customcosmetics/compatibility/ModernUiNicknameTextTest.java",
                "src/gametest/java/com/customcosmetics/gametest/ModernUiNicknameGameTest.java",
                "src/gametest/java/com/customcosmetics/gametest/mixin/ModernUiNicknameProbeMixin.java",
                "src/main/java/com/customcosmetics/sync/RealtimeClient.java",
                "src/main/java/com/customcosmetics/sync/RealtimeConnections.java",
                "src/main/java/com/customcosmetics/sync/PartyPeerClient.java",
                "src/main/java/com/customcosmetics/features/party/PartyTracer.java",
                "src/test/java/com/customcosmetics/sync/RealtimeConnectionsTest.java",
                "src/gametest/java/com/customcosmetics/gametest/PartyGameTest.java",
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
        assertTrue(readme.contains("## English") && readme.contains("支持玩家间同步") && readme.contains("A Fabric mod"));
        assertTrue(readme.contains("支持 26.1～26.3 Fabric"));
        assertTrue(readme.contains("Supports Minecraft 26.1–26.3 on Fabric"));
        assertFalse(readme.contains("26.1.x / 26.2 / 26.3"));
        assertFalse(readme.contains("/docs/") || readme.contains("SOURCE-DELIVERY.md"));
        String changes = Files.readString(ROOT.resolve("CHANGELOG.md"));
        assertTrue(changes.contains("## " + System.getProperty("customcosmetics.version")));
        assertTrue(changes.contains("New Feature: Party ESP and IRC For Party Members."));
        assertTrue(changes.contains("Sync: Improve latency in mainland China."));
        assertFalse(changes.substring(changes.indexOf("## 1.3.0"),changes.indexOf("## 1.2.1")).contains("P2P"));
        assertTrue(changes.contains("New Feature: Accessories."));
        assertTrue(changes.contains("Cape: Fix preview after restarting the client."));
        assertTrue(changes.contains("NickTweaks: Add segmented gradients."));
        assertTrue(changes.contains("Cape: Support high-resolution atlases up to 2 MiB."));
        assertTrue(changes.contains("Cape: Fix preview and slow uploads."));
        assertTrue(changes.contains("Player Size: Add synced X/Y/Z scaling."));
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
        assertFalse(Files.exists(ROOT.resolve("docs")), "Internal documents do not belong in the public checkout");
        assertFalse(Files.readString(ROOT.resolve("README.md")).contains("docs/"));
        assertFalse(Files.exists(ROOT.resolve("SOURCE-DELIVERY.md")));
        assertFalse(Files.exists(ROOT.resolve("server")));
        try (var zip = new ZipFile(System.getProperty("customcosmetics.fullSourceZip"))) {
            String prefix = "CustomCosmetics-" + System.getProperty("customcosmetics.version") + "/";
            for (String type : new String[]{"UdpPresenceClient", "WebSocketPresenceClient"})
                assertNotNull(zip.getEntry(prefix + "src/main/java/com/customcosmetics/sync/" + type + ".java"));
            assertTrue(zip.stream().noneMatch(entry -> entry.getName().startsWith(prefix + "server/")
                || entry.getName().startsWith(prefix + "docs/") || entry.getName().startsWith(prefix + "ops/")));
            assertTrue(zip.stream().filter(entry -> entry.getName().endsWith(".md"))
                .allMatch(entry -> entry.getName().equals(prefix + "README.md") || entry.getName().equals(prefix + "CHANGELOG.md")));
        }
        assertFalse(Files.readString(ROOT.resolve("NOTICE")).contains("docs/"));
    }

    @Test void latestChangelogIsBriefAndEnglish() throws Exception {
        String changes=Files.readString(ROOT.resolve("CHANGELOG.md")).replace("\r\n","\n");
        int start=changes.indexOf("## "+System.getProperty("customcosmetics.version"));
        assertTrue(start>=0);int end=changes.indexOf("\n## ",start+3);
        String entry=changes.substring(start,end<0?changes.length():end);
        assertTrue(entry.chars().allMatch(c->c<128), "Use English release notes");
        assertTrue(entry.length()<500, "Keep release notes brief");
        assertTrue(entry.lines().filter(line->line.startsWith("- ")).count()<=4);
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
