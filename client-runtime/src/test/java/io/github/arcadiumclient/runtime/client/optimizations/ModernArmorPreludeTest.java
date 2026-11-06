// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ModernArmorPreludeTest {
    @Test void theNativeSneakTranslationAppliesOnlyToTheAdultModernModel() {
        assertEquals(1,ModernArmorMvp.selectPrelude(true,false,false,false,true,false));
        assertEquals(0,ModernArmorMvp.selectPrelude(false,false,false,false,true,false));
        assertEquals(0,ModernArmorMvp.selectPrelude(false,false,false,false,true,true));
    }
    @Test void oldSneakDoesNotReceiveAnExtraNativeTranslation() {
        assertEquals(0,ModernArmorMvp.selectPrelude(true,false,false,false,true,true));
    }
    @Test void everyBabyModelRetainsItsSeparateNativeScalePrelude() {
        assertEquals(-1,ModernArmorMvp.selectPrelude(true,true,false,false,true,false));
        assertEquals(-1,ModernArmorMvp.selectPrelude(true,false,true,false,true,false));
        assertEquals(-1,ModernArmorMvp.selectPrelude(false,false,false,true,true,true));
    }
    @Test void anUnknownAnimationApiNeverGuessesTheTransform() {
        assertEquals(-1,ModernArmorMvp.selectPrelude(true,false,false,false,false,false));
        assertEquals(-1,ModernArmorMvp.selectPrelude(false,false,false,false,false,true));
    }
    @Test void missingAndInvalidPreludeTokensCannotTouchANativeContext() {
        var helper=new ModernArmorMvp();
        assertFalse(helper.begin(new Object(),null,null,1));
        helper.preparePrelude(2);
        assertFalse(helper.begin(new Object(),null,null,2));
        assertFalse(helper.begin(new Object(),null,null,3));
    }
}
