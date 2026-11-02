// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.sun.net.httpserver.HttpServer;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.optimizations.RenderHookAllocationControl;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.resource.Identifier;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class CeraCosmetics066Qa {
    private static final String A="ArcadiumCeraOne",B="ArcadiumCeraTwo";
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static final AtomicInteger requests=new AtomicInteger();
    private static final List<Object> ownedParts=new ArrayList<>();
    private static Object resolver,ceraConfig,capeA,capeB,cosmeticA,cosmeticB;
    private static Map<String,Object> capes;
    private static Map<String,List<Object>> cosmetics;
    private static Set<String> requested;
    private static Method cape,models;
    private static Field configEnabled,capeLoaded,capeRegistered,capeUrl;
    private static Constructor<?> capeConstructor;
    private static ChangingPlayer player;
    private static GameProfile profileA,profileB;
    private static HttpServer server;
    private static ExecutorService httpExecutor;
    private static boolean oldConfig,oldControl,done;
    private static int ticks,phase,wait,checks;
    private static String oldNonProxy;
    private static boolean proxyChanged;
    private CeraCosmetics066Qa() { }
    public static boolean enabled(){return System.getProperty("arcadium.ceraCosmetics066.output")!=null;}
    public static void tick(){
        if(done)return;Minecraft mc=Minecraft.getInstance();
        try{
            require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken()),"Use the isolated synthetic account");
            require(++ticks<1800,"Cera cosmetic oracle timed out at phase "+phase+", loopback requests="+requests.get());wait++;
            if(phase==0&&ticks>65&&mc.screen!=null){
                ClientPreferences.setSetupComplete(true);mc.options.pauseOnUnfocus=false;mc.options.vsync=false;Display.setVSyncEnabled(false);org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;
                mc.startGame("arcadium-cera-066","Cera Cosmetic QA",new WorldSettings(66068,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=1;wait=0;
            }else if(phase==1&&mc.world!=null&&mc.player!=null&&wait>60){
                mc.openScreen(null);initialize(mc);metadata();startLoopback();capeA=newCape(A,"first");capeB=newCape(B,"third");capes.put(A,capeA);capes.put(B,capeB);
                configEnabled.setBoolean(ceraConfig,false);require(resolveCape(false).isEmpty()&&resolveCape(true).isEmpty(),"Disabled Cera cape changed");require(!capeRegistered.getBoolean(capeA)&&requests.get()==0,"Disabled Cera registered a cape");
                configEnabled.setBoolean(ceraConfig,true);resolveCape(false);require(capeRegistered.getBoolean(capeA),"Native cape register was skipped");phase=2;wait=0;
            }else if(phase==2&&capeLoaded.getBoolean(capeA)){
                sameCape();require(requests.get()==1,"First native cape download count changed");report.put("nativeCapeRegistrationAndAsyncCompletion",true);
                capeA=newCape(A,"second");capes.put(A,capeA);require(resolveCape(true).isEmpty(),"Replacing an unloaded cape reused the old result");require(capeRegistered.getBoolean(capeA),"Replacement cape did not register");phase=3;wait=0;
            }else if(phase==3&&capeLoaded.getBoolean(capeA)){
                sameCape();player.current=profileB;require(resolveCape(true).isEmpty(),"New profile name inherited another cape");require(capeRegistered.getBoolean(capeB),"New profile cape did not register");phase=4;wait=0;
            }else if(phase==4&&capeLoaded.getBoolean(capeB)){
                sameCape();Identifier second=resolveCape(true).orElseThrow();player.current=profileA;sameCape();require(!resolveCape(true).orElseThrow().equals(second),"Live profile switch kept the old cape identifier");
                configEnabled.setBoolean(ceraConfig,false);require(resolveCape(false).isEmpty()&&resolveCape(true).isEmpty(),"Loaded cape ignored the config switch");configEnabled.setBoolean(ceraConfig,true);sameCape();
                require(requests.get()==3,"Repeated lookups started extra native downloads");report.put("sameNameCapeReplacementAndLiveProfileSwitch",true);report.put("loopbackOnlyNativeDownloads",requests.get());
                nativePixels();mc.reloadResources();sameCape();sameList();report.put("loopbackDownloadsAfterReload",requests.get());report.put("resourceReloadKeepsLiveResolution",true);report.put("checks",checks);report.put("actualUsernameSites",RenderHookAllocationControl.cosmeticSites);finish(null);
            }
        }catch(Throwable error){finish(error);}
    }
    @SuppressWarnings("unchecked") private static void initialize(Minecraft mc)throws Exception {
        resolver=mc.getClass().getMethod("cera$getOptifineCosmetics").invoke(mc);require(resolver!=null&&resolver.getClass().getName().equals("dev.rdh.cera.modules.OptifineCosmetics"),"Actual Cera resolver is unavailable");
        require(RenderHookAllocationControl.cosmeticSites==2,"Actual Cera did not receive exactly two username sites");
        Class<?> type=resolver.getClass();cape=type.getMethod("cape",ClientPlayerEntity.class);models=type.getDeclaredMethod("cosmetics",ClientPlayerEntity.class);models.setAccessible(true);
        capes=(Map<String,Object>)field(resolver,"capes");cosmetics=(Map<String,List<Object>>)field(resolver,"cosmetics");requested=(Set<String>)field(resolver,"requested");
        ceraConfig=Class.forName("dev.rdh.cera.Cera").getField("CONFIG").get(null);configEnabled=ceraConfig.getClass().getField("optifineCosmetics");oldConfig=configEnabled.getBoolean(ceraConfig);oldControl=RenderHookAllocationControl.enabled;
        profileA=new GameProfile(new UUID(0x66066000,0x100001),A);profileB=new GameProfile(profileA.getId(),B);player=new ChangingPlayer(mc.world,profileA);
        requested.add(A);requested.add(B);cosmeticA=cosmetic(-4);cosmeticB=cosmetic(0);cosmetics.put(A,new ArrayList<>());cosmetics.put(B,new ArrayList<>(List.of(cosmeticB)));
        Class<?> capeType=Class.forName(type.getName()+"$Cape");capeConstructor=capeType.getDeclaredConstructor(String.class);capeConstructor.setAccessible(true);capeLoaded=accessible(capeType,"loaded");capeRegistered=accessible(capeType,"registered");capeUrl=accessible(capeType,"url");
    }
    private static void metadata()throws Exception {
        configEnabled.setBoolean(ceraConfig,true);sameList();
        List<Object> published=new ArrayList<>(List.of(cosmeticA));CompletableFuture.runAsync(()->cosmetics.put(A,published)).get(5,TimeUnit.SECONDS);require(resolveModels(false)==published&&resolveModels(true)==published,"Asynchronous cosmetic publication was cached or copied");
        published.add(cosmeticB);require(resolveModels(true)==published&&resolveModels(false).size()==2,"Mutable cosmetic list changes were hidden");published.remove(cosmeticB);sameList();
        List<Object> replaced=new ArrayList<>(List.of(cosmeticB));cosmetics.put(A,replaced);require(resolveModels(true)==replaced&&resolveModels(false)==replaced,"Replacement cosmetic list was stale");cosmetics.put(A,published);
        configEnabled.setBoolean(ceraConfig,false);require(resolveModels(false).isEmpty()&&resolveModels(true).isEmpty(),"Disabled cosmetic resolver returned a cached list");configEnabled.setBoolean(ceraConfig,true);sameList();
        for(String name:new String[]{"", "bad name", "bad!", "\u212A", "\uD83D\uDE00"}){
            player.current=new GameProfile(profileA.getId(),name);require(resolveModels(false).isEmpty()&&resolveModels(true).isEmpty()&&resolveCape(false).isEmpty()&&resolveCape(true).isEmpty(),"Invalid name resolved differently: "+name);require(!requested.contains(name)&&!capes.containsKey(name),"Invalid name triggered a native request");
        }
        player.current=new GameProfile(profileA.getId(),null);require(resolveModels(false).isEmpty()&&resolveModels(true).isEmpty()&&resolveCape(false).isEmpty()&&resolveCape(true).isEmpty(),"Null profile name changed its native result");player.current=profileA;
        report.put("asyncReplacementMutableListsAndConfigSwitches",true);report.put("invalidAndNullNamesPreserveNativeEarlyReturns",true);
    }
    private static void sameList()throws Exception {List<?> original=resolveModels(false),actual=resolveModels(true);require(original==actual,"Cosmetic resolver did not return the actual current list");}
    @SuppressWarnings("unchecked") private static List<?> resolveModels(boolean enabled)throws Exception {RenderHookAllocationControl.enabled=enabled;return (List<?>)models.invoke(resolver,player);}
    @SuppressWarnings("unchecked") private static Optional<Identifier> resolveCape(boolean enabled)throws Exception {RenderHookAllocationControl.enabled=enabled;return (Optional<Identifier>)cape.invoke(resolver,player);}
    private static void sameCape()throws Exception {
        Optional<Identifier> original=resolveCape(false),actual=resolveCape(true);require(original.equals(actual)&&original.isPresent(),"Native loaded cape result differed");
        RenderHookAllocationControl.enabled=false;Identifier first=player.getCapeTextureLocation();RenderHookAllocationControl.enabled=true;Identifier second=player.getCapeTextureLocation();require(Objects.equals(first,second)&&Objects.equals(second,actual.orElseThrow()),"Native player cape caller changed");
    }
    private static void startLoopback()throws Exception {
        BufferedImage image=new BufferedImage(16,8,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<8;y++)for(int x=0;x<16;x++)image.setRGB(x,y,0xFF000000|x*13<<16|y*29<<8|0x60);
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();ImageIO.write(image,"png",encoded);byte[] png=encoded.toByteArray();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);httpExecutor=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"Arcadium Cera Loopback QA");thread.setDaemon(true);return thread;});server.setExecutor(httpExecutor);
        server.createContext("/",exchange->{try{requests.incrementAndGet();exchange.getResponseHeaders().set("Content-Type","image/png");exchange.sendResponseHeaders(200,png.length);exchange.getResponseBody().write(png);}finally{exchange.close();}});server.start();
        oldNonProxy=System.getProperty("http.nonProxyHosts");System.setProperty("http.nonProxyHosts","127.0.0.1|localhost");proxyChanged=true;
    }
    private static Object newCape(String name,String path)throws Exception {Object result=capeConstructor.newInstance(name);capeUrl.set(result,"http://127.0.0.1:"+server.getAddress().getPort()+"/"+path+".png");return result;}
    @SuppressWarnings("unchecked") private static Object cosmetic(float x)throws Exception {
        Class<?> partType=Class.forName("dev.rdh.cera.modules.OptifineCosmetics$CosmeticPart");Constructor<?> partConstructor=partType.getDeclaredConstructor(int.class,int.class,Identifier.class);partConstructor.setAccessible(true);Object part=partConstructor.newInstance(64,32,null);ownedParts.add(part);
        Class<?> boxType=Class.forName("dev.rdh.cera.modules.OptifineCosmetics$UvBox");Constructor<?> box=boxType.getDeclaredConstructor(ModelPart.class,int[].class,int[][].class,float[].class,float.class,boolean.class,boolean.class);box.setAccessible(true);
        ((List<Object>)field(part,"boxes")).add(box.newInstance(field(part,"boxModel"),new int[]{0,0},null,new float[]{x,-4,-2,8,8,4},0f,false,false));
        Class<?> binding=Class.forName("dev.rdh.cera.modules.OptifineCosmetics$Part");Constructor<?> bind=binding.getDeclaredConstructor(partType,String.class);bind.setAccessible(true);Object bound=bind.newInstance(part,"body");
        Class<?> cosmetic=Class.forName("dev.rdh.cera.modules.OptifineCosmetics$Cosmetic");Constructor<?> constructor=cosmetic.getDeclaredConstructor(List.class,boolean.class,BufferedImage.class,int.class);constructor.setAccessible(true);return constructor.newInstance(new ArrayList<>(List.of(bound)),true,null,7);
    }
    private record Image(byte[] rgba,List<Integer> state) { }
    private static void nativePixels()throws Exception {
        int oldFbo=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);IntBuffer viewport=BufferUtils.createIntBuffer(4);GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
        int target=GL30.glGenFramebuffers(),texture=GL11.glGenTextures();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();
        try{
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,target);GlStateManager.bindTexture(texture);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,256,256,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"Cosmetic framebuffer incomplete");
            PlayerRenderer renderer=new PlayerRenderer(Minecraft.getInstance().getEntityRenderDispatcher());Class<?> layerType=Class.forName(resolver.getClass().getName()+"$Layer");Object layer=layerType.getConstructor(resolver.getClass(),PlayerRenderer.class).newInstance(resolver,renderer);
            Method render=Arrays.stream(layerType.getDeclaredMethods()).filter(method->method.getReturnType()==void.class&&method.getParameterCount()==8&&method.getParameterTypes()[0]==ClientPlayerEntity.class).findFirst().orElseThrow();
            List<Object> first=new ArrayList<>(List.of(cosmeticA));cosmetics.put(A,first);Image original=pixels(false,layer,render),actual=pixels(true,layer,render);require(Arrays.equals(original.rgba,actual.rgba)&&original.state.equals(actual.state),"Actual native cosmetic pixels/GL tail changed");require(visible(original.rgba)>100,"Native cosmetic model reference is not visible");
            List<Object> second=new ArrayList<>(List.of(cosmeticB));cosmetics.put(A,second);Image changedOriginal=pixels(false,layer,render),changedActual=pixels(true,layer,render);require(Arrays.equals(changedOriginal.rgba,changedActual.rgba)&&changedOriginal.state.equals(changedActual.state),"Live cosmetic replacement pixels changed");require(!Arrays.equals(original.rgba,changedOriginal.rgba),"Native cosmetic list replacement did not change the scene");
            write("cera-cosmetic-original.png",original.rgba);write("cera-cosmetic-fast.png",actual.rgba);report.put("nativeCosmeticRgbaBytesCompared",2L*256*256*4);report.put("visibleNativeCosmeticPixels",visible(original.rgba));report.put("liveModelReplacementAndNativeGlTail",true);
        }finally{
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,oldFbo);GL30.glDeleteFramebuffers(target);GlStateManager.deleteTextures(texture);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_MODELVIEW);GL11.glPopAttrib();GL20.glUseProgram(oldProgram);GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
        }
    }
    private static Image pixels(boolean fast,Object layer,Method render)throws Exception {
        RenderHookAllocationControl.enabled=fast;GlStateManager.viewport(0,0,256,256);GL20.glUseProgram(0);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.loadIdentity();GlStateManager.ortho(-1,1,-1,1,-8,8);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();
        GlStateManager.disableDepthTest();GlStateManager.disableBlend();GlStateManager.disableCull();GlStateManager.disableLighting();GlStateManager.disableFog();GlStateManager.disableAlphaTest();GlStateManager.activeTexture(GL13.GL_TEXTURE1);GlStateManager.disableTexture();GlStateManager.activeTexture(GL13.GL_TEXTURE0);GlStateManager.enableTexture();GlStateManager.colorMask(true,true,true,true);GlStateManager.color4f(1,1,1,1);GlStateManager.clearColor(.08f,.12f,.19f,1);GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
        render.invoke(layer,player,.3f,.2f,.5f,41f,0f,0f,.0625f);
        ByteBuffer data=BufferUtils.createByteBuffer(256*256*4);GL11.glReadPixels(0,0,256,256,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);byte[] rgba=new byte[data.capacity()];data.get(rgba);
        return new Image(rgba,List.of(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH),GL11.glGetError()));
    }
    private static void write(String name,byte[] rgba)throws Exception {BufferedImage image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<256;y++)for(int x=0;x<256;x++){int at=((255-y)*256+x)*4;image.setRGB(x,y,(rgba[at+3]&255)<<24|(rgba[at]&255)<<16|(rgba[at+1]&255)<<8|rgba[at+2]&255);}ImageIO.write(image,"png",Path.of(System.getProperty("arcadium.ceraCosmetics066.output")).resolveSibling(name).toFile());}
    private static int visible(byte[] rgba){int count=0;for(int i=4;i<rgba.length;i+=4)if(rgba[i]!=rgba[0]||rgba[i+1]!=rgba[1]||rgba[i+2]!=rgba[2])count++;return count;}
    private static Field accessible(Class<?> owner,String name)throws Exception {Field field=owner.getDeclaredField(name);field.setAccessible(true);return field;}
    private static Object field(Object owner,String name)throws Exception {return accessible(owner.getClass(),name).get(owner);}
    private static void require(boolean condition,String message){checks++;if(!condition)throw new IllegalStateException(message);}
    private static void finish(Throwable error){
        if(done)return;done=true;report.put("passed",error==null);if(error!=null){report.put("error",error.toString());error.printStackTrace();}
        try{if(configEnabled!=null)configEnabled.setBoolean(ceraConfig,oldConfig);RenderHookAllocationControl.enabled=oldControl;if(capes!=null){capes.remove(A);capes.remove(B);}if(cosmetics!=null){cosmetics.remove(A);cosmetics.remove(B);}if(requested!=null){requested.remove(A);requested.remove(B);}for(Object part:ownedParts){int list=(int)field(part,"displayList");if(list!=0)GL11.glDeleteLists(list,1);}if(server!=null)server.stop(0);if(httpExecutor!=null)httpExecutor.shutdownNow();if(proxyChanged){if(oldNonProxy!=null)System.setProperty("http.nonProxyHosts",oldNonProxy);else System.clearProperty("http.nonProxyHosts");}}
        catch(Exception cleanup){report.put("cleanupError",cleanup.toString());report.put("passed",false);}
        try{Path path=Path.of(System.getProperty("arcadium.ceraCosmetics066.output"));Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){failure.printStackTrace();}Minecraft.getInstance().stop();
    }
    private static final class ChangingPlayer extends RemoteClientPlayerEntity {
        GameProfile current;
        ChangingPlayer(net.minecraft.world.World world,GameProfile profile){super(world,profile);current=profile;}
        @Override public GameProfile getGameProfile(){return current==null?super.getGameProfile():current;}
        @Override public void tick() { }
    }
}
