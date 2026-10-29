package com.customcosmetics.update;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class AutomaticUpdatesTest {
    @Test void updatesPreserveFontFlavorAndTreatItAsTheSameVersion()throws Exception{
        String url="https://github.com/WorksByAI/CustomCosmetics/releases/download/v2.1.0/test.jar",hash="sha256:"+"a".repeat(64);
        String normal=asset(url,hash),noFont=normal.replace("2.1.0+mc","2.1.0-nofont+mc");
        String both=release(noFont+","+normal);
        assertFalse(AutomaticUpdates.asset(both,"2.0.1","26.3",false).name().contains("nofont"));
        assertTrue(AutomaticUpdates.asset(both,"2.0.1-nofont","26.3",true).name().contains("nofont"));
        assertThrows(IOException.class,()->AutomaticUpdates.asset(release(normal),"2.0.1","26.3",true));
        assertTrue(GitHubUpdateCheck.newerRelease(release(normal).replace("v2.1.0","v2.0.1"),"2.0.1-nofont+mc26.1-26.3").isEmpty());
        assertTrue(GitHubUpdateCheck.newerRelease(release(normal).replace("v2.1.0","v2.0.1-nofont"),"2.0.1").isEmpty());
        var empty=jar("{}");AutomaticUpdates.validateFlavor(empty,true);assertThrows(IOException.class,()->AutomaticUpdates.validateFlavor(empty,false));
    }
    @TempDir Path directory;
    private static String release(String asset){return "{\"draft\":false,\"prerelease\":false,\"tag_name\":\"v2.1.0\",\"assets\":["+asset+"]}";}
    private static String asset(String url,String digest){return "{\"name\":\"CustomCosmetics-2.1.0+mc26.1-26.3.jar\",\"size\":2048,\"browser_download_url\":\""+url+"\",\"digest\":\""+digest+"\"}";}
    @Test void onlyOfficialVerifiableStableClientAssetsAreSelected()throws Exception{
        String url="https://github.com/WorksByAI/CustomCosmetics/releases/download/v2.1.0/test.jar",hash="sha256:"+"a".repeat(64);
        assertEquals("2.1.0",AutomaticUpdates.asset(release(asset(url,hash)),"2.0.0","26.3").version());
        for(String unsafe:List.of("http://github.com/WorksByAI/CustomCosmetics/releases/download/test.jar","https://example.test/test.jar","https://github.com/Other/CustomCosmetics/releases/download/test.jar"))
            assertThrows(IOException.class,()->AutomaticUpdates.asset(release(asset(unsafe,hash)),"2.0.0","26.3"));
        assertThrows(IOException.class,()->AutomaticUpdates.asset(release(asset(url,"")),"2.0.0","26.3"));
        assertThrows(IOException.class,()->AutomaticUpdates.asset(release(asset(url,hash)).replace("\"prerelease\":false","\"prerelease\":true"),"2.0.0","26.3"));
        assertFalse(AutomaticUpdates.trustedDownload(URI.create("https://github.com:8443/file")));assertFalse(AutomaticUpdates.trustedDownload(URI.create("https://github.com@example.test/file")));
    }
    private Path jar(String metadata)throws Exception{
        Path file=directory.resolve(UUID.randomUUID()+".jar");try(var zip=new ZipOutputStream(Files.newOutputStream(file))){zip.putNextEntry(new ZipEntry("fabric.mod.json"));zip.write(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}return file;
    }
    @Test void modIdentityVersionAndEveryRequiredDependencyAreChecked()throws Exception{
        String metadata="{\"id\":\"customcosmetics\",\"version\":\"2.1.0\",\"depends\":{\"minecraft\":[\"~26.2\",\"~26.3\"],\"java\":\">=25\",\"fabricloader\":\">=0.19.5\"}}";
        var versions=Map.of("minecraft","26.3","java","25","fabricloader","0.19.5");AutomaticUpdates.validate(jar(metadata),"2.1.0",versions::get);
        for(String invalid:List.of(metadata.replace("customcosmetics","other_mod"),metadata.replace("\"2.1.0\"","\"2.1.1\""),metadata.replace(">=25",">=26"),metadata.replace("fabricloader","unknown_dependency")))
            assertThrows(IOException.class,()->AutomaticUpdates.validate(jar(invalid),"2.1.0",versions::get));
    }
    @Test void installerRetainsTheOriginalAndRejectsChangedDownloads()throws Exception{
        Path current=directory.resolve("mod.jar"),next=directory.resolve("download.jar"),backup=directory.resolve("old/mod.jar.backup");Files.writeString(current,"old");Files.writeString(next,"new");
        String old=UpdateInstaller.digest(current),hash=UpdateInstaller.digest(next);
        assertThrows(IOException.class,()->UpdateInstaller.install(next,current,backup,old,"0".repeat(64)));assertEquals("old",Files.readString(current));
        UpdateInstaller.install(next,current,backup,old,hash);assertEquals("new",Files.readString(current));assertEquals("old",Files.readString(backup));
        Files.writeString(current,"manually changed");assertThrows(IOException.class,()->UpdateInstaller.install(next,current,backup,old,hash));assertEquals("manually changed",Files.readString(current));
    }
}
