// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.features.items.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import static com.customcosmetics.gui.PinkUi.*;

/** Apply and Done save drafts; actual inventory components are never changed. */
public final class ItemAppearanceScreen extends ThemedScreen {
    private final Screen parent;
    private final List<AbstractWidget> controls=new ArrayList<>();
    private final List<Placement> body=new ArrayList<>();
    private record Placement(AbstractWidget widget,int y){}
    private ItemStack item=ItemStack.EMPTY;
    private String uuid="",draftName="",draftModel="",draftArmorModel="",draftArmorColor="",draftHeadTexture="",status="";
    private List<HeadSkinCatalog.Entry> headEntries;
    private List<ItemChoiceScreen.Choice> headChoices=List.of();
    private ItemStack previewSource,previewCache;
    private String previewModelKey="",previewHeadKey="";
    private Object previewCatalog;
    private int x,y,w,h,previewWidth,scroll,bodyTop,bodyBottom;
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;
    private PinkUi.Field nameField,modelField,armorModelField,armorColorField,headTextureField;
    private PinkUi.ColorPicker armorPicker;
    public ItemAppearanceScreen(Screen parent){
        super(Component.literal("Item Customization"));this.parent=parent;
        var player=net.minecraft.client.Minecraft.getInstance().player;
        if(player!=null&&!ItemCosmetics.uuid(player.getMainHandItem()).isEmpty())select(player.getMainHandItem());
    }
    private void select(ItemStack stack){
        item=stack.copy();uuid=ItemCosmetics.uuid(item);
        var appearance=ItemCosmetics.store().get(uuid);
        draftName=appearance.name();draftModel=appearance.model();draftArmorModel=appearance.armorModel();
        draftHeadTexture=appearance.headTexture();
        draftArmorColor=appearance.armorColor()==null?"":hex(appearance.armorColor());status="";scroll=0;
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
        modelField.setHint(Component.literal("e.g. golden_axe or namespace:model"));modelField.setResponder(value->{draftModel=value;status="";CosmeticsScreen.childInteraction();});body.add(new Placement(addWidget(modelField),modelField.getY()));
        var selectModel=action(fx,y+209,fw,"Select Model",this::chooseModel);controls.remove(selectModel);body.add(new Placement(selectModel,selectModel.getY()));
        headTextureField=new PinkUi.Field(font,fx,y+266,fw,"Head Skin");headTextureField.setMaxLength(8192);
        headTextureField.setValue(draftHeadTexture);headTextureField.setHint(Component.literal("Texture URL, hash or Base64 (empty = original)"));
        headTextureField.setResponder(value->{draftHeadTexture=value;status="";CosmeticsScreen.childInteraction();});
        body.add(new Placement(addWidget(headTextureField),headTextureField.getY()));
        int headHalf=(fw-6)/2;
        var selectHead=action(fx,y+301,headHalf,"Select Head Skin",this::chooseHeadSkin);
        controls.remove(selectHead);body.add(new Placement(selectHead,selectHead.getY()));
        var clearHead=action(fx+headHalf+6,y+301,fw-headHalf-6,"Original Head Skin",()->{draftHeadTexture="";headTextureField.setValue("");});
        controls.remove(clearHead);body.add(new Placement(clearHead,clearHead.getY()));
        headTextureField.active=selectHead.active=clearHead.active=!uuid.isEmpty();
        armorModelField=new PinkUi.Field(font,fx,y+358,fw,"Armor Model ID");armorModelField.setMaxLength(256);
        armorModelField.setValue(draftArmorModel);armorModelField.setHint(Component.literal("e.g. minecraft:leather"));
        armorModelField.setResponder(value->{draftArmorModel=value;status="";CosmeticsScreen.childInteraction();});
        body.add(new Placement(addWidget(armorModelField),armorModelField.getY()));
        int half=(fw-6)/2;
        var leather=action(fx,y+388,half,"Use Leather",()->{draftHeadTexture="";headTextureField.setValue("");draftArmorModel="minecraft:leather";armorModelField.setValue(draftArmorModel);});
        controls.remove(leather);body.add(new Placement(leather,leather.getY()));
        var original=action(fx+half+6,y+388,fw-half-6,"Original Armor",()->{draftArmorModel="";armorModelField.setValue("");});
        controls.remove(original);body.add(new Placement(original,original.getY()));
        armorColorField=new PinkUi.Field(font,fx,y+452,fw,"Armor Color");armorColorField.setMaxLength(7);
        armorColorField.setValue(draftArmorColor);armorColorField.setHint(Component.literal("Empty = original color"));
        armorColorField.setResponder(value->{
            draftArmorColor=value;status="";
            if(value.matches("#?[0-9a-fA-F]{6}"))armorPicker.setColor(Integer.parseInt(value.replace("#",""),16));
            CosmeticsScreen.childInteraction();
        });
        body.add(new Placement(addWidget(armorColorField),armorColorField.getY()));
        armorPicker=new PinkUi.ColorPicker(fx,y+483,fw,82,draftArmorColor.matches("#?[0-9a-fA-F]{6}")
            ?Integer.parseInt(draftArmorColor.replace("#",""),16):0xA06540,
            color->armorColorField.setValue(hex(color)));
        body.add(new Placement(addWidget(armorPicker),armorPicker.getY()));
        int part=(w-44)/3;
        action(x+16,y+h-38,part,"Reset Item",this::reset);
        action(x+22+part,y+h-38,part,"Apply",this::apply);
        action(x+28+part*2,y+h-38,part,"Done",this::done);
        nameField.active=modelField.active=!uuid.isEmpty();
        boolean armorEditable=!uuid.isEmpty()&&ArmorAppearance.editable(item);
        armorModelField.active=armorColorField.active=armorPicker.active=leather.active=original.active=armorEditable;
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
        var choices=ItemCosmetics.modelChoices(item).stream()
            .map(value->new ItemChoiceScreen.Choice(value.label(),value.id().toString(),value.icon())).toList();
        ClientUiAccess.setScreen(minecraft,new ItemChoiceScreen(this,"Select Model",choices,choice->{draftHeadTexture="";draftModel=choice.id();rebuildWidgets();}));
    }
    private List<ItemChoiceScreen.Choice> headChoices(){
        var current=HeadSkins.entries();
        if(current!=headEntries){headEntries=current;headChoices=current.stream()
            .map(entry->new ItemChoiceScreen.Choice(entry.name(),entry.id(),()->HeadSkins.icon(entry.texture()))).toList();}
        return headChoices;
    }
    private void chooseHeadSkin(){
        if(uuid.isEmpty()){status="Select an item with a unique SkyBlock item UUID.";return;}
        HeadSkins.requestCatalog();
        ClientUiAccess.setScreen(minecraft,new ItemChoiceScreen(this,"Select Head Skin",this::headChoices,choice->{
            var entry=HeadSkins.entries().stream().filter(value->value.id().equals(choice.id())).findFirst().orElseThrow();
            draftHeadTexture=entry.texture();draftModel=draftArmorModel=draftArmorColor="";rebuildWidgets();
        },HeadSkins::notice));
    }
    private boolean apply(){
        if(uuid.isEmpty()){status="Select an item with a unique SkyBlock item UUID.";return false;}
        try{
            boolean armorEditable=ArmorAppearance.editable(item);
            String armorModel=draftArmorModel.strip();
            String headTexture=HeadTexture.normalize(draftHeadTexture);
            String rgb=draftArmorColor.strip();
            Integer color=rgb.isEmpty()?null:rgb.matches("#?[0-9a-fA-F]{6}")?Integer.parseInt(rgb.replace("#",""),16):null;
            if(!rgb.isEmpty()&&color==null){armorColorField.invalid(true);status="Use a six-digit armor color such as #FF8844.";return false;}
            if((!armorModel.isEmpty()||color!=null)&&!armorEditable){status="Armor options require a head or armor item.";return false;}
            if(!headTexture.isEmpty()&&(!armorModel.isEmpty()||color!=null)){status="Choose either a head skin or an armor model / color.";return false;}
            if(!armorModel.isEmpty()&&!ArmorAppearance.available(armorModel,ArmorAppearance.slot(item))){
                armorModelField.invalid(true);status="Armor asset has no usable model for this slot.";return false;
            }
            if(item.is(net.minecraft.world.item.Items.PLAYER_HEAD)&&color!=null&&armorModel.isEmpty()){
                status="Choose an armor model for a player head before setting its color.";return false;
            }
            var value=new ItemAppearanceStore.Appearance(draftName,headTexture.isEmpty()?draftModel:"",armorModel,color,headTexture);
            if(value.headTexture().isEmpty()&&!value.model().isEmpty()&&!ItemCosmetics.renderable(item,Identifier.tryParse(value.model()))){modelField.invalid(true);status="This model has no usable texture. Choose another model or clear the field.";return false;}
            ItemCosmetics.store().put(uuid,value);modelField.invalid(false);armorModelField.invalid(false);armorColorField.invalid(false);
            boolean saved=ItemCosmetics.save();
            status=saved?"Saved. Only visible on your client.":"Could not save. Please try again.";
            return saved;
        }catch(IllegalArgumentException|IllegalStateException error){status=error.getMessage();return false;}
    }
    private void done(){
        if(uuid.isEmpty()||apply())onClose();
    }
    private void reset(){
        if(uuid.isEmpty())return;
        ItemCosmetics.store().put(uuid,new ItemAppearanceStore.Appearance("",""));ItemCosmetics.save();
        draftName=draftModel=draftArmorModel=draftArmorColor=draftHeadTexture="";rebuildWidgets();status="Original appearance restored.";
    }
    private ItemStack preview(){
        Object catalog=ItemCosmetics.catalogIdentity();
        if(previewSource==item&&previewCache!=null&&previewModelKey.equals(draftModel)&&previewHeadKey.equals(draftHeadTexture)&&previewCatalog==catalog)return previewCache;
        previewSource=item;previewModelKey=draftModel;previewHeadKey=draftHeadTexture;previewCatalog=catalog;
        String texture="";try{texture=HeadTexture.normalize(draftHeadTexture);}catch(IllegalArgumentException ignored){}
        if(!texture.isEmpty())previewCache=HeadSkins.preview(item,texture);
        else{var model=Identifier.tryParse(draftModel.strip());previewCache=ItemCosmetics.preview(item,ItemCosmetics.renderable(item,model)?model:null);}
        return previewCache;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        outline(g,x,y,w,h,11,BORDER,PANEL);text(g,font,"Item Customization",x+16,y+17,PINK,w-32);
        for(var widget:controls)widget.extractRenderState(g,mx,my,delta);
        g.enableScissor(x+15,bodyTop,x+w-15,bodyBottom);g.pose().pushMatrix();g.pose().translate(0,-scroll);
        int fx=x+previewWidth+24,fw=w-previewWidth-40;
        outline(g,x+16,y+85,previewWidth-8,154,6,BORDER,CARD);text(g,font,"Preview",x+26,y+96,PINK,previewWidth-28);
        if(!item.isEmpty()){
            var preview=preview();
            g.pose().pushMatrix();g.pose().translate(x+16+(previewWidth-8)/2f-24,y+130);g.pose().scale(3,3);g.item(preview,0,0);g.pose().popMatrix();
            Component name=draftName.isEmpty()?preview.getHoverName():ItemNameText.cached(draftName);
            float scale=Math.min(1,(previewWidth-28)/(float)Math.max(1,font.width(name)));
            g.pose().pushMatrix();g.pose().translate(x+26,y+214);g.pose().scale(scale,scale);g.text(font,name,0,0,TEXT,false);g.pose().popMatrix();
        }
        text(g,font,"Custom Name",fx,y+85,TEXT,fw);text(g,font,"Colors: &0–&f, &r, &#RRGGBB",fx,y+135,MUTED,fw);
        text(g,font,"Model ID",fx,y+159,TEXT,fw);
        text(g,font,"Head Skin (item and worn appearance)",fx,y+250,TEXT,fw);
        text(g,font,"Worn Armor Model ID",fx,y+342,TEXT,fw);
        text(g,font,"Armor Color (#RRGGBB)",fx,y+436,TEXT,fw);
        if(ArmorAppearance.editable(item)&&!draftArmorColor.isEmpty()&&draftArmorColor.matches("#?[0-9a-fA-F]{6}"))
            rounded(g,x+24,y+229,24,8,2,0xFF000000|Integer.parseInt(draftArmorColor.replace("#",""),16));
        g.pose().popMatrix();for(var placement:body)if(placement.widget().visible)placement.widget().extractRenderState(g,mx,my,delta);g.disableScissor();
        if(maxScroll()>0){var bar=scrollbar();rounded(g,x+w-10,bodyTop,3,bodyBottom-bodyTop,1,BORDER);rounded(g,x+w-10,bar.thumbStart(scroll),3,bar.thumbLength(),1,PINK);}
        text(g,font,status.isEmpty()?(uuid.isEmpty()?"Select a SkyBlock item with a unique item UUID.":"Apply saves changes; Done saves and closes."):status,x+16,y+h-61,MUTED,w-32);
    }
    private int maxScroll(){return Math.max(0,y+574-bodyBottom);}
    private ScrollBarLayout scrollbar(){int viewport=Math.max(0,bodyBottom-bodyTop);return ScrollBarLayout.of(bodyTop,viewport,viewport,viewport+maxScroll(),maxScroll());}
    private void positionBody(){for(var p:body){p.widget().setY(p.y()-scroll);p.widget().visible=p.widget().getY()+p.widget().getHeight()>bodyTop&&p.widget().getY()<bodyBottom;}}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        CosmeticsScreen.childInteraction();if(mx>=x&&mx<x+w&&my>=bodyTop&&my<bodyBottom){scroll=Math.clamp(scroll-(int)Math.signum(vertical)*24,0,maxScroll());positionBody();return true;}return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(event.button()==com.customcosmetics.compatibility.ClientInput.MOUSE_LEFT&&maxScroll()>0
                &&event.x()>=x+w-18&&event.x()<x+w&&event.y()>=bodyTop&&event.y()<bodyBottom){
            var bar=scrollbar();int thumbTop=bar.thumbStart(scroll);
            scrollbarGrabOffset=event.y()>=thumbTop&&event.y()<thumbTop+bar.thumbLength()?(int)event.y()-thumbTop:bar.thumbLength()/2;
            scroll=bar.offsetAtPointer(event.y(),scrollbarGrabOffset);positionBody();draggingScrollbar=true;CosmeticsScreen.childInteraction();return true;
        }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(draggingScrollbar){scroll=scrollbar().offsetAtPointer(event.y(),scrollbarGrabOffset);positionBody();return true;}
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(draggingScrollbar){draggingScrollbar=false;return true;}
        return super.mouseReleased(event);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){ItemCosmetics.save();}
    @Override public void onClose(){ClientUiAccess.setScreen(minecraft,parent);}
    private static String hex(int color){return String.format(Locale.ROOT,"#%06X",color&0xFFFFFF);}
}
