package dev.microcontrollers.betterscreens.hook;

public class ScreenMouseHook {
    private static long lastCloseTime = -1L;
    private static boolean wasScreenOpen = false;

    public static long getLastCloseTime() {
        return lastCloseTime;
    }

    public static void setLastCloseTime(long time) {
        lastCloseTime = time;
    }

    public static boolean getWasScreenOpen() {
        return wasScreenOpen;
    }

    public static void setWasScreenOpen(boolean was) {
        wasScreenOpen = was;
    }
}
