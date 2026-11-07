//? if <= 1.8.9 {
/*package org.polyfrost.oneconfig.internal.legacy;

public final class NativeCursorVisibilityTest {
    private static int checks;
    private static final class Cursor implements FullscreenCursor.NativeCursor {
        boolean visible, available = true;
        int queries, shows;
        @Override public boolean visible() { queries++; return visible; }
        @Override public boolean show() { shows++; if (available) visible = true; return available; }
    }
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    public static void main(String[] args) {
        for (int flags = 0; flags < 32; flags++) {
            boolean ready = (flags & 1) != 0, menu = (flags & 2) != 0, grabbed = (flags & 4) != 0;
            boolean focus = (flags & 8) != 0, visible = (flags & 16) != 0;
            Cursor cursor = new Cursor(); cursor.visible = visible;
            check(FullscreenCursor.synchronize(ready, menu, grabbed, focus, cursor));
            boolean ui = ready && menu && !grabbed && focus;
            check(cursor.queries == (ui ? 1 : 0));
            check(cursor.shows == (ui && !visible ? 1 : 0));
        }
        Cursor cursor = new Cursor();
        check(FullscreenCursor.synchronize(false, true, false, true, cursor)); check(cursor.queries == 0);
        check(FullscreenCursor.synchronize(true, true, false, false, cursor)); check(cursor.shows == 0);
        check(FullscreenCursor.synchronize(true, true, false, true, cursor)); check(cursor.shows == 1);
        check(FullscreenCursor.synchronize(true, false, true, true, cursor)); check(cursor.shows == 1);
        check(FullscreenCursor.synchronize(true, true, true, true, cursor)); check(cursor.shows == 1);
        check(FullscreenCursor.synchronize(true, true, false, true, cursor)); check(cursor.shows == 1);
        check(FullscreenCursor.synchronize(false, false, false, false, cursor)); check(cursor.shows == 1);
        cursor.visible = false; cursor.available = false;
        check(!FullscreenCursor.synchronize(true, true, false, true, cursor)); check(cursor.shows == 2);
        cursor.available = true;
        check(FullscreenCursor.synchronize(true, true, false, true, cursor)); check(cursor.shows == 3);
        check(FullscreenCursor.synchronize(true, true, false, true, cursor)); check(cursor.shows == 3);
        System.out.println("PASS " + checks + " native-cursor visibility/capability/focus/grab/create/destroy/retry checks; no GL/native calls");
    }
}
*///?}
