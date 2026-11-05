// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CeraEmissiveAdmissionTest {
    @Test void changedApiResourcesAndUnfamiliarSubmissionHooksRetainNativeCallbacks() {
        String api = "a64ff3b9c39490c36590c4803a131b2d73887e9e1e8d57ac052aca53a46cffed";
        String extension = "f9f1444c42db358133f418a6d7a561dc6925ad1296c1b6e867155bfe777b2012";
        String manager = "d14c7e8016e649ce8f4dfbb8b13018192d7e6787625b8a7a0e5583ae5019cc4e";
        assertTrue(CeraEmissiveAdmission.knownApi(api, extension, manager));
        assertFalse(CeraEmissiveAdmission.knownApi("changed", extension, manager));
        assertFalse(CeraEmissiveAdmission.knownApi(api, "changed", manager));
        assertFalse(CeraEmissiveAdmission.knownApi(api, extension, "changed"));
        assertTrue(CeraEmissiveAdmission.knownHook("dev.rdh.cera.mixin.ModelInstancerMixin", true));
        assertFalse(CeraEmissiveAdmission.knownHook("dev.rdh.cera.mixin.ModelInstancerMixin", false));
        assertTrue(CeraEmissiveAdmission.knownHook("dev.rdh.cera.mixin.TextureManagerMixin", true));
        assertFalse(CeraEmissiveAdmission.knownHook("dev.rdh.cera.mixin.TextureManagerMixin", false));
        assertFalse(CeraEmissiveAdmission.knownHook("dev.rdh.cera.mixin.CustomSubmitMixin", true));
        assertFalse(CeraEmissiveAdmission.knownHook("other.mod.NativeSubmitMixin", true));
        assertTrue(CeraEmissiveAdmission.nativeSubmitRequired((net.minecraft.resource.Identifier)null));
    }

    @Test void onlyTheAuditedSarcioDeletionHookIsAccepted() {
        String jar = "bbccff506dcbe04b1b081b46cc5d98f93fcf1af316bd5fda333a499bc5bc378f";
        String mixin = "8b6e913faa78b52f1cf779e40e82e3c985264cd841a29a7f036239ef00333121";
        String name = "dev.rdh.sarcio.mixin.opt.textures.TextureManagerMixin";
        assertTrue(CeraEmissiveAdmission.knownSarcio("1.1.0", jar, mixin));
        assertFalse(CeraEmissiveAdmission.knownSarcio("1.1.1", jar, mixin));
        assertFalse(CeraEmissiveAdmission.knownSarcio("1.1.0", "changed", mixin));
        assertFalse(CeraEmissiveAdmission.knownSarcio("1.1.0", jar, "changed"));
        assertTrue(CeraEmissiveAdmission.knownHook(name, true, true));
        assertFalse(CeraEmissiveAdmission.knownHook(name, true, false));
        assertFalse(CeraEmissiveAdmission.knownHook("dev.rdh.sarcio.mixin.opt.textures.OtherMixin", true, true));
        assertFalse(CeraEmissiveAdmission.knownHook("other.mod.TextureManagerMixin", true, true));
    }
}
