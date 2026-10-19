package io.github.arcadiumclient.forgecompat;

import java.util.Objects;

public final class LegacyJavaVersion {
    private LegacyJavaVersion() { }

    public static boolean isJavaVersionAtLeast(Enum<?> required) {
        return atLeast(Integer.toString(Runtime.version().feature()), required);
    }

    static boolean atLeast(String current, Enum<?> required) {
        Objects.requireNonNull(required, "required Java version");
        if (!required.getDeclaringClass().getName().equals("org.apache.commons.lang3.JavaVersion")) {
            throw new IllegalArgumentException("Expected Commons Lang JavaVersion");
        }
        return feature(current) >= feature(required.toString());
    }

    private static int feature(String version) {
        // Commons Lang 3.3.2 compares Java 0.9 as Java 1.5.
        if (version.equals("0.9")) return 5;
        String number = version.startsWith("1.") ? version.substring(2) : version;
        int dot = number.indexOf('.');
        int feature = Integer.parseInt(dot < 0 ? number : number.substring(0, dot));
        if (feature < 1) throw new IllegalArgumentException("Invalid Java version: " + version);
        return feature;
    }
}
