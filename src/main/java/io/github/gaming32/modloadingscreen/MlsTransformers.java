package io.github.gaming32.modloadingscreen;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ListIterator;
import java.util.function.Consumer;

public final class MlsTransformers {
    private static final boolean DUMP_TRANSFORMED_CLASSES =
        Boolean.getBoolean("mod-loading-screen.dumpTransformedClasses");
    private static volatile boolean notifiedClassDump;

    public static final String FABRIC_LOADER_IMPL = "net/fabricmc/loader/impl/FabricLoaderImpl";

    public static final String FABRIC_ENTRYPOINT_UTILS = "net/fabricmc/loader/impl/entrypoint/EntrypointUtils";
    public static final String QUILT_ENTRYPOINT_UTILS = "org/quiltmc/loader/impl/entrypoint/EntrypointUtils";
    private static final String ENTRYPOINT_CONTAINER = "net/fabricmc/loader/api/entrypoint/EntrypointContainer";
    private static final String ENTRYPOINT_CONTAINER_IMPL = "net/fabricmc/loader/impl/entrypoint/EntrypointContainerImpl";
    private static final String MOD_CONTAINER = "net/fabricmc/loader/api/ModContainer";
    private static final String MOD_METADATA = "net/fabricmc/loader/api/metadata/ModMetadata";

    private static final String MOD_DISCOVERER = "net/fabricmc/loader/impl/discovery/ModDiscoverer";
    private static final String MOD_RESOLVER = "org/quiltmc/loader/impl/discovery/ModResolver";
    private static final String FABRIC_BUILTIN_MOD = "net/fabricmc/loader/impl/game/GameProvider$BuiltinMod";
    private static final String QUILT_BUILTIN_MOD = "org/quiltmc/loader/impl/game/GameProvider$BuiltinMod";
    private static final String FABRIC_VERSION = "net/fabricmc/loader/api/Version";

    private static final String STANDARD_QUILT_PLUGIN = "org/quiltmc/loader/impl/plugin/quilt/StandardQuiltPlugin";
    private static final String INTERNAL_MOD_METADATA = "org/quiltmc/loader/impl/metadata/qmj/InternalModMetadata";
    private static final String QUILT_VERSION = "org/quiltmc/loader/api/Version";

    public static final String ACTUAL_LOADING_SCREEN = "io/github/gaming32/modloadingscreen/ActualLoadingScreen";

    private static final Collection<Consumer<ClassNode>> FABRIC_LOADER_IMPL_TRANSFORMER = Collections.singleton(
        MlsTransformers::instrumentFabricLoaderImplInvokeEntrypoints
    );
    private static final Collection<Consumer<ClassNode>> FABRIC_ENTRYPOINT_UTILS_TRANSFORMER = Arrays.asList(
        clazz -> instrumentEntrypointUtilsInvoke(clazz, false),
        clazz -> instrumentEntrypointUtilsInvoke0(clazz, false)
    );
    private static final Collection<Consumer<ClassNode>> QUILT_ENTRYPOINT_UTILS_TRANSFORMER = Arrays.asList(
        clazz -> instrumentEntrypointUtilsInvoke(clazz, true),
        clazz -> instrumentEntrypointUtilsInvoke0(clazz, true)
    );
    private static final Collection<Consumer<ClassNode>> FABRIC_MOD_DISCOVERER_TRANSFORMER = Collections.singleton(
        clazz -> instrumentModDiscovererDiscoverMods(clazz, false)
    );
    private static final Collection<Consumer<ClassNode>> QUILT_MOD_RESOLVER_TRANSFORMER = Collections.singleton(
        clazz -> instrumentModDiscovererDiscoverMods(clazz, true)
    );
    private static final Collection<Consumer<ClassNode>> STANDARD_QUILT_PLUGIN_ADD_BUILTIN_MODS_TRANSFORMER = Collections.singleton(
        MlsTransformers::instrumentStandardQuiltPluginAddBuiltinMods
    );

