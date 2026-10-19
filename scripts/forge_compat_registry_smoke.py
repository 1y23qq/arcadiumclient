"""Build a private Forge registry smoke mod without loading Minecraft or Forge."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[1]
SOURCE = r'''
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public final class RegistrySmokeBuilder implements Opcodes {
    private static final String MOD = "arcadium/registry/smoke/RegistrySmoke";
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String MATERIAL = "net/minecraft/block/material/Material";
    private static final String GAME = "net/minecraftforge/fml/common/registry/GameRegistry";
    private static final String ID = "arcadiumregistrysmoke";

    public static void main(String[] args) throws Exception {
        Map<String, byte[]> classes = Map.of(MOD + ".class", mod(), MOD + "Block.class", block());
        for (byte[] bytes : classes.values()) {
            ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
            for (MethodNode method : node.methods) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
        }
        Manifest manifest = new Manifest(); manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(Path.of(args[0])), manifest)) {
            for (var entry : new TreeMap<>(classes).entrySet()) { jar.putNextEntry(new JarEntry(entry.getKey())); jar.write(entry.getValue()); jar.closeEntry(); }
            jar.putNextEntry(new JarEntry("mcmod.info"));
            jar.write(("[{\"modid\":\"" + ID + "\",\"name\":\"Arcadium Registry Smoke\",\"version\":\"1.0.0\",\"mcversion\":\"1.8.9\"}]").getBytes(java.nio.charset.StandardCharsets.UTF_8)); jar.closeEntry();
        }
        System.out.println("Verified 2 private smoke classes; no Minecraft/Forge class was defined");
    }

    private static byte[] block() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(V1_8, ACC_PUBLIC | ACC_FINAL, MOD + "Block", null, BLOCK, null);
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null); method.visitCode();
        method.visitVarInsn(ALOAD, 0); method.visitFieldInsn(GETSTATIC, MATERIAL, "field_151576_e", "L" + MATERIAL + ";");
        method.visitMethodInsn(INVOKESPECIAL, BLOCK, "<init>", "(L" + MATERIAL + ";)V", false); method.visitInsn(RETURN); end(method); writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] mod() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES); writer.visit(V1_8, ACC_PUBLIC | ACC_FINAL, MOD, null, "java/lang/Object", null);
        AnnotationVisitor mod = writer.visitAnnotation("Lnet/minecraftforge/fml/common/Mod;", true);
        mod.visit("modid", ID); mod.visit("name", "Arcadium Registry Smoke"); mod.visit("version", "1.0.0"); mod.visit("acceptedMinecraftVersions", "[1.8.9]"); mod.visitEnd();
        writer.visitField(ACC_PUBLIC | ACC_STATIC, "BLOCK", "L" + BLOCK + ";", null, null).visitEnd(); writer.visitField(ACC_PUBLIC | ACC_STATIC, "ITEM", "L" + ITEM + ";", null, null).visitEnd();
        for (String name : List.of("BLOCK_ID", "ITEM_ID", "SERVER_STARTS")) writer.visitField(ACC_PUBLIC | ACC_STATIC, name, "I", null, null).visitEnd();
        writer.visitField(ACC_PUBLIC | ACC_STATIC, "REGISTERED", "Z", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null); method.visitCode(); method.visitVarInsn(ALOAD, 0); method.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); method.visitInsn(RETURN); end(method);
        method = event(writer, "preInit", "FMLPreInitializationEvent");
        method.visitTypeInsn(NEW, MOD + "Block"); method.visitInsn(DUP); method.visitMethodInsn(INVOKESPECIAL, MOD + "Block", "<init>", "()V", false); method.visitFieldInsn(PUTSTATIC, MOD, "BLOCK", "L" + BLOCK + ";");
        method.visitFieldInsn(GETSTATIC, MOD, "BLOCK", "L" + BLOCK + ";"); method.visitLdcInsn("arcadium.registry_smoke_block"); method.visitMethodInsn(INVOKEVIRTUAL, BLOCK, "func_149663_c", "(Ljava/lang/String;)L" + BLOCK + ";", false); method.visitInsn(POP);
        method.visitFieldInsn(GETSTATIC, MOD, "BLOCK", "L" + BLOCK + ";"); method.visitLdcInsn("smoke_block"); method.visitMethodInsn(INVOKESTATIC, GAME, "registerBlock", "(L" + BLOCK + ";Ljava/lang/String;)L" + BLOCK + ";", false); method.visitInsn(POP);
        method.visitTypeInsn(NEW, ITEM); method.visitInsn(DUP); method.visitMethodInsn(INVOKESPECIAL, ITEM, "<init>", "()V", false); method.visitFieldInsn(PUTSTATIC, MOD, "ITEM", "L" + ITEM + ";");
        method.visitFieldInsn(GETSTATIC, MOD, "ITEM", "L" + ITEM + ";"); method.visitLdcInsn("arcadium.registry_smoke_item"); method.visitMethodInsn(INVOKEVIRTUAL, ITEM, "func_77655_b", "(Ljava/lang/String;)L" + ITEM + ";", false); method.visitInsn(POP);
        method.visitFieldInsn(GETSTATIC, MOD, "ITEM", "L" + ITEM + ";"); method.visitLdcInsn("smoke_item"); method.visitMethodInsn(INVOKESTATIC, GAME, "registerItem", "(L" + ITEM + ";Ljava/lang/String;)V", false);
        method.visitMethodInsn(INVOKESTATIC, MOD, "verifyRegistered", "()V", false);
        currentId(method, true); method.visitFieldInsn(PUTSTATIC, MOD, "BLOCK_ID", "I"); currentId(method, false); method.visitFieldInsn(PUTSTATIC, MOD, "ITEM_ID", "I");
        method.visitInsn(ICONST_1); method.visitFieldInsn(PUTSTATIC, MOD, "REGISTERED", "Z"); log(method, "[ArcadiumRegistrySmoke] preInit registered block/item"); method.visitInsn(RETURN); end(method);
        method = event(writer, "serverStarted", "FMLServerStartedEvent"); method.visitMethodInsn(INVOKESTATIC, MOD, "verifyRegistered", "()V", false);
        method.visitFieldInsn(GETSTATIC, MOD, "SERVER_STARTS", "I"); method.visitInsn(ICONST_1); method.visitInsn(IADD); method.visitFieldInsn(PUTSTATIC, MOD, "SERVER_STARTS", "I"); log(method, "[ArcadiumRegistrySmoke] integrated world registry verified"); method.visitInsn(RETURN); end(method);
        method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "verifyRegistered", "()V", null, null); method.visitCode();
        for (boolean block : List.of(true, false)) {
            String type = block ? BLOCK : ITEM, field = block ? "BLOCK" : "ITEM";
            method.visitLdcInsn(ID); method.visitLdcInsn(block ? "smoke_block" : "smoke_item"); method.visitMethodInsn(INVOKESTATIC, GAME, block ? "findBlock" : "findItem", "(Ljava/lang/String;Ljava/lang/String;)L" + type + ";", false);
            method.visitFieldInsn(GETSTATIC, MOD, field, "L" + type + ";"); Label correct = new Label(); method.visitJumpInsn(IF_ACMPEQ, correct); fail(method, "The Forge registry lookup lost the smoke " + field); method.visitLabel(correct);
            currentId(method, block); Label valid = new Label(); method.visitJumpInsn(IFGE, valid); fail(method, "The smoke registry ID is missing"); method.visitLabel(valid);
        }
        method.visitLdcInsn(ID); method.visitLdcInsn("smoke_block"); method.visitMethodInsn(INVOKESTATIC, GAME, "findItem", "(Ljava/lang/String;Ljava/lang/String;)L" + ITEM + ";", false); Label blockItem = new Label(); method.visitJumpInsn(IFNONNULL, blockItem); fail(method, "The Forge ItemBlock was not registered"); method.visitLabel(blockItem); method.visitInsn(RETURN); end(method);
        method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "verifyRestored", "()V", null, null); method.visitCode(); method.visitMethodInsn(INVOKESTATIC, MOD, "verifyRegistered", "()V", false);
        method.visitFieldInsn(GETSTATIC, MOD, "SERVER_STARTS", "I"); Label started = new Label(); method.visitJumpInsn(IFGT, started); fail(method, "The smoke mod has not joined an integrated world"); method.visitLabel(started);
        for (boolean block : List.of(true, false)) { currentId(method, block); method.visitFieldInsn(GETSTATIC, MOD, block ? "BLOCK_ID" : "ITEM_ID", "I"); Label restored = new Label(); method.visitJumpInsn(IF_ICMPEQ, restored); fail(method, "The original Forge registry ID was not restored"); method.visitLabel(restored); }
        log(method, "[ArcadiumRegistrySmoke] world/snapshot roundtrip verified"); method.visitInsn(RETURN); end(method); writer.visitEnd(); return writer.toByteArray();
    }
    private static MethodVisitor event(ClassWriter writer, String name, String event) { MethodVisitor method = writer.visitMethod(ACC_PUBLIC, name, "(Lnet/minecraftforge/fml/common/event/" + event + ";)V", null, null); method.visitAnnotation("Lnet/minecraftforge/fml/common/Mod$EventHandler;", true).visitEnd(); method.visitCode(); return method; }
    private static void currentId(MethodVisitor method, boolean block) { String type = block ? BLOCK : ITEM; method.visitFieldInsn(GETSTATIC, MOD, block ? "BLOCK" : "ITEM", "L" + type + ";"); method.visitMethodInsn(INVOKESTATIC, type, block ? "func_149682_b" : "func_150891_b", "(L" + type + ";)I", false); }
    private static void fail(MethodVisitor method, String message) { method.visitTypeInsn(NEW, "java/lang/IllegalStateException"); method.visitInsn(DUP); method.visitLdcInsn(message); method.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false); method.visitInsn(ATHROW); }
    private static void log(MethodVisitor method, String message) { method.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"); method.visitLdcInsn(message); method.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false); }
    private static void end(MethodVisitor method) { method.visitMaxs(0, 0); method.visitEnd(); }
}
'''


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, default=Path(os.environ["JAVA_HOME"]) if "JAVA_HOME" in os.environ else None)
    args = parser.parse_args()
    if args.java_home is None:
        raise SystemExit("Set JAVA_HOME or pass --java-home for the private fixture generator.")
    output = ROOT / "build/forge-compat/registry-smoke"
    generator = output / "generator"
    generator.mkdir(parents=True, exist_ok=True)
    source = generator / "RegistrySmokeBuilder.java"
    source.write_text(SOURCE, encoding="utf-8")
    jars = []
    for artifact in ("asm", "asm-tree", "asm-analysis"):
        matches = list((Path.home() / ".gradle/caches/modules-2/files-2.1/org.ow2.asm" / artifact / "9.10.1").glob(f"*/{artifact}-9.10.1.jar"))
        if len(matches) != 1:
            raise SystemExit(f"Expected one cached ASM 9.10.1 {artifact} JAR; no downloads were attempted.")
        jars.append(matches[0])
    classpath = os.pathsep.join(map(str, jars))
    suffix = ".exe" if os.name == "nt" else ""
    subprocess.run([str(args.java_home / "bin" / f"javac{suffix}"), "--release", "17", "-classpath", classpath, "-d", str(generator), str(source)], check=True)
    jar = output / "ArcadiumRegistrySmoke-1.0.0.jar"
    subprocess.run([str(args.java_home / "bin" / f"java{suffix}"), "-classpath", os.pathsep.join([str(generator), classpath]), "RegistrySmokeBuilder", str(jar)], check=True)
    result = {"path": str(jar), "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(), "bytes": jar.stat().st_size, "modId": "arcadiumregistrysmoke", "classes": 2, "minecraftOrForgeClassesDefined": False, "preInit": "GameRegistry.registerBlock/registerItem", "worldProbe": "FMLServerStartedEvent verifies real lookup identities", "afterReturnToMenuProbe": "arcadium.registry.smoke.RegistrySmoke.verifyRestored()", "published": False}
    (output / "manifest.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
