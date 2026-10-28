// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;
import com.customcosmetics.client.ModErrors;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.*;
import com.customcosmetics.features.creature.CreatureModels;
import com.customcosmetics.gui.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.entity.state.*;
public final class PreviewCrashGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.syncEnabled=false;ConfigManager.offlineMode=true;ConfigManager.creatureModel="ender_dragon";FeatureModules.set(FeatureModules.Feature.CREATURE_MODEL,true);});
        try(var world=context.worldBuilder().create()){
            context.waitTicks(8);checkPeerCodec(context);context.getInput().resizeWindow(1100,800);
            context.runOnClient(client->{
                var dispatcher=client.getEntityRenderDispatcher();
                if(!(dispatcher.extractEntity(client.player,0) instanceof EnderDragonRenderState))throw new AssertionError("Fixture did not reproduce the selected dragon model");
                CreatureModels.beginAvatarPreview();try{if(!(dispatcher.extractEntity(client.player,0) instanceof AvatarRenderState))throw new AssertionError("Avatar preview scope still replaced the player");}finally{CreatureModels.endAvatarPreview();}
                ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SKINS));
            });context.waitTicks(5);context.takeScreenshot("dragon-selected-skin-preview-keeps-avatar");
            context.runOnClient(client->ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.ACCESSORIES)));context.waitTicks(5);context.takeScreenshot("dragon-selected-accessory-preview-keeps-avatar");
            context.runOnClient(client->ClientUiAccess.setScreen(client,new CapeScreen(null)));context.waitTicks(5);context.takeScreenshot("dragon-selected-cape-preview-keeps-avatar");
            context.runOnClient(client->ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SIZE)));context.waitTicks(5);context.takeScreenshot("dragon-selected-size-preview-native-name");
            context.runOnClient(client->{
                if(!ModErrors.active(ModErrors.Key.PREVIEW))throw new AssertionError("A preview failure was hidden by recovery instead of fixed");
                if(!(client.getEntityRenderDispatcher().extractEntity(client.player,0) instanceof EnderDragonRenderState)||!ConfigManager.creatureModel.equals("ender_dragon"))throw new AssertionError("Preview scope leaked into normal rendering or changed the selection");
                ConfigManager.uiLanguage="zh_cn";UiLocale.refresh();ModErrors.report(ModErrors.Key.DEBUG,new IllegalStateException("Intentional native error-notice test"));ModErrors.flush(client);
                ClientUiAccess.setScreen(client,null);ConfigManager.creatureModel="player";
            });context.waitTicks(3);
            System.out.println("DRAGON_PLAYER_PREVIEW_CAST_REGRESSION_SCOPED_AND_LOCALIZED_RECOVERY_OK");
        }
    }
    private static void checkPeerCodec(ClientGameTestContext context){
        try{
            var capture=com.customcosmetics.sync.PresenceSync.class.getDeclaredMethod("capturePeerAppearance");capture.setAccessible(true);
            var source=context.computeOnClient(client->{
                int disabled=ConfigManager.disabledModules;boolean smol=ConfigManager.smolpeopleMode;
                try{ConfigManager.smolpeopleMode=true;FeatureModules.set(FeatureModules.Feature.SMOL,false);return (java.util.function.Supplier<com.google.gson.JsonObject>)capture.invoke(null);}
                finally{ConfigManager.disabledModules=disabled;ConfigManager.smolpeopleMode=smol;}
            });
            var features=java.util.concurrent.CompletableFuture.supplyAsync(source).get(5,java.util.concurrent.TimeUnit.SECONDS);
            if(features.getAsJsonObject("smolPeople").get("enabled").getAsBoolean())throw new AssertionError("Disabled SmolPeople is still advertised");
            if(!features.get("creatureModel").getAsString().equals("ender_dragon"))throw new AssertionError("Peer snapshot lost the chosen model");
            if(com.customcosmetics.sync.AppearanceTransfer.encode(features).size()>com.customcosmetics.sync.AppearanceTransfer.MAX_COMPRESSED)throw new AssertionError("Native appearance cannot be transported");
            System.out.println("PEER_APPEARANCE_NATIVE_CAPTURE_WORKER_CODEC_AND_DISABLED_MODULE_GATE_OK");
        }catch(Exception failure){throw new AssertionError(failure);}
    }

}
