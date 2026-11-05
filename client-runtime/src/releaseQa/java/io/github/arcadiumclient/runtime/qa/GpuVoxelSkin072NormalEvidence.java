// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.lang.invoke.*;
import java.nio.FloatBuffer;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.entity.living.player.PlayerEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class GpuVoxelSkin072NormalEvidence {
    private static final List<Map<String,Object>> samples = new ArrayList<>();
    private static final Map<String,Set<Object>> seen = new HashMap<>();
    private static final IdentityHashMap<Object,Integer> parts = new IdentityHashMap<>();
    private static final ClassValue<VarHandle> meshFields = new ClassValue<>() {
        @Override protected VarHandle computeValue(Class<?> type) {
            for (Class<?> owner=type;owner!=null;owner=owner.getSuperclass()) try {
                return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).unreflectVarHandle(owner.getDeclaredField("skinLayers$mesh"));
            } catch (NoSuchFieldException ignored) {} catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
            throw new IllegalStateException("Missing actual voxel mesh attachment");
        }
    };
    private static VarHandle phase;
    private static String actor;
    private static boolean hooked;
    private GpuVoxelSkin072NormalEvidence() {}

    private static boolean enabled() {
        return Boolean.getBoolean("arcadium.playerPipeline072.voxelNormalProbe") && PlayerPipeline072Qa.enabled()
                && System.getProperty("arcadium.playerPipeline072.mode", "visual").equals("visual");
    }
    public static void observe(Object model, Object player) {
        actor = null;
        if (!enabled() || !(model instanceof PlayerModel value) || !(player instanceof PlayerEntity entity)
                || !player.getClass().getName().equals("io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa$Actor")) return;
        try {
            if (phase == null) phase = MethodHandles.privateLookupIn(PlayerPipeline072Qa.class, MethodHandles.lookup()).findStaticVarHandle(PlayerPipeline072Qa.class, "phase", int.class);
            if ((int)phase.get() < 3) return;
            actor = entity.getName();
            attach(value.hat, 6); attach(value.leftPants, 7); attach(value.rightPants, 8);
            attach(value.leftSleeve, 9); attach(value.rightSleeve, 10); attach(value.jacket, 11);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot identify actual voxel fixture parts", failure); }
    }
    private static void attach(ModelPart part, int index) { Object mesh=meshFields.get(part.getClass()).get(part); if (mesh!=null) parts.put(mesh,index); }

    public static void sample(Object mesh, float scale, float[] quads) {
        if (!enabled() || actor == null || samples.size() >= 64 || !parts.containsKey(mesh)) return;
        if (!seen.computeIfAbsent(actor, key -> Collections.newSetFromMap(new IdentityHashMap<>())).add(mesh)) return;
        Map<String,Object> sample = new LinkedHashMap<>();
        sample.put("actor", actor); sample.put("part", parts.get(mesh)); sample.put("mesh", System.identityHashCode(mesh));
        sample.put("scale", scale); sample.put("quads", quads.length/23); sample.put("modelView", get(GL11.GL_MODELVIEW_MATRIX,16));
        sample.put("projection",get(GL11.GL_PROJECTION_MATRIX,16)); sample.put("rescaleNormal",GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL));
        sample.put("normalize",GL11.glIsEnabled(GL11.GL_NORMALIZE)); sample.put("lighting",GL11.glIsEnabled(GL11.GL_LIGHTING));
        sample.put("program",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)); sample.put("currentColor",get(GL11.GL_CURRENT_COLOR,4));
        sample.put("globalAmbient",get(GL11.GL_LIGHT_MODEL_AMBIENT,4)); sample.put("colorMaterial",GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL));
        sample.put("localViewer",GL11.glGetInteger(GL11.GL_LIGHT_MODEL_LOCAL_VIEWER)); sample.put("twoSide",GL11.glGetInteger(GL11.GL_LIGHT_MODEL_TWO_SIDE));
        sample.put("materialAmbient",material(GL11.GL_AMBIENT)); sample.put("materialDiffuse",material(GL11.GL_DIFFUSE));
        sample.put("materialSpecular",material(GL11.GL_SPECULAR)); sample.put("materialEmission",material(GL11.GL_EMISSION));
        List<Map<String,Object>> lights = new ArrayList<>();
        for (int light=0;light<8;light++) if (GL11.glIsEnabled(GL11.GL_LIGHT0+light)) lights.add(Map.of("index",light,
                "position",light(light,GL11.GL_POSITION),"ambient",light(light,GL11.GL_AMBIENT),"diffuse",light(light,GL11.GL_DIFFUSE)));
        sample.put("lights",lights); sample.put("blend",GL11.glIsEnabled(GL11.GL_BLEND)); sample.put("depthWrite",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        sample.put("blendSrcRgb",GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)); sample.put("blendDstRgb",GL11.glGetInteger(GL14.GL_BLEND_DST_RGB));
        sample.put("alphaTest",GL11.glIsEnabled(GL11.GL_ALPHA_TEST)); sample.put("alphaFunc",GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC));
        sample.put("alphaReference",GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF));
        samples.add(sample); hook();
        if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0) { sample.put("ffpProbeSkipped","A vertex program is active"); return; }
        if (GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH) >= GL11.glGetInteger(GL11.GL_MAX_ATTRIB_STACK_DEPTH)) throw new IllegalStateException("No attribute stack space for the owned FFP probe");
        int priorError=GL11.glGetError(); if(priorError!=0) throw new IllegalStateException("GL error before FFP probe: "+priorError);
        float[] matrix=(float[])sample.get("modelView"), projection=(float[])sample.get("projection");
        Matrix4f mv=new Matrix4f().set(matrix);
        Vector4f origin=new Matrix4f().set(projection).mul(mv).invert().transform(new Vector4f(0,0,0,1)); origin.div(origin.w);
        if (!Float.isFinite(origin.x+origin.y+origin.z)) throw new IllegalStateException("Invalid controlled raster origin");
        sample.put("controlledObjectOrigin",new float[]{origin.x,origin.y,origin.z});
        Matrix3f linear=new Matrix3f(mv), inverse=new Matrix3f(linear).invert(), normal=new Matrix3f(inverse).transpose();
        float factor=1/inverse.getRow(2,new Vector3f()).length(); sample.put("inverseRow3Rescale",factor);
        float[] lengths={linear.getColumn(0,new Vector3f()).length(),linear.getColumn(1,new Vector3f()).length(),linear.getColumn(2,new Vector3f()).length()};
        sample.put("columnScaleLengths",lengths);
        Map<Integer,float[]> old=tracked(); boolean oldRaster=GL11.glGetBoolean(GL11.GL_CURRENT_RASTER_POSITION_VALID);
        int oldMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE),oldActive=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        Map<Integer,float[]> oldMaterial=new LinkedHashMap<>();for(int key:new int[]{GL11.GL_AMBIENT,GL11.GL_DIFFUSE,GL11.GL_SPECULAR,GL11.GL_EMISSION})oldMaterial.put(key,material(key));
        Map<Integer,Boolean> oldFlags=new LinkedHashMap<>();for(int key:new int[]{GL11.GL_LIGHTING,GL12.GL_RESCALE_NORMAL,GL11.GL_NORMALIZE,GL11.GL_BLEND,GL11.GL_ALPHA_TEST,GL11.GL_DEPTH_TEST})oldFlags.put(key,GL11.glIsEnabled(key));
        List<Map<String,Object>> faces=new ArrayList<>(); GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            for(int axis=0;axis<3;axis++) for(int sign:new int[]{-1,1}){
                Vector3f input=new Vector3f().setComponent(axis,sign);
                GL11.glNormal3f(input.x,input.y,input.z); GL11.glRasterPos3f(origin.x,origin.y,origin.z);
                boolean valid=GL11.glGetBoolean(GL11.GL_CURRENT_RASTER_POSITION_VALID);
                Vector3f transformed=normal.transform(new Vector3f(input));
                Vector3f specified=new Vector3f(transformed).mul(factor), normalized=new Vector3f(transformed).normalize();
                Vector3f atlas=linear.transform(new Vector3f(input)).normalize().mul(lengths[2]/lengths[axis]);
                Map<String,Object> face=new LinkedHashMap<>(); face.put("axis",axis); face.put("sign",sign); face.put("rasterValid",valid);
                face.put("rasterColor",get(GL11.GL_CURRENT_RASTER_COLOR,4)); face.put("specifiedNormal",vector(specified));
                face.put("normalizedNormal",vector(normalized)); face.put("atlasNormal",vector(atlas));
                face.put("specifiedLighting",lighting(specified,sample)); face.put("normalizedLighting",lighting(normalized,sample));
                face.put("atlasLighting",lighting(atlas,sample)); faces.add(face);
                if(!valid) throw new IllegalStateException("Controlled FFP raster position was clipped");
            }
        } finally { GL11.glPopAttrib(); }
        boolean restored=oldRaster==GL11.glGetBoolean(GL11.GL_CURRENT_RASTER_POSITION_VALID);
        for(var entry:old.entrySet()) if(oldRaster || entry.getKey()!=GL11.GL_CURRENT_RASTER_POSITION && entry.getKey()!=GL11.GL_CURRENT_RASTER_COLOR && entry.getKey()!=GL11.GL_CURRENT_RASTER_TEXTURE_COORDS && entry.getKey()!=GL11.GL_CURRENT_RASTER_DISTANCE)
            restored &= Arrays.equals(entry.getValue(),get(entry.getKey(),entry.getValue().length));
        for(var entry:oldMaterial.entrySet())restored &= Arrays.equals(entry.getValue(),material(entry.getKey()));
        for(var entry:oldFlags.entrySet())restored &= entry.getValue()==GL11.glIsEnabled(entry.getKey());
        restored &= oldMode==GL11.glGetInteger(GL11.GL_MATRIX_MODE)&&oldActive==GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)&&oldProgram==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        for(var entry:lights)restored &= Arrays.equals((float[])entry.get("position"),light((int)entry.get("index"),GL11.GL_POSITION));
        sample.put("faces",faces); sample.put("allTrackedAttributesAndMatricesRestored",restored);
        sample.put("programAndUniformsUnmodified",oldProgram==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        int error=GL11.glGetError(); sample.put("postProbeGlError",error);
        if(!restored||error!=0) throw new IllegalStateException("FFP probe leaked GL state or produced an error");
    }
    private static Map<Integer,float[]> tracked(){
        Map<Integer,float[]> state=new LinkedHashMap<>();
        for(int key:new int[]{GL11.GL_CURRENT_COLOR,GL11.GL_CURRENT_TEXTURE_COORDS,GL11.GL_CURRENT_RASTER_POSITION,GL11.GL_CURRENT_RASTER_COLOR,GL11.GL_CURRENT_RASTER_TEXTURE_COORDS})state.put(key,get(key,4));
        state.put(GL11.GL_CURRENT_RASTER_DISTANCE,get(GL11.GL_CURRENT_RASTER_DISTANCE,1));state.put(GL11.GL_CURRENT_NORMAL,get(GL11.GL_CURRENT_NORMAL,3));
        state.put(GL11.GL_MODELVIEW_MATRIX,get(GL11.GL_MODELVIEW_MATRIX,16));state.put(GL11.GL_PROJECTION_MATRIX,get(GL11.GL_PROJECTION_MATRIX,16));state.put(GL11.GL_TEXTURE_MATRIX,get(GL11.GL_TEXTURE_MATRIX,16));return state;
    }
    @SuppressWarnings("unchecked") private static float[] lighting(Vector3f normal,Map<String,Object> state){
        float[] ambient=(float[])state.get("globalAmbient"),ma=(float[])state.get("materialAmbient"),md=(float[])state.get("materialDiffuse"),emission=(float[])state.get("materialEmission");
        float[] result={emission[0]+ambient[0]*ma[0],emission[1]+ambient[1]*ma[1],emission[2]+ambient[2]*ma[2],md[3]};
        for(var light:(List<Map<String,Object>>)state.get("lights")){
            float[] p=(float[])light.get("position"),a=(float[])light.get("ambient"),d=(float[])light.get("diffuse");
            if(p[3]!=0)continue;
            float diffuse=Math.max(0,normal.dot(new Vector3f(p[0],p[1],p[2]).normalize()));
            for(int i=0;i<3;i++)result[i]+=a[i]*ma[i]+diffuse*d[i]*md[i];
        }
        for(int i=0;i<3;i++)result[i]=Math.min(1,Math.max(0,result[i]));return result;
    }
    private static float[] vector(Vector3f value){return new float[]{value.x,value.y,value.z};}
    private static float[] get(int key,int count){FloatBuffer values=BufferUtils.createFloatBuffer(Math.max(16,count));GL11.glGetFloatv(key,values);float[] result=new float[count];values.get(result);return result;}
    private static float[] light(int index,int key){FloatBuffer values=BufferUtils.createFloatBuffer(4);GL11.glGetLightfv(GL11.GL_LIGHT0+index,key,values);float[] result=new float[4];values.get(result);return result;}
    private static float[] material(int key){FloatBuffer values=BufferUtils.createFloatBuffer(4);GL11.glGetMaterialfv(GL11.GL_FRONT,key,values);float[] result=new float[4];values.get(result);return result;}
    private static void hook(){
        if(hooked)return;hooked=true;Runtime.getRuntime().addShutdownHook(new Thread(()->{
            String output=System.getProperty("arcadium.playerPipeline072.output");if(output==null)return;
            try{Files.writeString(Path.of(output).resolveSibling("voxel-normal-states.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "visualDiagnosticsOnly",true,"source","https://registry.khronos.org/OpenGL/specs/gl/glspec21.pdf","samples",samples)));}
            catch(Exception failure){failure.printStackTrace();}
        },"Arcadium FFP normal QA"));
    }
}
