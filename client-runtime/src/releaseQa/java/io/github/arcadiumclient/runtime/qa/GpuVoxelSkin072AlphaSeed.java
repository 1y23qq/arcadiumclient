// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.skins.UploadedSkinTexture;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.FloatBuffer;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.*;

public final class GpuVoxelSkin072AlphaSeed {
    private static final int[] ALPHAS = {0, 64, 128, 192, 255};
    private static final List<Map<String,Object>> gpuStates = new ArrayList<>();
    private static final List<Map<String,Object>> legacyStates = new ArrayList<>();
    private static VarHandle phase;
    private static String currentActor;
    private static boolean hooked;
    private GpuVoxelSkin072AlphaSeed() {}

    public static boolean enabled() {
        return Boolean.getBoolean("arcadium.voxelQuality072.alphaSkin") && PlayerPipeline072Qa.enabled()
                && System.getProperty("arcadium.playerPipeline072.mode", "visual").equals("visual");
    }
    private static boolean traceMaterials(){return (enabled()||Boolean.getBoolean("arcadium.voxelQuality072.materialProbe"))
            &&PlayerPipeline072Qa.enabled()&&System.getProperty("arcadium.playerPipeline072.mode","visual").equals("visual");}
    public static boolean ownedFixtureActor(){return traceMaterials()&&currentActor!=null;}
    public static void observePlayer(Object player){
        currentActor=null;
        if(!traceMaterials()||!(player instanceof PlayerEntity entity)||!player.getClass().getName().equals("io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa$Actor"))return;
        try{
            if(phase==null)phase=MethodHandles.privateLookupIn(PlayerPipeline072Qa.class,MethodHandles.lookup()).findStaticVarHandle(PlayerPipeline072Qa.class,"phase",int.class);
            if((int)phase.get()>=3)currentActor=entity.getName();
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot identify the owned alpha fixture",failure);}
    }
    public static void legacyState(Object mesh,float scale){
        if(!traceMaterials()||currentActor==null||legacyStates.size()>=16)return;
        Map<String,Object> state=glState();state.put("actor",currentActor);state.put("mesh",System.identityHashCode(mesh));state.put("scale",scale);
        legacyStates.add(state);hookStates();
    }

    public static BufferedImage image() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        int[] alphas=alphas();
        for (int y=0;y<64;y++) for (int x=0;x<64;x++) image.setRGB(x, y, ((x+y)%3 == 0) ? 0xFF12CC66 : 0xFFEE2288);
        for (int[] region : new int[][]{{32,0,32,16},{16,32,24,16},{40,32,16,16},{0,32,16,16},{0,48,16,16},{48,48,16,16}}) {
            for (int y=region[1];y<region[1]+region[3];y++) for (int x=region[0];x<region[0]+region[2];x++) {
                int alpha = alphas[(x*3+y*7)%alphas.length], rgb = ((x+y)%2 == 0) ? 0x22AAFF : 0xFFCC22;
                image.setRGB(x, y, alpha<<24 | rgb);
            }
        }
        return image;
    }
    private static int[] alphas(){return Boolean.getBoolean("arcadium.voxelQuality072.binarySkin")?new int[]{0,255}:ALPHAS;}

