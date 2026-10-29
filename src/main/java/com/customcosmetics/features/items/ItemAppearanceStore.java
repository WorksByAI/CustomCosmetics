// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Local overrides indexed by the unique item UUID, never by the player's UUID. */
public final class ItemAppearanceStore {
    public static final int MAX_NAME = 256, MAX_ENTRIES = 1024;
    private static final Appearance ORIGINAL = new Appearance("", "");
    private static final java.util.regex.Pattern COMPACT_UUID = java.util.regex.Pattern.compile("[a-f0-9]{32}");
    private static final java.util.regex.Pattern FULL_UUID = java.util.regex.Pattern.compile("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}");
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final Executor WRITER = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task,"CustomCosmetics-item-save");thread.setDaemon(true);return thread;
    });
    public record Appearance(String name, String model, String armorModel, Integer armorColor, String headTexture, boolean hideGlint) {
        public Appearance(String name, String model) { this(name, model, "", null, ""); }
        public Appearance(String name, String model, String armorModel, Integer armorColor) { this(name, model, armorModel, armorColor, ""); }
        public Appearance(String name, String model, String armorModel, Integer armorColor, String headTexture) { this(name, model, armorModel, armorColor, headTexture, false); }
        public Appearance {
            name = name == null ? "" : name;
            model = canonicalIdentifier(model);
            armorModel = canonicalIdentifier(armorModel);
            headTexture = HeadTexture.normalize(headTexture);
            if (name.length() > MAX_NAME || name.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Use at most 256 characters without control characters.");
            if (!validIdentifier(model))
                throw new IllegalArgumentException("Invalid model ID.");
            if (!validIdentifier(armorModel)) throw new IllegalArgumentException("Invalid armor model ID.");
            if (armorColor != null && (armorColor < 0 || armorColor > 0xFFFFFF))
                throw new IllegalArgumentException("Armor color must be #RRGGBB.");
        }
        public boolean empty() { return name.isEmpty() && model.isEmpty() && armorModel.isEmpty() && armorColor == null && headTexture.isEmpty() && !hideGlint; }
    }
    private static String canonicalIdentifier(String raw) {
        String id = raw == null ? "" : raw.strip();
        return id.isEmpty() || id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }
    private static boolean validIdentifier(String id) {
        if (id.isEmpty()) return true;
        if (id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9_.-][a-z0-9/._-]*")) return false;
        for (String segment : id.substring(id.indexOf(':') + 1).split("/"))
            if (segment.equals(".") || segment.equals("..")) return false;
        return true;
    }
    private final Path path;
    private final Executor writer;
    private final Map<String, Appearance> entries = new TreeMap<>();
    private boolean enabled = true;
    private volatile boolean dirty;
    private long revision, requestedRevision = -1;
    private record Snapshot(long revision,boolean enabled,Map<String,Appearance> entries) {}
    private Snapshot pending;
    private boolean writing;
    private CompletableFuture<Void> completion = CompletableFuture.completedFuture(null);
    private int models,names,armorModels,armorColors,headTextures,hiddenGlints;
    public boolean hasModels(){return models!=0;}
    public boolean hasNames(){return names!=0;}
    public boolean hasArmorModels(){return armorModels!=0;}
    public boolean hasArmorColors(){return armorColors!=0;}
    public boolean hasHeadTextures(){return headTextures!=0;}
    public boolean hasHiddenGlints(){return hiddenGlints!=0;}
    private void count(Appearance value,int change){
        if(value==null)return;
        if(!value.model().isEmpty())models+=change;
        if(!value.name().isEmpty())names+=change;
        if(!value.armorModel().isEmpty())armorModels+=change;
        if(value.armorColor()!=null)armorColors+=change;
        if(!value.headTexture().isEmpty())headTextures+=change;
        if(value.hideGlint())hiddenGlints+=change;
    }
    public ItemAppearanceStore(Path path) { this(path,WRITER); }
    ItemAppearanceStore(Path path,Executor writer) { this.path=path;this.writer=writer; }
    public static String key(String raw) {
        if (raw == null) return "";
        String value = raw.strip().toLowerCase(Locale.ROOT);
        if (COMPACT_UUID.matcher(value).matches()) value = value.substring(0,8) + "-" + value.substring(8,12) + "-"
            + value.substring(12,16) + "-" + value.substring(16,20) + "-" + value.substring(20);
        if (!FULL_UUID.matcher(value).matches()) return "";
        return UUID.fromString(value).toString();
    }
    public boolean enabled() { return enabled; }
    public synchronized void enabled(boolean value) { if (enabled != value) { enabled = value; dirty = true;revision++; } }
    public Appearance get(String uuid) { return entries.getOrDefault(key(uuid), ORIGINAL); }
    /** The renderer already normalizes identities once per immutable CustomData object. */
    Appearance getCanonical(String uuid) { return entries.getOrDefault(uuid, ORIGINAL); }
    public int size() { return entries.size(); }
    public boolean dirty() { return dirty; }
    public synchronized void put(String uuid, Appearance value) {
        String key = key(uuid);
        if (key.isEmpty()) throw new IllegalArgumentException("This item has no valid unique item UUID.");
        if (value.empty()) { var removed=entries.remove(key);if(removed!=null){count(removed,-1);dirty=true;revision++;}return; }
        if (!entries.containsKey(key) && entries.size() >= MAX_ENTRIES) throw new IllegalStateException("Too many customized items.");
        var previous=entries.put(key,value);if(!value.equals(previous)){count(previous,-1);count(value,1);dirty=true;revision++;}
    }
    public void load() throws IOException {
        while(true){
            awaitPending();
            synchronized(this){if(writing)continue;read();return;}
        }
    }
    private void read() throws IOException {
        if (!Files.exists(path)) return;
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("The item appearance file is too large.");
        try {
            var root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("version") || root.get("version").getAsInt() != 1) throw new IOException("Unsupported item appearance format.");
            Map<String, Appearance> loaded = new TreeMap<>();
            var values = root.getAsJsonObject("items");
            if (values != null) for (var entry : values.entrySet()) {
                if (loaded.size() >= MAX_ENTRIES) throw new IOException("Too many customized items.");
                String key = key(entry.getKey());
                if (key.isEmpty()) continue;
                try {
                    var data = entry.getValue().getAsJsonObject();
                    var appearance = new Appearance(data.has("name") ? data.get("name").getAsString() : "",
                        data.has("model") ? data.get("model").getAsString() : "",
                        data.has("armorModel") ? data.get("armorModel").getAsString() : "",
                        data.has("armorColor") ? data.get("armorColor").getAsInt() : null,
                        data.has("headTexture") ? data.get("headTexture").getAsString() : "",
                        data.has("hideGlint") && data.get("hideGlint").getAsBoolean());
                    if (!appearance.empty()) loaded.put(key, appearance);
                } catch (RuntimeException malformedEntry) { /* An invalid override must not break other items. */ }
            }
            boolean newEnabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
            entries.clear(); entries.putAll(loaded);models=names=armorModels=armorColors=headTextures=hiddenGlints=0;for(var value:loaded.values())count(value,1); enabled = newEnabled; dirty = false;revision++;
        } catch (RuntimeException malformed) { throw new IOException("Invalid item appearance format.", malformed); }
    }
    public void save() throws IOException {
        await(requestSave());
    }
    /** Snapshot on the client thread; serialization and atomic disk replacement run on the writer. */
    public synchronized CompletableFuture<Void> requestSave(){
        if(!dirty||writing&&requestedRevision==revision)return completion;
        pending=new Snapshot(revision,enabled,Collections.unmodifiableMap(new TreeMap<>(entries)));
        requestedRevision=revision;
        if(!writing){
            writing=true;completion=new CompletableFuture<>();
            try{writer.execute(this::writePending);}
            catch(RuntimeException error){writing=false;pending=null;completion.completeExceptionally(error);}
        }
        return completion;
    }
    private void awaitPending()throws IOException{CompletableFuture<Void> future;synchronized(this){future=completion;}await(future);}
    private static void await(CompletableFuture<Void> future)throws IOException{
        try{future.join();}catch(CompletionException error){if(error.getCause() instanceof IOException io)throw io;throw new IOException("Could not save item appearances.",error.getCause());}
    }
    private void writePending(){
        Exception failure=null;
        while(true){
            Snapshot snapshot;CompletableFuture<Void> finished=null;
            synchronized(this){
                snapshot=pending;pending=null;
                if(snapshot==null){writing=false;finished=completion;}
            }
            if(finished!=null){if(failure==null)finished.complete(null);else finished.completeExceptionally(failure);return;}
            try{
                write(snapshot);failure=null;
                synchronized(this){if(revision==snapshot.revision())dirty=false;}
            }catch(Exception error){failure=error;}
        }
    }
    private void write(Snapshot snapshot) throws IOException {
        var root = new JsonObject(); root.addProperty("version", 1); root.addProperty("enabled", snapshot.enabled());
        var items = new JsonObject();
        for (var entry : snapshot.entries().entrySet()) {
            var value = new JsonObject(); value.addProperty("name", entry.getValue().name()); value.addProperty("model", entry.getValue().model());
            if (!entry.getValue().armorModel().isEmpty()) value.addProperty("armorModel", entry.getValue().armorModel());
            if (entry.getValue().armorColor() != null) value.addProperty("armorColor", entry.getValue().armorColor());
            if (!entry.getValue().headTexture().isEmpty()) value.addProperty("headTexture", entry.getValue().headTexture());
            if (entry.getValue().hideGlint()) value.addProperty("hideGlint", true);
            items.add(entry.getKey(), value);
        }
        root.add("items", items);
        Path directory = path.toAbsolutePath().getParent(); Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "item-appearances-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root), StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
