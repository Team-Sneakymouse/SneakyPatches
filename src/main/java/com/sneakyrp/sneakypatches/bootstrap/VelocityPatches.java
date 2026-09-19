package com.sneakyrp.sneakypatches.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public final class VelocityPatches extends PlatformPatches {
    private static final String TARGET = "net/momirealms/craftengine/proxy/velocity/VelocityCraftEngine.class";

    @Override public void onLoad(String mixinPackage) {
        if (!velocity(loader())) return;
        // Velocity uses parent-first plugin loading. Expose this one unchanged
        // plugin JAR to Ember before target discovery, not all plugin libraries.
        Path directory = Path.of(System.getProperty("sneakypatches.plugins", "plugins"));
        try {
            if (!Files.isDirectory(directory)) return;
            List<Path> matches = new ArrayList<>();
            try (var paths = Files.list(directory)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".jar")).toList()) {
                    try (JarFile jar = new JarFile(path.toFile())) {
                        if (jar.getJarEntry(TARGET) != null) matches.add(path.toAbsolutePath());
                    }
                }
            }
            if (matches.isEmpty()) return;
            if (matches.size() != 1) throw new IllegalStateException("Multiple CraftEngine Proxy JARs: " + matches);
            // Ignite's ASM frame writer resolves hierarchies through the system
            // loader. Mirror Ignite's own game/library registration for this JAR.
            Class.forName("space.vectrix.ignite.agent.IgniteAgent", true, ClassLoader.getSystemClassLoader())
                    .getMethod("addJar", Path.class).invoke(null, matches.getFirst());
            loader().getClass().getMethod("addTransformationPath", Path.class).invoke(loader(), matches.getFirst());
            active = true;
            System.out.println("[SneakyPatches] Velocity resource-pack reconciliation enabled: " + matches.getFirst().getFileName());
        } catch (ReflectiveOperationException | java.io.IOException e) {
            throw new IllegalStateException("Cannot expose CraftEngine Proxy to Ignite's transformation loader", e);
        }
    }
    @Override public List<String> getMixins() {
        return active ? List.of("ProxyPlayerMixin", "PackSendMixin", "PackRemoveMixin", "ConfigurationMixin", "SessionMixin", "RecordMixin", "VelocityStartupMixin", "VelocityBackendDedupMixin") : List.of();
    }
}
