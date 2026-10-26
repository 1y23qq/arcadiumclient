package pl.tomgirl.pylon.window;

import java.util.function.Supplier;

import org.lwjgl.sdl.SDL_MainThreadCallback;
import org.lwjgl.system.JNI;
import org.lwjgl.system.macosx.LibC;
import pl.tomgirl.pylon.Platform;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.macosx.DynamicLinkLoader.RTLD_DEFAULT;
import static org.lwjgl.system.macosx.DynamicLinkLoader.dlsym;

public class MainThread {
    public static final boolean DISPATCH = Platform.CURRENT == Platform.MACOS && System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + LibC.getpid()) == null;

    private static long dispatchSyncF;
    private static long mainQueue;
    private static SDL_MainThreadCallback trampoline;
    private static volatile Thread thread;
    private static volatile Supplier<?> task;
    private static volatile Object result;
    private static volatile Throwable failure;

    private MainThread() {}

    public static void run(Runnable task) {
        call(() -> {
            task.run();
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    public static synchronized <T> T call(Supplier<T> task) {
        if (!DISPATCH || Thread.currentThread() == thread) {
            return task.get();
        }

        if (trampoline == null) {
            dispatchSyncF = check(dlsym(RTLD_DEFAULT, "dispatch_sync_f"));
            mainQueue = check(dlsym(RTLD_DEFAULT, "_dispatch_main_q"));
            trampoline = SDL_MainThreadCallback.create(userdata -> execute());
        }

        MainThread.task = task;
        try {
            JNI.invokePPPV(mainQueue, NULL, trampoline.address(), dispatchSyncF);
            Throwable t = failure;
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            if (t != null) {
                throw new IllegalStateException(t);
            }
            return (T) result;
        } finally {
            MainThread.task = null;
            result = null;
            failure = null;
        }
    }

    private static void execute() {
        thread = Thread.currentThread();
        try {
            result = task.get();
        } catch (Throwable t) {
            failure = t;
        }
    }

    private static long check(long address) {
        if (address == NULL) {
            throw new IllegalStateException("libdispatch symbol not found");
        }
        return address;
    }
}
