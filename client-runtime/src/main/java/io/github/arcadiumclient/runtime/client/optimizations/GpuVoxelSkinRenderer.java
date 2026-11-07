// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.resource.Identifier;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.opengl.*;

public final class GpuVoxelSkinRenderer implements GpuVoxelSkinPipeline.Backend {
    private static final int MAX_BACKENDS = 2, MAX_INSTANCES = 65536, MAX_PROGRAMS=4;
    private static final int MAGIC_1 = Float.floatToRawIntBits(-9876543f), MAGIC_2 = Float.floatToRawIntBits(-8765432f);
    private static final Vector4f WHITE = new Vector4f(1), CLEAR = new Vector4f(0);
    private static GpuVoxelSkinRenderer instance;
    private final GpuVoxelSkinNativeAccess api;
    private final GpuVoxelSkinSelector selector;
    private final GpuVoxelSkinBorrowedBlendState borrowedBlend;
    private final IdentityHashMap<Object, State> owners = new IdentityHashMap<>(), geometries = new IdentityHashMap<>();
    private final Map<Long, Integer> variants = new HashMap<>();
    private final Matrix4f marker = new Matrix4f();
    private final float[] template = new float[192];
    private final GpuVoxelSkinAdmission admission = new GpuVoxelSkinAdmission();
    private final GpuVoxelSkinPreflight preflight=new GpuVoxelSkinPreflight();
    private final ModelPart[] observedParts = new ModelPart[12];
    private Object admissionCapture;
    private boolean observedModel;
    private long optionsRevision = -1;
    private long epoch;
    private int neutralTemplate = -1;
    private String state = "Waiting for voxel skins";
    private boolean failed;

    public static void initialize() throws ReflectiveOperationException {
        if (instance == null) instance = new GpuVoxelSkinRenderer();
        GpuVoxelSkinPipeline.attach(instance);
    }
    private GpuVoxelSkinRenderer() throws ReflectiveOperationException { api = new GpuVoxelSkinNativeAccess();selector=new GpuVoxelSkinSelector(api.capture.select);borrowedBlend=GpuVoxelSkinBorrowedBlendState.live(); }

