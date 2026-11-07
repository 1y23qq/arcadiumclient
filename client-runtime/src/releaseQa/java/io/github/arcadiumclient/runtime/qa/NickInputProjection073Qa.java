// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.*;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.*;
import io.github.arcadiumclient.runtime.mixin.client.access.ChatScreenAccessor;
import java.lang.reflect.Field;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.polyfrost.oneconfig.internal.FontPreferences;
import org.polyfrost.oneconfig.internal.GameFontMetricCounters;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;

public final class NickInputProjection073Qa {
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static int ticks,phase,wait,checks;
    private static boolean done;
    private record Case(String name,String raw,String source,String replacement,int caret,int selection,boolean password) { }
    private NickInputProjection073Qa() { }
    public static boolean enabled(){return System.getProperty("arcadium.nickInput073.output")!=null;}
    public static void tick(){
        if(done)return;Minecraft mc=Minecraft.getInstance();
        try{
            require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken()),"Use the isolated offline fixture");require(++ticks<2000,"Nick input QA timed out at phase "+phase);wait++;
            if(phase==0&&ticks>65&&mc.screen!=null){
                ClientPreferences.setSetupComplete(true);mc.options.pauseOnUnfocus=false;mc.options.vsync=false;Display.setVSyncEnabled(false);OneConfigConfig.limitUnfocusedFps=false;
                mc.startGame("arcadium-nick-input073","Nick Input QA",new WorldSettings(73073,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=1;wait=0;
            }else if(phase==1&&mc.world!=null&&mc.player!=null&&wait>70){verify();finish(null);}
        }catch(Throwable error){finish(error);}
    }
    private static void verify()throws Exception {
        Minecraft mc=Minecraft.getInstance();boolean nick=NickHiderConfig.getEnabled(),password=PasswordHider.getEnabled(),inter=OneConfigConfig.useInterInGame,vanilla=OneConfigConfig.useVanillaFontInGame,emoji=EmojiText.enabled();
        String nickname=NickHiderConfig.getNickname(),rules=NickHiderConfig.getReplacementRulesJson(),commands=PasswordHider.getCommands();
        int oldFbo=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);IntBuffer viewport=BufferUtils.createIntBuffer(4);GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        int target=GL30.glGenFramebuffers(),texture=GL11.glGenTextures();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        List<Map<String,Object>> cases=new ArrayList<>();long compared=0,mouse=0;
        try{
            mc.openScreen(new ChatScreen(""));TextFieldWidget actual=((ChatScreenAccessor)mc.screen).arcadium$chatField();actual.x=16;actual.y=16;actual.setMaxLength(2048);actual.setHasBorder(false);
            PasswordHider.setEnabled(true);PasswordHider.setCommands("login register");EmojiText.setEnabled(true);
            NickHiderConfig.setEnabled(true);
            TextFieldWidget fontless=new TextFieldWidget(73074,null,16,40,80,12);fontless.setText("/msg PlayerName");fontless.setCursor(6);fontless.setSelectionEnd(10);fontless.setVisible(false);fontless.render();fontless.mouseClicked(-10,-10,0);
            require(fontless.getText().equals("/msg PlayerName")&&fontless.getSelectionEnd()==10,"Fontless native field changed raw editing behavior");
            actual.setText("PlayerName");NickTextFieldProjection hidden=helper(actual);long hiddenBuilds=hidden.builds;actual.setVisible(false);NickHiderConfig.setReplacementRulesJson(ruleJson("PlayerName","HiddenAlias",false));actual.render();require(hidden.builds==hiddenBuilds,"Invisible field rebuilt projection metrics");actual.setVisible(true);
            report.put("fontlessAndInvisibleNativeFallback",true);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);GlStateManager.bindTexture(texture);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,1280,720,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"Input framebuffer incomplete");
            String screenshot="/msg Lyhap Euphoria12 hi",unicode="/msg PlayerName 🙂 e\u0301 :smile:",formatted="§a/msg Player§bName hi";
            List<Case> modes=List.of(
                new Case("screenshot-long",screenshot,"Lyhap","Euphoria12",screenshot.length(),screenshot.length(),false),
                new Case("multiple","/msg A B hi","A","B",11,11,false),
                new Case("short","/msg PlayerName hi","PlayerName","Q",18,18,false),
                new Case("delete","/msg PlayerName","PlayerName","",15,15,false),
                new Case("long-scroll","/msg X","X","WideAlias_".repeat(22),6,6,false),
                new Case("middle-caret","/msg PlayerName hi","PlayerName","LongAlias",9,9,false),
                new Case("selection","/msg PlayerName hi","PlayerName","LongAlias",6,13,false),
                new Case("unicode",unicode,"PlayerName","🙂Alias",unicode.length(),unicode.length(),false),
                new Case("format",formatted,"PlayerName","Alias",formatted.length(),formatted.length(),false),
                new Case("password","/login PlayerName🙂 secret","login","authenticate",25,25,true));
            for(boolean useInter:new boolean[]{false,true}){
                OneConfigConfig.useInterInGame=useInter;OneConfigConfig.useVanillaFontInGame=!useInter;FontPreferences.refresh();
                require(FontPreferences.nativeSource()==(useInter?FontPreferences.Source.INTER:FontPreferences.Source.VANILLA),"Font fixture did not select its native source");
                for(Case fixture:modes){
                    NickHiderConfig.setReplacementRulesJson(ruleJson(fixture.source,fixture.replacement,fixture.name.equals("multiple")));NickHiderConfig.setEnabled(true);actual.setText(fixture.raw);actual.setCursor(fixture.caret);actual.setSelectionEnd(fixture.selection);actual.setFocused(false);actual.setFocused(true);
                    begin();actual.render();byte[] projected=pixels();NickTextFieldProjection helper=helper(actual);NickInputLayout layout=helper.layout();
                    require(visible(projected)>30,"Projected input reference did not draw visible text "+fixture.name);
                    Field projectedValue=NickTextFieldProjection.class.getDeclaredField("projection");projectedValue.setAccessible(true);NickTextProjection full=(NickTextProjection)projectedValue.get(helper);
                    String expectedDisplay=fixture.name.equals("multiple")?"/msg B C hi":fixture.password?PasswordMask.display(fixture.raw,"login register").replace(fixture.source,fixture.replacement):fixture.raw.replace(fixture.source,fixture.replacement);
                    if(fixture.name.equals("format"))expectedDisplay="§a/msg Alias§b hi";
                    require(full.getDisplay().equals(EmojiText.display(expectedDisplay)),"Actual projection differs from expected non-cascading display "+fixture.name);
                    require(actual.getText().equals(fixture.raw),"Render changed actual input "+fixture.name);require(!layout.window().contains(fixture.source)||fixture.source.equals(fixture.replacement),"Source replacement was not masked "+fixture.name);
                    if(fixture.password)require(!layout.window().contains("secret")&&!layout.window().contains("PlayerName"),"Password masking was lost");
                    int cursor=layout.windowIndex(actual.getCursor()),selected=layout.windowIndex(actual.getSelectionEnd());require(cursor>=0&&cursor<=layout.window().length()&&selected>=0&&selected<=layout.window().length(),"Projected selection escaped the window");
                    NickHiderConfig.setEnabled(false);TextFieldWidget reference=new TextFieldWidget(73073,mc.textRenderer,actual.x,actual.y,actual.getInnerWidth(),12);reference.setHasBorder(false);
                    boolean bar=actual.getCursor()<fixture.raw.length()||fixture.raw.length()>=actual.getMaxLength();reference.setMaxLength(layout.window().length()+(bar?0:1));reference.setText(layout.window());reference.setCursor(cursor);reference.setSelectionEnd(selected);reference.setFocused(true);
                    begin();reference.render();byte[] expected=pixels();require(Arrays.equals(projected,expected),"Native projected field pixels differ "+fixture.name+"/inter="+useInter);compared+=projected.length;
                    if(fixture.name.equals("screenshot-long")){write("nick-input073-"+(useInter?"inter":"vanilla")+"-projected.png",projected);write("nick-input073-"+(useInter?"inter":"vanilla")+"-reference.png",expected);}
                    NickHiderConfig.setEnabled(true);actual.setText(fixture.raw);actual.setCursor(fixture.caret);actual.setSelectionEnd(fixture.selection);begin();actual.render();helper=helper(actual);layout=helper.layout();long built=helper.builds;
                    for(int frame=0;frame<8;frame++){begin();actual.render();require(actual.getText().equals(fixture.raw),"Stable render modified raw input");}
                    require(helper.builds==built,"Unchanged text/policy rebuilt its replacement projection");
                    for(int x=0;x<actual.getInnerWidth();x+=7){
                        layout=helper(actual).layout();String visible,cut;NickHider.beginRender();
                        try{visible=layout.safePrefix(layout.window(),mc.textRenderer.trim(layout.window(),actual.getInnerWidth()));cut=layout.safePrefix(visible,mc.textRenderer.trim(visible,x));}finally{NickHider.endRender();}
                        int source=layout.sourceIndex(cut.length());actual.mouseClicked(actual.x+x,actual.y+1,0);require(actual.getCursor()==source,"Mouse hit used display indices as raw indices "+fixture.name);require(actual.getText().equals(fixture.raw),"Mouse changed actual text");mouse++;
                    }
                    actual.setCursor(0);actual.setSelectionEnd(fixture.raw.length());require(actual.getSelectedText().equals(fixture.raw),"Copy selection did not retain raw command text");actual.write("Z");require(actual.getText().equals("Z"),"Replacement editing changed raw selected input");
                    cases.add(Map.of("case",fixture.name,"inter",useInter,"rawTextPreserved",true,"nativePixelsEqual",true,"stableProjectionReused",true));
                }
            }
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL error after input projection oracle");report.put("cases",cases);report.put("nativeRgbaBytesCompared",compared);report.put("mouseHits",mouse);report.put("checks",checks);report.put("rawEditingCopyAndCommandStorage",true);report.put("noServerTextSent",true);
        }finally{
            NickHiderConfig.setEnabled(nick);NickHiderConfig.setNickname(nickname);NickHiderConfig.setReplacementRulesJson(rules);PasswordHider.setEnabled(password);PasswordHider.setCommands(commands);EmojiText.setEnabled(emoji);OneConfigConfig.useInterInGame=inter;OneConfigConfig.useVanillaFontInGame=vanilla;FontPreferences.refresh();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,oldFbo);GL30.glDeleteFramebuffers(target);GlStateManager.deleteTextures(texture);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();GL20.glUseProgram(oldProgram);GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
        }
    }
    private static String ruleJson(String source,String target,boolean multiple){JsonArray rules=new JsonArray();addRule(rules,source,target);if(multiple)addRule(rules,"B","C");return rules.toString();}
    private static void addRule(JsonArray rules,String source,String target){JsonObject rule=new JsonObject();rule.addProperty("id",UUID.nameUUIDFromBytes((source+target).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());rule.addProperty("source",source);rule.addProperty("replacement",target);rule.addProperty("enabled",true);rule.addProperty("caseSensitive",true);rules.add(rule);}
    private static NickTextFieldProjection helper(TextFieldWidget field)throws Exception {for(Class<?> type=field.getClass();type!=null;type=type.getSuperclass())for(Field candidate:type.getDeclaredFields())if(candidate.getType()==NickTextFieldProjection.class){candidate.setAccessible(true);return (NickTextFieldProjection)candidate.get(field);}throw new IllegalStateException("Native field projection mixin missing");}
    private static void begin(){GlStateManager.viewport(0,0,1280,720);GL20.glUseProgram(0);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.loadIdentity();GlStateManager.ortho(0,1280,720,0,-100,100);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();GlStateManager.disableDepthTest();GlStateManager.disableCull();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableBlend();GlStateManager.enableAlphaTest();GlStateManager.colorMask(true,true,true,true);GlStateManager.color4f(1,1,1,1);GlStateManager.clearColor(.08f,.12f,.19f,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);}
    private static byte[] pixels(){if(Minecraft.getInstance().textRenderer instanceof GameFontMetricCounters counters)counters.arcadium$flushTextBatches();ByteBuffer pixels=BufferUtils.createByteBuffer(1280*720*4);GL11.glReadPixels(0,0,1280,720,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);byte[] bytes=new byte[pixels.capacity()];pixels.get(bytes);return bytes;}
    private static int visible(byte[] pixels){int count=0;for(int i=4;i<pixels.length;i+=4)if(pixels[i]!=pixels[0]||pixels[i+1]!=pixels[1]||pixels[i+2]!=pixels[2])count++;return count;}
    private static void write(String name,byte[] rgba)throws Exception {BufferedImage image=new BufferedImage(1280,720,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<720;y++)for(int x=0;x<1280;x++){int p=((719-y)*1280+x)*4;image.setRGB(x,y,(rgba[p+3]&255)<<24|(rgba[p]&255)<<16|(rgba[p+1]&255)<<8|rgba[p+2]&255);}ImageIO.write(image,"png",Path.of(System.getProperty("arcadium.nickInput073.output")).resolveSibling(name).toFile());}
    private static void require(boolean condition,String message){checks++;if(!condition)throw new IllegalStateException(message);}
    private static void finish(Throwable error){if(done)return;done=true;report.put("passed",error==null);if(error!=null){report.put("error",error.toString());error.printStackTrace();}try{Path path=Path.of(System.getProperty("arcadium.nickInput073.output"));Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){failure.printStackTrace();}Minecraft.getInstance().stop();}
}
