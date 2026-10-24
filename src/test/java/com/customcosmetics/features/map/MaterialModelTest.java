package com.customcosmetics.features.map;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class MaterialModelTest {
    private record Part(boolean useAmbientOcclusion) implements BlockStateModelPart {
        @Override public List<BakedQuad> getQuads(Direction face){return List.of();}
        @Override public Material.Baked particleMaterial(){return null;}
        @Override public int materialFlags(){return 0;}
    }
    private static final class Model implements BlockStateModel {
        final BlockStateModelPart first=new SimpleModelWrapper(QuadCollection.EMPTY,false,null),second=new SimpleModelWrapper(QuadCollection.EMPTY,true,null);int collects;
        @Override public void collectParts(RandomSource random,List<BlockStateModelPart> output){collects++;output.add(random.nextBoolean()?first:second);}
        @Override public Material.Baked particleMaterial(){return null;}
        @Override public int materialFlags(){return 0;}
    }
    @Test void repeatedBlocksReuseRetexturedPartsButStillSelectOriginalRandomVariants(){
        var original=new Model();var target=new net.minecraft.client.renderer.block.dispatch.SingleVariant(new SimpleModelWrapper(QuadCollection.EMPTY,false,null));var generation=new Object();
        var wrapped=MaterialModel.cached(generation,original,target,false);var output=new ArrayList<BlockStateModelPart>();
        var seen=new IdentityHashMap<BlockStateModelPart,Boolean>();
        for(int seed=0;seed<1000;seed++){
            assertSame(wrapped,MaterialModel.cached(generation,original,target,false));
            long randomSeed=seed*0x9E3779B97F4A7C15L;
            output.clear();wrapped.collectParts(RandomSource.create(randomSeed),output);
            assertEquals(!RandomSource.create(randomSeed).nextBoolean(),output.getFirst().useAmbientOcclusion());
            seen.put(output.getFirst(),true);
        }
        assertEquals(1000,original.collects);assertEquals(2,seen.size());
    }
    @Test void resourceGenerationAndWorkerChangesNeverReuseOldModelCaches()throws Exception{
        var original=new Model();var target=new Model();var generation=new Object();
        var old=MaterialModel.cached(generation,original,target,false);
        assertNotSame(old,MaterialModel.cached(new Object(),original,target,false));
        var current=MaterialModel.cached(generation,original,target,false);var worker=new AtomicReference<MaterialModel>();
        var thread=new Thread(()->worker.set(MaterialModel.cached(generation,original,target,false)));thread.start();thread.join();
        assertNotSame(current,worker.get());
    }
    @Test void modelPairCacheIsBoundedAndHiddenModelsNeverCollectGeometry(){
        var generation=new Object();var original=new Model();var target=new Model();
        var first=MaterialModel.cached(generation,original,target,false);
        for(int i=0;i<128;i++)MaterialModel.cached(generation,new Model(),target,false);
        assertNotSame(first,MaterialModel.cached(generation,original,target,false));
        var hidden=MaterialModel.cached(generation,original,target,true);var output=new ArrayList<BlockStateModelPart>();
        hidden.collectParts(RandomSource.create(0),output);assertTrue(output.isEmpty());assertEquals(0,original.collects);assertEquals(0,target.collects);
    }
}
