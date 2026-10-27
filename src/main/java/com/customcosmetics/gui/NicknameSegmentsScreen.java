// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.nickname.NicknameSegments;
import com.customcosmetics.utils.NickRenderUtils;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import static com.customcosmetics.gui.PinkUi.*;

/** Local drafts with a live preview. Apply saves settings for the normal synchronization cycle. */
public final class NicknameSegmentsScreen extends ThemedScreen {
    private final Screen parent;
    private final List<Draft> drafts=new ArrayList<>();
    private final List<AbstractWidget> fixed=new ArrayList<>();
    private final List<Placement> body=new ArrayList<>();
    private record Placement(AbstractWidget widget,int y){}
    private static String hex(int value){return String.format(Locale.ROOT,"#%06X",value&0xFFFFFF);}
    private static final class Draft {
        String from,to,start,end;
        Draft(NicknameSegments.Segment segment){from=""+segment.from();to=""+segment.to();start=hex(segment.start());end=hex(segment.end());}
        NicknameSegments.Segment value(){
            if(!from.matches("[0-9]{1,4}")||!to.matches("[0-9]{1,4}")||!start.matches("#?[0-9a-fA-F]{6}")||!end.matches("#?[0-9a-fA-F]{6}"))
                throw new IllegalArgumentException("Enter character positions and #RRGGBB colors for every segment.");
            return new NicknameSegments.Segment(Integer.parseInt(from),Integer.parseInt(to),Integer.parseInt(start.replace("#",""),16),Integer.parseInt(end.replace("#",""),16));
        }
    }
    private int x,y,w,h,top,bottom,scroll,grab;
    private boolean dragging;
    private String status="";
    public NicknameSegmentsScreen(Screen parent){
        super(Component.literal("Gradient Segments"));this.parent=parent;
        for(var segment:NicknameSegments.normalize(ConfigManager.nickTweaksGradientSegments))drafts.add(new Draft(segment));
        if(drafts.isEmpty()){
            int length=Math.max(1,visibleName().codePointCount(0,visibleName().length())),split=Math.max(1,length/2);
            drafts.add(new Draft(new NicknameSegments.Segment(1,split,0xED69E8,0x916BE8)));
            if(split<length)drafts.add(new Draft(new NicknameSegments.Segment(split+1,length,0x43D6EC,0x549FF5)));
        }
    }
    private String rawName(){
        String value=ConfigManager.nickTweaksNickChanger;
        if(value==null||value.isBlank()){
            var client=net.minecraft.client.Minecraft.getInstance();value=client.player==null?"ToryHi":client.player.getName().getString();
        }return value;
    }
    private String visibleName(){return NickRenderUtils.visibleNickname(rawName());}
    @Override protected void init(){
        fixed.clear();body.clear();w=Math.min(620,width-16);h=Math.min(436,height-16);x=(width-w)/2;y=(height-h)/2;
        top=y+109;bottom=y+h-74;scroll=Math.clamp(scroll,0,maxScroll());
        int cardWidth=w-48,rangeWidth=Math.max(36,(cardWidth-60)/6),colorWidth=Math.max(62,(cardWidth-50-2*rangeWidth)/2);
        for(int i=0;i<drafts.size();i++){
            Draft draft=drafts.get(i);int row=top+i*105;
            addField(x+24,row+45,rangeWidth,"From",draft.from,value->draft.from=value,4);
            addField(x+32+rangeWidth,row+45,rangeWidth,"To",draft.to,value->draft.to=value,4);
            addField(x+40+rangeWidth*2,row+45,colorWidth,"Start Color",draft.start,value->draft.start=value,7);
            addField(x+48+rangeWidth*2+colorWidth,row+45,colorWidth,"End Color",draft.end,value->draft.end=value,7);
            var remove=addWidget(new PinkUi.Action(x+w-90,row+7,58,23,"Remove",false,()->{drafts.remove(draft);status="";CosmeticsScreen.childInteraction();rebuildWidgets();}));body.add(new Placement(remove,remove.getY()));
        }
        int third=(w-44)/3;
        var add=addWidget(new PinkUi.Action(x+16,y+h-38,third,26,"Add Segment",false,()->{
            if(drafts.size()>=NicknameSegments.MAX_SEGMENTS){status="You can use up to 16 segments.";return;}
            int next=validDrafts().stream().mapToInt(NicknameSegments.Segment::to).max().orElse(0)+1;
            if(next>NicknameSegments.MAX_POSITION){status="No more character positions available.";return;}
            int count=visibleName().codePointCount(0,visibleName().length());drafts.add(new Draft(new NicknameSegments.Segment(next,Math.max(next,count),0xFFFFFF,0xFFFFFF)));
            CosmeticsScreen.childInteraction();rebuildWidgets();scroll=maxScroll();positionBody();
        }));fixed.add(add);add.active=drafts.size()<NicknameSegments.MAX_SEGMENTS;
        fixed.add(addWidget(new PinkUi.Action(x+22+third,y+h-38,third,26,"Apply Segments",true,this::apply)));
        fixed.add(addWidget(new PinkUi.Action(x+28+third*2,y+h-38,third,26,"Cancel",false,this::onClose)));positionBody();
    }
    private void addField(int bx,int by,int width,String label,String value,Consumer<String> setter,int max){
        var field=new PinkUi.Field(font,bx,by,width,label);field.setMaxLength(max);field.setValue(value);
        field.setResponder(text->{setter.accept(text);status="";CosmeticsScreen.childInteraction();
            field.invalid(label.contains("Color")?!text.matches("#?[0-9a-fA-F]{6}"):!text.matches("[0-9]{1,4}")||Integer.parseInt(text)<1||Integer.parseInt(text)>NicknameSegments.MAX_POSITION);
        });body.add(new Placement(addWidget(field),field.getY()));
    }
    private List<NicknameSegments.Segment> validDrafts(){
        var result=new ArrayList<NicknameSegments.Segment>();for(var draft:drafts)try{result.add(draft.value());}catch(IllegalArgumentException ignored){}
        return NicknameSegments.normalize(result);
    }
    private void apply(){
        try{
            var segments=NicknameSegments.validate(drafts.stream().map(Draft::value).toList());
            ConfigManager.nickTweaksGradientSegments=segments;ConfigManager.nickTweaksGradientStyle="segmented";
            ConfigManager.nickTweaksEnabled=true;ConfigManager.nickTweaksCustomNickColorEnabled=true;ConfigManager.nickTweaksChromaEnabled=false;
            ConfigManager.requestSave();NickRenderUtils.invalidateLocalTargetsCache();CosmeticsScreen.childInteraction();onClose();
        }catch(IllegalArgumentException error){status=error.getMessage();}
    }
    private int maxScroll(){return Math.max(0,drafts.size()*105-(bottom-top));}
    private ScrollBarLayout bar(){int visible=Math.max(0,bottom-top);return ScrollBarLayout.of(top,visible,visible,Math.max(visible,drafts.size()*105),maxScroll());}
    private void positionBody(){for(var placement:body){var widget=placement.widget();widget.setY(placement.y()-scroll);widget.visible=widget.getY()+widget.getHeight()>top&&widget.getY()<bottom;}}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        NickRenderUtils.beginClickGuiRenderScope();
        try{
            outline(g,x,y,w,h,11,BORDER,PANEL);text(g,font,"Gradient Segments",x+16,y+17,PINK,w-32);
            Component preview=NickRenderUtils.previewSegments(rawName(),validDrafts(),ConfigManager.nickTweaksGradientStartColor,ConfigManager.nickTweaksGradientEndColor);
            float scale=Math.min(2,(w-40)/(float)Math.max(1,font.width(preview)));
            g.pose().pushMatrix();g.pose().translate(x+(w-font.width(preview)*scale)/2f,y+45);g.pose().scale(scale,scale);g.text(font,preview,0,0,TEXT,false);g.pose().popMatrix();
            text(g,font,"Use visible character positions, starting at 1.",x+16,y+80,MUTED,w-32);
            text(g,font,"Color codes take priority. Unlisted characters use the base gradient.",x+16,y+94,DIM,w-32);
            g.enableScissor(x+15,top,x+w-15,bottom);g.pose().pushMatrix();g.pose().translate(0,-scroll);
            int cardWidth=w-48,rangeWidth=Math.max(36,(cardWidth-60)/6),colorWidth=Math.max(62,(cardWidth-50-2*rangeWidth)/2);
            for(int i=0;i<drafts.size();i++){
                int row=top+i*105;outline(g,x+16,row,w-40,96,6,BORDER,CARD);text(g,font,"Segment "+(i+1),x+26,row+14,PINK,w-132);
                text(g,font,"From",x+24,row+32,MUTED,rangeWidth);text(g,font,"To",x+32+rangeWidth,row+32,MUTED,rangeWidth);
                text(g,font,"Start Color",x+40+rangeWidth*2,row+32,MUTED,colorWidth);text(g,font,"End Color",x+48+rangeWidth*2+colorWidth,row+32,MUTED,colorWidth);
                try{var value=drafts.get(i).value();gradient(g,x+26,row+84,w-64,4,value.start(),value.end());}catch(IllegalArgumentException ignored){}
            }
            g.pose().popMatrix();for(var placement:body)if(placement.widget().visible)placement.widget().extractRenderState(g,mx,my,delta);g.disableScissor();
            if(maxScroll()>0){var bar=bar();rounded(g,x+w-13,top,3,bottom-top,1,BORDER);rounded(g,x+w-13,bar.thumbStart(scroll),3,bar.thumbLength(),1,PINK);}
            for(var widget:fixed)widget.extractRenderState(g,mx,my,delta);
            text(g,font,status.isEmpty()?"Apply enables segmented colors and turns Chroma off.":status,x+16,y+h-60,status.isEmpty()?DIM:PINK,w-32);
        }finally{NickRenderUtils.endClickGuiRenderScope();}
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(mx>=x&&mx<x+w&&my>=top&&my<bottom){scroll=Math.clamp(scroll-(int)(vertical*24),0,maxScroll());positionBody();CosmeticsScreen.childInteraction();return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(event.button()==com.customcosmetics.compatibility.ClientInput.MOUSE_LEFT&&maxScroll()>0&&event.x()>=x+w-22&&event.x()<x+w-3&&event.y()>=top&&event.y()<bottom){
            var bar=bar();int thumb=bar.thumbStart(scroll);grab=event.y()>=thumb&&event.y()<thumb+bar.thumbLength()?(int)event.y()-thumb:bar.thumbLength()/2;
            scroll=bar.offsetAtPointer(event.y(),grab);positionBody();dragging=true;CosmeticsScreen.childInteraction();return true;
        }
        if(event.y()<top||event.y()>=bottom)for(var placement:body)placement.widget().visible=false;
        try{return super.mouseClicked(event,twice);}finally{positionBody();}
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(dragging){scroll=bar().offsetAtPointer(event.y(),grab);positionBody();CosmeticsScreen.childInteraction();return true;}return super.mouseDragged(event,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent event){if(dragging){dragging=false;return true;}return super.mouseReleased(event);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){ClientUiAccess.setScreen(minecraft,parent);}
}
