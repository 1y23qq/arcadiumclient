package io.github.arcadiumclient.forgecompat;

import com.google.common.reflect.ClassPath;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayDeque;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Objects;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

public final class HostClassPathCompatibility {
    private HostClassPathCompatibility() { }

    public static ClassPath from(ClassLoader loader) throws IOException {
        Objects.requireNonNull(loader, "class loader");
        LaunchClassLoader configured = Launch.classLoader;
        if (configured == null || loader != configured && loader != configured.getParent()) return ClassPath.from(loader);
        ArrayDeque<ClassLoader> hierarchy = new ArrayDeque<>();
        for (ClassLoader current = loader; current != null; current = current.getParent()) hierarchy.push(current);
        LinkedHashMap<URI, URL> urls = new LinkedHashMap<>();
        while (!hierarchy.isEmpty()) if (hierarchy.pop() instanceof URLClassLoader source) {
            for (URL url : source.getURLs()) {
                try { urls.putIfAbsent(url.toURI(), url); }
                catch (URISyntaxException exception) { throw new IllegalArgumentException(exception); }
            }
        }
        try (ScanView view = new ScanView(urls.values().toArray(URL[]::new), configured.getParent())) {
            return ClassPath.from(view);
        }
    }

    private static final class ScanView extends URLClassLoader {
        private final ClassLoader target;

        private ScanView(URL[] urls, ClassLoader target) {
            super(urls, null);
            this.target = target;
        }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            return target.loadClass(name);
        }

        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            return target.loadClass(name);
        }

        @Override public URL getResource(String name) { return target.getResource(name); }
        @Override public URL findResource(String name) { return target.getResource(name); }
        @Override public Enumeration<URL> getResources(String name) throws IOException { return target.getResources(name); }
        @Override public Enumeration<URL> findResources(String name) throws IOException { return target.getResources(name); }
        @Override public InputStream getResourceAsStream(String name) { return target.getResourceAsStream(name); }
    }
}
