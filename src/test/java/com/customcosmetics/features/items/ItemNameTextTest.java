// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemNameTextTest {
    @Test void legacyCodesAndHexColorsProduceLiteralDisplayOnlyText(){
        var text=ItemNameText.parse("&dPink &lBold &rNormal &#12abEFHex §aGreen");
        assertEquals("Pink Bold Normal Hex Green",text.getString());
        var parts=text.getSiblings();assertEquals(0xff55ff,parts.get(0).getStyle().getColor().getValue());
        assertTrue(parts.get(1).getStyle().isBold());assertFalse(parts.get(2).getStyle().isBold());
        assertEquals(0x12abef,parts.get(3).getStyle().getColor().getValue());
        assertTrue(parts.stream().noneMatch(part->part.getStyle().isItalic()||part.getStyle().getClickEvent()!=null));
    }
    @Test void malformedCodesAndJsonRemainLiteral(){
        for(String value:new String[]{"name &z test","&#xyzxyzHello","&#+12345Hello","&#ff00","trailing&","{\"text\":\"Click\",\"clickEvent\":{}}"})
            assertEquals(value,ItemNameText.parse(value).getString());
    }
    @Test void colorChangesResetFormattingButExplicitItalicIsPreserved(){
        var text=ItemNameText.parse("&lBold &aGreen &oItalic");
        assertTrue(text.getSiblings().get(0).getStyle().isBold());assertFalse(text.getSiblings().get(1).getStyle().isBold());
        assertTrue(text.getSiblings().get(2).getStyle().isItalic());
    }
    @Test void supportsEmptyAndUnicodeWithoutEvaluatingCommands(){
        assertEquals("",ItemNameText.parse("").getString());
        assertEquals("😀 /warp home 中文",ItemNameText.parse("&d😀 /warp home 中文").getString());
    }
}
