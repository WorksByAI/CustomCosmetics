// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.cape;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class CapeImageImportTest {
    @TempDir Path folder;
    @Test void unicodeFilenameAndOriginalHighResolutionArePreserved()throws Exception{var file=folder.resolve("暮冬 披风.png");var source=new BufferedImage(2048,1024,BufferedImage.TYPE_INT_ARGB);source.setRGB(137,200,0xFFCC0066);ImageIO.write(source,"png",file.toFile());var imported=CapeImageImport.read(file,true);assertEquals(2048,imported.source().getWidth());assertEquals(0xFFCC0066,CapeImages.decode(imported.atlas()).getRGB(137,200));}
    @Test void cropInputCanBeAnyAspectRatioAndDoesNotCreateAnAtlasUntilConfirmed()throws Exception{var file=folder.resolve("crop.png");ImageIO.write(new BufferedImage(100,170,BufferedImage.TYPE_INT_ARGB),"png",file.toFile());var imported=CapeImageImport.read(file,false);assertEquals(100,imported.source().getWidth());assertNull(imported.atlas());assertThrows(IOException.class,()->CapeImageImport.read(file,true));}
    @Test void limitsApplyAfterChoosingAndCannotBeBypassedByFilters()throws Exception{var oversized=folder.resolve("large.png");Files.write(oversized,new byte[CapeImages.MAX_FILE+1]);assertThrows(IOException.class,()->CapeImageImport.read(oversized,false));assertThrows(IOException.class,()->CapeImageImport.read(folder,false));var invalid=folder.resolve("fake.png");Files.writeString(invalid,"not an image");assertThrows(IOException.class,()->CapeImageImport.read(invalid,true));}
}
