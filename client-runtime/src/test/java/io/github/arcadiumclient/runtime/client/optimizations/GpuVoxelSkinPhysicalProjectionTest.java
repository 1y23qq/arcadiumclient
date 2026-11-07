// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import com.google.gson.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GpuVoxelSkinPhysicalProjectionTest {
    private static final class Queries implements GpuVoxelSkinProjectionRows.Queries {
        float[] eye,p;int mode=5888;
        public int matrixMode(){return mode;}
        public void modelView(float[] out){System.arraycopy(eye,0,out,0,16);}
        public void projection(float[] out){System.arraycopy(p,0,out,0,16);}
    }
    @Test void actualNativeRowsMatchDriverMvpInBothProjectionPolicies()throws Exception {
        JsonArray records;try(var stream=getClass().getResourceAsStream("voxel-physical-row-fixture.json")){assertNotNull(stream);records=JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("rows");}
        for(boolean reuse:new boolean[]{false,true}){
            Queries q=new Queries();GpuVoxelSkinProjectionRows rows=new GpuVoxelSkinProjectionRows(16,q);Object owner=new Object(),instances=new Object(),sameMesh=new Object();rows.beginPass(owner,1,2,3,true,true,reuse,false);
            int[] expected=new int[192];for(int i=0;i<12;i++){
                JsonObject data=records.get(i).getAsJsonObject();q.eye=floats(data,"eye");q.p=floats(data,"projection");
                long ticket=rows.captureAtCompiledList(owner,1,2,sameMesh,data.get("part").getAsInt(),.0625f);assertNotEquals(0,ticket);
                float[] eye=new float[16],projection=new float[16],mvp=new float[16];assertTrue(rows.copyPending(ticket,eye,projection,mvp));assertArrayEquals(ints(data,"eye"),bits(eye));assertArrayEquals(ints(data,"mvp"),bits(mvp));
                assertTrue(rows.reserveBeforeNativeSubmit(ticket,instances,i));assertTrue(rows.commitAfterNativeAppend(ticket,i+1));System.arraycopy(ints(data,"mvp"),0,expected,i*16,16);
            }
            FloatBuffer upload=FloatBuffer.allocate(192);assertTrue(rows.writeUpload(owner,instances,1,2,3,12,upload));assertArrayEquals(expected,bits(upload.array()));
            assertEquals(12,rows.metrics().modelViewQueries());assertEquals(12,rows.metrics().modeQueries());assertEquals(reuse?1:12,rows.metrics().projectionQueries());assertEquals(0,rows.metrics().timedCaptureNanos());
            rows.reset();assertFalse(rows.writeUpload(owner,instances,1,2,3,12,FloatBuffer.allocate(192)));
        }
    }
    @Test void reserveCancelUnknownStateAndOwnerChangesCannotAppendRows(){
        Queries q=new Queries();q.eye=new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,-3,1};q.p=new float[]{1,0,0,0,0,1,0,0,0,0,-1.01f,-1,0,0,-.1f,0};
        GpuVoxelSkinProjectionRows rows=new GpuVoxelSkinProjectionRows(1,q);Object owner=new Object(),mesh=new Object(),instances=new Object();rows.beginPass(owner,1,2,3,true,true,false,false);
        assertEquals(0,rows.captureAtCompiledList(new Object(),1,2,mesh,6,.0625f));assertEquals(0,rows.metrics().modeQueries());
        assertEquals(0,rows.captureAtCompiledList(owner,1,2,mesh,6,.1f));q.mode=5889;assertEquals(0,rows.captureAtCompiledList(owner,1,2,mesh,6,.0625f));q.mode=5888;
        long ticket=rows.captureAtCompiledList(owner,1,2,mesh,6,.0625f);assertNotEquals(0,ticket);assertEquals(0,rows.captureAtCompiledList(owner,1,2,mesh,6,.0625f));
        assertFalse(rows.reserveBeforeNativeSubmit(ticket,instances,1));assertTrue(rows.reserveBeforeNativeSubmit(ticket,instances,0));rows.cancel(ticket);assertFalse(rows.commitAfterNativeAppend(ticket,1));assertEquals(0,rows.committedCount());
        ticket=rows.captureAtCompiledList(owner,1,2,mesh,6,.0625f);assertTrue(rows.reserveBeforeNativeSubmit(ticket,instances,0));assertTrue(rows.commitAfterNativeAppend(ticket,1));assertFalse(rows.commitAfterNativeAppend(ticket,2));assertEquals(0,rows.captureAtCompiledList(owner,1,2,mesh,6,.0625f));
        assertFalse(rows.writeUpload(owner,instances,1,3,3,1,FloatBuffer.allocate(16)));assertFalse(rows.writeUpload(owner,new Object(),1,2,3,1,FloatBuffer.allocate(16)));
    }
    @Test void actualListMismatchCancellationAndNestedScopesKeepTheRightReceiver(){
        GpuVoxelSkinLeafScope scope=new GpuVoxelSkinLeafScope(true);Object a=new Object(),b=new Object();int first=scope.enter(a,.0625f,12);
        assertSame(a,scope.mesh(12));assertNull(scope.mesh(13));int second=scope.enter(b,.0625f,13);assertSame(b,scope.mesh(13));assertNull(scope.mesh(12));scope.leave(second);assertSame(a,scope.mesh(12));scope.leave(first);assertNull(scope.mesh(12));
        int canceled=scope.enter(a,.0625f,12);scope.leave(canceled);assertNull(scope.mesh(12));
        for(int i=0;i<9;i++)scope.enter(a,.0625f,12);assertNull(scope.mesh(12));scope.beginFrame();assertNull(scope.mesh(12));int restored=scope.enter(a,.0625f,12);assertSame(a,scope.mesh(12));scope.leave(restored);
        GpuVoxelSkinLeafScope off=new GpuVoxelSkinLeafScope(false);assertEquals(0,off.enter(a,.0625f,12));assertNull(off.mesh(12));
    }
    @Test void ownedShaderPatchChangesOnlyThePositionRecipeAndKeepsUnknownPairsNative()throws Exception {
        String path=System.getProperty("arcadium.voxelSkin072.argentumJar");assertNotNull(path);String v,f;
        try(ZipFile jar=new ZipFile(Path.of(path).toFile())){v=new String(jar.getInputStream(jar.getEntry("assets/argentum/shaders/entity_instancing.vert")).readAllBytes(),StandardCharsets.UTF_8);f=new String(jar.getInputStream(jar.getEntry("assets/argentum/shaders/entity_instancing.frag")).readAllBytes(),StandardCharsets.UTF_8);}
        String pv=GpuVoxelSkinProjectionShaderPatch.patch("argentum:entity_instancing.vert",v,f),pf=GpuVoxelSkinProjectionShaderPatch.patch("argentum:entity_instancing.frag",f,v);
        assertNotEquals(v,pv);assertEquals(f.replace("#version 130","#version 430 compatibility"),pf);assertTrue(pv.contains("layout(location=14) in uint"));assertTrue(pv.contains("binding=0"));assertTrue(pv.contains("gl_Position = gl_ProjectionMatrix * eyePosition;"));assertFalse(pv.contains("gl_BaseInstanceARB"));assertTrue(pv.contains("fma(m[0]"));
        assertTrue(pv.contains("uniform mat4 uArcVoxelFallbackMvp;"));assertTrue(pv.indexOf("uArcVoxelProjectionFallback != 0")<pv.indexOf("else if (aArcVoxelProjectionRow"));
        assertSame(v,GpuVoxelSkinProjectionShaderPatch.patch("argentum:entity_instancing.vert",v,f+"\n"));assertSame(pv,GpuVoxelSkinProjectionShaderPatch.patch("argentum:entity_instancing.vert",pv,f));
    }
    private static int[] ints(JsonObject d,String k){JsonArray a=d.getAsJsonArray(k);int[] v=new int[a.size()];for(int i=0;i<v.length;i++)v[i]=a.get(i).getAsInt();return v;}
    private static float[] floats(JsonObject d,String k){int[] a=ints(d,k);float[] f=new float[a.length];for(int i=0;i<f.length;i++)f[i]=Float.intBitsToFloat(a[i]);return f;}
    private static int[] bits(float[] f){int[] a=new int[f.length];for(int i=0;i<a.length;i++)a[i]=Float.floatToRawIntBits(f[i]);return a;}
}
