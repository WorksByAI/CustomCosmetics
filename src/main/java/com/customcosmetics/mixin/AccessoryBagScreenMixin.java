// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;
import com.customcosmetics.config.*;
import com.customcosmetics.features.accessoryhelper.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(AbstractContainerScreen.class)
public abstract class AccessoryBagScreenMixin {
    @Shadow protected int leftPos,topPos;
    @Shadow @Final protected int imageWidth;
    @Unique private MissingAccessoriesPanel customcosmetics$missing;
    @Unique private boolean customcosmetics$enabled(){return ConfigManager.accessoryHelperTooltipEnabled&&FeatureModules.enabled(FeatureModules.Feature.ITEMS);}
    @Inject(method="extractRenderState",at=@At("TAIL"))
    private void panel(GuiGraphicsExtractor g,int mx,int my,float delta,CallbackInfo callback){
        if(!customcosmetics$enabled()||AccessoryTooltipHelper.bagCatalog(this)==null)return;
        if(customcosmetics$missing==null)customcosmetics$missing=new MissingAccessoriesPanel();
        customcosmetics$missing.render((Screen)(Object)this,g,leftPos,topPos,imageWidth,mx,my);
    }
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void click(MouseButtonEvent event,boolean twice,CallbackInfoReturnable<Boolean> callback){
        if(customcosmetics$enabled()&&AccessoryTooltipHelper.bagCatalog(this)!=null&&customcosmetics$missing!=null&&customcosmetics$missing.click(event))callback.setReturnValue(true);
    }
}
