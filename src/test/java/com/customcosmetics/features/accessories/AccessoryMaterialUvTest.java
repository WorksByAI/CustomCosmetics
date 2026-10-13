// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import org.junit.jupiter.api.Test;
import static com.customcosmetics.features.accessories.AccessoryGeometry.*;
import static org.junit.jupiter.api.Assertions.*;
class AccessoryMaterialUvTest {
    @Test void detailedFacesUseTheLargerAtlasWithoutChangingNormalizedCoordinates(){var a=new Builder();var b=new Builder();a.box(4,3,2,0,4);b.box(4,3,2,0,20);for(int i=0;i<a.faces().size();i++){var pixel=AccessoryMaterialUv.region(a.faces().get(i));var detailed=AccessoryMaterialUv.region(b.faces().get(i));assertEquals(pixel.u()/64,detailed.u()/256,1e-6);assertEquals(pixel.v()/64,detailed.v()/256,1e-6);assertEquals(pixel.width()/64,detailed.width()/256,1e-6);assertEquals(pixel.height()/64,detailed.height()/256,1e-6);}}
    @Test void halfPixelVoxelDoesNotCompressAnEntireMaterialTileOntoItsFace(){var builder=new Builder();builder.box(.5f,.5f,.5f,0,4);for(var face:builder.faces()){var uv=AccessoryMaterialUv.region(face);assertEquals(.5f,uv.width(),1e-5);assertEquals(.5f,uv.height(),1e-5);}}
    @Test void largeAndNegativeCoordinatesStayInsideTheirMaterialTile(){var builder=new Builder();builder.box(32,24,8,0,12);var translated=new Builder();translated.add(builder,new Point(-30,-24,17),.2f,.3f,.1f);for(var face:translated.faces()){var uv=AccessoryMaterialUv.region(face);assertTrue(uv.u()>=.5f&&uv.u()+uv.width()<=15.5f);assertTrue(uv.v()>=48.5f&&uv.v()+uv.height()<=63.5f);}}
}
