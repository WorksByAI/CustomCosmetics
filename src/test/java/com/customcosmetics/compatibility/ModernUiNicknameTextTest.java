// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;
import com.customcosmetics.features.nickname.*;
import com.customcosmetics.utils.NickRenderUtils;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2f;
import org.junit.jupiter.api.Test;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModernUiNicknameTextTest {
    private static NicknameGlyphEffect effect(){return new NicknameGlyphEffect(new NicknameGradient("smooth",0x00FF00,0xFF00FF,1),0,1);}
    @Test void markersAndTheirShadowsAreUniqueAndAvoidRealTextColors(){
        var reserved=Set.of(0x800000,0x200000,0x800004,0x200001,0xFFFFFF,0);
        var palette=new ModernUiNicknameText.Palette(reserved);var markers=new HashSet<Integer>();var shadows=new HashSet<Integer>();
        for(int i=0;i<500;i++){int marker=palette.add(effect()),shadow=ModernUiNicknameText.shadow(marker);assertTrue(markers.add(marker));assertTrue(shadows.add(shadow));assertFalse(reserved.contains(marker));assertFalse(reserved.contains(shadow));}
        assertTrue(Collections.disjoint(markers,shadows));
    }
    @Test void nativeGuiTransformsKeepGradientSamplingInFontCoordinates(){
        var palette=new ModernUiNicknameText.Palette(Set.of());int marker=palette.add(effect());
        var flat=draw(palette,marker,false,new Matrix3x2f());var transformed=draw(palette,marker,false,new Matrix3x2f().translate(100,25).rotate(.3f).scale(2));
        assertTrue(flat.vertices.size()>4);assertEquals(flat.vertices.stream().map(v->v.color).toList(),transformed.vertices.stream().map(v->v.color).toList());
        assertNotEquals(flat.vertices.getFirst().x,transformed.vertices.getFirst().x);
        assertTrue(flat.vertices.stream().allMatch(v->v.color>>>24==0x88&&v.light==0x00F000F0&&v.u>=.1f&&v.u<=.8f));
    }
    @Test void shadowColorsStayDarkAndNonNicknameQuadsStayUnchanged(){
        var palette=new ModernUiNicknameText.Palette(Set.of());int marker=palette.add(effect());
        var foreground=draw(palette,marker,false,new Matrix3x2f());var shadow=draw(palette,ModernUiNicknameText.shadow(marker),true,new Matrix3x2f());
        assertEquals(foreground.vertices.size(),shadow.vertices.size());
        for(int i=0;i<foreground.vertices.size();i++)assertEquals(ModernUiNicknameText.shadow(foreground.vertices.get(i).color),shadow.vertices.get(i).color&0xFFFFFF);
        var normal=draw(palette,0x123456,false,new Matrix3x2f());assertEquals(4,normal.vertices.size());assertTrue(normal.vertices.stream().allMatch(v->v.color==0x88123456));
    }
    @Test void paletteScopesRestoreNestedDrawsAndExceptions(){
        var first=new ModernUiNicknameText.Palette(Set.of());var second=new ModernUiNicknameText.Palette(Set.of());
        assertTrue(ModernUiNicknameText.current().empty());
        try(var outer=ModernUiNicknameText.enter(first)){
            assertSame(first,ModernUiNicknameText.current());
            assertThrows(IllegalArgumentException.class,()->{try(var inner=ModernUiNicknameText.enter(second)){assertSame(second,ModernUiNicknameText.current());throw new IllegalArgumentException();}});
            assertSame(first,ModernUiNicknameText.current());
        }
        assertSame(ModernUiNicknameText.Palette.EMPTY,ModernUiNicknameText.current());
    }
    @Test void alreadyResolvedNicknamesCannotBeSubstitutedAgain(){
        var text=new ProcessedNicknameText(sink->true);assertSame(text,NickRenderUtils.handleCharSequence(text));
        var masked=new ModernUiNicknameText.PaletteSequence(text,text,ModernUiNicknameText.Palette.EMPTY);assertSame(masked,NickRenderUtils.handleCharSequence(masked));
    }
    @Test void ordinaryTextKeepsItsOriginalSequenceAndFormatting(){
        var style=Style.EMPTY.withColor(0xC13F92).withBold(true).withUnderlined(true);
        var text=FormattedCharSequence.forward("untouched",style);
        var prepared=ModernUiNicknameText.mask(text,-1,0);
        assertSame(text,prepared.clean());assertSame(text,prepared.masked());assertTrue(prepared.palette().empty());
        prepared.masked().accept((i,s,cp)->{assertSame(style,s);return true;});
    }
    @Test void reenteringPreparedTextRetainsTheOriginalCleanSequence(){
        var clean=FormattedCharSequence.forward("nickname",Style.EMPTY);
        var palette=new ModernUiNicknameText.Palette(Set.of());palette.add(effect());
        var masked=new ModernUiNicknameText.PaletteSequence(clean,sink->true,palette);
        var prepared=ModernUiNicknameText.prepare(masked,null,-1,0);
        assertSame(clean,prepared.clean());assertSame(masked,prepared.masked());assertSame(palette,prepared.palette());
    }
    private static Capture draw(ModernUiNicknameText.Palette palette,int marker,boolean shadow,Matrix3x2f pose){
        var output=new Capture();var vertices=palette.wrap(output,shadow);
        vertices.addVertexWith2DPose(pose,4,6).setColor(0x88000000|marker).setUv(.1f,.2f).setLight(0x00F000F0);
        vertices.addVertexWith2DPose(pose,4,14).setColor(0x88000000|marker).setUv(.1f,.7f).setLight(0x00F000F0);
        vertices.addVertexWith2DPose(pose,12,14).setColor(0x88000000|marker).setUv(.8f,.7f).setLight(0x00F000F0);
        vertices.addVertexWith2DPose(pose,12,6).setColor(0x88000000|marker).setUv(.8f,.2f).setLight(0x00F000F0);vertices.finish();return output;
    }
    private static final class V {float x,y,u,v;int color,light;}
    private static final class Capture implements VertexConsumer {
        final List<V> vertices=new ArrayList<>();V current;
        @Override public VertexConsumer addVertex(float x,float y,float z){current=new V();current.x=x;current.y=y;vertices.add(current);return this;}
        @Override public VertexConsumer setColor(int color){current.color=color;return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){return setColor(a<<24|r<<16|g<<8|b);}
        @Override public VertexConsumer setUv(float u,float v){current.u=u;current.v=v;return this;}
        @Override public VertexConsumer setUv1(int u,int v){return this;}
        @Override public VertexConsumer setUv2(int u,int v){current.light=v<<16|u&65535;return this;}
        public VertexConsumer setUv3(float u,float v){return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
        @Override public VertexConsumer setLineWidth(float width){return this;}
    }
}
