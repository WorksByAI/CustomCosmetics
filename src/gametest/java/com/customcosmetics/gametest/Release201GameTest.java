// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.*;
import com.customcosmetics.gui.*;
import com.customcosmetics.update.ClientFlavor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Exercise real rendered depth changes, native drop submission and nofont fallback. */
public final class Release201GameTest implements FabricClientGameTest {
    public static volatile boolean trackDrops;
    public static volatile float dropRatio;
    public static volatile int dropDraws;
    public void runTest(ClientGameTestContext context){
        context.runOnClient(c->{ConfigManager.syncEnabled=false;ConfigManager.offlineMode=true;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.uiAnimationsEnabled=false;ConfigManager.uiThemeMode="dark";ConfigManager.uiFont="modern";ConfigManager.smolpeopleMode=false;ConfigManager.playerSizeEnabled=true;ConfigManager.playerSizeX=ConfigManager.playerSizeY=ConfigManager.playerSizeZ=1;ConfigManager.creatureModel="player";FeatureModules.all(true);FeatureModules.set(FeatureModules.Feature.ITEMS,false);});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("gamemode survival @a");world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear");
            for(String piece:new String[]{"chest.chestplate","legs.leggings","feet.boots"}){var p=piece.split("\\.");world.getServer().runCommand("item replace entity @a armor."+p[0]+" with minecraft:leather_"+p[1]+"[minecraft:dyed_color=16711680]");}
            context.waitTicks(8);context.getInput().resizeWindow(1600,1000);
            var panel=new AtomicReference<PreviewLayout.PlayerPanel>();var physical=new AtomicReference<Float>();
            context.runOnClient(c->{c.options.guiScale().set(2);physical.set(c.player.getBbWidth());ClientUiAccess.setScreen(c,new CosmeticsScreen(null,CosmeticsScreen.Page.SIZE));});context.waitTicks(3);context.takeScreenshot("201-portrait-before-drag");
            context.runOnClient(c->{var screen=(CosmeticsScreen)ClientUiAccess.screen(c);panel.set((PreviewLayout.PlayerPanel)read(screen,"playerPanel"));var p=panel.get();var event=mouse(p.previewX()+p.previewWidth()/2d,p.previewY()+p.previewHeight()/2d);if(!screen.mouseClicked(event,false)||!screen.mouseDragged(event,(90-WardrobeCamera.DEFAULT.yaw())/.7,-WardrobeCamera.DEFAULT.pitch()/.4))throw new AssertionError("Portrait does not accept drag: panel="+p+" top="+read(screen,"viewTop")+" bottom="+read(screen,"viewBottom")+" scroll="+read(screen,"scroll")+" entrance="+read(screen,"entranceScale")+" event="+event);screen.mouseReleased(event);});
            setDepth(context,.35);context.waitTicks(3);Path thin=context.takeScreenshot("201-player-depth-thin");
            setDepth(context,2.5);context.waitTicks(3);Path deep=context.takeScreenshot("201-player-depth-deep");
            int thinWidth=redWidth(thin,panel.get(),2),deepWidth=redWidth(deep,panel.get(),2);
            if(thinWidth<5||deepWidth<thinWidth*1.5)throw new AssertionError("Depth change is not visible: thin="+thinWidth+" deep="+deepWidth);
            context.runOnClient(c->{if(c.player.getBbWidth()!=physical.get())throw new AssertionError("Preview altered collision bounds");if(UiFonts.modern()!=ClientFlavor.BUNDLED_FONT)throw new AssertionError("Font flavor did not fall back correctly");ClientUiAccess.setScreen(c,null);ConfigManager.playerSizeEnabled=false;c.player.setYRot(0);c.player.setXRot(0);var item=new ItemEntity(c.level,c.player.getX(),c.player.getY()+1,c.player.getZ()+3,new ItemStack(Items.DIAMOND,32));item.setId(-20101);item.setNoGravity(true);c.level.addEntity(item);trackDrops=true;ConfigManager.droppedItems2dEnabled=false;dropDraws=0;});
            context.waitTicks(5);assertDrop(false);
            context.runOnClient(c->{ConfigManager.droppedItems2dEnabled=true;dropDraws=0;});context.waitTicks(5);assertDrop(true);context.takeScreenshot("201-flat-dropped-items");
            context.runOnClient(c->{FeatureModules.set(FeatureModules.Feature.DROP_2D,false);dropDraws=0;});context.waitTicks(4);assertDrop(false);
            context.runOnClient(c->{trackDrops=false;ClientUiAccess.setScreen(c,new CosmeticsScreen(null,CosmeticsScreen.Page.DROP_2D));});context.waitTicks(2);context.takeScreenshot("201-2d-drop-settings");
            System.out.println("RELEASE_201_NATIVE_DEPTH_AND_DROP_OK thin="+thinWidth+" deep="+deepWidth+" bundledFont="+ClientFlavor.BUNDLED_FONT);
            context.runOnClient(c->{ClientUiAccess.setScreen(c,null);c.level.removeEntity(-20101,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);});
        }catch(Exception error){throw new AssertionError(error);}finally{trackDrops=false;}
    }
    private static void assertDrop(boolean flat){if(dropDraws==0||!Float.isFinite(dropRatio)||(flat?dropRatio>.011f:dropRatio<.9f))throw new AssertionError("Drop submission differs, flat="+flat+" ratio="+dropRatio+" draws="+dropDraws);}
    private static void setDepth(ClientGameTestContext context,double value){context.runOnClient(c->{var screen=(CosmeticsScreen)ClientUiAccess.screen(c);var slider=screen.children().stream().filter(PinkUi.Slider.class::isInstance).map(PinkUi.Slider.class::cast).filter(s->s.getMessage().getString().startsWith("Z")).findFirst().orElseThrow();slider.setActual(value);if(Math.abs(ConfigManager.playerSizeZ-value)>.001)throw new AssertionError("Depth slider failed");});}
    private static int redWidth(Path file,PreviewLayout.PlayerPanel p,int scale)throws Exception{
        var image=javax.imageio.ImageIO.read(file.toFile());int min=image.getWidth(),max=-1;
        for(int y=(p.previewY()+55)*scale;y<(p.previewY()+p.previewHeight()-5)*scale;y++)for(int x=(p.previewX()+5)*scale;x<(p.previewX()+p.previewWidth()-5)*scale;x++){
            int rgb=image.getRGB(x,y),r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;if(r>65&&g<r*.5&&b<r*.5){min=Math.min(min,x);max=Math.max(max,x);}
        }return max<min?0:max-min+1;
    }
    private static Object read(Object object,String name){try{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}}
    private static MouseButtonEvent mouse(double x,double y){try{var button=Class.forName("net.minecraft.client.input.MouseButtonInfo");return(MouseButtonEvent)MouseButtonEvent.class.getConstructor(double.class,double.class,button).newInstance(x,y,button.getConstructor(int.class,int.class).newInstance(com.customcosmetics.compatibility.ClientInput.MOUSE_LEFT,0));}catch(Exception e){throw new AssertionError(e);}}
}
