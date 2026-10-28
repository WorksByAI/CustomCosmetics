// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessoryhelper;
import java.util.*;

/** Family-aware missing entries; lower tiers already superseded by owned items are excluded. */
public final class MissingAccessories {
    public enum Filter { MISSING, UPGRADES, ALL }
    private MissingAccessories(){}
    public static List<AccessoryCatalog.Entry> list(AccessoryCatalog catalog,AccessoryBagIndex.View bag,Filter filter,boolean allTiers){
        return list(catalog,bag,filter,allTiers,Set.of());
    }
    public static List<AccessoryCatalog.Entry> list(AccessoryCatalog catalog,AccessoryBagIndex.View bag,Filter filter,boolean allTiers,Set<String> ignored){
        if(!bag.complete())return List.of();
        var result=new ArrayList<AccessoryCatalog.Entry>();var families=new HashSet<String>();
        for(var item:catalog.entries()){
            if(ignored.contains(item.id()))continue;
            int owned=bag.highest().getOrDefault(item.family(),0);
            if(item.tier()<=owned||filter==Filter.MISSING&&owned>0||filter==Filter.UPGRADES&&owned==0)continue;
            if(allTiers||families.add(item.family()))result.add(item);
        }
        return List.copyOf(result);
    }
}
