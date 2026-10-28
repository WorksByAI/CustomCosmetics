// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

/** Keep the full preview name inside its own row, independently of the model's facing. */
public final class PreviewLayout {
    private PreviewLayout() {}
    public record PlayerPanel(int previewX,int previewY,int previewWidth,int previewHeight,
                              int controlsX,int controlsY,int controlsWidth,boolean sideBySide) {}
    public static PlayerPanel playerPanel(int x,int y,int width,int viewportHeight){
        width=Math.max(1,width);boolean sideBySide=width>=430;
        int previewHeight=Math.clamp(viewportHeight-16,96,sideBySide?360:320);
        int preferredWidth=sideBySide?Math.clamp(width/3,160,190):Math.min(180,width);
        int previewWidth=Math.min(preferredWidth,(int)(previewHeight*.62));
        int previewX=sideBySide?x:x+(width-previewWidth)/2;
        return new PlayerPanel(previewX,y,previewWidth,previewHeight,
            sideBySide?x+previewWidth+16:x,sideBySide?y:y+previewHeight+14,
            sideBySide?width-previewWidth-16:width,sideBySide);
    }
    public static float nameScale(int textWidth, int availableWidth) {
        return Math.min(1.35f, Math.max(1, availableWidth) / (float)Math.max(1, textWidth));
    }
    /** Reserve the title/name rows while fitting the full model into a short scroll viewport. */
    public static int playerCardHeight(int viewportHeight, boolean pinned) {
        return pinned ? 184 : Math.clamp(viewportHeight - 8, 104, 184);
    }
    public static int playerModelScale(int cardHeight) {
        return Math.max(1, Math.round(52f * Math.max(1, cardHeight - 57) / 127));
    }
    public static int creatureModelScale(int cardHeight, int availableWidth, float width, float height) {
        float fitWidth = Math.max(1, availableWidth) / Math.max(1, width * 1.4f);
        float fitHeight = Math.max(1, cardHeight - 57) / Math.max(1, height * 1.2f);
        return Math.max(1, (int)Math.min(playerModelScale(cardHeight), Math.min(fitWidth, fitHeight)));
    }
}
