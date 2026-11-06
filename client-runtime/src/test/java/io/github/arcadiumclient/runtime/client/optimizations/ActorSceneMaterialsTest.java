// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ActorSceneMaterialsTest {
    @Test void exactPoolLayerUploadIdentityIsStableAndReplacementGetsANewId(){
        var registry=new ActorSceneMaterials();Object pool=new Object(),fact=new Object();int first=registry.identity(pool,fact,4);
        assertEquals(first,registry.identity(pool,fact,4));assertNotEquals(first,registry.identity(pool,new Object(),4));assertNotEquals(first,registry.identity(pool,fact,5));assertNotEquals(first,registry.identity(new Object(),fact,4));
    }
    @Test void storageIsBoundedAndResourceClearReleasesReferences(){
        var registry=new ActorSceneMaterials();Object pool=new Object();for(int i=0;i<1024;i++)assertEquals(i+1,registry.identity(pool,new Object(),i&255));assertEquals(0,registry.identity(pool,new Object(),0));assertEquals(1024,registry.size());registry.clear();assertEquals(0,registry.size());
    }
    @Test void unknownMaterialIdentityStaysOnTheOriginalRoute(){
        var registry=new ActorSceneMaterials();assertEquals(0,registry.identity(null,new Object(),0));assertEquals(0,registry.identity(new Object(),null,0));assertEquals(0,registry.identity(new Object(),new Object(),256));
    }
}
