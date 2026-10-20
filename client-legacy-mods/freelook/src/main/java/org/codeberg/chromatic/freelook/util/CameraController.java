package org.codeberg.chromatic.freelook.util;

//? if >1.8.9
//import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

public class CameraController {
    public static int activePerspective = 0;

    public static int get() {
        //? if >1.8.9 {
        /*return Minecraft.getInstance().options.getCameraType().ordinal();
        *///?} else
        return Minecraft.getInstance().options.perspective;
    }

    public static void set(int type) {
        if (get() != type) restore(type);
        activePerspective = type;
    }

    public static void restore(int type) {
        activePerspective = type;
        //? if >1.8.9 {
        /*Minecraft.getInstance().options.setCameraType(CameraType.values()[type]);
        *///?} else {
        Minecraft.getInstance().options.perspective = type;
        //? if >=1.8.9
        //Minecraft.getInstance().worldRenderer.onViewChanged();
        //?}
    }
}
