// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.platform;

import java.math.BigInteger;

public interface PortSemanticVersion extends PortVersion {
    static PortSemanticVersion parse(String value) {
        if (!value.matches("[0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?"))
            throw new PortVersionParsingException("Invalid semantic version: " + value);
        return new Value(value);
    }

    record Value(String getFriendlyString) implements PortSemanticVersion {
        @Override public int compareTo(PortVersion other) {
            String[] a = getFriendlyString.split("\\+", 2)[0].split("-", 2);
            String[] b = other.getFriendlyString().split("\\+", 2)[0].split("-", 2);
            String[] av = a[0].split("\\."), bv = b[0].split("\\.");
            for (int i = 0; i < Math.max(av.length, bv.length); i++) {
                int comparison = new BigInteger(i < av.length ? av[i] : "0").compareTo(new BigInteger(i < bv.length ? bv[i] : "0"));
                if (comparison != 0) return comparison;
            }
            if (a.length != b.length) return a.length == 1 ? 1 : -1;
            if (a.length == 1) return 0;
            av = a[1].split("\\."); bv = b[1].split("\\.");
            for (int i = 0; i < Math.min(av.length, bv.length); i++) {
                boolean an = av[i].matches("[0-9]+"), bn = bv[i].matches("[0-9]+");
                int comparison = an && bn ? new BigInteger(av[i]).compareTo(new BigInteger(bv[i]))
                    : an != bn ? an ? -1 : 1 : av[i].compareTo(bv[i]);
                if (comparison != 0) return comparison;
            }
            return Integer.compare(av.length, bv.length);
        }
        @Override public String toString() { return getFriendlyString; }
    }
}
