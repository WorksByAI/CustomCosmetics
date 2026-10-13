// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.cape;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.io.IOException;
/** The same size, memory and atlas checks apply regardless of the chosen file picker. */
public final class CapeImageImport {
    public record Image(BufferedImage source,byte[] atlas){}
    private CapeImageImport(){}
    public static Image read(Path path,boolean atlas)throws IOException{if(!java.nio.file.Files.isRegularFile(path))throw new IOException("Select a local image file.");var image=CapeImages.read(path);return new Image(image,atlas?CapeImages.atlasPng(image):null);}
}
