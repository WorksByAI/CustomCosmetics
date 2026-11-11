// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.pack;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.waypoint.Waypoints;
import com.customcosmetics.sync.ExperimentalAccess;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** A subset of Catharsis selectors. Unavailable data uses the pack's fallback model. */
public final class CatharsisItemProperties {
    public interface ItemData { CompoundTag ccos$tag(); }
    private CatharsisItemProperties() {}

    static boolean enabled() { return ConfigManager.catharsisItemModels && ExperimentalAccess.allowed(); }

    public enum ItemModel implements SelectItemModelProperty<net.minecraft.resources.Identifier> {
        INSTANCE;
        public static final Type<ItemModel, net.minecraft.resources.Identifier> TYPE = Type.create(MapCodec.unit(INSTANCE), net.minecraft.resources.Identifier.CODEC);
        @Override public net.minecraft.resources.Identifier get(ItemStack stack, ClientLevel level, LivingEntity entity, int seed, ItemDisplayContext context) {
            return enabled() ? stack.get(DataComponents.ITEM_MODEL) : null;
        }
        @Override public Codec<net.minecraft.resources.Identifier> valueCodec() { return net.minecraft.resources.Identifier.CODEC; }
        @Override public Type<ItemModel, net.minecraft.resources.Identifier> type() { return TYPE; }
    }

    public enum Island implements SelectItemModelProperty<String> {
        INSTANCE;

        public static final Type<Island, String> TYPE = Type.create(MapCodec.unit(INSTANCE), Codec.STRING);

        @Override public String get(ItemStack stack, ClientLevel level, LivingEntity entity, int seed, ItemDisplayContext context) {
            return enabled() ? Waypoints.currentIslandMode() : null;
        }
        @Override public Codec<String> valueCodec() { return Codec.STRING; }
        @Override public Type<Island, String> type() { return TYPE; }
    }

    public record DataType(String key) implements SelectItemModelProperty<String> {
        public static final Type<DataType, String> TYPE = Type.create(
            Codec.STRING.fieldOf("data_type").xmap(DataType::new, DataType::key), Codec.STRING);

        @Override public String get(ItemStack stack, ClientLevel level, LivingEntity entity, int seed, ItemDisplayContext context) {
            return enabled() ? itemData(stack,key) : null;
        }
        @Override public Codec<String> valueCodec() { return Codec.STRING; }
        @Override public Type<DataType, String> type() { return TYPE; }
    }

    static String itemData(ItemStack stack, String key) {
            var data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) return null;
            String field = switch (key) {
                case "id", "skyblock_id" -> "id";
                case "skin" -> "skin";
                case "dye", "dye_item" -> "dye_item";
                default -> null;
            };
            if (field == null) return null;
            CompoundTag tag = ((ItemData) (Object) data).ccos$tag();
            if (tag.get("ExtraAttributes") instanceof CompoundTag extra) tag = extra;
            return tag.get(field) instanceof StringTag value ? value.value() : null;
    }
}
