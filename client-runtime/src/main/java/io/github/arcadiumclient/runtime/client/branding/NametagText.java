// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.branding;

public final class NametagText {
    private NametagText() { }

    public static String withoutTrailingPadding(String text) {
        if (text == null || text.isEmpty()) return text;
        int lastVisible = 0;
        for (int index = 0; index < text.length();) {
            if (text.charAt(index) == '§' && index + 1 < text.length()) { index += 2; continue; }
            int code = text.codePointAt(index);
            index += Character.charCount(code);
            if (!Character.isWhitespace(code) && !Character.isSpaceChar(code) && code != 0x200B && code != 0xFEFF)
                lastVisible = index;
        }
        if (lastVisible == text.length()) return text;
        StringBuilder output = new StringBuilder(text.substring(0, lastVisible));
        for (int index = lastVisible; index + 1 < text.length(); index++) {
            if (text.charAt(index) == '§') { output.append('§').append(text.charAt(++index)); }
        }
        return output.toString();
    }
}
