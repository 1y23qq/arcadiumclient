// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GpuResidentBodyDataTest {
    @Test void nativeUnsignedAttributesPreserveActorMaskProfileAndLightmap() {
        int[] storage=new int[21];Arrays.fill(storage,0x12345678);
        GpuResidentBodyPacket.write(storage,1,8191,0x811,127,0xfff0c0,100.5f,-2,3,4);
        ByteBuffer nativeRecord=ByteBuffer.allocate(76).order(ByteOrder.nativeOrder());for(int i=1;i<=19;i++)nativeRecord.putInt(storage[i]);
        assertEquals(8191,Short.toUnsignedInt(nativeRecord.getShort(64)));assertEquals(0x811,Short.toUnsignedInt(nativeRecord.getShort(66)));
        assertEquals(127,Short.toUnsignedInt(nativeRecord.getShort(68)));assertEquals(240,Byte.toUnsignedInt(nativeRecord.get(53)));assertEquals(255,Byte.toUnsignedInt(nativeRecord.get(54)));
        assertEquals(100.5f,nativeRecord.getFloat(48));assertEquals(-2,nativeRecord.getFloat(36));assertEquals(-1,nativeRecord.getInt(56));assertEquals(0,nativeRecord.getInt(60));
        assertEquals(0x12345678,storage[0]);assertEquals(0x12345678,storage[20]);
    }
    @Test void invalidRecordsCannotPartiallyChangeAdjacentStorage() {
        int[] storage=new int[21];Arrays.fill(storage,17);
        assertThrows(IllegalArgumentException.class,()->GpuResidentBodyPacket.write(storage,1,8192,1,0,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->GpuResidentBodyPacket.write(storage,1,0,0,0,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->GpuResidentBodyPacket.write(storage,1,0,1,256,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->GpuResidentBodyPacket.write(storage,1,0,1,0,0,0,Float.NaN,0,0));
        for(int value:storage)assertEquals(17,value);
    }
    @Test void wholeMeshChangesOnlyTheUnusedBoxUvBoneSelector() {
        float[] source=new float[300];for(int i=0;i<source.length;i++)source[i]=i*.125f;
        FloatBuffer normal=FloatBuffer.wrap(source).position(7).limit(295),mirrored=normal.duplicate();boolean[] mirror=new boolean[12];mirror[3]=true;
        FloatBuffer mesh=GpuResidentBodyMesh.build(normal,mirrored,mirror);
        assertTrue(mesh.isDirect());assertTrue(mesh.isReadOnly());assertEquals(3456,mesh.remaining());
        for(int bone=0;bone<12;bone++)for(int vertex=0;vertex<24;vertex++)for(int word=0;word<12;word++)
            assertEquals(word==3?bone:source[7+vertex*12+word],mesh.get((bone*24+vertex)*12+word));
        assertEquals(7,normal.position());assertEquals(295,normal.limit());
    }
    @Test void anUnknownCubeOrIncompleteBoneLayoutKeepsNativeRendering() {
        assertThrows(IllegalArgumentException.class,()->GpuResidentBodyMesh.build(FloatBuffer.allocate(287),FloatBuffer.allocate(288),new boolean[12]));
        assertThrows(IllegalArgumentException.class,()->GpuResidentBodyMesh.build(FloatBuffer.allocate(288),FloatBuffer.allocate(288),new boolean[11]));
    }
    @Test void oneActorRecordDoesNotTriggerTheNativePerPartComputeExpansion() {
        try {
            GpuPlayerPackets.beginBatch();int id=GpuPlayerPackets.registerTemplate(new float[192]);
            int job=GpuPlayerPackets.add(new Matrix4f(),0,0,0,0,0,0,0,0,false,false,false,id);
            int[] storage=new int[19];GpuResidentBodyPacket.write(storage,0,job,0xfff,0,0,100,0,0,0);
            assertFalse(GpuModelTransforms.hasJobs(IntBuffer.wrap(storage)));
        } finally {GpuPlayerPackets.resetTemplates();}
    }
    @Test void unpinnedShaderSourcesCannotIntroduceTheOwnedStorageInterface() {
        assertThrows(IllegalArgumentException.class,()->GpuResidentBodyShaders.vertex("#version 130\nvoid main(){}\n"));
        assertThrows(IllegalArgumentException.class,()->GpuResidentBodyShaders.vertex("#version 430\nvoid main(){}\n"));
    }
}
