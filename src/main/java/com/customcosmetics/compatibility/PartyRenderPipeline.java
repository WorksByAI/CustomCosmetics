// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.resources.Identifier;
import java.util.*;
import java.lang.reflect.Array;

/** Reuses the game's position/color shader, without changing any global depth state. */
public final class PartyRenderPipeline{
    private static RenderType type;
    private PartyRenderPipeline(){}
    public static synchronized RenderType type(){if(type!=null)return type;try{
        var field=RenderPipelines.class.getDeclaredField("DEBUG_FILLED_SNIPPET");field.setAccessible(true);
        // RenderPipeline moved to RenderPearl in 26.3. Avoid linking its old return type.
        Object base=RenderType.class.getMethod("pipeline").invoke(RenderTypes.debugQuads());
        Object snippets=Array.newInstance(field.getType(),1);Array.set(snippets,0,field.get(null));
        Object builder=base.getClass().getMethod("builder",snippets.getClass()).invoke(null,snippets);
        builder.getClass().getMethod("withLocation",Identifier.class).invoke(builder,Identifier.fromNamespaceAndPath("customcosmetics","pipeline/party"));
        builder.getClass().getMethod("withCull",boolean.class).invoke(builder,false);
        Object color;
        try{color=base.getClass().getMethod("getColorTargetState").invoke(base);}
        catch(NoSuchMethodException newer){color=((List<?>)base.getClass().getMethod("getColorTargetStates").invoke(base)).getFirst();}
        var colorState=Arrays.stream(builder.getClass().getMethods()).filter(m->m.getName().equals("withColorTargetState")&&m.getParameterCount()==1).findFirst().orElseThrow();
        colorState.invoke(builder,color);
        builder.getClass().getMethod("withDepthStencilState",Optional.class).invoke(builder,Optional.empty());
        try{
            Object format=base.getClass().getMethod("getVertexFormat").invoke(base),mode=base.getClass().getMethod("getVertexFormatMode").invoke(base);
            Arrays.stream(builder.getClass().getMethods()).filter(m->m.getName().equals("withVertexFormat")).findFirst().orElseThrow().invoke(builder,format,mode);
        }catch(NoSuchMethodException newer){
            Object formats=base.getClass().getMethod("getVertexFormatBindings").invoke(base);
            List<?> bindings=formats instanceof List<?> list?list:Arrays.asList((Object[])formats);
            var binding=Arrays.stream(builder.getClass().getMethods()).filter(m->m.getName().equals("withVertexBinding")).findFirst().orElseThrow();
            for(int i=0;i<bindings.size();i++)if(bindings.get(i)!=null)binding.invoke(builder,i,bindings.get(i));
            Object mode=base.getClass().getMethod("getPrimitiveTopology").invoke(base);
            Arrays.stream(builder.getClass().getMethods()).filter(m->m.getName().equals("withPrimitiveTopology")).findFirst().orElseThrow().invoke(builder,mode);
        }
        Object pipeline=builder.getClass().getMethod("build").invoke(builder);
        Object setupBuilder=RenderSetup.class.getMethod("builder",base.getClass()).invoke(null,pipeline);
        setupBuilder.getClass().getMethod("sortOnUpload").invoke(setupBuilder);
        var setup=(RenderSetup)setupBuilder.getClass().getMethod("createRenderSetup").invoke(setupBuilder);
        var create=RenderType.class.getDeclaredMethod("create",String.class,RenderSetup.class);create.setAccessible(true);return type=(RenderType)create.invoke(null,"customcosmetics_party",setup);
    }catch(ReflectiveOperationException problem){throw new IllegalStateException("Unsupported party render pipeline",problem);}}
}
