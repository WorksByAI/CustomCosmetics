// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.compatibility.*;
import com.customcosmetics.features.map.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static com.customcosmetics.gui.PinkUi.*;

public final class FmeImportScreen extends Screen {
    private final Screen parent;private PinkUi.Field path;private PinkUi.Action apply;private FmeImport.Plan plan;
    private int x,y,w,h;private boolean busy,replace;private String notice="Choose any FME JSON file, or paste the old FME folder path.";
    public FmeImportScreen(Screen parent){super(Component.literal("Import FME Config"));this.parent=parent;}
    @Override protected void init(){
        w=Math.min(640,width-16);h=Math.min(258,height-16);x=(width-w)/2;y=(height-h)/2;
        path=addRenderableWidget(new PinkUi.Field(font,x+12,y+40,w-116,"FME config path"));path.setMaxLength(4096);path.setHint(Component.literal("FME JSON file or folder"));path.setResponder(v->{plan=null;if(apply!=null)apply.active=false;});
        addRenderableWidget(new PinkUi.Action(x+w-96,y+40,84,24,"Browse",false,this::browse));
        addRenderableWidget(new PinkUi.Action(x+12,y+72,w-24,22,"Replace Existing Room Layouts: OFF",false,()->{replace=!replace;for(var child:children())if(child instanceof PinkUi.Action button&&button.getMessage().getString().startsWith("Replace Existing"))button.setMessage(Component.literal("Replace Existing Room Layouts: "+(replace?"ON":"OFF")));}).selected(()->replace));
        addRenderableWidget(new PinkUi.Action(x+12,y+h-32,(w-36)/3,22,"Preview",false,this::preview));
        apply=addRenderableWidget(new PinkUi.Action(x+18+(w-36)/3,y+h-32,(w-36)/3,22,"Import",true,this::apply));apply.active=false;
        addRenderableWidget(new PinkUi.Action(x+24+2*((w-36)/3),y+h-32,(w-36)/3,22,"Back",false,this::onClose));
    }
    private void browse(){if(busy)return;boolean sdl=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString().startsWith("26.3");long window=0;if(sdl)try{window=(long)minecraft.getWindow().getClass().getMethod("handle").invoke(minecraft.getWindow());}catch(ReflectiveOperationException error){notice="Paste the FME folder path or drop a JSON file here.";return;}
        NativeFileDialogs.open(new NativeFileDialogs.Request(false,CapeFileScreen.defaultDirectory(),sdl,window,"fme"),minecraft::execute,result->{if(ClientUiAccess.screen(minecraft)!=this)return;if(result.path()!=null){path.setValue(result.path().toString());preview();}else if(result.unavailable())notice="Paste the FME folder path or drop a JSON file here.";else notice=result.error();});
    }
    private void preview(){if(busy)return;Path selected;try{selected=Path.of(path.getValue().strip().replaceAll("^\"|\"$",""));}catch(RuntimeException error){notice="Invalid file path.";return;}busy=true;plan=null;apply.active=false;notice="Reading local FME config...";
        CompletableFuture.supplyAsync(()->{try{return FmeImport.read(selected,id->{String converted=LegacyMaterials.convert(id);var state=MapEditor.state(converted);if(state==null||state.hasBlockEntity()||!state.getFluidState().isEmpty())throw new IllegalArgumentException("Unsupported block");return converted;},id->{String converted=LegacyMaterials.favorite(id);int b=converted.indexOf('[');if(b>=0)converted=converted.substring(0,b);if(!MapEditor.selectable(converted))throw new IllegalArgumentException("Unsupported favorite");return converted;});}catch(Exception error){throw new java.util.concurrent.CompletionException(error);}}).whenComplete((result,error)->minecraft.execute(()->{if(ClientUiAccess.screen(minecraft)!=this)return;busy=false;if(error!=null){notice=error.getCause()==null?error.getMessage():error.getCause().getMessage();return;}plan=result;apply.active=!result.layouts().isEmpty()||!result.favorites().isEmpty();notice="Layouts: "+result.layouts().size()+" · Blocks: "+result.blocks()+" · Favorites: "+result.favorites().size()+" · Skipped: "+result.skipped();}));
    }
    private void apply(){if(plan==null||busy)return;busy=true;apply.active=false;notice="Importing in the background…";try{MapEditor.importFmeInteractive(plan,replace).whenComplete((message,error)->{busy=false;if(error==null){notice=message;MapEditor.notice(message);plan=null;}else{while(error.getCause()!=null)error=error.getCause();notice="Import failed: "+error.getMessage();apply.active=true;}});}catch(Exception error){busy=false;notice="Import failed: "+error.getMessage();apply.active=true;}}
    @Override public void onFilesDrop(List<Path> paths){if(!paths.isEmpty()){path.setValue(paths.getFirst().toString());preview();}}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){outline(g,x,y,w,h,10,BORDER,PANEL);text(g,font,getTitle().getString(),x+12,y+14,PINK,w-24);int yy=y+105;for(var line:font.split(Component.literal(notice),w-24)){if(yy>y+h-44)break;g.text(font,line,x+12,yy,TEXT,false);yy+=11;}if(plan!=null)for(var warning:plan.warnings()){if(yy>y+h-44)break;text(g,font,warning,x+12,yy,0xFFFFCA7A,w-24);yy+=11;}super.extractRenderState(g,mx,my,dt);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){ClientUiAccess.setScreen(minecraft,parent);}
}
