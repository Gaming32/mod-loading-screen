package io.github.gaming32.modloadingscreen;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.lenni0451.reflect.Agents;
import net.lenni0451.reflect.ClassLoaders;
import net.lenni0451.reflect.Methods;

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static io.github.gaming32.modloadingscreen.MlsTransformers.ACTUAL_LOADING_SCREEN;

public class ModLoadingScreen implements LanguageAdapter {
    private static final boolean RUNNING_ON_QUILT = FabricLoader.getInstance().isModLoaded("quilt_loader");
    private static final String ENTRYPOINT_UTILS = RUNNING_ON_QUILT
        ? MlsTransformers.QUILT_ENTRYPOINT_UTILS
        : MlsTransformers.FABRIC_ENTRYPOINT_UTILS;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        if (type != PreLaunchEntrypoint.class) {
            throw new LanguageAdapterException("Fake entrypoint only supported on PreLaunchEntrypoint");
        }
        return (T)(PreLaunchEntrypoint)() -> {};
    }

    public static void init() throws Throwable {
        System.out.println("[ModLoadingScreen] I just want to say... I'm loading *really* early.");
        if (System.setProperty("mod-loading-screen.loaded", "true") != null) {
            System.err.println("[ModLoadingScreen] [WARN] Mod Loading Screen installed as both a mod and an agent.");
            System.err.println("[ModLoadingScreen] [WARN] Please avoid doing this. To avoid issues, the mod has disabled itself.");
            return;
        }

        ClassLoaders.addToSystemClassPath(
            FabricLoader.getInstance()
                .getModContainer("mod-loading-screen")
                .orElseThrow(AssertionError::new)
                .getRootPaths().get(0)
                .toUri().toURL()
        );
        ClassLoaders.addToSystemClassPath(
            FabricLoader.getInstance()
                .getModContainer("com_formdev_flatlaf")
                .orElseThrow(AssertionError::new)
                .getRootPaths().get(0)
                .toUri().toURL()
        );

        final byte[] alsData = Files.readAllBytes(
            FabricLoader.getInstance()
                .getModContainer("mod-loading-screen")
                .orElseThrow(AssertionError::new)
                .findPath(ACTUAL_LOADING_SCREEN + ".class")
                .orElseThrow(AssertionError::new)
        );

        Methods.invoke(null, Methods.getDeclaredMethod(
            ClassLoaders.defineClass(ClassLoader.getSystemClassLoader(), ACTUAL_LOADING_SCREEN.replace('/', '.'), alsData),
            "startLoadingScreen", boolean.class
        ), true);

        final Instrumentation instrumentation = Agents.getInstrumentation();
        instrumentation.addTransformer(
            (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) ->
                MlsTransformers.instrumentClass(className, classfileBuffer),
            true
        );
        final List<Class<?>> toRetransform = new ArrayList<>(1);
        for (final Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (MlsTransformers.TRANSFORMERS.containsKey(loaded.getName().replace('.', '/'))) {
                toRetransform.add(loaded);
            }
        }
        instrumentation.retransformClasses(toRetransform.toArray(new Class<?>[0]));
    }

    static {
        try {
            init();
        } catch (Throwable t) {
            System.err.println("[ModLoadingScreen] Failed to initialize loading screen. Aborting!");
            throw new Error(t);
        }
    }
}
