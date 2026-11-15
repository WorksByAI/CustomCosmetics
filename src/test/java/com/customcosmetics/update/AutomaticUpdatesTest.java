package com.customcosmetics.update;
import com.customcosmetics.client.ClientEdition;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class AutomaticUpdatesTest {
    private static final String HASH = "sha256:" + "a".repeat(64);
    private static final String DOWNLOAD = "https://github.com/WorksByAI/CustomCosmetics/releases/download/v2.1.0/";

    @Test void eachEditionSelectsOnlyItsOwnAsset() throws Exception {
        String legit = ClientEdition.LEGIT.assetName("2.1.0");
        String cheating = ClientEdition.CHEATING.assetName("2.1.0");
        String legitAsset = asset(legit, DOWNLOAD + legit, HASH);
        String cheatingAsset = asset(cheating, DOWNLOAD + cheating, HASH);
        String both = release(cheatingAsset + "," + legitAsset);
        assertEquals(legit, AutomaticUpdates.asset(both, "2.0.1-nofont", ClientEdition.LEGIT).name());
        assertEquals(cheating, AutomaticUpdates.asset(both, "2.0.1", ClientEdition.CHEATING).name());
        assertThrows(IOException.class, () -> AutomaticUpdates.asset(release(cheatingAsset), "2.0.1", ClientEdition.LEGIT));
        assertThrows(IOException.class, () -> AutomaticUpdates.asset(release(legitAsset), "2.0.1", ClientEdition.CHEATING));
        String legacy = "CustomSkyBlock-26.1-26.3-2.1.0.jar";
        assertThrows(IOException.class, () -> AutomaticUpdates.asset(release(asset(legacy, DOWNLOAD + legacy, HASH)), "2.0.1", ClientEdition.LEGIT));
        assertTrue(GitHubUpdateCheck.newerRelease(release(legitAsset).replace("v2.1.0","v2.0.1"),"2.0.1-nofont+mc26.1-26.3").isEmpty());
        assertTrue(GitHubUpdateCheck.newerRelease(release(legitAsset).replace("v2.1.0","v2.0.1-nofont"),"2.0.1").isEmpty());
    }
    @TempDir Path directory;
    private static String release(String asset){return "{\"draft\":false,\"prerelease\":false,\"tag_name\":\"v2.1.0\",\"assets\":["+asset+"]}";}
    private static String asset(String name, String url, String digest){return "{\"name\":\""+name+"\",\"size\":2048,\"browser_download_url\":\""+url+"\",\"digest\":\""+digest+"\"}";}
    @Test void onlyOfficialVerifiableStableClientAssetsAreSelected()throws Exception{
        var edition = ClientEdition.LEGIT;
        String name = edition.assetName("2.1.0");
        String url = DOWNLOAD + name;
        assertEquals("2.1.0", AutomaticUpdates.asset(release(asset(name,url,HASH)), "2.0.0", edition).version());
        for (String unsafe : List.of(url.replace("https", "http"), url.replace("github.com", "example.test"),
                url.replace("WorksByAI", "Other"), url.replace("v2.1.0", "v3.0.0")))
            assertThrows(IOException.class, () -> AutomaticUpdates.asset(release(asset(name,unsafe,HASH)), "2.0.0", edition));
        assertThrows(IOException.class, () -> AutomaticUpdates.asset(release(asset(name,url,"")), "2.0.0", edition));
        assertThrows(IOException.class, () -> AutomaticUpdates.asset(release(asset(name,url,HASH)).replace("\"prerelease\":false","\"prerelease\":true"), "2.0.0", edition));
        assertFalse(AutomaticUpdates.trustedDownload(URI.create("https://github.com:8443/file")));assertFalse(AutomaticUpdates.trustedDownload(URI.create("https://github.com@example.test/file")));
    }
    private Path jar(String metadata)throws Exception{
        Path file=directory.resolve(UUID.randomUUID()+".jar");try(var zip=new ZipOutputStream(Files.newOutputStream(file))){zip.putNextEntry(new ZipEntry("fabric.mod.json"));zip.write(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}return file;
    }
    @Test void modIdentityVersionAndEveryRequiredDependencyAreChecked()throws Exception{
        String metadata="{\"id\":\"customcosmetics\",\"version\":\"2.1.0\",\"custom\":{\"customcosmetics:edition\":\"legit\"},\"depends\":{\"minecraft\":[\"~26.2\",\"~26.3\"],\"java\":\">=25\",\"fabricloader\":\">=0.19.5\"}}";
        var versions=Map.of("minecraft","26.3","java","25","fabricloader","0.19.5");
        AutomaticUpdates.validate(jar(metadata),"2.1.0",ClientEdition.LEGIT,versions::get);
        assertThrows(IOException.class, () -> AutomaticUpdates.validate(jar(metadata), "2.1.0", ClientEdition.CHEATING, versions::get));
        assertThrows(IOException.class, () -> AutomaticUpdates.validate(jar(metadata.replace("customcosmetics:edition", "legacy")), "2.1.0", ClientEdition.LEGIT, versions::get));
        for(String invalid:List.of(metadata.replace("customcosmetics","other_mod"),metadata.replace("\"2.1.0\"","\"2.1.1\""),metadata.replace(">=25",">=26"),metadata.replace("fabricloader","unknown_dependency")))
            assertThrows(IOException.class,()->AutomaticUpdates.validate(jar(invalid),"2.1.0",ClientEdition.LEGIT,versions::get));
    }
    @Test void installerRetainsTheOriginalAndRejectsChangedDownloads()throws Exception{
        Path current=directory.resolve("mod.jar"),next=directory.resolve("download.jar"),backup=directory.resolve("old/mod.jar.backup");Files.writeString(current,"old");Files.writeString(next,"new");
        String old=UpdateInstaller.digest(current),hash=UpdateInstaller.digest(next);
        assertThrows(IOException.class,()->UpdateInstaller.install(next,current,backup,old,"0".repeat(64)));assertEquals("old",Files.readString(current));
        UpdateInstaller.install(next,current,backup,old,hash);assertEquals("new",Files.readString(current));assertEquals("old",Files.readString(backup));
        Files.writeString(current,"manually changed");assertThrows(IOException.class,()->UpdateInstaller.install(next,current,backup,old,hash));assertEquals("manually changed",Files.readString(current));
    }
}
