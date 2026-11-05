// SPDX-License-Identifier: GPL-3.0-only
package arcadium.renderprobe;

import java.lang.reflect.Field;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(modid = "arcadiumrenderprobe", name = "Arcadium Render Callback QA", version = "1.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public final class RenderProbe {
    public static Object target;
    public static String mode = "observe", failure;
    public static boolean registered;
    public static long playerPre, playerPost, livingPre, livingPost, canceled, mutations;
    public static final StringBuilder sequence = new StringBuilder();
    private static Object movedHead;
    private static Field offset;
    private static float originalOffset;

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        registered = true;
    }

    @SubscribeEvent
    public void beforePlayer(RenderPlayerEvent.Pre event) {
        if (!selected(event, "entityPlayer")) return;
        playerPre++; record("playerPre");
        if (mode.equals("cancel-player")) {
            try { event.setCanceled(true); canceled++; } catch (Throwable error) { failure = error.toString(); }
        }
    }

    @SubscribeEvent
    public void afterPlayer(RenderPlayerEvent.Post event) {
        if (!selected(event, "entityPlayer")) return;
        playerPost++; record("playerPost");
    }

    @SubscribeEvent
    public void beforeLiving(RenderLivingEvent.Pre event) {
        if (!selected(event, "entity")) return;
        livingPre++; record("livingPre");
        if (mode.equals("cancel-living")) {
            try { event.setCanceled(true); canceled++; } catch (Throwable error) { failure = error.toString(); }
            return;
        }
        if (mode.equals("mutate")) {
            try {
                if (movedHead != null) throw new IllegalStateException("Previous render mutation was not restored");
                Object renderer = field(event, "renderer").get(event);
                Object model = field(renderer, "field_77045_g").get(renderer);
                Object head = field(model, "field_78116_c").get(model);
                Field position = field(head, "field_82906_o");
                float previous = position.getFloat(head);
                position.setFloat(head, previous + .125f);
                movedHead = head; offset = position; originalOffset = previous;
                mutations++;
            } catch (Throwable error) { failure = error.toString(); restore(); }
        }
    }

    @SubscribeEvent
    public void afterLiving(RenderLivingEvent.Post event) {
        if (!selected(event, "entity")) return;
        livingPost++; record("livingPost"); restore();
    }

    public static void reset() {
        restore(); playerPre = playerPost = livingPre = livingPost = canceled = mutations = 0;
        sequence.setLength(0); failure = null;
    }

    public static boolean restored() { return movedHead == null; }

    private static boolean selected(Object event, String member) {
        if (target == null || failure != null) return false;
        try { return event.getClass().getField(member).get(event) == target; }
        catch (Throwable error) { failure = error.toString(); return false; }
    }

    private static Field field(Object receiver, String name) throws ReflectiveOperationException {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(receiver.getClass().getName() + "." + name);
    }

    private static void restore() {
        if (movedHead == null) return;
        try { offset.setFloat(movedHead, originalOffset); }
        catch (Throwable error) { failure = error.toString(); }
        finally { movedHead = null; offset = null; }
    }

    private static void record(String name) {
        if (sequence.length() < 32768) { if (sequence.length() != 0) sequence.append(','); sequence.append(name); }
    }
}
