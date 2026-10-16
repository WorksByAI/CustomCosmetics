// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.util.*;
import java.util.function.IntUnaryOperator;

/** Column hashing adapted from Harry282/FunnyMap ScanUtils.kt (AGPL-3.0); see NOTICE. */
public final class RoomCores {
    private RoomCores(){}
    public static int hash(IntUnaryOperator column){
        int height=140;while(height>11&&column.applyAsInt(height)==0)height--;
        var text=new StringBuilder("0".repeat(140-height));int bedrock=0;
        for(int y=height;y>=12;y--){int id=column.applyAsInt(y);if(id<0)throw new IllegalArgumentException("Unknown legacy block");
            if(id==0&&bedrock>=2&&y<69){text.append("0".repeat(y-11));break;}
            if(id==7)bedrock++;else{bedrock=0;if(id==5||id==54||id==146)continue;}text.append(id);
        }return text.toString().hashCode();
    }
    /** Choose a stable cardinal direction by a unique column, checking closest offsets first. */
    public static int direction(java.util.function.IntFunction<int[]> rings){
        for(int distance=1;distance<=7;distance++){int[] hashes=rings.apply(distance);int best=-1;
            for(int i=0;i<4;i++){int count=0;for(int hash:hashes)if(hash==hashes[i])count++;if(count==1&&(best<0||hashes[i]<hashes[best]))best=i;}
            if(best>=0)return best;
        }return 0;
    }
}
