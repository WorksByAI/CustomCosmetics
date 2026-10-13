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
    public static final int MAX_FILE=16*1024*1024,MAX_PNG=131072;
    private CapeImages(){}
    public static BufferedImage read(Path path)throws IOException{
        if(Files.size(path)>MAX_FILE)throw new IOException("Image must be smaller than 16 MiB.");
        return decode(Files.readAllBytes(path));
    }
    public static BufferedImage decode(byte[] bytes)throws IOException{
        if(bytes.length>MAX_FILE)throw new IOException("Image is too large.");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Unsupported image. Use PNG, JPEG, BMP or GIF.");
            var reader=readers.next();try{
                reader.setInput(input,true,true);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||width>4096||height>4096)throw new IOException("Image dimensions must not exceed 4096 x 4096.");
                return reader.read(0);
            }finally{reader.dispose();}
        }
    }
    public static boolean atlas(BufferedImage image){return image.getWidth()>=64&&image.getWidth()<=512&&image.getWidth()%64==0&&image.getHeight()*2==image.getWidth();}
    public static byte[] atlasPng(BufferedImage image)throws IOException{
        if(!atlas(image))throw new IOException("Cape atlas must be 64 x 32, or a multiple up to 512 x 256.");
        var normalized=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)normalized.setRGB(x,y,image.getRGB(x,y));
        return png(normalized);
    }
    public static byte[] png(BufferedImage image)throws IOException{
        var out=new ByteArrayOutputStream();if(!ImageIO.write(image,"png",out))throw new IOException("Cannot encode PNG.");
        byte[] bytes=out.toByteArray();if(bytes.length>MAX_PNG)throw new IOException("Cape PNG must be smaller than 128 KiB.");return bytes;
    }
    public static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    public record Crop(double x,double y,double width,double height){
        public static Crop centered(int width,int height){double w=Math.min(width,height*10.0/16),h=w*16/10;return new Crop((width-w)/2,(height-h)/2,w,h);}
        public Crop move(double dx,double dy,int iw,int ih){return new Crop(Math.clamp(x+dx,0,iw-width),Math.clamp(y+dy,0,ih-height),width,height);}
        public Crop zoom(double factor,int iw,int ih){double w=Math.clamp(width*factor,Math.min(10,Math.min(iw,ih*10.0/16)),Math.min(iw,ih*10.0/16));double h=w*16/10;return new Crop(Math.clamp(x+(width-w)/2,0,iw-w),Math.clamp(y+(height-h)/2,0,ih-h),w,h);}
    }
    public static byte[] crop(BufferedImage source,Crop crop)throws IOException{
        if(!Double.isFinite(crop.x+crop.y+crop.width+crop.height)||crop.width<=0||crop.height<=0||crop.x<0||crop.y<0||crop.x+crop.width>source.getWidth()+.001||crop.y+crop.height>source.getHeight()+.001||Math.abs(crop.width/crop.height-10.0/16)>.001)throw new IOException("Invalid crop.");
        var atlas=new BufferedImage(512,256,BufferedImage.TYPE_INT_ARGB);
        // 10 x 16 cape faces at (1,1) and (12,1), scaled 8x. Preserve edge UVs as well.
        for(int y=0;y<128;y++)for(int x=0;x<80;x++){
            int color=source.getRGB(Math.min(source.getWidth()-1,(int)(crop.x+(x+.5)*crop.width/80)),Math.min(source.getHeight()-1,(int)(crop.y+(y+.5)*crop.height/128)));
            atlas.setRGB(8+x,8+y,color);atlas.setRGB(96+x,8+y,color);
        }
        for(int y=8;y<136;y++)for(int x=0;x<8;x++){atlas.setRGB(x,y,atlas.getRGB(8,y));atlas.setRGB(88+x,y,atlas.getRGB(87,y));}
        for(int x=8;x<88;x++)for(int y=0;y<8;y++){atlas.setRGB(x,y,atlas.getRGB(x,8));atlas.setRGB(x+80,y,atlas.getRGB(x,135));}
        return png(atlas);
    }
}
