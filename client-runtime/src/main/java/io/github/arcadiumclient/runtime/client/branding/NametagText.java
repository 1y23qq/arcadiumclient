// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.branding;

public final class NametagText {
    private NametagText() { }

    public static String stripFormatting(String text) {
        if(text==null||text.indexOf('§')<0)return text;
        StringBuilder result=null;
        for(int i=0;i<text.length();i++){
            if(text.charAt(i)=='§'&&i+1<text.length()){
                char code=text.charAt(i+1);if(code>='A'&&code<='Z')code=(char)(code+32);
                if(code>='0'&&code<='9'||code>='a'&&code<='f'||code>='k'&&code<='o'||code=='r'){
                    if(result==null)result=new StringBuilder(text.length()).append(text,0,i);i++;continue;
                }
            }
            if(result!=null)result.append(text.charAt(i));
        }
        return result==null?text:result.toString();
    }

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
