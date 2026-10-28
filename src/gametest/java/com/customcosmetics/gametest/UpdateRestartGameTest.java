// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;
import com.customcosmetics.update.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.zip.*;
/** Explicit opt-in, isolated real-Minecraft restart probe. Never touches a user's installed JAR. */
final class UpdateRestartGameTest {
    static void run(ClientGameTestContext context)throws Exception{
        var mod=FabricLoader.getInstance().getModContainer("customcosmetics").orElseThrow();Path current=mod.getOrigin().getPaths().getFirst().toRealPath();
        Path build=Path.of(System.getProperty("customcosmetics.testProjectDir")).resolve("build").toRealPath();
        if(!current.startsWith(build)||!current.getFileName().toString().equals("client-under-test.jar"))throw new AssertionError("Restart probe requires a dedicated build/ JAR");
        Path directory=context.computeOnClient(client->client.gameDirectory.toPath()).resolve("restart-probe");Files.createDirectories(directory);
        String version=mod.getMetadata().getVersion().getFriendlyString();
        if(version.endsWith("+restart-probe")){
            if(!UpdateInstaller.digest(current).equals(Files.readString(directory.resolve("expected.sha256"))))throw new AssertionError("Restart loaded the wrong JAR");
            Files.writeString(directory.resolve("success"),version);context.takeScreenshot("restart-new-mod-loaded");return;
        }
        Path candidate=directory.resolve("candidate.jar");
        try(var old=new ZipFile(current.toFile());var out=new ZipOutputStream(Files.newOutputStream(candidate))){
            for(var entries=old.entries();entries.hasMoreElements();){var entry=entries.nextElement();out.putNextEntry(new ZipEntry(entry.getName()));
                byte[] bytes;try(var in=old.getInputStream(entry)){bytes=in.readAllBytes();}
                if(entry.getName().equals("fabric.mod.json")){var metadata=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();metadata.addProperty("version",version+"+restart-probe");bytes=metadata.toString().getBytes(StandardCharsets.UTF_8);}
                out.write(bytes);out.closeEntry();
            }
        }
        String hash=UpdateInstaller.digest(candidate);Files.writeString(directory.resolve("expected.sha256"),hash);
        var prepared=new AutomaticUpdates.Prepared(candidate,current,directory,UpdateInstaller.digest(current),hash);
        var ready=CompletableFuture.supplyAsync(()->{try{return AutomaticUpdates.arm(prepared);}catch(Exception error){throw new CompletionException(error);}});
        context.waitFor(client->ready.isDone(),600);if(!ready.join())throw new AssertionError("Native launch arguments could not be recovered");
        Files.writeString(directory.resolve("armed"),"ready");
        // The test runner exits the first game normally. The helper waits, replaces and starts a second game.
    }
}
