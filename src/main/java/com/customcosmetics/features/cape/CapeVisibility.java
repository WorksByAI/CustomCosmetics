// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.cape;
import java.util.*;
public final class CapeVisibility {
    private CapeVisibility(){}
    public static String previewHash(UUID self,boolean online,String local,Map<UUID,String> approved){
        return local!=null?local:online&&self!=null?approved.get(self):null;
    }
    public static String hash(UUID player,UUID self,boolean online,boolean enabled,boolean preview,String local,Map<UUID,String> approved){
        if(!enabled||player==null)return null;
        if(preview&&player.equals(self))return previewHash(self,online,local,approved);
        if(!online)return player.equals(self)?local:null;
        return approved.get(player);
    }
}
