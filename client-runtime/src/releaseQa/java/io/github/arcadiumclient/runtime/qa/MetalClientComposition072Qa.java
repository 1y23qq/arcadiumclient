// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumMainMenu;
import io.github.arcadiumclient.runtime.client.optimizations.backend.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import pl.tomgirl.pylon.window.*;
import static org.lwjgl.sdl.SDLVideo.*;

public final class MetalClientComposition072Qa {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static final List<Map<String,Object>> captures=new ArrayList<>();
    private static Map<String,Object> marker;
    private static Path root,evidence;
    private static MetalResidentSceneBackend backend;
    private static MetalCompositeSurface metal;
    private static Surface original,outer;
    private static Screen title;
    private static boolean initialized,stopping,destroyed,explicitFallback,failed,control,defaultsChecked,resizeRequested;
    private static int phase,ticks,phaseFrames,frames,skikoFlushes,skikoStart,composeSubmits,composeStart;
    private static long submittedStart;
    private static String capture;

    public static boolean enabled(){return System.getProperty("arcadium.metalClient072.fixture")!=null;}
    @SuppressWarnings("unchecked")
    public static void beforeCreate(DisplaySdl display,GpuSurface fallback) {
        if(!enabled())return;
        try {
            require(original==null&&!display.isCreated(),"Metal QA attempted a second display");
            Path file=Path.of(System.getProperty("arcadium.metalClient072.fixture")).toRealPath();
            marker=GSON.fromJson(Files.readString(file),Map.class);
            require("arcadium.metal-client072.fixture.v1".equals(marker.get("schema"))&&Boolean.TRUE.equals(marker.get("syntheticOffline")),"Missing owned fake fixture");
            root=Path.of((String)marker.get("root")).toRealPath();evidence=Path.of((String)marker.get("evidence")).toRealPath();
            require(file.getParent().equals(root)&&evidence.startsWith(root),"Fixture paths escape the owned root");
            require(Path.of(System.getProperty("user.home")).toRealPath().equals(root.resolve("home")),"Unexpected user HOME");
            require(Path.of(System.getProperty("java.io.tmpdir")).toRealPath().equals(root.resolve("tmp")),"Unexpected temporary directory");
            require(Path.of(System.getProperty("fabric.modsFolder")).toRealPath().equals(root.resolve("mods")),"Unexpected mods directory");
            for(String prefix:List.of("http","https","socks")) {
                String host=prefix.equals("socks")?"socksProxyHost":prefix+".proxyHost";
                String port=prefix.equals("socks")?"socksProxyPort":prefix+".proxyPort";
                require("127.0.0.1".equals(System.getProperty(host))&&"9".equals(System.getProperty(port)),"Gameplay network is not blocked");
            }
            control=Boolean.TRUE.equals(marker.get("controlGl"));
            java.lang.reflect.Field configured=DisplaySdl.class.getDeclaredField("surface");configured.setAccessible(true);
            GpuSurface selected=(GpuSurface)configured.get(display);if(selected==null)selected=fallback;
            require(selected.getClass()==GlSurface.class,"Unknown original GPU surface");
            original=new Surface(selected,false);
            GpuSurface delegate=original;
            if(!control) {
                Path library=verified("nativeLibrary","nativeSha256");Path shader=verified("referenceShader","referenceShaderSha256");
                backend=MetalResidentSceneBackend.open(library,new ResidentSceneBackend.Layout(2,80,20,64),Files.readString(shader),"vertexMain","fragmentMain");
                metal=MetalCompositeSurface.prototype(backend,original,MetalCompositeSurface.RGBA8_LINEAR);delegate=metal;
            }
            outer=new Surface(delegate,true);display.setSurface(outer);
            report.put("fixtureOnly",true);report.put("minecraft",true);report.put("controlGl",control);
            report.put("performanceClaim",false);report.put("worldOrCeraQualified",false);report.put("backendAutomaticActivation",false);
        } catch(Throwable error){failed=true;throw new IllegalStateException("Private Metal client fixture rejected",error);}
    }
    private static Path verified(String pathKey,String hashKey)throws Exception {
        Path path=Path.of((String)marker.get(pathKey)).toRealPath();String expected=(String)marker.get(hashKey);
        require(expected!=null&&expected.matches("[a-f0-9]{64}")&&expected.equals(hash(path)),"Native/source input checksum mismatch");return path;
    }
    public static void initialized(){if(enabled())initialized=true;}
    public static void skikoFlush(){if(enabled()&&!stopping)skikoFlushes++;}
    public static void composeSubmit(){if(enabled()&&!stopping)composeSubmits++;}
    public static void tick() {
        if(!enabled()||stopping||!initialized)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken()),"Real accounts are forbidden");
            require(mc.world==null,"The first composition fixture cannot join a world or server");
            require(++ticks<2400,"Composition fixture timeout");
            if(phase==0&&ticks>35) {
                checkDefaults();
                ClientPreferences.setSetupComplete(true);title=new ArcadiumMainMenu();mc.openScreen(title);next(1);return;
            }
            if(phaseFrames<30||capture!=null)return;
            if(phase==1&&mc.screen==title&&ready()) {capture="title";next(2);}
            else if(phase==2&&doneCapture("title")) {OneConfigUI.open();next(3);}
            else if(phase==3&&mc.screen!=null&&"OneConfigUIScreen".equals(mc.screen.getClass().getSimpleName())&&ready()) {capture="options";next(4);}
            else if(phase==4&&doneCapture("options")) {mc.openScreen(title);next(5);}
            else if(phase==5&&mc.screen==title&&ready()) {
                resizeRequested=true;
            } else if(phase==6&&DisplaySdl.instance().getWindowWidth()==1152&&DisplaySdl.instance().getWindowHeight()==720&&ready()) {capture="title-resized";next(7);}
            else if(phase==7&&doneCapture("title-resized")) {
                if(backend!=null)backend.close();explicitFallback=true;next(8);
            } else if(phase==8&&original.swaps>0&&ready()) {capture="title-gl-fallback";next(9);}
            else if(phase==9&&doneCapture("title-gl-fallback")) {stopping=true;report.put("requestedNormalShutdown",true);write(false);mc.stop();}
        } catch(Throwable error){fail(error);}
    }
    public static void frame() {
        if(!enabled()||stopping||!initialized)return;
        try {
            frames++;phaseFrames++;
            if(!control&&!explicitFallback&&metal!=null)require(metal.fallbackFrames()==0,"Unexpected early GL fallback: "+metal.status());
        } catch(Throwable error){fail(error);}
    }
    public static void stopping(){if(enabled())report.put("minecraftStopObserved",true);}
    private static boolean ready() {
        if(skikoFlushes-skikoStart<3||composeSubmits-composeStart<3)return false;
        return control||explicitFallback||metal.submittedFrames()-submittedStart>=3&&metal.nativeCounter(4)>0;
    }
    @SuppressWarnings("unchecked")
    private static void checkDefaults()throws Exception {
        if(defaultsChecked)return;
        Map<String,Boolean> expected=(Map<String,Boolean>)marker.get("expectedBooleanDefaults");
        require(expected!=null&&expected.size()==21,"Missing explicit user-default certificate");
        Class<?> type=Class.forName("io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig");
        Object config=type.getField("INSTANCE").get(null);type.getMethod("preload").invoke(config);
        Map<String,Boolean> actual=new LinkedHashMap<>();
        for(var entry:expected.entrySet()) {
            String getter="get"+Character.toUpperCase(entry.getKey().charAt(0))+entry.getKey().substring(1);
            actual.put(entry.getKey(),(Boolean)type.getMethod(getter).invoke(null));
        }
        require(actual.equals(expected),"The fresh profile did not load the declared Beta defaults");
        report.put("loadedBetaBooleansBeforeOverrides",actual);report.put("freshDefaultOnCount",actual.values().stream().filter(Boolean::booleanValue).count());
        report.put("freshDefaultsVerified",true);defaultsChecked=true;
        if(Boolean.TRUE.equals(marker.get("storedOffReloadProof"))) {
            Class<?> managers=Class.forName("org.polyfrost.oneconfig.api.config.v1.ConfigManager");Object manager=managers.getMethod("active").invoke(null);
            Path folder=((Path)managers.getMethod("getFolder").invoke(manager)).toAbsolutePath().normalize();require(folder.startsWith(root),"Config backend escaped the owned fixture");
            java.lang.reflect.Method setter=type.getMethod("setCachedFontMetrics",boolean.class),getter=type.getMethod("getCachedFontMetrics");
            setter.invoke(null,false);type.getMethod("save").invoke(config);setter.invoke(null,true);
            java.lang.reflect.Field backendField=managers.getDeclaredField("backend");backendField.setAccessible(true);Object backend=backendField.get(manager);
            Object tree=type.getMethod("getTree").invoke(config);Class<?> treeType=Class.forName("org.polyfrost.oneconfig.api.config.v1.Tree");
            require(Boolean.TRUE.equals(backend.getClass().getMethod("load",treeType).invoke(backend,tree)),"Owned stored preference reload failed");
            require(Boolean.FALSE.equals(getter.invoke(null)),"A saved OFF choice was overwritten by the new ON default");
            report.put("isolatedStoredOffBackendReload",Map.of("property","cachedFontMetrics","savedOffRetained",true,"insideOwnedFixture",true,"sameJvmReloadOnly",true));
        }
    }
    private static void next(int value){
        phase=value;phaseFrames=0;skikoStart=skikoFlushes;composeStart=composeSubmits;submittedStart=metal==null?0:metal.submittedFrames();
        try{Files.writeString(evidence.resolve("status.json"),GSON.toJson(Map.of("phase",phase,"frames",frames,"composeSubmits",composeSubmits,"successfulSkikoGlFlushes",skikoFlushes)));}
        catch(Exception error){throw new IllegalStateException("Could not save owned QA phase",error);}
    }
    private static boolean doneCapture(String name){return captures.stream().anyMatch(row->name.equals(row.get("name")));}
    private static void afterSwap() {
        if(!resizeRequested||failed||stopping)return;
        try {
            require(phase==5,"Unexpected resize boundary");
            require(control||metal!=null&&metal.fallbackFrames()==0,"Resize preceded a successful Metal swap");
            MainThread.run(()->{require(SDL_SetWindowSize(original.window,1152,720),"SDL resize rejected");require(SDL_SyncWindow(original.window),"SDL resize did not finalize");});
            resizeRequested=false;report.put("resizeRequestedAfterSuccessfulSwap",true);next(6);
        } catch(Throwable error){fail(error);}
    }
    private static void beforeSwap() {
        if(capture==null||failed)return;
        try {
            require(GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)==0,"Composition is not resolved to the default framebuffer");
            require(GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)==0,"Unexpected pixel-pack buffer");
            int width=DisplaySdl.instance().getWidth(),height=DisplaySdl.instance().getHeight();
            require(width>0&&height>0&&(long)width*height<=8_000_000,"Invalid capture dimensions");
            ByteBuffer bytes=ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(width,height),4)).order(ByteOrder.nativeOrder());
            int read=GL11.glGetInteger(GL11.GL_READ_BUFFER),pack=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            try {GL11.glReadBuffer(GL11.GL_BACK);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);}
            finally {GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,pack);GL11.glReadBuffer(read);}
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"GL error during actual composition capture");
            BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Set<Integer> colors=new HashSet<>();
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){int p=((height-1-y)*width+x)*4;int color=0xff000000|(bytes.get(p)&255)<<16|(bytes.get(p+1)&255)<<8|(bytes.get(p+2)&255);image.setRGB(x,y,color);if(colors.size()<1024)colors.add(color);}
            require(colors.size()>32,"The actual composed source image is blank");String name=capture;capture=null;Path file=evidence.resolve(name+".png");ImageIO.write(image,"png",file.toFile());
            Map<String,Object> row=new LinkedHashMap<>();row.put("name",name);row.put("width",width);row.put("height",height);row.put("skikoFlushes",skikoFlushes);
            row.put("composeSubmits",composeSubmits);row.put("metalSubmitted",metal==null?0:metal.submittedFrames());row.put("metalDisplayed",metal==null?0:metal.nativeCounter(4));row.put("originalGlSwaps",original.swaps);row.put("sha256",hash(file));captures.add(row);
        }catch(Throwable error){fail(error);}
    }
    private static void fail(Throwable error) {
        failed=true;stopping=true;report.put("failureClass",error.getClass().getName());report.put("failure",String.valueOf(error.getMessage()));
        try{write(false);}catch(Throwable ignored){}try{Minecraft.getInstance().stop();}catch(Throwable ignored){}
    }
    private static void write(boolean finalResult)throws Exception {
        report.put("phase",phase);report.put("frames",frames);report.put("skikoFlushes",skikoFlushes);report.put("composeSubmits",composeSubmits);report.put("captures",captures);
        report.put("normalShutdownComplete",destroyed);report.put("passed",finalResult&&!failed&&stopping&&destroyed&&captures.size()==4&&original.destroys==1&&Boolean.TRUE.equals(report.get("minecraftStopObserved")));
        Files.writeString(evidence.resolve("result.json"),GSON.toJson(report));
    }
    private static String hash(Path path)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static final class Surface implements GpuSurface {
        final GpuSurface delegate;final boolean outer;long window;int swaps,destroys;
        Surface(GpuSurface delegate,boolean outer){this.delegate=delegate;this.outer=outer;}
        @Override public long createWindow(String title,int width,int height,boolean resizable){window=delegate.createWindow(title,width,height,resizable);if(!outer)delegate.setVSyncEnabled(true);return window;}
        @Override public void makeCurrent(){delegate.makeCurrent();}
        @Override public void releaseCurrent(){delegate.releaseCurrent();}
        @Override public void setVSyncEnabled(boolean enabled){delegate.setVSyncEnabled(enabled);}
        @Override public void swapBuffers(){if(outer)beforeSwap();else{require(metal==null||metal.nativeCounter(6)==0||metal.nativeCounter(7)==1,"GL swapped before hiding the old Metal layer");swaps++;}delegate.swapBuffers();if(outer)afterSwap();}
        @Override public void destroy(){
            if(!outer)require(metal==null||metal.nativeCounter(6)==0,"Original window destruction preceded child detachment");
            delegate.destroy();destroys++;
            if(outer){try{if(backend!=null)backend.close();destroyed=true;write(true);}catch(Throwable error){fail(error);}}
        }
    }
}
