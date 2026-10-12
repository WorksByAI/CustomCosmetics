// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.features.items.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import static com.customcosmetics.gui.PinkUi.*;

/** Draft changes stay in the editor until Apply; actual inventory components are never changed. */
public final class ItemAppearanceScreen extends Screen {
    private final Screen parent;
    private final List<AbstractWidget> controls=new ArrayList<>();
    private final List<Placement> body=new ArrayList<>();
    private record Placement(AbstractWidget widget,int y){}
    private ItemStack item=ItemStack.EMPTY;
    private String uuid="",draftName="",draftModel="",status="";
    private int x,y,w,h,previewWidth,scroll,bodyTop,bodyBottom;
    private PinkUi.Field nameField,modelField;
    public ItemAppearanceScreen(Screen parent){
        super(Component.literal("Item Cosmetics"));this.parent=parent;
        var player=net.minecraft.client.Minecraft.getInstance().player;
        if(player!=null&&!ItemCosmetics.uuid(player.getMainHandItem()).isEmpty())select(player.getMainHandItem());
    }
    private void select(ItemStack stack){
        item=stack.copy();uuid=ItemCosmetics.uuid(item);
        var appearance=ItemCosmetics.store().get(uuid);draftName=appearance.name();draftModel=appearance.model();status="";
    }
    @Override protected void init(){
        controls.clear();body.clear();w=Math.min(620,width-16);h=Math.min(370,height-16);x=(width-w)/2;y=(height-h)/2;
        bodyTop=y+80;bodyBottom=y+h-74;scroll=Math.clamp(scroll,0,maxScroll());
        previewWidth=Math.clamp(w/3,82,160);
        action(x+16,y+42,w-32,"Select Item",this::chooseItem);
        int fx=x+previewWidth+24,fw=w-previewWidth-40;
        nameField=new PinkUi.Field(font,fx,y+99,fw,"Custom Name");nameField.setMaxLength(ItemAppearanceStore.MAX_NAME);nameField.setValue(draftName);
        nameField.setHint(Component.literal("Empty = original name"));nameField.setResponder(value->{draftName=value;status="";CosmeticsScreen.childInteraction();});body.add(new Placement(addWidget(nameField),nameField.getY()));
        modelField=new PinkUi.Field(font,fx,y+174,fw,"Model ID");modelField.setMaxLength(256);modelField.setValue(draftModel);
        modelField.setHint(Component.literal("Empty = original model"));modelField.setResponder(value->{draftModel=value;status="";CosmeticsScreen.childInteraction();});body.add(new Placement(addWidget(modelField),modelField.getY()));
        var selectModel=action(fx,y+209,fw,"Select Model",this::chooseModel);controls.remove(selectModel);body.add(new Placement(selectModel,selectModel.getY()));
        int part=(w-44)/3;
        action(x+16,y+h-38,part,"Reset Item",this::reset);
        action(x+22+part,y+h-38,part,"Apply",this::apply);
        action(x+28+part*2,y+h-38,part,"Done",this::onClose);
        nameField.active=modelField.active=!uuid.isEmpty();
        positionBody();
    }
    private PinkUi.Action action(int bx,int by,int bw,String label,Runnable callback){
        var widget=addWidget(new PinkUi.Action(bx,by,bw,26,label,false,()->{CosmeticsScreen.childInteraction();callback.run();}));controls.add(widget);return widget;
    }
    private void chooseItem(){
        var choices=new LinkedHashMap<String,ItemChoiceScreen.Choice>();
        if(minecraft.player!=null){
            var inventory=minecraft.player.getInventory();
            for(int i=0;i<inventory.getContainerSize();i++)addChoice(choices,inventory.getItem(i));
            for(var slot:EquipmentSlot.values())addChoice(choices,minecraft.player.getItemBySlot(slot));
        }
        ClientUiAccess.setScreen(minecraft,new ItemChoiceScreen(this,"Select Item",new ArrayList<>(choices.values()),choice->{select(choice.icon());rebuildWidgets();}));
    }
    private static void addChoice(Map<String,ItemChoiceScreen.Choice> choices,ItemStack stack){
        String key=ItemCosmetics.uuid(stack);
        if(!key.isEmpty())choices.putIfAbsent(key,new ItemChoiceScreen.Choice(stack.getHoverName().getString(),key,stack.copy()));
    }
    private void chooseModel(){
        if(uuid.isEmpty()){status="Select an item with a unique SkyBlock item UUID.";return;}
        var choices=ItemCosmetics.models().stream().map(id->{
            var icon=new ItemStack(Items.PAPER);icon.set(DataComponents.ITEM_MODEL,id);
            return new ItemChoiceScreen.Choice(id.getPath().replace('_',' '),id.toString(),icon);
        }).toList();
        ClientUiAccess.setScreen(minecraft,new ItemChoiceScreen(this,"Select Model",choices,choice->{draftModel=choice.id();rebuildWidgets();}));
    }
    private void apply(){
        if(uuid.isEmpty()){status="Select an item with a unique SkyBlock item UUID.";return;}
        try{
            var value=new ItemAppearanceStore.Appearance(draftName,draftModel);
            if(!value.model().isEmpty()&&!ItemCosmetics.hasModel(Identifier.tryParse(value.model()))){modelField.invalid(true);status="This model is not loaded. Choose a model or clear the field.";return;}
            ItemCosmetics.store().put(uuid,value);modelField.invalid(false);
            status=ItemCosmetics.save()?"Saved. Only visible on your client.":"Could not save. Please try again.";
        }catch(IllegalArgumentException|IllegalStateException error){status=error.getMessage();}
    }
    private void reset(){
        if(uuid.isEmpty())return;
        ItemCosmetics.store().put(uuid,new ItemAppearanceStore.Appearance("",""));ItemCosmetics.save();
        draftName=draftModel="";rebuildWidgets();status="Original name and model restored.";
    }
    private ItemStack preview(){
        var copy=item.copy();
        // Remove identity only from this temporary preview so a saved override cannot replace the draft.
        var data=copy.get(DataComponents.CUSTOM_DATA);
        if(data!=null){var tag=data.copyTag();tag.remove("uuid");tag.getCompound("ExtraAttributes").ifPresent(extra->extra.remove("uuid"));copy.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
        var model=Identifier.tryParse(draftModel.strip());
        if(ItemCosmetics.hasModel(model))copy.set(DataComponents.ITEM_MODEL,model);
        return copy;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(minecraft.level==null)super.extractBackground(g,mx,my,delta);else g.blurBeforeThisStratum();g.fill(0,0,width,height,0x4020242A);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        outline(g,x,y,w,h,11,BORDER,PANEL);text(g,font,"Item Cosmetics",x+16,y+17,PINK,w-32);
        for(var widget:controls)widget.extractRenderState(g,mx,my,delta);
        g.enableScissor(x+15,bodyTop,x+w-15,bodyBottom);g.pose().pushMatrix();g.pose().translate(0,-scroll);
        int fx=x+previewWidth+24,fw=w-previewWidth-40;
        outline(g,x+16,y+85,previewWidth-8,154,6,BORDER,CARD);text(g,font,"Preview",x+26,y+96,PINK,previewWidth-28);
        if(!item.isEmpty()){
            var preview=preview();
            g.pose().pushMatrix();g.pose().translate(x+16+(previewWidth-8)/2f-24,y+130);g.pose().scale(3,3);g.item(preview,0,0);g.pose().popMatrix();
            Component name=draftName.isEmpty()?preview.getHoverName():ItemNameText.parse(draftName);
            float scale=Math.min(1,(previewWidth-28)/(float)Math.max(1,font.width(name)));
            g.pose().pushMatrix();g.pose().translate(x+26,y+214);g.pose().scale(scale,scale);g.text(font,name,0,0,TEXT,false);g.pose().popMatrix();
        }
        text(g,font,"Custom Name",fx,y+85,TEXT,fw);text(g,font,"Colors: &0–&f, &r, &#RRGGBB",fx,y+135,MUTED,fw);
        text(g,font,"Model ID",fx,y+159,TEXT,fw);
        g.pose().popMatrix();for(var placement:body)if(placement.widget().visible)placement.widget().extractRenderState(g,mx,my,delta);g.disableScissor();
        if(maxScroll()>0){int track=bodyBottom-bodyTop,thumb=Math.max(12,track*track/160);rounded(g,x+w-10,bodyTop,3,track,1,BORDER);rounded(g,x+w-10,bodyTop+scroll*(track-thumb)/maxScroll(),3,thumb,1,PINK);}
        text(g,font,status.isEmpty()?(uuid.isEmpty()?"Select a SkyBlock item with a unique item UUID.":"Click Apply to save changes to this item."):status,x+16,y+h-61,MUTED,w-32);
    }
    private int maxScroll(){return Math.max(0,160-(bodyBottom-bodyTop));}
    private void positionBody(){for(var p:body){p.widget().setY(p.y()-scroll);p.widget().visible=p.widget().getY()+p.widget().getHeight()>bodyTop&&p.widget().getY()<bodyBottom;}}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        CosmeticsScreen.childInteraction();if(mx>=x&&mx<x+w&&my>=bodyTop&&my<bodyBottom){scroll=Math.clamp(scroll-(int)Math.signum(vertical)*24,0,maxScroll());positionBody();return true;}return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){ItemCosmetics.save();}
    @Override public void onClose(){ClientUiAccess.setScreen(minecraft,parent);}
}