    private boolean supported() {
        if (failed) return false;
        if(!borrowedBlend.available()){state="Native voxel blend context unavailable; using original layers";return false;}
        if (!GL.getCapabilities().OpenGL43) { state = "OpenGL 4.3 required; using original skin layers"; return false; }
        if (!GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL) || GL11.glIsEnabled(GL11.GL_NORMALIZE))
            return reject("Unsupported voxel normal state", false);
        return GpuVoxelSkinDraw.ready();
    }
    private boolean reject(String message, boolean geometry) {
        GpuVoxelSkinPipeline.fallbacks++;
        if (geometry) GpuVoxelSkinPipeline.geometryFallbacks++; else GpuVoxelSkinPipeline.stateFallbacks++;
        state = message; return false;
    }
    private boolean materialSupported(){
        if(GpuVoxelSkinMaterialPolicy.standardCutout(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_ALPHA_TEST),GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC),GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),GL11.glGetInteger(GL11.GL_DEPTH_FUNC)))return true;
        return reject("Unsupported voxel cutout material; using original layer",false);
    }
    private State prepared(Object main) {
        State known=owners.get(main);
        if(known==null||known.closed||known.failed||!known.batch.accepts(epoch))return null;
        return known;
    }
    @Override public boolean usesMain(Object main){return owners.containsKey(main);}
    @Override public void prepare(Object main){
        try{
            Object actor=api.capture.current.invoke();
            if(actor==null||api.capture.backend.get(actor)!=main)return;
            if(!GL.getCapabilities().OpenGL43){state="OpenGL 4.3 required; using original skin layers";return;}
            if(!borrowedBlend.available()){state="Native voxel blend context unavailable; using original layers";return;}
            if(!GpuVoxelSkinDraw.ready())return;
            State storage=owners.get(main);
            if(storage==null){
                if(owners.size()==MAX_BACKENDS){GpuVoxelSkinPipeline.budgetFallbacks++;return;}
                Object auxiliary=api.modelConstructor.invoke();
                if(!GpuVoxelSkinPipeline.registerAuxiliary(main,auxiliary)){GpuVoxelSkinPipeline.budgetFallbacks++;return;}
                try{storage=new State(main,api.geometry.invoke(),auxiliary);owners.put(main,storage);geometries.put(storage.geometry,storage);}
                catch(Throwable failure){GpuVoxelSkinPipeline.unregisterAuxiliary(auxiliary);throw failure;}
            }
            if(storage.closed||storage.failed)return;
            if((boolean)api.modelActive.invoke(storage.auxiliary))api.modelDiscard.invoke(storage.auxiliary);
            storage.batch.discard();
            boolean began;
            if(GpuVoxelSkinPhysicalProjection.ENABLED){GpuVoxelSkinPhysicalProjection.beginCompile();try{began=(boolean)api.modelBegin.invoke(storage.auxiliary);}finally{GpuVoxelSkinPhysicalProjection.endCompile();}}
            else began=(boolean)api.modelBegin.invoke(storage.auxiliary);
            if(!began){storage.failed=true;state="Native auxiliary renderer unavailable; using original voxels";return;}
            if(((Map<?,?>)api.modelPrograms.get(storage.auxiliary)).size()>MAX_PROGRAMS){
                closeStateManaged(storage);storage.failed=true;state="Voxel shader budget; using original voxels";GpuVoxelSkinPipeline.budgetFallbacks++;return;
            }
            storage.batch.prepare(epoch);GpuVoxelSkinPipeline.auxiliaryPreparations++;
            if(storage.projection!=null){
                boolean owned=api.acquireManagedScope(RenderDevice.INSTANCE,RenderDevice::enterManagedCode);
                try(var commands=RenderDevice.INSTANCE.createCommandList()){
                    if(storage.projection.prepare(commands,(Map<?,?>)api.modelPrograms.get(storage.auxiliary),epoch,Integer.toUnsignedLong((int)api.modelFrame.get(main))))storage.draw.preparePhysical(commands);
                }finally{GpuVoxelSkinNativeAccess.releaseManagedScope(owned,RenderDevice::exitManagedCode);}
            }
        }catch(Throwable failure){fail(failure);}
    }

    @Override public int queue(ModelPart[] parts, int mask, Identifier texture, int sourceJob, int sourceTemplate, int light, float age) {
        if(GpuVoxelSkinPhysicalProjection.ENABLED)return 0;
        if (parts.length != 12 || mask == 0) return 0;
        int queued = 0, variant = -1;
        try {
            Object actor = api.capture.current.invoke();
            if (actor == null || !(boolean)api.capture.active.invoke(actor)) return 0;
            Object backend = api.capture.backend.get(actor); State storage = prepared(backend);
            if (storage == null||!supported()||!materialSupported()) return 0;
            Object fact=preflight.fact();int templateId=preflight.consume(backend,parts,mask,texture,sourceTemplate,epoch);
            if(templateId<0||fact!=admission.admittedUpload()){reject("Analytic voxel preflight was not current; using original layers",false);return 0;}
            admissionCapture=api.capture.activeCapture.get(actor);
            System.arraycopy(parts,0,observedParts,0,12);observedModel=true;
            for (int part = 0; part < 12; part++) {
                if ((mask & 1 << part) == 0) continue;
                Object mesh = GpuVoxelSkinBridge.mesh(parts[part]), rawOffset = GpuVoxelSkinBridge.rawOffset(parts[part]);
                if (mesh == null || rawOffset == null || !standardOffset(part, rawOffset)) { reject("Custom voxel offset; using original layer", true); continue; }
                GpuVoxelSkinMath.Offset offset = GpuVoxelSkinBridge.offset(rawOffset);
                if (offset == null) { reject("Unsupported voxel offset", true); continue; }
                GpuVoxelSkinGeometryCache.Entry geometry;
                try { geometry = storage.mesh(mesh, .0625f, offset); }
                catch (IllegalArgumentException unsupported) { reject("Unsupported immutable voxel geometry", true); continue; }
                if (geometry == null) { GpuVoxelSkinPipeline.budgetFallbacks++; continue; }
                if (geometry.vertexCount() == 0) { queued |= 1 << part; continue; }
                if (variant < 0) variant = GpuPlayerPackets.addVariant(sourceJob, templateId);
                if (variant < 0) { GpuVoxelSkinPipeline.budgetFallbacks++; break; }
                marker.identity().m00(-(variant * 16 + part + 1)).m01(Float.intBitsToFloat(MAGIC_1)).m02(Float.intBitsToFloat(MAGIC_2));
                if (submit(storage, geometry, backend, texture, api.normalPass, marker, light, WHITE, age, CLEAR, null,0)) {
                    queued |= 1 << part; GpuVoxelSkinPipeline.analyticParts++; GpuVoxelSkinPipeline.analyticPartMask |= 1 << part;
                }
            }
            if (queued == 0 && variant >= 0) GpuPlayerPackets.rollback(variant);
            if (queued != 0) state = "Batched voxel skins active";
            return queued;
        } catch (IllegalArgumentException unsupported) {
            if (queued == 0 && variant >= 0) GpuPlayerPackets.rollback(variant);
            GpuVoxelSkinPipeline.budgetFallbacks++; state = "Voxel template/geometry budget; using original layer";
            return queued;
        } catch (Throwable error) {
            if (queued == 0 && variant >= 0) GpuPlayerPackets.rollback(variant);
            fail(error); return queued;
        }
    }
    @Override public boolean preflight(Object main,ModelPart[] parts,int mask,Identifier texture,int sourceTemplate){
        if(GpuVoxelSkinPhysicalProjection.ENABLED)return false;
        preflight.clear();admission.beginActor();
        try{
            State storage=prepared(main);if(storage==null||!GL.getCapabilities().OpenGL43||!GpuVoxelSkinDraw.ready())return false;
            if(GL11.glIsEnabled(GL11.GL_NORMALIZE)||!GpuVoxelSkinMaterialPolicy.standardCutout(
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),GL11.glIsEnabled(GL11.GL_BLEND),true,
                    GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC),GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF),GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),GL11.glGetInteger(GL11.GL_DEPTH_FUNC)))return reject("Native outer skin material retains original callbacks",false);
            if(!admission.admit(api,main,texture,parts,mask)){state=admission.reason();return false;}
            if(!prepareTexture(storage,main,texture,admission.admittedUpload()))return false;
            int variantTemplate=variantTemplate(sourceTemplate,parts),nonempty=0;
            for(int part=6;part<12;part++)if((mask&1<<part)!=0){
                Object mesh=GpuVoxelSkinBridge.mesh(parts[part]),raw=GpuVoxelSkinBridge.rawOffset(parts[part]);
                if(mesh==null||raw==null||!standardOffset(part,raw))return reject("Native outer skin attachment retains original callbacks",true);
                var offset=GpuVoxelSkinBridge.offset(raw);if(offset==null)return reject("Native outer skin offset retains original callbacks",true);
                var geometry=storage.mesh(mesh,.0625f,offset);if(geometry==null){GpuVoxelSkinPipeline.budgetFallbacks++;return false;}
                if(geometry.vertexCount()!=0)nonempty++;
            }
            Object batch=selector.select(storage.auxiliary,texture,api.normalPass),instances=api.capture.instances.invoke(storage.geometry,batch);
            if(!GpuVoxelSkinPreflight.hasRoom(GpuPlayerPackets.count(),GpuPlayerPackets.MAX_PLAYERS,(int)api.capture.count.get(instances),nonempty)){
                GpuVoxelSkinPipeline.budgetFallbacks++;state="Voxel job budget retains original callbacks";return false;
            }
            preflight.arm(main,parts,mask,texture,sourceTemplate,variantTemplate,epoch,admission.admittedUpload());return true;
        }catch(IllegalArgumentException budget){GpuVoxelSkinPipeline.budgetFallbacks++;state="Voxel preflight budget retains original callbacks";return false;}
        catch(Throwable unsupported){GpuVoxelSkinPipeline.admissionFailure=unsupported.toString();return reject("Voxel preflight unavailable; retaining original callbacks",false);}
    }

    private int variantTemplate(int source, ModelPart[] parts) throws Throwable {
        long revision = GpuVoxelSkinBridge.revision();
        if (revision != optionsRevision) { variants.clear(); optionsRevision = revision; }
        long key = (long) source << 1;
        boolean slim = false;
        for (int part = 9; part <= 10; part++) {
            Object raw = GpuVoxelSkinBridge.rawOffset(parts[part]);
            if (raw != null) { var p = GpuVoxelSkinBridge.parameters(raw); slim |= p != null && p.shape().equals("SLIM_ARM"); }
        }
        if (slim) key |= 1;
        Integer known = variants.get(key); if (known != null) return known;
        GpuPlayerPackets.copyTemplate(source, template);
        var options = GpuVoxelSkinBridge.options();
        for (int part = 6; part < 12; part++) {
            String shape = part == 6 ? "HEAD" : part == 7 || part == 8 ? "LEG" : part == 11 ? "BODY" : slim ? "SLIM_ARM" : "ARM";
            GpuVoxelSkinMath.applyTemplate(template, part, GpuVoxelSkinMath.offset(shape, part == 10, false, false, options));
        }
        int result = GpuPlayerPackets.registerTemplate(template); variants.put(key, result); return result;
    }

    private static boolean standardOffset(int part, Object raw) {
        var p = GpuVoxelSkinBridge.parameters(raw);
        if (p == null || p.firstPerson() || p.skull()) return false;
        return switch (part) {
            case 6 -> p.shape().equals("HEAD");
            case 7, 8 -> p.shape().equals("LEG");
            case 9, 10 -> (p.shape().equals("ARM") || p.shape().equals("SLIM_ARM")) && p.mirrored() == (part == 10);
            case 11 -> p.shape().equals("BODY");
            default -> false;
        };
    }

    @Override public boolean capture(Object mesh, float scale) {
        if (scale != .0625f) return false;
        int job = -1;
        State projectionState=null;long projectionTicket=0;
        try {
            Object actor = api.capture.current.invoke();
            if (actor == null || !(boolean)api.capture.active.invoke(actor)) return false;
            Object capture = api.capture.activeCapture.get(actor);
            if (capture == null || !(boolean)api.capturedPlayer.invoke(capture) || !(boolean)api.capture.captureActive.get(capture)
                    || (boolean)api.capture.captureFinished.get(capture) || (boolean)api.capture.captureArmor.get(capture)
                    || (int)api.capture.captureMode.get(capture) != GL11.GL_MODELVIEW || GpuVoxelSkinBridge.excludedMesh(mesh)) return false;
            Object backend = api.capture.backend.get(actor); State storage = prepared(backend);
            if(storage==null||!supported())return false;
            if(GpuVoxelSkinPhysicalProjection.ENABLED){
                if(storage==null||storage.projection==null||!storage.projection.ready())return false;
                projectionState=storage;projectionTicket=storage.projection.capture(mesh,GpuVoxelSkinBridge.part(mesh),scale);
                if(projectionTicket==0){GpuVoxelSkinPhysicalProjection.declines++;return false;}
            }
            preflight.clear();
            if(!materialSupported())return false;
            GpuVoxelSkinMath.Offset offset = GpuVoxelSkinBridge.offsetForMesh(mesh);
            if (offset == null) return reject("Voxel offset was not observed in the player binder", true);
            Object pass = api.capture.capturePass.get(capture); String kind = ((Enum<?>)pass).name();
            if (!kind.equals("NORMAL") && !kind.equals("TRANSLUCENT")) return reject("Unsupported native voxel material pass", false);
            Identifier texture = (Identifier)api.capture.captureTexture.get(capture);
            if(admissionCapture!=capture){admission.beginActor();admissionCapture=capture;}
            int capturedPart=GpuVoxelSkinBridge.part(mesh);
            if(!observedModel||capturedPart<6||!admission.admit(api,backend,texture,observedParts,1<<capturedPart)){
                state=observedModel?admission.reason():"Outer skin model unavailable; using original layers";return false;
            }
            if(!prepareTexture(storage,backend,texture,admission.admittedUpload()))return false;
            Matrix4f matrix = (Matrix4f)api.capture.captureMatrices.get(capture);
            if (texture == null || !finite(matrix) || !GpuVoxelSkinMath.normalTransformMatches(matrix, offset))
                return reject("Custom voxel matrix or normal scale", false);
            var geometry = storage.mesh(mesh, scale, offset);
            if (geometry == null) { GpuVoxelSkinPipeline.budgetFallbacks++; return false; }
            if (geometry.vertexCount() == 0) return true;
            if(storage.projection!=null){
                boolean owned=api.acquireManagedScope(RenderDevice.INSTANCE,RenderDevice::enterManagedCode);
                boolean warm;
                try(var commands=RenderDevice.INSTANCE.createCommandList()){warm=storage.draw.warmPhysicalGeometry(commands);}
                finally{GpuVoxelSkinNativeAccess.releaseManagedScope(owned,RenderDevice::exitManagedCode);}
                if(!warm)return false;
            }
            Matrix4f submitted = matrix;
            if (storage.projection==null&&GpuModelTransforms.ready()) {
                if (neutralTemplate < 0) {
                    float[] data = new float[192];
                    for (int part = 0; part < 12; part++) data[part*16+12] = data[part*16+13] = data[part*16+14] = 1;
                    neutralTemplate = GpuPlayerPackets.registerTemplate(data);
                }
                job = GpuPlayerPackets.addPose(matrix, neutralTemplate);
                if (job < 0) { GpuVoxelSkinPipeline.budgetFallbacks++; return false; }
                GpuPlayerPackets.writePose(job, 0, 0, 0, 0, 0, 0, 0);
                submitted = marker.identity().m00(-(job*16+1)).m01(Float.intBitsToFloat(MAGIC_1)).m02(Float.intBitsToFloat(MAGIC_2));
            }
            boolean result = submit(storage, geometry, backend, texture, api.normalPass, submitted, (int)api.capture.captureLight.get(capture),
                    (Vector4fc)api.capture.captureColor.get(capture), (float)api.capture.captureTime.get(capture),
                    (Vector4fc)api.capture.captureOverlay.get(capture), capture,projectionTicket);
            if (!result && job >= 0) GpuPlayerPackets.rollback(job);
            if (result) {
                GpuVoxelSkinPipeline.nativeParts++;
                state = "Batched voxel skins active";
                int part = GpuVoxelSkinBridge.part(mesh);
                if (part >= 0) GpuVoxelSkinPipeline.nativePartMask |= 1 << part;
            }
            return result;
        } catch (IllegalArgumentException unsupported) {
            if (job >= 0) GpuPlayerPackets.rollback(job);
            return reject("Unsupported voxel template or immutable geometry", true);
        } catch (Throwable error) {
            if (job >= 0) GpuPlayerPackets.rollback(job);
            fail(error); return false;
        } finally {if(projectionState!=null)projectionState.projection.cancel(projectionTicket);}
    }

    private boolean submit(State storage, GpuVoxelSkinGeometryCache.Entry geometry, Object backend, Identifier texture, Object pass,
                           Matrix4f matrix, int light, Vector4fc color, float time, Vector4fc overlay, Object capture,long projectionTicket) throws Throwable {
        backend=storage.auxiliary;
        Object batch = selector.select(backend, texture, pass);
        Object instances = api.capture.instances.invoke(storage.geometry, batch);
        api.prioritize(batch, storage.geometry, instances);
        if ((int)api.capture.count.get(instances) >= MAX_INSTANCES) { GpuVoxelSkinPipeline.budgetFallbacks++; return false; }
        int oldSize = (int)api.capture.size.get(instances), oldCount = (int)api.capture.count.get(instances);
        int oldBatch = (int)api.capture.batchCount.get(batch), oldBackend = (int)api.capture.backendCount.get(backend);
        boolean oldRecorded=storage.projection!=null&&capture!=null&&(boolean)api.capture.captureRecorded.get(capture);
        if(storage.projection!=null&&!storage.projection.reserve(projectionTicket,instances,oldCount)){GpuVoxelSkinPhysicalProjection.declines++;return false;}
        try {
            if (!(boolean)api.submit.invoke(backend, storage.geometry, texture, pass, matrix, light, color, time, overlay, null)) return false;
            int[] values = (int[])api.capture.data.get(instances);
            int size = (int)api.capture.size.get(instances);
            if (size != oldSize + 19) throw new IllegalStateException("Unknown native voxel instance layout");
            values[size - 3] = geometry.firstVertex(); values[size - 2] = geometry.vertexCount(); values[size - 1] = 0;
            if (capture != null) api.capture.captureRecorded.set(capture, true);
            GpuVoxelSkinPipeline.queuedVertices += geometry.vertexCount();
            storage.batch.queued(epoch);
            if(storage.projection!=null&&!storage.projection.commit(projectionTicket,(int)api.capture.count.get(instances)))throw new IllegalStateException("Physical voxel row append mismatch");
            return true;
        } catch (Throwable failure) {
            api.capture.size.set(instances, oldSize); api.capture.count.set(instances, oldCount);
            api.capture.batchCount.set(batch, oldBatch); api.capture.backendCount.set(backend, oldBackend);
            if(storage.projection!=null&&capture!=null)api.capture.captureRecorded.set(capture,oldRecorded);
            throw failure;
        }
    }
    private boolean prepareTexture(State storage,Object main,Identifier texture,Object fact)throws Throwable{
        var source=Minecraft.getInstance().getTextureManager().get(texture);if(source==null)return reject("Skin source became unavailable",false);
        int name=source.getGlId(),generation=api.sourceGeneration(source);Object key=api.sourcePoolKey(main,texture,source,name,generation);
        if(key==null||fact!=GpuVoxelSkinUploadFacts.current(source,name,generation))return reject("Skin upload changed before auxiliary preparation",false);
        var descriptor=storage.descriptors.get(key);
        if(descriptor==null){descriptor=api.sourceDescriptor(main,texture,source,name);if(descriptor==null)return reject("Skin source pool is unavailable",false);}
        long previousBytes=storage.textureBudget.bytes();
        if(!storage.textureBudget.admit(source,texture,descriptor)){GpuVoxelSkinPipeline.budgetFallbacks++;state="Voxel texture budget; using original layers";return false;}
        storage.descriptors.putIfAbsent(key,descriptor);GpuVoxelSkinPipeline.auxiliaryTextureBytes+=storage.textureBudget.bytes()-previousBytes;
        selector.select(storage.auxiliary,texture,api.normalPass);
        if(!api.sourceCopyMatches(storage.auxiliary,texture,source,name,generation,fact))return reject("Auxiliary skin array has no current upload copy",false);
        return true;
    }

    @Override public void observe(Object model) {
        try {
            GpuVoxelSkinBridge.observeModel(model);admission.beginActor();admissionCapture=null;observedModel=model instanceof PlayerModel;
            preflight.clear();
            Arrays.fill(observedParts,null);
            if(model instanceof PlayerModel player){observedParts[6]=player.hat;observedParts[7]=player.leftPants;observedParts[8]=player.rightPants;
                observedParts[9]=player.leftSleeve;observedParts[10]=player.rightSleeve;observedParts[11]=player.jacket;}
        }
        catch (Throwable error) { fail(error); }
    }
    @Override public void dispose(Object mesh) {
        GpuVoxelSkinBridge.dispose(mesh);
        admission.dispose(mesh);
        for (State storage : owners.values()) storage.cache.dispose(mesh);
    }
    @Override public void beginBatch() {
        try {
            epoch++;
            if(GpuVoxelSkinPhysicalProjection.ENABLED)GpuVoxelSkinPhysicalProjection.beginFrame();
            preflight.clear();
            GpuVoxelSkinBridge.refreshOptions();
            for (State storage : owners.values()) {
                if(storage.projection!=null)storage.projection.beginFrame();
                storage.batch.discard();storage.cache.beginBatch();
                if(!storage.closed&&(boolean)api.modelActive.invoke(storage.auxiliary))api.modelDiscard.invoke(storage.auxiliary);
            }
        } catch (Throwable error) { fail(error); }
    }
    @Override public void flush(Object main,Object commandList,boolean beforeShadows){
        if(commandList==null)return;
        for(var entry:owners.entrySet()){
            if(main!=null&&entry.getKey()!=main)continue;
            State storage=entry.getValue();
            if(storage.closed)continue;
            int pending=storage.batch.claim(epoch);if(pending<0)continue;
            try{
                if(pending==0){api.modelDiscard.invoke(storage.auxiliary);continue;}
                int mainCount=(int)api.capture.backendCount.get(entry.getKey()),jobs=GpuPlayerPackets.count();
                long blendTicket=borrowedBlend.capture();Throwable flushFailure=null;
                try{api.modelFlush.invoke(storage.auxiliary,(CommandList)commandList);}
                catch(Throwable failure){flushFailure=failure;throw failure;}
                finally{try{borrowedBlend.restore(blendTicket);}catch(Throwable restoreFailure){if(flushFailure!=null)flushFailure.addSuppressed(restoreFailure);else throw restoreFailure;}}
                if(mainCount!=(int)api.capture.backendCount.get(entry.getKey())||jobs!=GpuPlayerPackets.count())
                    throw new IllegalStateException("Auxiliary flush changed the main actor batch");
                GpuVoxelSkinPipeline.auxiliaryBatches++;
                if(beforeShadows)GpuVoxelSkinPipeline.preShadowFlushes++;else GpuVoxelSkinPipeline.fallbackFlushes++;
                if(((Map<?,?>)api.modelPrograms.get(storage.auxiliary)).size()>MAX_PROGRAMS){storage.failed=true;storage.close((CommandList)commandList);state="Voxel shader budget; using original voxels";GpuVoxelSkinPipeline.budgetFallbacks++;}
            }catch(Throwable failure){storage.failed=true;fail(failure);}
        }
    }
    @Override public void draw(Object geometry, Object commandList, Object instances) {
        State storage = geometries.get(geometry);
        if (storage == null) return;
        try { storage.draw.physicalInstances(instances);storage.draw.draw((CommandList)commandList, (IntBuffer)api.instanceUpload.invoke(instances), (int)api.capture.count.get(instances)); }
        catch (Throwable failure) { fail(failure); }
    }
    @Override public void delete(Object geometry, Object commandList) {
        State storage = geometries.get(geometry);
        if (storage != null) storage.draw.delete((CommandList)commandList);
    }
    @Override public void reset() {
        if(GpuVoxelSkinPhysicalProjection.ENABLED)GpuVoxelSkinPhysicalProjection.resetScopes();
        if (!owners.isEmpty()) {
            boolean owned = api.acquireManagedScope(RenderDevice.INSTANCE, RenderDevice::enterManagedCode);
            try (var commands = RenderDevice.INSTANCE.createCommandList()) {
                for (State storage : owners.values()) storage.close(commands);
            } finally { GpuVoxelSkinNativeAccess.releaseManagedScope(owned, RenderDevice::exitManagedCode); }
        }
        owners.clear(); geometries.clear(); variants.clear(); neutralTemplate = -1; optionsRevision = -1;
        admission.reset();Arrays.fill(observedParts,null);admissionCapture=null;observedModel=false;
        preflight.clear();
        GpuVoxelSkinBridge.reset(); GpuVoxelSkinDraw.resetProgram(); failed = false; state = "Waiting for voxel skins";
    }
    @Override public String status() {
        long words = 0, hits = 0, budget = 0;
        for (State storage : owners.values()) { words += storage.cache.sizeWords(); hits += storage.cache.hits; budget += storage.cache.budgetFallbacks; }
        return state + " | Atlas bytes: " + words*4 + " | Mesh reuse: " + hits + " | Texture budget bytes: " + GpuVoxelSkinPipeline.auxiliaryTextureBytes
                + " | Pre-shadow/fallback flushes: "+GpuVoxelSkinPipeline.preShadowFlushes+"/"+GpuVoxelSkinPipeline.fallbackFlushes+" | Budget fallback: " + budget
                + " | Opaque admission sources/meshes: "+GpuVoxelSkinPipeline.admissionSources+"/"+GpuVoxelSkinPipeline.admissionMeshes
                + " | Binary/fractional/unknown: "+GpuVoxelSkinPipeline.binaryAlphaFallbacks+"/"+GpuVoxelSkinPipeline.fractionalAlphaFallbacks+"/"+GpuVoxelSkinPipeline.unknownImageFallbacks
                + " | Upload alpha bytes/copy pools: "+GpuVoxelSkinUploadFacts.retainedAlphaBytes()+"/"+GpuVoxelSkinUploadFacts.retainedPools();
    }
    private void fail(Throwable error) { failed = true; state = "Disabled after error; using original voxels"; GpuVoxelSkinPipeline.fail(error); }
    private static boolean finite(Matrix4fc matrix) {
        for (int c=0;c<4;c++) for (int r=0;r<4;r++) if (!Float.isFinite(matrix.get(c,r))) return false;
        return matrix.m03()==0 && matrix.m13()==0 && matrix.m23()==0 && matrix.m33()==1;
    }
    private void closeStateManaged(State storage){
        boolean owned=api.acquireManagedScope(RenderDevice.INSTANCE,RenderDevice::enterManagedCode);
        try(var commands=RenderDevice.INSTANCE.createCommandList()){storage.close(commands);}
        finally{GpuVoxelSkinNativeAccess.releaseManagedScope(owned,RenderDevice::exitManagedCode);}
    }

    private final class State {
        final Object geometry;
        final Object auxiliary;
        final GpuVoxelSkinGeometryCache cache = GpuVoxelSkinPhysicalProjection.ENABLED?new GpuVoxelSkinGeometryCache(GpuVoxelSkinDraw.PHYSICAL_VERTEX_WORDS,GpuVoxelSkinGeometryCache.DEFAULT_MESH_BUDGET):new GpuVoxelSkinGeometryCache();
        final GpuVoxelSkinTextureBudget textureBudget=new GpuVoxelSkinTextureBudget();
        final Map<Object,GpuVoxelSkinTextureBudget.Descriptor> descriptors=new HashMap<>();
        final GpuVoxelSkinBatchState batch=new GpuVoxelSkinBatchState();
        final GpuVoxelSkinDraw draw;
        final GpuVoxelSkinProjectionStore projection;
        boolean failed,closed;
        State(Object main,Object geometry,Object auxiliary) { this.geometry = geometry;this.auxiliary=auxiliary;projection=GpuVoxelSkinPhysicalProjection.ENABLED?new GpuVoxelSkinProjectionStore(main):null;draw = new GpuVoxelSkinDraw(api, cache,projection); }
        void close(CommandList commands){
            if(closed)return;closed=true;batch.discard();
            try{try{draw.delete(commands);}finally{api.modelClose.invoke(auxiliary,commands);}}
            catch(Throwable failure){fail(failure);}
            finally{
                GpuVoxelSkinPipeline.auxiliaryTextureBytes-=textureBudget.bytes();textureBudget.reset();descriptors.clear();cache.reset();
                GpuVoxelSkinPipeline.unregisterAuxiliary(auxiliary);
            }
        }
        GpuVoxelSkinGeometryCache.Entry mesh(Object mesh, float scale, GpuVoxelSkinMath.Offset offset) {
            long hits = cache.hits, misses = cache.misses;
            var entry = cache.mesh(mesh, GpuVoxelSkinBridge.quads(mesh), scale, offset);
            GpuVoxelSkinPipeline.meshHits += cache.hits - hits; GpuVoxelSkinPipeline.meshesBuilt += cache.misses - misses;
            return entry;
        }
    }
    public static boolean preparedProjectionQualified(){return false;}
    public static PreparedVoxelLease073 preflightPrepared(Object main,Object capture,ModelPart[] parts,int mask,Identifier texture,int sourceTemplate){
        GpuVoxelSkinRenderer renderer=instance;if(renderer==null||Integer.bitCount(mask)!=6||!renderer.preflight(main,parts,mask,texture,sourceTemplate))return null;
        try{
            State storage=renderer.prepared(main);if(storage==null)return null;Object fact=renderer.preflight.fact();int variant=renderer.preflight.consume(main,parts,mask,texture,sourceTemplate,renderer.epoch);if(variant<0||fact!=renderer.admission.admittedUpload())return null;
            Object[] meshes=new Object[6],geometry=new Object[6];int at=0;for(int part=6;part<12;part++)if((mask&1<<part)!=0){Object mesh=GpuVoxelSkinBridge.mesh(parts[part]),raw=GpuVoxelSkinBridge.rawOffset(parts[part]);if(mesh==null||raw==null||!standardOffset(part,raw))return null;var offset=GpuVoxelSkinBridge.offset(raw);var entry=storage.mesh(mesh,.0625f,offset);if(entry==null)return null;meshes[at]=mesh;geometry[at++]=entry;}
            // Existing analytic projection is not the qualified physical-leaf MVP path.
            return new PreparedVoxelLease073(main,capture,parts,texture,fact,meshes,geometry,mask,sourceTemplate,variant,renderer.epoch,false);
        }catch(Throwable unavailable){return null;}
    }
    public static int queuePreparedPose(PreparedVoxelLease073 lease,Object capture,ModelPart[] parts,Identifier texture,int sourceJob,int light,float age){
        GpuVoxelSkinRenderer renderer=instance;if(renderer==null||lease==null||sourceJob<0||!lease.consume(lease.main,capture,parts,texture,lease.sourceTemplate,renderer.epoch))return 0;
        if(lease.fact!=renderer.admission.admittedUpload())return 0;
        renderer.preflight.arm(lease.main,parts,lease.mask,texture,lease.sourceTemplate,lease.variantTemplate,renderer.epoch,lease.fact);
        return renderer.queue(parts,lease.mask,texture,sourceJob,lease.sourceTemplate,light,age);
    }

}
