// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.config.v1;

public interface ConfigEntrypoint {
    String ENTRYPOINT = "oneconfig:config";

    void registerConfigs(Context context);

    interface Context {
        String modId();
        <T extends Config> T register(T config);
        void originalScreen(Runnable opener);
    }
}
