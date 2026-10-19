// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
public final class NativeUtilityRegressionTest {
    @Test
    void visualStateAndArmorSlots() { CuteVisualsPureTest.main(new String[0]); }

    @Test
    void sprintChatArchiveAndImageTransport() throws Exception { ClientUtilitiesPureTest.main(new String[0]); }
}
