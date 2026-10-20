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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

final class ItemAppearanceGameTest {
    private static final String A="12345678-1234-1234-1234-123456789abc",B="12345678-1234-1234-1234-123456789abd",C="12345678-1234-1234-1234-123456789abe";
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
            var held = client.player.getMainHandItem();
            var choices = ItemCosmetics.modelChoices(held);
            for (String broken : new String[]{"fsr_generated:collections/mining/diamond/2", "customcosmetics:appearance_bad_texture", "customcosmetics:appearance_partial_missing"}) {
                var id = Identifier.parse(broken);
                if (!ItemCosmetics.hasModel(id)) throw new AssertionError("Regression fixture was not loaded: " + id);
                if (ItemCosmetics.renderable(held, id) || choices.stream().anyMatch(choice -> choice.id().equals(id)))
                    throw new AssertionError("Missing-texture model reached the picker: " + id);
            }
            if (!choices.getFirst().id().getNamespace().equals("minecraft")) throw new AssertionError("Vanilla items were buried behind generated pack entries");
            for (String good : new String[]{"minecraft:diamond_sword", "minecraft:shield", "customcosmetics:appearance_test", "fsr_generated:weapons/hyperion"}) {
                var choice = choices.stream().filter(value -> value.id().toString().equals(good)).findFirst().orElseThrow();
                var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
                client.getItemModelResolver().updateForTopItem(state, choice.icon(), net.minecraft.world.item.ItemDisplayContext.GUI, client.level, client.player, 0);
                if (!ItemModelVisibility.usable(state)) throw new AssertionError("Picker icon resolved to missing textures: " + good);
            }
            var display = ItemCosmetics.preview(held, Identifier.parse("minecraft:diamond_sword"));
            if (!ItemCosmetics.uuid(display).isEmpty() || !ItemStack.isSameItemSameComponents(held,original.get()))
                throw new AssertionError("Preview changed the actual inventory item");
            if (Boolean.getBoolean("customcosmetics.itemModelCatharsisTest")) {
                if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("catharsis")) throw new AssertionError("Catharsis compatibility test requires Catharsis");
                var fallthrough = Identifier.parse("customcosmetics:appearance_fallthrough");
                if (!ItemCosmetics.renderable(held, fallthrough)) throw new AssertionError("Catharsis conditional fixture missing");
                ItemCosmetics.store().put(A,new ItemAppearanceStore.Appearance("",fallthrough.toString()));
                var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
                client.getItemModelResolver().updateForTopItem(state,held,net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,client.level,client.player,0);
                if (!ItemModelVisibility.usable(state) || !state.pickParticleMaterial(net.minecraft.util.RandomSource.create(0)).sprite().contents().name().toString().equals("minecraft:item/blaze_rod"))
                    throw new AssertionError("Catharsis fallthrough did not reach the original item model");
                ItemCosmetics.store().put(A,new ItemAppearanceStore.Appearance("",""));
            }
            client.options.guiScale().set(2);ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.ITEMS));
        });
        context.getInput().resizeWindow(1600,1000);context.waitTicks(3);
        click(context,"Customize Items");context.waitTicks(2);
        chooseInventoryItem(context,B);chooseInventoryItem(context,A);
        click(context,"Model ID");context.getInput().typeChars("golden_axe");context.waitTicks(2);
        context.runOnClient(client->{
            try{
                var method=ItemAppearanceScreen.class.getDeclaredMethod("preview");method.setAccessible(true);
                var preview=(ItemStack)method.invoke(ClientUiAccess.screen(client));
                if(!Identifier.parse("minecraft:golden_axe").equals(preview.get(DataComponents.ITEM_MODEL)))
                    throw new AssertionError("Manually typed shorthand did not resolve in the preview");
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        });
        context.takeScreenshot("item-cosmetics-manual-shorthand-preview");click(context,"Apply");
        context.runOnClient(client->{
            if(!ItemCosmetics.store().get(A).model().equals("minecraft:golden_axe"))
                throw new AssertionError("Apply rejected the same shorthand model shown in the preview");
            field(client,"Custom Name").setValue("Saved with Done");field(client,"Model ID").setValue("iron_axe");
        });
        click(context,"Done");
        context.runOnClient(client->{
            if(ClientUiAccess.screen(client) instanceof ItemAppearanceScreen)
                throw new AssertionError("Done did not close after saving a valid draft");
            var restored=new ItemAppearanceStore(com.customcosmetics.config.CosmeticsConfigDir.getCosmeticsConfigDir().resolve("item-appearances.json"));
            try{restored.load();}catch(java.io.IOException error){throw new AssertionError(error);}
            if(!restored.get(A).model().equals("minecraft:iron_axe")||!restored.get(A).name().equals("Saved with Done"))
                throw new AssertionError("Done discarded a draft without an earlier Apply");
        });
        click(context,"Customize Items");context.waitTicks(2);
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
        context.runOnClient(client -> field(client,"Model ID").setValue("customcosmetics:appearance_bad_texture"));
        click(context,"Apply");
        click(context,"Done");
        context.runOnClient(client -> {
            if(!(ClientUiAccess.screen(client) instanceof ItemAppearanceScreen))
                throw new AssertionError("Done closed the editor despite an invalid model");
            if(!ItemCosmetics.store().get(A).model().equals("minecraft:diamond_sword"))
                throw new AssertionError("Typing a loaded but broken model bypassed validation");
            field(client,"Model ID").setValue("minecraft:diamond_sword");
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
        context.takeScreenshot("item-model-picker-filtered");
        context.runOnClient(ItemAppearanceGameTest::dragModelPickerScrollbar);
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
            ItemCosmetics.store().enabled(true);ItemCosmetics.store().put(A,new ItemAppearanceStore.Appearance("&dMy Hyperion","customcosmetics:appearance_bad_texture"));
        });context.waitTicks(8);
        context.runOnClient(client->{
            if(!RenderAssertions.customizedItemModel.equals("minecraft:blaze_rod"))throw new AssertionError("A loaded missing-texture model did not fall back safely");
            ClientUiAccess.setScreen(client,new ItemAppearanceScreen(null));
        });context.waitTicks(2);
        context.getInput().resizeWindow(854,480);context.waitTicks(3);context.takeScreenshot("item-cosmetics-compact-editor");
        context.getInput().setCursorPos(600,250);
        for(int i=0;i<5;i++){context.getInput().scroll(-1);context.waitTicks(1);}
        context.takeScreenshot("item-cosmetics-compact-editor-scrolled");click(context,"Select Model");click(context,"Back");
        click(context,"Reset Item");context.runOnClient(client->{if(!ItemCosmetics.store().get(A).empty())throw new AssertionError("Reset kept the override");});
        click(context,"Done");context.waitTicks(3);
        world.getServer().runCommand("item replace entity @a armor.head with minecraft:player_head[minecraft:custom_data={id:\"WARDEN_HELMET\",uuid:\""+C+"\"}]");
        context.waitFor(client->ItemCosmetics.uuid(client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)).equals(C));
        context.runOnClient(client->ClientUiAccess.setScreen(client,new ItemAppearanceScreen(null)));
        context.waitTicks(2);chooseInventoryItem(context,C);
        context.getInput().setCursorPos(600,250);
        for(int i=0;i<7;i++){context.getInput().scroll(-1);context.waitTicks(1);}
        click(context,"Use Leather");
        context.runOnClient(client->field(client,"Armor Color").setValue("#357ACB"));
        click(context,"Apply");
        context.runOnClient(client->{
            var saved=ItemCosmetics.store().get(C);
            if(!saved.armorModel().equals("minecraft:leather")||!Integer.valueOf(0x357ACB).equals(saved.armorColor()))
                throw new AssertionError("Armor editor did not save the leather model and tint");
        });
        click(context,"Done");context.waitTicks(2);
        context.runOnClient(client->{
            var real=client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            var originalHead=real.copy();
            if(!ArmorAppearance.available("minecraft:leather",net.minecraft.world.entity.EquipmentSlot.HEAD))
                throw new AssertionError("Vanilla leather armor asset was unavailable");
            var state=new net.minecraft.client.renderer.entity.state.HumanoidRenderState();
            net.minecraft.client.renderer.entity.HumanoidMobRenderer.extractHumanoidRenderState(client.player,state,0,client.getItemModelResolver());
            if(!state.headEquipment.is(net.minecraft.world.item.Items.LEATHER_HELMET)||!ItemCosmetics.uuid(state.headEquipment).equals(C))
                throw new AssertionError("The Warden-like player head did not become a leather render proxy");
            if(!ArmorAppearance.model(state.headEquipment,net.minecraft.world.item.equipment.EquipmentAssets.IRON,
                    net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID).equals(net.minecraft.world.item.equipment.EquipmentAssets.LEATHER))
                throw new AssertionError("The configured armor model did not reach the equipment renderer");
            if(net.minecraft.world.item.component.DyedItemColor.getOrDefault(state.headEquipment,0)!=0xFF357ACB)
                throw new AssertionError("The leather helmet did not receive its selected tint");
            if(!ItemStack.isSameItemSameComponents(real,originalHead))throw new AssertionError("Armor styling changed the real player head");
            ItemCosmetics.store().enabled(false);
            if(ArmorAppearance.headProxy(real,net.minecraft.world.entity.EquipmentSlot.HEAD)!=real)
                throw new AssertionError("Disabling item customization left the head override active");
            ItemCosmetics.store().enabled(true);
        });
        context.runOnClient(client->client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(5);context.takeScreenshot("item-cosmetics-warden-head-leather-armor");
        context.runOnClient(client->{client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);ItemCosmetics.store().put(C,new ItemAppearanceStore.Appearance("",""));ItemCosmetics.save();});
        world.getServer().runCommand("item replace entity @a armor.head with minecraft:air");
        System.out.println("ITEM_APPEARANCE_GAME_TEST_OK");
    }
    private static PinkUi.Field field(net.minecraft.client.Minecraft client,String title){
        return (PinkUi.Field)ClientUiAccess.screen(client).children().stream().filter(PinkUi.Field.class::isInstance)
            .map(PinkUi.Field.class::cast).filter(field->field.getMessage().getString().equals(title)).findFirst().orElseThrow();
    }
    private static void dragModelPickerScrollbar(net.minecraft.client.Minecraft client){
        Screen screen=ClientUiAccess.screen(client);
        if(!(screen instanceof ItemChoiceScreen))throw new AssertionError("Select Model did not open the model list");
        try{
            int x=intField(screen,"x"),w=intField(screen,"w"),top=intField(screen,"top"),bottom=intField(screen,"bottom");
            var choices=(java.util.List<?>)objectField(screen,"filtered");
            var rows=(java.util.List<?>)objectField(screen,"rows");
            int range=choices.size()-rows.size(),track=bottom-top;
            if(range<=0)throw new AssertionError("Model list has no scrollable overflow");
            int thumb=Math.min(track,Math.max(Math.min(12,track),(int)Math.round(track*rows.size()/(double)choices.size())));
            int travel=track-thumb,grab=thumb/2;
            double mouseX=x+w-20;
            var info=new MouseButtonInfo(ClientInput.MOUSE_LEFT,0);
            var press=new MouseButtonEvent(mouseX,top+grab,info);
            if(!screen.mouseClicked(press,false))throw new AssertionError("Scrollbar did not capture the mouse press");
            var end=new MouseButtonEvent(mouseX,top+travel+grab,info);
            if(!screen.mouseDragged(end,0,travel))throw new AssertionError("Scrollbar did not capture dragging");
            if(intField(screen,"offset")!=range)throw new AssertionError("Dragging to the bottom did not reveal the last model rows");
            var start=new MouseButtonEvent(mouseX,top+grab,info);
            screen.mouseDragged(start,0,-travel);
            if(intField(screen,"offset")!=0)throw new AssertionError("Dragging back to the top did not restore the first rows");
            screen.mouseReleased(start);
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private static Object objectField(Object target,String name)throws ReflectiveOperationException{
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    private static int intField(Object target,String name)throws ReflectiveOperationException{
        return ((Number)objectField(target,name)).intValue();
    }
    private static void chooseInventoryItem(ClientGameTestContext context,String uuid){
        click(context,"Select Item");
        context.runOnClient(client->{
            var search=(PinkUi.Field)ClientUiAccess.screen(client).children().stream().filter(PinkUi.Field.class::isInstance).findFirst().orElseThrow();
            search.setValue(uuid);
        });context.waitTicks(2);click(context,uuid.equals(C)?"Player Head":"Blaze Rod");
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