    public static void gpuState() {
        if (!traceMaterials() || gpuStates.size() >= 16) return;
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        Map<String,Object> state=glState();
        if(program!=0)for (String name : new String[]{"uLit", "uLightmapped", "uAlphaPass", "uTextureArrayEnabled", "uBoxInstancing"}) {
            int location = GL20.glGetUniformLocation(program, name); if (location >= 0) state.put(name, GL20.glGetUniformi(program, location));
        }
        if(program!=0){
            int enabled=GL20.glGetUniformLocation(program,"uTextureArrayEnabled"),sampler=GL20.glGetUniformLocation(program,"uTextureArray");
            boolean array=enabled>=0&&GL20.glGetUniformi(program,enabled)!=0;
            state.put("effectiveTexture",textureState(array&&sampler>=0?GL20.glGetUniformi(program,sampler):0,array?GL30.GL_TEXTURE_2D_ARRAY:GL11.GL_TEXTURE_2D));
        }
        gpuStates.add(state);hookStates();
    }
    private static Map<String,Object> glState(){
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        Map<String,Object> state = new LinkedHashMap<>(); state.put("program", program);
        state.put("blend", GL11.glIsEnabled(GL11.GL_BLEND)); state.put("alphaTest", GL11.glIsEnabled(GL11.GL_ALPHA_TEST));
        state.put("blendSrcRgb", GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)); state.put("blendDstRgb", GL11.glGetInteger(GL14.GL_BLEND_DST_RGB));
        state.put("blendSrcAlpha", GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)); state.put("blendDstAlpha", GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        state.put("alphaFunc", GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC)); state.put("alphaReference", GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF));
        state.put("depthTest", GL11.glIsEnabled(GL11.GL_DEPTH_TEST)); state.put("depthWrite", GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        state.put("depthFunc", GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        FloatBuffer color=org.lwjgl.BufferUtils.createFloatBuffer(4);GL11.glGetFloatv(GL11.GL_CURRENT_COLOR,color);state.put("currentColor",new float[]{color.get(0),color.get(1),color.get(2),color.get(3)});
        for(var matrix:Map.of("modelViewMatrix",GL11.GL_MODELVIEW_MATRIX,"projectionMatrix",GL11.GL_PROJECTION_MATRIX,"textureMatrix",GL11.GL_TEXTURE_MATRIX).entrySet()){
            FloatBuffer buffer=org.lwjgl.BufferUtils.createFloatBuffer(16);GL11.glGetFloatv(matrix.getValue(),buffer);float[] values=new float[16];buffer.get(values);state.put(matrix.getKey(),values);
        }
        if(program==0)state.put("effectiveTexture",textureState(0,GL11.GL_TEXTURE_2D));
        return state;
    }
    private static Map<String,Object> textureState(int unit,int target){
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);Map<String,Object> result=new LinkedHashMap<>();
        try{
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int texture=GL11.glGetInteger(target==GL11.GL_TEXTURE_2D?GL11.GL_TEXTURE_BINDING_2D:GL30.GL_TEXTURE_BINDING_2D_ARRAY);
            int sampler=GL30.glGetIntegeri(GL33.GL_SAMPLER_BINDING,unit);result.put("unit",unit);result.put("target",target);result.put("texture",texture);result.put("samplerObject",sampler);
            if(target==GL11.GL_TEXTURE_2D){
                for(var entry:Map.of("envMode",GL11.GL_TEXTURE_ENV_MODE,"combineAlpha",GL13.GL_COMBINE_ALPHA,"sourceAlpha0",GL13.GL_SOURCE0_ALPHA,"sourceAlpha1",GL13.GL_SOURCE1_ALPHA).entrySet())
                    result.put(entry.getKey(),GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV,entry.getValue()));
                result.put("alphaScale",GL11.glGetTexEnvf(GL11.GL_TEXTURE_ENV,GL11.GL_ALPHA_SCALE));
            }
            if(texture!=0){
                result.put("width",GL11.glGetTexLevelParameteri(target,0,GL11.GL_TEXTURE_WIDTH));result.put("height",GL11.glGetTexLevelParameteri(target,0,GL11.GL_TEXTURE_HEIGHT));
                result.put("internalFormat",GL11.glGetTexLevelParameteri(target,0,GL11.GL_TEXTURE_INTERNAL_FORMAT));
                for(var entry:Map.of("minFilter",GL11.GL_TEXTURE_MIN_FILTER,"magFilter",GL11.GL_TEXTURE_MAG_FILTER,"wrapS",GL11.GL_TEXTURE_WRAP_S,"wrapT",GL11.GL_TEXTURE_WRAP_T).entrySet())
                    result.put(entry.getKey(),sampler==0?GL11.glGetTexParameteri(target,entry.getValue()):GL33.glGetSamplerParameteri(sampler,entry.getValue()));
            }
        }finally{GL13.glActiveTexture(active);}return result;
    }
    private static void hookStates(){
        if (!hooked) {
            hooked = true;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                String output = System.getProperty("arcadium.playerPipeline072.output"); if (output == null) return;
                try {
                    Files.writeString(Path.of(output).resolveSibling("voxel-alpha-gpu-states.json"), new GsonBuilder().setPrettyPrinting().create().toJson(gpuStates));
                    Files.writeString(Path.of(output).resolveSibling("voxel-alpha-legacy-states.json"), new GsonBuilder().setPrettyPrinting().create().toJson(legacyStates));
                }
                catch (Exception failure) { failure.printStackTrace(); }
            }, "Arcadium voxel alpha QA"));
        }
    }

    @SuppressWarnings("unchecked")
    public static void seed(Minecraft mc) throws Exception {
        if (!enabled()) return;
        BufferedImage image = image(); int[] pixels = image.getRGB(0, 0, 64, 64, null, 0, 64);
        Identifier skin = Features062Qa.skin;
        mc.getTextureManager().register(skin, new UploadedSkinTexture(image));
        Class<?> pixelType = Class.forName("org.polyfrost.skinlayers.mesh.SkinPixels"), meshType = Class.forName("org.polyfrost.skinlayers.skin.SkinMeshes");
        Object data = pixelType.getConstructor(int.class, int.class, int[].class).newInstance(64, 64, pixels);
        Object companion = meshType.getField("Companion").get(null);
        var build = companion.getClass().getMethod("build", pixelType, boolean.class);
        Class<?> key = Class.forName("org.polyfrost.skinlayers.skin.SkinMeshCache$Key");
        var constructor = key.getDeclaredConstructor(Identifier.class, boolean.class); constructor.setAccessible(true);
        var field = Class.forName("org.polyfrost.skinlayers.skin.SkinMeshCache").getDeclaredField("players"); field.setAccessible(true);
        var cache = (Map<Object,Object>)field.get(null);
        for (boolean slim : new boolean[]{false, true}) cache.put(constructor.newInstance(skin, slim), build.invoke(companion, data, slim));
        String output = System.getProperty("arcadium.playerPipeline072.output");
        if (output != null) {
            Path folder = Path.of(output).getParent(); ImageIO.write(image, "png", folder.resolve("voxel-alpha-skin.png").toFile());
            Map<Integer,Integer> counts = new TreeMap<>(); for (int pixel : pixels) counts.merge(pixel>>>24, 1, Integer::sum);
            Files.writeString(folder.resolve("voxel-alpha-skin.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "visualOnly", true, "texture", skin.toString(), "outerAlphaValues", alphas(), "allTexelAlphaCounts", counts,
                    "underlyingColors", List.of("12CC66", "EE2288"), "outerColors", List.of("22AAFF", "FFCC22"), "pixelHash", Arrays.hashCode(pixels))));
        }
    }
}
