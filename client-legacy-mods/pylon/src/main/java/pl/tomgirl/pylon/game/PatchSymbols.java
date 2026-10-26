// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package pl.tomgirl.pylon.game;

import java.nio.charset.StandardCharsets;

final class PatchSymbols {
    private static final byte[][] SYMBOLS = {
        "org/lwjgl/opengl/Display".getBytes(StandardCharsets.US_ASCII),
        "java/awt/datatransfer/Clipboard".getBytes(StandardCharsets.US_ASCII),
        "java.awt.Desktop".getBytes(StandardCharsets.US_ASCII),
        "net/minecraftforge/fml/common/Loader".getBytes(StandardCharsets.US_ASCII),
        "cpw/mods/fml/common/Loader".getBytes(StandardCharsets.US_ASCII)
    };

    private PatchSymbols() {}

    static boolean mayNeedPatches(byte[] bytes) {
        if (bytes == null || bytes.length < 24 || bytes[0] != (byte) 0xca || bytes[1] != (byte) 0xfe ||
                bytes[2] != (byte) 0xba || bytes[3] != (byte) 0xbe) return true;
        int major = u2(bytes, 6);
        if (major < 45 || major > 69) return true;
        int constants = u2(bytes, 8);
        if (constants < 2) return true;
        int cursor = 10;
        for (int index = 1; index < constants; index++) {
            if (cursor >= bytes.length) return true;
            int tag = bytes[cursor++] & 255;
            int length;
            switch (tag) {
                case 1:
                    if (cursor > bytes.length - 2) return true;
                    length = u2(bytes, cursor);
                    cursor += 2;
                    if (length > bytes.length - cursor) return true;
                    for (byte[] symbol : SYMBOLS) {
                        if (length == symbol.length && matches(bytes, cursor, symbol)) return true;
                    }
                    break;
                case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18:
                    length = 4;
                    break;
                case 5: case 6:
                    if (++index >= constants) return true;
                    length = 8;
                    break;
                case 7: case 8: case 16: case 19: case 20:
                    length = 2;
                    break;
                case 15:
                    length = 3;
                    break;
                default:
                    return true;
            }
            if (length > bytes.length - cursor) return true;
            cursor += length;
        }
        return cursor > bytes.length - 14;
    }

    private static int u2(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 8 | bytes[offset + 1] & 255;
    }

    private static boolean matches(byte[] bytes, int offset, byte[] symbol) {
        for (int index = 0; index < symbol.length; index++) {
            if (bytes[offset + index] != symbol[index]) return false;
        }
        return true;
    }
}
