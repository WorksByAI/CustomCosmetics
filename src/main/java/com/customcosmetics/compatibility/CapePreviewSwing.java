// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;
import java.lang.reflect.Field;

/** Resets only the renderer's preview snapshot; Minecraft 26.3 renamed its swing field. */
public final class CapePreviewSwing {
    private static final ClassValue<Field> SWING=new ClassValue<>(){
        @Override protected Field computeValue(Class<?> type){
            for(String name:new String[]{"attackTime","swingAnimation"})try{
                var field=type.getField(name);if(field.getType()==float.class)return field;
            }catch(NoSuchFieldException missing){}
            throw new IllegalStateException("Unsupported cape preview swing snapshot");
        }
    };
    private CapePreviewSwing(){}
    public static void validate(Class<?> type){SWING.get(type);}
    public static void clear(Object state){try{SWING.get(state.getClass()).setFloat(state,0);}catch(IllegalAccessException error){throw new IllegalStateException("Cannot reset cape preview swing",error);}}
}