    static byte[] instrumentClass(String name, byte[] bytes) {
        if (DUMP_TRANSFORMED_CLASSES && !notifiedClassDump) {
            synchronized (MlsTransformers.class) {
                if (!notifiedClassDump) {
                    notifiedClassDump = true;
                    System.out.println("[ModLoadingScreen] Transformed class dumping is active");
                    try {
                        final Path dumpDir = Paths.get(".mlsDebugDump");
                        if (Files.isDirectory(dumpDir)) {
                            Files.walkFileTree(dumpDir, new SimpleFileVisitor<Path>() {
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                    Files.delete(file);
                                    return super.visitFile(file, attrs);
                                }

                                @Override
                                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                    Files.delete(dir);
                                    return super.postVisitDirectory(dir, exc);
                                }
                            });
                        }
                    } catch (Throwable t) {
                        System.err.println("[ModLoadingScreen] [ERROR] Failed to clear debug dump dir");
                    }
                }
            }
        }

        try {
            Collection<Consumer<ClassNode>> transformer = null;
            switch (name) {
                case FABRIC_LOADER_IMPL:
                    transformer = FABRIC_LOADER_IMPL_TRANSFORMER;
                    break;
                case FABRIC_ENTRYPOINT_UTILS:
                    transformer = FABRIC_ENTRYPOINT_UTILS_TRANSFORMER;
                    break;
                case QUILT_ENTRYPOINT_UTILS:
                    transformer = QUILT_ENTRYPOINT_UTILS_TRANSFORMER;
                    break;
                case MOD_DISCOVERER:
                    transformer = FABRIC_MOD_DISCOVERER_TRANSFORMER;
                    break;
                case MOD_RESOLVER:
                    transformer = QUILT_MOD_RESOLVER_TRANSFORMER;
                    break;
                case STANDARD_QUILT_PLUGIN:
                    transformer = STANDARD_QUILT_PLUGIN_ADD_BUILTIN_MODS_TRANSFORMER;
                    break;
            }
            if (transformer != null) {
                System.out.println("[ModLoadingScreen] Transforming " + name);
                final ClassReader reader = new ClassReader(bytes);
                final ClassNode clazz = new ClassNode();
                reader.accept(clazz, 0);
                for (final Consumer<ClassNode> part : transformer) {
                    try {
                        part.accept(clazz);
                    } catch (Exception e) {
                        System.err.println("[ModLoadingScreen] [ERROR] Transformer " + part + " for " + name + " failed");
                        e.printStackTrace();
                    }
                }
                final ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                clazz.accept(writer);
                final byte[] result = writer.toByteArray();
                if (DUMP_TRANSFORMED_CLASSES) {
                    try {
                        final Path dumpedPath = Paths.get(".mlsDebugDump", name + ".class");
                        Files.createDirectories(dumpedPath.getParent());
                        Files.write(dumpedPath, result);
                    } catch (Exception e) {
                        System.err.println("[ModLoadingScreen] [ERROR] Failed to dump class " + name);
                        e.printStackTrace();
                    }
                }
                return result;
            }
        } catch (Throwable t) {
            System.err.println("[ModLoadingScreen] [ERROR] Completely failed to transform " + name);
            t.printStackTrace();
        }
        return null;
    }

    private static void instrumentFabricLoaderImplInvokeEntrypoints(ClassNode clazz) {
        final MethodNode method = clazz.methods.stream()
            .filter(m -> m.name.equals("invokeEntrypoints"))
            .findFirst()
            .orElse(null);
        if (method == null) {
            System.out.println("[ModLoadingScreen] New-style FabricLoaderImpl.invokeEntrypoints not found. Assuming old Fabric.");
            return;
        }
        final ListIterator<AbstractInsnNode> it = method.instructions.iterator();

        maybeCloseAfter(it, true);

        while (it.hasNext()) {
            final AbstractInsnNode insn = it.next();
            if (!(insn instanceof InsnNode)) continue;
            if (insn.getOpcode() == Opcodes.ACONST_NULL) break;
        }
        it.previous();
        mainEntrypointHooks(it, false,  true);

        maybeCloseAfter(it, true);
    }

    private static void instrumentEntrypointUtilsInvoke(ClassNode clazz, boolean onQuilt) {
        final MethodNode method = clazz.methods.stream()
            .filter(m -> m.name.equals(onQuilt ? "invokeContainer" : "invoke"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
        final ListIterator<AbstractInsnNode> it = method.instructions.iterator();

        maybeCloseAfter(it, false);
    }

    /**
     * Inserts code for calling maybeCloseAfter, and leaves {@code it} pointing to the {@code RETURN}.
     */
    private static void maybeCloseAfter(ListIterator<AbstractInsnNode> it, boolean instanceMethod) {
        while (it.hasNext()) {
            final AbstractInsnNode insn = it.next();
            if (!(insn instanceof InsnNode)) continue;
            if (insn.getOpcode() == Opcodes.RETURN) break;
        }
        it.previous();
        it.add(new VarInsnNode(Opcodes.ALOAD, instanceMethod ? 1 : 0));
        it.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            ACTUAL_LOADING_SCREEN, "maybeCloseAfter",
            "(Ljava/lang/String;)V",
            false
        ));
    }

    private static void instrumentEntrypointUtilsInvoke0(ClassNode clazz, boolean onQuilt) {
        final MethodNode method = clazz.methods.stream()
            .filter(m -> m.name.equals("invoke0"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
        final ListIterator<AbstractInsnNode> it = method.instructions.iterator();

        mainEntrypointHooks(it, onQuilt, false);
    }

    private static void mainEntrypointHooks(ListIterator<AbstractInsnNode> it, boolean onQuilt, boolean instanceMethod) {
        final int varOffset = instanceMethod ? 1 : 0;
        //noinspection PointlessArithmeticExpression
        final int keyIndex = 0 + varOffset;
        final int typeIndex = 1 + varOffset;

        it.add(new VarInsnNode(Opcodes.ALOAD, keyIndex));
        it.add(new VarInsnNode(Opcodes.ALOAD, typeIndex));
        it.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            ACTUAL_LOADING_SCREEN, "beforeEntrypointType",
            "(Ljava/lang/String;Ljava/lang/Class;)V"
        ));

        final int container = (onQuilt ? 7 : 6) + varOffset;
        while (it.hasNext()) {
            final AbstractInsnNode insn = it.next();
            if (!(insn instanceof VarInsnNode)) continue;
            if (insn.getOpcode() == Opcodes.ASTORE && ((VarInsnNode)insn).var == container) break;
        }
        it.add(new VarInsnNode(Opcodes.ALOAD, keyIndex));
        it.add(new VarInsnNode(Opcodes.ALOAD, typeIndex));
        it.add(new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/Class", "getSimpleName",
            "()Ljava/lang/String;"
        ));
        if (onQuilt) {
            it.add(new TypeInsnNode(Opcodes.NEW, ENTRYPOINT_CONTAINER_IMPL));
            it.add(new InsnNode(Opcodes.DUP));
        }
        it.add(new VarInsnNode(Opcodes.ALOAD, container));
        if (onQuilt) {
            it.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                ENTRYPOINT_CONTAINER_IMPL, "<init>",
                "(Lorg/quiltmc/loader/api/entrypoint/EntrypointContainer;)V"
            ));
        }
        it.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            ENTRYPOINT_CONTAINER, "getProvider",
            "()L" + MOD_CONTAINER + ";"
        ));
        it.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            MOD_CONTAINER, "getMetadata",
            "()L" + MOD_METADATA + ";"
        ));
        it.add(new InsnNode(Opcodes.DUP));
        it.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            MOD_METADATA, "getId",
            "()Ljava/lang/String;"
        ));
        it.add(new InsnNode(Opcodes.SWAP));
        it.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            MOD_METADATA, "getName",
            "()Ljava/lang/String;"
        ));
        it.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            ACTUAL_LOADING_SCREEN, "beforeSingleEntrypoint",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
            false
        ));

        while (it.hasNext()) {
            final AbstractInsnNode insn = it.next();
            if (!(insn instanceof InsnNode)) continue;
            if (insn.getOpcode() == Opcodes.IFNULL) break;
        }
        it.previous();
        it.previous();
        it.add(new VarInsnNode(Opcodes.ALOAD, keyIndex));
        it.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            ACTUAL_LOADING_SCREEN, "afterEntrypointType",
            "(Ljava/lang/String;)V"
        ));
    }

    private static void instrumentModDiscovererDiscoverMods(ClassNode clazz, boolean onQuilt) {
        final MethodNode method = clazz.methods.stream()
            .filter(m -> m.name.equals(onQuilt ? "resolve" : "discoverMods"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
        final ListIterator<AbstractInsnNode> it = method.instructions.iterator();

        final String BuiltinMod = onQuilt ? QUILT_BUILTIN_MOD : FABRIC_BUILTIN_MOD;

        while (it.hasNext()) {
            final AbstractInsnNode insn = it.next();
            if (!(insn instanceof TypeInsnNode)) continue;
            if (insn.getOpcode() == Opcodes.CHECKCAST && ((TypeInsnNode)insn).desc.equals(BuiltinMod)) break;
        }

        // BuiltinMod
        it.add(new InsnNode(Opcodes.DUP));
        // BuiltinMod BuiltinMod
        it.add(new FieldInsnNode(Opcodes.GETFIELD, BuiltinMod, "metadata", "L" + MOD_METADATA + ";"));
        // BuiltinMod ModMetadata
        it.add(new InsnNode(Opcodes.DUP));
        // BuiltinMod ModMetadata ModMetadata
        it.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, MOD_METADATA, "getId", "()Ljava/lang/String;"));
        // BuiltinMod ModMetadata String
        it.add(new InsnNode(Opcodes.SWAP));
        // BuiltinMod String ModMetadata
        it.add(new InsnNode(Opcodes.DUP));
        // BuiltinMod String ModMetadata ModMetadata
        it.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, MOD_METADATA, "getName", "()Ljava/lang/String;"));
        // BuiltinMod String ModMetadata String
        it.add(new InsnNode(Opcodes.SWAP));
        // BuiltinMod String String ModMetadata
        it.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, MOD_METADATA, "getVersion", "()L" + FABRIC_VERSION + ";"));
        // BuiltinMod String String Version
        it.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, FABRIC_VERSION, "getFriendlyString", "()Ljava/lang/String;"));
        // BuiltinMod String String String
        it.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC, ACTUAL_LOADING_SCREEN, "setTitleFromMetadata",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
        ));
        // BuiltinMod
    }

    private static void instrumentStandardQuiltPluginAddBuiltinMods(ClassNode clazz) {
        final MethodNode method = clazz.methods.stream()
            .filter(m -> m.name.equals("addBuiltinMods"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
        final ListIterator<AbstractInsnNode> it = method.instructions.iterator();

        while (it.hasNext()) {
            final AbstractInsnNode insn = it.next();
            if (!(insn instanceof TypeInsnNode)) continue;
            if (insn.getOpcode() == Opcodes.CHECKCAST && ((TypeInsnNode)insn).desc.equals(QUILT_BUILTIN_MOD)) break;
        }

        // BuiltinMod
        it.add(new InsnNode(Opcodes.DUP));
        // BuiltinMod BuiltinMod
        it.add(new FieldInsnNode(Opcodes.GETFIELD, QUILT_BUILTIN_MOD, "metadata", "L" + INTERNAL_MOD_METADATA + ";"));
        // BuiltinMod InternalModMetadata
        it.add(new InsnNode(Opcodes.DUP));
        // BuiltinMod InternalModMetadata InternalModMetadata
        it.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, INTERNAL_MOD_METADATA, "id", "()Ljava/lang/String;"));
        // BuiltinMod InternalModMetadata String
        it.add(new InsnNode(Opcodes.SWAP));
        // BuiltinMod String InternalModMetadata
        it.add(new InsnNode(Opcodes.DUP));
        // BuiltinMod String InternalModMetadata InternalModMetadata
        it.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, INTERNAL_MOD_METADATA, "name", "()Ljava/lang/String;"));
        // BuiltinMod String InternalModMetadata String
        it.add(new InsnNode(Opcodes.SWAP));
        // BuiltinMod String String InternalModMetadata
        it.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, INTERNAL_MOD_METADATA, "version", "()L" + QUILT_VERSION + ";"));
        // BuiltinMod String String Version
        it.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, QUILT_VERSION, "raw", "()Ljava/lang/String;"));
        // BuiltinMod String String String
        it.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC, ACTUAL_LOADING_SCREEN, "setTitleFromMetadata",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
        ));
        // BuiltinMod
    }
}
