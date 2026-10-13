// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.cape;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Pure local image handling. Cropping never contacts the cosmetics service. */
public final class CapeImages {
    public static final int MAX_FILE=2*1024*1024,MAX_PNG=MAX_FILE;
    private static final long MAX_PIXELS=16L*1024*1024;
    private CapeImages(){}
    public static BufferedImage read(Path path)throws IOException{
        if(Files.size(path)>MAX_FILE)throw new IOException("Image must not exceed 2 MiB.");
        return decode(Files.readAllBytes(path));
    }
    public static BufferedImage decode(byte[] bytes)throws IOException{
        if(bytes.length>MAX_FILE)throw new IOException("Image must not exceed 2 MiB.");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Unsupported image. Use PNG, JPEG, BMP or GIF.");
            var reader=readers.next();try{
                reader.setInput(input,true,true);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||(long)width*height>MAX_PIXELS)throw new IOException("Image requires too much memory to decode.");
                return reader.read(0);
            }finally{reader.dispose();}
        }
    }
    public static boolean atlas(BufferedImage image){return image.getWidth()>=64&&image.getWidth()%64==0&&(long)image.getHeight()*2==image.getWidth();}
    public static byte[] atlasPng(BufferedImage image)throws IOException{
        if(!atlas(image))throw new IOException("Cape atlas must be a multiple of 64 x 32 (2:1).");
        var normalized=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)normalized.setRGB(x,y,image.getRGB(x,y));
        return png(normalized);
    }
    public static byte[] png(BufferedImage image)throws IOException{
        byte[] bytes=encodePng(image);if(bytes.length>MAX_PNG)throw new IOException("Cape PNG must not exceed 2 MiB.");return bytes;
    }
    private static byte[] encodePng(BufferedImage image)throws IOException{
        var out=new ByteArrayOutputStream();if(!ImageIO.write(image,"png",out))throw new IOException("Cannot encode PNG.");
        return out.toByteArray();
    }
    public static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    public record Crop(double x,double y,double width,double height){
        public static Crop centered(int width,int height){double w=Math.min(width,height*10.0/16),h=w*16/10;return new Crop((width-w)/2,(height-h)/2,w,h);}
        public Crop move(double dx,double dy,int iw,int ih){return new Crop(Math.clamp(x+dx,0,iw-width),Math.clamp(y+dy,0,ih-height),width,height);}
        public Crop zoom(double factor,int iw,int ih){double w=Math.clamp(width*factor,Math.min(10,Math.min(iw,ih*10.0/16)),Math.min(iw,ih*10.0/16));double h=w*16/10;return new Crop(Math.clamp(x+(width-w)/2,0,iw-w),Math.clamp(y+(height-h)/2,0,ih-h),w,h);}
    }
    public static byte[] crop(BufferedImage source,Crop crop)throws IOException{
        if(!Double.isFinite(crop.x+crop.y+crop.width+crop.height)||crop.width<=0||crop.height<=0||crop.x<0||crop.y<0||crop.x+crop.width>source.getWidth()+.001||crop.y+crop.height>source.getHeight()+.001||Math.abs(crop.width/crop.height-10.0/16)>.001)throw new IOException("Invalid crop.");
        int factor=cropScale(crop);
        for(;;){
            byte[] bytes=encodePng(cropAtlas(source,crop,factor));if(bytes.length<=MAX_PNG)return bytes;
            if(factor==1)throw new IOException("Cape PNG must not exceed 2 MiB.");
            factor=Math.max(1,Math.min(factor-1,(int)Math.floor(factor*Math.sqrt(MAX_PNG/(double)bytes.length)*.95)));
        }
    }
    public static int cropScale(Crop crop){return Math.max(1,Math.min((int)Math.sqrt(MAX_PIXELS/(64*32)),(int)Math.ceil(crop.width/10)));}
    private static BufferedImage cropAtlas(BufferedImage source,Crop crop,int factor){
        var atlas=new BufferedImage(64*factor,32*factor,BufferedImage.TYPE_INT_ARGB);int faceWidth=10*factor,faceHeight=16*factor;
        // Preserve the selected image's detail and the same cape-face/edge UVs at every scale.
        for(int y=0;y<faceHeight;y++)for(int x=0;x<faceWidth;x++){
            int color=source.getRGB(Math.min(source.getWidth()-1,(int)(crop.x+(x+.5)*crop.width/faceWidth)),Math.min(source.getHeight()-1,(int)(crop.y+(y+.5)*crop.height/faceHeight)));
            atlas.setRGB(factor+x,factor+y,color);atlas.setRGB(12*factor+x,factor+y,color);
        }
        for(int y=factor;y<17*factor;y++)for(int x=0;x<factor;x++){atlas.setRGB(x,y,atlas.getRGB(factor,y));atlas.setRGB(11*factor+x,y,atlas.getRGB(11*factor-1,y));}
        for(int x=factor;x<11*factor;x++)for(int y=0;y<factor;y++){atlas.setRGB(x,y,atlas.getRGB(x,factor));atlas.setRGB(x+10*factor,y,atlas.getRGB(x,17*factor-1));}
        return atlas;
    }
}
