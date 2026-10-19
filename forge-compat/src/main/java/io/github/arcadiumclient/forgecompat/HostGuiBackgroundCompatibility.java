package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HostGuiBackgroundCompatibility implements MixinAnnotationAdjuster {
    private static final String MIXIN = "eu.midnightdust.blur.mixin.ScreenMixin";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/HostGuiBackgroundCompatibility";
    private record Dispatch(MethodHandle bus, MethodHandle event, MethodHandle post) { }
    private static final ClassValue<Dispatch> DISPATCH = new ClassValue<>() {
        @Override protected Dispatch computeValue(Class<?> gui) {
            try {
                ClassLoader loader = gui.getClassLoader();
                Class<?> forge = Class.forName("net.minecraftforge.common.MinecraftForge", false, loader);
                Class<?> bus = Class.forName("net.minecraftforge.fml.common.eventhandler.EventBus", false, loader);
                Class<?> base = Class.forName("net.minecraftforge.fml.common.eventhandler.Event", false, loader);
                Class<?> event = Class.forName("net.minecraftforge.client.event.GuiScreenEvent$BackgroundDrawnEvent", false, loader);
                var lookup = MethodHandles.publicLookup();
                return new Dispatch(lookup.findStaticGetter(forge, "EVENT_BUS", bus).asType(MethodType.methodType(Object.class)),
                    lookup.findConstructor(event, MethodType.methodType(void.class, gui)).asType(MethodType.methodType(Object.class, Object.class)),
                    lookup.findVirtual(bus, "post", MethodType.methodType(boolean.class, base)).asType(MethodType.methodType(boolean.class, Object.class, Object.class)));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("The Forge GUI background event API could not be linked", exception);
            }
        }
    };
    private final String gui, background;

    HostGuiBackgroundCompatibility(ForgeMappings mappings) {
        gui = mappings.classFromSrg("net/minecraft/client/gui/GuiScreen");
        background = mappings.methodFromSrg("net/minecraft/client/gui/GuiScreen", "func_146276_q_", "()V") + "()V";
    }

    public static void post(Object screen, Class<?> gui) throws Throwable {
        Dispatch dispatch = DISPATCH.get(gui);
        Object bus = (Object) dispatch.bus.invokeExact();
        Object event = (Object) dispatch.event.invokeExact(screen);
        boolean ignored = (boolean) dispatch.post.invokeExact(bus, event);
    }

    @Override public AdjustableAnnotationNode adjust(List<String> targets, String mixin, MethodNode handler, AdjustableAnnotationNode annotation) {
        if (!mixin.replace('/', '.').equals(MIXIN) || targets.size() != 1 || !targets.get(0).replace('.', '/').equals(gui)
            || !handler.name.equals("blur$renderBackground") || !annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) return annotation;
        if (!handler.desc.equals("(L" + CALLBACK + ";)V") || (handler.access & Opcodes.ACC_STATIC) != 0
            || !List.of(background).equals(value(annotation, "method")) || !Boolean.TRUE.equals(value(annotation, "cancellable"))) throw unsupported();
        Object raw = value(annotation, "at");
        List<?> points = raw instanceof List<?> list ? list : raw instanceof AnnotationNode point ? List.of(point) : List.of();
        if (points.size() != 1 || !(points.get(0) instanceof AnnotationNode point) || !"HEAD".equals(value(point, "value"))) throw unsupported();
        List<AbstractInsnNode> body = Arrays.stream(handler.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList();
        boolean installed = body.stream().anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(BRIDGE) && call.name.equals("post"));
        if (installed) return annotation;
        if (body.size() != 7 || !load(body.get(0), 0) || !(body.get(1) instanceof TypeInsnNode cast) || cast.getOpcode() != Opcodes.CHECKCAST || !cast.desc.equals(gui)
            || !(body.get(2) instanceof MethodInsnNode render) || render.getOpcode() != Opcodes.INVOKESTATIC || render.itf
            || !render.owner.equals("eu/midnightdust/blur/BlurRenderer") || !render.name.equals("renderBackground") || !render.desc.equals("(L" + gui + ";)Z")
            || !(body.get(3) instanceof JumpInsnNode branch) || branch.getOpcode() != Opcodes.IFEQ || !load(body.get(4), 1)
            || !(body.get(5) instanceof MethodInsnNode cancel) || cancel.getOpcode() != Opcodes.INVOKEVIRTUAL || cancel.itf
            || !cancel.owner.equals(CALLBACK) || !cancel.name.equals("cancel") || !cancel.desc.equals("()V") || body.get(6).getOpcode() != Opcodes.RETURN) throw unsupported();
        AbstractInsnNode destination = branch.label;
        while (destination != null && destination.getOpcode() < 0) destination = destination.getNext();
        if (destination != body.get(6)) throw unsupported();
        InsnList event = new InsnList(); event.add(new VarInsnNode(Opcodes.ALOAD, 0)); event.add(new LdcInsnNode(Type.getObjectType(gui)));
        event.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "post", "(Ljava/lang/Object;Ljava/lang/Class;)V", false));
        handler.instructions.insert(cancel, event); handler.maxStack = Math.max(handler.maxStack, 2);
        return annotation;
    }

    private static boolean load(AbstractInsnNode value, int index) { return value instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD && variable.var == index; }
    private static Object value(AnnotationNode node, String key) { if (node.values != null) for (int i = 0; i < node.values.size(); i += 2) if (node.values.get(i).equals(key)) return node.values.get(i + 1); return null; }
    private static IllegalStateException unsupported() { return new IllegalStateException("Unsupported native Blur background handler"); }
}
