// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class ModernArmorAuxiliaryBudgetTest {
    @Test void retainedCapacityIncludesAllThreeStoresAndPersistsAcrossFrames(){
        var budget=new ModernArmorAuxiliaryBudget();Object owner=new Object(),profile=new Object(),geometry=new Object(),instance=new Object();
        assertTrue(budget.owner(owner));assertTrue(budget.profile(owner,profile));assertTrue(budget.geometry(owner,profile,geometry,128));assertEquals(1,budget.units());
        assertTrue(budget.instance(geometry,instance));assertEquals(3,budget.units());assertTrue(budget.grow(instance,128,128,128));assertEquals(384,budget.units());
        assertTrue(budget.used(instance,128));budget.begin(owner);assertEquals(384,budget.units());assertFalse(budget.grow(instance,1,1,1));assertEquals(384,budget.units());
    }
    @Test void aggregateExactLimitRejectsGrowthWithoutChangingTheCurrentReservation(){
        var budget=new ModernArmorAuxiliaryBudget();Object owner=new Object(),profile=new Object(),geometry=new Object(),instance=new Object();
        budget.owner(owner);budget.profile(owner,profile);budget.geometry(owner,profile,geometry,128);budget.instance(geometry,instance);
        assertTrue(budget.grow(instance,4096,2048,2048));assertEquals(8192,budget.units());assertFalse(budget.canInstance());
        assertFalse(budget.grow(instance,4096,4096,4096));assertEquals(8192,budget.units());assertTrue(budget.used(instance,2048));assertFalse(budget.used(instance,2049));
        assertFalse(budget.layer(owner,List.of(geometry)));assertEquals(8192,budget.units());budget.release(owner);assertEquals(0,budget.units());assertEquals(0,budget.vertexBytes());
    }
    @Test void closingOneOwnerDoesNotReleaseOrReassignAnotherOwnersResources(){
        var budget=new ModernArmorAuxiliaryBudget();Object a=new Object(),b=new Object(),pa=new Object(),pb=new Object(),ga=new Object(),gb=new Object(),ia=new Object(),ib=new Object();
        budget.owner(a);budget.owner(b);budget.profile(a,pa);budget.profile(b,pb);budget.geometry(a,pa,ga,128);budget.geometry(b,pb,gb,256);budget.instance(ga,ia);budget.instance(gb,ib);budget.grow(ia,64,64,64);
        budget.release(a);assertEquals(1,budget.owners());assertEquals(1,budget.profiles());assertEquals(1,budget.geometries());assertEquals(1,budget.instances());assertEquals(3,budget.units());assertEquals(256,budget.vertexBytes());
        assertTrue(budget.used(ib,1));assertFalse(budget.used(ia,1));assertFalse(budget.geometry(a,pa,gb,256));
    }
    @Test void identityOwnersAndProfilesCannotAliasThroughEquals(){
        var budget=new ModernArmorAuxiliaryBudget();Object a=new String("same"),b=new String("same"),p=new Object(),g=new Object();
        assertTrue(budget.owner(a));assertTrue(budget.owner(b));assertFalse(budget.owner(new Object()));assertTrue(budget.profile(a,p));assertFalse(budget.profile(b,p));assertTrue(budget.geometry(a,p,g,128));assertFalse(budget.geometry(b,p,g,128));
        assertEquals(2,budget.owners());assertEquals(1,budget.profiles());assertEquals(1,budget.geometries());
    }
    @Test void sourceAndVertexCapsAreCheckedBeforeAdmission(){
        var budget=new ModernArmorAuxiliaryBudget();Object owner=new Object(),profile=new Object();budget.owner(owner);budget.profile(owner,profile);
        assertFalse(budget.geometry(owner,profile,new Object(),0));assertFalse(budget.geometry(owner,profile,new Object(),-1));assertFalse(budget.geometry(owner,profile,new Object(),ModernArmorAuxiliaryBudget.VERTEX_BYTES+1));
        assertTrue(budget.geometry(owner,profile,new Object(),ModernArmorAuxiliaryBudget.VERTEX_BYTES));assertFalse(budget.geometry(owner,profile,new Object(),1));assertEquals(1,budget.geometries());assertEquals(ModernArmorAuxiliaryBudget.VERTEX_BYTES,budget.vertexBytes());
    }
    @Test void modelAndGeometryCapsRemainBoundedAcrossAnOwnersHistory(){
        var budget=new ModernArmorAuxiliaryBudget();Object owner=new Object();budget.owner(owner);List<Object> profiles=new ArrayList<>();
        for(int i=0;i<128;i++){Object profile=new Object();profiles.add(profile);assertTrue(budget.profile(owner,profile));for(int j=0;j<7;j++)assertTrue(budget.geometry(owner,profile,new Object(),128));}
        assertEquals(896,budget.geometries());assertFalse(budget.profile(owner,new Object()));assertFalse(budget.geometry(owner,profiles.getFirst(),new Object(),128));assertEquals(896,budget.units());budget.release(owner);assertEquals(0,budget.units());
    }
    @Test void wholeLayerProjectionIncludesExistingGrowthAndNewMaterialBuffers(){
        var budget=new ModernArmorAuxiliaryBudget();Object owner=new Object(),profile=new Object(),geometry=new Object(),instance=new Object();
        budget.owner(owner);budget.profile(owner,profile);budget.geometry(owner,profile,geometry,128);budget.instance(geometry,instance);assertTrue(budget.grow(instance,1024,1024,1024));budget.used(instance,1024);
        assertTrue(budget.layer(owner,List.of(geometry)));assertEquals(3072,budget.units());assertTrue(budget.grow(instance,2048,2048,2048));budget.used(instance,2048);
        assertFalse(budget.layer(owner,List.of(geometry)));assertEquals(6144,budget.units());assertFalse(budget.layer(new Object(),List.of(geometry)));assertFalse(budget.layer(owner,List.of()));
    }
    @Test void growthCalculationCannotWrapOrExceedTheRetainedLimit(){
        assertEquals(1,ModernArmorAuxiliaryBudget.capacity(1,1));assertEquals(128,ModernArmorAuxiliaryBudget.capacity(64,65));assertEquals(8192,ModernArmorAuxiliaryBudget.capacity(4096,8192));
        assertEquals(Integer.MAX_VALUE,ModernArmorAuxiliaryBudget.capacity(0,1));assertEquals(Integer.MAX_VALUE,ModernArmorAuxiliaryBudget.capacity(1,8193));assertEquals(Integer.MAX_VALUE,ModernArmorAuxiliaryBudget.capacity(1,-1));
    }
    @Test void generatedGeometryOnlyCallsItsOwnedBridgeAndNeverDeletesTheOriginal(){
        int[] draw={0},delete={0};List<String> other=new ArrayList<>();
        new ClassReader(ModernArmorAuxiliaryAccess.adapter()).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                return new MethodVisitor(Opcodes.ASM9){@Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){
                    if(owner.equals("io/github/arcadiumclient/runtime/client/optimizations/ModernArmorAuxiliary")&&method.equals("drawNative"))draw[0]++;
                    else if(owner.equals("io/github/arcadiumclient/runtime/client/optimizations/ModernArmorAuxiliary")&&method.equals("deleteNative"))delete[0]++;
                    else if(!method.equals("<init>"))other.add(owner+"."+method);
                }};
            }
        },0);
        assertEquals(1,draw[0]);assertEquals(1,delete[0]);assertTrue(other.isEmpty(),other.toString());
    }
}
