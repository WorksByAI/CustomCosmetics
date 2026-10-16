// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.compatibility.*;
import com.customcosmetics.features.skins.LocalSkins;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.List;
import static com.customcosmetics.gui.PinkUi.*;
public final class SkinImportScreen extends Screen {
    private final Screen parent;private PinkUi.Field path;private int x,y,w;private String notice="PNG · Modern 64–1024 square, or legacy 64x32 · Up to 2 MiB";private boolean loading;
    public SkinImportScreen(Screen parent){super(Component.literal("Import Local Skin"));this.parent=parent;}
    @Override protected void init(){w=Math.min(620,width-16);x=(width-w)/2;y=(height-164)/2;path=addRenderableWidget(new PinkUi.Field(font,x+12,y+43,w-120,"Skin PNG path"));path.setMaxLength(4096);path.setHint(Component.literal("Paste PNG path or drop a file"));
        addRenderableWidget(new PinkUi.Action(x+w-100,y+43,88,24,"Browse",false,this::browse));
        addRenderableWidget(new PinkUi.Action(x+12,y+99,(w-30)/2,24,"Import",true,()->{try{load(Path.of(path.getValue().strip().replaceAll("^\"|\"$","")));}catch(RuntimeException error){notice="Invalid file path.";}}));
        addRenderableWidget(new PinkUi.Action(x+18+(w-30)/2,y+99,(w-30)/2,24,"Cancel",false,this::onClose));
    }
    private void browse(){if(loading)return;boolean sdl=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString().startsWith("26.3");long window=0;
        if(sdl)try{window=(long)minecraft.getWindow().getClass().getMethod("handle").invoke(minecraft.getWindow());}catch(ReflectiveOperationException unavailable){notice="Paste the PNG path or drop the file here.";return;}
        NativeFileDialogs.open(new NativeFileDialogs.Request(true,CapeFileScreen.defaultDirectory(),sdl,window,"skin"),minecraft::execute,result->{if(ClientUiAccess.screen(minecraft)!=this)return;if(result.path()!=null){path.setValue(result.path().toString());load(result.path());}else if(result.unavailable())notice="Paste the PNG path or drop the file here.";else notice=result.error();});
    }
    private void load(Path file){if(loading)return;loading=true;notice="Importing local PNG...";LocalSkins.importFile(file).whenComplete((ignored,error)->minecraft.execute(()->{if(ClientUiAccess.screen(minecraft)!=this)return;loading=false;if(error!=null){var cause=error instanceof java.util.concurrent.CompletionException?error.getCause():error;notice=cause.getMessage();}else ClientUiAccess.setScreen(minecraft,parent);}));}
    @Override public void onFilesDrop(List<Path> paths){if(!paths.isEmpty()){path.setValue(paths.getFirst().toString());load(paths.getFirst());}}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){outline(g,x,y,w,164,10,BORDER,PANEL);text(g,font,"Import Local Skin",x+12,y+14,PINK,w-24);text(g,font,notice,x+12,y+78,MUTED,w-24);text(g,font,"Imported locally. Enable P2P sharing separately in Skins.",x+12,y+140,MUTED,w-24);super.extractRenderState(g,mx,my,dt);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){ClientUiAccess.setScreen(minecraft,parent);}
}
