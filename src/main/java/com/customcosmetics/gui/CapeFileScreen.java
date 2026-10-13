// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.features.cape.*;
import com.customcosmetics.compatibility.ClientUiAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;
import static com.customcosmetics.gui.PinkUi.*;

/** In-game local file picker, also available on SDL versions without TinyFD. */
public final class CapeFileScreen extends Screen {
    private final Screen parent;private final boolean atlas;private Path directory;
    private int x,y,w,h,top,bottom,scroll;private PinkUi.Field pathField;
    private final List<PinkUi.Action> files=new ArrayList<>();private String notice="PNG, JPEG, BMP or GIF · Local files only";
    public CapeFileScreen(Screen parent,boolean atlas){this(parent,atlas,defaultDirectory());}
    public CapeFileScreen(Screen parent,boolean atlas,Path directory){super(Component.literal("Choose Cape Image"));this.parent=parent;this.atlas=atlas;this.directory=directory.toAbsolutePath().normalize();}
    private static Path defaultDirectory(){var home=Path.of(System.getProperty("user.home"));var downloads=home.resolve("Downloads");return Files.isDirectory(downloads)?downloads:home;}
    @Override protected void init(){
        files.clear();w=Math.min(640,width-16);h=Math.min(410,height-16);x=(width-w)/2;y=(height-h)/2;top=y+83;bottom=y+h-62;
        pathField=addRenderableWidget(new PinkUi.Field(font,x+14,y+43,w-130,"Folder"));pathField.setMaxLength(4096);pathField.setValue(directory.toString());
        addRenderableWidget(new PinkUi.Action(x+w-108,y+43,44,24,"Go",false,()->{try{open(Path.of(pathField.getValue()));}catch(InvalidPathException invalid){notice="Invalid folder path.";}}));
        var up=addRenderableWidget(new PinkUi.Action(x+w-58,y+43,44,24,"Up",false,()->open(directory.getParent())));up.active=directory.getParent()!=null;
        addRenderableWidget(new PinkUi.Action(x+14,y+h-29,w-28,22,"Cancel",false,this::onClose));
        try(var stream=Files.list(directory)){
            var entries=stream.filter(p->Files.isDirectory(p)||p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|jpeg|bmp|gif)$"))
                .sorted(Comparator.comparing((Path p)->!Files.isDirectory(p)).thenComparing(p->p.getFileName().toString().toLowerCase(Locale.ROOT))).limit(500).toList();
            for(var path:entries){boolean folder=Files.isDirectory(path);String label=(folder?"[Folder] ":"")+path.getFileName();
                var button=addWidget(new PinkUi.Action(x+14,top+files.size()*28,w-28,24,label,false,()->{if(folder)open(path);else select(path);}));files.add(button);
            }
        }catch(Exception error){notice="Cannot read folder: "+error.getMessage();}
        scroll=Math.clamp(scroll,0,maxScroll());position();
    }
    private void open(Path next){if(next==null)return;try{if(!Files.isDirectory(next)){notice="Folder does not exist.";return;}directory=next.toAbsolutePath().normalize();scroll=0;rebuildWidgets();}catch(Exception error){notice="Cannot open folder.";}}
    private void select(Path path){try{var image=CapeImages.read(path);if(atlas){CapeCosmetics.saveLocal(CapeImages.atlasPng(image));ClientUiAccess.setScreen(minecraft,parent);}else ClientUiAccess.setScreen(minecraft,new CapeCropScreen(parent,image));}catch(Exception error){notice=error.getMessage()==null?"Could not open image.":error.getMessage();}}
    private int maxScroll(){return Math.max(0,files.size()*28-(bottom-top));}
    private void position(){for(int i=0;i<files.size();i++){var file=files.get(i);file.setY(top+i*28-scroll);file.visible=file.getBottom()>top&&file.getY()<bottom;}}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(mx>=x&&mx<x+w&&my>=top&&my<bottom){scroll=Math.clamp(scroll-(int)(vertical*28),0,maxScroll());position();return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        outline(g,x,y,w,h,10,BORDER,PANEL);text(g,font,atlas?"Choose Cape + Elytra":"Choose Image to Crop",x+14,y+15,PINK,w-28);position();
        g.enableScissor(x+10,top,x+w-10,bottom);for(var file:files)if(file.visible)file.extractRenderState(g,mx,my,dt);g.disableScissor();
        text(g,font,notice,x+14,y+h-50,MUTED,w-28);super.extractRenderState(g,mx,my,dt);
    }
    @Override public void onClose(){CosmeticsScreen.childInteraction();ClientUiAccess.setScreen(minecraft,parent);}
    @Override public boolean isPauseScreen(){return false;}
}
