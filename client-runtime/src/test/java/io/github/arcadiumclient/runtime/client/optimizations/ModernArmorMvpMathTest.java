// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import com.google.gson.*;
import java.io.InputStreamReader;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ModernArmorMvpMathTest {
    private static JsonArray oracle(){return JsonParser.parseReader(new InputStreamReader(ModernArmorMvpMathTest.class.getResourceAsStream("/optimization/modern-mvp-oracle.json"))).getAsJsonObject().getAsJsonArray("cases");}
    private static float[] values(JsonObject object,String name){JsonArray array=object.getAsJsonArray(name);float[] result=new float[array.size()];for(int i=0;i<result.length;i++)result[i]=array.get(i).getAsFloat();return result;}
    private static int[] bits(float[] values){int[] result=new int[values.length];for(int i=0;i<result.length;i++)result[i]=Float.floatToRawIntBits(values[i]);return result;}
    private static int[] words(JsonArray array){int[] result=new int[array.size()];for(int i=0;i<result.length;i++)result[i]=array.get(i).getAsInt();return result;}
    @Test void physicalProjectionCompositionMatchesTheActualDriverColumns(){
        for(JsonElement element:oracle()){JsonObject test=element.getAsJsonObject();float[] result=new float[16];ModernArmorMvpMath.multiply(values(test,"projection"),values(test,"eye"),result);assertArrayEquals(words(test.getAsJsonArray("driverMvpBits")),bits(result),test.get("part").getAsString());}
    }
    @Test void orderedFmaMatchesAllActualFtransformClipBits(){
        int vertices=0;
        for(JsonElement element:oracle()){JsonObject test=element.getAsJsonObject();int[] expectedMatrix=words(test.getAsJsonArray("driverMvpBits"));float[] matrix=new float[16];for(int i=0;i<16;i++)matrix[i]=Float.intBitsToFloat(expectedMatrix[i]);JsonArray positions=test.getAsJsonArray("vertices"),clips=test.getAsJsonArray("driverClipBits");
            for(int i=0;i<positions.size();i++){JsonArray vertex=positions.get(i).getAsJsonArray();float[] result=new float[4];ModernArmorMvpMath.clip(matrix,vertex.get(0).getAsFloat(),vertex.get(1).getAsFloat(),vertex.get(2).getAsFloat(),result);assertArrayEquals(words(clips.get(i).getAsJsonArray()),bits(result));vertices++;}}
        assertEquals(48,vertices);
    }
    @Test void separateEyeAndProjectionDoesNotSilentlyReplaceCombinedDriverMath(){
        int different=0;
        for(JsonElement element:oracle()){JsonObject test=element.getAsJsonObject();float[] projection=values(test,"projection"),eye=values(test,"eye");JsonArray positions=test.getAsJsonArray("vertices"),clips=test.getAsJsonArray("driverClipBits");
            for(int i=0;i<positions.size();i++){JsonArray p=positions.get(i).getAsJsonArray();float[] first=new float[4],result=new float[4];ModernArmorMvpMath.clip(eye,p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat(),first);ModernArmorMvpMath.clip(projection,first[0],first[1],first[2],result);if(!java.util.Arrays.equals(bits(result),words(clips.get(i).getAsJsonArray())))different++;}}
        assertTrue(different>0);
    }
    @Test void aliasedAndWrongSizedMatricesAreRejected(){float[] matrix=new Matrix4f().get(new float[16]);assertThrows(IllegalArgumentException.class,()->ModernArmorMvpMath.multiply(matrix,matrix,matrix));assertThrows(IllegalArgumentException.class,()->ModernArmorMvpMath.multiply(new float[15],matrix,new float[16]));assertThrows(IllegalArgumentException.class,()->ModernArmorMvpMath.clip(matrix,0,0,0,new float[3]));}
    @Test void finiteProjectiveRowsShareBoundedUploadArenaWithoutWeakeningAffineValidation(){
        ModernGlintMatrices arena=new ModernGlintMatrices();ModernArmorProjectiveMatrices projective=new ModernArmorProjectiveMatrices(arena);Matrix4f perspective=new Matrix4f().perspective(1.1f,16f/9,.05f,128);
        assertEquals(-1,arena.intern(perspective));float[] data=perspective.get(new float[16]);int id=projective.intern(data);assertEquals(0,id);assertEquals(id,projective.intern(data.clone()));assertEquals(1,arena.count());
        assertEquals(-1,projective.intern(new float[15]));data[0]=Float.NaN;assertEquals(-1,projective.intern(data));assertEquals(1,arena.count());assertEquals(-1,arena.intern(perspective));
    }
    @Test void textureAndPositionRowsCannotOverflowOrKeepPriorFrameIndices(){
        ModernGlintMatrices arena=new ModernGlintMatrices();ModernArmorProjectiveMatrices projective=new ModernArmorProjectiveMatrices(arena);float[] matrix=new Matrix4f().perspective(1,1,.1f,100).get(new float[16]);
        assertEquals(0,arena.intern(new Matrix4f()));for(int i=1;i<ModernGlintMatrices.LIMIT;i++){matrix[12]=i;assertEquals(i,projective.intern(matrix));}assertEquals(0,projective.remaining());matrix[12]=ModernGlintMatrices.LIMIT;assertEquals(-1,projective.intern(matrix));arena.beginFrame();assertEquals(0,projective.intern(matrix));assertEquals(1,arena.count());
    }
}
