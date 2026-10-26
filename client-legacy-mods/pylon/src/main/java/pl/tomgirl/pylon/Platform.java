package pl.tomgirl.pylon;

import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLMisc;
import pl.tomgirl.pylon.window.MainThread;
import java.util.Locale;
import java.util.logging.Level;

public enum Platform {
    UNIX(),
    MACOS(),
    WINDOWS(),
    UNKNOWN();

    public static final Platform CURRENT = getPlatform();

    public void open(String uri) {
        MainThread.run(() -> {
            if (!SDLMisc.SDL_OpenURL(uri)) {
                Pylon.LOG.log(Level.SEVERE, "Could not open URI " + uri + ": " + SDLError.SDL_GetError());
            }
        });
    }

    private static Platform getPlatform() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        return os.contains("win") ? Platform.WINDOWS
            : os.contains("mac") ? Platform.MACOS
            : os.contains("linux") || os.contains("unix") ? Platform.UNIX
            : Platform.UNKNOWN;
    }
}
