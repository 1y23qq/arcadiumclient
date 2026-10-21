// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.platform;

public interface PortVersion extends Comparable<PortVersion> {
    String getFriendlyString();

    static PortVersion parse(String value) {
        try { return PortSemanticVersion.parse(value); }
        catch (IllegalArgumentException ignored) { return new Value(value); }
    }

    record Value(String getFriendlyString) implements PortVersion {
        @Override
        public int compareTo(PortVersion other) {
            return getFriendlyString.compareTo(other.getFriendlyString());
        }
        @Override public String toString() { return getFriendlyString; }
    }
}
