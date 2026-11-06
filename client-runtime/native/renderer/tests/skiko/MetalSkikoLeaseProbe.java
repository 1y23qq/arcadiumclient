// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.jetbrains.skia.*;
import static java.lang.foreign.ValueLayout.*;

public final class MetalSkikoLeaseProbe {
    private static final LinkedHashMap<String,Object> report=new LinkedHashMap<>();
    private static final ArrayList<Map<String,Object>> frames=new ArrayList<>();
    public static void main(String[] args)throws Exception {
        require(args.length==5,"Pass native library/hash, reference shader, actual UI font and report path");
        Path result=Path.of(args[4]);boolean passed=false;
        try {
            require(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac"),"Native Skiko Metal smoke requires macOS");
            Path library=Path.of(args[0]);require(hash(library).equals(args[1]),"Native library checksum mismatch");
            String source=Files.readString(Path.of(args[2]));Path fontFile=Path.of(args[3]);
            report.put("nativeLibrarySha256",args[1]);report.put("actualUiFontSha256",hash(fontFile));
            try(MetalResidentSceneBackend backend=MetalResidentSceneBackend.open(library,new ResidentSceneBackend.Layout(1,80,20,64),source,"vertexMain","fragmentMain")) {
                MethodHandle query=Linker.nativeLinker().downcallHandle(backend.presentationSymbols().find("arc_scene_query").orElseThrow(),FunctionDescriptor.of(JAVA_LONG,ADDRESS,JAVA_INT));
                MemorySegment context=backend.presentationContext();
                backend.putPipeline(1,source,"vertexMain","fragmentMain",new ScenePasses.Pipeline(0,ScenePasses.ALWAYS,0,0,1,0,1,0,0,0,15));
                try(MetalDeviceLease device=backend.metalDeviceLease()) {
                    DirectContext skia=device.createResource((nativeDevice,queue)->DirectContext.Companion.makeMetal(nativeDevice,queue));
                    skia.setResourceCacheLimit(16L*1024*1024);
                    Typeface face=device.createResource((d,q)->FontMgr.Companion.getDefault().makeFromFile(fontFile.toString(),0));
                    Font font=device.createResource((d,q)->new Font(face,18));
                    ImageFilter blur=device.createResource((d,q)->ImageFilter.Companion.makeBlur(3,3,FilterTileMode.DECAL,null,null));
                    Paint blurred=device.createResource((d,q)->new Paint());blurred.setColor(0xffff0000);blurred.setImageFilter(blur);
                    Paint green=device.createResource((d,q)->new Paint());green.setColor(0xff00ff00);
                    Paint text=device.createResource((d,q)->new Paint());text.setColor(0xffffffff);text.setAntiAlias(true);
                    int[] widths={192,224,208},heights={96,128,112};long previousGeneration=0;
                    ByteBuffer uniforms=ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN),commands=ByteBuffer.allocateDirect(0);
                    for(int i=0;i<16;i++)uniforms.putFloat(i*4,i%5==0?1:0);
                    for(int i=0;i<3;i++) {
                        int width=widths[i],height=heights[i];ByteBuffer passes=ByteBuffer.allocateDirect(ScenePasses.PASS_BYTES);
                        ScenePasses.write(passes,List.of(new ScenePasses.Pass(1,0,0,0,64,5,3,0,0,0,width,height,0,0,0,0,.25f,.5f,.75f,1,1,0,1)));
                        require(backend.submitPasses(width,height,uniforms,passes,1,commands,0),"The previous frame did not retire");
                        long frame=backend.statistics().submittedFrames(),generation;
                        try(MetalDeviceLease.Target target=device.target(frame)) {
                            generation=target.generation();require(generation>previousGeneration&&target.width()==width&&target.height()==height,"Target generation or dimensions are stale");
                            BackendRenderTarget brt=target.createResource((texture,w,h)->BackendRenderTarget.Companion.makeMetal(w,h,texture));
                            Surface surface=target.createResource((texture,w,h)->Surface.Companion.makeFromBackendRenderTarget(skia,brt,SurfaceOrigin.TOP_LEFT,SurfaceColorFormat.BGRA_8888,ColorSpace.Companion.getSRGB(),null));
                            Canvas canvas=surface.getCanvas();canvas.drawRect(4,4,24,24,green);canvas.drawString("Metal "+(i+1),6,65,font,text);canvas.drawRect(100,10,120,30,blurred);
                            surface.flushAndSubmit(false);
                        }
                        backend.awaitFrame();ByteBuffer pixels=ByteBuffer.allocateDirect(width*height*4);backend.readRgba(pixels);
                        require(color(pixels,width,8,8,0,255,0),"Actual Metal Skiko rectangle is absent");
                        require(color(pixels,width,0,0,64,128,191),"The native scene background was not retained");
                        long textPixels=0,blurPixels=0;
                        for(int y=43;y<68;y++)for(int x=4;x<90;x++)if(!color(pixels,width,x,y,64,128,191))textPixels++;
                        for(int y=12;y<28;y++)for(int x=95;x<100;x++)if(!color(pixels,width,x,y,64,128,191))blurPixels++;
                        require(textPixels>20&&blurPixels>10,"Actual glyph or image-filter blur work is absent");
                        long targetCount=(long)query.invokeExact(context,11),deviceCount=(long)query.invokeExact(context,10);
                        require(targetCount==0&&deviceCount==1,"Frame lease did not retire while the device stayed owned");
                        Map<String,Object> row=new LinkedHashMap<>();row.put("frame",frame);row.put("generation",generation);row.put("width",width);row.put("height",height);
                        row.put("textPixels",textPixels);row.put("blurOutsideSourcePixels",blurPixels);row.put("diagnosticRgbaSha256",hash(pixels));row.put("targetLeasesAfterAwait",targetCount);frames.add(row);previousGeneration=generation;
                    }
                    report.put("actualMetalDirectContext",true);report.put("actualMetalBackendRenderTargets",3);
                }
                backend.awaitFrame();long deviceCount=(long)query.invokeExact(context,10),targetCount=(long)query.invokeExact(context,11);
                require(deviceCount==0&&targetCount==0,"Device or frame lifetime was not retired");report.put("leaseCountsZeroBeforeBackendClose",true);
            }
            passed=true;
        }catch(Throwable failure){report.put("failureClass",failure.getClass().getName());report.put("failure",String.valueOf(failure.getMessage()));failure.printStackTrace();}
        finally {
            report.put("passed",passed);report.put("frames",frames);report.put("skikoVersion","0.154.0");report.put("graphicsApi","Metal");
            report.put("worldSceneInput","native clear-pass correctness fixture");report.put("diagnosticReadbacks",frames.size());report.put("skikoCpuSyncRequested",false);
            report.put("minecraftWorldOrCeraQualified",false);report.put("oneConfigUiSwitched",false);report.put("displayedQualified",false);report.put("performanceClaim",false);
            Files.writeString(result,json(report)+"\n");
        }
        if(!passed)throw new IllegalStateException("Actual pinned Skiko Metal lease smoke failed");
    }
    private static boolean color(ByteBuffer p,int width,int x,int y,int r,int g,int b) {
        int offset=(y*width+x)*4;return Math.abs((p.get(offset)&255)-r)<=1&&Math.abs((p.get(offset+1)&255)-g)<=1
                &&Math.abs((p.get(offset+2)&255)-b)<=1&&(p.get(offset+3)&255)==255;
    }
    private static String hash(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static String hash(ByteBuffer bytes)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(bytes.duplicate());return HexFormat.of().formatHex(digest.digest());}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static String json(Object value) {
        if(value==null)return "null";if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map<?,?> map){ArrayList<String> entries=new ArrayList<>();map.forEach((k,v)->entries.add(json(k.toString())+":"+json(v)));return "{"+String.join(",",entries)+"}";}
        if(value instanceof Iterable<?> iterable){ArrayList<String> entries=new ArrayList<>();iterable.forEach(v->entries.add(json(v)));return "["+String.join(",",entries)+"]";}
        StringBuilder out=new StringBuilder("\"");for(char c:value.toString().toCharArray()){if(c=='"'||c=='\\')out.append('\\').append(c);else if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}return out.append('"').toString();
    }
}
