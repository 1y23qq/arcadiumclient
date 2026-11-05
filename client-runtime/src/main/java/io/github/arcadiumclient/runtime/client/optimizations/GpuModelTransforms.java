// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class GpuModelTransforms {
    public static final int STRIDE = 19;
    private static final int JOB_FLOATS = 40, MAX_JOBS = 32768;
    private static final int MAGIC_1 = Float.floatToRawIntBits(-9876543f), MAGIC_2 = Float.floatToRawIntBits(-8765432f), SORT_MAGIC = Float.floatToRawIntBits(-7654321f);
    private static FloatBuffer jobs = BufferUtils.createFloatBuffer(1024 * JOB_FLOATS);
    private static final Matrix4f marker = new Matrix4f();
    private static final Matrix4f root = new Matrix4f(), box = new Matrix4f(), resolved = new Matrix4f();
    private static int count, uploaded, program, input, countUniform, players, sortProgram, sortCountUniform, scratch;
    private static final VisiblePlayerBudget visibility = new VisiblePlayerBudget();
    private static boolean checked, supported, failed;
    private static Object batchOwner;
    private static Thread batchThread;
    private static long batchEpoch, modelRevision;
    public static long offloadedParts, fallbackParts, dispatches, cpuRecoveries, sortedBatches;
    private static volatile String capability = "Not tested yet";

    private GpuModelTransforms() { }

    public static void beginBatch() {
        beginBatch(null);
    }

    public static void beginBatch(Object owner) {
        batchOwner = owner; batchThread = Thread.currentThread(); batchEpoch++; modelRevision++;
        count = uploaded = 0;
        GpuPlayerPackets.beginBatch();
        NativePlayerPosePipeline.beginBatch();
        GpuVoxelSkinPipeline.beginBatch();
    }
    public static Object batchOwner() { return batchOwner; }
    public static boolean batchThread() { return batchThread == Thread.currentThread(); }
    public static long batchEpoch() { return batchEpoch; }
    public static long modelRevision() { return modelRevision; }
    public static boolean outputsReusable() { return supported && !failed && program != 0; }

    public static void beginFrame() {
        if(BetaOptimizationsConfig.getGpuPlayerPipeline()||BetaOptimizationsConfig.getGpuEquipmentPipeline())PlayerPoseOptions.refresh();
        if(BetaOptimizationsConfig.getGpuModelTransforms() || BetaOptimizationsConfig.getGpuPlayerPipeline()||BetaOptimizationsConfig.getGpuEquipmentPipeline())visibility.beginFrame();
        else visibility.clear();
        players=visibility.previousFrame();
    }

    public static void notePlayer(Object player) { if(BetaOptimizationsConfig.getGpuModelTransforms()||BetaOptimizationsConfig.getGpuPlayerPipeline()||BetaOptimizationsConfig.getGpuEquipmentPipeline())visibility.observe(player); }
    public static void clearFrameBudget(){visibility.clear();players=0;}

    public static boolean ready() {
        int minimum=BetaOptimizationsConfig.getMinimumPlayers();
        if ((!BetaOptimizationsConfig.getGpuModelTransforms()&&!BetaOptimizationsConfig.getGpuPlayerPipeline()&&!BetaOptimizationsConfig.getGpuEquipmentPipeline()) || players < minimum || failed) return false;
        if (!checked) initialize();
        return supported;
    }
    public static int visiblePlayers(){return players;}

    private static void initialize() {
        checked = true;
        if (!GL.getCapabilities().OpenGL43) { capability = "OpenGL 4.3 required; using Argentum"; return; }
        int shader = 0;
        try {
            String source;
            try (var stream = GpuModelTransforms.class.getResourceAsStream("/assets/arcadium/shaders/model_transforms.comp")) {
                if (stream == null) throw new IOException("Missing model transform shader");
                source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            try(var stream=GpuModelTransforms.class.getResourceAsStream("/assets/arcadium/shaders/player_pose.glsl")){
                if(stream==null)throw new IOException("Missing compact player shader");
                int line=source.indexOf('\n');source=source.substring(0,line+1)+new String(stream.readAllBytes(),StandardCharsets.UTF_8)+source.substring(line+1);
            }
            shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
            GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            program = GL20.glCreateProgram(); GL20.glAttachShader(program, shader); GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            countUniform = GL20.glGetUniformLocation(program, "instanceCount");
            input = GL15.glGenBuffers(); scratch = GL15.glGenBuffers();
            String sortSource;
            try (var stream = GpuModelTransforms.class.getResourceAsStream("/assets/arcadium/shaders/model_sort.comp")) {
                if (stream == null) throw new IOException("Missing model sort shader");
                sortSource = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            int sortShader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
            try {
                GL20.glShaderSource(sortShader, sortSource); GL20.glCompileShader(sortShader);
                if (GL20.glGetShaderi(sortShader, GL20.GL_COMPILE_STATUS) == 0) throw new IllegalStateException(GL20.glGetShaderInfoLog(sortShader));
                sortProgram = GL20.glCreateProgram(); GL20.glAttachShader(sortProgram, sortShader); GL20.glLinkProgram(sortProgram);
                if (GL20.glGetProgrami(sortProgram, GL20.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20.glGetProgramInfoLog(sortProgram));
                sortCountUniform = GL20.glGetUniformLocation(sortProgram, "instanceCount");
            } finally { GL20.glDeleteShader(sortShader); }
            supported = true; validateProgram(); capability = "Validated compute";
        } catch (Exception error) { disable(error); }
        finally { if (shader != 0) GL20.glDeleteShader(shader); }
    }

    public static Matrix4f enqueue(Matrix4fc base, float tx, float ty, float tz, float rx, float ry, float rz, Matrix4fc localBox) {
        if (count >= MAX_JOBS) throw new IllegalStateException("GPU pose job limit exceeded");
        int offset = count * JOB_FLOATS;
        if (offset + JOB_FLOATS > jobs.capacity()) {
            FloatBuffer larger = BufferUtils.createFloatBuffer(Math.min(MAX_JOBS * JOB_FLOATS, jobs.capacity() * 2));
            jobs.position(0).limit(offset); larger.put(jobs); jobs = larger;
        }
        jobs.clear(); base.get(offset, jobs);
        jobs.put(offset + 16, tx).put(offset + 17, ty).put(offset + 18, tz).put(offset + 19, 0);
        jobs.put(offset + 20, rx).put(offset + 21, ry).put(offset + 22, rz).put(offset + 23, 0);
        localBox.get(offset + 24, jobs);
        marker.identity().m00(count++).m01(Float.intBitsToFloat(MAGIC_1)).m02(Float.intBitsToFloat(MAGIC_2)); modelRevision++;
        return marker;
    }

    private static void validateProgram() {
        int testBuffer=GL15.glGenBuffers();
        int oldCopy=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER), oldStorage=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        long oldParts=offloadedParts,oldDispatches=dispatches,oldSorted=sortedBatches;
        int[] payload=new int[64*STRIDE];Matrix4f[] expected=new Matrix4f[64];
        float[] rest=new float[12*16];
        for(int part=0;part<12;part++){
            int bone=PlayerPoseMath.BONES[part],t=part*16;
            rest[t]=bone==2?-5:bone==3?5:bone==4?-1.9f:bone==5?1.9f:0;
            rest[t+1]=bone==2||bone==3?2:bone==4||bone==5?12:0;
            rest[t+8]=-.25f;rest[t+10]=-.125f;rest[t+12]=.5f;rest[t+13]=.75f;rest[t+14]=.25f;
        }
        int compactTemplate=GpuPlayerPackets.registerTemplate(rest);
        try {
            for(int i=0;i<64;i++) {
                var base=new Matrix4f().translation(i*.75f-24,i%7-3,8+i*.125f).rotateY(i*.13f).scale(-.9375f,-.9375f,.9375f);
                var shape=new Matrix4f().translation(-.25f,-.125f,-.125f).scale(i%2==0?.5f:-.5f,.75f,.25f);
                Matrix4f encoded;
                if(i<32){encoded=enqueue(base,.1f,.5f,0,i*.11f,i*.07f,i*.03f,shape);expected[i]=resolve(i,new Matrix4f());}
                else if(i<48){
                    int player=GpuPlayerPackets.add(base,i*.51f,.6f,i*.78f,45,30,(i%7)/7f,0,i%3==0?3:1,i%5==0,i%3==0,i%11==0,compactTemplate),part=i%12;
                    encoded=new Matrix4f().m00(-(player*16+part+1)).m01(Float.intBitsToFloat(MAGIC_1)).m02(Float.intBitsToFloat(MAGIC_2));
                    expected[i]=GpuPlayerPackets.resolve(player*16+part,new Matrix4f());
                }else{
                    int player=GpuPlayerPackets.addPose(base,compactTemplate),part=i%12;
                    float tx=i*.01f,ty=-i*.02f,tz=.25f,rx=i*.13f,ry=i*-.17f,rz=i*.05f;
                    GpuPlayerPackets.writePose(player,part,tx,ty,tz,rx,ry,rz);
                    encoded=new Matrix4f().m00(-(player*16+part+1)).m01(Float.intBitsToFloat(MAGIC_1)).m02(Float.intBitsToFloat(MAGIC_2));
                    expected[i]=new Matrix4f(base).translate(tx,ty,tz).rotateZ(rz).rotateY(ry).rotateX(rx)
                            .translate(-.25f,0,-.125f).scale(.5f,.75f,.25f);
                }
                int p=i*STRIDE;
                for(int c=0;c<4;c++)for(int r=0;r<3;r++)payload[p+c*3+r]=Float.floatToRawIntBits(encoded.get(c,r));
                payload[p+12]=i;
            }
            deferSort(payload,64);var data=BufferUtils.createIntBuffer(payload.length);data.put(payload).flip();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,testBuffer);GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,data,GL15.GL_STREAM_DRAW);
            expand(testBuffer,data);var actual=BufferUtils.createIntBuffer(payload.length);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,testBuffer);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,actual);
            boolean[] seen=new boolean[64];float previous=Float.POSITIVE_INFINITY;
            for(int i=0;i<64;i++) {
                int p=i*STRIDE,id=actual.get(p+12);
                if(id<0||id>=64||seen[id])throw new IllegalStateException("GPU sort lost an instance");seen[id]=true;
                for(int c=0;c<4;c++)for(int r=0;r<3;r++) {
                    float value=Float.intBitsToFloat(actual.get(p+c*3+r));
                    if(!Float.isFinite(value)||Math.abs(value-expected[id].get(c,r))>0.0005f)throw new IllegalStateException("GPU transform validation failed");
                }
                float d=distance(actual,i);
                if(d>previous+0.002f)throw new IllegalStateException("GPU sort validation failed");previous=d;
            }
        } finally {
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,oldCopy);GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,oldStorage);GL15.glDeleteBuffers(testBuffer);
            count=uploaded=0;offloadedParts=oldParts;dispatches=oldDispatches;sortedBatches=oldSorted;
            modelRevision++;
            GpuPlayerPackets.beginBatch();
        }
    }

    public static boolean hasJobs(IntBuffer instances) {
        if ((count == 0&&GpuPlayerPackets.count()==0) || instances.remaining() % STRIDE != 0) return false;
        for (int i = instances.position(); i < instances.limit(); i += STRIDE)
            if (marked(instances.get(i + 1), instances.get(i + 2))) return true;
        return false;
    }

    public static void expand(int instanceBuffer, IntBuffer payload) {
        int instances = payload.remaining() / STRIDE;
        boolean sort = false;
        int gpuParts = 0;
        for (int i=payload.position();i<payload.limit();i+=STRIDE) if(marked(payload.get(i+1),payload.get(i+2))) {
            gpuParts++; sort |= payload.get(i+2)==SORT_MAGIC;
        }
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousGeneric = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int previous0 = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 0);
        int previous1 = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 1);
        int previous2 = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 2);
        long start0=range(previous0,GL43.GL_SHADER_STORAGE_BUFFER_START,0), size0=range(previous0,GL43.GL_SHADER_STORAGE_BUFFER_SIZE,0);
        long start1=range(previous1,GL43.GL_SHADER_STORAGE_BUFFER_START,1), size1=range(previous1,GL43.GL_SHADER_STORAGE_BUFFER_SIZE,1);
        long start2=range(previous2,GL43.GL_SHADER_STORAGE_BUFFER_START,2), size2=range(previous2,GL43.GL_SHADER_STORAGE_BUFFER_SIZE,2);
        int previous3=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,3),previous4=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,4),previous5=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,5);
        int previous6=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,6);
        long start3=range(previous3,GL43.GL_SHADER_STORAGE_BUFFER_START,3),size3=range(previous3,GL43.GL_SHADER_STORAGE_BUFFER_SIZE,3);
        long start4=range(previous4,GL43.GL_SHADER_STORAGE_BUFFER_START,4),size4=range(previous4,GL43.GL_SHADER_STORAGE_BUFFER_SIZE,4);
        long start5=range(previous5,GL43.GL_SHADER_STORAGE_BUFFER_START,5),size5=range(previous5,GL43.GL_SHADER_STORAGE_BUFFER_SIZE,5);
        long start6=range(previous6,GL43.GL_SHADER_STORAGE_BUFFER_START,6),size6=range(previous6,GL43.GL_SHADER_STORAGE_BUFFER_SIZE,6);
        try {
            if (!supported || failed) throw new IllegalStateException("GPU transforms unavailable");
            if (uploaded != count) {
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, input);
                GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, jobs.duplicate().position(0).limit(count * JOB_FLOATS), GL15.GL_STREAM_DRAW);
                uploaded = count;
            }
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, instanceBuffer);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, input);
            GpuPlayerPackets.bind();
            GL20.glUseProgram(program); GL20.glUniform1i(countUniform, instances);
            GL43.glDispatchCompute((instances + 63) / 64, 1, 1);
            GL42.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
            if(GpuResidentPlayerState.enabled)GpuResidentPlayerState.submitted();
            dispatches++;
            if (sort) sortGpu(instanceBuffer, instances);
            offloadedParts += gpuParts;
        } finally {
            GL20.glUseProgram(previousProgram);
            restoreBinding(0,previous0,start0,size0);
            restoreBinding(1,previous1,start1,size1);
            restoreBinding(2,previous2,start2,size2);
            restoreBinding(3,previous3,start3,size3);restoreBinding(4,previous4,start4,size4);restoreBinding(5,previous5,start5,size5);
            restoreBinding(6,previous6,start6,size6);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousGeneric);
        }
    }

    private static long range(int buffer,int name,int binding) {return buffer==0?0:GL32.glGetInteger64i(name,binding);}
    private static void restoreBinding(int index,int buffer,long start,long size) {
        if(buffer==0||size==0)GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,index,buffer);
        else GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER,index,buffer,start,size);
    }

    public static void recover(IntBuffer instances) {
        boolean sort = false;
        for (int i = instances.position(); i < instances.limit(); i += STRIDE) {
            if (!marked(instances.get(i + 1), instances.get(i + 2))) continue;
            sort |= instances.get(i+2)==SORT_MAGIC;
            int job = (int) Float.intBitsToFloat(instances.get(i));
            resolve(job, resolved);
            int cursor = i;
            for (int column = 0; column < 4; column++) for (int row = 0; row < 3; row++)
                instances.put(cursor++, Float.floatToRawIntBits(resolved.get(column, row)));
            cpuRecoveries++;
        }
        if(sort) sortCpu(instances, 0, instances.remaining()/STRIDE-1);
    }

    private static boolean marked(int first, int second) { return first==MAGIC_1 && (second==MAGIC_2 || second==SORT_MAGIC); }

    public static boolean deferSort(int[] data, int instances) {
        if(count==0&&GpuPlayerPackets.count()==0)return false;
        boolean marked=false;
        for(int i=0;i<instances*STRIDE;i+=STRIDE) if(marked(data[i+1],data[i+2])) {marked=true;break;}
        if(!marked)return false;
        if(instances>4096 || failed) {recover(IntBuffer.wrap(data,0,instances*STRIDE)); return false;}
        for(int i=0;i<instances*STRIDE;i+=STRIDE) if(marked(data[i+1],data[i+2])) data[i+2]=SORT_MAGIC;
        return true;
    }

    private static void sortGpu(int instanceBuffer, int instances) {
        int previousRead=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER), previousWrite=GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER);
        try {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,scratch);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER,(long)instances*STRIDE*4,GL15.GL_STREAM_DRAW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,2,scratch);
            GL20.glUseProgram(sortProgram); GL20.glUniform1i(sortCountUniform,instances);
            GL43.glDispatchCompute(1,1,1); GL42.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
            GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,scratch);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,instanceBuffer);
            GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER,GL31.GL_COPY_WRITE_BUFFER,0,0,(long)instances*STRIDE*4);
            GL42.glMemoryBarrier(GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);sortedBatches++;dispatches++;
        } finally {GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,previousRead);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,previousWrite);}
    }

    private static float distance(IntBuffer data,int index) {
        int i=data.position()+index*STRIDE+9;float x=Float.intBitsToFloat(data.get(i)),y=Float.intBitsToFloat(data.get(i+1)),z=Float.intBitsToFloat(data.get(i+2));
        return x*x+y*y+z*z;
    }
    private static void sortCpu(IntBuffer data,int low,int high) {
        if(low>=high)return;int left=low,right=high;float pivot=distance(data,(low+high)>>>1);
        while(left<=right){while(distance(data,left)>pivot)left++;while(distance(data,right)<pivot)right--;
            if(left<=right){int a=data.position()+left++*STRIDE,b=data.position()+right--*STRIDE;for(int i=0;i<STRIDE;i++){int value=data.get(a+i);data.put(a+i,data.get(b+i));data.put(b+i,value);}}}
        if(low<right)sortCpu(data,low,right);if(left<high)sortCpu(data,left,high);
    }

    public static Matrix4f resolve(int job, Matrix4f destination) {
        if(job<0)return GpuPlayerPackets.resolve(-job-1,destination);
        if (job < 0 || job >= count) throw new IllegalArgumentException("Invalid model transform job");
        int i = job * JOB_FLOATS;
        root.set(i, jobs); box.set(i + 24, jobs);
        return destination.set(root).translate(jobs.get(i+16), jobs.get(i+17), jobs.get(i+18))
                .rotateZ(jobs.get(i+22)).rotateY(jobs.get(i+21)).rotateX(jobs.get(i+20)).mul(box);
    }

    public static void disable(Throwable error) {
        failed = true; supported = false; capability = "GPU path disabled; using Argentum";
        LogManager.getLogger("Arcadium/Optimizations").warn("GPU model transforms disabled", error);
    }

    public static String status() {
        if (!BetaOptimizationsConfig.getGpuModelTransforms()) return "GPU model transforms: Off";
        return "GPU model transforms: " + capability + " | Parts: " + offloadedParts + " | Fallback: " + (fallbackParts+cpuRecoveries);
    }

    public static void close() {
        batchOwner = null; batchThread = null; batchEpoch++; modelRevision++;
        if (input != 0) GL15.glDeleteBuffers(input);
        if (program != 0) GL20.glDeleteProgram(program);
        if (scratch != 0) GL15.glDeleteBuffers(scratch);
        if (sortProgram != 0) GL20.glDeleteProgram(sortProgram);
        GpuPlayerPackets.close();
        visibility.clear();players=0;
        input = program = scratch = sortProgram = count = uploaded = 0; checked = supported = failed = false;
    }
}
