// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.resource.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.polyfrost.oneconfig.internal.*;

public final class FontBatch070Qa {
    private static final int W=512,H=128;
    private record Case(String text,float scale,int color,boolean shadow,boolean unicode,int source,boolean elements) { }
    private record Image(byte[] rgba,int end,int width) { }
    private static final List<Case> cases=new ArrayList<>();
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static RenderTarget target;
    private static Object batcher;
    private static Random random;
    private static Method beginBatch,endBatch;
    private static int ticks,index,maxDelta;
    private static long comparedPixels,metricChecks,batchedBefore;
    private static boolean ready,done;
    private FontBatch070Qa() { }

    public static void tick() {
        if(done||System.getProperty("arcadium.font070.output")==null)return;
        try {
            Minecraft mc=Minecraft.getInstance();
            require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken()),"Use the isolated offline fixture");
            require(++ticks<1800,"Font QA timeout");
            if(!ready&&ticks>65&&mc.screen!=null)prepare(mc);
        } catch(Throwable error){finish(error);}
    }

    private static void prepare(Minecraft mc)throws Exception {
        io.github.arcadiumclient.runtime.client.ClientPreferences.setSetupComplete(true);
        OneConfigConfig.limitUnfocusedFps=false;mc.options.fpsLimit=180;
        io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig.setRetainedHudBatches(true);
        batcher=mc.textRenderer.getClass().getMethod("argentum$getBatcher").invoke(mc.textRenderer);
        require(batcher instanceof org.polyfrost.oneconfig.internal.ui.compose.VanillaGlyphSink,"Native font batch adapter missing");
        beginBatch=mc.textRenderer.getClass().getMethod("argentum$beginBatch",Runnable.class);endBatch=mc.textRenderer.getClass().getMethod("argentum$endBatch");
        for(Class<?> type=mc.textRenderer.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields())
            if(field.getType()==Random.class&&!Modifier.isStatic(field.getModifiers())){field.setAccessible(true);random=(Random)field.get(mc.textRenderer);}
        require(random!=null,"Font random generator missing");
        target=new RenderTarget(W,H,true);
        batchedBefore=org.polyfrost.oneconfig.internal.ui.compose.LegacyGameFont.batchedGlyphs;
        String[] text={"Hello Arcadium 123","§aBedwars §b[VIP] §fArcadiumTest","§lBold §rPlain","§lA§cB§rC","§oItalic §nunder §mstrike","§rReset §xUnknown","§kRandom123","日本語 Кириллица Ωλ","A\u00a0B\u202fC","A 😂 B", "§l❤ §rplain"};
        for(int source=0;source<4;source++)for(String value:text)for(float scale:new float[]{.75f,1f,1.5f})
            for(boolean shadow:new boolean[]{false,true})cases.add(new Case(value,scale,source==2?0x9FC37F41:0xFFFFFFFF,shadow,false,source,index++%2==0));
        for(String value:text)cases.add(new Case(value,1.25f,0x7FFFFFFF,true,true,0,true));
        index=0;ready=true;
    }

    public static void frame() {
        if(!ready||done)return;
        try {
            if(index<cases.size()) {
                Case value=cases.get(index++);
                Image reference=draw(value,false,false),actual=draw(value,true,true),cached=draw(value,true,true);
                compare(reference,actual,value);compare(reference,cached,value);
            } else {
                verifySourceIsolation();
                long count=org.polyfrost.oneconfig.internal.ui.compose.LegacyGameFont.batchedGlyphs-batchedBefore;
                require(count>100,"No forced vanilla glyphs used the batch adapter");
                GameFontMetricCounters metrics=(GameFontMetricCounters)Minecraft.getInstance().textRenderer;
                require(metrics.arcadium$metricHits()>0,"The exact width cache never hit");
                report.put("cases",cases.size());report.put("comparedPixels",comparedPixels);report.put("maxRgbaChannelDelta",maxDelta);
                report.put("metricAndReturnChecks",metricChecks);report.put("batchedGlyphs",count);report.put("metricHits",metrics.arcadium$metricHits());
                require(io.github.arcadiumclient.runtime.client.optimizations.RetainedHudBuffers.hits>0,"Retained HUD never reused the native font batches");
                report.put("retainedHudHits",io.github.arcadiumclient.runtime.client.optimizations.RetainedHudBuffers.hits);
                finish(null);
            }
        } catch(Throwable failure){finish(failure);}
    }

    private static Image draw(Case value,boolean batching,boolean cached)throws Exception {
        Minecraft mc=Minecraft.getInstance();
        OneConfigConfig.useInterInGame=value.source==2;OneConfigConfig.useInterForModText=false;
        OneConfigConfig.useVanillaFontInGame=value.source==0;OneConfigConfig.useVanillaFontForModText=value.source==1;
        boolean fontScope=value.source==1?FontPreferences.beginModText():false;
        TextOptimizationOptions.vanillaFontBatching=batching;TextOptimizationOptions.fontMetricCache=cached;
        ((io.github.arcadiumclient.runtime.qa.mixin.FontBatch070Access)mc.textRenderer).font070$unicode(value.unicode);
        int fbo=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        IntBuffer viewport=BufferUtils.createIntBuffer(4);GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);
        int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();GL11.glOrtho(0,W,H,0,-1000,1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();GL11.glScalef(value.scale,value.scale,1);
        try {
            target.bindWrite(true);GL20.glUseProgram(0);GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            net.minecraft.client.render.platform.GlStateManager.disableLighting();net.minecraft.client.render.platform.GlStateManager.disableDepthTest();
            net.minecraft.client.render.platform.GlStateManager.disableCull();net.minecraft.client.render.platform.GlStateManager.disableTexture();net.minecraft.client.render.platform.GlStateManager.enableTexture();
            net.minecraft.client.render.platform.GlStateManager.disableBlend();net.minecraft.client.render.platform.GlStateManager.enableBlend();
            net.minecraft.client.render.platform.GlStateManager.blendFuncSeparate(770,771,1,0);
            net.minecraft.client.render.platform.GlStateManager.color4f(0,0,0,0);net.minecraft.client.render.platform.GlStateManager.color4f(1,1,1,1);
            random.setSeed(70031);
            int width=mc.textRenderer.getWidth(value.text);
            if(value.elements)beginBatch.invoke(mc.textRenderer,(Runnable)()->{});
            int end;
            try {end=mc.textRenderer.draw(value.text,7.25f,8.75f,value.color,value.shadow);}
            finally {if(value.elements)endBatch.invoke(mc.textRenderer);}
            byte[] pixels=new byte[W*H*4];ByteBuffer bytes=BufferUtils.createByteBuffer(pixels.length);
            GL11.glReadPixels(0,0,W,H,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);bytes.get(pixels);
            return new Image(pixels,end,width);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(mode);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL11.glViewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));GL20.glUseProgram(program);
            if(value.source==1)FontPreferences.endModText(fontScope);
        }
    }
    private static void compare(Image a,Image b,Case value)throws Exception {
        require(a.end==b.end&&a.width==b.width,"Font advance changed: "+value+" / "+a.end+","+a.width+" != "+b.end+","+b.width);
        metricChecks++;
        int mismatch=0,delta=0;
        for(int i=0;i<a.rgba.length;i++){int difference=Math.abs((a.rgba[i]&255)-(b.rgba[i]&255));if(difference>0)mismatch++;delta=Math.max(delta,difference);}
        comparedPixels+=W*H;maxDelta=Math.max(maxDelta,delta);
        if(mismatch>0) {
            Path output=Path.of(System.getProperty("arcadium.font070.output")).toAbsolutePath().getParent();
            png(output.resolve("reference.png"),a.rgba);png(output.resolve("actual.png"),b.rgba);
            throw new IllegalStateException("Font pixels changed: "+value+" / channels="+mismatch+", max="+delta);
        }
    }
    private static void verifySourceIsolation()throws Exception {
        Minecraft mc=Minecraft.getInstance();
        Identifier location=(Identifier)mc.textRenderer.getClass().getMethod("argentum$getFontLocation").invoke(mc.textRenderer);
        mc.getTextureManager().bind(location);Texture old=mc.getTextureManager().get(location);
        BufferedImage image;
        try(var input=mc.getResourcePacks().defaultPack.getResource(location)){image=ImageIO.read(input);}
        int cell=image.getWidth()/16;
        for(int y=0;y<cell;y++)for(int x=0;x<cell-1;x++)image.setRGB(('A'%16)*cell+x,('A'/16)*cell+y,0xFFFF0000);
        DynamicTexture altered=new DynamicTexture(image);
        try {
            mc.getTextureManager().register(location,altered);
            batcher.getClass().getMethod("clearCaches").invoke(batcher);
            Case resource=new Case("AAAA ABC",1.25f,-1,true,false,3,true);
            Case vanilla=new Case("AAAA ABC",1.25f,-1,true,false,0,true);
            Image expectedResource=draw(resource,true,false),expectedVanilla=draw(vanilla,false,false);
            require(!Arrays.equals(expectedResource.rgba,expectedVanilla.rgba),"Resource font test did not differ from bundled font");
            for(int i=0;i<8;i++){compare(expectedVanilla,draw(vanilla,true,true),vanilla);compare(expectedResource,draw(resource,true,true),resource);}
            report.put("resourceAndVanillaCacheIsolation",true);
        } finally {mc.getTextureManager().register(location,old);batcher.getClass().getMethod("clearCaches").invoke(batcher);}
    }
    private static void png(Path file,byte[] bytes)throws Exception {
        BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-y-1)*W+x)*4;image.setRGB(x,y,(bytes[i+3]&255)<<24|(bytes[i]&255)<<16|(bytes[i+1]&255)<<8|(bytes[i+2]&255));}
        ImageIO.write(image,"png",file.toFile());
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void finish(Throwable failure) {
        done=true;report.put("passed",failure==null);report.put("caseIndex",index);report.put("syntheticOffline",true);
        if(failure!=null){report.put("failure",failure.toString());failure.printStackTrace();}
        try {Path file=Path.of(System.getProperty("arcadium.font070.output"));Files.createDirectories(file.toAbsolutePath().getParent());Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(Exception error){error.printStackTrace();}
        Minecraft.getInstance().stop();
    }
}
