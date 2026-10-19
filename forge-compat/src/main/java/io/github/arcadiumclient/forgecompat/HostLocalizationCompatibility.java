package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class HostLocalizationCompatibility implements MixinAnnotationAdjuster {
    private static final String MIXIN = "net.ornithemc.osl.localization.impl.mixin.client.LocaleMixin";
    private static final String LOCALE_API = "net/ornithemc/osl/localization/impl/Locale";
    private static final String SELF = "io/github/arcadiumclient/forgecompat/HostLocalizationCompatibility";
    private static final String ACTIVE = "arcadium$forge$activeLocaleMap";
    private static final String MAP = "Ljava/util/Map;";
    private static final String BIND = "(Ljava/lang/Object;" + MAP + MAP + ")V";
    private static final String LOAD = "(Ljava/lang/Object;Ljava/io/InputStream;" + MAP + MAP + ")V";
    private final String locale, manager, properties;
    private record Api(MethodHandle wrap, MethodHandle lang, MethodHandle json) { }
    private static final ClassValue<Api> APIS = new ClassValue<>() {
        @Override protected Api computeValue(Class<?> type) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                MethodHandle wrap = lookup.findVirtual(type,"wrap",MethodType.methodType(void.class,Map.class))
                    .asType(MethodType.methodType(void.class,Object.class,Map.class));
                MethodType parser = MethodType.methodType(void.class,InputStream.class), erased = MethodType.methodType(void.class,Object.class,InputStream.class);
                return new Api(wrap,lookup.findVirtual(type,"loadFromLang",parser).asType(erased),lookup.findVirtual(type,"loadFromJson",parser).asType(erased));
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException("The OSL localization parser API is unavailable.",exception); }
        }
    };
    private static final ClassValue<IdentityHashMap<Object,ContextMap>> CONTEXTS = new ClassValue<>() {
        @Override protected IdentityHashMap<Object,ContextMap> computeValue(Class<?> type) { return new IdentityHashMap<>(); }
    };

    private static final class ContextMap extends AbstractMap<String,String> {
        volatile Map<String,String> active;
        final ThreadLocal<ArrayDeque<Map<String,String>>> scopes = new ThreadLocal<>();
        Map<String,String> target() { ArrayDeque<Map<String,String>> stack=scopes.get();return stack==null?active:stack.peek(); }
        @Override public Set<Entry<String,String>> entrySet() { return target().entrySet(); }
        @Override public String get(Object key) { return target().get(key); }
        @Override public String getOrDefault(Object key,String fallback) { return target().getOrDefault(key,fallback); }
        @Override public boolean containsKey(Object key) { return target().containsKey(key); }
        @Override public boolean containsValue(Object value) { return target().containsValue(value); }
        @Override public Set<String> keySet() { return target().keySet(); }
        @Override public Collection<String> values() { return target().values(); }
        @Override public String put(String key,String value) { return target().put(key,value); }
        @Override public void putAll(Map<? extends String,? extends String> values) { target().putAll(values); }
        @Override public String remove(Object key) { return target().remove(key); }
        @Override public void clear() { target().clear(); }
        @Override public int size() { return target().size(); }
        @Override public boolean isEmpty() { return target().isEmpty(); }
    }

    public HostLocalizationCompatibility(ForgeMappings mappings) {
        locale = mappings.classFromSrg("net/minecraft/client/resources/Locale");
        manager = mappings.classFromSrg("net/minecraft/client/resources/LanguageManager");
        properties = mappings.fieldFromSrg("net/minecraft/client/resources/Locale","field_135032_a",MAP);
    }

    public static void normalize(ClassNode node, ForgeMappings mappings) {
        String manager = mappings.classFromSrg("net/minecraft/client/resources/LanguageManager");
        if (!node.name.equals(manager)) return;
        String locale = mappings.classFromSrg("net/minecraft/client/resources/Locale");
        String selected = mappings.fieldFromSrg("net/minecraft/client/resources/LanguageManager","field_135049_a","Lnet/minecraft/client/resources/Locale;");
        String properties = mappings.fieldFromSrg("net/minecraft/client/resources/Locale","field_135032_a",MAP);
        if (node.methods.stream().anyMatch(method -> method.name.equals(ACTIVE) && method.desc.equals("()"+MAP))) return;
        if (node.fields.stream().noneMatch(field -> field.name.equals(selected) && field.desc.equals("L"+locale+";") && (field.access & Opcodes.ACC_STATIC)!=0))
            throw new IllegalStateException("The active Minecraft locale field is unavailable.");
        MethodNode getter = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC,ACTIVE,"()"+MAP,null,null);
        LabelNode present = new LabelNode();
        getter.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,manager,selected,"L"+locale+";"));getter.instructions.add(new InsnNode(Opcodes.DUP));
        getter.instructions.add(new JumpInsnNode(Opcodes.IFNONNULL,present));getter.instructions.add(new InsnNode(Opcodes.POP));getter.instructions.add(new InsnNode(Opcodes.ACONST_NULL));getter.instructions.add(new InsnNode(Opcodes.ARETURN));
        getter.instructions.add(present);getter.instructions.add(new FrameNode(Opcodes.F_SAME1,0,null,1,new Object[]{locale}));
        getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,locale,properties,MAP));getter.instructions.add(new InsnNode(Opcodes.ARETURN));getter.maxStack=2;node.methods.add(getter);
    }

    @Override public AdjustableAnnotationNode adjust(List<String> targets,String mixin,MethodNode handler,AdjustableAnnotationNode annotation) {
        if (!mixin.replace('/','.').equals(MIXIN) || targets.size()!=1 || !targets.get(0).replace('.','/').equals(locale)) return annotation;
        boolean constructor = handler.name.equals("osl$localization$initLocale") && handler.desc.equals("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")
            && annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;");
        boolean parser = handler.name.equals("osl$localization$loadExtraTranslations") && handler.desc.startsWith("(L"+locale+";Ljava/io/InputStream;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;")
            && annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
        if (!constructor && !parser) return annotation;
        int expected = constructor ? 1 : 2, adapted = 0;List<MethodInsnNode> changes=new ArrayList<>();
        for (AbstractInsnNode instruction : handler.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.owner.equals(SELF) && (constructor && call.name.equals("bind") || parser && (call.name.equals("loadLang") || call.name.equals("loadJson")))) { adapted++;continue; }
            if (!call.owner.equals(LOCALE_API) || call.getOpcode()!=Opcodes.INVOKEVIRTUAL) continue;
            boolean wrap = constructor && call.name.equals("wrap") && call.desc.equals("("+MAP+")V");
            boolean lang = parser && call.name.equals("loadFromLang") && call.desc.equals("(Ljava/io/InputStream;)V");
            boolean json = parser && call.name.equals("loadFromJson") && call.desc.equals("(Ljava/io/InputStream;)V");
            if (!wrap && !lang && !json) continue;
            changes.add(call);
        }
        if (adapted+changes.size()!=expected) throw new IllegalStateException("Unsupported OSL locale binding handler: "+handler.name+handler.desc);
        for(MethodInsnNode call:changes) {
            boolean wrap=constructor,lang=call.name.equals("loadFromLang");
            InsnList context = new InsnList();
            if (parser) { context.add(new VarInsnNode(Opcodes.ALOAD,0));context.add(new FieldInsnNode(Opcodes.GETFIELD,mixin.replace('.','/'),properties,MAP)); }
            context.add(new MethodInsnNode(Opcodes.INVOKESTATIC,manager,ACTIVE,"()"+MAP,false));handler.instructions.insertBefore(call,context);
            call.setOpcode(Opcodes.INVOKESTATIC);call.owner=SELF;call.name=wrap?"bind":lang?"loadLang":"loadJson";call.desc=wrap?BIND:LOAD;call.itf=false;adapted++;
        }
        handler.maxStack=Math.max(handler.maxStack,constructor?3:4);return annotation;
    }

    public static void bind(Object locale,Map<?,?> instance,Map<?,?> active) {
        context(locale,instance,active);
    }
    public static void loadLang(Object locale,InputStream input,Map<?,?> instance,Map<?,?> active) { parse(locale,input,instance,active,false); }
    public static void loadJson(Object locale,InputStream input,Map<?,?> instance,Map<?,?> active) { parse(locale,input,instance,active,true); }

    private static void parse(Object locale,InputStream input,Map<?,?> instance,Map<?,?> active,boolean json) {
        ContextMap context=context(locale,instance,active);ArrayDeque<Map<String,String>> stack=context.scopes.get();
        if(stack==null){stack=new ArrayDeque<>();context.scopes.set(stack);}stack.push(strings(instance));
        try { Api api=APIS.get(locale.getClass());if(json)api.json.invokeExact(locale,input);else api.lang.invokeExact(locale,input); }
        catch(Throwable failure) { HostLocalizationCompatibility.<RuntimeException>raise(failure); }
        finally { stack.pop();if(stack.isEmpty())context.scopes.remove(); }
    }

    private static ContextMap context(Object locale,Map<?,?> instance,Map<?,?> active) {
        synchronized(locale) {
            IdentityHashMap<Object,ContextMap> contexts=CONTEXTS.get(locale.getClass());ContextMap context;
            synchronized(contexts){context=contexts.computeIfAbsent(locale,key->new ContextMap());}
            context.active=strings(active==null?instance:active);
            try { APIS.get(locale.getClass()).wrap.invokeExact(locale,(Map)context); }
            catch(Throwable failure){HostLocalizationCompatibility.<RuntimeException>raise(failure);}
            return context;
        }
    }
    @SuppressWarnings("unchecked") private static Map<String,String> strings(Map<?,?> values) { return (Map<String,String>)values; }
    @SuppressWarnings("unchecked") private static <T extends Throwable> void raise(Throwable failure) throws T { throw (T)failure; }
}
