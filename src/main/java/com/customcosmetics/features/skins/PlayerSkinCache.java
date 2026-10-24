// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.skins;

import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Small render-thread cache. Check every inherited field so another mod's skin/cape changes survive. */
public final class PlayerSkinCache {
    private static final int LIMIT = 512;
    private final LinkedHashMap<UUID, PlayerSkin> entries = new LinkedHashMap<>(16, .75f, true);
    private record Texture(Identifier id) implements ClientAsset.Texture {
        @Override public Identifier texturePath() { return id; }
    }
    public PlayerSkin body(UUID player, PlayerSkin original, Identifier texture, PlayerModelType model) {
        var cached = entries.get(player);
        if (cached != null && cached.body().texturePath().equals(texture) && cached.cape() == original.cape()
                && cached.elytra() == original.elytra() && cached.model() == model && !cached.secure()) return cached;
        return remember(player, new PlayerSkin(new Texture(texture), original.cape(), original.elytra(), model, false));
    }
    public PlayerSkin cape(UUID player, PlayerSkin original, Identifier texture) {
        var cached = entries.get(player);
        if (cached != null && cached.body() == original.body() && cached.cape() != null && cached.cape().texturePath().equals(texture)
                && cached.elytra() == cached.cape() && cached.model() == original.model()
                && cached.secure() == original.secure()) return cached;
        var asset = new Texture(texture);
        return remember(player, new PlayerSkin(original.body(), asset, asset, original.model(), original.secure()));
    }
    private PlayerSkin remember(UUID player, PlayerSkin skin) {
        entries.put(player, skin);
        if (entries.size() > LIMIT) entries.pollFirstEntry();
        return skin;
    }
    public void clear() { entries.clear(); }
}
