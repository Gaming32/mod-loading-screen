package io.github.gaming32.modloadingscreen;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

public class EarlyLoadingAgent {
    public static void premain(String args, Instrumentation instrumentation) throws IOException {
        System.out.println("[ModLoadingScreen] I just want to say... I'm loading *really* **extremely** early.");
        System.setProperty("mod-loading-screen.loaded", "true");

        final Path flatlafDestPath = Paths.get(".cache/mod-loading-screen/flatlaf.jar").toAbsolutePath();
        Files.createDirectories(flatlafDestPath.getParent());
        try (InputStream is = EarlyLoadingAgent.class.getResourceAsStream("/META-INF/jars/flatlaf-3.0.jar")) {
            if (is == null) {
                System.err.println("[ModLoadingScreen] [ERROR] flatlaf.jar not found! Aborting.");
                return;
            }
            Files.copy(is, flatlafDestPath, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("[ModLoadingScreen] Extracted flatlaf.jar");
        instrumentation.appendToSystemClassLoaderSearch(new JarFile(flatlafDestPath.toFile()));

        ActualLoadingScreen.startLoadingScreen(false);
        instrumentation.addTransformer(
            (loader, className, classBeingRedefined, protectionDomain, classfileBuffer) ->
                MlsTransformers.instrumentClass(className, classfileBuffer),
            false
        );
    }
}
