// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.platform;

import java.nio.file.Path;
import java.util.List;

public interface PortModContainer {
    PortMetadata getMetadata();
    List<Path> getRootPaths();
    default java.util.Optional<Path> findPath(String path) {
        for (Path root : getRootPaths()) {
            Path candidate = root.resolve(path).normalize();
            if (candidate.startsWith(root) && java.nio.file.Files.exists(candidate)) return java.util.Optional.of(candidate);
        }
        return java.util.Optional.empty();
    }
}
