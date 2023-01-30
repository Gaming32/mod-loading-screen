package io.github.gaming32.modloadingscreen;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.lenni0451.reflect.Agents;
import net.lenni0451.reflect.ClassLoaders;
import net.lenni0451.reflect.JavaBypass;
import net.lenni0451.reflect.Methods;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.ListIterator;

public class ModLoadingScreen implements LanguageAdapter {
    private static final boolean RUNNING_ON_QUILT = FabricLoader.getInstance().isModLoaded("quilt_loader");

    private static final String ENTRYPOINT_UTILS = RUNNING_ON_QUILT
        ? "org/quiltmc/loader/impl/entrypoint/EntrypointUtils"
        : "net/fabricmc/loader/impl/entrypoint/EntrypointUtils";
    private static final String ENTRYPOINT_CONTAINER = "net/fabricmc/loader/api/entrypoint/EntrypointContainer";
    private static final String ENTRYPOINT_CONTAINER_IMPL = "net/fabricmc/loader/impl/entrypoint/EntrypointContainerImpl";
    private static final String ACTUAL_LOADING_SCREEN = "io/github/gaming32/modloadingscreen/ActualLoadingScreen";

    @Override
    @SuppressWarnings("unchecked")
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        if (type != PreLaunchEntrypoint.class) {
            throw new LanguageAdapterException("Fake entrypoint only supported on PreLaunchEntrypoint");
        }
        return (T)(PreLaunchEntrypoint)() -> {};
    }

    public static void init() throws Throwable {
        System.out.println("[ModLoadingScreen] I just want to say... I'm loading " + (RUNNING_ON_QUILT ? "kinda" : "*really*") + " early.");

        ClassLoaders.addToSystemClassPath(
            FabricLoader.getInstance()
                .getModContainer("mod-loading-screen")
                .orElseThrow()
                .getRootPaths().get(0)
                .toUri().toURL()
        );
        ClassLoaders.addToSystemClassPath(
            FabricLoader.getInstance()
                .getModContainer("com_formdev_flatlaf")
                .orElseThrow()
                .getRootPaths().get(0)
                .toUri().toURL()
        );

        final byte[] aclData = Files.readAllBytes(
            FabricLoader.getInstance()
                .getModContainer("mod-loading-screen")
                .orElseThrow()
                .findPath(ACTUAL_LOADING_SCREEN + ".bin")
                .orElseThrow()
        );

        Methods.invoke(null, Methods.getDeclaredMethod(
            ClassLoaders.defineClass(ClassLoader.getSystemClassLoader(), ACTUAL_LOADING_SCREEN.replace('/', '.'), aclData),
            "startLoadingScreen"
        ));

        Agents.getInstrumentation().addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                return className.equals(ENTRYPOINT_UTILS) ? instrumentClass(classfileBuffer) : null;
            }
        }, true);
        Agents.getInstrumentation().retransformClasses(Class.forName(ENTRYPOINT_UTILS.replace('/', '.')));
    }

    private static byte[] instrumentClass(byte[] bytes) {
        final ClassNode clazz = new ClassNode();
        new ClassReader(bytes).accept(clazz, 0);

        instrumentInvoke(clazz);
        instrumentInvoke0(clazz);

        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        clazz.accept(writer);
        return writer.toByteArray();
    }

    private static void instrumentInvoke(ClassNode clazz) {
        final MethodNode method = clazz.methods.stream()
            .filter(m -> m.name.equals(RUNNING_ON_QUILT ? "invokeContainer" : "invoke"))
            .findFirst()
            .orElseThrow();
        final ListIterator<AbstractInsnNode> it = method.instructions.iterator();

        while (it.hasNext()) {
            if (!(it.next() instanceof InsnNode insn)) continue;
            if (insn.getOpcode() == Opcodes.RETURN) break;
        }
        it.previous();
        it.add(new VarInsnNode(Opcodes.ALOAD, 0));
        it.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            ACTUAL_LOADING_SCREEN, "maybeCloseAfter",
            "(Ljava/lang/String;)V",
            false
        ));
    }

    private static void instrumentInvoke0(ClassNode clazz) {
        final MethodNode method = clazz.methods.stream()
            .filter(m -> m.name.equals("invoke0"))
            .findFirst()
            .orElseThrow();
        final ListIterator<AbstractInsnNode> it = method.instructions.iterator();

        it.add(new VarInsnNode(Opcodes.ALOAD, 0));
        it.add(new VarInsnNode(Opcodes.ALOAD, 1));
        it.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            ACTUAL_LOADING_SCREEN, "beforeEntrypointType",
            "(Ljava/lang/String;Ljava/lang/Class;)V",
            false
        ));

        final int container = RUNNING_ON_QUILT ? 7 : 6;
        while (it.hasNext()) {
            if (!(it.next() instanceof VarInsnNode varInsn)) continue;
            if (varInsn.getOpcode() == Opcodes.ASTORE && varInsn.var == container) break;
        }
        it.add(new VarInsnNode(Opcodes.ALOAD, 0));
        it.add(new VarInsnNode(Opcodes.ALOAD, 1));
        if (RUNNING_ON_QUILT) {
            it.add(new TypeInsnNode(Opcodes.NEW, ENTRYPOINT_CONTAINER_IMPL));
            it.add(new InsnNode(Opcodes.DUP));
        }
        it.add(new VarInsnNode(Opcodes.ALOAD, container));
        if (RUNNING_ON_QUILT) {
            it.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                ENTRYPOINT_CONTAINER_IMPL, "<init>",
                "(Lorg/quiltmc/loader/api/entrypoint/EntrypointContainer;)V",
                false
            ));
        }
        it.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            ACTUAL_LOADING_SCREEN, "beforeSingleEntrypoint",
            "(Ljava/lang/String;Ljava/lang/Class;L" + ENTRYPOINT_CONTAINER + ";)V",
            false
        ));

        while (it.hasNext()) {
            if (!(it.next() instanceof InsnNode insn)) continue;
            if (insn.getOpcode() == Opcodes.IFNULL) break;
        }
        it.previous();
        it.previous();
        it.add(new VarInsnNode(Opcodes.ALOAD, 0));
        it.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            ACTUAL_LOADING_SCREEN, "afterEntrypointType",
            "(Ljava/lang/String;)V",
            false
        ));
    }

    static {
        try {
            init();
        } catch (Throwable t) {
            JavaBypass.getUnsafe().throwException(t);
        }
    }
}
