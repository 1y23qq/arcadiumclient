package pl.tomgirl.pylon.bakery.patch;

import java.nio.IntBuffer;

import org.lwjgl.LWJGLException;
import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.openal.AL.createCapabilities;

@SuppressWarnings("unused")
public abstract class ALPatch {
    private static long _contextPtr;
    private static long _devicePtr;
    private static boolean _created;

    @CompatStub
    private static boolean isCreated() {
        return _created;
    }

    @CompatStub
    private static void create(String deviceArguments, int contextFrequency, int contextRefresh, boolean contextSynchronized) throws LWJGLException {
        create(deviceArguments, contextFrequency, contextRefresh, contextSynchronized, true);
    }

    @CompatStub
    private static void create(String deviceArguments, int contextFrequency, int contextRefresh, boolean contextSynchronized, boolean openDevice) throws LWJGLException {
        if (_created) {
            throw new IllegalStateException("Only one OpenAL context may be instantiated");
        } else {
            init(deviceArguments, contextFrequency, contextRefresh, contextSynchronized, openDevice);
            _created = true;
        }
    }

    private static void init(String deviceArguments, int contextFrequency, int contextRefresh, boolean contextSynchronized, boolean openDevice) throws LWJGLException {
        try {
            if (!openDevice) {
                return;
            }

            _devicePtr = ALC10.alcOpenDevice(deviceArguments);
            if (_devicePtr == 0L) {
                throw new LWJGLException("Could not open ALC device");
            }

            ALCCapabilities deviceCaps = ALC.createCapabilities(_devicePtr);
            if (contextFrequency == -1) {
                _contextPtr = ALC10.alcCreateContext(_devicePtr, (IntBuffer) null);
            } else {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    _contextPtr = ALC10.alcCreateContext(_devicePtr, createAttributeList(contextFrequency, contextRefresh, contextSynchronized ? 1 : 0, stack));
                }
            }

            if (_contextPtr == 0L || !ALC10.alcMakeContextCurrent(_contextPtr)) {
                throw new LWJGLException("Could not create or make current an OpenAL context");
            }
            createCapabilities(deviceCaps);
        } catch (LWJGLException | RuntimeException | Error ex) {
            exit();
            throw ex;
        }
    }

    @CompatStub
    private static void create() throws LWJGLException {
        create(null, 44100, 60, false);
    }

    private static IntBuffer createAttributeList(int contextFrequency, int contextRefresh, int contextSynchronized, MemoryStack stack) {
        IntBuffer buf = stack.callocInt(5 * 2 + 1);
        buf.put(ALC10.ALC_FREQUENCY).put(contextFrequency);
        buf.put(ALC10.ALC_REFRESH).put(contextRefresh);
        buf.put(ALC10.ALC_SYNC).put(contextSynchronized);

        if (ALC10.alcGetInteger(_devicePtr, SOFTHRTF.ALC_NUM_HRTF_SPECIFIERS_SOFT) > 0) {
            buf.put(SOFTHRTF.ALC_HRTF_SOFT).put(0);
            buf.put(SOFTHRTF.ALC_HRTF_ID_SOFT).put(0);
        }

        buf.put(0).flip();
        return buf;
    }

    @CompatStub
    private static void exit() {
        if (_contextPtr != 0L) {
            ALC10.alcMakeContextCurrent(0L);
            ALC10.alcDestroyContext(_contextPtr);
            _contextPtr = 0L;
        }

        if (_devicePtr != 0L) {
            ALC10.alcCloseDevice(_devicePtr);
            _devicePtr = 0L;
        }

        AL.setCurrentProcess(null);
        _created = false;
    }
}
