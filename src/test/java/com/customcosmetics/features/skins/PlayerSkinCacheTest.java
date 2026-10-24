// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.skins;

import java.util.UUID;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerSkinCacheTest {
    private record Texture(Identifier id) implements ClientAsset.Texture {
        @Override public Identifier texturePath(){return id;}
    }
    private static Texture texture(String name){return new Texture(Identifier.parse("customcosmetics:"+name));}
    private static PlayerSkin original(){return new PlayerSkin(texture("body"),texture("cape"),texture("elytra"),PlayerModelType.WIDE,true);}
    @Test void unchangedBodySharesTheResultButTextureModelAndInheritedCapesInvalidate(){
        var cache=new PlayerSkinCache();var player=UUID.randomUUID();var original=original();var body=texture("override").id();
        var result=cache.body(player,original,body,PlayerModelType.WIDE);
        assertSame(result,cache.body(player,original,body,PlayerModelType.WIDE));assertFalse(result.secure());
        assertSame(original.cape(),result.cape());assertSame(original.elytra(),result.elytra());
        var slim=cache.body(player,original,body,PlayerModelType.SLIM);assertNotSame(result,slim);assertEquals(PlayerModelType.SLIM,slim.model());
        var changedCape=new PlayerSkin(original.body(),texture("new-cape"),original.elytra(),original.model(),true);
        var changed=cache.body(player,changedCape,body,PlayerModelType.SLIM);assertNotSame(slim,changed);assertSame(changedCape.cape(),changed.cape());
        var changedElytra=new PlayerSkin(original.body(),changedCape.cape(),texture("new-elytra"),original.model(),true);
        assertSame(changedElytra.elytra(),cache.body(player,changedElytra,body,PlayerModelType.SLIM).elytra());
        var newTexture=texture("second-override").id();assertEquals(newTexture,cache.body(player,original,newTexture,PlayerModelType.SLIM).body().texturePath());
    }
    @Test void capeKeepsOriginalBodyModelAndSecurityWhileUpdatingBothCapeTextures(){
        var cache=new PlayerSkinCache();var player=UUID.randomUUID();var original=original();var cape=texture("override-cape").id();
        var result=cache.cape(player,original,cape);assertSame(result,cache.cape(player,original,cape));assertSame(result.cape(),result.elytra());
        var changed=new PlayerSkin(texture("new-body"),null,null,PlayerModelType.SLIM,false);
        var next=cache.cape(player,changed,cape);assertNotSame(result,next);assertSame(changed.body(),next.body());assertEquals(changed.model(),next.model());assertFalse(next.secure());
        var updated=texture("another-cape").id();assertEquals(updated,cache.cape(player,changed,updated).cape().texturePath());
    }
    @Test void entriesAreBoundedAndClearDoesNotRetainOldPlayers(){
        var cache=new PlayerSkinCache();var original=original();var player=UUID.randomUUID();var id=texture("override").id();
        var first=cache.body(player,original,id,PlayerModelType.WIDE);
        for(int i=0;i<512;i++)cache.body(new UUID(0,i),original,id,PlayerModelType.WIDE);
        var evicted=cache.body(player,original,id,PlayerModelType.WIDE);assertNotSame(first,evicted);
        cache.clear();assertNotSame(evicted,cache.body(player,original,id,PlayerModelType.WIDE));
    }
}
