package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class P3NativeMatrixOps073Qa {
    private static final boolean ENABLED=Boolean.getBoolean("arcadium.playerFeatures073.nativeOpTrace");
    private static final NativeMatrixTrace073 trace=new NativeMatrixTrace073();
    private static final FloatBuffer scratch=BufferUtils.createFloatBuffer(16);
    private static final Map<Integer,Map<String,Object>> nativeReferences=new HashMap<>();
    private static Method bridgeSnapshot;
    private static boolean registered,active;
    private static long viewToken;
    private static int[] frameProjection;
    private static int rootQueries,projectionQueries,beginEvents,partEvents;
    private static String variant="off",failure;
    private static Map<String,Object> driver;
    private static Thread renderThread;
    private P3NativeMatrixOps073Qa(){}

    public static void begin(String next) {
        if(!ENABLED)return;
        if(active)throw new IllegalStateException("Nested native operation view");
        variant=next;failure=null;frameProjection=null;rootQueries=projectionQueries=beginEvents=partEvents=0;
        trace.begin(++viewToken,next);active=true;renderThread=Thread.currentThread();
        try {
            if(!PlayerFeatures073Qa.voxelObservationEnabled())throw new IllegalStateException("Not the owned one-case voxel fixture");
            if(!registered) {
                Class<?> bridge=Class.forName("arcadium.qa.p3ops.bootstrap.MatrixOpBridge073",true,null);
                if(bridge.getClassLoader()!=null)throw new IllegalStateException("Bridge is not bootstrap defined");
                MethodHandles.Lookup lookup=MethodHandles.lookup();
                MethodHandle event=lookup.findStatic(P3NativeMatrixOps073Qa.class,"event",MethodType.methodType(void.class,int.class,Object.class,int.class));
                MethodHandle before=lookup.findStatic(P3NativeMatrixOps073Qa.class,"before",MethodType.methodType(Object.class,int.class,String.class,Object[].class));
                MethodHandle after=lookup.findStatic(P3NativeMatrixOps073Qa.class,"completed",MethodType.methodType(void.class,Object.class));
                bridge.getMethod("register",MethodHandle.class,MethodHandle.class,MethodHandle.class).invoke(null,event,before,after);
                bridgeSnapshot=bridge.getMethod("snapshot");registered=true;
            }
            if(driver==null) {
                if(!GL.getCapabilities().OpenGL43||(GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK)&GL32.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT)==0)
                    throw new IllegalStateException("Actual compatibility4.3 context required");
                driver=Map.of("renderer",GL11.glGetString(GL11.GL_RENDERER),"vendor",GL11.glGetString(GL11.GL_VENDOR),
                        "version",GL11.glGetString(GL11.GL_VERSION),"profileMask",GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK));
            }
        } catch(Throwable error) {fail(error);}
    }
    public static void event(int kind,Object target,int rawScale) {
        if(!active)return;
        if(Thread.currentThread()!=renderThread){trace.fail("Native model operation arrived on another thread");return;}
        try {
            if(kind==1) {
                beginEvents++;
                if(!(boolean)field(target,"modelActive")||(boolean)field(target,"item")||(boolean)field(target,"finished")
                        ||(boolean)field(target,"suppressFixedFunction"))return;
                if(currentCapture()!=target)throw new IllegalStateException("Model root is not current capture");
                int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE),depth=GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
                int[] root=matrix(GL11.GL_MODELVIEW_MATRIX);rootQueries++;
                if(frameProjection==null){frameProjection=matrix(GL11.GL_PROJECTION_MATRIX);projectionQueries++;}
                trace.model(target,root,frameProjection,mode,depth);
            } else if(kind==2)trace.endModel(target,false);
            else if(kind==3)trace.endModel(target,true);
            else if(kind==4){partEvents++;trace.part(target,rawScale,true);}
            else if(kind==5)trace.part(target,0,false);
            else throw new IllegalArgumentException("Unknown native scope event");
        } catch(Throwable error) {fail(error);}
    }
    public static Object before(int opcode,String site,Object[] arguments) {
        if(!active||!trace.active())return null;
        if(Thread.currentThread()!=renderThread){trace.fail("Native matrix operation arrived on another thread");return null;}
        try {
            int[] f=new int[0];long[] d=new long[0];
            if(opcode==1) {if(arguments.length!=1||!(arguments[0] instanceof Integer))throw new IllegalArgumentException("Matrix mode ABI");f=new int[]{(Integer)arguments[0]};}
            else if(opcode==2||opcode==3||opcode==4) {if(arguments.length!=0)throw new IllegalArgumentException("Matrix stack ABI");}
            else if(opcode==8||opcode==9) {f=floatMatrix(arguments);}
            else if(opcode==13||opcode==14) {d=doubleMatrix(arguments);}
            else if(opcode==5||opcode==6||opcode==7) {
                int count=opcode==7?4:3;if(arguments.length!=count)throw new IllegalArgumentException("Float matrix argument count");
                f=new int[count];for(int i=0;i<count;i++){if(!(arguments[i] instanceof Float))throw new IllegalArgumentException("Float matrix argument type");f[i]=Float.floatToRawIntBits((Float)arguments[i]);}
            } else if(opcode==10||opcode==11||opcode==12||opcode==15||opcode==16) {
                int count=opcode==12?4:opcode==15||opcode==16?6:3;if(arguments.length!=count)throw new IllegalArgumentException("Double matrix argument count");
                d=new long[count];for(int i=0;i<count;i++){if(!(arguments[i] instanceof Double))throw new IllegalArgumentException("Double matrix argument type");d[i]=Double.doubleToRawLongBits((Double)arguments[i]);}
            } else {
                trace.fail("Unsupported actual raw native matrix operation");
                for(Object value:arguments)if(value instanceof Number n){d=Arrays.copyOf(d,d.length+1);d[d.length-1]=n.longValue();}
            }
            return trace.prepare(opcode,site,f,d);
        } catch(Throwable error) {fail(error);return null;}
    }
    public static void completed(Object pending) {
        if(!active)return;
        try{if(!(pending instanceof NativeMatrixTrace073.Pending value))throw new IllegalArgumentException("Wrong completed operation token");trace.completed(value);}catch(Throwable error){fail(error);}
    }
    public static void leaf(Map<String,Object> snapshot) {
        if(!ENABLED||!active)return;
        try {
            int mesh=((Number)snapshot.get("meshId")).intValue();
            int scale=Float.floatToRawIntBits(((Number)snapshot.get("compiledScale")).floatValue());
            Map<String,Object> pair=trace.leaf(currentCapture(),mesh,scale,
                    (int[])((Map<?,?>)snapshot.get("physicalModelView")).get("rawBits"),
                    (int[])((Map<?,?>)snapshot.get("projection")).get("rawBits"));
            snapshot.put("nativeMatrixOperationAssociation",pair);
        } catch(Throwable error){fail(error);}
    }
    public static void submission(Map<String,Object> snapshot) {
        if(!ENABLED||!active)return;
        try{trace.submission(((Number)snapshot.get("meshId")).intValue(),((Number)snapshot.get("observedMeshPart")).intValue(),
                (int[])((Map<?,?>)snapshot.get("physicalModelViewAtSubmitEntry")).get("rawBits"),
                (int[])((Map<?,?>)snapshot.get("projectionAtSubmitEntry")).get("rawBits"));}catch(Throwable error){fail(error);}
    }
    public static void upload(int count,int[] originalWords) {
        if(!ENABLED||!active)return;
        try{trace.upload(count,originalWords);}catch(Throwable error){fail(error);}
    }
    public static Map<String,Object> end() {
        if(!ENABLED)return Map.of("enabled",false);
        Map<String,Object> result=trace.end();
        try {
            result.put("enabled",true);result.put("driver",driver);result.put("rootMatrixQueryCount",rootQueries);
            result.put("frameProjectionQueryCount",projectionQueries);result.put("actualModelBeginEvents",beginEvents);result.put("actualPartEnterEvents",partEvents);
            result.put("bootstrap",registered?bridgeSnapshot.invoke(null):Map.of("registered",false));
            if(rootQueries!=((Number)result.get("modelCount")).intValue()||projectionQueries!=1||!registered)throw new IllegalStateException("Authoritative seed query/registration count changed");
            if(variant.equals("native")) {
                nativeReferences.clear();
                for(Object model:(List<?>)result.get("models"))for(Object row:(List<?>)((Map<?,?>)model).get("leaves")) {
                    Map<String,Object> leaf=(Map<String,Object>)row;int id=((Number)leaf.get("meshId")).intValue();
                    if(nativeReferences.put(id,leaf)!=null)throw new IllegalStateException("Native reference mesh repeated");
                }
                if(nativeReferences.size()!=12)throw new IllegalStateException("Native reference mesh roster changed");
            }
            List<Map<String,Object>> comparisons=new ArrayList<>();
            for(Object row:(List<?>)result.get("submissions")) {
                Map<?,?> value=(Map<?,?>)row,reference=nativeReferences.get(((Number)value.get("meshId")).intValue());
                if(reference==null)throw new IllegalStateException("Submission has no same-JVM native mesh reference");
                comparisons.add(Map.of("meshId",value.get("meshId"),"actualPartId",value.get("partId"),
                        "physicalRawMatch",Arrays.equals((int[])reference.get("physicalRawBits"),(int[])value.get("physicalRawBits")),
                        "projectionRawMatch",Arrays.equals((int[])reference.get("projectionRawBits"),(int[])value.get("projectionRawBits"))));
            }
            result.put("sameJvmNativeSubmissionComparisons",comparisons);
            if(!comparisons.isEmpty()&&(comparisons.size()!=12||comparisons.stream().anyMatch(v->!Boolean.TRUE.equals(v.get("physicalRawMatch"))||!Boolean.TRUE.equals(v.get("projectionRawMatch")))))
                throw new IllegalStateException("Native/submission physical matrix raw comparison differs");
        } catch(Throwable error){fail(error);}
        if(failure!=null){result.put("failure",failure);result.put("recordingPassed",false);}
        result.put("argumentProvenance","Primitive raw arguments captured at the final GL invocation site before call, committed only after original call returns");
        result.put("nativeReplayPending",true);result.put("performanceClaim",false);
        try {
            Path directory=Path.of(System.getProperty("arcadium.playerFeatures073.output")).toAbsolutePath().getParent();
            String name=variant.equals("optimized")?"full":variant;
            if(!Set.of("native","native-repeat","full","body-native3d","voxel-affine").contains(name))throw new IllegalArgumentException("Unknown bounded view name");
            Path file=directory.resolve(String.format(Locale.ROOT,"native-matrix-ops-%04d-%s.json",viewToken,name));
            result.put("sidecarFileName",file.getFileName().toString());
            if(Files.exists(file))throw new IllegalStateException("Never overwrite a prior native operation trace");
            Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        } catch(Throwable error){result.put("sidecarFailure",error.getClass().getName());result.put("recordingPassed",false);}
        active=false;renderThread=null;variant="off";return result;
    }
    private static int[] matrix(int name){scratch.clear();GL11.glGetFloatv(name,scratch);int[] bits=new int[16];for(int i=0;i<16;i++)bits[i]=Float.floatToRawIntBits(scratch.get(i));return bits;}
    private static int[] floatMatrix(Object[] args) {
        if(args.length!=1)throw new IllegalArgumentException("Float matrix argument count");int[] bits=new int[16];
        if(args[0] instanceof FloatBuffer b){int p=b.position();if(b.remaining()<16||b.remaining()>4096)throw new IllegalArgumentException("Float matrix range");for(int i=0;i<16;i++)bits[i]=Float.floatToRawIntBits(b.get(p+i));}
        else if(args[0] instanceof float[] a){if(a.length<16||a.length>4096)throw new IllegalArgumentException("Float array range");for(int i=0;i<16;i++)bits[i]=Float.floatToRawIntBits(a[i]);}
        else throw new IllegalArgumentException("Float matrix native ABI");return bits;
    }
    private static long[] doubleMatrix(Object[] args) {
        if(args.length!=1)throw new IllegalArgumentException("Double matrix argument count");long[] bits=new long[16];
        if(args[0] instanceof DoubleBuffer b){int p=b.position();if(b.remaining()<16||b.remaining()>4096)throw new IllegalArgumentException("Double matrix range");for(int i=0;i<16;i++)bits[i]=Double.doubleToRawLongBits(b.get(p+i));}
        else if(args[0] instanceof double[] a){if(a.length<16||a.length>4096)throw new IllegalArgumentException("Double array range");for(int i=0;i<16;i++)bits[i]=Double.doubleToRawLongBits(a[i]);}
        else throw new IllegalArgumentException("Double matrix native ABI");return bits;
    }
    private static Object currentCapture()throws Exception{return Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityCapture").getMethod("current").invoke(null);}
    private static Object field(Object object,String name)throws Exception{for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private static void fail(Throwable error){if(failure==null)failure=error.getClass().getName();trace.fail("Native operation provider failure: "+error.getClass().getName());}
}
