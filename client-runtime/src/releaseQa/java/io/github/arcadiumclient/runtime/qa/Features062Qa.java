package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.debug.CustomF3Data;
import io.github.arcadiumclient.runtime.client.debug.DebugInfoSource;
import io.github.arcadiumclient.runtime.client.features.*;
import io.github.arcadiumclient.runtime.client.gui.*;
import io.github.arcadiumclient.runtime.client.skins.UploadedSkinTexture;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.resource.Identifier;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.GL11;

public final class Features062Qa {
    public static Identifier skin;
    public static int voxelDraws, meshCompiles, flatParts, voxelParts;
    private static int ticks, phase, wait, previousCompiles;
    private static long frameTime;
    private static boolean done;
    private static final Map<String,Object> report = new LinkedHashMap<>();
    private static final List<Double> samples = new ArrayList<>();
    public static boolean enabled() { return System.getProperty("arcadium.features062.output") != null; }
    private static void require(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
    public static void tick() {
        if(done)return;
        var mc=Minecraft.getInstance();
        try {
            require(++ticks<2400,"Feature QA timed out at phase "+phase);wait++;
            if(phase==0 && ticks>65 && mc.screen != null && mc.screen.getClass().getSimpleName().contains("MainMenu")) {
                require(!CustomF3Config.getEnabled(),"Custom F3 default was enabled");
                ClientPreferences.setSetupComplete(true);mc.options.pauseOnUnfocus=false;mc.options.vsync=false;mc.options.viewDistance=3;mc.options.fpsLimit=260;org.lwjgl.opengl.Display.setVSyncEnabled(false);
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;
                var repository=mc.getResourcePacks();repository.reload();
                var pack=repository.getAvailablePacks().stream().filter(p->p.getName().equals("Compat062.zip")).findFirst().orElseThrow();
                require(pack.getFormat()==1,"Pack catalog format was not adapted");
                repository.setSelectedPacks(List.of(pack));mc.options.resourcePacks.clear();mc.options.resourcePacks.add(pack.getName());mc.reloadResources();
                try(var stream=mc.getResourceManager().getResource(new Identifier("minecraft","textures/items/diamond_sword.png")).asStream()) { require(ImageIO.read(stream).getRGB(0,0)==0xFF123456,"Converted texture did not reach the resource manager"); }
                report.put("crossVersionPackApplied",true);
                mc.startGame("arcadium-features062","Arcadium 062 QA",new WorldSettings(62,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());
                phase=1;wait=0;
            } else if(phase==1 && mc.world!=null && mc.player!=null && wait>40) {
                mc.openScreen(null);mc.player.yaw=0;mc.player.pitch=0;seedSkin(mc);
                CustomF3Config.setEnabled(true);CustomF3Config.setAnimations(false);CustomF3Config.setSpoofSpecs(true);CustomF3Config.setCpu("QA CPU");CustomF3Config.setGpu("QA GPU");
                require(CustomF3Config.overrideText("fps","1234").equals("1234"),"Specification overrides touched FPS");
                mc.options.debugEnabled=true;phase=2;wait=0;
            } else if(phase==2 && wait>30) {
                DebugInfoSource source=null;
                for(var field:mc.gui.getClass().getDeclaredFields())if(DebugInfoSource.class.isAssignableFrom(field.getType())){field.setAccessible(true);source=(DebugInfoSource)field.get(mc.gui);break;}
                require(source!=null,"Debug source mixin did not apply");
                var frame=CustomF3Data.collect(mc,source);
                String text=frame.left().toString()+frame.right();require(text.contains("QA CPU") && text.contains("QA GPU"),"Hardware text overrides were not drawn");
                require(!"QA GPU".equals(GL11.glGetString(GL11.GL_RENDERER)),"Hardware override changed the renderer");
                report.put("customF3",true);report.put("specOverridesDisplayOnly",true);
                Screenshot.take(mc.gameDir,"custom-f3.png",mc.width,mc.height,mc.getRenderTarget());
                mc.openScreen(new ArcadiumCustomF3Screen(null));phase=3;wait=0;
            } else if(phase==3 && wait>20) {
                Screenshot.take(mc.gameDir,"custom-f3-editor.png",mc.width,mc.height,mc.getRenderTarget());
                var method=mc.screen.getClass().getDeclaredMethod("handleKeyPressed",int.class,int.class);method.setAccessible(true);require((boolean)method.invoke(mc.screen,256,0),"Editor did not consume Escape");phase=4;wait=0;
            } else if(phase==4 && wait>10) {
                require(mc.screen==null,"Escape did not return to the game");report.put("editorEscape",true);
                mc.options.debugEnabled=false;
                var video=ArcadiumVideoSettingsScreen.replace(new net.minecraft.client.gui.screen.menu.options.VideoOptionsScreen(null,mc.options));mc.openScreen(video);
                var query=video.getClass().getDeclaredField("query$delegate");query.setAccessible(true);((androidx.compose.runtime.MutableState<String>)query.get(video)).setValue("mipmap");phase=40;wait=0;
            } else if(phase==40 && wait>20) {
                Screenshot.take(mc.gameDir,"video-search.png",mc.width,mc.height,mc.getRenderTarget());report.put("videoMainSearch",true);mc.openScreen(null);
                mc.options.perspective=1;Skin3DConfig.INSTANCE.chooseEnabled(false);phase=5;wait=0;
            } else if(phase==5 && wait>55) {
                report.put("flatSkinFrameTime",summary());Skin3DConfig.INSTANCE.chooseEnabled(true);samples.clear();voxelDraws=meshCompiles=flatParts=voxelParts=0;phase=6;wait=0;
            } else if(phase==6 && wait>55) {
                report.put("skinCounters",Map.of("voxelDraws",voxelDraws,"voxelParts",voxelParts,"flatParts",flatParts,"meshCompiles",meshCompiles));
                require(voxelDraws>0,"Third-person voxel skins did not render");require(flatParts>0,"Flat body parts lost instancing");
                report.put("thirdPersonBack",Map.of("voxelDraws",voxelDraws,"voxelParts",voxelParts,"instancedFlatParts",flatParts,"compiledMeshes",meshCompiles,"frameTime",summary()));
                Screenshot.take(mc.gameDir,"skin-third-person-back.png",mc.width,mc.height,mc.getRenderTarget());
                previousCompiles=meshCompiles;voxelDraws=0;samples.clear();mc.options.perspective=2;phase=7;wait=0;
            } else if(phase==7 && wait>55) {
                require(voxelDraws>0,"Front-facing third person did not render voxel skins");require(meshCompiles==previousCompiles,"Camera change rebuilt cached skin meshes");
                report.put("thirdPersonFront",Map.of("voxelDraws",voxelDraws,"additionalMeshCompiles",meshCompiles-previousCompiles,"frameTime",summary()));
                Screenshot.take(mc.gameDir,"skin-third-person-front.png",mc.width,mc.height,mc.getRenderTarget());finish(null);
            }
        } catch(Throwable failure) { finish(failure); }
    }
    private static Object summary() {var sorted=samples.stream().sorted().toList();return Map.of("frames",samples.size(),"meanMillis",samples.stream().mapToDouble(v->v).average().orElse(0),"p95Millis",sorted.isEmpty()?0:sorted.get(Math.min(sorted.size()-1,(int)(sorted.size()*.95))));}
    public static void frame() {long now=System.nanoTime();if(phase>=5 && phase<=7 && wait>15 && frameTime!=0)samples.add((now-frameTime)/1_000_000d);frameTime=now;}
    private static void seedSkin(Minecraft mc) throws Exception {
        BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);int[] pixels=new int[4096];
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){int color=((x+y)%3==0)?0xFF2255CC:0xFFDD8844;image.setRGB(x,y,color);pixels[y*64+x]=color;}
        skin=new Identifier("arcadium","qa-skin-062");mc.getTextureManager().register(skin,new UploadedSkinTexture(image));
        Class<?> px=Class.forName("org.polyfrost.skinlayers.mesh.SkinPixels"),meshes=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshes");
        Object data=px.getConstructor(int.class,int.class,int[].class).newInstance(64,64,pixels),companion=meshes.getField("Companion").get(null);
        var build=companion.getClass().getMethod("build",px,boolean.class);Class<?> key=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshCache$Key");
        var constructor=key.getDeclaredConstructor(Identifier.class,boolean.class);constructor.setAccessible(true);
        var field=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshCache").getDeclaredField("players");field.setAccessible(true);var cache=(Map<Object,Object>)field.get(null);
        for(boolean slim:new boolean[]{false,true})cache.put(constructor.newInstance(skin,slim),build.invoke(companion,data,slim));
    }
    private static void finish(Throwable failure) {
        done=true;report.put("passed",failure==null);if(failure!=null){report.put("error",failure.toString());failure.printStackTrace();}
        try{Path path=Path.of(System.getProperty("arcadium.features062.output"));Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));
            if(failure!=null){var mc=Minecraft.getInstance();Screenshot.take(mc.gameDir,"failure-062.png",mc.width,mc.height,mc.getRenderTarget());}
        }catch(Exception error){error.printStackTrace();}
        Minecraft.getInstance().stop();
    }
}
