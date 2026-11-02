// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.optimizations.RenderHookAllocationControl;
import java.awt.image.BufferedImage;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.*;
import net.minecraft.entity.Entity;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class ParticleOperation066Qa {
    private static final int WIDTH=640,HEIGHT=360;
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static int ticks,phase,wait,framebuffer,color,depth;
    private static boolean done,fast,silent;
    private static Object manager;
    private static MethodHandle original,typed,fade,rendering,renderedEntity,setRendering,setEntity;
    private static Operation<Void> nativeRender,nativeFade;
    private static List<String> trace;
    private static Fixture outer,inner;
    private static Field nativeConfig,resolved;
    private static Method enabled,fadeEnabled,fadeStart,customColor,colorMode,chroma,chromaSpeed;
    private static Object normalStyle,blockStyle,styleColor;
    private static int calls;
    private ParticleOperation066Qa() { }
    public static boolean enabled(){return System.getProperty("arcadium.particleOperations066.output")!=null;}
    public static void tick(){
        if(done)return;Minecraft mc=Minecraft.getInstance();
        try{
            require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken()),"Particle operation QA requires the synthetic offline fixture");
            require(++ticks<1800,"Particle operation QA timed out");wait++;
            if(phase==0&&ticks>65&&mc.screen!=null){
                ClientPreferences.setSetupComplete(true);mc.options.pauseOnUnfocus=false;mc.options.vsync=false;Display.setVSyncEnabled(false);
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;
                mc.startGame("arcadium-particle-operations066","Particle Operations QA",new WorldSettings(66066,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=1;wait=0;
            }else if(phase==1&&mc.world!=null&&mc.player!=null&&wait>60){mc.openScreen(null);verify();finish(null);}
        }catch(Throwable error){finish(error);}
    }
    private record Image(int[] words,byte[] rgba,List<String> events,List<Integer> state,int alpha,int nestedAlpha,int calls,boolean rendering,int owner,String error) { }
    private static void verify()throws Throwable {
        Minecraft mc=Minecraft.getInstance();boolean previous=RenderHookAllocationControl.enabled;
        double cameraX=Particle.cameraX,cameraY=Particle.cameraY,cameraZ=Particle.cameraZ;
        int oldFbo=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);IntBuffer viewport=BufferUtils.createIntBuffer(4);GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{
            initialize(mc);target();List<String> cases=new ArrayList<>();Map<String,Integer> coverage=new LinkedHashMap<>();report.put("visiblePixelsPerCase",coverage);long pixelBytes=0,wordChecks=0;
            for(int scenario=0;scenario<13;scenario++){
                configure(scenario);Image reference=image(false,scenario),actual=image(true,scenario);
                coverage.put(Integer.toString(scenario),visible(reference.rgba));
                require(Arrays.equals(reference.words,actual.words),"Particle vertex words changed at case "+scenario);
                require(Arrays.equals(reference.rgba,actual.rgba),"Particle RGBA pixels changed at case "+scenario);
                require(reference.events.equals(actual.events)&&reference.state.equals(actual.state),"Particle event order or GL tail changed at case "+scenario);
                require(reference.alpha==actual.alpha&&reference.nestedAlpha==actual.nestedAlpha&&reference.calls==actual.calls&&reference.rendering==actual.rendering&&reference.owner==actual.owner&&Objects.equals(reference.error,actual.error),"Particle state/exception changed at case "+scenario);
                if(scenario==1){write("particle-operation-original.png",reference.rgba);write("particle-operation-typed.png",actual.rgba);require(reference.words.length>0,"Custom virtual particle did not render");}
                if(scenario==1||scenario==7||scenario==8)require(visible(reference.rgba)>100,"Particle pixel fixture did not cover 100 pixels at case "+scenario+": "+visible(reference.rgba));
                if(scenario==2)require(reference.calls==0,"Disabled non-block style did not preserve the native skip");
                if(scenario==3)require(reference.calls==1,"Disabled block style no longer used the native block path");
                if(scenario==5)require(reference.calls==2&&Math.abs(Float.intBitsToFloat(reference.alpha)-.1125f)<.00001,"Native render-time fade did not evolve twice in one tick");
                if(scenario==9||scenario==11)require(reference.owner==2,"Nested particle state was restored differently from native behavior");
                if(scenario==10||scenario==11)require(reference.error!=null&&reference.rendering,"The native exceptional rendering state was changed");
                cases.add(switch(scenario){case 0->"unknown style";case 1->"enabled custom virtual renderer";case 2->"disabled style";case 3->"disabled block style";case 4->"live fade";case 5->"two renders without a tick";case 6->"zero fade start";case 7->"native billboard and custom color";case 8->"native chroma path with speed zero";case 9->"nested virtual render";case 10->"throwing custom render";case 11->"nested exception";default->"preexisting rendering state";});
                pixelBytes+=reference.rgba.length;wordChecks+=reference.words.length;
            }
            report.put("cases",cases);report.put("rgbaBytesCompared",pixelBytes);report.put("nativeVertexWordsCompared",wordChecks);
            allocation();require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL error after typed particle verification");
            report.put("particleSites",RenderHookAllocationControl.particleSites);report.put("renderer",GL11.glGetString(GL11.GL_RENDERER));report.put("glVersion",GL11.glGetString(GL11.GL_VERSION));
        }finally{
            trace=null;silent=false;RenderHookAllocationControl.enabled=previous;
            Particle.cameraX=cameraX;Particle.cameraY=cameraY;Particle.cameraZ=cameraZ;
            if(setRendering!=null)setRendering.invokeExact(false);if(setEntity!=null)setEntity.invokeExact((Particle)null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,oldFbo);if(depth!=0)GL30.glDeleteRenderbuffers(depth);if(framebuffer!=0)GL30.glDeleteFramebuffers(framebuffer);if(color!=0)GlStateManager.deleteTextures(color);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
        }
    }
    private static void initialize(Minecraft mc)throws Throwable {
        require(RenderHookAllocationControl.particleSites==2,"The actual ParticleManager did not receive both typed gates");
        manager=new ParticleManager(mc.world,mc.getTextureManager());
        MethodType signature=MethodType.methodType(void.class,Object.class,Particle.class,BufferBuilder.class,Entity.class,float.class,float.class,float.class,float.class,float.class,float.class);
        Method originalMethod=Arrays.stream(ParticleManager.class.getDeclaredMethods()).filter(method->method.getName().endsWith("$updateRenderingEntityState")).findFirst().orElseThrow();
        Method fadeMethod=Arrays.stream(ParticleManager.class.getDeclaredMethods()).filter(method->method.getName().endsWith("$overflowparticle$fade")).findFirst().orElseThrow();
        Method typedMethod=Arrays.stream(ParticleManager.class.getDeclaredMethods()).filter(method->method.getName().equals("arcadium$typedParticleState")).findFirst().orElseThrow();
        originalMethod.setAccessible(true);fadeMethod.setAccessible(true);typedMethod.setAccessible(true);
        original=MethodHandles.lookup().unreflect(originalMethod).asType(signature.appendParameterTypes(Operation.class));fade=MethodHandles.lookup().unreflect(fadeMethod).asType(signature.appendParameterTypes(Operation.class));typed=MethodHandles.lookup().unreflect(typedMethod).asType(signature);
        Class<?> state=Class.forName("org.polyfrost.overflowparticles.client.OverflowParticlesClient");
        rendering=MethodHandles.lookup().unreflect(state.getMethod("isRendering"));renderedEntity=MethodHandles.lookup().unreflect(state.getMethod("getRenderingEntity"));
        setRendering=MethodHandles.lookup().unreflect(state.getMethod("setRendering",boolean.class));setEntity=MethodHandles.lookup().unreflect(state.getMethod("setRenderingEntity",Particle.class));
        nativeRender=args->{((Particle)args[0]).render((BufferBuilder)args[1],(Entity)args[2],(float)args[3],(float)args[4],(float)args[5],(float)args[6],(float)args[7],(float)args[8]);return null;};
        nativeFade=args->{try{fade.invokeExact(manager,(Particle)args[0],(BufferBuilder)args[1],(Entity)args[2],(float)args[3],(float)args[4],(float)args[5],(float)args[6],(float)args[7],(float)args[8],nativeRender);return null;}catch(Throwable error){throw unchecked(error);}};
        nativeConfig=Particle.class.getDeclaredField("overflowparticles$config");resolved=Particle.class.getDeclaredField("overflowparticles$configResolved");nativeConfig.setAccessible(true);resolved.setAccessible(true);
        Class<?> config=Class.forName("org.polyfrost.overflowparticles.client.config.ParticleConfig");Constructor<?> constructor=config.getConstructors()[0];Object[] types=constructor.getParameterTypes()[1].getEnumConstants();
        Object normal=Arrays.stream(types).filter(value->((Enum<?>)value).name().contains("SPELL")).findFirst().orElse(types[0]);
        Object blocks=Class.forName("org.polyfrost.overflowparticles.client.particles.VanillaParticles").getField("BLOCKS").get(null);Object blockId=blocks.getClass().getMethod("getId").invoke(blocks);
        normalStyle=constructor.newInstance("Arcadium Typed Operations QA",normal);blockStyle=constructor.newInstance("Arcadium Typed Blocks QA",blockId);
        enabled=config.getMethod("setEnabled",boolean.class);fadeEnabled=config.getMethod("setFade",boolean.class);fadeStart=config.getMethod("setFadeStart",float.class);customColor=config.getMethod("setCustomColor",boolean.class);colorMode=config.getMethod("setColorMode",int.class);
        styleColor=config.getMethod("getColor").invoke(normalStyle);chroma=styleColor.getClass().getMethod("setChroma",boolean.class);chromaSpeed=styleColor.getClass().getMethod("setChromaSpeed",float.class);
        styleColor.getClass().getMethod("setArgb",int.class).invoke(styleColor,0xCC40C080);
        outer=new Fixture(mc,1);inner=new Fixture(mc,2);
    }
    private static void configure(int scenario)throws Throwable {
        enabled.invoke(normalStyle,scenario!=2);enabled.invoke(blockStyle,false);fadeEnabled.invoke(normalStyle,scenario>=4&&scenario<=6);fadeStart.invoke(normalStyle,scenario==6?0f:.2f);
        customColor.invoke(normalStyle,scenario==7||scenario==8);colorMode.invoke(normalStyle,0);chroma.invoke(styleColor,scenario==8);chromaSpeed.invoke(styleColor,0f);
        nativeConfig.set(outer,scenario==0||scenario==12?null:scenario==3?blockStyle:normalStyle);resolved.setBoolean(outer,true);nativeConfig.set(inner,normalStyle);resolved.setBoolean(inner,true);
        outer.nested=scenario==9||scenario==11;outer.throwing=scenario==10;inner.throwing=scenario==11;outer.billboard=scenario==7||scenario==8;
    }
    private static Image image(boolean accelerated,int scenario)throws Throwable {
        fast=accelerated;RenderHookAllocationControl.enabled=accelerated;silent=false;trace=new ArrayList<>();calls=0;outer.reset();inner.reset();setRendering.invokeExact(scenario==12);setEntity.invokeExact((Particle)null);
        Particle.cameraX=Particle.cameraY=Particle.cameraZ=0;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);GlStateManager.viewport(0,0,WIDTH,HEIGHT);GL20.glUseProgram(0);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.loadIdentity();GlStateManager.ortho(-4,4,-2.25,2.25,-10,10);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();
        GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableCull();GlStateManager.disableDepthTest();GlStateManager.disableAlphaTest();GlStateManager.enableBlend();GlStateManager.blendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.activeTexture(GL13.GL_TEXTURE1);GlStateManager.disableTexture();GlStateManager.activeTexture(GL13.GL_TEXTURE0);GlStateManager.disableTexture();GlStateManager.colorMask(true,true,true,true);GlStateManager.clearColor(.11f,.16f,.23f,.37f);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        BufferBuilder buffer=new BufferBuilder(256);buffer.begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);String failure=null;
        try{invoke(outer,buffer,mcEntity(),.35f,-.7f,.6f,.2f,-.4f,.1f);if(scenario==5)invoke(outer,buffer,mcEntity(),.35f,-.7f,.6f,.2f,-.4f,.1f);}catch(ProbeFailure expected){failure=expected.getMessage();}
        buffer.end();int[] words=ParticleNative065Reference.words(buffer);new BufferUploader().end(buffer);
        ByteBuffer pixels=BufferUtils.createByteBuffer(WIDTH*HEIGHT*4);GL11.glReadPixels(0,0,WIDTH,HEIGHT,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);byte[] rgba=new byte[pixels.capacity()];pixels.get(rgba);
        List<Integer> state=List.of(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING),GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK),GL11.glGetInteger(GL11.GL_BLEND_SRC),GL11.glGetInteger(GL11.GL_BLEND_DST));
        Particle owner=(Particle)renderedEntity.invokeExact();return new Image(words,rgba,List.copyOf(trace),state,Float.floatToRawIntBits(outer.getAlpha()),Float.floatToRawIntBits(inner.getAlpha()),calls,(boolean)rendering.invokeExact(),owner instanceof Fixture fixture?fixture.id:0,failure);
    }
    private static Entity mcEntity(){return Minecraft.getInstance().player;}
    private static void invoke(Particle particle,BufferBuilder buffer,Entity camera,float delta,float x,float y,float z,float forward,float sideways)throws Throwable {
        if(fast)typed.invokeExact(manager,particle,buffer,camera,delta,x,y,z,forward,sideways);
        else original.invokeExact(manager,particle,buffer,camera,delta,x,y,z,forward,sideways,nativeFade);
    }
    private static void allocation()throws Throwable {
        configure(0);silent=true;trace=null;BufferBuilder buffer=new BufferBuilder(128);buffer.begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().threadId();Map<String,Long> sizes=new LinkedHashMap<>();
        for(boolean accelerated:new boolean[]{false,true}){
            fast=accelerated;RenderHookAllocationControl.enabled=accelerated;
            for(int i=0;i<30000;i++)invoke(outer,buffer,mcEntity(),.35f,1,0,0,1,0);
            long best=Long.MAX_VALUE;for(int pass=0;pass<3;pass++){long before=bean.getThreadAllocatedBytes(thread);for(int i=0;i<40000;i++)invoke(outer,buffer,mcEntity(),.35f,1,0,0,1,0);best=Math.min(best,bean.getThreadAllocatedBytes(thread)-before);}
            sizes.put(accelerated?"typedBytes":"originalBytes",best);
        }
        buffer.end();require(sizes.get("typedBytes")==0,"Typed operations still allocate per draw: "+sizes);
        report.put("operationAllocationFor40000Calls",sizes);report.put("minimalReferenceAlsoScalarEliminated",sizes.get("originalBytes")==0);silent=false;
    }
    private static void target(){
        framebuffer=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);color=GL11.glGenTextures();GlStateManager.bindTexture(color);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,WIDTH,HEIGHT,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
        depth=GL30.glGenRenderbuffers();GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,depth);GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,GL30.GL_DEPTH_COMPONENT24,WIDTH,HEIGHT);GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_RENDERBUFFER,depth);require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"Particle operation framebuffer is incomplete");
    }
    private static void write(String name,byte[] rgba)throws Exception {
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int at=((HEIGHT-y-1)*WIDTH+x)*4;image.setRGB(x,y,(rgba[at+3]&255)<<24|(rgba[at]&255)<<16|(rgba[at+1]&255)<<8|rgba[at+2]&255);}ImageIO.write(image,"png",Path.of(System.getProperty("arcadium.particleOperations066.output")).resolveSibling(name).toFile());
    }
    private static int visible(byte[] rgba){int count=0;for(int i=4;i<rgba.length;i+=4)if(rgba[i]!=rgba[0]||rgba[i+1]!=rgba[1]||rgba[i+2]!=rgba[2])count++;return count;}
    private static void finish(Throwable error){if(done)return;done=true;report.put("passed",error==null);if(error!=null){report.put("error",error.toString());error.printStackTrace();}try{Path path=Path.of(System.getProperty("arcadium.particleOperations066.output"));Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){failure.printStackTrace();}Minecraft.getInstance().stop();}
    private static RuntimeException unchecked(Throwable error){if(error instanceof RuntimeException runtime)return runtime;if(error instanceof Error fatal)throw fatal;return new IllegalStateException(error);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static final class ProbeFailure extends RuntimeException {ProbeFailure(int id){super("native custom particle "+id);}}
    private static final class Fixture extends Particle {
        final int id;boolean nested,throwing,billboard;
        Fixture(Minecraft mc,int id){super(mc.world,0,0,0,0,0,0);this.id=id;}
        void reset(){alpha=.8f;red=.3f;green=.6f;blue=.9f;size=billboard?8f:.75f;age=7;lifetime=10;lastX=lastY=lastZ=x=y=z=0;}
        @Override public void render(BufferBuilder buffer,Entity camera,float delta,float dx,float dy,float dz,float forward,float sideways){
            calls++;if(silent)return;
            try{
                trace.add(id+":"+(boolean)rendering.invokeExact()+":"+Float.floatToRawIntBits(alpha)+":"+Float.floatToRawIntBits(delta)+":"+Float.floatToRawIntBits(dx)+":"+Float.floatToRawIntBits(dy)+":"+Float.floatToRawIntBits(dz)+":"+Float.floatToRawIntBits(forward)+":"+Float.floatToRawIntBits(sideways));
                if(nested)invoke(inner,buffer,camera,delta,dx,dy,dz,forward,sideways);if(throwing)throw new ProbeFailure(id);
                if(billboard){super.render(buffer,camera,delta,dx,dy,dz,forward,sideways);return;}
                for(int i=0;i<4;i++)buffer.vertex(id-2+(i==1||i==2?1:0),i>=2?.75:-.75,0).texture(0d,0d).color(red,green,blue,alpha).texture(240,240).nextVertex();
            }catch(Throwable error){throw unchecked(error);}
        }
    }
}
