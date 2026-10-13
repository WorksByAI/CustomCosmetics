// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.accessories.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
import static com.customcosmetics.gui.PinkUi.*;

/** A local wardrobe: selecting, resizing and recoloring never starts a remote request. */
public final class WardrobeScreen extends Screen {
    private final Screen parent;private String slot="wings";private int page;private AccessoryCatalog.Collection collection=AccessoryCatalog.Collection.PIXEL;private WardrobeCamera camera=WardrobeCamera.DEFAULT;private boolean turning;
    private final List<AbstractWidget> widgets=new ArrayList<>();
    private int x,y,w,h,contentX,contentW,previewH,optionsTop,colorsY,perPage,styleFooterY;
    public WardrobeScreen(Screen parent){super(Component.literal("Wardrobe"));this.parent=parent;}
    public WardrobeScreen(Screen parent,String slot){this(parent);if(!AccessoryCatalog.known(slot))throw new IllegalArgumentException(slot);this.slot=slot;}
    public WardrobeScreen(Screen parent,String slot,AccessoryCatalog.Collection collection){this(parent,slot);this.collection=Objects.requireNonNull(collection);}
    @Override protected void init(){
        widgets.clear();w=Math.min(740,width-16);h=Math.min(436,height-16);x=(width-w)/2;y=(height-h)/2;
        int side=w<480?84:112;contentX=x+side+20;contentW=w-side-36;
        var categories=AccessoryCatalog.slots(collection);if(categories.stream().noneMatch(category->category.id().equals(slot)))slot=categories.getFirst().id();
        int tabWidth=Math.min(98,(contentW-6)/2);for(var group:AccessoryCatalog.Collection.values())button(contentX+(group.ordinal()*(tabWidth+6)),y+10,tabWidth,24,group.label,()->{collection=group;page=0;rebuildWidgets();}).selected(()->collection==group);
        if(h>=330){int gap=Math.min(26,(h-88)/categories.size());
            for(int i=0;i<categories.size();i++){var category=categories.get(i);button(x+10,y+44+i*gap,side-8,gap-3,category.label(),()->{slot=category.id();page=0;rebuildWidgets();}).selected(()->slot.equals(category.id()));}
        }else{button(x+10,y+58,side-8,24,"‹ Part",()->cycle(-1));button(x+10,y+90,side-8,24,"Part ›",()->cycle(1));}
        previewH=h<240?0:Math.clamp(h-240,56,196);optionsTop=y+46+previewH+8;
        var styles=AccessoryCatalog.styles(slot,collection);int columns=contentW<360?2:3,rows=Math.clamp((h-179-previewH)/27,1,3);perPage=columns*rows-1;page=Math.min(page,(styles.size()-1)/perPage);
        int cell=(contentW-(columns-1)*6)/columns;var current=ConfigManager.accessories.part(slot);
        button(contentX,optionsTop,cell,23,"None",()->{update(p->p.enabled(false));rebuildWidgets();}).selected(()->!ConfigManager.accessories.part(slot).enabled());
        for(int i=0;i<perPage;i++){int index=page*perPage+i;if(index>=styles.size())break;var style=styles.get(index);int cellIndex=i+1;
            button(contentX+(cellIndex%columns)*(cell+6),optionsTop+(cellIndex/columns)*27,cell,23,style.label(),()->{
                var latest=ConfigManager.accessories.part(slot);ConfigManager.accessories=ConfigManager.accessories.with(slot,new AccessoriesSettings.Part(true,latest.scale(),style.color(),style.accent(),latest.speed(),style.id()));changed();rebuildWidgets();
            }).selected(()->{var p=ConfigManager.accessories.part(slot);return p.enabled()&&p.style().equals(style.id());});
        }
        int navigationY=optionsTop+rows*27;styleFooterY=navigationY;button(contentX,navigationY,32,20,"‹",()->{page--;rebuildWidgets();}).active=page>0;
        button(contentX+contentW-32,navigationY,32,20,"›",()->{page++;rebuildWidgets();}).active=(page+1)*perPage<styles.size();
        colorsY=navigationY+26;int fieldW=(contentW-12)/2;
        colorField(contentX,colorsY,fieldW,"Color",false);colorField(contentX+fieldW+12,colorsY,fieldW,"Accent",true);
        int controlsY=colorsY+38;
        boolean garment=Set.of("outfit","arms","pants","feet").contains(slot);
        slider(contentX,controlsY,fieldW,garment?"Fit":"Size",Math.max(AccessoryPlacement.fitted(slot)?1:.5,current.scale()),AccessoryPlacement.fitted(slot)?1:.5,2,.05,v->update(p->p.scale(v)));
        if(slot.equals("wings"))slider(contentX+fieldW+12,controlsY,fieldW,"Flap Speed",current.speed(),0,3,.1,v->update(p->p.speed(v)));
        else button(contentX+fieldW+12,controlsY,fieldW,22,current.enabled()?"Disable Part":"Enable Part",()->{update(p->p.enabled(!p.enabled()));rebuildWidgets();});
        button(x+10,y+h-31,side-8,22,"Clear Outfit",()->{ConfigManager.accessories=AccessoriesSettings.NONE;changed();rebuildWidgets();});
        button(x+w-88,y+h-31,76,22,"Done",this::onClose);
    }
    private void cycle(int direction){var slots=AccessoryCatalog.slots(collection);int index=0;for(int i=0;i<slots.size();i++)if(slots.get(i).id().equals(slot))index=i;slot=slots.get(Math.floorMod(index+direction,slots.size())).id();page=0;rebuildWidgets();}
    private PinkUi.Action button(int x,int y,int w,int h,String label,Runnable action){var button=new PinkUi.Action(x,y,w,h,label,false,()->{CosmeticsScreen.childInteraction();action.run();});button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label)));widgets.add(addWidget(button));return button;}
    private void update(java.util.function.UnaryOperator<AccessoriesSettings.Part> change){ConfigManager.accessories=ConfigManager.accessories.with(slot,change.apply(ConfigManager.accessories.part(slot)));changed();}
    private void changed(){CosmeticsScreen.childInteraction();ConfigManager.requestSave();}
    private void colorField(int x,int y,int w,String title,boolean accent){
        var current=ConfigManager.accessories.part(slot);var field=new PinkUi.Field(font,x+55,y,w-55,title+" Hex");field.setMaxLength(7);field.setValue(AccessoriesSettings.hex(accent?current.accent():current.color()));
        field.setResponder(value->{boolean valid=value.matches("#[0-9a-fA-F]{6}");field.invalid(!valid);if(valid){int c=Integer.parseInt(value.substring(1),16);update(p->accent?p.accent(c):p.color(c));}});widgets.add(addWidget(field));
    }
    private void slider(int x,int y,int w,String title,double value,double min,double max,double step,java.util.function.DoubleConsumer setter){
        widgets.add(addWidget(new PinkUi.Slider(x,y,w,title,value,min,max,step,setter)));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt){if(minecraft.level==null)super.extractBackground(g,mx,my,dt);else g.blurBeforeThisStratum();g.fill(0,0,width,height,0x4020242A);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        outline(g,x,y,w,h,11,BORDER,PANEL);text(g,font,"Wardrobe",x+16,y+16,PINK,contentX-x-32);if(contentW>300)text(g,font,AccessoryCatalog.count(collection)+" styles",contentX+220,y+18,MUTED,contentW-220);
        if(previewH>0){rounded(g,contentX,y+43,contentW,previewH+5,6,CARD);
            int half=contentW/2;CapePreview.drawAccessories(g,contentX,y+44,half,previewH-12,true,camera);CapePreview.drawAccessories(g,contentX+half,y+44,half,previewH-12,false,camera);text(g,font,"Drag to rotate · Scroll to zoom",contentX+10,y+43+previewH-9,MUTED,contentW-20);}
        else{int side=contentX-x-20;rounded(g,x+10,y+122,side-8,h-161,6,CARD);CapePreview.drawAccessories(g,x+10,y+124,side-8,h-165,true,camera);}
        text(g,font,AccessoryCatalog.slot(slot).label()+" · "+(page+1)+" / "+((AccessoryCatalog.styles(slot,collection).size()+perPage-1)/perPage),contentX+40,styleFooterY+5,PINK,contentW-80);
        text(g,font,"Color",contentX,colorsY+9,TEXT,52);text(g,font,"Accent",contentX+(contentW-12)/2+12,colorsY+9,TEXT,52);
        String status=AccessoryPlacement.covered(slot,ConfigManager.accessories)?"Covered by your Head accessory. Selection is kept.":ConfigManager.offlineMode?"Offline · Only you can see this outfit.":"Online · Shared on your normal sync interval.";
        text(g,font,status,contentX,y+h-25,MUTED,contentW-94);
        for(var widget:widgets)widget.extractRenderState(g,mx,my,dt);
    }
    @Override public boolean isPauseScreen(){return false;}
    private boolean insidePreview(double mx,double my){return previewH>0?mx>=contentX&&mx<contentX+contentW&&my>=y+43&&my<y+43+previewH:mx>=x+10&&mx<contentX-20&&my>=y+122&&my<y+h-39;}
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,boolean twice){if(event.button()==0&&insidePreview(event.x(),event.y())){CosmeticsScreen.childInteraction();turning=true;if(twice)camera=WardrobeCamera.DEFAULT;return true;}return super.mouseClicked(event,twice);}
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,double dx,double dy){if(turning){CosmeticsScreen.childInteraction();camera=camera.drag(dx,dy);return true;}return super.mouseDragged(event,dx,dy);}
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event){turning=false;return super.mouseReleased(event);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(insidePreview(mx,my)){CosmeticsScreen.childInteraction();camera=camera.zoom(vertical);return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
    @Override public void onClose(){ConfigManager.requestSave();ClientUiAccess.setScreen(minecraft,parent);}
}
