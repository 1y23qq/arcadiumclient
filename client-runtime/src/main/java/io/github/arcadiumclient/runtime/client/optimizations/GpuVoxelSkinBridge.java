// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.PlayerModel;

final class GpuVoxelSkinBridge {
    record Attachment(Object mesh, Object offset) {}
    record Parameters(String shape, boolean mirrored, boolean firstPerson, boolean skull) {}
    private record CachedOffset(long revision, GpuVoxelSkinMath.Offset value) {}
    private static final ClassValue<VarHandle[]> PARTS = new ClassValue<>() {
        @Override protected VarHandle[] computeValue(Class<?> type) {
            try { return new VarHandle[]{field(type, "skinLayers$mesh"), field(type, "skinLayers$offset")}; }
            catch (ReflectiveOperationException ignored) { return new VarHandle[0]; }
        }
    };
    private static final ClassValue<VarHandle> QUADS = new ClassValue<>() {
        @Override protected VarHandle computeValue(Class<?> type) {
            try { return field(type, "quads"); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Unknown voxel mesh layout", error); }
        }
    };
    private static final ClassValue<VarHandle[]> OFFSET = new ClassValue<>() {
        @Override protected VarHandle[] computeValue(Class<?> type) {
            try { return new VarHandle[]{field(type, "shape"), field(type, "mirrored"), field(type, "firstPerson"), field(type, "skull")}; }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Unknown voxel offset layout", error); }
        }
    };
    private static final IdentityHashMap<Object, Parameters> parameters = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, CachedOffset> offsets = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, Object> meshOffsets = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, Integer> meshParts = new IdentityHashMap<>();
    private static final ClassValue<MethodHandle[]> OPTIONS = new ClassValue<>() {
        @Override protected MethodHandle[] computeValue(Class<?> type) {
            String[] names = {"BaseVoxelSize", "BodyVoxelWidthSize", "HeadVoxelSize", "FirstPersonPixelScaling", "SkullVoxelSize"};
            MethodHandle[] getters = new MethodHandle[names.length];
            try {
                for (int index = 0; index < names.length; index++) {
                    var getter = type.getMethod("get" + names[index]);
                    if (getter.getReturnType() != float.class) throw new NoSuchMethodException("Unsupported voxel scale getter: " + getter);
                    getters[index] = MethodHandles.publicLookup().unreflect(getter);
                }
                return getters;
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unknown voxel configuration layout", failure); }
        }
    };
    private static final ClassValue<MethodHandle[]> ENABLED = new ClassValue<>() {
        @Override protected MethodHandle[] computeValue(Class<?> type) {
            String[] names={"Hat","LeftPants","RightPants","LeftSleeve","RightSleeve","Jacket"};
            MethodHandle[] getters=new MethodHandle[names.length];
            try{for(int index=0;index<names.length;index++){
                var getter=type.getMethod("getEnable"+names[index]);
                if(getter.getReturnType()!=boolean.class)throw new NoSuchMethodException("Unknown voxel part toggle: "+getter);
                getters[index]=MethodHandles.publicLookup().unreflect(getter);
            }return getters;}catch(ReflectiveOperationException failure){throw new IllegalStateException("Unknown voxel part configuration",failure);}
        }
    };
    private static MethodHandle configuration;
    private static GpuVoxelSkinMath.Options current;
    private static long revision;
    private static int enabledMask;
    private GpuVoxelSkinBridge() {}

    static GpuVoxelSkinMath.Options refreshOptions() throws Throwable {
        if (configuration == null) {
            Class<?> mod = Class.forName("org.polyfrost.skinlayers.SkinLayers");
            Object instance = mod.getField("INSTANCE").get(null);
            configuration = MethodHandles.publicLookup().unreflect(mod.getMethod("getConfig")).bindTo(instance);
        }
        Object config=configuration.invoke();
        GpuVoxelSkinMath.Options value = readOptions(config);enabledMask=readEnabledMask(config);
        if (!value.equals(current)) { current = value; revision++; }
        return current;
    }

    static GpuVoxelSkinMath.Options readOptions(Object config) throws Throwable {
        MethodHandle[] getters = OPTIONS.get(config.getClass());
        return new GpuVoxelSkinMath.Options((float)getters[0].invoke(config), (float)getters[1].invoke(config),
                (float)getters[2].invoke(config), (float)getters[3].invoke(config), (float)getters[4].invoke(config));
    }

    static GpuVoxelSkinMath.Options options() throws Throwable { return current == null ? refreshOptions() : current; }
    static int enabledMask() throws Throwable { if(current==null)refreshOptions();return enabledMask; }
    static int readEnabledMask(Object config)throws Throwable{
        int mask=0;MethodHandle[] getters=ENABLED.get(config.getClass());
        for(int index=0;index<getters.length;index++)if((boolean)getters[index].invoke(config))mask|=1<<(6+index);
        return mask;
    }

    static long revision() { return revision; }
    static Object mesh(ModelPart part) {
        VarHandle[] fields = PARTS.get(part.getClass()); return fields.length == 0 ? null : fields[0].get(part);
    }
    static Object rawOffset(ModelPart part) {
        VarHandle[] fields = PARTS.get(part.getClass()); return fields.length == 0 ? null : fields[1].get(part);
    }
    static Attachment attachment(ModelPart part) {
        VarHandle[] fields = PARTS.get(part.getClass());
        if (fields.length == 0) return null;
        Object mesh = fields[0].get(part), offset = fields[1].get(part);
        if (mesh == null || offset == null) return null;
        observe(mesh, offset);
        return new Attachment(mesh, offset);
    }

    static void observeModel(Object value) {
        if (!(value instanceof PlayerModel model)) return;
        observePart(model.hat, 6); observePart(model.jacket, 11); observePart(model.leftSleeve, 9);
        observePart(model.rightSleeve, 10); observePart(model.leftPants, 7); observePart(model.rightPants, 8);
    }
    private static void observePart(ModelPart part, int index) {
        VarHandle[] fields = PARTS.get(part.getClass());
        if (fields.length == 0) return;
        Object mesh = fields[0].get(part), offset = fields[1].get(part);
        if (mesh != null && offset != null) { observe(mesh, offset); if (meshOffsets.containsKey(mesh)) meshParts.put(mesh, index); }
    }
    private static void observe(Object mesh, Object offset) {
        if (meshOffsets.containsKey(mesh) || meshOffsets.size() < GpuVoxelSkinGeometryCache.DEFAULT_MESH_BUDGET) meshOffsets.put(mesh, offset);
    }

    static Parameters parameters(Object offset) {
        Parameters known = parameters.get(offset);
        if (known != null) return known;
        if (!offset.getClass().getName().equals("org.polyfrost.skinlayers.render.MeshOffset") || parameters.size() >= 32) return null;
        VarHandle[] fields = OFFSET.get(offset.getClass());
        Object shape = fields[0].get(offset);
        if (!(shape instanceof Enum<?> kind)) return null;
        known = new Parameters(kind.name(), (boolean)fields[1].get(offset), (boolean)fields[2].get(offset), (boolean)fields[3].get(offset));
        parameters.put(offset, known); return known;
    }
    static GpuVoxelSkinMath.Offset offset(Object raw) throws Throwable {
        Parameters p = parameters(raw);
        if (p == null || p.firstPerson || p.skull) return null;
        GpuVoxelSkinMath.Options value = options();
        CachedOffset known = offsets.get(raw);
        if (known != null && known.revision == revision) return known.value;
        GpuVoxelSkinMath.Offset result = GpuVoxelSkinMath.offset(p.shape, p.mirrored, false, false, value);
        offsets.put(raw, new CachedOffset(revision, result)); return result;
    }
    static GpuVoxelSkinMath.Offset offsetForMesh(Object mesh) throws Throwable {
        Object offset = meshOffsets.get(mesh); return offset == null ? null : offset(offset);
    }
    static boolean excludedMesh(Object mesh) {
        Object offset=meshOffsets.get(mesh);Parameters value=offset==null?null:parameters(offset);
        return value!=null&&(value.firstPerson()||value.skull());
    }
    static float[] quads(Object mesh) {
        if (!mesh.getClass().getName().equals("org.polyfrost.skinlayers.render.CompiledMesh")) throw new IllegalArgumentException("Unknown voxel geometry class");
        return (float[]) QUADS.get(mesh.getClass()).get(mesh);
    }
    static int part(Object mesh) { return meshParts.getOrDefault(mesh, -1); }
    static void dispose(Object mesh) { meshOffsets.remove(mesh); meshParts.remove(mesh); }
    static void reset() { meshOffsets.clear(); meshParts.clear(); offsets.clear(); current = null; enabledMask=0;revision++; }

    private static VarHandle field(Class<?> type, String name) throws ReflectiveOperationException {
        for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) {
            try {
                Field field = owner.getDeclaredField(name);
                return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).unreflectVarHandle(field);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
