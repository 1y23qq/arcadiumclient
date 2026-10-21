// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;
import net.fabricmc.tinyremapper.extension.mixin.MixinExtension;
import net.fabricmc.loom.kotlin.remapping.KotlinMetadataTinyRemapperExtensionImpl;

public final class ForgeReobfuscator {
    public static void main(String[] args) throws Exception {
        Path mappings = Path.of(args[0]);
        Path input = Path.of(args[1]);
        Path output = Path.of(args[2]);
        Files.deleteIfExists(output);
        TinyRemapper remapper = TinyRemapper.newRemapper()
            .withMappings(TinyUtils.createTinyMappingProvider(mappings,
                args.length > 4 ? args[4] : "intermediary", args.length > 5 ? args[5] : "srg"))
            .extension(new MixinExtension())
            .extension(KotlinMetadataTinyRemapperExtensionImpl.INSTANCE)
            .renameInvalidLocals(true)
            .resolveMissing(true)
            .build();
        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(output).build()) {
            for (String line : Files.readAllLines(Path.of(args[3]))) {
                if (!line.isBlank() && Files.exists(Path.of(line))) remapper.readClassPath(Path.of(line));
            }
            remapper.readInputs(input);
            consumer.addNonClassFiles(input, NonClassCopyMode.FIX_META_INF, remapper);
            remapper.apply((name, bytes) -> consumer.accept(name,
                args.length > 4 ? bytes : LwjglApiUsage.apply(name, ForgeMixinTargets.apply(name, bytes))));
        } finally {
            remapper.finish();
        }
        System.out.println("Reobfuscated Arcadium for Forge 1.7.10: " + output);
    }
}
