// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.config;

import com.customcosmetics.features.blocking.BlockingRules;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Separate list preferences, with strict reads so a damaged file is never silently replaced. */
final class SwordBlockingLists {
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger("CustomCosmetics/SwordBlockingLists");
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_BYTES=2*1024*1024;
    private static final Set<String> FIELDS=Set.of("swordBlockingListMode","swordBlockingBlacklist","swordBlockingWhitelist","swordBlockingListsApplyToOthers");
    record Data(String mode,boolean applyToOthers,List<String> blacklist,List<String> whitelist){
        Data{mode=BlockingRules.Mode.parse(mode).value();blacklist=List.copyOf(blacklist);whitelist=List.copyOf(whitelist);}
    }
    private SwordBlockingLists(){}
    static boolean separate(String field){return FIELDS.contains(field);}
    static Path path(Path main){return main.resolveSibling("sword-blocking-lists.json");}
    static Data capture(){return new Data(ConfigManager.swordBlockingListMode,ConfigManager.swordBlockingListsApplyToOthers,
        Arrays.asList(BlockingRules.sanitize(ConfigManager.swordBlockingBlacklist)),Arrays.asList(BlockingRules.sanitize(ConfigManager.swordBlockingWhitelist)));}
    static void apply(Data data){
        ConfigManager.swordBlockingListMode=data.mode();ConfigManager.swordBlockingListsApplyToOthers=data.applyToOthers();
        ConfigManager.swordBlockingBlacklist=data.blacklist().toArray(String[]::new);ConfigManager.swordBlockingWhitelist=data.whitelist().toArray(String[]::new);
    }
    static Data read(Path file)throws IOException{
        try{
            if(Files.size(file)>MAX_BYTES)throw new IOException("Sword blocking list file exceeds 2 MiB");
            JsonElement parsed=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8));
            if(!parsed.isJsonObject())throw new IOException("Expected sword blocking list object");
            var root=parsed.getAsJsonObject();var version=root.get("version");
            if(version==null||!version.isJsonPrimitive()||!version.getAsJsonPrimitive().isNumber()||!version.getAsString().equals("1"))throw new IOException("Unsupported sword blocking list version");
            var mode=root.get("mode");var apply=root.get("applyToOthers");
            if(mode==null||!mode.isJsonPrimitive()||!mode.getAsJsonPrimitive().isString()||!Set.of("off","blacklist","whitelist").contains(mode.getAsString()))throw new IOException("Invalid sword blocking list mode");
            if(apply==null||!apply.isJsonPrimitive()||!apply.getAsJsonPrimitive().isBoolean())throw new IOException("Invalid applyToOthers value");
            return new Data(mode.getAsString(),apply.getAsBoolean(),entries(root,"blacklist"),entries(root,"whitelist"));
        }catch(RuntimeException error){throw new IOException("Invalid sword blocking list file",error);}
    }
    private static List<String> entries(JsonObject root,String key)throws IOException{
        var value=root.get(key);if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()>4096)throw new IOException("Invalid "+key+" list");
        var result=new TreeSet<String>();
        for(var entry:value.getAsJsonArray()){
            if(!entry.isJsonPrimitive()||!entry.getAsJsonPrimitive().isString())throw new IOException("Invalid "+key+" entry");
            String normalized=BlockingRules.normalize(entry.getAsString());if(normalized.isEmpty())throw new IOException("Invalid "+key+" item ID");result.add(normalized);
        }
        return List.copyOf(result);
    }
    static void write(Path file,Data data)throws IOException{
        if(Files.exists(file)){
            if(!Files.isRegularFile(file))throw new IOException("Sword blocking list path is not a regular file");
            try{read(file);}catch(IOException invalid){
                Path backup=file.resolveSibling("sword-blocking-lists.invalid-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".json");
                Files.copy(file,backup);
                LOGGER.error("Preserved damaged or unsupported sword blocking lists at {}; replacing the active file with recovered preferences",backup,invalid);
            }
        }
        Path parent=file.toAbsolutePath().getParent();Files.createDirectories(parent);
        var root=new JsonObject();root.addProperty("version",1);root.addProperty("mode",data.mode());root.addProperty("applyToOthers",data.applyToOthers());
        root.add("blacklist",JSON.toJsonTree(data.blacklist()));root.add("whitelist",JSON.toJsonTree(data.whitelist()));
        Path temporary=Files.createTempFile(parent,"sword-blocking-lists-",".tmp");
        try{
            Files.writeString(temporary,JSON.toJson(root),StandardCharsets.UTF_8);
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException error){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
}
