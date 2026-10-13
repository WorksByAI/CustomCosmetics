// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.cape;
import java.util.*;
public final class CapeVisibility {
    private CapeVisibility(){}
    public static String hash(UUID player,UUID self,boolean online,boolean enabled,boolean preview,String local,Map<UUID,String> approved){
        if(!enabled)return null;
        if(preview&&player.equals(self))return local;
        if(!online)return player.equals(self)?local:null;
        return approved.get(player);
    }
}
