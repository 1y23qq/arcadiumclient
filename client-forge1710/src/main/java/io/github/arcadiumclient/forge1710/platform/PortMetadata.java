// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.platform;

import java.util.Collection;
import java.util.Optional;

public interface PortMetadata {
    String getId();
    String getName();
    String getDescription();
    String getType();
    PortVersion getVersion();
    Collection<PortPerson> getAuthors();
    Collection<PortPerson> getContributors();
    Optional<String> getIconPath(int size);
}
