// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.render.model.block.BakedQuad;
import org.lwjgl.opengl.*;

public final class PlayerFeatures073QuadEvidence {
    private static final String MODE=System.getProperty("arcadium.playerFeatures073.quadMode","off");
    private static final String UNPACKED="net.minecraftforge.client.model.pipeline.UnpackedBakedQuad";
    private static final ThreadLocal<ArrayDeque<Build>> CURRENT=ThreadLocal.withInitial(ArrayDeque::new);
    private static final List<Map<String,Object>> records=new ArrayList<>();
    private static final IdentityHashMap<Object,Boolean> packedQuads=new IdentityHashMap<>();
    private static String failure;
    private static long consumed,getterCalls,mirroredReturns,unsupportedClasses;
    private static final class Build {
        final int start;final Map<String,Object> record=new LinkedHashMap<>();final List<Map<String,Object>> quads=new ArrayList<>();int count;
        Build(FloatBuffer buffer,Iterable<?> iterable){start=buffer.position();record.put("context",PlayerFeatures073Qa.quadContext());record.put("iterableClass",iterable.getClass().getName());record.put("iterableTraversedByObserver",false);}
    }
    private PlayerFeatures073QuadEvidence(){}
    public static String mode(){return MODE;}
    public static boolean enabled(){return !MODE.equals("off");}
    public static Object begin(FloatBuffer buffer,Iterable<?> iterable){
        if(!enabled()||!PlayerFeatures073Qa.observingQuadBuild())return null;
        try{ArrayDeque<Build> stack=CURRENT.get();if(stack.size()==8)throw new IllegalStateException("Quad observation nesting exceeded");Build build=new Build(buffer,iterable);stack.push(build);return build;}
        catch(Throwable error){failed(error);return null;}
    }
    public static Object consume(Object quad){
        if(!enabled())return quad;ArrayDeque<Build> stack=CURRENT.get();if(stack.isEmpty())return quad;
        try{
            Build build=stack.peek();build.count++;consumed++;boolean sample=records.size()<32&&build.quads.size()<8;
            Map<String,Object> data=sample?snapshot(quad):null;
            if(MODE.equals("pack")&&quad.getClass().getName().equals(UNPACKED)&&!packedQuads.containsKey(quad)){
                if(packedQuads.size()==16384)throw new IllegalStateException("Quad diagnostic identity budget exceeded");
                packedQuads.put(quad,true);int[] returned=((BakedQuad)quad).getVertices();getterCalls++;
                Object backing=field(quad,"vertices","f_40952693");boolean mirrored=returned==backing;if(mirrored)mirroredReturns++;
                if(!mirrored)throw new IllegalStateException("Official virtual getter did not mirror the native backing array");
                if(sample){data.put("officialVirtualGetterCalledOnce",true);data.put("returnedArrayIsNativeBacking",mirrored);data.put("afterGetter",snapshot(quad));}
            }else if(MODE.equals("pack")&&!quad.getClass().getName().equals(UNPACKED))unsupportedClasses++;
            if(sample)build.quads.add(data);
        }catch(Throwable error){failed(error);}
        return quad;
    }
    public static void end(Object token,FloatBuffer buffer){
        if(token==null)return;
        try{
            ArrayDeque<Build> stack=CURRENT.get();if(stack.isEmpty()||stack.peek()!=token)throw new IllegalStateException("Quad observation scope order changed");Build build=stack.pop();
            if(build.count!=0&&records.size()<32){build.record.put("actualQuadsConsumed",build.count);build.record.put("quads",build.quads);build.record.put("nativeEmittedBuffer",bufferSummary(buffer,build.start));build.record.put("textureState",textureState());records.add(build.record);persist();}
        }catch(Throwable error){failed(error);}
    }
    private static Map<String,Object> snapshot(Object quad)throws Exception{
        Map<String,Object> data=new LinkedHashMap<>();data.put("quadClass",quad.getClass().getName());data.put("quadIdentity",System.identityHashCode(quad));
        int[] raw=(int[])field(quad,"vertices","f_40952693");int scanned=Math.min(raw.length,4096),nonzero=0;for(int i=0;i<scanned;i++)if(raw[i]!=0)nonzero++;
        data.put("nativeBackingLength",raw.length);data.put("nativeBackingIdentity",System.identityHashCode(raw));data.put("nativeBackingSampledInts",scanned);data.put("nativeBackingFullyScanned",scanned==raw.length);data.put("nativeBackingNonzeroInts",nonzero);
        if(quad.getClass().getName().equals(UNPACKED)){
            data.put("forgePacked",field(quad,"packed"));float[][][] unpacked=(float[][][])field(quad,"unpackedData");int nonzeroFloats=0;List<List<Float>> first=new ArrayList<>();
            for(int vertex=0;vertex<Math.min(4,unpacked.length);vertex++){List<Float> element=new ArrayList<>();for(int part=0;part<Math.min(16,unpacked[vertex].length);part++)for(int component=0;component<Math.min(4,unpacked[vertex][part].length);component++){float value=unpacked[vertex][part][component];if(!Float.isFinite(value))throw new IllegalStateException("Nonfinite official unpacked data");if(value!=0)nonzeroFloats++;if(part==0)element.add(value);}first.add(element);}
            data.put("unpackedNonzeroFloats",nonzeroFloats);data.put("unpackedFirstElementByVertex",first);data.put("forgeFormatClass",field(quad,"format").getClass().getName());
        }
        return data;
    }
    private static Object field(Object owner,String...names)throws Exception{
        for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())for(String name:names)try{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(NoSuchFieldException next){}
        throw new NoSuchFieldException(Arrays.toString(names));
    }
    private static Map<String,Object> bufferSummary(FloatBuffer original,int start){
        int end=original.position(),written=end-start;if(written<0||written%12!=0)throw new IllegalStateException("Unknown pinned baked vertex layout");
        FloatBuffer data=original.duplicate();int vertices=written/12,scanned=Math.min(vertices,4096),nonzeroPositions=0,nonzeroUv=0,areaQuads=0;float[] low={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},high={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};float[] uvLow={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY},uvHigh={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for(int vertex=0;vertex<scanned;vertex++){
            int at=start+vertex*12;for(int axis=0;axis<3;axis++){float value=data.get(at+axis);if(!Float.isFinite(value))throw new IllegalStateException("Nonfinite native baked position");if(value!=0)nonzeroPositions++;low[axis]=Math.min(low[axis],value);high[axis]=Math.max(high[axis],value);}
            for(int axis=0;axis<2;axis++){float value=data.get(at+3+axis);if(!Float.isFinite(value))throw new IllegalStateException("Nonfinite native baked UV");if(value!=0)nonzeroUv++;uvLow[axis]=Math.min(uvLow[axis],value);uvHigh[axis]=Math.max(uvHigh[axis],value);}
        }
        for(int quad=0;quad+3<scanned;quad+=4){int a=start+quad*12,b=a+12,c=a+24;double x=data.get(b)-data.get(a),y=data.get(b+1)-data.get(a+1),z=data.get(b+2)-data.get(a+2),u=data.get(c)-data.get(a),v=data.get(c+1)-data.get(a+1),w=data.get(c+2)-data.get(a+2);if(y*w-z*v!=0||z*u-x*w!=0||x*v-y*u!=0)areaQuads++;}
        Map<String,Object> result=new LinkedHashMap<>();result.put("floatStride",12);result.put("start",start);result.put("end",end);result.put("vertexCount",vertices);result.put("scannedVertices",scanned);result.put("fullyScanned",scanned==vertices);result.put("nonzeroPositionComponents",nonzeroPositions);result.put("nonzeroUvComponents",nonzeroUv);result.put("nondegenerateQuads",areaQuads);result.put("xyzMinimum",scanned==0?"empty":low);result.put("xyzMaximum",scanned==0?"empty":high);result.put("uvMinimum",scanned==0?"empty":uvLow);result.put("uvMaximum",scanned==0?"empty":uvHigh);result.put("observerChangedNativeBufferPosition",original.position()!=end);return result;
    }
    private static Map<String,Object> textureState(){try{int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);return Map.of("activeTexture",GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),"binding2D",binding,"width",binding==0?0:GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),"height",binding==0?0:GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT));}catch(IllegalStateException noContext){return Map.of("context","not current");}}
    private static void failed(Throwable error){if(failure==null)failure=error.toString();try{persist();}catch(Exception ignored){}}
    public static Map<String,Object> summary(){Map<String,Object> result=new LinkedHashMap<>();result.put("mode",MODE);result.put("nativeBuildsCaptured",records.size());result.put("actualConsumedQuads",consumed);result.put("officialGetterCalls",getterCalls);result.put("getterReturnsMirroredInNativeBacking",mirroredReturns);result.put("unsupportedPackClassesLeftUntouched",unsupportedClasses);result.put("arbitraryIterableTraversals",0);result.put("failure",failure);return result;}
    private static void persist()throws Exception{Map<String,Object> result=new LinkedHashMap<>(summary());result.put("builds",records);Path path=Path.of(System.getProperty("arcadium.playerFeatures073.output")).toAbsolutePath().resolveSibling("quad-evidence.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(result));}
}
