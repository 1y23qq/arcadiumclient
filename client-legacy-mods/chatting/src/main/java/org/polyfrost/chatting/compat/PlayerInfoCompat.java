package org.polyfrost.chatting.compat;

//? if <= 1.8.9 {
import net.minecraft.client.multiplayer.PlayerInfo;
import org.polyfrost.oneconfig.internal.legacy.PlayerSkin;

public interface PlayerInfoCompat {
    default PlayerSkin getSkin() {
        //? if = 1.7.10 {
        return new PlayerSkin(LegacySkinLookup.texture(((PlayerInfo) (Object) this).name));
        //?} else {
        /*return new PlayerSkin(((PlayerInfo) (Object) this).getSkinTexture());
        *///?}
    }
}
//?}
