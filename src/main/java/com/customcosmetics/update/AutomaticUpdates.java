// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.update;
import com.google.gson.*;
import com.customcosmetics.config.CosmeticsConfigDir;
import com.customcosmetics.sync.RemoteAccess;
import net.fabricmc.loader.api.*;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipFile;
/** Official stable releases only. All disk/network work is explicitly requested and off the client thread. */
public final class AutomaticUpdates {
    public record Asset(String version,String name,URI url,long size,String sha256){}
    public record Prepared(Path jar,Path installed,Path directory,String oldHash,String newHash){}
    private static volatile Process armed;
    public static void cancelArmed(){var helper=armed;armed=null;if(helper!=null&&helper.isAlive())helper.destroy();}
    private static final long LIMIT=64L*1024*1024;
    private AutomaticUpdates(){}
    public static Asset asset(String json,String installed,String minecraft)throws IOException{
        try{
            String version=GitHubUpdateCheck.newerRelease(json,installed).orElseThrow();var release=JsonParser.parseString(json).getAsJsonObject();
            var assets=new ArrayList<Asset>();
            for(var raw:release.getAsJsonArray("assets")){
                var a=raw.getAsJsonObject();String name=a.get("name").getAsString(),lower=name.toLowerCase(Locale.ROOT),digest=a.has("digest")&&!a.get("digest").isJsonNull()?a.get("digest").getAsString():"";
                long size=a.get("size").getAsLong();URI url=URI.create(a.get("browser_download_url").getAsString());
                if(!lower.startsWith("customcosmetics-")||!lower.endsWith(".jar")||lower.contains("sources")||lower.contains("javadoc")||size<=0||size>LIMIT||!digest.matches("sha256:[0-9a-f]{64}"))continue;
                if(!"https".equals(url.getScheme())||!"github.com".equalsIgnoreCase(url.getHost())||url.getUserInfo()!=null||!url.getPath().startsWith("/WorksByAI/CustomCosmetics/releases/download/"))continue;
                assets.add(new Asset(version,name,url,size,digest.substring(7)));
            }
            assets.sort(Comparator.comparingInt(a->a.name().contains(minecraft)?0:1));if(assets.isEmpty())throw new IOException("No verifiable release asset");return assets.getFirst();
        }catch(RuntimeException malformed){throw new IOException("No compatible stable release",malformed);}
    }
    private static HttpURLConnection connect(URI uri)throws IOException{
        if(!RemoteAccess.online())throw new IOException("Offline");
        var c=RemoteAccess.manage((HttpURLConnection)uri.toURL().openConnection());c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("User-Agent","CustomCosmetics-Updater");return c;
    }
    public static CompletableFuture<Prepared> download(String installed){return CompletableFuture.supplyAsync(()->{
        try{
            var loader=FabricLoader.getInstance();if(loader.isDevelopmentEnvironment())throw new IOException("Development installation");
            var paths=loader.getModContainer("customcosmetics").orElseThrow().getOrigin().getPaths();
            if(paths.size()!=1||!Files.isRegularFile(paths.getFirst())||!paths.getFirst().getFileName().toString().endsWith(".jar"))throw new IOException("Not an installed JAR");
            Path current=paths.getFirst().toRealPath();if(!Files.isWritable(current)||!Files.isWritable(current.getParent()))throw new IOException("Installation is read-only");String oldHash=UpdateInstaller.digest(current),json;
            var connection=connect(GitHubUpdateCheck.API);connection.setRequestProperty("Accept","application/vnd.github+json");
            try{if(connection.getResponseCode()!=200)throw new IOException("Release unavailable");try(var in=connection.getInputStream()){byte[] bytes=in.readNBytes(262145);if(bytes.length>262144)throw new IOException("Release metadata too large");json=new String(bytes,StandardCharsets.UTF_8);}}finally{connection.disconnect();}
            var asset=asset(json,installed,loader.getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString());
            Path directory=CosmeticsConfigDir.getCosmeticsConfigDir().resolve("updates").resolve(asset.version());Files.createDirectories(directory);Path jar=directory.resolve("verified-update.jar"),partial=directory.resolve("download.part");
            URI uri=asset.url();boolean done=false;
            try{
                for(int redirects=0;redirects<6;redirects++){
                    if(!trustedDownload(uri))throw new IOException("Untrusted redirect");var download=connect(uri);
                    try{int code=download.getResponseCode();if(code==301||code==302||code==303||code==307||code==308){uri=uri.resolve(download.getHeaderField("Location"));continue;}if(code!=200)throw new IOException("Download unavailable");
                        try(var input=download.getInputStream();var out=Files.newOutputStream(partial)){byte[] bytes=new byte[65536];long count=0;for(int n;(n=input.read(bytes))!=-1;){count+=n;if(count>asset.size()||count>LIMIT)throw new IOException("Download too large");out.write(bytes,0,n);}if(count!=asset.size())throw new IOException("Incomplete download");}
                        done=true;break;
                    }finally{download.disconnect();}
                }
                if(!done||!UpdateInstaller.digest(partial).equals(asset.sha256()))throw new IOException("Download checksum mismatch");
                validate(partial,asset.version(),id->loader.getModContainer(id).map(m->m.getMetadata().getVersion().getFriendlyString()).orElse(null));
                Files.move(partial,jar,StandardCopyOption.REPLACE_EXISTING);return new Prepared(jar,current,directory,oldHash,asset.sha256());
            }finally{Files.deleteIfExists(partial);}
        }catch(Exception failure){throw new CompletionException(new IOException("Automatic update could not be prepared. The installed mod is unchanged."));}
    });}
    public static boolean trustedDownload(URI uri){return "https".equals(uri.getScheme())&&(uri.getPort()==-1||uri.getPort()==443)&&uri.getUserInfo()==null&&Set.of("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com").contains(uri.getHost());}
    public static void validate(Path jar,String expected,java.util.function.Function<String,String> installed)throws IOException{
        try(var zip=new ZipFile(jar.toFile())){
            var entry=zip.getEntry("fabric.mod.json");if(entry==null)throw new IOException("Not a Fabric mod");byte[] bytes;try(var in=zip.getInputStream(entry)){bytes=in.readNBytes(65537);}if(bytes.length>65536)throw new IOException("Invalid metadata");
            var metadata=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!"customcosmetics".equals(metadata.get("id").getAsString())||SemanticVersion.parse(metadata.get("version").getAsString()).compareTo(SemanticVersion.parse(expected))!=0)throw new IOException("Wrong mod or version");
            for(var dependency:metadata.getAsJsonObject("depends").entrySet()){
                String current=installed.apply(dependency.getKey());if(current==null)throw new IOException("Missing dependency");
                var rules=new ArrayList<String>();if(dependency.getValue().isJsonArray())for(var r:dependency.getValue().getAsJsonArray())rules.add(r.getAsString());else rules.add(dependency.getValue().getAsString());
                boolean matched=false;for(String rule:rules)if(VersionPredicate.parse(rule).test(Version.parse(current)))matched=true;if(!matched)throw new IOException("Incompatible dependency");
            }
        }catch(RuntimeException|VersionParsingException malformed){throw new IOException("Incompatible update metadata",malformed);}
    }
    /** Return false if launcher arguments cannot be recovered; the verified update still applies on exit. */
    public static boolean arm(Prepared prepared)throws IOException{
        List<String> restart;try{restart=RestartCommand.capture();}catch(Exception|LinkageError unavailable){restart=List.of();}
        String resource=UpdateInstaller.class.getName().replace('.','/')+".class";Path helper=prepared.directory.resolve("helper"),file=helper.resolve(resource);Files.createDirectories(file.getParent());
        try(var in=UpdateInstaller.class.getClassLoader().getResourceAsStream(resource)){if(in==null)throw new IOException("Missing updater helper");Files.copy(in,file,StandardCopyOption.REPLACE_EXISTING);}
        boolean windows=System.getProperty("os.name","").startsWith("Windows");Path java=Path.of(System.getProperty("java.home"),"bin",windows?"javaw.exe":"java");
        var process=new ProcessBuilder(java.toString(),"-cp",helper.toString(),UpdateInstaller.class.getName()).directory(Path.of("").toAbsolutePath().toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try(var out=new DataOutputStream(process.getOutputStream())){
            out.writeInt(0x43435550);out.writeLong(ProcessHandle.current().pid());out.writeUTF(prepared.jar.toString());out.writeUTF(prepared.installed.toString());out.writeUTF(prepared.directory.resolve("previous.jar.backup").toString());out.writeUTF(prepared.directory.resolve("update.status").toString());out.writeUTF(prepared.oldHash);out.writeUTF(prepared.newHash);out.writeUTF(Path.of("").toAbsolutePath().toString());out.writeInt(restart.size());for(String arg:restart)out.writeUTF(arg);
        }
        try{String ready=CompletableFuture.supplyAsync(()->{try{return new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8)).readLine();}catch(IOException error){return null;}}).get(15,TimeUnit.SECONDS);if(!"READY".equals(ready))throw new IOException("Update helper unavailable");}
        catch(Exception failure){process.destroy();throw new IOException("Update helper unavailable");}armed=process;return !restart.isEmpty();
    }
}
