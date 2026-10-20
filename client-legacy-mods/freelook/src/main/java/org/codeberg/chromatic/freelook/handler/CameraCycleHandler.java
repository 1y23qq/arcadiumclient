package org.codeberg.chromatic.freelook.handler;

import org.codeberg.chromatic.freelook.option.FreelookConfig;

public class CameraCycleHandler {
    public static boolean hasCycledFreelook = false;

    public static boolean shouldOverrideCameraCycle() {
        boolean freelook = FreelookHandler.INSTANCE.freelookToggled;

        switch (FreelookConfig.onCycleChange) {
            case 0:
                break;
            case 1:
                if (freelook) {
                    FreelookHandler.INSTANCE.stop();
                }
                break;
            case 2:
                if (freelook) {
                    return false;
                }
                break;
        }

        return true;
    }
}
