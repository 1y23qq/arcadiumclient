// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.platform;

public interface PortEntrypoint<T> {
    T getEntrypoint();
    PortModContainer getProvider();
}
