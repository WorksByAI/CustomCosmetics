// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.lang.reflect.*;
import java.util.Arrays;
/** 26.3 added glint fields and replaced the shade boolean; preserve every version's existing fields. */
final class MaterialInfoCopy {
    private static final RecordComponent[] FIELDS=BakedQuad.MaterialInfo.class.getRecordComponents();
    private static final Constructor<?> CONSTRUCTOR;
    static {try{CONSTRUCTOR=BakedQuad.MaterialInfo.class.getConstructor(Arrays.stream(FIELDS).map(RecordComponent::getType).toArray(Class<?>[]::new));}catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}}
    static BakedQuad.MaterialInfo withTexture(BakedQuad.MaterialInfo original,BakedQuad.MaterialInfo replacement,TextureAtlasSprite sprite){try{
        Object[] values=new Object[FIELDS.length];for(int i=0;i<values.length;i++){
            String name=FIELDS[i].getName();Object source=replacement!=null&&(name.equals("layer")||name.startsWith("item"))?replacement:original;
            values[i]=name.equals("sprite")?sprite:name.equals("tintIndex")?-1:FIELDS[i].getAccessor().invoke(source);
        }return (BakedQuad.MaterialInfo)CONSTRUCTOR.newInstance(values);
    }catch(ReflectiveOperationException error){throw new IllegalStateException("Unsupported block material record",error);}}
}
