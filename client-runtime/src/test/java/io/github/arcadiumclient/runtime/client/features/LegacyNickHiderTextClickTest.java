package io.github.arcadiumclient.runtime.client.features;

import java.lang.reflect.Constructor;
import java.util.List;
import net.minecraft.text.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyNickHiderTextClickTest {
    private static Object nativeHash(Text text) {
        try { return text.hashCode(); }
        catch (NullPointerException nativeNullableStyle) { return nativeNullableStyle.getClass(); }
    }
    private static Text rendered(Text value, Text original) throws Exception {
        Class<?> marker = Class.forName(LegacyNickHiderText.class.getName() + "$RenderedText");
        Constructor<?> factory = marker.getDeclaredConstructor(Text.class, Text.class, long.class);
        factory.setAccessible(true);
        return (Text) factory.newInstance(value, original, 73L);
    }

    @Test void literalLeavesRemainSelectableAndKeepOriginalCommands() throws Exception {
        for (ClickEvent.Action action : List.of(ClickEvent.Action.RUN_COMMAND, ClickEvent.Action.SUGGEST_COMMAND)) {
            for (String alias : List.of("G", "LongDisplayedAliasForThePlayer")) {
                String command = "/msg ArcadiumTest original-value";
                var click = new ClickEvent(action, command);
                Text original = new LiteralText("ArcadiumTest").setStyle(new Style().setClickEvent(click).setBold(true));
                Text display = rendered(LegacyNickHiderText.display(original, "ArcadiumTest", alias), original);
                Text leaf = java.util.stream.StreamSupport.stream(display.spliterator(), false)
                        .filter(part -> part.getContent().equals(alias)).findFirst().orElseThrow();
                assertFalse(leaf instanceof LiteralText);
                leaf = LegacyNickHiderText.interactionComponent(leaf);
                assertInstanceOf(LiteralText.class, leaf);
                assertEquals(alias, ((LiteralText) leaf).getRawString());
                assertEquals(alias, leaf.getContent());
                assertEquals(action, leaf.getStyle().getClickEvent().getAction());
                assertEquals(command, leaf.getStyle().getClickEvent().getValue());
                assertTrue(leaf.getStyle().isBold());
                assertEquals("ArcadiumTest", original.getString());
                assertEquals(command, original.getStyle().getClickEvent().getValue());
            }
        }
    }

    @Test void inheritedClickHoverAndInsertionSurviveNestedMarkers() throws Exception {
        var click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gift ArcadiumTest original");
        var hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText("Original hover ArcadiumTest"));
        var parent = new Style().setClickEvent(click).setHoverEvent(hover).setInsertion("Original ArcadiumTest insertion");
        Text root = new LiteralText("").setStyle(parent).append(new LiteralText("prefix ")
                .append(new LiteralText("ArcadiumTest")));
        Text masked = rendered(LegacyNickHiderText.display(root, "ArcadiumTest", "Guest"), root);
        Text leaf = java.util.stream.StreamSupport.stream(masked.spliterator(), false)
                .filter(part -> part.getContent().equals("Guest")).findFirst().orElseThrow();
        leaf = LegacyNickHiderText.interactionComponent(leaf);
        assertInstanceOf(LiteralText.class, leaf);
        assertEquals(click, leaf.getStyle().getClickEvent());
        assertEquals(hover, leaf.getStyle().getHoverEvent());
        assertEquals("Original ArcadiumTest insertion", leaf.getStyle().getInsertion());
        assertEquals("prefix ArcadiumTest", root.getString());
    }

    @Test void nonmatchingRenderedLinesKeepTheirLiteralHitTargets() throws Exception {
        Text original = new LiteralText("Click here").setStyle(new Style()
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/local original")));
        Text marked = rendered(original, original);
        Text leaf = LegacyNickHiderText.interactionComponent(marked.iterator().next());
        assertInstanceOf(LiteralText.class, leaf);
        assertEquals("Click here", ((LiteralText) leaf).getRawString());
        assertEquals("/local original", leaf.getStyle().getClickEvent().getValue());
    }

    @Test void bookPageActionsAndSerializationKeepNativeLiteralSemantics() throws Exception {
        var click = new ClickEvent(ClickEvent.Action.CHANGE_PAGE, "2");
        LiteralText original = new LiteralText("Next page");
        original.setStyle(new Style().setClickEvent(click).setUnderlined(true));
        Text marked = rendered(original, original);
        String json = Text.Serializer.toJson(LegacyNickHiderText.interactionComponent(marked));
        Text restored = Text.Serializer.fromJson(json);
        assertEquals("Next page", restored.getString());
        assertEquals(click, restored.getStyle().getClickEvent());
        assertTrue(restored.getStyle().isUnderlined());
        assertEquals(Text.Serializer.toJson(original), json);
    }

    @Test void copiesSiblingsAndNativeEqualityRemainCoherent() throws Exception {
        LiteralText original = new LiteralText("Guest");
        original.setStyle(new Style().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/unchanged ArcadiumTest")));
        original.append(new LiteralText(" sibling"));
        Text outer = rendered(original, original);
        LiteralText marked = (LiteralText) LegacyNickHiderText.interactionComponent(outer);
        assertEquals(original, marked);
        assertEquals(marked, original);
        assertEquals(nativeHash(original), nativeHash(marked));
        Text copy = marked.copy();
        assertNotSame(marked, copy);
        assertEquals(marked, copy);
        assertEquals(copy, marked);
        assertEquals(nativeHash(marked), nativeHash(copy));
        marked.append(new LiteralText(" appended"));
        assertEquals("Guest sibling appended", marked.getString());
        assertEquals("Guest sibling", copy.getString());
        assertEquals("/unchanged ArcadiumTest", marked.getStyle().getClickEvent().getValue());
        marked.getSiblings().add(new LiteralText(" direct"));
        assertEquals("Guest sibling appended direct", marked.getString());
        assertEquals("Guest sibling appended direct", marked.copy().getString());
    }

    @Test void outerMarkersAndUrlMetadataAreUnchanged() throws Exception {
        var original = new LiteralText("ArcadiumTest");
        var url = new ClickEvent(ClickEvent.Action.OPEN_URL, "https://example.invalid/ArcadiumTest");
        Text value = new LiteralText("Guest").setStyle(new Style().setClickEvent(url));
        Text outer = rendered(value, original);
        Text copy = outer.copy();
        assertEquals(outer.getClass(), copy.getClass());
        assertFalse(outer instanceof LiteralText);
        assertFalse(copy instanceof LiteralText);
        var revision = outer.getClass().getDeclaredField("revision");
        revision.setAccessible(true);
        assertEquals(73L, revision.getLong(copy));
        var source = outer.getClass().getDeclaredField("original");
        source.setAccessible(true);
        assertSame(original, source.get(outer));
        assertEquals("ArcadiumTest", ((Text) source.get(copy)).getString());
        Text target = LegacyNickHiderText.interactionComponent(copy);
        assertInstanceOf(LiteralText.class, target);
        assertEquals("Guest", ((LiteralText) target).getRawString());
        assertEquals(url, target.getStyle().getClickEvent());
        assertSame(target, LegacyNickHiderText.interactionComponent(target));
    }

    public static void main(String[] args) throws Exception {
        var test = new LegacyNickHiderTextClickTest();
        test.literalLeavesRemainSelectableAndKeepOriginalCommands();
        test.inheritedClickHoverAndInsertionSurviveNestedMarkers();
        test.nonmatchingRenderedLinesKeepTheirLiteralHitTargets();
        test.bookPageActionsAndSerializationKeepNativeLiteralSemantics();
        test.copiesSiblingsAndNativeEqualityRemainCoherent();
        test.outerMarkersAndUrlMetadataAreUnchanged();
        System.out.println("PASS six literal/style/click/serialization/copy/marker regression cases; no game or network launched");
    }
}
