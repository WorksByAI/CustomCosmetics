// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.CosmeticsConfigDir;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Never modifies inventory stacks or contacts either the game or cosmetics server. */
public final class ItemCosmetics {
    private static final Map<CustomData, String> IDENTITIES = new IdentityHashMap<>();
    private static final Map<ItemStack, Identifier> PREVIEWS = new WeakHashMap<>();
    private static final Set<ItemStack> VALIDATING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Validation, Boolean> VALIDATED = new LinkedHashMap<>();
    private static final Map<ItemStack, StackValidation> STACK_VALIDATED = new WeakHashMap<>();
    private static final LinkedHashMap<String, Identifier> MODEL_IDS = new LinkedHashMap<>(16,.75f,true);
    private record Validation(Identifier model, net.minecraft.world.item.Item item, net.minecraft.core.component.DataComponentPatch components, int count) {}
    private record StackValidation(Object revision, Validation key, boolean usable) {}
    private static Map<Identifier, ?> validatedModels = Map.of();
    public record ModelChoice(Identifier id, String label, ItemStack icon) {}
    private static ItemAppearanceStore store;
    private static long lastSave;
    private static java.util.concurrent.CompletableFuture<Void> observedSave;
    private ItemCosmetics() {}
    public static void init() {
        store = new ItemAppearanceStore(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("item-appearances.json"));
        try { store.load(); }
        catch (java.io.IOException error) { CustomCosmeticsClient.LOGGER.warn("Could not load local item appearances: {}", error.getMessage()); }
        HeadSkins.requestCatalog();
    }
    public static ItemAppearanceStore store() { return store; }
    public static boolean modelsActive(){return com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ITEMS)&&(!PREVIEWS.isEmpty()||store!=null&&store.enabled()&&(store.hasModels()||store.hasHeadTextures()));}
    public static boolean modelOverrideActive(ItemStack stack){
        if(!modelsActive())return false;
        if(PREVIEWS.containsKey(stack))return true;
        if(store==null||!store.enabled())return false;
        var appearance=store.getCanonical(appearanceKey(stack));
        return !appearance.model().isEmpty()||!appearance.headTexture().isEmpty();
    }
    public static String uuid(CompoundTag data) {
        String direct = ItemAppearanceStore.key(data.getStringOr("uuid", ""));
        if (!direct.isEmpty()) return direct;
        return data.getCompound("ExtraAttributes").map(extra -> ItemAppearanceStore.key(extra.getStringOr("uuid", ""))).orElse("");
    }
    public static String uuid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        synchronized (IDENTITIES) {
            if (IDENTITIES.size() >= 1024) IDENTITIES.clear();
            return IDENTITIES.computeIfAbsent(data, value -> uuid(value.copyTag()));
        }
    }
    /** Resolve display settings without presenting a recovered UUID as real server metadata. */
    public static String appearanceKey(ItemStack stack){
        String uuid=uuid(stack);
        return uuid.isEmpty()?ArmorItemIdentity.localAppearanceKey(stack):uuid;
    }
    public static List<Identifier> models() {
        return modelChoices(new ItemStack(Items.PAPER)).stream().map(ModelChoice::id).toList();
    }
    public static boolean hasModel(Identifier id) { return id != null && modelMap().containsKey(id); }
    static Identifier modelId(String value) {
        if (value == null || value.isEmpty()) return null;
        Identifier cached = MODEL_IDS.get(value);
        if (cached != null) return cached;
        Identifier parsed = Identifier.tryParse(value);
        if (parsed != null) {
            MODEL_IDS.put(value, parsed);
            if (MODEL_IDS.size() > ItemAppearanceStore.MAX_ENTRIES) MODEL_IDS.pollFirstEntry();
        }
        return parsed;
    }
    public static Object catalogIdentity() { return modelMap(); }
    public static Object componentIdentity(ItemStack stack) {
        var components=stack.getComponents();
        return components instanceof ItemComponentIdentity identity ? identity.customcosmetics$componentIdentity() : null;
    }
    private static Map<Identifier, ?> modelMap() {
        var manager = Minecraft.getInstance().getModelManager();
        if (manager instanceof ItemModelCatalog catalog && catalog.customcosmetics$itemModels() != null)
            return catalog.customcosmetics$itemModels();
        return Map.of();
    }
    public static List<ModelChoice> modelChoices(ItemStack source) {
        var vanilla = new HashMap<Identifier, ItemStack>();
        for (var item : BuiltInRegistries.ITEM) {
            var icon = item.getDefaultInstance();
            var id = icon.get(DataComponents.ITEM_MODEL);
            if (id != null && !icon.isEmpty()) vanilla.putIfAbsent(id, icon);
        }
        var result = new ArrayList<ModelChoice>();
        for (var id : modelMap().keySet()) {
            var icon = preview(vanilla.getOrDefault(id, source), id);
            if (renderable(icon, id)) result.add(new ModelChoice(id,
                vanilla.containsKey(id) ? vanilla.get(id).getHoverName().getString() : id.getPath().replace('_', ' '), icon));
        }
        result.sort(Comparator.<ModelChoice, Boolean>comparing(value -> !value.id().getNamespace().equals("minecraft"))
            .thenComparing(value -> value.id().toString()));
        return List.copyOf(result);
    }
    /** A temporary display copy. Catharsis may resolve the real item, but must not replace this explicit preview. */
    public static ItemStack preview(ItemStack source, Identifier id) {
        var copy = source.copy();
        var data = copy.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            var tag = data.copyTag(); tag.remove("uuid");
            tag.getCompound("ExtraAttributes").ifPresent(extra -> extra.remove("uuid"));
            copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        if (id != null) { copy.set(DataComponents.ITEM_MODEL, id); PREVIEWS.put(copy, id); }
        return copy;
    }
    public static boolean renderable(ItemStack source, Identifier id) {
        if (!hasModel(id) || source == null || source.isEmpty()) return false;
        if (!VALIDATING.add(source)) return false;
        var client = Minecraft.getInstance();
        var state = new ItemStackRenderState();
        try {
            client.getModelManager().getItemModel(id).update(state, source, client.getItemModelResolver(),
                ItemDisplayContext.GUI, client.level, client.player, 0);
            return ItemModelVisibility.usable(state);
        } catch (RuntimeException error) { return false; }
        finally { VALIDATING.remove(source); }
    }
    private static boolean validated(ItemStack source, Identifier id) {
        var models = modelMap();
        if (models != validatedModels) { VALIDATED.clear(); STACK_VALIDATED.clear(); validatedModels = models; }
        Object revision=componentIdentity(source);
        StackValidation cached=STACK_VALIDATED.get(source);
        if(cached!=null&&cached.key().model().equals(id)&&cached.key().item()==source.getItem()
                &&cached.key().count()==source.getCount()&&revision!=null&&cached.revision()==revision)return cached.usable();
        if (VALIDATED.size() >= 256) VALIDATED.clear();
        if (STACK_VALIDATED.size() >= 256) STACK_VALIDATED.clear();
        var key=new Validation(id,source.getItem(),source.getComponentsPatch(),source.getCount());
        boolean usable=VALIDATED.computeIfAbsent(key, ignored -> renderable(source, id));
        STACK_VALIDATED.put(source,new StackValidation(revision,key,usable));return usable;
    }
    public static Identifier model(ItemStack stack, Identifier original) {
        if(!modelsActive())return original;
        if (VALIDATING.contains(stack)) return original;
        var preview = PREVIEWS.get(stack);
        if (preview != null) return preview;
        if (store == null || !store.enabled()) return original;
        var appearance = store.getCanonical(appearanceKey(stack));
        if (!appearance.headTexture().isEmpty()) return HeadSkins.MODEL;
        String raw = appearance.model();
        Identifier selected = modelId(raw);
        return hasModel(selected) && validated(stack, selected) ? selected : original;
    }
    public static Component name(ItemStack stack, Component original) {
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ITEMS))return original;
        if(store==null||!store.hasNames())return original;
        // The integrated server shares ItemStack classes but must retain its original names.
        if (store == null || !store.enabled() || !Minecraft.getInstance().isSameThread()) return original;
        String raw = store.getCanonical(appearanceKey(stack)).name();
        return raw.isEmpty() ? original : ItemNameText.cached(raw);
    }
    /** Render-side visibility only; keep real enchantments and component data intact. */
    public static boolean foil(ItemStack stack, boolean original) {
        if (!original || !com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ITEMS)
                || store == null || !store.enabled() || !store.hasHiddenGlints()) return original;
        if (!Minecraft.getInstance().isSameThread()) return original;
        return !store.getCanonical(appearanceKey(stack)).hideGlint();
    }
    public static void tick() {
        ArmorTrace.tick(Minecraft.getInstance());
        ArmorItemIdentity.tick();
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ITEMS))return;
        HeadSkins.tick();
        if (store != null && store.dirty() && System.currentTimeMillis() - lastSave > 750) save();
    }
    public static boolean save() {
        if (store == null) return false;
        lastSave = System.currentTimeMillis();
        var saving=store.requestSave();
        if(saving!=observedSave){
            observedSave=saving;saving.whenComplete((ignored,error)->{
                if(error!=null){CustomCosmeticsClient.LOGGER.warn("Could not save local item appearances",error);
                    com.customcosmetics.features.cape.CapeCosmetics.notice("Could not save item appearances. Your changes are still in memory; try saving again.");}
            });
        }
        return !saving.isCompletedExceptionally();
    }
    /** Shutdown/readback barrier; ordinary edits only enqueue immutable snapshots. */
    public static boolean flush() {
        if(store==null)return true;
        try { store.save(); return true; }
        catch (java.io.IOException error) { CustomCosmeticsClient.LOGGER.warn("Could not save local item appearances: {}", error.getMessage()); return false; }
    }
}
