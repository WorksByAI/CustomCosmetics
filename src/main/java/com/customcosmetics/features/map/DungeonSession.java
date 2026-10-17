// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.util.Collection;

/** A confirmed run survives boss sidebar changes, but never crosses a world or connection. */
final class DungeonSession {
    record Context(boolean detected,String floor) {}
    private Object world,connection;
    private Context context=new Context(false,"");

    Context update(Object nextWorld,Object nextConnection,String title,Collection<String> rows){
        if(world!=nextWorld||connection!=nextConnection||nextWorld==null||nextConnection==null){
            world=nextWorld;connection=nextConnection;context=new Context(false,"");
        }
        if(nextWorld==null||nextConnection==null)return context;
        if(DungeonSidebar.matches(title,rows)){
            String floor=DungeonSidebar.floor(rows);
            context=new Context(true,floor.isEmpty()?context.floor():floor);
        }else if(DungeonSidebar.outsideDungeon(rows)||DungeonSidebar.kuudra(title,rows)
                ||!DungeonSidebar.normalize(title).isEmpty()&&!DungeonSidebar.branded(title,rows)){
            context=new Context(false,"");
        }
        return context;
    }
}
