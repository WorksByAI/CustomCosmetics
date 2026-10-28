package com.customcosmetics.gui;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Reproducible, code-authored coverage masks. Run with the destination resource directory. */
public final class UiShapeAssetGenerator {
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]);Files.createDirectories(root);
        for(int radius=1;radius<=16;radius++)for(boolean ring:new boolean[]{false,true}){
            int logical=radius*2+2,side=logical*4;var image=new BufferedImage(side,side,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<side;y++)for(int x=0;x<side;x++){
                int coverage=0;
                for(int sy=0;sy<4;sy++)for(int sx=0;sx<4;sx++){
                    double px=(x+(sx+.5)/4)/4,py=(y+(sy+.5)/4)/4;
                    if(inside(px,py,logical,radius)&&(!ring||!inside(px-1,py-1,logical-2,radius-1)))coverage++;
                }
                image.setRGB(x,y,Math.round(coverage*255f/16)<<24|0xFFFFFF);
            }
            String name=(ring?"ring_":"fill_")+radius+".png";ImageIO.write(image,"png",root.resolve(name).toFile());
            Files.writeString(root.resolve(name+".mcmeta"),"{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":"+logical+",\"height\":"+logical+",\"border\":"+radius+",\"stretch_inner\":true}}}\n");
        }
    }
    private static boolean inside(double x,double y,int size,int r){
        if(x<0||y<0||x>size||y>size)return false;
        double dx=x-Math.clamp(x,r,size-r),dy=y-Math.clamp(y,r,size-r);return dx*dx+dy*dy<=r*r;
    }
}
