// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.items.*;
import com.customcosmetics.gui.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class ItemAppearanceGameTest {
    private static final String A="12345678-1234-1234-1234-123456789abc",B="12345678-1234-1234-1234-123456789abd";
    static void check(ClientGameTestContext context,TestSingleplayerContext world){
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:blaze_rod[minecraft:custom_data={id:\"HYPERION\",uuid:\""+A+"\"}]");
        world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:blaze_rod[minecraft:custom_data={id:\"HYPERION\",uuid:\""+B+"\"}]");
        context.waitFor(client->ItemCosmetics.uuid(client.player.getMainHandItem()).equals(A)&&ItemCosmetics.uuid(client.player.getOffhandItem()).equals(B));
        var original=new AtomicReference<ItemStack>();var beforeName=new AtomicReference<String>();
        context.runOnClient(client->{
            ConfigManager.resetAnimations();ConfigManager.smolpeopleMode=false;ItemCosmetics.store().enabled(true);
            ItemCosmetics.store().put(A,new ItemAppearanceStore.Appearance("",""));ItemCosmetics.store().put(B,new ItemAppearanceStore.Appearance("",""));
            original.set(client.player.getMainHandItem().copy());beforeName.set(original.get().getHoverName().getString());
            if(!ItemCosmetics.models().contains(Identifier.parse("customcosmetics:appearance_test")))throw new AssertionError("Active resource-pack models were not listed");
            client.options.guiScale().set(2);ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.ITEMS));
        });
        context.getInput().resizeWindow(1600,1000);context.waitTicks(3);
        click(context,"Customize Items");context.waitTicks(2);
        chooseInventoryItem(context,B);chooseInventoryItem(context,A);
        context.runOnClient(client->{
            field(client,"Custom Name").setValue("&dMy Hyperion");field(client,"Model ID").setValue("minecraft:diamond_sword");
        });
        context.takeScreenshot("item-cosmetics-editor-vanilla-model");click(context,"Apply");
        context.runOnClient(client->{
            var held=client.player.getMainHandItem();
            if(!held.getHoverName().getString().equals("My Hyperion"))throw new AssertionError("Custom name did not affect the actual inventory item");
            if(!ItemStack.isSameItemSameComponents(held,original.get()))throw new AssertionError("Local appearance changed the real item components");
            if(!client.player.getOffhandItem().getHoverName().getString().equals(beforeName.get()))throw new AssertionError("Another item with the same SkyBlock ID was renamed");
            if(!com.customcosmetics.features.blocking.SwordItems.isSword(held))throw new AssertionError("Visual model replacement changed sword identity");
            var store=new ItemAppearanceStore(com.customcosmetics.config.CosmeticsConfigDir.getCosmeticsConfigDir().resolve("item-appearances.json"));
            try{store.load();}catch(java.io.IOException error){throw new AssertionError(error);}
            if(!store.get(A).model().equals("minecraft:diamond_sword"))throw new AssertionError("Apply did not save the local override");
        });
        click(context,"Done");context.getInput().pressKey(ClientInput.ESCAPE);context.waitTicks(10);
        context.takeScreenshot("item-cosmetics-held-vanilla-model");
        context.runOnClient(client->{
            if(RenderAssertions.itemModelOverrides.get()==0||!RenderAssertions.customizedItemModel.equals("minecraft:diamond_sword")
                ||!RenderAssertions.untouchedItemModel.equals("minecraft:blaze_rod"))throw new AssertionError("The real renderer did not independently replace only the chosen item model");
        });
        // Server/background readers must see the actual name, not a display override.
        String otherThread=java.util.concurrent.CompletableFuture.supplyAsync(()->original.get().getHoverName().getString()).join();
        if(!otherThread.equals(beforeName.get()))throw new AssertionError("Local names escaped the client thread");
        context.runOnClient(client->ClientUiAccess.setScreen(client,new ItemAppearanceScreen(null)));context.waitTicks(2);
        context.runOnClient(client->{
            field(client,"Custom Name").setValue("");
            if(!ItemCosmetics.store().get(A).name().equals("&dMy Hyperion"))throw new AssertionError("Editing a draft changed the saved appearance before Apply");
        });context.takeScreenshot("item-cosmetics-empty-name-draft-original-preview");
        context.runOnClient(client->field(client,"Custom Name").setValue("&dMy Hyperion"));
        click(context,"Select Model");context.waitTicks(2);
        context.runOnClient(client->{
            var search=(PinkUi.Field)ClientUiAccess.screen(client).children().stream().filter(PinkUi.Field.class::isInstance).findFirst().orElseThrow();
            search.setValue("customcosmetics:appearance_test");
        });
        context.takeScreenshot("item-cosmetics-resource-pack-selector");click(context,"appearance test");
        context.takeScreenshot("item-cosmetics-editor-resource-pack-model");click(context,"Apply");click(context,"Done");context.waitTicks(10);
        context.takeScreenshot("item-cosmetics-held-resource-pack-model");
        context.runOnClient(client->{
            if(!RenderAssertions.customizedItemModel.equals("customcosmetics:appearance_test"))throw new AssertionError("Resource-pack model did not reach the renderer");
            if(!ItemStack.isSameItemSameComponents(client.player.getMainHandItem(),original.get()))throw new AssertionError("Resource-pack override mutated the item");
            ItemCosmetics.store().enabled(false);
            if(!client.player.getMainHandItem().getHoverName().getString().equals(beforeName.get()))throw new AssertionError("Disabling did not restore names");
        });
        context.waitTicks(8);
        context.runOnClient(client->{
            if(!RenderAssertions.customizedItemModel.equals("minecraft:blaze_rod"))throw new AssertionError("Disabling did not restore models");
            ItemCosmetics.store().enabled(true);ItemCosmetics.store().put(A,new ItemAppearanceStore.Appearance("&dMy Hyperion","customcosmetics:missing_model"));
        });context.waitTicks(8);
        context.runOnClient(client->{
            if(!RenderAssertions.customizedItemModel.equals("minecraft:blaze_rod"))throw new AssertionError("An unloaded model did not fall back safely");
            ClientUiAccess.setScreen(client,new ItemAppearanceScreen(null));
        });context.waitTicks(2);
        context.getInput().resizeWindow(854,480);context.waitTicks(3);context.takeScreenshot("item-cosmetics-compact-editor");
        context.getInput().setCursorPos(600,250);
        for(int i=0;i<5;i++){context.getInput().scroll(-1);context.waitTicks(1);}
        context.takeScreenshot("item-cosmetics-compact-editor-scrolled");click(context,"Select Model");click(context,"Back");
        click(context,"Reset Item");context.runOnClient(client->{if(!ItemCosmetics.store().get(A).empty())throw new AssertionError("Reset kept the override");});
        click(context,"Done");context.waitTicks(3);
        System.out.println("ITEM_APPEARANCE_GAME_TEST_OK");
    }
    private static PinkUi.Field field(net.minecraft.client.Minecraft client,String title){
        return (PinkUi.Field)ClientUiAccess.screen(client).children().stream().filter(PinkUi.Field.class::isInstance)
            .map(PinkUi.Field.class::cast).filter(field->field.getMessage().getString().equals(title)).findFirst().orElseThrow();
    }
    private static void chooseInventoryItem(ClientGameTestContext context,String uuid){
        click(context,"Select Item");
        context.runOnClient(client->{
            var search=(PinkUi.Field)ClientUiAccess.screen(client).children().stream().filter(PinkUi.Field.class::isInstance).findFirst().orElseThrow();
            search.setValue(uuid);
        });context.waitTicks(2);click(context,"Blaze Rod");
        context.runOnClient(client->{
            try{
                var selected=ItemAppearanceScreen.class.getDeclaredField("uuid");selected.setAccessible(true);
                if(!uuid.equals(selected.get(ClientUiAccess.screen(client))))throw new AssertionError("Inventory selection chose the wrong physical item");
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });
    }
    private static void click(ClientGameTestContext context,String label){
        var position=new AtomicReference<double[]>();
        context.runOnClient(client->{
            var widget=ClientUiAccess.screen(client).children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(value->value.active&&value.visible&&value.getMessage().getString().startsWith(label)).findFirst().orElseThrow(()->new AssertionError("Missing control "+label));
            double scale=client.getWindow().getGuiScale();position.set(new double[]{(widget.getX()+widget.getWidth()/2.0)*scale,(widget.getY()+widget.getHeight()/2.0)*scale});
        });
        context.getInput().setCursorPos(position.get()[0],position.get()[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.waitTicks(3);
    }
}
