// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemVisualOnlyTest {
    @Test void itemEditingCannotUseGameActionsOrEitherNetworkTransport() throws Exception {
        for(String type:List.of("features/items/ItemCosmetics","features/items/ArmorAppearance","features/items/ItemModelVisibility","features/items/ItemAppearanceStore","features/items/ItemNameText",
            "gui/ItemAppearanceScreen","gui/ItemChoiceScreen","gui/ItemChoiceScreen$Row","mixin/ItemAppearanceMixin$Names","mixin/ItemAppearanceMixin$Models",
            "mixin/ArmorAppearanceMixin$Assets","mixin/ArmorAppearanceMixin$HeadEquipment","mixin/ArmorAppearanceMixin$HideOriginalHead",
            "mixin/ArmorAppearanceMixin$Model","mixin/ArmorAppearanceMixin$Color")){
            try(var input=getClass().getClassLoader().getResourceAsStream("com/customcosmetics/"+type+".class")){
                assertNotNull(input);var model=ClassFile.of().parse(input.readAllBytes());
                for(var method:model.methods())for(var instruction:method.code().map(code->code.elementStream().toList()).orElse(List.of())){
                    if(instruction instanceof InvokeInstruction call){
                        String owner=call.owner().name().stringValue(),name=call.name().stringValue();
                        assertFalse(owner.startsWith("java/net/")||owner.startsWith("com/customcosmetics/sync/")
                            ||owner.startsWith("net/minecraft/network/protocol/")||owner.startsWith("net/minecraft/client/multiplayer/"),owner);
                        assertFalse(Set.of("sendPacket","sendCommand","getConnection","useItem","useItemOn","attack","swing",
                            "startDestroyBlock","continueDestroyBlock","setItemInHand","setDown").contains(name),name);
                        if(type.equals("features/items/ItemCosmetics")&&!method.methodName().stringValue().equals("preview")&&owner.equals("net/minecraft/world/item/ItemStack"))
                            assertFalse(Set.of("set","remove","applyComponents","applyComponentsAndValidate","setCount").contains(name),"Runtime display hooks cannot mutate the original stack");
                    }
                }
            }
        }
    }
}
