// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.FloatBuffer;
import java.util.BitSet;

final class ActorFusedReplay {
    private ActorFusedReplay(){}
    static int materialize(ActorSceneData scene,BitSet lazy,FloatBuffer nativeRows,ActorFusedRows selection){
        if(scene==null||lazy==null||nativeRows==null||nativeRows.isReadOnly())throw new IllegalArgumentException("Unknown native CPU replay target");scene.requireOwner();
        if(selection==null){int last=lazy.length()-1;if(last>=8192||(last>=0&&nativeRows.limit()<(last+1)*32))throw new IllegalArgumentException("Native replay span exceeds the packet buffer");}
        else for(int i=0;i<selection.count();i++)if(nativeRows.limit()<(selection.at(i)+1)*32)throw new IllegalArgumentException("Selected native replay span exceeds the packet buffer");
        int copied=0;
        if(selection==null){for(int row=lazy.nextSetBit(0);row>=0;row=lazy.nextSetBit(row+1)){if(scene.present(row)){scene.cpu(row,nativeRows,row*32);copied++;}lazy.clear(row);}}
        else for(int i=0;i<selection.count();i++){int row=selection.at(i);if(!lazy.get(row))continue;if(scene.present(row)){scene.cpu(row,nativeRows,row*32);copied++;}lazy.clear(row);}
        return copied;
    }
}
