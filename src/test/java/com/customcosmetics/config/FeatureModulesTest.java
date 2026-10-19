package com.customcosmetics.config;

import com.customcosmetics.features.chat.CommandTooltips;
import com.customcosmetics.features.nickname.NicknameGradient;
import com.customcosmetics.features.playersize.PlayerSizeSettings;
import com.customcosmetics.utils.NickRenderUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static com.customcosmetics.config.FeatureModules.*;
import static org.junit.jupiter.api.Assertions.*;

class FeatureModulesTest {
    private final int previous=ConfigManager.disabledModules;
    @AfterEach void restore(){ConfigManager.disabledModules=previous;}
    @Test void switchesAreIndependentAndDoNotEraseDetailedSettings(){
        all(true);for(var feature:Feature.values()){assertTrue(enabled(feature));set(feature,false);assertFalse(enabled(feature));for(var other:Feature.values())if(other!=feature)assertTrue(enabled(other));set(feature,true);}
        String colorStyle=ConfigManager.nickTweaksGradientStyle;
        all(false);for(var feature:Feature.values())assertFalse(enabled(feature));assertFalse(remoteConsumers());assertEquals(colorStyle,ConfigManager.nickTweaksGradientStyle);
        set(Feature.MAP,true);assertFalse(remoteConsumers());set(Feature.NICK,true);assertTrue(remoteConsumers());
    }
    @Test void disabledTextAndPlayerHooksReturnBeforeTraversingOrResolvingAnything(){
        all(false);FormattedCharSequence poison=sink->{throw new AssertionError("Disabled nickname hook traversed the text");};
        assertSame(poison,NickRenderUtils.handleCharSequence(poison));assertSame(poison,NickRenderUtils.measureGradient(poison,null));
        assertEquals(PlayerSizeSettings.NORMAL,PlayerSizeSettings.local());assertSame(Style.EMPTY,CommandTooltips.enrich(Style.EMPTY));
    }
}
