// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.regex.Pattern;

public final class CosmeticUsernamePredicate {
    private static volatile Pattern trusted;
    private CosmeticUsernamePredicate() { }
    public static boolean matches(Pattern pattern,CharSequence input){
        if(!RenderHookAllocationControl.enabled||!(input instanceof String name)||!trusted(pattern))return pattern.matcher(input).matches();
        if(name.isEmpty())return false;
        for(int i=0;i<name.length();i++){
            char character=name.charAt(i);
            if(character!='_'&&(character<'A'||character>'Z')&&(character<'a'||character>'z')&&(character<'0'||character>'9'))return false;
        }
        return true;
    }
    private static boolean trusted(Pattern pattern){
        if(pattern==null)return false;
        if(pattern==trusted)return true;
        if(pattern.flags()!=0||!pattern.pattern().equals("[A-Za-z0-9_]+"))return false;
        trusted=pattern;return true;
    }
}
